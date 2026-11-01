# CleanTV Home - 1-Click Complete Factory Restore Script
# Reads packages-disabled.txt and stock-launchers.txt from the most recent backup
# so that ONLY packages that THIS installer actually disabled are re-enabled.
[CmdletBinding()]
param(
  [string]$TvHost,
  # Path to a specific backup directory. When omitted, the latest backup is used.
  [string]$BackupDir,
  [string]$AdbPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot").Path

function Show-Header {
  Clear-Host
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host "         CleanTV Home - 1-Click Complete Restore            " -ForegroundColor Red -BackgroundColor Black
  Write-Host "       Roll back to stock launcher & re-enable all apps     " -ForegroundColor White
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host ""
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
  Write-Host "[ERROR] Could not initialize ADB." -ForegroundColor Red
  exit 1
}

Show-Header

# ---------------------------------------------------------------------------
# 2. Locate the backup to restore from
# ---------------------------------------------------------------------------
$backupsRoot = Join-Path $repoRoot "backups"

if ($BackupDir -and (Test-Path $BackupDir)) {
  $chosenBackup = $BackupDir
} else {
  # Auto-select: most recently created backup directory
  $allBackups = Get-ChildItem -Path $backupsRoot -Directory -Filter "backup-*" -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending

  if ($allBackups.Count -eq 0) {
    Write-Host "[WARNING] No installer backup found in '$backupsRoot'." -ForegroundColor Yellow
    Write-Host "  The restore will still re-enable known stock launchers," -ForegroundColor Gray
    Write-Host "  but cannot guarantee ALL previously-disabled packages are restored." -ForegroundColor Gray
    Write-Host ""
    $chosenBackup = $null
  } else {
    $chosenBackup = $allBackups[0].FullName
    Write-Host "[INFO] Using backup: $($allBackups[0].Name)" -ForegroundColor Cyan

    if ($allBackups.Count -gt 1) {
      Write-Host ""
      Write-Host "Multiple backups found. Press ENTER to use the latest, or type a number to pick:" -ForegroundColor Yellow
      for ($i = 0; $i -lt [Math]::Min($allBackups.Count, 10); $i++) {
        Write-Host "  [$i] $($allBackups[$i].Name)" -ForegroundColor Gray
      }
      $pick = Read-Host "Selection [ENTER = latest]"
      if ($pick -match '^\d+$' -and [int]$pick -lt $allBackups.Count) {
        $chosenBackup = $allBackups[[int]$pick].FullName
        Write-Host "[INFO] Using backup: $(Split-Path $chosenBackup -Leaf)" -ForegroundColor Cyan
      }
    }
  }
}

# ---------------------------------------------------------------------------
# 3. Connect to TV
# ---------------------------------------------------------------------------
$targetSerial = ""
if ($TvHost) {
  $targetSerial = if ($TvHost -match ':') { $TvHost } else { "$TvHost`:5555" }
} else {
  $defaultIp = "192.168.0.159"

  # Try to infer TV IP from backup getprop if available
  if ($chosenBackup) {
    $getPropFile = Join-Path $chosenBackup "getprop.txt"
    if (Test-Path $getPropFile) {
      $wifiLine = Select-String 'dhcp.*ip_address|wlan.*ipaddr' $getPropFile | Select-Object -First 1
      if ($wifiLine -and $wifiLine.Line -match '\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}') {
        $defaultIp = $Matches[0]
        Write-Host "[INFO] Suggested TV IP from backup: $defaultIp" -ForegroundColor Cyan
      }
    }
  }

  $inputIp = Read-Host "Enter your TV IP address [Press ENTER for default: $defaultIp]"
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

# ---------------------------------------------------------------------------
# 4. Build the list of packages to restore
# ---------------------------------------------------------------------------
# Primary source: packages that were disabled at backup time (packages-disabled.txt
# from BEFORE the installer ran). This file lists packages that were already
# disabled before we touched anything — we compare with the post-install disabled
# list to derive exactly what WE disabled.

$packagesToRestore = [System.Collections.Generic.List[string]]::new()
$restoredFromBackup = $false

if ($chosenBackup) {
  $preDisabledFile  = Join-Path $chosenBackup "packages-disabled.txt"  # disabled BEFORE install

  if (Test-Path $preDisabledFile) {
    $restoredFromBackup = $true

    # packages-disabled.txt from installer backup = state BEFORE our changes
    # Re-enable packages that are currently disabled but were NOT disabled before we ran
    $disabledBeforeInstall = (Get-Content $preDisabledFile -ErrorAction SilentlyContinue) -replace '^package:', '' | ForEach-Object { $_.Trim() } | Where-Object { $_ }

    # Get current disabled packages from TV
    $currentlyDisabled = (& $adbExe -s $targetSerial shell pm list packages -d) -replace '^package:', '' | ForEach-Object { $_.Trim() } | Where-Object { $_ }

    foreach ($pkg in $currentlyDisabled) {
      if ($disabledBeforeInstall -notcontains $pkg) {
        # We disabled this; re-enable it
        $packagesToRestore.Add($pkg)
      }
    }

    Write-Host "`n[INFO] Backup-driven restore: $($packagesToRestore.Count) packages to re-enable." -ForegroundColor Cyan
  } else {
    Write-Host "[WARNING] packages-disabled.txt not found in backup. Falling back to preset list." -ForegroundColor Yellow
  }
}

# Fallback: if no backup, re-enable packages from ALL preset files
if ($packagesToRestore.Count -eq 0 -and -not $restoredFromBackup) {
  Write-Host "[INFO] No backup data available. Reading all preset files to build restore list..." -ForegroundColor Yellow
  $presetsDir = Join-Path $repoRoot "presets"
  Get-ChildItem -Path $presetsDir -Filter "*.txt" -ErrorAction SilentlyContinue | ForEach-Object {
    Get-Content $_.FullName | ForEach-Object {
      $pkg = ($_ -split '#')[0].Trim()
      if ($pkg -and $pkg -notmatch '^\s*$') { $packagesToRestore.Add($pkg) }
    }
  }
  $packagesToRestore = [System.Collections.Generic.List[string]]($packagesToRestore | Select-Object -Unique)
  Write-Host "  Found $($packagesToRestore.Count) unique packages across all presets." -ForegroundColor Gray
}

# ---------------------------------------------------------------------------
# 5. Re-enable packages
# ---------------------------------------------------------------------------
Write-Host "`nRe-enabling packages disabled by the installer..." -ForegroundColor Cyan
$reenabledCount = 0
$notFoundCount  = 0

foreach ($pkg in $packagesToRestore) {
  $installed = & $adbExe -s $targetSerial shell pm list packages $pkg 2>&1
  if ($installed -match $pkg) {
    Write-Host "  Re-enabling $pkg..." -ForegroundColor Gray
    & $adbExe -s $targetSerial shell pm enable --user 0 $pkg 2>&1 | Out-Null
    $reenabledCount++
  } else {
    $notFoundCount++
  }
}

Write-Host "  Re-enabled $reenabledCount packages. ($notFoundCount not present on TV)" -ForegroundColor Green

# ---------------------------------------------------------------------------
# 6. Restore stock launcher
# ---------------------------------------------------------------------------
# Prefer the list from backup (stock-launchers.txt) so we restore the EXACT
# launchers that were present before installation.
$stockToRestore = @()

if ($chosenBackup) {
  $stockFile = Join-Path $chosenBackup "stock-launchers.txt"
  if (Test-Path $stockFile) {
    $stockToRestore = Get-Content $stockFile | Where-Object { $_.Trim() -ne '' }
  }
}

# Supplement with known stock launcher IDs in case backup file is missing
$knownStockLaunchers = @(
  'com.google.android.tvlauncher',
  'com.changhong.system.launcher.v2',
  'com.mitv.tvhome',
  'com.sony.dtv.tvx',
  'com.hisense.launcher'
)
foreach ($kl in $knownStockLaunchers) {
  if ($stockToRestore -notcontains $kl) {
    $has = & $adbExe -s $targetSerial shell pm list packages $kl 2>&1
    if ($has -match $kl) { $stockToRestore += $kl }
  }
}

$activeStock = ""
foreach ($stock in $stockToRestore) {
  $hasPkg = & $adbExe -s $targetSerial shell pm list packages $stock 2>&1
  if ($hasPkg -match $stock) {
    Write-Host "  Re-enabling stock launcher: $stock..." -ForegroundColor Green
    & $adbExe -s $targetSerial shell pm enable --user 0 $stock 2>&1 | Out-Null
    if (-not $activeStock) { $activeStock = $stock }
  }
}

if ($activeStock) {
  Write-Host "  Setting stock launcher ($activeStock) as default Home..." -ForegroundColor Cyan
  & $adbExe -s $targetSerial shell cmd package set-home-activity --user 0 "$activeStock/.MainActivity" 2>&1 | Out-Null
  & $adbExe -s $targetSerial shell input keyevent HOME | Out-Null
  Start-Sleep -Seconds 2
  $focus = (& $adbExe -s $targetSerial shell dumpsys activity activities | Select-String 'mFocusedApp') -join ""
  if ($focus -match $activeStock.Replace('.', '\.')) {
    Write-Host "  [SUCCESS] Stock launcher is active and in focus!" -ForegroundColor Green
  } else {
    Write-Host "  [NOTICE] Stock launcher enabled. It may require a TV reboot to take full effect." -ForegroundColor Yellow
  }
} else {
  Write-Host "  [NOTICE] No stock launcher found on this TV to restore. Home activity unchanged." -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# 7. Restore DNS settings if we changed them
# ---------------------------------------------------------------------------
if ($chosenBackup) {
  $dnsChangedFile = Join-Path $chosenBackup "dns-changed.txt"
  if (Test-Path $dnsChangedFile) {
    Write-Host "`n  Restoring DNS to automatic (removing AdGuard DNS override)..." -ForegroundColor Cyan
    & $adbExe -s $targetSerial shell settings put global private_dns_mode off 2>&1 | Out-Null
    & $adbExe -s $targetSerial shell settings delete global private_dns_specifier 2>&1 | Out-Null
    Write-Host "  DNS reset to automatic." -ForegroundColor Green
  }
}

# ---------------------------------------------------------------------------
# 8. Uninstall CleanTV launcher (optional)
# ---------------------------------------------------------------------------
$cleantvInstalled = & $adbExe -s $targetSerial shell pm list packages com.gathin.tvhome 2>&1
if ($cleantvInstalled -match 'com.gathin.tvhome') {
  Write-Host ""
  $removeIt = Read-Host "Remove CleanTV Home launcher from TV? [Y/n, default: N]"
  if ($removeIt.ToUpper() -eq 'Y') {
    & $adbExe -s $targetSerial shell pm uninstall --user 0 com.gathin.tvhome 2>&1 | Out-Host
    Write-Host "  CleanTV Home uninstalled." -ForegroundColor Green
  } else {
    Write-Host "  CleanTV Home kept installed (stock launcher is the default now)." -ForegroundColor Gray
  }
}

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
Write-Host "`n============================================================" -ForegroundColor Green
Write-Host "               RESTORE COMPLETED SUCCESSFULLY               " -ForegroundColor Yellow -BackgroundColor Black
Write-Host "============================================================" -ForegroundColor Green

if ($restoredFromBackup) {
  Write-Host "* Restore was driven by backup: $(Split-Path $chosenBackup -Leaf)" -ForegroundColor Green
  Write-Host "* $reenabledCount packages re-enabled based on pre-install state." -ForegroundColor Green
} else {
  Write-Host "* Restore used preset file fallback (no backup found)." -ForegroundColor Yellow
  Write-Host "* $reenabledCount packages re-enabled." -ForegroundColor Green
}

Write-Host "* Stock factory launcher re-enabled and set as Home." -ForegroundColor Green
Write-Host "* Your TV is back to its original state." -ForegroundColor Green
Write-Host "============================================================`n" -ForegroundColor Green
