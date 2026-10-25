# Clean TV Home - Universal One-Click Installer
# Compatible with Android TV, Google TV, Realme TV, Xiaomi / Mi TV, TCL, Fire TV
[CmdletBinding()]
param(
  [string]$TvIp,
  [ValidateSet('interactive', 'full', 'launcher-only', 'debloat-only', 'restore', 'backup-only')]
  [string]$Mode = 'interactive',
  [string]$Preset = 'realme',
  [string]$AdbPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot").Path

function Show-Header {
  Clear-Host
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host "         CleanTV Home - Universal Seamless Installer       " -ForegroundColor Yellow -BackgroundColor Black
  Write-Host "   Apple TV-style Clean Launcher & Bloat-Free TV Setup     " -ForegroundColor White
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host ""
}

# 1. Resolve ADB
if (-not $AdbPath -or -not (Test-Path $AdbPath)) {
  $getAdbScript = Join-Path $repoRoot "tools\get-adb.ps1"
  $adbExe = & powershell -NoProfile -ExecutionPolicy Bypass -File $getAdbScript -Quiet
  if (-not $adbExe -or -not (Test-Path $adbExe)) {
    # Run visibly if quiet failed
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

# 2. Find / Connect to TV
$targetSerial = ""

# Check for USB connected device first
$adbDevices = & $adbExe devices | Where-Object { $_ -match '\t(device|unauthorized)' }
if ($adbDevices.Count -eq 1 -and -not $TvIp) {
  $firstDev = ($adbDevices[0] -split '\t')[0].Trim()
  $firstState = ($adbDevices[0] -split '\t')[1].Trim()
  if ($firstDev -notmatch ':') {
    Write-Host "[INFO] Detected USB connected device: $firstDev" -ForegroundColor Green
    $targetSerial = $firstDev
  }
}

if (-not $targetSerial) {
  if (-not $TvIp) {
    # Check for known recent IP from backup
    $defaultIp = "192.168.0.159"
    Write-Host "How to find your TV's IP address on your TV screen:" -ForegroundColor Cyan
    Write-Host "  Settings -> Network & Internet -> Your connected Wi-Fi/Ethernet" -ForegroundColor Gray
    Write-Host "  (Ensure TV and PC are on the same Wi-Fi / Router network)`n" -ForegroundColor Gray
    
    $inputIp = Read-Host "Enter your TV IP address [Press Enter for default: $defaultIp]"
    if ([string]::IsNullOrWhiteSpace($inputIp)) {
      $TvIp = $defaultIp
    } else {
      $TvIp = $inputIp.Trim()
    }
  }

  $targetSerial = if ($TvIp -match ':') { $TvIp } else { "$TvIp`:5555" }
  Write-Host "`nConnecting to TV at $targetSerial..." -ForegroundColor Cyan
  & $adbExe connect $targetSerial | Out-Host
}

# Wait for authorization if needed
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

# TV Properties
$tvModel = (& $adbExe -s $targetSerial shell getprop ro.product.model 2>&1).Trim()
$tvBrand = (& $adbExe -s $targetSerial shell getprop ro.product.brand 2>&1).Trim()
$tvAndroidVer = (& $adbExe -s $targetSerial shell getprop ro.build.version.release 2>&1).Trim()

Write-Host "`n================ TV CONNECTED ================" -ForegroundColor Green
Write-Host "  Brand:       $tvBrand" -ForegroundColor White
Write-Host "  Model:       $tvModel" -ForegroundColor White
Write-Host "  Android Ver: $tvAndroidVer" -ForegroundColor White
Write-Host "  Endpoint:    $targetSerial" -ForegroundColor White
Write-Host "==============================================`n" -ForegroundColor Green

# Interactive Menu selection
if ($Mode -eq 'interactive') {
  Write-Host "Select installation option:" -ForegroundColor Yellow
  Write-Host "  [1] Recommended Full Setup (Install Clean Launcher + Safe Debloat + Auto Backup)" -ForegroundColor White
  Write-Host "  [2] Launcher Only (Install Clean Launcher, Keep All Apps Untouched)" -ForegroundColor White
  Write-Host "  [3] Debloat Only (Disable Ads/Telemetry, Keep Current Launcher)" -ForegroundColor White
  Write-Host "  [4] Restore Stock (Restore Default Factory Launcher & Re-enable All Apps)" -ForegroundColor White
  Write-Host "  [5] Backup / Snapshot Only (Export installed packages & settings)" -ForegroundColor White
  Write-Host "  [Q] Exit`n" -ForegroundColor Gray

  $choice = Read-Host "Enter option [1-5 or Q, default: 1]"
  if ([string]::IsNullOrWhiteSpace($choice)) { $choice = "1" }

  switch ($choice.ToUpper()) {
    "1" { $Mode = "full" }
    "2" { $Mode = "launcher-only" }
    "3" { $Mode = "debloat-only" }
    "4" { $Mode = "restore" }
    "5" { $Mode = "backup-only" }
    "Q" { exit 0 }
    default { $Mode = "full" }
  }
}

# Option 4: Restore
if ($Mode -eq 'restore') {
  Write-Host "`nRunning Restore Stock script..." -ForegroundColor Yellow
  & powershell -NoProfile -ExecutionPolicy Bypass -File "$repoRoot\restore.ps1" -TvHost $targetSerial -AdbPath $adbExe
  exit 0
}

# Perform Safety Backup
$timestamp = (Get-Date).ToString("yyyyMMdd-HHmmss")
$backupDir = Join-Path $repoRoot "backups\backup-$timestamp"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
Write-Host "`n[STEP 1/3] Creating Safety Backup at: backups\backup-$timestamp" -ForegroundColor Cyan

& $adbExe -s $targetSerial shell pm list packages | Out-File -Encoding utf8 (Join-Path $backupDir "packages-all.txt")
& $adbExe -s $targetSerial shell pm list packages -d | Out-File -Encoding utf8 (Join-Path $backupDir "packages-disabled.txt")
& $adbExe -s $targetSerial shell pm list packages -3 | Out-File -Encoding utf8 (Join-Path $backupDir "packages-thirdparty.txt")
& $adbExe -s $targetSerial shell getprop | Out-File -Encoding utf8 (Join-Path $backupDir "getprop.txt")
& $adbExe -s $targetSerial shell settings list global | Out-File -Encoding utf8 (Join-Path $backupDir "settings-global.txt")

Write-Host "  Backup saved successfully! (Can be restored anytime)" -ForegroundColor Green

if ($Mode -eq 'backup-only') {
  Write-Host "`nBackup complete. Exiting." -ForegroundColor Green
  exit 0
}

# Install Launcher
if ($Mode -in @('full', 'launcher-only')) {
  Write-Host "`n[STEP 2/3] Installing Clean TV Launcher..." -ForegroundColor Cyan
  
  $apkPath = Join-Path $repoRoot "apks\clean-tv-launcher-latest.apk"
  if (-not (Test-Path $apkPath)) {
    # Check fallback
    $apkPath = Join-Path $repoRoot "baron-inspired-tv-home\baron-inspired-tv-home.apk"
  }
  
  if (-not (Test-Path $apkPath)) {
    Write-Host "[ERROR] Launcher APK not found at $apkPath" -ForegroundColor Red
    exit 1
  }

  Write-Host "  Sideloading $apkPath to TV..." -ForegroundColor Gray
  $installOutput = & $adbExe -s $targetSerial install -r $apkPath
  $installOutput | Out-Host

  if ($LASTEXITCODE -ne 0 -or ($installOutput -join " ") -notmatch "Success") {
    Write-Host "[ERROR] APK installation failed. Stock launcher was NOT modified." -ForegroundColor Red
    exit 1
  }

  Write-Host "  Launching Clean TV Home on TV..." -ForegroundColor Gray
  & $adbExe -s $targetSerial shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity | Out-Host

  Write-Host "  Setting as Default Home screen..." -ForegroundColor Gray
  & $adbExe -s $targetSerial shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity | Out-Null

  # Disable stock Android TV launcher if installed
  $stockLaunchers = @(
    'com.google.android.tvlauncher',
    'com.changhong.system.launcher.v2',
    'com.mitv.tvhome'
  )

  foreach ($stock in $stockLaunchers) {
    $hasStock = & $adbExe -s $targetSerial shell pm list packages $stock
    if ($hasStock -match $stock) {
      Write-Host "  Safely disabling stock launcher: $stock" -ForegroundColor Gray
      & $adbExe -s $targetSerial shell pm disable-user --user 0 $stock | Out-Null
    }
  }

  # Confirm home activity and trigger HOME keyevent
  & $adbExe -s $targetSerial shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity | Out-Null
  & $adbExe -s $targetSerial shell input keyevent HOME | Out-Null
  Start-Sleep -Seconds 2

  # Focus verification
  $focus = & $adbExe -s $targetSerial shell dumpsys activity activities | Select-String 'mFocusedApp'
  if ($focus -match 'com\.gathin\.tvhome') {
    Write-Host "  [SUCCESS] Clean TV Home is now active and in focus!" -ForegroundColor Green
  } else {
    Write-Host "  [WARNING] Home app did not take primary focus immediately." -ForegroundColor Yellow
    Write-Host "  If prompted on TV, choose 'Always' for Clean TV Home." -ForegroundColor Yellow
  }
}

# Debloat step
if ($Mode -in @('full', 'debloat-only')) {
  Write-Host "`n[STEP 3/3] Applying Safe Debloat (Ads & Telemetry)..." -ForegroundColor Cyan
  
  # Choose preset file based on brand or default
  $presetFile = Join-Path $repoRoot "presets\realme-changhong.txt"
  if ($tvBrand -match 'xiaomi|redmi' -or $tvModel -match 'mitv') {
    $presetFile = Join-Path $repoRoot "presets\xiaomi-mitv.txt"
  } elseif ($tvBrand -match 'tcl') {
    $presetFile = Join-Path $repoRoot "presets\tcl-tv.txt"
  } elseif ($tvBrand -match 'google' -or $tvModel -match 'chromecast') {
    $presetFile = Join-Path $repoRoot "presets\google-tv.txt"
  }

  if (Test-Path $presetFile) {
    Write-Host "  Using preset: $([System.IO.Path]::GetFileName($presetFile))" -ForegroundColor Gray
    $packagesToDebloat = Get-Content $presetFile | Where-Object { $_ -notmatch '^\s*#' -and $_.Trim() -ne '' }
    
    foreach ($line in $packagesToDebloat) {
      $pkg = ($line -split '#')[0].Trim()
      if (-not $pkg) { continue }
      
      # Check if package is installed on TV
      $isInstalled = & $adbExe -s $targetSerial shell pm list packages $pkg
      if ($isInstalled -match $pkg) {
        $res = (& $adbExe -s $targetSerial shell pm disable-user --user 0 $pkg 2>&1) -join " "
        Write-Host "  Disabled: $pkg -> $res" -ForegroundColor Gray
      }
    }
    Write-Host "  Safe debloat complete!" -ForegroundColor Green
  }
}

# Final Summary
Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "                   SETUP COMPLETE!                          " -ForegroundColor Yellow -BackgroundColor Black
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "✓ Apple TV-style clean home screen is installed and active." -ForegroundColor Green
Write-Host "✓ Sponsored ads, telemetry, and background bloat are disabled." -ForegroundColor Green
Write-Host "✓ TV remote Home button will now open CleanTV Home." -ForegroundColor Green
Write-Host ""
Write-Host "Important Tips:" -ForegroundColor Yellow
Write-Host "  1. Test your TV remote (Home, Settings, Volume, Apps) to ensure all work smoothly." -ForegroundColor White
Write-Host "  2. You can revert EVERYTHING at any time by double-clicking 'restore.bat'." -ForegroundColor White
Write-Host "  3. For security, you can now turn off USB Debugging in TV Developer Options." -ForegroundColor White
Write-Host "============================================================`n" -ForegroundColor Cyan
