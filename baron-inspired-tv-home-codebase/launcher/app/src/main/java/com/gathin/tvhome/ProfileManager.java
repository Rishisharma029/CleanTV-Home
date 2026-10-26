package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Multi-Profile & Kids Guard Manager:
 * - Profiles:
 *   1) PERSONAL: Full access, custom dock, wallpaper, settings, library.
 *   2) KIDS: PIN-guarded child-safe profile. Whitelisted kids apps only,
 *            blocks store/browser/settings, enforces automatic bedtime screen limits.
 *   3) GUEST / CINEMA: Minimalist single-dock media interface for visitors.
 */
public final class ProfileManager {

    public static final String PROFILE_PERSONAL = "personal";
    public static final String PROFILE_KIDS = "kids";
    public static final String PROFILE_GUEST = "guest";

    private static final String PREFS_NAME = "cleantv_profiles";
    private static final String KEY_CURRENT_PROFILE = "active_profile";
    private static final String KEY_PARENT_PIN = "parent_pin";
    private static final String KEY_KIDS_BEDTIME_LIMIT_MIN = "kids_bedtime_limit_min";
    private static final String KEY_KIDS_SESSION_START = "kids_session_start_ms";
    private static final String KEY_KIDS_EXTRA_TIME_MS = "kids_extra_time_ms";
    private static final String KEY_CUSTOM_KIDS_WHITELIST = "custom_kids_whitelist";
    private static final String KEY_CUSTOM_GUEST_WHITELIST = "custom_guest_whitelist";

    public static final String DEFAULT_PIN = "0000";
    public static final int DEFAULT_KIDS_BEDTIME_MIN = 60; // 1 hour screen limit

    // Curated kids-safe packages whitelist
    private static final Set<String> DEFAULT_KIDS_WHITELIST = new HashSet<>(Arrays.asList(
            "com.google.android.youtube.tvkids",      // YouTube Kids (Android TV)
            "com.google.android.youtube.googletvkids",// YouTube Kids (Google TV)
            "com.disney.disneyplus",                 // Disney+
            "com.netflix.ninja",                     // Netflix (Kids profile inside)
            "org.pbskids.video",                     // PBS Kids Video
            "org.pbskids.games",                     // PBS Kids Games
            "com.nick.kids",                         // Nickelodeon
            "com.android.tv"                        // Live TV
    ));

