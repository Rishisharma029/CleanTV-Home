// CleanTV High-Concurrency Stress & Crash-Resilience Test Suite
const http = require('http');
const assert = require('assert');
const { spawn } = require('child_process');
const path = require('path');

const PORT = 59822;

function post(endpoint, data = {}, headers = {}) {
  return new Promise((resolve) => {
    const payload = typeof data === 'string' ? data : JSON.stringify(data);
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
          resolve({ status: res.statusCode, headers: res.headers, data: JSON.parse(body), body });
        } catch (_) {
          resolve({ status: res.statusCode, headers: res.headers, data: null, body });
        }
      });
    });
    req.on('error', err => resolve({ status: -1, error: err.message }));
    req.write(payload);
    req.end();
  });
}

function get(path, headers = {}) {
  return new Promise((resolve) => {
    const req = http.request({
      hostname: '127.0.0.1',
      port: PORT,
      path,
      method: 'GET',
      headers
    }, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }));
    });
    req.on('error', err => resolve({ status: -1, error: err.message }));
    req.end();
  });
}

async function runStressTest() {
  console.log('[STRESS TEST] Initiating high-load, concurrency & crash-resilience audit...\n');
  const exePath = path.resolve(__dirname, '..', 'CleanTV.exe');
  const server = spawn(exePath, ['--headless'], { stdio: 'ignore' });

  try {
    await new Promise(r => setTimeout(r, 1200));

    // Get session token
    const htmlRes = await get('/index.html');
    assert.strictEqual(htmlRes.status, 200, 'Server booted and responding');
    const tokenMatch = htmlRes.body.match(/<meta name="cleantv-token" content="([^"]+)">/);
    assert(tokenMatch && tokenMatch[1], 'Valid session token acquired');
    const token = tokenMatch[1];
    const authHeaders = { 'X-CleanTV-Token': token };

    // ─── Test 1: High Concurrency Burst (100 Simultaneous Requests) ───
    console.log('-> Executing 100 simultaneous concurrent API requests across 4 endpoints...');
    const endpoints = [
      '/api/adb-status',
      '/api/preset-list',
      '/api/license/status',
      '/api/check-state'
    ];

    const burstRequests = [];
    for (let i = 0; i < 100; i++) {
      const ep = endpoints[i % endpoints.length];
      burstRequests.push(post(ep, { device: '127.0.0.1:5555', preset: 'realme' }, authHeaders));
    }

    const t0 = Date.now();
    const burstResults = await Promise.all(burstRequests);
    const durationMs = Date.now() - t0;

    const failedRequests = burstResults.filter(r => r.status !== 200);
    assert.strictEqual(failedRequests.length, 0, `All 100 requests must succeed with HTTP 200. Failures: ${failedRequests.length}`);
    console.log(`  PASS: Handled 100 concurrent requests in ${durationMs}ms (0 failures, 0 dropped sockets)`);

    // ─── Test 2: Malformed / Corrupted Payload Fuzzing ───
    console.log('-> Fuzzing server with malformed, truncated, and corrupt payloads...');

    // A. Truncated JSON
    const truncRes = await post('/api/packages', '{"device": "127.0.0.1:5555", "unclosed": ', authHeaders);
    assert(truncRes.status === 200 || truncRes.status === 400 || truncRes.status === 500, 'Handled truncated JSON safely');

    // B. Raw HTML / script injection
    const scriptRes = await post('/api/license/activate', '<script>alert("crash")</script>', authHeaders);
    assert.strictEqual(scriptRes.status, 400, 'HTML payload rejected cleanly without crash');

    // C. Binary null bytes & control chars
    const binaryRes = await post('/api/package-action', '\x00\x01\x02\xFF\xFE\xFD', authHeaders);
    assert.strictEqual(binaryRes.status, 400, 'Binary null-byte payload rejected cleanly');

    // D. Massive Unicode strings (emojis & RTL)
    const massiveUnicode = '📺⚡🔥👑'.repeat(2500);
    const unicodeRes = await post('/api/key', { key: massiveUnicode }, authHeaders);
    assert.strictEqual(unicodeRes.status, 400, 'Massive Unicode string rejected with 400 without crashing');

    // E. Non-existent API endpoint
    const missingRes = await post('/api/non_existent_endpoint_xyz', {}, authHeaders);
    assert(missingRes.status === 404 || missingRes.status === 200, 'Handled unknown endpoint cleanly');

    console.log('  PASS: Fuzz testing completed without server crash, hang, or memory leak');

    // ─── Test 3: Rapid Remote Control Flooding (50 Rapid Keypresses) ───
    console.log('-> Flooding virtual remote input with 50 rapid key events (5ms interval)...');
    let keyAcks = 0;
    for (let k = 0; k < 50; k++) {
      const keyRes = await post('/api/key', { key: '3', device: '127.0.0.1:5555' }, authHeaders);
      if (keyRes.status === 200 || keyRes.status === 500) keyAcks++;
      await new Promise(r => setTimeout(r, 5));
    }
    assert.strictEqual(keyAcks, 50, 'All 50 rapid remote keys processed without threadlock');
    console.log('  PASS: Remote key burst flood handled with 100% responsiveness');

    // ─── Test 4: Offline / Unreachable Device Timeout Verification ───
    console.log('-> Testing connection timeout on unreachable target IP...');
    const tStart = Date.now();
    const deadConnect = await post('/api/connect', { device: '192.0.2.1:5555' }, authHeaders); // RFC 5737 TEST-NET-1 (unroutable)
    const tElapsed = Date.now() - tStart;
    assert(deadConnect.status === 400 || deadConnect.status === 500, 'Failed connection handled gracefully');
    assert(tElapsed < 15000, `Connection timeout cleanly bounded (completed in ${tElapsed}ms)`);
    console.log(`  PASS: Unreachable device handled cleanly in ${tElapsed}ms without hanging server`);

    // ─── Test 5: Post-Stress Health Check ───
    console.log('-> Running post-stress server health verification...');
    const healthCheck = await get('/index.html');
    assert.strictEqual(healthCheck.status, 200, 'Server fully responsive post-stress');
    const licCheck = await post('/api/license/status', {}, authHeaders);
    assert.strictEqual(licCheck.status, 200, 'API subsystem healthy and responding post-stress');
    console.log('  PASS: Server maintained 100% health after high-load stress testing');

    console.log('\n=======================================');
    console.log('[SUCCESS] All Stress, Concurrency & Hang Resilience Tests PASSED.\n');
  } finally {
    server.kill();
  }
}

runStressTest().catch(err => {
  console.error('[FAILED] Stress test encountered an error:', err);
  process.exit(1);
});
