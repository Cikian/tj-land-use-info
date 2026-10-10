package org.jeecg.modules.land.data.imports.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.support.DataSupport;
import org.jeecg.modules.land.data.imports.LandDuplicateStrategy;
import org.jeecg.modules.land.data.imports.LandImportField;
import org.jeecg.modules.land.data.imports.LandImportResultVO;
import org.jeecg.modules.land.data.imports.service.ILandImportService;
import org.jeecg.modules.land.data.imports.support.LandImportSupport;
import org.jeecg.modules.land.data.mapper.LandMapper;
import org.jeecg.modules.land.data.service.ILandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @Description: 经营性用地批量导入 - 解析 / 校验 / 入库实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>实现要点（每条都是踩点，不是想当然）：
 *
 * <p><b>① 表头认「英文字段名」，不认列位置、也不认中文说明行。</b>
 * 用户删列、调列顺序、改中文说明都不该导致导入失败。只有「必填列一个都没有」
 * 才判定为模板不匹配并直接报错。
 *
 * <p><b>② 兼容旧系统模板（两行表头）。</b>
 * 旧 {@code getExcelDemo()} 生成的是第 1 行英文字段名 + 第 2 行中文说明、数据从第 3 行开始；
 * 新模板沿用同一形状。解析器会识别「第 2 行是中文说明行」并跳过它，
 * 否则中文说明会被当成一条数据、报「crzdbh 不能为空」，
 * 用户会以为自己填错了。判据是「本行首个非空单元格里没有任何英文字段名」，
 * 而不是「第 2 行长得像中文」——后者会把一行全中文的真实数据误判成表头。
 *
 * <p><b>③ 预览与入库共用同一条解析路径</b>（{@code parse(file, apply, …)}），
 * 保证「预览看到什么，导入就写什么」。
 *
 * <p><b>④ 有错误默认整批不入库。</b>
 * 批量导入最怕「一半进了一半没进」——那会让系统里的数据与中心的 Excel 对不上，
 * 而且用户拿不到一份确定的回执。需要先救一部分时，显式勾「跳过错误行」。
 * 旧系统在手机号格式错时直接中断并留下半成品（只提示「已导入 N 条」），
 * 本实现把这个行为改成了「整表预校验 + 一行都不写」。
 *
 * <p><b>⑤ 写库全部参数化。</b>
 * 旧实现是 {@code INSERT INTO tbl SET k="v"} 字符串拼 SQL（明确注入风险，
 * 设计文档 5.3.3 要求修掉），这里一律走 MyBatis-Plus 实体写入。
 *
 * <p><b>⑥ 覆盖更新时「空单元格 = 不改动」。</b>
 * 选择 UPDATE 策略时，文件里没填的列保持库内原值 ——
 * 否则用户拿一份只填了面积的补丁表导进去，会把受让人、四至、录入人全部抹空。
 */
@Slf4j
@Service
public class LandImportServiceImpl implements ILandImportService {

