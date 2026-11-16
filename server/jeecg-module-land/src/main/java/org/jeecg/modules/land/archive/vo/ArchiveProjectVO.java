package org.jeecg.modules.land.archive.vo;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jeecg.modules.land.archive.entity.Archive;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 档案按项目聚合视图（树形第一层：项目 → 案卷 → 材料类别 → 文件）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-16
 * @Version V1.0
 *
 * <p><b>为什么不新建「中心业务档案表」</b>：测试报告未附中心业务档案表的字段表，
 * 用户已确认本次只做「按项目聚合展示 + 列表可视」，不得虚构新字段、不得重建表。
 * 因此这里<b>全部是查询派生字段</b>（聚合出来的），表结构一行未动：
 * 材料类别来自 {@code t_archive_file.category_name}，文件数与总大小来自
 * {@code t_archive_file} 的实际行数与 {@code file_size} 之和。
 *
 * <p>派生字段与来源（便于日后与中心业务档案表做字段映射时对照）：
 * <pre>
 *   projectKey / facilityId / ptxmmc / crzdbh / dkmc / xzqh  ← t_archive 的冗余列
 *   archiveCount   ← COUNT(DISTINCT t_archive.id)
 *   fileCount      ← COUNT(t_archive_file.id)
 *   totalSize      ← SUM(t_archive_file.file_size)
 *   categoryNames  ← GROUP_CONCAT(DISTINCT category_name)
 *   caseFiles      ← 第二层（按需查询，列表页首屏不填）
 * </pre>
 */
@Data
@Accessors(chain = true)
public class ArchiveProjectVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 项目分组键。
     *
     * <p>有 facilityId 时就是 facilityId；历史数据没有 facilityId 时退化为
     * {@code ptxmmc|crzdbh} 组合键。前端只做「原样回传」，不解析它的结构。
     */
    private String projectKey;

    /** 配套项目ID（历史数据可能为空） */
    private String facilityId;

    /** 配套项目名称 */
    private String ptxmmc;

    /** 出让宗地编号 */
    private String crzdbh;

    /** 地块名称 */
    private String dkmc;

    /** 行政区划 */
    private String xzqh;

    /** 该项目的案卷数 */
    private long archiveCount;

    /** 其中已归档的案卷数 */
    private long archivedCount;

    /** 卷内文件总数 */
    private long fileCount;

    /** 卷内文件总字节数 */
    private long totalSize;

    /**
     * 材料类别摘要（多类别用「、」拼接），列表与树形第一层直接展示，
     * 这样不点进详情也能看出「这个项目里都有哪些类别的材料」。
     */
    private String categoryNames;

    /** 材料类别个数（摘要太长时前端据此折叠） */
    private long categoryCount;

    /** 第二层：该项目的案卷（含各自卷内文件）。按需加载，首屏为 null */
    private List<Archive> caseFiles = new ArrayList<>();
}
