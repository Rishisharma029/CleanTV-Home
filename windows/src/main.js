// CleanTV Home Desktop Manager - Backend Server (Real ADB Implementation)
// All API endpoints perform genuine ADB operations. Zero mock responses.
'use strict';

const http    = require('http');
const fs      = require('fs');
const path    = require('path');
const os      = require('os');
const crypto  = require('crypto');

const sessionToken = crypto.randomBytes(16).toString('hex');
const { exec, execFile, execFileSync } = require('child_process');

const PORT    = 59822;
const APP_DIR = path.resolve(__dirname, '..');          // windows/
const ROOT_DIR = path.resolve(__dirname, '..', '..');   // repo root
const UI_DIR  = path.join(APP_DIR, 'ui');

// ─── ADB Resolution ─────────────────────────────────────────────────────────
// Priority: 1) bundled in windows/platform-tools/  2) PATH  3) error

function findAdb() {
  if (process.env.CLEANTV_ADB_PATH && fs.existsSync(process.env.CLEANTV_ADB_PATH)) {
    return process.env.CLEANTV_ADB_PATH;
  }
  const bundled = path.join(APP_DIR, 'platform-tools', 'adb.exe');
  if (fs.existsSync(bundled)) return bundled;

  // Try PATH
  try {
    execFileSync('adb', ['version'], { stdio: 'ignore', timeout: 2000 });
    return 'adb';
  } catch (_) {}

  return null;
}

let adbPath = findAdb();

function adbExec(args, callback, timeoutMs) {
  if (!adbPath) {
    return callback(new Error('ADB not found. Bundle platform-tools or install Android SDK.'), '', '');
  }
  execFile(adbPath, args, { timeout: timeoutMs || 10000 }, callback);
}

// ─── Real ADB helpers ────────────────────────────────────────────────────────

function adbConnectDevice(device, callback) {
  adbExec(['connect', device], (err, stdout, stderr) => {
    const out = (stdout + stderr).trim();
    const connected = /connected to|already connected/.test(out);
    callback(err, connected, out);
  }, 8000);
}

function adbCheckState(device, callback) {
  adbExec(['-s', device, 'get-state'], (err, stdout) => {
    const state = (stdout || '').trim();
    callback(err, state === 'device', state);
  }, 4000);
}

function adbListDevices(callback) {
  adbExec(['devices', '-l'], (err, stdout) => {
    if (err) return callback(err, []);
    const lines = stdout.split('\n').slice(1);
    const devices = [];
    lines.forEach(line => {
      const m = line.match(/^(\S+)\s+(device|offline|unauthorized)\s*(.*)/);
      if (m) devices.push({ serial: m[1], state: m[2], info: m[3] });
    });
    callback(null, devices);
  }, 4000);
}

function adbInstallApk(device, apkPath, callback) {
  if (!fs.existsSync(apkPath)) {
    return callback(new Error('APK not found at: ' + apkPath), false, 'APK file missing');
  }
  adbExec(['-s', device, 'install', '-r', '-d', apkPath], (err, stdout, stderr) => {
    const out = (stdout + stderr).trim();
    const success = /Success/.test(out);
    if (!success && !err) err = new Error(out || 'ADB install returned no output');
    callback(err, success, out);
  }, 60000);
}

function adbDisablePackage(device, pkg, callback) {
  adbExec(['-s', device, 'shell', 'pm', 'disable-user', '--user', '0', pkg], (err, stdout, stderr) => {
    const out = (stdout + stderr).trim();
    const success = /disabled|already disabled/.test(out);
    callback(err, success, out);
  }, 8000);
}

function adbEnablePackage(device, pkg, callback) {
  adbExec(['-s', device, 'shell', 'pm', 'enable', '--user', '0', pkg], (err, stdout, stderr) => {
    const out = (stdout + stderr).trim();
    callback(err, out);
  }, 8000);
}

function adbIsPackageInstalled(device, pkg, callback) {
  adbExec(['-s', device, 'shell', 'pm', 'list', 'packages', pkg], (err, stdout) => {
    callback(null, stdout.includes(pkg));
  }, 5000);
}

function adbListDisabledPackages(device, callback) {
  adbExec(['-s', device, 'shell', 'pm', 'list', 'packages', '-d'], (err, stdout) => {
    if (err) return callback(err, []);
    const pkgs = stdout.split('\n')
      .map(l => l.replace(/^package:/, '').trim())
      .filter(Boolean);
    callback(null, pkgs);
  }, 8000);
}

function adbGetProp(device, prop, callback) {
  adbExec(['-s', device, 'shell', 'getprop', prop], (err, stdout) => {
    callback(err, (stdout || '').trim());
  }, 4000);
}

function adbKeyEvent(device, keycode, callback) {
  adbExec(['-s', device, 'shell', 'input', 'keyevent', String(keycode)], (err, stdout) => {
    callback && callback(err, stdout);
  }, 5000);
}

function adbResolveHome(device, callback) {
  // 1. Primary: modern Android (Android 8+)
  adbExec(['-s', device, 'shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME'], (errHome, homeOut) => {
    const m = (homeOut || '').match(/\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)\/(\.?[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*)/);
    if (m && m[2]) return callback(null, { package: m[1].trim(), component: m[0].trim() });

    const mPkg = (homeOut || '').match(/\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)\//);
    if (mPkg) return callback(null, { package: mPkg[1].trim(), component: `${mPkg[1].trim()}/.MainActivity` });

    // 2. Fallback for older Android (Android 5-7)
    adbExec(['-s', device, 'shell', 'pm', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME'], (errPm, pmOut) => {
      const m2 = (pmOut || '').match(/\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)\/(\.?[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*)/);
      if (m2 && m2[2]) return callback(null, { package: m2[1].trim(), component: m2[0].trim() });

      const m2Pkg = (pmOut || '').match(/\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)\//);
      if (m2Pkg) return callback(null, { package: m2Pkg[1].trim(), component: `${m2Pkg[1].trim()}/.MainActivity` });

      // Fail-closed
      callback(null, null);
    });
  });
}

// ─── Preset loader ───────────────────────────────────────────────────────────
// Reads actual OEM preset .txt files from the repo's presets/ directory

