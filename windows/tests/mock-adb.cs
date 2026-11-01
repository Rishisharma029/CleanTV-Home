using System;
using System.IO;
using System.Collections.Generic;

namespace CleanTV.Tests
{
    class MockAdb
    {
        static int Main(string[] args)
        {
            string cmd = string.Join(" ", args);
            string stateFile = Path.Combine(Path.GetTempPath(), "cleantv_mock_disabled_pkgs.txt");

            if (cmd.Contains("version"))
            {
                Console.WriteLine("Android Debug Bridge version 1.0.41");
                Console.WriteLine("Version 34.0.5-10900870");
                return 0;
            }

            if (cmd.Contains("devices"))
            {
                Console.WriteLine("List of devices attached");
                Console.WriteLine("mock-tv-device\tdevice");
                return 0;
            }

            if (cmd.Contains("connect"))
            {
                Console.WriteLine("connected to mock-tv-device:5555");
                return 0;
            }

            // Test scenario: fail-closed home launcher detection
            if (cmd.Contains("unresolved-home-device"))
            {
                if (cmd.Contains("resolve-activity") || cmd.Contains("dumpsys"))
                {
                    Console.Error.WriteLine("Error: Activity not found");
                    return 1;
                }
            }

            // Test scenario: resolver fails, foreground app is YouTube (must NOT be treated as HOME)
            if (cmd.Contains("foreground-app-device"))
            {
                if (cmd.Contains("resolve-activity"))
                {
                    Console.Error.WriteLine("Error: Activity not found");
                    return 1;
                }
                if (cmd.Contains("dumpsys activity activities"))
                {
                    Console.WriteLine("  mResumedActivity: ActivityRecord{12345 u0 com.google.android.youtube.tv/.MainActivity t12}");
                    return 0;
                }
            }

            // Normal home launcher detection
            if (cmd.Contains("resolve-activity"))
            {
                if (cmd.Contains("cleantv-active-device"))
                {
                    Console.WriteLine("priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true");
                    Console.WriteLine("com.gathin.tvhome/.MainActivity");
                }
                else
                {
                    // Real-world OEM TV launcher with custom activity class (NOT .MainActivity)
                    Console.WriteLine("priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true");
                    Console.WriteLine("com.mitv.tvhome/com.mitv.tvhome.ui.TvHomeActivity");
                }
                return 0;
            }

            if (cmd.Contains("dumpsys activity activities"))
            {
                Console.WriteLine("  mResumedActivity: ActivityRecord{12345 u0 com.mitv.tvhome/com.mitv.tvhome.ui.TvHomeActivity t12}");
                return 0;
            }

            // Package disable: record in persistent state
            if (cmd.Contains("shell pm disable-user"))
            {
                string pkg = args[args.Length - 1];
                var pkgs = new HashSet<string>();
                if (File.Exists(stateFile))
                {
                    foreach (var l in File.ReadAllLines(stateFile))
                    {
                        if (!string.IsNullOrEmpty(l.Trim())) pkgs.Add(l.Trim());
                    }
                }
                pkgs.Add(pkg);
                File.WriteAllLines(stateFile, new List<string>(pkgs).ToArray());
                Console.WriteLine("Package " + pkg + " new state: disabled-user");
                return 0;
            }

            // Package enable: remove from state
            if (cmd.Contains("shell pm enable"))
            {
                string pkg = args[args.Length - 1];
                if (File.Exists(stateFile))
                {
                    var lines = new List<string>();
                    foreach (var l in File.ReadAllLines(stateFile))
                    {
                        if (!string.IsNullOrEmpty(l.Trim()) && l.Trim() != pkg)
                        {
                            lines.Add(l.Trim());
                        }
                    }
                    File.WriteAllLines(stateFile, lines.ToArray());
                }
                Console.WriteLine("Package " + pkg + " new state: enabled");
                return 0;
            }

            // List disabled packages: return baseline + state
            if (cmd.Contains("shell pm list packages -d"))
            {
                Console.WriteLine("package:com.mitv.screensaver");
                if (File.Exists(stateFile))
                {
                    foreach (var l in File.ReadAllLines(stateFile))
                    {
                        if (!string.IsNullOrEmpty(l.Trim()))
                        {
                            Console.WriteLine("package:" + l.Trim());
                        }
                    }
                }
                return 0;
            }

            // List all packages
            if (cmd.Contains("shell pm list packages"))
            {
                Console.WriteLine("package:com.google.android.youtube.tv");
                Console.WriteLine("package:com.mitv.tvhome");
                Console.WriteLine("package:com.xiaomi.mitv.upgrade");
                Console.WriteLine("package:com.xiaomi.mitv.advertise");
                Console.WriteLine("package:com.google.android.tvlauncher");
                Console.WriteLine("package:com.gathin.tvhome");
                return 0;
            }

            // System properties
            if (cmd.Contains("shell getprop"))
            {
                Console.WriteLine("[ro.product.brand]: [Xiaomi]");
                Console.WriteLine("[ro.product.model]: [MiTV-4A]");
                Console.WriteLine("[ro.build.version.release]: [9]");
                Console.WriteLine("[ro.product.manufacturer]: [Xiaomi]");
                return 0;
            }

            // Sideload install
            if (cmd.Contains("install -r -d"))
            {
                Console.WriteLine("Performing Streamed Install");
                Console.WriteLine("Success");
                return 0;
            }

            // Set home activity: require exact activity component (fails if assuming .MainActivity on com.mitv.tvhome)
            if (cmd.Contains("cmd package set-home-activity"))
            {
                string comp = args[args.Length - 1];
                if (comp.Contains(".MainActivity") && comp.Contains("com.mitv.tvhome"))
                {
                    Console.Error.WriteLine("Error: Activity class {.MainActivity} does not exist in target package com.mitv.tvhome");
                    return 1;
                }
                Console.WriteLine("Success: Home activity set to " + comp);
                return 0;
            }

            return 0;
        }
    }
}
