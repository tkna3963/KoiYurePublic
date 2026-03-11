package com.example.koiyurepublic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * BootReceiver
 *
 * 端末再起動後に BOOT_COMPLETED / QUICKBOOT_POWERON を受け取り、
 * SpinalCord を自動起動する。
 *
 * 必要なManifestパーミッション:
 *   <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.d(TAG, "Boot completed — SpinalCord を起動");
            Intent serviceIntent = new Intent(context, SpinalCord.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
