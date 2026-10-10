package org.jeecg.modules.land.data.attachment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * @Description: 数据管理统一附件（宗地 / 配套项目 / 环节进度）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>表 {@code t_land_attachment}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/data/04_t_land_attachment.sql}。
 *
 * <p><b>★ 为什么三种业务共用一张表</b>：附件管理要支持
 * 「上传 → 查询 → 预览 → 下载」这一整套动作。三张结构一样的表就要写三套 Service、
 * 三套 Controller、三份前端；而它们的差异只有 {@code biz_type} 一个字段的取值。
 * 这与设计文档 5.3.5 给出的 {@code t_attachment(biz_type, biz_id)} 建议一致。
 *
 * <p><b>★ 为什么必须抛弃旧实现</b>（旧系统 5 个硬伤，逐条对应本实体的字段）：
 * <ol>
 *   <li>类型写死为 4 个槽位 {@code file01~file04} → {@link #fileType} 走字典可扩展；</li>
 *   <li>查询靠<b>递归扫描磁盘目录</b> → 改为查库：目录丢了数据还在；</li>
 *   <li>预览前 {@code Files.deleteDir(tempPath)} <b>清空整个 temp 目录</b>，
 *       并发下互相删除 → {@link #storePath} 直读，不经临时目录；</li>
 *   <li>无大小/上传人/上传时间 → {@link #fileSize} / {@link #uploadBy} / {@link #uploadTime}；</li>
 *   <li>目录名里带业务语义（{@code 01-土地整理计划}），改名即丢数据 →
 *       {@link #fileType} 与物理目录解耦（目录只按业务类型+年月分）。</li>
 * </ol>
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_land_attachment")
public class LandAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================================================================
    // 业务类型取值（与 Service 层的白名单必须一致）
    // ==================================================================

    /** 业务类型：经营性用地（宗地） */
    public static final String BIZ_LAND = "land";
    /** 业务类型：配套项目 */
    public static final String BIZ_FACILITY = "facility";
    /** 业务类型：环节进度 */
    public static final String BIZ_PROCESS = "process";

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务类型：land / facility / process */
    private String bizType;

    /** 业务主键：t_land.id / 配套项目.id / t_facility_process.id */
    private String bizId;

    /** 业务可读键（宗地编号 / 配套项目名称）：列表与搜索直接展示，避免每次联表 */
    private String bizKey;

    /**
     * 附件类型码（材料类型）。
     *
     * <p>★ <b>这一列同时就是「目录」</b>：附件树按它分组，树上每个目录节点
     * 就是一个材料类型，节点下的文件就是该类型的附件。
     * 所以本模块**不需要**再单独存一份目录路径 —— 同一事实存两份，
     * 迟早会不一致（2026-10-10 曾短暂加过 dir_path，随后按此原则回收）。
     *
     * <p>取值来自两套字典（按 {@link #bizType} 区分）：
     * <ul>
     *   <li>{@code land} / {@code process} → {@code land_attach_type}（宗地 5 类）</li>
     *   <li>{@code facility} → {@code land_facility_attach_type}（配套 13 类）</li>
     * </ul>
     * 两套字典的名称逐字取自旧系统存储目录
     * {@code docs/基础设施配套动态监管工作站/} 下的类型目录名。
     */
    private String fileType;

    /** 原始文件名 */
    private String fileName;

    /** 扩展名（小写，不含点） */
    private String fileExt;

    /** 文件字节数 */
    private Long fileSize;

    /** MD5（秒传 / 去重） */
    private String fileMd5;

    /** MIME 类型（预览时决定用图片 / PDF / Office 哪种方式打开） */
    private String contentType;

    /** 存储类型：local / minio / oss */
    private String storeType;

    /**
     * 相对存储路径（不含前导斜杠）。
     *
     * <p>★ 落库前 Service 会归一：去掉前导斜杠（jeecg 上传接口返回的路径**带**前导斜杠，
     * 因为它来自 {@code biz} 参数，而前端的 {@code buildBizPath()} 就是带斜杠的），
     * 并拒绝含 {@code ..} 或盘符的值。读盘时再做一次 canonicalPath 归属校验。
     */
    private String storePath;

    /** 备注 */
    private String remark;

    /** 排序 */
    private Integer sortNo;

    /** 上传人账号 */
    private String uploadBy;

    /** 上传人姓名 */
    private String uploadName;

    /** 上传时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadTime;

    /** 下载次数（留痕用） */
    private Integer downloadCount;

    /** 删除状态：0正常 1已删除 */
    @TableLogic
    private Integer delFlag;

    // ==================================================================
    // 展示型字段（不入库，Service 组装）
    // ==================================================================

    /** 前端拼 staticDomainURL 用的相对路径（与 storePath 同值，单独给一个语义化字段） */
    @TableField(exist = false)
    private String url;

    /** 可读文件大小（如「2.31 MB」） */
    @TableField(exist = false)
    private String readableSize;

    /** 附件类型中文名（来自字典，列表展示用） */
    @TableField(exist = false)
    private String fileTypeText;

    /** 是否可以直接在浏览器里预览（图片 / PDF / 纯文本） */
    @TableField(exist = false)
    private Boolean previewable;

    /** 预览方式：image / pdf / office / download（前端据此选打开方式） */
    @TableField(exist = false)
    private String previewMode;
}
