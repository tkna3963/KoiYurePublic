package com.example.koiyurepublic;

import android.util.Log;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;

public class P2PQuakeWebSocketClient {
    private static final String TAG = "P2PQuakeWS";
    private static final String WS_URL = "wss://api.p2pquake.net/v2/ws";

    private static final long RECONNECT_DELAY_MS = 3000;
    private static final long MAX_RECONNECT_DELAY_MS = 60000;
    private static final int MAX_RECONNECT_ATTEMPTS = -1;

    // ==================== EventBus ====================

    public interface Listener {
        void onMessage(String json);
        void onConnected();
        void onDisconnected(boolean willReconnect);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    private static void broadcastMessage(String json) {
        for (Listener l : listeners) l.onMessage(json);
    }

    private static void broadcastConnected() {
        for (Listener l : listeners) l.onConnected();
    }

    private static void broadcastDisconnected(boolean willReconnect) {
        for (Listener l : listeners) l.onDisconnected(willReconnect);
    }

    // ==================== WebSocket ====================

    private WebSocketClient client;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> reconnectFuture;

    private int reconnectAttempts = 0;
    private boolean manualDisconnect = false;

    public void connect() {
        manualDisconnect = false;
        reconnectAttempts = 0;
        doConnect();
    }

    private void doConnect() {
        try {
            client = new WebSocketClient(new URI(WS_URL)) {
                @Override
                public void onOpen(ServerHandshake h) {
                    Log.d(TAG, "connected");
                    reconnectAttempts = 0;
                    broadcastConnected();
                }

                @Override
                public void onMessage(String message) {
                    broadcastMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "closed: " + reason + " (remote=" + remote + ")");
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "error", e);
                }
            };
            client.setSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());
            client.connect();
        } catch (Exception e) {
            Log.e(TAG, "connect failed", e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (manualDisconnect) {
            Log.d(TAG, "manual disconnect — skip reconnect");
            return;
        }
        if (MAX_RECONNECT_ATTEMPTS >= 0 && reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "max reconnect attempts reached");
            broadcastDisconnected(false);
            return;
        }

        long delay = Math.min(RECONNECT_DELAY_MS * (1L << reconnectAttempts), MAX_RECONNECT_DELAY_MS);
        reconnectAttempts++;
        Log.d(TAG, "reconnecting in " + delay + "ms (attempt " + reconnectAttempts + ")");

        broadcastDisconnected(true);

        reconnectFuture = scheduler.schedule(() -> {
            if (!manualDisconnect) doConnect();
        }, delay, TimeUnit.MILLISECONDS);
    }

    public void disconnect() {
        manualDisconnect = true;
        if (reconnectFuture != null) reconnectFuture.cancel(false);
        if (client != null) client.close();
        scheduler.shutdownNow();
    }
}