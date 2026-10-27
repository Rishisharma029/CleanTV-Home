package com.gathin.tvhome.ai;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 5-tier fallback intent ladder for routing unresolved queries to Google AI / Search:
 * Tier 1: Intent.ACTION_VOICE_ASSIST
 * Tier 2: Intent.ACTION_ASSIST
 * Tier 3: Katniss ACTION_SEARCH (com.google.android.katniss)
 * Tier 4: Intent.ACTION_WEB_SEARCH
 * Tier 5: Browser Intent.ACTION_VIEW (Google Search URL)
 * Tier 6: Graceful degradation (status: "unavailable")
 */
public class GoogleAiFallbackDispatcher {
    private static final String TAG = "GoogleAiFallback";

    public static final String ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST";
    public static final String ACTION_ASSIST = Intent.ACTION_ASSIST;
    public static final String ACTION_SEARCH = Intent.ACTION_SEARCH;
    public static final String ACTION_WEB_SEARCH = Intent.ACTION_WEB_SEARCH;
    public static final String ACTION_VIEW = Intent.ACTION_VIEW;

    public static final String CATEGORY_BROWSABLE = Intent.CATEGORY_BROWSABLE;

    public static final String EXTRA_QUERY = SearchManager.QUERY;                   // "query"
    public static final String EXTRA_USER_QUERY = SearchManager.USER_QUERY;         // "user_query"
    public static final String EXTRA_TEXT = Intent.EXTRA_TEXT;                     // "android.intent.extra.TEXT"
    public static final String EXTRA_ASSIST_PACKAGE = "android.intent.extra.ASSIST_PACKAGE"; // "android.intent.extra.ASSIST_PACKAGE"

    public static final String PACKAGE_KATNISS = "com.google.android.katniss";
    public static final String PACKAGE_GQS = "com.google.android.googlequicksearchbox";
    public static final String DEFAULT_ASSIST_PACKAGE = "com.gathin.tvhome";
    public static final String GOOGLE_SEARCH_URL_PREFIX = "https://www.google.com/search?q=";

    public static final int FLAG_ACTIVITY_NEW_TASK = Intent.FLAG_ACTIVITY_NEW_TASK;

    public static final String TIER_VOICE_ASSIST = "voice_assist";
    public static final String TIER_SYSTEM_ASSIST = "system_assist";
    public static final String TIER_KATNISS = "katniss";
    public static final String TIER_WEB_SEARCH = "web_search";
    public static final String TIER_BROWSER = "browser";

    /**
     * Immutable specification of an intent to allow pure JVM unit testing
     * without mocking the Android framework.
     */
    public static class FallbackIntentSpec {
        public final String tier;
        public final String action;
        public final String targetPackage;
        public final String dataUri;
        public final String category;
        public final int flags;
        public final Map<String, String> extras;

        public FallbackIntentSpec(String tier, String action, String targetPackage,
                                  String dataUri, String category, int flags,
                                  Map<String, String> extras) {
            this.tier = tier;
            this.action = action;
            this.targetPackage = targetPackage;
            this.dataUri = dataUri;
            this.category = category;
            this.flags = flags;
            this.extras = extras != null ? Collections.unmodifiableMap(new LinkedHashMap<>(extras)) : Collections.emptyMap();
        }
    }

    /**
     * Interface abstracting intent resolution and execution for unit tests and runtime.
     */
    public interface ActivityLauncher {
        boolean isResolvable(FallbackIntentSpec spec);
        boolean launch(FallbackIntentSpec spec);
    }

    // --- Spec Factory Methods ---

    public static FallbackIntentSpec createVoiceAssistSpec(String query, String assistPackage) {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put(EXTRA_QUERY, query);
        extras.put(EXTRA_USER_QUERY, query);
        extras.put(EXTRA_TEXT, query);
        if (assistPackage != null && !assistPackage.isEmpty()) {
            extras.put(EXTRA_ASSIST_PACKAGE, assistPackage);
        }
        return new FallbackIntentSpec(TIER_VOICE_ASSIST, ACTION_VOICE_ASSIST, null, null, null, FLAG_ACTIVITY_NEW_TASK, extras);
    }

