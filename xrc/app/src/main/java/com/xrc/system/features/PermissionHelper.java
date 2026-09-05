package com.xrc.system.features;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import com.xrc.system.core.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PermissionHelper {
    private static final String TAG = Constants.TAG + ":Perm";
    private static PermissionHelper instance;
    private final Context ctx;

    private PermissionHelper(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized PermissionHelper get(Context ctx) {
        if (instance == null) instance = new PermissionHelper(ctx);
        return instance;
    }

    public boolean hasPermission(String perm) {
        return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(ctx);
        }
        return true;
    }

    public boolean isAccessibilityEnabled() {
        try {
            String enabled = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains(ctx.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isDeviceAdminActive() {
        return DeviceAdmin.get(ctx).isActive();
    }

    public boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        }
        return true;
    }

    public void autoGrantAll() {
        // This requires MANAGE_APP_OPS_MODES which is a system-level permission
        // On non-rooted devices, this will silently fail
        Log.w(TAG, "autoGrantAll requires system privileges");
    }

    public JSONObject getPermissionStatus() {
        JSONObject status = new JSONObject();
        try {
            JSONArray perms = new JSONArray();
            String[] required = {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            for (String p : required) {
                JSONObject obj = new JSONObject();
                obj.put("perm", p);
                obj.put("granted", hasPermission(p));
                perms.put(obj);
            }
            status.put("permissions", perms);
            status.put("overlay", canDrawOverlays());
            status.put("accessibility", isAccessibilityEnabled());
            status.put("device_admin", isDeviceAdminActive());
            status.put("battery_optimization", isIgnoringBatteryOptimizations());
        } catch (JSONException e) {
            Log.e(TAG, "Status JSON failed", e);
        }
        return status;
    }
}
