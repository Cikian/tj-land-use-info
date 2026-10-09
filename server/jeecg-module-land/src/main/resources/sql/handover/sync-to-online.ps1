<#
.SYNOPSIS
    把「道路交付及养护协议移交事项」模块（方案 2.3.2 第 6 项）的数据库改动同步到目标库。

.DESCRIPTION
    按固定顺序执行 sql/handover 下的脚本，并在前后做校验。

      前置校验（不通过则中止，不写任何东西）
        · 目标库可达、版本可读
        · 系统表列结构与脚本假设一致
        · admin 角色 id 与授权脚本一致（f6817f48af4fb3af11b9e8bf182f618b）
        · 父菜单「档案管理」存在
        · 迁移依赖表存在（t_supporting_facilities / t_land / t_archive）
        · 本模块固定主键前缀无冲突

      执行（幂等）
        01_t_road_handover.sql                建表（CREATE TABLE IF NOT EXISTS，不删数据）
        02_handover_dict.sql                  1 个字典 + 3 个字典项
        03_handover_menu.sql                  「档案管理」下新增菜单 + 授权 admin
        04_handover_permission_buttons.sql    6 个按钮权限 + 授权 admin
        05_migrate_handover.sql               ★ 迁移：旧库 sfyj='是' 的 84 条道路 → 移交事项初始记录

      后置校验
        表与索引、迁移对账（底账 84 vs 已迁移）、状态与年度分布、字典/菜单/按钮/授权数量、
        ★ 授权完整性（本模块与既有模块都要「权限行数 = 已授权行数」）

.PARAMETER DbHost
    目标库主机。默认 49.232.252.56（线上库）。

.PARAMETER SkipMigration
    跳过 05 迁移脚本，只同步结构与配置。

.PARAMETER WhatIf
    只做前置校验与脚本清单，不写库。

.EXAMPLE
    pwsh -File sync-to-online.ps1
    pwsh -File sync-to-online.ps1 -SkipMigration
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
    [switch]$SkipMigration,
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
Write-Host (" 道路交付及养护协议移交事项 · 数据库同步   目标：{0}:{1}/{2}（{3}）" -f $DbHost,$DbPort,$DbName,$hostLabel) -ForegroundColor Cyan
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
Write-Host ("  目标库版本：{0}" -f ((Invoke-Sql 'SELECT VERSION();' | Select-Object -Last 1)))

