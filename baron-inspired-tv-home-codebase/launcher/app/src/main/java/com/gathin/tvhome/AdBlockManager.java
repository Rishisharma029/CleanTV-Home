package com.gathin.tvhome;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Ad-Blocker Add-on:
 * 1-Click Private DNS / Host Blocking to eliminate advertisements, banners, and telemetry
 * in third-party TV apps without needing root.
 */
public final class AdBlockManager {

    public static final int MODE_OFF = 0;
    public static final int MODE_ADGUARD = 1;
    public static final int MODE_MULLVAD = 2;
    public static final int MODE_CLOUDFLARE = 3;

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

    public static int getCurrentMode(Context context) {
        if (context == null) return MODE_OFF;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_DNS_MODE, MODE_OFF);
    }

    public static String getModeName(int mode) {
        switch (mode) {
            case MODE_ADGUARD:
                return "AdGuard DNS";
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
        int next = (current + 1) % 4;
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
            try {
                ContentResolver cr = activity.getContentResolver();
                if (mode == MODE_OFF) {
                    Settings.Global.putString(cr, "private_dns_mode", "off");
                    Settings.Global.putString(cr, "private_dns_specifier", "");
                } else {
                    Settings.Global.putString(cr, "private_dns_mode", "hostname");
                    Settings.Global.putString(cr, "private_dns_specifier", hostname);
                }
                appliedToSystem = true;
                Toast.makeText(activity, "Ad-Blocker: " + modeName + " Activated", Toast.LENGTH_SHORT).show();
            } catch (SecurityException se) {
                // WRITE_SECURE_SETTINGS not yet granted via ADB
                appliedToSystem = false;
                if (mode != MODE_OFF) {
                    Toast.makeText(activity, "Set Private DNS to: " + hostname, Toast.LENGTH_LONG).show();
                    openPrivateDnsSettings(activity);
                } else {
                    Toast.makeText(activity, "Ad-Blocker turned Off", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable ignored) {}
        } else {
            Toast.makeText(activity, "Ad-Blocker: " + modeName + " (Requires Android 9+ for system-wide DNS)", Toast.LENGTH_SHORT).show();
        }

        notifyListeners(mode, modeName, hostname);
        return appliedToSystem;
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
