-- =============================================================================
--  把用户录入名称里的半角括号统一成中文括号
-- =============================================================================
--  口径（2026-10-10 确认）：
--    用户录入与展示的名称类内容（宗地编号、地块名称、配套项目名称、类型名等）
--    一律使用中文括号「（）」。
--
--  ★ 为什么必须刷历史数据，而不只在写入侧归一：
--    半角 () 与中文 （） 是**不同的字符**。写入侧归一只能保证「此后不再产生两种写法」，
--    但已经存在的半角记录会与新录入的中文写法**不相等**，于是：
--      · 判重失效 → 同一宗地/配套出现两条记录
--      · 批量导入的「宗地必须已存在」校验会误报「宗地不存在」
--      · 附件/环节挂在两条记录的 id 上，统计翻倍
--    实测：847 条宗地编号里 846 条用中文括号，2 条用了半角。
--    旧系统存储目录名（津北辰仓（挂）2018-015、义安路（永顺道-永尚道））本来就全用
--    中文括号，所以这次刷新是「向旧系统口径对齐」。
--
--  注意：只替换括号字符，其余字符（含中间空格、大小写）一律不动。
--        编号的书写习惯归用户，只统一「看起来一样却是不同字符」的那一处。
--
--  幂等：只更新含半角括号的行；重复执行不再匹配。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 0. 先看影响面（执行前跑一次）
-- ---------------------------------------------------------------------------
SELECT 't_land.crzdbh'      AS 字段, COUNT(*) AS 待修 FROM t_land
 WHERE crzdbh LIKE '%(%' OR crzdbh LIKE '%)%'
UNION ALL
SELECT 't_land.dkmc',        COUNT(*) FROM t_land
 WHERE (dkmc LIKE '%(%' OR dkmc LIKE '%)%') AND del_flag = 0
UNION ALL
SELECT 't_supporting_facilities.ptxmmc', COUNT(*) FROM t_supporting_facilities
 WHERE ptxmmc LIKE '%(%' OR ptxmmc LIKE '%)%'
UNION ALL
SELECT 't_supporting_facilities.crzdbh', COUNT(*) FROM t_supporting_facilities
 WHERE crzdbh LIKE '%(%' OR crzdbh LIKE '%)%';

-- ---------------------------------------------------------------------------
-- 1. 宗地编号
-- ---------------------------------------------------------------------------
UPDATE t_land
SET crzdbh = REPLACE(REPLACE(crzdbh, '(', '（'), ')', '）')
WHERE crzdbh LIKE '%(%' OR crzdbh LIKE '%)%';

-- ---------------------------------------------------------------------------
-- 2. 宗地的地块名称 / 受让人 / 楼盘名称
-- ---------------------------------------------------------------------------
UPDATE t_land
SET dkmc = REPLACE(REPLACE(dkmc, '(', '（'), ')', '）')
WHERE (dkmc LIKE '%(%' OR dkmc LIKE '%)%') AND del_flag = 0;

UPDATE t_land
SET srr = REPLACE(REPLACE(srr, '(', '（'), ')', '）')
WHERE (srr LIKE '%(%' OR srr LIKE '%)%') AND del_flag = 0;

UPDATE t_land
SET lpmc = REPLACE(REPLACE(lpmc, '(', '（'), ')', '）')
WHERE (lpmc LIKE '%(%' OR lpmc LIKE '%)%') AND del_flag = 0;

-- ---------------------------------------------------------------------------
-- 3. 配套项目：名称 / 宗地编号 / 地块名称 / 建设单位
--    ★ 本表字段是驼峰、软删列也是驼峰 delFlag（见 Facility 实体注释）
-- ---------------------------------------------------------------------------
UPDATE t_supporting_facilities
SET ptxmmc = REPLACE(REPLACE(ptxmmc, '(', '（'), ')', '）')
WHERE ptxmmc LIKE '%(%' OR ptxmmc LIKE '%)%';

UPDATE t_supporting_facilities
SET crzdbh = REPLACE(REPLACE(crzdbh, '(', '（'), ')', '）')
WHERE crzdbh LIKE '%(%' OR crzdbh LIKE '%)%';

UPDATE t_supporting_facilities
SET dkmc = REPLACE(REPLACE(dkmc, '(', '（'), ')', '）')
WHERE dkmc LIKE '%(%' OR dkmc LIKE '%)%';

UPDATE t_supporting_facilities
SET jsdw = REPLACE(REPLACE(jsdw, '(', '（'), ')', '）')
WHERE jsdw LIKE '%(%' OR jsdw LIKE '%)%';

-- ---------------------------------------------------------------------------
-- 4. 附件的归属可读键（biz_key）与文件名：同样展示给用户看，一并归一
--    ★ 文件名里的括号只影响展示，不影响任何匹配；归一让它与其它地方一致
-- ---------------------------------------------------------------------------
UPDATE t_land_attachment
SET biz_key = REPLACE(REPLACE(biz_key, '(', '（'), ')', '）')
WHERE biz_key IS NOT NULL AND (biz_key LIKE '%(%' OR biz_key LIKE '%)%');

-- ---------------------------------------------------------------------------
-- 5. 核对：应全部为 0
-- ---------------------------------------------------------------------------
SELECT 't_land.crzdbh' AS 字段, COUNT(*) AS 仍含半角 FROM t_land
 WHERE crzdbh LIKE '%(%' OR crzdbh LIKE '%)%'
UNION ALL
SELECT 't_supporting_facilities.ptxmmc', COUNT(*) FROM t_supporting_facilities
 WHERE ptxmmc LIKE '%(%' OR ptxmmc LIKE '%)%'
UNION ALL
SELECT 't_land_attachment.biz_key', COUNT(*) FROM t_land_attachment
 WHERE biz_key LIKE '%(%' OR biz_key LIKE '%)%';
