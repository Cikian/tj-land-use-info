-- ============================================================================
--  档案管理 · 测试（演示）数据
--  目标库：tj-jyxyd
--
--  前置：04_t_archive.sql 已执行（三张表存在）、t_land 已迁移、t_archive_category 已有类别树。
--
--  ★ 数据是怎么"造"出来的
--    不是随便编名字，而是**基于库里的真实数据**：
--      · 宗地 / 配套项目：取自 xj_kjkfb_supporting_facilities + t_land，
--        覆盖 河东/河西/南开/东丽 4 个区、排水/燃气/道路 3 类配套、市级+区级 2 类项目；
--      · 档案类别：只挂 t_archive_category 里**真实存在的叶子类别**（37 个叶子，覆盖 6 个顶级类别）；
--      · 文件大小 / 扩展名 / 卷内序号：按真实档案习惯给（pdf 为主，另有 jpg/xlsx/zip/docx/dwg）。
--
--  ★ 覆盖到每个界面分支，方便逐项验收
--    · 状态：已归档 11 / 审核中 3 / 归档中 2 / 未归档 2
--    · 年度：2022×3、2023×3、2024×4、2025×4、2026×4 —— 年度趋势折线图有完整 5 个点
--    · 类型：电子 15 / 纸质 3      密级：一般 14 / 内部 3 / 秘密 1
--    · 保管期限：永久 7 / 长期 7 / 短期 4
--    · 责任部门：住建 8 / 规划 5 / 财政 3 / 发改 1 / 其他 1
--    · 一类档案里**横跨多个档案类别**（这正是「类别挂在文件上」的设计意图）
--    · 「未归档」的两卷故意**没有任何文件**，用来验证「没有文件不能置为已归档」的校验
--
--  ★ 与磁盘文件的配合
--    脚本只写数据库。sql 同目录另有 scripts/gen-archive-demo-files.ps1，
--    它读 t_archive_file 里 id like 'demo-af-%' 的 store_path，在
--    jeecg.path.upload（dev = E:/work-space/upFiles/tj-land-use-info）下补齐真实文件，
--    这样「文件下载」与「按项目导出 ZIP」才能真的下到东西。
--    不生成也能用：导出会把缺失文件写成「.缺失说明.txt」并在清单里标注「文件缺失」。
--
--  ★ 档案号为什么用 DA-{yyyy}-DEMO{nn} 而不是 DA-{yyyy}-{4位流水}
--    ① 不撞号：真实使用中随时可能已经存在 DA-2026-0001 这类编号
--       （本机就有一条用户手工建的），用标准流水号会撞 uk_archive_no 唯一键；
--    ② 不污染流水：ArchiveServiceImpl.nextArchiveNo() 取「同前缀 + 恰好 4 位数字」的最大值，
--       DEMO01（6 位后缀）不满足该长度条件，因此**不会把真实流水号顶高**；
--    ③ 一眼能认出是演示数据，清理时不会误删真实档案。
--
--  清理：见脚本末尾（按 demo- 前缀删除即可，不影响真实数据）
--  可重复执行：先按 id 前缀清理，再插入，最后按实际文件重算 file_count / total_size。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 清理旧的演示数据（幂等）
-- ---------------------------------------------------------------------------
DELETE FROM `t_archive_log`  WHERE `id` LIKE 'demo-al-%' OR `archive_id` LIKE 'demo-ar-%';
DELETE FROM `t_archive_file` WHERE `id` LIKE 'demo-af-%' OR `archive_id` LIKE 'demo-ar-%';
DELETE FROM `t_archive`      WHERE `id` LIKE 'demo-ar-%';

