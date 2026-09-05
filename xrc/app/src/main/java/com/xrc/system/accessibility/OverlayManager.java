package com.xrc.system.accessibility;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.xrc.system.core.Constants;

public class OverlayManager {
    private static final String TAG = Constants.TAG + ":Overlay";
    private static OverlayManager instance;
    private final Context ctx;
    private final WindowManager wm;
    private View overlayView;

    private OverlayManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized OverlayManager get(Context ctx) {
        if (instance == null) instance = new OverlayManager(ctx);
        return instance;
    }

    public boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(ctx);
        }
        return true;
    }

    public void showOverlay(View view) {
        if (!canDrawOverlays()) {
            Log.w(TAG, "Cannot draw overlays");
            return;
        }
        removeOverlay();
        overlayView = view;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(view, params);
        } catch (Exception e) {
            Log.e(TAG, "Show overlay failed", e);
        }
    }

    public void removeOverlay() {
        if (overlayView != null) {
            try {
                wm.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "Remove overlay failed", e);
            }
            overlayView = null;
        }
    }

    public void launchOverlay() {
        try {
            Intent intent = new Intent(ctx, com.xrc.system.ui.OverlayActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Launch overlay failed", e);
        }
    }
}
