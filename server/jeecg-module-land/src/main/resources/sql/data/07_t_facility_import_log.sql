-- ============================================================================
--  配套信息批量导入 · 导入日志表 t_facility_import_log
--  依据：docs/升级改造工作内容清单.md 5.3.4「配套信息批量导入管理」的日志要求
--
--  ★ 为什么单独建这张表，而不是复用 t_land_import_log：
--    两者的判重口径不同（宗地按编号、配套按「宗地编号 + 配套项目名称」），
--    而且配套导入多两个只有它才有的信息 —— 孤儿行数与「是否允许孤儿」。
--    合成一张表就得靠 biz_type 区分，查「这批配套有多少条挂不上宗地」时
--    要先筛业务类型再筛孤儿列，表长起来以后每次看记录都要多想一层。
--    旧系统是用 xj_kjkfb_operationrecord 记一句「添加了 N 条数据」——
--    信息量不够，且与其它操作日志混在一起。
--
--  ★ 字段说明（与 t_land_import_log 完全同名同义，只有最后两列是本表新增）：
--    total_rows       解析到的数据行数（不含表头/空行）
--    inserted_rows    实际新增条数
--    updated_rows     实际覆盖更新条数（策略 = update 时）
--    skipped_rows     因「宗地编号 + 配套项目名称」重复被跳过的行数（策略 = skip 时）
--    error_rows       错误条数（★ 孤儿在 allow_orphan=1 时不计入错误）
--    duplicate_strategy  本次使用的重复处理策略：reject / skip / update
--    skip_error_rows  是否勾选了「跳过错误行」
--    aborted          是否因存在错误而整批未入库
--    orphan_rows      ★ 本表新增：孤儿行数（出让宗地编号在 t_land 里查不到的行数）
--    allow_orphan     ★ 本表新增：是否勾选了「允许挂到未登记宗地（记入孤儿清单）」
--    error_summary    摘要（孤儿行数 + 前 5 条错误，截断到 2000 字）
--
--  可重复执行：建表用 IF NOT EXISTS。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_facility_import_log` (
  `id`                 varchar(32)   NOT NULL                COMMENT '主键',
  `file_name`          varchar(255)      NULL                COMMENT '上传文件名',
  `file_size`          bigint            NULL                COMMENT '文件字节数',
  `total_rows`         int               NULL                COMMENT '解析到的数据行数',
  `inserted_rows`      int               NULL                COMMENT '实际新增条数',
  `updated_rows`       int               NULL                COMMENT '实际覆盖更新条数',
  `skipped_rows`       int               NULL                COMMENT '因重复被跳过的行数',
  `error_rows`         int               NULL                COMMENT '校验错误条数（不含孤儿）',
  `duplicate_strategy` varchar(20)       NULL                COMMENT '重复处理策略：reject/skip/update',
  `skip_error_rows`    tinyint(1)        NULL                COMMENT '是否勾选跳过错误行',
  `aborted`            tinyint(1)        NULL                COMMENT '是否因错误整批未入库',
  `orphan_rows`        int               NULL                COMMENT '孤儿行数：出让宗地编号在 t_land 中查不到的行数',
  `allow_orphan`       tinyint(1)        NULL                COMMENT '是否勾选允许挂到未登记宗地（记入孤儿清单）',
  `error_summary`      varchar(2000)     NULL                COMMENT '摘要（孤儿行数 + 前 5 条错误）',
  `operator`           varchar(64)       NULL                COMMENT '操作人账号',
  `operator_name`      varchar(64)       NULL                COMMENT '操作人姓名',
  `create_time`        datetime          NULL                COMMENT '导入时间',
  PRIMARY KEY (`id`),
  KEY `idx_facility_import_log_time` (`create_time`),
  KEY `idx_facility_import_log_operator` (`operator`),
  KEY `idx_facility_import_log_orphan` (`orphan_rows`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配套信息批量导入日志';

-- ---------------------------------------------------------------------------
-- 核对
-- ---------------------------------------------------------------------------

-- 核对 1：建表后应输出 1 行
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 't_facility_import_log';

-- 核对 2：应输出 17 行，且 orphan_rows / allow_orphan 都在其中
--        （若输出 0 行或行数不足，说明建表脚本没执行完整）
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 't_facility_import_log'
ORDER BY ORDINAL_POSITION;

-- 核对 3：应输出 0 行 —— 与宗地导入日志表 t_land_import_log 逐列比对，
--        本表必须**恰好**多出 orphan_rows / allow_orphan 两列（15 + 2 = 17）。
--
--        ★ 初版这条查询写错了：它在「t_facility_import_log 里存在、t_land_import_log
--          里不存在」的分支上，用 IN (...) 排除的是**本表新增列**，
--          而没排除「两表本来同名同义的 15 列」—— 于是把 15 列全部报成「多出列」，
--          看起来像建表脚本错得一塌糊涂，实际那个库连 t_land_import_log 都还没建。
--          现在改成先各自取列清单，再做双向差集。
-- ---------------------------------------------------------------------------

-- 前置：t_land_import_log 必须已存在（先执行 sql/land/02_t_land_import_log.sql）
SELECT IF(COUNT(*) = 1, '前置表 t_land_import_log 已存在', '★缺少前置表 t_land_import_log，请先执行 sql/land/02_t_land_import_log.sql') AS 前置检查
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 't_land_import_log';

-- 差异清单：只在发现**预期之外**的差异时才有行
--   预期：本表比 t_land_import_log 多 orphan_rows、allow_orphan 两列，其余列名完全一致
SELECT '★缺少列（t_land_import_log 有、本表没有，且不属于本表新增列）' AS 问题, l.COLUMN_NAME AS 差异项
FROM information_schema.COLUMNS l
WHERE l.TABLE_SCHEMA = (SELECT DATABASE()) AND l.TABLE_NAME = 't_land_import_log'
  AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS f
         WHERE f.TABLE_SCHEMA = l.TABLE_SCHEMA AND f.TABLE_NAME = 't_facility_import_log'
           AND f.COLUMN_NAME = l.COLUMN_NAME)
UNION ALL
SELECT '★多出列（本表有、t_land_import_log 没有，且不是预期新增列）', f.COLUMN_NAME
FROM information_schema.COLUMNS f
WHERE f.TABLE_SCHEMA = (SELECT DATABASE()) AND f.TABLE_NAME = 't_facility_import_log'
  AND f.COLUMN_NAME NOT IN ('orphan_rows', 'allow_orphan')
  AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS l
         WHERE l.TABLE_SCHEMA = f.TABLE_SCHEMA AND l.TABLE_NAME = 't_land_import_log'
           AND l.COLUMN_NAME = f.COLUMN_NAME)
UNION ALL
-- 预期新增的两列必须真的在
SELECT '★预期新增列缺失', 'orphan_rows'
WHERE NOT EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 't_facility_import_log'
         AND COLUMN_NAME = 'orphan_rows')
UNION ALL
SELECT '★预期新增列缺失', 'allow_orphan'
WHERE NOT EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 't_facility_import_log'
         AND COLUMN_NAME = 'allow_orphan');

-- 核对 4：两表列数应分别为 15 与 17
SELECT TABLE_NAME AS 表名, COUNT(*) AS 列数,
       CASE TABLE_NAME WHEN 't_land_import_log' THEN 15 WHEN 't_facility_import_log' THEN 17 END AS 预期列数,
       CASE WHEN COUNT(*) = (CASE TABLE_NAME WHEN 't_land_import_log' THEN 15 ELSE 17 END)
            THEN '正常' ELSE '★异常' END AS 判定
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = (SELECT DATABASE())
  AND TABLE_NAME IN ('t_land_import_log', 't_facility_import_log')
GROUP BY TABLE_NAME ORDER BY TABLE_NAME;
