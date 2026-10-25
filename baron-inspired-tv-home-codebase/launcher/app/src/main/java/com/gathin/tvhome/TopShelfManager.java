package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Apple TV-style Dynamic Top Shelf manager.
 * Reads native Android TV TvContract Watch Next programs and preview channels,
 * and falls back to curated ad-free glance cards for installed streaming services.
 */
public final class TopShelfManager {

    public static final class ShelfItem {
        public final String title;
        public final String subtitle;
        public final String posterUri;
        public final String intentUri;
        public final String targetPackage;
        public Bitmap posterBitmap;

        public ShelfItem(String title, String subtitle, String posterUri, String intentUri, String targetPackage) {
            this.title = title;
            this.subtitle = subtitle;
            this.posterUri = posterUri;
            this.intentUri = intentUri;
            this.targetPackage = targetPackage;
        }
    }

    public interface ShelfCallback {
        void onLoaded(String packageName, int requestId, List<ShelfItem> items);
    }

    private static final Uri WATCH_NEXT_URI = Uri.parse("content://android.media.tv/watch_next_program");
    private static final Uri PREVIEW_PROGRAMS_URI = Uri.parse("content://android.media.tv/preview_program");

    private TopShelfManager() {}

    public static void loadShelf(Context context, String packageName, String appLabel, int requestId, ShelfCallback callback) {
        new Thread(() -> {
            List<ShelfItem> items = new ArrayList<>();

            // Inputs / HDMI Live Glance Deck
            if ("inputs".equals(packageName)) {
                List<HdmiPreviewManager.HdmiDevice> devices = HdmiPreviewManager.getConnectedHdmiDevices(context);
                for (HdmiPreviewManager.HdmiDevice dev : devices) {
                    items.add(new ShelfItem(dev.getDisplayTitle(), dev.getDisplaySubtitle(), null, "input:" + dev.inputId, "com.gathin.tvhome"));
                }
                if (items.isEmpty()) {
                    items.add(new ShelfItem("HDMI 1 · Live Feed", "Active Port · 4K 60Hz HDR", null, "input:hdmi1", "com.gathin.tvhome"));
                    items.add(new ShelfItem("HDMI 2 · Gaming Console", "Active Port · 120Hz", null, "input:hdmi2", "com.gathin.tvhome"));
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onLoaded(packageName, requestId, items);
                });
                return;
            }

            // 1. Try querying native Android TvProvider (Watch Next / Continue Playing)
            try {
                ContentResolver cr = context.getContentResolver();
                queryTvProvider(cr, WATCH_NEXT_URI, packageName, "Continue Watching", items);
                if (items.size() < 3) {
                    queryTvProvider(cr, PREVIEW_PROGRAMS_URI, packageName, "Recommended", items);
                }
            } catch (Throwable ignored) {
                // Ignore SecurityException or missing provider
            }

            // 2. If no system items were found, build curated ad-free glance channels
            if (items.isEmpty()) {
                buildCuratedGlanceItems(packageName, appLabel, items);
            }

            // 3. Load poster images asynchronously if available (up to 6 items)
            for (int i = 0; i < Math.min(items.size(), 6); i++) {
                ShelfItem item = items.get(i);
                if (item.posterUri != null && !item.posterUri.isEmpty()) {
                    item.posterBitmap = loadPosterBitmap(context, item.posterUri);
                }
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onLoaded(packageName, requestId, items);
            });
        }, "top-shelf-worker").start();
    }

    private static void queryTvProvider(ContentResolver cr, Uri uri, String targetPkg, String defaultSub, List<ShelfItem> out) {
        Cursor cursor = null;
        try {
            // AOSP TvProvider forbids selection clause without signature permission: read all & filter in memory
            cursor = cr.query(uri, null, null, null, null);
            if (cursor == null) return;

            int pkgIdx = cursor.getColumnIndex("package_name");
            int titleIdx = cursor.getColumnIndex("title");
            int posterIdx = cursor.getColumnIndex("poster_art_uri");
            int intentIdx = cursor.getColumnIndex("intent_uri");
            int descIdx = cursor.getColumnIndex("short_description");

            while (cursor.moveToNext() && out.size() < 6) {
                if (pkgIdx != -1) {
                    String pkg = cursor.getString(pkgIdx);
                    if (pkg == null || !pkg.equalsIgnoreCase(targetPkg)) {
                        continue;
                    }
                }

                String title = titleIdx != -1 ? cursor.getString(titleIdx) : null;
                if (title == null || title.trim().isEmpty()) continue;

                String poster = posterIdx != -1 ? cursor.getString(posterIdx) : null;
                String intentUri = intentIdx != -1 ? cursor.getString(intentIdx) : null;
                String desc = descIdx != -1 ? cursor.getString(descIdx) : defaultSub;

                out.add(new ShelfItem(title.trim(), desc != null ? desc.trim() : defaultSub, poster, intentUri, targetPkg));
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void buildCuratedGlanceItems(String pkg, String appLabel, List<ShelfItem> out) {
        if (pkg == null) return;
        String lower = pkg.toLowerCase();

        if (lower.contains("youtube") || lower.contains("smarttube")) {
            out.add(new ShelfItem("Subscriptions", "Latest uploads", null, null, pkg));
            out.add(new ShelfItem("Trending", "Top videos now", null, null, pkg));
            out.add(new ShelfItem("Watch History", "Pick up where you left off", null, null, pkg));
            out.add(new ShelfItem("Watch Later", "Saved for later", null, null, pkg));
        } else if (lower.contains("netflix")) {
            out.add(new ShelfItem("Continue Watching", "Resume playback", null, null, pkg));
            out.add(new ShelfItem("My List", "Saved movies & series", null, null, pkg));
            out.add(new ShelfItem("Top 10 Today", "Trending in your region", null, null, pkg));
            out.add(new ShelfItem("New Releases", "Fresh additions", null, null, pkg));
        } else if (lower.contains("amazonvideo") || lower.contains("primevideo")) {
            out.add(new ShelfItem("Continue Watching", "Jump back in", null, null, pkg));
            out.add(new ShelfItem("Included with Prime", "Popular movies", null, null, pkg));
            out.add(new ShelfItem("Watchlist", "Your saved queue", null, null, pkg));
            out.add(new ShelfItem("Top 10 on Prime", "Most watched", null, null, pkg));
        } else if (lower.contains("hotstar")) {
            out.add(new ShelfItem("Continue Watching", "Pick up episode", null, null, pkg));
            out.add(new ShelfItem("Live Sports", "Matches & Highlights", null, null, pkg));
            out.add(new ShelfItem("Hotstar Specials", "Top series", null, null, pkg));
            out.add(new ShelfItem("Watchlist", "Saved movies", null, null, pkg));
        } else if (lower.contains("zee") || lower.contains("news")) {
            out.add(new ShelfItem("Live Broadcast", "24/7 News Feed", null, null, pkg));
            out.add(new ShelfItem("Top Headlines", "Breaking national news", null, null, pkg));
            out.add(new ShelfItem("Special Bulletins", "Analysis & debates", null, null, pkg));
            out.add(new ShelfItem("Prime Time", "Evening coverage", null, null, pkg));
        } else if (lower.contains("music") || lower.contains("spotify")) {
            out.add(new ShelfItem("Jump Back In", "Recently played", null, null, pkg));
            out.add(new ShelfItem("Daily Mix", "Personalized playlist", null, null, pkg));
            out.add(new ShelfItem("Top Hits", "Trending tracks", null, null, pkg));
            out.add(new ShelfItem("Library", "Saved albums & artists", null, null, pkg));
        } else {
            // General clean quick launcher
            out.add(new ShelfItem("Resume " + appLabel, "Open and continue", null, null, pkg));
            out.add(new ShelfItem("Browse " + appLabel, "Explore content", null, null, pkg));
            out.add(new ShelfItem("Search in " + appLabel, "Find movies & shows", null, null, pkg));
        }
    }

    private static Bitmap loadPosterBitmap(Context context, String uriString) {
        if (uriString == null || uriString.isEmpty()) return null;
        try {
            if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                URL url = new URL(uriString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setInstanceFollowRedirects(true);
                try (InputStream is = conn.getInputStream()) {
                    return BitmapFactory.decodeStream(is);
                } finally {
                    conn.disconnect();
                }
            } else if (uriString.startsWith("content://") || uriString.startsWith("android.resource://")) {
                Uri uri = Uri.parse(uriString);
                try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                    return BitmapFactory.decodeStream(is);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
