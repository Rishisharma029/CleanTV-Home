package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages the TV Sleep Timer:
 * - Steps: 15m / 30m / 45m / 60m countdown (and Off).
 * - Live second-by-second countdown.
 * - Puts the TV into deep standby upon timer expiration.
 */
public final class SleepTimerManager {

    public static final int[] PRESET_MINUTES = {15, 30, 45, 60};

    public interface SleepTimerListener {
        void onTick(long millisRemaining, String formattedRemaining);
        void onTimerStarted(int minutes, long millisRemaining);
        void onTimerCancelled();
        void onDeepStandbyTriggered();
    }

    private static final List<SleepTimerListener> listeners = new ArrayList<>();
    private static Handler handler;

    private static synchronized Handler getHandler() {
        if (handler == null) {
            try {
                Looper looper = Looper.getMainLooper();
                if (looper != null) {
                    handler = new Handler(looper);
                }
            } catch (Throwable ignored) {
                // In local JVM test runners without mocked Looper
            }
        }
        return handler;
    }

    private static int targetMinutes = 0;
    private static long targetEndTimeMs = 0;
    private static boolean active = false;

    private static final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!active) return;

            long remaining = targetEndTimeMs - System.currentTimeMillis();
            if (remaining <= 0) {
                active = false;
                targetMinutes = 0;
                targetEndTimeMs = 0;
                notifyDeepStandby();
                return;
            }

            notifyTick(remaining);
            Handler h = getHandler();
            if (h != null) {
                h.postDelayed(this, 1000L);
            }
        }
    };

    private static Context appContext;

    private SleepTimerManager() {}

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static synchronized void registerListener(SleepTimerListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            if (active) {
                long remaining = Math.max(0, targetEndTimeMs - System.currentTimeMillis());
                listener.onTick(remaining, formatRemaining(remaining));
            } else {
                listener.onTick(0, "Off");
            }
        }
    }

    public static synchronized void unregisterListener(SleepTimerListener listener) {
        listeners.remove(listener);
    }

    public static boolean isTimerActive() {
        return active && (targetEndTimeMs > System.currentTimeMillis());
    }

    public static int getTargetMinutes() {
        return targetMinutes;
    }

    public static long getMillisRemaining() {
        if (!active) return 0;
        return Math.max(0, targetEndTimeMs - System.currentTimeMillis());
    }

    public static String getFormattedCountdown() {
        if (!isTimerActive()) return "Off";
        long remaining = getMillisRemaining();
        return formatRemaining(remaining);
    }

    public static String formatRemaining(long remainingMs) {
        if (remainingMs <= 0) return "Off";
        long totalSeconds = (remainingMs + 999) / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    /**
     * Cycles through: Off -> 15m -> 30m -> 45m -> 60m -> Off.
     */
    public static int cycleNext(Context context) {
        if (context != null) appContext = context.getApplicationContext();

        int nextMinutes;
        if (!active || targetMinutes == 0) {
            nextMinutes = 15;
        } else if (targetMinutes == 15) {
            nextMinutes = 30;
        } else if (targetMinutes == 30) {
            nextMinutes = 45;
        } else if (targetMinutes == 45) {
            nextMinutes = 60;
        } else {
            nextMinutes = 0; // Off
        }

        setTimer(context, nextMinutes);
        return nextMinutes;
    }

    public static void setTimer(Context context, int minutes) {
        if (context != null) appContext = context.getApplicationContext();
        Handler h = getHandler();
        if (h != null) {
            h.removeCallbacks(tickRunnable);
        }

        if (minutes <= 0) {
            active = false;
            targetMinutes = 0;
            targetEndTimeMs = 0;
            if (context != null) {
                Toast.makeText(context, "Sleep timer turned off", Toast.LENGTH_SHORT).show();
            }
            notifyCancelled();
            return;
        }

        targetMinutes = minutes;
        targetEndTimeMs = System.currentTimeMillis() + (minutes * 60L * 1000L);
        active = true;

        if (context != null) {
            Toast.makeText(context, "Sleep timer set for " + minutes + " minutes", Toast.LENGTH_SHORT).show();
        }

        long remaining = targetEndTimeMs - System.currentTimeMillis();
        notifyStarted(minutes, remaining);
        if (h != null) {
            h.postDelayed(tickRunnable, 1000L);
        }
    }

    public static void cancelTimer(Context context) {
        setTimer(context, 0);
    }

    private static synchronized void notifyTick(long remainingMs) {
        String formatted = formatRemaining(remainingMs);
        for (SleepTimerListener listener : listeners) {
            try {
                listener.onTick(remainingMs, formatted);
            } catch (Throwable ignored) {}
        }
    }

    private static synchronized void notifyStarted(int minutes, long remainingMs) {
        for (SleepTimerListener listener : listeners) {
            try {
                listener.onTimerStarted(minutes, remainingMs);
            } catch (Throwable ignored) {}
        }
    }

    private static synchronized void notifyCancelled() {
        for (SleepTimerListener listener : listeners) {
            try {
                listener.onTimerCancelled();
                listener.onTick(0, "Off");
            } catch (Throwable ignored) {}
        }
    }

    private static synchronized void notifyDeepStandby() {
        for (SleepTimerListener listener : listeners) {
            try {
                listener.onDeepStandbyTriggered();
                listener.onTick(0, "Off");
            } catch (Throwable ignored) {}
        }
        if (appContext != null) {
            executeDeepStandby(appContext);
        }
    }

    /**
     * Executes deep standby across all Android TV & system layers:
     * 1. Mutes audio volume.
     * 2. Injects KEYCODE_SLEEP (223) / KEYCODE_POWER (26).
     * 3. Invokes PowerManager.goToSleep() reflection.
     * 4. Broadcasts Android TV standby intents.
     * 5. Falls back to dock screensaver.
     */
    public static void executeDeepStandby(Context context) {
        if (context == null) return;

        // 1. Mute audio volume immediately
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            }
        } catch (Throwable ignored) {}

        // 2. Dispatch KEYCODE_SLEEP (223) via shell
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "223"});
        } catch (Throwable ignored) {
            try {
                Runtime.getRuntime().exec(new String[]{"input", "keyevent", "KEYCODE_SLEEP"});
            } catch (Throwable ignored2) {}
        }

        // 3. Dispatch KEYCODE_POWER (26) via shell fallback
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "26"});
        } catch (Throwable ignored) {}

        // 4. PowerManager goToSleep (reflection for system TV builds)
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                Method goToSleep = PowerManager.class.getMethod("goToSleep", long.class);
                goToSleep.invoke(pm, SystemClock.uptimeMillis());
            }
        } catch (Throwable ignored) {}

        // 5. Send Android TV standby / pre-shutdown broadcasts
        try {
            context.sendBroadcast(new Intent("android.intent.action.ACTION_PRE_SHUTDOWN"));
            context.sendBroadcast(new Intent("com.android.tv.action.STANDBY"));
        } catch (Throwable ignored) {}

        // 6. Fallback: launch desk dock screensaver
        try {
            context.startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_DESK_DOCK)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {}
    }
}
