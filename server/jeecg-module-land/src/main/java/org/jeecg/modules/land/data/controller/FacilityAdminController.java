package org.jeecg.modules.land.data.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.data.dto.FacilitySaveDTO;
import org.jeecg.modules.land.data.dto.LandAdminQueryDTO;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.service.IFacilityAdminService;
import org.jeecg.modules.land.data.vo.FacilityProcessTreeVO;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @Description: 数据管理 · 配套地块数据录入（1 宗地 N 配套 + 29 环节进度）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）第 2 项。统一前缀 {@code /land/data/facilityAdmin}。
 *
 * <p><b>接口一览</b>：
 * <pre>
 *   GET    /land/data/facilityAdmin/list            分页列表
 *   GET    /land/data/facilityAdmin/detail          详情
 *   GET    /land/data/facilityAdmin/byLand          某宗地下的全部配套 + 阶段进度汇总
 *   GET    /land/data/facilityAdmin/checkPtxmmc     名称唯一性 + 宗地存在性校验
 *   POST   /land/data/facilityAdmin/add             新增
 *   PUT    /land/data/facilityAdmin/edit            编辑
 *   DELETE /land/data/facilityAdmin/delete          移除（软删）
 *   DELETE /land/data/facilityAdmin/deleteBatch     批量移除（软删）
 *   POST   /land/data/facilityAdmin/process         新增/编辑
 *   GET    /land/data/facilityAdmin/processTree     阶段进度树（6 阶段 × 24 事项 + 汇总）
 *   GET    /land/data/facilityAdmin/processList     环节进度扁平列表（导出/表格用）
 *   POST   /land/data/facilityAdmin/process/save    保存单个环节进度
 *   POST   /land/data/facilityAdmin/process/saveBatch 整屏保存环节进度
 *   DELETE /land/data/facilityAdmin/process/delete  撤回某环节进度
 *   GET    /land/data/facilityAdmin/processConfig   环节配置骨架（前端渲染录入界面）
 *   GET    /land/data/facilityAdmin/expectEnd       按工作日推算预计结束时间
 * </pre>
 */
@Slf4j
@Api(tags = "数据管理-配套地块数据录入")
@RestController
@RequestMapping("/land/data/facilityAdmin")
public class FacilityAdminController {

    /** 本模块权限码（与 sql/data/08 脚本登记的按钮权限一致） */
    private static final String PERM = "land:data:facility";

    @Autowired
    private IFacilityAdminService facilityAdminService;

    // ==================================================================
    // 一、配套项目
    // ==================================================================

    @AutoLog(value = "配套地块录入-分页列表")
    @ApiOperation(value = "配套地块录入-分页列表")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/list")
    public Result<IPage<Facility>> list(LandAdminQueryDTO query) {
        return Result.OK(facilityAdminService.queryPage(query));
    }

    @AutoLog(value = "配套地块录入-详情")
    @ApiOperation(value = "配套地块录入-详情")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/detail")
    public Result<Facility> detail(@RequestParam(name = "id") String id) {
        Facility facility = facilityAdminService.queryDetail(id);
        if (facility == null) {
            return Result.error("未找到对应的配套项目（可能已被移除）");
        }
        return Result.OK(facility);
    }

    /** 「1 宗地 N 配套」的入口：一次拿到该宗地下全部配套 + 各自阶段进度 */
    @AutoLog(value = "配套地块录入-按宗地查配套")
    @ApiOperation(value = "配套地块录入-按宗地查配套",
            notes = "返回该宗地下的全部配套项目，并附六大阶段的汇总状态与完成度")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/byLand")
    public Result<List<Map<String, Object>>> byLand(@RequestParam(name = "crzdbh") String crzdbh) {
        return Result.OK(facilityAdminService.queryByLand(crzdbh));
    }

