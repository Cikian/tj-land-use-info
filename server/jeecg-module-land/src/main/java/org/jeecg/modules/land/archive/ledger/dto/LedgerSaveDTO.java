package org.jeecg.modules.land.archive.ledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 道路设施验收及移交资料台账-新增/编辑入参
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p><b>为什么新增与编辑共用一个 DTO</b>：两者字段完全相同，差别只在
 * 「新增时校验必填、编辑时逐字段覆盖」（见 Service 的注释）。
 * 与提级论证模块用 {@code EscalationSaveDTO} 同时服务 add/edit 是同一处理。
 *
 * <p><b>★ 13 类资料用 0/1 整型传值</b>（而不是 boolean）：与库里的 {@code tinyint(1)} 一一对应，
 * 前端勾选框直接绑 {@code 0/1}，避免 boolean ↔ tinyint 的隐式转换在多处出现。
 * {@code null} 视为 0（未勾选）。
 *
 * <p><b>★ 迁移字段不可编辑</b>：{@code sourceFacilityId} 只在迁移脚本里写，
 * 页面不提供入口，因此本 DTO 里没有它 —— 这样「手工改台账」永远不会污染迁移幂等键。
 */
@Data
public class LedgerSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（编辑必传；新增忽略） */
    private String id;

    /** 台账编号（空则自动生成 YS-{yyyy}-{4位}；非空则校验唯一） */
    private String ledgerNo;

    // ------------------------------------------------------------------
    // 道路 / 项目基本信息
    // ------------------------------------------------------------------

    /** ★道路名称 */
    private String roadName;

    /** 行政区划（16 区） */
    private String xzqh;

    /** 功能区 */
    private String gnq;

    /** 出让宗地编号 */
    private String crzdbh;

    /** 出让宗地ID（前端选中宗地后回传，便于直达档案） */
    private String landId;

    /** 地块名称（可由宗地自动带出，也可手工填写） */
    private String dkmc;

    /** 配套设施类别 */
    private String ptsslb;

    /** 配套项目ID（来自 t_supporting_facilities） */
    private String facilityId;

    /** 配套项目名称（冗余） */
    private String ptxmmc;

    /** 道路等级 */
    private String dldj;

    /** 建设单位 */
    private String jsdw;

    /** 施工单位 */
    private String sgdw;

    /** 监理单位 */
    private String jldw;

    // ------------------------------------------------------------------
    // 验收信息
    // ------------------------------------------------------------------

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

    /** 实际竣工日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date completeDate;

    // ------------------------------------------------------------------
    // ★ 13 类资料勾选（0/1；null 视为 0）
    // ------------------------------------------------------------------

    /** 资料01 施工许可证 */
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

    /** 资料12 竣工文件 */
    private Integer hasJgwj;

    /** 资料13 移交文件 */
    private Integer hasYjwj;

    // ------------------------------------------------------------------
    // 移交信息
    // ------------------------------------------------------------------

    /** 移交单位 */
    private String handoverUnit;

    /** 接收管养单位 */
    private String receiveUnit;

    /** 移交日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date handoverDate;

    // ------------------------------------------------------------------
    // 状态与档案
    // ------------------------------------------------------------------

    /** 状态（未验收/验收中/已验收/已移交；空则沿用旧值，新增时取默认「未验收」） */
    private String status;

    /** 关联档案ID（t_archive.id；留空表示不关联/取消关联） */
    private String archiveId;

    /** 备注 */
    private String remark;
}
