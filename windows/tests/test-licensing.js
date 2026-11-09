// CleanTV Complete Licensing Lifecycle & Anti-Tamper Security Test Suite
const http = require('http');
const path = require('path');
const fs = require('fs');
const os = require('os');
const crypto = require('crypto');
const { spawn } = require('child_process');

function getTestEphemeralKey() {
  const salt = Buffer.from([25, 22, 31, 27, 20, 14, 12, 5, 17, 31, 3, 5, 9, 27, 22, 14, 5, 110, 24, 107, 98, 30])
    .map(b => b ^ 0x5A)
    .toString('utf-8');
  const part1 = crypto.randomBytes(2).toString('hex').toUpperCase();
  const sum = crypto.createHash('sha256').update(salt + part1).digest('hex').substring(0, 4).toUpperCase();
  return `CTV-PRO-${part1}-${sum}`;
}

const PORT = 59822;
console.log('[TEST] Starting CleanTV Licensing & Pro Monetization Tests...\n');

let serverProc = null;
let failed = false;

function assert(cond, msg) {
  if (!cond) {
    console.error(`  FAIL: ${msg}`);
    failed = true;
  } else {
    console.log(`  PASS: ${msg}`);
  }
}

function post(endpoint, data, token) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify(data || {});
    const headers = {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(payload)
    };
    if (token) headers['X-CleanTV-Token'] = token;

    const req = http.request({
      hostname: '127.0.0.1',
      port: PORT,
      path: endpoint,
      method: 'POST',
      headers
    }, res => {
      let body = '';
      res.on('data', c => body += c);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, data: JSON.parse(body || '{}') }); }
        catch (e) { resolve({ status: res.statusCode, raw: body }); }
      });
    });
    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

function get(endpoint) {
  return new Promise((resolve, reject) => {
    http.get(`http://127.0.0.1:${PORT}${endpoint}`, res => {
      let body = '';
      res.on('data', c => body += c);
      res.on('end', () => resolve({ status: res.statusCode, body }));
    }).on('error', reject);
  });
}

