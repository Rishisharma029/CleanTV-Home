package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/**
 * Ambient Artwork Screensaver (Apple TV Aerial-style):
 * Displays 4K aerial shots of earth, oceans, and cityscapes drifting across the screen during idle.
 * Consumes any key or remote interaction to return to Home.
 */
public class AmbientAerialActivity extends Activity {

    private AerialSceneView aerialCanvas;
    private TextView txtTitle;
    private TextView txtLocation;
    private View locationPill;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aerial_screensaver);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        aerialCanvas = findViewById(R.id.aerialCanvas);
        txtTitle = findViewById(R.id.aerialTitle);
        txtLocation = findViewById(R.id.aerialLocation);
        locationPill = findViewById(R.id.aerialLocationPill);

        if (aerialCanvas != null) {
            aerialCanvas.setShotChangeListener(shot -> {
                if (txtTitle != null && txtLocation != null) {
                    if (locationPill != null) {
                        locationPill.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                            txtTitle.setText(shot.title);
                            txtLocation.setText(shot.location);
                            locationPill.animate().alpha(1f).setDuration(600).start();
                        }).start();
                    } else {
                        txtTitle.setText(shot.title);
                        txtLocation.setText(shot.location);
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        if (aerialCanvas != null) aerialCanvas.stop();
        super.onPause();
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            finish();
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
