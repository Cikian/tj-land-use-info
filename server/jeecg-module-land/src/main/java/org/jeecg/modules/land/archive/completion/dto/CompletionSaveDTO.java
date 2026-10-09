package org.jeecg.modules.land.archive.completion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @Description: 竣工验收项目历史工程资料数字化档案-新增/编辑入参
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p><b>为什么新增与编辑共用一个 DTO</b>：两者字段完全相同，差别只在
 * 「新增时校验必填、编辑时逐字段覆盖」（见 Service 的注释）。
 * 与第 7 项台账模块用 {@code LedgerSaveDTO} 同时服务 add/edit 是同一处理。
 *
 * <p><b>★ 刻意没有的两个字段</b>：
 * <ul>
 *   <li>{@code archiveId} / {@code archiveCount} —— 扫描件指针只由
 *       {@code linkArchive} / {@code unlinkArchive} 两个接口维护，
 *       不放进表单，避免「编辑档案信息」时误清关联（也对应 Mapper 的
 *       {@code updateCompletionAll} 刻意不写这两列）；</li>
 *   <li>{@code delFlag} / 审计列 —— 由服务端维护。</li>
 * </ul>
 */
@Data
public class CompletionSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（编辑必传；新增忽略） */
    private String id;

    /** 档案编号（空则自动生成 JG-{yyyy}-{4位}；非空则校验唯一） */
    private String archiveNo;

    // ------------------------------------------------------------------
    // 历史项目基本信息
    // ------------------------------------------------------------------

    /** ★历史项目名称 */
    private String projectName;

    /** 历史项目原编号（项目自带的编号，可空） */
    private String projectCode;

    /** 行政区划（16 区） */
    private String xzqh;

    /** 项目类型（字典 land_completion_project_type） */
    private String projectType;

    // ------------------------------------------------------------------
    // 关联项目信息（★ 全选填）
    // ------------------------------------------------------------------

    /** 出让宗地ID */
    private String landId;

    /** 配套项目ID（选中配套项目后回传，便于关联扫描件） */
    private String facilityId;

    /** 出让宗地编号 */
    private String crzdbh;

    /** 配套项目名称（冗余，可由配套项目自动带出） */
    private String ptxmmc;

    /** 地块名称（冗余，可由宗地自动带出） */
    private String dkmc;

    /** 配套设施类别（冗余，可由配套项目自动带出） */
    private String ptsslb;

    // ------------------------------------------------------------------
    // 参建单位
    // ------------------------------------------------------------------

    /** 建设单位 */
    private String buildUnit;

    /** 施工单位 */
    private String constructUnit;

    /** 设计单位 */
    private String designUnit;

    /** 监理单位 */
    private String superviseUnit;

    // ------------------------------------------------------------------
    // 关键日期
    // ------------------------------------------------------------------

    /** 开工日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    /** 竣工日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date completeDate;

    /** 验收日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date acceptanceDate;

    // ------------------------------------------------------------------
    // 投资与保管
    // ------------------------------------------------------------------

    /** 投资额（万元） */
    private BigDecimal investAmount;

    /** 保管期限（字典 land_completion_retention） */
    private String retention;

    // ------------------------------------------------------------------
    // ★ 数字化属性
    // ------------------------------------------------------------------

    /** 数字化状态（未数字化/数字化中/已数字化；空则沿用旧值，新增时取默认「未数字化」） */
    private String digitizeStatus;

    /** 数字化完成日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date digitizeDate;

    /** 数字化加工单位 */
    private String digitizeOrg;

    /** 总页数 */
    private Integer pageCount;

    /** 文件数 */
    private Integer fileCount;

    /** 扫描分辨率（DPI） */
    private Integer scanDpi;

    /** 备注 */
    private String remark;
}
