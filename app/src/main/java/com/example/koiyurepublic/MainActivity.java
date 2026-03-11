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
            runJs("window.onServiceStateChanged && window.onServiceStateChanged(true)");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            spinalCord = null;
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

        // JavascriptInterface名: "AndroidBridge"（MainScript.js の Bridge クラスに対応）
        webView.addJavascriptInterface(new JsBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
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
        Intent intent = new Intent(this, SpinalCord.class);
        if (SpinalCord.isRunning) {
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
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
        // JSON文字列をJS文字列リテラルとして安全にエスケープ
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
    //  JsBridge — WebViewからJavaへのコールバック
    //  JavascriptInterface名: "AndroidBridge"
    // ──────────────────────────────────────────────

    private class JsBridge {

        @JavascriptInterface
        public void notifyReady() { /* ページ準備完了通知（将来用） */ }

        /** Service停止。HTML側: AndroidBridge.stopBackground() */
        @JavascriptInterface
        public void stopBackground() {
            mainHandler.post(() -> {
                android.util.Log.d("JsBridge", "stopBackground called from JS");

                if (spinalCord != null) spinalCord.stopIntentionally();

                if (bound) {
                    if (spinalCord != null) spinalCord.clearUICallback();
                    unbindService(connection);
                    bound = false;
                    spinalCord = null;
                }

                SpinalCord.cancelWatchdog(MainActivity.this);

                Intent intent = new Intent(MainActivity.this, SpinalCord.class);
                MainActivity.this.stopService(intent);

                runJs("window.onServiceStateChanged && window.onServiceStateChanged(false)");
            });
        }

        /** Service起動。HTML側: AndroidBridge.startBackground() */
        @JavascriptInterface
        public void startBackground() {
            mainHandler.post(() -> {
                android.util.Log.d("JsBridge", "startBackground called from JS");

                Intent intent = new Intent(MainActivity.this, SpinalCord.class);
                MainActivity.this.startForegroundService(intent);

                mainHandler.postDelayed(() -> {
                    if (!bound) bindService(intent, connection, Context.BIND_AUTO_CREATE);
                }, 500);
            });
        }

        /** Service実行中かを返す。HTML側: AndroidBridge.isServiceRunning() */
        @JavascriptInterface
        public boolean isServiceRunning() {
            return SpinalCord.isRunning;
        }

        // ──────────────────────────────────────────
        //  TTS 制御
        // ──────────────────────────────────────────

        /**
         * 読み上げ（TTS）をON/OFFする。
         * HTML側: AndroidBridge.setTtsEnabled(true/false)
         */
        @JavascriptInterface
        public void setTtsEnabled(boolean enabled) {
            mainHandler.post(() -> {
                if (spinalCord != null) spinalCord.setTtsEnabled(enabled);
                android.util.Log.d("JsBridge", "setTtsEnabled=" + enabled);
            });
        }

        /**
         * TTS が有効かを返す。
         * HTML側: AndroidBridge.isTtsEnabled()
         */
        @JavascriptInterface
        public boolean isTtsEnabled() {
            return spinalCord != null && spinalCord.isTtsEnabled();
        }

        /**
         * TTS の読み上げ速度を設定する（0.5〜2.0 推奨）。
         * HTML側: AndroidBridge.setTtsSpeechRate(1.0)
         */
        @JavascriptInterface
        public void setTtsSpeechRate(float rate) {
            mainHandler.post(() -> {
                if (spinalCord != null) spinalCord.setTtsSpeechRate(rate);
            });
        }

        /**
         * TTS の音程を設定する（0.5〜2.0 推奨）。
         * HTML側: AndroidBridge.setTtsPitch(1.3)
         */
        @JavascriptInterface
        public void setTtsPitch(float pitch) {
            mainHandler.post(() -> {
                if (spinalCord != null) spinalCord.setTtsPitch(pitch);
            });
        }

        // ──────────────────────────────────────────
        //  通知 制御
        // ──────────────────────────────────────────

        /**
         * プッシュ通知をON/OFFする。
         * HTML側: AndroidBridge.setNotificationEnabled(true/false)
         */
        @JavascriptInterface
        public void setNotificationEnabled(boolean enabled) {
            mainHandler.post(() -> {
                if (spinalCord != null) spinalCord.setNotificationEnabled(enabled);
                android.util.Log.d("JsBridge", "setNotificationEnabled=" + enabled);
            });
        }

        /**
         * プッシュ通知が有効かを返す。
         * HTML側: AndroidBridge.isNotificationEnabled()
         */
        @JavascriptInterface
        public boolean isNotificationEnabled() {
            return spinalCord != null && spinalCord.isNotificationEnabled();
        }

        // ──────────────────────────────────────────
        //  デバッグ
        // ──────────────────────────────────────────

        /** JSからAndroidのLogcatにログを出力する。HTML側: AndroidBridge.log("msg") */
        @JavascriptInterface
        public void log(String message) {
            android.util.Log.d("WebView/JS", message);
        }
    }

    // ──────────────────────────────────────────────
    //  ヘルパー
    // ──────────────────────────────────────────────

    /** UIスレッドでJSを実行する */
    private void runJs(String js) {
        mainHandler.post(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }
}