-- add by lf_20240823 增加图层属性
alter table stargis_layer_attr add COLUMN brightness varchar(10) DEFAULT '1' COMMENT '亮度  【0,2】 步长0.1 默认  1';
alter table stargis_layer_attr add COLUMN contrast varchar(10) DEFAULT '1' COMMENT '对比度 【0,2】 步长0.1 默认  1';
alter table stargis_layer_attr add COLUMN hue varchar(10) DEFAULT '0' COMMENT '色调 【0,2】 步长0.1 默认  0';
alter table stargis_layer_attr add COLUMN saturation varchar(10) DEFAULT '1' COMMENT '饱和度 【0,2】 步长0.1 默认  1';
alter table stargis_layer_attr add COLUMN gamma varchar(10) DEFAULT '1' COMMENT '伽马 【0,2】 步长0.1 默认  1';

-- add by lf_20240823 增加方案属性
alter table stargis_scheme_attr add COLUMN brightness varchar(10) DEFAULT '1' COMMENT '亮度  【0,2】 步长0.1 默认  1';
alter table stargis_scheme_attr add COLUMN contrast varchar(10) DEFAULT '1' COMMENT '对比度 【0,2】 步长0.1 默认  1';
alter table stargis_scheme_attr add COLUMN hue varchar(10) DEFAULT '0' COMMENT '色调 【0,2】 步长0.1 默认  0';
alter table stargis_scheme_attr add COLUMN saturation varchar(10) DEFAULT '1' COMMENT '饱和度 【0,2】 步长0.1 默认  1';
alter table stargis_scheme_attr add COLUMN gamma varchar(10) DEFAULT '1' COMMENT '伽马 【0,2】 步长0.1 默认  1';
