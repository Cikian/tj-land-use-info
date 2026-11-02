-- =============================================================================
-- 统计预警 · 预警等级阈值表（分级预警 D-603 第 ① 步）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 方案依据：2.3.1（五）市区两级项目预警展示；旧系统
--   ProcessStatusController.createSpecial L163-172 / ProcessWarningServiceImpl.getLevelForStage
--   —— 逾期天数落到哪个区间，就是哪个预警等级。
--
-- ★ 这张表是「分级预警」的分级依据：没有它，逾期只剩「逾期 / 逾期几天」，
--   有了它才有蓝 / 黄 / 橙 / 红四级与对应的处置力度。
--
-- ★★ 数据来源：旧库 nutzwk_ywk.process_waring_level（4 行，逐字平移）
--     旧 id（40 位）原样沿用，保证「旧库里 yjdjid 引用了哪个等级」这层语义
--     在新库里还能对上；旧表名把 warning 拼成了 waring，新表名改正为
--     warning，**列名同时改成英文**（旧列 yjdjmc/yjdjbm/yjdjzxz/yjdjzdz）：
--
--       旧列          → 新列        含义
--       yjdjmc        → level_name  预警等级名称（蓝色预警…）
--       yjdjbm        → level_code  预警等级别名（blue/yellow/orange/red，前端着色令牌）
--       yjdjzxz       → min_days    区间下界（含）
--       yjdjzdz       → max_days    区间上界（含）
--
--     改列名的理由：新系统的业务表一律用英文列名（t_process_configuration /
--     t_facility_process 均如此），拼音缩写只有原作者能读；且 level_code 是
--     前端着色的**语义令牌**，落在列名里让「改文字不改颜色」成为可能。
--     旧库仍在，字段对照写在这里，对账时按上表查即可。
--
-- ★★ 区间是 [1,10] / [11,20] / [21,30] / [31,100]（旧库实测，逐字照搬）
--     注意三点：
--     1. **从 1 开始，不是 0**：逾期 0 天（＝今天正好到期）不算预警。
--        所以匹配时先判 overdue_days >= 1，再落区间；0 天一律不生成预警。
--     2. **区间连续无空档**：10/11、20/21、30/31 首尾相接，不会出现
--        「逾期 25 天匹配不到等级」的空洞。
--     3. **上界 100 是软上限**：真实业务里逾期 120 天完全可能。匹配算法
--        （WarningLevelSupport#match）对「超过最大上界」的情况取最高等级并
--        标注 overflow，不返回 null —— 否则逾期越久反而没预警，恒不安全。
--        所以**不要**为了盖住 120 天就把红色上界改成 9999：改配置会让
--        「级别边界」失去业务含义，算法侧兜底才是正解。
--
-- 脚本可重复执行（先按固定 id 清理，再插入；主键固定所以不会重复）。
-- 执行后核对查询见文件末尾。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 建表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_process_warning_level` (
  `id`          varchar(64)  NOT NULL                COMMENT '预警等级ID（沿用旧库 process_waring_level.id）',
  `level_name`  varchar(50)  NOT NULL                COMMENT '预警等级名称：蓝色预警 / 黄色预警 / 橙色预警 / 红色预警',
  `level_code`  varchar(30)  NOT NULL                COMMENT '预警等级别名（前端着色令牌）：blue / yellow / orange / red',
  `min_days`    int(11)      NOT NULL DEFAULT 0      COMMENT '区间下界（含）：逾期工作日天数 >= min_days',
  `max_days`    int(11)      NOT NULL DEFAULT 0      COMMENT '区间上界（含）：逾期工作日天数 <= max_days',
  `sort_no`     int(11)      NOT NULL DEFAULT 0      COMMENT '排序号（升序＝由轻到重，列表与下拉按它排）',
  `remark`      varchar(255) DEFAULT NULL            COMMENT '备注',
  `create_by`   varchar(64)  DEFAULT NULL            COMMENT '创建人',
  `create_time` datetime     DEFAULT NULL            COMMENT '创建时间',
  `update_by`   varchar(64)  DEFAULT NULL            COMMENT '更新人',
  `update_time` datetime     DEFAULT NULL            COMMENT '更新时间',
  `del_flag`    tinyint(1)   NOT NULL DEFAULT 0      COMMENT '删除标识：0正常 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_pwl_range` (`min_days`, `max_days`),
  KEY `idx_pwl_sort` (`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预警等级阈值（分级预警：逾期天数 → 蓝/黄/橙/红），旧库 process_waring_level 平移';

-- ---------------------------------------------------------------------------
-- 2) 种子数据：4 个等级（旧库实测全量，逐字照搬区间与名称）
--    用 ON DUPLICATE KEY UPDATE 保证「重复执行 = 回到基线」，
--    而不是「重复执行 = 报主键冲突」。
--    ★ 中心的维护页（管理端「预警等级维护」）会改这四行；
--      重跑本脚本会把它们**恢复成基线**，这是有意的：脚本是基线，不是增量。
-- ---------------------------------------------------------------------------
INSERT INTO `t_process_warning_level`
  (`id`, `level_name`, `level_code`, `min_days`, `max_days`, `sort_no`, `remark`, `create_by`, `create_time`, `del_flag`)
VALUES
  ('4028879188adf4560188adf456880000',   '蓝色预警', 'blue',    1,  10,  1, '轻微逾期：督促责任部门核实进展、尽快办结', 'admin', NOW(), 0),
  ('2c948a8688ae44c60188ae44c6e20000',   '黄色预警', 'yellow', 11,  20,  2, '一般逾期：要求书面说明原因并明确办结时限',   'admin', NOW(), 0),
  ('2c948a8688ae44c60188ae45110a0001',   '橙色预警', 'orange', 21,  30,  3, '较久逾期：列入月度调度会督办事项',           'admin', NOW(), 0),
  ('2c948a8688ae44c60188ae4568c30002',   '红色预警', 'red',    31, 100,  4, '严重逾期：提级督办并报告分管领导',           'admin', NOW(), 0)
ON DUPLICATE KEY UPDATE
  `level_name` = VALUES(`level_name`),
  `level_code` = VALUES(`level_code`),
  `min_days`   = VALUES(`min_days`),
  `max_days`   = VALUES(`max_days`),
  `sort_no`    = VALUES(`sort_no`),
  `remark`     = VALUES(`remark`),
  `del_flag`   = 0;

-- ---------------------------------------------------------------------------
-- 3) 核对查询（应为 4 行、区间首尾相接、max_days 严格递增）
-- ---------------------------------------------------------------------------
SELECT `level_name`, `level_code`, `min_days`, `max_days`, `sort_no`
  FROM `t_process_warning_level`
 WHERE `del_flag` = 0
 ORDER BY `sort_no`;

-- 区间守卫：除了第一个等级的 min_days 应为 1，其余每个等级的 min_days
-- 必须等于上一等级的 max_days + 1。返回 0 行才算连续。
SELECT cur.`level_name` AS 当前等级, cur.`min_days` AS 当前下界,
       prev.`level_name` AS 上一等级, prev.`max_days` AS 上一上界
  FROM `t_process_warning_level` cur
  JOIN `t_process_warning_level` prev
    ON prev.`sort_no` = cur.`sort_no` - 1
   AND prev.`del_flag` = 0
 WHERE cur.`del_flag` = 0
   AND cur.`min_days` <> prev.`max_days` + 1;
