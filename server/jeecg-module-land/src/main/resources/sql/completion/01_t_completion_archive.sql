-- =============================================================================
-- 竣工验收项目历史工程资料数字化档案 · 建表脚本（方案 2.3.2 第 8 项）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版 / MySQL 5.7）
--
-- ★★ 开工前的逆向结论（必须先读）
--   本功能在旧系统里【不存在】。与「道路设施验收及移交资料台账」（2.3.2 第 7 项）
--   同样是按方案新建，三条独立证据（均可复跑）：
--     ① 旧代码 code/old/3DPlanWeb（NutzWk）与 code/old/tj-sfw（JEECG）全文检索
--        「竣工验收项目历史工程资料 / 历史工程资料 / 数字化档案」→ 0 命中；
--     ② 旧库 nutzwk_ywk(179 表) / nutzwk_jyxyd(50 表) / jeecg-boot 的
--        information_schema 里没有任何「历史工程资料 / 数字化」表；
--     ③ 旧菜单表 nutzwk_ywk.stargis_menu（92 行、8 个一级菜单）里没有该菜单。
--
--   ★★ 因此本模块【不做数据迁移】——旧系统里没有对应数据可迁。
--      本脚本只建空表，数据由页面录入 + Excel 导出（需求原文「可快速导出档案信息」）。
--      （对比：第 7 项台账模块有 1340 条旧配套项目可作底账，那一个才带迁移脚本。）
--
-- ★ 与清单 §6.2.7 DDL 的差异（照抄会缺东西，逐条说明）
--   1) 新增 `archive_no`（NOT NULL + 单列唯一键 uk_ca_no）：
--      §6.2.7 只有 `project_code`（项目编号），但它是**历史项目自带的原编号**，
--      可空、且不保证唯一；本模块需要一个**系统生成、可检索、可追溯**的档案编号，
--      格式 JG-{yyyy}-{4位}（JG = 竣工），允许业务手工改写。
--      两者语义不同，因此分成两列并存：`archive_no` = 本系统档案编号，`project_code` = 历史项目原编号。
--   2) 新增关联上下文 6 列（land_id / facility_id / crzdbh / ptxmmc / dkmc / ptsslb）：
--      §6.3.8 明确「这是存量历史项目，不一定有对应的 t_land/t_facility 记录」，
--      所以这 6 列**全部可空**，只作为「关联项目信息（选填）」；
--      其中 `facility_id` 与 `crzdbh` 是关联扫描件（t_archive）的查找键
--      （按配套项目ID 优先、出让宗地编号兜底，与档案模块「先选宗地→再选配套」的口径一致）。
--   3) 新增 `retention`（保管期限，入字典 land_completion_retention）：
--      本模块是档案域功能，保管期限是档案的基本属性，且取值由上级规范规定、
--      需要集中维护（字典），故补上。
--   4) 新增 `archive_count`（关联档案数，冗余）：列表页要显示「已挂 N 份扫描件」，
--      由服务端在关联/取消关联时维护，避免列表页为了一个数字去 JOIN t_archive。
--   5) 补齐 ENGINE / CHARSET（InnoDB + utf8mb4_general_ci，与 t_archive / t_land /
--      t_road_acceptance_ledger 一致）、审计列用 varchar(64)（与既有表实测一致）、
--      取消 `del_flag` 参与唯一键（见第 1 条：唯一键就是 archive_no 单列）。
--   6) 不加 `gnq`（功能区）：第 7 项加它是因为**旧数据**把「生态城/经开区」等非行政区值
--      混在 xzqh 里需要拆开；本模块没有旧数据要清洗，凭空加一列只会增加录入负担。
--
-- ★ 数字化状态（未数字化/数字化中/已数字化）**不入字典**：
--   3 个取值固定、且驱动前端标签配色与统计口径，放代码枚举（DigitizeStatus）更稳，
--   并由后端做白名单校验 —— 与第 7 项台账模块对 status 的处理完全一致。
--   而「项目类型」「保管期限」措辞可能被中心调整，入字典。
--
-- ★ 扫描件口径（清单 §6.3.8）：与 t_archive 通过 archive_id 关联，实际扫描件放
--   t_archive_file。本表**只存指针**，绝不写 t_archive。
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
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_completion_archive';

