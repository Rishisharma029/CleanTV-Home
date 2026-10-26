package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 4-Digit Parent PIN modal dialog for Kids Mode.
 * Supports both PIN verification and first-time/change PIN setup with confirmation.
 */
public final class PinEntryDialog {

    public interface OnPinVerifiedListener {
        void onVerified();
    }

    public interface OnPinSetListener {
        void onPinSet();
    }

    private PinEntryDialog() {}

    public static boolean isLockedOut() {
        return ProfileManager.isPinLockedOut(null);
    }

    public static boolean isLockedOut(Context context) {
        return ProfileManager.isPinLockedOut(context);
    }

    public static long getRemainingLockoutSeconds() {
        return ProfileManager.getPinLockoutRemainingSeconds(null);
    }

    public static long getRemainingLockoutSeconds(Context context) {
        return ProfileManager.getPinLockoutRemainingSeconds(context);
    }

    /**
     * Shows PIN verification dialog.
     */
    public static void show(Activity activity, String title, String subtitle, OnPinVerifiedListener listener) {
        showInternal(activity, title, subtitle, false, listener, null);
    }

    /**
     * Shows 2-step PIN setup dialog (Enter PIN -> Confirm PIN).
     */
    public static void showSetup(Activity activity, String title, String subtitle, OnPinSetListener listener) {
        showInternal(activity, title, subtitle, true, null, listener);
    }

    private static void showInternal(Activity activity, String title, String subtitle,
                                     boolean isSetupMode,
                                     OnPinVerifiedListener verifiedListener,
                                     OnPinSetListener setListener) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_pin_entry, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        TextView tvTitle = view.findViewById(R.id.tvPinTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvPinSubtitle);
        TextView tvError = view.findViewById(R.id.tvPinError);
        TextView[] dots = new TextView[]{
                view.findViewById(R.id.pinDot0),
                view.findViewById(R.id.pinDot1),
                view.findViewById(R.id.pinDot2),
                view.findViewById(R.id.pinDot3)
        };

        final String initialTitle = title != null ? title : (isSetupMode ? "Set Parent PIN" : "Parent PIN Required");
        final String initialSubtitle = subtitle != null ? subtitle : (isSetupMode ? "Create a 4-digit PIN" : "Enter 4-digit PIN");

        if (tvTitle != null) tvTitle.setText(initialTitle);
        if (tvSubtitle != null) tvSubtitle.setText(initialSubtitle);

        StringBuilder pinBuilder = new StringBuilder();
        final int[] setupStep = new int[]{1}; // 1 = initial entry, 2 = confirmation
        final String[] firstEnteredPin = new String[1];

        Runnable updateDots = () -> {
            int len = pinBuilder.length();
            for (int i = 0; i < dots.length; i++) {
                if (dots[i] != null) {
                    dots[i].setText(i < len ? "●" : "○");
                    dots[i].setTextColor(i < len ? 0xFF38BDF8 : 0x88FFFFFF);
                }
            }
            if (tvError != null && tvError.getVisibility() == View.VISIBLE
                    && !ProfileManager.isPinLockedOut(activity)) {
                tvError.setVisibility(View.INVISIBLE);
            }
        };

        // Check initial lockout state
        if (!isSetupMode && ProfileManager.isPinLockedOut(activity)) {
            if (tvError != null) {
                long rem = ProfileManager.getPinLockoutRemainingSeconds(activity);
                tvError.setText("Locked for " + rem + "s. Please wait.");
                tvError.setVisibility(View.VISIBLE);
            }
        }