async function run() {
  const localAppData = process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local');
  const licFile = path.join(localAppData, 'CleanTV', 'license.json');

  // Clean initial license state to simulate fresh Free installation
  try { if (fs.existsSync(licFile)) fs.unlinkSync(licFile); } catch (e) {}

  const mainScript = path.resolve(__dirname, '..', 'src', 'main.js');
  const mockAdbExe = path.resolve(__dirname, 'mock-adb.exe');
  const env = { ...process.env, CLEANTV_ADB_PATH: mockAdbExe };

  serverProc = spawn('node', [mainScript], { env, stdio: 'pipe' });
  await new Promise(r => setTimeout(r, 1500));

  try {
    const htmlRes = await get('/index.html');
    const m = htmlRes.body.match(/name="cleantv-token"\s+content="([^"]+)"/);
    assert(m && m[1], 'Extracted session security token');
    const token = m[1];

    // Test 1: Fresh Free Installation status
    const status1 = await post('/api/license/status', {}, token);
    assert(status1.status === 200, 'POST /api/license/status returns 200');
    assert(status1.data.tier === 'free' && status1.data.isPro === false, 'Fresh installation default is Free Tier');

    // Test 2: Pricing configuration endpoint
    const cfgRes = await post('/api/license/config', {}, token);
    assert(cfgRes.status === 200, 'POST /api/license/config returns 200');
    assert(cfgRes.data.monthlyPriceInr === '₹99' && cfgRes.data.lifetimePriceInr === '₹200', 'Config exposes ₹99 monthly / ₹200 lifetime pricing');

    // Test 3: Free Tier Enforcement: Batch Action is rejected with 403 Forbidden
    const batchFree = await post('/api/batch-action', { device: 'mock-tv-device', packages: ['com.xiaomi.mitv.upgrade'], action: 'disable' }, token);
    assert(batchFree.status === 403, 'POST /api/batch-action rejected on Free Tier with HTTP 403 Forbidden');
    assert(batchFree.data.code === 'PRO_REQUIRED', 'Batch action error reports PRO_REQUIRED error code');

    // Test 4: Free Tier Enforcement: Clear Data is rejected with 403 Forbidden
    const clearFree = await post('/api/package-action', { device: 'mock-tv-device', pkg: 'com.xiaomi.mitv.upgrade', action: 'clear' }, token);
    assert(clearFree.status === 403, 'POST /api/package-action (clear) rejected on Free Tier with HTTP 403 Forbidden');

    // Test 5: Activation rejection: invalid key format
    const badFmt = await post('/api/license/activate', { licenseKey: 'BAD-FORMAT-123' }, token);
    assert(badFmt.status === 400, 'Rejects invalid key format with HTTP 400');
    assert(badFmt.data.message.includes('CTV-PRO') || badFmt.data.message.includes('CTV-MON'), 'Error message instructs on correct CTV-PRO / CTV-MON format');

    // Test 6: Activation rejection: invalid checksum
    const badSum = await post('/api/license/activate', { licenseKey: 'CTV-PRO-AAAA-0000' }, token);
    assert(badSum.status === 400, 'Rejects fabricated key with bad checksum with HTTP 400');
    assert(badSum.data.message.includes('checksum'), 'Error message explicitly reports checksum failure');

    // Test 7: Activation rejection: revoked license
    const revoked = await post('/api/license/activate', { licenseKey: 'CTV-PRO-REV1-9999' }, token);
    assert(revoked.status === 400, 'Rejects revoked license key with HTTP 400');
    assert(revoked.data.message.includes('revoked'), 'Error message reports license is revoked');

    // Test 8: Valid Activation: algorithmic key verification succeeds with dynamic ephemeral key
    const validEphemeralKey = getTestEphemeralKey();
    const validAct = await post('/api/license/activate', { licenseKey: validEphemeralKey }, token);
    assert(validAct.status === 200, 'Valid key activation succeeds with HTTP 200');
    assert(validAct.data.isPro === true && validAct.data.tier === 'pro', 'Entitlement upgraded to Pro');
    assert(fs.existsSync(licFile), 'Verified license persisted to %LOCALAPPDATA%\\CleanTV\\license.json');

    // Test 9: Verified Status after activation
    const statusPro = await post('/api/license/status', {}, token);
    assert(statusPro.data.isPro === true && statusPro.data.verified === true, 'POST /api/license/status confirms verified Pro status');

    // Test 10: Pro Feature Unlock: Batch Action succeeds once Pro is active
    const batchPro = await post('/api/batch-action', { device: 'mock-tv-device', packages: ['com.xiaomi.mitv.upgrade'], action: 'disable' }, token);
    assert(batchPro.status === 200, 'POST /api/batch-action succeeds after Pro activation');

    // Test 11: Anti-Tamper Security: Manual file modification is detected & rejected
    const tampered = JSON.parse(fs.readFileSync(licFile, 'utf8'));
    tampered.key = 'CTV-PRO-HACK-FAKE'; // modify key without matching HMAC signature
    fs.writeFileSync(licFile, JSON.stringify(tampered, null, 2), 'utf8');

    const statusTampered = await post('/api/license/status', {}, token);
    assert(statusTampered.data.isPro === false && statusTampered.data.tier === 'free', 'Tampered license file is detected and downgraded to Free Tier');
    assert(statusTampered.data.tampered === true, 'Tampered flag reported in status');

    // Test 12: Re-activate and test Restore Purchases
    const restoreEphemeralKey = getTestEphemeralKey();
    await post('/api/license/activate', { licenseKey: restoreEphemeralKey }, token);
    const restoreRes = await post('/api/license/restore', {}, token);
    assert(restoreRes.status === 200 && restoreRes.data.restored === true && restoreRes.data.isPro === true, 'Restore purchase successfully re-verifies legitimate Pro entitlement');

    console.log('\n=======================================');
    console.log(failed ? '[FAILED] Some licensing tests failed.' : '[SUCCESS] All Licensing & Anti-Tamper Tests PASSED.');
  } finally {
    if (fs.existsSync(licFile)) {
      try { fs.unlinkSync(licFile); } catch (_) {}
    }
    if (serverProc) {
      serverProc.kill();
    }
  }

  process.exit(failed ? 1 : 0);
}

run();
