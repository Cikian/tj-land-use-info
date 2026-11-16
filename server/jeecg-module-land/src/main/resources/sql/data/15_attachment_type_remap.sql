-- =============================================================================
--  附件材料类型重编码（P1-08）：按报告图 3 把历史 file_type 迁到新编码
-- =============================================================================
--  目标表：t_land_attachment（统一附件表，biz_type + biz_id + file_type）
--  基准  ：《系统测试问题分级整改清单》P1-08「报告图 3」
--          宗地（BIZ_LAND → biz_type='land'）4 类
--          配套（BIZ_FACILITY → biz_type='facility'）13 类
--          环节进度（biz_type='process'）挂配套下，跟随配套那 13 类
--
--  ★ 这不是「只改显示顺序」：file_type 是落库的**编码**，历史行必须逐行搬，
--    否则旧码 02 在新字典里是「配套情况函」、旧数据里却是「配套方案」——
--    用户会在目录树里看到文件挂在一个完全不相干的类别下（P1-08 的验收红线）。
--
--  ★ 本脚本只动 t_land_attachment.file_type，不动 biz_type/biz_id/store_path，
--    附件本体与归属关系全部不变；磁盘文件更不会被碰。
--    字典项由配套脚本 16_attach_type_dict_sync.sql 负责（两件事分开跑，便于单独回滚判断）。
--
-- -----------------------------------------------------------------------------
--  一、旧编码 → 新编码 → 含义（全表；旧码为 2026-11 整改前的值）
-- -----------------------------------------------------------------------------
--  A. 宗地（biz_type='land'）旧 5 类 → 新 4 类
--     旧码 旧含义                          新码  新含义                        去向
--     01   土地整理计划                    01    土地整理计划                  原地保留
--     02   配套方案                        99    其他（历史）                  ★ 宗地侧不再保留该类别；
--                                                                            业务内容归配套侧 07 专业配套方案，
--                                                                            但附件 biz_id 是 t_land.id，
--                                                                            改挂配套会指向不存在的配套项目 →
--                                                                            只能留宗地下并单列人工核对
--     03   配套情况函                      02    配套情况函                    提前一位
--     04   配套筹备函                      03    配套筹备函                    提前一位
--     05   出让宗地图形数据（SHP）         04    出让宗地图形数据（shp）        提前一位
--
--  B. 配套（biz_type='facility'）旧 13 类 → 新 13 类（**整体置换**，见下方幂等说明）
--     旧码 旧含义                          新码  新含义
--     01   项建批复文件                    02    项建批复
--     02   可研批复文件                    03    可研批复
--     03   初设及概算批复文件              04    初设及概算批复
--     04   道路规划                        05    道路规划
--     05   专业配套方案                    07    专业配套方案（同时接收宗地侧原 02 的业务内容）
--     06   施工许可                        10    施工许可
--     07   专业管理意见                    08    专业管理意见
--     08   配套项目核定用地与地籍调查      01    配套项目核定用地与地籍调查（报告图里是第 1 项）
--     09   规划工程许可                    06    规划工程许可
--     10   规划用地许可与划拨手续办理      09    规划用地许可与划拨手续办理
--     11   不动产登记                      11    不动产登记（不变）
--     12   竣工文件                        12    竣工文件（不变）
--     13   移交文件                        13    移交文件（不变）
--
--  C. 环节进度（biz_type='process'）旧用宗地那套（01~05）→ 新跟随配套 13 类
--     旧码 旧含义                          新码  说明
--     01   土地整理计划                    99    配套目录里没有对口类别
--     02   配套方案                        07    正好归入配套侧「专业配套方案」
--     03   配套情况函                      99    配套目录里没有对口类别
--     04   配套筹备函                      99    配套目录里没有对口类别
--     05   出让宗地图形数据（SHP）         99    配套目录里没有对口类别
--     ★ 2026-11-16 实测：库里 biz_type 只有 'land' 与 'facility'，process 行数为 0，
--       本节是「将来若有历史过程数据也不会串错」的保险，执行时影响 0 行。
--
--  ★ 无对应项的旧码统一处置为 99「其他（历史）」：
--    · 附件不丢、不软删、不改归属，只在目录树末尾多一个「其他（历史）」分组；
--    · 99 不在上传白名单里（LandAttachmentServiceImpl.TYPE_NAMES_LEGACY 只用于回显），
--      新数据不允许再落到 99；
--    · 迁移后由「三、核对」里的 99 明细清单逐条人工确认归位。
--
-- -----------------------------------------------------------------------------
--  二、幂等方案（关键：两次执行结果必须完全相同）
-- -----------------------------------------------------------------------------
--  ★ 为什么不能写成一串小 UPDATE：旧→新是一组**置换**
--    （facility 的 01→02 与 02→03 互相踩；land 的 03→02 与 02→99 也互相踩）。
--    逐条执行会把上一条的结果当成下一条的输入 —— 一次就串味，跑两次彻底乱。
--
--  ★ 做法：
--    1) **单条** UPDATE + CASE 完成整轮置换。MySQL 的 UPDATE 对每一行都按**原值**
--       计算 SET 表达式（不是边改边读），所以一条语句内置换是安全的。
--    2) 幂等靠迁移台账表 t_data_migration_marker：同一 code 已登记就整段跳过。
--       三个 code 分块登记（LAND / FACILITY / PROCESS），任一块失败不影响其它块重跑。
--    3) UPDATE 与台账登记放在**同一个存储过程 + 同一个事务**里（START TRANSACTION /
--       COMMIT + 异常 ROLLBACK），保证「数据搬了但台账没记」不会发生 ——
--       否则第二次执行会把已经搬过的行再搬一遍。
--    4) 台账的 remark 里记「迁移前/后的分组计数」，事后核对不必再猜。
--    5) **防误跑闸门**：每块搬数据前先看字典还是不是旧口径
--       （宗地看有没有『02 配套方案』，配套看『核定用地与地籍调查』是否还在 08）。
--       新库（用改造后的 01_data_dict.sql 初始化）跑到这里会被判定为「无需搬移」，
--       只登记一行「跳过」台账，**不碰数据** —— 否则新数据的 02（配套情况函）
--       会被旧映射搬进 99 历史桶，破坏比不跑更难发现。
--       ★ 因此本脚本必须**先于** 16_attach_type_dict_sync.sql 执行：
--         16 会把字典改成新口径，之后 15 再跑就只能判定为「跳过」。
--         16 里也有对应的顺序闸门（未看到本脚本的台账就直接报错）。
--
--  ★ 执行顺序：先本脚本（15），再 16_attach_type_dict_sync.sql；最后重启/清前端字典缓存。
--  ★ 回滚：本脚本不自动回滚。如需回滚，用同一套 CASE 反向执行（台账 code 加 _ROLLBACK），
--    建议执行前先备份：
--      CREATE TABLE t_land_attachment_bak_20261116 AS SELECT * FROM t_land_attachment;
--
--  ★ 本次实测（2026-11-16，49.232.252.56 / tj-jyxyd，含 del_flag=1 的软删行）：
--      迁移前 land     ：01=28、02=23、03=5、05=1（合计 57）
--      迁移前 facility ：01=14、02=3、03=3、04=5、05=1（合计 26）
--      迁移前 process  ：0 行
--      预期迁移后 land     ：01=28、02=5、03=0、04=1、99=23（合计仍 57）
--      预期迁移后 facility ：02=14、03=3、04=3、05=5、07=1（合计仍 26）
--    （其它库请以台账 remark 里记录的实测值为准，不要照抄上面这组数字。）
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0) 迁移台账表（本库此前没有任何 migration 台账表，这里建立最小的一张）
--    幂等：IF NOT EXISTS
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_data_migration_marker` (
  `code`         varchar(64)  NOT NULL COMMENT '迁移批次码（同一 code 只执行一次）',
  `applied_time` datetime     NOT NULL COMMENT '执行时间',
  `remark`       varchar(500)     NULL COMMENT '执行结果摘要（含迁移前/后分组计数，便于事后核对）',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据迁移台账（幂等标记：同一 code 只应执行一次）';

-- ---------------------------------------------------------------------------
-- 1) 迁移前核对（先留证据，再动数据）
--    1.1 按 biz_type + file_type 分组（del_flag 分列，软删行也要迁 —— 回收站恢复后
--        必须仍然显示在正确的材料类型下；不迁的话恢复出来的附件是错类）
-- ---------------------------------------------------------------------------
SELECT biz_type,
       file_type,
       SUM(CASE WHEN del_flag = 0 THEN 1 ELSE 0 END) AS 有效行,
       SUM(CASE WHEN del_flag = 1 THEN 1 ELSE 0 END) AS 软删行,
       COUNT(*) AS 合计
FROM `t_land_attachment`
GROUP BY biz_type, file_type
ORDER BY biz_type, file_type;

--    1.2 全表行数（迁移后必须与这一行完全相同：本脚本不增不删任何附件行）
SELECT COUNT(*) AS 迁移前附件总行数 FROM `t_land_attachment`;

-- ---------------------------------------------------------------------------
-- 2) 宗地侧迁移（biz_type='land'）：旧 01~05 → 新 01~04 + 99
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_att_type_land`;

