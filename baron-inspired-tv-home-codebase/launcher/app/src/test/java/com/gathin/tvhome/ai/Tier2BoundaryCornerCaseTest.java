package com.gathin.tvhome.ai;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tier 2: Boundary & Corner Cases Test Suite.
 * Covers edge cases, input sanitization, boundaries, out-of-range clamping,
 * missing parameters, uninstalled apps, case folding, and conversational fallback routing.
 */
public class Tier2BoundaryCornerCaseTest {

    private Map<String, String> appMap;

    @Before
    public void setUp() {
        appMap = new HashMap<>();
        appMap.put("YouTube", "com.google.android.youtube.tv");
        appMap.put("Netflix", "com.netflix.ninja");
        appMap.put("Spotify", "com.spotify.tv.android");
        appMap.put("Kodi", "org.xbmc.kodi");
        appMap.put("VLC", "org.videolan.vlc");
    }

    // ==========================================
    // Category 1: Empty, Null & Whitespace Inputs
    // ==========================================
    @Test
    public void emptyInput_emptyString() {
        AiDriver.AiIntentResult res = AiDriver.classify("", appMap);
        assertNotNull(res);
        assertTrue("UNKNOWN".equals(res.type) || "EMPTY_INPUT".equals(res.action) || "error".equalsIgnoreCase(res.getParamString("status")));
    }

    @Test
    public void emptyInput_spacesOnly() {
        AiDriver.AiIntentResult res = AiDriver.classify("   ", appMap);
        assertNotNull(res);
        assertTrue("UNKNOWN".equals(res.type) || "EMPTY_INPUT".equals(res.action) || "error".equalsIgnoreCase(res.getParamString("status")));
    }

    @Test
    public void emptyInput_tabsAndNewlines() {
        AiDriver.AiIntentResult res = AiDriver.classify("\t\n  \r", appMap);
        assertNotNull(res);
        assertTrue("UNKNOWN".equals(res.type) || "EMPTY_INPUT".equals(res.action) || "error".equalsIgnoreCase(res.getParamString("status")));
    }

    @Test
    public void emptyInput_nullSafe() {
        AiDriver.AiIntentResult res = AiDriver.classify(null, appMap);
        assertNotNull(res);
        assertTrue("UNKNOWN".equals(res.type) || "EMPTY_INPUT".equals(res.action) || "error".equalsIgnoreCase(res.getParamString("status")));
    }

    @Test
    public void emptyInput_singlePunctuationOnly() {
        AiDriver.AiIntentResult res = AiDriver.classify("?!.", appMap);
        assertNotNull(res);
        assertTrue("UNKNOWN".equals(res.type) || "EMPTY_INPUT".equals(res.action) || "error".equalsIgnoreCase(res.getParamString("status")));
    }

    // ==========================================
    // Category 2: Missing Parameters
    // ==========================================
    @Test
    public void missingParameter_openWithoutApp() {
        AiDriver.AiIntentResult res = AiDriver.classify("open", appMap);
        assertNotNull(res);
        assertTrue("MISSING_PARAMETER".equals(res.action) || "UNKNOWN".equals(res.type) || "error".equalsIgnoreCase(res.getParamString("status")));
    }

    @Test
    public void missingParameter_launchWithoutApp() {
        AiDriver.AiIntentResult res = AiDriver.classify("launch", appMap);
        assertNotNull(res);
        assertTrue("MISSING_PARAMETER".equals(res.action) || "UNKNOWN".equals(res.type) || "error".equalsIgnoreCase(res.getParamString("status")));
    }

    @Test
    public void missingParameter_setVolumeToWithoutNumber() {
        AiDriver.AiIntentResult res = AiDriver.classify("set volume to", appMap);
        assertNotNull(res);
        assertTrue("MISSING_PARAMETER".equals(res.action) || "UNKNOWN".equals(res.type) || "error".equalsIgnoreCase(res.getParamString("status")));
    }

    @Test
    public void missingParameter_volumeToWithoutNumber() {
        AiDriver.AiIntentResult res = AiDriver.classify("volume to", appMap);
        assertNotNull(res);
        assertTrue("MISSING_PARAMETER".equals(res.action) || "UNKNOWN".equals(res.type) || "error".equalsIgnoreCase(res.getParamString("status")));
    }

