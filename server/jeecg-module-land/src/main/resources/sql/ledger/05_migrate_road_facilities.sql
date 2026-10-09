-- =============================================================================
-- 道路设施验收及移交资料台账 · 旧数据迁移脚本（★ 本模块唯一「迁移」性质的脚本）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版 / MySQL 5.7）
--
-- ★★ 迁移背景（务必先读 01 脚本的表头：旧系统没有这个功能）
--   旧系统没有台账表可搬，所以「迁移」在这里的含义是：
--   以旧配套项目表 xj_kjkfb_supporting_facilities 里的**道路类项目为底账**，
--   为每条道路生成一条台账初始记录（需求方 2026-09 确认：道路类全部生成）。
--
--   底账范围（实测 1340 条，线上库与本地库数字一致）：
--     ptsslb IN ('道路' 1332, '市政道路' 4, '道路及管线' 4)
--     delFlag 全部为 '0'（1340/1340），ptxmmc 无空值，
--     但 ptxmmc 只有 1028 个不同值（重名 312 条）→ ★ 必须按 id 建账，不能用 ptxmmc 做键。
--
-- ★ 字段映射表（只迁「有确凿依据」的字段，其余一律留空由中心补录）
--   | 台账列             | 来源                                        | 实测条数 |
--   |--------------------|---------------------------------------------|----------|
--   | source_facility_id | s.id                                        | 1340     |
--   | road_name/ptxmmc   | s.ptxmmc                                    | 1340     |
--   | ptsslb             | s.ptsslb                                    | 1340     |
--   | crzdbh             | s.crzdbh                                    | 1340     |
--   | land_id / dkmc     | 按 crzdbh 反查 t_land（del_flag=0）          | 1287     |
--   | xzqh / gnq         | s.xzqh 拆分：16 区进 xzqh，功能区进 gnq       | 192 进 gnq |
--   | dldj/jsdw/sgdw/jldw| s.dldj / s.jsdw / s.sgdw / s.jldw           | 按源值    |
--   | complete_date      | s.sjjgsj（实际竣工日期）                     | 292      |
--   | receive_unit       | s.jsgydw（接收管养单位）                     | 410      |
--   | handover_unit      | s.jsdw，仅当 sfyj='是' 时写入（否则 NULL）    | 84       |
--   | has_sgxk           | s.sgxk='是'（旧配套附件 11-施工许可）         | 408      |
--   | has_ysbg           | s.sfjg='是'（已竣工 ⇒ 必有竣工验收报告，推断）| 409      |
--   | has_jgtc           | s.sjjgsj 非空（有实际竣工日期 ⇒ 有竣工图测）  | 292      |
--   | has_jgwj           | s.jgwj='是'（旧附件目录 13-竣工文件）         | 7        |
--   | has_yjwj           | s.yjwj='是'（旧附件目录 14-移交文件）         | 1        |
--   | 其余 8 类资料       | ★ 源库无对应标志，一律 0（待中心补录）        | 0        |
--   | status             | 见下方 CASE（已移交 84 / 已验收 / 验收中 / 未验收） | —  |
--
--   ★ 推断类字段（has_ysbg / has_jgtc）在台账 remark 里留了说明吗？——没有。
--     理由是：它们由「是否竣工」「实际竣工日期」直接派生，来源可追溯（source_facility_id
--     可随时回查旧表），而 remark 留给真正需要人工注意的行（宗地匹配不上的孤儿）。
--     若中心认为这两类不该自动勾选，执行 05 的下方「回退推断」语句即可（脚本末尾已给出）。
--
-- ★ 编号规则：ledger_no = 'YS-{年度}-{4位流水}'
--   年度取 YEAR(COALESCE(sjjgsj, createTime, NOW()))，同年度内按
--   (COALESCE(sjjgsj,createTime,NOW()), id) 升序编号——用相关子查询计数实现，
--   不用用户变量：MySQL 5.7 的 @var 赋值顺序在 ORDER BY 下无保证，
--   而本脚本要长期可复跑、结果必须逐次一致（1340 行 × 1340 行的比较量可接受）。
--   实测年度分布：2025 年 747 条、2026 年 301 条、其余年份 2~54 条，
--   最大流水号 747 << 9999，4 位足够。
--
-- ★ 幂等：主键 = MD5('ral:' + s.id)，source_facility_id 建唯一键，
--   用 INSERT IGNORE —— 只补缺失的行，**已存在的行（含人工改过的）一律不动**。
--   因此本脚本可以反复执行，不会覆盖业务人员在页面上的补录结果。
--
-- ★ 未迁移的东西（如实记录）：
--   1) 资料原件。旧附件按目录存盘（{AttachmentPath}\市政配套建设及进展情况信息附件\
--      {配套项目名称}\13-竣工文件|14-移交文件\），而旧附件根目录
--      D:\基础设施配套动态监管工作站 在本次执行环境**不存在**，无法随库迁移；
--      资料原件按需求方口径统一挂档案管理 t_archive/t_archive_file，由人工新建/上传。
--   2) 验收信息（验收类型/验收单编号/验收日期/验收组织单位/验收结果）。
--      旧库 nutzwk_ywk.process_status 里「竣工验收许可阶段 / 竣工移交许可阶段」实测 0 条，
--      没有进度数据可迁，一律留空。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0) 前置检查（只报告，不写库）
--    0.1 源表与目标表是否都在
-- ---------------------------------------------------------------------------
SELECT '依赖检查' AS 检查项,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'xj_kjkfb_supporting_facilities') AS 源表_配套项目,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_acceptance_ledger') AS 目标表_台账,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_land') AS 依赖表_宗地;