    public static FallbackIntentSpec createSystemAssistSpec(String query, String assistPackage) {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put(EXTRA_QUERY, query);
        extras.put(EXTRA_USER_QUERY, query);
        extras.put(EXTRA_TEXT, query);
        if (assistPackage != null && !assistPackage.isEmpty()) {
            extras.put(EXTRA_ASSIST_PACKAGE, assistPackage);
        }
        return new FallbackIntentSpec(TIER_SYSTEM_ASSIST, ACTION_ASSIST, null, null, null, FLAG_ACTIVITY_NEW_TASK, extras);
    }

    public static FallbackIntentSpec createKatnissSearchSpec(String query) {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put(EXTRA_QUERY, query);
        extras.put(EXTRA_USER_QUERY, query);
        extras.put(EXTRA_TEXT, query);
        return new FallbackIntentSpec(TIER_KATNISS, ACTION_SEARCH, PACKAGE_KATNISS, null, null, FLAG_ACTIVITY_NEW_TASK, extras);
    }

    public static FallbackIntentSpec createWebSearchSpec(String query) {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put(EXTRA_QUERY, query);
        extras.put(EXTRA_USER_QUERY, query);
        extras.put(EXTRA_TEXT, query);
        return new FallbackIntentSpec(TIER_WEB_SEARCH, ACTION_WEB_SEARCH, null, null, null, FLAG_ACTIVITY_NEW_TASK, extras);
    }

    public static FallbackIntentSpec createBrowserSearchSpec(String query) {
        String encodedQuery;
        try {
            encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            encodedQuery = query.replace(" ", "+");
        }
        String url = GOOGLE_SEARCH_URL_PREFIX + encodedQuery;
        return new FallbackIntentSpec(TIER_BROWSER, ACTION_VIEW, null, url, CATEGORY_BROWSABLE, FLAG_ACTIVITY_NEW_TASK, Collections.emptyMap());
    }

    public static List<FallbackIntentSpec> getFallbackLadder(String query, String assistPackage) {
        List<FallbackIntentSpec> ladder = new ArrayList<>(5);
        ladder.add(createVoiceAssistSpec(query, assistPackage));
        ladder.add(createSystemAssistSpec(query, assistPackage));
        ladder.add(createKatnissSearchSpec(query));
        ladder.add(createWebSearchSpec(query));
        ladder.add(createBrowserSearchSpec(query));
        return ladder;
    }

    // --- Intent Conversion & Public Builder Contracts ---

    public static class FallbackIntent extends Intent {
        private String action;
        private String targetPackage;
        private Uri dataUri;
        private final java.util.Set<String> categories = new java.util.HashSet<>();
        private int flags;
        private final Map<String, Object> extras = new LinkedHashMap<>();

        public FallbackIntent(String action) {
            super();
            this.action = action;
            if (action != null) {
                try {
                    super.setAction(action);
                } catch (Throwable ignored) {}
            }
        }

