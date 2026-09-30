package com.example.koiyurepublic;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/** SharedPreferences の設定キーと既定値を一元管理する。 */
public final class SettingsRepository {
    private static final String TAG = "SettingsRepository";
    private static final String PREFS = "koiyure_settings";
    private static final String KEY_TTS_ENABLED = "tts_enabled";
    private static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";
    private static final String KEY_TTS_RATE = "tts_speech_rate";
    private static final String KEY_TTS_PITCH = "tts_pitch";
    private static final String KEY_TTS_CODE_PREFIX = "code_tts_";
    private static final String KEY_NOTIFICATION_CODE_PREFIX = "code_notif_";

    private final SharedPreferences preferences;

    public SettingsRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isTtsEnabled() {
        return preferences.getBoolean(KEY_TTS_ENABLED, true);
    }

    public void setTtsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply();
        Log.d(TAG, "TTS設定保存 enabled=" + enabled);
    }

    public boolean isNotificationEnabled() {
        return preferences.getBoolean(KEY_NOTIFICATION_ENABLED, true);
    }

    public void setNotificationEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply();
        Log.d(TAG, "通知設定保存 enabled=" + enabled);
    }

    public float getTtsSpeechRate() {
        return preferences.getFloat(KEY_TTS_RATE, 1.0f);
    }

    public void setTtsSpeechRate(float rate) {
        preferences.edit().putFloat(KEY_TTS_RATE, clampTtsValue(rate)).apply();
    }

    public float getTtsPitch() {
        return preferences.getFloat(KEY_TTS_PITCH, 1.3f);
    }

    public void setTtsPitch(float pitch) {
        preferences.edit().putFloat(KEY_TTS_PITCH, clampTtsValue(pitch)).apply();
    }

    public boolean isTtsEnabledForCode(int code) {
        return preferences.getBoolean(KEY_TTS_CODE_PREFIX + code, true);
    }

    public void setTtsCodeEnabled(int code, boolean enabled) {
        preferences.edit().putBoolean(KEY_TTS_CODE_PREFIX + code, enabled).apply();
        Log.d(TAG, "TTSコード設定保存 code=" + code + " enabled=" + enabled);
    }

    public boolean isNotificationEnabledForCode(int code) {
        return preferences.getBoolean(KEY_NOTIFICATION_CODE_PREFIX + code, true);
    }

    public void setNotificationCodeEnabled(int code, boolean enabled) {
        preferences.edit().putBoolean(KEY_NOTIFICATION_CODE_PREFIX + code, enabled).apply();
        Log.d(TAG, "通知コード設定保存 code=" + code + " enabled=" + enabled);
    }

    public void resetCodeSettings() {
        SharedPreferences.Editor editor = preferences.edit();
        int[] codes = {551, 552, 554, 555, 556, 561, 9611, 1112};
        for (int code : codes) {
            editor.remove(KEY_TTS_CODE_PREFIX + code);
            editor.remove(KEY_NOTIFICATION_CODE_PREFIX + code);
        }
        editor.apply();
        Log.d(TAG, "コード別設定をリセット");
    }

    private static float clampTtsValue(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1.0f;
        return Math.max(0.5f, Math.min(2.0f, value));
    }
}