    // Explicitly blocked packages in Kids Mode
    private static final Set<String> KIDS_BLOCKED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.vending",              // Google Play Store
            "com.android.tv.settings",          // System Settings
            "com.phlox.tvwebbrowser",           // TV Bro Browser
            "com.android.chrome"                // Chrome Browser
    ));

    // Guest profile allowed media/streaming packages whitelist
    private static final Set<String> GUEST_WHITELIST = new HashSet<>(Arrays.asList(
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvmusic",
            "com.android.tv",
            "org.videolan.vlc",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus"
    ));

    public interface ProfileChangeListener {
        void onProfileChanged(String newProfile);
        void onBedtimeLimitReached();
    }

    private static final List<ProfileChangeListener> listeners = new ArrayList<>();
    private static Handler bedtimeHandler;
    private static final Runnable bedtimeRunnable = ProfileManager::checkBedtimeTimer;

    private ProfileManager() {}

    public static synchronized void registerListener(ProfileChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static synchronized void unregisterListener(ProfileChangeListener listener) {
        listeners.remove(listener);
    }

    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getActiveProfile(Context context) {
        SharedPreferences p = getPrefs(context);
        return p != null ? p.getString(KEY_CURRENT_PROFILE, PROFILE_PERSONAL) : PROFILE_PERSONAL;
    }

    public static boolean isKidsMode(Context context) {
        return PROFILE_KIDS.equals(getActiveProfile(context));
    }

    public static boolean isGuestMode(Context context) {
        return PROFILE_GUEST.equals(getActiveProfile(context));
    }

    public static boolean isPersonalMode(Context context) {
        return PROFILE_PERSONAL.equals(getActiveProfile(context));
    }

    public static String getProfileDisplayName(String profile) {
        switch (profile) {
            case PROFILE_KIDS:
                return "Kids Mode";
            case PROFILE_GUEST:
                return "Guest / Cinema";
            case PROFILE_PERSONAL:
            default:
                return "Personal";
        }
    }

    public static String getProfileIcon(String profile) {
        switch (profile) {
            case PROFILE_KIDS:
                return "🎈";
            case PROFILE_GUEST:
                return "🍿";
            case PROFILE_PERSONAL:
            default:
                return "👤";
        }
    }

    public static String getParentPin(Context context) {
        SharedPreferences p = getPrefs(context);
        return p != null ? p.getString(KEY_PARENT_PIN, DEFAULT_PIN) : DEFAULT_PIN;
    }

    public static void setParentPin(Context context, String newPin) {
        SharedPreferences p = getPrefs(context);
        if (p != null && newPin != null && newPin.length() == 4) {
            p.edit().putString(KEY_PARENT_PIN, newPin).apply();
        }
    }

    public static boolean verifyPin(Context context, String inputPin) {
        if (inputPin == null) return false;
        return getParentPin(context).equals(inputPin.trim());
    }

    public static int getKidsBedtimeLimitMinutes(Context context) {
        return getPrefs(context).getInt(KEY_KIDS_BEDTIME_LIMIT_MIN, DEFAULT_KIDS_BEDTIME_MIN);
    }

    public static void setKidsBedtimeLimitMinutes(Context context, int minutes) {
        getPrefs(context).edit().putInt(KEY_KIDS_BEDTIME_LIMIT_MIN, Math.max(10, minutes)).apply();
    }

    public static void switchProfile(Context context, String targetProfile) {
        if (targetProfile == null) return;
        String current = getActiveProfile(context);
        if (current.equals(targetProfile)) return;

        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putString(KEY_CURRENT_PROFILE, targetProfile);

        if (PROFILE_KIDS.equals(targetProfile)) {
            // Start kids session timer
            editor.putLong(KEY_KIDS_SESSION_START, System.currentTimeMillis());
            editor.putLong(KEY_KIDS_EXTRA_TIME_MS, 0);
            startBedtimeMonitoring(context);
        } else {
            stopBedtimeMonitoring();
        }
        editor.apply();

        notifyProfileChanged(targetProfile);
    }

    public static void addExtraKidsTime(Context context, int extraMinutes) {
        long currentExtra = getPrefs(context).getLong(KEY_KIDS_EXTRA_TIME_MS, 0);
        long added = extraMinutes * 60L * 1000L;
        getPrefs(context).edit().putLong(KEY_KIDS_EXTRA_TIME_MS, currentExtra + added).apply();
        if (context != null) {
            startBedtimeMonitoring(context);
        }
    }

    public static Set<String> getCustomKidsAllowlist(Context context) {
        SharedPreferences p = getPrefs(context);
        return p != null ? new HashSet<>(p.getStringSet(KEY_CUSTOM_KIDS_WHITELIST, Collections.emptySet())) : new HashSet<>();
    }

    public static void addCustomKidsPackage(Context context, String packageName) {
        if (context == null || packageName == null || packageName.trim().isEmpty()) return;
        String cleanPkg = packageName.trim();
        if (KIDS_BLOCKED_PACKAGES.contains(cleanPkg)) return;
        Set<String> custom = getCustomKidsAllowlist(context);
        custom.add(cleanPkg);
        SharedPreferences p = getPrefs(context);
        if (p != null) {
            p.edit().putStringSet(KEY_CUSTOM_KIDS_WHITELIST, custom).apply();
            notifyProfileChanged(PROFILE_KIDS);
        }
    }

    public static void removeCustomKidsPackage(Context context, String packageName) {
        if (context == null || packageName == null) return;
        Set<String> custom = getCustomKidsAllowlist(context);
        if (custom.remove(packageName.trim())) {
            SharedPreferences p = getPrefs(context);
            if (p != null) {
                p.edit().putStringSet(KEY_CUSTOM_KIDS_WHITELIST, custom).apply();
                notifyProfileChanged(PROFILE_KIDS);
            }
        }
    }

    public static Set<String> getCustomGuestAllowlist(Context context) {
        SharedPreferences p = getPrefs(context);
        return p != null ? new HashSet<>(p.getStringSet(KEY_CUSTOM_GUEST_WHITELIST, Collections.emptySet())) : new HashSet<>();
    }

    public static void addCustomGuestPackage(Context context, String packageName) {
        if (context == null || packageName == null || packageName.trim().isEmpty()) return;
        Set<String> custom = getCustomGuestAllowlist(context);
        custom.add(packageName.trim());
        SharedPreferences p = getPrefs(context);
        if (p != null) {
            p.edit().putStringSet(KEY_CUSTOM_GUEST_WHITELIST, custom).apply();
            notifyProfileChanged(PROFILE_GUEST);
        }
    }

    public static void removeCustomGuestPackage(Context context, String packageName) {
        if (context == null || packageName == null) return;
        Set<String> custom = getCustomGuestAllowlist(context);
        if (custom.remove(packageName.trim())) {
            SharedPreferences p = getPrefs(context);
            if (p != null) {
                p.edit().putStringSet(KEY_CUSTOM_GUEST_WHITELIST, custom).apply();
                notifyProfileChanged(PROFILE_GUEST);
            }
        }
    }

    public static long getKidsTimeRemainingMillis(Context context) {
        if (!isKidsMode(context)) return Long.MAX_VALUE;
        long start = getPrefs(context).getLong(KEY_KIDS_SESSION_START, System.currentTimeMillis());
        long extra = getPrefs(context).getLong(KEY_KIDS_EXTRA_TIME_MS, 0);
        long allowedTotal = (getKidsBedtimeLimitMinutes(context) * 60L * 1000L) + extra;
        long elapsed = System.currentTimeMillis() - start;
        return Math.max(0, allowedTotal - elapsed);
    }

    public static boolean isBedtimeLimitReached(Context context) {
        if (!isKidsMode(context)) return false;
        return getKidsTimeRemainingMillis(context) <= 0;
    }

    public static String getFormattedKidsRemainingTime(Context context) {
        if (!isKidsMode(context)) return "";
        long millis = getKidsTimeRemainingMillis(context);
        long minutes = millis / (60 * 1000);
        long seconds = (millis / 1000) % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    /**
     * Checks if a package is allowed to be visible/launched in the current profile.
     */
    public static boolean isAppAllowed(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return false;
        String cleanPkg = packageName.trim();
        String active = getActiveProfile(context);
        if (PROFILE_PERSONAL.equals(active)) {
            return true;
        }

        if (PROFILE_KIDS.equals(active)) {
            if (KIDS_BLOCKED_PACKAGES.contains(cleanPkg)) return false;
            if (DEFAULT_KIDS_WHITELIST.contains(cleanPkg)) return true;
            return context != null && getCustomKidsAllowlist(context).contains(cleanPkg);
        }

        if (PROFILE_GUEST.equals(active)) {
            if (GUEST_WHITELIST.contains(cleanPkg)) return true;
            return context != null && getCustomGuestAllowlist(context).contains(cleanPkg);
        }

        return false;
    }

    public static boolean isAppAllowedForProfile(String profile, String packageName) {
        // A missing package is never a launchable app. Do not fail open on malformed input.
        if (packageName == null || packageName.trim().isEmpty()) return false;
        String cleanPkg = packageName.trim();
        if (profile == null || PROFILE_PERSONAL.equals(profile)) {
            return true; // Personal has no restrictions
        }

        if (PROFILE_KIDS.equals(profile)) {
            if (KIDS_BLOCKED_PACKAGES.contains(cleanPkg)) return false;
            // Kids Mode is a strict package allowlist. Substring checks (e.g. "kids",
            // "disney", or "cartoon") can accidentally trust unrelated or malicious packages.
            return DEFAULT_KIDS_WHITELIST.contains(cleanPkg);
        }

        if (PROFILE_GUEST.equals(profile)) {
            // Guest Mode is also allowlist-only; unknown packages stay hidden/blocked.
            return GUEST_WHITELIST.contains(cleanPkg);
        }

        // An unrecognized profile identifier must not silently receive unrestricted access.
        return false;
    }

    public static boolean isSettingsAllowed(Context context) {
        return isPersonalMode(context);
    }

    public static boolean isReorderAllowed(Context context) {
        return isPersonalMode(context);
    }

    public static boolean isAppLibraryAllowed(Context context) {
        return isPersonalMode(context);
    }

    private static void notifyProfileChanged(String newProfile) {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (ProfileChangeListener l : new ArrayList<>(listeners)) {
                try {
                    l.onProfileChanged(newProfile);
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void notifyBedtimeReached() {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (ProfileChangeListener l : new ArrayList<>(listeners)) {
                try {
                    l.onBedtimeLimitReached();
                } catch (Throwable ignored) {}
            }
        });
    }

    private static Context appContextRef;

    private static void startBedtimeMonitoring(Context context) {
        appContextRef = context.getApplicationContext();
        if (bedtimeHandler == null) {
            bedtimeHandler = new Handler(Looper.getMainLooper());
        }
        bedtimeHandler.removeCallbacks(bedtimeRunnable);
        bedtimeHandler.postDelayed(bedtimeRunnable, 15000); // Check every 15 seconds
    }

    private static void stopBedtimeMonitoring() {
        if (bedtimeHandler != null) {
            bedtimeHandler.removeCallbacks(bedtimeRunnable);
        }
    }

    private static void checkBedtimeTimer() {
        if (appContextRef == null || !isKidsMode(appContextRef)) {
            return;
        }
        if (isBedtimeLimitReached(appContextRef)) {
            notifyBedtimeReached();
        } else if (bedtimeHandler != null) {
            bedtimeHandler.postDelayed(bedtimeRunnable, 15000);
        }
    }
}
