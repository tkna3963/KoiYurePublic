package com.example.koiyurepublic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * NotifiConnection
 *
 * 地震情報の受信時にプッシュ通知を発行するクラス。
 * SpinalCord から生成・利用される。
 *
 * チャンネル構成（重要度別に3種類）:
 *   CHANNEL_EEW      緊急地震速報・大津波警報  → 最高優先度・バイブ・ライト赤
 *   CHANNEL_QUAKE    地震情報・津波注意/警報   → 高優先度・バイブあり
 *   CHANNEL_INFO     感知情報・ピア数等       → 低優先度・サイレント
 *
 * 通知IDはコード別に固定し、同種の情報は上書き更新される。
 * setEnabled() でユーザーが通知全体をOFF可能。
 */
public class NotifiConnection {

    private static final String TAG = "NotifiConnection";

    // ── チャンネルID ──────────────────────────────
    public static final String CHANNEL_EEW   = "koiyure_eew";
    public static final String CHANNEL_QUAKE = "koiyure_quake";
    public static final String CHANNEL_INFO  = "koiyure_info";

    // ── 通知ID（コード別に固定してスタックを防ぐ） ──
    private static final int NOTIF_EEW       = 100;  // 556 EEW
    private static final int NOTIF_EEW_DET   = 101;  // 554 EEWDetection
    private static final int NOTIF_QUAKE     = 200;  // 551 JMAQuake
    private static final int NOTIF_TSUNAMI   = 201;  // 552 JMATsunami
    private static final int NOTIF_USERQUAKE = 300;  // 9611 UserquakeEvaluation
    private static final int NOTIF_INFO      = 400;  // 555/561 その他

    private final Context context;
    private final NotificationManager nm;
    private boolean enabled = true;

    // ──────────────────────────────────────────────
    //  初期化
    // ──────────────────────────────────────────────

    public NotifiConnection(Context context) {
        this.context = context;
        this.nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels();
        Log.d(TAG, "NotifiConnection 初期化完了");
    }

    private void createChannels() {
        // 緊急地震速報チャンネル（最高重要度）
        NotificationChannel eew = new NotificationChannel(
                CHANNEL_EEW, "緊急地震速報・大津波警報",
                NotificationManager.IMPORTANCE_HIGH
        );
        eew.setDescription("緊急地震速報（警報）と大津波警報を通知します");
        eew.enableVibration(true);
        eew.setVibrationPattern(new long[]{0, 300, 200, 300, 200, 300});
        eew.enableLights(true);
        eew.setLightColor(Color.RED);
        eew.setBypassDnd(true);  // サイレントモードを突き抜ける

        // 地震情報チャンネル（高重要度）
        NotificationChannel quake = new NotificationChannel(
                CHANNEL_QUAKE, "地震情報・津波予報",
                NotificationManager.IMPORTANCE_HIGH
        );
        quake.setDescription("地震情報と津波予報を通知します");
        quake.enableVibration(true);
        quake.setVibrationPattern(new long[]{0, 200, 100, 200});
        quake.enableLights(true);
        quake.setLightColor(Color.YELLOW);

        // 一般情報チャンネル（低重要度）
        NotificationChannel info = new NotificationChannel(
                CHANNEL_INFO, "地震感知・接続情報",
                NotificationManager.IMPORTANCE_LOW
        );
        info.setDescription("地震感知情報やピア数などを通知します");
        info.enableVibration(false);

        nm.createNotificationChannel(eew);
        nm.createNotificationChannel(quake);
        nm.createNotificationChannel(info);
    }

    // ──────────────────────────────────────────────
    //  公開メソッド — SpinalCord から呼ぶ
    // ──────────────────────────────────────────────

