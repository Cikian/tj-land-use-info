-- =============================================================================
-- 道路交付及养护协议移交事项 · 建表脚本（方案 2.3.2 第 6 项）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版 / MySQL 5.7）
--
-- 需求原文：
--   实现对道路交付养护协议及移交事项的管理，可进行快速检索查询。
--
-- ★★ 与「道路设施验收及移交资料台账」（第 7 项）一样，本功能在旧系统中【不存在】：
--     ① 旧代码 code/old/3DPlanWeb 与 code/old/tj-sfw 全文检索「养护协议 / 交付 / 移交事项」
--        → 0 命中；
--     ② 旧库三个库没有任何移交/协议表，只有 t_supporting_facilities 的
--        sfyj(是否移交) / jsgydw(接收管养单位) / jsdw(建设单位) 等字段；
--     ③ 旧菜单表 stargis_menu（92 行）无此项。
--   因此本模块是**按方案新建**；「迁移」只做一件事：
--   把旧库中 sfyj='是' 的 84 条道路生成「正式移交」事项初始记录（见 05 脚本）。
--
-- ★ 与台账（第 7 项）的分工，避免两处重复登记同一件事：
--   · 台账（t_road_acceptance_ledger）：**普查**——1340 条道路一条一行，管「13 类资料齐不齐」；
--   · 本表（t_road_handover）：**事项**——只登记真正发生/在办的移交事项，
--     管「交付与养护协议签了没、养护期从哪天到哪天、谁接收管养」。
--   所以本表**不**为 710 条 sfyj='否' 的道路预生成「待移交」记录：
--   「哪些路还没移交」用台账页筛选 `status <> 已移交` 即可看到，不必两处维护同一份清单。
--
-- ★ 与清单 §6.2.5 DDL 的差异（照抄会翻车）：
--   1) 台账编号唯一键单列（不含 del_flag），否则「同一编号删两次即冲突」；
--   2) 新增 source_facility_id 唯一键——迁移幂等键（人工新增时为 NULL，唯一键允许多 NULL）；
--   3) 新增 gnq（功能区）：旧数据 xzqh 里混入 192 条功能区值（生态城/经开区/高新区/
--      保税区/土地发展中心），按清单 3.0 第 4 条拆成「16 区 + 功能区」两列；
--   4) 新增 land_id / dkmc / ptsslb 等冗余列，让列表页免 JOIN 旧设施表（旧表 utf8_general_ci、
--      与本表 utf8mb4_general_ci 跨表 JOIN 走不上索引）；
--   5) status 固定 3 值（待移交/移交中/已移交）**不入字典**：它驱动前端配色与列表筛选，
--      放代码枚举（HandoverStatus）比放字典表更稳；
--      而「移交类型」（道路交付/养护协议/正式移交）措辞可能调整，因此入字典；
--   6) 附件口径（与台账一致）：协议扫描件、移交单统一挂档案管理 t_archive/t_archive_file，
--      本表只留 archive_id 指针 + archive_count 计数。
--
-- 脚本可重复执行：CREATE TABLE IF NOT EXISTS，**不会删除已有数据**。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0) 前置检查（只报告，不破坏）
-- ---------------------------------------------------------------------------
SELECT '前置检查：已存在的本模块表' AS 检查项, TABLE_NAME AS 表名, TABLE_ROWS AS 估算行数
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_handover';