DELIMITER $$
CREATE PROCEDURE `sp_att_type_land`()
BEGIN
  DECLARE v_done     INT DEFAULT 0;
  DECLARE v_old_dict INT DEFAULT 0;
  DECLARE v_rows     INT DEFAULT 0;
  DECLARE v_before   VARCHAR(500) DEFAULT NULL;
  DECLARE v_after    VARCHAR(500) DEFAULT NULL;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  SELECT COUNT(*) INTO v_done FROM `t_data_migration_marker`
   WHERE `code` = 'ATTACH_TYPE_V2_LAND';

  -- ★ 防误跑闸门：只有「字典还是旧口径」的库才允许搬数据。
  --   判据就是旧字典里那条只在旧口径存在的『02 配套方案』。
  --   为什么必须有这道闸：新库（用改造后的 01_data_dict.sql 初始化）里 02 是
  --   『配套情况函』，若把它的附件按旧映射搬成 99，等于把新数据搬到历史桶里 ——
  --   这类「脚本跑错库」的破坏比不跑更难发现。
  SELECT COUNT(*) INTO v_old_dict
    FROM `sys_dict_item` i JOIN `sys_dict` d ON d.`id` = i.`dict_id`
   WHERE d.`dict_code` = 'land_attach_type'
     AND i.`item_value` = '02' AND i.`item_text` = '配套方案';

  IF v_done = 0 THEN
    IF v_old_dict = 0 THEN
      INSERT INTO `t_data_migration_marker` (`code`, `applied_time`, `remark`)
      VALUES ('ATTACH_TYPE_V2_LAND', NOW(),
              '跳过：字典已是新口径（无旧「02 配套方案」），判定本库无需搬移宗地附件');
    ELSE
      SELECT GROUP_CONCAT(CONCAT(t.`file_type`, '=', t.cnt) ORDER BY t.`file_type` SEPARATOR ',')
        INTO v_before
        FROM (SELECT `file_type`, COUNT(*) AS cnt FROM `t_land_attachment`
               WHERE `biz_type` = 'land' GROUP BY `file_type`) t;

      START TRANSACTION;

      -- ★ 单条 CASE：整轮置换在一次语句内完成；没有「先改哪条」的顺序问题。
      --   02 特别处理：宗地侧不再保留「配套方案」，迁到 99（其他（历史）），附件不丢。
      UPDATE `t_land_attachment`
         SET `file_type` = CASE TRIM(`file_type`)
                             WHEN '01' THEN '01'   -- 土地整理计划              → 土地整理计划
                             WHEN '02' THEN '99'   -- 配套方案                  → 其他（历史）
                             WHEN '03' THEN '02'   -- 配套情况函                → 配套情况函
                             WHEN '04' THEN '03'   -- 配套筹备函                → 配套筹备函
                             WHEN '05' THEN '04'   -- 出让宗地图形数据（SHP）   → 出让宗地图形数据（shp）
                             ELSE TRIM(`file_type`)
                           END
       WHERE `biz_type` = 'land'
         AND TRIM(`file_type`) IN ('01', '02', '03', '04', '05');

      SET v_rows = ROW_COUNT();

      SELECT GROUP_CONCAT(CONCAT(t.`file_type`, '=', t.cnt) ORDER BY t.`file_type` SEPARATOR ',')
        INTO v_after
        FROM (SELECT `file_type`, COUNT(*) AS cnt FROM `t_land_attachment`
               WHERE `biz_type` = 'land' GROUP BY `file_type`) t;

      INSERT INTO `t_data_migration_marker` (`code`, `applied_time`, `remark`)
      VALUES ('ATTACH_TYPE_V2_LAND', NOW(),
              CONCAT('宗地材料类型迁移 ', v_rows, ' 行；前：', IFNULL(v_before, '空'),
                     '；后：', IFNULL(v_after, '空')));

      COMMIT;
    END IF;
  END IF;
