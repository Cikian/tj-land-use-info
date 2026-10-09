package org.jeecg.modules.land.data.imports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 经营性用地批量导入 - 字段字典（模板 / 校验 / 入库 / 错误回执 的唯一真相）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>为什么要有这个类</b>：批量导入最容易烂掉的地方是「模板一套列名、校验一套规则、
 * 入库再一套映射」——三处各写一遍，改一个字段就要改三个地方，且必然漂移。
 * 这里把「列名（英文字段名）→ 中文说明 → 类型 → 是否必填 → 校验规则 → 入库 setter」
 * 全部收敛成一张表，模板生成、预览校验、确认入库、错误回执导出<b>都从这里读</b>。
 *
 * <p><b>★ 列名为什么用旧库的英文字段名而不是中文</b>：
 * 旧系统 {@code xjKjkfbCommercialLandController.getExcelDemo()} 生成的两行表头就是
 * 「第 0 行 = 英文字段名、第 1 行 = 中文注释」，数据从第 3 行开始。
 * 沿用同一套列名有三个好处：
 * <ol>
 *   <li>中心手里<b>已经在用</b>的 Excel 可以直接传进来导入，不需要重新填一遍；</li>
 *   <li>旧库 {@code xj_kjkfb_commercial_land} 与 {@code t_land} 列名一一对应，
 *       排查数据问题时不用做中文↔英文的二次翻译；</li>
 *   <li>解析器可以按「英文字段名」直接定位列，不受用户是否改过中文说明行影响。</li>
 * </ol>
 *
 * <p><b>★ 与旧系统的差异（都是有意的）</b>：
 * <table border="1">
 *   <tr><th>项</th><th>旧系统</th><th>本实现</th><th>为什么</th></tr>
 *   <tr>
 *     <td>排除列</td><td>id / wcd / createAccount / createTime / delFlag</td>
 *     <td>同上 + source_id</td>
 *     <td>{@code source_id} 是迁移溯源列，由迁移脚本写，不允许用户填</td>
 *   </tr>
 *   <tr>
 *     <td>必填</td><td>crzdbh / xmfl / lrdw / lrr / lxdh（五者任一为空 → 静默跳过该行并计数）</td>
 *     <td>同上，但<b>逐行回执</b>「第 N 行：xxx 不能为空」，不允许静默跳过</td>
 *     <td>静默跳过会让用户以为导进去了，是旧系统最坑人的一点</td>
 *   </tr>
 *   <tr>
 *     <td>手机号/项目分类格式错</td><td>中断整个导入，留下「已导入 N 条」的半成品</td>
 *     <td>整表预校验，一行都不写</td>
 *     <td>批量导入最怕「一半进了一半没进」</td>
 *   </tr>
 *   <tr>
 *     <td>重复宗地编号</td><td>固定「跳过」并计数</td>
 *     <td>三种策略可选：报错（默认）/ 跳过 / 覆盖更新</td>
 *     <td>日常补录要的是「别覆盖」，年度数据更新要的是「覆盖」</td>
 *   </tr>
 *   <tr>
 *     <td>写库方式</td><td>字符串拼 SQL（{@code INSERT INTO tbl SET k="v"}），注入风险</td>
 *     <td>MyBatis-Plus 参数化写入</td>
 *     <td>设计文档 5.3.3 明确要求修掉</td>
 *   </tr>
 * </table>
 */
public final class LandImportField {

    private LandImportField() {
    }

    /**
     * @Description: 字段类型
     */
    public enum FieldType {
        /** 文本 */
        TEXT,
        /** 数值（decimal(14,2)） */
        NUMBER,
        /** 日期（date） */
        DATE,
        /** 是/否 枚举（写入前会归一化为「是」/「否」） */
        YES_NO,
        /** 受控枚举（取值必须在 {@link FieldSpec#getOptions()} 内，或命中别名） */
        ENUM
    }

    /**
     * @Description: 一个可导入字段的完整规格
     */
    public static final class FieldSpec {

