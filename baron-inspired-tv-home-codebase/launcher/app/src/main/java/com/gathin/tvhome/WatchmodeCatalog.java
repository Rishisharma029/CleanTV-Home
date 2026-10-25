package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Watchmode catalog suggestions filtered to streaming providers available in India. */
final class WatchmodeCatalog {
    static final String KEY_PREF = "watchmode_api_key";
    private static final String API = "https://api.watchmode.com/v1";
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1000L;

    static final class Item {
        final String title;
        final String image;
        final String kind;
        Item(String title, String image, String kind) {
            this.title = title;
            this.image = image;
            this.kind = kind;
        }
    }

    static final class Result {
        final List<Item> items = new ArrayList<>();
        String message;
    }

    private WatchmodeCatalog() {}

    static Result fetch(Context context, String packageName) {
        Result out = new Result();
        int sourceId = sourceId(packageName);
        if (sourceId == 0) {
            out.message = "No Watchmode title feed is available for this app";
            return out;
        }
        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String key = prefs.getString(KEY_PREF, "").trim();
        if (key.isEmpty()) {
            out.message = "Add a Watchmode API key in launcher settings";
            return out;
        }
        String cacheKey = "watchmode_latest_cache_" + sourceId;
        Result cached = readCache(prefs, cacheKey);
        if (cached != null) return cached;
        try {
            String query = API + "/list-titles/?apiKey=" + encode(key)
                    + "&source_ids=" + sourceId
                    + "&regions=IN&types=movie%2Ctv_series&sort_by=release_date_desc&page=1";
            JSONObject listing = requestJson(query);
            JSONArray titles = listing.optJSONArray("titles");
            if (titles == null) throw new IllegalStateException("Watchmode returned no title list");

            int movies = 0;
            int series = 0;
            for (int i = 0; i < titles.length() && out.items.size() < 6; i++) {
                JSONObject row = titles.optJSONObject(i);
                if (row == null) continue;
                String type = row.optString("type", "");
                boolean movie = "movie".equalsIgnoreCase(type);
                boolean show = "tv_series".equalsIgnoreCase(type) || "tv".equalsIgnoreCase(type);
                if ((movie && movies >= 3) || (show && series >= 3)) continue;
                if (!movie && !show) continue;

                String title = row.optString("title", "");
                if (title.isEmpty()) continue;
                String image = "";
                int id = row.optInt("id", 0);
                if (id > 0) {
                    try {
                        JSONObject details = requestJson(API + "/title/" + id + "/details/?apiKey=" + encode(key));
                        image = details.optString("posterLarge",
                                details.optString("posterMedium", details.optString("poster", "")));
                        if (image.isEmpty()) image = details.optString("backdrop", "");
                        String detailedTitle = details.optString("title", "");
                        if (!detailedTitle.isEmpty()) title = detailedTitle;
                    } catch (Exception ignored) {
                        // Keep the title card when artwork lookup is unavailable.
                    }
                }
                out.items.add(new Item(title, image, movie ? "Movie" : "Series"));
                if (movie) movies++; else series++;
            }
            if (out.items.isEmpty()) {
                out.message = "No popular India titles returned for this app";
            } else {
                writeCache(prefs, cacheKey, out);
            }
            return out;
        } catch (Exception error) {
            out.message = "Could not load the catalog. Check the API key and internet connection.";
            return out;
        }
    }

    static Bitmap loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static int sourceId(String pkg) {
        if ("com.netflix.ninja".equals(pkg)) return 203;                  // Netflix
        if ("com.amazon.amazonvideo.livingroom".equals(pkg)) return 26;  // Prime Video
        if ("in.startv.hotstar".equals(pkg)) return 447;                 // JioHotstar
        if ("com.google.android.youtube.tv".equals(pkg)) return 344;     // YouTube
        return 0;
    }

    static void clearCache(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("watchmode_cache_") || key.startsWith("watchmode_latest_cache_")) editor.remove(key);
        }
        editor.apply();
    }
    private static Result readCache(SharedPreferences prefs, String cacheKey) {
        long savedAt = prefs.getLong(cacheKey + "_time", 0L);
        if (savedAt <= 0 || System.currentTimeMillis() - savedAt >= CACHE_TTL_MS) return null;
        String raw = prefs.getString(cacheKey, "");
        if (raw.isEmpty()) return null;
        try {
            JSONArray rows = new JSONArray(raw);
            Result result = new Result();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                result.items.add(new Item(row.optString("title", ""),
                        row.optString("image", ""), row.optString("kind", "")));
            }
            return result.items.isEmpty() ? null : result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeCache(SharedPreferences prefs, String cacheKey, Result result) {
        JSONArray rows = new JSONArray();
        for (Item item : result.items) {
            JSONObject row = new JSONObject();
            try {
                row.put("title", item.title);
                row.put("image", item.image);
                row.put("kind", item.kind);
                rows.put(row);
            } catch (Exception ignored) { }
        }
        prefs.edit().putString(cacheKey, rows.toString())
                .putLong(cacheKey + "_time", System.currentTimeMillis()).apply();
    }

    private static JSONObject requestJson(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("accept", "application/json");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Watchmode HTTP " + status);
            }
            try (InputStream stream = connection.getInputStream();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = stream.read(chunk)) != -1 && bytes.size() < 1_500_000) {
                    bytes.write(chunk, 0, read);
                }
                return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }
}