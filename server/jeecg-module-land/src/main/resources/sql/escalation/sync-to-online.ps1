<#
.SYNOPSIS
    把「提级论证管理」模块的数据库改动同步到目标库（本地 / 线上）。

.DESCRIPTION
    按固定顺序执行 sql/escalation 下的脚本，并在前后做校验：

      前置校验（不通过则中止，不写任何东西）
        · 目标库可达、版本可读
        · 系统表列结构与脚本假设一致（sys_permission / sys_dict / sys_dict_item / sys_role_permission）
        · admin 角色 id 与授权脚本一致（脚本里硬编码 f6817f48af4fb3af11b9e8bf182f618b）
        · yn 字典存在（「是否土地整理项目」复用）
        · 本模块固定主键前缀无冲突（避免覆盖别的记录）

      执行（幂等，可重复执行）
        01_t_escalation.sql                 建 3 张表 —— ★ 已改为 CREATE TABLE IF NOT EXISTS，**不会删除已有数据**
        03_escalation_dict.sql              3 个字典 + 字典项（先删自己再插）
        02_escalation_menu.sql              一级 + 4 个子菜单（先删自己再插，授权 admin）
        04_escalation_permission_buttons.sql 6 个按钮权限 + 授权 admin

      可选（仅演示/测试环境）
        05_t_escalation_demo_data.sql       10 条演示项目 / 22 材料 / 18 意见 —— 假数据，勿入生产
        06_escalation_demo_link_real.sql    把 3 条演示项目挂到真实配套项目/宗地

      后置校验
        表是否存在、字典/菜单/按钮数量、admin 授权数

.PARAMETER DbHost
    目标库主机。默认 127.0.0.1（本机）。

.PARAMETER IncludeDemoData
    ★ 同时同步演示数据（05 + 06）。**生产库不要加这个开关**：
    不仅会写入 10 条假项目，其材料指向的文件也不在目标环境的
    jeecg.path.upload 目录下（下载会失败）。演示数据请只用于本地/演示环境。

.PARAMETER WhatIf
    只做前置校验与将要执行的脚本清单，不写库。

.EXAMPLE
    # 本地
    pwsh -File sync-to-online.ps1

    # 线上（结构与配置，不含假数据）
    pwsh -File sync-to-online.ps1 -DbHost 49.232.252.56 -DbPass 'xxx'

    # 先干跑看看
    pwsh -File sync-to-online.ps1 -DbHost 49.232.252.56 -DbPass 'xxx' -WhatIf
