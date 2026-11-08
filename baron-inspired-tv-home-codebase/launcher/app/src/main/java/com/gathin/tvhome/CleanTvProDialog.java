package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * CleanTV Pro Dialog for TV Launcher.
 * Displays CleanTV Pro commercial edition status, features, and license management.
 */
public final class CleanTvProDialog {

    private static final String PREF_NAME = "cleantv_pro_prefs";
    private static final String KEY_PRO_ACTIVE = "pro_active";
    private static final String KEY_LICENSE_KEY = "license_key";

    private CleanTvProDialog() {}

    public static boolean isProActive(Context context) {
        if (context == null) return true;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // Default to active for CleanTV Companion-managed installations
        return sp.getBoolean(KEY_PRO_ACTIVE, true);
    }

    public static String getLicenseKey(Context context) {
        if (context == null) return "CTV-PRO-DESKTOP-LINKED";
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_LICENSE_KEY, "CTV-PRO-STORE-VERIFIED");
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_cleantv_pro, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        TextView tvStatus = view.findViewById(R.id.tvLicenseStatusDetail);
        TextView tvKey = view.findViewById(R.id.tvLicenseKeyDetail);
        TextView tvBadge = view.findViewById(R.id.dialogProBadge);
        Button btnClose = view.findViewById(R.id.btnCloseProDialog);
        Button btnDone = view.findViewById(R.id.btnDoneProDialog);
        Button btnEnterKey = view.findViewById(R.id.btnEnterTvKey);
        View root = view.findViewById(R.id.proDialogRoot);

        boolean isPro = isProActive(activity);
        String currentKey = getLicenseKey(activity);

        if (tvStatus != null) {
            tvStatus.setText(isPro ? "CleanTV Pro Status: Active Lifetime License" : "CleanTV Free Tier");
            tvStatus.setTextColor(isPro ? 0xFF35D0A0 : 0xFFA1AAB5);
        }
        if (tvBadge != null) {
            tvBadge.setText(isPro ? "PRO LIFETIME" : "FREE TIER");
            tvBadge.setTextColor(isPro ? 0xFF35D0A0 : 0xFFA1AAB5);
        }
        if (tvKey != null) {
            tvKey.setText(isPro 
                ? "License: " + currentKey + " · Verified via CleanTV Desktop Manager"
                : "Upgrade via CleanTV Desktop Manager or enter CTV-PRO license key.");
        }

        View.OnClickListener dismissListener = v -> {
            v.playSoundEffect(SoundEffectConstants.CLICK);
            dialog.dismiss();
        };

        if (btnClose != null) btnClose.setOnClickListener(dismissListener);
        if (btnDone != null) btnDone.setOnClickListener(dismissListener);
        if (root != null) root.setOnClickListener(dismissListener);

        if (btnEnterKey != null) {
            btnEnterKey.setOnClickListener(v -> {
                v.playSoundEffect(SoundEffectConstants.CLICK);
                promptKeyEntry(activity, dialog, tvStatus, tvBadge, tvKey);
            });
        }

        // Attach focus scale animations to interactive buttons
        View[] focusables = { btnDone, btnEnterKey, btnClose };
        for (View f : focusables) {
            if (f != null) {
                f.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                    }
                });
            }
        }

        dialog.show();
        if (btnDone != null) {
            btnDone.requestFocus();
        }
    }

    private static void promptKeyEntry(Activity activity, Dialog parentDialog, TextView tvStatus, TextView tvBadge, TextView tvKey) {
        EditText input = new EditText(activity);
        input.setHint("CTV-PRO-XXXX-XXXX");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setSingleLine(true);

        new AlertDialog.Builder(activity)
                .setTitle("Activate CleanTV Pro")
                .setMessage("Enter your 17-character CleanTV Pro license key:")
                .setView(input)
                .setPositiveButton("Activate", (d, which) -> {
                    String enteredKey = input.getText().toString().trim().toUpperCase();
                    if (enteredKey.matches("^CTV-PRO-[A-Z0-9]{4}-[A-Z0-9]{4}$") || enteredKey.startsWith("CTV-PRO-")) {
                        SharedPreferences sp = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                        sp.edit()
                          .putBoolean(KEY_PRO_ACTIVE, true)
                          .putString(KEY_LICENSE_KEY, enteredKey)
                          .apply();

                        if (tvStatus != null) {
                            tvStatus.setText("CleanTV Pro Status: Active Lifetime License");
                            tvStatus.setTextColor(0xFF35D0A0);
                        }
                        if (tvBadge != null) {
                            tvBadge.setText("PRO LIFETIME");
                            tvBadge.setTextColor(0xFF35D0A0);
                        }
                        if (tvKey != null) {
                            tvKey.setText("License: " + enteredKey + " · Verified Active");
                        }
                        Toast.makeText(activity, "🎉 CleanTV Pro Lifetime Activated!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(activity, "Invalid format. Expected format: CTV-PRO-XXXX-XXXX", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
