package com.gathin.tvhome.ai;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Adversarial stress harness and empirical fault injection suite
 * for GoogleAiFallbackDispatcher and fallback ladder contracts.
 */
public class AdversarialFallbackTest {

    // =========================================================================
    // 1. TIER-BY-TIER CONTRACT & SPECIFICATION VERIFICATION (Tiers 1 to 6)
    // =========================================================================

    @Test
    public void testTier1_VoiceAssist_CompleteSpec() {
        String query = "open the pod bay doors";
        String pkg = "com.gathin.tvhome";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createVoiceAssistSpec(query, pkg);

        assertEquals("voice_assist", spec.tier);
        assertEquals("android.intent.action.VOICE_ASSIST", spec.action);
        assertNull(spec.targetPackage);
        assertNull(spec.dataUri);
        assertNull(spec.category);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, spec.flags);

        // Required Extras
        assertEquals(query, spec.extras.get(SearchManager.QUERY));
        assertEquals(query, spec.extras.get(SearchManager.USER_QUERY));
        assertEquals(query, spec.extras.get(Intent.EXTRA_TEXT));
        assertEquals(pkg, spec.extras.get("android.intent.extra.ASSIST_PACKAGE"));
    }

    @Test
    public void testTier2_SystemAssist_CompleteSpec() {
        String query = "what is the meaning of life";
        String pkg = "com.gathin.tvhome";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createSystemAssistSpec(query, pkg);

        assertEquals("system_assist", spec.tier);
        assertEquals(Intent.ACTION_ASSIST, spec.action);
        assertNull(spec.targetPackage);
        assertNull(spec.dataUri);
        assertNull(spec.category);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, spec.flags);

        assertEquals(query, spec.extras.get(SearchManager.QUERY));
        assertEquals(query, spec.extras.get(SearchManager.USER_QUERY));
        assertEquals(query, spec.extras.get(Intent.EXTRA_TEXT));
        assertEquals(pkg, spec.extras.get("android.intent.extra.ASSIST_PACKAGE"));
    }

    @Test
    public void testTier3_Katniss_ExplicitTargetAndExtras() {
        String query = "find action movies 2026";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createKatnissSearchSpec(query);

        assertEquals("katniss", spec.tier);
        assertEquals(Intent.ACTION_SEARCH, spec.action);
        assertEquals("com.google.android.katniss", spec.targetPackage);
        assertNull(spec.dataUri);
        assertNull(spec.category);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, spec.flags);

        assertEquals(query, spec.extras.get(SearchManager.QUERY));
        assertEquals(query, spec.extras.get(SearchManager.USER_QUERY));
        assertEquals(query, spec.extras.get(Intent.EXTRA_TEXT));
    }

    @Test
    public void testTier4_WebSearch_ActionAndExtras() {
        String query = "history of television technology";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createWebSearchSpec(query);

        assertEquals("web_search", spec.tier);
        assertEquals(Intent.ACTION_WEB_SEARCH, spec.action);
        assertNull(spec.targetPackage);
        assertNull(spec.dataUri);
        assertNull(spec.category);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, spec.flags);

        assertEquals(query, spec.extras.get(SearchManager.QUERY));
        assertEquals(query, spec.extras.get(SearchManager.USER_QUERY));
        assertEquals(query, spec.extras.get(Intent.EXTRA_TEXT));
    }

    @Test
    public void testTier5_Browser_UriEncodingAndBrowsableCategory() throws UnsupportedEncodingException {
        String query = "query with special characters: 100% & 50% = 150? <tag> \"quotes\"";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createBrowserSearchSpec(query);

        assertEquals("browser", spec.tier);
        assertEquals(Intent.ACTION_VIEW, spec.action);
        assertNull(spec.targetPackage);
        assertEquals(Intent.CATEGORY_BROWSABLE, spec.category);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, spec.flags);

        assertNotNull(spec.dataUri);
        assertTrue(spec.dataUri.startsWith("https://www.google.com/search?q="));
        String rawQueryParam = spec.dataUri.substring("https://www.google.com/search?q=".length());
        String decoded = URLDecoder.decode(rawQueryParam, StandardCharsets.UTF_8.name());
        assertEquals(query, decoded);
    }

    @Test
    public void testTier6_GracefulDegradation_AllTiersDisabled() {
        FaultyActivityLauncher launcher = new FaultyActivityLauncher();
        // Zero resolvable tiers
        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "why is the sky blue", "com.gathin.tvhome", launcher);

        assertNotNull(result);
        assertEquals("unavailable", result.status);
        assertEquals("google_fallback", result.action);
        assertNull(result.target);
        assertTrue(result.isFallback);
        assertEquals("why is the sky blue", result.query);
        assertNotNull(result.message);
        assertTrue(result.message.contains("unavailable") || result.message.contains("not available"));
        assertTrue(launcher.launchedTiers.isEmpty());
    }

    // =========================================================================
    // 2. FAULT INJECTION & EXCEPTION TOLERANCE
    // =========================================================================

    @Test
    public void testFaultInjection_SecurityExceptionDuringIsResolvable() {
        FaultyActivityLauncher launcher = new FaultyActivityLauncher();
        // Tier 1 throws SecurityException during isResolvable
        launcher.resolvableExceptions.put("voice_assist", new SecurityException("Permission denial for VOICE_ASSIST"));
        // Tier 2 is normal and resolvable
        launcher.resolvableTiers.add("system_assist");

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "turn lights off", "com.gathin.tvhome", launcher);

        assertEquals("ok", result.status);
        assertEquals("system_assist", result.target);
        assertEquals(1, launcher.launchedTiers.size());
        assertEquals("system_assist", launcher.launchedTiers.get(0));
    }

    @Test
    public void testFaultInjection_SecurityExceptionDuringLaunch() {
        FaultyActivityLauncher launcher = new FaultyActivityLauncher();
        launcher.resolvableTiers.add("voice_assist");
        launcher.launchExceptions.put("voice_assist", new SecurityException("Permission denied to start activity"));

        launcher.resolvableTiers.add("system_assist");
        launcher.launchExceptions.put("system_assist", new SecurityException("Permission denied 2"));

        launcher.resolvableTiers.add("katniss");

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "play jazz", "com.gathin.tvhome", launcher);

        assertEquals("ok", result.status);
        assertEquals("katniss", result.target);
        assertEquals(3, launcher.launchedTiers.size());
        assertEquals("voice_assist", launcher.launchedTiers.get(0));
        assertEquals("system_assist", launcher.launchedTiers.get(1));
        assertEquals("katniss", launcher.launchedTiers.get(2));
    }

    @Test
    public void testFaultInjection_ActivityNotFoundExceptionDuringLaunch() {
        FaultyActivityLauncher launcher = new FaultyActivityLauncher();
        launcher.resolvableTiers.add("voice_assist");
        launcher.launchExceptions.put("voice_assist", new ActivityNotFoundException("Activity not found"));
        launcher.resolvableTiers.add("system_assist");

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "tell me news", "com.gathin.tvhome", launcher);

        assertEquals("ok", result.status);
        assertEquals("system_assist", result.target);
        assertEquals(2, launcher.launchedTiers.size());
    }

    @Test
    public void testFaultInjection_NullPointerExceptionDuringIsResolvable() {
        FaultyActivityLauncher launcher = new FaultyActivityLauncher();
        launcher.resolvableExceptions.put("voice_assist", new NullPointerException("Simulated corrupted PM"));
        launcher.resolvableTiers.add("system_assist");

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "tell me news", "com.gathin.tvhome", launcher);

        assertEquals("ok", result.status);
        assertEquals("system_assist", result.target);
    }

    @Test
    public void testFaultInjection_EveryTierThrowsSecurityException_DegradesGracefully() {
        FaultyActivityLauncher launcher = new FaultyActivityLauncher();
        String[] allTiers = {"voice_assist", "system_assist", "katniss", "web_search", "browser"};
        for (String tier : allTiers) {
            launcher.resolvableTiers.add(tier);
            launcher.launchExceptions.put(tier, new SecurityException("Lockdown security sandbox exception"));
        }

        ExecutionResult result = GoogleAiFallbackDispatcher.dispatch(
                "super secret query", "com.gathin.tvhome", launcher);

        assertEquals("unavailable", result.status);
        assertNull(result.target);
        assertEquals(5, launcher.launchedTiers.size());
    }

    // =========================================================================
    // 3. BOUNDARY CONDITIONS & MALFORMED INPUTS
    // =========================================================================

    @Test
    public void testBoundary_NullQuery() {
        FaultyActivityLauncher launcher = new FaultyActivityLauncher();
        launcher.resolvableTiers.add("voice_assist");

        ExecutionResult res = GoogleAiFallbackDispatcher.dispatch(null, "com.gathin.tvhome", launcher);
        assertEquals("error", res.status);
        assertTrue(launcher.launchedTiers.isEmpty());
    }

    @Test
    public void testBoundary_EmptyAndWhitespaceQuery() {
        FaultyActivityLauncher launcher = new FaultyActivityLauncher();
        launcher.resolvableTiers.add("voice_assist");

        assertEquals("error", GoogleAiFallbackDispatcher.dispatch("", "com.gathin.tvhome", launcher).status);
        assertEquals("error", GoogleAiFallbackDispatcher.dispatch("   ", "com.gathin.tvhome", launcher).status);
        assertEquals("error", GoogleAiFallbackDispatcher.dispatch("\t\n\r  ", "com.gathin.tvhome", launcher).status);
        assertTrue(launcher.launchedTiers.isEmpty());
    }

    @Test
    public void testBoundary_NullContext() {
        ExecutionResult res = GoogleAiFallbackDispatcher.dispatch((Context) null, "test query");
        assertNotNull(res);
        assertEquals("error", res.status);
        assertTrue(res.isFallback);
        assertEquals("test query", res.query);
    }

    @Test
    public void testBoundary_NullCallerPackageUsesDefault() {
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createVoiceAssistSpec("query", null);
        assertFalse(spec.extras.containsKey("android.intent.extra.ASSIST_PACKAGE"));

        // When dispatched with null callerPackage
        FaultyActivityLauncher launcher = new FaultyActivityLauncher();
        launcher.resolvableTiers.add("voice_assist");
        ExecutionResult res = GoogleAiFallbackDispatcher.dispatch("query", null, launcher);
        assertEquals("ok", res.status);
        assertEquals("voice_assist", res.target);
    }

    @Test
    public void testBoundary_ExtremelyLongQuery() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("longqueryword ");
        }
        String giantQuery = sb.toString();

        FaultyActivityLauncher launcher = new FaultyActivityLauncher();
        launcher.resolvableTiers.add("browser");

        ExecutionResult res = GoogleAiFallbackDispatcher.dispatch(giantQuery, "com.gathin.tvhome", launcher);
        assertEquals("ok", res.status);
        assertEquals("browser", res.target);
    }

    @Test
    public void testBoundary_UnicodeEmojisAndNonLatin() throws Exception {
        String unicodeQuery = "🌟 电视 한국어 검색 🚀";
        GoogleAiFallbackDispatcher.FallbackIntentSpec spec =
                GoogleAiFallbackDispatcher.createBrowserSearchSpec(unicodeQuery);

        assertTrue(spec.dataUri.startsWith("https://www.google.com/search?q="));
        String rawQueryParam = spec.dataUri.substring("https://www.google.com/search?q=".length());
        String decoded = URLDecoder.decode(rawQueryParam, StandardCharsets.UTF_8.name());
        assertEquals(unicodeQuery, decoded);
    }

    // =========================================================================
    // 4. INTENT EXTENSION & OBJECT MODEL EMPIRICAL CHALLENGES
    // =========================================================================

    @Test
    public void testIntentModel_FallbackIntentExtrasAndFlags() {
        Intent intent = GoogleAiFallbackDispatcher.buildVoiceAssistIntent("how is the traffic");
        assertNotNull(intent);
        assertEquals("android.intent.action.VOICE_ASSIST", intent.getAction());
        assertEquals("how is the traffic", intent.getStringExtra(SearchManager.QUERY));
        assertEquals("how is the traffic", intent.getStringExtra(SearchManager.USER_QUERY));
        assertEquals("how is the traffic", intent.getStringExtra(Intent.EXTRA_TEXT));
        assertEquals("com.gathin.tvhome", intent.getStringExtra("android.intent.extra.ASSIST_PACKAGE"));
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void testIntentModel_KatnissIntentPackageAndFlags() {
        Intent intent = GoogleAiFallbackDispatcher.buildKatnissSearchIntent("action thrillers");
        assertNotNull(intent);
        assertEquals("android.intent.action.SEARCH", intent.getAction());
        assertEquals("com.google.android.katniss", intent.getPackage());
        assertEquals("action thrillers", intent.getStringExtra(SearchManager.QUERY));
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void testIntentModel_BrowserIntentUriAndCategories() {
        Intent intent = GoogleAiFallbackDispatcher.buildBrowserSearchIntent("best tv shows 2026");
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue(intent.getData().toString().startsWith("https://www.google.com/search?q="));
    }

    @Test
    public void testIntentModel_StandardIntentMethodsOnFallbackIntent() {
        Intent intent = GoogleAiFallbackDispatcher.buildVoiceAssistIntent("test standard intent");
        // Many Android components and libraries call hasExtra() or getExtras()
        try {
            boolean hasQ = intent.hasExtra(SearchManager.QUERY);
            assertTrue("Expected hasExtra to be true", hasQ);
        } catch (Throwable t) {
            System.err.println("CRITICAL FAILURE in intent.hasExtra: " + t.getMessage());
            throw t;
        }
    }

    private static class FaultyActivityLauncher implements GoogleAiFallbackDispatcher.ActivityLauncher {
        final Set<String> resolvableTiers = new HashSet<>();
        final java.util.Map<String, RuntimeException> resolvableExceptions = new java.util.HashMap<>();
        final java.util.Map<String, RuntimeException> launchExceptions = new java.util.HashMap<>();
        final List<String> launchedTiers = new ArrayList<>();

        @Override
        public boolean isResolvable(GoogleAiFallbackDispatcher.FallbackIntentSpec spec) {
            if (resolvableExceptions.containsKey(spec.tier)) {
                throw resolvableExceptions.get(spec.tier);
            }
            return resolvableTiers.contains(spec.tier);
        }

        @Override
        public boolean launch(GoogleAiFallbackDispatcher.FallbackIntentSpec spec) {
            launchedTiers.add(spec.tier);
            if (launchExceptions.containsKey(spec.tier)) {
                throw launchExceptions.get(spec.tier);
            }
            return true;
        }
    }
}
