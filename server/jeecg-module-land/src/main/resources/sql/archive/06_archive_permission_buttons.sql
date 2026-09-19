-- =============================================================================
-- 档案管理 · 接口权限（按钮权限 menu_type=2）与角色授权
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- ★ 为什么必须建成「按钮权限」而不是写在菜单上
--   jeecg 的权限分两条链路：
--     ① 后端 @RequiresPermissions  → ShiroRealm → SysBaseApiImpl.getUserPermissionSet()
--        → SysPermissionMapper.queryByUser()：把该用户角色下**所有** perms 非空的行都收进来
--        （不区分 menu_type），所以权限码写在菜单行上后端也能拦住；
--     ② 前端 v-has 指令 / 菜单管理的按钮授权 → SysPermissionController.getUserPermissionJsonArray()
--        → getAuthJsonArray()：**只收 menu_type=2（按钮）且 status=1 的行**。
--   把权限码写在 menu_type=1 的菜单行上，链路 ② 拿不到任何条目，结果就是
--   「菜单管理里看不到任何可授权的接口权限」「前端 v-has 一律判定无权限」。
--   因此本脚本把权限码全部下沉为**按钮权限**，并清空菜单行上的 perms。
--
-- ★ 权限码规范：land:{模块}:{操作}
--   同一权限码只定义一次（按钮挂在最相关的菜单下），其它页面通过 v-has 复用同一个码，
--   避免同一 perms 出现多行导致「角色授权时要勾好几个同名按钮」。
--
-- ★ 按钮 id 前缀：档案模块用 ...d5e6ba，收发文模块用 ...d5e6bb
--   （两个脚本各自按前缀清理，互不误删；脚本 04 在 sql/document 目录下）
--
-- 脚本可重复执行：先按按钮 id 前缀清理（含角色授权），再插入、再授权。
-- 执行后需重新登录（或清理 Redis 里的 shiro 权限缓存）才会生效。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) 清理（幂等）
--    本模块按钮权限的 id 前缀固定为 a1b2c3d4e5f6470891a2b3c4d5e6ba
-- ---------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6ba%';
DELETE FROM `sys_permission`
 WHERE `id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6ba%';

-- 2) 清空菜单行上的权限码：权限码只保留在按钮权限上，避免「菜单 + 按钮」两处重复
UPDATE `sys_permission` SET `perms` = NULL, `perms_type` = '0'
 WHERE `id` IN (
   '7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30',   -- 档案管理（一级）
   '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41',   -- 档案类别管理
   'a1b2c3d4e5f6470891a2b3c4d5e6f701',   -- 档案维护
   'a1b2c3d4e5f6470891a2b3c4d5e6f702',   -- 档案查询
   'a1b2c3d4e5f6470891a2b3c4d5e6f703'    -- 档案统计
 );

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
  -- ===== 档案维护（parent = a1b2c3d4e5f6470891a2b3c4d5e6f701）=====
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba01', 'a1b2c3d4e5f6470891a2b3c4d5e6f701',
   '档案查询', NULL, NULL, NULL, NULL, 2,
   'land:archive:list', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '档案列表 / 详情 / 卷内文件 / 操作记录 / 生成档案号 / 档案号校验', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba02', 'a1b2c3d4e5f6470891a2b3c4d5e6f701',
   '档案新增', NULL, NULL, NULL, NULL, 2,
   'land:archive:add', '1', 2.00, 0, NULL, 1, 1, 0,
   0, 0, '新增档案（含上传卷内文件）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba03', 'a1b2c3d4e5f6470891a2b3c4d5e6f701',
   '档案编辑', NULL, NULL, NULL, NULL, 2,
   'land:archive:edit', '1', 3.00, 0, NULL, 1, 1, 0,
   0, 0, '编辑档案与其卷内文件', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba04', 'a1b2c3d4e5f6470891a2b3c4d5e6f701',
   '档案删除', NULL, NULL, NULL, NULL, 2,
   'land:archive:delete', '1', 4.00, 0, NULL, 1, 1, 0,
   0, 0, '删除档案、批量删除档案、删除单个卷内文件', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba05', 'a1b2c3d4e5f6470891a2b3c4d5e6f701',
   '档案状态变更', NULL, NULL, NULL, NULL, 2,
   'land:archive:status', '1', 5.00, 0, NULL, 1, 1, 0,
   0, 0, '变更档案状态（未归档 / 归档中 / 审核中 / 已归档）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba06', 'a1b2c3d4e5f6470891a2b3c4d5e6f701',
   '档案文件下载', NULL, NULL, NULL, NULL, 2,
   'land:archive:download', '1', 6.00, 0, NULL, 1, 1, 0,
   0, 0, '下载单个卷内文件（服务端只接受 fileId，路径由后端解析）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba07', 'a1b2c3d4e5f6470891a2b3c4d5e6f701',
   '档案导出', NULL, NULL, NULL, NULL, 2,
   'land:archive:export', '1', 7.00, 0, NULL, 1, 1, 0,
   0, 0, '按查询条件导出 ZIP（档案维护 / 档案查询 / 档案统计共用）', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 档案统计（parent = a1b2c3d4e5f6470891a2b3c4d5e6f703）=====
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba08', 'a1b2c3d4e5f6470891a2b3c4d5e6f703',
   '档案统计查询', NULL, NULL, NULL, NULL, 2,
   'land:archive:stat', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '档案统计（总览 / 类别分布 / 年度趋势 / 按项目）', 'admin', NOW(), 0, 0, '1', 0),

  -- ===== 档案类别管理（parent = 8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41）=====
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba21', '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41',
   '类别查询', NULL, NULL, NULL, NULL, 2,
   'land:archiveCategory:list', '1', 1.00, 0, NULL, 1, 1, 0,
   0, 0, '档案类别树 / 类别详情（档案、收发文页面的类别选择器也复用此权限）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba22', '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41',
   '类别新增', NULL, NULL, NULL, NULL, 2,
   'land:archiveCategory:add', '1', 2.00, 0, NULL, 1, 1, 0,
   0, 0, '新建档案类别（含同级重名校验）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba23', '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41',
   '类别编辑', NULL, NULL, NULL, NULL, 2,
   'land:archiveCategory:edit', '1', 3.00, 0, NULL, 1, 1, 0,
   0, 0, '类别更名 / 移动 / 编码别名说明', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba24', '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41',
   '类别移除', NULL, NULL, NULL, NULL, 2,
   'land:archiveCategory:delete', '1', 4.00, 0, NULL, 1, 1, 0,
   0, 0, '移除档案类别（前置校验：无子类别且类别下无档案）', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba25', '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41',
   '类别排序', NULL, NULL, NULL, NULL, 2,
   'land:archiveCategory:sort', '1', 5.00, 0, NULL, 1, 1, 0,
   0, 0, '同级排序 / 拖拽移动落库', 'admin', NOW(), 0, 0, '1', 0),
  ('a1b2c3d4e5f6470891a2b3c4d5e6ba26', '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41',
   '类别启停', NULL, NULL, NULL, NULL, 2,
   'land:archiveCategory:status', '1', 6.00, 0, NULL, 1, 1, 0,
   0, 0, '启用 / 停用档案类别（停用级联整棵子树）', 'admin', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------------------------
-- 4) ★ 把「挂了按钮权限的菜单」的 is_leaf 置为 0
--
--    这是最容易漏、而且从代码上完全看不出来的一步：
--    jeecg 建菜单树时是
--        if (!permission.isLeaf()) { 递归子节点 }
--    （见 SysPermissionController.getTreeList / SysRoleController.getTreeModelList）
--    也就是说 **菜单行的 is_leaf 一旦是 1，它的子节点就再也不会被遍历到**。
--    菜单下面挂了 menu_type=2 的按钮权限时，后果是：
--      · 「系统管理 → 菜单管理」里看不到这些按钮；
--      · 「系统管理 → 角色管理 → 授权」里也勾不到这些按钮；
--      · 而数据库里那 27 行按钮权限其实是存在的 —— 排查起来非常费劲。
--
--    本机实测：所有 jeecg 自带菜单（用户管理、Online表单开发…）只要是按钮的父节点，
--    is_leaf 一律为 0。所以这里显式修正，并留一条守卫查询在脚本末尾。
-- ---------------------------------------------------------------------------
UPDATE `sys_permission` SET `is_leaf` = 0
 WHERE `id` IN (
   '8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41',   -- 档案类别管理（6 个按钮）
   'a1b2c3d4e5f6470891a2b3c4d5e6f701',   -- 档案维护（7 个按钮）
   'a1b2c3d4e5f6470891a2b3c4d5e6f703'    -- 档案统计（1 个按钮）
 );
-- 「档案查询」没有按钮子节点，保持 is_leaf = 1（它是真正的叶子路由）

-- ---------------------------------------------------------------------------
-- 5) 对齐按钮行的字段形态（与库内 jeecg 自带按钮权限、代码生成器产物保持一致）
--    参考行：「Online表单开发 → 代码生成按钮」的
--            keep_alive / hide_tab / internal_or_external 都是 NULL。
--    SysPermission 里这三个字段是基本类型 boolean，NULL 会被映射成 false，行为等价。
-- ---------------------------------------------------------------------------
UPDATE `sys_permission`
   SET `keep_alive` = NULL, `hide_tab` = NULL, `internal_or_external` = NULL
 WHERE `perms` LIKE 'land:%';

-- ---------------------------------------------------------------------------
-- 6) 按钮改名：避免与同名的「档案查询」页面菜单混淆
--    按钮「land:archive:list」覆盖的是列表/详情/附件/日志等读取类接口，
--    而「档案查询」是另一个页面菜单，两者同名会在菜单管理与角色授权树里分不清。
-- ---------------------------------------------------------------------------
UPDATE `sys_permission`
   SET `name` = '档案查看',
       `description` = '档案列表 / 详情 / 卷内文件 / 操作记录 / 生成档案号 / 档案号校验'
 WHERE `id` = 'a1b2c3d4e5f6470891a2b3c4d5e6ba01';

-- ---------------------------------------------------------------------------
-- 7) 授权给「管理员」角色（role_code = admin）
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
WHERE p.`id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6ba%'
  AND p.`del_flag` = 0
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- ---------------------------------------------------------------------------
-- 8) 守卫查询
--    8.1 应输出 0 行：有按钮权限子节点、但 is_leaf 仍是 1 的菜单
--        （一旦有输出，说明按钮在菜单管理/角色授权里看不到）
-- ---------------------------------------------------------------------------
SELECT p.`name` AS 有问题菜单, p.`is_leaf`, COUNT(c.`id`) AS 按钮数
FROM `sys_permission` p
JOIN `sys_permission` c ON c.`parent_id` = p.`id` AND c.`menu_type` = 2 AND c.`del_flag` = 0
WHERE p.`del_flag` = 0 AND p.`is_leaf` = 1
GROUP BY p.`id`, p.`name`, p.`is_leaf`;

-- 8.2 应输出 14 条（档案 8 + 类别 6），且全部已授权给 admin
SELECT p.`perms` AS 权限码, p.`name` AS 按钮名, m.`name` AS 所属菜单,
       CASE WHEN rp.`id` IS NULL THEN '未授权' ELSE '已授权 admin' END AS 授权状态
FROM `sys_permission` p
LEFT JOIN `sys_permission` m ON m.`id` = p.`parent_id`
LEFT JOIN `sys_role_permission` rp
       ON rp.`permission_id` = p.`id`
      AND rp.`role_id` = 'f6817f48af4fb3af11b9e8bf182f618b'
WHERE p.`id` LIKE 'a1b2c3d4e5f6470891a2b3c4d5e6ba%'
  AND p.`del_flag` = 0
ORDER BY m.`sort_no`, p.`sort_no`;
