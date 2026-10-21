package org.jeecg.modules.land.stat.query.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 查询统计 · 字段白名单（自定义列 / 高级查询 / 导出共用的唯一字段来源）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（五）「经营性用地综合查询」里旧系统的两个能力：
 * ① 结果表「自定义列」（旧 {@code getCommercialLandField()} / {@code getCustomCommercialLandField()}）；
 * ② 高级查询的「字段 / 运算符 / 字段值 / 关系符」四件套（旧 {@code fieldQuery} +
 * {@code fieldTypeQuery} + {@code queryFieldContent}）。
 *
 * <p><b>★ 为什么需要这份白名单，而不是让前端直接传列名</b>：
 * 旧系统的做法是把前端拼出来的 SQL 片段（{@code textSql}）原样丢给后端执行
 * （见 {@code xjKjkfbCommercialLandQuery.js} 的 {@code insertSql123()}），
 * 属于教科书级的 SQL 注入面。新系统只接受<b>字段 key</b>，
 * 由本表映射到真实列名——用户永远无法让一个不存在的列名进入 SQL。
 *
 * <p>同一份定义同时服务四个用途，避免「条件下拉的字段」「自定义列的字段」
 * 「导出列」「实际能查的列」四份清单互相漂移：
 * <pre>
 *   label        中文名（条件下拉 / 列头 / Excel 表头）
 *   column       真实列名（仅本类内部使用，不出现在任何响应里）
 *   type         TEXT / NUMBER / DATE —— 决定可用的运算符与是否加引号
 *   ops          该字段允许的运算符
 *   defaultColumn 结果表默认显示（旧系统写死的 6 列 / 配套的 7 列）
 * </pre>
 */
public final class QueryFieldRegistry {

    private QueryFieldRegistry() {
    }

    /** 数据集：经营性用地（t_land）/ 配套项目（t_supporting_facilities） */
    public enum DataSet {
        LAND, FACILITY;

        public static DataSet of(String value) {
            if (value == null || value.trim().isEmpty()) {
                return LAND;
            }
            String v = value.trim().toUpperCase();
            if ("FACILITY".equals(v) || "SUPPORTING".equals(v)) {
                return FACILITY;
            }
            return LAND;
        }
    }

    /** 字段类型：决定运算符集合与值是否按字符串处理 */
    public enum FieldType {
        TEXT, NUMBER, DATE
    }

    /** 运算符（只允许这几种，杜绝任意 SQL 片段） */
    public enum Operator {
        LIKE, EQ, NE, GT, GE, LT, LE;

        /** 前端传来的运算符 → 枚举；不认识的一律当 LIKE（与旧系统默认行为一致） */
        public static Operator of(String raw) {
            if (raw == null) {
                return LIKE;
            }
            switch (raw.trim().toLowerCase()) {
                case "like":
                    return LIKE;
                case "=":
                case "eq":
                    return EQ;
                case "!=":
                case "<>":
                case "ne":
                    return NE;
                case ">":
                case "gt":
                    return GT;
                case ">=":
                case "ge":
                    return GE;
                case "<":
                case "lt":
                    return LT;
                case "<=":
                case "le":
                    return LE;
                default:
                    return LIKE;
            }
        }

        /** MyBatis 里要拼的 SQL 比较符（仅白名单值，不含用户输入） */
        public String sql() {
            switch (this) {
                case EQ:
                    return "=";
                case NE:
                    return "<>";
                case GT:
                    return ">";
                case GE:
                    return ">=";
                case LT:
                    return "<";
                case LE:
                    return "<=";
                default:
                    return "LIKE";
            }
        }
    }

    /** 一个可查询 / 可展示的字段 */
    public static final class Field {
        private final String key;
        private final String label;
        private final String column;
        private final FieldType type;
        private final List<Operator> ops;
        private final boolean defaultColumn;
        private final String unit;

        Field(String key, String label, String column, FieldType type,
              List<Operator> ops, boolean defaultColumn, String unit) {
            this.key = key;
            this.label = label;
            this.column = column;
            this.type = type;
            this.ops = ops;
            this.defaultColumn = defaultColumn;
            this.unit = unit;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public String getColumn() {
            return column;
        }

        public FieldType getType() {
            return type;
        }

        public List<Operator> getOps() {
            return ops;
        }

        public boolean isDefaultColumn() {
            return defaultColumn;
        }

        public String getUnit() {
            return unit;
        }
    }

