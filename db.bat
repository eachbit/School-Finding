@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title 校园失物招领 - 数据库

rem 依次尝试：PATH 里的 mysql / 本机安装目录
set "MYSQL="
where mysql >nul 2>nul && set "MYSQL=mysql"
if not defined MYSQL if exist "D:\mysql\mysql-8.0.41-winx64\bin\mysql.exe" set "MYSQL=D:\mysql\mysql-8.0.41-winx64\bin\mysql.exe"
if not defined MYSQL if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not defined MYSQL if exist "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL=C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysql.exe"

if not defined MYSQL (
    echo   找不到 mysql.exe。
    echo   把它所在目录加到 PATH，或者打开本文件改掉 MYSQL 那一行的路径。
    echo.
    pause
    exit /b 1
)

if "%~1"=="" (
    echo   正在连接 school_finding  ^(root / 123456^) …
    echo   输入 exit 或按 Ctrl+C 退出。
    echo.
)

"%MYSQL%" -h127.0.0.1 -P3306 -uroot -p123456 --default-character-set=utf8mb4 school_finding %*
