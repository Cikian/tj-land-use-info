package org.jeecg.modules.land.data.imports.facility.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.data.imports.facility.FacilityImportField;
import org.jeecg.modules.land.data.imports.facility.FacilityImportResultVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 配套信息批量导入 - 公共支撑（当前用户 / 文本清洗 / 模板与回执工作簿）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>把「当前登录人」与「工作簿生成」从业务解析里拆出来，
 * 与宗地导入模块的 {@code LandImportSupport} 保持同一写法：
 * 服务层只关心「一行数据合不合法」，不关心表头样式和 Shiro 上下文。
 *
 * <p><b>★ 本模块与宗地导入在回执上的唯一结构差异：多一个「孤儿清单」sheet</b>。
 * 原因是孤儿行在「允许导入」时<b>不算错误</b>，因此它们不会出现在错误回执的行里；
 * 若不给一个单独的 sheet，用户在「允许导入」这条路径上就拿不到孤儿明细
 * （页面上能看到，但下载的 Excel 里没有，两次看到的东西不一致会很难解释）。
 * 没有孤儿时该 sheet 不生成，避免每次下载都多一个空表。
 */
@Slf4j
@Component
public class FacilityImportSupport {

    /** 模板预留的空白数据行数（带边框，方便直接粘贴；不预填任何内容，避免误导入） */
    public static final int TEMPLATE_BLANK_ROWS = 200;

    /** 模板第一个 sheet 名（沿用旧系统导出的文件名口径） */
    public static final String SHEET_TEMPLATE = "市政配套建设及进展情况信息表模板";
    /** 模板第二个 sheet 名 */
    public static final String SHEET_INSTRUCTION = "填表说明";
    /** 错误回执的 sheet 名 */
    public static final String SHEET_ERROR_REPORT = "错误回执";
    /** 孤儿清单的 sheet 名（仅在有孤儿时生成） */
    public static final String SHEET_ORPHAN = "孤儿清单";

    // ==================================================================
    // 一、当前登录人
    // ==================================================================

