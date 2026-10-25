package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AdvancedFeaturesTest {

    @Test
    public void adBlockModesAndHostnames() {
        assertEquals("Off (ISP DNS)", AdBlockManager.getModeName(AdBlockManager.MODE_OFF));
        assertEquals("AdGuard DNS", AdBlockManager.getModeName(AdBlockManager.MODE_ADGUARD));
        assertEquals("Mullvad AdBlock", AdBlockManager.getModeName(AdBlockManager.MODE_MULLVAD));
        assertEquals("Cloudflare Secure", AdBlockManager.getModeName(AdBlockManager.MODE_CLOUDFLARE));

        assertEquals("", AdBlockManager.getDnsHostname(AdBlockManager.MODE_OFF));
        assertEquals("dns.adguard-dns.com", AdBlockManager.getDnsHostname(AdBlockManager.MODE_ADGUARD));
        assertEquals("adblock.dns.mullvad.net", AdBlockManager.getDnsHostname(AdBlockManager.MODE_MULLVAD));
        assertEquals("security.cloudflare-dns.com", AdBlockManager.getDnsHostname(AdBlockManager.MODE_CLOUDFLARE));
    }

    @Test
    public void ambientAerialShots() {
        AerialSceneView.AerialShot[] shots = AerialSceneView.SHOTS;
        assertNotNull(shots);
        assertTrue(shots.length >= 5);

        for (AerialSceneView.AerialShot shot : shots) {
            assertNotNull(shot.title);
            assertFalse(shot.title.isEmpty());
            assertNotNull(shot.location);
            assertFalse(shot.location.isEmpty());
            assertTrue(shot.skyTop != 0);
            assertTrue(shot.skyBottom != 0);
        }
    }

    @Test
    public void smartHomeConstantsAndDefaults() {
        assertEquals("movie_dim", SmartHomeManager.ACTION_MOVIE_DIM);
        assertEquals("lights_toggle", SmartHomeManager.ACTION_LIGHTS_TOGGLE);
        assertEquals("warm_cozy", SmartHomeManager.ACTION_WARM_COZY);
        assertEquals("all_off", SmartHomeManager.ACTION_ALL_OFF);

        String haUrl = SmartHomeManager.getHaBaseUrl(null);
        assertNotNull(haUrl);
        assertTrue(haUrl.startsWith("http"));

        String camUrl = SmartHomeManager.getCameraUrl(null);
        assertNotNull(camUrl);
        assertTrue(camUrl.startsWith("http"));
    }
}
