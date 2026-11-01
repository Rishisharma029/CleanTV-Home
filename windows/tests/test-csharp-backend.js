// CleanTV Shipped C# Windows Backend Integration Test
// Spawns CleanTV.exe with mock ADB controller and validates end-to-end functionality over real HTTP
const http = require('http');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { spawn, execSync } = require('child_process');

const PORT = 59822;
console.log('[TEST] Starting CleanTV Shipped C# Backend (CleanTV.exe) Integration Tests...\n');

let exeProc = null;
let failed = false;

function assert(condition, message) {
  if (!condition) {
    console.error(`  FAIL: ${message}`);
    failed = true;
  } else {
    console.log(`  PASS: ${message}`);
  }
}

function post(endpoint, data, headers = {}) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify(data || {});
    const req = http.request({
      hostname: '127.0.0.1',
      port: PORT,
      path: endpoint,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload),
        ...headers
      }
    }, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, headers: res.headers, data: JSON.parse(body || '{}') });
        } catch (e) {
          resolve({ status: res.statusCode, headers: res.headers, raw: body });
        }
      });
    });

    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

function get(endpoint, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: '127.0.0.1',
      port: PORT,
      path: endpoint,
      method: 'GET',
      headers
    }, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }));
    });
    req.on('error', reject);
    req.end();
  });
}

