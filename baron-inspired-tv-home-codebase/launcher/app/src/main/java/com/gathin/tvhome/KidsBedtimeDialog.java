package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

/**
 * Fullscreen Bedtime Lockout Screen for Kids Mode.
 * Triggers when the configured daily screen limit is reached.
 * Requires parent PIN unlock to extend screen time or switch profile.
 */
public final class KidsBedtimeDialog {

    private static Dialog activeDialog;

    private KidsBedtimeDialog() {}

    public static synchronized void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (activeDialog != null && activeDialog.isShowing()) return;

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_kids_bedtime, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0xFA0B0F19));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        // Prevent accidental back exit without PIN
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        Button btnUnlock = view.findViewById(R.id.btnBedtimeUnlock);
        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> {
                PinEntryDialog.show(activity, "Parent Bedtime Unlock", "Enter PIN to add +30 mins or switch profile", () -> {
                    // PIN verified: dismiss lockout and grant +30 mins
                    ProfileManager.addExtraKidsTime(activity, 30);
                    dialog.dismiss();
                    Toast.makeText(activity, "🌟 +30 Minutes Screen Time Granted!", Toast.LENGTH_LONG).show();
                });
            });

            btnUnlock.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
                    v.playSoundEffect(SoundEffectConstants.CLICK);
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            });
        }

        dialog.setOnShowListener(d -> {
            if (btnUnlock != null) btnUnlock.requestFocus();
        });

        activeDialog = dialog;
        dialog.show();
    }

    public static synchronized void dismiss() {
        if (activeDialog != null && activeDialog.isShowing()) {
            try {
                activeDialog.dismiss();
            } catch (Throwable ignored) {}
            activeDialog = null;
        }
    }
}
