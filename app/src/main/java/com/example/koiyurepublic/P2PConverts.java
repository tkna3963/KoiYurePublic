package com.example.koiyurepublic;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * P2PConverts
 *
 * P2PQuake WebSocket から受信した JSON を
 * 通知・表示用のメッセージ文字列に変換するユーティリティクラス。
 *
 * ■ 2種類の変換メソッド
 *   toFullMessage(json)  : 受信データをすべて日本語テキストに展開したフルバージョン
 *   toBriefMessage(json) : 通知バナー等に使う1〜2行のコンパクトバージョン
 *
 * ■ 対応コード
 *   551  JMAQuake              地震情報
 *   552  JMATsunami            津波予報
 *   554  EEWDetection          緊急地震速報 発表検出
 *   555  Areapeers             各地域ピア数
 *   556  EEW                   緊急地震速報（警報）
 *   561  Userquake             地震感知情報
 *   9611 UserquakeEvaluation   地震感知情報 解析結果
 */
public class P2PConverts {

    // ================================================================
    //  公開API
    // ================================================================

    /**
     * フルメッセージ変換。
     * 受信データの全フィールドを日本語テキストに変換して返す。
     * TTS読み上げ・詳細表示パネルへの表示に向いている。
     *
     * @param json WebSocketから受信したJSON文字列
     * @return 変換後の日本語メッセージ文字列。解析失敗時は簡易エラーメッセージ。
     */
    public static String toFullMessage(String json) {
        try {
            JSONObject p2pJson = new JSONObject(json);
            int p2pQuakeCode = p2pJson.optInt("code", -1);
            switch (p2pQuakeCode) {
                case 551:  return toFullMessage_JMAQuake(p2pJson);
                case 552:  return toFullMessage_JMATsunami(p2pJson);
                case 554:  return toFullMessage_EEWDetection(p2pJson);
                case 555:  return toFullMessage_Areapeers(p2pJson);
                case 556:  return toFullMessage_EEW(p2pJson);
                case 561:  return toFullMessage_Userquake(p2pJson);
                case 9611: return toFullMessage_UserquakeEvaluation(p2pJson);
                default:   return "【不明な情報】コード: " + p2pQuakeCode;
            }
        } catch (Exception e) {
            return "【解析エラー】" + e.getMessage();
        }
    }

    /**
     * ブリーフメッセージ変換。
     * 通知バナー・ステータスバー・吹き出し表示に向いた短いメッセージを返す。
     *
     * @param json WebSocketから受信したJSON文字列
     * @return 1〜2行程度の短い日本語メッセージ。解析失敗時は簡易エラーメッセージ。
     */
    public static String toBriefMessage(String json) {
        try {
            JSONObject p2pJson = new JSONObject(json);
            int p2pQuakeCode = p2pJson.optInt("code", -1);
            switch (p2pQuakeCode) {
                case 551:  return toBriefMessage_JMAQuake(p2pJson);
                case 552:  return toBriefMessage_JMATsunami(p2pJson);
                case 554:  return toBriefMessage_EEWDetection(p2pJson);
                case 555:  return toBriefMessage_Areapeers(p2pJson);
                case 556:  return toBriefMessage_EEW(p2pJson);
                case 561:  return toBriefMessage_Userquake(p2pJson);
                case 9611: return toBriefMessage_UserquakeEvaluation(p2pJson);
                default:   return "不明な情報を受信しました（コード: " + p2pQuakeCode + "）";
            }
        } catch (Exception e) {
            return "情報の解析に失敗しました";
        }
    }

    // ================================================================
    //  551: JMAQuake — 地震情報
    // ================================================================

    private static String toFullMessage_JMAQuake(JSONObject p2pJson) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("【地震情報】\n");

        JSONObject issue = p2pJson.optJSONObject("issue");
        if (issue != null) {
            sb.append("発表: ").append(issue.optString("source", "不明"))
              .append("  ").append(issue.optString("time", ""))
              .append("\n");
            sb.append("種類: ").append(issueTypeToJapanese(issue.optString("type", "")))
              .append("\n");
            String correct = issue.optString("correct", "None");
            if (!"None".equals(correct)) {
                sb.append("訂正: ").append(correctTypeToJapanese(correct)).append("\n");
            }
        }

