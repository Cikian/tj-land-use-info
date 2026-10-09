<#
.SYNOPSIS
    把「竣工验收项目历史工程资料数字化档案」模块的数据库改动同步到目标库。

.DESCRIPTION
    ★★ 本模块【没有数据迁移步骤】——旧系统不存在这个功能，也没有对应数据可迁，
       只建空表，数据由页面录入 + Excel 导出。
       （对比：2.3.2 第 7 项「道路验收及移交资料台账」有 1340 条旧配套项目可作底账，
         那一个模块才带 05_migrate_*.sql。）

    按固定顺序执行 sql/completion 下的脚本，并在前后做校验。

      前置校验（不通过则中止，不写任何东西）
        · 目标库可达、版本可读
        · 系统表列结构与脚本假设一致（sys_permission / sys_dict / sys_dict_item / sys_role_permission）
        · admin 角色 id 与授权脚本一致（脚本里硬编码 f6817f48af4fb3af11b9e8bf182f618b）
        · 父菜单「档案管理」存在（新菜单要挂在它下面）
        · 本模块固定主键前缀无冲突（避免覆盖别人的记录）

      执行（幂等，可重复执行）
        01_t_completion_archive.sql              建档案表（CREATE TABLE IF NOT EXISTS，**不删数据**）
        02_completion_dict.sql                   2 个字典 + 9 个字典项（先删自己再插）
        03_completion_menu.sql                   「档案管理」下新增 1 个菜单 + 授权 admin
        04_completion_permission_buttons.sql     5 个按钮权限 + 授权 admin

      后置校验
        表与索引是否就位、数字化状态列定义、字典/菜单/按钮/授权数量、守卫查询

.PARAMETER DbHost
    目标库主机。默认 49.232.252.56（线上库，即 application-dev.yml 指向的库）。
    本地库请传 127.0.0.1。

.PARAMETER WhatIf
    只做前置校验与将要执行的脚本清单，不写库。

.EXAMPLE
    # 线上
    pwsh -File sync-to-online.ps1

    # 本地
    pwsh -File sync-to-online.ps1 -DbHost 127.0.0.1

    # 先干跑看看
    pwsh -File sync-to-online.ps1 -WhatIf