-- ---------------------------------------------------------------------------
-- 2) 档案主表：18 卷
--    facility_id / land_id / crzdbh / dkmc / ptsslb / xzqh 全部取自真实业务数据
-- ---------------------------------------------------------------------------
INSERT INTO `t_archive`
  (`id`, `archive_no`, `archive_name`, `land_id`, `crzdbh`, `facility_id`, `ptxmmc`, `dkmc`, `ptsslb`,
   `archive_type`, `secret_level`, `retention`, `archive_year`, `responsible_dept`, `responsible_user`, `xzqh`,
   `archive_date`, `source_type`, `source_id`, `remark`, `status`, `file_count`, `total_size`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  -- ===== 2022 年 =====
  ('demo-ar-01', 'DA-2022-DEMO01', '仁昌路、诚盛道、诚润道道路工程档案',
   '402888a79ab417a3019ab426e98b0a4b', '津丽（挂）2018-07', '402888a79919217501991923d1800510',
   '仁昌路、诚盛道、诚润道', '金钟', '道路',
   'electronic', '一般', '永久', 2022, '规划', '张建国', '东丽区',
   '2022-11-20', 'manual', NULL, '含规划、施工、移交三类要件，跨 4 个档案类别', '已归档', 0, 0, 0,
   'admin', '2022-11-20 09:30:00', 'admin', '2022-11-25 16:10:00'),

  ('demo-ar-02', 'DA-2022-DEMO02', '富贵路（北旺道—兴业道）道路工程档案',
   '402888a79ab417a3019ab426e9cd0a4c', '津丽（挂）2019-01', '402888a79919217501991923d19f0511',
   '富贵路（北旺道—兴业道）道路工程', '军粮城', '道路',
   'electronic', '一般', '长期', 2022, '住建', '李伟', '东丽区',
   '2022-12-05', 'manual', NULL, NULL, '已归档', 0, 0, 0,
   'zhangsan', '2022-12-05 14:10:00', 'zhangsan', '2022-12-08 09:20:00'),

  ('demo-ar-03', 'DA-2022-DEMO03', '立新路（芥园西道-黄河道）燃气管线工程档案',
   '402888a79919217501991934679d142a', '津南黄（挂）2021-038', '402888a79919217501991927b88c09cc',
   '立新路（芥园西道-黄河道）燃气管线工程', '南开区战备楼棚户区改造B地块', '燃气',
   'paper', '内部', '永久', 2022, '发改（行政审批）', '王海涛', '南开区',
   '2022-12-28', 'manual', NULL, '纸质原件已入库，扫描件待补', '已归档', 0, 0, 0,
   'admin', '2022-12-28 10:05:00', 'admin', '2023-01-06 11:00:00'),

  -- ===== 2023 年 =====
  ('demo-ar-04', 'DA-2023-DEMO01', '富锦路（北旺道—兴业道）道路工程档案',
   '402888a79ab417a3019ab426ea100a4d', '津丽（挂）2019-02', '402888a79919217501991923d1e10513',
   '富锦路（北旺道—兴业道）道路工程', '军粮城', '道路',
   'electronic', '一般', '长期', 2023, '住建', '李伟', '东丽区',
   '2023-06-18', 'manual', NULL, NULL, '已归档', 0, 0, 0,
   'zhangsan', '2023-06-18 11:20:00', 'zhangsan', '2023-06-20 15:30:00'),

  ('demo-ar-05', 'DA-2023-DEMO02', '霞宏道（航双路-富安路）道路工程档案',
   '402888a79ab417a3019ab426ea5e0a4e', '津丽（挂）2019-03', '402888a79919217501991923d2240515',
   '霞宏道（航双路-富安路）道路工程', '新立', '道路',
   'electronic', '一般', '永久', 2023, '规划', '赵敏', '东丽区',
   '2023-09-26', 'manual', NULL, '含规划条件附图与竣工测绘报告', '已归档', 0, 0, 0,
   'admin', '2023-09-26 15:40:00', 'admin', '2023-09-28 10:05:00'),

  ('demo-ar-06', 'DA-2023-DEMO03', '广东山庄路（金梭南道-红星路）排水配套工程档案',
   '402888a799192175019919345a3813d7', '津东成（挂）2024-34', '402888a79919217501991927a03c092e',
   '广东山庄路（金梭南道-红星路）排水配套工程', '河东区成林道（工业大学）4号地块', '排水',
   'electronic', '一般', '长期', 2023, '住建', '陈立', '河东区',
   '2023-12-11', 'manual', NULL, '质量监督报告待归档确认', '审核中', 0, 0, 0,
   'jeecg', '2023-12-11 09:15:00', 'jeecg', '2023-12-15 16:40:00'),

  -- ===== 2024 年 =====
  ('demo-ar-07', 'DA-2024-DEMO01', '霞宏道东段道路工程档案',
   '402888a79ab417a3019ab426eaa00a4f', '津丽（挂）2019-04', '402888a79919217501991923d27f0517',
   '霞宏道东段', '新立', '道路',
   'electronic', '一般', '长期', 2024, '规划', '赵敏', '东丽区',
   '2024-04-22', 'manual', NULL, NULL, '已归档', 0, 0, 0,
   'admin', '2024-04-22 10:30:00', 'admin', '2024-04-24 09:10:00'),

  ('demo-ar-08', 'DA-2024-DEMO02', '学苑路、小海地路地下配套管线工程档案',
   '402888a7991921750199193473f4146a', '津西新（挂）2020-013', '402888a79919217501991927d19a0a5e',
   '学苑路（小海地路-新会道）、小海地路（曲江路-学苑路）仅地下配套管线', '新会道棚改地块', '排水',
   'electronic', '内部', '永久', 2024, '财政', '刘芳', '河西区',
   '2024-07-15', 'manual', NULL, '含资金计划、拨付凭证与决算审核', '已归档', 0, 0, 0,
   'zhangsan', '2024-07-15 16:05:00', 'zhangsan', '2024-07-18 14:20:00'),

  ('demo-ar-09', 'DA-2024-DEMO03', '南开区冶金路燃气管线工程档案',
   '402888a7991921750199193467531429', '津南黄（挂）2021-033', '402888afa06ba14d01a06bebcd37013d',
   '南开区冶金路（芥园西道-黄河道）燃气管线工程', '南开区战备楼C棚户区改造地块', '燃气',
   'electronic', '一般', '长期', 2024, '住建', '陈立', '南开区',
   '2024-10-09', 'manual', NULL, '施工许可已上传，其余要件在办', '归档中', 0, 0, 0,
   'jeecg', '2024-10-09 09:50:00', 'jeecg', '2024-10-11 10:35:00'),

  ('demo-ar-10', 'DA-2024-DEMO04', '曲江路、小海地路地下配套管线工程档案（纸质待数字化）',
   '402888a799192175019919347421146b', '津西学（挂）2020-014', '402888a79919217501991927d1f30a5f',
   '曲江路（大沽南路-东江道）、小海地路（曲江路-微山路）仅地下配套管线', '海地路棚改地块', '排水',
   'paper', '一般', '短期', 2024, '其他', '孙倩', '河西区',
   '2024-12-20', 'manual', NULL, '纸质档案已接收，尚未数字化，故暂无卷内文件', '未归档', 0, 0, 0,
   'admin', '2024-12-20 14:25:00', 'admin', '2024-12-20 14:25:00'),

  -- ===== 2025 年 =====
  ('demo-ar-11', 'DA-2025-DEMO01', '仁昌路、诚盛道、诚润道道路工程竣工档案',
   '402888a79ab417a3019ab426e98b0a4b', '津丽（挂）2018-07', '402888a79919217501991923d1800510',
   '仁昌路、诚盛道、诚润道', '金钟', '道路',
   'electronic', '一般', '永久', 2025, '住建', '李伟', '东丽区',
   '2025-03-18', 'manual', NULL, '竣工阶段全要件（含竣工图）', '已归档', 0, 0, 0,
   'zhangsan', '2025-03-18 10:15:00', 'zhangsan', '2025-03-21 11:05:00'),

  ('demo-ar-12', 'DA-2025-DEMO02', '富贵路（北旺道—兴业道）道路工程竣工档案',
   '402888a79ab417a3019ab426e9cd0a4c', '津丽（挂）2019-01', '402888a79919217501991923d19f0511',
   '富贵路（北旺道—兴业道）道路工程', '军粮城', '道路',
   'electronic', '一般', '长期', 2025, '住建', '李伟', '东丽区',
   '2025-05-27', 'manual', NULL, NULL, '已归档', 0, 0, 0,
   'admin', '2025-05-27 11:45:00', 'admin', '2025-05-29 09:40:00'),

  ('demo-ar-13', 'DA-2025-DEMO03', '广东山庄路排水配套工程规划验收档案',
   '402888a799192175019919345a3813d7', '津东成（挂）2024-34', '402888a79919217501991927a03c092e',
   '广东山庄路（金梭南道-红星路）排水配套工程', '河东区成林道（工业大学）4号地块', '排水',
   'electronic', '内部', '永久', 2025, '规划', '张建国', '河东区',
   '2025-08-14', 'manual', NULL, '竣工测绘报告体积较大，已完成上传', '审核中', 0, 0, 0,
   'jeecg', '2025-08-14 09:05:00', 'jeecg', '2025-08-18 15:15:00'),

  ('demo-ar-14', 'DA-2025-DEMO04', '立新路燃气管线工程资金决算档案',
   '402888a79919217501991934679d142a', '津南黄（挂）2021-038', '402888a79919217501991927b88c09cc',
   '立新路（芥园西道-黄河道）燃气管线工程', '南开区战备楼棚户区改造B地块', '燃气',
   'electronic', '一般', '短期', 2025, '财政', '刘芳', '南开区',
   '2025-11-06', 'manual', NULL, '决算审核意见已上传', '归档中', 0, 0, 0,
   'zhangsan', '2025-11-06 15:30:00', 'zhangsan', '2025-11-07 09:25:00'),

  -- ===== 2026 年 =====
  ('demo-ar-15', 'DA-2026-DEMO01', '霞宏道道路及排水工程移交档案',
   '402888a79ab417a3019ab426ea5e0a4e', '津丽（挂）2019-03', '402888a79919217501991923d2240515',
   '霞宏道（航双路-富安路）道路工程', '新立', '道路',
   'electronic', '一般', '永久', 2026, '住建', '陈立', '东丽区',
   '2026-02-26', 'manual', NULL, '道路、排水两专业移交材料齐全', '已归档', 0, 0, 0,
   'admin', '2026-02-26 10:40:00', 'admin', '2026-02-28 14:50:00'),

  ('demo-ar-16', 'DA-2026-DEMO02', '学苑路地下配套管线工程结算档案',
   '402888a7991921750199193473f4146a', '津西新（挂）2020-013', '402888a79919217501991927d19a0a5e',
   '学苑路（小海地路-新会道）、小海地路（曲江路-学苑路）仅地下配套管线', '新会道棚改地块', '排水',
   'electronic', '一般', '短期', 2026, '财政', '刘芳', '河西区',
   '2026-04-08', 'manual', NULL, '结算材料已收集，尚未上传扫描件', '未归档', 0, 0, 0,
   'zhangsan', '2026-04-08 14:00:00', 'zhangsan', '2026-04-08 14:00:00'),

  ('demo-ar-17', 'DA-2026-DEMO03', '南开区冶金路燃气管线工程竣工档案',
   '402888a7991921750199193467531429', '津南黄（挂）2021-033', '402888afa06ba14d01a06bebcd37013d',
   '南开区冶金路（芥园西道-黄河道）燃气管线工程', '南开区战备楼C棚户区改造地块', '燃气',
   'paper', '一般', '长期', 2026, '规划', '赵敏', '南开区',
   '2026-05-19', 'manual', NULL, '纸质竣工资料已扫描，等待归档复核', '审核中', 0, 0, 0,
   'admin', '2026-05-19 09:35:00', 'admin', '2026-05-22 16:30:00'),

  ('demo-ar-18', 'DA-2026-DEMO04', '富锦路道路工程移交档案',
   '402888a79ab417a3019ab426ea100a4d', '津丽（挂）2019-02', '402888a79919217501991923d1e10513',
   '富锦路（北旺道—兴业道）道路工程', '军粮城', '道路',
   'electronic', '秘密', '永久', 2026, '住建', '李伟', '东丽区',
   '2026-06-30', 'manual', NULL, '★ 密级为「秘密」，用于验证涉密档案的展示与后续导出限制', '已归档', 0, 0, 0,
   'jeecg', '2026-06-30 16:20:00', 'jeecg', '2026-07-02 10:10:00');

-- ---------------------------------------------------------------------------
-- 3) 卷内文件：43 个
--    ★ category_path / category_name 先留空，第 5 段按类别树统一回填
--      （category_name 是列表页「档案类别」列的数据源，必须填，否则那一列是空的）
-- ---------------------------------------------------------------------------
INSERT INTO `t_archive_file`
  (`id`, `archive_id`, `seq_no`, `category_id`, `category_path`, `category_name`,
   `file_name`, `file_title`, `file_ext`, `file_size`, `file_md5`, `page_count`,
   `store_type`, `store_path`, `status`, `sort_no`, `remark`, `del_flag`, `create_by`, `create_time`)
