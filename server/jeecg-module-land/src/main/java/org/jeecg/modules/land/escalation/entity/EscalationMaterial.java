package org.jeecg.modules.land.escalation.entity;

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
 * @Description: 提级论证材料表（方案 2.3.3 第 1 项：论证材料标准化归集）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>表 {@code t_escalation_material}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/escalation/01_t_escalation.sql}。
 *
 * <p>业务痛点原文是「材料多以纸质或分散电子文档形式存放」，所以本表支持：
 * 一个项目<b>多文件</b>、{@link #version} 版本号、{@link #isSupplement} 补充材料标记。
 *
 * <p><b>文件不落本表二进制</b>：上传复用 jeecg 通用接口 {@code /sys/common/upload}
 * （与档案模块一致），落盘在 {@code jeecg.path.upload}，
 * 本表只存相对路径 {@link #storePath}；下载/预览时由服务端
 * {@code EscalationMaterialServiceImpl.resolveAbsolutePath} 做前缀与目录穿越校验，
 * <b>绝不接受前端传入的磁盘路径</b>。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_escalation_material")
public class EscalationMaterial implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 存储方式：本地磁盘（与 t_escalation_material.store_type 的 DDL 默认值一致） */
    public static final String STORE_TYPE_LOCAL = "local";

    /** 是否补充材料：否 */
    public static final int SUPPLEMENT_NO = 0;
    /** 是否补充材料：是（版本号在此基础上 +1） */
    public static final int SUPPLEMENT_YES = 1;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 提级论证项目ID → t_escalation_project.id */
    private String projectId;

    /** 材料类型（字典 land_escalation_material_type：申请材料/论证报告/支撑材料/其他） */
    private String materialType;

    /** 文件名（含扩展名，原样保存，下载时回给用户） */
    private String fileName;

    /** 扩展名（小写，不含点） */
    private String fileExt;

    /** 文件大小（字节，保存时校验 ≤ 50MB） */
    private Long fileSize;

    /** MD5（用于去重，jeecg 上传接口返回时带回） */
    private String fileMd5;

    /** 存储方式：local */
    private String storeType;

    /** 相对路径（相对 jeecg.path.upload，形如 /escalation/2026/09/xxx.pdf） */
    private String storePath;

    /** 预览路径（可空；为空时预览直接回源 store_path） */
    private String previewPath;

    /** 版本号（补充材料 +1，默认 1） */
    private Integer version;

    /** 是否补充材料 0否 1是 */
    private Integer isSupplement;

    /** 上传人（账号） */
    private String uploadBy;

    /** 上传时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadTime;

    /** 删除状态 0正常 1已删除 */
    @TableLogic
    private Integer delFlag;

    // ------------------------------------------------------------------
    // 非表字段（展示用，服务端补齐，不参与入库）
    // ------------------------------------------------------------------

    /** 下载/预览用的相对 URL（`store_path` 去掉前导斜杠，交给前端 staticDomainURL 拼接） */
    @TableField(exist = false)
    private String url;

    /** 人性化文件大小，如「12.40 MB」 */
    @TableField(exist = false)
    private String readableSize;
}
