-- =============================================================================
-- 提级论证管理 · 接口权限（按钮权限 menu_type=2）与角色授权
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- ★ 为什么必须建成「按钮权限」而不是写在菜单上
--   jeecg 的权限分两条链路：
--     ① 后端 @RequiresPermissions → ShiroRealm → SysBaseApiImpl.getUserPermissionSet()
--        → SysPermissionMapper.queryByUser()：把该用户角色下**所有** perms 非空的行都收进来
--        （不区分 menu_type），所以权限码写在菜单行上后端也能拦住；
--     ② 前端 v-has 指令 / 菜单管理的按钮授权 → SysPermissionController.getUserPermissionJsonArray()
--        → getAuthJsonArray()：**只收 menu_type=2（按钮）且 status=1 的行**。
--   把权限码写在 menu_type=1 的菜单行上，链路 ② 拿不到任何条目，结果就是
--   「菜单管理里看不到任何可授权的接口权限」「前端 v-has 一律判定无权限」。
--   因此本脚本把权限码全部下沉为**按钮权限**（02 脚本里的菜单行 perms 均为 NULL）。
--
-- ★ 权限码规范：land:{模块}:{操作}（与档案模块 land:archive:* 保持一致）
--   同一权限码只定义一次（按钮挂在最相关的菜单下），其它页面通过 v-has 复用同一个码，
--   避免同一 perms 出现多行导致「角色授权时要勾好几个同名按钮」。
--   对照关系（设计文档 2.5）：
--     land:escalation:list      列表 / 详情 / 材料列表 / 意见记录 / 台账 / 统计图
--     land:escalation:add       新增 / 生成编号 / 编号校验
--     land:escalation:edit      编辑 / 材料上传删除 / ★登记审核意见 / 登记办理状态
--     land:escalation:delete    删除 / 批量删除
--     land:escalation:download  材料下载 / 预览
--     land:escalation:export    查询结果导出 / 台账导出
--
-- ★ 按钮 id 前缀：档案模块 ...d5e6ba、收发文模块 ...d5e6bb、本模块 ...d5e6bc
--   （三个脚本各自按前缀清理，互不误删）
--
-- 脚本可重复执行：先按按钮 id 前缀清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理（幂等）
--    本模块按钮权限的 id 前缀固定为 a1b2c3d4e5f6470891a2b3c4d5e6bc
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%';
DELETE FROM `sys_permission`
 WHERE `id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%';

-- ---------------------------------------------------------------------------
-- 2) 兜底：清空本模块菜单行上的权限码
--    （02 脚本插入菜单时 perms 已经是 NULL，这里再确保一次，防止手工改过库）
-- ---------------------------------------------------------------------------
UPDATE `sys_permission` SET `perms` = NULL, `perms_type` = '0'
 WHERE `id` IN (
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d01',   -- 提级论证管理（一级）
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02',   -- 项目录入
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d03',   -- 查询统计
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d04',   -- 资料及台账管理
   'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d05');  -- 提级论证审批

-- ---------------------------------------------------------------------------
-- 3) 按钮权限（menu_type=2）
--    字段形态与 jeecg 代码生成器产物 / 库内既有按钮行保持一致：
--    url/component/component_name/redirect/icon 为 NULL，is_route=1，is_leaf=1，
--    keep_alive=0，hidden=0，hide_tab=0，perms_type='1'，status='1'，del_flag=0
-- ---------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `component_name`, `redirect`, `menu_type`,
   `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_route`, `is_leaf`, `keep_alive`,
   `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`, `rule_flag`,
   `status`, `internal_or_external`)
VALUES
  -- ===== 挂在「项目录入」（parent = e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02）=====
  ('a1b2c3d4e5f6470891a2b3c4d5e6bc01', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02',
   '提级论证查看', NULL, NULL, NULL, NULL, 2,
   'land:escalation:list', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '提级论证列表 / 详情 / 材料列表 / 审核意见记录 / 台账 / 统计图（录入、查询统计、台账、审批四个页面共用）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bc02', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02',
   '提级论证新增', NULL, NULL, NULL, NULL, 2,
   'land:escalation:add', '1', 2.00, 0, NULL, 1, 1, 0,
   0, 0, '新增提级论证项目 / 生成项目编号 / 项目编号唯一校验', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bc03', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02',
   '提级论证编辑', NULL, NULL, NULL, NULL, 2,
   'land:escalation:edit', '1', 3.00, 0, NULL, 1, 1, 0,
   0, 0, '编辑项目 / 上传与删除材料 / ★登记审核意见 / 登记办理状态与论证结果', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bc04', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02',
   '提级论证删除', NULL, NULL, NULL, NULL, 2,
   'land:escalation:delete', '1', 4.00, 0, NULL, 1, 1, 0,
   0, 0, '删除 / 批量删除提级论证项目', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6bc05', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d02',
   '提级论证材料下载', NULL, NULL, NULL, NULL, 2,
   'land:escalation:download', '1', 5.00, 0, NULL, 1, 1, 0,
   0, 0, '下载 / 在线预览提级论证材料文件', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 挂在「查询统计」（parent = e5c1d2e3f4a54b6c8d9e0f1a2b3c4d03）=====
  ('a1b2c3d4e5f6470891a2b3c4d5e6bc06', 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d03',
   '提级论证导出', NULL, NULL, NULL, NULL, 2,
   'land:escalation:export', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '查询结果导出 / 台账导出（xlsx）', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) 对齐按钮行的字段形态
--    库内 jeecg 自带按钮权限（如「Online表单开发 → 代码生成按钮」）的
--    keep_alive / hide_tab / internal_or_external 都是 NULL；
--    SysPermission 里这三个字段是基本类型 boolean，NULL 会被映射成 false，行为等价。
--    ★ 这里只处理本模块的按钮，避免影响档案/收发文模块（它们的脚本已各自处理过）。
-- ---------------------------------------------------------------------------
UPDATE `sys_permission`
   SET `keep_alive` = NULL, `hide_tab` = NULL, `internal_or_external` = NULL
 WHERE `id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%';

