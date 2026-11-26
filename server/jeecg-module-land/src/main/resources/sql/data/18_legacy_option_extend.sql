-- =============================================================================
-- 旧库全量数据迁移 · 取值扩展（枚举值扩档 + 补齐字典项）
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
-- 建立日期：2026-11-23
--
-- 背景（为什么需要这份脚本）
--   旧系统的「配套设施类别 / 建设性质 / 道路等级 / 资金来源 / 资金落实情况 /
--   审批与开工状态」这几列是**自由文本**（页面给下拉，但历史数据是手工录的）。
--   把旧库 847 条地块 + 1433 条配套全量整理成导入 Excel 时，统计出 400 余处
--   「标准值之外但确有其事」的写法，例如：
--     建设性质：道路 / 城镇其他 / 城镇建设与改造
--     道路等级：次干路、支路 / 步行街 / 管线不涉及
--     资金来源：自筹 / 企业自筹 / PPP模式 / 区内统筹、专项债 / 城市更新成本 …
--     资金落实：已落实（部分） / 正常推进 / 未批复
--     审批状态：已批复 / 已完成 / 未开始 / 未申报 / 不涉及 / 前期筹备 …
--   这些值如果按「归不了位就留空」处理，结果是**业务事实被抹掉**，而且
--   首页的立项率、开工率、资金到位率分母跟着变小 —— 指标静默失真比报错更难查。
--
--   因此本次口径改为：**保留原值 + 原值必须是合法取值**。
--   合法取值的唯一真相仍是 Java 数组（FacilityImportField.XXX_OPTIONS），
--   本脚本负责把同一批值同步进 sys_dict，让界面下拉也能选到、后台也能继续新增。
--
-- ★★ 与 Java 数组同源（改一处必须同步另一处）
--   land_jsxx                ↔ FacilityImportField.JSXX_OPTIONS
--   land_dldj                ↔ FacilityImportField.DLDJ_OPTIONS
--   land_facility_category   ↔ FacilityImportField.PTSSLB_OPTIONS
--   land_zjly                ↔ FacilityImportField.ZJLY_OPTIONS
--   land_zjlsqk              ↔ FacilityImportField.ZJLSQK_OPTIONS
--   land_pfzt                ↔ FacilityImportField.PROGRESS_STATUS_OPTIONS
--   （land_xzqh / land_gnq / land_project_type 本次不动）
--
-- ★ 幂等性（可重复执行）
--   1) sys_dict：按 dict_code 判断，缺失才插入，已存在则**保留库里既有 id**；
--   2) sys_dict_item：只**补缺失项**（按 dict_id + item_value 判重），
--      **不 DELETE 任何既有项** —— 与 17 脚本「删了重建」不同，
--      因为本次要支持「管理员在界面里自己加过项」的场景，删了会把人加的项冲掉。
--   3) 全部 id 是固定常量（28 字符，sys_dict.id 是 varchar(32)）。
--
-- 执行顺序：本脚本只碰 sys_dict / sys_dict_item，与 01 / 17 无先后依赖；
--   但**必须与含扩取值改动的后端版本一起上线**，否则界面下拉有、导入仍会报
--   「不在允许的取值内」（Java 数组才是导入校验的那一侧）。
--
-- 执行后前端字典缓存需刷新（重新登录，或在「系统管理 → 数据字典」点一次刷新）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 缺失的字典（只插入缺的，保留已有 id）
--    land_facility_category / land_jsxx / land_dldj 已由 01 / 17 建立，
--    这里只补三个原本**没有字典**的（页面下拉是写死的）：
--      land_zjly 资金来源 / land_zjlsqk 资金落实情况 / land_pfzt 审批与开工状态
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
SELECT '7a1f0d2c4e5b4d8a9c3f6b1ed111', '配套-资金来源', 'land_zjly',
       '配套项目资金来源。★ 与 FacilityImportField.ZJLY_OPTIONS 同源；2026-11-23 按旧库实有写法扩档',
       0, 'admin', NOW(), NULL, NULL, 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT `dict_code` FROM `sys_dict`) d WHERE d.`dict_code` = 'land_zjly');

INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
SELECT '7a1f0d2c4e5b4d8a9c3f6b1ed112', '配套-资金落实情况', 'land_zjlsqk',
       '配套资金落实情况。★ 与 FacilityImportField.ZJLSQK_OPTIONS 同源；含「已落实（部分）」这一业务中间态',
       0, 'admin', NOW(), NULL, NULL, 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT `dict_code` FROM `sys_dict`) d WHERE d.`dict_code` = 'land_zjlsqk');

INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
SELECT '7a1f0d2c4e5b4d8a9c3f6b1ed113', '配套-审批与开工状态', 'land_pfzt',
       '项建/可研/初设概算批复状态与开工状态共用。★ 与 FacilityImportField.PROGRESS_STATUS_OPTIONS 同源',
       0, 'admin', NOW(), NULL, NULL, 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT `dict_code` FROM `sys_dict`) d WHERE d.`dict_code` = 'land_pfzt');

-- ---------------------------------------------------------------------------
-- 2) 补齐字典项（只补缺的，不动既有项）
--    sort_order 从 101 起：新增值统一排在标准值之后，下拉里一眼能看出「哪些是历史写法」
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict_item`
  (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT t.`id`, d.`id`, t.`item_text`, t.`item_value`, t.`description`, t.`sort_order`, 1,
       'admin', NOW(), NULL, NULL
  FROM (
    -- ===== 配套设施类别（land_facility_category）：+2 =====
              SELECT '7a1f0d2c4e5b4d8a9c3f6b1eq101' AS `id`, 'land_facility_category' AS `dict_code`,
                     '道路及管线' AS `item_text`, '道路及管线' AS `item_value`,
                     '一条配套同时含道路与管线（旧库写法，跨类别，刻意不归位）' AS `description`, 101 AS `sort_order`
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1eq102', 'land_facility_category', '供水、排水', '供水、排水',
                     '一条配套同时含供水与排水（旧库写法，跨类别）', 102

    -- ===== 建设性质（land_jsxx）：+3 =====
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1er101', 'land_jsxx', '道路', '道路', '旧库写法：配套本身就是一条道路', 101
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1er102', 'land_jsxx', '城镇其他', '城镇其他', '旧库写法', 102
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1er103', 'land_jsxx', '城镇建设与改造', '城镇建设与改造', '旧库写法', 103

    -- ===== 道路等级（land_dldj）：+3 =====
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1es101', 'land_dldj', '次干路、支路', '次干路、支路',
                     '一条配套同时含次干路与支路', 101
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1es102', 'land_dldj', '步行街', '步行街', '旧库写法：步行街不属四级道路', 102
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1es103', 'land_dldj', '管线不涉及', '管线不涉及',
                     '旧库写法：该条配套没有道路工程，只有管线', 103

    -- ===== 资金来源（land_zjly）：13 项（含 5 个标准值）=====
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em101', 'land_zjly', '土地整理成本', '土地整理成本', '标准值', 1
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em102', 'land_zjly', '地块收益', '地块收益', '标准值', 2
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em103', 'land_zjly', '成本分摊', '成本分摊', '标准值', 3
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em104', 'land_zjly', '区内统筹', '区内统筹',
                     '标准值（旧库的「区级财政资金 / 区财政 / 区级资金 / 区财政统筹」导入时归到这里）', 4
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em105', 'land_zjly', '其它', '其它', '标准值', 5
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em106', 'land_zjly', '自筹', '自筹', '旧库写法', 101
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em107', 'land_zjly', '企业自筹', '企业自筹', '旧库写法', 102
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em108', 'land_zjly', 'PPP模式', 'PPP模式', '旧库写法', 103
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em109', 'land_zjly', '区内统筹、专项债', '区内统筹、专项债', '旧库写法（两种来源并列）', 104
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em110', 'land_zjly', '城市更新成本', '城市更新成本', '旧库写法', 105
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em111', 'land_zjly', '政府专项债及自有资金', '政府专项债及自有资金', '旧库写法', 106
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em112', 'land_zjly', '财政', '财政', '旧库写法', 107
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em113', 'land_zjly', '自筹及其它', '自筹及其它', '旧库写法', 108
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em114', 'land_zjly', '财政资金和专项债', '财政资金和专项债', '旧库写法（两种来源并列）', 109
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em115', 'land_zjly', '市级资金', '市级资金', '旧库写法', 110
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em116', 'land_zjly', '自筹及银行贷款', '自筹及银行贷款', '旧库写法（两种来源并列）', 111
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em117', 'land_zjly', '市财政资金', '市财政资金', '旧库写法', 112
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1em118', 'land_zjly', '自筹、银行贷款', '自筹、银行贷款', '旧库写法（与「自筹及银行贷款」同义，保留原样待业务收敛）', 113

    -- ===== 资金落实情况（land_zjlsqk）：5 项 =====
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1en101', 'land_zjlsqk', '已落实', '已落实', '标准值（旧库的「落实」导入时归到这里）', 1
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1en102', 'land_zjlsqk', '未落实', '未落实', '标准值', 2
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1en103', 'land_zjlsqk', '已落实（部分）', '已落实（部分）',
                     '旧库写法：部分资金到位（业务中间态，并入「已落实」会让到位率虚高）', 101
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1en104', 'land_zjlsqk', '正常推进', '正常推进', '旧库写法', 102
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1en105', 'land_zjlsqk', '未批复', '未批复', '旧库写法：说的是审批进度，不是到位情况', 103

    -- ===== 审批与开工状态（land_pfzt）：2 + 12 项 =====
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep101', 'land_pfzt', '正常推进', '正常推进', '标准值', 1
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep102', 'land_pfzt', '有问题', '有问题', '标准值', 2
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep103', 'land_pfzt', '已批复', '已批复', '旧库写法', 101
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep104', 'land_pfzt', '不涉及', '不涉及', '旧库写法：该配套不涉及此项批复', 102
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep105', 'land_pfzt', '已完成', '已完成', '旧库写法', 103
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep106', 'land_pfzt', '未批复', '未批复', '旧库写法', 104
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep107', 'land_pfzt', '未开始', '未开始', '旧库写法', 105
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep108', 'land_pfzt', '未申报', '未申报', '旧库写法', 106
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep109', 'land_pfzt', '已完工', '已完工', '旧库写法（开工状态列）', 107
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep110', 'land_pfzt', '已竣工', '已竣工', '旧库写法（开工状态列）', 108
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep111', 'land_pfzt', '未开工', '未开工', '旧库写法（开工状态列）', 109
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep112', 'land_pfzt', '前期筹备', '前期筹备', '旧库写法（开工状态列）', 110
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep113', 'land_pfzt', '以备案制进行推进', '以备案制进行推进', '旧库写法', 111
    UNION ALL SELECT '7a1f0d2c4e5b4d8a9c3f6b1ep114', 'land_pfzt', '评审单位正在进行造价评审', '评审单位正在进行造价评审', '旧库写法', 112
  ) t
  JOIN `sys_dict` d ON d.`dict_code` = t.`dict_code`
 WHERE NOT EXISTS (
         SELECT 1 FROM (SELECT `dict_id`, `item_value` FROM `sys_dict_item`) i
          WHERE i.`dict_id` = d.`id` AND i.`item_value` = t.`item_value`);

-- ---------------------------------------------------------------------------
-- 3) 核对查询（逐条看结果，注释里写了期望）
-- ---------------------------------------------------------------------------

-- 3.1 六个字典的项数：期望 land_facility_category=10 / land_jsxx=9 / land_dldj=7
--     / land_zjly=18 / land_zjlsqk=5 / land_pfzt=14
SELECT d.`dict_code` AS 字典, d.`dict_name` AS 名称, COUNT(i.`id`) AS 项数
  FROM `sys_dict` d
  LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
 WHERE d.`dict_code` IN ('land_facility_category', 'land_jsxx', 'land_dldj',
                         'land_zjly', 'land_zjlsqk', 'land_pfzt')
 GROUP BY d.`dict_code`, d.`dict_name`
 ORDER BY d.`dict_code`;

-- 3.2 项数与预期不一致的：期望 0 行
SELECT d.`dict_code` AS 字典, COUNT(i.`id`) AS 实际项数,
       CASE d.`dict_code`
         WHEN 'land_facility_category' THEN 10
         WHEN 'land_jsxx' THEN 9
         WHEN 'land_dldj' THEN 7
         WHEN 'land_zjly' THEN 18
         WHEN 'land_zjlsqk' THEN 5
         WHEN 'land_pfzt' THEN 14
       END AS 预期项数
  FROM `sys_dict` d
  LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
 WHERE d.`dict_code` IN ('land_facility_category', 'land_jsxx', 'land_dldj',
                         'land_zjly', 'land_zjlsqk', 'land_pfzt')
 GROUP BY d.`dict_code`
HAVING 实际项数 <> 预期项数;

-- 3.3 同一字典内取值重复：期望 0 行
SELECT d.`dict_code` AS 字典, i.`item_value` AS 取值, COUNT(*) AS 重复次数
  FROM `sys_dict_item` i
  JOIN `sys_dict` d ON d.`id` = i.`dict_id`
 WHERE d.`dict_code` IN ('land_facility_category', 'land_jsxx', 'land_dldj',
                         'land_zjly', 'land_zjlsqk', 'land_pfzt')
 GROUP BY d.`dict_code`, i.`item_value`
HAVING COUNT(*) > 1;

-- 3.4 旧库本次用到、但字典里仍缺的取值（期望 0 行）
--     列表与导入 Excel 里实际出现的取值一一对应；有遗漏就说明 Java 数组/字典漏了一档
SELECT t.`dict_code` AS 字典, t.`item_value` AS 缺项
  FROM (
              SELECT 'land_jsxx' AS `dict_code`, '道路' AS `item_value` UNION ALL
              SELECT 'land_jsxx', '城镇其他' UNION ALL
              SELECT 'land_jsxx', '城镇建设与改造' UNION ALL
              SELECT 'land_dldj', '次干路、支路' UNION ALL
              SELECT 'land_dldj', '步行街' UNION ALL
              SELECT 'land_dldj', '管线不涉及' UNION ALL
              SELECT 'land_facility_category', '道路及管线' UNION ALL
              SELECT 'land_facility_category', '供水、排水' UNION ALL
              SELECT 'land_zjly', '自筹' UNION ALL
              SELECT 'land_zjly', '企业自筹' UNION ALL
              SELECT 'land_zjly', 'PPP模式' UNION ALL
              SELECT 'land_zjly', '区内统筹、专项债' UNION ALL
              SELECT 'land_zjly', '城市更新成本' UNION ALL
              SELECT 'land_zjly', '政府专项债及自有资金' UNION ALL
              SELECT 'land_zjly', '财政' UNION ALL
              SELECT 'land_zjly', '自筹及其它' UNION ALL
              SELECT 'land_zjly', '财政资金和专项债' UNION ALL
              SELECT 'land_zjly', '市级资金' UNION ALL
              SELECT 'land_zjly', '自筹及银行贷款' UNION ALL
              SELECT 'land_zjly', '市财政资金' UNION ALL
              SELECT 'land_zjly', '自筹、银行贷款' UNION ALL
              SELECT 'land_zjlsqk', '已落实（部分）' UNION ALL
              SELECT 'land_zjlsqk', '正常推进' UNION ALL
              SELECT 'land_zjlsqk', '未批复' UNION ALL
              SELECT 'land_pfzt', '已批复' UNION ALL
              SELECT 'land_pfzt', '不涉及' UNION ALL
              SELECT 'land_pfzt', '已完成' UNION ALL
              SELECT 'land_pfzt', '未批复' UNION ALL
              SELECT 'land_pfzt', '未开始' UNION ALL
              SELECT 'land_pfzt', '未申报' UNION ALL
              SELECT 'land_pfzt', '已完工' UNION ALL
              SELECT 'land_pfzt', '已竣工' UNION ALL
              SELECT 'land_pfzt', '未开工' UNION ALL
              SELECT 'land_pfzt', '前期筹备' UNION ALL
              SELECT 'land_pfzt', '以备案制进行推进' UNION ALL
              SELECT 'land_pfzt', '评审单位正在进行造价评审'
       ) t
  LEFT JOIN `sys_dict` d ON d.`dict_code` = t.`dict_code`
  LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id` AND i.`item_value` = t.`item_value`
 WHERE i.`id` IS NULL;

-- 3.5 行政区划字典未被牵连：期望 16
SELECT COUNT(*) AS 行政区划项数
  FROM `sys_dict_item` i
  JOIN `sys_dict` d ON d.`id` = i.`dict_id`
 WHERE d.`dict_code` = 'land_xzqh';
