package org.jeecg.modules.land.data.imports.facility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 配套信息批量导入 - 字段字典（模板 / 校验 / 入库 / 错误回执 的唯一真相）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>与经营性用地导入的 {@code LandImportField} 是<b>同构的两份字典</b>，
 * 刻意不做成一份共用：两者的列名、列宽、必填项、枚举值完全不同，
 * 硬塞进一个类只会变成「一半字段对宗地有意义、一半对配套有意义」的联合体，
 * 每个调用点都要先判断「我这次是哪种导入」。两份各自独立、各自完整，改一边不会牵连另一边。
 *
 * <p><b>★ 列名为什么必须是旧库的真实列名</b>：
 * 旧系统 {@code xjKjkfbSupportingFacilitiesController.getExcelDemo()} 生成的两行表头就是
 * 「第 0 行 = 英文字段名、第 1 行 = 中文注释」，数据从第 3 行开始。
 * 而配套表 {@code xj_kjkfb_supporting_facilities} 是<b>直接复用旧表名</b>的
 * （见 {@code Facility} 实体注释），列名与旧库逐字一致，因此：
 * <ol>
 *   <li>中心手里已在用的配套 Excel 可以直接传进来，不需要重新填；</li>
 *   <li>解析器按英文字段名定位列，用户改中文说明行、删列、调列顺序都不影响导入。</li>
 * </ol>
 *
 * <p><b>★ 与旧系统的差异（都是有意的）</b>：
 * <table border="1">
 *   <tr><th>项</th><th>旧系统</th><th>本实现</th><th>为什么</th></tr>
 *   <tr>
 *     <td>排除列</td><td>id / createAccount / createTime / delFlag</td>
 *     <td>同上</td>
 *     <td>这 4 列由系统写，不接受人工填写（与旧模板一致）</td>
 *   </tr>
 *   <tr>
 *     <td>必填</td><td>crzdbh / ptxmmc / lrdw / lrr / lxdh（旧代码这段校验被注释掉了，
 *         实际是「只要有一列能拼进 SQL 就插」，于是产生了大量无编号无名称的废行）</td>
 *     <td>同五项，<b>逐行回执</b>「第 N 行：xxx 不能为空」</td>
 *     <td>静默插入半空行比拒绝导入危害大得多：页面上看起来「导入成功 800 条」，
 *         实际一堆行没有宗地编号，谁也没法用</td>
 *   </tr>
 *   <tr>
 *     <td>重复数据</td><td>(crzdbh, ptxmmc) 已存在 → 固定跳过并计数</td>
 *     <td>三种策略可选：报错（默认）/ 跳过 / 覆盖更新</td>
 *     <td>日常补录要「别覆盖」，年度更新要「覆盖」，一种策略满足不了两种场景</td>
 *   </tr>
 *   <tr>
 *     <td>宗地是否存在</td><td><b>完全不校验</b>（旧代码只把 crzdbh 当字符串插进去）</td>
 *     <td>默认硬校验；确需导入时勾「允许挂到未登记宗地」，这些行进<b>孤儿清单</b></td>
 *     <td>设计文档实测旧库有 <b>60 行「孤儿配套」</b>（配套表有、宗地表查不到），
 *         档案与收发文按宗地关联时这些配套永远挂不上。默认拦住，
 *         但保留一个「知道自己在干什么」的出口 —— 见
 *         {@link FacilityImportResultVO#getOrphans()}</td>
 *   </tr>
 *   <tr>
 *     <td>写库方式</td><td>字符串拼 SQL（{@code INSERT INTO tbl SET k="v"}），注入风险</td>
 *     <td>MyBatis-Plus 参数化写入</td>
 *     <td>设计文档 5.3.3/5.3.4 明确要求修掉</td>
 *   </tr>
 * </table>
 *
 * <p><b>★ 关于两个列名不同名的坑</b>：配套表是 {@code zlqsnrjsm}（含 js），
 * 宗地表是 {@code zlqsnrsm}（不含 js）—— 旧系统两处命名不一致。
 * 本字典只登记配套表的 {@code zlqsnrjsm}，用户按宗地表模板填的 {@code zlqsnrsm}
 * 在本字典里查不到，会被当作「本系统不认识的列」忽略（不会报错、也不会串列）。
 *
 * <p><b>★ 刻意<b>不</b>收录的两列</b>：{@code jgwj}（竣工文件）、{@code yjwj}（移交文件）
 * 在旧表里存在，但 {@link org.jeecg.modules.land.data.entity.Facility} 实体<b>没有声明</b>
 * 这两个字段，收进模板只会让用户填了却写不进去（模板与入库不一致是最难查的问题）。
 * 竣工与移交文件在新系统里走统一附件模块（{@code t_land_attachment} 的 12 竣工与移交文件），
 * 不再用「是/否」标志位表示。
 */
public final class FacilityImportField {

    private FacilityImportField() {
    }

    /**
     * @Description: 字段类型
     */
    public enum FieldType {
        /** 文本 */
        TEXT,
        /** 数值（decimal(10,2)） */
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

        /** 列名 = 旧库真实列名 = 模板第 1 行表头 */
        private final String column;
        /** 中文说明 = 旧库 COLUMN_COMMENT = 模板第 2 行表头 */
        private final String label;
        /** 类型 */
        private final FieldType type;
        /** 是否必填（批量导入口径） */
        private final boolean required;
        /** 是否出现在模板里 */
        private final boolean templated;
        /** 受控取值（仅 ENUM / YES_NO 有意义；空表示不限制取值） */
        private final List<String> options;
        /** 取值别名 → 规范值（仅 ENUM / YES_NO 有意义） */
        private final Map<String, String> aliases;
        /** 最大长度（0 表示不限制；来自旧表 DDL 的列宽） */
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

    /** 是 / 否 */
    public static final List<String> YES_NO_OPTIONS = Collections.unmodifiableList(
            Arrays.asList("是", "否"));

    /** 项目分类（旧系统只认这两个值） */
    public static final List<String> XMFL_OPTIONS = Collections.unmodifiableList(
            Arrays.asList("市级项目", "区级项目"));

    /**
     * 天津市 16 个行政区（与 {@code sys_dict.land_xzqh} 逐字一致）。
     *
     * <p>★ 实测旧库配套表的 {@code xzqh} <b>混入了非行政区取值</b>
     * （{@code 生态城 / 土地发展中心 / 经开区 / 高新区 / 保税区}），而且没有「滨海新区」。
     * 本字典按设计文档 3.0-4 的要求<b>只认 16 个标准行政区</b>：
     * 功能区本身不是行政区，混进 {@code xzqh} 后「按区统计」会多出几行对不上总数的分类。
     * 用户填了功能区写法会收到明确的取值提示（而不是静默写进库）。
     */
    public static final List<String> XZQH_OPTIONS = Collections.unmodifiableList(Arrays.asList(
            "和平区", "河东区", "河西区", "南开区", "河北区", "红桥区",
            "东丽区", "西青区", "津南区", "北辰区", "武清区", "宝坻区",
            "滨海新区", "宁河区", "静海区", "蓟州区"));

    /**
     * 配套设施类别（与 {@code sys_dict.land_facility_category} 逐字一致）。
     *
     * <p>★ 取「燃气」而不是旧录入页写的「供气」：字典表
     * {@code xj_kjkfb_supporting_facilities_category} 与 {@code sql/data/01_data_dict.sql}
     * 都用「燃气」，旧录入页那一处是笔误。同义异写由 {@code PTSSLB_ALIASES} 兜住。
     */
    public static final List<String> PTSSLB_OPTIONS = Collections.unmodifiableList(Arrays.asList(
            "道路", "排水", "供水", "中水", "燃气", "路灯", "绿化", "交通设施"));

    /** 建设性质 */
    public static final List<String> JSXX_OPTIONS = Collections.unmodifiableList(
            Arrays.asList("新建", "改建", "扩建", "翻建", "其他"));

    /**
     * 道路等级（3 档）。
     *
     * <p>★ 旧库实测还有「主干道 / 城市主干道 / 次干道 / 快速路」等异写，
     * 能唯一归位的（主干道系）放进别名表；语义上多一档的「快速路」<b>刻意不归位</b>
     * （快速路既不是主干路也不是次干路，硬归会改掉业务事实）。
     */
    public static final List<String> DLDJ_OPTIONS = Collections.unmodifiableList(
            Arrays.asList("城市主干路", "城市次干路", "城市支路"));

    /** 资金来源 */
    public static final List<String> ZJLY_OPTIONS = Collections.unmodifiableList(
            Arrays.asList("土地整理成本", "地块收益", "成本分摊", "区内统筹", "其它"));

    /** 审批 / 开工状态（三阶段批复与开工状态共用同一套取值） */
    public static final List<String> PROGRESS_STATUS_OPTIONS = Collections.unmodifiableList(
            Arrays.asList("正常推进", "有问题"));

    /** 资金落实情况 */
    public static final List<String> ZJLSQK_OPTIONS = Collections.unmodifiableList(
            Arrays.asList("已落实", "未落实"));

    /** 是/否 的常见写法 → 规范值（与宗地导入同一套口径） */
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
     * 行政区划别名：允许写「滨海」「蓟州」这种不带「区」的形式。
     *
     * <p>★ 别名表里<b>只放能唯一确定目标区</b>的写法。
     * 「市内六区」「环城四区」这类<b>区组</b>统称在旧库的区级口径数据里出现过，
     * 但它们对应多个区、无法自动归位，因此<b>刻意不放进别名表</b> ——
     * 放进去等于猜，猜错就把配套挂到别的区去了。
     */
    private static Map<String, String> xzqhAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String option : XZQH_OPTIONS) {
            map.put(option, option);
            String shortName = option.endsWith("区") ? option.substring(0, option.length() - 1) : option;
            map.put(shortName, option);
        }
        return map;
    }

    /**
     * 配套设施类别的同义异写 → 标准类别。
     *
     * <p>★ 只收「能唯一定位」的写法（实测旧库 7 种字典外写法，见 _db_schema_report 4.2）：
     * <ul>
     *   <li>{@code 供气} → 燃气：字典与录入页的用词不一致，语义完全相同；</li>
     *   <li>{@code 给水管线 / 自来水 / 给水工程} → 供水；{@code 排水工程 / 排水管线} → 排水；</li>
     *   <li>{@code 市政道路} → 道路。</li>
     * </ul>
     * <b>刻意不归位</b>：{@code 道路及管线}、{@code 供水、排水} 这类<b>跨两类</b>的写法
     * （一条配套同时含道路与管线时，归到哪一类都会让另一类的统计少一条）。
     * 这类值会走 ENUM 校验被拦下，用户自己拆成两条即可 ——
     * 猜错类别会让「按类别汇总配套费」直接算错，代价比让用户改一行大得多。
     */
    private static Map<String, String> ptsslbAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String option : PTSSLB_OPTIONS) {
            map.put(option, option);
        }
        map.put("供气", "燃气");
        map.put("给水管线", "供水");
        map.put("自来水", "供水");
        map.put("给水工程", "供水");
        map.put("排水工程", "排水");
        map.put("排水管线", "排水");
        map.put("市政道路", "道路");
        return map;
    }

    /**
     * 道路等级的异写 → 标准等级。
     *
     * <p>旧库把「路」写成「道」的情况很普遍（主干道 / 次干道 / 支路），
     * 三个标准级各收一批等价写法；「快速路」不归位（见 {@link #DLDJ_OPTIONS}）。
     */
    private static Map<String, String> dldjAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String option : DLDJ_OPTIONS) {
            map.put(option, option);
        }
        map.put("主干路", "城市主干路");
        map.put("主干道", "城市主干路");
        map.put("城市主干道", "城市主干路");
        map.put("次干路", "城市次干路");
        map.put("次干道", "城市次干路");
        map.put("城市次干道", "城市次干路");
        map.put("支路", "城市支路");
        return map;
    }

    /**
     * 资金来源的异写 → 标准来源。
     *
     * <p>实测旧库把「区级财政资金 / 区财政 / 区级资金 / 区财政统筹」四种写法
     * 表达同一件事（都是「区里统筹」），统一归到「区内统筹」；
     * {@code 土地成本} 与 {@code 土地整理成本} 同类，也归位。
     *
     * <p><b>刻意不归位</b>：{@code 自筹 / 企业自筹 / 自筹及其它 / 自筹及银行贷款} ——
     * 这一族里「自筹」本身不说明钱的来源口径，且「自筹及其它」含两种来源，
     * 归到「其它」会把可区分的信息抹掉。
     */
    private static Map<String, String> zjlyAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String option : ZJLY_OPTIONS) {
            map.put(option, option);
        }
        map.put("土地成本", "土地整理成本");
        map.put("区级财政资金", "区内统筹");
        map.put("区财政统筹", "区内统筹");
        map.put("区级资金", "区内统筹");
        map.put("区财政", "区内统筹");
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
        Map<String, String> ptsslb = ptsslbAliases();
        Map<String, String> dldj = dldjAliases();
        Map<String, String> zjly = zjlyAliases();

        List<FieldSpec> list = new ArrayList<>();

        // ---- 标识（前两个是配套的业务键，第三个是宗地名称冗余）----
        list.add(new FieldSpec("crzdbh", "出让宗地编号", FieldType.TEXT, true, true,
                null, null, 100, "津西青(挂)2024-01号"));
        list.add(new FieldSpec("ptxmmc", "配套项目名称", FieldType.TEXT, true, true,
                null, null, 100, "侯台片区规划路一道路工程"));
        list.add(new FieldSpec("dkmc", "地块名称", FieldType.TEXT, false, true,
                null, null, 255, "侯台片区地块一"));

        // ---- 属性 ----
        list.add(new FieldSpec("ptsslb", "配套设施类别", FieldType.ENUM, false, true,
                PTSSLB_OPTIONS, ptsslb, 100, "道路"));
        list.add(new FieldSpec("xzqh", "行政区划", FieldType.ENUM, false, true,
                XZQH_OPTIONS, xzqh, 100, "西青区"));
        list.add(new FieldSpec("xmfl", "项目分类", FieldType.ENUM, false, true,
                XMFL_OPTIONS, null, 100, "区级项目"));
        list.add(new FieldSpec("jsxx", "建设性质", FieldType.ENUM, false, true,
                JSXX_OPTIONS, null, 100, "新建"));
        list.add(new FieldSpec("dldj", "道路等级", FieldType.ENUM, false, true,
                DLDJ_OPTIONS, dldj, 100, "城市次干路"));
        list.add(new FieldSpec("ghhxkd", "规划红线宽度（米）", FieldType.NUMBER, false, true,
                null, null, 0, "30.00"));
        list.add(new FieldSpec("cd", "长度（米）", FieldType.NUMBER, false, true,
                null, null, 0, "860.50"));
        list.add(new FieldSpec("tzgs", "投资估算（万元）", FieldType.NUMBER, false, true,
                null, null, 0, "3250.00"));
        list.add(new FieldSpec("zjly", "资金来源", FieldType.ENUM, false, true,
                ZJLY_OPTIONS, zjly, 100, "土地整理成本"));
        list.add(new FieldSpec("dkcrscndptjgsj", "地块出让时承诺的配套竣工时间", FieldType.DATE, false, true,
                null, null, 0, "2026-12-31"));

        // ---- 提级论证（新模块的挂钩点：留在这里是为了历史数据可导入）----
        list.add(new FieldSpec("sfzsjtjlz", "是否涉及提级论证", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("tjlzsftg", "提级论证是否通过", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));

        // ---- 参建单位 ----
        list.add(new FieldSpec("jsdw", "建设单位", FieldType.TEXT, false, true,
                null, null, 100, "天津市某某建设发展有限公司"));
        list.add(new FieldSpec("sjdw", "设计单位", FieldType.TEXT, false, true,
                null, null, 100, "天津市某某市政设计院"));
        list.add(new FieldSpec("kcdw", "勘察单位", FieldType.TEXT, false, true,
                null, null, 100, "天津市某某勘察院"));
        list.add(new FieldSpec("jldw", "监理单位", FieldType.TEXT, false, true,
                null, null, 100, "天津市某某工程监理有限公司"));
        list.add(new FieldSpec("sgdw", "施工单位", FieldType.TEXT, false, true,
                null, null, 100, "天津市某某市政工程有限公司"));
        list.add(new FieldSpec("jsgydw", "接收管养单位", FieldType.TEXT, false, true,
                null, null, 100, "天津市某某区城市管理委员会"));

        // ---- 前期审批（是否完成 / 状态 / 金额 / 资金落实）----
        list.add(new FieldSpec("xjpfsfwc", "项建批复是否完成", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "是"));
        list.add(new FieldSpec("xjpfzt", "项建批复状态", FieldType.ENUM, false, true,
                PROGRESS_STATUS_OPTIONS, null, 100, "正常推进"));
        list.add(new FieldSpec("kypfsfwc", "可研批复是否完成", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "是"));
        list.add(new FieldSpec("kypfzt", "可研批复状态", FieldType.ENUM, false, true,
                PROGRESS_STATUS_OPTIONS, null, 100, "正常推进"));
        list.add(new FieldSpec("csjgspfsfwc", "初设及概算批复是否完成", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("csjgspfzt", "初设及概算批复状态", FieldType.ENUM, false, true,
                PROGRESS_STATUS_OPTIONS, null, 100, "有问题"));
        list.add(new FieldSpec("gspfje", "概算批复金额（万元）", FieldType.NUMBER, false, true,
                null, null, 0, "3180.00"));
        list.add(new FieldSpec("zjlsqk", "资金落实情况", FieldType.ENUM, false, true,
                ZJLSQK_OPTIONS, null, 100, "已落实"));

        // ---- 进度 ----
        list.add(new FieldSpec("sfkg", "是否开工", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("kgzt", "开工状态", FieldType.ENUM, false, true,
                PROGRESS_STATUS_OPTIONS, null, 100, "正常推进"));
        list.add(new FieldSpec("yjkgsj", "预计开工时间", FieldType.DATE, false, true,
                null, null, 0, "2025-03-01"));
        list.add(new FieldSpec("sjkgsj", "实际开工时间", FieldType.DATE, false, true,
                null, null, 0, "2025-03-18"));
        list.add(new FieldSpec("sfjg", "是否竣工", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("yjjgsj", "预计竣工时间", FieldType.DATE, false, true,
                null, null, 0, "2026-06-30"));
        list.add(new FieldSpec("sjjgsj", "实际竣工时间", FieldType.DATE, false, true,
                null, null, 0, "2026-06-20"));
        list.add(new FieldSpec("sfyj", "是否移交", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("ptxmhdydydjdc", "配套项目核定用地与地籍调查", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "是"));

        // ---- 档案要件标志位（★ 历史数据只读，新数据请走统一附件模块）----
        list.add(new FieldSpec("xjpfwj", "项建批复文件", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "是"));
        list.add(new FieldSpec("kypfwj", "可研批复文件", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "是"));
        list.add(new FieldSpec("csjgspfwj", "初设及概算批复文件", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("dlgh", "道路规划", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "是"));
        list.add(new FieldSpec("ghgcxk", "规划工程许可", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("gxzhslsj", "管线综合矢量数据(shp)", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("zyptfa", "专业配套方案", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "是"));
        list.add(new FieldSpec("zyglyj", "专业管理意见", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("ghydxkyhbsxbl", "规划用地许可与划拨手续办理", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("sgxk", "施工许可", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));
        list.add(new FieldSpec("bdcdj", "不动产登记", FieldType.YES_NO, false, true,
                YES_NO_OPTIONS, yesNo, 100, "否"));

        // ---- 说明 ----
        list.add(new FieldSpec("jtwt", "具体问题", FieldType.TEXT, false, true,
                null, null, 2500, "北侧燃气管道未迁改，影响路基施工"));
        list.add(new FieldSpec("gzjy", "工作建议", FieldType.TEXT, false, true,
                null, null, 100, "建议协调燃气公司提前迁改"));
        list.add(new FieldSpec("zlqsnrjsm", "资料缺失内容及说明", FieldType.TEXT, false, true,
                null, null, 2500, "缺少施工许可扫描件"));
        list.add(new FieldSpec("bz", "备注", FieldType.TEXT, false, true,
                null, null, 100, ""));

        // ---- 录入信息（旧系统要求批量导入时必填后三项）----
        list.add(new FieldSpec("lrdw", "录入单位", FieldType.TEXT, true, true,
                null, null, 100, "天津市土地利用事务中心"));
        list.add(new FieldSpec("lrr", "录入人", FieldType.TEXT, true, true,
                null, null, 100, "张三"));
        list.add(new FieldSpec("lxdh", "联系电话", FieldType.TEXT, true, true,
                null, null, 100, "13812345678"));

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

    /** 业务键列名：出让宗地编号 */
    public static final String COL_CRZDBH = "crzdbh";

    /** 业务键列名：配套项目名称 */
    public static final String COL_PTXMMC = "ptxmmc";

    /** 全部可导入字段，顺序即回执列顺序 */
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
