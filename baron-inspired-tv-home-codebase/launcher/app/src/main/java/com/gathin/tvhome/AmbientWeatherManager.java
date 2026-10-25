package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ambient Live Glance: Fetches, caches, and maintains local weather conditions
 * using Open-Meteo API (zero key required, privacy-respecting, ~1km resolution).
 */
public final class AmbientWeatherManager {

    public interface WeatherCallback {
        void onWeatherLoaded(WeatherInfo info);
    }

    public static final class WeatherInfo {
        public final int tempC;
        public final int tempF;
        public final int code;
        public final boolean isDay;
        public final String condition;
        public final String icon;
        public final int particleMode;
        public final String city;
        public final long timestamp;

        public WeatherInfo(int tempC, int tempF, int code, boolean isDay,
                           String condition, String icon, int particleMode,
                           String city, long timestamp) {
            this.tempC = tempC;
            this.tempF = tempF;
            this.code = code;
            this.isDay = isDay;
            this.condition = condition;
            this.icon = icon;
            this.particleMode = particleMode;
            this.city = city;
            this.timestamp = timestamp;
        }

        public String format(boolean useFahrenheit) {
            int temp = useFahrenheit ? tempF : tempC;
            String unit = useFahrenheit ? "°F" : "°C";
            return temp + unit + "  ·  " + condition;
        }
    }

    private static final String PREFS_NAME = "ambient_weather_prefs";
    private static final String KEY_TEMP_C = "temp_c";
    private static final String KEY_CODE = "code";
    private static final String KEY_IS_DAY = "is_day";
    private static final String KEY_CONDITION = "condition";
    private static final String KEY_ICON = "icon";
    private static final String KEY_MODE = "mode";
    private static final String KEY_CITY = "city";
    private static final String KEY_TIME = "time";

    private static final long CACHE_VALIDITY_MS = 30 * 60 * 1000L; // 30 mins
    private static final int TIMEOUT_MS = 6000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AmbientWeatherManager() {}

    /**
     * Loads current weather asynchronously, serving cached data immediately if available.
     */
    public static void loadWeather(Context context, WeatherCallback callback) {
        if (context == null || callback == null) return;
        final Context appCtx = context.getApplicationContext();

        // 1. Deliver cached weather immediately to avoid layout pop-in
        WeatherInfo cached = getCachedWeather(appCtx);
        if (cached != null) {
            callback.onWeatherLoaded(cached);
            if (System.currentTimeMillis() - cached.timestamp < CACHE_VALIDITY_MS) {
                return; // cache is fresh, no network call needed
            }
        }

        // 2. Fetch fresh weather in background executor
        executor.execute(() -> {
            WeatherInfo fresh = fetchWeather(appCtx);
            if (fresh != null) {
                saveCachedWeather(appCtx, fresh);
                mainHandler.post(() -> callback.onWeatherLoaded(fresh));
            } else if (cached == null) {
                // Generate graceful seasonal/time-of-day ambient fallback
                WeatherInfo fallback = generateAmbientFallback(appCtx);
                mainHandler.post(() -> callback.onWeatherLoaded(fallback));
            }
        });
    }

