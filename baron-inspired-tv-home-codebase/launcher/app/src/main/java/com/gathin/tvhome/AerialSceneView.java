package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * 4K Ambient Aerial Screensaver Canvas:
 * Renders cinematic drifting vistas of Earth from orbit, mountain ranges, coastlines, and city nightscapes
 * with smooth Ken Burns camera pan and cross-fade transitions.
 */
public class AerialSceneView extends View {

    public static class AerialShot {
        public final String title;
        public final String location;
        public final int skyTop;
        public final int skyBottom;
        public final int horizonColor;
        public final int terrainColor;
        public final int glowColor;
        public final boolean isNight;

        public AerialShot(String title, String location, int skyTop, int skyBottom, int horizonColor, int terrainColor, int glowColor, boolean isNight) {
            this.title = title;
            this.location = location;
            this.skyTop = skyTop;
            this.skyBottom = skyBottom;
            this.horizonColor = horizonColor;
            this.terrainColor = terrainColor;
            this.glowColor = glowColor;
            this.isNight = isNight;
        }
    }

    public static final AerialShot[] SHOTS = new AerialShot[]{
            new AerialShot("Earth from Orbit", "ISS Low Orbit • Sunrise Horizon", 0xFF030712, 0xFF0B192C, 0xFF38BDF8, 0xFF0F172A, 0x8860A5FA, false),
            new AerialShot("San Francisco", "Golden Gate & Pacific Fog", 0xFF0F172A, 0xFF1E293B, 0xFFF97316, 0xFF1E3A8A, 0x66F97316, false),
            new AerialShot("Tokyo Nightscape", "Shinjuku & Shibuya Neon Lights", 0xFF05050A, 0xFF0D0D1A, 0xFFA855F7, 0xFF0B0F19, 0x88EC4899, true),
            new AerialShot("Reykjavik Fjords", "Aurora Borealis over Icelandic Coast", 0xFF020617, 0xFF064E3B, 0xFF10B981, 0xFF022C22, 0x9934D399, true),
            new AerialShot("Dubai Skyline", "Downtown & Burj Marina at Dusk", 0xFF111827, 0xFF31102B, 0xFFF59E0B, 0xFF1F1235, 0x77F59E0B, false),
            new AerialShot("Swiss Alps", "Matterhorn Glacier Dawn Light", 0xFF0B1325, 0xFF1E293B, 0xFFE0E7FF, 0xFF0F172A, 0x7793C5FD, false)
    };

    public interface ShotChangeListener {
        void onShotChanged(AerialShot shot);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(42);
    private int currentShotIndex = 0;
    private int nextShotIndex = 1;
    private float transitionAlpha = 0f; // 0 = current, 1 = next
    private float driftProgress = 0f;
    private ValueAnimator driftAnimator;
    private ValueAnimator crossFadeAnimator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ShotChangeListener shotChangeListener;

    public AerialSceneView(Context context) {
        super(context);
        init();
    }

    public AerialSceneView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        startDriftAnimation();
    }

    public void setShotChangeListener(ShotChangeListener listener) {
        this.shotChangeListener = listener;
        if (listener != null) {
            listener.onShotChanged(SHOTS[currentShotIndex]);
        }
    }

    public AerialShot getCurrentShot() {
        return SHOTS[currentShotIndex];
    }

    private void startDriftAnimation() {
        if (driftAnimator != null) driftAnimator.cancel();

        driftAnimator = ValueAnimator.ofFloat(0f, 1f);
        driftAnimator.setDuration(22000);
        driftAnimator.setInterpolator(new LinearInterpolator());
        driftAnimator.addUpdateListener(anim -> {
            driftProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        driftAnimator.start();

        // Schedule next transition in 20s
        handler.removeCallbacks(nextShotRunnable);
        handler.postDelayed(nextShotRunnable, 20000);
    }

    private final Runnable nextShotRunnable = new Runnable() {
        @Override
        public void run() {
            nextShotIndex = (currentShotIndex + 1) % SHOTS.length;
            if (crossFadeAnimator != null) crossFadeAnimator.cancel();

            crossFadeAnimator = ValueAnimator.ofFloat(0f, 1f);
            crossFadeAnimator.setDuration(2500);
            crossFadeAnimator.addUpdateListener(anim -> {
                transitionAlpha = (float) anim.getAnimatedValue();
                invalidate();
            });
            crossFadeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    currentShotIndex = nextShotIndex;
                    transitionAlpha = 0f;
                    if (shotChangeListener != null) {
                        shotChangeListener.onShotChanged(SHOTS[currentShotIndex]);
                    }
                    startDriftAnimation();
                }
            });
            crossFadeAnimator.start();
        }
    };

    public void stop() {
        handler.removeCallbacks(nextShotRunnable);
        if (driftAnimator != null) driftAnimator.cancel();
        if (crossFadeAnimator != null) crossFadeAnimator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Draw current shot
        drawShot(canvas, SHOTS[currentShotIndex], w, h, 1f - transitionAlpha, driftProgress);

        // Cross-fade next shot
        if (transitionAlpha > 0f) {
            drawShot(canvas, SHOTS[nextShotIndex], w, h, transitionAlpha, driftProgress * 0.5f);
        }
    }

    private void drawShot(Canvas canvas, AerialShot shot, int w, int h, float alpha, float progress) {
        if (alpha <= 0.001f) return;

        canvas.save();
        // Ken Burns subtle zoom and pan
        float scale = 1.0f + (progress * 0.12f);
        float panX = progress * (w * 0.04f);
        float panY = progress * (h * 0.02f);
        canvas.translate(-panX, -panY);
        canvas.scale(scale, scale, w / 2f, h / 2f);

        // 1. Sky Gradient
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha((int) (255 * alpha));
        paint.setShader(new LinearGradient(0, 0, 0, h * 0.65f, shot.skyTop, shot.skyBottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h * 0.65f, paint);

        // 2. Horizon Glow (Radial Gradient)
        paint.setShader(new RadialGradient(w * 0.5f, h * 0.55f, w * 0.7f,
                shot.glowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        // 3. Terrain / City Floor Gradient
        paint.setShader(new LinearGradient(0, h * 0.52f, 0, h, shot.horizonColor, shot.terrainColor, Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * 0.52f, w, h, paint);

        // 4. Night Lights / Topography Stars
        paint.setShader(null);
        if (shot.isNight) {
            paint.setColor(Color.WHITE);
            random.setSeed(shot.title.hashCode());
            int pointCount = 120;
            for (int i = 0; i < pointCount; i++) {
                float px = random.nextFloat() * w;
                float py = (h * 0.55f) + (random.nextFloat() * (h * 0.45f));
                float r = 1.5f + (random.nextFloat() * 2.5f);
                int pAlpha = (int) ((120 + random.nextInt(135)) * alpha);
                paint.setAlpha(pAlpha);
                if (random.nextBoolean()) {
                    paint.setColor(shot.horizonColor);
                } else {
                    paint.setColor(Color.WHITE);
                }
                canvas.drawCircle(px, py, r, paint);
            }
        }

        // 5. Cinematic Vignette
        paint.setShader(new RadialGradient(w * 0.5f, h * 0.5f, Math.max(w, h) * 0.75f,
                Color.TRANSPARENT, 0xCC000000, Shader.TileMode.CLAMP));
        paint.setAlpha((int) (220 * alpha));
        canvas.drawRect(0, 0, w, h, paint);

        canvas.restore();
    }
}