    @AutoLog(value = "配套地块录入-名称与宗地校验")
    @ApiOperation(value = "配套地块录入-名称与宗地校验",
            notes = "同时校验：宗地是否存在、同一宗地下配套项目名称是否重复")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/checkPtxmmc")
    public Result<Map<String, Object>> checkPtxmmc(
            @RequestParam(name = "crzdbh") String crzdbh,
            @RequestParam(name = "ptxmmc") String ptxmmc,
            @RequestParam(name = "excludeId", required = false) String excludeId) {
        return Result.OK(facilityAdminService.checkPtxmmc(crzdbh, ptxmmc, excludeId));
    }

    @AutoLog(value = "配套地块录入-新增")
    @ApiOperation(value = "配套地块录入-新增",
            notes = "必须挂到已有宗地（旧系统不校验，产生了 60 行孤儿配套）")
    @RequiresPermissions(PERM)
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody FacilitySaveDTO dto, HttpServletRequest request) {
        try {
            return Result.OK("创建成功！", facilityAdminService.createFacility(dto, request));
        } catch (Exception e) {
            log.warn("新增配套项目失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @AutoLog(value = "配套地块录入-编辑")
    @ApiOperation(value = "配套地块录入-编辑")
    @RequiresPermissions(PERM)
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody FacilitySaveDTO dto, HttpServletRequest request) {
        try {
            facilityAdminService.updateFacility(dto, request);
            return Result.OK("更新成功");
        } catch (Exception e) {
            log.warn("编辑配套项目失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @AutoLog(value = "配套地块录入-移除")
    @ApiOperation(value = "配套地块录入-移除", notes = "软删（delFlag 置 1），可在数据更新与移除页恢复")
    @RequiresPermissions(PERM)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id") String id,
                            @RequestParam(name = "reason", required = false) String reason,
                            HttpServletRequest request) {
        try {
            facilityAdminService.removeFacility(id, reason, request);
            return Result.OK("已移除，可在「数据更新与移除」中恢复");
        } catch (Exception e) {
            log.warn("移除配套项目失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @AutoLog(value = "配套地块录入-批量移除")
    @ApiOperation(value = "配套地块录入-批量移除")
    @RequiresPermissions(PERM)
    @DeleteMapping(value = "/deleteBatch")
    public Result<Map<String, Object>> deleteBatch(@RequestParam(name = "ids") String ids,
                                                   @RequestParam(name = "reason", required = false) String reason,
                                                   HttpServletRequest request) {
        List<String> idList = Arrays.asList(ids.split(","));
        Map<String, Object> result = facilityAdminService.removeFacilityBatch(idList, reason, request);
        return Result.OK(result);
    }

    // ==================================================================
    // 二、29 环节进度
    // ==================================================================

    /**
     * 阶段进度树。
     *
     * <p>未录入的环节也会出现（{@code filled=false}、{@code lcqk=未开启}）——
     * 录入界面必须显示全部 24 个事项，否则用户不知道还有哪些没填。
     */
    @AutoLog(value = "配套地块录入-阶段进度树")
    @ApiOperation(value = "配套地块录入-阶段进度树",
            notes = "六大阶段 × 24 事项 + 阶段汇总状态/完成度；未录入的环节也会返回")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/processTree")
    public Result<FacilityProcessTreeVO> processTree(@RequestParam(name = "ptId") String ptId) {
        try {
            return Result.OK(facilityAdminService.queryProcessTree(ptId));
        } catch (Exception e) {
            log.warn("查询环节进度树失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /** 环节进度扁平列表（表格展示 / 导出用；含阶段名与阶段汇总） */
    @AutoLog(value = "配套地块录入-环节进度列表")
    @ApiOperation(value = "配套地块录入-环节进度列表")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/processList")
    public Result<List<Map<String, Object>>> processList(@RequestParam(name = "ptId") String ptId) {
        try {
            return Result.OK(facilityAdminService.queryProcessList(ptId));
        } catch (Exception e) {
            log.warn("查询环节进度列表失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /** 环节配置骨架（阶段 + 事项），供前端在数据加载前先渲染界面 */
    @AutoLog(value = "配套地块录入-环节配置")
    @ApiOperation(value = "配套地块录入-环节配置", notes = "六大阶段与各自事项，含标准时长与主管部门")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/processConfig")
    public Result<List<Map<String, Object>>> processConfig() {
        return Result.OK(facilityAdminService.queryProcessConfig());
    }

    /**
     * 保存单个环节进度。
     *
     * <p>用 POST 而不是 PUT：路径里的 {lcId} 与 body 里的字段一起构成一次提交，
     * 而 jeecg 的 {@code @AutoLog} 对 PUT 的 body 记录不完整，POST 更稳。
     */
    @AutoLog(value = "配套地块录入-保存环节进度")
    @ApiOperation(value = "配套地块录入-保存环节进度",
            notes = "有则更新、无则新增；自动推算预计结束时间并校验状态与时间自洽")
    @RequiresPermissions(PERM)
    @PostMapping(value = "/process/save")
    public Result<String> saveProcess(@RequestParam(name = "ptId") String ptId,
                                      @RequestParam(name = "lcId") String lcId,
                                      @RequestBody Map<String, Object> payload,
                                      HttpServletRequest request) {
        try {
            return Result.OK("保存成功", facilityAdminService.saveProcess(ptId, lcId, payload, request));
        } catch (Exception e) {
            log.warn("保存环节进度失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /** 整屏保存：用户在录入界面一次提交多个环节 */
    @AutoLog(value = "配套地块录入-整屏保存环节进度")
    @ApiOperation(value = "配套地块录入-整屏保存环节进度",
            notes = "逐个保存并返回失败明细，部分失败不影响其它环节")
    @RequiresPermissions(PERM)
    @PostMapping(value = "/process/saveBatch")
    public Result<Map<String, Object>> saveProcessBatch(@RequestParam(name = "ptId") String ptId,
                                                        @RequestBody List<Map<String, Object>> items,
                                                        HttpServletRequest request) {
        Map<String, Object> result = facilityAdminService.saveProcessBatch(ptId, items, request);
        int failCount = result.get("failCount") == null ? 0 : (Integer) result.get("failCount");
        if (failCount > 0) {
            return Result.OK("部分环节保存失败（详见返回明细）", result);
        }
        return Result.OK("保存成功", result);
    }

    /** 撤回某环节进度（回到「未填报」） */
    @AutoLog(value = "配套地块录入-撤回环节进度")
    @ApiOperation(value = "配套地块录入-撤回环节进度", notes = "清空该环节的进度记录，回到未填报状态")
    @RequiresPermissions(PERM)
    @DeleteMapping(value = "/process/delete")
    public Result<?> deleteProcess(@RequestParam(name = "ptId") String ptId,
                                   @RequestParam(name = "lcId") String lcId,
                                   HttpServletRequest request) {
        try {
            facilityAdminService.removeProcess(ptId, lcId, request);
            return Result.OK("已撤回");
        } catch (Exception e) {
            log.warn("撤回环节进度失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 按工作日推算预计结束时间。
     *
     * <p>前端在用户填「环节开始时间」时调它自动带出预计结束时间，
     * 用户也可以手工改（改了就按用户填的存）。
     */
    @AutoLog(value = "配套地块录入-预计结束时间")
    @ApiOperation(value = "配套地块录入-预计结束时间",
            notes = "从开始日往后推 N 个工作日（剔除周末与节假日日历）；calendarBased 标记是否使用了人工维护的日历")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/expectEnd")
    public Result<Map<String, Object>> expectEnd(@RequestParam(name = "startDate") String startDate,
                                                  @RequestParam(name = "days") Integer days) {
        try {
            return Result.OK(facilityAdminService.calcExpectEnd(startDate, days));
        } catch (Exception e) {
            log.warn("计算预计结束时间失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}
