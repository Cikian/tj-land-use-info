package org.jeecg.modules.land.escalation.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 提级论证-登记审核意见入参（append-only 意见的唯一入口）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>对应设计文档 4.2 的 {@code POST /land/escalation/record/add} 与 5.3 的登记面板。
 *
 * <p><b>登记一条意见会做什么</b>（同一个事务内）：
 * <ol>
 *   <li>INSERT 一条 {@code t_escalation_record}（只增不改不删）；</li>
 *   <li>回写主表 {@code latest_opinion} / {@code latest_opinion_time} /
 *       {@code latest_opinion_by} 与 {@code record_count}；</li>
 *   <li><b>仅当</b> {@link #status} 非空时才顺带更新主表的 {@code status} ——
 *       对应界面上的「☐ 同时更新办理状态」，<b>默认不勾选</b>，
 *       避免登记一条意见就把办理状态改掉（设计文档 2.3「不做意见驱动的状态流转」）。</li>
 * </ol>
 *
 * <p>{@link #recorderId} / {@link #recorderName} / {@link #recorderDept} 一律
 * <b>由服务端从当前登录用户取</b>（前端不传也以服务端为准），
 * 否则留痕可以伪造，append-only 就失去意义。
 */
@Data
public class EscalationRecordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提级论证项目ID（必填） */
    private String projectId;

    /** 记录类型：审核意见/补充说明/其他；为空时默认「审核意见」 */
    private String recordType;

    /** 结论短语：同意/基本同意/请补充材料/需进一步论证/不同意（可空） */
    private String action;

    /** ★意见正文（前端限 1000 字；后端再按列容量兜一层） */
    private String opinion;

    /** 附件ID列表（逗号分隔 → t_escalation_material.id，可空） */
    private String attachmentIds;

    /**
     * 可选：同时把主表办理状态更新为此值（未办理/办理中/已办结/已归档）。
     *
     * <p><b>为空 = 不改状态</b>，这是默认行为；非空时校验取值合法性。
     * 服务端不做任何流转顺序校验（无状态机）。
     */
    private String status;

    // ------------------------------------------------------------------
    // 记录人信息（服务端覆盖，前端传了也不采信）
    // ------------------------------------------------------------------

    /** 记录人账号（服务端从 LoginUser 取） */
    private String recorderId;

    /** 记录人姓名（服务端从 LoginUser 取） */
    private String recorderName;

    /** 记录人部门（服务端尽力取，取不到为 null） */
    private String recorderDept;
}
