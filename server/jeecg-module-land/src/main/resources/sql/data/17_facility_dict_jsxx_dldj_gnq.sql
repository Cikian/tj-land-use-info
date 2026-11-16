-- =============================================================================
-- 配套三项业务字典入库（P1-05 整改）：建设性质 / 道路等级 / 功能区
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 为什么单独一个脚本、而不是改 01_data_dict.sql：
--   01 是「数据管理六大字典」的建库脚本，含 DELETE + 重建整套；本脚本只补三个
--   字典，且**必须能安稳地打在已有生产库上**（生产库里 01 早就执行过）。
--   两份脚本各管各的，谁先执行都不影响对方。
--
-- 建立 3 个字典（dict_code 与后端硬编码数组同源）：
--   land_jsxx  建设性质  新建 / 改建 / 扩建 / 翻建 / 现状 / 其他
--   land_dldj  道路等级  城市主干路 / 城市次干路 / 城市支路 / 快速路
--   land_gnq   功能区    生态城 / 土地发展中心 / 经开区 / 高新区 / 保税区
--
-- ★★ 这三个字典与 Java 硬编码数组**同源**（改一处必须同步另一处）：
--   · 界面下拉 / 导出显示读字典（sys_dict）；
--   · 导入校验与模板「填表说明」读 Java：
--       FacilityImportField.JSXX_OPTIONS / DLDJ_OPTIONS / GNQ_OPTIONS
--   只改一边的后果是「页面能选、导入说不在允许取值内」（P1-05 测试现象就是这个）。
--   本脚本末尾的核对查询 4.3 会把两边逐值比出来，执行完请扫一眼。
--
-- ★ 16 个行政区（land_xzqh）**原样不动**：本脚本不 DELETE 也不 INSERT 任何
--   land_xzqh 的行，只在 4.4 里核对它仍然是 16 项。
--   功能区是「片区分设机构」，不是行政区 —— 2026-11 起它单独成列（t_supporting_facilities.gnq，
--   见 14_facility_gnq_and_dict.sql），因此这里单列一个 land_gnq 字典，
--   绝不能把「生态城」等值塞进 land_xzqh（那样按 16 区统计会多出几行对不上总数的分类）。
--
-- ★ 幂等性说明（可重复执行，重复执行结果不变）：
--   1) sys_dict：按 dict_code 判断，已存在就<b>不插入</b>（保留库里既有 id，
--      避免「二次执行把字典 id 换掉、界面绑定的 dictCode 缓存失效」）；
--      因此本脚本的固定 id 只在「首次入库」时生效。
--   2) sys_dict_item：先按 dict_code 找到字典 id、删掉它的全部项，再按固定 id 重新插入。
--      字典项没有业务外键引用（只被界面按 dictCode 读取），删后重建是安全的，
--      且能保证「项数/取值/排序」与脚本完全一致，不会残留上一版的旧项。
--   3) 全部 id 都是固定常量（见 4.5 的核对），可重复执行、结果可复现，不用随机 UUID。
--
-- 执行顺序：14_facility_gnq_and_dict.sql（建 gnq 列 + 迁历史数据）→ 本脚本。
--   反过来也能跑（本脚本只碰 sys_dict），但那样 gnq 列还没建，功能区值无处可存。
--
-- 执行后前端字典缓存需刷新（重新登录，或在「系统管理 → 数据字典」里点一次刷新）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 字典（仅缺失时插入；保留已有 id）
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
SELECT '7a1f0d2c4e5b4d8a9c3f6b1ed108', '配套-建设性质', 'land_jsxx',
       '配套项目建设性质（含「现状」：已建成、无新建改建动作的配套）。★ 与 FacilityImportField.JSXX_OPTIONS 同源',
       0, 'admin', NOW(), NULL, NULL, 0
 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `dict_code` = 'land_jsxx');

INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
SELECT '7a1f0d2c4e5b4d8a9c3f6b1ed109', '配套-道路等级', 'land_dldj',
       '配套道路等级（含「快速路」：业务确认为独立一档，不并入主干路）。★ 与 FacilityImportField.DLDJ_OPTIONS 同源',
       0, 'admin', NOW(), NULL, NULL, 0
 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `dict_code` = 'land_dldj');

INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
SELECT '7a1f0d2c4e5b4d8a9c3f6b1ed110', '配套-功能区', 'land_gnq',
       '滨海等功能区（片区分设机构，非行政区）。★ 与 FacilityImportField.GNQ_OPTIONS 同源；导入侧只提示不拦截',
       0, 'admin', NOW(), NULL, NULL, 0
 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `dict_code` = 'land_gnq');