    private static final List<Operator> TEXT_OPS =
            Collections.unmodifiableList(Arrays.asList(Operator.LIKE, Operator.EQ, Operator.NE));
    private static final List<Operator> NUMBER_OPS =
            Collections.unmodifiableList(Arrays.asList(Operator.EQ, Operator.NE,
                    Operator.GT, Operator.GE, Operator.LT, Operator.LE));
    private static final List<Operator> DATE_OPS = NUMBER_OPS;

    /** 经营性用地字段（顺序 = 条件下拉与导出列的顺序 = 旧系统字段清单顺序） */
    private static final Map<String, Field> LAND_FIELDS;
    /** 配套项目字段 */
    private static final Map<String, Field> FACILITY_FIELDS;

    static {
        Map<String, Field> land = new LinkedHashMap<>();
        // —— 旧系统写死的 6 列（行政区划 / 出让宗地编号 / 地块名称 / 规划用地性质 /
        //    纳入成本配套费 / 受让人），顺序对齐结果表默认列 ——
        add(land, text("crzdbh", "出让宗地编号", true, null));
        add(land, text("dkmc", "地块名称", true, null));
        add(land, text("xzqh", "行政区划", true, null));
        add(land, text("ghydxz", "规划用地性质", true, null));
        add(land, number("nrcbdptf", "纳入成本的配套费", true, "万元"));
        add(land, text("srr", "受让人", true, null));
        // —— 其余可查询/可导出字段 ——
        add(land, text("xmfl", "项目分类", false, null));
        add(land, number("crj", "出让金", false, "亿元"));
        add(land, date("crsj", "出让时间", false, null));
        add(land, number("kjsydmj", "可建设用地面积", false, "平方米"));
        add(land, number("zydmj", "总用地面积", false, "平方米"));
        add(land, number("jsmj", "建设面积", false, "平方米"));
        add(land, number("wcd", "完成度", false, null));
        add(land, text("ptsfqq", "配套是否齐全", false, null));
        add(land, text("ptjsnr", "配套建设内容", false, null));
        add(land, date("htydjfsj", "合同约定交付时间", false, null));
        add(land, text("lpmc", "楼盘名称", false, null));
        add(land, date("lpjfsj", "楼盘交付时间", false, null));
        add(land, text("tdzldw", "土地整理单位", false, null));
        add(land, text("tdzljhxdwjh", "土地整理计划下达文件号", false, null));
        add(land, text("tdzljh", "土地整理计划", false, null));
        add(land, text("dz", "东至", false, null));
        add(land, text("xz", "西至", false, null));
        add(land, text("nz", "南至", false, null));
        add(land, text("bz", "北至", false, null));
        add(land, text("ptqkh", "配套情况函", false, null));
        add(land, text("ptcbh", "配套筹备函", false, null));
        // ★ xzqh2 的注释是「录入单位简称」：旧系统（getCnd）却拿它当行政区划筛选用，
        //   差异清单 D-604 已登记。新系统照实标成「录入单位简称」，不再冒充行政区划，
        //   同时保留可查（老用户可能按录入单位找数据）。
        add(land, text("xzqh2", "录入单位简称", false, null));
        add(land, text("zlqsnrsm", "资料缺失内容及说明", false, null));
        add(land, text("lrdw", "录入单位", false, null));
        add(land, text("lrr", "录入人", false, null));
        add(land, text("lxdh", "联系电话", false, null));
        add(land, text("beizhu", "备注", false, null));
        add(land, date("createTime", "创建时间", false, null));
        LAND_FIELDS = Collections.unmodifiableMap(land);

        Map<String, Field> facility = new LinkedHashMap<>();
        // —— 高保真「配套查询结果」默认 7 列 ——
        add(facility, text("crzdbh", "出让宗地编号", true, null));
        add(facility, text("ptxmmc", "配套项目名称", true, null));
        add(facility, text("dkmc", "地块名称", true, null));
        add(facility, text("ptsslb", "配套设施类别", true, null));
        add(facility, text("xzqh", "行政区划", true, null));
        add(facility, text("xmfl", "项目分类", true, null));
        add(facility, text("jsxx", "建设性质", true, null));
        // —— 其余 ——
        add(facility, text("dldj", "道路等级", false, null));
        add(facility, text("sfzsjtjlz", "是否涉及提级论证", false, null));
        add(facility, text("tjlzsftg", "提级论证是否通过", false, null));
        add(facility, number("ghhxkd", "规划红线宽度", false, "米"));
        add(facility, number("cd", "长度", false, "米"));
        add(facility, number("tzgs", "投资估算", false, "万元"));
        add(facility, text("zjly", "资金来源", false, null));
        add(facility, date("dkcrscndptjgsj", "地块出让时承诺的配套竣工时间", false, null));
        add(facility, text("jsdw", "建设单位", false, null));
        add(facility, text("sjdw", "设计单位", false, null));
        add(facility, text("kcdw", "勘察单位", false, null));
        add(facility, text("jldw", "监理单位", false, null));
        add(facility, text("sgdw", "施工单位", false, null));
        add(facility, text("jsgydw", "接收管养单位", false, null));
        add(facility, text("xjpfsfwc", "项建批复是否完成", false, null));
        add(facility, text("kypfsfwc", "可研批复是否完成", false, null));
        add(facility, text("csjgspfsfwc", "初设及概算批复是否完成", false, null));
        add(facility, number("gspfje", "概算批复金额", false, "万元"));
        add(facility, text("zjlsqk", "资金落实情况", false, null));
        add(facility, text("sfkg", "是否开工", false, null));
        add(facility, date("yjkgsj", "预计开工时间", false, null));
        add(facility, date("sjkgsj", "实际开工时间", false, null));
        add(facility, text("sfjg", "是否竣工", false, null));
        add(facility, date("yjjgsj", "预计竣工时间", false, null));
        add(facility, date("sjjgsj", "实际竣工时间", false, null));
        add(facility, text("sfyj", "是否移交", false, null));
        add(facility, text("jtwt", "具体问题", false, null));
        add(facility, text("gzjy", "工作建议", false, null));
        add(facility, text("zlqsnrjsm", "资料缺失内容及说明", false, null));
        add(facility, text("bz", "备注", false, null));
        add(facility, text("lrdw", "录入单位", false, null));
        add(facility, text("lrr", "录入人", false, null));
        add(facility, text("lxdh", "联系电话", false, null));
        add(facility, date("createTime", "创建时间", false, null));
        FACILITY_FIELDS = Collections.unmodifiableMap(facility);
    }

