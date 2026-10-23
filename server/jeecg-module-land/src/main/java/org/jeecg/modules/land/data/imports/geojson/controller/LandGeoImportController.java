package org.jeecg.modules.land.data.imports.geojson.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.data.imports.geojson.ILandGeoImportService;
import org.jeecg.modules.land.data.imports.geojson.LandGeoImportResultVO;
import org.jeecg.modules.land.data.imports.mapper.LandImportLogMapper;
import org.jeecg.modules.land.data.imports.support.LandImportSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

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

    @Autowired
    private LandImportSupport support;

    @Autowired
    private LandImportLogMapper importLogMapper;

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
        LandGeoImportResultVO result;
        try {
            result = geoImportService.confirm(file);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("地块图形导入入库失败", e);
            return Result.error("导入失败：" + e.getMessage());
        }
        writeImportLog(file, result);
        return Result.OK(result);
    }

    // ==================================================================
    // 导入日志
    // ==================================================================

    /**
     * 写导入日志（复用 {@code t_land_import_log}，与 Excel 批量导入同表可见）。
     *
     * <p>★ 刻意 try/catch 吞掉：日志是「辅助追溯」，失败不该让一次成功的
     * 导入在用户眼里变成「导入失败」（与 Excel 导入同一处理）。
     */
    private void writeImportLog(MultipartFile file, LandGeoImportResultVO result) {
        try {
            importLogMapper.insertLog(
                    UUID.randomUUID().toString().replace("-", ""),
                    file == null ? null : file.getOriginalFilename(),
                    file == null ? null : file.getSize(),
                    result.getTotalFeatures(),
                    0,
                    result.getUpdatedRows(),
                    result.getUnmatchedCount(),
                    result.getErrorCount(),
                    null,
                    0,
                    result.isAborted() ? 1 : 0,
                    buildErrorSummary(result),
                    support.currentUsername(),
                    support.currentRealname());
        } catch (Exception e) {
            log.warn("写地块图形导入日志失败（不影响导入结果）：{}", e.getMessage());
        }
    }

    /** 错误摘要：只取前 5 条，避免日志列被撑爆（与 Excel 导入同一口径） */
    private String buildErrorSummary(LandGeoImportResultVO result) {
        if (result == null || result.getErrors().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(5, result.getErrors().size());
        for (int i = 0; i < limit; i++) {
            LandGeoImportResultVO.FeatureError error = result.getErrors().get(i);
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("第").append(error.getFeatureIndex()).append("个地块 ");
            if (org.apache.commons.lang.StringUtils.isNotBlank(error.getCrzdbh())) {
                sb.append("[").append(error.getCrzdbh()).append("] ");
            }
            sb.append(error.getMessage());
        }
        if (result.getErrors().size() > limit) {
            sb.append(" | …共 ").append(result.getErrors().size()).append(" 条");
        }
        return sb.length() > 2000 ? sb.substring(0, 2000) : sb.toString();
    }
}
