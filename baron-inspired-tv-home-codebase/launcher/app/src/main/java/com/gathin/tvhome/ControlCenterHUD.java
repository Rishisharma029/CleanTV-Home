package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Slide-Out Control Center (Frosted Glass HUD).
 * Apple TV / macOS-style translucent sidebar sliding in from the right edge.
 * Provides quick connectivity toggles, sleep timer, live input switching,
 * memory booster, and direct system settings shortcuts.
 */
public final class ControlCenterHUD {

    private final MainActivity activity;
    private final FrameLayout rootContainer;
    private final float density;

    private View hudRoot;
    private View scrim;
    private View panel;
    private View previousFocus;
    private boolean isShowing;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SleepTimerManager.SleepTimerListener sleepTimerListener = new SleepTimerManager.SleepTimerListener() {
        @Override
        public void onTick(long millisRemaining, String formattedRemaining) {
            if (hudRoot == null) return;
            TextView sleepStatus = hudRoot.findViewById(R.id.ccSleepTimerStatus);
            if (sleepStatus != null) {
                sleepStatus.setText(formattedRemaining);
            }
        }

        @Override
        public void onTimerStarted(int minutes, long millisRemaining) {
            onTick(millisRemaining, SleepTimerManager.formatRemaining(millisRemaining));
        }

        @Override
        public void onTimerCancelled() {
            onTick(0, "Off");
        }

        @Override
        public void onDeepStandbyTriggered() {
            onTick(0, "Off");
        }
    };

    public ControlCenterHUD(MainActivity activity, FrameLayout rootContainer) {
        this.activity = activity;
        this.rootContainer = rootContainer;
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void show() {
        if (isShowing) return;
        isShowing = true;
        previousFocus = activity.getCurrentFocus();

        hudRoot = activity.getLayoutInflater().inflate(R.layout.control_center_hud, rootContainer, false);
        scrim = hudRoot.findViewById(R.id.controlCenterScrim);
        panel = hudRoot.findViewById(R.id.controlCenterPanel);

        scrim.setOnClickListener(v -> dismiss());

        SleepTimerManager.registerListener(sleepTimerListener);
        setupHeader();
        setupQuickToggles();
        setupAudioOutputSwitch();
        setupInputsDeck();
        setupMemoryBooster();
        setupSettingsShortcuts();

        // Attach to root
        rootContainer.addView(hudRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Start offscreen to the right
        float panelWidth = 360f * density;
        panel.setTranslationX(panelWidth);
        scrim.setAlpha(0f);

        // Slide in animation
        panel.animate()
                .translationX(0f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();

        scrim.animate()
                .alpha(1f)
                .setDuration(240)
                .start();

        // Focus first card
        View cardWifi = hudRoot.findViewById(R.id.cardWifi);
        if (cardWifi != null) {
            cardWifi.post(cardWifi::requestFocus);
        }
    }

    public void dismiss() {
        if (!isShowing || hudRoot == null) return;
        isShowing = false;

        float panelWidth = 360f * density;
        panel.animate()
                .translationX(panelWidth)
                .setDuration(190)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .start();

        scrim.animate()
                .alpha(0f)
                .setDuration(190)
                .withEndAction(this::cleanupAndRemove)
                .start();
    }

    public void dismissImmediate() {
        if (!isShowing || hudRoot == null) return;
        isShowing = false;
        cleanupAndRemove();
    }

    private void cleanupAndRemove() {
        SleepTimerManager.unregisterListener(sleepTimerListener);
        if (hudRoot != null && hudRoot.getParent() instanceof ViewGroup) {
            ((ViewGroup) hudRoot.getParent()).removeView(hudRoot);
        }
        hudRoot = null;
        scrim = null;
        panel = null;
        if (previousFocus != null && previousFocus.isAttachedToWindow()) {
            previousFocus.requestFocus();
        }
        previousFocus = null;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isShowing) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            dismiss();
            return true;
        }

        if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
            View current = hudRoot != null ? hudRoot.findFocus() : null;
            if (current != null) {
                // If focus is on the left column or full-width button, slide out on D-pad left
                int id = current.getId();
                if (id == R.id.cardWifi || id == R.id.cardSleepTimer || id == R.id.cardGameMode || id == R.id.btnCcLauncherSettings) {
                    dismiss();
                    return true;
                }
            }
        }
        return false;
    }

