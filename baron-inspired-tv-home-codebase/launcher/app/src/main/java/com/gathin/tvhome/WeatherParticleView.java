package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Ambient Live Glance: subtle, hardware-accelerated drifting particle overlay
 * matching current local weather conditions (Sun motes / Rain streaks / Snow crystals / Clouds).
 *
 * Engineered specifically for Android TV GPUs:
 * - Allocates 0 memory per frame in onDraw()
 * - Reusable primitive float arrays
 * - Pauses rendering automatically when paused or detached (0% CPU/GPU when apps are open)
 * - Non-interactive (takes no focus, touch, or accessibility)
 * - Subtle ambient opacities so UI text and dock tiles remain crystal clear.
 */
public final class WeatherParticleView extends View {

    public static final int MODE_NONE = 0;
    public static final int MODE_SUN = 1;
    public static final int MODE_RAIN = 2;
    public static final int MODE_SNOW = 3;
    public static final int MODE_CLOUDS = 4;
    public static final int MODE_THUNDER = 5;

    private static final int MAX_PARTICLES = 45;
    private static final double TAU = 2 * Math.PI;

    private final float density;
    private final Random rnd = new Random();

    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightningPaint = new Paint();

    // Particle primitive arrays to avoid GC pressure
    private final float[] px = new float[MAX_PARTICLES];
    private final float[] py = new float[MAX_PARTICLES];
    private final float[] vx = new float[MAX_PARTICLES];
    private final float[] vy = new float[MAX_PARTICLES];
    private final float[] radius = new float[MAX_PARTICLES];
    private final float[] baseAlpha = new float[MAX_PARTICLES];
    private final float[] swayAmp = new float[MAX_PARTICLES];
    private final float[] swayFreq = new float[MAX_PARTICLES];
    private final float[] swayPhase = new float[MAX_PARTICLES];
    private final int[] colors = new int[MAX_PARTICLES];

    private int activeCount = 0;
    private int currentMode = MODE_NONE;
    private boolean playing = false;
    private long lastFrameTime = 0;

    // Thunder/lightning state
    private long lastLightningTime = 0;
    private long nextLightningDelay = 12000;
    private float lightningAlpha = 0f;

    public WeatherParticleView(Context context) {
        this(context, null);
    }

