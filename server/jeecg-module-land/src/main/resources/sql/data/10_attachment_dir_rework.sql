-- =============================================================================
--  附件目录按「材料类型」重构：回收 2026-10-10 上一版的目录表与 dir_path
-- =============================================================================
--  需求最终口径（2026-10-10 二次确认）：
--    1. 附件类型（材料类型）**完全按旧系统存储目录里的类型**定：
--       宗地 5 类（t_land_attachment 用 land_attach_type 字典）
--       配套 13 类（用 land_facility_attach_type 字典）
--    2. 目录 = 材料类型，**只有一层**（项目 → 材料类型 → 文件）；
--       选文件夹上传时把内部文件**拉平**释放到该材料类型目录（重名加后缀）
--    3. 空目录不建、不显示
--    4. 树形展示在「附件管理」与「地块/配套详情」两处
--
--  ★ 因此上一版的「任意层级目录表 + dir_path」是多余的，本脚本回收：
--    · t_land_attachment_dir —— 它存在的唯一理由是「表达空目录」，而空目录不建
--    · t_land_attachment.dir_path —— 材料类型码已经存在 file_type 里，再存一份
--      就是同一事实的第二份记录（必然有一天不一致）
--
--  说明：这两样都没有产生过业务数据（功能尚未接线），因此直接回收，
--        不需要数据迁移。
--
--  幂等：删除前先判断是否存在。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 回收 t_land_attachment.dir_path（含索引）
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_drop_att_dir_path`;

DELIMITER $$
CREATE PROCEDURE `sp_drop_att_dir_path`()
BEGIN
  IF EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = (SELECT DATABASE())
        AND TABLE_NAME = 't_land_attachment'
        AND COLUMN_NAME = 'dir_path'
  ) THEN
    -- 索引跟着列一起删；若索引先存在则单独删（重复执行不报错）
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = (SELECT DATABASE())
          AND TABLE_NAME = 't_land_attachment'
          AND INDEX_NAME = 'idx_att_dir'
    ) THEN
      ALTER TABLE `t_land_attachment` DROP INDEX `idx_att_dir`;
    END IF;
    ALTER TABLE `t_land_attachment` DROP COLUMN `dir_path`;
  END IF;
END$$
DELIMITER ;

CALL `sp_drop_att_dir_path`();
DROP PROCEDURE IF EXISTS `sp_drop_att_dir_path`;

-- ---------------------------------------------------------------------------
-- 2. 回收目录表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_land_attachment_dir`;

-- ---------------------------------------------------------------------------
-- 3. 核对：应输出 0 行（表已不存在）与 0 行（列已不存在）
-- ---------------------------------------------------------------------------
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = (SELECT DATABASE())
  AND TABLE_NAME = 't_land_attachment_dir';

SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = (SELECT DATABASE())
  AND TABLE_NAME = 't_land_attachment'
  AND COLUMN_NAME = 'dir_path';

-- 附件按材料类型分布（新树形结构的数据基础：只显示有文件的类型）
SELECT file_type,
       CASE biz_type
         WHEN 'land'     THEN (SELECT item_text FROM sys_dict_item i
                                JOIN sys_dict d ON d.id = i.dict_id
                               WHERE d.dict_code = 'land_attach_type'
                                 AND i.item_value = a.file_type LIMIT 1)
         WHEN 'facility' THEN (SELECT item_text FROM sys_dict_item i
                                JOIN sys_dict d ON d.id = i.dict_id
                               WHERE d.dict_code = 'land_facility_attach_type'
                                 AND i.item_value = a.file_type LIMIT 1)
       END AS type_name,
       COUNT(*) AS file_count
FROM t_land_attachment a
WHERE a.del_flag = 0
GROUP BY a.biz_type, a.file_type
ORDER BY a.biz_type, a.file_type;
