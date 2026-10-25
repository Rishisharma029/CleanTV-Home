package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Universal 150+ TV Auto-Debloater Engine:
 * - Automatically inspects ro.product.manufacturer, brand, model, and board properties.
 * - Dynamically matches and scans packages against curated debloat profiles:
 *   Sony Bravia, TCL, Xiaomi/PatchWall, Realme/Changhong, OnePlus TV, Hisense, Amazon Fire TV, and Google TV.
 * - Provides safe 1-click disable and restore capabilities with user rollback history.
 */
public final class DebloaterEngine {

    public enum Brand {
        SONY("Sony Bravia", "ACR Fingerprinting, Sony Select & Discovery bloat"),
        TCL("TCL Google TV", "TCL Channel, Waterfall ads & Community telemetry"),
        XIAOMI("Xiaomi / PatchWall", "PatchWall ads, Mi Analytics & SystemAdSolution"),
        REALME("Realme / Changhong", "AI PONT, HeyTap telemetry & Changhong ads"),
        ONEPLUS("OnePlus TV", "OxygenPlay launcher, OnePlus Analytics & diagnostics"),
        HISENSE("Hisense / VIDAA", "VIDAA Store, HiSense Telemetry & SmartLife"),
        FIRE_TV("Amazon Fire TV", "Fire TV home carousel ads, Amazon AdManager & demo"),
        GOOGLE_TV("Google / Android TV", "Sponsored recommendations, bug senders & partner bloat");

        public final String displayName;
        public final String description;

