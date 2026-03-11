package com.example.koiyurepublic;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * EpspArea
 *
 * assets/accompanying/MYepsp-area.csv を読み込み、
 * 地域コード → 地域情報 の変換を提供するシングルトン。
 *
 * CSV フォーマット（1行目はヘッダー）:
 *   地域コード(文字列型), 地域コード(数値型), 地方, 都道府県, 地域,
 *   緯度, 経度, mysub_code, mysub_name
 *
 * 使い方:
 *   EpspArea.init(context);                    // Application.onCreate() か SpinalCord.onCreate() で1回だけ呼ぶ
 *   EpspArea.Entry e = EpspArea.get(250);       // 地域コード → エントリ取得
 *   String name = EpspArea.nameOf(250);         // 地域コード → "大阪府 近畿 大阪府" などの短い名前
 *   double[] latlon = EpspArea.latLonOf(250);   // 地域コード → [緯度, 経度]（不明時は null）
 */
public class EpspArea {

    private static final String TAG      = "EpspArea";
    private static final String CSV_PATH = "accompanying/MYepsp-area.csv";

    // ──────────────────────────────────────────────
    //  データクラス
    // ──────────────────────────────────────────────

    public static class Entry {
        public final int    code;       // 地域コード(数値)
        public final String region;     // 地方
        public final String pref;       // 都道府県
        public final String area;       // 地域名
        public final double latitude;   // 緯度  (未設定時 Double.NaN)
        public final double longitude;  // 経度  (未設定時 Double.NaN)
        public final int    subCode;    // mysub_code (未設定時 -1)
        public final String subName;    // mysub_name

        public Entry(int code, String region, String pref, String area,
                     double lat, double lon, int subCode, String subName) {
            this.code      = code;
            this.region    = region;
            this.pref      = pref;
            this.area      = area;
            this.latitude  = lat;
            this.longitude = lon;
            this.subCode   = subCode;
            this.subName   = subName;
        }

        /** 地図表示に使える座標を持っているか */
        public boolean hasLocation() {
            return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                    && latitude != 0.0 && longitude != 0.0;
        }

        /** "都道府県 地域名" の短い表示文字列 */
        public String displayName() {
            if (pref != null && !pref.isEmpty() && area != null && !area.isEmpty()) {
                // area に都道府県名が重複している場合は area だけ返す
                return area.startsWith(pref) ? area : pref + " " + area;
            }
            if (area != null && !area.isEmpty()) return area;
            if (pref != null && !pref.isEmpty()) return pref;
            return "地域コード " + code;
        }

        @Override
        public String toString() {
            return "EpspArea.Entry{code=" + code + ", name=" + displayName()
                    + ", lat=" + latitude + ", lon=" + longitude + "}";
        }
    }

    // ──────────────────────────────────────────────
    //  シングルトン
    // ──────────────────────────────────────────────

    private static volatile Map<Integer, Entry> table = null;

    /**
     * CSV を読み込んでテーブルを構築する。
     * SpinalCord.onCreate() から一度だけ呼ぶこと。
     * 既に初期化済みの場合は何もしない。
     */
    public static synchronized void init(Context context) {
        if (table != null) return;
        Map<Integer, Entry> tmp = new HashMap<>();
        int count = 0;
        try {
            InputStream is = context.getAssets().open(CSV_PATH);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }  // ヘッダースキップ
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] cols = splitCsv(line);
                if (cols.length < 7) continue;

                try {
                    int    code    = parseInt(cols[1]);   // 数値型コード
                    String region  = cols[2].trim();
                    String pref    = cols[3].trim();
                    String area    = cols[4].trim();
                    double lat     = parseDouble(cols[5]);
                    double lon     = parseDouble(cols[6]);
                    int    subCode = cols.length > 7 ? parseIntSafe(cols[7]) : -1;
                    String subName = cols.length > 8 ? cols[8].trim() : "";

                    tmp.put(code, new Entry(code, region, pref, area, lat, lon, subCode, subName));
                    count++;
                } catch (Exception ignored) {}
            }
            br.close();
        } catch (Exception e) {
            Log.e(TAG, "CSV読み込み失敗: " + e.getMessage());
        }
        table = Collections.unmodifiableMap(tmp);
        Log.d(TAG, "EpspArea 初期化完了: " + count + " 件");
    }

    // ──────────────────────────────────────────────
    //  公開API
    // ──────────────────────────────────────────────

    /** 地域コードから Entry を取得。未登録の場合は null */
    public static Entry get(int code) {
        if (table == null) return null;
        return table.get(code);
    }

    /** 地域コードから表示名を取得。未登録の場合は "地域コード XXX" */
    public static String nameOf(int code) {
        Entry e = get(code);
        return (e != null) ? e.displayName() : "地域コード " + code;
    }

    /**
     * 地域コードから [緯度, 経度] を取得。
     * 座標を持たないコードの場合は null を返す。
     */
    public static double[] latLonOf(int code) {
        Entry e = get(code);
        if (e == null || !e.hasLocation()) return null;
        return new double[]{e.latitude, e.longitude};
    }

    /** テーブル全体を取得（地図初期化時に全マーカーを打つ場合など） */
    public static Map<Integer, Entry> all() {
        return (table != null) ? table : Collections.emptyMap();
    }

    // ──────────────────────────────────────────────
    //  CSV パースヘルパー
    // ──────────────────────────────────────────────

    /** カンマ区切りをダブルクォート考慮で分割 */
    private static String[] splitCsv(String line) {
        return line.split(",", -1);
    }

    private static int parseInt(String s) {
        s = s.trim();
        if (s.isEmpty()) return -1;
        // "101.0" のような小数付き整数に対応
        return (int) Double.parseDouble(s);
    }

    private static int parseIntSafe(String s) {
        try { return parseInt(s); } catch (Exception e) { return -1; }
    }

    private static double parseDouble(String s) {
        s = s.trim();
        if (s.isEmpty()) return Double.NaN;
        try { return Double.parseDouble(s); } catch (Exception e) { return Double.NaN; }
    }
}
