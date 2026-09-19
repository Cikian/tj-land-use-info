-- 图层属性增加字段   add by lf_20240507
alter table estar_cim_basicattr add COLUMN invertColor varchar(10) DEFAULT 'false' COMMENT '是否反色 默认值：false';
alter table estar_cim_basicattr add COLUMN filterRGB varchar(100) DEFAULT '[70.0, 110.0, 150.0]' COMMENT '滤镜值  默认值：[70.0, 110.0, 150.0]';
alter table estar_cim_basicattr add COLUMN setNodataValueEnableStatus varchar(10) DEFAULT 'false' COMMENT '设置是否使用无效值 默认值：false';
alter table estar_cim_basicattr add COLUMN setNodataValue varchar(100) DEFAULT '[0,0,0]' COMMENT '设置无效值 默认值：[0,0,0]';