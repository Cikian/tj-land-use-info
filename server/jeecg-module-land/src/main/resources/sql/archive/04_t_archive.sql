-- ============================================================================
--  档案管理主表族：t_archive（档案）/ t_archive_file（卷内文件）/ t_archive_log（操作记录）
--  依据：docs/升级改造工作内容清单.md 6.2.2 / 6.2.3 / 6.2.4
--
--  ★ 与设计文档的唯一差异（已与需求方确认）：
--    设计文档把「档案类别」放在档案主表 t_archive.category_id 上（一份档案一个类别）。
--    实际需求是「每个上传的文件各关联一个档案类别」，因此：
--      · t_archive 不再有 category_id / category_path；
--      · t_archive_file 增加 category_id / category_path / category_name（必填）。
--    「档案类别管理」里「类别下有档案则不能移除」的校验，也随之改为统计
--    t_archive_file.category_id（见 ArchiveCategoryMapper.countArchiveGroupByCategory）。
--
--  ★ 宗地/配套项目：
--    档案主体是「配套项目」，但录入时按「先选出让宗地 → 再联动选该宗地下的配套项目」，
--    因此 t_archive 同时冗余 land_id/crzdbh（宗地）与 facility_id/ptxmmc（配套项目）。
--
--  可重复执行：建表 IF NOT EXISTS。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 6.2.2 档案主表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_archive` (
  `id`               varchar(36)   NOT NULL                COMMENT '主键',
  `archive_no`       varchar(64)   NOT NULL                COMMENT '档案号（默认自动生成 DA-{yyyy}-{4位流水}，允许手工改写）',
  `archive_name`     varchar(255)  NOT NULL                COMMENT '档案名称 / 案卷题名',
  -- 关联业务对象
  `land_id`          varchar(64)       NULL                COMMENT '出让宗地ID → t_land.id',
  `crzdbh`           varchar(100)      NULL                COMMENT '出让宗地编号（冗余，业务键）',
  `facility_id`      varchar(100)      NULL                COMMENT '配套项目ID → xj_kjkfb_supporting_facilities.id',
  `ptxmmc`           varchar(100)      NULL                COMMENT '配套项目名称（冗余）',
  `dkmc`             varchar(255)      NULL                COMMENT '地块名称（冗余，来自宗地）',
  `ptsslb`           varchar(100)      NULL                COMMENT '配套设施类别（冗余，来自配套项目）',
  -- 档案属性
  `archive_type`     varchar(32)   NOT NULL DEFAULT 'electronic' COMMENT 'electronic电子档案 / paper纸质档案',
  `secret_level`     varchar(20)   NOT NULL DEFAULT '一般'       COMMENT '密级：一般/内部/秘密/机密',
  `retention`        varchar(20)       NULL                COMMENT '保管期限：永久/长期/短期',
  `archive_year`     int               NULL                COMMENT '档案年度（统计用）',
  `responsible_dept` varchar(100)      NULL                COMMENT '责任部门',
  `responsible_user` varchar(64)       NULL                COMMENT '配套负责人',
  `xzqh`             varchar(100)      NULL                COMMENT '所属行政区（统计用）',
  `archive_date`     date              NULL                COMMENT '归档日期',
  `source_type`      varchar(20)   NOT NULL DEFAULT 'manual' COMMENT '来源：manual手工录入 / doc_receive收文归档 / doc_send发文归档',
  `source_id`        varchar(36)       NULL                COMMENT '来源单据ID（收文/发文ID）',
  `remark`           varchar(1000)     NULL                COMMENT '备注',
  `status`           varchar(20)   NOT NULL DEFAULT '未归档'  COMMENT '未归档/归档中/审核中/已归档',
  `file_count`       int           NOT NULL DEFAULT 0      COMMENT '文件数（冗余，服务端维护）',
  `total_size`       bigint        NOT NULL DEFAULT 0      COMMENT '文件总字节（冗余，服务端维护）',
  `del_flag`         tinyint(1)    NOT NULL DEFAULT 0      COMMENT '删除状态：0正常 1已删除',
  `create_by`        varchar(64)       NULL                COMMENT '创建人',
  `create_time`      datetime          NULL                COMMENT '创建时间',
  `update_by`        varchar(64)       NULL                COMMENT '更新人',
  `update_time`      datetime          NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_archive_no` (`archive_no`, `del_flag`),
  KEY `idx_ar_land` (`land_id`),
  KEY `idx_ar_facility` (`facility_id`),
  KEY `idx_ar_crzdbh` (`crzdbh`),
  KEY `idx_ar_year` (`archive_year`),
  KEY `idx_ar_status` (`status`),
  KEY `idx_ar_source` (`source_type`, `source_id`),
  KEY `idx_ar_name` (`archive_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案主表';

-- ---------------------------------------------------------------------------
-- 6.2.3 档案文件（卷内文件）—— ★ 类别挂在这一层
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_archive_file` (
  `id`            varchar(36)   NOT NULL                COMMENT '主键',
  `archive_id`    varchar(36)   NOT NULL                COMMENT '档案ID → t_archive.id',
  `seq_no`        int           NOT NULL DEFAULT 1      COMMENT '卷内序号',
  `category_id`   varchar(36)   NOT NULL                COMMENT '档案类别ID → t_archive_category.id（必须是叶子类别）',
  `category_path` varchar(200)      NULL                COMMENT '类别 path（冗余，便于按父类别含子类检索/统计）',
  `category_name` varchar(500)      NULL                COMMENT '类别全路径名称（冗余，列表直接展示）',
  `file_name`     varchar(255)  NOT NULL                COMMENT '原始文件名',
  `file_title`    varchar(255)      NULL                COMMENT '文件题名（默认取文件名去扩展名）',
  `file_ext`      varchar(20)       NULL                COMMENT '扩展名 pdf/doc/docx/xls/xlsx/jpg/png/dwg/zip…',
  `file_size`     bigint            NULL                COMMENT '文件字节数',
  `file_md5`      varchar(32)       NULL                COMMENT 'MD5（去重/秒传）',
  `page_count`    int               NULL                COMMENT '页数（PDF 可自动解析）',
  `store_type`    varchar(20)   NOT NULL DEFAULT 'local' COMMENT '存储类型 local/oss/minio',
  `store_path`    varchar(500)  NOT NULL                COMMENT '相对存储路径，如 /archive/2026/09/xxx.pdf',
  `preview_path`  varchar(500)      NULL                COMMENT '预览文件路径',
  `status`        varchar(20)   NOT NULL DEFAULT '已归档' COMMENT '已归档/审核中/归档中',
  `sort_no`       int           NOT NULL DEFAULT 0      COMMENT '同档案内排序',
  `remark`        varchar(500)      NULL                COMMENT '备注',
  `del_flag`      tinyint(1)    NOT NULL DEFAULT 0      COMMENT '删除状态：0正常 1已删除',
  `create_by`     varchar(64)       NULL                COMMENT '创建人（上传人）',
  `create_time`   datetime          NULL                COMMENT '创建时间（上传时间）',
  `update_by`     varchar(64)       NULL                COMMENT '更新人',
  `update_time`   datetime          NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_af_archive` (`archive_id`),
  KEY `idx_af_category` (`category_id`),
  KEY `idx_af_md5` (`file_md5`),
  KEY `idx_af_name` (`file_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案文件（卷内文件）';

-- ---------------------------------------------------------------------------
-- 6.2.4 档案操作记录（方案要求「并记录相关操作」）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_archive_log` (
  `id`           varchar(36)   NOT NULL                COMMENT '主键',
  `archive_id`   varchar(36)       NULL                COMMENT '档案ID',
  `file_id`      varchar(36)       NULL                COMMENT '文件ID',
  `action`       varchar(32)   NOT NULL                COMMENT '新增/修改/删除/上传/下载/预览/导出/归档',
  `detail`       varchar(2000)     NULL                COMMENT '操作明细',
  `biz_key`      varchar(200)      NULL                COMMENT '业务键（档案号 / 宗地编号 / 项目名称）',
  `operate_by`   varchar(64)       NULL                COMMENT '操作人账号',
  `operate_name` varchar(64)       NULL                COMMENT '操作人姓名',
  `operate_time` datetime      NOT NULL                COMMENT '操作时间',
  `ip`           varchar(64)       NULL                COMMENT '操作IP',
  PRIMARY KEY (`id`),
  KEY `idx_al_archive` (`archive_id`),
  KEY `idx_al_time` (`operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案操作记录';
