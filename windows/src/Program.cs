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
        private static string backupsDir;
        private static Process edgeProcess = null;
        private static string cachedAdbPath = null;

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            appDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');

            // Resolve UI directory
            uiDir = Path.Combine(appDir, "ui");
            if (!Directory.Exists(uiDir))
            {
                string srcUi = Path.Combine(appDir, "src", "ui");
                if (Directory.Exists(srcUi)) uiDir = srcUi;
                else uiDir = appDir;
            }

            // Resolve Presets directory
            presetsDir = Path.Combine(appDir, "presets");
            if (!Directory.Exists(presetsDir))
            {
                string parentPresets = Path.Combine(appDir, "..", "presets");
                if (Directory.Exists(parentPresets)) presetsDir = Path.GetFullPath(parentPresets);
            }

            // Resolve Backups directory
            backupsDir = Path.Combine(appDir, "backups");
            if (!Directory.Exists(backupsDir))
            {
                string parentBackups = Path.Combine(appDir, "..", "backups");
                if (Directory.Exists(parentBackups)) backupsDir = Path.GetFullPath(parentBackups);
            }

            // Locate ADB on startup
            cachedAdbPath = ResolveAdb();

            // Start Embedded Web & API Server
            StartHttpServer();

            // Setup System Tray Icon
            SetupTray();

            // Launch App Window
            LaunchAppWindow();

            Application.Run();
        }

        public static string ResolveAdb()
        {
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
                MessageBox.Show("Could not start local server on port " + PORT + ":\n" + ex.Message,
                    "CleanTV Server Notice", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private static void HandleRequest(HttpListenerContext context)
        {
            try
            {
                var req = context.Request;
                var res = context.Response;

                res.AddHeader("Access-Control-Allow-Origin", "*");
                res.AddHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                res.AddHeader("Access-Control-Allow-Headers", "Content-Type");

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
                    byte[] fileBytes = File.ReadAllBytes(filePath);
                    res.ContentLength64 = fileBytes.Length;
                    res.OutputStream.Write(fileBytes, 0, fileBytes.Length);
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

        private static void HandleApi(string endpoint, HttpListenerRequest req, HttpListenerResponse res)
        {
            string body = "";
            using (var reader = new StreamReader(req.InputStream, req.ContentEncoding))
            {
                body = reader.ReadToEnd();
            }

            string device = ExtractJsonField(body, "device") ?? "192.168.0.159:5555";
            if (!device.Contains(":")) device += ":5555";

            string jsonResponse = "{\"status\":\"ok\"}";
            int statusCode = 200;

            if (endpoint == "/api/adb-status")
            {
                cachedAdbPath = ResolveAdb();
                bool found = !string.IsNullOrEmpty(cachedAdbPath);
                bool bundled = found && cachedAdbPath.IndexOf("platform-tools", StringComparison.OrdinalIgnoreCase) >= 0;
                jsonResponse = string.Format("{{\"status\":\"ok\",\"adbFound\":{0},\"adbPath\":{1},\"bundled\":{2}}}",
                    found ? "true" : "false",
                    ToJsonString(cachedAdbPath),
                    bundled ? "true" : "false");
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
                var memTotalMatch = Regex.Match(meminfo, @"MemTotal:\s+(\d+)");
                var memAvailMatch = Regex.Match(meminfo, @"MemAvailable:\s+(\d+)");
                if (memTotalMatch.Success && memAvailMatch.Success)
                {
                    long totalKb = long.Parse(memTotalMatch.Groups[1].Value);
                    long availKb = long.Parse(memAvailMatch.Groups[1].Value);
                    double totalGb = Math.Round(totalKb / 1048576.0, 1);
                    double availGb = Math.Round(availKb / 1048576.0, 1);
                    memStr = availGb + " GB Free / " + totalGb + " GB Total";
                }

                // Launcher info
                string homeRes = RunAdb("-s " + device + " shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").Combined;
                string launcherState = homeRes.Contains("com.gathin.tvhome") ? "CleanTV Home v4.0 Active" : "Stock Launcher Active";

                jsonResponse = string.Format("{{\"status\":\"ok\",\"device\":{0},\"brand\":{1},\"model\":{2},\"androidVersion\":{3},\"manufacturer\":{4},\"memory\":{5},\"launcherState\":{6}}}",
                    ToJsonString(device), ToJsonString(brand), ToJsonString(model), ToJsonString(android), ToJsonString(manufacturer), ToJsonString(memStr), ToJsonString(launcherState));
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

                    // Create real safety backup before modifying
                    string backupDir = CreateBackup(device);

                    int disabledCount = 0;
                    int skippedCount = 0;
                    int failedCount = 0;
                    var resultsList = new List<string>();

                    foreach (var pkg in packagesToDisable)
                    {
                        // Check if installed
                        var hasPkg = RunAdb("-s " + device + " shell pm list packages " + pkg, 4000);
                        if (!hasPkg.Combined.Contains(pkg))
                        {
                            skippedCount++;
                            resultsList.Add(string.Format("{{\"pkg\":{0},\"result\":\"not_installed\"}}", ToJsonString(pkg)));
                            continue;
                        }

                        // Disable package
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
                // Backup-driven restore: read pre-install packages-disabled.txt from latest backup
                string targetBackupDir = FindLatestBackupDir();
                int restoredCount = 0;
                var restoredPkgs = new List<string>();

                if (!string.IsNullOrEmpty(targetBackupDir) && File.Exists(Path.Combine(targetBackupDir, "packages-disabled.txt")))
                {
                    var preDisabled = new HashSet<string>();
                    foreach (var l in File.ReadAllLines(Path.Combine(targetBackupDir, "packages-disabled.txt")))
                    {
                        string p = l.Replace("package:", "").Trim();
                        if (!string.IsNullOrEmpty(p)) preDisabled.Add(p);
                    }

                    // Get currently disabled packages from TV
                    var curDisabledRes = RunAdb("-s " + device + " shell pm list packages -d", 8000);
                    var curDisabled = new List<string>();
                    foreach (var l in curDisabledRes.StdOut.Split(new char[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries))
                    {
                        string p = l.Replace("package:", "").Trim();
                        if (!string.IsNullOrEmpty(p)) curDisabled.Add(p);
                    }

                    // Diff: re-enable packages currently disabled that were NOT disabled before
                    foreach (var p in curDisabled)
                    {
                        if (!preDisabled.Contains(p))
                        {
                            var enRes = RunAdb("-s " + device + " shell pm enable --user 0 " + p, 5000);
                            restoredCount++;
                            restoredPkgs.Add(ToJsonString(p));
                        }
                    }
                }
                else
                {
                    // Fallback to presets list
                    string[] presetFiles = Directory.Exists(presetsDir) ? Directory.GetFiles(presetsDir, "*.txt") : new string[0];
                    var allPresetPkgs = new HashSet<string>();
                    foreach (var pf in presetFiles)
                    {
                        foreach (var l in File.ReadAllLines(pf))
                        {
                            string p = l.Split('#')[0].Trim();
                            if (!string.IsNullOrEmpty(p)) allPresetPkgs.Add(p);
                        }
                    }

                    foreach (var p in allPresetPkgs)
                    {
                        var check = RunAdb("-s " + device + " shell pm list packages -d " + p, 3000);
                        if (check.Combined.Contains(p))
                        {
                            RunAdb("-s " + device + " shell pm enable --user 0 " + p, 5000);
                            restoredCount++;
                            restoredPkgs.Add(ToJsonString(p));
                        }
                    }
                }

                // Re-enable stock launchers if present
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

                jsonResponse = string.Format("{{\"status\":\"ok\",\"restored\":{0},\"packages\":[{1}]}}",
                    restoredCount, string.Join(",", restoredPkgs.ToArray()));
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
                    if (instRes.Combined.IndexOf("Success", StringComparison.OrdinalIgnoreCase) >= 0)
                    {
                        RunAdb("-s " + device + " shell am start -n com.gathin.tvhome/.MainActivity", 5000);
                        jsonResponse = string.Format("{{\"status\":\"ok\",\"installed\":true,\"launched\":true,\"output\":{0}}}",
                            ToJsonString(instRes.Combined));
                    }
                    else
                    {
                        statusCode = 500;
                        string err = string.IsNullOrEmpty(instRes.Combined) ? "Install command returned non-zero code" : instRes.Combined;
                        jsonResponse = string.Format("{{\"status\":\"error\",\"installed\":false,\"message\":{0}}}", ToJsonString(err));
                    }
                }
            }
            else if (endpoint == "/api/key")
            {
                string key = ExtractJsonField(body, "key") ?? "3";
                var keyRes = RunAdb("-s " + device + " shell input keyevent " + key, 4000);
                jsonResponse = string.Format("{{\"status\":\"ok\",\"key\":{0},\"sent\":true}}", ToJsonString(key));
            }
            else if (endpoint == "/api/ai/command")
            {
                string query = (ExtractJsonField(body, "query") ?? "").Trim();
                string qLower = query.ToLower();
                string actionTaken = "Command dispatched to CleanTV AI Controller";

                if (qLower == "volume up" || qLower == "louder")
                {
                    RunAdb("-s " + device + " shell input keyevent 24");
                    actionTaken = "Triggered Volume Up (KEYCODE_VOLUME_UP)";
                }
                else if (qLower == "volume down" || qLower == "quieter")
                {
                    RunAdb("-s " + device + " shell input keyevent 25");
                    actionTaken = "Triggered Volume Down (KEYCODE_VOLUME_DOWN)";
                }
                else if (qLower == "mute" || qLower == "unmute")
                {
                    RunAdb("-s " + device + " shell input keyevent 164");
                    actionTaken = "Toggled Mute (KEYCODE_VOLUME_MUTE)";
                }
                else if (qLower == "open settings" || qLower == "settings")
                {
                    RunAdb("-s " + device + " shell am start -a android.settings.SETTINGS");
                    actionTaken = "Opened System Settings";
                }
                else if (qLower == "open youtube" || qLower == "youtube")
                {
                    RunAdb("-s " + device + " shell monkey -p com.google.android.youtube.tv 1");
                    actionTaken = "Launched YouTube for Android TV";
                }
                else if (qLower == "home" || qLower == "go home")
                {
                    RunAdb("-s " + device + " shell input keyevent 3");
                    actionTaken = "Returned to CleanTV Home Screen";
                }
                else if (qLower == "back")
                {
                    RunAdb("-s " + device + " shell input keyevent 4");
                    actionTaken = "Dispatched Back keyevent";
                }
                else
                {
                    // Dispatch to CleanTV Home MainActivity with query intent
                    RunAdb("-s " + device + " shell am start -n com.gathin.tvhome/.MainActivity --es query \"" + query.Replace("\"", "\\\"") + "\"");
                    actionTaken = "Dispatched intent query to TV: \"" + query + "\"";
                }

                jsonResponse = string.Format("{{\"status\":\"ok\",\"intent\":{0},\"action\":{1},\"message\":{2}}}",
                    ToJsonString(query), ToJsonString(query), ToJsonString(actionTaken));
            }

            res.StatusCode = statusCode;
            byte[] jsonBytes = Encoding.UTF8.GetBytes(jsonResponse);
            res.ContentType = "application/json";
            res.ContentLength64 = jsonBytes.Length;
            res.OutputStream.Write(jsonBytes, 0, jsonBytes.Length);
            res.Close();
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
            string ts = DateTime.Now.ToString("yyyyMMdd-HHmmss");
            string dir = Path.Combine(backupsDir ?? Path.Combine(appDir, "backups"), "win-backup-" + ts);
            try
            {
                Directory.CreateDirectory(dir);
                var pDisabled = RunAdb("-s " + device + " shell pm list packages -d", 8000);
                File.WriteAllText(Path.Combine(dir, "packages-disabled.txt"), pDisabled.StdOut ?? "");

                var pAll = RunAdb("-s " + device + " shell pm list packages", 8000);
                File.WriteAllText(Path.Combine(dir, "packages-all.txt"), pAll.StdOut ?? "");

                var getprop = RunAdb("-s " + device + " shell getprop", 8000);
                File.WriteAllText(Path.Combine(dir, "getprop.txt"), getprop.StdOut ?? "");

                return dir;
            }
            catch
            {
                return dir;
            }
        }

        private static string FindLatestBackupDir()
        {
            string baseDir = backupsDir ?? Path.Combine(appDir, "backups");
            if (!Directory.Exists(baseDir)) return null;

            var dirs = new List<string>(Directory.GetDirectories(baseDir, "*backup-*"));
            if (dirs.Count == 0) return null;

            dirs.Sort();
            dirs.Reverse();
            return dirs[0];
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
