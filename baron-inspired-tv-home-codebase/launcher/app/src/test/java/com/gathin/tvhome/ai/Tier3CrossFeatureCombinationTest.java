package com.gathin.tvhome.ai;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tier 3: Cross-Feature Combinations Test Suite.
 * Verifies pairwise interactions, sequences, and state combinations between major CleanTV AI features:
 * Media playback, Volume, App Launching, Settings Navigation, Launcher Features, and Fallback.
 */
public class Tier3CrossFeatureCombinationTest {

    private Map<String, String> appMap;

    @Before
    public void setUp() {
        appMap = AiDriver.standardTvApps();
    }

    // ==========================================
    // Combination 1: Volume Control + Media Playback
    // ==========================================
    @Test
    public void combo_mediaPlayThenVolumeAdjust() {
        AiDriver.AiIntentResult play = AiDriver.classify("play", appMap);
        assertEquals("MEDIA_CONTROL", play.type);
        assertEquals("play", play.action);

        AiDriver.AiIntentResult volUp = AiDriver.classify("volume up", appMap);
        assertEquals("VOLUME_CONTROL", volUp.type);
        assertEquals(Integer.valueOf(1), volUp.getParamInt("direction"));

        AiDriver.AiIntentResult pause = AiDriver.classify("pause", appMap);
        assertEquals("MEDIA_CONTROL", pause.type);
        assertEquals("pause", pause.action);
    }

    @Test
    public void combo_mediaSkipThenMute() {
        AiDriver.AiIntentResult skip = AiDriver.classify("skip", appMap);
        assertEquals("MEDIA_CONTROL", skip.type);

        AiDriver.AiIntentResult mute = AiDriver.classify("mute", appMap);
        assertEquals("VOLUME_CONTROL", mute.type);
    }

    // ==========================================
    // Combination 2: App Launch + Home Navigation
    // ==========================================
    @Test
    public void combo_appLaunchThenReturnHome() {
        AiDriver.AiIntentResult openYt = AiDriver.classify("open YouTube", appMap);
        assertEquals("LAUNCH_APP", openYt.type);
        assertEquals("com.google.android.youtube.tv", openYt.getParamString("package"));

        AiDriver.AiIntentResult goHome = AiDriver.classify("go home", appMap);
        assertEquals("SYSTEM_ACTION", goHome.type);
        assertEquals("home", goHome.action);
    }

    @Test
    public void combo_netflixThenMainScreen() {
        AiDriver.AiIntentResult openNf = AiDriver.classify("launch Netflix", appMap);
        assertEquals("LAUNCH_APP", openNf.type);
        assertEquals("com.netflix.ninja", openNf.getParamString("package"));

        AiDriver.AiIntentResult home = AiDriver.classify("return home", appMap);
        assertEquals("SYSTEM_ACTION", home.type);
        assertEquals("home", home.action);
    }

    // ==========================================
    // Combination 3: Settings + App Launch
    // ==========================================
    @Test
    public void combo_wifiSettingsThenAppLaunch() {
        AiDriver.AiIntentResult wifi = AiDriver.classify("open wifi", appMap);
        assertEquals("SYSTEM_ACTION", wifi.type);
        assertEquals("wifi", wifi.action);

        AiDriver.AiIntentResult spotify = AiDriver.classify("open Spotify", appMap);
        assertEquals("LAUNCH_APP", spotify.type);
        assertEquals("com.spotify.tv.android", spotify.getParamString("package"));
    }

    @Test
    public void combo_bluetoothSettingsThenKodiLaunch() {
        AiDriver.AiIntentResult bt = AiDriver.classify("bluetooth settings", appMap);
        assertEquals("SYSTEM_ACTION", bt.type);
        assertEquals("bluetooth", bt.action);

        AiDriver.AiIntentResult kodi = AiDriver.classify("start Kodi", appMap);
        assertEquals("LAUNCH_APP", kodi.type);
        assertEquals("org.xbmc.kodi", kodi.getParamString("package"));
    }

    // ==========================================
    // Combination 4: Sleep Timer + Game Mode
    // ==========================================
    @Test
    public void combo_sleepTimerThenGameMode() {
        AiDriver.AiIntentResult sleep = AiDriver.classify("sleep timer", appMap);
        assertEquals("LAUNCHER_FEATURE", sleep.type);
        assertEquals("sleep_timer", sleep.action);

        AiDriver.AiIntentResult game = AiDriver.classify("game mode", appMap);
        assertEquals("LAUNCHER_FEATURE", game.type);
        assertEquals("game_mode", game.action);
    }

    // ==========================================
    // Combination 5: Volume Mute + Absolute Volume Set
    // ==========================================
    @Test
    public void combo_muteThenSetAbsoluteVolume() {
        AiDriver.AiIntentResult mute = AiDriver.classify("mute", appMap);
        assertEquals("VOLUME_CONTROL", mute.type);

        AiDriver.AiIntentResult setVol = AiDriver.classify("set volume to 40%", appMap);
        assertEquals("VOLUME_CONTROL", setVol.type);
        assertEquals(Integer.valueOf(40), setVol.getParamInt("level"));
    }

    // ==========================================
    // Combination 6: Relative Volume Steps Sequence
    // ==========================================
    @Test
    public void combo_volumeUpUpDowMuteSequence() {
        AiDriver.AiIntentResult up1 = AiDriver.classify("volume up", appMap);
        assertEquals(Integer.valueOf(1), up1.getParamInt("direction"));

        AiDriver.AiIntentResult up2 = AiDriver.classify("louder", appMap);
        assertEquals(Integer.valueOf(1), up2.getParamInt("direction"));

        AiDriver.AiIntentResult down = AiDriver.classify("quieter", appMap);
        assertEquals(Integer.valueOf(-1), down.getParamInt("direction"));

        AiDriver.AiIntentResult mute = AiDriver.classify("silence", appMap);
        assertEquals("VOLUME_CONTROL", mute.type);
    }

