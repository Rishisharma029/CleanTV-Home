package com.gathin.tvhome.ai;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable execution result returned by TvActionExecutor and GoogleAiFallbackDispatcher.
 */
public class ExecutionResult {
    public final String status;      // "ok", "error", "blocked", "unavailable"
    public final String action;      // "open_app", "volume_set", "google_fallback", etc.
    public final String target;      // package name or target tier parameter (e.g. "voice_assist", "katniss")
    public final String label;       // human readable label
    public final String message;     // UI feedback message
    public final String query;       // original raw query
    public final boolean isFallback; // true if handled by Google AI fallback

    public ExecutionResult(String status, String action, String target, String label,
                           String message, String query, boolean isFallback) {
        this.status = status != null ? status : "error";
        this.action = action != null ? action : "unknown";
        this.target = target;
        this.label = label != null ? label : "";
        this.message = message != null ? message : "";
        this.query = query != null ? query : "";
        this.isFallback = isFallback;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("status", status);
            json.put("action", action);
            if (target != null) {
                json.put("target", target);
            } else {
                json.put("target", JSONObject.NULL);
            }
            json.put("label", label);
            json.put("message", message);
            json.put("query", query);
            json.put("isFallback", isFallback);
        } catch (JSONException ignored) {
        }
        return json;
    }

    @Override
    public String toString() {
        return "ExecutionResult{" +
                "status='" + status + '\'' +
                ", action='" + action + '\'' +
                ", target='" + target + '\'' +
                ", label='" + label + '\'' +
                ", message='" + message + '\'' +
                ", query='" + query + '\'' +
                ", isFallback=" + isFallback +
                '}';
    }
}
