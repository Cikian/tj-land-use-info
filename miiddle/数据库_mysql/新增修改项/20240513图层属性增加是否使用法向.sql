-- 图层属性增加字段   add by lf_20240507
alter table stargis_layer_attr add COLUMN ignoreNormal varchar(10) DEFAULT 'false' COMMENT '是否使用法向  仅支持三维，默认值为false';