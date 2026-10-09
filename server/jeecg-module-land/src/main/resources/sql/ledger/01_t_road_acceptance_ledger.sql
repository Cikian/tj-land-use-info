-- =============================================================================
-- 道路设施验收及移交资料台账 · 建表脚本（方案 2.3.2 第 7 项）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版 / MySQL 5.7）
--
-- ★★ 开工前的逆向结论（必须先读，否则会误以为这是「旧功能搬家」）
--   本功能在旧系统里【不存在】。三条实测证据：
--     ① 旧代码 code/old/3DPlanWeb（NutzWk）与 code/old/tj-sfw（JEECG）全文检索
--        「台账 / 道路设施验收 / 验收及移交」→ 0 命中（唯二命中是 jeecg 自带积木报表
--        模板与 attachmentContrast.js 里的「建设工程规划验收」字样，均无关）；
--     ② 旧库 nutzwk_ywk(179 表) / nutzwk_jyxyd(50 表) / jeecg-boot 的
--        information_schema 里没有任何台账/验收表；只有
--        t_supporting_facilities 的 sfyj(是否移交) / yjwj(移交文件) /
--        jgwj(竣工文件) / sjjgsj(实际竣工时间) / jsgydw(接收管养单位) 等字段；
--     ③ 旧菜单表 nutzwk_ywk.stargis_menu（92 行、8 个一级菜单）里没有该菜单，
--        连「档案管理/收发文管理」也只是 href 指向不存在页面的空菜单。
--   因此本模块是**按方案新建**（不是迁移旧模块），但**要迁旧数据**：
--   以旧配套项目表里的道路类项目为底账，生成台账初始记录（见 05 脚本）。
--
-- ★ 与清单 §6.2.6 DDL 的关键差异（照抄会翻车）
--   1) 资料勾选列由 6 个扩到 **13 个**（需求方 2026-09 确认）：
--      前 6 个沿用清单原文（has_sgxk/has_ysbg/has_jgtc/has_zljdbg/has_cljybg/has_ajbg），
--      另补 7 个，与旧系统「配套附件 14 类目录」里的验收/移交部分一一对号
--      （旧代码 xjKjkfbSupportingFacilitiesController.java:650 实测的目录清单：
--        01-配套项目核定用地与地籍调查 … 11-施工许可、12-不动产登记、
--        13-竣工文件、14-移交文件）。
--   2) 台账编号唯一键单列（照提级论证模块的教训：不含 del_flag，避免「删两次就冲突」）；
--   3) 新增 source_facility_id 唯一键——迁移幂等键（人工新增的记录该列为 NULL，
--      MySQL 唯一键允许多个 NULL，所以不影响手工录入）；
--   4) 新增 gnq（功能区）：旧数据 xzqh 里混入「生态城 136 / 土地发展中心 20 /
--      经开区 19 / 高新区 15 / 保税区 2」共 192 条非行政区值（清单 3.0 第 4 条），
--      迁移时按「16 区 + 功能区」两列拆开；
--   5) 新增 6 个冗余列（dkmc/ptsslb/ptxmmc/receive_unit/material_count/complete_date），
--      让台账页免 JOIN（旧设施表 t_supporting_facilities 无合适索引，
--      且是 utf8_general_ci，与本表 utf8mb4_general_ci 跨表 JOIN 走不上索引）；
--   6) 状态 4 值（未验收/验收中/已验收/已移交）**不入字典**：它驱动前端标签配色，
--      放代码枚举更稳（照提级论证模块对 status 的处理）；
--      验收类型与验收结果入字典（措辞可能被中心调整）。
--
-- ★ 附件口径（需求方确认）：资料原件**不另建附件表**，统一挂到档案管理
--   t_archive / t_archive_file，本表只留 archive_id 指针。
--   因此本模块没有 has_* 之外的「文件表」，导出 zIP 之类一律复用档案模块。
--
-- ★ 脏数据口径：旧库 process_status 里「道路工程移交/竣工验收许可阶段」实测 0 条，
--   所以台账的「验收日期/验收单编号/验收组织单位/验收结果」在迁移时**一律留空**，
--   由中心按实际验收资料补录；迁移只填「有确凿依据」的字段（见 05 脚本的映射表）。
--
-- 脚本可重复执行：用 CREATE TABLE IF NOT EXISTS，**不会删除已有数据**
-- （因此本地库与线上库可共用同一份脚本）。需要清空重建时另执行 99_rollback_online.sql。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0) 前置检查：表是否已存在（只报告，不做任何破坏）
-- ---------------------------------------------------------------------------
SELECT '前置检查：已存在的本模块表' AS 检查项, TABLE_NAME AS 表名, TABLE_ROWS AS 估算行数
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_acceptance_ledger';