VALUES
  -- ===== demo-ar-01 仁昌路（跨 4 个类别）=====
  ('demo-af-0101', 'demo-ar-01', 1, 'd3-05', NULL, NULL, '仁昌路规划条件通知书.pdf', '仁昌路规划条件通知书', 'pdf', 1887436, NULL, NULL, 'local', '/archive/2022/11/DA-2022-DEMO01-01.pdf', '已归档', 1, NULL, 0, 'admin', '2022-11-20 09:35:00'),
  ('demo-af-0102', 'demo-ar-01', 2, 'd2-08', NULL, NULL, '仁昌路建设用地规划许可证.pdf', '仁昌路建设用地规划许可证', 'pdf', 1258291, NULL, NULL, 'local', '/archive/2022/11/DA-2022-DEMO01-02.pdf', '已归档', 2, NULL, 0, 'admin', '2022-11-20 09:38:00'),
  ('demo-af-0103', 'demo-ar-01', 3, 'd2-20', NULL, NULL, '仁昌路施工许可.pdf', '仁昌路施工许可', 'pdf', 2516582, NULL, NULL, 'local', '/archive/2022/11/DA-2022-DEMO01-03.pdf', '已归档', 3, NULL, 0, 'admin', '2022-11-21 10:20:00'),
  ('demo-af-0104', 'demo-ar-01', 4, 'd3-17', NULL, NULL, '仁昌路道路工程移交单.pdf', '仁昌路道路工程移交单', 'pdf', 921600, NULL, NULL, 'local', '/archive/2022/11/DA-2022-DEMO01-04.pdf', '已归档', 4, NULL, 0, 'admin', '2022-11-25 16:05:00'),

  -- ===== demo-ar-02 富贵路 =====
  ('demo-af-0201', 'demo-ar-02', 1, 'd3-05', NULL, NULL, '富贵路规划条件通知书.pdf', '富贵路规划条件通知书', 'pdf', 1572864, NULL, NULL, 'local', '/archive/2022/12/DA-2022-DEMO02-01.pdf', '已归档', 1, NULL, 0, 'zhangsan', '2022-12-05 14:15:00'),
  ('demo-af-0202', 'demo-ar-02', 2, 'd2-09', NULL, NULL, '富贵路建设工程规划许可证.pdf', '富贵路建设工程规划许可证', 'pdf', 1153433, NULL, NULL, 'local', '/archive/2022/12/DA-2022-DEMO02-02.pdf', '已归档', 2, NULL, 0, 'zhangsan', '2022-12-05 14:18:00'),
  ('demo-af-0203', 'demo-ar-02', 3, 'd2-20', NULL, NULL, '富贵路施工许可证.pdf', '富贵路施工许可证', 'pdf', 2097152, NULL, NULL, 'local', '/archive/2022/12/DA-2022-DEMO02-03.pdf', '已归档', 3, NULL, 0, 'zhangsan', '2022-12-08 09:30:00'),

  -- ===== demo-ar-03 立新路（纸质档案）=====
  ('demo-af-0301', 'demo-ar-03', 1, 'd2-02', NULL, NULL, '立新路可行性研究报告批复.pdf', '立新路可行性研究报告批复', 'pdf', 2726297, NULL, NULL, 'local', '/archive/2022/12/DA-2022-DEMO03-01.pdf', '已归档', 1, NULL, 0, 'admin', '2022-12-28 10:12:00'),
  ('demo-af-0302', 'demo-ar-03', 2, 'd2-04', NULL, NULL, '立新路概算批复.pdf', '立新路概算批复', 'pdf', 1468006, NULL, NULL, 'local', '/archive/2022/12/DA-2022-DEMO03-02.pdf', '已归档', 2, NULL, 0, 'admin', '2022-12-28 10:16:00'),

  -- ===== demo-ar-04 富锦路 =====
  ('demo-af-0401', 'demo-ar-04', 1, 'd3-05', NULL, NULL, '富锦路规划条件通知书.pdf', '富锦路规划条件通知书', 'pdf', 1677721, NULL, NULL, 'local', '/archive/2023/06/DA-2023-DEMO01-01.pdf', '已归档', 1, NULL, 0, 'zhangsan', '2023-06-18 11:25:00'),
  ('demo-af-0402', 'demo-ar-04', 2, 'd2-09', NULL, NULL, '富锦路建设工程规划许可证.pdf', '富锦路建设工程规划许可证', 'pdf', 1048576, NULL, NULL, 'local', '/archive/2023/06/DA-2023-DEMO01-02.pdf', '已归档', 2, NULL, 0, 'zhangsan', '2023-06-18 11:28:00'),
  ('demo-af-0403', 'demo-ar-04', 3, 'd3-13', NULL, NULL, '富锦路施工图审查合格书.pdf', '富锦路施工图审查合格书', 'pdf', 1363148, NULL, NULL, 'local', '/archive/2023/06/DA-2023-DEMO01-03.pdf', '已归档', 3, NULL, 0, 'zhangsan', '2023-06-20 15:35:00'),

  -- ===== demo-ar-05 霞宏道 =====
  ('demo-af-0501', 'demo-ar-05', 1, 'd2-06', NULL, NULL, '霞宏道选址意见书.pdf', '霞宏道选址意见书', 'pdf', 1258291, NULL, NULL, 'local', '/archive/2023/09/DA-2023-DEMO02-01.pdf', '已归档', 1, NULL, 0, 'admin', '2023-09-26 15:45:00'),
  ('demo-af-0502', 'demo-ar-05', 2, 'd3-06', NULL, NULL, '霞宏道规划条件附图.jpg', '霞宏道规划条件附图', 'jpg', 3565158, NULL, NULL, 'local', '/archive/2023/09/DA-2023-DEMO02-02.jpg', '已归档', 2, NULL, 0, 'admin', '2023-09-26 15:50:00'),
  ('demo-af-0503', 'demo-ar-05', 3, 'd2-09', NULL, NULL, '霞宏道建设工程规划许可证.pdf', '霞宏道建设工程规划许可证', 'pdf', 1153433, NULL, NULL, 'local', '/archive/2023/09/DA-2023-DEMO02-03.pdf', '已归档', 3, NULL, 0, 'admin', '2023-09-27 09:15:00'),
  ('demo-af-0504', 'demo-ar-05', 4, 'd3-08', NULL, NULL, '霞宏道竣工测绘报告.pdf', '霞宏道竣工测绘报告', 'pdf', 5033164, NULL, NULL, 'local', '/archive/2023/09/DA-2023-DEMO02-04.pdf', '已归档', 4, NULL, 0, 'admin', '2023-09-28 10:10:00'),

  -- ===== demo-ar-06 广东山庄路（审核中，文件也是审核中）=====
  ('demo-af-0601', 'demo-ar-06', 1, 'd3-18', NULL, NULL, '广东山庄路排水工程移交单.pdf', '广东山庄路排水工程移交单', 'pdf', 1992294, NULL, NULL, 'local', '/archive/2023/12/DA-2023-DEMO03-01.pdf', '审核中', 1, '待复核接收单位签章', 0, 'jeecg', '2023-12-11 09:20:00'),
  ('demo-af-0602', 'demo-ar-06', 2, 'd2-21', NULL, NULL, '广东山庄路质量监督报告.pdf', '广东山庄路质量监督报告', 'pdf', 2306867, NULL, NULL, 'local', '/archive/2023/12/DA-2023-DEMO03-02.pdf', '审核中', 2, NULL, 0, 'jeecg', '2023-12-15 16:45:00'),

  -- ===== demo-ar-07 霞宏道东段 =====
  ('demo-af-0701', 'demo-ar-07', 1, 'd3-05', NULL, NULL, '霞宏道东段规划条件通知书.pdf', '霞宏道东段规划条件通知书', 'pdf', 1468006, NULL, NULL, 'local', '/archive/2024/04/DA-2024-DEMO01-01.pdf', '已归档', 1, NULL, 0, 'admin', '2024-04-22 10:35:00'),
  ('demo-af-0702', 'demo-ar-07', 2, 'd2-09', NULL, NULL, '霞宏道东段建设工程规划许可证.pdf', '霞宏道东段建设工程规划许可证', 'pdf', 1153433, NULL, NULL, 'local', '/archive/2024/04/DA-2024-DEMO01-02.pdf', '已归档', 2, NULL, 0, 'admin', '2024-04-22 10:38:00'),
  ('demo-af-0703', 'demo-ar-07', 3, 'd3-07', NULL, NULL, '霞宏道东段规划验收合格证.pdf', '霞宏道东段规划验收合格证', 'pdf', 838860, NULL, NULL, 'local', '/archive/2024/04/DA-2024-DEMO01-03.pdf', '已归档', 3, NULL, 0, 'admin', '2024-04-24 09:15:00'),

  -- ===== demo-ar-08 学苑路（资金类）=====
  ('demo-af-0801', 'demo-ar-08', 1, 'd3-11', NULL, NULL, '学苑路2024年度资金计划.xlsx', '学苑路2024年度资金计划', 'xlsx', 389120, NULL, NULL, 'local', '/archive/2024/07/DA-2024-DEMO02-01.xlsx', '已归档', 1, NULL, 0, 'zhangsan', '2024-07-15 16:10:00'),
  ('demo-af-0802', 'demo-ar-08', 2, 'd2-17', NULL, NULL, '学苑路资金拨付凭证.pdf', '学苑路资金拨付凭证', 'pdf', 1258291, NULL, NULL, 'local', '/archive/2024/07/DA-2024-DEMO02-02.pdf', '已归档', 2, NULL, 0, 'zhangsan', '2024-07-15 16:14:00'),
  ('demo-af-0803', 'demo-ar-08', 3, 'd2-18', NULL, NULL, '学苑路决算审核意见.pdf', '学苑路决算审核意见', 'pdf', 1572864, NULL, NULL, 'local', '/archive/2024/07/DA-2024-DEMO02-03.pdf', '已归档', 3, NULL, 0, 'zhangsan', '2024-07-18 14:25:00'),

  -- ===== demo-ar-09 冶金路（归档中）=====
  ('demo-af-0901', 'demo-ar-09', 1, 'd2-20', NULL, NULL, '冶金路施工许可.pdf', '冶金路施工许可', 'pdf', 2202009, NULL, NULL, 'local', '/archive/2024/10/DA-2024-DEMO03-01.pdf', '归档中', 1, '其余要件待补充', 0, 'jeecg', '2024-10-09 09:55:00'),

  -- ===== demo-ar-10 曲江路：故意没有文件（验证「无文件不能置为已归档」）=====

  -- ===== demo-ar-11 仁昌路竣工 =====
  ('demo-af-1101', 'demo-ar-11', 1, 'd3-13', NULL, NULL, '仁昌路施工图审查合格书.pdf', '仁昌路施工图审查合格书', 'pdf', 1468006, NULL, NULL, 'local', '/archive/2025/03/DA-2025-DEMO01-01.pdf', '已归档', 1, NULL, 0, 'zhangsan', '2025-03-18 10:20:00'),
  ('demo-af-1102', 'demo-ar-11', 2, 'd3-15', NULL, NULL, '仁昌路竣工验收报告.pdf', '仁昌路竣工验收报告', 'pdf', 5872025, NULL, NULL, 'local', '/archive/2025/03/DA-2025-DEMO01-02.pdf', '已归档', 2, NULL, 0, 'zhangsan', '2025-03-18 10:26:00'),
  ('demo-af-1103', 'demo-ar-11', 3, 'd3-16', NULL, NULL, '仁昌路竣工验收备案表.pdf', '仁昌路竣工验收备案表', 'pdf', 1153433, NULL, NULL, 'local', '/archive/2025/03/DA-2025-DEMO01-03.pdf', '已归档', 3, NULL, 0, 'zhangsan', '2025-03-18 10:30:00'),
  ('demo-af-1104', 'demo-ar-11', 4, 'd3-16', NULL, NULL, '仁昌路道路竣工图.dwg', '仁昌路道路竣工图', 'dwg', 4718592, NULL, NULL, 'local', '/archive/2025/03/DA-2025-DEMO01-04.dwg', '已归档', 4, 'CAD 竣工图，仅登记元数据（演示数据不生成真实 CAD 文件）', 0, 'zhangsan', '2025-03-21 11:10:00'),
  ('demo-af-1105', 'demo-ar-11', 5, 'd3-17', NULL, NULL, '仁昌路道路工程移交书.pdf', '仁昌路道路工程移交书', 'pdf', 2411724, NULL, NULL, 'local', '/archive/2025/03/DA-2025-DEMO01-05.pdf', '已归档', 5, NULL, 0, 'zhangsan', '2025-03-21 11:15:00'),

  -- ===== demo-ar-12 富贵路竣工 =====
  ('demo-af-1201', 'demo-ar-12', 1, 'd2-21', NULL, NULL, '富贵路质量监督报告.pdf', '富贵路质量监督报告', 'pdf', 2831155, NULL, NULL, 'local', '/archive/2025/05/DA-2025-DEMO02-01.pdf', '已归档', 1, NULL, 0, 'admin', '2025-05-27 11:50:00'),
  ('demo-af-1202', 'demo-ar-12', 2, 'd3-15', NULL, NULL, '富贵路竣工验收报告.pdf', '富贵路竣工验收报告', 'pdf', 5138022, NULL, NULL, 'local', '/archive/2025/05/DA-2025-DEMO02-02.pdf', '已归档', 2, NULL, 0, 'admin', '2025-05-27 11:55:00'),
  ('demo-af-1203', 'demo-ar-12', 3, 'd3-17', NULL, NULL, '富贵路道路工程移交书.pdf', '富贵路道路工程移交书', 'pdf', 1887436, NULL, NULL, 'local', '/archive/2025/05/DA-2025-DEMO02-03.pdf', '已归档', 3, NULL, 0, 'admin', '2025-05-29 09:45:00'),

  -- ===== demo-ar-13 广东山庄路规划验收（审核中）=====
  ('demo-af-1301', 'demo-ar-13', 1, 'd3-07', NULL, NULL, '广东山庄路规划验收合格证.pdf', '广东山庄路规划验收合格证', 'pdf', 943718, NULL, NULL, 'local', '/archive/2025/08/DA-2025-DEMO03-01.pdf', '审核中', 1, NULL, 0, 'jeecg', '2025-08-14 09:10:00'),
  ('demo-af-1302', 'demo-ar-13', 2, 'd3-08', NULL, NULL, '广东山庄路竣工测绘报告.pdf', '广东山庄路竣工测绘报告', 'pdf', 6501171, NULL, NULL, 'local', '/archive/2025/08/DA-2025-DEMO03-02.pdf', '审核中', 2, '大文件，用于验证下载与打包', 0, 'jeecg', '2025-08-18 15:20:00'),

  -- ===== demo-ar-14 立新路资金决算（归档中）=====
  ('demo-af-1401', 'demo-ar-14', 1, 'd2-18', NULL, NULL, '立新路决算审核意见.pdf', '立新路决算审核意见', 'pdf', 1677721, NULL, NULL, 'local', '/archive/2025/11/DA-2025-DEMO04-01.pdf', '归档中', 1, NULL, 0, 'zhangsan', '2025-11-06 15:35:00'),

  -- ===== demo-ar-15 霞宏道移交 =====
  ('demo-af-1501', 'demo-ar-15', 1, 'd3-17', NULL, NULL, '霞宏道道路工程移交书.pdf', '霞宏道道路工程移交书', 'pdf', 2097152, NULL, NULL, 'local', '/archive/2026/02/DA-2026-DEMO01-01.pdf', '已归档', 1, NULL, 0, 'admin', '2026-02-26 10:45:00'),
  ('demo-af-1502', 'demo-ar-15', 2, 'd3-18', NULL, NULL, '霞宏道排水工程移交书.pdf', '霞宏道排水工程移交书', 'pdf', 1782579, NULL, NULL, 'local', '/archive/2026/02/DA-2026-DEMO01-02.pdf', '已归档', 2, NULL, 0, 'admin', '2026-02-26 10:50:00'),
  ('demo-af-1503', 'demo-ar-15', 3, 'd2-15', NULL, NULL, '霞宏道不动产登记资料.zip', '霞宏道不动产登记资料', 'zip', 8808038, NULL, NULL, 'local', '/archive/2026/02/DA-2026-DEMO01-03.zip', '已归档', 3, '（真实 zip，内含登记材料清单文本）', 0, 'admin', '2026-02-28 14:55:00'),

  -- ===== demo-ar-16 学苑路结算：故意没有文件 =====

  -- ===== demo-ar-17 冶金路竣工（纸质、审核中）=====
  ('demo-af-1701', 'demo-ar-17', 1, 'd3-15', NULL, NULL, '冶金路竣工验收报告.pdf', '冶金路竣工验收报告', 'pdf', 3460301, NULL, NULL, 'local', '/archive/2026/05/DA-2026-DEMO03-01.pdf', '审核中', 1, NULL, 0, 'admin', '2026-05-19 09:40:00'),
  ('demo-af-1702', 'demo-ar-17', 2, 'd3-07', NULL, NULL, '冶金路规划验收合格证.pdf', '冶金路规划验收合格证', 'pdf', 734003, NULL, NULL, 'local', '/archive/2026/05/DA-2026-DEMO03-02.pdf', '审核中', 2, NULL, 0, 'admin', '2026-05-22 16:35:00'),

  -- ===== demo-ar-18 富锦路移交（秘密）=====
  ('demo-af-1801', 'demo-ar-18', 1, 'd3-17', NULL, NULL, '富锦路道路工程移交书.pdf', '富锦路道路工程移交书', 'pdf', 1992294, NULL, NULL, 'local', '/archive/2026/06/DA-2026-DEMO04-01.pdf', '已归档', 1, NULL, 0, 'jeecg', '2026-06-30 16:25:00'),
  ('demo-af-1802', 'demo-ar-18', 2, 'd2-27', NULL, NULL, '富锦路移交补充材料.docx', '富锦路移交补充材料', 'docx', 471040, NULL, NULL, 'local', '/archive/2026/06/DA-2026-DEMO04-02.docx', '已归档', 2, NULL, 0, 'jeecg', '2026-07-02 10:15:00');

