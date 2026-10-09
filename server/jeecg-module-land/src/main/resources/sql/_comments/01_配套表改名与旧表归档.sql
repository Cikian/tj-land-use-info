-- ============================================================================
--  配套项目表改名：xj_kjkfb_supporting_facilities → t_supporting_facilities
--  目标库：tj-jyxyd
--
--  ★ 背景
--    库内曾同时存在配套主表的**两个副本**：
--        xj_kjkfb_supporting_facilities   旧库表名，代码 93 处引用都指向它
--        t_supporting_facilities          新命名，但代码从不读写
--    这是「写错表、数据静默丢失」的雷：写进副本的记录程序永远读不到。
--    本脚本把二者收敛成一张：**t_supporting_facilities 为唯一定口**，
--    旧表冻结为历史档案（数据保留、不再读写）。
--
--  ★ 为什么方向是「旧 → 新」而不是「新 → 旧」
--    项目的新表统一用 t_ 前缀（t_land / t_archive / t_doc_* / …）。
--    把配套表也收进同一命名体系，才不会在新代码里继续出现 xj_ 前缀的旧库表名。
--    （列名仍是旧系统的写法 —— 见下「遗留」一节，不在本次范围内改。）
--
--  ★ 结构一致性（执行前已核对，见核对 1）
--    两表 62 列，列名/类型/可空/默认值/排序规则/EXTRA **逐列完全相同**，
--    索引也相同（都只有 PRIMARY(id)），引擎与排序规则相同。
--    因此本脚本的「同步」在结构上无操作，只做数据对齐。
--
--  ★ 遗留（明确不在本次范围）
--    列名沿用了旧系统的驼峰/拼音写法（delFlag / createTime / createAccount、
--    sfzsjtjlz、ptxmhdydydjdc …）。改成下划线语义名要动实体、Mapper、
--    导入模板、以及中心手里在用的 Excel 表头，收益与风险不成比例，故保持原样。
--    这些列名已在各实体/脚本注释里标注为「沿用旧系统配套表」。
--
--  ★ 本脚本可重复执行（幂等）。
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 一、把旧表数据全量同步到新表
--
--  ★ 两表列顺序已核对一致（GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION)
--    逐字相同），所以 SELECT * 不会错位。
--  ★ 用 REPLACE 而不是 INSERT：主键冲突时以**旧表**的版本为准，
--    保证「以旧表为准」这个语义，且可重复执行。
-- ---------------------------------------------------------------------------
REPLACE INTO `t_supporting_facilities`
  SELECT * FROM `xj_kjkfb_supporting_facilities`;

-- ---------------------------------------------------------------------------
-- 二、表备注改为最终状态
--
--  新表 = 唯一定口；旧表 = 已冻结的历史档案。
--  ★ 写清楚「请勿写入」是刻意的：这张重复表造成的静默数据丢失，
--    靠的就是没人知道该写哪张。
-- ---------------------------------------------------------------------------
ALTER TABLE `t_supporting_facilities`
  COMMENT = '配套项目主表：1 宗地 N 配套。★ 新系统唯一定口，业务代码全部使用本表。列结构沿用旧系统配套表，故审计列是驼峰 delFlag/createTime/createAccount（非下划线），且无唯一键；删除状态为 varchar ''0''/''1''';

ALTER TABLE `xj_kjkfb_supporting_facilities`
  COMMENT = '【历史档案 · 已冻结】配套项目主表（旧库表名）。2026-10-09 起全部业务代码改用 t_supporting_facilities，本表仅保留历史数据供追溯，不再被任何代码读写。★ 请勿写入本表';

-- ---------------------------------------------------------------------------
-- 三、核对
-- ---------------------------------------------------------------------------

-- 核对 1：应输出 0 行 —— 两表列定义完全一致
SELECT * FROM (
  SELECT COALESCE(a.COLUMN_NAME, b.COLUMN_NAME) AS 列,
         CASE WHEN a.COLUMN_NAME IS NULL OR b.COLUMN_NAME IS NULL THEN '★仅单边存在'
              WHEN a.COLUMN_TYPE <> b.COLUMN_TYPE OR a.IS_NULLABLE <> b.IS_NULLABLE
                OR NOT (a.COLUMN_DEFAULT <=> b.COLUMN_DEFAULT)
                OR NOT (a.COLLATION_NAME <=> b.COLLATION_NAME)
                OR NOT (a.EXTRA <=> b.EXTRA) THEN '★定义不同'
              ELSE '相同' END AS 判定
  FROM (SELECT * FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_supporting_facilities') a
  LEFT JOIN (SELECT * FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'xj_kjkfb_supporting_facilities') b
    ON a.COLUMN_NAME = b.COLUMN_NAME
) d WHERE d.判定 <> '相同';

-- 核对 2：应输出「逐行一致数 = 总行数，且两个差集均为 0」
SELECT (SELECT COUNT(*) FROM t_supporting_facilities) AS 新表行数,
       (SELECT COUNT(*) FROM xj_kjkfb_supporting_facilities) AS 旧表行数,
       (SELECT COUNT(*) FROM t_supporting_facilities a
          JOIN xj_kjkfb_supporting_facilities b ON a.id = b.id
         WHERE a.crzdbh <=> b.crzdbh AND a.ptxmmc <=> b.ptxmmc
           AND a.xzqh <=> b.xzqh AND a.delFlag <=> b.delFlag
           AND a.createTime <=> b.createTime) AS 逐行一致数,
       (SELECT COUNT(*) FROM t_supporting_facilities a
         LEFT JOIN xj_kjkfb_supporting_facilities b ON a.id = b.id WHERE b.id IS NULL) AS 仅新表有,
       (SELECT COUNT(*) FROM xj_kjkfb_supporting_facilities b
         LEFT JOIN t_supporting_facilities a ON a.id = b.id WHERE a.id IS NULL) AS 仅旧表有;

-- 核对 3：两表最终备注
SELECT TABLE_NAME AS 表名, TABLE_COMMENT AS 表备注
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('t_supporting_facilities','xj_kjkfb_supporting_facilities')
ORDER BY TABLE_NAME;
