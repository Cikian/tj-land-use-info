package org.jeecg.modules.land.data.oplog.service.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;
import org.jeecg.modules.land.data.oplog.mapper.DataChangeLogMapper;
import org.jeecg.modules.land.data.oplog.support.ChangeLogSupport;
import org.jeecg.modules.land.data.support.DataSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 按导入批次导出字段级变更，数据源仅为 t_data_change_log，不读取业务表最终值。 */
@Service
public class DataChangeLogExportServiceImpl {

    private static final Pattern EXCEL_ROW = Pattern.compile("Excel 第 (\\d+) 行");
    private static final String[] HEADERS = {
            "批次号", "业务类型", "Excel 行号", "宗地编号", "配套项目名称",
            "字段中文名", "修改前", "修改后", "操作人", "时间"};

    @Autowired
    private DataChangeLogMapper changeLogMapper;

    @Autowired
    private ChangeLogSupport changeLogSupport;

    public Workbook buildBatchExport(String batchId) {
        if (StringUtils.isBlank(batchId) || !batchId.matches("[0-9a-fA-F]{32}")) {
            throw new JeecgBootException("批次号格式不正确");
        }
        List<DataChangeLog> logs = changeLogMapper.selectByBatch(batchId.trim());
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("本批变更明细");
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, (i == 0 ? 36 : (i >= 5 && i <= 7 ? 32 : 20)) * 256);
        }
        sheet.createFreezePane(0, 1);

        int rowIndex = 1;
        for (DataChangeLog log : logs) {
            List<Map<String, Object>> details = changeLogSupport.parseDetail(log.getChangeDetail());
            for (Map<String, Object> detail : details) {
                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                row.createCell(col++).setCellValue(batchId);
                row.createCell(col++).setCellValue(DataSupport.BIZ_LAND.equals(log.getBizType())
                        ? "经营性用地" : "配套项目");
                Integer excelRow = excelRow(log.getChangeSummary());
                if (excelRow != null) {
                    row.createCell(col++).setCellValue(excelRow);
                } else {
                    row.createCell(col++).setCellValue("");
                }
                if (DataSupport.BIZ_LAND.equals(log.getBizType())) {
                    row.createCell(col++).setCellValue(StringUtils.defaultString(log.getBizKey()));
                    row.createCell(col++).setCellValue("");
                } else {
                    row.createCell(col++).setCellValue("");
                    row.createCell(col++).setCellValue(StringUtils.defaultString(log.getBizKey()));
                }
                row.createCell(col++).setCellValue(StringUtils.defaultString(
                        (String) detail.get("label"), (String) detail.get("field")));
                row.createCell(col++).setCellValue(display(detail.get("before")));
                row.createCell(col++).setCellValue(display(detail.get("after")));
                row.createCell(col++).setCellValue(StringUtils.defaultString(log.getOperatorName(), log.getOperator()));
                row.createCell(col).setCellValue(log.getCreateTime() == null ? ""
                        : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(log.getCreateTime()));
            }
        }
        return workbook;
    }

    private Integer excelRow(String summary) {
        if (summary == null) {
            return null;
        }
        Matcher matcher = EXCEL_ROW.matcher(summary);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String display(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
