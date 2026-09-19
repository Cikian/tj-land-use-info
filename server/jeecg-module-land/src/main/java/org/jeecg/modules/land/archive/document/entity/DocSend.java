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
 * @Description: 发文（方案 2.3.2 第 9 项：发文的信息记录）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>表 {@code t_doc_send}。方案原文对发文的要求只有「发文的信息记录」，
 * 旧 tj-sfw 的发文表也是纯台账（无流转、无状态），本实现保持一致：
 * <b>发文不做流转</b>，只登记 + 附件 + 可选归档。
 *
 * <p><b>修正旧系统的字段语义反置问题（R14）</b>：
 * 旧表 {@code xj_filemanage_send.file_receive_num} 名字像「收文文号」，
 * 实际存的是<b>发文文号</b>；{@code file_send_num} 反而存收文文号。
 * 新表直接用语义正确的 {@code doc_no}（发文登记号），不沿用旧名。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_doc_send")
public class DocSend implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 发文登记号（默认 FW-{yyyy}-{4位}，允许手工改写） */
    private String docNo;

    /** 文件标题 */
    private String docTitle;

    /** 文件类型 */
    private String docType;

    /** 主送单位 */
    private String toDept;

    /** 抄送单位 */
    private String ccDept;

    /** 发文日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date issueDate;

    /** 签发人 */
    private String signer;

    /** 拟稿人 */
    private String drafter;

    /** 密级 */
    private String secretLevel;

    /** 紧急程度 */
    private String urgency;

    /** 份数 */
    private Integer copies;

    // ------------------------------------------------------------------
    // 关联业务
    // ------------------------------------------------------------------

    /** 出让宗地ID */
    private String landId;

    /** 出让宗地编号（冗余） */
    private String crzdbh;

    /** 配套项目ID */
    private String facilityId;

    /** 配套项目名称（冗余） */
    private String ptxmmc;

    /** 归档后关联的档案ID */
    private String archiveId;

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

    /** 附件（详情接口返回） */
    @TableField(exist = false)
    private List<DocAttachment> attachments;

    /** 附件数（列表接口返回） */
    @TableField(exist = false)
    private Integer attachmentCount;
}
