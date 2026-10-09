-- =============================================================================
-- 数据管理 · 数据字典脚本（方案 2.3.1（三））
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 建立 6 个业务字典：
--   land_project_type        项目分类        市级项目 / 区级项目
--   land_xzqh                行政区划        天津市 16 个区
--   land_attach_type         附件类型        按旧系统 4 个槽位 + 配套侧目录扩成 13 类
--   land_process_status      环节情况        未开启 / 进行中 / 已完成 / 不涉及
--   land_process_issue       环节问题类型    审批问题 / 资金问题 / 权属问题 / 管线问题 / 地质问题
--   land_facility_category   配套设施类别    道路 / 排水 / 供水 / 中水 / 燃气 / 路灯 / 绿化 / 交通设施
--
-- ★ 为什么这几个进字典、而「是否涉及提级论证」之类不进：
--   · 项目分类 / 行政区划 / 附件类型 / 环节情况 / 问题类型 / 配套类别
--     都是**中心可能调整措辞或增删取值**的业务可选值（附件类型尤其会扩，
--     旧系统写死在 Controller 里的 4 个槽位就是反面教材），做成字典可后台改；
--   · 而「是/否」这种二值、以及「环节情况」在**代码里驱动状态配色与阶段汇总口径**
--     （未开启灰 / 进行中蓝 / 已完成绿 / 不涉及灰），
--     配色与汇总必须与取值强绑定 —— 所以环节情况**同时**在代码里有枚举，
--     字典只供界面下拉与导出显示。两边必须一致，由 06 脚本末尾的守卫查询比对。
--
-- ★ item_value 直接用中文文本（与档案、提级论证模块一致）：
--   库里既有业务枚举都是中文直存，导出 Excel 后可读性最好，
--   也避免「存编码、显示文本」两层映射带来的对不上问题。
--   ★ 例外：附件类型用「编码」存（01/02/…/13），因为它是**文件分类码**，
--     会被拼进存储目录名与导出文件名，必须稳定且与旧系统目录名可对照。
--
-- 脚本可重复执行：按 dict_code 先清字典项、再清字典、然后插入。
-- 执行后前端字典缓存需刷新（重新登录，或在「系统管理 → 数据字典」里点一次刷新）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 清理旧记录（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `sys_dict_item`
 WHERE `dict_id` IN (
   SELECT `id` FROM `sys_dict`
    WHERE `dict_code` IN ('land_project_type', 'land_xzqh', 'land_attach_type',
                          'land_process_status', 'land_process_issue', 'land_facility_category'));

DELETE FROM `sys_dict`
 WHERE `dict_code` IN ('land_project_type', 'land_xzqh', 'land_attach_type',
                       'land_process_status', 'land_process_issue', 'land_facility_category');

