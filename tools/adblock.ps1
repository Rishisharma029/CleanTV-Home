# CleanTV Home - Ad-Blocking Suite for Android TV
[CmdletBinding()]
param(
  [string]$TvIp,
  [ValidateSet('interactive', 'enable-dns', 'disable-dns', 'install-tile', 'install-smarttube', 'status')]
  [string]$Action = 'interactive',
  [string]$Provider = 'adguard',
  [string]$AdbPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path

function Show-Header {
  Clear-Host
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host "         CleanTV Home - Universal Ad-Blocking Suite         " -ForegroundColor Yellow -BackgroundColor Black
  Write-Host "   Zero-Overhead DNS Blocker, Dashboard Card & Ad-Free Video " -ForegroundColor White
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

# 2. Connect to TV
if (-not $TvIp) {
  $defaultIp = "192.168.0.159"
  $inputIp = Read-Host "Enter your TV IP address [Press Enter for default: $defaultIp]"
  $TvIp = if ([string]::IsNullOrWhiteSpace($inputIp)) { $defaultIp } else { $inputIp.Trim() }
}

$targetSerial = if ($TvIp -match ':') { $TvIp } else { "$TvIp`:5555" }
Write-Host "Connecting to TV at $targetSerial..." -ForegroundColor Cyan
& $adbExe connect $targetSerial | Out-Null

$state = (& $adbExe -s $targetSerial get-state 2>&1) -join ""
if ("$state".Trim() -ne 'device') {
  Write-Host "[ERROR] TV is not connected or unauthorized. Ensure USB Debugging is ON." -ForegroundColor Red
  exit 1
}

# 3. Read Current Status
$dnsMode = (& $adbExe -s $targetSerial shell settings get global private_dns_mode 2>&1).Trim()
$dnsHost = (& $adbExe -s $targetSerial shell settings get global private_dns_specifier 2>&1).Trim()
$hasTile = & $adbExe -s $targetSerial shell pm list packages io.github.maxhayim.adblocking
$hasSmartTube = & $adbExe -s $targetSerial shell pm list packages com.liskovsoft.smarttubetv.beta

Write-Host "Current TV Ad-Blocking Status:" -ForegroundColor Yellow
if ($dnsMode -eq 'hostname' -and $dnsHost) {
  Write-Host "  • Private DNS:        ACTIVE [Hostname: $dnsHost]" -ForegroundColor Green
} else {
  Write-Host "  • Private DNS:        OFF / Default ISP" -ForegroundColor Gray
}
Write-Host "  • Dashboard Card:     $(if ($hasTile -match 'adblocking') { 'INSTALLED' } else { 'Not Installed' })" -ForegroundColor Gray
Write-Host "  • Ad-Free YouTube:    $(if ($hasSmartTube -match 'smarttube') { 'INSTALLED' } else { 'Not Installed' })" -ForegroundColor Gray
Write-Host ""

# 4. Interactive Menu
if ($Action -eq 'interactive') {
  Write-Host "Select an Ad-Blocking Option:" -ForegroundColor Cyan
  Write-Host "  [1] Enable System-Wide Ad-Blocking DNS (AdGuard DNS - Zero RAM/CPU, blocks ads across all apps)" -ForegroundColor White
  Write-Host "  [2] Enable System-Wide Privacy DNS (Control D - High speed ad/tracker blocking)" -ForegroundColor White
  Write-Host "  [3] Install CleanTV Ad-Block Dashboard Card (Pi-hole / AdGuard Home dock widget with pause)" -ForegroundColor White
  Write-Host "  [4] Install Ad-Free 4K YouTube Client (SmartTube - No video ads, SponsorBlock)" -ForegroundColor White
  Write-Host "  [5] Disable Private DNS (Revert back to stock network DNS)" -ForegroundColor White
  Write-Host "  [Q] Exit`n" -ForegroundColor Gray

  $choice = Read-Host "Enter option [1-5 or Q]"
  switch ($choice) {
    "1" { $Action = "enable-dns"; $Provider = "adguard" }
    "2" { $Action = "enable-dns"; $Provider = "controld" }
    "3" { $Action = "install-tile" }
    "4" { $Action = "install-smarttube" }
    "5" { $Action = "disable-dns" }
    default { exit 0 }
  }
}

# Execute Action
switch ($Action) {
  "enable-dns" {
    $dnsHostname = switch ($Provider) {
      "controld" { "p2.freedns.controld.com" }
      "mullvad"  { "adblock.doh.mullvad.net" }
      default    { "dns.adguard-dns.com" }
    }

    Write-Host "`nSetting System-Wide Private DNS to: $dnsHostname..." -ForegroundColor Cyan
    & $adbExe -s $targetSerial shell settings put global private_dns_mode hostname | Out-Null
    & $adbExe -s $targetSerial shell settings put global private_dns_specifier $dnsHostname | Out-Null

    Write-Host "✓ Ad-Blocking DNS successfully activated!" -ForegroundColor Green
    Write-Host "  - Zero TV memory or CPU overhead." -ForegroundColor Gray
    Write-Host "  - System-wide filtering for banner ads, telemetry, and tracking." -ForegroundColor Gray
  }

  "disable-dns" {
    Write-Host "`nDisabling Private DNS..." -ForegroundColor Yellow
    & $adbExe -s $targetSerial shell settings put global private_dns_mode off | Out-Null
    & $adbExe -s $targetSerial shell settings delete global private_dns_specifier | Out-Null

    Write-Host "✓ Private DNS turned off. TV is now using standard router DNS." -ForegroundColor Green
  }

  "install-tile" {
    $tileApk = Join-Path $repoRoot "apks\addons\ad-blocking.apk"
    if (-not (Test-Path $tileApk)) {
      Write-Host "[ERROR] Tile APK not found at $tileApk." -ForegroundColor Red
      exit 1
    }

    Write-Host "`nInstalling CleanTV Ad-Block Dashboard Card to TV..." -ForegroundColor Cyan
    $res = & $adbExe -s $targetSerial install -r $tileApk
    $res | Out-Host

    Write-Host "✓ Ad-Blocking tile installed!" -ForegroundColor Green
    Write-Host "  - Open the tile on your TV to connect to Pi-hole or AdGuard Home." -ForegroundColor Gray
    Write-Host "  - Hold or tap OK on the card to pause blocking for 5 minutes if needed." -ForegroundColor Gray
  }

  "install-smarttube" {
    Write-Host "`nDownloading latest stable SmartTube (Ad-Free YouTube for TV)..." -ForegroundColor Cyan
    $smartTubeDir = Join-Path $repoRoot "bin"
    $smartTubeApk = Join-Path $smartTubeDir "smarttube-stable.apk"
    $downloadUrl = "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_beta.apk"

    try {
      [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
      $wc = New-Object System.Net.WebClient
      $wc.DownloadFile($downloadUrl, $smartTubeApk)

      Write-Host "Installing SmartTube to TV..." -ForegroundColor Cyan
      $res = & $adbExe -s $targetSerial install -r $smartTubeApk
      $res | Out-Host

      Write-Host "✓ SmartTube installed successfully!" -ForegroundColor Green
      Write-Host "  - 4K 60fps HDR, SponsorBlock, and zero video advertisements." -ForegroundColor Gray
    } catch {
      Write-Host "[WARN] Automated download failed: $($_.Exception.Message)" -ForegroundColor Yellow
      Write-Host "You can download the APK directly from: https://github.com/yuliskov/SmartTube" -ForegroundColor Gray
    }
  }
}

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "Done! Enjoy an ad-free TV experience." -ForegroundColor Green
Write-Host "============================================================`n" -ForegroundColor Cyan
