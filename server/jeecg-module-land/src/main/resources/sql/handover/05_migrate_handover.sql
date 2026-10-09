-- =============================================================================
-- 道路交付及养护协议移交事项 · 旧数据迁移脚本
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版 / MySQL 5.7）
--
-- ★★ 迁移范围：旧库「是否移交 = 是」的**道路类**项目（实测 84 条）
--
--   旧系统没有移交事项表，但有唯一一个能证明「已经移交了」的标志位：
--     t_supporting_facilities.sfyj = '是'
--   实测（线上库 tj-jyxyd）：
--     道路类（ptsslb ∈ 道路/市政道路/道路及管线）共 1340 条，其中 sfyj='是' → **84 条**
--     非道路类另有 sfyj='是' 2 条（排水），**不纳入本模块**（那是排水工程移交，方案未列）
--
--   那 710 条 sfyj='否' 为什么不预生成「待移交」记录？见 01 脚本的说明：
--   「哪些路还没移交」用台账页筛 `status <> 已移交` 就能看到，
--   两处各维护一份清单只会互相矛盾。
--
-- ★ 字段映射表（只迁「有确凿依据」的字段，其余留空待补录）
--   | 本表列              | 来源                                            | 实测 |
--   |---------------------|-------------------------------------------------|------|
--   | source_facility_id  | s.id（★ 迁移幂等键）                             | 84   |
--   | road_name / ptxmmc  | s.ptxmmc                                        | 84   |
--   | dldj                | s.dldj（道路等级）                              | 按源 |
--   | length_m            | s.cd（长度，米）                                | 按源 |
--   | red_line_width      | s.ghhxkd（规划红线宽度，米）                     | 按源 |
--   | xzqh / gnq          | s.xzqh 拆分（16 区 / 功能区）                    | 按源 |
--   | crzdbh              | s.crzdbh                                        | 84   |
--   | land_id / dkmc      | 按 crzdbh 反查 t_land                            | 按源 |
--   | ptsslb              | s.ptsslb                                        | 84   |
--   | handover_type       | 固定 '正式移交'（因为 sfyj='是'）                | 84   |
--   | build_unit          | s.jsdw（建设单位）                              | 按源 |
--   | receive_unit        | s.jsgydw（接收管养单位）                        | 按源 |
--   | status              | 固定 '已移交'                                    | 84   |
--   | ★ agreement_no/name/agreement_date/maintenance_* | 旧库**无**对应字段 → 留空待补录 | 0 |
--   | ★ handover_date     | 旧库**无**移交日期字段 → 留空待补录               | 0 |
--   | start_point/end_point | 旧库无对应字段 → 留空                           | 0 |
--
-- ★ 编号规则：handover_no = 'YJ-{年度}-{4位流水}'
--   年度取 YEAR(COALESCE(sjjgsj, createTime, NOW()))（实际竣工年度），
--   同年度内按 (COALESCE(sjjgsj,createTime,NOW()), id) 升序编号 —— 用相关子查询计数实现，
--   **不用 MySQL 用户变量**（其赋值顺序在 ORDER BY 下无保证，而本脚本要长期可复跑、结果必须一致）。
--   84 条数据规模下 4 位流水绰绰有余。
--
-- ★ 幂等：主键 = MD5('rh:' + s.id)，source_facility_id 建唯一键，用 INSERT IGNORE ——
--   只补缺失的行，**已存在的行（含人工补录过协议信息的）一律不动**。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0) 前置检查（只报告，不写库）
-- ---------------------------------------------------------------------------
SELECT '依赖检查' AS 检查项,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_supporting_facilities') AS 源表_配套项目,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_handover') AS 目标表_移交事项,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_land') AS 依赖表_宗地;

SELECT '迁移前基线' AS 检查项,
       (SELECT COUNT(*) FROM `t_supporting_facilities`
         WHERE `ptsslb` IN ('道路', '市政道路', '道路及管线')
           AND `sfyj` = '是' AND `ptxmmc` IS NOT NULL AND `ptxmmc` <> ''
           AND (`delFlag` IS NULL OR `delFlag` = '0')) AS 底账条数,
       (SELECT COUNT(*) FROM `t_road_handover`) AS 现有条数;

