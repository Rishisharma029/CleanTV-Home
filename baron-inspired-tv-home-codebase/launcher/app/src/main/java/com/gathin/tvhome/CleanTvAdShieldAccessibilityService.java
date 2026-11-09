package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Locale;

/**
 * CleanTV Background Ad-Shield Service:
 * Automatically skips and mutes commercial video ads across streaming apps
 * (Amazon Prime Video, YouTube TV, Hotstar, etc.) mirroring Brave Browser
 * ad-skipping behaviors directly on Android TV.
 */
public class CleanTvAdShieldAccessibilityService extends AccessibilityService {

    public static final String PREFS_NAME = "clean_tv_ad_shield_prefs";
    public static final String KEY_AUTO_SKIP = "auto_skip_enabled";
    public static final String KEY_AUTO_MUTE = "auto_mute_enabled";

    private AudioManager audioManager;
    private boolean isMutedByShield = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static CleanTvAdShieldAccessibilityService instance;

    public static CleanTvAdShieldAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onDestroy() {
        if (isMutedByShield && audioManager != null) {
            unmuteAudio();
        }
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean autoSkipEnabled = prefs.getBoolean(KEY_AUTO_SKIP, true);
        boolean autoMuteEnabled = prefs.getBoolean(KEY_AUTO_MUTE, true);

        if (!autoSkipEnabled && !autoMuteEnabled) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            boolean adDetected = false;

            // 1. Scan for and automatically click any "Skip" buttons
            if (autoSkipEnabled) {
                boolean clicked = scanAndClickSkip(root);
                if (clicked) {
                    adDetected = true;
                }
            }

            // 2. Scan for commercial / ad banners and handle auto-mute
            if (autoMuteEnabled) {
                if (!adDetected) {
                    adDetected = hasAdMarkers(root);
                }

                if (adDetected) {
                    if (!isMutedByShield) {
                        muteAudio();
                    }
                } else {
                    if (isMutedByShield) {
                        // Debounce unmute slightly to ensure ad transition completed
                        handler.postDelayed(() -> {
                            AccessibilityNodeInfo current = getRootInActiveWindow();
                            if (current != null && !hasAdMarkers(current)) {
                                unmuteAudio();
                            }
                        }, 500);
                    }
                }
            }
        } finally {
            root.recycle();
        }
    }

    private boolean scanAndClickSkip(AccessibilityNodeInfo node) {
        if (node == null) return false;

        CharSequence textSeq = node.getText();
        CharSequence descSeq = node.getContentDescription();
        String viewId = node.getViewIdResourceName();

        String text = textSeq != null ? textSeq.toString().toLowerCase(Locale.ROOT) : "";
        String desc = descSeq != null ? descSeq.toString().toLowerCase(Locale.ROOT) : "";
        String id = viewId != null ? viewId.toLowerCase(Locale.ROOT) : "";

        // Check if node is a skip button
        if (isSkipButton(text) || isSkipButton(desc) || id.contains("skip_ad") || id.contains("ad_skip") || id.contains("skipintro")) {
            if (node.isClickable()) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            // Check clickable parent
            AccessibilityNodeInfo parent = node.getParent();
            while (parent != null) {
                if (parent.isClickable()) {
                    boolean res = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    parent.recycle();
                    return res;
                }
                AccessibilityNodeInfo nextParent = parent.getParent();
                parent.recycle();
                parent = nextParent;
            }
        }

        // Recursively check children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean clicked = scanAndClickSkip(child);
                child.recycle();
                if (clicked) return true;
            }
        }

        return false;
    }

    private boolean isSkipButton(String str) {
        if (str == null || str.isEmpty()) return false;
        return str.contains("skip ad") || str.equals("skip") || str.contains("skip in") ||
                str.contains("skip intro") || str.contains("skip commercial") || str.contains("dismiss");
    }

    private boolean hasAdMarkers(AccessibilityNodeInfo node) {
        if (node == null) return false;

        CharSequence textSeq = node.getText();
        CharSequence descSeq = node.getContentDescription();
        String text = textSeq != null ? textSeq.toString().toLowerCase(Locale.ROOT) : "";
        String desc = descSeq != null ? descSeq.toString().toLowerCase(Locale.ROOT) : "";

        if (text.contains("ad 1 of") || text.contains("ad 2 of") || text.contains("sponsored") ||
                text.contains("commercial break") || text.contains("ad ·") || desc.contains("ad 1 of") ||
                desc.contains("sponsored") || desc.contains("commercial")) {
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean found = hasAdMarkers(child);
                child.recycle();
                if (found) return true;
            }
        }

        return false;
    }

    private synchronized void muteAudio() {
        if (audioManager == null || isMutedByShield) return;
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            isMutedByShield = true;
        } catch (Throwable ignored) {}
    }

    private synchronized void unmuteAudio() {
        if (audioManager == null || !isMutedByShield) return;
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            isMutedByShield = false;
        } catch (Throwable ignored) {}
    }

    @Override
    public void onInterrupt() {
        if (isMutedByShield) unmuteAudio();
    }
}