END$$
DELIMITER ;

CALL `sp_att_type_land`();
DROP PROCEDURE IF EXISTS `sp_att_type_land`;

-- ---------------------------------------------------------------------------
-- 3) 配套侧迁移（biz_type='facility'）：旧 01~13 → 新 01~13（整体置换）
--    02 项建批复文件 → 03 可研批复文件 → … 互相踩，必须一条 CASE 完成
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_att_type_facility`;

DELIMITER $$
CREATE PROCEDURE `sp_att_type_facility`()
BEGIN
  DECLARE v_done     INT DEFAULT 0;
  DECLARE v_old_dict INT DEFAULT 0;
  DECLARE v_rows     INT DEFAULT 0;
  DECLARE v_before   VARCHAR(500) DEFAULT NULL;
  DECLARE v_after    VARCHAR(500) DEFAULT NULL;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  SELECT COUNT(*) INTO v_done FROM `t_data_migration_marker`
   WHERE `code` = 'ATTACH_TYPE_V2_FACILITY';

  -- ★ 防误跑闸门（同宗地侧）：旧口径里「配套项目核定用地与地籍调查」在 08，
  --   新口径在 01。这条判据只在旧库里成立；新库跑到这里说明本脚本不该动数据。
  SELECT COUNT(*) INTO v_old_dict
    FROM `sys_dict_item` i JOIN `sys_dict` d ON d.`id` = i.`dict_id`
   WHERE d.`dict_code` = 'land_facility_attach_type'
     AND i.`item_value` = '08' AND i.`item_text` = '配套项目核定用地与地籍调查';

  IF v_done = 0 THEN
    IF v_old_dict = 0 THEN
      INSERT INTO `t_data_migration_marker` (`code`, `applied_time`, `remark`)
      VALUES ('ATTACH_TYPE_V2_FACILITY', NOW(),
              '跳过：字典已是新口径（「核定用地与地籍调查」已在 01），判定本库无需搬移配套附件');
    ELSE
      SELECT GROUP_CONCAT(CONCAT(t.`file_type`, '=', t.cnt) ORDER BY t.`file_type` SEPARATOR ',')
        INTO v_before
        FROM (SELECT `file_type`, COUNT(*) AS cnt FROM `t_land_attachment`
               WHERE `biz_type` = 'facility' GROUP BY `file_type`) t;

      START TRANSACTION;

      UPDATE `t_land_attachment`
         SET `file_type` = CASE TRIM(`file_type`)
                             WHEN '01' THEN '02'   -- 项建批复文件              → 项建批复
                             WHEN '02' THEN '03'   -- 可研批复文件              → 可研批复
                             WHEN '03' THEN '04'   -- 初设及概算批复文件        → 初设及概算批复
                             WHEN '04' THEN '05'   -- 道路规划                  → 道路规划
                             WHEN '05' THEN '07'   -- 专业配套方案              → 专业配套方案
                             WHEN '06' THEN '10'   -- 施工许可                  → 施工许可
                             WHEN '07' THEN '08'   -- 专业管理意见              → 专业管理意见
                             WHEN '08' THEN '01'   -- 配套项目核定用地与地籍调查 → 第 1 项
                             WHEN '09' THEN '06'   -- 规划工程许可              → 规划工程许可
                             WHEN '10' THEN '09'   -- 规划用地许可与划拨手续办理 → 第 9 项
                             WHEN '11' THEN '11'   -- 不动产登记                → 不动产登记
                             WHEN '12' THEN '12'   -- 竣工文件                  → 竣工文件
                             WHEN '13' THEN '13'   -- 移交文件                  → 移交文件
                             ELSE TRIM(`file_type`)
                           END
       WHERE `biz_type` = 'facility'
         AND TRIM(`file_type`) IN ('01', '02', '03', '04', '05', '06', '07',
                                   '08', '09', '10', '11', '12', '13');

      SET v_rows = ROW_COUNT();

      SELECT GROUP_CONCAT(CONCAT(t.`file_type`, '=', t.cnt) ORDER BY t.`file_type` SEPARATOR ',')
        INTO v_after
        FROM (SELECT `file_type`, COUNT(*) AS cnt FROM `t_land_attachment`
               WHERE `biz_type` = 'facility' GROUP BY `file_type`) t;

      INSERT INTO `t_data_migration_marker` (`code`, `applied_time`, `remark`)
      VALUES ('ATTACH_TYPE_V2_FACILITY', NOW(),
              CONCAT('配套材料类型迁移 ', v_rows, ' 行；前：', IFNULL(v_before, '空'),
                     '；后：', IFNULL(v_after, '空')));

      COMMIT;
    END IF;
  END IF;
END$$
DELIMITER ;

CALL `sp_att_type_facility`();
DROP PROCEDURE IF EXISTS `sp_att_type_facility`;

-- ---------------------------------------------------------------------------
-- 4) 环节进度迁移（biz_type='process'）：旧用宗地那套 01~05 → 跟随配套 13 类
--    ★ 现行库里 process 行数为 0（2026-11-16 实测），本段影响 0 行；
--      保留它是为了让「有历史环节附件的库」也不会串错类别。
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_att_type_process`;

