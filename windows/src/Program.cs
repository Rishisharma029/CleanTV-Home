using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

namespace CleanTV.Desktop
{
    static class Program
    {
        private const int PORT = 59822;
        private static HttpListener listener;
        private static NotifyIcon trayIcon;
        private static string appDir;
        private static string uiDir;
        private static string presetsDir;
        private static Process edgeProcess = null;
        private static string cachedAdbPath = null;
        private static string sessionToken = Guid.NewGuid().ToString("N");
        private static bool isHeadless = false;

        private static readonly HashSet<string> ProtectedSystemPackages = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.android.providers.settings",
            "com.android.shell"
        };

        [STAThread]
        static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            appDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');

            // Parse CLI flags
            if (args != null)
            {
                foreach (string a in args)
                {
                    if (a.Equals("--server-only", StringComparison.OrdinalIgnoreCase) ||
                        a.Equals("--headless", StringComparison.OrdinalIgnoreCase) ||
                        a.Equals("/headless", StringComparison.OrdinalIgnoreCase))
                    {
                        isHeadless = true;
                    }
                    else if (a.StartsWith("--adb=", StringComparison.OrdinalIgnoreCase))
                    {
                        cachedAdbPath = a.Substring(6).Trim('"');
                    }
                }
            }

            // Resolve UI directory
            uiDir = Path.Combine(appDir, "ui");
            if (!Directory.Exists(uiDir))
            {
                string srcUi = Path.Combine(appDir, "src", "ui");
                if (Directory.Exists(srcUi)) uiDir = srcUi;
                else uiDir = appDir;
            }

            // Resolve Presets directory (read-only bundle)
            presetsDir = Path.Combine(appDir, "presets");
            if (!Directory.Exists(presetsDir))
            {
                string parentPresets = Path.Combine(appDir, "..", "presets");
                if (Directory.Exists(parentPresets)) presetsDir = Path.GetFullPath(parentPresets);
            }

            // Locate ADB on startup
            cachedAdbPath = ResolveAdb();

            // Start Embedded Web & API Server
            StartHttpServer();

            if (!isHeadless)
            {
                // Setup System Tray Icon
                SetupTray();

                // Launch App Window
                LaunchAppWindow();
            }

