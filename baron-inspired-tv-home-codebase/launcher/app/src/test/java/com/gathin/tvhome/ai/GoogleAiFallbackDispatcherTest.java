package com.gathin.tvhome.ai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit test suite for GoogleAiFallbackDispatcher.
 * Verifies the 5-tier intent fallback ladder, intent parameter specification,
 * URL encoding, tier progression, error handling, and graceful degradation.
 */
public class GoogleAiFallbackDispatcherTest {

    @Test
    public void testVoiceAssistSpecParameters() {
        String query = "what is the capital of France";
        String pkg = "com.gathin.tvhome";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createVoiceAssistSpec(query, pkg);

        assertEquals(GoogleAiFallbackDispatcher.TIER_VOICE_ASSIST, spec.tier);
        assertEquals(GoogleAiFallbackDispatcher.ACTION_VOICE_ASSIST, spec.action);
        assertNull(spec.targetPackage);
        assertNull(spec.dataUri);
        assertNull(spec.category);
        assertEquals(GoogleAiFallbackDispatcher.FLAG_ACTIVITY_NEW_TASK, spec.flags);

        assertEquals(query, spec.extras.get(GoogleAiFallbackDispatcher.EXTRA_QUERY));
        assertEquals(query, spec.extras.get(GoogleAiFallbackDispatcher.EXTRA_USER_QUERY));
        assertEquals(query, spec.extras.get(GoogleAiFallbackDispatcher.EXTRA_TEXT));
        assertEquals(pkg, spec.extras.get(GoogleAiFallbackDispatcher.EXTRA_ASSIST_PACKAGE));
    }

    @Test
    public void testSystemAssistSpecParameters() {
        String query = "who won the cricket world cup";
        String pkg = "com.gathin.tvhome";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createSystemAssistSpec(query, pkg);

        assertEquals(GoogleAiFallbackDispatcher.TIER_SYSTEM_ASSIST, spec.tier);
        assertEquals(GoogleAiFallbackDispatcher.ACTION_ASSIST, spec.action);
        assertNull(spec.targetPackage);
        assertEquals(query, spec.extras.get(GoogleAiFallbackDispatcher.EXTRA_QUERY));
        assertEquals(pkg, spec.extras.get(GoogleAiFallbackDispatcher.EXTRA_ASSIST_PACKAGE));
        assertEquals(GoogleAiFallbackDispatcher.FLAG_ACTIVITY_NEW_TASK, spec.flags);
    }

    @Test
    public void testKatnissSpecParameters() {
        String query = "weather in Tokyo";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createKatnissSearchSpec(query);

        assertEquals(GoogleAiFallbackDispatcher.TIER_KATNISS, spec.tier);
        assertEquals(GoogleAiFallbackDispatcher.ACTION_SEARCH, spec.action);
        assertEquals(GoogleAiFallbackDispatcher.PACKAGE_KATNISS, spec.targetPackage);
        assertNull(spec.dataUri);
        assertEquals(query, spec.extras.get(GoogleAiFallbackDispatcher.EXTRA_QUERY));
        assertEquals(GoogleAiFallbackDispatcher.FLAG_ACTIVITY_NEW_TASK, spec.flags);
    }

    @Test
    public void testWebSearchSpecParameters() {
        String query = "how to make pasta";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createWebSearchSpec(query);

        assertEquals(GoogleAiFallbackDispatcher.TIER_WEB_SEARCH, spec.tier);
        assertEquals(GoogleAiFallbackDispatcher.ACTION_WEB_SEARCH, spec.action);
        assertNull(spec.targetPackage);
        assertEquals(query, spec.extras.get(GoogleAiFallbackDispatcher.EXTRA_QUERY));
        assertEquals(GoogleAiFallbackDispatcher.FLAG_ACTIVITY_NEW_TASK, spec.flags);
    }

    @Test
    public void testBrowserSearchSpecParametersAndEncoding() {
        String query = "what's the weather in New York & London?";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createBrowserSearchSpec(query);

        assertEquals(GoogleAiFallbackDispatcher.TIER_BROWSER, spec.tier);
        assertEquals(GoogleAiFallbackDispatcher.ACTION_VIEW, spec.action);
        assertEquals(GoogleAiFallbackDispatcher.CATEGORY_BROWSABLE, spec.category);
        assertNotNull(spec.dataUri);
        assertTrue(spec.dataUri.startsWith("https://www.google.com/search?q="));
        assertTrue(spec.dataUri.contains("weather"));
        assertTrue(spec.extras.isEmpty());
        assertEquals(GoogleAiFallbackDispatcher.FLAG_ACTIVITY_NEW_TASK, spec.flags);
    }

    @Test
    public void testLadderTier1SelectedWhenAvailable() {
        FakeActivityLauncher launcher = new FakeActivityLauncher();
        launcher.resolvableTiers.add(GoogleAiFallbackDispatcher.TIER_VOICE_ASSIST);

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "tell me a joke", "com.gathin.tvhome", launcher);

