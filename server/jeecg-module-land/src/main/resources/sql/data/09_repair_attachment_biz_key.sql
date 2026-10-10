-- =============================================================================
-- 修复历史附件记录里 biz_key 为空的「归属信息」
-- =============================================================================
-- 背景（2026-10-10）：
--   在「配套附件管理」里直接点「上传附件」，在弹窗下拉里现挑一个归属对象再上传时，
--   列表里那条附件**不显示项目信息**（「文件名 / 归属」的副行是空的）。
--
-- 根因：
--   biz_key 这一列以前完全依赖前端提交；用户不是从配套详情跳转过来时（没有预置
--   bizKey），前端一旦没带上，落库就是 NULL。代码侧已修（服务端在 bizKey 为空时
--   用 bizId 反查业务对象兜底），本脚本负责**把已经产生的空值补回来**。
--
-- 影响范围：
--   只影响「展示」。附件本身、磁盘文件、下载/预览/删除全部正常；
--   检索里的「所属对象」是 LIKE biz_key，所以这些记录在此之前搜不到。
--
-- ★ 格式统一为「名称（编号）」——与前端下拉选项、以及服务端
--   LandAttachmentServiceImpl#withCode 的口径必须完全一致，
--   否则同一列会出现两种写法，用户看起来像两套数据。
--   名称缺失 → 只留编号；编号缺失 → 只留名称。
--
-- 幂等：只更新 biz_key 为 NULL 或空白字符的记录，可重复执行。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 0. 先看看有多少条需要修（执行前跑一次，确认影响面）
-- ---------------------------------------------------------------------------
SELECT a.biz_type,
       COUNT(*) AS blank_count
FROM t_land_attachment a
WHERE a.del_flag = 0
  AND (a.biz_key IS NULL OR TRIM(a.biz_key) = '')
GROUP BY a.biz_type;

-- ---------------------------------------------------------------------------
-- 1. 宗地（land）：编号 + 全角空格 + 地块名称
-- ---------------------------------------------------------------------------
UPDATE t_land_attachment a
JOIN t_land l ON l.id = a.biz_id
SET a.biz_key = TRIM(CONCAT(COALESCE(l.crzdbh, ''), '　', COALESCE(l.dkmc, '')))
WHERE a.del_flag = 0
  AND a.biz_type = 'land'
  AND (a.biz_key IS NULL OR TRIM(a.biz_key) = '');

-- ---------------------------------------------------------------------------
-- 2. 配套项目（facility）：项目名称（宗地编号）
--    ★ t_supporting_facilities 的字段是驼峰 ptxmmc / crzdbh，软删列也是驼峰 delFlag
-- ---------------------------------------------------------------------------
UPDATE t_land_attachment a
JOIN t_supporting_facilities f ON f.id = a.biz_id
SET a.biz_key = TRIM(CONCAT(
        COALESCE(f.ptxmmc, ''),
        CASE WHEN f.ptxmmc IS NULL OR TRIM(f.ptxmmc) = ''
             THEN '' ELSE '（' END,
        CASE WHEN f.ptxmmc IS NULL OR TRIM(f.ptxmmc) = ''
             THEN COALESCE(f.crzdbh, '')
             ELSE CONCAT(COALESCE(f.crzdbh, ''), '）') END
    ))
WHERE a.del_flag = 0
  AND a.biz_type = 'facility'
  AND (a.biz_key IS NULL OR TRIM(a.biz_key) = '')
  AND COALESCE(f.delFlag, '0') = '0';

-- ---------------------------------------------------------------------------
-- 3. 环节进度（process）：环节名（宗地编号）
-- ---------------------------------------------------------------------------
UPDATE t_land_attachment a
JOIN t_facility_process p ON p.id = a.biz_id
SET a.biz_key = TRIM(CONCAT(
        COALESCE(p.lc_name, ''),
        CASE WHEN p.lc_name IS NULL OR TRIM(p.lc_name) = ''
             THEN '' ELSE '（' END,
        CASE WHEN p.lc_name IS NULL OR TRIM(p.lc_name) = ''
             THEN COALESCE(p.crzdbh, '')
             ELSE CONCAT(COALESCE(p.crzdbh, ''), '）') END
    ))
WHERE a.del_flag = 0
  AND a.biz_type = 'process'
  AND (a.biz_key IS NULL OR TRIM(a.biz_key) = '');

-- ---------------------------------------------------------------------------
-- 4. 核对：理论上应只剩「归属对象已被删除 / 关联不上」的记录
--    （这类补不了也不该补 —— 业务对象都没了，名称无从谈起）
-- ---------------------------------------------------------------------------
SELECT a.id, a.biz_type, a.biz_id, a.file_name,
       '归属对象已不存在或已移除，无法补名' AS reason
FROM t_land_attachment a
LEFT JOIN t_land l               ON a.biz_type = 'land'     AND l.id = a.biz_id
LEFT JOIN t_supporting_facilities f ON a.biz_type = 'facility' AND f.id = a.biz_id
LEFT JOIN t_facility_process p   ON a.biz_type = 'process'  AND p.id = a.biz_id
WHERE a.del_flag = 0
  AND (a.biz_key IS NULL OR TRIM(a.biz_key) = '')
  AND (
        (a.biz_type = 'land'     AND l.id IS NULL)
     OR (a.biz_type = 'facility' AND (f.id IS NULL OR COALESCE(f.delFlag, '0') = '1'))
     OR (a.biz_type = 'process'  AND p.id IS NULL)
  );

-- 剩余仍为空、且业务对象存在的记录应为 0 行
SELECT COUNT(*) AS still_blank
FROM t_land_attachment a
WHERE a.del_flag = 0
  AND (a.biz_key IS NULL OR TRIM(a.biz_key) = '');
