package com.xrc.system.core;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xrc.system.service.CoreService;
import com.xrc.system.service.WatchdogService;

public class SelfHealer {
    private static final String TAG = Constants.TAG + ":Healer";
    private static final long CHECK_INTERVAL = 30000;
    private static SelfHealer instance;
    private final Context ctx;
    private final Handler handler;
    private Runnable healTask;

    private SelfHealer(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
    }

    public static synchronized SelfHealer get(Context ctx) {
        if (instance == null) {
            instance = new SelfHealer(ctx);
        }
        return instance;
    }

    public void start() {
        healTask = () -> {
            ensureService(CoreService.class);
            ensureService(WatchdogService.class);
            handler.postDelayed(healTask, CHECK_INTERVAL);
        };
        handler.postDelayed(healTask, CHECK_INTERVAL);
    }

    public void stop() {
        if (healTask != null) {
            handler.removeCallbacks(healTask);
        }
    }

    public void triggerRestart() {
        Log.w(TAG, "Triggering restart");
        ensureService(CoreService.class);
        ensureService(WatchdogService.class);
    }

    private void ensureService(Class<?> cls) {
        try {
            Intent intent = new Intent(ctx, cls);
            intent.setAction(Constants.ACTION_START_SERVICES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent);
            } else {
                ctx.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Service restart failed: " + cls.getSimpleName(), e);
        }
    }
}
