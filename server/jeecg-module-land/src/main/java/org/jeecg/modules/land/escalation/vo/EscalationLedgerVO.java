package org.jeecg.modules.land.escalation.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @Description: 提级论证台账行（方案 2.3.3 第 3 项：台账方式管理，快速查看各项目当前状态）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>对应设计文档 5.6 的<b>台账 14 列</b>：
 * 序号 / 项目编号 / 项目名称 / 申报单位 / 行政区划 / 项目类型 / 总投资（亿元） /
 * 申报时间 / 材料数 / 办理状态 / 论证结果 / 最新审核意见摘要 / 最近更新时间 / 操作。
 * 其中「操作」列由前端渲染，不占后端字段；「序号」由前端按分页算出，后端另给
 * {@code seq} 便于导出与打印时也能连续编号。
 *
 * <p><b>为什么单独建 VO 而不是直接返回实体</b>：
 * 台账是给「高密度速查」用的窄视图（含 5 处展示型派生列），
 * 与录入表单需要的 30+ 字段实体不是同一个契约；单独一个 VO 可以让
 * 「台账接口返回什么」一目了然，也让导出与列表严格共用同一份列定义。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class EscalationLedgerVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 序号（按当前查询条件的行序，从 1 开始；由 Mapper 侧计算分页偏移） */
    private Integer seq;

    /** 项目ID（操作列「查看/材料下载」用） */
    private String id;

    /** 项目编号 */
    private String projectNo;

    /** 项目名称 */
    private String projectName;

    /** 申报单位 */
    private String declareDept;

    /** 行政区划 */
    private String xzqh;

    /** 功能区（界面可用「行政区划 + 功能区」合并展示） */
    private String gnq;

    /** 项目类型 */
    private String projectType;

    /** 总投资（亿元） */
    private BigDecimal totalInvestment;

    /** 申报时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date declareDate;

    /** 材料数（读主表冗余列 material_count，免子查询） */
    private Integer materialCount;

    /** 办理状态：未办理/办理中/已办结/已归档（前端按状态上色） */
    private String status;

    /** 论证结果（字典 land_escalation_arg_result） */
    private String argResult;

    /** 最新审核意见摘要（读主表冗余列 latest_opinion，超长由前端省略号截断） */
    private String latestOpinion;

    /** 最新意见时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date latestOpinionTime;

    /** 最近更新时间（无更新过则为创建时间） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 关联配套项目名称（台账可不展示，材料下载与联动排查时有用） */
    private String ptxmmc;

    /**
     * @Description: 台账顶部状态 tab 的计数项
     *
     * <p>对应 {@code GET /land/escalation/ledger/countByStatus}：
     * 前端顶部 tab 与卡片视图要显示「未办理 3 / 办理中 2 / 已办结 4 / 已归档 1」，
     * 所以这里除了数据库里实际存在的状态，还要能表达<b>数量为 0 的状态</b>——
     * 返回结构固定为 4 项（服务端补齐 0），前端不必再兜底。
     */
    @Data
    public static class StatusCount implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 状态文案（未办理/办理中/已办结/已归档） */
        private String status;

        /** 该状态下的项目数（无数据时为 0） */
        private long count;

        public StatusCount() {
        }

        public StatusCount(String status, long count) {
            this.status = status;
            this.count = count;
        }
    }
}
