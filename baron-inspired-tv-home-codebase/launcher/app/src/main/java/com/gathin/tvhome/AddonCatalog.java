package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Built-in CleanTV Add-on Catalog & Package Manager:
 * - Curated 1-click popular TV apps, tools, and entertainment extensions.
 * - Sideload/Install APKs directly over Wi-Fi without USB flash drives or manual ADB.
 * - Detects installed state, triggers automatic downloading, and launches the Android TV package installer.
 */
public final class AddonCatalog {

    public interface DownloadListener {
        void onProgress(int percent);
        void onSuccess(File apkFile);
        void onError(String message);
    }

    public static final class AddonItem {
        public final String id;
        public final String name;
        public final String category;
        public final String description;
        public final String pkg;
        public final String downloadUrl;
        public final int accentColor;

        public AddonItem(String id, String name, String category, String description, String pkg, String downloadUrl, int accentColor) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.description = description;
            this.pkg = pkg;
            this.downloadUrl = downloadUrl;
            this.accentColor = accentColor;
        }
    }

    private static final List<AddonItem> CATALOG = new ArrayList<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static Handler mainHandler;

    static {
        CATALOG.add(new AddonItem(
                "adblock",
                "Ad-Blocker (Private DNS)",
                "Privacy & AdBlock",
                "1-click system-wide ad & telemetry blocking for third-party TV apps and web browsers.",
                "com.adguard.android.contentblocker",
                "https://github.com/AdguardTeam/AdguardForAndroid/releases/download/v4.4.24/adguard-4.4.24.apk",
                0xFF10B981
        ));

        CATALOG.add(new AddonItem(
                "smarttube",
                "SmartTube",
                "Streaming",
                "Ad-free YouTube client for Android TV with SponsorBlock & 4K HDR playback.",
                "com.teamsmart.videomanager.tv",
                "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable.apk",
                0xFF38BDF8
        ));

        CATALOG.add(new AddonItem(
                "kodi",
                "Kodi Media Center",
                "Media Hub",
                "Open-source home theater & entertainment center for movies, music, and streaming add-ons.",
                "org.xbmc.kodi",
                "http://mirrors.kodi.tv/releases/android/arm/kodi-21.0-Omega-armeabi-v7a.apk",
                0xFF10B981
        ));

        CATALOG.add(new AddonItem(
                "vlc",
                "VLC Media Player",
                "Video Player",
                "Plays any video format, network streams, DVD ISOs, and multi-track audio without extra codecs.",
                "org.videolan.vlc",
                "https://get.videolan.org/vlc-android/3.5.4/VLC-Android-3.5.4-armv7.apk",
                0xFFF59E0B
        ));

        CATALOG.add(new AddonItem(
                "tvbro",
                "TV Bro Browser",
                "Web Browser",
                "Fast TV-optimized web browser with remote D-pad controls, built-in ad block & download manager.",
                "com.phlox.tvwebbrowser",
                "https://github.com/truefedex/tv-bro/releases/download/v2.0.1/app-release.apk",
                0xFFA855F7
        ));

        CATALOG.add(new AddonItem(
                "retroarch",
                "RetroArch",
                "Retro Gaming",
                "Classic retro console emulator frontend for NES, SNES, GBA, Genesis, and arcade gaming.",
                "com.retroarch",
                "https://buildbot.libretro.com/stable/1.19.1/android/RetroArch.apk",
                0xFFEC4899
        ));

        CATALOG.add(new AddonItem(
                "stremio",
                "Stremio",
                "Streaming",
                "Media aggregator to organize, stream, and discover movies, series, YouTube, and podcasts.",
                "com.stremio.one",
                "https://dl.strem.io/four/v1.6.11/android/1.6.11-armv7.apk",
                0xFF6366F1
        ));
    }

    private static synchronized Handler getMainHandler() {
        if (mainHandler == null) {
            try {
                Looper looper = Looper.getMainLooper();
                if (looper != null) {
                    mainHandler = new Handler(looper);
                }
            } catch (Throwable ignored) {}
        }
        return mainHandler;
    }

    private AddonCatalog() {}

    public static List<AddonItem> getCatalog() {
        return Collections.unmodifiableList(CATALOG);
    }

    public static AddonItem findById(String id) {
        if (id == null) return null;
        for (AddonItem item : CATALOG) {
            if (item.id.equalsIgnoreCase(id)) return item;
        }
        return null;
    }

    public static boolean isInstalled(Context context, String pkg) {
        if (context == null || pkg == null || pkg.isEmpty()) return false;
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void launchApp(Context context, String pkg) {
        if (context == null || pkg == null) return;
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "Could not launch " + pkg, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Toast.makeText(context, "Launch error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void downloadAndInstall(Context context, AddonItem addon, DownloadListener listener) {
        if (context == null || addon == null) return;

        executor.execute(() -> {
            HttpURLConnection conn = null;
            InputStream in = null;
            FileOutputStream out = null;
            try {
                File targetDir = context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir();
                File apkFile = new File(targetDir, addon.id + ".apk");

                conn = openConnectionWithRedirects(addon.downloadUrl);
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    postError(listener, "HTTP " + code + " error downloading " + addon.name);
                    return;
                }

                long contentLength = conn.getContentLength();
                in = conn.getInputStream();
                out = new FileOutputStream(apkFile);

                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;
                int lastReportedPercent = -1;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    if (contentLength > 0) {
                        int percent = (int) ((totalRead * 100) / contentLength);
                        if (percent != lastReportedPercent && percent % 5 == 0) {
                            lastReportedPercent = percent;
                            postProgress(listener, percent);
                        }
                    }
                }
                out.flush();

                postSuccess(listener, apkFile);

                // Launch package installer on UI thread
                Handler h = getMainHandler();
                if (h != null) {
                    h.post(() -> installApkFile(context, apkFile));
                }

            } catch (Throwable t) {
                postError(listener, "Download failed: " + t.getMessage());
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) {}
                try { if (out != null) out.close(); } catch (Throwable ignored) {}
                try { if (conn != null) conn.disconnect(); } catch (Throwable ignored) {}
            }
        });
    }

    public static void installApkFile(Context context, File file) {
        if (context == null || file == null || !file.exists()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (!context.getPackageManager().canRequestPackageInstalls()) {
                    Intent manageIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + context.getPackageName()));
                    manageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(manageIntent);
                    Toast.makeText(context, "Please allow CleanTV to install add-ons, then tap install again", Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (Throwable ignored) {}
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = Uri.parse("content://" + context.getPackageName() + ".addons/" + file.getName());
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(context, "Installation error: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static HttpURLConnection openConnectionWithRedirects(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; CleanTV)");
        conn.setInstanceFollowRedirects(true);
        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == 307 || status == 308) {
            String newUrl = conn.getHeaderField("Location");
            if (newUrl != null && !newUrl.isEmpty()) {
                conn.disconnect();
                return openConnectionWithRedirects(newUrl);
            }
        }
        return conn;
    }

    private static void postProgress(DownloadListener listener, int percent) {
        if (listener == null) return;
        Handler h = getMainHandler();
        if (h != null) h.post(() -> listener.onProgress(percent));
        else listener.onProgress(percent);
    }

    private static void postSuccess(DownloadListener listener, File file) {
        if (listener == null) return;
        Handler h = getMainHandler();
        if (h != null) h.post(() -> listener.onSuccess(file));
        else listener.onSuccess(file);
    }

    private static void postError(DownloadListener listener, String err) {
        if (listener == null) return;
        Handler h = getMainHandler();
        if (h != null) h.post(() -> listener.onError(err));
        else listener.onError(err);
    }
}
