<#
    校园失物招领 · 不依赖 Maven 的本地启动脚本

    用途：机器上没装 Maven，但本地 .m2 里已经有依赖时，直接编译 + 启动。
    做法：用 javac 编译 src/main/java，再拼 classpath 跑 Spring Boot。

    用法：
        powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
        powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1 -Port 8081 -NoBrowser
#>
param(
    [int]$Port = 8080,
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Write-Step($text) { Write-Host "[*] $text" -ForegroundColor Cyan }
function Write-Ok($text) { Write-Host "[+] $text" -ForegroundColor Green }
function Write-Warn2($text) { Write-Host "[!] $text" -ForegroundColor Yellow }
function Fail($text) { Write-Host "[x] $text" -ForegroundColor Red; exit 1 }

function Test-Port([int]$p) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $client.Connect('127.0.0.1', $p)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

# ---------- 1. 找 JDK ----------
$javaExe = $null
$javacExe = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $candidate) {
        $javaExe = $candidate
        $javacCandidate = Join-Path $env:JAVA_HOME 'bin\javac.exe'
        if (Test-Path $javacCandidate) { $javacExe = $javacCandidate }
    }
}
if (-not $javaExe) {
    $found = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($found) { $javaExe = $found.Source }
    $foundJavac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($foundJavac) { $javacExe = $foundJavac.Source }
}
if (-not $javaExe) {
    Fail '找不到 java。请安装 JDK 17 或以上，并设置 JAVA_HOME，或者直接用 IDEA 运行 SchoolFindingApplication。'
}
Write-Step "Java: $javaExe"

# ---------- 2. 端口检查 ----------
if (Test-Port $Port) {
    Write-Warn2 "端口 $Port 已经在监听，服务可能已经启动了。"
    if (-not $NoBrowser -and -not $env:SF_NO_BROWSER) { Start-Process "http://localhost:$Port/" }
    exit 0
}

# ---------- 3. MySQL 提醒 ----------
if (-not (Test-Port 3306)) {
    Write-Warn2 '3306 端口没有监听，MySQL 可能没启动。先执行 net start MySQL，否则服务会启动失败。'
}

# ---------- 4. 收集依赖 ----------
$repo = Join-Path $env:USERPROFILE '.m2\repository'
if (-not (Test-Path $repo)) {
    Fail '找不到本地 Maven 仓库（~/.m2/repository）。请先用 Maven 构建一次以下载依赖，或用 IDEA 运行。'
}

$allow = '^(org\\springframework|org\\apache\\tomcat|org\\apache\\logging|org\\apache\\commons\\commons-lang3\\3\.13\.0|org\\mybatis|org\\slf4j|ch\\qos|com\\fasterxml|com\\mysql|com\\zaxxer|com\\github\\xiaoymin|io\\swagger|org\\webjars|org\\springdoc|jakarta\\|org\\hibernate|io\\micrometer|org\\yaml|com\\thoughtworks|org\\jboss|org\\glassfish|org\\projectlombok)'

$jars = Get-ChildItem $repo -Recurse -Filter '*.jar' | Where-Object {
    $rel = $_.FullName.Substring($repo.Length + 1)
    ($rel -match $allow) -and
    ($rel -notmatch 'slf4j-api\\1\.7\.36') -and ($rel -notmatch '\\3\.5\.6\\') -and ($rel -notmatch '\\6\.2\.11\\') -and
    ($rel -notmatch '\\10\.1\.46\\') -and ($rel -notmatch '\\2\.19\.2\\') -and ($rel -notmatch 'mybatis.3\.5\.19') -and
    ($rel -notmatch 'mybatis-spring\\3\.0\.5') -and ($rel -notmatch '\\6\.3\.3\\') -and ($rel -notmatch '\\1\.5\.18\\') -and
    ($rel -notmatch '\\2\.0\.17\\') -and ($rel -notmatch '\\2\.24\.3\\') -and ($rel -notmatch '\\9\.4\.0\\') -and
    ($rel -notmatch 'micrometer.*\\1\.14\.11\\') -and ($rel -notmatch 'micrometer.*\\1\.15\.4\\') -and
    ($rel -notmatch 'snakeyaml\\2\.4') -and ($rel -notmatch 'sources\.jar$') -and ($rel -notmatch 'javadoc\.jar$')
} | Select-Object -ExpandProperty FullName

if (-not $jars -or $jars.Count -lt 20) {
    Fail "本地仓库里只找到 $($jars.Count) 个依赖，说明依赖没下全。请先用 Maven 构建一次，或用 IDEA 运行。"
}
Write-Step "依赖: $($jars.Count) 个 jar"

# ---------- 5. 需要时编译 ----------
$classes = Join-Path $root 'target\classes'
$mainClass = Join-Path $classes 'com\project\SchoolFindingApplication.class'
$sourceDir = Join-Path $root 'src\main\java'
$sources = Get-ChildItem $sourceDir -Recurse -Filter '*.java'

$needCompile = -not (Test-Path $mainClass)
if (-not $needCompile) {
    $newest = ($sources | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
    if ($newest -gt (Get-Item $mainClass).LastWriteTime) { $needCompile = $true }
}

if ($needCompile) {
    if (-not $javacExe) {
        Fail '需要重新编译，但找不到 javac（说明装的是 JRE 而不是 JDK）。请安装 JDK 17+。'
    }
    Write-Step "编译 $($sources.Count) 个源文件…"
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    $compileCp = $jars -join ';'
    & $javacExe -encoding UTF-8 -nowarn -cp $compileCp -d $classes @($sources.FullName)
    if ($LASTEXITCODE -ne 0) { Fail '编译失败，请看上面的错误信息。' }
    Write-Ok '编译完成'
} else {
    Write-Step '检测到已编译的 class，跳过编译'
}

# ---------- 6. 启动 ----------
$cpParts = @((Join-Path $root 'src\main\resources'), $classes) + $jars
$runtimeCp = $cpParts -join ';'

Write-Step "启动服务（端口 $Port，日志在另一个窗口）…"
$proc = Start-Process -FilePath $javaExe `
    -ArgumentList @('-cp', $runtimeCp, '-Dfile.encoding=UTF-8', '-Dspring.main.banner-mode=off',
    'com.project.SchoolFindingApplication', "--server.port=$Port") `
    -WorkingDirectory $root -PassThru

$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Milliseconds 1000
    if ($proc.HasExited) { break }
    if (Test-Port $Port) { $ok = $true; break }
}

if (-not $ok) {
    Write-Host ''
    Fail "启动失败（进程已退出或端口一直没起来）。常见原因：MySQL 没启动、数据库没初始化（sql/init.sql）、$Port 被占用。"
}

Write-Host ''
Write-Ok "服务已启动：http://localhost:$Port/   PID=$($proc.Id)"
Write-Host '    学生：student1 / 123456     管理员：admin / 123456'
Write-Host '    停止：关掉那个 java 日志窗口，或双击 stop.bat'
Write-Host ''

if (-not $NoBrowser -and -not $env:SF_NO_BROWSER) {
    Start-Process "http://localhost:$Port/"
}
