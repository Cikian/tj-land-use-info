-- =============================================================================
-- 统计预警 · 预警记录表（分级预警 D-603 第 ④ 步：落库供查询/导出）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 旧系统对应表：nutzwk_ywk.process_warning（13 行）。
-- ★★ 旧库那 13 行**一行都不迁**：内容是
--    「龙华道（辰永路-辰永东路）预警分析 / 阿纳海姆 / 冯布朗 / 月神三号炸了」
--    这类演示数据（旧库 process_status 同样是演示数据）。
--    迁进来只会让中心的预警列表一打开就是假数据。
--    设计文档把 process_* 列为「测试数据残留，迁移时整表丢弃」，
--    所以本脚本只建表、不插数据 —— 数据由算法在环节录入 / 手动重算时生成。
--
-- ★ 与旧表的列对照（旧列 → 新列）
--   旧列        → 新列             说明
--   yjmc        → yj_name          「{配套项目名}预警分析」
--   yjfl        → yj_type          环节预警 / 阶段预警 / 总体预警
--   yjdjid      → level_id         预警等级ID（→ t_process_warning_level.id）
--   yjz         → yj_value         预警值（比率，1.1000 = 110%）
--   jcyj        → decision_opinion 决策意见（→ t_decision_opinion.opinion）
--   lcztid      → lc_id            环节/阶段ID；**总体预警为空串**
--   zrbm        → zrbm             责任部门（取配置表 zgbm，保留拼音列名：见下）
--   yjxq        → problem_type + problem_detail（拆成结构化两列，见下）
--
-- ★ 为什么拆 yjxq：旧库的 yjxq 是一句拼装文本
--   「问题类型：管线问题具体情况是:123」—— 想按问题类型统计或筛选只能靠
--   LIKE '问题类型：%'，且冒号全半角一变就漏。新库拆成问题类型（可筛选、
--   可关联决策意见）+ 具体情况（自由文本），拼装展示交给前端。
--
-- ★ 为什么 zrbm 保留拼音列名：它和 t_process_configuration.zgbm
--   （主管部门）是同一套业务口径、同一批取值，两处都叫 zrbm/zgbm 在
--   对账 SQL 里可以直接 JOIN 比对；改名反而要写映射注释。
--
-- ★★ 唯一键 uk_pw_key (pt_id, yj_type, lc_id)：一个配套项目的一个环节
--   （或一个阶段 / 该项目的总体）**只允许一条当前预警**。
--   重算时按这个键 upsert：等级降了是更新，不再逾期是删除。
--   旧系统没有这个约束（先 query 再 insert/update，且分支写漏了一处），
--   后果是同一环节能堆出多条预警，列表里重复、导出里重复计数。
--   lc_id 用 NOT NULL DEFAULT '' 而不是 NULL：MySQL 唯一键允许多个 NULL，
--   若总体预警的 lc_id 允许 NULL，「总体预警」这一行就能被插无数次。
--
-- ★ yj_value 用 decimal(10,4) 而不是旧库的 decimal(10,2)：
--   旧库 2 位小数把 1.105 存成 1.11，展示时再 ×100 得 111.0%，
--   与真实 110.5% 差 0.5 个点；重算比对时还会因为四舍五入抖动误判「值变了」。
--   4 位小数足够（比率精度 0.01%），展示统一四舍五入到 1 位。
-- =============================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `t_process_warning` (
  `id`               varchar(64)   NOT NULL                COMMENT '预警ID',
  `yj_name`          varchar(200)  NOT NULL                COMMENT '预警名称：{配套项目名称}预警分析（与旧系统口径一致）',
  `yj_type`          varchar(20)   NOT NULL                COMMENT '预警分类：环节预警 / 阶段预警 / 总体预警',
  `pt_id`            varchar(64)   NOT NULL                COMMENT '配套项目ID → t_supporting_facilities.id',
  `pt_name`          varchar(200)  DEFAULT NULL            COMMENT '配套项目名称（冗余快照：改名后预警仍显示当时名称）',
  `crzdbh`           varchar(100)  DEFAULT NULL            COMMENT '出让宗地编号（冗余，按宗地下钻用）',
  `xzqh`             varchar(50)   DEFAULT NULL            COMMENT '行政区划（冗余，取 t_land.xzqh；区级项目再带上 xzqh2 供区级口径筛选）',
  `xzqh2`            varchar(50)   DEFAULT NULL            COMMENT '录入单位/区级区划（冗余，取 t_land.xzqh2）',
  `dkmc`             varchar(200)  DEFAULT NULL            COMMENT '地块名称（冗余快照）',
  `ptxmmc`           varchar(200)  DEFAULT NULL            COMMENT '配套项目名称（与 pt_name 同值，留独立列是为了和旧接口入参 ptxmmc 对齐）',
  `lc_id`            varchar(64)   NOT NULL DEFAULT ''     COMMENT '环节/阶段ID（→ t_process_configuration.id）；总体预警为空串',
  `lc_name`          varchar(100)  DEFAULT NULL            COMMENT '环节/阶段名称（冗余快照）',
  `stage_id`         varchar(64)   DEFAULT NULL            COMMENT '所属阶段ID（环节预警用）',
  `level_id`         varchar(64)   DEFAULT NULL            COMMENT '预警等级ID → t_process_warning_level.id',
  `level_code`       varchar(30)   DEFAULT NULL            COMMENT '预警等级别名（冗余：blue/yellow/orange/red，前端着色直接用）',
  `level_name`       varchar(50)   DEFAULT NULL            COMMENT '预警等级名称（冗余）',
  `yj_value`         decimal(10,4) DEFAULT NULL            COMMENT '预警值（比率）：1.1000 = 110%，展示时 ×100 保留 1 位',
  `overdue_days`     int(11)       NOT NULL DEFAULT 0      COMMENT '逾期天数（工作日，已剔除周末与节假日）',
  `std_days`         int(11)       DEFAULT NULL            COMMENT '标准办理时长（工作日）= 预警值的分母，留档便于复核',
  `actual_days`      int(11)       DEFAULT NULL            COMMENT '实际历时（工作日）= 预警值的分子，留档便于复核',
  `problem_type`     varchar(50)   DEFAULT NULL            COMMENT '存在问题类型（口径同 t_facility_process.czwtlx / 字典 land_process_issue）',
  `problem_detail`   varchar(1000) DEFAULT NULL            COMMENT '具体情况（自由文本，取 t_facility_process.jtwt）',
  `decision_opinion` varchar(1000) DEFAULT NULL            COMMENT '决策意见（按 问题类型 × 预警等级 从 t_decision_opinion 取，可为空）',
  `zrbm`             varchar(200)  DEFAULT NULL            COMMENT '责任部门（取环节配置 zgbm）',
  `calc_time`        datetime      DEFAULT NULL            COMMENT '本次计算时间（时效提示：预警是「重算出来的」，不是事件流）',
  `create_by`        varchar(64)   DEFAULT NULL            COMMENT '创建人',
  `create_time`      datetime      DEFAULT NULL            COMMENT '创建时间',
  `update_by`        varchar(64)   DEFAULT NULL            COMMENT '更新人',
  `update_time`      datetime      DEFAULT NULL            COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_key` (`pt_id`, `yj_type`, `lc_id`),
  KEY `idx_pw_type` (`yj_type`, `level_id`),
  KEY `idx_pw_level` (`level_id`),
  KEY `idx_pw_crzdbh` (`crzdbh`),
  KEY `idx_pw_xzqh` (`xzqh`),
  KEY `idx_pw_calc` (`calc_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分级预警记录（环节/阶段/总体三种口径，含预警等级、预警值、决策意见）';

-- 核对查询：应为空表（旧库演示数据不迁）；若历史环境已跑过重算，这里会 > 0，属正常。
SELECT COUNT(*) AS 预警记录数 FROM `t_process_warning`;
