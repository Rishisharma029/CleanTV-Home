package com.gathin.tvhome.ai;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tier 4: Real-World Application Scenarios Test Suite.
 * Validates realistic, multi-step user voice and text interaction sessions.
 */
public class Tier4RealWorldScenarioTest {

    private Map<String, String> appMap;

    @Before
    public void setUp() {
        appMap = AiDriver.standardTvApps();
    }

    // ==========================================
    // Scenario 1: Morning Entertainment Session
    // ==========================================
    @Test
    public void scenario1_morningEntertainmentSession() {
        // Step 1: User sets moderate morning volume
        AiDriver.AiIntentResult step1 = AiDriver.classify("set volume to 25%", appMap);
        assertEquals("VOLUME_CONTROL", step1.type);
        assertEquals(Integer.valueOf(25), step1.getParamInt("level"));

        // Step 2: User opens YouTube for morning news
        AiDriver.AiIntentResult step2 = AiDriver.classify("open YouTube", appMap);
        assertEquals("LAUNCH_APP", step2.type);
        assertEquals("com.google.android.youtube.tv", step2.getParamString("package"));

        // Step 3: Video starts playing
        AiDriver.AiIntentResult step3 = AiDriver.classify("play", appMap);
        assertEquals("MEDIA_CONTROL", step3.type);
        assertEquals("play", step3.action);

        // Step 4: User skips an ad / segment
        AiDriver.AiIntentResult step4 = AiDriver.classify("skip", appMap);
        assertEquals("MEDIA_CONTROL", step4.type);

        // Step 5: User raises volume slightly
        AiDriver.AiIntentResult step5 = AiDriver.classify("louder", appMap);
        assertEquals("VOLUME_CONTROL", step5.type);
        assertEquals(Integer.valueOf(1), step5.getParamInt("direction"));
    }

    // ==========================================
    // Scenario 2: Night-Time Movie Routine
    // ==========================================
    @Test
    public void scenario2_nightTimeMovieRoutine() {
        // Step 1: User opens Netflix
        AiDriver.AiIntentResult step1 = AiDriver.classify("launch Netflix", appMap);
        assertEquals("LAUNCH_APP", step1.type);
        assertEquals("com.netflix.ninja", step1.getParamString("package"));

        // Step 2: Sets bedtime volume level
        AiDriver.AiIntentResult step2 = AiDriver.classify("set volume to 18%", appMap);
        assertEquals("VOLUME_CONTROL", step2.type);
        assertEquals(Integer.valueOf(18), step2.getParamInt("level"));

        // Step 3: Sets sleep timer for 30 minutes
        AiDriver.AiIntentResult step3 = AiDriver.classify("sleep timer", appMap);
        assertEquals("LAUNCHER_FEATURE", step3.type);
        assertEquals("sleep_timer", step3.action);

        // Step 4: Pauses for a break
        AiDriver.AiIntentResult step4 = AiDriver.classify("pause", appMap);
        assertEquals("MEDIA_CONTROL", step4.type);

        // Step 5: Resumes video
        AiDriver.AiIntentResult step5 = AiDriver.classify("resume", appMap);
        assertEquals("MEDIA_CONTROL", step5.type);

        // Step 6: Returns home when done
        AiDriver.AiIntentResult step6 = AiDriver.classify("go home", appMap);
        assertEquals("SYSTEM_ACTION", step6.type);
        assertEquals("home", step6.action);
    }

    // ==========================================
    // Scenario 3: Kids Guard Parental Journey
    // ==========================================
    @Test
    public void scenario3_kidsGuardParentalJourney() {
        // Step 1: Parent switches profile to Kids Mode
        AiDriver.AiIntentResult step1 = AiDriver.classify("kids mode", appMap);
        assertEquals("LAUNCHER_FEATURE", step1.type);
        assertEquals("profile_switcher", step1.action);

        // Step 2: Child attempts to open YouTube
        AiDriver.AiIntentResult step2 = AiDriver.classify("open YouTube", appMap);
        assertEquals("LAUNCH_APP", step2.type);
        assertEquals("com.google.android.youtube.tv", step2.getParamString("package"));

        // Step 3: Child opens allowed media app (Kodi)
        AiDriver.AiIntentResult step3 = AiDriver.classify("open Kodi", appMap);
        assertEquals("LAUNCH_APP", step3.type);
        assertEquals("org.xbmc.kodi", step3.getParamString("package"));

        // Step 4: Child mutes the audio
        AiDriver.AiIntentResult step4 = AiDriver.classify("mute", appMap);
        assertEquals("VOLUME_CONTROL", step4.type);

        // Step 5: Child goes back home
        AiDriver.AiIntentResult step5 = AiDriver.classify("go home", appMap);
        assertEquals("SYSTEM_ACTION", step5.type);
        assertEquals("home", step5.action);
    }