        JSONObject earthquake = p2pJson.optJSONObject("earthquake");
        if (earthquake != null) {
            sb.append("発生日時: ").append(earthquake.optString("time", "不明")).append("\n");

            JSONObject hypocenter = earthquake.optJSONObject("hypocenter");
            if (hypocenter != null) {
                String name = hypocenter.optString("name", "");
                if (!name.isEmpty()) sb.append("震源: ").append(name).append("\n");
                double magnitude = hypocenter.optDouble("magnitude", -1);
                if (magnitude >= 0) sb.append("M: ").append(magnitude).append("\n");
                int depth = hypocenter.optInt("depth", -1);
                if (depth >= 0) {
                    sb.append("深さ: ").append(depth == 0 ? "ごく浅い" : depth + "km").append("\n");
                }
                double lat = hypocenter.optDouble("latitude", -200);
                double lon = hypocenter.optDouble("longitude", -200);
                if (lat > -100) {
                    sb.append("緯度/経度: ").append(lat).append("/").append(lon).append("\n");
                }
            }

            int maxScale = earthquake.optInt("maxScale", -1);
            sb.append("最大震度: ").append(seismicScaleToText(maxScale)).append("\n");

            String domesticTsunamiStatus = earthquake.optString("domesticTsunami", "");
            if (!domesticTsunamiStatus.isEmpty()) {
                sb.append("国内津波: ").append(domesticTsunamiToJapanese(domesticTsunamiStatus)).append("\n");
            }
            String foreignTsunamiStatus = earthquake.optString("foreignTsunami", "");
            if (!foreignTsunamiStatus.isEmpty()) {
                sb.append("海外津波: ").append(foreignTsunamiToJapanese(foreignTsunamiStatus)).append("\n");
            }
        }

        // 観測点
        JSONArray observationPoints = p2pJson.optJSONArray("points");
        if (observationPoints != null && observationPoints.length() > 0) {
            sb.append("\n【各地の震度】\n");
            int max = Math.min(observationPoints.length(), 20);
            for (int i = 0; i < max; i++) {
                JSONObject point = observationPoints.getJSONObject(i);
                sb.append("  ").append(point.optString("pref", "")).append(" ")
                  .append(point.optString("addr", "")).append(" 震度")
                  .append(seismicScaleToText(point.optInt("scale", -1))).append("\n");
            }
            if (observationPoints.length() > 20) {
                sb.append("  …他 ").append(observationPoints.length() - 20).append(" 地点\n");
            }
        }

        // 付加文
        JSONObject comments = p2pJson.optJSONObject("comments");
        if (comments != null) {
            String freeFormComment = comments.optString("freeFormComment", "");
            if (!freeFormComment.isEmpty()) sb.append("\n").append(freeFormComment).append("\n");
        }

