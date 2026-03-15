package com.example.samuraibot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class SamuraiAccessibilityService extends AccessibilityService {
    private static final String TAG = "SamuraiBot";
    private static final String TARGET_PACKAGE = "delivery.samurai.android";
    private boolean isRunning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private String targetBranch = "";
    private boolean bookFullShift = true;

    private final Runnable automationRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                loadPreferences();
                performAutomation();
                handler.postDelayed(this, 1500); // Check every 1.5 seconds for faster response
            }
        }
    };

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences("SamuraiBotPrefs", Context.MODE_PRIVATE);
        targetBranch = prefs.getString("target_branch", "");
        bookFullShift = prefs.getBoolean("book_full_shift", true);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        isRunning = false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isRunning = true;
        handler.post(automationRunnable);
        Log.d(TAG, "Service Connected with Advanced Features");
    }

    private void performAutomation() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        if (!TARGET_PACKAGE.equals(rootNode.getPackageName())) {
            return;
        }

        // 1. Handle Branch Selection if we are on Area Listing screen
        if (isAreaListingScreen(rootNode)) {
            handleBranchSelection(rootNode);
            return;
        }

        // 2. Handle Shift Booking if we are on Shift Listing screen
        if (isShiftListingScreen(rootNode)) {
            handleShiftBooking(rootNode);
            return;
        }

        // 3. Handle Confirmation Dialogs
        handleConfirmations(rootNode);
    }

    private boolean isAreaListingScreen(AccessibilityNodeInfo rootNode) {
        // Check for activity_area_listing elements
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(TARGET_PACKAGE + ":id/tv_branch");
        return nodes != null && !nodes.isEmpty() && rootNode.findAccessibilityNodeInfosByViewId(TARGET_PACKAGE + ":id/btnBookShift") != null;
    }

    private void handleBranchSelection(AccessibilityNodeInfo rootNode) {
        if (targetBranch.isEmpty()) return;

        List<AccessibilityNodeInfo> branchNodes = rootNode.findAccessibilityNodeInfosByViewId(TARGET_PACKAGE + ":id/tv_branch");
        if (branchNodes != null) {
            for (AccessibilityNodeInfo node : branchNodes) {
                String branchName = node.getText() != null ? node.getText().toString() : "";
                if (branchName.contains(targetBranch)) {
                    // Found the target branch, now find the "Shifts" button in the same row
                    AccessibilityNodeInfo parent = node.getParent();
                    while (parent != null) {
                        List<AccessibilityNodeInfo> buttons = parent.findAccessibilityNodeInfosByViewId(TARGET_PACKAGE + ":id/btnBookShift");
                        if (buttons != null && !buttons.isEmpty()) {
                            buttons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.d(TAG, "Selected Branch: " + branchName);
                            return;
                        }
                        parent = parent.getParent();
                    }
                }
            }
        }
    }

    private boolean isShiftListingScreen(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(TARGET_PACKAGE + ":id/swipeRefresh");
        return nodes != null && !nodes.isEmpty();
    }

    private void handleShiftBooking(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> bookButtons = rootNode.findAccessibilityNodeInfosByViewId(TARGET_PACKAGE + ":id/btnBookShift");
        if (bookButtons == null || bookButtons.isEmpty()) {
            performSwipeDown();
            return;
        }

        for (AccessibilityNodeInfo button : bookButtons) {
            if (button.isEnabled() && button.isVisibleToUser()) {
                // If user wants partial shift, we might need to look for a specific button or text
                // For now, we click the main book button. If a dialog appears for partial/full, 
                // handleConfirmations will take care of it.
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Attempting to book shift");
                return;
            }
        }
    }

    private void handleConfirmations(AccessibilityNodeInfo rootNode) {
        if (bookFullShift) {
            clickByText(rootNode, "Full Shift");
            clickByText(rootNode, "شفت كامل");
            clickByText(rootNode, "كامل");
        } else {
            clickByText(rootNode, "Partial Shift");
            clickByText(rootNode, "شفت جزئي");
            clickByText(rootNode, "جزئي");
        }

        // General confirmation buttons
        clickByText(rootNode, "Book");
        clickByText(rootNode, "حجز");
        clickByText(rootNode, "Yes");
        clickByText(rootNode, "نعم");
        clickByText(rootNode, "Confirm");
        clickByText(rootNode, "تأكيد");
    }

    private void clickByText(AccessibilityNodeInfo rootNode, String text) {
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                } else {
                    AccessibilityNodeInfo parent = node.getParent();
                    while (parent != null) {
                        if (parent.isClickable()) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            break;
                        }
                        parent = parent.getParent();
                    }
                }
            }
        }
    }

    private void performSwipeDown() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        Path path = new Path();
        path.moveTo(width / 2f, height * 0.3f);
        path.lineTo(width / 2f, height * 0.7f);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 100, 500));
        dispatchGesture(builder.build(), null, null);
    }
}
