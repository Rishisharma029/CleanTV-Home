package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WebRemoteTest {

    @Test
    public void qrCodeEncoding() {
        String testUrl = "http://192.168.1.100:8080";
        QrCode qr = QrCode.encodeText(testUrl, QrCode.Ecc.MEDIUM);
        assertNotNull(qr);
        assertTrue(qr.size >= 21); // Version 1 is 21x21, higher versions larger

        // Finder pattern check: Top-left corner (0,0) is dark
        assertTrue(qr.getModule(0, 0));
        assertTrue(qr.getModule(6, 0));
        assertTrue(qr.getModule(0, 6));
    }

    @Test
    public void webRemoteServerPortsAndUrls() {
        assertEquals(8080, WebRemoteServer.DEFAULT_PORT);
        assertEquals(8888, WebRemoteServer.FALLBACK_PORT);

        String ip = WebRemoteServer.getLocalIpAddress(null);
        assertNotNull(ip);
        assertFalse(ip.isEmpty());

        String url = WebRemoteServer.getServerUrl(null);
        assertNotNull(url);
        assertTrue(url.startsWith("http://"));
        assertTrue(url.contains(":8080") || url.contains(":8888"));
    }

    @Test
    public void appEntryInfoStructure() {
        MainActivity.AppEntryInfo entry = new MainActivity.AppEntryInfo("YouTube", "com.google.android.youtube.tv", 2);
        assertEquals("YouTube", entry.label);
        assertEquals("com.google.android.youtube.tv", entry.pkg);
        assertEquals(2, entry.pos);

        MainActivity.AppEntryInfo fallback = new MainActivity.AppEntryInfo(null, null, 0);
        assertEquals("", fallback.label);
        assertEquals("", fallback.pkg);
        assertEquals(0, fallback.pos);
    }
}