-- ---------------------------------------------------------------------------
-- 2) 清理这三个字典的旧项（按 dict_code 定位，重复执行安全）
--    ★ 只删这三个字典的项，绝不碰 land_xzqh（16 个行政区原样保留）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_dict_item`
 WHERE `dict_id` IN (
   SELECT `id` FROM `sys_dict`
    WHERE `dict_code` IN ('land_jsxx', 'land_dldj', 'land_gnq'));

-- ---------------------------------------------------------------------------
-- 3) 字典项（item_text = item_value = 中文原值，与库里既有业务枚举同一口径）
--    dict_id 用子查询取：即使字典是别人在界面上先建的（id 与本脚本不同）也能挂对
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict_item`
  (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT t.`id`, (SELECT `id` FROM `sys_dict` WHERE `dict_code` = t.`dict_code`),
       t.`item_text`, t.`item_value`, t.`description`, t.`sort_order`, 1,
       'admin', NOW(), NULL, NULL
  FROM (
    -- ===== 建设性质（6 项）=====
    SELECT '7a1f0d2c4e5b4d8a9c3f6b1ej101' AS `id`, 'land_jsxx' AS `dict_code`,
           '新建' AS `item_text`, '新建' AS `item_value`, '新立项建设的配套' AS `description`, 1 AS `sort_order`
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ej102', 'land_jsxx', '改建', '改建', '在原有设施上改建', 2
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ej103', 'land_jsxx', '扩建', '扩建', '在原有设施上扩建', 3
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ej104', 'land_jsxx', '翻建', '翻建', '拆除后原址重建', 4
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ej105', 'land_jsxx', '现状', '现状',
           '★ P1-05 新增：已经建成、没有新建/改建动作的配套按现状填报', 5
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ej106', 'land_jsxx', '其他', '其他', '无法归入以上四类', 6

    -- ===== 道路等级（4 项）=====
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ek101', 'land_dldj', '城市主干路', '城市主干路', '主干路', 1
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ek102', 'land_dldj', '城市次干路', '城市次干路', '次干路', 2
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ek103', 'land_dldj', '城市支路', '城市支路', '支路', 3
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ek104', 'land_dldj', '快速路', '快速路',
           '★ P1-05 新增：快速路既不是主干路也不是次干路，独立一档（不并入主干路，避免改掉业务事实）', 4

    -- ===== 功能区（5 项，与 14 脚本的历史数据迁入口径一致）=====
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1el101', 'land_gnq', '生态城', '生态城', '中新天津生态城', 1
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1el102', 'land_gnq', '土地发展中心', '土地发展中心', '市土地利用发展中心', 2
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1el103', 'land_gnq', '经开区', '经开区', '经济技术开发区', 3
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1el104', 'land_gnq', '高新区', '高新区', '高新技术产业开发区', 4
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1el105', 'land_gnq', '保税区', '保税区', '保税港区', 5
  ) t
 WHERE EXISTS (SELECT 1 FROM `sys_dict` d WHERE d.`dict_code` = t.`dict_code`);

-- ---------------------------------------------------------------------------
-- 4) 核对查询（执行后逐条看结果，注释里写的是「期望值」）
-- ---------------------------------------------------------------------------

-- 4.1 三个字典的项数：应输出 3 行 —— land_jsxx=6 / land_dldj=4 / land_gnq=5
SELECT d.`dict_code`, d.`dict_name` AS 字典, COUNT(i.`id`) AS 项数
FROM `sys_dict` d
LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_jsxx', 'land_dldj', 'land_gnq')
GROUP BY d.`dict_code`, d.`dict_name`
ORDER BY d.`dict_code`;

-- 4.2 项数是否与预期一致：应输出 0 行
SELECT d.`dict_code`, COUNT(i.`id`) AS 实际项数,
       CASE d.`dict_code`
         WHEN 'land_jsxx' THEN 6
         WHEN 'land_dldj' THEN 4
         WHEN 'land_gnq'  THEN 5
       END AS 预期项数
FROM `sys_dict` d
LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_jsxx', 'land_dldj', 'land_gnq')
GROUP BY d.`dict_code`
HAVING 实际项数 <> 预期项数;

