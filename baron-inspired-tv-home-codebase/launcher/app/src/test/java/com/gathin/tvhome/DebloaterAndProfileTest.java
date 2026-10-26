package com.gathin.tvhome;

import android.content.ComponentCallbacks2;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DebloaterAndProfileTest {

    @Before
    @After
    public void resetPrefs() {
        ProfileManager.setTestPreferences(null);
    }

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

        // When unconfigured, default PIN 0000 is rejected and returns false
        assertFalse(ProfileManager.verifyPin(null, "0000"));
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

    @Test
    public void pinSecuritySetupAndHashVerification() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        ProfileManager.setTestPreferences(prefs);

        // 1. Initial unconfigured state
        assertFalse("Unconfigured state must report isPinConfigured=false", ProfileManager.isPinConfigured(null));
        assertFalse("Unconfigured state must reject 0000", ProfileManager.verifyPin(null, "0000"));
        assertFalse("Unconfigured state must reject any PIN", ProfileManager.verifyPin(null, "1234"));

        // 2. Setup PIN with validation
        assertFalse("Invalid short PIN must be rejected", ProfileManager.setParentPin(null, "12"));
        assertFalse("Invalid non-numeric PIN must be rejected", ProfileManager.setParentPin(null, "abcd"));
        assertFalse("Null PIN must be rejected", ProfileManager.setParentPin(null, null));

        assertTrue("Valid 4-digit PIN setup must succeed", ProfileManager.setParentPin(null, "2580"));
        assertTrue("Configured state must now report true", ProfileManager.isPinConfigured(null));

        // Ensure plaintext PIN was NOT stored in preferences
        assertFalse("Plaintext PIN key must never exist in preferences", prefs.contains("parent_pin"));
        assertTrue("Salt must be stored", prefs.contains("parent_pin_salt"));
        assertTrue("Hash must be stored", prefs.contains("parent_pin_hash"));
        String saltHex = prefs.getString("parent_pin_salt", "");
        String hashHex = prefs.getString("parent_pin_hash", "");
        assertNotNull(saltHex);
        assertNotNull(hashHex);
        assertEquals("Salt must be 16 bytes (32 hex characters)", 32, saltHex.length());
        assertEquals("Hash must be 32 bytes (64 hex characters)", 64, hashHex.length());

        // 3. Verification
        assertTrue("Correct PIN must verify successfully", ProfileManager.verifyPin(null, "2580"));
        assertFalse("Incorrect PIN must fail", ProfileManager.verifyPin(null, "9999"));
        assertFalse("Default 0000 must fail", ProfileManager.verifyPin(null, "0000"));
        assertFalse("Malformed PIN must fail", ProfileManager.verifyPin(null, "258"));
        assertFalse("Null input must fail", ProfileManager.verifyPin(null, null));
    }

    @Test
    public void pinLockoutRateLimitingAndPersistence() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        ProfileManager.setTestPreferences(prefs);

        ProfileManager.setParentPin(null, "7410");
        assertFalse(ProfileManager.isPinLockedOut(null));
        assertEquals(0, ProfileManager.getFailedPinAttempts(null));

        // 4 failed attempts should increment counter but not lockout yet
        for (int i = 1; i <= 4; i++) {
            assertFalse(ProfileManager.verifyPin(null, "1111"));
            assertFalse(ProfileManager.isPinLockedOut(null));
            assertEquals(i, ProfileManager.getFailedPinAttempts(null));
        }

        // 5th failed attempt triggers lockout
        assertFalse(ProfileManager.verifyPin(null, "1111"));
        assertTrue("5 failed attempts must trigger lockout", ProfileManager.isPinLockedOut(null));
        long remaining = ProfileManager.getPinLockoutRemainingSeconds(null);
        assertTrue("Remaining seconds should be between 1 and 30s", remaining >= 1 && remaining <= 30);

        // During lockout, even correct PIN must be rejected
        assertFalse("Correct PIN must be rejected while locked out", ProfileManager.verifyPin(null, "7410"));

        // Persisted state test: simulate launcher restart with a new ProfileManager reference reading same prefs
        ProfileManager.setTestPreferences(prefs);
        assertTrue("Lockout state must persist across launcher restart", ProfileManager.isPinLockedOut(null));
        assertTrue("Lockout seconds must be positive across restart", ProfileManager.getPinLockoutRemainingSeconds(null) > 0);

        // Dialog methods must reflect the same lockout
        assertTrue(PinEntryDialog.isLockedOut());
        assertTrue(PinEntryDialog.getRemainingLockoutSeconds() > 0);

        // Reset lockout manually (e.g. after lockout duration expires or admin reset)
        ProfileManager.resetPinLockout(null);
        assertFalse(ProfileManager.isPinLockedOut(null));
        assertEquals(0, ProfileManager.getPinLockoutRemainingSeconds(null));

        // Correct PIN now succeeds and resets attempts
        assertTrue(ProfileManager.verifyPin(null, "7410"));
        assertEquals(0, ProfileManager.getFailedPinAttempts(null));
    }

    @Test
    public void pinAuthenticationAndModificationPermissions() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        ProfileManager.setTestPreferences(prefs);

        ProfileManager.setParentPin(null, "1357");

        // Changing PIN requires valid current PIN
        assertFalse("Changing PIN with wrong current PIN must fail",
                ProfileManager.changeParentPin(null, "0000", "2468"));
        assertTrue("PIN must remain unchanged after failed attempt",
                ProfileManager.verifyPin(null, "1357"));

        assertTrue("Changing PIN with valid current PIN must succeed",
                ProfileManager.changeParentPin(null, "1357", "2468"));
        assertFalse("Old PIN must no longer work", ProfileManager.verifyPin(null, "1357"));
        assertTrue("New PIN must work", ProfileManager.verifyPin(null, "2468"));

        // Safe recovery: Device owner in Personal mode can reset PIN
        // Default profile is Personal
        assertTrue(ProfileManager.isPersonalMode(null));
        assertTrue("Personal mode can safely reset PIN",
                ProfileManager.resetParentPinInPersonalMode(null, "9876"));
        assertTrue(ProfileManager.verifyPin(null, "9876"));

        // Switch to Kids Mode
        ProfileManager.switchProfile(null, ProfileManager.PROFILE_KIDS);
        assertFalse(ProfileManager.isPersonalMode(null));
        assertTrue(ProfileManager.isKidsMode(null));

        // In Kids Mode, resetting PIN without authentication must fail
        assertFalse("Kids mode cannot execute safe recovery reset",
                ProfileManager.resetParentPinInPersonalMode(null, "1111"));
        assertTrue("PIN remains 9876", ProfileManager.verifyPin(null, "9876"));

        // Kids Mode cannot modify bedtime limit or custom allowlists
        int originalLimit = ProfileManager.getKidsBedtimeLimitMinutes(null);
        ProfileManager.setKidsBedtimeLimitMinutes(null, 15);
        assertEquals("Bedtime limit cannot be modified outside Personal mode",
                originalLimit, ProfileManager.getKidsBedtimeLimitMinutes(null));

        ProfileManager.addCustomKidsPackage(null, "com.example.unauthorized");
        assertFalse("Kids mode cannot add packages to allowlist",
                ProfileManager.getCustomKidsAllowlist(null).contains("com.example.unauthorized"));
    }

    @Test
    public void pinClockJumpProtectionAndRecovery() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        ProfileManager.setTestPreferences(prefs);

        ProfileManager.setParentPin(null, "3690");

        // Simulate a corrupted or RTC-reset timestamp far into the future (e.g. 1 year from now)
        long now = System.currentTimeMillis();
        prefs.edit().putLong("parent_pin_lockout_until_ms", now + (365L * 24 * 3600 * 1000)).apply();

        // Clock jump guard should detect the abnormal future timestamp and reset lockout
        assertFalse("Clock jump guard must prevent permanent multi-year lockout",
                ProfileManager.isPinLockedOut(null));
        assertEquals(0, ProfileManager.getPinLockoutRemainingSeconds(null));

        // Normal PIN verification should work immediately
        assertTrue(ProfileManager.verifyPin(null, "3690"));
    }
}
