// Automated Test: AppxManifest.xml, MSIX and Preset Validation
const fs = require('fs');
const path = require('path');

const WINDOWS_DIR = path.resolve(__dirname, '..');
const ROOT_DIR    = path.resolve(__dirname, '..', '..');
const MANIFEST_PATH = path.join(WINDOWS_DIR, 'AppxManifest.xml');
const DIST_MSIX     = path.join(WINDOWS_DIR, 'dist', 'CleanTV-Desktop-Manager-v4.0.0.msix');
const PRESETS_DIR   = path.join(ROOT_DIR, 'presets');

console.log('[TEST] Starting Windows MSIX, Manifest & Preset Validation...\n');

let failed = false;

function assert(condition, message) {
  if (!condition) {
    console.error(`  FAIL: ${message}`);
    failed = true;
  } else {
    console.log(`  PASS: ${message}`);
  }
}

// 1. Manifest file existence
assert(fs.existsSync(MANIFEST_PATH), 'AppxManifest.xml exists');

const manifestContent = fs.readFileSync(MANIFEST_PATH, 'utf-8');

// 2. Identity checks
assert(manifestContent.includes('<Identity Name="RishiSharma.CleanTV"'), 'Identity Name is RishiSharma.CleanTV');
assert(manifestContent.includes('Publisher="CN=4C22920B-E5FC-4E8E-BFF1-7F166D6008F2"'), 'Publisher is official Microsoft Store ID CN=4C22920B-E5FC-4E8E-BFF1-7F166D6008F2');
assert(manifestContent.includes('<PublisherDisplayName>Rishi Sharma</PublisherDisplayName>'), 'PublisherDisplayName is Rishi Sharma');
assert(manifestContent.includes('Version="4.0.0.0"'), 'Package Version is 4.0.0.0');
assert(manifestContent.includes('ProcessorArchitecture="x64"'), 'ProcessorArchitecture is x64');

// 3. Executable and EntryPoint
assert(manifestContent.includes('Executable="CleanTV.exe"'), 'Application Executable is CleanTV.exe');
assert(manifestContent.includes('EntryPoint="Windows.FullTrustApplication"'), 'EntryPoint is Windows.FullTrustApplication');
assert(manifestContent.includes('<rescap:Capability Name="runFullTrust" />'), 'Capability runFullTrust declared');

// 4. Asset references verification
const requiredAssets = [
  'assets/StoreLogo.png',
  'assets/Square150x150Logo.png',
  'assets/Square44x44Logo.png',
  'assets/Wide310x150Logo.png',
  'assets/Square310x310Logo.png',
  'assets/SplashScreen.png'
];
requiredAssets.forEach(asset => {
  const fullAssetPath = path.join(WINDOWS_DIR, asset);
  assert(fs.existsSync(fullAssetPath), `Required asset exists on disk: ${asset}`);
});

// 5. Binary verification
const exePath = path.join(WINDOWS_DIR, 'CleanTV.exe');
assert(fs.existsSync(exePath), 'Compiled CleanTV.exe binary exists');
if (fs.existsSync(exePath)) {
  const stat = fs.statSync(exePath);
  assert(stat.size > 1000, `CleanTV.exe is non-trivial (${stat.size} bytes)`);
}

// 6. MSIX Package check
assert(fs.existsSync(DIST_MSIX), `Dist MSIX package exists: ${path.basename(DIST_MSIX)}`);
if (fs.existsSync(DIST_MSIX)) {
  const stat = fs.statSync(DIST_MSIX);
  assert(stat.size > 5000000, `MSIX package file size contains complete bundled payload (${stat.size} bytes > 5MB)`);
}

// 6b. Bundled APK and Platform Tools verification
const bundledApk = path.join(WINDOWS_DIR, 'apks', 'clean-tv-launcher-latest.apk');
assert(fs.existsSync(bundledApk), 'Bundled launcher APK exists in windows/apks/');
if (fs.existsSync(bundledApk)) {
  const apkStat = fs.statSync(bundledApk);
  assert(apkStat.size > 1000000, `Launcher APK size is valid (${apkStat.size} bytes)`);
}

const bundledAdb = path.join(WINDOWS_DIR, 'platform-tools', 'adb.exe');
assert(fs.existsSync(bundledAdb), 'Bundled adb.exe exists in windows/platform-tools/');

// 7. Preset files: all 7 expected presets must exist and be non-empty
const requiredPresets = [
  'realme-changhong.txt',
  'xiaomi-mitv.txt',
  'tcl-tv.txt',
  'google-tv.txt',
  'generic-android-tv.txt',
  'sony-bravia.txt',
  'hisense.txt'
];
requiredPresets.forEach(p => {
  const fp = path.join(PRESETS_DIR, p);
  assert(fs.existsSync(fp), `Preset file exists: ${p}`);
  if (fs.existsSync(fp)) {
    const lines = fs.readFileSync(fp, 'utf8').split('\n')
      .filter(l => l.trim() && !l.trim().startsWith('#'));
    assert(lines.length > 0, `Preset ${p} has active (non-comment) entries (${lines.length} found)`);
  }
});

// 8. install.ps1 must declare Resolve-PresetFile function and honour $Preset param
const installPs1 = path.join(ROOT_DIR, 'install.ps1');
assert(fs.existsSync(installPs1), 'install.ps1 exists');
if (fs.existsSync(installPs1)) {
  const src = fs.readFileSync(installPs1, 'utf8');
  assert(src.includes('Resolve-PresetFile'), 'install.ps1 defines Resolve-PresetFile function');
  assert(src.includes('ForcedPreset'), 'install.ps1 Resolve-PresetFile honours $Preset parameter');
  assert(src.includes("'sony'") || src.includes("'sony-bravia'") || src.includes("sony"), 'install.ps1 handles Sony brand');
  assert(src.includes('hisense'), 'install.ps1 handles Hisense brand');
  assert(src.includes('generic-android-tv'), 'install.ps1 uses generic fallback for unknown brands');
}

// 9. restore.ps1 must use backup-driven logic (not a hardcoded list)
const restorePs1 = path.join(ROOT_DIR, 'restore.ps1');
assert(fs.existsSync(restorePs1), 'restore.ps1 exists');
if (fs.existsSync(restorePs1)) {
  const src = fs.readFileSync(restorePs1, 'utf8');
  assert(src.includes('packages-disabled.txt'), 'restore.ps1 reads backup packages-disabled.txt');
  assert(src.includes('disabledBeforeInstall'), 'restore.ps1 performs backup-diff restore logic');
  assert(!src.includes("'tv.anoki.acr.controller'") || src.includes('packagesToRestore.Add'),
    'restore.ps1 does not use a hardcoded static package list as primary restore source');
  assert(src.includes('dns-changed.txt'), 'restore.ps1 restores DNS settings from backup flag');
}

console.log('\n=======================================');
if (failed) {
  console.error('[FAILED] One or more validations failed.');
  process.exit(1);
} else {
  console.log('[SUCCESS] All Manifest, MSIX, Preset and Script tests PASSED.');
  process.exit(0);
}
