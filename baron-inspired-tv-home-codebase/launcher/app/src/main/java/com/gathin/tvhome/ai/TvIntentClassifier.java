package com.gathin.tvhome.ai;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java 17 deterministic intent classifier for CleanTV voice and text commands.
 * Matches user commands across 6 intent domains:
 * 1. LAUNCH_APP (installed apps, aliases, packages, uninstalled target detection)
 * 2. VOLUME_CONTROL (discrete steps, mute/unmute, 0-100% level)
 * 3. SYSTEM_ACTION (home, settings, wifi, bluetooth, inputs, manage apps)
 * 4. MEDIA_CONTROL (play, pause, stop, next, previous/rewind)
 * 5. LAUNCHER_FEATURE (control center, addons, screensaver, sleep timer, game mode, profile)
 * 6. GOOGLE_AI_FALLBACK (conversational, 5W1H questions, weather, out-of-scope)
 */
public final class TvIntentClassifier {

    private TvIntentClassifier() {}

    // Normalization patterns
    private static final Pattern PATTERN_POLITE_PREFIX = Pattern.compile(
            "^(?:please[,\\s]+|can\\s+you\\s+(?:please\\s+|kindly\\s+)?|could\\s+you\\s+(?:please\\s+|kindly\\s+)?|would\\s+you\\s+(?:please\\s+|kindly\\s+)?|i\\s+want\\s+to\\s+|hey\\s+(?:tv|cleantv|google|assistant)[,\\s]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_POLITE_SUFFIX = Pattern.compile(
            "(?:[,\\s]+(?:please|now))+$",
            Pattern.CASE_INSENSITIVE
    );

    // Incomplete commands
    private static final Pattern PATTERN_APP_INCOMPLETE = Pattern.compile(
            "^(?:open|launch|start|run|go\\s+to|switch\\s+to)(?:\\s+(?:the\\s+)?app)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_VOLUME_SET_INCOMPLETE = Pattern.compile(
            "^(?:(?:set|turn|change)\\s+)?(?:the\\s+)?(?:volume|sound)\\s+(?:to|up\\s+to|down\\s+to)?$",
            Pattern.CASE_INSENSITIVE
    );

