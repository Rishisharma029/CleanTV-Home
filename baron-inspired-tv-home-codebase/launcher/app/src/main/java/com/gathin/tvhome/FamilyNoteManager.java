package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class FamilyNoteManager {

    private static final String PREFS_NAME = "cleantv_family_note";
    private static final String KEY_NOTE_JSON = "family_note_json";
    private static final int MAX_STROKES = 100;

    public static final class Point {
        public final float x;
        public final float y;

        public Point(float x, float y) {
            this.x = Math.max(0f, Math.min(1f, x));
            this.y = Math.max(0f, Math.min(1f, y));
        }
    }

    public static final class Stroke {
        public final int color;
        public final float width;
        public final List<Point> points;

        public Stroke(int color, float width, List<Point> points) {
            this.color = color;
            this.width = Math.max(0.001f, Math.min(0.1f, width));
            this.points = points != null ? Collections.unmodifiableList(new ArrayList<>(points)) : Collections.emptyList();
        }

        public Stroke(String hexColor, float width, List<Point> points) {
            this(parseColor(hexColor), width, points);
        }

        public String getColorHex() {
            if ((color & 0xFF000000) == 0xFF000000) {
                return String.format(Locale.US, "#%06X", color & 0x00FFFFFF);
            }
            return String.format(Locale.US, "#%08X", color);
        }
    }

    public interface NoteChangeListener {
        void onNoteChanged(List<Stroke> strokes);
        default void onNoteCleared() {
            onNoteChanged(Collections.emptyList());
        }
    }

    private static final List<Stroke> strokes = new ArrayList<>();
    private static final List<NoteChangeListener> listeners = new ArrayList<>();
    private static SharedPreferences testPrefs = null;

    private FamilyNoteManager() {}

    public static void setTestPreferences(SharedPreferences prefs) {
        testPrefs = prefs;
    }

    private static SharedPreferences getPrefs(Context context) {
        if (testPrefs != null) return testPrefs;
        if (context == null) return null;
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized void registerListener(NoteChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static synchronized void unregisterListener(NoteChangeListener listener) {
        listeners.remove(listener);
    }

    public static synchronized List<Stroke> getStrokes() {
        return new ArrayList<>(strokes);
    }

    public static synchronized boolean hasContent() {
        return !strokes.isEmpty();
    }

    public static synchronized void addStroke(Context context, Stroke stroke) {
        if (stroke == null || stroke.points.isEmpty()) return;
        strokes.add(stroke);
        while (strokes.size() > MAX_STROKES) {
            strokes.remove(0);
        }
        save(context);
        notifyNoteChanged();
    }

    public static synchronized void setStrokes(Context context, List<Stroke> newStrokes) {
        strokes.clear();
        if (newStrokes != null) {
            strokes.addAll(newStrokes);
        }
        while (strokes.size() > MAX_STROKES) {
            strokes.remove(0);
        }
        save(context);
        notifyNoteChanged();
    }

    public static synchronized void clear(Context context) {
        strokes.clear();
        save(context);
        notifyNoteCleared();
    }

    public static synchronized void load(Context context) {
        SharedPreferences p = getPrefs(context);
        if (p == null) return;
        String json = p.getString(KEY_NOTE_JSON, null);
        if (json != null && !json.trim().isEmpty()) {
            List<Stroke> loaded = deserializeFromJson(json);
            strokes.clear();
            strokes.addAll(loaded);
            while (strokes.size() > MAX_STROKES) {
                strokes.remove(0);
            }
            if (!strokes.isEmpty()) {
                notifyNoteChanged();
            }
        }
    }

    public static synchronized void save(Context context) {
        SharedPreferences p = getPrefs(context);
        if (p == null) return;
        if (strokes.isEmpty()) {
            p.edit().remove(KEY_NOTE_JSON).apply();
        } else {
            String json = serializeToJson(strokes);
            p.edit().putString(KEY_NOTE_JSON, json).apply();
        }
    }

    public static int parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return 0xFFFFFFFF;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) {
                return (int) (0xFF000000L | Long.parseLong(s, 16));
            } else if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
        } catch (Throwable ignored) {}
        return 0xFFFFFFFF;
    }

    public static String serializeToJson(List<Stroke> strokeList) {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            if (strokeList != null) {
                for (Stroke s : strokeList) {
                    JSONObject sObj = new JSONObject();
                    String hexColor = ((s.color & 0xFF000000) == 0xFF000000)
                            ? String.format(Locale.US, "#%06X", s.color & 0x00FFFFFF)
                            : String.format(Locale.US, "#%08X", s.color);
                    sObj.put("color", hexColor);
                    sObj.put("width", (double) s.width);
                    JSONArray pts = new JSONArray();
                    for (Point pt : s.points) {
                        JSONObject pObj = new JSONObject();
                        pObj.put("x", (double) pt.x);
                        pObj.put("y", (double) pt.y);
                        pts.put(pObj);
                    }
                    sObj.put("points", pts);
                    arr.put(sObj);
                }
            }
            root.put("strokes", arr);
            root.put("timestamp", System.currentTimeMillis());
            return root.toString();
        } catch (Throwable t) {
            return "{\"strokes\":[]}";
        }
    }

    public static List<Stroke> deserializeFromJson(String json) {
        List<Stroke> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return result;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("strokes");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject sObj = arr.getJSONObject(i);
                    String colorHex = sObj.optString("color", "#FFFFFFFF");
                    int color = parseColor(colorHex);
                    float width = (float) sObj.optDouble("width", 0.015);
                    JSONArray pts = sObj.optJSONArray("points");
                    List<Point> points = new ArrayList<>();
                    if (pts != null) {
                        for (int j = 0; j < pts.length(); j++) {
                            JSONObject pObj = pts.getJSONObject(j);
                            float x = (float) pObj.optDouble("x", 0.0);
                            float y = (float) pObj.optDouble("y", 0.0);
                            points.add(new Point(x, y));
                        }
                    }
                    if (!points.isEmpty()) {
                        result.add(new Stroke(color, width, points));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    public static Stroke parseSingleStrokeJson(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JSONObject sObj = new JSONObject(json);
            String colorHex = sObj.optString("color", "#FFFFFFFF");
            int color = parseColor(colorHex);
            float width = (float) sObj.optDouble("width", 0.015);
            JSONArray pts = sObj.optJSONArray("points");
            List<Point> points = new ArrayList<>();
            if (pts != null) {
                for (int j = 0; j < pts.length(); j++) {
                    JSONObject pObj = pts.getJSONObject(j);
                    float x = (float) pObj.optDouble("x", 0.0);
                    float y = (float) pObj.optDouble("y", 0.0);
                    points.add(new Point(x, y));
                }
            }
            if (!points.isEmpty()) {
                return new Stroke(color, width, points);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void notifyNoteChanged() {
        List<Stroke> copy = getStrokes();
        try {
            Looper looper = Looper.getMainLooper();
            if (looper != null) {
                new Handler(looper).post(() -> dispatchChange(copy));
                return;
            }
        } catch (Throwable ignored) {}
        dispatchChange(copy);
    }

    private static void dispatchChange(List<Stroke> copy) {
        for (NoteChangeListener l : new ArrayList<>(listeners)) {
            try {
                l.onNoteChanged(copy);
            } catch (Throwable ignored) {}
        }
    }

    private static void notifyNoteCleared() {
        try {
            Looper looper = Looper.getMainLooper();
            if (looper != null) {
                new Handler(looper).post(FamilyNoteManager::dispatchClear);
                return;
            }
        } catch (Throwable ignored) {}
        dispatchClear();
    }

    private static void dispatchClear() {
        for (NoteChangeListener l : new ArrayList<>(listeners)) {
            try {
                l.onNoteCleared();
            } catch (Throwable ignored) {}
        }
    }
}
