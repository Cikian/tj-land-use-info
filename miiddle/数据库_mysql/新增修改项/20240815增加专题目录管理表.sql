-- add by lf_20240815 增加专题目录管理表
CREATE TABLE IF NOT EXISTS `stargis_catalogs` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `name` varchar(255) NOT NULL COMMENT '专题名称',
  `alias` varchar(255) DEFAULT NULL COMMENT '专题别名',
  `isoverview` int(11) DEFAULT '0' COMMENT '是否鹰眼  0-否  1-是',
  `description` text COMMENT '描述',
  `filepath` text COMMENT '文件',
  `ordernum` int(11) DEFAULT NULL COMMENT '序号',
  `parentid` varchar(64) DEFAULT NULL COMMENT '父级节点',
  `creator` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updater` varchar(64) DEFAULT NULL COMMENT '修改人',
  `creation_Time` datetime DEFAULT NULL COMMENT '创建时间',
  `modification_Time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专题目录管理';