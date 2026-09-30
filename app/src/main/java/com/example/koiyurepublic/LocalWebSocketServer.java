package com.example.koiyurepublic;

import android.util.Log;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * LocalWebSocketServer
 *
 * Android アプリ内で動作する WebSocket サーバー。
 * WebView（JavaScript）との超高速双方向通信を実現。
 *
 * 機能:
 *   - Service 状態の即座更新
 *   - WebSocket 接続状態の即座更新
 *   - 地震データの配信
 *   - TTS/通知設定の同期
 *
 * ポート: 9001 (localhost)
 * URL: ws://127.0.0.1:9001
 */
public class LocalWebSocketServer extends WebSocketServer {
    private static final String TAG = "LocalWebSocketServer";
    private static final int PORT = 9001;

    // 接続中の全クライアント
    private static final CopyOnWriteArraySet<WebSocket> connectedClients = new CopyOnWriteArraySet<>();

    // 新しく接続したWebViewへ現在状態を再送するためのスナップショット
    private volatile boolean serviceRunning = false;
    private volatile boolean p2pConnected = false;
    private volatile boolean p2pWillReconnect = false;
    private volatile boolean ttsEnabled = true;
    private volatile boolean notificationEnabled = true;

    private static LocalWebSocketServer instance;

    private LocalWebSocketServer() {
        super(new InetSocketAddress("127.0.0.1", PORT));
    }

    public static synchronized LocalWebSocketServer getInstance() {
        if (instance == null) {
            instance = new LocalWebSocketServer();
        }
        return instance;
    }

    // ──────────────────────────────────────────────
    //  WebSocket Server Callbacks
    // ──────────────────────────────────────────────

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connectedClients.add(conn);
        Log.d(TAG, "クライアント接続: " + conn.getRemoteSocketAddress()
              + " (total: " + connectedClients.size() + ")");
        sendCurrentState(conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connectedClients.remove(conn);
        Log.d(TAG, "クライアント切断: " + conn.getRemoteSocketAddress()
              + " (total: " + connectedClients.size() + ")");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "受信 length=" + (message == null ? 0 : message.length())
                + " client=" + (conn == null ? "null" : conn.getRemoteSocketAddress()));

        // JavaScript からのメッセージ処理
        // 例: { "type": "getTtsStatus" } → { "type": "ttsStatus", "enabled": true }
        try {
            // JSON パース は SpinalCord で処理可能
            // 現在は表示用ログのみ
        } catch (Exception e) {
            Log.e(TAG, "メッセージ処理エラー length=" + (message == null ? 0 : message.length()), e);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String remoteAddress = (conn != null && conn.getRemoteSocketAddress() != null)
                ? conn.getRemoteSocketAddress().toString()
                : "server";
        if (conn == null) {
            synchronized (this) {
                isRunning = false;
                startRequested = false;
            }
        }
        Log.e(TAG, "エラー: " + remoteAddress, ex);
    }

    @Override
    public void onStart() {
        synchronized (this) {
            isRunning = true;
            startRequested = false;
        }
        Log.d(TAG, "ローカル WebSocket サーバー起動: ws://127.0.0.1:" + PORT);
    }

    // ──────────────────────────────────────────────
    //  ブロードキャスト（全クライアントに配信）
    // ──────────────────────────────────────────────

    /**
     * Service 状態を全クライアントに配信
     * メッセージ形式: { "type": "serviceStateChanged", "running": true }
     */
    public synchronized void broadcastServiceState(boolean running) {
        serviceRunning = running;
        String msg = "{\"type\":\"serviceStateChanged\",\"running\":" + running + "}";
        sendToAllClients(msg);
        Log.d(TAG, "配信: " + msg);
    }

    /**
     * WebSocket 接続状態を全クライアントに配信
     * メッセージ形式: { "type": "connectionStateChanged", "connected": true, "willReconnect": false }
     */
    public synchronized void broadcastConnectionState(boolean connected, boolean willReconnect) {
        p2pConnected = connected;
        p2pWillReconnect = willReconnect;
        String msg = "{\"type\":\"connectionStateChanged\",\"connected\":" + connected
                   + ",\"willReconnect\":" + willReconnect + "}";
        sendToAllClients(msg);
        Log.d(TAG, "配信: " + msg);
    }

