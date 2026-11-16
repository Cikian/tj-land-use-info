-- 配套功能区独立列与字典扩展（可重复执行）
-- 口径：xzqh 只保留 16 区；已确认的功能区迁到 gnq，行政区归并为滨海新区。
-- 不对未知值做“其他”归类，避免丢失业务事实。

SET @schema_name = DATABASE();
SET @add_gnq_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_supporting_facilities` ADD COLUMN `gnq` VARCHAR(50) NULL COMMENT ''功能区（生态城/土地发展中心/经开区/高新区/保税区）'' AFTER `xzqh`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 't_supporting_facilities'
    AND COLUMN_NAME = 'gnq'
);
PREPARE stmt FROM @add_gnq_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史功能区数据：只迁移已确认的 5 类写法，保留原 gnq 非空值。
UPDATE `t_supporting_facilities`
SET `gnq` = `xzqh`, `xzqh` = '滨海新区'
WHERE `xzqh` IN ('生态城', '土地发展中心', '经开区', '高新区', '保税区')
  AND (`gnq` IS NULL OR `gnq` = '');

-- 核对：应只剩 16 区行政区；如有返回值需人工确认，不得直接改成“其他”。
SELECT DISTINCT `xzqh`
FROM `t_supporting_facilities`
WHERE `xzqh` IS NOT NULL
  AND `xzqh` NOT IN (
    '和平区','河东区','河西区','南开区','河北区','红桥区','东丽区','西青区',
    '津南区','北辰区','武清区','宝坻区','滨海新区','宁河区','静海区','蓟州区'
  )
ORDER BY `xzqh`;

SELECT `xzqh`, `gnq`, COUNT(*) AS `record_count`
FROM `t_supporting_facilities`
WHERE `gnq` IS NOT NULL AND `gnq` <> ''
GROUP BY `xzqh`, `gnq`
ORDER BY `gnq`;