DELIMITER $$
CREATE PROCEDURE `sp_att_type_process`()
BEGIN
  DECLARE v_done     INT DEFAULT 0;
  DECLARE v_old_dict INT DEFAULT 0;
  DECLARE v_rows     INT DEFAULT 0;
  DECLARE v_before   VARCHAR(500) DEFAULT NULL;
  DECLARE v_after    VARCHAR(500) DEFAULT NULL;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  SELECT COUNT(*) INTO v_done FROM `t_data_migration_marker`
   WHERE `code` = 'ATTACH_TYPE_V2_PROCESS';

  -- ★ 防误跑闸门：环节旧码用的是宗地那套（01~05），与新配套码 01~05 **数值重叠**，
  --   光看码值分辨不出新旧。所以判据借用宗地字典的旧态（旧库里才有『02 配套方案』）：
  --   旧库 → 环节行一定是旧码，可以搬；新库 → 环节行本来就按配套 13 类落库，不能动。
  SELECT COUNT(*) INTO v_old_dict
    FROM `sys_dict_item` i JOIN `sys_dict` d ON d.`id` = i.`dict_id`
   WHERE d.`dict_code` = 'land_attach_type'
     AND i.`item_value` = '02' AND i.`item_text` = '配套方案';

  IF v_done = 0 THEN
    IF v_old_dict = 0 THEN
      INSERT INTO `t_data_migration_marker` (`code`, `applied_time`, `remark`)
      VALUES ('ATTACH_TYPE_V2_PROCESS', NOW(),
              '跳过：字典已是新口径，环节附件本来就按配套 13 类落库，无需搬移');
    ELSE
      SELECT GROUP_CONCAT(CONCAT(t.`file_type`, '=', t.cnt) ORDER BY t.`file_type` SEPARATOR ',')
        INTO v_before
        FROM (SELECT `file_type`, COUNT(*) AS cnt FROM `t_land_attachment`
               WHERE `biz_type` = 'process' GROUP BY `file_type`) t;

      START TRANSACTION;

      UPDATE `t_land_attachment`
         SET `file_type` = CASE TRIM(`file_type`)
                             WHEN '01' THEN '99'   -- 土地整理计划            → 其他（历史）
                             WHEN '02' THEN '07'   -- 配套方案                → 专业配套方案
                             WHEN '03' THEN '99'   -- 配套情况函              → 其他（历史）
                             WHEN '04' THEN '99'   -- 配套筹备函              → 其他（历史）
                             WHEN '05' THEN '99'   -- 出让宗地图形数据（SHP） → 其他（历史）
                             ELSE TRIM(`file_type`)
                           END
       WHERE `biz_type` = 'process'
         AND TRIM(`file_type`) IN ('01', '02', '03', '04', '05');

      SET v_rows = ROW_COUNT();

      SELECT GROUP_CONCAT(CONCAT(t.`file_type`, '=', t.cnt) ORDER BY t.`file_type` SEPARATOR ',')
        INTO v_after
        FROM (SELECT `file_type`, COUNT(*) AS cnt FROM `t_land_attachment`
               WHERE `biz_type` = 'process' GROUP BY `file_type`) t;

      INSERT INTO `t_data_migration_marker` (`code`, `applied_time`, `remark`)
      VALUES ('ATTACH_TYPE_V2_PROCESS', NOW(),
              CONCAT('环节材料类型迁移 ', v_rows, ' 行；前：', IFNULL(v_before, '空'),
                     '；后：', IFNULL(v_after, '空')));

      COMMIT;
    END IF;
  END IF;
