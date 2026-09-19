-- add by lf_20241111 增加专题目录日志管理
CREATE TABLE IF NOT EXISTS `stargis_catalogs_logs` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `catalogid` varchar(64) NOT NULL COMMENT '专题目录id',
  `type` varchar(64) NOT NULL COMMENT '类型',
  `creator` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updater` varchar(64) DEFAULT NULL COMMENT '修改人',
  `creation_Time` datetime(3) DEFAULT NULL COMMENT '创建时间',
  `modification_Time` datetime(3) DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专题目录日志管理';