-- =============================================================================
-- 档案管理模块 · 菜单与权限（方案 2.3.2）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 建立两级菜单：
--   档案管理（一级，分组）
--     └─ 档案类别管理（二级，路由组件 land/archive/ArchiveCategoryList）
--
-- ★ 菜单行不配权限码（perms 留空）：
--   接口权限统一登记为 sys_permission 里 menu_type=2 的「按钮权限」，
--   见同目录 06_archive_permission_buttons.sql（含给 admin 角色授权）。
--   原因见 docs/档案管理-实现说明.md 7B 节：jeecg 的前端 v-has 与菜单管理的按钮授权
--   只认 menu_type=2 的行，权限码写在菜单行上会「看不见、授不了」。
--
-- 脚本可重复执行：先按固定主键清除旧记录，再插入。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

-- 1) 清理旧记录（固定主键，保证幂等）
DELETE FROM `sys_role_permission`
 WHERE `permission_id` IN ('7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30', '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41');
DELETE FROM `sys_permission`
 WHERE `id` IN ('7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30', '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41');

-- 2) 菜单
--    ★ is_leaf 必须为 0：本菜单下面要挂 menu_type=2 的按钮权限，
--      而 jeecg 建树时是 if (!permission.isLeaf()) { 递归子节点 }，
--      is_leaf=1 会导致它的按钮在「菜单管理」和「角色授权」里都看不到。
--      详见 06_archive_permission_buttons.sql 第 4 段。
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30', NULL,
   '档案管理', '/land/archive', 'layouts/RouteView', NULL, NULL, 0,
   NULL, '0', 5.00, 0, 'folder-open', 1, 0, 0,
   0, 0, '档案管理（方案 2.3.2）', 'admin', NOW(), 0, 0, '1', 0),
  ('8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41', '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30',
   '档案类别管理', '/land/archive/category', 'land/archive/ArchiveCategoryList', 'ArchiveCategoryList', NULL, 1,
   NULL, '0', 1.00, 0, 'apartment', 1, 0, 1,
   0, 0, '档案类别树维护（方案 2.3.2 第 1 项）', 'admin', NOW(), 0, 0, '1', 0);

-- 3) 授权给「管理员」角色（role_code = admin）
--    如需授权给其它角色，把下面的 role_id 换成对应 sys_role.id 即可。
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
VALUES
  ('9a4c1e6b3d8f4c7a0b2e7d9f1c3a4b52', 'f6817f48af4fb3af11b9e8bf182f618b',
   '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30', NULL, NOW(), '127.0.0.1'),
  ('ab5d2f7c4e9a4d8b1c3f8e0a2d4b5c63', 'f6817f48af4fb3af11b9e8bf182f618b',
   '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41', NULL, NOW(), '127.0.0.1');
