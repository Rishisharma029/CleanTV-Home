@echo off
setlocal
echo =======================================================
echo CleanTV Home Desktop Manager - Windows MSIX Builder
echo =======================================================

cd /d "%~dp0\windows"
powershell -ExecutionPolicy Bypass -File scripts\build-exe.ps1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] CleanTV.exe build failed!
    exit /b %ERRORLEVEL%
)

powershell -ExecutionPolicy Bypass -File scripts\build-msix.ps1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] MSIX packaging failed!
    exit /b %ERRORLEVEL%
)

node tests\test-manifest.js
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] MSIX validation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo =======================================================
echo [SUCCESS] Windows Store MSIX Package is ready!
echo Output: windows\dist\CleanTV-Desktop-Manager-v4.0.0.msix
echo =======================================================
endlocal
