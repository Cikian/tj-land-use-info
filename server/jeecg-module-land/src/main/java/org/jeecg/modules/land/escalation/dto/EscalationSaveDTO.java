package org.jeecg.modules.land.escalation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.jeecg.modules.land.escalation.entity.EscalationMaterial;

/**
 * @Description: 提级论证项目录入 / 编辑入参（主表 + 材料列表）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>对应设计文档 5.4 的三步向导：
 * ① 项目基本信息 → ② 关联地块（选填） + 提级论证信息与结果 → ③ 提级论证材料。
 *
 * <p><b>为什么不直接 {@code @RequestBody EscalationProject}</b>：
 * 实体里带着 {@code delFlag} / {@code materialCount} / {@code createBy} 这类
 * <b>只允许服务端写</b>的列，若直接用实体接参，前端就能伪造冗余计数与审计列。
 * 因此入口收窄成 DTO，服务端再逐字段搬进实体（编辑时也只覆盖 DTO 里非 null 的字段）。
 *
 * <p><b>★ 无状态机</b>：{@link #status} 与 {@link #argResult} 由前端传入，
 * 服务端只做「取值必须在允许集合内」的校验，<b>不做流转顺序校验</b>。
 */
@Data
public class EscalationSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（新增为空；编辑必填） */
    private String id;

    /** 项目编号（允许手工改写；为空时服务端自动生成 TJ-{yyyy}-{4位流水}） */
    private String projectNo;

    /** ★项目名称 */
    private String projectName;

    // ------------------------------------------------------------------
    // 基本信息
    // ------------------------------------------------------------------

    /** ★申报单位 */
    private String declareDept;

    /** ★项目类型（字典 land_escalation_project_type） */
    private String projectType;

    /** 项目规模 */
    private String projectScale;

    /** ★总投资（亿元） */
    private BigDecimal totalInvestment;

    /** ★建设地点 */
    private String buildLocation;

    /** ★申报时间
     *  ★ 必须带 @JsonFormat：前端 a-date-picker 用 valueFormat="YYYY-MM-DD" 发出纯日期字符串，
     *    没有该注解时 Jackson 会报 Unparseable date（联调实测踩坑）。 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date declareDate;

    /** ★项目概述 */
    private String projectSummary;

    // ------------------------------------------------------------------
    // 关联地块信息（选填）
    // ------------------------------------------------------------------

    /** 行政区划（16 区） */
    private String xzqh;

    /** 功能区 */
    private String gnq;

    /** 地块名称 */
    private String dkmc;

    /** 地块面积（㎡） */
    private BigDecimal dkArea;

    /** 规划用地性质 */
    private String ghydxz;

    /** 是否土地整理项目（字典 yn：1 是 / 0 否） */
    private Integer tdzlProject;

    // ------------------------------------------------------------------
    // 挂钩点
    // ------------------------------------------------------------------

    /** 关联配套项目ID（非空时触发 L1 回写 sfzsjtjlz='是'） */
    private String facilityId;

    /** 配套项目名称（冗余；前端选择配套项目时一并带上） */
    private String ptxmmc;

    /** 关联出让宗地编号 */
    private String crzdbh;

    // ------------------------------------------------------------------
    // 论证信息
    // ------------------------------------------------------------------

    /** 提级论证事由 */
    private String argReason;

    /** 提级论证依据 */
    private String argBasis;

    /** 必要性说明 */
    private String argNecessity;

    /** 可行性说明 */
    private String argFeasibility;

    /** 提级论证事项内容 */
    private String argContent;

    // ------------------------------------------------------------------
    // 论证结果（人工登记）
    // ------------------------------------------------------------------

    /** ★论证结果（字典 land_escalation_arg_result，∈ {通过, 基本通过} 时触发 L2 回写） */
    private String argResult;

    /** 论证结论 */
    private String argConclusion;

    /** 论证组织单位 */
    private String argOrg;

    /** 论证会日期（同上，需要 @JsonFormat 才能接收 yyyy-MM-dd） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date argMeetingDate;

    /** 论证专家名单 */
    private String argExpertList;

    /** 论证完成日期（同上，需要 @JsonFormat 才能接收 yyyy-MM-dd） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date argDate;

    /** 办理状态（未办理/办理中/已办结/已归档；为空时新增默认「未办理」、编辑保持原值） */
    private String status;

    /** 备注 */
    private String remark;

    // ------------------------------------------------------------------
    // 材料（第三步）
    // ------------------------------------------------------------------

    /**
     * 材料列表。
     *
     * <p>编辑时<b>以本列表为准做三向合并</b>（带 id 的视为已存在、不带 id 的视为新上传、
     * 库里存在但本列表没有的视为删除），该写法照抄档案模块的
     * {@code ArchiveServiceImpl#syncFiles}，避免前端漏传材料把已有材料全删掉。
     */
    private List<EscalationMaterial> materials;
}
