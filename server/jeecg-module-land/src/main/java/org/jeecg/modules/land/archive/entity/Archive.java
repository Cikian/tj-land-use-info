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
import java.util.List;

/**
 * @Description: 档案主表（方案 2.3.2 第 2 项：档案维护）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>表 {@code t_archive}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/archive/04_t_archive.sql}。
 *
 * <p><b>★ 与设计文档 6.2.2 的关键差异</b>：
 * 设计文档把「档案类别」放在档案主表（{@code category_id}）上，
 * 即「一份档案一个类别」。实际需求是「每个上传的文件各关联一个档案类别」，
 * 因此本实体<b>没有</b> {@code categoryId}，类别落在
 * {@link ArchiveFile#getCategoryId()} 上。
 * 「档案类别管理」中「类别下有档案则不能移除」的校验也随之改为统计
 * {@code t_archive_file.category_id}。
 *
 * <p><b>档案主体是配套项目</b>：录入时按「先选出让宗地（{@code landId}/{@code crzdbh}）
 * → 再联动选该宗地下的配套项目（{@code facilityId}/{@code ptxmmc}）」，
 * 两者都冗余落库，便于列表展示与按项目/宗地统计、导出。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_archive")
public class Archive implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 档案类型：电子档案 */
    public static final String TYPE_ELECTRONIC = "electronic";
    /** 档案类型：纸质档案 */
    public static final String TYPE_PAPER = "paper";

    /** 状态：未归档 */
    public static final String STATUS_PENDING = "未归档";
    /** 状态：归档中 */
    public static final String STATUS_ARCHIVING = "归档中";
    /** 状态：审核中 */
    public static final String STATUS_AUDITING = "审核中";
    /** 状态：已归档 */
    public static final String STATUS_ARCHIVED = "已归档";

    /** 来源：手工录入 */
    public static final String SOURCE_MANUAL = "manual";
    /** 来源：收文归档 */
    public static final String SOURCE_DOC_RECEIVE = "doc_receive";
    /** 来源：发文归档 */
    public static final String SOURCE_DOC_SEND = "doc_send";

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 档案号（默认自动生成 DA-{yyyy}-{4位流水}，允许手工改写） */
    private String archiveNo;

    /** 档案名称 / 案卷题名 */
    private String archiveName;

    // ------------------------------------------------------------------
    // 关联业务对象
    // ------------------------------------------------------------------

    /** 出让宗地ID → t_land.id */
    private String landId;

    /** 出让宗地编号（冗余业务键） */
    private String crzdbh;

    /** 配套项目ID → xj_kjkfb_supporting_facilities.id */
    private String facilityId;

    /** 配套项目名称（冗余） */
    private String ptxmmc;

    /** 地块名称（冗余，来自宗地） */
    private String dkmc;

    /** 配套设施类别（冗余，来自配套项目） */
    private String ptsslb;

    // ------------------------------------------------------------------
    // 档案属性
    // ------------------------------------------------------------------

    /** archival type：electronic 电子档案 / paper 纸质档案 */
    private String archiveType;

    /** 密级：一般/内部/秘密/机密 */
    private String secretLevel;

    /** 保管期限：永久/长期/短期 */
    private String retention;

    /** 档案年度（统计用） */
    private Integer archiveYear;

    /** 责任部门 */
    private String responsibleDept;

    /** 配套负责人 */
    private String responsibleUser;

    /** 所属行政区（统计用） */
    private String xzqh;

    /** 归档日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date archiveDate;

    /** 来源：manual / doc_receive / doc_send */
    private String sourceType;

    /** 来源单据ID（收文/发文ID） */
    private String sourceId;

    /** 备注 */
    private String remark;

    /** 状态：未归档/归档中/审核中/已归档 */
    private String status;

    /** 文件数（冗余，服务端维护） */
    private Integer fileCount;

    /** 文件总字节（冗余，服务端维护） */
    private Long totalSize;

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

    /** 卷内文件（详情接口返回；列表接口为 null） */
    @TableField(exist = false)
    private List<ArchiveFile> files;

    /**
     * 档案包含的类别名称（去重后用「、」连接）。
     * 类别挂在文件上，所以列表里的「档案类别」列由子查询聚合得到。
     */
    @TableField(exist = false)
    private String categoryNames;

    /** 档案包含的类别ID（去重后逗号分隔，供前端筛选回显） */
    @TableField(exist = false)
    private String categoryIds;
}