    // 5W1H questions & Fallback triggers (evaluated before control domains and app matching)
    private static final Pattern PATTERN_QUESTION_5W1H = Pattern.compile(
            "^(?:what|who|when|where|why|how|which|whose|whom)(?:'s|s|\\s+is|\\s+are|\\s+was|\\s+were|\\s+did|\\s+do|\\s+does|\\s+can|\\s+could|\\s+will|\\s+would|\\s+to|\\s+much|\\s+many|\\s+far|\\s+tall|\\s+old)?\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_WEATHER = Pattern.compile(
            "^.*\\b(?:weather|forecast|temperature|humidity|rain(?:ing)?|snow(?:ing)?)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_CHIT_CHAT = Pattern.compile(
            "^(?:tell\\s+me\\s+a\\s+joke|joke|sing(?:\\s+a\\s+song)?|tell\\s+me\\s+a\\s+story|are\\s+you\\s+(?:alive|ai|human|real)|who\\s+(?:made|created)\\s+you|good\\s+(?:morning|afternoon|evening|night)|hello|hi|hey|how\\s+are\\s+you|thank\\s+you|thanks|can\\s+you\\s+(?:fly|dance|sing|think|cook|swim|dream|run|walk|help|hear)|do\\s+you\\s+(?:know|think|feel|dream)|is\\s+it\\s+(?:raining|snowing|sunny|cold|hot))\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_SEARCH = Pattern.compile(
            "^(?:search(?:\\s+for)?|google|look\\s+up|find\\s+(?:information\\s+about|out)?)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    // Volume Control Patterns
    private static final Pattern PATTERN_VOLUME_SET = Pattern.compile(
            "^(?:(?:set|turn|change)\\s+)?(?:the\\s+)?(?:volume|sound)\\s+(?:up\\s+to|down\\s+to|to\\s+)?(-?\\d{1,3})\\s*(?:%|percent)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_VOLUME_SET_SHORT = Pattern.compile(
            "^(?:volume|sound)\\s+(-?\\d{1,3})\\s*(?:%|percent)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_VOLUME_UP = Pattern.compile(
            "^(?:volume\\s+up|louder|make\\s+it\\s+louder|turn\\s+(?:up(?:\\s+(?:the\\s+)?(?:volume|sound|it))?|(?:it\\s+|(?:the\\s+)?(?:volume|sound)\\s+)?up)|(?:increase|raise|boost)\\s+(?:the\\s+)?(?:volume|sound)|sound\\s+up)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_VOLUME_DOWN = Pattern.compile(
            "^(?:volume\\s+down|quieter|make\\s+it\\s+quieter|softer|turn\\s+(?:down(?:\\s+(?:the\\s+)?(?:volume|sound|it))?|(?:it\\s+|(?:the\\s+)?(?:volume|sound)\\s+)?down)|(?:decrease|lower)\\s+(?:the\\s+)?(?:volume|sound)|sound\\s+down)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_VOLUME_MUTE = Pattern.compile(
            "^(?:mute(?:\\s+(?:the\\s+)?(?:volume|tv|audio|sound))?|silence|sound\\s+off|turn\\s+off\\s+(?:the\\s+)?(?:sound|audio))$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_VOLUME_UNMUTE = Pattern.compile(
            "^(?:unmute(?:\\s+(?:the\\s+)?(?:volume|tv|audio|sound))?|sound\\s+on|turn\\s+on\\s+(?:the\\s+)?(?:sound|audio)|restore\\s+(?:the\\s+)?(?:volume|sound))$",
            Pattern.CASE_INSENSITIVE
    );

    // Media Control Patterns
    private static final Pattern PATTERN_MEDIA_PLAY = Pattern.compile(
            "^(?:play|resume|start\\s+playback|continue(?:\\s+playback)?|unpause|resume\\s+(?:playback|video|music))$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MEDIA_PAUSE = Pattern.compile(
            "^(?:pause(?:\\s+(?:playback|video|music))?|hold\\s+on)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MEDIA_STOP = Pattern.compile(
            "^(?:stop(?:\\s+(?:playback|video|music))?)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MEDIA_NEXT = Pattern.compile(
            "^(?:next(?:\\s+(?:track|song|video))?|skip(?:\\s+track)?)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MEDIA_FAST_FORWARD = Pattern.compile(
            "^(?:fast\\s+forward)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MEDIA_PREV = Pattern.compile(
            "^(?:previous(?:\\s+(?:track|song|video))?)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MEDIA_REWIND = Pattern.compile(
            "^(?:rewind|replay)$",
            Pattern.CASE_INSENSITIVE
    );

    // System Navigation Patterns
    private static final Pattern PATTERN_SYS_HOME = Pattern.compile(
            "^(?:(?:go\\s+|return\\s+|back\\s+to\\s+|open\\s+|launch\\s+)?home|main\\s+screen)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_SYS_SETTINGS = Pattern.compile(
            "^(?:(?:open\\s+)?(?:system\\s+|tv\\s+)?settings|(?:open\\s+)?preferences)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_SYS_WIFI = Pattern.compile(
            "^(?:(?:open\\s+|connect\\s+)?wi-?fi(?:\\s+settings)?|(?:network|wireless)\\s+settings|connect\\s+wifi)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_SYS_BLUETOOTH = Pattern.compile(
            "^(?:(?:open\\s+|pair\\s+)?bluetooth(?:\\s+settings)?|pair\\s+remote|pair\\s+bluetooth)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_SYS_INPUTS = Pattern.compile(
            "^(?:(?:open\\s+|switch\\s+)?(?:tv\\s+)?inputs?|switch\\s+input|switch\\s+hdmi|hdmi|tv\\s+source|source|input\\s+switcher)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_SYS_MANAGE_APPS = Pattern.compile(
            "^(?:manage\\s+apps|manage\\s+applications|app\\s+settings|installed\\s+apps|applications)$",
            Pattern.CASE_INSENSITIVE
    );

    // Launcher Features Patterns
    private static final Pattern PATTERN_FEAT_CONTROL_CENTER = Pattern.compile(
            "^(?:(?:open|show)\\s+)?control\\s+center|quick\\s+settings|(?:open\\s+)?hud$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_FEAT_ADDONS = Pattern.compile(
            "^(?:(?:open|show)\\s+)?add-?ons?(?:\\s+hub)?|extensions|plugins$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_FEAT_SCREENSAVER = Pattern.compile(
            "^(?:(?:start|open|show)\\s+)?screensaver|aerial|clock\\s+screensaver|ambient(?:\\s+artwork)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_FEAT_SLEEP_TIMER = Pattern.compile(
            "^(?:(?:set|turn\\s+off|cycle)\\s+)?sleep\\s+timer(?:\\s+cycle)?|turn\\s+off\\s+timer|bedtime\\s+timer|standby\\s+timer$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_FEAT_GAME_MODE = Pattern.compile(
            "^(?:(?:toggle|turn\\s+on|turn\\s+off|enable|disable)\\s+)?game\\s+mode|low\\s+latency|gaming\\s+mode$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_FEAT_PROFILE = Pattern.compile(
            "^(?:(?:switch|change)\\s+(?:to\\s+)?profile|profile\\s+picker|profiles|(?:switch\\s+to\\s+)?kids\\s+mode|guest\\s+mode)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_FEAT_DEBLOATER = Pattern.compile(
            "^(?:(?:open\\s+)?debloater|clean\\s+tv|remove\\s+bloatware)$",
            Pattern.CASE_INSENSITIVE
    );

    // App Launch Patterns
    private static final Pattern PATTERN_APP_LAUNCH_PREFIX = Pattern.compile(
            "^(?:open|launch|start|run|go\\s+to|switch\\s+to|play)(?:\\s+(?:the\\s+)?app)?\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_PACKAGE_FORMAT = Pattern.compile(
            "^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$",
            Pattern.CASE_INSENSITIVE
    );

    public static TvIntent classify(String rawQuery, TvAppLookup appLookup) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return TvIntent.error("empty_input", "No speech or text command detected", rawQuery != null ? rawQuery : "");
        }

