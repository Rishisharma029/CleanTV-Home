// CleanTV Home Desktop Manager - Backend Server (Real ADB Implementation)
// All API endpoints perform genuine ADB operations. Zero mock responses.
'use strict';

const http    = require('http');
const fs      = require('fs');
const path    = require('path');
const os      = require('os');
const { exec, execFile, execFileSync } = require('child_process');

const PORT    = 59822;
const APP_DIR = path.resolve(__dirname, '..');          // windows/
const ROOT_DIR = path.resolve(__dirname, '..', '..');   // repo root
const UI_DIR  = path.join(APP_DIR, 'ui');

// ─── ADB Resolution ─────────────────────────────────────────────────────────
// Priority: 1) bundled in windows/platform-tools/  2) PATH  3) error

function findAdb() {
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

  adbExec(['-s', device, 'shell', 'pm', 'list', 'packages'], (err, stdout) => {
    if (!err) fs.writeFileSync(path.join(dir, 'packages-all.txt'), stdout || '');
  }, 8000);

  adbExec(['-s', device, 'shell', 'pm', 'list', 'packages', '-d'], (err, stdout) => {
    if (!err) fs.writeFileSync(path.join(dir, 'packages-disabled.txt'), stdout || '');
    adbExec(['-s', device, 'shell', 'getprop'], (err2, stdout2) => {
      if (!err2) fs.writeFileSync(path.join(dir, 'getprop.txt'), stdout2 || '');
      // Verify files
      const fDis = path.join(dir, 'packages-disabled.txt');
      const fAll = path.join(dir, 'packages-all.txt');
      if (fs.existsSync(fDis) && fs.existsSync(fAll)) {
        callback(null, dir, ts);
      } else {
        callback(new Error('Failed to verify safety backup files on disk'));
      }
    }, 8000);
  }, 8000);
}

function findLatestBackup(device) {
  if (!fs.existsSync(BACKUP_DIR)) return null;
  const devKey = sanitizeDeviceKey(device);
  let dirs = fs.readdirSync(BACKUP_DIR)
    .filter(d => d.startsWith(`win-backup-${devKey}-`))
    .sort().reverse();
  if (dirs.length === 0) {
    dirs = fs.readdirSync(BACKUP_DIR)
      .filter(d => d.startsWith('win-backup-'))
      .sort().reverse();
  }
  return dirs.length ? path.join(BACKUP_DIR, dirs[0]) : null;
}

// ─── JSON response helpers ───────────────────────────────────────────────────

function jsonOk(res, data) {
  const body = JSON.stringify({ status: 'ok', ...data });
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(body);
}

function jsonErr(res, message, code) {
  const body = JSON.stringify({ status: 'error', message });
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
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

  const url = req.url.split('?')[0];

  // ── Static file serving ──
  if (!url.startsWith('/api/')) {
    let filePath = path.join(UI_DIR, url === '/' ? 'index.html' : url);
    if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
      const ext = path.extname(filePath);
      const mimes = { '.html':'text/html','.css':'text/css','.js':'application/javascript','.json':'application/json','.png':'image/png','.svg':'image/svg+xml','.ico':'image/x-icon' };
      res.writeHead(200, { 'Content-Type': mimes[ext] || 'application/octet-stream' });
      fs.createReadStream(filePath).pipe(res);
    } else {
      res.writeHead(404); res.end('Not Found');
    }
    return;
  }

  // ── API Routes ──
  parseBody(req, (_, data) => {
    const device = (data.device || '192.168.0.159:5555').trim();

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

        // Query active home launcher
        adbExec(['-s', device, 'shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME'], (errHome, homeOut) => {
          let activeHome = '';
          const m = (homeOut || '').match(/\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)\//);
          if (m) activeHome = m[1].trim();
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
      const backupDir = data.backupDir || findLatestBackup('win-backup-');
      if (!backupDir || !fs.existsSync(backupDir)) {
        return jsonErr(res, 'No backup found. Run debloat first to create a backup.', 404);
      }

      const preDisabledFile = path.join(backupDir, 'packages-disabled.txt');
      let disabledBefore = [];
      if (fs.existsSync(preDisabledFile)) {
        disabledBefore = fs.readFileSync(preDisabledFile, 'utf8').split('\n')
          .map(l => l.replace(/^package:/, '').trim()).filter(Boolean);
      }

      adbListDisabledPackages(device, (err, currentlyDisabled) => {
        if (err) return jsonErr(res, 'Could not list packages: ' + err.message);

        const toRestore = currentlyDisabled.filter(p => !disabledBefore.includes(p));
        if (toRestore.length === 0) {
          return jsonOk(res, { restored: 0, message: 'Nothing to restore — no installer-disabled packages found.' });
        }

        let done = 0;
        let restored = 0;
        toRestore.forEach(pkg => {
          adbEnablePackage(device, pkg, (err2) => {
            if (!err2) restored++;
            if (++done === toRestore.length) {
              jsonOk(res, { restored, total: toRestore.length, backupDir, packages: toRestore });
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

    // POST /api/ai/command — launch MainActivity with query extra via ADB intent
    if (url === '/api/ai/command') {
      const query = (data.query || '').replace(/"/g, '\\"');
      if (!query) return jsonErr(res, 'Empty query', 400);
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
