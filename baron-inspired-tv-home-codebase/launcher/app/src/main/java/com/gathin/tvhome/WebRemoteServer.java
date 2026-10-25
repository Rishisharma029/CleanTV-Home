package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Zero-install local HTTP micro-server serving the Mobile Web Remote & Keyboard:
 * - Hosted on local TV Wi-Fi IP (default port 8080 / 8888).
 * - Instant phone control with zero apps to install.
 * - Virtual keyboard text input, D-pad, volume, and TV quick settings.
 * - 1-Tap App Launcher and real-time drag-and-drop dock reordering.
 */
public final class WebRemoteServer {

    public static final int DEFAULT_PORT = 8080;
    public static final int FALLBACK_PORT = 8888;

    private static ServerSocket serverSocket;
    private static int activePort = DEFAULT_PORT;
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static ExecutorService serverExecutor;
    private static WeakReference<Activity> currentActivityRef;
    private static Handler mainHandler;

    private static synchronized Handler getMainHandler() {
        if (mainHandler == null) {
            try {
                Looper looper = Looper.getMainLooper();
                if (looper != null) {
                    mainHandler = new Handler(looper);
                }
            } catch (Throwable ignored) {}
        }
        return mainHandler;
    }

    private WebRemoteServer() {}

    public static synchronized void start(Activity activity) {
        if (activity != null) {
            currentActivityRef = new WeakReference<>(activity);
        }
        if (isRunning.get()) return;

        isRunning.set(true);
        serverExecutor = Executors.newCachedThreadPool();

        serverExecutor.execute(() -> {
            try {
                try {
                    serverSocket = new ServerSocket(DEFAULT_PORT);
                    activePort = DEFAULT_PORT;
                } catch (Throwable t) {
                    serverSocket = new ServerSocket(FALLBACK_PORT);
                    activePort = FALLBACK_PORT;
                }

                while (isRunning.get() && !serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    serverExecutor.execute(() -> handleClient(client));
                }
            } catch (Throwable ignored) {
            } finally {
                isRunning.set(false);
            }
        });
    }

