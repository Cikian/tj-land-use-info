-- =============================================================================
-- 收发文管理 · 接口权限（按钮权限 menu_type=2）与角色授权
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- ★ 与 06_archive_permission_buttons.sql 同一套做法：权限码必须下沉为
--   menu_type=2 的「按钮权限」，否则前端 v-has 与菜单管理的按钮授权都拿不到
--   （详见该脚本头部的两条链路说明）。
--
-- ★ 权限码规范：land:{模块}:{操作}
--   收文的流转被拆成三个独立动作（转办 / 退回 / 办结），各自一个权限码，
--   这样一个角色可以「只允许办结、不允许退回」，而不是共用一个 manage 码。
--
-- 脚本可重复执行：先按按钮 id 前缀清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) 清理（幂等）：按 perms 精确清理，与档案脚本（按 id 前缀 ...d5e6ba 清理）互不影响。
--    收发文按钮权限的 id 前缀为 a1b2c3d4e5f6470891a2b3c4d5e6bb
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` IN (
   SELECT `id` FROM `sys_permission`
    WHERE `perms` LIKE 'land:docReceive:%' OR `perms` LIKE 'land:docSend:%'
 );
DELETE FROM `sys_permission`
 WHERE `perms` LIKE 'land:docReceive:%' OR `perms` LIKE 'land:docSend:%';

-- 2) 清空菜单行上的权限码（与档案脚本一致：权限码只保留在按钮上）
UPDATE `sys_permission` SET `perms` = NULL, `perms_type` = '0'
 WHERE `id` IN (
   'a1b2c3d4e5f6470891a2b3c4d5e6f710',   -- 收发文管理（一级）
   'a1b2c3d4e5f6470891a2b3c4d5e6f711',   -- 收文管理
   'a1b2c3d4e5f6470891a2b3c4d5e6f712'    -- 发文管理
 );

-- ---------------------------------------------------------------------------
-- 3) 按钮权限
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  -- ===== 收文管理（parent = a1b2c3d4e5f6470891a2b3c4d5e6f711）=====
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb31', 'a1b2c3d4e5f6470891a2b3c4d5e6f711',
   '收文查询', NULL, NULL, NULL, NULL, 2,
   'land:docReceive:list', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '收文列表 / 详情 / 流转记录 / 统计 / 生成登记号', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb32', 'a1b2c3d4e5f6470891a2b3c4d5e6f711',
   '收文登记', NULL, NULL, NULL, NULL, 2,
   'land:docReceive:add', '1', 2.00, 0, NULL, 1, 1, 0,
   0, 0, '收文登记（写主表 + 附件 + 第一条流转记录）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb33', 'a1b2c3d4e5f6470891a2b3c4d5e6f711',
   '收文编辑', NULL, NULL, NULL, NULL, 2,
   'land:docReceive:edit', '1', 3.00, 0, NULL, 1, 1, 0,
   0, 0, '编辑收文信息与附件（不推进流转）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb34', 'a1b2c3d4e5f6470891a2b3c4d5e6f711',
   '收文删除', NULL, NULL, NULL, NULL, 2,
   'land:docReceive:delete', '1', 4.00, 0, NULL, 1, 1, 0,
   0, 0, '删除收文（级联附件与流转记录）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb35', 'a1b2c3d4e5f6470891a2b3c4d5e6f711',
   '收文转办', NULL, NULL, NULL, NULL, 2,
   'land:docReceive:transfer', '1', 5.00, 0, NULL, 1, 1, 0,
   0, 0, '转办 / 分办：把待办交给指定处理人', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb36', 'a1b2c3d4e5f6470891a2b3c4d5e6f711',
   '收文退回', NULL, NULL, NULL, NULL, 2,
   'land:docReceive:reject', '1', 6.00, 0, NULL, 1, 1, 0,
   0, 0, '退回：必须填写退回原因，退回到上一位处理人', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb37', 'a1b2c3d4e5f6470891a2b3c4d5e6f711',
   '收文办结', NULL, NULL, NULL, NULL, 2,
   'land:docReceive:finish', '1', 7.00, 0, NULL, 1, 1, 0,
   0, 0, '办结：结束办理并清空当前处理人', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb38', 'a1b2c3d4e5f6470891a2b3c4d5e6f711',
   '收文归档', NULL, NULL, NULL, NULL, 2,
   'land:docReceive:archive', '1', 8.00, 0, NULL, 1, 1, 0,
   0, 0, '归档到档案管理（必须选择档案类别）', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 发文管理（parent = a1b2c3d4e5f6470891a2b3c4d5e6f712）=====
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb41', 'a1b2c3d4e5f6470891a2b3c4d5e6f712',
   '发文查询', NULL, NULL, NULL, NULL, 2,
   'land:docSend:list', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '发文列表 / 详情 / 统计 / 生成登记号', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb42', 'a1b2c3d4e5f6470891a2b3c4d5e6f712',
   '发文登记', NULL, NULL, NULL, NULL, 2,
   'land:docSend:add', '1', 2.00, 0, NULL, 1, 1, 0,
   0, 0, '发文登记（含附件）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb43', 'a1b2c3d4e5f6470891a2b3c4d5e6f712',
   '发文编辑', NULL, NULL, NULL, NULL, 2,
   'land:docSend:edit', '1', 3.00, 0, NULL, 1, 1, 0,
   0, 0, '编辑发文信息与附件', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb44', 'a1b2c3d4e5f6470891a2b3c4d5e6f712',
   '发文删除', NULL, NULL, NULL, NULL, 2,
   'land:docSend:delete', '1', 4.00, 0, NULL, 1, 1, 0,
   0, 0, '删除发文（级联附件）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bb45', 'a1b2c3d4e5f6470891a2b3c4d5e6f712',
   '发文归档', NULL, NULL, NULL, NULL, 2,
   'land:docSend:archive', '1', 5.00, 0, NULL, 1, 1, 0,
   0, 0, '归档到档案管理（必须选择档案类别）', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) ★ 把「挂了按钮权限的菜单」的 is_leaf 置为 0
--
--    jeecg 建菜单树时是 if (!permission.isLeaf()) { 递归子节点 }
--    （SysPermissionController.getTreeList / SysRoleController.getTreeModelList），
--    菜单行 is_leaf = 1 会让它的按钮子节点永远不被遍历到 ——
--    结果是数据库里明明有按钮权限，但菜单管理和角色授权里都看不到。
--    详见 06_archive_permission_buttons.sql 第 4 段的说明。
-- ---------------------------------------------------------------------------
UPDATE `sys_permission` SET `is_leaf` = 0
 WHERE `id` IN (
   'a1b2c3d4e5f6470891a2b3c4d5e6f711',   -- 收文管理（8 个按钮）
   'a1b2c3d4e5f6470891a2b3c4d5e6f712'    -- 发文管理（5 个按钮）
 );

-- ---------------------------------------------------------------------------
-- 5) 对齐按钮行的字段形态（与库内 jeecg 自带按钮权限保持一致，详见 06 脚本第 5 段）
-- ---------------------------------------------------------------------------
UPDATE `sys_permission`
   SET `keep_alive` = NULL, `hide_tab` = NULL, `internal_or_external` = NULL
 WHERE `perms` LIKE 'land:%';

-- ---------------------------------------------------------------------------
-- 6) 授权给「管理员」角色（role_code = admin）
--    确定性主键（rp + 权限ID后 30 位）= 32 位，脚本可重复执行且不会滚出重复授权。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('rp', SUBSTRING(p.`id`, 3)), 'f6817f48af4fb3af11b9e8bf182f618b', p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE (p.`perms` LIKE 'land:docReceive:%' OR p.`perms` LIKE 'land:docSend:%')
  AND p.`del_flag` = 0
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 7) 守卫查询
--    7.1 应输出 0 行：有按钮权限子节点、但 is_leaf 仍是 1 的菜单
-- ---------------------------------------------------------------------------
SELECT p.`name` AS 有问题菜单, p.`is_leaf`, COUNT(c.`id`) AS 按钮数
FROM `sys_permission` p
JOIN `sys_permission` c ON c.`parent_id` = p.`id` AND c.`menu_type` = 2 AND c.`del_flag` = 0
WHERE p.`del_flag` = 0 AND p.`is_leaf` = 1
GROUP BY p.`id`, p.`name`, p.`is_leaf`;

-- 7.2 应输出 13 条（收文 8 + 发文 5），且全部已授权给 admin
SELECT p.`perms` AS 权限码, p.`name` AS 按钮名, m.`name` AS 所属菜单,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_permission` m ON m.`id` = p.`parent_id`
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE (p.`perms` LIKE 'land:docReceive:%' OR p.`perms` LIKE 'land:docSend:%')
  AND p.`del_flag` = 0
ORDER BY m.`sort_no`, p.`sort_no`;
