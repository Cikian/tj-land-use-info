-- =============================================================================
-- 中台（ZK-SERVER 2.0.0）同步字段扩展脚本 —— 角色
-- -----------------------------------------------------------------------------
-- 目标库：jeecg 业务库（tj-jyxyd）
-- 作用  ：为 sys_role 增加“中台镜像”字段，记录本地角色与中台角色
--         （estar_workpost_system，“角色”在中台叫“岗位”）的对应关系与同步状态。
-- 版本  ：MySQL 5.7（jeecg-boot 3.4.3 默认），故未使用 ADD COLUMN IF NOT EXISTS
-- 注意  ：脚本不可重复执行；执行前请先用下方“执行前检查”确认当前列状态。
--
-- ★ 角色同步的实测结论（2026-09 真机验证，勿再走弯路）：
--   1) 中台 addEntity 走 JSON 体，postname/postkey 必填，menuid **可以不传**（实测能建成功）；
--   2) 中台 postkey 等同于“权限字符”，应用层保证唯一（重复返回 code=20001「该权限字符已存在」）；
--   3) updateEntity **不传 menuid 时不会清空**该角色的菜单授权（实测原有 role2menu 记录保留）
--      —— 这正是“只同步角色管理、不动菜单权限”得以成立的前提；
--   4) deleteBetch 会**级联删除** estar_role2menu_system 与 estar_role2user_system 中该角色的记录。
--   因此本系统保存 zk_role_id 作为主锚点，并用 postkey（=本系统 role_code）做存量补同步的反查键。
-- -----------------------------------------------------------------------------

-- 【执行前检查】返回 0 行表示尚未加过列，可以执行本脚本
-- SELECT column_name FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND column_name LIKE 'zk\_%';

-- =============================================================================
-- 一、sys_role 角色表
-- =============================================================================
ALTER TABLE `sys_role`
  ADD COLUMN `zk_role_id`     varchar(64)  NULL DEFAULT NULL COMMENT '中台角色ID(estar_workpost_system.id)' AFTER `description`,
  ADD COLUMN `zk_sync_status` varchar(20)  NULL DEFAULT NULL COMMENT '中台同步状态:not_synced/synced/failed' AFTER `zk_role_id`,
  ADD COLUMN `zk_sync_time`   datetime     NULL DEFAULT NULL COMMENT '中台同步时间'                        AFTER `zk_sync_status`,
  ADD COLUMN `zk_sync_msg`    varchar(500) NULL DEFAULT NULL COMMENT '中台同步失败原因'                    AFTER `zk_sync_time`;

CREATE INDEX `idx_sr_zk_role_id`     ON `sys_role` (`zk_role_id`);
CREATE INDEX `idx_sr_zk_sync_status` ON `sys_role` (`zk_sync_status`);

-- =============================================================================
-- 二、存量数据处理
-- -----------------------------------------------------------------------------
-- 存量角色标记为 not_synced，便于用 zk_sync_status='not_synced' 找出待补同步的角色。
-- 补同步做法：在角色管理里对该角色“编辑保存”一次（同步服务会按 role_code=postkey 在中台反查，
-- 命中则复用中台已有角色并回写 zk_role_id，不会造重复）。
-- =============================================================================
UPDATE `sys_role` SET `zk_sync_status` = 'not_synced' WHERE `zk_sync_status` IS NULL;

-- =============================================================================
-- 【执行后检查】应返回 4 行
-- SELECT column_name, column_type, column_comment FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND column_name LIKE 'zk\_%';
-- =============================================================================
