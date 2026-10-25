package com.gathin.tvhome;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.view.Window;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages Game Mode:
 * - Forces ALLM (Auto Low Latency Mode) via Window.setPreferMinimalPostProcessing().
 * - Disables motion smoothing (MEMC) and image processing lag for connected gaming consoles.
 */
public final class GameModeManager {

    private static final String PREFS_NAME = "cleantv_gaming_prefs";
    private static final String KEY_GAME_MODE = "game_mode_enabled";

    public interface GameModeListener {
        void onGameModeChanged(boolean enabled);
    }

    private static final List<GameModeListener> listeners = new ArrayList<>();

    private GameModeManager() {}

    public static synchronized void registerListener(GameModeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static synchronized void unregisterListener(GameModeListener listener) {
        listeners.remove(listener);
    }

    public static boolean isGameModeEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_GAME_MODE, false);
    }

    public static boolean toggle(Activity activity) {
        if (activity == null) return false;
        boolean current = isGameModeEnabled(activity);
        boolean target = !current;
        setGameMode(activity, target);
        return target;
    }

    public static void setGameMode(Activity activity, boolean enabled) {
        if (activity == null) return;

        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_GAME_MODE, enabled).apply();

        // 1. Force ALLM (Auto Low Latency Mode) via Window minimal post processing (Android 11+ / API 30+)
        try {
            Window window = activity.getWindow();
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setPreferMinimalPostProcessing(enabled);
            }
        } catch (Throwable ignored) {}

        // 2. Adjust system picture and latency settings
        try {
            ContentResolver cr = activity.getContentResolver();
            Settings.Global.putInt(cr, "game_mode", enabled ? 1 : 0);
            Settings.Global.putInt(cr, "low_latency_mode", enabled ? 1 : 0);
            Settings.System.putInt(cr, "picture_mode_game", enabled ? 1 : 0);

            // Turn off motion smoothing (MEMC) when Game Mode is ON
            if (enabled) {
                Settings.Global.putInt(cr, "motion_smoothing", 0);
                Settings.Global.putInt(cr, "memc_mode", 0);
                Settings.System.putInt(cr, "picture_memc", 0);
            }
        } catch (Throwable ignored) {}

        // 3. Notify listeners
        synchronized (GameModeManager.class) {
            for (GameModeListener listener : listeners) {
                try {
                    listener.onGameModeChanged(enabled);
                } catch (Throwable ignored) {}
            }
        }

        // 4. User feedback
        if (enabled) {
            Toast.makeText(activity, "Game Mode ON: ALLM Active · MEMC Off", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, "Game Mode OFF: Standard Picture", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Applies the current persisted game mode to an Activity window (e.g. in onCreate / onResume).
     */
    public static void applyWindowMode(Activity activity) {
        if (activity == null) return;
        boolean enabled = isGameModeEnabled(activity);
        try {
            Window window = activity.getWindow();
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setPreferMinimalPostProcessing(enabled);
            }
        } catch (Throwable ignored) {}
    }
}
