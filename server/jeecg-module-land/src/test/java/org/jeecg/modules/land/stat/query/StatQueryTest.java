package org.jeecg.modules.land.stat.query;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.dto.FacilitySaveDTO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.service.IFacilityAdminService;
import org.jeecg.modules.land.data.service.ILandAdminService;
import org.jeecg.modules.land.stat.export.IStatQueryExportService;
import org.jeecg.modules.land.stat.query.dto.AdvancedCondition;
import org.jeecg.modules.land.stat.query.dto.StatQueryDTO;
import org.jeecg.modules.land.stat.query.service.IStatQueryService;
import org.jeecg.modules.land.stat.query.vo.QueryColumnVO;
import org.jeecg.modules.land.stat.query.vo.QueryPageVO;
import org.jeecg.modules.land.stat.query.vo.StatSummaryVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Description: 查询统计（方案 2.3.1（五）5-01 / 5-02）· 集成测试
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>测试跑在事务里、结束回滚，测试数据一律带 {@code TJ-TEST-DATA-} 前缀，
 * 并且每条断言都<b>先按测试专属行政区划/名称把范围收窄</b>，
 * 这样在真实库（847 宗地 / 1433 配套）上也只算自己造的那几条 —— 不写绝对值断言。
 *
 * <p><b>★ 这里钉住的几条口径，错了前端数字会悄悄错</b>：
 * <ul>
 *   <li>{@link #statsDeduplicateFacilityNameWithinScope()} —— 配套按 {@code ptxmmc}
 *       去重（同名道路挂两处只算 1 个），这是旧 {@code getSelectInfo()} 的口径；</li>
 *   <li>{@link #statsExcludeCurrentStateAndNotInvolved()} —— 「现状 / 不涉及」不进配套总数；</li>
 *   <li>{@link #statsFunnelAndRateUseFacilityTotalAsDenominator()} —— 各环节是漏斗，
 *       且比率分母恒为配套总数（不是新建道路数）；</li>
 *   <li>{@link #advancedConditionIgnoresWhitelistViolation()} —— 白名单外的字段被忽略而不是拼进 SQL；</li>
 *   <li>{@link #filterByXzqhNotXzqh2()} —— 行政区划筛选用 {@code xzqh}
 *       而非旧系统的 {@code xzqh2}（差异清单 D-609）。</li>
 * </ul>
 */
public class StatQueryTest extends LandIntegrationTestBase {

    private static final String TEST_PREFIX = "TJ-TEST-DATA-";

    @Autowired
    private IStatQueryService statQueryService;

    @Autowired
    private IStatQueryExportService statQueryExportService;

    @Autowired
    private ILandAdminService landAdminService;

    @Autowired
    private IFacilityAdminService facilityAdminService;

    // ==================================================================
    // 一、字段元数据与自定义列
    // ==================================================================

    @Test
    public void columnsExposeWhitelistedFieldsOnly() {
        List<QueryColumnVO> landColumns = statQueryService.queryColumns("land");
        assertFalse(landColumns.isEmpty(), "经营性用地字段清单不能为空");
        assertTrue(containsKey(landColumns, "crzdbh"), "缺少出让宗地编号");
        assertTrue(containsKey(landColumns, "nrcbdptf"), "缺少纳入成本的配套费（旧系统默认列之一）");
        assertFalse(containsKey(landColumns, "delFlag"), "软删列不能出现在可选字段里");
        assertFalse(containsKey(landColumns, "del_flag"), "软删列不能出现在可选字段里");

        // 旧系统写死 6 列：行政区划 / 出让宗地编号 / 地块名称 / 规划用地性质 / 纳入成本配套费 / 受让人
        long defaults = 0;
        for (QueryColumnVO column : landColumns) {
            if (Boolean.TRUE.equals(column.getDefaultColumn())) {
                defaults++;
            }
        }
        assertEquals(6, defaults, "经营性用地默认列应为旧系统的 6 列");

        List<QueryColumnVO> facilityColumns = statQueryService.queryColumns("facility");
        assertTrue(containsKey(facilityColumns, "ptxmmc"), "配套字段清单缺少配套项目名称");
        long facilityDefaults = 0;
        for (QueryColumnVO column : facilityColumns) {
            if (Boolean.TRUE.equals(column.getDefaultColumn())) {
                facilityDefaults++;
            }
        }
        assertEquals(7, facilityDefaults, "配套默认列应为高保真「配套查询结果」的 7 列");
    }

    @Test
    public void customColumnsDriveResultTable() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "自定义列-区-" + tag;
        String code = storedCode(TEST_PREFIX + "col-" + tag);
        landAdminService.createLand(buildLand(TEST_PREFIX + "col-" + tag, "自定义列测试地块",
                "市级项目", xzqh, null), null);

        StatQueryDTO query = new StatQueryDTO();
        query.setDataType("land");
        query.setXzqh(Collections.singletonList(xzqh));
        query.setColumns(Arrays.asList("crzdbh", "srr", "notExistField"));

        QueryPageVO page = statQueryService.queryPage(query);

        assertEquals(1, page.getTotal(), "应只命中 1 宗测试宗地");
        assertEquals(2, page.getColumns().size(), "白名单外的列必须被丢弃");
        Map<String, Object> row = page.getRows().get(0);
        assertEquals(code, row.get("crzdbh"));
        assertEquals(2, row.size(), "行里的 key 必须与返回的列一一对应");
    }

    // ==================================================================
    // 二、基础条件
    // ==================================================================

    @Test
    public void filterByXzqhNotXzqh2() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "区划口径-区-" + tag;
        String xzqh2 = TEST_PREFIX + "区划口径-录入单位-" + tag;
        landAdminService.createLand(buildLand(TEST_PREFIX + "xz-" + tag, "区划口径测试地块",
                "区级项目", xzqh, xzqh2), null);

        StatQueryDTO byXzqh = new StatQueryDTO();
        byXzqh.setDataType("land");
        byXzqh.setXzqh(Collections.singletonList(xzqh));
        assertEquals(1, statQueryService.queryPage(byXzqh).getTotal(),
                "★ 行政区划条件必须按 xzqh 命中（差异清单 D-609）");

        StatQueryDTO byXzqh2 = new StatQueryDTO();
        byXzqh2.setDataType("land");
        byXzqh2.setXzqh(Collections.singletonList(xzqh2));
        assertEquals(0, statQueryService.queryPage(byXzqh2).getTotal(),
                "★ xzqh2 是「录入单位简称」，不再当作行政区划筛选（旧系统 getCnd 的行为）");
    }

    @Test
    public void filterByYearAndProjectType() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "年份-区-" + tag;
        String rawCode = TEST_PREFIX + "year-" + tag;
        LandSaveDTO dto = buildLand(rawCode, "年份测试地块", "区级项目", xzqh, null);
        dto.setCrsj(java.sql.Date.valueOf("2024-06-01"));
        landAdminService.createLand(dto, null);

        StatQueryDTO hit = new StatQueryDTO();
        hit.setDataType("land");
        hit.setXzqh(Collections.singletonList(xzqh));
        hit.setCrsjYears(Collections.singletonList("2024"));
        hit.setXmfl("区级项目");
        assertEquals(1, statQueryService.queryPage(hit).getTotal(), "2024 年出让的区级项目应命中");

        StatQueryDTO miss = new StatQueryDTO();
        miss.setDataType("land");
        miss.setXzqh(Collections.singletonList(xzqh));
        miss.setCrsjYears(Collections.singletonList("2019"));
        assertEquals(0, statQueryService.queryPage(miss).getTotal(), "2019 年不应命中");
    }

    // ==================================================================
    // 三、分析结果口径（经营性用地）
    // ==================================================================

    @Test
    public void statsDeduplicateFacilityNameWithinScope() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "去重-区-" + tag;
        String ptxmmc = TEST_PREFIX + "去重道路-" + tag;
        String rawCodeA = TEST_PREFIX + "dedupe-a-" + tag;
        String rawCodeB = TEST_PREFIX + "dedupe-b-" + tag;
        landAdminService.createLand(buildLand(rawCodeA, "去重测试地块甲", "市级项目", xzqh, null), null);
        landAdminService.createLand(buildLand(rawCodeB, "去重测试地块乙", "市级项目", xzqh, null), null);

        // ★ 同名配套挂到**两宗不同宗地**下：这是「按名称去重」真正会发生作用的场景
        //   （新系统在写入侧已禁止同一宗地下重名配套，所以同宗地内造不出重复项）
        facilityAdminService.createFacility(
                buildFacility(storedCode(rawCodeA), ptxmmc, "新建", null, null), null);
        facilityAdminService.createFacility(
                buildFacility(storedCode(rawCodeB), ptxmmc, "新建", null, null), null);

        StatSummaryVO summary = statQueryService.querySummary(landQuery(xzqh));

        assertEquals(2L, summary.getLandTotal(), "地块总数应为 2");
        assertEquals(1L, summary.getFacilityTotal(),
                "★ 配套总数必须按 ptxmmc 去重：同一条道路挂两宗地只算 1 个（旧 getSelectInfo 口径）");
        assertEquals(1L, summary.getNewRoadCount(), "新建道路 1 条");
        assertEquals(0, new BigDecimal("0.000").compareTo(summary.getSupportingFeeTotal()),
                "未填配套费时总额应为 0.000");
    }

    @Test
    public void statsExcludeCurrentStateAndNotInvolved() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "现状-区-" + tag;
        String rawCode = TEST_PREFIX + "current-" + tag;
        landAdminService.createLand(buildLand(rawCode, "现状配套测试地块", "市级项目", xzqh, null), null);
        String code = storedCode(rawCode);

        facilityAdminService.createFacility(
                buildFacility(code, TEST_PREFIX + "现状道路-" + tag, "现状", null, null), null);
        facilityAdminService.createFacility(
                buildFacility(code, TEST_PREFIX + "不涉及事项-" + tag, "不涉及", null, null), null);

        StatSummaryVO summary = statQueryService.querySummary(landQuery(xzqh));

        assertEquals(0L, summary.getFacilityTotal(),
                "★ 「现状 / 不涉及」不计入配套总数（旧 getSelectInfo 的过滤条件）");
        assertEquals(0L, summary.getNewRoadCount(), "现状道路不是新建道路");
    }

    @Test
    public void statsFunnelAndRateUseFacilityTotalAsDenominator() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "漏斗-区-" + tag;
        String rawCode = TEST_PREFIX + "funnel-" + tag;
        LandSaveDTO land = buildLand(rawCode, "漏斗测试地块", "市级项目", xzqh, null);
        land.setNrcbdptf(new BigDecimal("12.345"));
        landAdminService.createLand(land, null);
        String code = storedCode(rawCode);

        // 一条走完全流程的道路 + 一条只到立项的另一道路
        FacilitySaveDTO full = buildFacility(code, TEST_PREFIX + "全流程道路-" + tag, "新建", "是", "是");
        full.setXjpfsfwc("是");
        full.setKypfsfwc("是");
        full.setCsjgspfsfwc("是");
        facilityAdminService.createFacility(full, null);

        FacilitySaveDTO partial = buildFacility(code, TEST_PREFIX + "半流程道路-" + tag, "新建", "否", "否");
        partial.setXjpfsfwc("是");
        partial.setKypfsfwc("否");
        facilityAdminService.createFacility(partial, null);

        StatSummaryVO summary = statQueryService.querySummary(landQuery(xzqh));

        assertEquals(2L, summary.getFacilityTotal(), "两条配套都应计入配套总数");
        assertEquals(2L, summary.getApprovedCount(), "两条的项建批复都是「是」");
        assertEquals(1L, summary.getFeasibilityCount(),
                "★ 漏斗：可研批复为「否」的那条不再进入下一环节");
        assertEquals(1L, summary.getDesignCount(), "初设及概算批复为「是」的只有 1 条");
        assertEquals(1L, summary.getStartedCount(), "开工为「是」的只有 1 条");
        assertEquals(1L, summary.getCompletedCount(), "竣工为「是」的只有 1 条");
        assertEquals(0, new BigDecimal("100.0").compareTo(summary.getApprovedRate()),
                "★ 立项率分母恒为配套总数：2 条配套里 2 条已立项 = 100.0%（不是新建道路数）");
        assertEquals(0, new BigDecimal("50.0").compareTo(summary.getCompletedRate()),
                "★ 竣工率同样是 1 / 2 = 50.0%");
    }

    @Test
    public void statsRateIsZeroWhenNoFacility() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "空口径-区-" + tag;
        landAdminService.createLand(buildLand(TEST_PREFIX + "empty-" + tag, "无配套测试地块",
                "市级项目", xzqh, null), null);

        StatSummaryVO summary = statQueryService.querySummary(landQuery(xzqh));

        assertEquals(1L, summary.getLandTotal(), "地块总数 1");
        assertEquals(0L, summary.getFacilityTotal(), "没有配套时配套总数为 0");
        assertEquals(0, new BigDecimal("0.0").compareTo(summary.getStartedRate()),
                "★ 分母为 0 时必须返回 0.0（旧系统这里会输出 NaN%）");
    }

    // ==================================================================
    // 四、高级查询（白名单 / 模糊 / 关系符）
    // ==================================================================

    @Test
    public void advancedConditionIgnoresWhitelistViolation() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "注入-区-" + tag;
        landAdminService.createLand(buildLand(TEST_PREFIX + "safe-" + tag, "白名单测试地块",
                "市级项目", xzqh, null), null);

        StatQueryDTO query = landQuery(xzqh);
        AdvancedCondition evil = new AdvancedCondition();
        evil.setField("crzdbh`) OR (1=1");
        evil.setOperator("like");
        evil.setValue("' OR '1'='1");
        query.setConditions(Collections.singletonList(evil));

        QueryPageVO page = statQueryService.queryPage(query);

        assertEquals(1, page.getTotal(),
                "★ 白名单外的字段与值必须被忽略：条件不生效，只剩行政区划条件筛出的 1 条");
    }

    @Test
    public void advancedConditionLikeFiltersText() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "高级-区-" + tag;
        String dkmc = TEST_PREFIX + "高级查询目标地块-" + tag;
        landAdminService.createLand(buildLand(TEST_PREFIX + "adv-a-" + tag, dkmc,
                "市级项目", xzqh, null), null);
        landAdminService.createLand(buildLand(TEST_PREFIX + "adv-b-" + tag,
                TEST_PREFIX + "另一个地块-" + tag, "市级项目", xzqh, null), null);

        StatQueryDTO query = landQuery(xzqh);
        AdvancedCondition condition = new AdvancedCondition();
        condition.setField("dkmc");
        condition.setOperator("like");
        condition.setValue("目标");
        query.setConditions(Collections.singletonList(condition));

        assertEquals(1, statQueryService.queryPage(query).getTotal(),
                "高级条件的 like 应只命中含「目标」的那块地");
    }

    @Test
    public void advancedConditionsCombineWithOr() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "与或-区-" + tag;
        landAdminService.createLand(buildLand(TEST_PREFIX + "or-a-" + tag, TEST_PREFIX + "甲地块-" + tag,
                "市级项目", xzqh, null), null);
        landAdminService.createLand(buildLand(TEST_PREFIX + "or-b-" + tag, TEST_PREFIX + "乙地块-" + tag,
                "市级项目", xzqh, null), null);
        landAdminService.createLand(buildLand(TEST_PREFIX + "or-c-" + tag, TEST_PREFIX + "丙地块-" + tag,
                "市级项目", xzqh, null), null);

        StatQueryDTO query = landQuery(xzqh);
        AdvancedCondition first = new AdvancedCondition();
        first.setField("dkmc");
        first.setOperator("like");
        first.setValue("甲地块");
        AdvancedCondition second = new AdvancedCondition();
        second.setField("dkmc");
        second.setOperator("like");
        second.setValue("乙地块");
        second.setRelation("OR");
        query.setConditions(Arrays.asList(first, second));

        assertEquals(2, statQueryService.queryPage(query).getTotal(),
                "★ 关系符 OR 应把两个名称条件并集起来（丙地块不命中）");
    }

    // ==================================================================
    // 五、配套项目查询
    // ==================================================================

    @Test
    public void facilityQueryAndSummary() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "配套-区-" + tag;
        String ptxmmc = TEST_PREFIX + "配套道路-" + tag;
        String rawCode = TEST_PREFIX + "fac-" + tag;
        landAdminService.createLand(buildLand(rawCode, "配套查询地块", "市级项目", xzqh, null), null);
        String code = storedCode(rawCode);

        FacilitySaveDTO dto = buildFacility(code, ptxmmc, "新建", "是", "是");
        dto.setSfyj("是");
        dto.setXjpfsfwc("是");
        facilityAdminService.createFacility(dto, null);

        StatQueryDTO query = new StatQueryDTO();
        query.setDataType("facility");
        query.setPtxmmc(Collections.singletonList(ptxmmc));

        QueryPageVO page = statQueryService.queryPage(query);
        assertEquals(1, page.getTotal(), "按配套项目名称应命中 1 条");
        assertEquals(7, page.getColumns().size(), "配套默认列 7 列");

        StatSummaryVO summary = statQueryService.querySummary(query);
        assertEquals(1L, summary.getFacilityTotal(), "配套总数按条计（配套数据集不去重）");
        assertEquals(1L, summary.getHandedOverCount(), "已移交 1 条");
        assertEquals(0, new BigDecimal("100.0").compareTo(summary.getHandedOverRate()),
                "移交率 1/1 = 100.0%");
    }

    // ==================================================================
    // 六、地块配套详情与导出
    // ==================================================================

    @Test
    public void landDetailListsFacilitiesOfThatLand() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "详情-区-" + tag;
        String rawCode = TEST_PREFIX + "detail-" + tag;
        landAdminService.createLand(buildLand(rawCode, "详情测试地块", "市级项目", xzqh, null), null);
        String code = storedCode(rawCode);
        facilityAdminService.createFacility(
                buildFacility(code, TEST_PREFIX + "详情道路甲-" + tag, "新建", null, null), null);
        facilityAdminService.createFacility(
                buildFacility(code, TEST_PREFIX + "详情道路乙-" + tag, "新建", null, null), null);

        Map<String, Object> detail = statQueryService.queryLandDetail(code);

        assertEquals(code, detail.get("crzdbh"));
        assertEquals(2, detail.get("total"), "该宗地下应有 2 条配套");
        assertTrue(detail.get("facilities") instanceof List);
    }

    @Test
    public void exportWorkbookHasResultAndSummarySheets() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "导出-区-" + tag;
        landAdminService.createLand(buildLand(TEST_PREFIX + "exp-" + tag, "导出测试地块",
                "市级项目", xzqh, null), null);

        StatQueryDTO query = landQuery(xzqh);
        query.setColumns(Arrays.asList("crzdbh", "dkmc", "nrcbdptf"));

        Workbook workbook = statQueryExportService.exportQuery(query);
        try {
            assertEquals(2, workbook.getNumberOfSheets(), "导出必须包含「查询结果」与「统计指标」两个 sheet");
            Sheet result = workbook.getSheetAt(0);
            // ExportParams 默认带一行大标题：标题行(0) + 表头行(1) + 数据行(2)
            assertEquals(2, result.getLastRowNum(), "1 条数据 = 标题 1 行 + 表头 1 行 + 数据 1 行");
            assertEquals(4, result.getRow(1).getLastCellNum(), "表头 = 序号 + 3 个自定义列 = 4 列");
            assertNotNull(result.getRow(2).getCell(0), "数据行必须有值（序号列）");
        } finally {
            try {
                workbook.close();
            } catch (Exception ignore) {
                // 关闭失败不影响断言
            }
        }
    }

    @Test
    public void countRowsMatchesPageTotal() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "计数-区-" + tag;
        landAdminService.createLand(buildLand(TEST_PREFIX + "cnt-a-" + tag, "计数甲地块",
                "市级项目", xzqh, null), null);
        landAdminService.createLand(buildLand(TEST_PREFIX + "cnt-b-" + tag, "计数乙地块",
                "市级项目", xzqh, null), null);

        StatQueryDTO query = landQuery(xzqh);
        assertEquals(2, statQueryService.countRows(query), "命中行数应为 2");
        assertEquals(2, statQueryService.queryPage(query).getTotal(), "分页 total 应与命中行数一致");
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private static StatQueryDTO landQuery(String xzqh) {
        StatQueryDTO query = new StatQueryDTO();
        query.setDataType("land");
        query.setXzqh(Collections.singletonList(xzqh));
        query.setPageSize(50);
        return query;
    }

    private static boolean containsKey(List<QueryColumnVO> columns, String key) {
        for (QueryColumnVO column : columns) {
            if (key.equals(column.getKey())) {
                return true;
            }
        }
        return false;
    }

    private LandSaveDTO buildLand(String crzdbh, String dkmc, String xmfl, String xzqh, String xzqh2) {
        LandSaveDTO dto = new LandSaveDTO();
        dto.setCrzdbh(crzdbh);
        dto.setDkmc(dkmc);
        dto.setXmfl(xmfl);
        dto.setXzqh(xzqh);
        dto.setXzqh2(xzqh2);
        dto.setLrdw("测试录入单位");
        dto.setLrr("测试录入人");
        dto.setLxdh("13800000000");
        dto.setCrj(new BigDecimal("1.00"));
        return dto;
    }

    private FacilitySaveDTO buildFacility(String crzdbh, String ptxmmc,
                                          String jsxx, String sfjg, String sfkg) {
        FacilitySaveDTO dto = new FacilitySaveDTO();
        dto.setCrzdbh(crzdbh);
        dto.setPtxmmc(ptxmmc);
        dto.setPtsslb("道路");
        dto.setJsxx(jsxx);
        dto.setSfjg(sfjg);
        dto.setSfkg(sfkg);
        dto.setDldj("城市次干路");
        dto.setZjly("土地整理成本");
        dto.setLrdw("测试录入单位");
        dto.setLrr("测试录入人");
        dto.setLxdh("13800000000");
        dto.setGhhxkd(new BigDecimal("30.00"));
        dto.setCd(new BigDecimal("520.00"));
        dto.setTzgs(new BigDecimal("1800.00"));
        return dto;
    }

    private static String uniqueTag() {
        return String.valueOf(System.nanoTime());
    }
}
