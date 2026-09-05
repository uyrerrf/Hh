package com.xrc.system.receiver;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;

public class DeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = Constants.TAG + ":AdminRecv";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "Device admin enabled");
        ConfigManager.get(context).setBoolean(Constants.PREF_DEVICE_ADMIN, true);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.w(TAG, "Device admin disabled");
        ConfigManager.get(context).setBoolean(Constants.PREF_DEVICE_ADMIN, false);
    }

    @Override
    public void onPasswordChanged(Context context, Intent intent) {
        super.onPasswordChanged(context, intent);
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
    }
}
