-- add by lf_20240704 新增图层查看申请表
CREATE TABLE IF NOT EXISTS  `stargis_layer_apply`  (
`id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
`resourceid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源ID',
`opgrant` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '2' COMMENT '权限 检索-1   查看-2',
`optype` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'user' COMMENT '操作者类型   user-用户、org-组织',
`opid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作者ID',
`creation_time` datetime NULL DEFAULT NULL,
`modification_time` datetime NULL DEFAULT NULL,
`enable` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '启用标志，1-启用，0-停用',
`field` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '字段，多选',
`region` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '区域'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '(图层查看申请表)' ROW_FORMAT = Dynamic;


-- add by lf_20240704 修改图层共享表的enable默认值为1
alter table stargis_layer_shareinfo alter column enable set default '1';



-- add by lf_20240708 新增功能查看申请表
CREATE TABLE IF NOT EXISTS `stargis_function_apply`  (
`id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
`resourceid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源ID',
`opgrant` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '2' COMMENT '权限 检索-1   查看-2',
`optype` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'user' COMMENT '操作者类型   user-用户、org-组织',
`opid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作者ID',
`creation_time` datetime NULL DEFAULT NULL,
`modification_time` datetime NULL DEFAULT NULL,
`enable` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '启用标志，1-启用，0-停用',
`field` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '字段，多选',
`region` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '区域'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '(功能查看申请表)' ROW_FORMAT = Dynamic;


-- add by lf_20240704 修改功能共享表的enable默认值为1
alter table stargis_function_shareinfo alter column enable set default '1';


-- add by lf_20240708 新增方案查看申请表
CREATE TABLE IF NOT EXISTS `stargis_scheme_apply`  (
`id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
`resourceid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源ID',
`opgrant` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '2' COMMENT '权限 检索-1   查看-2',
`optype` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'user' COMMENT '操作者类型   user-用户、org-组织',
`opid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作者ID',
`creation_time` datetime NULL DEFAULT NULL,
`modification_time` datetime NULL DEFAULT NULL,
`enable` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '启用标志，1-启用，0-停用',
`field` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '字段，多选',
`region` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '区域'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '(方案查看申请表)' ROW_FORMAT = Dynamic;


-- add by lf_20240704 修改方案共享表的enable默认值为1
alter table stargis_scheme_shareinfo alter column enable set default '1';