    /** 当前登录用户；无登录上下文时返回 null（定时任务 / 单元测试场景） */
    public LoginUser currentUser() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser) {
                return (LoginUser) principal;
            }
        } catch (Exception e) {
            log.debug("获取当前登录用户失败：{}", e.getMessage());
        }
        return null;
    }

    /** 当前登录账号，无上下文时返回 null */
    public String currentUsername() {
        LoginUser user = currentUser();
        return user == null ? null : user.getUsername();
    }

    /** 当前登录人姓名，取不到时回退为账号 */
    public String currentRealname() {
        LoginUser user = currentUser();
        if (user == null) {
            return null;
        }
        return StringUtils.isNotBlank(user.getRealname()) ? user.getRealname() : user.getUsername();
    }

    /** 姓名取不到时给一个明确占位，避免导出里出现空白单元格 */
    public static String nameOrPlaceholder(String name) {
        return StringUtils.isBlank(name) ? "未知" : name;
    }

    // ==================================================================
    // 二、文本清洗
    // ==================================================================

    /**
     * 剔除 ASCII 10~13 控制字符（{@code \n \r \v \f}）。
     *
     * <p>沿用旧系统的清洗口径，与宗地导入模块逐字一致：单元格里从 Word/网页
     * 粘贴过来的换行与软回车不剔掉会写进库，还会把
     * 「(宗地编号, 配套项目名称) 是否重复」的比对搞挂
     * （末尾多一个不可见字符，看起来完全一样的两行并不相等）。
     */
    public static String stripControlChars(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 10 && c <= 13) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** 去首尾空白 → 空串归一化为 null */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = stripControlChars(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 按列宽截断超长文本，超长时返回截断后的值。
     *
     * <p>★ 与宗地导入同一取舍：选「截断 + 警告」而不是「报错」。
     * 超长通常来自用户误把整段说明贴进了「备注（varchar(100)）」这类短列，
     * 截断能保住整批导入，警告能让用户看到并回去改；
     * 若直接报错，用户为了导进去 700 行会去改一个只影响 1 行的字段，得不偿失。
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    // ==================================================================
    // 三、模板工作簿
    // ==================================================================

    /**
     * 生成导入模板。
     *
     * <p>结构照搬旧系统 {@code getExcelDemo()}（它按
     * {@code information_schema.COLUMNS} 生成两行表头）：
     * <pre>
     *   sheet1「市政配套建设及进展情况信息表模板」
     *     第 1 行：英文字段名（crzdbh / ptxmmc / …）  ← 解析器按这一行定位列
     *     第 2 行：中文说明（出让宗地编号 / 配套项目名称 / …）
     *     第 3 行起：数据
     *   sheet2「填表说明」
     *     用法说明 + 每个字段一行：列名 / 中文名 / 类型 / 是否必填 / 取值 / 示例
     * </pre>
     *
     * <p><b>★ 与旧模板的一处有意差异</b>：旧模板是按「表里的列」机械生成的
     * （连 {@code createAccount} 这种系统列都排除了，却没有排除
     * 实体里根本不存在的列）。本模板只放<b>真正能写进实体</b>的列 ——
     * 模板里有一列却写不进去，是用户最难自查的一类问题。
     */
    public Workbook buildTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle subHeaderStyle = subHeaderStyle(workbook);
        CellStyle textStyle = textStyle(workbook);

        List<FacilityImportField.FieldSpec> fields = FacilityImportField.templated();

        Sheet sheet = workbook.createSheet(SHEET_TEMPLATE);
        Row columnRow = sheet.createRow(0);
        Row labelRow = sheet.createRow(1);
        for (int i = 0; i < fields.size(); i++) {
            FacilityImportField.FieldSpec spec = fields.get(i);

            Cell columnCell = columnRow.createCell(i);
            columnCell.setCellValue(spec.getColumn());
            columnCell.setCellStyle(headerStyle);

            Cell labelCell = labelRow.createCell(i);
            labelCell.setCellValue(labelText(spec));
            labelCell.setCellStyle(subHeaderStyle);

            sheet.setColumnWidth(i, columnWidth(spec));
        }
        // 冻结前两行：几百行数据往下翻时，英文字段名与中文名始终可见
        sheet.createFreezePane(0, 2);
        for (int r = 2; r < 2 + TEMPLATE_BLANK_ROWS; r++) {
            Row row = sheet.createRow(r);
            for (int c = 0; c < fields.size(); c++) {
                row.createCell(c).setCellStyle(textStyle);
            }
        }

        writeInstructionSheet(workbook, fields);
        return workbook;
    }

    /** 第 2 行表头文案：中文名 + 必填标记 */
    private String labelText(FacilityImportField.FieldSpec spec) {
        return spec.isRequired() ? spec.getLabel() + "（必填）" : spec.getLabel();
    }

    private void writeInstructionSheet(Workbook workbook, List<FacilityImportField.FieldSpec> fields) {
        Sheet sheet = workbook.createSheet(SHEET_INSTRUCTION);
        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 30 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 10 * 256);
        sheet.setColumnWidth(4, 52 * 256);
        sheet.setColumnWidth(5, 26 * 256);

        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"市政配套建设及进展情况 · 批量导入填表说明", "", "", "", "", ""});
        lines.add(new String[]{"", "", "", "", "", ""});
        lines.add(new String[]{"一、怎么用", "", "", "", "", ""});
        lines.add(new String[]{"1", "表头不要改",
                "第 1 行是英文字段名（程序按它认列），第 2 行是中文说明。删列、换列顺序都可以，"
                        + "但不要改第 1 行的英文名。", "", "", ""});
        lines.add(new String[]{"2", "数据从第 3 行开始",
                "一行一个配套项目；同一块地可以有多行（配套不同）。空行会被忽略。", "", "", ""});
        lines.add(new String[]{"3", "先预览再导入",
                "点「选择 Excel」后先点「预览校验」：会告诉你会新增多少条、哪几行有问题、"
                        + "哪些行找不到宗地；有错时默认整批不入库（一行都不会写）。", "", "", ""});
        lines.add(new String[]{"4", "重复的判定",
                "「出让宗地编号 + 配套项目名称」两项都相同才算重复。同一块地上配套名称不能重名，"
                        + "文件内重复与与库里重复都算。", "", "", ""});
        lines.add(new String[]{"5", "重复的处理",
                "按你选的策略：重复即报错（默认）/ 跳过重复只导新增 / 覆盖更新"
                        + "（覆盖时文件中空着的单元格不会把库里的值清空）。", "", "", ""});
        lines.add(new String[]{"6", "★ 宗地必须先在系统里",
                "每一行的「出让宗地编号」都要能在系统里查到，否则默认报错不导入。"
                        + "如果确实要先导配套、后补宗地，请勾选「允许挂到未登记宗地」，"
                        + "这些行会记入「孤儿清单」照常导入，但请把清单交给维护宗地的同事补录。",
                "", "", ""});
        lines.add(new String[]{"7", "错误回执",
                "预览或导入后可以「下载错误回执」：回执里保留你填的内容 + 一列「错误原因」，"
                        + "改完那几行直接重传即可；有孤儿时回执里会多一个「孤儿清单」sheet。",
                "", "", ""});
        lines.add(new String[]{"8", "日期与数字",
                "日期写 2025-03-01 或 2025/3/1 或直接用 Excel 的日期格式；"
                        + "数字不要带「米」「万元」「㎡」等单位，单位已写在列名里。", "", "", ""});
        lines.add(new String[]{"9", "档案要件列",
                "「项建批复文件 / 可研批复文件 / 道路规划 …」这些列只填「是 / 否」，"
                        + "是旧系统的历史标志位（新数据请改用附件管理上传真实文件）。",
                "", "", ""});
        lines.add(new String[]{"", "", "", "", "", ""});
        lines.add(new String[]{"二、字段清单（★ = 必填）", "", "", "", "", ""});
        lines.add(new String[]{"列名（第 1 行）", "中文名（第 2 行）", "类型", "必填", "取值 / 说明", "示例"});
        for (FacilityImportField.FieldSpec spec : fields) {
            lines.add(new String[]{
                    spec.getColumn(),
                    spec.getLabel(),
                    typeText(spec),
                    spec.isRequired() ? "★" : "",
                    valueHint(spec),
                    StringUtils.defaultString(spec.getSample())});
        }
        lines.add(new String[]{"", "", "", "", "", ""});
        lines.add(new String[]{"三、由系统自动填写的列（模板里没有）", "", "", "", "", ""});
        lines.add(new String[]{"id", "主键", "文本", "", "系统生成，导入时自动分配", ""});
        lines.add(new String[]{"delFlag", "删除状态（驼峰列名）", "文本", "",
                "新导入记录固定为 '0'；本列是 varchar('0'/'1')，不是数字", ""});
        lines.add(new String[]{"createAccount / createTime", "创建账号 / 创建时间", "文本 / 时间", "",
                "取当前登录人，导入时自动写入", ""});
        lines.add(new String[]{"jgwj / yjwj", "竣工文件 / 移交文件", "文本", "",
                "旧表有这两列但实体未建模，本模板不含；竣工与移交文件请走附件管理", ""});

        for (int i = 0; i < lines.size(); i++) {
            Row row = sheet.createRow(i);
            String[] cells = lines.get(i);
            for (int c = 0; c < cells.length; c++) {
                row.createCell(c).setCellValue(cells[c] == null ? "" : cells[c]);
            }
        }
    }

    private String typeText(FacilityImportField.FieldSpec spec) {
        switch (spec.getType()) {
            case NUMBER:
                return "数值";
            case DATE:
                return "日期";
            case YES_NO:
                return "是/否";
            case ENUM:
                return "枚举";
            default:
                return "文本";
        }
    }

    private String valueHint(FacilityImportField.FieldSpec spec) {
        StringBuilder sb = new StringBuilder();
        if (spec.getType() == FacilityImportField.FieldType.YES_NO) {
            sb.append("填「是」或「否」（也认 √/×/1/0）。");
        } else if (spec.getType() == FacilityImportField.FieldType.ENUM) {
            sb.append("只能填：").append(spec.optionText()).append("。");
        } else if (spec.getType() == FacilityImportField.FieldType.NUMBER) {
            sb.append("只填数字，最多两位小数。");
        } else if (spec.getType() == FacilityImportField.FieldType.DATE) {
            sb.append("只填日期，如 2025-03-01。");
        } else {
            sb.append("文本。");
        }
        if (spec.getMaxLength() > 0) {
            sb.append("最长 ").append(spec.getMaxLength()).append(" 字，超出会被截断并提示。");
        }
        if (FacilityImportField.COL_CRZDBH.equals(spec.getColumn())) {
            sb.append("★ 必须与系统里已有的宗地编号逐字一致（注意全角/半角括号与末尾的「号」字）；"
                    + "查不到的行会计入孤儿清单。");
        }
        if (FacilityImportField.COL_PTXMMC.equals(spec.getColumn())) {
            sb.append("★ 与宗地编号一起构成业务唯一键：同一宗地下不能重名。");
        }
        if ("lxdh".equals(spec.getColumn())) {
            sb.append("格式：1 开头的 11 位手机号。");
        }
        if ("ptsslb".equals(spec.getColumn())) {
            sb.append("旧写法「供气」「给水管线」「市政道路」会自动归一到标准类别；"
                    + "「道路及管线」这类跨两类的写法请拆成两行。");
        }
        if ("xzqh".equals(spec.getColumn())) {
            sb.append("只填标准行政区；功能区（生态城/经开区/高新区等）不是行政区，不接受。");
        }
        return sb.toString();
    }

    private int columnWidth(FacilityImportField.FieldSpec spec) {
        switch (spec.getColumn()) {
            case "crzdbh":
                return 24 * 256;
            case "ptxmmc":
                return 30 * 256;
            case "dkmc":
                return 28 * 256;
            case "jsdw":
            case "sjdw":
            case "kcdw":
            case "jldw":
            case "sgdw":
            case "jsgydw":
                return 26 * 256;
            case "jtwt":
            case "zlqsnrjsm":
                return 34 * 256;
            case "lxdh":
                return 16 * 256;
            default:
                break;
        }
        if (spec.getType() == FacilityImportField.FieldType.DATE) {
            return 16 * 256;
        }
        return 18 * 256;
    }

    // ==================================================================
    // 四、错误回执工作簿
    // ==================================================================

    /**
     * 生成错误回执。
     *
     * <p>结构：第 1 行 = 与导入模板完全一致的英文字段名表头（用户能直接把改好的回执当导入文件重传），
     * 最后追加 3 列诊断信息（Excel 行号 / 错误字段 / 错误原因）。
     * 行序与文件顺序一致，用户从上往下改即可。
     *
     * <p>若本次存在孤儿行，追加第二个 sheet「孤儿清单」
     * （列：Excel 行号 / 出让宗地编号 / 配套项目名称 / 说明）。
     * ★ 孤儿与错误的区别（见 {@link FacilityImportResultVO} 类注释）决定了它们必须分开列：
     * 错误行是「这行没导进去」，孤儿行在「允许导入」时是「导进去了但挂不上宗地」，
     * 混在一张表里会让用户按错误的处理方式去重填孤儿行。
     *
     * @param result 预览或入库的结果（读它的 errors 与 orphans）
     * @param source 原始上传文件解析出来的「行号 → 该行原始单元格文本」（保持用户填的内容）
     */
    public Workbook buildErrorReport(FacilityImportResultVO result, ErrorReportSource source) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle errorStyle = errorCellStyle(workbook);
        CellStyle textStyle = textStyle(workbook);

        List<FacilityImportField.FieldSpec> fields = FacilityImportField.templated();
        Sheet sheet = workbook.createSheet(SHEET_ERROR_REPORT);

        Row header = sheet.createRow(0);
        int columnIndex = 0;
        for (FacilityImportField.FieldSpec spec : fields) {
            Cell cell = header.createCell(columnIndex++);
            cell.setCellValue(spec.getColumn());
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(columnIndex - 1, columnWidth(spec));
        }
        // 诊断列固定追加在末尾
        String[] diagnosticHeaders = {"__excel行号", "__错误字段", "__错误原因"};
        int[] diagnosticWidths = {12, 18, 60};
        for (int i = 0; i < diagnosticHeaders.length; i++) {
            Cell cell = header.createCell(columnIndex);
            cell.setCellValue(diagnosticHeaders[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(columnIndex, diagnosticWidths[i] * 256);
            columnIndex++;
        }
        sheet.createFreezePane(0, 1);

        int rowIndex = 1;
        if (result != null && result.getErrors() != null) {
            for (FacilityImportResultVO.RowError error : result.getErrors()) {
                Row row = sheet.createRow(rowIndex++);
                // 原始内容（保持用户填的东西，含那些没填的列）
                List<String> values = source == null ? null : source.valuesOf(error.getRowNum());
                for (int c = 0; c < fields.size(); c++) {
                    String value = values != null && c < values.size() ? values.get(c) : null;
                    Cell cell = row.createCell(c);
                    cell.setCellValue(value == null ? "" : value);
                    cell.setCellStyle(textStyle);
                }
                Cell rowNumCell = row.createCell(columnIndex - 3);
                rowNumCell.setCellValue(error.getRowNum());
                rowNumCell.setCellStyle(errorStyle);
                Cell columnCell = row.createCell(columnIndex - 2);
                columnCell.setCellValue(StringUtils.defaultString(error.getColumnLabel(),
                        StringUtils.defaultString(error.getColumn(), "—")));
                columnCell.setCellStyle(errorStyle);
                Cell messageCell = row.createCell(columnIndex - 1);
                messageCell.setCellValue(StringUtils.defaultString(error.getMessage()));
                messageCell.setCellStyle(errorStyle);
            }
        }
        writeOrphanSheet(workbook, result, headerStyle, textStyle);
        writeNoteSheet(workbook, fields, result);
        return workbook;
    }

    /** 孤儿清单 sheet（无孤儿时不生成，避免每次下载都多一个空表） */
    private void writeOrphanSheet(Workbook workbook, FacilityImportResultVO result,
                                  CellStyle headerStyle, CellStyle textStyle) {
        if (result == null || result.getOrphans() == null || result.getOrphans().isEmpty()) {
            return;
        }
        Sheet sheet = workbook.createSheet(SHEET_ORPHAN);
        String[] headers = {"__excel行号", "出让宗地编号", "配套项目名称", "说明"};
        int[] widths = {12, 26, 30, 70};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.createFreezePane(0, 1);

        int rowIndex = 1;
        for (FacilityImportResultVO.OrphanRow orphan : result.getOrphans()) {
            Row row = sheet.createRow(rowIndex++);
            Cell rowNumCell = row.createCell(0);
            rowNumCell.setCellValue(orphan.getRowNum());
            rowNumCell.setCellStyle(textStyle);
            setCell(row.createCell(1), orphan.getCrzdbh(), textStyle);
            setCell(row.createCell(2), orphan.getPtxmmc(), textStyle);
            setCell(row.createCell(3), orphan.getReason(), textStyle);
        }
    }

    private void setCell(Cell cell, String value, CellStyle style) {
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    /** 说明 sheet：几句话讲清楚怎么用这份回执（内容随「有没有孤儿」变化） */
    private void writeNoteSheet(Workbook workbook, List<FacilityImportField.FieldSpec> fields,
                                FacilityImportResultVO result) {
        Sheet note = workbook.createSheet("回执说明");
        note.setColumnWidth(0, 100 * 256);
        boolean hasOrphan = result != null && result.getOrphans() != null && !result.getOrphans().isEmpty();
        List<String> notes = new ArrayList<>();
        notes.add("市政配套建设及进展情况批量导入 · 错误回执");
        notes.add("");
        notes.add("1. 前 " + fields.size() + " 列与导入模板完全一致，是你上传文件里的原始内容；"
                + "末尾 3 列是系统给出的诊断信息（__excel行号 / __错误字段 / __错误原因）。");
        notes.add("2. 改完之后，可以删掉末尾 3 列，把这份文件直接当导入文件重新上传（表头与模板一致）。");
        notes.add("3. 若同一行有多个字段出错，会分行列出（同一 __excel行号 出现多次）。");
        if (hasOrphan) {
            notes.add("4. 「" + SHEET_ORPHAN + "」sheet 里的行，其「出让宗地编号」在系统里查不到。"
                    + "本次" + (result.isAllowOrphan() ? "已按你的选择「允许挂到未登记宗地」导入"
                    : "未导入（不允许挂到未登记宗地）")
                    + "；请把这份清单交给维护宗地信息的同事，先补录宗地。");
            notes.add("5. 若编号只是写法不一致（少「号」字、全角/半角括号不同），"
                    + "在错误原因列里会给出「疑似应为 xxx」，按提示改写法即可。");
        } else {
            notes.add("4. 本次没有「找不到宗地」的行（每一行的出让宗地编号都能在系统里查到）。");
        }
        notes.add((hasOrphan ? "6" : "5") + ". 错误原因以「预览校验」那一刻的库内数据为准；"
                + "如果期间别人改了库，请重新预览。");
        for (int i = 0; i < notes.size(); i++) {
            note.createRow(i).createCell(0).setCellValue(notes.get(i));
        }
    }

    /**
     * @Description: 错误回执的数据源 —— 把「Excel 行号」映射回该行的原始单元格文本
     *
     * <p>做成接口是为了让 {@link #buildErrorReport} 不依赖解析器的内部结构：
     * 解析器解析时顺手把「行号 → 原始值」塞进一个实现类即可。
     */
    public interface ErrorReportSource {

        /** 该 Excel 行号对应的原始列值（顺序与 {@link FacilityImportField#templated()} 一致），无该行返回 null */
        List<String> valuesOf(int rowNum);
    }

    // ==================================================================
    // 五、样式
    // ==================================================================

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        border(style);
        return style;
    }

    private CellStyle subHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        border(style);
        return style;
    }

    private CellStyle textStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        border(style);
        return style;
    }

    private CellStyle errorCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        style.setWrapText(true);
        border(style);
        return style;
    }

    private void border(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
