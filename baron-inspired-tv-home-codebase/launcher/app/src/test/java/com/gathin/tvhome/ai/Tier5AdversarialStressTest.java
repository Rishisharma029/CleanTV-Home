package com.gathin.tvhome.ai;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tier 5: Adversarial Stress Test & Forensic Boundary Suite for Milestone 1.
 * Tests weird inputs, unicode, emoji, mixed casing, extra punctuation, numbers,
 * phonetic variations, multiple commands, Levenshtein distance bounds (<= 2),
 * short words (<= 2 chars), 5W1H interrogatives, ReDoS resistance, and high-throughput stress.
 */
public class Tier5AdversarialStressTest {

    private TvAppResolver resolver;

    @Before
    public void setUp() {
        resolver = new TvAppResolver();
        resolver.addApp("com.google.android.youtube.tv", "YouTube");
        resolver.addApp("com.netflix.ninja", "Netflix");
        resolver.addApp("com.amazon.amazonvideo.livingroom", "Prime Video");
        resolver.addApp("com.spotify.tv.android", "Spotify");
        resolver.addApp("in.startv.hotstar", "Disney+ Hotstar");
        resolver.addApp("org.xbmc.kodi", "Kodi");
        resolver.addApp("org.videolan.vlc", "VLC");
        resolver.addApp("tv.twitch.android.app", "Twitch");
    }

    // =========================================================================
    // SECTION 1: Punctuation, Quotes & Whitespace Anomalies
    // =========================================================================

    @Test
    public void testEnclosingSingleQuotes() {
        TvIntent res = TvIntentClassifier.classify("'open youtube'", resolver);
        assertEquals(IntentType.LAUNCH_APP, res.type);
        assertEquals("com.google.android.youtube.tv", res.getParamString("package"));
    }

    @Test
    public void testEnclosingDoubleQuotes() {
        TvIntent res = TvIntentClassifier.classify("\"netflix\"", resolver);
        assertEquals(IntentType.LAUNCH_APP, res.type);
        assertEquals("com.netflix.ninja", res.getParamString("package"));
    }

    @Test
    public void testInternalQuotesAroundApp() {
        TvIntent res = TvIntentClassifier.classify("open 'Spotify'", resolver);
        assertEquals(IntentType.LAUNCH_APP, res.type);
        assertEquals("com.spotify.tv.android", res.getParamString("package"));
    }

    @Test
    public void testMultipleTrailingPunctuation() {
        TvIntent res1 = TvIntentClassifier.classify("volume up?!?!?", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, res1.type);
        assertEquals("volume_up", res1.action);

        TvIntent res2 = TvIntentClassifier.classify("mute......", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, res2.type);
        assertEquals("mute", res2.action);

        TvIntent res3 = TvIntentClassifier.classify("go home!!!!!", resolver);
        assertEquals(IntentType.SYSTEM_ACTION, res3.type);
        assertEquals("home", res3.action);
    }

    @Test
    public void testMultipleInternalSpacesAndTabs() {
        TvIntent res = TvIntentClassifier.classify("open   \t   YouTube", resolver);
        assertEquals(IntentType.LAUNCH_APP, res.type);
        assertEquals("com.google.android.youtube.tv", res.getParamString("package"));
    }

    @Test
    public void testOnlyPunctuation() {
        TvIntent res = TvIntentClassifier.classify("!?!?!?", resolver);
        assertEquals(IntentType.UNKNOWN, res.type);
        assertEquals("empty_input", res.action);

        TvIntent res2 = TvIntentClassifier.classify("....,,,;;;:::", resolver);
        assertEquals(IntentType.UNKNOWN, res2.type);
        assertEquals("empty_input", res2.action);
    }

    // =========================================================================
    // SECTION 2: Unicode, Accents & Emoji Inputs
    // =========================================================================

    @Test
    public void testEmojiAppLaunch() {
        TvIntent res1 = TvIntentClassifier.classify("open YouTube 📺", resolver);
        assertEquals(IntentType.LAUNCH_APP, res1.type);
        assertEquals("com.google.android.youtube.tv", res1.getParamString("package"));

        TvIntent res2 = TvIntentClassifier.classify("launch 🎬 Netflix", resolver);
        assertEquals(IntentType.LAUNCH_APP, res2.type);
        assertEquals("com.netflix.ninja", res2.getParamString("package"));
    }

    @Test
    public void testEmojiVolumeControl() {
        // Emojis on volume/control commands fall through to Google AI Fallback
        // because regex requires exact matching and clean only strips ASCII punctuation
        TvIntent res1 = TvIntentClassifier.classify("volume up 🔊", resolver);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res1.type);

