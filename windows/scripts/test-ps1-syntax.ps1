$scripts = @('install.ps1','restore.ps1','windows\scripts\build-exe.ps1','windows\scripts\build-msix.ps1')
$allOk = $true
foreach ($s in $scripts) {
    $errors = $null
    $null = [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path $s).Path, [ref]$null, [ref]$errors)
    if ($errors.Count -gt 0) {
        Write-Host "FAIL: $s - $($errors.Count) error(s)" -ForegroundColor Red
        $errors | ForEach-Object { Write-Host "  $_" }
        $allOk = $false
    } else {
        Write-Host "PASS: $s" -ForegroundColor Green
    }
}
if (-not $allOk) { exit 1 } else { Write-Host "`nAll PowerShell scripts: syntax OK" -ForegroundColor Green }
