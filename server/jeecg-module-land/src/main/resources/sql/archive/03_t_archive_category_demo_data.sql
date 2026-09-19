-- =============================================================================
-- 档案类别树 · 多级测试数据（演示 / 联调用）
-- 目标库：tj-jyxyd
--
-- 层级规划（在方案原文的 6 个顶级类别之下展开 3~4 级）：
--   1 级：基本建设手续 / 规划手续 / 用地手续 / 资金审批 / 工程建设手续 / 其他手续（已有）
--   2 级：28 个（项目建议书批复、选址意见书、施工许可 ……）
--   3 级：19 个（市级发改委批复、规划条件通知书、竣工验收报告 ……）
--   4 级：4 个（批复正文、初步设计图纸、合同补充协议 ……）
--
-- 说明：
--   * id 用 d2-/d3-/d4- 前缀，一眼能看出层级，也便于整批清理；
--   * path = 父.path + '/' + id，level = 父.level + 1，由脚本自动计算；
--   * 其中 d2-28 及其子节点 d3-19 故意置为 status=0，用来演示「停用」与级联停用；
--   * 脚本可重复执行：先按 id 前缀删除旧数据，再重新插入。
-- =============================================================================

-- 0) 清理旧的演示数据（只删 d2-/d3-/d4- 前缀，不会碰到真实类别）
DELETE FROM `t_archive_category`
 WHERE `id` LIKE 'd2-%' OR `id` LIKE 'd3-%' OR `id` LIKE 'd4-%';

-- 1) 第 2 级
INSERT INTO `t_archive_category`
  (`id`, `parent_id`, `path`, `name`, `code`, `alias_name`, `note`, `level`, `has_children`, `is_leaf`, `sort_no`, `status`, `del_flag`, `create_by`, `create_time`)
SELECT v.id, p.id, CONCAT(p.path, '/', v.id), v.name, v.code, v.alias_name, v.note,
       p.level + 1, 0, 1, v.sort_no, v.status, 0, 'admin', NOW()