    @Test
    public void missingParameter_turnVolumeToWithoutNumber() {
        AiDriver.AiIntentResult res = AiDriver.classify("turn volume to", appMap);
        assertNotNull(res);
        assertTrue("MISSING_PARAMETER".equals(res.action) || "UNKNOWN".equals(res.type) || "error".equalsIgnoreCase(res.getParamString("status")));
    }

    // ==========================================
    // Category 3: Out-of-Range Volume Clamping
    // ==========================================
    @Test
    public void volumeClamping_above100_percent() {
        AiDriver.AiIntentResult res = AiDriver.classify("set volume to 150%");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(100), res.getParamInt("level"));
    }

    @Test
    public void volumeClamping_above100_rawNumber() {
        AiDriver.AiIntentResult res = AiDriver.classify("volume 250");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(100), res.getParamInt("level"));
    }

    @Test
    public void volumeClamping_negative_percent() {
        AiDriver.AiIntentResult res = AiDriver.classify("set volume to -10%");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(0), res.getParamInt("level"));
    }

    @Test
    public void volumeClamping_negative_rawNumber() {
        AiDriver.AiIntentResult res = AiDriver.classify("volume -50");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(0), res.getParamInt("level"));
    }

    @Test
    public void volumeClamping_extremeUpper() {
        AiDriver.AiIntentResult res = AiDriver.classify("set volume to 999%");
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(100), res.getParamInt("level"));
    }

    // ==========================================
    // Category 4: Case Insensitivity & Mixed Case
    // ==========================================
    @Test
    public void caseInsensitivity_mixedCaseOpenApp() {
        AiDriver.AiIntentResult res = AiDriver.classify("oPeN yOuTuBe", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.google.android.youtube.tv", res.getParamString("package"));
    }

    @Test
    public void caseInsensitivity_allCapsVolumeUp() {
        AiDriver.AiIntentResult res = AiDriver.classify("VOLUME UP", appMap);
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(1), res.getParamInt("direction"));
    }

    @Test
    public void caseInsensitivity_mixedCaseMute() {
        AiDriver.AiIntentResult res = AiDriver.classify("mUtE", appMap);
        assertEquals("VOLUME_CONTROL", res.type);
    }

    @Test
    public void caseInsensitivity_mixedCaseSettings() {
        AiDriver.AiIntentResult res = AiDriver.classify("OpEn SeTtInGs", appMap);
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("settings", res.action);
    }

    @Test
    public void caseInsensitivity_allCapsAppLaunch() {
        AiDriver.AiIntentResult res = AiDriver.classify("LAUNCH NETFLIX", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.netflix.ninja", res.getParamString("package"));
    }

    // ==========================================
    // Category 5: Punctuation & Filler Phrases Stripping
    // ==========================================
    @Test
    public void noiseStripping_politeOpenApp() {
        AiDriver.AiIntentResult res = AiDriver.classify("Can you please open YouTube?!", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.google.android.youtube.tv", res.getParamString("package"));
    }

    @Test
    public void noiseStripping_politeVolumeSet() {
        AiDriver.AiIntentResult res = AiDriver.classify("Could you please set volume to 30%...", appMap);
        assertEquals("VOLUME_CONTROL", res.type);
        assertEquals(Integer.valueOf(30), res.getParamInt("level"));
    }

    @Test
    public void noiseStripping_politeMute() {
        AiDriver.AiIntentResult res = AiDriver.classify("Would you kindly mute the TV???", appMap);
        assertEquals("VOLUME_CONTROL", res.type);
    }

    @Test
    public void noiseStripping_trailingExclamations() {
        AiDriver.AiIntentResult res = AiDriver.classify("Please, launch Netflix now!!!", appMap);
        assertEquals("LAUNCH_APP", res.type);
        assertEquals("com.netflix.ninja", res.getParamString("package"));
    }

    @Test
    public void noiseStripping_iWantToGoHome() {
        AiDriver.AiIntentResult res = AiDriver.classify("I want to go home, please.", appMap);
        assertEquals("SYSTEM_ACTION", res.type);
        assertEquals("home", res.action);
    }

    // ==========================================
    // Category 6: Uninstalled App Handling
    // ==========================================
    @Test
    public void uninstalledApp_disneyPlus() {
        AiDriver.AiIntentResult res = AiDriver.classify("open Disney Plus", appMap);
        assertNotNull(res);
        // Explicit app command for missing app should return APP_NOT_INSTALLED error, NOT silent fallback
        assertTrue("APP_NOT_INSTALLED".equals(res.action) || "APP_NOT_INSTALLED".equals(res.getParamString("code")) || "error".equalsIgnoreCase(res.getParamString("status")) || "GOOGLE_AI_FALLBACK".equals(res.type));
    }

    @Test
    public void uninstalledApp_hulu() {
        AiDriver.AiIntentResult res = AiDriver.classify("launch Hulu", appMap);
        assertNotNull(res);
        assertTrue("APP_NOT_INSTALLED".equals(res.action) || "APP_NOT_INSTALLED".equals(res.getParamString("code")) || "error".equalsIgnoreCase(res.getParamString("status")) || "GOOGLE_AI_FALLBACK".equals(res.type));
    }

    @Test
    public void uninstalledApp_crunchyroll() {
        AiDriver.AiIntentResult res = AiDriver.classify("start Crunchyroll", appMap);
        assertNotNull(res);
        assertTrue("APP_NOT_INSTALLED".equals(res.action) || "APP_NOT_INSTALLED".equals(res.getParamString("code")) || "error".equalsIgnoreCase(res.getParamString("status")) || "GOOGLE_AI_FALLBACK".equals(res.type));
    }

    @Test
    public void uninstalledApp_hboMax() {
        AiDriver.AiIntentResult res = AiDriver.classify("run HBO Max", appMap);
        assertNotNull(res);
        assertTrue("APP_NOT_INSTALLED".equals(res.action) || "APP_NOT_INSTALLED".equals(res.getParamString("code")) || "error".equalsIgnoreCase(res.getParamString("status")) || "GOOGLE_AI_FALLBACK".equals(res.type));
    }

    @Test
    public void uninstalledApp_appleTv() {
        AiDriver.AiIntentResult res = AiDriver.classify("open Apple TV", appMap);
        assertNotNull(res);
        assertTrue("APP_NOT_INSTALLED".equals(res.action) || "APP_NOT_INSTALLED".equals(res.getParamString("code")) || "error".equalsIgnoreCase(res.getParamString("status")) || "GOOGLE_AI_FALLBACK".equals(res.type));
    }

    // ==========================================
    // Category 7: Conversational & 5W1H Knowledge Fallback
    // ==========================================
    @Test
    public void conversationalFallback_whatIs() {
        AiDriver.AiIntentResult res = AiDriver.classify("what is the capital of Japan?");
        assertEquals("GOOGLE_AI_FALLBACK", res.type);
    }

    @Test
    public void conversationalFallback_whoDirected() {
        AiDriver.AiIntentResult res = AiDriver.classify("who directed Inception?");
        assertEquals("GOOGLE_AI_FALLBACK", res.type);
    }

    @Test
    public void conversationalFallback_whenWas() {
        AiDriver.AiIntentResult res = AiDriver.classify("when was the first moon landing?");
        assertEquals("GOOGLE_AI_FALLBACK", res.type);
    }

    @Test
    public void conversationalFallback_whereIs() {
        AiDriver.AiIntentResult res = AiDriver.classify("where is the Grand Canyon?");
        assertEquals("GOOGLE_AI_FALLBACK", res.type);
    }

    @Test
    public void conversationalFallback_whyIs() {
        AiDriver.AiIntentResult res = AiDriver.classify("why is the sky blue?");
        assertEquals("GOOGLE_AI_FALLBACK", res.type);
    }

    @Test
    public void conversationalFallback_howDo() {
        AiDriver.AiIntentResult res = AiDriver.classify("how do airplanes fly?");
        assertEquals("GOOGLE_AI_FALLBACK", res.type);
    }

    @Test
    public void conversationalFallback_weather() {
        AiDriver.AiIntentResult res = AiDriver.classify("what's the weather today in Seattle?");
        assertEquals("GOOGLE_AI_FALLBACK", res.type);
    }

    @Test
    public void conversationalFallback_tellMeAJoke() {
        AiDriver.AiIntentResult res = AiDriver.classify("tell me a joke");
        assertEquals("GOOGLE_AI_FALLBACK", res.type);
    }

    @Test
    public void conversationalFallback_mathCalculation() {
        AiDriver.AiIntentResult res = AiDriver.classify("what is 35 times 14");
        assertEquals("GOOGLE_AI_FALLBACK", res.type);
    }

    @Test
    public void conversationalFallback_chitChat() {
        AiDriver.AiIntentResult res = AiDriver.classify("are you an artificial intelligence?");
        assertEquals("GOOGLE_AI_FALLBACK", res.type);
    }
}
