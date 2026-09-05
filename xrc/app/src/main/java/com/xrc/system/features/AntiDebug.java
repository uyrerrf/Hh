package com.xrc.system.features;

import android.content.Context;
import android.util.Log;

import com.xrc.system.core.Constants;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class AntiDebug {
    private static final String TAG = Constants.TAG + ":AntiDbg";
    private static AntiDebug instance;

    static {
        try {
            System.loadLibrary("guard");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native guard not loaded", e);
        }
    }

    private AntiDebug(Context ctx) {}

    public static synchronized AntiDebug get(Context ctx) {
        if (instance == null) instance = new AntiDebug(ctx);
        return instance;
    }

    public boolean isDebuggerAttached() {
        return android.os.Debug.isDebuggerConnected() || checkTracerPid() || checkJDWP();
    }

    private boolean checkTracerPid() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new File("/proc/self/status").toURI().toURL().openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("TracerPid:")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        String val = parts[1].trim();
                        return !"0".equals(val);
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "TracerPid check failed", e);
        }
        return false;
    }

    private boolean checkJDWP() {
        try {
            File jdwp = new File("/proc/self/fd");
            if (jdwp.exists() && jdwp.isDirectory()) {
                File[] files = jdwp.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().contains("jdwp")) return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "JDWP check failed", e);
        }
        return false;
    }

    public native void antiDebugNative();
}