        Runnable processPin = () -> {
            if (!isSetupMode && ProfileManager.isPinLockedOut(activity)) {
                if (tvError != null) {
                    long rem = ProfileManager.getPinLockoutRemainingSeconds(activity);
                    tvError.setText("Locked for " + rem + "s. Please wait.");
                    tvError.setVisibility(View.VISIBLE);
                }
                pinBuilder.setLength(0);
                updateDots.run();
                return;
            }

            if (pinBuilder.length() != 4) return;

            if (isSetupMode) {
                if (setupStep[0] == 1) {
                    firstEnteredPin[0] = pinBuilder.toString();
                    pinBuilder.setLength(0);
                    setupStep[0] = 2;
                    if (tvTitle != null) tvTitle.setText("Confirm Parent PIN");
                    if (tvSubtitle != null) tvSubtitle.setText("Re-enter the 4-digit PIN to confirm");
                    updateDots.run();
                } else {
                    String confirmedPin = pinBuilder.toString();
                    if (confirmedPin.equals(firstEnteredPin[0])) {
                        ProfileManager.setParentPin(activity, confirmedPin);
                        dialog.dismiss();
                        if (setListener != null) {
                            setListener.onPinSet();
                        }
                    } else {
                        if (tvError != null) {
                            tvError.setText("PINs did not match. Please try again.");
                            tvError.setVisibility(View.VISIBLE);
                        }
                        setupStep[0] = 1;
                        firstEnteredPin[0] = null;
                        pinBuilder.setLength(0);
                        if (tvTitle != null) tvTitle.setText(initialTitle);
                        if (tvSubtitle != null) tvSubtitle.setText(initialSubtitle);
                        updateDots.run();
                    }
                }
            } else {
                if (ProfileManager.verifyPin(activity, pinBuilder.toString())) {
                    dialog.dismiss();
                    if (verifiedListener != null) {
                        verifiedListener.onVerified();
                    }
                } else {
                    if (ProfileManager.isPinLockedOut(activity)) {
                        long rem = ProfileManager.getPinLockoutRemainingSeconds(activity);
                        if (tvError != null) {
                            tvError.setText("Too many failed attempts. Locked for " + rem + "s.");
                            tvError.setVisibility(View.VISIBLE);
                        }
                    } else {
                        int failed = ProfileManager.getFailedPinAttempts(activity);
                        int attemptsLeft = Math.max(1, ProfileManager.MAX_FAILED_PIN_ATTEMPTS - failed);
                        if (tvError != null) {
                            tvError.setText("Incorrect PIN. (" + attemptsLeft + " attempts left)");
                            tvError.setVisibility(View.VISIBLE);
                        }
                    }
                    pinBuilder.setLength(0);
                    updateDots.run();
                }
            }
        };

        View.OnClickListener numClick = v -> {
            if (!isSetupMode && ProfileManager.isPinLockedOut(activity)) {
                if (tvError != null) {
                    long rem = ProfileManager.getPinLockoutRemainingSeconds(activity);
                    tvError.setText("Locked for " + rem + "s. Please wait.");
                    tvError.setVisibility(View.VISIBLE);
                }
                return;
            }
            if (v instanceof Button && pinBuilder.length() < 4) {
                String digit = ((Button) v).getText().toString();
                pinBuilder.append(digit);
                updateDots.run();
                if (pinBuilder.length() == 4) {
                    v.postDelayed(processPin, 150);
                }
            }
        };

        int[] btnIds = {
                R.id.btnPin0, R.id.btnPin1, R.id.btnPin2, R.id.btnPin3, R.id.btnPin4,
                R.id.btnPin5, R.id.btnPin6, R.id.btnPin7, R.id.btnPin8, R.id.btnPin9
        };
        for (int id : btnIds) {
            Button b = view.findViewById(id);
            if (b != null) {
                b.setOnClickListener(numClick);
                b.setOnFocusChangeListener((btn, hasFocus) -> {
                    if (hasFocus) {
                        btn.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start();
                        btn.playSoundEffect(SoundEffectConstants.CLICK);
                    } else {
                        btn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                    }
                });
            }
        }

        Button btnBack = view.findViewById(R.id.btnPinBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (pinBuilder.length() > 0) {
                    pinBuilder.deleteCharAt(pinBuilder.length() - 1);
                    updateDots.run();
                }
            });
        }

        Button btnCancel = view.findViewById(R.id.btnPinCancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        // Allow entering PIN via physical keyboard / remote digits
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (!isSetupMode && ProfileManager.isPinLockedOut(activity)) {
                    if (tvError != null) {
                        long rem = ProfileManager.getPinLockoutRemainingSeconds(activity);
                        tvError.setText("Locked for " + rem + "s. Please wait.");
                        tvError.setVisibility(View.VISIBLE);
                    }
                    return true;
                }
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    int digit = keyCode - KeyEvent.KEYCODE_0;
                    if (pinBuilder.length() < 4) {
                        pinBuilder.append(digit);
                        updateDots.run();
                        if (pinBuilder.length() == 4) {
                            view.postDelayed(processPin, 150);
                        }
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DEL) {
                    if (pinBuilder.length() > 0) {
                        pinBuilder.deleteCharAt(pinBuilder.length() - 1);
                        updateDots.run();
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dialog.dismiss();
                    return true;
                }
            }
            return false;
        });

        dialog.setOnShowListener(d -> {
            Button b1 = view.findViewById(R.id.btnPin1);
            if (b1 != null) b1.requestFocus();
        });

        dialog.show();
    }
}
