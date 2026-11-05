package org.jeecg.modules.land.stat.home.service;

import org.jeecg.modules.land.stat.home.vo.LandOverviewVO;
import org.jeecg.modules.land.stat.home.vo.PendingFacilityVO;

import java.util.List;

/**
 * @Description: 首页看板统计 Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-19
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（五）首页看板的两块统计：
 * 出让地块情况统计、待落实配套统计（含市区两级与地块预警）。
 *
 * <p>旧实现是 {@code XjKjkfbCommonController} 里的 5 个方法，
 * 前端在同一个首页面板里同时请求它们；这里收成两个接口，
 * 分别对应前端的两块面板，避免首屏发 5 个请求。
 */
public interface IHomeStatService {

    /**
     * 出让地块情况统计（总数 / 市区两级 / 涉及道路 / 行政区排行）。
     *
     * <p>★ 市级按 {@code t_land.xzqh}、区级按 {@code t_land.xzqh2} 分组 ——
     * 旧系统口径，不能统一成 xzqh（原因见 {@code HomeStatMapper.xml}）。
     */
    LandOverviewVO queryLandOverview();

    /**
     * 待落实配套统计（宗地口径 / 配套口径 / 行政区排行 / 预警面板）。
     *
     * <p>配套按 {@code ptxmmc} 去重后再统计，同名配套跨宗地只算一条。
     */
    PendingFacilityVO queryPendingFacility();

    /**
     * 预警下钻：某个行政区下的宗地明细（一行 = 一宗地，7 列指标为该宗地自身的未完成率）。
     *
     * <p>对应旧系统预警表的「操作 → 查看」（{@code homePage.js} 的
     * {@code seeDataInfo717 / seeDataInfo0805} → {@code byPageQuery}）。
     *
     * <p>★ 返回的比率与预警行同源（同一个 {@code warningRates} 片段），
     * 所以「区级预警里这一行 40%」点进去能看到具体是哪几宗地把它拉高的。
     *
     * @param projectType 前端口径：{@code city} / {@code district}
     *                    （也兼容直接传「市级项目 / 区级项目」）
     * @param district    分组名（市级 = xzqh 值、区级 = xzqh2 值）
     * @return 宗地明细；参数非法或没有配套时返回空列表，不抛异常
     *         （首页面板点开一个空区不该弹错误框）
     */
    List<PendingFacilityVO.WarningItem> queryWarningLands(String projectType, String district);
}
