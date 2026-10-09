-- ============================================================================
--  数据管理统一附件表 t_land_attachment
--  依据：docs/升级改造工作内容清单.md 5.3.5「配套附件管理（上传、查询、预览、下载）」
--  旧实现：4 个固定槽位 file01~file04 → 目录 01-土地整理计划 / 02-配套情况函 /
--          03-配套筹备函 / 04-出让宗地图形数据（shp）；配套侧另有一套目录
--
--  ★ 为什么建「统一附件表」而不是宗地/配套各一张：
--    附件管理要支持三种业务（宗地 / 配套项目 / 环节进度）＋「查询、预览、下载」
--    这一整套动作。三张结构一样的表就要写三套 Service、三套 Controller、
--    三份前端；而它们的差异只有 biz_type 一个字段的取值。
--    这与设计文档 5.3.5 给的 t_attachment 建议一致（那里就叫 biz_type/biz_id）。
--
--  ★ 为什么必须丢掉旧实现（旧系统的 5 个硬伤，逐条对应本表设计）：
--    1. 类型写死：4 个槽位名硬编码在 Controller 里 → file_type 走字典，可扩展；
--    2. 查询靠**递归扫描磁盘目录** → 改为查库（目录丢了数据还在）；
--    3. 预览前 `Files.deleteDir(tempPath)` **清空整个 temp 目录**，
--       并发下互相删除 → store_path 直读，不经临时目录；
--    4. 无大小/上传人/上传时间 → 本表都有；
--    5. 目录名里带业务语义（01-土地整理计划），改名即丢数据 → file_type 与目录解耦。
--
--  ★ 路径安全（本表的硬约束）：
--    store_path 只存**相对路径**（由 jeecg 的 /sys/common/upload 返回，
--    形如 /land/facility/2026/10/xxx.pdf），服务端读盘时会做
--    「canonicalPath 必须落在 uploadRoot 之内 + 拒绝 .. 」双重校验
--    （照抄档案模块 ArchiveFileServiceImpl.resolveAbsolutePath 的做法）。
--
--  可重复执行：建表用 IF NOT EXISTS。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_land_attachment` (
  `id`            varchar(64)   NOT NULL                COMMENT '主键',
  `biz_type`      varchar(32)   NOT NULL                COMMENT '业务类型：land 宗地 / facility 配套项目 / process 环节进度',
  `biz_id`        varchar(100)  NOT NULL                COMMENT '业务主键：t_land.id / 配套项目.id / t_facility_process.id',
  `biz_key`       varchar(100)      NULL                COMMENT '业务可读键（宗地编号/配套项目名称），列表与搜索用，避免每次联表',
  `file_type`     varchar(50)   NOT NULL                COMMENT '附件类型码（走字典 land_attachment_type）',
  `file_name`     varchar(255)  NOT NULL                COMMENT '原始文件名',
  `file_ext`      varchar(20)       NULL                COMMENT '扩展名（小写，不含点）',
  `file_size`     bigint            NULL                COMMENT '文件字节数',
  `file_md5`      varchar(32)       NULL                COMMENT 'MD5（秒传/去重）',
  `content_type`  varchar(150)      NULL                COMMENT 'MIME 类型（预览时决定用图片/PDF/Office 哪种方式打开）',
  `store_type`    varchar(20)   NOT NULL DEFAULT 'local' COMMENT '存储类型：local / minio / oss',
  `store_path`    varchar(500)  NOT NULL                COMMENT '相对存储路径（禁止绝对路径，禁止 ..）',
  `remark`        varchar(500)      NULL                COMMENT '备注',
  `sort_no`       int               NULL                COMMENT '排序',
  `upload_by`     varchar(64)       NULL                COMMENT '上传人账号',
  `upload_name`   varchar(64)       NULL                COMMENT '上传人姓名',
  `upload_time`   datetime          NULL                COMMENT '上传时间',
  `download_count` int          NOT NULL DEFAULT 0      COMMENT '下载次数（留痕用）',
  `del_flag`      tinyint(1)    NOT NULL DEFAULT 0      COMMENT '删除状态：0正常 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_att_biz` (`biz_type`, `biz_id`),
  KEY `idx_att_md5` (`file_md5`),
  KEY `idx_att_type` (`file_type`),
  KEY `idx_att_biz_key` (`biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据管理统一附件（宗地/配套项目/环节进度）';

-- 核对：建表后应输出 1 行
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 't_land_attachment';
