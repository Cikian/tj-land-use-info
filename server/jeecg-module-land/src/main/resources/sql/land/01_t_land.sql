-- ============================================================================
--  经营性用地（出让宗地）表 t_land
--  依据：docs/升级改造工作内容清单.md 3.1.A（xj_kjkfb_commercial_land 847 条 → t_land）
--
--  为什么要有这张表：
--    新系统（档案管理 / 收发文管理 / 数据管理）全部落在 tj-jyxyd 库，
--    而宗地数据只在旧库 nutzwk_ywk.xj_kjkfb_commercial_land 里。
--    设计文档 6.3.9（3）明确要求「不要跨库读」——配套项目表
--    xj_kjkfb_supporting_facilities 早已复制进 tj-jyxyd，宗地表却没有，
--    本脚本把宗地表也复制进来，使「宗地 → 配套项目」的 1:N 关联可以在同库 JOIN。
--
--  字段策略：
--    业务字段沿用旧库列名（不改名），避免与其它模块/文档产生语义漂移；
--    只把审计字段规范化：delFlag(varchar '0'/'1') → del_flag(tinyint 0/1)、
--    createAccount → create_by、createTime → create_time，并补 update_by/update_time。
--    source_id 保留旧库主键，便于迁移溯源（本期 id 直接沿用旧库 id，两者相同）。
--
--  可重复执行：建表用 IF NOT EXISTS，数据用 INSERT ... ON DUPLICATE KEY UPDATE。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_land` (
  `id`              varchar(64)   NOT NULL                COMMENT '主键（沿用旧库 xj_kjkfb_commercial_land.id）',
  `crzdbh`          varchar(100)  NOT NULL                COMMENT '出让宗地编号（业务主键，宗地↔配套的关联键）',
  `dkmc`            varchar(255)      NULL                COMMENT '地块名称',
  `xzqh`            varchar(100)      NULL                COMMENT '行政区划',
  `xmfl`            varchar(100)      NULL                COMMENT '项目分类：市级项目 / 区级项目',
  `ghydxz`          varchar(100)      NULL                COMMENT '规划用地性质',
  `crj`             decimal(14,2)     NULL                COMMENT '出让金（亿元）',
  `crsj`            date              NULL                COMMENT '出让时间',
  `kjsydmj`         decimal(14,2)     NULL                COMMENT '可建设用地面积（平方米）',
  `zydmj`           decimal(14,2)     NULL                COMMENT '总用地面积（平方米）',
  `jsmj`            decimal(14,2)     NULL                COMMENT '建设面积（平方米）',
  `nrcbdptf`        decimal(14,2)     NULL                COMMENT '纳入成本的配套费（万元）',
  `wcd`             decimal(10,2)     NULL                COMMENT '完成度',
  `ptsfqq`          varchar(100)      NULL                COMMENT '配套是否齐全',
  `ptjsnr`          varchar(2000)     NULL                COMMENT '配套建设内容',
  `srr`             varchar(100)      NULL                COMMENT '受让人',
  `htydjfsj`        date              NULL                COMMENT '合同约定交付时间',
  `lpmc`            varchar(100)      NULL                COMMENT '楼盘名称',
  `lpjfsj`          date              NULL                COMMENT '楼盘交付时间（或计划交付时间）',
  `tdzldw`          varchar(100)      NULL                COMMENT '土地整理单位',
  `tdzljhxdwjh`     varchar(2000)     NULL                COMMENT '土地整理计划下达文件号',
  `tdzljh`          varchar(100)      NULL                COMMENT '土地整理计划',
  `dz`              varchar(100)      NULL                COMMENT '东至',
  `xz`              varchar(100)      NULL                COMMENT '西至',
  `nz`              varchar(100)      NULL                COMMENT '南至',
  `bz`              varchar(100)      NULL                COMMENT '北至',
  `ptqkh`           varchar(100)      NULL                COMMENT '配套情况函',
  `ptcbh`           varchar(100)      NULL                COMMENT '配套筹备函',
  `crzdtxsj`        varchar(100)      NULL                COMMENT '出让宗地图形数据（shp）',
  `xzqh2`           varchar(100)      NULL                COMMENT '录入单位简称（旧字段名为 xzqh2，语义与名称不符，沿用但不展示为行政区划）',
  `zlqsnrsm`        varchar(2500)     NULL                COMMENT '资料缺失内容及说明',
  `lrdw`            varchar(100)      NULL                COMMENT '录入单位',
  `lrr`             varchar(100)      NULL                COMMENT '录入人',
  `lxdh`            varchar(100)      NULL                COMMENT '联系电话',
  `beizhu`          varchar(2000)     NULL                COMMENT '备注',
  `source_id`       varchar(100)      NULL                COMMENT '旧库主键（迁移溯源，本期与 id 相同）',
  `del_flag`        tinyint(1)    NOT NULL DEFAULT 0      COMMENT '删除状态：0正常 1已删除',
  `create_by`       varchar(64)       NULL                COMMENT '创建人',
  `create_time`     datetime          NULL                COMMENT '创建时间',
  `update_by`       varchar(64)       NULL                COMMENT '更新人',
  `update_time`     datetime          NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_land_crzdbh` (`crzdbh`, `del_flag`),
  KEY `idx_land_dkmc` (`dkmc`),
  KEY `idx_land_xzqh` (`xzqh`),
  KEY `idx_land_xmfl` (`xmfl`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经营性用地（出让宗地）';

-- ---------------------------------------------------------------------------
-- 数据迁移：nutzwk_ywk.xj_kjkfb_commercial_land → tj-jyxyd.t_land
-- 实测：847 条 delFlag='0'、crzdbh 无重复且无空值，可直接建唯一键。
-- 幂等：按主键 id 覆盖更新，可反复执行。
-- 注意：需 MySQL 账号对 nutzwk_ywk 有 SELECT 权限（本机 root 具备）。
-- ---------------------------------------------------------------------------
INSERT INTO `t_land` (
  `id`, `crzdbh`, `dkmc`, `xzqh`, `xmfl`, `ghydxz`, `crj`, `crsj`, `kjsydmj`, `zydmj`, `jsmj`,
  `nrcbdptf`, `wcd`, `ptsfqq`, `ptjsnr`, `srr`, `htydjfsj`, `lpmc`, `lpjfsj`, `tdzldw`,
  `tdzljhxdwjh`, `tdzljh`, `dz`, `xz`, `nz`, `bz`, `ptqkh`, `ptcbh`, `crzdtxsj`, `xzqh2`,
  `zlqsnrsm`, `lrdw`, `lrr`, `lxdh`, `beizhu`, `source_id`, `del_flag`, `create_by`, `create_time`
)
SELECT
  s.`id`, s.`crzdbh`, s.`dkmc`, s.`xzqh`, s.`xmfl`, s.`ghydxz`, s.`crj`, s.`crsj`, s.`kjsydmj`, s.`zydmj`, s.`jsmj`,
  s.`nrcbdptf`, s.`wcd`, s.`ptsfqq`, s.`ptjsnr`, s.`srr`, s.`htydjfsj`, s.`lpmc`, s.`lpjfsj`, s.`tdzldw`,
  s.`tdzljhxdwjh`, s.`tdzljh`, s.`dz`, s.`xz`, s.`nz`, s.`bz`, s.`ptqkh`, s.`ptcbh`, s.`crzdtxsj`, s.`xzqh2`,
  s.`zlqsnrsm`, s.`lrdw`, s.`lrr`, s.`lxdh`, s.`beizhu`, s.`id`,
  CASE WHEN s.`delFlag` = '1' THEN 1 ELSE 0 END, s.`createAccount`, s.`createTime`
FROM `nutzwk_ywk`.`xj_kjkfb_commercial_land` s
ON DUPLICATE KEY UPDATE
  `crzdbh`   = VALUES(`crzdbh`),
  `dkmc`     = VALUES(`dkmc`),
  `xzqh`     = VALUES(`xzqh`),
  `xmfl`     = VALUES(`xmfl`),
  `ghydxz`   = VALUES(`ghydxz`),
  `crj`      = VALUES(`crj`),
  `crsj`     = VALUES(`crsj`),
  `kjsydmj`  = VALUES(`kjsydmj`),
  `zydmj`    = VALUES(`zydmj`),
  `jsmj`     = VALUES(`jsmj`),
  `nrcbdptf` = VALUES(`nrcbdptf`),
  `wcd`      = VALUES(`wcd`),
  `ptsfqq`   = VALUES(`ptsfqq`),
  `ptjsnr`   = VALUES(`ptjsnr`),
  `srr`      = VALUES(`srr`),
  `htydjfsj` = VALUES(`htydjfsj`),
  `lpmc`     = VALUES(`lpmc`),
  `lpjfsj`   = VALUES(`lpjfsj`),
  `tdzldw`   = VALUES(`tdzldw`),
  `tdzljhxdwjh` = VALUES(`tdzljhxdwjh`),
  `tdzljh`   = VALUES(`tdzljh`),
  `dz`       = VALUES(`dz`),
  `xz`       = VALUES(`xz`),
  `nz`       = VALUES(`nz`),
  `bz`       = VALUES(`bz`),
  `ptqkh`    = VALUES(`ptqkh`),
  `ptcbh`    = VALUES(`ptcbh`),
  `crzdtxsj` = VALUES(`crzdtxsj`),
  `xzqh2`    = VALUES(`xzqh2`),
  `zlqsnrsm` = VALUES(`zlqsnrsm`),
  `lrdw`     = VALUES(`lrdw`),
  `lrr`      = VALUES(`lrr`),
  `lxdh`     = VALUES(`lxdh`),
  `beizhu`   = VALUES(`beizhu`),
  `source_id`= VALUES(`source_id`),
  `del_flag` = VALUES(`del_flag`),
  `create_by`= VALUES(`create_by`),
  `create_time` = VALUES(`create_time`);

-- 核对：应输出 847（del_flag=0）
SELECT COUNT(*) AS land_total,
       SUM(CASE WHEN del_flag = 0 THEN 1 ELSE 0 END) AS land_active
FROM `t_land`;