        @Override
        public String getAction() {
            if (action != null) {
                return action;
            }
            try {
                return super.getAction();
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public Intent setAction(String action) {
            this.action = action;
            try {
                super.setAction(action);
            } catch (Throwable ignored) {}
            return this;
        }

        @Override
        public String getPackage() {
            if (targetPackage != null) {
                return targetPackage;
            }
            try {
                return super.getPackage();
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public Intent setPackage(String targetPackage) {
            this.targetPackage = targetPackage;
            try {
                super.setPackage(targetPackage);
            } catch (Throwable ignored) {}
            return this;
        }

        @Override
        public Uri getData() {
            if (dataUri != null) {
                return dataUri;
            }
            try {
                return super.getData();
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public Intent setData(Uri data) {
            this.dataUri = data;
            try {
                super.setData(data);
            } catch (Throwable ignored) {}
            return this;
        }

        @Override
        public String getDataString() {
            if (dataUri != null) {
                return dataUri.toString();
            }
            try {
                return super.getDataString();
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public Intent addCategory(String category) {
            if (category != null) {
                this.categories.add(category);
                try {
                    super.addCategory(category);
                } catch (Throwable ignored) {}
            }
            return this;
        }

        @Override
        public boolean hasCategory(String category) {
            if (categories.contains(category)) {
                return true;
            }
            try {
                return super.hasCategory(category);
            } catch (Throwable ignored) {
                return false;
            }
        }

        @Override
        public java.util.Set<String> getCategories() {
            if (!categories.isEmpty()) {
                return categories;
            }
            try {
                return super.getCategories();
            } catch (Throwable ignored) {
                return categories;
            }
        }

        @Override
        public int getFlags() {
            if (flags != 0) {
                return flags;
            }
            try {
                return super.getFlags();
            } catch (Throwable ignored) {
                return 0;
            }
        }

        @Override
        public Intent setFlags(int flags) {
            this.flags = flags;
            try {
                super.setFlags(flags);
            } catch (Throwable ignored) {}
            return this;
        }

        @Override
        public Intent addFlags(int flags) {
            this.flags |= flags;
            try {
                super.addFlags(flags);
            } catch (Throwable ignored) {}
            return this;
        }

        @Override
        public Intent putExtra(String name, String value) {
            extras.put(name, value);
            try {
                super.putExtra(name, value);
            } catch (Throwable ignored) {}
            return this;
        }

        @Override
        public Intent putExtra(String name, boolean value) {
            extras.put(name, value);
            try {
                super.putExtra(name, value);
            } catch (Throwable ignored) {}
            return this;
        }

        @Override
        public Intent putExtra(String name, int value) {
            extras.put(name, value);
            try {
                super.putExtra(name, value);
            } catch (Throwable ignored) {}
            return this;
        }

        @Override
        public boolean hasExtra(String name) {
            if (extras.containsKey(name)) {
                return true;
            }
            try {
                return super.hasExtra(name);
            } catch (Throwable ignored) {
                return false;
            }
        }

        @Override
        public String getStringExtra(String name) {
            Object val = extras.get(name);
            if (val != null) {
                return val.toString();
            }
            try {
                return super.getStringExtra(name);
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public boolean getBooleanExtra(String name, boolean defaultValue) {
            Object val = extras.get(name);
            if (val instanceof Boolean) {
                return (Boolean) val;
            }
            try {
                return super.getBooleanExtra(name, defaultValue);
            } catch (Throwable ignored) {
                return defaultValue;
            }
        }

        @Override
        public int getIntExtra(String name, int defaultValue) {
            Object val = extras.get(name);
            if (val instanceof Integer) {
                return (Integer) val;
            }
            try {
                return super.getIntExtra(name, defaultValue);
            } catch (Throwable ignored) {
                return defaultValue;
            }
        }

        @Override
        public android.os.Bundle getExtras() {
            try {
                android.os.Bundle superExtras = super.getExtras();
                if (superExtras != null) {
                    return superExtras;
                }
            } catch (Throwable ignored) {}

            try {
                android.os.Bundle bundle = new android.os.Bundle();
                for (Map.Entry<String, Object> entry : extras.entrySet()) {
                    if (entry.getValue() instanceof String) {
                        bundle.putString(entry.getKey(), (String) entry.getValue());
                    } else if (entry.getValue() instanceof Boolean) {
                        bundle.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                    } else if (entry.getValue() instanceof Integer) {
                        bundle.putInt(entry.getKey(), (Integer) entry.getValue());
                    }
                }
                return bundle;
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public void writeToParcel(android.os.Parcel dest, int flags) {
            try {
                super.writeToParcel(dest, flags);
            } catch (Throwable ignored) {}
        }

        public static final android.os.Parcelable.Creator<FallbackIntent> CREATOR =
                new android.os.Parcelable.Creator<FallbackIntent>() {
            @Override
            public FallbackIntent createFromParcel(android.os.Parcel in) {
                try {
                    Intent base = Intent.CREATOR.createFromParcel(in);
                    FallbackIntent fi = new FallbackIntent(base.getAction());
                    fi.setPackage(base.getPackage());
                    fi.setData(base.getData());
                    fi.setFlags(base.getFlags());
                    return fi;
                } catch (Throwable t) {
                    return new FallbackIntent(null);
                }
            }

            @Override
            public FallbackIntent[] newArray(int size) {
                return new FallbackIntent[size];
            }
        };
    }

    public static Intent toIntent(FallbackIntentSpec spec) {
        FallbackIntent intent = new FallbackIntent(spec.action);
        if (spec.targetPackage != null) {
            intent.setPackage(spec.targetPackage);
        }
        if (spec.dataUri != null) {
            Uri parsed = null;
            try {
                parsed = Uri.parse(spec.dataUri);
            } catch (Throwable ignored) {}
            if (parsed == null || parsed.toString() == null || parsed.toString().isEmpty()) {
                parsed = new android.net.FallbackUri(spec.dataUri);
            }
            intent.setData(parsed);
        }
        if (spec.category != null) {
            intent.addCategory(spec.category);
        }
        intent.addFlags(spec.flags);
        for (Map.Entry<String, String> entry : spec.extras.entrySet()) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }
        return intent;
    }

    public static Intent buildVoiceAssistIntent(String query) {
        return toIntent(createVoiceAssistSpec(query, DEFAULT_ASSIST_PACKAGE));
    }

    public static Intent buildSystemAssistIntent(String query) {
        return toIntent(createSystemAssistSpec(query, DEFAULT_ASSIST_PACKAGE));
    }

    public static Intent buildKatnissSearchIntent(String query) {
        return toIntent(createKatnissSearchSpec(query));
    }

    public static Intent buildWebSearchIntent(String query) {
        return toIntent(createWebSearchSpec(query));
    }

    public static Intent buildBrowserSearchIntent(String query) {
        return toIntent(createBrowserSearchSpec(query));
    }

    // --- Dispatch Logic ---

    public static ExecutionResult dispatch(Context context, String query) {
        if (context == null) {
            return new ExecutionResult("error", "google_fallback", null, "Google Assistant",
                    "Context is null", query, true);
        }
        String pkg = context.getPackageName();
        if (pkg == null || pkg.isEmpty()) {
            pkg = DEFAULT_ASSIST_PACKAGE;
        }
        return dispatch(query, pkg, new AndroidActivityLauncher(context));
    }

    public static ExecutionResult dispatch(String query, String callerPackage, ActivityLauncher launcher) {
        if (query == null || query.trim().isEmpty()) {
            return new ExecutionResult("error", "google_fallback", null, "Google Assistant",
                    "Query is empty", query, true);
        }

        List<FallbackIntentSpec> ladder = getFallbackLadder(query, callerPackage);
        for (FallbackIntentSpec spec : ladder) {
            try {
                if (launcher.isResolvable(spec)) {
                    boolean success = launcher.launch(spec);
                    if (success) {
                        return new ExecutionResult(
                                "ok",
                                "google_fallback",
                                spec.tier,
                                "Google Assistant",
                                "Delegated to " + spec.tier + ": " + query,
                                query,
                                true
                        );
                    }
                }
            } catch (Exception e) {
                try {
                    Log.w(TAG, "Tier " + spec.tier + " failed to launch: " + e.getMessage());
                } catch (Throwable ignored) {}
            }
        }

        return new ExecutionResult(
                "unavailable",
                "google_fallback",
                null,
                "Google Assistant",
                "Google Assistant is not available on this TV",
                query,
                true
        );
    }

    private static class AndroidActivityLauncher implements ActivityLauncher {
        private final Context context;

        AndroidActivityLauncher(Context context) {
            this.context = context;
        }

        @Override
        public boolean isResolvable(FallbackIntentSpec spec) {
            try {
                Intent intent = toIntent(spec);
                PackageManager pm = context.getPackageManager();
                return pm != null && intent.resolveActivity(pm) != null;
            } catch (Exception e) {
                try {
                    Log.w(TAG, "Resolution check failed for tier " + spec.tier + ": " + e.getMessage());
                } catch (Throwable ignored) {}
                return false;
            }
        }

        @Override
        public boolean launch(FallbackIntentSpec spec) {
            try {
                Intent intent = toIntent(spec);
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                try {
                    Log.w(TAG, "Failed to startActivity for tier " + spec.tier + ": " + e.getMessage());
                } catch (Throwable ignored) {}
                return false;
            }
        }
    }
}
