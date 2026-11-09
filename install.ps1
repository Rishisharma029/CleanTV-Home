# CleanTV Home - Universal One-Click Installer
# Compatible with Android TV, Google TV, Realme TV, Xiaomi / Mi TV, TCL, Sony, Hisense, Fire TV
[CmdletBinding()]
param(
  [string]$TvIp,
  [ValidateSet('interactive', 'full', 'launcher-only', 'debloat-only', 'restore', 'backup-only', 'adblock')]
  [string]$Mode = 'interactive',
  # When set, forces a specific preset file name (without .txt) regardless of detected brand.
  # Example: -Preset sony-bravia   or   -Preset generic-android-tv
  [string]$Preset = '',
  [string]$AdbPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot").Path

function Show-Header {
  Clear-Host
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host "         CleanTV Home - Universal Seamless Installer        " -ForegroundColor Yellow -BackgroundColor Black
  Write-Host "   Apple TV-style Clean Launcher & Bloat-Free TV Setup      " -ForegroundColor White
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host ""
}

# ---------------------------------------------------------------------------
# Preset auto-detection: returns the preset file path based on TV brand/model.
# $Preset parameter always wins when explicitly provided by the caller.
# ---------------------------------------------------------------------------
function Resolve-PresetFile {
  param(
    [string]$Brand,
    [string]$Model,
    [string]$ForcedPreset
  )

  $presetsDir = Join-Path $repoRoot "presets"

  # If caller explicitly passed -Preset, honour it (validated below)
  if (-not [string]::IsNullOrWhiteSpace($ForcedPreset)) {
    $forced = Join-Path $presetsDir "$ForcedPreset.txt"
    if (Test-Path $forced) {
      return $forced
    }
    Write-Host "  [WARNING] Specified preset '$ForcedPreset.txt' not found in presets/." -ForegroundColor Yellow
    Write-Host "  Falling back to brand auto-detection." -ForegroundColor Yellow
  }

  # Auto-detect by brand / model properties
  $b = $Brand.ToLower()
  $m = $Model.ToLower()

  if ($b -match 'realme' -or $b -match 'changhong' -or $m -match 'homwee') {
    return (Join-Path $presetsDir "realme-changhong.txt")
  }
  if ($b -match 'xiaomi' -or $b -match 'redmi' -or $m -match 'mitv') {
    return (Join-Path $presetsDir "xiaomi-mitv.txt")
  }
  if ($b -match 'tcl') {
    return (Join-Path $presetsDir "tcl-tv.txt")
  }
  if ($b -match 'google' -or $m -match 'chromecast') {
    return (Join-Path $presetsDir "google-tv.txt")
  }
  if ($b -match 'sony' -or $m -match 'bravia') {
    return (Join-Path $presetsDir "sony-bravia.txt")
  }
  if ($b -match 'hisense') {
    return (Join-Path $presetsDir "hisense.txt")
  }

  # Unknown / unsupported brand - use safe generic preset (NOT Realme-specific)
  Write-Host "  [NOTICE] Brand '$Brand' not in supported list." -ForegroundColor Yellow
  Write-Host "  Using generic-android-tv.txt (conservative, cross-brand safe)." -ForegroundColor Yellow
  return (Join-Path $presetsDir "generic-android-tv.txt")
}

# ---------------------------------------------------------------------------
# 1. Resolve ADB
# ---------------------------------------------------------------------------
if (-not $AdbPath -or -not (Test-Path $AdbPath)) {
  $getAdbScript = Join-Path $repoRoot "tools\get-adb.ps1"
  $adbExe = & powershell -NoProfile -ExecutionPolicy Bypass -File $getAdbScript -Quiet
  if (-not $adbExe -or -not (Test-Path $adbExe)) {
    $adbExe = & powershell -NoProfile -ExecutionPolicy Bypass -File $getAdbScript
  }
} else {
  $adbExe = $AdbPath
}

if (-not (Test-Path $adbExe)) {
  Write-Host "[ERROR] Could not initialize ADB. Please install platform-tools." -ForegroundColor Red
  exit 1
}

Show-Header

# ---------------------------------------------------------------------------
# 2. Find / Connect to TV
# ---------------------------------------------------------------------------
$targetSerial = ""

$adbDevices = & $adbExe devices | Where-Object { $_ -match '\t(device|unauthorized)' }
if ($adbDevices.Count -eq 1 -and -not $TvIp) {
  $firstDev = ($adbDevices[0] -split '\t')[0].Trim()
  if ($firstDev -notmatch ':') {
    Write-Host "[INFO] Detected USB connected device: $firstDev" -ForegroundColor Green
    $targetSerial = $firstDev
  }
}

if (-not $targetSerial) {
  if (-not $TvIp) {
    $defaultIp = "192.168.1.100"
    Write-Host "How to find your TV's IP address on your TV screen:" -ForegroundColor Cyan
    Write-Host "  Settings -> Network & Internet -> Your connected Wi-Fi/Ethernet" -ForegroundColor Gray
    Write-Host "  (Ensure TV and PC are on the same Wi-Fi / Router network)`n" -ForegroundColor Gray
    $inputIp = Read-Host "Enter your TV IP address [Press Enter for default: $defaultIp]"
    $TvIp = if ([string]::IsNullOrWhiteSpace($inputIp)) { $defaultIp } else { $inputIp.Trim() }
  }
  $targetSerial = if ($TvIp -match ':') { $TvIp } else { "$TvIp`:5555" }
  Write-Host "`nConnecting to TV at $targetSerial..." -ForegroundColor Cyan
  & $adbExe connect $targetSerial | Out-Host
}

# ---------------------------------------------------------------------------
# 3. Wait for ADB authorization
# ---------------------------------------------------------------------------
$timeout = 30
$authGranted = $false
Write-Host "Checking TV ADB connection status..." -ForegroundColor Cyan

for ($i = 0; $i -lt $timeout; $i++) {
  $state = (& $adbExe -s $targetSerial get-state 2>&1) -join ""
  $state = "$state".Trim()

  if ($state -eq 'device') {
    $authGranted = $true
    break
  } elseif ($state -match 'unauthorized') {
    Write-Host "`n[ACTION REQUIRED] Look at your TV screen right now!" -ForegroundColor Yellow -BackgroundColor Black
    Write-Host "A popup titled 'Allow USB debugging?' will appear." -ForegroundColor Yellow
    Write-Host "1. Check the box: [x] Always allow from this computer" -ForegroundColor White
    Write-Host "2. Click OK / Allow using your TV remote." -ForegroundColor White
    Write-Host "Waiting for authorization... ($($timeout - $i)s remaining)" -ForegroundColor Gray
    Start-Sleep -Seconds 2
  } else {
    Start-Sleep -Seconds 1
  }
}

if (-not $authGranted) {
  Write-Host "`n[ERROR] TV is not authorized or not responding at $targetSerial." -ForegroundColor Red
  Write-Host "Please ensure:" -ForegroundColor Yellow
  Write-Host "  1. The TV is turned ON and connected to the same Wi-Fi as your PC." -ForegroundColor Gray
  Write-Host "  2. Developer Options -> USB Debugging (and Network Debugging) is ON." -ForegroundColor Gray
  Write-Host "  3. You accepted the 'Allow USB debugging' prompt on your TV screen." -ForegroundColor Gray
  Write-Host ""
  exit 1
}

# ---------------------------------------------------------------------------
# 4. Read TV properties
# ---------------------------------------------------------------------------
$tvModel   = (& $adbExe -s $targetSerial shell getprop ro.product.model   2>&1).Trim()
$tvBrand   = (& $adbExe -s $targetSerial shell getprop ro.product.brand   2>&1).Trim()
$tvAndroidVer = (& $adbExe -s $targetSerial shell getprop ro.build.version.release 2>&1).Trim()

Write-Host "`n================ TV CONNECTED ================" -ForegroundColor Green
Write-Host "  Brand:       $tvBrand" -ForegroundColor White
Write-Host "  Model:       $tvModel" -ForegroundColor White
Write-Host "  Android Ver: $tvAndroidVer" -ForegroundColor White
Write-Host "  Endpoint:    $targetSerial" -ForegroundColor White
Write-Host "==============================================`n" -ForegroundColor Green

# ---------------------------------------------------------------------------
# 5. Interactive mode menu
# ---------------------------------------------------------------------------
if ($Mode -eq 'interactive') {
  Write-Host "Select installation option:" -ForegroundColor Yellow
  Write-Host "  [1] Recommended Full Setup (Install Clean Launcher + Safe Debloat + Auto Backup)" -ForegroundColor White
  Write-Host "  [2] Launcher Only (Install Clean Launcher, Keep All Apps Untouched)" -ForegroundColor White
  Write-Host "  [3] Debloat Only (Disable Ads/Telemetry, Keep Current Launcher)" -ForegroundColor White
  Write-Host "  [4] Ad-Blocking Suite (System-Wide Ad-Blocking DNS)" -ForegroundColor White
  Write-Host "  [5] Restore Stock (Restore Default Factory Launcher & Re-enable All Apps)" -ForegroundColor White
  Write-Host "  [6] Backup / Snapshot Only (Export installed packages & settings)" -ForegroundColor White
  Write-Host "  [Q] Exit`n" -ForegroundColor Gray

  $choice = Read-Host "Enter option [1-6 or Q, default: 1]"
  if ([string]::IsNullOrWhiteSpace($choice)) { $choice = "1" }

  switch ($choice.ToUpper()) {
    "1" { $Mode = "full" }
    "2" { $Mode = "launcher-only" }
    "3" { $Mode = "debloat-only" }
    "4" { $Mode = "adblock" }
    "5" { $Mode = "restore" }
    "6" { $Mode = "backup-only" }
    "Q" { exit 0 }
    default { $Mode = "full" }
  }
}

# ---------------------------------------------------------------------------
# Option: Adblock
# ---------------------------------------------------------------------------
if ($Mode -eq 'adblock') {
  & powershell -NoProfile -ExecutionPolicy Bypass -File "$repoRoot\tools\adblock.ps1" -TvIp $targetSerial -AdbPath $adbExe
  exit 0
}

# ---------------------------------------------------------------------------
# Option: Restore
# ---------------------------------------------------------------------------
if ($Mode -eq 'restore') {
  Write-Host "`nRunning Restore Stock script..." -ForegroundColor Yellow
  & powershell -NoProfile -ExecutionPolicy Bypass -File "$repoRoot\restore.ps1" -TvHost $targetSerial -AdbPath $adbExe
  exit 0
}

# ---------------------------------------------------------------------------
# STEP 1: Safety Backup (always run before any modification)
# ---------------------------------------------------------------------------
$timestamp = (Get-Date).ToString("yyyyMMdd-HHmmss")
$backupDir = Join-Path $repoRoot "backups\backup-$timestamp"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
Write-Host "`n[STEP 1/3] Creating Safety Backup at: backups\backup-$timestamp" -ForegroundColor Cyan

& $adbExe -s $targetSerial shell pm list packages        | Out-File -Encoding utf8 (Join-Path $backupDir "packages-all.txt")
& $adbExe -s $targetSerial shell pm list packages -d     | Out-File -Encoding utf8 (Join-Path $backupDir "packages-disabled.txt")
& $adbExe -s $targetSerial shell pm list packages -3     | Out-File -Encoding utf8 (Join-Path $backupDir "packages-thirdparty.txt")
& $adbExe -s $targetSerial shell getprop                 | Out-File -Encoding utf8 (Join-Path $backupDir "getprop.txt")
& $adbExe -s $targetSerial shell settings list global    | Out-File -Encoding utf8 (Join-Path $backupDir "settings-global.txt")

# Record which stock launchers are currently ENABLED so restore knows what to bring back
$stockLauncherIds = @(
  'com.google.android.tvlauncher',
  'com.changhong.system.launcher.v2',
  'com.mitv.tvhome',
  'com.sony.dtv.tvx',
  'com.hisense.launcher'
)
$enabledLaunchersAtBackup = @()
foreach ($sl in $stockLauncherIds) {
  $has = & $adbExe -s $targetSerial shell pm list packages $sl
  if ($has -match $sl) { $enabledLaunchersAtBackup += $sl }
}
$enabledLaunchersAtBackup | Out-File -Encoding utf8 (Join-Path $backupDir "stock-launchers.txt")

Write-Host "  Backup saved successfully! (Can be restored anytime via restore.bat)" -ForegroundColor Green

if ($Mode -eq 'backup-only') {
  Write-Host "`nBackup complete. Exiting." -ForegroundColor Green
  exit 0
}

# ---------------------------------------------------------------------------
# STEP 2: Install Launcher (with APK install verification before any side-effect)
# ---------------------------------------------------------------------------
if ($Mode -in @('full', 'launcher-only')) {
  Write-Host "`n[STEP 2/3] Installing CleanTV Home Launcher..." -ForegroundColor Cyan

  $apkPath = Join-Path $repoRoot "apks\clean-tv-launcher-latest.apk"
  if (-not (Test-Path $apkPath)) {
    $apkPath = Join-Path $repoRoot "baron-inspired-tv-home\baron-inspired-tv-home.apk"
  }
  if (-not (Test-Path $apkPath)) {
    Write-Host "[ERROR] Launcher APK not found at $apkPath" -ForegroundColor Red
    exit 1
  }

  Write-Host "  Sideloading $apkPath to TV..." -ForegroundColor Gray
  $installOutput = & $adbExe -s $targetSerial install -r $apkPath
  $installOutput | Out-Host

  # SAFETY: Only proceed to disable stock launcher AFTER confirmed install success
  if ($LASTEXITCODE -ne 0 -or ($installOutput -join " ") -notmatch "Success") {
    Write-Host "[ERROR] APK installation failed. Stock launcher was NOT modified." -ForegroundColor Red
    Write-Host "  You can safely re-run the installer. No changes were made to your TV." -ForegroundColor Yellow
    exit 1
  }

  Write-Host "  Launching CleanTV Home on TV..." -ForegroundColor Gray
  & $adbExe -s $targetSerial shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity | Out-Host

  Write-Host "  Setting as Default Home screen..." -ForegroundColor Gray
  & $adbExe -s $targetSerial shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity | Out-Null

  # Verify CleanTV is actually set as home BEFORE disabling stock launchers
  $homeCheck = & $adbExe -s $targetSerial shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  if (($homeCheck -join "") -notmatch 'com\.gathin\.tvhome') {
    Write-Host "[WARNING] Could not confirm CleanTV is the active Home. Skipping stock launcher disable for safety." -ForegroundColor Yellow
    Write-Host "  Please set CleanTV as Home manually on your TV if prompted." -ForegroundColor Gray
  } else {
    # Only disable stock launcher once CleanTV is confirmed active
    foreach ($stock in $enabledLaunchersAtBackup) {
      Write-Host "  Safely disabling stock launcher: $stock" -ForegroundColor Gray
      & $adbExe -s $targetSerial shell pm disable-user --user 0 $stock | Out-Null
    }
  }

  & $adbExe -s $targetSerial shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity | Out-Null
  & $adbExe -s $targetSerial shell input keyevent HOME | Out-Null
  Start-Sleep -Seconds 2

  $focus = & $adbExe -s $targetSerial shell dumpsys activity activities | Select-String 'mFocusedApp'
  if ($focus -match 'com\.gathin\.tvhome') {
    Write-Host "  [SUCCESS] CleanTV Home is now active and in focus!" -ForegroundColor Green
  } else {
    Write-Host "  [WARNING] Home app did not take primary focus immediately." -ForegroundColor Yellow
    Write-Host "  If prompted on TV, choose 'Always' for CleanTV Home." -ForegroundColor Yellow
  }
}

# ---------------------------------------------------------------------------
# STEP 3: Debloat (with $Preset parameter honoured)
# ---------------------------------------------------------------------------
if ($Mode -in @('full', 'debloat-only')) {
  Write-Host "`n[STEP 3/3] Applying Safe Debloat (Ads & Telemetry)..." -ForegroundColor Cyan

  $presetFile = Resolve-PresetFile -Brand $tvBrand -Model $tvModel -ForcedPreset $Preset

  if (Test-Path $presetFile) {
    Write-Host "  Using preset: $([System.IO.Path]::GetFileName($presetFile))" -ForegroundColor Gray
    $packagesToDebloat = Get-Content $presetFile | Where-Object { $_ -notmatch '^\s*#' -and $_.Trim() -ne '' }

    $disabledCount = 0
    $skippedCount  = 0
    foreach ($line in $packagesToDebloat) {
      $pkg = ($line -split '#')[0].Trim()
      if (-not $pkg) { continue }

      $isInstalled = & $adbExe -s $targetSerial shell pm list packages $pkg
      if ($isInstalled -match $pkg) {
        $res = (& $adbExe -s $targetSerial shell pm disable-user --user 0 $pkg 2>&1) -join " "
        Write-Host "  Disabled: $pkg" -ForegroundColor Gray
        $disabledCount++
      } else {
        $skippedCount++
      }
    }
    Write-Host "  Safe debloat complete! ($disabledCount disabled, $skippedCount not present on this TV)" -ForegroundColor Green
  } else {
    Write-Host "  [NOTICE] No matching preset file found. Debloat skipped." -ForegroundColor Yellow
  }
}

# ---------------------------------------------------------------------------
# Optional: Ad-Blocking DNS
# ---------------------------------------------------------------------------
if ($Mode -eq 'full') {
  Write-Host "`nEnable System-Wide Ad-Blocking DNS (dns.adguard-dns.com)?" -ForegroundColor Cyan
  Write-Host "  * Zero RAM / Zero CPU overhead" -ForegroundColor Gray
  Write-Host "  * Blocks banner ads, telemetry & tracking across all apps" -ForegroundColor Gray
  Write-Host "  * Reversible: restore.ps1 resets DNS to automatic" -ForegroundColor Gray
  $enableAdblock = Read-Host "Enable Ad-Blocking DNS? [Y/n, default: Y]"
  if ([string]::IsNullOrWhiteSpace($enableAdblock) -or $enableAdblock.ToUpper() -eq 'Y') {
    & $adbExe -s $targetSerial shell settings put global private_dns_mode hostname | Out-Null
    & $adbExe -s $targetSerial shell settings put global private_dns_specifier dns.adguard-dns.com | Out-Null
    # Record that DNS was changed so restore.ps1 knows to reset it
    "adguard" | Out-File -Encoding utf8 (Join-Path $backupDir "dns-changed.txt")
    Write-Host "* System-wide Ad-Blocking DNS enabled!" -ForegroundColor Green
  }
}

# ---------------------------------------------------------------------------
# Final Summary
# ---------------------------------------------------------------------------
Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "                   SETUP COMPLETE!                          " -ForegroundColor Yellow -BackgroundColor Black
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "* Apple TV-style clean home screen is installed and active." -ForegroundColor Green
Write-Host "* Sponsored ads, telemetry, and background bloat are disabled." -ForegroundColor Green
Write-Host "* TV remote Home button will now open CleanTV Home." -ForegroundColor Green
Write-Host ""
Write-Host "Important Tips:" -ForegroundColor Yellow
Write-Host "  1. Test your TV remote (Home, Settings, Volume, Apps) to ensure all work smoothly." -ForegroundColor White
Write-Host "  2. You can revert EVERYTHING at any time by double-clicking 'restore.bat'." -ForegroundColor White
Write-Host "  3. For security, you can now turn off USB Debugging in TV Developer Options." -ForegroundColor White
Write-Host "  4. Backup saved at: backups\backup-$timestamp" -ForegroundColor White
Write-Host "============================================================`n" -ForegroundColor Cyan
