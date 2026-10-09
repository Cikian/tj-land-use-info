package org.jeecg.modules.land.archive.completion.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.completion.dto.CompletionQueryDTO;
import org.jeecg.modules.land.archive.completion.entity.CompletionArchive;
import org.jeecg.modules.land.archive.completion.service.ICompletionArchiveService;
import org.jeecg.modules.land.archive.completion.service.ICompletionExportService;
import org.jeecg.modules.land.archive.completion.support.CompletionSupport;
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
 * @Description: 竣工验收项目历史工程资料数字化档案 Excel 导出实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>实现方式与档案、提级论证、台账模块一致：用 easypoi 的 {@link ExcelExportUtil} +
 * {@link ExcelExportEntity} 显式声明列，而不是靠实体注解 ——
 * 导出列的顺序、表头文案、宽度都是验收时要对照的固定结构，不该随实体字段增删而变化。
 *
 * <p><b>★ 必须显式 {@code setType(ExcelType.XSSF)}</b>：
 * easypoi 的 {@link ExportParams} 默认是 HSSF，写出的是 OLE2 复合文档（.xls 的二进制格式），
 * 而文件名是 {@code .xlsx} —— 名字是 xlsx、内容是 xls，Excel 会直接报
 * 「文件格式或文件扩展名无效」拒绝打开（档案模块踩过这个坑，见《档案管理-实现说明》6.1）。
 *
 * <p><b>★ 30 列 = 全部查询条件列 + 全部结果列</b>：
 * 需求原文只写了「可快速导出档案信息」，没说导哪些列。这里的取舍是
 * <b>把查询面板上能筛的每一个条件都出现在导出里</b>（筛什么就能看到什么），
 * 再补齐结果列（数字化状态/页数/DPI/关联档案数等），
 * 这样导出的表既能当「档案信息报送表」，也能当「数字化进度盘点表」。
 */
@Slf4j
@Service
public class CompletionExportServiceImpl implements ICompletionExportService {

    /** 导出里的空值统一显示为 —，避免打开看到一片空白分不清「没填」与「导出丢了」 */
    private static final String EMPTY = "—";

    /** 导出上限保护：超大批量导出让调用方明确报错，而不是把服务器内存打满 */
    private static final int MAX_EXPORT_ROWS = 20000;

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private ICompletionArchiveService completionService;

    @Autowired
    private CompletionSupport completionSupport;

    @Override
    public Workbook exportArchive(CompletionQueryDTO query) {
        List<CompletionArchive> rows = completionService.queryForExport(query);
        int size = rows == null ? 0 : rows.size();
        if (size > MAX_EXPORT_ROWS) {
            throw new JeecgBootException("档案本次导出 " + size + " 行，超过 " + MAX_EXPORT_ROWS
                    + " 行的导出上限，请缩小查询范围（例如按行政区或竣工年度筛选）后再导出");
        }
        List<Map<String, Object>> data = new ArrayList<>(size);
        if (rows != null) {
            for (CompletionArchive row : rows) {
                data.add(toRow(row));
            }
        }
        ExportParams exportParams = new ExportParams("竣工验收项目历史工程资料数字化档案", "历史档案");
        // ★ 不加这一行，写出的就是 xls 内容 + xlsx 文件名，Excel 打不开
        exportParams.setType(ExcelType.XSSF);
        log.info("导出竣工验收历史档案：{} 行，导出人={}", size,
                CompletionSupport.nameOrPlaceholder(completionSupport.currentRealname()));
        return ExcelExportUtil.exportExcel(exportParams, columns(), data);
    }

