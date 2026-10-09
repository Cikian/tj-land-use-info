-- =============================================================================
-- 道路设施验收及移交资料台账 · 数据字典脚本
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 建 2 个业务字典：
--   land_road_acceptance_type    道路验收移交-验收类型   竣工验收/规划验收/档案专项验收/移交验收
--   land_road_acceptance_result  道路验收移交-验收结果   合格/不合格/整改后合格
--
-- ★ 为什么「状态」（未验收/验收中/已验收/已移交）不进字典：
--   它是固定 4 值枚举，且驱动前端标签配色（灰/蓝/绿/青）与台账顶部 tab，
--   放代码枚举（LedgerStatus + api/land/ledger.js 的 LEDGER_STATUS）更稳，
--   避免后台误改导致「状态值与配色/统计口径不一致」——与提级论证模块对 status 的处理一致。
--
-- ★ 为什么「13 类资料」也不进字典：
--   它们是**台账表的 13 个物理列**（不是可选值），列名与前端列定义、导出列定义必须
--   三处严格一致，放代码常量（api/land/ledger.js 的 LEDGER_MATERIALS）才能保证。
--
-- ★ item_value 直接用中文文本（与库内既有业务枚举一致）：导出 Excel 后可直接读。
--
-- ★ 固定主键前缀：本模块统一用 '1ed9e0a11ed9e0a11ed9e0a11ed9e%'
--   （档案模块用 a1b2c3d4e5f6470891a2b3c4d5e6ba/bb、提级论证用 ...bc、
--     其字典用 e5c1d2e3f4a54b6c8d9e0f1a2b3c4%——本模块刻意换前缀，互不误删）
--
-- 脚本可重复执行：按固定主键先清理，再插入。
-- 执行后前端字典缓存需刷新（重新登录，或在「系统管理 → 数据字典」里点一次刷新）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理旧记录（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_dict_item` WHERE `id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e3%';
DELETE FROM `sys_dict`      WHERE `id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e2%';

-- ---------------------------------------------------------------------------
-- 2) 字典
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
VALUES
  ('1ed9e0a11ed9e0a11ed9e0a11ed9e201', '道路验收移交-验收类型', 'land_road_acceptance_type',
   '道路设施验收及移交资料台账的验收类型（方案 2.3.2 第 7 项；取值待中心确认，可后台调整）', 0, 'admin', NOW(), NULL, NULL, 0),
  ('1ed9e0a11ed9e0a11ed9e0a11ed9e202', '道路验收移交-验收结果', 'land_road_acceptance_result',
   '道路设施验收结果（方案 2.3.2 第 7 项；与旧库 process_configuration 的竣工验收许可阶段口径对齐）', 0, 'admin', NOW(), NULL, NULL, 0);

-- ---------------------------------------------------------------------------
-- 3) 字典项
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict_item`
  (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  -- 验收类型（4 项）：对齐旧库流程配置的「竣工验收许可阶段」3 个环节 + 移交验收
  ('1ed9e0a11ed9e0a11ed9e0a11ed9e301', '1ed9e0a11ed9e0a11ed9e0a11ed9e201', '竣工验收',     '竣工验收',     '建设工程竣工验收备案（旧流程环节）',       1, 1, 'admin', NOW(), NULL, NULL),
  ('1ed9e0a11ed9e0a11ed9e0a11ed9e302', '1ed9e0a11ed9e0a11ed9e0a11ed9e201', '规划验收',     '规划验收',     '建设工程规划验收合格证核发（旧流程环节）', 2, 1, 'admin', NOW(), NULL, NULL),
  ('1ed9e0a11ed9e0a11ed9e0a11ed9e303', '1ed9e0a11ed9e0a11ed9e0a11ed9e201', '档案专项验收', '档案专项验收', '建设项目档案专项验收（旧流程环节）',       3, 1, 'admin', NOW(), NULL, NULL),
  ('1ed9e0a11ed9e0a11ed9e0a11ed9e304', '1ed9e0a11ed9e0a11ed9e0a11ed9e201', '移交验收',     '移交验收',     '道路工程移交环节的验收',                   4, 1, 'admin', NOW(), NULL, NULL),

  -- 验收结果（3 项）
  ('1ed9e0a11ed9e0a11ed9e0a11ed9e311', '1ed9e0a11ed9e0a11ed9e0a11ed9e202', '合格',       '合格',       '验收合格',           1, 1, 'admin', NOW(), NULL, NULL),
  ('1ed9e0a11ed9e0a11ed9e0a11ed9e312', '1ed9e0a11ed9e0a11ed9e0a11ed9e202', '不合格',     '不合格',     '验收不合格',         2, 1, 'admin', NOW(), NULL, NULL),
  ('1ed9e0a11ed9e0a11ed9e0a11ed9e313', '1ed9e0a11ed9e0a11ed9e0a11ed9e202', '整改后合格', '整改后合格', '整改完成后复验合格', 3, 1, 'admin', NOW(), NULL, NULL);

-- ---------------------------------------------------------------------------
-- 4) 守卫查询
--    4.1 应输出 2 行字典，字典项数分别为 4 / 3
-- ---------------------------------------------------------------------------
SELECT d.`dict_code` AS 字典编码, d.`dict_name` AS 字典名称, COUNT(i.`id`) AS 字典项数
FROM `sys_dict` d
LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_road_acceptance_type', 'land_road_acceptance_result')
GROUP BY d.`id`, d.`dict_code`, d.`dict_name`
ORDER BY d.`dict_code`;

--    4.2 应输出 7 行明细
SELECT d.`dict_code` AS 字典编码, i.`item_text` AS 文本, i.`item_value` AS 值, i.`sort_order` AS 排序
FROM `sys_dict` d
JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_road_acceptance_type', 'land_road_acceptance_result')
ORDER BY d.`dict_code`, i.`sort_order`;
