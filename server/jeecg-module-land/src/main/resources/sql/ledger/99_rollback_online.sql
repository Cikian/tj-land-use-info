-- =============================================================================
-- 道路设施验收及移交资料台账 · 线上回滚脚本
-- 目标库：tj-jyxyd
--
-- 回滚分四段，按需取用（★ 默认只执行第 1 段：撤配置、留数据）：
--   第 1 段  撤销菜单 / 按钮权限 / 角色授权 / 字典 / 字典项        —— 默认执行
--   第 2 段  清空「由迁移生成」的台账记录（source_facility_id 非空）—— 默认注释
--   第 3 段  清空全部台账记录（含人工新增）                          —— 默认注释
--   第 4 段  DROP 台账表                                            —— 默认注释
--
-- ★ 为什么第 1 段默认执行、其余默认注释：
--   最常见的回滚需求是「菜单/权限配错了，先撤下来」，此时业务数据必须保住。
--   删数据的语句一旦被顺手执行就无法恢复（本模块只做逻辑删除，DROP 是真删）。
--
-- 执行完第 1 段后同样需要**重新登录**（或清 Redis 的 shiro 授权缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 第 1 段（默认执行）：撤销菜单 / 按钮 / 授权 / 字典
--   固定主键前缀 '1ed9e0a11ed9e0a11ed9e0a11ed9e%'，只影响本模块
-- ---------------------------------------------------------------------------
-- 1.1 先撤角色授权（含菜单与按钮）
DELETE FROM `sys_role_permission`
 WHERE `permission_id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e%';

-- 1.2 再撤按钮权限与菜单
DELETE FROM `sys_permission` WHERE `id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e1%';
DELETE FROM `sys_permission` WHERE `id` = '1ed9e0a11ed9e0a11ed9e0a11ed9e001';

-- 1.3 撤字典项与字典
DELETE FROM `sys_dict_item` WHERE `id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e3%';
DELETE FROM `sys_dict`      WHERE `id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e2%';

-- ---------------------------------------------------------------------------
-- 第 2 段（默认注释）：清空「由迁移生成」的台账记录
--   只删 source_facility_id 非空的（迁移产物），人工新增的记录保留。
--   如果想保留数据只做逻辑删除，把 DELETE 换成 UPDATE ... SET del_flag = 1。
-- ---------------------------------------------------------------------------
-- DELETE FROM `t_road_acceptance_ledger` WHERE `source_facility_id` IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 第 3 段（默认注释）：清空全部台账记录
-- ---------------------------------------------------------------------------
-- DELETE FROM `t_road_acceptance_ledger`;

-- ---------------------------------------------------------------------------
-- 第 4 段（默认注释）：DROP 表
--   ★ 这会连人工录入的台账一起删掉，且不可恢复，请先备份：
--     mysqldump -h49.232.252.56 -uroot -p tj-jyxyd t_road_acceptance_ledger > ledger_backup.sql
-- ---------------------------------------------------------------------------
-- DROP TABLE IF EXISTS `t_road_acceptance_ledger`;

-- ---------------------------------------------------------------------------
-- 回滚自检
--   5.1 应输出 0 行：本模块残留的权限行
-- ---------------------------------------------------------------------------
SELECT '残留权限' AS 项, COUNT(*) AS 行数 FROM `sys_permission`
 WHERE `id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e%';

--   5.2 应输出 0 行：本模块残留的字典与字典项
SELECT '残留字典项' AS 项, COUNT(*) AS 行数 FROM `sys_dict_item`
 WHERE `id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e3%';

SELECT '残留字典' AS 项, COUNT(*) AS 行数 FROM `sys_dict`
 WHERE `id` LIKE '1ed9e0a11ed9e0a11ed9e0a11ed9e2%';

--   5.3 ★ 既有模块的权限码必须仍然存在（证明回滚没有误伤）
--       应输出档案 / 收发文 / 提级论证三组各若干行
SELECT SUBSTRING_INDEX(`perms`, ':', 2) AS 模块前缀, COUNT(*) AS 权限数
FROM `sys_permission`
WHERE `perms` LIKE 'land:%' AND `del_flag` = 0
GROUP BY SUBSTRING_INDEX(`perms`, ':', 2)
ORDER BY 模块前缀;

--   5.4 台账表状态（第 2/3/4 段执行与否的结果对照）
SELECT '台账表' AS 项, TABLE_NAME AS 对象, TABLE_ROWS AS 估算行数
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_acceptance_ledger';
