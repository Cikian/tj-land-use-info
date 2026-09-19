package org.jeecg.modules.land.archive.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 档案查询条件（档案维护列表与档案查询页共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>对应方案 2.3.2 第 3 项「各配套负责人可以根据<b>项目名称、时间、档案类型</b>等进行数据检索」。
 * 用 GET query string 绑定（Controller 里是非 {@code @RequestBody} 的对象参数）。
 *
 * <p><b>categoryId 的语义</b>：档案类别挂在文件上，因此按类别检索走的是一条
 * {@code EXISTS (SELECT 1 FROM t_archive_file ...)} 子查询；
 * 并且支持传<b>父类别</b>——只要文件的类别 path 落在该父类别的 path 之下就算命中，
 * 这样「选『规划手续』就能查出它全部子类下的档案」。
 * 该展开动作由前端把所选节点的 path 一起带上（{@link #categoryPath}）。
 */
@Data
public class ArchiveQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 档案号（模糊） */
    private String archiveNo;

    /** 档案名称 / 案卷题名（模糊） */
    private String archiveName;

    /** 配套项目名称（模糊） */
    private String ptxmmc;

    /** 出让宗地编号（模糊） */
    private String crzdbh;

    /** 地块名称（模糊） */
    private String dkmc;

    /** 所属行政区（精确） */
    private String xzqh;

    /** 配套设施类别（精确） */
    private String ptsslb;

    /** 配套项目ID（精确） */
    private String facilityId;

    /** 宗地ID（精确） */
    private String landId;

    /** 档案类别ID（精确，叶子类别） */
    private String categoryId;

    /**
     * 档案类别的 path。传了它时按「该类别及其所有子类」过滤；
     * 不传则退化为 {@link #categoryId} 精确匹配。
     */
    private String categoryPath;

    /** 档案年度（精确） */
    private Integer archiveYear;

    /** 责任部门（精确） */
    private String responsibleDept;

    /** 配套负责人（模糊） */
    private String responsibleUser;

    /** 状态（精确） */
    private String status;

    /** 密级（精确） */
    private String secretLevel;

    /** 档案类型 electronic / paper（精确） */
    private String archiveType;

    /** 来源 manual / doc_receive / doc_send（精确） */
    private String sourceType;

    /** 文件名（模糊，走 t_archive_file 的 EXISTS 子查询） */
    private String fileName;

    /** 归档日期起（含），格式 yyyy-MM-dd */
    private String beginDate;

    /** 归档日期止（含），格式 yyyy-MM-dd */
    private String endDate;

    /** 创建时间起（含），格式 yyyy-MM-dd */
    private String beginCreateTime;

    /** 创建时间止（含），格式 yyyy-MM-dd */
    private String endCreateTime;

    /** 页码，从 1 开始（jeecg 约定：请求参数名 pageNo） */
    private Integer pageNo;

    /** 每页条数（jeecg 约定：请求参数名 pageSize） */
    private Integer pageSize;

    public int resolvePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int resolvePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        // 上限 500：列表页没有理由一次拉几万条，导出走专门的导出接口
        return Math.min(pageSize, 500);
    }
}
