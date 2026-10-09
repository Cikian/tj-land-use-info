-- =============================================================================
-- 数据管理 · 经营性用地批量导入 · 菜单脚本（方案 2.3.1（三））
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- ★ 本脚本新建「数据管理」一级菜单 —— 方案 2.3.1（三）共 6 项功能
--   （经营性用地信息录入、配套地块数据录入、经营性用地批量导入管理、
--    配套信息批量导入管理、配套附件管理、数据更新与移除），
--   全都挂在它下面。本次只落地第 3 项「经营性用地批量导入管理」，
--   其余 5 项由并行开发的同事按 sort_no 2.00~6.00 依次补入，
--   因此本脚本占用 sort_no 1.00，并预留 2.00~6.00 不占用。
--
-- ★ 为什么新建一级菜单而不是挂在既有菜单下：
--   方案原文把这一项列在「数据管理」（三）里，与地图管理、配套分析管理并列。
--   挂到「档案管理」下会让方案验收时对不上分组结构。
--
-- ★ 菜单行不配权限码（perms 必须为 NULL）：接口权限统一登记为 sys_permission 里
--   menu_type=2 的「按钮权限」，见同目录 04_land_data_permission_buttons.sql。
--   原因见 docs/档案管理-实现说明.md 7B 节：jeecg 的前端 v-has 与菜单授权只认 menu_type=2。
--
-- ★ is_leaf 规则：菜单下面挂了按钮权限时必须为 0，否则 jeecg 建树不会递归到按钮，
--   菜单管理与角色授权里都看不到它们（见档案模块坑 7B.0）。
--
-- ★★ 固定主键（务必逐字照抄，不要凭感觉数十六进制位数）
--   本模块共 3 个权限行，id 固定为（每个 28 位 = 24 位模块前缀 + 4 位行号）：
--     模块前缀                      = 7a1f0d2c4e5b4d8a9c3f6b1e        （24 位）
--     一级菜单「数据管理」           = 7a1f0d2c4e5b4d8a9c3f6b1ea001  （28 位）
--     二级菜单「经营性用地批量导入」   = 7a1f0d2c4e5b4d8a9c3f6b1ea002  （28 位）
--     按钮权限「经营性用地批量导入」   = 7a1f0d2c4e5b4d8a9c3f6b1eb001  （28 位）
--   清理用的统配前缀 = '7a1f0d2c4e5b4d8a9c3f6b1e%'（24 位，只命中本模块）
--
--   ★ 坑：sys_permission.id 是 varchar(32)，本模块 id 用 28 位、留足余量。写超 32 位会直接报
--     ERROR 1406 Data too long for column 'id'（本项目实测踩过：34 位 id），
--     而且脚本前半段（DELETE）已经跑掉、后半段（INSERT）全失败，看起来就像「菜单建了但授权没建」。
--     本脚本末尾 5.4 特意加了「id 长度 <> 32 → 报出来」这条守卫查询（防手改 / 防复制粘贴丢字符）。
--
-- 脚本可重复执行：先按固定前缀清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理旧记录（幂等）
--    注意：只清本模块前缀的行，不动 sys_permission 里其它模块。
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%';
DELETE FROM `sys_permission`
 WHERE `id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%';

-- ---------------------------------------------------------------------------
-- 2) 一级菜单：数据管理（无权限码，仅作为分组容器）
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('7a1f0d2c4e5b4d8a9c3f6b1ea001', NULL,
   '数据管理', '/land/data', 'layouts/RouteView', NULL, NULL, 0,
   NULL, '0', 4.00, 0, 'database', 1, 0, 0,
   0, 0, '数据管理（方案 2.3.1（三））：经营性用地录入/批量导入、配套数据录入/批量导入、配套附件管理、数据更新与移除', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 3) 二级菜单：经营性用地批量导入（sort_no 1.00，其余 5 项留给并行开发）
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('7a1f0d2c4e5b4d8a9c3f6b1ea002', '7a1f0d2c4e5b4d8a9c3f6b1ea001',
   '经营性用地批量导入', '/land/data/import', 'land/data/LandImportList', 'LandImportList', NULL, 1,
   NULL, '0', 1.00, 0, 'upload', 1, 0, 1,
   0, 0, 'Excel 批量导入经营性用地（出让宗地）：模板下载 + 预览校验 + 错误回执下载 + 入库（方案 2.3.1（三）第 3 项）', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) 授权给「管理员」角色（role_code = admin，id = f6817f48af4fb3af11b9e8bf182f618b）
--
-- ★★ 授权行主键的算法（这个坑在本项目已连踩两次，务必照抄本写法）
--   sys_role_permission.id 是 varchar(32) 且唯一，而权限 id 本身就有 28 位，
--   所以任何「前缀 + 权限 id 的片段」都可能把不同权限压成同一个主键：
--     ❌ CONCAT('rp', SUBSTRING(p.id, 3)) —— 丢掉前 2 位；本项目的模块 id 往往只在
--        开头几位区分，于是不同模块撞成一行，后执行的模块被当成重复行，
--        界面上表现为「权限码建好了却显示未授权」。
--     ❌ CONCAT('r', LEFT(p.id, 31))      —— 丢掉最后 1 位；而按钮 id 恰恰只在末位区分，
--        于是多个按钮只剩 1 个授权成功。
--     ✅ MD5(CONCAT('role-perm:', p.id)) —— 32 位、对任意不同权限 id 都不同、
--        完全确定性（脚本可反复执行，不会越滚越多）。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT MD5(CONCAT('role-perm:', p.`id`)), 'f6817f48af4fb3af11b9e8bf182f618b', p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%'
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 5) 守卫查询
--    5.1 应输出 2 行（一级 + 二级），全部「已授权 admin」，id 长度全部 28（不超过 32）
-- ---------------------------------------------------------------------------
SELECT p.`id`, LENGTH(p.`id`) AS id长度, p.`name` AS 菜单, p.`url`, p.`component`,
       p.`menu_type` AS 类型, p.`sort_no` AS 排序, p.`is_leaf` AS 是否叶子,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%' AND p.`del_flag` = 0
ORDER BY p.`menu_type`, p.`sort_no`;

--    5.2 应输出 0 行：有按钮子节点、但 is_leaf 仍是 1 的菜单
SELECT p.`name` AS 有问题菜单, p.`is_leaf`, COUNT(c.`id`) AS 按钮数
FROM `sys_permission` p
JOIN `sys_permission` c ON c.`parent_id` = p.`id` AND c.`menu_type` = 2 AND c.`del_flag` = 0
WHERE p.`del_flag` = 0 AND p.`is_leaf` = 1
  AND p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%'
GROUP BY p.`id`, p.`name`, p.`is_leaf`;

--    5.3 应输出 0 行：本模块菜单行上被误写了权限码
SELECT p.`name` AS 菜单, p.`menu_type`, p.`perms`
FROM `sys_permission` p
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%' AND p.`menu_type` = 1 AND p.`perms` IS NOT NULL;

--    5.4 应输出 0 行：本模块 id 长度超过 32 位的行（防手改 / 防复制粘贴多字符）
SELECT p.`id`, LENGTH(p.`id`) AS id长度, p.`name` AS 菜单
FROM `sys_permission` p
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%' AND LENGTH(p.`id`) <> 32;