-- ---------------------------------------------------------------------------
-- 4) ★ 回填 category_path / category_name（模拟服务层保存文件时的冗余逻辑）
--
--    category_name 是「类别全路径名称」，由 path（主键串）逐级拼出来。
--    这里用 3 次自连接取出第 2/3/4 段对应的类别名，
--    再用 CASE 把「就是自己」的那一段排除掉，从而兼容 2~4 级类别。
--
--    ★ 为什么必须回填：列表页的「档案类别」列、详情页的文件类别、
--      以及按父类别检索（走 category_path 前缀匹配）都依赖这两列。
-- ---------------------------------------------------------------------------
UPDATE `t_archive_file` f
JOIN `t_archive_category` c   ON c.`id` = f.`category_id`
LEFT JOIN `t_archive_category` lv1 ON lv1.`id` = SUBSTRING_INDEX(SUBSTRING_INDEX(c.`path`, '/', 2), '/', -1)
LEFT JOIN `t_archive_category` lv2 ON lv2.`id` = SUBSTRING_INDEX(SUBSTRING_INDEX(c.`path`, '/', 3), '/', -1)
LEFT JOIN `t_archive_category` lv3 ON lv3.`id` = SUBSTRING_INDEX(SUBSTRING_INDEX(c.`path`, '/', 4), '/', -1)
SET f.`category_path` = c.`path`,
    f.`category_name` = CONCAT_WS(' / ',
        lv1.`name`,
        CASE WHEN lv2.`id` IS NOT NULL AND lv2.`id` <> c.`id` THEN lv2.`name` END,
        CASE WHEN lv3.`id` IS NOT NULL AND lv3.`id` <> c.`id` AND lv3.`id` <> lv2.`id` THEN lv3.`name` END,
        c.`name`)
