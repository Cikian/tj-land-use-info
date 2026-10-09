package org.jeecg.modules.land.data.imports.support;

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
import org.jeecg.modules.land.data.imports.LandImportField;
import org.jeecg.modules.land.data.imports.LandImportResultVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 经营性用地批量导入 - 公共支撑（当前用户 / 文本清洗 / 模板与回执工作簿）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>把「当前登录人」与「工作簿生成」从业务解析里拆出来，
 * 与台账补录模块的 {@code LedgerSupport} 保持同一写法：
 * 服务层只关心「一行数据合不合法」，不关心表头样式和 Shiro 上下文。
 */
@Slf4j
@Component
public class LandImportSupport {

    /** 模板预留的空白数据行数（带边框，方便直接粘贴；不预填任何内容，避免误导入） */
    public static final int TEMPLATE_BLANK_ROWS = 200;

    /** 模板第一个 sheet 名 */
    public static final String SHEET_TEMPLATE = "经营性用地信息表模板";
    /** 模板第二个 sheet 名 */
    public static final String SHEET_INSTRUCTION = "填表说明";
    /** 错误回执的 sheet 名 */
    public static final String SHEET_ERROR_REPORT = "错误回执";

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
     * <p>沿用旧系统的清洗口径（{@code importData} 里那个 {@code for(i=10;i<14;i++)} 循环）：
     * 中心从 Word/网页里粘贴过来的单元格经常夹着换行与软回车，
     * 不剔掉会写进库里，还会把后续的「重复编号」比对搞挂
     * （末尾多个不可见字符，看起来一样的两个编号并不相等）。
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
     * <p>★ 业务上<b>选择了「截断 + 警告」而不是「报错」</b>：
     * 超长通常来自用户误把整段说明贴进了「地块名称」这类短列，
     * 截断能保住整批导入，警告能让用户看到并回去改。
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
     * <p>结构照搬旧系统 {@code getExcelDemo()}：
     * <pre>
     *   sheet1「经营性用地信息表模板」
     *     第 1 行：英文字段名（crzdbh / xmfl / …）  ← 解析器按这一行定位列
     *     第 2 行：中文说明（出让宗地编号 / 项目分类 / …）
     *     第 3 行起：数据
     *   sheet2「填表说明」
     *     每个字段一行：列名 / 中文名 / 类型 / 是否必填 / 取值 / 示例
     * </pre>
     *
     * <p><b>★ 为什么第 2 行是「中文说明」而不是空行</b>：
     * 旧系统就是用中文说明占第 2 行，中心手里的历史 Excel 全是这个形状。
     * 保持同样形状，用户可以直接把旧文件传进来；解析器也会识别并跳过这一行
     * （见 {@code LandImportServiceImpl.isSecondHeaderRow}）。
     */
    public Workbook buildTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle subHeaderStyle = subHeaderStyle(workbook);
        CellStyle textStyle = textStyle(workbook);

        List<LandImportField.FieldSpec> fields = LandImportField.templated();

