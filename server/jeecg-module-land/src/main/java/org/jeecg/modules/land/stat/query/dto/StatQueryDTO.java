package org.jeecg.modules.land.stat.query.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Description: 查询统计 · 查询条件（经营性用地 / 配套项目共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>字段与旧系统面板逐个对应（高保真 `查询统计.html` 的条件控件也是这套）：
 * <pre>
 *   经营性用地：地块名称 / 行政区划 / 出让时间 / 建设单位(受让人) / 项目分类
 *   配套项目  ：配套项目名称 / 配套设施类别 / 地块名称 / 行政区划 / 项目分类 /
 *               建设性质 / 是否开工 / 是否竣工 / 是否移交 / 是否涉及提级论证 /
 *               提级论证是否通过 / 项建批复 / 可研批复 / 初设及概算批复
 * </pre>
 *
 * <p><b>★ 多值条件为什么是 List 而不是逗号串</b>：
 * 旧系统的 {@code getCnd()} 用 {@code split(",")} 把「甲,乙」拼成 {@code dkmc LIKE '%甲%' OR dkmc LIKE '%乙%'}。
 * 语义上确实是多选，所以这里直接用 List 表达，由 Controller 接住
 * （GET 查询串 {@code dkmc=甲,乙} 与 POST 的 JSON 数组 Spring 都能绑成 List）。
 *
 * <p><b>★ 为什么不复刻旧的 {@code textSql}</b>：
 * 旧前端把「字段 运算符 值 关系符」拼成 SQL 片段交给后端原样执行。
 * 新系统只接受 {@link AdvancedCondition} 结构化条件，字段名走
 * {@code QueryFieldRegistry} 白名单、值走 MyBatis 参数绑定。
 */
@Data
public class StatQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据集：land（经营性用地）/ facility（配套项目）；缺省 land */
    private String dataType;

    // ==================================================================
    // 一、经营性用地条件
    // ==================================================================

    /** 地块名称（多值，OR + 模糊） */
    private List<String> dkmc;

    /** 出让时间（多值，年份：2025 / 2024 …） */
    private List<String> crsjYears;

    /** 受让人 / 建设单位（多值，OR + 模糊） */
    private List<String> srr;

    // ==================================================================
    // 二、两侧共用条件
    // ==================================================================

    /** 行政区划（多值，精确）。★ 用 xzqh，不沿用旧系统的 xzqh2（见差异清单 D-604 / D-609） */
    private List<String> xzqh;

    /** 项目分类：市级项目 / 区级项目；空=全部 */
    private String xmfl;

    // ==================================================================
    // 三、配套项目专用条件
    // ==================================================================

    /** 配套项目名称（多值，OR + 模糊） */
    private List<String> ptxmmc;

    /** 配套设施类别（多值，精确）：道路 / 排水 / 供水 … */
    private List<String> ptsslb;

    /** 建设性质：新建 / 现状 / 改扩建 … */
    private String jsxx;

    /** 是否开工（是 / 否） */
    private String sfkg;

    /** 是否竣工（是 / 否） */
    private String sfjg;

    /** 是否移交（是 / 否） */
    private String sfyj;

    /** 是否涉及提级论证（是 / 否） */
    private String sfzsjtjlz;

    /** 提级论证是否通过（是 / 否） */
    private String tjlzsftg;

    /** 项建批复是否完成（是 / 否 / 不涉及） */
    private String xjpfsfwc;

    /** 可研批复是否完成（是 / 否 / 不涉及） */
    private String kypfsfwc;

    /** 初设及概算批复是否完成（是 / 否 / 不涉及） */
    private String csjgspfwc;

    /** 资金落实情况（已落实 / 未落实 …） */
    private String zjlsqk;

    // ==================================================================
    // 四、高级查询条件（自由组合，替换旧系统的 textSql）
    // ==================================================================

    /** 结构化条件列表（POST 用） */
    private List<AdvancedCondition> conditions;

    /**
     * 结构化条件的 JSON 字符串（GET 导出用）。
     * Excel 导出走 {@code window.open}，只能是 GET，所以条件要能塞进查询串。
     */
    private String conditionsJson;

    // ==================================================================
    // 五、列与分页
    // ==================================================================

    /** 自定义展示列（字段 key 列表）；空=默认列 */
    private List<String> columns;

    /** 分页页码（从 1 开始） */
    private Integer pageNo = 1;

    /** 每页条数 */
    private Integer pageSize = 20;
}
