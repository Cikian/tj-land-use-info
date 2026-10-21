-- =============================================================================
--  编号与名称归一：括号 / 连字符 / 空白 / 【城更】标记 / 末尾「号」+ 去重
-- =============================================================================
--  口径（2026-10-20 确认）：
--    1. 半角 ( ) → 中文 （ ）
--    2. 连字符族 _ — – ― ‐ ‑ ‒ − － ﹣ ～ 〜 → 半角 -
--    3. 去掉名称里所有空白字符（含全角空格、NBSP、零宽字符）
--    4. 编号末尾的【城更】/【城市更新】标记摘下来写进备注
--       （t_land.beizhu / t_supporting_facilities.bz）—— 标记不是编号的一部分，
--       留在编号里会让同一个项目因「带不带标记」变成两条
--    5. 编号统一以「号」结尾（宗地、配套两侧都补）—— 编号是
--       t_supporting_facilities.crzdbh = t_land.crzdbh 的关联键，写法不一致就挂不上
--    6. 因「带不带号」造成的重复记录去重（见第 4、6 步）
--
--  ★ 为什么必须刷历史数据：实测（2026-10-20）
--    · 58 条配套挂不上宗地：47 条只差末尾「号」（25 个编号）、11 条宗地不存在（8 个编号）
--    · 12 对宗地、46 条配套是「同一个项目录了两遍」（一遍带号一遍不带）
--    · 819 条宗地编号、1366 条配套编号缺末尾「号」
--    首页「待落实配套」少算、数据管理判重失效，都是这里来的。
--
--  幂等：每一步都只更新「需要改」的行；重复执行结果不变。
--  执行：mysql --default-character-set=utf8mb4 ...（脚本是 UTF-8，别用 latin1 客户端）
--  回滚：第 4、6 步是软删（del_flag=1 / delFlag='1'），恢复只需把标记改回 0；
--        其余步骤是纯文本替换，可用备份表回滚（建议执行前备份两张表）：
--          CREATE TABLE t_land_bak_YYYYMMDD AS SELECT * FROM t_land;
--          CREATE TABLE t_supporting_facilities_bak_YYYYMMDD AS SELECT * FROM t_supporting_facilities;
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 0. 执行前影响面（只读，先看一眼）
-- ---------------------------------------------------------------------------
SELECT '宗地·编号缺号' AS 项, COUNT(*) AS 行数 FROM t_land WHERE crzdbh NOT LIKE '%号'
UNION ALL
SELECT '宗地·编号含【…】标记', COUNT(*) FROM t_land WHERE crzdbh LIKE '%【%】'
UNION ALL
SELECT '宗地·重复对（只差号）', COUNT(*) FROM t_land a
  JOIN t_land b ON b.crzdbh = CONCAT(a.crzdbh, '号') AND b.del_flag = 0 WHERE a.del_flag = 0
UNION ALL
SELECT '配套·编号缺号', COUNT(*) FROM t_supporting_facilities
 WHERE COALESCE(delFlag, '0') = '0' AND crzdbh NOT LIKE '%号'
UNION ALL
SELECT '配套·编号含【…】标记', COUNT(*) FROM t_supporting_facilities
 WHERE COALESCE(delFlag, '0') = '0' AND crzdbh LIKE '%【%】'
UNION ALL
SELECT '配套·挂不上宗地', COUNT(*) FROM t_supporting_facilities f
 WHERE COALESCE(f.delFlag, '0') = '0'
   AND NOT EXISTS (SELECT 1 FROM t_land l WHERE l.crzdbh = f.crzdbh AND l.del_flag = 0);

