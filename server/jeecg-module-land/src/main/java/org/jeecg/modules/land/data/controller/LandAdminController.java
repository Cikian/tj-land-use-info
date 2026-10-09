package org.jeecg.modules.land.data.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.data.dto.LandAdminQueryDTO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.service.ILandAdminService;
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
 * @Description: 数据管理 · 经营性用地信息录入（逐条）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）第 1 项。统一前缀 {@code /land/data/landAdmin}。
 *
 * <p><b>接口一览</b>：
 * <pre>
 *   GET    /land/data/landAdmin/list           分页列表
 *   GET    /land/data/landAdmin/detail         详情
 *   GET    /land/data/landAdmin/history        变更履历
 *   GET    /land/data/landAdmin/codeOptions    宗地编号下拉（支持关键字）
 *   GET    /land/data/landAdmin/checkCrzdbh    编号唯一性校验
 *   POST   /land/data/landAdmin/add            新增
 *   PUT    /land/data/landAdmin/edit           编辑
 *   DELETE /land/data/landAdmin/delete         移除（软删）
 *   DELETE /land/data/landAdmin/deleteBatch    批量移除（软删）
 * </pre>
 *
 * <p><b>★ 为什么不挂在已有的 {@code LandDataController} 上</b>：
 * 那个控制器是「基础只读」（下拉、详情、首页看板），被档案、收发文、道路台账
 * 四个模块复用，权限用的是 {@code Logical.OR} 放行一组业务读取权限。
 * 把写操作塞进去，会让「授了档案查看权限的角色」意外获得数据写入能力 ——
 * 这正是旧系统「零鉴权」那类问题的变体。写操作单独一个控制器，
 * 权限码也只有 {@code land:data:*} 一族。
 *
 * <p><b>★ 权限码为什么只用一个 {@code land:data:land} 而不是 list/add/edit/delete 四个</b>：
 * 见 {@code sql/data/08_data_permission_buttons.sql} 的说明 ——
 * 「能进这个录入页的人就能录入」，拆细只增加授权负担。
 * 真正的安全边界是菜单能不能打开。
 */
@Slf4j
@Api(tags = "数据管理-经营性用地信息录入")
@RestController
@RequestMapping("/land/data/landAdmin")
public class LandAdminController {

    /** 本模块权限码（与 sql/data/08 脚本登记的按钮权限一致） */
    private static final String PERM = "land:data:land";

    @Autowired
    private ILandAdminService landAdminService;

    // ==================================================================
    // 查询
    // ==================================================================

    @AutoLog(value = "经营性用地录入-分页列表")
    @ApiOperation(value = "经营性用地录入-分页列表", notes = "支持编号/名称/区划/分类/配套有无等条件")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/list")
    public Result<IPage<Land>> list(LandAdminQueryDTO query) {
        return Result.OK(landAdminService.queryPage(query));
    }

    @AutoLog(value = "经营性用地录入-详情")
    @ApiOperation(value = "经营性用地录入-详情")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/detail")
    public Result<Land> detail(@RequestParam(name = "id") String id) {
        Land land = landAdminService.queryDetail(id);
        if (land == null) {
            return Result.error("未找到对应的出让宗地（可能已被移除）");
        }
        return Result.OK(land);
    }

    @AutoLog(value = "经营性用地录入-变更履历")
    @ApiOperation(value = "经营性用地录入-变更履历", notes = "字段级：改了哪个字段、改前改后、谁改的")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/history")
    public Result<List<Map<String, Object>>> history(
            @RequestParam(name = "id") String id,
            @RequestParam(name = "limit", required = false, defaultValue = "50") Integer limit) {
        return Result.OK(landAdminService.queryHistory(id, limit));
    }

    /** 宗地编号下拉（表单联动与跨模块选择共用） */
    @AutoLog(value = "经营性用地录入-编号下拉")
    @ApiOperation(value = "经营性用地录入-编号下拉", notes = "返回 id/crzdbh/dkmc/xzqh/xmfl，供联动带出")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/codeOptions")
    public Result<List<Map<String, Object>>> codeOptions(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.OK(landAdminService.queryCodeOptions(keyword, limit));
    }

    /** 出让宗地编号唯一性校验（表单 onBlur 实时校验） */
    @AutoLog(value = "经营性用地录入-编号唯一性校验")
    @ApiOperation(value = "经营性用地录入-编号唯一性校验")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/checkCrzdbh")
    public Result<Map<String, Object>> checkCrzdbh(
            @RequestParam(name = "crzdbh") String crzdbh,
            @RequestParam(name = "excludeId", required = false) String excludeId) {
        return Result.OK(landAdminService.checkCrzdbh(crzdbh, excludeId));
    }

    // ==================================================================
    // 写入
    // ==================================================================

    @AutoLog(value = "经营性用地录入-新增")
    @ApiOperation(value = "经营性用地录入-新增", notes = "出让宗地编号必填且不可重复")
    @RequiresPermissions(PERM)
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody LandSaveDTO dto, HttpServletRequest request) {
        try {
            return Result.OK("创建成功！", landAdminService.createLand(dto, request));
        } catch (Exception e) {
            log.warn("新增经营性用地失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @AutoLog(value = "经营性用地录入-编辑")
    @ApiOperation(value = "经营性用地录入-编辑", notes = "会记字段级变更履历")
    @RequiresPermissions(PERM)
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody LandSaveDTO dto, HttpServletRequest request) {
        try {
            landAdminService.updateLand(dto, request);
            return Result.OK("更新成功");
        } catch (Exception e) {
            log.warn("编辑经营性用地失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @AutoLog(value = "经营性用地录入-移除")
    @ApiOperation(value = "经营性用地录入-移除", notes = "软删，可在「数据更新与移除」页恢复")
    @RequiresPermissions(PERM)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id") String id,
                            @RequestParam(name = "reason", required = false) String reason,
                            HttpServletRequest request) {
        try {
            landAdminService.removeLand(id, reason, request);
            return Result.OK("已移除，可在「数据更新与移除」中恢复");
        } catch (Exception e) {
            log.warn("移除经营性用地失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @AutoLog(value = "经营性用地录入-批量移除")
    @ApiOperation(value = "经营性用地录入-批量移除", notes = "逐条软删并逐条留痕；返回成功/失败明细")
    @RequiresPermissions(PERM)
    @DeleteMapping(value = "/deleteBatch")
    public Result<Map<String, Object>> deleteBatch(@RequestParam(name = "ids") String ids,
                                                   @RequestParam(name = "reason", required = false) String reason,
                                                   HttpServletRequest request) {
        List<String> idList = Arrays.asList(ids.split(","));
        Map<String, Object> result = landAdminService.removeLandBatch(idList, reason, request);
        int successCount = result.get("successCount") == null ? 0 : (Integer) result.get("successCount");
        int failCount = result.get("failCount") == null ? 0 : (Integer) result.get("failCount");
        if (failCount > 0) {
            return Result.OK("成功移除 " + successCount + " 条，失败 " + failCount + " 条（详见返回明细）", result);
        }
        return Result.OK("成功移除 " + successCount + " 条", result);
    }
}
