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
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            spinalCord = null;
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
        webView.setWebViewClient(new WebViewClient());
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
        // Serviceを起動しつつBind（既に動いていれば起動はスキップされる）
        Intent intent = new Intent(this, SpinalCord.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
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
        // Serviceのスレッドから呼ばれる可能性があるのでUIスレッドで実行
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

        @JavascriptInterface
        public void requestReconnect() {
            if (bound && spinalCord != null) {
                // 再接続はServiceに移譲
                // SpinalCordにreconnect()メソッドを追加してもよい
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