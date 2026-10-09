package org.jeecg.modules.land.data.process.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @Description: 配套项目环节进度（29 环节录入）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>表 {@code t_facility_process}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/data/03_t_facility_process.sql}。
 *
 * <p><b>★ 为什么旧数据整表丢弃、只搬配置不搬进度</b>：
 * 旧库 {@code process_status} 那 29 行的录入人/单位/问题分别是
 * 「阿纳海姆 / 冯布朗 / 天人组织 / AAAA」「月神三号炸了 / 戴肯家叛乱了 / 吉翁不给」——
 * 明确的演示数据。迁进来只会污染台账与预警统计，
 * 设计文档 §6 也把这几张表列入「测试数据残留，迁移时应整表丢弃」。
 *
 * <p><b>★ 唯一键 (pt_id, lc_id)</b>：一个配套项目的一个环节只允许一行。
 * 旧库没有这个约束，同一 (ptid, lcid) 可以有多行，后果是
 * 「同一环节填两次，状态取哪一行不确定」。Service 层用「先查后写」保证语义，
 * 数据库层用唯一键兜底。
 *
 * <p><b>审计列命名</b>：{@code create_account} 是下划线、{@code create_time} 也是下划线，
 * 与全局 {@code map-underscore-to-camel-case} 一致，不需要 {@code @TableField}。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_facility_process")
public class FacilityProcess implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 配套项目ID（xj_kjkfb_supporting_facilities.id） */
    private String ptId;

    /** 出让宗地编号（冗余，按宗地筛查环节时不联表） */
    private String crzdbh;

    /** 环节ID（t_process_configuration.id） */
    private String lcId;

    /** 环节树路径（冗余：00010001） */
    private String lcPath;

    /** 环节名称（冗余快照） */
    private String lcName;

    /** 所属阶段ID（冗余：0001~0006） */
    private String stageId;

    /** 环节情况：未开启 / 进行中 / 已完成 / 不涉及 */
    private String lcqk;

    /** 环节开始时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date lckssj;

    /** 预计结束时间（按环节标准时长 + 非工作日日历推算） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date yjjssj;

    /** 环节实际结束时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date lcjssj;

    /** 存在问题类型 */
    private String czwtlx;

    /** 具体问题 */
    private String jtwt;

    /** 工作建议 */
    private String gzjy;

    /** 录入单位 */
    private String lrdw;

    /** 录入人 */
    private String lrr;

    /** 联系电话 */
    private String lxdh;

    /** ★历史兼容列（旧库 fileUrl），新界面不再写入 */
    private String fileUrl;

    /** 创建账号 */
    private String createAccount;

    /** 创建时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    // ==================================================================
    // 展示型字段（不入库，Service 组装）
    // ==================================================================

    /** 环节标准时长（天），来自 t_process_configuration.process_time */
    @TableField(exist = false)
    private Long processTime;

    /** 主管部门，来自 t_process_configuration.zgbm */
    @TableField(exist = false)
    private String zgbm;

    /** 同级排序号，来自 t_process_configuration.location（保证返回顺序就是业务顺序） */
    @TableField(exist = false)
    private Integer location;

    /**
     * 是否逾期。
     *
     * <p>判据与旧系统一致：<b>实际结束时间与今天，谁晚于预计结束时间</b>。
     * 已完成但超期也算逾期（旧代码就是这么判的，用于生成环节预警）。
     */
    @TableField(exist = false)
    private Boolean overdue;

    /** 逾期天数（未逾期为 0） */
    @TableField(exist = false)
    private Integer overdueDays;

    /** 本环节的附件列表（Service 按 biz_type=process 组装） */
    @TableField(exist = false)
    private List<?> attachments;
}
