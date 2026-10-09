-- =============================================================================
-- 数据管理 · 接口权限（按钮权限 menu_type=2）与角色授权（方案 2.3.1（三）全 6 项）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- ★ 前置：先执行 03_land_data_menu.sql（一级菜单 + 第 3 项菜单 + land:data:import）
--   与 09_data_menus_ext.sql（其余 5 项菜单）。本脚本只登记接口权限。
--
-- ★ 为什么必须建成「按钮权限」而不是写在菜单上
--   jeecg 的权限分两条链路：
--     ① 后端 @RequiresPermissions → ShiroRealm → SysBaseApiImpl.getUserPermissionSet()
--        → SysPermissionMapper.queryByUser()：把该用户角色下**所有** perms 非空的行
--        都收进来（不区分 menu_type），所以权限码写在菜单行上后端也能拦住；
--     ② 前端 v-has 指令 / 菜单管理的按钮授权 → SysPermissionController.getUserPermissionJsonArray()
--        → getAuthJsonArray()：**只收 menu_type=2（按钮）且 status=1 的行**。
--   把权限码写在 menu_type=1 的菜单行上，链路 ② 拿不到任何条目，
--   结果就是「菜单管理里看不到任何可授权的接口权限」。
--   因此本脚本把权限码全部下沉为**按钮权限**（菜单行 perms 均为 NULL）。
--
-- ★ 权限码清单（6 个，与代码里的 @RequiresPermissions 逐一对应）
--   land:data:import      经营性用地批量导入（03 脚本已登记，本脚本兜底重登一次）
--   land:data:land        经营性用地信息录入（逐条）
--   land:data:facility    配套地块数据录入（含 29 环节进度）
--   land:data:facilityImport 配套信息批量导入（含孤儿清单）
--   land:data:attachment  配套附件管理（上传/查询/预览/下载）
--   land:data:recycle     数据更新与移除（回收站恢复 + 变更留痕）
--
-- ★★ 粒度取舍：为什么不是 list/add/edit/delete 四个码一组
--   本项目实际使用这个模块的是中心 2~3 个人，而「能不能打开这个菜单」
--   才是真正的安全边界（没授权菜单就进不来页面，后端也拿不到对应 perms）。
--   把每个菜单再拆成 4 个码，结果是角色授权界面上出现 24 个复选框，
--   管理员每次加人都要勾 24 下，而且很容易漏勾一个导致「按钮消失」这种
--   最难排查的问题（权限问题的表现是「功能没了」，用户会以为系统坏了）。
--   所以这里是**一面菜单一个码**：
--     · land:data:recycle 单独一个码，因为恢复数据 = 一次写入，
--       且它能看到别人的全部操作留痕，敏感度高于普通查看；
--     · 其余 5 个与菜单一一对应。
--   将来若中心提出「某人只能看不能改」，再按需拆 land:data:land:edit 之类的码即可。
--
-- ★★ 主键长度必须正好 32 位（sys_permission.id 是 varchar(32)）
--   模块前缀 = 7a1f0d2c4e5b4d8a9c3f6b1e（28 位），按钮后缀 4 位 bxxx：
--     b001 land:data:import（与 03 脚本同主键，ON DUPLICATE 兜底）
--     b002 land:data:land
--     b003 land:data:facility
--     b004 land:data:facilityImport
--     b005 land:data:attachment
--     b006 land:data:recycle
--
-- 脚本可重复执行：先按按钮 id 前缀清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理（幂等）
--    注意：只清按钮行（b 前缀），不动菜单行（a 前缀）。
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1eb%';
DELETE FROM `sys_permission`
 WHERE `id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1eb%';

-- ---------------------------------------------------------------------------
-- 2) 兜底：清空本模块全部菜单行上的权限码
--    （菜单行有 perms 会导致链路 ② 拿不到条目，见文件头说明）
-- ---------------------------------------------------------------------------
UPDATE `sys_permission` SET `perms` = NULL, `perms_type` = '0'
 WHERE `id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1ea%';

-- ---------------------------------------------------------------------------
-- 3) 按钮权限（menu_type=2）
--    字段形态与 jeecg 代码生成器产物 / 库内既有按钮行保持一致：
--    url/component/component_name/redirect/icon 为 NULL，is_route=1，is_leaf=1，
--    keep_alive=0，hidden=0，hide_tab=0，perms_type='1'，status='1'，del_flag=0
--
--    parent_id 指向「该权限码最相关的那个菜单」：
--    按钮挂在哪个菜单下只影响「角色授权界面上的归属显示」，不影响鉴权。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  -- ===== 挂在「经营性用地批量导入」菜单下（a002）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1eb001', '7a1f0d2c4e5b4d8a9c3f6b1ea002',
   '经营性用地批量导入', NULL, NULL, NULL, NULL, 2,
   'land:data:import', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '经营性用地批量导入：模板下载 / 字段字典 / 重复策略 / 预览校验 / 确认入库 / 错误回执下载 / 导入记录', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 挂在「经营性用地信息录入」菜单下（a003）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1eb002', '7a1f0d2c4e5b4d8a9c3f6b1ea003',
   '经营性用地信息录入', NULL, NULL, NULL, NULL, 2,
   'land:data:land', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '经营性用地逐条录入：列表 / 详情 / 新增 / 编辑 / 移除 / 批量移除 / 编号唯一性校验 / 宗地编号下拉 / 变更履历', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 挂在「配套地块数据录入」菜单下（a004）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1eb003', '7a1f0d2c4e5b4d8a9c3f6b1ea004',
   '配套地块数据录入', NULL, NULL, NULL, NULL, 2,
   'land:data:facility', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '配套项目录入 + 29 环节进度：列表 / 详情 / 按宗地查配套 / 名称与宗地校验 / 新增 / 编辑 / 移除 / 环节进度树与保存 / 预计结束时间推算', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 挂在「配套信息批量导入」菜单下（a005）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1eb004', '7a1f0d2c4e5b4d8a9c3f6b1ea005',
   '配套信息批量导入', NULL, NULL, NULL, NULL, 2,
   'land:data:facilityImport', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '配套信息批量导入：模板下载 / 字段字典 / 重复策略 / 预览校验（含宗地存在性校验与孤儿清单）/ 确认入库 / 错误回执下载 / 导入记录', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 挂在「配套附件管理」菜单下（a006）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1eb005', '7a1f0d2c4e5b4d8a9c3f6b1ea006',
   '配套附件管理', NULL, NULL, NULL, NULL, 2,
   'land:data:attachment', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '附件管理：分页查询 / 按业务对象查询 / 概览与类型分布 / 允许类型 / 登记上传 / 下载 / 预览地址 / 删除', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 挂在「数据更新与移除」菜单下（a007）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1eb006', '7a1f0d2c4e5b4d8a9c3f6b1ea007',
   '数据更新与移除', NULL, NULL, NULL, NULL, 2,
   'land:data:recycle', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '数据更新与移除：回收站列表与计数 / 恢复（含唯一键冲突检测）/ 批量恢复 / 变更留痕分页 / 完整履历 / 动作分布与下拉', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) 授权给「管理员」角色