-- ---------------------------------------------------------------------------
-- 2) 字典
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict`
  (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
VALUES
  ('7a1f0d2c4e5b4d8a9c3f6b1ed101', '数据管理-项目分类', 'land_project_type',
   '经营性用地与配套项目的项目分类（方案 2.3.1（三）；旧系统只认这两个值）', 0, 'admin', NOW(), NULL, NULL, 0),
  ('7a1f0d2c4e5b4d8a9c3f6b1ed102', '数据管理-行政区划', 'land_xzqh',
   '天津市 16 个行政区（与旧库 xj_kjkfb_commercial_land.xzqh 实测取值逐字一致）', 0, 'admin', NOW(), NULL, NULL, 0),
  ('7a1f0d2c4e5b4d8a9c3f6b1ed103', '数据管理-附件类型', 'land_attach_type',
   '宗地/配套项目附件分类（前 4 类沿用旧系统 01-04 槽位目录名，后 9 类为配套侧扩展）', 0, 'admin', NOW(), NULL, NULL, 0),
  ('7a1f0d2c4e5b4d8a9c3f6b1ed104', '数据管理-环节情况', 'land_process_status',
   '配套项目环节进度状态（★ 代码里也有同名枚举驱动配色与阶段汇总，两者必须一致）', 0, 'admin', NOW(), NULL, NULL, 0),
  ('7a1f0d2c4e5b4d8a9c3f6b1ed105', '数据管理-环节问题类型', 'land_process_issue',
   '环节存在问题的类型（对应旧库 process_status.czwtlx）', 0, 'admin', NOW(), NULL, NULL, 0),
  ('7a1f0d2c4e5b4d8a9c3f6b1ed106', '数据管理-配套设施类别', 'land_facility_category',
   '配套设施类别（旧库 xj_kjkfb_supporting_facilities_category 8 行；★ 旧录入页写「供气」而字典写「燃气」，此处统一为「燃气」）', 0, 'admin', NOW(), NULL, NULL, 0);

-- ---------------------------------------------------------------------------
-- 3) 字典项
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict_item`
  (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  -- ===== 项目分类（2 项）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1ee101', '7a1f0d2c4e5b4d8a9c3f6b1ed101', '市级项目', '市级项目', '市土地利用中心统筹', 1, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee102', '7a1f0d2c4e5b4d8a9c3f6b1ed101', '区级项目', '区级项目', '各区自行推进',       2, 1, 'admin', NOW(), NULL, NULL),

  -- ===== 行政区划（16 项，与旧库实测取值一致）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1ee201', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '和平区',   '和平区',   '市内六区',   1, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee202', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '河东区',   '河东区',   '市内六区',   2, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee203', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '河西区',   '河西区',   '市内六区',   3, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee204', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '南开区',   '南开区',   '市内六区',   4, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee205', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '河北区',   '河北区',   '市内六区',   5, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee206', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '红桥区',   '红桥区',   '市内六区',   6, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee207', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '东丽区',   '东丽区',   '环城四区',   7, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee208', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '西青区',   '西青区',   '环城四区',   8, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee209', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '津南区',   '津南区',   '环城四区',   9, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee210', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '北辰区',   '北辰区',   '环城四区',  10, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee211', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '武清区',   '武清区',   '远郊',      11, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee212', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '宝坻区',   '宝坻区',   '远郊',      12, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee213', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '滨海新区', '滨海新区', '滨海',      13, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee214', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '宁河区',   '宁河区',   '远郊',      14, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee215', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '静海区',   '静海区',   '远郊',      15, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ee216', '7a1f0d2c4e5b4d8a9c3f6b1ed102', '蓟州区',   '蓟州区',   '远郊',      16, 1, 'admin', NOW(), NULL, NULL),

  -- ===== 附件类型（13 项）=====
  -- 01~04 沿用旧系统宗地侧 4 个槽位目录名；05~13 为配套侧/环节侧扩展（旧系统是一个大目录）
  ('7a1f0d2c4e5b4d8a9c3f6b1ef101', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '土地整理计划',       '01', '旧系统槽位 01-土地整理计划（宗地）',        1, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef102', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '配套情况函',         '02', '旧系统槽位 02-配套情况函（宗地）',          2, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef103', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '配套筹备函',         '03', '旧系统槽位 03-配套筹备函（宗地）',          3, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef104', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '出让宗地图形数据',   '04', '旧系统槽位 04-出让宗地图形数据（shp）',      4, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef105', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '项建批复',           '05', '项建批复文件（旧配套表 xjpfwj）',            5, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef106', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '可研批复',           '06', '可研批复文件（旧配套表 kypfwj）',            6, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef107', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '初设及概算批复',     '07', '初设及概算批复文件（旧配套表 csjgspfwj）',   7, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef108', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '道路规划',           '08', '道路规划（旧配套表 dlgh）',                  8, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef109', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '管线综合矢量数据',   '09', '管线综合矢量数据（旧配套表 gxzhslsj，shp）', 9, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef110', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '专业配套方案',       '10', '专业配套方案（旧配套表 zyptfa）',           10, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef111', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '施工许可',           '11', '施工许可（旧配套表 sgxk）',                 11, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef112', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '竣工与移交文件',     '12', '竣工文件 / 移交文件（旧配套表 jgwj / yjwj）', 12, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ef113', '7a1f0d2c4e5b4d8a9c3f6b1ed103', '其他',               '99', '其他附件（环节进度佐证材料等）',           99, 1, 'admin', NOW(), NULL, NULL),

  -- ===== 环节情况（4 项）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1eg101', '7a1f0d2c4e5b4d8a9c3f6b1ed104', '未开启', '未开启', '环节尚未开始（默认值）',       1, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1eg102', '7a1f0d2c4e5b4d8a9c3f6b1ed104', '进行中', '进行中', '环节正在办理',                 2, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1eg103', '7a1f0d2c4e5b4d8a9c3f6b1ed104', '已完成', '已完成', '环节已办结（必填实际结束时间）', 3, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1eg104', '7a1f0d2c4e5b4d8a9c3f6b1ed104', '不涉及', '不涉及', '本项目不涉及该环节（阶段汇总时剔除）', 4, 1, 'admin', NOW(), NULL, NULL),

  -- ===== 环节问题类型（5 项）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1eh101', '7a1f0d2c4e5b4d8a9c3f6b1ed105', '审批问题', '审批问题', '审批环节受阻',   1, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1eh102', '7a1f0d2c4e5b4d8a9c3f6b1ed105', '资金问题', '资金问题', '资金未落实',     2, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1eh103', '7a1f0d2c4e5b4d8a9c3f6b1ed105', '权属问题', '权属问题', '土地权属争议',   3, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1eh104', '7a1f0d2c4e5b4d8a9c3f6b1ed105', '管线问题', '管线问题', '管线迁改/交叉',  4, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1eh105', '7a1f0d2c4e5b4d8a9c3f6b1ed105', '地质问题', '地质问题', '地质条件受限',   5, 1, 'admin', NOW(), NULL, NULL),

  -- ===== 配套设施类别（8 项，取自旧库 category 表）=====
  ('7a1f0d2c4e5b4d8a9c3f6b1ei101', '7a1f0d2c4e5b4d8a9c3f6b1ed106', '道路',     '道路',     '配套费单价 53 元/㎡，权重 5', 1, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ei102', '7a1f0d2c4e5b4d8a9c3f6b1ed106', '排水',     '排水',     '配套费单价 20 元/㎡，权重 3', 2, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ei103', '7a1f0d2c4e5b4d8a9c3f6b1ed106', '供水',     '供水',     '配套费单价 22 元/㎡，权重 3', 3, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ei104', '7a1f0d2c4e5b4d8a9c3f6b1ed106', '中水',     '中水',     '配套费单价 18 元/㎡，权重 3', 4, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ei105', '7a1f0d2c4e5b4d8a9c3f6b1ed106', '燃气',     '燃气',     '★ 旧录入页写「供气」、字典写「燃气」，此处统一为「燃气」', 5, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ei106', '7a1f0d2c4e5b4d8a9c3f6b1ed106', '路灯',     '路灯',     '配套费单价 15 元/㎡，权重 1', 6, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ei107', '7a1f0d2c4e5b4d8a9c3f6b1ed106', '绿化',     '绿化',     '配套费单价 32 元/㎡，权重 4', 7, 1, 'admin', NOW(), NULL, NULL),
  ('7a1f0d2c4e5b4d8a9c3f6b1ei108', '7a1f0d2c4e5b4d8a9c3f6b1ed106', '交通设施', '交通设施', '配套费单价 30 元/㎡，权重 4', 8, 1, 'admin', NOW(), NULL, NULL);

