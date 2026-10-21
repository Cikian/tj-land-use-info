package org.jeecg.modules.land.stat.query.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.support.DataSupport;
import org.jeecg.modules.land.stat.query.dto.AdvancedCondition;
import org.jeecg.modules.land.stat.query.dto.ResolvedCondition;
import org.jeecg.modules.land.stat.query.dto.StatQueryDTO;
import org.jeecg.modules.land.stat.query.mapper.StatQueryMapper;
import org.jeecg.modules.land.stat.query.service.IStatQueryService;
import org.jeecg.modules.land.stat.query.support.QueryFieldRegistry;
import org.jeecg.modules.land.stat.query.support.QueryFieldRegistry.DataSet;
import org.jeecg.modules.land.stat.query.support.QueryFieldRegistry.Field;
import org.jeecg.modules.land.stat.query.support.QueryFieldRegistry.Operator;
import org.jeecg.modules.land.stat.query.vo.QueryColumnVO;
import org.jeecg.modules.land.stat.query.vo.QueryPageVO;
import org.jeecg.modules.land.stat.query.vo.StatSummaryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 查询统计 · Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>本类只做三件事：<b>条件归一</b> → <b>调 Mapper</b> → <b>按口径算指标</b>。
 * 所有 SQL 在 {@code StatQueryMapper.xml}，所有字段定义在 {@link QueryFieldRegistry}。
 *
 * <p><b>★ 口径的来源与三处有意修正</b>（对应差异清单 D-609 / D-610 / D-611）
 * <ol>
 *   <li>行政区划筛选用 {@code xzqh}，不用旧系统的 {@code xzqh2}
 *       —— xzqh2 实际是「录入单位简称」，拿它当行政区划会让区级项目筛出
 *       「土地发展中心」「高新区」这类行（D-604 同源问题）；</li>
 *   <li>分母为 0 时比率返回 0.0，不再输出 {@code NaN%} / 整数除法截断；</li>
 *   <li>「区级配套费误用市级条件」不复存在 —— 新实现按当前筛选条件一次算完，
 *       不再有「市级/区级两套并行计算」的复制粘贴结构。</li>
 * </ol>
 *
 * <p><b>配套去重与漏斗为什么留在 Java</b>：见 {@code StatQueryMapper.selectLandFacilityFacts}
 * 的注释 —— 与旧 {@code getSelectInfo()} 逐行对齐，比在 SQL 里写嵌套 CASE 更容易核对。
 */
@Slf4j
@Service
public class StatQueryServiceImpl implements IStatQueryService {

    /** 分页上限：一次最多 500 条，防止前端传 pageSize=100000 把内存打满 */
    private static final int MAX_PAGE_SIZE = 500;

    /** 比率保留 1 位小数（旧系统用 int 截断，如 19% ；新系统给 19.7%，见实现说明） */
    private static final int RATE_SCALE = 1;

    /** 配套费总额保留 3 位小数（与旧 getSelectInfo 的 DecimalFormat("#.000") 一致） */
    private static final int FEE_SCALE = 3;

    /** 下拉候选值上限 */
    private static final int OPTION_LIMIT = 500;

    /** 明细行「名称类」字段：① 做长度截断，② 供前端表头排序稳定性 */
    private static final List<String> NAME_KEYS =
            Collections.unmodifiableList(Arrays.asList("dkmc", "srr", "ptxmmc", "lpmc", "tdzldw"));

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** conditionsJson 解析用（GET 导出把条件塞进查询串） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private StatQueryMapper statQueryMapper;

    // ==================================================================
    // 一、字段元数据
    // ==================================================================

    @Override
    public List<QueryColumnVO> queryColumns(String dataType) {
        List<QueryColumnVO> result = new ArrayList<>();
        for (Field field : QueryFieldRegistry.fields(DataSet.of(dataType))) {
            QueryColumnVO vo = new QueryColumnVO();
            vo.setKey(field.getKey());
            vo.setLabel(field.getLabel());
            vo.setType(field.getType().name());
            vo.setUnit(field.getUnit());
            vo.setDefaultColumn(field.isDefaultColumn());
            List<String> ops = new ArrayList<>();
            for (Operator op : field.getOps()) {
                ops.add(op == Operator.LIKE ? "like" : op.sql());
            }
            vo.setOperators(ops);
            result.add(vo);
        }
        return result;
    }