    /**
     * コードを自動判別して適切な通知を発行する。
     * SpinalCord.onMessage() から直接渡せる。
     *
     * @param code      P2PQuake 情報コード（551/552/554/555/556/561/9611）
     * @param title     通知タイトル
     * @param message   通知本文（P2PConverts.toBriefMessage() の戻り値を推奨）
     */
    public void notify(int code, String title, String message) {
        if (!enabled) return;

        switch (code) {
            case 556: postEEW(title, message);          break;
            case 554: postEEWDetection(title, message); break;
            case 552: postTsunami(title, message);      break;
            case 551: postQuake(title, message);        break;
            case 9611:postUserquakeEval(title, message);break;
            case 555:
            case 561: postInfo(title, message);         break;
            default:  postInfo(title, message);         break;
        }
    }

    // ──────────────────────────────────────────────
    //  各コード向けの通知発行
    // ──────────────────────────────────────────────

    /** 556: 緊急地震速報（警報） */
    private void postEEW(String title, String message) {
        Notification n = baseBuilder(CHANNEL_EEW, title, message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setColor(Color.RED)
                .setColorized(true)
                .setVibrate(new long[]{0, 300, 200, 300, 200, 300})
                .setAutoCancel(false)
                .build();
        nm.notify(NOTIF_EEW, n);
        Log.d(TAG, "EEW通知発行");
    }

    /** 554: EEW検出 */
    private void postEEWDetection(String title, String message) {
        Notification n = baseBuilder(CHANNEL_EEW, title, message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setColor(Color.RED)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_EEW_DET, n);
    }

    /** 552: 津波予報 */
    private void postTsunami(String title, String message) {
        // 大津波警報かどうかでチャンネルを分ける
        boolean major = message.contains("大津波");
        String ch = major ? CHANNEL_EEW : CHANNEL_QUAKE;
        int color  = major ? Color.RED   : Color.rgb(255, 140, 0);

        Notification n = baseBuilder(ch, title, message)
                .setPriority(major
                        ? NotificationCompat.PRIORITY_MAX
                        : NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setColor(color)
                .setColorized(major)
                .setAutoCancel(false)
                .build();
        nm.notify(NOTIF_TSUNAMI, n);
        Log.d(TAG, "津波通知発行 major=" + major);
    }

    /** 551: 地震情報 */
    private void postQuake(String title, String message) {
        Notification n = baseBuilder(CHANNEL_QUAKE, title, message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setColor(Color.rgb(255, 200, 0))
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_QUAKE, n);
        Log.d(TAG, "地震情報通知発行");
    }

    /** 9611: 地震感知情報 解析結果 */
    private void postUserquakeEval(String title, String message) {
        // 「非表示」レベルは通知しない
        if (message.contains("信頼度低")) return;

        Notification n = baseBuilder(CHANNEL_INFO, title, message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_USERQUAKE, n);
    }

    /** 555/561: 一般情報（サイレント） */
    private void postInfo(String title, String message) {
        Notification n = baseBuilder(CHANNEL_INFO, title, message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_INFO, n);
    }

    // ──────────────────────────────────────────────
    //  通知キャンセル
    // ──────────────────────────────────────────────

    /** 津波予報解除時に呼ぶ */
    public void cancelTsunami() {
        nm.cancel(NOTIF_TSUNAMI);
        Log.d(TAG, "津波通知キャンセル");
    }

    /** EEW取消時に呼ぶ */
    public void cancelEEW() {
        nm.cancel(NOTIF_EEW);
        nm.cancel(NOTIF_EEW_DET);
        Log.d(TAG, "EEW通知キャンセル");
    }

    // ──────────────────────────────────────────────
    //  設定
    // ──────────────────────────────────────────────

    /** 通知全体のON/OFFを切り替える */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        Log.d(TAG, "通知 enabled=" + enabled);
    }

    public boolean isEnabled() { return enabled; }

    // ──────────────────────────────────────────────
    //  ヘルパー
    // ──────────────────────────────────────────────

    private NotificationCompat.Builder baseBuilder(String channel, String title, String message) {
        PendingIntent pi = PendingIntent.getActivity(
                context, 0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(context, channel)
                .setContentTitle(title)
                .setContentText(message)
                // BigTextStyle で長いメッセージも展開表示
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOnlyAlertOnce(false);
    }
}
