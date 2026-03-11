package com.example.koiyurepublic;

import android.annotation.SuppressLint;
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
import android.os.PowerManager;
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
 *     → UICallback.onEarthquakeMessage() → MainActivity → WebView(JS)
 */
public class SpinalCord extends Service implements P2PQuakeWebSocketClient.Listener {

    private static final String TAG = "SpinalCord";

    // フォアグラウンド通知（常駐用）は NotifiConnection とは別チャンネルで管理
    private static final String CHANNEL_FG  = "koiyure_ws_channel";
    private static final int    NOTIF_FG_ID = 1;

    public static final long WATCHDOG_INTERVAL_MS = 60_000L;

    /** MainActivity 側から現在の起動状態を確認するためのフラグ */
    public static volatile boolean isRunning = false;

    // ──────────────────────────────────────────────
    //  子コンポーネント
    // ──────────────────────────────────────────────

    private final P2PQuakeWebSocketClient wsClient = new P2PQuakeWebSocketClient();
    private TTSConnection     tts     = null;
    private NotifiConnection  notifi  = null;

    // ──────────────────────────────────────────────
    //  WakeLock
    // ──────────────────────────────────────────────

    private PowerManager.WakeLock wakeLock = null;

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KoiYure:SpinalCordLock");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
        Log.d(TAG, "WakeLock acquired");
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released");
        }
    }

    // ──────────────────────────────────────────────
    //  Watchdog AlarmManager
    // ──────────────────────────────────────────────

    @SuppressLint("ScheduleExactAlarm")
    private void scheduleWatchdog() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getWatchdogPendingIntent(this);
        am.cancel(pi);
        am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                pi
        );
    }

    public static void cancelWatchdog(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
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

    // ──────────────────────────────────────────────
    //  Binder / UICallback
    // ──────────────────────────────────────────────

    public interface UICallback {
        void onEarthquakeMessage(String json);
        void onConnectionStateChanged(boolean connected, boolean willReconnect);
    }

    public class LocalBinder extends Binder {
        public SpinalCord getService() { return SpinalCord.this; }
    }

    private final IBinder binder     = new LocalBinder();
    private UICallback    uiCallback = null;

    public void setUICallback(UICallback cb) { this.uiCallback = cb; }
    public void clearUICallback()            { this.uiCallback = null; }

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
        isRunning              = true;
        isIntentionallyStopped = false;

        // ① フォアグラウンド通知（常駐用）を先に立てる
        createForegroundChannel();
        startForeground(NOTIF_FG_ID, buildForegroundNotification("接続中…"));

        // ② WakeLock
        acquireWakeLock();

        // ③ Watchdog
        scheduleWatchdog();

        // ④ 子コンポーネント初期化
        EpspArea.init(this);          // 地域コードCSVを読み込む
        tts    = new TTSConnection(this);
        notifi = new NotifiConnection(this);

        // ⑤ こいしちゃんらしい高めの声に設定（お好みで調整）
        tts.setSpeechRate(1.0f);
        tts.setPitch(1.3f);

        // ⑥ WebSocket 接続
        P2PQuakeWebSocketClient.addListener(this);
        wsClient.connect();

        Log.d(TAG, "SpinalCord onCreate 完了");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        scheduleWatchdog();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public boolean onUnbind(Intent intent) {
        uiCallback = null;
        return true;
    }

    @Override
    public void onRebind(Intent intent) { Log.d(TAG, "onRebind"); }

    @Override
    public void onDestroy() {
        isRunning = false;

        // 子コンポーネントを先に解放
        if (tts    != null) { tts.shutdown();  tts    = null; }
        if (notifi != null) {                  notifi = null; }

        wsClient.disconnect();
        P2PQuakeWebSocketClient.removeListener(this);
        releaseWakeLock();

        // 意図的な停止でなければ自己再起動
        if (!isIntentionallyStopped) {
            Intent restartIntent = new Intent(getApplicationContext(), SpinalCord.class);
            PendingIntent pi = PendingIntent.getService(
                    getApplicationContext(), 1, restartIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, pi);
            Log.d(TAG, "自己再起動をスケジュール");
        } else {
            Log.d(TAG, "意図的停止 — 再起動しない");
        }

        super.onDestroy();
    }

    // ──────────────────────────────────────────────
    //  P2PQuakeWebSocketClient.Listener
    // ──────────────────────────────────────────────

    @Override
    public void onConnected() {
        Log.d(TAG, "WS接続完了");
        updateForegroundNotification("● 接続済み — 地震情報受信中");
        if (uiCallback != null) uiCallback.onConnectionStateChanged(true, false);
    }

    @Override
    public void onDisconnected(boolean willReconnect) {
        Log.d(TAG, "WS切断 willReconnect=" + willReconnect);
        updateForegroundNotification(willReconnect ? "○ 切断 — 再接続中…" : "✕ 切断");
        if (uiCallback != null) uiCallback.onConnectionStateChanged(false, willReconnect);
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
     *   5. UIコールバック → MainActivity → WebView
     */
    @Override
    public void onMessage(String json) {
        Log.d(TAG, "受信: " + json.substring(0, Math.min(80, json.length())));

        // --- コード取得 ---
        int code = extractCode(json);

        // --- 短文変換 ---
        String brief = P2PConverts.toBriefMessage(json);
        String title  = codeToTitle(code);

        // --- 通知 ---
        if (notifi != null) {
            // 津波解除 / EEW取消は既存通知をキャンセル
            if (code == 552 && brief.contains("解除")) {
                notifi.cancelTsunami();
            } else if (code == 556 && brief.contains("取消")) {
                notifi.cancelEEW();
            }
            notifi.notify(code, title, brief);
        }

        // --- 読み上げ ---
        if (tts != null) {
            String fullText = P2PConverts.toFullMessage(json);
            // EEW・EEW検出は割り込み読み上げ
            if (code == 556 || code == 554) {
                tts.speakNow(fullText);
            } else {
                tts.speak(fullText);
            }
        }

        // --- UIコールバック ---
        if (uiCallback != null) uiCallback.onEarthquakeMessage(json);
    }

    // ──────────────────────────────────────────────
    //  外部から TTS / 通知の設定を変更するメソッド
    //  MainActivityのJsBridgeや設定画面から呼ぶ
    // ──────────────────────────────────────────────

    public void setTtsEnabled(boolean enabled) {
        if (tts != null) tts.setEnabled(enabled);
    }

    public void setNotificationEnabled(boolean enabled) {
        if (notifi != null) notifi.setEnabled(enabled);
    }

    public void setTtsSpeechRate(float rate) {
        if (tts != null) tts.setSpeechRate(rate);
    }

    public void setTtsPitch(float pitch) {
        if (tts != null) tts.setPitch(pitch);
    }

    public boolean isTtsEnabled() {
        return tts != null && tts.isEnabled();
    }

    public boolean isNotificationEnabled() {
        return notifi != null && notifi.isEnabled();
    }

    // ──────────────────────────────────────────────
    //  ヘルパー
    // ──────────────────────────────────────────────

    /** JSON から code フィールドだけを手早く取り出す（JSONObject生成のコスト削減） */
    private static int extractCode(String json) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            return o.optInt("code", -1);
        } catch (Exception e) {
            return -1;
        }
    }

    /** コード → 通知タイトル文字列 */
    private static String codeToTitle(int code) {
        switch (code) {
            case 551:  return "地震情報";
            case 552:  return "津波予報";
            case 554:  return "緊急地震速報 検出";
            case 555:  return "ピア情報";
            case 556:  return "⚡ 緊急地震速報（警報）";
            case 561:  return "地震感知情報";
            case 9611: return "地震感知 解析結果";
            default:   return "KoiYure";
        }
    }

    // ──────────────────────────────────────────────
    //  フォアグラウンド通知ヘルパー（常駐通知専用）
    // ──────────────────────────────────────────────

    private void createForegroundChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_FG, "地震情報WebSocket接続",
                NotificationManager.IMPORTANCE_LOW  // 音なし・常駐用
        );
        ch.setDescription("P2PQuake WebSocket接続を維持します");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildForegroundNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_FG)
                .setContentTitle("KoiYure 地震情報")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void updateForegroundNotification(String text) {
        getSystemService(NotificationManager.class)
                .notify(NOTIF_FG_ID, buildForegroundNotification(text));
    }
}
