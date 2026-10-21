package org.jeecg.modules.land.stat.query.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.stat.export.IStatQueryExportService;
import org.jeecg.modules.land.stat.query.dto.StatQueryDTO;
import org.jeecg.modules.land.stat.query.service.IStatQueryService;
import org.jeecg.modules.land.stat.query.vo.QueryColumnVO;
import org.jeecg.modules.land.stat.query.vo.QueryPageVO;
import org.jeecg.modules.land.stat.query.vo.StatSummaryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Description: 查询统计（方案 2.3.1（五）5-01 经营性用地综合查询 / 5-02 配套设施进展情况查询）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p><b>接口一览</b>：
 * <pre>
 *   GET  /land/stat/query/columns            字段元数据（条件下拉 / 自定义列 / 导出列）
 *   GET  /land/stat/query/options            条件候选值（地块名称 / 区划 / 受让人 / 年份 / 配套类别…）
 *   GET  /land/stat/query/land               经营性用地分页明细（基础条件）
 *   GET  /land/stat/query/land/stats         经营性用地「分析结果」
 *   GET  /land/stat/query/land/detail        地块配套详情（点某行地块）
 *   GET  /land/stat/query/facility           用地配套项目分页明细
 *   GET  /land/stat/query/facility/stats     用地配套项目「分析结果」
 *   GET  /land/stat/query/facility/detail    配套项目详情（点某行配套）
 *   POST /land/stat/query/page               分页明细（支持高级查询条件）
 *   POST /land/stat/query/summary            分析结果（支持高级查询条件）
 *   GET  /land/stat/query/exportXls          Excel 导出（结果表 + 统计指标两个 sheet）
 * </pre>
 *
 * <p><b>★ 为什么 GET 与 POST 两套并存</b>：
 * 查询面板的基础条件（地块名称/区划/年份/受让人/项目分类…）都是短标量，
 * 走 GET 便于深链与排障；「项目查询 / 查询导出」页签的自由组合条件是数组结构，
 * 只能走 POST。两条路径最终落到同一个 Service，口径不可能分叉。
 *
 * <p><b>★ 为什么不挂 {@code @RequiresPermissions}</b>：
 * 与 {@code HomeStatController} 同一处理 —— 本模块的数据是宗地/配套的业务字段
 * （不含档案正文、不含收发文），边界是「查询统计这个菜单能不能打开」。
 * 大屏端从不下发按钮权限清单（加了 {@code v-has} 会把按钮从 DOM 删掉，见 AGENTS §3.6），
 * 若后端硬挂一个尚未在 {@code sys_permission} 里登记并授权的码，
 * 会让「已授权进菜单」的角色整页 403。待中心的权限清单落定后再补，
 * 记在 {@code docs/查询统计-实现说明.md} 的「待确认」一节。
 */
@Slf4j
@Api(tags = "查询统计")
@RestController
@RequestMapping("/land/stat/query")
public class StatQueryController {

    @Autowired
    private IStatQueryService statQueryService;

    @Autowired
    private IStatQueryExportService statQueryExportService;

    // ==================================================================
    // 一、元数据
    // ==================================================================

    @AutoLog(value = "查询统计-字段元数据")
    @ApiOperation(value = "字段元数据", notes = "条件字段、自定义列、导出列共用；不含真实列名")
    @GetMapping(value = "/columns")
    public Result<List<QueryColumnVO>> columns(
            @RequestParam(name = "dataType", required = false, defaultValue = "land") String dataType) {
        return Result.OK(statQueryService.queryColumns(dataType));
    }

    @AutoLog(value = "查询统计-条件候选值")
    @ApiOperation(value = "条件候选值", notes = "下拉去重值：地块名称/区划/受让人/出让年份/配套项目名称/配套设施类别")
    @GetMapping(value = "/options")
    public Result<Map<String, List<String>>> options(
            @RequestParam(name = "dataType", required = false, defaultValue = "land") String dataType) {
        return Result.OK(statQueryService.queryOptions(dataType));
    }

    // ==================================================================
    // 二、经营性用地查询
    // ==================================================================

    @AutoLog(value = "查询统计-经营性用地明细")
    @ApiOperation(value = "经营性用地-分页明细")
    @GetMapping(value = "/land")
    public Result<QueryPageVO> landPage(StatQueryDTO query) {
        query.setDataType("land");
        try {
            return Result.OK(statQueryService.queryPage(query));
        } catch (Exception e) {
            return failure("查询经营性用地失败", e);
        }
    }

    @AutoLog(value = "查询统计-经营性用地分析结果")
    @ApiOperation(value = "经营性用地-分析结果", notes = "地块总数/配套总数/配套费总额/各环节数量与比率")
    @GetMapping(value = "/land/stats")
    public Result<StatSummaryVO> landStats(StatQueryDTO query) {
        query.setDataType("land");
        try {
            return Result.OK(statQueryService.querySummary(query));
        } catch (Exception e) {
            return failure("统计经营性用地失败", e);
        }
    }

    @AutoLog(value = "查询统计-地块配套详情")
    @ApiOperation(value = "地块配套详情", notes = "某宗地下全部配套（点结果行触发）")
    @GetMapping(value = "/land/detail")
    public Result<Map<String, Object>> landDetail(
            @RequestParam(name = "crzdbh") String crzdbh) {
        try {
            return Result.OK(statQueryService.queryLandDetail(crzdbh));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return failure("查询地块配套详情失败", e);
        }
    }

