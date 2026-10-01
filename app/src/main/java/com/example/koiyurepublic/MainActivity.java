package com.example.koiyurepublic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.GeolocationPermissions;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1002;
    private String pendingGeolocationOrigin;
    private GeolocationPermissions.Callback pendingGeolocationCallback;

    // ──────────────────────────────────────────────
    //  Service Bind
    // ──────────────────────────────────────────────

    private SpinalCord spinalCord = null;
    private boolean bound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SpinalCord.LocalBinder localBinder = (SpinalCord.LocalBinder) service;
            spinalCord = localBinder.getService();
            spinalCord.syncWebViewState();
            bound = true;
            Log.d(TAG, "Service接続完了 component=" + name.getClassName());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            spinalCord = null;
            Log.w(TAG, "Service接続切断 component=" + name.getClassName());
        }
    };

    // ──────────────────────────────────────────────
    //  Activity ライフサイクル
    // ──────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate savedInstanceState=" + (savedInstanceState != null));
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);

        // JavascriptInterface名: "AndroidBridge"（MainScript.js の Bridge クラスに対応）
        webView.addJavascriptInterface(new JsBridge(), "AndroidBridge");
        webView.getSettings().setGeolocationEnabled(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                if (!"file:///android_asset".equals(origin)) {
                    callback.invoke(origin, false, false);
                    return;
                }
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                    return;
                }
                pendingGeolocationOrigin = origin;
                pendingGeolocationCallback = callback;
                requestLocationPermissionIfNeeded();
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "WebViewページ読み込み完了 urlLength=" + (url == null ? 0 : url.length()));
            }
        });
        webView.loadUrl("file:///android_asset/Maindex.html");
        requestNotificationPermissionIfNeeded();
        requestLocationPermissionIfNeeded();

        // ═══════════════════════════════════════
        // 🟢 アプリ起動時に Service を自動開始
        // ═══════════════════════════════════════
        if (!SpinalCord.isServiceRunning()) {
            Log.d(TAG, "Service not running → auto-start");
            Intent serviceIntent = new Intent(this, SpinalCord.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }

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
        Log.d(TAG, "onStart bound=" + bound);
        Intent serviceIntent = new Intent(this, SpinalCord.class);
        // 必ず Service が起動していることを確認
        if (!SpinalCord.isServiceRunning()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
        // bind する
        if (!bound) {
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop bound=" + bound);
        if (bound) {
            unbindService(serviceConnection);
            bound = false;
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy bound=" + bound);
        mainHandler.removeCallbacksAndMessages(null);
        if (bound) {
            unbindService(serviceConnection);
            bound = false;
            spinalCord = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
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
                Log.d("JsBridge", "stopBackground called from JS");

                if (spinalCord != null) {
                    spinalCord.stopIntentionally();
                }

                if (bound) {
                    unbindService(serviceConnection);
                    bound = false;
                    spinalCord = null;
                }

                SpinalCord.cancelWatchdog(MainActivity.this);
                Intent serviceIntent = new Intent(MainActivity.this, SpinalCord.class);
                MainActivity.this.stopService(serviceIntent);
            });
        }

        /** Service起動。HTML側: AndroidBridge.startBackground() */
        @JavascriptInterface
        public void startBackground() {
            mainHandler.post(() -> {
                Log.d("JsBridge", "startBackground called from JS");

                if (SpinalCord.isServiceRunning()) {
                    // Already running
                    if (!bound) {
                        Intent serviceIntent = new Intent(MainActivity.this, SpinalCord.class);
                        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
                    }
                    return;
                }

                Intent serviceIntent = new Intent(MainActivity.this, SpinalCord.class);
                MainActivity.this.startForegroundService(serviceIntent);

                mainHandler.postDelayed(() -> {
                    if (!bound) bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
                }, 500);
            });
        }

        /** Service実行中かを返す。HTML側: AndroidBridge.isServiceRunning() */
        @JavascriptInterface
        public boolean isServiceRunning() {
            return SpinalCord.isRunning;
        }

        /**
         * Local WebSocketの実際の接続先を返す。
         * 9001番ポートが使用中の場合はLocalWebSocketServerが別ポートへ退避する。
         */
        @JavascriptInterface
        public String getLocalWebSocketUrl() {
            String url = LocalWebSocketServer.getWebSocketUrl();
            Log.d("JsBridge", "getLocalWebSocketUrl=" + url);
            return url;
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
                if (spinalCord != null) {
                    spinalCord.setTtsEnabled(enabled);
                } else {
                    new SettingsRepository(MainActivity.this).setTtsEnabled(enabled);
                }
                Log.d("JsBridge", "setTtsEnabled=" + enabled);
            });
        }

        /**
         * TTS が有効かを返す。
         * HTML側: AndroidBridge.isTtsEnabled()
         */
        @JavascriptInterface
        public boolean isTtsEnabled() {
            return spinalCord != null
                    ? spinalCord.isTtsEnabled()
                    : new SettingsRepository(MainActivity.this).isTtsEnabled();
        }

        /**
         * TTS の読み上げ速度を設定する（0.5〜2.0 推奨）。
         * HTML側: AndroidBridge.setTtsSpeechRate(1.0)
         */
        @JavascriptInterface
        public void setTtsSpeechRate(float rate) {
            mainHandler.post(() -> {
                if (spinalCord != null) {
                    spinalCord.setTtsSpeechRate(rate);
                } else {
                    new SettingsRepository(MainActivity.this).setTtsSpeechRate(rate);
                }
            });
        }

        /**
         * TTS の音程を設定する（0.5〜2.0 推奨）。
         * HTML側: AndroidBridge.setTtsPitch(1.3)
         */
        @JavascriptInterface
        public void setTtsPitch(float pitch) {
            mainHandler.post(() -> {
                if (spinalCord != null) {
                    spinalCord.setTtsPitch(pitch);
                } else {
                    new SettingsRepository(MainActivity.this).setTtsPitch(pitch);
                }
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
                if (spinalCord != null) {
                    spinalCord.setNotificationEnabled(enabled);
                } else {
                    new SettingsRepository(MainActivity.this).setNotificationEnabled(enabled);
                }
                Log.d("JsBridge", "setNotificationEnabled=" + enabled);
            });
        }

        @JavascriptInterface
        public void setTtsCodeEnabled(int code, boolean enabled) {
            mainHandler.post(() -> {
                if (spinalCord != null) spinalCord.setTtsCodeEnabled(code, enabled);
            });
        }

        @JavascriptInterface
        public void setNotificationCodeEnabled(int code, boolean enabled) {
            mainHandler.post(() -> {
                if (spinalCord != null) spinalCord.setNotificationCodeEnabled(code, enabled);
            });
        }

        @JavascriptInterface
        public void resetCodeSettings() {
            mainHandler.post(() -> {
                if (spinalCord != null) spinalCord.resetCodeSettings();
            });
        }

        /**
         * プッシュ通知が有効かを返す。
         * HTML側: AndroidBridge.isNotificationEnabled()
         */
        @JavascriptInterface
        public boolean isNotificationEnabled() {
            return spinalCord != null
                    ? spinalCord.isNotificationEnabled()
                    : new SettingsRepository(MainActivity.this).isNotificationEnabled();
        }

        // ──────────────────────────────────────────
        //  デバッグ
        // ──────────────────────────────────────────

        /** JSからAndroidのLogcatにログを出力する。HTML側: AndroidBridge.log("msg") */
        @JavascriptInterface
        public void log(String message) {
            Log.d("WebView/JS", message);
        }
    }

    // ──────────────────────────────────────────────
    //  ヘルパー
    // ──────────────────────────────────────────────

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Log.d(TAG, "通知権限を要求");
        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) return;
        requestPermissions(new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
        }, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE
                || pendingGeolocationCallback == null
                || pendingGeolocationOrigin == null) {
            return;
        }
        boolean granted = hasLocationPermission();
        pendingGeolocationCallback.invoke(pendingGeolocationOrigin, granted, false);
        pendingGeolocationCallback = null;
        pendingGeolocationOrigin = null;
    }
}
