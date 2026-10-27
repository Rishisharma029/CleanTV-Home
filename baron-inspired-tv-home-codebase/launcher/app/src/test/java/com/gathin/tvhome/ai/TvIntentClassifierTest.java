package com.gathin.tvhome.ai;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Comprehensive unit test suite for TvIntentClassifier.
 * Verifies on-device intent parsing, token normalization, polite phrase stripping,
 * volume, media, system, launcher feature domains, and conversational fallback.
 */
public class TvIntentClassifierTest {

    private TvAppLookup appLookup;

    @Before
    public void setUp() {
        Map<String, String> labelToPkg = new HashMap<>();
        labelToPkg.put("youtube", "com.google.android.youtube.tv");
        labelToPkg.put("netflix", "com.netflix.ninja");
        labelToPkg.put("prime video", "com.amazon.amazonvideo.livingroom");
        labelToPkg.put("spotify", "com.spotify.tv.android");
        labelToPkg.put("kodi", "org.xbmc.kodi");
        labelToPkg.put("vlc", "org.videolan.vlc");
        labelToPkg.put("plex", "com.plexapp.android");

        Map<String, String> pkgToLabel = new HashMap<>();
        pkgToLabel.put("com.google.android.youtube.tv", "YouTube");
        pkgToLabel.put("com.netflix.ninja", "Netflix");
        pkgToLabel.put("com.amazon.amazonvideo.livingroom", "Prime Video");
        pkgToLabel.put("com.spotify.tv.android", "Spotify");
        pkgToLabel.put("org.xbmc.kodi", "Kodi");
        pkgToLabel.put("org.videolan.vlc", "VLC");
        pkgToLabel.put("com.plexapp.android", "Plex");

        appLookup = new TvAppLookup() {
            @Override
            public String findPackageForLabel(String query) {
                if (query == null) return null;
                String norm = query.toLowerCase(Locale.ROOT).trim();
                if (pkgToLabel.containsKey(norm)) return norm;
                return labelToPkg.get(norm);
            }

            @Override
            public String getLabelForPackage(String pkg) {
                return pkgToLabel.get(pkg);
            }
        };
    }

    @Test
    public void testEmptyAndNullQueries() {
        TvIntent resNull = TvIntentClassifier.classify(null, appLookup);
        assertEquals(IntentType.UNKNOWN, resNull.type);
        assertEquals("empty_input", resNull.action);

        TvIntent resEmpty = TvIntentClassifier.classify("", appLookup);
        assertEquals(IntentType.UNKNOWN, resEmpty.type);
        assertEquals("empty_input", resEmpty.action);

        TvIntent resWhitespace = TvIntentClassifier.classify("   ", appLookup);
        assertEquals(IntentType.UNKNOWN, resWhitespace.type);
        assertEquals("empty_input", resWhitespace.action);
    }

    @Test
    public void testMissingParameterQueries() {
        TvIntent resOpen = TvIntentClassifier.classify("open", appLookup);
        assertEquals(IntentType.UNKNOWN, resOpen.type);
        assertEquals("missing_parameter", resOpen.action);

        TvIntent resLaunch = TvIntentClassifier.classify("launch", appLookup);
        assertEquals(IntentType.UNKNOWN, resLaunch.type);
        assertEquals("missing_parameter", resLaunch.action);

        TvIntent resVolSet = TvIntentClassifier.classify("set volume to", appLookup);
        assertEquals(IntentType.UNKNOWN, resVolSet.type);
        assertEquals("missing_parameter", resVolSet.action);
    }

    @Test
    public void testPolitePhrasesAndPunctuation() {
        TvIntent res1 = TvIntentClassifier.classify("Please open YouTube!", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res1.type);
        assertEquals("com.google.android.youtube.tv", res1.getParamString("package"));

        TvIntent res2 = TvIntentClassifier.classify("Can you mute?", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, res2.type);
        assertEquals("mute", res2.action);

        TvIntent res3 = TvIntentClassifier.classify("Could you please turn up the volume?", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, res3.type);
        assertEquals(1, res3.getParamInt("direction", 0));

        TvIntent res4 = TvIntentClassifier.classify("open netflix, please", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res4.type);
        assertEquals("com.netflix.ninja", res4.getParamString("package"));
    }

