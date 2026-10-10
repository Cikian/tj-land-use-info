package org.jeecg.modules.land.data.imports.facility.service.impl;

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
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.support.DataSupport;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.imports.facility.FacilityDuplicateStrategy;
import org.jeecg.modules.land.data.imports.facility.FacilityImportField;
import org.jeecg.modules.land.data.imports.facility.FacilityImportResultVO;
import org.jeecg.modules.land.data.imports.facility.service.IFacilityImportService;
import org.jeecg.modules.land.data.imports.facility.support.FacilityImportSupport;
import org.jeecg.modules.land.data.mapper.FacilityMapper;
import org.jeecg.modules.land.data.mapper.LandMapper;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @Description: 配套信息批量导入 - 解析 / 校验（含宗地存在性）/ 入库实现
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
 * 旧 {@code getExcelDemo()} 生成的是第 1 行英文字段名 + 第 2 行中文说明、数据从第 3 行开始。
 * 解析器识别「第 2 行是中文说明行」并跳过它 ——
 * 判据是「本行某个单元格的内容<b>等于模板里写的中文名</b>」，
 * 不是「长得像中文」。后者会把一行正常业务数据（配套项目名称「某某道路工程」、
 * 录入人「张三」全是中文）当成表头整行丢掉，用户看到「成功导入 N 条」，
 * 少了的那条却查不出来 —— 这是批量导入里最难排查的一类事故。
 *
 * <p><b>③ 预览与入库共用同一条解析路径</b>（{@code parse(file, apply, …)}），
 * 保证「预览看到什么，导入就写什么」。
 *
 * <p><b>④ 有错误默认整批不入库。</b>
 * 批量导入最怕「一半进了一半没进」—— 那会让系统里的数据与中心的 Excel 对不上，
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
 * 否则用户拿一份只填了进度的补丁表导进去，会把六方单位、资金来源、录入人全部抹空。
 *
 * <p><b>★★ ⑦ 宗地存在性校验与孤儿清单（本模块相对宗地导入的核心新增）</b>：
 * <ul>
 *   <li><b>一次查询、不做 N+1</b>：把 {@code t_land} 的 {@code crzdbh} 全量取一次
 *       （实测 847 行，只取 4 个短列），在内存里建「精确命中集合」+「归一化索引」。
 *       逐行查库在几百行时会打出几百次查询，而全量取一列的成本远低于一次往返；</li>
 *   <li><b>查不到 = 孤儿</b>：{@code allowOrphan=false} 时作为硬错误拦截
 *       （错误文案里直接告诉用户「该怎么改」）；
 *       {@code allowOrphan=true} 时记入孤儿清单后照常导入；</li>
 *   <li><b>编号书写不一致给「近失配」提示</b>：归一化（去全角/半角括号、去末尾「号」、
 *       去空格、转大写）后能命中的，说明<b>大概率只是写法不同</b>，
 *       于是在提示里点名「疑似应为 xxx」。设计文档记录了两个真实例子：
 *       {@code 津南长（挂）G2024-01} 少了末尾的「号」、{@code 津西青楚(挂)} 用了半角括号。
 *       这两种问题的处理方式与「宗地真的没录」完全不同，必须能区分 ——
 *       提示而不是自动改写：自动「纠正」编号会静默把配套挂到别的宗地上，后果比报错严重。</li>
 * </ul>
 *
 * <p><b>★ 与宗地导入的一处结构性差异：不做「清理软删残留」</b>。
 * 宗地导入在新增前会物理删掉同编号的 {@code del_flag=1} 残留行（那里有唯一键，
 * 且残留行没有任何外部引用）。配套表<b>没有唯一键</b>（旧表本来就没有），
 * 插入新行不会与软删行冲突；而且配套项目被 {@code t_facility_process}、
 * {@code t_land_attachment}、档案按 {@code id} 引用，物理删掉软删行会立刻造出孤儿引用。
 * 因此这里只按 {@code delFlag='0'} 参与「是否重复」的判断，绝不动历史软删行。
 *
 * <p><b>★ 配套表的软删列是驼峰 {@code delFlag}、类型 varchar('0'/'1')</b>
 * （与宗地的下划线 {@code del_flag} tinyint 完全不同，见 {@code Facility} 实体注释），
 * 实体上没有 {@code @TableLogic}，所以本类所有手写的条件都<b>显式带 {@code delFlag='0'}，
 * 且一律写字符串 {@code "0"} 而不是数字 0</b> —— 写成数字在 MySQL 里虽然能隐式转换，
 * 但一旦将来列里出现 {@code '00'} 之类的脏值，字符串比较才是可预期的。
 */
@Slf4j
@Service
public class FacilityImportServiceImpl implements IFacilityImportService {