FROM (
  SELECT 'd2-01' AS id, 'ac00000000000000000000000000000001' AS parent_id, '项目建议书批复' AS name, 'JBJS-01' AS code, 'xiangmujianyishupifu' AS alias_name, '发改部门对项目建议书的批复文件' AS note, 2 AS sort_no, 1 AS status
  UNION ALL SELECT 'd2-02', 'ac00000000000000000000000000000001', '可行性研究报告批复', 'JBJS-02', 'keyanbaogaopifu', '可研报告及批复', 3, 1
  UNION ALL SELECT 'd2-03', 'ac00000000000000000000000000000001', '初步设计批复', 'JBJS-03', 'chubushejipifu', '初步设计及概算批复', 4, 1
  UNION ALL SELECT 'd2-04', 'ac00000000000000000000000000000001', '概算批复', 'JBJS-04', 'gaisuanpifu', '工程概算批复文件', 5, 1
  UNION ALL SELECT 'd2-05', 'ac00000000000000000000000000000001', '节能审查意见', 'JBJS-05', 'jienengshencha', '固定资产投资项目节能审查', 6, 1
  UNION ALL SELECT 'd2-06', 'ac00000000000000000000000000000002', '选址意见书', 'GH-01', 'xuanzhiyijianshu', '建设项目选址意见书', 1, 1
  UNION ALL SELECT 'd2-07', 'ac00000000000000000000000000000002', '规划条件', 'GH-02', 'guihuatiaojian', '规划条件通知书及附图', 2, 1
  UNION ALL SELECT 'd2-08', 'ac00000000000000000000000000000002', '建设用地规划许可证', 'GH-03', 'jsydghxkz', '建设用地规划许可', 3, 1
  UNION ALL SELECT 'd2-09', 'ac00000000000000000000000000000002', '建设工程规划许可证', 'GH-04', 'jsgcghxkz', '建设工程规划许可', 4, 1
  UNION ALL SELECT 'd2-10', 'ac00000000000000000000000000000002', '规划验收', 'GH-05', 'guihuayanshou', '规划竣工验收材料', 5, 1
  UNION ALL SELECT 'd2-11', 'ac00000000000000000000000000000002', '方案审查意见', 'GH-06', 'fanganshencha', '总平面及建筑方案审查', 6, 1
  UNION ALL SELECT 'd2-12', 'ac00000000000000000000000000000003', '用地预审', 'YD-01', 'yongdiyushen', '建设项目用地预审意见', 1, 1
  UNION ALL SELECT 'd2-13', 'ac00000000000000000000000000000003', '土地划拨决定书', 'YD-02', 'tudihuabo', '划拨用地决定书', 2, 1
  UNION ALL SELECT 'd2-14', 'ac00000000000000000000000000000003', '供地文件', 'YD-03', 'gongdiwenjian', '出让合同、成交确认书等', 3, 1
  UNION ALL SELECT 'd2-15', 'ac00000000000000000000000000000003', '不动产登记', 'YD-04', 'budongchandengji', '不动产权证书及登记材料', 4, 1
  UNION ALL SELECT 'd2-16', 'ac00000000000000000000000000000004', '资金计划', 'ZJSP-01', 'zijinjihua', '年度 / 季度资金计划', 1, 1
  UNION ALL SELECT 'd2-17', 'ac00000000000000000000000000000004', '资金拨付', 'ZJSP-02', 'zijinbofu', '资金拨付凭证与台账', 2, 1
  UNION ALL SELECT 'd2-18', 'ac00000000000000000000000000000004', '决算审核', 'ZJSP-03', 'juesuanshenhe', '竣工财务决算审核', 3, 1
  UNION ALL SELECT 'd2-19', 'ac00000000000000000000000000000005', '施工图审查', 'GCJS-01', 'shigongtushencha', '施工图审查合格书等', 1, 1
  UNION ALL SELECT 'd2-20', 'ac00000000000000000000000000000005', '施工许可', 'GCJS-02', 'shigongxuke', '建筑工程施工许可证', 2, 1
  UNION ALL SELECT 'd2-21', 'ac00000000000000000000000000000005', '质量监督', 'GCJS-03', 'zhiliangjiandu', '质量监督登记与报告', 3, 1
  UNION ALL SELECT 'd2-22', 'ac00000000000000000000000000000005', '竣工验收备案', 'GCJS-04', 'jungongbeian', '竣工验收报告与备案表', 4, 1
  UNION ALL SELECT 'd2-23', 'ac00000000000000000000000000000005', '移交', 'GCJS-05', 'yijiao', '各专业工程移交材料', 5, 1
  UNION ALL SELECT 'd2-24', 'ac00000000000000000000000000000006', '地名命名', 'QT-01', 'dimingmingming', '道路地名命名批复', 1, 1
  UNION ALL SELECT 'd2-25', 'ac00000000000000000000000000000006', '环评', 'QT-02', 'huanping', '环境影响评价文件', 2, 1
  UNION ALL SELECT 'd2-26', 'ac00000000000000000000000000000006', '水保', 'QT-03', 'shuibao', '水土保持方案及批复', 3, 1
  UNION ALL SELECT 'd2-27', 'ac00000000000000000000000000000006', '其他', 'QT-04', 'qita', '未归入以上类别的零散材料', 4, 1
  UNION ALL SELECT 'd2-28', 'ac00000000000000000000000000000006', '历史遗留类别（已停用示例）', 'QT-99', 'lishiyiliu', '演示停用状态：停用后不可在其下新建类别', 5, 0
) v
JOIN `t_archive_category` p ON p.id = v.parent_id AND p.del_flag = 0;

-- 2) 第 3 级
INSERT INTO `t_archive_category`
  (`id`, `parent_id`, `path`, `name`, `code`, `alias_name`, `note`, `level`, `has_children`, `is_leaf`, `sort_no`, `status`, `del_flag`, `create_by`, `create_time`)
SELECT v.id, p.id, CONCAT(p.path, '/', v.id), v.name, v.code, v.alias_name, v.note,
       p.level + 1, 0, 1, v.sort_no, v.status, 0, 'admin', NOW()
