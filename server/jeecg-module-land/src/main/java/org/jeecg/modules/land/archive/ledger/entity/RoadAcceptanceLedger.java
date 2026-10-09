package org.jeecg.modules.land.archive.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @Description: 道路设施验收及移交资料台账（方案 2.3.2 第 7 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>表 {@code t_road_acceptance_ledger}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/ledger/01_t_road_acceptance_ledger.sql}。
 *
 * <p><b>★ 这个功能在旧系统里不存在</b>（旧代码全文检索 0 命中、旧库无表、旧菜单无此项），
 * 属于方案 2.3.2 第 7 项的全新开发；但**有旧数据要迁** ——
 * 以旧配套项目表 {@code t_supporting_facilities} 的道路类项目为底账
 * 生成台账初始记录（见 {@code sql/ledger/05_migrate_road_facilities.sql}）。
 * 因此本实体上有两个「迁移专用」列（{@link #sourceFacilityId} / {@link #facilityId}），
 * 它们共同保证「一条配套项目 ↔ 一条台账」可追溯、可幂等重跑。
 *
 * <p><b>★ 13 类资料的勾选字段</b>（{@link #hasSgxk} … {@link #hasYjwj}）是台账的核心：
 * 列名、驼峰属性名、中文名的对应关系统一登记在
 * {@link org.jeecg.modules.land.archive.ledger.enums.LedgerMaterial} 里，
 * 统计归集数（{@link #materialCount}）也只在那里算一次。
 *
 * <p><b>★ 附件口径</b>：资料原件不存本表，统一挂档案管理
 * {@code t_archive / t_archive_file}，本表只留 {@link #archiveId} 指针 + {@link #archiveCount} 计数。
 * 「该道路下的全部档案」由台账 id 反查（见 {@link #relatedArchives}，非表字段）。
 *
 * <p><b>★ 为什么列表接口直接返回实体、而没有像提级论证那样单独建 LedgerVO</b>：
 * 提级论证的台账是「14 列窄视图」，与 30+ 字段的录入实体不是同一个契约；
 * 而本模块**台账页就是主页面**，列表要展示的几乎就是全部字段（含 13 列勾选矩阵），
 * 再建一个 40 字段的 VO 只是把同一份字段抄一遍，反而制造「列表与导出取数不一致」的风险。
 * 因此这里用实体 + 两个非表字段（{@link #seq} 序号、{@link #relatedArchives} 关联档案）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_road_acceptance_ledger")
public class RoadAcceptanceLedger implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    // ==================================================================
    // 台账标识
    // ==================================================================

    /** ★台账编号（自动生成 YS-{yyyy}-{4位流水}，允许手工改写；唯一键 uk_ral_no 是单列） */
    private String ledgerNo;

    // ==================================================================
    // 道路 / 项目基本信息
    // ==================================================================

    /** ★道路名称（= 配套项目名称 ptxmmc） */
    private String roadName;

    /** 行政区划（16 区；旧数据的「生态城/经开区/…」等 192 条功能区值已拆到 {@link #gnq}） */
    private String xzqh;

    /** 功能区（生态城/土地发展中心/经开区/高新区/保税区） */
    private String gnq;

    /** 出让宗地编号 → t_land.crzdbh */
    private String crzdbh;

    /** 出让宗地ID → t_land.id（冗余，便于直达档案） */
    private String landId;

    /** 地块名称（冗余，迁移时由 crzdbh 反查 t_land） */
    private String dkmc;

    /** 配套设施类别（道路/市政道路/道路及管线…） */
    private String ptsslb;

    /** 配套项目ID → t_supporting_facilities.id */
    private String facilityId;

    /** 配套项目名称（冗余，与 roadName 同值） */
    private String ptxmmc;

    /** 道路等级 */
    private String dldj;

    /** 建设单位 */
    private String jsdw;

    /** 施工单位 */
    private String sgdw;

    /** 监理单位 */
    private String jldw;

    // ==================================================================
    // 验收信息（迁移时留空——旧库 process_status 里竣工/移交环节实测 0 条，无进度可迁）
    // ==================================================================

    /** 验收类型（字典 land_road_acceptance_type） */
    private String acceptanceType;

    /** 验收单编号 */
    private String acceptanceNo;

    /** 验收日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date acceptanceDate;

    /** 验收组织单位 */
    private String acceptanceOrg;

    /** 验收结果（字典 land_road_acceptance_result） */
    private String acceptanceResult;

    /** 实际竣工日期（迁移自旧表 sjjgsj） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date completeDate;

    // ==================================================================
    // ★ 13 类资料勾选矩阵（列名/中文名/取值方式见 LedgerMaterial）
    // ==================================================================

    /** 资料01 施工许可证（旧字段 sgxk） */
    private Integer hasSgxk;

    /** 资料02 竣工验收报告 */
    private Integer hasYsbg;

    /** 资料03 竣工图测 */
    private Integer hasJgtc;

    /** 资料04 质量监督报告 */
    private Integer hasZljdbg;

    /** 资料05 材料检验报告 */
    private Integer hasCljybg;

    /** 资料06 安全监督报告 */
    private Integer hasAjbg;

    /** 资料07 规划验收合格证 */
    private Integer hasGhyshgz;

    /** 资料08 竣工验收备案表 */
    private Integer hasJgbaba;

    /** 资料09 档案专项验收意见 */
    private Integer hasDazxys;

    /** 资料10 道路工程移交单 */
    private Integer hasDlyjd;

    /** 资料11 养护协议 */
    private Integer hasYhxy;

    /** 资料12 竣工文件（旧附件目录 13-竣工文件） */
    private Integer hasJgwj;

    /** 资料13 移交文件（旧附件目录 14-移交文件） */
    private Integer hasYjwj;

    // ==================================================================
    // 移交信息
    // ==================================================================

    /** 移交单位（通常 = 建设单位；仅已移交时写值） */
    private String handoverUnit;

    /** 接收管养单位（旧字段 jsgydw） */
    private String receiveUnit;

    /** 移交日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date handoverDate;

    // ==================================================================
    // 状态与档案关联
    // ==================================================================

    /** 状态：未验收/验收中/已验收/已移交（见 LedgerStatus，不入字典） */
    private String status;

    /** 关联档案 → t_archive.id（资料原件挂档案，本表只留指针） */
    private String archiveId;

    /** 关联档案数（冗余，服务端维护；由 facilityId/crzdbh 反查 t_archive 得到） */
    private Integer archiveCount;

    // ==================================================================
    // 迁移与冗余统计
    // ==================================================================

    /** 迁移来源：旧配套项目ID（★ 迁移幂等键，唯一键 uk_ral_source；人工新增为 NULL） */
    private String sourceFacilityId;

    /** 已归集资料份数（13 列勾选之和，冗余；服务端用 LedgerMaterial.countMaterials 重算） */
    private Integer materialCount;

    /** 备注（迁移时只给「宗地在 t_land 里找不到」的孤儿行写提示） */
    private String remark;

    /** 删除状态 0正常 1已删除 */
    @TableLogic
    private Integer delFlag;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    // ==================================================================
    // 非表字段
    // ==================================================================

    /**
     * 序号（按当前查询条件的行序，从 1 开始）。
     *
     * <p>不在 SQL 里算：MySQL 5.7 没有 {@code ROW_NUMBER()}，用用户变量又会被
     * 分页插件的 COUNT 优化搅乱（提级论证模块已踩过），因此由 Service 层
     * 用「分页偏移 + 行号」赋值。
     */
    @TableField(exist = false)
    private Integer seq;

    /**
     * 关联档案列表（详情接口返回；列表接口为 null）。
     *
     * <p>优先按 {@link #facilityId} 反查 {@code t_archive}，查不到再按 {@link #crzdbh} 兜底
     * —— 与档案管理模块用「先选宗地→再选配套项目」的口径一致。
     */
    @TableField(exist = false)
    private List<org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO> relatedArchives;

    /** 13 类资料里已归集的数量（= {@link #materialCount}，详情页展示「资料 3/13」用） */
    @TableField(exist = false)
    private Integer materialTotal;

    /** 13 类资料里缺失的数量（= 13 - {@link #materialCount}，台账页「待补资料」列用） */
    @TableField(exist = false)
    private Integer materialMissing;
}
