package com.gathin.tvhome.ai;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of a parsed TV intent.
 */
public final class TvIntent {
    public final IntentType type;
    public final String action;
    public final Map<String, Object> params;
    public final String rawQuery;

    public TvIntent(IntentType type, String action, Map<String, Object> params, String rawQuery) {
        this.type = type != null ? type : IntentType.UNKNOWN;
        this.action = action != null ? action : "";
        this.params = params != null ? Collections.unmodifiableMap(new HashMap<>(params)) : Collections.emptyMap();
        this.rawQuery = rawQuery != null ? rawQuery : "";
    }

    public String getParamString(String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    public int getParamInt(String key, int defaultValue) {
        Object val = params.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public boolean getParamBoolean(String key, boolean defaultValue) {
        Object val = params.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val instanceof String) {
            return Boolean.parseBoolean((String) val);
        }
        return defaultValue;
    }

    public boolean hasParam(String key) {
        return params.containsKey(key);
    }

    public boolean isFallback() {
        return type == IntentType.GOOGLE_AI_FALLBACK;
    }

    public boolean isUnknown() {
        return type == IntentType.UNKNOWN;
    }

    // Static Factory Builders
    public static TvIntent appLaunch(String pkg, String label, String rawQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("package", pkg);
        params.put("target", pkg != null ? pkg : label);
        params.put("label", label);
        params.put("installed", pkg != null);
        return new TvIntent(IntentType.LAUNCH_APP, "open_app", params, rawQuery);
    }

    public static TvIntent appNotFound(String requestedApp, String rawQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("package", null);
        params.put("target", requestedApp);
        params.put("requestedApp", requestedApp);
        params.put("installed", false);
        params.put("code", "APP_NOT_INSTALLED");
        params.put("status", "error");
        return new TvIntent(IntentType.LAUNCH_APP, "open_app", params, rawQuery);
    }

    public static TvIntent volumeStep(String action, int direction, String rawQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("direction", direction);
        params.put("step", direction);
        return new TvIntent(IntentType.VOLUME_CONTROL, action, params, rawQuery);
    }

    public static TvIntent volumeMute(String action, String rawQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        return new TvIntent(IntentType.VOLUME_CONTROL, action, params, rawQuery);
    }

    public static TvIntent volumePercent(int percent, String rawQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("percent", percent);
        params.put("level", percent);
        return new TvIntent(IntentType.VOLUME_CONTROL, "volume_set", params, rawQuery);
    }

    public static TvIntent media(String action, String rawQuery) {
        return new TvIntent(IntentType.MEDIA_CONTROL, action, Collections.emptyMap(), rawQuery);
    }

    public static TvIntent system(String action, String rawQuery) {
        return new TvIntent(IntentType.SYSTEM_ACTION, action, Collections.emptyMap(), rawQuery);
    }

    public static TvIntent launcherFeature(String action, String rawQuery) {
        return new TvIntent(IntentType.LAUNCHER_FEATURE, action, Collections.emptyMap(), rawQuery);
    }

    public static TvIntent googleFallback(String query, String rawQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        return new TvIntent(IntentType.GOOGLE_AI_FALLBACK, "google_fallback", params, rawQuery);
    }

    public static TvIntent error(String action, String message, String rawQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("message", message);
        params.put("error", action);
        params.put("status", "error");
        return new TvIntent(IntentType.UNKNOWN, action, params, rawQuery);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TvIntent)) return false;
        TvIntent tvIntent = (TvIntent) o;
        return type == tvIntent.type &&
               Objects.equals(action, tvIntent.action) &&
               Objects.equals(params, tvIntent.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, action, params);
    }

    @Override
    public String toString() {
        return "TvIntent{" +
               "type=" + type +
               ", action='" + action + '\'' +
               ", params=" + params +
               ", rawQuery='" + rawQuery + '\'' +
               '}';
    }
}
