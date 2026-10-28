@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title 校园失物招领 - 启动

rem 用绝对路径找 PowerShell，避免某些环境 PATH 里没有 powershell
set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PS%" set "PS=powershell.exe"

echo.
echo   校园失物招领 · 本地启动
echo   ==========================================

rem ---------- 端口已占用：直接打开页面 ----------
"%PS%" -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1', 8080); $c.Close(); exit 0 } catch { exit 1 }"
if errorlevel 1 goto port_free
echo   [i] 8080 已在监听，服务应该已经起来了，直接打开页面。
if not defined SF_NO_BROWSER start "" "http://localhost:8080/"
goto end

:port_free
rem ---------- MySQL 提醒 ----------
"%PS%" -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1', 3306); $c.Close(); exit 0 } catch { exit 1 }"
if not errorlevel 1 goto mysql_ok
echo   [!] 3306 端口没有监听，MySQL 可能没启动。
echo       可以先执行:  net start MySQL
echo.
:mysql_ok

rem ---------- 优先用 Maven ----------
where mvn >nul 2>nul
if errorlevel 1 goto local_run

echo   [i] 检测到 Maven，使用 mvn spring-boot:run
echo.
call mvn spring-boot:run
goto end

:local_run
rem ---------- 回退：本地依赖 + javac ----------
echo   [i] 没检测到 Maven，改用本地 .m2 依赖 + javac 直接启动
echo.
"%PS%" -NoProfile -ExecutionPolicy Bypass -File "scripts\run-local.ps1"
if errorlevel 1 (
    echo.
    echo   [x] 启动失败。也可以直接用 IDEA 打开项目，运行 SchoolFindingApplication。
)

:end
echo.
pause
