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
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Zero-install local HTTP micro-server serving the Mobile Web Remote & Keyboard:
 * - Hosted on local TV Wi-Fi IP (default port 8080 / 8888).
 * - Instant phone control with zero apps to install.
 * - Virtual keyboard text input, D-pad, volume, and TV quick settings.
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
            String method = parts[0];
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
            } else {
                // Serve HTML5 Remote Web App
                sendHtml(out, buildRemoteHtml());
            }
        } catch (Throwable ignored) {}
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
                "    body { background: #0b0f19; color: #fff; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; flex-direction: column; align-items: center; min-height: 100vh; padding: 18px 16px 32px; }\n" +
                "    .header { width: 100%; max-width: 380px; display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; padding: 0 4px; }\n" +
                "    .title { font-size: 19px; font-weight: 700; letter-spacing: -0.3px; display: flex; align-items: center; gap: 8px; }\n" +
                "    .dot { width: 8px; height: 8px; border-radius: 50%; background: #10b981; box-shadow: 0 0 10px #10b981; }\n" +
                "    .subtitle { font-size: 11px; color: #64748b; font-weight: 500; }\n" +
                "    .card { width: 100%; max-width: 380px; background: rgba(30, 41, 59, 0.7); backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.08); border-radius: 20px; padding: 14px; margin-bottom: 14px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); }\n" +
                "    .input-box { display: flex; gap: 8px; }\n" +
                "    input[type=text] { flex: 1; background: rgba(15, 23, 42, 0.8); border: 1px solid rgba(255,255,255,0.12); border-radius: 12px; color: #fff; padding: 12px 14px; font-size: 15px; outline: none; user-select: text; }\n" +
                "    input[type=text]:focus { border-color: #38bdf8; }\n" +
                "    .btn-send { background: #38bdf8; color: #0f172a; border: none; border-radius: 12px; font-weight: 700; padding: 0 18px; cursor: pointer; }\n" +
                "    .dpad-container { width: 260px; height: 260px; position: relative; margin: 12px auto; background: rgba(15, 23, 42, 0.9); border-radius: 50%; border: 1px solid rgba(255,255,255,0.1); box-shadow: inset 0 4px 20px rgba(0,0,0,0.6); }\n" +
                "    .dpad-btn { position: absolute; background: transparent; border: none; color: #e2e8f0; font-size: 20px; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: transform 0.08s, background 0.1s; }\n" +
                "    .dpad-btn:active { background: rgba(255,255,255,0.08); transform: scale(0.94); }\n" +
                "    .btn-up { top: 6px; left: 75px; width: 110px; height: 70px; border-radius: 55px 55px 0 0; }\n" +
                "    .btn-down { bottom: 6px; left: 75px; width: 110px; height: 70px; border-radius: 0 0 55px 55px; }\n" +
                "    .btn-left { top: 75px; left: 6px; width: 70px; height: 110px; border-radius: 55px 0 0 55px; }\n" +
                "    .btn-right { top: 75px; right: 6px; width: 70px; height: 110px; border-radius: 0 55px 55px 0; }\n" +
                "    .btn-center { top: 80px; left: 80px; width: 100px; height: 100px; border-radius: 50%; background: #1e293b; border: 1px solid rgba(255,255,255,0.15); font-weight: 700; font-size: 15px; color: #38bdf8; box-shadow: 0 4px 15px rgba(0,0,0,0.5); }\n" +
                "    .btn-center:active { transform: scale(0.92); background: #334155; }\n" +
                "    .grid-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; width: 100%; max-width: 380px; margin-bottom: 12px; }\n" +
                "    .btn-ctrl { background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; height: 54px; color: #fff; font-size: 13px; font-weight: 600; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; cursor: pointer; transition: transform 0.08s, background 0.1s; }\n" +
                "    .btn-ctrl:active { transform: scale(0.95); background: rgba(255,255,255,0.12); }\n" +
                "    .icon { font-size: 17px; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"header\">\n" +
                "    <div>\n" +
                "      <div class=\"title\"><span class=\"dot\"></span>CleanTV Remote</div>\n" +
                "      <div class=\"subtitle\">Zero-Install Local Web Controller</div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "  <div class=\"card\">\n" +
                "    <form class=\"input-box\" onsubmit=\"sendType(event)\">\n" +
                "      <input type=\"text\" id=\"txtInput\" placeholder=\"Type on TV keyboard...\" autocomplete=\"off\">\n" +
                "      <button type=\"submit\" class=\"btn-send\">Send</button>\n" +
                "    </form>\n" +
                "  </div>\n" +
                "  <div class=\"dpad-container\">\n" +
                "    <button class=\"dpad-btn btn-up\" onclick=\"sendKey(19)\">&#9650;</button>\n" +
                "    <button class=\"dpad-btn btn-down\" onclick=\"sendKey(20)\">&#9660;</button>\n" +
                "    <button class=\"dpad-btn btn-left\" onclick=\"sendKey(21)\">&#9664;</button>\n" +
                "    <button class=\"dpad-btn btn-right\" onclick=\"sendKey(22)\">&#9654;</button>\n" +
                "    <button class=\"dpad-btn btn-center\" onclick=\"sendKey(23)\">OK</button>\n" +
                "  </div>\n" +
                "  <div class=\"grid-row\">\n" +
                "    <button class=\"btn-ctrl\" onclick=\"sendKey(4)\"><span class=\"icon\">&#8617;</span>Back</button>\n" +
                "    <button class=\"btn-ctrl\" onclick=\"sendKey(3)\"><span class=\"icon\">&#8962;</span>Home</button>\n" +
                "    <button class=\"btn-ctrl\" onclick=\"sendAction('control_center')\"><span class=\"icon\">&#9881;</span>HUD</button>\n" +
                "  </div>\n" +
                "  <div class=\"grid-row\">\n" +
                "    <button class=\"btn-ctrl\" onclick=\"sendKey(25)\"><span class=\"icon\">&#128265;</span>Vol -</button>\n" +
                "    <button class=\"btn-ctrl\" onclick=\"sendKey(164)\"><span class=\"icon\">&#128263;</span>Mute</button>\n" +
                "    <button class=\"btn-ctrl\" onclick=\"sendKey(24)\"><span class=\"icon\">&#128266;</span>Vol +</button>\n" +
                "  </div>\n" +
                "  <div class=\"grid-row\">\n" +
                "    <button class=\"btn-ctrl\" onclick=\"sendAction('audio_cycle')\"><span class=\"icon\">&#127911;</span>Audio</button>\n" +
                "    <button class=\"btn-ctrl\" onclick=\"sendAction('gamemode')\"><span class=\"icon\">&#127918;</span>Game</button>\n" +
                "    <button class=\"btn-ctrl\" onclick=\"sendAction('sleeptimer')\"><span class=\"icon\">&#9201;</span>Sleep</button>\n" +
                "  </div>\n" +
                "  <script>\n" +
                "    function haptic() { if (navigator.vibrate) navigator.vibrate(25); }\n" +
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
                "      inp.value = '';\n" +
                "    }\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
