# Auto-detects or downloads standalone Google ADB Platform-Tools
[CmdletBinding()]
param(
  [switch]$Quiet
)

function Write-Info($msg) {
  if (-not $Quiet) { Write-Host $msg -ForegroundColor Cyan }
}

function Write-Success($msg) {
  if (-not $Quiet) { Write-Host $msg -ForegroundColor Green }
}

function Write-Warn($msg) {
  if (-not $Quiet) { Write-Host $msg -ForegroundColor Yellow }
}

# 1. Check if adb is in system PATH
$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCmd) {
  Write-Info "Found ADB in system PATH: $($adbCmd.Source)"
  return $adbCmd.Source
}

# 2. Check local repo / known default directories
$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$localAdb = Join-Path $repoRoot "bin\platform-tools\adb.exe"
if (Test-Path $localAdb) {
  Write-Info "Found local ADB: $localAdb"
  return $localAdb
}

$commonPaths = @(
  "C:\adb\platform-tools\adb.exe",
  "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
  "$env:ProgramFiles\Android\platform-tools\adb.exe"
)

foreach ($path in $commonPaths) {
  if (Test-Path $path) {
    Write-Info "Found ADB at $path"
    return $path
  }
}

# 3. Not found: auto-download official Google standalone package
Write-Warn "ADB (Android Debug Bridge) not detected on your PC."
Write-Info "Downloading official Google Android Platform-Tools..."

$binDir = Join-Path $repoRoot "bin"
if (-not (Test-Path $binDir)) {
  New-Item -ItemType Directory -Force -Path $binDir | Out-Null
}

$zipUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
$zipFile = Join-Path $binDir "platform-tools.zip"

try {
  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
  $webClient = New-Object System.Net.WebClient
  $webClient.DownloadFile($zipUrl, $zipFile)
  
  Write-Info "Extracting platform-tools..."
  Expand-Archive -Path $zipFile -DestinationPath $binDir -Force
  Remove-Item $zipFile -Force -ErrorAction SilentlyContinue

  if (Test-Path $localAdb) {
    Write-Success "ADB installed successfully to bin\platform-tools\adb.exe"
    return $localAdb
  } else {
    throw "ADB extraction finished but adb.exe was not found."
  }
} catch {
  Write-Host "Error obtaining ADB: $($_.Exception.Message)" -ForegroundColor Red
  throw $_
}
