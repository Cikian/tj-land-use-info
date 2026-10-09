-- =============================================================================
-- 提级论证管理 · 菜单脚本（方案 2.3.3）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 建立两级菜单（与方案 2.3.3 四项子功能一一对号，便于验收逐项点验）：
--   提级论证管理（一级，url=/land/escalation，分组）
--     ├─ 项目录入         land/escalation/EscalationEntryList   ← 方案 2.3.3-1
--     ├─ 查询统计         land/escalation/EscalationQuery       ← 方案 2.3.3-2
--     ├─ 资料及台账管理   land/escalation/EscalationLedger      ← 方案 2.3.3-3
--     └─ 提级论证审批     land/escalation/EscalationAudit       ← 方案 2.3.3-4
--
-- ★ 第 4 个菜单名保留「提级论证审批」（与方案第 4 项同名，便于验收对号），
--   但页面标题是「审核意见登记」：本期不驱动流程，实际论证在线下办理，
--   此处只登记论证结论与审核意见（需求原文只要求「可批注审核意见」）。
--
-- ★ 菜单行不配权限码（perms 留空）：
--   接口权限统一登记为 sys_permission 里 menu_type=2 的「按钮权限」，
--   见同目录 04_escalation_permission_buttons.sql（含给 admin 角色授权）。
--   原因见 docs/档案管理-实现说明.md 7B 节：jeecg 的前端 v-has 与菜单管理的按钮授权
--   只认 menu_type=2 的行，权限码写在菜单行上会「看不见、授不了」。
--
-- 脚本可重复执行：先按固定主键清除旧记录，再插入。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) 清理旧记录（固定主键，保证幂等）
--    一级 e5c...d01 / 子菜单 ...d02~d05
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` IN (
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d01',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d03',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d04',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d05');
DELETE FROM `sys_permission`
 WHERE `id` IN (
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d01',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d03',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d04',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d05');

-- ---------------------------------------------------------------------------
-- 2) 菜单
--    ★ is_leaf 规则：菜单下面挂了按钮权限（menu_type=2）时必须为 0，
--      否则 jeecg 建树不会递归到按钮，菜单管理与角色授权里都看不到它们。
--      本脚本里：一级=0（下挂 4 个子菜单）、项目录入=0（挂 5 个按钮）、
--                查询统计=0（挂 1 个按钮）、资料及台账管理=1、提级论证审批=1。
--    ★ sort_no：档案管理是 5.00，本模块紧随其后取 6.00。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4d01', NULL,
   '提级论证管理', '/land/escalation', 'layouts/RouteView', NULL, NULL, 0,
   NULL, '0', 6.00, 0, 'audit', 1, 0, 0,
   0, 0, '提级论证管理（方案 2.3.3，记录型台账：录入/查询统计/台账/审核意见登记）', 'admin', NOW(), 0, 0, '1', 0),

  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d01',
   '项目录入', '/land/escalation/entry', 'land/escalation/EscalationEntryList', 'EscalationEntryList', NULL, 1,
   NULL, '0', 1.00, 0, 'form', 1, 0, 1,
   0, 0, '提级论证项目录入：基本信息 + 关联地块（选填）+ 论证信息与结果 + 论证材料（方案 2.3.3 第 1 项）', 'admin', NOW(), 0, 0, '1', 0),

  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4d03', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d01',
   '查询统计', '/land/escalation/query', 'land/escalation/EscalationQuery', 'EscalationQuery', NULL, 1,
   NULL, '0', 2.00, 0, 'bar-chart', 1, 0, 1,
   0, 0, '提级论证项目查询统计：按基本信息与论证结果多维查询、统计图、结果导出（方案 2.3.3 第 2 项）', 'admin', NOW(), 0, 0, '1', 0),

  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4d04', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d01',
   '资料及台账管理', '/land/escalation/ledger', 'land/escalation/EscalationLedger', 'EscalationLedger', NULL, 1,
   NULL, '0', 3.00, 0, 'table', 1, 1, 1,
   0, 0, '提级论证数据资料及台账管理：台账方式管理、快速查看各项目当前状态（方案 2.3.3 第 3 项）', 'admin', NOW(), 0, 0, '1', 0),

  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4d05', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d01',
   '提级论证审批', '/land/escalation/audit', 'land/escalation/EscalationAudit', 'EscalationAudit', NULL, 1,
   NULL, '0', 4.00, 0, 'solution', 1, 1, 1,
   0, 0, '对各单位提交的提级论证材料进行初步审批、可批注审核意见（方案 2.3.3 第 4 项；流程在线下办理，此处登记结论与意见）', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 3) 授权给「管理员」角色（role_code = admin，id = f6817f48af4fb3af11b9e8bf182f618b）
--    如需授权给其它角色，把下面的 role_id 换成对应 sys_role.id 即可；
--    也可以在「系统管理 → 角色管理 → 授权」里按菜单/按钮勾选。
--    sys_role_permission.id 是 varchar(32) 且唯一，这里用「rp + 权限ID后 30 位」拼确定性主键
--    （原因见 04 脚本第 7 段说明：UUID() 在 MySQL 5.7 同语句高频调用会撞主键）。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('rp', SUBSTRING(p.`id`, 3)), 'f6817f48af4fb3af11b9e8bf182f618b', p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` IN (
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d01',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d03',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d04',
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d05')
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 4) 守卫查询
--    4.1 应输出 5 行菜单，且「项目录入 / 查询统计」的 is_leaf = 0
-- ---------------------------------------------------------------------------
SELECT p.`id`, p.`name` AS 菜单, p.`url`, p.`component`, p.`is_leaf` AS 是否叶子,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`id` LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%'
  AND p.`del_flag` = 0
ORDER BY p.`sort_no`;
