package org.jeecg.modules.land.stat.home.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.stat.home.service.IHomeStatService;
import org.jeecg.modules.land.stat.home.vo.LandOverviewVO;
import org.jeecg.modules.land.stat.home.vo.PendingFacilityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Description: 首页看板 · 统计接口
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-19
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（五）首页看板。旧实现
 * {@code XjKjkfbCommonController}（Nutz）的 5 个方法 +
 * {@code ProcessWarningController} 的两个预警接口，收成三个接口：
 * <pre>
 *   出让地块情况统计 → GET /land/stat/home/landOverview
 *   待落实配套统计   → GET /land/stat/home/pendingFacility（含市区两级/地块预警）
 *   预警下钻明细     → GET /land/stat/home/warningDetail
 * </pre>
 *
 * <p><b>★ 为什么不挂 {@code @RequiresPermissions}</b>：
 * 首页工作台是「登录后默认打开的页面」，看板数据本身不含敏感字段（只有汇总数）。
 * 如果挂上档案/收发文的按钮权限，没被授权那些模块的角色打开首页会直接 403，
 * 首屏一片空白 —— 这正是旧 data 域 {@code /land/data/dashboard} 的处理方式
 * （见其注释「首页工作台登录后即可查看」），本接口迁移时保持一致。
 * 真正的数据边界在「能不能登录」，不在按钮权限。
 */
@Slf4j
@Api(tags = "统计-首页看板")
@RestController
@RequestMapping("/land/stat/home")
public class HomeStatController {

    @Autowired
    private IHomeStatService homeStatService;

    /**
     * 出让地块情况统计。
     *
     * <p>返回总数、市级/区级数量、涉及道路数与行政区排行。
     * 区级排行按旧系统 {@code xzqh2} 分组（不是 xzqh），详见 Mapper XML 注释。
     */
    @AutoLog(value = "首页看板-出让地块情况统计")
    @ApiOperation(value = "出让地块情况统计", notes = "已出让宗地总数、市级/区级数量、涉及道路数、按行政区排行")
    @GetMapping(value = "/landOverview")
    public Result<LandOverviewVO> landOverview() {
        return Result.OK(homeStatService.queryLandOverview());
    }

    /**
     * 待落实配套统计。
     *
     * <p>返回宗地口径（至少有一个需落实配套的宗地数）、配套口径（按名称去重）、
     * 行政区排行与市区两级/地块预警。
     */
    @AutoLog(value = "首页看板-待落实配套统计")
    @ApiOperation(value = "待落实配套统计", notes = "待落实配套宗地、按名称去重的配套总数与完成率、行政区排行、市区两级/地块预警")
    @GetMapping(value = "/pendingFacility")
    public Result<PendingFacilityVO> pendingFacility() {
        return Result.OK(homeStatService.queryPendingFacility());
    }

    /**
     * 预警下钻：某个行政区下的宗地明细。
     *
     * <p>对应旧系统预警表每行的「操作 → 查看」（{@code homePage.js} 的
     * {@code seeDataInfo717 / seeDataInfo0805} → {@code byPageQuery}）：
     * 弹一张「地块预警信息」表，列出该区每宗地的 7 项未完成率，
     * 让用户看到是哪些宗地把整区的比例拉高的。
     *
     * <p>★ 为什么放在 stat/home 而不是 data 域：它和预警行共用同一套
     * {@code warningRates} 口径，放在一起才不会两处各自演化
     * （data 域原有的 {@code /land/data/facility/warning/detail} 已删除）。
     *
     * @param projectType {@code city} / {@code district}（也兼容「市级项目 / 区级项目」）
     * @param district    分组名：市级传 xzqh 值、区级传 xzqh2 值
     */
    @AutoLog(value = "首页看板-预警下钻")
    @ApiOperation(value = "预警下钻", notes = "某个行政区下的宗地明细（每宗地的 7 项未完成率）")
    @GetMapping(value = "/warningDetail")
    public Result<List<PendingFacilityVO.WarningItem>> warningDetail(
            @RequestParam(name = "projectType", required = false) String projectType,
            @RequestParam(name = "district", required = false) String district) {
        return Result.OK(homeStatService.queryWarningLands(projectType, district));
    }
}
