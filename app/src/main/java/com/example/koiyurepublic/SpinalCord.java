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

public class SpinalCord extends Service implements P2PQuakeWebSocketClient.Listener {

    private static final String TAG = "SpinalCord";
    private static final String CHANNEL_ID = "koiyure_ws_channel";
    private static final int NOTIF_ID = 1;

    public static final long WATCHDOG_INTERVAL_MS = 60_000L;

    /**
     * Serviceが現在動いているかをHTML/MainActivity側から確認するためのフラグ。
     * onCreate/onDestroyで更新する。
     */
    public static volatile boolean isRunning = false;

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
        Log.d(TAG, "Watchdog scheduled in " + WATCHDOG_INTERVAL_MS + "ms");
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
    //  Binder / コールバックインターフェース
    // ──────────────────────────────────────────────

    public interface UICallback {
        void onEarthquakeMessage(String json);
        void onConnectionStateChanged(boolean connected, boolean willReconnect);
    }

    public class LocalBinder extends Binder {
        public SpinalCord getService() { return SpinalCord.this; }
    }

    private final IBinder binder = new LocalBinder();
    private UICallback uiCallback = null;

    public void setUICallback(UICallback cb) { this.uiCallback = cb; }
    public void clearUICallback() { this.uiCallback = null; }

    // ──────────────────────────────────────────────
    //  WebSocket クライアント
    // ──────────────────────────────────────────────

    private final P2PQuakeWebSocketClient wsClient = new P2PQuakeWebSocketClient();

    // ──────────────────────────────────────────────
    //  意図的停止フラグ
    //  stopIntentionally() を呼んでからstopService()することで
    //  onDestroy内の自己再起動を抑制する
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
        isRunning = true;
        isIntentionallyStopped = false; // 再起動時にフラグをリセット
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("接続中…"));
        acquireWakeLock();
        scheduleWatchdog();
        P2PQuakeWebSocketClient.addListener(this);
        wsClient.connect();
        Log.d(TAG, "Service onCreate — WebSocket接続開始");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        scheduleWatchdog();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        uiCallback = null;
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind");
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        wsClient.disconnect();
        P2PQuakeWebSocketClient.removeListener(this);
        releaseWakeLock();

        // 意図的な停止でなければ自己再起動（OS killからの復帰）
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
            Log.d(TAG, "ユーザーによる意図的な停止 — 自己再起動しない");
        }

        super.onDestroy();
    }

    // ──────────────────────────────────────────────
    //  P2PQuakeWebSocketClient.Listener
    // ──────────────────────────────────────────────

    @Override
    public void onConnected() {
        Log.d(TAG, "WS接続完了");
        updateNotification("● 接続済み — 地震情報受信中");
        if (uiCallback != null) uiCallback.onConnectionStateChanged(true, false);
    }

    @Override
    public void onDisconnected(boolean willReconnect) {
        Log.d(TAG, "WS切断 willReconnect=" + willReconnect);
        updateNotification(willReconnect ? "○ 切断 — 再接続中…" : "✕ 切断");
        if (uiCallback != null) uiCallback.onConnectionStateChanged(false, willReconnect);
    }

    @Override
    public void onMessage(String json) {
        Log.d(TAG, "受信: " + json.substring(0, Math.min(80, json.length())));
        if (uiCallback != null) uiCallback.onEarthquakeMessage(json);
    }

    // ──────────────────────────────────────────────
    //  通知ヘルパー
    // ──────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "地震情報WebSocket", NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("P2PQuake WebSocket接続を維持します");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("KoiYure 地震情報")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class)
                .notify(NOTIF_ID, buildNotification(text));
    }
}
