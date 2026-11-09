package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.Toast;

/**
 * CleanTV Ad-Shield Mode Selector Dialog:
 * Empowers users to choose between:
 * 1. CleanTV Shield Streamer (Brave Browser-like video ad-interception & scriptlet engine)
 * 2. Background Auto-Skip & Auto-Mute (for native Prime Video and streaming TV apps)
 * 3. Maximum Protection (Both Active)
 * 4. System-Wide Private DNS Shielding
 */
public final class CleanTvAdShieldDialog {

    private CleanTvAdShieldDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        if (!CleanTvProDialog.isFeatureUnlocked(activity, CleanTvProDialog.FEATURE_AD_BLOCK)) {
            CleanTvProDialog.show(activity,
                    "👑 CleanTV Ad-Shield Engine (Brave Video Blocker) is reserved for CleanTV Pro members.\n\nUpgrade now to eliminate video ads on Prime Video, YouTube, and all TV streaming apps!");
            return;
        }

        SharedPreferences prefs = activity.getSharedPreferences(
                CleanTvAdShieldAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE);
        boolean autoSkip = prefs.getBoolean(CleanTvAdShieldAccessibilityService.KEY_AUTO_SKIP, true);
        boolean autoMute = prefs.getBoolean(CleanTvAdShieldAccessibilityService.KEY_AUTO_MUTE, true);
        boolean serviceActive = CleanTvAdShieldAccessibilityService.isRunning();

        String[] options = {
                "🚀 Launch CleanTV Shield Streamer (Brave Engine - Ad-Free Prime Video)",
                "🔇 Background Auto-Skip & Mute (For Native Prime Video App) [" + (serviceActive ? "ACTIVE" : "SETUP") + "]",
                "⚡ Maximum Protection (Shield Streamer + Background Auto-Skip)",
                "🌐 System-Wide Private DNS Filter (AdGuard / Control D / Mullvad)"
        };

        new AlertDialog.Builder(activity)
                .setTitle("🛡️ CleanTV Ad-Shield Center")
                .setItems(options, (dialog, which) -> {
                    dialog.dismiss();
                    switch (which) {
                        case 0:
                            // 1. Launch Shield Streamer
                            activity.startActivity(new Intent(activity, CleanTvShieldStreamerActivity.class));
                            break;

                        case 1:
                            // 2. Configure Background Auto-Skip & Mute
                            toggleBackgroundShield(activity);
                            break;

                        case 2:
                            // 3. Both: Enable background settings and launch streamer
                            prefs.edit()
                                    .putBoolean(CleanTvAdShieldAccessibilityService.KEY_AUTO_SKIP, true)
                                    .putBoolean(CleanTvAdShieldAccessibilityService.KEY_AUTO_MUTE, true)
                                    .apply();
                            Toast.makeText(activity, "⚡ Maximum Ad-Shielding Enabled!", Toast.LENGTH_SHORT).show();
                            activity.startActivity(new Intent(activity, CleanTvShieldStreamerActivity.class));
                            break;

                        case 3:
                            // 4. DNS Filter selection
                            AdBlockManager.showProviderSelectionDialog(activity);
                            break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private static void toggleBackgroundShield(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(
                CleanTvAdShieldAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE);
        boolean currentSkip = prefs.getBoolean(CleanTvAdShieldAccessibilityService.KEY_AUTO_SKIP, true);
        boolean currentMute = prefs.getBoolean(CleanTvAdShieldAccessibilityService.KEY_AUTO_MUTE, true);

        String[] settingsOptions = {
                "Auto-Click 'Skip Ad' Buttons: " + (currentSkip ? "ON ✓" : "OFF ✗"),
                "Auto-Mute Commercial Audio: " + (currentMute ? "ON ✓" : "OFF ✗"),
                "Open TV Accessibility Settings (to enable service)"
        };

        new AlertDialog.Builder(activity)
                .setTitle("Background Ad-Shield Settings")
                .setItems(settingsOptions, (d, sel) -> {
                    d.dismiss();
                    if (sel == 0) {
                        prefs.edit().putBoolean(CleanTvAdShieldAccessibilityService.KEY_AUTO_SKIP, !currentSkip).apply();
                        Toast.makeText(activity, "Auto-Skip set to: " + (!currentSkip ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
                        toggleBackgroundShield(activity);
                    } else if (sel == 1) {
                        prefs.edit().putBoolean(CleanTvAdShieldAccessibilityService.KEY_AUTO_MUTE, !currentMute).apply();
                        Toast.makeText(activity, "Auto-Mute set to: " + (!currentMute ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
                        toggleBackgroundShield(activity);
                    } else if (sel == 2) {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Throwable t) {
                            Toast.makeText(activity, "Open TV Settings > Device Preferences > Accessibility", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("Back", (d, w) -> show(activity))
                .show();
    }
}
