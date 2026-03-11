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
 *   551  JMAQuake          地震情報
 *   552  JMATsunami        津波予報
 *   554  EEWDetection      緊急地震速報 発表検出
 *   555  Areapeers         各地域ピア数
 *   556  EEW               緊急地震速報（警報）
 *   561  Userquake         地震感知情報
 *   9611 UserquakeEvaluation 地震感知情報 解析結果
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
            JSONObject obj = new JSONObject(json);
            int code = obj.optInt("code", -1);
            switch (code) {
                case 551:  return full551(obj);
                case 552:  return full552(obj);
                case 554:  return full554(obj);
                case 555:  return full555(obj);
                case 556:  return full556(obj);
                case 561:  return full561(obj);
                case 9611: return full9611(obj);
                default:   return "【不明な情報】コード: " + code;
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
            JSONObject obj = new JSONObject(json);
            int code = obj.optInt("code", -1);
            switch (code) {
                case 551:  return brief551(obj);
                case 552:  return brief552(obj);
                case 554:  return brief554(obj);
                case 555:  return brief555(obj);
                case 556:  return brief556(obj);
                case 561:  return brief561(obj);
                case 9611: return brief9611(obj);
                default:   return "不明な情報を受信しました（コード: " + code + "）";
            }
        } catch (Exception e) {
            return "情報の解析に失敗しました";
        }
    }

    // ================================================================
    //  551: JMAQuake — 地震情報
    // ================================================================

    private static String full551(JSONObject obj) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("【地震情報】\n");

        JSONObject issue = obj.optJSONObject("issue");
        if (issue != null) {
            sb.append("発表: ").append(issue.optString("source", "不明"))
              .append("  ").append(issue.optString("time", ""))
              .append("\n");
            sb.append("種類: ").append(issueType(issue.optString("type", "")))
              .append("\n");
            String correct = issue.optString("correct", "None");
            if (!"None".equals(correct)) {
                sb.append("訂正: ").append(correctType(correct)).append("\n");
            }
        }

        JSONObject quake = obj.optJSONObject("earthquake");
        if (quake != null) {
            sb.append("発生日時: ").append(quake.optString("time", "不明")).append("\n");

            JSONObject hypo = quake.optJSONObject("hypocenter");
            if (hypo != null) {
                String name = hypo.optString("name", "");
                if (!name.isEmpty()) sb.append("震源: ").append(name).append("\n");
                double mag = hypo.optDouble("magnitude", -1);
                if (mag >= 0) sb.append("M: ").append(mag).append("\n");
                int depth = hypo.optInt("depth", -1);
                if (depth >= 0) {
                    sb.append("深さ: ").append(depth == 0 ? "ごく浅い" : depth + "km").append("\n");
                }
                double lat = hypo.optDouble("latitude", -200);
                double lon = hypo.optDouble("longitude", -200);
                if (lat > -100) {
                    sb.append("緯度/経度: ").append(lat).append("/").append(lon).append("\n");
                }
            }

            int maxScale = quake.optInt("maxScale", -1);
            sb.append("最大震度: ").append(scaleToText(maxScale)).append("\n");

            String domestic = quake.optString("domesticTsunami", "");
            if (!domestic.isEmpty()) {
                sb.append("国内津波: ").append(domesticTsunami(domestic)).append("\n");
            }
            String foreign = quake.optString("foreignTsunami", "");
            if (!foreign.isEmpty()) {
                sb.append("海外津波: ").append(foreignTsunami(foreign)).append("\n");
            }
        }

        // 観測点
        JSONArray points = obj.optJSONArray("points");
        if (points != null && points.length() > 0) {
            sb.append("\n【各地の震度】\n");
            // 最大5件だけ表示（フルでも長すぎ防止）
            int max = Math.min(points.length(), 20);
            for (int i = 0; i < max; i++) {
                JSONObject p = points.getJSONObject(i);
                sb.append("  ").append(p.optString("pref", "")).append(" ")
                  .append(p.optString("addr", "")).append(" 震度")
                  .append(scaleToText(p.optInt("scale", -1))).append("\n");
            }
            if (points.length() > 20) {
                sb.append("  …他 ").append(points.length() - 20).append(" 地点\n");
            }
        }

        // 付加文
        JSONObject comments = obj.optJSONObject("comments");
        if (comments != null) {
            String free = comments.optString("freeFormComment", "");
            if (!free.isEmpty()) sb.append("\n").append(free).append("\n");
        }

        return sb.toString().trim();
    }

    private static String brief551(JSONObject obj) throws Exception {
        JSONObject quake = obj.optJSONObject("earthquake");
        if (quake == null) return "地震情報を受信しました";

        String time = quake.optString("time", "");
        int maxScale = quake.optInt("maxScale", -1);

        JSONObject hypo = quake.optJSONObject("hypocenter");
        String name = (hypo != null) ? hypo.optString("name", "震源不明") : "震源不明";
        double mag   = (hypo != null) ? hypo.optDouble("magnitude", -1) : -1;

        StringBuilder sb = new StringBuilder();
        sb.append("地震情報 ").append(time.length() >= 16 ? time.substring(5, 16) : time).append("\n");
        sb.append(name);
        if (mag >= 0) sb.append(" M").append(mag);
        sb.append(" 最大震度").append(scaleToText(maxScale));

        // 津波注意
        String tsunami = (quake != null) ? quake.optString("domesticTsunami", "None") : "None";
        if ("Watch".equals(tsunami) || "Warning".equals(tsunami)) {
            sb.append(" ⚠津波");
        }

        return sb.toString();
    }

    // ================================================================
    //  552: JMATsunami — 津波予報
    // ================================================================

    private static String full552(JSONObject obj) throws Exception {
        StringBuilder sb = new StringBuilder();
        boolean cancelled = obj.optBoolean("cancelled", false);

        if (cancelled) {
            sb.append("【津波予報 解除】\n");
        } else {
            sb.append("【津波予報】\n");
        }

        JSONObject issue = obj.optJSONObject("issue");
        if (issue != null) {
            sb.append("発表: ").append(issue.optString("source", "不明"))
              .append("  ").append(issue.optString("time", "")).append("\n");
        }

        if (!cancelled) {
            JSONArray areas = obj.optJSONArray("areas");
            if (areas != null) {
                for (int i = 0; i < areas.length(); i++) {
                    JSONObject a = areas.getJSONObject(i);
                    sb.append("\n").append(a.optString("name", "")).append("\n");
                    sb.append("  種別: ").append(tsunamiGrade(a.optString("grade", ""))).append("\n");
                    sb.append("  直ちに来襲: ").append(a.optBoolean("immediate") ? "はい" : "いいえ").append("\n");

                    JSONObject fh = a.optJSONObject("firstHeight");
                    if (fh != null) {
                        String cond = fh.optString("condition", "");
                        String arr  = fh.optString("arrivalTime", "");
                        if (!cond.isEmpty()) sb.append("  到達状況: ").append(cond).append("\n");
                        if (!arr.isEmpty())  sb.append("  到達予想: ").append(arr).append("\n");
                    }

                    JSONObject mh = a.optJSONObject("maxHeight");
                    if (mh != null) {
                        String desc = mh.optString("description", "");
                        if (!desc.isEmpty()) sb.append("  予想高さ: ").append(desc).append("\n");
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private static String brief552(JSONObject obj) throws Exception {
        boolean cancelled = obj.optBoolean("cancelled", false);
        if (cancelled) return "津波予報が解除されました";

        JSONArray areas = obj.optJSONArray("areas");
        if (areas == null || areas.length() == 0) return "津波予報が発表されました";

        // 最も危険なgradeを先頭に
        String topGrade = "";
        String topName  = "";
        for (int i = 0; i < areas.length(); i++) {
            JSONObject a = areas.getJSONObject(i);
            String g = a.optString("grade", "");
            if (topGrade.isEmpty() || tsunamiGradeLevel(g) > tsunamiGradeLevel(topGrade)) {
                topGrade = g;
                topName  = a.optString("name", "");
            }
        }
        return "津波予報 " + tsunamiGrade(topGrade) + "\n"
             + topName + " など " + areas.length() + " 地域";
    }

    // ================================================================
    //  554: EEWDetection — 緊急地震速報 発表検出
    // ================================================================

    private static String full554(JSONObject obj) throws Exception {
        String type = obj.optString("type", "");
        String time = obj.optString("time", "");
        return "【緊急地震速報 検出】\n"
             + "検出時刻: " + time + "\n"
             + "種別: " + eewDetectionType(type);
    }

    private static String brief554(JSONObject obj) throws Exception {
        return "⚡ 緊急地震速報を検出しました";
    }

    // ================================================================
    //  555: Areapeers — 各地域ピア数
    // ================================================================

    private static String full555(JSONObject obj) throws Exception {
        JSONArray areas = obj.optJSONArray("areas");
        int total = 0;
        if (areas != null) {
            for (int i = 0; i < areas.length(); i++) {
                total += areas.getJSONObject(i).optInt("peer", 0);
            }
        }
        return "【ピア情報】\n"
             + "接続ピア総数: " + total + "\n"
             + "地域数: " + (areas != null ? areas.length() : 0);
    }

    private static String brief555(JSONObject obj) throws Exception {
        JSONArray areas = obj.optJSONArray("areas");
        int total = 0;
        if (areas != null) {
            for (int i = 0; i < areas.length(); i++) {
                total += areas.getJSONObject(i).optInt("peer", 0);
            }
        }
        return "ピア: " + total + " 接続中";
    }

    // ================================================================
    //  556: EEW — 緊急地震速報（警報）
    // ================================================================

    private static String full556(JSONObject obj) throws Exception {
        StringBuilder sb = new StringBuilder();
        boolean cancelled = obj.optBoolean("cancelled", false);
        boolean test      = obj.optBoolean("test", false);

        sb.append(test ? "【緊急地震速報（テスト）】\n" : "【緊急地震速報（警報）】\n");
        if (cancelled) {
            sb.append("⚠ この速報は取消されました\n");
            return sb.toString().trim();
        }

        JSONObject issue = obj.optJSONObject("issue");
        if (issue != null) {
            sb.append("発表時刻: ").append(issue.optString("time", "")).append("\n");
            sb.append("第").append(issue.optString("serial", "?")).append("報\n");
        }

        JSONObject quake = obj.optJSONObject("earthquake");
        if (quake != null) {
            sb.append("地震発生: ").append(quake.optString("originTime", "不明")).append("\n");

            JSONObject hypo = quake.optJSONObject("hypocenter");
            if (hypo != null) {
                String name = hypo.optString("name", "不明");
                sb.append("震央: ").append(name).append("\n");
                String reduce = hypo.optString("reduceName", "");
                if (!reduce.isEmpty() && !reduce.equals(name)) {
                    sb.append("（").append(reduce).append("）\n");
                }
                double mag = hypo.optDouble("magnitude", -1);
                if (mag >= 0) sb.append("M: ").append(mag).append("\n");
                double depth = hypo.optDouble("depth", -1);
                if (depth >= 0) {
                    sb.append("深さ: ").append((int)depth == 0 ? "ごく浅い" : (int)depth + "km").append("\n");
                }
            }

            String cond = quake.optString("condition", "");
            if (!cond.isEmpty()) sb.append("備考: ").append(cond).append("\n");
        }

        JSONArray areas = obj.optJSONArray("areas");
        if (areas != null && areas.length() > 0) {
            sb.append("\n【警報対象地域】\n");
            for (int i = 0; i < areas.length(); i++) {
                JSONObject a = areas.getJSONObject(i);
                sb.append("  ").append(a.optString("pref", "")).append(" ")
                  .append(a.optString("name", "")).append("\n");
                sb.append("  予測震度: ").append(eewScaleRange(
                        a.optInt("scaleFrom", -1), a.optInt("scaleTo", -1)
                )).append("\n");
                String arrTime = a.optString("arrivalTime", "");
                if (!arrTime.isEmpty() && !"null".equals(arrTime)) {
                    sb.append("  主要動到達: ").append(arrTime).append("\n");
                }
            }
        }

        return sb.toString().trim();
    }

    private static String brief556(JSONObject obj) throws Exception {
        boolean cancelled = obj.optBoolean("cancelled", false);
        if (cancelled) return "⚡ 緊急地震速報（取消）";

        boolean test = obj.optBoolean("test", false);
        String prefix = test ? "⚡【テスト】緊急地震速報\n" : "⚡ 緊急地震速報（警報）\n";

        JSONObject quake = obj.optJSONObject("earthquake");
        if (quake == null) return prefix + "詳細不明";

        JSONObject hypo = quake.optJSONObject("hypocenter");
        String name = (hypo != null) ? hypo.optString("name", "震源不明") : "震源不明";
        double mag  = (hypo != null) ? hypo.optDouble("magnitude", -1) : -1;

        StringBuilder sb = new StringBuilder(prefix);
        sb.append(name);
        if (mag >= 0) sb.append(" 推定M").append(mag);
        return sb.toString();
    }

    // ================================================================
    //  561: Userquake — 地震感知情報
    // ================================================================

    private static String full561(JSONObject obj) throws Exception {
        int area = obj.optInt("area", -1);
        String time = obj.optString("time", "");
        String areaName = (area >= 0) ? EpspArea.nameOf(area) : "不明";
        return "【地震感知情報】\n"
             + "受信日時: " + time + "\n"
             + "地域: " + areaName + " (コード: " + area + ")";
    }

    private static String brief561(JSONObject obj) throws Exception {
        int area = obj.optInt("area", -1);
        String areaName = (area >= 0) ? EpspArea.nameOf(area) : "不明";
        return "地震感知情報: " + areaName;
    }

    // ================================================================
    //  9611: UserquakeEvaluation — 地震感知情報 解析結果
    // ================================================================

    private static String full9611(JSONObject obj) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("【地震感知情報 解析結果】\n");
        sb.append("評価日時: ").append(obj.optString("time", "")).append("\n");
        sb.append("開始日時: ").append(obj.optString("started_at", "")).append("\n");
        sb.append("件数: ").append(obj.optInt("count", 0)).append("\n");

        double conf = obj.optDouble("confidence", 0);
        sb.append("信頼度: ").append(String.format("%.5f", conf))
          .append(" (").append(confidenceLabel(conf)).append(")\n");

        JSONObject areaConf = obj.optJSONObject("area_confidences");
        if (areaConf != null && areaConf.length() > 0) {
            sb.append("\n【地域別信頼度】\n");
            for (java.util.Iterator<String> it = areaConf.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONObject ac = areaConf.optJSONObject(key);
                if (ac == null) continue;
                String display  = ac.optString("display", "");
                int cnt         = ac.optInt("count", 0);
                // キーは地域コード文字列 → EpspAreaで地域名に変換
                String areaName = EpspArea.nameOf(parseAreaKey(key));
                sb.append("  ").append(areaName).append(": ")
                  .append(display.isEmpty() ? "-" : display)
                  .append(" (").append(cnt).append("件)\n");
            }
        }
        return sb.toString().trim();
    }

    private static String brief9611(JSONObject obj) throws Exception {
        double conf = obj.optDouble("confidence", 0);
        int count   = obj.optInt("count", 0);
        String label = confidenceLabel(conf);
        if ("非表示".equals(label)) return "地震感知情報 (信頼度低)";
        return "地震感知情報 解析結果\n件数: " + count + "  信頼度: " + label;
    }

    // ================================================================
    //  変換ヘルパー
    // ================================================================

    /** 震度コード → 表示文字列 */
    public static String scaleToText(int scale) {
        switch (scale) {
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
    private static String issueType(String t) {
        switch (t) {
            case "ScalePrompt":         return "震度速報";
            case "Destination":         return "震源に関する情報";
            case "ScaleAndDestination": return "震度・震源に関する情報";
            case "DetailScale":         return "各地の震度に関する情報";
            case "Foreign":             return "遠地地震に関する情報";
            case "Other":               return "その他";
            default: return t;
        }
    }

    /** issue.correct → 日本語 */
    private static String correctType(String c) {
        switch (c) {
            case "None":                 return "訂正なし";
            case "Unknown":              return "不明";
            case "ScaleOnly":            return "震度のみ訂正";
            case "DestinationOnly":      return "震源のみ訂正";
            case "ScaleAndDestination":  return "震度・震源を訂正";
            default: return c;
        }
    }

    /** earthquake.domesticTsunami → 日本語 */
    private static String domesticTsunami(String t) {
        switch (t) {
            case "None":          return "なし";
            case "Unknown":       return "不明";
            case "Checking":      return "調査中";
            case "NonEffective":  return "若干の海面変動（被害の心配なし）";
            case "Watch":         return "津波注意報";
            case "Warning":       return "津波予報（種類不明）";
            default: return t;
        }
    }

    /** earthquake.foreignTsunami → 日本語 */
    private static String foreignTsunami(String t) {
        switch (t) {
            case "None":                  return "なし";
            case "Unknown":               return "不明";
            case "Checking":              return "調査中";
            case "NonEffectiveNearby":    return "震源近傍で小さな津波の可能性（被害なし）";
            case "WarningNearby":         return "震源近傍で津波の可能性";
            case "WarningPacific":        return "太平洋で津波の可能性";
            case "WarningPacificWide":    return "太平洋の広域で津波の可能性";
            case "WarningIndian":         return "インド洋で津波の可能性";
            case "WarningIndianWide":     return "インド洋の広域で津波の可能性";
            case "Potential":             return "この規模では津波の可能性あり";
            default: return t;
        }
    }

    /** tsunami area.grade → 日本語 */
    private static String tsunamiGrade(String g) {
        switch (g) {
            case "MajorWarning": return "大津波警報";
            case "Warning":      return "津波警報";
            case "Watch":        return "津波注意報";
            case "Unknown":      return "不明";
            default: return g;
        }
    }

    /** 危険度数値（比較用） */
    private static int tsunamiGradeLevel(String g) {
        switch (g) {
            case "MajorWarning": return 3;
            case "Warning":      return 2;
            case "Watch":        return 1;
            default:             return 0;
        }
    }

    /** EEWDetection type → 日本語 */
    private static String eewDetectionType(String t) {
        switch (t) {
            case "Full":  return "チャイム＋音声";
            case "Chime": return "チャイムのみ";
            default: return t;
        }
    }

    /** EEW scaleFrom〜scaleTo → 表示文字列 */
    private static String eewScaleRange(int from, int to) {
        if (from == to) return "震度" + scaleToText(from);
        if (to == 99)   return "震度" + scaleToText(from) + "以上";
        return "震度" + scaleToText(from) + "〜" + scaleToText(to);
    }

    /** area_confidences のキー文字列を int に変換するヘルパー */
    private static int parseAreaKey(String key) {
        try { return (int) Double.parseDouble(key.trim()); }
        catch (Exception e) { return -1; }
    }

    /** UserquakeEvaluation confidence → レベル文字列 */
    private static String confidenceLabel(double c) {
        if (c <= 0)          return "非表示";
        if (c >= 0.98052)    return "レベル4";
        if (c >= 0.97024)    return "レベル3";
        if (c >= 0.97015)    return "レベル1";  // 仕様通り
        if (c >= 0.96774)    return "レベル2";
        return "レベル不明";
    }
}
