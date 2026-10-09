package org.jeecg.modules.land.escalation.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.escalation.dto.EscalationQueryDTO;
import org.jeecg.modules.land.escalation.service.IEscalationProjectService;
import org.jeecg.modules.land.escalation.vo.EscalationStatVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Description: 提级论证查询统计（方案 2.3.3 第 2 项：按基本信息、论证结果多维查询统计）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/escalation/stat}，5 个统计接口对应 5 张图
 * （设计文档 5.5），全部归 {@code land:escalation:list} 权限（统计不单设权限码）。
 *
 * <p>接口清单（本类 5 个）：
 * <pre>
 *   GET /land/escalation/stat/byResult 按论证结果分布（饼图）
 *   GET /land/escalation/stat/byDept   按申报单位排行（条形图）
 *   GET /land/escalation/stat/byXzqh   按行政区划分布（条形图）
 *   GET /land/escalation/stat/byMonth  按申报时间趋势（折线图）
 *   GET /land/escalation/stat/byType   按项目类型分布（环形图）
 * </pre>
 *
 * <p>实现约定：5 个接口都先调一次 {@code queryStat}（一次拿全 5 个维度），
 * 只返回自己那一段。<b>牺牲一次多余聚合查询，换来「口径只写一遍」</b>——
 * 统计最容易出的问题就是「同一份数据、两张图对不上」，比多查 4 条 GROUP BY 更值得防。
 */
@Slf4j
@Api(tags = "提级论证-查询统计")
@RestController
@RequestMapping("/land/escalation/stat")
public class EscalationStatController {

    /** 权限码：查询（统计图与列表同权限） */
    private static final String PERM_LIST = "land:escalation:list";

    @Autowired
    private IEscalationProjectService projectService;

    @AutoLog(value = "提级论证统计-按论证结果")
    @ApiOperation(value = "提级论证统计-按论证结果", notes = "饼图；未登记论证结果的项目归为「未登记」")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/byResult")
    public Result<List<EscalationStatVO.NameCount>> byResult(EscalationQueryDTO query) {
        return Result.OK(projectService.queryStat(query).getByResult());
    }

    @AutoLog(value = "提级论证统计-按申报单位")
    @ApiOperation(value = "提级论证统计-按申报单位", notes = "条形图；未填单位归为「未填写」")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/byDept")
    public Result<List<EscalationStatVO.NameCount>> byDept(EscalationQueryDTO query) {
        return Result.OK(projectService.queryStat(query).getByDept());
    }

    @AutoLog(value = "提级论证统计-按行政区划")
    @ApiOperation(value = "提级论证统计-按行政区划", notes = "条形图；未填行政区划归为「未填写」")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/byXzqh")
    public Result<List<EscalationStatVO.NameCount>> byXzqh(EscalationQueryDTO query) {
        return Result.OK(projectService.queryStat(query).getByXzqh());
    }

    @AutoLog(value = "提级论证统计-按申报月份")
    @ApiOperation(value = "提级论证统计-按申报月份", notes = "折线图；name 形如 2026-09，未填申报时间的项目不计入")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/byMonth")
    public Result<List<EscalationStatVO.NameCount>> byMonth(EscalationQueryDTO query) {
        return Result.OK(projectService.queryStat(query).getByMonth());
    }

    @AutoLog(value = "提级论证统计-按项目类型")
    @ApiOperation(value = "提级论证统计-按项目类型", notes = "环形图；未填类型归为「未填写」")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/byType")
    public Result<List<EscalationStatVO.NameCount>> byType(EscalationQueryDTO query) {
        return Result.OK(projectService.queryStat(query).getByType());
    }
}
