package com.gathin.tvhome.ai;

import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;
import org.junit.Assume;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Decoupled, opaque-box test execution bridge for CleanTV AI Controller.
 * Bridges test cases to production classes via reflection and dynamic proxies,
 * ensuring progressive testability and clean execution across all milestones.
 */
public final class AiDriver {

    private static final String CLASSIFIER_CLASS = "com.gathin.tvhome.ai.TvIntentClassifier";
    private static final String FALLBACK_CLASS = "com.gathin.tvhome.ai.GoogleAiFallbackDispatcher";
    private static final String EXECUTOR_CLASS = "com.gathin.tvhome.ai.TvActionExecutor";
    private static final String LOOKUP_INTERFACE = "com.gathin.tvhome.ai.TvAppLookup";

    public static boolean isIntentClassifierPresent() {
        try {
            Class.forName(CLASSIFIER_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isFallbackDispatcherPresent() {
        try {
            Class.forName(FALLBACK_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isActionExecutorPresent() {
        try {
            Class.forName(EXECUTOR_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void assumeClassifierPresent() {
        Assume.assumeTrue("TvIntentClassifier not yet compiled into classpath", isIntentClassifierPresent());
    }

    public static void assumeFallbackDispatcherPresent() {
        Assume.assumeTrue("GoogleAiFallbackDispatcher not yet compiled into classpath", isFallbackDispatcherPresent());
    }

    public static void assumeActionExecutorPresent() {
        Assume.assumeTrue("TvActionExecutor not yet compiled into classpath", isActionExecutorPresent());
    }

    /**
     * Creates a dynamic proxy for the TvAppLookup interface matching the given app dictionary.
     */
    public static Object createTvAppLookup(final Map<String, String> labelToPkg) {
        try {
            Class<?> lookupClass = Class.forName(LOOKUP_INTERFACE);
            return Proxy.newProxyInstance(
                    lookupClass.getClassLoader(),
                    new Class<?>[]{lookupClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("findPackageForLabel".equals(name) && args != null && args.length > 0) {
                                String query = (String) args[0];
                                if (query == null) return null;
                                String q = query.trim().toLowerCase();
                                for (Map.Entry<String, String> entry : labelToPkg.entrySet()) {
                                    if (entry.getKey().equalsIgnoreCase(q)) {
                                        return entry.getValue();
                                    }
                                }
                                for (Map.Entry<String, String> entry : labelToPkg.entrySet()) {
                                    if (entry.getKey().toLowerCase().contains(q) || q.contains(entry.getKey().toLowerCase())) {
                                        return entry.getValue();
                                    }
                                }
                                return null;
                            } else if ("getLabelForPackage".equals(name) && args != null && args.length > 0) {
                                String pkg = (String) args[0];
                                if (pkg == null) return null;
                                for (Map.Entry<String, String> entry : labelToPkg.entrySet()) {
                                    if (entry.getValue().equalsIgnoreCase(pkg)) {
                                        return entry.getKey();
                                    }
                                }
                                return null;
                            }
                            return null;
                        }
                    }
            );
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Standard TV App Lookup dictionary with popular TV apps.
     */
    public static Map<String, String> standardTvApps() {
        Map<String, String> apps = new HashMap<>();
        apps.put("YouTube", "com.google.android.youtube.tv");
        apps.put("Netflix", "com.netflix.ninja");
        apps.put("Prime Video", "com.amazon.amazonvideo.livingroom");
        apps.put("Spotify", "com.spotify.tv.android");
        apps.put("Disney+", "in.startv.hotstar");
        apps.put("Hotstar", "in.startv.hotstar");
        apps.put("Kodi", "org.xbmc.kodi");
        apps.put("VLC", "org.videolan.vlc");
        apps.put("Plex", "com.plexapp.android");
        apps.put("Twitch", "tv.twitch.android.app");
        apps.put("Play Store", "com.android.vending");
        return apps;
    }

    /**
     * Classifies a natural language query using TvIntentClassifier.
     */
    public static AiIntentResult classify(String query, Map<String, String> appMap) {
        assumeClassifierPresent();
        try {
            Class<?> classifierCls = Class.forName(CLASSIFIER_CLASS);
            Object lookupProxy = createTvAppLookup(appMap != null ? appMap : standardTvApps());
            Class<?> lookupInterface = Class.forName(LOOKUP_INTERFACE);
            Method method = classifierCls.getMethod("classify", String.class, lookupInterface);
            Object tvIntent = method.invoke(null, query, lookupProxy);
            return wrapTvIntent(tvIntent);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke TvIntentClassifier.classify", t);
        }
    }

    public static AiIntentResult classify(String query) {
        return classify(query, standardTvApps());
    }

    /**
     * Unpacks a TvIntent object into AiIntentResult.
     */
    @SuppressWarnings("unchecked")
    private static AiIntentResult wrapTvIntent(Object tvIntent) {
        if (tvIntent == null) return null;
        try {
            Class<?> cls = tvIntent.getClass();
            Field typeField = cls.getField("type");
            Field actionField = cls.getField("action");
            Field paramsField = cls.getField("params");
            Field queryField = cls.getField("rawQuery");

            Object typeObj = typeField.get(tvIntent);
            String typeStr = typeObj != null ? typeObj.toString() : "UNKNOWN";
            String actionStr = (String) actionField.get(tvIntent);
            Map<String, Object> params = (Map<String, Object>) paramsField.get(tvIntent);
            String rawQuery = (String) queryField.get(tvIntent);

            return new AiIntentResult(typeStr, actionStr, params != null ? params : Collections.emptyMap(), rawQuery, tvIntent);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to unwrap TvIntent fields", t);
        }
    }

    /**
     * Invokes GoogleAiFallbackDispatcher builders.
     */
    public static Intent buildVoiceAssistIntent(String query) {
        assumeFallbackDispatcherPresent();
        try {
            Class<?> cls = Class.forName(FALLBACK_CLASS);
            Method m = cls.getMethod("buildVoiceAssistIntent", String.class);
            return (Intent) m.invoke(null, query);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke buildVoiceAssistIntent", t);
        }
    }

    public static Intent buildSystemAssistIntent(String query) {
        assumeFallbackDispatcherPresent();
        try {
            Class<?> cls = Class.forName(FALLBACK_CLASS);
            Method m = cls.getMethod("buildSystemAssistIntent", String.class);
            return (Intent) m.invoke(null, query);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke buildSystemAssistIntent", t);
        }
    }

    public static Intent buildKatnissSearchIntent(String query) {
        assumeFallbackDispatcherPresent();
        try {
            Class<?> cls = Class.forName(FALLBACK_CLASS);
            Method m = cls.getMethod("buildKatnissSearchIntent", String.class);
            return (Intent) m.invoke(null, query);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke buildKatnissSearchIntent", t);
        }
    }

    public static Intent buildWebSearchIntent(String query) {
        assumeFallbackDispatcherPresent();
        try {
            Class<?> cls = Class.forName(FALLBACK_CLASS);
            Method m = cls.getMethod("buildWebSearchIntent", String.class);
            return (Intent) m.invoke(null, query);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke buildWebSearchIntent", t);
        }
    }

    public static Intent buildBrowserSearchIntent(String query) {
        assumeFallbackDispatcherPresent();
        try {
            Class<?> cls = Class.forName(FALLBACK_CLASS);
            Method m = cls.getMethod("buildBrowserSearchIntent", String.class);
            return (Intent) m.invoke(null, query);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke buildBrowserSearchIntent", t);
        }
    }

    public static AiExecutionResult dispatchFallback(Context context, String query) {
        assumeFallbackDispatcherPresent();
        try {
            Class<?> cls = Class.forName(FALLBACK_CLASS);
            Method m = cls.getMethod("dispatch", Context.class, String.class);
            Object res = m.invoke(null, context, query);
            return wrapExecutionResult(res);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke GoogleAiFallbackDispatcher.dispatch", t);
        }
    }

    /**
     * Unpacks ExecutionResult.
     */
    private static AiExecutionResult wrapExecutionResult(Object res) {
        if (res == null) return null;
        try {
            Class<?> cls = res.getClass();
            String status = (String) cls.getField("status").get(res);
            String action = (String) cls.getField("action").get(res);
            String target = (String) cls.getField("target").get(res);
            String label = (String) cls.getField("label").get(res);
            String message = (String) cls.getField("message").get(res);
            String query = (String) cls.getField("query").get(res);
            boolean isFallback = cls.getField("isFallback").getBoolean(res);

            return new AiExecutionResult(status, action, target, label, message, query, isFallback);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to unwrap ExecutionResult", t);
        }
    }

    /**
     * Queries the Web Remote HTTP server AI command endpoint.
     */
    public static JSONObject sendWebRemoteCommand(int port, String query) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8");
        URL url = new URL("http://127.0.0.1:" + port + "/api/ai/command?q=" + encoded);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(3000);

        int code = conn.getResponseCode();
        BufferedReader in;
        if (code >= 200 && code < 400) {
            in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            in = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            sb.append(line);
        }
        in.close();
        return new JSONObject(sb.toString());
    }

    /**
     * Data wrapper for classified intent results.
     */
    public static final class AiIntentResult {
        public final String type;
        public final String action;
        public final Map<String, Object> params;
        public final String rawQuery;
        public final Object rawObject;

        public AiIntentResult(String type, String action, Map<String, Object> params, String rawQuery, Object rawObject) {
            this.type = type;
            this.action = action;
            this.params = params;
            this.rawQuery = rawQuery;
            this.rawObject = rawObject;
        }

        public Object getParam(String key) {
            return params != null ? params.get(key) : null;
        }

        public String getParamString(String key) {
            Object v = getParam(key);
            return v != null ? v.toString() : null;
        }

        public Integer getParamInt(String key) {
            Object v = getParam(key);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) {
                try {
                    return Integer.parseInt((String) v);
                } catch (NumberFormatException ignored) {}
            }
            return null;
        }
    }

    /**
     * Data wrapper for action execution outcomes.
     */
    public static final class AiExecutionResult {
        public final String status;
        public final String action;
        public final String target;
        public final String label;
        public final String message;
        public final String query;
        public final boolean isFallback;

        public AiExecutionResult(String status, String action, String target, String label, String message, String query, boolean isFallback) {
            this.status = status;
            this.action = action;
            this.target = target;
            this.label = label;
            this.message = message;
            this.query = query;
            this.isFallback = isFallback;
        }
    }
}