--    ✅ MD5(CONCAT('role-perm:', p.id)) —— 确定性、32 位、不会撞主键
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT MD5(CONCAT('role-perm:', p.`id`)), 'f6817f48af4fb3af11b9e8bf182f618b', p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1eb%'
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 5) 守卫查询
--    5.1 应输出 6 行，每行「已授权 admin」，id 长度全部 32
-- ---------------------------------------------------------------------------
SELECT p.`perms` AS 权限码, p.`name` AS 按钮, LENGTH(p.`id`) AS id长度,
       parent.`name` AS 挂在菜单,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_permission` parent ON parent.`id` = p.`parent_id`
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1eb%' AND p.`del_flag` = 0
ORDER BY p.`perms`;

--    5.2 应输出 0 行：按钮的父菜单不存在（角色授权里看不到归属）
SELECT p.`id`, p.`name` AS 孤儿按钮
FROM `sys_permission` p
LEFT JOIN `sys_permission` parent ON parent.`id` = p.`parent_id`
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1eb%'
  -- ★ 必须排除 parent_id 为空的行：一级菜单「数据管理」本来就没有父级，
  --   不排除它就会多出一个「孤儿菜单」的假告警，让人以为脚本建错了
  AND (p.`parent_id` IS NOT NULL AND p.`parent_id` <> '')
  AND parent.`id` IS NULL;

--    5.3 应输出 0 行：本模块 id 长度超过 32 位
SELECT p.`id`, LENGTH(p.`id`) AS id长度, p.`name` AS 按钮
FROM `sys_permission` p
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%' AND LENGTH(p.`id`) > 32;

--    5.4 ★ 关键一致性守卫：菜单数（6）+ 按钮数（6）应各为 6
SELECT p.`menu_type` AS 类型,
       CASE p.`menu_type` WHEN 0 THEN '一级菜单' WHEN 1 THEN '二级菜单' WHEN 2 THEN '按钮权限' END AS 类型名,
       COUNT(*) AS 行数,
       CASE p.`menu_type` WHEN 0 THEN 1 WHEN 1 THEN 6 WHEN 2 THEN 6 END AS 预期行数
FROM `sys_permission` p
WHERE p.`id` LIKE '7a1f0d2c4e5b4d8a9c3f6b1e%' AND p.`del_flag` = 0
GROUP BY p.`menu_type`
ORDER BY p.`menu_type`;

--    5.5 ★ 代码与库的权限码比对：应输出 0 行「不一致」
--        代码里的 6 个 @RequiresPermissions 值（按模块顺序）
SELECT 'land:data:import' AS 代码权限码,
       CASE WHEN EXISTS (SELECT 1 FROM `sys_permission` WHERE `perms` = 'land:data:import' AND `del_flag` = 0)
            THEN '一致' ELSE '★缺失' END AS 比对结果
UNION ALL SELECT 'land:data:land',
       CASE WHEN EXISTS (SELECT 1 FROM `sys_permission` WHERE `perms` = 'land:data:land' AND `del_flag` = 0)
            THEN '一致' ELSE '★缺失' END
UNION ALL SELECT 'land:data:facility',
       CASE WHEN EXISTS (SELECT 1 FROM `sys_permission` WHERE `perms` = 'land:data:facility' AND `del_flag` = 0)
            THEN '一致' ELSE '★缺失' END
UNION ALL SELECT 'land:data:facilityImport',
       CASE WHEN EXISTS (SELECT 1 FROM `sys_permission` WHERE `perms` = 'land:data:facilityImport' AND `del_flag` = 0)
            THEN '一致' ELSE '★缺失' END
UNION ALL SELECT 'land:data:attachment',
       CASE WHEN EXISTS (SELECT 1 FROM `sys_permission` WHERE `perms` = 'land:data:attachment' AND `del_flag` = 0)
            THEN '一致' ELSE '★缺失' END
UNION ALL SELECT 'land:data:recycle',
       CASE WHEN EXISTS (SELECT 1 FROM `sys_permission` WHERE `perms` = 'land:data:recycle' AND `del_flag` = 0)
            THEN '一致' ELSE '★缺失' END;
