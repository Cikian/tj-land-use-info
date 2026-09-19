-- add by lf_20240329 图层树增加是否展开和视角
alter table zk_layer_treetable add COLUMN expandflag varchar(50) DEFAULT NULL COMMENT '是否支持展开';
alter table zk_layer_treetable add COLUMN angle varchar(255) DEFAULT NULL COMMENT '视角';