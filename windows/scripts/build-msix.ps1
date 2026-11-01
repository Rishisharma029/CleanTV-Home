# CleanTV Desktop Manager - MSIX Build PowerShell Script
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$pyScript = Join-Path $scriptDir "build-msix.py"

Write-Host "Running MSIX Package Builder..."
python "$pyScript"

if ($LASTEXITCODE -ne 0) {
    Write-Error "MSIX Build failed with code $LASTEXITCODE"
    exit $LASTEXITCODE
}
