package com.example.koiyurepublic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity implements SpinalCord.UICallback {

    private WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ──────────────────────────────────────────────
    //  Service Bind
    // ──────────────────────────────────────────────

    private SpinalCord spinalCord = null;
    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SpinalCord.LocalBinder lb = (SpinalCord.LocalBinder) service;
            spinalCord = lb.getService();
            spinalCord.setUICallback(MainActivity.this);
            bound = true;
            // HTML側にService起動状態を通知
            runJs("window.onServiceStateChanged && window.onServiceStateChanged(true)");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            spinalCord = null;
            // HTML側にService停止状態を通知
            runJs("window.onServiceStateChanged && window.onServiceStateChanged(false)");
        }
    };

    // ──────────────────────────────────────────────
    //  Activity ライフサイクル
    // ──────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        webView = findViewById(R.id.webView);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setDomStorageEnabled(true);

        webView.addJavascriptInterface(new JsBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // ページ読み込み完了後に現在のService状態をHTML側に通知
                runJs("window.onServiceStateChanged && window.onServiceStateChanged(" + bound + ")");
            }
        });
        webView.loadUrl("file:///android_asset/Maindex.html");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Serviceが実行中ならBindのみ、停止中なら起動してBind
        Intent intent = new Intent(this, SpinalCord.class);
        if (SpinalCord.isRunning) {
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } else {
            // ユーザーが意図的に停止していた場合は自動起動しない
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            spinalCord.clearUICallback();
            unbindService(connection);
            bound = false;
        }
    }

    // ──────────────────────────────────────────────
    //  SpinalCord.UICallback — Serviceからのデータ受信
    // ──────────────────────────────────────────────

    @Override
    public void onEarthquakeMessage(String json) {
        String escaped = json
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        runJs("window.onEarthquakeData && window.onEarthquakeData('" + escaped + "')");
    }

    @Override
    public void onConnectionStateChanged(boolean connected, boolean willReconnect) {
        runJs("window.onConnectionStateChanged && window.onConnectionStateChanged("
                + connected + "," + willReconnect + ")");
    }

    // ──────────────────────────────────────────────
    //  JavascriptInterface — WebViewからJavaへのコールバック
    // ──────────────────────────────────────────────

    private class JsBridge {

        @JavascriptInterface
        public void notifyReady() {
            // ページの準備完了通知
        }

        /**
         * バックグラウンド実行を停止する。
         * WatchdogAlarmも解除するため、OSによる自動再起動もなくなる。
         * HTML側: AndroidBridge.stopBackground()
         * ※ Context.stopService(Intent) との名前衝突を避けるため stopBackground に命名
         */
        @JavascriptInterface
        public void stopBackground() {
            mainHandler.post(() -> {
                android.util.Log.d("JsBridge", "stopBackground called from JS");

                // 意図的停止フラグを立ててから止める（onDestroy内の自己再起動を抑制）
                if (spinalCord != null) spinalCord.stopIntentionally();

                // Bindを解除
                if (bound) {
                    if (spinalCord != null) spinalCord.clearUICallback();
                    unbindService(connection);
                    bound = false;
                    spinalCord = null;
                }

                // WatchdogAlarmを解除（再起動ループを止める）
                SpinalCord.cancelWatchdog(MainActivity.this);

                // Serviceを停止（Context.stopService(Intent) を明示的に呼ぶ）
                Intent intent = new Intent(MainActivity.this, SpinalCord.class);
                MainActivity.this.stopService(intent);

                // HTML側に停止完了を通知
                runJs("window.onServiceStateChanged && window.onServiceStateChanged(false)");
            });
        }

        /**
         * バックグラウンド実行を開始する。
         * HTML側: AndroidBridge.startBackground()
         * ※ Context.startForegroundService(Intent) との名前衝突を避けるため startBackground に命名
         */
        @JavascriptInterface
        public void startBackground() {
            mainHandler.post(() -> {
                android.util.Log.d("JsBridge", "startBackground called from JS");

                Intent intent = new Intent(MainActivity.this, SpinalCord.class);
                MainActivity.this.startForegroundService(intent);

                // 少し待ってからBind（Service起動完了を待つ）
                mainHandler.postDelayed(() -> {
                    if (!bound) {
                        bindService(intent, connection, Context.BIND_AUTO_CREATE);
                    }
                }, 500);
            });
        }

        /**
         * 現在のService実行状態を返す（同期取得用）。
         * HTML側: const running = AndroidBridge.isServiceRunning()
         */
        @JavascriptInterface
        public boolean isServiceRunning() {
            return SpinalCord.isRunning;
        }

        @JavascriptInterface
        public void requestReconnect() {
            if (bound && spinalCord != null) {
                // 将来: spinalCord.reconnect();
            }
        }

        @JavascriptInterface
        public void log(String message) {
            android.util.Log.d("WebView/JS", message);
        }
    }

    // ──────────────────────────────────────────────
    //  ヘルパー
    // ──────────────────────────────────────────────

    private void runJs(String js) {
        mainHandler.post(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }
}