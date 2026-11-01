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

node tests\test-backend-integration.js
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Backend integration tests failed!
    exit /b %ERRORLEVEL%
)

node tests\test-csharp-backend.js
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Shipped CleanTV.exe C# backend tests failed!
    exit /b %ERRORLEVEL%
)

echo.
echo =======================================================
echo [SUCCESS] Windows Store MSIX Package is ready!
echo Output: windows\dist\CleanTV-Desktop-Manager-v4.0.0.msix
echo =======================================================
endlocal
