-- =============================================================================
--  附件目录树：t_land_attachment 增加 dir_path + 新建目录表 t_land_attachment_dir
-- =============================================================================
--  需求（2026-10-10）：
--    1. 允许直接上传文件夹；
--    2. 附件管理按「项目 → 目录结构」树形展示；
--    3. 地块 / 项目详情里的附件同样树形展示。
--
--  ★ 目录怎么建模（两种做法，这里选后者）：
--    甲、只存文件的相对路径前缀，树由路径推导 —— 轻，但**建不出空目录**，
--        而「新建 / 重命名 / 删除目录」是本次明确要求的能力（文件全删了目录也要在）。
--    乙、独立目录表 + 附件上冗余一个 dir_path —— 空目录天然能表达，
--        重命名目录只需改一张表（附件只冗余路径，不必逐条更新 store_path）。
--    选乙：本表存目录节点，t_land_attachment.dir_path 存**该文件所在目录**的相对路径。
--
--  ★ 为什么 dir_path 是「相对业务对象的路径」而不是完整路径：
--    业务对象（宗地 / 配套 / 环节）本身就是树的第一层，由 biz_type + biz_id 决定。
--    dir_path 只表示它下面那一层，例如 '招标文件/2024'；根目录用空字符串 ''。
--    这样同一份目录结构可以复用到不同业务对象上，也不会因为改了宗地编号而失效。
--
--  ★ dir_path / 目录表 都要带 biz_type + biz_id：
--    目录名允许在不同项目下重复（A 项目的「合同」与 B 项目的「合同」是两回事）。
--
--  幂等：建表用 IF NOT EXISTS；加列用存储过程包一层（MySQL 没有
--        ADD COLUMN IF NOT EXISTS，直接 ALTER 在重复执行时会报 1060）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. t_land_attachment 增加 dir_path
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_add_att_dir_path`;

DELIMITER $$
CREATE PROCEDURE `sp_add_att_dir_path`()
BEGIN
  IF NOT EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = (SELECT DATABASE())
        AND TABLE_NAME = 't_land_attachment'
        AND COLUMN_NAME = 'dir_path'
  ) THEN
    ALTER TABLE `t_land_attachment`
      ADD COLUMN `dir_path` varchar(500) NOT NULL DEFAULT ''
      COMMENT '所在目录的相对路径（相对 biz_type+biz_id 指定的业务对象），根目录为空串'
      AFTER `biz_key`;
    ALTER TABLE `t_land_attachment`
      ADD KEY `idx_att_dir` (`biz_type`, `biz_id`, `dir_path`);
  END IF;
END$$
DELIMITER ;

CALL `sp_add_att_dir_path`();
DROP PROCEDURE IF EXISTS `sp_add_att_dir_path`;

-- ---------------------------------------------------------------------------
-- 2. 目录表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_land_attachment_dir` (
  `id`           varchar(64)   NOT NULL                COMMENT '主键',
  `biz_type`     varchar(32)   NOT NULL                COMMENT '业务类型：land 宗地 / facility 配套项目 / process 环节进度',
  `biz_id`       varchar(100)  NOT NULL                COMMENT '业务主键：t_land.id / 配套项目.id / t_facility_process.id',
  `biz_key`      varchar(100)      NULL                COMMENT '业务可读键（冗余，列表与检索直接展示）',
  `dir_path`     varchar(500)  NOT NULL                COMMENT '目录相对路径（不含业务对象本身），例如 招标文件/2024',
  `parent_path`  varchar(500)  NOT NULL DEFAULT ''     COMMENT '父目录相对路径；根目录的父为空串',
  `dir_name`     varchar(200)  NOT NULL                COMMENT '目录名（dir_path 的最后一段，冗余便于展示与排序）',
  `depth`        int           NOT NULL DEFAULT 1      COMMENT '层级：根下第一层为 1',
  `sort_no`      int               NULL                COMMENT '同级排序；为空按名称排',
  `create_by`    varchar(64)       NULL                COMMENT '创建人账号',
  `create_name`  varchar(64)       NULL                COMMENT '创建人姓名',
  `create_time`  datetime          NULL                COMMENT '创建时间',
  `del_flag`     tinyint(1)    NOT NULL DEFAULT 0      COMMENT '删除状态：0正常 1已删除',
  PRIMARY KEY (`id`),
  -- 同一业务对象下目录路径唯一（软删记录也占位由业务层处理，避免索引里带 del_flag 影响复用）
  UNIQUE KEY `uk_att_dir` (`biz_type`, `biz_id`, `dir_path`),
  KEY `idx_att_dir_parent` (`biz_type`, `biz_id`, `parent_path`),
  KEY `idx_att_dir_biz_key` (`biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件目录（支撑附件树的空目录与目录维护）';

-- ---------------------------------------------------------------------------
-- 3. 核对
-- ---------------------------------------------------------------------------
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 't_land_attachment_dir';

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = (SELECT DATABASE())
  AND TABLE_NAME = 't_land_attachment'
  AND COLUMN_NAME = 'dir_path';

-- 现有附件都落在根目录（dir_path 默认为空串），核对一下数量与分布
SELECT dir_path, COUNT(*) AS file_count
FROM t_land_attachment
WHERE del_flag = 0
GROUP BY dir_path
ORDER BY file_count DESC;