    // ==========================================
    // Combination 7: Control Center + Addons Hub
    // ==========================================
    @Test
    public void combo_controlCenterThenAddonHub() {
        AiDriver.AiIntentResult cc = AiDriver.classify("control center", appMap);
        assertEquals("LAUNCHER_FEATURE", cc.type);
        assertEquals("control_center", cc.action);

        AiDriver.AiIntentResult addons = AiDriver.classify("addon hub", appMap);
        assertEquals("LAUNCHER_FEATURE", addons.type);
        assertEquals("addons", addons.action);
    }

    // ==========================================
    // Combination 8: Screensaver + Return Home
    // ==========================================
    @Test
    public void combo_screensaverThenHome() {
        AiDriver.AiIntentResult aerial = AiDriver.classify("screensaver", appMap);
        assertEquals("LAUNCHER_FEATURE", aerial.type);
        assertEquals("screensaver", aerial.action);

        AiDriver.AiIntentResult home = AiDriver.classify("go home", appMap);
        assertEquals("SYSTEM_ACTION", home.type);
        assertEquals("home", home.action);
    }

    // ==========================================
    // Combination 9: Direct App Name + Media Control
    // ==========================================
    @Test
    public void combo_directAppTitleThenMediaControl() {
        AiDriver.AiIntentResult spotify = AiDriver.classify("Spotify", appMap);
        assertEquals("LAUNCH_APP", spotify.type);
        assertEquals("com.spotify.tv.android", spotify.getParamString("package"));

        AiDriver.AiIntentResult next = AiDriver.classify("next track", appMap);
        assertEquals("MEDIA_CONTROL", next.type);

        AiDriver.AiIntentResult pause = AiDriver.classify("pause", appMap);
        assertEquals("MEDIA_CONTROL", pause.type);
    }

    // ==========================================
    // Combination 10: Conversational Fallback + Local Control Return
    // ==========================================
    @Test
    public void combo_fallbackQuestionThenLocalTvAction() {
        AiDriver.AiIntentResult question = AiDriver.classify("what is the weather today?", appMap);
        assertEquals("GOOGLE_AI_FALLBACK", question.type);

        AiDriver.AiIntentResult openYt = AiDriver.classify("open YouTube", appMap);
        assertEquals("LAUNCH_APP", openYt.type);
        assertEquals("com.google.android.youtube.tv", openYt.getParamString("package"));
    }

    // ==========================================
    // Combination 11: TV Inputs + Settings Sequence
    // ==========================================
    @Test
    public void combo_tvInputsThenSettings() {
        AiDriver.AiIntentResult inputs = AiDriver.classify("inputs", appMap);
        assertEquals("SYSTEM_ACTION", inputs.type);
        assertEquals("inputs", inputs.action);

        AiDriver.AiIntentResult settings = AiDriver.classify("open settings", appMap);
        assertEquals("SYSTEM_ACTION", settings.type);
        assertEquals("settings", settings.action);
    }

    // ==========================================
    // Combination 12: Package Launch + Home + Mute
    // ==========================================
    @Test
    public void combo_packageLaunchThenHomeThenMute() {
        AiDriver.AiIntentResult pkg = AiDriver.classify("com.netflix.ninja", appMap);
        assertEquals("LAUNCH_APP", pkg.type);
        assertEquals("com.netflix.ninja", pkg.getParamString("package"));

        AiDriver.AiIntentResult home = AiDriver.classify("go home", appMap);
        assertEquals("SYSTEM_ACTION", home.type);

        AiDriver.AiIntentResult mute = AiDriver.classify("mute", appMap);
        assertEquals("VOLUME_CONTROL", mute.type);
    }

    // ==========================================
    // Combination 13: Profile Switcher + App Launch
    // ==========================================
    @Test
    public void combo_profileSwitcherThenAppLaunch() {
        AiDriver.AiIntentResult profile = AiDriver.classify("switch profile", appMap);
        assertEquals("LAUNCHER_FEATURE", profile.type);
        assertEquals("profile_switcher", profile.action);

        AiDriver.AiIntentResult vlc = AiDriver.classify("open VLC", appMap);
        assertEquals("LAUNCH_APP", vlc.type);
        assertEquals("org.videolan.vlc", vlc.getParamString("package"));
    }

    // ==========================================
    // Combination 14: Absolute Volume + Relative Adjust
    // ==========================================
    @Test
    public void combo_absoluteVolumeThenRelativeStep() {
        AiDriver.AiIntentResult vol50 = AiDriver.classify("set volume to 50%", appMap);
        assertEquals("VOLUME_CONTROL", vol50.type);
        assertEquals(Integer.valueOf(50), vol50.getParamInt("level"));

        AiDriver.AiIntentResult louder = AiDriver.classify("louder", appMap);
        assertEquals("VOLUME_CONTROL", louder.type);
        assertEquals(Integer.valueOf(1), louder.getParamInt("direction"));
    }

    // ==========================================
    // Combination 15: Media Fast Forward + Rewind
    // ==========================================
    @Test
    public void combo_mediaFastForwardThenRewind() {
        AiDriver.AiIntentResult ff = AiDriver.classify("fast forward", appMap);
        assertEquals("MEDIA_CONTROL", ff.type);

        AiDriver.AiIntentResult rew = AiDriver.classify("rewind", appMap);
        assertEquals("MEDIA_CONTROL", rew.type);
    }
}