-- 4.3 ★ 关键一致性守卫：字典取值必须逐字等于后端硬编码数组
--     应输出 15 行（6+4+5），且每行「与后端一致」= 是
SELECT d.`dict_code` AS 字典, i.`item_value` AS 字典取值,
       CASE
         WHEN d.`dict_code` = 'land_jsxx'
              AND i.`item_value` IN ('新建', '改建', '扩建', '翻建', '现状', '其他')        THEN '是'
         WHEN d.`dict_code` = 'land_dldj'
              AND i.`item_value` IN ('城市主干路', '城市次干路', '城市支路', '快速路')      THEN '是'
         WHEN d.`dict_code` = 'land_gnq'
              AND i.`item_value` IN ('生态城', '土地发展中心', '经开区', '高新区', '保税区') THEN '是'
         ELSE '★否，后端数组里没有这个值'
       END AS 与后端一致
FROM `sys_dict_item` i
JOIN `sys_dict` d ON d.`id` = i.`dict_id`
WHERE d.`dict_code` IN ('land_jsxx', 'land_dldj', 'land_gnq')
ORDER BY d.`dict_code`, i.`sort_order`;

-- 4.4 同一字典内取值重复：应输出 0 行（重复项会让下拉出现两个一模一样的选项）
SELECT d.`dict_code`, i.`item_value`, COUNT(*) AS 重复次数
FROM `sys_dict_item` i
JOIN `sys_dict` d ON d.`id` = i.`dict_id`
WHERE d.`dict_code` IN ('land_jsxx', 'land_dldj', 'land_gnq')
GROUP BY d.`dict_code`, i.`item_value`
HAVING COUNT(*) > 1;

-- 4.5 固定 id 落库自检：应输出 1 行 —— 字典 3 / 字典项 15
--     说明：id 是主键，「数量对上」即意味着既不缺、也不重（重复会被主键挡在插入阶段）。
--     取值均为 28 字符，与 sys_dict / sys_dict_item 的 varchar(32) 一致，不会 ERROR 1406。
SELECT
  (SELECT COUNT(*) FROM `sys_dict`
    WHERE `id` IN ('7a1f0d2c4e5b4d8a9c3f6b1ed108',
                   '7a1f0d2c4e5b4d8a9c3f6b1ed109',
                   '7a1f0d2c4e5b4d8a9c3f6b1ed110')) AS 字典落库数,
  (SELECT COUNT(*) FROM `sys_dict_item`
    WHERE `id` IN ('7a1f0d2c4e5b4d8a9c3f6b1ej101',
                   '7a1f0d2c4e5b4d8a9c3f6b1ej102',
                   '7a1f0d2c4e5b4d8a9c3f6b1ej103',
                   '7a1f0d2c4e5b4d8a9c3f6b1ej104',
                   '7a1f0d2c4e5b4d8a9c3f6b1ej105',
                   '7a1f0d2c4e5b4d8a9c3f6b1ej106',
                   '7a1f0d2c4e5b4d8a9c3f6b1ek101',
                   '7a1f0d2c4e5b4d8a9c3f6b1ek102',
                   '7a1f0d2c4e5b4d8a9c3f6b1ek103',
                   '7a1f0d2c4e5b4d8a9c3f6b1ek104',
                   '7a1f0d2c4e5b4d8a9c3f6b1el101',
                   '7a1f0d2c4e5b4d8a9c3f6b1el102',
                   '7a1f0d2c4e5b4d8a9c3f6b1el103',
                   '7a1f0d2c4e5b4d8a9c3f6b1el104',
                   '7a1f0d2c4e5b4d8a9c3f6b1el105')) AS 字典项落库数;

-- 4.6 行政区划未被牵连：应输出 16（且应仍是原来那 16 个区，不含任何功能区值）
SELECT COUNT(*) AS 行政区划项数
FROM `sys_dict_item` i
JOIN `sys_dict` d ON d.`id` = i.`dict_id`
WHERE d.`dict_code` = 'land_xzqh';

SELECT i.`item_value` AS 行政区划取值
FROM `sys_dict_item` i
JOIN `sys_dict` d ON d.`id` = i.`dict_id`
WHERE d.`dict_code` = 'land_xzqh'
  AND i.`item_value` NOT IN (
    '和平区','河东区','河西区','南开区','河北区','红桥区','东丽区','西青区',
    '津南区','北辰区','武清区','宝坻区','滨海新区','宁河区','静海区','蓟州区'
  )
ORDER BY i.`item_value`;   -- 应输出 0 行：功能区值不得混进行政区划字典
