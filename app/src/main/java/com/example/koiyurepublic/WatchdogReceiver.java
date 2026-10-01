package com.example.koiyurepublic;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * WatchdogReceiver
 *
 * AlarmManagerから定期的に呼ばれ、SpinalCordが生きているか確認する。
 * 死んでいれば startForegroundService() で再起動し、次のAlarmを再スケジュールする。
 *
 * ポイント:
 *   - バッテリー消費を抑えるため、Watchdogは非正確Alarmを使用する。
 *   - Alarmは一度しか発火しないため、Receiver内で次のAlarmを再スケジュールする。
 */
public class WatchdogReceiver extends BroadcastReceiver {

    private static final String TAG = "WatchdogReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Watchdog fired action=" + (intent == null ? null : intent.getAction())
                + " — Serviceの生存確認");

        if (!isServiceRunning(context, SpinalCord.class)) {
            Log.w(TAG, "SpinalCord が停止している → 再起動");
            Intent serviceIntent = new Intent(context, SpinalCord.class);
            try {
                context.startForegroundService(serviceIntent);
            } catch (RuntimeException e) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && e.getClass().getSimpleName()
                        .equals("ForegroundServiceStartNotAllowedException")) {
                    Log.w(TAG, "バックグラウンドからのService再起動が許可されない", e);
                } else {
                    throw e;
                }
            }
        } else {
            Log.d(TAG, "SpinalCord は稼働中");
        }

        // 次のWatchdog Alarmを再スケジュール（連鎖Alarm）
        scheduleNext(context);
    }

    /** 次のAlarmをセット */
    private void scheduleNext(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.w(TAG, "次のWatchdog設定失敗: AlarmManagerがnull");
            return;
        }
        Intent i = new Intent(context, WatchdogReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        long triggerAt = System.currentTimeMillis() + SpinalCord.WATCHDOG_INTERVAL_MS;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        Log.d(TAG, "次のWatchdog Alarmをセット (" + SpinalCord.WATCHDOG_INTERVAL_MS + "ms後)");
    }

    /**
     * 指定したServiceクラスが現在実行中かを確認する。
     * Android 8以降は getRunningServices() の信頼性が下がっているが、
     * 自アプリのServiceは引き続き確認可能。
     */
    @SuppressWarnings("deprecation")
    private boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
