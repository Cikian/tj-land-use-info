package org.jeecg.modules.land.archive.ledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.ledger.entity.RoadAcceptanceLedger;
import org.jeecg.modules.land.archive.ledger.enums.LedgerMaterial;
import org.jeecg.modules.land.archive.ledger.enums.LedgerStatus;
import org.jeecg.modules.land.archive.ledger.mapper.RoadAcceptanceLedgerMapper;
import org.jeecg.modules.land.archive.ledger.service.ILedgerImportService;
import org.jeecg.modules.land.archive.ledger.service.IRoadAcceptanceLedgerService;
import org.jeecg.modules.land.archive.ledger.support.LedgerSupport;
import org.jeecg.modules.land.archive.ledger.vo.LedgerImportResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 道路设施验收及移交资料台账 - Excel 批量补录实现（模块 C）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>实现要点（每条都是踩点不是想当然）：
 *
 * <p><b>① 空单元格 = 不改动，而不是清空。</b>
 * 用户传上来的表通常只填要补的那几列。如果按「空即清空」处理，
 * 一次导入就会把库里已有的验收日期、接收管养单位全部抹掉。
 * 因此解析时只记「提供了值的列」，写回时先读出库里的整条记录、
 * 只覆盖这些列、再整体 UPDATE —— 未涉及的列原样保留。
 *
 * <p><b>② 匹配键是台账编号，道路名称只核对不写回。</b>
 * 台账编号是单列唯一键，天然适合做匹配；道路名称允许有人工修正过的差异，
 * 自动改回去等于把人的修改抹掉，所以只给警告。
 *
 * <p><b>③ 行号按 Excel 的 1 基行号回执</b>，用户可以拿着「第 37 行」直接跳过去改。
 *
 * <p><b>④ 有错误默认整批不入库</b>（{@code skipErrorRows=false}）——
 * 批量补录最怕「一半进了一半没进」，那会让台账与中心的 Excel 对不上。
 * 需要先救一部分时可以显式勾选「跳过错误行」。
 *
 * <p><b>⑤ 功能区写进「行政区划」列会被自动归位</b>：旧数据里 192 条功能区值
 * （生态城/经开区/高新区/保税区/土地发展中心）本来就不该放在 xzqh，
 * 用户按老习惯填在这里时，系统把它挪到 gnq 并给警告，而不是写坏 xzqh。
 */
@Slf4j
@Service
public class LedgerImportServiceImpl implements ILedgerImportService {

    /** 匹配键列 */
    private static final String COL_LEDGER_NO = "台账编号";
    /** 只用于核对的列 */
    private static final String COL_ROAD_NAME = "道路名称";

    /** 模板的完整表头（顺序即列顺序；除台账编号外都可以缺列，缺的列不会被更新） */
    private static final List<String> HEADERS = Collections.unmodifiableList(Arrays.asList(
            COL_LEDGER_NO, COL_ROAD_NAME, "行政区划", "功能区", "出让宗地编号",
            "验收类型", "验收单编号", "验收日期", "验收组织单位", "验收结果", "实际竣工日期",
            "移交单位", "接收管养单位", "移交日期", "状态", "备注"));

    /** 功能区取值（与迁移脚本、台账页取值一致） */
    private static final List<String> GNQ_VALUES = Collections.unmodifiableList(Arrays.asList(
            "生态城", "经开区", "高新区", "保税区", "土地发展中心"));

    /** 视为「已归集」的输入写法 */
    private static final Set<String> YES_WORDS = new HashSet<>(Arrays.asList(
            "1", "√", "✓", "是", "有", "对", "true", "y", "yes"));
    /** 视为「未归集」的输入写法 */
    private static final Set<String> NO_WORDS = new HashSet<>(Arrays.asList(
            "0", "×", "x", "╳", "否", "无", "未", "false", "n", "no"));

