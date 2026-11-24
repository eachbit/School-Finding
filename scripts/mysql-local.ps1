# 仅管理项目 .local 中的 MySQL 实例。
$projectRoot = Split-Path -Parent $PSScriptRoot
$mysqlRoot = Join-Path $projectRoot '.local/mysql'
$mysqlStatePath = Join-Path $mysqlRoot 'process.json'
$mysqlCredentialsPath = Join-Path $mysqlRoot 'credentials.json'
function Test-LocalPort([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $Port); return $true }
    catch { return $false }
    finally { $client.Dispose() }
}
function New-LocalPassword {
    $bytes = New-Object byte[] 24
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
function Get-MySqlBin {
    if ($env:MYSQL_HOME -and (Test-Path (Join-Path $env:MYSQL_HOME 'bin/mysqld.exe'))) { return Join-Path $env:MYSQL_HOME 'bin' }
    $command = Get-Command mysqld.exe -ErrorAction SilentlyContinue
    if ($command) { return Split-Path -Parent $command.Source }
    $candidates = @('D:/mysql/mysql-8.0.41-winx64/bin','C:/Program Files/MySQL/MySQL Server 8.0/bin')
    $shared = Get-ChildItem 'D:/HZX/Tools/mysql' -Filter mysqld.exe -Recurse -ErrorAction SilentlyContinue
    foreach ($binary in $shared) { $candidates += $binary.DirectoryName }
    foreach ($candidate in $candidates) { if (Test-Path (Join-Path $candidate 'mysqld.exe')) { return $candidate } }
    throw '找不到 MySQL 8，请设置 MYSQL_HOME 为安装目录。'
}
function Get-LocalDatabaseCredentials {
    if (-not (Test-Path $mysqlCredentialsPath)) { throw '本机数据库尚未初始化，请运行 start.bat。' }
    return Get-Content $mysqlCredentialsPath -Raw | ConvertFrom-Json
}
function Save-ManagedProcess([int]$ProcessId, [string]$IdentityPath, [int]$Port, [string]$StatePath) {
    $details = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId"
    if (-not $details) { throw '启动的进程已退出，请检查日志。' }
    @{ ProcessId = $ProcessId; CreationTime = $details.CreationDate.ToUniversalTime().ToString('o'); IdentityPath = $IdentityPath; Port = $Port } | ConvertTo-Json | Set-Content -LiteralPath $StatePath -Encoding UTF8
}
function Get-ManagedProcess([string]$StatePath) {
    if (-not (Test-Path $StatePath)) { return $null }
    $state = Get-Content $StatePath -Raw | ConvertFrom-Json
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($state.ProcessId)" -ErrorAction SilentlyContinue
    if ($process -and $process.CommandLine.Contains($state.IdentityPath) -and $process.CreationDate.ToUniversalTime().Ticks -eq ([datetime]$state.CreationTime).ToUniversalTime().Ticks) { return $state }
    return $null
}
function Invoke-LocalMysql {
    param([int]$Port, [string]$User, [string]$Password, [string[]]$MysqlArguments)
    $client = Join-Path (Get-MySqlBin) 'mysql.exe'
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        & $client --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$Port" "--user=$User" --default-character-set=utf8mb4 @MysqlArguments
        if ($LASTEXITCODE -ne 0) { throw "MySQL 命令执行失败，退出码 $LASTEXITCODE。" }
    } finally { $env:MYSQL_PWD = $previousPassword }
}
function Initialize-LocalDatabaseAccess([int]$Port) {
    $marker = Join-Path $mysqlRoot 'bootstrap-complete'
    if (Test-Path $marker) { return }
    $credentials = Get-LocalDatabaseCredentials
    $bootstrapPassword = $credentials.RootPassword
    try {
        Invoke-LocalMysql -Port $Port -User root -Password $bootstrapPassword -MysqlArguments @('--execute=SELECT 1', '--silent') 2>$null | Out-Null
    } catch { $bootstrapPassword = '' }
    $bootstrap = @"
CREATE DATABASE IF NOT EXISTS school_finding_local CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS school_finding_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS 'school_finding_app'@'127.0.0.1' IDENTIFIED BY '$($credentials.AppPassword)';
GRANT ALL ON school_finding_local.* TO 'school_finding_app'@'127.0.0.1';
GRANT ALL ON school_finding_test.* TO 'school_finding_app'@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$($credentials.RootPassword)';
"@
    Invoke-LocalMysql -Port $Port -User root -Password $bootstrapPassword -MysqlArguments @("--execute=$bootstrap")
    Set-Content -LiteralPath $marker -Value 'ready' -Encoding ASCII
}
function Assert-LocalMySqlPath([string]$Path) {
    $rootPath = [IO.Path]::GetFullPath($mysqlRoot).TrimEnd('\', '/')
    $fullPath = [IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($rootPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "MySQL 路径超出项目运行目录，已停止操作 $fullPath"
    }
    foreach ($existingPath in @($rootPath, $fullPath)) {
        if ((Test-Path -LiteralPath $existingPath) -and
            ((Get-Item -LiteralPath $existingPath -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "MySQL 路径含链接，需确认真实数据位置 $existingPath"
        }
    }
}
function Write-LocalMySqlConfig([string]$MysqlBin, [string]$DataPath, [string]$ConfigPath, [string]$LogPath, [int]$Port) {
    $configLines = @(
        '[mysqld]',
        "basedir=$((Split-Path -Parent $MysqlBin).Replace('\','/'))",
        "datadir=$($DataPath.Replace('\','/'))",
        "port=$Port",
        'bind-address=127.0.0.1',
        'mysqlx=OFF',
        'character-set-server=utf8mb4',
        'collation-server=utf8mb4_general_ci',
        "log-error=$($LogPath.Replace('\','/'))"
    )
    $encoding = [Text.Encoding]::GetEncoding([Globalization.CultureInfo]::CurrentCulture.TextInfo.ANSICodePage)
    [IO.File]::WriteAllLines($ConfigPath, $configLines, $encoding)
}
function Invoke-MySqlInitialization([string]$Server, [string]$StagePath) {
    $process = Start-Process -FilePath $Server -ArgumentList '--defaults-file=my.ini --initialize-insecure --no-monitor' `
        -WorkingDirectory $StagePath -WindowStyle Hidden -PassThru -Wait `
        -RedirectStandardOutput (Join-Path $StagePath 'initialize-out.log') `
        -RedirectStandardError (Join-Path $StagePath 'initialize-error.log')
    if ($process.ExitCode -ne 0) {
        throw "MySQL 初始化失败，暂存数据和日志保留于 $StagePath"
    }
}
function Initialize-LocalDatabaseData([string]$MysqlBin, [int]$Port) {
    $dataPath = Join-Path $mysqlRoot 'data'
    Assert-LocalMySqlPath $dataPath
    if (Test-Path -LiteralPath $dataPath) {
        if (Test-Path -LiteralPath (Join-Path $mysqlRoot 'bootstrap-complete')) { return }
        $hasSystemTables = (Test-Path -LiteralPath (Join-Path $dataPath 'mysql.ibd')) -and
            (Test-Path -LiteralPath (Join-Path $dataPath 'mysql') -PathType Container)
        if ($hasSystemTables) { return }
        $children = @(Get-ChildItem -LiteralPath $dataPath -Force)
        $unknownEntries = @($children | Where-Object {
            if ($_.PSIsContainer) { return $_.Name -notin @('#innodb_redo', '#innodb_temp') }
            return $_.Name -notmatch '^(auto\.cnf|ibdata1|undo_\d+|#ib_.*\.dblwr|[^.]+\.pem|ib_buffer_pool)$'
        })
        if ($unknownEntries.Count -gt 0 -or (Test-Path -LiteralPath (Join-Path $dataPath 'mysql.ibd')) -or
            (Test-Path -LiteralPath (Join-Path $dataPath 'mysql'))) {
            throw '已有 MySQL 数据目录的初始化状态无法确定，已保留原目录，请检查 .local/mysql/data。'
        }
        $backupPath = Join-Path $mysqlRoot ('incomplete-' + [guid]::NewGuid().ToString('N'))
        Assert-LocalMySqlPath $backupPath
        [IO.Directory]::Move($dataPath, $backupPath)
        Write-Host "未完成的初始化目录已备份至 $backupPath"
    }
    $stagePath = Join-Path $mysqlRoot ('stage-' + [guid]::NewGuid().ToString('N'))
    $stageDataPath = Join-Path $stagePath 'data'
    Assert-LocalMySqlPath $stagePath
    New-Item -ItemType Directory -Path $stagePath -ErrorAction Stop | Out-Null
    Write-LocalMySqlConfig -MysqlBin $MysqlBin -DataPath $stageDataPath -ConfigPath (Join-Path $stagePath 'my.ini') `
        -LogPath (Join-Path $stagePath 'mysql-initialize.log') -Port $Port
    Invoke-MySqlInitialization -Server (Join-Path $MysqlBin 'mysqld.exe') -StagePath $stagePath
    if (-not (Test-Path -LiteralPath (Join-Path $stageDataPath 'mysql.ibd')) -or
        -not (Test-Path -LiteralPath (Join-Path $stageDataPath 'auto.cnf')) -or
        -not (Test-Path -LiteralPath (Join-Path $stageDataPath 'mysql') -PathType Container)) {
        throw "MySQL 初始化未生成完整系统表，暂存数据和日志保留于 $stagePath"
    }
    Assert-LocalMySqlPath $stageDataPath
    Assert-LocalMySqlPath $dataPath
    if (Test-Path -LiteralPath $dataPath) { throw '正式数据目录已存在，暂存目录已保留，请检查并发启动。' }
    [IO.Directory]::Move($stageDataPath, $dataPath)
}
function Start-LocalDatabase([int]$Port = 3307) {
    $existing = Get-ManagedProcess -StatePath $mysqlStatePath
    if ($existing) {
        if ($existing.Port -ne $Port) { throw "本机实例正在使用 $($existing.Port)，请使用相同的 -DatabasePort。" }
        $credentials = Get-LocalDatabaseCredentials
        Initialize-LocalDatabaseAccess -Port $Port
        Invoke-LocalMysql -Port $Port -User root -Password $credentials.RootPassword -MysqlArguments @('--execute=SELECT 1','--silent') | Out-Null
        Write-Host "MySQL 已就绪，127.0.0.1:$Port。"
        return
    }
    if (Test-LocalPort $Port) { throw "数据库端口 $Port 已被其他进程使用，请指定 -DatabasePort。" }
    $mysqlBin = Get-MySqlBin
    $server = Join-Path $mysqlBin 'mysqld.exe'
    $dataPath = Join-Path $mysqlRoot 'data'
    $configPath = Join-Path $mysqlRoot 'my.ini'
    $logPath = Join-Path $mysqlRoot 'mysql-error.log'
    New-Item -ItemType Directory -Path $mysqlRoot -Force | Out-Null
    if (-not (Test-Path $mysqlCredentialsPath)) {
        if (Test-Path $dataPath) { throw '已有 MySQL 数据目录但缺少凭证文件，请恢复 .local/mysql/credentials.json 后启动。' }
        @{ RootPassword = New-LocalPassword; AppPassword = New-LocalPassword } | ConvertTo-Json | Set-Content -LiteralPath $mysqlCredentialsPath -Encoding UTF8
    }
    $credentials = Get-LocalDatabaseCredentials
    Initialize-LocalDatabaseData -MysqlBin $mysqlBin -Port $Port
    Write-LocalMySqlConfig -MysqlBin $mysqlBin -DataPath $dataPath -ConfigPath $configPath -LogPath $logPath -Port $Port
    $process = Start-Process -FilePath $server -ArgumentList '--defaults-file=my.ini --no-monitor' -WorkingDirectory $mysqlRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $mysqlRoot 'server-out.log') -RedirectStandardError (Join-Path $mysqlRoot 'server-err.log')
    Save-ManagedProcess -ProcessId $process.Id -IdentityPath '--defaults-file=my.ini' -Port $Port -StatePath $mysqlStatePath
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $process.Refresh()
        if ($process.HasExited) { throw "MySQL 已退出，请查看 $logPath。" }
        if (Test-LocalPort $Port) { break }
        Start-Sleep -Seconds 1
    }
    if (-not (Test-LocalPort $Port)) { throw "MySQL 启动超时，请查看 $logPath。" }
    Initialize-LocalDatabaseAccess -Port $Port
    Invoke-LocalMysql -Port $Port -User root -Password $credentials.RootPassword -MysqlArguments @('--execute=SELECT 1','--silent') | Out-Null
    Write-Host "MySQL 已就绪，127.0.0.1:$Port，数据位于 .local/mysql/data。"
}
function Stop-LocalDatabase {
    $state = Get-ManagedProcess -StatePath $mysqlStatePath
    if (-not $state) { Write-Host '未发现本项目管理的 MySQL 进程。'; return }
    $credentials = Get-LocalDatabaseCredentials
    $client = Join-Path (Get-MySqlBin) 'mysqladmin.exe'
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $credentials.RootPassword
        & $client --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$($state.Port)" --user=root shutdown
        if ($LASTEXITCODE -ne 0) { throw 'MySQL 正常关闭失败，请检查日志。' }
        Wait-Process -Id $state.ProcessId -Timeout 30 -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $mysqlStatePath -Force
        Write-Host '本项目 MySQL 已正常停止。'
    } finally { $env:MYSQL_PWD = $previousPassword }
}