--    0.2 底账条数（应为 1340）与迁移前台账条数（首次执行应为 0）
SELECT '迁移前基线' AS 检查项,
       (SELECT COUNT(*) FROM `xj_kjkfb_supporting_facilities`
         WHERE `ptsslb` IN ('道路', '市政道路', '道路及管线')
           AND `ptxmmc` IS NOT NULL AND `ptxmmc` <> ''
           AND (`delFlag` IS NULL OR `delFlag` = '0')) AS 底账条数,
       (SELECT COUNT(*) FROM `t_road_acceptance_ledger`) AS 台账现有条数;

-- ---------------------------------------------------------------------------
-- 1) 迁移：道路类配套项目 → 台账初始记录
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `t_road_acceptance_ledger` (
  `id`, `ledger_no`, `road_name`, `xzqh`, `gnq`, `crzdbh`, `land_id`, `dkmc`, `ptsslb`,
  `facility_id`, `ptxmmc`, `dldj`, `jsdw`, `sgdw`, `jldw`, `complete_date`,
  `has_sgxk`, `has_ysbg`, `has_jgtc`, `has_zljdbg`, `has_cljybg`, `has_ajbg`,
  `has_ghyshgz`, `has_jgbaba`, `has_dazxys`, `has_dlyjd`, `has_yhxy`, `has_jgwj`, `has_yjwj`,
  `handover_unit`, `receive_unit`, `status`, `source_facility_id`, `material_count`,
  `remark`, `del_flag`, `create_by`, `create_time`
)
SELECT
  -- ★ 确定性主键：MD5 32 位（≤ varchar(36)），同一配套项目永远得到同一 id → 幂等
  MD5(CONCAT('ral:', `src`.`id`))                                   AS `id`,
  CONCAT('YS-', `src`.`y`, '-', LPAD(`src`.`seq`, 4, '0'))          AS `ledger_no`,
  `src`.`ptxmmc`                                                    AS `road_name`,
  -- ★ 行政区划拆分：功能区值不进 xzqh（清单 3.0 第 4 条）
  CASE WHEN `src`.`xzqh` IN ('生态城', '土地发展中心', '经开区', '高新区', '保税区')
       THEN NULL ELSE `src`.`xzqh` END                              AS `xzqh`,
  CASE WHEN `src`.`xzqh` IN ('生态城', '土地发展中心', '经开区', '高新区', '保税区')
       THEN `src`.`xzqh` ELSE NULL END                              AS `gnq`,
  `src`.`crzdbh`                                                    AS `crzdbh`,
  -- 用标量子查询而不是 LEFT JOIN：即便 t_land.crzdbh 出现重复也不会把底账放大成多行
  (SELECT `l`.`id`   FROM `t_land` `l` WHERE `l`.`crzdbh` = `src`.`crzdbh` AND `l`.`del_flag` = 0 LIMIT 1) AS `land_id`,
  (SELECT `l`.`dkmc` FROM `t_land` `l` WHERE `l`.`crzdbh` = `src`.`crzdbh` AND `l`.`del_flag` = 0 LIMIT 1) AS `dkmc`,
  `src`.`ptsslb`                                                    AS `ptsslb`,
  `src`.`id`                                                        AS `facility_id`,
  `src`.`ptxmmc`                                                    AS `ptxmmc`,
  `src`.`dldj`                                                      AS `dldj`,
  `src`.`jsdw`                                                      AS `jsdw`,
  `src`.`sgdw`                                                      AS `sgdw`,
  `src`.`jldw`                                                      AS `jldw`,
  `src`.`sjjgsj`                                                    AS `complete_date`,

  -- ===== 13 类资料勾选：有确凿依据的 5 类 =====
  CASE WHEN `src`.`sgxk` = '是' THEN 1 ELSE 0 END                    AS `has_sgxk`,
  CASE WHEN `src`.`sfjg` = '是' THEN 1 ELSE 0 END                    AS `has_ysbg`,
  CASE WHEN `src`.`sjjgsj` IS NOT NULL THEN 1 ELSE 0 END             AS `has_jgtc`,
  -- 04~11 类（质量监督/材料检验/安全监督/规划验收合格证/竣工验收备案/档案专项验收/
  -- 道路工程移交单/养护协议）：源库无对应标志，一律 0，待中心按实际资料补录
  0, 0, 0, 0, 0, 0, 0, 0,
  CASE WHEN `src`.`jgwj` = '是' THEN 1 ELSE 0 END                    AS `has_jgwj`,
  CASE WHEN `src`.`yjwj` = '是' THEN 1 ELSE 0 END                    AS `has_yjwj`,

  -- ===== 移交信息 =====
  CASE WHEN `src`.`sfyj` = '是' THEN `src`.`jsdw` ELSE NULL END       AS `handover_unit`,
  NULLIF(TRIM(COALESCE(`src`.`jsgydw`, '')), '')                     AS `receive_unit`,

  -- ===== 状态：已移交 > 已验收 > 验收中 > 未验收 =====
  CASE
    WHEN `src`.`sfyj` = '是' THEN '已移交'
    WHEN `src`.`sfjg` = '是' THEN '已验收'
    WHEN `src`.`jgwj` = '是' OR `src`.`yjwj` = '是' THEN '验收中'
    ELSE '未验收'
  END                                                                AS `status`,

  `src`.`id`                                                        AS `source_facility_id`,

  -- ===== 资料归集数（13 列勾选之和，与后端 LedgerSupport.recountMaterials 口径一致）=====
  (CASE WHEN `src`.`sgxk` = '是' THEN 1 ELSE 0 END
   + CASE WHEN `src`.`sfjg` = '是' THEN 1 ELSE 0 END
   + CASE WHEN `src`.`sjjgsj` IS NOT NULL THEN 1 ELSE 0 END
   + CASE WHEN `src`.`jgwj` = '是' THEN 1 ELSE 0 END
   + CASE WHEN `src`.`yjwj` = '是' THEN 1 ELSE 0 END)                AS `material_count`,

  -- ===== 备注：只给「宗地在 t_land 里找不到」的孤儿行写提示（实测 53 条）=====
  CASE WHEN (SELECT `l`.`id` FROM `t_land` `l`
              WHERE `l`.`crzdbh` = `src`.`crzdbh` AND `l`.`del_flag` = 0 LIMIT 1) IS NULL
       THEN CONCAT('迁移提示：出让宗地编号「', COALESCE(`src`.`crzdbh`, '（空）'),
                   '」在 t_land 中无匹配记录，地块名称待人工核对补录')
       ELSE NULL END                                                 AS `remark`,

  0                                                                  AS `del_flag`,
  'migration'                                                        AS `create_by`,
  NOW()                                                              AS `create_time`
