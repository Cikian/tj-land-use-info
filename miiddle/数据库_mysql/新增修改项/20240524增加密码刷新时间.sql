-- 增加密码刷新时间  add by lf_20240524
alter table estar_user_system add COLUMN password_Time datetime DEFAULT NULL COMMENT '密码刷新时间';
update estar_user_system set password_time = creation_time; -- 初始化密码刷新时间