-- ---------------------------------------------------------------------------
-- 1) 迁移
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `t_road_handover` (
  `id`, `handover_no`, `road_name`, `road_code`, `dldj`, `start_point`, `end_point`,
  `length_m`, `red_line_width`, `xzqh`, `gnq`, `ptsslb`,
  `crzdbh`, `land_id`, `dkmc`, `facility_id`, `ptxmmc`,
  `handover_type`, `agreement_no`, `agreement_name`, `agreement_date`,
  `build_unit`, `receive_unit`, `handover_date`, `maintenance_start`, `maintenance_end`,
  `status`, `archive_id`, `archive_count`, `source_facility_id`,
  `remark`, `del_flag`, `create_by`, `create_time`
)
SELECT
  MD5(CONCAT('rh:', `src`.`id`))                                     AS `id`,
  CONCAT('YJ-', `src`.`y`, '-', LPAD(`src`.`seq`, 4, '0'))            AS `handover_no`,
  `src`.`ptxmmc`                                                     AS `road_name`,
  NULL                                                               AS `road_code`,
  `src`.`dldj`                                                       AS `dldj`,
  NULL                                                               AS `start_point`,
  NULL                                                               AS `end_point`,
  `src`.`cd`                                                         AS `length_m`,
  `src`.`ghhxkd`                                                     AS `red_line_width`,
  CASE WHEN `src`.`xzqh` IN ('生态城', '土地发展中心', '经开区', '高新区', '保税区')
       THEN NULL ELSE `src`.`xzqh` END                               AS `xzqh`,
  CASE WHEN `src`.`xzqh` IN ('生态城', '土地发展中心', '经开区', '高新区', '保税区')
       THEN `src`.`xzqh` ELSE NULL END                               AS `gnq`,
  `src`.`ptsslb`                                                     AS `ptsslb`,
  `src`.`crzdbh`                                                     AS `crzdbh`,
  (SELECT `l`.`id`   FROM `t_land` `l` WHERE `l`.`crzdbh` = `src`.`crzdbh` AND `l`.`del_flag` = 0 LIMIT 1) AS `land_id`,
  (SELECT `l`.`dkmc` FROM `t_land` `l` WHERE `l`.`crzdbh` = `src`.`crzdbh` AND `l`.`del_flag` = 0 LIMIT 1) AS `dkmc`,
  `src`.`id`                                                         AS `facility_id`,
  `src`.`ptxmmc`                                                     AS `ptxmmc`,
  '正式移交'                                                          AS `handover_type`,
  NULL, NULL, NULL,                                                      -- 协议编号/名称/日期：旧库无
  `src`.`jsdw`                                                       AS `build_unit`,
  NULLIF(TRIM(COALESCE(`src`.`jsgydw`, '')), '')                     AS `receive_unit`,
  NULL, NULL, NULL,                                                      -- 移交日期与养护期：旧库无
  '已移交'                                                            AS `status`,
  NULL                                                               AS `archive_id`,
  0                                                                  AS `archive_count`,
  `src`.`id`                                                         AS `source_facility_id`,
  CONCAT('迁移自旧库配套项目表「是否移交=是」；协议编号、协议日期、实际移交日期与养护期'
         '旧库无对应字段，待中心按实际协议补录',
         CASE WHEN (SELECT `l`.`id` FROM `t_land` `l`
                     WHERE `l`.`crzdbh` = `src`.`crzdbh` AND `l`.`del_flag` = 0 LIMIT 1) IS NULL
              THEN CONCAT('；另：出让宗地编号「', COALESCE(`src`.`crzdbh`, '（空）'),
                          '」在 t_land 中无匹配记录，地块名称待人工核对')
              ELSE '' END)                                             AS `remark`,
  0                                                                  AS `del_flag`,
  'migration'                                                        AS `create_by`,
  NOW()                                                              AS `create_time`
