-- =====================================================================
-- 08_archive_status_and_receive_unit_check.sql
-- ---------------------------------------------------------------------
-- 用途：**只查不改**的核对脚本（幂等，可重复执行）。
--
--   ① P1-15 档案状态核对：同一个档案 ID 在「库 / 列表 / 详情 / 统计 / 导出」
--      五处应当是同一个 t_archive.status。这五处口径本来就是同一条 SQL 片段
--      （ArchiveMapper.xml 的 queryWhere），因此差异只可能来自三种原因：
--         · 改状态的事务没提交（前端显示成功但库里没变）；
--         · 列表没刷新（前端缓存了旧行）；
--         · 该条历史数据的 status 不在枚举内（历史非法值，需要修数据）。
--      下面的查询把三种情况分别指出来。
--
--   ② P1-11 归档链路核对：某条收发文归档后「原附件数 → 转换文件数 →
--      t_archive_file 行数」是否对得上（断点定位用）。
--
--   ③ P2-08 接收管养单位数据质量核对：排行里排除掉的占位值到底有多少条、
--      都是些什么值（与新统计口径「接收管养单位未填写数量」一致）。
--
-- ★ 本脚本不建表、不改列、不写数据；执行后只需看结果。
-- ★ 不要在本脚本里加 UPDATE / DELETE：历史档案与文件的清理必须由业务确认后单独处理。
-- =====================================================================

-- ---------------------------------------------------------------------
-- ① 档案状态五处核对
--    把 :archiveId 换成要核对的档案 ID（报告样例是档案号 DA-2026-0002，
--    所以先按档案号查出 ID，再往下套）。
-- ---------------------------------------------------------------------

-- ①-1 先按档案号取 ID（列表里点开的那一行）
SELECT a.id, a.archive_no, a.archive_name, a.status, a.del_flag, a.update_time
  FROM t_archive a
 WHERE a.del_flag = 0
   AND a.archive_no = 'DA-2026-0002';

-- ①-2 「库」的值：直接读主表
SELECT a.id, a.archive_no, a.status AS dbStatus, a.file_count, a.total_size
  FROM t_archive a
 WHERE a.del_flag = 0
   AND a.id = 'DA-2026-0002';   -- ← 换成 ①-1 查到的 id

-- ①-3 「列表 / 详情 / 统计 / 导出」的口径一致性：
--      四者共用 ArchiveMapper.xml 的 queryWhere + t_archive.status，
--      这里用同一条查询同时给出「行数」与「去重后的状态集合」。
--      若某处页面显示的状态不在下面这个集合里，就是前端缓存/刷新问题，不是数据问题。
SELECT COUNT(1)            AS rowCount,
       COUNT(DISTINCT a.status) AS distinctStatusCount,
       GROUP_CONCAT(DISTINCT a.status ORDER BY a.status) AS statuses
  FROM t_archive a
 WHERE a.del_flag = 0
   AND a.id = 'DA-2026-0002';

-- ①-4 历史非法状态清单（不在「未归档 / 归档中 / 审核中 / 已归档」四值内的）
--      ★ 这些值**不要**在展示层映射成别的状态（那会让列表与详情一起说谎），
--        应作为数据修复清单交给业务确认后统一刷。
SELECT a.status, COUNT(1) AS cnt
  FROM t_archive a
 WHERE a.del_flag = 0
   AND a.status NOT IN ('未归档', '归档中', '审核中', '已归档')
 GROUP BY a.status
 ORDER BY cnt DESC;

-- ①-5 已归档但没有任何卷内文件的档案（状态与事实矛盾，属于数据问题）
SELECT a.id, a.archive_no, a.status, a.file_count
  FROM t_archive a
 WHERE a.del_flag = 0
   AND a.status = '已归档'
   AND NOT EXISTS (SELECT 1 FROM t_archive_file f
                    WHERE f.archive_id = a.id AND f.del_flag = 0)
 ORDER BY a.archive_no;

-- ---------------------------------------------------------------------
-- ② 收发文归档链路核对（P1-11 第 4 条）
--    :docId 换成收文 / 发文 ID。
--    三段数字对不上时：attachment > file 说明转换丢；file > 落库说明没写进去。
-- ---------------------------------------------------------------------

