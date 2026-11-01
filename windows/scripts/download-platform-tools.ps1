# Download Google's official Android Platform Tools (ADB) for Windows
# Saves to windows/platform-tools/ so the app uses them automatically
$ErrorActionPreference = 'Stop'

$scriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Definition
$windowsDir = Split-Path -Parent $scriptDir
$outDir     = Join-Path $windowsDir "platform-tools"
$zipPath    = Join-Path $env:TEMP "platform-tools-windows.zip"

$url = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"

if (Test-Path (Join-Path $outDir "adb.exe")) {
    $ver = & (Join-Path $outDir "adb.exe") version 2>&1 | Select-Object -First 1
    Write-Host "ADB already bundled: $ver" -ForegroundColor Green
    exit 0
}

Write-Host "Downloading Android Platform Tools from Google..." -ForegroundColor Cyan
Write-Host "  Source: $url" -ForegroundColor Gray

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing

Write-Host "Extracting to: $outDir" -ForegroundColor Cyan
if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
Expand-Archive -Path $zipPath -DestinationPath (Split-Path $outDir -Parent) -Force

Remove-Item $zipPath -Force

$adb = Join-Path $outDir "adb.exe"
if (-not (Test-Path $adb)) {
    Write-Error "adb.exe not found after extraction."
    exit 1
}

$verInfo = & $adb version 2>&1 | Select-Object -First 1
Write-Host "`n[SUCCESS] ADB bundled: $verInfo" -ForegroundColor Green
Write-Host "Path: $adb" -ForegroundColor Gray
Write-Host "`nThe Windows app will now use this bundled ADB automatically." -ForegroundColor Cyan
