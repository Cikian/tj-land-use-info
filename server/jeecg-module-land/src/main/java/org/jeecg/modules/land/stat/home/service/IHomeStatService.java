package org.jeecg.modules.land.stat.home.service;

import org.jeecg.modules.land.stat.home.vo.LandOverviewVO;
import org.jeecg.modules.land.stat.home.vo.PendingFacilityVO;

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
}
