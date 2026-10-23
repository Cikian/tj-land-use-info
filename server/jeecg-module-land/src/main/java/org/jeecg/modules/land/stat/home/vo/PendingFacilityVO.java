package org.jeecg.modules.land.stat.home.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 首页看板 · 待落实配套统计
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-19
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（五）首页看板第二块。旧实现
 * {@code XjKjkfbCommonController.getLandNumber / getFacilitiesInfoBySJ / getFacilitiesInfoByQJ}。
 *
 * <p><b>★ 字段名是对前端的契约，不能改</b>：前端 {@code HomeAttrPanel} 已经依赖
 * {@code warnings.city / warnings.district / warnings.plot} 三个键与
 * {@link WarningItem} 的字段名；改名不会报错，只会让面板显示空白。
 *
 * <p>本类从 data 域 {@code FacilityDashboardVO} 迁移而来，并补了
 * {@code facilityCount}（旧前端排行视图要「配套总数」）。
 */
@Data
public class PendingFacilityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 至少有一个「需落实」配套的宗地数（市级 + 区级） */
    private long landTotal;

    /** 至少有一个「需落实」配套的市级宗地数 */
    private long cityLandCount;

    /** 至少有一个「需落实」配套的区级宗地数 */
    private long districtLandCount;

    /** 需落实配套项目总数（按 ptxmmc 去重，不是行数） */
    private long facilityTotal;

    /** 已完成配套项目数（先去重、再过滤「竣工且未标注开工否」） */
    private long completedFacilityCount;

    /** 完成率（百分比，保留 2 位小数） */
    private BigDecimal completionRate = BigDecimal.ZERO;

    /** 行政区排行：市级按 xzqh、区级按 xzqh2 分组 */
    private List<RankItem> ranks = new ArrayList<>();

    /** 预警面板：city（市级）/ district（区级）/ plot（地块） */
    private Map<String, List<WarningItem>> warnings = new LinkedHashMap<>();

    /**
     * 排行项。
     *
     * <p>{@code value} 是「宗地数」、{@code facilityCount} 是「配套项目数」——
     * 两者含义不同，不能混用：前端排行条按 value，配套总数按 facilityCount。
     */
    @Data
    public static class RankItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 项目分类：市级项目 / 区级项目 */
        private String projectType;

        /** 分组名（市级 = 行政区划，区级 = 旧口径的 xzqh2） */
        private String name;

        /** 该分组内「有需落实配套」的宗地数（COUNT(DISTINCT l.id)） */
        private long value;

        /** 该分组内需落实配套项目数（COUNT(DISTINCT ptxmmc)，先去重再计数） */
        private long facilityCount;

        /** 该分组内已完成的配套项目数（先去重、再按「任一行竣工且未标注开工否」判定） */
        private long completedCount;
    }

    /**
     * 预警行。
     *
     * <p><b>★ 字段名与迁移前 {@code FacilityDashboardVO.WarningItem} 逐字一致</b>，
     * 前端 {@code HomeAttrPanel} 已依赖；7 个比率字段缺失时会显示 0%，不会报错，
     * 所以这类「契约漂移」只能靠本注释与测试兜住。
     */
    @Data
    public static class WarningItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 行标识（市级/区级预警为「项目分类-区划」，地块预警为宗地 id） */
        private String id;

        /** 行政区（区级为 xzqh2；地块预警为纯 xzqh，地块名称另有 plotName 列） */
        private String district;

        /** 该区级行政区在库里一条配套都没有时置 true（前端提示「未上报」） */
        private boolean unreported;

        /**
         * 项目分类：市级项目 / 区级项目。
         *
         * <p>为什么预警行要带它：前端按「市级 5 档、区级 3 档」两套阈值给百分比着色
         * （旧系统 {@code GetColumn717} / {@code GetColumn0805}），
         * 「地块预警信息」弹窗里市/区两级混在一张表里，只能逐行看分类才知道用哪套档位。
         */
        private String projectType;

        /**
         * 该行涉及的配套项目条数（分母）。
         *
         * <p>只有「按区下钻的宗地明细」会填（一行 = 一宗地）；
         * 市区两级预警行填 0 —— 那里的分母是整区合计，单行没有意义。
         */
        private long facilityCount;

        /** 出让宗地编号（仅地块预警有） */
        private String landNo;

        /** 地块名称（仅地块预警有） */
        private String plotName;

        /** 征地拆迁（项建批复未完成率，%） */
        private BigDecimal landAcquisition = BigDecimal.ZERO;

        /** 可行性研究（可研批复未完成率，%） */
        private BigDecimal feasibility = BigDecimal.ZERO;

        /** 初步设计（初设及概算批复未完成率，%） */
        private BigDecimal preliminaryDesign = BigDecimal.ZERO;

        /** 资金落实（资金落实情况「未落实」占比，%） */
        private BigDecimal fund = BigDecimal.ZERO;

        /** 未开工（是否开工「否」占比，%） */
        private BigDecimal notStarted = BigDecimal.ZERO;

        /** 未竣工（是否竣工「否」占比，%） */
        private BigDecimal notCompleted = BigDecimal.ZERO;

        /** 未移交（是否移交「否」占比，%） */
        private BigDecimal notHandedOver = BigDecimal.ZERO;
    }
}
