package org.jeecg.modules.land.stat.home.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @Description: 首页看板统计 Mapper（出让地块情况 + 待落实配套）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-19
 * @Version V1.0
 *
 * <p><b>★ 为什么从 data 域迁到 stat/home</b>：这两块统计原本挂在
 * {@code LandMapper / FacilityMapper} 上，属于「数据管理」Mapper 却服务首页看板，
 * 结果是 data 域与统计域各有一份口径、改一处忘一处。迁到统计域后
 * data 域的 dashboard 方法已整体删除，全仓库只剩这一份实现。
 *
 * <p><b>★ 为什么返回 {@code Map} 而不是直接返回 VO</b>：
 * 统计 SQL 的分组列与聚合列会随「市级 / 区级」动态变化（见 XML 里的 CASE），
 * 直接映射 VO 需要为每块统计写一个 resultMap；而这里只有两三行聚合结果，
 * 用 Map 交给 Service 组装更省事，也避免 resultMap 与 SQL 别名两处维护。
 *
 * <p>本 Mapper 无对应实体（不继承 {@code BaseMapper}）：统计只读，
 * 不需要单表 CRUD，也不需要 {@code @TableLogic}。
 */
public interface HomeStatMapper {

    /** 出让地块总览：total / cityCount / districtCount / roadCount。 */
    Map<String, Object> selectLandOverview();

    /**
     * 出让地块行政区排行（市级按 xzqh、区级按 xzqh2 分组）。
     *
     * @return 每行 {@code projectType / name / value / roadCount}
     */
    List<Map<String, Object>> selectLandOverviewRanks();

    /** 待落实配套总览：landTotal / cityLandCount / districtLandCount。 */
    Map<String, Object> selectPendingFacilityOverview();

    /**
     * 待落实配套全量口径：facilityTotal / completedFacilityCount。
     *
     * <p>★ 先去重（按 ptxmmc）再过滤，不能直接对行数求和 —— 同一个配套项目
     * 可能挂在多个宗地下（实测库里有同名道路），按行数会重复计数。
     */
    Map<String, Object> selectPendingFacilityTotals();

    /**
     * 待落实配套行政区排行（市级按 xzqh、区级按 xzqh2 分组）。
     *
     * @return 每行 {@code projectType / name / value / facilityCount / completedCount}
     */
    List<Map<String, Object>> selectPendingFacilityRanks();

    /** 市级 / 区级项目预警（7 列指标）。 */
    List<Map<String, Object>> selectWarningByProjectType(@Param("xmfl") String xmfl);

    /** 区级项目按旧系统 xzqh2 列出的全部行政区（用于补「未上报」行）。 */
    List<Map<String, Object>> selectDistrictGroups();

    /** 地块预警（按宗地聚合 7 列指标）。 */
    List<Map<String, Object>> selectLandWarnings();

    /**
     * 预警下钻：某个行政区下的宗地明细（一行 = 一宗地，7 列指标为该宗地自身的未完成率）。
     *
     * <p>对应旧系统预警表的「操作 → 查看」：{@code homePage.js} 的
     * {@code seeDataInfo717 / seeDataInfo0805} → {@code byPageQuery}。
     *
     * @param xmfl     项目分类（市级项目 / 区级项目）——同时决定分组列是 xzqh 还是 xzqh2
     * @param district 分组名（市级 = xzqh 值、区级 = xzqh2 值）
     * @return 每行 {@code id / landNo / plotName / facilityCount + 7 个比率}
     */
    List<Map<String, Object>> selectWarningLands(@Param("xmfl") String xmfl,
                                                 @Param("district") String district);
}
