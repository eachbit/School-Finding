@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title 校园失物招领 - 停止

set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PS%" set "PS=powershell.exe"

echo.
set "TARGET="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /r /c:"LISTENING" ^| findstr /c:":8080 "') do set "TARGET=%%a"

if not defined TARGET (
    echo   8080 没有在监听，服务未运行。
    goto end
)

echo   正在停止 PID !TARGET! ...
"%PS%" -NoProfile -Command "try { Stop-Process -Id !TARGET! -Force -ErrorAction Stop; exit 0 } catch { exit 1 }"
if errorlevel 1 (
    echo   停止失败，请手动关闭那个 java 日志窗口。
) else (
    echo   已停止 http://localhost:8080/
)

:end
echo.
pause
