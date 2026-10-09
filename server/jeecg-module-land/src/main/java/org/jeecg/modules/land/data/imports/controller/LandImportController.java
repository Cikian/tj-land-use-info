package org.jeecg.modules.land.data.imports.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.data.imports.LandDuplicateStrategy;
import org.jeecg.modules.land.data.imports.LandImportField;
import org.jeecg.modules.land.data.imports.LandImportResultVO;
import org.jeecg.modules.land.data.imports.mapper.LandImportLogMapper;
import org.jeecg.modules.land.data.imports.service.ILandImportService;
import org.jeecg.modules.land.data.imports.support.LandImportSupport;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @Description: 数据管理 · 经营性用地批量导入（模板 + 校验 + 错误回执 + 入库）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）「经营性用地批量导入管理」。
 * 旧实现：{@code cn.wizzer.app.web.profitorientedland.controllers
 * .xjKjkfbCommercialLandController#importData / #getExcelDemo}
 * （旧系统前端面板 {@code batchImportXjCommercialLand.html}）。
 *
 * <p><b>接口一览</b>（统一前缀 {@code /land/data/import}）：
 * <pre>
 *   GET  /land/data/import/template       下载导入模板（含「填表说明」sheet）
 *   GET  /land/data/import/fields         字段字典（前端渲染列说明 / 取值提示）
 *   GET  /land/data/import/strategies     重复宗地编号处理策略选项
 *   POST /land/data/import/preview        ★ 预览校验（不写库）
 *   POST /land/data/import/confirm        ★ 确认入库
 *   POST /land/data/import/errorReport    下载错误回执（重新解析上传文件）
 *   GET  /land/data/import/logs           最近导入记录
 * </pre>
 *
 * <p><b>★ 为什么「预览」单独做成一个接口</b>：
 * 批量导入是不可逆的（虽然逻辑删除还在，但几百条一起写错很难逐条回退）。
 * 让用户先看到「会新增多少条、会覆盖多少条、错在哪几行」，
 * 再决定是否落库，是这类功能能不能真正被人敢用的前提。
 * 后端也据此实现：预览与入库共用同一条解析路径，保证「预览看到什么，导入就写什么」。
 *
 * <p><b>★ 错误回执为什么有两个入口</b>：
 * <ul>
 *   <li>{@code POST /errorReport}：用户只做了预览、手里只有原文件时用
 *       （重新解析一遍文件，回填原始内容）；</li>
 *   <li>入库接口的响应里也带上错误清单：用户在「导入」这一步之后想下载回执时，
 *       直接用当次结果生成 —— 不再重新解析（文件可能已被用户改过，
 *       重新解析会得到与刚才不一致的回执，反而让人怀疑）。</li>
 * </ul>
 */
@Slf4j
@Api(tags = "数据管理-经营性用地批量导入")
@RestController
@RequestMapping("/land/data/import")
public class LandImportController {

    /** 本模块权限码（sys_permission 里 menu_type=2 的按钮权限，见 sql/land/03_land_data_menu.sql） */
    private static final String PERM_IMPORT = "land:data:import";

    @Autowired
    private ILandImportService importService;

    @Autowired
    private LandImportSupport support;

    @Autowired
    private LandImportLogMapper importLogMapper;

    // ==================================================================
    // 一、模板
    // ==================================================================

    /**
     * 下载导入模板。
     *
     * <p>模板两个 sheet：第 1 个是数据表（两行表头：英文字段名 + 中文说明，数据从第 3 行起，
     * 预留 200 行带边框空行），第 2 个是「填表说明」（逐字段的取值与示例）。
     */
    @AutoLog(value = "经营性用地批量导入-下载模板")
    @ApiOperation(value = "经营性用地批量导入-下载模板",
            notes = "文件名：经营性用地信息批量导入模板.xlsx；两行表头 + 填表说明 sheet")
    @RequiresPermissions(PERM_IMPORT)
    @GetMapping(value = "/template")
    public void template(HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = importService.buildTemplate();
            writeWorkbook(response, workbook, "经营性用地信息批量导入模板.xlsx");
        } catch (Exception e) {
            log.error("下载经营性用地导入模板失败", e);
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
    @AutoLog(value = "经营性用地批量导入-字段字典")
    @ApiOperation(value = "经营性用地批量导入-字段字典", notes = "列名 / 中文名 / 类型 / 是否必填 / 取值 / 示例")
    @RequiresPermissions(PERM_IMPORT)
    @GetMapping(value = "/fields")
    public Result<List<Map<String, Object>>> fields() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LandImportField.FieldSpec spec : LandImportField.templated()) {
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

    /** 重复宗地编号的处理策略选项（前端下拉） */
    @AutoLog(value = "经营性用地批量导入-重复处理策略")
    @ApiOperation(value = "经营性用地批量导入-重复处理策略", notes = "reject / skip / update")
    @RequiresPermissions(PERM_IMPORT)
    @GetMapping(value = "/strategies")
    public Result<List<Map<String, Object>>> strategies() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LandDuplicateStrategy strategy : LandDuplicateStrategy.values()) {
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
     * <p>返回「会新增多少条 / 会覆盖多少条 / 跳过多少条 / 错在哪几行（Excel 行号 + 宗地编号 + 字段 + 原因）」。
     */
    @AutoLog(value = "经营性用地批量导入-预览校验")
    @ApiOperation(value = "经营性用地批量导入-预览校验",
            notes = "解析并校验上传的 Excel，不写库；返回新增/覆盖行数预览与错误清单")
    @RequiresPermissions(PERM_IMPORT)
    @PostMapping(value = "/preview")
    public Result<LandImportResultVO> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "duplicateStrategy", required = false) String duplicateStrategy) {
        try {
            return Result.OK(importService.preview(file, parseStrategy(duplicateStrategy)));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("经营性用地批量导入预览失败", e);
            return Result.error("预览失败：" + e.getMessage());
        }
    }

    /**
     * 确认入库。
     *
     * @param duplicateStrategy reject（默认，重复即报错）/ skip（跳过重复只导新增）/ update（覆盖更新）
     * @param skipErrorRows     true = 跳过有错误的行只导入正确的行；
     *                          false（默认）= 只要有一行错误就整批不入库
     */
    @AutoLog(value = "经营性用地批量导入-确认入库")
    @ApiOperation(value = "经营性用地批量导入-确认入库",
            notes = "默认有错整批不入库；覆盖更新时空单元格不会清空库内原值")
    @RequiresPermissions(PERM_IMPORT)
    @PostMapping(value = "/confirm")
    public Result<LandImportResultVO> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "duplicateStrategy", required = false) String duplicateStrategy,
            @RequestParam(name = "skipErrorRows", required = false, defaultValue = "false") boolean skipErrorRows) {
        LandDuplicateStrategy strategy;
        try {
            strategy = parseStrategy(duplicateStrategy);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        try {
            LandImportResultVO result = importService.confirm(file, strategy, skipErrorRows);
            writeImportLog(file, strategy, skipErrorRows, result);
            return Result.OK(result);
        } catch (Exception e) {
            log.error("经营性用地批量导入入库失败", e);
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
     * 末尾追加 {@code __excel行号 / __错误字段 / __错误原因} 三列诊断信息。
     */
    @AutoLog(value = "经营性用地批量导入-下载错误回执")
    @ApiOperation(value = "经营性用地批量导入-下载错误回执",
            notes = "文件名：经营性用地导入错误回执_yyyyMMddHHmmss.xlsx；保留原始内容 + 追加错误原因列")
    @RequiresPermissions(PERM_IMPORT)
    @PostMapping(value = "/errorReport")
    public void errorReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "duplicateStrategy", required = false) String duplicateStrategy,
            HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = importService.buildErrorReport(file, parseStrategy(duplicateStrategy));
            writeWorkbook(response, workbook, errorReportFileName());
        } catch (IllegalArgumentException e) {
            writeJsonError(response, e.getMessage());
        } catch (Exception e) {
            log.error("下载经营性用地导入错误回执失败", e);
            writeJsonError(response, "生成错误回执失败：" + e.getMessage());
        } finally {
            closeQuietly(workbook);
        }
    }

    // ==================================================================
    // 四、导入记录
    // ==================================================================

    /** 最近导入记录（默认 20 条，最多 100 条） */
    @AutoLog(value = "经营性用地批量导入-导入记录")
    @ApiOperation(value = "经营性用地批量导入-导入记录", notes = "最近若干次批量导入的结果留痕")
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
            log.warn("查询导入记录失败（t_land_import_log 可能尚未建表）：{}", e.getMessage());
            return Result.OK(new ArrayList<Map<String, Object>>());
        }
    }

    // ==================================================================
    // 五、内部工具
    // ==================================================================

    /** 前端传参 → 策略枚举（空 = 默认「重复即报错」） */
    private LandDuplicateStrategy parseStrategy(String value) {
        return LandDuplicateStrategy.of(value);
    }

    /**
     * 写导入日志。
     *
     * <p>★ 刻意用 try/catch 包住：导入日志是「辅助追溯」，它失败不该让一次
     * 已经成功的导入在用户眼里变成「导入失败」。
     * 旧系统在这一步也是 try/catch 吞掉的（{@code insertNewRecord} 外面套了空 catch）。
     */
    private void writeImportLog(MultipartFile file, LandDuplicateStrategy strategy, boolean skipErrorRows,
                                LandImportResultVO result) {
        try {
            String summary = buildErrorSummary(result);
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
                    summary,
                    support.currentUsername(),
                    support.currentRealname());
        } catch (Exception e) {
            log.warn("写经营性用地导入日志失败（不影响导入结果）：{}", e.getMessage());
        }
    }

    /** 错误摘要：只取前 5 条，避免日志列被几百条错误撑爆 */
    private String buildErrorSummary(LandImportResultVO result) {
        if (result == null || result.getErrors().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(5, result.getErrors().size());
        for (int i = 0; i < limit; i++) {
            LandImportResultVO.RowError error = result.getErrors().get(i);
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
        return sb.length() > 2000 ? sb.substring(0, 2000) : sb.toString();
    }

    private String errorReportFileName() {
        return "经营性用地导入错误回执_"
                + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date())
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
     * <p>与台账导出模块同一处理：此时换不了 HTTP 状态码（响应已提交），
     * 但用户至少能在浏览器下载到的文件里看到错误原因，而不是一个坏掉的 xlsx。
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
