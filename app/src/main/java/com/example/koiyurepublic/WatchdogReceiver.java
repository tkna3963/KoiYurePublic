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
 * ポイント：
 *   - setExactAndAllowWhileIdle は一度しか発火しないため、
 *     Receiver内で次のAlarmを自分でセットする「連鎖Alarm」方式を採用。
 *   - これにより AlarmManager → Receiver → AlarmManager の無限ループが成立し、
 *     Xiaomi/OPPO 等の強制終了からの復旧が可能になる。
 */
public class WatchdogReceiver extends BroadcastReceiver {

    private static final String TAG = "WatchdogReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Watchdog fired — Serviceの生存確認");

        if (!isServiceRunning(context, SpinalCord.class)) {
            Log.w(TAG, "SpinalCord が停止している → 再起動");
            Intent serviceIntent = new Intent(context, SpinalCord.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
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
        Intent i = new Intent(context, WatchdogReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + SpinalCord.WATCHDOG_INTERVAL_MS,
                pi
        );
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
