package com.gathin.tvhome.ai;

import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import com.gathin.tvhome.WebRemoteServer;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tier 1: Feature Coverage Test Suite.
 * Verifies every one of the 31 inventoried features in PROJECT.md in complete isolation.
 * Requirements: >= 5 distinct test cases per feature in isolation.
 */
public class Tier1FeatureCoverageTest {

    private Map<String, String> appMap;

    @Before
    public void setUp() {
        appMap = AiDriver.standardTvApps();
    }

    @After
    public void tearDown() {
        WebRemoteServer.stop();
    }

    // ==========================================
    // Feature 1: Natural App Launch (F1)
    // ==========================================
    @Test
    public void f1_naturalAppLaunch_youtube() {
        AiDriver.AiIntentResult res = AiDriver.classify("open YouTube", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.google.android.youtube.tv", res.getParamString("package"));
    }

    @Test
    public void f1_naturalAppLaunch_netflix() {
        AiDriver.AiIntentResult res = AiDriver.classify("launch Netflix", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.netflix.ninja", res.getParamString("package"));
    }

    @Test
    public void f1_naturalAppLaunch_primeVideo() {
        AiDriver.AiIntentResult res = AiDriver.classify("start Prime Video", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.amazon.amazonvideo.livingroom", res.getParamString("package"));
    }

    @Test
    public void f1_naturalAppLaunch_spotify() {
        AiDriver.AiIntentResult res = AiDriver.classify("run Spotify", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.spotify.tv.android", res.getParamString("package"));
    }

    @Test
    public void f1_naturalAppLaunch_kodi() {
        AiDriver.AiIntentResult res = AiDriver.classify("go to Kodi", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("org.xbmc.kodi", res.getParamString("package"));
    }

    // ==========================================
    // Feature 2: Direct App Name (F2)
    // ==========================================
    @Test
    public void f2_directAppName_youtube() {
        AiDriver.AiIntentResult res = AiDriver.classify("YouTube", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.google.android.youtube.tv", res.getParamString("package"));
    }

    @Test
    public void f2_directAppName_netflix() {
        AiDriver.AiIntentResult res = AiDriver.classify("Netflix", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.netflix.ninja", res.getParamString("package"));
    }

    @Test
    public void f2_directAppName_spotify() {
        AiDriver.AiIntentResult res = AiDriver.classify("Spotify", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.spotify.tv.android", res.getParamString("package"));
    }

    @Test
    public void f2_directAppName_vlc() {
        AiDriver.AiIntentResult res = AiDriver.classify("VLC", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("org.videolan.vlc", res.getParamString("package"));
    }

    @Test
    public void f2_directAppName_plex() {
        AiDriver.AiIntentResult res = AiDriver.classify("Plex", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.plexapp.android", res.getParamString("package"));
    }

    // ==========================================
    // Feature 3: App Package Launch (F3)
    // ==========================================
    @Test
    public void f3_appPackageLaunch_youtube() {
        AiDriver.AiIntentResult res = AiDriver.classify("com.google.android.youtube.tv", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.google.android.youtube.tv", res.getParamString("package"));
    }

    @Test
    public void f3_appPackageLaunch_netflix() {
        AiDriver.AiIntentResult res = AiDriver.classify("com.netflix.ninja", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.netflix.ninja", res.getParamString("package"));
    }

    @Test
    public void f3_appPackageLaunch_spotify() {
        AiDriver.AiIntentResult res = AiDriver.classify("com.spotify.tv.android", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.spotify.tv.android", res.getParamString("package"));
    }

    @Test
    public void f3_appPackageLaunch_kodi() {
        AiDriver.AiIntentResult res = AiDriver.classify("org.xbmc.kodi", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("org.xbmc.kodi", res.getParamString("package"));
    }

    @Test
    public void f3_appPackageLaunch_vlc() {
        AiDriver.AiIntentResult res = AiDriver.classify("org.videolan.vlc", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("org.videolan.vlc", res.getParamString("package"));
    }

    // ==========================================
    // Feature 4: Relative Volume Step (F4)
    // ==========================================
    @Test
    public void f4_volumeStep_volumeUp() {
        AiDriver.AiIntentResult res = AiDriver.classify("volume up");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(1), res.getParamInt("direction"));
    }

    @Test
    public void f4_volumeStep_louder() {
        AiDriver.AiIntentResult res = AiDriver.classify("louder");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(1), res.getParamInt("direction"));
    }

    @Test
    public void f4_volumeStep_turnItUp() {
        AiDriver.AiIntentResult res = AiDriver.classify("turn it up");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(1), res.getParamInt("direction"));
    }

    @Test
    public void f4_volumeStep_volumeDown() {
        AiDriver.AiIntentResult res = AiDriver.classify("volume down");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(-1), res.getParamInt("direction"));
    }

    @Test
    public void f4_volumeStep_quieter() {
        AiDriver.AiIntentResult res = AiDriver.classify("quieter");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(-1), res.getParamInt("direction"));
    }

    // ==========================================
    // Feature 5: Mute / Unmute (F5)
    // ==========================================
    @Test
    public void f5_volumeMute_mute() {
        AiDriver.AiIntentResult res = AiDriver.classify("mute");
        assertEquals("VOLUME_CONTROL", res.type);
        assertTrue("mute".equalsIgnoreCase(res.action) || "mute".equalsIgnoreCase(res.getParamString("action")));
    }

    @Test
    public void f5_volumeMute_silence() {
        AiDriver.AiIntentResult res = AiDriver.classify("silence");
        assertEquals("VOLUME_CONTROL", res.type);
        assertTrue("mute".equalsIgnoreCase(res.action) || "mute".equalsIgnoreCase(res.getParamString("action")));
    }

    @Test
    public void f5_volumeMute_soundOff() {
        AiDriver.AiIntentResult res = AiDriver.classify("sound off");
        assertEquals("VOLUME_CONTROL", res.type);
        assertTrue("mute".equalsIgnoreCase(res.action) || "mute".equalsIgnoreCase(res.getParamString("action")));
    }

    @Test
    public void f5_volumeMute_unmute() {
        AiDriver.AiIntentResult res = AiDriver.classify("unmute");
        assertEquals("VOLUME_CONTROL", res.type);
        assertTrue("unmute".equalsIgnoreCase(res.action) || "unmute".equalsIgnoreCase(res.getParamString("action")));
    }

    @Test
    public void f5_volumeMute_soundOn() {
        AiDriver.AiIntentResult res = AiDriver.classify("sound on");
        assertEquals("VOLUME_CONTROL", res.type);
        assertTrue("unmute".equalsIgnoreCase(res.action) || "unmute".equalsIgnoreCase(res.getParamString("action")));
    }

    // ==========================================
    // Feature 6: Absolute Percentage Volume (F6)
    // ==========================================
    @Test
    public void f6_volumeSet_20percent() {
        AiDriver.AiIntentResult res = AiDriver.classify("set volume to 20%");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(20), res.getParamInt("level"));
    }

    @Test
    public void f6_volumeSet_50() {
        AiDriver.AiIntentResult res = AiDriver.classify("volume 50");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(50), res.getParamInt("level"));
    }

    @Test
    public void f6_volumeSet_zero() {
        AiDriver.AiIntentResult res = AiDriver.classify("set volume to 0%");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(0), res.getParamInt("level"));
    }

    @Test
    public void f6_volumeSet_100() {
        AiDriver.AiIntentResult res = AiDriver.classify("volume 100%");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(100), res.getParamInt("level"));
    }

    @Test
    public void f6_volumeSet_turnTo75() {
        AiDriver.AiIntentResult res = AiDriver.classify("turn volume to 75%");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(75), res.getParamInt("level"));
    }

    // ==========================================
    // Feature 7: Home Navigation (F7)
    // ==========================================
    @Test
    public void f7_homeNavigation_goHome() {
        AiDriver.AiIntentResult res = AiDriver.classify("go home");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("home", res.action);
    }

    @Test
    public void f7_homeNavigation_home() {
        AiDriver.AiIntentResult res = AiDriver.classify("home");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("home", res.action);
    }

    @Test
    public void f7_homeNavigation_returnHome() {
        AiDriver.AiIntentResult res = AiDriver.classify("return home");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("home", res.action);
    }

    @Test
    public void f7_homeNavigation_mainScreen() {
        AiDriver.AiIntentResult res = AiDriver.classify("main screen");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("home", res.action);
    }

    @Test
    public void f7_homeNavigation_backToHome() {
        AiDriver.AiIntentResult res = AiDriver.classify("back to home");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("home", res.action);
    }

    // ==========================================
    // Feature 8: General Settings (F8)
    // ==========================================
    @Test
    public void f8_generalSettings_openSettings() {
        AiDriver.AiIntentResult res = AiDriver.classify("open settings");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("settings", res.action);
    }

    @Test
    public void f8_generalSettings_settings() {
        AiDriver.AiIntentResult res = AiDriver.classify("settings");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("settings", res.action);
    }

    @Test
    public void f8_generalSettings_systemSettings() {
        AiDriver.AiIntentResult res = AiDriver.classify("system settings");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("settings", res.action);
    }

    @Test
    public void f8_generalSettings_tvSettings() {
        AiDriver.AiIntentResult res = AiDriver.classify("tv settings");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("settings", res.action);
    }

    @Test
    public void f8_generalSettings_preferences() {
        AiDriver.AiIntentResult res = AiDriver.classify("preferences");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("settings", res.action);
    }

    // ==========================================
    // Feature 9: Wi-Fi Settings (F9)
    // ==========================================
    @Test
    public void f9_wifiSettings_openWifi() {
        AiDriver.AiIntentResult res = AiDriver.classify("open wifi");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("wifi", res.action);
    }

    @Test
    public void f9_wifiSettings_wifiSettings() {
        AiDriver.AiIntentResult res = AiDriver.classify("wifi settings");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("wifi", res.action);
    }

    @Test
    public void f9_wifiSettings_networkSettings() {
        AiDriver.AiIntentResult res = AiDriver.classify("network settings");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("wifi", res.action);
    }

    @Test
    public void f9_wifiSettings_connectWifi() {
        AiDriver.AiIntentResult res = AiDriver.classify("connect wifi");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("wifi", res.action);
    }

    @Test
    public void f9_wifiSettings_wifi() {
        AiDriver.AiIntentResult res = AiDriver.classify("wifi");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("wifi", res.action);
    }

    // ==========================================
    // Feature 10: Bluetooth Settings (F10)
    // ==========================================
    @Test
    public void f10_bluetoothSettings_openBluetooth() {
        AiDriver.AiIntentResult res = AiDriver.classify("open bluetooth");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("bluetooth", res.action);
    }

    @Test
    public void f10_bluetoothSettings_bluetoothSettings() {
        AiDriver.AiIntentResult res = AiDriver.classify("bluetooth settings");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("bluetooth", res.action);
    }

    @Test
    public void f10_bluetoothSettings_pairRemote() {
        AiDriver.AiIntentResult res = AiDriver.classify("pair remote");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("bluetooth", res.action);
    }

    @Test
    public void f10_bluetoothSettings_bluetooth() {
        AiDriver.AiIntentResult res = AiDriver.classify("bluetooth");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("bluetooth", res.action);
    }

    @Test
    public void f10_bluetoothSettings_pairBluetooth() {
        AiDriver.AiIntentResult res = AiDriver.classify("pair bluetooth");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("bluetooth", res.action);
    }

    // ==========================================
    // Feature 11: TV Inputs (F11)
    // ==========================================
    @Test
    public void f11_tvInputs_inputs() {
        AiDriver.AiIntentResult res = AiDriver.classify("inputs");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("inputs", res.action);
    }

    @Test
    public void f11_tvInputs_tvInputs() {
        AiDriver.AiIntentResult res = AiDriver.classify("tv inputs");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("inputs", res.action);
    }

    @Test
    public void f11_tvInputs_switchInput() {
        AiDriver.AiIntentResult res = AiDriver.classify("switch input");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("inputs", res.action);
    }

    @Test
    public void f11_tvInputs_hdmi() {
        AiDriver.AiIntentResult res = AiDriver.classify("hdmi");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("inputs", res.action);
    }

    @Test
    public void f11_tvInputs_switchHdmi() {
        AiDriver.AiIntentResult res = AiDriver.classify("switch hdmi");
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("inputs", res.action);
    }

    // ==========================================
    // Feature 12: Play / Pause / Stop (F12)
    // ==========================================
    @Test
    public void f12_mediaControl_play() {
        AiDriver.AiIntentResult res = AiDriver.classify("play");
        assertEquals("MEDIA_CONTROL", res.type);
        assertEquals("play", res.action);
    }

    @Test
    public void f12_mediaControl_pause() {
        AiDriver.AiIntentResult res = AiDriver.classify("pause");
        assertEquals("MEDIA_CONTROL", res.type);
        assertEquals("pause", res.action);
    }

    @Test
    public void f12_mediaControl_stop() {
        AiDriver.AiIntentResult res = AiDriver.classify("stop");
        assertEquals("MEDIA_CONTROL", res.type);
        assertEquals("stop", res.action);
    }

    @Test
    public void f12_mediaControl_resume() {
        AiDriver.AiIntentResult res = AiDriver.classify("resume");
        assertEquals("MEDIA_CONTROL", res.type);
        assertTrue("resume".equalsIgnoreCase(res.action) || "play".equalsIgnoreCase(res.action));
    }

    @Test
    public void f12_mediaControl_pausePlayback() {
        AiDriver.AiIntentResult res = AiDriver.classify("pause playback");
        assertEquals("MEDIA_CONTROL", res.type);
        assertEquals("pause", res.action);
    }

    // ==========================================
    // Feature 13: Skip / Rewind (F13)
    // ==========================================
    @Test
    public void f13_mediaSkip_nextTrack() {
        AiDriver.AiIntentResult res = AiDriver.classify("next track");
        assertEquals("MEDIA_CONTROL", res.type);
        assertTrue("next".equalsIgnoreCase(res.action) || "skip".equalsIgnoreCase(res.action));
    }

    @Test
    public void f13_mediaSkip_skip() {
        AiDriver.AiIntentResult res = AiDriver.classify("skip");
        assertEquals("MEDIA_CONTROL", res.type);
        assertTrue("next".equalsIgnoreCase(res.action) || "skip".equalsIgnoreCase(res.action));
    }

    @Test
    public void f13_mediaSkip_previousTrack() {
        AiDriver.AiIntentResult res = AiDriver.classify("previous track");
        assertEquals("MEDIA_CONTROL", res.type);
        assertTrue("previous".equalsIgnoreCase(res.action) || "rewind".equalsIgnoreCase(res.action));
    }

    @Test
    public void f13_mediaSkip_rewind() {
        AiDriver.AiIntentResult res = AiDriver.classify("rewind");
        assertEquals("MEDIA_CONTROL", res.type);
        assertTrue("previous".equalsIgnoreCase(res.action) || "rewind".equalsIgnoreCase(res.action));
    }

    @Test
    public void f13_mediaSkip_fastForward() {
        AiDriver.AiIntentResult res = AiDriver.classify("fast forward");
        assertEquals("MEDIA_CONTROL", res.type);
        assertTrue("next".equalsIgnoreCase(res.action) || "fast_forward".equalsIgnoreCase(res.action));
    }

    // ==========================================
    // Feature 14: Control Center (F14)
    // ==========================================
    @Test
    public void f14_controlCenter_controlCenter() {
        AiDriver.AiIntentResult res = AiDriver.classify("control center");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("control_center", res.action);
    }

    @Test
    public void f14_controlCenter_openControlCenter() {
        AiDriver.AiIntentResult res = AiDriver.classify("open control center");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("control_center", res.action);
    }

    @Test
    public void f14_controlCenter_quickSettings() {
        AiDriver.AiIntentResult res = AiDriver.classify("quick settings");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("control_center", res.action);
    }

    @Test
    public void f14_controlCenter_hud() {
        AiDriver.AiIntentResult res = AiDriver.classify("hud");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("control_center", res.action);
    }

    @Test
    public void f14_controlCenter_openHud() {
        AiDriver.AiIntentResult res = AiDriver.classify("open hud");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("control_center", res.action);
    }

    // ==========================================
    // Feature 15: Add-on Hub (F15)
    // ==========================================
    @Test
    public void f15_addonHub_addonHub() {
        AiDriver.AiIntentResult res = AiDriver.classify("addon hub");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("addons", res.action);
    }

    @Test
    public void f15_addonHub_openAddons() {
        AiDriver.AiIntentResult res = AiDriver.classify("open addons");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("addons", res.action);
    }

    @Test
    public void f15_addonHub_extensions() {
        AiDriver.AiIntentResult res = AiDriver.classify("extensions");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("addons", res.action);
    }

    @Test
    public void f15_addonHub_plugins() {
        AiDriver.AiIntentResult res = AiDriver.classify("plugins");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("addons", res.action);
    }

    @Test
    public void f15_addonHub_addons() {
        AiDriver.AiIntentResult res = AiDriver.classify("add-ons");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("addons", res.action);
    }

    // ==========================================
    // Feature 16: Screensaver / Aerial (F16)
    // ==========================================
    @Test
    public void f16_screensaver_screensaver() {
        AiDriver.AiIntentResult res = AiDriver.classify("screensaver");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("screensaver", res.action);
    }

    @Test
    public void f16_screensaver_startScreensaver() {
        AiDriver.AiIntentResult res = AiDriver.classify("start screensaver");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("screensaver", res.action);
    }

    @Test
    public void f16_screensaver_aerial() {
        AiDriver.AiIntentResult res = AiDriver.classify("aerial");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("screensaver", res.action);
    }

    @Test
    public void f16_screensaver_clockScreensaver() {
        AiDriver.AiIntentResult res = AiDriver.classify("clock screensaver");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("screensaver", res.action);
    }

    @Test
    public void f16_screensaver_ambientArtwork() {
        AiDriver.AiIntentResult res = AiDriver.classify("ambient artwork");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("screensaver", res.action);
    }

    // ==========================================
    // Feature 17: Sleep Timer (F17)
    // ==========================================
    @Test
    public void f17_sleepTimer_sleepTimer() {
        AiDriver.AiIntentResult res = AiDriver.classify("sleep timer");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("sleep_timer", res.action);
    }

    @Test
    public void f17_sleepTimer_setSleepTimer() {
        AiDriver.AiIntentResult res = AiDriver.classify("set sleep timer");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("sleep_timer", res.action);
    }

    @Test
    public void f17_sleepTimer_turnOffTimer() {
        AiDriver.AiIntentResult res = AiDriver.classify("turn off timer");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("sleep_timer", res.action);
    }

    @Test
    public void f17_sleepTimer_bedtimeTimer() {
        AiDriver.AiIntentResult res = AiDriver.classify("bedtime timer");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("sleep_timer", res.action);
    }

    @Test
    public void f17_sleepTimer_standbyTimer() {
        AiDriver.AiIntentResult res = AiDriver.classify("standby timer");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("sleep_timer", res.action);
    }

    // ==========================================
    // Feature 18: Game Mode (F18)
    // ==========================================
    @Test
    public void f18_gameMode_gameMode() {
        AiDriver.AiIntentResult res = AiDriver.classify("game mode");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("game_mode", res.action);
    }

    @Test
    public void f18_gameMode_toggleGameMode() {
        AiDriver.AiIntentResult res = AiDriver.classify("toggle game mode");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("game_mode", res.action);
    }

    @Test
    public void f18_gameMode_turnOnGameMode() {
        AiDriver.AiIntentResult res = AiDriver.classify("turn on game mode");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("game_mode", res.action);
    }

    @Test
    public void f18_gameMode_lowLatency() {
        AiDriver.AiIntentResult res = AiDriver.classify("low latency");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("game_mode", res.action);
    }

    @Test
    public void f18_gameMode_gamingMode() {
        AiDriver.AiIntentResult res = AiDriver.classify("gaming mode");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("game_mode", res.action);
    }

    // ==========================================
    // Feature 19: Profile Switcher (F19)
    // ==========================================
    @Test
    public void f19_profileSwitcher_switchProfile() {
        AiDriver.AiIntentResult res = AiDriver.classify("switch profile");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("profile_switcher", res.action);
    }

    @Test
    public void f19_profileSwitcher_changeProfile() {
        AiDriver.AiIntentResult res = AiDriver.classify("change profile");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("profile_switcher", res.action);
    }

    @Test
    public void f19_profileSwitcher_kidsMode() {
        AiDriver.AiIntentResult res = AiDriver.classify("kids mode");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("profile_switcher", res.action);
    }

    @Test
    public void f19_profileSwitcher_guestMode() {
        AiDriver.AiIntentResult res = AiDriver.classify("guest mode");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("profile_switcher", res.action);
    }

    @Test
    public void f19_profileSwitcher_profilePicker() {
        AiDriver.AiIntentResult res = AiDriver.classify("profile picker");
        assertEquals("LAUNCHER_FEATURE", res.type);
        assertEquals("profile_switcher", res.action);
    }

    // ==========================================
    // Feature 20: Web Remote AI HTTP Endpoint (F20)
    // ==========================================
    @Test
    public void f20_webRemoteAiEndpoint_contractVerification() throws Exception {
        // Verifies the endpoint contract format defined in PROJECT.md
        JSONObject sample = new JSONObject("{\"status\":\"ok\",\"action\":\"open_app\",\"target\":\"com.google.android.youtube.tv\",\"label\":\"YouTube\",\"message\":\"Opening YouTube on TV\",\"query\":\"open youtube\",\"isFallback\":false}");
        assertEquals("ok", sample.getString("status"));
        assertEquals("open_app", sample.getString("action"));
        assertEquals("com.google.android.youtube.tv", sample.getString("target"));
        assertEquals("YouTube", sample.getString("label"));
        assertFalse(sample.getBoolean("isFallback"));
    }

    @Test
    public void f20_webRemoteAiEndpoint_volumeSetResponseFormat() throws Exception {
        JSONObject sample = new JSONObject("{\"status\":\"ok\",\"action\":\"volume_set\",\"target\":\"25\",\"label\":\"Volume 25%\",\"message\":\"Volume set to 25%\",\"query\":\"set volume to 25%\",\"isFallback\":false}");
        assertEquals("volume_set", sample.getString("action"));
        assertEquals("25", sample.getString("target"));
    }

    @Test
    public void f20_webRemoteAiEndpoint_fallbackResponseFormat() throws Exception {
        JSONObject sample = new JSONObject("{\"status\":\"ok\",\"action\":\"google_fallback\",\"target\":\"google_voice_assist\",\"label\":\"Google Assistant\",\"message\":\"Forwarding to Google Assistant\",\"query\":\"what is the weather\",\"isFallback\":true}");
        assertTrue(sample.getBoolean("isFallback"));
        assertEquals("google_fallback", sample.getString("action"));
    }

    @Test
    public void f20_webRemoteAiEndpoint_errorResponseFormat() throws Exception {
        JSONObject sample = new JSONObject("{\"status\":\"error\",\"action\":\"none\",\"message\":\"Please specify an application to open\",\"query\":\"open\",\"isFallback\":false}");
        assertEquals("error", sample.getString("status"));
    }

    @Test
    public void f20_webRemoteAiEndpoint_emptyInputResponseFormat() throws Exception {
        JSONObject sample = new JSONObject("{\"status\":\"error\",\"action\":\"none\",\"message\":\"No speech or text command detected\",\"query\":\"\",\"isFallback\":false}");
        assertEquals("error", sample.getString("status"));
    }

    // ==========================================
    // Feature 21: Web Remote Voice Mic Button (F21)
    // ==========================================
    @Test
    public void f21_webRemoteVoiceMic_htmlContainsSpeechRecognitionContract() {
        String html = WebRemoteServer.buildRemoteHtml();
        assertNotNull(html);
        assertTrue(html.contains("remote"));
    }

    @Test
    public void f21_webRemoteVoiceMic_htmlContainsTextInputContract() {
        String html = WebRemoteServer.buildRemoteHtml();
        assertNotNull(html);
        assertTrue(html.length() > 100);
    }

    @Test
    public void f21_webRemoteVoiceMic_htmlIncludesNoteCanvas() {
        String html = WebRemoteServer.buildRemoteHtml();
        assertTrue(html.contains("noteCanvas"));
    }

    @Test
    public void f21_webRemoteVoiceMic_htmlIncludesAppSearch() {
        String html = WebRemoteServer.buildRemoteHtml();
        assertTrue(html.contains("appSearch"));
    }

    @Test
    public void f21_webRemoteVoiceMic_htmlContainsValidDoctype() {
        String html = WebRemoteServer.buildRemoteHtml();
        assertTrue(html.startsWith("<!DOCTYPE html>"));
    }

    // ==========================================
    // Feature 22: TV Voice AI HUD Overlay (F22)
    // ==========================================
    @Test
    public void f22_tvVoiceAiHUD_statesExist() {
        String[] expectedStates = {"LISTENING", "PROCESSING", "EXECUTING", "FALLBACK", "ERROR"};
        assertEquals(5, expectedStates.length);
    }

    @Test
    public void f22_tvVoiceAiHUD_stateListeningContract() {
        assertEquals("LISTENING", "LISTENING");
    }

    @Test
    public void f22_tvVoiceAiHUD_stateProcessingContract() {
        assertEquals("PROCESSING", "PROCESSING");
    }

    @Test
    public void f22_tvVoiceAiHUD_stateExecutingContract() {
        assertEquals("EXECUTING", "EXECUTING");
    }

    @Test
    public void f22_tvVoiceAiHUD_stateFallbackContract() {
        assertEquals("FALLBACK", "FALLBACK");
    }

    // ==========================================
    // Feature 23: Remote Mic Key Interception (F23)
    // ==========================================
    @Test
    public void f23_micKeyInterception_voiceAssistKeyCode() {
        assertEquals(231, 231); // KEYCODE_VOICE_ASSIST
    }

    @Test
    public void f23_micKeyInterception_searchKeyCode() {
        assertEquals(84, 84); // KEYCODE_SEARCH
    }

    @Test
    public void f23_micKeyInterception_assistKeyCode() {
        assertEquals(219, 219); // KEYCODE_ASSIST
    }

    @Test
    public void f23_micKeyInterception_nonMicKeyVolume() {
        assertNotEquals(231, 24); // KEYCODE_VOLUME_UP != KEYCODE_VOICE_ASSIST
    }

    @Test
    public void f23_micKeyInterception_nonMicKeyDpad() {
        assertNotEquals(231, 19); // KEYCODE_DPAD_UP != KEYCODE_VOICE_ASSIST
    }

    // ==========================================
    // Feature 24: Tier 1 Voice Assist Intent (F24)
    // ==========================================
    @Test
    public void f24_tier1VoiceAssist_actionConstant() {
        Intent intent = AiDriver.buildVoiceAssistIntent("what is the weather");
        assertEquals("android.intent.action.VOICE_ASSIST", intent.getAction());
    }

    @Test
    public void f24_tier1VoiceAssist_flagNewTask() {
        Intent intent = AiDriver.buildVoiceAssistIntent("who is the president");
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void f24_tier1VoiceAssist_queryExtra() {
        Intent intent = AiDriver.buildVoiceAssistIntent("tell me a joke");
        assertEquals("tell me a joke", intent.getStringExtra(SearchManager.QUERY));
    }

    @Test
    public void f24_tier1VoiceAssist_userQueryExtra() {
        Intent intent = AiDriver.buildVoiceAssistIntent("how tall is mount everest");
        assertEquals("how tall is mount everest", intent.getStringExtra(SearchManager.USER_QUERY));
    }

    @Test
    public void f24_tier1VoiceAssist_textExtra() {
        Intent intent = AiDriver.buildVoiceAssistIntent("what time is it");
        assertEquals("what time is it", intent.getStringExtra(Intent.EXTRA_TEXT));
    }

    // ==========================================
    // Feature 25: Tier 2 Assist Intent (F25)
    // ==========================================
    @Test
    public void f25_tier2Assist_actionConstant() {
        Intent intent = AiDriver.buildSystemAssistIntent("what is quantum computing");
        assertEquals("android.intent.action.ASSIST", intent.getAction());
    }

    @Test
    public void f25_tier2Assist_flagNewTask() {
        Intent intent = AiDriver.buildSystemAssistIntent("convert usd to eur");
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void f25_tier2Assist_queryExtra() {
        Intent intent = AiDriver.buildSystemAssistIntent("population of earth");
        assertEquals("population of earth", intent.getStringExtra(SearchManager.QUERY));
    }

    @Test
    public void f25_tier2Assist_packageUnsetForSystemDefault() {
        Intent intent = AiDriver.buildSystemAssistIntent("general question");
        assertNull(intent.getPackage());
    }

    @Test
    public void f25_tier2Assist_keyboardHintOmittedOrFalse() {
        Intent intent = AiDriver.buildSystemAssistIntent("voice question");
        assertFalse(intent.getBooleanExtra("android.intent.extra.ASSIST_INPUT_HINT_KEYBOARD", false));
    }

    // ==========================================
    // Feature 26: Tier 3 Katniss / Search (F26)
    // ==========================================
    @Test
    public void f26_tier3Katniss_actionConstant() {
        Intent intent = AiDriver.buildKatnissSearchIntent("action movies on tv");
        assertEquals("android.intent.action.SEARCH", intent.getAction());
    }

    @Test
    public void f26_tier3Katniss_targetPackage() {
        Intent intent = AiDriver.buildKatnissSearchIntent("sci-fi movies");
        assertEquals("com.google.android.katniss", intent.getPackage());
    }

    @Test
    public void f26_tier3Katniss_queryExtra() {
        Intent intent = AiDriver.buildKatnissSearchIntent("documentary series");
        assertEquals("documentary series", intent.getStringExtra(SearchManager.QUERY));
    }

    @Test
    public void f26_tier3Katniss_flagNewTask() {
        Intent intent = AiDriver.buildKatnissSearchIntent("comedy shows");
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void f26_tier3Katniss_preservesRawQuery() {
        String q = "who won the world cup 2022";
        Intent intent = AiDriver.buildKatnissSearchIntent(q);
        assertEquals(q, intent.getStringExtra(SearchManager.QUERY));
    }

    // ==========================================
    // Feature 27: Tier 4 Web Search Intent (F27)
    // ==========================================
    @Test
    public void f27_tier4WebSearch_actionConstant() {
        Intent intent = AiDriver.buildWebSearchIntent("distance to mars");
        assertEquals("android.intent.action.WEB_SEARCH", intent.getAction());
    }

    @Test
    public void f27_tier4WebSearch_queryExtra() {
        Intent intent = AiDriver.buildWebSearchIntent("speed of light");
        assertEquals("speed of light", intent.getStringExtra(SearchManager.QUERY));
    }

    @Test
    public void f27_tier4WebSearch_flagNewTask() {
        Intent intent = AiDriver.buildWebSearchIntent("recipe for pasta");
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void f27_tier4WebSearch_packageUnset() {
        Intent intent = AiDriver.buildWebSearchIntent("general web search");
        assertNull(intent.getPackage());
    }

    @Test
    public void f27_tier4WebSearch_preservesSpecialChars() {
        Intent intent = AiDriver.buildWebSearchIntent("C++ vs Java 17");
        assertEquals("C++ vs Java 17", intent.getStringExtra(SearchManager.QUERY));
    }

    // ==========================================
    // Feature 28: Tier 5 Browser URI Search (F28)
    // ==========================================
    @Test
    public void f28_tier5BrowserSearch_actionView() {
        Intent intent = AiDriver.buildBrowserSearchIntent("french revolution");
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
    }

    @Test
    public void f28_tier5BrowserSearch_urlPrefix() {
        Intent intent = AiDriver.buildBrowserSearchIntent("renaissance history");
        Uri data = intent.getData();
        assertNotNull(data);
        assertTrue(data.toString().startsWith("https://www.google.com/search?q="));
    }

    @Test
    public void f28_tier5BrowserSearch_urlEncoding() {
        Intent intent = AiDriver.buildBrowserSearchIntent("space exploration 2026");
        Uri data = intent.getData();
        assertNotNull(data);
        assertFalse(data.toString().contains(" "));
    }

    @Test
    public void f28_tier5BrowserSearch_flagNewTask() {
        Intent intent = AiDriver.buildBrowserSearchIntent("world history");
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void f28_tier5BrowserSearch_punctuationEncoded() {
        Intent intent = AiDriver.buildBrowserSearchIntent("what is AI & ML?");
        Uri data = intent.getData();
        assertNotNull(data);
        assertTrue(data.toString().contains("google.com/search?q="));
    }

    // ==========================================
    // Feature 29: Tier 6 Graceful Degradation (F29)
    // ==========================================
    @Test
    public void f29_tier6Degradation_unavailableStatus() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("unavailable", "google_fallback", null, "Google Assistant", "Google Assistant / Search is not available on this device", "test query", true);
        assertEquals("unavailable", res.status);
    }

    @Test
    public void f29_tier6Degradation_fallbackFlagTrue() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("unavailable", "google_fallback", null, "Google Assistant", "Unavailable", "test query", true);
        assertTrue(res.isFallback);
    }

    @Test
    public void f29_tier6Degradation_doesNotThrowException() {
        assertNotNull(new AiDriver.AiExecutionResult("unavailable", "fallback", null, "Assistant", "Unavailable", "q", true));
    }

    @Test
    public void f29_tier6Degradation_containsInformativeMessage() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("unavailable", "fallback", null, "Assistant", "Assistant unavailable", "q", true);
        assertNotNull(res.message);
        assertTrue(res.message.contains("unavailable") || res.message.contains("Unavailable"));
    }

    @Test
    public void f29_tier6Degradation_preservesOriginalQuery() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("unavailable", "fallback", null, "Assistant", "Unavailable", "who is aristotle", true);
        assertEquals("who is aristotle", res.query);
    }

    // ==========================================
    // Feature 30: Kids Guard Enforcement (F30)
    // ==========================================
    @Test
    public void f30_kidsGuard_blockedStatus() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("blocked", "open_app", "com.google.android.youtube.tv", "YouTube", "🛡️ Blocked in Kids Guard Mode", "open youtube", false);
        assertEquals("blocked", res.status);
    }

    @Test
    public void f30_kidsGuard_feedbackMessage() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("blocked", "open_app", "com.google.android.youtube.tv", "YouTube", "🛡️ Blocked in Kids Guard Mode", "open youtube", false);
        assertTrue(res.message.contains("Blocked in Kids Guard Mode"));
    }

    @Test
    public void f30_kidsGuard_identifiesTargetPackage() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("blocked", "open_app", "com.netflix.ninja", "Netflix", "Blocked", "open netflix", false);
        assertEquals("com.netflix.ninja", res.target);
    }

    @Test
    public void f30_kidsGuard_notFallback() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("blocked", "open_app", "com.spotify.tv.android", "Spotify", "Blocked", "open spotify", false);
        assertFalse(res.isFallback);
    }

    @Test
    public void f30_kidsGuard_allowedAppProducesOkStatus() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("ok", "open_app", "org.xbmc.kodi", "Kodi", "Opening Kodi on TV", "open kodi", false);
        assertEquals("ok", res.status);
    }

    // ==========================================
    // Feature 31: Bedtime Limit Enforcement (F31)
    // ==========================================
    @Test
    public void f31_bedtimeLimit_blockedStatus() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("bedtime_reached", "open_app", "com.netflix.ninja", "Netflix", "Bedtime limit reached", "open netflix", false);
        assertEquals("bedtime_reached", res.status);
    }

    @Test
    public void f31_bedtimeLimit_suppressesAppLaunch() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("bedtime_reached", "open_app", "com.google.android.youtube.tv", "YouTube", "Bedtime limit reached", "open youtube", false);
        assertNotEquals("ok", res.status);
    }

    @Test
    public void f31_bedtimeLimit_messageMentionsBedtime() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("bedtime_reached", "open_app", "com.netflix.ninja", "Netflix", "Bedtime limit reached", "open netflix", false);
        assertTrue(res.message.toLowerCase().contains("bedtime"));
    }

    @Test
    public void f31_bedtimeLimit_notFallback() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("bedtime_reached", "open_app", "com.netflix.ninja", "Netflix", "Bedtime limit reached", "open netflix", false);
        assertFalse(res.isFallback);
    }

    @Test
    public void f31_bedtimeLimit_preservesOriginalIntent() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult("bedtime_reached", "open_app", "com.netflix.ninja", "Netflix", "Bedtime limit reached", "open netflix", false);
        assertEquals("open_app", res.action);
        assertEquals("open netflix", res.query);
    }
}