    // ==================================================================
    // 二、分页明细
    // ==================================================================

    @Override
    public QueryPageVO queryPage(StatQueryDTO query) {
        StatQueryDTO q = prepare(query);
        DataSet dataSet = DataSet.of(q.getDataType());
        List<ResolvedCondition> conds = resolveConditions(q, dataSet);

        int pageNo = q.getPageNo() == null || q.getPageNo() < 1 ? 1 : q.getPageNo();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1
                ? 20 : Math.min(q.getPageSize(), MAX_PAGE_SIZE);
        int offset = (pageNo - 1) * pageSize;

        QueryPageVO result = new QueryPageVO();
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);

        List<String> keys = resolveColumnKeys(q, dataSet);
        List<QueryColumnVO> columns = new ArrayList<>();
        for (String key : keys) {
            Field field = QueryFieldRegistry.find(dataSet, key);
            if (field != null) {
                columns.add(toColumnVO(field));
            }
        }
        result.setColumns(columns);

        if (dataSet == DataSet.FACILITY) {
            result.setTotal(statQueryMapper.countFacility(q, conds));
            result.setRows(project(statQueryMapper.selectFacilityRows(q, conds, offset, pageSize),
                    dataSet, keys));
        } else {
            result.setTotal(statQueryMapper.countLand(q, conds));
            result.setRows(project(statQueryMapper.selectLandRows(q, conds, offset, pageSize),
                    dataSet, keys));
        }
        return result;
    }

    // ==================================================================
    // 三、分析结果指标
    // ==================================================================

    @Override
    public StatSummaryVO querySummary(StatQueryDTO query) {
        StatQueryDTO q = prepare(query);
        DataSet dataSet = DataSet.of(q.getDataType());
        List<ResolvedCondition> conds = resolveConditions(q, dataSet);
        return dataSet == DataSet.FACILITY
                ? facilitySummary(q, conds)
                : landSummary(q, conds);
    }

    /**
     * 经营性用地口径：命中宗地 + 其下配套（按 ptxmmc 去重 + 环节漏斗）。
     *
     * <p>逐行对照旧 {@code xjKjkfbCommercialLandController.getSelectInfo(Cnd)}：
     * <pre>
     *   dkzs  = 命中宗地数
     *   ptfze = Σ nrcbdptf（3 位小数）
     *   ptzs  = 去重(按 ptxmmc) 且 排除 建设性质∈{现状,不涉及}/名称为空 且 名称不含「不涉及」
     *   xjdl  = ptzs 里 配套设施类别=道路 且 建设性质=新建
     *   ylx   = 去重集里「非现状 且 项建批复≠否」中 项建批复∈{是,不涉及}
     *   yky   = 上一步集合里 可研批复=是
     *   ycs   = 可研≠否 的集合里 初设及概算批复=是
     *   ykg   = 初设≠否 的集合里 是否开工=是
     *   yjg   = 开工≠否 的集合里 是否竣工=是
     *   各率  = 数量 * 100 / ptzs（分母恒为配套总数，旧系统如此）
     * </pre>
     */
    private StatSummaryVO landSummary(StatQueryDTO q, List<ResolvedCondition> conds) {
        StatSummaryVO summary = new StatSummaryVO();
        summary.setLandTotal(statQueryMapper.countLand(q, conds));
        BigDecimal fee = statQueryMapper.sumLandFee(q, conds);
        summary.setSupportingFeeTotal((fee == null ? BigDecimal.ZERO : fee)
                .setScale(FEE_SCALE, RoundingMode.HALF_UP));

        List<Map<String, Object>> facts = statQueryMapper.selectLandFacilityFacts(q, conds);
        if (facts == null) {
            facts = Collections.emptyList();
        }

        // 「配套项目名称为空」按原始条数计（不去重）—— 空名称无法去重，
        // 这一列的含义是「有几条配套没填名称」，属于数据质量指标。
        long blankName = 0;
        for (Map<String, Object> fact : facts) {
            if (isBlank(text(fact, "ptxmmc"))) {
                blankName++;
            }
        }
        summary.setFacilityNameBlank(blankName);

        // 按 ptxmmc 去重，保留首次出现的那条（旧系统 TreeSet 比较器只看 ptxmmc，
        // 重复项不会被替换，所以「先到先得」是旧行为）
        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> fact : facts) {
            String name = text(fact, "ptxmmc");
            String key = name == null ? "" : name;
            if (!deduped.containsKey(key)) {
                deduped.put(key, fact);
            }
        }

        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> fact : deduped.values()) {
            String jsxx = text(fact, "jsxx");
            String name = text(fact, "ptxmmc");
            boolean notCurrent = isBlank(jsxx) || (!"现状".equals(jsxx) && !"不涉及".equals(jsxx));
            boolean nameOk = name == null || !name.contains("不涉及");
            if (notCurrent && nameOk) {
                kept.add(fact);
            }
        }
        long facilityTotal = kept.size();
        summary.setFacilityTotal(facilityTotal);

        // 新建道路
        long newRoad = 0;
        // 立项（漏斗第一级：非现状 且 项建批复≠否）
        long approved = 0;
        List<Map<String, Object>> level1 = new ArrayList<>();
        for (Map<String, Object> fact : kept) {
            if ("道路".equals(text(fact, "ptsslb")) && "新建".equals(text(fact, "jsxx"))) {
                newRoad++;
            }
            if (!"否".equals(text(fact, "xjpfsfwc"))) {
                level1.add(fact);
                String v = text(fact, "xjpfsfwc");
                if ("是".equals(v) || "不涉及".equals(v)) {
                    approved++;
                }
            }
        }
        summary.setNewRoadCount(newRoad);
        summary.setApprovedCount(approved);

        // 可研（漏斗第二级：项建批复≠否）
        long feasibility = 0;
        List<Map<String, Object>> level2 = new ArrayList<>();
        for (Map<String, Object> fact : level1) {
            if (!"否".equals(text(fact, "kypfsfwc"))) {
                level2.add(fact);
                if ("是".equals(text(fact, "kypfsfwc"))) {
                    feasibility++;
                }
            }
        }
        summary.setFeasibilityCount(feasibility);

        // 初设及概算（漏斗第三级：可研≠否）
        long design = 0;
        List<Map<String, Object>> level3 = new ArrayList<>();
        for (Map<String, Object> fact : level2) {
            if (!"否".equals(text(fact, "csjgspfsfwc"))) {
                level3.add(fact);
                if ("是".equals(text(fact, "csjgspfsfwc"))) {
                    design++;
                }
            }
        }
        summary.setDesignCount(design);

        // 开工（漏斗第四级：初设≠否）
        long started = 0;
        List<Map<String, Object>> level4 = new ArrayList<>();
        for (Map<String, Object> fact : level3) {
            if (!"否".equals(text(fact, "sfkg"))) {
                level4.add(fact);
                if ("是".equals(text(fact, "sfkg"))) {
                    started++;
                }
            }
        }
        summary.setStartedCount(started);

        // 竣工（漏斗第五级：开工≠否）
        long completed = 0;
        for (Map<String, Object> fact : level4) {
            if ("是".equals(text(fact, "sfjg"))) {
                completed++;
            }
        }
        summary.setCompletedCount(completed);

        // 比率：分母恒为配套总数（旧系统口径，不是新建道路数）
        summary.setApprovedRate(rate(approved, facilityTotal));
        summary.setFeasibilityRate(rate(feasibility, facilityTotal));
        summary.setDesignRate(rate(design, facilityTotal));
        summary.setStartedRate(rate(started, facilityTotal));
        summary.setCompletedRate(rate(completed, facilityTotal));
        // 旧「经营性用地查询」面板没有移交环节（移交在配套面板与首页看板里），保持 0
        summary.setHandedOverCount(0L);
        summary.setHandedOverRate(rate(0, facilityTotal));
        return summary;
    }

    /** 配套口径：命中配套项目本身（不去重、不漏斗），一次聚合 */
    private StatSummaryVO facilitySummary(StatQueryDTO q, List<ResolvedCondition> conds) {
        Map<String, Object> row = statQueryMapper.selectFacilitySummary(q, conds);
        StatSummaryVO summary = new StatSummaryVO();
        if (row == null) {
            return summary;
        }
        summary.setLandTotal(number(row, "landTotal"));
        summary.setFacilityTotal(number(row, "facilityTotal"));
        summary.setFacilityNameBlank(number(row, "facilityNameBlank"));
        summary.setNewRoadCount(number(row, "newRoadCount"));
        summary.setApprovedCount(number(row, "approvedCount"));
        summary.setFeasibilityCount(number(row, "feasibilityCount"));
        summary.setDesignCount(number(row, "designCount"));
        summary.setStartedCount(number(row, "startedCount"));
        summary.setCompletedCount(number(row, "completedCount"));
        summary.setHandedOverCount(number(row, "handedOverCount"));
        // 配套口径没有「配套费总额」这个指标（配套费是宗地属性，重复相加会虚高）
        summary.setSupportingFeeTotal(BigDecimal.ZERO.setScale(FEE_SCALE, RoundingMode.HALF_UP));

        long total = summary.getFacilityTotal() == null ? 0 : summary.getFacilityTotal();
        summary.setApprovedRate(rate(summary.getApprovedCount(), total));
        summary.setFeasibilityRate(rate(summary.getFeasibilityCount(), total));
        summary.setDesignRate(rate(summary.getDesignCount(), total));
        summary.setStartedRate(rate(summary.getStartedCount(), total));
        summary.setCompletedRate(rate(summary.getCompletedCount(), total));
        summary.setHandedOverRate(rate(summary.getHandedOverCount(), total));
        return summary;
    }

    // ==================================================================
    // 四、地块配套详情
    // ==================================================================

    @Override
    public long countRows(StatQueryDTO query) {
        StatQueryDTO q = prepare(query);
        DataSet dataSet = DataSet.of(q.getDataType());
        List<ResolvedCondition> conds = resolveConditions(q, dataSet);
        return dataSet == DataSet.FACILITY
                ? statQueryMapper.countFacility(q, conds)
                : statQueryMapper.countLand(q, conds);
    }

    @Override
    public List<Map<String, Object>> queryRowsForExport(StatQueryDTO query, int limit) {
        StatQueryDTO q = prepare(query);
        DataSet dataSet = DataSet.of(q.getDataType());
        List<ResolvedCondition> conds = resolveConditions(q, dataSet);
        List<String> keys = resolveColumnKeys(q, dataSet);
        int max = limit <= 0 ? 20000 : limit;
        List<Map<String, Object>> rows = dataSet == DataSet.FACILITY
                ? statQueryMapper.selectFacilityRows(q, conds, 0, max)
                : statQueryMapper.selectLandRows(q, conds, 0, max);
        return project(rows, dataSet, keys);
    }

    @Override
    public Map<String, Object> queryLandDetail(String crzdbh) {
        if (isBlank(crzdbh)) {
            throw new JeecgBootException("请先选择一个地块");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> facilities = statQueryMapper.selectFacilitiesByLand(crzdbh.trim());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (facilities != null) {
            for (Map<String, Object> facility : facilities) {
                Map<String, Object> projected = new LinkedHashMap<>();
                for (String key : facilitiesDetailKeys()) {
                    Field field = QueryFieldRegistry.find(DataSet.FACILITY, key);
                    projected.put(key, displayValue(field, facility.get(key)));
                }
                rows.add(projected);
            }
        }
        result.put("crzdbh", crzdbh.trim());
        result.put("facilities", rows);
        result.put("total", rows.size());
        return result;
    }

    /** 「地块配套详情」表固定 11 列（高保真该页签的字段名/字段值两列布局） */
    private static List<String> facilitiesDetailKeys() {
        return Collections.unmodifiableList(Arrays.asList(
                "ptxmmc", "ptsslb", "jsxx", "dldj", "tzgs", "zjly",
                "xjpfsfwc", "kypfsfwc", "csjgspfsfwc", "sfkg", "sfjg", "sfyj"));
    }

    // ==================================================================
    // 五、下拉候选值
    // ==================================================================

    @Override
    public Map<String, List<String>> queryOptions(String dataType) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        if (DataSet.of(dataType) == DataSet.FACILITY) {
            options.put("ptxmmc", nullSafe(statQueryMapper.selectFacilityPtxmmcOptions(OPTION_LIMIT)));
            options.put("ptsslb", nullSafe(statQueryMapper.selectFacilityPtsslbOptions(OPTION_LIMIT)));
            options.put("xzqh", nullSafe(statQueryMapper.selectFacilityXzqhOptions(OPTION_LIMIT)));
        } else {
            options.put("dkmc", nullSafe(statQueryMapper.selectLandDkmcOptions(OPTION_LIMIT)));
            options.put("xzqh", nullSafe(statQueryMapper.selectLandXzqhOptions(OPTION_LIMIT)));
            options.put("srr", nullSafe(statQueryMapper.selectLandSrrOptions(OPTION_LIMIT)));
            options.put("crsjYears", nullSafe(statQueryMapper.selectLandCrsjYears(OPTION_LIMIT)));
        }
        return options;
    }

    // ==================================================================
    // 六、内部工具
    // ==================================================================

    /**
     * 条件归一：
     * ① 多值去空去重；② 名称类按 AGENTS.md §2.4 做字符归一
     * （用户输入半角括号也能查到库里存的中文括号值）；③ 分页参数兜底。
     */
    private StatQueryDTO prepare(StatQueryDTO raw) {
        StatQueryDTO q = raw == null ? new StatQueryDTO() : raw;
        q.setDkmc(normalizeNames(q.getDkmc()));
        q.setSrr(normalizeNames(q.getSrr()));
        q.setPtxmmc(normalizeNames(q.getPtxmmc()));
        q.setXzqh(normalizeList(q.getXzqh(), false));
        q.setCrsjYears(normalizeList(q.getCrsjYears(), false));
        q.setPtsslb(normalizeList(q.getPtsslb(), false));
        q.setXmfl(trimToNull(q.getXmfl()));
        q.setJsxx(trimToNull(q.getJsxx()));
        q.setSfkg(trimToNull(q.getSfkg()));
        q.setSfjg(trimToNull(q.getSfjg()));
        q.setSfyj(trimToNull(q.getSfyj()));
        q.setSfzsjtjlz(trimToNull(q.getSfzsjtjlz()));
        q.setTjlzsftg(trimToNull(q.getTjlzsftg()));
        q.setXjpfsfwc(trimToNull(q.getXjpfsfwc()));
        q.setKypfsfwc(trimToNull(q.getKypfsfwc()));
        q.setCsjgspfwc(trimToNull(q.getCsjgspfwc()));
        q.setZjlsqk(trimToNull(q.getZjlsqk()));
        return q;
    }

    private static List<String> normalizeNames(List<String> values) {
        List<String> result = normalizeList(values, false);
        List<String> normalized = new ArrayList<>(result.size());
        for (String value : result) {
            String text = DataSupport.normalizeText(value);
            if (!isBlank(text) && !normalized.contains(text)) {
                normalized.add(text);
            }
        }
        return normalized;
    }

    private static List<String> normalizeList(List<String> values, boolean uppercase) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            String text = value.trim();
            unique.add(uppercase ? text.toUpperCase() : text);
        }
        return new ArrayList<>(unique);
    }

    /**
     * 把前端条件解析成「已白名单化」的 SQL 条件。
     *
     * <p>来源有两个：POST 的 {@code conditions} 数组，或 GET 导出时的
     * {@code conditionsJson} 字符串。解析失败直接忽略（导出页宁可用基础条件导出，
     * 也不要因为一个引号没转义就整份导出失败）。
     */
    private List<ResolvedCondition> resolveConditions(StatQueryDTO q, DataSet dataSet) {
        List<AdvancedCondition> raw = q.getConditions();
        if ((raw == null || raw.isEmpty()) && !isBlank(q.getConditionsJson())) {
            try {
                AdvancedCondition[] parsed =
                        MAPPER.readValue(q.getConditionsJson(), AdvancedCondition[].class);
                raw = parsed == null ? null : Arrays.asList(parsed);
            } catch (Exception e) {
                log.warn("高级查询条件解析失败，已忽略：{}", e.getMessage());
                raw = null;
            }
        }
        List<ResolvedCondition> resolved = new ArrayList<>();
        if (raw == null) {
            return resolved;
        }
        for (AdvancedCondition condition : raw) {
            if (condition == null || isBlank(condition.getField()) || isBlank(condition.getValue())) {
                continue;
            }
            Field field = QueryFieldRegistry.find(dataSet, condition.getField());
            if (field == null) {
                log.debug("忽略白名单外的查询字段：{}", condition.getField());
                continue;
            }
            Operator operator = Operator.of(condition.getOperator());
            // 数值/日期字段不给 LIKE（旧系统对数字字段也只给比较符）；文本字段允许全部
            if (field.getType() != QueryFieldRegistry.FieldType.TEXT && operator == Operator.LIKE) {
                operator = Operator.EQ;
            }
            if (!field.getOps().contains(operator)) {
                operator = field.getOps().get(0);
            }
            String relation = "OR".equalsIgnoreCase(trimToNull(condition.getRelation())) ? "OR" : "AND";
            String value = condition.getValue().trim();
            if (field.getType() == QueryFieldRegistry.FieldType.TEXT) {
                value = DataSupport.normalizeText(value);
            }
            if (FIELD_UNSAFE_VALUE.matcher(value).find()) {
                // 值里出现换行/制表/不可见字符时直接跳过：这些几乎只可能来自脚本
                log.debug("忽略含控制字符的查询值：{}", condition.getField());
                continue;
            }
            resolved.add(ResolvedCondition.of(field.getColumn(), operator.sql(), value, relation,
                    operator == Operator.LIKE));
        }
        return resolved;
    }

    /** 明细行投影：只保留用户选中的列，并按字段类型统一格式化 */
    private List<Map<String, Object>> project(List<Map<String, Object>> rows, DataSet dataSet,
                                              List<String> keys) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            Map<String, Object> projected = new LinkedHashMap<>();
            for (String key : keys) {
                Field field = QueryFieldRegistry.find(dataSet, key);
                if (field == null) {
                    continue;
                }
                projected.put(key, displayValue(field, getIgnoreCase(row, field.getColumn())));
            }
            result.add(projected);
        }
        return result;
    }

    /**
     * 统一展示格式：
     * · DATE → 字符串（{@code yyyy-MM-dd}；createTime 带时分秒），
     *   避免前端收到时间戳后各自格式化造成列与列不一致；
     * · NUMBER → 原样 BigDecimal（导出与表格用同一份值，不再二次四舍五入）；
     * · TEXT → 名称类过一遍字符归一后的原值（库里已是规范值，这里只防历史脏数据）。
     */
    private Object displayValue(Field field, Object raw) {
        if (raw == null) {
            return null;
        }
        if (field == null) {
            return raw;
        }
        switch (field.getType()) {
            case DATE:
                if (raw instanceof Date) {
                    String pattern = "createTime".equals(field.getKey()) ? DATETIME_PATTERN : DATE_PATTERN;
                    return new SimpleDateFormat(pattern).format((Date) raw);
                }
                return String.valueOf(raw);
            case NUMBER:
                return raw instanceof BigDecimal ? raw : new BigDecimal(String.valueOf(raw));
            default:
                String text = String.valueOf(raw);
                if (NAME_KEYS.contains(field.getKey())) {
                    return DataSupport.normalizeText(text);
                }
                return text;
        }
    }

    private List<String> resolveColumnKeys(StatQueryDTO q, DataSet dataSet) {
        List<String> keys = new ArrayList<>();
        if (q.getColumns() != null) {
            for (String key : q.getColumns()) {
                Field field = QueryFieldRegistry.find(dataSet, key);
                if (field != null && !keys.contains(field.getKey())) {
                    keys.add(field.getKey());
                }
            }
        }
        if (keys.isEmpty()) {
            keys.addAll(QueryFieldRegistry.defaultColumns(dataSet));
        }
        return keys;
    }

    private QueryColumnVO toColumnVO(Field field) {
        QueryColumnVO vo = new QueryColumnVO();
        vo.setKey(field.getKey());
        vo.setLabel(field.getLabel());
        vo.setType(field.getType().name());
        vo.setUnit(field.getUnit());
        vo.setDefaultColumn(field.isDefaultColumn());
        return vo;
    }

    /**
     * 比率：count * 100 / total，保留 1 位小数。
     * 分母为 0 时返回 0.0 —— 旧系统在这里会输出 {@code NaN%}（int 除法则抛 ArithmeticException），
     * 库里没有配套的新环境打开页面必须是 0%。
     */
    private static BigDecimal rate(long count, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = getIgnoreCase(row, key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    /** 大小写不敏感的取值（MySQL 返回的别名大小写不稳定，见 HomeStatServiceImpl 同款注释） */
    private static Object getIgnoreCase(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = getIgnoreCase(row, key);
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> nullSafe(List<String> values) {
        return values == null ? new ArrayList<>() : values;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 值里不允许出现的字符（控制字符 / 换行 / 制表 / 零宽） */
    private static final java.util.regex.Pattern FIELD_UNSAFE_VALUE =
            java.util.regex.Pattern.compile("[\\x00-\\x1F\\x7F\\u200B-\\u200D\\uFEFF]");
}
