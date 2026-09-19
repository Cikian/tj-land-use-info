-- add by lf_20240716 令牌管理信息表
CREATE TABLE IF NOT EXISTS `stargis_access_token` (
  `id` varchar(64) NOT NULL,
  `user_id` varchar(64) DEFAULT NULL COMMENT '用户id',
  `access_token_validity_seconds` int(11) DEFAULT NULL COMMENT '令牌有效时间',
  `refresh_token_validity_seconds` int(11) DEFAULT NULL COMMENT '刷新令牌有效时间',
  `access_token` varchar(64) DEFAULT NULL COMMENT '令牌',
  `refresh_token` varchar(64) DEFAULT NULL COMMENT '刷新令牌',
  `expires_time` datetime DEFAULT NULL COMMENT '令牌到期时间',
  `refresh_expires_time` datetime DEFAULT NULL COMMENT '刷新令牌到期时间',
  `description` longtext COMMENT '描述',
  `creator` varchar(64) DEFAULT NULL COMMENT '创建者',
  `creation_time` datetime NOT NULL COMMENT '创建时间',
  `modification_time` datetime DEFAULT NULL COMMENT '修改时间',
  `refreshflag` varchar(10) DEFAULT '0' COMMENT '是否刷新  0-否 1-是',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='令牌管理信息表';

-- add by lf_20240716 元数据信息表
CREATE TABLE IF NOT EXISTS `stargis_layer_metadata` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '名称',
  `zhname` varchar(100) DEFAULT NULL COMMENT '中文名',
  `type` varchar(100) DEFAULT NULL COMMENT '类型',
  `needflag` varchar(10) DEFAULT NULL COMMENT '必填标志',
  `defaultflag` varchar(100) DEFAULT NULL COMMENT '默认值',
  `teamname` varchar(100) DEFAULT NULL COMMENT '分组名称',
  `servicetype` varchar(32) DEFAULT NULL COMMENT '服务类型',
  `restype` varchar(32) DEFAULT NULL COMMENT '资源类型：数据资源、功能资源、物联感知资源',
  `deleteflag` varchar(10) DEFAULT NULL COMMENT '删除标志',
  `enableflag` varchar(10) DEFAULT NULL COMMENT '是否可用',
  `creation_time` datetime DEFAULT NULL,
  `modification_time` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据信息';


-- add by lf_20240717 添加元数据相关的数据字典
INSERT INTO estar_dictionary_system (id, dickey, dicname, dicvalue, dicdesc, status, parentid, creation_Time, modification_Time, codekey, dicversion, isdelete, sortid, dictcode, mylevel, state) VALUES ('2c9180927fd380cd01801d1e6c000020', 'METATYPE', '元数据类型', '元数据类型', NULL, 'app', NULL, '2024-04-12 17:32:55', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO estar_dictionary_system (id, dickey, dicname, dicvalue, dicdesc, status, parentid, creation_Time, modification_Time, codekey, dicversion, isdelete, sortid, dictcode, mylevel, state) VALUES ('2c9180927fd380cd01801d1f65f80021', 'meta_int', '整数型', '整数型', 1, 'app', '2c9180927fd380cd01801d1e6c000020', '2024-04-12 17:33:59', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO estar_dictionary_system (id, dickey, dicname, dicvalue, dicdesc, status, parentid, creation_Time, modification_Time, codekey, dicversion, isdelete, sortid, dictcode, mylevel, state) VALUES ('2c9180927fd380cd01801d1fdcd90022', 'meta_float', '浮点型', '浮点型', 2, 'app', '2c9180927fd380cd01801d1e6c000020', '2024-04-12 17:34:29', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO estar_dictionary_system (id, dickey, dicname, dicvalue, dicdesc, status, parentid, creation_Time, modification_Time, codekey, dicversion, isdelete, sortid, dictcode, mylevel, state) VALUES ('2c9180927fd380cd01801d20aa3f0023', 'meta_string', '字符型', '字符型', 3, 'app', '2c9180927fd380cd01801d1e6c000020', '2024-04-12 17:35:22', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO estar_dictionary_system (id, dickey, dicname, dicvalue, dicdesc, status, parentid, creation_Time, modification_Time, codekey, dicversion, isdelete, sortid, dictcode, mylevel, state) VALUES ('2c9180927fd380cd01801d2124e80024', 'meta_date', '日期型', '日期型', 4, 'app', '2c9180927fd380cd01801d1e6c000020', '2024-04-12 17:35:53', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO estar_dictionary_system (id, dickey, dicname, dicvalue, dicdesc, status, parentid, creation_Time, modification_Time, codekey, dicversion, isdelete, sortid, dictcode, mylevel, state) VALUES ('2c9180927fd380cd01801d21b10f0025', 'meta_boolean', '布尔型', '布尔型', 5, 'app', '2c9180927fd380cd01801d1e6c000020', '2024-04-12 17:36:29', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);

-- add by lf_20240717 资源数据与元数据关联表
CREATE TABLE IF NOT EXISTS `stargis_infometa` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `resourceid` varchar(100) DEFAULT NULL COMMENT '资源id',
  `metadataid` varchar(100) DEFAULT NULL COMMENT '元数据id',
  `metavalue` varchar(100) DEFAULT NULL COMMENT '元数据值',
  `creation_time` datetime DEFAULT NULL,
  `modification_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源数据与元数据关联表';


-- add by lf_20240718 功能资源服务子表
CREATE TABLE IF NOT EXISTS `stargis_funservice` (
  `id` varchar(32) NOT NULL,
  `inputparam` longtext COMMENT '传入参数',
  `outputparam` longtext COMMENT '输出参数',
  `testparm` longtext COMMENT '返回示例',
  `creation_time` datetime DEFAULT NULL COMMENT '创建时间',
  `modification_time` datetime DEFAULT NULL COMMENT '更新时间',
  `resourceid` varchar(100) DEFAULT NULL COMMENT '资源id，基础表外键',
  `requesttype` varchar(255) DEFAULT NULL COMMENT 'requesttype请求方式',
  `bodytype` varchar(255) DEFAULT NULL COMMENT 'bodytype请求体参数类型',
  `f1` longtext COMMENT '备用字段1',
  `f2` longtext COMMENT '备用字段2',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='功能资源服务子表';


-- add by lf_20240718 时空服务中心（功能-对外接口）表
CREATE TABLE IF NOT EXISTS `stargis_funbasicinfo` (
  `id` varchar(100) NOT NULL,
  `name` varchar(100) DEFAULT NULL COMMENT '资源名称',
  `url` varchar(500) DEFAULT NULL COMMENT '资源URL',
  `proxyurl` varchar(500) DEFAULT NULL COMMENT '资源代理URL',
  `type` varchar(100) DEFAULT NULL COMMENT '资源类型：theme',
  `busstatus` varchar(50) DEFAULT NULL COMMENT '资源当前业务状态：注册时   busstatus值为1-流程审批中     2-流程未通过   3-流程通过   注销时： 0-注销    0.5-注销中',
  `subtypes` varchar(100) DEFAULT NULL COMMENT '服务类型属性：servicetype',
  `legend` varchar(500) DEFAULT NULL COMMENT '图例',
  `thumbnail` varchar(500) DEFAULT NULL COMMENT '缩略图',
  `visitcount` varchar(10) DEFAULT NULL COMMENT '访问次数',
  `tags` varchar(500) DEFAULT NULL COMMENT '标签，多个用逗号隔开',
  `coordinate` text COMMENT '坐标-后加',
  `featurcclassid` varchar(255) DEFAULT NULL COMMENT '要素类编码',
  `tiletype` varchar(10) DEFAULT NULL COMMENT '切片类型',
  `proxyflag` varchar(10) DEFAULT NULL COMMENT '是否代理',
  `proxyuri` varchar(255) DEFAULT NULL COMMENT 'proxyuri',
  `proxypredicates` varchar(50) DEFAULT NULL COMMENT '断言',
  `proxyparams` varchar(500) DEFAULT NULL COMMENT 'proxyparams',
  `feaclass` varchar(255) DEFAULT NULL COMMENT '绑定要素类的id',
  `metadata` varchar(100) DEFAULT NULL COMMENT '数据源表',
  `enable` varchar(10) DEFAULT '0' COMMENT '启用标志，0-停用，1-启用',
  `shareway` varchar(50) DEFAULT NULL COMMENT '共享方式  1-私有、2-所有用户可检索、3-所有用户可查询',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `creation_time` datetime DEFAULT NULL COMMENT '创建时间',
  `modification_time` datetime DEFAULT NULL COMMENT '更新时间',
  `createuserid` varchar(100) DEFAULT NULL COMMENT '创建者用户id',
  `createdepid` varchar(100) DEFAULT NULL,
  `modifyuserid` varchar(100) DEFAULT NULL,
  `modifydepid` varchar(100) DEFAULT NULL,
  `datasource` varchar(100) DEFAULT NULL COMMENT '服务来源',
  `fs1` varchar(255) DEFAULT NULL COMMENT '备用字段1',
  `fs2` varchar(255) DEFAULT NULL COMMENT '备用字段2',
  `fs3` varchar(255) DEFAULT NULL COMMENT '备用字段3',
  `parent` varchar(10) DEFAULT NULL COMMENT '是否是父节点',
  `layertype` varchar(10) DEFAULT NULL COMMENT '图层类型',
  `path` varchar(50) DEFAULT NULL,
  `parentid` varchar(50) DEFAULT NULL COMMENT '父id',
  `mylevel` int(10) DEFAULT NULL,
  `codekey` varchar(100) DEFAULT NULL,
  `state` varchar(10) DEFAULT NULL,
  `dirtypes` text COMMENT '主题类型，多个用逗号隔开',
  `sharedir` varchar(50) DEFAULT NULL COMMENT '共享归属  1-个人/2-组织',
  `scopetypes` varchar(100) DEFAULT NULL COMMENT '公开资源-public、分享资源-share、我的组织资源-dep、我的资源-own，多个用逗号隔开',
  `visitstatus` varchar(10) DEFAULT NULL COMMENT '访问类型',
  `statusflag` varchar(10) DEFAULT '0' COMMENT '服务状态标志，0-在线，1-离线',
  `fieldid` varchar(100) DEFAULT NULL COMMENT '字段id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='时空服务中心表';


-- add by lf_20240718 时空服务共享信息表
CREATE TABLE IF NOT EXISTS `stargis_funbasicinfo_shareinfo` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `resourceid` varchar(32) DEFAULT NULL COMMENT '资源ID',
  `opgrant` varchar(50) DEFAULT NULL COMMENT '权限 检索-1   查看-2',
  `optype` varchar(50) DEFAULT NULL COMMENT '操作者类型   user-用户、org-组织',
  `opid` varchar(64) DEFAULT NULL COMMENT '操作者ID',
  `creation_time` datetime DEFAULT NULL,
  `modification_time` datetime DEFAULT NULL,
  `enable` varchar(10) DEFAULT '1' COMMENT '启用标志，1-启用，0-停用',
  `field` text COMMENT '字段，多选',
  `region` text COMMENT '区域，可多选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='(时空服务共享信息表)';


-- add by lf_20240718 时空服务查看申请表
CREATE TABLE IF NOT EXISTS `stargis_funbasicinfo_apply` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `resourceid` varchar(32) DEFAULT NULL COMMENT '资源ID',
  `opgrant` varchar(50) DEFAULT '2' COMMENT '权限 检索-1   查看-2',
  `optype` varchar(50) DEFAULT 'user' COMMENT '操作者类型   user-用户、org-组织',
  `opid` varchar(64) DEFAULT NULL COMMENT '操作者ID',
  `creation_time` datetime DEFAULT NULL,
  `modification_time` datetime DEFAULT NULL,
  `enable` varchar(10) DEFAULT '1' COMMENT '启用标志，1-启用，0-停用',
  `field` text COMMENT '字段，多选',
  `region` text COMMENT '区域'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='(时空服务查看申请表)';


-- add by lf_20240718 数据字典的资源类型增加时空服务
INSERT INTO estar_dictionary_system (id, dickey, dicname, dicvalue, dicdesc, status, parentid, creation_Time, modification_Time, codekey, dicversion, isdelete, sortid, dictcode, mylevel, state) VALUES ('88f57fea4df94e5b92b29a9a0d70b112', 'funBasicinfo', '时空服务', '时空服务', 5, 'app', '2e08a915d39d4562ab2bdec7e8ab0f8a', '2024-07-18 17:59:27', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);


-- add by lf_20240719 图层表增加三个备用字段
alter table stargis_layer add COLUMN fs1 varchar(255) DEFAULT NULL COMMENT '备用字段1';
alter table stargis_layer add COLUMN fs2 varchar(255) DEFAULT NULL COMMENT '备用字段2';
alter table stargis_layer add COLUMN fs3 varchar(255) DEFAULT NULL COMMENT '备用字段3';


-- add by lf_20240722 代理服务信息表
CREATE TABLE IF NOT EXISTS `gateway_listener` (
  `id` varchar(100) NOT NULL COMMENT '代理服务信息表id',
  `creation_time` datetime DEFAULT NULL COMMENT '创建时间',
  `modification_time` datetime DEFAULT NULL COMMENT '修改时间',
  `loginname` varchar(255) DEFAULT NULL COMMENT '当前登录用户名称',
  `loginid` varchar(100) DEFAULT NULL COMMENT '当前登录用户id',
  `dataid` varchar(100) DEFAULT NULL COMMENT '资源id',
  `proxypredicates` varchar(50) DEFAULT NULL COMMENT '代理谓词',
  `type` varchar(64) DEFAULT NULL COMMENT '资源类型',
  `subtypes` varchar(100) DEFAULT NULL COMMENT '服务类型',
  `iserror` varchar(10) DEFAULT NULL COMMENT '是否成功  success error',
  `errorlevel` varchar(10) DEFAULT NULL COMMENT '异常级别 鉴权失败的一律记为10002',
  `errormsg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `responsespeed` varchar(50) DEFAULT NULL COMMENT '响应速度',
  `measures` varchar(500) DEFAULT NULL COMMENT '处理措施',
  `attribute1` varchar(255) DEFAULT NULL COMMENT '备用字段1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理服务信息表';

-- add by lf_20240801  流程增加申请理由和申请文件路径
alter table app_alldata_system add COLUMN applicationreason text DEFAULT NULL COMMENT '申请理由';
alter table app_alldata_system add COLUMN applicationfile text DEFAULT NULL COMMENT '申请文件路径';


-- add by lf_20240802 时空管理服务+元数据相关菜单新增
INSERT INTO estar_appmenu_system (id, name, icon, menukey, menuorder, parentid, creation_time, modification_time, component, urlpath, openwith) VALUES ('962c141b9061402b83c9824aa1e3b85a', '元数据管理', 'hhtxshujuzidian1', NULL, 5, 'ac609ea446114424a05b0799f0b62241', '2024-07-16 17:57:33.445', NULL, '/metadata-manage', '/metadata-manage', '0');
INSERT INTO estar_appmenu_system (id, name, icon, menukey, menuorder, parentid, creation_time, modification_time, component, urlpath, openwith) VALUES ('a8866194fd1f438e9b14e49caee843e0', '时空服务中心', 'hhtxshujuzidian1', NULL, 3, 'd749ad8075274b82b8f5a09ede528a54', '2024-07-16 14:30:06.166', '2024-07-16 14:37:22.662', '/service-serve', '/service-serve', '0');
INSERT INTO estar_appmenu_system (id, name, icon, menukey, menuorder, parentid, creation_time, modification_time, component, urlpath, openwith) VALUES ('68ba6fa1ecee4b6fb3b54c5ef33d301f', '时空服务管理', 'hhtxfenleitianchong', NULL, 3, 'a8808690ca7446b88e337ea74140d90e', '2024-07-19 18:30:12.482', '2024-07-19 18:30:22.676', '/serve-manage', '/serve-manage', '0');

-- add by lf_20240802 时空管理服务相关资源类型新增
INSERT INTO stargis_theme (id, name, icon, parentid, code, ordernum, enableflag, deleteflag, creation_time, modification_time, mylevel, codekey, state) VALUES ('4028eedd90b9602b0190ba5295f40006', '云图服务', NULL, '402894067fd50d9a017fd5250e110005', 'ytfw', '1', '1', NULL, '2024-07-16 14:54:52.916', '2024-07-16 15:45:02.087', 0, '00140002000000000000', 'opened');
INSERT INTO stargis_theme (id, name, icon, parentid, code, ordernum, enableflag, deleteflag, creation_time, modification_time, mylevel, codekey, state) VALUES ('4028eedd90b9602b0190ba450fb00005', '雨量监测站点', NULL, '4028eedd90b9602b0190ba44c3520004', 'yljczd', '1', '1', NULL, '2024-07-16 14:40:06.576', NULL, 0, '00140001000100000000', 'opened');
INSERT INTO stargis_theme (id, name, icon, parentid, code, ordernum, enableflag, deleteflag, creation_time, modification_time, mylevel, codekey, state) VALUES ('4028eedd90b9602b0190ba44c3520004', '监测站点服务', NULL, '402894067fd50d9a017fd5250e110005', 'jcsjfw', '1', '1', NULL, '2024-07-16 14:39:46.946', '2024-07-16 15:44:57.15', 0, '00140001000000000000', 'closed');
INSERT INTO stargis_theme (id, name, icon, parentid, code, ordernum, enableflag, deleteflag, creation_time, modification_time, mylevel, codekey, state) VALUES ('402894067fd50d9a017fd5250e110005', '时空服务', NULL, '', '3', '3', '1', NULL, '2022-03-29 18:07:30', '2024-07-16 15:44:11.252', 0, '00150000000000000000', 'opened');

-- add by lf_20240802 时空管理服务相关服务类型新增
INSERT INTO stargis_servicetype (id, name, zhurl, zhid, zhname, description, enableflag, deleteflag, ordernum, restype, modification_time, creation_time, metaflag, urlrules, thumbnail, code, featurcclassid, parentid, restype2, componentname, icon) VALUES ('4028eedd90b9602b0190ba86f8760008', '查询统计服务', NULL, NULL, 'cxtjfw', NULL, NULL, NULL, NULL, '402894067fd50d9a017fd5250e110005', NULL, '2024-07-16 15:52:06.006', NULL, NULL, NULL, 'cxtjfw', NULL, '', NULL, NULL, NULL);
INSERT INTO stargis_servicetype (id, name, zhurl, zhid, zhname, description, enableflag, deleteflag, ordernum, restype, modification_time, creation_time, metaflag, urlrules, thumbnail, code, featurcclassid, parentid, restype2, componentname, icon) VALUES ('4028eedd90b9602b0190ba8682f00007', '资源管理服务', NULL, NULL, 'zyglfw', NULL, NULL, NULL, NULL, '402894067fd50d9a017fd5250e110005', NULL, '2024-07-16 15:51:35.92', NULL, NULL, NULL, 'zyglfw', NULL, '', NULL, NULL, NULL);