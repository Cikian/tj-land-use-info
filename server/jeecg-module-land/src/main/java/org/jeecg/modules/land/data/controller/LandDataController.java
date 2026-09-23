package org.jeecg.modules.land.data.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.service.IFacilityService;
import org.jeecg.modules.land.data.service.ILandService;
import org.jeecg.modules.land.data.vo.FacilityDashboardVO;
import org.jeecg.modules.land.data.vo.FacilityOptionVO;
import org.jeecg.modules.land.data.vo.LandDashboardVO;
import org.jeecg.modules.land.data.vo.LandOptionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

/**
 * @Description: 数据管理 · 宗地 / 配套项目 基础查询接口
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/data}。这一层不是「数据管理模块」的完整实现
 * （录入/导入/移除见方案 2.3.1（三），另行开发），
 * 只提供**档案管理与收发文管理共同依赖的两个联动下拉**：
 *
 * <pre>
 *   第一步：选出让宗地   →  GET /land/data/land/options
 *   第二步：选配套项目   →  GET /land/data/facility/options?crzdbh=xxx
 * </pre>
 *
 * <p><b>权限</b>：这一层是档案与收发文录入时共用的<b>只读基础数据</b>
 * （宗地下拉、配套项目下拉、行政区划下拉），被三个业务页面复用，
 * 因此用 {@code Logical.OR} 放行「任意一个业务页面的读取权限」，
 * 避免为此单独造一个「公共查询」权限码而增加授权负担。
 *
 * <p>涉及的权限码（均为 {@code menu_type=2} 的按钮权限）：
 * <pre>
 *   land:archive:list / add / edit
 *   land:docReceive:list / add / edit
 *   land:docSend:list / add / edit
 * </pre>
 */
@Slf4j
@Api(tags = "数据管理-宗地与配套项目")
@RestController
@RequestMapping("/land/data")
public class LandDataController {

    /**
     * 说明：这些下拉被三处复用——档案新增/编辑、收文登记/编辑、发文登记/编辑；
     * 只授其中一处权限的角色也必须能拉到下拉数据，否则页面会「能打开但选不了项目」。
     *
     * <p>注意：{@code @RequiresPermissions} 的 value 必须是编译期常量表达式，
     * Java 不允许引用「数组类型的 static final 字段」，因此下面每个接口都写了完整的字符串数组，
     * 没有抽成公共常量。
     */

    @Autowired
    private ILandService landService;

    @Autowired
    private IFacilityService facilityService;

    /**
     * 宗地下拉（联动第一步）。
     *
     * @param keyword 出让宗地编号 / 地块名称 模糊
     * @param xzqh    行政区划精确过滤
     * @param limit   返回条数上限（默认 50，最大 200）
     */
    @AutoLog(value = "数据管理-宗地下拉")
    @ApiOperation(value = "宗地下拉", notes = "按出让宗地编号/地块名称模糊搜索，返回时附带该宗地下的配套项目数量")
    @RequiresPermissions(value = {
            "land:archive:list", "land:archive:add", "land:archive:edit",
            "land:docReceive:list", "land:docReceive:add", "land:docReceive:edit",
            "land:docSend:list", "land:docSend:add", "land:docSend:edit"}, logical = Logical.OR)
    @GetMapping(value = "/land/options")
    public Result<List<LandOptionVO>> landOptions(@RequestParam(name = "keyword", required = false) String keyword,
                                                  @RequestParam(name = "xzqh", required = false) String xzqh,
                                                  @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.OK(landService.queryOptions(keyword, xzqh, limit));
    }

    /** 宗地详情（选中后自动带出地块名称、行政区划等） */
    @AutoLog(value = "数据管理-宗地详情")
    @ApiOperation(value = "宗地详情")
    @RequiresPermissions(value = {
            "land:archive:list", "land:archive:add", "land:archive:edit",
            "land:docReceive:list", "land:docReceive:add", "land:docReceive:edit",
            "land:docSend:list", "land:docSend:add", "land:docSend:edit"}, logical = Logical.OR)
    @GetMapping(value = "/land/queryById")
    public Result<Land> landById(@RequestParam(name = "id", required = false) String id,
                                 @RequestParam(name = "crzdbh", required = false) String crzdbh) {
        Land land = id != null ? landService.queryById(id) : landService.queryByCrzdbh(crzdbh);
        if (land == null) {
            return Result.error("未找到对应的出让宗地");
        }
        return Result.OK(land);
    }

