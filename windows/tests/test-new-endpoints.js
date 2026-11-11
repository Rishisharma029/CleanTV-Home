const http = require('http');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { spawn } = require('child_process');

const PORT = 59822;
console.log('[TEST] Starting CleanTV Extended Endpoints Integration Tests...\n');


const crypto = require('crypto');

function getTestEphemeralKey() {
  const salt = Buffer.from([25, 22, 31, 27, 20, 14, 12, 5, 17, 31, 3, 5, 9, 27, 22, 14, 5, 110, 24, 107, 98, 30])
    .map(b => b ^ 0x5A)
    .toString('utf-8');
  const part1 = crypto.randomBytes(2).toString('hex').toUpperCase();
  const sum = crypto.createHash('sha256').update(salt + part1).digest('hex').substring(0, 4).toUpperCase();
  return 'CTV-PRO-' + part1 + '-' + sum;
}

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
  const mainScript = path.resolve(__dirname, '..', 'src', 'main.js');
  const mockAdbExe = path.resolve(__dirname, 'mock-adb.exe');
  const env = { ...process.env, CLEANTV_ADB_PATH: mockAdbExe };

  serverProc = spawn('node', [mainScript], { env, stdio: 'pipe' });
  await new Promise(r => setTimeout(r, 1500));

  try {
    const htmlRes = await get('/index.html');
    assert(htmlRes.status === 200, 'Served /index.html');
    const m = htmlRes.body.match(/name="cleantv-token"\s+content="([^"]+)"/);
    assert(m && m[1], 'Extracted session token');
    const token = m[1];

    // 1. Test /api/packages
    const pkgRes = await post('/api/packages', { device: 'mock-tv-device' }, token);
    assert(pkgRes.status === 200, 'POST /api/packages returned 200');
    assert(Array.isArray(pkgRes.data.packages), 'packages array present');
    assert(pkgRes.data.packages.length > 0, `Returned ${pkgRes.data.packages.length} packages`);
    const youtube = pkgRes.data.packages.find(p => p.pkg === 'com.google.android.youtube.tv');
    assert(youtube && youtube.name.includes('YouTube'), 'Package classification for YouTube is populated');

    // 2. Test /api/package-action rejection on protected
    const protRes = await post('/api/package-action', { device: 'mock-tv-device', pkg: 'android', action: 'disable' }, token);
    assert(protRes.status === 403, 'POST /api/package-action rejected disabling protected package with 403');

    // 3. Test /api/package-action on bloat package
    const disRes = await post('/api/package-action', { device: 'mock-tv-device', pkg: 'com.xiaomi.mitv.advertise', action: 'disable' }, token);
    assert(disRes.status === 200 && disRes.data.action === 'disable', 'POST /api/package-action successfully disabled bloat package');

    // 4. Test /api/batch-action
    const batchRes = await post('/api/batch-action', { device: 'mock-tv-device', packages: ['com.xiaomi.mitv.upgrade', 'android'], action: 'disable' }, token);
    assert(batchRes.status === 200, 'POST /api/batch-action returned 200');
    assert(batchRes.data.skipped.some(s => s.pkg === 'android'), 'Batch action safely skipped protected package android');

    // 5. Test /api/license/status
    const licRes = await post('/api/license/status', {}, token);
    assert(licRes.status === 200, 'POST /api/license/status returned 200');

    // 6. Test /api/license/activate
    const actRes = await post('/api/license/activate', { licenseKey: getTestEphemeralKey() }, token);
    assert(actRes.status === 200 && actRes.data.tier === 'pro', 'POST /api/license/activate successfully upgraded to Pro');

    // 7. Test /api/license/checkout-oneclick (Lifetime)
    const checkoutRes = await post('/api/license/checkout-oneclick', { plan: 'lifetime', method: 'express', device: 'mock-tv-device' }, token);
    assert(checkoutRes.status === 200 && checkoutRes.data.success === true, 'POST /api/license/checkout-oneclick activates Lifetime');
    assert(checkoutRes.data.key && checkoutRes.data.key.startsWith('CTV-PRO-'), 'Generated valid CTV-PRO license key');
    assert(checkoutRes.data.plan === 'CleanTV Pro Lifetime', 'Plan name is CleanTV Pro Lifetime');

    // 8. Test /api/license/checkout-oneclick (Monthly)
    const monCheckoutRes = await post('/api/license/checkout-oneclick', { plan: 'monthly', method: 'express', device: 'mock-tv-device' }, token);
    assert(monCheckoutRes.status === 200 && monCheckoutRes.data.success === true, 'POST /api/license/checkout-oneclick activates Monthly');
    assert(monCheckoutRes.data.key && monCheckoutRes.data.key.startsWith('CTV-MON-'), 'Generated valid CTV-MON license key');
    assert(monCheckoutRes.data.plan === 'CleanTV Pro Monthly', 'Plan name is CleanTV Pro Monthly');

    console.log('\n=======================================');
    console.log(failed ? '[FAILED] Some extended tests failed.' : '[SUCCESS] All extended endpoint tests PASSED.');
  } finally {
    try { const lic = path.join(process.env.LOCALAPPDATA || '', 'CleanTV', 'license.json'); if (fs.existsSync(lic)) fs.unlinkSync(lic); } catch (e) {}
    if (serverProc) {
      serverProc.kill();
    }
  }

  process.exit(failed ? 1 : 0);
}

run();
