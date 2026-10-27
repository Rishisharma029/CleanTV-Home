package com.gathin.tvhome.ai;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit test suite for TvAppResolver verifying 5-tier resolution:
 * Tier 1: Exact Package ID Match
 * Tier 2: Exact Case-Insensitive Label Match
 * Tier 3: Known TV App Aliases Dictionary
 * Tier 4: Normalized Token Containment
 * Tier 5: Levenshtein Distance (<= 2)
 */
public class TvAppResolverTest {

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

    // Tier 1: Exact Package ID
    @Test
    public void testExactPackageId() {
        assertEquals("com.google.android.youtube.tv",
                resolver.findPackageForLabel("com.google.android.youtube.tv"));
    }

    @Test
    public void testExactPackageIdCaseInsensitive() {
        assertEquals("com.netflix.ninja",
                resolver.findPackageForLabel("COM.NETFLIX.NINJA"));
    }

    // Tier 2: Exact Label Match
    @Test
    public void testExactLabelMatch() {
        assertEquals("com.google.android.youtube.tv",
                resolver.findPackageForLabel("YouTube"));
    }

    @Test
    public void testExactLabelCaseInsensitive() {
        assertEquals("com.spotify.tv.android",
                resolver.findPackageForLabel("spotify"));
    }

    // Tier 3: Known TV App Aliases
    @Test
    public void testKnownAliasYt() {
        assertEquals("com.google.android.youtube.tv",
                resolver.findPackageForLabel("yt"));
    }

    @Test
    public void testKnownAliasPrimeVideo() {
        assertEquals("com.amazon.amazonvideo.livingroom",
                resolver.findPackageForLabel("amazon prime"));
        assertEquals("com.amazon.amazonvideo.livingroom",
                resolver.findPackageForLabel("prime"));
    }

    @Test
    public void testKnownAliasDisneyPlus() {
        assertEquals("in.startv.hotstar",
                resolver.findPackageForLabel("disney+"));
        assertEquals("in.startv.hotstar",
                resolver.findPackageForLabel("disney plus"));
        assertEquals("in.startv.hotstar",
                resolver.findPackageForLabel("hotstar"));
    }

    @Test
    public void testKnownAliasKodi() {
        assertEquals("org.xbmc.kodi",
                resolver.findPackageForLabel("xbmc"));
    }

    // Tier 4: Normalized Token Containment
    @Test
    public void testNormalizedContainmentGlued() {
        assertEquals("com.amazon.amazonvideo.livingroom",
                resolver.findPackageForLabel("primevideo"));
    }

    @Test
    public void testNormalizedContainmentPlus() {
        assertEquals("in.startv.hotstar",
                resolver.findPackageForLabel("disneyplus"));
    }

    @Test
    public void testNormalizedContainmentSuffix() {
        assertEquals("org.videolan.vlc",
                resolver.findPackageForLabel("vlc player"));
    }

    // Tier 5: Levenshtein Distance
    @Test
    public void testLevenshteinDistance1Typo() {
        assertEquals("com.google.android.youtube.tv",
                resolver.findPackageForLabel("youtbe"));
    }

    @Test
    public void testLevenshteinDistance1Netfix() {
        assertEquals("com.netflix.ninja",
                resolver.findPackageForLabel("netfix"));
    }

    @Test
    public void testLevenshteinDistance1Spotfy() {
        assertEquals("com.spotify.tv.android",
                resolver.findPackageForLabel("spotfy"));
    }

    @Test
    public void testLevenshteinDistance2Cody() {
        assertEquals("org.xbmc.kodi",
                resolver.findPackageForLabel("cody"));
    }

    // Safeguards & Uninstalled
    @Test
    public void testShortQuerySafeguard() {
        // "it", "at", "to" should NOT match "yt"
        assertNull(resolver.findPackageForLabel("it"));
        assertNull(resolver.findPackageForLabel("at"));
        assertNull(resolver.findPackageForLabel("to"));
    }

    @Test
    public void testUninstalledAppReturnsNull() {
        assertNull(resolver.findPackageForLabel("Hulu"));
        assertNull(resolver.findPackageForLabel("Paramount+"));
    }

    @Test
    public void testGetLabelForPackage() {
        assertEquals("YouTube", resolver.getLabelForPackage("com.google.android.youtube.tv"));
        assertEquals("Netflix", resolver.getLabelForPackage("com.netflix.ninja"));
    }

    @Test
    public void testExecutionPerformanceBenchmark() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            resolver.findPackageForLabel("youtbe");
            resolver.findPackageForLabel("primevideo");
            resolver.findPackageForLabel("disney plus");
        }
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("3000 resolutions took " + elapsed + "ms (should be < 300ms)", elapsed < 300);
    }
}
