using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
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
                if (urlPath == "/" || string.IsNullOrEmpty(urlPath))
                {
                    urlPath = "/index.html";
                }

                string filePath = Path.Combine(uiDir, urlPath.TrimStart('/').Replace('/', '\\'));

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
            if (string.IsNullOrEmpty(dev)) return "192.168.0.159:5555";
            dev = dev.Trim();
            if (dev.Contains(":")) return dev;
            // Only append port if it's an IP address or hostname with dots / localhost
            if (dev.Contains(".") || dev.Equals("localhost", StringComparison.OrdinalIgnoreCase))
            {
                return dev + ":5555";
            }
            // USB serial (e.g. "0123456789ABCDEF" or "emulator-5554")
            return dev;
        }

        private static string GetActiveHomePackage(string device)
        {
            var homeRes = RunAdb("-s " + device + " shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME", 5000);
            if (homeRes.ExitCode == 0)
            {
                string combined = homeRes.Combined;
                var match = Regex.Match(combined, @"\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/");
                if (match.Success) return match.Groups[1].Value.Trim();
            }

            // Fallback for older Android TV OS versions
            var dumpsysRes = RunAdb("-s " + device + " shell dumpsys activity activities", 5000);
            if (dumpsysRes.ExitCode == 0)
            {
                var match = Regex.Match(dumpsysRes.StdOut ?? "", @"mResumedActivity: ActivityRecord\{[^\}]+ ([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/");
                if (match.Success) return match.Groups[1].Value.Trim();
            }

            return null;
        }

        private static void HandleApi(string endpoint, HttpListenerRequest req, HttpListenerResponse res)
        {
            // Security: Enforce session token validation on all API calls
            string clientToken = req.Headers["X-CleanTV-Token"];
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

            string rawDevice = ExtractJsonField(body, "device");
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

                if (isConnectedOutput && isDevice)
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
                var devResult = RunAdb("devices -l", 5000);
                var deviceList = new List<string>();
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
                        deviceList.Add(string.Format("{{\"serial\":{0},\"state\":{1},\"info\":{2}}}",
                            ToJsonString(ser), ToJsonString(st), ToJsonString(inf)));
                    }
                }
                jsonResponse = string.Format("{{\"status\":\"ok\",\"devices\":[{0}]}}", string.Join(",", deviceList.ToArray()));
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
                string preset = (ExtractJsonField(body, "preset") ?? "realme").ToLower();
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
                string preset = (ExtractJsonField(body, "preset") ?? "realme").ToLower();
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
                    string activeHome = GetActiveHomePackage(device);
                    if (string.IsNullOrEmpty(activeHome))
                    {
                        statusCode = 500;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"message\":{0}}}",
                            ToJsonString("Could not identify the TV's active HOME launcher via ADB. Debloating aborted to prevent disabling the TV's only working launcher. Please ensure the TV screen is unlocked and responds to ADB."));
                        SendResponse(res, statusCode, jsonResponse);
                        return;
                    }

                    bool isCleanTvActive = activeHome.Equals("com.gathin.tvhome", StringComparison.OrdinalIgnoreCase);

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

                // Diff: only re-enable packages currently disabled that were NOT disabled in baseline snapshot
                foreach (var p in curDisabled)
                {
                    if (!preDisabled.Contains(p))
                    {
                        var enRes = RunAdb("-s " + device + " shell pm enable --user 0 " + p, 5000);
                        if (enRes.ExitCode == 0)
                        {
                            restoredCount++;
                            restoredPkgs.Add(ToJsonString(p));
                        }
                    }
                }

                // Re-enable stock launchers if present on this device
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
                        RunAdb("-s " + device + " shell cmd package set-home-activity --user 0 " + sl + "/.MainActivity", 4000);
                    }
                }

                RunAdb("-s " + device + " shell input keyevent 3", 3000); // Home

                jsonResponse = string.Format("{{\"status\":\"ok\",\"restored\":{0},\"backupDir\":{1},\"packages\":[{2}]}}",
                    restoredCount, ToJsonString(targetBackupDir), string.Join(",", restoredPkgs.ToArray()));
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
            else if (endpoint == "/api/key")
            {
                string key = ExtractJsonField(body, "key") ?? "3";
                if (!Regex.IsMatch(key, @"^\d+$"))
                {
                    statusCode = 400;
                    jsonResponse = "{\"status\":\"error\",\"message\":\"Invalid keycode. Key must be numeric.\"}";
                }
                else
                {
                    var keyRes = RunAdb("-s " + device + " shell input keyevent " + key, 4000);
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
            else if (endpoint == "/api/ai/command")
            {
                string query = (ExtractJsonField(body, "query") ?? "").Trim();
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
                        RunAdb("-s " + device + " shell am start -n com.gathin.tvhome/.MainActivity --es query \"" + query.Replace("\"", "\\\"") + "\"");
                        actionTaken = "Dispatched intent query to TV: \"" + query + "\"";
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
            menu.Items.Add("Connect Default TV (192.168.0.159)", null, (s, e) => {
                ThreadPool.QueueUserWorkItem(_ => RunAdb("connect 192.168.0.159:5555"));
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
