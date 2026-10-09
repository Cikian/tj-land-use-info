package org.jeecg.modules.land.data.imports.facility.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.data.imports.facility.FacilityDuplicateStrategy;
import org.jeecg.modules.land.data.imports.facility.FacilityImportField;
import org.jeecg.modules.land.data.imports.facility.FacilityImportResultVO;
import org.jeecg.modules.land.data.imports.facility.mapper.FacilityImportLogMapper;
import org.jeecg.modules.land.data.imports.facility.service.IFacilityImportService;
import org.jeecg.modules.land.data.imports.facility.support.FacilityImportSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @Description: 数据管理 · 配套信息批量导入（模板 + 校验 + 孤儿清单 + 错误回执 + 入库）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）「配套信息批量导入管理」。
 * 旧实现：{@code cn.wizzer.app.web.profitorientedland.controllers
 * .xjKjkfbSupportingFacilitiesController#importData / #getExcelDemo}
 * （旧系统前端面板 {@code batchImportXjPublicFacilities.html}）。
 *
 * <p><b>接口一览</b>（统一前缀 {@code /land/data/facilityImport}）：
 * <pre>
 *   GET  /land/data/facilityImport/template       下载导入模板（含「填表说明」sheet）
 *   GET  /land/data/facilityImport/fields         字段字典（前端渲染列说明 / 取值提示）
 *   GET  /land/data/facilityImport/strategies     重复 (宗地编号, 配套项目名称) 处理策略选项
 *   POST /land/data/facilityImport/preview        ★ 预览校验（不写库，含孤儿清单）
 *   POST /land/data/facilityImport/confirm        ★ 确认入库
 *   POST /land/data/facilityImport/errorReport    下载错误回执（重新解析上传文件）
 *   GET  /land/data/facilityImport/logs           最近导入记录
 * </pre>
 *
 * <p><b>★ 为什么「预览」单独做成一个接口</b>：
 * 批量导入是不可逆的（虽然逻辑删除还在，但几百条一起写错很难逐条回退）。
 * 让用户先看到「会新增多少条、会覆盖多少条、错在哪几行、哪些行找不到宗地」，
 * 再决定是否落库，是这类功能能不能真正被人敢用的前提。
 * 后端也据此实现：预览与入库共用同一条解析路径，保证「预览看到什么，导入就写什么」。
 *
 * <p><b>★ 为什么 {@code allowOrphan} 在 preview 与 confirm 上都要传</b>：
 * 它不是一个「入库选项」，而是<b>校验口径本身</b>（孤儿算不算错误）。
 * 若只在 confirm 上传，用户会看到「预览说 12 行有错」，勾上开关再点导入时
 * 却导进去了 12 条他从未在预览里确认过的数据；两个接口口径一致，
 * 用户才能形成「预览看到的就是最终结果」的稳定预期。
 *
 * <p><b>★ 错误回执为什么有两个入口</b>：
 * <ul>
 *   <li>{@code POST /errorReport}：用户只做了预览、手里只有原文件时用
 *       （重新解析一遍文件，回填原始内容与孤儿清单）；</li>
 *   <li>入库接口的响应里也带上错误清单与孤儿清单：用户在「导入」这一步之后想下载回执时，
 *       直接用当次结果生成 —— 不再重新解析（文件可能已被用户改过，
 *       重新解析会得到与刚才不一致的回执，反而让人怀疑）。</li>
 * </ul>
 */
@Slf4j
@Api(tags = "数据管理-配套信息批量导入")
@RestController
@RequestMapping("/land/data/facilityImport")
public class FacilityImportController {

    /** 本模块权限码（sys_permission 里 menu_type=2 的按钮权限，见 sql/data 的菜单脚本） */
    private static final String PERM_IMPORT = "land:data:facilityImport";

    @Autowired
    private IFacilityImportService importService;

    @Autowired
    private FacilityImportSupport support;

    @Autowired
    private FacilityImportLogMapper importLogMapper;

    // ==================================================================
    // 一、模板
    // ==================================================================

    /**
     * 下载导入模板。
     *
     * <p>模板两个 sheet：第 1 个是数据表（两行表头：英文字段名 + 中文说明，数据从第 3 行起，
     * 预留 200 行带边框空行），第 2 个是「填表说明」（逐字段的取值与示例，含孤儿清单的说明）。
     */
    @AutoLog(value = "配套信息批量导入-下载模板")
    @ApiOperation(value = "配套信息批量导入-下载模板",
            notes = "文件名：市政配套建设及进展情况信息表批量导入模板.xlsx；两行表头 + 填表说明 sheet")
    @RequiresPermissions(PERM_IMPORT)
    @GetMapping(value = "/template")
    public void template(HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = importService.buildTemplate();
            writeWorkbook(response, workbook, "市政配套建设及进展情况信息表批量导入模板.xlsx");
        } catch (Exception e) {
            log.error("下载配套信息导入模板失败", e);
            writeJsonError(response, "下载模板失败：" + e.getMessage());
        } finally {
            closeQuietly(workbook);
        }
    }

    /**
     * 字段字典（前端「列说明 / 取值提示」用）。
     *
     * <p>之所以要这个接口而不是让前端自己写一份列定义：
     * 列定义一旦有两份就会漂移 —— 后端加了列、前端还是老的，
     * 用户按页面提示填的表会被后端拒绝。
     */
    @AutoLog(value = "配套信息批量导入-字段字典")
    @ApiOperation(value = "配套信息批量导入-字段字典", notes = "列名 / 中文名 / 类型 / 是否必填 / 取值 / 示例")
    @RequiresPermissions(PERM_IMPORT)
    @GetMapping(value = "/fields")
    public Result<List<Map<String, Object>>> fields() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FacilityImportField.FieldSpec spec : FacilityImportField.templated()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("column", spec.getColumn());
            item.put("label", spec.getLabel());
            item.put("type", spec.getType().name().toLowerCase());
            item.put("required", spec.isRequired());
            item.put("options", spec.getOptions());
            item.put("optionText", spec.optionText());
            item.put("maxLength", spec.getMaxLength());
            item.put("sample", spec.getSample());
            list.add(item);
        }
        return Result.OK(list);
    }

    /** 重复 (宗地编号, 配套项目名称) 的处理策略选项（前端下拉） */
    @AutoLog(value = "配套信息批量导入-重复处理策略")
    @ApiOperation(value = "配套信息批量导入-重复处理策略", notes = "reject / skip / update")
    @RequiresPermissions(PERM_IMPORT)
    @GetMapping(value = "/strategies")
    public Result<List<Map<String, Object>>> strategies() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FacilityDuplicateStrategy strategy : FacilityDuplicateStrategy.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", strategy.getCode());
            item.put("label", strategy.getLabel());
            list.add(item);
        }
        return Result.OK(list);
    }

    // ==================================================================
    // 二、预览与入库
    // ==================================================================

    /**
     * 预览校验（★ 不写库）。
     *
     * <p>返回「会新增多少条 / 会覆盖多少条 / 跳过多少条 / 错在哪几行
     * （Excel 行号 + 宗地编号 + 字段 + 原因）/ 哪些行找不到宗地（孤儿清单）」。
     *
     * @param allowOrphan 勾选后，宗地编号查不到的行不再当成错误拦截，
     *                    而是记入孤儿清单并仍按「可导入」预览（与 confirm 口径一致）
     */
    @AutoLog(value = "配套信息批量导入-预览校验")
    @ApiOperation(value = "配套信息批量导入-预览校验",
            notes = "解析并校验上传的 Excel，不写库；返回新增/覆盖行数预览、错误清单与孤儿清单")
    @RequiresPermissions(PERM_IMPORT)
    @PostMapping(value = "/preview")
    public Result<FacilityImportResultVO> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "duplicateStrategy", required = false) String duplicateStrategy,
            @RequestParam(name = "allowOrphan", required = false, defaultValue = "false") boolean allowOrphan) {
        try {
            return Result.OK(importService.preview(file, parseStrategy(duplicateStrategy), allowOrphan));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("配套信息批量导入预览失败", e);
            return Result.error("预览失败：" + e.getMessage());
        }
    }

    /**
     * 确认入库。
     *
     * @param duplicateStrategy reject（默认，重复即报错）/ skip（跳过重复只导新增）/ update（覆盖更新）
     * @param skipErrorRows     true = 跳过有错误的行只导入正确的行；
     *                          false（默认）= 只要有一行错误就整批不入库
     * @param allowOrphan       true = 宗地编号查不到的行记入孤儿清单后照常导入
     */
    @AutoLog(value = "配套信息批量导入-确认入库")
    @ApiOperation(value = "配套信息批量导入-确认入库",
            notes = "默认有错整批不入库；覆盖更新时空单元格不会清空库内原值；"
                    + "allowOrphan=true 时孤儿行照样入库并记入清单")
    @RequiresPermissions(PERM_IMPORT)
    @PostMapping(value = "/confirm")
    public Result<FacilityImportResultVO> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "duplicateStrategy", required = false) String duplicateStrategy,
            @RequestParam(name = "skipErrorRows", required = false, defaultValue = "false") boolean skipErrorRows,
            @RequestParam(name = "allowOrphan", required = false, defaultValue = "false") boolean allowOrphan) {
        FacilityDuplicateStrategy strategy;
        try {
            strategy = parseStrategy(duplicateStrategy);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        try {
            FacilityImportResultVO result = importService.confirm(file, strategy, skipErrorRows, allowOrphan);
            writeImportLog(file, strategy, skipErrorRows, allowOrphan, result);
            return Result.OK(result);
        } catch (Exception e) {
            log.error("配套信息批量导入入库失败", e);
            return Result.error("导入失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 三、错误回执
    // ==================================================================

    /**
     * 下载错误回执。
     *
     * <p>回执前若干列与导入模板完全一致（是用户上传文件里的原始内容，改好可直接重传），
     * 末尾追加 {@code __excel行号 / __错误字段 / __错误原因} 三列诊断信息；
     * 有孤儿时另附一个「孤儿清单」sheet。
     */
    @AutoLog(value = "配套信息批量导入-下载错误回执")
    @ApiOperation(value = "配套信息批量导入-下载错误回执",
            notes = "文件名：配套信息导入错误回执_yyyyMMddHHmmss.xlsx；保留原始内容 + 追加错误原因列 + 孤儿清单 sheet")
    @RequiresPermissions(PERM_IMPORT)
    @PostMapping(value = "/errorReport")
    public void errorReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "duplicateStrategy", required = false) String duplicateStrategy,
            @RequestParam(name = "allowOrphan", required = false, defaultValue = "false") boolean allowOrphan,
            HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = importService.buildErrorReport(file, parseStrategy(duplicateStrategy), allowOrphan);
            writeWorkbook(response, workbook, errorReportFileName());
        } catch (IllegalArgumentException e) {
            writeJsonError(response, e.getMessage());
        } catch (Exception e) {
            log.error("下载配套信息导入错误回执失败", e);
            writeJsonError(response, "生成错误回执失败：" + e.getMessage());
        } finally {
            closeQuietly(workbook);
        }
    }

    // ==================================================================
    // 四、导入记录
    // ==================================================================

    /** 最近导入记录（默认 20 条，最多 100 条） */
    @AutoLog(value = "配套信息批量导入-导入记录")
    @ApiOperation(value = "配套信息批量导入-导入记录", notes = "最近若干次批量导入的结果留痕（含孤儿行数）")
    @RequiresPermissions(PERM_IMPORT)
    @GetMapping(value = "/logs")
    public Result<List<Map<String, Object>>> logs(
            @RequestParam(name = "limit", required = false, defaultValue = "20") Integer limit) {
        int size = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        try {
            List<Map<String, Object>> rows = importLogMapper.selectRecent(size);
            return Result.OK(rows == null ? new ArrayList<Map<String, Object>>() : rows);
        } catch (Exception e) {
            // 导入日志表还没建时不该让页面报错：返回空清单 + 明确提示
            log.warn("查询配套导入记录失败（t_facility_import_log 可能尚未建表）：{}", e.getMessage());
            return Result.OK(new ArrayList<Map<String, Object>>());
        }
    }

    // ==================================================================
    // 五、内部工具
    // ==================================================================

    /** 前端传参 → 策略枚举（空 = 默认「重复即报错」） */
    private FacilityDuplicateStrategy parseStrategy(String value) {
        return FacilityDuplicateStrategy.of(value);
    }

    /**
     * 写导入日志。
     *
     * <p>★ 刻意用 try/catch 包住：导入日志是「辅助追溯」，它失败不该让一次
     * 已经成功的导入在用户眼里变成「导入失败」。
     * 旧系统在这一步也是 try/catch 吞掉的（{@code insertNewRecord} 外面套了空 catch）。
     *
     * <p>★ 顺便把「孤儿行数」与「是否允许孤儿」也记进去：事后有人问
     * 「上个月那批配套里有多少条挂不上宗地」，看日志表就知道，
     * 不用再去 {@code t_land} 与配套表做差集（那个差集随着两边数据变化还会漂移）。
     */
    private void writeImportLog(MultipartFile file, FacilityDuplicateStrategy strategy, boolean skipErrorRows,
                                boolean allowOrphan, FacilityImportResultVO result) {
        try {
            importLogMapper.insertLog(
                    UUID.randomUUID().toString().replace("-", ""),
                    file == null ? null : file.getOriginalFilename(),
                    file == null ? null : file.getSize(),
                    result.getTotalRows(),
                    result.getInsertedRows(),
                    result.getUpdatedRows(),
                    result.getSkippedDuplicates(),
                    result.getErrorCount(),
                    strategy.getCode(),
                    skipErrorRows ? 1 : 0,
                    result.isAborted() ? 1 : 0,
                    result.getOrphanRows(),
                    allowOrphan ? 1 : 0,
                    buildErrorSummary(result),
                    support.currentUsername(),
                    support.currentRealname());
        } catch (Exception e) {
            log.warn("写配套信息导入日志失败（不影响导入结果）：{}", e.getMessage());
        }
    }

    /**
     * 错误摘要：只取前 5 条，避免日志列被几百条错误撑爆。
     *
     * <p>有孤儿但没错误时也要给一句摘要 —— 日志表里「错误 0 条」看起来像一切正常，
     * 而实际上可能有一批配套挂不上宗地（那才是要跟进的），所以孤儿也要进摘要。
     */
    private String buildErrorSummary(FacilityImportResultVO result) {
        if (result == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (result.getOrphanRows() > 0) {
            sb.append("孤儿行 ").append(result.getOrphanRows()).append(" 条（宗地编号在系统中不存在）");
        }
        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            int limit = Math.min(5, result.getErrors().size());
            for (int i = 0; i < limit; i++) {
                FacilityImportResultVO.RowError error = result.getErrors().get(i);
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append("第").append(error.getRowNum()).append("行 ");
                if (StringUtils.isNotBlank(error.getColumnLabel())) {
                    sb.append("[").append(error.getColumnLabel()).append("] ");
                }
                sb.append(error.getMessage());
            }
            if (result.getErrors().size() > limit) {
                sb.append(" | …共 ").append(result.getErrors().size()).append(" 条");
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.length() > 2000 ? sb.substring(0, 2000) : sb.toString();
    }

    private String errorReportFileName() {
        return "配套信息导入错误回执_"
                + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + ".xlsx";
    }

    /** 把工作簿写进响应流（.xlsx） */
    private void writeWorkbook(HttpServletResponse response, Workbook workbook, String fileName)
            throws Exception {
        String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        response.reset();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        try (OutputStream out = response.getOutputStream()) {
            workbook.write(out);
            out.flush();
        }
    }

    /**
     * 文件流已经写过一半时出错，只能往响应里补一个 JSON 错误体。
     *
     * <p>此时换不了 HTTP 状态码（响应已提交），但用户至少能在浏览器下载到的文件里
     * 看到错误原因，而不是一个坏掉的 xlsx。
     */
    private void writeJsonError(HttpServletResponse response, String message) {
        try {
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\""
                    + StringUtils.defaultString(message).replace("\"", "'") + "\"}");
            response.getWriter().flush();
        } catch (Exception ignored) {
            log.warn("写响应错误信息也失败了：{}", message);
        }
    }

    private void closeQuietly(Workbook workbook) {
        if (workbook != null) {
            try {
                workbook.close();
            } catch (Exception ignored) {
                // 关闭失败无需处理
            }
        }
    }
}