        return sb.toString().trim();
    }

    private static String toBriefMessage_JMAQuake(JSONObject p2pJson) throws Exception {
        JSONObject earthquake = p2pJson.optJSONObject("earthquake");
        if (earthquake == null) return "地震情報を受信しました";

        String time = earthquake.optString("time", "");
        int maxScale = earthquake.optInt("maxScale", -1);

        JSONObject hypocenter = earthquake.optJSONObject("hypocenter");
        String hypocenterName = (hypocenter != null) ? hypocenter.optString("name", "震源不明") : "震源不明";
        double magnitude      = (hypocenter != null) ? hypocenter.optDouble("magnitude", -1) : -1;

        StringBuilder sb = new StringBuilder();
        sb.append("地震情報 ").append(time.length() >= 16 ? time.substring(5, 16) : time).append("\n");
        sb.append(hypocenterName);
        if (magnitude >= 0) sb.append(" M").append(magnitude);
        sb.append(" 最大震度").append(seismicScaleToText(maxScale));

        // 津波注意
        String domesticTsunamiStatus = earthquake.optString("domesticTsunami", "None");
        if ("Watch".equals(domesticTsunamiStatus) || "Warning".equals(domesticTsunamiStatus)) {
            sb.append(" ⚠津波");
        }

        return sb.toString();
    }

    // ================================================================
    //  552: JMATsunami — 津波予報
    // ================================================================

    private static String toFullMessage_JMATsunami(JSONObject p2pJson) throws Exception {
        StringBuilder sb = new StringBuilder();
        boolean cancelled = p2pJson.optBoolean("cancelled", false);

        sb.append(cancelled ? "【津波予報 解除】\n" : "【津波予報】\n");

        JSONObject issue = p2pJson.optJSONObject("issue");
        if (issue != null) {
            sb.append("発表: ").append(issue.optString("source", "不明"))
              .append("  ").append(issue.optString("time", "")).append("\n");
        }

        if (!cancelled) {
            JSONArray tsunamiAreas = p2pJson.optJSONArray("areas");
            if (tsunamiAreas != null) {
                for (int i = 0; i < tsunamiAreas.length(); i++) {
                    JSONObject area = tsunamiAreas.getJSONObject(i);
                    sb.append("\n").append(area.optString("name", "")).append("\n");
                    sb.append("  種別: ").append(tsunamiGradeToJapanese(area.optString("grade", ""))).append("\n");
                    sb.append("  直ちに来襲: ").append(area.optBoolean("immediate") ? "はい" : "いいえ").append("\n");

                    JSONObject firstHeight = area.optJSONObject("firstHeight");
                    if (firstHeight != null) {
                        String condition   = firstHeight.optString("condition", "");
                        String arrivalTime = firstHeight.optString("arrivalTime", "");
                        if (!condition.isEmpty())   sb.append("  到達状況: ").append(condition).append("\n");
                        if (!arrivalTime.isEmpty()) sb.append("  到達予想: ").append(arrivalTime).append("\n");
                    }

                    JSONObject maxHeight = area.optJSONObject("maxHeight");
                    if (maxHeight != null) {
                        String description = maxHeight.optString("description", "");
                        if (!description.isEmpty()) sb.append("  予想高さ: ").append(description).append("\n");
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private static String toBriefMessage_JMATsunami(JSONObject p2pJson) throws Exception {
        boolean cancelled = p2pJson.optBoolean("cancelled", false);
        if (cancelled) return "津波予報が解除されました";

        JSONArray tsunamiAreas = p2pJson.optJSONArray("areas");
        if (tsunamiAreas == null || tsunamiAreas.length() == 0) return "津波予報が発表されました";

        // 最も危険なgradeを先頭に
        String topGrade    = "";
        String topAreaName = "";
        for (int i = 0; i < tsunamiAreas.length(); i++) {
            JSONObject area = tsunamiAreas.getJSONObject(i);
            String grade = area.optString("grade", "");
            if (topGrade.isEmpty() || tsunamiGradeToDangerLevel(grade) > tsunamiGradeToDangerLevel(topGrade)) {
                topGrade    = grade;
                topAreaName = area.optString("name", "");
            }
        }
        return "津波予報 " + tsunamiGradeToJapanese(topGrade) + "\n"
             + topAreaName + " など " + tsunamiAreas.length() + " 地域";
    }

    // ================================================================
    //  554: EEWDetection — 緊急地震速報 発表検出
    // ================================================================

    private static String toFullMessage_EEWDetection(JSONObject p2pJson) throws Exception {
        String detectionType = p2pJson.optString("type", "");
        String detectionTime = p2pJson.optString("time", "");
        return "【緊急地震速報 検出】\n"
             + "検出時刻: " + detectionTime + "\n"
             + "種別: " + eewDetectionTypeToJapanese(detectionType);
    }

    private static String toBriefMessage_EEWDetection(JSONObject p2pJson) throws Exception {
        return "⚡ 緊急地震速報を検出しました";
    }

    // ================================================================
    //  555: Areapeers — 各地域ピア数
    // ================================================================

    private static String toFullMessage_Areapeers(JSONObject p2pJson) throws Exception {
        JSONArray peerAreas = p2pJson.optJSONArray("areas");
        int totalPeers = 0;
        if (peerAreas != null) {
            for (int i = 0; i < peerAreas.length(); i++) {
                totalPeers += peerAreas.getJSONObject(i).optInt("peer", 0);
            }
        }
        return "【ピア情報】\n"
             + "接続ピア総数: " + totalPeers + "\n"
             + "地域数: " + (peerAreas != null ? peerAreas.length() : 0);
    }

    private static String toBriefMessage_Areapeers(JSONObject p2pJson) throws Exception {
        JSONArray peerAreas = p2pJson.optJSONArray("areas");
        int totalPeers = 0;
        if (peerAreas != null) {
            for (int i = 0; i < peerAreas.length(); i++) {
                totalPeers += peerAreas.getJSONObject(i).optInt("peer", 0);
            }
        }
        return "ピア: " + totalPeers + " 接続中";
    }

    // ================================================================
    //  556: EEW — 緊急地震速報（警報）
    // ================================================================

    private static String toFullMessage_EEW(JSONObject p2pJson) throws Exception {
        StringBuilder sb = new StringBuilder();
        boolean cancelled = p2pJson.optBoolean("cancelled", false);
        boolean isTest    = p2pJson.optBoolean("test", false);

        sb.append(isTest ? "【緊急地震速報（テスト）】\n" : "【緊急地震速報（警報）】\n");
        if (cancelled) {
            sb.append("⚠ この速報は取消されました\n");
            return sb.toString().trim();
        }

        JSONObject issue = p2pJson.optJSONObject("issue");
        if (issue != null) {
            sb.append("発表時刻: ").append(issue.optString("time", "")).append("\n");
            sb.append("第").append(issue.optString("serial", "?")).append("報\n");
        }

        JSONObject earthquake = p2pJson.optJSONObject("earthquake");
        if (earthquake != null) {
            sb.append("地震発生: ").append(earthquake.optString("originTime", "不明")).append("\n");

            JSONObject hypocenter = earthquake.optJSONObject("hypocenter");
            if (hypocenter != null) {
                String hypocenterName = hypocenter.optString("name", "不明");
                sb.append("震央: ").append(hypocenterName).append("\n");
                String reducedName = hypocenter.optString("reduceName", "");
                if (!reducedName.isEmpty() && !reducedName.equals(hypocenterName)) {
                    sb.append("（").append(reducedName).append("）\n");
                }
                double magnitude = hypocenter.optDouble("magnitude", -1);
                if (magnitude >= 0) sb.append("M: ").append(magnitude).append("\n");
                double depth = hypocenter.optDouble("depth", -1);
                if (depth >= 0) {
                    sb.append("深さ: ").append((int)depth == 0 ? "ごく浅い" : (int)depth + "km").append("\n");
                }
            }

            String condition = earthquake.optString("condition", "");
            if (!condition.isEmpty()) sb.append("備考: ").append(condition).append("\n");
        }

        JSONArray eewWarningAreas = p2pJson.optJSONArray("areas");
        if (eewWarningAreas != null && eewWarningAreas.length() > 0) {
            sb.append("\n【警報対象地域】\n");
            for (int i = 0; i < eewWarningAreas.length(); i++) {
                JSONObject area = eewWarningAreas.getJSONObject(i);
                sb.append("  ").append(area.optString("pref", "")).append(" ")
                  .append(area.optString("name", "")).append("\n");
                sb.append("  予測震度: ").append(eewScaleRangeToText(
                        area.optInt("scaleFrom", -1), area.optInt("scaleTo", -1)
                )).append("\n");
                String arrivalTime = area.optString("arrivalTime", "");
                if (!arrivalTime.isEmpty() && !"null".equals(arrivalTime)) {
                    sb.append("  主要動到達: ").append(arrivalTime).append("\n");
                }
            }
        }

        return sb.toString().trim();
    }

    private static String toBriefMessage_EEW(JSONObject p2pJson) throws Exception {
        boolean cancelled = p2pJson.optBoolean("cancelled", false);
        if (cancelled) return "⚡ 緊急地震速報（取消）";

        boolean isTest  = p2pJson.optBoolean("test", false);
        String prefix   = isTest ? "⚡【テスト】緊急地震速報\n" : "⚡ 緊急地震速報（警報）\n";

        JSONObject earthquake = p2pJson.optJSONObject("earthquake");
        if (earthquake == null) return prefix + "詳細不明";

        JSONObject hypocenter = earthquake.optJSONObject("hypocenter");
        String hypocenterName = (hypocenter != null) ? hypocenter.optString("name", "震源不明") : "震源不明";
        double magnitude      = (hypocenter != null) ? hypocenter.optDouble("magnitude", -1) : -1;

        StringBuilder sb = new StringBuilder(prefix);
        sb.append(hypocenterName);
        if (magnitude >= 0) sb.append(" 推定M").append(magnitude);
        return sb.toString();
    }

    // ================================================================
    //  561: Userquake — 地震感知情報
    // ================================================================

    private static String toFullMessage_Userquake(JSONObject p2pJson) throws Exception {
        int    areaCode  = p2pJson.optInt("area", -1);
        String eventTime = p2pJson.optString("time", "");
        String areaName  = (areaCode >= 0) ? EpspArea.nameOf(areaCode) : "不明";
        return "【地震感知情報】\n"
             + "受信日時: " + eventTime + "\n"
             + "地域: " + areaName + " (コード: " + areaCode + ")";
    }

    private static String toBriefMessage_Userquake(JSONObject p2pJson) throws Exception {
        int    areaCode = p2pJson.optInt("area", -1);
        String areaName = (areaCode >= 0) ? EpspArea.nameOf(areaCode) : "不明";
        return "地震感知情報: " + areaName;
    }

    // ================================================================
    //  9611: UserquakeEvaluation — 地震感知情報 解析結果
    // ================================================================

    private static String toFullMessage_UserquakeEvaluation(JSONObject p2pJson) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("【地震感知情報 解析結果】\n");
        sb.append("評価日時: ").append(p2pJson.optString("time", "")).append("\n");
        sb.append("開始日時: ").append(p2pJson.optString("started_at", "")).append("\n");
        sb.append("件数: ").append(p2pJson.optInt("count", 0)).append("\n");

        double confidenceScore = p2pJson.optDouble("confidence", 0);
        sb.append("信頼度: ").append(String.format("%.5f", confidenceScore))
          .append(" (").append(confidenceScoreToLabel(confidenceScore)).append(")\n");

        JSONObject areaConfidences = p2pJson.optJSONObject("area_confidences");
        if (areaConfidences != null && areaConfidences.length() > 0) {
            sb.append("\n【地域別信頼度】\n");
            for (java.util.Iterator<String> it = areaConfidences.keys(); it.hasNext(); ) {
                String areaKey = it.next();
                JSONObject areaConfidence = areaConfidences.optJSONObject(areaKey);
                if (areaConfidence == null) continue;
                String displayValue = areaConfidence.optString("display", "");
                int    reportCount  = areaConfidence.optInt("count", 0);
                // キーは地域コード文字列 → EpspAreaで地域名に変換
                String areaName = EpspArea.nameOf(parseAreaCodeKey(areaKey));
                sb.append("  ").append(areaName).append(": ")
                  .append(displayValue.isEmpty() ? "-" : displayValue)
                  .append(" (").append(reportCount).append("件)\n");
            }
        }
        return sb.toString().trim();
    }

    private static String toBriefMessage_UserquakeEvaluation(JSONObject p2pJson) throws Exception {
        double confidenceScore = p2pJson.optDouble("confidence", 0);
        int    reportCount     = p2pJson.optInt("count", 0);
        String confidenceLabel = confidenceScoreToLabel(confidenceScore);
        if ("非表示".equals(confidenceLabel)) return "地震感知情報 (信頼度低)";
        return "地震感知情報 解析結果\n件数: " + reportCount + "  信頼度: " + confidenceLabel;
    }

    // ================================================================
    //  変換ヘルパー
    // ================================================================

    /** 震度コード → 表示文字列 */
    public static String seismicScaleToText(int scaleCode) {
        switch (scaleCode) {
            case 10: return "1";
            case 20: return "2";
            case 30: return "3";
            case 40: return "4";
            case 45: return "5弱";
            case 46: return "5弱以上（推定）";
            case 50: return "5強";
            case 55: return "6弱";
            case 60: return "6強";
            case 70: return "7";
            default: return "不明";
        }
    }

    /** issue.type → 日本語 */
    private static String issueTypeToJapanese(String issueType) {
        switch (issueType) {
            case "ScalePrompt":         return "震度速報";
            case "Destination":         return "震源に関する情報";
            case "ScaleAndDestination": return "震度・震源に関する情報";
            case "DetailScale":         return "各地の震度に関する情報";
            case "Foreign":             return "遠地地震に関する情報";
            case "Other":               return "その他";
            default: return issueType;
        }
    }

    /** issue.correct → 日本語 */
    private static String correctTypeToJapanese(String correctType) {
        switch (correctType) {
            case "None":                return "訂正なし";
            case "Unknown":             return "不明";
            case "ScaleOnly":           return "震度のみ訂正";
            case "DestinationOnly":     return "震源のみ訂正";
            case "ScaleAndDestination": return "震度・震源を訂正";
            default: return correctType;
        }
    }

    /** earthquake.domesticTsunami → 日本語 */
    private static String domesticTsunamiToJapanese(String tsunamiStatus) {
        switch (tsunamiStatus) {
            case "None":         return "なし";
            case "Unknown":      return "不明";
            case "Checking":     return "調査中";
            case "NonEffective": return "若干の海面変動（被害の心配なし）";
            case "Watch":        return "津波注意報";
            case "Warning":      return "津波予報（種類不明）";
            default: return tsunamiStatus;
        }
    }

    /** earthquake.foreignTsunami → 日本語 */
    private static String foreignTsunamiToJapanese(String tsunamiStatus) {
        switch (tsunamiStatus) {
            case "None":               return "なし";
            case "Unknown":            return "不明";
            case "Checking":           return "調査中";
            case "NonEffectiveNearby": return "震源近傍で小さな津波の可能性（被害なし）";
            case "WarningNearby":      return "震源近傍で津波の可能性";
            case "WarningPacific":     return "太平洋で津波の可能性";
            case "WarningPacificWide": return "太平洋の広域で津波の可能性";
            case "WarningIndian":      return "インド洋で津波の可能性";
            case "WarningIndianWide":  return "インド洋の広域で津波の可能性";
            case "Potential":          return "この規模では津波の可能性あり";
            default: return tsunamiStatus;
        }
    }

    /** tsunami area.grade → 日本語 */
    private static String tsunamiGradeToJapanese(String grade) {
        switch (grade) {
            case "MajorWarning": return "大津波警報";
            case "Warning":      return "津波警報";
            case "Watch":        return "津波注意報";
            case "Unknown":      return "不明";
            default: return grade;
        }
    }

    /** 津波グレードの危険度数値（比較用） */
    private static int tsunamiGradeToDangerLevel(String grade) {
        switch (grade) {
            case "MajorWarning": return 3;
            case "Warning":      return 2;
            case "Watch":        return 1;
            default:             return 0;
        }
    }

    /** EEWDetection type → 日本語 */
    private static String eewDetectionTypeToJapanese(String detectionType) {
        switch (detectionType) {
            case "Full":  return "チャイム＋音声";
            case "Chime": return "チャイムのみ";
            default: return detectionType;
        }
    }

    /** EEW scaleFrom〜scaleTo → 表示文字列 */
    private static String eewScaleRangeToText(int scaleFrom, int scaleTo) {
        if (scaleFrom == scaleTo) return "震度" + seismicScaleToText(scaleFrom);
        if (scaleTo == 99)        return "震度" + seismicScaleToText(scaleFrom) + "以上";
        return "震度" + seismicScaleToText(scaleFrom) + "〜" + seismicScaleToText(scaleTo);
    }

    /** area_confidences のキー文字列を int に変換するヘルパー */
    private static int parseAreaCodeKey(String areaKey) {
        try { return (int) Double.parseDouble(areaKey.trim()); }
        catch (Exception e) { return -1; }
    }

    /** UserquakeEvaluation confidence → レベル文字列 */
    private static String confidenceScoreToLabel(double confidenceScore) {
        if (confidenceScore <= 0)       return "非表示";
        if (confidenceScore >= 0.98052) return "レベル4";
        if (confidenceScore >= 0.97024) return "レベル3";
        if (confidenceScore >= 0.97015) return "レベル1";
        if (confidenceScore >= 0.96774) return "レベル2";
        return "レベル不明";
    }
}
