-- =============================================================================
-- 收发文管理模块 · 菜单与权限（方案 2.3.2 第 9 项）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 菜单结构：
--   收发文管理（一级，/land/document）
--     ├─ 收文管理（land/document/DocReceiveList）  权限码 doc:receive:manage
--     └─ 发文管理（land/document/DocSendList）     权限码 doc:send:manage
--
-- 为什么与「档案管理」并列而不是放在它下面：
--   方案原文把收发文列为档案管理第 9 项，但两者职责不同——
--   档案是「案卷」，收发文是「公文流转台账」，且档案详情里要反向引用收发文。
--   一级菜单并列（排序 6）更符合使用习惯，也与原型一级导航口径一致。
--
-- ★ 菜单行不配权限码（perms 留空）：
--   接口权限统一登记为 menu_type=2 的「按钮权限」，
--   见同目录 04_document_permission_buttons.sql（含给 admin 角色授权）。
--   原因见 docs/档案管理-实现说明.md 7B 节。
--
-- 脚本可重复执行：先按固定主键清除旧记录，再插入。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

DELETE FROM `sys_role_permission`
 WHERE `permission_id` IN (
   'a1b2c3d4e5f6470891a2b3c4d5e6f710',
   'a1b2c3d4e5f6470891a2b3c4d5e6f711',
   'a1b2c3d4e5f6470891a2b3c4d5e6f712');
DELETE FROM `sys_permission`
 WHERE `id` IN (
   'a1b2c3d4e5f6470891a2b3c4d5e6f710',
   'a1b2c3d4e5f6470891a2b3c4d5e6f711',
   'a1b2c3d4e5f6470891a2b3c4d5e6f712');

-- ★ is_leaf 规则：菜单下面挂了按钮权限（menu_type=2）时必须为 0，
--   否则 jeecg 建树不会递归到按钮，菜单管理与角色授权里都看不到它们。
--   收文管理 / 发文管理下面都有按钮权限，因此 is_leaf = 0。
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('a1b2c3d4e5f6470891a2b3c4d5e6f710', NULL,
   '收发文管理', '/land/document', 'layouts/RouteView', NULL, NULL, 0,
   NULL, '0', 6.00, 0, 'mail-sent', 1, 0, 0,
   0, 0, '收文与发文管理（方案 2.3.2 第 9 项）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6f711', 'a1b2c3d4e5f6470891a2b3c4d5e6f710',
   '收文管理', '/land/document/receive', 'land/document/DocReceiveList', 'DocReceiveList', NULL, 1,
   NULL, '0', 1.00, 0, 'import', 1, 0, 1,
   0, 0, '收文登记与收文中心内流转（登记→承办→办结，支持转办与退回）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6f712', 'a1b2c3d4e5f6470891a2b3c4d5e6f710',
   '发文管理', '/land/document/send', 'land/document/DocSendList', 'DocSendList', NULL, 1,
   NULL, '0', 2.00, 0, 'export', 1, 0, 1,
   0, 0, '发文信息记录台账', 'admin', NOW(), 0, 0, '1', 0);

INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
VALUES
  ('b1c2d3e4f5a6470891a2b3c4d5e6f710', 'f6817f48af4fb3af11b9e8bf182f618b',
   'a1b2c3d4e5f6470891a2b3c4d5e6f710', NULL, NOW(), '127.0.0.1'),
  ('b1c2d3e4f5a6470891a2b3c4d5e6f711', 'f6817f48af4fb3af11b9e8bf182f618b',
   'a1b2c3d4e5f6470891a2b3c4d5e6f711', NULL, NOW(), '127.0.0.1'),
  ('b1c2d3e4f5a6470891a2b3c4d5e6f712', 'f6817f48af4fb3af11b9e8bf182f618b',
   'a1b2c3d4e5f6470891a2b3c4d5e6f712', NULL, NOW(), '127.0.0.1');
