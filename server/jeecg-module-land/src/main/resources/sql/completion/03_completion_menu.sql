-- =============================================================================
-- 竣工验收项目历史工程资料数字化档案 · 菜单脚本（方案 2.3.2 第 8 项）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 挂载点：现有的「档案管理」一级菜单下（不新建一级菜单），
--   档案管理（id = 7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30，url = /land/archive）
--     ├─ 档案维护            land/archive/ArchiveList
--     ├─ 档案查询            land/archive/ArchiveQuery
--     ├─ 档案统计            land/archive/ArchiveStatistics
--     ├─ 档案类别管理        land/archive/ArchiveCategoryList
--     ├─ 道路验收移交台账    land/archive/RoadAcceptanceLedger     （2.3.2 第 7 项，sort_no 5.00）
--     ├─ （6.00 预留给「道路交付及养护协议移交」模块，本脚本不占用）
--     └─ 竣工验收历史档案    land/archive/CompletionArchiveList     ← 本次新增（2.3.2 第 8 项，sort_no 7.00）
--
-- ★ 为什么挂在「档案管理」下而不是新建一级菜单：
--   方案 2.3.2 把这一项列在「档案管理」（九项）里，与档案维护/查询/统计同级；
--   新建一级菜单会让方案验收时对不上「档案管理九项」的分组结构。
--
-- ★ sort_no 取 7.00：5.00 已被「道路验收移交台账」占用，6.00 留给并行开发的
--   「道路交付及养护协议移交」模块，本菜单排在它之后。
--
-- ★ 菜单行不配权限码（perms 必须为 NULL）：接口权限统一登记为 sys_permission 里
--   menu_type=2 的「按钮权限」，见同目录 04_completion_permission_buttons.sql。
--   原因见 docs/档案管理-实现说明.md 7B 节：jeecg 的前端 v-has 与菜单授权只认 menu_type=2。
--
-- ★ is_leaf 规则：菜单下面挂了按钮权限时必须为 0，否则 jeecg 建树不会递归到按钮，
--   菜单管理与角色授权里都看不到它们（见档案模块坑 7B.0）。
--   本菜单挂 5 个按钮 → is_leaf = 0。
--
-- ★ 固定主键前缀：'3ed9e0a11ed9e0a11ed9e0a11ed9e%'
--   菜单 = ...e001，按钮 = ...e101~105，字典 = ...e2xx，字典项 = ...e3xx。
--
-- 脚本可重复执行：先按固定主键清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理旧记录（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission` WHERE `permission_id` = '3ed9e0a11ed9e0a11ed9e0a11ed9e001';
DELETE FROM `sys_permission`      WHERE `id` = '3ed9e0a11ed9e0a11ed9e0a11ed9e001';

-- ---------------------------------------------------------------------------
-- 2) 前置校验：父菜单（档案管理）必须存在
--    应输出 1 行；若为 0 行，说明目标库不是本项目库或菜单被改过，后续插入会挂空父级
-- ---------------------------------------------------------------------------
SELECT `id`, `name`, `url`, `component`, `is_leaf` AS 是否叶子, `sort_no` AS 排序
FROM `sys_permission`
WHERE `id` = '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30' AND `del_flag` = 0;

-- 2.2 排在前面的同级子菜单（用于确认 7.00 确实排在「道路验收移交台账」之后）
SELECT p.`id`, p.`name` AS 菜单, p.`sort_no` AS 排序
FROM `sys_permission` p
WHERE p.`parent_id` = '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30' AND p.`del_flag` = 0
ORDER BY p.`sort_no`;

-- ---------------------------------------------------------------------------
-- 3) 菜单
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e001', '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30',
   '竣工验收历史档案', '/land/archive/completion', 'land/archive/CompletionArchiveList', 'CompletionArchiveList', NULL, 1,
   NULL, '0', 7.00, 0, 'folder-open', 1, 0, 1,
   0, 0, '竣工验收项目历史工程资料数字化档案：历史项目 + 数字化状态追踪（页数/DPI）+ 快速检索与导出（方案 2.3.2 第 8 项）', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) 授权给「管理员」角色（role_code = admin，id = f6817f48af4fb3af11b9e8bf182f618b）
--    sys_role_permission.id 是 varchar(32) 且唯一，用「rp + 权限ID后 30 位」拼确定性主键
--    （原因见 04 脚本第 5 段：UUID() 在 MySQL 5.7 同语句高频调用会撞主键）。
-- ---------------------------------------------------------------------------
-- ★★ 授权行主键的算法（这个坑在本项目已连踩两次，务必照抄本写法）
--   sys_role_permission.id 是 varchar(32) 且唯一，而权限 id 本身就是 32 位，
--   所以任何「前缀 + 权限 id 的片段」都可能把不同权限压成同一个主键：
--     ❌ CONCAT('rp', SUBSTRING(p.id, 3)) —— 丢掉前 2 位；本项目的模块 id 恰恰只在
--        第 1 位区分（台账 1ed9… / 移交 2ed9… / 竣工档案 3ed9…），于是三个模块撞成一行，
--        后执行的模块被当成重复行，界面上表现为「权限码建好了却显示未授权」。
--     ❌ CONCAT('r', LEFT(p.id, 31))      —— 丢掉最后 1 位；而按钮 id 恰恰只在末位区分
--        （…101 / …102 / …107），于是 5~7 个按钮只剩 1 个授权成功。
--     ✅ MD5(CONCAT('role-perm:', p.id)) —— 32 位、对任意不同权限 id 都不同、
--        与「哪一位不同」无关，且完全确定性（脚本可反复执行，不会越滚越多）。
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT MD5(CONCAT('role-perm:', p.`id`)), 'f6817f48af4fb3af11b9e8bf182f618b', p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` = '3ed9e0a11ed9e0a11ed9e0a11ed9e001'
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 5) 守卫查询
--    5.1 应输出「档案管理」下的 6 个子菜单（按 sort_no），新菜单在最后一行
-- ---------------------------------------------------------------------------
SELECT p.`id`, p.`name` AS 菜单, p.`url`, p.`component`, p.`sort_no` AS 排序, p.`is_leaf` AS 是否叶子,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`parent_id` = '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30' AND p.`del_flag` = 0
ORDER BY p.`sort_no`;

--    5.2 应输出 0 行：有按钮子节点、但 is_leaf 仍是 1 的菜单
--        （一旦有输出，说明按钮在「菜单管理 / 角色授权」里看不到）
SELECT p.`name` AS 有问题菜单, p.`is_leaf`, COUNT(c.`id`) AS 按钮数
FROM `sys_permission` p
JOIN `sys_permission` c ON c.`parent_id` = p.`id` AND c.`menu_type` = 2 AND c.`del_flag` = 0
WHERE p.`del_flag` = 0 AND p.`is_leaf` = 1
  AND (p.`id` = '3ed9e0a11ed9e0a11ed9e0a11ed9e001'
       OR p.`id` = '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30')
GROUP BY p.`id`, p.`name`, p.`is_leaf`;

--    5.3 应输出 0 行：本模块菜单行上没有权限码
SELECT p.`name` AS 菜单, p.`menu_type`, p.`perms`
FROM `sys_permission` p
WHERE p.`id` = '3ed9e0a11ed9e0a11ed9e0a11ed9e001' AND p.`perms` IS NOT NULL;