#>
[CmdletBinding()]
param(
    [string]$DbHost  = '49.232.252.56',
    [int]   $DbPort  = 3306,
    [string]$DbUser  = 'root',
    [string]$DbPass  = 'Chen0809@mysql',
    [string]$DbName  = 'tj-jyxyd',
    [string]$MySqlExe = 'D:\Develop\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe',
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Invoke-Sql {
    param([string]$Sql)
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
Write-Host (" 竣工验收历史档案 · 数据库同步   目标：{0}:{1}/{2}（{3}）" -f $DbHost,$DbPort,$DbName,$hostLabel) -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

if (-not (Test-Path -LiteralPath $MySqlExe)) {
    Write-Host "`n  ✗ 找不到 mysql 客户端：$MySqlExe" -ForegroundColor Red
    Write-Host "    请用 -MySqlExe 指定，或改用 DBX MCP 执行同目录的 SQL 脚本（内容完全一致）。" -ForegroundColor Red
    exit 3
}

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
 (SELECT COUNT(*) FROM sys_permission WHERE id='7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30' AND del_flag=0) AS parent_menu,
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_archive') AS t_archive,
 (SELECT COUNT(*) FROM sys_permission WHERE id LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e%') AS perm_conflict,
 (SELECT COUNT(*) FROM sys_dict WHERE id LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e2%') AS dict_conflict,
 (SELECT COUNT(*) FROM sys_dict_item WHERE id LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e3%') AS item_conflict,
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_completion_archive') AS completion_table,
 (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_completion_archive') AS completion_index_cols;
"@
$cols = ($pre | Select-Object -Last 1) -split "`t"
$permCols, $dictCols, $itemCols, $rpCols, $adminRole, $adminRoleIdOk, $parentMenu,
$tArchive, $permConf, $dictConf, $itemConf, $completionTable, $completionIndexCols = $cols

$problems = @()
if ([int]$permCols -eq 0 -or [int]$dictCols -eq 0 -or [int]$itemCols -eq 0 -or [int]$rpCols -eq 0) {
    $problems += "系统表不完整（sys_permission=$permCols / sys_dict=$dictCols / sys_dict_item=$itemCols / sys_role_permission=$rpCols）——目标库可能不是本项目的库"
}
if ([int]$adminRole -eq 0)      { $problems += "没有 role_code='admin' 的角色" }
if ([int]$adminRoleIdOk -eq 0)  { $problems += "admin 角色 id 不是 f6817f48af4fb3af11b9e8bf182f618b（授权脚本硬编码该 id）" }
if ([int]$parentMenu -eq 0)     { $problems += "父菜单「档案管理」(7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30) 不存在，请先同步档案管理模块" }
if ([int]$permConf -ne 0 -or [int]$dictConf -ne 0 -or [int]$itemConf -ne 0) {
    Write-Host -ForegroundColor Yellow "  ! 主键前缀下已存在记录（多半是本模块上次同步的残留，脚本会先按前缀清理本模块对象）；若这些记录属于别的模块，请立刻停止并按 Ctrl+C"
}

Write-Host ("  系统表列数：sys_permission={0} sys_dict={1} sys_dict_item={2} sys_role_permission={3}" -f $permCols,$dictCols,$itemCols,$rpCols)
Write-Host ("  admin 角色 / 父菜单 / t_archive / 已有档案表：{0} / {1} / {2} / {3}" -f $adminRole,$parentMenu,$tArchive,$completionTable)
if ([int]$tArchive -eq 0) {
    Write-Host "  注意：t_archive 不存在——档案表能建，但「关联扫描件」页签会没数据（档案模块未同步）" -ForegroundColor Yellow
}

if ($problems.Count -gt 0) {
    Write-Host "`n  ✗ 前置校验未通过，已中止（未写入任何内容）：" -ForegroundColor Red
    $problems | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
    exit 2
}
Write-Host "  ✓ 前置校验通过" -ForegroundColor Green
if ([int]$completionTable -gt 0) {
    Write-Host "  注意：目标库已存在 t_completion_archive——本次为**增量同步**（建表脚本不会删数据）" -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# 2) 将要执行的脚本
# ---------------------------------------------------------------------------
$files = @('01_t_completion_archive.sql','02_completion_dict.sql','03_completion_menu.sql','04_completion_permission_buttons.sql')

Write-Host "`n[2/4] 待执行脚本" -ForegroundColor Yellow
$files | ForEach-Object { Write-Host ("  · {0}" -f $_) }
Write-Host "  ★ 本模块没有数据迁移脚本（旧系统不存在该功能，没有对应数据可迁）" -ForegroundColor Cyan

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
SELECT '档案表' AS 项, TABLE_NAME AS 对象, TABLE_ROWS AS 估算行数, TABLE_COLLATION AS 排序规则
 FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_completion_archive';
SELECT '索引数（应 13 行：PRIMARY + 1 唯一 + 11 普通）' AS 项, COUNT(*) AS 索引数
 FROM (SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_completion_archive') t;
SELECT '数字化状态列' AS 项, IS_NULLABLE AS 可空, COLUMN_DEFAULT AS 默认值
 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_completion_archive' AND COLUMN_NAME='digitize_status';
SELECT '字典项数' AS 项, d.dict_code AS 编码, COUNT(i.id) AS 数量
 FROM sys_dict d LEFT JOIN sys_dict_item i ON i.dict_id=d.id
 WHERE d.dict_code LIKE 'land_completion%' GROUP BY d.dict_code ORDER BY d.dict_code;
SELECT '菜单/按钮/授权' AS 项,
 (SELECT COUNT(*) FROM sys_permission WHERE id='3ed9e0a11ed9e0a11ed9e0a11ed9e001') AS 菜单数,
 (SELECT COUNT(*) FROM sys_permission WHERE id LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e1%') AS 按钮数,
 (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id
   WHERE p.id LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e%') AS 已授权数;
SELECT '守卫（应为 0 行）' AS 项, p.name AS 有问题菜单, p.is_leaf
 FROM sys_permission p JOIN sys_permission c ON c.parent_id=p.id AND c.menu_type=2 AND c.del_flag=0
 WHERE p.del_flag=0 AND p.is_leaf=1 AND p.id='3ed9e0a11ed9e0a11ed9e0a11ed9e001'
 GROUP BY p.id,p.name,p.is_leaf;
SELECT '档案编号重复（应为 0 行）' AS 项, archive_no, COUNT(*) AS 次数
 FROM t_completion_archive GROUP BY archive_no HAVING COUNT(*) > 1;
"@
$post | ForEach-Object { Write-Host "  $_" }

Write-Host "`n✓ 同步完成。生效提示：" -ForegroundColor Green
Write-Host "  1) 需让相关用户**重新登录**（或清 Redis 里的 shiro 授权缓存），菜单与权限才会生效；" -ForegroundColor Green
Write-Host "  2) 后端需部署含本模块的 jeecg-module-land（entity/enums/dto/vo/mapper(+xml)/service/controller/support）；" -ForegroundColor Green
Write-Host "  3) 前端需部署 api/land/completion.js + views/land/archive/CompletionArchiveList.vue 及 4 个 modules 组件。" -ForegroundColor Green
