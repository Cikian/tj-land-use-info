-- =============================================================================
-- 中台（ZK-SERVER 2.0.0）同步字段扩展脚本
-- -----------------------------------------------------------------------------
-- 目标库：jeecg 业务库（tj-jyxyd）
-- 作用  ：为 sys_depart / sys_user 增加“中台镜像”字段，用于记录本地记录与
--         中台机构(estar_department_system)/用户(estar_user_system)的对应关系
--         以及同步状态，供同步、对账、排障使用。
-- 版本  ：MySQL 5.7（jeecg-boot 3.4.3 默认），故未使用 ADD COLUMN IF NOT EXISTS
-- 注意  ：脚本不可重复执行；执行前请先用下方“执行前检查”确认当前列状态。
-- -----------------------------------------------------------------------------

-- 【执行前检查】返回 0 行表示尚未加过列，可以执行本脚本
-- SELECT table_name, column_name
--   FROM information_schema.columns
--  WHERE table_schema = DATABASE()
--    AND table_name IN ('sys_depart', 'sys_user')
--    AND column_name IN ('zk_dept_id','zk_user_id','zk_loginname',
--                        'zk_sync_status','zk_sync_time','zk_sync_msg');

-- =============================================================================
-- 一、sys_depart 机构表
-- -----------------------------------------------------------------------------
-- ⚠️ 关于"映射键"的实测结论（2026-09 真机验证，勿再走弯路）：
--   1) 中台 estar_department_system.codekey 列虽然存在，但 /departmentApi/addEntity
--      传了也不会写入（实测传 ZZ-CK-INSERT-TEST，库里仍为 NULL）；
--   2) /departmentApi/getAllData 返回的机构对象里**根本没有 codekey 字段**
--      （直接 SQL 写入 codekey 的行，接口同样读不出来；dporder 也不返回）。
--   因此两侧唯一可用的映射就是本表保存的 zk_dept_id —— 中台侧没有任何本系统标识，
--   对账只能按"机构名称 + 父级"匹配。
-- =============================================================================
ALTER TABLE `sys_depart`
  ADD COLUMN `zk_dept_id`     varchar(64)  NULL DEFAULT NULL COMMENT '中台机构ID(estar_department_system.id)' AFTER `qywx_identifier`,
  ADD COLUMN `zk_sync_status` varchar(20)  NULL DEFAULT NULL COMMENT '中台同步状态:not_synced/synced/failed'   AFTER `zk_dept_id`,
  ADD COLUMN `zk_sync_time`   datetime     NULL DEFAULT NULL COMMENT '中台同步时间'                          AFTER `zk_sync_status`,
  ADD COLUMN `zk_sync_msg`    varchar(500) NULL DEFAULT NULL COMMENT '中台同步失败原因'                      AFTER `zk_sync_time`;

CREATE INDEX `idx_sd_zk_dept_id`     ON `sys_depart` (`zk_dept_id`);
CREATE INDEX `idx_sd_zk_sync_status` ON `sys_depart` (`zk_sync_status`);

-- =============================================================================
-- 二、sys_user 用户表
-- =============================================================================
ALTER TABLE `sys_user`
  ADD COLUMN `zk_user_id`     varchar(64)  NULL DEFAULT NULL COMMENT '中台用户ID(estar_user_system.id)' AFTER `client_id`,
  ADD COLUMN `zk_loginname`   varchar(100) NULL DEFAULT NULL COMMENT '中台登录名'                     AFTER `zk_user_id`,
  ADD COLUMN `zk_sync_status` varchar(20)  NULL DEFAULT NULL COMMENT '中台同步状态:not_synced/synced/failed' AFTER `zk_loginname`,
  ADD COLUMN `zk_sync_time`   datetime     NULL DEFAULT NULL COMMENT '中台同步时间'                   AFTER `zk_sync_status`,
  ADD COLUMN `zk_sync_msg`    varchar(500) NULL DEFAULT NULL COMMENT '中台同步失败原因'               AFTER `zk_sync_time`;

CREATE INDEX `idx_su_zk_user_id`     ON `sys_user` (`zk_user_id`);
CREATE INDEX `idx_su_zk_loginname`   ON `sys_user` (`zk_loginname`);
CREATE INDEX `idx_su_zk_sync_status` ON `sys_user` (`zk_sync_status`);

-- =============================================================================
-- 三、存量数据处理（可选）
-- -----------------------------------------------------------------------------
-- 新增列后存量机构/用户 zk_sync_status 为 NULL。首次同步（对账）前建议先标记为
-- not_synced，便于用 “zk_sync_status = 'not_synced'” 找出所有待同步记录。
-- =============================================================================
UPDATE `sys_depart` SET `zk_sync_status` = 'not_synced' WHERE `zk_sync_status` IS NULL;
UPDATE `sys_user`   SET `zk_sync_status` = 'not_synced' WHERE `zk_sync_status` IS NULL;

-- =============================================================================
-- 四、数据字典（可选）
-- -----------------------------------------------------------------------------
-- 实体上标注了 @Dict(dicCode = "zk_sync_status")，缺字典时列表页只显示英文值，
-- 不影响功能。若字典已存在请跳过本节（重复插入会因 dict_code 唯一约束失败）。
-- 也可以在【系统管理 → 数据字典】里手工新增：字典编码 zk_sync_status。
-- =============================================================================
INSERT INTO `sys_dict` (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `type`)
VALUES (REPLACE(UUID(), '-', ''), '中台同步状态', 'zk_sync_status', '机构/用户同步至中台ZK-SERVER的状态', 0, 'admin', NOW(), 0);

INSERT INTO `sys_dict_item` (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT REPLACE(UUID(), '-', ''), d.id, t.item_text, t.item_value, t.item_text, t.sort_order, 1, 'admin', NOW()
  FROM `sys_dict` d
  JOIN (SELECT '未同步' AS item_text, 'not_synced' AS item_value, 1 AS sort_order
        UNION ALL SELECT '已同步', 'synced', 2
        UNION ALL SELECT '同步失败', 'failed', 3) t
 WHERE d.dict_code = 'zk_sync_status';

-- =============================================================================
-- 【执行后检查】应各返回 4 / 5 行
-- SELECT table_name, column_name, column_type, column_comment
--   FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'sys_depart'
--    AND column_name LIKE 'zk_%';
-- SELECT table_name, column_name, column_type, column_comment
--   FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'sys_user'
--    AND column_name LIKE 'zk_%';
-- =============================================================================
