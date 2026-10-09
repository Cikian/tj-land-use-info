-- ============================================================================
--  项目业务表 · 补齐缺失的表/列备注
--  目标库：tj-jyxyd（腾讯-北京 49.232.252.56）
--
--  ★ 背景
--    全库 131 张表里，只有 27 张是本项目业务表（t_* / xj_*）。
--    核对时发现其中 5 张缺**表备注**、3 个列缺**列备注**（其余列备注早已齐全）。
--    本脚本就是把这 8 处补齐。
--
--  ★ 原则：只补空的，不改已有的
--    已有的表/列备注（如「档案主表」「出让宗地编号」）保留原样，
--    因此本脚本用 COMMENT = '...' 直接覆盖 —— 只针对**当前为空**的那几处，
--    不对已有备注做任何「统一改写」。
--
--  ★ 可重复执行：ALTER ... COMMENT 是幂等的（同样的值再设一次不会有副作用）。
--
--  ★★ 核对中最重要的一个发现（已写进表备注）
--    t_supporting_facilities 与 t_supporting_facilities **是同一份数据的两个副本**：
--      · 均 1433 行，id 完全重合，关键字段逐行一致；
--      · 但代码 93 处引用**全部**指向 t_supporting_facilities
--        （Facility 实体 @TableName、FacilityMapper.xml、DataRecycleMapper、
--         台账/移交/竣工档案/提级论证的 facility_id 注释……）；
--      · t_supporting_facilities 在代码与配置（online 表单、积木报表）中**零引用**。
--    这是一颗「写错表、数据静默丢失」的雷 —— 写进 t_supporting_facilities 的记录
--    程序永远读不到。所以在表备注里明确标注了「未使用的重复副本，请勿写入」。
--    建议后续确认后删除该副本（本脚本不做删除，避免超出「加备注」的范围）。
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 一、5 张缺表备注的表
-- ---------------------------------------------------------------------------

-- ① 配套项目主表：新系统实际在用的那张
ALTER TABLE `t_supporting_facilities`
  COMMENT = '配套项目主表（旧库同名表，新系统直接复用）：1 宗地 N 配套；1433 行于 2026-09 自旧库迁入。★ 唯一在用的一张配套表，Facility 实体与 93 处代码全部指向它；删除状态列是驼峰 delFlag（varchar 0/1），与 t_land.del_flag（下划线 tinyint）不同';

-- ② 同结构重复表：零引用，标注为「勿写入」
ALTER TABLE `t_supporting_facilities`
  COMMENT = '配套项目主表 · 未使用的重复副本：与 t_supporting_facilities 同结构同数据（1433 行、id 完全重合），但代码与配置中零引用。★ 请勿写入本表——写进来的数据不会被程序读到；新系统一律用 t_supporting_facilities';

-- ③④⑤ 旧系统收发文三表（已迁入新结构）
ALTER TABLE `xj_filemanage_send`
  COMMENT = '旧系统发文表（收发文管理）：已由 sql/document/03_migrate_legacy_filemanage.sql 迁入 t_doc_send，本表仅作历史留档';

ALTER TABLE `xj_filemanage_receive`
  COMMENT = '旧系统收文表（收发文管理）：已由 sql/document/03_migrate_legacy_filemanage.sql 迁入 t_doc_receive，本表仅作历史留档';

ALTER TABLE `xj_filemanage_flow`
  COMMENT = '旧系统收发文流转记录表：已迁入 t_doc_receive_flow；sr_id 关联收发文主表（被收/发文共用，故未建外键）';

-- ---------------------------------------------------------------------------
-- 二、3 个缺列备注的列
--
--  ★ 用 MODIFY 而不是「ALTER TABLE ... CHANGE」：只改备注，不改列定义。
--    列定义（varchar(36) NOT NULL）与两表其它 id 列保持一致，注释文字沿用
--    它们既有的「编号ID」写法，避免同一份模型里出现两种措辞。
--  ★ MODIFY 会重建该列，务必保持 NOT NULL 与类型不变 —— 漏写 NOT NULL 会把主键列
--    变成可空（MySQL 会连带影响主键约束），这一点在脚本里显式写全。
-- ---------------------------------------------------------------------------
ALTER TABLE `xj_filemanage_send`    MODIFY COLUMN `id` varchar(36) NOT NULL COMMENT '编号ID';
ALTER TABLE `xj_filemanage_receive` MODIFY COLUMN `id` varchar(36) NOT NULL COMMENT '编号ID';
ALTER TABLE `xj_filemanage_flow`    MODIFY COLUMN `id` varchar(36) NOT NULL COMMENT '编号ID';

-- ---------------------------------------------------------------------------
-- 三、核对
-- ---------------------------------------------------------------------------

-- 核对 1：应输出 0 行 —— 项目业务表里已无「表备注为空」或「存在空列备注」的表
SELECT t.TABLE_NAME AS 仍缺备注的表
FROM information_schema.TABLES t
WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_TYPE = 'BASE TABLE'
  AND (t.TABLE_NAME LIKE 't\_%' OR t.TABLE_NAME LIKE 'xj\_%')
  AND (
        t.TABLE_COMMENT = '' OR t.TABLE_COMMENT IS NULL
     OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME
                   AND (c.COLUMN_COMMENT = '' OR c.COLUMN_COMMENT IS NULL))
      )
ORDER BY t.TABLE_NAME;

-- 核对 2：项目业务表的备注覆盖率（27 / 27）
SELECT COUNT(*) AS 业务表数,
       SUM(CASE WHEN TABLE_COMMENT <> '' THEN 1 ELSE 0 END) AS 表有备注,
       SUM(CASE WHEN TABLE_COMMENT = '' OR TABLE_COMMENT IS NULL THEN 1 ELSE 0 END) AS 表无备注
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
  AND (TABLE_NAME LIKE 't\_%' OR TABLE_NAME LIKE 'xj\_%');

-- 核对 3：本次补的 3 个列备注，且主键仍在、列仍为 NOT NULL
SELECT c.TABLE_NAME AS 表名, c.COLUMN_NAME AS 列, c.COLUMN_TYPE AS 类型,
       c.IS_NULLABLE AS 可空, c.COLUMN_COMMENT AS 备注
FROM information_schema.COLUMNS c
WHERE c.TABLE_SCHEMA = DATABASE() AND c.COLUMN_NAME = 'id'
  AND c.TABLE_NAME IN ('xj_filemanage_send','xj_filemanage_receive','xj_filemanage_flow')
ORDER BY c.TABLE_NAME;

SELECT TABLE_NAME AS 表名, INDEX_NAME AS 索引, COLUMN_NAME AS 列
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND INDEX_NAME = 'PRIMARY'
  AND TABLE_NAME IN ('xj_filemanage_send','xj_filemanage_receive','xj_filemanage_flow')
ORDER BY TABLE_NAME;
