package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * TV Modal Dialog for the Universal 150+ TV Auto-Debloater Engine.
 * Features:
 * - Safe Preview with 1-Click Execution.
 * - Displays estimated RAM freed & CPU wakeups saved for 1GB RAM TVs.
 * - Hardware-accelerated animator pipelines running on RenderThread.
 * - Fully navigable via remote D-pad with focus trapping.
 */
public final class DebloaterDialog {

    private DebloaterDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_debloater, null);
        dialog.setContentView(dialogView);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        TextView tvDeviceSummary = dialogView.findViewById(R.id.tvDebloaterDeviceSummary);
        TextView tvBrandBadge = dialogView.findViewById(R.id.tvDebloaterBrandBadge);
        TextView tvStats = dialogView.findViewById(R.id.tvDebloaterStats);
        TextView tvSavings = dialogView.findViewById(R.id.tvDebloaterSavings);
        Button btnAutoDebloat = dialogView.findViewById(R.id.btnAutoDebloat);
        Button btnRestoreAll = dialogView.findViewById(R.id.btnRestoreAll);
        Button btnClose = dialogView.findViewById(R.id.btnCloseDebloater);
        LinearLayout listContainer = dialogView.findViewById(R.id.debloatListContainer);

        DebloaterEngine.Brand brand = DebloaterEngine.detectDeviceBrand();
        String devSummary = DebloaterEngine.getDeviceInfoSummary();

        if (tvDeviceSummary != null) {
            tvDeviceSummary.setText(String.format(Locale.US, "%s • %s", brand.displayName, devSummary));
        }
        if (tvBrandBadge != null) {
            tvBrandBadge.setText(brand.name().replace('_', ' '));
        }

        // Setup Buttons with RenderThread animations
        if (btnClose != null) {
            setupButtonHover(btnClose);
            btnClose.setOnClickListener(v -> dialog.dismiss());
            btnClose.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
                            || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                            || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        dialog.dismiss();
                        return true;
                    }
                }
                return false;
            });
        }

        Runnable refreshList = () -> populateList(activity, listContainer, tvStats, tvSavings);

        if (btnAutoDebloat != null) {
            setupButtonHover(btnAutoDebloat);
            btnAutoDebloat.setOnClickListener(v -> {
                showSafePreviewConfirmation(activity, refreshList);
            });
        }

        if (btnRestoreAll != null) {
            setupButtonHover(btnRestoreAll);
            btnRestoreAll.setOnClickListener(v -> {
                int count = DebloaterEngine.restoreAll(activity);
                Toast.makeText(activity, "↩ CleanTV: Restored " + count + " packages", Toast.LENGTH_SHORT).show();
                refreshList.run();
            });
        }

        refreshList.run();

        View previousFocus = activity.getCurrentFocus();
        dialog.setOnDismissListener(d -> {
            if (previousFocus != null && previousFocus.isAttachedToWindow() && previousFocus.getVisibility() == View.VISIBLE) {
                previousFocus.requestFocus();
            } else if (activity instanceof MainActivity) {
                ((MainActivity) activity).focusTile(MainActivity.getLastFocused());
            }
        });

        dialog.setOnShowListener(d -> {
            if (btnAutoDebloat != null) {
                btnAutoDebloat.requestFocus();
            } else if (btnClose != null) {
                btnClose.requestFocus();
            }
        });

        dialog.show();
    }

    private static void setupButtonHover(View view) {
        if (view == null) return;
        view.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate()
                    .scaleX(hasFocus ? 1.08f : 1.0f)
                    .scaleY(hasFocus ? 1.08f : 1.0f)
                    .setDuration(140)
                    .withLayer()
                    .start();
        });
    }

    /**
     * Safe Preview Confirmation Dialog before 1-Click execution.
     * Displays exact RAM recovery and safety guarantees to the user.
     */
    private static void showSafePreviewConfirmation(Activity activity, Runnable onExecuted) {
        DebloaterEngine.DebloaterPreview preview = DebloaterEngine.getDebloaterPreview(activity);

        Dialog previewDialog = new Dialog(activity);
        previewDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.glass_panel);
        root.setPadding((int) (24 * density), (int) (20 * density), (int) (24 * density), (int) (20 * density));

        TextView title = new TextView(activity);
        title.setText("⚡ 1-Click Auto-Debloat Safe Preview");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView devText = new TextView(activity);
        devText.setText(preview.brand.displayName + " • " + preview.deviceInfo);
        devText.setTextColor(0xFF38BDF8);
        devText.setTextSize(11);
        devText.setPadding(0, (int) (4 * density), 0, (int) (12 * density));

        TextView summary = new TextView(activity);
        summary.setText(String.format(Locale.US,
                "CleanTV will disable %d background OEM telemetry, ad injectors, and demo services.\n\n" +
                "Estimated Recovery:\n" +
                "• %d MB RAM Freed (Crucial for 1GB RAM TVs)\n" +
                "• %d CPU Wakeups/Hr Halted (Smooth 60–120 FPS)\n" +
                "• 100%% Reversible with 1-Click Restore anytime.",
                preview.activeBloatCount, preview.estimatedRamFreedMb, preview.cpuWakeupsSavedPerHour));
        summary.setTextColor(0xDDFFFFFF);
        summary.setTextSize(12);
        summary.setLineSpacing(3, 1.15f);

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, (int) (16 * density), 0, 0);

        Button btnExecute = new Button(activity);
        btnExecute.setText("⚡ Execute Auto-Debloat");
        btnExecute.setBackgroundResource(R.drawable.pill_bg);
        btnExecute.setTextColor(activity.getResources().getColorStateList(R.drawable.pill_text));
        btnExecute.setTextSize(11);
        btnExecute.setTypeface(null, android.graphics.Typeface.BOLD);
        btnExecute.setFocusable(true);
        btnExecute.setClickable(true);
        setupButtonHover(btnExecute);

        Button btnCancel = new Button(activity);
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundResource(R.drawable.pill_bg);
        btnCancel.setTextColor(activity.getResources().getColorStateList(R.drawable.pill_text));
        btnCancel.setTextSize(11);
        btnCancel.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCancel.setFocusable(true);
        btnCancel.setClickable(true);
        setupButtonHover(btnCancel);

        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (36 * density));
        cancelLp.leftMargin = (int) (10 * density);
        btnCancel.setLayoutParams(cancelLp);

        btnRow.addView(btnExecute);
        btnRow.addView(btnCancel);

        root.addView(title);
        root.addView(devText);
        root.addView(summary);
        root.addView(btnRow);

        previewDialog.setContentView(root);
        Window w = previewDialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (520 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnExecute.setOnClickListener(v -> {
            previewDialog.dismiss();
            int count = DebloaterEngine.autoDebloatAll(activity);
            Toast.makeText(activity, "⚡ CleanTV: Successfully debloated " + count + " packages!", Toast.LENGTH_SHORT).show();
            if (onExecuted != null) onExecuted.run();
        });

        btnCancel.setOnClickListener(v -> previewDialog.dismiss());

        previewDialog.setOnShowListener(d -> btnExecute.requestFocus());
        previewDialog.show();
    }

    private static void populateList(Activity activity, LinearLayout listContainer, TextView tvStats, TextView tvSavings) {
        if (listContainer == null || activity == null) return;
        listContainer.removeAllViews();

        float density = activity.getResources().getDisplayMetrics().density;
        DebloaterEngine.DebloaterPreview preview = DebloaterEngine.getDebloaterPreview(activity);
        List<DebloaterEngine.DebloatItem> items = DebloaterEngine.scanDebloatCandidates(activity);

        if (tvStats != null) {
            if (items.isEmpty()) {
                tvStats.setText("✨ Clean System! No OEM bloatware detected for your device profile.");
            } else {
                tvStats.setText(String.format(Locale.US,
                        "Found %d OEM signature packages (%d Active Bloat, %d Disabled)",
                        preview.totalFound, preview.activeBloatCount, preview.alreadyDisabledCount));
            }
        }

        if (tvSavings != null) {
            if (preview.activeBloatCount > 0) {
                tvSavings.setText(String.format(Locale.US,
                        "⚡ Safe Preview: ~%d MB RAM Freed • %d CPU Wakeups/Hr Halted (60-120 FPS)",
                        preview.estimatedRamFreedMb, preview.cpuWakeupsSavedPerHour));
                tvSavings.setTextColor(0xFF34D399);
            } else {
                tvSavings.setText("🛡️ System 100% Clean • Maximum Performance Pinned");
                tvSavings.setTextColor(0xFF38BDF8);
            }
        }

        if (items.isEmpty()) {
            TextView emptyText = new TextView(activity);
            emptyText.setText("🎉 Congratulations! Your device is 100% clean of OEM telemetry, ad injections, and vendor partner bloatware.");
            emptyText.setTextColor(0xFF34D399);
            emptyText.setTextSize(13);
            emptyText.setPadding((int)(16 * density), (int)(24 * density), (int)(16 * density), (int)(24 * density));
            listContainer.addView(emptyText);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            DebloaterEngine.DebloatItem item = items.get(i);

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackgroundResource(R.drawable.glass_panel);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (6 * density);
            lp.bottomMargin = (int) (6 * density);
            card.setLayoutParams(lp);
            card.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));

            // Left risk & category badges column
            LinearLayout badgeCol = new LinearLayout(activity);
            badgeCol.setOrientation(LinearLayout.VERTICAL);
            badgeCol.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView riskView = new TextView(activity);
            riskView.setText(item.risk.displayName);
            riskView.setTextColor(item.risk.color);
            riskView.setTextSize(8);
            riskView.setTypeface(null, android.graphics.Typeface.BOLD);
            riskView.setBackgroundColor(0x22000000 | (item.risk.color & 0x00FFFFFF));
            riskView.setPadding((int)(6 * density), (int)(2 * density), (int)(6 * density), (int)(2 * density));

            TextView catView = new TextView(activity);
            catView.setText(item.category.toUpperCase(Locale.US));
            catView.setTextColor(0xFFFBBF24);
            catView.setTextSize(9);
            catView.setTypeface(null, android.graphics.Typeface.BOLD);
            catView.setBackgroundColor(0x22FBBF24);
            catView.setPadding((int)(6 * density), (int)(3 * density), (int)(6 * density), (int)(3 * density));
            LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            catLp.topMargin = (int) (4 * density);
            catView.setLayoutParams(catLp);

            badgeCol.addView(riskView);
            badgeCol.addView(catView);

            // Content column
            LinearLayout infoCol = new LinearLayout(activity);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            infoLp.setMarginStart((int) (12 * density));
            infoLp.leftMargin = (int) (12 * density);
            infoCol.setLayoutParams(infoLp);

            TextView titleView = new TextView(activity);
            titleView.setText(item.label + "  ·  " + item.pkg);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(12);
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView descView = new TextView(activity);
            descView.setText(item.description);
            descView.setTextColor(0x99FFFFFF);
            descView.setTextSize(10);
            descView.setPadding(0, (int) (2 * density), 0, 0);

            infoCol.addView(titleView);
            infoCol.addView(descView);

            // RAM Savings & Status Badges
            LinearLayout rightCol = new LinearLayout(activity);
            rightCol.setOrientation(LinearLayout.VERTICAL);
            rightCol.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);

            TextView ramSavings = new TextView(activity);
            ramSavings.setText("+" + item.estimatedRamMb + " MB RAM");
            ramSavings.setTextColor(0xFF38BDF8);
            ramSavings.setTextSize(9);
            ramSavings.setTypeface(null, android.graphics.Typeface.BOLD);
            ramSavings.setPadding(0, 0, 0, (int) (4 * density));

            TextView badge = new TextView(activity);
            badge.setTextSize(10);
            badge.setTypeface(null, android.graphics.Typeface.BOLD);
            badge.setPadding((int)(8 * density), (int)(4 * density), (int)(8 * density), (int)(4 * density));

            if (item.isEnabled) {
                badge.setText("ACTIVE (BLOAT)");
                badge.setTextColor(0xFFEF4444);
                badge.setBackgroundColor(0x22EF4444);
            } else {
                badge.setText("DISABLED (CLEAN)");
                badge.setTextColor(0xFF34D399);
                badge.setBackgroundColor(0x2234D399);
            }

            rightCol.addView(ramSavings);
            rightCol.addView(badge);

            card.addView(badgeCol);
            card.addView(infoCol);
            card.addView(rightCol);

            // Hardware-accelerated RenderThread zoom
            card.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate()
                        .scaleX(hasFocus ? 1.02f : 1.0f)
                        .scaleY(hasFocus ? 1.02f : 1.0f)
                        .setDuration(120)
                        .withLayer()
                        .start();
            });

            card.setOnClickListener(v -> {
                if (item.isEnabled) {
                    boolean ok = DebloaterEngine.disablePackage(activity, item.pkg);
                    if (ok) {
                        Toast.makeText(activity, "Disabled " + item.label, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "Requires ADB or system permission to disable", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    boolean ok = DebloaterEngine.enablePackage(activity, item.pkg);
                    if (ok) {
                        Toast.makeText(activity, "Restored " + item.label, Toast.LENGTH_SHORT).show();
                    }
                }
                populateList(activity, listContainer, tvStats, tvSavings);
            });

            listContainer.addView(card);
        }
    }
}
