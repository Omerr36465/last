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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SamuraiAccessibilityService extends AccessibilityService {
    private static final String TAG = "SamuraiBot";
    private static final String TARGET_PACKAGE = "delivery.samurai.android";

    // Pre-built view ID constants to avoid repeated string concatenation each cycle
    private static final String VIEW_ID_BRANCH = TARGET_PACKAGE + ":id/tv_branch";
    private static final String VIEW_ID_BOOK_SHIFT = TARGET_PACKAGE + ":id/btnBookShift";
    private static final String VIEW_ID_SWIPE_REFRESH = TARGET_PACKAGE + ":id/swipeRefresh";

    // Pre-built confirmation text arrays indexed by shift type (lower-cased for O(1) matching)
    private static final String[] FULL_SHIFT_TEXTS = {"full shift", "\u0634\u0641\u062A \u0643\u0627\u0645\u0644", "\u0643\u0627\u0645\u0644"};
    private static final String[] PARTIAL_SHIFT_TEXTS = {"partial shift", "\u0634\u0641\u062A \u062C\u0632\u0626\u064A", "\u062C\u0632\u0626\u064A"};
    private static final String[] CONFIRM_TEXTS = {"book", "\u062D\u062C\u0632", "yes", "\u0646\u0639\u0645", "confirm", "\u062A\u0623\u0643\u064A\u062F"};

    // Pre-built lookup set of all confirmation texts for O(1) membership testing during tree traversal
    private static final Set<String> ALL_CONFIRMATION_TEXTS;
    static {
        Set<String> texts = new HashSet<>();
        for (String s : FULL_SHIFT_TEXTS) texts.add(s);
        for (String s : PARTIAL_SHIFT_TEXTS) texts.add(s);
        for (String s : CONFIRM_TEXTS) texts.add(s);
        ALL_CONFIRMATION_TEXTS = Collections.unmodifiableSet(texts);
    }

    private boolean isRunning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String targetBranch = "";
    private boolean bookFullShift = true;

    // Per-cycle cache for view ID lookups to avoid redundant tree traversals.
    // The same view ID (e.g. btnBookShift) is queried across multiple methods per cycle;
    // caching reduces repeated O(tree_size) traversals to O(1) after the first lookup.
    private final Map<String, List<AccessibilityNodeInfo>> viewIdCache = new HashMap<>();

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

    /**
     * Retrieves nodes by view ID, using a per-cycle cache to avoid redundant tree traversals.
     * For example, VIEW_ID_BOOK_SHIFT is queried in isAreaListingScreen(), handleBranchSelection(),
     * and handleShiftBooking() — caching turns the 2nd and 3rd lookups from O(tree_size) to O(1).
     */
    private List<AccessibilityNodeInfo> findNodesByViewIdCached(AccessibilityNodeInfo rootNode, String viewId) {
        List<AccessibilityNodeInfo> cached = viewIdCache.get(viewId);
        if (cached != null) return cached;
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes == null) nodes = Collections.emptyList();
        viewIdCache.put(viewId, nodes);
        return nodes;
    }

    private void performAutomation() {
        // Clear per-cycle view ID cache at the start of each automation cycle
        viewIdCache.clear();

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
        // Uses cached lookups — also pre-populates the cache for handleBranchSelection/handleShiftBooking
        List<AccessibilityNodeInfo> branchNodes = findNodesByViewIdCached(rootNode, VIEW_ID_BRANCH);
        if (branchNodes.isEmpty()) return false;
        List<AccessibilityNodeInfo> bookNodes = findNodesByViewIdCached(rootNode, VIEW_ID_BOOK_SHIFT);
        return bookNodes != null;
    }

    private void handleBranchSelection(AccessibilityNodeInfo rootNode) {
        if (targetBranch.isEmpty()) return;

        List<AccessibilityNodeInfo> branchNodes = findNodesByViewIdCached(rootNode, VIEW_ID_BRANCH);
        if (branchNodes.isEmpty()) return;

        // Build a branch name -> node index for efficient lookup
        Map<String, AccessibilityNodeInfo> branchIndex = new HashMap<>(branchNodes.size());
        for (AccessibilityNodeInfo node : branchNodes) {
            String branchName = node.getText() != null ? node.getText().toString() : "";
            if (!branchName.isEmpty()) {
                branchIndex.put(branchName, node);
            }
        }

        // Try O(1) exact match first via the HashMap index
        AccessibilityNodeInfo matchedNode = branchIndex.get(targetBranch);

        // Fall back to contains matching for partial name matches
        if (matchedNode == null) {
            for (Map.Entry<String, AccessibilityNodeInfo> entry : branchIndex.entrySet()) {
                if (entry.getKey().contains(targetBranch)) {
                    matchedNode = entry.getValue();
                    break;
                }
            }
        }

        if (matchedNode == null) return;

        // Found the target branch, now find the "Shifts" button in the same row
        String branchName = matchedNode.getText().toString();
        AccessibilityNodeInfo parent = matchedNode.getParent();
        while (parent != null) {
            List<AccessibilityNodeInfo> buttons = parent.findAccessibilityNodeInfosByViewId(VIEW_ID_BOOK_SHIFT);
            if (buttons != null && !buttons.isEmpty()) {
                buttons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Selected Branch: " + branchName);
                return;
            }
            parent = parent.getParent();
        }
    }

    private boolean isShiftListingScreen(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> nodes = findNodesByViewIdCached(rootNode, VIEW_ID_SWIPE_REFRESH);
        return !nodes.isEmpty();
    }

    private void handleShiftBooking(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> bookButtons = findNodesByViewIdCached(rootNode, VIEW_ID_BOOK_SHIFT);
        if (bookButtons.isEmpty()) {
            performSwipeDown();
            return;
        }

        for (AccessibilityNodeInfo button : bookButtons) {
            if (button.isEnabled() && button.isVisibleToUser()) {
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Attempting to book shift");
                return;
            }
        }
    }

    /**
     * Handles confirmation dialogs using a single-pass text index.
     * Instead of calling findAccessibilityNodeInfosByText N times (each traversing the
     * entire accessibility tree), we traverse the tree once and build a HashMap index
     * mapping text content to matching nodes. This reduces N tree traversals to 1.
     */
    private void handleConfirmations(AccessibilityNodeInfo rootNode) {
        String[] shiftTexts = bookFullShift ? FULL_SHIFT_TEXTS : PARTIAL_SHIFT_TEXTS;

        // Collect the specific texts we need to find in this cycle
        Set<String> targetTexts = new HashSet<>(shiftTexts.length + CONFIRM_TEXTS.length);
        for (String s : shiftTexts) targetTexts.add(s);
        for (String s : CONFIRM_TEXTS) targetTexts.add(s);

        // Build text index with a single tree traversal instead of N separate traversals
        Map<String, List<AccessibilityNodeInfo>> textIndex = new HashMap<>();
        buildTextIndex(rootNode, targetTexts, textIndex);

        // Click shift type buttons using the index (O(1) lookup per text)
        for (String text : shiftTexts) {
            clickFromTextIndex(textIndex, text);
        }

        // Click general confirmation buttons using the index (O(1) lookup per text)
        for (String text : CONFIRM_TEXTS) {
            clickFromTextIndex(textIndex, text);
        }
    }

    /**
     * Traverses the accessibility tree once and builds an index mapping target text
     * strings to their matching nodes. This replaces N separate findAccessibilityNodeInfosByText
     * calls (each O(tree_size)) with a single O(tree_size) traversal.
     *
     * Matches use case-insensitive containment to replicate findAccessibilityNodeInfosByText behavior.
     * Both node text and content description are checked.
     */
    private void buildTextIndex(AccessibilityNodeInfo node, Set<String> targetTexts,
                                Map<String, List<AccessibilityNodeInfo>> index) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null) {
            String textLower = text.toString().toLowerCase();
            for (String target : targetTexts) {
                if (textLower.contains(target)) {
                    List<AccessibilityNodeInfo> list = index.get(target);
                    if (list == null) {
                        list = new ArrayList<>();
                        index.put(target, list);
                    }
                    list.add(node);
                }
            }
        }

        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String descLower = desc.toString().toLowerCase();
            for (String target : targetTexts) {
                if (descLower.contains(target)) {
                    List<AccessibilityNodeInfo> list = index.get(target);
                    if (list == null) {
                        list = new ArrayList<>();
                        index.put(target, list);
                    }
                    list.add(node);
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            buildTextIndex(node.getChild(i), targetTexts, index);
        }
    }

    /**
     * Clicks a node found via the pre-built text index. Walks up the parent chain
     * to find a clickable ancestor if the node itself isn't clickable.
     */
    private void clickFromTextIndex(Map<String, List<AccessibilityNodeInfo>> textIndex, String text) {
        List<AccessibilityNodeInfo> nodes = textIndex.get(text);
        if (nodes == null || nodes.isEmpty()) return;

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
