-- =============================================================================
-- 统计预警 · 菜单与按钮权限
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- ★ 本脚本只建**管理端（admin-client）**的两张维护页：
--     统计预警（一级，url=/land/stat，分组）
--       ├─ 预警等级维护   land/stat/WarningLevelList    ← 蓝/黄/橙/红的阈值区间
--       └─ 决策意见维护   land/stat/DecisionOpinionList ← 问题类型 × 等级 → 处理建议
--
-- ★★ 大屏端（stargis-client-web）的「预警信息查询」**不占菜单**：
--   它是「查询统计」页的第 4 个页签（与经营性用地查询 / 用地配套项目查询 /
--   高级查询并列），导航仍是 `views/screen/config.js` 的 headerMenus 七个一级项。
--   大屏端顶栏是前端常量、不由 sys_permission 驱动，所以这里不需要为它建菜单。
--   ★ 也**不要**给大屏端加 v-has：大屏的 GetPermissionList 从不下发按钮权限清单，
--     指令会把元素从 DOM 删掉 → 表现是按钮全消失（AGENTS.md §3.6）。
--
-- ★ 固定主键（28 位 = 24 位模块前缀 + 4 位行号，逐字照抄不要手数）
--     模块前缀                    = 8c2e5d7a1b3f4c6d9e0a2b4c   （24 位）
--     一级菜单「统计预警」          = 8c2e5d7a1b3f4c6d9e0a2b4ca001
--     二级菜单「预警等级维护」      = 8c2e5d7a1b3f4c6d9e0a2b4ca002
--     二级菜单「决策意见维护」      = 8c2e5d7a1b3f4c6d9e0a2b4ca003
--     按钮权限 b001~b004 见下
--   清理用统配前缀 = '8c2e5d7a1b3f4c6d9e0a2b4c%'（24 位，只命中本模块）
--   ★ sys_permission.id 是 varchar(32)：28 位留足余量，写超 32 位会
--     ERROR 1406 Data too long，且 DELETE 已执行、INSERT 全失败（本项目踩过）。
--
-- ★ is_leaf 规则：菜单下挂了按钮权限（menu_type=2）时必须为 0，否则建树不递归，
--   菜单管理与角色授权里都看不到按钮（档案模块坑 7B.0）。
--
-- ★ sort_no：数据管理 4.00、档案 3.00、提级论证 6.00，本模块取 4.50，
--   紧跟在「数据管理」后面（预警是数据管理产生的环节进度算出来的，放一起好找）。
--
-- 脚本可重复执行：先按前缀清（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清 Redis 的 shiro 权限缓存）才生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理旧记录（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` LIKE '8c2e5d7a1b3f4c6d9e0a2b4c%';
DELETE FROM `sys_permission`
 WHERE `id` LIKE '8c2e5d7a1b3f4c6d9e0a2b4c%';

-- ---------------------------------------------------------------------------
-- 2) 菜单（一级 + 两个二级）
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('8c2e5d7a1b3f4c6d9e0a2b4ca001', NULL,
   '统计预警', '/land/stat', 'layouts/RouteView', NULL, NULL, 0,
   NULL, '0', 4.50, 0, 'alert', 1, 0, 0,
   0, 0, '统计预警（方案 2.3.1（五）· 分级预警）：预警等级阈值、决策意见维护；预警记录查询在大屏端「查询统计 → 预警信息查询」', 'admin', NOW(), 0, 0, '1', 0),

  ('8c2e5d7a1b3f4c6d9e0a2b4ca002', '8c2e5d7a1b3f4c6d9e0a2b4ca001',
   '预警等级维护', '/land/stat/warningLevel', 'land/stat/WarningLevelList', 'WarningLevelList', NULL, 1,
   NULL, '0', 1.00, 0, 'flag', 1, 0, 1,
   0, 0, '预警等级阈值维护：蓝/黄/橙/红四级的逾期天数区间（1-10 / 11-20 / 21-30 / 31-100），改这里直接决定分级结果', 'admin', NOW(), 0, 0, '1', 0),

  ('8c2e5d7a1b3f4c6d9e0a2b4ca003', '8c2e5d7a1b3f4c6d9e0a2b4ca001',
   '决策意见维护', '/land/stat/decisionOpinion', 'land/stat/DecisionOpinionList', 'DecisionOpinionList', NULL, 1,
   NULL, '0', 2.00, 0, 'message', 1, 0, 1,
   0, 0, '决策意见维护：问题类型 × 预警等级 → 处理建议；问题类型留空的记录表示该等级的通用兜底意见', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 3) 按钮权限（menu_type=2）
--    ★ 预警等级 / 决策意见的查询接口本身不加 @RequiresPermissions
--      （列表是页面首屏数据，拦了会白屏），只有**写操作**（新增/编辑/删除）
--      在后端用 `land:stat:warningLevel:edit` / `land:stat:decisionOpinion:edit`
--      校验；查询权限行保留给菜单管理里的可见性与前端 v-has 用。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  ('8c2e5d7a1b3f4c6d9e0a2b4cb001', '8c2e5d7a1b3f4c6d9e0a2b4ca002',
   '预警等级-查询', NULL, NULL, NULL, NULL, 2,
   'land:stat:warningLevel:list', '1', 1.00, 0, NULL, 0, 1, 0,
   0, 0, '预警等级列表查询', 'admin', NOW(), 0, 0, '1', 0),

  ('8c2e5d7a1b3f4c6d9e0a2b4cb002', '8c2e5d7a1b3f4c6d9e0a2b4ca002',
   '预警等级-维护', NULL, NULL, NULL, NULL, 2,
   'land:stat:warningLevel:edit', '1', 2.00, 0, NULL, 0, 1, 0,
   0, 0, '预警等级新增 / 编辑 / 删除', 'admin', NOW(), 0, 0, '1', 0),

  ('8c2e5d7a1b3f4c6d9e0a2b4cb003', '8c2e5d7a1b3f4c6d9e0a2b4ca003',
   '决策意见-查询', NULL, NULL, NULL, NULL, 2,
   'land:stat:decisionOpinion:list', '1', 1.00, 0, NULL, 0, 1, 0,
   0, 0, '决策意见列表查询', 'admin', NOW(), 0, 0, '1', 0),

  ('8c2e5d7a1b3f4c6d9e0a2b4cb004', '8c2e5d7a1b3f4c6d9e0a2b4ca003',
   '决策意见-维护', NULL, NULL, NULL, NULL, 2,
   'land:stat:decisionOpinion:edit', '1', 2.00, 0, NULL, 0, 1, 0,
   0, 0, '决策意见新增 / 编辑 / 删除', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) 授权给「管理员」角色（role_code = admin，id = f6817f48af4fb3af11b9e8bf182f618b）
--    sys_role_permission.id 用「rp + 权限ID后 30 位」拼确定性主键
--    （UUID() 在同语句高频调用会撞主键，见 04 脚本第 7 段）。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('rp', SUBSTRING(p.`id`, 3)), 'f6817f48af4fb3af11b9e8bf182f618b', p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` LIKE '8c2e5d7a1b3f4c6d9e0a2b4c%'
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 5) 守卫查询
--    5.1 应输出 7 行（3 菜单 + 4 按钮），两个二级菜单 is_leaf = 0
--    5.2 id 长度必须 <= 32（防手改/复制丢字符）
-- ---------------------------------------------------------------------------
SELECT p.`id`, p.`name` AS 名称, p.`url`, p.`component`, p.`menu_type` AS 类型,
       p.`perms`, p.`is_leaf` AS 是否叶子,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`id` LIKE '8c2e5d7a1b3f4c6d9e0a2b4c%'
  AND p.`del_flag` = 0
ORDER BY p.`menu_type`, p.`sort_no`;

SELECT p.`id`, CHAR_LENGTH(p.`id`) AS id长度
  FROM `sys_permission` p
 WHERE p.`id` LIKE '8c2e5d7a1b3f4c6d9e0a2b4c%'
   AND CHAR_LENGTH(p.`id`) > 32;