-- ---------------------------------------------------------------------------
-- 1) 道路设施验收及移交资料台账
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_road_acceptance_ledger` (
  `id`                VARCHAR(36)  NOT NULL COMMENT '主键',

  -- ===== 台账标识 =====
  `ledger_no`         VARCHAR(64)  NOT NULL COMMENT '★台账编号（自动生成 YS-{yyyy}-{4位}，允许手工改写）',

  -- ===== 道路 / 项目基本信息 =====
  `road_name`         VARCHAR(200) NOT NULL COMMENT '★道路名称（= 配套项目名称 ptxmmc）',
  `xzqh`              VARCHAR(20)      NULL COMMENT '行政区划（16 区；功能区值已拆到 gnq）',
  `gnq`               VARCHAR(50)      NULL COMMENT '功能区（生态城/土地发展中心/经开区/高新区/保税区）',
  `crzdbh`            VARCHAR(100)     NULL COMMENT '出让宗地编号 → t_land.crzdbh',
  `land_id`           VARCHAR(64)      NULL COMMENT '出让宗地ID → t_land.id（冗余，便于直达档案）',
  `dkmc`              VARCHAR(255)     NULL COMMENT '地块名称（冗余，迁移时由 crzdbh 反查）',
  `ptsslb`            VARCHAR(100)     NULL COMMENT '配套设施类别（道路/市政道路/道路及管线…）',
  `facility_id`       VARCHAR(100)     NULL COMMENT '配套项目ID → t_supporting_facilities.id',
  `ptxmmc`            VARCHAR(100)     NULL COMMENT '配套项目名称（冗余，与 road_name 同值）',
  `dldj`              VARCHAR(50)      NULL COMMENT '道路等级',
  `jsdw`              VARCHAR(100)     NULL COMMENT '建设单位',
  `sgdw`              VARCHAR(100)     NULL COMMENT '施工单位',
  `jldw`              VARCHAR(100)     NULL COMMENT '监理单位',

  -- ===== 验收信息（迁移时留空，由中心补录）=====
  `acceptance_type`   VARCHAR(50)      NULL COMMENT '验收类型（字典 land_road_acceptance_type：竣工验收/规划验收/档案专项验收/移交验收）',
  `acceptance_no`     VARCHAR(100)     NULL COMMENT '验收单编号',
  `acceptance_date`   DATE             NULL COMMENT '验收日期',
  `acceptance_org`    VARCHAR(200)     NULL COMMENT '验收组织单位',
  `acceptance_result` VARCHAR(50)      NULL COMMENT '验收结果（字典 land_road_acceptance_result：合格/不合格/整改后合格）',
  `complete_date`     DATE             NULL COMMENT '实际竣工日期（迁移自旧表 sjjgsj）',

  -- ===== ★ 13 类资料勾选矩阵 =====
  -- 前 6 列沿用清单 §6.2.6 原文命名；后 7 列对齐旧系统配套附件目录的验收/移交部分
  `has_sgxk`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料01 施工许可证（旧字段 sgxk）',
  `has_ysbg`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料02 竣工验收报告',
  `has_jgtc`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料03 竣工图测',
  `has_zljdbg`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料04 质量监督报告',
  `has_cljybg`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料05 材料检验报告',
  `has_ajbg`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料06 安全监督报告',
  `has_ghyshgz`   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料07 规划验收合格证',
  `has_jgbaba`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料08 竣工验收备案表',
  `has_dazxys`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料09 档案专项验收意见',
  `has_dlyjd`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料10 道路工程移交单',
  `has_yhxy`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料11 养护协议',
  `has_jgwj`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料12 竣工文件（旧系统目录 13-竣工文件）',
  `has_yjwj`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '资料13 移交文件（旧系统目录 14-移交文件）',

  -- ===== 移交信息 =====
  `handover_unit`     VARCHAR(200)     NULL COMMENT '移交单位（通常=建设单位）',
  `receive_unit`      VARCHAR(200)     NULL COMMENT '接收管养单位（旧字段 jsgydw）',
  `handover_date`     DATE             NULL COMMENT '移交日期',

  -- ===== 状态与档案关联 =====
  `status`            VARCHAR(20)  NOT NULL DEFAULT '未验收'
      COMMENT '状态：未验收/验收中/已验收/已移交（不入字典，见 LedgerStatus 枚举）',
  `archive_id`        VARCHAR(36)      NULL COMMENT '关联档案 → t_archive.id（资料原件挂档案，本表只留指针）',
  `archive_count`     INT          NOT NULL DEFAULT 0 COMMENT '关联档案数（冗余，服务端维护）',

  -- ===== 迁移与统计 =====
  `source_facility_id` VARCHAR(100)    NULL COMMENT '迁移来源：旧配套项目ID（★ 迁移幂等键，人工新增为 NULL）',
  `material_count`    INT          NOT NULL DEFAULT 0 COMMENT '已归集资料份数（13 列勾选之和，冗余）',

  `remark`            VARCHAR(1000)    NULL COMMENT '备注',
  `del_flag`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '删除状态 0正常 1已删除',
  `create_by`         VARCHAR(64)      NULL COMMENT '创建人',
  `create_time`       DATETIME         NULL COMMENT '创建时间',
  `update_by`         VARCHAR(64)      NULL COMMENT '更新人',
  `update_time`       DATETIME         NULL COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ral_no` (`ledger_no`),
  -- ★ 迁移幂等键：一个配套项目最多一条台账；人工新增的记录此列为 NULL（唯一键允许多 NULL）
  UNIQUE KEY `uk_ral_source` (`source_facility_id`),
  KEY `idx_ral_road` (`road_name`),
  KEY `idx_ral_xzqh` (`xzqh`),
  KEY `idx_ral_status` (`status`),
  KEY `idx_ral_crzdbh` (`crzdbh`),
  KEY `idx_ral_facility` (`facility_id`),
  KEY `idx_ral_archive` (`archive_id`),
  KEY `idx_ral_acceptance_date` (`acceptance_date`),
  KEY `idx_ral_handover_date` (`handover_date`),
  KEY `idx_ral_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='道路设施验收及移交资料台账';

-- ---------------------------------------------------------------------------
-- 2) 建表自检
-- ---------------------------------------------------------------------------
SELECT TABLE_NAME AS 表名, TABLE_COMMENT AS 说明, TABLE_COLLATION AS 排序规则
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_acceptance_ledger';

-- 13 个资料列是否齐备（应输出 13 行）
SELECT COLUMN_NAME AS 资料列, COLUMN_COMMENT AS 说明
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_acceptance_ledger'
  AND COLUMN_NAME LIKE 'has\_%'
ORDER BY ORDINAL_POSITION;