        TvIntent res2 = TvIntentClassifier.classify("mute 🔇", resolver);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res2.type);
    }

    @Test
    public void testPureEmojiQueriesRouteToFallback() {
        TvIntent res1 = TvIntentClassifier.classify("📺🍿", resolver);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res1.type);

        TvIntent res2 = TvIntentClassifier.classify("❓🤔", resolver);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res2.type);
    }

    @Test
    public void testAccentedCharactersNormalization() {
        // "YöuTube" has 'ö' stripped in normalize() -> "yutube" (edit distance 1 from "youtube")
        TvIntent res = TvIntentClassifier.classify("open YöuTube", resolver);
        assertEquals(IntentType.LAUNCH_APP, res.type);
        assertEquals("com.google.android.youtube.tv", res.getParamString("package"));
    }

    @Test
    public void testNonLatinCharactersRouteGracefully() {
        // Cyrillic
        TvIntent resCyrillic = TvIntentClassifier.classify("Привет как дела", resolver);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, resCyrillic.type);

        // Devanagari
        TvIntent resHindi = TvIntentClassifier.classify("मौसम कैसा है", resolver);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, resHindi.type);

        // Chinese
        TvIntent resChinese = TvIntentClassifier.classify("打开优酷", resolver);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, resChinese.type);

        // Arabic
        TvIntent resArabic = TvIntentClassifier.classify("ما هو الطقس", resolver);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, resArabic.type);
    }

    @Test
    public void testZeroWidthAndNonBreakingSpaces() {
        // Non-breaking space
        TvIntent res1 = TvIntentClassifier.classify("open\u00A0YouTube", resolver);
        assertEquals(IntentType.LAUNCH_APP, res1.type);
        assertEquals("com.google.android.youtube.tv", res1.getParamString("package"));

        // Zero-width space
        TvIntent res2 = TvIntentClassifier.classify("volume\u200Bup", resolver);
        assertNotNull(res2); // Must not throw
    }

    // =========================================================================
    // SECTION 3: Mixed Casing & Exotic Case Variations
    // =========================================================================

    @Test
    public void testSpongeBobMockingCasing() {
        TvIntent resApp = TvIntentClassifier.classify("oPeN yOuTuBe", resolver);
        assertEquals(IntentType.LAUNCH_APP, resApp.type);
        assertEquals("com.google.android.youtube.tv", resApp.getParamString("package"));

        TvIntent resVol = TvIntentClassifier.classify("vOlUmE uP", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, resVol.type);

        TvIntent resMute = TvIntentClassifier.classify("mUtE", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, resMute.type);

        TvIntent resHome = TvIntentClassifier.classify("gO hOmE", resolver);
        assertEquals(IntentType.SYSTEM_ACTION, resHome.type);
    }

    @Test
    public void testAllCapsCommands() {
        TvIntent res1 = TvIntentClassifier.classify("OPEN NETFLIX", resolver);
        assertEquals(IntentType.LAUNCH_APP, res1.type);
        assertEquals("com.netflix.ninja", res1.getParamString("package"));

        TvIntent res2 = TvIntentClassifier.classify("SET VOLUME TO 50%", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, res2.type);
        assertEquals(50, res2.getParamInt("level", -1));

        TvIntent res3 = TvIntentClassifier.classify("CONTROL CENTER", resolver);
        assertEquals(IntentType.LAUNCHER_FEATURE, res3.type);
        assertEquals("control_center", res3.action);
    }

    // =========================================================================
    // SECTION 4: Volume Control Numerics & Bounds Clamping
    // =========================================================================

    @Test
    public void testNegativeVolumeClampsToZero() {
        TvIntent res1 = TvIntentClassifier.classify("set volume to -10%", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, res1.type);
        assertEquals(0, res1.getParamInt("level", -1));

        TvIntent res2 = TvIntentClassifier.classify("volume -50", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, res2.type);
        assertEquals(0, res2.getParamInt("level", -1));
    }

    @Test
    public void testExcessiveVolumeClampsToOneHundred() {
        TvIntent res1 = TvIntentClassifier.classify("set volume to 150%", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, res1.type);
        assertEquals(100, res1.getParamInt("level", -1));

        TvIntent res2 = TvIntentClassifier.classify("volume 999", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, res2.type);
        assertEquals(100, res2.getParamInt("level", -1));
    }

    @Test
    public void testVolumeExactBoundaries() {
        TvIntent res0 = TvIntentClassifier.classify("volume 0", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, res0.type);
        assertEquals(0, res0.getParamInt("level", -1));

        TvIntent res100 = TvIntentClassifier.classify("volume 100", resolver);
        assertEquals(IntentType.VOLUME_CONTROL, res100.type);
        assertEquals(100, res100.getParamInt("level", -1));
    }

    @Test
    public void testGiantNumberNoOverflowCrash() {
        // Number with > 3 digits will not match \\d{1,3} in volume set pattern
        TvIntent res = TvIntentClassifier.classify("volume 999999999999999999999999999", resolver);
        assertNotNull(res);
        // Does not throw NumberFormatException, falls through gracefully
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res.type);
    }

    @Test
    public void testSpelledOutNumbersFallback() {
        TvIntent res = TvIntentClassifier.classify("volume fifty percent", resolver);
        assertNotNull(res);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res.type);
    }

    // =========================================================================
    // SECTION 5: Levenshtein Distance Bounds (Distance <= 2) & Short Words (<= 2 chars)
    // =========================================================================

    @Test
    public void testLevenshteinDistanceZeroExactMatch() {
        assertEquals("com.google.android.youtube.tv", resolver.findPackageForLabel("youtube"));
        assertEquals("com.netflix.ninja", resolver.findPackageForLabel("netflix"));
        assertEquals("org.xbmc.kodi", resolver.findPackageForLabel("kodi"));
    }

    @Test
    public void testLevenshteinDistanceOneSingleTypo() {
        // YouTube (7 chars) -> "youtub" (len 6, dist 1)
        assertEquals("com.google.android.youtube.tv", resolver.findPackageForLabel("youtub"));
        // "yutube" (len 6, dist 1)
        assertEquals("com.google.android.youtube.tv", resolver.findPackageForLabel("yutube"));
        // Netflix (7 chars) -> "netflx" (len 6, dist 1)
        assertEquals("com.netflix.ninja", resolver.findPackageForLabel("netflx"));
        // Spotify (7 chars) -> "spotfy" (len 6, dist 1)
        assertEquals("com.spotify.tv.android", resolver.findPackageForLabel("spotfy"));
    }

    @Test
    public void testLevenshteinDistanceTwoTwoTypos() {
        // YouTube -> "yotub" (len 5, dist 2: -u, -e)
        assertEquals("com.google.android.youtube.tv", resolver.findPackageForLabel("yotub"));
        // Netflix -> "neflx" (len 5, dist 2: -t, -i)
        assertEquals("com.netflix.ninja", resolver.findPackageForLabel("neflx"));
        // Kodi -> "cody" (len 4, dist 2: k->c, i->y)
        assertEquals("org.xbmc.kodi", resolver.findPackageForLabel("cody"));
    }

    @Test
    public void testLevenshteinDistanceThreeMustNotMatch() {
        // YouTube -> "yxxxube" (dist 3: o->x, u->x, t->x)
        assertNull(resolver.findPackageForLabel("yxxxube"));
        // YouTube -> "yoootuuube" (dist 3: extra o, extra u)
        assertNull(resolver.findPackageForLabel("yoootuuube"));
        // Netflix -> "neeeetflx" (dist 3)
        assertNull(resolver.findPackageForLabel("neeeetflx"));
        // Spotify -> "spoxxxfy" (dist 3)
        assertNull(resolver.findPackageForLabel("spoxxxfy"));
        // Kodi -> "kozzzi" (dist 3)
        assertNull(resolver.findPackageForLabel("kozzzi"));
    }

    @Test
    public void testShortWordsMustNotFalselyMatch() {
        // Words with length <= 2 must NOT falsely match installed apps
        String[] shortWords = {
                "it", "at", "to", "go", "up", "on", "no", "tv", "me", "hi", "he",
                "we", "in", "by", "is", "am", "an", "as", "if", "of", "or", "so", "do"
        };
        for (String word : shortWords) {
            String pkg = resolver.findPackageForLabel(word);
            assertNull("Short word '" + word + "' must NOT resolve to any app, but resolved to " + pkg, pkg);
        }
    }

    @Test
    public void testLegitimateShortAliasMatches() {
        // "yt" is in known aliases for YouTube, so it MUST match
        assertEquals("com.google.android.youtube.tv", resolver.findPackageForLabel("yt"));
    }

    @Test
    public void testThreeCharWordLevenshteinThresholdIsOne() {
        // For a 3-character query (e.g. "vlc"), max distance is strictly 1
        // "vlc" exact (dist 0) matches VLC
        assertEquals("org.videolan.vlc", resolver.findPackageForLabel("vlc"));
        // Distance 2 substitution (e.g. "xxx" vs "vlc", dist 3) must NOT match
        assertNull(resolver.findPackageForLabel("xxx"));
        assertNull(resolver.findPackageForLabel("abc"));
    }

    @Test
    public void testLevenshteinAlgorithmEdgeCasesDirectly() {
        // Null checks
        assertEquals(3, LevenshteinDistance.compute(null, "test", 2));
        assertEquals(3, LevenshteinDistance.compute("test", null, 2));

        // Exact match
        assertEquals(0, LevenshteinDistance.compute("abc", "abc", 2));

        // Length diff > maxDistance early exit
        assertEquals(3, LevenshteinDistance.compute("a", "abcde", 2));
        assertEquals(3, LevenshteinDistance.compute("abcde", "a", 2));

        // Distance 1
        assertEquals(1, LevenshteinDistance.compute("cat", "hat", 2));
        assertEquals(1, LevenshteinDistance.compute("cat", "cats", 2));
        assertEquals(1, LevenshteinDistance.compute("cats", "cat", 2));

        // Distance 2
        assertEquals(2, LevenshteinDistance.compute("kitten", "mittin", 2)); // k->m, e->i = 2
        assertEquals(3, LevenshteinDistance.compute("kitten", "sitting", 2)); // dist 3 > maxDistance 2 -> returns 3

        // Distance exceeding threshold
        assertEquals(3, LevenshteinDistance.compute("abc", "xyz", 2));
    }

    // =========================================================================
    // SECTION 6: 5W1H Questions & Out-of-Scope Fallback Routing
    // =========================================================================

    @Test
    public void testWhatQuestionsRouteToFallback() {
        String[] queries = {
                "what time is it",
                "what is quantum computing",
                "what's the capital of France",
                "what is the weather today",
                "what are the settings for picture mode",
                "what is youtube",
                "what can you do",
                "what movie should i watch"
        };
        for (String q : queries) {
            TvIntent res = TvIntentClassifier.classify(q, resolver);
            assertEquals("Query '" + q + "' must route to GOOGLE_AI_FALLBACK",
                    IntentType.GOOGLE_AI_FALLBACK, res.type);
            assertTrue(res.isFallback());
        }
    }

    @Test
    public void testWhoQuestionsRouteToFallback() {
        String[] queries = {
                "who is the president",
                "who won the world cup",
                "who directed interstellar",
                "who invented the television",
                "who made kodi",
                "who created netflix",
                "who is singing"
        };
        for (String q : queries) {
            TvIntent res = TvIntentClassifier.classify(q, resolver);
            assertEquals("Query '" + q + "' must route to GOOGLE_AI_FALLBACK",
                    IntentType.GOOGLE_AI_FALLBACK, res.type);
            assertTrue(res.isFallback());
        }
    }

    @Test
    public void testWhenQuestionsRouteToFallback() {
        String[] queries = {
                "when does the movie start",
                "when was youtube founded",
                "when is sunset today",
                "when will it rain",
                "when is the next solar eclipse"
        };
        for (String q : queries) {
            TvIntent res = TvIntentClassifier.classify(q, resolver);
            assertEquals("Query '" + q + "' must route to GOOGLE_AI_FALLBACK",
                    IntentType.GOOGLE_AI_FALLBACK, res.type);
            assertTrue(res.isFallback());
        }
    }

    @Test
    public void testWhereQuestionsRouteToFallback() {
        String[] queries = {
                "where is Mount Everest",
                "where is the nearest hospital",
                "where can i watch live cricket",
                "where is my home",
                "where was the lord of the rings filmed"
        };
        for (String q : queries) {
            TvIntent res = TvIntentClassifier.classify(q, resolver);
            assertEquals("Query '" + q + "' must route to GOOGLE_AI_FALLBACK",
                    IntentType.GOOGLE_AI_FALLBACK, res.type);
            assertTrue(res.isFallback());
        }
    }

    @Test
    public void testWhyQuestionsRouteToFallback() {
        String[] queries = {
                "why is the sky blue",
                "why does the tv mute",
                "why do leaves change color",
                "why is water wet",
                "why did the chicken cross the road"
        };
        for (String q : queries) {
            TvIntent res = TvIntentClassifier.classify(q, resolver);
            assertEquals("Query '" + q + "' must route to GOOGLE_AI_FALLBACK",
                    IntentType.GOOGLE_AI_FALLBACK, res.type);
            assertTrue(res.isFallback());
        }
    }

    @Test
    public void testHowQuestionsRouteToFallback() {
        String[] queries = {
                "how do airplanes fly",
                "how to cook pasta",
                "how tall is mount everest",
                "how many miles to the moon",
                "how much does a tesla cost",
                "how far is the sun",
                "how old is the universe",
                "how to increase volume"
        };
        for (String q : queries) {
            TvIntent res = TvIntentClassifier.classify(q, resolver);
            assertEquals("Query '" + q + "' must route to GOOGLE_AI_FALLBACK",
                    IntentType.GOOGLE_AI_FALLBACK, res.type);
            assertTrue(res.isFallback());
        }
    }

    @Test
    public void testWhichWhoseWhomQuestionsRouteToFallback() {
        String[] queries = {
                "which team won the game",
                "which planet is closest to the sun",
                "which app is the best",
                "whose line is it anyway",
                "whose phone is ringing",
                "whom did they nominate",
                "whom should i contact"
        };
        for (String q : queries) {
            TvIntent res = TvIntentClassifier.classify(q, resolver);
            assertEquals("Query '" + q + "' must route to GOOGLE_AI_FALLBACK",
                    IntentType.GOOGLE_AI_FALLBACK, res.type);
            assertTrue(res.isFallback());
        }
    }

    @Test
    public void testConversationalAndWeatherTriggersRouteToFallback() {
        String[] chitChatAndWeather = {
                "tell me a joke",
                "tell me a story",
                "sing a song",
                "are you an ai",
                "who made you",
                "hello there",
                "good evening",
                "how are you doing",
                "thank you very much",
                "is it raining outside",
                "what's the weather tomorrow",
                "forecast for the weekend",
                "current temperature",
                "search for artificial intelligence",
                "google latest news"
        };
        for (String q : chitChatAndWeather) {
            TvIntent res = TvIntentClassifier.classify(q, resolver);
            assertEquals("Query '" + q + "' must route to GOOGLE_AI_FALLBACK",
                    IntentType.GOOGLE_AI_FALLBACK, res.type);
            assertTrue(res.isFallback());
        }
    }

    // =========================================================================
    // SECTION 7: Compound Commands & Ambiguities
    // =========================================================================

    @Test
    public void testMultipleCommandsDoNotCrash() {
        TvIntent res1 = TvIntentClassifier.classify("open youtube and volume up", resolver);
        assertNotNull(res1);
        assertFalse(res1.isUnknown());

        TvIntent res2 = TvIntentClassifier.classify("mute and go home", resolver);
        assertNotNull(res2);

        TvIntent res3 = TvIntentClassifier.classify("play and pause", resolver);
        assertNotNull(res3);
    }

    @Test
    public void testPlayVerbWithNonAppRoutesToFallback() {
        // "play Bohemian Rhapsody" should route to Google AI fallback, NOT APP_NOT_INSTALLED
        TvIntent res = TvIntentClassifier.classify("play Bohemian Rhapsody", resolver);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res.type);
    }

    // =========================================================================
    // SECTION 8: Performance, Stress & ReDoS Resilience
    // =========================================================================

    @Test
    public void testExtremeInputLengthResilience() {
        // 5000 characters input
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("repeat ");
        }
        String giant = sb.toString();

        long start = System.currentTimeMillis();
        TvIntent res = TvIntentClassifier.classify(giant, resolver);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(res);
        assertTrue("5000-character input took " + elapsed + "ms (should be < 100ms, no ReDoS)", elapsed < 100);
        assertEquals(IntentType.GOOGLE_AI_FALLBACK, res.type);
    }

    @Test
    public void testCatastrophicBacktrackingResilience() {
        // Potential ReDoS attack pattern with many repeated characters and whitespace
        String evil = "please can you please can you kindly please " + "a".repeat(200) + " please now now now";
        long start = System.currentTimeMillis();
        TvIntent res = TvIntentClassifier.classify(evil, resolver);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(res);
        assertTrue("Potential ReDoS pattern took " + elapsed + "ms (should be < 50ms)", elapsed < 50);
    }

    @Test
    public void testHighThroughputClassificationBenchmark() {
        String[] samples = {
                "open youtube", "volume up", "mute", "set volume to 25%", "play", "pause",
                "go home", "settings", "control center", "screensaver", "tell me a joke",
                "what's the weather", "how do airplanes fly", "open netflix, please",
                "who is the president", "turn it louder", "sound off", "game mode"
        };

        long start = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            for (String sample : samples) {
                TvIntent res = TvIntentClassifier.classify(sample, resolver);
                assertNotNull(res);
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        // 9,000 classifications should take < 500ms
        assertTrue("9000 classifications took " + elapsed + "ms (should be < 500ms)", elapsed < 500);
    }
}
