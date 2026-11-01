# CleanTV Home Desktop Manager - Root Windows Build Script
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$windowsDir = Join-Path $root "windows"

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "CleanTV Home Desktop Manager - Windows MSIX Builder" -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan

# 1. Compile CleanTV.exe
Write-Host "`n[Step 1/3] Compiling native Windows binary (CleanTV.exe)..." -ForegroundColor Yellow
& powershell -ExecutionPolicy Bypass -File (Join-Path $windowsDir "scripts\build-exe.ps1")

# 2. Package MSIX
Write-Host "`n[Step 2/3] Building Store-compliant MSIX package..." -ForegroundColor Yellow
& powershell -ExecutionPolicy Bypass -File (Join-Path $windowsDir "scripts\build-msix.ps1")

# 3. Test & Validate
Write-Host "`n[Step 3/3] Validating Manifest and MSIX Package..." -ForegroundColor Yellow
& node (Join-Path $windowsDir "tests\test-manifest.js")

Write-Host "`n=======================================================" -ForegroundColor Green
Write-Host "[SUCCESS] Windows Store MSIX Package is ready!" -ForegroundColor Green
Write-Host "Output: $(Join-Path $windowsDir 'dist\CleanTV-Desktop-Manager-v4.0.0.msix')" -ForegroundColor Green
Write-Host "=======================================================" -ForegroundColor Green
