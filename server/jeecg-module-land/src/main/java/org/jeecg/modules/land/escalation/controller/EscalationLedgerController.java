package org.jeecg.modules.land.escalation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.escalation.dto.EscalationQueryDTO;
import org.jeecg.modules.land.escalation.service.IEscalationExportService;
import org.jeecg.modules.land.escalation.service.IEscalationProjectService;
import org.jeecg.modules.land.escalation.vo.EscalationLedgerVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @Description: 提级论证数据资料及台账管理（方案 2.3.3 第 3 项：台账方式管理，快速查看各项目当前状态）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/escalation/ledger}。
 *
 * <p><b>权限码</b>：列表与状态计数归 {@code land:escalation:list}，
 * 台账导出归 {@code land:escalation:export}（台账不单设权限码，见设计文档 2.5）。
 *
 * <p>接口清单（本类 3 个）：
 * <pre>
 *   GET /land/escalation/ledger/list          台账分页（14 列所需的窄字段）
 *   GET /land/escalation/ledger/countByStatus 各办理状态计数（固定 4 项，缺的补 0）
 *   GET /land/escalation/ledger/exportXls     台账导出 Excel
 * </pre>
 *
 * <p>台账直接读主表冗余列（{@code material_count} / {@code latest_opinion}），
 * <b>不 JOIN 材料表与意见表</b>：这是「快速查看」的性能前提（设计文档 3.2 第 7 条）。
 */
@Slf4j
@Api(tags = "提级论证-资料及台账管理")
@RestController
@RequestMapping("/land/escalation/ledger")
public class EscalationLedgerController {

    /** 权限码：查询（台账列表 / 状态计数） */
    private static final String PERM_LIST = "land:escalation:list";
    /** 权限码：导出 */
    private static final String PERM_EXPORT = "land:escalation:export";

    @Autowired
    private IEscalationProjectService projectService;

    @Autowired
    private IEscalationExportService exportService;

    @AutoLog(value = "提级论证台账-分页列表查询")
    @ApiOperation(value = "提级论证台账-分页列表查询",
            notes = "14 列：序号/项目编号/项目名称/申报单位/行政区划/项目类型/总投资/申报时间/材料数/办理状态/论证结果/最新意见摘要/更新时间/操作")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/list")
    public Result<IPage<EscalationLedgerVO>> list(EscalationQueryDTO query) {
        return Result.OK(projectService.queryLedgerPage(query));
    }

    @AutoLog(value = "提级论证台账-各状态计数")
    @ApiOperation(value = "提级论证台账-各状态计数",
            notes = "固定返回未办理/办理中/已办结/已归档 4 项，数量为 0 也返回，前端 tab 角标直接用")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/countByStatus")
    public Result<List<EscalationLedgerVO.StatusCount>> countByStatus(EscalationQueryDTO query) {
        return Result.OK(projectService.countByStatus(query));
    }

    /**
     * 台账导出。
     *
     * <p>与台账列表<b>共用同一套查询条件</b>，所以导出的行就是屏幕上的行；
     * 文件里的「序号」列由 SQL 侧生成，与列表看到的序号一致。
     */
    @AutoLog(value = "提级论证台账-导出Excel")
    @ApiOperation(value = "提级论证台账-导出Excel", notes = "文件名：提级论证台账_{yyyyMMddHHmm}.xlsx")
    @RequiresPermissions(PERM_EXPORT)
    @GetMapping(value = "/exportXls")
    public void exportXls(EscalationQueryDTO query, HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = exportService.exportLedger(query);
            String fileName = "提级论证台账_" + new SimpleDateFormat("yyyyMMddHHmm").format(new Date()) + ".xlsx";
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
        } catch (Exception e) {
            log.error("导出台账失败", e);
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

    /** 已提交响应时不再写错误信息 */
    private void writeJsonError(HttpServletResponse response, String message) {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\""
                    + (message == null ? "未知错误" : message.replace("\"", "'")) + "\"}");
        } catch (Exception e) {
            log.warn("回写错误信息失败：{}", e.getMessage());
        }
    }
}
