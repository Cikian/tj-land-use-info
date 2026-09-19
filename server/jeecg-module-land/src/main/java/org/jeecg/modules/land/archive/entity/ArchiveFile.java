package org.jeecg.modules.land.archive.entity;

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

/**
 * @Description: 档案文件（卷内文件）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>表 {@code t_archive_file}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/archive/04_t_archive.sql}。
 *
 * <p><b>★ 档案类别挂在「文件」这一层</b>（需求方确认）：
 * 每个上传的文件必须选择一个<b>叶子</b>档案类别，
 * {@code categoryId} / {@code categoryPath} / {@code categoryName} 三个字段
 * 在保存时由服务端从 {@code t_archive_category} 冗余写入，前端不需要自己拼。
 *
 * <p>为什么要冗余 {@code categoryPath}：类别树用「主键串路径」表示层级，
 * 有了 path 就能用一条 {@code LIKE '父path/%'} 检索出「某父类别（含全部子类）下的所有文件」，
 * 档案查询页选一个非叶子类别时正是靠它做包含式过滤。
 *
 * <p><b>文件本身不落盘在数据库</b>：{@code storePath} 是相对
 * {@code jeecg.path.upload} 的相对路径（由 jeecg 通用上传接口
 * {@code /sys/common/upload} 返回），实际文件在磁盘上。
 * 下载接口只接受 {@code fileId}，服务端自己查 storePath，
 * <b>绝不接受前端传来的路径</b>（旧 tj-sfw 的 /pdf/render 就是路径拼接导致的任意文件读取漏洞）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_archive_file")
public class ArchiveFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：已归档 */
    public static final String STATUS_ARCHIVED = "已归档";
    /** 状态：审核中 */
    public static final String STATUS_AUDITING = "审核中";
    /** 状态：归档中 */
    public static final String STATUS_ARCHIVING = "归档中";

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 档案ID → t_archive.id */
    private String archiveId;

    /** 卷内序号 */
    private Integer seqNo;

    /** 档案类别ID → t_archive_category.id（必须是叶子类别） */
    private String categoryId;

    /** 类别 path（冗余） */
    private String categoryPath;

    /** 类别全路径名称（冗余），如「基本建设手续 / 项目建议书批复」 */
    private String categoryName;

    /** 原始文件名 */
    private String fileName;

    /** 文件题名（默认取文件名去扩展名） */
    private String fileTitle;

    /** 扩展名 */
    private String fileExt;

    /** 文件字节数 */
    private Long fileSize;

    /** MD5（去重/秒传） */
    private String fileMd5;

    /** 页数（PDF 可自动解析） */
    private Integer pageCount;

    /** 存储类型 local/oss/minio */
    private String storeType;

    /** 相对存储路径 */
    private String storePath;

    /** 预览文件路径 */
    private String previewPath;

    /** 状态：已归档/审核中/归档中 */
    private String status;

    /** 同档案内排序 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 删除状态 0正常 1已删除 */
    @TableLogic
    private Integer delFlag;

    /** 上传人 */
    private String createBy;

    /** 上传时间 */
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

    /** 文件可访问的相对 URL（由后端按 staticDomainURL 规则拼装，供前端预览/下载） */
    @TableField(exist = false)
    private String url;

    /** 便于列表展示的可读大小，如 5.7 MB */
    @TableField(exist = false)
    private String readableSize;
}
