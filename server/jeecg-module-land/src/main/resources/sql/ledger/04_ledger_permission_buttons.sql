-- =============================================================================
-- 道路设施验收及移交资料台账 · 接口权限（按钮权限 menu_type=2）与角色授权
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
-- ★ 权限码规范：land:{模块}:{操作}（沿用档案 land:archive:* / 提级论证 land:escalation:*）
--   本模块用 land:ledger:*，共 7 个：
--     land:ledger:list      台账分页 / 详情 / 各状态计数 / 13 类资料定义 / ★按项目查关联档案
--     land:ledger:add       新增 / 生成台账编号 / 台账编号唯一校验
--     land:ledger:edit      编辑 / 变更状态
--     land:ledger:delete    删除 / 批量删除
--     land:ledger:archive   ★关联档案 / 取消关联（资料原件挂 t_archive，本表只留指针）
--     land:ledger:export    台账导出 Excel
--     land:ledger:import    ★Excel 批量补录（模板下载 / 预览校验 / 入库）
--
-- ★ 为什么不复用 land:archive:* ：
--   本模块是独立的台账业务（有自己的表、自己的菜单），复用档案权限码会让
--   「只想给某人台账查看权、不给档案删除权」这种最小授权做不到；
--   而共享的只读基础数据（宗地/配套/行政区划下拉）走 LandDataController 的 Logical.OR 放行。
--
-- ★ 固定主键前缀：按钮 = '1ed9e0a11ed9e0a11ed9e0a11ed9e101' ~ '...106'
--   档案模块 ...d5e6ba、收发文 ...d5e6bb、提级论证 ...d5e6bc 已占用，本模块换前缀互不误删。
--
-- 脚本可重复执行：先按按钮 id 前缀清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission` WHERE `permission_id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e1%';
DELETE FROM `sys_permission`      WHERE `id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e1%';

-- ---------------------------------------------------------------------------
-- 2) 兜底：清空本模块菜单行上的权限码（03 脚本已写 NULL，这里防手工改过库）
-- ---------------------------------------------------------------------------
UPDATE `sys_permission` SET `perms` = NULL, `perms_type` = '0'
 WHERE `id` = '1ed9e0a11ed9e0a11ed9e0a11ed9e001';

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
  ('1ed9e0a11ed9e0a11ed9e0a11ed9e101', '1ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '台账查看', NULL, NULL, NULL, NULL, 2,
   'land:ledger:list', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '台账分页列表 / 详情 / 各状态计数 / 按项目查关联档案', 'admin', NOW(), 0, 0, '1', 0),

  ('1ed9e0a11ed9e0a11ed9e0a11ed9e102', '1ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '台账新增', NULL, NULL, NULL, NULL, 2,
   'land:ledger:add', '1', 2.00, 0, NULL, 1, 1, 0,
   0, 0, '新增台账记录 / 生成台账编号 YS-{yyyy}-{4位} / 台账编号唯一校验', 'admin', NOW(), 0, 0, '1', 0),

  ('1ed9e0a11ed9e0a11ed9e0a11ed9e103', '1ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '台账编辑', NULL, NULL, NULL, NULL, 2,
   'land:ledger:edit', '1', 3.00, 0, NULL, 1, 1, 0,
   0, 0, '编辑台账 / 勾选与取消 13 类资料 / 变更状态（未验收/验收中/已验收/已移交）', 'admin', NOW(), 0, 0, '1', 0),

  ('1ed9e0a11ed9e0a11ed9e0a11ed9e104', '1ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '台账删除', NULL, NULL, NULL, NULL, 2,
   'land:ledger:delete', '1', 4.00, 0, NULL, 1, 1, 0,
   0, 0, '删除 / 批量删除台账记录（逻辑删除，不影响档案与配套项目）', 'admin', NOW(), 0, 0, '1', 0),

  ('1ed9e0a11ed9e0a11ed9e0a11ed9e105', '1ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '台账关联档案', NULL, NULL, NULL, NULL, 2,
   'land:ledger:archive', '1', 5.00, 0, NULL, 1, 1, 0,
   0, 0, '关联档案 / 取消关联（资料原件统一挂 t_archive / t_archive_file，台账只留 archive_id 指针）', 'admin', NOW(), 0, 0, '1', 0),

  ('1ed9e0a11ed9e0a11ed9e0a11ed9e106', '1ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '台账导出', NULL, NULL, NULL, NULL, 2,
   'land:ledger:export', '1', 6.00, 0, NULL, 1, 1, 0,
   0, 0, '按当前查询条件导出台账 Excel（列 = 台账列 + 13 类资料列 + 状态）', 'admin', NOW(), 0, 0, '1', 0),

  ('1ed9e0a11ed9e0a11ed9e0a11ed9e107', '1ed9e0a11ed9e0a11ed9e0a11ed9e001',
   '台账批量补录', NULL, NULL, NULL, NULL, 2,
   'land:ledger:import', '1', 7.00, 0, NULL, 1, 1, 0,
   0, 0, '按台账编号批量补录 13 类资料与验收/移交字段（Excel 模板下载 / 预览校验 / 错误回执 / 入库）', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) 对齐按钮行的字段形态
--    库内 jeecg 自带按钮权限的 keep_alive / hide_tab / internal_or_external 都是 NULL；
--    SysPermission 里这三个字段是基本类型 boolean，NULL 会被映射成 false，行为等价。
--    ★ 只处理本模块的按钮，避免影响档案/收发文/提级论证模块。
-- ---------------------------------------------------------------------------
UPDATE `sys_permission`
   SET `keep_alive` = NULL, `hide_tab` = NULL, `internal_or_external` = NULL
 WHERE `id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e1%';

-- ---------------------------------------------------------------------------
-- 5) 授权给「管理员」角色（role_code = admin）
--    sys_role_permission.id = 'rp' + 权限ID后 30 位（确定性主键，天然幂等；
--    不用 UUID()：MySQL 5.7 同语句内高频调用会撞主键）。
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
WHERE p.`id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e1%'
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
  AND p.`id` = '1ed9e0a11ed9e0a11ed9e0a11ed9e001'
GROUP BY p.`id`, p.`name`, p.`is_leaf`;

--    6.2 应输出 7 行，且全部「已授权 admin」
SELECT p.`perms` AS 权限码, p.`name` AS 按钮名, m.`name` AS 所属菜单,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_permission` m ON m.`id` = p.`parent_id`
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e1%'
  AND p.`del_flag` = 0
ORDER BY p.`sort_no`;

--    6.3 应输出 0 行：本模块 7 个权限码之外，本前缀下不该有别的权限行
SELECT p.`perms` AS 意外权限码, p.`name`
FROM `sys_permission` p
WHERE p.`id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e1%'
  AND p.`perms` NOT IN ('land:ledger:list', 'land:ledger:add', 'land:ledger:edit',
                        'land:ledger:delete', 'land:ledger:archive', 'land:ledger:export',
                        'land:ledger:import');
