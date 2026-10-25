package com.gathin.tvhome;

import android.net.Uri;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AddonCatalogTest {

    @Test
    public void catalogIntegrity() {
        List<AddonCatalog.AddonItem> catalog = AddonCatalog.getCatalog();
        assertNotNull(catalog);
        assertTrue(catalog.size() >= 5);

        for (AddonCatalog.AddonItem item : catalog) {
            assertNotNull(item.id);
            assertFalse(item.id.trim().isEmpty());
            assertNotNull(item.name);
            assertFalse(item.name.trim().isEmpty());
            assertNotNull(item.category);
            assertFalse(item.category.trim().isEmpty());
            assertNotNull(item.description);
            assertNotNull(item.pkg);
            assertFalse(item.pkg.trim().isEmpty());
            assertNotNull(item.downloadUrl);
            assertTrue(item.downloadUrl.startsWith("http://") || item.downloadUrl.startsWith("https://"));
        }
    }

    @Test
    public void findAddonById() {
        AddonCatalog.AddonItem smarttube = AddonCatalog.findById("smarttube");
        assertNotNull(smarttube);
        assertEquals("SmartTube", smarttube.name);
        assertEquals("com.teamsmart.videomanager.tv", smarttube.pkg);

        AddonCatalog.AddonItem kodi = AddonCatalog.findById("KODI");
        assertNotNull(kodi);
        assertEquals("org.xbmc.kodi", kodi.pkg);

        AddonCatalog.AddonItem vlc = AddonCatalog.findById("vlc");
        assertNotNull(vlc);
        assertEquals("org.videolan.vlc", vlc.pkg);

        assertNull(AddonCatalog.findById("non_existent_app_id"));
        assertNull(AddonCatalog.findById(null));
    }

    @Test
    public void fileProviderType() {
        CleanTVFileProvider provider = new CleanTVFileProvider();
        assertEquals("application/vnd.android.package-archive", provider.getType(null));
    }
}
