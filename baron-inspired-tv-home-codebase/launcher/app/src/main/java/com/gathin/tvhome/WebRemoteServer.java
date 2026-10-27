package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Zero-install local HTTP micro-server serving the Mobile Web Remote & Add-on Package Manager:
 * - Hosted on local TV Wi-Fi IP (default port 8080 / 8888).
 * - Instant phone & PC control with zero apps to install.
 * - Virtual keyboard text input, D-pad, volume, and TV quick settings.
 * - 1-Tap App Launcher and real-time drag-and-drop dock reordering.
 * - Built-in CleanTV Add-on Hub: 1-click catalog install and wireless PC/phone APK sideloading.
 */
public final class WebRemoteServer {

    public static final int DEFAULT_PORT = 8080;
    public static final int FALLBACK_PORT = 8888;

    private static final List<ServerSocket> serverSockets = new CopyOnWriteArrayList<>();
    private static ServerSocket serverSocket;
    private static int activePort = DEFAULT_PORT;
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static ExecutorService serverExecutor;
    private static WeakReference<Activity> currentActivityRef;
    private static Handler mainHandler;

    private static void logDebug(String msg) {
        try {
            android.util.Log.d("WebRemote", msg);
        } catch (Throwable ignored) {}
    }

    private static void logError(String msg, Throwable t) {
        try {
            android.util.Log.e("WebRemote", msg, t);
        } catch (Throwable ignored) {}
    }

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

    private static List<ServerSocket> bindSockets(int port) {
        List<ServerSocket> list = new ArrayList<>();

        // Priority 1: Attempt dual-stack IPv6 bind (::) FIRST so dual-stack kernels accept IPv6 & IPv4 on one socket
        ServerSocket v6Socket = null;
        try {
            v6Socket = new ServerSocket();
            v6Socket.setReuseAddress(true);
            v6Socket.bind(new InetSocketAddress(InetAddress.getByName("::"), port), 50);
            list.add(v6Socket);
        } catch (Throwable ignored) {
            if (v6Socket != null && !v6Socket.isClosed()) {
                try { v6Socket.close(); } catch (Throwable ignored2) {}
            }
        }

        // Priority 2: Bind IPv4 0.0.0.0 (succeeds if IPv6 is separate-stack or if IPv6 bind failed)
        ServerSocket v4Socket = null;
        try {
            v4Socket = new ServerSocket();
            v4Socket.setReuseAddress(true);
            v4Socket.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 50);
            list.add(v4Socket);
        } catch (Throwable t1) {
            if (v4Socket != null && !v4Socket.isClosed()) {
                try { v4Socket.close(); } catch (Throwable ignored) {}
            }
            if (list.isEmpty()) {
                // Fallback: Wildcard bind if neither explicit :: nor 0.0.0.0 succeeded
                try {
                    v4Socket = new ServerSocket();
                    v4Socket.setReuseAddress(true);
                    v4Socket.bind(new InetSocketAddress(port), 50);
                    list.add(v4Socket);
                } catch (Throwable t2) {
                    if (v4Socket != null && !v4Socket.isClosed()) {
                        try { v4Socket.close(); } catch (Throwable ignored) {}
                    }
                }
            }
        }

