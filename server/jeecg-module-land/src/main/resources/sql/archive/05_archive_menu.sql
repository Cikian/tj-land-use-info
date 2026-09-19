-- =============================================================================
-- 档案管理模块 · 档案维护 / 档案查询 / 档案统计 菜单与权限
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 菜单结构（档案管理一级菜单由 02_archive_category_menu.sql 建立）：
--   档案管理（一级，/land/archive）
--     ├─ 档案维护（land/archive/ArchiveList）        权限码 archive:manage
--     ├─ 档案查询（land/archive/ArchiveQuery）       权限码 archive:manage
--     ├─ 档案统计（land/archive/ArchiveStatistics）  权限码 archive:manage
--     └─ 档案类别管理（land/archive/ArchiveCategoryList）权限码 archive:category:manage
--
-- 说明：菜单行不配权限码（perms 留空）。
--      档案维护 / 档案查询 / 档案统计 的接口权限统一登记为 menu_type=2 的「按钮权限」，
--      见同目录 06_archive_permission_buttons.sql（含给 admin 角色授权）。
--      原因见 docs/档案管理-实现说明.md 7B 节。
--
-- 脚本可重复执行：先按固定主键清除旧记录，再插入。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

-- 1) 清理旧记录（固定主键，保证幂等）
DELETE FROM `sys_role_permission`
 WHERE `permission_id` IN (
   'a1b2c3d4e5f6470891a2b3c4d5e6f701',
   'a1b2c3d4e5f6470891a2b3c4d5e6f702',
   'a1b2c3d4e5f6470891a2b3c4d5e6f703');
DELETE FROM `sys_permission`
 WHERE `id` IN (
   'a1b2c3d4e5f6470891a2b3c4d5e6f701',
   'a1b2c3d4e5f6470891a2b3c4d5e6f702',
   'a1b2c3d4e5f6470891a2b3c4d5e6f703');

-- 2) 把已有的「档案类别管理」排到最后一位，保持「维护 → 查询 → 统计 → 类别」的阅读顺序
UPDATE `sys_permission` SET `sort_no` = 4.00
 WHERE `id` = '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41';

-- 3) 菜单
--    ★ is_leaf 规则：菜单下面挂了按钮权限（menu_type=2）时必须为 0，
--      否则 jeecg 建树不会递归到按钮，菜单管理与角色授权里都看不到它们。
--      本脚本里：档案维护(7 个按钮)=0、档案统计(1 个按钮)=0、档案查询(无按钮)=1。
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('a1b2c3d4e5f6470891a2b3c4d5e6f701', '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30',
   '档案维护', '/land/archive/maintain', 'land/archive/ArchiveList', 'ArchiveList', NULL, 1,
   NULL, '0', 1.00, 0, 'file-add', 1, 0, 1,
   0, 0, '档案录入、编辑、删除与详情查看（方案 2.3.2 第 2 项）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6f702', '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30',
   '档案查询', '/land/archive/query', 'land/archive/ArchiveQuery', 'ArchiveQuery', NULL, 1,
   NULL, '0', 2.00, 0, 'search', 1, 1, 1,
   0, 0, '按项目名称/时间/档案类别等多条件检索（方案 2.3.2 第 3 项）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6f703', '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30',
   '档案统计', '/land/archive/statistics', 'land/archive/ArchiveStatistics', 'ArchiveStatistics', NULL, 1,
   NULL, '0', 3.00, 0, 'pie-chart', 1, 0, 1,
   0, 0, '按项目/类别/年度的档案统计（方案 2.3.2 第 5 项）', 'admin', NOW(), 0, 0, '1', 0);

-- 4) 授权给「管理员」角色（role_code = admin）
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
VALUES
  ('b1c2d3e4f5a6470891a2b3c4d5e6f701', 'f6817f48af4fb3af11b9e8bf182f618b',
   'a1b2c3d4e5f6470891a2b3c4d5e6f701', NULL, NOW(), '127.0.0.1'),
  ('b1c2d3e4f5a6470891a2b3c4d5e6f702', 'f6817f48af4fb3af11b9e8bf182f618b',
   'a1b2c3d4e5f6470891a2b3c4d5e6f702', NULL, NOW(), '127.0.0.1'),
  ('b1c2d3e4f5a6470891a2b3c4d5e6f703', 'f6817f48af4fb3af11b9e8bf182f618b',
   'a1b2c3d4e5f6470891a2b3c4d5e6f703', NULL, NOW(), '127.0.0.1');
