-- =============================================================================
--  附件材料类型字典同步（P1-08）：让**已有库**的两个字典与本报告图 3 一致
-- =============================================================================
--  为什么单独一个脚本、而不是改 01_data_dict.sql 就够：
--    01_data_dict.sql 是「新库初始化」，它开头会 DELETE 掉字典项再 INSERT。
--    对已经跑过的生产库，那条路会**换掉字典项 id**，而 id 可能被别处引用
--    （导出模板、报表参数、数据权限配置都可能存了 item id）。
--    所以已有库走本脚本：**按 item_value 原地 UPDATE，保留 id**；
--    只在「这一项不存在」时 INSERT（逐项先 COUNT 再单行插入，见 2.4）。
--
--  两个字典的目标状态（与 01_data_dict.sql / LandAttachmentServiceImpl 逐字一致）：
--    land_attach_type（宗地，4 项）
--      01 土地整理计划 / 02 配套情况函 / 03 配套筹备函 / 04 出让宗地图形数据（shp）
--      ★ 原 02 配套方案与 05 出让宗地图形数据（SHP）两项被回收：
--        「配套方案」的内容归配套侧 07 专业配套方案（宗地侧不再有该类别）；
--        出让宗地图形数据只是编码从 05 → 04、名字统一为小写 shp。
--    land_facility_attach_type（配套，13 项）
--      01 配套项目核定用地与地籍调查 / 02 项建批复 / 03 可研批复 / 04 初设及概算批复 /
--      05 道路规划 / 06 规划工程许可 / 07 专业配套方案 / 08 专业管理意见 /
--      09 规划用地许可与划拨手续办理 / 10 施工许可 / 11 不动产登记 /
--      12 竣工文件 / 13 移交文件
--
--  ★ 编码整体位移（例如「项建批复文件 01」→「项建批复 02」）是一次**置换**，
--    与附件表的搬迁（15_attachment_type_remap.sql）用同一套映射，
--    两个脚本必须**同时生效**：只改字典不改数据 = 历史附件挂错类别；
--    只改数据不改字典 = 目录树上的名字对不上。
--
--  幂等：台账表 t_data_migration_marker 已登记 ATTACH_TYPE_DICT_V2 就整段跳过；
--        落在里面的 UPDATE/INSERT 本身也都是「设成目标值」，重复执行结果相同。
--  执行顺序：15_attachment_type_remap.sql → 本脚本 → 重启应用 / 前端重新登录刷字典缓存。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0) 台账表（与 15 脚本共用；若 15 还没跑，这里建出来）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_data_migration_marker` (
  `code`         varchar(64)  NOT NULL COMMENT '迁移批次码（同一 code 只执行一次）',
  `applied_time` datetime     NOT NULL COMMENT '执行时间',
  `remark`       varchar(500)     NULL COMMENT '执行结果摘要',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据迁移台账（幂等标记：同一 code 只应执行一次）';

-- ---------------------------------------------------------------------------
-- 1) 同步前核对：两个字典当前的样子（先留证据）
-- ---------------------------------------------------------------------------
SELECT d.`dict_code`, i.`id`, i.`item_value` AS 编码, i.`item_text` AS 名称, i.`sort_order` AS 排序
FROM `sys_dict` d
JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_attach_type', 'land_facility_attach_type')
ORDER BY d.`dict_code`, i.`sort_order`;

-- ---------------------------------------------------------------------------
-- 2) 同步（单事务：字典项全部成功或全部不动）
-- ---------------------------------------------------------------------------

-- 2.0 辅助过程：字典项「不存在才插入」。
--     ★ 为什么要有它：MySQL 的 INSERT ... SELECT 不能在子查询里再查目标表
--       （ERROR 1093），而补插必须先判断该项是否已存在。做成「先 COUNT 再单行 INSERT」
--       的小过程，一处实现、17 个字典项共用，也不会因为顺序问题漏项。
DROP PROCEDURE IF EXISTS `sp_ins_dict_item`;

DELIMITER $$
CREATE PROCEDURE `sp_ins_dict_item`(IN p_id      VARCHAR(32),
                                    IN p_dict_id VARCHAR(32),
                                    IN p_value   VARCHAR(100),
                                    IN p_text    VARCHAR(100),
                                    IN p_desc    VARCHAR(255),
                                    IN p_sort    INT)
BEGIN
  IF (SELECT COUNT(*) FROM `sys_dict_item`
       WHERE `dict_id` = p_dict_id AND `item_value` = p_value) = 0 THEN
    INSERT INTO `sys_dict_item`
      (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`,
       `create_by`, `create_time`, `update_by`, `update_time`)
    VALUES (p_id, p_dict_id, p_text, p_value, p_desc, p_sort, 1, 'admin', NOW(), NULL, NULL);
  END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS `sp_att_type_dict_v2`;