WHERE f.`id` LIKE 'demo-af-%';

-- ---------------------------------------------------------------------------
-- 5) 回填 file_count / total_size（主表上的冗余统计，按实际文件重算）
-- ---------------------------------------------------------------------------
UPDATE `t_archive` a
LEFT JOIN (
    SELECT `archive_id`, COUNT(1) AS cnt, COALESCE(SUM(`file_size`), 0) AS sz
    FROM `t_archive_file`
    WHERE `del_flag` = 0 AND `id` LIKE 'demo-af-%'
    GROUP BY `archive_id`
) s ON s.`archive_id` = a.`id`
SET a.`file_count` = COALESCE(s.cnt, 0),
    a.`total_size` = COALESCE(s.sz, 0)
WHERE a.`id` LIKE 'demo-ar-%';

-- ---------------------------------------------------------------------------
-- 6) 操作记录
--    新增 / 上传 / 归档 三类按档案数据批量生成（保证与主表一致），
--    另外手写几条修改 / 下载 / 导出，让「操作记录」页签有真实感的痕迹。
-- ---------------------------------------------------------------------------
INSERT INTO `t_archive_log`
  (`id`, `archive_id`, `action`, `detail`, `biz_key`, `operate_by`, `operate_name`, `operate_time`, `ip`)
