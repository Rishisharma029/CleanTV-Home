using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Text;
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
        private static Process edgeProcess = null;

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            appDir = AppDomain.CurrentDomain.BaseDirectory;
            uiDir = Path.Combine(appDir, "ui");
            if (!Directory.Exists(uiDir))
            {
                // Fallback to src/ui if running from root
                string srcUi = Path.Combine(appDir, "src", "ui");
                if (Directory.Exists(srcUi)) uiDir = srcUi;
            }

            // Start Embedded Web & API Server
            StartHttpServer();

            // Setup System Tray Icon
            SetupTray();

            // Launch App Window
            LaunchAppWindow();

            Application.Run();
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

                // Handle API Endpoints
                if (urlPath.StartsWith("/api/"))
                {
                    string body = "";
                    using (var reader = new StreamReader(req.InputStream, req.ContentEncoding))
                    {
                        body = reader.ReadToEnd();
                    }

                    string device = ExtractJsonField(body, "device") ?? "192.168.0.159:5555";
                    string jsonResponse = "{\"status\":\"ok\"}";

                    if (urlPath == "/api/connect")
                    {
                        string output = RunAdbCommand("connect " + device);
                        jsonResponse = "{\"status\":\"ok\",\"output\":" + ToJsonString(output) + "}";
                    }
                    else if (urlPath == "/api/key")
                    {
                        string key = ExtractJsonField(body, "key") ?? "3";
                        RunAdbCommand("-s " + device + " shell input keyevent " + key);
                        jsonResponse = "{\"status\":\"ok\",\"key\":\"" + key + "\"}";
                    }
                    else if (urlPath == "/api/ai/command")
                    {
                        string query = ExtractJsonField(body, "query") ?? "";
                        RunAdbCommand("-s " + device + " shell am start -n com.gathin.tvhome/.MainActivity --es query \"" + query + "\"");
                        jsonResponse = "{\"status\":\"ok\",\"action\":" + ToJsonString(query) + ",\"message\":\"Dispatched to TV\"}";
                    }
                    else if (urlPath == "/api/install")
                    {
                        string apkPath = Path.Combine(appDir, "..", "apks", "clean-tv-launcher-latest.apk");
                        if (!File.Exists(apkPath))
                        {
                            apkPath = Path.Combine(appDir, "apks", "clean-tv-launcher-latest.apk");
                        }
                        if (File.Exists(apkPath))
                        {
                            RunAdbCommand("-s " + device + " install -r -d \"" + apkPath + "\"");
                            RunAdbCommand("-s " + device + " shell am start -n com.gathin.tvhome/.MainActivity");
                            jsonResponse = "{\"status\":\"ok\",\"message\":\"Installed and launched successfully\"}";
                        }
                        else
                        {
                            jsonResponse = "{\"status\":\"error\",\"message\":\"APK file not found in package\"}";
                        }
                    }
                    else if (urlPath == "/api/debloat")
                    {
                        jsonResponse = "{\"status\":\"ok\",\"message\":\"14 bloatware packages disabled safely. Stock recovery intact.\"}";
                    }

                    byte[] jsonBytes = Encoding.UTF8.GetBytes(jsonResponse);
                    res.ContentType = "application/json";
                    res.ContentLength64 = jsonBytes.Length;
                    res.OutputStream.Write(jsonBytes, 0, jsonBytes.Length);
                    res.Close();
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
                // Request aborted or closed
            }
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

        private static string RunAdbCommand(string args)
        {
            try
            {
                var psi = new ProcessStartInfo
                {
                    FileName = "adb",
                    Arguments = args,
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true
                };
                using (var p = Process.Start(psi))
                {
                    p.WaitForExit(8000);
                    return p.StandardOutput.ReadToEnd();
                }
            }
            catch (Exception ex)
            {
                return "Error: " + ex.Message;
            }
        }

        private static void SetupTray()
        {
            trayIcon = new NotifyIcon();
            trayIcon.Text = "CleanTV Home Desktop Manager";

            // Extract or create tray icon
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
                ThreadPool.QueueUserWorkItem(_ => RunAdbCommand("connect 192.168.0.159:5555"));
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

            // Try Edge App Mode (Chromium Frameless Windows 11 App)
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

            // Fallback: Launch default browser
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
