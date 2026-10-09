package org.jeecg.modules.land.escalation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 提级论证审核意见 / 办理记录（★ append-only，方案 2.3.3 第 4 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>表 {@code t_escalation_record}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/escalation/01_t_escalation.sql}。
 *
 * <p><b>★ append-only 是本模块「可追溯性」的技术保证</b>（痛点原文「可追溯性不足」）：
 * 本表<b>只 INSERT</b>。因此配套的 Controller 只有 {@code record/list} 与
 * {@code record/add} 两个接口，<b>没有</b> edit / delete 接口（设计文档 4.2 注）。
 *
 * <p>登记一条意见后，在<b>同一事务</b>内回写主表三个冗余列
 * （{@code latest_opinion} / {@code latest_opinion_time} / {@code latest_opinion_by}）
 * 与 {@code record_count}，供台账「最新审核意见」列直接读取，免子查询。
 *
 * <p><b>没有节点语义</b>：本表不是审批流转表，记录类型只有
 * 「审核意见 / 补充说明 / 其他」三种（见 {@code RecordTypeEnum}）。
 * {@link #nodeCode} / {@link #seqNo} 是二期上流程时的预留列，本期恒为 NULL。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_escalation_record")
public class EscalationRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 提级论证项目ID → t_escalation_project.id */
    private String projectId;

    /** 记录类型：审核意见/补充说明/其他（见 RecordTypeEnum） */
    private String recordType;

    /**
     * 结论短语：同意/基本同意/请补充材料/需进一步论证/不同意（可空）。
     *
     * <p>这是原型界面上的「快捷短语按钮」回填值，落库后便于按结论做检索，
     * 与 {@code t_escalation_project.arg_result} 是两回事：
     * 前者是<b>意见附件结论</b>（一条意见一个），后者是<b>项目最终论证结果</b>（项目一个）。
     */
    private String action;

    /** ★意见正文（前端限 1000 字，后端按 2000 字符的列容量再兜一层） */
    private String opinion;

    /** 附件ID列表（逗号分隔 → t_escalation_material.id） */
    private String attachmentIds;

    /** 记录人账号 */
    private String recorderId;

    /** 记录人姓名 */
    private String recorderName;

    /** 记录人部门 */
    private String recorderDept;

    /** 记录时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date recordTime;

    /** 【预留】二期流程化后的节点编码（本期恒为 NULL） */
    private String nodeCode;

    /** 【预留】二期流程化后的节点顺序（本期恒为 NULL） */
    private Integer seqNo;
}