    /** 手机号校验（沿用旧系统的正则：1 开头 + 3~9 + 9 位数字） */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3456789]\\d{9}$");

    /** 文本日期可接受的分隔写法 */
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "yyyyMMdd",
            "yyyy-M-d", "yyyy/M/d", "yyyy年MM月dd日", "yyyy年M月d日"};

    private static final DataFormatter FORMATTER = new DataFormatter();

    /**
     * 「宗地编号 + 配套项目名称」拼成判重键时用的分隔符。
     *
     * <p>★ 用 {@code \u0001}（ASCII 控制字符）而不是 {@code "-"} 或 {@code "_"}：
     * 后者本身就是编号里会出现的字符，用它们拼键会出现
     * 「(A-B, C) 与 (A, B-C) 拼出同一个键」的撞车 —— 结果是两条不同的配套被判成重复，
     * 用户改名称也导不进去。控制字符在清洗阶段已被剔除，不会出现在真实值里。
     */
    private static final char KEY_SEPARATOR = '\u0001';

    @Autowired
    private LandMapper landMapper;

    @Autowired
    private FacilityMapper facilityMapper;

    @Autowired
    private FacilityImportSupport support;

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
    public FacilityImportResultVO preview(MultipartFile file, FacilityDuplicateStrategy duplicateStrategy,
                                          boolean allowOrphan) {
        return parse(file, false, normalize(duplicateStrategy), false, allowOrphan).getResult();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FacilityImportResultVO confirm(MultipartFile file, FacilityDuplicateStrategy duplicateStrategy,
                                          boolean skipErrorRows, boolean allowOrphan) {
        FacilityDuplicateStrategy strategy = normalize(duplicateStrategy);
        ParsedFile parsed = parse(file, true, strategy, skipErrorRows, allowOrphan);
        FacilityImportResultVO result = parsed.getResult();
        log.info("配套信息批量导入：解析 {} 行 / 有效 {} 行 / 新增 {} 条 / 覆盖 {} 条 / 跳过重复 {} 条 / "
                        + "错误 {} 条 / 孤儿 {} 条（匹配到宗地 {} 行）/ 策略 {} / 跳过错误行 {} / "
                        + "允许挂未登记宗地 {} / 操作人 {}",
                result.getTotalRows(), result.getValidRows(), result.getInsertedRows(), result.getUpdatedRows(),
                result.getSkippedDuplicates(), result.getErrorCount(), result.getOrphanRows(),
                result.getMatchedLandRows(), strategy.getCode(), skipErrorRows, allowOrphan,
                FacilityImportSupport.nameOrPlaceholder(support.currentRealname()));
        return result;
    }

    @Override
    public ParsedFile parseOnly(MultipartFile file, FacilityDuplicateStrategy duplicateStrategy,
                                boolean allowOrphan) {
        return parse(file, false, normalize(duplicateStrategy), false, allowOrphan);
    }

    // ==================================================================
    // 三、错误回执
    // ==================================================================

    @Override
    public Workbook buildErrorReport(MultipartFile file, FacilityDuplicateStrategy duplicateStrategy,
                                     boolean allowOrphan) {
        ParsedFile parsed = parse(file, false, normalize(duplicateStrategy), false, allowOrphan);
        return support.buildErrorReport(parsed.getResult(), parsed.getSource());
    }

    @Override
    public Workbook buildErrorReport(FacilityImportResultVO result, FacilityImportSupport.ErrorReportSource source) {
        return support.buildErrorReport(result, source);
    }

    // ==================================================================
    // 四、解析与校验（预览与入库共用）
    // ==================================================================

    private FacilityDuplicateStrategy normalize(FacilityDuplicateStrategy strategy) {
        return strategy == null ? FacilityDuplicateStrategy.REJECT : strategy;
    }

    /**
     * 解析 + 校验 +（可选）入库的唯一路径。
     *
     * @param apply          true = 真正写库；false = 只预览
     * @param strategy       重复 (宗地编号, 配套项目名称) 的处理策略
     * @param skipErrorRows  true = 有错误的行跳过、只写合法行
     * @param allowOrphan    true = 宗地查不到的行记入孤儿清单后照常导入
     */
    private ParsedFile parse(MultipartFile file, boolean apply, FacilityDuplicateStrategy strategy,
                             boolean skipErrorRows, boolean allowOrphan) {
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("请选择要导入的 Excel 文件");
        }
        FacilityImportResultVO result = new FacilityImportResultVO();
        result.setDuplicateStrategy(strategy.getCode());
        result.setDuplicateStrategyLabel(strategy.getLabel());
        result.setAllowOrphan(allowOrphan);

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

            Map<Integer, FacilityImportField.FieldSpec> columnMap = readHeader(headerRow);
            if (columnMap.isEmpty()) {
                throw new JeecgBootException("模板不匹配：第 1 行表头里没有任何本系统认识的列名。"
                        + "请点「下载导入模板」后按模板填写（表头是英文字段名，例如 crzdbh / ptxmmc）");
            }
            checkRequiredColumns(columnMap);
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

            // ---------- 必填与文件内重复（判重键 = 宗地编号 + 配套项目名称）----------
            List<RowData> candidates = checkRequiredAndFileDuplicates(rows, result);

            // ---------- 宗地存在性：一次查询 + 孤儿清单 ----------
            List<RowData> importable = candidates;
            if (!candidates.isEmpty()) {
                LandIndex landIndex = loadLandIndex();
                importable = checkLandExistence(candidates, landIndex, allowOrphan, result);
            }

            // ---------- 与库内比对 + 策略分流 ----------
            List<RowData> toInsert = new ArrayList<>();
            List<RowData> toUpdate = new ArrayList<>();
            if (!importable.isEmpty()) {
                classifyAgainstDatabase(importable, strategy, result, toInsert, toUpdate);
            }

            result.setValidRows(toInsert.size() + toUpdate.size());
            result.setWillInsertRows(toInsert.size());
            result.setWillUpdateRows(toUpdate.size());
            summarizeOrphans(result, allowOrphan);

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
                write(toInsert, toUpdate, result);
            }
            if (result.getSkippedDuplicates() > 0) {
                result.addNotice("有 " + result.getSkippedDuplicates()
                        + " 行因「出让宗地编号 + 配套项目名称」与库内重复被跳过（策略：跳过重复只导新增）");
            }
            if (strategy == FacilityDuplicateStrategy.UPDATE && !toUpdate.isEmpty()) {
                result.addNotice("覆盖更新时，文件中空着的单元格保持库内原值不变（不会被清空）");
            }
            return new ParsedFile(result, originalValues);
        } catch (JeecgBootException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.error("解析配套信息导入 Excel 失败", e);
            throw new JeecgBootException("解析 Excel 失败：" + e.getMessage()
                    + "（请确认上传的是 .xlsx/.xls，并使用「下载导入模板」得到的表头）");
        }
    }

    // ------------------------------------------------------------------
    // 4.1 表头
    // ------------------------------------------------------------------

    /**
     * 读表头：<b>只认英文字段名</b>（列名与 {@link FacilityImportField} 的 column 一致）。
     *
     * <p>同时容忍用户在表头单元格前后加了空白、或写成「crzdbh（必填）」这种带后缀的形式 ——
     * 用 {@code startsWith} 匹配，但必须<b>先长后短</b>，
     * 否则「cd」（长度）会先命中「cdxxx」这类更长的列名，把数据读错列。
     */
    private Map<Integer, FacilityImportField.FieldSpec> readHeader(Row headerRow) {
        Map<Integer, FacilityImportField.FieldSpec> map = new LinkedHashMap<>();
        Set<String> hitColumns = new HashSet<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            String text = cellText(headerRow.getCell(c));
            if (text.isEmpty()) {
                continue;
            }
            FacilityImportField.FieldSpec spec = matchColumn(text, hitColumns);
            if (spec == null) {
                continue;
            }
            hitColumns.add(spec.getColumn());
            map.put(c, spec);
        }
        return map;
    }

    /** 表头文本 → 字段规格（精确匹配优先，其次前缀匹配，前缀匹配取最长的那个） */
    private FacilityImportField.FieldSpec matchColumn(String headerText, Set<String> hitColumns) {
        String text = headerText.trim();
        // ① 精确匹配
        FacilityImportField.FieldSpec spec = FacilityImportField.of(text);
        if (spec != null && !hitColumns.contains(spec.getColumn())) {
            return spec;
        }
        // ② 前缀匹配：容忍「crzdbh（必填）」「电话 lxdh」这类前后缀
        FacilityImportField.FieldSpec best = null;
        for (FacilityImportField.FieldSpec candidate : FacilityImportField.all()) {
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
    private void checkRequiredColumns(Map<Integer, FacilityImportField.FieldSpec> columnMap) {
        Set<String> present = new HashSet<>();
        for (FacilityImportField.FieldSpec spec : columnMap.values()) {
            present.add(spec.getColumn());
        }
        List<String> missing = new ArrayList<>();
        for (FacilityImportField.FieldSpec spec : FacilityImportField.required()) {
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
    private void warnMissingOptionalColumns(Map<Integer, FacilityImportField.FieldSpec> columnMap,
                                            FacilityImportResultVO result) {
        Set<String> present = new HashSet<>();
        for (FacilityImportField.FieldSpec spec : columnMap.values()) {
            present.add(spec.getColumn());
        }
        List<String> missing = new ArrayList<>();
        for (FacilityImportField.FieldSpec spec : FacilityImportField.templated()) {
            if (!spec.isRequired() && !present.contains(spec.getColumn())) {
                missing.add(spec.getLabel());
            }
        }
        // 配套表有 56 列，用户只填 10 列是常态，这里限 8 条以内才提示，避免提示本身变成噪音
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
    private int detectDataStartRow(Sheet sheet, Map<Integer, FacilityImportField.FieldSpec> columnMap) {
        Row second = sheet.getRow(1);
        if (second == null || isBlankRow(second)) {
            return 2;
        }
        return isSecondHeaderRow(second, columnMap) ? 2 : 1;
    }

    /**
     * 第 2 行是不是「中文说明行」。
     *
     * <p>★ 判据是「本行某个单元格的内容等于<b>模板里写的中文名</b>（允许带「（必填）」后缀）」，
     * 而不是「长得像中文」。这一条极其要紧：若按「有中文就是说明行」来判，
     * 一行正常业务数据（配套项目名称「侯台片区规划路一道路工程」、录入人「张三」全是中文）
     * 会被当成表头<b>整行丢掉</b>，而用户看到的是「成功导入 N 条」。
     *
     * <p>真实数据里 {@code dkmc} 恰好写成「地块名称」、{@code lxdh} 恰好写成「联系电话」
     * 的概率极低；即便撞上，也只是这一行被当表头跳过，属于可接受的极小概率。
     */
    private boolean isSecondHeaderRow(Row row, Map<Integer, FacilityImportField.FieldSpec> columnMap) {
        for (Map.Entry<Integer, FacilityImportField.FieldSpec> entry : columnMap.entrySet()) {
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
                            Map<Integer, FacilityImportField.FieldSpec> columnMap,
                            FacilityImportResultVO result) {
        RowData data = new RowData(excelRowNum);
        for (Map.Entry<Integer, FacilityImportField.FieldSpec> entry : columnMap.entrySet()) {
            FacilityImportField.FieldSpec spec = entry.getValue();
            Cell cell = row.getCell(entry.getKey());
            String raw = cellText(cell);
            data.rawValues.add(raw);
            if (raw.isEmpty()) {
                continue;
            }
            switch (spec.getType()) {
                case NUMBER:
                    readNumber(data, spec, cell, raw);
                    break;
                case DATE:
                    readDate(data, spec, cell, raw);
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
        data.crzdbh = data.text(FacilityImportField.COL_CRZDBH);
        data.ptxmmc = data.text(FacilityImportField.COL_PTXMMC);
        return data;
    }

    private void readText(RowData data, FacilityImportField.FieldSpec spec, String raw,
                          FacilityImportResultVO result) {
        String value = FacilityImportSupport.trimToNull(raw);
        if (value == null) {
            return;
        }
        if (spec.getMaxLength() > 0 && value.length() > spec.getMaxLength()) {
            result.addWarning("第 " + data.rowNum + " 行：" + spec.getLabel() + " 超过 "
                    + spec.getMaxLength() + " 字，已截断（原长 " + value.length() + " 字）");
            value = FacilityImportSupport.truncate(value, spec.getMaxLength());
        }
        data.put(spec.getColumn(), value);
    }

    /**
     * 数值列。
     *
     * <p>★ 与宗地导入同一取舍：用户很自然会把「30米」「3250万元」整句填进来，
     * 这里把常见单位与千分位去掉后仍然解析失败才报错 ——
     * 因为「带单位」是填表习惯问题，「不是数字」才是数据问题，
     * 前者不该让整批导入失败（列名里已经写明单位，扣掉单位后语义是明确的）。
     */
    private void readNumber(RowData data, FacilityImportField.FieldSpec spec, Cell cell, String raw) {
        BigDecimal value = null;
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            value = BigDecimal.valueOf(cell.getNumericCellValue());
        } else {
            String text = raw.replace(",", "").replace("，", "").replace(" ", "")
                    .replace("万元", "").replace("米", "")
                    .replace("平方米", "").replace("㎡", "");
            try {
                value = new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                // 落到下面统一报错
            }
        }
        if (value == null) {
            data.addError(spec, spec.getLabel() + "「" + raw + "」不是有效数字，"
                    + "请只填数字（单位已写在列名里，不要带「米」「万元」「㎡」）");
            return;
        }
        if (value.scale() > 2) {
            value = value.setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        data.put(spec.getColumn(), value);
    }

    private void readDate(RowData data, FacilityImportField.FieldSpec spec, Cell cell, String raw) {
        Date value = cellDate(cell, raw);
        if (value == null) {
            data.addError(spec, spec.getLabel() + "「" + raw + "」不是可识别的日期，"
                    + "请用 2025-03-01 或 2025/3/1 这种写法（或直接用 Excel 的日期格式）");
            return;
        }
        data.put(spec.getColumn(), value);
    }

    private void readYesNo(RowData data, FacilityImportField.FieldSpec spec, String raw,
                           FacilityImportResultVO result) {
        String text = raw.trim();
        String mapped = spec.getAliases().get(text);
        if (mapped == null) {
            mapped = spec.getAliases().get(text.toLowerCase(Locale.ROOT));
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

    private void readEnum(RowData data, FacilityImportField.FieldSpec spec, String raw,
                          FacilityImportResultVO result) {
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

    /**
     * 必填校验 + 文件内重复校验，返回「本行没有任何问题」的行。
     *
     * <p>判重键是 <b>{@code (crzdbh, ptxmmc)}</b>：一块地上可以有任意多个配套项目，
     * 但同名的只能有一个（业务上「同一宗地下的同名配套」一定是重复录入）。
     * 旧系统 {@code importData()} 用的就是这个条件。
     */
    private List<RowData> checkRequiredAndFileDuplicates(List<RowData> rows, FacilityImportResultVO result) {
        List<RowData> candidates = new ArrayList<>(rows.size());
        // 判重键 → 首次出现的 Excel 行号（用于「文件内重复」的定位提示）
        Map<String, Integer> firstSeen = new LinkedHashMap<>();
        for (RowData data : rows) {
            // ---- 必填 ----
            // ★ 已经因为「取值非法」报过错的列不再报「不能为空」：
            //   两处都报会让用户看到「配套设施类别「供气管道」不在允许的取值内」
            //   紧跟一条「配套设施类别不能为空」，看上去像两个问题，其实是同一个。
            boolean rowOk = data.fieldErrors.isEmpty();
            for (FacilityImportField.FieldSpec spec : FacilityImportField.required()) {
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
                data.addError(FacilityImportField.of("lxdh"),
                        "联系电话「" + phone + "」格式不正确，应为 1 开头的 11 位手机号");
                rowOk = false;
            }
            // ---- 文件内重复 ----
            // ★ firstSeen 只登记「这一行本身没别的问题」的判重键：
            //   否则一个本身就被拒的行会占住键值，让后面一行合法的同键数据
            //   反被报成「重复」—— 用户改掉第一行的问题后，第二行还是导不进去。
            if (data.crzdbh != null && data.ptxmmc != null) {
                String key = duplicateKey(data.crzdbh, data.ptxmmc);
                Integer firstRow = firstSeen.get(key);
                if (firstRow == null) {
                    if (rowOk) {
                        firstSeen.put(key, data.rowNum);
                    }
                } else {
                    data.addError(FacilityImportField.of(FacilityImportField.COL_PTXMMC),
                            "本文件中第 " + firstRow + " 行已出现同样的「" + data.crzdbh + " + "
                                    + data.ptxmmc + "」，同一宗地下的配套项目名称不能重复");
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
            //   实际「投资估算」那列是空的，而且没有任何地方提醒他。
            data.valid = rowOk;
            if (rowOk) {
                candidates.add(data);
            }
        }
        return candidates;
    }

    /** 判重键：出让宗地编号 + 配套项目名称（分隔符见 {@link #KEY_SEPARATOR}） */
    private static String duplicateKey(String crzdbh, String ptxmmc) {
        return crzdbh + KEY_SEPARATOR + ptxmmc;
    }

    // ------------------------------------------------------------------
    // 4.4 宗地存在性校验（孤儿清单）
    // ------------------------------------------------------------------

    /**
     * 把 {@code t_land} 的编号全量取一次，建「精确命中集合」+「归一化索引」。
     *
     * <p><b>★ 为什么一次全量取而不是按文件里的编号 {@code IN (...)} 查</b>：
     * <ul>
     *   <li>「近失配提示」需要知道<b>库内都有哪些编号</b>。只按 {@code IN} 查命中的，
     *       就永远发现不了「用户写的是 {@code 津南长（挂）G2024-01}、库里其实是
     *       {@code 津南长（挂）G2024-01号}」—— 而这正是设计文档记录的典型问题；</li>
     *   <li>成本可接受：实测 {@code t_land} 847 行，且这里只取 4 个短列
     *       （编号 / 地块名称 / 行政区划 / 项目分类，后三个用于新增时补默认值），
     *       一次查询远优于逐行查库（N+1）或为近失配再打一轮 {@code LIKE} 往返。</li>
     * </ul>
     */
    private LandIndex loadLandIndex() {
        LandIndex index = new LandIndex();
        // 只取需要用的列；Land 上的 @TableLogic 会自动附上 del_flag = 0（已删宗地不算存在）
        QueryWrapper<Land> wrapper = new QueryWrapper<>();
        wrapper.select("crzdbh", "dkmc", "xzqh", "xmfl");
        List<Land> lands = landMapper.selectList(wrapper);
        if (lands == null) {
            return index;
        }
        for (Land land : lands) {
            String code = land.getCrzdbh();
            if (StringUtils.isBlank(code)) {
                continue;
            }
            String trimmed = code.trim();
            index.byCode.put(trimmed, land);
            String normalized = normalizeCrzdbh(trimmed);
            // 归一化后同键的只留第一个：两个不同编号归一化后相同（说明库里本身就有两种写法），
            // 这时点名任何一个都可能点错，随机挑一个不如稳定地挑第一个。
            if (normalized != null && !index.byNormalized.containsKey(normalized)) {
                index.byNormalized.put(normalized, trimmed);
            }
        }
        return index;
    }

    /**
     * 宗地存在性校验。
     *
     * <p>三种结果（见类注释 ⑦）：
     * <ol>
     *   <li><b>精确命中</b> → {@code matchedLandRows} +1，并把命中宗地挂到行上
     *       （新增时用它补 {@code dkmc / xzqh / xmfl} 的默认值）；</li>
     *   <li><b>未命中 + allowOrphan=false</b> → 记入错误（文案里给出「允许挂到未登记宗地」这个出口），
     *       本行不再参与后续「是否与库内重复」的判断（一个挂不上宗地的行，
     *       它的重复与否对用户没有意义，两条都报只会让人以为有两个问题）；</li>
     *   <li><b>未命中 + allowOrphan=true</b> → 记入 {@link FacilityImportResultVO#getOrphans()}
     *       并<b>照常返回该行</b>，让它继续走重复判定与入库。</li>
     * </ol>
     *
     * <p>★ 无论是否允许导入，孤儿都会被登记进清单（{@code orphanRows} 照常计数）——
     * 「不允许导入」时用户更需要这份清单去催录宗地。
     *
     * @return 仍然可导入的行（{@code allowOrphan=false} 时已剔除孤儿行）
     */
    private List<RowData> checkLandExistence(List<RowData> candidates, LandIndex landIndex,
                                             boolean allowOrphan, FacilityImportResultVO result) {
        List<RowData> importable = new ArrayList<>(candidates.size());
        for (RowData data : candidates) {
            Land land = landIndex.byCode.get(data.crzdbh);
            if (land != null) {
                result.setMatchedLandRows(result.getMatchedLandRows() + 1);
                data.land = land;
                importable.add(data);
                continue;
            }
            // 未命中：先看看是不是「只是写法不同」
            String likely = landIndex.byNormalized.get(normalizeCrzdbh(data.crzdbh));
            String reason = buildOrphanReason(data.crzdbh, data.ptxmmc, likely);
            result.addOrphan(data.rowNum, data.crzdbh, data.ptxmmc, reason);
            if (likely != null) {
                // 近失配是「可操作」的提示（数量少、每条都要用户去核对写法），单独给一条警告
                result.addWarning("第 " + data.rowNum + " 行：出让宗地编号「" + data.crzdbh
                        + "」在系统中不存在，但与库内「" + likely
                        + "」仅相差全角/半角括号、末尾的「号」字或大小写，疑似应为「" + likely + "」");
            }
            if (allowOrphan) {
                importable.add(data);
            } else {
                // ★ 这里直接入回执，而不是走 data.fieldErrors 再统一 drain：
                //   孤儿判定发生在「本行的类型/必填错误已经全部入回执」之后，
                //   再绕一次暂存队列只会让错误顺序变得难以跟踪（回执是按行分组展示的，
                //   同一行的多条错误谁先谁后由用户从上往下读，顺序稳定更重要）。
                result.addError(data.rowNum, data.crzdbh,
                        FacilityImportField.COL_CRZDBH, "出让宗地编号",
                        "出让宗地编号「" + data.crzdbh + "」在系统中不存在；若确认要导入，"
                                + "请勾选「允许挂到未登记宗地（记入孤儿清单）」"
                                + (likely == null ? "" : "。库内有「" + likely + "」，请核对写法是否写错"));
                data.valid = false;
            }
        }
        return importable;
    }

    /** 孤儿说明文案（供孤儿清单与回执的「说明」列使用） */
    private String buildOrphanReason(String crzdbh, String ptxmmc, String likely) {
        StringBuilder sb = new StringBuilder();
        sb.append("出让宗地编号「").append(crzdbh).append("」在系统中不存在");
        if (likely != null) {
            sb.append("，但与库内「").append(likely).append("」仅相差全角/半角括号、末尾的「号」字或大小写");
        }
        sb.append("。配套项目「").append(StringUtils.defaultString(ptxmmc, "（未填名称）"))
                .append("」在系统里将没有归属宗地，请先补录宗地或核对编号写法");
        return sb.toString();
    }

    /** 孤儿汇总提示：逐行提示会刷屏，用户在页面上要看的是「一共几条、影响是什么」 */
    private void summarizeOrphans(FacilityImportResultVO result, boolean allowOrphan) {
        if (result.getOrphanRows() <= 0) {
            return;
        }
        if (allowOrphan) {
            result.addWarning("有 " + result.getOrphanRows()
                    + " 行的出让宗地编号在系统中找不到，已按「允许挂到未登记宗地」导入，"
                    + "明细见孤儿清单（下载的错误回执里另有一个「孤儿清单」sheet）；"
                    + "请把这批配套对应的宗地先补录进系统，否则档案与收发文按宗地关联时找不到它们");
        } else {
            result.addWarning("有 " + result.getOrphanRows()
                    + " 行的出让宗地编号在系统中找不到（孤儿清单见返回结果）："
                    + "这些行未导入。若确认要导入，请勾选「允许挂到未登记宗地（记入孤儿清单）」");
        }
    }

    /**
     * 宗地编号归一化：去全角/半角括号、去末尾「号」、去空格、转大写。
     *
     * <p>这四类差异是设计文档实测的真实问题：
     * <ul>
     *   <li>{@code 津南长（挂）G2024-01} —— 少了末尾的「号」（库里是 {@code …G2024-01号}）；</li>
     *   <li>{@code 津西青楚(挂)} —— 用了半角括号（库里可能是全角「（挂）」）；</li>
     *   <li>大小写与空格 —— 从 Word/网页复制编号时最常见的附带差异。</li>
     * </ul>
     *
     * <p>★ 归一化<b>只用于「提示」</b>，绝不用于「自动改写编号」。
     * 自动改写等于替用户猜「这两个编号是同一个宗地」，猜错就会把配套静默挂到
     * 另一块地上去 —— 而用户从回执上完全看不出来。宁可让他自己核对一遍。
     *
     * <p>括号类字符<b>全部删除</b>而不是替换成占位符：括号本身不承载语义
     * （「（挂）」只是一个标记），删掉后 {@code 津西青楚(挂)} 与 {@code 津西青楚（挂）}
     * 归一化结果相同，正好是我们要的判定。
     */
    static String normalizeCrzdbh(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String text = value.toUpperCase(Locale.ROOT);
        text = text.replace("（", "").replace("）", "")
                .replace("(", "").replace(")", "");
        text = text.replace(" ", "").replace("\u3000", "");
        while (text.endsWith("号")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    // ------------------------------------------------------------------
    // 4.5 与库内比对 + 策略分流
    // ------------------------------------------------------------------

    private void classifyAgainstDatabase(List<RowData> candidates, FacilityDuplicateStrategy strategy,
                                         FacilityImportResultVO result,
                                         List<RowData> toInsert, List<RowData> toUpdate) {
        // 一次性把命中的配套查出来（避免逐行查库）
        Map<String, Facility> existing = new LinkedHashMap<>();
        if (!candidates.isEmpty()) {
            List<String> crzdbhList = new ArrayList<>(candidates.size());
            for (RowData data : candidates) {
                crzdbhList.add(data.crzdbh);
            }
            existing = loadExistingFacilities(crzdbhList);
        }

        for (RowData data : candidates) {
            Facility hit = existing.get(duplicateKey(data.crzdbh, data.ptxmmc));
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
                    result.addError(data.rowNum, data.crzdbh, FacilityImportField.COL_PTXMMC, "配套项目名称",
                            "出让宗地编号「" + data.crzdbh + "」下的配套项目「" + data.ptxmmc
                                    + "」在系统中已存在。可改用「跳过重复只导新增」或"
                                    + "「覆盖更新已有配套（空单元格不改动）」策略");
                    data.valid = false;
                    break;
            }
        }
    }

    /**
     * 按文件里的宗地编号批量取库内<b>有效</b>配套（一次查询）。
     *
     * <p>★ 条件里的 {@code delFlag} 必须显式写成字符串 {@code '0'}：
     * 本表软删列是 varchar（见类注释），且实体上没有 {@code @TableLogic}，
     * 不写这个条件会把已移除的配套也当成「已存在」，让用户反复覆盖一条看不见的记录。
     */
    private Map<String, Facility> loadExistingFacilities(List<String> crzdbhList) {
        Map<String, Facility> map = new LinkedHashMap<>();
        if (crzdbhList == null || crzdbhList.isEmpty()) {
            return map;
        }
        QueryWrapper<Facility> wrapper = new QueryWrapper<>();
        wrapper.eq("delFlag", "0").in("crzdbh", crzdbhList);
        List<Facility> list = facilityMapper.selectList(wrapper);
        if (list != null) {
            for (Facility item : list) {
                if (item.getCrzdbh() == null || item.getPtxmmc() == null) {
                    continue;
                }
                map.put(duplicateKey(item.getCrzdbh(), item.getPtxmmc()), item);
            }
        }
        return map;
    }

    // ------------------------------------------------------------------
    // 4.6 写库
    // ------------------------------------------------------------------

    private void write(List<RowData> toInsert, List<RowData> toUpdate, FacilityImportResultVO result) {
        String username = support.currentUsername();
        Date now = new Date();

        for (RowData data : toInsert) {
            Facility facility = new Facility();
            for (Map.Entry<String, Object> entry : data.values.entrySet()) {
                applyValue(facility, entry.getKey(), entry.getValue());
            }
            facility.setCrzdbh(data.crzdbh);
            facility.setPtxmmc(data.ptxmmc);
            fillFromLand(facility, data.land);
            facility.setDelFlag("0");
            facility.setCreateAccount(username);
            facility.setCreateTime(now);
            facilityMapper.insert(facility);
            result.setInsertedRows(result.getInsertedRows() + 1);
        }

        for (RowData data : toUpdate) {
            Facility facility = data.target;
            for (Map.Entry<String, Object> entry : data.values.entrySet()) {
                // ★ 空单元格对应的列根本不在 values 里，因此不会被覆盖 —— 见类注释 ⑥
                applyValue(facility, entry.getKey(), entry.getValue());
            }
            // 审计字段保持原值：updateById 默认不更新 null 字段，
            // 但这里显式不碰它们，避免「导入把创建人改成当前登录人」这种痕迹污染
            facilityMapper.updateById(facility);
            result.setUpdatedRows(result.getUpdatedRows() + 1);
        }

        if (result.getInsertedRows() == 0 && result.getUpdatedRows() == 0 && result.getErrorCount() == 0
                && result.getSkippedDuplicates() == 0) {
            result.addNotice("没有需要写入的数据");
        }
    }

    /**
     * 新增时用宗地信息补默认值（地块名称 / 行政区划 / 项目分类）。
     *
     * <p>与配套录入 Service（{@code FacilityAdminServiceImpl.createFacility}）同一口径：
     * 这三项在宗地表里已经有了，配套再抄一遍纯属重复劳动，且很容易抄错
     * （实测旧库配套表的 {@code xzqh} 就混进了「生态城 / 经开区」这类非行政区值）。
     * ★ 只在<b>文件里没填</b>时补：用户明确填了的值一律以用户为准 ——
     * 配套的行政区划确实可能与宗地不同（跨区配套是存在的）。
     *
     * <p>覆盖更新（UPDATE 策略）时<b>不</b>补：那会变成「用一个用户没填的单元格去改库里的值」，
     * 违背「空单元格不改动」的承诺（见类注释 ⑥）。
     */
    private void fillFromLand(Facility facility, Land land) {
        if (land == null) {
            return;
        }
        if (StringUtils.isBlank(facility.getDkmc())) {
            facility.setDkmc(land.getDkmc());
        }
        if (StringUtils.isBlank(facility.getXzqh())) {
            facility.setXzqh(land.getXzqh());
        }
        if (StringUtils.isBlank(facility.getXmfl())) {
            facility.setXmfl(land.getXmfl());
        }
    }

    /**
     * 列名 → 实体字段（每个列名只在这里出现一次）。
     *
     * <p>刻意不用反射：反射写错字段名要到运行时才炸，而这里是编译期就能查出来的 switch。
     * 也刻意不做成「一份 Map&lt;列名, Setter&gt;」：56 个列的 setter 引用
     * 写起来和 switch 一样长，但 switch 在编译期就能查出错别字。
     */
    private void applyValue(Facility facility, String column, Object value) {
        switch (column) {
            case "crzdbh":
                // 半角括号归一成中文括号，与单个录入（FacilityAdminServiceImpl）同一口径 ——
                // 否则同一个配套会因括号写法不同而变成两条记录
                facility.setCrzdbh(DataSupport.normalizeBrackets((String) value));
                break;
            case "ptxmmc":
                facility.setPtxmmc(DataSupport.normalizeBrackets((String) value));
                break;
            case "dkmc":
                facility.setDkmc(DataSupport.normalizeBrackets((String) value));
                break;
            case "ptsslb":
                facility.setPtsslb((String) value);
                break;
            case "xzqh":
                facility.setXzqh((String) value);
                break;
            case "xmfl":
                facility.setXmfl((String) value);
                break;
            case "jsxx":
                facility.setJsxx((String) value);
                break;
            case "dldj":
                facility.setDldj((String) value);
                break;
            case "sfzsjtjlz":
                facility.setSfzsjtjlz((String) value);
                break;
            case "tjlzsftg":
                facility.setTjlzsftg((String) value);
                break;
            case "ghhxkd":
                facility.setGhhxkd((BigDecimal) value);
                break;
            case "cd":
                facility.setCd((BigDecimal) value);
                break;
            case "tzgs":
                facility.setTzgs((BigDecimal) value);
                break;
            case "zjly":
                facility.setZjly((String) value);
                break;
            case "dkcrscndptjgsj":
                facility.setDkcrscndptjgsj((Date) value);
                break;
            case "jsdw":
                facility.setJsdw((String) value);
                break;
            case "sjdw":
                facility.setSjdw((String) value);
                break;
            case "kcdw":
                facility.setKcdw((String) value);
                break;
            case "jldw":
                facility.setJldw((String) value);
                break;
            case "sgdw":
                facility.setSgdw((String) value);
                break;
            case "jsgydw":
                facility.setJsgydw((String) value);
                break;
            case "xjpfsfwc":
                facility.setXjpfsfwc((String) value);
                break;
            case "xjpfzt":
                facility.setXjpfzt((String) value);
                break;
            case "kypfsfwc":
                facility.setKypfsfwc((String) value);
                break;
            case "kypfzt":
                facility.setKypfzt((String) value);
                break;
            case "csjgspfsfwc":
                facility.setCsjgspfsfwc((String) value);
                break;
            case "csjgspfzt":
                facility.setCsjgspfzt((String) value);
                break;
            case "gspfje":
                facility.setGspfje((BigDecimal) value);
                break;
            case "zjlsqk":
                facility.setZjlsqk((String) value);
                break;
            case "sfkg":
                facility.setSfkg((String) value);
                break;
            case "kgzt":
                facility.setKgzt((String) value);
                break;
            case "yjkgsj":
                facility.setYjkgsj((Date) value);
                break;
            case "sjkgsj":
                facility.setSjkgsj((Date) value);
                break;
            case "sfjg":
                facility.setSfjg((String) value);
                break;
            case "yjjgsj":
                facility.setYjjgsj((Date) value);
                break;
            case "sjjgsj":
                facility.setSjjgsj((Date) value);
                break;
            case "sfyj":
                facility.setSfyj((String) value);
                break;
            case "ptxmhdydydjdc":
                facility.setPtxmhdydydjdc((String) value);
                break;
            case "xjpfwj":
                facility.setXjpfwj((String) value);
                break;
            case "kypfwj":
                facility.setKypfwj((String) value);
                break;
            case "csjgspfwj":
                facility.setCsjgspfwj((String) value);
                break;
            case "dlgh":
                facility.setDlgh((String) value);
                break;
            case "ghgcxk":
                facility.setGhgcxk((String) value);
                break;
            case "gxzhslsj":
                facility.setGxzhslsj((String) value);
                break;
            case "zyptfa":
                facility.setZyptfa((String) value);
                break;
            case "zyglyj":
                facility.setZyglyj((String) value);
                break;
            case "ghydxkyhbsxbl":
                facility.setGhydxkyhbsxbl((String) value);
                break;
            case "sgxk":
                facility.setSgxk((String) value);
                break;
            case "bdcdj":
                facility.setBdcdj((String) value);
                break;
            case "jtwt":
                facility.setJtwt((String) value);
                break;
            case "gzjy":
                facility.setGzjy((String) value);
                break;
            case "zlqsnrjsm":
                facility.setZlqsnrjsm((String) value);
                break;
            case "bz":
                facility.setBz((String) value);
                break;
            case "lrdw":
                facility.setLrdw((String) value);
                break;
            case "lrr":
                facility.setLrr((String) value);
                break;
            case "lxdh":
                facility.setLxdh((String) value);
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
        return text == null ? "" : FacilityImportSupport.stripControlChars(text).trim();
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
     * @Description: 库内宗地索引（一次查询建好，供存在性校验与近失配提示共用）
     */
    private static class LandIndex {

        /** 编号（原样） → 宗地 */
        final Map<String, Land> byCode = new LinkedHashMap<>();

        /** 归一化编号 → 库内原编号（近失配提示用；同键只留第一个） */
        final Map<String, String> byNormalized = new LinkedHashMap<>();
    }

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
        /** 出让宗地编号（判重键的一半） */
        String crzdbh;
        /** 配套项目名称（判重键的另一半） */
        String ptxmmc;
        /** 是否通过校验 */
        boolean valid = true;
        /** 是否因重复被跳过（策略 = skip） */
        boolean skipped;
        /** 按编号命中的库内宗地（新增时补默认值用） */
        Land land;
        /** 覆盖更新时命中的库内配套 */
        Facility target;

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

        void addError(FacilityImportField.FieldSpec spec, String message) {
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
        final FacilityImportField.FieldSpec spec;
        final String message;

        RowErrorDraft(FacilityImportField.FieldSpec spec, String message) {
            this.spec = spec;
            this.message = message;
        }
    }

    /**
     * @Description: 原始行内容（Excel 行号 → 该行的原始列值），供错误回执回填
     */
    private static class OriginalValues implements FacilityImportSupport.ErrorReportSource {

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
