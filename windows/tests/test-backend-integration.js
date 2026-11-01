// Real Windows Backend Integration Test
// Spawns CleanTV backend and tests all endpoints over real HTTP
const http = require('http');
const path = require('path');
const { spawn } = require('child_process');

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
  // 1. Start backend process
  const mainScript = path.resolve(__dirname, '..', 'src', 'main.js');
  serverProc = spawn('node', [mainScript], { stdio: 'pipe' });

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

    // Test 6: Safe restore rejection when no backup exists for device
    const restoreSafeRes = await post('/api/restore', { device: '192.0.2.99:5555' }, authHeaders);
    assert(restoreSafeRes.status === 404, 'POST /api/restore safely fails with HTTP 404 when no backup exists for device');

    // Test 7: Key endpoint validation
    const invalidKeyRes = await post('/api/key', { device: '127.0.0.1:5555', key: 'invalid_code' }, authHeaders);
    assert(invalidKeyRes.status === 400, 'POST /api/key rejects non-numeric keycode with HTTP 400');

    // Test 8: AI Command endpoint validation
    const emptyAiRes = await post('/api/ai/command', { device: '127.0.0.1:5555', query: '' }, authHeaders);
    assert(emptyAiRes.status === 400, 'POST /api/ai/command rejects empty query with HTTP 400');

    // Test 9: Sideload install endpoint rejects unreachable device gracefully
    const instRes = await post('/api/install', { device: '192.0.2.1:5555' }, authHeaders);
    assert(instRes.status === 500 || instRes.data.status === 'error', 'POST /api/install gracefully handles unreachable device');

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
  }
}

runTests().catch(err => {
  console.error('Test runner fatal error:', err);
  if (serverProc) serverProc.kill();
  process.exit(1);
});
