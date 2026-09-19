CREATE TABLE `system_oauth2_access_token` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `user_id` varchar(64) NOT NULL COMMENT '用户编号',
  `access_token` varchar(500) NOT NULL COMMENT '访问令牌',
  `refresh_token` varchar(64) NOT NULL COMMENT '刷新令牌',
  `access_token_validity_minute` int(10) NOT NULL COMMENT '访问令牌有效期（分钟）',
  `expires_time` datetime NOT NULL COMMENT '过期时间',
  `creation_Time` datetime DEFAULT NULL COMMENT '创建时间',
  `modification_Time` datetime DEFAULT NULL COMMENT '修改时间',
  `deleted` varchar(2) DEFAULT '0' COMMENT '是否删除 0-否  1-是',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2 访问令牌';

CREATE TABLE `system_oauth2_refresh_token` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `user_id` varchar(64) NOT NULL COMMENT '用户编号',
  `refresh_token` varchar(64) NOT NULL COMMENT '刷新令牌',
  `refresh_token_validity_minute` int(10) NOT NULL COMMENT '刷新令牌有效期（分钟）',
  `expires_time` datetime NOT NULL COMMENT '过期时间',
  `creation_Time` datetime DEFAULT NULL COMMENT '创建时间',
  `modification_Time` datetime DEFAULT NULL COMMENT '修改时间',
  `deleted` varchar(2) DEFAULT '0' COMMENT '是否删除 0-否  1-是',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2 刷新令牌';


CREATE TABLE `system_login_log` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `user_id` varchar(64) NOT NULL COMMENT '用户编号',
  `username` varchar(50) NOT NULL COMMENT '用户账号',
  `result` tinyint(4) NOT NULL COMMENT '登陆结果',
  `result_msg` varchar(512) DEFAULT NULL COMMENT '登录结果信息',
  `user_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 IP',
  `user_agent` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '浏览器 UA',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `creation_Time` datetime DEFAULT NULL COMMENT '创建时间',
  `modification_Time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统访问记录';