        return list;
    }

    public static synchronized void start(Activity activity) {
        if (activity != null) {
            currentActivityRef = new WeakReference<>(activity);
        }
        if (isRunning()) {
            return;
        }

        stopSockets();

        int targetPort = DEFAULT_PORT;
        List<ServerSocket> bound = bindSockets(targetPort);
        if (bound.isEmpty()) {
            targetPort = FALLBACK_PORT;
            bound = bindSockets(targetPort);
        }

        if (bound.isEmpty()) {
            isRunning.set(false);
            return;
        }

        activePort = targetPort;
        serverSockets.clear();
        serverSockets.addAll(bound);
        serverSocket = bound.get(0);
        isRunning.set(true);

        if (serverExecutor == null || serverExecutor.isShutdown()) {
            serverExecutor = Executors.newCachedThreadPool();
        }

        for (ServerSocket ss : bound) {
            serverExecutor.execute(() -> {
                while (isRunning.get() && !ss.isClosed()) {
                    try {
                        Socket client = ss.accept();
                        try {
                            client.setSoTimeout(5000);
                            client.setTcpNoDelay(true);
                        } catch (Throwable ignored) {}
                        try {
                            if (serverExecutor != null && !serverExecutor.isShutdown()) {
                                serverExecutor.execute(() -> handleClient(client));
                            } else {
                                try { client.close(); } catch (Throwable ignored) {}
                            }
                        } catch (Throwable poolRejected) {
                            try { client.close(); } catch (Throwable ignored) {}
                        }
                    } catch (Throwable t) {
                        break;
                    }
                }
            });
        }
    }

    public static synchronized void stop() {
        isRunning.set(false);
        stopSockets();
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            serverExecutor = null;
        }
    }

    private static void stopSockets() {
        for (ServerSocket ss : serverSockets) {
            try {
                ss.close();
            } catch (Throwable ignored) {}
        }
        serverSockets.clear();
        serverSocket = null;
    }

    public static boolean isRunning() {
        if (!isRunning.get() || serverSockets.isEmpty()) {
            return false;
        }
        for (ServerSocket ss : serverSockets) {
            if (ss != null && !ss.isClosed()) {
                return true;
            }
        }
        return false;
    }

    public static int getActivePort() {
        return activePort;
    }

    public static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("sdk_google");
    }

    public static String getSystemProperty(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method method = clazz.getMethod("get", String.class);
            return (String) method.invoke(null, key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String getLocalIpAddress(Context context) {
        try {
            String overrideIp = getSystemProperty("debug.tvhome.host_ip");
            if (overrideIp != null && !overrideIp.trim().isEmpty()) {
                return overrideIp.trim();
            }

            // Priority 1: Check active network routing via ConnectivityManager
            if (context != null) {
                try {
                    android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                            context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        List<android.net.Network> candidateNets = new ArrayList<>();
                        android.net.Network activeNet = cm.getActiveNetwork();
                        if (activeNet != null) {
                            candidateNets.add(activeNet);
                        }
                        android.net.Network[] allNets = cm.getAllNetworks();
                        if (allNets != null) {
                            for (android.net.Network n : allNets) {
                                if (n != null && !candidateNets.contains(n)) {
                                    candidateNets.add(n);
                                }
                            }
                        }

                        for (android.net.Network net : candidateNets) {
                            android.net.LinkProperties lp = cm.getLinkProperties(net);
                            if (lp != null) {
                                String ifaceName = lp.getInterfaceName() != null ? lp.getInterfaceName().toLowerCase(Locale.US) : "";
                                // Avoid tunnel, VPN, dummy, or virtual interfaces
                                boolean isVirtual = ifaceName.contains("tun") || ifaceName.contains("tap")
                                        || ifaceName.contains("vpn") || ifaceName.contains("dummy")
                                        || ifaceName.contains("p2p") || ifaceName.contains("rmnet")
                                        || ifaceName.contains("gre") || ifaceName.contains("ip6tnl");
                                if (!isVirtual) {
                                    for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                                        InetAddress addr = la.getAddress();
                                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isAnyLocalAddress()) {
                                            String host = addr.getHostAddress();
                                            if (host != null && !host.startsWith("127.") && !host.startsWith("169.254.") && !"0.0.0.0".equals(host)) {
                                                // Avoid returning emulator NAT IP if physical interface is available
                                                if (!"10.0.2.15".equals(host)) {
                                                    return host;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // Priority 2: Iterate physical network interfaces
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                String bestIp = null;
                int bestScore = -1;

                while (interfaces.hasMoreElements()) {
                    NetworkInterface intf = interfaces.nextElement();
                    try {
                        if (intf.isLoopback()) {
                            continue;
                        }
                    } catch (Throwable ignored) {}

                    boolean isUp = true;
                    try {
                        isUp = intf.isUp();
                        if (!isUp) {
                            // Interface is definitively down
                            continue;
                        }
                    } catch (Throwable ignored) {
                        // SIOCGIFFLAGS permission denied on Android TV SELinux - do NOT discard interface
                        isUp = false;
                    }

                    String name = intf.getName() != null ? intf.getName().toLowerCase(Locale.US) : "";
                    String displayName = intf.getDisplayName() != null ? intf.getDisplayName().toLowerCase(Locale.US) : "";
                    String combined = name + " " + displayName;

                    // Filter out loopback, virtual, container, and tunnel interfaces
                    if (combined.contains("dummy") || combined.contains("sit") || combined.contains("tun")
                            || combined.contains("tap") || combined.contains("p2p") || combined.contains("rmnet")
                            || combined.contains("virbr") || combined.contains("vbox") || combined.contains("docker")
                            || combined.contains("vethernet") || combined.contains("hyper-v") || combined.contains("wsl")
                            || combined.contains("vmware") || combined.contains("gre") || combined.contains("ip6tnl")
                            || name.startsWith("lo")) {
                        continue;
                    }

                    int ifaceScore = 10;
                    if (combined.contains("wlan") || combined.contains("wi-fi") || combined.contains("wifi") || combined.contains("wireless") || name.startsWith("wl")) {
                        ifaceScore = 100; // Wi-Fi preferred
                    } else if (combined.contains("eth") || combined.contains("ethernet") || combined.contains("gigabit") || name.startsWith("en")) {
                        ifaceScore = 110; // Physical Ethernet preferred
                    } else if (combined.contains("usb")) {
                        ifaceScore = 95;  // USB Ethernet adapter
                    }

                    if (isUp) {
                        ifaceScore += 20;
                    }

                    Enumeration<InetAddress> addresses = intf.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || !(addr instanceof Inet4Address)) {
                            continue;
                        }

                        String host = addr.getHostAddress();
                        if (host == null || host.isEmpty() || host.startsWith("127.") || host.startsWith("169.254.") || "0.0.0.0".equals(host)) {
                            continue;
                        }

                        int totalScore = ifaceScore;
                        if (addr.isSiteLocalAddress()) {
                            totalScore += 30;
                        }
                        if (host.startsWith("192.168.")) {
                            totalScore += 10; // Common LAN subnet
                        } else if ("10.0.2.15".equals(host)) {
                            totalScore -= 40; // Deprioritize emulator NAT IP if physical IP is present
                        }

                        if (totalScore > bestScore) {
                            bestScore = totalScore;
                            bestIp = host;
                        }
                    }
                }

                if (bestIp != null) {
                    return bestIp;
                }
            }

            // Priority 3: WifiManager fallback
            if (context != null) {
                try {
                    WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null && wm.getConnectionInfo() != null) {
                        int ipInt = wm.getConnectionInfo().getIpAddress();
                        if (ipInt != 0) {
                            String wifiIp = String.format(Locale.US, "%d.%d.%d.%d",
                                    (ipInt & 0xff),
                                    (ipInt >> 8 & 0xff),
                                    (ipInt >> 16 & 0xff),
                                    (ipInt >> 24 & 0xff));
                            if (!wifiIp.startsWith("127.") && !wifiIp.startsWith("169.254.")) {
                                return wifiIp;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return "192.168.1.100";
    }

    public static String getServerUrl(Context context) {
        return "http://" + getLocalIpAddress(context) + ":" + activePort;
    }

    private static String readHeaderLine(InputStream in) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') baos.write(b);
            if (baos.size() > 8192) {
                while ((b = in.read()) != -1 && b != '\n') {}
                break;
            }
        }
        if (b == -1 && baos.size() == 0) return null;
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static void drainBody(InputStream in, int contentLength) {
        if (contentLength <= 0 || contentLength > 10 * 1024 * 1024) return;
        try {
            long remaining = contentLength;
            byte[] discard = new byte[1024];
            while (remaining > 0) {
                int read = in.read(discard, 0, (int) Math.min(discard.length, remaining));
                if (read == -1) break;
                remaining -= read;
            }
        } catch (Throwable ignored) {}
    }

    private static void sendCorsPreflight(OutputStream out) throws Exception {
        String resp = "HTTP/1.1 200 OK\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n";
        out.write(resp.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static void sendError(OutputStream out, int code, String msg) {
        try {
            String resp = "HTTP/1.1 " + code + " " + msg + "\r\n" +
                    "Content-Type: text/plain; charset=utf-8\r\n" +
                    "Content-Length: " + msg.length() + "\r\n" +
                    "Connection: close\r\n\r\n" +
                    msg;
            out.write(resp.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (Throwable ignored) {}
    }

    private static void handleClient(Socket socket) {
        try (socket;
             InputStream in = new BufferedInputStream(socket.getInputStream(), 8192);
             OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 8192)) {

            String requestLine;
            int emptyLineCount = 0;
            do {
                requestLine = readHeaderLine(in);
                if (requestLine == null) return;
                emptyLineCount++;
                if (emptyLineCount > 16) return;
            } while (requestLine.trim().isEmpty());

            String[] parts = requestLine.trim().split("\\s+");
            if (parts.length < 2) {
                sendError(out, 400, "Bad Request");
                return;
            }
            String method = parts[0].toUpperCase(Locale.US);
            String uri = parts[1];
            boolean isHead = "HEAD".equals(method);

            // Handle absolute-URI from HTTP proxies (e.g. GET http://192.168.1.50:8080/api/status HTTP/1.1)
            if (uri.regionMatches(true, 0, "http://", 0, 7) || uri.regionMatches(true, 0, "https://", 0, 8)) {
                int slashIdx = uri.indexOf('/', uri.indexOf("//") + 2);
                if (slashIdx != -1) {
                    uri = uri.substring(slashIdx);
                } else {
                    uri = "/";
                }
            }
            logDebug("--> " + method + " " + uri);

            // Parse headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = readHeaderLine(in)) != null && !headerLine.isEmpty()) {
                if (headers.size() < 100) {
                    int colon = headerLine.indexOf(':');
                    if (colon > 0) {
                        headers.put(headerLine.substring(0, colon).trim().toLowerCase(Locale.US),
                                    headerLine.substring(colon + 1).trim());
                    }
                }
            }

            int contentLength = 0;
            String lenHeader = headers.get("content-length");
            if (lenHeader != null) {
                try {
                    contentLength = Math.max(0, Integer.parseInt(lenHeader.trim()));
                } catch (Throwable ignored) {}
            }

            // Fast-path CORS preflight
            if ("OPTIONS".equals(method)) {
                drainBody(in, contentLength);
                sendCorsPreflight(out);
            } else

            if (uri.startsWith("/api/key")) {
                drainBody(in, contentLength);
                int code = extractQueryParamInt(uri, "code", -1);
                if (code > 0) {
                    dispatchKeyEvent(code);
                }
                sendJson(out, "{\"status\":\"ok\",\"action\":\"key\",\"code\":" + code + "}", isHead);
            } else if (uri.startsWith("/api/type")) {
                drainBody(in, contentLength);
                String text = extractQueryParam(uri, "text");
                if (text != null && !text.isEmpty()) {
                    dispatchTextInput(text);
                }
                sendJson(out, "{\"status\":\"ok\",\"action\":\"type\"}", isHead);
            } else if (uri.startsWith("/api/action")) {
                drainBody(in, contentLength);
                String cmd = extractQueryParam(uri, "cmd");
                executeAction(cmd);
                sendJson(out, "{\"status\":\"ok\",\"action\":\"" + (cmd != null ? cmd : "") + "\"}", isHead);
            } else if (uri.startsWith("/api/status")) {
                drainBody(in, contentLength);
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                boolean gameMode = act != null && GameModeManager.isGameModeEnabled(act);
                int audioMode = act != null ? AudioOutputManager.getCurrentMode(act) : 0;
                String audioName = AudioOutputManager.getModeName(audioMode);
                String sleepTimer = SleepTimerManager.getFormattedCountdown();
                String profile = act != null ? ProfileManager.getActiveProfile(act) : "personal";
                String profileName = ProfileManager.getProfileDisplayName(profile);
                String profileIcon = ProfileManager.getProfileIcon(profile);
                String kidsTime = act != null ? ProfileManager.getFormattedKidsRemainingTime(act) : "";

                String json = String.format(Locale.US,
                        "{\"gameMode\":%b,\"audioMode\":\"%s\",\"sleepTimer\":\"%s\",\"profile\":\"%s\",\"profileName\":\"%s\",\"profileIcon\":\"%s\",\"kidsTime\":\"%s\"}",
                        gameMode, audioName, sleepTimer, profile, escapeJson(profileName), escapeJson(profileIcon), kidsTime);
                sendJson(out, json, isHead);
            } else if (uri.startsWith("/api/profile")) {
                drainBody(in, contentLength);
                String target = extractQueryParam(uri, "name");
                String pin = extractQueryParam(uri, "pin");
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                boolean success = false;
                boolean isValidTarget = target != null && ProfileManager.isValidProfile(target);

                if (act != null && isValidTarget) {
                    String current = ProfileManager.getActiveProfile(act);
                    if (ProfileManager.requiresAuthenticationForSwitch(current, target)) {
                        // Leaving Kids Mode or leaving Guest Mode for Personal requires parent PIN
                        if (ProfileManager.verifyPin(act, pin)) {
                            ProfileManager.switchProfile(act, target);
                            success = true;
                        }
                    } else if (ProfileManager.PROFILE_KIDS.equals(target) && !ProfileManager.isPinConfigured(act)) {
                        // Kids Mode cannot be activated without PIN configured
                        success = false;
                    } else {
                        ProfileManager.switchProfile(act, target);
                        success = true;
                    }
                }
                sendJson(out, "{\"status\":\"" + (success ? "ok" : "error") + "\",\"profile\":\"" + (target != null && isValidTarget ? target : "") + "\"}", isHead);
            } else if (uri.startsWith("/api/dock")) {
                drainBody(in, contentLength);
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
                sendJson(out, sb.toString(), isHead);
            } else if (uri.startsWith("/api/apps")) {
                drainBody(in, contentLength);
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
                sendJson(out, sb.toString(), isHead);
            } else if (uri.startsWith("/api/reorder")) {
                drainBody(in, contentLength);
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                if (act != null && !ProfileManager.isPersonalMode(act)) {
                    sendJson(out, "{\"status\":\"forbidden\",\"error\":\"Reordering disabled in non-personal profile\"}", isHead);
                    return;
                }
                int from = extractQueryParamInt(uri, "from", -1);
                int to = extractQueryParamInt(uri, "to", -1);
                boolean success = false;
                if (from >= 0 && to >= 0 && from != to) {
                    Handler h = getMainHandler();
                    if (h != null) {
                        h.post(() -> {
                            if (act instanceof MainActivity) {
                                ((MainActivity) act).reorderDockSlot(from, to);
                            }
                        });
                        success = true;
                    }
                }
                sendJson(out, "{\"status\":\"" + (success ? "ok" : "error") + "\",\"from\":" + from + ",\"to\":" + to + "}", isHead);
            } else if (uri.startsWith("/api/launch")) {
                drainBody(in, contentLength);
                String pkg = extractQueryParam(uri, "pkg");
                boolean success = false;
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                if (act != null && pkg != null && !pkg.isEmpty()) {
                    if (ProfileManager.isBedtimeLimitReached(act)) {
                        sendJson(out, "{\"status\":\"bedtime_limit_reached\",\"pkg\":\"" + escapeJson(pkg) + "\"}", isHead);
                        return;
                    }
                    if (!ProfileManager.isAppAllowed(act, pkg)) {
                        sendJson(out, "{\"status\":\"blocked\",\"pkg\":\"" + escapeJson(pkg) + "\"}", isHead);
                        return;
                    }
                    Handler h = getMainHandler();
                    if (h != null) {
                        h.post(() -> {
                            Activity a = currentActivityRef != null ? currentActivityRef.get() : null;
                            if (a instanceof MainActivity) {
                                ((MainActivity) a).launchAppByPackage(pkg);
                            }
                        });
                        success = true;
                    }
                }
                sendJson(out, "{\"status\":\"" + (success ? "ok" : "error") + "\",\"pkg\":\"" + (pkg != null ? pkg : "") + "\"}", isHead);
            } else if (uri.startsWith("/api/addons/install")) {
                drainBody(in, contentLength);
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                if (act != null && !ProfileManager.isPersonalMode(act)) {
                    sendJson(out, "{\"status\":\"forbidden\",\"error\":\"Addon installation disabled in non-personal profile\"}", isHead);
                    return;
                }
                String id = extractQueryParam(uri, "id");
                AddonCatalog.AddonItem item = AddonCatalog.findById(id);
                boolean started = false;
                if (item != null) {
                    if (act != null) {
                        AddonCatalog.downloadAndInstall(act, item, null);
                        started = true;
                    }
                }
                sendJson(out, "{\"status\":\"" + (started ? "ok" : "error") + "\",\"id\":\"" + (id != null ? id : "") + "\"}", isHead);
            } else if (uri.startsWith("/api/smarthome")) {
                drainBody(in, contentLength);
                String action = extractQueryParam(uri, "action");
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                if (action != null && act != null) {
                    SmartHomeManager.executeSmartAction(act, action);
                }
                boolean lights = act != null && SmartHomeManager.getLastLightState(act);
                String cam = act != null ? SmartHomeManager.getCameraUrl(act) : "";
                sendJson(out, "{\"status\":\"ok\",\"action\":\"" + (action != null ? action : "") + "\",\"lights\":" + lights + ",\"camera\":\"" + escapeJson(cam) + "\"}", isHead);
            } else if (uri.startsWith("/api/addons")) {
                drainBody(in, contentLength);
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                List<AddonCatalog.AddonItem> list = AddonCatalog.getCatalog();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    AddonCatalog.AddonItem item = list.get(i);
                    boolean installed = act != null && AddonCatalog.isInstalled(act, item.pkg);
                    sb.append("{\"id\":\"").append(escapeJson(item.id))
                      .append("\",\"name\":\"").append(escapeJson(item.name))
                      .append("\",\"category\":\"").append(escapeJson(item.category))
                      .append("\",\"description\":\"").append(escapeJson(item.description))
                      .append("\",\"pkg\":\"").append(escapeJson(item.pkg))
                      .append("\",\"installed\":").append(installed)
                      .append("}");
                }
                sb.append("]");
                sendJson(out, sb.toString(), isHead);
            } else if (uri.startsWith("/api/debloat/scan")) {
                drainBody(in, contentLength);
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                DebloaterEngine.DebloaterPreview preview = DebloaterEngine.getDebloaterPreview(act);
                List<DebloaterEngine.DebloatItem> items = DebloaterEngine.scanDebloatCandidates(act);

                StringBuilder sb = new StringBuilder("{");
                sb.append("\"brand\":\"").append(escapeJson(preview.brand.name())).append("\",");
                sb.append("\"brandName\":\"").append(escapeJson(preview.brand.displayName)).append("\",");
                sb.append("\"device\":\"").append(escapeJson(preview.deviceInfo)).append("\",");
                sb.append("\"totalFound\":").append(preview.totalFound).append(",");
                sb.append("\"activeBloatCount\":").append(preview.activeBloatCount).append(",");
                sb.append("\"alreadyDisabledCount\":").append(preview.alreadyDisabledCount).append(",");
                sb.append("\"estimatedRamFreedMb\":").append(preview.estimatedRamFreedMb).append(",");
                sb.append("\"cpuWakeupsSavedPerHour\":").append(preview.cpuWakeupsSavedPerHour).append(",");
                sb.append("\"items\":[");
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) sb.append(",");
                    DebloaterEngine.DebloatItem it = items.get(i);
                    sb.append("{\"pkg\":\"").append(escapeJson(it.pkg))
                      .append("\",\"label\":\"").append(escapeJson(it.label))
                      .append("\",\"category\":\"").append(escapeJson(it.category))
                      .append("\",\"description\":\"").append(escapeJson(it.description))
                      .append("\",\"risk\":\"").append(escapeJson(it.risk.displayName))
                      .append("\",\"estimatedRamMb\":").append(it.estimatedRamMb)
                      .append(",\"installed\":").append(it.isInstalled)
                      .append(",\"enabled\":").append(it.isEnabled)
                      .append("}");
                }
                sb.append("]}");
                sendJson(out, sb.toString(), isHead);
            } else if (uri.startsWith("/api/debloat/apply")) {
                drainBody(in, contentLength);
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                if (act != null && !ProfileManager.isPersonalMode(act)) {
                    sendJson(out, "{\"status\":\"forbidden\",\"error\":\"Debloater disabled in non-personal profile\"}", isHead);
                    return;
                }
                String pkg = extractQueryParam(uri, "pkg");
                int count = 0;
                boolean success = false;
                if (act != null) {
                    if (pkg != null && !pkg.isEmpty()) {
                        success = DebloaterEngine.disablePackage(act, pkg);
                        count = success ? 1 : 0;
                    } else {
                        count = DebloaterEngine.autoDebloatAll(act);
                        success = count > 0;
                    }
                }
                sendJson(out, "{\"status\":\"" + (success ? "ok" : "error") + "\",\"count\":" + count + "}", isHead);
            } else if (uri.startsWith("/api/debloat/restore")) {
                drainBody(in, contentLength);
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                if (act != null && !ProfileManager.isPersonalMode(act)) {
                    sendJson(out, "{\"status\":\"forbidden\",\"error\":\"Debloater disabled in non-personal profile\"}", isHead);
                    return;
                }
                String pkg = extractQueryParam(uri, "pkg");
                int count = 0;
                boolean success = false;
                if (act != null) {
                    if (pkg != null && !pkg.isEmpty()) {
                        success = DebloaterEngine.enablePackage(act, pkg);
                        count = success ? 1 : 0;
                    } else {
                        count = DebloaterEngine.restoreAll(act);
                        success = count > 0;
                    }
                }
                sendJson(out, "{\"status\":\"" + (success ? "ok" : "error") + "\",\"count\":" + count + "}", isHead);
            } else if (uri.startsWith("/api/canvas/stroke")) {
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                boolean ok = false;
                if (contentLength > 0 && contentLength < 1000000) {
                    byte[] buf = new byte[contentLength];
                    int total = 0;
                    while (total < contentLength) {
                        int read = in.read(buf, total, contentLength - total);
                        if (read == -1) break;
                        total += read;
                    }
                    if (total == contentLength) {
                        String body = new String(buf, 0, total, StandardCharsets.UTF_8);
                        FamilyNoteManager.Stroke stroke = FamilyNoteManager.parseSingleStrokeJson(body);
                        if (stroke != null) {
                            FamilyNoteManager.addStroke(act, stroke);
                            ok = true;
                        }
                    } else {
                        drainBody(in, contentLength - total);
                    }
                } else if (contentLength > 0) {
                    drainBody(in, contentLength);
                }
                sendJson(out, "{\"status\":\"" + (ok ? "ok" : "error") + "\"}", isHead);
            } else if (uri.startsWith("/api/canvas/clear")) {
                drainBody(in, contentLength);
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                FamilyNoteManager.clear(act);
                sendJson(out, "{\"status\":\"ok\"}", isHead);
            } else if (uri.startsWith("/api/canvas/state")) {
                drainBody(in, contentLength);
                List<FamilyNoteManager.Stroke> strokes = FamilyNoteManager.getStrokes();
                String json = FamilyNoteManager.serializeToJson(strokes);
                sendJson(out, json, isHead);
            } else if (uri.startsWith("/api/upload")) {
                // Sideload APK directly from PC / Mobile browser over Wi-Fi
                String fileName = extractQueryParam(uri, "name");
                if (fileName != null) {
                    fileName = new File(fileName).getName(); // Prevent path traversal
                }
                if (fileName == null || fileName.isEmpty() || !fileName.endsWith(".apk")) {
                    fileName = "sideload_" + System.currentTimeMillis() + ".apk";
                }
                Activity act = currentActivityRef != null ? currentActivityRef.get() : null;
                if (act != null && contentLength > 0) {
                    File targetDir = act.getExternalCacheDir() != null ? act.getExternalCacheDir() : act.getCacheDir();
                    File dest = new File(targetDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        long totalRead = 0;
                        int n;
                        while (totalRead < contentLength && (n = in.read(buf, 0, (int) Math.min(buf.length, contentLength - totalRead))) != -1) {
                            fos.write(buf, 0, n);
                            totalRead += n;
                        }
                        fos.flush();
                    }
                    // Trigger TV installation on UI thread
                    Handler h = getMainHandler();
                    if (h != null) {
                        h.post(() -> AddonCatalog.installApkFile(act, dest));
                    }
                    sendJson(out, "{\"status\":\"ok\",\"file\":\"" + escapeJson(fileName) + "\"}", isHead);
                } else {
                    drainBody(in, contentLength);
                    sendJson(out, "{\"status\":\"error\",\"message\":\"Missing file content or TV inactive\"}", isHead);
                }
            } else {
                drainBody(in, contentLength);
                // Serve HTML5 Remote Web App
                logDebug("Serving HTML...");
                sendHtml(out, buildRemoteHtml(), isHead);
                logDebug("HTML sent successfully!");
            }

            try {
                socket.shutdownOutput();
            } catch (Throwable ignored) {}

            try {
                socket.setSoTimeout(50);
                byte[] discard = new byte[512];
                while (in.read(discard) != -1) {}
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            logError("Error in handleClient", t);
        }
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
        } catch (Throwable t) {
            logError("Error in dispatchKeyEvent", t);
        }
    }

    private static void dispatchTextInput(String rawText) {
        try {
            String sanitized = rawText.replace(" ", "%s");
            Runtime.getRuntime().exec(new String[]{"input", "text", sanitized});
        } catch (Throwable t) {
            logError("Error in dispatchTextInput", t);
        }
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
                        case "addon_hub":
                            main.showAddonHub();
                            break;
                        case "adblock_cycle":
                            AdBlockManager.cycleNext(main);
                            break;
                        case "smarthome":
                            main.showSmartHome();
                            break;
                        case "aerial":
                            main.showAerialScreensaver();
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
                        case "webremote_dialog":
                            WebRemoteDialog.show(main);
                            break;
                        case "profile_picker":
                            main.showProfilePicker();
                            break;
                        case "debloater_dialog":
                            main.showDebloaterDialog();
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
            int hashIdx = query.indexOf('#');
            if (hashIdx != -1) {
                query = query.substring(0, hashIdx);
            }
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq != -1) {
                    String k = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                    if (k.equals(param)) {
                        return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                    }
                } else {
                    String k = URLDecoder.decode(pair, "UTF-8");
                    if (k.equals(param)) {
                        return "";
                    }
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
        sendJson(out, json, false);
    }

    private static void sendJson(OutputStream out, String json, boolean isHead) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        if (!isHead) {
            out.write(bytes);
        }
        out.flush();
    }

    private static void sendHtml(OutputStream out, String html) throws Exception {
        sendHtml(out, html, false);
    }

    private static void sendHtml(OutputStream out, String html, boolean isHead) throws Exception {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        if (!isHead) {
            out.write(bytes);
        }
        out.flush();
    }

    public static String buildRemoteHtml() {
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
                "    .header { width: 100%; max-width: 400px; display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; padding: 0 4px; }\n" +
                "    .title { font-size: 19px; font-weight: 700; letter-spacing: -0.3px; display: flex; align-items: center; gap: 8px; }\n" +
                "    .dot { width: 8px; height: 8px; border-radius: 50%; background: #10b981; box-shadow: 0 0 10px #10b981; }\n" +
                "    .subtitle { font-size: 11px; color: #64748b; font-weight: 500; }\n" +
                "    .tabs { display: flex; width: 100%; max-width: 400px; background: rgba(30, 41, 59, 0.6); border-radius: 14px; padding: 4px; margin-bottom: 14px; gap: 3px; border: 1px solid rgba(255,255,255,0.06); overflow-x: auto; scrollbar-width: none; }\n" +
                "    .tabs::-webkit-scrollbar { display: none; }\n" +
                "    .tab-btn { flex: 1 0 auto; background: transparent; border: none; color: #94a3b8; padding: 9px 8px; font-size: 11px; font-weight: 600; border-radius: 10px; cursor: pointer; transition: all 0.15s ease; display: flex; align-items: center; justify-content: center; gap: 4px; white-space: nowrap; }\n" +
                "    .note-canvas-wrapper { width: 100%; position: relative; margin-bottom: 14px; }\n" +
                "    .canvas-box { width: 100%; height: 260px; background: #0b1120; border-radius: 18px; border: 1px solid rgba(255,255,255,0.12); box-shadow: inset 0 2px 10px rgba(0,0,0,0.5); touch-action: none; display: block; cursor: crosshair; }\n" +
                "    .palette-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; padding: 0 4px; }\n" +
                "    .color-swatch { width: 32px; height: 32px; border-radius: 50%; border: 2px solid transparent; cursor: pointer; transition: transform 0.15s, border-color 0.15s; outline: none; }\n" +
                "    .color-swatch:active { transform: scale(0.9); }\n" +
                "    .color-swatch.selected { border-color: #fff; transform: scale(1.15); box-shadow: 0 0 10px rgba(255,255,255,0.4); }\n" +
                "    .note-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; }\n" +
                "    .width-picker { display: flex; gap: 6px; background: rgba(15, 23, 42, 0.8); padding: 4px; border-radius: 10px; border: 1px solid rgba(255,255,255,0.08); }\n" +
                "    .width-btn { width: 28px; height: 28px; border-radius: 6px; border: none; background: transparent; color: #94a3b8; display: flex; align-items: center; justify-content: center; cursor: pointer; }\n" +
                "    .width-btn.active { background: rgba(56, 189, 248, 0.2); color: #38bdf8; }\n" +
                "    .tab-btn.active { background: #38bdf8; color: #0b0f19; font-weight: 700; box-shadow: 0 2px 10px rgba(56, 189, 248, 0.35); }\n" +
                "    .tab-content { width: 100%; max-width: 400px; }\n" +
                "    .card { background: rgba(30, 41, 59, 0.7); backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.08); border-radius: 20px; padding: 14px; margin-bottom: 14px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); }\n" +
                "    .input-box { display: flex; gap: 8px; }\n" +
                "    input[type=text] { flex: 1; background: rgba(15, 23, 42, 0.8); border: 1px solid rgba(255,255,255,0.12); border-radius: 12px; color: #fff; padding: 12px 14px; font-size: 14px; outline: none; user-select: text; }\n" +
                "    input[type=text]:focus { border-color: #38bdf8; }\n" +
                "    .btn-send { background: #38bdf8; color: #0f172a; border: none; border-radius: 12px; font-weight: 700; padding: 0 16px; cursor: pointer; }\n" +
                "    .dpad-container { width: 240px; height: 240px; position: relative; margin: 10px auto 14px; background: rgba(15, 23, 42, 0.9); border-radius: 50%; border: 1px solid rgba(255,255,255,0.1); box-shadow: inset 0 4px 20px rgba(0,0,0,0.6); }\n" +
                "    .dpad-btn { position: absolute; background: transparent; border: none; color: #e2e8f0; font-size: 20px; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: transform 0.08s, background 0.1s; }\n" +
                "    .dpad-btn:active { background: rgba(255,255,255,0.08); transform: scale(0.94); }\n" +
                "    .btn-up { top: 6px; left: 65px; width: 110px; height: 65px; border-radius: 55px 55px 0 0; }\n" +
                "    .btn-down { bottom: 6px; left: 65px; width: 110px; height: 65px; border-radius: 0 0 55px 55px; }\n" +
                "    .btn-left { top: 65px; left: 6px; width: 65px; height: 110px; border-radius: 55px 0 0 55px; }\n" +
                "    .btn-right { top: 65px; right: 6px; width: 65px; height: 110px; border-radius: 0 55px 55px 0; }\n" +
                "    .btn-center { top: 72px; left: 72px; width: 96px; height: 96px; border-radius: 50%; background: #1e293b; border: 1px solid rgba(255,255,255,0.15); font-weight: 700; font-size: 15px; color: #38bdf8; box-shadow: 0 4px 15px rgba(0,0,0,0.5); }\n" +
                "    .btn-center:active { transform: scale(0.92); background: #334155; }\n" +
                "    .grid-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; width: 100%; margin-bottom: 12px; }\n" +
                "    .btn-ctrl { background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; height: 50px; color: #fff; font-size: 12px; font-weight: 600; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; cursor: pointer; transition: transform 0.08s, background 0.1s; }\n" +
                "    .btn-ctrl:active { transform: scale(0.95); background: rgba(255,255,255,0.12); }\n" +
                "    .icon { font-size: 15px; }\n" +
                "    .search-box { width: 100%; margin-bottom: 12px; }\n" +
                "    .app-card { display: flex; align-items: center; background: rgba(30, 41, 59, 0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; padding: 10px 12px; margin-bottom: 8px; cursor: pointer; transition: all 0.15s ease; }\n" +
                "    .app-card:active { background: rgba(56, 189, 248, 0.15); border-color: rgba(56, 189, 248, 0.4); transform: scale(0.98); }\n" +
                "    .app-icon { width: 36px; height: 36px; border-radius: 10px; background: linear-gradient(135deg, #38bdf8, #818cf8); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 15px; color: #0f172a; margin-right: 12px; flex-shrink: 0; }\n" +
                "    .app-details { flex: 1; min-width: 0; }\n" +
                "    .app-title { font-size: 13px; font-weight: 600; color: #f1f5f9; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
                "    .app-pkg { font-size: 11px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
                "    .btn-launch { background: rgba(56, 189, 248, 0.2); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.4); border-radius: 8px; padding: 5px 12px; font-size: 11px; font-weight: 600; flex-shrink: 0; }\n" +
                "    .dock-hint { font-size: 12px; color: #94a3b8; text-align: center; margin-bottom: 12px; line-height: 1.4; }\n" +
                "    .dock-item { display: flex; align-items: center; background: rgba(30, 41, 59, 0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; padding: 10px 12px; margin-bottom: 8px; transition: transform 0.15s, background 0.15s; }\n" +
                "    .dock-item.dragging { opacity: 0.4; border-style: dashed; }\n" +
                "    .dock-item.over { border-color: #38bdf8; background: rgba(56, 189, 248, 0.1); }\n" +
                "    .drag-handle { font-size: 18px; color: #64748b; padding: 0 10px 0 2px; cursor: grab; }\n" +
                "    .pos-badge { background: #0f172a; color: #38bdf8; font-size: 11px; font-weight: 700; border-radius: 6px; padding: 3px 6px; margin-right: 10px; border: 1px solid rgba(56,189,248,0.2); }\n" +
                "    .dock-details { flex: 1; min-width: 0; }\n" +
                "    .dock-title { font-size: 13px; font-weight: 600; color: #f1f5f9; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
                "    .dock-pkg { font-size: 11px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
                "    .dock-actions { display: flex; gap: 6px; flex-shrink: 0; }\n" +
                "    .btn-arrow { width: 32px; height: 32px; border-radius: 8px; background: rgba(15, 23, 42, 0.8); border: 1px solid rgba(255,255,255,0.1); color: #cbd5e1; font-size: 13px; display: flex; align-items: center; justify-content: center; cursor: pointer; }\n" +
                "    .btn-arrow:active:not(:disabled) { background: #38bdf8; color: #0b0f19; }\n" +
                "    .btn-arrow:disabled { opacity: 0.25; cursor: default; }\n" +
                "    .addon-card { background: rgba(30, 41, 59, 0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; padding: 12px; margin-bottom: 10px; }\n" +
                "    .addon-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }\n" +
                "    .addon-tag { font-size: 10px; font-weight: 700; letter-spacing: 0.05em; color: #38bdf8; background: rgba(56,189,248,0.15); padding: 2px 8px; border-radius: 6px; }\n" +
                "    .addon-title { font-size: 14px; font-weight: 700; color: #fff; margin-bottom: 4px; }\n" +
                "    .addon-desc { font-size: 12px; color: #94a3b8; line-height: 1.4; margin-bottom: 10px; }\n" +
                "    .addon-btn { border: none; border-radius: 8px; padding: 7px 14px; font-size: 12px; font-weight: 700; cursor: pointer; width: 100%; transition: opacity 0.15s; }\n" +
                "    .addon-btn:active { opacity: 0.8; }\n" +
                "    .btn-install { background: #38bdf8; color: #0b0f19; }\n" +
                "    .btn-open { background: #10b981; color: #0b0f19; }\n" +
                "    .sideload-box { background: rgba(15, 23, 42, 0.8); border: 1px dashed rgba(56, 189, 248, 0.4); border-radius: 14px; padding: 14px; text-align: center; margin-bottom: 14px; }\n" +
                "    .sideload-title { font-size: 13px; font-weight: 700; color: #38bdf8; margin-bottom: 4px; }\n" +
                "    .sideload-sub { font-size: 11px; color: #64748b; margin-bottom: 10px; }\n" +
                "    .empty-state { text-align: center; color: #64748b; font-size: 13px; padding: 24px; }\n" +
                "    .toast { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%) translateY(80px); background: #10b981; color: #042f2e; font-weight: 700; font-size: 13px; padding: 9px 18px; border-radius: 9999px; box-shadow: 0 8px 20px rgba(0,0,0,0.4); transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.25s; opacity: 0; pointer-events: none; z-index: 1000; }\n" +
                "    .toast.show { transform: translateX(-50%) translateY(0); opacity: 1; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"header\">\n" +
                "    <div>\n" +
                "      <div class=\"title\"><span class=\"dot\"></span>CleanTV Remote</div>\n" +
                "      <div class=\"subtitle\">Zero-Install Controller &amp; Add-on Hub</div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "  <div class=\"tabs\">\n" +
                "    <button class=\"tab-btn active\" id=\"tab-remote\" onclick=\"switchTab('remote')\">🎮 Remote</button>\n" +
                "    <button class=\"tab-btn\" id=\"tab-note\" onclick=\"switchTab('note')\">✏️ Note</button>\n" +
                "    <button class=\"tab-btn\" id=\"tab-apps\" onclick=\"switchTab('apps')\">📱 Apps</button>\n" +
                "    <button class=\"tab-btn\" id=\"tab-dock\" onclick=\"switchTab('dock')\">🗂️ Dock</button>\n" +
                "    <button class=\"tab-btn\" id=\"tab-addons\" onclick=\"switchTab('addons')\">📦 Add-ons</button>\n" +
                "    <button class=\"tab-btn\" id=\"tab-debloat\" onclick=\"switchTab('debloat')\">⚡ Debloat</button>\n" +
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
                "    <div class=\"grid-row\">\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendAction('adblock_cycle')\"><span class=\"icon\">&#128737;</span>AdBlock</button>\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendAction('smarthome')\"><span class=\"icon\">&#127968;</span>SmartHome</button>\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendAction('aerial')\"><span class=\"icon\">&#127757;</span>Aerial</button>\n" +
                "    </div>\n" +
                "    <div class=\"grid-row\">\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendAction('profile_picker')\"><span class=\"icon\">&#128101;</span>Profiles</button>\n" +
                "      <button class=\"btn-ctrl\" onclick=\"sendAction('debloater_dialog')\"><span class=\"icon\">⚡</span>Debloat</button>\n" +
                "      <button class=\"btn-ctrl\" onclick=\"cycleProfile()\"><span class=\"icon\">&#128118;</span>Kids Pin</button>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "  <div id=\"pane-note\" class=\"tab-content\" style=\"display:none;\">\n" +
                "    <div class=\"card\">\n" +
                "      <div class=\"note-canvas-wrapper\">\n" +
                "        <canvas id=\"noteCanvas\" class=\"canvas-box\"></canvas>\n" +
                "      </div>\n" +
                "      <div class=\"palette-row\">\n" +
                "        <button class=\"color-swatch selected\" style=\"background:#ffffff;\" onclick=\"setColor('#ffffff', this)\"></button>\n" +
                "        <button class=\"color-swatch\" style=\"background:#38bdf8;\" onclick=\"setColor('#38bdf8', this)\"></button>\n" +
                "        <button class=\"color-swatch\" style=\"background:#34d399;\" onclick=\"setColor('#34d399', this)\"></button>\n" +
                "        <button class=\"color-swatch\" style=\"background:#fbbf24;\" onclick=\"setColor('#fbbf24', this)\"></button>\n" +
                "        <button class=\"color-swatch\" style=\"background:#f87171;\" onclick=\"setColor('#f87171', this)\"></button>\n" +
                "        <button class=\"color-swatch\" style=\"background:#c084fc;\" onclick=\"setColor('#c084fc', this)\"></button>\n" +
                "      </div>\n" +
                "      <div class=\"note-toolbar\">\n" +
                "        <div class=\"width-picker\">\n" +
                "          <button class=\"width-btn\" onclick=\"setStrokeWidth(0.008, this)\">S</button>\n" +
                "          <button class=\"width-btn active\" onclick=\"setStrokeWidth(0.016, this)\">M</button>\n" +
                "          <button class=\"width-btn\" onclick=\"setStrokeWidth(0.032, this)\">L</button>\n" +
                "        </div>\n" +
                "        <button class=\"addon-btn\" style=\"background:rgba(239,68,68,0.2);color:#ef4444;width:auto;padding:6px 14px;\" onclick=\"clearNote()\">🗑️ Clear</button>\n" +
                "      </div>\n" +
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
                "  <div id=\"pane-addons\" class=\"tab-content\" style=\"display:none;\">\n" +
                "    <div class=\"sideload-box\">\n" +
                "      <div class=\"sideload-title\">Wireless APK Sideload (PC/Phone to TV)</div>\n" +
                "      <div class=\"sideload-sub\">Select an APK to install directly over Wi-Fi without USB drives:</div>\n" +
                "      <input type=\"file\" id=\"fileApk\" accept=\".apk\" style=\"margin-bottom:8px;font-size:12px;\">\n" +
                "      <button class=\"addon-btn btn-install\" onclick=\"uploadApk()\">Upload &amp; Install on TV</button>\n" +
                "    </div>\n" +
                "    <div style=\"font-size:12px;font-weight:700;color:#64748b;margin-bottom:8px;letter-spacing:0.05em;\">1-CLICK TV ADD-ON CATALOG</div>\n" +
                "    <div id=\"addonsList\"></div>\n" +
                "  </div>\n" +
                "  <div id=\"pane-debloat\" class=\"tab-content\" style=\"display:none;\">\n" +
                "    <div class=\"card\">\n" +
                "      <div style=\"font-size:14px;font-weight:700;color:#fff;margin-bottom:4px;display:flex;align-items:center;justify-content:space-between;\">" +
                "        <span>⚡ Auto-Debloater Engine</span>" +
                "        <span id=\"debloatBrandBadge\" style=\"font-size:10px;font-weight:700;background:rgba(239,68,68,0.2);color:#ef4444;padding:3px 8px;border-radius:6px;\">DETECTING</span>" +
                "      </div>" +
                "      <div id=\"debloatDevSummary\" style=\"font-size:11px;color:#94a3b8;margin-bottom:12px;\">Scanning OEM hardware...</div>" +
                "      <div style=\"display:flex;gap:8px;\">" +
                "        <button class=\"addon-btn btn-install\" style=\"flex:1;background:#ef4444;color:#fff;\" onclick=\"runAutoDebloat()\">⚡ 1-Click Debloat</button>" +
                "        <button class=\"addon-btn\" style=\"flex:1;background:rgba(255,255,255,0.1);color:#fff;\" onclick=\"runRestoreDebloat()\">↩ Restore All</button>" +
                "      </div>" +
                "    </div>\n" +
                "    <div style=\"font-size:12px;font-weight:700;color:#64748b;margin-bottom:8px;letter-spacing:0.05em;\">DETECTED OEM BLOATWARE PACKAGES</div>\n" +
                "    <div id=\"debloatList\"></div>\n" +
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
                "      toastTimer = setTimeout(() => t.classList.remove('show'), 2200);\n" +
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
                "      if (tab === 'addons') loadAddons();\n" +
                "      if (tab === 'note') { if (!noteCanvasInitialized) setTimeout(initNoteCanvas, 50); else loadNoteState(); }\n" +
                "      if (tab === 'debloat') loadDebloat();\n" +
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
                "    function loadAddons() {\n" +
                "      fetch('/api/addons').then(r => r.json()).then(data => {\n" +
                "        const c = document.getElementById('addonsList');\n" +
                "        if (!data || data.length === 0) {\n" +
                "          c.innerHTML = '<div class=\"empty-state\">No add-ons available</div>';\n" +
                "          return;\n" +
                "        }\n" +
                "        c.innerHTML = data.map(item => {\n" +
                "          const btn = item.installed\n" +
                "            ? `<button class=\"addon-btn btn-open\" onclick=\"launchApp('${encodeURIComponent(item.pkg)}', '${encodeURIComponent(item.name)}')\">Open on TV</button>`\n" +
                "            : `<button class=\"addon-btn btn-install\" onclick=\"installAddon('${item.id}', '${encodeURIComponent(item.name)}')\">Install on TV</button>`;\n" +
                "          return `<div class=\"addon-card\">` +\n" +
                "            `<div class=\"addon-top\"><span class=\"addon-tag\">${item.category}</span><span style=\"font-size:11px;color:${item.installed ? '#10b981' : '#64748b'};font-weight:700;\">${item.installed ? 'Installed' : 'Available'}</span></div>` +\n" +
                "            `<div class=\"addon-title\">${item.name}</div>` +\n" +
                "            `<div class=\"addon-desc\">${item.description}</div>` +\n" +
                "            btn +\n" +
                "          `</div>`;\n" +
                "        }).join('');\n" +
                "      }).catch(()=>{});\n" +
                "    }\n" +
                "    function installAddon(id, nameEnc) {\n" +
                "      haptic();\n" +
                "      const name = decodeURIComponent(nameEnc);\n" +
                "      showToast('Downloading ' + name + ' on TV...');\n" +
                "      fetch('/api/addons/install?id=' + id, { method: 'POST' }).catch(()=>{});\n" +
                "    }\n" +
                "    function uploadApk() {\n" +
                "      const fileInput = document.getElementById('fileApk');\n" +
                "      if (!fileInput.files || fileInput.files.length === 0) {\n" +
                "        alert('Please choose an .apk file to upload');\n" +
                "        return;\n" +
                "      }\n" +
                "      const file = fileInput.files[0];\n" +
                "      haptic();\n" +
                "      showToast('Uploading ' + file.name + ' to TV...');\n" +
                "      fetch('/api/upload?name=' + encodeURIComponent(file.name), {\n" +
                "        method: 'POST',\n" +
                "        body: file\n" +
                "      }).then(r => r.json()).then(res => {\n" +
                "        if (res.status === 'ok') {\n" +
                "          showToast('Uploaded! Check TV screen to install');\n" +
                "          fileInput.value = '';\n" +
                "        } else {\n" +
                "          alert('Upload failed: ' + (res.message || 'Error'));\n" +
                "        }\n" +
                "      }).catch(err => {\n" +
                "        alert('Upload error: ' + err);\n" +
                "      });\n" +
                "    }\n" +
                "    let currentProfile = 'personal';\n" +
                "    function cycleProfile() {\n" +
                "      haptic();\n" +
                "      if (currentProfile === 'kids') {\n" +
                "        const pin = prompt('Enter 4-digit Parent PIN to exit Kids Mode (Default: 0000):');\n" +
                "        if (!pin) return;\n" +
                "        fetch('/api/profile?name=personal&pin=' + encodeURIComponent(pin)).then(r => r.json()).then(res => {\n" +
                "          if (res.status === 'ok') { showToast('Switched to Personal Profile'); updateStatus(); }\n" +
                "          else { alert('Incorrect PIN!'); }\n" +
                "        }).catch(()=>{});\n" +
                "        return;\n" +
                "      }\n" +
                "      const target = currentProfile === 'personal' ? 'kids' : 'personal';\n" +
                "      fetch('/api/profile?name=' + target).then(r => r.json()).then(res => {\n" +
                "        if (res.status === 'ok') { showToast('Switched to ' + target); updateStatus(); }\n" +
                "      }).catch(()=>{});\n" +
                "    }\n" +
                "    function updateStatus() {\n" +
                "      fetch('/api/status').then(r => r.json()).then(data => {\n" +
                "        if (data.profile) currentProfile = data.profile;\n" +
                "      }).catch(()=>{});\n" +
                "    }\n" +
                "    setInterval(updateStatus, 5000);\n" +
                "    updateStatus();\n" +
                "    function loadDebloat() {\n" +
                "      fetch('/api/debloat/scan').then(r => r.json()).then(data => {\n" +
                "        const badge = document.getElementById('debloatBrandBadge');\n" +
                "        const summary = document.getElementById('debloatDevSummary');\n" +
                "        const list = document.getElementById('debloatList');\n" +
                "        if (badge && data.brandName) badge.textContent = data.brandName;\n" +
                "        if (summary && data.device) summary.textContent = data.device;\n" +
                "        if (!data.items || data.items.length === 0) {\n" +
                "          list.innerHTML = '<div class=\'empty-state\'>🎉 Clean TV! No OEM bloatware detected.</div>';\n" +
                "          return;\n" +
                "        }\n" +
                "        list.innerHTML = data.items.map(it => {\n" +
                "          const isBloat = it.enabled;\n" +
                "          const statusColor = isBloat ? '#ef4444' : '#10b981';\n" +
                "          const statusText = isBloat ? 'Active Bloat' : 'Disabled (Clean)';\n" +
                "          const btnText = isBloat ? 'Disable' : 'Enable';\n" +
                "          const btnBg = isBloat ? 'rgba(239,68,68,0.2)' : 'rgba(16,185,129,0.2)';\n" +
                "          const btnColor = isBloat ? '#ef4444' : '#10b981';\n" +
                "          return `<div class=\'addon-card\'>` +\n" +
                "            `<div class=\'addon-top\'><span class=\'addon-tag\' style=\'color:#f59e0b;background:rgba(245,158,11,0.15);\'>${it.category}</span><span style=\'font-size:11px;color:${statusColor};font-weight:700;\'>${statusText}</span></div>` +\n" +
                "            `<div class=\'addon-title\'>${it.label}</div>` +\n" +
                "            `<div class=\'addon-desc\'>${it.description} <br><code style=\'font-size:10px;color:#64748b;\'>${it.pkg}</code></div>` +\n" +
                "            `<button class=\"addon-btn\" style=\"background:${btnBg};color:${btnColor};border:1px solid ${btnColor};\" onclick=\"toggleDebloat(\'${encodeURIComponent(it.pkg)}\', ${!isBloat})\">${btnText} on TV</button>` +\n" +
                "          `</div>`;\n" +
                "        }).join('');\n" +
                "      }).catch(()=>{});\n" +
                "    }\n" +
                "    function toggleDebloat(pkgEnc, enable) {\n" +
                "      haptic();\n" +
                "      const pkg = decodeURIComponent(pkgEnc);\n" +
                "      const endpoint = enable ? '/api/debloat/restore?pkg=' : '/api/debloat/apply?pkg=';\n" +
                "      showToast((enable ? 'Enabling ' : 'Disabling ') + pkg);\n" +
                "      fetch(endpoint + encodeURIComponent(pkg), { method: 'POST' }).then(() => loadDebloat()).catch(()=>{});\n" +
                "    }\n" +
                "    function runAutoDebloat() {\n" +
                "      haptic();\n" +
                "      showToast('Running 1-Click Auto-Debloat on TV...');\n" +
                "      fetch('/api/debloat/apply', { method: 'POST' }).then(r => r.json()).then(res => {\n" +
                "        showToast('Debloated ' + (res.count || 0) + ' OEM packages!');\n" +
                "        loadDebloat();\n" +
                "      }).catch(()=>{});\n" +
                "    }\n" +
                "    let currentNoteColor = '#FFFFFF';\n" +
                "    let currentStrokeWidth = 0.016;\n" +
                "    let isDrawing = false;\n" +
                "    let currentPoints = [];\n" +
                "    let noteCanvasInitialized = false;\n" +
                "    function initNoteCanvas() {\n" +
                "      const canvas = document.getElementById('noteCanvas');\n" +
                "      if (!canvas) return;\n" +
                "      const rect = canvas.getBoundingClientRect();\n" +
                "      if (!rect.width || !rect.height) {\n" +
                "        setTimeout(initNoteCanvas, 100);\n" +
                "        return;\n" +
                "      }\n" +
                "      const dpr = window.devicePixelRatio || 1;\n" +
                "      canvas.width = rect.width * dpr;\n" +
                "      canvas.height = rect.height * dpr;\n" +
                "      const ctx = canvas.getContext('2d');\n" +
                "      ctx.scale(dpr, dpr);\n" +
                "      ctx.lineCap = 'round';\n" +
                "      ctx.lineJoin = 'round';\n" +
                "      function getPoint(e) {\n" +
                "        const r = canvas.getBoundingClientRect();\n" +
                "        const clientX = (e.touches && e.touches.length > 0) ? e.touches[0].clientX : (e.changedTouches && e.changedTouches.length > 0 ? e.changedTouches[0].clientX : e.clientX);\n" +
                "        const clientY = (e.touches && e.touches.length > 0) ? e.touches[0].clientY : (e.changedTouches && e.changedTouches.length > 0 ? e.changedTouches[0].clientY : e.clientY);\n" +
                "        return { x: Math.max(0, Math.min(1, (clientX - r.left) / r.width)), y: Math.max(0, Math.min(1, (clientY - r.top) / r.height)) };\n" +
                "      }\n" +
                "      function startDraw(e) {\n" +
                "        e.preventDefault();\n" +
                "        isDrawing = true;\n" +
                "        currentPoints = [];\n" +
                "        currentPoints.push(getPoint(e));\n" +
                "      }\n" +
                "      function moveDraw(e) {\n" +
                "        if (!isDrawing) return;\n" +
                "        e.preventDefault();\n" +
                "        const pt = getPoint(e);\n" +
                "        currentPoints.push(pt);\n" +
                "        if (currentPoints.length >= 2) {\n" +
                "          const p1 = currentPoints[currentPoints.length - 2];\n" +
                "          const p2 = currentPoints[currentPoints.length - 1];\n" +
                "          const r = canvas.getBoundingClientRect();\n" +
                "          const minDim = Math.min(r.width, r.height);\n" +
                "          ctx.strokeStyle = currentNoteColor;\n" +
                "          ctx.lineWidth = Math.max(2, currentStrokeWidth * minDim);\n" +
                "          ctx.beginPath();\n" +
                "          ctx.moveTo(p1.x * r.width, p1.y * r.height);\n" +
                "          ctx.lineTo(p2.x * r.width, p2.y * r.height);\n" +
                "          ctx.stroke();\n" +
                "        }\n" +
                "      }\n" +
                "      function endDraw(e) {\n" +
                "        if (!isDrawing) return;\n" +
                "        e.preventDefault();\n" +
                "        isDrawing = false;\n" +
                "        if (currentPoints.length === 1) {\n" +
                "          const r = canvas.getBoundingClientRect();\n" +
                "          const minDim = Math.min(r.width, r.height);\n" +
                "          const pt = currentPoints[0];\n" +
                "          const radius = Math.max(2, (currentStrokeWidth * minDim) / 2);\n" +
                "          ctx.fillStyle = currentNoteColor;\n" +
                "          ctx.beginPath();\n" +
                "          ctx.arc(pt.x * r.width, pt.y * r.height, radius, 0, Math.PI * 2);\n" +
                "          ctx.fill();\n" +
                "        }\n" +
                "        if (currentPoints.length >= 1) {\n" +
                "          const strokeData = { color: currentNoteColor, width: currentStrokeWidth, points: currentPoints };\n" +
                "          haptic();\n" +
                "          fetch('/api/canvas/stroke', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(strokeData) }).catch(()=>{});\n" +
                "        }\n" +
                "        currentPoints = [];\n" +
                "      }\n" +
                "      canvas.addEventListener('touchstart', startDraw, { passive: false });\n" +
                "      canvas.addEventListener('touchmove', moveDraw, { passive: false });\n" +
                "      canvas.addEventListener('touchend', endDraw, { passive: false });\n" +
                "      canvas.addEventListener('touchcancel', endDraw, { passive: false });\n" +
                "      canvas.addEventListener('mousedown', startDraw);\n" +
                "      window.addEventListener('mousemove', moveDraw);\n" +
                "      window.addEventListener('mouseup', endDraw);\n" +
                "      if (!noteCanvasInitialized) {\n" +
                "        window.addEventListener('resize', () => {\n" +
                "          if (noteCanvasInitialized) {\n" +
                "            const r = canvas.getBoundingClientRect();\n" +
                "            if (r.width > 0 && r.height > 0) {\n" +
                "              const d = window.devicePixelRatio || 1;\n" +
                "              canvas.width = r.width * d;\n" +
                "              canvas.height = r.height * d;\n" +
                "              const c = canvas.getContext('2d');\n" +
                "              c.scale(d, d);\n" +
                "              c.lineCap = 'round';\n" +
                "              c.lineJoin = 'round';\n" +
                "              loadNoteState();\n" +
                "            }\n" +
                "          }\n" +
                "        });\n" +
                "      }\n" +
                "      noteCanvasInitialized = true;\n" +
                "      loadNoteState();\n" +
                "    }\n" +
                "    function loadNoteState() {\n" +
                "      fetch('/api/canvas/state').then(r => r.json()).then(data => {\n" +
                "        const strokes = (data && data.strokes) ? data.strokes : (Array.isArray(data) ? data : []);\n" +
                "        redrawNoteCanvas(strokes);\n" +
                "      }).catch(()=>{});\n" +
                "    }\n" +
                "    function redrawNoteCanvas(strokes) {\n" +
                "      const canvas = document.getElementById('noteCanvas');\n" +
                "      if (!canvas) return;\n" +
                "      const ctx = canvas.getContext('2d');\n" +
                "      const r = canvas.getBoundingClientRect();\n" +
                "      const minDim = Math.min(r.width, r.height);\n" +
                "      ctx.save();\n" +
                "      ctx.setTransform(1, 0, 0, 1, 0, 0);\n" +
                "      ctx.clearRect(0, 0, canvas.width, canvas.height);\n" +
                "      ctx.restore();\n" +
                "      if (!strokes || !Array.isArray(strokes)) return;\n" +
                "      strokes.forEach(s => {\n" +
                "        if (!s.points || s.points.length === 0) return;\n" +
                "        const pts = s.points;\n" +
                "        const strokeW = s.width ? (s.width > 0.5 ? s.width : Math.max(2, s.width * minDim)) : 4;\n" +
                "        if (pts.length === 1) {\n" +
                "          ctx.fillStyle = s.color || '#FFFFFF';\n" +
                "          ctx.beginPath();\n" +
                "          ctx.arc(pts[0].x * r.width, pts[0].y * r.height, strokeW / 2, 0, Math.PI * 2);\n" +
                "          ctx.fill();\n" +
                "          return;\n" +
                "        }\n" +
                "        ctx.strokeStyle = s.color || '#FFFFFF';\n" +
                "        ctx.lineWidth = strokeW;\n" +
                "        ctx.beginPath();\n" +
                "        ctx.moveTo(pts[0].x * r.width, pts[0].y * r.height);\n" +
                "        for (let i = 1; i < pts.length; i++) {\n" +
                "          ctx.lineTo(pts[i].x * r.width, pts[i].y * r.height);\n" +
                "        }\n" +
                "        ctx.stroke();\n" +
                "      });\n" +
                "    }\n" +
                "    function setColor(c, btn) {\n" +
                "      haptic();\n" +
                "      currentNoteColor = c;\n" +
                "      document.querySelectorAll('.color-swatch').forEach(b => b.classList.remove('selected'));\n" +
                "      if (btn) btn.classList.add('selected');\n" +
                "    }\n" +
                "    function setStrokeWidth(w, btn) {\n" +
                "      haptic();\n" +
                "      currentStrokeWidth = w;\n" +
                "      document.querySelectorAll('.width-btn').forEach(b => b.classList.remove('active'));\n" +
                "      if (btn) btn.classList.add('active');\n" +
                "    }\n" +
                "    function clearNote() {\n" +
                "      haptic();\n" +
                "      const canvas = document.getElementById('noteCanvas');\n" +
                "      if (canvas) {\n" +
                "        const ctx = canvas.getContext('2d');\n" +
                "        ctx.save();\n" +
                "        ctx.setTransform(1, 0, 0, 1, 0, 0);\n" +
                "        ctx.clearRect(0, 0, canvas.width, canvas.height);\n" +
                "        ctx.restore();\n" +
                "      }\n" +
                "      showToast('Note cleared on TV');\n" +
                "      fetch('/api/canvas/clear', { method: 'POST' }).catch(()=>{});\n" +
                "    }\n" +
                "    function runRestoreDebloat() {\n" +
                "      haptic();\n" +
                "      showToast('Restoring all OEM packages...');\n" +
                "      fetch('/api/debloat/restore', { method: 'POST' }).then(r => r.json()).then(res => {\n" +
                "        showToast('Restored ' + (res.count || 0) + ' packages');\n" +
                "        loadDebloat();\n" +
                "      }).catch(()=>{});\n" +
                "    }\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