    // ==================================================================
    // 三、用地配套项目查询
    // ==================================================================

    @AutoLog(value = "查询统计-用地配套项目明细")
    @ApiOperation(value = "用地配套项目-分页明细")
    @GetMapping(value = "/facility")
    public Result<QueryPageVO> facilityPage(StatQueryDTO query) {
        query.setDataType("facility");
        try {
            return Result.OK(statQueryService.queryPage(query));
        } catch (Exception e) {
            return failure("查询用地配套项目失败", e);
        }
    }

    @AutoLog(value = "查询统计-用地配套项目分析结果")
    @ApiOperation(value = "用地配套项目-分析结果")
    @GetMapping(value = "/facility/stats")
    public Result<StatSummaryVO> facilityStats(StatQueryDTO query) {
        query.setDataType("facility");
        try {
            return Result.OK(statQueryService.querySummary(query));
        } catch (Exception e) {
            return failure("统计用地配套项目失败", e);
        }
    }

    @AutoLog(value = "查询统计-配套项目详情")
    @ApiOperation(value = "配套项目详情", notes = "结果表点开某一行时看：环节进展、责任单位、时间节点")
    @GetMapping(value = "/facility/detail")
    public Result<Map<String, Object>> facilityDetail(@RequestParam(name = "id") String id) {
        try {
            return Result.OK(statQueryService.queryFacilityDetail(id));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return failure("查询配套项目详情失败", e);
        }
    }

    // ==================================================================
    // 四、高级查询（自由组合条件，POST）
    // ==================================================================

    @AutoLog(value = "查询统计-高级查询分页")
    @ApiOperation(value = "高级查询-分页明细", notes = "条件结构：field / operator / value / relation；字段走白名单")
    @PostMapping(value = "/page")
    public Result<QueryPageVO> page(@RequestBody StatQueryDTO query) {
        try {
            return Result.OK(statQueryService.queryPage(query));
        } catch (Exception e) {
            return failure("高级查询失败", e);
        }
    }

    @AutoLog(value = "查询统计-高级查询分析结果")
    @ApiOperation(value = "高级查询-分析结果")
    @PostMapping(value = "/summary")
    public Result<StatSummaryVO> summary(@RequestBody StatQueryDTO query) {
        try {
            return Result.OK(statQueryService.querySummary(query));
        } catch (Exception e) {
            return failure("高级查询统计失败", e);
        }
    }

    // ==================================================================
    // 五、导出
    // ==================================================================

    /**
     * Excel 导出：结果表 + 统计指标。
     *
     * <p>走 GET 是为了配合前端的 {@code window.open(带 token 的下载地址)}（与档案导出同一套），
     * 高级查询条件以 {@code conditionsJson} 塞进查询串。
     */
    @AutoLog(value = "查询统计-导出Excel")
    @ApiOperation(value = "导出Excel", notes = "两个 sheet：查询结果（含自定义列）+ 统计指标")
    @GetMapping(value = "/exportXls")
    public void exportXls(StatQueryDTO query, HttpServletResponse response) {
        Workbook workbook = null;
        try {
            if (query.getDataType() == null || query.getDataType().trim().isEmpty()) {
                query.setDataType("land");
            }
            workbook = statQueryExportService.exportQuery(query);
            String prefix = "facility".equalsIgnoreCase(query.getDataType())
                    ? "用地配套项目查询结果_" : "经营性用地查询结果_";
            String fileName = prefix + new SimpleDateFormat("yyyyMMddHHmm").format(new Date()) + ".xlsx";
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
        } catch (JeecgBootException e) {
            log.warn("查询统计导出被拒绝：{}", e.getMessage());
            writeJsonError(response, e.getMessage());
        } catch (Exception e) {
            log.error("查询统计导出失败", e);
            writeJsonError(response, "导出失败：" + e.getMessage());
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (Exception e) {
                    log.warn("关闭导出工作簿失败：{}", e.getMessage());
                }
            }
        }
    }

    // ==================================================================
    // 六、内部工具
    // ==================================================================

    /**
     * 统一错误出口：业务异常给原文，其他异常给一句能懂的话并记 error 日志。
     *
     * <p>为什么要包一层：查询接口直接抛异常会变成 HTTP 500，
     * 前端拿到的是 {@code NestedServletException} 而不是可读提示（档案模块同款处理）。
     */
    private <T> Result<T> failure(String action, Exception e) {
        if (e instanceof JeecgBootException) {
            log.warn("{}：{}", action, e.getMessage());
            return Result.error(e.getMessage());
        }
        log.error(action, e);
        return Result.error(action + "：" + (e.getMessage() == null ? "内部错误" : e.getMessage()));
    }

    /** 导出失败时按 JSON 回写（此时响应头可能已经写过，尽量让前端拿到可读提示） */
    private void writeJsonError(HttpServletResponse response, String message) {
        try {
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"" + safe(message) + "\"}");
            response.getWriter().flush();
        } catch (Exception ex) {
            log.error("回写导出错误信息失败", ex);
        }
    }

    private static String safe(String message) {
        return message == null ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