        String trimmed = rawQuery.trim();
        // Remove enclosing quotes
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            if (trimmed.length() >= 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }

        // Clean punctuation for matching while retaining query text
        String clean = trimmed.replaceAll("[\\.\\,\\!\\?\\;\\:]+$", "").trim();
        if (clean.isEmpty()) {
            return TvIntent.error("empty_input", "No speech or text command detected", rawQuery);
        }
        String lower = clean.toLowerCase(Locale.ROOT);

        // Check for 5W1H questions, weather, chit-chat, search FIRST
        if (PATTERN_QUESTION_5W1H.matcher(lower).matches() ||
            PATTERN_WEATHER.matcher(lower).matches() ||
            PATTERN_CHIT_CHAT.matcher(lower).matches() ||
            PATTERN_SEARCH.matcher(lower).matches()) {
            return TvIntent.googleFallback(clean, rawQuery);
        }

        // Strip polite framing from both lower and clean (to preserve casing for app names)
        String stripped = lower;
        String cleanStripped = clean;
        Matcher prefixMatcher = PATTERN_POLITE_PREFIX.matcher(stripped);
        if (prefixMatcher.find()) {
            int end = prefixMatcher.end();
            stripped = stripped.substring(end).trim();
            if (end < cleanStripped.length()) {
                cleanStripped = cleanStripped.substring(end).trim();
            }
        }
        Matcher suffixMatcher = PATTERN_POLITE_SUFFIX.matcher(stripped);
        if (suffixMatcher.find()) {
            int start = suffixMatcher.start();
            stripped = stripped.substring(0, start).trim();
            if (start <= cleanStripped.length()) {
                cleanStripped = cleanStripped.substring(0, start).trim();
            }
        }

        // Check for incomplete commands (missing parameter error)
        if (PATTERN_APP_INCOMPLETE.matcher(stripped).matches()) {
            return TvIntent.error("missing_parameter", "Please specify an application to open", rawQuery);
        }
        if (PATTERN_VOLUME_SET_INCOMPLETE.matcher(stripped).matches()) {
            return TvIntent.error("missing_parameter", "Please specify a volume level (0-100)", rawQuery);
        }

        // 1. Volume Control Domain
        Matcher volSetMatcher = PATTERN_VOLUME_SET.matcher(stripped);
        if (!volSetMatcher.matches()) {
            volSetMatcher = PATTERN_VOLUME_SET_SHORT.matcher(stripped);
        }
        if (volSetMatcher.matches()) {
            int percent = Integer.parseInt(volSetMatcher.group(1));
            percent = Math.max(0, Math.min(100, percent));
            return TvIntent.volumePercent(percent, rawQuery);
        }
        if (PATTERN_VOLUME_UP.matcher(stripped).matches()) {
            return TvIntent.volumeStep("volume_up", 1, rawQuery);
        }
        if (PATTERN_VOLUME_DOWN.matcher(stripped).matches()) {
            return TvIntent.volumeStep("volume_down", -1, rawQuery);
        }
        if (PATTERN_VOLUME_MUTE.matcher(stripped).matches()) {
            return TvIntent.volumeMute("mute", rawQuery);
        }
        if (PATTERN_VOLUME_UNMUTE.matcher(stripped).matches()) {
            return TvIntent.volumeMute("unmute", rawQuery);
        }