FROM (
  SELECT 'd3-01' AS id, 'd2-01' AS parent_id, '市级发改委批复' AS name, 'JBJS-01-01' AS code, 'shijifagaiwei' AS alias_name, NULL AS note, 1 AS sort_no, 1 AS status
  UNION ALL SELECT 'd3-02', 'd2-01', '区级发改委批复', 'JBJS-01-02', 'qujifagaiwei', NULL, 2, 1
  UNION ALL SELECT 'd3-03', 'd2-03', '初步设计文本', 'JBJS-03-01', 'chubushejiwenben', '含设计说明与图纸目录', 1, 1
  UNION ALL SELECT 'd3-04', 'd2-03', '概算书', 'JBJS-03-02', 'gaisuanshu', NULL, 2, 1
  UNION ALL SELECT 'd3-05', 'd2-07', '规划条件通知书', 'GH-02-01', 'guihuatiaojianTZS', NULL, 1, 1
  UNION ALL SELECT 'd3-06', 'd2-07', '规划条件附图', 'GH-02-02', 'guihuatiaojianFT', '红线图、控制线图', 2, 1
  UNION ALL SELECT 'd3-07', 'd2-10', '规划验收合格证', 'GH-05-01', 'guihuayanshouHZ', NULL, 1, 1
  UNION ALL SELECT 'd3-08', 'd2-10', '竣工测绘报告', 'GH-05-02', 'jungongcehui', NULL, 2, 1
  UNION ALL SELECT 'd3-09', 'd2-14', '出让合同', 'YD-03-01', 'churanghetong', '国有建设用地使用权出让合同', 1, 1
  UNION ALL SELECT 'd3-10', 'd2-14', '成交确认书', 'YD-03-02', 'chengjiaoqueren', NULL, 2, 1
  UNION ALL SELECT 'd3-11', 'd2-16', '年度资金计划', 'ZJSP-01-01', 'niandujihua', NULL, 1, 1
  UNION ALL SELECT 'd3-12', 'd2-16', '季度资金计划', 'ZJSP-01-02', 'jidujihua', NULL, 2, 1
  UNION ALL SELECT 'd3-13', 'd2-19', '施工图审查合格书', 'GCJS-01-01', 'shentuhegeshu', NULL, 1, 1
  UNION ALL SELECT 'd3-14', 'd2-19', '消防设计审查意见', 'GCJS-01-02', 'xiaofangshencha', NULL, 2, 1
  UNION ALL SELECT 'd3-15', 'd2-22', '竣工验收报告', 'GCJS-04-01', 'jungongyanshouBG', NULL, 1, 1
  UNION ALL SELECT 'd3-16', 'd2-22', '竣工验收备案表', 'GCJS-04-02', 'jungongbeianbiao', NULL, 2, 1
  UNION ALL SELECT 'd3-17', 'd2-23', '道路工程移交', 'GCJS-05-01', 'daoluyijiao', '道路交付及养护协议', 1, 1
  UNION ALL SELECT 'd3-18', 'd2-23', '排水工程移交', 'GCJS-05-02', 'paishuiyijiao', NULL, 2, 1
  UNION ALL SELECT 'd3-19', 'd2-28', '停用父级下的子类别', 'QT-99-01', 'tingyongzi', '跟随父级一起停用', 1, 0
) v
JOIN `t_archive_category` p ON p.id = v.parent_id AND p.del_flag = 0;

-- 3) 第 4 级（验证 4 层树）
INSERT INTO `t_archive_category`
  (`id`, `parent_id`, `path`, `name`, `code`, `alias_name`, `note`, `level`, `has_children`, `is_leaf`, `sort_no`, `status`, `del_flag`, `create_by`, `create_time`)
SELECT v.id, p.id, CONCAT(p.path, '/', v.id), v.name, v.code, v.alias_name, v.note,
       p.level + 1, 0, 1, v.sort_no, p.status, 0, 'admin', NOW()
FROM (
  SELECT 'd4-01' AS id, 'd3-01' AS parent_id, '批复正文' AS name, 'JBJS-01-01-01' AS code, NULL AS alias_name, NULL AS note, 1 AS sort_no
  UNION ALL SELECT 'd4-02', 'd3-01', '批复附件', 'JBJS-01-01-02', NULL, NULL, 2
  UNION ALL SELECT 'd4-03', 'd3-03', '初步设计图纸', 'JBJS-03-01-01', NULL, NULL, 1
  UNION ALL SELECT 'd4-04', 'd3-09', '合同补充协议', 'YD-03-01-01', NULL, '父级停用时子级同步停用', 1
) v
JOIN `t_archive_category` p ON p.id = v.parent_id AND p.del_flag = 0;

-- 4) 重算 has_children / is_leaf（服务层写入时会自动维护，脚本直接插库需要补一次）
UPDATE `t_archive_category` c
LEFT JOIN (
  SELECT `parent_id`, COUNT(1) AS cnt
  FROM `t_archive_category`
  WHERE `del_flag` = 0
  GROUP BY `parent_id`
) t ON t.`parent_id` = c.`id`
SET c.`has_children` = IF(COALESCE(t.cnt, 0) > 0, 1, 0),
    c.`is_leaf`      = IF(COALESCE(t.cnt, 0) > 0, 0, 1)
WHERE c.`del_flag` = 0;

-- 5) 结果核对：各层级数量
SELECT `level`, COUNT(1) AS node_count, SUM(`status` = 0) AS disabled_count
FROM `t_archive_category`
WHERE `del_flag` = 0
GROUP BY `level`
ORDER BY `level`;
