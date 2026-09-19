-- 图层信息表 	cim_res_basicinfo		stargis_layer
alter table if exists cim_res_basicinfo rename to stargis_layer;
-- 功能信息表 	estar_cim_function	stargis_function
alter table if exists estar_cim_function rename to stargis_function;
-- 方案信息表 	estar_cim_scheme		stargis_scheme
alter table if exists estar_cim_scheme rename to stargis_scheme;


-- 功能共享信息		cim_function_shareinfo  stargis_function_shareinfo
alter table if exists cim_function_shareinfo rename to stargis_function_shareinfo;
-- 方案共享信息		cim_scheme_shareinfo  stargis_scheme_shareinfo
alter table if exists cim_scheme_shareinfo rename to stargis_scheme_shareinfo;
-- 图层共享信息		cim_res_shareinfo  stargis_layer_shareinfo
alter table if exists cim_res_shareinfo rename to stargis_layer_shareinfo;


-- 功能树信息表 	zk_function_treetable 	stargis_function_treetable
alter table if exists zk_function_treetable rename to stargis_function_treetable;
-- 图层树信息表 	zk_layer_treetable 			stargis_layer_treetable
alter table if exists zk_layer_treetable rename to stargis_layer_treetable;
-- 方案树信息表 	zk_scheme_treetable 		stargis_scheme_treetable
alter table if exists zk_scheme_treetable rename to stargis_scheme_treetable;


-- 图层属性		estar_cim_basicattr			stargis_layer_attr
alter table if exists estar_cim_basicattr rename to stargis_layer_attr;
-- 功能属性		estar_cim_functionattr	stargis_function_attr
alter table if exists estar_cim_functionattr rename to stargis_function_attr;
-- 方案属性		estar_cim_schemeattr		stargis_scheme_attr
alter table if exists estar_cim_schemeattr rename to stargis_scheme_attr;


-- 符号化信息		estar_cim_layersymbol		stargis_layersymbol
alter table if exists estar_cim_layersymbol rename to stargis_layersymbol;
-- 标注信息			estar_cim_textrender		stargis_textrender
alter table if exists estar_cim_textrender rename to stargis_textrender;
-- 图层字段			estar_gislayer2field_app	stargis_layer_field
alter table if exists estar_gislayer2field_app rename to stargis_layer_field;


-- 数据源信息		estar_cim_metadata								stargis_metadata
alter table if exists estar_cim_metadata rename to stargis_metadata;
-- 要素集信息		estar_cim_featuredataset					stargis_featuredataset
alter table if exists estar_cim_featuredataset rename to stargis_featuredataset;
-- 管线类信息		estar_cim_pipefeatureclass				stargis_pipefeatureclass
alter table if exists estar_cim_pipefeatureclass rename to stargis_pipefeatureclass;
-- 要素类信息		estar_cim_featureclass						stargis_featureclass
alter table if exists estar_cim_featureclass rename to stargis_featureclass;
-- 字段信息			estar_cim_featureclassfield				stargis_featureclassfield
alter table if exists estar_cim_featureclassfield rename to stargis_featureclassfield;
-- 数据源共享		zk_shareinfo_metadata							stargis_metadata_shareinfo
alter table if exists zk_shareinfo_metadata rename to stargis_metadata_shareinfo;


-- 系统名称（已弃用该功能）		estar_cim_systemname  stargis_systemname
alter table if exists estar_cim_systemname rename to stargis_systemname;
-- 资源类型		cim_res_theme					stargis_theme
alter table if exists cim_res_theme rename to stargis_theme;
-- 服务类型		cim_res_servicetype		stargis_servicetype
alter table if exists cim_res_servicetype rename to stargis_servicetype;
-- 场景组装		cim_scene_create			stargis_scene_create
alter table if exists cim_scene_create rename to stargis_scene_create;


-- 动画 estar_cim_animate				stargis_animate
alter table if exists estar_cim_animate rename to stargis_animate;
-- 书签	estar_cim_bookmark			stargis_bookmark
alter table if exists estar_cim_bookmark rename to stargis_bookmark;
-- 高级书签	estar_cim_bookmarkal	stargis_bookmarkal
alter table if exists estar_cim_bookmarkal rename to stargis_bookmarkal;
-- 定线巡航	estar_cim_dxxh			stargis_dxxh
alter table if exists estar_cim_dxxh rename to stargis_dxxh;


-- 收藏  estar_cim_collection		stargis_collection
alter table if exists estar_cim_collection rename to stargis_collection;
-- 浏览  estar_cim_myviews			stargis_myviews
alter table if exists estar_cim_myviews rename to stargis_myviews;
-- 消息  cim_res_usermessage		stargis_usermessage
alter table if exists cim_res_usermessage rename to stargis_usermessage;
