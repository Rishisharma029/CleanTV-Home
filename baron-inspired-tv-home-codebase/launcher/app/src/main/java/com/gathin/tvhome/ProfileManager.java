package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

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
    private static final String KEY_PARENT_PIN = "parent_pin"; // Legacy plaintext key for migration
    private static final String KEY_PIN_HASH = "parent_pin_hash";
    private static final String KEY_PIN_SALT = "parent_pin_salt";
    private static final String KEY_PIN_FAILED_ATTEMPTS = "parent_pin_failed_attempts";
    private static final String KEY_PIN_LOCKOUT_UNTIL_MS = "parent_pin_lockout_until_ms";
    private static final String KEY_KIDS_BEDTIME_LIMIT_MIN = "kids_bedtime_limit_min";
    private static final String KEY_KIDS_SESSION_START = "kids_session_start_ms";
    private static final String KEY_KIDS_EXTRA_TIME_MS = "kids_extra_time_ms";
    private static final String KEY_CUSTOM_KIDS_WHITELIST = "custom_kids_whitelist";
    private static final String KEY_CUSTOM_GUEST_WHITELIST = "custom_guest_whitelist";

    public static final int MAX_FAILED_PIN_ATTEMPTS = 5;
    public static final long PIN_LOCKOUT_DURATION_MS = 30_000L;
    public static final long MAX_PIN_LOCKOUT_DURATION_MS = 300_000L; // 5 min safety cap for clock anomalies
    public static final int DEFAULT_KIDS_BEDTIME_MIN = 60; // 1 hour screen limit

    private static final int SALT_BYTES = 16;
    private static final int PBKDF2_ITERATIONS = 10000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

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

    private static SharedPreferences testPrefs = null;

    public static void setTestPreferences(SharedPreferences prefs) {
        testPrefs = prefs;
    }

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
        if (testPrefs != null) return testPrefs;
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

    // --- PIN SECURITY & CREDENTIAL STORAGE ---

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) return new byte[0];
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static byte[] generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static byte[] derivePinHash(String pin, byte[] salt) {
        if (pin == null || salt == null) return null;
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Throwable t) {
            // Fallback: salted iterated SHA-256
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(salt);
                byte[] current = md.digest(pin.getBytes(StandardCharsets.UTF_8));
                for (int i = 0; i < PBKDF2_ITERATIONS; i++) {
                    md.reset();
                    md.update(current);
                    md.update(salt);
                    current = md.digest(pin.getBytes(StandardCharsets.UTF_8));
                }
                return current;
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /**
     * Checks if parent PIN has been configured.
     * Migrates legacy plaintext PIN if present (purging "0000").
     */
    public static boolean isPinConfigured(Context context) {
        SharedPreferences p = getPrefs(context);
        if (p == null) return false;

        // Migrate legacy plaintext PIN if present
        if (p.contains(KEY_PARENT_PIN)) {
            String legacyPin = p.getString(KEY_PARENT_PIN, null);
            p.edit().remove(KEY_PARENT_PIN).apply();
            if (legacyPin != null && !"0000".equals(legacyPin) && legacyPin.matches("\\d{4}")) {
                setParentPin(context, legacyPin);
                return true;
            }
        }

        String hash = p.getString(KEY_PIN_HASH, null);
        String salt = p.getString(KEY_PIN_SALT, null);
        return hash != null && !hash.isEmpty() && salt != null && !salt.isEmpty();
    }

    /**
     * Sets a new parent PIN by hashing it with a cryptographically secure random salt.
     * Never stores plaintext PIN in SharedPreferences.
     */
    public static boolean setParentPin(Context context, String newPin) {
        if (newPin == null || !newPin.matches("\\d{4}")) {
            return false;
        }
        SharedPreferences p = getPrefs(context);
        if (p == null) return false;

        byte[] salt = generateSalt();
        byte[] hash = derivePinHash(newPin, salt);
        if (hash == null || hash.length == 0) {
            return false;
        }

        p.edit()
                .putString(KEY_PIN_HASH, bytesToHex(hash))
                .putString(KEY_PIN_SALT, bytesToHex(salt))
                .remove(KEY_PARENT_PIN)
                .putInt(KEY_PIN_FAILED_ATTEMPTS, 0)
                .putLong(KEY_PIN_LOCKOUT_UNTIL_MS, 0L)
                .apply();
        return true;
    }

    /**
     * Verifies the input PIN using constant-time hash comparison.
     * Enforces rate limiting and persistent lockout across app restarts.
     */
    public static boolean verifyPin(Context context, String inputPin) {
        if (inputPin == null || inputPin.length() != 4 || !inputPin.matches("\\d{4}")) {
            return false;
        }

        // Refuse immediately if currently locked out
        if (isPinLockedOut(context)) {
            return false;
        }

        SharedPreferences p = getPrefs(context);
        if (p == null) return false;

        if (!isPinConfigured(context)) {
            return false;
        }

        String storedHashHex = p.getString(KEY_PIN_HASH, null);
        String storedSaltHex = p.getString(KEY_PIN_SALT, null);
        if (storedHashHex == null || storedSaltHex == null) {
            return false;
        }

        byte[] salt = hexToBytes(storedSaltHex);
        byte[] expectedHash = hexToBytes(storedHashHex);
        byte[] actualHash = derivePinHash(inputPin.trim(), salt);

        if (actualHash == null || expectedHash.length == 0 || actualHash.length != expectedHash.length) {
            recordFailedPinAttempt(context);
            return false;
        }

        // Constant-time comparison to protect against timing attacks
        boolean matches = MessageDigest.isEqual(expectedHash, actualHash);
        if (matches) {
            resetPinLockout(context);
            return true;
        } else {
            recordFailedPinAttempt(context);
            return false;
        }
    }

    /**
     * Changes parent PIN, requiring the current PIN for authentication first.
     */
    public static boolean changeParentPin(Context context, String currentPin, String newPin) {
        if (getPrefs(context) == null || newPin == null || !newPin.matches("\\d{4}")) {
            return false;
        }
        if (!isPinConfigured(context)) {
            return setParentPin(context, newPin);
        }
        if (!verifyPin(context, currentPin)) {
            return false;
        }
        return setParentPin(context, newPin);
    }

    /**
     * Safe recovery: Device owner in Personal profile can reset PIN if forgotten.
     * Blocked if currently in Kids or Guest mode.
     */
    public static boolean resetParentPinInPersonalMode(Context context, String newPin) {
        if (getPrefs(context) == null || !isPersonalMode(context)) {
            return false;
        }
        return setParentPin(context, newPin);
    }

    // --- LOCKOUT & RATE LIMITING PERSISTENCE ---

    public static boolean isPinLockedOut(Context context) {
        SharedPreferences p = getPrefs(context);
        if (p == null) return false;

        long lockoutUntil = p.getLong(KEY_PIN_LOCKOUT_UNTIL_MS, 0L);
        if (lockoutUntil <= 0L) return false;

        long now = System.currentTimeMillis();

        // Clock jump guard: if lockout is unreasonably far in future (> MAX_PIN_LOCKOUT_DURATION_MS)
        // or clock went back before Unix epoch / yesterday, reset to avoid permanent lockout.
        if (lockoutUntil > now + MAX_PIN_LOCKOUT_DURATION_MS || lockoutUntil < now - 86400000L) {
            resetPinLockout(context);
            return false;
        }

        if (now < lockoutUntil) {
            return true;
        } else {
            // Lockout period has elapsed; clear it
            resetPinLockout(context);
            return false;
        }
    }

    public static long getPinLockoutRemainingSeconds(Context context) {
        if (!isPinLockedOut(context)) return 0;
        SharedPreferences p = getPrefs(context);
        if (p == null) return 0;
        long lockoutUntil = p.getLong(KEY_PIN_LOCKOUT_UNTIL_MS, 0L);
        long now = System.currentTimeMillis();
        return Math.max(0, (lockoutUntil - now + 999) / 1000);
    }

    public static int getFailedPinAttempts(Context context) {
        SharedPreferences p = getPrefs(context);
        return p != null ? p.getInt(KEY_PIN_FAILED_ATTEMPTS, 0) : 0;
    }

    public static void recordFailedPinAttempt(Context context) {
        SharedPreferences p = getPrefs(context);
        if (p == null) return;

        int failed = p.getInt(KEY_PIN_FAILED_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor editor = p.edit();
        if (failed >= MAX_FAILED_PIN_ATTEMPTS) {
            editor.putLong(KEY_PIN_LOCKOUT_UNTIL_MS, System.currentTimeMillis() + PIN_LOCKOUT_DURATION_MS);
            editor.putInt(KEY_PIN_FAILED_ATTEMPTS, 0);
        } else {
            editor.putInt(KEY_PIN_FAILED_ATTEMPTS, failed);
        }
        editor.apply();
    }

    public static void resetPinLockout(Context context) {
        SharedPreferences p = getPrefs(context);
        if (p != null) {
            p.edit()
                    .putInt(KEY_PIN_FAILED_ATTEMPTS, 0)
                    .putLong(KEY_PIN_LOCKOUT_UNTIL_MS, 0L)
                    .apply();
        }
    }

    // --- BEDTIME & RESTRICTIONS ---

    public static int getKidsBedtimeLimitMinutes(Context context) {
        SharedPreferences p = getPrefs(context);
        return p != null ? p.getInt(KEY_KIDS_BEDTIME_LIMIT_MIN, DEFAULT_KIDS_BEDTIME_MIN) : DEFAULT_KIDS_BEDTIME_MIN;
    }

    public static void setKidsBedtimeLimitMinutes(Context context, int minutes) {
        if (getPrefs(context) == null || !isPersonalMode(context)) return;
        SharedPreferences p = getPrefs(context);
        if (p != null) {
            p.edit().putInt(KEY_KIDS_BEDTIME_LIMIT_MIN, Math.max(10, minutes)).apply();
        }
    }

    public static void switchProfile(Context context, String targetProfile) {
        if (targetProfile == null) return;
        String current = getActiveProfile(context);
        if (current.equals(targetProfile)) return;

        SharedPreferences p = getPrefs(context);
        if (p == null) return;

        SharedPreferences.Editor editor = p.edit();
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
        SharedPreferences p = getPrefs(context);
        if (p == null) return;
        long currentExtra = p.getLong(KEY_KIDS_EXTRA_TIME_MS, 0);
        long added = extraMinutes * 60L * 1000L;
        p.edit().putLong(KEY_KIDS_EXTRA_TIME_MS, currentExtra + added).apply();
        if (context != null) {
            startBedtimeMonitoring(context);
        }
    }

    public static Set<String> getCustomKidsAllowlist(Context context) {
        SharedPreferences p = getPrefs(context);
        return p != null ? new HashSet<>(p.getStringSet(KEY_CUSTOM_KIDS_WHITELIST, Collections.emptySet())) : new HashSet<>();
    }

    public static void addCustomKidsPackage(Context context, String packageName) {
        if (getPrefs(context) == null || !isPersonalMode(context) || packageName == null || packageName.trim().isEmpty()) return;
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
        if (getPrefs(context) == null || !isPersonalMode(context) || packageName == null) return;
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
        if (getPrefs(context) == null || !isPersonalMode(context) || packageName == null || packageName.trim().isEmpty()) return;
        Set<String> custom = getCustomGuestAllowlist(context);
        custom.add(packageName.trim());
        SharedPreferences p = getPrefs(context);
        if (p != null) {
            p.edit().putStringSet(KEY_CUSTOM_GUEST_WHITELIST, custom).apply();
            notifyProfileChanged(PROFILE_GUEST);
        }
    }

    public static void removeCustomGuestPackage(Context context, String packageName) {
        if (getPrefs(context) == null || !isPersonalMode(context) || packageName == null) return;
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
        SharedPreferences p = getPrefs(context);
        if (p == null) return 0;
        long start = p.getLong(KEY_KIDS_SESSION_START, System.currentTimeMillis());
        long extra = p.getLong(KEY_KIDS_EXTRA_TIME_MS, 0);
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
        if (packageName == null || packageName.trim().isEmpty()) return false;
        String cleanPkg = packageName.trim();
        if (profile == null || PROFILE_PERSONAL.equals(profile)) {
            return true; // Personal has no restrictions
        }

        if (PROFILE_KIDS.equals(profile)) {
            if (KIDS_BLOCKED_PACKAGES.contains(cleanPkg)) return false;
            return DEFAULT_KIDS_WHITELIST.contains(cleanPkg);
        }

        if (PROFILE_GUEST.equals(profile)) {
            return GUEST_WHITELIST.contains(cleanPkg);
        }

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
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                for (ProfileChangeListener l : new ArrayList<>(listeners)) {
                    try {
                        l.onProfileChanged(newProfile);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {
            for (ProfileChangeListener l : new ArrayList<>(listeners)) {
                try {
                    l.onProfileChanged(newProfile);
                } catch (Throwable ignored2) {}
            }
        }
    }

    private static void notifyBedtimeReached() {
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                for (ProfileChangeListener l : new ArrayList<>(listeners)) {
                    try {
                        l.onBedtimeLimitReached();
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {
            for (ProfileChangeListener l : new ArrayList<>(listeners)) {
                try {
                    l.onBedtimeLimitReached();
                } catch (Throwable ignored2) {}
            }
        }
    }

    private static Context appContextRef;

    private static void startBedtimeMonitoring(Context context) {
        if (context == null) return;
        appContextRef = context.getApplicationContext();
        try {
            if (bedtimeHandler == null) {
                bedtimeHandler = new Handler(Looper.getMainLooper());
            }
            bedtimeHandler.removeCallbacks(bedtimeRunnable);
            bedtimeHandler.postDelayed(bedtimeRunnable, 15000); // Check every 15 seconds
        } catch (Throwable ignored) {}
    }

    private static void stopBedtimeMonitoring() {
        if (bedtimeHandler != null) {
            try {
                bedtimeHandler.removeCallbacks(bedtimeRunnable);
            } catch (Throwable ignored) {}
        }
    }

    private static void checkBedtimeTimer() {
        if (appContextRef == null || !isKidsMode(appContextRef)) {
            return;
        }
        if (isBedtimeLimitReached(appContextRef)) {
            notifyBedtimeReached();
        } else if (bedtimeHandler != null) {
            try {
                bedtimeHandler.postDelayed(bedtimeRunnable, 15000);
            } catch (Throwable ignored) {}
        }
    }
}
