-- add by lf_20250814 新增菜单，并增加超管对新增菜单的使用权限
INSERT INTO estar_appmenu_system (id, name, icon, menukey, menuorder, parentid, creation_time, modification_time, component, urlpath, openwith) VALUES ('4f7917b570c4401084a9d2e68347b2e8', '平台监控', 'hhtxyunhang', NULL, 12, '', '2025-08-05 14:36:19.571', NULL, '/platform-monitor', '', '0');
INSERT INTO estar_appmenu_system (id, name, icon, menukey, menuorder, parentid, creation_time, modification_time, component, urlpath, openwith) VALUES ('826b40e7096e46b783c5dda2758ba515', '服务访问统计', 'hhtxlianjie', NULL, 2, '4f7917b570c4401084a9d2e68347b2e8', '2025-08-05 14:36:58.25', '2025-08-05 14:37:02.589', '/service-access-statistics', '', '0');
INSERT INTO estar_appmenu_system (id, name, icon, menukey, menuorder, parentid, creation_time, modification_time, component, urlpath, openwith) VALUES ('69563af02ed94e2d92af7ea496b877fe', '服务监控', 'hhtxshujuzidian1', NULL, 1, '4f7917b570c4401084a9d2e68347b2e8', '2025-08-05 14:36:36.122', '2025-08-05 14:37:08.397', '/service-monitor', '', '0');

INSERT INTO estar_role2menu_system (id, roleid, menuid) VALUES ('5e8ff092da0142e5a05b0d7559b83ce4', 'fa7f801c80364a48ac6d4402c9b969a7', '4f7917b570c4401084a9d2e68347b2e8');
INSERT INTO estar_role2menu_system (id, roleid, menuid) VALUES ('25319af979554f948352e5503dd621cb', 'fa7f801c80364a48ac6d4402c9b969a7', '69563af02ed94e2d92af7ea496b877fe');
INSERT INTO estar_role2menu_system (id, roleid, menuid) VALUES ('c432940de6a84164acc5b29fcebdcd72', 'fa7f801c80364a48ac6d4402c9b969a7', '826b40e7096e46b783c5dda2758ba515');


-- add by lf_20250814 服务调用情况表 新增加字段ipaddr和url
alter table gateway_listener add column ipaddr varchar(255) COMMENT 'IP地址';
alter table gateway_listener add column url text COMMENT '请求URL';


-- add by lf_20250814 时空管理服务与图层表的字段保持一致，所以增加customProperty
alter table stargis_funbasicinfo add COLUMN customProperty longtext COMMENT '自定义属性';