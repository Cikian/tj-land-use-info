package org.jeecg.modules.land.stat.warning.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.stat.warning.dto.WarningQueryDTO;
import org.jeecg.modules.land.stat.warning.dto.WarningRecalcDTO;
import org.jeecg.modules.land.stat.warning.service.IWarningService;
import org.jeecg.modules.land.stat.warning.vo.RecalcResultVO;
import org.jeecg.modules.land.stat.warning.vo.WarningOptionsVO;
import org.jeecg.modules.land.stat.warning.vo.WarningSummaryVO;
import org.jeecg.modules.land.stat.warning.vo.WarningVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @Description: 统计预警 · 分级预警查询接口
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（五）与差异清单 D-603（旧系统
 * {@code ProcessStatusController.createSpecial} L152-241 的四件事：等级、预警值、
 * 决策意见、预警记录）。本控制器只做查询 / 汇总 / 下拉 / 重算 / 导出：
 * <pre>
 *   预警记录分页 → GET  /land/stat/warning/list
 *   按等级/分类汇总 → GET  /land/stat/warning/summary
 *   下拉选项     → GET  /land/stat/warning/options
 *   手动重算     → POST /land/stat/warning/recalc
 *   导出 xlsx    → GET  /land/stat/warning/export
 * </pre>
 *
 * <p><b>★ 为什么不挂 {@code @RequiresPermissions}</b>：本组接口服务的是
 * <b>大屏端「查询统计 → 预警信息查询」</b>，而大屏的 {@code GetPermissionList}
 * 从不下发按钮权限清单（AGENTS.md §3.6 第 1 条）。挂上按钮权限的结果是
 * 「大屏上按钮全部消失」或「整页 403」，而不是「更安全」。
 * 真正的边界是「能不能登录 + 能不能打开这个页签」。
 * 管理端的两组写接口（等级 / 决策意见）则必须挂权限，见对应 Controller。
 *
 * <p><b>★ 所有错误都走 {@code Result.error(msg)}</b>，不抛 500：
 * 大屏端只认 {@code {success, message, result}}，直接抛异常时拿到的是
 * {@code NestedServletException}，界面上只会显示一句「请求失败」，
 * 运维与用户都无从判断是参数问题还是数据问题。
 *
 * <p><b>★ 重算要 {@code @AutoLog} 但不挂权限</b>：重算会写库（
 * {@code t_process_warning}），必须留操作人与时间；
 * 但它属于「刷新一下当前数据」，不该要求额外授权。
 */
@Slf4j
@Api(tags = "统计预警-分级预警查询")
@RestController
@RequestMapping("/land/stat/warning")
public class WarningController {

    @Autowired
    private IWarningService warningService;

    /**
     * 预警记录分页（契约 §5.1）。
     *
     * <p>GET 入参直接绑定到 {@link WarningQueryDTO}：{@code yjType / levelId / levelCode /
     * xzqh / dkmc / ptxmmc / crzdbh / keyword / pageNo / pageSize}。
     * {@code pageSize} 超过 200 会被<b>夹到 200</b>（不报错）——
     * 前端误传 1000 时应当少给一些而不是整页报错。
     */
    @AutoLog(value = "分级预警-预警记录分页")
    @ApiOperation(value = "预警记录分页", notes = "支持分类/等级/行政区划/地块/配套项目/宗地编号/关键词筛选")
    @GetMapping(value = "/list")
    public Result<IPage<WarningVO>> list(WarningQueryDTO query) {
        try {
            return Result.OK(warningService.list(query));
        } catch (Exception e) {
            return failure("查询预警记录失败", e);
        }
    }

    /**
     * 按等级 / 分类汇总（契约 §5.2）。
     *
     * <p>{@code byLevel} 用等级表全量补齐（含 count = 0 的等级），
     * 四个字段 {@code levelId / levelCode / levelName / count}；大屏端据此画四色卡片，
     * 缺一档就会「跳色」。
     */
    @AutoLog(value = "分级预警-预警汇总")
    @ApiOperation(value = "按等级/分类汇总", notes = "byLevel 四档齐全（含 0），total 与 calcTime 在顶层")
    @GetMapping(value = "/summary")
    public Result<WarningSummaryVO> summary(WarningQueryDTO query) {
        try {
            return Result.OK(warningService.summary(query));
        } catch (Exception e) {
            return failure("查询预警汇总失败", e);
        }
    }

