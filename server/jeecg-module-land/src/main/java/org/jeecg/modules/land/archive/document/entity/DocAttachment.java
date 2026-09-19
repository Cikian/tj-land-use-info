package org.jeecg.modules.land.archive.document.entity;

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
 * @Description: 收发文附件
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>表 {@code t_doc_attachment}。
 *
 * <p><b>为什么要有这张表</b>：旧 tj-sfw 把多个附件用逗号拼在一个
 * {@code file_path varchar(5000)} 字段里，导致
 * ① 文件名里的逗号会把一条记录拆成两个假附件；
 * ② 无法记录大小/上传人/上传时间；
 * ③ 无法单独删除某一个附件。
 * 新实现改为独立附件表，多个附件就是多行。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_doc_attachment")
public class DocAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 归属：收文 */
    public static final String DOC_TYPE_RECEIVE = "receive";
    /** 归属：发文 */
    public static final String DOC_TYPE_SEND = "send";

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** receive 收文 / send 发文 */
    private String docType;

    /** 对应主表ID */
    private String docId;

    /** 原始文件名 */
    private String fileName;

    /** 扩展名 */
    private String fileExt;

    /** 文件字节数 */
    private Long fileSize;

    /** MD5 */
    private String fileMd5;

    /** 存储类型 */
    private String storeType;

    /** 相对存储路径 */
    private String storePath;

    /** 预览文件路径 */
    private String previewPath;

    /** 排序 */
    private Integer sortNo;

    /** 上传人账号 */
    private String uploadBy;

    /** 上传人姓名 */
    private String uploadName;

    /** 上传时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadTime;

    /** 删除状态 0正常 1已删除 */
    @TableLogic
    private Integer delFlag;
}
