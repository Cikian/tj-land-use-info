-- =============================================================================
-- 竣工验收项目历史工程资料数字化档案 · 接口权限（按钮权限 menu_type=2）与角色授权
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- ★ 为什么必须建成「按钮权限」而不是写在菜单上（档案模块踩过的坑，见 7B.1）
--   jeecg 的权限分两条互相独立的链路：
--     ① 后端 @RequiresPermissions → ShiroRealm → SysPermissionMapper.queryByUser()：
--        把该用户角色下**所有** perms 非空的行都收进权限集合（不看 menu_type）；
--     ② 前端 v-has / 菜单管理的按钮授权 → SysPermissionController.getAuthJsonArray()：
--        **只收 menu_type=2（按钮）且 status=1 的行**。
--   权限码写在 menu_type=1 的菜单行上时，链路 ① 能拦住接口，链路 ② 拿不到任何条目 ——
--   结果「菜单管理里看不到可授权的接口权限」「前端 v-has 一律判定无权限」。
--
-- ★ 权限码规范：land:{模块}:{操作}（沿用 land:archive:* / land:escalation:* / land:ledger:*）
--   本模块用 land:completion:*，共 5 个：
--     land:completion:list    列表 / 详情 / 统计 / 各状态计数 / ★按项目查关联扫描件
--     land:completion:add     新增 / 生成档案编号 / 档案编号唯一校验
--     land:completion:edit    编辑 / ★变更数字化状态 / ★挑档案 / ★关联档案 / ★取消关联
--     land:completion:delete  删除 / 批量删除
--     land:completion:export  按当前条件导出档案信息 Excel
--
-- ★ 为什么关联档案归在 edit、而不新造 land:completion:archive：
--   需求方给定的权限码清单就是这 5 个（list/add/edit/delete/export）。
--   关联扫描件只是「编辑这条档案记录的一个字段（archive_id）」，语义上属于 edit；
--   再拆一个码就超出了既定清单，也会让角色授权多一个需要勾选的项。
--
-- ★ 固定主键前缀：按钮 = '3ed9e0a11ed9e0a11ed9e0a11ed9e101' ~ '...105'
--   档案模块 …d5e6ba/bb、提级论证 …bc、第 7 项台账 1ed9e0a1… 都已占用，本模块独立前缀。
--
-- 脚本可重复执行：先按按钮 id 前缀清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission` WHERE `permission_id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e1%';
DELETE FROM `sys_permission`      WHERE `id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e1%';

-- ---------------------------------------------------------------------------
-- 2) 兜底：清空本模块菜单行上的权限码（03 脚本已写 NULL，这里防手工改过库）
-- ---------------------------------------------------------------------------
UPDATE `sys_permission` SET `perms` = NULL, `perms_type` = '0'
 WHERE `id` = '3ed9e0a11ed9e0a11ed9e0a11ed9e001';

-- ---------------------------------------------------------------------------
-- 3) 按钮权限（menu_type=2）
--    字段形态与 jeecg 代码生成器产物 / 库内既有按钮行保持一致：
--    url/component/component_name/redirect/icon 为 NULL，is_route=1，is_leaf=1，
--    hidden=0，perms_type='1'，status='1'，del_flag=0
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e101', '3ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '历史档案查看', NULL, NULL, NULL, NULL, 2,
   'land:completion:list', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '历史档案分页列表 / 详情 / 统计 / 各数字化状态计数 / 按项目查关联扫描件', 'admin', NOW(), 0, 0, '1', 0),

  ('3ed9e0a11ed9e0a11ed9e0a11ed9e102', '3ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '历史档案新增', NULL, NULL, NULL, NULL, 2,
   'land:completion:add', '1', 2.00, 0, NULL, 1, 1, 0,
   0, 0, '新增历史项目档案 / 生成档案编号 JG-{yyyy}-{4位} / 档案编号唯一校验', 'admin', NOW(), 0, 0, '1', 0),

  ('3ed9e0a11ed9e0a11ed9e0a11ed9e103', '3ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '历史档案编辑', NULL, NULL, NULL, NULL, 2,
   'land:completion:edit', '1', 3.00, 0, NULL, 1, 1, 0,
   0, 0, '编辑档案信息 / ★变更数字化状态（未数字化/数字化中/已数字化）/ ★挑档案 / 关联与取消关联扫描件', 'admin', NOW(), 0, 0, '1', 0),

  ('3ed9e0a11ed9e0a11ed9e0a11ed9e104', '3ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '历史档案删除', NULL, NULL, NULL, NULL, 2,
   'land:completion:delete', '1', 4.00, 0, NULL, 1, 1, 0,
   0, 0, '删除 / 批量删除历史档案记录（逻辑删除，不影响档案与配套项目）', 'admin', NOW(), 0, 0, '1', 0),

  ('3ed9e0a11ed9e0a11ed9e0a11ed9e105', '3ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '历史档案导出', NULL, NULL, NULL, NULL, 2,
   'land:completion:export', '1', 5.00, 0, NULL, 1, 1, 0,
   0, 0, '按当前查询条件导出档案信息 Excel（列 = 全部查询条件列 + 全部结果列，共 30 列）', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) 对齐按钮行的字段形态
--    库内 jeecg 自带按钮权限的 keep_alive / hide_tab / internal_or_external 都是 NULL；
--    SysPermission 里这三个字段是基本类型 boolean，NULL 会被映射成 false，行为等价。
--    ★ 只处理本模块的按钮，避免影响其它模块。
-- ---------------------------------------------------------------------------
UPDATE `sys_permission`
   SET `keep_alive` = NULL, `hide_tab` = NULL, `internal_or_external` = NULL
 WHERE `id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e1%';

-- ---------------------------------------------------------------------------
-- 5) 授权给「管理员」角色（role_code = admin）
--    sys_role_permission.id = 'rp' + 权限ID后 30 位（确定性主键，天然幂等；
--    不用 UUID()：MySQL 5.7 同语句内高频调用会撞主键）。
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
WHERE p.`id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e1%'
  AND p.`del_flag` = 0
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 6) 守卫查询
--    6.1 应输出 0 行：有按钮权限子节点、但 is_leaf 仍是 1 的菜单
-- ---------------------------------------------------------------------------
SELECT p.`name` AS 有问题菜单, p.`is_leaf`, COUNT(c.`id`) AS 按钮数
FROM `sys_permission` p
JOIN `sys_permission` c ON c.`parent_id` = p.`id` AND c.`menu_type` = 2 AND c.`del_flag` = 0
WHERE p.`del_flag` = 0 AND p.`is_leaf` = 1
  AND p.`id` = '3ed9e0a11ed9e0a11ed9e0a11ed9e001'
GROUP BY p.`id`, p.`name`, p.`is_leaf`;

--    6.2 应输出 5 行，且全部「已授权 admin」
SELECT p.`perms` AS 权限码, p.`name` AS 按钮名, m.`name` AS 所属菜单,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_permission` m ON m.`id` = p.`parent_id`
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e1%'
  AND p.`del_flag` = 0
ORDER BY p.`sort_no`;

--    6.3 应输出 0 行：本模块 5 个权限码之外，本前缀下不该有别的权限行
SELECT p.`perms` AS 意外权限码, p.`name`
FROM `sys_permission` p
WHERE p.`id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e1%'
  AND p.`perms` NOT IN ('land:completion:list', 'land:completion:add', 'land:completion:edit',
                        'land:completion:delete', 'land:completion:export');
