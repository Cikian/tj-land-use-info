-- 创建存储过程，修改表名
DROP PROCEDURE IF EXISTS RenameTableIfExists;
DELIMITER $$
CREATE PROCEDURE RenameTableIfExists(IN old_table_name VARCHAR(255), IN new_table_name VARCHAR(255))
BEGIN
  DECLARE current_db_name VARCHAR(255);
  DECLARE table_exists INT;
 
  SET current_db_name = DATABASE();
  
  SELECT COUNT(*) INTO table_exists FROM information_schema.tables 
  WHERE table_schema = current_db_name AND table_name = old_table_name;
 
  IF table_exists > 0 THEN
    SET @sql = CONCAT('RENAME TABLE ', current_db_name, '.', old_table_name, ' TO ', current_db_name, '.', new_table_name);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

-- 图层信息表 	cim_res_basicinfo		stargis_layer
CALL RenameTableIfExists('cim_res_basicinfo', 'stargis_layer');
-- 功能信息表 	estar_cim_function	stargis_function
CALL RenameTableIfExists('estar_cim_function', 'stargis_function');
-- 方案信息表 	estar_cim_scheme		stargis_scheme
CALL RenameTableIfExists('estar_cim_scheme', 'stargis_scheme');


-- 功能共享信息		cim_function_shareinfo  stargis_function_shareinfo
CALL RenameTableIfExists('cim_function_shareinfo', 'stargis_function_shareinfo');
-- 方案共享信息		cim_scheme_shareinfo  stargis_scheme_shareinfo
CALL RenameTableIfExists('cim_scheme_shareinfo', 'stargis_scheme_shareinfo');
-- 图层共享信息		cim_res_shareinfo  stargis_layer_shareinfo
CALL RenameTableIfExists('cim_res_shareinfo', 'stargis_layer_shareinfo');


-- 功能树信息表 	zk_function_treetable 	stargis_function_treetable
CALL RenameTableIfExists('zk_function_treetable', 'stargis_function_treetable');
-- 图层树信息表 	zk_layer_treetable 			stargis_layer_treetable
CALL RenameTableIfExists('zk_layer_treetable', 'stargis_layer_treetable');
-- 方案树信息表 	zk_scheme_treetable 		stargis_scheme_treetable
CALL RenameTableIfExists('zk_scheme_treetable', 'stargis_scheme_treetable');


-- 图层属性		estar_cim_basicattr			stargis_layer_attr
CALL RenameTableIfExists('estar_cim_basicattr', 'stargis_layer_attr');
-- 功能属性		estar_cim_functionattr	stargis_function_attr
CALL RenameTableIfExists('estar_cim_functionattr', 'stargis_function_attr');
-- 方案属性		estar_cim_schemeattr		stargis_scheme_attr
CALL RenameTableIfExists('estar_cim_schemeattr', 'stargis_scheme_attr');


-- 符号化信息		estar_cim_layersymbol     stargis_layersymbol
CALL RenameTableIfExists('estar_cim_layersymbol', 'stargis_layersymbol');
-- 标注信息			estar_cim_textrender		  stargis_textrender
CALL RenameTableIfExists('estar_cim_textrender', 'stargis_textrender');
-- 图层字段			estar_gislayer2field_app	stargis_layer_field
CALL RenameTableIfExists('estar_gislayer2field_app', 'stargis_layer_field');


-- 数据源信息		estar_cim_metadata								stargis_metadata
CALL RenameTableIfExists('estar_cim_metadata', 'stargis_metadata');
-- 要素集信息		estar_cim_featuredataset					stargis_featuredataset
CALL RenameTableIfExists('estar_cim_featuredataset', 'stargis_featuredataset');
-- 管线类信息		estar_cim_pipefeatureclass				stargis_pipefeatureclass
CALL RenameTableIfExists('estar_cim_pipefeatureclass', 'stargis_pipefeatureclass');
-- 要素类信息		estar_cim_featureclass						stargis_featureclass
CALL RenameTableIfExists('estar_cim_featureclass', 'stargis_featureclass');
-- 字段信息			estar_cim_featureclassfield				stargis_featureclassfield
CALL RenameTableIfExists('estar_cim_featureclassfield', 'stargis_featureclassfield');
-- 数据源共享		zk_shareinfo_metadata							stargis_metadata_shareinfo
CALL RenameTableIfExists('zk_shareinfo_metadata', 'stargis_metadata_shareinfo');


-- 系统名称（已弃用该功能）		estar_cim_systemname  stargis_systemname
CALL RenameTableIfExists('estar_cim_systemname', 'stargis_systemname');
-- 资源类型		cim_res_theme					stargis_theme
CALL RenameTableIfExists('cim_res_theme', 'stargis_theme');
-- 服务类型		cim_res_servicetype		stargis_servicetype
CALL RenameTableIfExists('cim_res_servicetype', 'stargis_servicetype');
-- 场景组装		cim_scene_create			stargis_scene_create
CALL RenameTableIfExists('cim_scene_create', 'stargis_scene_create');


-- 动画 estar_cim_animate				stargis_animate
CALL RenameTableIfExists('estar_cim_animate', 'stargis_animate');
-- 书签	estar_cim_bookmark			stargis_bookmark
CALL RenameTableIfExists('estar_cim_bookmark', 'stargis_bookmark');
-- 高级书签	estar_cim_bookmarkal	stargis_bookmarkal
CALL RenameTableIfExists('estar_cim_bookmarkal', 'stargis_bookmarkal');
-- 定线巡航	estar_cim_dxxh			stargis_dxxh
CALL RenameTableIfExists('estar_cim_dxxh', 'stargis_dxxh');


-- 收藏  estar_cim_collection		stargis_collection
CALL RenameTableIfExists('estar_cim_collection', 'stargis_collection');
-- 浏览  estar_cim_myviews			  stargis_myviews
CALL RenameTableIfExists('estar_cim_myviews', 'stargis_myviews');
-- 消息  cim_res_usermessage		  stargis_usermessage
CALL RenameTableIfExists('cim_res_usermessage', 'stargis_usermessage');


-- 删除存储过程
DROP PROCEDURE IF EXISTS RenameTableIfExists;