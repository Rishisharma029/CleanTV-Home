package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Smart Home HUD:
 * Pops up a frosted overlay allowing users to monitor their doorbell camera (Frigate/RTSP)
 * and toggle room lighting scenes without exiting active video playback.
 */
public final class SmartHomeHUD {

    private SmartHomeHUD() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_smart_home, root, false);
        Button btnClose = dialogView.findViewById(R.id.btnCloseSmartHome);

        View btnMovie = dialogView.findViewById(R.id.btnLightMovie);
        View btnToggle = dialogView.findViewById(R.id.btnLightToggle);
        View btnWarm = dialogView.findViewById(R.id.btnLightWarm);
        View btnAllOff = dialogView.findViewById(R.id.btnLightAllOff);
        View btnRefresh = dialogView.findViewById(R.id.btnRefreshCamera);

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

        // Smart Lighting Actions
        if (btnMovie != null) {
            applyFocus(btnMovie);
            btnMovie.setOnClickListener(v -> SmartHomeManager.executeSmartAction(activity, SmartHomeManager.ACTION_MOVIE_DIM));
        }

        if (btnToggle != null) {
            applyFocus(btnToggle);
            btnToggle.setOnClickListener(v -> SmartHomeManager.executeSmartAction(activity, SmartHomeManager.ACTION_LIGHTS_TOGGLE));
        }

        if (btnWarm != null) {
            applyFocus(btnWarm);
            btnWarm.setOnClickListener(v -> SmartHomeManager.executeSmartAction(activity, SmartHomeManager.ACTION_WARM_COZY));
        }

        if (btnAllOff != null) {
            applyFocus(btnAllOff);
            btnAllOff.setOnClickListener(v -> SmartHomeManager.executeSmartAction(activity, SmartHomeManager.ACTION_ALL_OFF));
        }

        if (btnRefresh != null) {
            applyFocus(btnRefresh);
            btnRefresh.setOnClickListener(v -> Toast.makeText(activity, "Refreshed Front Doorbell Snapshot", Toast.LENGTH_SHORT).show());
        }

        root.addView(dialogView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (btnMovie != null) {
            btnMovie.post(btnMovie::requestFocus);
        } else if (btnClose != null) {
            btnClose.requestFocus();
        }
    }

    private static void applyFocus(View v) {
        v.setOnFocusChangeListener((view, hasFocus) -> {
            view.animate().scaleX(hasFocus ? 1.04f : 1.0f)
                    .scaleY(hasFocus ? 1.04f : 1.0f)
                    .setDuration(120)
                    .start();
            if (hasFocus) {
                view.setBackgroundColor(Color.parseColor("#3338BDF8"));
            } else {
                view.setBackgroundResource(R.drawable.glass_panel);
            }
        });
    }
}
