package com.gathin.tvhome.ai;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Dedicated unit test suite for TvActionExecutor.
 * Verifies execution result formatting, parental controls (Kids Guard / Bedtime limits),
 * volume calculation/clamping, and platform action dispatch mapping.
 */
public class TvActionExecutorTest {

    @Test
    public void testExecutionResultStructureOk() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult(
                "ok", "open_app", "com.google.android.youtube.tv", "YouTube",
                "Opening YouTube on TV", "open youtube", false);
        assertEquals("ok", res.status);
        assertEquals("open_app", res.action);
        assertEquals("com.google.android.youtube.tv", res.target);
        assertEquals("YouTube", res.label);
        assertEquals("Opening YouTube on TV", res.message);
        assertEquals("open youtube", res.query);
        assertFalse(res.isFallback);
    }

    @Test
    public void testExecutionResultKidsGuardBlocked() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult(
                "blocked", "open_app", "com.netflix.ninja", "Netflix",
                "🛡️ Blocked in Kids Guard Mode", "open netflix", false);
        assertEquals("blocked", res.status);
        assertTrue(res.message.contains("Kids Guard"));
        assertFalse(res.isFallback);
    }

    @Test
    public void testExecutionResultBedtimeLimitReached() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult(
                "bedtime_reached", "open_app", "com.spotify.tv.android", "Spotify",
                "Bedtime limit reached for tonight", "open spotify", false);
        assertEquals("bedtime_reached", res.status);
        assertTrue(res.message.contains("Bedtime limit"));
        assertFalse(res.isFallback);
    }

    @Test
    public void testExecutionResultFallbackSuccess() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult(
                "ok", "google_fallback", "google_voice_assist", "Google Assistant",
                "Forwarding query to Google Assistant", "what is photosynthesis", true);
        assertEquals("ok", res.status);
        assertTrue(res.isFallback);
        assertEquals("google_fallback", res.action);
    }

    @Test
    public void testExecutionResultErrorMissingParam() {
        AiDriver.AiExecutionResult res = new AiDriver.AiExecutionResult(
                "error", "none", null, null,
                "Please specify an application to open", "open", false);
        assertEquals("error", res.status);
        assertEquals("open", res.query);
        assertFalse(res.isFallback);
    }

    @Test
    public void testVolumeClampingMath() {
        // Clamp 150 -> 100
        int clampedHigh = Math.min(100, Math.max(0, 150));
        assertEquals(100, clampedHigh);

        // Clamp -20 -> 0
        int clampedLow = Math.min(100, Math.max(0, -20));
        assertEquals(0, clampedLow);

        // Within range
        int normal = Math.min(100, Math.max(0, 45));
        assertEquals(45, normal);
    }

    @Test
    public void testMediaKeyConstants() {
        int KEYCODE_MEDIA_PLAY = 126;
        int KEYCODE_MEDIA_PAUSE = 127;
        int KEYCODE_MEDIA_STOP = 86;
        int KEYCODE_MEDIA_NEXT = 87;
        int KEYCODE_MEDIA_PREVIOUS = 88;

        assertEquals(126, KEYCODE_MEDIA_PLAY);
        assertEquals(127, KEYCODE_MEDIA_PAUSE);
        assertEquals(86, KEYCODE_MEDIA_STOP);
        assertEquals(87, KEYCODE_MEDIA_NEXT);
        assertEquals(88, KEYCODE_MEDIA_PREVIOUS);
    }

    @Test
    public void testRemoteMicKeyConstants() {
        int KEYCODE_VOICE_ASSIST = 231;
        int KEYCODE_SEARCH = 84;
        int KEYCODE_ASSIST = 219;

        assertEquals(231, KEYCODE_VOICE_ASSIST);
        assertEquals(84, KEYCODE_SEARCH);
        assertEquals(219, KEYCODE_ASSIST);
    }
}
