-- =============================================================================
-- 数据管理 · 其余 5 项功能的菜单脚本（方案 2.3.1（三）第 1、2、4、5、6 项）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- ★ 前置：先执行 03_land_data_menu.sql（那里建了「数据管理」一级菜单与
--   第 3 项「经营性用地批量导入」）。本脚本补齐剩余 5 项。
--
-- ★ 完整菜单结构（sort_no 1.00~6.00 对应方案 2.3.1（三）的 6 项功能）：
--   数据管理 /land/data  (layouts/RouteView)
--     ├─ 1.00 经营性用地批量导入     land/data/LandImportList      ← 03 脚本
--     ├─ 2.00 经营性用地信息录入     land/data/LandEntry           ← 本脚本
--     ├─ 3.00 配套地块数据录入       land/data/FacilityEntry       ← 本脚本
--     ├─ 4.00 配套信息批量导入       land/data/FacilityImport      ← 本脚本
--     ├─ 5.00 配套附件管理           land/data/AttachmentList      ← 本脚本
--     └─ 6.00 数据更新与移除         land/data/DataRecycle         ← 本脚本
--
-- ★ 为什么「经营性用地信息录入」与「经营性用地批量导入」是两个菜单而不是一个页面的两个 tab：
--   两者的使用场景与出错代价完全不同 —— 批量导入一次写几百条、错了影响面大，
--   所以它有自己的「预览 → 确认 → 回执」流程；逐条录入是日常补录，
--   表单即时校验、保存即生效。合成一个页面会让「一次性导入」的确认步骤被弱化。
--
-- ★ 菜单行不配权限码（perms 必须为 NULL）：接口权限统一登记为 sys_permission 里
--   menu_type=2 的「按钮权限」，见同目录 08_data_permission_buttons.sql。
--   原因见 docs/档案管理-实现说明.md 7B 节：jeecg 的前端 v-has 与菜单授权只认 menu_type=2。
--
-- ★★ 主键长度必须正好 32 位（sys_permission.id 是 varchar(32)）
--   本模块前缀 = 7a1f0d2c4e5b4d8a9c3f6b1e （28 位），后缀 4 位：
--     03 脚本已用：a001 数据管理（一级） / a002 经营性用地批量导入（二级）
--     本脚本使用：a003 经营性用地信息录入 / a004 配套地块数据录入
--                a005 配套信息批量导入 / a006 配套附件管理 / a007 数据更新与移除
--   ★ 踩坑记录：id 写成 34 位会直接报 ERROR 1406 Data too long for column 'id'，
--     而脚本是「先 DELETE 后 INSERT」——前半段已经跑掉、后半段全失败，
--     界面上看起来就像「菜单建了但授权没建」。脚本末尾 5.3 有长度守卫查询。
--
-- 脚本可重复执行：先按固定主键清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理旧记录（幂等）
--    只清这 5 个子菜单及其授权，不动 03 脚本建的 a001/a002。
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` IN (
   '7a1f0d2c4e5b4d8a9c3f6b1ea003', '7a1f0d2c4e5b4d8a9c3f6b1ea004',
   '7a1f0d2c4e5b4d8a9c3f6b1ea005', '7a1f0d2c4e5b4d8a9c3f6b1ea006',
   '7a1f0d2c4e5b4d8a9c3f6b1ea007');
DELETE FROM `sys_permission`
 WHERE `id` IN (
   '7a1f0d2c4e5b4d8a9c3f6b1ea003', '7a1f0d2c4e5b4d8a9c3f6b1ea004',
   '7a1f0d2c4e5b4d8a9c3f6b1ea005', '7a1f0d2c4e5b4d8a9c3f6b1ea006',
   '7a1f0d2c4e5b4d8a9c3f6b1ea007');

-- ---------------------------------------------------------------------------
-- 2) 前置校验：父菜单「数据管理」必须存在（由 03 脚本创建）
--    应输出 1 行；为 0 行说明还没跑 03 脚本，后续插入会挂空父级。
-- ---------------------------------------------------------------------------
SELECT `id`, `name`, `url`, `component`, `is_leaf` AS 是否叶子
FROM `sys_permission`
WHERE `id` = '7a1f0d2c4e5b4d8a9c3f6b1ea001' AND `del_flag` = 0;

-- ---------------------------------------------------------------------------
-- 3) 5 个二级菜单
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  -- ===== 2.00 经营性用地信息录入（方案 2.3.1（三）第 1 项）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1ea003', '7a1f0d2c4e5b4d8a9c3f6b1ea001',
   '经营性用地信息录入', '/land/data/land', 'land/data/LandEntry', 'LandEntry', NULL, 1,
   NULL, '0', 2.00, 0, 'form', 1, 0, 1,
   0, 0, '逐条录入/编辑/移除经营性用地（出让宗地）34 个业务字段：唯一性校验、字典联动、变更履历、软删恢复（方案 2.3.1（三）第 1 项）', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 3.00 配套地块数据录入（方案 2.3.1（三）第 2 项）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1ea004', '7a1f0d2c4e5b4d8a9c3f6b1ea001',
   '配套地块数据录入', '/land/data/facility', 'land/data/FacilityEntry', 'FacilityEntry', NULL, 1,
   NULL, '0', 3.00, 0, 'apartment', 1, 0, 1,
   0, 0, '1 宗地 N 配套项目录入 + 六大阶段 29 环节进度录入（含阶段汇总、预计结束时间按工作日推算、逾期标记）（方案 2.3.1（三）第 2 项）', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 4.00 配套信息批量导入（方案 2.3.1（三）第 4 项）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1ea005', '7a1f0d2c4e5b4d8a9c3f6b1ea001',
   '配套信息批量导入', '/land/data/facilityImport', 'land/data/FacilityImport', 'FacilityImport', NULL, 1,
   NULL, '0', 4.00, 0, 'cloud-upload', 1, 0, 1,
   0, 0, 'Excel 批量导入配套项目：模板下载 + 预览校验 + 宗地存在性校验与孤儿清单 + 错误回执 + 入库（方案 2.3.1（三）第 4 项）', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 5.00 配套附件管理（方案 2.3.1（三）第 5 项）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1ea006', '7a1f0d2c4e5b4d8a9c3f6b1ea001',
   '配套附件管理', '/land/data/attachment', 'land/data/AttachmentList', 'AttachmentList', NULL, 1,
   NULL, '0', 5.00, 0, 'paper-clip', 1, 0, 1,
   0, 0, '宗地/配套项目/环节进度附件的上传、查询、预览、下载（替代旧系统 4 个固定槽位 + 目录扫描）（方案 2.3.1（三）第 5 项）', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 6.00 数据更新与移除（方案 2.3.1（三）第 6 项）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1ea007', '7a1f0d2c4e5b4d8a9c3f6b1ea001',
   '数据更新与移除', '/land/data/recycle', 'land/data/DataRecycle', 'DataRecycle', NULL, 1,
   NULL, '0', 6.00, 0, 'history', 1, 0, 1,
   0, 0, '回收站（软删记录的查看与恢复，含唯一键冲突检测）+ 字段级变更留痕（方案 2.3.1（三）第 6 项）', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) 授权给「管理员」角色
--    ✅ MD5(CONCAT('role-perm:', p.id)) —— 32 位、确定性、不会撞主键
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT MD5(CONCAT('role-perm:', p.`id`)), 'f6817f48af4fb3af11b9e8bf182f618b', p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` IN (
   '7a1f0d2c4e5b4d8a9c3f6b1ea003', '7a1f0d2c4e5b4d8a9c3f6b1ea004',
   '7a1f0d2c4e5b4d8a9c3f6b1ea005', '7a1f0d2c4e5b4d8a9c3f6b1ea006',
   '7a1f0d2c4e5b4d8a9c3f6b1ea007')
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 5) 守卫查询
--    5.1 应输出 6 行：「数据管理」下 6 个子菜单，sort_no 1.00~6.00
-- ---------------------------------------------------------------------------
SELECT p.`sort_no` AS 排序, p.`name` AS 菜单, p.`url`, p.`component`,
       LENGTH(p.`id`) AS id长度,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`parent_id` = '7a1f0d2c4e5b4d8a9c3f6b1ea001' AND p.`del_flag` = 0
