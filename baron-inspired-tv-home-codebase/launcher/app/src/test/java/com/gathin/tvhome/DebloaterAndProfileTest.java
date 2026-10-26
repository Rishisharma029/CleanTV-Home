package com.gathin.tvhome;

import android.content.ComponentCallbacks2;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DebloaterAndProfileTest {

    @Test
    public void debloaterBrandsAndPreview() {
        for (DebloaterEngine.Brand brand : DebloaterEngine.Brand.values()) {
            assertNotNull(brand.displayName);
            assertFalse(brand.displayName.isEmpty());
            assertNotNull(brand.description);
            assertFalse(brand.description.isEmpty());
        }

        for (DebloaterEngine.Risk risk : DebloaterEngine.Risk.values()) {
            assertNotNull(risk.displayName);
            assertTrue(risk.color != 0);
        }

        DebloaterEngine.Brand detected = DebloaterEngine.detectDeviceBrand();
        assertNotNull(detected);

        String summary = DebloaterEngine.getDeviceInfoSummary();
        assertNotNull(summary);
        assertFalse(summary.isEmpty());

        List<DebloaterEngine.DebloatItem> nullScan = DebloaterEngine.scanDebloatCandidates(null);
        assertNotNull(nullScan);
        assertTrue(nullScan.isEmpty());

        DebloaterEngine.DebloaterPreview preview = DebloaterEngine.getDebloaterPreview(null);
        assertNotNull(preview);
        assertEquals(0, preview.totalFound);
        assertEquals(0, preview.activeBloatCount);

        assertFalse(DebloaterEngine.disablePackage(null, null));
        assertFalse(DebloaterEngine.enablePackage(null, null));
    }

    @Test
    public void texturePoolBounded15MbAndRecycler() {
        assertEquals(15 * 1024 * 1024, TexturePool.MAX_CACHE_SIZE_BYTES);

        TexturePool.cacheDominantColor("test.pkg", 0xFF123456);
        assertEquals(Integer.valueOf(0xFF123456), TexturePool.getCachedDominantColor("test.pkg"));

        int[] backdrop = new int[]{0xFF112233, 0xFF445566};
        TexturePool.cacheBackdrop("test.pkg", backdrop);
        assertEquals(backdrop, TexturePool.getCachedBackdrop("test.pkg"));

        TexturePool.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL);
        TexturePool.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
    }

    @Test
    public void eventBroadcastEngineZeroPolling() {
        assertTrue(EventBroadcastEngine.isScreenActive());

        EventBroadcastEngine.ClockListener clockListener = () -> {};
        EventBroadcastEngine.addClockListener(clockListener);
        EventBroadcastEngine.removeClockListener(clockListener);

        EventBroadcastEngine.ScreenListener screenListener = new EventBroadcastEngine.ScreenListener() {
            @Override public void onScreenOff() {}
            @Override public void onScreenOn() {}
        };
        EventBroadcastEngine.addScreenListener(screenListener);
        EventBroadcastEngine.removeScreenListener(screenListener);

        EventBroadcastEngine.NetworkListener netListener = (conn, wifi, eth) -> {};
        EventBroadcastEngine.addNetworkListener(netListener);
        EventBroadcastEngine.removeNetworkListener(netListener);
    }

    @Test
    public void multiProfileLogic() {
        assertEquals("Personal", ProfileManager.getProfileDisplayName(ProfileManager.PROFILE_PERSONAL));
        assertEquals("Kids Mode", ProfileManager.getProfileDisplayName(ProfileManager.PROFILE_KIDS));
        assertEquals("Guest / Cinema", ProfileManager.getProfileDisplayName(ProfileManager.PROFILE_GUEST));

        assertEquals("👤", ProfileManager.getProfileIcon(ProfileManager.PROFILE_PERSONAL));
        assertEquals("🎈", ProfileManager.getProfileIcon(ProfileManager.PROFILE_KIDS));
        assertEquals("🍿", ProfileManager.getProfileIcon(ProfileManager.PROFILE_GUEST));

        // Default PIN
        assertTrue(ProfileManager.verifyPin(null, "0000"));
        assertFalse(ProfileManager.verifyPin(null, "1234"));
        assertFalse(ProfileManager.verifyPin(null, ""));
        assertFalse(ProfileManager.verifyPin(null, null));

        // Curated Kids allowlist packages
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.google.android.youtube.tvkids"));
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.google.android.youtube.googletvkids"));
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.disney.disneyplus"));
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.netflix.ninja"));
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "org.pbskids.video"));
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "org.pbskids.games"));
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.nick.kids"));
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.android.tv"));

        // Explicitly blocked and lookalike packages in Kids mode
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.android.vending"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.android.tv.settings"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.phlox.tvwebbrowser"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.android.chrome"));

        // A strict Kids allowlist must reject ordinary YouTube and unlisted packages.
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.google.android.youtube.tv"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.google.android.play.games"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.example.kids.video"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.disney.fake"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.example.cartoon"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "com.example.unknown"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, null));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_KIDS, "   "));

        // Guest Mode also rejects package-name lookalikes; only exact curated packages pass.
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_GUEST, "org.videolan.vlc"));
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_GUEST, "com.google.android.youtube.tv"));
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_GUEST, "com.netflix.ninja"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_GUEST, "com.example.tvhelper"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_GUEST, "com.example.video"));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_GUEST, null));
        assertFalse(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_GUEST, ""));

        // Unknown profile identifiers must fail closed.
        assertFalse(ProfileManager.isAppAllowedForProfile("unknown-profile", "com.disney.disneyplus"));
        assertFalse(ProfileManager.isAppAllowedForProfile(null, null));

        // All allowed in Personal mode
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_PERSONAL, "com.android.vending"));
        assertTrue(ProfileManager.isAppAllowedForProfile(ProfileManager.PROFILE_PERSONAL, "com.android.tv.settings"));

        // Rate limiting initial state
        assertFalse(PinEntryDialog.isLockedOut());
        assertEquals(0, PinEntryDialog.getRemainingLockoutSeconds());
    }
}