-- ---------------------------------------------------------------------------
-- 1. 名称类字段归一（括号 / 连字符 / 空白）
--    对每列套同一串 REPLACE：先括号、再连字符族、最后空白。
--    这里不写 WHERE 过滤脏字符（只有 847 + 1433 行），重复执行值不变，省得漏掉某种字符。
--    零宽字符（U+200B/U+FEFF）MySQL 5.7 的 CHAR() 拿不到，由 Java 写入侧兜住（DataSupport.normalizeText）。
-- ---------------------------------------------------------------------------
UPDATE t_land
SET crzdbh = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(crzdbh, '(', '（'), ')', '）'), '_', '-'), '—', '-'), '–', '-'), '―', '-'), '‐', '-'), '‑', '-'), '‒', '-'), '−', '-'), '－', '-'), '﹣', '-'), '～', '-'), '〜', '-'), ' ', ''), '\t', ''), '\n', ''), '\r', ''), '　', ''), CHAR(160), '')
WHERE crzdbh IS NOT NULL;

UPDATE t_land
SET dkmc = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(dkmc, '(', '（'), ')', '）'), '_', '-'), '—', '-'), '–', '-'), '―', '-'), '‐', '-'), '‑', '-'), '‒', '-'), '−', '-'), '－', '-'), '﹣', '-'), '～', '-'), '〜', '-'), ' ', ''), '\t', ''), '\n', ''), '\r', ''), '　', ''), CHAR(160), '')
WHERE dkmc IS NOT NULL AND del_flag = 0;

UPDATE t_land
SET srr = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(srr, '(', '（'), ')', '）'), '_', '-'), '—', '-'), '–', '-'), '―', '-'), '‐', '-'), '‑', '-'), '‒', '-'), '−', '-'), '－', '-'), '﹣', '-'), '～', '-'), '〜', '-'), ' ', ''), '\t', ''), '\n', ''), '\r', ''), '　', ''), CHAR(160), '')
WHERE srr IS NOT NULL AND del_flag = 0;

UPDATE t_land
SET lpmc = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(lpmc, '(', '（'), ')', '）'), '_', '-'), '—', '-'), '–', '-'), '―', '-'), '‐', '-'), '‑', '-'), '‒', '-'), '−', '-'), '－', '-'), '﹣', '-'), '～', '-'), '〜', '-'), ' ', ''), '\t', ''), '\n', ''), '\r', ''), '　', ''), CHAR(160), '')
WHERE lpmc IS NOT NULL AND del_flag = 0;

UPDATE t_supporting_facilities
SET crzdbh = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(crzdbh, '(', '（'), ')', '）'), '_', '-'), '—', '-'), '–', '-'), '―', '-'), '‐', '-'), '‑', '-'), '‒', '-'), '−', '-'), '－', '-'), '﹣', '-'), '～', '-'), '〜', '-'), ' ', ''), '\t', ''), '\n', ''), '\r', ''), '　', ''), CHAR(160), '')
WHERE crzdbh IS NOT NULL;

UPDATE t_supporting_facilities
SET ptxmmc = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(ptxmmc, '(', '（'), ')', '）'), '_', '-'), '—', '-'), '–', '-'), '―', '-'), '‐', '-'), '‑', '-'), '‒', '-'), '−', '-'), '－', '-'), '﹣', '-'), '～', '-'), '〜', '-'), ' ', ''), '\t', ''), '\n', ''), '\r', ''), '　', ''), CHAR(160), '')
WHERE ptxmmc IS NOT NULL AND COALESCE(delFlag, '0') = '0';

UPDATE t_supporting_facilities
SET dkmc = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(dkmc, '(', '（'), ')', '）'), '_', '-'), '—', '-'), '–', '-'), '―', '-'), '‐', '-'), '‑', '-'), '‒', '-'), '−', '-'), '－', '-'), '﹣', '-'), '～', '-'), '〜', '-'), ' ', ''), '\t', ''), '\n', ''), '\r', ''), '　', ''), CHAR(160), '')
WHERE dkmc IS NOT NULL AND COALESCE(delFlag, '0') = '0';