        assertEquals("ok", result.status);
        assertEquals("google_fallback", result.action);
        assertEquals(GoogleAiFallbackDispatcher.TIER_VOICE_ASSIST, result.target);
        assertTrue(result.isFallback);
        assertEquals(1, launcher.launchedTiers.size());
        assertEquals(GoogleAiFallbackDispatcher.TIER_VOICE_ASSIST, launcher.launchedTiers.get(0));
    }

    @Test
    public void testLadderTier2SelectedWhenTier1Unavailable() {
        FakeActivityLauncher launcher = new FakeActivityLauncher();
        launcher.resolvableTiers.add(GoogleAiFallbackDispatcher.TIER_SYSTEM_ASSIST);

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "tell me a joke", "com.gathin.tvhome", launcher);

        assertEquals("ok", result.status);
        assertEquals(GoogleAiFallbackDispatcher.TIER_SYSTEM_ASSIST, result.target);
        assertEquals(1, launcher.launchedTiers.size());
        assertEquals(GoogleAiFallbackDispatcher.TIER_SYSTEM_ASSIST, launcher.launchedTiers.get(0));
    }

    @Test
    public void testLadderKatnissSelectedWhenTiers1And2Unavailable() {
        FakeActivityLauncher launcher = new FakeActivityLauncher();
        launcher.resolvableTiers.add(GoogleAiFallbackDispatcher.TIER_KATNISS);

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "latest sports news", "com.gathin.tvhome", launcher);

        assertEquals("ok", result.status);
        assertEquals(GoogleAiFallbackDispatcher.TIER_KATNISS, result.target);
        assertEquals(1, launcher.launchedTiers.size());
        assertEquals(GoogleAiFallbackDispatcher.TIER_KATNISS, launcher.launchedTiers.get(0));
    }

    @Test
    public void testLadderWebSearchSelectedWhenTiers1To3Unavailable() {
        FakeActivityLauncher launcher = new FakeActivityLauncher();
        launcher.resolvableTiers.add(GoogleAiFallbackDispatcher.TIER_WEB_SEARCH);

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "stock price of Google", "com.gathin.tvhome", launcher);

        assertEquals("ok", result.status);
        assertEquals(GoogleAiFallbackDispatcher.TIER_WEB_SEARCH, result.target);
        assertEquals(1, launcher.launchedTiers.size());
        assertEquals(GoogleAiFallbackDispatcher.TIER_WEB_SEARCH, launcher.launchedTiers.get(0));
    }

    @Test
    public void testLadderBrowserSelectedWhenTiers1To4Unavailable() {
        FakeActivityLauncher launcher = new FakeActivityLauncher();
        launcher.resolvableTiers.add(GoogleAiFallbackDispatcher.TIER_BROWSER);

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "photos of galaxies", "com.gathin.tvhome", launcher);

        assertEquals("ok", result.status);
        assertEquals(GoogleAiFallbackDispatcher.TIER_BROWSER, result.target);
        assertEquals(1, launcher.launchedTiers.size());
        assertEquals(GoogleAiFallbackDispatcher.TIER_BROWSER, launcher.launchedTiers.get(0));
    }

    @Test
    public void testLadderGracefulDegradationWhenAllTiersUnavailable() {
        FakeActivityLauncher launcher = new FakeActivityLauncher();
        // Nothing is resolvable

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "quantum mechanics", "com.gathin.tvhome", launcher);

        assertEquals("unavailable", result.status);
        assertEquals("google_fallback", result.action);
        assertNull(result.target);
        assertTrue(result.isFallback);
        assertTrue(launcher.launchedTiers.isEmpty());
        assertNotNull(result.message);
    }

    @Test
    public void testLadderSkipsTierWhenLaunchFails() {
        FakeActivityLauncher launcher = new FakeActivityLauncher();
        launcher.resolvableTiers.add(GoogleAiFallbackDispatcher.TIER_VOICE_ASSIST);
        launcher.failingTiers.add(GoogleAiFallbackDispatcher.TIER_VOICE_ASSIST);
        launcher.resolvableTiers.add(GoogleAiFallbackDispatcher.TIER_SYSTEM_ASSIST);

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "good morning", "com.gathin.tvhome", launcher);

        assertEquals("ok", result.status);
        assertEquals(GoogleAiFallbackDispatcher.TIER_SYSTEM_ASSIST, result.target);
        assertEquals(2, launcher.launchedTiers.size());
        assertEquals(GoogleAiFallbackDispatcher.TIER_VOICE_ASSIST, launcher.launchedTiers.get(0));
        assertEquals(GoogleAiFallbackDispatcher.TIER_SYSTEM_ASSIST, launcher.launchedTiers.get(1));
    }

    @Test
    public void testNullOrEmptyQueryReturnsError() {
        FakeActivityLauncher launcher = new FakeActivityLauncher();
        launcher.resolvableTiers.add(GoogleAiFallbackDispatcher.TIER_VOICE_ASSIST);

        ExecutionResult resNull = GoogleAiFallbackDispatcher.dispatch(null, "com.gathin.tvhome", launcher);
        assertEquals("error", resNull.status);

        ExecutionResult resEmpty = GoogleAiFallbackDispatcher.dispatch("   ", "com.gathin.tvhome", launcher);
        assertEquals("error", resEmpty.status);

        assertTrue(launcher.launchedTiers.isEmpty());
    }

    private static class FakeActivityLauncher implements GoogleAiFallbackDispatcher.ActivityLauncher {
        final Set<String> resolvableTiers = new HashSet<>();
        final Set<String> failingTiers = new HashSet<>();
        final List<String> launchedTiers = new ArrayList<>();

        @Override
        public boolean isResolvable(GoogleAiFallbackDispatcher.FallbackIntentSpec spec) {
            return resolvableTiers.contains(spec.tier);
        }

        @Override
        public boolean launch(GoogleAiFallbackDispatcher.FallbackIntentSpec spec) {
            launchedTiers.add(spec.tier);
            if (failingTiers.contains(spec.tier)) {
                return false;
            }
            return true;
        }
    }
}