END$$
DELIMITER ;

CALL `sp_att_type_process`();
DROP PROCEDURE IF EXISTS `sp_att_type_process`;

-- ---------------------------------------------------------------------------
-- 5) 迁移后核对（四条都要看）
-- ---------------------------------------------------------------------------

-- 5.1 台账：三行，remark 里就是「迁移前后分组计数」的实测快照
SELECT `code`, `applied_time`, `remark`
FROM `t_data_migration_marker`
WHERE `code` LIKE 'ATTACH_TYPE_V2%'
ORDER BY `code`;

-- 5.2 迁移后分组计数（与 5.1 的「后」列必须一致；合计行数要与步骤 1.2 相同）
SELECT biz_type,
       file_type,
       SUM(CASE WHEN del_flag = 0 THEN 1 ELSE 0 END) AS 有效行,
       SUM(CASE WHEN del_flag = 1 THEN 1 ELSE 0 END) AS 软删行,
       COUNT(*) AS 合计
FROM `t_land_attachment`
GROUP BY biz_type, file_type
ORDER BY biz_type, file_type;

-- 5.3 ★ 99「其他（历史）」明细清单（人工核对用：这些附件需要业务确认归位，
--     不得丢弃、不得软删）。按当前实测应为 23 行（宗地侧原「配套方案」）。
--     归位方式：业务确认后在页面上删掉旧附件重新上传到正确类型，
--     或由主 Agent 按确认结果补一条 UPDATE（本脚本不擅自归位 —— 归错比不归更难查）。
SELECT id, biz_type, biz_id, biz_key, file_name, store_path,
       del_flag, upload_time
