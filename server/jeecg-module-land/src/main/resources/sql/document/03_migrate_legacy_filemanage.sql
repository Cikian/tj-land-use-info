-- ============================================================================
--  旧收发文数据迁移：xj_filemanage_*（旧 tj-sfw 表） → t_doc_*（新表族）
--  目标库：tj-jyxyd
--
--  前置：01_t_doc.sql 已执行（新表存在）。
--
--  ★ 迁移原则
--    1. **主键沿用旧 id**：旧表 id 是 varchar(36) 的雪花串，直接复用可以做到
--       「幂等 + 可溯源」——重复执行只是覆盖更新，不需要额外的 source_id 列。
--    2. **字段语义修正（旧系统的坑 R14）**：
--       xj_filemanage_send.file_receive_num 实际存的是**发文文号** → t_doc_send.doc_no；
--       xj_filemanage_send.file_send_num   实际存的是**收文文号** → 拼进 remark 保留痕迹。
--    3. **状态映射**沿用旧语义：1待流转→待承办 / 2流转中→承办中 / 3已退回→已退回 / 4已办结→已办结。
--    4. **附件拆分**：旧表只有一个逗号分隔的 file_path 字段，这里拆成 t_doc_attachment 多条。
--       MySQL 5.7 没有 split 函数，用 1..10 的数字表 + SUBSTRING_INDEX 实现。
--    5. **孤儿流转记录**：本机旧库里 17 条 xj_filemanage_flow 的 sr_id 全部指向
--       已不存在的收文（实测收文表只剩 2 条），因此按「只迁主表存在的收文」的规则，
--       这 17 条不会被迁入。脚本末尾会输出孤儿条数供核对。
--    6. 旧表**不删除**，仅作为历史留痕；回滚只需 DELETE 新表里 id 属于旧 id 集合的行。
--
--  可重复执行。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 收文：xj_filemanage_receive → t_doc_receive
-- ---------------------------------------------------------------------------
INSERT INTO `t_doc_receive` (
  `id`, `doc_no`, `doc_title`, `doc_type`, `from_dept`, `receive_date`,
  `secret_level`, `land_id`, `crzdbh`, `facility_id`, `ptxmmc`,
  `status`, `current_handler`, `current_handler_name`,
  `archive_id`, `remark`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`
)
SELECT
  r.`id`,
  COALESCE(NULLIF(TRIM(r.`file_receive_num`), ''), CONCAT('SW-LEGACY-', r.`id`)),
  COALESCE(NULLIF(TRIM(r.`file_name`), ''), '（历史收文，无标题）'),
  NULLIF(TRIM(r.`file_type`), ''),
  NULLIF(TRIM(r.`send_org`), ''),
  DATE(r.`scan_time`),
  NULLIF(TRIM(r.`file_classification`), ''),
  l.`id`,
  f.`crzdbh`,
  f.`id`,
  NULLIF(TRIM(r.`ywk_pro`), ''),
  CASE r.`file_status`
    WHEN '2' THEN '承办中'
    WHEN '3' THEN '已退回'
    WHEN '4' THEN '已办结'
    ELSE '待承办'
  END,
  NULLIF(TRIM(r.`current_flow`), ''),
  (SELECT u.`realname` FROM `sys_user` u WHERE u.`username` = TRIM(r.`current_flow`) LIMIT 1),
  NULL,
  NULLIF(TRIM(r.`bz`), ''),
  0,
  r.`create_by`, r.`create_time`, r.`update_by`, r.`update_time`
FROM `xj_filemanage_receive` r
LEFT JOIN `xj_kjkfb_supporting_facilities` f ON f.`ptxmmc` = TRIM(r.`ywk_pro`)
LEFT JOIN `t_land` l ON l.`crzdbh` = f.`crzdbh` AND l.`del_flag` = 0
ON DUPLICATE KEY UPDATE
  `doc_no`        = VALUES(`doc_no`),
  `doc_title`     = VALUES(`doc_title`),
  `doc_type`      = VALUES(`doc_type`),
  `from_dept`     = VALUES(`from_dept`),
  `receive_date`  = VALUES(`receive_date`),
  `secret_level`  = VALUES(`secret_level`),
  `land_id`       = VALUES(`land_id`),
  `crzdbh`        = VALUES(`crzdbh`),
  `facility_id`   = VALUES(`facility_id`),
  `ptxmmc`        = VALUES(`ptxmmc`),
  `status`        = VALUES(`status`),
  `current_handler` = VALUES(`current_handler`),
  `current_handler_name` = VALUES(`current_handler_name`),
  `remark`        = VALUES(`remark`),
  `update_time`   = VALUES(`update_time`);

-- ---------------------------------------------------------------------------
-- 2) 发文：xj_filemanage_send → t_doc_send
--    ★ file_receive_num 存的是发文文号（旧系统字段名语义反置）
-- ---------------------------------------------------------------------------
INSERT INTO `t_doc_send` (
  `id`, `doc_no`, `doc_title`, `doc_type`, `to_dept`, `drafter`, `issue_date`,
  `land_id`, `crzdbh`, `facility_id`, `ptxmmc`,
  `archive_id`, `remark`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`
)
SELECT
  s.`id`,
  COALESCE(NULLIF(TRIM(s.`file_receive_num`), ''), NULLIF(TRIM(s.`file_send_num`), ''), CONCAT('FW-LEGACY-', s.`id`)),
  COALESCE(NULLIF(TRIM(s.`file_name`), ''), '（历史发文，无标题）'),
  NULLIF(TRIM(s.`file_type`), ''),
  NULLIF(TRIM(s.`file_receive_org`), ''),
  NULLIF(TRIM(s.`file_send_person`), ''),
  DATE(s.`send_time`),
  l.`id`,
  f.`crzdbh`,
  f.`id`,
  NULLIF(TRIM(s.`ywk_pro`), ''),
  NULL,
  CONCAT_WS(' | ',
    NULLIF(TRIM(s.`remark`), ''),
    CASE WHEN NULLIF(TRIM(s.`file_send_num`), '') IS NOT NULL
         THEN CONCAT('旧系统关联收文文号：', TRIM(s.`file_send_num`)) END),
  0,
  s.`create_by`, s.`create_time`, s.`update_by`, s.`update_time`
