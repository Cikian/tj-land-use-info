-- =============================================================================
-- 统计预警 · 决策意见表（分级预警 D-603 第 ③ 步）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 业务含义：**问题类型 × 预警等级 → 处理建议**。
--   同样是逾期 25 天，「资金问题」和「管线问题」该找的部门、该做的事完全不同，
--   所以决策意见不是按等级一档一句话，而是按 (问题类型, 等级) 定位。
--   旧系统 ProcessStatusController.createSpecial L178-183 就是这么取的。
--
-- ★★ 与旧库的三处差别（都是有意的）
--   1) **用文本存问题类型，不用 sys_dict 的 id**
--      旧库 decision_opinion.wtlxid 指向 sys_dict 里「问题类型」字典项的 id，
--      取意见时先按名称查 dict 拿 id、再查意见表
--      （旧代码 `dictService.query(Cnd.where("name", "=", czwtlx)).get(0).getId()`
--       —— 这一行有两个坑：字典项重名时取第一条、字典项不存在时 get(0) 直接抛
--       IndexOutOfBounds，导致整个环节保存失败）。
--      新系统 t_facility_process.czwtlx 存的就是名称文本（字典 land_process_issue：
--      审批问题 / 资金问题 / 权属问题 / 管线问题 / 地质问题），
--      中间那层 id 只增加一次查库和两个失败点，没有任何收益 → 直接存名称。
--   2) **支持「通用意见」**：problem_type = ''（空串）表示「该等级下所有问题类型的
--      兜底意见」。匹配顺序是「精确 (类型, 等级) → 兜底 ('', 等级) → 无意见」。
--      旧系统没有兜底，配置漏一个 (类型, 等级) 组合，那一格就永远没有建议 ——
--      中心配了 5 类 × 4 级 = 20 格，漏一格就是一处静默失效。
--   3) **表名去掉了拼写错误**：旧库 decision_opinion 本身没拼错，新库加 t_ 前缀。
--
-- ★ 为什么 opinion 长度给到 1000：旧库 jcyj 是 varchar(1000)，
--   实际中心可能写一段处置流程（3~5 句），200 字经常不够。
--
-- ★★ 种子只给 4 行「通用意见」（每级一句，示例口径），**不给 20 格全量**：
--   20 格的具体话术是中心的业务口径，凭空编出来只会变成「看起来配了、
--   其实不对」的假数据。给 4 行兜底，中心就能拿到「还没细配也不至于空着」的
--   体验，再按需要逐格细化（管理端「决策意见维护」页）。
-- =============================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `t_decision_opinion` (
  `id`           varchar(64)   NOT NULL COMMENT '决策意见ID',
  `problem_type` varchar(50)   NOT NULL DEFAULT '' COMMENT '问题类型（口径同字典 land_process_issue）；空串 = 通用兜底意见',
  `level_id`     varchar(64)   NOT NULL COMMENT '预警等级ID → t_process_warning_level.id',
  `level_name`   varchar(50)   DEFAULT NULL COMMENT '预警等级名称（冗余，列表直接展示免联表）',
  `opinion`      varchar(1000) DEFAULT NULL COMMENT '决策意见：该问题类型在该等级下的处理建议',
  `sort_no`      int(11)       NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_by`    varchar(64)   DEFAULT NULL COMMENT '创建人',
  `create_time`  datetime      DEFAULT NULL COMMENT '创建时间',
  `update_by`    varchar(64)   DEFAULT NULL COMMENT '更新人',
  `update_time`  datetime      DEFAULT NULL COMMENT '更新时间',
  `del_flag`     tinyint(1)    NOT NULL DEFAULT 0 COMMENT '删除标识：0正常 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_do_match` (`problem_type`, `level_id`, `del_flag`),
  KEY `idx_do_level` (`level_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策意见（问题类型 × 预警等级 → 处理建议）';

-- ---------------------------------------------------------------------------
-- 种子：4 条通用意见（problem_type = ''，每级一条）
--   ★ 话术是「示例口径」，中心可在管理端「决策意见维护」里改；
--     重跑本脚本会把它恢复成示例，属基线行为（与预警等级表一致）。
-- ---------------------------------------------------------------------------
INSERT INTO `t_decision_opinion`
  (`id`, `problem_type`, `level_id`, `level_name`, `opinion`, `sort_no`, `create_by`, `create_time`, `del_flag`)
VALUES
  ('8c2e5d7a1b3f4c6d9e0a2b4cf001', '', '4028879188adf4560188adf456880000', '蓝色预警',
   '已发生逾期，请责任部门核实该环节进展，督促承办单位尽快办结并补录实际完成时间。', 1, 'admin', NOW(), 0),
  ('8c2e5d7a1b3f4c6d9e0a2b4cf002', '', '2c948a8688ae44c60188ae44c6e20000', '黄色预警',
   '逾期已超半月，请责任部门书面说明原因与堵点，明确办结时限并报分管领导。', 2, 'admin', NOW(), 0),
  ('8c2e5d7a1b3f4c6d9e0a2b4cf003', '', '2c948a8688ae44c60188ae45110a0001', '橙色预警',
   '逾期较久，建议列入月度调度会督办事项，由主管部门牵头协调解决堵点问题。', 3, 'admin', NOW(), 0),
  ('8c2e5d7a1b3f4c6d9e0a2b4cf004', '', '2c948a8688ae44c60188ae4568c30002', '红色预警',
   '严重逾期，建议提级督办并启动问责，报市局分管领导专题研究处理意见。', 4, 'admin', NOW(), 0)
ON DUPLICATE KEY UPDATE
  `problem_type` = VALUES(`problem_type`),
  `level_id`     = VALUES(`level_id`),
  `level_name`   = VALUES(`level_name`),
  `opinion`      = VALUES(`opinion`),
  `sort_no`      = VALUES(`sort_no`),
  `del_flag`     = 0;

-- ---------------------------------------------------------------------------
-- 核对查询
--   3.1 应输出 4 行通用意见，且每行都能对上预警等级表（等级名称非空）
-- ---------------------------------------------------------------------------
SELECT o.`problem_type` AS 问题类型, o.`level_name` AS 等级, l.`min_days`, l.`max_days`, o.`opinion`
  FROM `t_decision_opinion` o
  LEFT JOIN `t_process_warning_level` l ON l.`id` = o.`level_id` AND l.`del_flag` = 0
 WHERE o.`del_flag` = 0
 ORDER BY o.`sort_no`;

--   3.2 孤儿检查：指向不存在等级的决策意见（应为 0 行）
SELECT o.`id`, o.`problem_type`, o.`level_id`
  FROM `t_decision_opinion` o
  LEFT JOIN `t_process_warning_level` l ON l.`id` = o.`level_id`
 WHERE o.`del_flag` = 0 AND l.`id` IS NULL;
