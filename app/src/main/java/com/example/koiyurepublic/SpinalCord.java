package com.example.koiyurepublic;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * SpinalCord — バックグラウンド動作の中枢
 *
 * 管理するコンポーネント:
 *   P2PQuakeWebSocketClient  地震情報 WebSocket 受信
 *   TTSConnection            読み上げ（Android TTS）
 *   NotifiConnection         プッシュ通知
 *
 * データフロー:
 *   WebSocket受信
 *     → P2PConverts.toBriefMessage()  短文変換
 *     → NotifiConnection.notify()     プッシュ通知
 *     → TTSConnection.speak()         読み上げ
 *     → LocalWebSocketServer         → WebView(JS)
 */
public class SpinalCord extends Service implements P2PQuakeWebSocketClient.Listener {

    private static final String TAG = "SpinalCord";

    // フォアグラウンド通知（常駐用）は NotifiConnection とは別チャンネルで管理
    private static final String CHANNEL_FOREGROUND = "koiyure_ws_channel";
    private static final int    NOTIF_FOREGROUND_ID = 1;
    private static final long   SELF_RESTART_DELAY_MS = 1500L;

    public static final long WATCHDOG_INTERVAL_MS = 60_000L;

    /** MainActivity 側から現在の起動状態を確認するためのフラグ */
    static volatile boolean isRunning = false;

    private static synchronized void setRunning(boolean running) {
        isRunning = running;
    }

    public static synchronized boolean isServiceRunning() {
        return isRunning;
    }

    // ──────────────────────────────────────────────
    //  WebSocket 接続状態（初期状態通知用）
    // ──────────────────────────────────────────────

    private volatile boolean isP2PQuakeConnected = false;
    private volatile boolean isP2PQuakeReconnecting = false;

    private synchronized void setP2PQuakeConnectionState(boolean connected, boolean willReconnect) {
        isP2PQuakeConnected = connected;
        isP2PQuakeReconnecting = willReconnect;
    }

    private synchronized boolean getIsP2PQuakeConnected() {
        return isP2PQuakeConnected;
    }

    private synchronized boolean getIsP2PQuakeReconnecting() {
        return isP2PQuakeReconnecting;
    }

    // ──────────────────────────────────────────────
    //  子コンポーネント
    // ──────────────────────────────────────────────

    private final P2PQuakeWebSocketClient p2pQuakeWsClient = new P2PQuakeWebSocketClient();
    private TTSConnection    ttsConnection    = null;
    private NotifiConnection notifConnection  = null;
    private SettingsRepository settingsRepository;
    private EarthquakeMessageCoordinator messageCoordinator;

    // ──────────────────────────────────────────────
    //  Watchdog AlarmManager
    // ──────────────────────────────────────────────