        /** 列名 = 旧库英文字段名 = 模板第 1 行表头 */
        private final String column;
        /** 中文说明 = 旧库 COLUMN_COMMENT = 模板第 2 行表头 */
        private final String label;
        /** 类型 */
        private final FieldType type;
        /** 是否必填（批量导入口径） */
        private final boolean required;
        /** 是否出现在模板里 */
        private final boolean templated;
        /** 受控取值（仅 ENUM / YES_NO 有意义；空表示不限制取值，例如 ghydxz 有 60+ 种自由文本） */
        private final List<String> options;
        /** 取值别名 → 规范值（仅 ENUM / YES_NO 有意义） */
        private final Map<String, String> aliases;
        /** 最大长度（0 表示不限制；来自建表脚本的列宽） */
        private final int maxLength;
        /** 示例值（填表说明里给一行示范） */
        private final String sample;

        FieldSpec(String column, String label, FieldType type, boolean required, boolean templated,
                  List<String> options, Map<String, String> aliases, int maxLength, String sample) {
            this.column = column;
            this.label = label;
            this.type = type;
            this.required = required;
            this.templated = templated;
            this.options = options == null ? Collections.<String>emptyList() : options;
            this.aliases = aliases == null ? Collections.<String, String>emptyMap() : aliases;
            this.maxLength = maxLength;
            this.sample = sample;
        }

        public String getColumn() {
            return column;
        }

        public String getLabel() {
            return label;
        }

        public FieldType getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }

        public boolean isTemplated() {
            return templated;
        }

        public List<String> getOptions() {
            return options;
        }

        public Map<String, String> getAliases() {
            return aliases;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public String getSample() {
            return sample;
        }

        /** 「是 / 否」这种取值提示文案，用于填表说明与错误提示 */
        public String optionText() {
            if (type == FieldType.YES_NO) {
                return "是 / 否";
            }
            if (options.isEmpty()) {
                return "自由文本";
            }
            return String.join(" / ", options);
        }
    }

    // ==================================================================
    // 一、取值字典
    // ==================================================================

    /** 项目分类（旧系统只认这两个值，写死不允许扩） */
    public static final List<String> XMFL_OPTIONS = Collections.unmodifiableList(
            Arrays.asList("市级项目", "区级项目"));

    /** 是 / 否 */
    public static final List<String> YES_NO_OPTIONS = Collections.unmodifiableList(
            Arrays.asList("是", "否"));

    /**
     * 天津市 16 个行政区。
     * 实测旧库 {@code xj_kjkfb_commercial_land.xzqh} 的 16 个取值与本表完全一致（无功能区混入）。
     */
    public static final List<String> XZQH_OPTIONS = Collections.unmodifiableList(Arrays.asList(
            "和平区", "河东区", "河西区", "南开区", "河北区", "红桥区",
            "东丽区", "西青区", "津南区", "北辰区", "武清区", "宝坻区",
            "滨海新区", "宁河区", "静海区", "蓟州区"));

