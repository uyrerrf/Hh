package com.xrc.system.features;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.XRCWebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

public class AntiAnalysis {
    private static final String TAG = Constants.TAG + ":Anti";
    private static AntiAnalysis instance;
    private final Context ctx;
    private final Handler bgHandler;

    private AntiAnalysis(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.bgHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized AntiAnalysis get(Context ctx) {
        if (instance == null) instance = new AntiAnalysis(ctx);
        return instance;
    }

    public void runChecks() {
        new Thread(() -> {
            try {
                JSONObject result = new JSONObject();
                result.put("emulator", isEmulator());
                result.put("frida", isFrida());
                result.put("root", isRooted());
                result.put("debug", isDebug());
                result.put("vpn", isVpn());
                result.put("proxy", isProxy());
                result.put("tampered", isTampered());
                XRCWebSocketClient.get(ctx).sendEvent("anti_analysis", result);
            } catch (JSONException e) {
                Log.e(TAG, "Check result failed", e);
            }
        }).start();
    }

    private boolean isEmulator() {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT));
    }

    private boolean isFrida() {
        String[] paths = {
            "/data/local/tmp/frida-server",
            "/data/local/tmp/frida",
            "/data/adb/frida-server",
            "/data/adb/frida",
            "/system/bin/frida-server",
            "/system/xbin/frida-server"
        };
        for (String p : paths) {
            if (new File(p).exists()) return true;
        }
        // Check for frida processes using sh -c for pipe support
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"sh", "-c", "ps -A | grep frida"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("frida")) {
                    proc.destroy();
                    return true;
                }
            }
            proc.waitFor();
            proc.destroy();
        } catch (Exception e) {
            Log.d(TAG, "Frida process check failed", e);
        }
        return false;
    }

    private boolean isRooted() {
        String[] paths = {
            "/system/app/Superuser.apk",
            "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su"
        };
        for (String p : paths) {
            if (new File(p).exists()) return true;
        }
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"which", "su"});
            int exit = proc.waitFor();
            proc.destroy();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDebug() {
        try {
            return (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isVpn() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(interfaces)) {
                String name = iface.getName().toLowerCase();
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("wg")
                        || name.contains("vpn") || name.contains("pptp")) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "VPN check failed", e);
        }
        return false;
    }

    private boolean isProxy() {
        String proxy = System.getProperty("http.proxyHost");
        String port = System.getProperty("http.proxyPort");
        return proxy != null && !proxy.isEmpty() && !"0".equals(port);
    }

    private boolean isTampered() {
        try {
            PackageManager pm = ctx.getPackageManager();
            String installer = pm.getInstallerPackageName(ctx.getPackageName());
            if (installer == null || !"com.android.vending".equals(installer)) {
                return true;
            }
            pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
        } catch (Exception e) {
            return true;
        }
        return false;
    }
}