    /** 行政区划下拉（从已有宗地数据聚合） */
    @AutoLog(value = "数据管理-行政区划下拉")
    @ApiOperation(value = "行政区划下拉")
    @RequiresPermissions(value = {
            "land:archive:list", "land:archive:add", "land:archive:edit",
            "land:docReceive:list", "land:docReceive:add", "land:docReceive:edit",
            "land:docSend:list", "land:docSend:add", "land:docSend:edit"}, logical = Logical.OR)
    @GetMapping(value = "/land/xzqhOptions")
    public Result<List<Map<String, Object>>> xzqhOptions() {
        return Result.OK(landService.queryXzqhOptions());
    }

    /**
     * 配套项目下拉（联动第二步，必须带 crzdbh）。
     *
     * @param crzdbh  出让宗地编号（必传）
     * @param keyword 配套项目名称模糊
     * @param limit   返回条数上限
     */
    @AutoLog(value = "数据管理-配套项目下拉")
    @ApiOperation(value = "配套项目下拉", notes = "必须先选宗地；只返回该宗地下的配套项目")
    @RequiresPermissions(value = {
            "land:archive:list", "land:archive:add", "land:archive:edit",
            "land:docReceive:list", "land:docReceive:add", "land:docReceive:edit",
            "land:docSend:list", "land:docSend:add", "land:docSend:edit"}, logical = Logical.OR)
    @GetMapping(value = "/facility/options")
    public Result<List<FacilityOptionVO>> facilityOptions(@RequestParam(name = "crzdbh", required = false) String crzdbh,
                                                          @RequestParam(name = "keyword", required = false) String keyword,
                                                          @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.OK(facilityService.queryOptions(crzdbh, keyword, limit));
    }

    /** 跨宗地的配套项目搜索（档案查询页的项目名称条件用） */
    @AutoLog(value = "数据管理-配套项目全局搜索")
    @ApiOperation(value = "配套项目全局搜索", notes = "不限宗地，供查询条件使用")
    @RequiresPermissions(value = {
            "land:archive:list", "land:archive:add", "land:archive:edit",
            "land:docReceive:list", "land:docReceive:add", "land:docReceive:edit",
            "land:docSend:list", "land:docSend:add", "land:docSend:edit"}, logical = Logical.OR)
    @GetMapping(value = "/facility/search")
    public Result<List<FacilityOptionVO>> facilitySearch(@RequestParam(name = "keyword", required = false) String keyword,
                                                         @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.OK(facilityService.searchOptions(keyword, limit));
    }

    /** 配套项目详情 */
    @AutoLog(value = "数据管理-配套项目详情")
    @ApiOperation(value = "配套项目详情")
    @RequiresPermissions(value = {
            "land:archive:list", "land:archive:add", "land:archive:edit",
            "land:docReceive:list", "land:docReceive:add", "land:docReceive:edit",
            "land:docSend:list", "land:docSend:add", "land:docSend:edit"}, logical = Logical.OR)
    @GetMapping(value = "/facility/queryById")
    public Result<Facility> facilityById(@RequestParam(name = "id", required = true) String id) {
        Facility facility = facilityService.queryById(id);
        if (facility == null) {
            return Result.error("未找到对应的配套项目");
        }
        return Result.OK(facility);
    }

    /**
     * 首页出让情况。
     * 首页工作台登录后即可查看，不挂档案/收发文的按钮权限。
     */
    @AutoLog(value = "数据管理-首页宗地统计")
    @ApiOperation(value = "首页宗地统计", notes = "已出让宗地总数、市级/区级数量、按行政区划的出让地块与涉及道路")
    @GetMapping(value = "/dashboard")
    public Result<LandDashboardVO> dashboard() {
        return Result.OK(landService.queryDashboard());
    }

    /**
     * 首页落实配套情况与预警。
     * 数据表沿用旧库 {@code xj_kjkfb_supporting_facilities}。
     */
    @AutoLog(value = "数据管理-首页配套统计")
    @ApiOperation(value = "首页配套统计", notes = "待落实配套宗地、行政区排行、市级/区级/地块预警")
    @GetMapping(value = "/facility/dashboard")
    public Result<FacilityDashboardVO> facilityDashboard() {
        return Result.OK(facilityService.queryDashboard());
    }

