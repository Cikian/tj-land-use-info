-- =============================================================================
-- 道路交付及养护协议移交事项 · 数据字典脚本
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 只建 1 个字典：
--   land_road_handover_type   道路移交-移交类型   道路交付 / 养护协议 / 正式移交
--
-- ★ 为什么「状态」（待移交/移交中/已移交）不进字典：
--   它是固定 3 值枚举，驱动前端标签配色（灰/橙/绿）与列表筛选，放代码枚举
--   （HandoverStatus）比放字典表更稳 —— 与台账模块对 status 的处理完全一致。
--
-- ★ 固定主键前缀：本模块统一用 '2ed9e0a11ed9e0a11ed9e0a11ed9e%'
--   （菜单 …e001、按钮 …e1xx、字典 …e2xx、字典项 …e3xx）
--
-- 脚本可重复执行：先按固定主键清理，再插入。
-- 执行后前端字典缓存需刷新（重新登录，或在「系统管理 → 数据字典」里点一次刷新）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理旧记录（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_dict_item` WHERE `id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e3%';
DELETE FROM `sys_dict`      WHERE `id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e2%';

-- ---------------------------------------------------------------------------
-- 2) 字典
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
VALUES
  ('2ed9e0a11ed9e0a11ed9e0a11ed9e201', '道路移交-移交类型', 'land_road_handover_type',
   '道路交付及养护协议移交事项的类型（方案 2.3.2 第 6 项；取值待中心确认，可后台调整）', 0, 'admin', NOW(), NULL, NULL, 0);

-- ---------------------------------------------------------------------------
-- 3) 字典项
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict_item`
  (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('2ed9e0a11ed9e0a11ed9e0a11ed9e301', '2ed9e0a11ed9e0a11ed9e0a11ed9e201', '道路交付',   '道路交付',
   '道路工程建成后的交付（尚未涉及养护协议）', 1, 1, 'admin', NOW(), NULL, NULL),
  ('2ed9e0a11ed9e0a11ed9e0a11ed9e302', '2ed9e0a11ed9e0a11ed9e0a11ed9e201', '养护协议',   '养护协议',
   '签订养护协议（约定养护范围与养护期）',     2, 1, 'admin', NOW(), NULL, NULL),
  ('2ed9e0a11ed9e0a11ed9e0a11ed9e303', '2ed9e0a11ed9e0a11ed9e0a11ed9e201', '正式移交',   '正式移交',
   '完成正式移交（接收管养单位接管）',         3, 1, 'admin', NOW(), NULL, NULL);

-- ---------------------------------------------------------------------------
-- 4) 守卫查询
--    4.1 应输出 1 行字典，字典项数 3
-- ---------------------------------------------------------------------------
SELECT d.`dict_code` AS 字典编码, d.`dict_name` AS 字典名称, COUNT(i.`id`) AS 字典项数
FROM `sys_dict` d
LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` = 'land_road_handover_type'
GROUP BY d.`id`, d.`dict_code`, d.`dict_name`;

--    4.2 应输出 3 行明细
SELECT d.`dict_code` AS 字典编码, i.`item_text` AS 文本, i.`item_value` AS 值, i.`sort_order` AS 排序
FROM `sys_dict` d
JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` = 'land_road_handover_type'
ORDER BY i.`sort_order`;