    private static void add(Map<String, Field> target, Field field) {
        target.put(field.getKey(), field);
    }

    private static Field text(String key, String label, boolean defaultColumn, String unit) {
        return new Field(key, label, key, FieldType.TEXT, TEXT_OPS, defaultColumn, unit);
    }

    private static Field number(String key, String label, boolean defaultColumn, String unit) {
        return new Field(key, label, key, FieldType.NUMBER, NUMBER_OPS, defaultColumn, unit);
    }

    private static Field date(String key, String label, boolean defaultColumn, String unit) {
        return new Field(key, label, key, FieldType.DATE, DATE_OPS, defaultColumn, unit);
    }

    /** 全部字段（顺序即向前端下发的顺序） */
    public static List<Field> fields(DataSet dataSet) {
        return new ArrayList<>((dataSet == DataSet.FACILITY ? FACILITY_FIELDS : LAND_FIELDS).values());
    }

    /** 取字段定义；不在白名单里返回 null（调用方负责忽略或报错） */
    public static Field find(DataSet dataSet, String key) {
        if (key == null) {
            return null;
        }
        return (dataSet == DataSet.FACILITY ? FACILITY_FIELDS : LAND_FIELDS).get(key.trim());
    }

    /** 默认展示列（旧系统写死的那几列） */
    public static List<String> defaultColumns(DataSet dataSet) {
        List<String> keys = new ArrayList<>();
        for (Field field : fields(dataSet)) {
            if (field.isDefaultColumn()) {
                keys.add(field.getKey());
            }
        }
        return keys;
    }

    /**
     * 解析前端传来的自定义列（逗号分隔）。
     *
     * <p>白名单外的 key 直接丢弃而不是报错：旧系统结果表列由后端字段定义生成，
     * 前端传错只会「少一列」；报错会让整张表打不开，体验更差。
     * 全都非法或为空时回落到默认列。
     */
    public static List<String> resolveColumns(DataSet dataSet, String raw) {
        List<String> resolved = new ArrayList<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                Field field = find(dataSet, part);
                if (field != null && !resolved.contains(field.getKey())) {
                    resolved.add(field.getKey());
                }
            }
        }
        if (resolved.isEmpty()) {
            resolved.addAll(defaultColumns(dataSet));
        }
        return resolved;
    }
}
