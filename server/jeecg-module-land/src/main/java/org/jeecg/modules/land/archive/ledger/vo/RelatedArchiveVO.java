package org.jeecg.modules.land.archive.ledger.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 道路设施验收及移交资料台账-关联档案行（只读，来源 t_archive）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p><b>★ 附件口径（需求方 2026-09 确认）</b>：台账的 13 类资料原件<b>不另建附件表</b>，
 * 统一挂在档案管理模块的 {@code t_archive / t_archive_file} 下；
 * 台账只持有 {@code archive_id} 指针，页面上的「关联档案」页签按
 * <b>配套项目ID 优先、宗地编号兜底</b>反查全部档案。
 *
 * <p>这样做的好处：档案模块已经实现了上传、预览、下载、类别树、ZIP 导出、操作日志，
 * 台账再建一套附件表就意味着两套权限、两套存储路径、两套清理逻辑。
 *
 * <p>本 VO 只取页面需要的字段（不 {@code SELECT *}）：档案模块的实体有 30+ 列，
 * 台账详情页只需要十来列。
 */
@Data
public class RelatedArchiveVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 档案ID（t_archive.id） */
    private String id;

    /** 档案号（DA-{yyyy}-{4位}） */
    private String archiveNo;

    /** 档案名称 / 案卷题名 */
    private String archiveName;

    /** 档案形态：electronic 电子档案 / paper 纸质档案 */
    private String archiveType;

    /** 密级 */
    private String secretLevel;

    /** 保管期限 */
    private String retention;

    /** 档案年度 */
    private Integer archiveYear;

    /** 归档日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date archiveDate;

    /** 状态：未归档/归档中/审核中/已归档 */
    private String status;

    /** 所属行政区 */
    private String xzqh;

    /** 责任部门 */
    private String responsibleDept;

    /** 关联配套项目ID */
    private String facilityId;

    /** 配套项目名称（冗余） */
    private String ptxmmc;

    /** 出让宗地编号（冗余） */
    private String crzdbh;

    /** 地块名称（冗余） */
    private String dkmc;

    /** 配套设施类别（冗余） */
    private String ptsslb;

    /** 文件数（冗余） */
    private Integer fileCount;

    /** 文件总字节（冗余） */
    private Long totalSize;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