-- ---------------------------------------------------------------------------
-- 1) 道路交付及养护协议移交事项
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_road_handover` (
  `id`                VARCHAR(36)  NOT NULL COMMENT '主键',
  `handover_no`       VARCHAR(64)  NOT NULL COMMENT '★移交事项编号（自动生成 YJ-{yyyy}-{4位}，允许手工改写）',

  -- ===== 道路 / 项目基本信息 =====
  `road_name`         VARCHAR(200) NOT NULL COMMENT '★道路名称',
  `road_code`         VARCHAR(64)      NULL COMMENT '道路编号',
  `dldj`              VARCHAR(50)      NULL COMMENT '道路等级：城市主干路/次干路/支路',
  `start_point`       VARCHAR(200)     NULL COMMENT '起点',
  `end_point`         VARCHAR(200)     NULL COMMENT '终点',
  `length_m`          DECIMAL(12,2)    NULL COMMENT '长度(米)',
  `red_line_width`    DECIMAL(10,2)    NULL COMMENT '红线宽度(米)',
  `xzqh`              VARCHAR(20)      NULL COMMENT '行政区划（16 区；功能区值已拆到 gnq）',
  `gnq`               VARCHAR(50)      NULL COMMENT '功能区（生态城/经开区/高新区/保税区/土地发展中心）',
  `ptsslb`            VARCHAR(100)     NULL COMMENT '配套设施类别（道路/市政道路/道路及管线…）',

  -- ===== 与主业务系统的挂钩点 =====
  `crzdbh`            VARCHAR(100)     NULL COMMENT '出让宗地编号 → t_land.crzdbh',
  `land_id`           VARCHAR(64)      NULL COMMENT '出让宗地ID → t_land.id（冗余）',
  `dkmc`              VARCHAR(255)     NULL COMMENT '地块名称（冗余，迁移时由 crzdbh 反查）',
  `facility_id`       VARCHAR(100)     NULL COMMENT '配套项目ID → t_supporting_facilities.id',
  `ptxmmc`            VARCHAR(100)     NULL COMMENT '配套项目名称（冗余，与 road_name 同值）',

  -- ===== 协议与移交信息 =====
  `handover_type`     VARCHAR(50)      NULL COMMENT '移交类型（字典 land_road_handover_type：道路交付/养护协议/正式移交）',
  `agreement_no`      VARCHAR(100)     NULL COMMENT '协议编号',
  `agreement_name`    VARCHAR(255)     NULL COMMENT '协议名称',
  `agreement_date`    DATE             NULL COMMENT '协议签订日期',
  `build_unit`        VARCHAR(200)     NULL COMMENT '建设单位',
  `receive_unit`      VARCHAR(200)     NULL COMMENT '接收管养单位（旧字段 jsgydw）',
  `handover_date`     DATE             NULL COMMENT '实际移交日期',
  `maintenance_start` DATE             NULL COMMENT '养护起始日期',
  `maintenance_end`   DATE             NULL COMMENT '养护截止日期',

  -- ===== 状态与档案关联 =====
  `status`            VARCHAR(20)  NOT NULL DEFAULT '待移交'
      COMMENT '状态：待移交/移交中/已移交（不入字典，见 HandoverStatus 枚举）',
  `archive_id`        VARCHAR(36)      NULL COMMENT '关联档案 → t_archive.id（协议扫描件/移交单挂档案）',
  `archive_count`     INT          NOT NULL DEFAULT 0 COMMENT '关联档案数（冗余，服务端维护）',

  -- ===== 迁移 =====
  `source_facility_id` VARCHAR(100)    NULL COMMENT '迁移来源：旧配套项目ID（★ 迁移幂等键，人工新增为 NULL）',

  `remark`            VARCHAR(1000)    NULL COMMENT '备注',
  `del_flag`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '删除状态 0正常 1已删除',
  `create_by`         VARCHAR(64)      NULL COMMENT '创建人',
  `create_time`       DATETIME         NULL COMMENT '创建时间',
  `update_by`         VARCHAR(64)      NULL COMMENT '更新人',
  `update_time`       DATETIME         NULL COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rh_no` (`handover_no`),
  -- ★ 迁移幂等键：一个配套项目最多一条移交事项；人工新增时为 NULL（唯一键允许多 NULL）
  UNIQUE KEY `uk_rh_source` (`source_facility_id`),
  KEY `idx_rh_road` (`road_name`),
  KEY `idx_rh_xzqh` (`xzqh`),
  KEY `idx_rh_status` (`status`),
  KEY `idx_rh_type` (`handover_type`),
  KEY `idx_rh_crzdbh` (`crzdbh`),
  KEY `idx_rh_facility` (`facility_id`),
  KEY `idx_rh_archive` (`archive_id`),
  KEY `idx_rh_agreement_date` (`agreement_date`),
  KEY `idx_rh_handover_date` (`handover_date`),
  KEY `idx_rh_maintenance_end` (`maintenance_end`),
  KEY `idx_rh_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='道路交付及养护协议移交事项';

-- ---------------------------------------------------------------------------
-- 2) 建表自检
-- ---------------------------------------------------------------------------
SELECT TABLE_NAME AS 表名, TABLE_COMMENT AS 说明, TABLE_COLLATION AS 排序规则
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_handover';

-- 关键列是否齐备（应输出 12 行左右）
SELECT COLUMN_NAME AS 列, COLUMN_TYPE AS 类型, COLUMN_COMMENT AS 说明
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_handover'
  AND COLUMN_NAME IN ('handover_no', 'road_name', 'handover_type', 'agreement_no', 'agreement_date',
                      'handover_date', 'maintenance_start', 'maintenance_end', 'receive_unit',
                      'status', 'archive_id', 'source_facility_id')
ORDER BY ORDINAL_POSITION;
