-- =============================================================================
-- 数据管理 · 经营性用地批量导入 · 接口权限（按钮权限 menu_type=2）与角色授权
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- ★ 为什么必须建成「按钮权限」而不是写在菜单上
--   jeecg 的权限分两条链路：
--     ① 后端 @RequiresPermissions → ShiroRealm → SysBaseApiImpl.getUserPermissionSet()
--        → SysPermissionMapper.queryByUser()：把该用户角色下**所有** perms 非空的行都收进来
--        （不区分 menu_type），所以权限码写在菜单行上后端也能拦住；
--     ② 前端 v-has 指令 / 菜单管理的按钮授权 → SysPermissionController.getUserPermissionJsonArray()
--        → getAuthJsonArray()：**只收 menu_type=2（按钮）且 status=1 的行**。
--   把权限码写在 menu_type=1 的菜单行上，链路 ② 拿不到任何条目，结果就是
--   「菜单管理里看不到任何可授权的接口权限」「前端 v-has 一律判定无权限」。
--   因此本脚本把权限码下沉为**按钮权限**（03 脚本里的菜单行 perms 均为 NULL）。
--
-- ★ 权限码规范：land:data:import（与 land:archive:*、land:escalation:* 一致）
--   本模块只用一个权限码，挂在「经营性用地批量导入」菜单下：
--     land:data:import   模板下载 / 字段字典 / 策略选项 / 预览校验 / 确认入库 /
--                        错误回执下载 / 导入记录
--   ★ 为什么不再细分 add/edit：批量导入是一次「整批写库」的动作，
--     能打开这个页面的人就能导入；把它拆成「只看不导」两个码，
--     在中心只有 2~3 个人用这个功能的情况下只会增加授权负担，
--     而真正的安全边界是**菜单能不能打开**（没这个码就进不来页面）。
--     后续的「数据更新与移除」会另建 land:data:edit / land:data:delete，
--     那时「批量导入」与「逐条改删」是两个不同的权限粒度，才值得分开。
--
-- ★★ 固定主键（务必逐字照抄，不要凭感觉数十六进制位数）
--   按钮权限行 id = 7a1f0d2c4e5b4d8a9c3f6b1eb001（28 位）
--   清理用的统配前缀 = '7a1f0d2c4e5b4d8a9c3f6b1eb%'（25 位，只命中按钮行）
--   ★ 坑：sys_permission.id 是 varchar(32)，超过就会报 ERROR 1406 Data too long（本项目实测踩过：34 位 id）。
--
-- 脚本可重复执行：先按按钮 id 前缀清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1eb%';
DELETE FROM `sys_permission`
 WHERE `id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1eb%';

-- ---------------------------------------------------------------------------
-- 2) 兜底：清空本模块菜单行上的权限码
--    （03 脚本插入菜单时 perms 已经是 NULL，这里再确保一次，防止手工改过库）
-- ---------------------------------------------------------------------------
UPDATE `sys_permission` SET `perms` = NULL, `perms_type` = '0'
 WHERE `id` IN (
   '7a1f0d2c4e5b4d8a9c3f6b1ea001',   -- 数据管理（一级）
   '7a1f0d2c4e5b4d8a9c3f6b1ea002');  -- 经营性用地批量导入（二级）

-- ---------------------------------------------------------------------------
-- 3) 按钮权限（menu_type=2）
--    字段形态与 jeecg 代码生成器产物 / 库内既有按钮行保持一致：
--    url/component/component_name/redirect/icon 为 NULL，is_route=1，is_leaf=1，
--    keep_alive=0，hidden=0，hide_tab=0，perms_type='1'，status='1'，del_flag=0
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('7a1f0d2c4e5b4d8a9c3f6b1eb001', '7a1f0d2c4e5b4d8a9c3f6b1ea002',
   '经营性用地批量导入', NULL, NULL, NULL, NULL, 2,
   'land:data:import', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '经营性用地批量导入：模板下载 / 字段字典 / 重复策略 / 预览校验 / 确认入库 / 错误回执下载 / 导入记录（方案 2.3.1（三）第 3 项）', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) 授权给「管理员」角色
--    ✅ MD5(CONCAT('role-perm:', p.id)) —— 确定性、32 位、不会撞主键
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT MD5(CONCAT('role-perm:', p.`id`)), 'f6817f48af4fb3af11b9e8bf182f618b', p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1eb%'
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 5) 守卫查询
--    5.1 应输出 1 行，id 长度 28（不超过 32），授权状态为「已授权 admin」
-- ---------------------------------------------------------------------------
SELECT p.`id`, LENGTH(p.`id`) AS id长度, p.`name` AS 按钮, p.`perms` AS 权限码,
       p.`menu_type` AS 类型,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1eb%' AND p.`del_flag` = 0;

--    5.2 应输出 0 行：本模块里 menu_type=2 但 perms 为空的行（空权限码的按钮拦不住任何东西）
SELECT p.`name` AS 空权限码按钮, p.`menu_type`
FROM `sys_permission` p
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%' AND p.`menu_type` = 2
  AND (p.`perms` IS NULL OR p.`perms` = '');

--    5.3 应输出 1 行且「一致」：确认代码里 @RequiresPermissions 用的权限码与库里一致
SELECT p.`perms` AS 库里权限码, 'land:data:import' AS 代码权限码,
       CASE WHEN p.`perms` = 'land:data:import' THEN '一致' ELSE '★不一致，要改代码或改库' END AS 比对结果
FROM `sys_permission` p
WHERE p.`id` = '7a1f0d2c4e5b4d8a9c3f6b1eb001';

--    5.4 应输出 0 行：按钮的父菜单不存在（会变成孤儿按钮，角色授权里看不到归属）
SELECT p.`id`, p.`name` AS 孤儿按钮
FROM `sys_permission` p
LEFT JOIN `sys_permission` parent ON parent.`id` = p.`parent_id`
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1eb%' AND parent.`id` IS NULL;
