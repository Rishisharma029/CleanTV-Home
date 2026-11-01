@echo off
setlocal
echo Starting CleanTV Home Desktop Manager...
if not exist "%~dp0\windows\CleanTV.exe" (
    call "%~dp0\build-windows.bat"
)
start "" "%~dp0\windows\CleanTV.exe"
endlocal