SELECT CONCAT('demo-al-add-', SUBSTRING(a.`id`, 9)), a.`id`, '新增',
       CONCAT('新增档案【', a.`archive_no`, '】', a.`archive_name`),
       a.`archive_no`, a.`create_by`,
       CASE a.`create_by` WHEN 'admin' THEN '管理员' WHEN 'zhangsan' THEN '张三' ELSE 'jeecg' END,
       a.`create_time`, '127.0.0.1'
FROM `t_archive` a WHERE a.`id` LIKE 'demo-ar-%';

INSERT INTO `t_archive_log`
  (`id`, `archive_id`, `action`, `detail`, `biz_key`, `operate_by`, `operate_name`, `operate_time`, `ip`)
SELECT CONCAT('demo-al-up-', SUBSTRING(a.`id`, 9)), a.`id`, '上传',
       CONCAT('上传 ', a.`file_count`, ' 个卷内文件，合计 ',
              ROUND(a.`total_size` / 1048576, 2), ' MB'),
       a.`archive_no`, a.`create_by`,
       CASE a.`create_by` WHEN 'admin' THEN '管理员' WHEN 'zhangsan' THEN '张三' ELSE 'jeecg' END,
       DATE_ADD(a.`create_time`, INTERVAL 5 MINUTE), '127.0.0.1'
