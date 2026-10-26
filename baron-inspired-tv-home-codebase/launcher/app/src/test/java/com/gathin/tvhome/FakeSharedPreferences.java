package com.gathin.tvhome;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-memory implementation of SharedPreferences for JVM unit tests.
 */
public class FakeSharedPreferences implements SharedPreferences {

    private final Map<String, Object> values = new HashMap<>();

    public FakeSharedPreferences() {}

    public FakeSharedPreferences(Map<String, Object> initial) {
        if (initial != null) {
            values.putAll(initial);
        }
    }

    @Override
    public Map<String, ?> getAll() {
        return new HashMap<>(values);
    }

    @Override
    public String getString(String key, String defValue) {
        Object val = values.get(key);
        return val instanceof String ? (String) val : defValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object val = values.get(key);
        return val instanceof Set ? (Set<String>) val : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object val = values.get(key);
        return val instanceof Integer ? (Integer) val : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object val = values.get(key);
        return val instanceof Long ? (Long) val : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object val = values.get(key);
        return val instanceof Float ? (Float) val : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object val = values.get(key);
        return val instanceof Boolean ? (Boolean) val : defValue;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    private class FakeEditor implements Editor {
        private final Map<String, Object> pending = new HashMap<>();
        private final Set<String> removed = new HashSet<>();
        private boolean clearAll = false;

        @Override
        public Editor putString(String key, String value) {
            pending.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            pending.put(key, values != null ? new HashSet<>(values) : null);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            pending.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            pending.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            pending.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            pending.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor remove(String key) {
            removed.add(key);
            pending.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            clearAll = true;
            pending.clear();
            removed.clear();
            return this;
        }

        @Override
        public boolean commit() {
            apply();
            return true;
        }

        @Override
        public void apply() {
            if (clearAll) {
                values.clear();
            }
            for (String key : removed) {
                values.remove(key);
            }
            values.putAll(pending);
        }
    }
}