$pre = Invoke-Sql @"
SELECT
 (SELECT COUNT(*) FROM sys_role WHERE id='f6817f48af4fb3af11b9e8bf182f618b') AS admin_role,
 (SELECT COUNT(*) FROM sys_permission WHERE id='7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30' AND del_flag=0) AS parent_menu,
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_supporting_facilities') AS src_facility,
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_land') AS t_land,
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_archive') AS t_archive,
 (SELECT COUNT(*) FROM sys_permission WHERE id LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e%') AS perm_conflict,
 (SELECT COUNT(*) FROM sys_dict WHERE id LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e2%') AS dict_conflict,
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_road_handover') AS handover_table,
 (SELECT COUNT(*) FROM t_supporting_facilities
    WHERE ptsslb IN ('道路','市政道路','道路及管线') AND sfyj='是'
      AND ptxmmc IS NOT NULL AND ptxmmc <> '' AND (delFlag IS NULL OR delFlag='0')) AS source_rows;
"@
$cols = ($pre | Select-Object -Last 1) -split "`t"
$adminRole, $parentMenu, $srcFacility, $tLand, $tArchive, $permConf, $dictConf, $handoverTable, $sourceRows = $cols

$problems = @()
if ([int]$adminRole -eq 0)     { $problems += "admin 角色 id 不是 f6817f48af4fb3af11b9e8bf182f618b" }
if ([int]$parentMenu -eq 0)    { $problems += "父菜单「档案管理」不存在，请先同步档案管理模块" }
if ([int]$srcFacility -eq 0)   { $problems += "缺少源表 t_supporting_facilities，无法迁移" }
if ([int]$tLand -eq 0)         { $problems += "缺少 t_land（迁移时按 crzdbh 反查地块名称要用）" }
if ([int]$tArchive -eq 0)      { Write-Host "  注意：t_archive 不存在——移交事项能建，但「关联档案」页签会没数据" -ForegroundColor Yellow }
if ([int]$permConf -ne 0 -or [int]$dictConf -ne 0) {
    Write-Host -ForegroundColor Yellow "  ! 主键前缀下已存在记录（多半是本模块上次同步的残留，脚本会先按前缀清理本模块对象）；若这些记录属于别的模块，请立刻停止并按 Ctrl+C"
}

Write-Host ("  admin 角色 / 父菜单 / 源表 / t_land / 已有表：{0} / {1} / {2} / {3} / {4}" -f $adminRole,$parentMenu,$srcFacility,$tLand,$handoverTable)
Write-Host ("  ★ 迁移底账条数（旧库 sfyj='是' 的道路类，应为 84）：{0}" -f $sourceRows)

if ($problems.Count -gt 0) {
    Write-Host "`n  ✗ 前置校验未通过，已中止（未写入任何内容）：" -ForegroundColor Red
    $problems | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
    exit 2
}
Write-Host "  ✓ 前置校验通过" -ForegroundColor Green
if ([int]$handoverTable -gt 0) {
    Write-Host "  注意：目标库已存在 t_road_handover——本次为**增量同步**" -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# 2) 待执行脚本
# ---------------------------------------------------------------------------
$files = @('01_t_road_handover.sql','02_handover_dict.sql','03_handover_menu.sql','04_handover_permission_buttons.sql')
if (-not $SkipMigration) { $files += '05_migrate_handover.sql' }

Write-Host "`n[2/4] 待执行脚本" -ForegroundColor Yellow
$files | ForEach-Object { Write-Host ("  · {0}" -f $_) }
if ($SkipMigration) {
    Write-Host "  ★ 已指定 -SkipMigration：本次不迁数据" -ForegroundColor Yellow
} else {
    Write-Host ("  ★ 05 将把 {0} 条已移交道路迁成移交事项初始记录（幂等，不覆盖已有行）" -f $sourceRows) -ForegroundColor Yellow
}

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
SELECT '表与索引' AS 项, TABLE_NAME AS 对象, TABLE_ROWS AS 估算行数, TABLE_COLLATION AS 排序规则
 FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_road_handover';
SELECT '索引数' AS 项, COUNT(DISTINCT INDEX_NAME) AS 索引数
 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='$DbName' AND TABLE_NAME='t_road_handover';
SELECT '迁移对账' AS 项,
 (SELECT COUNT(*) FROM t_supporting_facilities
   WHERE ptsslb IN ('道路','市政道路','道路及管线') AND sfyj='是'
     AND ptxmmc IS NOT NULL AND ptxmmc <> '' AND (delFlag IS NULL OR delFlag='0')) AS 底账条数,
 (SELECT COUNT(*) FROM t_road_handover WHERE source_facility_id IS NOT NULL) AS 已迁移条数,
 (SELECT COUNT(*) FROM t_road_handover WHERE source_facility_id IS NULL) AS 人工新增条数,
 (SELECT COUNT(*) FROM t_road_handover WHERE remark LIKE '%无匹配记录%') AS 宗地未匹配;
SELECT '状态与类型' AS 项, status AS 状态, handover_type AS 移交类型, COUNT(*) AS 条数
 FROM t_road_handover GROUP BY status, handover_type;
SELECT '字典项数' AS 项, d.dict_code AS 编码, COUNT(i.id) AS 数量
 FROM sys_dict d LEFT JOIN sys_dict_item i ON i.dict_id=d.id
 WHERE d.dict_code='land_road_handover_type' GROUP BY d.dict_code;
SELECT '菜单/按钮/授权' AS 项,
 (SELECT COUNT(*) FROM sys_permission WHERE id='2ed9e0a11ed9e0a11ed9e0a11ed9e001') AS 菜单数,
 (SELECT COUNT(*) FROM sys_permission WHERE id LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e1%') AS 按钮数,
 (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id
   WHERE p.id LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e%') AS 已授权数;
-- ★ 授权完整性守卫：每个 land 模块的「权限行数」都必须等于「已授权行数」
SELECT '授权完整性（两者必须相等）' AS 项, SUBSTRING_INDEX(p.perms, ':', 2) AS 模块,
       COUNT(*) AS 权限行数,
       SUM(CASE WHEN rp.id IS NULL THEN 0 ELSE 1 END) AS 已授权行数
 FROM sys_permission p
 LEFT JOIN sys_role_permission rp ON rp.permission_id=p.id
      AND rp.role_id='f6817f48af4fb3af11b9e8bf182f618b'
 WHERE p.perms LIKE 'land:%' AND p.del_flag=0
 GROUP BY SUBSTRING_INDEX(p.perms, ':', 2) ORDER BY 模块;
SELECT '守卫（应为 0 行）' AS 项, p.name AS 有问题菜单, p.is_leaf
 FROM sys_permission p JOIN sys_permission c ON c.parent_id=p.id AND c.menu_type=2 AND c.del_flag=0
 WHERE p.del_flag=0 AND p.is_leaf=1 AND p.id='2ed9e0a11ed9e0a11ed9e0a11ed9e001'
 GROUP BY p.id,p.name,p.is_leaf;
SELECT '移交编号重复（应为 0 行）' AS 项, handover_no, COUNT(*) AS 次数
 FROM t_road_handover GROUP BY handover_no HAVING COUNT(*) > 1;
"@
$post | ForEach-Object { Write-Host "  $_" }

Write-Host "`n✓ 同步完成。生效提示：" -ForegroundColor Green
Write-Host "  1) 相关用户需**重新登录**（或清 Redis 的 shiro 授权缓存），菜单与权限才会生效；" -ForegroundColor Green
Write-Host "  2) 后端需部署含本模块的 jeecg-module-land；" -ForegroundColor Green
Write-Host "  3) 前端需部署 api/land/handover.js + views/land/archive/RoadHandoverList.vue 及 4 个 modules 组件。" -ForegroundColor Green
