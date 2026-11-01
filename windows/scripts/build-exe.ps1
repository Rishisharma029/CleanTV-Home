# Build native CleanTV.exe using .NET Framework C# compiler
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$windowsDir = Split-Path -Parent $scriptDir
$srcFile = Join-Path $windowsDir "src\Program.cs"
$outFile = Join-Path $windowsDir "CleanTV.exe"

$cscCandidates = @(
    "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe",
    "C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe"
)

$csc = $null
foreach ($cand in $cscCandidates) {
    if (Test-Path $cand) {
        $csc = $cand
        break
    }
}

if (-not $csc) {
    Write-Error "Microsoft Visual C# Compiler (csc.exe) not found."
    exit 1
}

Write-Host "Compiling CleanTV.exe with $csc..."
& $csc /target:winexe /platform:x64 /optimize+ /r:System.Drawing.dll,System.Windows.Forms.dll /out:"$outFile" "$srcFile"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "Build successful: $outFile ($((Get-Item $outFile).Length) bytes)"