FROM `xj_filemanage_send` s
LEFT JOIN `xj_kjkfb_supporting_facilities` f ON f.`ptxmmc` = TRIM(s.`ywk_pro`)
LEFT JOIN `t_land` l ON l.`crzdbh` = f.`crzdbh` AND l.`del_flag` = 0
ON DUPLICATE KEY UPDATE
  `doc_no`     = VALUES(`doc_no`),
  `doc_title`  = VALUES(`doc_title`),
  `doc_type`   = VALUES(`doc_type`),
  `to_dept`    = VALUES(`to_dept`),
  `drafter`    = VALUES(`drafter`),
  `issue_date` = VALUES(`issue_date`),
  `land_id`    = VALUES(`land_id`),
  `crzdbh`     = VALUES(`crzdbh`),
  `facility_id`= VALUES(`facility_id`),
  `ptxmmc`     = VALUES(`ptxmmc`),
  `remark`     = VALUES(`remark`),
  `update_time`= VALUES(`update_time`);

-- ---------------------------------------------------------------------------
-- 3) 流转记录：xj_filemanage_flow → t_doc_receive_flow（只迁主表仍存在的收文）
-- ---------------------------------------------------------------------------
INSERT INTO `t_doc_receive_flow` (
  `id`, `doc_id`, `node_name`, `action`, `handler`, `handler_name`,
  `opinion`, `receive_time`, `handle_time`, `seq_no`, `del_flag`, `create_by`, `create_time`
)
SELECT
  fl.`id`,
  fl.`sr_id`,
  '承办',
  CASE WHEN fl.`remark` LIKE '%退回%' THEN '退回' ELSE '转办' END,
  fl.`sender`,
  fl.`sender_name`,
  fl.`remark`,
  fl.`create_time`,
  fl.`create_time`,
  (SELECT COUNT(*) FROM `xj_filemanage_flow` f2
    WHERE f2.`sr_id` = fl.`sr_id`
      AND (f2.`create_time` < fl.`create_time`
           OR (f2.`create_time` = fl.`create_time` AND f2.`id` <= fl.`id`))) AS seq_no,
  0,
  fl.`create_by`,
  fl.`create_time`
FROM `xj_filemanage_flow` fl
JOIN `xj_filemanage_receive` r ON r.`id` = fl.`sr_id`
ON DUPLICATE KEY UPDATE
  `opinion` = VALUES(`opinion`);

