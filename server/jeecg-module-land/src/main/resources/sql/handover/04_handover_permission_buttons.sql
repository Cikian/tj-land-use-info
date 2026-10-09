-- =============================================================================
-- 道路交付及养护协议移交事项 · 接口权限（按钮权限 menu_type=2）与角色授权
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- ★ 为什么必须建成「按钮权限」而不是写在菜单上（档案模块踩过的坑，见 7B.1）
--   后端 @RequiresPermissions 走的是「所有 perms 非空的行」（不看 menu_type），
--   而前端 v-has 与菜单管理的按钮授权只认 menu_type=2 且 status=1 的行。
--   权限码写在 menu_type=1 的菜单行上时，后端能拦住，前端却什么也拿不到 ——
--   菜单管理里看不到、v-has 一律判无权限。因此本模块 6 个权限码全部下沉为按钮权限。
--
-- ★ 权限码规范：land:{模块}:{操作}（沿用 land:archive:* / land:ledger:*）
--   本模块用 land:handover:*，共 6 个：
--     land:handover:list      列表 / 详情 / 统计 / 按项目查关联档案 / 字典下拉
--     land:handover:add       新增 / 生成移交编号 / 编号唯一校验
--     land:handover:edit      编辑 / 变更状态
--     land:handover:delete    删除 / 批量删除
--     land:handover:archive   关联档案 / 取消关联 / 挑档案（协议扫描件与移交单挂 t_archive）
--     land:handover:export    导出 Excel
--
-- ★ 固定主键前缀：按钮 = '2ed9e0a11ed9e0a11ed9e0a11ed9e101' ~ '…106'
--   （台账用 1ed9e0a1…，竣工验收历史档案用 3ed9e0a1…，互不误删）
--
-- 脚本可重复执行：先按按钮 id 前缀清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission` WHERE `permission_id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e1%';
DELETE FROM `sys_permission`      WHERE `id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e1%';

-- ---------------------------------------------------------------------------
-- 2) 兜底：清空本模块菜单行上的权限码
-- ---------------------------------------------------------------------------
UPDATE `sys_permission` SET `perms` = NULL, `perms_type` = '0'
 WHERE `id` = '2ed9e0a11ed9e0a11ed9e0a11ed9e001';

-- ---------------------------------------------------------------------------
-- 3) 按钮权限
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('2ed9e0a11ed9e0a11ed9e0a11ed9e101', '2ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '移交事项查看', NULL, NULL, NULL, NULL, 2, 'land:handover:list', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '移交事项列表 / 详情 / 统计 / 关联档案查询', 'admin', NOW(), 0, 0, '1', 0),
  ('2ed9e0a11ed9e0a11ed9e0a11ed9e102', '2ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '移交事项新增', NULL, NULL, NULL, NULL, 2, 'land:handover:add', '1', 2.00, 0, NULL, 1, 1, 0,
   0, 0, '新增移交事项 / 生成移交编号 YJ-{yyyy}-{4位} / 编号唯一校验', 'admin', NOW(), 0, 0, '1', 0),
  ('2ed9e0a11ed9e0a11ed9e0a11ed9e103', '2ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '移交事项编辑', NULL, NULL, NULL, NULL, 2, 'land:handover:edit', '1', 3.00, 0, NULL, 1, 1, 0,
   0, 0, '编辑移交事项 / 变更状态（待移交/移交中/已移交）', 'admin', NOW(), 0, 0, '1', 0),
  ('2ed9e0a11ed9e0a11ed9e0a11ed9e104', '2ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '移交事项删除', NULL, NULL, NULL, NULL, 2, 'land:handover:delete', '1', 4.00, 0, NULL, 1, 1, 0,
   0, 0, '删除 / 批量删除移交事项（逻辑删除，不影响档案与配套项目）', 'admin', NOW(), 0, 0, '1', 0),
  ('2ed9e0a11ed9e0a11ed9e0a11ed9e105', '2ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '移交事项关联档案', NULL, NULL, NULL, NULL, 2, 'land:handover:archive', '1', 5.00, 0, NULL, 1, 1, 0,
   0, 0, '关联档案 / 取消关联（协议扫描件、移交单统一挂 t_archive，本表只留指针）', 'admin', NOW(), 0, 0, '1', 0),
  ('2ed9e0a11ed9e0a11ed9e0a11ed9e106', '2ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '移交事项导出', NULL, NULL, NULL, NULL, 2, 'land:handover:export', '1', 6.00, 0, NULL, 1, 1, 0,
   0, 0, '按当前查询条件导出移交事项 Excel', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) 对齐按钮行的字段形态（与库内既有按钮一致）
-- ---------------------------------------------------------------------------
UPDATE `sys_permission`
   SET `keep_alive` = NULL, `hide_tab` = NULL, `internal_or_external` = NULL
 WHERE `id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e1%';

