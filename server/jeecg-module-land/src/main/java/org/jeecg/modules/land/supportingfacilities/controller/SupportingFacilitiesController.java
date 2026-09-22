package org.jeecg.modules.land.supportingfacilities.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.land.supportingfacilities.entity.SupportingFacilities;
import org.jeecg.modules.land.supportingfacilities.service.ISupportingFacilitiesService;
import org.jeecg.modules.land.supportingfacilities.vo.SupportingFacilitiesDashboardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

/**
 * 市政配套项目标准表模块。
 *
 * <p>业务数据表为 {@code t_supporting_facilities}，
 * 联动下拉仍由 {@code /land/data/facility/*} 提供，供档案和收发文模块复用。
 */
@Slf4j
@Api(tags = "市政配套项目")
@RestController
@RequestMapping("/land/supporting-facilities")
public class SupportingFacilitiesController extends JeecgController<SupportingFacilities, ISupportingFacilitiesService> {

    @Autowired
    private ISupportingFacilitiesService supportingFacilitiesService;

    @ApiOperation(value = "首页配套设施统计")
    @GetMapping("/dashboard")
    public Result<SupportingFacilitiesDashboardVO> dashboard() {
        return Result.OK(supportingFacilitiesService.queryDashboard());
    }

    @AutoLog(value = "市政配套项目-分页列表查询")
    @ApiOperation(value = "市政配套项目-分页列表查询")
    @GetMapping("/list")
    public Result<IPage<SupportingFacilities>> list(
            SupportingFacilities entity,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        QueryWrapper<SupportingFacilities> wrapper = QueryGenerator.initQueryWrapper(entity, request.getParameterMap());
        return Result.OK(supportingFacilitiesService.page(new Page<>(pageNo, pageSize), wrapper));
    }

    @AutoLog(value = "市政配套项目-通过id查询")
    @ApiOperation(value = "市政配套项目-通过id查询")
    @GetMapping("/queryById")
    public Result<SupportingFacilities> queryById(@RequestParam(name = "id") String id) {
        SupportingFacilities entity = supportingFacilitiesService.getById(id);
        return entity == null ? Result.error("未找到对应的配套项目") : Result.OK(entity);
    }

    @AutoLog(value = "市政配套项目-添加")
    @ApiOperation(value = "市政配套项目-添加")
    @PostMapping("/add")
    public Result<?> add(@RequestBody SupportingFacilities entity) {
        supportingFacilitiesService.save(entity);
        return Result.OK("添加成功！");
    }

    @AutoLog(value = "市政配套项目-编辑")
    @ApiOperation(value = "市政配套项目-编辑")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody SupportingFacilities entity) {
        supportingFacilitiesService.updateById(entity);
        return Result.OK("编辑成功！");
    }

    @AutoLog(value = "市政配套项目-通过id删除")
    @ApiOperation(value = "市政配套项目-通过id删除")
    @DeleteMapping("/delete")
    public Result<?> delete(@RequestParam(name = "id") String id) {
        supportingFacilitiesService.removeById(id);
        return Result.OK("删除成功！");
    }

    @AutoLog(value = "市政配套项目-批量删除")
    @ApiOperation(value = "市政配套项目-批量删除")
    @DeleteMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids") String ids) {
        supportingFacilitiesService.removeByIds(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功！");
    }

    @GetMapping("/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, SupportingFacilities entity) {
        return super.exportXls(request, entity, SupportingFacilities.class, "市政配套项目");
    }

    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, SupportingFacilities.class);
    }
}
