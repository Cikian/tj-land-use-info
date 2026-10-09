-- =============================================================================
-- 竣工验收项目历史工程资料数字化档案 · 数据字典脚本
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 建 2 个业务字典：
--   land_completion_project_type  竣工验收历史档案-项目类型  道路工程/排水工程/给水工程/燃气工程/其他工程
--   land_completion_retention     竣工验收历史档案-保管期限  永久/定期30年/定期10年/长期
--
-- ★ 为什么「项目类型」是 5 个取值：
--   本项目配套设施的 8 个真实类别是「道路/排水/中水/供水/燃气/路灯/绿化/交通设施」
--   （见清单 §3.1 A 的配套设施类别字典）。历史竣工项目绝大多数集中在道路、排水、
--   给水、燃气这四类，其余（中水/路灯/绿化/交通设施）历史上量很少，统一归入「其他工程」，
--   避免字典里出现一堆常年 0 条目的选项。**中心若要细拆，在「系统管理 → 数据字典」
--   里加字典项即可，不需要改代码**（页面下拉是字典驱动的）。
--
-- ★ 为什么「保管期限」入字典：
--   取值由档案管理规范规定（永久/定期30年/定期10年/长期），措辞相对固定但需要集中维护，
--   且是本模块作为档案域功能的基本属性。
--
-- ★ 为什么「数字化状态」不入字典：
--   未数字化/数字化中/已数字化 是 3 个固定取值，且驱动前端标签配色（灰/蓝/绿）、
--   统计口径与「数字化进度」判断；放代码枚举（DigitizeStatus）比放字典表更稳，
--   避免后台误改导致「状态与配色/统计不一致」。后端同时对写入值做白名单校验。
--   —— 与第 7 项台账模块对 status 的处理完全一致。
--
-- ★ item_value 直接用中文文本（与库内既有业务枚举一致）：导出 Excel 后可直接读。
--
-- ★ 固定主键前缀：本模块统一用 '3ed9e0a11ed9e0a11ed9e0a11ed9e%'
--   （档案模块用 a1b2c3d4e5f6470891a2b3c4d5e6ba/bb、提级论证用 …bc、
--     第 7 项台账用 1ed9e0a11ed9e0a11ed9e0a11ed9e%——本模块独立前缀，互不误删）
--
-- 脚本可重复执行：按固定主键先清理，再插入。
-- 执行后前端字典缓存需刷新（重新登录，或在「系统管理 → 数据字典」里点一次刷新）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理旧记录（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_dict_item` WHERE `id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e3%';
DELETE FROM `sys_dict`      WHERE `id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e2%';

-- ---------------------------------------------------------------------------
-- 2) 字典
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
VALUES
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e201', '竣工验收历史档案-项目类型', 'land_completion_project_type',
   '竣工验收项目历史工程资料的项目类型（方案 2.3.2 第 8 项；取值可按中心口径在数据字典里增改）', 0, 'admin', NOW(), NULL, NULL, 0),
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e202', '竣工验收历史档案-保管期限', 'land_completion_retention',
   '历史工程资料数字化档案的保管期限（档案管理规范取值：永久/定期30年/定期10年/长期）', 0, 'admin', NOW(), NULL, NULL, 0);

-- ---------------------------------------------------------------------------
-- 3) 字典项
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict_item`
  (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  -- 项目类型（5 项）
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e301', '3ed9e0a11ed9e0a11ed9e0a11ed9e201', '道路工程', '道路工程', '道路及配套管线工程',       1, 1, 'admin', NOW(), NULL, NULL),
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e302', '3ed9e0a11ed9e0a11ed9e0a11ed9e201', '排水工程', '排水工程', '雨水/污水排水工程',        2, 1, 'admin', NOW(), NULL, NULL),
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e303', '3ed9e0a11ed9e0a11ed9e0a11ed9e201', '给水工程', '给水工程', '给水（自来水）工程',       3, 1, 'admin', NOW(), NULL, NULL),
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e304', '3ed9e0a11ed9e0a11ed9e0a11ed9e201', '燃气工程', '燃气工程', '燃气工程',                 4, 1, 'admin', NOW(), NULL, NULL),
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e305', '3ed9e0a11ed9e0a11ed9e0a11ed9e201', '其他工程', '其他工程', '中水/路灯/绿化/交通设施等历史量较少的类别，需要时可在数据字典里拆分', 5, 1, 'admin', NOW(), NULL, NULL),

  -- 保管期限（4 项）
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e311', '3ed9e0a11ed9e0a11ed9e0a11ed9e202', '永久',     '永久',     '永久保管',       1, 1, 'admin', NOW(), NULL, NULL),
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e312', '3ed9e0a11ed9e0a11ed9e0a11ed9e202', '定期30年', '定期30年', '定期 30 年',     2, 1, 'admin', NOW(), NULL, NULL),
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e313', '3ed9e0a11ed9e0a11ed9e0a11ed9e202', '定期10年', '定期10年', '定期 10 年',     3, 1, 'admin', NOW(), NULL, NULL),
  ('3ed9e0a11ed9e0a11ed9e0a11ed9e314', '3ed9e0a11ed9e0a11ed9e0a11ed9e202', '长期',     '长期',     '长期保管',       4, 1, 'admin', NOW(), NULL, NULL);

-- ---------------------------------------------------------------------------
-- 4) 守卫查询
--    4.1 应输出 2 行字典，字典项数分别为 5 / 4
-- ---------------------------------------------------------------------------
SELECT d.`dict_code` AS 字典编码, d.`dict_name` AS 字典名称, COUNT(i.`id`) AS 字典项数
FROM `sys_dict` d
LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_completion_project_type', 'land_completion_retention')
GROUP BY d.`id`, d.`dict_code`, d.`dict_name`
ORDER BY d.`dict_code`;

--    4.2 应输出 9 行明细
SELECT d.`dict_code` AS 字典编码, i.`item_text` AS 文本, i.`item_value` AS 值, i.`sort_order` AS 排序
FROM `sys_dict` d
JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_completion_project_type', 'land_completion_retention')
ORDER BY d.`dict_code`, i.`sort_order`;

--    4.3 应输出 0 行：数字化状态不得出现在任何字典里（它是代码枚举）
SELECT `dict_code`, `dict_name` FROM `sys_dict`
WHERE `dict_code` LIKE '%completion%' AND `dict_code` LIKE '%status%';
