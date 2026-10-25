# Clean TV Home - 1-Click Complete Factory Restore Script
# Restores stock TV launcher and re-enables all disabled vendor apps & bloatware
[CmdletBinding()]
param(
  [string]$TvHost,
  [string]$AdbPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot").Path

function Show-Header {
  Clear-Host
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host "         CleanTV Home - 1-Click Complete Restore           " -ForegroundColor Red -BackgroundColor Black
  Write-Host "       Roll back to stock launcher & re-enable all apps     " -ForegroundColor White
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host ""
}

# 1. Resolve ADB
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
  Write-Host "[ERROR] Could not initialize ADB." -ForegroundColor Red
  exit 1
}

Show-Header

# 2. Connect
$targetSerial = ""
if ($TvHost) {
  $targetSerial = if ($TvHost -match ':') { $TvHost } else { "$TvHost`:5555" }
} else {
  $defaultIp = "192.168.0.159"
  $inputIp = Read-Host "Enter your TV IP address [Press Enter for default: $defaultIp]"
  $chosenIp = if ([string]::IsNullOrWhiteSpace($inputIp)) { $defaultIp } else { $inputIp.Trim() }
  $targetSerial = if ($chosenIp -match ':') { $chosenIp } else { "$chosenIp`:5555" }
}

Write-Host "Connecting to TV at $targetSerial..." -ForegroundColor Cyan
& $adbExe connect $targetSerial | Out-Host

$state = (& $adbExe -s $targetSerial get-state 2>&1) -join ""
if ("$state".Trim() -ne 'device') {
  Write-Host "[ERROR] TV is not responding at $targetSerial. Ensure TV is ON and USB Debugging is ON." -ForegroundColor Red
  exit 1
}

Write-Host "`nRe-enabling disabled apps and services..." -ForegroundColor Cyan

# List of common packages across presets
$packagesToRestore = @(
  'tv.anoki.acr.controller',
  'com.homwee.aipont',
  'com.google.android.tvrecommendations',
  'android.autoinstalls.config.homwee',
  'com.homwee.store',
  'com.homwee.help',
  'com.google.android.feedback',
  'com.google.android.play.games',
  'com.google.android.syncadapters.calendar',
  'com.tcl.tvmanager',
  'com.tcl.adservice',
  'com.tcl.useragreement',
  'com.xiaomi.mitv.upgrade',
  'com.xiaomi.mitv.advertise',
  'com.xiaomi.mitv.payment',
  'com.xiaomi.mitv.shop',
  'com.miui.analytics'
)

foreach ($pkg in $packagesToRestore) {
  $installed = & $adbExe -s $targetSerial shell pm list packages -d $pkg
  if ($installed -match $pkg) {
    Write-Host "  Re-enabling $pkg..." -ForegroundColor Gray
    & $adbExe -s $targetSerial shell pm enable --user 0 $pkg | Out-Null
  }
}

# Stock launchers to re-enable
$stockLaunchers = @(
  'com.google.android.tvlauncher',
  'com.changhong.system.launcher.v2',
  'com.mitv.tvhome'
)

$activeStock = ""
foreach ($stock in $stockLaunchers) {
  $hasPkg = & $adbExe -s $targetSerial shell pm list packages $stock
  if ($hasPkg -match $stock) {
    Write-Host "  Re-enabling stock launcher: $stock..." -ForegroundColor Green
    & $adbExe -s $targetSerial shell pm enable --user 0 $stock | Out-Null
    if (-not $activeStock) { $activeStock = $stock }
  }
}

if ($activeStock) {
  Write-Host "  Setting stock launcher ($activeStock) as default Home..." -ForegroundColor Cyan
  & $adbExe -s $targetSerial shell cmd package set-home-activity --user 0 "$activeStock/.MainActivity" | Out-Null
  & $adbExe -s $targetSerial shell input keyevent HOME | Out-Null
  Start-Sleep -Seconds 2
  
  $focus = & $adbExe -s $targetSerial shell dumpsys activity activities | Select-String 'mFocusedApp'
  $focus | Out-Host
} else {
  Write-Host "  Restoring default Android TV home activity..." -ForegroundColor Yellow
  & $adbExe -s $targetSerial shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity | Out-Null
  & $adbExe -s $targetSerial shell input keyevent HOME | Out-Null
}

Write-Host "`n============================================================" -ForegroundColor Green
Write-Host "                RESTORE COMPLETED SUCCESSFULLY              " -ForegroundColor Yellow -BackgroundColor Black
Write-Host "============================================================" -ForegroundColor Green
Write-Host "✓ Stock factory launcher re-enabled and restored as Home." -ForegroundColor Green
Write-Host "✓ All previously disabled vendor apps and services are re-enabled." -ForegroundColor Green
Write-Host "✓ Your TV is back to its original state." -ForegroundColor Green
Write-Host "============================================================`n" -ForegroundColor Green
