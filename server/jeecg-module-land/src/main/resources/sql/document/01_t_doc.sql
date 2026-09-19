-- ============================================================================
--  收发文管理表族（方案 2.3.2 第 9 项）
--  依据：docs/升级改造工作内容清单.md 6.3.9（5）自建表设计 + docs/_tj_sfw_business_report.md
--
--  ★ 为什么不用旧 tj-sfw 的 xj_filemanage_* 三表（R1–R22）：
--    ① 所有 @RequiresPermissions 被注释 → 零鉴权；
--    ② save()/updateById() 的流转方法体被整段注释 → 流转链永远是断的；
--    ③ rejectFlow() 里 getLastFlow() 返回 null 未判空 → 必 NPE；
--    ④ 附件只存一个逗号分隔的 file_path 字段，没有附件表；
--    ⑤ 字段名语义反置（fileReceiveNum 实际是发文文号）；
--    ⑥ 无 del_flag 逻辑删除、无文号自动生成。
--    旧表保留不动（不删），其历史数据由 03_migrate_legacy_filemanage.sql 迁入本表族。
--
--  ★ 流转设计（经确认采用「精简三级流转」，逻辑参考旧系统但做了合理化优化）：
--      登记 → 承办 → 办结，中间支持「转办」（换承办人）与「退回」（退回来文登记人）。
--      与旧系统 file_status 语义保持一致，便于历史数据对照：
--        1 = 待承办（旧：待流转）  2 = 承办中（旧：流转中）
--        3 = 已退回                4 = 已办结
--      旧系统的两个缺陷已修正：
--        · 新增收文时立即写入第一条流转记录并把 current_handler 指向承办人（旧系统被注释掉了）；
--        · 转办/退回时先在事务内推进流转记录、再更新主表状态（旧系统无 @Transactional）。
--
--  可重复执行：建表 IF NOT EXISTS。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 收文
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_doc_receive` (
  `id`                varchar(36)   NOT NULL                COMMENT '主键',
  `doc_no`            varchar(64)   NOT NULL                COMMENT '收文登记号（默认 SW-{yyyy}-{4位}，允许手工改写）',
  `doc_title`         varchar(500)  NOT NULL                COMMENT '文件标题',
  `doc_type`          varchar(50)       NULL                COMMENT '文件类型：通知/函/批复/报告/其他',
  `from_dept`         varchar(200)      NULL                COMMENT '来文单位',
  `from_doc_no`       varchar(100)      NULL                COMMENT '来文字号',
  `receive_date`      date              NULL                COMMENT '收文（来文）日期',
  `urgency`           varchar(20)       NULL                COMMENT '紧急程度：普通/急件/特急',
  `secret_level`      varchar(20)       NULL                COMMENT '密级：一般/内部/秘密/机密',
  `page_count`        int               NULL                COMMENT '页数',
  `copies`            int               NULL                COMMENT '份数',
  -- 关联业务（先选宗地，再联动选配套项目）
  `land_id`           varchar(64)       NULL                COMMENT '出让宗地ID → t_land.id',
  `crzdbh`            varchar(100)      NULL                COMMENT '出让宗地编号（冗余）',
  `facility_id`       varchar(100)      NULL                COMMENT '配套项目ID',
  `ptxmmc`            varchar(100)      NULL                COMMENT '配套项目名称（冗余）',
  -- 流转
  `status`            varchar(20)   NOT NULL DEFAULT '待承办' COMMENT '待承办/承办中/已退回/已办结/已归档',
  `current_handler`   varchar(64)       NULL                COMMENT '当前处理人账号',
  `current_handler_name` varchar(64)    NULL                COMMENT '当前处理人姓名',
  `handle_deadline`   date              NULL                COMMENT '办理期限',
  `finish_time`       datetime          NULL                COMMENT '办结时间',
  `finish_opinion`    varchar(2000)     NULL                COMMENT '办结说明',
  `archive_id`        varchar(36)       NULL                COMMENT '归档后关联档案ID → t_archive.id',
  `remark`            varchar(1000)     NULL                COMMENT '备注',
  `del_flag`          tinyint(1)    NOT NULL DEFAULT 0      COMMENT '删除状态',
  `create_by`         varchar(64)       NULL                COMMENT '创建人',
  `create_time`       datetime          NULL                COMMENT '创建时间',
  `update_by`         varchar(64)       NULL                COMMENT '更新人',
  `update_time`       datetime          NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_no` (`doc_no`, `del_flag`),
  KEY `idx_dr_status` (`status`),
  KEY `idx_dr_handler` (`current_handler`),
  KEY `idx_dr_facility` (`facility_id`),
  KEY `idx_dr_crzdbh` (`crzdbh`),
  KEY `idx_dr_date` (`receive_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收文';

-- ---------------------------------------------------------------------------
-- 发文（方案原文：「发文的信息记录」——纯台账，无流转）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_doc_send` (
  `id`            varchar(36)   NOT NULL                COMMENT '主键',
  `doc_no`        varchar(64)   NOT NULL                COMMENT '发文登记号（默认 FW-{yyyy}-{4位}，允许手工改写）',
  `doc_title`     varchar(500)  NOT NULL                COMMENT '文件标题',
  `doc_type`      varchar(50)       NULL                COMMENT '文件类型：通知/函/批复/报告/其他',
  `to_dept`       varchar(500)      NULL                COMMENT '主送单位',
  `cc_dept`       varchar(500)      NULL                COMMENT '抄送单位',
  `issue_date`    date              NULL                COMMENT '发文日期',
  `signer`        varchar(64)       NULL                COMMENT '签发人',
  `drafter`       varchar(64)       NULL                COMMENT '拟稿人',
  `secret_level`  varchar(20)       NULL                COMMENT '密级',
  `urgency`       varchar(20)       NULL                COMMENT '紧急程度',
  `copies`        int               NULL                COMMENT '份数',
  -- 关联业务
  `land_id`       varchar(64)       NULL                COMMENT '出让宗地ID',
  `crzdbh`        varchar(100)      NULL                COMMENT '出让宗地编号（冗余）',
  `facility_id`   varchar(100)      NULL                COMMENT '配套项目ID',
  `ptxmmc`        varchar(100)      NULL                COMMENT '配套项目名称（冗余）',
  `archive_id`    varchar(36)       NULL                COMMENT '归档后关联档案ID',
  `remark`        varchar(1000)     NULL                COMMENT '备注',
  `del_flag`      tinyint(1)    NOT NULL DEFAULT 0      COMMENT '删除状态',
  `create_by`     varchar(64)       NULL                COMMENT '创建人',
  `create_time`   datetime          NULL                COMMENT '创建时间',
  `update_by`     varchar(64)       NULL                COMMENT '更新人',
  `update_time`   datetime          NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ds_no` (`doc_no`, `del_flag`),
  KEY `idx_ds_facility` (`facility_id`),
  KEY `idx_ds_crzdbh` (`crzdbh`),
  KEY `idx_ds_date` (`issue_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发文';

-- ---------------------------------------------------------------------------
-- 收文流转记录（「收文中心内的流转」）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_doc_receive_flow` (
  `id`           varchar(36)   NOT NULL                COMMENT '主键',
  `doc_id`       varchar(36)   NOT NULL                COMMENT '收文ID → t_doc_receive.id',
  `node_name`    varchar(100)      NULL                COMMENT '流转节点：登记/承办/退回/办结',
  `action`       varchar(50)       NULL                COMMENT '动作：登记/转办/退回/办结',
  `handler`      varchar(64)       NULL                COMMENT '处理人账号',
  `handler_name` varchar(64)       NULL                COMMENT '处理人姓名',
  `dept_name`    varchar(200)      NULL                COMMENT '处理部门',
  `opinion`      varchar(2000)     NULL                COMMENT '处理意见 / 批注',
  `receive_time` datetime          NULL                COMMENT '接收（送达）时间',
  `handle_time`  datetime          NULL                COMMENT '处理时间',
  `seq_no`       int           NOT NULL DEFAULT 0      COMMENT '流转顺序',
  `del_flag`     tinyint(1)    NOT NULL DEFAULT 0      COMMENT '删除状态',
  `create_by`    varchar(64)       NULL                COMMENT '创建人',
  `create_time`  datetime          NULL                COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_drf_doc` (`doc_id`),
  KEY `idx_drf_seq` (`doc_id`, `seq_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收文流转记录';

-- ---------------------------------------------------------------------------
-- 收发文附件（旧系统用逗号分隔的 file_path，无元数据，这里改为独立附件表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_doc_attachment` (
  `id`           varchar(36)   NOT NULL                COMMENT '主键',
  `doc_type`     varchar(20)   NOT NULL                COMMENT 'receive 收文 / send 发文',
  `doc_id`       varchar(36)   NOT NULL                COMMENT '对应主表ID',
  `file_name`    varchar(255)  NOT NULL                COMMENT '原始文件名',
  `file_ext`     varchar(20)       NULL                COMMENT '扩展名',
  `file_size`    bigint            NULL                COMMENT '文件字节数',
  `file_md5`     varchar(32)       NULL                COMMENT 'MD5',
  `store_type`   varchar(20)   NOT NULL DEFAULT 'local' COMMENT '存储类型',
  `store_path`   varchar(500)  NOT NULL                COMMENT '相对存储路径',
  `preview_path` varchar(500)      NULL                COMMENT '预览文件路径',
  `sort_no`      int           NOT NULL DEFAULT 0      COMMENT '排序',
  `upload_by`    varchar(64)       NULL                COMMENT '上传人账号',
  `upload_name`  varchar(64)       NULL                COMMENT '上传人姓名',
  `upload_time`  datetime          NULL                COMMENT '上传时间',
  `del_flag`     tinyint(1)    NOT NULL DEFAULT 0      COMMENT '删除状态',
  PRIMARY KEY (`id`),
  KEY `idx_da_doc` (`doc_type`, `doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收发文附件';
