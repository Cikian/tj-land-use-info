-- =============================================================================
-- 提级论证管理 · 【危险】清空重建脚本
-- 目标库：tj-jyxyd（本地或线上）
--
-- ★★★ 警告 ★★★
--   本脚本会 **DROP 三张表，删除其中全部数据**，且不可恢复。
--   只在「确认要丢弃本模块全部数据、重建空表」时执行：
--     · 本地开发环境重置；
--     · 线上首次上线前若表建坏了需要推倒重来（执行前请先备份）。
--   **日常建表 / 上线同步一律用 01_t_escalation.sql（IF NOT EXISTS，不删数据）。**
--
-- 执行顺序建议（清空重建的完整流程）：
--   1) 00_reset_tables.sql           ← 本脚本（危险）
--   2) 01_t_escalation.sql           ← 重建空表
--   3) 03_escalation_dict.sql        ← 字典（幂等）
--   4) 02_escalation_menu.sql        ← 菜单（幂等）
--   5) 04_escalation_permission_buttons.sql ← 按钮权限（幂等）
--   6) 05_...demo_data.sql / 06_...  ← 仅演示环境需要
--
-- 备份命令示例（在能访问该库的机器上执行）：
--   mysqldump -h<host> -uroot -p --single-transaction --default-character-set=utf8mb4 \
--     tj-jyxyd t_escalation_project t_escalation_material t_escalation_record \
--     > escalation_backup_YYYYMMDD.sql
-- =============================================================================

SET NAMES utf8mb4;

-- 先报告将被删除的数据量（执行前留个记录）
SELECT '即将删除的数据量' AS 检查项,
       (SELECT COUNT(*) FROM `t_escalation_project`)  AS 项目数,
       (SELECT COUNT(*) FROM `t_escalation_material`) AS 材料数,
       (SELECT COUNT(*) FROM `t_escalation_record`)   AS 意见记录数;

-- ★ 删除（按依赖顺序：先子表后主表）
DROP TABLE IF EXISTS `t_escalation_record`;
DROP TABLE IF EXISTS `t_escalation_material`;
DROP TABLE IF EXISTS `t_escalation_project`;

SELECT '已删除的本模块表（应为空）' AS 检查项, TABLE_NAME AS 残留表
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 't\_escalation%';