UPDATE t_supporting_facilities
SET jsdw = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(jsdw, '(', '（'), ')', '）'), '_', '-'), '—', '-'), '–', '-'), '―', '-'), '‐', '-'), '‑', '-'), '‒', '-'), '−', '-'), '－', '-'), '﹣', '-'), '～', '-'), '〜', '-'), ' ', ''), '\t', ''), '\n', ''), '\r', ''), '　', ''), CHAR(160), '')
WHERE jsdw IS NOT NULL AND COALESCE(delFlag, '0') = '0';

-- 2. 编号末尾的【城更】/【城市更新】→ 备注
--    ★ SET 子句从左到右求值：先算 beizhu（用旧 crzdbh），再改 crzdbh
-- ---------------------------------------------------------------------------
UPDATE t_land
SET beizhu = CONCAT(COALESCE(beizhu, ''), IF(beizhu IS NULL OR beizhu = '', '', ' '),
                    SUBSTRING(crzdbh, LOCATE('【', crzdbh))),
    crzdbh = SUBSTRING_INDEX(crzdbh, '【', 1)
WHERE del_flag = 0 AND crzdbh LIKE '%【%】';

UPDATE t_supporting_facilities
SET bz = CONCAT(COALESCE(bz, ''), IF(bz IS NULL OR bz = '', '', ' '),
                SUBSTRING(crzdbh, LOCATE('【', crzdbh))),
    crzdbh = SUBSTRING_INDEX(crzdbh, '【', 1)
WHERE COALESCE(delFlag, '0') = '0' AND crzdbh LIKE '%【%】';

-- ---------------------------------------------------------------------------
-- 3. 宗地去重（必须放在「补号」之前：补号后两条编号相同，就分不出该删谁了）
--    同一编号只差末尾「号」的两条，保留带「号」的那条（编号的规范写法），
--    另一条软删（del_flag=1，可在回收站恢复）。
--    实测这 24 条记录上没有任何附件/变更记录，删除不丢数据。
-- ---------------------------------------------------------------------------
UPDATE t_land a
JOIN t_land b ON b.crzdbh = CONCAT(a.crzdbh, '号') AND b.del_flag = 0
LEFT JOIN t_land c ON c.crzdbh = a.crzdbh AND c.del_flag = 1
SET a.del_flag = 1
WHERE a.del_flag = 0 AND c.id IS NULL;

-- ---------------------------------------------------------------------------
-- 4. 编号补「号」（宗地、配套两侧同时补，补完两边才匹配）
--    删除态的行也补：避免从回收站恢复后与带号的活记录撞唯一索引
-- ---------------------------------------------------------------------------
UPDATE t_land SET crzdbh = CONCAT(crzdbh, '号') WHERE crzdbh IS NOT NULL AND crzdbh NOT LIKE '%号';
UPDATE t_supporting_facilities SET crzdbh = CONCAT(crzdbh, '号') WHERE crzdbh IS NOT NULL AND crzdbh NOT LIKE '%号';

-- ---------------------------------------------------------------------------
-- 5. 配套去重（补号后，「X」与「X号」下的同一配套会撞成同一条）
--    同一（编号 + 名称）保留最早录入的一条，其余软删；实测 56 条，
--    这些重复行上没有附件（6 个配套附件都不在重复行上）。
-- ---------------------------------------------------------------------------
UPDATE t_supporting_facilities a
JOIN t_supporting_facilities b
  ON a.crzdbh = b.crzdbh AND a.ptxmmc = b.ptxmmc
 AND (COALESCE(a.createTime, '2999-01-01 00:00:00') > COALESCE(b.createTime, '2999-01-01 00:00:00')
      OR (COALESCE(a.createTime, '2999-01-01 00:00:00') = COALESCE(b.createTime, '2999-01-01 00:00:00')
          AND a.id > b.id))
SET a.delFlag = '1'
WHERE COALESCE(a.delFlag, '0') = '0' AND COALESCE(b.delFlag, '0') = '0';