-- ②-1 收文：原附件数 vs 档案实际行数
SELECT d.id                       AS docId,
       d.doc_no                   AS docNo,
       d.archive_id               AS archiveId,
       d.status                   AS docStatus,
       (SELECT COUNT(1) FROM t_doc_attachment t
         WHERE t.doc_type = 'receive' AND t.doc_id = d.id AND t.del_flag = 0) AS attachmentCount,
       (SELECT COUNT(1) FROM t_archive_file f
         WHERE f.archive_id = d.archive_id AND f.del_flag = 0)                 AS archiveFileCount,
       (SELECT a.status FROM t_archive a WHERE a.id = d.archive_id)            AS archiveStatus
  FROM t_doc_receive d
 WHERE d.del_flag = 0
   AND d.id = 'DOC-RECEIVE-ID';   -- ← 换成要核对的收文 ID

-- ②-2 发文：同上
SELECT d.id                       AS docId,
       d.doc_no                   AS docNo,
       d.archive_id               AS archiveId,
       (SELECT COUNT(1) FROM t_doc_attachment t
         WHERE t.doc_type = 'send' AND t.doc_id = d.id AND t.del_flag = 0)     AS attachmentCount,
       (SELECT COUNT(1) FROM t_archive_file f
         WHERE f.archive_id = d.archive_id AND f.del_flag = 0)                 AS archiveFileCount,
       (SELECT a.status FROM t_archive a WHERE a.id = d.archive_id)            AS archiveStatus
  FROM t_doc_send d
 WHERE d.del_flag = 0
   AND d.id = 'DOC-SEND-ID';      -- ← 换成要核对的发文 ID

-- ②-3 同一项目是否存在多卷（P2-07「一项目一案卷」复用后的实况）
--      ★ 这里只**列出来**，不做任何去重/删除：
--        历史数据必须由业务确认后才能归并，本次整改只保证「新增归档不再造新卷」。
SELECT COALESCE(NULLIF(a.facility_id, ''), CONCAT('ptxmmc:', a.ptxmmc, '|', a.crzdbh)) AS projectKey,
       COUNT(1)          AS archiveCount,
       GROUP_CONCAT(a.archive_no ORDER BY a.archive_no) AS archiveNos
  FROM t_archive a
 WHERE a.del_flag = 0
 GROUP BY COALESCE(NULLIF(a.facility_id, ''), CONCAT('ptxmmc:', a.ptxmmc, '|', a.crzdbh))
HAVING COUNT(1) > 1
 ORDER BY archiveCount DESC;

-- ---------------------------------------------------------------------
-- ③ 接收管养单位数据质量（P2-08）
--    与 HandoverStatVO.receiveUnitBlank / selectStatByReceiveUnit 的排除口径一致：
--    NULL、空串、「未填写」「否」「无」「-」「—」「/」。
-- ---------------------------------------------------------------------

-- ③-1 未填写数量（这个数字 = 页面新增指标卡的取值）
SELECT COUNT(1) AS receiveUnitBlank
  FROM t_road_handover h
 WHERE h.del_flag = 0
   AND (h.receive_unit IS NULL
        OR TRIM(h.receive_unit) = ''
        OR TRIM(h.receive_unit) IN ('未填写', '否', '无', '-', '—', '/'));

-- ③-2 被排除的占位值都长什么样（用来看有没有漏掉新的占位写法）
SELECT h.receive_unit, COUNT(1) AS cnt
  FROM t_road_handover h
 WHERE h.del_flag = 0
   AND (h.receive_unit IS NULL OR TRIM(h.receive_unit) = ''
        OR TRIM(h.receive_unit) IN ('未填写', '否', '无', '-', '—', '/'))
 GROUP BY h.receive_unit
 ORDER BY cnt DESC;

-- ③-3 有效单位排行（应等于页面「接收管养单位」相关的有效口径；前 10 即原 Top10 口径）
SELECT TRIM(h.receive_unit) AS receiveUnit, COUNT(1) AS cnt
  FROM t_road_handover h
 WHERE h.del_flag = 0
   AND h.receive_unit IS NOT NULL
   AND TRIM(h.receive_unit) NOT IN ('', '未填写', '否', '无', '-', '—', '/')
 GROUP BY TRIM(h.receive_unit)
 ORDER BY cnt DESC, receiveUnit
 LIMIT 10;