    @Test
    public void testVolumeControlUpAndDown() {
        TvIntent resUp1 = TvIntentClassifier.classify("volume up", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resUp1.type);
        assertEquals(1, resUp1.getParamInt("direction", 0));

        TvIntent resUp2 = TvIntentClassifier.classify("louder", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resUp2.type);
        assertEquals(1, resUp2.getParamInt("direction", 0));

        TvIntent resUp3 = TvIntentClassifier.classify("turn it up", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resUp3.type);
        assertEquals(1, resUp3.getParamInt("direction", 0));

        TvIntent resDown1 = TvIntentClassifier.classify("volume down", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resDown1.type);
        assertEquals(-1, resDown1.getParamInt("direction", 0));

        TvIntent resDown2 = TvIntentClassifier.classify("quieter", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resDown2.type);
        assertEquals(-1, resDown2.getParamInt("direction", 0));

        TvIntent resDown3 = TvIntentClassifier.classify("lower volume", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resDown3.type);
        assertEquals(-1, resDown3.getParamInt("direction", 0));
    }

    @Test
    public void testVolumeMuteAndUnmute() {
        TvIntent resMute = TvIntentClassifier.classify("mute", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resMute.type);
        assertEquals("mute", resMute.action);

        TvIntent resSilence = TvIntentClassifier.classify("silence", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resSilence.type);
        assertEquals("mute", resSilence.action);

        TvIntent resSoundOff = TvIntentClassifier.classify("sound off", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resSoundOff.type);
        assertEquals("mute", resSoundOff.action);

        TvIntent resUnmute = TvIntentClassifier.classify("unmute", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resUnmute.type);
        assertEquals("unmute", resUnmute.action);

        TvIntent resSoundOn = TvIntentClassifier.classify("sound on", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resSoundOn.type);
        assertEquals("unmute", resSoundOn.action);
    }

    @Test
    public void testVolumePercentage() {
        TvIntent res1 = TvIntentClassifier.classify("set volume to 20%", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, res1.type);
        assertEquals(20, res1.getParamInt("level", -1));

        TvIntent res2 = TvIntentClassifier.classify("volume 50", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, res2.type);
        assertEquals(50, res2.getParamInt("level", -1));

        TvIntent res3 = TvIntentClassifier.classify("turn volume to 75%", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, res3.type);
        assertEquals(75, res3.getParamInt("level", -1));

        TvIntent resZero = TvIntentClassifier.classify("volume 0%", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, resZero.type);
        assertEquals(0, resZero.getParamInt("level", -1));

        TvIntent res100 = TvIntentClassifier.classify("set volume to 100", appLookup);
        assertEquals(IntentType.VOLUME_CONTROL, res100.type);
        assertEquals(100, res100.getParamInt("level", -1));
    }

    @Test
    public void testMediaControl() {
        TvIntent resPlay = TvIntentClassifier.classify("play", appLookup);
        assertEquals(IntentType.MEDIA_CONTROL, resPlay.type);
        assertEquals("play", resPlay.action);

        TvIntent resPause = TvIntentClassifier.classify("pause", appLookup);
        assertEquals(IntentType.MEDIA_CONTROL, resPause.type);
        assertEquals("pause", resPause.action);

        TvIntent resStop = TvIntentClassifier.classify("stop", appLookup);
        assertEquals(IntentType.MEDIA_CONTROL, resStop.type);
        assertEquals("stop", resStop.action);

        TvIntent resNext = TvIntentClassifier.classify("next track", appLookup);
        assertEquals(IntentType.MEDIA_CONTROL, resNext.type);
        assertEquals("next", resNext.action);

        TvIntent resSkip = TvIntentClassifier.classify("skip", appLookup);
        assertEquals(IntentType.MEDIA_CONTROL, resSkip.type);
        assertEquals("next", resSkip.action);

        TvIntent resRewind = TvIntentClassifier.classify("rewind", appLookup);
        assertEquals(IntentType.MEDIA_CONTROL, resRewind.type);
        assertEquals("rewind", resRewind.action);

        TvIntent resPrev = TvIntentClassifier.classify("previous", appLookup);
        assertEquals(IntentType.MEDIA_CONTROL, resPrev.type);
        assertEquals("previous", resPrev.action);
    }

