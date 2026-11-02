package org.jeecg.modules.land.stat.warning;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.stat.warning.service.IDecisionOpinionService;
import org.jeecg.modules.land.stat.warning.service.IWarningService;
import org.jeecg.modules.land.stat.warning.vo.DecisionOpinionVO;
import org.jeecg.modules.land.stat.warning.vo.RecalcResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Description: 分级预警 · 重算集成测试（端到端 / 幂等 / 三种口径 / 串行并行 / 等级缺失 / 意见优先级）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>守的是实现契约 §3.2~§3.8 与验收清单 §8 的第 3、4 项。
 * 每个用例都在事务里造数、结束回滚（见 {@link WarningTestSupport} / {@link org.jeecg.modules.land.LandIntegrationTestBase}）。
 *
 * <p><b>★ 断言为什么都用「构造出来的工作日天数」而不是绝对值</b>：
 * 「今天」是不可控的，而预警的输入是「逾期了几个工作日」。
 * 所以造数一律用 {@link #dateBeforeWorkdays(int)} 反推日期
 * （{@code workdaysBetween(D, 今天) == N} 由构造保证），
 * 这样任何一天跑测试，逾期天数都是同一个数，且跨周末/节假日不会漂移。
 *
 * <p><b>★ 这里钉住的几条「错了数字会悄悄错」的口径</b>：
 * <ul>
 *   <li>{@link #stageWarningUsesMaxForParallelStage()} —— 立项阶段 4 个事项全并行，
 *       阶段历时取 <b>max</b>；取 sum 会把历时放大 4 倍（旧代码在混合配置下就是乱取一个）；</li>
 *   <li>{@link #stageWarningUsesSumForSerialStage()} —— 项目施工阶段 3 个事项全串行，
 *       阶段历时取 <b>sum</b>；取 max 会让「一项一项办」的阶段永远不预警；</li>
 *   <li>{@link #itemWarningIsRemovedAfterCompletedWithinPlan()} —— 补录完成时间后
 *       预警必须<b>消失</b>（旧系统从不删，这是被投诉的点）；</li>
 *   <li>{@link #recalcIsIdempotent()} —— 同一份数据连算两次，
 *       第二次 created=0 / removed=0、行数不变；</li>
 *   <li>{@link #opinionPrefersExactOverGeneric()} —— 决策意见「精确 → 通用兜底」的优先级。</li>
 * </ul>
 */
public class WarningRecalcTest extends WarningTestSupport {

    @Autowired
    private IWarningService warningService;

    @Autowired
    private IDecisionOpinionService decisionOpinionService;

    // ==================================================================
    // 一、环节预警端到端（契约 §3.2 环节口径 + §3.5 预警值 + §3.7 决策意见）
    // ==================================================================

    /**
     * 一条逾期环节 → 生成一条环节预警：等级 / 预警值 / 逾期天数 / 决策意见 全都要对。
     *
     * <p>造数：项目建设开工（标准 15 工作日），开始日 = 25 个工作日前，
     * 预计结束时间 = 3 个工作日前 → 逾期 3 个工作日（蓝色 1-10）。
     * 实际历时 = 25 个工作日。因为 25 &lt; 阶段标准 60，阶段与总体都不会出预警，
     * 于是「t_process_warning 里恰好 1 行」本身就是一条断言。
     */
    @Test
    public void itemWarningCarriesLevelValueAndOpinion() {
        Fixture fixture = newFixture();
        Config item = config(ITEM_PROJECT_1);
        // 前置条件：这个环节的标准时长必须是 15（种子里 00040001 就是 15）
        assertEquals(15, item.processTime, "前置条件：项目建设开工的标准时长应为 15 工作日");

        String problemType = "资金问题";
        String problemDetail = TEST_PREFIX + "资金未到位-" + uniqueTag();
        insertProcess(fixture, ITEM_PROJECT_1, "进行中",
                dateBeforeWorkdays(25), dateBeforeWorkdays(3), null, problemType, problemDetail);

        RecalcResultVO result = warningService.recalc(fixture.ptId);

        assertEquals(1, result.getScannedFacilities(), "只重算指定的这一个配套");
        assertEquals(1, result.getScannedProcesses(), "该配套只有 1 条环节进度");
        assertFalse(Boolean.TRUE.equals(result.getLevelMissing()), "等级表有 4 档，不应报等级缺失");
        assertEquals(1, result.getCreated().intValue(), "首次重算应新增 1 条预警");

        List<Map<String, Object>> rows = warningRows(fixture.ptId);
        assertEquals(1, rows.size(), "25 个工作日 < 阶段标准 60 → 只应生成 1 条环节预警，实际：" + rows);

        Map<String, Object> row = findWarning(rows, "环节预警", item.id);
        assertNotNull(row, "★ 应按 (pt_id, yj_type, lc_id) 生成环节预警");

        // ---- 等级 ----
        assertEquals("blue", row.get("levelCode"),
                "★ 逾期 3 个工作日应落在蓝色区间 [1,10]");
        assertEquals("蓝色预警", row.get("levelName"));
        assertEquals(levelIdByCode("blue"), row.get("levelId"), "等级 id 必须指向等级表里蓝色那一行");

        // ---- 三个天数列 ----
        assertEquals(3, asInt(row.get("overdueDays"), -1),
                "★ 逾期天数 = 工作日(预计结束时间, 今天)，周末与节假日不计");
        assertEquals(15, asInt(row.get("stdDays"), -1), "标准时长取环节配置的 process_time");
        assertEquals(25, asInt(row.get("actualDays"), -1),
                "实际历时 = 工作日(开始时间, 结束时间)，未完成时算到今天");

        // ---- 预警值：环节口径 (std + overdue) / std ----
        BigDecimal yjValue = (BigDecimal) row.get("yjValue");
        assertNotNull(yjValue, "预警值不能为空");
        assertEquals(0, new BigDecimal("1.2000").compareTo(yjValue),
                "(15 + 3) / 15 = 1.2 → 1.2000，实际 " + yjValue);
        assertEquals(4, yjValue.scale(), "库里是 decimal(10,4)，必须保留 4 位小数");

        // ---- 问题类型 / 具体情况 / 决策意见 ----
        assertEquals(problemType, row.get("problemType"), "问题类型取环节进度的 czwtlx");
        assertEquals(problemDetail, row.get("problemDetail"), "具体情况取环节进度的 jtwt");

        String expectedOpinion = exactOpinion(levelIdByCode("blue"), problemType);
        if (expectedOpinion == null) {
            expectedOpinion = genericOpinion(levelIdByCode("blue"));
        }
        assertNotNull(expectedOpinion,
                "★ 蓝色预警至少要有一条决策意见（种子里有 problem_type='' 的通用意见），"
                        + "否则「决策意见」这一列永远空着，用户看不出该怎么办");
        assertEquals(expectedOpinion, row.get("decisionOpinion"),
                "★ 决策意见按「精确 (问题类型, 等级) → 通用 ('', 等级)」取值");

        // ---- 冗余身份列（列表不联表就靠它们）----
        assertEquals(fixture.ptxmmc + "预警分析", row.get("yjName"), "★ 预警名称 = 配套项目名 + 预警分析（旧口径）");
        assertEquals(fixture.ptxmmc, row.get("ptName"));
        assertEquals(fixture.ptxmmc, row.get("ptxmmc"), "ptxmmc 与 pt_name 同值（与旧接口入参对齐）");
        assertEquals(fixture.crzdbh, row.get("crzdbh"));
        assertEquals(fixture.xzqh, row.get("xzqh"),
                "★ 行政区划取配套表的（配套为空才回落宗地表），此处两者故意给了不同的值");
        assertEquals(fixture.dkmc, row.get("dkmc"), "★ 地块名称同样「配套优先、回落宗地」");
        assertEquals(item.zgbm, row.get("zrbm"), "责任部门取环节配置的 zgbm");
        assertEquals(item.name, row.get("lcName"));
        assertEquals(config(STAGE_PROJECT).id, row.get("stageId"), "环节预警要带所属阶段 id");
        assertNotNull(row.get("calcTime"), "calcTime 是「这条数是几时算的」，不能为空");
    }

    // ==================================================================
    // 二、补录完成 → 预警消失（契约 §3.8 第 3 步）
    // ==================================================================

    /**
     * 补录实际完成时间后重算：
     * <ol>
     *   <li>完成时间<b>仍晚于</b>预计结束时间 → 预警<b>保留</b>（逾期是既成事实，这是正确语义）；</li>
     *   <li>完成时间<b>不晚于</b>预计结束时间 → 预警<b>删除</b>。</li>
     * </ol>
     *
     * <p>★ 第 2 条是旧系统被投诉的点：旧实现只 upsert 从不删，
     * 「已经补录完成了，预警还挂在列表上」。所以这里既断言 removed 计数、
     * 也断言库里真的没有行（只看回执不看库会被「删了但没删掉」骗过去）。
     */
    @Test
    public void itemWarningIsRemovedAfterCompletedWithinPlan() {
        Fixture fixture = newFixture();
        Config item = config(ITEM_PROJECT_1);
        insertProcess(fixture, ITEM_PROJECT_1, "进行中",
                dateBeforeWorkdays(25), dateBeforeWorkdays(3), null, null, null);
        // 直接 INSERT 不会触发钩子（见 WarningTestSupport#insertProcess），所以先手动算一遍
        warningService.recalc(fixture.ptId);

        assertEquals(1, warningRows(fixture.ptId).size(), "前置：先有一条环节预警");

        // ① 补录「今天完成」——仍然晚于预计结束时间（3 个工作日前），预警应保留并按新数据重算
        finishProcess(fixture, ITEM_PROJECT_1, "已完成", today());
        RecalcResultVO keep = warningService.recalc(fixture.ptId);
        assertEquals(0, keep.getCreated().intValue(), "已经有这条预警了，不应再新增");
        assertEquals(1, keep.getUpdated().intValue(), "应更新这一条");
        assertEquals(0, keep.getRemoved().intValue(), "★ 完成时间仍晚于预计结束时间 → 逾期成立，预警必须保留");
        Map<String, Object> kept = findWarning(warningRows(fixture.ptId), "环节预警", item.id);
        assertNotNull(kept);
        assertEquals(3, asInt(kept.get("overdueDays"), -1), "逾期天数仍按「预计结束时间 → 实际结束时间」重算");

        // ② 补录「在预计结束时间当天完成」→ 逾期天数 0 → 预警应被删除
        finishProcess(fixture, ITEM_PROJECT_1, "已完成", dateBeforeWorkdays(3));
        RecalcResultVO removed = warningService.recalc(fixture.ptId);
        assertEquals(0, removed.getCreated().intValue());
        assertEquals(1, removed.getRemoved().intValue(), "★ 不再逾期，应删除该预警");
        assertTrue(warningRows(fixture.ptId).isEmpty(),
                "★ 补录完成后库里不能还留着预警行（旧系统就是不删，列表里一堆过期预警）");
    }

    // ==================================================================
    // 三、幂等（契约 §3.8 / 验收 §8 第 4 项）
    // ==================================================================

    /**
     * 同一份数据连算两次：第二次 {@code created=0 / removed=0}、总行数不变。
     *
     * <p>★ 幂等的基石是唯一键 {@code uk_pw_key (pt_id, yj_type, lc_id)} +
     * 「与库内现有行求差」的写法。没有它，每点一次「重新计算」列表就翻一倍
     * —— 旧系统正因如此会堆重复行。
     */
    @Test
    public void recalcIsIdempotent() {
        Fixture fixture = newFixture();
        insertProcess(fixture, ITEM_PROJECT_1, "进行中",
                dateBeforeWorkdays(25), dateBeforeWorkdays(3), null, "审批问题", null);
        // 再给一条「并行阶段」的数据，让这一轮同时产出环节 + 阶段两类预警（更接近真实情况）
        insertProcess(fixture, ITEM_LIXIANG_1, "进行中",
                dateBeforeWorkdays(30), today(), null, null, null);

        RecalcResultVO first = warningService.recalc(fixture.ptId);
        int rowsAfterFirst = warningRows(fixture.ptId).size();
        assertTrue(first.getCreated() >= 2,
                "首轮应至少新增 2 条（环节 + 阶段），实际 created=" + first.getCreated());
        assertEquals(first.getCreated().intValue(), rowsAfterFirst,
                "首轮新增数应等于库里最终行数（本轮之前该配套没有任何预警）");

        RecalcResultVO second = warningService.recalc(fixture.ptId);
        assertEquals(0, second.getCreated().intValue(), "★ 第二轮不应再新增（幂等）");
        assertEquals(0, second.getRemoved().intValue(), "★ 第二轮不应删除任何行（幂等）");
        assertEquals(rowsAfterFirst, second.getUpdated().intValue(), "第二轮应把每一行都「更新」一次（值没变也算更新）");
        assertEquals(rowsAfterFirst, warningRows(fixture.ptId).size(),
                "★ 连算两次总行数必须不变，否则列表会越点越多");
    }

    // ==================================================================
    // 四、串行 / 并行（契约 §3.3，验收 §8 第 3 项）
    // ==================================================================

    /**
     * 立项阶段（4 个事项全部 {@code is_parallel=1}）→ 阶段历时取 <b>max</b>。
     *
     * <p>造数：两个事项分别在 25 / 5 个工作日前开始、今天完成。
     * 并行口径取 max = 25 → 阶段标准 20 → 逾期 5（蓝色）；
     * 若错写成 sum = 30 → 逾期 10（也是蓝色，但逾期天数与预警值不同）——
     * 所以断言重点放在 {@code actualDays=25} 与 {@code overdueDays=5} 上，
     * 只断言等级是抓不到「sum/max 写反」的。
     *
     * <p>两个事项的 {@code yjjssj} 都设成今天 → 逾期 0 → 不生成环节预警，
     * 这样断言不会被「同一批数据还产出了别的预警」干扰。
     */
    @Test
    public void stageWarningUsesMaxForParallelStage() {
        // 前置条件断言：种子配置必须是「立项 4 项全并行」，否则本用例的口径不成立
        for (Config item : itemsOfStage(STAGE_LIXIANG)) {
            assertEquals(1, item.isParallel, "前置条件：立项阶段的每个事项都应 is_parallel=1（并行）。"
                    + "若中心改过配置，本用例的断言要一起改：" + item.name);
        }
        assertTrue(itemsOfStage(STAGE_LIXIANG).size() >= 2, "前置条件：立项阶段至少要有 2 个事项才验得出 max/sum 差异");

        Fixture fixture = newFixture();
        insertProcess(fixture, ITEM_LIXIANG_1, "已完成",
                dateBeforeWorkdays(25), today(), today(), null, null);
        insertProcess(fixture, ITEM_LIXIANG_2, "已完成",
                dateBeforeWorkdays(5), today(), today(), null, null);

        warningService.recalc(fixture.ptId);

        List<Map<String, Object>> rows = warningRows(fixture.ptId);
        assertEquals(0, countWarnings(fixture.ptId, "环节预警"),
                "两个事项的预计结束时间都是今天 → 逾期 0 → 不应有环节预警");

        Map<String, Object> stage = findWarning(rows, "阶段预警", config(STAGE_LIXIANG).id);
        assertNotNull(stage, "★ 阶段历时 25 > 阶段标准 20，应生成阶段预警");
        assertEquals(25, asInt(stage.get("actualDays"), -1),
                "★ 并行阶段的实际历时必须取 max(25, 5) = 25；若是 sum 会得到 30");
        assertEquals(config(STAGE_LIXIANG).processTime, asInt(stage.get("stdDays"), -1),
                "阶段标准时长取阶段配置的 process_time（20）");
        assertEquals(5, asInt(stage.get("overdueDays"), -1),
                "★ 阶段逾期天数 = actual - std = 25 - 20 = 5；sum 口径会是 10");
        assertEquals(0, new BigDecimal("1.2500").compareTo((BigDecimal) stage.get("yjValue")),
                "阶段预警值 = actual / std = 25 / 20 = 1.2500");
        // 阶段预警不填问题类型与决策意见（契约 §3.7：阶段没有「问题类型」）
        assertNull(stage.get("problemType"), "★ 阶段预警不应有 problem_type（硬套话术会误导用户）");
        assertNull(stage.get("decisionOpinion"), "★ 阶段预警不应有决策意见");
    }

    /**
     * 项目施工阶段（3 个事项全部 {@code is_parallel=0}）→ 阶段历时取 <b>sum</b>。
     *
     * <p>造数：两个事项各历时 40 个工作日（开始日 = 40 个工作日前，今天完成）。
     * 串行口径 sum = 80 → 阶段标准 60 → 逾期 20（黄色）；
     * 若错写成 max = 40 → 40 &lt; 60 → <b>一条阶段预警都不会有</b>，
     * 所以断言「存在 + actualDays=80」能干净地区分两种口径。
     * 总体口径 80 &lt; 155（六阶段标准之和），也不会出总体预警。
     */
    @Test
    public void stageWarningUsesSumForSerialStage() {
        for (Config item : itemsOfStage(STAGE_PROJECT)) {
            assertEquals(0, item.isParallel, "前置条件：项目施工阶段的每个事项都应 is_parallel=0（串行）。"
                    + "若中心改过配置，本用例的断言要一起改：" + item.name);
        }

        Fixture fixture = newFixture();
        insertProcess(fixture, ITEM_PROJECT_1, "已完成",
                dateBeforeWorkdays(40), today(), today(), null, null);
        insertProcess(fixture, ITEM_PROJECT_2, "已完成",
                dateBeforeWorkdays(40), today(), today(), null, null);

        warningService.recalc(fixture.ptId);

        List<Map<String, Object>> rows = warningRows(fixture.ptId);
        Map<String, Object> stage = findWarning(rows, "阶段预警", config(STAGE_PROJECT).id);
        assertNotNull(stage, "★ 串行阶段历时 40 + 40 = 80 > 60，应生成阶段预警（取 max 的话永远不会预警）");
        assertEquals(80, asInt(stage.get("actualDays"), -1),
                "★ 串行阶段的实际历时必须取 sum(40, 40) = 80");
        assertEquals(config(STAGE_PROJECT).processTime, asInt(stage.get("stdDays"), -1),
                "阶段标准时长取阶段配置的 process_time（60）");
        assertEquals(20, asInt(stage.get("overdueDays"), -1), "阶段逾期 = 80 - 60 = 20");
        assertEquals("yellow", stage.get("levelCode"), "★ 逾期 20 天落在黄色区间 [11,20]");
        assertEquals(0, new BigDecimal("1.3333").compareTo((BigDecimal) stage.get("yjValue")),
                "阶段预警值 = 80 / 60 = 1.3333（4 位小数 HALF_UP）");
    }

    // ==================================================================
    // 五、总体预警（契约 §3.2 第三种口径）
    // ==================================================================

    /**
     * 总体预警：实际 = 六个阶段实际历时之和，标准 = 六个阶段标准时长之和。
     *
     * <p>造数：一个事项历时 160 个工作日（开始日 = 160 个工作日前，今天完成）。
     * 它所在的「项目施工阶段」串行历时也是 160（超出 60 → 红色），
     * 而六阶段标准之和是 20+30+15+60+15+15 = 155 → 总体逾期 5（蓝色）。
     *
     * <p>★ 这条用例的价值在于「总体」与「阶段」用的是<b>不同的分母</b>：
     * 阶段口径比 160/60，总体口径比 160/155，两者等级不同。
     * 若总体误用「单个阶段的标准时长」或「漏加某个阶段」，等级会立刻变。
     */
    @Test
    public void overallWarningSumsAllSixStages() {
        int expectedStdSum = stageStdDaysSum();
        assertEquals(155, expectedStdSum, "前置条件：六个阶段的标准时长之和应为 155 个工作日");

        Fixture fixture = newFixture();
        // 预计结束时间设成今天 → 该事项逾期 0 → 不产环节预警，只留阶段 + 总体两类
        insertProcess(fixture, ITEM_PROJECT_1, "已完成",
                dateBeforeWorkdays(160), today(), today(), null, null);

        warningService.recalc(fixture.ptId);

        List<Map<String, Object>> rows = warningRows(fixture.ptId);
        assertEquals(0, countWarnings(fixture.ptId, "环节预警"),
                "预计结束时间 = 今天 → 逾期 0 → 不预警（0 天是「正好到期」，不是逾期）");

        Map<String, Object> overall = findWarning(rows, "总体预警", "");
        assertNotNull(overall, "★ 160 > 155 应生成总体预警（lc_id 为空串）");
        assertEquals(160, asInt(overall.get("actualDays"), -1), "总体实际历时 = 六阶段实际之和");
        assertEquals(expectedStdSum, asInt(overall.get("stdDays"), -1), "总体标准时长 = 六阶段标准之和（155）");
        assertEquals(160 - expectedStdSum, asInt(overall.get("overdueDays"), -1), "总体逾期 = 160 - 155 = 5");
        assertEquals("blue", overall.get("levelCode"), "★ 总体逾期 5 天落在蓝色区间 [1,10]");
        assertEquals("项目总体", overall.get("lcName"), "总体预警的流程环节列显示「项目总体」，比留空好懂");
        assertNull(overall.get("stageId"), "总体预警不属于任何一个阶段");
        assertEquals(0, new BigDecimal("1.0323").compareTo((BigDecimal) overall.get("yjValue")),
                "总体预警值 = 160 / 155 = 1.0323");

        // 同一条数据在阶段口径下是「160 / 60 = 逾期 100 → 红色」，与总体口径互不干扰
        Map<String, Object> stage = findWarning(rows, "阶段预警", config(STAGE_PROJECT).id);
        assertNotNull(stage, "阶段口径同时应命中");
        assertEquals("red", stage.get("levelCode"), "阶段口径 160 - 60 = 100 → 红色 [31,100]");
        assertEquals(100, asInt(stage.get("overdueDays"), -1), "★ 阶段逾期上限 100 天正好是红色下界之上，不该走 overflow");
    }

    // ==================================================================
    // 六、等级表为空（契约 §3.6 情形 ⑤ / §5.4 回执）
    // ==================================================================

    /**
     * 等级表一条都没有 → 不生成任何预警，且回执给出 {@code levelMissing=true}。
     *
     * <p>★ 为什么这条重要：这是「新环境还没配阈值」时的表现。
     * 如果不给 {@code levelMissing} 提示，界面只会显示「0 条预警」，
     * 用户会以为「系统算出来没有逾期」，而实际是「一条都算不出等级」。
     *
     * <p>造数方式：事务内把 4 个等级全部软删（结束回滚，不影响库里真实配置）。
     */
    @Test
    public void levelMissingSkipsWarnings() {
        Fixture fixture = newFixture();
        insertProcess(fixture, ITEM_PROJECT_1, "进行中",
                dateBeforeWorkdays(25), dateBeforeWorkdays(3), null, null, null);

        // 事务内临时软删全部等级（模拟「新环境还没配阈值」）
        int deleted = jdbc().update(
                "UPDATE `t_process_warning_level` SET `del_flag` = 1, `update_time` = NOW() WHERE `del_flag` = 0");
        assertTrue(deleted >= 4, "前置条件：库里至少要有 4 档等级，实际软删了 " + deleted);

        RecalcResultVO result = warningService.recalc(fixture.ptId);

        assertTrue(Boolean.TRUE.equals(result.getLevelMissing()),
                "★ 等级表为空时回执必须给 levelMissing=true（否则界面只显示「0 条」，没人知道是没配阈值）");
        assertEquals(0, result.getCreated().intValue(), "等级表为空 → 一条也算不出来");
        assertTrue(warningRows(fixture.ptId).isEmpty(),
                "★ 等级缺失时不能生成「没有等级」的预警行（前端四色卡片会缺色、也没法着色）");
    }

    // ==================================================================
    // 七、决策意见优先级（契约 §3.7）
    // ==================================================================

    /**
     * 决策意见：精确 {@code (问题类型, 等级)} 优先，匹配不到才用通用 {@code ('', 等级)}。
     *
     * <p>★ 旧系统只有「精确」这一层，5 类 × 4 级 = 20 格里漏配一格，
     * 那一格就永远没有建议，而且完全静默。新实现加了通用兜底，
     * 但**必须精确优先** —— 否则中心为「资金问题」专门写的处置口径会被一句通用话术盖掉。
     *
     * <p>造数：事务内给「资金问题 × 蓝色预警」补一条精确意见（正文带测试前缀，便于兜底清理），
     * 然后造一个蓝色的环节预警，断言取到的是这条精确意见而不是通用那条。
     */
    @Test
    public void opinionPrefersExactOverGeneric() {
        String blueLevelId = levelIdByCode("blue");
        assertNotNull(blueLevelId, "前置条件：等级表里应有 blue 这一档");
        String generic = genericOpinion(blueLevelId);
        assertNotNull(generic, "前置条件：种子里应有蓝色预警的通用意见（problem_type=''）");

        String problemType = "资金问题";
        String exactText = TEST_PREFIX + "精确决策意见-" + uniqueTag();
        String existingExact = exactOpinion(blueLevelId, problemType);
        if (existingExact == null) {
            DecisionOpinionVO vo = new DecisionOpinionVO();
            vo.setLevelId(blueLevelId);
            vo.setProblemType(problemType);
            vo.setOpinion(exactText);
            decisionOpinionService.save(vo);
        } else {
            // 库里已经有这一格（中心配过）→ 直接用它的正文当期望值，不去覆盖真实配置
            exactText = existingExact;
        }
        assertNotEquals(generic, exactText,
                "测试用的精确意见正文必须与通用意见不同，否则「命中了哪一条」验不出来");

        Fixture fixture = newFixture();
        insertProcess(fixture, ITEM_PROJECT_1, "进行中",
                dateBeforeWorkdays(25), dateBeforeWorkdays(3), null, problemType, null);

        warningService.recalc(fixture.ptId);

        Map<String, Object> row = findWarning(warningRows(fixture.ptId), "环节预警", config(ITEM_PROJECT_1).id);
        assertNotNull(row);
        assertEquals("blue", row.get("levelCode"));
        assertEquals(exactText, row.get("decisionOpinion"),
                "★ 有精确 (问题类型, 等级) 的意见时必须用它，不能被通用兜底盖掉");
    }

    // ==================================================================
    // 八、工作日口径（验收 §8 第 2 项）
    // ==================================================================

    /**
     * 工作日口径：跨周末 + 跨法定节假日时，只数工作日。
     *
     * <p>造数用固定日期（2026-09-30 预计结束 → 2026-10-12 实际完成）：
     * 自然日差 12 天，其中 10-01~10-08 是国庆长假、10-03/04 与 10-10/11 是周末，
     * 只有 10-09（周五）和 10-12（周一）是工作日 → 逾期 <b>2</b> 天。
     *
     * <p>★ 为什么这条不能省：旧代码 {@code getDay} 是「递归 + 补偿」，
     * 跨长假时天数会滚雪球（旧注释自己写了这一点），算出来的逾期天数偏大，
     * 于是「国庆前到期」的环节会被提前判成黄色/橙色。
     *
     * <p>第二段造数验证「含头不含尾」：预计结束时间与实际完成时间是同一天 → 逾期 0 → 不预警。
     */
    @Test
    public void workdayCaliberExcludesWeekendAndHoliday() {
        // ① 日历本身：国庆假期与周末不是工作日，节后第一个周五是工作日
        assertFalse(workdayCalculator.isWorkday(date("2026-10-01")), "国庆当天不是工作日");
        assertFalse(workdayCalculator.isWorkday(date("2026-10-07")), "国庆假期内不是工作日");
        assertFalse(workdayCalculator.isWorkday(date("2026-10-03")), "周六不是工作日");
        assertTrue(workdayCalculator.isWorkday(date("2026-10-09")), "节后第一个工作日是 10-09（周五）");

        // ② 逾期天数只数工作日
        Fixture fixture = newFixture();
        insertProcess(fixture, ITEM_PROJECT_1, "已完成",
                date("2026-09-01"), date("2026-09-30"), date("2026-10-12"), null, null);
        warningService.recalc(fixture.ptId);

        Map<String, Object> row = findWarning(warningRows(fixture.ptId), "环节预警", config(ITEM_PROJECT_1).id);
        assertNotNull(row, "9-30 到期、10-12 完成 → 跨假期逾期，应生成预警");
        long calendarDays = ChronoUnit.DAYS.between(toLocalDate(date("2026-09-30")), toLocalDate(date("2026-10-12")));
        assertEquals(12, calendarDays, "前置条件：两个日期相差 12 个自然日");
        assertEquals(2, asInt(row.get("overdueDays"), -1),
                "★ 12 个自然日里只有 10-09、10-12 是工作日 → 逾期 2 天（含头不含尾，(9-30, 10-12]）");
        assertEquals("blue", row.get("levelCode"), "逾期 2 天 → 蓝色");

        // ③ 「含头不含尾」：预计结束时间 = 实际完成时间 → 逾期 0 → 不生成预警
        Fixture sameDay = newFixture();
        insertProcess(sameDay, ITEM_PROJECT_1, "已完成",
                date("2026-09-01"), date("2026-10-12"), date("2026-10-12"), null, null);
        warningService.recalc(sameDay.ptId);
        assertTrue(warningRows(sameDay.ptId).isEmpty(),
                "★ 预计结束时间与实际完成时间是同一天 → 逾期 0 → 不预警（含头不含尾，不是「当天算 1 天」）");
    }

    // ==================================================================
    // 九、保存环节后的钩子（契约 §3.9）
    // ==================================================================

    /**
     * 保存 / 撤回环节进度后，重算必须被自动触发（且失败也不会影响保存）。
     *
     * <p>★ 为什么要单独验钩子：{@code FacilityAdminServiceImpl} 里注入的是
     * {@code @Autowired(required = false) IWarningService}（预警是<b>可降级</b>能力）。
     * 万一 {@code LandTestConfig} 的白名单忘了登记 {@code stat.warning}，
     * 这里会静默不重算 —— 表现是「录入完环节，预警列表还是空的」，
     * 而且不会有任何报错。这条用例把「注入成功 + 钩子被调用」一起钉住。
     *
     * <p>注意：这里刻意走真实的 {@code saveProcess} / {@code removeProcess}
     * （而不是直接 INSERT），因为被测的正是这两个入口上的钩子。
     */
    @Test
    public void saveProcessHookRecalculates() {
        Fixture fixture = newFixture();
        Config item = config(ITEM_PROJECT_1);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lcqk", "进行中");
        payload.put("lckssj", formatDate(dateBeforeWorkdays(25)));
        payload.put("yjjssj", formatDate(dateBeforeWorkdays(3)));
        payload.put("czwtlx", "审批问题");
        facilityAdminService.saveProcess(fixture.ptId, item.id, payload, null);

        assertNotNull(findWarning(warningRows(fixture.ptId), "环节预警", item.id),
                "★ 保存一条逾期环节后，钩子应立刻重算并生成环节预警（不需要手动点「重新计算」）");

        facilityAdminService.removeProcess(fixture.ptId, item.id, null);

        assertTrue(warningRows(fixture.ptId).isEmpty(),
                "★ 撤回环节进度后，钩子应重算并清掉这条预警（旧系统从不删，预警会一直挂着）");
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 固定日期格式（saveProcess 接受 yyyy-MM-dd 字符串） */
    private static String formatDate(Date date) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
