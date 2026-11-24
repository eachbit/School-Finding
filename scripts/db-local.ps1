$MysqlArguments = $args
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'mysql-local.ps1')
try {
    $state = Get-ManagedProcess -StatePath $mysqlStatePath
    if (-not $state) { throw '本项目 MySQL 未运行，请先运行 start.bat。' }
    $credentials = Get-LocalDatabaseCredentials
    Invoke-LocalMysql -Port $state.Port -User root -Password $credentials.RootPassword -MysqlArguments (@('--database=school_finding_local') + $MysqlArguments)
} catch { Write-Error $_.Exception.Message; exit 1 }
