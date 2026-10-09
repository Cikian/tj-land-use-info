-- ============================================================================
--  经营性用地批量导入 · 导入日志表 t_land_import_log
--  依据：docs/升级改造工作内容清单.md 5.3.3「经营性用地批量导入管理」的日志要求
--
--  ★ 为什么要单独建这张表，而不是复用 jeecg 的 sys_log：
--    sys_log 只记「谁在什么时候调了哪个接口」，记不下「本次解析 800 行、
--    新增 780 条、20 条错误、错误明细是什么」这类批量导入特有的信息。
--    而中心在核对「这批 800 条到底进去没有、哪几条没进去」时，
--    要的恰恰是后者。旧系统是用 xj_kjkfb_operationrecord 记一句
--    「添加了 N 条数据」——信息量不够，且与其它操作日志混在一起。
--
--  ★ 字段说明：
--    total_rows       解析到的数据行数（不含表头/空行）
--    inserted_rows    实际新增条数
--    updated_rows     实际覆盖更新条数（策略 = update 时）
--    skipped_rows     因重复宗地编号被跳过的行数（策略 = skip 时）
--    error_rows       错误条数
--    duplicate_strategy  本次使用的重复处理策略：reject / skip / update
--    skip_error_rows  是否勾选了「跳过错误行」
--    aborted          是否因存在错误而整批未入库
--    error_summary    错误摘要（前 5 条，截断到 2000 字）
--
--  可重复执行：建表用 IF NOT EXISTS。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_land_import_log` (
  `id`                 varchar(32)   NOT NULL                COMMENT '主键',
  `file_name`          varchar(255)      NULL                COMMENT '上传文件名',
  `file_size`          bigint            NULL                COMMENT '文件字节数',
  `total_rows`         int               NULL                COMMENT '解析到的数据行数',
  `inserted_rows`      int               NULL                COMMENT '实际新增条数',
  `updated_rows`       int               NULL                COMMENT '实际覆盖更新条数',
  `skipped_rows`       int               NULL                COMMENT '因重复被跳过的行数',
  `error_rows`         int               NULL                COMMENT '校验错误条数',
  `duplicate_strategy` varchar(20)       NULL                COMMENT '重复处理策略：reject/skip/update',
  `skip_error_rows`    tinyint(1)        NULL                COMMENT '是否勾选跳过错误行',
  `aborted`            tinyint(1)        NULL                COMMENT '是否因错误整批未入库',
  `error_summary`      varchar(2000)     NULL                COMMENT '错误摘要（前 5 条）',
  `operator`           varchar(64)       NULL                COMMENT '操作人账号',
  `operator_name`      varchar(64)       NULL                COMMENT '操作人姓名',
  `create_time`        datetime          NULL                COMMENT '导入时间',
  PRIMARY KEY (`id`),
  KEY `idx_land_import_log_time` (`create_time`),
  KEY `idx_land_import_log_operator` (`operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经营性用地批量导入日志';

-- 核对：建表后应输出 1 行
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 't_land_import_log';