    /** 手机号校验（沿用旧系统的正则：1 开头 + 3~9 + 9 位数字） */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3456789]\\d{9}$");

    /** 文本日期可接受的分隔写法 */
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "yyyyMMdd",
            "yyyy-M-d", "yyyy/M/d", "yyyy年MM月dd日", "yyyy年M月d日"};

    private static final DataFormatter FORMATTER = new DataFormatter();

    @Autowired
    private ILandService landService;

    @Autowired
    private LandMapper landMapper;

    @Autowired
    private LandImportSupport support;

    // ==================================================================
    // 一、模板
    // ==================================================================

    @Override
    public Workbook buildTemplate() {
        return support.buildTemplate();
    }

    // ==================================================================
    // 二、预览 / 入库
    // ==================================================================

    @Override
    public LandImportResultVO preview(MultipartFile file, LandDuplicateStrategy duplicateStrategy) {
        return parse(file, false, normalize(duplicateStrategy), false).getResult();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LandImportResultVO confirm(MultipartFile file, LandDuplicateStrategy duplicateStrategy,
                                     boolean skipErrorRows) {
        LandDuplicateStrategy strategy = normalize(duplicateStrategy);
        ParsedFile parsed = parse(file, true, strategy, skipErrorRows);
        LandImportResultVO result = parsed.getResult();
        log.info("经营性用地批量导入：解析 {} 行 / 有效 {} 行 / 新增 {} 条 / 覆盖 {} 条 / 跳过重复 {} 条 / "
                        + "错误 {} 条 / 策略 {} / 跳过错误行 {} / 操作人 {}",
                result.getTotalRows(), result.getValidRows(), result.getInsertedRows(), result.getUpdatedRows(),
                result.getSkippedDuplicates(), result.getErrorCount(), strategy.getCode(), skipErrorRows,
                LandImportSupport.nameOrPlaceholder(support.currentRealname()));
        return result;
    }

    @Override
    public ParsedFile parseOnly(MultipartFile file, LandDuplicateStrategy duplicateStrategy) {
        return parse(file, false, normalize(duplicateStrategy), false);
    }

    // ==================================================================
    // 三、错误回执
    // ==================================================================

    @Override
    public Workbook buildErrorReport(MultipartFile file, LandDuplicateStrategy duplicateStrategy) {
        ParsedFile parsed = parse(file, false, normalize(duplicateStrategy), false);
        return support.buildErrorReport(parsed.getResult(), parsed.getSource());
    }

    @Override
    public Workbook buildErrorReport(LandImportResultVO result, LandImportSupport.ErrorReportSource source) {
        return support.buildErrorReport(result, source);
    }

    // ==================================================================
    // 四、解析与校验（预览与入库共用）
    // ==================================================================

    private LandDuplicateStrategy normalize(LandDuplicateStrategy strategy) {
        return strategy == null ? LandDuplicateStrategy.REJECT : strategy;
    }

    /**
     * 解析 + 校验 +（可选）入库的唯一路径。
     *
     * @param apply          true = 真正写库；false = 只预览
     * @param strategy       重复宗地编号处理策略
     * @param skipErrorRows  true = 有错误的行跳过、只写合法行
     */
    private ParsedFile parse(MultipartFile file, boolean apply, LandDuplicateStrategy strategy,
                             boolean skipErrorRows) {
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("请选择要导入的 Excel 文件");
        }
        LandImportResultVO result = new LandImportResultVO();
        result.setDuplicateStrategy(strategy.getCode());
        result.setDuplicateStrategyLabel(strategy.getLabel());

        OriginalValues originalValues = new OriginalValues();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new JeecgBootException("Excel 里没有任何工作表");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new JeecgBootException("Excel 第 1 行必须是表头（英文字段名），"
                        + "请从「下载导入模板」开始填写");
            }

            Map<Integer, LandImportField.FieldSpec> columnMap = readHeader(headerRow);
            if (columnMap.isEmpty()) {
                throw new JeecgBootException("模板不匹配：第 1 行表头里没有任何本系统认识的列名。"
                        + "请点「下载导入模板」后按模板填写（表头是英文字段名，例如 crzdbh / xmfl）");
            }
            checkRequiredColumns(columnMap, result);
            warnMissingOptionalColumns(columnMap, result);

            int dataStartRow = detectDataStartRow(sheet, columnMap);

            // ---------- 逐行解析 ----------
            List<RowData> rows = new ArrayList<>();
            for (int r = dataStartRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isBlankRow(row)) {
                    continue;
                }
                int excelRowNum = r + 1;   // ★ 回执用 1 基行号，与用户在 Excel 左侧看到的一致
                result.setTotalRows(result.getTotalRows() + 1);
                RowData data = readRow(row, excelRowNum, columnMap, result);
                originalValues.put(excelRowNum, data.rawValues);
                rows.add(data);
            }

            if (result.getTotalRows() == 0) {
                throw new JeecgBootException("Excel 里没有解析到任何数据行。"
                        + "请确认数据填在第 " + (dataStartRow + 1) + " 行起（第 1、2 行是表头）");
            }

            // ---------- 必填与文件内重复 ----------
            List<RowData> candidates = checkRequiredAndFileDuplicates(rows, result);

            // ---------- 与库内比对 + 策略分流 ----------
            List<RowData> toInsert = new ArrayList<>();
            List<RowData> toUpdate = new ArrayList<>();
            classifyAgainstDatabase(candidates, strategy, result, toInsert, toUpdate);

            result.setValidRows(toInsert.size() + toUpdate.size());
            result.setWillInsertRows(toInsert.size());
            result.setWillUpdateRows(toUpdate.size());

            // ---------- 是否允许写入 ----------
            boolean hasError = !result.getErrors().isEmpty();
            if (hasError && !skipErrorRows) {
                result.setAborted(true);
                result.addNotice("存在 " + result.getErrorCount() + " 条错误："
                        + (apply ? "本次未写入任何数据。" : "预览不写库。")
                        + "修正后重传，或勾选「跳过错误行」只导入正确的行。");
                return new ParsedFile(result, originalValues);
            }
            if (hasError) {
                result.addNotice("已勾选「跳过错误行」：本次只导入校验通过的 "
                        + result.getValidRows() + " 行，错误清单仍会完整回执。");
            }

            if (apply) {
                write(toInsert, toUpdate, strategy, result);
            }
            if (result.getSkippedDuplicates() > 0) {
                result.addNotice("有 " + result.getSkippedDuplicates()
                        + " 行因出让宗地编号与库内重复被跳过（策略：跳过重复只导新增）");
            }
            if (strategy == LandDuplicateStrategy.UPDATE && !toUpdate.isEmpty()) {
                result.addNotice("覆盖更新时，文件中空着的单元格保持库内原值不变（不会被清空）");
            }
            return new ParsedFile(result, originalValues);
        } catch (JeecgBootException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.error("解析经营性用地导入 Excel 失败", e);
            throw new JeecgBootException("解析 Excel 失败：" + e.getMessage()
                    + "（请确认上传的是 .xlsx/.xls，并使用「下载导入模板」得到的表头）");
        }
    }

    // ------------------------------------------------------------------
    // 4.1 表头
    // ------------------------------------------------------------------

    /**
     * 读表头：<b>只认英文字段名</b>（列名与 {@link LandImportField} 的 column 一致）。
     *
     * <p>同时容忍用户在表头单元格前后加了空白、或写成「crzdbh（必填）」
     * 这种带后缀的形式 —— 用 {@code startsWith} 匹配，但只取第一个命中的列，
     * 避免「xzqh」把「xzqh2」也匹配上。
     */
    private Map<Integer, LandImportField.FieldSpec> readHeader(Row headerRow) {
        Map<Integer, LandImportField.FieldSpec> map = new LinkedHashMap<>();
        Set<String> hitColumns = new HashSet<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            String text = cellText(headerRow.getCell(c));
            if (text.isEmpty()) {
                continue;
            }
            LandImportField.FieldSpec spec = matchColumn(text, hitColumns);
            if (spec == null) {
                continue;
            }
            hitColumns.add(spec.getColumn());
            map.put(c, spec);
        }
        return map;
    }

    /** 表头文本 → 字段规格（精确匹配优先，其次前缀匹配） */
    private LandImportField.FieldSpec matchColumn(String headerText, Set<String> hitColumns) {
        String text = headerText.trim();
        // ① 精确匹配
        LandImportField.FieldSpec spec = LandImportField.of(text);
        if (spec != null && !hitColumns.contains(spec.getColumn())) {
            return spec;
        }
        // ② 前缀匹配：容忍「crzdbh（必填）」「电话 lxdh」这类前后缀。
        //    必须先长后短（xzqh2 比 xzqh 长），否则 xzqh 先命中就把 xzqh2 顶掉了。
        LandImportField.FieldSpec best = null;
        for (LandImportField.FieldSpec candidate : LandImportField.all()) {
            if (hitColumns.contains(candidate.getColumn())) {
                continue;
            }
            if (text.startsWith(candidate.getColumn())
                    && (best == null || candidate.getColumn().length() > best.getColumn().length())) {
                best = candidate;
            }
        }
        return best;
    }

    /** 必填列缺失 → 直接报错（这种文件必然整批失败，早点说清楚比逐行报「不能为空」有用） */
    private void checkRequiredColumns(Map<Integer, LandImportField.FieldSpec> columnMap,
                                      LandImportResultVO result) {
        Set<String> present = new HashSet<>();
        for (LandImportField.FieldSpec spec : columnMap.values()) {
            present.add(spec.getColumn());
        }
        List<String> missing = new ArrayList<>();
        for (LandImportField.FieldSpec spec : LandImportField.required()) {
            if (!present.contains(spec.getColumn())) {
                missing.add(spec.getColumn() + "（" + spec.getLabel() + "）");
            }
        }
        if (!missing.isEmpty()) {
            throw new JeecgBootException("模板不匹配：缺少必填列 " + String.join("、", missing)
                    + "。请点「下载导入模板」后按模板填写");
        }
    }

    /** 只提示一次「缺了哪些可选列」，避免每行都刷一遍 */
    private void warnMissingOptionalColumns(Map<Integer, LandImportField.FieldSpec> columnMap,
                                            LandImportResultVO result) {
        Set<String> present = new HashSet<>();
        for (LandImportField.FieldSpec spec : columnMap.values()) {
            present.add(spec.getColumn());
        }
        List<String> missing = new ArrayList<>();
        for (LandImportField.FieldSpec spec : LandImportField.templated()) {
            if (!spec.isRequired() && !present.contains(spec.getColumn())) {
                missing.add(spec.getLabel());
            }
        }
        if (!missing.isEmpty() && missing.size() <= 8) {
            result.addWarning("文件中没有这些列：" + String.join("、", missing)
                    + "（将按空值导入，不影响其它列）");
        }
    }

    /**
     * 判断数据起始行（0 基）。
     *
     * <p>标准模板：第 0 行英文字段名、第 1 行中文说明 → 数据从第 2 行（0 基）开始。
     * 若第 1 行不是「中文说明行」，则它本身就是数据，从第 1 行开始解析。
     */
    private int detectDataStartRow(Sheet sheet, Map<Integer, LandImportField.FieldSpec> columnMap) {
        Row second = sheet.getRow(1);
        if (second == null || isBlankRow(second)) {
            return 2;
        }
        return isSecondHeaderRow(second, columnMap) ? 2 : 1;
    }

    /**
     * 第 2 行是不是「中文说明行」。
     *
     * <p>★ 判据是「本行的单元格内容与<b>模板里写的中文说明</b>一致」，
     * 而不是「长得像中文」。这一条极其要紧：
     * 若按「有中文就是说明行」来判，那么一行正常业务数据
     * （地块名称「某某云著」、东至「规划路一」、录入人「张三」全是中文）
     * 会被当成表头<b>整行丢掉</b> —— 用户看到「成功导入 N 条」，
     * 少了的那条却查不出来，是批量导入里最难排查的一类事故。
     *
     * <p>因此只有「第 2 行里至少有一个单元格等于某个字段的中文名（允许带「（必填）」后缀）」
     * 才算说明行。真实数据里 {@code dkmc} 恰好写成「地块名称」、{@code dz} 恰好写成「东至」
     * 的概率极低，而且即便撞上，也只是这一行被当表头跳过，属于可接受的极小概率。
     */
    private boolean isSecondHeaderRow(Row row, Map<Integer, LandImportField.FieldSpec> columnMap) {
        for (Map.Entry<Integer, LandImportField.FieldSpec> entry : columnMap.entrySet()) {
            String text = cellText(row.getCell(entry.getKey()));
            if (text.isEmpty()) {
                continue;
            }
            String normalized = text.endsWith("（必填）")
                    ? text.substring(0, text.length() - "（必填）".length())
                    : text;
            if (normalized.equals(entry.getValue().getLabel())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 4.2 单行解析
    // ------------------------------------------------------------------

    /** 读一行并做类型级校验；返回的 {@link RowData} 一定非 null（含错误的行也要保留，回执要用） */
    private RowData readRow(Row row, int excelRowNum,
                            Map<Integer, LandImportField.FieldSpec> columnMap,
                            LandImportResultVO result) {
        RowData data = new RowData(excelRowNum);
        for (Map.Entry<Integer, LandImportField.FieldSpec> entry : columnMap.entrySet()) {
            LandImportField.FieldSpec spec = entry.getValue();
            Cell cell = row.getCell(entry.getKey());
            String raw = cellText(cell);
            data.rawValues.add(raw);
            if (raw.isEmpty()) {
                continue;
            }
            switch (spec.getType()) {
                case NUMBER:
                    readNumber(data, spec, cell, raw, result);
                    break;
                case DATE:
                    readDate(data, spec, cell, raw, result);
                    break;
                case YES_NO:
                    readYesNo(data, spec, raw, result);
                    break;
                case ENUM:
                    readEnum(data, spec, raw, result);
                    break;
                default:
                    readText(data, spec, raw, result);
                    break;
            }
        }
        data.crzdbh = data.text("crzdbh");
        return data;
    }

    private void readText(RowData data, LandImportField.FieldSpec spec, String raw,
                          LandImportResultVO result) {
        String value = LandImportSupport.trimToNull(raw);
        if (value == null) {
            return;
        }
        if (spec.getMaxLength() > 0 && value.length() > spec.getMaxLength()) {
            result.addWarning("第 " + data.rowNum + " 行：" + spec.getLabel() + " 超过 "
                    + spec.getMaxLength() + " 字，已截断（原长 " + value.length() + " 字）");
            value = LandImportSupport.truncate(value, spec.getMaxLength());
        }
        data.put(spec.getColumn(), value);
    }

    private void readNumber(RowData data, LandImportField.FieldSpec spec, Cell cell, String raw,
                            LandImportResultVO result) {
        BigDecimal value = null;
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            value = BigDecimal.valueOf(cell.getNumericCellValue());
        } else {
            String text = raw.replace(",", "").replace("，", "").replace(" ", "")
                    .replace("亿元", "").replace("万元", "").replace("平方米", "")
                    .replace("㎡", "").replace("米", "");
            try {
                value = new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                // 落到下面统一报错
            }
        }
        if (value == null) {
            data.addError(spec, spec.getLabel() + "「" + raw + "」不是有效数字，"
                    + "请只填数字（单位已写在列名里，不要带「亿元」「万元」「㎡」）");
            return;
        }
        if (value.scale() > 2) {
            value = value.setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        data.put(spec.getColumn(), value);
    }

    private void readDate(RowData data, LandImportField.FieldSpec spec, Cell cell, String raw,
                          LandImportResultVO result) {
        Date value = cellDate(cell, raw);
        if (value == null) {
            data.addError(spec, spec.getLabel() + "「" + raw + "」不是可识别的日期，"
                    + "请用 2024-06-18 或 2024/6/18 这种写法（或直接用 Excel 的日期格式）");
            return;
        }
        data.put(spec.getColumn(), value);
    }

    private void readYesNo(RowData data, LandImportField.FieldSpec spec, String raw,
                           LandImportResultVO result) {
        String text = raw.trim();
        String mapped = spec.getAliases().get(text);
        if (mapped == null) {
            mapped = spec.getAliases().get(text.toLowerCase());
        }
        if (mapped == null) {
            data.addError(spec, spec.getLabel() + "「" + raw + "」无法识别，"
                    + "请填「是」或「否」（也认 √ / × / 1 / 0）");
            return;
        }
        if (!mapped.equals(text)) {
            result.addWarning("第 " + data.rowNum + " 行：" + spec.getLabel() + "「" + raw
                    + "」已按「" + mapped + "」入库");
        }
        data.put(spec.getColumn(), mapped);
    }

    private void readEnum(RowData data, LandImportField.FieldSpec spec, String raw,
                          LandImportResultVO result) {
        String text = raw.trim();
        String mapped = spec.getAliases().get(text);
        if (mapped == null) {
            mapped = text;
        }
        if (!spec.getOptions().contains(mapped)) {
            data.addError(spec, spec.getLabel() + "「" + raw + "」不在允许的取值内，只能填："
                    + spec.optionText());
            return;
        }
        if (!mapped.equals(text)) {
            result.addWarning("第 " + data.rowNum + " 行：" + spec.getLabel() + "「" + raw
                    + "」已按标准写法「" + mapped + "」入库");
        }
        data.put(spec.getColumn(), mapped);
    }

    // ------------------------------------------------------------------
    // 4.3 必填与文件内重复
    // ------------------------------------------------------------------

    private List<RowData> checkRequiredAndFileDuplicates(List<RowData> rows, LandImportResultVO result) {
        List<RowData> candidates = new ArrayList<>(rows.size());
        // crzdbh → 首次出现的 Excel 行号（用于「文件内重复」的定位提示）
        Map<String, Integer> firstSeen = new LinkedHashMap<>();
        for (RowData data : rows) {
            // ---- 必填 ----
            // ★ 已经因为「取值非法」报过错的列不再报「不能为空」：
            //   两处都报会让用户看到「项目分类「国家级项目」不在允许的取值内」
            //   紧跟一条「项目分类不能为空」，看上去像两个问题，其实是同一个。
            boolean rowOk = data.fieldErrors.isEmpty();
            for (LandImportField.FieldSpec spec : LandImportField.required()) {
                if (data.hasFieldError(spec.getColumn())) {
                    rowOk = false;
                    continue;
                }
                Object value = data.values.get(spec.getColumn());
                if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                    data.addError(spec, spec.getLabel() + "不能为空（必填）");
                    rowOk = false;
                }
            }
            // ---- 联系电话格式（旧系统规则，沿用） ----
            String phone = data.text("lxdh");
            if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
                data.addError(LandImportField.of("lxdh"),
                        "联系电话「" + phone + "」格式不正确，应为 1 开头的 11 位手机号");
                rowOk = false;
            }
            // ---- 文件内重复 ----
            // ★ firstSeen 只登记「这一行本身没别的问题」的编号：
            //   否则一个本身就被拒的行会占住编号，让后面一行合法的同编号数据
            //   反被报成「重复」—— 用户改掉第一行的问题后，第二行还是导不进去。
            if (data.crzdbh != null) {
                Integer firstRow = firstSeen.get(data.crzdbh);
                if (firstRow == null) {
                    if (rowOk) {
                        firstSeen.put(data.crzdbh, data.rowNum);
                    }
                } else {
                    data.addError(LandImportField.of("crzdbh"),
                            "出让宗地编号「" + data.crzdbh + "」在本文件中重复（第 " + firstRow
                                    + " 行已出现过），同一编号只能出现一次");
                    rowOk = false;
                }
            }
            // 收集本行的错误（校验阶段产生的 + 类型解析阶段产生的）
            for (RowErrorDraft draft : data.fieldErrors) {
                result.addError(data.rowNum, data.crzdbh, draft.spec.getColumn(),
                        draft.spec.getLabel(), draft.message);
            }
            data.fieldErrors.clear();
            // ★ 只要本行有任何一处错误，这一行就整体不写：
            //   半行的数据落库比不落库更糟 —— 用户以为导进去了，
            //   实际「出让时间」那列是空的，而且没有任何地方提醒他。
            data.valid = rowOk;
            if (rowOk) {
                candidates.add(data);
            }
        }
        return candidates;
    }

    // ------------------------------------------------------------------
    // 4.4 与库内比对 + 策略分流
    // ------------------------------------------------------------------

    private void classifyAgainstDatabase(List<RowData> candidates, LandDuplicateStrategy strategy,
                                         LandImportResultVO result,
                                         List<RowData> toInsert, List<RowData> toUpdate) {
        // 一次性把命中的宗地查出来（避免逐行查库）
        Map<String, Land> existing = new LinkedHashMap<>();
        if (!candidates.isEmpty()) {
            List<String> crzdbhList = new ArrayList<>(candidates.size());
            for (RowData data : candidates) {
                crzdbhList.add(data.crzdbh);
            }
            existing = loadExisting(crzdbhList);
        }

        for (RowData data : candidates) {
            Land hit = existing.get(data.crzdbh);
            if (hit == null) {
                toInsert.add(data);
                continue;
            }
            switch (strategy) {
                case SKIP:
                    result.setSkippedDuplicates(result.getSkippedDuplicates() + 1);
                    data.skipped = true;
                    break;
                case UPDATE:
                    data.target = hit;
                    toUpdate.add(data);
                    break;
                case REJECT:
                default:
                    result.addError(data.rowNum, data.crzdbh, "crzdbh", "出让宗地编号",
                            "出让宗地编号「" + data.crzdbh + "」在系统中已存在（地块名称："
                                    + StringUtils.defaultString(hit.getDkmc(), "—")
                                    + "）。可改用「跳过重复只导新增」或「覆盖更新已有宗地」策略");
                    data.valid = false;
                    break;
            }
        }
    }

    /** 按出让宗地编号批量取库内<b>有效</b>记录（一次查询） */
    private Map<String, Land> loadExisting(List<String> crzdbhList) {
        Map<String, Land> map = new LinkedHashMap<>();
        if (crzdbhList == null || crzdbhList.isEmpty()) {
            return map;
        }
        QueryWrapper<Land> wrapper = new QueryWrapper<>();
        wrapper.in("crzdbh", crzdbhList);
        List<Land> list = landService.list(wrapper);
        if (list != null) {
            for (Land item : list) {
                map.put(item.getCrzdbh(), item);
            }
        }
        return map;
    }

    // ------------------------------------------------------------------
    // 4.5 写库
    // ------------------------------------------------------------------

    private void write(List<RowData> toInsert, List<RowData> toUpdate, LandDuplicateStrategy strategy,
                       LandImportResultVO result) {
        String username = support.currentUsername();
        Date now = new Date();

        for (RowData data : toInsert) {
            Land land = new Land();
            // 先清掉同编号的软删残留。
            //
            // ★ 实测结论（在 tj-jyxyd 上逐条验证过）：t_land 的唯一键是 (crzdbh, del_flag)，
            //   所以「有一条 del_flag=1 的旧行」时，插入 del_flag=0 的新行**本来就允许**
            //   （两者键值不同，不冲突）。换句话说这一步<b>并不是插入成功的前提</b>。
            //   保留它的理由有两个：
            //     ① 兜底：万一将来唯一键被人改成只按 crzdbh，这里不会突然插入失败；
            //     ② 卫生：反复「录入→删除→重录」同一编号不会在 t_land 里堆一串
            //        del_flag=1 的僵尸行（旧系统的表里就有不少这种残留）。
            //   注意 delete(Wrapper) 不会自动带上逻辑删除条件，这里就是真删。
            removeSoftDeleted(data.crzdbh);
            for (Map.Entry<String, Object> entry : data.values.entrySet()) {
                applyValue(land, entry.getKey(), entry.getValue());
            }
            land.setCrzdbh(data.crzdbh);
            land.setDelFlag(0);
            land.setSourceId(null);
            land.setCreateBy(username);
            land.setCreateTime(now);
            landMapper.insert(land);
            result.setInsertedRows(result.getInsertedRows() + 1);
        }

        for (RowData data : toUpdate) {
            Land land = data.target;
            for (Map.Entry<String, Object> entry : data.values.entrySet()) {
                // ★ 空单元格对应的列根本不在 values 里，因此不会被覆盖 —— 见类注释 ⑥
                applyValue(land, entry.getKey(), entry.getValue());
            }
            land.setUpdateBy(username);
            land.setUpdateTime(now);
            landMapper.updateById(land);
            result.setUpdatedRows(result.getUpdatedRows() + 1);
        }

        if (strategy == LandDuplicateStrategy.UPDATE && result.getUpdatedRows() == 0
                && result.getInsertedRows() == 0 && result.getErrorCount() == 0) {
            result.addNotice("没有需要写入的数据");
        }
    }

    /** 物理清掉同编号的软删残留（只在新增前调用） */
    private void removeSoftDeleted(String crzdbh) {
        try {
            int deleted = landMapper.delete(new QueryWrapper<Land>()
                    .eq("crzdbh", crzdbh)
                    .eq("del_flag", 1));
            if (deleted > 0) {
                log.info("导入前清理了 {} 条同编号的软删记录：crzdbh={}", deleted, crzdbh);
            }
        } catch (Exception e) {
            // 清理失败不阻断导入：真正的插入失败会在下面以明确异常暴露出来
            log.warn("清理软删记录失败：crzdbh={}，{}", crzdbh, e.getMessage());
        }
    }

    /**
     * 列名 → 实体字段（每个列名只在这里出现一次）。
     *
     * <p>刻意不用反射：反射写错字段名要到运行时才炸，而这里是编译期就能查出来的 switch。
     */
    private void applyValue(Land land, String column, Object value) {
        switch (column) {
            case "crzdbh":
                // 半角括号归一成中文括号，与单个录入（LandAdminServiceImpl）同一口径 ——
                // 否则同一宗地会因括号写法不同而变成两条记录
                land.setCrzdbh(DataSupport.normalizeBrackets((String) value));
                break;
            case "xmfl":
                land.setXmfl((String) value);
                break;
            case "dkmc":
                land.setDkmc(DataSupport.normalizeBrackets((String) value));
                break;
            case "xzqh":
                land.setXzqh((String) value);
                break;
            case "ghydxz":
                land.setGhydxz((String) value);
                break;
            case "crj":
                land.setCrj((BigDecimal) value);
                break;
            case "crsj":
                land.setCrsj((Date) value);
                break;
            case "kjsydmj":
                land.setKjsydmj((BigDecimal) value);
                break;
            case "zydmj":
                land.setZydmj((BigDecimal) value);
                break;
            case "jsmj":
                land.setJsmj((BigDecimal) value);
                break;
            case "nrcbdptf":
                land.setNrcbdptf((BigDecimal) value);
                break;
            case "wcd":
                land.setWcd((BigDecimal) value);
                break;
            case "srr":
                land.setSrr((String) value);
                break;
            case "htydjfsj":
                land.setHtydjfsj((Date) value);
                break;
            case "lpmc":
                land.setLpmc((String) value);
                break;
            case "lpjfsj":
                land.setLpjfsj((Date) value);
                break;
            case "dz":
                land.setDz((String) value);
                break;
            case "xz":
                land.setXz((String) value);
                break;
            case "nz":
                land.setNz((String) value);
                break;
            case "bz":
                land.setBz((String) value);
                break;
            case "tdzldw":
                land.setTdzldw((String) value);
                break;
            case "tdzljhxdwjh":
                land.setTdzljhxdwjh((String) value);
                break;
            case "tdzljh":
                land.setTdzljh((String) value);
                break;
            case "ptsfqq":
                land.setPtsfqq((String) value);
                break;
            case "ptqkh":
                land.setPtqkh((String) value);
                break;
            case "ptcbh":
                land.setPtcbh((String) value);
                break;
            case "crzdtxsj":
                land.setCrzdtxsj((String) value);
                break;
            case "ptjsnr":
                land.setPtjsnr((String) value);
                break;
            case "lrdw":
                land.setLrdw((String) value);
                break;
            case "lrr":
                land.setLrr((String) value);
                break;
            case "lxdh":
                land.setLxdh((String) value);
                break;
            case "zlqsnrsm":
                land.setZlqsnrsm((String) value);
                break;
            case "beizhu":
                land.setBeizhu((String) value);
                break;
            case "xzqh2":
                land.setXzqh2((String) value);
                break;
            default:
                // 未登记的列直接忽略（模板外的多余列不该让导入失败）
                break;
        }
    }

    // ==================================================================
    // 五、单元格读取
    // ==================================================================

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

    /** 单元格文本（数字不会变成 1.0；公式取缓存值；剔除 \n \r 等控制字符） */
    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        String text = FORMATTER.formatCellValue(cell);
        return text == null ? "" : LandImportSupport.stripControlChars(text).trim();
    }

    /** 单元格日期：支持 Excel 日期格式与常见文本写法 */
    private Date cellDate(Cell cell, String raw) {
        if (cell != null) {
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
        }
        return parseDateText(raw);
    }

    private Date parseDateText(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String trimmed = text.trim();
        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern);
                format.setLenient(false);
                return format.parse(trimmed);
            } catch (ParseException ignored) {
                // 试下一种写法
            }
        }
        return null;
    }

    // ==================================================================
    // 六、内部数据结构
    // ==================================================================

    /**
     * @Description: 一行数据的解析结果
     */
    private static class RowData {

        /** Excel 1 基行号 */
        final int rowNum;
        /** 该行在模板列顺序下的原始文本（回执用） */
        final List<String> rawValues = new ArrayList<>();
        /** 列名 → 已解析并校验通过的值 */
        final Map<String, Object> values = new LinkedHashMap<>();
        /** 类型级校验产生的错误（在必填校验之后统一并入回执，保证错误按行聚合） */
        final List<RowErrorDraft> fieldErrors = new ArrayList<>();
        /** 出让宗地编号（业务键） */
        String crzdbh;
        /** 是否通过校验 */
        boolean valid = true;
        /** 是否因重复被跳过（策略 = skip） */
        boolean skipped;
        /** 覆盖更新时命中的库内记录 */
        Land target;

        RowData(int rowNum) {
            this.rowNum = rowNum;
        }

        void put(String column, Object value) {
            values.put(column, value);
        }

        String text(String column) {
            Object value = values.get(column);
            return value == null ? null : String.valueOf(value);
        }

        void addError(LandImportField.FieldSpec spec, String message) {
            fieldErrors.add(new RowErrorDraft(spec, message));
        }

        /** 该列是否已经有错误（用于避免同一列既报「取值非法」又报「不能为空」） */
        boolean hasFieldError(String column) {
            for (RowErrorDraft draft : fieldErrors) {
                if (draft.spec.getColumn().equals(column)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 类型级错误的暂存（等本行所有列解析完再统一入回执） */
    private static class RowErrorDraft {
        final LandImportField.FieldSpec spec;
        final String message;

        RowErrorDraft(LandImportField.FieldSpec spec, String message) {
            this.spec = spec;
            this.message = message;
        }
    }

    /**
     * @Description: 原始行内容（Excel 行号 → 该行的原始列值），供错误回执回填
     */
    private static class OriginalValues implements LandImportSupport.ErrorReportSource {

        private final Map<Integer, List<String>> map = new HashMap<>();

        void put(int rowNum, List<String> values) {
            map.put(rowNum, values);
        }

        @Override
        public List<String> valuesOf(int rowNum) {
            return map.get(rowNum);
        }
    }
}