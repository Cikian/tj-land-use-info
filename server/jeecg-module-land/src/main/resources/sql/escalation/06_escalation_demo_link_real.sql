-- ============================================================================
--  提级论证管理 · 演示数据「挂接库内真实业务数据」（可选脚本）
--  目标库：tj-jyxyd
--
--  前置：05_t_escalation_demo_data.sql 已执行（10 条演示项目存在）。
--
--  ★ 为什么单独一个脚本
--    05 脚本为了让「不依赖库内既有数据分布也能执行」，把
--    facility_id / ptxmmc / crzdbh 一律留空。
--    本脚本把其中 3 条演示项目挂到库内**真实存在**的
--    xj_kjkfb_supporting_facilities（配套项目）与 t_land（出让宗地）上，
--    用来验证两件事：
--      1) 录入/保存时的**反查**：按 crzdbh 带出 dkmc / xzqh / ghydxz；
--      2) 保存时的**回写**：facility_id 非空 → 回写 sfzsjtjlz='是'；
--         arg_result ∈ {通过, 基本通过} → 回写 tjlzsftg='是'。
--    ★ 回写动作由**后端应用**在执行保存时完成，本脚本只负责建立关联关系。
--
--  ★ 匹配策略（功能区优先 → 区划 → 尽量不同宗地）
--    实测库内 xj_kjkfb_supporting_facilities.xzqh 里**没有「滨海新区」**，
--    滨海新区的配套用的是功能区值（生态城 / 经开区 / 高新区 / 保税区）。
--    因此本脚本按「项目的 gnq（功能区）优先，其次 xzqh（行政区划）」去匹配，
--    并且**按宗地编号分组**，保证三条挂接落在**不同宗地**上（否则会像
--    「按 id 排序取前 3 条」那样全挤在同一个宗地，看起来很不合理）。
--
--    匹配结果（脚本末尾守卫查询会打印出来核对）：
--      demo-ep-01 滨海新区·高新区 → 高新区第 1 个宗地下的配套项目
--      demo-ep-06 滨海新区·高新区 → 高新区第 2 个宗地下的配套项目
--      demo-ep-03 东丽区           → 东丽区第 1 个宗地下的配套项目
--
--  可重复执行：每次执行都会按同样规则覆盖这 3 条演示项目的关联字段。
--  回滚：执行脚本末尾注释里的「解除关联」UPDATE 即可。
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0) 先看看库内有哪些可挂接的真实数据（供人工判断是否合理）
-- ---------------------------------------------------------------------------
SELECT '配套项目·按区划分布' AS 数据源, `xzqh` AS 区划, COUNT(*) AS 条数, COUNT(DISTINCT `crzdbh`) AS 不同宗地数
FROM `xj_kjkfb_supporting_facilities`
WHERE `xzqh` IN ('高新区', '经开区', '保税区', '生态城', '东丽区', '津南区')
GROUP BY `xzqh`
ORDER BY 条数 DESC;

SELECT '出让宗地（前 5 条）' AS 数据源,
       `id`, `crzdbh` AS 出让宗地编号, `dkmc` AS 地块名称, `xzqh` AS 行政区划, `ghydxz` AS 规划用地性质
FROM `t_land`
WHERE `del_flag` = 0
ORDER BY `crzdbh`
LIMIT 5;

-- ---------------------------------------------------------------------------
-- 1) 挂接 3 条演示项目到真实配套项目（不同宗地、同区/同功能区）
--    派生表用「按 crzdbh 分组 + 取组内最小 id」的方式做到「一个宗地只取一条」，
--    GROUP_CONCAT 取该条的名称（分隔符用 '~~' 以免与名称里的逗号冲突）。
-- ---------------------------------------------------------------------------

-- 1.1 demo-ep-01 滨海新区 · 高新区 → 高新区第 1 个宗地
UPDATE `t_escalation_project` p
JOIN (
    SELECT MIN(`id`) AS `id`,
           `crzdbh`,
           SUBSTRING_INDEX(GROUP_CONCAT(`ptxmmc` ORDER BY `id` SEPARATOR '~~'), '~~', 1) AS `ptxmmc`
      FROM `xj_kjkfb_supporting_facilities`
     WHERE `xzqh` = '高新区'
     GROUP BY `crzdbh`
     ORDER BY `id`
     LIMIT 1
) f
SET p.`facility_id` = f.`id`,
    p.`ptxmmc`      = f.`ptxmmc`,
    p.`crzdbh`      = IFNULL(p.`crzdbh`, f.`crzdbh`)
WHERE p.`id` = 'demo-ep-01';

-- 1.2 demo-ep-06 滨海新区 · 高新区 → 高新区第 2 个宗地（与 1.1 不同宗地）
UPDATE `t_escalation_project` p
JOIN (
    SELECT MIN(`id`) AS `id`,
           `crzdbh`,
           SUBSTRING_INDEX(GROUP_CONCAT(`ptxmmc` ORDER BY `id` SEPARATOR '~~'), '~~', 1) AS `ptxmmc`
      FROM `xj_kjkfb_supporting_facilities`
     WHERE `xzqh` = '高新区'
     GROUP BY `crzdbh`
     ORDER BY `id`
     LIMIT 1 OFFSET 1
) f
SET p.`facility_id` = f.`id`,
    p.`ptxmmc`      = f.`ptxmmc`,
    p.`crzdbh`      = IFNULL(p.`crzdbh`, f.`crzdbh`)
WHERE p.`id` = 'demo-ep-06';

