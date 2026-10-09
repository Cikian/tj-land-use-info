-- =============================================================================
-- 提级论证管理 · 【回滚】撤销本模块在目标库上的全部改动
-- 目标库：与同步时相同的库（本地或线上）
--
-- ★ 适用场景
--   执行过 01/03/02/04（以及可选 05/06）之后，需要**把本模块从库里撤掉**：
--     · 菜单、按钮权限、角色授权（本模块的 11 条 sys_permission + 11 条 sys_role_permission）
--     · 3 个业务字典 + 13 个字典项
--     · （可选，见第 4 段）3 张业务表及其中的全部数据
--
-- ★ 安全性
--   前三段只删除**本模块固定主键/固定 dict_code** 的记录：
--     菜单   id LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%'
--     按钮   id LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%'
--     字典   dict_code IN ('land_escalation_project_type','land_escalation_arg_result','land_escalation_material_type')
--   不会触碰档案/收发文/其它模块的任何记录。
--   第 4 段（DROP 表）会**删除本模块全部业务数据**，默认注释，需要时才手工打开。
--
-- ★ 执行顺序：三段必须按 1→2→3 顺序执行（先删授权，再删权限/字典）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0) 回滚前报告（先看清将删除什么）
-- ---------------------------------------------------------------------------
SELECT '回滚前：本模块在库内的记录数' AS 报告项,
 (SELECT COUNT(*) FROM `sys_permission` WHERE `id` LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%') AS 菜单,
 (SELECT COUNT(*) FROM `sys_permission` WHERE `id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%') AS 按钮,
 (SELECT COUNT(*) FROM `sys_role_permission`
    WHERE `permission_id` LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%'
       OR `permission_id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%') AS 角色授权,
 (SELECT COUNT(*) FROM `sys_dict` WHERE `dict_code` LIKE 'land_escalation%') AS 字典,
 (SELECT COUNT(*) FROM `sys_dict_item` i JOIN `sys_dict` d ON d.`id` = i.`dict_id`
    WHERE d.`dict_code` LIKE 'land_escalation%') AS 字典项;

-- ---------------------------------------------------------------------------
-- 1) 删除角色授权（先删授权，避免留下指向已删权限的孤儿记录）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%'
    OR `permission_id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%';

-- ---------------------------------------------------------------------------
-- 2) 删除按钮权限与菜单
-- ---------------------------------------------------------------------------
DELETE FROM `sys_permission`
 WHERE `id` LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%'
    OR `id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%';

-- ---------------------------------------------------------------------------
-- 3) 删除字典项与字典（先子表后主表）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_dict_item`
 WHERE `dict_id` IN (
   SELECT `id` FROM `sys_dict`
    WHERE `dict_code` IN ('land_escalation_project_type',
                          'land_escalation_arg_result',
                          'land_escalation_material_type'));

DELETE FROM `sys_dict`
 WHERE `dict_code` IN ('land_escalation_project_type',
                       'land_escalation_arg_result',
                       'land_escalation_material_type');

-- ---------------------------------------------------------------------------
-- 4) 【可选·破坏性】删除 3 张业务表及其全部数据
--    默认注释：不动它，数据留着（只撤配置，模块页面消失但数据可留待下次上线）。
--    确实要连数据一起清掉时，去掉下面的注释再执行。
-- ---------------------------------------------------------------------------
-- DROP TABLE IF EXISTS `t_escalation_record`;
-- DROP TABLE IF EXISTS `t_escalation_material`;
-- DROP TABLE IF EXISTS `t_escalation_project`;

-- ---------------------------------------------------------------------------
-- 5) 回滚后校验
--    5.1 应输出 0 行（本模块配置已全部清除）
-- ---------------------------------------------------------------------------
SELECT '残留配置（应为 0 行）' AS 检查项, p.`id`, p.`name`, p.`perms`
FROM `sys_permission` p
WHERE p.`id` LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%'
   OR p.`id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%'
   OR p.`perms` LIKE 'land:escalation%';

SELECT '残留字典（应为 0 行）' AS 检查项, `dict_code`, `dict_name`
FROM `sys_dict` WHERE `dict_code` LIKE 'land_escalation%';

--    5.2 展示既有模块是否完好（应仍有档案/收发文相关权限）
SELECT '既有 land 权限码（应仍存在，非 0）' AS 检查项, COUNT(*) AS 数量
FROM `sys_permission` WHERE `perms` LIKE 'land:%';