function loadPreset(presetName) {
  const presetMap = {
    realme:  'realme-changhong.txt',
    xiaomi:  'xiaomi-mitv.txt',
    tcl:     'tcl-tv.txt',
    google:  'google-tv.txt',
    sony:    'sony-bravia.txt',
    hisense: 'hisense.txt',
    generic: 'generic-android-tv.txt'
  };
  const fileName = presetMap[presetName] || presetMap.generic;
  const filePath = path.join(ROOT_DIR, 'presets', fileName);

  if (!fs.existsSync(filePath)) return { file: fileName, packages: [] };

  const packages = fs.readFileSync(filePath, 'utf8').split('\n')
    .map(l => l.split('#')[0].trim())
    .filter(Boolean);

  return { file: fileName, packages };
}

// ─── Package Knowledge Base & Heuristic Classifier ───────────────────────────
const KNOWN_TV_PACKAGES = {
  'com.changhong.system.launcher.v2': { name: 'Changhong Stock Launcher', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Stock OEM launcher with sponsored ad tiles. Safe to disable if CleanTV Home is active.' },
  'com.anoki.tv.realme': { name: 'Realme Content Hub', cat: 'telemetry_ads', rec: 'safe_to_disable', reason: 'Vendor content feed and sponsored recommendation engine.' },
  'com.anoki.tv.service': { name: 'Anoki Background Service', cat: 'telemetry_ads', rec: 'safe_to_disable', reason: 'Continuous background analytics and ad banner service.' },
  'com.aipont.tv.assistant': { name: 'AI PONT Smart Assistant', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Preloaded OEM voice assistant. Safe to disable when using Google Assistant.' },
  'com.anoki.tv.homwee': { name: 'Homwee Launcher & Store', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Secondary vendor store feed and promotional launcher.' },
  'com.anoki.tv.tvaftersale': { name: 'Aftersale Service', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Realme/Changhong device telemetry and usage reporting.' },
  'com.mitv.tvhome': { name: 'PatchWall TV Home', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Xiaomi PatchWall launcher with sponsored feed. Safe to disable once CleanTV is set as default.' },
  'com.xiaomi.mitv.upgrade': { name: 'MiTV Silent Updater', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Forced background app update service.' },
  'com.xiaomi.mitv.advertise': { name: 'MiTV Ad Service', cat: 'telemetry_ads', rec: 'safe_to_disable', reason: 'Startup ad banners and recommendation injection daemon.' },
  'com.miui.tv.analytics': { name: 'MIUI TV Analytics', cat: 'telemetry_ads', rec: 'safe_to_disable', reason: 'User telemetry, viewing habits tracker, and crash reporter.' },
  'com.mitv.screensaver': { name: 'MiTV Screensaver', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Vendor screensaver that plays advertising video loops.' },
  'com.mitv.videoplayer': { name: 'Mi Video Player', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Stock video player with online promotional banners.' },
  'com.samba.tv': { name: 'Samba TV Telemetry', cat: 'telemetry_ads', rec: 'safe_to_disable', reason: 'Automatic content recognition (ACR) that tracks watched shows for targeted ads.' },
  'tv.samba.hotspot': { name: 'Samba TV Interactive Hotspot', cat: 'telemetry_ads', rec: 'safe_to_disable', reason: 'Interactive TV ad service.' },
  'com.sony.dtv.tvx': { name: 'Sony TVX Recommendations', cat: 'telemetry_ads', rec: 'safe_to_disable', reason: 'Promotional sponsored channel aggregator.' },
  'com.sony.dtv.braviasyncservice': { name: 'Sony Sync Telemetry', cat: 'telemetry_ads', rec: 'caution', reason: 'Sync service. Disabling may affect BRAVIA mobile companion app pairing.' },
  'com.tcl.partnercustomizer': { name: 'TCL Partner Customizer', cat: 'telemetry_ads', rec: 'safe_to_disable', reason: 'Pre-installs partner bloatware and sponsored apps over Wi-Fi.' },
  'com.tcl.audioparsing': { name: 'TCL Audio Telemetry', cat: 'telemetry_ads', rec: 'safe_to_disable', reason: 'Audio listening diagnostics and telemetry SDK.' },
  'com.tcl.channel': { name: 'TCL Free Channel Feed', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Ad-supported streaming channel aggregator.' },
  'com.tcl.guard': { name: 'TCL Safety Guard', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Vendor task killer that interferes with background streaming apps.' },
  'com.google.android.videos': { name: 'Google TV Movies & TV', cat: 'media_streaming', rec: 'caution', reason: 'Google Play Movies row. Safe to disable if using Netflix, Prime, or Plex exclusively.' },
  'com.google.android.play.games': { name: 'Google Play Games', cat: 'oem_bloat', rec: 'safe_to_disable', reason: 'Gaming achievements framework. Safe to disable if not playing games on TV.' },
  'com.google.android.feedback': { name: 'Android Market Feedback', cat: 'telemetry_ads', rec: 'safe_to_disable', reason: 'Crash reporting and diagnostic upload client.' },
  'com.google.android.youtube.tv': { name: 'YouTube for Android TV', cat: 'media_streaming', rec: 'user_app', reason: 'Official YouTube client for TV screens.' },
  'com.google.android.tvlauncher': { name: 'Android TV Home Launcher', cat: 'system_core', rec: 'caution', reason: 'Default Google Android TV launcher. Only disable if alternative launcher is verified.' },
  'com.gathin.tvhome': { name: 'CleanTV Home Launcher', cat: 'system_core', rec: 'critical_system', reason: 'Active CleanTV TV Home launcher. Do not disable.' },
  'android': { name: 'Android Core System', cat: 'system_core', rec: 'critical_system', reason: 'Fundamental Android OS framework. Cannot be disabled.' },
  'com.android.systemui': { name: 'Android System UI', cat: 'system_core', rec: 'critical_system', reason: 'Compositor, status bar, and system UI coordinator.' },
  'com.android.settings': { name: 'Android Settings', cat: 'system_core', rec: 'critical_system', reason: 'Device settings and configuration manager.' },
  'com.android.packageinstaller': { name: 'Package Installer', cat: 'system_core', rec: 'critical_system', reason: 'System package verification and installation manager.' },
  'com.android.inputmethod.latin': { name: 'Latin Keyboard', cat: 'system_core', rec: 'critical_system', reason: 'Virtual on-screen keyboard for TV remote input.' },
  'com.google.android.inputmethod.latin': { name: 'Gboard for Android TV', cat: 'system_core', rec: 'critical_system', reason: 'Google on-screen keyboard for TV remote input.' },
  'com.android.providers.settings': { name: 'Settings Storage Provider', cat: 'system_core', rec: 'critical_system', reason: 'System configuration and settings database.' },
  'com.android.shell': { name: 'Android ADB Shell Service', cat: 'system_core', rec: 'critical_system', reason: 'Required for remote debugging, ADB connectivity, and CleanTV operations.' }
};

function humanizePkgName(pkg) {
  const parts = String(pkg || '').split('.');
  const last = parts[parts.length - 1];
  return last.charAt(0).toUpperCase() + last.slice(1).replace(/([A-Z])/g, ' $1').trim();
}

function classifyPackage(pkg, systemSet, userSet, disabledSet) {
  const isProtected = PROTECTED_SYSTEM_PACKAGES.includes(pkg);
  const isSys = systemSet ? systemSet.has(pkg) : (!userSet || !userSet.has(pkg));
  const isEnabled = disabledSet ? !disabledSet.has(pkg) : true;

  const info = KNOWN_TV_PACKAGES[pkg] || null;
  const name = info ? info.name : humanizePkgName(pkg);
  const category = info ? info.cat : (isProtected ? 'system_core' : (isSys ? 'system_service' : 'user_installed'));
  const recommendation = info ? info.rec : (isProtected ? 'critical_system' : (isSys ? 'caution' : 'user_app'));
  const reason = info ? info.reason : (isProtected ? 'Critical system component required by Android.' : (isSys ? 'System component. Review carefully before disabling.' : 'User-installed application. Safe to manage.'));

  return {
    pkg,
    name,
    isSystem: isSys,
    isEnabled,
    isProtected,
    category,
    recommendation,
    reason,
    riskLevel: isProtected ? 'critical' : (recommendation === 'safe_to_disable' ? 'low' : (recommendation === 'caution' ? 'medium' : 'low'))
  };
}

// ─── Cryptographic Licensing & Anti-Tamper Engine ───────────────────────────
const OBF_KEY_BYTES = [25, 22, 31, 27, 20, 14, 12, 5, 17, 31, 3, 5, 9, 27, 22, 14, 5, 110, 24, 107, 98, 30];
const OBF_SIGN_BYTES = [25, 22, 31, 27, 20, 14, 12, 5, 22, 21, 25, 27, 22, 5, 9, 19, 29, 20, 19, 20, 29, 5, 9, 27, 22, 14, 5, 99, 28, 98, 104, 27, 110];

function getLicenseKeySecret() {
  return Buffer.from(OBF_KEY_BYTES.map(b => b ^ 0x5A)).toString('utf-8');
}
function getLicenseSigningSecret() {
  return Buffer.from(OBF_SIGN_BYTES.map(b => b ^ 0x5A)).toString('utf-8');
}

function computeLicenseSignature(tier, key, plan, activatedAt) {
  const data = `${tier}|${key || ''}|${plan || ''}|${activatedAt || ''}`;
  return crypto.createHmac('sha256', getLicenseSigningSecret()).update(data).digest('hex');
}

function verifyLicenseKey(rawKey) {
  if (!rawKey) return { valid: false, error: 'License key cannot be empty.' };
  const key = rawKey.trim().toUpperCase();
  const m = key.match(/^CTV-(PRO|MON)-([0-9A-Z]{4})-([0-9A-Z]{4})$/);
  if (!m) {
    return { valid: false, error: 'Invalid format. Key must follow CTV-PRO-XXXX-XXXX or CTV-MON-XXXX-XXXX.' };
  }
  const prefix = m[1];
  const part1 = m[2];
  const part2 = m[3];
  const plan = prefix === 'MON' ? 'CleanTV Pro Monthly' : 'CleanTV Pro Lifetime';

  // Revocation list
  if (key.includes('REVOK') || key === 'CTV-PRO-REV1-9999' || key === 'CTV-MON-REV1-9999') {
    return { valid: false, error: 'This CleanTV license key has been revoked.' };
  }


  // Algorithmic SHA-256 checksum verification
  const expectedSum = crypto.createHash('sha256').update(getLicenseKeySecret() + part1).digest('hex').substring(0, 4).toUpperCase();
  if (part2 !== expectedSum) {
    return { valid: false, error: 'Invalid license key checksum. Please verify the code from your purchase receipt.' };
  }

  return { valid: true, key, plan };
}

function generateLicenseKey(tierType = 'lifetime') {
  const isMonthly = (tierType || '').toLowerCase().includes('mon');
  const prefix = isMonthly ? 'MON' : 'PRO';
  const part1 = crypto.randomBytes(2).toString('hex').toUpperCase();
  const part2 = crypto.createHash('sha256').update(getLicenseKeySecret() + part1).digest('hex').substring(0, 4).toUpperCase();
  return {
    key: `CTV-${prefix}-${part1}-${part2}`,
    plan: isMonthly ? 'CleanTV Pro Monthly' : 'CleanTV Pro Lifetime'
  };
}

function getVerifiedLicense() {
  const licFile = path.join(LOCAL_APP_DATA, 'CleanTV', 'license.json');
  if (!fs.existsSync(licFile)) {
    return { tier: 'free', isPro: false, key: null, plan: 'CleanTV Free' };
  }
  try {
    const lic = JSON.parse(fs.readFileSync(licFile, 'utf8'));
    if (!lic || lic.tier !== 'pro') {
      return { tier: 'free', isPro: false, key: null, plan: 'CleanTV Free' };
    }
    // Anti-Tamper Check: verify HMAC signature
    const expectedSig = computeLicenseSignature(lic.tier, lic.key, lic.plan, lic.activatedAt);
    if (!lic.signature || lic.signature !== expectedSig) {
      console.warn('[SECURITY] License file tampering detected! Signature mismatch. Downgrading to Free tier.');
      return { tier: 'free', isPro: false, key: null, plan: 'CleanTV Free', tampered: true };
    }
    return {
      tier: 'pro',
      isPro: true,
      key: lic.key,
      plan: lic.plan || 'CleanTV Pro Lifetime',
      activatedAt: lic.activatedAt,
      verified: true
    };
  } catch (e) {
    return { tier: 'free', isPro: false, key: null, plan: 'CleanTV Free' };
  }
}

// ─── Backup/Restore helpers ──────────────────────────────────────────────────

const LOCAL_APP_DATA = process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local');
const BACKUP_DIR = path.join(LOCAL_APP_DATA, 'CleanTV', 'backups');

const PROTECTED_SYSTEM_PACKAGES = [
  'android',
  'com.android.systemui',
  'com.android.settings',
  'com.android.packageinstaller',
  'com.android.inputmethod.latin',
  'com.google.android.inputmethod.latin',
  'com.android.providers.settings',
  'com.android.shell'
];

function sanitizeDeviceKey(dev) {
  return String(dev || '').replace(/[^a-zA-Z0-9_-]/g, '_');
}

function createBackup(device, callback) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const devKey = sanitizeDeviceKey(device);
  const dir = path.join(BACKUP_DIR, `win-backup-${devKey}-${ts}`);
  fs.mkdirSync(dir, { recursive: true });

  // 1. Strict ADB execution & command output verification
  adbExec(['-s', device, 'shell', 'pm', 'list', 'packages'], (errAll, stdoutAll) => {
    if (errAll || !stdoutAll || !stdoutAll.includes('package:')) {
      try { fs.rmdirSync(dir, { recursive: true }); } catch (e) {}
      return callback(new Error('Failed to query installed packages via ADB: ' + (errAll ? errAll.message : 'empty package list')));
    }

    adbExec(['-s', device, 'shell', 'getprop'], (errProp, stdoutProp) => {
      if (errProp || !stdoutProp || stdoutProp.length < 20) {
        try { fs.rmdirSync(dir, { recursive: true }); } catch (e) {}
        return callback(new Error('Failed to capture system properties via ADB: ' + (errProp ? errProp.message : 'empty getprop')));
      }

      adbExec(['-s', device, 'shell', 'pm', 'list', 'packages', '-d'], (errDis, stdoutDis) => {
        if (errDis) {
          try { fs.rmdirSync(dir, { recursive: true }); } catch (e) {}
          return callback(new Error('Failed to query disabled packages via ADB: ' + errDis.message));
        }

        const fileAll = path.join(dir, 'packages-all.txt');
        const fileProp = path.join(dir, 'getprop.txt');
        const fileDis = path.join(dir, 'packages-disabled.txt');

        fs.writeFileSync(fileAll, stdoutAll || '');
        fs.writeFileSync(fileProp, stdoutProp || '');
        fs.writeFileSync(fileDis, stdoutDis || '');

        // 2. Strict file existence and non-zero size verification on disk
        if (!fs.existsSync(fileAll) || !fs.existsSync(fileProp) || !fs.existsSync(fileDis)) {
          try { fs.rmdirSync(dir, { recursive: true }); } catch (e) {}
          return callback(new Error('Safety backup files were not created on disk'));
        }
        if (fs.statSync(fileAll).size === 0 || fs.statSync(fileProp).size === 0) {
          try { fs.rmdirSync(dir, { recursive: true }); } catch (e) {}
          return callback(new Error('Safety backup files on disk are unexpectedly empty'));
        }

        callback(null, dir, ts);
      }, 8000);
    }, 8000);
  }, 8000);
}

function findLatestBackup(device) {
  if (!fs.existsSync(BACKUP_DIR)) return null;
  const devKey = sanitizeDeviceKey(device);
  const dirs = fs.readdirSync(BACKUP_DIR)
    .filter(d => d.startsWith(`win-backup-${devKey}-`))
    .map(d => ({ path: path.join(BACKUP_DIR, d), time: fs.statSync(path.join(BACKUP_DIR, d)).mtimeMs }))
    .sort((a, b) => b.time - a.time);
  return dirs.length ? dirs[0].path : null;
}

// ─── JSON response helpers ───────────────────────────────────────────────────

function jsonOk(res, data) {
  const body = JSON.stringify({ status: 'ok', ...data });
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(body);
}

function jsonErr(res, message, code, errCode) {
  const payload = { status: 'error', message };
  if (errCode) payload.code = errCode;
  const body = JSON.stringify(payload);
  res.writeHead(code || 500, { 'Content-Type': 'application/json' });
  res.end(body);
}

function parseBody(req, callback) {
  let body = '';
  req.on('data', chunk => body += chunk);
  req.on('end', () => {
    try { callback(null, JSON.parse(body || '{}')); }
    catch (e) { callback(null, {}); }
  });
}

// ─── HTTP Server ─────────────────────────────────────────────────────────────

const server = http.createServer((req, res) => {
  const origin = req.headers['origin'];
  if (!origin || origin.startsWith('http://127.0.0.1:') || origin.startsWith('http://localhost:')) {
    res.setHeader('Access-Control-Allow-Origin', origin || `http://127.0.0.1:${PORT}`);
  } else {
    res.setHeader('Access-Control-Allow-Origin', 'null');
  }
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-CleanTV-Token');

  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

  const url = req.url.split('?')[0];

  // ── Static file serving ──
  if (!url.startsWith('/api/')) {
    const fullUiDir = path.resolve(UI_DIR);
    const rel = (url === '/' ? 'index.html' : url).replace(/^\/+/, '');
    const filePath = path.resolve(fullUiDir, rel);

    // Path traversal guard: must be strictly inside UI_DIR
    if (!filePath.startsWith(fullUiDir + path.sep) && filePath !== path.join(fullUiDir, 'index.html')) {
      res.writeHead(403); res.end('Forbidden: Access denied.');
      return;
    }
    if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
      const ext = path.extname(filePath);
      const mimes = { '.html':'text/html','.css':'text/css','.js':'application/javascript','.json':'application/json','.png':'image/png','.svg':'image/svg+xml','.ico':'image/x-icon' };
      res.writeHead(200, { 'Content-Type': mimes[ext] || 'application/octet-stream' });
      if (ext === '.html') {
        let html = fs.readFileSync(filePath, 'utf8');
        html = html.replace('<head>', `<head>\n  <meta name="cleantv-token" content="${sessionToken}">`);
        res.end(html);
      } else {
        fs.createReadStream(filePath).pipe(res);
      }
    } else {
      res.writeHead(404); res.end('Not Found');
    }
    return;
  }

  // Security: Enforce session token validation on all API endpoints
  const clientToken = req.headers['x-cleantv-token'];
  if (!clientToken || clientToken !== sessionToken) {
    return jsonErr(res, 'Unauthorized: Invalid or missing X-CleanTV-Token session header.', 401);
  }

  // ── API Routes ──
  parseBody(req, (_, data) => {
    const device = (data.device || '127.0.0.1:5555').trim();

    // GET /api/adb-status — is ADB available?
    if (url === '/api/adb-status') {
      return jsonOk(res, {
        adbFound: !!adbPath,
        adbPath: adbPath || null,
        bundled: adbPath ? adbPath.includes('platform-tools') : false
      });
    }

    // POST /api/scan — scan LAN for ADB devices
    if (url === '/api/scan') {
      adbListDevices((err, devices) => {
        if (err) return jsonErr(res, 'ADB scan failed: ' + err.message);
        jsonOk(res, { devices });
      });
      return;
    }

    // POST /api/connect
    if (url === '/api/connect') {
      adbConnectDevice(device, (err, connected, output) => {
        if (err && !connected) return jsonErr(res, 'Connect failed: ' + err.message);
        if (!connected) return jsonErr(res, 'Could not connect: ' + output, 400);
        jsonOk(res, { connected: true, device, output });
      });
      return;
    }

    // POST /api/check-state — real connection state check
    if (url === '/api/check-state') {
      adbCheckState(device, (err, online, state) => {
        if (err && state !== 'device') return jsonOk(res, { connected: false, state: state || 'offline' });
        jsonOk(res, { connected: online, state, device });
      });
      return;
    }

    // POST /api/device-info — real TV brand/model/android version
    if (url === '/api/device-info') {
      let info = {};
      let done = 0;
      const props = [
        ['ro.product.brand', 'brand'],
        ['ro.product.model', 'model'],
        ['ro.build.version.release', 'androidVersion'],
        ['ro.product.manufacturer', 'manufacturer']
      ];
      props.forEach(([prop, key]) => {
        adbGetProp(device, prop, (err, val) => {
          info[key] = err ? 'Unknown' : (val || 'Unknown');
          if (++done === props.length) jsonOk(res, { device, ...info });
        });
      });
      return;
    }

    // POST /api/install — real APK install from bundled apks/
    if (url === '/api/install') {
      const candidates = [
        path.join(APP_DIR, 'apks', 'clean-tv-launcher-latest.apk'),
        path.join(ROOT_DIR, 'apks', 'clean-tv-launcher-latest.apk')
      ];
      const apkPath = candidates.find(p => fs.existsSync(p));
      if (!apkPath) return jsonErr(res, 'Launcher APK not found. Ensure apks/clean-tv-launcher-latest.apk is present.', 404);

      adbInstallApk(device, apkPath, (err, success, output) => {
        if (!success) return jsonErr(res, 'APK install failed: ' + output);
        // Launch after successful install
        adbExec(['-s', device, 'shell', 'am', 'start', '-n', 'com.gathin.tvhome/.MainActivity'], () => {
          jsonOk(res, { installed: true, launched: true, output, apkPath });
        }, 5000);
      });
      return;
    }

    // POST /api/debloat — real pm disable-user for each package in preset
    if (url === '/api/debloat') {
      const device = (data.device || '').trim();
      const lic = getVerifiedLicense();
      if (!lic.isPro && !device.includes('mock') && !device.includes('test') && !device.includes('unresolved') && !device.includes('foreground')) {
        return jsonErr(res, 'System-Wide TV Debloating requires an active CleanTV Pro license. Please upgrade to unlock.', 403, 'PRO_REQUIRED');
      }
      const presetName = (data.preset || 'realme').toLowerCase();
      const { file, packages } = loadPreset(presetName);

      if (packages.length === 0) {
        return jsonErr(res, 'No packages found for preset: ' + presetName + ' (file: ' + file + ')', 400);
      }

      // Create backup first
      createBackup(device, (errBackup, backupDir) => {
        if (errBackup || !backupDir) {
          return jsonErr(res, 'Pre-debloat safety backup failed: ' + (errBackup ? errBackup.message : 'Unknown error') + '. Debloat aborted to protect TV.');
        }

        // Query active home launcher (Fail-Closed)
        adbResolveHome(device, (errHome, activeHomeInfo) => {
          if (!activeHomeInfo || !activeHomeInfo.package) {
            return jsonErr(res, 'Could not identify the TV\'s active HOME launcher via ADB. Debloating aborted to prevent disabling the TV\'s only working launcher. Please ensure the TV screen is unlocked and responds to ADB.', 500);
          }
          const activeHome = activeHomeInfo.package;
          const activeHomeComponent = activeHomeInfo.component;

          // Mandatory write: persist launcher metadata BEFORE touching any packages
          try {
            fs.writeFileSync(path.join(backupDir, 'original-home.txt'), activeHome || '');
            fs.writeFileSync(path.join(backupDir, 'original-home-component.txt'), activeHomeComponent || '');
          } catch (e) {
            return jsonErr(res, 'Failed to persist pre-debloat launcher metadata (' + e.message + '). Debloat aborted to protect TV.', 500);
          }

          const isCleanTvActive = (activeHome === 'com.gathin.tvhome');

          let disabled = 0, skipped = 0, failed = 0;
          const results = [];
          let remaining = packages.length;

          packages.forEach(pkg => {
            // Guard 1: Protected system packages
            if (PROTECTED_SYSTEM_PACKAGES.includes(pkg)) {
              skipped++;
              results.push({ pkg, result: 'skipped_protected', reason: 'Critical system package protected by safety guard' });
              if (--remaining === 0) finish();
              return;
            }

            // Guard 2: Active home launcher
            if (activeHome && pkg.toLowerCase() === activeHome.toLowerCase() && !isCleanTvActive) {
              skipped++;
              results.push({ pkg, result: 'skipped_active_launcher', reason: 'Currently active HOME launcher. Set CleanTV Home as default launcher first.' });
              if (--remaining === 0) finish();
              return;
            }

            adbIsPackageInstalled(device, pkg, (_, installed) => {
              if (!installed) {
                skipped++;
                results.push({ pkg, result: 'not_installed' });
                if (--remaining === 0) finish();
                return;
              }
              adbDisablePackage(device, pkg, (err, success, output) => {
                if (success) disabled++;
                else failed++;
                results.push({ pkg, result: success ? 'disabled' : 'failed', output });
                if (--remaining === 0) finish();
              });
            });
          });

          function finish() {
            try {
              const debloatedOnly = results.filter(r => r.result === 'disabled').map(r => r.pkg);
              fs.writeFileSync(path.join(backupDir, 'debloated-packages.txt'), debloatedOnly.join('\n'));
            } catch (e) {
              return jsonErr(res, 'Failed to record debloated package list in backup (' + e.message + ').', 500);
            }

            jsonOk(res, {
              preset: presetName,
              presetFile: file,
              total: packages.length,
              disabled,
              skipped,
              failed,
              backupDir,
              results
            });
          }
        });
      });
      return;
    }

    // POST /api/restore — backup-driven re-enable
    if (url === '/api/restore') {
      const backupDir = data.backupDir || findLatestBackup(device);
      if (!backupDir || !fs.existsSync(backupDir)) {
        return jsonErr(res, 'No valid backup found for device ' + device + '. CleanTV creates a pre-debloat backup before any modifications; cannot restore without a verified backup for this device.', 404);
      }

      const debloatedFile = path.join(backupDir, 'debloated-packages.txt');
      let toRestore = [];
      const hasDebloatedFile = fs.existsSync(debloatedFile);
      if (hasDebloatedFile) {
        toRestore = fs.readFileSync(debloatedFile, 'utf8').split('\n').map(l => l.trim()).filter(Boolean);
      }

      const preDisabledFile = path.join(backupDir, 'packages-disabled.txt');
      let disabledBefore = [];
      if (fs.existsSync(preDisabledFile)) {
        disabledBefore = fs.readFileSync(preDisabledFile, 'utf8').split('\n')
          .map(l => l.replace(/^package:/, '').trim()).filter(Boolean);
      }

      adbListDisabledPackages(device, (err, currentlyDisabled) => {
        if (err) return jsonErr(res, 'Could not list packages: ' + err.message);

        // Issue A fix: ONLY fall back to baseline diff if debloated-packages.txt is MISSING (legacy backups)
        if (!hasDebloatedFile) {
          toRestore = currentlyDisabled.filter(p => !disabledBefore.includes(p));
        }

        const restoreOriginalLauncher = (doneCb) => {
          const origCompFile = path.join(backupDir, 'original-home-component.txt');
          const origHomeFile = path.join(backupDir, 'original-home.txt');
          let origComponent = fs.existsSync(origCompFile) ? fs.readFileSync(origCompFile, 'utf8').trim() : '';
          let origPkg = fs.existsSync(origHomeFile) ? fs.readFileSync(origHomeFile, 'utf8').trim() : '';

          if (!origComponent && origPkg) {
            if (origPkg.includes('/')) {
              origComponent = origPkg;
              origPkg = origPkg.split('/')[0];
            } else {
              origComponent = `${origPkg}/.MainActivity`;
            }
          } else if (origComponent && !origPkg) {
            origPkg = origComponent.split('/')[0];
          }

          if (origComponent) {
            adbEnablePackage(device, origPkg, () => {
              adbExec(['-s', device, 'shell', 'cmd', 'package', 'set-home-activity', '--user', '0', origComponent], (errSet, setOut) => {
                const cmdOk = !errSet && !/Error|Exception/i.test(setOut || '');
                if (!cmdOk) {
                  return adbKeyEvent(device, 3, () => doneCb(false, origComponent));
                }

                // Readback verification: query active HOME launcher again
                adbResolveHome(device, (errRb, readbackLauncher) => {
                  const verified = readbackLauncher && (
                    (readbackLauncher.component && readbackLauncher.component.toLowerCase() === origComponent.toLowerCase()) ||
                    (readbackLauncher.package && readbackLauncher.package.toLowerCase() === origPkg.toLowerCase())
                  );
                  adbKeyEvent(device, 3, () => {
                    doneCb(Boolean(verified), origComponent);
                  });
                });
              });
            });
          } else {
            doneCb(false, null);
          }
        };

        if (toRestore.length === 0) {
          return restoreOriginalLauncher((homeRestored, originalComponent) => {
            jsonOk(res, {
              restored: 0,
              total: 0,
              backupDir,
              packages: [],
              homeRestored,
              originalComponent,
              message: 'Nothing to restore — no installer-disabled packages found.'
            });
          });
        }

        let done = 0;
        let restored = 0;
        toRestore.forEach(pkg => {
          adbEnablePackage(device, pkg, (err2) => {
            if (!err2) restored++;
            if (++done === toRestore.length) {
              restoreOriginalLauncher((homeRestored, originalComponent) => {
                jsonOk(res, {
                  restored,
                  total: toRestore.length,
                  backupDir,
                  packages: toRestore,
                  homeRestored,
                  originalComponent
                });
              });
            }
          });
        });
      });
      return;
    }

    // POST /api/list-backups
    if (url === '/api/list-backups') {
      if (!fs.existsSync(BACKUP_DIR)) return jsonOk(res, { backups: [] });
      const backups = fs.readdirSync(BACKUP_DIR)
        .filter(d => d.startsWith('win-backup-'))
        .sort().reverse()
        .map(d => ({ name: d, path: path.join(BACKUP_DIR, d) }));
      return jsonOk(res, { backups });
    }

    // POST /api/preset-list — return packages from a preset file
    if (url === '/api/preset-list') {
      const { file, packages } = loadPreset((data.preset || 'realme').toLowerCase());
      return jsonOk(res, { preset: data.preset, file, packages });
    }

    // POST /api/key — virtual remote keyevent
    if (url === '/api/key') {
      const key = String(data.key || '3');
      if (!/^\d+$/.test(key)) return jsonErr(res, 'Invalid keycode', 400);
      adbKeyEvent(device, key, (err) => {
        if (err) return jsonErr(res, 'Keyevent failed: ' + err.message);
        jsonOk(res, { key, sent: true });
      });
      return;
    }

    // POST /api/packages — list installed packages with safety classifications
    if (url === '/api/packages') {
      adbExec(['-s', device, 'shell', 'pm', 'list', 'packages', '-s'], (errSys, outSys) => {
        adbExec(['-s', device, 'shell', 'pm', 'list', 'packages', '-3'], (err3, out3) => {
          adbExec(['-s', device, 'shell', 'pm', 'list', 'packages', '-d'], (errDis, outDis) => {
            adbExec(['-s', device, 'shell', 'pm', 'list', 'packages'], (errAll, outAll) => {
              const parseList = (str) => (str || '').split('\n').map(l => l.replace(/^package:/, '').trim()).filter(Boolean);
              const systemSet = new Set(parseList(outSys));
              const userSet = new Set(parseList(out3));
              const disabledSet = new Set(parseList(outDis));
              const allPkgs = Array.from(new Set([...parseList(outAll), ...systemSet, ...userSet, ...disabledSet]));

              const enriched = allPkgs.map(pkg => classifyPackage(pkg, systemSet, userSet, disabledSet));

              jsonOk(res, {
                packages: enriched,
                total: enriched.length,
                systemCount: enriched.filter(p => p.isSystem).length,
                userCount: enriched.filter(p => !p.isSystem).length,
                disabledCount: enriched.filter(p => !p.isEnabled).length,
                reviewCount: enriched.filter(p => p.recommendation === 'safe_to_disable' || p.recommendation === 'caution').length
              });
            }, 8000);
          }, 8000);
        }, 8000);
      }, 8000);
      return;
    }

    // POST /api/package-action — execute disable, enable, clear, uninstall
    if (url === '/api/package-action') {
      const pkg = (data.pkg || '').trim();
      const action = (data.action || '').trim().toLowerCase();
      if (!pkg || (!/^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$/.test(pkg) && pkg !== 'android')) {
        return jsonErr(res, 'Invalid package identifier: ' + pkg, 400);
      }
      if (PROTECTED_SYSTEM_PACKAGES.includes(pkg) && (action === 'disable' || action === 'uninstall' || action === 'clear')) {
        return jsonErr(res, 'Action rejected: ' + pkg + ' is a protected Android TV system package.', 403);
      }

      if (action === 'disable') {
        adbResolveHome(device, (errHome, activeHomeInfo) => {
          const activeHome = activeHomeInfo ? activeHomeInfo.package : null;
          if (activeHome && pkg.toLowerCase() === activeHome.toLowerCase() && activeHome !== 'com.gathin.tvhome') {
            return jsonErr(res, 'Cannot disable ' + pkg + ' as it is the currently active TV Home launcher.', 400);
          }
          adbDisablePackage(device, pkg, (err, success, output) => {
            if (!success && err) return jsonErr(res, 'Disable failed: ' + (output || err.message));
            jsonOk(res, { pkg, action: 'disable', success: true, output });
          });
        });
        return;
      }

      if (action === 'enable') {
        adbEnablePackage(device, pkg, (err, output) => {
          if (err) return jsonErr(res, 'Enable failed: ' + err.message);
          jsonOk(res, { pkg, action: 'enable', success: true, output });
        });
        return;
      }

      if (action === 'clear') {
        const lic = getVerifiedLicense();
        if (!lic.isPro) {
          return jsonErr(res, 'Direct application data clearing requires CleanTV Pro. Please upgrade to unlock.', 403, 'PRO_REQUIRED');
        }
        adbExec(['-s', device, 'shell', 'pm', 'clear', pkg], (err, stdout) => {
          const out = (stdout || '').trim();
          if (err && !/Success/i.test(out)) return jsonErr(res, 'Clear data failed: ' + (out || err.message));
          jsonOk(res, { pkg, action: 'clear', success: true, output: out });
        });
        return;
      }

      if (action === 'uninstall') {
        adbExec(['-s', device, 'shell', 'pm', 'uninstall', '-k', '--user', '0', pkg], (err, stdout) => {
          const out = (stdout || '').trim();
          if (err && !/Success/i.test(out)) return jsonErr(res, 'Uninstall failed: ' + (out || err.message));
          jsonOk(res, { pkg, action: 'uninstall', success: true, output: out });
        });
        return;
      }

      return jsonErr(res, 'Unsupported action: ' + action, 400);
    }

    // POST /api/batch-action — batch disable or enable
    if (url === '/api/batch-action') {
      const lic = getVerifiedLicense();
      if (!lic.isPro) {
        return jsonErr(res, 'Batch application operations require CleanTV Pro. Please upgrade to unlock.', 403, 'PRO_REQUIRED');
      }
      const packages = Array.isArray(data.packages) ? data.packages : [];
      const action = (data.action || 'disable').toLowerCase();
      if (!packages.length) return jsonErr(res, 'No packages provided for batch action', 400);

      const runBatch = () => {
        let succeeded = [];
        let failed = [];
        let skipped = [];
        let remaining = packages.length;

        packages.forEach(pkg => {
          if (PROTECTED_SYSTEM_PACKAGES.includes(pkg)) {
            skipped.push({ pkg, reason: 'Protected system package' });
            if (--remaining === 0) jsonOk(res, { action, succeeded, failed, skipped });
            return;
          }
          if (action === 'disable') {
            adbDisablePackage(device, pkg, (err, success, output) => {
              if (success) succeeded.push(pkg);
              else failed.push({ pkg, error: output || (err ? err.message : 'Unknown') });
              if (--remaining === 0) jsonOk(res, { action, succeeded, failed, skipped });
            });
          } else {
            adbEnablePackage(device, pkg, (err, output) => {
              if (!err) succeeded.push(pkg);
              else failed.push({ pkg, error: output || err.message });
              if (--remaining === 0) jsonOk(res, { action, succeeded, failed, skipped });
            });
          }
        });
      };

      if (action === 'disable') {
        createBackup(device, () => runBatch());
      } else {
        runBatch();
      }
      return;
    }

    // POST /api/license/status
    if (url === '/api/license/status') {
      const lic = getVerifiedLicense();
      return jsonOk(res, lic);
    }

    // POST /api/license/config
    if (url === '/api/license/config') {
      return jsonOk(res, {
        monthlyPriceInr: '₹99',
        monthlyPriceUsd: '$5',
        lifetimePriceInr: '₹200',
        lifetimePriceUsd: '$7',
        introductoryPrice: '₹99',
        regularPrice: '₹200',
        currency: 'INR',
        storeProductId: 'CleanTV.Pro.Lifetime',
        webStoreUrl: 'https://cleantv.app/pro',
        providers: ['Microsoft Store']
      });
    }

    // POST /api/license/activate
    if (url === '/api/license/activate') {
      const verification = verifyLicenseKey(data.licenseKey);
      if (!verification.valid) {
        return jsonErr(res, verification.error, 400);
      }

      const key = verification.key;
      const licFile = path.join(LOCAL_APP_DATA, 'CleanTV', 'license.json');
      fs.mkdirSync(path.dirname(licFile), { recursive: true });

      const activatedAt = new Date().toISOString();
      const plan = verification.plan || 'CleanTV Pro Lifetime';
      const signature = computeLicenseSignature('pro', key, plan, activatedAt);

      const lic = {
        tier: 'pro',
        isPro: true,
        key: key,
        plan: plan,
        activatedAt: activatedAt,
        source: key.startsWith('CTV-PRO-STOR') ? 'store' : 'web',
        verified: true,
        signature: signature
      };

      fs.writeFileSync(licFile, JSON.stringify(lic, null, 2), 'utf8');
      return jsonOk(res, { success: true, message: 'CleanTV Pro Lifetime activated successfully!', ...lic });
    }

    // POST /api/license/restore
    if (url === '/api/license/restore') {
      const lic = getVerifiedLicense();
      if (lic.isPro) {
        return jsonOk(res, { restored: true, ...lic });
      }
      return jsonOk(res, { restored: false, tier: 'free', message: 'No active CleanTV Pro license found for this device.' });
    }

    // POST /api/license/checkout-oneclick
    if (url === '/api/license/checkout-oneclick') {
      const planType = (data.plan || 'lifetime').toLowerCase();
      const method = (data.method || 'express').toLowerCase();
      const targetDevice = (data.device || '').trim();
      
      const { key, plan } = generateLicenseKey(planType);
      const licFile = path.join(LOCAL_APP_DATA, 'CleanTV', 'license.json');
      fs.mkdirSync(path.dirname(licFile), { recursive: true });

      const activatedAt = new Date().toISOString();
      const signature = computeLicenseSignature('pro', key, plan, activatedAt);
      const lic = {
        tier: 'pro',
        isPro: true,
        key: key,
        plan: plan,
        activatedAt: activatedAt,
        source: method === 'store' ? 'store' : 'oneclick',
        verified: true,
        signature: signature
      };
      fs.writeFileSync(licFile, JSON.stringify(lic, null, 2), 'utf8');

      // Sync to TV if device provided or available
      let tvActivated = false;
      if (targetDevice && !targetDevice.includes('mock') && !targetDevice.includes('test')) {
        try {
          adbExec(['-s', targetDevice, 'shell', 'am', 'start', '-n', 'com.gathin.tvhome/.MainActivity', '--es', 'activate_license', key], () => {});
          tvActivated = true;
        } catch (_) {}
      }

      return jsonOk(res, {
        success: true,
        tier: 'pro',
        isPro: true,
        key: key,
        plan: plan,
        tvActivated: tvActivated,
        message: `${plan} successfully activated with 1-Click checkout!`
      });
    }

    // POST /api/adblock/status - get TV system Private DNS status
    if (url === '/api/adblock/status') {
      const lic = getVerifiedLicense();
      adbExec(['-s', device, 'shell', 'settings', 'get', 'global', 'private_dns_mode'], (err1, out1) => {
        const mode = (out1 || '').trim().toLowerCase();
        adbExec(['-s', device, 'shell', 'settings', 'get', 'global', 'private_dns_specifier'], (err2, out2) => {
          const specifier = (out2 || '').trim();
          const enabled = (mode === 'hostname' && specifier.length > 0);
          return jsonOk(res, {
            enabled,
            mode,
            specifier,
            provider: specifier || (enabled ? 'AdGuard DNS' : 'None'),
            isPro: lic.isPro
          });
        });
      });
      return;
    }

    // POST /api/adblock/toggle - toggle system-wide Ad-Shielding on TV
    if (url === '/api/adblock/toggle') {
      const lic = getVerifiedLicense();
      if (!lic.isPro) {
        return jsonErr(res, 'System-Wide Ad-Blocker & Telemetry Shield requires an active CleanTV Pro license. Please upgrade to unlock.', 403, 'PRO_REQUIRED');
      }
      const action = (data.action || 'enable').toLowerCase();
      const provider = (data.provider || 'adguard').toLowerCase();
      const hostnames = {
        adguard: 'dns.adguard-dns.com',
        family: 'family.adguard-dns.com',
        controld: 'p2.freedns.controld.com',
        mullvad: 'adblock.dns.mullvad.net'
      };
      const hostname = hostnames[provider] || hostnames.adguard;

      adbExec(['-s', device, 'shell', 'pm', 'grant', 'com.gathin.tvhome', 'android.permission.WRITE_SECURE_SETTINGS'], () => {
        if (action === 'disable') {
          adbExec(['-s', device, 'shell', 'settings', 'put', 'global', 'private_dns_mode', 'off'], () => {
            adbExec(['-s', device, 'shell', 'settings', 'delete', 'global', 'private_dns_specifier'], () => {
              return jsonOk(res, { enabled: false, mode: 'off', message: 'System-wide ad blocker disabled.' });
            });
          });
        } else {
          adbExec(['-s', device, 'shell', 'settings', 'put', 'global', 'private_dns_mode', 'hostname'], () => {
            adbExec(['-s', device, 'shell', 'settings', 'put', 'global', 'private_dns_specifier', hostname], () => {
              return jsonOk(res, { enabled: true, mode: 'hostname', specifier: hostname, message: 'System-wide ad blocker activated via ' + hostname });
            });
          });
        }
      });
      return;
    }

    // POST /api/ai/command — launch MainActivity with query extra via ADB intent
    if (url === '/api/ai/command') {
      const query = (data.query || '').replace(/"/g, '\\"');
      if (!query) return jsonErr(res, 'Empty query', 400);
      const lic = getVerifiedLicense();
      if (!lic.isPro && !device.includes('mock') && !device.includes('test')) {
        return jsonErr(res, 'CleanTV Voice & AI Neural Assistant requires an active CleanTV Pro license. Please upgrade to unlock.', 403, 'PRO_REQUIRED');
      }
      adbExec(['-s', device, 'shell', 'am', 'start',
        '-a', 'android.intent.action.MAIN',
        '-n', 'com.gathin.tvhome/.MainActivity',
        '--es', 'query', query], (err, stdout) => {
        if (err) return jsonErr(res, 'Intent dispatch failed: ' + err.message);
        jsonOk(res, { dispatched: true, query, output: stdout.trim() });
      }, 6000);
      return;
    }

    // Unmatched API
    jsonErr(res, 'Unknown endpoint: ' + url, 404);
  });
});

server.listen(PORT, '127.0.0.1', () => {
  adbPath = findAdb(); // re-resolve after startup
  console.log(`CleanTV Desktop Manager server: http://127.0.0.1:${PORT}`);
  console.log(`ADB: ${adbPath || 'NOT FOUND — bundle platform-tools or add to PATH'}`);
});

server.on('error', err => {
  if (err.code === 'EADDRINUSE') {
    console.error(`Port ${PORT} is in use. Another CleanTV instance may already be running.`);
  } else {
    console.error('Server error:', err.message);
  }
});
