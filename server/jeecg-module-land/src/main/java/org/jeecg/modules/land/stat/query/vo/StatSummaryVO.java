package org.jeecg.modules.land.stat.query.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @Description: 查询统计 · 「分析结果」指标（旧面板里那 10 个数字）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>口径来源：旧 {@code xjKjkfbCommercialLandController.getSelectInfo(Cnd)}（L1500-1596）。
 * 高保真的「分析结果」字段名依次是：地块总数 / 配套总数 / 配套项目名称为空 /
 * 新建道路 / 配套费总额 / 已立项 / 立项率 / 已开工 / 开工率 / 已竣工 / 竣工率。
 *
 * <p>本 VO 同时承载「已可研 / 已初设」两组（旧后端算了但面板没显示，
 * 差异清单 D-602 记着这块新系统一直缺实现，这里一次补齐；
 * 前端按需展示，未展示的字段不影响口径）。
 *
 * <p><b>★ 两个数据集的口径差异（同一个 VO 两种含义）</b>
 * <pre>
 *   经营性用地（land）    ：口径是「命中宗地 → 其下配套」，
 *                          配套按 ptxmmc 去重（同一条路挂多宗地只算 1 个），
 *                          各环节是漏斗（上一环节「非否」才进入下一环节）；
 *   配套项目（facility）  ：口径是「命中配套项目」本身，不去重、不漏斗，
 *                          每个环节各算各的，landTotal = 涉及宗地数。
 * </pre>
 * 这两套口径在旧系统里分别由 {@code getSelectInfo()} 与配套面板的方法实现，数值不可互相校验。
 */
@Data
public class StatSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================================================================
    // 规模（两个数据集都有）
    // ==================================================================

    /** 地块总数（宗）。land=命中宗地数；facility=命中配套所涉及的宗地数 */
    private Long landTotal = 0L;

    /** 配套总数（个）。land=按 ptxmmc 去重后的配套数；facility=命中配套条数 */
    private Long facilityTotal = 0L;

    /** 配套项目名称为空的条数（高保真新增指标，旧后端无实现，口径见实现说明） */
    private Long facilityNameBlank = 0L;

    /** 新建道路（条）：配套设施类别=道路 且 建设性质=新建 */
    private Long newRoadCount = 0L;

    /** 配套费总额（万元，3 位小数）：Σ nrcbdptf */
    private BigDecimal supportingFeeTotal = BigDecimal.ZERO;

    // ==================================================================
    // 环节（数量 + 比率）
    // ==================================================================

    /** 已立项 */
    private Long approvedCount = 0L;
    /** 立项率（%） */
    private BigDecimal approvedRate = BigDecimal.ZERO;

    /** 已可研 */
    private Long feasibilityCount = 0L;
    /** 可研率（%） */
    private BigDecimal feasibilityRate = BigDecimal.ZERO;

    /** 已初设 */
    private Long designCount = 0L;
    /** 初设率（%） */
    private BigDecimal designRate = BigDecimal.ZERO;

    /** 已开工 */
    private Long startedCount = 0L;
    /** 开工率（%） */
    private BigDecimal startedRate = BigDecimal.ZERO;

    /** 已竣工 */
    private Long completedCount = 0L;
    /** 竣工率（%） */
    private BigDecimal completedRate = BigDecimal.ZERO;

    /** 已移交（仅配套数据集有值：land 口径旧系统没有移环节） */
    private Long handedOverCount = 0L;
    /** 移交率（%） */
    private BigDecimal handedOverRate = BigDecimal.ZERO;
}