DELIMITER $$
CREATE PROCEDURE `sp_att_type_dict_v2`()
BEGIN
  DECLARE v_done     INT DEFAULT 0;
  DECLARE v_att_done INT DEFAULT 0;
  DECLARE v_land_id  VARCHAR(32) DEFAULT NULL;
  DECLARE v_fac_id   VARCHAR(32) DEFAULT NULL;
  DECLARE v_land_old INT DEFAULT 0;
  DECLARE v_fac_old  INT DEFAULT 0;
  DECLARE v_rows     INT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  SELECT COUNT(*) INTO v_done FROM `t_data_migration_marker`
   WHERE `code` = 'ATTACH_TYPE_DICT_V2';

  -- ★ 顺序闸门：必须先跑 15_attachment_type_remap.sql。
  --   为什么必须拦住：字典一改成新口径，15 的防误跑闸门就会判定「这是新库、无需搬移」，
  --   于是历史附件的旧码**永远搬不动**了（数据挂在错的类别下，而字典看起来完全正确 ——
  --   这种状态最难查）。宁可在这里报错停下，也不能让它悄悄错下去。
  SELECT COUNT(*) INTO v_att_done FROM `t_data_migration_marker`
   WHERE `code` IN ('ATTACH_TYPE_V2_LAND', 'ATTACH_TYPE_V2_FACILITY');

  IF v_att_done < 2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '请先执行 15_attachment_type_remap.sql（附件数据未迁移就改字典，会让历史附件永久挂错类别）';
  END IF;

  IF v_done = 0 THEN
    SELECT `id` INTO v_land_id FROM `sys_dict` WHERE `dict_code` = 'land_attach_type' LIMIT 1;
    SELECT `id` INTO v_fac_id  FROM `sys_dict` WHERE `dict_code` = 'land_facility_attach_type' LIMIT 1;

    -- ★ 防误跑闸门：只有「还是旧口径」的字典才需要位移。
    --   新库（用改造后的 01_data_dict.sql 初始化）里这两个特征项都不存在，
    --   此时若还照旧映射搬一遍，会把新口径的 03 搬成 02 —— 字典直接被改坏。
    IF v_land_id IS NOT NULL THEN
      SELECT COUNT(*) INTO v_land_old FROM `sys_dict_item`
       WHERE `dict_id` = v_land_id AND `item_value` = '02' AND `item_text` = '配套方案';
    END IF;
    IF v_fac_id IS NOT NULL THEN
      SELECT COUNT(*) INTO v_fac_old FROM `sys_dict_item`
       WHERE `dict_id` = v_fac_id AND `item_value` = '08'
         AND `item_text` = '配套项目核定用地与地籍调查';
    END IF;

    START TRANSACTION;

    -- -----------------------------------------------------------------------
    -- 2.1 字典本身的说明文字（原来写「5 个类型目录名」，现在口径变了）
    -- -----------------------------------------------------------------------
    UPDATE `sys_dict`
       SET `description` = '经营性用地（宗地）材料类型。★ 按 P1-08 报告图 3：共 4 类（01~04），编码与顺序即业务目录顺序'
     WHERE `dict_code` = 'land_attach_type';

    UPDATE `sys_dict`
       SET `description` = '配套项目材料类型。★ 按 P1-08 报告图 3：共 13 类（01 核定用地与地籍调查 → 13 移交文件）；环节进度挂配套下，也用这一套'
     WHERE `dict_code` = 'land_facility_attach_type';

    -- -----------------------------------------------------------------------
    -- 2.2 宗地字典：先回收「配套方案」那一项，再按旧值位移
    --     ★ 顺序很关键：新的 02 是「配套情况函」（由旧 03 移上来），
    --       所以必须先把旧 02「配套方案」删掉，否则位移后会出现两条 02。
    --       （sys_dict_item 的 (dict_id,item_value) 只是普通索引 idx_sdi_dict_val，
    --         不唯一，所以即使顺序反过来也不会报错，但会留下重复项 —— 更难查。）
    --     ★ 只删「值=02 且名=配套方案」这一条：名字一起匹配，避免误删已经同步过的库。
    -- -----------------------------------------------------------------------
    IF v_land_id IS NOT NULL AND v_land_old > 0 THEN

      -- 先回收旧 02「配套方案」：宗地侧不再有这个类别（内容归配套侧 07 专业配套方案）
      DELETE FROM `sys_dict_item`
       WHERE `dict_id` = v_land_id
         AND `item_value` = '02'
         AND `item_text` = '配套方案';

      -- 位移：01→01、03→02、04→03、05→04（一条 CASE 完成，避免 03→02 之后又被 02→99 之类再搬一次）
      UPDATE `sys_dict_item`
         SET `item_value` = CASE `item_value`
                              WHEN '01' THEN '01'
                              WHEN '03' THEN '02'
                              WHEN '04' THEN '03'
                              WHEN '05' THEN '04'
                              ELSE `item_value`
                            END,
             `item_text`  = CASE `item_value`
                              WHEN '01' THEN '土地整理计划'
                              WHEN '03' THEN '配套情况函'
                              WHEN '04' THEN '配套筹备函'
                              WHEN '05' THEN '出让宗地图形数据（shp）'
                              ELSE `item_text`
                            END,
             `sort_order` = CASE `item_value`
                              WHEN '01' THEN 1
                              WHEN '03' THEN 2
                              WHEN '04' THEN 3
                              WHEN '05' THEN 4
                              ELSE `sort_order`
                            END,
             `description` = CASE `item_value`
                              WHEN '01' THEN '报告图 3 第 1 项'
                              WHEN '03' THEN '报告图 3 第 2 项（原编码 03）'
                              WHEN '04' THEN '报告图 3 第 3 项（原编码 04）'
                              WHEN '05' THEN '报告图 3 第 4 项（原编码 05；旧目录另有 （SHP）/无后缀两种写法，统一为本名）'
                              ELSE `description`
                            END
       WHERE `dict_id` = v_land_id
         AND `item_value` IN ('01', '03', '04', '05');

      -- 兜底：任何落在 4 类之外的残留项都清掉（例如重复执行留下的 05），
      -- 否则下拉里会多出一个「选得到但上传会被拒」的选项。
      DELETE FROM `sys_dict_item`
       WHERE `dict_id` = v_land_id
         AND `item_value` NOT IN ('01', '02', '03', '04');
    END IF;

    -- -----------------------------------------------------------------------
    -- 2.3 配套字典：整轮置换（01~13），一条 CASE 完成
    --     ★ 只在旧口径库执行（v_fac_old > 0）；新库的特征项已不存在，跳过位移，
    --       只走 2.4 的「缺则补插」。
    -- -----------------------------------------------------------------------
    IF v_fac_id IS NOT NULL AND v_fac_old > 0 THEN
      UPDATE `sys_dict_item`
         SET `item_value` = CASE `item_value`
                              WHEN '01' THEN '02'
                              WHEN '02' THEN '03'
                              WHEN '03' THEN '04'
                              WHEN '04' THEN '05'
                              WHEN '05' THEN '07'
                              WHEN '06' THEN '10'
                              WHEN '07' THEN '08'
                              WHEN '08' THEN '01'
                              WHEN '09' THEN '06'
                              WHEN '10' THEN '09'
                              WHEN '11' THEN '11'
                              WHEN '12' THEN '12'
                              WHEN '13' THEN '13'
                              ELSE `item_value`
                            END,
             `item_text`  = CASE `item_value`
                              WHEN '01' THEN '项建批复'
                              WHEN '02' THEN '可研批复'
                              WHEN '03' THEN '初设及概算批复'
                              WHEN '04' THEN '道路规划'
                              WHEN '05' THEN '专业配套方案'
                              WHEN '06' THEN '施工许可'
                              WHEN '07' THEN '专业管理意见'
                              WHEN '08' THEN '配套项目核定用地与地籍调查'
                              WHEN '09' THEN '规划工程许可'
                              WHEN '10' THEN '规划用地许可与划拨手续办理'
                              WHEN '11' THEN '不动产登记'
                              WHEN '12' THEN '竣工文件'
                              WHEN '13' THEN '移交文件'
                              ELSE `item_text`
                            END,
             `sort_order` = CASE `item_value`
                              WHEN '01' THEN 2
                              WHEN '02' THEN 3
                              WHEN '03' THEN 4
                              WHEN '04' THEN 5
                              WHEN '05' THEN 7
                              WHEN '06' THEN 10
                              WHEN '07' THEN 8
                              WHEN '08' THEN 1
                              WHEN '09' THEN 6
                              WHEN '10' THEN 9
                              WHEN '11' THEN 11
                              WHEN '12' THEN 12
                              WHEN '13' THEN 13
                              ELSE `sort_order`
                            END,
             `description` = CASE `item_value`
                              WHEN '08' THEN '报告图 3 第 1 项（原编码 08）'
                              WHEN '01' THEN '报告图 3 第 2 项（原编码 01，原名「项建批复文件」）'
                              WHEN '02' THEN '报告图 3 第 3 项（原编码 02，原名「可研批复文件」）'
                              WHEN '03' THEN '报告图 3 第 4 项（原编码 03，原名「初设及概算批复文件」）'
                              WHEN '04' THEN '报告图 3 第 5 项（原编码 04）'
                              WHEN '09' THEN '报告图 3 第 6 项（原编码 09）'
                              WHEN '05' THEN '报告图 3 第 7 项（原编码 05；宗地侧原「配套方案」的内容也并入这一类）'
                              WHEN '07' THEN '报告图 3 第 8 项（原编码 07）'
                              WHEN '10' THEN '报告图 3 第 9 项（原编码 10）'
                              WHEN '06' THEN '报告图 3 第 10 项（原编码 06）'
                              WHEN '11' THEN '报告图 3 第 11 项（编码不变）'
                              WHEN '12' THEN '报告图 3 第 12 项（编码不变；与「移交文件」是两个独立类型）'
                              WHEN '13' THEN '报告图 3 第 13 项（编码不变）'
                              ELSE `description`
                            END
       WHERE `dict_id` = v_fac_id
         AND `item_value` IN ('01', '02', '03', '04', '05', '06', '07',
                              '08', '09', '10', '11', '12', '13');

      -- 兜底：13 类之外的残留项清掉（新库不会走到这里，老库若有人手加过会清掉）
      DELETE FROM `sys_dict_item`
       WHERE `dict_id` = v_fac_id
         AND `item_value` NOT IN ('01', '02', '03', '04', '05', '06', '07',
                                  '08', '09', '10', '11', '12', '13');
    END IF;

    -- -----------------------------------------------------------------------
    -- 2.4 补插：字典项缺失时补上，已有则不动 —— id 因此保持稳定
    --     ★ 为什么不用 INSERT ... SELECT ... WHERE NOT EXISTS：MySQL 的
    --       INSERT ... SELECT 不允许在子查询里再查目标表（ERROR 1093），
    --       而「这一项是否已存在」只能查 sys_dict_item 本身。
    --       改为逐项调用 sp_ins_dict_item（内部先 COUNT 再单行 INSERT）：
    --       每条只碰一行、不依赖其它语句，重复执行结果相同。
    -- -----------------------------------------------------------------------
    IF v_land_id IS NOT NULL THEN
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef101', v_land_id, '01', '土地整理计划',
              '报告图 3 第 1 项', 1);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef102', v_land_id, '02', '配套情况函',
              '报告图 3 第 2 项（原编码 03）', 2);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef103', v_land_id, '03', '配套筹备函',
              '报告图 3 第 3 项（原编码 04）', 3);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef104', v_land_id, '04', '出让宗地图形数据（shp）',
              '报告图 3 第 4 项（原编码 05；旧目录另有 （SHP）/无后缀两种写法，统一为本名）', 4);
    END IF;

    IF v_fac_id IS NOT NULL THEN
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef201', v_fac_id, '01', '配套项目核定用地与地籍调查',
              '报告图 3 第 1 项（原编码 08）', 1);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef202', v_fac_id, '02', '项建批复',
              '报告图 3 第 2 项（原编码 01，原名「项建批复文件」）', 2);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef203', v_fac_id, '03', '可研批复',
              '报告图 3 第 3 项（原编码 02，原名「可研批复文件」）', 3);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef204', v_fac_id, '04', '初设及概算批复',
              '报告图 3 第 4 项（原编码 03，原名「初设及概算批复文件」）', 4);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef205', v_fac_id, '05', '道路规划',
              '报告图 3 第 5 项（原编码 04）', 5);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef206', v_fac_id, '06', '规划工程许可',
              '报告图 3 第 6 项（原编码 09）', 6);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef207', v_fac_id, '07', '专业配套方案',
              '报告图 3 第 7 项（原编码 05；宗地侧原「配套方案」的内容也并入这一类）', 7);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef208', v_fac_id, '08', '专业管理意见',
              '报告图 3 第 8 项（原编码 07）', 8);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef209', v_fac_id, '09', '规划用地许可与划拨手续办理',
              '报告图 3 第 9 项（原编码 10）', 9);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef210', v_fac_id, '10', '施工许可',
              '报告图 3 第 10 项（原编码 06）', 10);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef211', v_fac_id, '11', '不动产登记',
              '报告图 3 第 11 项（编码不变）', 11);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef212', v_fac_id, '12', '竣工文件',
              '报告图 3 第 12 项（编码不变；与「移交文件」是两个独立类型）', 12);
      CALL sp_ins_dict_item('7a1f0d2c4e5b4d8a9c3f6b1ef213', v_fac_id, '13', '移交文件',
              '报告图 3 第 13 项（编码不变）', 13);
    END IF;

    SET v_rows = ROW_COUNT();

    INSERT INTO `t_data_migration_marker` (`code`, `applied_time`, `remark`)
    VALUES ('ATTACH_TYPE_DICT_V2', NOW(),
            CONCAT('材料类型字典同步完成（宗地 4 项 / 配套 13 项，按 item_value 原地更新，id 保持不变）；末段补插影响 ', v_rows, ' 行'));

    COMMIT;
  END IF;