-- ---------------------------------------------------------------------------
-- 5b. 去重后补回「已完成」状态
--     重复的两条里，被软删的那条如果已竣工、留下的那条还没竣工，
--     说明后录入的一条更新过进度 —— 把竣工标记补到留下的那条上，
--     否则首页「已完成」会少算（实测 4 条）。
-- ---------------------------------------------------------------------------
UPDATE t_supporting_facilities b
JOIN t_supporting_facilities a
  ON a.crzdbh = b.crzdbh AND a.ptxmmc = b.ptxmmc
 AND a.delFlag = '1' AND COALESCE(b.delFlag, '0') = '0'
SET b.sfjg = '是'
WHERE a.sfjg = '是' AND COALESCE(b.sfjg, '') <> '是';

-- ---------------------------------------------------------------------------
-- 6. 附件可读键 biz_key 里的编号同步补「号」（展示用，不影响匹配）
--    biz_key 形如「地块名称（编号）」，把括号里的旧编号替换成新编号
-- ---------------------------------------------------------------------------
UPDATE t_land_attachment a
JOIN t_land l ON l.id = a.biz_id
SET a.biz_key = REPLACE(a.biz_key, TRIM(TRAILING '号' FROM l.crzdbh), l.crzdbh)
WHERE a.biz_type = 'land' AND a.del_flag = 0
  AND CHAR_LENGTH(TRIM(TRAILING '号' FROM l.crzdbh)) >= 6
  AND a.biz_key LIKE CONCAT('%', TRIM(TRAILING '号' FROM l.crzdbh), '%')
  AND a.biz_key NOT LIKE CONCAT('%', l.crzdbh, '%');

UPDATE t_land_attachment a
JOIN t_supporting_facilities f ON f.id = a.biz_id
SET a.biz_key = REPLACE(a.biz_key, TRIM(TRAILING '号' FROM f.crzdbh), f.crzdbh)
WHERE a.biz_type = 'facility' AND a.del_flag = 0
  AND CHAR_LENGTH(TRIM(TRAILING '号' FROM f.crzdbh)) >= 6
  AND a.biz_key LIKE CONCAT('%', TRIM(TRAILING '号' FROM f.crzdbh), '%')
  AND a.biz_key NOT LIKE CONCAT('%', f.crzdbh, '%');

-- ---------------------------------------------------------------------------
-- 7. 核对：前三项应为 0
-- ---------------------------------------------------------------------------
SELECT '宗地·编号缺号' AS 项, COUNT(*) AS 行数 FROM t_land WHERE crzdbh NOT LIKE '%号'
UNION ALL
SELECT '宗地·编号含【…】标记', COUNT(*) FROM t_land WHERE crzdbh LIKE '%【%】'
UNION ALL
SELECT '配套·编号缺号', COUNT(*) FROM t_supporting_facilities
 WHERE COALESCE(delFlag, '0') = '0' AND crzdbh NOT LIKE '%号'
UNION ALL
SELECT '宗地·重复对（只差号）', COUNT(*) FROM t_land a
  JOIN t_land b ON b.crzdbh = CONCAT(a.crzdbh, '号') AND b.del_flag = 0 WHERE a.del_flag = 0
UNION ALL
SELECT '配套·重复组（同编号+同名称）', COUNT(*) FROM t_supporting_facilities a
  JOIN t_supporting_facilities b ON a.crzdbh = b.crzdbh AND a.ptxmmc = b.ptxmmc AND a.id <> b.id
 WHERE COALESCE(a.delFlag, '0') = '0' AND COALESCE(b.delFlag, '0') = '0'
UNION ALL
SELECT '配套·仍挂不上宗地', COUNT(*) FROM t_supporting_facilities f
 WHERE COALESCE(f.delFlag, '0') = '0'
   AND NOT EXISTS (SELECT 1 FROM t_land l WHERE l.crzdbh = f.crzdbh AND l.del_flag = 0);
