package com.example.koiyurepublic;

import android.util.Log;

/** 通知可否の判定と低レベル通知エンジンの呼び出しを担う。 */
public final class NotificationDispatcher {
    private static final String TAG = "NotificationDispatcher";
    private final NotifiConnection connection;
    private final EarthquakePolicy policy;

    public NotificationDispatcher(NotifiConnection connection, EarthquakePolicy policy) {
        this.connection = connection;
        this.policy = policy;
    }

    public void dispatch(String json) {
        int code;
        try {
            code = policy.extractCode(json);
        } catch (org.json.JSONException e) {
            Log.w(TAG, "通知対象のコード解析に失敗", e);
            return;
        }
        String brief = P2PConverts.toBriefMessage(json);
        Log.d(TAG, "通知判定 code=" + code + " enabled=" + policy.shouldNotify(code));
        if (!policy.shouldNotify(code)) {
            Log.d(TAG, "通知スキップ code=" + code);
            return;
        }
        if (policy.isTsunamiCancellation(code, brief)) {
            connection.cancelTsunami();
        } else if (policy.isEewCancellation(code, brief)) {
            connection.cancelEEW();
        }
        connection.notify(code, policy.titleForCode(code), brief);
    }
}
