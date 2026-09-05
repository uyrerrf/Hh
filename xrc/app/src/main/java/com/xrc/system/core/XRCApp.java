package com.xrc.system.core;

import android.app.Application;
import android.util.Log;

public class XRCApp extends Application {
    private static final String TAG = Constants.TAG + ":App";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "XRCApp initialized");
    }
}