-- ---------------------------------------------------------------------------
-- 1) 竣工验收项目历史工程资料数字化档案
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_completion_archive` (
  `id`                VARCHAR(36)  NOT NULL COMMENT '主键',

  -- ===== 档案标识 =====
  `archive_no`        VARCHAR(64)  NOT NULL COMMENT '★档案编号（自动生成 JG-{yyyy}-{4位}，允许手工改写）',

  -- ===== 历史项目基本信息 =====
  `project_name`      VARCHAR(255) NOT NULL COMMENT '★历史项目名称',
  `project_code`      VARCHAR(100)     NULL COMMENT '历史项目原编号（项目自带的编号，可空、不保证唯一）',
  `xzqh`              VARCHAR(20)      NULL COMMENT '行政区划（16 区）',
  `project_type`      VARCHAR(50)      NULL COMMENT '项目类型（字典 land_completion_project_type）',

  -- ===== 关联项目信息（★ 全选填：存量历史项目不一定有对应宗地/配套项目，见 §6.3.8）=====
  `land_id`           VARCHAR(64)      NULL COMMENT '出让宗地ID → t_land.id（可空）',
  `facility_id`       VARCHAR(100)     NULL COMMENT '配套项目ID → t_supporting_facilities.id（可空；关联扫描件的第一查找键）',
  `crzdbh`            VARCHAR(100)     NULL COMMENT '出让宗地编号 → t_land.crzdbh（可空；关联扫描件的兜底查找键）',
  `ptxmmc`            VARCHAR(100)     NULL COMMENT '配套项目名称（冗余，便于关联扫描件与展示）',
  `dkmc`              VARCHAR(255)     NULL COMMENT '地块名称（冗余）',
  `ptsslb`            VARCHAR(100)     NULL COMMENT '配套设施类别（道路/排水/供水/燃气…，冗余）',

  -- ===== 参建单位 =====
  `build_unit`        VARCHAR(200)     NULL COMMENT '建设单位',
  `construct_unit`    VARCHAR(200)     NULL COMMENT '施工单位',
  `design_unit`       VARCHAR(200)     NULL COMMENT '设计单位',
  `supervise_unit`    VARCHAR(200)     NULL COMMENT '监理单位',

  -- ===== 关键日期 =====
  `start_date`        DATE             NULL COMMENT '开工日期',
  `complete_date`     DATE             NULL COMMENT '竣工日期',
  `acceptance_date`   DATE             NULL COMMENT '验收日期',

  -- ===== 投资与保管 =====
  `invest_amount`     DECIMAL(15,2)    NULL COMMENT '投资额（万元）',
  `retention`         VARCHAR(20)      NULL COMMENT '保管期限（字典 land_completion_retention：永久/定期30年/定期10年/长期）',

  -- ===== ★ 数字化属性（本模块的追踪重点，见 §6.3.8）=====
  `digitize_status`   VARCHAR(20)  NOT NULL DEFAULT '未数字化'
      COMMENT '数字化状态：未数字化/数字化中/已数字化（不入字典，见 DigitizeStatus 枚举）',
  `digitize_date`     DATE             NULL COMMENT '数字化完成日期',
  `digitize_org`      VARCHAR(200)     NULL COMMENT '数字化加工单位',
  `page_count`        INT              NULL COMMENT '总页数',
  `file_count`        INT              NULL COMMENT '文件数',
  `scan_dpi`          INT              NULL COMMENT '扫描分辨率（DPI）',

  -- ===== 关联档案（扫描件）=====
  `archive_id`        VARCHAR(36)      NULL COMMENT '关联档案 → t_archive.id（扫描件本体放 t_archive_file，本表只留指针）',
  `archive_count`     INT          NOT NULL DEFAULT 0 COMMENT '关联档案数（冗余，服务端在关联/取消关联时维护）',

  `remark`            VARCHAR(1000)    NULL COMMENT '备注',
  `del_flag`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '删除状态 0正常 1已删除',
  `create_by`         VARCHAR(64)      NULL COMMENT '创建人',
  `create_time`       DATETIME         NULL COMMENT '创建时间',
  `update_by`         VARCHAR(64)      NULL COMMENT '更新人',
  `update_time`       DATETIME         NULL COMMENT '更新时间',

  PRIMARY KEY (`id`),
  -- ★ 唯一键是 archive_no 单列：不含 del_flag（否则「同一编号删两次就冲突」，且允许删后重号）
  UNIQUE KEY `uk_ca_no` (`archive_no`),
  KEY `idx_ca_name` (`project_name`),
  KEY `idx_ca_code` (`project_code`),
  KEY `idx_ca_xzqh` (`xzqh`),
  KEY `idx_ca_type` (`project_type`),
  KEY `idx_ca_status` (`digitize_status`),
  KEY `idx_ca_complete_date` (`complete_date`),
  KEY `idx_ca_digitize_date` (`digitize_date`),
  KEY `idx_ca_archive` (`archive_id`),
  KEY `idx_ca_facility` (`facility_id`),
  KEY `idx_ca_crzdbh` (`crzdbh`),
  KEY `idx_ca_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='竣工验收项目历史工程资料数字化档案';

-- ---------------------------------------------------------------------------
-- 2) 建表自检
-- ---------------------------------------------------------------------------
SELECT TABLE_NAME AS 表名, TABLE_COMMENT AS 说明, TABLE_COLLATION AS 排序规则
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_completion_archive';

-- 索引清单（应输出 13 行：PRIMARY + 1 唯一键 + 11 普通索引）
SELECT INDEX_NAME AS 索引名, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS 列,
       CASE WHEN NON_UNIQUE = 0 THEN '唯一' ELSE '普通' END AS 类型
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_completion_archive'
GROUP BY INDEX_NAME, NON_UNIQUE
ORDER BY INDEX_NAME;

-- 数字化状态列必须是 NOT NULL 且默认「未数字化」
SELECT COLUMN_NAME AS 列, IS_NULLABLE AS 可空, COLUMN_DEFAULT AS 默认值, COLUMN_COMMENT AS 说明
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_completion_archive'
  AND COLUMN_NAME IN ('digitize_status', 'archive_no', 'archive_id', 'archive_count');