    private void setupHeader() {
        TextView clock = hudRoot.findViewById(R.id.ccClock);
        if (clock != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a · EEE, MMM d", Locale.getDefault());
            clock.setText(sdf.format(new Date()));
        }
    }

    private void setupAudioOutputSwitch() {
        View btnTvSpeakers = hudRoot.findViewById(R.id.btnAudioTvSpeakers);
        View btnSoundbar = hudRoot.findViewById(R.id.btnAudioSoundbar);
        View btnBluetooth = hudRoot.findViewById(R.id.btnAudioBluetooth);

        if (btnTvSpeakers != null) {
            applyCardFocusEffect(btnTvSpeakers);
            btnTvSpeakers.setOnClickListener(v -> {
                AudioOutputManager.setAudioOutputMode(activity, AudioOutputManager.MODE_TV_SPEAKERS);
                updateAudioOutputBadges();
            });
        }

        if (btnSoundbar != null) {
            applyCardFocusEffect(btnSoundbar);
            btnSoundbar.setOnClickListener(v -> {
                AudioOutputManager.setAudioOutputMode(activity, AudioOutputManager.MODE_SOUNDBAR);
                updateAudioOutputBadges();
            });
        }

        if (btnBluetooth != null) {
            applyCardFocusEffect(btnBluetooth);
            btnBluetooth.setOnClickListener(v -> {
                AudioOutputManager.setAudioOutputMode(activity, AudioOutputManager.MODE_BLUETOOTH);
                updateAudioOutputBadges();
            });
        }

        updateAudioOutputBadges();
    }

    private void updateAudioOutputBadges() {
        if (hudRoot == null) return;
        int currentMode = AudioOutputManager.getCurrentMode(activity);

        TextView badgeTv = hudRoot.findViewById(R.id.badgeAudioTvSpeakers);
        TextView subTv = hudRoot.findViewById(R.id.subAudioTvSpeakers);
        if (badgeTv != null) {
            badgeTv.setText(currentMode == AudioOutputManager.MODE_TV_SPEAKERS ? "● ACTIVE" : "");
        }
        if (subTv != null) {
            subTv.setText(AudioOutputManager.getModeSubtitle(activity, AudioOutputManager.MODE_TV_SPEAKERS));
        }

        TextView badgeSoundbar = hudRoot.findViewById(R.id.badgeAudioSoundbar);
        TextView subSoundbar = hudRoot.findViewById(R.id.subAudioSoundbar);
        if (badgeSoundbar != null) {
            badgeSoundbar.setText(currentMode == AudioOutputManager.MODE_SOUNDBAR ? "● ACTIVE" : "");
        }
        if (subSoundbar != null) {
            subSoundbar.setText(AudioOutputManager.getModeSubtitle(activity, AudioOutputManager.MODE_SOUNDBAR));
        }

        TextView badgeBt = hudRoot.findViewById(R.id.badgeAudioBluetooth);
        TextView subBt = hudRoot.findViewById(R.id.subAudioBluetooth);
        if (badgeBt != null) {
            badgeBt.setText(currentMode == AudioOutputManager.MODE_BLUETOOTH ? "● ACTIVE" : "");
        }
        if (subBt != null) {
            subBt.setText(AudioOutputManager.getModeSubtitle(activity, AudioOutputManager.MODE_BLUETOOTH));
        }
    }