    public WeatherParticleView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeatherParticleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;

        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);

        lightningPaint.setColor(Color.WHITE);
        lightningPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * Sets the active particle weather mode (MODE_SUN, MODE_RAIN, MODE_SNOW, etc.)
     */
    public void setMode(int mode) {
        if (currentMode == mode) return;
        currentMode = mode;
        initParticlesForMode(mode);
        if (playing && mode != MODE_NONE) {
            lastFrameTime = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        } else {
            invalidate();
        }
    }

    public int getMode() {
        return currentMode;
    }

    /**
     * Starts particle animation loop. Call in onResume().
     */
    public void start() {
        if (playing) return;
        playing = true;
        lastFrameTime = SystemClock.uptimeMillis();
        lastLightningTime = lastFrameTime;
        postInvalidateOnAnimation();
    }

    /**
     * Stops particle animation loop to save TV battery/CPU/GPU. Call in onPause().
     */
    public void stop() {
        playing = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (currentMode != MODE_NONE) {
            start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            scatterParticles(w, h);
        }
    }

    private void scatterParticles(int w, int h) {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            px[i] = rnd.nextFloat() * w;
            py[i] = rnd.nextFloat() * h;
        }
    }

    private void initParticlesForMode(int mode) {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());

        switch (mode) {
            case MODE_SUN:
                // Gentle floating golden sun motes / sunbeams drifting upwards
                activeCount = 28;
                particlePaint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < activeCount; i++) {
                    px[i] = rnd.nextFloat() * w;
                    py[i] = rnd.nextFloat() * h;
                    vx[i] = (rnd.nextFloat() - 0.5f) * 12f * density;
                    vy[i] = -(14f + rnd.nextFloat() * 18f) * density; // drift up
                    radius[i] = (2.5f + rnd.nextFloat() * 4.5f) * density;
                    baseAlpha[i] = 0.25f + rnd.nextFloat() * 0.35f;
                    swayAmp[i] = (15f + rnd.nextFloat() * 25f) * density;
                    swayFreq[i] = 0.3f + rnd.nextFloat() * 0.4f;
                    swayPhase[i] = rnd.nextFloat() * (float) TAU;
                    // Warm golden amber tones
                    int[] sunTones = {0xFFFFE082, 0xFFFFD54F, 0xFFFFCA28, 0xFFFFF9C4};
                    colors[i] = sunTones[rnd.nextInt(sunTones.length)];
                }
                break;

            case MODE_RAIN:
            case MODE_THUNDER:
                // Fine translucent rain streaks
                activeCount = mode == MODE_THUNDER ? 44 : 36;
                particlePaint.setStyle(Paint.Style.STROKE);
                particlePaint.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < activeCount; i++) {
                    px[i] = rnd.nextFloat() * w;
                    py[i] = rnd.nextFloat() * h;
                    vx[i] = (45f + rnd.nextFloat() * 35f) * density; // slanting with wind
                    vy[i] = (340f + rnd.nextFloat() * 180f) * density; // falling down fast
                    radius[i] = (14f + rnd.nextFloat() * 12f) * density; // length of streak
                    baseAlpha[i] = 0.20f + rnd.nextFloat() * 0.30f;
                    swayAmp[i] = 0f;
                    swayFreq[i] = 0f;
                    swayPhase[i] = 0f;
                    // Translucent blue-silver rain tints
                    int[] rainTones = {0xFFB0BEC5, 0xFF90CAF9, 0xFF81D4FA, 0xFFB3E5FC};
                    colors[i] = rainTones[rnd.nextInt(rainTones.length)];
                }
                break;

            case MODE_SNOW:
                // Soft white crystalline snowflakes drifting and swaying
                activeCount = 34;
                particlePaint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < activeCount; i++) {
                    px[i] = rnd.nextFloat() * w;
                    py[i] = rnd.nextFloat() * h;
                    vx[i] = (rnd.nextFloat() - 0.5f) * 10f * density;
                    vy[i] = (25f + rnd.nextFloat() * 35f) * density; // falling slowly
                    radius[i] = (2.2f + rnd.nextFloat() * 3.8f) * density;
                    baseAlpha[i] = 0.35f + rnd.nextFloat() * 0.45f;
                    swayAmp[i] = (18f + rnd.nextFloat() * 30f) * density;
                    swayFreq[i] = 0.4f + rnd.nextFloat() * 0.5f;
                    swayPhase[i] = rnd.nextFloat() * (float) TAU;
                    // Pure white and soft ice blue
                    int[] snowTones = {0xFFFFFFFF, 0xFFF0F8FF, 0xFFE0F7FA, 0xFFE1F5FE};
                    colors[i] = snowTones[rnd.nextInt(snowTones.length)];
                }
                break;

            case MODE_CLOUDS:
                // Soft atmospheric mist/cloud wisps
                activeCount = 18;
                particlePaint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < activeCount; i++) {
                    px[i] = rnd.nextFloat() * w;
                    py[i] = rnd.nextFloat() * h;
                    vx[i] = (8f + rnd.nextFloat() * 14f) * density; // slow horizontal glide
                    vy[i] = (rnd.nextFloat() - 0.5f) * 4f * density;
                    radius[i] = (22f + rnd.nextFloat() * 26f) * density; // large diffuse motes
                    baseAlpha[i] = 0.08f + rnd.nextFloat() * 0.12f;
                    swayAmp[i] = (10f + rnd.nextFloat() * 15f) * density;
                    swayFreq[i] = 0.2f + rnd.nextFloat() * 0.2f;
                    swayPhase[i] = rnd.nextFloat() * (float) TAU;
                    colors[i] = 0xFFECEFF1;
                }
                break;

            case MODE_NONE:
            default:
                activeCount = 0;
                break;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!playing || currentMode == MODE_NONE || activeCount == 0) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        float dt = (now - lastFrameTime) / 1000f;
        if (dt <= 0f || dt > 0.15f) dt = 0.016f; // clamp delta time for stability
        lastFrameTime = now;

        // Subtle ambient lightning sheet flash for thunderstorm
        if (currentMode == MODE_THUNDER) {
            if (now - lastLightningTime > nextLightningDelay) {
                lastLightningTime = now;
                nextLightningDelay = 9000 + rnd.nextInt(11000);
                lightningAlpha = 0.18f; // delicate flash
            }
            if (lightningAlpha > 0.005f) {
                lightningPaint.setAlpha((int) (255 * lightningAlpha));
                canvas.drawRect(0, 0, w, h, lightningPaint);
                lightningAlpha *= 0.82f; // quick decay
            }
        }

        // Draw active weather particles
        for (int i = 0; i < activeCount; i++) {
            // Update physics
            py[i] += vy[i] * dt;
            px[i] += vx[i] * dt;

            // Sway calculation
            float curX = px[i];
            if (swayAmp[i] > 0) {
                curX += swayAmp[i] * (float) Math.sin(swayPhase[i] + now * 0.0018f * swayFreq[i]);
            }

            // Screen boundary wrapping
            if (currentMode == MODE_SUN) {
                if (py[i] < -radius[i] * 2) {
                    py[i] = h + radius[i];
                    px[i] = rnd.nextFloat() * w;
                }
            } else if (currentMode == MODE_RAIN || currentMode == MODE_THUNDER) {
                if (py[i] > h + radius[i]) {
                    py[i] = -radius[i] * 2;
                    px[i] = rnd.nextFloat() * w;
                }
                if (curX > w + 20) curX = -20;
            } else if (currentMode == MODE_SNOW) {
                if (py[i] > h + radius[i]) {
                    py[i] = -radius[i];
                    px[i] = rnd.nextFloat() * w;
                }
                if (curX > w + 20) curX = -20;
                else if (curX < -20) curX = w + 20;
            } else if (currentMode == MODE_CLOUDS) {
                if (px[i] > w + radius[i] * 2) {
                    px[i] = -radius[i] * 2;
                    py[i] = rnd.nextFloat() * h;
                }
            }

            // Dynamic shimmer alpha for sun/snow
            float a = baseAlpha[i];
            if (currentMode == MODE_SUN) {
                a *= (0.65f + 0.35f * (float) Math.sin(swayPhase[i] + now * 0.0028f));
            } else if (currentMode == MODE_SNOW) {
                a *= (0.75f + 0.25f * (float) Math.sin(swayPhase[i] + now * 0.0022f));
            }

            particlePaint.setColor(colors[i]);
            particlePaint.setAlpha(Math.max(0, Math.min(255, (int) (255 * a))));

            if (currentMode == MODE_RAIN || currentMode == MODE_THUNDER) {
                // Draw falling rain streak
                particlePaint.setStrokeWidth(1.4f * density);
                float endX = curX - (vx[i] * 0.045f);
                float endY = py[i] - (radius[i]);
                canvas.drawLine(curX, py[i], endX, endY, particlePaint);
            } else {
                // Draw circular soft mote / snowflake / cloud
                canvas.drawCircle(curX, py[i], radius[i], particlePaint);
            }
        }

        postInvalidateOnAnimation();
    }
}
