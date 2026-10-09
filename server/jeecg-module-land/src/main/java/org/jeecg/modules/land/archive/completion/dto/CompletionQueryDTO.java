package org.jeecg.modules.land.archive.completion.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @Description: 竣工验收项目历史工程资料数字化档案-查询条件（列表 / 统计 / 导出共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>对应方案 2.3.2 第 8 项「可进行快速检索查询，可快速导出档案信息」。
 * 清单 §6.3.8 只给了三条定性要求（存量历史项目、数字化状态追踪、扫描件关联），
 * 没有明列检索条件，因此这里按「历史档案盘点」的实际用法设计：
 * <ol>
 *   <li><b>项目维度</b>：档案编号、历史项目名称、项目编号、行政区划、项目类型、四家参建单位；</li>
 *   <li><b>时间维度</b>：开工 / 竣工 / 验收 三段日期区间；</li>
 *   <li><b>★ 数字化维度</b>（本模块的重点）：数字化状态、数字化加工单位、
 *       数字化完成日期区间、总页数区间、扫描分辨率、<b>是否已挂扫描件</b>；</li>
 *   <li><b>盘点维度</b>：投资额区间、保管期限、关键词全文。</li>
 * </ol>
 *
 * <p><b>★ 列表、统计、导出共用同一套条件</b>，SQL 侧也是同一段 {@code queryWhere}，
 * 保证「档案列表里看到什么，统计与导出就是什么」——这是档案与台账模块立下的约定，本模块照抄。
 *
 * <p>用 GET query string 绑定（Controller 里是非 {@code @RequestBody} 的对象参数）。
 * 分页沿用 jeecg 的 {@code pageNo/pageSize}，上限 500（见 {@link #resolvePageSize()}）。
 */
@Data
public class CompletionQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 一、项目维度
    // ------------------------------------------------------------------

    /** 档案编号（模糊，格式 JG-{yyyy}-{4位}） */
    private String archiveNo;

    /** ★历史项目名称（模糊） */
    private String projectName;

    /** 历史项目原编号（模糊） */
    private String projectCode;

    /** ★行政区划（精确，16 区） */
    private String xzqh;

    /** 项目类型（精确，字典 land_completion_project_type） */
    private String projectType;

    /** 建设单位（模糊） */
    private String buildUnit;

    /** 施工单位（模糊） */
    private String constructUnit;

    /** 设计单位（模糊） */
    private String designUnit;

    /** 监理单位（模糊） */
    private String superviseUnit;

    // ------------------------------------------------------------------
    // 二、时间维度
    // ------------------------------------------------------------------

    /** 开工日期起（含），格式 yyyy-MM-dd */
    private String beginStartDate;

    /** 开工日期止（含），格式 yyyy-MM-dd */
    private String endStartDate;

    /** ★竣工日期起（含），格式 yyyy-MM-dd */
    private String beginCompleteDate;

    /** ★竣工日期止（含），格式 yyyy-MM-dd */
    private String endCompleteDate;

    /** 验收日期起（含），格式 yyyy-MM-dd */
    private String beginAcceptanceDate;

    /** 验收日期止（含），格式 yyyy-MM-dd */
    private String endAcceptanceDate;

    // ------------------------------------------------------------------
    // 三、★ 数字化维度（本模块的重点）
    // ------------------------------------------------------------------

    /** ★数字化状态（精确，固定 3 值，见 DigitizeStatus） */
    private String digitizeStatus;

    /** 数字化加工单位（模糊） */
    private String digitizeOrg;

    /** 数字化完成日期起（含），格式 yyyy-MM-dd */
    private String beginDigitizeDate;

    /** 数字化完成日期止（含），格式 yyyy-MM-dd */
    private String endDigitizeDate;

    /** 总页数下限（含） */
    private Integer beginPageCount;

    /** 总页数上限（含） */
    private Integer endPageCount;

    /** 扫描分辨率（精确，DPI；标准取值如 200/300/400/600） */
    private Integer scanDpi;

    /**
     * 是否已挂扫描件（精确）。
     *
     * <p>{@code true} = 只看已关联 {@code t_archive} 的；{@code false} = 只看还没挂的；
     * {@code null} = 不限。这是「数字化推进」最常用的一个筛选项
     * （一键筛出「状态已数字化但没挂扫描件」这种矛盾记录）。
     *
     * <p>用 Boolean 而不是字符串：只有 true/false 两种取值，拼不出第三种 SQL 分支。
     */
    private Boolean hasArchive;

    // ------------------------------------------------------------------
    // 四、盘点维度
    // ------------------------------------------------------------------

    /** 投资额下限（万元，含） */
    private BigDecimal beginInvestAmount;

    /** 投资额上限（万元，含） */
    private BigDecimal endInvestAmount;

    /** 保管期限（精确，字典 land_completion_retention） */
    private String retention;

    /** 关联配套项目ID（精确，用于从配套项目页反查历史档案） */
    private String facilityId;

    /** 出让宗地编号（模糊，用于从宗地页反查历史档案） */
    private String crzdbh;

    /** 关联档案ID（精确，用于从档案详情反查历史档案） */
    private String archiveId;

    /** 关键词（档案编号 / 历史项目名称 / 项目编号 / 建设与施工单位 的 OR 匹配） */
    private String keyword;

    /** 创建时间起（含），格式 yyyy-MM-dd */
    private String beginCreateTime;

    /** 创建时间止（含），格式 yyyy-MM-dd */
    private String endCreateTime;

    // ------------------------------------------------------------------
    // 五、分页
    // ------------------------------------------------------------------

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
        // 上限 500：档案页没有理由一次拉几千条，导出走专门的 exportXls 接口
        return Math.min(pageSize, 500);
    }
}
