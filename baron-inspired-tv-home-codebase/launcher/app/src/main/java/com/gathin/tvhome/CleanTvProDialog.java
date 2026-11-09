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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * CleanTV Comprehensive 3-Tier Plan Manager & Dialog.
 * Plans:
 * 1. Basic Plan (Free) - Default on clean installation: Basic ad blocker & safe bloat disabler.
 * 2. Pro Monthly Plan (₹99 / $5 USD) - 30-day complete bloatware removal, Voice AI assistant, VIP profiles.
 * 3. Pro Lifetime Plan (₹200 / $7 USD) - Lifetime permanent ownership with all features forever.
 */
public final class CleanTvProDialog {

    public static final String PREF_NAME = "cleantv_pro_prefs";
    public static final String KEY_PLAN = "active_plan";
    public static final String KEY_LICENSE_KEY = "license_key";

    public static final String PLAN_BASIC = "basic";
    public static final String PLAN_MONTHLY = "monthly";
    public static final String PLAN_LIFETIME = "lifetime";

    public static final String FEATURE_VOICE_AI = "voice_ai";
    public static final String FEATURE_DEEP_DEBLOAT = "deep_debloat";
    public static final String FEATURE_BATCH_TOOLS = "batch_tools";
    public static final String FEATURE_AD_BLOCK = "ad_block";

    private CleanTvProDialog() {}

