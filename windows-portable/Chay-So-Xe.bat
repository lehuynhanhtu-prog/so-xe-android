@echo off
setlocal
cd /d "%~dp0"

start "So Xe" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0SoXeServer.ps1"

endlocal