-- ---------------------------------------------------------------------------
-- 5) 授权给「管理员」角色（role_code = admin）
--    如需授权给其它角色，把下面的 role_id 换成对应 sys_role.id 即可；
--    也可以在「系统管理 → 角色管理 → 授权」里按按钮勾选。
--
--    sys_role_permission.id 是 varchar(32)，且唯一。这里用「rp + 权限ID的后 30 位」
--    拼出确定性主键，而不是 UUID()：
--      · UUID() 在本机 MySQL 5.7.26（Windows）上同一条语句内的高频调用会撞主键；
--      · 确定性主键让脚本天然幂等（同一权限重复授权是同一条记录，不会越滚越多）。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_permission`
  (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('rp', SUBSTRING(p.`id`, 3)), 'f6817f48af4fb3af11b9e8bf182f618b', p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%'
  AND p.`del_flag` = 0
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 6) 守卫查询
--    6.1 应输出 0 行：有按钮权限子节点、但 is_leaf 仍是 1 的菜单
--        （一旦有输出，说明按钮在菜单管理/角色授权里看不到）
-- ---------------------------------------------------------------------------
SELECT p.`name` AS 有问题菜单, p.`is_leaf`, COUNT(c.`id`) AS 按钮数
FROM `sys_permission` p
JOIN `sys_permission` c ON c.`parent_id` = p.`id` AND c.`menu_type` = 2 AND c.`del_flag` = 0
WHERE p.`del_flag` = 0 AND p.`is_leaf` = 1
  AND p.`id` LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%'
GROUP BY p.`id`, p.`name`, p.`is_leaf`;

--    6.2 应输出 6 行，且全部「已授权 admin」
SELECT p.`perms` AS 权限码, p.`name` AS 按钮名, m.`name` AS 所属菜单,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_permission` m ON m.`id` = p.`parent_id`
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6bc%'
  AND p.`del_flag` = 0
ORDER BY m.`sort_no`, p.`sort_no`;

--    6.3 应输出 3 行：本模块 3 个权限码组合的前缀一致性检查
--        （确认没有把权限码写到菜单行上）
SELECT p.`name` AS 菜单, p.`menu_type`, p.`perms`
FROM `sys_permission` p
WHERE p.`id` LIKE 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4d%'
  AND p.`perms` IS NOT NULL;
