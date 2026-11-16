-- =============================================================================
-- 提级论证管理 · 历史项目类型「旧四类 → 市级/区级」映射通道（清单 P2-10 要求③）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 【为什么要单独一个脚本】
--   07_escalation_project_type_v2.sql 只做了「口径切换 + 存量置待确认」，
--   没有产出清单要求的「映射方案」，于是 12 行存量永远停在「待确认」，
--   每次统计都有一块说不清的份额。本脚本补的就是这一段：
--   把「业务确认」变成一个可填、可查、可回写、可审计的动作，而不是让开发逐条手改 SQL。
--
-- 【映射规则（写死在这里，避免以后有人"顺手"改成自动映射）】
--   1) 旧四类（产业类 / 基础设施类 / 民生类 / 其他）是**项目属性**，
--      市级 / 区级是**审批层级**——两者不是同一分类维度，任何"产业类→市级"的规则都是编造。
--   2) 因此本脚本**不做任何自动映射**，也不提供"按行政区划/申报单位推断"的默认值：
--      滨海新区的项目一样可能是市级项目，和平区的项目一样可能是区级项目。
--   3) 唯一权威来源是业务逐条确认（`t_escalation_project_type_confirm`），
--      确认一条回写一条，未确认的保持「待确认」。
--   4) 「待确认」不是业务分类，只能作为过渡值存在；统计口径里如需剔除，
--      用 `project_type <> '待确认'` 单独说明，不要把它并进市/区级任一侧。
--
-- 【已经不可逆的事实（必须告知使用人）】
--   07 脚本执行时只落了聚合值（基础设施类 6 / 民生类 3 / 产业类 2 / NULL 1），
--   **逐行原值没有保存**，所以本脚本的 `old_type` 列只能由业务凭项目名人工回填，
--   系统无法再还原。
--
-- 【幂等】
--   · 建表用 CREATE TABLE IF NOT EXISTS；
--   · 回写只动 `project_type = '待确认'` 且确认表里目标值为市/区级的行，
--     重复执行不会改写已经确认过的结果，也不会把已被人工改成市/区级的行再刷一遍。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 业务确认表（人工填写，本脚本只建表、不插数据）
--    ★ 为什么不直接改 t_escalation_project：确认过程要留「谁在什么时候把哪条改成什么」，
--      业务表只有 update_by/update_time，回答不了"改成之前是什么"。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_escalation_project_type_confirm` (
  `project_id`     varchar(32)  NOT NULL COMMENT 't_escalation_project.id',
  `project_name`   varchar(255)     NULL COMMENT '项目名称快照（便于业务核对，回写时不使用）',
  `old_type`       varchar(50)      NULL COMMENT '迁移前原值（人工回填；07 脚本已丢失逐行原值，未知则留空）',
  `target_type`    varchar(20)  NOT NULL COMMENT '确认结果：市级项目 / 区级项目',
  `confirmed_by`   varchar(64)      NULL COMMENT '确认人',
  `confirmed_time` datetime         NULL COMMENT '确认时间',
  `remark`         varchar(255)     NULL COMMENT '确认依据（如：立项批复文号、市局会议纪要）',
  PRIMARY KEY (`project_id`),
  KEY `idx_target_type` (`target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提级论证项目类型历史映射确认表（业务填写，P2-10）';

-- ---------------------------------------------------------------------------
-- 2) 待确认清单：现状 + 是否演示/联调数据
--    ★ 线上实测：12 行「待确认」里有 11 行是演示/联调数据（demo-ep-*、项目名 "123"/"12"），
--      只有 1 行指向真实宗地。业务只需确认真实项目；演示数据建议直接物理删除或保持待确认。
-- ---------------------------------------------------------------------------
SELECT p.`id`            AS 项目ID,
       p.`project_no`    AS 项目编号,
       p.`project_name`  AS 项目名称,
       p.`declare_dept`  AS 申报单位,
       p.`xzqh`          AS 行政区划,
       p.`crzdbh`        AS 出让宗地编号,
       p.`project_type`  AS 当前类型,
       CASE
         WHEN p.`id` LIKE 'demo-%'
           OR p.`project_name` IN ('123', '12')
           OR p.`project_name` REGEXP '^[0-9]+$' THEN '演示/联调数据'
         ELSE '需业务确认'
       END               AS 数据性质,
       c.`target_type`   AS 已确认结果
  FROM `t_escalation_project` p
  LEFT JOIN `t_escalation_project_type_confirm` c ON c.`project_id` = p.`id`
 WHERE p.`project_type` = '待确认'
 ORDER BY 数据性质 DESC, p.`project_no`;

-- ---------------------------------------------------------------------------
-- 3) 回写：把确认表的结果写回业务表（只动仍为「待确认」的行）
--    执行前先跑第 2 节，确认「需业务确认」行都已在确认表里有结论。
-- ---------------------------------------------------------------------------
UPDATE `t_escalation_project` p
  JOIN `t_escalation_project_type_confirm` c ON c.`project_id` = p.`id`
   SET p.`project_type` = c.`target_type`,
       p.`update_time`  = NOW()
 WHERE p.`project_type` = '待确认'
   AND c.`target_type` IN ('市级项目', '区级项目');

-- ---------------------------------------------------------------------------
-- 4) 核对
-- ---------------------------------------------------------------------------

-- 4.1 回写后分布：应只剩真实项目仍可能为「待确认」，且不再出现旧四类
SELECT `project_type` AS 项目类型, COUNT(*) AS 条数
  FROM `t_escalation_project`
 GROUP BY `project_type`
 ORDER BY 条数 DESC;

-- 4.2 仍未确认的真实项目（应为空；不为空说明业务还没确认完，属正常中间状态）
SELECT p.`id`, p.`project_no`, p.`project_name`
  FROM `t_escalation_project` p
  LEFT JOIN `t_escalation_project_type_confirm` c ON c.`project_id` = p.`id`
 WHERE p.`project_type` = '待确认'
   AND p.`id` NOT LIKE 'demo-%'
   AND p.`project_name` NOT IN ('123', '12');

-- 4.3 确认表与业务表不一致的行（人工改过业务表、但没更新确认表）
SELECT c.`project_id`, c.`target_type` AS 确认值, p.`project_type` AS 库内值
  FROM `t_escalation_project_type_confirm` c
  JOIN `t_escalation_project` p ON p.`id` = c.`project_id`
 WHERE p.`project_type` <> c.`target_type`;
