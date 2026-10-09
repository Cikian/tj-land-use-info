package org.jeecg.modules.land.data.fieldconfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 经营性用地 / 配套项目 字段中文名字典（变更留痕与表单分组共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 为什么需要它</b>：变更留痕要显示「出让金（亿元） 1.20 → 2.00」，
 * 而不是「crj 1.20 → 2.00」。而 Java 反射拿不到业务中文名 ——
 * 实体上没有这类注解，Lombok 生成的 getter 也不带注释。
 *
 * <p>所以中文名在这里显式登记一份。<b>这些名字本来就要写在前端表单上</b>，
 * 服务层再写一遍不额外增加维护面，却换来「履历里显示的是人看得懂的名字」。
 *
 * <p>字段名与 {@code t_land} / {@code t_supporting_facilities} 的列名
 * （即批量导入模板的英文字段名）完全一致，三处共用同一套命名，
 * 排查数据问题时不需要做二次翻译。
 */
public final class DataFieldLabels {

    private DataFieldLabels() {
    }

    /** 经营性用地（宗地）字段中文名 */
    private static final Map<String, String> LAND_LABELS;

    /** 配套项目字段中文名 */
    private static final Map<String, String> FACILITY_LABELS;

    /** 环节进度字段中文名 */
    private static final Map<String, String> PROCESS_LABELS;