END$$
DELIMITER ;

CALL `sp_att_type_dict_v2`();
DROP PROCEDURE IF EXISTS `sp_att_type_dict_v2`;
DROP PROCEDURE IF EXISTS `sp_ins_dict_item`;

-- ---------------------------------------------------------------------------
-- 3) 同步后核对（四条都要看）
-- ---------------------------------------------------------------------------

-- 3.1 台账
SELECT `code`, `applied_time`, `remark`
FROM `t_data_migration_marker`
WHERE `code` = 'ATTACH_TYPE_DICT_V2';

-- 3.2 ★ 字典现状：应 17 行（宗地 4 + 配套 13），编码与名称即报告图 3
SELECT d.`dict_code`, i.`item_value` AS 编码, i.`item_text` AS 名称, i.`sort_order` AS 排序
FROM `sys_dict` d
JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_attach_type', 'land_facility_attach_type')
ORDER BY d.`dict_code`, i.`sort_order`;

-- 3.3 应输出 0 行：项数与预期不符的字典（宗地 4 / 配套 13）
SELECT d.`dict_code`, COUNT(i.`id`) AS 实际项数,
       CASE d.`dict_code` WHEN 'land_attach_type' THEN 4 ELSE 13 END AS 预期项数
FROM `sys_dict` d
LEFT JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE d.`dict_code` IN ('land_attach_type', 'land_facility_attach_type')
GROUP BY d.`dict_code`
HAVING 实际项数 <> 预期项数;

