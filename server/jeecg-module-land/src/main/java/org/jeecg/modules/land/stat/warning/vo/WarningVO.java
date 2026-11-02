package org.jeecg.modules.land.stat.warning.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @Description: 分级预警 · 列表一行（合同 §5.1，字段名逐字对前端）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>由 {@code ProcessWarningMapper.xml} 直接映射（{@code resultType}），
 * 所以**字段名与别名一一对应**，改字段名要同时改 XML。
 *
 * <p><b>★ {@code yjValue} 与 {@code yjPercent} 都给</b>：
 * {@code yjValue} 是库里的比率（1.1000 = 110%，4 位小数），
 * {@code yjPercent} 是已 ×100 且只保留 1 位的展示值（110.0）。
 * 前端**不要自己乘**——两边各乘一次就会出现 11000%，
 * 而且后端乘的那次用的是 decimal 四舍五入，与 JS 浮点结果不会完全一致。
 *
 * <p>{@code calcTime} 用字符串而不是日期：预警是快照数据，
 * 前端只需要原样显示「这条数是哪个时刻算的」，不需要按它排序或再做时区换算。
 */
@Data
public class WarningVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预警ID */
    private String id;

    /** 预警名称：{配套项目名称}预警分析 */
    private String yjName;

    /** 预警分类：环节预警 / 阶段预警 / 总体预警 */
    private String yjType;

    /** 配套项目ID */
    private String ptId;

    /** 配套项目名称 */
    private String ptName;

    /** 配套项目名称（与 ptName 同值，保留独立字段与旧接口入参口径对齐） */
    private String ptxmmc;

    /** 出让宗地编号 */
    private String crzdbh;

    /** 行政区划 */
    private String xzqh;

    /** 区级/录入单位区划（来自宗地表，配套表没有这一列） */
    private String xzqh2;

    /** 地块名称 */
    private String dkmc;

    /** 环节/阶段ID（总体预警为空串） */
    private String lcId;

    /** 环节/阶段名称 */
    private String lcName;

    /** 所属阶段ID */
    private String stageId;

    /** 预警等级ID */
    private String levelId;

    /** 预警等级别名（前端着色令牌） */
    private String levelCode;

    /** 预警等级名称 */
    private String levelName;

    /** 预警值（比率，4 位小数；1.1000 = 110%） */
    private BigDecimal yjValue;

    /** 预警值（已 ×100，1 位小数；前端直接拼「%」） */
    private BigDecimal yjPercent;

    /** 逾期天数（工作日） */
    private Integer overdueDays;

    /** 标准办理时长（工作日） */
    private Integer stdDays;

    /** 实际历时（工作日） */
    private Integer actualDays;

    /** 问题类型（仅环节预警有值） */
    private String problemType;

    /** 具体情况（仅环节预警有值） */
    private String problemDetail;

    /** 决策意见（仅环节预警有值，可能为空） */
    private String decisionOpinion;

    /** 责任部门 */
    private String zrbm;

    /** 计算时间（yyyy-MM-dd HH:mm:ss） */
    private String calcTime;
}
