package org.jeecg.modules.land.stat.warning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @Description: 分级预警 · 预警记录（环节 / 阶段 / 总体三种口径）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>表 {@code t_process_warning}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/stat/03_t_process_warning.sql}。
 * 对应旧库 {@code nutzwk_ywk.process_warning}，但**旧库那 13 行一行都不迁**
 * （内容是「阿纳海姆 / 冯布朗 / 月神三号炸了」这类演示数据）——
 * 本表的行全部由算法在「录入环节进度」或「手动重算」时算出来。
 *
 * <p><b>★ 这是一张「算出来的快照表」，不是业务主数据</b>：
 * <ul>
 *   <li>没有 {@code del_flag}：不软删，重算时该消失的行<b>物理删除</b>。
 *       软删会让「已经补录完成了、预警还挂在列表上」变成永久问题，
 *       而预警本身就是可重算的派生数据，留删除痕迹没有业务意义。</li>
 *   <li>{@code calc_time} 记录本次计算时刻：用户看到的数字属于这个时刻，
 *       重算之前数据变了它也不会自己动（没有事件流，也不做定时任务）。</li>
 * </ul>
 *
 * <p><b>★ 唯一键 {@code uk_pw_key (pt_id, yj_type, lc_id)} 是幂等的基石</b>：
 * 一个配套项目的一个环节 / 一个阶段 / 该项目总体**只允许一条当前预警**。
 * 重算按它 upsert：等级降了是更新，不再逾期是删除。
 * 旧系统没有这个约束（先 query 再 insert/update，且分支写漏了一处），
 * 后果是同一环节能堆出多条预警，列表里重复、导出里重复计数。
 * {@code lc_id} 对总体预警用空串而不是 NULL —— MySQL 唯一键允许多个 NULL，
 * 若允许 NULL，同一项目的总体预警就能被插无数次。
 *
 * <p><b>★ 冗余列的取舍</b>：{@code pt_name / crzdbh / xzqh / xzqh2 / dkmc / lc_name}
 * 都是快照。配套改名后，历史预警仍显示当时的名称——这是有意的：
 * 预警是「当时算出来的结论」，跟着主数据漂移会让对账没法做。
 * 另外这些列让列表查询不必联四张表（1433 条配套 × 环节，联表代价不划算）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_process_warning")
public class ProcessWarning implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预警ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 预警名称：{@code pt_name + "预警分析"}（旧系统口径，三类共用） */
    private String yjName;

    /**
     * 预警分类：环节预警 / 阶段预警 / 总体预警。
     *
     * <p>它与 {@code lcId} 的组合决定唯一键，所以取值必须**逐字**用这三个词
     * （前端筛选也按同样的词传参），见 {@code WarningFields}。
     */
    private String yjType;

    /** 配套项目ID → {@code t_supporting_facilities.id} */
    private String ptId;

    /** 配套项目名称（冗余快照，改名后预警仍显示当时名称） */
    private String ptName;

    /** 出让宗地编号（冗余，按宗地下钻用） */
    private String crzdbh;

    /** 行政区划（冗余，取 t_land.xzqh） */
    private String xzqh;

    /** 区级/录入单位区划（冗余，取 t_land.xzqh2；配套表没有这一列，必须从宗地带过来） */
    private String xzqh2;

    /** 地块名称（冗余快照：优先取配套表的 dkmc，为空时回落宗地表） */
    private String dkmc;

    /** 配套项目名称（与 ptName 同值；独立列是为了和旧接口入参 ptxmmc 对齐） */
    private String ptxmmc;

    /** 环节/阶段ID → {@code t_process_configuration.id}；**总体预警为空串** */
    private String lcId;

    /** 环节/阶段名称（冗余快照） */
    private String lcName;

    /** 所属阶段ID（环节预警用来定位所属阶段；阶段预警填阶段自身 id） */
    private String stageId;

    /** 预警等级ID → {@code t_process_warning_level.id} */
    private String levelId;

    /** 预警等级别名（冗余：blue/yellow/orange/red，前端着色直接用） */
    private String levelCode;

    /** 预警等级名称（冗余） */
    private String levelName;

    /**
     * 预警值（比率）：1.1000 = 110%，展示时 ×100 保留 1 位。
     *
     * <p>用 decimal(10,4) 而不是旧库的 decimal(10,2)：两位小数把 1.105 存成 1.11，
     * 展示成 111.0% 与真实 110.5% 差 0.5 个点；重算比对时还会因四舍五入抖动误判「值变了」。
     */
    private BigDecimal yjValue;

    /** 逾期天数（工作日，已剔除周末与节假日） */
    private Integer overdueDays;

    /** 标准办理时长（工作日）= 预警值的分母，留档便于复核 */
    private Integer stdDays;

    /** 实际历时（工作日）= 预警值的分子，留档便于复核 */
    private Integer actualDays;

    /** 存在问题类型（口径同 {@code t_facility_process.czwtlx} / 字典 land_process_issue） */
    private String problemType;

    /** 具体情况（自由文本，取 {@code t_facility_process.jtwt}） */
    private String problemDetail;

    /**
     * 决策意见（按「问题类型 × 预警等级」从 {@code t_decision_opinion} 取，可为空）。
     *
     * <p><b>只有环节预警有值</b>：阶段/总体预警没有对应的「问题类型」，
     * 硬套一条通用话术会让用户以为「这个阶段的问题是资金问题」。
     */
    private String decisionOpinion;

    /** 责任部门（取环节配置 {@code t_process_configuration.zgbm}） */
    private String zrbm;

    /** 本次计算时间（时效提示：预警是「重算出来的」，不是事件流） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date calcTime;

    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
