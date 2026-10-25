@echo off
title CleanTV Home Installer
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
echo.
echo ============================================================
echo Press any key to close this window...
echo ============================================================
pause >nul
