package org.jeecg.modules.land.stat.warning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 分级预警 · 决策意见（问题类型 × 预警等级 → 处理建议）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>表 {@code t_decision_opinion}，建表与种子脚本
 * {@code jeecg-module-land/src/main/resources/sql/stat/02_t_decision_opinion.sql}。
 * 对应旧库 {@code decision_opinion}（旧列 wtlxid / yjdjid / jcyj）。
 *
 * <p><b>为什么按「问题类型 × 等级」定位而不是按等级一句话</b>：
 * 同样是逾期 25 天，「资金问题」要找财政局、「管线问题」要找管线权属单位，
 * 处置动作完全不同。旧代码 {@code ProcessStatusController.createSpecial} L178-183
 * 就是这么取的。
 *
 * <p><b>★ 与旧库的三处有意差别</b>：
 * <ol>
 *   <li><b>用文本存问题类型，不用 sys_dict 的 id</b>：旧代码要先按名称查字典项拿 id
 *       （{@code dictService.query(...).get(0)} —— 字典项不存在时直接抛
 *       IndexOutOfBounds，导致整个环节保存失败）。新系统
 *       {@code t_facility_process.czwtlx} 存的就是名称文本，中间那层 id
 *       只增加一次查库和两个失败点。</li>
 *   <li><b>支持通用兜底意见</b>：{@code problemType} 为空串表示「该等级下所有
 *       问题类型的兜底意见」。匹配顺序：精确 (problem_type, level_id) →
 *       兜底 ('', level_id) → 无意见（null）。旧系统没有兜底，
 *       5 类 × 4 级 = 20 格里漏配一格，那一格就永远没有建议（静默失效）。</li>
 *   <li><b>表名加 t_ 前缀</b>（新库命名约定），旧表名本身没拼错。</li>
 * </ol>
 *
 * <p>{@code opinion} 给到 1000 字符：旧库 {@code jcyj} 就是 varchar(1000)，
 * 实际中心会写一段处置流程（3~5 句），200 字经常不够。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_decision_opinion")
public class DecisionOpinion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 决策意见ID（新增由 MP 生成 snowflake；种子行的固定 id 原样保留） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 问题类型（口径同字典 {@code land_process_issue}：审批/资金/权属/管线/地质问题）。
     *
     * <p><b>空串 = 通用兜底意见</b>（不是 null）：字典列上不允许 null，
     * 且唯一判重、匹配都靠 `= ''` 而不是 `IS NULL`。
     */
    private String problemType;

    /** 预警等级ID → {@link ProcessWarningLevel#getId()} */
    private String levelId;

    /** 预警等级名称（冗余，列表直接展示免联表） */
    private String levelName;

    /** 决策意见正文：该问题类型在该等级下的处理建议 */
    private String opinion;

    /** 排序号（数值小在前） */
    private Integer sortNo;

    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 删除标识：0 正常 / 1 已删除。
     *
     * <p>同 {@link ProcessWarningLevel#getDelFlag()}：实体查询靠 {@code @TableLogic}，
     * 手写 SQL 必须自己带 {@code del_flag = 0}。
     */
    @TableLogic
    private Integer delFlag;
}