    /** 是/否 的常见写法 → 规范值 */
    private static Map<String, String> yesNoAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String yes : Arrays.asList("是", "有", "√", "✓", "对", "1", "true", "y", "yes", "已")) {
            map.put(yes, "是");
        }
        for (String no : Arrays.asList("否", "无", "×", "x", "╳", "0", "false", "n", "no", "未")) {
            map.put(no, "否");
        }
        return map;
    }

    /**
     * 行政区划的别名：允许写「滨海」（不带「区」），会归一化成「滨海新区」。
     *
     * <p>★ 别名表里<b>只放能唯一确定目标区的写法</b>。
     * 「市内六区」「环城四区」这类<b>区组</b>统称在旧库的区级口径表里出现过，
     * 但它对应多个区、无法自动归位，因此<b>刻意不放进别名表</b> ——
     * 放进去等于猜，猜错就把数据写到别的区去了。
     * 用户填这类写法会走 ENUM 校验被拦下，并收到「16 个区」的取值提示。
     */
    private static Map<String, String> xzqhAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String option : XZQH_OPTIONS) {
            map.put(option, option);
            // 允许「滨海」「蓟州」这种不带「区」的写法
            String shortName = option.endsWith("区") ? option.substring(0, option.length() - 1) : option;
            map.put(shortName, option);
        }
        return map;
    }

    // ==================================================================
    // 二、字段清单（顺序 = 模板列顺序 = 错误回执列顺序）
    // ==================================================================

    private static final List<FieldSpec> FIELDS;

    /** 列名 → 字段规格（保持插入顺序） */
    private static final Map<String, FieldSpec> BY_COLUMN;

    static {
        Map<String, String> yesNo = yesNoAliases();
        Map<String, String> xzqh = xzqhAliases();

        List<FieldSpec> list = new ArrayList<>();

        // ---- 标识与分类（前 5 个就是旧系统的必填五项）----
        list.add(new FieldSpec("crzdbh", "出让宗地编号", FieldType.TEXT, true, true,
                null, null, 100, "津西青(挂)2024-01号"));
        list.add(new FieldSpec("xmfl", "项目分类", FieldType.ENUM, true, true,
                XMFL_OPTIONS, null, 100, "区级项目"));
        list.add(new FieldSpec("dkmc", "地块名称", FieldType.TEXT, false, true,
                null, null, 255, "侯台片区地块一"));
        list.add(new FieldSpec("xzqh", "行政区划", FieldType.ENUM, false, true,
                XZQH_OPTIONS, xzqh, 100, "西青区"));
        list.add(new FieldSpec("ghydxz", "规划用地性质", FieldType.TEXT, false, true,
                null, null, 100, "城镇住宅、商服"));

        // ---- 面积与金额 ----
        list.add(new FieldSpec("crj", "出让金（亿元）", FieldType.NUMBER, false, true,
                null, null, 0, "12.35"));
        list.add(new FieldSpec("crsj", "出让时间", FieldType.DATE, false, true,
                null, null, 0, "2024-06-18"));
        list.add(new FieldSpec("kjsydmj", "可建设用地面积(平方米)", FieldType.NUMBER, false, true,
                null, null, 0, "45231.50"));
        list.add(new FieldSpec("zydmj", "总用地面积（平方米）", FieldType.NUMBER, false, true,
                null, null, 0, "51008.00"));
        list.add(new FieldSpec("jsmj", "建设面积（平方米）", FieldType.NUMBER, false, true,
                null, null, 0, "90463.00"));
        list.add(new FieldSpec("nrcbdptf", "纳入成本的配套费(万元)", FieldType.NUMBER, false, true,
                null, null, 0, "1580.00"));

        // ---- 受让人与交付 ----
        list.add(new FieldSpec("srr", "受让人", FieldType.TEXT, false, true,
                null, null, 100, "天津某某置业有限公司"));
        list.add(new FieldSpec("htydjfsj", "合同约定交付时间", FieldType.DATE, false, true,
                null, null, 0, "2026-12-31"));
        list.add(new FieldSpec("lpmc", "楼盘名称", FieldType.TEXT, false, true,
                null, null, 100, "某某云著"));
        list.add(new FieldSpec("lpjfsj", "楼盘交付时间（或计划交付时间）", FieldType.DATE, false, true,
                null, null, 0, "2027-06-30"));

        // ---- 四至 ----
        list.add(new FieldSpec("dz", "东至", FieldType.TEXT, false, true,
                null, null, 100, "规划路一"));
        list.add(new FieldSpec("xz", "西至", FieldType.TEXT, false, true,
                null, null, 100, "现状住宅"));
        list.add(new FieldSpec("nz", "南至", FieldType.TEXT, false, true,
                null, null, 100, "规划绿地"));
        list.add(new FieldSpec("bz", "北至", FieldType.TEXT, false, true,
                null, null, 100, "某某路"));

        // ---- 土地整理 ----
        list.add(new FieldSpec("tdzldw", "土地整理单位", FieldType.TEXT, false, true,
                null, null, 100, "天津市土地利用事务中心"));
        list.add(new FieldSpec("tdzljhxdwjh", "土地整理计划下达文件号", FieldType.TEXT, false, true,
                null, null, 2000, "津国土房整〔2024〕15号"));
        list.add(new FieldSpec("tdzljh", "土地整理计划", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "是"));

        // ---- 配套与图形 ----
        list.add(new FieldSpec("ptsfqq", "配套是否齐全", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("ptqkh", "配套情况函", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "是"));
        list.add(new FieldSpec("ptcbh", "配套筹备函", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "是"));
        list.add(new FieldSpec("crzdtxsj", "出让宗地图形数据（shp）", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("ptjsnr", "配套建设内容", FieldType.TEXT, false, true,
                null, null, 2000, "地块北侧规划路及雨污水管线"));

        // ---- 录入信息（旧系统要求批量导入时必填后三项）----
        list.add(new FieldSpec("lrdw", "录入单位", FieldType.TEXT, true, true,
                null, null, 100, "天津市土地利用事务中心"));
        list.add(new FieldSpec("lrr", "录入人", FieldType.TEXT, true, true,
                null, null, 100, "张三"));
        list.add(new FieldSpec("lxdh", "联系电话", FieldType.TEXT, true, true,
                null, null, 100, "13812345678"));

        // ---- 说明 ----
        list.add(new FieldSpec("zlqsnrsm", "资料缺失内容及说明", FieldType.TEXT, false, true,
                null, null, 2500, "缺少出让合同扫描件"));
        list.add(new FieldSpec("beizhu", "备注", FieldType.TEXT, false, true,
                null, null, 2000, ""));

        // ---- 模板外字段：允许在回执里、也允许解析（用户从旧导出文件里带回），但不进模板 ----
        list.add(new FieldSpec("wcd", "完成度", FieldType.NUMBER, false, false,
                null, null, 0, ""));
        list.add(new FieldSpec("xzqh2", "录入单位简称（旧列名 xzqh2）", FieldType.TEXT, false, false,
                null, null, 100, ""));

        FIELDS = Collections.unmodifiableList(list);

        Map<String, FieldSpec> byColumn = new LinkedHashMap<>();
        for (FieldSpec spec : FIELDS) {
            byColumn.put(spec.getColumn(), spec);
        }
        BY_COLUMN = Collections.unmodifiableMap(byColumn);
    }

    // ==================================================================
    // 三、只读访问
    // ==================================================================

    /** 全部可导入字段（含模板外字段），顺序即回执列顺序 */
    public static List<FieldSpec> all() {
        return FIELDS;
    }

    /** 模板里的字段（顺序即模板列顺序） */
    public static List<FieldSpec> templated() {
        List<FieldSpec> list = new ArrayList<>();
        for (FieldSpec spec : FIELDS) {
            if (spec.isTemplated()) {
                list.add(spec);
            }
        }
        return list;
    }

    /** 必填字段 */
    public static List<FieldSpec> required() {
        List<FieldSpec> list = new ArrayList<>();
        for (FieldSpec spec : FIELDS) {
            if (spec.isRequired()) {
                list.add(spec);
            }
        }
        return list;
    }

    /** 按列名取字段规格，未登记返回 null */
    public static FieldSpec of(String column) {
        return column == null ? null : BY_COLUMN.get(column);
    }

    /** 模板列名清单（错误回执、模板校验都用它） */
    public static List<String> templatedColumns() {
        List<String> columns = new ArrayList<>();
        for (FieldSpec spec : templated()) {
            columns.add(spec.getColumn());
        }
        return columns;
    }

    /** 必填列的中文名，用于「模板不匹配」这类整体性提示 */
    public static String requiredLabelText() {
        List<String> labels = new ArrayList<>();
        for (FieldSpec spec : required()) {
            labels.add(spec.getLabel());
        }
        return String.join("、", labels);
    }
}