    // ==========================================
    // Scenario 4: Assistant Knowledge Exploration & TV Return
    // ==========================================
    @Test
    public void scenario4_assistantKnowledgeExploration() {
        // Step 1: User asks weather question (routed to Google AI Fallback)
        AiDriver.AiIntentResult step1 = AiDriver.classify("what is the weather in New York today?", appMap);
        assertEquals("GOOGLE_AI_FALLBACK", step1.type);

        // Step 2: User asks movie director question (routed to Google AI Fallback)
        AiDriver.AiIntentResult step2 = AiDriver.classify("who directed Oppenheimer?", appMap);
        assertEquals("GOOGLE_AI_FALLBACK", step2.type);

        // Step 3: User asks astronomy question (routed to Google AI Fallback)
        AiDriver.AiIntentResult step3 = AiDriver.classify("how far is Mars from the sun?", appMap);
        assertEquals("GOOGLE_AI_FALLBACK", step3.type);

        // Step 4: User seamlessly transitions back to local TV music app
        AiDriver.AiIntentResult step4 = AiDriver.classify("open Spotify", appMap);
        assertEquals("LAUNCH_APP", step4.type);
        assertEquals("com.spotify.tv.android", step4.getParamString("package"));

        // Step 5: User controls music playback
        AiDriver.AiIntentResult step5 = AiDriver.classify("play", appMap);
        assertEquals("MEDIA_CONTROL", step5.type);
        assertEquals("play", step5.action);
    }

    // ==========================================
    // Scenario 5: Gaming Console Setup Session
    // ==========================================
    @Test
    public void scenario5_gamingConsoleSetupSession() {
        // Step 1: User switches TV input to gaming console
        AiDriver.AiIntentResult step1 = AiDriver.classify("tv inputs", appMap);
        assertEquals("SYSTEM_ACTION", step1.type);
        assertEquals("inputs", step1.action);

        // Step 2: User enables low latency Game Mode
        AiDriver.AiIntentResult step2 = AiDriver.classify("game mode", appMap);
        assertEquals("LAUNCHER_FEATURE", step2.type);
        assertEquals("game_mode", step2.action);

        // Step 3: User sets gaming audio volume
        AiDriver.AiIntentResult step3 = AiDriver.classify("set volume to 35%", appMap);
        assertEquals("VOLUME_CONTROL", step3.type);
        assertEquals(Integer.valueOf(35), step3.getParamInt("level"));

        // Step 4: User verifies unmuted status
        AiDriver.AiIntentResult step4 = AiDriver.classify("unmute", appMap);
        assertEquals("VOLUME_CONTROL", step4.type);
    }

    // ==========================================
    // Scenario 6: System Connectivity & Audio Setup
    // ==========================================
    @Test
    public void scenario6_systemConnectivitySetup() {
        // Step 1: First-time user connects to Wi-Fi
        AiDriver.AiIntentResult step1 = AiDriver.classify("open wifi", appMap);
        assertEquals("SYSTEM_ACTION", step1.type);
        assertEquals("wifi", step1.action);

        // Step 2: User pairs Bluetooth soundbar / remote
        AiDriver.AiIntentResult step2 = AiDriver.classify("pair remote", appMap);
        assertEquals("SYSTEM_ACTION", step2.type);
        assertEquals("bluetooth", step2.action);

        // Step 3: User inspects general settings
        AiDriver.AiIntentResult step3 = AiDriver.classify("system settings", appMap);
        assertEquals("SYSTEM_ACTION", step3.type);
        assertEquals("settings", step3.action);

        // Step 4: Returns home
        AiDriver.AiIntentResult step4 = AiDriver.classify("home", appMap);
        assertEquals("SYSTEM_ACTION", step4.type);
        assertEquals("home", step4.action);
    }

    // ==========================================
    // Scenario 7: Late-Night Casual Browsing Session
    // ==========================================
    @Test
    public void scenario7_lateNightBrowsingSession() {
        // Step 1: User turns down volume
        AiDriver.AiIntentResult step1 = AiDriver.classify("turn it down", appMap);
        assertEquals("VOLUME_CONTROL", step1.type);
        assertEquals(Integer.valueOf(-1), step1.getParamInt("direction"));

        // Step 2: Starts screensaver artwork while reading
        AiDriver.AiIntentResult step2 = AiDriver.classify("screensaver", appMap);
        assertEquals("LAUNCHER_FEATURE", step2.type);
        assertEquals("screensaver", step2.action);

        // Step 3: Opens control center to check clock/status
        AiDriver.AiIntentResult step3 = AiDriver.classify("control center", appMap);
        assertEquals("LAUNCHER_FEATURE", step3.type);
        assertEquals("control_center", step3.action);

        // Step 4: Launches video player
        AiDriver.AiIntentResult step4 = AiDriver.classify("open VLC", appMap);
        assertEquals("LAUNCH_APP", step4.type);
        assertEquals("org.videolan.vlc", step4.getParamString("package"));
    }
}
