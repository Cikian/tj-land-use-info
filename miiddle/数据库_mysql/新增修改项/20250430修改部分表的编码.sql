-- update by lf_20250430 修改部分表的编码,解决查询字段值不区分大小写的情况
ALTER TABLE stargis_layer_treetable CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE stargis_function_treetable CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE stargis_scheme_treetable CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

ALTER TABLE stargis_layer_attr CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE stargis_function_attr CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE stargis_scheme_attr CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

ALTER TABLE stargis_layer CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE stargis_funbasicinfo CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;


ALTER TABLE stargis_layer_field  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;