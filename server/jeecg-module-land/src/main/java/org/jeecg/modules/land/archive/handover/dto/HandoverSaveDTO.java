package org.jeecg.modules.land.archive.handover.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @Description: 道路交付及养护协议移交事项-新增/编辑入参
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>新增与编辑共用（差别只在「新增校验必填、编辑逐字段覆盖」，与台账模块一致）。
 *
 * <p><b>★ 迁移专用字段不在本 DTO 里</b>：{@code sourceFacilityId}（迁移幂等键）不提供录入入口，
 * 免得人工编辑把它改坏、导致重跑迁移时插出重复记录。
 */
@Data
public class HandoverSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（编辑必传） */
    private String id;

    /** 移交编号（空则自动生成 YJ-{yyyy}-{4位}） */
    private String handoverNo;

    /** ★道路名称 */
    private String roadName;

    /** 道路编号 */
    private String roadCode;

    /** 道路等级 */
    private String dldj;

    /** 起点 */
    private String startPoint;

    /** 终点 */
    private String endPoint;

    /** 长度(米) */
    private BigDecimal lengthM;

    /** 红线宽度(米) */
    private BigDecimal redLineWidth;

    /** 行政区划 */
    private String xzqh;

    /** 功能区 */
    private String gnq;

    /** 配套设施类别 */
    private String ptsslb;

    /** 出让宗地编号 */
    private String crzdbh;

    /** 出让宗地ID */
    private String landId;

    /** 地块名称 */
    private String dkmc;

    /** 配套项目ID */
    private String facilityId;

    /** 配套项目名称 */
    private String ptxmmc;

    /** 移交类型（字典 land_road_handover_type） */
    private String handoverType;

    /** 协议编号 */
    private String agreementNo;

    /** 协议名称 */
    private String agreementName;

    /** 协议签订日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date agreementDate;

    /** 建设单位 */
    private String buildUnit;

    /** 接收管养单位 */
    private String receiveUnit;

    /** 实际移交日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date handoverDate;

    /** 养护起始日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date maintenanceStart;

    /** 养护截止日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date maintenanceEnd;

    /** 状态（待移交/移交中/已移交；空则沿用旧值，新增时取默认「待移交」） */
    private String status;

    /** 备注 */
    private String remark;
}