-- ---------------------------------------------------------------------------
-- 5) 授权给「管理员」角色
-- ---------------------------------------------------------------------------
-- ★★ 授权行主键的算法（这个坑连踩两次，务必照抄）
--   sys_role_permission.id 是 varchar(32) 且唯一，而权限 id 本身就是 32 位，
--   所以任何「前缀 + 权限 id 的片段」写法都可能把不同权限压成同一个主键：
--     ❌ CONCAT('rp', SUBSTRING(p.id, 3))  —— 丢掉前 2 位；本项目的模块 id 恰恰只在
--        第 1 位区分（台账 1ed9… / 移交 2ed9… / 竣工档案 3ed9…），于是三个模块撞成一行，
--        后执行的模块被当成重复行，界面上表现为「权限码建好了却显示未授权」。
--     ❌ CONCAT('r', LEFT(p.id, 31))      —— 丢掉最后 1 位；而按钮 id 恰恰只在末位区分
--        （…101 / …102 / …107），于是 6~7 个按钮只剩 1 个授权成功。
--     ✅ MD5(CONCAT('role-perm:', p.id)) —— 32 位、对任意不同权限 id 都不同、
--        与「哪一位不同」无关，且完全确定性（脚本可反复执行，不会越滚越多）。
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT MD5(CONCAT('role-perm:', p.`id`)), 'f6817f48af4fb3af11b9e8bf182f618b', p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e1%'
  AND p.`del_flag` = 0
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 6) 守卫查询
--    6.1 应输出 0 行：有按钮子节点、但 is_leaf 仍是 1 的菜单
-- ---------------------------------------------------------------------------
SELECT p.`name` AS 有问题菜单, p.`is_leaf`, COUNT(c.`id`) AS 按钮数
FROM `sys_permission` p
JOIN `sys_permission` c ON c.`parent_id` = p.`id` AND c.`menu_type` = 2 AND c.`del_flag` = 0
WHERE p.`del_flag` = 0 AND p.`is_leaf` = 1 AND p.`id` = '2ed9e0a11ed9e0a11ed9e0a11ed9e001'
GROUP BY p.`id`, p.`name`, p.`is_leaf`;

--    6.2 应输出 6 行，且全部「已授权 admin」
SELECT p.`perms` AS 权限码, p.`name` AS 按钮名, m.`name` AS 所属菜单,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_permission` m ON m.`id` = p.`parent_id`
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e1%' AND p.`del_flag` = 0
ORDER BY p.`sort_no`;

--    6.3 应输出 0 行：本模块 6 个权限码之外，本前缀下不该有别的权限行
SELECT p.`perms` AS 意外权限码, p.`name`
FROM `sys_permission` p
WHERE p.`id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e1%'
  AND p.`perms` NOT IN ('land:handover:list', 'land:handover:add', 'land:handover:edit',
                        'land:handover:delete', 'land:handover:archive', 'land:handover:export');
