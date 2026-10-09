-- =============================================================================
-- 提级论证管理 · 建表脚本（方案 2.3.3）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版 / MySQL 5.7）
--
-- 本模块定位（见 docs/提级论证管理-详细实施设计.md 第一、二章）：
--   ★ 记录型台账：录入 → 存证 → 查询统计 → 人工登记进展与审核意见；**不驱动流程**。
--     实际论证流程在线下办理，有答复后由操作人员手工把信息录回本系统。
--   因此本脚本**只有 3 张表**（相比清单第 7 章的 4 张）：
--     t_escalation_project   项目主表（基本信息 + 地块信息 + 论证信息 + 论证结果 + 办理状态）
--     t_escalation_material  提级论证材料（多文件 / 版本 / 补充标记）
--     t_escalation_record    审核意见 / 办理记录（★ append-only：只 INSERT）
--   没有「审批流转表」和独立的「操作日志表」：
--     · 流转表在本期无意义（不上流程）；
--     · 操作日志走 jeecg 自带的 @AutoLog → sys_log（零额外表）。
--
-- ★ 与清单第 7.2 章 DDL 的关键差异（照抄会翻车，详见设计文档 3.2）：
--   1) 关联配套项目表是 xj_kjkfb_supporting_facilities，**不是 t_facility**
--      （档案模块落地时已确认：配套表复用旧表名，未新建 t_facility）；
--   2) 唯一键改为 project_no 单列。清单写的 (project_no, del_flag) 会导致
--      「同一编号删除两次即冲突」，且允许删除后重号；
--   3) 补齐 ENGINE / CHARSET（InnoDB + utf8mb4_general_ci，与 t_archive / t_land 一致）；
--   4) 审计列 create_by / update_by 用 varchar(64)（与 t_archive 实测一致）；
--   5) 新增 gnq（功能区）——旧数据 xzqh 里混入「生态城/经开区/高新区/保税区」等非行政区值，
--      按清单 3.0 第 4 条要求拆为「16 区 + 功能区」两列；
--   6) 新增冗余列 ptxmmc / material_count / record_count / latest_opinion*，
--      让台账页与列表页免去子查询（旧设施表无合适索引，应尽量避免 JOIN）；
--   7) ★ 7 个长文本列由 VARCHAR(2000) 改为 TEXT（project_summary、arg_reason、arg_basis、
--      arg_necessity、arg_feasibility、arg_content、arg_conclusion）：
--      utf8mb4 下 VARCHAR 按「4 字节/字符」计入行大小，本表 VARCHAR 合计约 19208 字符
--      ≈ 76.8KB，超过 InnoDB 单行 65535 字节上限——实测建表直接报
--      「ERROR 1118 (42000) Row size too large」。
--      改 TEXT 后行内仅占约 12 字节（正文另存），行大小降到约 21KB。
--      「≤2000 字」的上限改由**前端 maxlength + 后端校验**保证（不依赖列长度）。
--
-- ★ 口径修正（设计文档 1.3 考证）：
--   管理对象是「提级论证事项/项目」（大概率是政府投资项目），**不是**「未出让地块」。
--   「未出让地块」在方案原文里只出现过一次（1.3 背景段），而 2.3.3 四项功能通篇说「项目」，
--   原型图字段也全是项目字段。因此地块相关列（xzqh/gnq/dkmc/dk_area/ghydxz/crzdbh）
--   一律**可空**，作为「关联地块信息（选填）」处理。
--
-- 脚本可重复执行：先 DROP 再 CREATE（★ 会清空本模块数据，仅建表脚本允许如此）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0) 前置检查：表是否已存在（只报告，不做任何破坏）
--    ★ 本脚本**不再是破坏性脚本**：用 CREATE TABLE IF NOT EXISTS，
--      重复执行不会删除已有数据，因此**本地与线上库可以用同一份脚本**。
--      需要清空重建时，另执行同目录 00_reset_tables.sql（那才是危险脚本）。
-- ---------------------------------------------------------------------------
SELECT '前置检查：已存在的本模块表' AS 检查项, TABLE_NAME AS 表名, TABLE_ROWS AS 估算行数
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 't\_escalation%'
ORDER BY TABLE_NAME;

