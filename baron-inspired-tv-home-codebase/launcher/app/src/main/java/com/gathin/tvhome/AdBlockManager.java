package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * CleanTV Universal System-Wide Ad-Blocker Engine:
 * Eliminates advertisements, banner ads, tracking beacons, OEM telemetry,
 * and malicious data mining across all third-party TV apps using Android's
 * native Private DNS over TLS (DoT) with zero RAM/CPU battery overhead.
 */
public final class AdBlockManager {

    public static final int MODE_OFF = 0;
    public static final int MODE_ADGUARD = 1;
    public static final int MODE_FAMILY = 2;
    public static final int MODE_CONTROLD = 3;
    public static final int MODE_MULLVAD = 4;
    public static final int MODE_CLOUDFLARE = 5;

    private static final String PREFS_NAME = "adblock_prefs";
    private static final String KEY_DNS_MODE = "private_dns_mode";

    public interface AdBlockListener {
        void onAdBlockChanged(int mode, String modeName, String hostname);
    }

    private static final List<AdBlockListener> listeners = new ArrayList<>();

    private AdBlockManager() {}

    public static synchronized void registerListener(AdBlockListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static synchronized void unregisterListener(AdBlockListener listener) {
        listeners.remove(listener);
    }

    public static boolean hasSecureSettingsPermission(Context context) {
        if (context == null) return false;
        try {
            return context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getCurrentMode(Context context) {
        if (context == null) return MODE_OFF;

        // 1. Query live system Private DNS state from Android Global Settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ContentResolver cr = context.getContentResolver();
                String mode = Settings.Global.getString(cr, "private_dns_mode");
                String spec = Settings.Global.getString(cr, "private_dns_specifier");

                if ("hostname".equalsIgnoreCase(mode) && spec != null && !spec.trim().isEmpty()) {
                    String s = spec.toLowerCase();
                    if (s.contains("family.adguard")) return MODE_FAMILY;
                    if (s.contains("adguard")) return MODE_ADGUARD;
                    if (s.contains("controld") || s.contains("freedns")) return MODE_CONTROLD;
                    if (s.contains("mullvad")) return MODE_MULLVAD;
                    if (s.contains("cloudflare")) return MODE_CLOUDFLARE;
                    return MODE_ADGUARD;
                } else if ("off".equalsIgnoreCase(mode)) {
                    return MODE_OFF;
                }
            } catch (Throwable ignored) {}
        }

        // 2. Fall back to local preferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_DNS_MODE, MODE_OFF);
    }

    public static String getModeName(int mode) {
        switch (mode) {
            case MODE_ADGUARD:
                return "AdGuard Ad-Shield";
            case MODE_FAMILY:
                return "AdGuard Family Shield";
            case MODE_CONTROLD:
                return "Control D Edge";
            case MODE_MULLVAD:
                return "Mullvad AdBlock";
            case MODE_CLOUDFLARE:
                return "Cloudflare Secure";
            case MODE_OFF:
            default:
                return "Off (ISP DNS)";
        }
    }

    public static String getDnsHostname(int mode) {
        switch (mode) {
            case MODE_ADGUARD:
                return "dns.adguard-dns.com";
            case MODE_FAMILY:
                return "family.adguard-dns.com";
            case MODE_CONTROLD:
                return "p2.freedns.controld.com";
            case MODE_MULLVAD:
                return "adblock.dns.mullvad.net";
            case MODE_CLOUDFLARE:
                return "security.cloudflare-dns.com";
            case MODE_OFF:
            default:
                return "";
        }
    }

    public static int cycleNext(Activity activity) {
        int current = getCurrentMode(activity);
        // Toggle between OFF and ADGUARD (default full ad blocking) on short click
        int next = (current == MODE_OFF) ? MODE_ADGUARD : MODE_OFF;
        setMode(activity, next);
        return next;
    }

    public static boolean setMode(Activity activity, int mode) {
        if (activity == null) return false;

        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_DNS_MODE, mode).apply();

        String hostname = getDnsHostname(mode);
        String modeName = getModeName(mode);
        boolean appliedToSystem = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContentResolver cr = activity.getContentResolver();
            if (hasSecureSettingsPermission(activity)) {
                try {
                    if (mode == MODE_OFF) {
                        Settings.Global.putString(cr, "private_dns_mode", "off");
                        Settings.Global.putString(cr, "private_dns_specifier", "");
                        Toast.makeText(activity, "CleanTV: Ad-Blocker Turned Off", Toast.LENGTH_SHORT).show();
                    } else {
                        Settings.Global.putString(cr, "private_dns_mode", "hostname");
                        Settings.Global.putString(cr, "private_dns_specifier", hostname);
                        Toast.makeText(activity, "🛡️ CleanTV: System-Wide Ad-Shield Active (" + modeName + ")", Toast.LENGTH_LONG).show();
                    }
                    appliedToSystem = true;
                } catch (Throwable t) {
                    appliedToSystem = false;
                }
            } else {
                appliedToSystem = false;
                if (mode != MODE_OFF) {
                    showAdBlockSetupDialog(activity, mode, hostname);
                } else {
                    Toast.makeText(activity, "Ad-Blocker turned Off", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(activity, "Ad-Blocker: " + modeName + " (Requires Android 9+ for system-wide Private DNS)", Toast.LENGTH_SHORT).show();
        }

        notifyListeners(mode, modeName, hostname);
        return appliedToSystem;
    }

    public static void showAdBlockSetupDialog(Activity activity, int targetMode, String hostname) {
        if (activity == null || activity.isFinishing()) return;

        new AlertDialog.Builder(activity)
                .setTitle("🛡️ Activate System-Wide Ad-Shield")
                .setMessage("CleanTV blocks advertisements, video trackers, banner popups, and OEM data mining across ALL TV apps using Android's native Private DNS over TLS.\n\nTo activate system-wide protection, choose an option below:")
                .setPositiveButton("Open TV DNS Settings", (d, w) -> {
                    Toast.makeText(activity, "Select 'Private DNS provider hostname' and enter:\n" + hostname, Toast.LENGTH_LONG).show();
                    openPrivateDnsSettings(activity);
                })
                .setNeutralButton("CleanTV Windows Companion", (d, w) -> {
                    new AlertDialog.Builder(activity)
                            .setTitle("CleanTV Windows Companion")
                            .setMessage("Open CleanTV on your PC while connected to your TV (via Wi-Fi or USB) and click 'Enable System-Wide AdBlock'.\n\nCleanTV Desktop automatically grants elevated permissions and activates AdGuard protection in 1 second with zero typing!")
                            .setPositiveButton("OK", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showProviderSelectionDialog(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        String[] providers = {
                "🛡️ AdGuard DNS (Recommended - Full Ad & Tracker Shield)",
                "👨‍👩‍👦 AdGuard Family (Ad-Shield + Adult & Phishing Protection)",
                "⚡ Control D Edge (High Speed Low Latency Filter)",
                "🔒 Mullvad AdBlock (Strict Open-Source Filter)",
                "❌ Disable Ad-Blocker (Revert to Stock Network)"
        };
        int[] modes = { MODE_ADGUARD, MODE_FAMILY, MODE_CONTROLD, MODE_MULLVAD, MODE_OFF };

        int currentMode = getCurrentMode(activity);
        int selectedIndex = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == currentMode) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(activity)
                .setTitle("Select Ad-Shield Provider")
                .setSingleChoiceItems(providers, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    setMode(activity, modes[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void openPrivateDnsSettings(Activity activity) {
        if (activity == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable t) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(fallback);
            } catch (Throwable ignored) {}
        }
    }

    private static synchronized void notifyListeners(int mode, String modeName, String hostname) {
        for (AdBlockListener l : listeners) {
            try {
                l.onAdBlockChanged(mode, modeName, hostname);
            } catch (Throwable ignored) {}
        }
    }
}
