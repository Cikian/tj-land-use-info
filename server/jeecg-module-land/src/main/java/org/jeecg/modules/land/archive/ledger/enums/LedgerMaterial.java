package org.jeecg.modules.land.archive.ledger.enums;

import org.apache.commons.lang.StringUtils;
import org.jeecg.modules.land.archive.ledger.entity.RoadAcceptanceLedger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @Description: 道路设施验收及移交资料台账-13 类资料清单（方案 2.3.2 第 7 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>台账的核心是「是否有某份资料」的勾选矩阵。13 类资料的来源分两段：
 * <ol>
 *   <li><b>前 6 类</b>沿用《升级改造工作内容清单》§6.2.6 原文列名：
 *       施工许可证 / 竣工验收报告 / 竣工图测 / 质量监督报告 / 材料检验报告 / 安全监督报告；</li>
 *   <li><b>后 7 类</b>按需求方 2026-09 确认，对齐旧系统「配套附件 14 类目录」里的
 *       验收与移交部分（实测目录见旧代码
 *       {@code xjKjkfbSupportingFacilitiesController.java:650}：
 *       01-配套项目核定用地与地籍调查 … 11-施工许可、12-不动产登记、
 *       <b>13-竣工文件、14-移交文件</b>）。</li>
 * </ol>
 *
 * <p><b>★ 三处必须保持一致</b>（改一处就要同步改另外两处，这是本模块唯一的「同步契约」）：
 * <pre>
 *   1. 数据库列        sql/ledger/01_t_road_acceptance_ledger.sql 的 has_* 列
 *   2. 后端本枚举      LedgerMaterial.column / property / label
 *   3. 前端列定义      admin-client/src/api/land/ledger.js 的 LEDGER_MATERIALS
 * </pre>
 * 契约断了会在运行期暴露为「列取不到值」或「表头与数据错位」，而不是编译期报错。
 * 建表脚本末尾有一段守卫查询（{@code COLUMN_NAME LIKE 'has\_%'}）用来核对列数是否为 13；
 * 后端启动与前端页面加载时不另做校验，避免为一个静态契约引入运行时依赖。
 *
 * <p><b>为什么用枚举而不是常量数组</b>：勾选矩阵需要「列名 → 取值」的反射式映射
 * （{@link #countMaterials} 统计归集数、导出服务按序输出 13 列、查询条件按资料筛选），
 * 用带 {@link Function} 访问器的枚举可以把「列名 / 驼峰属性名 / 中文名 / 取值方式」
 * 绑在一处，避免到处写 if-else。
 */
public enum LedgerMaterial {

    /** 资料01 施工许可证（旧配套表 sgxk） */
    SGXK(1, "has_sgxk", "hasSgxk", "施工许可证",
            "旧配套项目表的 sgxk='是'（对应旧附件目录 11-施工许可）", RoadAcceptanceLedger::getHasSgxk),

    /** 资料02 竣工验收报告 */
    YSBG(2, "has_ysbg", "hasYsbg", "竣工验收报告",
            "旧配套表 sfjg='是' 推断（已竣工 ⇒ 必有竣工验收报告）", RoadAcceptanceLedger::getHasYsbg),

    /** 资料03 竣工图测（竣工图） */
    JGTC(3, "has_jgtc", "hasJgtc", "竣工图测",
            "旧配套表 sjjgsj 非空推断（有实际竣工日期 ⇒ 应有竣工图测）", RoadAcceptanceLedger::getHasJgtc),

    /** 资料04 质量监督报告 */
    ZLJDBG(4, "has_zljdbg", "hasZljdbg", "质量监督报告",
            "旧库无对应标志，待中心按实际资料补录", RoadAcceptanceLedger::getHasZljdbg),

    /** 资料05 材料检验报告 */
    CLJYBG(5, "has_cljybg", "hasCljybg", "材料检验报告",
            "旧库无对应标志，待中心按实际资料补录", RoadAcceptanceLedger::getHasCljybg),

    /** 资料06 安全监督报告 */
    AJBG(6, "has_ajbg", "hasAjbg", "安全监督报告",
            "旧库无对应标志，待中心按实际资料补录", RoadAcceptanceLedger::getHasAjbg),

    /** 资料07 规划验收合格证 */
    GHYSHGZ(7, "has_ghyshgz", "hasGhyshgz", "规划验收合格证",
            "旧流程「建设工程规划验收合格证核发」环节产物；旧库无标志，待补录", RoadAcceptanceLedger::getHasGhyshgz),

    /** 资料08 竣工验收备案表 */
    JGBABA(8, "has_jgbaba", "hasJgbaba", "竣工验收备案表",
            "旧流程「建设工程竣工验收备案」环节产物；旧库无标志，待补录", RoadAcceptanceLedger::getHasJgbaba),

    /** 资料09 档案专项验收意见 */
    DAZXYS(9, "has_dazxys", "hasDazxys", "档案专项验收意见",
            "旧流程「建设项目档案专项验收」环节产物；旧库无标志，待补录", RoadAcceptanceLedger::getHasDazxys),

    /** 资料10 道路工程移交单 */
    DLYJD(10, "has_dlyjd", "hasDlyjd", "道路工程移交单",
            "旧流程「道路工程移交」环节产物；旧库无标志，待补录", RoadAcceptanceLedger::getHasDlyjd),

    /** 资料11 养护协议 */
    YHXY(11, "has_yhxy", "hasYhxy", "养护协议",
            "方案 2.3.2 第 6 项（道路交付及养护协议移交）的产物；旧库无标志，待补录", RoadAcceptanceLedger::getHasYhxy),

    /** 资料12 竣工文件（旧附件目录 13-竣工文件） */
    JGWJ(12, "has_jgwj", "hasJgwj", "竣工文件",
            "旧配套表 jgwj='是'（对应旧附件目录 13-竣工文件）", RoadAcceptanceLedger::getHasJgwj),

    /** 资料13 移交文件（旧附件目录 14-移交文件） */
    YJWJ(13, "has_yjwj", "hasYjwj", "移交文件",
            "旧配套表 yjwj='是'（对应旧附件目录 14-移交文件）", RoadAcceptanceLedger::getHasYjwj);

    /** 资料序号（1~13，台账表头与导出列序都用它） */
    private final int seq;

    /** 数据库列名（has_sgxk …），查询条件的「缺少某类资料」筛选要用它 */
    private final String column;

    /** 驼峰属性名（hasSgxk …），与前端 LEDGER_MATERIALS 的 key 对齐 */
    private final String property;

    /** 资料中文名（台账表头、导出表头用） */
    private final String label;

    /** 迁移来源说明（写进实现文档与前端 tooltip） */
    private final String source;

    /** 取该资料勾选值的访问器（0/1；null 视为未勾选） */
    private final Function<RoadAcceptanceLedger, Integer> accessor;

    LedgerMaterial(int seq, String column, String property, String label, String source,
                   Function<RoadAcceptanceLedger, Integer> accessor) {
        this.seq = seq;
        this.column = column;
        this.property = property;
        this.label = label;
        this.source = source;
        this.accessor = accessor;
    }

    public int getSeq() {
        return seq;
    }

    public String getColumn() {
        return column;
    }

    public String getProperty() {
        return property;
    }

    public String getLabel() {
        return label;
    }

    public String getSource() {
        return source;
    }

    /** 该资料在给定台账上的勾选值（0 / 1） */
    public int valueOf(RoadAcceptanceLedger ledger) {
        if (ledger == null) {
            return 0;
        }
        Integer flag = accessor.apply(ledger);
        return flag != null && flag == 1 ? 1 : 0;
    }

    /**
     * 统计一条台账已归集的资料份数（0~13）。
     *
     * <p>口径与迁移脚本里 {@code material_count} 的表达式、以及页面上
     * 「资料 3/13」的显示完全一致 —— 只有这一处实现，避免三处各算一遍算错。
     */
    public static int countMaterials(RoadAcceptanceLedger ledger) {
        if (ledger == null) {
            return 0;
        }
        int count = 0;
        for (LedgerMaterial material : values()) {
            if (material.valueOf(ledger) == 1) {
                count++;
            }
        }
        return count;
    }

    /** 全部 13 类资料（按 seq 升序，即声明顺序） */
    public static List<LedgerMaterial> ordered() {
        return Collections.unmodifiableList(new ArrayList<>(java.util.Arrays.asList(values())));
    }

    /**
     * 按驼峰属性名取枚举（查询条件用；取不到返回 null）。
     *
     * <p>★ 这个方法同时承担<b>SQL 注入防线</b>：查询条件里的「缺少某类资料」是
     * 前端传入的字符串，只有先经这里白名单化，才能把 {@link #getColumn()} 拼进 SQL。
     * 调用方不得直接把请求参数当列名用。
     */
    public static LedgerMaterial ofProperty(String property) {
        if (StringUtils.isBlank(property)) {
            return null;
        }
        String trimmed = property.trim();
        for (LedgerMaterial material : values()) {
            if (material.property.equalsIgnoreCase(trimmed)) {
                return material;
            }
        }
        return null;
    }

    /** 按数据库列名取枚举（取不到返回 null） */
    public static LedgerMaterial ofColumn(String column) {
        if (StringUtils.isBlank(column)) {
            return null;
        }
        String trimmed = column.trim();
        for (LedgerMaterial material : values()) {
            if (material.column.equalsIgnoreCase(trimmed)) {
                return material;
            }
        }
        return null;
    }
}