-- 3.4 应输出 0 行：与后端白名单不一致的字典项（两处不一致时界面上会看到两种叫法）
SELECT d.`dict_code`, i.`item_value` AS 编码, i.`item_text` AS 名称
FROM `sys_dict` d
JOIN `sys_dict_item` i ON i.`dict_id` = d.`id`
WHERE (d.`dict_code` = 'land_attach_type' AND NOT (
          (i.`item_value` = '01' AND i.`item_text` = '土地整理计划')
       OR (i.`item_value` = '02' AND i.`item_text` = '配套情况函')
       OR (i.`item_value` = '03' AND i.`item_text` = '配套筹备函')
       OR (i.`item_value` = '04' AND i.`item_text` = '出让宗地图形数据（shp）')))
   OR (d.`dict_code` = 'land_facility_attach_type' AND NOT (
          (i.`item_value` = '01' AND i.`item_text` = '配套项目核定用地与地籍调查')
       OR (i.`item_value` = '02' AND i.`item_text` = '项建批复')
       OR (i.`item_value` = '03' AND i.`item_text` = '可研批复')
       OR (i.`item_value` = '04' AND i.`item_text` = '初设及概算批复')
       OR (i.`item_value` = '05' AND i.`item_text` = '道路规划')
       OR (i.`item_value` = '06' AND i.`item_text` = '规划工程许可')
       OR (i.`item_value` = '07' AND i.`item_text` = '专业配套方案')
       OR (i.`item_value` = '08' AND i.`item_text` = '专业管理意见')
       OR (i.`item_value` = '09' AND i.`item_text` = '规划用地许可与划拨手续办理')
       OR (i.`item_value` = '10' AND i.`item_text` = '施工许可')
       OR (i.`item_value` = '11' AND i.`item_text` = '不动产登记')
       OR (i.`item_value` = '12' AND i.`item_text` = '竣工文件')
       OR (i.`item_value` = '13' AND i.`item_text` = '移交文件')))
ORDER BY d.`dict_code`, i.`item_value`;

-- ---------------------------------------------------------------------------
-- 4) 幂等性自证：再执行一次，过程因台账已登记直接跳过，
--    3.1 的 applied_time 不变、3.2 仍是 17 行且逐字相同。
-- ---------------------------------------------------------------------------
