package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * TV Modal Dialog for CleanTV Add-on Hub & Package Manager.
 * Fully navigable via remote D-pad.
 */
public final class AddonHubDialog {

    private AddonHubDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_addon_hub, root, false);
        Button btnClose = dialogView.findViewById(R.id.btnCloseAddons);
        LinearLayout listContainer = dialogView.findViewById(R.id.addonListContainer);

        Runnable dismiss = () -> {
            if (dialogView.getParent() instanceof ViewGroup) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
        };

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss.run());
        }

        dialogView.setOnClickListener(v -> dismiss.run());
        dialogView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                dismiss.run();
                return true;
            }
            return false;
        });

        populateList(activity, listContainer);

        root.addView(dialogView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (listContainer != null && listContainer.getChildCount() > 0) {
            View firstCard = listContainer.getChildAt(0);
            firstCard.post(firstCard::requestFocus);
        } else if (btnClose != null) {
            btnClose.requestFocus();
        }
    }

    private static void populateList(Activity activity, LinearLayout listContainer) {
        if (listContainer == null || activity == null) return;
        listContainer.removeAllViews();

        float density = activity.getResources().getDisplayMetrics().density;
        List<AddonCatalog.AddonItem> addons = AddonCatalog.getCatalog();

        for (int i = 0; i < addons.size(); i++) {
            AddonCatalog.AddonItem item = addons.get(i);
            boolean installed = AddonCatalog.isInstalled(activity, item.pkg);

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackgroundResource(R.drawable.glass_panel);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (10 * density);
            card.setLayoutParams(lp);

            int p = (int) (14 * density);
            card.setPadding(p, p, p, p);
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // Icon / Initial badge
            TextView icon = new TextView(activity);
            icon.setText(item.name.substring(0, 1));
            icon.setTextColor(Color.WHITE);
            icon.setTextSize(16);
            icon.setTypeface(null, android.graphics.Typeface.BOLD);
            icon.setGravity(android.view.Gravity.CENTER);

            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.OVAL);
            iconBg.setColor(item.accentColor);
            icon.setBackground(iconBg);

            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int) (40 * density), (int) (40 * density));
            iconLp.rightMargin = (int) (14 * density);
            card.addView(icon, iconLp);

            // Content: Category, Name, Description
            LinearLayout textLayout = new LinearLayout(activity);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            textLayout.setLayoutParams(textLp);

            TextView category = new TextView(activity);
            category.setText(item.category.toUpperCase());
            category.setTextColor(item.accentColor);
            category.setTextSize(9);
            category.setLetterSpacing(0.08f);
            textLayout.addView(category);

            TextView title = new TextView(activity);
            title.setText(item.name);
            title.setTextColor(Color.WHITE);
            title.setTextSize(14);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setPadding(0, (int) (2 * density), 0, (int) (2 * density));
            textLayout.addView(title);

            TextView desc = new TextView(activity);
            desc.setText(item.description);
            desc.setTextColor(Color.parseColor("#99FFFFFF"));
            desc.setTextSize(11);
            textLayout.addView(desc);

            card.addView(textLayout);

            // Action Button
            TextView btnAction = new TextView(activity);
            btnAction.setPadding((int) (16 * density), (int) (8 * density), (int) (16 * density), (int) (8 * density));
            btnAction.setTextSize(11);
            btnAction.setTypeface(null, android.graphics.Typeface.BOLD);
            btnAction.setGravity(android.view.Gravity.CENTER);

            updateActionView(btnAction, installed);

            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnLp.leftMargin = (int) (12 * density);
            card.addView(btnAction, btnLp);

            // Focus animations
            card.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.02f : 1.0f)
                        .scaleY(hasFocus ? 1.02f : 1.0f)
                        .setDuration(120)
                        .start();
                if (hasFocus) {
                    v.setBackgroundColor(Color.parseColor("#33FFFFFF"));
                } else {
                    v.setBackgroundResource(R.drawable.glass_panel);
                }
            });

            // Click handling
            card.setOnClickListener(v -> {
                if (AddonCatalog.isInstalled(activity, item.pkg)) {
                    AddonCatalog.launchApp(activity, item.pkg);
                } else {
                    btnAction.setText("Downloading 0%...");
                    Toast.makeText(activity, "Downloading " + item.name + "...", Toast.LENGTH_SHORT).show();

                    AddonCatalog.downloadAndInstall(activity, item, new AddonCatalog.DownloadListener() {
                        @Override
                        public void onProgress(int percent) {
                            btnAction.setText("Downloading " + percent + "%");
                        }

                        @Override
                        public void onSuccess(File apkFile) {
                            btnAction.setText("Installing...");
                        }

                        @Override
                        public void onError(String message) {
                            btnAction.setText("INSTALL");
                            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });

            listContainer.addView(card);
        }
    }

    private static void updateActionView(TextView btnAction, boolean installed) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16);
        if (installed) {
            btnAction.setText("OPEN");
            btnAction.setTextColor(Color.parseColor("#10B981"));
            bg.setColor(Color.parseColor("#2210B981"));
            bg.setStroke(2, Color.parseColor("#4410B981"));
        } else {
            btnAction.setText("INSTALL");
            btnAction.setTextColor(Color.parseColor("#38BDF8"));
            bg.setColor(Color.parseColor("#2238BDF8"));
            bg.setStroke(2, Color.parseColor("#4438BDF8"));
        }
        btnAction.setBackground(bg);
    }
}