    /**
     * 导出列（30 列）：序号 → 项目维度 → 时间维度 → 数字化维度 → 扫描件与留痕。
     *
     * <p>列序与档案页的表格、以及查询面板的字段顺序保持一致，
     * 这样「屏幕上从左往右看」与「Excel 里从左往右看」是同一个顺序。
     */
    private List<ExcelExportEntity> columns() {
        List<ExcelExportEntity> columns = new ArrayList<>();
        // ---- 标识与项目维度（对应查询条件：档案编号/名称/项目编号/行政区/类型/参建单位）----
        columns.add(new ExcelExportEntity("序号", "seq", 6));
        columns.add(new ExcelExportEntity("档案编号", "archiveNo", 16));
        columns.add(new ExcelExportEntity("历史项目名称", "projectName", 40));
        columns.add(new ExcelExportEntity("项目编号", "projectCode", 20));
        columns.add(new ExcelExportEntity("行政区划", "xzqh", 12));
        columns.add(new ExcelExportEntity("项目类型", "projectType", 14));
        columns.add(new ExcelExportEntity("建设单位", "buildUnit", 26));
        columns.add(new ExcelExportEntity("施工单位", "constructUnit", 26));
        columns.add(new ExcelExportEntity("设计单位", "designUnit", 26));
        columns.add(new ExcelExportEntity("监理单位", "superviseUnit", 26));
        // ---- 时间维度（对应查询条件：开工/竣工/验收三段区间）----
        columns.add(new ExcelExportEntity("开工日期", "startDate", 14));
        columns.add(new ExcelExportEntity("竣工日期", "completeDate", 14));
        columns.add(new ExcelExportEntity("验收日期", "acceptanceDate", 14));
        // ---- 投资与保管 ----
        columns.add(new ExcelExportEntity("投资额(万元)", "investAmount", 14));
        columns.add(new ExcelExportEntity("保管期限", "retention", 12));
        // ---- ★ 数字化维度（本模块重点）----
        columns.add(new ExcelExportEntity("数字化状态", "digitizeStatus", 12));
        columns.add(new ExcelExportEntity("数字化完成日期", "digitizeDate", 16));
        columns.add(new ExcelExportEntity("数字化加工单位", "digitizeOrg", 24));
        columns.add(new ExcelExportEntity("总页数", "pageCount", 10));
        columns.add(new ExcelExportEntity("文件数", "fileCount", 10));
        columns.add(new ExcelExportEntity("扫描分辨率(DPI)", "scanDpi", 16));
        // ---- 扫描件与关联项目 ----
        columns.add(new ExcelExportEntity("关联档案数", "archiveCount", 12));
        columns.add(new ExcelExportEntity("出让宗地编号", "crzdbh", 20));
        columns.add(new ExcelExportEntity("地块名称", "dkmc", 24));
        columns.add(new ExcelExportEntity("配套设施类别", "ptsslb", 14));
        columns.add(new ExcelExportEntity("关联配套项目", "ptxmmc", 24));
        // ---- 备注与留痕 ----
        columns.add(new ExcelExportEntity("备注", "remark", 40));
        columns.add(new ExcelExportEntity("创建人", "createBy", 14));
        columns.add(new ExcelExportEntity("创建时间", "createTime", 20));
        columns.add(new ExcelExportEntity("更新时间", "updateTime", 20));
        return columns;
    }

    /** 一条档案 → 一行导出数据（键必须与 {@link #columns()} 的第二个参数一致） */
    private Map<String, Object> toRow(CompletionArchive archive) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seq", archive.getSeq() == null ? EMPTY : archive.getSeq());
        row.put("archiveNo", value(archive.getArchiveNo()));
        row.put("projectName", value(archive.getProjectName()));
        row.put("projectCode", value(archive.getProjectCode()));
        row.put("xzqh", value(archive.getXzqh()));
        row.put("projectType", value(archive.getProjectType()));
        row.put("buildUnit", value(archive.getBuildUnit()));
        row.put("constructUnit", value(archive.getConstructUnit()));
        row.put("designUnit", value(archive.getDesignUnit()));
        row.put("superviseUnit", value(archive.getSuperviseUnit()));
        row.put("startDate", format(archive.getStartDate(), DATE_PATTERN));
        row.put("completeDate", format(archive.getCompleteDate(), DATE_PATTERN));
        row.put("acceptanceDate", format(archive.getAcceptanceDate(), DATE_PATTERN));
        row.put("investAmount", archive.getInvestAmount() == null ? EMPTY : archive.getInvestAmount());
        row.put("retention", value(archive.getRetention()));
        row.put("digitizeStatus", value(archive.getDigitizeStatus()));
        row.put("digitizeDate", format(archive.getDigitizeDate(), DATE_PATTERN));
        row.put("digitizeOrg", value(archive.getDigitizeOrg()));
        row.put("pageCount", archive.getPageCount() == null ? EMPTY : archive.getPageCount());
        row.put("fileCount", archive.getFileCount() == null ? EMPTY : archive.getFileCount());
        row.put("scanDpi", archive.getScanDpi() == null ? EMPTY : archive.getScanDpi());
        row.put("archiveCount", archive.getArchiveCount() == null ? 0 : archive.getArchiveCount());
        row.put("crzdbh", value(archive.getCrzdbh()));
        row.put("dkmc", value(archive.getDkmc()));
        row.put("ptsslb", value(archive.getPtsslb()));
        row.put("ptxmmc", value(archive.getPtxmmc()));
        row.put("remark", value(archive.getRemark()));
        row.put("createBy", value(archive.getCreateBy()));
        row.put("createTime", format(archive.getCreateTime(), DATETIME_PATTERN));
        row.put("updateTime", format(archive.getUpdateTime(), DATETIME_PATTERN));
        return row;
    }

    private static Object value(String text) {
        return text == null || text.trim().isEmpty() ? EMPTY : text;
    }

    private static String format(Date date, String pattern) {
        return date == null ? EMPTY : new SimpleDateFormat(pattern).format(date);
    }
}