FROM (
  SELECT
    `s`.*,
    YEAR(COALESCE(`s`.`sjjgsj`, `s`.`createTime`, NOW()))            AS `y`,
    -- 同年度内流水：(竣工时间, id) 升序计数。确定性、可复跑，不依赖用户变量
    (SELECT COUNT(*)
       FROM `t_supporting_facilities` `b`
      WHERE `b`.`ptsslb` IN ('道路', '市政道路', '道路及管线')
        AND `b`.`sfyj` = '是' AND `b`.`ptxmmc` IS NOT NULL AND `b`.`ptxmmc` <> ''
        AND (`b`.`delFlag` IS NULL OR `b`.`delFlag` = '0')
        AND YEAR(COALESCE(`b`.`sjjgsj`, `b`.`createTime`, NOW()))
            = YEAR(COALESCE(`s`.`sjjgsj`, `s`.`createTime`, NOW()))
        AND (COALESCE(`b`.`sjjgsj`, `b`.`createTime`, NOW()), `b`.`id`)
         <= (COALESCE(`s`.`sjjgsj`, `s`.`createTime`, NOW()), `s`.`id`)
    )                                                                AS `seq`
  FROM `t_supporting_facilities` `s`
  WHERE `s`.`ptsslb` IN ('道路', '市政道路', '道路及管线')
    AND `s`.`sfyj` = '是' AND `s`.`ptxmmc` IS NOT NULL AND `s`.`ptxmmc` <> ''
    AND (`s`.`delFlag` IS NULL OR `s`.`delFlag` = '0')
) `src`;

-- ---------------------------------------------------------------------------
-- 2) 后置校验
--    2.1 迁移结果总览（应：总数 84、由迁移生成 84、人工新增 0）
-- ---------------------------------------------------------------------------
SELECT '迁移结果' AS 项,
       COUNT(*) AS 移交事项总数,
       SUM(CASE WHEN `source_facility_id` IS NOT NULL THEN 1 ELSE 0 END) AS 由迁移生成,
       SUM(CASE WHEN `source_facility_id` IS NULL THEN 1 ELSE 0 END)     AS 人工新增,
       SUM(CASE WHEN `remark` LIKE '%无匹配记录%' THEN 1 ELSE 0 END)      AS 宗地未匹配,
       SUM(CASE WHEN `receive_unit` IS NOT NULL THEN 1 ELSE 0 END)        AS 有接收管养单位,
       SUM(CASE WHEN `length_m` IS NOT NULL THEN 1 ELSE 0 END)            AS 有长度,
       SUM(CASE WHEN `red_line_width` IS NOT NULL THEN 1 ELSE 0 END)      AS 有红线宽度
FROM `t_road_handover`;

--    2.2 对账：底账条数 与 已迁移条数 必须相等
SELECT (SELECT COUNT(*) FROM `t_supporting_facilities`
         WHERE `ptsslb` IN ('道路', '市政道路', '道路及管线')
           AND `sfyj` = '是' AND `ptxmmc` IS NOT NULL AND `ptxmmc` <> ''
           AND (`delFlag` IS NULL OR `delFlag` = '0'))                AS 底账条数,
       (SELECT COUNT(*) FROM `t_road_handover`
         WHERE `source_facility_id` IS NOT NULL)                      AS 已迁移条数,
       (SELECT COUNT(*) FROM `t_road_handover` WHERE `handover_no` LIKE 'YJ-%') AS 移交编号条数;

--    2.3 编号重复检查：应输出 0 行
SELECT `handover_no`, COUNT(*) AS 重复次数
FROM `t_road_handover` GROUP BY `handover_no` HAVING COUNT(*) > 1;

--    2.4 状态与类型分布（迁移后应全部是「已移交 / 正式移交」）
SELECT `status` AS 状态, `handover_type` AS 移交类型, COUNT(*) AS 条数
FROM `t_road_handover` GROUP BY `status`, `handover_type` ORDER BY 条数 DESC;

--    2.5 年度分布（校验 4 位流水是否够用）
SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(`handover_no`, '-', 2), '-', -1) AS 年度,
       COUNT(*) AS 条数, MIN(`handover_no`) AS 最小号, MAX(`handover_no`) AS 最大号
FROM `t_road_handover` WHERE `handover_no` LIKE 'YJ-%'
GROUP BY SUBSTRING_INDEX(SUBSTRING_INDEX(`handover_no`, '-', 2), '-', -1) ORDER BY 年度;

--    2.6 抽样（人工可读，核对映射是否正确）
SELECT `handover_no` AS 移交编号, `road_name` AS 道路名称, `dldj` AS 道路等级,
       `xzqh` AS 行政区, `gnq` AS 功能区, `crzdbh` AS 宗地编号, `dkmc` AS 地块名称,
       `build_unit` AS 建设单位, `receive_unit` AS 接收管养单位,
       `length_m` AS 长度, `red_line_width` AS 红线宽度, `status` AS 状态
FROM `t_road_handover`
ORDER BY `handover_no` LIMIT 8;
