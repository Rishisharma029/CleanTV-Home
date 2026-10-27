package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WebRemoteTest {

    @After
    public void tearDown() {
        WebRemoteServer.stop();
    }

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
        assertFalse("IP should not be loopback", "127.0.0.1".equals(ip));

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

    private void ensureServerStarted() throws Exception {
        WebRemoteServer.start(null);
        long startWait = System.currentTimeMillis();
        while (!WebRemoteServer.isRunning() && System.currentTimeMillis() - startWait < 3000) {
            Thread.sleep(50);
        }
        assertTrue("WebRemoteServer must be running", WebRemoteServer.isRunning());

        int port = WebRemoteServer.getActivePort();
        boolean ready = false;
        for (int i = 0; i < 20; i++) {
            try (Socket s = new Socket("127.0.0.1", port)) {
                ready = true;
                break;
            } catch (Throwable t) {
                Thread.sleep(50);
            }
        }
        assertTrue("Server socket must accept connection on port " + port, ready);
    }

    @Test
    public void webRemoteHttpServerServesRemoteHtmlAndCanvasWithin500Ms() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        long startTime = System.currentTimeMillis();
        URL url = new URL("http://127.0.0.1:" + port + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode);
        assertEquals("text/html; charset=utf-8", conn.getContentType());

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        long duration = System.currentTimeMillis() - startTime;
        assertTrue("Response must return within 500ms, took " + duration + "ms", duration < 500);

        String html = sb.toString();
        assertTrue("HTML should contain remote title", html.contains("CleanTV Remote"));
        assertTrue("HTML must include Note tab button", html.contains("id=\"tab-note\""));
        assertTrue("HTML must include Note canvas tab pane", html.contains("id=\"pane-note\""));
        assertTrue("HTML must include noteCanvas element", html.contains("id=\"noteCanvas\""));
        assertTrue("HTML must include palette color swatches", html.contains("class=\"palette-row\""));
    }

    @Test
    public void webRemoteHttpServerReturnsValidStatusJson() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        URL url = new URL("http://127.0.0.1:" + port + "/api/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode);
        assertEquals("application/json; charset=utf-8", conn.getContentType());

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        JSONObject json = new JSONObject(sb.toString());
        assertTrue(json.has("gameMode"));
        assertTrue(json.has("audioMode"));
        assertTrue(json.has("sleepTimer"));
        assertTrue(json.has("profile"));
        assertTrue(json.has("profileName"));
    }

    @Test
    public void webRemoteHttpServerHandlesCanvasStrokeAndStateSync() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        // 1. Clear note canvas
        URL clearUrl = new URL("http://127.0.0.1:" + port + "/api/canvas/clear");
        HttpURLConnection clearConn = (HttpURLConnection) clearUrl.openConnection();
        clearConn.setRequestMethod("POST");
        assertEquals(200, clearConn.getResponseCode());
        clearConn.disconnect();

        // 2. Post a drawing stroke
        URL strokeUrl = new URL("http://127.0.0.1:" + port + "/api/canvas/stroke");
        HttpURLConnection strokeConn = (HttpURLConnection) strokeUrl.openConnection();
        strokeConn.setRequestMethod("POST");
        strokeConn.setDoOutput(true);
        strokeConn.setRequestProperty("Content-Type", "application/json");

        String strokeJson = "{\"color\":\"#38BDF8\",\"width\":0.02,\"points\":[{\"x\":0.1,\"y\":0.2},{\"x\":0.5,\"y\":0.6}]}";
        try (OutputStream os = strokeConn.getOutputStream()) {
            os.write(strokeJson.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        assertEquals(200, strokeConn.getResponseCode());
        strokeConn.disconnect();

        // 3. Fetch canvas state
        URL stateUrl = new URL("http://127.0.0.1:" + port + "/api/canvas/state");
        HttpURLConnection stateConn = (HttpURLConnection) stateUrl.openConnection();
        stateConn.setRequestMethod("GET");
        assertEquals(200, stateConn.getResponseCode());

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stateConn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        stateConn.disconnect();

        JSONObject stateObj = new JSONObject(sb.toString());
        assertTrue("Canvas state must include strokes", stateObj.has("strokes"));
        assertTrue(stateObj.getJSONArray("strokes").length() >= 1);
    }

    @Test
    public void webRemoteHttpServerHandlesCorsOptionsPreflight() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        URL url = new URL("http://127.0.0.1:" + port + "/api/canvas/stroke");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("OPTIONS");
        conn.setRequestProperty("Origin", "http://example.com");
        conn.setRequestProperty("Access-Control-Request-Method", "POST");
        conn.setRequestProperty("Access-Control-Request-Headers", "Content-Type");

        assertEquals(200, conn.getResponseCode());
        assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"));
        String allowMethods = conn.getHeaderField("Access-Control-Allow-Methods");
        assertNotNull(allowMethods);
        assertTrue(allowMethods.contains("POST"));
        assertTrue(allowMethods.contains("OPTIONS"));
        conn.disconnect();
    }

    @Test
    public void webRemoteHttpServerResilientToKeepAliveStreams() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        // Send raw HTTP request with keep-alive over a direct TCP socket without closing client socket
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            OutputStream os = socket.getOutputStream();
            String rawHttp = "GET /api/status HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:" + port + "\r\n" +
                    "User-Agent: Mozilla/5.0 (Mobile)\r\n" +
                    "Connection: keep-alive\r\n\r\n";
            os.write(rawHttp.getBytes(StandardCharsets.US_ASCII));
            os.flush();

            InputStream is = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            assertNotNull("Server must return status line without hanging", statusLine);
            assertTrue(statusLine.contains("200 OK"));
        }
    }

    @Test
    public void webRemoteServerStopShutsDownSockets() throws Exception {
        ensureServerStarted();
        assertTrue(WebRemoteServer.isRunning());

        WebRemoteServer.stop();
        assertFalse(WebRemoteServer.isRunning());
    }

    @Test
    public void headRequestReturns200WithContentLengthAndNoBody() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        // 1. HEAD on root HTML
        URL url = new URL("http://127.0.0.1:" + port + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("HEAD");
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentLength() > 0);
        InputStream is = conn.getInputStream();
        byte[] buf = new byte[64];
        assertEquals("HEAD request must return 0 body bytes", -1, is.read(buf));
        conn.disconnect();

        // 2. HEAD on /api/status
        URL statusUrl = new URL("http://127.0.0.1:" + port + "/api/status");
        HttpURLConnection statusConn = (HttpURLConnection) statusUrl.openConnection();
        statusConn.setRequestMethod("HEAD");
        assertEquals(200, statusConn.getResponseCode());
        assertTrue(statusConn.getContentLength() > 0);
        InputStream statusIs = statusConn.getInputStream();
        assertEquals("HEAD request to /api/status must return 0 body bytes", -1, statusIs.read(buf));
        statusConn.disconnect();
    }

    @Test
    public void rapidStartConcurrentLifecycleNoPortMismatch() throws Exception {
        WebRemoteServer.stop();

        int threadCount = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.execute(() -> {
                try {
                    WebRemoteServer.start(null);
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue("All start threads should complete within 3s", latch.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS));
        pool.shutdown();

        assertTrue(WebRemoteServer.isRunning());
        int port = WebRemoteServer.getActivePort();
        assertTrue("Port must be standard 8080 or fallback 8888", port == 8080 || port == 8888);

        WebRemoteServer.stop();
        assertFalse(WebRemoteServer.isRunning());
    }

    @Test
    public void concurrentDrawingStrokesFromMultipleDevices() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        // Clear canvas
        URL clearUrl = new URL("http://127.0.0.1:" + port + "/api/canvas/clear");
        HttpURLConnection clearConn = (HttpURLConnection) clearUrl.openConnection();
        clearConn.setRequestMethod("POST");
        assertEquals(200, clearConn.getResponseCode());
        clearConn.disconnect();

        int clientCount = 10;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(clientCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(clientCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < clientCount; i++) {
            final int clientIdx = i;
            pool.execute(() -> {
                try {
                    URL strokeUrl = new URL("http://127.0.0.1:" + port + "/api/canvas/stroke");
                    HttpURLConnection strokeConn = (HttpURLConnection) strokeUrl.openConnection();
                    strokeConn.setRequestMethod("POST");
                    strokeConn.setDoOutput(true);
                    strokeConn.setRequestProperty("Content-Type", "application/json");

                    String strokeJson = String.format(java.util.Locale.US,
                            "{\"color\":\"#38BDF8\",\"width\":0.016,\"points\":[{\"x\":%f,\"y\":%f},{\"x\":%f,\"y\":%f}]}",
                            clientIdx * 0.05, clientIdx * 0.05, clientIdx * 0.05 + 0.02, clientIdx * 0.05 + 0.02);
                    try (OutputStream os = strokeConn.getOutputStream()) {
                        os.write(strokeJson.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                    if (strokeConn.getResponseCode() == 200) {
                        successCount.incrementAndGet();
                    }
                    strokeConn.disconnect();
                } catch (Throwable ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("All concurrent stroke posts should finish", latch.await(5000, java.util.concurrent.TimeUnit.MILLISECONDS));
        pool.shutdown();
        assertEquals("All 10 concurrent clients should successfully send strokes", clientCount, successCount.get());

        // Verify canvas state contains strokes
        URL stateUrl = new URL("http://127.0.0.1:" + port + "/api/canvas/state");
        HttpURLConnection stateConn = (HttpURLConnection) stateUrl.openConnection();
        stateConn.setRequestMethod("GET");
        assertEquals(200, stateConn.getResponseCode());

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stateConn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        stateConn.disconnect();

        JSONObject stateObj = new JSONObject(sb.toString());
        assertTrue("Canvas state must include strokes", stateObj.has("strokes"));
        assertEquals(clientCount, stateObj.getJSONArray("strokes").length());
    }

    @Test
    public void singleTapDotCanvasSync() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        // Clear canvas
        URL clearUrl = new URL("http://127.0.0.1:" + port + "/api/canvas/clear");
        HttpURLConnection clearConn = (HttpURLConnection) clearUrl.openConnection();
        clearConn.setRequestMethod("POST");
        assertEquals(200, clearConn.getResponseCode());
        clearConn.disconnect();

        // Send a single-point dot stroke
        URL strokeUrl = new URL("http://127.0.0.1:" + port + "/api/canvas/stroke");
        HttpURLConnection strokeConn = (HttpURLConnection) strokeUrl.openConnection();
        strokeConn.setRequestMethod("POST");
        strokeConn.setDoOutput(true);
        strokeConn.setRequestProperty("Content-Type", "application/json");

        String dotJson = "{\"color\":\"#FB7171\",\"width\":0.016,\"points\":[{\"x\":0.5,\"y\":0.5}]}";
        try (OutputStream os = strokeConn.getOutputStream()) {
            os.write(dotJson.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        assertEquals(200, strokeConn.getResponseCode());
        strokeConn.disconnect();

        // Verify canvas state contains the single-point stroke
        URL stateUrl = new URL("http://127.0.0.1:" + port + "/api/canvas/state");
        HttpURLConnection stateConn = (HttpURLConnection) stateUrl.openConnection();
        stateConn.setRequestMethod("GET");
        assertEquals(200, stateConn.getResponseCode());

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stateConn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        stateConn.disconnect();

        JSONObject stateObj = new JSONObject(sb.toString());
        assertTrue(stateObj.has("strokes"));
        assertEquals(1, stateObj.getJSONArray("strokes").length());
        JSONObject s = stateObj.getJSONArray("strokes").getJSONObject(0);
        assertEquals(1, s.getJSONArray("points").length());
    }

    @Test
    public void canvasStateJsonContainsStrokesArrayAndHtmlRedraws() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        URL url = new URL("http://127.0.0.1:" + port + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        assertEquals(200, conn.getResponseCode());

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        conn.disconnect();

        String html = sb.toString();
        // HTML must unpack data.strokes before passing to redrawNoteCanvas
        assertTrue("HTML JS must unpack data.strokes", html.contains("data.strokes"));
    }

    @Test
    public void headRequestReturns200WithContentLengthAndNoBodyOnAllEndpoints() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();
        String[] endpoints = new String[]{"/", "/api/status", "/api/canvas/state", "/api/dock", "/api/apps"};

        for (String ep : endpoints) {
            URL url = new URL("http://127.0.0.1:" + port + ep);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            assertEquals("HEAD on " + ep + " must return 200", 200, conn.getResponseCode());
            assertTrue("HEAD on " + ep + " must return Content-Length > 0", conn.getContentLength() > 0);
            InputStream is = conn.getInputStream();
            byte[] buf = new byte[64];
            assertEquals("HEAD on " + ep + " must return 0 body bytes", -1, is.read(buf));
            conn.disconnect();
        }
    }

    @Test
    public void proxyAbsoluteUriRequestsParsedCorrectly() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            OutputStream os = socket.getOutputStream();
            String rawHttp = "GET http://192.168.1.100:" + port + "/api/status HTTP/1.1\r\n" +
                    "Host: 192.168.1.100:" + port + "\r\n" +
                    "Connection: close\r\n\r\n";
            os.write(rawHttp.getBytes(StandardCharsets.US_ASCII));
            os.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            assertNotNull("Status line must not be null", statusLine);
            assertTrue("Must return 200 OK for absolute-URI: " + statusLine, statusLine.contains("200 OK"));

            StringBuilder body = new StringBuilder();
            String line;
            boolean inBody = false;
            while ((line = reader.readLine()) != null) {
                if (inBody) {
                    body.append(line);
                } else if (line.isEmpty()) {
                    inBody = true;
                }
            }
            JSONObject json = new JSONObject(body.toString());
            assertTrue(json.has("gameMode"));
            assertTrue(json.has("profile"));
        }
    }

    @Test
    public void initialCrlfSkippedBeforeRequestLine() throws Exception {
        ensureServerStarted();
        int port = WebRemoteServer.getActivePort();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            OutputStream os = socket.getOutputStream();
            String rawHttp = "\r\n\r\nGET /api/status HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:" + port + "\r\n" +
                    "Connection: close\r\n\r\n";
            os.write(rawHttp.getBytes(StandardCharsets.US_ASCII));
            os.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            assertNotNull("Server must tolerate leading empty lines and return status", statusLine);
            assertTrue(statusLine.contains("200 OK"));
        }
    }

    @Test
    public void noteCanvasHtmlIncludesResizeListenerAndZeroDimensionCheck() {
        String html = WebRemoteServer.buildRemoteHtml();
        assertNotNull(html);
        assertTrue("Must include zero-dimension check", html.contains("!rect.width || !rect.height"));
        assertTrue("Must include resize listener", html.contains("resize"));
    }
}

