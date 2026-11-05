package org.jeecg.modules.land.data.imports.facilitygeo.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.data.imports.facilitygeo.IFacilityGeoImportService;
import org.jeecg.modules.land.data.imports.facilitygeo.FacilityGeoImportResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: 数据管理 · 配套地块图形（GeoJSON）导入
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p>功能：上传配套矢量成果 GeoJSON，按每个 Feature 的 {@code properties.PTXMMC}
 * （配套项目名称，归一后）匹配 {@code t_supporting_facilities.ptxmmc}，
 * 把匹配记录的 {@code dk_geojson} 更新为该 Feature 的 geometry。
 * 入口在「配套地块数据录入」页（大屏端）工具栏的「导入地块图形」按钮。
 *
 * <p><b>接口一览</b>（统一前缀 {@code /land/data/facilityGeoImport}）：
 * <pre>
 *   POST /preview   ★ 预览校验（不写库）
 *   POST /confirm   ★ 确认入库
 * </pre>
 *
 * <p><b>★ 权限码用 {@code land:data:facility}</b>（配套录入页的码）：
 * 本功能是「配套地块数据录入」页的一个动作，与宗地侧「导入按钮挂页面码」同一粒度。
 *
 * <p><b>★ 不写 t_facility_import_log</b>（配套 Excel 导入的「导入记录」表）——
 * 两个功能互不相关，混表会让 Excel 导入页冒出 .geojson 行（宗地侧已踩过并修正）。
 * 留痕走字段级变更履历：每条被更新配套的 ACTION_IMPORT 记录（含来源文件/时间/操作人）。
 */
@Slf4j
@Api(tags = "数据管理-配套地块图形导入")
@RestController
@RequestMapping("/land/data/facilityGeoImport")
public class FacilityGeoImportController {

    /** 与「配套地块数据录入」页同一权限码 */
    private static final String PERM_FACILITY = "land:data:facility";

    @Autowired
    private IFacilityGeoImportService facilityGeoImportService;

    /** 预览校验（★ 不写库）。 */
    @AutoLog(value = "配套地块图形导入-预览校验")
    @ApiOperation(value = "配套地块图形导入-预览校验",
            notes = "解析并校验上传的 GeoJSON（按 PTXMMC 匹配），不写库；返回将更新/未变化/未匹配计数与错误清单")
    @RequiresPermissions(PERM_FACILITY)
    @PostMapping(value = "/preview")
    public Result<FacilityGeoImportResultVO> preview(@RequestParam("file") MultipartFile file) {
        try {
            return Result.OK(facilityGeoImportService.preview(file));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("配套地块图形导入预览失败", e);
            return Result.error("预览失败：" + e.getMessage());
        }
    }

    /** 确认入库（重新解析文件后按名称匹配更新；有错误整批不写）。 */
    @AutoLog(value = "配套地块图形导入-确认入库")
    @ApiOperation(value = "配套地块图形导入-确认入库",
            notes = "重新解析上传的 GeoJSON 后按 PTXMMC 匹配更新 dk_geojson；有错误整批不写")
    @RequiresPermissions(PERM_FACILITY)
    @PostMapping(value = "/confirm")
    public Result<FacilityGeoImportResultVO> confirm(@RequestParam("file") MultipartFile file) {
        try {
            return Result.OK(facilityGeoImportService.confirm(file));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("配套地块图形导入入库失败", e);
            return Result.error("导入失败：" + e.getMessage());
        }
    }
}
