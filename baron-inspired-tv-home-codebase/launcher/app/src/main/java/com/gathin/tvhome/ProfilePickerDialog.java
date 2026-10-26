package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * TV Modal Dialog for switching between:
 * 1) Personal Profile
 * 2) Kids Guard Mode (PIN-protected exit)
 * 3) Guest / Cinema Mode
 */
public final class ProfilePickerDialog {

    private ProfilePickerDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_profile_picker, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        String currentProfile = ProfileManager.getActiveProfile(activity);

        LinearLayout cardPersonal = view.findViewById(R.id.cardPersonal);
        LinearLayout cardKids = view.findViewById(R.id.cardKids);
        LinearLayout cardGuest = view.findViewById(R.id.cardGuest);
        Button btnClose = view.findViewById(R.id.btnCloseProfilePicker);
        View rootOverlay = view.findViewById(R.id.profileDialogRoot);
        View cardContent = view.findViewById(R.id.profileCardContent);

        TextView tagPersonal = view.findViewById(R.id.tagPersonal);
        TextView tagKids = view.findViewById(R.id.tagKids);
        TextView tagGuest = view.findViewById(R.id.tagGuest);

        if (tagPersonal != null && ProfileManager.PROFILE_PERSONAL.equals(currentProfile)) {
            tagPersonal.setText("● Active");
            tagPersonal.setTextColor(0xFF34D399);
        }
        if (tagKids != null && ProfileManager.PROFILE_KIDS.equals(currentProfile)) {
            tagKids.setText("● Active Guard");
            tagKids.setTextColor(0xFF34D399);
        }
        if (tagGuest != null && ProfileManager.PROFILE_GUEST.equals(currentProfile)) {
            tagGuest.setText("● Active");
            tagGuest.setTextColor(0xFF34D399);
        }

        float density = activity.getResources().getDisplayMetrics().density;

        setupCardFocus(cardPersonal, density);
        setupCardFocus(cardKids, density);
        setupCardFocus(cardGuest, density);

        cardPersonal.setOnClickListener(v -> {
            if (ProfileManager.PROFILE_PERSONAL.equals(currentProfile)) {
                dialog.dismiss();
                return;
            }
            if (ProfileManager.isKidsMode(activity)) {
                if (!ProfileManager.isPinConfigured(activity)) {
                    // Safe recovery fallback if PIN is not configured
                    ProfileManager.switchProfile(activity, ProfileManager.PROFILE_PERSONAL);
                    dialog.dismiss();
                    Toast.makeText(activity, "👤 Switched to Personal Profile", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Require Parent PIN to exit kids mode
                PinEntryDialog.show(activity, "Unlock Personal Profile", "Enter 4-digit PIN to exit Kids Guard Mode", () -> {
                    ProfileManager.switchProfile(activity, ProfileManager.PROFILE_PERSONAL);
                    dialog.dismiss();
                    Toast.makeText(activity, "👤 Switched to Personal Profile", Toast.LENGTH_SHORT).show();
                });
            } else {
                ProfileManager.switchProfile(activity, ProfileManager.PROFILE_PERSONAL);
                dialog.dismiss();
                Toast.makeText(activity, "👤 Switched to Personal Profile", Toast.LENGTH_SHORT).show();
            }
        });

        cardKids.setOnClickListener(v -> {
            if (ProfileManager.PROFILE_KIDS.equals(currentProfile)) {
                dialog.dismiss();
                return;
            }
            if (!ProfileManager.isPinConfigured(activity)) {
                PinEntryDialog.showSetup(activity, "Set Parent PIN", "Create a 4-digit PIN to enable Kids Guard", () -> {
                    ProfileManager.switchProfile(activity, ProfileManager.PROFILE_KIDS);
                    dialog.dismiss();
                    Toast.makeText(activity, "🎈 Kids Guard Mode Active! Safe viewing enabled.", Toast.LENGTH_LONG).show();
                });
                return;
            }
            ProfileManager.switchProfile(activity, ProfileManager.PROFILE_KIDS);
            dialog.dismiss();
            Toast.makeText(activity, "🎈 Kids Guard Mode Active! Safe viewing enabled.", Toast.LENGTH_LONG).show();
        });

        cardGuest.setOnClickListener(v -> {
            if (ProfileManager.PROFILE_GUEST.equals(currentProfile)) {
                dialog.dismiss();
                return;
            }
            if (ProfileManager.isKidsMode(activity)) {
                PinEntryDialog.show(activity, "Parent PIN Required", "Enter PIN to switch to Guest Mode", () -> {
                    ProfileManager.switchProfile(activity, ProfileManager.PROFILE_GUEST);
                    dialog.dismiss();
                    Toast.makeText(activity, "🍿 Switched to Guest / Cinema Mode", Toast.LENGTH_SHORT).show();
                });
            } else {
                ProfileManager.switchProfile(activity, ProfileManager.PROFILE_GUEST);
                dialog.dismiss();
                Toast.makeText(activity, "🍿 Switched to Guest / Cinema Mode", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        if (rootOverlay != null) {
            rootOverlay.setOnClickListener(v -> dialog.dismiss());
        }
        if (cardContent != null) {
            cardContent.setOnClickListener(v -> {});
        }

        View previousFocus = activity.getCurrentFocus();
        dialog.setOnDismissListener(d -> {
            View prof = activity.findViewById(R.id.btnProfile);
            if (prof != null && prof.getVisibility() == View.VISIBLE) {
                prof.requestFocus();
            } else if (previousFocus != null && previousFocus.isAttachedToWindow() && previousFocus.getVisibility() == View.VISIBLE) {
                previousFocus.requestFocus();
            } else if (activity instanceof MainActivity) {
                ((MainActivity) activity).focusTile(MainActivity.getLastFocused());
            }
        });

        dialog.setOnShowListener(d -> {
            if (ProfileManager.PROFILE_KIDS.equals(currentProfile)) {
                if (cardKids != null) cardKids.requestFocus();
            } else if (ProfileManager.PROFILE_GUEST.equals(currentProfile)) {
                if (cardGuest != null) cardGuest.requestFocus();
            } else {
                if (cardPersonal != null) cardPersonal.requestFocus();
            }
        });

        dialog.show();
    }

    private static void setupCardFocus(View card, float density) {
        if (card == null) return;
        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setCornerRadius(14 * density);
        normalBg.setColor(0x28FFFFFF);
        normalBg.setStroke((int) (1 * density), 0x22FFFFFF);

        GradientDrawable focusedBg = new GradientDrawable();
        focusedBg.setShape(GradientDrawable.RECTANGLE);
        focusedBg.setCornerRadius(14 * density);
        focusedBg.setColor(0x55FFFFFF);
        focusedBg.setStroke((int) (2 * density), 0xFF38BDF8);

        card.setBackground(normalBg);
        card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setBackground(focusedBg);
                v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(6 * density).setDuration(120).start();
                v.playSoundEffect(SoundEffectConstants.CLICK);
            } else {
                v.setBackground(normalBg);
                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120).start();
            }
        });
    }
}