    /**
     * 下拉选项（契约 §5.3）：等级 + 分类 + 问题类型。
     *
     * <p>{@code yjTypes} 与 {@code problemTypes} 都是<b>裸字符串数组</b>，
     * 不是对象数组（前端按 {@code label} 直接用，包成对象就要多一层 map）。
     */
    @AutoLog(value = "分级预警-下拉选项")
    @ApiOperation(value = "下拉选项", notes = "等级（升序）/ 预警分类 / 问题类型（取自字典 land_process_issue）")
    @GetMapping(value = "/options")
    public Result<WarningOptionsVO> options() {
        try {
            return Result.OK(warningService.options());
        } catch (Exception e) {
            return failure("查询预警下拉选项失败", e);
        }
    }

    /**
     * 手动重算（契约 §5.4 / §3.8）。
     *
     * <p>请求体 {@code {"ptId": null}} 表示全量；body 整个不带也按全量处理
     * （大屏端的「重新计算」按钮不带参数）。
     *
     * <p><b>★ 全量重算只扫「存在环节进度记录」的配套项目</b>：
     * 一条进度都没录的配套必然算不出预警，扫它只会让回执虚高。
     * 所以新环境里 {@code scannedFacilities = 0} 是<b>正确结果</b>，不是算法坏了。
     */
    @AutoLog(value = "分级预警-手动重算")
    @ApiOperation(value = "手动重算", notes = "body 里 ptId 为空 = 全量重算；返回 created/updated/removed 等回执")
    @PostMapping(value = "/recalc")
    public Result<RecalcResultVO> recalc(@RequestBody(required = false) WarningRecalcDTO body) {
        try {
            String ptId = body == null ? null : body.getPtId();
            return Result.OK(warningService.recalc(ptId));
        } catch (Exception e) {
            return failure("重算预警失败", e);
        }
    }

    /**
     * 导出 xlsx（契约 §5.5）。
     *
     * <p>必须是 <b>GET + 文件流</b>：大屏端用
     * {@code window.open(url + "&token=" + token)} 下载（token 走查询参数，
     * jeecg 的 {@code JwtFilter} 在取不到 {@code X-Access-Token} 头时会回落读
     * {@code request.getParameter("token")}，所以这里不需要任何额外放行）。
     * <b>不要改成 POST</b> —— {@code window.open} 发不出带 body 的请求。
     *
     * <p>分页参数一律忽略（导出全部匹配行），但受 2 万行上限保护，
     * 超限时返回可读的 JSON 错误而不是把服务端拖垮。
     */
    @AutoLog(value = "分级预警-导出Excel")
    @ApiOperation(value = "导出Excel", notes = "GET 文件流；忽略分页参数；文件名 预警信息_yyyyMMddHHmmss.xlsx")
    @GetMapping(value = "/export")
    public void export(WarningQueryDTO query, HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = warningService.export(query);
            String fileName = "预警信息_"
                    + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".xlsx";
            // 中文文件名必须 URL 编码：不编码时不同浏览器给出的名字千奇百怪（或直接空名）
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
            log.warn("分级预警导出被拒绝：{}", e.getMessage());
            writeJsonError(response, e.getMessage());
        } catch (Exception e) {
            log.error("分级预警导出失败", e);
            writeJsonError(response, "导出失败：" + (e.getMessage() == null ? "内部错误" : e.getMessage()));
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
    // 内部工具
    // ==================================================================

    /**
     * 统一错误出口：业务异常给原文，其它异常给一句能懂的话并记 error 日志。
     *
     * <p>与 {@code StatQueryController#failure} 同一写法（同一工程内保持一致，
     * 前端只需认 {@code success / message} 两个字段）。
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
        } catch (Exception e) {
            log.error("回写导出错误信息失败", e);
        }
    }

    private static String safe(String message) {
        return message == null ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