FROM (
  SELECT
    `s`.*,
    YEAR(COALESCE(`s`.`sjjgsj`, `s`.`createTime`, NOW()))            AS `y`,
    -- 同年度内流水号：(竣工时间, id) 升序计数。确定性、可复跑，不依赖用户变量
    (SELECT COUNT(*)
       FROM `xj_kjkfb_supporting_facilities` `b`
      WHERE `b`.`ptsslb` IN ('道路', '市政道路', '道路及管线')
        AND `b`.`ptxmmc` IS NOT NULL AND `b`.`ptxmmc` <> ''
        AND (`b`.`delFlag` IS NULL OR `b`.`delFlag` = '0')
        AND YEAR(COALESCE(`b`.`sjjgsj`, `b`.`createTime`, NOW()))
            = YEAR(COALESCE(`s`.`sjjgsj`, `s`.`createTime`, NOW()))
        AND (COALESCE(`b`.`sjjgsj`, `b`.`createTime`, NOW()), `b`.`id`)
         <= (COALESCE(`s`.`sjjgsj`, `s`.`createTime`, NOW()), `s`.`id`)
    )                                                                AS `seq`
  FROM `xj_kjkfb_supporting_facilities` `s`
  WHERE `s`.`ptsslb` IN ('道路', '市政道路', '道路及管线')
    AND `s`.`ptxmmc` IS NOT NULL AND `s`.`ptxmmc` <> ''
    AND (`s`.`delFlag` IS NULL OR `s`.`delFlag` = '0')
) `src`;