FROM `t_land_attachment`
WHERE `file_type` = '99'
ORDER BY biz_type, upload_time, id;

-- 5.4 ★ 「迁移后不应再出现的旧码」检查：应输出 0 行
--     · land：新目录只有 01~04（+ 历史桶 99）。旧 05（出让宗地图形数据）已不存在，
--             任何 05/06/07/08/09/10 都是漏迁或错写。
--     · facility / process：新目录只有 01~13，其它码一律不对。
--     · 注意 land 的 01~04 与 facility 的 01~13 都「看起来合法」，
--       所以旧码残留**不能只靠码值判断**，必须对照 5.1 的计数快照 ——
--       下面的查询只兜住「码值根本不在新集合里」这一类，计数核对看 5.1/5.2。
SELECT biz_type, file_type, COUNT(*) AS 异常行数,
       CASE biz_type
         WHEN 'land'              THEN '宗地只允许 01~04、99'
         WHEN 'process'           THEN '环节只允许 01~13、99'
         ELSE '配套只允许 01~13'
       END AS 允许范围
FROM `t_land_attachment`
WHERE (`biz_type` = 'land'
        AND TRIM(`file_type`) NOT IN ('01', '02', '03', '04', '99'))
   OR (`biz_type` = 'process'
        AND TRIM(`file_type`) NOT IN ('01', '02', '03', '04', '05', '06', '07',
                                      '08', '09', '10', '11', '12', '13', '99'))
   OR (`biz_type` = 'facility'
        AND TRIM(`file_type`) NOT IN ('01', '02', '03', '04', '05', '06', '07',
                                      '08', '09', '10', '11', '12', '13'))
