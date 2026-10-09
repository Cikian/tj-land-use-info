-- ============================================================================
--  非工作日（节假日）日历表 t_non_working_day
--  依据：docs/升级改造工作内容清单.md 5.3.2「29 环节进度录入」
--        旧实现用「预计结束时间 = 开始日 + 标准办理时长(工作日)」推算，
--        需要一张节假日日历；旧表为 nutzwk_ywk.non_working_day（116 行）。
--
--  ★ 为什么这张表必须搬进来：
--    「预计结束时间」与「逾期预警」都建立在「工作日」这个概念上。
--    如果把节假日日历留在旧库，新系统要么跨库读（设计文档 6.3.9 明确禁止），
--    要么退化成「自然日 + N」—— 后者会把春节七天、国庆七天全算成工作日，
--    于是每年 2 月和 10 月的环节预计结束时间系统性地偏早，
--    预警会提前一片，中心看到的「逾期环节」里混着一堆其实还没到期的。
--
--  ★ 实测旧表结构与数据：
--    id / nonWoringTime(date) / nonWoringTimeString / nonWoringTimeChina /
--    weekEnd(1=周末 2=非周末) / weekEndChina
--    116 行全是 **2023 年** —— 表名里的 "Woring" 是旧系统的拼写错误（Woring → Working），
--    列名沿用旧名以保证迁移脚本可逐列对照，另加语义化注释说明。
--
--  ★★ 数据时效性（重要，不要以为搬完就完事）：
--    这 116 行只覆盖 2023 年。2024~2027 年的节假日需要中心按年维护 ——
--    好在「周末」是可以算出来的（每周六日），只有「法定节假日调休」必须人工给。
--    因此服务层的算法是：
--      ① 先按「周六日 + 法定节假日 − 调休上班日」判断；
--      ② 表里查到的日期一律视为非工作日（人工维护的权威数据）；
--      ③ 表里没有任何数据时退化为「只剔周六日」，并在返回结果里标注
--         calendarBased=true，让前端提示用户「未使用节假日日历」。
--    这样即使中心忘了维护，也不会算出明显离谱的结果。
--
--  可重复执行：建表用 IF NOT EXISTS，数据用 INSERT ... ON DUPLICATE KEY UPDATE。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_non_working_day` (
  `id`                   varchar(64)   NOT NULL                COMMENT '主键（沿用旧库 non_working_day.id）',
  `non_working_time`     date              NULL                COMMENT '非工作日日期（旧列名 nonWoringTime）',
  `non_working_string`   varchar(20)       NULL                COMMENT '日期字符串 yyyyMMdd（旧列名 nonWoringTimeString）',
  `non_working_china`    varchar(50)       NULL                COMMENT '日期中文 yyyy年MM月dd日（旧列名 nonWoringTimeChina）',
  `week_end`             varchar(10)       NULL                COMMENT '分类：1 周末 / 2 非周末（旧列名 weekEnd）',
  `week_end_china`       varchar(50)       NULL                COMMENT '分类中文：周末 / 非周末（旧列名 weekEndChina）',
  `create_by`            varchar(64)       NULL                COMMENT '创建人',
  `create_time`          datetime          NULL                COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_nwd_date` (`non_working_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非工作日（节假日）日历：用于环节预计结束时间与逾期预警';

-- ---------------------------------------------------------------------------
-- 数据迁移：nutzwk_ywk.non_working_day → t_non_working_day（116 行，2023 年）
-- 前置条件：执行账号需对 nutzwk_ywk 有 SELECT 权限（本机 root 具备）。
-- ---------------------------------------------------------------------------
INSERT INTO `t_non_working_day`
  (`id`, `non_working_time`, `non_working_string`, `non_working_china`,
   `week_end`, `week_end_china`, `create_by`, `create_time`)
SELECT
  s.`id`, s.`nonWoringTime`, s.`nonWoringTimeString`, s.`nonWoringTimeChina`,
  s.`weekEnd`, s.`weekEndChina`, 'admin', NOW()
FROM `nutzwk_ywk`.`non_working_day` s
ON DUPLICATE KEY UPDATE
  `non_working_time`   = VALUES(`non_working_time`),
  `non_working_string` = VALUES(`non_working_string`),
  `non_working_china`  = VALUES(`non_working_china`),
  `week_end`           = VALUES(`week_end`),
  `week_end_china`     = VALUES(`week_end_china`);

-- 核对：应输出 116 行，且年份分布只有 2023
SELECT COUNT(*) AS 总行数,
       MIN(`non_working_time`) AS 最早,
       MAX(`non_working_time`) AS 最晚,
       COUNT(DISTINCT YEAR(`non_working_time`)) AS 覆盖年数
FROM `t_non_working_day`;

SELECT YEAR(`non_working_time`) AS 年份, COUNT(*) AS 天数,
       SUM(CASE WHEN `week_end` = '1' THEN 1 ELSE 0 END) AS 周末,
       SUM(CASE WHEN `week_end` = '2' THEN 1 ELSE 0 END) AS 法定节假日
FROM `t_non_working_day` GROUP BY YEAR(`non_working_time`) ORDER BY 年份;

-- 提示：以下查询列出「表里没有数据的年份」，
-- 这些年份的工作日计算会退化为「只剔周六日」，中心应按年补维护。
SELECT '★ 以下年份无非工作日数据，工作日推算将退化为「只剔周六日」' AS 提示;
SELECT y.`年份`
FROM (SELECT 2024 AS `年份` UNION SELECT 2025 UNION SELECT 2026 UNION SELECT 2027) y
LEFT JOIN (SELECT DISTINCT YEAR(`non_working_time`) AS `年份` FROM `t_non_working_day`) d
       ON d.`年份` = y.`年份`
WHERE d.`年份` IS NULL
ORDER BY y.`年份`;
