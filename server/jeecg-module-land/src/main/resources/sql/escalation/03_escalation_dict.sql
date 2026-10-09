-- =============================================================================
-- 提级论证管理 · 数据字典脚本
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 建立 3 个业务字典（★ 本模块是 land 域第一个使用 sys_dict 的子模块；
-- 档案模块的状态/密级是前端硬编码 + t_archive_category 表，没有走字典）：
--
--   land_escalation_project_type   提级论证-项目类型   产业类/基础设施类/民生类/其他
--   land_escalation_arg_result     提级论证-论证结果   通过/基本通过/需补充材料/需进一步论证/不通过
--   land_escalation_material_type  提级论证-材料类型   申请材料/论证报告/支撑材料/其他
--
-- 「是否土地整理项目」复用库内既有字典 yn（1=是 / 0=否），不另建。
--
-- ★ 为什么这些进字典、而「办理状态」不进字典：
--   · project_type / arg_result / material_type 是**业务可选值**，中心随时可能调整措辞
--     （清单 7.3.1 对项目类型明确标注「需中心确认字典」），做成字典可后台改，不用改代码；
--   · status（未办理/办理中/已办结/已归档）是**固定 4 值的枚举**，且驱动前端标签配色
--     （灰/蓝/绿/青），放代码枚举里更稳，避免后台误改导致状态与配色不一致。
--
-- ★ item_value 直接用中文文本（与档案模块现状一致）：
--   库里现有业务枚举（档案状态、密级等）都是中文直存，导出 Excel 后可读性最好，
--   也避免「存编码、显示文本」两层映射带来的对不上问题。
--
-- 脚本可重复执行：按 dict_code 先清字典项、再清字典、然后插入（sys_dict.dict_code 有唯一键）。
-- 执行后前端字典缓存需刷新（重新登录，或在「系统管理 → 数据字典」里点一次刷新）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理旧记录（幂等：先按 dict_code 找出旧 id，再删字典项与字典）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_dict_item`
 WHERE `dict_id` IN (
   SELECT `id` FROM `sys_dict`
    WHERE `dict_code` IN ('land_escalation_project_type',
                          'land_escalation_arg_result',
                          'land_escalation_material_type'));

DELETE FROM `sys_dict`
 WHERE `dict_code` IN ('land_escalation_project_type',
                       'land_escalation_arg_result',
                       'land_escalation_material_type');

-- ---------------------------------------------------------------------------
-- 2) 字典（固定主键，便于幂等与排查）
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
VALUES
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4e01', '提级论证-项目类型', 'land_escalation_project_type',
   '提级论证项目类型（方案 2.3.3 第 1 项；取值待中心确认，可后台调整）', 0, 'admin', NOW(), NULL, NULL, 0),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4e02', '提级论证-论证结果', 'land_escalation_arg_result',
   '提级论证结果（方案 2.3.3 第 2 项查询条件与台账列；与审核意见快捷短语一致）', 0, 'admin', NOW(), NULL, NULL, 0),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4e03', '提级论证-材料类型', 'land_escalation_material_type',
   '提级论证材料类型（方案 2.3.3 第 1 项材料上传时必选）', 0, 'admin', NOW(), NULL, NULL, 0);

-- ---------------------------------------------------------------------------
-- 3) 字典项
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict_item`
  (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  -- 项目类型（4 项）
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4f01', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e01', '产业类',     '产业类',     '产业类项目',     1, 1, 'admin', NOW(), NULL, NULL),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4f02', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e01', '基础设施类', '基础设施类', '基础设施类项目', 2, 1, 'admin', NOW(), NULL, NULL),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4f03', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e01', '民生类',     '民生类',     '民生类项目',     3, 1, 'admin', NOW(), NULL, NULL),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4f04', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e01', '其他',       '其他',       '其他类型',       4, 1, 'admin', NOW(), NULL, NULL),

  -- 论证结果（5 项，与审核意见的 5 个快捷短语同词表）
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4g01', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e02', '通过',         '通过',         '论证通过',           1, 1, 'admin', NOW(), NULL, NULL),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4g02', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e02', '基本通过',     '基本通过',     '基本通过，需按意见完善', 2, 1, 'admin', NOW(), NULL, NULL),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4g03', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e02', '需补充材料',   '需补充材料',   '需补充材料后再议',   3, 1, 'admin', NOW(), NULL, NULL),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4g04', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e02', '需进一步论证', '需进一步论证', '需进一步论证',       4, 1, 'admin', NOW(), NULL, NULL),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4g05', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e02', '不通过',       '不通过',       '论证不通过',         5, 1, 'admin', NOW(), NULL, NULL),

  -- 材料类型（4 项）
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4h01', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e03', '申请材料', '申请材料', '提级论证申请材料', 1, 1, 'admin', NOW(), NULL, NULL),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4h02', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e03', '论证报告', '论证报告', '论证报告',         2, 1, 'admin', NOW(), NULL, NULL),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4h03', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e03', '支撑材料', '支撑材料', '支撑性材料',       3, 1, 'admin', NOW(), NULL, NULL),
  ('e5c1d2e3f4a54b6c8d9e0f1a2b3c4h04', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4e03', '其他',     '其他',     '其他材料',         4, 1, 'admin', NOW(), NULL, NULL);

-- ---------------------------------------------------------------------------
-- 4) 守卫查询
--    4.1 应输出 3 行字典，字典项数分别为 4 / 5 / 4
-- ---------------------------------------------------------------------------
SELECT d.`dict_code` AS 字典编码, d.`dict_name` AS 字典名称, COUNT(i.`id`) AS 字典项数
FROM `sys_dict` d
LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_escalation_project_type',
                        'land_escalation_arg_result',
                        'land_escalation_material_type')
GROUP BY d.`id`, d.`dict_code`, d.`dict_name`
ORDER BY d.`dict_code`;

--    4.2 应输出 13 行明细（供人工核对取值）
SELECT d.`dict_code` AS 字典编码, i.`item_text` AS 文本, i.`item_value` AS 值, i.`sort_order` AS 排序, i.`status` AS 状态
FROM `sys_dict` d
JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_escalation_project_type',
                        'land_escalation_arg_result',
                        'land_escalation_material_type')
ORDER BY d.`dict_code`, i.`sort_order`;

--    4.3 应输出 1 行：复用既有 yes/no 字典
SELECT `dict_code`, `dict_name`, `description` FROM `sys_dict` WHERE `dict_code` = 'yn';