    public static synchronized void stop() {
        isRunning.set(false);
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Throwable ignored) {}
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            serverExecutor = null;
        }
    }

    public static boolean isRunning() {
        return isRunning.get();
    }

    public static int getActivePort() {
        return activePort;
    }

    public static String getLocalIpAddress(Context context) {
        try {
            if (context != null) {
                WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) {
                    int ipInt = wm.getConnectionInfo().getIpAddress();
                    if (ipInt != 0) {
                        return String.format(Locale.US, "%d.%d.%d.%d",
                                (ipInt & 0xff),
                                (ipInt >> 8 & 0xff),
                                (ipInt >> 16 & 0xff),
                                (ipInt >> 24 & 0xff));
                    }
                }
            }
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIp = intf.getInetAddresses(); enumIp.hasMoreElements();) {
                    InetAddress addr = enumIp.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "192.168.1.100";
    }

    public static String getServerUrl(Context context) {
        return "http://" + getLocalIpAddress(context) + ":" + activePort;
    }

    private static void handleClient(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = socket.getOutputStream()) {

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String uri = parts[1];

            if (uri.startsWith("/api/key")) {
                int code = extractQueryParamInt(uri, "code", -1);
                if (code > 0) {
                    dispatchKeyEvent(code);
                }
                sendJson(out, "{\"status\":\"ok\",\"action\":\"key\",\"code\":" + code + "}");
            } else if (uri.startsWith("/api/type")) {
                String text = extractQueryParam(uri, "text");
                if (text != null && !text.isEmpty()) {
                    dispatchTextInput(text);
                }
                sendJson(out, "{\"status\":\"ok\",\"action\":\"type\"}");
            } else if (uri.startsWith("/api/action")) {
                String cmd = extractQueryParam(uri, "cmd");
                executeAction(cmd);
                sendJson(out, "{\"status\":\"ok\",\"action\":\"" + (cmd != null ? cmd : "") + "\"}");
            } else if (uri.startsWith("/api/status")) {
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                boolean gameMode = act != null && GameModeManager.isGameModeEnabled(act);
                int audioMode = act != null ? AudioOutputManager.getCurrentMode(act) : 0;
                String audioName = AudioOutputManager.getModeName(audioMode);
                String sleepTimer = SleepTimerManager.getFormattedCountdown();

                String json = String.format(Locale.US,
                        "{\"gameMode\":%b,\"audioMode\":\"%s\",\"sleepTimer\":\"%s\"}",
                        gameMode, audioName, sleepTimer);
                sendJson(out, json);
            } else if (uri.startsWith("/api/dock")) {
                List<MainActivity.AppEntryInfo> apps = fetchAppsFromMain(true);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < apps.size(); i++) {
                    if (i > 0) sb.append(",");
                    MainActivity.AppEntryInfo app = apps.get(i);
                    sb.append("{\"pos\":").append(app.pos)
                      .append(",\"label\":\"").append(escapeJson(app.label))
                      .append("\",\"pkg\":\"").append(escapeJson(app.pkg))
                      .append("\"}");
                }
                sb.append("]");
                sendJson(out, sb.toString());
            } else if (uri.startsWith("/api/apps")) {
                List<MainActivity.AppEntryInfo> apps = fetchAppsFromMain(false);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < apps.size(); i++) {
                    if (i > 0) sb.append(",");
                    MainActivity.AppEntryInfo app = apps.get(i);
                    sb.append("{\"pos\":").append(app.pos)
                      .append(",\"label\":\"").append(escapeJson(app.label))
                      .append("\",\"pkg\":\"").append(escapeJson(app.pkg))
                      .append("\"}");
                }
                sb.append("]");
                sendJson(out, sb.toString());
            } else if (uri.startsWith("/api/reorder")) {
                int from = extractQueryParamInt(uri, "from", -1);
                int to = extractQueryParamInt(uri, "to", -1);
                boolean success = false;
                if (from >= 0 && to >= 0 && from != to) {
                    Handler h = getMainHandler();
                    if (h != null) {
                        h.post(() -> {
                            Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                            if (act instanceof MainActivity) {
                                ((MainActivity) act).reorderDockSlot(from, to);
                            }
                        });
                        success = true;
                    }
                }
                sendJson(out, "{\"status\":\"" + (success ? "ok" : "error") + "\",\"from\":" + from + ",\"to\":" + to + "}");
            } else if (uri.startsWith("/api/launch")) {
                String pkg = extractQueryParam(uri, "pkg");
                boolean success = false;
                if (pkg != null && !pkg.isEmpty()) {
                    Handler h = getMainHandler();
                    if (h != null) {
                        h.post(() -> {
                            Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                            if (act instanceof MainActivity) {
                                ((MainActivity) act).launchAppByPackage(pkg);
                            }
                        });
                        success = true;
                    }
                }
                sendJson(out, "{\"status\":\"" + (success ? "ok" : "error") + "\",\"pkg\":\"" + (pkg != null ? pkg : "") + "\"}");
            } else {
                // Serve HTML5 Remote Web App
                sendHtml(out, buildRemoteHtml());
            }
        } catch (Throwable ignored) {}
    }

    private static List<MainActivity.AppEntryInfo> fetchAppsFromMain(boolean dockOnly) {
        Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
        if (!(act instanceof MainActivity)) return Collections.emptyList();
        MainActivity main = (MainActivity) act;

        Handler h = getMainHandler();
        if (h == null) {
            try {
                return dockOnly ? main.getDockAppsList() : main.getAllAppsList();
            } catch (Throwable ignored) {
                return Collections.emptyList();
            }
        }

        AtomicReference<List<MainActivity.AppEntryInfo>> ref = new AtomicReference<>(Collections.emptyList());
        CountDownLatch latch = new CountDownLatch(1);

        h.post(() -> {
            try {
                ref.set(dockOnly ? main.getDockAppsList() : main.getAllAppsList());
            } catch (Throwable ignored) {
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}

        return ref.get();
    }

    private static void dispatchKeyEvent(int keyCode) {
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)});
        } catch (Throwable ignored) {}
    }

    private static void dispatchTextInput(String rawText) {
        try {
            String sanitized = rawText.replace(" ", "%s");
            Runtime.getRuntime().exec(new String[]{"input", "text", sanitized});
        } catch (Throwable ignored) {}
    }

    private static void executeAction(String cmd) {
        if (cmd == null) return;
        Handler h = getMainHandler();
        if (h != null) {
            h.post(() -> {
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                if (act instanceof MainActivity) {
                    MainActivity main = (MainActivity) act;
                    switch (cmd) {
                        case "control_center":
                            main.showControlCenter();
                            break;
                        case "gamemode":
                            GameModeManager.toggle(main);
                            break;
                        case "audio_cycle":
                            AudioOutputManager.cycleNextMode(main);
                            break;
                        case "sleeptimer":
                            SleepTimerManager.cycleNext(main);
                            break;
                    }
                }
            });
        }
    }

    private static String extractQueryParam(String uri, String param) {
        try {
            int qIdx = uri.indexOf('?');
            if (qIdx == -1) return null;
            String query = uri.substring(qIdx + 1);
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2 && kv[0].equals(param)) {
                    return URLDecoder.decode(kv[1], "UTF-8");
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int extractQueryParamInt(String uri, String param, int def) {
        String val = extractQueryParam(uri, param);
        if (val == null) return def;
        try {
            return Integer.parseInt(val);
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\b') sb.append("\\b");
            else if (c == '\f') sb.append("\\f");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        return sb.toString();
    }

    private static void sendJson(OutputStream out, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    private static void sendHtml(OutputStream out, String html) throws Exception {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    private static String buildRemoteHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover\">\n" +
                "  <meta name=\"apple-mobile-web-app-capable\" content=\"yes\">\n" +
                "  <meta name=\"theme-color\" content=\"#0b0f19\">\n" +
                "  <title>CleanTV Remote</title>\n" +
                "  <style>\n" +
                "    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; margin: 0; padding: 0; user-select: none; }\n" +
                "    body { background: #0b0f19; color: #fff; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; flex-direction: column; align-items: center; min-height: 100vh; padding: 16px 14px 40px; }\n" +
                "    .header { width: 100%; max-width: 380px; display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; padding: 0 4px; }\n" +
                "    .title { font-size: 19px; font-weight: 700; letter-spacing: -0.3px; display: flex; align-items: center; gap: 8px; }\n" +
                "    .dot { width: 8px; height: 8px; border-radius: 50%; background: #10b981; box-shadow: 0 0 10px #10b981; }\n" +
                "    .subtitle { font-size: 11px; color: #64748b; font-weight: 500; }\n" +
                "    .tabs { display: flex; width: 100%; max-width: 380px; background: rgba(30, 41, 59, 0.6); border-radius: 14px; padding: 4px; margin-bottom: 14px; gap: 4px; border: 1px solid rgba(255,255,255,0.06); }\n" +
                "    .tab-btn { flex: 1; background: transparent; border: none; color: #94a3b8; padding: 9px 0; font-size: 13px; font-weight: 600; border-radius: 10px; cursor: pointer; transition: all 0.15s ease; display: flex; align-items: center; justify-content: center; gap: 6px; }\n" +
                "    .tab-btn.active { background: #38bdf8; color: #0b0f19; font-weight: 700; box-shadow: 0 2px 10px rgba(56, 189, 248, 0.35); }\n" +
                "    .tab-content { width: 100%; max-width: 380px; }\n" +
                "    .card { background: rgba(30, 41, 59, 0.7); backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.08); border-radius: 20px; padding: 14px; margin-bottom: 14px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); }\n" +
                "    .input-box { display: flex; gap: 8px; }\n" +
                "    input[type=text] { flex: 1; background: rgba(15, 23, 42, 0.8); border: 1px solid rgba(255,255,255,0.12); border-radius: 12px; color: #fff; padding: 12px 14px; font-size: 15px; outline: none; user-select: text; }\n" +
                "    input[type=text]:focus { border-color: #38bdf8; }\n" +
                "    .btn-send { background: #38bdf8; color: #0f172a; border: none; border-radius: 12px; font-weight: 700; padding: 0 18px; cursor: pointer; }\n" +
                "    .dpad-container { width: 250px; height: 250px; position: relative; margin: 10px auto 14px; background: rgba(15, 23, 42, 0.9); border-radius: 50%; border: 1px solid rgba(255,255,255,0.1); box-shadow: inset 0 4px 20px rgba(0,0,0,0.6); }\n" +
                "    .dpad-btn { position: absolute; background: transparent; border: none; color: #e2e8f0; font-size: 20px; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: transform 0.08s, background 0.1s; }\n" +
                "    .dpad-btn:active { background: rgba(255,255,255,0.08); transform: scale(0.94); }\n" +
                "    .btn-up { top: 6px; left: 70px; width: 110px; height: 68px; border-radius: 55px 55px 0 0; }\n" +
                "    .btn-down { bottom: 6px; left: 70px; width: 110px; height: 68px; border-radius: 0 0 55px 55px; }\n" +
                "    .btn-left { top: 70px; left: 6px; width: 68px; height: 110px; border-radius: 55px 0 0 55px; }\n" +
                "    .btn-right { top: 70px; right: 6px; width: 68px; height: 110px; border-radius: 0 55px 55px 0; }\n" +
                "    .btn-center { top: 76px; left: 76px; width: 98px; height: 98px; border-radius: 50%; background: #1e293b; border: 1px solid rgba(255,255,255,0.15); font-weight: 700; font-size: 15px; color: #38bdf8; box-shadow: 0 4px 15px rgba(0,0,0,0.5); }\n" +
                "    .btn-center:active { transform: scale(0.92); background: #334155; }\n" +
                "    .grid-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; width: 100%; margin-bottom: 12px; }\n" +
                "    .btn-ctrl { background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; height: 52px; color: #fff; font-size: 13px; font-weight: 600; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; cursor: pointer; transition: transform 0.08s, background 0.1s; }\n" +
                "    .btn-ctrl:active { transform: scale(0.95); background: rgba(255,255,255,0.12); }\n" +
                "    .icon { font-size: 16px; }\n" +
                "    .search-box { width: 100%; margin-bottom: 12px; }\n" +
                "    .app-card { display: flex; align-items: center; background: rgba(30, 41, 59, 0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; padding: 10px 12px; margin-bottom: 8px; cursor: pointer; transition: all 0.15s ease; }\n" +
                "    .app-card:active { background: rgba(56, 189, 248, 0.15); border-color: rgba(56, 189, 248, 0.4); transform: scale(0.98); }\n" +
                "    .app-icon { width: 38px; height: 38px; border-radius: 10px; background: linear-gradient(135deg, #38bdf8, #818cf8); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 16px; color: #0f172a; margin-right: 12px; flex-shrink: 0; }\n" +
                "    .app-details { flex: 1; min-width: 0; }\n" +
                "    .app-title { font-size: 14px; font-weight: 600; color: #f1f5f9; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
                "    .app-pkg { font-size: 11px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
                "    .btn-launch { background: rgba(56, 189, 248, 0.2); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.4); border-radius: 8px; padding: 5px 12px; font-size: 12px; font-weight: 600; flex-shrink: 0; }\n" +
                "    .dock-hint { font-size: 12px; color: #94a3b8; text-align: center; margin-bottom: 12px; line-height: 1.4; }\n" +
                "    .dock-item { display: flex; align-items: center; background: rgba(30, 41, 59, 0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; padding: 10px 12px; margin-bottom: 8px; transition: transform 0.15s, background 0.15s; }\n" +
                "    .dock-item.dragging { opacity: 0.4; border-style: dashed; }\n" +
                "    .dock-item.over { border-color: #38bdf8; background: rgba(56, 189, 248, 0.1); }\n" +
                "    .drag-handle { font-size: 18px; color: #64748b; padding: 0 10px 0 2px; cursor: grab; }\n" +
                "    .pos-badge { background: #0f172a; color: #38bdf8; font-size: 11px; font-weight: 700; border-radius: 6px; padding: 3px 6px; margin-right: 10px; border: 1px solid rgba(56,189,248,0.2); }\n" +
                "    .dock-details { flex: 1; min-width: 0; }\n" +
                "    .dock-title { font-size: 14px; font-weight: 600; color: #f1f5f9; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
                "    .dock-pkg { font-size: 11px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
                "    .dock-actions { display: flex; gap: 6px; flex-shrink: 0; }\n" +
                "    .btn-arrow { width: 32px; height: 32px; border-radius: 8px; background: rgba(15, 23, 42, 0.8); border: 1px solid rgba(255,255,255,0.1); color: #cbd5e1; font-size: 13px; display: flex; align-items: center; justify-content: center; cursor: pointer; }\n" +
                "    .btn-arrow:active:not(:disabled) { background: #38bdf8; color: #0b0f19; }\\n" +
                "    .btn-arrow:disabled { opacity: 0.25; cursor: default; }\n" +
                "    .empty-state { text-align: center; color: #64748b; font-size: 13px; padding: 24px; }\n" +
                "    .toast { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%) translateY(80px); background: #10b981; color: #042f2e; font-weight: 700; font-size: 13px; padding: 9px 18px; border-radius: 9999px; box-shadow: 0 8px 20px rgba(0,0,0,0.4); transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.25s; opacity: 0; pointer-events: none; z-index: 1000; }\n" +
                "    .toast.show { transform: translateX(-50%) translateY(0); opacity: 1; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"header\">\n" +
                "    <div>\n" +
                "      <div class=\"title\"><span class=\"dot\"></span>CleanTV Remote</div>\n" +
                "      <div class=\"subtitle\">Zero-Install Controller & Dock Editor</div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "  <div class=\"tabs\">\n" +
                "    <button class=\"tab-btn active\" id=\"tab-remote\" onclick=\"switchTab('remote')\">🎮 Remote</button>\n" +
                "    <button class=\"tab-btn\" id=\"tab-apps\" onclick=\"switchTab('apps')\">📱 Apps</button>\n" +
                "    <button class=\"tab-btn\" id=\"tab-dock\" onclick=\"switchTab('dock')\">🗂️ Reorder Dock</button>\n" +
                "  </div>\n" +
                "  <div id=\"pane-remote\" class=\"tab-content\">\n" +
                "    <div class=\"card\">\n" +
                "      <form class=\"input-box\" onsubmit=\"sendType(event)\">\n" +
                "        <input type=\"text\" id=\"txtInput\" placeholder=\"Type on TV keyboard...\" autocomplete=\"off\">\n" +
                "        <button type=\"submit\" class=\"btn-send\">Send</button>\n" +
                "      </form>\n" +
                "    </div>\n" +
                "    <div class=\"dpad-container\">\n" +
                "      <button class=\"dpad-btn btn-up\" onclick=\"sendKey(19)\">&#9650;</button>\n" +
                "      <button class=\"dpad-btn btn-down\" onclick=\"sendKey(20)\">&#9660;</button>\n" +
                "      <button class=\"dpad-btn btn-left\" onclick=\"sendKey(21)\">&#9664;</button>\n" +
                "      <button class=\"dpad-btn btn-right\" onclick=\"sendKey(22)\">&#9654;</button>\n" +
                "      <button class=\"dpad-btn btn-center\" onclick=\"sendKey(23)\">OK</button>\n" +
                "    </div>\n" +
                "    <div class=\"grid-row\">\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendKey(4)\"><span class=\"icon\">&#8617;</span>Back</button>\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendKey(3)\"><span class=\"icon\">&#8962;</span>Home</button>\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendAction('control_center')\"><span class=\"icon\">&#9881;</span>HUD</button>\n" +
                "    </div>\n" +
                "    <div class=\"grid-row\">\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendKey(25)\"><span class=\"icon\">&#128265;</span>Vol -</button>\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendKey(164)\"><span class=\"icon\">&#128263;</span>Mute</button>\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendKey(24)\"><span class=\"icon\">&#128266;</span>Vol +</button>\n" +
                "    </div>\n" +
                "    <div class=\"grid-row\">\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendAction('audio_cycle')\"><span class=\"icon\">&#127911;</span>Audio</button>\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendAction('gamemode')\"><span class=\"icon\">&#127918;</span>Game</button>\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendAction('sleeptimer')\"><span class=\"icon\">&#9201;</span>Sleep</button>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "  <div id=\"pane-apps\" class=\"tab-content\" style=\"display:none;\">\n" +
                "    <div class=\"search-box\">\n" +
                "      <input type=\"text\" id=\"appSearch\" placeholder=\"Search TV apps...\" oninput=\"filterApps()\">\n" +
                "    </div>\n" +
                "    <div id=\"appsList\"></div>\n" +
                "  </div>\n" +
                "  <div id=\"pane-dock\" class=\"tab-content\" style=\"display:none;\">\n" +
                "    <div class=\"dock-hint\">Drag items or tap 🔼 / 🔽 to reorder TV dock icons in real time.</div>\n" +
                "    <div id=\"dockList\"></div>\n" +
                "  </div>\n" +
                "  <div id=\"toast\" class=\"toast\"></div>\n" +
                "  <script>\n" +
                "    function haptic() { if (navigator.vibrate) navigator.vibrate(22); }\n" +
                "    let toastTimer = null;\n" +
                "    function showToast(msg) {\n" +
                "      const t = document.getElementById('toast');\n" +
                "      t.innerText = msg;\n" +
                "      t.classList.add('show');\n" +
                "      clearTimeout(toastTimer);\n" +
                "      toastTimer = setTimeout(() => t.classList.remove('show'), 2000);\n" +
                "    }\n" +
                "    function sendKey(code) {\n" +
                "      haptic();\n" +
                "      fetch('/api/key?code=' + code, { method: 'POST' }).catch(()=>{});\n" +
                "    }\n" +
                "    function sendAction(cmd) {\n" +
                "      haptic();\n" +
                "      fetch('/api/action?cmd=' + cmd, { method: 'POST' }).catch(()=>{});\n" +
                "    }\n" +
                "    function sendType(e) {\n" +
                "      e.preventDefault();\n" +
                "      haptic();\n" +
                "      const inp = document.getElementById('txtInput');\n" +
                "      if (!inp.value) return;\n" +
                "      fetch('/api/type?text=' + encodeURIComponent(inp.value), { method: 'POST' });\n" +
                "      showToast('Sent to TV keyboard');\n" +
                "      inp.value = '';\n" +
                "    }\n" +
                "    function switchTab(tab) {\n" +
                "      haptic();\n" +
                "      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));\n" +
                "      document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');\n" +
                "      const btn = document.getElementById('tab-' + tab);\n" +
                "      const pane = document.getElementById('pane-' + tab);\n" +
                "      if (btn) btn.classList.add('active');\n" +
                "      if (pane) pane.style.display = 'block';\n" +
                "      if (tab === 'apps') loadApps();\n" +
                "      if (tab === 'dock') loadDock();\n" +
                "    }\n" +
                "    let allApps = [];\n" +
                "    function loadApps() {\n" +
                "      fetch('/api/apps').then(r => r.json()).then(data => {\n" +
                "        allApps = data || [];\n" +
                "        filterApps();\n" +
                "      }).catch(()=>{});\n" +
                "    }\n" +
                "    function renderApps(list) {\n" +
                "      const c = document.getElementById('appsList');\n" +
                "      if (!list || list.length === 0) {\n" +
                "        c.innerHTML = '<div class=\"empty-state\">No apps found</div>';\n" +
                "        return;\n" +
                "      }\n" +
                "      c.innerHTML = list.map(a => {\n" +
                "        const init = (a.label || '?').charAt(0).toUpperCase();\n" +
                "        const encPkg = encodeURIComponent(a.pkg);\n" +
                "        const encLbl = encodeURIComponent(a.label);\n" +
                "        return `<div class=\"app-card\" onclick=\"launchApp('${encPkg}', '${encLbl}')\">` +\n" +
                "          `<div class=\"app-icon\">${init}</div>` +\n" +
                "          `<div class=\"app-details\"><div class=\"app-title\">${a.label}</div><div class=\"app-pkg\">${a.pkg}</div></div>` +\n" +
                "          `<button class=\"btn-launch\">Launch</button>` +\n" +
                "        `</div>`;\n" +
                "      }).join('');\n" +
                "    }\n" +
                "    function filterApps() {\n" +
                "      const q = (document.getElementById('appSearch').value || '').toLowerCase();\n" +
                "      const filtered = allApps.filter(a => (a.label||'').toLowerCase().includes(q) || (a.pkg||'').toLowerCase().includes(q));\n" +
                "      renderApps(filtered);\n" +
                "    }\n" +
                "    function launchApp(pkgEnc, labelEnc) {\n" +
                "      haptic();\n" +
                "      const pkg = decodeURIComponent(pkgEnc);\n" +
                "      const label = decodeURIComponent(labelEnc);\n" +
                "      showToast('Launching ' + label + ' on TV...');\n" +
                "      fetch('/api/launch?pkg=' + encodeURIComponent(pkg), { method: 'POST' }).catch(()=>{});\n" +
                "    }\n" +
                "    let dockApps = [];\n" +
                "    function loadDock() {\n" +
                "      fetch('/api/dock').then(r => r.json()).then(data => {\n" +
                "        dockApps = data || [];\n" +
                "        renderDock();\n" +
                "      }).catch(()=>{});\n" +
                "    }\n" +
                "    function renderDock() {\n" +
                "      const c = document.getElementById('dockList');\n" +
                "      if (!dockApps || dockApps.length === 0) {\n" +
                "        c.innerHTML = '<div class=\"empty-state\">No dock apps loaded</div>';\n" +
                "        return;\n" +
                "      }\n" +
                "      c.innerHTML = dockApps.map((a, idx) => {\n" +
                "        return `<div class=\"dock-item\" draggable=\"true\" data-index=\"${idx}\">` +\n" +
                "          `<div class=\"drag-handle\">&#8942;&#8942;</div>` +\n" +
                "          `<div class=\"pos-badge\">#${idx + 1}</div>` +\n" +
                "          `<div class=\"dock-details\"><div class=\"dock-title\">${a.label}</div><div class=\"dock-pkg\">${a.pkg}</div></div>` +\n" +
                "          `<div class=\"dock-actions\">` +\n" +
                "            `<button class=\"btn-arrow\" onclick=\"moveDock(${idx}, ${idx - 1})\" ${idx === 0 ? 'disabled' : ''}>&#9650;</button>` +\n" +
                "            `<button class=\"btn-arrow\" onclick=\"moveDock(${idx}, ${idx + 1})\" ${idx === dockApps.length - 1 ? 'disabled' : ''}>&#9660;</button>` +\n" +
                "          `</div>` +\n" +
                "        `</div>`;\n" +
                "      }).join('');\n" +
                "      attachDragEvents();\n" +
                "    }\n" +
                "    function moveDock(from, to) {\n" +
                "      if (from < 0 || to < 0 || from >= dockApps.length || to >= dockApps.length || from === to) return;\n" +
                "      haptic();\n" +
                "      const moved = dockApps.splice(from, 1)[0];\n" +
                "      dockApps.splice(to, 0, moved);\n" +
                "      renderDock();\n" +
                "      showToast('Reordered dock on TV!');\n" +
                "      fetch('/api/reorder?from=' + from + '&to=' + to, { method: 'POST' }).catch(()=>{});\n" +
                "    }\n" +
                "    let dragSrcEl = null;\n" +
                "    function attachDragEvents() {\n" +
                "      const items = document.querySelectorAll('.dock-item');\n" +
                "      items.forEach(item => {\n" +
                "        item.addEventListener('dragstart', function(e) {\n" +
                "          dragSrcEl = this;\n" +
                "          e.dataTransfer.effectAllowed = 'move';\n" +
                "          this.classList.add('dragging');\n" +
                "        });\n" +
                "        item.addEventListener('dragover', function(e) {\n" +
                "          if (e.preventDefault) e.preventDefault();\n" +
                "          e.dataTransfer.dropEffect = 'move';\n" +
                "          return false;\n" +
                "        });\n" +
                "        item.addEventListener('dragenter', function() { this.classList.add('over'); });\n" +
                "        item.addEventListener('dragleave', function() { this.classList.remove('over'); });\n" +
                "        item.addEventListener('drop', function(e) {\n" +
                "          if (e.stopPropagation) e.stopPropagation();\n" +
                "          if (dragSrcEl && dragSrcEl !== this) {\n" +
                "            const fromIdx = parseInt(dragSrcEl.getAttribute('data-index'));\n" +
                "            const toIdx = parseInt(this.getAttribute('data-index'));\n" +
                "            moveDock(fromIdx, toIdx);\n" +
                "          }\n" +
                "          return false;\n" +
                "        });\n" +
                "        item.addEventListener('dragend', function() {\n" +
                "          this.classList.remove('dragging');\n" +
                "          items.forEach(i => i.classList.remove('over'));\n" +
                "        });\n" +
                "      });\n" +
                "    }\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
