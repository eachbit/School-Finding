param(
    [ValidateSet('Start','Stop','Status','Build','DatabaseStart','DatabaseStop')][string]$Action = 'Start',
    [int]$Port = 8080,
    [int]$DatabasePort = 3307,
    [ValidateSet('school_finding_local','school_finding_test')][string]$DatabaseName = 'school_finding_local',
    [string]$UploadDirectory,
    [switch]$NoBrowser,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localRoot = Join-Path $projectRoot '.local'
$logRoot = Join-Path $localRoot 'logs'
$appStatePath = Join-Path $localRoot "app-$Port-process.json"
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
Set-Location $projectRoot
. (Join-Path $PSScriptRoot 'mysql-local.ps1')

function Get-Java {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { return Join-Path $env:JAVA_HOME 'bin/java.exe' }
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $command) { throw '找不到 Java，请安装 JDK 17 或以上并设置 JAVA_HOME。' }
    return $command.Source
}
function Get-Maven {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    foreach ($mavenHome in @($env:MAVEN_HOME, $env:M2_HOME)) {
        if ($mavenHome -and (Test-Path (Join-Path $mavenHome 'bin/mvn.cmd'))) { return Join-Path $mavenHome 'bin/mvn.cmd' }
    }
    $candidates = @(Get-ChildItem 'D:/HZX/Tools/maven', 'D:/HZX/Tools/apache-maven', (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue)
    if ($candidates.Count -eq 0) { throw '找不到 Maven，请设置 MAVEN_HOME，或将 Maven 安装到 D:/HZX/Tools/maven。' }
    return ($candidates | Sort-Object FullName -Descending | Select-Object -First 1).FullName
}
function Test-Application([int]$ApplicationPort) {
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:$ApplicationPort/api/health" -TimeoutSec 3
        $health = $response
        if ($response.data) { $health = $response.data }
        return $health.status -eq 'UP' -and $health.application -eq 'School-Finding' -and $health.database -eq 'UP'
    } catch { return $false }
}
try {
    if ($Action -eq 'DatabaseStart') { Start-LocalDatabase -Port $DatabasePort; exit 0 }
    if ($Action -eq 'DatabaseStop') { Stop-LocalDatabase; exit 0 }
    if ($Action -eq 'Stop') {
        $state = Get-ManagedProcess -StatePath $appStatePath
        if ($state) {
            Stop-Process -Id $state.ProcessId -ErrorAction Stop
            Wait-Process -Id $state.ProcessId -Timeout 20 -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $appStatePath -Force
            Write-Host '应用已停止，数据库继续运行。'
        } else { Write-Host '未发现本项目管理的应用进程。' }
        exit 0
    }
    if ($Action -eq 'Status') {
        $state = Get-ManagedProcess -StatePath $appStatePath
        if ($state -and (Test-Application $state.Port)) {
            Write-Host "应用与数据库健康，http://127.0.0.1:$($state.Port)/，PID=$($state.ProcessId)"
            exit 0
        }
        throw '应用未就绪，请检查 .local/logs。'
    }
    $java = Get-Java
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)
    $jarPath = Join-Path $projectRoot 'target/school-finding-1.0.0.jar'
    if ($Action -eq 'Start') {
        $existing = Get-ManagedProcess -StatePath $appStatePath
        if ($existing) {
            if (Test-Application $existing.Port) { Write-Host "应用已运行，http://127.0.0.1:$($existing.Port)/，PID=$($existing.ProcessId)"; exit 0 }
            throw '已有本项目进程但健康检查失败，请检查日志，必要时执行 stop.bat。'
        }
        if (Test-LocalPort $Port) { throw "端口 $Port 已被其他进程使用，请选择 -Port。" }
    }
    if (-not $SkipBuild -or $Action -eq 'Build') {
        $maven = Get-Maven
        Write-Host "使用 Maven 构建 $maven"
        & $maven -B '-Dstyle.color=never' '-DskipTests' package
        if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败，请查看上方输出。' }
    }
    if ($Action -eq 'Build') { exit 0 }
    if (-not (Test-Path $jarPath)) { throw '找不到可执行 JAR，请先构建。' }
    Start-LocalDatabase -Port $DatabasePort
    $credentials = Get-LocalDatabaseCredentials
    $env:DB_HOST = '127.0.0.1'
    $env:DB_PORT = [string]$DatabasePort
    $env:DB_NAME = $DatabaseName
    $env:DB_USERNAME = 'school_finding_app'
    $env:DB_PASSWORD = $credentials.AppPassword
    $uploadPath = Join-Path $localRoot 'uploads'
    if ($UploadDirectory) { $uploadPath = $UploadDirectory }
    $env:UPLOAD_DIRECTORY = [IO.Path]::GetFullPath($uploadPath)
    foreach ($schema in @('sql/init.sql', 'sql/migrate.sql')) {
        $schemaPath = Join-Path $projectRoot $schema
        if (Test-Path $schemaPath) {
            Invoke-LocalMysql -Port $DatabasePort -User root -Password $credentials.RootPassword -MysqlArguments @("--database=$DatabaseName", "--execute=source $($schema.Replace('\', '/'))")
        }
    }
    $adminPath = Join-Path $localRoot 'admin-credentials.txt'
    if (-not (Test-Path $adminPath)) {
        $adminName = 'admin'
        if ($env:ADMIN_USERNAME) { $adminName = $env:ADMIN_USERNAME }
        $adminPassword = New-LocalPassword
        if ($env:ADMIN_PASSWORD) { $adminPassword = $env:ADMIN_PASSWORD }
        @('# 仅用于本机首次初始化，已有管理员密码不会被重置。', "username=$adminName", "password=$adminPassword") | Set-Content -LiteralPath $adminPath -Encoding UTF8
    }
    $admin = @{}
    Get-Content $adminPath | Where-Object { $_ -match '^(username|password)=' } | ForEach-Object { $entry = $_.Split('=', 2); $admin[$entry[0]] = $entry[1] }
    $env:ADMIN_USERNAME = $admin.username
    $env:ADMIN_PASSWORD = $admin.password
    $runtimeRoot = Join-Path $localRoot 'runtime'
    New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
    $runtimeJarPath = Join-Path $runtimeRoot "app-$Port.jar"
    Copy-Item -LiteralPath $jarPath -Destination $runtimeJarPath -Force
    $arguments = @('-Dfile.encoding=UTF-8', '-jar', ('"' + $runtimeJarPath + '"'), '--server.address=127.0.0.1', "--server.port=$Port")
    $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logRoot "application-$Port.log") -RedirectStandardError (Join-Path $logRoot "application-$Port-error.log")
    Save-ManagedProcess -ProcessId $process.Id -IdentityPath $runtimeJarPath -Port $Port -StatePath $appStatePath
    $ready = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $process.Refresh()
        if ($process.HasExited) { break }
        if (Test-Application $Port) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue }
        throw "应用健康检查失败，请查看 .local/logs/application-$Port.log 和 application-$Port-error.log。"
    }
    Write-Host "应用和数据库已就绪，http://127.0.0.1:$Port/，PID=$($process.Id)"
    Write-Host '管理员首次初始化凭证保存在 .local/admin-credentials.txt。'
    if (-not $NoBrowser -and -not $env:SF_NO_BROWSER) { Start-Process "http://127.0.0.1:$Port/" }
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
