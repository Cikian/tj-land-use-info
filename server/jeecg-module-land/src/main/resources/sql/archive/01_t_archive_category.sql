-- =============================================================================
-- 档案管理 · 档案类别管理（方案 2.3.2 第 1 项）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版，MySQL 5.7 / utf8mb4）
-- 对应代码：jeecg-module-land / org.jeecg.modules.land.archive
-- 对应设计：docs/升级改造工作内容清单.md 6.2.1
--
-- 执行方式：整段脚本可重复执行（先 DROP 再建，仅限开发环境）
-- =============================================================================

DROP TABLE IF EXISTS `t_archive_category`;
CREATE TABLE `t_archive_category` (
  `id`           VARCHAR(36)  NOT NULL                COMMENT '主键',
  `parent_id`    VARCHAR(36)      NULL                COMMENT '父级ID，顶级为空',
  `path`         VARCHAR(200) NOT NULL                COMMENT '树路径（主键串），如 /idA/idB，用于整棵子树检索',
  `name`         VARCHAR(100) NOT NULL                COMMENT '类别名称，如 基本建设手续',
  `code`         VARCHAR(50)      NULL                COMMENT '类别编码（预留对接档案标准）',
  `alias_name`   VARCHAR(100)     NULL                COMMENT '别名/拼音码，便于检索',
  `note`         VARCHAR(500)     NULL                COMMENT '类别说明',
  `level`        INT          NOT NULL DEFAULT 1      COMMENT '层级，顶级为 1',
  `has_children` TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '是否有子节点',
  `is_leaf`      TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '是否叶子（叶子才能挂档案）',
  `sort_no`      INT          NOT NULL DEFAULT 0      COMMENT '同级排序，升序',
  `status`       TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `del_flag`     TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '删除状态 0正常 1已删除',
  `create_by`    VARCHAR(32)      NULL                COMMENT '创建人',
  `create_time`  DATETIME         NULL                COMMENT '创建时间',
  `update_by`    VARCHAR(32)      NULL                COMMENT '更新人',
  `update_time`  DATETIME         NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cat_path` (`path`, `del_flag`),
  KEY `idx_cat_parent` (`parent_id`),
  KEY `idx_cat_name` (`name`),
  KEY `idx_cat_sort` (`parent_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案类别树';

-- -----------------------------------------------------------------------------
-- 初始化数据：仅写入方案原文明确列出的 6 个顶级类别。
--
-- 注意：设计文档 6.3.1 里列出的子类别（如"项目建议书批复""选址意见书"等）
--       标注为"建议子类，需与中心确认"，因此不在此脚本中预置，
--       由中心项目负责人在页面上自行维护。
-- -----------------------------------------------------------------------------
INSERT INTO `t_archive_category`
  (`id`, `parent_id`, `path`, `name`, `code`, `alias_name`, `note`, `level`, `has_children`, `is_leaf`, `sort_no`, `status`, `del_flag`, `create_by`, `create_time`)
VALUES
  ('ac00000000000000000000000000000001', NULL, '/ac00000000000000000000000000000001', '基本建设手续', 'JBJS', 'jibejiansheshouxu', '项目建议书、可研、初设及概算等基本建设阶段手续', 1, 0, 1, 1, 1, 0, 'admin', NOW()),
  ('ac00000000000000000000000000000002', NULL, '/ac00000000000000000000000000000002', '规划手续',     'GH',   'guihuashouxu',      '选址意见书、规划条件、用地及工程规划许可、规划验收', 1, 0, 1, 2, 1, 0, 'admin', NOW()),
  ('ac00000000000000000000000000000003', NULL, '/ac00000000000000000000000000000003', '用地手续',     'YD',   'yongdishouxu',      '用地预审、划拨决定书、供地文件、不动产登记',     1, 0, 1, 3, 1, 0, 'admin', NOW()),
  ('ac00000000000000000000000000000004', NULL, '/ac00000000000000000000000000000004', '资金审批',     'ZJSP', 'zijinshenpi',       '资金计划、资金拨付、决算审核',                   1, 0, 1, 4, 1, 0, 'admin', NOW()),
  ('ac00000000000000000000000000000005', NULL, '/ac00000000000000000000000000000005', '工程建设手续', 'GCJS', 'gongchengjianshe',  '施工图审查、施工许可、质量监督、竣工验收、移交', 1, 0, 1, 5, 1, 0, 'admin', NOW()),
  ('ac00000000000000000000000000000006', NULL, '/ac00000000000000000000000000000006', '其他手续',     'QT',   'qitashouxu',        '地名命名、环评、水保及其他零散手续',             1, 0, 1, 6, 1, 0, 'admin', NOW());
