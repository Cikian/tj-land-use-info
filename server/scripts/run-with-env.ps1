<#
.SYNOPSIS
    从 server/.env 读取环境变量并启动 jeecg 单体版后端。

.DESCRIPTION
    Spring Boot 的 ${TJ_XXX:} 占位符只认「系统属性」与「进程环境变量」，
    **不会**自动读 .env 文件。这个脚本负责把 .env 注入当前进程的环境，
    再拉起应用 —— 这样 application-*.yml 里就不需要出现任何口令。

    为什么用脚本而不是引入 dotenv Maven/Gradle 插件：
    本工程的依赖是离线锁定的（.m2 里有什么才能用什么），
    为了读一个文本文件去加构建期依赖，代价大于收益；
    而启动入口本来就只有这一个，包一层脚本最直接。

    集成测试**不走**这个脚本：LandTestConfig 自己会读 .env，
    这样 `mvn test` 在 CI 上只靠真环境变量也能跑。

.PARAMETER Profile
    Spring profile，对应 application-<Profile>.yml。默认 dev。

.PARAMETER EnvFile
    .env 路径。默认取脚本上一级目录下的 .env（即 server/.env）。

.PARAMETER Jar
    直接指定可执行 jar 路径。给了就跳过 Maven，用 java -jar 启动。

.PARAMETER SkipBuild
    配合默认（Maven）模式使用：跳过 package，直接跑已构建好的 jar。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/run-with-env.ps1
    powershell -ExecutionPolicy Bypass -File scripts/run-with-env.ps1 -Profile prod

.NOTES
    ★ 口令不会打印到控制台 —— 只逐行回显「已注入哪些键」，
      避免构建/启动日志把口令带出去。
#>
[CmdletBinding()]
param(
    [string]$Profile = 'dev',
    [string]$EnvFile,
    [string]$Jar,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

# ---- 定位 .env：默认是脚本目录的上一级（scripts/ 的父目录 = server/）----
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $EnvFile) {
    $EnvFile = Join-Path (Split-Path -Parent $scriptDir) '.env'
}

if (-not (Test-Path $EnvFile)) {
    Write-Host "✗ 未找到 .env：$EnvFile" -ForegroundColor Red
    Write-Host ""
    Write-Host "  请先创建它（模板在 .env.example）：" -ForegroundColor Yellow
    Write-Host "      Copy-Item '$((Join-Path (Split-Path -Parent $scriptDir) '.env.example'))' '$EnvFile'"
    Write-Host "  然后填入本环境的真实口令。"
    exit 1
}

# ---- 解析 .env ----
# 只按「第一个 =」切分、只去掉行尾 \r：
#   值里可能含 @ # : 等字符，用 .NET Properties 或更复杂的解析都会把它们当分隔符/转义符。
$injected = @()
$lines = [System.IO.File]::ReadAllLines($EnvFile, [System.Text.Encoding]::UTF8)
foreach ($line in $lines) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    if ($line.TrimStart().StartsWith('#')) { continue }
    $eq = $line.IndexOf('=')
    if ($eq -le 0) { continue }
    $key = $line.Substring(0, $eq).Trim()
    $value = $line.Substring($eq + 1).TrimEnd("`r")
    # 用进程级环境变量：Spring 的 ${TJ_XXX:} 就能取到
    [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    $injected += $key
}

Write-Host "✓ 已从 $(Split-Path -Leaf $EnvFile) 注入 $($injected.Count) 个环境变量：" -ForegroundColor Green
Write-Host "    $($injected -join ', ')"
Write-Host ""

# ---- 启动 ----
if ($Jar) {
    if (-not (Test-Path $Jar)) { Write-Host "✗ jar 不存在：$Jar" -ForegroundColor Red; exit 1 }
    Write-Host "▶ java -jar $Jar --spring.profiles.active=$Profile" -ForegroundColor Cyan
    & java "-Dspring.profiles.active=$Profile" -jar $Jar
    exit $LASTEXITCODE
}

$serverRoot = Split-Path -Parent $scriptDir
if (-not $SkipBuild) {
    Write-Host "▶ mvn package -DskipTests（首次或改动后需要）" -ForegroundColor Cyan
    Push-Location $serverRoot
    & mvn -o -pl jeecg-module-system/jeecg-system-start -am package -DskipTests
    $buildExit = $LASTEXITCODE
    Pop-Location
    if ($buildExit -ne 0) { Write-Host "✗ 构建失败（exit=$buildExit）" -ForegroundColor Red; exit $buildExit }
}

$targetJar = Join-Path $serverRoot 'jeecg-module-system\jeecg-system-start\target\jeecg-system-start-3.4.3.jar'
if (-not (Test-Path $targetJar)) {
    Write-Host "✗ 未找到 jar：$targetJar" -ForegroundColor Red
    Write-Host "  可能是版本号变了，请检查 target 目录下的实际文件名。"
    exit 1
}
Write-Host "▶ java -jar $targetJar --spring.profiles.active=$Profile" -ForegroundColor Cyan
& java "-Dspring.profiles.active=$Profile" -jar $targetJar
exit $LASTEXITCODE
