// Real Windows Backend Integration Test
// Spawns CleanTV backend with mock ADB controller and tests all endpoints over real HTTP
const http = require('http');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { spawn, execSync } = require('child_process');

const PORT = 59822;
console.log('[TEST] Starting CleanTV Backend Integration Tests...\n');

let serverProc = null;
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
  const mockAdbCs = path.resolve(__dirname, 'mock-adb.cs');
  const mockAdbExe = path.resolve(__dirname, 'mock-adb.exe');

  // Clean mock state
  const stateFile = path.join(os.tmpdir(), 'cleantv_mock_disabled_pkgs.txt');
  try { if (fs.existsSync(stateFile)) fs.unlinkSync(stateFile); } catch (e) {}

  if (!fs.existsSync(mockAdbExe)) {
    const cscPath = 'C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe';
    execSync(`"${cscPath}" /nologo /target:exe /out:"${mockAdbExe}" "${mockAdbCs}"`);
  }

  // 1. Start backend process with mock ADB
  const mainScript = path.resolve(__dirname, '..', 'src', 'main.js');
  const env = { ...process.env, CLEANTV_ADB_PATH: mockAdbExe };
  serverProc = spawn('node', [mainScript], { env, stdio: 'pipe' });

  serverProc.stdout.on('data', d => process.stdout.write('  [Server] ' + d));
  serverProc.stderr.on('data', d => process.stderr.write('  [Server ERR] ' + d));

  // Wait 1.5 seconds for server to bind
  await new Promise(r => setTimeout(r, 1500));

  try {
    // Test 1: Static UI HTML serving with injected session token
    const htmlRes = await get('/index.html');
    assert(htmlRes.status === 200, 'GET /index.html returns HTTP 200');
    assert(htmlRes.body.includes('CleanTV Home Desktop Manager'), 'index.html contains app title');
    assert(htmlRes.body.includes('cleantv-token'), 'index.html contains injected session token');

    const tokenMatch = htmlRes.body.match(/<meta name="cleantv-token" content="([a-zA-Z0-9_-]+)">/);
    assert(tokenMatch && tokenMatch[1], 'Extracted session token from HTML');
    const token = tokenMatch ? tokenMatch[1] : '';
    const authHeaders = { 'X-CleanTV-Token': token };

    // Test 2: Unauthenticated request rejected with HTTP 401
    const unauthRes = await post('/api/adb-status', {});
    assert(unauthRes.status === 401, 'POST /api/adb-status without token is rejected with HTTP 401');

    // Test 3: Authenticated ADB Status endpoint
    const adbRes = await post('/api/adb-status', {}, authHeaders);
    assert(adbRes.status === 200, 'POST /api/adb-status returns HTTP 200');
    assert(typeof adbRes.data.adbFound === 'boolean', 'adb-status returns boolean adbFound');
    assert(adbRes.data.adbFound === true, 'adb-status detects available ADB');

    // Test 4: Preset List endpoint
    const presetRes = await post('/api/preset-list', { preset: 'realme' }, authHeaders);
    assert(presetRes.status === 200, 'POST /api/preset-list returns HTTP 200');
    assert(presetRes.data.packages && presetRes.data.packages.length > 0, 'preset-list returns active packages');
    assert(presetRes.data.file === 'realme-changhong.txt', 'preset-list maps realme to realme-changhong.txt');

    // Test 5: All other presets
    for (const pr of ['sony', 'xiaomi', 'tcl', 'google', 'hisense', 'generic']) {
      const pRes = await post('/api/preset-list', { preset: pr }, authHeaders);
      assert(pRes.data && pRes.data.packages && pRes.data.packages.length > 0, `preset-list handles preset "${pr}" (${pRes.data.packages.length} pkgs)`);
    }

    // Test 6: Fail-closed launcher check on unresolved-home-device
    const noHomeRes = await post('/api/debloat', { device: 'unresolved-home-device', preset: 'xiaomi' }, authHeaders);
    assert(noHomeRes.status === 500, 'POST /api/debloat fails closed with HTTP 500 when active launcher cannot be identified');

    // Test 6b: Foreground App Is Not HOME (YouTube on screen test)
    const youtubeForegroundRes = await post('/api/debloat', { device: 'foreground-app-device', preset: 'xiaomi' }, authHeaders);
    assert(youtubeForegroundRes.status === 500, 'POST /api/debloat aborts with HTTP 500 when foreground app is YouTube and HOME launcher cannot be resolved');
    assert(youtubeForegroundRes.data && youtubeForegroundRes.data.message && youtubeForegroundRes.data.message.includes('active HOME launcher'), 'Aborts cleanly without mistaking YouTube for a HOME launcher');

    // Test 7: Controlled debloat with active launcher protection
    const debloatRes = await post('/api/debloat', { device: 'mock-tv-device', preset: 'xiaomi' }, authHeaders);
    assert(debloatRes.status === 200, 'POST /api/debloat completes with HTTP 200 on mock device');
    assert(debloatRes.data.disabled > 0, `Debloated ${debloatRes.data.disabled} packages safely`);
    assert(debloatRes.data.skipped > 0, `Protected active launcher (skipped: ${debloatRes.data.skipped})`);
    const backupDir = debloatRes.data.backupDir;
    assert(backupDir && fs.existsSync(backupDir), `Pre-debloat safety backup directory verified on disk: ${backupDir}`);
    assert(fs.existsSync(path.join(backupDir, 'original-home.txt')), 'Backup recorded original-home.txt');
    assert(fs.readFileSync(path.join(backupDir, 'original-home.txt'), 'utf8').trim() === 'com.mitv.tvhome', 'original-home.txt recorded exact active launcher com.mitv.tvhome');
    assert(fs.existsSync(path.join(backupDir, 'debloated-packages.txt')), 'Backup recorded debloated-packages.txt');

    // Test 8: Full backup-and-restore cycle
    const restoreRes = await post('/api/restore', { device: 'mock-tv-device' }, authHeaders);
    assert(restoreRes.status === 200, 'POST /api/restore completes with HTTP 200 using pre-debloat backup');
    assert(restoreRes.data && restoreRes.data.restored > 0, `Restore successfully re-enabled ${restoreRes.data.restored} package(s)`);

    // Test 9: Safe restore rejection when no backup exists for device
    const restoreSafeRes = await post('/api/restore', { device: 'unbacked-mock-tv-999' }, authHeaders);
    assert(restoreSafeRes.status === 404, 'POST /api/restore safely fails with HTTP 404 when no backup exists for device');

    // Test 10: Key endpoint validation
    const invalidKeyRes = await post('/api/key', { device: 'mock-tv-device', key: 'invalid_code' }, authHeaders);
    assert(invalidKeyRes.status === 400, 'POST /api/key rejects non-numeric keycode with HTTP 400');

    // Test 11: AI Command endpoint validation
    const emptyAiRes = await post('/api/ai/command', { device: 'mock-tv-device', query: '' }, authHeaders);
    assert(emptyAiRes.status === 400, 'POST /api/ai/command rejects empty query with HTTP 400');

    // Test 12: Sideload install endpoint succeeds with mock ADB
    const instRes = await post('/api/install', { device: 'mock-tv-device' }, authHeaders);
    assert(instRes.status === 200, 'POST /api/install succeeds on mock device');

    console.log('\n=======================================');
    if (failed) {
      console.error('[FAILED] One or more integration tests failed.');
      process.exit(1);
    } else {
      console.log('[SUCCESS] All Backend Integration Tests PASSED.');
      process.exit(0);
    }
  } finally {
    if (serverProc) {
      serverProc.kill();
    }
    try { if (fs.existsSync(stateFile)) fs.unlinkSync(stateFile); } catch (e) {}
  }
}

runTests().catch(err => {
  console.error('Test runner fatal error:', err);
  if (serverProc) serverProc.kill();
  process.exit(1);
});
