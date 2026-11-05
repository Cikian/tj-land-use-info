package org.jeecg.modules.land.data.imports.geojson.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.data.imports.geojson.ILandGeoImportService;
import org.jeecg.modules.land.data.imports.geojson.LandGeoImportResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: 数据管理 · 地块图形（GeoJSON）导入
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p>功能：上传 GeoJSON 文件（shp 转出，一般来自地块矢量成果），按每个 Feature 的
 * {@code properties.CRZDBH} 匹配 {@code t_land.crzdbh}，把匹配记录的
 * {@code dk_geojson} 更新为该 Feature 的 geometry。
 * 入口在「经营性用地信息录入」页工具栏的「导入地块图形」按钮（弹窗，两步：预览 → 确认）。
 *
 * <p><b>接口一览</b>（统一前缀 {@code /land/data/geoImport}）：
 * <pre>
 *   POST /land/data/geoImport/preview   ★ 预览校验（不写库）
 *   POST /land/data/geoImport/confirm   ★ 确认入库
 * </pre>
 *
 * <p><b>★ 为什么只做两个接口、不做错误回执下载</b>：
 * 一次导入的地块数是「宗地个数」（几百条），不是 Excel 那种几千行，
 * 且错误只有「编号缺失 / 重复 / 图形不合法」三类，弹窗里的错误表格
 * （序号 + 编号 + 原因）已足够定位；Excel 导入的错误回执（原始内容 + 诊断列）
 * 对 GeoJSON 没有意义 —— 用户没法在文本编辑器里按行改一个几十 MB 的坐标串。
 *
 * <p><b>★ 权限码为什么用 {@code land:data:land}</b>：
 * 本功能是「经营性用地信息录入」页的一个动作（给已有宗地补图形），
 * 按模块「一面一码」的粒度挂在宗地页的权限码下，不单设
 * {@code land:data:geoImport}（避免多一个要授权的按钮权限）。
 */
@Slf4j
@Api(tags = "数据管理-地块图形导入")
@RestController
@RequestMapping("/land/data/geoImport")
public class LandGeoImportController {

    /** 与「经营性用地信息录入」页同一权限码 */
    private static final String PERM_LAND = "land:data:land";

    @Autowired
    private ILandGeoImportService geoImportService;

    /**
     * 预览校验（★ 不写库）。
     *
     * <p>返回「共多少个地块 / 会更新多少条 / 图形未变化多少条 / 未匹配多少条 /
     * 错在哪几个地块（序号 + 编号 + 原因）」。
     */
    @AutoLog(value = "地块图形导入-预览校验")
    @ApiOperation(value = "地块图形导入-预览校验",
            notes = "解析并校验上传的 GeoJSON，不写库；返回将更新/未变化/未匹配计数与错误清单")
    @RequiresPermissions(PERM_LAND)
    @PostMapping(value = "/preview")
    public Result<LandGeoImportResultVO> preview(@RequestParam("file") MultipartFile file) {
        try {
            return Result.OK(geoImportService.preview(file));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("地块图形导入预览失败", e);
            return Result.error("预览失败：" + e.getMessage());
        }
    }

    /**
     * 确认入库。
     *
     * <p>与 Excel 导入一致：接口无状态，前端把预览时的同一文件再传一次，
     * 后端重新解析后写库（文件在两次请求之间可能被用户改过，重新解析保证
     * 「写进去的就是此刻文件里的内容」）。存在错误时后端拒绝写库并返回错误清单。
     */
    @AutoLog(value = "地块图形导入-确认入库")
    @ApiOperation(value = "地块图形导入-确认入库",
            notes = "重新解析上传的 GeoJSON 后按 CRZDBH 匹配更新 t_land.dk_geojson；有错误整批不写")
    @RequiresPermissions(PERM_LAND)
    @PostMapping(value = "/confirm")
    public Result<LandGeoImportResultVO> confirm(@RequestParam("file") MultipartFile file) {
        try {
            return Result.OK(geoImportService.confirm(file));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("地块图形导入入库失败", e);
            return Result.error("导入失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 留痕说明
    // ==================================================================

    /**
     * ★ 本功能<b>刻意不写</b> {@code t_land_import_log}（Excel 批量导入的日志表）。
     *
     * <p>初版曾复用该表「与 Excel 导入记录同表可见」，实测发现是错误设计：
     * 那张表是「经营性用地批量导入」页面的「导入记录」，混入 .geojson 行
     * 会让用户以为 Excel 导入功能被改动/记录被覆盖 —— 两个功能互不相关，
     * 页面上也各看各的。
     *
     * <p>本功能的留痕走<b>字段级变更履历</b>（每条被更新宗地的
     * {@code ACTION_IMPORT} 记录，含来源文件名 / 时间 / 操作人，
     * 见 {@code LandGeoImportServiceImpl#write}）——
     * 在「详情 → 变更履历」里按宗地精确可查，信息量远大于一张流水表。
     */
}
