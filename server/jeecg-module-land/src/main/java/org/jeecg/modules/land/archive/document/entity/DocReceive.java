package org.jeecg.modules.land.archive.document.entity;

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
import java.util.Date;
import java.util.List;

/**
 * @Description: 收文（方案 2.3.2 第 9 项：实现对项目的收文与发文进行管理，实现收文中心内的流转）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>表 {@code t_doc_receive}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/document/01_t_doc.sql}。
 *
 * <p><b>流转设计（精简三级 + 退回，逻辑参考旧 tj-sfw 并做了合理化优化）</b>：
 * <pre>
 *   登记(待承办) ──转办──► 承办中 ──办结──► 已办结 ──可选归档──► 已归档
 *        ▲                    │
 *        └────────退回────────┘
 * </pre>
 * 与旧系统 file_status 的对应关系：1 待流转 → 待承办，2 流转中 → 承办中，
 * 3 已退回 → 已退回，4 已办结 → 已办结。这样历史数据迁移后语义可对照。
 *
 * <p><b>旧系统的三个缺陷已修正</b>：
 * <ol>
 *   <li>旧 {@code save()} 方法体被整段注释 → 新增收文不产生流转记录。本实现登记时立即写第一条流转记录；</li>
 *   <li>旧 {@code rejectFlow()} 里 {@code getLastFlow()} 返回 null 未判空 → 必 NPE。本实现全链路判空；</li>
 *   <li>旧退回流程无 {@code @Transactional}。本实现所有流转操作都在事务内。</li>
 * </ol>
 *
 * <p><b>「当前处理人」语义</b>：{@code currentHandler} 指向<b>当前待办人</b>（打开的那条流转
 * 记录的 handler）；流转记录里 {@code handleTime} 为空的那一条就是当前待办。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_doc_receive")
public class DocReceive implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待承办 */
    public static final String STATUS_PENDING = "待承办";
    /** 状态：承办中 */
    public static final String STATUS_HANDLING = "承办中";
    /** 状态：已退回 */
    public static final String STATUS_REJECTED = "已退回";
    /** 状态：已办结 */
    public static final String STATUS_FINISHED = "已办结";
    /** 状态：已归档 */
    public static final String STATUS_ARCHIVED = "已归档";

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 收文登记号（默认 SW-{yyyy}-{4位}，允许手工改写） */
    private String docNo;

    /** 文件标题 */
    private String docTitle;

    /** 文件类型：通知/函/批复/报告/其他 */
    private String docType;

    /** 来文单位 */
    private String fromDept;

    /** 来文字号 */
    private String fromDocNo;

    /** 收文（来文）日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date receiveDate;

    /** 紧急程度：普通/急件/特急 */
    private String urgency;

    /** 密级 */
    private String secretLevel;

    /** 页数 */
    private Integer pageCount;

    /** 份数 */
    private Integer copies;

    // ------------------------------------------------------------------
    // 关联业务（先选宗地 → 再联动选配套项目）
    // ------------------------------------------------------------------

    /** 出让宗地ID */
    private String landId;

    /** 出让宗地编号（冗余） */
    private String crzdbh;

    /** 配套项目ID */
    private String facilityId;

    /** 配套项目名称（冗余） */
    private String ptxmmc;

    // ------------------------------------------------------------------
    // 流转
    // ------------------------------------------------------------------

    /** 状态 */
    private String status;

    /** 当前处理人账号 */
    private String currentHandler;

    /** 当前处理人姓名 */
    private String currentHandlerName;

    /** 办理期限 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date handleDeadline;

    /** 办结时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;

    /** 办结说明 */
    private String finishOpinion;

    /** 归档后关联的档案ID */
    private String archiveId;

    /** 备注 */
    private String remark;

    /** 删除状态 0正常 1已删除 */
    @TableLogic
    private Integer delFlag;

    /** 创建人（登记人） */
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

    /** 附件（详情接口返回） */
    @TableField(exist = false)
    private List<DocAttachment> attachments;

    /** 流转记录（详情接口返回，按时间正序） */
    @TableField(exist = false)
    private List<DocReceiveFlow> flows;

    /** 附件数（列表接口返回） */
    @TableField(exist = false)
    private Integer attachmentCount;
}