    @AutoLog(value = "数据管理-首页预警明细")
    @ApiOperation(value = "首页预警明细", notes = "某个行政区下未完成的配套项目")
    @GetMapping(value = "/facility/warning/detail")
    public Result<List<Facility>> warningDetail(@RequestParam(name = "projectType") String projectType,
                                                @RequestParam(name = "district") String district) {
        return Result.OK(facilityService.queryWarningDetails(projectType, district));
    }

    @AutoLog(value = "经营性用地-分页列表")
    @ApiOperation(value = "经营性用地-分页列表")
    // @RequiresPermissions("land:data:land:list")
    @GetMapping(value = "/land/list")
    public Result<IPage<Land>> landList(Land land,
                                        @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                        @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                        HttpServletRequest request) {
        QueryWrapper<Land> wrapper = QueryGenerator.initQueryWrapper(land, request.getParameterMap());
        wrapper.orderByDesc("create_time");
        return Result.OK(landService.page(new Page<>(pageNo, pageSize), wrapper));
    }

    @AutoLog(value = "经营性用地-添加")
    @ApiOperation(value = "经营性用地-添加", notes = "出让宗地编号必填且不可重复")
    // @RequiresPermissions("land:data:land:add")
    @PostMapping(value = "/land/add")
    public Result<?> addLand(@RequestBody Land land) {
        landService.createLand(land);
        return Result.OK("创建成功！");
    }

    @AutoLog(value = "经营性用地-编辑")
    @ApiOperation(value = "经营性用地-编辑")
    // @RequiresPermissions("land:data:land:edit")
    @RequestMapping(value = "/land/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> editLand(@RequestBody Land land) {
        landService.updateLand(land);
        return Result.OK("更新成功");
    }

    @AutoLog(value = "经营性用地-删除")
    @ApiOperation(value = "经营性用地-删除", notes = "逻辑删除")
    // @RequiresPermissions("land:data:land:delete")
    @DeleteMapping(value = "/land/delete")
    public Result<?> deleteLand(@RequestParam(name = "id") String id) {
        if (landService.queryById(id) == null) {
            return Result.error("删除失败不存在");
        }
        landService.removeById(id);
        return Result.OK("删除成功");
    }

    @AutoLog(value = "配套项目-分页列表")
    @ApiOperation(value = "配套项目-分页列表")
    // @RequiresPermissions("land:data:facility:list")
    @GetMapping(value = "/facility/list")
    public Result<IPage<Facility>> facilityList(Facility facility,
                                                @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                HttpServletRequest request) {
        QueryWrapper<Facility> wrapper = QueryGenerator.initQueryWrapper(facility, request.getParameterMap());
        wrapper.eq("delFlag", "0");
        wrapper.orderByDesc("createTime");
        return Result.OK(facilityService.page(new Page<>(pageNo, pageSize), wrapper));
    }

    @AutoLog(value = "配套项目-添加")
    @ApiOperation(value = "配套项目-添加", notes = "必须挂到已有宗地，配套项目名称不可重复")
    // @RequiresPermissions("land:data:facility:add")
    @PostMapping(value = "/facility/add")
    public Result<?> addFacility(@RequestBody Facility facility) {
        facilityService.createFacility(facility);
        return Result.OK("创建成功！");
    }

    @AutoLog(value = "配套项目-编辑")
    @ApiOperation(value = "配套项目-编辑")
    // @RequiresPermissions("land:data:facility:edit")
    @RequestMapping(value = "/facility/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> editFacility(@RequestBody Facility facility) {
        facilityService.updateFacility(facility);
        return Result.OK("更新成功");
    }

    @AutoLog(value = "配套项目-删除")
    @ApiOperation(value = "配套项目-删除", notes = "逻辑删除，delFlag 置为 1")
    // @RequiresPermissions("land:data:facility:delete")
    @DeleteMapping(value = "/facility/delete")
    public Result<?> deleteFacility(@RequestParam(name = "id") String id) {
        Facility facility = facilityService.queryById(id);
        if (facility == null) {
            return Result.error("删除失败不存在");
        }
        facility.setDelFlag("1");
        facilityService.updateById(facility);
        return Result.OK("删除成功");
    }
}