-- 1.3 demo-ep-03 东丽区 → 东丽区第 1 个宗地
UPDATE `t_escalation_project` p
JOIN (
    SELECT MIN(`id`) AS `id`,
           `crzdbh`,
           SUBSTRING_INDEX(GROUP_CONCAT(`ptxmmc` ORDER BY `id` SEPARATOR '~~'), '~~', 1) AS `ptxmmc`
      FROM `xj_kjkfb_supporting_facilities`
     WHERE `xzqh` = '东丽区'
     GROUP BY `crzdbh`
     ORDER BY `id`
     LIMIT 1
) f
SET p.`facility_id` = f.`id`,
    p.`ptxmmc`      = f.`ptxmmc`,
    p.`crzdbh`      = IFNULL(p.`crzdbh`, f.`crzdbh`)
WHERE p.`id` = 'demo-ep-03';

-- ---------------------------------------------------------------------------
-- 2) 用真实宗地补全「关联地块信息」（按 crzdbh 反查，与后端录入时的反查逻辑一致）
--    只补空的字段，不覆盖 05 脚本里已写好的值
-- ---------------------------------------------------------------------------
UPDATE `t_escalation_project` p
JOIN `t_land` l ON l.`crzdbh` = p.`crzdbh` AND l.`del_flag` = 0
SET p.`dkmc`   = IFNULL(p.`dkmc`,   l.`dkmc`),
    p.`xzqh`   = IFNULL(p.`xzqh`,   l.`xzqh`),
    p.`ghydxz` = IFNULL(p.`ghydxz`, l.`ghydxz`)
WHERE p.`id` LIKE 'demo-ep-%';

-- ---------------------------------------------------------------------------
-- 3) 守卫查询
--    3.1 应输出 3 行，且**crzdbh 互不相同**（高新区 2 条不同宗地 + 东丽区 1 条）
-- ---------------------------------------------------------------------------
SELECT p.`id`, p.`project_no`, p.`project_name`, p.`xzqh`, p.`gnq`,
       p.`crzdbh`, p.`ptxmmc`, p.`facility_id`, p.`arg_result`, p.`status`
FROM `t_escalation_project` p
WHERE p.`id` LIKE 'demo-ep-%' AND p.`facility_id` IS NOT NULL
ORDER BY p.`id`;

--    3.2 应输出 3 行且「已挂接宗地数」= 3；若某行 facility_id 为 NULL，
--        说明该区/功能区在库内没有配套项目，需要改匹配条件或忽略该条
SELECT p.`id`, p.`project_name`,
       CASE WHEN p.`facility_id` IS NULL THEN '✗ 未匹配到配套项目' ELSE '✓ 已挂接' END AS 挂接结果,
       p.`ptxmmc`, p.`crzdbh`
FROM `t_escalation_project` p
WHERE p.`id` IN ('demo-ep-01', 'demo-ep-03', 'demo-ep-06')
ORDER BY p.`id`;

--    3.3 挂接到的配套项目当前的提级论证两个字段值
--        （此刻应仍是原值，可能为 NULL 或 '否'；要到后端保存接口执行回写后才变 '是'）
SELECT f.`id` AS 配套项目ID, f.`ptxmmc` AS 配套项目名称, f.`crzdbh` AS 宗地编号,
       f.`sfzsjtjlz` AS 是否涉及提级论证_当前值,
       f.`tjlzsftg`  AS 提级论证是否通过_当前值,
       p.`project_no` AS 关联的演示项目编号
FROM `xj_kjkfb_supporting_facilities` f
JOIN `t_escalation_project` p ON p.`facility_id` = f.`id`
WHERE p.`id` LIKE 'demo-ep-%'
ORDER BY p.`id`;

--    3.4 反查效果：演示项目的地块信息是否从 t_land 补全
SELECT p.`id`, p.`crzdbh`, p.`dkmc`, p.`xzqh`, p.`ghydxz`,
       CASE WHEN l.`id` IS NULL THEN '（库内无此宗地，未反查）' ELSE '已按宗地反查' END AS 反查结果
FROM `t_escalation_project` p
LEFT JOIN `t_land` l ON l.`crzdbh` = p.`crzdbh` AND l.`del_flag` = 0
WHERE p.`id` IN ('demo-ep-01', 'demo-ep-03', 'demo-ep-06')
ORDER BY p.`id`;

-- ---------------------------------------------------------------------------
-- 4) 手工验证回写效果（可选，仅用于不启动后端时看库内变化；★ 会写库）
-- ---------------------------------------------------------------------------
-- UPDATE `xj_kjkfb_supporting_facilities`
--    SET `sfzsjtjlz` = '是'
--  WHERE `id` IN (SELECT `facility_id` FROM `t_escalation_project`
--                 WHERE `id` LIKE 'demo-ep-%' AND `facility_id` IS NOT NULL);
--
-- UPDATE `xj_kjkfb_supporting_facilities`
--    SET `tjlzsftg` = '是'
--  WHERE `id` IN (SELECT `facility_id` FROM `t_escalation_project`
--                 WHERE `id` LIKE 'demo-ep-%' AND `facility_id` IS NOT NULL
--                   AND `arg_result` IN ('通过', '基本通过'));

-- ---------------------------------------------------------------------------
-- 5) 解除关联（重置演示数据，需要时手工执行）
-- ---------------------------------------------------------------------------
-- UPDATE `t_escalation_project`
--    SET `facility_id` = NULL, `ptxmmc` = NULL
--  WHERE `id` LIKE 'demo-ep-%';
