package com.example.koiyurepublic;

import org.json.JSONException;
import org.json.JSONObject;

/** P2PQuake のコード別配信可否と、特殊イベントの扱いを決める。 */
public final class EarthquakePolicy {
    private final SettingsRepository settings;

    public EarthquakePolicy(SettingsRepository settings) {
        this.settings = settings;
    }

    public int extractCode(String json) throws JSONException {
        if (json == null) {
            throw new JSONException("JSON is null");
        }
        return new JSONObject(json).optInt("code", -1);
    }

    public boolean shouldNotify(int code) {
        return settings.isNotificationEnabled() && settings.isNotificationEnabledForCode(code);
    }

    public boolean shouldSpeak(int code) {
        return settings.isTtsEnabled() && settings.isTtsEnabledForCode(code);
    }

    public boolean shouldSkipTts(int code, String fullMessage) {
        return code == 555 || (code == 9611 && fullMessage.contains("非表示"));
    }

    public boolean isTsunamiCancellation(int code, String briefMessage) {
        return code == 552 && briefMessage.contains("解除");
    }

    public boolean isEewCancellation(int code, String briefMessage) {
        return code == 556 && briefMessage.contains("取消");
    }

    public boolean isInterruptTts(int code) {
        return code == 556 || code == 554;
    }

    public String titleForCode(int code) {
        switch (code) {
            case 551: return "地震情報";
            case 552: return "津波予報";
            case 554: return "緊急地震速報 検出";
            case 555: return "ピア情報";
            case 556: return "⚡ 緊急地震速報（警報）";
            case 561: return "地震感知情報";
            case 9611: return "地震感知 解析結果";
            default: return "KoiYure";
        }
    }
}
