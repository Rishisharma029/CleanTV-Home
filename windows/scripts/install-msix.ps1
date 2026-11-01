# Install / Sideload CleanTV MSIX package on local Windows PC
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$windowsDir = Split-Path -Parent $scriptDir
$msixPath = Join-Path $windowsDir "dist\CleanTV-Desktop-Manager-v4.0.0.msix"

if (-not (Test-Path $msixPath)) {
    Write-Error "MSIX package not found at $msixPath. Run build-msix.ps1 first."
    exit 1
}

Write-Host "Installing CleanTV MSIX Package: $msixPath"
Add-AppxPackage -Path "$msixPath"

Write-Host "CleanTV Desktop Manager installed successfully!"
Write-Host "You can now launch CleanTV from the Windows Start Menu or search for 'CleanTV'."