-- ---------------------------------------------------------------------------
-- 2) 后置校验
--    2.1 迁移结果总览：台账条数（应 = 1340）、已迁移（source_facility_id 非空）、
--        人工新增（source_facility_id 为空）
-- ---------------------------------------------------------------------------
SELECT '迁移结果' AS 项,
       COUNT(*) AS 台账总数,
       SUM(CASE WHEN `source_facility_id` IS NOT NULL THEN 1 ELSE 0 END) AS 由迁移生成,
       SUM(CASE WHEN `source_facility_id` IS NULL THEN 1 ELSE 0 END)     AS 人工新增,
       SUM(CASE WHEN `material_count` > 0 THEN 1 ELSE 0 END)            AS 已有资料勾选,
       SUM(CASE WHEN `remark` LIKE '迁移提示%' THEN 1 ELSE 0 END)        AS 宗地未匹配孤儿
FROM `t_road_acceptance_ledger`;

--    2.2 ★ 对账：底账条数 与 已迁移条数 必须相等（差 > 0 说明有行被 INSERT IGNORE 静默丢弃，
--        通常是 ledger_no 撞唯一键，需查下面的 2.3）
SELECT (SELECT COUNT(*) FROM `xj_kjkfb_supporting_facilities`
         WHERE `ptsslb` IN ('道路', '市政道路', '道路及管线')
           AND `ptxmmc` IS NOT NULL AND `ptxmmc` <> ''
           AND (`delFlag` IS NULL OR `delFlag` = '0'))                AS 底账条数,
       (SELECT COUNT(*) FROM `t_road_acceptance_ledger`
         WHERE `source_facility_id` IS NOT NULL)                      AS 已迁移条数,
       (SELECT COUNT(*) FROM `t_road_acceptance_ledger` WHERE `ledger_no` LIKE 'YS-%') AS 台账编号条数;