    /** 文本日期可接受的分隔写法 */
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "yyyyMMdd", "yyyy-M-d", "yyyy/M/d"};

    private static final DataFormatter FORMATTER = new DataFormatter();

    @Autowired
    private IRoadAcceptanceLedgerService ledgerService;

    @Autowired
    private RoadAcceptanceLedgerMapper ledgerMapper;

    @Autowired
    private LedgerSupport ledgerSupport;

    // ==================================================================
    // 一、模板
    // ==================================================================

    @Override
    public Workbook buildTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();

        // ---------- Sheet1：补录表 ----------
        Sheet sheet = workbook.createSheet("台账资料补录");
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle textStyle = textStyle(workbook);

        Row header = sheet.createRow(0);
        List<String> allHeaders = new ArrayList<>(HEADERS);
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            allHeaders.add(materialHeader(material));
        }
        for (int i = 0; i < allHeaders.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(allHeaders.get(i));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, columnWidth(allHeaders.get(i)));
        }
        // 冻结首行，几百行数据往下翻时表头始终可见
        sheet.createFreezePane(0, 1);
        // 预留 200 行空白（带边框，方便直接粘贴），不预填任何内容，避免误导入
        for (int r = 1; r <= 200; r++) {
            Row row = sheet.createRow(r);
            for (int c = 0; c < allHeaders.size(); c++) {
                row.createCell(c).setCellStyle(textStyle);
            }
        }

        // ---------- Sheet2：填表说明 ----------
        writeInstructionSheet(workbook);
        return workbook;
    }

    /** 表头样式：加粗 + 浅底 + 居中 + 边框 */
    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle textStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private int columnWidth(String headerText) {
        if (COL_LEDGER_NO.equals(headerText)) {
            return 20 * 256;
        }
        if (COL_ROAD_NAME.equals(headerText)) {
            return 40 * 256;
        }
        if (headerText.startsWith("资料")) {
            return 12 * 256;
        }
        if (headerText.contains("日期") || headerText.contains("编号")) {
            return 16 * 256;
        }
        return 18 * 256;
    }

    private void writeInstructionSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("填表说明");
        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, 28 * 256);
        sheet.setColumnWidth(2, 70 * 256);

        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"", "道路设施验收及移交资料台账 · 批量补录说明", ""});
        lines.add(new String[]{"", "", ""});
        lines.add(new String[]{"一", "怎么用", ""});
        lines.add(new String[]{"1", "只填要补的格子", "空着的格子不会被改动（不是清空）。已填的验收日期、接收管养单位等不会被覆盖。"});
        lines.add(new String[]{"2", "匹配键 = 台账编号", "必须与系统里的台账编号完全一致。可从台账页「导出台账 Excel」导出后复制过来。"});
        lines.add(new String[]{"3", "道路名称只是核对", "与系统不一致时预览会给出警告，但不会被写回（避免抹掉人工修正过的名称）。"});
        lines.add(new String[]{"4", "先预览再导入", "先点「预览校验」看会改多少条、错在哪几行；有错时整批不入库，除非勾选「跳过错误行」。"});
        lines.add(new String[]{"5", "资料列怎么填", "填 √ / 是 / 1 表示已归集；填 × / 否 / 0 或留空表示未归集。"});
        lines.add(new String[]{"", "", ""});
        lines.add(new String[]{"二", "13 类资料", "本台账按下列 13 类资料逐项勾选；表的最后一组列与之逐一对应"});
        int seq = 1;
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            lines.add(new String[]{String.format("%02d", seq++), material.getLabel(), material.getSource()});
        }
        lines.add(new String[]{"", "", ""});
        lines.add(new String[]{"三", "状态取值", String.join(" / ", LedgerStatus.allValues())});
        lines.add(new String[]{"", "验收类型取值", "竣工验收 / 规划验收 / 档案专项验收 / 移交验收（可在「系统管理 → 数据字典」里维护）"});
        lines.add(new String[]{"", "验收结果取值", "合格 / 不合格 / 整改后合格（同上）"});
        lines.add(new String[]{"", "行政区划 / 功能区", "行政区划填 16 区；功能区填 " + String.join(" / ", GNQ_VALUES)
                + "（填到行政区划列会被自动归位到功能区并给出警告）"});

        for (int i = 0; i < lines.size(); i++) {
            Row row = sheet.createRow(i);
            String[] cells = lines.get(i);
            for (int c = 0; c < cells.length; c++) {
                row.createCell(c).setCellValue(cells[c] == null ? "" : cells[c]);
            }
        }
    }

    private String materialHeader(LedgerMaterial material) {
        return String.format("资料%02d %s", material.getSeq(), material.getLabel());
    }

    // ==================================================================
    // 二、预览
    // ==================================================================

    @Override
    public LedgerImportResultVO preview(MultipartFile file) {
        return parse(file, false, false);
    }

    // ==================================================================
    // 三、入库
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LedgerImportResultVO confirm(MultipartFile file, boolean skipErrorRows) {
        LedgerImportResultVO result = parse(file, true, skipErrorRows);
        log.info("台账批量补录：解析 {} 行 / 命中 {} 行 / 更新 {} 行 / 错误 {} 条 / 跳过错误行={} / 操作人={}",
                result.getTotalRows(), result.getMatchedRows(), result.getUpdatedRows(),
                result.getErrors().size(), skipErrorRows,
                LedgerSupport.nameOrPlaceholder(ledgerSupport.currentRealname()));
        return result;
    }

    // ==================================================================
    // 四、解析与校验（预览与入库共用同一条路径，保证「预览看到什么就导入什么」）
    // ==================================================================

    private LedgerImportResultVO parse(MultipartFile file, boolean apply, boolean skipErrorRows) {
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("请选择要导入的 Excel 文件");
        }
        LedgerImportResultVO result = new LedgerImportResultVO();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new JeecgBootException("Excel 里没有任何工作表");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new JeecgBootException("Excel 第 1 行必须是表头，请从「下载模板」开始填写");
            }
            Map<String, Integer> headerIndex = readHeader(headerRow);
            if (!headerIndex.containsKey(COL_LEDGER_NO)) {
                throw new JeecgBootException("模板不匹配：找不到「" + COL_LEDGER_NO
                        + "」列。请点「下载模板」后按模板填写（匹配键是台账编号）");
            }
            Map<Integer, LedgerMaterial> materialColumnIndex = readMaterialColumns(headerIndex);

            // ---------- 逐行解析 ----------
            List<RowData> rows = new ArrayList<>();
            Set<String> seenLedgerNos = new HashSet<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isBlankRow(row)) {
                    continue;
                }
                int excelRowNum = r + 1;   // ★ 回执用 1 基行号，与用户在 Excel 里看到的一致
                RowData data = readRow(row, excelRowNum, headerIndex, materialColumnIndex, result);
                if (data == null) {
                    continue;
                }
                result.setTotalRows(result.getTotalRows() + 1);
                if (data.ledgerNo == null) {
                    result.addError(excelRowNum, null, "「" + COL_LEDGER_NO + "」不能为空");
                    continue;
                }
                if (!seenLedgerNos.add(data.ledgerNo)) {
                    result.addError(excelRowNum, data.ledgerNo, "文件内台账编号重复，只允许出现一次");
                    continue;
                }
                rows.add(data);
            }

            // ---------- 与库内匹配 ----------
            // ★ 注意顺序：**先校验编号是否存在，再看有没有可写内容**。
            //   反过来会把「只填了编号、但编号写错了」的行当成「没内容可写，跳过」，
            //   用户永远发现不了自己的编号写错了 —— 这正是批量补录最容易踩的坑。
            if (!rows.isEmpty()) {
                List<String> ledgerNos = new ArrayList<>(rows.size());
                for (RowData data : rows) {
                    ledgerNos.add(data.ledgerNo);
                }
                Map<String, RoadAcceptanceLedger> existing = loadExisting(ledgerNos);
                List<RowData> matched = new ArrayList<>(rows.size());
                for (RowData data : rows) {
                    RoadAcceptanceLedger target = existing.get(data.ledgerNo);
                    if (target == null) {
                        result.addError(data.rowNum, data.ledgerNo,
                                "台账编号不存在或已删除，请核对（库内编号可从台账页导出后复制）");
                        continue;
                    }
                    // 道路名称只核对不写回
                    if (data.roadNameForCheck != null && target.getRoadName() != null
                            && !data.roadNameForCheck.equals(target.getRoadName().trim())) {
                        result.addWarning(String.format("第 %d 行：道路名称与系统不一致（表内「%s」/ 系统「%s」），"
                                        + "已按系统为准且不会写回",
                                data.rowNum, data.roadNameForCheck, target.getRoadName()));
                    }
                    if (!data.hasWritable) {
                        // 编号对得上，但这一行没有任何要补的内容（例如用户只粘了编号列）
                        result.setSkippedRows(result.getSkippedRows() + 1);
                        continue;
                    }
                    data.target = target;
                    matched.add(data);
                }
                // matchedRows = **真正会被更新的行数**（编号命中 且 有待写内容），
                // 有错误的行不在这里面，所以前端的「可更新」直接用这个数
                result.setMatchedRows(matched.size());

                // ---------- 是否允许写入 ----------
                boolean hasError = !result.getErrors().isEmpty();
                if (apply && hasError && !skipErrorRows) {
                    result.setAborted(true);
                    result.addWarning("存在 " + result.getErrors().size()
                            + " 条错误，按「整批不入库」处理（未写入任何数据）。"
                            + "修正后重传，或勾选「跳过错误行」只导入正确的行。");
                    return result;
                }

                if (apply) {
                    write(matched, result);
                }
            }

            if (!apply && !result.getErrors().isEmpty()) {
                result.setAborted(true);
                result.addWarning("存在 " + result.getErrors().size()
                        + " 条错误：预览不写库；直接点「确认导入」也会被拒绝，"
                        + "除非勾选「跳过错误行」。");
            }
            return result;
        } catch (JeecgBootException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.error("解析台账补录 Excel 失败", e);
            throw new JeecgBootException("解析 Excel 失败：" + e.getMessage()
                    + "（请确认上传的是 .xlsx 且使用「下载模板」得到的表头）");
        }
    }

    /** 写入：只覆盖「文件里提供了值」的列；material_count 重算 */
    private void write(List<RowData> rows, LedgerImportResultVO result) {
        for (RowData data : rows) {
            RoadAcceptanceLedger target = data.target;
            int materialBefore = LedgerMaterial.countMaterials(target);

            // 普通列（只覆盖提供了值的）
            for (Map.Entry<String, String> entry : data.textValues.entrySet()) {
                applyText(target, entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Date> entry : data.dateValues.entrySet()) {
                applyDate(target, entry.getKey(), entry.getValue());
            }
            // 13 类资料
            for (Map.Entry<Integer, Integer> entry : data.materialValues.entrySet()) {
                applyMaterial(target, entry.getKey(), entry.getValue());
            }

            LedgerSupport.fillMaterialStats(target);
            target.setUpdateBy(ledgerSupport.currentUsername());
            target.setUpdateTime(new Date());
            ledgerMapper.updateLedgerAll(target);

            result.setUpdatedRows(result.getUpdatedRows() + 1);
            int delta = target.getMaterialCount() - materialBefore;
            if (delta > 0) {
                result.setMaterialFilled(result.getMaterialFilled() + delta);
            }
        }
    }

    // ==================================================================
    // 五、单元格读取
    // ==================================================================

    /** 表头 → 列下标（去空白） */
    private Map<String, Integer> readHeader(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            String text = cellText(headerRow.getCell(c));
            if (text != null && !text.isEmpty() && !map.containsKey(text)) {
                map.put(text, c);
            }
        }
        return map;
    }

    /** 13 类资料的列下标：用「表头包含资料名」匹配，容忍用户手工调整过表头前后缀 */
    private Map<Integer, LedgerMaterial> readMaterialColumns(Map<String, Integer> headerIndex) {
        Map<Integer, LedgerMaterial> map = new HashMap<>();
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            for (Map.Entry<String, Integer> entry : headerIndex.entrySet()) {
                if (entry.getKey().contains(material.getLabel())) {
                    map.put(entry.getValue(), material);
                    break;
                }
            }
        }
        return map;
    }

    /** 读一行；返回 null 表示该行没解析出有效内容（不入统计） */
    private RowData readRow(Row row, int excelRowNum, Map<String, Integer> headerIndex,
                           Map<Integer, LedgerMaterial> materialColumnIndex,
                           LedgerImportResultVO result) {
        RowData data = new RowData();
        data.rowNum = excelRowNum;
        data.ledgerNo = trimToNull(cellText(row.getCell(headerIndex.get(COL_LEDGER_NO))));
        Integer roadNameIdx = headerIndex.get(COL_ROAD_NAME);
        data.roadNameForCheck = roadNameIdx == null ? null : trimToNull(cellText(row.getCell(roadNameIdx)));

        // ---------- 文本列 ----------
        String[] textColumns = {"功能区", "出让宗地编号", "验收类型", "验收单编号", "验收组织单位",
                "验收结果", "移交单位", "接收管养单位", "状态", "备注"};
        for (String column : textColumns) {
            Integer idx = headerIndex.get(column);
            if (idx == null) {
                continue;
            }
            String value = trimToNull(cellText(row.getCell(idx)));
            if (value == null) {
                continue;
            }
            if ("状态".equals(column)) {
                if (!LedgerStatus.isValid(value)) {
                    result.addError(excelRowNum, data.ledgerNo,
                            "状态「" + value + "」不合法，允许值：" + String.join(" / ", LedgerStatus.allValues()));
                    continue;
                }
            }
            data.textValues.put(column, value);
            data.hasWritable = true;
        }

        // ---------- 行政区划：功能区值会被自动归位 ----------
        Integer xzqhIdx = headerIndex.get("行政区划");
        if (xzqhIdx != null) {
            String xzqh = trimToNull(cellText(row.getCell(xzqhIdx)));
            if (xzqh != null) {
                if (GNQ_VALUES.contains(xzqh)) {
                    data.textValues.put("功能区", xzqh);
                    result.addWarning(String.format("第 %d 行：行政区划列填的是功能区「%s」，"
                            + "已自动归位到功能区列（行政区划保持原值不变）", excelRowNum, xzqh));
                } else {
                    data.textValues.put("行政区划", xzqh);
                }
                data.hasWritable = true;
            }
        }

        // ---------- 日期列 ----------
        String[] dateColumns = {"验收日期", "实际竣工日期", "移交日期"};
        for (String column : dateColumns) {
            Integer idx = headerIndex.get(column);
            if (idx == null) {
                continue;
            }
            Cell cell = row.getCell(idx);
            if (cell == null || cellText(cell).isEmpty()) {
                continue;
            }
            Date date = cellDate(cell);
            if (date == null) {
                result.addError(excelRowNum, data.ledgerNo,
                        column + "「" + cellText(cell) + "」不是可识别的日期，"
                                + "请用 yyyy-MM-dd 格式（或 Excel 的日期格式）");
                continue;
            }
            data.dateValues.put(column, date);
            data.hasWritable = true;
        }

        // ---------- 13 类资料 ----------
        for (Map.Entry<Integer, LedgerMaterial> entry : materialColumnIndex.entrySet()) {
            String text = trimToNull(cellText(row.getCell(entry.getKey())));
            if (text == null) {
                continue;
            }
            Integer flag = parseMaterialFlag(text);
            if (flag == null) {
                result.addError(excelRowNum, data.ledgerNo,
                        "资料「" + entry.getValue().getLabel() + "」的取值「" + text
                                + "」无法识别，请填 √ / 是 / 1（已归集）或 × / 否 / 0（未归集）");
                continue;
            }
            data.materialValues.put(entry.getValue().getSeq(), flag);
            data.hasWritable = true;
        }
        return data;
    }

    /** √/是/1 → 1；×/否/0 → 0；其它 → null（非法） */
    private Integer parseMaterialFlag(String text) {
        String value = text.trim().toLowerCase();
        if (YES_WORDS.contains(value)) {
            return 1;
        }
        if (NO_WORDS.contains(value)) {
            return 0;
        }
        return null;
    }

    private boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = 0; c < row.getLastCellNum(); c++) {
            if (!cellText(row.getCell(c)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 单元格文本（数字不会变成 1.0；公式取缓存值） */
    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return FORMATTER.formatCellValue(cell).trim();
    }

    /** 单元格日期：支持 Excel 日期格式与常见文本写法 */
    private Date cellDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue();
            }
            // 有些人把日期列设成「常规」，此时是 1900 起的序列号
            double serial = cell.getNumericCellValue();
            if (serial > 20000 && serial < 80000) {
                return DateUtil.getJavaDate(serial);
            }
            return null;
        }
        if (type == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue();
        }
        return parseDateText(cellText(cell));
    }

    private Date parseDateText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern);
                format.setLenient(false);
                return format.parse(text);
            } catch (ParseException ignored) {
                // 试下一种写法
            }
        }
        return null;
    }

    // ==================================================================
    // 六、把「列名」映射到实体字段（每个列名只在这里出现一次）
    // ==================================================================

    private void applyText(RoadAcceptanceLedger target, String column, String value) {
        switch (column) {
            case "功能区":
                target.setGnq(value);
                break;
            case "行政区划":
                target.setXzqh(value);
                break;
            case "出让宗地编号":
                target.setCrzdbh(value);
                break;
            case "验收类型":
                target.setAcceptanceType(value);
                break;
            case "验收单编号":
                target.setAcceptanceNo(value);
                break;
            case "验收组织单位":
                target.setAcceptanceOrg(value);
                break;
            case "验收结果":
                target.setAcceptanceResult(value);
                break;
            case "移交单位":
                target.setHandoverUnit(value);
                break;
            case "接收管养单位":
                target.setReceiveUnit(value);
                break;
            case "状态":
                target.setStatus(value);
                break;
            case "备注":
                target.setRemark(value);
                break;
            default:
                // 未登记的列直接忽略（模板外的多余列不该让导入失败）
                break;
        }
    }

    private void applyDate(RoadAcceptanceLedger target, String column, Date value) {
        switch (column) {
            case "验收日期":
                target.setAcceptanceDate(value);
                break;
            case "实际竣工日期":
                target.setCompleteDate(value);
                break;
            case "移交日期":
                target.setHandoverDate(value);
                break;
            default:
                break;
        }
    }

    private void applyMaterial(RoadAcceptanceLedger target, int seq, int flag) {
        LedgerMaterial material = LedgerMaterial.ordered().get(seq - 1);
        switch (material) {
            case SGXK:
                target.setHasSgxk(flag);
                break;
            case YSBG:
                target.setHasYsbg(flag);
                break;
            case JGTC:
                target.setHasJgtc(flag);
                break;
            case ZLJDBG:
                target.setHasZljdbg(flag);
                break;
            case CLJYBG:
                target.setHasCljybg(flag);
                break;
            case AJBG:
                target.setHasAjbg(flag);
                break;
            case GHYSHGZ:
                target.setHasGhyshgz(flag);
                break;
            case JGBABA:
                target.setHasJgbaba(flag);
                break;
            case DAZXYS:
                target.setHasDazxys(flag);
                break;
            case DLYJD:
                target.setHasDlyjd(flag);
                break;
            case YHXY:
                target.setHasYhxy(flag);
                break;
            case JGWJ:
                target.setHasJgwj(flag);
                break;
            case YJWJ:
                target.setHasYjwj(flag);
                break;
            default:
                break;
        }
    }

    /** 按台账编号批量取库内记录（一次查询，避免逐行查库） */
    private Map<String, RoadAcceptanceLedger> loadExisting(List<String> ledgerNos) {
        QueryWrapper<RoadAcceptanceLedger> wrapper = new QueryWrapper<>();
        wrapper.in("ledger_no", ledgerNos);
        List<RoadAcceptanceLedger> list = ledgerService.list(wrapper);
        Map<String, RoadAcceptanceLedger> map = new LinkedHashMap<>();
        if (list != null) {
            for (RoadAcceptanceLedger item : list) {
                map.put(item.getLedgerNo(), item);
            }
        }
        return map;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * @Description: 一行的解析结果（占位对象，避免解析期直接改实体）
     */
    private static class RowData {
        /** Excel 1 基行号 */
        int rowNum;
        /** 台账编号（匹配键） */
        String ledgerNo;
        /** 表里的道路名称（只核对） */
        String roadNameForCheck;
        /** 库内匹配到的记录 */
        RoadAcceptanceLedger target;
        /** 是否至少提供了一个要写回的值 */
        boolean hasWritable;
        /** 文本列：列名 → 值 */
        final Map<String, String> textValues = new LinkedHashMap<>();
        /** 日期列：列名 → 值 */
        final Map<String, Date> dateValues = new LinkedHashMap<>();
        /** 资料列：seq → 0/1 */
        final Map<Integer, Integer> materialValues = new LinkedHashMap<>();
    }
}