        Sheet sheet = workbook.createSheet(SHEET_TEMPLATE);
        Row columnRow = sheet.createRow(0);
        Row labelRow = sheet.createRow(1);
        for (int i = 0; i < fields.size(); i++) {
            LandImportField.FieldSpec spec = fields.get(i);

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
    private String labelText(LandImportField.FieldSpec spec) {
        return spec.isRequired() ? spec.getLabel() + "（必填）" : spec.getLabel();
    }

    private void writeInstructionSheet(Workbook workbook, List<LandImportField.FieldSpec> fields) {
        Sheet sheet = workbook.createSheet(SHEET_INSTRUCTION);
        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 30 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 10 * 256);
        sheet.setColumnWidth(4, 44 * 256);
        sheet.setColumnWidth(5, 26 * 256);

        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"经营性用地信息 · 批量导入填表说明", "", "", "", "", ""});
        lines.add(new String[]{"", "", "", "", "", ""});
        lines.add(new String[]{"一、怎么用", "", "", "", "", ""});
        lines.add(new String[]{"1", "表头不要改",
                "第 1 行是英文字段名（程序按它认列），第 2 行是中文说明。删列、换列顺序都可以，"
                        + "但不要改第 1 行的英文名。", "", "", ""});
        lines.add(new String[]{"2", "数据从第 3 行开始", "一行一个地块，空行会被忽略。", "", "", ""});
        lines.add(new String[]{"3", "先预览再导入",
                "点「选择 Excel」后先点「预览校验」：会告诉你会新增多少条、哪几行有问题；"
                        + "有错时默认整批不入库（一行都不会写）。", "", "", ""});
        lines.add(new String[]{"4", "错误回执",
                "预览或导入后可以「下载错误回执」：回执里保留你填的内容 + 一列「错误原因」，"
                        + "改完那几行直接重传即可。", "", "", ""});
        lines.add(new String[]{"5", "重复宗地编号",
                "同一份文件里 crzdbh 只能出现一次；与库里已有的重复时，按你选的策略处理："
                        + "重复即报错（默认）/ 跳过重复只导新增 / 覆盖更新（空单元格不会把原值清空）。", "", "", ""});
        lines.add(new String[]{"6", "日期与数字",
                "日期写 2024-06-18 或 2024/6/18 或直接用 Excel 的日期格式；"
                        + "数字不要带「亿元」「万元」「㎡」等单位，单位已写在列名里。", "", "", ""});
        lines.add(new String[]{"", "", "", "", "", ""});
        lines.add(new String[]{"二、字段清单（★ = 必填）", "", "", "", "", ""});
        lines.add(new String[]{"列名（第 1 行）", "中文名（第 2 行）", "类型", "必填", "取值 / 说明", "示例"});
        for (LandImportField.FieldSpec spec : fields) {
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
        lines.add(new String[]{"wcd", "完成度", "数值", "", "由配套竣工验收情况计算，不接受人工填写", ""});
        lines.add(new String[]{"del_flag", "删除状态", "数值", "", "新导入记录固定为 0（正常）", ""});
        lines.add(new String[]{"create_by / create_time", "创建人 / 创建时间", "文本 / 时间", "",
                "取当前登录人，导入时自动写入", ""});
        lines.add(new String[]{"source_id", "旧库主键", "文本", "",
                "仅供数据迁移溯源，导入时不写", ""});

        for (int i = 0; i < lines.size(); i++) {
            Row row = sheet.createRow(i);
            String[] cells = lines.get(i);
            for (int c = 0; c < cells.length; c++) {
                row.createCell(c).setCellValue(cells[c] == null ? "" : cells[c]);
            }
        }
    }

    private String typeText(LandImportField.FieldSpec spec) {
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

    private String valueHint(LandImportField.FieldSpec spec) {
        StringBuilder sb = new StringBuilder();
        if (spec.getType() == LandImportField.FieldType.YES_NO) {
            sb.append("填「是」或「否」（也认 √/×/1/0）。");
        } else if (spec.getType() == LandImportField.FieldType.ENUM) {
            sb.append("只能填：").append(spec.optionText()).append("。");
        } else if (spec.getType() == LandImportField.FieldType.NUMBER) {
            sb.append("只填数字，最多两位小数。");
        } else if (spec.getType() == LandImportField.FieldType.DATE) {
            sb.append("只填日期，如 2024-06-18。");
        } else {
            sb.append("文本。");
        }
        if (spec.getMaxLength() > 0) {
            sb.append("最长 ").append(spec.getMaxLength()).append(" 字，超出会被截断并提示。");
        }
        if ("crzdbh".equals(spec.getColumn())) {
            sb.append("★ 业务唯一键，宗地与配套项目靠它关联，必须与出让文件一致。");
        }
        if ("lxdh".equals(spec.getColumn())) {
            sb.append("格式：1 开头的 11 位手机号。");
        }
        if ("ghydxz".equals(spec.getColumn())) {
            sb.append("旧库有 60 多种写法，本列不做取值限制，按出让文件原样填写。");
        }
        return sb.toString();
    }

    private int columnWidth(LandImportField.FieldSpec spec) {
        switch (spec.getColumn()) {
            case "crzdbh":
                return 24 * 256;
            case "dkmc":
                return 28 * 256;
            case "tdzljhxdwjh":
                return 26 * 256;
            case "srr":
            case "lpmc":
            case "tdzldw":
                return 24 * 256;
            case "ghydxz":
                return 26 * 256;
            case "lxdh":
                return 16 * 256;
            case "beizhu":
                return 24 * 256;
            case "zlqsnrsm":
                return 26 * 256;
            default:
                break;
        }
        if (spec.getType() == LandImportField.FieldType.DATE) {
            return 16 * 256;
        }
        if (spec.getType() == LandImportField.FieldType.NUMBER) {
            return 18 * 256;
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
     * @param result 预览或入库的结果（读它的 errors）
     * @param source 原始上传文件解析出来的「行号 → 该行原始单元格文本」（保持用户填的内容）
     */
    public Workbook buildErrorReport(LandImportResultVO result, ErrorReportSource source) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle errorStyle = errorCellStyle(workbook);
        CellStyle textStyle = textStyle(workbook);

        List<LandImportField.FieldSpec> fields = LandImportField.templated();
        Sheet sheet = workbook.createSheet(SHEET_ERROR_REPORT);

        Row header = sheet.createRow(0);
        int columnIndex = 0;
        for (LandImportField.FieldSpec spec : fields) {
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
            for (LandImportResultVO.RowError error : result.getErrors()) {
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
        // 说明 sheet：一行话讲清楚怎么用这份回执
        Sheet note = workbook.createSheet("回执说明");
        note.setColumnWidth(0, 100 * 256);
        String[] notes = new String[]{
                "经营性用地批量导入 · 错误回执",
                "",
                "1. 前 " + fields.size() + " 列与导入模板完全一致，是你上传文件里的原始内容；"
                        + "末尾 3 列是系统给出的诊断信息（__excel行号 / __错误字段 / __错误原因）。",
                "2. 改完之后，可以删掉末尾 3 列，把这份文件直接当导入文件重新上传（表头与模板一致）。",
                "3. 若同一行有多个字段出错，会分行列出（同一 __excel行号 出现多次）。",
                "4. 错误原因以「预览校验」那一刻的库内数据为准；如果期间别人改了库，请重新预览。"
        };
        for (int i = 0; i < notes.length; i++) {
            note.createRow(i).createCell(0).setCellValue(notes[i]);
        }
        return workbook;
    }

    /**
     * @Description: 错误回执的数据源 —— 把「Excel 行号」映射回该行的原始单元格文本
     *
     * <p>做成接口是为了让 {@link #buildErrorReport} 不依赖解析器的内部结构：
     * 解析器解析时顺手把「行号 → 原始值」塞进一个实现类即可。
     */
    public interface ErrorReportSource {

        /** 该 Excel 行号对应的原始列值（顺序与 {@link LandImportField#templated()} 一致），无该行返回 null */
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
