package com.example.koiyurepublic;

import android.util.Log;

/** 受信イベントを通知、TTS、LocalWebSocketへ一度ずつ配る。 */
public final class EarthquakeMessageCoordinator {
    private static final String TAG = "EarthquakeCoordinator";
    private final EarthquakePolicy policy;
    private final NotificationDispatcher notifications;
    private final TtsDispatcher tts;

    public EarthquakeMessageCoordinator(EarthquakePolicy policy,
                                         NotificationDispatcher notifications,
                                         TtsDispatcher tts) {
        this.policy = policy;
        this.notifications = notifications;
        this.tts = tts;
    }

    public void handle(String json) {
        int code;
        try {
            code = policy.extractCode(json);
        } catch (org.json.JSONException e) {
            Log.w(TAG, "受信イベントのコード解析に失敗", e);
            code = -1;
        }
        Log.d(TAG, "受信 length=" + (json == null ? 0 : json.length()));
        Log.d(TAG, "メッセージ解析 code=" + code + " イベント処理開始 length="
                + (json == null ? 0 : json.length()));
        notifications.dispatch(json);
        tts.dispatch(json);
        LocalWebSocketServer.getInstance().broadcastEarthquakeData(json);
    }
}