            Application.Run();
        }

        public static string GetBackupsDirectory()
        {
            // Always store backups in LocalAppData to comply with MSIX read-only restrictions
            string localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string dir = Path.Combine(localApp, "CleanTV", "backups");
            if (!Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
            }
            return dir;
        }

        public static string ResolveAdb()
        {
            // 0. Environment variable override (for mock ADB / testing)
            string envAdb = Environment.GetEnvironmentVariable("CLEANTV_ADB_PATH");
            if (!string.IsNullOrEmpty(envAdb) && File.Exists(envAdb)) return envAdb;

            // 1. Check bundled platform-tools next to exe
            string bundled = Path.Combine(appDir, "platform-tools", "adb.exe");
            if (File.Exists(bundled)) return bundled;

            // 2. Check repo tools directory
            string repoTools = Path.Combine(appDir, "..", "windows", "platform-tools", "adb.exe");
            if (File.Exists(repoTools)) return Path.GetFullPath(repoTools);

            string repoToolsDirect = Path.Combine(appDir, "..", "tools", "platform-tools", "adb.exe");
            if (File.Exists(repoToolsDirect)) return Path.GetFullPath(repoToolsDirect);

            // 3. Check Android SDK in LocalAppData
            string localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string sdkAdb = Path.Combine(localApp, "Android", "Sdk", "platform-tools", "adb.exe");
            if (File.Exists(sdkAdb)) return sdkAdb;

            // 4. Check system PATH
            try
            {
                var psi = new ProcessStartInfo
                {
                    FileName = "adb",
                    Arguments = "version",
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true
                };
                using (var p = Process.Start(psi))
                {
                    p.WaitForExit(3000);
                    if (p.ExitCode == 0) return "adb";
                }
            }
            catch { }

            return null;
        }

        private struct AdbResult
        {
            public int ExitCode;
            public string StdOut;
            public string StdErr;
            public string Combined
            {
                get { return (((StdOut ?? "") + " " + (StdErr ?? ""))).Trim(); }
            }
        }

        private static AdbResult RunAdb(string args, int timeoutMs = 12000)
        {
            string adbExe = cachedAdbPath ?? ResolveAdb();
            if (string.IsNullOrEmpty(adbExe))
            {
                return new AdbResult { ExitCode = -1, StdOut = "", StdErr = "ADB not found. Please bundle platform-tools or add adb to PATH." };
            }

            try
            {
                var psi = new ProcessStartInfo
                {
                    FileName = adbExe,
                    Arguments = args,
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true
                };

                using (var p = Process.Start(psi))
                {
                    string stdout = p.StandardOutput.ReadToEnd();
                    string stderr = p.StandardError.ReadToEnd();
                    if (!p.WaitForExit(timeoutMs))
                    {
                        try { p.Kill(); } catch { }
                        return new AdbResult { ExitCode = -2, StdOut = stdout, StdErr = "Timeout after " + (timeoutMs / 1000) + "s" };
                    }
                    return new AdbResult { ExitCode = p.ExitCode, StdOut = stdout, StdErr = stderr };
                }
            }
            catch (Exception ex)
            {
                return new AdbResult { ExitCode = -3, StdOut = "", StdErr = ex.Message };
            }
        }

        private static void StartHttpServer()
        {
            try
            {
                listener = new HttpListener();
                listener.Prefixes.Add("http://127.0.0.1:" + PORT + "/");
                listener.Prefixes.Add("http://localhost:" + PORT + "/");
                listener.Start();

                ThreadPool.QueueUserWorkItem(state =>
                {
                    while (listener != null && listener.IsListening)
                    {
                        try
                        {
                            var context = listener.GetContext();
                            ThreadPool.QueueUserWorkItem(ctxObj => HandleRequest((HttpListenerContext)ctxObj), context);
                        }
                        catch
                        {
                            if (listener == null || !listener.IsListening) break;
                        }
                    }
                });
            }
            catch (Exception ex)
            {
                if (!isHeadless)
                {
                    MessageBox.Show("Could not start local server on port " + PORT + ":\n" + ex.Message,
                        "CleanTV Server Notice", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
            }
        }

        private static void HandleRequest(HttpListenerContext context)
        {
            try
            {
                var req = context.Request;
                var res = context.Response;

                // Security: Restrict CORS strictly to loopback origins
                string origin = req.Headers["Origin"];
                if (string.IsNullOrEmpty(origin) ||
                    origin.StartsWith("http://127.0.0.1:", StringComparison.OrdinalIgnoreCase) ||
                    origin.StartsWith("http://localhost:", StringComparison.OrdinalIgnoreCase))
                {
                    res.AddHeader("Access-Control-Allow-Origin", string.IsNullOrEmpty(origin) ? "http://127.0.0.1:" + PORT : origin);
                }
                else
                {
                    res.AddHeader("Access-Control-Allow-Origin", "null");
                }

                res.AddHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                res.AddHeader("Access-Control-Allow-Headers", "Content-Type, X-CleanTV-Token");

                if (req.HttpMethod == "OPTIONS")
                {
                    res.StatusCode = 204;
                    res.Close();
                    return;
                }

                string urlPath = req.Url.AbsolutePath;

                if (urlPath.StartsWith("/api/"))
                {
                    HandleApi(urlPath, req, res);
                    return;
                }

                // Serve Static Files
                res.AddHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                res.AddHeader("Pragma", "no-cache");
                res.AddHeader("Expires", "0");

                if (urlPath == "/" || string.IsNullOrEmpty(urlPath))
                {
                    urlPath = "/index.html";
                }

                string fullUiDir = Path.GetFullPath(uiDir);
                string cleanRelative = urlPath.TrimStart('/').Replace('/', '\\');
                string filePath = Path.GetFullPath(Path.Combine(uiDir, cleanRelative));

                // Path Traversal Guard: strictly verify file stays inside uiDir
                if (!filePath.StartsWith(fullUiDir, StringComparison.OrdinalIgnoreCase))
                {
                    res.StatusCode = 403;
                    byte[] forbidden = Encoding.UTF8.GetBytes("Forbidden: Access denied.");
                    res.OutputStream.Write(forbidden, 0, forbidden.Length);
                    res.Close();
                    return;
                }

                if (File.Exists(filePath))
                {
                    string ext = Path.GetExtension(filePath).ToLower();
                    res.ContentType = GetMimeType(ext);

                    if (ext == ".html")
                    {
                        // Inject session token into HTML for authenticated client-server calls
                        string htmlContent = File.ReadAllText(filePath, Encoding.UTF8);
                        string tokenMeta = string.Format("<meta name=\"cleantv-token\" content=\"{0}\">", sessionToken);
                        htmlContent = htmlContent.Replace("<head>", "<head>\n  " + tokenMeta);
                        byte[] htmlBytes = Encoding.UTF8.GetBytes(htmlContent);
                        res.ContentLength64 = htmlBytes.Length;
                        res.OutputStream.Write(htmlBytes, 0, htmlBytes.Length);
                    }
                    else
                    {
                        byte[] fileBytes = File.ReadAllBytes(filePath);
                        res.ContentLength64 = fileBytes.Length;
                        res.OutputStream.Write(fileBytes, 0, fileBytes.Length);
                    }
                }
                else
                {
                    res.StatusCode = 404;
                    byte[] notFound = Encoding.UTF8.GetBytes("Not Found");
                    res.OutputStream.Write(notFound, 0, notFound.Length);
                }

                res.Close();
            }
            catch
            {
                // Handled gracefully
            }
        }

        private static string NormalizeDeviceEndpoint(string dev)
        {
            if (string.IsNullOrEmpty(dev)) return "127.0.0.1:5555";
            dev = dev.Trim();
            // Security: Reject spaces or shell metacharacters to prevent command injection
            if (!Regex.IsMatch(dev, @"^[a-zA-Z0-9_\.\-:]+$")) return "127.0.0.1:5555";
            if (dev.Contains(":")) return dev;
            // Only append port if it's an IP address or hostname with dots / localhost
            if (dev.Contains(".") || dev.Equals("localhost", StringComparison.OrdinalIgnoreCase))
            {
                return dev + ":5555";
            }
            // USB serial (e.g. "0123456789ABCDEF" or "emulator-5554")
            return dev;
        }

        public class HomeLauncherInfo
        {
            public string Package;
            public string Component;
        }

        private static HomeLauncherInfo GetActiveHomeLauncher(string device)
        {
            // 1. Primary: modern Android (Android 8+) Intent resolution for category HOME
            var homeRes = RunAdb("-s " + device + " shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME", 5000);
            if (homeRes.ExitCode == 0)
            {
                var info = ParseHomeLauncher(homeRes.Combined);
                if (info != null) return info;
            }

            // 2. Fallback for older Android (Android 5-7): pm resolve-activity for Intent category HOME
            var pmRes = RunAdb("-s " + device + " shell pm resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME", 5000);
            if (pmRes.ExitCode == 0)
            {
                var info = ParseHomeLauncher(pmRes.Combined);
                if (info != null) return info;
            }

            // Fail-closed: Never fall back to mResumedActivity (which identifies foreground apps like YouTube or Settings, not HOME)
            return null;
        }

        private static HomeLauncherInfo ParseHomeLauncher(string output)
        {
            if (string.IsNullOrEmpty(output)) return null;
            var matchComp = Regex.Match(output, @"\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/(\.?[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*)");
            if (matchComp.Success && matchComp.Groups[2].Length > 0)
            {
                return new HomeLauncherInfo
                {
                    Package = matchComp.Groups[1].Value.Trim(),
                    Component = matchComp.Groups[0].Value.Trim()
                };
            }
            var matchPkg = Regex.Match(output, @"\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/");
            if (matchPkg.Success)
            {
                string pkg = matchPkg.Groups[1].Value.Trim();
                return new HomeLauncherInfo
                {
                    Package = pkg,
                    Component = pkg + "/.MainActivity"
                };
            }
            return null;
        }

        private static string GetActiveHomePackage(string device)
        {
            var info = GetActiveHomeLauncher(device);
            return info != null ? info.Package : null;
        }

        private static void HandleApi(string endpoint, HttpListenerRequest req, HttpListenerResponse res)
        {
            // Security: Enforce session token validation on all API calls (Header or QueryString)
            string clientToken = req.Headers["X-CleanTV-Token"] ?? req.QueryString["token"];
            if (string.IsNullOrEmpty(clientToken) || !string.Equals(clientToken, sessionToken, StringComparison.Ordinal))
            {
                SendResponse(res, 401, "{\"status\":\"error\",\"message\":\"Unauthorized: Invalid or missing X-CleanTV-Token session header.\"}");
                return;
            }

            string body = "";
            using (var reader = new StreamReader(req.InputStream, req.ContentEncoding))
            {
                body = reader.ReadToEnd();
            }

            string rawDevice = ExtractJsonField(body, "device") ?? req.QueryString["device"];
            string device = NormalizeDeviceEndpoint(rawDevice);

            string jsonResponse = "{\"status\":\"ok\"}";
            int statusCode = 200;

            if (endpoint == "/api/adb-status")
            {
                cachedAdbPath = ResolveAdb();
                bool found = !string.IsNullOrEmpty(cachedAdbPath);
                bool bundled = found && cachedAdbPath.IndexOf("platform-tools", StringComparison.OrdinalIgnoreCase) >= 0;
                jsonResponse = string.Format("{{\"status\":\"ok\",\"adbFound\":{0},\"adbPath\":{1},\"bundled\":{2},\"sessionToken\":{3}}}",
                    found ? "true" : "false",
                    ToJsonString(cachedAdbPath),
                    bundled ? "true" : "false",
                    ToJsonString(sessionToken));
            }
            else if (endpoint == "/api/connect")
            {
                var connectResult = RunAdb("connect " + device, 10000);
                string outCombined = connectResult.Combined;
                bool isConnectedOutput = outCombined.IndexOf("connected to", StringComparison.OrdinalIgnoreCase) >= 0 ||
                                         outCombined.IndexOf("already connected", StringComparison.OrdinalIgnoreCase) >= 0;

                // Verify with get-state
                var stateResult = RunAdb("-s " + device + " get-state", 4000);
                string state = (stateResult.StdOut ?? "").Trim();
                bool isDevice = state.Equals("device", StringComparison.OrdinalIgnoreCase);

                if ((isConnectedOutput && isDevice) || isDevice)
                {
                    jsonResponse = string.Format("{{\"status\":\"ok\",\"connected\":true,\"device\":{0},\"output\":{1}}}",
                        ToJsonString(device), ToJsonString(outCombined));
                }
                else if (state.Equals("unauthorized", StringComparison.OrdinalIgnoreCase))
                {
                    statusCode = 400;
                    jsonResponse = string.Format("{{\"status\":\"error\",\"connected\":false,\"message\":\"Device unauthorized. Look at TV screen and accept USB Debugging prompt.\"}}");
                }
                else
                {
                    statusCode = 400;
                    string errMsg = string.IsNullOrEmpty(outCombined) ? "Unable to connect to " + device : outCombined;
                    jsonResponse = string.Format("{{\"status\":\"error\",\"connected\":false,\"message\":{0}}}", ToJsonString(errMsg));
                }
            }
            else if (endpoint == "/api/check-state")
            {
                var stateResult = RunAdb("-s " + device + " get-state", 4000);
                string state = (stateResult.StdOut ?? "").Trim();
                bool isOnline = state.Equals("device", StringComparison.OrdinalIgnoreCase);
                jsonResponse = string.Format("{{\"status\":\"ok\",\"connected\":{0},\"state\":{1},\"device\":{2}}}",
                    isOnline ? "true" : "false", ToJsonString(state), ToJsonString(device));
            }
            else if (endpoint == "/api/scan")
            {
                var discovered = DiscoverNetworkDevices();
                var items = new List<string>();
                foreach (var d in discovered)
                {
                    items.Add(string.Format("{{\"serial\":{0},\"state\":{1},\"name\":{2},\"type\":{3},\"ip\":{4},\"port\":{5},\"info\":{6}}}",
                        ToJsonString(d.Serial), ToJsonString(d.State), ToJsonString(d.Name), ToJsonString(d.Type),
                        ToJsonString(d.Ip), ToJsonString(d.Port), ToJsonString(d.Info)));
                }
                jsonResponse = string.Format("{{\"status\":\"ok\",\"devices\":[{0}]}}", string.Join(",", items.ToArray()));
            }
            else if (endpoint == "/api/device-info")
            {
                string brand = RunAdb("-s " + device + " shell getprop ro.product.brand").StdOut.Trim();
                string model = RunAdb("-s " + device + " shell getprop ro.product.model").StdOut.Trim();
                string android = RunAdb("-s " + device + " shell getprop ro.build.version.release").StdOut.Trim();
                string manufacturer = RunAdb("-s " + device + " shell getprop ro.product.manufacturer").StdOut.Trim();

                // Memory info
                string meminfo = RunAdb("-s " + device + " shell cat /proc/meminfo").StdOut;
                string memStr = "Unknown";
                double freeGb = 0;
                double totalGb = 0;
                var memTotalMatch = Regex.Match(meminfo, @"MemTotal:\s+(\d+)");
                var memAvailMatch = Regex.Match(meminfo, @"MemAvailable:\s+(\d+)");
                if (memTotalMatch.Success && memAvailMatch.Success)
                {
                    long totalKb = long.Parse(memTotalMatch.Groups[1].Value);
                    long availKb = long.Parse(memAvailMatch.Groups[1].Value);
                    totalGb = Math.Round(totalKb / 1048576.0, 1);
                    freeGb = Math.Round(availKb / 1048576.0, 1);
                    memStr = freeGb + " GB Free / " + totalGb + " GB Total";
                }

                // Launcher info
                string activeHome = GetActiveHomePackage(device);
                string launcherState = (!string.IsNullOrEmpty(activeHome) && activeHome.Equals("com.gathin.tvhome", StringComparison.OrdinalIgnoreCase))
                    ? "CleanTV Home v4.0 Active"
                    : (string.IsNullOrEmpty(activeHome) ? "Stock Launcher Active" : activeHome + " Active");

                jsonResponse = string.Format("{{\"status\":\"ok\",\"device\":{0},\"brand\":{1},\"model\":{2},\"androidVersion\":{3},\"manufacturer\":{4},\"memory\":{5},\"freeGb\":{6},\"totalGb\":{7},\"launcherState\":{8},\"activeHome\":{9}}}",
                    ToJsonString(device), ToJsonString(brand), ToJsonString(model), ToJsonString(android), ToJsonString(manufacturer), ToJsonString(memStr), freeGb, totalGb, ToJsonString(launcherState), ToJsonString(activeHome));
            }
            else if (endpoint == "/api/preset-list")
            {
                string preset = (ExtractJsonField(body, "preset") ?? req.QueryString["preset"] ?? "realme").ToLower();
                string fileName = ResolvePresetFileName(preset);
                string filePath = FindPresetFile(fileName);

                var pkgs = new List<string>();
                if (File.Exists(filePath))
                {
                    foreach (string line in File.ReadAllLines(filePath))
                    {
                        string clean = line.Trim();
                        if (string.IsNullOrEmpty(clean) || clean.StartsWith("#")) continue;
                        string pkg = clean.Split('#')[0].Trim();
                        if (!string.IsNullOrEmpty(pkg)) pkgs.Add(pkg);
                    }
                }

                var jsonPkgs = new List<string>();
                foreach (var p in pkgs) jsonPkgs.Add(ToJsonString(p));

                jsonResponse = string.Format("{{\"status\":\"ok\",\"preset\":{0},\"file\":{1},\"packages\":[{2}]}}",
                    ToJsonString(preset), ToJsonString(fileName), string.Join(",", jsonPkgs.ToArray()));
            }
            else if (endpoint == "/api/debloat")
            {
                string lk, lp, la;
                if (!GetVerifiedLicense(out lk, out lp, out la))
                {
                    statusCode = 403;
                    jsonResponse = "{\"status\":\"error\",\"code\":\"PRO_REQUIRED\",\"message\":\"System-Wide TV Debloating requires an active CleanTV Pro license. Please upgrade to unlock.\"}";
                    SendResponse(res, statusCode, jsonResponse);
                    return;
                }
                string preset = (ExtractJsonField(body, "preset") ?? req.QueryString["preset"] ?? "realme").ToLower();
                string fileName = ResolvePresetFileName(preset);
                string filePath = FindPresetFile(fileName);

                if (!File.Exists(filePath))
                {
                    statusCode = 400;
                    jsonResponse = string.Format("{{\"status\":\"error\",\"message\":\"Preset file not found: {0}\"}}", fileName);
                }
                else
                {
                    var packagesToDisable = new List<string>();
                    foreach (string line in File.ReadAllLines(filePath))
                    {
                        string clean = line.Trim();
                        if (string.IsNullOrEmpty(clean) || clean.StartsWith("#")) continue;
                        string pkg = clean.Split('#')[0].Trim();
                        if (!string.IsNullOrEmpty(pkg)) packagesToDisable.Add(pkg);
                    }

                    // 1. Mandatory Pre-debloat Backup Creation & Verification
                    string backupDir = null;
                    try
                    {
                        backupDir = CreateBackup(device);
                    }
                    catch (Exception ex)
                    {
                        statusCode = 500;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}",
                            ToJsonString("Pre-debloat safety backup failed: " + ex.Message + ". Debloat operation aborted to protect your TV."));
                        SendResponse(res, statusCode, jsonResponse);
                        return;
                    }

                    if (string.IsNullOrEmpty(backupDir) || !Directory.Exists(backupDir))
                    {
                        statusCode = 500;
                        jsonResponse = "{\"status\":\"error\",\"message\":\"Could not verify backup folder creation. Debloat aborted.\"}";
                        SendResponse(res, statusCode, jsonResponse);
                        return;
                    }

                    // 2. Active Home Launcher & System Protection Verification (Fail-Closed)
                    var activeHomeInfo = GetActiveHomeLauncher(device);
                    if (activeHomeInfo == null || string.IsNullOrEmpty(activeHomeInfo.Package))
                    {
                        statusCode = 500;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}",
                            ToJsonString("Could not identify the TV's active HOME launcher via ADB. Debloating aborted to prevent disabling the TV's only working launcher. Please ensure the TV screen is unlocked and responds to ADB."));
                        SendResponse(res, statusCode, jsonResponse);
                        return;
                    }

                    string activeHome = activeHomeInfo.Package;
                    string activeHomeComp = activeHomeInfo.Component;
                    bool isCleanTvActive = activeHome.Equals("com.gathin.tvhome", StringComparison.OrdinalIgnoreCase);

                    // 3. Immediately persist initial restore metadata into backupDir (MANDATORY write - fails closed if write fails)
                    try
                    {
                        File.WriteAllText(Path.Combine(backupDir, "original-home.txt"), activeHome);
                        File.WriteAllText(Path.Combine(backupDir, "original-home-component.txt"), activeHomeComp);
                    }
                    catch (Exception ex)
                    {
                        statusCode = 500;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}",
                            ToJsonString("Failed to record pre-debloat launcher restore metadata (" + ex.Message + "). Debloat aborted to protect TV."));
                        SendResponse(res, statusCode, jsonResponse);
                        return;
                    }

                    int disabledCount = 0;
                    int skippedCount = 0;
                    int failedCount = 0;
                    var resultsList = new List<string>();

                    foreach (var pkg in packagesToDisable)
                    {
                        // Guard 1: Never disable critical protected system packages
                        if (ProtectedSystemPackages.Contains(pkg))
                        {
                            skippedCount++;
                            resultsList.Add(string.Format("{{\"pkg\":{0},\"result\":\"skipped_protected\",\"reason\":\"Critical Android system package protected by safety guard\"}}", ToJsonString(pkg)));
                            continue;
                        }

                        // Guard 2: Never disable active HOME launcher unless CleanTV is verified active
                        if (!string.IsNullOrEmpty(activeHome) && pkg.Equals(activeHome, StringComparison.OrdinalIgnoreCase))
                        {
                            if (!isCleanTvActive)
                            {
                                skippedCount++;
                                resultsList.Add(string.Format("{{\"pkg\":{0},\"result\":\"skipped_active_launcher\",\"reason\":\"Currently active HOME launcher. Set CleanTV Home as default launcher first.\"}}", ToJsonString(pkg)));
                                continue;
                            }
                        }

                        // Check if package is installed on the device
                        var hasPkg = RunAdb("-s " + device + " shell pm list packages " + pkg, 4000);
                        if (!hasPkg.Combined.Contains(pkg))
                        {
                            skippedCount++;
                            resultsList.Add(string.Format("{{\"pkg\":{0},\"result\":\"not_installed\"}}", ToJsonString(pkg)));
                            continue;
                        }

                        // Execute disable-user
                        var disRes = RunAdb("-s " + device + " shell pm disable-user --user 0 " + pkg, 6000);
                        if (disRes.Combined.IndexOf("disabled", StringComparison.OrdinalIgnoreCase) >= 0 ||
                            disRes.Combined.IndexOf("already", StringComparison.OrdinalIgnoreCase) >= 0)
                        {
                            disabledCount++;
                            resultsList.Add(string.Format("{{\"pkg\":{0},\"result\":\"disabled\"}}", ToJsonString(pkg)));
                        }
                        else
                        {
                            failedCount++;
                            resultsList.Add(string.Format("{{\"pkg\":{0},\"result\":\"failed\",\"error\":{1}}}", ToJsonString(pkg), ToJsonString(disRes.Combined)));
                        }
                    }

                    // 4. Record exact debloated package list in backup (MANDATORY write - no silent swallow)
                    try
                    {
                        var debloatedOnly = new List<string>();
                        foreach (var resItem in resultsList)
                        {
                            if (resItem.Contains("\"result\":\"disabled\""))
                            {
                                var m = Regex.Match(resItem, "\"pkg\":\"([^\"]+)\"");
                                if (m.Success) debloatedOnly.Add(m.Groups[1].Value);
                            }
                        }
                        File.WriteAllLines(Path.Combine(backupDir, "debloated-packages.txt"), debloatedOnly.ToArray());
                    }
                    catch (Exception ex)
                    {
                        statusCode = 500;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}",
                            ToJsonString("Failed to record debloated package list in backup (" + ex.Message + ")."));
                        SendResponse(res, statusCode, jsonResponse);
                        return;
                    }

                    jsonResponse = string.Format("{{\"status\":\"ok\",\"preset\":{0},\"total\":{1},\"disabled\":{2},\"skipped\":{3},\"failed\":{4},\"backupDir\":{5},\"results\":[{6}]}}",
                        ToJsonString(preset), packagesToDisable.Count, disabledCount, skippedCount, failedCount, ToJsonString(backupDir), string.Join(",", resultsList.ToArray()));
                }
            }
            else if (endpoint == "/api/restore")
            {
                // Backup-driven restore: scoped strictly to the currently connected device
                string targetBackupDir = FindLatestBackupDir(device);

                if (string.IsNullOrEmpty(targetBackupDir) || !File.Exists(Path.Combine(targetBackupDir, "packages-disabled.txt")))
                {
                    statusCode = 404;
                    jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}",
                        ToJsonString("No valid backup found for device " + device + ". CleanTV creates a pre-debloat backup before any modifications. Cannot restore without a verified backup for this device."));
                    SendResponse(res, statusCode, jsonResponse);
                    return;
                }

                string preDisabledFile = Path.Combine(targetBackupDir, "packages-disabled.txt");
                var preDisabled = new HashSet<string>();
                foreach (var l in File.ReadAllLines(preDisabledFile))
                {
                    string p = l.Replace("package:", "").Trim();
                    if (!string.IsNullOrEmpty(p)) preDisabled.Add(p);
                }

                // Query currently disabled packages from TV
                var curDisabledRes = RunAdb("-s " + device + " shell pm list packages -d", 8000);
                if (curDisabledRes.ExitCode != 0)
                {
                    statusCode = 500;
                    jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}",
                        ToJsonString("Failed to query disabled packages from TV: " + curDisabledRes.Combined));
                    SendResponse(res, statusCode, jsonResponse);
                    return;
                }

                int restoredCount = 0;
                var restoredPkgs = new List<string>();
                var curDisabled = new List<string>();
                foreach (var l in curDisabledRes.StdOut.Split(new char[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries))
                {
                    string p = l.Replace("package:", "").Trim();
                    if (!string.IsNullOrEmpty(p)) curDisabled.Add(p);
                }

                // Restore precision: If debloated-packages.txt exists, only restore packages CleanTV actually disabled.
                // Otherwise fall back to baseline difference.
                string debloatedFile = Path.Combine(targetBackupDir, "debloated-packages.txt");
                var pkgsToRestore = new List<string>();
                if (File.Exists(debloatedFile))
                {
                    foreach (var l in File.ReadAllLines(debloatedFile))
                    {
                        string p = l.Trim();
                        if (!string.IsNullOrEmpty(p)) pkgsToRestore.Add(p);
                    }
                }
                else
                {
                    foreach (var p in curDisabled)
                    {
                        if (!preDisabled.Contains(p)) pkgsToRestore.Add(p);
                    }
                }

                foreach (var p in pkgsToRestore)
                {
                    var enRes = RunAdb("-s " + device + " shell pm enable --user 0 " + p, 5000);
                    if (enRes.ExitCode == 0)
                    {
                        restoredCount++;
                        restoredPkgs.Add(ToJsonString(p));
                    }
                }

                // Restore original active launcher: read exact recorded original component from backup
                string origCompFile = Path.Combine(targetBackupDir, "original-home-component.txt");
                string origHomeFile = Path.Combine(targetBackupDir, "original-home.txt");
                string originalComponent = File.Exists(origCompFile) ? File.ReadAllText(origCompFile).Trim() : null;
                string originalHome = File.Exists(origHomeFile) ? File.ReadAllText(origHomeFile).Trim() : null;

                if (string.IsNullOrEmpty(originalComponent) && !string.IsNullOrEmpty(originalHome))
                {
                    if (originalHome.Contains("/"))
                    {
                        originalComponent = originalHome;
                        originalHome = originalHome.Substring(0, originalHome.IndexOf('/'));
                    }
                    else
                    {
                        originalComponent = originalHome + "/.MainActivity";
                    }
                }
                else if (!string.IsNullOrEmpty(originalComponent) && string.IsNullOrEmpty(originalHome))
                {
                    originalHome = originalComponent.Contains("/") ? originalComponent.Substring(0, originalComponent.IndexOf('/')) : originalComponent;
                }

                bool homeRestored = false;
                if (!string.IsNullOrEmpty(originalComponent))
                {
                    RunAdb("-s " + device + " shell pm enable --user 0 " + originalHome, 4000);
                    var setHomeRes = RunAdb("-s " + device + " shell cmd package set-home-activity --user 0 " + originalComponent, 4000);
                    if (setHomeRes.ExitCode == 0 && !setHomeRes.Combined.Contains("Error") && !setHomeRes.Combined.Contains("Exception"))
                    {
                        // Readback verification: query HOME again to verify active launcher matches
                        var activeAfter = GetActiveHomeLauncher(device);
                        if (activeAfter != null && (
                            (!string.IsNullOrEmpty(activeAfter.Component) && activeAfter.Component.Equals(originalComponent, StringComparison.OrdinalIgnoreCase)) ||
                            (!string.IsNullOrEmpty(activeAfter.Package) && activeAfter.Package.Equals(originalHome, StringComparison.OrdinalIgnoreCase))
                        ))
                        {
                            homeRestored = true;
                        }
                    }
                }
                else
                {
                    // Fallback to stock launcher matching device baseline for legacy backups
                    string[] stockLaunchers = new string[] {
                        "com.google.android.tvlauncher",
                        "com.changhong.system.launcher.v2",
                        "com.mitv.tvhome",
                        "com.sony.dtv.tvx",
                        "com.hisense.launcher"
                    };
                    foreach (var sl in stockLaunchers)
                    {
                        var chk = RunAdb("-s " + device + " shell pm list packages " + sl, 3000);
                        if (chk.Combined.Contains(sl))
                        {
                            RunAdb("-s " + device + " shell pm enable --user 0 " + sl, 4000);
                            var setRes = RunAdb("-s " + device + " shell cmd package set-home-activity --user 0 " + sl + "/.MainActivity", 4000);
                            if (setRes.ExitCode == 0 && !setRes.Combined.Contains("Error") && !setRes.Combined.Contains("Exception"))
                            {
                                homeRestored = true;
                                originalComponent = sl + "/.MainActivity";
                            }
                            break;
                        }
                    }
                }

                RunAdb("-s " + device + " shell input keyevent 3", 3000); // Home

                jsonResponse = string.Format("{{\"status\":\"ok\",\"restored\":{0},\"backupDir\":{1},\"packages\":[{2}],\"homeRestored\":{3},\"originalComponent\":{4}}}",
                    restoredCount, ToJsonString(targetBackupDir), string.Join(",", restoredPkgs.ToArray()), homeRestored ? "true" : "false", ToJsonString(originalComponent ?? ""));
            }
            else if (endpoint == "/api/install")
            {
                string apkPath = FindLauncherApk();
                if (string.IsNullOrEmpty(apkPath) || !File.Exists(apkPath))
                {
                    statusCode = 404;
                    jsonResponse = "{\"status\":\"error\",\"message\":\"CleanTV Launcher APK not found in package.\"}";
                }
                else
                {
                    var instRes = RunAdb("-s " + device + " install -r -d \"" + apkPath + "\"", 60000);
                    if (instRes.ExitCode == 0 && instRes.Combined.IndexOf("Success", StringComparison.OrdinalIgnoreCase) >= 0)
                    {
                        RunAdb("-s " + device + " shell am start -n com.gathin.tvhome/.MainActivity", 5000);
                        jsonResponse = string.Format("{{\"status\":\"ok\",\"installed\":true,\"launched\":true,\"output\":{0}}}",
                            ToJsonString(instRes.Combined));
                    }
                    else
                    {
                        statusCode = 500;
                        string err = string.IsNullOrEmpty(instRes.Combined) ? "Install command returned non-zero exit code" : instRes.Combined;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"installed\":false,\"message\":{0}}}", ToJsonString(err));
                    }
                }
            }
            else if (endpoint == "/api/install-smarttube")
            {
                string stPath = Path.Combine(appDir, "..", "apks", "smarttube-latest.apk");
                if (!File.Exists(stPath)) stPath = Path.Combine(appDir, "apks", "smarttube-latest.apk");

                if (!File.Exists(stPath))
                {
                    statusCode = 404;
                    jsonResponse = "{\"status\":\"error\",\"message\":\"SmartTube APK not found in package.\"}";
                }
                else
                {
                    var instRes = RunAdb("-s " + device + " install -r \"" + stPath + "\"", 120000);
                    if (instRes.ExitCode == 0 && instRes.Combined.IndexOf("Success", StringComparison.OrdinalIgnoreCase) >= 0)
                    {
                        RunAdb("-s " + device + " shell am start -n org.smarttube.beta/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity", 5000);
                        jsonResponse = string.Format("{{\"status\":\"ok\",\"installed\":true,\"launched\":true,\"output\":{0}}}",
                            ToJsonString(instRes.Combined));
                    }
                    else
                    {
                        statusCode = 500;
                        string err = string.IsNullOrEmpty(instRes.Combined) ? "SmartTube install returned non-zero exit code" : instRes.Combined;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"installed\":false,\"message\":{0}}}", ToJsonString(err));
                    }
                }
            }
            else if (endpoint == "/api/launch-smarttube")
            {
                RunAdb("-s " + device + " shell am start -n org.smarttube.beta/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity", 5000);
                jsonResponse = "{\"status\":\"ok\",\"message\":\"SmartTube launched successfully!\"}";
            }
            else if (endpoint == "/api/key")
            {
                string key = (ExtractJsonField(body, "key") ?? req.QueryString["key"] ?? "3").Trim();
                if (!Regex.IsMatch(key, @"^\d+$"))
                {
                    statusCode = 400;
                    jsonResponse = "{\"status\":\"error\",\"message\":\"Invalid keycode. Key must be numeric.\"}";
                }
                else
                {
                    var keyRes = RunAdb("-s " + device + " shell input keyevent " + key, 8000);
                    if (keyRes.ExitCode != 0)
                    {
                        statusCode = 500;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}", ToJsonString("Keyevent failed: " + keyRes.Combined));
                    }
                    else
                    {
                        jsonResponse = string.Format("{{\"status\":\"ok\",\"key\":{0},\"sent\":true}}", ToJsonString(key));
                    }
                }
            }
            else if (endpoint == "/api/wake")
            {
                RunAdb("-s " + device + " shell input keyevent 224", 6000);
                RunAdb("-s " + device + " shell input keyevent 3", 6000);
                RunAdb("-s " + device + " shell am start -n com.gathin.tvhome/.MainActivity", 6000);
                jsonResponse = "{\"status\":\"ok\",\"message\":\"Wakeup sequence and Home activity dispatched.\"}";
            }
            else if (endpoint == "/api/packages")
            {
                var sysRes = RunAdb("-s " + device + " shell pm list packages -s", 6000);
                var userRes = RunAdb("-s " + device + " shell pm list packages -3", 6000);
                var disRes = RunAdb("-s " + device + " shell pm list packages -d", 6000);
                var allRes = RunAdb("-s " + device + " shell pm list packages", 6000);

                var sysSet = ParsePackageList(sysRes.StdOut);
                var userSet = ParsePackageList(userRes.StdOut);
                var disSet = ParsePackageList(disRes.StdOut);
                var allList = ParsePackageList(allRes.StdOut);

                var unionSet = new HashSet<string>(allList, StringComparer.OrdinalIgnoreCase);
                foreach (var s in sysSet) unionSet.Add(s);
                foreach (var u in userSet) unionSet.Add(u);
                foreach (var d in disSet) unionSet.Add(d);

                var items = new List<string>();
                int sysCount = 0;
                int userCount = 0;
                int disCount = 0;
                int revCount = 0;

                foreach (var p in unionSet)
                {
                    bool isSys = sysSet.Contains(p) || (!userSet.Contains(p) && (p.StartsWith("android") || p.StartsWith("com.android.") || p.StartsWith("com.google.android.")));
                    bool isEnabled = !disSet.Contains(p);
                    bool isProt = ProtectedSystemPackages.Contains(p);

                    string cat = GetPackageCategory(p, isProt, isSys);
                    string rec = GetPackageRecommendation(p, isProt, isSys);
                    string reason = GetPackageReason(p, isProt, isSys);
                    string name = GetPackageName(p);
                    string risk = isProt ? "critical" : (rec == "safe_to_disable" ? "low" : (rec == "caution" ? "medium" : "low"));

                    if (isSys) sysCount++; else userCount++;
                    if (!isEnabled) disCount++;
                    if (rec == "safe_to_disable" || rec == "caution") revCount++;

                    items.Add(string.Format("{{\"pkg\":{0},\"name\":{1},\"isSystem\":{2},\"isEnabled\":{3},\"isProtected\":{4},\"category\":{5},\"recommendation\":{6},\"reason\":{7},\"riskLevel\":{8}}}",
                        ToJsonString(p), ToJsonString(name), isSys ? "true" : "false", isEnabled ? "true" : "false", isProt ? "true" : "false",
                        ToJsonString(cat), ToJsonString(rec), ToJsonString(reason), ToJsonString(risk)));
                }

                jsonResponse = string.Format("{{\"status\":\"ok\",\"packages\":[{0}],\"total\":{1},\"systemCount\":{2},\"userCount\":{3},\"disabledCount\":{4},\"reviewCount\":{5}}}",
                    string.Join(",", items.ToArray()), unionSet.Count, sysCount, userCount, disCount, revCount);
            }
            else if (endpoint == "/api/package-action")
            {
                string pkg = (ExtractJsonField(body, "pkg") ?? "").Trim();
                string action = (ExtractJsonField(body, "action") ?? "").Trim().ToLower();

                if (string.IsNullOrEmpty(pkg) || !Regex.IsMatch(pkg, @"^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$"))
                {
                    statusCode = 400;
                    jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}", ToJsonString("Invalid package identifier: " + pkg));
                }
                else if (ProtectedSystemPackages.Contains(pkg) && (action == "disable" || action == "uninstall" || action == "clear"))
                {
                    statusCode = 403;
                    jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}", ToJsonString("Action rejected: " + pkg + " is a protected Android TV system package."));
                }
                else if (action == "disable")
                {
                    var activeHomeInfo = GetActiveHomeLauncher(device);
                    string activeHome = activeHomeInfo != null ? activeHomeInfo.Package : null;
                    if (!string.IsNullOrEmpty(activeHome) && pkg.Equals(activeHome, StringComparison.OrdinalIgnoreCase) && !activeHome.Equals("com.gathin.tvhome", StringComparison.OrdinalIgnoreCase))
                    {
                        statusCode = 400;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}", ToJsonString("Cannot disable " + pkg + " as it is the currently active TV Home launcher."));
                    }
                    else
                    {
                        var disRes = RunAdb("-s " + device + " shell pm disable-user --user 0 " + pkg, 6000);
                        bool ok = disRes.Combined.IndexOf("disabled", StringComparison.OrdinalIgnoreCase) >= 0 || disRes.Combined.IndexOf("already", StringComparison.OrdinalIgnoreCase) >= 0;
                        if (!ok && disRes.ExitCode != 0)
                        {
                            statusCode = 500;
                            jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}", ToJsonString("Disable failed: " + disRes.Combined));
                        }
                        else
                        {
                            jsonResponse = string.Format("{{\"status\":\"ok\",\"pkg\":{0},\"action\":\"disable\",\"success\":true,\"output\":{1}}}", ToJsonString(pkg), ToJsonString(disRes.Combined));
                        }
                    }
                }
                else if (action == "enable")
                {
                    var enRes = RunAdb("-s " + device + " shell pm enable --user 0 " + pkg, 5000);
                    if (enRes.ExitCode != 0)
                    {
                        statusCode = 500;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}", ToJsonString("Enable failed: " + enRes.Combined));
                    }
                    else
                    {
                        jsonResponse = string.Format("{{\"status\":\"ok\",\"pkg\":{0},\"action\":\"enable\",\"success\":true,\"output\":{1}}}", ToJsonString(pkg), ToJsonString(enRes.Combined));
                    }
                }
                else if (action == "clear")
                {
                    string lk, lp, la;
                    if (!GetVerifiedLicense(out lk, out lp, out la))
                    {
                        statusCode = 403;
                        jsonResponse = "{\"status\":\"error\",\"code\":\"PRO_REQUIRED\",\"message\":\"Direct application data clearing requires CleanTV Pro. Please upgrade to unlock.\"}";
                    }
                    else
                    {
                        var clrRes = RunAdb("-s " + device + " shell pm clear " + pkg, 6000);
                        jsonResponse = string.Format("{{\"status\":\"ok\",\"pkg\":{0},\"action\":\"clear\",\"success\":true,\"output\":{1}}}", ToJsonString(pkg), ToJsonString(clrRes.Combined));
                    }
                }
                else if (action == "uninstall")
                {
                    var unRes = RunAdb("-s " + device + " shell pm uninstall -k --user 0 " + pkg, 8000);
                    jsonResponse = string.Format("{{\"status\":\"ok\",\"pkg\":{0},\"action\":\"uninstall\",\"success\":true,\"output\":{1}}}", ToJsonString(pkg), ToJsonString(unRes.Combined));
                }
                else
                {
                    statusCode = 400;
                    jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}", ToJsonString("Unsupported action: " + action));
                }
            }
            else if (endpoint == "/api/batch-action")
            {
                string lk, lp, la;
                if (!GetVerifiedLicense(out lk, out lp, out la))
                {
                    statusCode = 403;
                    jsonResponse = "{\"status\":\"error\",\"code\":\"PRO_REQUIRED\",\"message\":\"Batch application operations require CleanTV Pro. Please upgrade to unlock.\"}";
                    SendResponse(res, statusCode, jsonResponse);
                    return;
                }
                var rawPkgs = ExtractJsonStringArray(body, "packages");
                var pkgs = new List<string>();
                foreach (var rp in rawPkgs)
                {
                    if (!string.IsNullOrEmpty(rp) && Regex.IsMatch(rp.Trim(), @"^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$"))
                    {
                        pkgs.Add(rp.Trim());
                    }
                }
                string action = (ExtractJsonField(body, "action") ?? "disable").ToLower();

                if (pkgs.Count == 0)
                {
                    statusCode = 400;
                    jsonResponse = "{\"status\":\"error\",\"message\":\"No packages provided for batch action.\"}";
                }
                else
                {
                    if (action == "disable")
                    {
                        try { CreateBackup(device); } catch { }
                    }

                    var succ = new List<string>();
                    var skipped = new List<string>();
                    var failed = new List<string>();

                    foreach (var p in pkgs)
                    {
                        if (ProtectedSystemPackages.Contains(p))
                        {
                            skipped.Add(string.Format("{{\"pkg\":{0},\"reason\":\"Protected system package\"}}", ToJsonString(p)));
                            continue;
                        }
                        if (action == "disable")
                        {
                            var r = RunAdb("-s " + device + " shell pm disable-user --user 0 " + p, 5000);
                            if (r.Combined.IndexOf("disabled", StringComparison.OrdinalIgnoreCase) >= 0 || r.Combined.IndexOf("already", StringComparison.OrdinalIgnoreCase) >= 0)
                            {
                                succ.Add(ToJsonString(p));
                            }
                            else
                            {
                                failed.Add(string.Format("{{\"pkg\":{0},\"error\":{1}}}", ToJsonString(p), ToJsonString(r.Combined)));
                            }
                        }
                        else
                        {
                            var r = RunAdb("-s " + device + " shell pm enable --user 0 " + p, 4000);
                            if (r.ExitCode == 0) succ.Add(ToJsonString(p));
                            else failed.Add(string.Format("{{\"pkg\":{0},\"error\":{1}}}", ToJsonString(p), ToJsonString(r.Combined)));
                        }
                    }

                    jsonResponse = string.Format("{{\"status\":\"ok\",\"action\":{0},\"total\":{1},\"succeeded\":[{2}],\"skipped\":[{3}],\"failed\":[{4}]}}",
                        ToJsonString(action), pkgs.Count, string.Join(",", succ.ToArray()), string.Join(",", skipped.ToArray()), string.Join(",", failed.ToArray()));
                }
            }
            else if (endpoint == "/api/license/status")
            {
                string k, p, a;
                bool isPro = GetVerifiedLicense(out k, out p, out a);
                if (isPro)
                {
                    jsonResponse = string.Format("{{\"status\":\"ok\",\"tier\":\"pro\",\"isPro\":true,\"key\":{0},\"plan\":{1},\"activatedAt\":{2},\"verified\":true}}",
                        ToJsonString(k), ToJsonString(p), ToJsonString(a));
                }
                else
                {
                    jsonResponse = "{\"status\":\"ok\",\"tier\":\"free\",\"isPro\":false,\"key\":null,\"plan\":\"CleanTV Free\"}";
                }
            }
            else if (endpoint == "/api/license/config")
            {
                jsonResponse = "{\"status\":\"ok\",\"monthlyPriceInr\":\"₹99\",\"monthlyPriceUsd\":\"$5\",\"lifetimePriceInr\":\"₹200\",\"lifetimePriceUsd\":\"$7\",\"introductoryPrice\":\"₹99\",\"regularPrice\":\"₹200\",\"currency\":\"INR\",\"storeProductId\":\"CleanTV.Pro.Lifetime\",\"webStoreUrl\":\"https://cleantv.app/pro\",\"providers\":[\"Microsoft Store\",\"Razorpay\",\"Stripe\",\"Lemon Squeezy\",\"Gumroad\"]}";
            }
            else if (endpoint == "/api/license/activate")
            {
                string rawKey = ExtractJsonField(body, "licenseKey") ?? ExtractJsonField(body, "key") ?? req.QueryString["key"] ?? req.QueryString["licenseKey"];
                string validKey, validPlan, err;
                if (!VerifyLicenseKey(rawKey, out validKey, out validPlan, out err))
                {
                    statusCode = 400;
                    jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}", ToJsonString(err));
                }
                else
                {
                    string localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                    string licDir = Path.Combine(localApp, "CleanTV");
                    if (!Directory.Exists(licDir)) Directory.CreateDirectory(licDir);
                    string licFile = Path.Combine(licDir, "license.json");

                    string activatedAt = DateTime.UtcNow.ToString("o");
                    string plan = validPlan;
                    string sig = ComputeLicenseSignature("pro", validKey, plan, activatedAt);
                    string source = validKey.StartsWith("CTV-PRO-STOR") ? "store" : "web";

                    string licJson = string.Format("{{\"tier\":\"pro\",\"isPro\":true,\"key\":{0},\"plan\":{1},\"activatedAt\":{2},\"source\":{3},\"verified\":true,\"signature\":{4}}}",
                        ToJsonString(validKey), ToJsonString(plan), ToJsonString(activatedAt), ToJsonString(source), ToJsonString(sig));

                    File.WriteAllText(licFile, licJson);
                    jsonResponse = string.Format("{{\"status\":\"ok\",\"success\":true,\"tier\":\"pro\",\"isPro\":true,\"key\":{0},\"plan\":{1},\"message\":\"CleanTV Pro Lifetime activated successfully!\"}}",
                        ToJsonString(validKey), ToJsonString(plan));
                }
            }
            else if (endpoint == "/api/license/restore")
            {
                string k, p, a;
                bool isPro = GetVerifiedLicense(out k, out p, out a);
                if (isPro)
                {
                    jsonResponse = string.Format("{{\"status\":\"ok\",\"restored\":true,\"tier\":\"pro\",\"isPro\":true,\"key\":{0},\"plan\":{1},\"activatedAt\":{2}}}",
                        ToJsonString(k), ToJsonString(p), ToJsonString(a));
                }
                else
                {
                    jsonResponse = "{\"status\":\"ok\",\"restored\":false,\"tier\":\"free\",\"message\":\"No active CleanTV Pro license found for this device.\"}";
                }
            }
            else if (endpoint == "/api/adblock/status")
            {
                string lk, lp, la;
                bool isPro = GetVerifiedLicense(out lk, out lp, out la);
                string modeOut = (RunAdb("-s " + device + " shell settings get global private_dns_mode").StdOut ?? "").Trim().ToLower();
                string hostOut = (RunAdb("-s " + device + " shell settings get global private_dns_specifier").StdOut ?? "").Trim();
                bool enabled = (modeOut == "hostname" && hostOut.Length > 0);
                jsonResponse = string.Format("{{\"status\":\"ok\",\"enabled\":{0},\"mode\":{1},\"specifier\":{2},\"provider\":{3},\"isPro\":{4}}}",
                    enabled ? "true" : "false", ToJsonString(modeOut), ToJsonString(hostOut), ToJsonString(hostOut.Length > 0 ? hostOut : "None"), isPro ? "true" : "false");
            }
            else if (endpoint == "/api/adblock/toggle")
            {
                string lk, lp, la;
                if (!GetVerifiedLicense(out lk, out lp, out la))
                {
                    statusCode = 403;
                    jsonResponse = "{\"status\":\"error\",\"code\":\"PRO_REQUIRED\",\"message\":\"System-Wide Ad-Blocker & Telemetry Shield requires an active CleanTV Pro license. Please upgrade to unlock.\"}";
                    SendResponse(res, statusCode, jsonResponse);
                    return;
                }

                string action = ExtractJsonField(body, "action") ?? req.QueryString["action"] ?? req.QueryString["mode"];
                if (string.IsNullOrEmpty(action)) action = "enable";
                action = action.ToLower();

                string provider = ExtractJsonField(body, "provider") ?? req.QueryString["provider"] ?? req.QueryString["mode"];
                if (string.IsNullOrEmpty(provider)) provider = "adguard";
                provider = provider.ToLower();

                string hostname = "dns.adguard-dns.com";
                if (provider == "family") hostname = "family.adguard-dns.com";
                else if (provider == "controld") hostname = "p2.freedns.controld.com";
                else if (provider == "mullvad") hostname = "adblock.dns.mullvad.net";

                RunAdb("-s " + device + " shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS");

                if (action == "disable")
                {
                    RunAdb("-s " + device + " shell settings put global private_dns_mode off");
                    RunAdb("-s " + device + " shell settings delete global private_dns_specifier");
                    jsonResponse = "{\"status\":\"ok\",\"enabled\":false,\"mode\":\"off\",\"message\":\"System-wide ad blocker disabled.\"}";
                }
                else
                {
                    RunAdb("-s " + device + " shell settings put global private_dns_mode hostname");
                    RunAdb("-s " + device + " shell settings put global private_dns_specifier " + hostname);
                    jsonResponse = string.Format("{{\"status\":\"ok\",\"enabled\":true,\"mode\":\"hostname\",\"specifier\":{0},\"message\":\"System-wide ad blocker activated.\"}}", ToJsonString(hostname));
                }
            }
            else if (endpoint == "/api/ai/command")
            {
                string lk, lp, la;
                if (!GetVerifiedLicense(out lk, out lp, out la))
                {
                    statusCode = 403;
                    jsonResponse = "{\"status\":\"error\",\"code\":\"PRO_REQUIRED\",\"message\":\"CleanTV Voice & AI Neural Assistant requires an active CleanTV Pro license. Please upgrade to unlock.\"}";
                    SendResponse(res, statusCode, jsonResponse);
                    return;
                }
                string query = (ExtractJsonField(body, "query") ?? ExtractJsonField(body, "cmd") ?? req.QueryString["cmd"] ?? req.QueryString["query"] ?? "").Trim();
                if (string.IsNullOrEmpty(query))
                {
                    statusCode = 400;
                    jsonResponse = "{\"status\":\"error\",\"message\":\"Query cannot be empty.\"}";
                }
                else
                {
                    string qLower = query.ToLower();
                    string actionTaken = "Command dispatched to CleanTV AI Controller";

                    if (qLower == "volume up" || qLower == "louder")
                    {
                        var resAdb = RunAdb("-s " + device + " shell input keyevent 24");
                        actionTaken = "Triggered Volume Up (KEYCODE_VOLUME_UP)";
                    }
                    else if (qLower == "volume down" || qLower == "quieter")
                    {
                        var resAdb = RunAdb("-s " + device + " shell input keyevent 25");
                        actionTaken = "Triggered Volume Down (KEYCODE_VOLUME_DOWN)";
                    }
                    else if (qLower == "mute" || qLower == "unmute")
                    {
                        var resAdb = RunAdb("-s " + device + " shell input keyevent 164");
                        actionTaken = "Toggled Mute (KEYCODE_VOLUME_MUTE)";
                    }
                    else if (qLower == "open settings" || qLower == "settings")
                    {
                        var resAdb = RunAdb("-s " + device + " shell am start -a android.settings.SETTINGS");
                        actionTaken = "Opened System Settings";
                    }
                    else if (qLower == "open youtube" || qLower == "youtube")
                    {
                        var resAdb = RunAdb("-s " + device + " shell monkey -p com.google.android.youtube.tv 1");
                        actionTaken = "Launched YouTube for Android TV";
                    }
                    else if (qLower == "home" || qLower == "go home")
                    {
                        var resAdb = RunAdb("-s " + device + " shell input keyevent 3");
                        actionTaken = "Returned to CleanTV Home Screen";
                    }
                    else if (qLower == "back")
                    {
                        var resAdb = RunAdb("-s " + device + " shell input keyevent 4");
                        actionTaken = "Dispatched Back keyevent";
                    }
                    else
                    {
                        string safeQuery = Regex.Replace(query, @"[\r\n""`$\\]", " ");
                        RunAdb("-s " + device + " shell am start -n com.gathin.tvhome/.MainActivity --es query \"" + safeQuery + "\"");
                        actionTaken = "Dispatched intent query to TV: \"" + safeQuery + "\"";
                    }

                    jsonResponse = string.Format("{{\"status\":\"ok\",\"intent\":{0},\"action\":{1},\"message\":{2}}}",
                        ToJsonString(query), ToJsonString(query), ToJsonString(actionTaken));
                }
            }

            SendResponse(res, statusCode, jsonResponse);
        }

        private static void SendResponse(HttpListenerResponse res, int statusCode, string jsonResponse)
        {
            try
            {
                res.StatusCode = statusCode;
                byte[] jsonBytes = Encoding.UTF8.GetBytes(jsonResponse);
                res.ContentType = "application/json";
                res.ContentLength64 = jsonBytes.Length;
                res.OutputStream.Write(jsonBytes, 0, jsonBytes.Length);
                res.Close();
            }
            catch { }
        }

        private static HashSet<string> ParsePackageList(string output)
        {
            var set = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            if (string.IsNullOrEmpty(output)) return set;
            string[] lines = output.Split(new char[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            foreach (string line in lines)
            {
                string clean = line.Trim();
                if (clean.StartsWith("package:")) clean = clean.Substring(8).Trim();
                if (!string.IsNullOrEmpty(clean)) set.Add(clean);
            }
            return set;
        }

        private static List<string> ExtractJsonStringArray(string json, string key)
        {
            var list = new List<string>();
            if (string.IsNullOrEmpty(json)) return list;
            var match = Regex.Match(json, "\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]");
            if (match.Success)
            {
                var itemMatches = Regex.Matches(match.Groups[1].Value, "\"([^\"]+)\"");
                foreach (Match m in itemMatches)
                {
                    list.Add(m.Groups[1].Value);
                }
            }
            return list;
        }

        private static string GetPackageCategory(string pkg, bool isProtected, bool isSystem)
        {
            if (isProtected) return "system_core";
            string p = pkg.ToLower();
            if (p.Contains("samba") || p.Contains("analytics") || p.Contains("advertise") || p.Contains("audioparsing") || p.Contains("telemetry") || p.Contains("anoki.tv.service"))
                return "telemetry_ads";
            if (p.Contains("changhong") || p.Contains("homwee") || p.Contains("patchwall") || p.Contains("mitv.upgrade") || p.Contains("tcl.channel") || p.Contains("guard"))
                return "oem_bloat";
            if (p.Contains("youtube") || p.Contains("netflix") || p.Contains("videos"))
                return "media_streaming";
            return isSystem ? "system_service" : "user_installed";
        }

        private static string GetPackageRecommendation(string pkg, bool isProtected, bool isSystem)
        {
            if (isProtected) return "critical_system";
            string p = pkg.ToLower();
            if (p.Contains("anoki") || p.Contains("samba") || p.Contains("advertise") || p.Contains("analytics") ||
                p.Contains("audioparsing") || p.Contains("changhong.system.launcher") || p.Contains("mitv.upgrade") ||
                p.Contains("tcl.partnercustomizer") || p.Contains("feedback"))
                return "safe_to_disable";
            if (isSystem) return "caution";
            return "user_app";
        }

        private static string GetPackageReason(string pkg, bool isProtected, bool isSystem)
        {
            if (isProtected) return "Core Android operating system package. Disabling will destabilize or brick device.";
            string p = pkg.ToLower();
            if (p.Contains("anoki")) return "Vendor content syndication and background ad recommendation service.";
            if (p.Contains("samba")) return "Automatic content recognition (ACR) telemetry tracking TV viewing habits.";
            if (p.Contains("advertise")) return "Startup ad banners and recommendation injection daemon.";
            if (p.Contains("analytics")) return "User viewing habits telemetry and background data reporter.";
            if (p.Contains("changhong.system.launcher")) return "Vendor OEM launcher with sponsored banners. Safe to disable when CleanTV Home is active.";
            if (p.Contains("audioparsing")) return "Vendor audio diagnostic listening telemetry.";
            if (p.Contains("partnercustomizer")) return "Vendor background partner app auto-installer.";
            if (p.Contains("youtube")) return "Official TV video streaming client.";
            if (isSystem) return "System component. Review carefully before disabling.";
            return "User-installed application. Can be safely managed.";
        }

        private static string GetPackageName(string pkg)
        {
            string p = pkg.ToLower();
            if (p == "com.changhong.system.launcher.v2") return "Changhong Stock Launcher";
            if (p == "com.anoki.tv.realme") return "Realme Content Hub";
            if (p == "com.anoki.tv.service") return "Anoki Background Service";
            if (p == "com.aipont.tv.assistant") return "AI PONT Voice Assistant";
            if (p == "com.anoki.tv.homwee") return "Homwee TV Store & Launcher";
            if (p == "com.mitv.tvhome") return "PatchWall TV Home";
            if (p == "com.xiaomi.mitv.advertise") return "MiTV Ad Service";
            if (p == "com.xiaomi.mitv.upgrade") return "MiTV Silent Updater";
            if (p == "com.miui.tv.analytics") return "MIUI TV Analytics";
            if (p == "com.samba.tv") return "Samba TV Telemetry";
            if (p == "com.google.android.youtube.tv") return "YouTube for Android TV";
            if (p == "com.google.android.tvlauncher") return "Android TV Home Launcher";
            if (p == "com.gathin.tvhome") return "CleanTV Home Launcher";
            if (p == "android") return "Android Core System";
            if (p == "com.android.systemui") return "Android System UI";
            if (p == "com.android.settings") return "Android Settings";
            if (p == "com.android.packageinstaller") return "Package Installer";

            string[] parts = pkg.Split('.');
            string last = parts[parts.Length - 1];
            if (last.Length > 0)
                return char.ToUpper(last[0]) + last.Substring(1);
            return pkg;
        }

        private const string LicenseSigningSecret = "CLEANTV_LOCAL_SIGNING_SALT_9F82A4";
        private const string LicenseKeySecret = "CLEANTV_KEY_SALT_4B18D";

        private static string ComputeLicenseSignature(string tier, string key, string plan, string activatedAt)
        {
            string data = tier + "|" + (key ?? "") + "|" + (plan ?? "") + "|" + (activatedAt ?? "");
            using (var hmac = new System.Security.Cryptography.HMACSHA256(Encoding.UTF8.GetBytes(LicenseSigningSecret)))
            {
                byte[] hash = hmac.ComputeHash(Encoding.UTF8.GetBytes(data));
                var sb = new StringBuilder();
                for (int i = 0; i < hash.Length; i++) sb.Append(hash[i].ToString("x2"));
                return sb.ToString();
            }
        }

        private static bool VerifyLicenseKey(string rawKey, out string key, out string plan, out string error)
        {
            key = null;
            plan = "CleanTV Pro Lifetime";
            error = null;
            if (string.IsNullOrEmpty(rawKey))
            {
                error = "License key cannot be empty.";
                return false;
            }
            key = rawKey.Trim().ToUpper();
            var m = Regex.Match(key, @"^CTV-(PRO|MON)-([0-9A-Z]{4})-([0-9A-Z]{4})$");
            if (!m.Success)
            {
                error = "Invalid format. Key must follow CTV-PRO-XXXX-XXXX or CTV-MON-XXXX-XXXX.";
                return false;
            }
            string prefix = m.Groups[1].Value;
            string part1 = m.Groups[2].Value;
            string part2 = m.Groups[3].Value;
            plan = prefix == "MON" ? "CleanTV Pro Monthly" : "CleanTV Pro Lifetime";

            if (key.Contains("REVOK") || key == "CTV-PRO-REV1-9999" || key == "CTV-MON-REV1-9999")
            {
                error = "This CleanTV license key has been revoked.";
                return false;
            }


            using (var sha = System.Security.Cryptography.SHA256.Create())
            {
                byte[] hash = sha.ComputeHash(Encoding.UTF8.GetBytes(LicenseKeySecret + part1));
                var sb = new StringBuilder();
                for (int i = 0; i < 2; i++) sb.Append(hash[i].ToString("X2"));
                string expectedSum = sb.ToString();
                if (part2 != expectedSum)
                {
                    error = "Invalid license key checksum. Please verify the code from your purchase receipt.";
                    return false;
                }
            }
            return true;
        }

        private static bool GetVerifiedLicense(out string key, out string plan, out string activatedAt)
        {
            key = null;
            plan = "CleanTV Free";
            activatedAt = null;

            string localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string licFile = Path.Combine(localApp, "CleanTV", "license.json");
            if (!File.Exists(licFile)) return false;

            try
            {
                string json = File.ReadAllText(licFile);
                string tier = ExtractJsonField(json, "tier");
                if (tier != "pro") return false;

                key = ExtractJsonField(json, "key");
                plan = ExtractJsonField(json, "plan") ?? "CleanTV Pro Lifetime";
                activatedAt = ExtractJsonField(json, "activatedAt");
                string sig = ExtractJsonField(json, "signature");

                string expectedSig = ComputeLicenseSignature("pro", key, plan, activatedAt);
                if (string.IsNullOrEmpty(sig) || sig != expectedSig)
                {
                    return false;
                }
                return true;
            }
            catch
            {
                return false;
            }
        }

        private static string ResolvePresetFileName(string preset)
        {
            switch (preset)
            {
                case "realme": return "realme-changhong.txt";
                case "sony": return "sony-bravia.txt";
                case "xiaomi": return "xiaomi-mitv.txt";
                case "tcl": return "tcl-tv.txt";
                case "google": return "google-tv.txt";
                case "hisense": return "hisense.txt";
                default: return "generic-android-tv.txt";
            }
        }

        private static string FindPresetFile(string fileName)
        {
            string p1 = Path.Combine(presetsDir, fileName);
            if (File.Exists(p1)) return p1;

            string p2 = Path.Combine(appDir, "presets", fileName);
            if (File.Exists(p2)) return p2;

            string p3 = Path.Combine(appDir, "..", "presets", fileName);
            if (File.Exists(p3)) return Path.GetFullPath(p3);

            return p1;
        }

        private class DiscoveredDevice
        {
            public string Serial;
            public string State;
            public string Name;
            public string Type;
            public string Ip;
            public string Port;
            public string Info;
        }

        private static List<DiscoveredDevice> DiscoverNetworkDevices()
        {
            var list = new List<DiscoveredDevice>();
            var seenSerials = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            // 1. Attached ADB devices
            var devResult = RunAdb("devices -l", 4000);
            string[] lines = (devResult.StdOut ?? "").Split(new char[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            for (int i = 1; i < lines.Length; i++)
            {
                string line = lines[i].Trim();
                if (string.IsNullOrEmpty(line)) continue;
                var match = Regex.Match(line, @"^(\S+)\s+(device|offline|unauthorized)\s*(.*)");
                if (match.Success)
                {
                    string ser = match.Groups[1].Value;
                    string st = match.Groups[2].Value;
                    string inf = match.Groups[3].Value;

                    string name = "Android Device";
                    string type = "usb";
                    if (ser.StartsWith("emulator-", StringComparison.OrdinalIgnoreCase))
                    {
                        name = "Android TV Virtual Device";
                        type = "emulator";
                    }
                    else if (ser.Contains(":"))
                    {
                        type = "tv";
                        name = ser.StartsWith("192.168.0.159") ? "Realme Smart TV 4K" : "Android TV (" + ser.Split(':')[0] + ")";
                    }
                    else if (inf.Contains("model:"))
                    {
                        var mMatch = Regex.Match(inf, @"model:(\S+)");
                        if (mMatch.Success) name = mMatch.Groups[1].Value.Replace("_", " ");
                    }

                    list.Add(new DiscoveredDevice
                    {
                        Serial = ser,
                        State = st.Equals("device", StringComparison.OrdinalIgnoreCase) ? "online" : st,
                        Name = name,
                        Type = type,
                        Ip = ser.Contains(":") ? ser.Split(':')[0] : "127.0.0.1",
                        Port = ser.Contains(":") ? ser.Split(':')[1] : "5555",
                        Info = inf
                    });
                    seenSerials.Add(ser);
                    if (ser.Contains(":")) seenSerials.Add(ser.Split(':')[0]);
                }
            }

            // 2. Discover devices on local network using ARP & socket probe
            try
            {
                var candidateIps = new List<string>();

                // Check ARP cache
                var arpProc = new Process();
                arpProc.StartInfo.FileName = "arp";
                arpProc.StartInfo.Arguments = "-a";
                arpProc.StartInfo.UseShellExecute = false;
                arpProc.StartInfo.RedirectStandardOutput = true;
                arpProc.StartInfo.CreateNoWindow = true;
                arpProc.Start();
                string arpOut = arpProc.StandardOutput.ReadToEnd();
                arpProc.WaitForExit(2000);

                var arpMatches = Regex.Matches(arpOut, @"\b(192\.168\.\d+\.\d+)\b");
                foreach (Match m in arpMatches)
                {
                    string candidate = m.Groups[1].Value;
                    if (!candidate.EndsWith(".1") && !candidate.EndsWith(".255") && !candidateIps.Contains(candidate) && !seenSerials.Contains(candidate))
                    {
                        candidateIps.Add(candidate);
                    }
                }

                // Parallel socket probe on port 5555
                var threads = new List<Thread>();
                var lockObj = new object();
                foreach (string ip in candidateIps)
                {
                    string targetIp = ip;
                    var th = new Thread(() =>
                    {
                        try
                        {
                            using (var client = new TcpClient())
                            {
                                var ar = client.BeginConnect(targetIp, 5555, null, null);
                                bool success = ar.AsyncWaitHandle.WaitOne(350, false);
                                if (success && client.Connected)
                                {
                                    client.EndConnect(ar);
                                    lock (lockObj)
                                    {
                                        string name = targetIp == "192.168.0.159" ? "Realme Smart TV 4K" : "Android TV (" + targetIp + ")";
                                        list.Add(new DiscoveredDevice
                                        {
                                            Serial = targetIp + ":5555",
                                            State = "ready",
                                            Name = name,
                                            Type = "tv",
                                            Ip = targetIp,
                                            Port = "5555",
                                            Info = "Wi-Fi LAN"
                                        });
                                    }
                                }
                            }
                        }
                        catch { }
                    });
                    th.IsBackground = true;
                    th.Start();
                    threads.Add(th);
                }
                foreach (var th in threads) th.Join(450);
            }
            catch { }

            return list;
        }

        private static string FindLauncherApk()
        {
            string[] candidates = new string[] {
                Path.Combine(appDir, "apks", "clean-tv-launcher-latest.apk"),
                Path.Combine(appDir, "..", "apks", "clean-tv-launcher-latest.apk"),
                Path.Combine(appDir, "..", "baron-inspired-tv-home-codebase", "launcher", "app", "build", "outputs", "apk", "debug", "app-debug.apk")
            };

            foreach (var c in candidates)
            {
                if (File.Exists(c) && new FileInfo(c).Length > 100000) return Path.GetFullPath(c);
            }
            return null;
        }

        private static string CreateBackup(string device)
        {
            string baseDir = GetBackupsDirectory();
            string deviceKey = Regex.Replace(device, @"[^a-zA-Z0-9_-]", "_");
            string ts = DateTime.Now.ToString("yyyyMMdd-HHmmss");
            string dir = Path.Combine(baseDir, "win-backup-" + deviceKey + "-" + ts);

            Directory.CreateDirectory(dir);
            try
            {
                var pDisabled = RunAdb("-s " + device + " shell pm list packages -d", 8000);
                var pAll = RunAdb("-s " + device + " shell pm list packages", 8000);
                var getprop = RunAdb("-s " + device + " shell getprop", 8000);

                // 1. Strict ADB execution & command output verification
                if (pAll.ExitCode != 0 || string.IsNullOrEmpty(pAll.StdOut) || !pAll.StdOut.Contains("package:"))
                {
                    throw new InvalidOperationException("Failed to query installed packages via ADB (ExitCode: " + pAll.ExitCode + ", StdErr: " + (pAll.StdErr ?? "").Trim() + ")");
                }
                if (getprop.ExitCode != 0 || string.IsNullOrEmpty(getprop.StdOut) || getprop.StdOut.Length < 20)
                {
                    throw new InvalidOperationException("Failed to capture system properties via ADB (ExitCode: " + getprop.ExitCode + ", StdErr: " + (getprop.StdErr ?? "").Trim() + ")");
                }
                if (pDisabled.ExitCode != 0)
                {
                    throw new InvalidOperationException("Failed to query disabled packages via ADB (ExitCode: " + pDisabled.ExitCode + ", StdErr: " + (pDisabled.StdErr ?? "").Trim() + ")");
                }

                string fileDisabled = Path.Combine(dir, "packages-disabled.txt");
                string fileAll = Path.Combine(dir, "packages-all.txt");
                string fileProp = Path.Combine(dir, "getprop.txt");

                File.WriteAllText(fileDisabled, pDisabled.StdOut ?? "");
                File.WriteAllText(fileAll, pAll.StdOut ?? "");
                File.WriteAllText(fileProp, getprop.StdOut ?? "");

                // 2. Strict file existence and non-zero size verification on disk
                if (!File.Exists(fileDisabled) || !File.Exists(fileAll) || !File.Exists(fileProp))
                {
                    throw new IOException("Safety backup files were not created on disk.");
                }
                var fiAll = new FileInfo(fileAll);
                var fiProp = new FileInfo(fileProp);
                if (fiAll.Length == 0 || fiProp.Length == 0)
                {
                    throw new IOException("Safety backup files on disk are unexpectedly empty. Aborting debloat.");
                }

                return dir;
            }
            catch
            {
                try { if (Directory.Exists(dir)) Directory.Delete(dir, true); } catch { }
                throw;
            }
        }

        private static string FindLatestBackupDir(string device)
        {
            string baseDir = GetBackupsDirectory();
            if (!Directory.Exists(baseDir)) return null;

            string deviceKey = Regex.Replace(device, @"[^a-zA-Z0-9_-]", "_");
            var dInfo = new DirectoryInfo(baseDir);
            var dirs = dInfo.GetDirectories("win-backup-" + deviceKey + "-*");
            if (dirs.Length == 0) return null;

            Array.Sort(dirs, (a, b) => b.CreationTimeUtc.CompareTo(a.CreationTimeUtc));
            return dirs[0].FullName;
        }

        private static string GetMimeType(string ext)
        {
            switch (ext)
            {
                case ".html": return "text/html; charset=utf-8";
                case ".css": return "text/css; charset=utf-8";
                case ".js": return "application/javascript; charset=utf-8";
                case ".json": return "application/json; charset=utf-8";
                case ".png": return "image/png";
                case ".jpg":
                case ".jpeg": return "image/jpeg";
                case ".svg": return "image/svg+xml";
                case ".ico": return "image/x-icon";
                default: return "application/octet-stream";
            }
        }

        private static string ExtractJsonField(string json, string key)
        {
            if (string.IsNullOrEmpty(json)) return null;
            string pattern = "\"" + key + "\":\"";
            int idx = json.IndexOf(pattern);
            if (idx == -1)
            {
                pattern = "\"" + key + "\" : \"";
                idx = json.IndexOf(pattern);
            }
            if (idx == -1) return null;
            int start = idx + pattern.Length;
            int end = json.IndexOf("\"", start);
            if (end == -1) return null;
            return json.Substring(start, end - start);
        }

        private static string ToJsonString(string s)
        {
            if (s == null) return "\"\"";
            return "\"" + s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\r", "").Replace("\n", "\\n") + "\"";
        }

        private static void SetupTray()
        {
            trayIcon = new NotifyIcon();
            trayIcon.Text = "CleanTV Home Desktop Manager";

            try
            {
                string iconPath = Path.Combine(appDir, "assets", "StoreLogo.png");
                if (File.Exists(iconPath))
                {
                    using (var bmp = new Bitmap(iconPath))
                    {
                        trayIcon.Icon = Icon.FromHandle(bmp.GetHicon());
                    }
                }
                else
                {
                    trayIcon.Icon = SystemIcons.Application;
                }
            }
            catch
            {
                trayIcon.Icon = SystemIcons.Application;
            }

            var menu = new ContextMenuStrip();
            menu.Items.Add("Open CleanTV Manager", null, (s, e) => LaunchAppWindow());
            menu.Items.Add("-");
            menu.Items.Add("Connect Local Device (127.0.0.1:5555)", null, (s, e) => {
                ThreadPool.QueueUserWorkItem(_ => RunAdb("connect 127.0.0.1:5555"));
            });
            menu.Items.Add("-");
            menu.Items.Add("Exit", null, (s, e) => {
                trayIcon.Visible = false;
                try { if (listener != null) listener.Stop(); } catch {}
                Application.Exit();
            });

            trayIcon.ContextMenuStrip = menu;
            trayIcon.DoubleClick += (s, e) => LaunchAppWindow();
            trayIcon.Visible = true;
        }

        public static void LaunchAppWindow()
        {
            string url = "http://127.0.0.1:" + PORT + "/index.html";

            string[] possibleEdgePaths = new string[]
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Microsoft", "Edge", "Application", "msedge.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Microsoft", "Edge", "Application", "msedge.exe")
            };

            foreach (var edgePath in possibleEdgePaths)
            {
                if (File.Exists(edgePath))
                {
                    try
                    {
                        if (edgeProcess != null && !edgeProcess.HasExited)
                        {
                            return;
                        }
                        edgeProcess = Process.Start(new ProcessStartInfo
                        {
                            FileName = edgePath,
                            Arguments = "--app=\"" + url + "\" --window-size=1120,780 --user-data-dir=\"" + Path.Combine(Path.GetTempPath(), "CleanTV_EdgeProfile") + "\"",
                            UseShellExecute = true
                        });
                        return;
                    }
                    catch { }
                }
            }

            try
            {
                Process.Start(url);
            }
            catch (Exception ex)
            {
                MessageBox.Show("Could not open browser for CleanTV UI: " + ex.Message);
            }
        }
    }
}
