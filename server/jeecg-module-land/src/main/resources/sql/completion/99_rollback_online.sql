-- =============================================================================
-- 竣工验收项目历史工程资料数字化档案 · 线上回滚脚本
-- 目标库：tj-jyxyd
--
-- 回滚分四段，按需取用（★ 默认只执行第 1 段：撤配置、留数据）：
--   第 1 段  撤销菜单 / 按钮权限 / 角色授权 / 字典 / 字典项   —— 默认执行
--   第 2 段  逻辑删除全部档案记录（del_flag = 1，可恢复）      —— 默认注释
--   第 3 段  物理删除全部档案记录（不可恢复，先备份）          —— 默认注释
--   第 4 段  DROP 档案表                                      —— 默认注释
--
-- ★ 为什么第 1 段默认执行、其余默认注释：
--   最常见的回滚需求是「菜单/权限配错了，先撤下来」，此时业务数据必须保住。
--   删数据的语句一旦被顺手执行就无法恢复（第 3/4 段是真删）。
--
-- ★ 本模块没有数据迁移，所以**没有**「清空由迁移生成的行」这种段落
--   （第 7 项台账模块的 99 脚本里有，那一个模块才需要）。
--
-- 执行完第 1 段后同样需**重新登录**（或清 Redis 的 shiro 授权缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 第 1 段（默认执行）：撤销菜单 / 按钮 / 授权 / 字典
--   固定主键前缀 '3ed9e0a11ed9e0a11ed9e0a11ed9e%'，只影响本模块
-- ---------------------------------------------------------------------------
-- 1.1 先撤角色授权（含菜单与按钮）
DELETE FROM `sys_role_permission`
 WHERE `permission_id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e%';

-- 1.2 再撤按钮权限与菜单
DELETE FROM `sys_permission` WHERE `id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e1%';
DELETE FROM `sys_permission` WHERE `id` = '3ed9e0a11ed9e0a11ed9e0a11ed9e001';

-- 1.3 撤字典项与字典
DELETE FROM `sys_dict_item` WHERE `id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e3%';
DELETE FROM `sys_dict`      WHERE `id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e2%';

-- ---------------------------------------------------------------------------
-- 第 2 段（默认注释）：逻辑删除全部档案记录（可恢复：del_flag 改回 0 即可）
-- ---------------------------------------------------------------------------
-- UPDATE `t_completion_archive` SET `del_flag` = 1 WHERE `del_flag` = 0;

-- ---------------------------------------------------------------------------
-- 第 3 段（默认注释）：物理删除全部档案记录
-- ---------------------------------------------------------------------------
-- DELETE FROM `t_completion_archive`;

-- ---------------------------------------------------------------------------
-- 第 4 段（默认注释）：DROP 表
--   ★ 这会连录入的历史档案一起删掉，且不可恢复，请先备份：
--     mysqldump -h49.232.252.56 -uroot -p tj-jyxyd t_completion_archive > completion_archive_backup.sql
-- ---------------------------------------------------------------------------
-- DROP TABLE IF EXISTS `t_completion_archive`;

-- ---------------------------------------------------------------------------
-- 回滚自检
--   5.1 应输出 0 行：本模块残留的权限行
-- ---------------------------------------------------------------------------
SELECT '残留权限' AS 项, COUNT(*) AS 行数 FROM `sys_permission`
 WHERE `id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e%';

--   5.2 应输出 0 行：本模块残留的字典与字典项
SELECT '残留字典项' AS 项, COUNT(*) AS 行数 FROM `sys_dict_item`
 WHERE `id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e3%';

SELECT '残留字典' AS 项, COUNT(*) AS 行数 FROM `sys_dict`
 WHERE `id` LIKE '3ed9e0a11ed9e0a11ed9e0a11ed9e2%';

--   5.3 ★ 既有模块的权限码必须仍然存在（证明回滚没有误伤）
--       应输出档案 / 收发文 / 提级论证 / 台账 四组各若干行
SELECT SUBSTRING_INDEX(`perms`, ':', 2) AS 模块前缀, COUNT(*) AS 权限数
FROM `sys_permission`
WHERE `perms` LIKE 'land:%' AND `del_flag` = 0
GROUP BY SUBSTRING_INDEX(`perms`, ':', 2)
ORDER BY 模块前缀;

--   5.4 档案表状态（第 2/3/4 段执行与否的结果对照）
SELECT '档案表' AS 项, TABLE_NAME AS 对象, TABLE_ROWS AS 估算行数
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_completion_archive';