    private void scheduleWatchdog() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.w(TAG, "Watchdog設定失敗: AlarmManagerがnull");
            return;
        }
        PendingIntent pi = getWatchdogPendingIntent(this);
        am.cancel(pi);
        scheduleAlarm(am, System.currentTimeMillis() + WATCHDOG_INTERVAL_MS, pi);
    }

    public static void cancelWatchdog(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.w(TAG, "Watchdogキャンセル失敗: AlarmManagerがnull");
            return;
        }
        am.cancel(getWatchdogPendingIntent(ctx));
        Log.d(TAG, "Watchdog cancelled");
    }

    private static PendingIntent getWatchdogPendingIntent(Context ctx) {
        Intent i = new Intent(ctx, WatchdogReceiver.class);
        return PendingIntent.getBroadcast(
                ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent getSelfRestartPendingIntent() {
        Intent restartIntent = new Intent(getApplicationContext(), SpinalCord.class);
        return PendingIntent.getService(
                getApplicationContext(),
                1,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void scheduleSelfRestart(String reason) {
        if (isIntentionallyStopped) {
            Log.d(TAG, reason + " — 意図的停止中のため再起動しない");
            return;
        }
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.w(TAG, reason + " — AlarmManager取得失敗");
            return;
        }
        scheduleAlarm(am, System.currentTimeMillis() + SELF_RESTART_DELAY_MS,
                getSelfRestartPendingIntent());
        Log.d(TAG, reason + " — 自己再起動をスケジュール");
    }

    private void scheduleAlarm(AlarmManager alarmManager, long triggerAtMillis,
                               PendingIntent pendingIntent) {
        alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
    }

    // ──────────────────────────────────────────────
    //  Binder / WebView state synchronization
    // ──────────────────────────────────────────────

    public class LocalBinder extends Binder {
        public SpinalCord getService() { return SpinalCord.this; }
    }

    private final IBinder binder = new LocalBinder();

    /**
     * WebViewが接続した後に現在の状態をローカルWebSocketへ再送する。
     * 地震データや状態変更の通常配信はこのWebSocketに統一する。
     */
    public void syncWebViewState() {
        Log.d(TAG, "WebView状態同期開始");
        LocalWebSocketServer server = LocalWebSocketServer.getInstance();
        server.broadcastConnectionState(getIsP2PQuakeConnected(), getIsP2PQuakeReconnecting());
        server.broadcastServiceState(isServiceRunning());
    }

    // ──────────────────────────────────────────────
    //  意図的停止フラグ
    // ──────────────────────────────────────────────

    private boolean isIntentionallyStopped = false;

    public void stopIntentionally() {
        isIntentionallyStopped = true;
        Log.d(TAG, "stopIntentionally — 自己再起動を無効化");
    }

    // ──────────────────────────────────────────────
    //  Service ライフサイクル
    // ──────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate開始");
        setRunning(true);
        isIntentionallyStopped = false;
        LocalWebSocketServer.getInstance().broadcastServiceState(true);

        // ① フォアグラウンド通知（常駐用）を先に立てる
        createForegroundChannel();
        startForeground(NOTIF_FOREGROUND_ID, buildForegroundNotification("接続中…"));

        // Watchdog
        scheduleWatchdog();

        // 子コンポーネント初期化
        EpspArea.init(this);          // 地域コードCSVを読み込む
        ttsConnection   = new TTSConnection(this);
        notifConnection = new NotifiConnection(this);
        settingsRepository = new SettingsRepository(this);
        EarthquakePolicy policy = new EarthquakePolicy(settingsRepository);
        messageCoordinator = new EarthquakeMessageCoordinator(
                policy,
                new NotificationDispatcher(notifConnection, policy),
                new TtsDispatcher(ttsConnection, policy));

        // 保存済み設定を低レベルエンジンへ反映する
        ttsConnection.setEnabled(settingsRepository.isTtsEnabled());
        notifConnection.setEnabled(settingsRepository.isNotificationEnabled());
        ttsConnection.setSpeechRate(settingsRepository.getTtsSpeechRate());
        ttsConnection.setPitch(settingsRepository.getTtsPitch());

        // WebSocket 接続
        P2PQuakeWebSocketClient.addListener(this);
        p2pQuakeWsClient.connect();

        // ローカル WebSocket サーバー起動（JavaScript との超高速通信用）
        LocalWebSocketServer localWsServer = LocalWebSocketServer.getInstance();
        localWsServer.start();

        Log.d(TAG, "SpinalCord onCreate 完了");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand startId=" + startId + " flags=" + flags
                + " action=" + (intent == null ? null : intent.getAction()));
        scheduleWatchdog();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind action=" + (intent == null ? null : intent.getAction()));
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onRebind(Intent intent) { Log.d(TAG, "onRebind"); }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        scheduleSelfRestart("onTaskRemoved");
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy開始 intentionallyStopped=" + isIntentionallyStopped);
        setRunning(false);
        LocalWebSocketServer.getInstance().broadcastServiceState(false);

        // ローカル WebSocket サーバー停止
        LocalWebSocketServer.getInstance().stop();

        // 子コンポーネントを先に解放
        if (ttsConnection   != null) { ttsConnection.shutdown(); ttsConnection   = null; }
        if (notifConnection != null) {                           notifConnection = null; }

        p2pQuakeWsClient.disconnect();
        P2PQuakeWebSocketClient.removeListener(this);
        scheduleSelfRestart("onDestroy");

        super.onDestroy();
    }

    // ──────────────────────────────────────────────
    //  P2PQuakeWebSocketClient.Listener
    // ──────────────────────────────────────────────

    @Override
    public void onConnected() {
        Log.d(TAG, "WS接続完了");
        setP2PQuakeConnectionState(true, false);
        updateForegroundNotification("● 接続済み — 地震情報受信中");

        // ローカル WebSocket に配信（超高速）
        LocalWebSocketServer.getInstance().broadcastConnectionState(true, false);

    }

    @Override
    public void onDisconnected(boolean willReconnect) {
        Log.d(TAG, "WS切断 willReconnect=" + willReconnect);
        setP2PQuakeConnectionState(false, willReconnect);
        updateForegroundNotification(willReconnect ? "○ 切断 — 再接続中…" : "✕ 切断");

        // ローカル WebSocket に配信（超高速）
        LocalWebSocketServer.getInstance().broadcastConnectionState(false, willReconnect);

    }

    /**
     * WebSocket からメッセージ受信 — データフローの起点。
     *
     * 処理順:
     *   1. コード取得
     *   2. 短文メッセージ生成（P2PConverts）
     *   3. 通知発行（NotifiConnection）
     *   4. 読み上げ（TTSConnection）
     *      556 EEW は speakNow() で割り込み読み上げ
     *   5. LocalWebSocketServer → WebView
     */
    @Override
    public void onMessage(String json) {
        EarthquakeMessageCoordinator coordinator = messageCoordinator;
        if (coordinator != null) {
            coordinator.handle(json);
        } else {
            Log.w(TAG, "受信したがCoordinator未初期化");
        }
    }

    // ──────────────────────────────────────────────
    //  外部から TTS / 通知の設定を変更するメソッド
    //  MainActivityのJsBridgeや設定画面から呼ぶ
    // ──────────────────────────────────────────────

    public void setTtsEnabled(boolean enabled) {
        Log.d(TAG, "設定変更 TTS enabled=" + enabled);
        if (settingsRepository != null) settingsRepository.setTtsEnabled(enabled);
        if (ttsConnection != null) ttsConnection.setEnabled(enabled);
        // ローカル WebSocket に配信
        LocalWebSocketServer.getInstance().broadcastTtsStatus(enabled);
    }

    public void setNotificationEnabled(boolean enabled) {
        Log.d(TAG, "設定変更 notification enabled=" + enabled);
        if (settingsRepository != null) settingsRepository.setNotificationEnabled(enabled);
        if (notifConnection != null) notifConnection.setEnabled(enabled);
        // ローカル WebSocket に配信
        LocalWebSocketServer.getInstance().broadcastNotifStatus(enabled);
    }

    public void setTtsCodeEnabled(int code, boolean enabled) {
        Log.d(TAG, "設定変更 TTS code=" + code + " enabled=" + enabled);
        if (settingsRepository != null) settingsRepository.setTtsCodeEnabled(code, enabled);
    }

    public void setNotificationCodeEnabled(int code, boolean enabled) {
        Log.d(TAG, "設定変更 notification code=" + code + " enabled=" + enabled);
        if (settingsRepository != null) settingsRepository.setNotificationCodeEnabled(code, enabled);
    }

    public void resetCodeSettings() {
        Log.d(TAG, "設定変更 code settings reset");
        if (settingsRepository != null) settingsRepository.resetCodeSettings();
    }

    public void setTtsSpeechRate(float rate) {
        rate = sanitizeTtsValue(rate);
        Log.d(TAG, "設定変更 TTS speechRate=" + rate);
        if (settingsRepository != null) settingsRepository.setTtsSpeechRate(rate);
        if (ttsConnection != null) ttsConnection.setSpeechRate(rate);
    }

    public void setTtsPitch(float pitch) {
        pitch = sanitizeTtsValue(pitch);
        Log.d(TAG, "設定変更 TTS pitch=" + pitch);
        if (settingsRepository != null) settingsRepository.setTtsPitch(pitch);
        if (ttsConnection != null) ttsConnection.setPitch(pitch);
    }

    public boolean isTtsEnabled() {
        return ttsConnection != null && ttsConnection.isEnabled();
    }

    public boolean isNotificationEnabled() {
        return notifConnection != null && notifConnection.isEnabled();
    }

    private static float sanitizeTtsValue(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1.0f;
        return Math.max(0.5f, Math.min(2.0f, value));
    }

    // ──────────────────────────────────────────────
    //  ヘルパー
    // ──────────────────────────────────────────────


    // ──────────────────────────────────────────────
    //  フォアグラウンド通知ヘルパー（常駐通知専用）
    // ──────────────────────────────────────────────

    private void createForegroundChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) {
            Log.w(TAG, "Foreground通知チャンネル作成失敗: NotificationManagerがnull");
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_FOREGROUND, "地震情報WebSocket接続",
                NotificationManager.IMPORTANCE_LOW  // 音なし・常駐用
        );
        ch.setDescription("P2PQuake WebSocket接続を維持します");
        nm.createNotificationChannel(ch);
        Log.d(TAG, "Foreground通知チャンネル初期化完了");
    }

    private Notification buildForegroundNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_FOREGROUND)
                .setContentTitle("KoiYure 地震情報")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void updateForegroundNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) {
            Log.w(TAG, "Foreground通知更新失敗: NotificationManagerがnull");
            return;
        }
        nm.notify(NOTIF_FOREGROUND_ID, buildForegroundNotification(text));
        Log.d(TAG, "Foreground通知更新 textLength=" + (text == null ? 0 : text.length()));
    }
}