-- ---------------------------------------------------------------------------
-- 1) 提级论证项目主表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_escalation_project` (
  `id`                VARCHAR(36)  NOT NULL COMMENT '主键',
  `project_no`        VARCHAR(64)  NOT NULL COMMENT '项目编号（自动生成 TJ-{yyyy}-{4位}，允许手工改写）',
  `project_name`      VARCHAR(255) NOT NULL COMMENT '★项目名称',

  -- 基本信息（对应原型「项目基本信息」卡片）
  `declare_dept`      VARCHAR(200)     NULL COMMENT '★申报单位',
  `project_type`      VARCHAR(50)      NULL COMMENT '★项目类型（字典 land_escalation_project_type）',
  `project_scale`     VARCHAR(100)     NULL COMMENT '项目规模',
  `total_investment`  DECIMAL(15,4)    NULL COMMENT '★总投资（亿元）',
  `build_location`    VARCHAR(255)     NULL COMMENT '★建设地点',
  `declare_date`      DATE             NULL COMMENT '★申报时间',
  `project_summary`   TEXT                 NULL COMMENT '★项目概述（≤2000 字，由前端与后端校验）',

  -- 关联地块信息（★ 选填：论证对象是「项目」，地块只是可选上下文，见设计文档 1.3 考证）
  `xzqh`              VARCHAR(20)      NULL COMMENT '行政区划（16 区）',
  `gnq`               VARCHAR(50)      NULL COMMENT '功能区（生态城/经开区/高新区/保税区…）',
  `dkmc`              VARCHAR(200)     NULL COMMENT '地块名称',
  `dk_area`           DECIMAL(12,2)    NULL COMMENT '地块面积(㎡)',
  `ghydxz`            VARCHAR(200)     NULL COMMENT '规划用地性质',
  `tdzl_project`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否土地整理项目（字典 yn）',

  -- 与主业务系统的挂钩点
  `facility_id`       VARCHAR(100)     NULL COMMENT '关联配套项目ID → xj_kjkfb_supporting_facilities.id',
  `ptxmmc`            VARCHAR(100)     NULL COMMENT '配套项目名称（冗余，台账展示用）',
  `crzdbh`            VARCHAR(100)     NULL COMMENT '关联出让宗地编号 → t_land.crzdbh（可空）',

  -- 论证信息
  `arg_reason`        TEXT                 NULL COMMENT '提级论证事由（≤2000 字）',
  `arg_basis`         TEXT                 NULL COMMENT '提级论证依据（政策/规划，≤2000 字）',
  `arg_necessity`     TEXT                 NULL COMMENT '必要性说明（≤2000 字）',
  `arg_feasibility`   TEXT                 NULL COMMENT '可行性说明（≤2000 字）',
  `arg_content`       TEXT                 NULL COMMENT '提级论证事项内容（≤2000 字）',

  -- 论证结果（人工登记；取值来自字典 land_escalation_arg_result）
  `arg_result`        VARCHAR(30)      NULL COMMENT '★论证结果：通过/基本通过/需补充材料/需进一步论证/不通过',
  `arg_conclusion`    TEXT                 NULL COMMENT '论证结论（≤2000 字）',
  `arg_org`           VARCHAR(200)     NULL COMMENT '论证组织单位',
  `arg_meeting_date`  DATE             NULL COMMENT '论证会日期',
  `arg_expert_list`   VARCHAR(1000)    NULL COMMENT '论证专家名单',
  `arg_date`          DATE             NULL COMMENT '论证完成日期',

  -- 办理状态（人工登记，固定 4 值，不入字典：它驱动前端标签配色，放代码枚举更稳）
  `status`            VARCHAR(20)  NOT NULL DEFAULT '未办理'
      COMMENT '办理状态：未办理/办理中/已办结/已归档',

  -- 最新审核意见（冗余，供台账「最新审核意见」列直接读取）
  `latest_opinion`       VARCHAR(1000) NULL COMMENT '最新审核意见摘要',
  `latest_opinion_time`  DATETIME      NULL COMMENT '最新意见时间',
  `latest_opinion_by`    VARCHAR(64)   NULL COMMENT '最新意见记录人',

  -- 归档（本期预留，不做联动：归档由档案管理模块单独办理）
  `archive_id`        VARCHAR(36)      NULL COMMENT '预留：归档后关联 t_archive.id（本期不写）',

  -- 冗余计数（服务端维护）
  `material_count`    INT          NOT NULL DEFAULT 0 COMMENT '材料数（冗余）',
  `record_count`      INT          NOT NULL DEFAULT 0 COMMENT '意见记录数（冗余）',

  `remark`            VARCHAR(1000)    NULL COMMENT '备注',
  `del_flag`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '删除状态 0正常 1已删除',
  `create_by`         VARCHAR(64)      NULL COMMENT '创建人',
  `create_time`       DATETIME         NULL COMMENT '创建时间',
  `update_by`         VARCHAR(64)      NULL COMMENT '更新人',
  `update_time`       DATETIME         NULL COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_esc_no` (`project_no`),
  KEY `idx_esc_name` (`project_name`),
  KEY `idx_esc_status` (`status`),
  KEY `idx_esc_dept` (`declare_dept`),
  KEY `idx_esc_result` (`arg_result`),
  KEY `idx_esc_xzqh` (`xzqh`),
  KEY `idx_esc_facility` (`facility_id`),
  KEY `idx_esc_declare_date` (`declare_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='提级论证项目';

-- ---------------------------------------------------------------------------
-- 2) 提级论证材料表（IF NOT EXISTS：已存在则跳过，不删数据）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_escalation_material` (
  `id`            VARCHAR(36)  NOT NULL COMMENT '主键',
  `project_id`    VARCHAR(36)  NOT NULL COMMENT '提级论证项目ID',
  `material_type` VARCHAR(50)      NULL COMMENT '材料类型（字典 land_escalation_material_type）',
  `file_name`     VARCHAR(255) NOT NULL COMMENT '文件名',
  `file_ext`      VARCHAR(20)      NULL COMMENT '扩展名（小写，不含点）',
  `file_size`     BIGINT           NULL COMMENT '字节',
  `file_md5`      VARCHAR(32)      NULL COMMENT 'MD5（用于去重）',
  `store_type`    VARCHAR(20)  NOT NULL DEFAULT 'local' COMMENT '存储方式：local',
  `store_path`    VARCHAR(500) NOT NULL COMMENT '相对路径（相对 jeecg.path.upload）',
  `preview_path`  VARCHAR(500)     NULL COMMENT '预览路径（可空）',
  `version`       INT          NOT NULL DEFAULT 1 COMMENT '版本号（补充材料 +1）',
  `is_supplement` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否补充材料',
  `upload_by`     VARCHAR(64)      NULL COMMENT '上传人',
  `upload_time`   DATETIME         NULL COMMENT '上传时间',
  `del_flag`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '删除状态 0正常 1已删除',

  PRIMARY KEY (`id`),
  KEY `idx_em_project` (`project_id`),
  KEY `idx_em_type` (`material_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='提级论证材料';

-- ---------------------------------------------------------------------------
-- 3) 审核意见 / 办理记录（IF NOT EXISTS）（★ append-only：只 INSERT，不 UPDATE / DELETE）
--    这是「可追溯」的技术保证：意见一旦登记就不可篡改。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_escalation_record` (
  `id`             VARCHAR(36)  NOT NULL COMMENT '主键',
  `project_id`     VARCHAR(36)  NOT NULL COMMENT '提级论证项目ID',
  `record_type`    VARCHAR(30)  NOT NULL DEFAULT '审核意见' COMMENT '记录类型：审核意见/补充说明/其他',
  `action`         VARCHAR(30)      NULL COMMENT '结论短语：同意/基本同意/请补充材料/需进一步论证/不同意（可空）',
  `opinion`        VARCHAR(2000)    NULL COMMENT '★意见正文（前端限 1000 字；VARCHAR 按字符计，容量足够）',
  `attachment_ids` VARCHAR(1000)    NULL COMMENT '附件（逗号分隔：材料ID；意见附件走通用上传，存相对路径 store_path）',
  `recorder_id`    VARCHAR(64)      NULL COMMENT '记录人账号',
  `recorder_name`  VARCHAR(64)      NULL COMMENT '记录人姓名',
  `recorder_dept`  VARCHAR(200)     NULL COMMENT '记录人部门',
  `record_time`    DATETIME     NOT NULL COMMENT '记录时间',

  -- ★ 二期上流程时的预留列：本期恒为 NULL。
  --   将来要上节点流转时直接填值即可，无需 ALTER TABLE（见设计文档第九章）。
  `node_code`      VARCHAR(50)      NULL COMMENT '【预留】二期流程化后的节点编码',
  `seq_no`         INT              NULL COMMENT '【预留】二期流程化后的节点顺序',

  PRIMARY KEY (`id`),
  KEY `idx_er_project` (`project_id`),
  KEY `idx_er_time` (`record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='提级论证审核意见/办理记录';

-- ---------------------------------------------------------------------------
-- 4) 建表自检
-- ---------------------------------------------------------------------------
SELECT TABLE_NAME AS 表名, TABLE_COMMENT AS 说明, TABLE_COLLATION AS 排序规则
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 't_escalation%'
ORDER BY TABLE_NAME;