    static {
        Map<String, String> land = new LinkedHashMap<>();
        // 标识与分类
        land.put("crzdbh", "出让宗地编号");
        land.put("dkmc", "地块名称");
        land.put("xzqh", "行政区划");
        land.put("xmfl", "项目分类");
        land.put("ghydxz", "规划用地性质");
        // 出让与面积
        land.put("crj", "出让金（亿元）");
        land.put("crsj", "出让时间");
        land.put("kjsydmj", "可建设用地面积(平方米)");
        land.put("zydmj", "总用地面积（平方米）");
        land.put("jsmj", "建设面积（平方米）");
        land.put("nrcbdptf", "纳入成本的配套费(万元)");
        land.put("wcd", "完成度");
        // 受让人与交付
        land.put("srr", "受让人");
        land.put("htydjfsj", "合同约定交付时间");
        land.put("lpmc", "楼盘名称");
        land.put("lpjfsj", "楼盘交付时间（或计划交付时间）");
        // 四至
        land.put("dz", "东至");
        land.put("xz", "西至");
        land.put("nz", "南至");
        land.put("bz", "北至");
        // 土地整理
        land.put("tdzldw", "土地整理单位");
        land.put("tdzljhxdwjh", "土地整理计划下达文件号");
        land.put("tdzljh", "土地整理计划");
        // 配套与图形
        land.put("ptsfqq", "配套是否齐全");
        land.put("ptqkh", "配套情况函");
        land.put("ptcbh", "配套筹备函");
        land.put("crzdtxsj", "出让宗地图形数据（shp）");
        land.put("ptjsnr", "配套建设内容");
        // 录入
        land.put("lrdw", "录入单位");
        land.put("lrr", "录入人");
        land.put("lxdh", "联系电话");
        land.put("zlqsnrsm", "资料缺失内容及说明");
        land.put("beizhu", "备注");
        land.put("xzqh2", "录入单位简称");
        LAND_LABELS = Collections.unmodifiableMap(land);

        Map<String, String> facility = new LinkedHashMap<>();
        facility.put("crzdbh", "出让宗地编号");
        facility.put("ptxmmc", "配套项目名称");
        facility.put("dkmc", "地块名称");
        facility.put("ptsslb", "配套设施类别");
        facility.put("xzqh", "行政区划");
        facility.put("xmfl", "项目分类");
        facility.put("jsxx", "建设性质");
        facility.put("dldj", "道路等级");
        facility.put("sfzsjtjlz", "是否涉及提级论证");
        facility.put("tjlzsftg", "提级论证是否通过");
        facility.put("ghhxkd", "规划红线宽度（米）");
        facility.put("cd", "长度（米）");
        facility.put("tzgs", "投资估算（万元）");
        facility.put("zjly", "资金来源");
        facility.put("dkcrscndptjgsj", "地块出让时承诺的配套竣工时间");
        facility.put("jsdw", "建设单位");
        facility.put("sjdw", "设计单位");
        facility.put("kcdw", "勘察单位");
        facility.put("jldw", "监理单位");
        facility.put("sgdw", "施工单位");
        facility.put("jsgydw", "接收管养单位");
        facility.put("xjpfsfwc", "项建批复是否完成");
        facility.put("xjpfzt", "项建批复状态");
        facility.put("kypfsfwc", "可研批复是否完成");
        facility.put("kypfzt", "可研批复状态");
        facility.put("csjgspfsfwc", "初设及概算批复是否完成");
        facility.put("csjgspfzt", "初设及概算批复状态");
        facility.put("gspfje", "概算批复金额（万元）");
        facility.put("zjlsqk", "资金落实情况");
        facility.put("sfkg", "是否开工");
        facility.put("kgzt", "开工状态");
        facility.put("yjkgsj", "预计开工时间");
        facility.put("sjkgsj", "实际开工时间");
        facility.put("sfjg", "是否竣工");
        facility.put("yjjgsj", "预计竣工时间");
        facility.put("sjjgsj", "实际竣工时间");
        facility.put("sfyj", "是否移交");
        facility.put("ptxmhdydydjdc", "配套项目核定用地与地籍调查");
        facility.put("xjpfwj", "项建批复文件");
        facility.put("kypfwj", "可研批复文件");
        facility.put("csjgspfwj", "初设及概算批复文件");
        facility.put("dlgh", "道路规划");
        facility.put("ghgcxk", "规划工程许可");
        facility.put("gxzhslsj", "管线综合矢量数据（shp）");
        facility.put("zyptfa", "专业配套方案");
        facility.put("zyglyj", "专业管理意见");
        facility.put("ghydxkyhbsxbl", "规划用地许可与划拨手续办理");
        facility.put("sgxk", "施工许可");
        facility.put("bdcdj", "不动产登记");
        facility.put("jgwj", "竣工文件");
        facility.put("yjwj", "移交文件");
        facility.put("jtwt", "具体问题");
        facility.put("gzjy", "工作建议");
        facility.put("zlqsnrjsm", "资料缺失内容及说明");
        facility.put("bz", "备注");
        facility.put("lrdw", "录入单位");
        facility.put("lrr", "录入人");
        facility.put("lxdh", "联系电话");
        FACILITY_LABELS = Collections.unmodifiableMap(facility);

        Map<String, String> process = new LinkedHashMap<>();
        process.put("lcqk", "环节情况");
        process.put("lckssj", "环节开始时间");
        process.put("yjjssj", "预计结束时间");
        process.put("lcjssj", "环节结束时间");
        process.put("czwtlx", "存在问题类型");
        process.put("jtwt", "具体问题");
        process.put("gzjy", "工作建议");
        process.put("lrdw", "录入单位");
        process.put("lrr", "录入人");
        process.put("lxdh", "联系电话");
        PROCESS_LABELS = Collections.unmodifiableMap(process);
    }

    /** 宗地字段中文名 */
    public static Map<String, String> landLabels() {
        return LAND_LABELS;
    }

    /** 配套项目字段中文名 */
    public static Map<String, String> facilityLabels() {
        return FACILITY_LABELS;
    }

    /** 环节进度字段中文名 */
    public static Map<String, String> processLabels() {
        return PROCESS_LABELS;
    }

    /**
     * 取中文字段名，未登记时回退为字段名本身。
     *
     * <p>回退而不是抛异常：将来加了字段但忘了登记，履历里会显示英文列名
     * （可读性差一点），而不是整条履历写不进去（功能坏掉）。
     */
    public static String label(Map<String, String> labels, String field) {
        if (field == null) {
            return "";
        }
        String hit = labels == null ? null : labels.get(field);
        return hit == null ? field : hit;
    }

    /** 已登记的宗地字段名集合（测试用来核对「实体字段是否都登记了」） */
    public static Set<String> landFields() {
        return LAND_LABELS.keySet();
    }

    /** 已登记的配套字段名集合 */
    public static Set<String> facilityFields() {
        return FACILITY_LABELS.keySet();
    }
}
