package org.jeecg.modules.land.archive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 档案操作记录
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>表 {@code t_archive_log}。方案 2.3.2 第 2 项原文要求
 * 「可在已上传的配套项目中添加档案信息，<b>并记录相关操作</b>」，
 * 因此所有写操作（新增/修改/删除/上传/下载/预览/导出/归档）都必须落一条记录。
 *
 * <p>本表<b>不做逻辑删除</b>：日志就是要留痕，删掉就没有意义了。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_archive_log")
public class ArchiveLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 动作：新增 */
    public static final String ACTION_ADD = "新增";
    /** 动作：修改 */
    public static final String ACTION_EDIT = "修改";
    /** 动作：删除 */
    public static final String ACTION_DELETE = "删除";
    /** 动作：上传 */
    public static final String ACTION_UPLOAD = "上传";
    /** 动作：下载 */
    public static final String ACTION_DOWNLOAD = "下载";
    /** 动作：预览 */
    public static final String ACTION_PREVIEW = "预览";
    /** 动作：导出 */
    public static final String ACTION_EXPORT = "导出";
    /** 动作：归档 */
    public static final String ACTION_ARCHIVE = "归档";

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 档案ID */
    private String archiveId;

    /** 文件ID */
    private String fileId;

    /** 动作：新增/修改/删除/上传/下载/预览/导出/归档 */
    private String action;

    /** 操作明细 */
    private String detail;

    /** 业务键（档案号 / 宗地编号 / 项目名称） */
    private String bizKey;

    /** 操作人账号 */
    private String operateBy;

    /** 操作人姓名 */
    private String operateName;

    /** 操作时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date operateTime;

    /** 操作IP */
    private String ip;
}