    @Nullable
    private static WeatherInfo getCachedWeather(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_TEMP_C)) return null;

        int tempC = prefs.getInt(KEY_TEMP_C, 22);
        int tempF = (int) Math.round(tempC * 9.0 / 5.0 + 32.0);
        int code = prefs.getInt(KEY_CODE, 0);
        boolean isDay = prefs.getBoolean(KEY_IS_DAY, true);
        String condition = prefs.getString(KEY_CONDITION, "Clear");
        String icon = prefs.getString(KEY_ICON, "☀️");
        int mode = prefs.getInt(KEY_MODE, WeatherParticleView.MODE_SUN);
        String city = prefs.getString(KEY_CITY, "");
        long time = prefs.getLong(KEY_TIME, 0);

        return new WeatherInfo(tempC, tempF, code, isDay, condition, icon, mode, city, time);
    }

    private static void saveCachedWeather(Context context, WeatherInfo info) {
        SharedPreferences.Editor ed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        ed.putInt(KEY_TEMP_C, info.tempC);
        ed.putInt(KEY_CODE, info.code);
        ed.putBoolean(KEY_IS_DAY, info.isDay);
        ed.putString(KEY_CONDITION, info.condition);
        ed.putString(KEY_ICON, info.icon);
        ed.putInt(KEY_MODE, info.particleMode);
        ed.putString(KEY_CITY, info.city);
        ed.putLong(KEY_TIME, info.timestamp);
        ed.apply();
    }

    private static WeatherInfo fetchWeather(Context context) {
        try {
            double[] coords = resolveCoordinates(context);
            if (coords == null) return null;

            double lat = coords[0];
            double lon = coords[1];
            String city = coords.length > 2 && coords[2] > 0 ? "" : "";

            String url = String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day",
                    lat, lon);

            JSONObject json = getJson(url);
            if (json == null || !json.has("current")) return null;

            JSONObject current = json.getJSONObject("current");
            int tempC = (int) Math.round(current.getDouble("temperature_2m"));
            int tempF = (int) Math.round(tempC * 9.0 / 5.0 + 32.0);
            int code = current.getInt("weather_code");
            boolean isDay = current.optInt("is_day", 1) == 1;

            ConditionMeta meta = mapWmoCode(code, isDay);

            return new WeatherInfo(tempC, tempF, code, isDay, meta.condition,
                    meta.icon, meta.particleMode, city, System.currentTimeMillis());
        } catch (Exception e) {
            return null;
        }
    }

    private static double[] resolveCoordinates(Context context) {
        // Option A: Android TV LocationManager
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                if (lm != null) {
                    for (String provider : lm.getProviders(true)) {
                        Location loc = lm.getLastKnownLocation(provider);
                        if (loc != null) {
                            return new double[]{loc.getLatitude(), loc.getLongitude()};
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Option B: Fast IP-based Geolocation query
        try {
            JSONObject ipGeo = getJson("https://ip-api.com/json/?fields=status,lat,lon,city");
            if (ipGeo != null && "success".equalsIgnoreCase(ipGeo.optString("status"))) {
                double lat = ipGeo.getDouble("lat");
                double lon = ipGeo.getDouble("lon");
                return new double[]{lat, lon};
            }
        } catch (Exception ignored) {}

        // Option C: config.xml predefined latitude/longitude
        try {
            String latStr = context.getString(R.string.latitude);
            String lonStr = context.getString(R.string.longitude);
            if (!latStr.isEmpty() && !lonStr.isEmpty()) {
                return new double[]{Double.parseDouble(latStr), Double.parseDouble(lonStr)};
            }
        } catch (Exception ignored) {}

        // Option D: TimeZone approximate coordinates fallback
        String tz = TimeZone.getDefault().getID();
        if (tz.contains("Calcutta") || tz.contains("Kolkata") || tz.contains("India")) {
            return new double[]{28.6139, 77.2090}; // New Delhi / India
        } else if (tz.contains("New_York") || tz.contains("Eastern")) {
            return new double[]{40.7128, -74.0060}; // New York
        } else if (tz.contains("Los_Angeles") || tz.contains("Pacific")) {
            return new double[]{34.0522, -118.2437}; // Los Angeles
        } else if (tz.contains("Chicago") || tz.contains("Central")) {
            return new double[]{41.8781, -87.6298}; // Chicago
        } else if (tz.contains("London") || tz.contains("GMT")) {
            return new double[]{51.5074, -0.1278}; // London
        } else if (tz.contains("Berlin") || tz.contains("Paris") || tz.contains("Rome")) {
            return new double[]{52.5200, 13.4050}; // Central Europe
        } else if (tz.contains("Tokyo") || tz.contains("Japan")) {
            return new double[]{35.6762, 139.6503}; // Tokyo
        } else if (tz.contains("Sydney") || tz.contains("Melbourne")) {
            return new double[]{-33.8688, 151.2093}; // Sydney
        }

        return new double[]{28.6139, 77.2090}; // Safe default
    }

    private static JSONObject getJson(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "CleanTV-Home/3.5");

            if (conn.getResponseCode() != 200) return null;

            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > 64 * 1024) return null;
                }
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static final class ConditionMeta {
        final String condition;
        final String icon;
        final int particleMode;

        ConditionMeta(String condition, String icon, int particleMode) {
            this.condition = condition;
            this.icon = icon;
            this.particleMode = particleMode;
        }
    }

    private static ConditionMeta mapWmoCode(int code, boolean isDay) {
        switch (code) {
            case 0: // Clear sky
                return new ConditionMeta(isDay ? "Clear Sky" : "Clear Night",
                        isDay ? "☀️" : "🌙", WeatherParticleView.MODE_SUN);
            case 1: // Mainly clear
                return new ConditionMeta("Mainly Clear",
                        isDay ? "🌤️" : "🌙", WeatherParticleView.MODE_SUN);
            case 2: // Partly cloudy
                return new ConditionMeta("Partly Cloudy",
                        isDay ? "⛅" : "☁️", isDay ? WeatherParticleView.MODE_SUN : WeatherParticleView.MODE_CLOUDS);
            case 3: // Overcast
                return new ConditionMeta("Overcast", "☁️", WeatherParticleView.MODE_CLOUDS);
            case 45:
            case 48: // Fog
                return new ConditionMeta("Foggy", "🌫️", WeatherParticleView.MODE_CLOUDS);
            case 51:
            case 53:
            case 55: // Drizzle
                return new ConditionMeta("Light Drizzle", "🌦️", WeatherParticleView.MODE_RAIN);
            case 61:
            case 63:
            case 65: // Rain
                return new ConditionMeta("Rain", "🌧️", WeatherParticleView.MODE_RAIN);
            case 66:
            case 67: // Freezing rain
                return new ConditionMeta("Freezing Rain", "🌧️", WeatherParticleView.MODE_RAIN);
            case 71:
            case 73:
            case 75:
            case 77: // Snow
                return new ConditionMeta("Snowfall", "❄️", WeatherParticleView.MODE_SNOW);
            case 80:
            case 81:
            case 82: // Rain showers
                return new ConditionMeta("Rain Showers", "🌧️", WeatherParticleView.MODE_RAIN);
            case 85:
            case 86: // Snow showers
                return new ConditionMeta("Snow Showers", "🌨️", WeatherParticleView.MODE_SNOW);
            case 95:
            case 96:
            case 99: // Thunderstorm
                return new ConditionMeta("Thunderstorm", "⛈️", WeatherParticleView.MODE_THUNDER);
            default:
                return new ConditionMeta("Fair", isDay ? "☀️" : "🌙", WeatherParticleView.MODE_SUN);
        }
    }

    private static WeatherInfo generateAmbientFallback(Context context) {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        boolean isDay = hour >= 6 && hour < 19;
        int month = cal.get(Calendar.MONTH);

        // Winter months in northern hemisphere (Dec, Jan, Feb)
        boolean isWinter = month == Calendar.DECEMBER || month == Calendar.JANUARY || month == Calendar.FEBRUARY;

        int tempC = isWinter ? 12 : 24;
        int tempF = (int) Math.round(tempC * 9.0 / 5.0 + 32.0);

        String condition = isDay ? "Clear Sky" : "Clear Night";
        String icon = isDay ? "☀️" : "🌙";
        int mode = WeatherParticleView.MODE_SUN;

        return new WeatherInfo(tempC, tempF, 0, isDay, condition, icon, mode, "", System.currentTimeMillis());
    }
}
