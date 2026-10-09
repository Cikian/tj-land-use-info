-- ============================================================================
--  审批流程（环节）配置表 t_process_configuration
--  依据：docs/升级改造工作内容清单.md 5.3.2「配套地块数据录入（1 宗地 N 配套
--        + 29 环节进度录入）」
--  旧表：nutzwk_ywk.process_configuration（30 行，实测全量已核）
--
--  ★ 为什么把这张「配置表」也搬进新库：
--    29 环节不是代码里的常量，而是**业务配置** —— 中心的审批事项会随政策调整
--    （旧库 2023-06-26 建的那批就有「中水工程移市」这种错别字，
--     以及「项目竣工」processTime=0 的异常值）。做成表，中心自己就能改；
--    做成枚举，改一个字就要重新发版。
--
--  ★ 结构实测结论（旧库 30 行）：
--    6 个「阶段」父节点（parent_id 为空、has_children=1）：
--      0001 立项用地规划许可阶段(20天) / 0002 工程建设许可阶段(30天)
--      0003 施工许可阶段(15天)       / 0004 项目施工阶段(60天)
--      0005 竣工验收许可阶段(15天)   / 0006 竣工移交许可阶段(15天)
--    24 个「事项」叶子节点（parent_id 指向阶段）—— 阶段 + 事项 = 30 行。
--    业务上说的「29 环节」= 页面按 6 阶段分组展示 24 个可填事项节点，
--    另有 5 个阶段汇总行由系统自动算出来（阶段合计 6，但最后两个阶段各含
--    一个非必填项，口径见 service 注释）。
--
--  ★ 相对旧库修正的两处（本次已修，注释留痕）：
--    1. 「中水工程移市」→「中水工程移交」（旧库存的是错别字）；
--    2. 「项目竣工」process_time 旧库为 0，这里补为 1（0 会让预警计算除零）。
--
--  字段名沿用旧库列名（含驼峰），便于与文档/旧数据对照；
--  只把审计字段规范化：delFlag(tinyint) 保留、补 create_by/create_time。
--
--  可重复执行：建表用 IF NOT EXISTS，种子数据用 INSERT ... ON DUPLICATE KEY UPDATE。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_process_configuration` (
  `id`            varchar(64)   NOT NULL                COMMENT '主键（沿用旧库 process_configuration.id）',
  `name`          varchar(100)      NULL                COMMENT '环节名称',
  `alias_name`    varchar(100)      NULL                COMMENT '环节别名（拼音首字母）',
  `note`          varchar(255)      NULL                COMMENT '环节介绍',
  `zgbm`          varchar(200)      NULL                COMMENT '主管部门',
  `location`      int               NULL                COMMENT '同级排序号',
  `parent_id`     varchar(64)       NULL                COMMENT '父环节ID（空 = 六大阶段之一）',
  `has_children`  tinyint(1)        NULL                COMMENT '是否有子节点',
  `process_time`  bigint            NULL                COMMENT '标准办理时长（天），用于算预计结束时间与预警',
  `path`          varchar(200)      NULL                COMMENT '树路径（0001 / 00010001，用于排序与层级判断）',
  `disabled`      tinyint(1)    NOT NULL DEFAULT 0      COMMENT '是否禁用：0启用 1停用',
  `is_pipeline`   tinyint(1)        NULL                COMMENT '是否管线流程',
  `is_parallel`   tinyint(1)        NULL                COMMENT '是否可并行办理',
  `create_by`     varchar(64)       NULL                COMMENT '创建人',
  `create_time`   datetime          NULL                COMMENT '创建时间',
  `update_by`     varchar(64)       NULL                COMMENT '更新人',
  `update_time`   datetime          NULL                COMMENT '更新时间',
  `del_flag`      tinyint(1)    NOT NULL DEFAULT 0      COMMENT '删除标识：0正常 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_pc_parent` (`parent_id`, `location`),
  KEY `idx_pc_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程（环节）配置：六大阶段 + 24 个事项';

-- ---------------------------------------------------------------------------
-- 种子数据：6 个阶段 + 24 个事项（共 30 行）
--
-- ★ 为什么用 INSERT ... SELECT 从旧库搬、而不是把 30 行硬编码进本脚本：
--   30 行 × 15 列手抄一遍，抄错一个主管部门或一个天数没人看得出来 ——
--   而这类配置错一个值，就会让某个环节的预计结束时间算错、预警提前或延后触发。
--   直接从旧表搬，保证与文档 §2.14/§4.5 逐行可核对。
--
-- ★ 迁移时顺手修掉的两处旧数据问题（见文件头的说明）：
--   1. 「中水工程移市」→「中水工程移交」（错别字）；
--   2. 「项目竣工」process_time 由 0 改为 1（0 会让预警的天数比值除零）。
--
-- ★ 空串归一化：旧库用 `parentId = ''` 表示「无父节点」（阶段行），
--   新系统统一用 NULL，这样「阶段 = parent_id IS NULL」这一条判据唯一且可走索引。
--   否则每个查询都要写 (parent_id IS NULL OR parent_id = '')，漏一处就出错。
--
-- 前置条件：执行本脚本的 MySQL 账号需对 nutzwk_ywk 有 SELECT 权限（本机 root 具备）。
-- 若目标环境拿不到旧库，可改用同目录 02b_t_process_configuration_data.sql
-- （那里是本脚本一次执行后的定值快照，内容等价，不依赖旧库）。
-- ---------------------------------------------------------------------------
INSERT INTO `t_process_configuration`
  (`id`, `name`, `alias_name`, `note`, `zgbm`, `location`, `parent_id`, `has_children`,
   `process_time`, `path`, `disabled`, `is_pipeline`, `is_parallel`,
   `create_by`, `create_time`, `del_flag`)
SELECT
  s.`id`,
  -- 修正 1：错别字
  CASE WHEN s.`name` = '中水工程移市' THEN '中水工程移交' ELSE s.`name` END,
  s.`aliasName`,
  s.`note`,
  s.`zgbm`,
  s.`location`,
  -- 空串 → NULL（阶段行）
  CASE WHEN s.`parentId` IS NULL OR s.`parentId` = '' THEN NULL ELSE s.`parentId` END,
  s.`hasChildren`,
  -- 修正 2：processTime = 0 → 1
  CASE WHEN s.`processTime` IS NULL OR s.`processTime` = 0 THEN 1 ELSE s.`processTime` END,
  s.`path`,
  IFNULL(s.`disabled`, 0),
  s.`isPipeLine`,
  s.`isParallel`,
  'admin', NOW(), IFNULL(s.`delFlag`, 0)
FROM `nutzwk_ywk`.`process_configuration` s
ON DUPLICATE KEY UPDATE
  `name`         = VALUES(`name`),
  `alias_name`   = VALUES(`alias_name`),
  `note`         = VALUES(`note`),
  `zgbm`         = VALUES(`zgbm`),
  `location`     = VALUES(`location`),
  `parent_id`    = VALUES(`parent_id`),
  `has_children` = VALUES(`has_children`),
  `process_time` = VALUES(`process_time`),
  `path`         = VALUES(`path`),
  `is_pipeline`  = VALUES(`is_pipeline`),
  `is_parallel`  = VALUES(`is_parallel`);

-- 兜底：万一旧库里阶段行的 parentId 是别的空白写法，这里再归一化一次
UPDATE `t_process_configuration` SET `parent_id` = NULL WHERE `parent_id` = '';

-- 核对：应输出 stages=6 / items=24 / total=30
SELECT SUM(CASE WHEN `parent_id` IS NULL THEN 1 ELSE 0 END) AS stages,
       SUM(CASE WHEN `parent_id` IS NOT NULL THEN 1 ELSE 0 END) AS items,
       COUNT(*) AS total
FROM `t_process_configuration` WHERE `del_flag` = 0;

-- 核对：六大阶段与其标准时长（供人工与文档 §2.14 对照）
SELECT `path`, `name` AS 阶段, `process_time` AS 标准时长天, `zgbm` AS 主管部门
FROM `t_process_configuration` WHERE `parent_id` IS NULL ORDER BY `location`;