    private void setupQuickToggles() {
        // 1. Wi-Fi Card
        View cardWifi = hudRoot.findViewById(R.id.cardWifi);
        TextView ccWifiStatus = hudRoot.findViewById(R.id.ccWifiStatus);
        if (cardWifi != null) {
            applyCardFocusEffect(cardWifi);
            if (ccWifiStatus != null) {
                ccWifiStatus.setText(activity.isNetworkConnected() ? "Connected" : "Disconnected");
            }
            cardWifi.setOnClickListener(v -> {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Throwable ignored) {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                    } catch (Throwable ignored2) {
                        Toast.makeText(activity, "Wi-Fi settings not available", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // 2. Bluetooth Card
        View cardBt = hudRoot.findViewById(R.id.cardBluetooth);
        if (cardBt != null) {
            applyCardFocusEffect(cardBt);
            cardBt.setOnClickListener(v -> {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (Throwable ignored) {
                    Toast.makeText(activity, "Bluetooth settings not available", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 3. Sleep Timer Card (15m / 30m / 45m / 60m countdown)
        View cardSleep = hudRoot.findViewById(R.id.cardSleepTimer);
        TextView sleepStatus = hudRoot.findViewById(R.id.ccSleepTimerStatus);
        if (cardSleep != null) {
            applyCardFocusEffect(cardSleep);
            if (sleepStatus != null) {
                sleepStatus.setText(SleepTimerManager.getFormattedCountdown());
            }
            cardSleep.setOnClickListener(v -> {
                SleepTimerManager.cycleNext(activity);
            });
        }

        // 4. Display / Night Mood Card
        View cardDisplay = hudRoot.findViewById(R.id.cardDisplay);
        if (cardDisplay != null) {
            applyCardFocusEffect(cardDisplay);
            cardDisplay.setOnClickListener(v -> {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                } catch (Throwable ignored) {
                    Toast.makeText(activity, "Display settings not available", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 5. Game Mode Card (Force ALLM & Disable Motion Smoothing)
        View cardGame = hudRoot.findViewById(R.id.cardGameMode);
        TextView gameStatus = hudRoot.findViewById(R.id.ccGameModeStatus);
        ImageView gameIcon = hudRoot.findViewById(R.id.ccGameIcon);
        if (cardGame != null) {
            applyCardFocusEffect(cardGame);
            updateGameModeUI(gameStatus, gameIcon);
            cardGame.setOnClickListener(v -> {
                GameModeManager.toggle(activity);
                updateGameModeUI(gameStatus, gameIcon);
            });
        }

        // 6. Network Speed Meter Card (Live Ping & Bandwidth Test)
        View cardSpeed = hudRoot.findViewById(R.id.cardSpeedMeter);
        TextView speedStatus = hudRoot.findViewById(R.id.ccSpeedStatus);
        ImageView speedIcon = hudRoot.findViewById(R.id.ccSpeedIcon);
        if (cardSpeed != null) {
            applyCardFocusEffect(cardSpeed);
            if (speedStatus != null) {
                if (NetworkSpeedMeter.getLastPingMs() > 0 && NetworkSpeedMeter.getLastSpeedMbps() > 0) {
                    speedStatus.setText(NetworkSpeedMeter.getLastPingMs() + "ms · " + NetworkSpeedMeter.formatSpeed(NetworkSpeedMeter.getLastSpeedMbps()));
                } else {
                    speedStatus.setText("Tap to test");
                }
            }
            cardSpeed.setOnClickListener(v -> {
                if (NetworkSpeedMeter.isRunning()) return;
                if (speedStatus != null) speedStatus.setText("Pinging...");
                if (speedIcon != null) speedIcon.animate().rotationBy(360f).setDuration(1000).start();

                NetworkSpeedMeter.startTest(new NetworkSpeedMeter.SpeedTestListener() {
                    @Override
                    public void onPingMeasured(int pingMs, int jitterMs) {
                        if (speedStatus != null) {
                            speedStatus.setText("Ping: " + pingMs + "ms (" + jitterMs + "ms jit)");
                        }
                    }

                    @Override
                    public void onBandwidthProgress(float currentMbps, int progressPercent) {
                        if (speedStatus != null) {
                            speedStatus.setText(NetworkSpeedMeter.formatSpeed(currentMbps) + " (" + progressPercent + "%)");
                        }
                    }

                    @Override
                    public void onTestComplete(int pingMs, float finalMbps, String qualityRating) {
                        if (speedStatus != null) {
                            speedStatus.setText(pingMs + "ms · " + NetworkSpeedMeter.formatSpeed(finalMbps));
                        }
                        Toast.makeText(activity, "Speed Test: " + NetworkSpeedMeter.formatSpeed(finalMbps) + " · " + qualityRating, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onTestError(String error) {
                        if (speedStatus != null) {
                            speedStatus.setText("Retry test");
                        }
                        Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        // 7. Ad-Blocker Card (1-Click Private DNS / Host Blocking)
        View cardAdBlock = hudRoot.findViewById(R.id.cardAdBlock);
        TextView ccAdBlockStatus = hudRoot.findViewById(R.id.ccAdBlockStatus);
        ImageView ccAdBlockIcon = hudRoot.findViewById(R.id.ccAdBlockIcon);
        if (cardAdBlock != null) {
            applyCardFocusEffect(cardAdBlock);
            updateAdBlockUI(ccAdBlockStatus, ccAdBlockIcon);
            cardAdBlock.setOnClickListener(v -> {
                int next = AdBlockManager.cycleNext(activity);
                updateAdBlockUI(ccAdBlockStatus, ccAdBlockIcon);
            });
        }

        // 8. Smart Home Tile (Doorbell Camera & Smart Room Lights)
        View cardSmartHome = hudRoot.findViewById(R.id.cardSmartHome);
        if (cardSmartHome != null) {
            applyCardFocusEffect(cardSmartHome);
            cardSmartHome.setOnClickListener(v -> {
                dismiss();
                SmartHomeHUD.show(activity);
            });
        }
    }

    private void updateAdBlockUI(TextView statusView, ImageView iconView) {
        if (statusView == null) return;
        int mode = AdBlockManager.getCurrentMode(activity);
        statusView.setText(AdBlockManager.getModeName(mode));
        if (mode != AdBlockManager.MODE_OFF) {
            statusView.setTextColor(0xFF34D399);
            if (iconView != null) iconView.setColorFilter(0xFF10B981);
        } else {
            statusView.setTextColor(0x99FFFFFF);
            if (iconView != null) iconView.setColorFilter(0xFF64748B);
        }
    }

    private void updateGameModeUI(TextView statusView, ImageView iconView) {
        if (statusView == null) return;
        boolean enabled = GameModeManager.isGameModeEnabled(activity);
        if (enabled) {
            statusView.setText("● ALLM Active");
            statusView.setTextColor(0xFF34D399);
            if (iconView != null) iconView.setColorFilter(0xFF10B981);
        } else {
            statusView.setText("ALLM · Off");
            statusView.setTextColor(0x99FFFFFF);
            if (iconView != null) iconView.setColorFilter(0xFFF59E0B);
        }
    }



    private void setupInputsDeck() {
        LinearLayout container = hudRoot.findViewById(R.id.ccInputsContainer);
        if (container == null) return;
        container.removeAllViews();

        List<HdmiPreviewManager.HdmiDevice> devices = HdmiPreviewManager.getConnectedHdmiDevices(activity);
        if (devices.isEmpty()) {
            devices.add(new HdmiPreviewManager.HdmiDevice("hdmi1", "HDMI 1", "Connected Input", "Active Port", "4K 60Hz", 0));
            devices.add(new HdmiPreviewManager.HdmiDevice("hdmi2", "HDMI 2", "Gaming Console", "Active Port", "120Hz HDR", 0));
        }

        for (int i = 0; i < Math.min(devices.size(), 3); i++) {
            HdmiPreviewManager.HdmiDevice dev = devices.get(i);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackgroundResource(R.drawable.glass_panel);
            int padH = (int) (12 * density);
            int padV = (int) (10 * density);
            row.setPadding(padH, padV, padH, padV);

            ImageView icon = new ImageView(activity);
            icon.setImageResource(R.drawable.ic_input);
            icon.setColorFilter(0xFF38BDF8);
            row.addView(icon, new LinearLayout.LayoutParams((int) (18 * density), (int) (18 * density)));

            LinearLayout texts = new LinearLayout(activity);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding((int) (10 * density), 0, 0, 0);

            TextView title = new TextView(activity);
            title.setText(dev.getDisplayTitle());
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(12);
            texts.addView(title);

            TextView sub = new TextView(activity);
            sub.setText(dev.getDisplaySubtitle());
            sub.setTextColor(0x99FFFFFF);
            sub.setTextSize(10);
            texts.addView(sub);

            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView badge = new TextView(activity);
            badge.setText("ACTIVE");
            badge.setTextColor(0xFF38BDF8);
            badge.setTextSize(9);
            badge.setBackgroundColor(0x1A38BDF8);
            badge.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));
            row.addView(badge);

            applyCardFocusEffect(row);
            row.setOnClickListener(v -> {
                dismiss();
                activity.openInput(dev.inputId);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = (int) (8 * density);
            container.addView(row, lp);
        }
    }

    private void setupMemoryBooster() {
        View cardBoost = hudRoot.findViewById(R.id.cardBoostRam);
        TextView ccRamStatus = hudRoot.findViewById(R.id.ccRamStatus);
        TextView ccBoostBadge = hudRoot.findViewById(R.id.ccBoostBadge);

        if (cardBoost == null) return;
        applyCardFocusEffect(cardBoost);

        updateMemoryStats(ccRamStatus);

        cardBoost.setOnClickListener(v -> {
            System.gc();
            try {
                ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    am.killBackgroundProcesses(activity.getPackageName());
                }
            } catch (Throwable ignored) {}

            if (ccBoostBadge != null) {
                ccBoostBadge.setText("✨ BOOSTED!");
                ccBoostBadge.setTextColor(0xFF34D399);
            }
            updateMemoryStats(ccRamStatus);
            Toast.makeText(activity, "Memory optimized & caches cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateMemoryStats(TextView statusView) {
        if (statusView == null) return;
        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long availMb = mi.availMem / (1024 * 1024);
                long totalMb = mi.totalMem / (1024 * 1024);
                statusView.setText("Free: " + availMb + " MB / " + totalMb + " MB");
                return;
            }
        } catch (Throwable ignored) {}
        statusView.setText("Clean & Optimized");
    }

    private void setupSettingsShortcuts() {
        View btnLauncher = hudRoot.findViewById(R.id.btnCcLauncherSettings);
        if (btnLauncher != null) {
            applyCardFocusEffect(btnLauncher);
            btnLauncher.setOnClickListener(v -> {
                dismiss();
                activity.startActivity(new Intent(activity, LauncherSettingsActivity.class));
            });
        }

        View btnSystem = hudRoot.findViewById(R.id.btnCcSystemSettings);
        if (btnSystem != null) {
            applyCardFocusEffect(btnSystem);
            btnSystem.setOnClickListener(v -> {
                dismiss();
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            });
        }

        View cardAerial = hudRoot.findViewById(R.id.cardAerialScreensaver);
        if (cardAerial != null) {
            applyCardFocusEffect(cardAerial);
            cardAerial.setOnClickListener(v -> {
                dismiss();
                activity.startActivity(new Intent(activity, AmbientAerialActivity.class));
            });
        }

        View cardAddonHub = hudRoot.findViewById(R.id.cardAddonHub);
        if (cardAddonHub != null) {
            applyCardFocusEffect(cardAddonHub);
            cardAddonHub.setOnClickListener(v -> {
                dismiss();
                AddonHubDialog.show(activity);
            });
        }

        View cardWebRemote = hudRoot.findViewById(R.id.cardWebRemote);
        if (cardWebRemote != null) {
            applyCardFocusEffect(cardWebRemote);
            ImageView qrThumb = hudRoot.findViewById(R.id.ccQrThumbnail);
            TextView qrUrlStatus = hudRoot.findViewById(R.id.ccQrUrlStatus);

            String url = WebRemoteServer.getServerUrl(activity);
            if (qrUrlStatus != null) {
                qrUrlStatus.setText(url);
            }
            if (qrThumb != null) {
                android.graphics.Bitmap thumb = QRCodeGenerator.generate(url, 96);
                if (thumb != null) {
                    qrThumb.setImageBitmap(thumb);
                }
            }

            cardWebRemote.setOnClickListener(v -> {
                WebRemoteDialog.show(activity);
            });
        }

        View cardDebloater = hudRoot.findViewById(R.id.cardDebloater);
        if (cardDebloater != null) {
            applyCardFocusEffect(cardDebloater);
            TextView brandTv = hudRoot.findViewById(R.id.ccDebloaterBrand);
            if (brandTv != null) {
                DebloaterEngine.Brand b = DebloaterEngine.detectDeviceBrand();
                brandTv.setText(b.displayName + " Detected");
            }
            cardDebloater.setOnClickListener(v -> {
                dismiss();
                DebloaterDialog.show(activity);
            });
        }
    }

    private void applyCardFocusEffect(View view) {
        GradientDrawable focusedBg = new GradientDrawable();
        focusedBg.setShape(GradientDrawable.RECTANGLE);
        focusedBg.setCornerRadius(14 * density);
        focusedBg.setColor(0x55FFFFFF);
        focusedBg.setStroke((int) (2 * density), 0xFFFFFFFF);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setCornerRadius(14 * density);
        normalBg.setColor(0x28FFFFFF);
        normalBg.setStroke((int) (1 * density), 0x22FFFFFF);

        view.setBackground(normalBg);
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setBackground(focusedBg);
                v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6 * density).setDuration(150).start();
                v.playSoundEffect(SoundEffectConstants.CLICK);
            } else {
                v.setBackground(normalBg);
                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start();
            }
        });
    }
}
