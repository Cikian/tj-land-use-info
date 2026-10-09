-- =============================================================================
-- 道路交付及养护协议移交事项 · 线上回滚脚本
-- 目标库：tj-jyxyd
--
-- 四段式，按需取用（★ 默认只执行第 1 段：撤配置、留数据）：
--   第 1 段  撤销菜单 / 按钮权限 / 角色授权 / 字典 / 字典项   —— 默认执行
--   第 2 段  清空「由迁移生成」的移交事项（source_facility_id 非空）—— 默认注释
--   第 3 段  清空全部移交事项（含人工新增）                    —— 默认注释
--   第 4 段  DROP 表                                          —— 默认注释
--
-- 执行完第 1 段后同样需要**重新登录**（或清 Redis 的 shiro 授权缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 第 1 段（默认执行）：撤销菜单 / 按钮 / 授权 / 字典
--   固定主键前缀 '2ed9e0a11ed9e0a11ed9e0a11ed9e%'，只影响本模块
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e%';

DELETE FROM `sys_permission` WHERE `id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e1%';
DELETE FROM `sys_permission` WHERE `id` = '2ed9e0a11ed9e0a11ed9e0a11ed9e001';

DELETE FROM `sys_dict_item` WHERE `id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e3%';
DELETE FROM `sys_dict`      WHERE `id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e2%';

-- ---------------------------------------------------------------------------
-- 第 2 段（默认注释）：只删迁移生成的移交事项
-- ---------------------------------------------------------------------------
-- DELETE FROM `t_road_handover` WHERE `source_facility_id` IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 第 3 段（默认注释）：清空全部移交事项
--   想保留数据只做逻辑删除时，把 DELETE 换成 UPDATE ... SET del_flag = 1
-- ---------------------------------------------------------------------------
-- DELETE FROM `t_road_handover`;

-- ---------------------------------------------------------------------------
-- 第 4 段（默认注释）：DROP 表（★ 先备份）
--   mysqldump -h49.232.252.56 -uroot -p tj-jyxyd t_road_handover > handover_backup.sql
-- ---------------------------------------------------------------------------
-- DROP TABLE IF EXISTS `t_road_handover`;

-- ---------------------------------------------------------------------------
-- 回滚自检
-- ---------------------------------------------------------------------------
SELECT '残留权限' AS 项, COUNT(*) AS 行数 FROM `sys_permission`
 WHERE `id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e%';

SELECT '残留字典' AS 项, COUNT(*) AS 行数 FROM `sys_dict`
 WHERE `id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e2%';

SELECT '残留字典项' AS 项, COUNT(*) AS 行数 FROM `sys_dict_item`
 WHERE `id` LIKE '2ed9e0a11ed9e0a11ed9e0a11ed9e3%';

-- ★ 既有模块的权限码必须仍然存在，且**必须仍然处于已授权状态**
--   （曾经踩过：授权行主键算法把不同模块压成同一行，导致「权限码在、但没授权」）
SELECT SUBSTRING_INDEX(`perms`, ':', 2) AS 模块, COUNT(*) AS 权限行数,
       SUM(CASE WHEN EXISTS (SELECT 1 FROM `sys_role_permission` rp
                              WHERE rp.`permission_id` = p.`id`
                                AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b')
                THEN 1 ELSE 0 END) AS 已授权行数
FROM `sys_permission` p
WHERE `perms` LIKE 'land:%' AND `del_flag` = 0
GROUP BY SUBSTRING_INDEX(`perms`, ':', 2) ORDER BY 模块;

SELECT '移交事项表状态' AS 项, TABLE_NAME AS 对象, TABLE_ROWS AS 估算行数
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_handover';
