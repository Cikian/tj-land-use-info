package org.jeecg.modules.land.escalation.entity;

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
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @Description: 提级论证项目主表（方案 2.3.3 第 1/2/3/4 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>表 {@code t_escalation_project}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/escalation/01_t_escalation.sql}。
 *
 * <p><b>★ 本模块是「记录型台账」不是流程引擎</b>（设计文档第一、二章）：
 * 实际提级论证流程在线下办理，有答复后由操作人员手工把信息录回本系统。
 * 因此：
 * <ul>
 *   <li>{@link #status} 与 {@link #argResult} 都<b>由前端传入</b>，后端只校验取值合法，
 *       <b>不做任何流转顺序校验</b>；</li>
 *   <li>「进展」拆成两个字段而不是一个：{@code status} 表办理进度，
 *       {@code arg_result} 表论证结论 —— 这样台账能做「状态 × 结果」二维筛选，
 *       也避免出现「审批中但结果不通过」这种自相矛盾的记录（设计文档 2.2）。</li>
 * </ul>
 *
 * <p><b>管理对象是「提级论证事项/项目」，不是「未出让地块」</b>（设计文档 1.3 考证）：
 * 地块相关字段（{@link #xzqh} / {@link #gnq} / {@link #dkmc} / {@link #dkArea} /
 * {@link #ghydxz} / {@link #crzdbh}）全部<b>可空</b>，作为「关联地块信息（选填）」处理。
 *
 * <p>三个冗余列（{@link #materialCount} / {@link #recordCount} /
 * {@link #latestOpinion} 系列）由服务端维护，供台账页免子查询直接读取。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_escalation_project")
public class EscalationProject implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 论证结果：通过（会触发 L2 回写 tjlzsftg='是'）。
     * 取值与字典 {@code land_escalation_arg_result} 一致。
     */
    public static final String ARG_RESULT_PASS = "通过";
    /** 论证结果：基本通过（同样触发 L2 回写） */
    public static final String ARG_RESULT_BASIC_PASS = "基本通过";
    /** 论证结果：需补充材料 */
    public static final String ARG_RESULT_NEED_MATERIAL = "需补充材料";
    /** 论证结果：需进一步论证 */
    public static final String ARG_RESULT_NEED_FURTHER = "需进一步论证";
    /** 论证结果：不通过 */
    public static final String ARG_RESULT_REJECT = "不通过";

    /**
     * 全部合法论证结果。
     *
     * <p>取值与字典 {@code land_escalation_arg_result} 对齐，但后端仍固定一份常量做校验：
     * 字典是<b>给前端下拉用</b>的，「写入库的值是否合法」必须由后端把关，
     * 否则字典被后台改坏就会写入脏数据（设计文档 4.3 第 1 条）。
     */
    public static final List<String> ARG_RESULT_VALUES = Collections.unmodifiableList(
            Arrays.asList(ARG_RESULT_PASS, ARG_RESULT_BASIC_PASS, ARG_RESULT_NEED_MATERIAL,
                    ARG_RESULT_NEED_FURTHER, ARG_RESULT_REJECT));

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 项目编号（自动生成 TJ-{yyyy}-{4位流水}，允许手工改写；唯一键 uk_esc_no 是单列） */
    private String projectNo;

    /** ★项目名称 */
    private String projectName;

    // ------------------------------------------------------------------
    // 基本信息
    // ------------------------------------------------------------------

    /** ★申报单位 */
    private String declareDept;

    /** ★项目类型（字典 land_escalation_project_type：产业类/基础设施类/民生类/其他） */
    private String projectType;

    /** 项目规模 */
    private String projectScale;

    /** ★总投资（亿元） */
    private BigDecimal totalInvestment;

    /** ★建设地点 */
    private String buildLocation;

    /** ★申报时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date declareDate;

    /** ★项目概述 */
    private String projectSummary;

    // ------------------------------------------------------------------
    // 关联地块信息（★ 全选填，见类注释 1.3 考证）
    // ------------------------------------------------------------------

    /** 行政区划（16 区） */
    private String xzqh;

    /** 功能区（生态城/经开区/高新区/保税区…） */
    private String gnq;

    /** 地块名称 */
    private String dkmc;

    /** 地块面积（㎡） */
    private BigDecimal dkArea;

    /** 规划用地性质 */
    private String ghydxz;

    /** 是否土地整理项目（字典 yn，1=是 / 0=否）—— 用 Integer 存 tinyint(1) */
    private Integer tdzlProject;

    // ------------------------------------------------------------------
    // 与主业务系统的挂钩点（回写见 EscalationProjectServiceImpl.writebackFacility）
    // ------------------------------------------------------------------

    /** 关联配套项目ID → xj_kjkfb_supporting_facilities.id */
    private String facilityId;

    /** 配套项目名称（冗余，台账展示用，避免 JOIN 旧设施表） */
    private String ptxmmc;

    /** 关联出让宗地编号 → t_land.crzdbh（可空） */
    private String crzdbh;

    // ------------------------------------------------------------------
    // 论证信息
    // ------------------------------------------------------------------

    /** 提级论证事由 */
    private String argReason;

    /** 提级论证依据（政策/规划） */
    private String argBasis;

    /** 必要性说明 */
    private String argNecessity;

    /** 可行性说明 */
    private String argFeasibility;

    /** 提级论证事项内容 */
    private String argContent;

    // ------------------------------------------------------------------
    // 论证结果（人工登记，非流程产生）
    // ------------------------------------------------------------------

    /** ★论证结果（字典 land_escalation_arg_result，见 {@link #ARG_RESULT_VALUES}） */
    private String argResult;

    /** 论证结论 */
    private String argConclusion;

    /** 论证组织单位 */
    private String argOrg;

    /** 论证会日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date argMeetingDate;

    /** 论证专家名单 */
    private String argExpertList;

    /** 论证完成日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date argDate;

    // ------------------------------------------------------------------
    // 办理状态（人工登记，固定 4 值，见 EscalationStatus）
    // ------------------------------------------------------------------

    /** 办理状态：未办理/办理中/已办结/已归档 */
    private String status;

    /** 最新审核意见摘要（冗余，台账「最新审核意见」列直接读，服务端维护） */
    private String latestOpinion;

    /** 最新意见时间（服务端维护） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date latestOpinionTime;

    /** 最新意见记录人（服务端维护） */
    private String latestOpinionBy;

    // ------------------------------------------------------------------
    // 归档（本期预留，不做联动）
    // ------------------------------------------------------------------

    /** 预留：归档后关联 t_archive.id（本期不写，归档由档案管理模块单独办理） */
    private String archiveId;

    // ------------------------------------------------------------------
    // 冗余计数（服务端维护）
    // ------------------------------------------------------------------

    /** 材料数（冗余，材料增删后由 EscalationMaterialServiceImpl 重算） */
    private Integer materialCount;

    /** 意见记录数（冗余，登记意见后由 EscalationRecordServiceImpl 重算） */
    private Integer recordCount;

    /** 备注 */
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

    // ------------------------------------------------------------------
    // 非表字段
    // ------------------------------------------------------------------

    /** 提级论证材料列表（详情接口与录入向导回显用；列表接口为 null） */
    @TableField(exist = false)
    private List<EscalationMaterial> materials;

    /** 审核意见记录（详情接口可带；列表接口为 null，append-only 只读展示） */
    @TableField(exist = false)
    private List<EscalationRecord> records;
}
