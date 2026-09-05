package com.xrc.system.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.service.CoreService;
import com.xrc.system.service.WatchdogService;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = Constants.TAG + ":Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            Log.i(TAG, "Boot received, starting services");
            try {
                Intent core = new Intent(context, CoreService.class);
                Intent watchdog = new Intent(context, WatchdogService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(core);
                    context.startForegroundService(watchdog);
                } else {
                    context.startService(core);
                    context.startService(watchdog);
                }
            } catch (Exception e) {
                Log.e(TAG, "Boot start failed", e);
            }
        }
    }
}
