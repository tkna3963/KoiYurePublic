package com.example.koiyurepublic;

import android.util.Log;

/** TTS可否・特殊除外・割り込みを決め、TTSエンジンへ依頼する。 */
public final class TtsDispatcher {
    private static final String TAG = "TtsDispatcher";
    private final TTSConnection connection;
    private final EarthquakePolicy policy;

    public TtsDispatcher(TTSConnection connection, EarthquakePolicy policy) {
        this.connection = connection;
        this.policy = policy;
    }

    public void dispatch(String json) {
        int code;
        try {
            code = policy.extractCode(json);
        } catch (org.json.JSONException e) {
            Log.w(TAG, "TTS対象のコード解析に失敗", e);
            return;
        }
        if (!policy.shouldSpeak(code)) {
            Log.d(TAG, "TTSスキップ code=" + code);
            return;
        }
        String fullMessage = P2PConverts.toFullMessage(json);
        boolean skip = policy.shouldSkipTts(code, fullMessage);
        Log.d(TAG, "TTS判定 code=" + code + " skip=" + skip
                + " fullLength=" + fullMessage.length());
        if (skip) return;
        if (policy.isInterruptTts(code)) {
            connection.speakNow(fullMessage);
        } else {
            connection.speak(fullMessage);
        }
    }
}
