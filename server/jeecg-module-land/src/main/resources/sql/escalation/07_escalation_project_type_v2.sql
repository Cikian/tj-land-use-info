-- =============================================================================
-- 提级论证管理 · 项目类型口径变更（P2-10）· 字典原地更新 + 存量数据置「待确认」
-- 目标库：tj-jyxyd（JeecgBoot 3.4.3 单体版）
--
-- 【背景（清单 P2-10）】
--   测试反馈：项目类型应为「市级项目 / 区级项目」，而库里是
--   「产业类 / 基础设施类 / 民生类 / 其他」。
--
-- 【为什么不是「四类 → 市/区级」的映射】
--   这两套不是同一个分类维度：前者是项目属性（产业/基础设施/民生），
--   后者是审批层级（市级/区级）。把它们硬映射过去，等于替业务编造结论 ——
--   一条「产业类」项目到底归市还是归区，只有经办人知道。
--   因此本脚本**不做任何猜测映射**：存量值一律置为过渡值「待确认」，
--   再按第 3 节的人工确认清单逐条改写。
--
-- 【三值关系】
--   市级项目 / 区级项目 —— 新口径，录入与筛选只用这两个；
--   待确认             —— 历史过渡值，不是业务分类，只出现在迁移后的存量行上。
--
-- 【幂等（可重复执行）】
--   · 字典项：按固定 id 原地 UPDATE（值已是新值则 0 行受影响），
--     缺项用「不存在才插入」补，**不 DELETE + INSERT**（换 id 会让页面上的字典缓存与
--     引用该 id 的数据对不上）；
--   · 存量数据：UPDATE 带 `NOT EXISTS (SELECT 1 FROM t_data_migration_marker WHERE code=...)`，
--     第二次执行时标记已在，整段不再改动（连 update_time 都不会被刷）。
--
-- 【执行顺序】
--   1) 直接执行本脚本（第 1 节的清单查询必须在 UPDATE 之前看，见下）；
--   2) 重启应用或重新登录，刷掉前端字典缓存（sys_dict_item 是缓存读的）。
--
-- 【回滚】
--   本项目不做数据回滚：存量值已统一成「待确认」，没有保留原值字段。
--   如需撤销字典，重跑 03_escalation_dict.sql 可恢复旧四项（但那会让界面回到旧口径）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0) 迁移台账表（与 data/15、data/16 同一张；已存在则跳过）
--    ★ 一张表、一个 code 一次迁移，避免「跑了两遍」把已经人工确认过的行又刷回「待确认」。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_data_migration_marker` (
  `code`         varchar(64)  NOT NULL COMMENT '迁移批次码（同一 code 只执行一次）',
  `applied_time` datetime     NOT NULL COMMENT '执行时间',
  `remark`       varchar(500)     NULL COMMENT '执行结果摘要',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据迁移台账（幂等标记：同一 code 只应执行一次）';

-- ---------------------------------------------------------------------------
-- 1) 迁移前留证据（★ 这两条必须在 UPDATE 之前看：
--    UPDATE 之后 project_type 的原值就没了，事后无法再生成「原值」列）
-- ---------------------------------------------------------------------------

-- 1.1 迁移前分组计数：线上实测应为 基础设施类 6 / 民生类 3 / 产业类 2 / (NULL) 1，合计 12
SELECT COALESCE(`project_type`, '(NULL)') AS 项目类型原值,
       COUNT(*)                           AS 条数,
       SUM(CASE WHEN `del_flag` = 0 THEN 1 ELSE 0 END) AS 未删除条数
FROM `t_escalation_project`
GROUP BY `project_type`
ORDER BY 条数 DESC;

-- 1.2 ★ 12 行人工确认清单（原值 + 项目名 + id）。
--     请**先复制这份结果**再执行后半段：迁移后原值即丢失，这是唯一一次能看到原值的机会。
--     清单交给业务逐条确认是「市级项目」还是「区级项目」，确认后按 id 单独 UPDATE。
SELECT `id`           AS 项目ID,
       `project_no`   AS 项目编号,
       `project_name` AS 项目名称,
       `declare_dept` AS 申报单位,
       COALESCE(`project_type`, '(NULL)') AS 项目类型原值,
       `del_flag`     AS 是否已删除
FROM `t_escalation_project`
WHERE `project_type` IN ('产业类', '基础设施类', '民生类', '其他')
   OR `project_type` IS NULL
   OR `project_type` = ''
ORDER BY COALESCE(`project_type`, '(NULL)'), `project_no`;

-- 1.3 条数核对：应输出「实际 12 / 预期 12」一行。不一致先停下问清楚，别盲跑
SELECT COUNT(*) AS 实际待迁移行数, 12 AS 预期行数
FROM `t_escalation_project`
WHERE `project_type` IN ('产业类', '基础设施类', '民生类', '其他')
   OR `project_type` IS NULL
   OR `project_type` = '';

-- ---------------------------------------------------------------------------
-- 2) 字典 land_escalation_project_type 原地更新为三值
--    id 保持不变（e01 = 字典，f01/f02/f03 = 三项；f04「其他」停用而不是删除）
-- ---------------------------------------------------------------------------

-- 2.0 「不存在才插入」的小过程（等价于 INSERT ... WHERE NOT EXISTS）。
--     ★ 为什么不用一条 INSERT ... SELECT ... WHERE NOT EXISTS：
--       MySQL 对「写某张表、又在同语句的子查询里读它」有限制（UPDATE 报 1093，
--       临时表场景报 1137，跨版本表现不一）；先 COUNT 再单行 INSERT 最稳，
--       与 data/16_attach_type_dict_sync.sql 的 sp_ins_dict_item 同一写法。
DROP PROCEDURE IF EXISTS `sp_esc_ptype_ins_item`;

DELIMITER $$
CREATE PROCEDURE `sp_esc_ptype_ins_item`(IN p_id      VARCHAR(32),
                                         IN p_dict_id VARCHAR(32),
                                         IN p_value   VARCHAR(100),
                                         IN p_text    VARCHAR(100),
                                         IN p_desc    VARCHAR(255),
                                         IN p_sort    INT)
BEGIN
  -- p_dict_id 为空 = 字典本体不存在，此时插入字典项只会造出孤儿行，直接跳过
  IF p_dict_id IS NOT NULL
     AND (SELECT COUNT(*) FROM `sys_dict_item`
           WHERE `dict_id` = p_dict_id AND `item_value` = p_value) = 0 THEN
    INSERT INTO `sys_dict_item`
      (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`,
       `create_by`, `create_time`, `update_by`, `update_time`)
    VALUES (p_id, p_dict_id, p_text, p_value, p_desc, p_sort, 1, 'admin', NOW(), NULL, NULL);
  END IF;