    /**
     * Gets active plan. Default is PLAN_BASIC on fresh installation.
     */
    public static String getActivePlan(Context context) {
        if (context == null) return PLAN_BASIC;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_PLAN, PLAN_BASIC);
    }

    public static boolean isPro(Context context) {
        String plan = getActivePlan(context);
        return PLAN_LIFETIME.equals(plan) || PLAN_MONTHLY.equals(plan);
    }

    public static boolean isFeatureUnlocked(Context context, String feature) {
        if (isPro(context)) {
            return true;
        }
        // Basic tier restricts Voice AI, deep 150+ debloat, batch tools, and System-Wide Ad-Shield
        if (FEATURE_VOICE_AI.equals(feature) || FEATURE_DEEP_DEBLOAT.equals(feature)
                || FEATURE_BATCH_TOOLS.equals(feature) || FEATURE_AD_BLOCK.equals(feature)) {
            return false;
        }
        return true;
    }

    public static String getLicenseKey(Context context) {
        if (context == null) return "NONE";
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_LICENSE_KEY, "");
    }

    public static void setActivePlan(Context context, String plan, String key) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
          .putString(KEY_PLAN, plan)
          .putString(KEY_LICENSE_KEY, key != null ? key : "")
          .apply();
    }

    public static void show(Activity activity) {
        show(activity, null);
    }

    public static void show(Activity activity, String upgradeReason) {
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

        // Header and notices
        TextView tvHeaderBadge = view.findViewById(R.id.dialogHeaderCurrentBadge);
        TextView tvNoticeMessage = view.findViewById(R.id.tvUpgradeNoticeMessage);
        LinearLayout layoutNotice = view.findViewById(R.id.layoutUpgradeNotice);
        TextView tvActiveNotice = view.findViewById(R.id.tvActivePlanNotice);

        // Buttons
        Button btnClose = view.findViewById(R.id.btnCloseProDialog);
        Button btnDone = view.findViewById(R.id.btnDoneProDialog);
        Button btnEnterKey = view.findViewById(R.id.btnEnterTvKey);
        Button btnBasicStatus = view.findViewById(R.id.btnPlanBasicStatus);
        Button btnSelectMonthly = view.findViewById(R.id.btnSelectMonthly);
        Button btnSelectLifetime = view.findViewById(R.id.btnSelectLifetime);
        View root = view.findViewById(R.id.proDialogRoot);

        // Show upgrade warning if intercepted
        if (upgradeReason != null && !upgradeReason.trim().isEmpty()) {
            if (layoutNotice != null) layoutNotice.setVisibility(View.VISIBLE);
            if (tvNoticeMessage != null) tvNoticeMessage.setText(upgradeReason);
        } else {
            if (layoutNotice != null) layoutNotice.setVisibility(View.GONE);
        }

        refreshDialogUI(activity, tvHeaderBadge, tvActiveNotice, btnBasicStatus, btnSelectMonthly, btnSelectLifetime);

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
                promptKeyEntry(activity, dialog, tvHeaderBadge, tvActiveNotice, btnBasicStatus, btnSelectMonthly, btnSelectLifetime);
            });
        }

        if (btnSelectMonthly != null) {
            btnSelectMonthly.setOnClickListener(v -> {
                v.playSoundEffect(SoundEffectConstants.CLICK);
                new AlertDialog.Builder(activity)
                        .setTitle("CleanTV Pro Monthly")
                        .setMessage("Plan: Pro Monthly\nPrice: ₹99 / month (India) · $5 USD (International)\n\nComplete checkout on your phone/PC via web (https://cleantv.app/pro) or Microsoft Store, then enter your received license key here.")
                        .setPositiveButton("Enter License Key", (d, w) -> {
                            promptKeyEntry(activity, dialog, tvHeaderBadge, tvActiveNotice, btnBasicStatus, btnSelectMonthly, btnSelectLifetime);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        if (btnSelectLifetime != null) {
            btnSelectLifetime.setOnClickListener(v -> {
                v.playSoundEffect(SoundEffectConstants.CLICK);
                new AlertDialog.Builder(activity)
                        .setTitle("CleanTV Pro Lifetime (Best Value)")
                        .setMessage("Plan: Pro Lifetime Edition\nPrice: ₹200 One-Time (India) · $7 USD (International)\n\nZero recurring fees forever. Complete checkout on your phone/PC via web (https://cleantv.app/pro) or Microsoft Store, then enter your received license key here.")
                        .setPositiveButton("Enter License Key", (d, w) -> {
                            promptKeyEntry(activity, dialog, tvHeaderBadge, tvActiveNotice, btnBasicStatus, btnSelectMonthly, btnSelectLifetime);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // Attach focus scale animation to buttons
        View[] focusables = { btnSelectLifetime, btnSelectMonthly, btnEnterKey, btnDone, btnClose };
        for (View f : focusables) {
            if (f != null) {
                f.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(110).start();
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(110).start();
                    }
                });
            }
        }

        dialog.show();
        if (btnSelectLifetime != null) {
            btnSelectLifetime.requestFocus();
        }
    }

    private static void refreshDialogUI(Context context, TextView tvHeaderBadge, TextView tvActiveNotice,
                                        Button btnBasic, Button btnMonthly, Button btnLifetime) {
        String plan = getActivePlan(context);
        String key = getLicenseKey(context);

        if (PLAN_LIFETIME.equals(plan)) {
            if (tvHeaderBadge != null) {
                tvHeaderBadge.setText("PRO LIFETIME (ACTIVE)");
                tvHeaderBadge.setTextColor(0xFF35D0A0);
            }
            if (tvActiveNotice != null) {
                tvActiveNotice.setText("Active Plan: CleanTV Pro Lifetime Edition · Key: " + (key.isEmpty() ? "Verified Lifetime" : key));
                tvActiveNotice.setTextColor(0xFF35D0A0);
            }
            if (btnLifetime != null) {
                btnLifetime.setText("✓ Current Lifetime Plan");
                btnLifetime.setEnabled(false);
            }
            if (btnMonthly != null) btnMonthly.setText("Switch to Monthly");
            if (btnBasic != null) btnBasic.setText("Basic Features Included");
        } else if (PLAN_MONTHLY.equals(plan)) {
            if (tvHeaderBadge != null) {
                tvHeaderBadge.setText("PRO MONTHLY (ACTIVE)");
                tvHeaderBadge.setTextColor(0xFF49B6FF);
            }
            if (tvActiveNotice != null) {
                tvActiveNotice.setText("Active Plan: CleanTV Pro Monthly (30 Days) · Key: " + (key.isEmpty() ? "Verified Monthly" : key));
                tvActiveNotice.setTextColor(0xFF49B6FF);
            }
            if (btnMonthly != null) {
                btnMonthly.setText("✓ Current Monthly Plan");
                btnMonthly.setEnabled(false);
            }
            if (btnLifetime != null) btnLifetime.setText("Upgrade to Lifetime (₹200 / $7)");
            if (btnBasic != null) btnBasic.setText("Basic Features Included");
        } else {
            // Basic Plan
            if (tvHeaderBadge != null) {
                tvHeaderBadge.setText("BASIC PLAN (CURRENT)");
                tvHeaderBadge.setTextColor(0xFFA1AAB5);
            }
            if (tvActiveNotice != null) {
                tvActiveNotice.setText("Current Plan: Basic (Free). Upgrade to unlock Voice AI & 150+ Bloatware removal.");
                tvActiveNotice.setTextColor(0xFFA1AAB5);
            }
            if (btnBasic != null) {
                btnBasic.setText("✓ Current Plan (Active)");
                btnBasic.setEnabled(false);
            }
            if (btnMonthly != null) btnMonthly.setText("Choose Monthly (₹99 / $5)");
            if (btnLifetime != null) btnLifetime.setText("Get Lifetime (₹200 / $7)");
        }
    }

    private static void promptKeyEntry(Activity activity, Dialog parentDialog,
                                       TextView tvHeaderBadge, TextView tvActiveNotice,
                                       Button btnBasic, Button btnMonthly, Button btnLifetime) {
        EditText input = new EditText(activity);
        input.setHint("CTV-PRO-XXXX-XXXX or CTV-MON-XXXX-XXXX");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setSingleLine(true);

        new AlertDialog.Builder(activity)
                .setTitle("Activate CleanTV License")
                .setMessage("Enter the CleanTV license key received upon payment:")
                .setView(input)
                .setPositiveButton("Activate", (d, which) -> {
                    String enteredKey = input.getText().toString().trim().toUpperCase();
                    if (enteredKey.contains("MON")) {
                        // Monthly License
                        setActivePlan(activity, PLAN_MONTHLY, enteredKey);
                        refreshDialogUI(activity, tvHeaderBadge, tvActiveNotice, btnBasic, btnMonthly, btnLifetime);
                        if (activity instanceof MainActivity) {
                            ((MainActivity) activity).updatePlanBadge();
                        }
                        Toast.makeText(activity, "🎉 CleanTV Pro Monthly Plan Activated (₹99 / $5)!", Toast.LENGTH_LONG).show();
                        if (parentDialog != null && parentDialog.isShowing()) {
                            parentDialog.dismiss();
                        }
                    } else if (enteredKey.matches("^CTV-[A-Z0-9]{3,4}-[A-Z0-9]{4}-[A-Z0-9]{4}$") || enteredKey.startsWith("CTV-")) {
                        // Lifetime License
                        setActivePlan(activity, PLAN_LIFETIME, enteredKey);
                        refreshDialogUI(activity, tvHeaderBadge, tvActiveNotice, btnBasic, btnMonthly, btnLifetime);
                        if (activity instanceof MainActivity) {
                            ((MainActivity) activity).updatePlanBadge();
                        }
                        Toast.makeText(activity, "🎉 CleanTV Pro Lifetime Plan Activated (₹200 / $7)!", Toast.LENGTH_LONG).show();
                        if (parentDialog != null && parentDialog.isShowing()) {
                            parentDialog.dismiss();
                        }
                    } else {
                        Toast.makeText(activity, "Invalid format. Expected format: CTV-PRO-XXXX-XXXX or CTV-MON-XXXX-XXXX", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