    @Test
    public void testSystemNavigation() {
        TvIntent resHome = TvIntentClassifier.classify("go home", appLookup);
        assertEquals(IntentType.SYSTEM_ACTION, resHome.type);
        assertEquals("home", resHome.action);

        TvIntent resSettings = TvIntentClassifier.classify("open settings", appLookup);
        assertEquals(IntentType.SYSTEM_ACTION, resSettings.type);
        assertEquals("settings", resSettings.action);

        TvIntent resWifi = TvIntentClassifier.classify("wifi settings", appLookup);
        assertEquals(IntentType.SYSTEM_ACTION, resWifi.type);
        assertEquals("wifi", resWifi.action);

        TvIntent resBt = TvIntentClassifier.classify("pair remote", appLookup);
        assertEquals(IntentType.SYSTEM_ACTION, resBt.type);
        assertEquals("bluetooth", resBt.action);

        TvIntent resInputs = TvIntentClassifier.classify("tv inputs", appLookup);
        assertEquals(IntentType.SYSTEM_ACTION, resInputs.type);
        assertEquals("inputs", resInputs.action);

        TvIntent resHdmi = TvIntentClassifier.classify("switch hdmi", appLookup);
        assertEquals(IntentType.SYSTEM_ACTION, resHdmi.type);
        assertEquals("inputs", resHdmi.action);

        TvIntent resManageApps = TvIntentClassifier.classify("manage apps", appLookup);
        assertEquals(IntentType.SYSTEM_ACTION, resManageApps.type);
        assertEquals("manage_apps", resManageApps.action);
    }

    @Test
    public void testLauncherFeatures() {
        TvIntent resHud = TvIntentClassifier.classify("control center", appLookup);
        assertEquals(IntentType.LAUNCHER_FEATURE, resHud.type);
        assertEquals("control_center", resHud.action);

        TvIntent resAddons = TvIntentClassifier.classify("addon hub", appLookup);
        assertEquals(IntentType.LAUNCHER_FEATURE, resAddons.type);
        assertEquals("addons", resAddons.action);

        TvIntent resScreensaver = TvIntentClassifier.classify("screensaver", appLookup);
        assertEquals(IntentType.LAUNCHER_FEATURE, resScreensaver.type);
        assertEquals("screensaver", resScreensaver.action);

        TvIntent resSleep = TvIntentClassifier.classify("sleep timer", appLookup);
        assertEquals(IntentType.LAUNCHER_FEATURE, resSleep.type);
        assertEquals("sleep_timer", resSleep.action);

        TvIntent resGame = TvIntentClassifier.classify("game mode", appLookup);
        assertEquals(IntentType.LAUNCHER_FEATURE, resGame.type);
        assertEquals("game_mode", resGame.action);

        TvIntent resProfile = TvIntentClassifier.classify("switch profile", appLookup);
        assertEquals(IntentType.LAUNCHER_FEATURE, resProfile.type);
        assertEquals("profile_switcher", resProfile.action);

        TvIntent resKids = TvIntentClassifier.classify("kids mode", appLookup);
        assertEquals(IntentType.LAUNCHER_FEATURE, resKids.type);
        assertEquals("profile_switcher", resKids.action);

        TvIntent resDebloater = TvIntentClassifier.classify("debloater", appLookup);
        assertEquals(IntentType.LAUNCHER_FEATURE, resDebloater.type);
        assertEquals("debloater", resDebloater.action);
    }

