@echo off
title CleanTV Home Restore
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore.ps1"
echo.
echo ============================================================
echo Press any key to close this window...
echo ============================================================
pause >nul