FROM `t_archive` a WHERE a.`id` LIKE 'demo-ar-%' AND a.`file_count` > 0;

INSERT INTO `t_archive_log`
  (`id`, `archive_id`, `action`, `detail`, `biz_key`, `operate_by`, `operate_name`, `operate_time`, `ip`)
SELECT CONCAT('demo-al-arc-', SUBSTRING(a.`id`, 9)), a.`id`, '归档',
       CONCAT('档案【', a.`archive_no`, '】状态 审核中 → 已归档'),
       a.`archive_no`, a.`update_by`,
       CASE a.`update_by` WHEN 'admin' THEN '管理员' WHEN 'zhangsan' THEN '张三' ELSE 'jeecg' END,
       a.`update_time`, '127.0.0.1'
FROM `t_archive` a WHERE a.`id` LIKE 'demo-ar-%' AND a.`status` = '已归档';

INSERT INTO `t_archive_log`
  (`id`, `archive_id`, `file_id`, `action`, `detail`, `biz_key`, `operate_by`, `operate_name`, `operate_time`, `ip`)
VALUES
  ('demo-al-mod-01', 'demo-ar-01', NULL, '修改',
   '修改档案【DA-2022-DEMO01】；配套负责人 王海涛 → 张建国；备注 无 → 含规划、施工、移交三类要件，跨 4 个档案类别',
   'DA-2022-DEMO01', 'admin', '管理员', '2022-11-22 09:12:00', '127.0.0.1'),
  ('demo-al-mod-02', 'demo-ar-06', NULL, '修改',
   '修改档案【DA-2023-DEMO03】；状态 未归档 → 审核中；密级 内部 → 一般',
   'DA-2023-DEMO03', 'jeecg', 'jeecg', '2023-12-15 16:38:00', '127.0.0.1'),
  ('demo-al-mod-03', 'demo-ar-18', NULL, '修改',
   '修改档案【DA-2026-DEMO04】；密级 内部 → 秘密',
   'DA-2026-DEMO04', 'jeecg', 'jeecg', '2026-07-02 10:08:00', '127.0.0.1'),
  ('demo-al-dl-01', 'demo-ar-05', 'demo-af-0504', '下载',
   '下载文件「霞宏道竣工测绘报告.pdf」', '规划手续 / 规划验收 / 竣工测绘报告', 'zhangsan', '张三', '2023-09-29 08:55:00', '127.0.0.1'),
  ('demo-al-dl-02', 'demo-ar-13', 'demo-af-1302', '下载',
   '下载文件「广东山庄路竣工测绘报告.pdf」', '规划手续 / 规划验收 / 竣工测绘报告', 'jeecg', 'jeecg', '2025-08-19 14:02:00', '127.0.0.1'),
  ('demo-al-exp-01', NULL, NULL, '导出',
   '导出档案，共 4 卷，操作人：管理员', '仁昌路、诚盛道、诚润道', 'admin', '管理员', '2026-03-02 11:20:00', '127.0.0.1');

