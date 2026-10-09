package org.jeecg.modules.land.escalation.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.escalation.dto.EscalationQueryDTO;
import org.jeecg.modules.land.escalation.entity.EscalationProject;
import org.jeecg.modules.land.escalation.service.IEscalationExportService;
import org.jeecg.modules.land.escalation.service.IEscalationProjectService;
import org.jeecg.modules.land.escalation.support.EscalationAuditSupport;
import org.jeecg.modules.land.escalation.vo.EscalationLedgerVO;
import org.jeecgframework.poi.excel.ExcelExportUtil;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.entity.params.ExcelExportEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 提级论证 Excel 导出实现（方案 2.3.3 第 2 项：可导出）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>实现方式<b>照抄档案模块</b>（{@code ArchiveServiceImpl#writeManifest} 的写法）：
 * 用 easypoi 的 {@link ExcelExportUtil} + {@link ExcelExportEntity} 显式声明列，
 * 而不是靠实体注解 —— 因为导出列的顺序、表头文案、宽度都要按设计文档 4.5 固定下来，
 * 而且新增字段不该悄悄改变导出结构。
 *
 * <p><b>★ 必须显式 {@code setType(ExcelType.XSSF)}</b>：
 * easypoi 的 {@link ExportParams} 默认是 HSSF，写出的是 OLE2 复合文档（.xls 的二进制格式），
 * 而我们把文件名命名成 {@code .xlsx} —— 名字是 xlsx、内容是 xls，
 * Excel 会直接报「文件格式或扩展名无效」拒绝打开（档案模块踩过这个坑，见设计文档 4.5 注）。
 */
@Slf4j
@Service
public class EscalationExportServiceImpl implements IEscalationExportService {

    /** 导出里的空值统一显示为 —，避免前端打开看到一片空白分不清「没填」与「导出丢了」 */
    private static final String EMPTY = "—";

    /** 导出上限保护：超大批量导出让调用方明确报错，而不是把服务器内存打满 */
    private static final int MAX_EXPORT_ROWS = 20000;

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private IEscalationProjectService projectService;

    @Autowired
    private EscalationAuditSupport auditSupport;

    // ==================================================================
    // 项目导出
    // ==================================================================

    @Override
    public Workbook exportProject(EscalationQueryDTO query) {
        List<EscalationProject> projects = projectService.queryForExport(query);
        checkExportSize(projects == null ? 0 : projects.size(), "提级论证项目");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (projects != null) {
            for (EscalationProject project : projects) {
                rows.add(toProjectRow(project));
            }
        }
        ExportParams exportParams = new ExportParams("提级论证项目", "提级论证项目");
        exportParams.setType(ExcelType.XSSF);
        log.info("导出提级论证项目：{} 行", rows.size());
        return ExcelExportUtil.exportExcel(exportParams, projectColumns(), rows);
    }

    /** 项目导出的列：查询条件列 + 结果列（设计文档 4.5「全部查询条件列 + 结果列」） */
    private List<ExcelExportEntity> projectColumns() {
        List<ExcelExportEntity> columns = new ArrayList<>();
        columns.add(new ExcelExportEntity("项目编号", "projectNo", 18));
        columns.add(new ExcelExportEntity("项目名称", "projectName", 34));
        columns.add(new ExcelExportEntity("申报单位", "declareDept", 24));
        columns.add(new ExcelExportEntity("项目类型", "projectType", 14));
        columns.add(new ExcelExportEntity("项目规模", "projectScale", 18));
        columns.add(new ExcelExportEntity("总投资(亿元)", "totalInvestment", 14));
        columns.add(new ExcelExportEntity("建设地点", "buildLocation", 28));
        columns.add(new ExcelExportEntity("申报时间", "declareDate", 14));
        columns.add(new ExcelExportEntity("项目概述", "projectSummary", 40));
        columns.add(new ExcelExportEntity("行政区划", "xzqh", 12));
        columns.add(new ExcelExportEntity("功能区", "gnq", 14));
        columns.add(new ExcelExportEntity("地块名称", "dkmc", 22));
        columns.add(new ExcelExportEntity("地块面积(㎡)", "dkArea", 14));
        columns.add(new ExcelExportEntity("规划用地性质", "ghydxz", 20));
        columns.add(new ExcelExportEntity("是否土地整理项目", "tdzlProjectText", 18));
        columns.add(new ExcelExportEntity("关联出让宗地编号", "crzdbh", 20));
        columns.add(new ExcelExportEntity("关联配套项目", "ptxmmc", 24));
        columns.add(new ExcelExportEntity("提级论证事由", "argReason", 40));
        columns.add(new ExcelExportEntity("提级论证依据", "argBasis", 40));
        columns.add(new ExcelExportEntity("必要性说明", "argNecessity", 40));
        columns.add(new ExcelExportEntity("可行性说明", "argFeasibility", 40));
        columns.add(new ExcelExportEntity("提级论证事项内容", "argContent", 40));
        columns.add(new ExcelExportEntity("办理状态", "status", 12));
        columns.add(new ExcelExportEntity("论证结果", "argResult", 14));
        columns.add(new ExcelExportEntity("论证结论", "argConclusion", 40));
        columns.add(new ExcelExportEntity("论证组织单位", "argOrg", 24));
        columns.add(new ExcelExportEntity("论证会日期", "argMeetingDate", 14));
        columns.add(new ExcelExportEntity("论证专家名单", "argExpertList", 30));
        columns.add(new ExcelExportEntity("论证完成日期", "argDate", 14));
        columns.add(new ExcelExportEntity("材料数", "materialCount", 10));
        columns.add(new ExcelExportEntity("意见记录数", "recordCount", 12));
        columns.add(new ExcelExportEntity("最新审核意见", "latestOpinion", 40));
        columns.add(new ExcelExportEntity("最新意见时间", "latestOpinionTime", 20));
        columns.add(new ExcelExportEntity("最新意见记录人", "latestOpinionBy", 16));
        columns.add(new ExcelExportEntity("备注", "remark", 24));
        columns.add(new ExcelExportEntity("创建人", "createBy", 14));
        columns.add(new ExcelExportEntity("创建时间", "createTime", 20));
        columns.add(new ExcelExportEntity("更新时间", "updateTime", 20));
        return columns;
    }

    private Map<String, Object> toProjectRow(EscalationProject project) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectNo", value(project.getProjectNo()));
        row.put("projectName", value(project.getProjectName()));
        row.put("declareDept", value(project.getDeclareDept()));
        row.put("projectType", value(project.getProjectType()));
        row.put("projectScale", value(project.getProjectScale()));
        row.put("totalInvestment", project.getTotalInvestment() == null ? EMPTY : project.getTotalInvestment());
        row.put("buildLocation", value(project.getBuildLocation()));
        row.put("declareDate", format(project.getDeclareDate(), DATE_PATTERN));
        row.put("projectSummary", value(project.getProjectSummary()));
        row.put("xzqh", value(project.getXzqh()));
        row.put("gnq", value(project.getGnq()));
        row.put("dkmc", value(project.getDkmc()));
        row.put("dkArea", project.getDkArea() == null ? EMPTY : project.getDkArea());
        row.put("ghydxz", value(project.getGhydxz()));
        // 字典 yn 的中文文案：导出给业务看，不写 0/1
        row.put("tdzlProjectText", Integer.valueOf(1).equals(project.getTdzlProject()) ? "是" : "否");
        row.put("crzdbh", value(project.getCrzdbh()));
        row.put("ptxmmc", value(project.getPtxmmc()));
        row.put("argReason", value(project.getArgReason()));
        row.put("argBasis", value(project.getArgBasis()));
        row.put("argNecessity", value(project.getArgNecessity()));
        row.put("argFeasibility", value(project.getArgFeasibility()));
        row.put("argContent", value(project.getArgContent()));
        row.put("status", value(project.getStatus()));
        row.put("argResult", value(project.getArgResult()));
        row.put("argConclusion", value(project.getArgConclusion()));
        row.put("argOrg", value(project.getArgOrg()));
        row.put("argMeetingDate", format(project.getArgMeetingDate(), DATE_PATTERN));
        row.put("argExpertList", value(project.getArgExpertList()));
        row.put("argDate", format(project.getArgDate(), DATE_PATTERN));
        row.put("materialCount", project.getMaterialCount() == null ? 0 : project.getMaterialCount());
        row.put("recordCount", project.getRecordCount() == null ? 0 : project.getRecordCount());
        row.put("latestOpinion", value(project.getLatestOpinion()));
        row.put("latestOpinionTime", format(project.getLatestOpinionTime(), DATETIME_PATTERN));
        row.put("latestOpinionBy", value(project.getLatestOpinionBy()));
        row.put("remark", value(project.getRemark()));
        row.put("createBy", value(project.getCreateBy()));
        row.put("createTime", format(project.getCreateTime(), DATETIME_PATTERN));
        row.put("updateTime", format(project.getUpdateTime(), DATETIME_PATTERN));
        return row;
    }

    // ==================================================================
    // 台账导出
    // ==================================================================

    @Override
    public Workbook exportLedger(EscalationQueryDTO query) {
        List<EscalationLedgerVO> ledger = projectService.queryLedgerForExport(query);
        checkExportSize(ledger == null ? 0 : ledger.size(), "提级论证台账");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ledger != null) {
            for (EscalationLedgerVO item : ledger) {
                rows.add(toLedgerRow(item));
            }
        }
        ExportParams exportParams = new ExportParams("提级论证台账", "提级论证台账");
        exportParams.setType(ExcelType.XSSF);
        log.info("导出提级论证台账：{} 行，导出人={}", rows.size(), auditSupport.currentRealname());
        return ExcelExportUtil.exportExcel(exportParams, ledgerColumns(), rows);
    }

    /** 台账 14 列（设计文档 5.6） */
    private List<ExcelExportEntity> ledgerColumns() {
        List<ExcelExportEntity> columns = new ArrayList<>();
        columns.add(new ExcelExportEntity("序号", "seq", 8));
        columns.add(new ExcelExportEntity("项目编号", "projectNo", 18));
        columns.add(new ExcelExportEntity("项目名称", "projectName", 34));
        columns.add(new ExcelExportEntity("申报单位", "declareDept", 24));
        columns.add(new ExcelExportEntity("行政区划", "xzqh", 12));
        columns.add(new ExcelExportEntity("项目类型", "projectType", 14));
        columns.add(new ExcelExportEntity("总投资(亿元)", "totalInvestment", 14));
        columns.add(new ExcelExportEntity("申报时间", "declareDate", 14));
        columns.add(new ExcelExportEntity("材料数", "materialCount", 10));
        columns.add(new ExcelExportEntity("办理状态", "status", 12));
        columns.add(new ExcelExportEntity("论证结果", "argResult", 14));
        columns.add(new ExcelExportEntity("最新审核意见摘要", "latestOpinion", 40));
        columns.add(new ExcelExportEntity("最近更新时间", "updateTime", 20));
        columns.add(new ExcelExportEntity("关联配套项目", "ptxmmc", 24));
        return columns;
    }

    private Map<String, Object> toLedgerRow(EscalationLedgerVO item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seq", item.getSeq() == null ? EMPTY : item.getSeq());
        row.put("projectNo", value(item.getProjectNo()));
        row.put("projectName", value(item.getProjectName()));
        row.put("declareDept", value(item.getDeclareDept()));
        row.put("xzqh", value(item.getXzqh()));
        row.put("projectType", value(item.getProjectType()));
        row.put("totalInvestment", item.getTotalInvestment() == null ? EMPTY : item.getTotalInvestment());
        row.put("declareDate", format(item.getDeclareDate(), DATE_PATTERN));
        row.put("materialCount", item.getMaterialCount() == null ? 0 : item.getMaterialCount());
        row.put("status", value(item.getStatus()));
        row.put("argResult", value(item.getArgResult()));
        row.put("latestOpinion", value(item.getLatestOpinion()));
        row.put("updateTime", format(item.getUpdateTime(), DATETIME_PATTERN));
        row.put("ptxmmc", value(item.getPtxmmc()));
        return row;
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    private void checkExportSize(int size, String what) {
        if (size > MAX_EXPORT_ROWS) {
            throw new JeecgBootException(
                    what + "本次导出 " + size + " 行，超过 " + MAX_EXPORT_ROWS
                            + " 行的导出上限，请缩小查询范围后再导出");
        }
    }

    private static Object value(String text) {
        return text == null || text.trim().isEmpty() ? EMPTY : text;
    }

    private static String format(Date date, String pattern) {
        return date == null ? EMPTY : new SimpleDateFormat(pattern).format(date);
    }
}
