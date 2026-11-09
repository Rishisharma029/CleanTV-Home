// CleanTV Security Audit & Pen-Test Validation
const http = require('http');
const assert = require('assert');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const PORT = 59822;

function post(endpoint, data = {}, headers = {}) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify(data);
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
    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

function get(path, headers = {}) {
  return new Promise((resolve, reject) => {
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
    req.on('error', reject);
    req.end();
  });
}

async function runAudit() {
  console.log('[SECURITY AUDIT] Testing against backdoors, path traversal, injection & bypasses...\n');
  const exePath = path.resolve(__dirname, '..', 'CleanTV.exe');
  const server = spawn(exePath, ['--headless'], { stdio: 'ignore' });

  try {
    await new Promise(r => setTimeout(r, 1200));

    // Get session token
    const htmlRes = await get('/index.html');
    assert.strictEqual(htmlRes.status, 200, 'Server is alive');
    const tokenMatch = htmlRes.body.match(/<meta name="cleantv-token" content="([^"]+)">/);
    assert(tokenMatch && tokenMatch[1], 'Session token generated and extracted');
    const token = tokenMatch[1];
    const authHeaders = { 'X-CleanTV-Token': token };

    // 1. Path Traversal Test (Escaping ui directory)
    const traverse1 = await get('/..%2f..%2f..%2fWindows%2fwin.ini');
    assert.strictEqual(traverse1.status, 403, 'Path traversal attempt /..%2f..%2fWindows/win.ini blocked with HTTP 403 Forbidden');
    console.log('  PASS: Path traversal (slash encoding) blocked with 403 Forbidden');

    const traverse2 = await get('/..%5c..%5c..%5cWindows%5cwin.ini');
    assert.strictEqual(traverse2.status, 403, 'Path traversal attempt /..%5c..%5c blocked with HTTP 403 Forbidden');
    console.log('  PASS: Path traversal (backslash encoding) blocked with 403 Forbidden');

    // 2. STOR Key Backdoor Bypass Test
    const storAttempt = await post('/api/license/activate', { licenseKey: 'CTV-PRO-STOR-0000' }, authHeaders);
    assert.strictEqual(storAttempt.status, 400, 'STOR key backdoor without checksum is blocked with HTTP 400');
    assert(storAttempt.data.message.includes('checksum'), 'Blocked with invalid checksum error');
    console.log('  PASS: STOR backdoor bypass blocked; strict checksum required');

    // 3. Loose Key Format Bypass Test
    const looseAttempt = await post('/api/license/activate', { licenseKey: 'CTV-' }, authHeaders);
    assert.strictEqual(looseAttempt.status, 400, 'Loose CTV- key prefix blocked with HTTP 400');
    console.log('  PASS: Loose CTV- key prefix blocked');

    // 4. Package Name Injection Test
    const pkgInject = await post('/api/package-action', { pkg: 'com.realme;calc.exe', action: 'disable' }, authHeaders);
    assert.strictEqual(pkgInject.status, 400, 'Package name with shell metacharacters blocked with HTTP 400');
    console.log('  PASS: Shell metacharacters in package names rejected');

    // 5. Unauthenticated Gating Test (Debloat / AI)
    // Downgrade or ensure free tier
    const licFile = path.join(process.env.LOCALAPPDATA, 'CleanTV', 'license.json');
    if (fs.existsSync(licFile)) fs.unlinkSync(licFile);

    const debloatFree = await post('/api/debloat', { preset: 'realme' }, authHeaders);
    assert.strictEqual(debloatFree.status, 403, 'Debloat strictly requires Pro (403)');
    console.log('  PASS: Debloat blocked on Free Tier with 403 PRO_REQUIRED');

    const aiFree = await post('/api/ai/command', { query: 'volume up' }, authHeaders);
    assert.strictEqual(aiFree.status, 403, 'Voice AI strictly requires Pro (403)');
    console.log('  PASS: Voice AI blocked on Free Tier with 403 PRO_REQUIRED');

    // 6. Legitimate Pro Key Activation Test (with formatted JSON spacing)
    const formattedPayload = JSON.stringify({ licenseKey: 'CTV-PRO-8892-98B4' }, null, 2);
    const legitAct = await post('/api/license/activate', JSON.parse(formattedPayload), authHeaders);
    assert.strictEqual(legitAct.status, 200, 'Valid cryptographically signed key activates Pro');
    assert.strictEqual(legitAct.data.isPro, true, 'Pro tier entitlement granted');
    console.log('  PASS: Valid cryptographically signed license activates Pro');

    // 7. DNS Rebinding & Host Header Validation
    const rebinding = await get('/api/adb-status', { Host: 'evil-attacker-website.com' });
    assert.strictEqual(rebinding.status, 400, 'Host header spoofing blocked with HTTP 400 Bad Request');
    console.log('  PASS: Host header validation blocks DNS rebinding attacks');

    // 8. Payload Too Large (DoS Protection)
    const hugePayload = 'A'.repeat(3 * 1024 * 1024);
    const hugeReq = await post('/api/packages', { data: hugePayload }, authHeaders);
    assert.strictEqual(hugeReq.status, 413, 'Payloads exceeding 2MB blocked with HTTP 413 Payload Too Large');
    console.log('  PASS: Large request payload blocked with 413 Payload Too Large');

    console.log('\n[SUCCESS] All Security & Anti-Backdoor penetration tests PASSED.\n');
  } finally {
    server.kill();
  }
}

runAudit().catch(err => {
  console.error('[FAILED] Security audit encountered failure:', err);
  process.exit(1);
});
