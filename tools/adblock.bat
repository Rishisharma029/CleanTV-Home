@echo off
title CleanTV Ad-Blocker Manager
cd /d "%~dp0\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0adblock.ps1"
echo.
echo ============================================================
echo Press any key to close this window...
echo ============================================================
pause >nul