END$$
DELIMITER ;

-- 2.1 字典说明文字（原来写的是四类取值）
UPDATE `sys_dict`
   SET `description` = '提级论证项目类型（P2-10 口径：市级项目 / 区级项目 / 待确认；待确认为历史存量过渡值，不是业务分类）',
       `update_time` = NOW()
 WHERE `dict_code` = 'land_escalation_project_type'
   AND (`description` IS NULL OR `description` NOT LIKE '%市级项目%');

-- 2.2 三项原地改名（按固定 id；新库已是新值 → 0 行受影响，可重复执行）
UPDATE `sys_dict_item`
   SET `item_text`   = '市级项目',
       `item_value`  = '市级项目',
       `description` = '市级项目（审批层级：市级）',
       `sort_order`  = 1,
       `status`      = 1,
       `update_time` = NOW()
 WHERE `id` = 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4f01'
   AND (`item_value` <> '市级项目' OR `item_text` <> '市级项目' OR `sort_order` <> 1 OR `status` <> 1);

UPDATE `sys_dict_item`
   SET `item_text`   = '区级项目',
       `item_value`  = '区级项目',
       `description` = '区级项目（审批层级：区级）',
       `sort_order`  = 2,
       `status`      = 1,
       `update_time` = NOW()
 WHERE `id` = 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4f02'
   AND (`item_value` <> '区级项目' OR `item_text` <> '区级项目' OR `sort_order` <> 2 OR `status` <> 1);

UPDATE `sys_dict_item`
   SET `item_text`   = '待确认',
       `item_value`  = '待确认',
       `description` = '历史存量过渡值：原「产业类/基础设施类/民生类/其他」待业务确认归属',
       `sort_order`  = 3,
       `status`      = 1,
       `update_time` = NOW()
 WHERE `id` = 'e5c1d2e3f4a54b6c8d9e0f1a2b3c4f03'
   AND (`item_value` <> '待确认' OR `item_text` <> '待确认' OR `sort_order` <> 3 OR `status` <> 1);

-- 2.3 残留的第 4 项「其他」停用（★ 不删除：若线上有人手工建过同值项、或引用了该 id，
--     删除会连带失去溯源信息；字典查询只取 status = 1，停用即可让它不再出现在候选里）
UPDATE `sys_dict_item` i
  JOIN `sys_dict` d ON d.`id` = i.`dict_id`
   SET i.`status` = 0, i.`update_time` = NOW()
 WHERE d.`dict_code` = 'land_escalation_project_type'
   AND i.`status` = 1
   AND i.`item_value` IN ('产业类', '基础设施类', '民生类', '其他');

-- 2.4 兜底补插三项（环境里 id 与上面不同、或某项被误删时靠它补齐）
SET @v_esc_ptype_dict_id = (SELECT `id` FROM `sys_dict`
                             WHERE `dict_code` = 'land_escalation_project_type' LIMIT 1);

