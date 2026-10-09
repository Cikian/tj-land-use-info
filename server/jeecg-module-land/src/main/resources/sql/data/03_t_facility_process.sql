-- ============================================================================
--  配套项目环节进度表 t_facility_process
--  依据：docs/升级改造工作内容清单.md 5.3.2「配套地块数据录入（1 宗地 N 配套
--        + 29 环节进度录入）」
--  旧表：nutzwk_ywk.process_status（29 行，实测为**测试数据**，整表丢弃不迁移）
--
--  ★ 为什么整表丢弃旧数据：
--    旧库这 29 行的 lrdw/lrr 是「阿纳海姆 / 冯布朗 / 天人组织 / AAAA」，
--    jtwt 是「月神三号炸了」「戴肯家叛乱了」「吉翁不给」—— 明确的演示数据。
--    迁进来只会污染新系统的台账与预警统计（设计文档 §6 也把这几张表
--    列入「测试数据残留，迁移时应整表丢弃」）。因此本表**只建结构、不导数据**。
--
--  ★ 表结构相对旧库的改动（逐条都有理由）：
--    1. 旧库 `fileUrl varchar(800)` 用逗号拼多个附件 → 改为走 t_land_attachment
--       统一附件表（旧做法：文件名里的逗号会把一条记录拆成两个假附件，
--       且无法记录大小/上传人/上传时间、无法单独删除某一个）。
--       本表保留 `file_url` 列但**只作历史兼容**，新界面不再写入。
--    2. 旧库 lcqk 实际出现 4 种取值（进行中/已完成 + 代码里出现的未开启/不涉及），
--       这里显式写明并加 CHECK 语义说明。
--    3. 补 update_by/update_time，`createTime`/`createAccount` 沿用旧列名
--       （与配套主表 xj_kjkfb_supporting_facilities 的驼峰风格保持一致，
--        避免同一业务域里一套下划线一套驼峰）。
--
--  ★ 唯一性：一个配套项目的一个环节只应有一行 —— 唯一键 (pt_id, lc_id)。
--    旧库没有这个约束，实测存在同一 (ptid, lcid) 多行的情况，
--    后果是「同一环节填两次、状态取哪一行不确定」。新系统必须堵住。
--
--  可重复执行：建表用 IF NOT EXISTS。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_facility_process` (
  `id`             varchar(64)    NOT NULL                COMMENT '主键',
  `pt_id`          varchar(64)    NOT NULL                COMMENT '配套项目ID（xj_kjkfb_supporting_facilities.id）',
  `crzdbh`         varchar(100)       NULL                COMMENT '出让宗地编号（冗余：按宗地筛查环节时不联表）',
  `lc_id`          varchar(64)    NOT NULL                COMMENT '环节ID（t_process_configuration.id）',
  `lc_path`        varchar(20)        NULL                COMMENT '环节树路径（冗余：00010001，用于排序与「阶段→事项」归并）',
  `lc_name`        varchar(100)       NULL                COMMENT '环节名称（冗余快照）',
  `stage_id`       varchar(64)        NULL                COMMENT '所属阶段ID（冗余：0001~0006）',
  `lcqk`           varchar(30)        NULL                COMMENT '环节情况：未开启 / 进行中 / 已完成 / 不涉及',
  `lckssj`         date               NULL                COMMENT '环节开始时间',
  `yjjssj`         date               NULL                COMMENT '预计结束时间（按环节标准时长 + 非工作日日历推算）',
  `lcjssj`         date               NULL                COMMENT '环节实际结束时间',
  `czwtlx`         varchar(30)        NULL                COMMENT '存在问题类型：审批问题 / 资金问题 / 权属问题 / 管线问题 / 地质问题',
  `jtwt`           varchar(1000)      NULL                COMMENT '具体问题',
  `gzjy`           varchar(1000)      NULL                COMMENT '工作建议',
  `lrdw`           varchar(100)       NULL                COMMENT '录入单位',
  `lrr`            varchar(100)       NULL                COMMENT '录入人',
  `lxdh`           varchar(100)       NULL                COMMENT '联系电话',
  `file_url`       varchar(800)       NULL                COMMENT '★历史兼容列（旧库 fileUrl）。新界面不再写入，附件走 t_land_attachment',
  `create_account` varchar(100)       NULL                COMMENT '创建账号',
  `create_time`    datetime           NULL                COMMENT '创建时间',
  `update_by`      varchar(64)        NULL                COMMENT '更新人',
  `update_time`    datetime           NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fp_pt_lc` (`pt_id`, `lc_id`),
  KEY `idx_fp_crzdbh` (`crzdbh`),
  KEY `idx_fp_stage` (`stage_id`),
  KEY `idx_fp_lcqk` (`lcqk`, `yjjssj`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配套项目环节进度（29 环节录入）';

-- 核对：建表后应输出 1 行
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 't_facility_process';

-- 核对：与流程配置表能对上（应输出 30）
SELECT COUNT(*) AS 可录入环节数 FROM `t_process_configuration` WHERE `del_flag` = 0;