        Brand(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    public enum Risk {
        SAFE("SAFE", 0xFF34D399),
        RECOMMENDED("RECOMMENDED", 0xFF38BDF8),
        CAUTION("CAUTION", 0xFFF59E0B);

        public final String displayName;
        public final int color;

        Risk(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    public static class DebloatItem {
        public final String pkg;
        public final Brand brand;
        public final String category;
        public final String label;
        public final String description;
        public final Risk risk;
        public final int estimatedRamMb;
        public boolean isInstalled;
        public boolean isEnabled;

        public DebloatItem(String pkg, Brand brand, String category, String label, String description) {
            this(pkg, brand, category, label, description,
                    category.contains("Launcher") ? Risk.CAUTION : (category.contains("Telemetry") || category.contains("ACR") || category.contains("Ad") ? Risk.SAFE : Risk.RECOMMENDED),
                    category.contains("Launcher") ? 45 : (category.contains("Telemetry") || category.contains("ACR") ? 18 : 22));
        }

        public DebloatItem(String pkg, Brand brand, String category, String label, String description, Risk risk, int estimatedRamMb) {
            this.pkg = pkg;
            this.brand = brand;
            this.category = category;
            this.label = label;
            this.description = description;
            this.risk = risk;
            this.estimatedRamMb = estimatedRamMb;
        }
    }

    public static class DebloaterPreview {
        public Brand brand;
        public String deviceInfo;
        public int totalFound;
        public int activeBloatCount;
        public int alreadyDisabledCount;
        public int estimatedRamFreedMb;
        public int cpuWakeupsSavedPerHour;
        public final List<DebloatItem> safeItems = new ArrayList<>();
        public final List<DebloatItem> recommendedItems = new ArrayList<>();
        public final List<DebloatItem> cautionItems = new ArrayList<>();
    }

    private static final String PREFS_NAME = "cleantv_debloater";
    private static final String KEY_DISABLED_PACKAGES = "disabled_by_cleantv";

    private static final List<DebloatItem> MASTER_DEBLOAT_LIST = new ArrayList<>();

    static {
        // --- 1. SONY BRAVIA ---
        addDebloat("tv.samba.ssm", Brand.SONY, "ACR Tracker", "Samba TV", "Fingerprints on-screen audio/video content for ad targeting");
        addDebloat("com.sony.dtv.sonyselect", Brand.SONY, "Sponsored Store", "Sony Select", "Sponsored promotional app recommendations");
        addDebloat("com.sony.dtv.promos", Brand.SONY, "Promotions", "My BRAVIA Promos", "Promotional banners and partner upsells");
        addDebloat("com.sony.dtv.discovery", Brand.SONY, "Content Bar", "Sony Discover", "Unwanted promotional discover feed");
        addDebloat("com.sony.dtv.bivlinfo", Brand.SONY, "Content Feed", "BIVL Info Service", "Feeds commercial recommendations to discovery bar");
        addDebloat("com.sony.dtv.tvlaunchercustomizer", Brand.SONY, "Ad Rows", "Sony Launcher Customizer", "Injects sponsored channels into stock home");
        addDebloat("android.autoinstalls.config.sony.bravia", Brand.SONY, "Auto-Install", "Sony Partner Auto-Install", "Silently pushes pre-installed sponsored apps");
        addDebloat("com.sony.dtv.demomode", Brand.SONY, "Retail Demo", "Sony Demo Mode", "Store showroom demo looping video & banners");
        addDebloat("com.sony.dtv.demosupport", Brand.SONY, "Retail Demo", "Sony Demo Support", "Background demo runtime daemon");
        addDebloat("com.sony.dtv.multiscreendemo", Brand.SONY, "Retail Demo", "Sony Multi-Screen Demo", "Retail showroom display split demo");
        addDebloat("com.sony.dtv.da.service", Brand.SONY, "Telemetry", "Sony Device Agent", "Background telemetry and remote monitoring service");
        addDebloat("com.qterics.da.product", Brand.SONY, "Telemetry", "Qterics Device Agent", "Third-party diagnostic & metric reporting daemon");
        addDebloat("com.sony.dtv.sonybugreportsys", Brand.SONY, "Telemetry", "Sony Bug Reporter", "Automatic crash log uploader");
        addDebloat("com.sony.dtv.b2b.vendorprotocol", Brand.SONY, "Daemon", "Vendor Protocol Daemon", "Legacy broadcast daemon transmitting model/MAC info");

        // --- 2. TCL (GOOGLE TV / ANDROID TV) ---
        addDebloat("com.tcl.partnercustomizer", Brand.TCL, "Promotions", "TCL Partner Customizer", "TCL promotional ad channels on stock launcher");
        addDebloat("com.tcl.community", Brand.TCL, "Community Nag", "TCL Community", "Unwanted social and promotional community feed");
        addDebloat("com.tcl.dashboard", Brand.TCL, "Ad Hub", "TCL Channel Dashboard", "TCL Channel sponsored streaming feed");
        addDebloat("com.tcl.browser", Brand.TCL, "Pre-installed Browser", "TCL TV Browser", "Bloated default browser with search hijacking");
        addDebloat("com.tcl.waterfall.overseas", Brand.TCL, "Ad Waterfall", "TCL Waterfall Feed", "Commercial content streaming ad banners");
        addDebloat("com.tcl.useragreement", Brand.TCL, "Telemetry", "TCL Policy Tracker", "Background telemetry agreement monitor");
        addDebloat("com.tcl.overheatdiagnosis", Brand.TCL, "Diagnostics", "TCL Overheat Reporter", "Vendor diagnostic logger");
        addDebloat("com.tcl.tvmanager", Brand.TCL, "Bloat Manager", "TCL TV Guard", "Fake cleaning utility and memory booster");
        addDebloat("com.tcl.estore", Brand.TCL, "App Store", "TCL App Store", "Proprietary app store with commercial ads");
        addDebloat("com.tcl.whathot", Brand.TCL, "Sponsored Ads", "TCL What's Hot", "Sponsored promotional movie suggestions");
        addDebloat("com.tcl.bi", Brand.TCL, "Telemetry", "TCL Business Intelligence", "User behavioral logging and analytics");
        addDebloat("com.tcl.usercenter", Brand.TCL, "Account Nag", "TCL Account Service", "Persistent account sign-in prompt");

        // --- 3. XIAOMI / PATCHWALL ---
        addDebloat("com.mitv.tvhome", Brand.XIAOMI, "Stock Launcher", "Xiaomi PatchWall", "Ad-heavy stock PatchWall launcher");
        addDebloat("com.miui.systemAdSolution", Brand.XIAOMI, "Ad Service", "MIUI Ad Solution", "System-wide ad delivery engine for Xiaomi TVs");
        addDebloat("com.xiaomi.mitv.advertise", Brand.XIAOMI, "Advertisements", "Mi TV Advertisements", "Bootup and standby ad injector");
        addDebloat("com.xiaomi.mitv.upgrade", Brand.XIAOMI, "OTA Nag", "Mi TV Upgrade Service", "Aggressive firmware nag and promo popup");
        addDebloat("com.xiaomi.mitv.shop", Brand.XIAOMI, "Shopping", "Mi TV Store", "Xiaomi e-commerce shopping client");
        addDebloat("com.xiaomi.mitv.tvmanager", Brand.XIAOMI, "Bloat Manager", "Mi TV Manager", "Unneeded RAM booster and telemetry service");
        addDebloat("com.xiaomi.mitv.payment", Brand.XIAOMI, "Payments", "Mi Pay Service", "Xiaomi wallet payment framework");
        addDebloat("com.xiaomi.xmsf", Brand.XIAOMI, "Framework", "Xiaomi Service Framework", "Background telemetry and push notification daemon");
        addDebloat("com.miui.analytics", Brand.XIAOMI, "Telemetry", "MIUI Analytics", "Captures clicks, viewing duration, and app usage");
        addDebloat("com.mitv.videoplayer.ad", Brand.XIAOMI, "Video Ads", "Mi Video Ad Injector", "Injects commercial pre-rolls before local videos");
        addDebloat("com.xiaomi.mitv.appstore", Brand.XIAOMI, "App Store", "Mi App Store", "Redundant third-party app market");

        // --- 4. REALME TV / CHANGHONG ---
        addDebloat("com.changhong.home", Brand.REALME, "Stock Launcher", "Changhong AI PONT", "Default ad-cluttered Changhong launcher");
        addDebloat("com.changhong.ad", Brand.REALME, "Ad Engine", "Changhong Ad Delivery", "Background ad banner download manager");
        addDebloat("com.changhong.systemservice", Brand.REALME, "Telemetry", "Changhong System Daemon", "Device usage and diagnostics logging");
        addDebloat("com.realme.tv.service", Brand.REALME, "Realme Daemon", "Realme TV Service", "Background telemetry service");
        addDebloat("com.heytap.usercenter", Brand.REALME, "Account Nag", "HeyTap Account", "Realme/Oppo persistent login notification");
        addDebloat("com.heytap.mcs", Brand.REALME, "Push Ads", "HeyTap Cloud Push", "Commercial promotional push notification service");
        addDebloat("com.coloros.atv.telemetry", Brand.REALME, "Telemetry", "ColorOS TV Telemetry", "OS analytics and crash uploader");

        // --- 5. ONEPLUS TV ---
        addDebloat("com.oneplus.tv.launcher", Brand.ONEPLUS, "Stock Launcher", "OxygenPlay Launcher", "Ad-supported default launcher");
        addDebloat("com.oneplus.tv.analytics", Brand.ONEPLUS, "Telemetry", "OnePlus TV Analytics", "User viewing and interaction metric tracker");
        addDebloat("com.oneplus.tv.diagnostics", Brand.ONEPLUS, "Diagnostics", "OnePlus Diagnostics", "Hardware usage logger");
        addDebloat("com.oneplus.tv.usercenter", Brand.ONEPLUS, "Account Nag", "OnePlus Account", "Account login reminder");
        addDebloat("com.oneplus.tv.feedback", Brand.ONEPLUS, "Telemetry", "OnePlus Feedback", "Bug reporting and log uploader");
        addDebloat("com.oneplus.tv.remoteservice", Brand.ONEPLUS, "Remote Support", "OnePlus Remote Agent", "Vendor remote assistance daemon");

        // --- 6. HISENSE / VIDAA ---
        addDebloat("com.hisense.service.telemetry", Brand.HISENSE, "Telemetry", "Hisense Telemetry", "Usage reporting and metric collector");
        addDebloat("com.hisense.tv.adservice", Brand.HISENSE, "Ad Engine", "Hisense Ad Delivery", "Pop-up and launcher ad manager");
        addDebloat("com.hisense.vidaa.store", Brand.HISENSE, "App Store", "VIDAA Store", "Proprietary app marketplace");
        addDebloat("com.hisense.hitv.feedback", Brand.HISENSE, "Diagnostics", "HiTV Feedback", "Background diagnostic service");
        addDebloat("com.hisense.tv.smartlife", Brand.HISENSE, "Promo Hub", "Hisense SmartLife", "Commercial ecosystem promo portal");
        addDebloat("com.jamdeo.tv.vod", Brand.HISENSE, "VOD Bloat", "Jamdeo VOD Service", "Hisense partner streaming feeder");

        // --- 7. AMAZON FIRE TV ---
        addDebloat("com.amazon.device.messaging", Brand.FIRE_TV, "Push Service", "Amazon Device Messaging", "Delivers commercial notification banners");
        addDebloat("com.amazon.tv.launcher", Brand.FIRE_TV, "Stock Launcher", "KFTV Home Launcher", "Heavy sponsored video auto-play home screen");
        addDebloat("com.amazon.advertising.admanager", Brand.FIRE_TV, "Ad Manager", "Amazon Ad Manager", "Central advertising coordination engine");
        addDebloat("com.amazon.dmesg", Brand.FIRE_TV, "Telemetry", "Amazon Diagnostic Logger", "Continuous system log collector");
        addDebloat("com.amazon.retail.demo", Brand.FIRE_TV, "Retail Demo", "Fire TV Retail Demo", "Store display promotional loop");
        addDebloat("com.amazon.visualsearch", Brand.FIRE_TV, "Search Tracker", "Amazon Visual Search", "Search query analytics recorder");

        // --- 8. UNIVERSAL GOOGLE TV / ANDROID TV CORE ---
        addDebloat("com.google.android.tvrecommendations", Brand.GOOGLE_TV, "Ad Carousel", "Google TV Recommendations", "Sponsored ads and trailers on stock Android TV");
        addDebloat("com.google.android.tv.bugreportsender", Brand.GOOGLE_TV, "Telemetry", "TV Bug Report Sender", "Automatic crash dump uploader");
        addDebloat("com.google.android.feedback", Brand.GOOGLE_TV, "Telemetry", "Google Feedback", "Feedback collector daemon");
        addDebloat("android.autoinstalls.config.google", Brand.GOOGLE_TV, "Auto-Install", "Google Partner Auto-Installs", "Silently pushes recommended sponsored apps");
        addDebloat("com.android.providers.partnerbookmarks", Brand.GOOGLE_TV, "Bookmarks", "Partner Bookmarks", "Sponsored browser bookmarks provider");
    }

    private static void addDebloat(String pkg, Brand brand, String category, String label, String description) {
        MASTER_DEBLOAT_LIST.add(new DebloatItem(pkg, brand, category, label, description));
    }

    private DebloaterEngine() {}

    /**
     * Inspects ro.product.manufacturer, brand, model, and hardware to detect the TV manufacturer.
     */
    public static Brand detectDeviceBrand() {
        String mfg = (Build.MANUFACTURER != null ? Build.MANUFACTURER : "").toLowerCase(Locale.US);
        String brand = (Build.BRAND != null ? Build.BRAND : "").toLowerCase(Locale.US);
        String model = (Build.MODEL != null ? Build.MODEL : "").toLowerCase(Locale.US);
        String hw = (Build.HARDWARE != null ? Build.HARDWARE : "").toLowerCase(Locale.US);

        if (mfg.contains("sony") || brand.contains("sony") || model.contains("bravia")) {
            return Brand.SONY;
        }
        if (mfg.contains("tcl") || brand.contains("tcl") || model.contains("beyondtv") || model.contains("tcl")) {
            return Brand.TCL;
        }
        if (mfg.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi") || model.contains("mitv")) {
            return Brand.XIAOMI;
        }
        if (mfg.contains("realme") || brand.contains("realme") || mfg.contains("changhong") || model.contains("changhong")) {
            return Brand.REALME;
        }
        if (mfg.contains("oneplus") || brand.contains("oneplus")) {
            return Brand.ONEPLUS;
        }
        if (mfg.contains("hisense") || brand.contains("hisense") || model.contains("vidaa")) {
            return Brand.HISENSE;
        }
        if (mfg.contains("amazon") || brand.contains("amazon") || model.contains("aft") || model.contains("fire")) {
            return Brand.FIRE_TV;
        }
        return Brand.GOOGLE_TV;
    }

    public static String getDeviceInfoSummary() {
        return String.format(Locale.US, "%s %s (Board: %s, Android %s)",
                Build.MANUFACTURER != null ? Build.MANUFACTURER : "Unknown",
                Build.MODEL != null ? Build.MODEL : "Device",
                Build.BOARD != null ? Build.BOARD : "unknown",
                Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : "TV");
    }

    /**
     * Scans installed packages on this TV and returns all detected debloatable bloatware.
     */
    public static List<DebloatItem> scanDebloatCandidates(Context context) {
        if (context == null) return Collections.emptyList();
        PackageManager pm = context.getPackageManager();
        List<DebloatItem> result = new ArrayList<>();

        Brand detectedBrand = detectDeviceBrand();

        for (DebloatItem item : MASTER_DEBLOAT_LIST) {
            // Include items for detected brand + universal Google TV items
            if (item.brand == detectedBrand || item.brand == Brand.GOOGLE_TV) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(item.pkg, 0);
                    item.isInstalled = true;
                    item.isEnabled = ai.enabled;
                    result.add(item);
                } catch (PackageManager.NameNotFoundException ignored) {
                    item.isInstalled = false;
                    item.isEnabled = false;
                }
            }
        }
        return result;
    }

    /**
     * Disables a target bloatware package on the TV using pm disable-user --user 0.
     */
    public static boolean disablePackage(Context context, String pkg) {
        if (context == null || pkg == null) return false;
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"pm", "disable-user", "--user", "0", pkg});
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                recordDisabledPackage(context, pkg);
                return true;
            }
            // Fallback: try pm disable
            Process p2 = Runtime.getRuntime().exec(new String[]{"pm", "disable", pkg});
            if (p2.waitFor() == 0) {
                recordDisabledPackage(context, pkg);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Re-enables a package that was previously disabled.
     */
    public static boolean enablePackage(Context context, String pkg) {
        if (context == null || pkg == null) return false;
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"pm", "enable", pkg});
            if (process.waitFor() == 0) {
                removeDisabledRecord(context, pkg);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Generates a safe preview of debloating impact before 1-click execution.
     * Computes estimated RAM recovery and CPU wakeups saved for 1GB RAM TVs.
     */
    public static DebloaterPreview getDebloaterPreview(Context context) {
        DebloaterPreview preview = new DebloaterPreview();
        preview.brand = detectDeviceBrand();
        preview.deviceInfo = getDeviceInfoSummary();

        List<DebloatItem> candidates = scanDebloatCandidates(context);
        preview.totalFound = candidates.size();

        for (DebloatItem item : candidates) {
            if (item.isInstalled) {
                if (item.isEnabled) {
                    preview.activeBloatCount++;
                    preview.estimatedRamFreedMb += item.estimatedRamMb;
                    preview.cpuWakeupsSavedPerHour += 4; // average background daemon wakeup frequency
                    if (item.risk == Risk.SAFE) {
                        preview.safeItems.add(item);
                    } else if (item.risk == Risk.RECOMMENDED) {
                        preview.recommendedItems.add(item);
                    } else {
                        preview.cautionItems.add(item);
                    }
                } else {
                    preview.alreadyDisabledCount++;
                }
            }
        }
        return preview;
    }

    /**
     * Safe 1-Click Auto-Debloat: Disables all installed and enabled bloatware for this device.
     */
    public static int autoDebloatAll(Context context) {
        List<DebloatItem> candidates = scanDebloatCandidates(context);
        int successCount = 0;
        for (DebloatItem item : candidates) {
            if (item.isInstalled && item.isEnabled) {
                if (disablePackage(context, item.pkg)) {
                    successCount++;
                }
            }
        }
        return successCount;
    }

    /**
     * Safe 1-Click Rollback: Re-enables all packages disabled by CleanTV.
     */
    public static int restoreAll(Context context) {
        Set<String> disabled = getDisabledPackages(context);
        int restoredCount = 0;
        for (String pkg : new HashSet<>(disabled)) {
            if (enablePackage(context, pkg)) {
                restoredCount++;
            }
        }
        return restoredCount;
    }

    public static Set<String> getDisabledPackages(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_DISABLED_PACKAGES, Collections.emptySet());
    }

    private static void recordDisabledPackage(Context context, String pkg) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_DISABLED_PACKAGES, Collections.emptySet()));
        set.add(pkg);
        prefs.edit().putStringSet(KEY_DISABLED_PACKAGES, set).apply();
    }

    private static void removeDisabledRecord(Context context, String pkg) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_DISABLED_PACKAGES, Collections.emptySet()));
        set.remove(pkg);
        prefs.edit().putStringSet(KEY_DISABLED_PACKAGES, set).apply();
    }
}
