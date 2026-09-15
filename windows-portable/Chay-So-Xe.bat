@echo off
setlocal
cd /d "%~dp0"

if not exist "%~dp0SoXeServer.ps1" (
  echo Khong tim thay SoXeServer.ps1. Hay giai nen day du file ZIP roi chay lai.
  pause
  exit /b 1
)

start "So Xe" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0SoXeServer.ps1"

endlocal
