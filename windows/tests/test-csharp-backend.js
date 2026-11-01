// CleanTV Shipped C# Windows Backend Integration Test
// Spawns CleanTV.exe --server-only and validates the native C# executable over HTTP
const http = require('http');
const path = require('path');
const { spawn } = require('child_process');

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
  console.log(`  Target binary: ${exePath}`);

  // Spawn CleanTV.exe with --server-only flag (headless mode for automated testing)
  exeProc = spawn(exePath, ['--server-only'], { stdio: 'ignore', windowsHide: true });

  // Give server 1.5 seconds to bind to port 59822
  await new Promise(r => setTimeout(r, 1500));

  try {
    // 1. Static UI serving from C# HttpListener
    const htmlRes = await get('/index.html');
    assert(htmlRes.status === 200, 'CleanTV.exe serves /index.html with HTTP 200');
    assert(htmlRes.body.includes('CleanTV Home Desktop Manager'), 'Served HTML contains correct title');
    assert(htmlRes.body.includes('cleantv-token'), 'Served HTML has injected session security token');

    // 2. CORS validation (evil origins blocked)
    const corsRes = await get('/index.html', { 'Origin': 'http://malicious-website.com' });
    const allowOrigin = corsRes.headers['access-control-allow-origin'];
    assert(allowOrigin === 'null', 'CORS restricts non-loopback origins to null');

    // 3. ADB Status
    const adbRes = await post('/api/adb-status', {});
    assert(adbRes.status === 200, 'POST /api/adb-status returns HTTP 200');
    assert(adbRes.data && adbRes.data.adbFound === true, 'C# backend detects bundled or system ADB');
    assert(typeof adbRes.data.sessionToken === 'string', 'C# backend provides session token');

    // 4. Presets loading across all 7 supported configurations
    const presets = ['realme', 'sony', 'xiaomi', 'tcl', 'google', 'hisense', 'generic'];
    for (const p of presets) {
      const pRes = await post('/api/preset-list', { preset: p });
      assert(pRes.status === 200, `POST /api/preset-list [${p}] returns HTTP 200`);
      assert(pRes.data && pRes.data.packages && pRes.data.packages.length > 0, `Preset [${p}] contains active packages (${pRes.data.packages.length})`);
    }

    // 5. Input validation on /api/key (numeric check)
    const invalidKeyRes = await post('/api/key', { device: '127.0.0.1:5555', key: 'non_numeric' });
    assert(invalidKeyRes.status === 400, 'POST /api/key rejects non-numeric input with HTTP 400');

    // 6. Input validation on /api/ai/command (empty query check)
    const emptyAiRes = await post('/api/ai/command', { device: '127.0.0.1:5555', query: '' });
    assert(emptyAiRes.status === 400, 'POST /api/ai/command rejects empty query with HTTP 400');

    // 7. Sideload APK endpoint handles unreachable endpoint safely
    const instRes = await post('/api/install', { device: '192.0.2.1:5555' });
    assert(instRes.status === 404 || instRes.status === 500, 'POST /api/install safely handles unreachable endpoint without crashing');

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
  }
}

runTests().catch(err => {
  console.error('Fatal error during C# backend test:', err);
  if (exeProc) {
    try { spawn('taskkill', ['/pid', exeProc.pid, '/f', '/t'], { stdio: 'ignore' }); } catch { }
  }
  process.exit(1);
});