    /**
     * 地震データを全クライアントに配信
     * メッセージ形式: { "type": "earthquakeData", "data": {...} }
     */
    public synchronized void broadcastEarthquakeData(String jsonData) {
        String msg = "{\"type\":\"earthquakeData\",\"data\":" + jsonData + "}";
        sendToAllClients(msg);
        Log.d(TAG, "配信: 地震データ(" + jsonData.length() + " bytes)");
    }

    /**
     * TTS 有効状態を全クライアントに配信
     * メッセージ形式: { "type": "ttsStatus", "enabled": true }
     */
    public synchronized void broadcastTtsStatus(boolean enabled) {
        ttsEnabled = enabled;
        String msg = "{\"type\":\"ttsStatus\",\"enabled\":" + enabled + "}";
        sendToAllClients(msg);
        Log.d(TAG, "配信: " + msg);
    }

    /**
     * 通知 有効状態を全クライアントに配信
     * メッセージ形式: { "type": "notifStatus", "enabled": true }
     */
    public synchronized void broadcastNotifStatus(boolean enabled) {
        notificationEnabled = enabled;
        String msg = "{\"type\":\"notifStatus\",\"enabled\":" + enabled + "}";
        sendToAllClients(msg);
        Log.d(TAG, "配信: " + msg);
    }

    /**
     * 内部用：全クライアントにメッセージを送信する。
     * 親クラスの broadcast() とのメソッド名衝突を避けるため sendToAllClients() に変更。
     */
    private void sendToAllClients(String msg) {
        for (WebSocket client : connectedClients) {
            sendToClient(client, msg);
        }
    }

    /**
     * 接続直後に現在の状態をこのクライアントだけへ送信する。
     * onOpen()がService側の初期同期より先に実行されても状態を失わない。
     */
    private synchronized void sendCurrentState(WebSocket client) {
        sendToClient(client, "{\"type\":\"serviceStateChanged\",\"running\":" + serviceRunning + "}");
        sendToClient(client, "{\"type\":\"connectionStateChanged\",\"connected\":" + p2pConnected
                + ",\"willReconnect\":" + p2pWillReconnect + "}");
        sendToClient(client, "{\"type\":\"ttsStatus\",\"enabled\":" + ttsEnabled + "}");
        sendToClient(client, "{\"type\":\"notifStatus\",\"enabled\":" + notificationEnabled + "}");
    }

    private void sendToClient(WebSocket client, String msg) {
        try {
            if (client != null && client.isOpen()) {
                client.send(msg);
            }
        } catch (Exception e) {
            Log.e(TAG, "配信失敗 length=" + (msg == null ? 0 : msg.length()), e);
        }
    }

    // ──────────────────────────────────────────────
    //  ライフサイクル管理
    // ──────────────────────────────────────────────

    private boolean isRunning = false;
    private boolean startRequested = false;

    /**
     * サーバー起動
     */
    public synchronized void start() {
        if (isRunning || startRequested) {
            Log.d(TAG, "サーバー開始要求を無視: 既に起動中/起動処理中 running="
                    + isRunning + ", starting=" + startRequested);
            return;
        }
        try {
            startRequested = true;
            Log.d(TAG, "サーバー開始要求: ws://127.0.0.1:" + PORT);
            super.start();
        } catch (Exception e) {
            startRequested = false;
            Log.e(TAG, "サーバー起動エラー", e);
        }
    }

    /**
     * サーバー停止
     */
    public synchronized void stop() {
        try {
            connectedClients.clear();
            if (isRunning || startRequested) super.stop();
            isRunning = false;
            startRequested = false;
            Log.d(TAG, "サーバー停止");
        } catch (Exception e) {
            Log.e(TAG, "サーバー停止エラー", e);
        }
    }
}