-- ---------------------------------------------------------------------------
-- 7) 核对
-- ---------------------------------------------------------------------------
SELECT '档案总数' AS 项, COUNT(*) AS 值 FROM `t_archive` WHERE `id` LIKE 'demo-ar-%'
UNION ALL SELECT '卷内文件总数', COUNT(*) FROM `t_archive_file` WHERE `id` LIKE 'demo-af-%'
UNION ALL SELECT '操作记录总数', COUNT(*) FROM `t_archive_log` WHERE `id` LIKE 'demo-al-%' OR `archive_id` LIKE 'demo-ar-%'
UNION ALL SELECT '文件类别未回填的行(应为0)', COUNT(*) FROM `t_archive_file` WHERE `id` LIKE 'demo-af-%' AND (`category_name` IS NULL OR `category_path` IS NULL)
UNION ALL SELECT '主表文件数与实际不符的行(应为0)', COUNT(*) FROM `t_archive` a
    LEFT JOIN (SELECT `archive_id`, COUNT(1) c FROM `t_archive_file` WHERE `del_flag`=0 AND `id` LIKE 'demo-af-%' GROUP BY `archive_id`) s
      ON s.`archive_id` = a.`id`
    WHERE a.`id` LIKE 'demo-ar-%' AND a.`file_count` <> COALESCE(s.c, 0);

SELECT a.`status` AS 状态, COUNT(*) AS 卷数, SUM(a.`file_count`) AS 文件数
FROM `t_archive` a WHERE a.`id` LIKE 'demo-ar-%' GROUP BY a.`status` ORDER BY 卷数 DESC;

SELECT a.`archive_year` AS 年度, COUNT(*) AS 卷数 FROM `t_archive` a
WHERE a.`id` LIKE 'demo-ar-%' GROUP BY a.`archive_year` ORDER BY a.`archive_year`;

SELECT c1.`name` AS 顶级类别, COUNT(f.`id`) AS 文件数
FROM `t_archive_file` f
JOIN `t_archive_category` c ON c.`id` = f.`category_id`
JOIN `t_archive_category` c1 ON c1.`id` = SUBSTRING_INDEX(SUBSTRING_INDEX(c.`path`, '/', 2), '/', -1)
WHERE f.`id` LIKE 'demo-af-%'
GROUP BY c1.`id`, c1.`name` ORDER BY 文件数 DESC;

-- ============================================================================
--  清理（需要时手工执行，只删演示数据，不影响真实档案）
-- ============================================================================
-- DELETE FROM t_archive_log  WHERE id LIKE 'demo-al-%' OR archive_id LIKE 'demo-ar-%';
-- DELETE FROM t_archive_file WHERE id LIKE 'demo-af-%';
-- DELETE FROM t_archive      WHERE id LIKE 'demo-ar-%';
-- 磁盘文件：删掉 {jeecg.path.upload}/archive 下对应目录即可（见 scripts/gen-archive-demo-files.ps1 的 -Clean 开关）