    @Test
    public void testAppLaunchInstalled() {
        TvIntent res1 = TvIntentClassifier.classify("open YouTube", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res1.type);
        assertEquals("com.google.android.youtube.tv", res1.getParamString("package"));
        assertTrue(res1.getParamBoolean("installed", false));

        TvIntent res2 = TvIntentClassifier.classify("launch Netflix", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res2.type);
        assertEquals("com.netflix.ninja", res2.getParamString("package"));

        TvIntent res3 = TvIntentClassifier.classify("start Prime Video", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res3.type);
        assertEquals("com.amazon.amazonvideo.livingroom", res3.getParamString("package"));

        TvIntent res4 = TvIntentClassifier.classify("open YouTube app", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res4.type);
        assertEquals("com.google.android.youtube.tv", res4.getParamString("package"));
    }

    @Test
    public void testAppDirectName() {
        TvIntent res1 = TvIntentClassifier.classify("YouTube", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res1.type);
        assertEquals("com.google.android.youtube.tv", res1.getParamString("package"));

        TvIntent res2 = TvIntentClassifier.classify("Netflix", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res2.type);
        assertEquals("com.netflix.ninja", res2.getParamString("package"));

        TvIntent res3 = TvIntentClassifier.classify("Spotify", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res3.type);
        assertEquals("com.spotify.tv.android", res3.getParamString("package"));
    }

    @Test
    public void testAppPackageDirect() {
        TvIntent res = TvIntentClassifier.classify("com.google.android.youtube.tv", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res.type);
        assertEquals("com.google.android.youtube.tv", res.getParamString("package"));
    }

    @Test
    public void testAppLaunchUninstalled() {
        TvIntent res = TvIntentClassifier.classify("open Disney Plus", appLookup);
        assertEquals(IntentType.LAUNCH_APP, res.type);
        assertNull(res.getParamString("package"));
        assertFalse(res.getParamBoolean("installed", true));
        assertEquals("Disney Plus", res.getParamString("target"));
    }

    @Test
    public void testGoogleAiFallback5W1H() {
        TvIntent res1 = TvIntentClassifier.classify("what is quantum computing", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res1.type);
        assertTrue(res1.isFallback());

        TvIntent res2 = TvIntentClassifier.classify("who is the president", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res2.type);

        TvIntent res3 = TvIntentClassifier.classify("where is Mount Everest", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res3.type);

        TvIntent res4 = TvIntentClassifier.classify("why is the sky blue", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res4.type);

        TvIntent res5 = TvIntentClassifier.classify("how do airplanes fly", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res5.type);
    }

    @Test
    public void testGoogleAiFallbackWeather() {
        TvIntent res1 = TvIntentClassifier.classify("what's the weather", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res1.type);

        TvIntent res2 = TvIntentClassifier.classify("is it raining", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res2.type);

        TvIntent res3 = TvIntentClassifier.classify("temperature tomorrow", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res3.type);
    }

    @Test
    public void testGoogleAiFallbackChitChat() {
        TvIntent res1 = TvIntentClassifier.classify("tell me a joke", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res1.type);

        TvIntent res2 = TvIntentClassifier.classify("good morning", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res2.type);

        TvIntent res3 = TvIntentClassifier.classify("are you alive", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res3.type);

        TvIntent res4 = TvIntentClassifier.classify("can you fly", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res4.type);
    }

    @Test
    public void testGoogleAiFallbackExplicitSearch() {
        TvIntent res1 = TvIntentClassifier.classify("search for quantum computing", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res1.type);

        TvIntent res2 = TvIntentClassifier.classify("google latest sports scores", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res2.type);
    }

    @Test
    public void testQuestionWithKeywordsNotTriggeringActions() {
        TvIntent resSettings = TvIntentClassifier.classify("what are the settings for picture mode", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, resSettings.type);

        TvIntent resHome = TvIntentClassifier.classify("where is my home", appLookup);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, resHome.type);
    }
}
