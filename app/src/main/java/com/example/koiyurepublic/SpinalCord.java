package com.example.koiyurepublic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class SpinalCord extends Service implements P2PQuakeWebSocketClient.Listener {

    private static final String TAG = "SpinalCord";
    private static final String CHANNEL_ID = "koiyure_ws_channel";
    private static final int NOTIF_ID = 1;

    // ──────────────────────────────────────────────
    //  Binder / コールバックインターフェース
    // ──────────────────────────────────────────────

    /** MainActivity が実装してデータを受け取るコールバック */
    public interface UICallback {
        void onEarthquakeMessage(String json);
        void onConnectionStateChanged(boolean connected, boolean willReconnect);
    }

    public class LocalBinder extends Binder {
        public SpinalCord getService() { return SpinalCord.this; }
    }

    private final IBinder binder = new LocalBinder();
    private UICallback uiCallback = null;

    /** MainActivity が onStart() でコールバックを登録 */
    public void setUICallback(UICallback cb) { this.uiCallback = cb; }

    /** MainActivity が onStop() でコールバックを解除 */
    public void clearUICallback() { this.uiCallback = null; }

    // ──────────────────────────────────────────────
    //  WebSocket クライアント
    // ──────────────────────────────────────────────

    private final P2PQuakeWebSocketClient wsClient = new P2PQuakeWebSocketClient();

    // ──────────────────────────────────────────────
    //  Service ライフサイクル
    // ──────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("接続中…"));
        P2PQuakeWebSocketClient.addListener(this);
        wsClient.connect();
        Log.d(TAG, "Service onCreate — WebSocket接続開始");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // STICKYにしてOSにkillされても自動再起動
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind — コールバック解除");
        uiCallback = null;
        return true; // trueを返すとonRebindが呼ばれるようになる
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind");
    }

    @Override
    public void onDestroy() {
        wsClient.disconnect();
        P2PQuakeWebSocketClient.removeListener(this);
        Log.d(TAG, "Service onDestroy");
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
        // バックグラウンド処理はここに追加（DB保存・プッシュ通知など）
        if (uiCallback != null) uiCallback.onEarthquakeMessage(json);
    }

    // ──────────────────────────────────────────────
    //  通知ヘルパー
    // ──────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "地震情報WebSocket",
                NotificationManager.IMPORTANCE_LOW  // 音なし・常駐用
        );
        channel.setDescription("P2PQuake WebSocket接続を維持します");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("KoiYure 地震情報")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class)
                .notify(NOTIF_ID, buildNotification(text));
    }
}