-- ---------------------------------------------------------------------------
-- 4) 附件：file_path（逗号分隔）→ t_doc_attachment
--    用 1..10 的数字表把一行拆成多行；本机旧数据最长只有 2 个附件。
-- ---------------------------------------------------------------------------
INSERT INTO `t_doc_attachment` (
  `id`, `doc_type`, `doc_id`, `file_name`, `file_ext`, `store_path`,
  `sort_no`, `upload_by`, `upload_time`, `del_flag`
)
SELECT
  CONCAT('lg-recv-', r.`id`, '-', n.`i`),
  'receive',
  r.`id`,
  SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(r.`file_path`, ',', n.`i`), ',', -1), '/', -1),
  CASE WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(r.`file_path`, ',', n.`i`), ',', -1), '/', -1) LIKE '%.%'
       THEN LOWER(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(r.`file_path`, ',', n.`i`), ',', -1), '.', -1)) END,
  SUBSTRING_INDEX(SUBSTRING_INDEX(r.`file_path`, ',', n.`i`), ',', -1),
  n.`i`,
  r.`create_by`,
  r.`create_time`,
  0
FROM `xj_filemanage_receive` r
JOIN (SELECT 1 AS i UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
      UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10) n
  ON n.`i` <= 1 + LENGTH(r.`file_path`) - LENGTH(REPLACE(r.`file_path`, ',', ''))
WHERE r.`file_path` IS NOT NULL AND TRIM(r.`file_path`) <> ''
ON DUPLICATE KEY UPDATE `store_path` = VALUES(`store_path`);

INSERT INTO `t_doc_attachment` (
  `id`, `doc_type`, `doc_id`, `file_name`, `file_ext`, `store_path`,
  `sort_no`, `upload_by`, `upload_time`, `del_flag`
)
SELECT
  CONCAT('lg-send-', s.`id`, '-', n.`i`),
  'send',
  s.`id`,
  SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(s.`file_path`, ',', n.`i`), ',', -1), '/', -1),
  CASE WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(s.`file_path`, ',', n.`i`), ',', -1), '/', -1) LIKE '%.%'
       THEN LOWER(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(s.`file_path`, ',', n.`i`), ',', -1), '.', -1)) END,
  SUBSTRING_INDEX(SUBSTRING_INDEX(s.`file_path`, ',', n.`i`), ',', -1),
  n.`i`,
  s.`create_by`,
  s.`create_time`,
  0
FROM `xj_filemanage_send` s
JOIN (SELECT 1 AS i UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
      UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10) n
  ON n.`i` <= 1 + LENGTH(s.`file_path`) - LENGTH(REPLACE(s.`file_path`, ',', ''))
WHERE s.`file_path` IS NOT NULL AND TRIM(s.`file_path`) <> ''
ON DUPLICATE KEY UPDATE `store_path` = VALUES(`store_path`);

-- ---------------------------------------------------------------------------
-- 5) 核对
-- ---------------------------------------------------------------------------
SELECT '旧收文' AS 项目, COUNT(*) AS 旧表条数 FROM `xj_filemanage_receive`
UNION ALL SELECT '新收文', COUNT(*) FROM `t_doc_receive` WHERE id IN (SELECT id FROM `xj_filemanage_receive`)
UNION ALL SELECT '旧发文', COUNT(*) FROM `xj_filemanage_send`
UNION ALL SELECT '新发文', COUNT(*) FROM `t_doc_send` WHERE id IN (SELECT id FROM `xj_filemanage_send`)
UNION ALL SELECT '旧流转', COUNT(*) FROM `xj_filemanage_flow`
UNION ALL SELECT '新流转', COUNT(*) FROM `t_doc_receive_flow` WHERE id IN (SELECT id FROM `xj_filemanage_flow`)
UNION ALL SELECT '孤儿流转(不迁)', COUNT(*) FROM `xj_filemanage_flow` fl
  WHERE NOT EXISTS (SELECT 1 FROM `xj_filemanage_receive` r WHERE r.id = fl.sr_id)
UNION ALL SELECT '新附件(收文)', COUNT(*) FROM `t_doc_attachment` WHERE doc_type = 'receive'
UNION ALL SELECT '新附件(发文)', COUNT(*) FROM `t_doc_attachment` WHERE doc_type = 'send';