#>
[CmdletBinding()]
param(
    [string]$DbHost  = '127.0.0.1',
    [int]   $DbPort  = 3306,
    [string]$DbUser  = 'root',
    [string]$DbPass  = 'Chen0809@mysql',
    [string]$DbName  = 'tj-jyxyd',
    [string]$MySqlExe = 'D:\Develop\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe',
    [switch]$IncludeDemoData,
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Invoke-Sql {
    param([string]$Sql, [switch]$Raw)
    $a = @('-h',$DbHost,'-P',"$DbPort","-u$DbUser","-p$DbPass",'--connect-timeout=15',
           '--default-character-set=utf8mb4','--batch','-D',$DbName,'-e',$Sql)
    $out = & $MySqlExe @a 2>&1 | Where-Object { $_ -notmatch 'Using a password' }
    if ($LASTEXITCODE -ne 0) { throw "SQL 执行失败（退出码 $LASTEXITCODE）：`n$($out -join "`n")" }
    return $out
}

function Invoke-SqlFile {
    param([string]$Path)
    $name = Split-Path -Leaf $Path
    if (-not (Test-Path -LiteralPath $Path)) { throw "脚本不存在：$Path" }
    Write-Host ("  → {0}" -f $name) -ForegroundColor Gray
    # 用 cmd 重定向把文件喂给 mysql（PowerShell 不支持 <）
    $line = '"{0}" -h{1} -P{2} -u{3} -p{4} --default-character-set=utf8mb4 -D {5} < "{6}" 2>&1' -f `
            $MySqlExe, $DbHost, $DbPort, $DbUser, $DbPass, $DbName, $Path
    $out = cmd /c $line
    $code = $LASTEXITCODE
    $clean = $out | Where-Object { $_ -notmatch 'Using a password' }
    if ($code -ne 0) {
        $clean | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
        throw "$name 执行失败（退出码 $code）"
    }
    return $clean
}

$hostLabel = if ($DbHost -eq '127.0.0.1' -or $DbHost -eq 'localhost') { '本机' } else { $DbHost }

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host (" 提级论证管理 · 数据库同步   目标：{0}:{1}/{2}（{3}）" -f $DbHost,$DbPort,$DbName,$hostLabel) -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# 1) 前置校验
# ---------------------------------------------------------------------------
Write-Host "`n[1/4] 前置校验" -ForegroundColor Yellow
$ver = (Invoke-Sql 'SELECT VERSION();' | Select-Object -Last 1)
Write-Host ("  目标库版本：{0}" -f $ver)

$pre = Invoke-Sql @"
SELECT
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='sys_permission') AS perm_cols,
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='sys_dict') AS dict_cols,
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='sys_dict_item') AS item_cols,
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='sys_role_permission') AS rp_cols,
 (SELECT COUNT(*) FROM sys_role WHERE role_code='admin') AS admin_role,
 (SELECT COUNT(*) FROM sys_role WHERE id='f6817f48af4fb3af11b9e8bf182f618b') AS admin_role_id_ok,
 (SELECT COUNT(*) FROM sys_dict WHERE dict_code='yn') AS yn_dict,
 (SELECT COUNT(*) FROM sys_permission WHERE id LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%') AS menu_conflict,
 (SELECT COUNT(*) FROM sys_permission WHERE id LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%') AS btn_conflict,
 (SELECT COUNT(*) FROM sys_dict WHERE id LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4%') AS dict_conflict,
 (SELECT COUNT(*) FROM sys_dict_item WHERE id LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4%') AS item_conflict,
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME LIKE 't\_escalation%') AS existing_tables;
"@
$cols = ($pre | Select-Object -Last 1) -split "`t"
$permCols, $dictCols, $itemCols, $rpCols, $adminRole, $adminRoleIdOk, $ynDict, $menuConf, $btnConf, $dictConf, $itemConf, $existTables = $cols

$problems = @()
if ([int]$permCols -eq 0 -or [int]$dictCols -eq 0 -or [int]$itemCols -eq 0 -or [int]$rpCols -eq 0) {
    $problems += "系统表不完整（sys_permission=$permCols / sys_dict=$dictCols / sys_dict_item=$itemCols / sys_role_permission=$rpCols）——目标库可能不是本项目的库"
}
if ([int]$adminRole -eq 0)      { $problems += "没有 role_code='admin' 的角色" }
if ([int]$adminRoleIdOk -eq 0)  { $problems += "admin 角色 id 不是 f6817f48af4fb3af11b9e8bf182f618b（授权脚本硬编码该 id）" }
if ([int]$ynDict -eq 0)         { $problems += "缺少 yn 字典（「是否土地整理项目」要用）" }
if ([int]$menuConf -ne 0 -or [int]$btnConf -ne 0 -or [int]$dictConf -ne 0 -or [int]$itemConf -ne 0) {
    $problems += "固定主键前缀冲突（菜单=$menuConf 按钮=$btnConf 字典=$dictConf 字典项=$itemConf）——可能有别人的记录占用同 id"
}

Write-Host ("  系统表列数：sys_permission={0} sys_dict={1} sys_dict_item={2} sys_role_permission={3}" -f $permCols,$dictCols,$itemCols,$rpCols)
Write-Host ("  admin 角色 / yn 字典 / 已存在本模块表：{0} / {1} / {2}" -f $adminRole,$ynDict,$existTables)

if ($problems.Count -gt 0) {
    Write-Host "`n  ✗ 前置校验未通过，已中止（未写入任何内容）：" -ForegroundColor Red
    $problems | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
    exit 2
}
Write-Host "  ✓ 前置校验通过" -ForegroundColor Green
if ([int]$existTables -gt 0) {
    Write-Host ("  注意：目标库已存在 {0} 张本模块表——本次为**增量同步**（建表脚本不会删数据）" -f $existTables) -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# 2) 将要执行的脚本
# ---------------------------------------------------------------------------
$files = @('01_t_escalation.sql','03_escalation_dict.sql','02_escalation_menu.sql','04_escalation_permission_buttons.sql')
if ($IncludeDemoData) { $files += @('05_t_escalation_demo_data.sql','06_escalation_demo_link_real.sql') }

Write-Host "`n[2/4] 待执行脚本" -ForegroundColor Yellow
$files | ForEach-Object { Write-Host ("  · {0}" -f $_) }
if ($IncludeDemoData) { Write-Host "  ★ 含演示数据（假数据）——请确认目标不是生产库！" -ForegroundColor Yellow }

if ($WhatIf) {
    Write-Host "`n-WhatIf：仅校验，不执行。结束。" -ForegroundColor Cyan
    exit 0
}

# ---------------------------------------------------------------------------
# 3) 执行
# ---------------------------------------------------------------------------
Write-Host "`n[3/4] 执行" -ForegroundColor Yellow
foreach ($f in $files) {
    $res = Invoke-SqlFile -Path (Join-Path $scriptDir $f)
    $res | Select-Object -Last 6 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
}

# ---------------------------------------------------------------------------
# 4) 后置校验
# ---------------------------------------------------------------------------
Write-Host "`n[4/4] 后置校验" -ForegroundColor Yellow
$post = Invoke-Sql @"
SELECT TABLE_NAME AS 表, TABLE_ROWS AS 估算行数 FROM information_schema.TABLES
 WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME LIKE 't\_escalation%' ORDER BY TABLE_NAME;
SELECT '字典项数' AS 项, d.dict_code AS 编码, COUNT(i.id) AS 数量
 FROM sys_dict d LEFT JOIN sys_dict_item i ON i.dict_id=d.id
 WHERE d.dict_code LIKE 'land_escalation%' GROUP BY d.dict_code ORDER BY d.dict_code;
SELECT '菜单/按钮/授权' AS 项,
 (SELECT COUNT(*) FROM sys_permission WHERE id LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%') AS 菜单数,
 (SELECT COUNT(*) FROM sys_permission WHERE id LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%') AS 按钮数,
 (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id
   WHERE p.id LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%' OR p.id LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%') AS 已授权数;
SELECT '守卫（应为 0 行）' AS 项, p.name AS 有问题菜单, p.is_leaf
 FROM sys_permission p JOIN sys_permission c ON c.parent_id=p.id AND c.menu_type=2 AND c.del_flag=0
 WHERE p.del_flag=0 AND p.is_leaf=1 AND p.id LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%'
 GROUP BY p.id,p.name,p.is_leaf;
"@
$post | ForEach-Object { Write-Host "  $_" }

Write-Host "`n✓ 同步完成。生效提示：执行后需让相关用户**重新登录**（或清 Redis 里的 shiro 授权缓存）菜单与权限才会生效。" -ForegroundColor Green