ORDER BY p.`sort_no`;

--    5.2 应输出 0 行：父菜单不存在（会变成孤儿菜单，界面上看不到）
SELECT p.`id`, p.`name` AS 孤儿菜单
FROM `sys_permission` p
LEFT JOIN `sys_permission` parent ON parent.`id` = p.`parent_id`
WHERE p.`id` IN (
   '7a1f0d2c4e5b4d8a9c3f6b1ea003', '7a1f0d2c4e5b4d8a9c3f6b1ea004',
   '7a1f0d2c4e5b4d8a9c3f6b1ea005', '7a1f0d2c4e5b4d8a9c3f6b1ea006',
   '7a1f0d2c4e5b4d8a9c3f6b1ea007')
  AND parent.`id` IS NULL;

--    5.3 应输出 0 行：本模块 id 长度超过 32 位（varchar(32) 会报 1406）
SELECT p.`id`, LENGTH(p.`id`) AS id长度, p.`name` AS 菜单
FROM `sys_permission` p
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%' AND LENGTH(p.`id`) > 32;

--    5.4 应输出 0 行：本模块菜单行上误写了权限码（应与 08 脚本一致，menu_type=2 才有 perms）
SELECT p.`name` AS 菜单, p.`menu_type`, p.`perms`
FROM `sys_permission` p
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%' AND p.`menu_type` = 1 AND p.`perms` IS NOT NULL;
