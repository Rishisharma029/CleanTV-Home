package com.gathin.tvhome.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.KeyEvent;

import com.gathin.tvhome.GameModeManager;
import com.gathin.tvhome.MainActivity;
import com.gathin.tvhome.SleepTimerManager;

public final class TvActionExecutor {
    private TvActionExecutor() {}

    public static ExecutionResult execute(Context context, TvIntent intent) {
        if (intent == null) {
            return new ExecutionResult("error", "none", null, "", "No intent to execute", "", false);
        }
        String query = intent.rawQuery;
        try {
            if (intent.isFallback()) {
                String fallbackQuery = intent.getParamString("query");
                if (fallbackQuery == null || fallbackQuery.trim().isEmpty()) {
                    fallbackQuery = query;
                }
                return GoogleAiFallbackDispatcher.dispatch(context, fallbackQuery);
            }
            String action = intent.action != null ? intent.action : "";
            switch (action) {
                case "open_app":
                    return openApp(context, intent, query);
                case "volume_up":
                    dispatchGlobalKey(KeyEvent.KEYCODE_VOLUME_UP);
                    return ok(action, null, "", "Volume up", query);
                case "volume_down":
                    dispatchGlobalKey(KeyEvent.KEYCODE_VOLUME_DOWN);
                    return ok(action, null, "", "Volume down", query);
                case "mute":
                    dispatchGlobalKey(KeyEvent.KEYCODE_VOLUME_MUTE);
                    return ok(action, null, "", "Muted", query);
                case "unmute":
                    dispatchGlobalKey(KeyEvent.KEYCODE_VOLUME_MUTE);
                    return ok(action, null, "", "Unmuted", query);
                case "volume_set":
                    return setVolume(context, intent, query);
                case "play":
                    return mediaKey(action, KeyEvent.KEYCODE_MEDIA_PLAY, "Play", query);
                case "pause":
                    return mediaKey(action, KeyEvent.KEYCODE_MEDIA_PAUSE, "Pause", query);
                case "stop":
                    return mediaKey(action, KeyEvent.KEYCODE_MEDIA_STOP, "Stop", query);
                case "next":
                case "fast_forward":
                    return mediaKey(action, KeyEvent.KEYCODE_MEDIA_NEXT, "Next", query);
                case "previous":
                case "rewind":
                    return mediaKey(action, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous", query);
                case "home":
                    dispatchGlobalKey(KeyEvent.KEYCODE_HOME);
                    return ok(action, null, "", "Going Home", query);
                case "settings":
                    return launchSystemSettings(context, Settings.ACTION_SETTINGS, action, "Opening Settings", query);
                case "wifi":
                    return launchSystemSettings(context, Settings.ACTION_WIFI_SETTINGS, action, "Opening Wi‑Fi settings", query);
                case "bluetooth":
                    return launchSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS, action, "Opening Bluetooth settings", query);
                case "inputs":
                    return launchSystemSettings(context, "com.android.tv.action.VIEW_INPUTS", action, "Opening Inputs", query);
                case "manage_apps":
                    return launchSystemSettings(context, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, action, "Opening app settings", query);
                case "control_center":
                case "addons":
                case "screensaver":
                case "sleep_timer":
                case "game_mode":
                case "profile_switcher":
                case "debloater":
                    return runLauncherFeature(context, action, query);
                default:
                    return new ExecutionResult("error", action, null, "", "Unsupported action: " + action, query, false);
            }
        } catch (Throwable t) {
            return new ExecutionResult("error", intent.action, null, "", "Execution failed: " + t.getMessage(), query, false);
        }
    }

    private static ExecutionResult openApp(Context context, TvIntent intent, String query) {
        String pkg = intent.getParamString("package");
        String label = intent.getParamString("label");
        if (pkg == null || pkg.trim().isEmpty()) {
            return new ExecutionResult("error", "open_app", null, labelOrEmpty(label), "Please specify an application to open", query, false);
        }
        if (context == null) {
            return ok("open_app", pkg, labelOrEmpty(label), "Opening " + readableLabel(label, pkg), query);
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            return new ExecutionResult("error", "open_app", pkg, labelOrEmpty(label), "App is not installed on this TV", query, false);
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
        return ok("open_app", pkg, labelOrEmpty(label), "Opening " + readableLabel(label, pkg) + " on TV", query);
    }

    private static ExecutionResult setVolume(Context context, TvIntent intent, String query) {
        int percent = intent.getParamInt("percent", -1);
        if (percent < 0) {
            percent = intent.getParamInt("level", -1);
        }
        if (percent < 0) {
            return new ExecutionResult("error", "volume_set", null, "", "Please specify a volume level (0-100)", query, false);
        }
        percent = Math.max(0, Math.min(100, percent));
        if (context != null) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int value = Math.round((percent / 100f) * max);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
                return ok("volume_set", null, "", "Volume set to " + percent + "%", query);
            }
        }
        return ok("volume_set", null, "", "Volume set to " + percent + "%", query);
    }

    private static ExecutionResult mediaKey(String action, int keyCode, String message, String query) {
        dispatchGlobalKey(keyCode);
        return ok(action, null, "", message, query);
    }

    private static ExecutionResult runLauncherFeature(Context context, String action, String query) {
        if (!(context instanceof MainActivity)) {
            return new ExecutionResult("error", action, null, "", "Launcher feature requires home screen", query, false);
        }
        MainActivity main = (MainActivity) context;
        switch (action) {
            case "control_center":
                main.showControlCenter();
                return ok(action, null, "", "Opening Control Center", query);
            case "addons":
                main.showAddonHub();
                return ok(action, null, "", "Opening Add-ons Hub", query);
            case "screensaver":
                main.showAerialScreensaver();
                return ok(action, null, "", "Opening screensaver", query);
            case "sleep_timer":
                SleepTimerManager.cycleNext(main);
                return ok(action, null, "", "Cycling sleep timer", query);
            case "game_mode":
                GameModeManager.toggle(main);
                return ok(action, null, "", "Toggling game mode", query);
            case "profile_switcher":
                main.showProfilePicker();
                return ok(action, null, "", "Opening profile picker", query);
            case "debloater":
                main.showDebloaterDialog();
                return ok(action, null, "", "Opening debloater", query);
            default:
                return new ExecutionResult("error", action, null, "", "Unsupported launcher feature", query, false);
        }
    }

    private static ExecutionResult launchSystemSettings(Context context, String intentAction, String action, String message, String query) {
        if (context == null) {
            return ok(action, null, "", message, query);
        }
        Intent intent = new Intent(intentAction);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return ok(action, null, "", message, query);
    }

    private static void dispatchGlobalKey(int keyCode) {
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)});
        } catch (Throwable ignored) {
        }
    }

    private static String labelOrEmpty(String label) {
        return label != null ? label : "";
    }

    private static String readableLabel(String label, String fallback) {
        if (label != null && !label.trim().isEmpty()) {
            return label.trim();
        }
        return fallback != null ? fallback : "app";
    }

    private static ExecutionResult ok(String action, String target, String label, String message, String query) {
        return new ExecutionResult("ok", action, target, label, message, query, false);
    }
}