GROUP BY biz_type, file_type;

-- 5.5 「旧含义是否还挂错」抽样核对：按新编码列目录树（与页面上的分组同名同序）
--     宗地：01 土地整理计划 / 02 配套情况函 / 03 配套筹备函 /
--           04 出让宗地图形数据（shp）/ 99 其他（历史）
SELECT a.biz_type,
       a.file_type,
       CASE a.biz_type
         WHEN 'land' THEN CASE a.file_type
                            WHEN '01' THEN '土地整理计划'
                            WHEN '02' THEN '配套情况函'
                            WHEN '03' THEN '配套筹备函'
                            WHEN '04' THEN '出让宗地图形数据（shp）'
                            WHEN '99' THEN '其他（历史）'
                            ELSE '★未登记' END
         ELSE CASE a.file_type
                WHEN '01' THEN '配套项目核定用地与地籍调查'
                WHEN '02' THEN '项建批复'
                WHEN '03' THEN '可研批复'
                WHEN '04' THEN '初设及概算批复'
                WHEN '05' THEN '道路规划'
                WHEN '06' THEN '规划工程许可'
                WHEN '07' THEN '专业配套方案'
                WHEN '08' THEN '专业管理意见'
                WHEN '09' THEN '规划用地许可与划拨手续办理'
                WHEN '10' THEN '施工许可'
                WHEN '11' THEN '不动产登记'
                WHEN '12' THEN '竣工文件'
                WHEN '13' THEN '移交文件'
                WHEN '99' THEN '其他（历史）'
                ELSE '★未登记' END
       END AS 材料类型,
       COUNT(*) AS 文件数
FROM `t_land_attachment` a
GROUP BY a.biz_type, a.file_type
ORDER BY a.biz_type, a.file_type;

-- ---------------------------------------------------------------------------
-- 6) 幂等性自证：再执行一次本脚本，三个存储过程都会因台账已登记而跳过，
--    步骤 5.1 的 applied_time 不变、5.2 的计数不变 —— 这就是「跑两次数据不错乱」的证据。
-- ---------------------------------------------------------------------------