--    2.3 台账编号重复检查：应输出 0 行
SELECT `ledger_no`, COUNT(*) AS 重复次数
FROM `t_road_acceptance_ledger`
GROUP BY `ledger_no` HAVING COUNT(*) > 1;

--    2.4 状态分布（迁移后基线：已移交 84、已验收、验收中、未验收 合计 1340）
SELECT `status` AS 状态, COUNT(*) AS 条数
FROM `t_road_acceptance_ledger`
GROUP BY `status` ORDER BY 条数 DESC;

--    2.5 13 类资料勾选分布（迁移只填 5 类，其余 8 类应全为 0）
SELECT '01 施工许可证' AS 资料, SUM(`has_sgxk`) AS 已勾选
FROM `t_road_acceptance_ledger`
UNION ALL SELECT '02 竣工验收报告',   SUM(`has_ysbg`)    FROM `t_road_acceptance_ledger`
UNION ALL SELECT '03 竣工图测',       SUM(`has_jgtc`)    FROM `t_road_acceptance_ledger`
UNION ALL SELECT '04 质量监督报告',   SUM(`has_zljdbg`)  FROM `t_road_acceptance_ledger`
UNION ALL SELECT '05 材料检验报告',   SUM(`has_cljybg`)  FROM `t_road_acceptance_ledger`
UNION ALL SELECT '06 安全监督报告',   SUM(`has_ajbg`)    FROM `t_road_acceptance_ledger`
UNION ALL SELECT '07 规划验收合格证', SUM(`has_ghyshgz`) FROM `t_road_acceptance_ledger`
UNION ALL SELECT '08 竣工验收备案表', SUM(`has_jgbaba`)  FROM `t_road_acceptance_ledger`
UNION ALL SELECT '09 档案专项验收',   SUM(`has_dazxys`)  FROM `t_road_acceptance_ledger`
UNION ALL SELECT '10 道路工程移交单', SUM(`has_dlyjd`)   FROM `t_road_acceptance_ledger`
UNION ALL SELECT '11 养护协议',       SUM(`has_yhxy`)    FROM `t_road_acceptance_ledger`
UNION ALL SELECT '12 竣工文件',       SUM(`has_jgwj`)    FROM `t_road_acceptance_ledger`
UNION ALL SELECT '13 移交文件',       SUM(`has_yjwj`)    FROM `t_road_acceptance_ledger`;

--    2.6 按年度看台账编号分布（用于核对 4 位流水是否够用）
SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(`ledger_no`, '-', 2), '-', -1) AS 年度,
       COUNT(*) AS 条数, MIN(`ledger_no`) AS 最小号, MAX(`ledger_no`) AS 最大号
FROM `t_road_acceptance_ledger`
WHERE `ledger_no` LIKE 'YS-%'
GROUP BY SUBSTRING_INDEX(SUBSTRING_INDEX(`ledger_no`, '-', 2), '-', -1)
ORDER BY 年度;

-- ---------------------------------------------------------------------------
-- 3) 可选：回退「推断类」资料勾选（中心若不认可自动勾选，执行下面两行即可）
--    说明：has_ysbg 由「是否竣工」推断、has_jgtc 由「有实际竣工日期」推断，
--          回退后这两列归零，同时 material_count 需重算（保留 3 类：施工许可/竣工文件/移交文件）。
-- ---------------------------------------------------------------------------
-- UPDATE `t_road_acceptance_ledger`
--    SET `has_ysbg` = 0, `has_jgtc` = 0
--  WHERE `source_facility_id` IS NOT NULL;
--
-- UPDATE `t_road_acceptance_ledger`
--    SET `material_count` = `has_sgxk` + `has_ysbg` + `has_jgtc` + `has_zljdbg` + `has_cljybg`
--                         + `has_ajbg` + `has_ghyshgz` + `has_jgbaba` + `has_dazxys` + `has_dlyjd`
--                         + `has_yhxy` + `has_jgwj` + `has_yjwj`
--  WHERE `source_facility_id` IS NOT NULL;