CALL `sp_esc_ptype_ins_item`('e5c1d2e3f4a54b6c8d9e0f1a2b3c4f01', @v_esc_ptype_dict_id,
     '市级项目', '市级项目', '市级项目（审批层级：市级）', 1);
CALL `sp_esc_ptype_ins_item`('e5c1d2e3f4a54b6c8d9e0f1a2b3c4f02', @v_esc_ptype_dict_id,
     '区级项目', '区级项目', '区级项目（审批层级：区级）', 2);
CALL `sp_esc_ptype_ins_item`('e5c1d2e3f4a54b6c8d9e0f1a2b3c4f03', @v_esc_ptype_dict_id,
     '待确认', '待确认', '历史存量过渡值：原「产业类/基础设施类/民生类/其他」待业务确认归属', 3);

DROP PROCEDURE IF EXISTS `sp_esc_ptype_ins_item`;

-- ---------------------------------------------------------------------------
-- 3) 存量数据：旧四类 + 空值 → 「待确认」
--    ★ 只写「待确认」，不做任何市/区级猜测映射（理由见文件头）。
--    ★ 含 del_flag = 1 的行一起改：软删行同样可能被回收站恢复，
--      留着旧值会让「旧值清零」核对永远不干净。
--    ★ 不动 update_by（那是业务操作人），只记 update_time。
-- ---------------------------------------------------------------------------
UPDATE `t_escalation_project`
   SET `project_type` = '待确认',
       `update_time`  = NOW()
 WHERE (`project_type` IN ('产业类', '基础设施类', '民生类', '其他')
        OR `project_type` IS NULL
        OR `project_type` = '')
   AND NOT EXISTS (SELECT 1 FROM `t_data_migration_marker`
                    WHERE `code` = 'ESCALATION_PROJECT_TYPE_V2');

-- 3.1 登记迁移标记（code 是主键 → 已登记时 ON DUPLICATE 走空更新，等于整段跳过）
INSERT INTO `t_data_migration_marker` (`code`, `applied_time`, `remark`)
VALUES ('ESCALATION_PROJECT_TYPE_V2',
        NOW(),
        CONCAT('项目类型口径改为市级/区级，存量旧四类+空值统一置「待确认」，待业务逐条确认；',
               '迁移后「待确认」行数=', (SELECT COUNT(*) FROM `t_escalation_project` WHERE `project_type` = '待确认')))
ON DUPLICATE KEY UPDATE `code` = `code`;

-- ---------------------------------------------------------------------------
-- 4) 迁移后核对（四条，全部对照 1.1 / 1.2 的结果看）
-- ---------------------------------------------------------------------------

-- 4.1 迁移后分组计数：应只剩「待确认 12」，且不再出现旧四类与 (NULL)
SELECT COALESCE(`project_type`, '(NULL)') AS 项目类型, COUNT(*) AS 条数
FROM `t_escalation_project`
GROUP BY `project_type`
ORDER BY 条数 DESC;

-- 4.2 ★ 旧值清零核对：应输出 0 行（任意一行都说明还有没迁移的记录）
SELECT `id` AS 项目ID, `project_no` AS 项目编号, `project_name` AS 项目名称,
       `project_type` AS 项目类型
FROM `t_escalation_project`
WHERE `project_type` IN ('产业类', '基础设施类', '民生类', '其他')
   OR `project_type` IS NULL
   OR `project_type` = '';

-- 4.3 字典核对：应 3 行（市级项目 1 / 区级项目 2 / 待确认 3，状态均为 1）
SELECT i.`id`, i.`item_value` AS 值, i.`item_text` AS 文本,
       i.`sort_order` AS 排序, i.`status` AS 状态
FROM `sys_dict` d
JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` = 'land_escalation_project_type'
ORDER BY i.`status` DESC, i.`sort_order`;

-- 4.4 迁移台账
SELECT `code`, `applied_time`, `remark` FROM `t_data_migration_marker`
WHERE `code` = 'ESCALATION_PROJECT_TYPE_V2';

-- ---------------------------------------------------------------------------
-- 5) 业务确认后的改名模板（人工确认完再逐条执行，本脚本不自动跑）
--    ★ 一次只改一行，改完再看 4.1 的计数是否符合预期。
-- ---------------------------------------------------------------------------
-- UPDATE `t_escalation_project` SET `project_type` = '市级项目', `update_time` = NOW()
--  WHERE `id` = '<从 1.2 清单里挑出的 id>';
-- UPDATE `t_escalation_project` SET `project_type` = '区级项目', `update_time` = NOW()
--  WHERE `id` = '<从 1.2 清单里挑出的 id>';