-- ---------------------------------------------------------------------------
-- 4) 守卫查询
--    4.1 应输出 6 行，每行 项数 与预期一致
-- ---------------------------------------------------------------------------
SELECT d.`dict_code`, d.`dict_name` AS 字典, COUNT(i.`id`) AS 项数
FROM `sys_dict` d
LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_project_type', 'land_xzqh', 'land_attach_type',
                        'land_process_status', 'land_process_issue', 'land_facility_category')
GROUP BY d.`dict_code`, d.`dict_name`
ORDER BY d.`dict_code`;

--    4.2 应输出 0 行：字典项数与预期不符的字典
SELECT d.`dict_code`,
       COUNT(i.`id`) AS 实际项数,
       CASE d.`dict_code`
         WHEN 'land_project_type'      THEN 2
         WHEN 'land_xzqh'              THEN 16
         WHEN 'land_attach_type'       THEN 13
         WHEN 'land_process_status'    THEN 4
         WHEN 'land_process_issue'     THEN 5
         WHEN 'land_facility_category' THEN 8
       END AS 预期项数
FROM `sys_dict` d
LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_project_type', 'land_xzqh', 'land_attach_type',
                        'land_process_status', 'land_process_issue', 'land_facility_category')
GROUP BY d.`dict_code`
HAVING 实际项数 <> 预期项数;

--    4.3 ★ 关键一致性守卫：字典里的「环节情况」取值必须与代码枚举逐字一致
--        应输出 4 行，且每行 是否在枚举内 = 是
SELECT i.`item_value` AS 字典取值,
       CASE WHEN i.`item_value` IN ('未开启', '进行中', '已完成', '不涉及')
            THEN '是' ELSE '★否，代码枚举里没有这个值' END AS 是否在枚举内
FROM `sys_dict_item` i
JOIN `sys_dict` d ON d.`id` = i.`dict_id`
WHERE d.`dict_code` = 'land_process_status'
ORDER BY i.`sort_order`;
