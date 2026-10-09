-- ============================================================================
--  数据变更留痕表 t_data_change_log
--  依据：docs/升级改造工作内容清单.md 5.3「数据更新与移除（软删/恢复/变更留痕）」
--        + 方案 2.3.1（三）第 6 项「数据更新与移除」
--  旧实现：nutzwk_ywk.xj_kjkfb_operationrecord（786 行）
--
--  ★ 旧日志表为什么不够（实测结论，逐条对应本表设计）：
--    旧表只有 6 列：specialName(操作名称) / operateTime(**字符串**) /
--    operatePeople(**786 行全是「普通用户」**) / bz / loginName。
--    问题：
--      1. 只记「添加了 N 条数据」这种一句话，**记不下改了哪个字段、改前改后是什么**，
--         事后想回答「这个出让金是谁从 1.2 改成 2.0 的」根本查不出来；
--      2. `operateTime` 是字符串（`2024/10/15 10:15:03`），按时间范围筛要先做字符串
--         函数转换，无法走索引；
--      3. `operatePeople` 恒为「普通用户」，等于没有记录操作人（真正的操作人在
--         loginName 里，但那是账号不是姓名，导出给人看时要再联一次用户表）；
--      4. 没有业务主键列，无法回答「这一条宗地的完整履历」。
--    本表因此按「业务对象 + 动作 + 字段级差异 + 操作人 + 时间」重新设计。
--
--  ★ 与导入日志 t_land_import_log 的分工（两张表都要有，不是重复）：
--    t_land_import_log  —— 一次**批量导入**的汇总（解析多少行/新增多少条/错了多少条）
--    t_data_change_log  —— **单条数据**的每一次字段级变更（含批量导入写进去的每一条）
--    前者回答「这批导入怎么样」，后者回答「这条数据经历了什么」。
--
--  ★ 变更明细的存储形态：`change_detail` 存 JSON 数组
--      [{"field":"crj","label":"出让金（亿元）","before":"1.20","after":"2.00"}, ...]
--    存 JSON 而不是「每个字段一行」的理由：一条记录的变更天然是一次操作产生的一组值，
--    拆成多行会让「一次修改」在列表里散成七八行，反而看不出是一次操作。
--    查「某字段被谁改过」时用 JSON_CONTAINS / LIKE 兜底即可（本表数据量很小）。
--
--  可重复执行：建表用 IF NOT EXISTS。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_data_change_log` (
  `id`            varchar(64)   NOT NULL                COMMENT '主键',
  `biz_type`      varchar(32)   NOT NULL                COMMENT '业务类型：land 宗地 / facility 配套项目 / process 环节进度 / attachment 附件',
  `biz_id`        varchar(100)  NOT NULL                COMMENT '业务主键',
  `biz_key`       varchar(200)      NULL                COMMENT '业务可读键（宗地编号 / 配套项目名称），列表直接展示',
  `action`        varchar(30)   NOT NULL                COMMENT '动作：CREATE 新增 / UPDATE 修改 / DELETE 移除 / RESTORE 恢复 / IMPORT 批量导入 / UPLOAD 附件上传',
  `change_summary` varchar(500)      NULL                COMMENT '一句话摘要（列表展示，不点开也能看懂）',
  `change_detail`  text              NULL                COMMENT '字段级变更明细 JSON：[{"field","label","before","after"}]',
  `change_count`   int          NOT NULL DEFAULT 0      COMMENT '本次变更的字段个数',
  `operator`      varchar(64)       NULL                COMMENT '操作人账号',
  `operator_name` varchar(64)       NULL                COMMENT '操作人姓名',
  `operator_ip`   varchar(64)       NULL                COMMENT '操作 IP',
  `create_time`   datetime          NULL                COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_dcl_biz` (`biz_type`, `biz_id`, `create_time`),
  KEY `idx_dcl_time` (`create_time`),
  KEY `idx_dcl_action` (`action`),
  KEY `idx_dcl_operator` (`operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据变更留痕（字段级，含软删与恢复）';

-- 核对：建表后应输出 1 行
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 't_data_change_log';