        // 2. Media Playback Domain
        if (PATTERN_MEDIA_PLAY.matcher(stripped).matches()) {
            return TvIntent.media("play", rawQuery);
        }
        if (PATTERN_MEDIA_PAUSE.matcher(stripped).matches()) {
            return TvIntent.media("pause", rawQuery);
        }
        if (PATTERN_MEDIA_STOP.matcher(stripped).matches()) {
            return TvIntent.media("stop", rawQuery);
        }
        if (PATTERN_MEDIA_NEXT.matcher(stripped).matches()) {
            return TvIntent.media("next", rawQuery);
        }
        if (PATTERN_MEDIA_FAST_FORWARD.matcher(stripped).matches()) {
            return TvIntent.media("fast_forward", rawQuery);
        }
        if (PATTERN_MEDIA_PREV.matcher(stripped).matches()) {
            return TvIntent.media("previous", rawQuery);
        }
        if (PATTERN_MEDIA_REWIND.matcher(stripped).matches()) {
            return TvIntent.media("rewind", rawQuery);
        }

        // 3. System Action Domain
        if (PATTERN_SYS_HOME.matcher(stripped).matches()) {
            return TvIntent.system("home", rawQuery);
        }
        if (PATTERN_SYS_SETTINGS.matcher(stripped).matches()) {
            return TvIntent.system("settings", rawQuery);
        }
        if (PATTERN_SYS_WIFI.matcher(stripped).matches()) {
            return TvIntent.system("wifi", rawQuery);
        }
        if (PATTERN_SYS_BLUETOOTH.matcher(stripped).matches()) {
            return TvIntent.system("bluetooth", rawQuery);
        }
        if (PATTERN_SYS_INPUTS.matcher(stripped).matches()) {
            return TvIntent.system("inputs", rawQuery);
        }
        if (PATTERN_SYS_MANAGE_APPS.matcher(stripped).matches()) {
            return TvIntent.system("manage_apps", rawQuery);
        }

        // 4. Launcher Features Domain
        if (PATTERN_FEAT_CONTROL_CENTER.matcher(stripped).matches()) {
            return TvIntent.launcherFeature("control_center", rawQuery);
        }
        if (PATTERN_FEAT_ADDONS.matcher(stripped).matches()) {
            return TvIntent.launcherFeature("addons", rawQuery);
        }
        if (PATTERN_FEAT_SCREENSAVER.matcher(stripped).matches()) {
            return TvIntent.launcherFeature("screensaver", rawQuery);
        }
        if (PATTERN_FEAT_SLEEP_TIMER.matcher(stripped).matches()) {
            return TvIntent.launcherFeature("sleep_timer", rawQuery);
        }
        if (PATTERN_FEAT_GAME_MODE.matcher(stripped).matches()) {
            return TvIntent.launcherFeature("game_mode", rawQuery);
        }
        if (PATTERN_FEAT_PROFILE.matcher(stripped).matches()) {
            return TvIntent.launcherFeature("profile_switcher", rawQuery);
        }
        if (PATTERN_FEAT_DEBLOATER.matcher(stripped).matches()) {
            return TvIntent.launcherFeature("debloater", rawQuery);
        }

        // 5. App Launching Domain
        // 5a. Explicit Prefix
        Matcher appLaunchMatcher = PATTERN_APP_LAUNCH_PREFIX.matcher(cleanStripped);
        if (appLaunchMatcher.matches()) {
            String appTarget = appLaunchMatcher.group(1).trim();
            appTarget = appTarget.replaceAll("(?i)\\s+(?:app|application|tv)$", "").trim();
            if (!appTarget.isEmpty()) {
                String pkg = appLookup != null ? appLookup.findPackageForLabel(appTarget) : null;
                if (pkg != null) {
                    String label = appLookup.getLabelForPackage(pkg);
                    return TvIntent.appLaunch(pkg, label != null ? label : appTarget, rawQuery);
                } else {
                    // If prefix was "play" and target is not an app, route to fallback
                    if (stripped.startsWith("play ")) {
                        return TvIntent.googleFallback(clean, rawQuery);
                    }
                    return TvIntent.appNotFound(appTarget, rawQuery);
                }
            }
        }

        // 5b. Direct Package ID
        if (PATTERN_PACKAGE_FORMAT.matcher(stripped).matches()) {
            String pkg = appLookup != null ? appLookup.findPackageForLabel(stripped) : null;
            if (pkg == null) {
                pkg = stripped;
            }
            String label = appLookup != null ? appLookup.getLabelForPackage(pkg) : null;
            return TvIntent.appLaunch(pkg, label != null ? label : stripped, rawQuery);
        }

        // 5c. Direct App Name / Alias
        if (appLookup != null) {
            String pkg = appLookup.findPackageForLabel(stripped);
            if (pkg != null) {
                String label = appLookup.getLabelForPackage(pkg);
                return TvIntent.appLaunch(pkg, label != null ? label : cleanStripped, rawQuery);
            }
        }

        // 6. Catch-All Fallback
        return TvIntent.googleFallback(clean, rawQuery);
    }
}