async function runTests() {
  const exePath = path.resolve(__dirname, '..', 'CleanTV.exe');
  const mockAdbCs = path.resolve(__dirname, 'mock-adb.cs');
  const mockAdbExe = path.resolve(__dirname, 'mock-adb.exe');

  // Clean mock persistent state
  const stateFile = path.join(os.tmpdir(), 'cleantv_mock_disabled_pkgs.txt');
  try { if (fs.existsSync(stateFile)) fs.unlinkSync(stateFile); } catch (e) {}

  // 1. Compile Mock ADB if needed
  if (!fs.existsSync(mockAdbExe)) {
    console.log('  Compiling mock-adb.exe for controlled device testing...');
    const cscPath = 'C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe';
    execSync(`"${cscPath}" /nologo /target:exe /out:"${mockAdbExe}" "${mockAdbCs}"`);
  }
  assert(fs.existsSync(mockAdbExe), 'Mock ADB test controller is ready');

  console.log(`  Target binary: ${exePath}`);
  console.log(`  Target mock ADB: ${mockAdbExe}`);

  // 2. Spawn CleanTV.exe with --server-only and --adb=<mockAdbExe>
  const env = { ...process.env, CLEANTV_ADB_PATH: mockAdbExe };
  exeProc = spawn(exePath, ['--server-only', `--adb=${mockAdbExe}`], {
    env,
    stdio: 'ignore',
    windowsHide: true
  });

  // Give server 1.5 seconds to bind to port 59822
  await new Promise(r => setTimeout(r, 1500));

  try {
    // Test 1: Static UI serving from C# HttpListener
    const htmlRes = await get('/index.html');
    assert(htmlRes.status === 200, 'CleanTV.exe serves /index.html with HTTP 200');
    assert(htmlRes.body.includes('CleanTV Home Desktop Manager'), 'Served HTML contains correct title');
    assert(htmlRes.body.includes('cleantv-token'), 'Served HTML has injected session security token');

    // Extract session token from <meta name="cleantv-token" content="...">
    const tokenMatch = htmlRes.body.match(/<meta name="cleantv-token" content="([a-zA-Z0-9_-]+)">/);
    assert(tokenMatch && tokenMatch[1], 'Successfully extracted session security token from served HTML');
    const token = tokenMatch ? tokenMatch[1] : '';
    const authHeaders = { 'X-CleanTV-Token': token };

    // Test 2: CORS validation (evil origins blocked)
    const corsRes = await get('/index.html', { 'Origin': 'http://malicious-website.com' });
    const allowOrigin = corsRes.headers['access-control-allow-origin'];
    assert(allowOrigin === 'null', 'CORS restricts non-loopback origins to null');

    // Test 3: Session Token Enforcement (Positive & Negative tests)
    const noTokenRes = await post('/api/adb-status', {});
    assert(noTokenRes.status === 401, 'POST /api/adb-status without token is rejected with HTTP 401 Unauthorized');

    const badTokenRes = await post('/api/adb-status', {}, { 'X-CleanTV-Token': 'invalid_forged_token' });
    assert(badTokenRes.status === 401, 'POST /api/adb-status with invalid token is rejected with HTTP 401 Unauthorized');

    const unauthPresetRes = await post('/api/preset-list', { preset: 'realme' });
    assert(unauthPresetRes.status === 401, 'POST /api/preset-list without token is rejected with HTTP 401 Unauthorized');

    // Test 4: Authenticated ADB Status
    const adbRes = await post('/api/adb-status', {}, authHeaders);
    assert(adbRes.status === 200, 'POST /api/adb-status with valid token returns HTTP 200');
    assert(adbRes.data && adbRes.data.adbFound === true, 'C# backend detects bundled or mock ADB');

    // Test 5: Presets loading across all 7 supported configurations
    const presets = ['realme', 'sony', 'xiaomi', 'tcl', 'google', 'hisense', 'generic'];
    for (const p of presets) {
      const pRes = await post('/api/preset-list', { preset: p }, authHeaders);
      assert(pRes.status === 200, `POST /api/preset-list [${p}] returns HTTP 200`);
      assert(pRes.data && pRes.data.packages && pRes.data.packages.length > 0, `Preset [${p}] contains active packages (${pRes.data.packages.length})`);
    }

    // Test 6: Fail-Closed Active Launcher Protection
    // When TV's active launcher cannot be resolved via ADB, debloater MUST abort immediately
    const noHomeRes = await post('/api/debloat', { device: 'unresolved-home-device', preset: 'xiaomi' }, authHeaders);
    assert(noHomeRes.status === 500, 'POST /api/debloat fails closed with HTTP 500 when active launcher cannot be identified');
    assert(noHomeRes.data && noHomeRes.data.message && noHomeRes.data.message.includes('active HOME launcher'), 'Fail-closed debloat abort explicitly protects unknown/unresolved launcher');

    // Test 6b: Foreground App Is Not HOME (YouTube on screen test)
    // When dumpsys reports YouTube foreground but Intent resolver fails for HOME, debloater must abort
    const youtubeForegroundRes = await post('/api/debloat', { device: 'foreground-app-device', preset: 'xiaomi' }, authHeaders);
    assert(youtubeForegroundRes.status === 500, 'POST /api/debloat aborts with HTTP 500 when foreground app is YouTube and HOME launcher cannot be resolved');
    assert(youtubeForegroundRes.data && youtubeForegroundRes.data.message && youtubeForegroundRes.data.message.includes('active HOME launcher'), 'Aborts cleanly without mistaking YouTube for a HOME launcher');

    // Test 7: Controlled Debloat Execution & Active Launcher Safety Guard
    // Debloating Xiaomi preset on mock device: com.mitv.tvhome is the active launcher and CleanTV is NOT active
    // Debloater must skip com.mitv.tvhome while safely disabling remaining bloatware
    const debloatRes = await post('/api/debloat', { device: 'mock-tv-device', preset: 'xiaomi' }, authHeaders);
    assert(debloatRes.status === 200, 'POST /api/debloat completes with HTTP 200 on controlled test device');
    assert(debloatRes.data && debloatRes.data.disabled > 0, `Debloater successfully disabled ${debloatRes.data.disabled} OEM bloat packages`);
    assert(debloatRes.data.skipped > 0, `Debloater protected active launcher & system packages (skipped: ${debloatRes.data.skipped})`);

    // Verify com.mitv.tvhome was protected and skipped
    const launcherResult = debloatRes.data.results.find(r => r.pkg === 'com.mitv.tvhome');
    assert(launcherResult && launcherResult.result === 'skipped_active_launcher', 'Active launcher com.mitv.tvhome was safely protected from being disabled');

    // Verify pre-debloat backup directory was created and verified
    const backupDir = debloatRes.data.backupDir;
    assert(backupDir && fs.existsSync(backupDir), `Pre-debloat safety backup directory verified on disk: ${backupDir}`);
    assert(fs.existsSync(path.join(backupDir, 'packages-all.txt')), 'Backup contains packages-all.txt');
    assert(fs.existsSync(path.join(backupDir, 'getprop.txt')), 'Backup contains getprop.txt');
    assert(fs.existsSync(path.join(backupDir, 'original-home.txt')), 'Backup recorded original-home.txt');
    assert(fs.readFileSync(path.join(backupDir, 'original-home.txt'), 'utf8').trim() === 'com.mitv.tvhome', 'original-home.txt recorded exact active launcher com.mitv.tvhome');
    assert(fs.existsSync(path.join(backupDir, 'original-home-component.txt')), 'Backup recorded original-home-component.txt');
    assert(fs.readFileSync(path.join(backupDir, 'original-home-component.txt'), 'utf8').trim() === 'com.mitv.tvhome/com.mitv.tvhome.ui.TvHomeActivity', 'original-home-component.txt recorded exact full component');
    assert(fs.existsSync(path.join(backupDir, 'debloated-packages.txt')), 'Backup recorded debloated-packages.txt');

    // Test 8: Full Successful Backup-and-Restore Cycle
    // Restores the controlled test device using the pre-debloat snapshot created above
    const restoreRes = await post('/api/restore', { device: 'mock-tv-device' }, authHeaders);
    assert(restoreRes.status === 200, 'POST /api/restore completes with HTTP 200 on controlled test device');
    assert(restoreRes.data && restoreRes.data.restored > 0, `Restore operation successfully re-enabled ${restoreRes.data.restored} package(s)`);
    assert(restoreRes.data.homeRestored === true, 'Original home component was successfully restored and verified');
    assert(restoreRes.data.originalComponent === 'com.mitv.tvhome/com.mitv.tvhome.ui.TvHomeActivity', 'Restored exact component com.mitv.tvhome/com.mitv.tvhome.ui.TvHomeActivity without assuming .MainActivity');
    assert(path.resolve(restoreRes.data.backupDir) === path.resolve(backupDir), 'Restore operation used exact device-scoped backup from debloat');

    // Test 8b: Issue A verification - Empty debloated-packages.txt does NOT fall back to baseline diff
    const emptyBackupDir = path.join(process.env.LOCALAPPDATA || path.join(process.env.USERPROFILE, 'AppData', 'Local'), 'CleanTV', 'backups', 'win-backup-empty-debloat-device-20261101-999999');
    fs.mkdirSync(emptyBackupDir, { recursive: true });
    fs.writeFileSync(path.join(emptyBackupDir, 'packages-all.txt'), 'package:com.mitv.screensaver\npackage:com.mitv.tvhome\n');
    fs.writeFileSync(path.join(emptyBackupDir, 'packages-disabled.txt'), '');
    fs.writeFileSync(path.join(emptyBackupDir, 'debloated-packages.txt'), '');
    fs.writeFileSync(path.join(emptyBackupDir, 'original-home-component.txt'), 'com.mitv.tvhome/com.mitv.tvhome.ui.TvHomeActivity');

    const emptyRestoreRes = await post('/api/restore', { device: 'empty-debloat-device' }, authHeaders);
    assert(emptyRestoreRes.status === 200, 'Restore on device with empty debloated-packages.txt returns HTTP 200');
    assert(emptyRestoreRes.data.restored === 0, 'Does NOT fall back to baseline diff when debloated-packages.txt exists but is empty');

    // Test 9: Restore Safe 404 Rejection on Unbacked Device
    const noBackupRestore = await post('/api/restore', { device: 'unbacked-mock-tv-999' }, authHeaders);
    assert(noBackupRestore.status === 404, 'POST /api/restore safely returns HTTP 404 when no backup exists for device');
    assert(noBackupRestore.data.message.includes('No valid backup found'), 'Restore error message explains missing device backup requirement');

    // Test 10: Input validation on /api/key (numeric check)
    const invalidKeyRes = await post('/api/key', { device: 'mock-tv-device', key: 'non_numeric' }, authHeaders);
    assert(invalidKeyRes.status === 400, 'POST /api/key rejects non-numeric input with HTTP 400');

    // Test 11: Input validation on /api/ai/command (empty query check)
    const emptyAiRes = await post('/api/ai/command', { device: 'mock-tv-device', query: '' }, authHeaders);
    assert(emptyAiRes.status === 400, 'POST /api/ai/command rejects empty query with HTTP 400');

    // Test 12: Sideload install endpoint succeeds with mock ADB
    const instRes = await post('/api/install', { device: 'mock-tv-device' }, authHeaders);
    assert(instRes.status === 200, 'POST /api/install executes streamed APK install successfully');
    assert(instRes.data && instRes.data.installed === true, 'Sideload response confirms successful installation');

    console.log('\n=======================================');
    if (failed) {
      console.error('[FAILED] One or more C# backend tests failed.');
      process.exit(1);
    } else {
      console.log('[SUCCESS] All CleanTV.exe C# Backend Integration Tests PASSED.');
      process.exit(0);
    }
  } finally {
    if (exeProc) {
      try {
        exeProc.kill('SIGTERM');
        // On Windows kill process tree
        spawn('taskkill', ['/pid', exeProc.pid, '/f', '/t'], { stdio: 'ignore' });
      } catch { }
    }
    try { if (fs.existsSync(stateFile)) fs.unlinkSync(stateFile); } catch (e) {}
  }
}

runTests().catch(err => {
  console.error('Fatal error during C# backend test:', err);
  if (exeProc) {
    try { spawn('taskkill', ['/pid', exeProc.pid, '/f', '/t'], { stdio: 'ignore' }); } catch { }
  }
  process.exit(1);
});
