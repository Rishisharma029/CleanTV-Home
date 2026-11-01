package com.gathin.tvhome.ai.ui;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.ViewGroup;

import com.gathin.tvhome.MainActivity;
import com.gathin.tvhome.ai.ExecutionResult;
import com.gathin.tvhome.ai.TvActionExecutor;
import com.gathin.tvhome.ai.TvAppLookup;
import com.gathin.tvhome.ai.TvAppResolver;
import com.gathin.tvhome.ai.TvIntent;
import com.gathin.tvhome.ai.TvIntentClassifier;

public class VoiceAiHUD {
    public static final int KEYCODE_VOICE_ASSIST = 231;
    public static final int KEYCODE_SEARCH = 84;
    public static final int KEYCODE_ASSIST = 219;

    public static final String STATE_LISTENING = "LISTENING";
    public static final String STATE_PROCESSING = "PROCESSING";
    public static final String STATE_EXECUTING = "EXECUTING";
    public static final String STATE_FALLBACK = "FALLBACK";
    public static final String STATE_ERROR = "ERROR";

    private final Activity activity;
    @SuppressWarnings("unused")
    private final ViewGroup rootContainer;

    private boolean showing;
    private String state = STATE_LISTENING;
    private ExecutionResult lastResult = new ExecutionResult("ok", "none", null, "", "", "", false);

    public VoiceAiHUD(Activity activity, ViewGroup rootContainer) {
        this.activity = activity;
        this.rootContainer = rootContainer;
    }

    public void show() {
        showing = true;
        state = STATE_LISTENING;
    }

    public void show(String query) {
        showing = true;
        if (query == null || query.trim().isEmpty()) {
            state = STATE_LISTENING;
            return;
        }
        state = STATE_PROCESSING;
        TvAppLookup lookup = resolveLookup();
        TvIntent intent = TvIntentClassifier.classify(query, lookup);
        state = STATE_EXECUTING;
        lastResult = TvActionExecutor.execute(activity, intent);
        if (lastResult == null) {
            lastResult = new ExecutionResult("error", "none", null, "", "No execution result", query, false);
            state = STATE_ERROR;
            return;
        }
        if (lastResult.isFallback) {
            state = STATE_FALLBACK;
        } else if (!"ok".equals(lastResult.status)) {
            state = STATE_ERROR;
        }
    }

    public void dismiss() {
        showing = false;
    }

    public boolean isShowing() {
        return showing;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!showing || event == null) return false;
        if (event.getAction() != KeyEvent.ACTION_UP) return true;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KEYCODE_SEARCH || code == KEYCODE_ASSIST || code == KEYCODE_VOICE_ASSIST) {
            dismiss();
            return true;
        }
        return true;
    }

    public String getState() {
        return state;
    }

    public ExecutionResult getLastResult() {
        return lastResult;
    }

    private TvAppLookup resolveLookup() {
        if (activity instanceof MainActivity) {
            try {
                return ((MainActivity) activity).getAppLookup();
            } catch (Throwable ignored) {
            }
        }
        return new TvAppResolver();
    }
}
