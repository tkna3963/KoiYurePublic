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
    private static final CopyOnWriteArraySet<WebSocket> clients = new CopyOnWriteArraySet<>();

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
        clients.add(conn);
        Log.d(TAG, "クライアント接続: " + conn.getRemoteSocketAddress() 
              + " (total: " + clients.size() + ")");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        Log.d(TAG, "クライアント切断: " + conn.getRemoteSocketAddress() 
              + " (total: " + clients.size() + ")");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "受信: " + message);
        
        // JavaScript からのメッセージ処理
        // 例: { "type": "getTtsStatus" } → { "type": "ttsStatus", "enabled": true }
        try {
            // JSON パース は SpinalCord で処理可能
            // 現在は表示用ログのみ
        } catch (Exception e) {
            Log.e(TAG, "メッセージ処理エラー: " + message, e);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(TAG, "エラー: " + conn.getRemoteSocketAddress(), ex);
    }

    @Override
    public void onStart() {
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
        String msg = "{\"type\":\"serviceStateChanged\",\"running\":" + running + "}";
        broadcast(msg);
        Log.d(TAG, "配信: " + msg);
    }

    /**
     * WebSocket 接続状態を全クライアントに配信
     * メッセージ形式: { "type": "connectionStateChanged", "connected": true, "willReconnect": false }
     */
    public synchronized void broadcastConnectionState(boolean connected, boolean willReconnect) {
        String msg = "{\"type\":\"connectionStateChanged\",\"connected\":" + connected 
                   + ",\"willReconnect\":" + willReconnect + "}";
        broadcastToClients(msg);
        Log.d(TAG, "配信: " + msg);
    }

    /**
     * 地震データを全クライアントに配信
     * メッセージ形式: { "type": "earthquakeData", "data": {...} }
     */
    public synchronized void broadcastEarthquakeData(String jsonData) {
        String msg = "{\"type\":\"earthquakeData\",\"data\":" + jsonData + "}";
        broadcastToClients(msg);
        Log.d(TAG, "配信: 地震データ(" + jsonData.length() + " bytes)");
    }

    /**
     * TTS 有効状態を全クライアントに配信
     * メッセージ形式: { "type": "ttsStatus", "enabled": true }
     */
    public synchronized void broadcastTtsStatus(boolean enabled) {
        String msg = "{\"type\":\"ttsStatus\",\"enabled\":" + enabled + "}";
        broadcastToClients(msg);
        Log.d(TAG, "配信: " + msg);
    }

    /**
     * 通知 有効状態を全クライアントに配信
     * メッセージ形式: { "type": "notifStatus", "enabled": true }
     */
    public synchronized void broadcastNotifStatus(boolean enabled) {
        String msg = "{\"type\":\"notifStatus\",\"enabled\":" + enabled + "}";
        broadcastToClients(msg);
        Log.d(TAG, "配信: " + msg);
    }

    /**
     * 内部用：全クライアントにメッセージを配信
     * 親クラスの broadcast() とのメソッド名衝突を避けるため broadcastToClients() に変更
     */
    private void broadcastToClients(String msg) {
        for (WebSocket client : clients) {
            try {
                if (client != null && client.isOpen()) {
                    client.send(msg);
                }
            } catch (Exception e) {
                Log.e(TAG, "配信失敗: " + msg, e);
            }
        }
    }

    // ──────────────────────────────────────────────
    //  ライフサイクル管理
    // ──────────────────────────────────────────────

    /**
     * サーバー起動
     */
    private boolean isRunning = false;

    public synchronized void start() {
        try {
            if (!isRunning) {
                super.start();
                isRunning = true;
                Log.d(TAG, "サーバー開始");
            } else {
                Log.d(TAG, "サーバーは既に起動中");
            }
        } catch (Exception e) {
            Log.e(TAG, "サーバー起動エラー", e);
        }
    }
    /**
     * サーバー停止
     */
    public synchronized void stop() {
        try {
            clients.clear();
            super.stop();
            Log.d(TAG, "サーバー停止");
        } catch (Exception e) {
            Log.e(TAG, "サーバー停止エラー", e);
        }
    }
}
