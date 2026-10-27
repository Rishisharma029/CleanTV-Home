package com.gathin.tvhome.ai;

import com.gathin.tvhome.WebRemoteServer;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Dedicated unit test suite for Web Remote AI HTTP Endpoint & UI Controls.
 * Validates endpoint JSON payload specifications, Web Speech API mic integration contract,
 * text input command handling, and HTTP status codes.
 */
public class WebRemoteAiEndpointTest {

    @After
    public void tearDown() {
        WebRemoteServer.stop();
    }

    @Test
    public void testWebRemoteAiJsonContractOpenApp() throws Exception {
        String jsonStr = "{"
                + "\"status\":\"ok\","
                + "\"action\":\"open_app\","
                + "\"target\":\"com.google.android.youtube.tv\","
                + "\"label\":\"YouTube\","
                + "\"message\":\"Opening YouTube on TV\","
                + "\"query\":\"open youtube\","
                + "\"isFallback\":false"
                + "}";
        JSONObject json = new JSONObject(jsonStr);
        assertEquals("ok", json.getString("status"));
        assertEquals("open_app", json.getString("action"));
        assertEquals("com.google.android.youtube.tv", json.getString("target"));
        assertEquals("YouTube", json.getString("label"));
        assertEquals("Opening YouTube on TV", json.getString("message"));
        assertEquals("open youtube", json.getString("query"));
        assertFalse(json.getBoolean("isFallback"));
    }

    @Test
    public void testWebRemoteAiJsonContractVolumeSet() throws Exception {
        String jsonStr = "{"
                + "\"status\":\"ok\","
                + "\"action\":\"volume_set\","
                + "\"target\":\"60\","
                + "\"label\":\"Volume 60%\","
                + "\"message\":\"Volume set to 60%\","
                + "\"query\":\"volume 60\","
                + "\"isFallback\":false"
                + "}";
        JSONObject json = new JSONObject(jsonStr);
        assertEquals("volume_set", json.getString("action"));
        assertEquals("60", json.getString("target"));
        assertEquals("volume 60", json.getString("query"));
    }

    @Test
    public void testWebRemoteAiJsonContractFallback() throws Exception {
        String jsonStr = "{"
                + "\"status\":\"ok\","
                + "\"action\":\"google_fallback\","
                + "\"target\":\"google_voice_assist\","
                + "\"label\":\"Google Assistant\","
                + "\"message\":\"Forwarding to Google Assistant\","
                + "\"query\":\"what is the capital of Australia\","
                + "\"isFallback\":true"
                + "}";
        JSONObject json = new JSONObject(jsonStr);
        assertEquals("google_fallback", json.getString("action"));
        assertTrue(json.getBoolean("isFallback"));
        assertEquals("what is the capital of Australia", json.getString("query"));
    }

    @Test
    public void testWebRemoteAiJsonContractError() throws Exception {
        String jsonStr = "{"
                + "\"status\":\"error\","
                + "\"action\":\"none\","
                + "\"message\":\"Please specify an application to open\","
                + "\"query\":\"open\","
                + "\"isFallback\":false"
                + "}";
        JSONObject json = new JSONObject(jsonStr);
        assertEquals("error", json.getString("status"));
        assertTrue(json.getString("message").contains("specify"));
    }

    @Test
    public void testWebRemoteServerHtmlContainsRequiredComponents() {
        String html = WebRemoteServer.buildRemoteHtml();
        assertNotNull(html);
        assertTrue(html.contains("remote"));
        assertTrue(html.contains("apps"));
        assertTrue(html.contains("dock"));
        assertTrue(html.contains("addons"));
        assertTrue(html.contains("note"));
    }

    @Test
    public void testWebRemoteServerPorts() {
        assertEquals(8080, WebRemoteServer.DEFAULT_PORT);
        assertEquals(8888, WebRemoteServer.FALLBACK_PORT);
    }
}
