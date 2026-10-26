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
import android.widget.TextView;
import android.widget.Toast;

/**
 * 4-Digit Parent PIN verification modal dialog for Kids Mode.
 */
public final class PinEntryDialog {

    public interface OnPinVerifiedListener {
        void onVerified();
    }

    private static int failedAttempts = 0;
    private static long lockoutUntilMs = 0L;

    private PinEntryDialog() {}

    public static boolean isLockedOut() {
        return System.currentTimeMillis() < lockoutUntilMs;
    }

    public static long getRemainingLockoutSeconds() {
        return Math.max(0, (lockoutUntilMs - System.currentTimeMillis() + 999) / 1000);
    }

    public static void show(Activity activity, String title, String subtitle, OnPinVerifiedListener listener) {
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

        if (title != null && tvTitle != null) tvTitle.setText(title);
        if (subtitle != null && tvSubtitle != null) tvSubtitle.setText(subtitle);

        StringBuilder pinBuilder = new StringBuilder();

        Runnable updateDots = () -> {
            int len = pinBuilder.length();
            for (int i = 0; i < dots.length; i++) {
                if (dots[i] != null) {
                    dots[i].setText(i < len ? "●" : "○");
                    dots[i].setTextColor(i < len ? 0xFF38BDF8 : 0x88FFFFFF);
                }
            }
            if (tvError != null) tvError.setVisibility(View.INVISIBLE);
        };

        Runnable checkPin = () -> {
            if (isLockedOut()) {
                if (tvError != null) {
                    tvError.setText("Locked for " + getRemainingLockoutSeconds() + "s. Please wait.");
                    tvError.setVisibility(View.VISIBLE);
                }
                pinBuilder.setLength(0);
                updateDots.run();
                return;
            }
            if (pinBuilder.length() == 4) {
                if (ProfileManager.verifyPin(activity, pinBuilder.toString())) {
                    failedAttempts = 0;
                    lockoutUntilMs = 0L;
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onVerified();
                    }
                } else {
                    failedAttempts++;
                    if (failedAttempts >= 5) {
                        lockoutUntilMs = System.currentTimeMillis() + 30000L;
                        failedAttempts = 0;
                        if (tvError != null) {
                            tvError.setText("Too many failed attempts. Locked for 30s.");
                            tvError.setVisibility(View.VISIBLE);
                        }
                    } else {
                        if (tvError != null) {
                            tvError.setText("Incorrect PIN. (" + (5 - failedAttempts) + " attempts left)");
                            tvError.setVisibility(View.VISIBLE);
                        }
                    }
                    pinBuilder.setLength(0);
                    updateDots.run();
                }
            }
        };

        View.OnClickListener numClick = v -> {
            if (isLockedOut()) {
                if (tvError != null) {
                    tvError.setText("Locked for " + getRemainingLockoutSeconds() + "s. Please wait.");
                    tvError.setVisibility(View.VISIBLE);
                }
                return;
            }
            if (v instanceof Button && pinBuilder.length() < 4) {
                String digit = ((Button) v).getText().toString();
                pinBuilder.append(digit);
                updateDots.run();
                if (pinBuilder.length() == 4) {
                    v.postDelayed(checkPin, 150);
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

        // Allow entering PIN via physical keyboard digits
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (isLockedOut()) {
                    if (tvError != null) {
                        tvError.setText("Locked for " + getRemainingLockoutSeconds() + "s. Please wait.");
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
                            view.postDelayed(checkPin, 150);
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
