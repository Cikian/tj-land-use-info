package org.jeecg.modules.land.stat.warning;

import org.jeecg.modules.land.stat.warning.entity.ProcessWarningLevel;
import org.jeecg.modules.land.stat.warning.support.WarningLevelSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Description: 分级预警 · 等级匹配与预警值计算 单元测试（不连库、不起 Spring）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>守的是实现契约 §3.6（等级匹配五种情形）与 §3.5（预警值公式、stdDays 兜底、
 * percent 四舍五入）。对应验收清单 §8 第 1 项。
 *
 * <p><b>★ 为什么这些边界要单独做「不连库」的单测</b>：
 * 逾期天数 → 等级的边界（1/10/11/20/21/30/31/100）一共有 8 个点，
 * 在集成测试里造 8 套环节数据既慢又脆弱（要挑日期、碰节假日），
 * 而 {@link WarningLevelSupport} 是纯函数 —— 直接 {@code new} 一个 List 就能把
 * 全部边界 + 5 种异常配置一次测完。集成测试（{@code WarningRecalcTest}）只验
 * 「算法真的被接到了重算流程上」。
 *
 * <p><b>★ 断言的写法约定</b>：金额/比率一律 {@code compareTo} + {@code scale()} 两条，
 * 不用 {@code assertEquals(new BigDecimal("1.1000"), x)} ——
 * {@code BigDecimal.equals} 连 scale 一起比，1.1 与 1.1000 会判不相等，
 * 报出来的信息（「expected 1.1000 but was 1.1」）很容易被误读成「算错了」，
 * 而实际只是小数位没对齐。分开断言，失败时一眼能看出是哪一类问题。
 */
public class WarningLevelSupportTest {

    /** 种子口径的四个等级：blue 1-10 / yellow 11-20 / orange 21-30 / red 31-100 */
    private static final List<ProcessWarningLevel> SEED_LEVELS = Arrays.asList(
            level("蓝色预警", "blue", 1, 10, 1),
            level("黄色预警", "yellow", 11, 20, 2),
            level("橙色预警", "orange", 21, 30, 3),
            level("红色预警", "red", 31, 100, 4));

    // ==================================================================
    // 一、等级匹配：闭区间边界（契约 §3.6 情形 ①）
    // ==================================================================

    /**
     * 8 个边界点逐个钉住。
     *
     * <p>★ 为什么 10/11、20/21、30/31 各测两次：区间是**闭区间**，
     * 「上一档的上界」和「下一档的下界」是相邻的两个数，
     * 把 {@code <=} 写成 {@code <}（或把 {@code >=} 写反）只会在这六个点上露出来；
     * 只测 5/15/25/35 这种中间值，边界写错也照样「过」。
     */
    @Test
    public void matchHitsClosedRangeBoundaries() {
        assertMatch(1, "blue", false);
        assertMatch(10, "blue", false);
        assertMatch(11, "yellow", false);
        assertMatch(20, "yellow", false);
        assertMatch(21, "orange", false);
        assertMatch(30, "orange", false);
        assertMatch(31, "red", false);
        assertMatch(100, "red", false);
        // 抽查区间中间值，避免「只有边界对、中间错」的实现
        assertMatch(5, "blue", false);
        assertMatch(15, "yellow", false);
        assertMatch(25, "orange", false);
        assertMatch(65, "red", false);
    }

    // ==================================================================
    // 二、等级匹配：超过最高上界（契约 §3.6 情形 ③）
    // ==================================================================

    /**
     * 逾期 101 / 120 / 999 天 → 取最高级（红色）并标记 overflow。
     *
     * <p>★ 为什么这条不是「边界补充」而是独立用例：旧代码在
     * 「过滤完为空」时**直接返回没有等级**，于是逾期 120 天反而没有预警。
     * 新实现明确要求「逾期越久越不能没有预警」，所以这里必须断言
     * <b>既命中红色、又带 overflow 标记</b>（前端据此可以区分「刚进红档」和「红档爆表」）。
     */
    @Test
    public void matchFallsBackToHighestLevelWhenBeyondMaxBound() {
        assertMatch(101, "red", true);
        assertMatch(120, "red", true);
        assertMatch(999, "red", true);
    }

    // ==================================================================
    // 三、等级匹配：区间有空洞（契约 §3.6 情形 ②）
    // ==================================================================

    /**
     * 把蓝色配成 1-10、黄色配成 15-20，逾期 12 天 → 向下就近取蓝色，且不算 overflow。
     *
     * <p>★ 空洞是真实会发生的配置（中心改阈值时先改一档、过两天再改下一档），
     * 而「向下就近」是**安全侧**选择：宁可少报一档，也不要跳过 12 天不预警。
     * 旧代码在这种配置下取的是「集合里的第 0 条」，结果取决于集合顺序 ——
     * 同一份数据两次算可能得到不同等级。
     */
    @Test
    public void matchFallsBackDownwardWhenRangesHaveHole() {
        List<ProcessWarningLevel> holed = Arrays.asList(
                level("蓝色预警", "blue", 1, 10, 1),
                level("黄色预警", "yellow", 15, 20, 2));

        WarningLevelSupport.MatchResult inHole = WarningLevelSupport.match(holed, 12);
        assertNotNull(inHole.getLevel(), "落在空洞里也必须给出等级（向下就近），不能返回 null");
        assertEquals("blue", inHole.getLevel().getLevelCode(), "★ 逾期 12 天在 [1,10]+[15,20] 的空洞里应向下就近取蓝色");
        assertFalse(inHole.isOverflow(), "空洞属于「区间内的空档」，不是「超过最高上界」，不能标 overflow");

        // 空洞之后的第二个区间仍然正常命中
        assertEquals("yellow", WarningLevelSupport.match(holed, 15).getLevel().getLevelCode());
        // 空洞之前
        assertEquals("blue", WarningLevelSupport.match(holed, 10).getLevel().getLevelCode());
        // 超过最高上界 → 取最高一档（黄色）并标 overflow
        WarningLevelSupport.MatchResult beyond = WarningLevelSupport.match(holed, 22);
        assertEquals("yellow", beyond.getLevel().getLevelCode());
        assertTrue(beyond.isOverflow(), "逾期 22 天超过 [15,20] 的上界，应标记 overflow");
    }

    // ==================================================================
    // 四、等级匹配：低于最小下界（契约 §3.6 情形 ④）
    // ==================================================================

    /**
     * 配置被改成从 5 起，逾期 2 天 → null（不生成预警）；逾期 7 天 → 命中。
     *
     * <p>★ 这一档与「超过上界」方向相反，是**故意不对称**的：
     * 超过上界要兜底（不能漏报），低于下界要放行（不能误报）——
     * 配置说「5 天以内不算预警」，算法就应当尊重这个口径。
     */
    @Test
    public void matchReturnsNullWhenBelowLowestBound() {
        List<ProcessWarningLevel> fromFive = Collections.singletonList(
                level("蓝色预警", "blue", 5, 10, 1));

        WarningLevelSupport.MatchResult below = WarningLevelSupport.match(fromFive, 2);
        assertNull(below.getLevel(), "★ 逾期 2 天 < 最小下界 5，应按「还不算预警」返回 null");
        assertFalse(below.isMatched());
        assertFalse(below.isOverflow(), "返回 null 时不能同时标 overflow（两个语义互斥）");

        WarningLevelSupport.MatchResult hit = WarningLevelSupport.match(fromFive, 7);
        assertNotNull(hit.getLevel());
        assertEquals("blue", hit.getLevel().getLevelCode());
        // 恰好等于下界也要命中（闭区间）
        assertEquals("blue", WarningLevelSupport.match(fromFive, 5).getLevel().getLevelCode());
    }

    // ==================================================================
    // 五、等级匹配：空等级表（契约 §3.6 情形 ⑤）
    // ==================================================================

    /**
     * 等级表为空 / null / 全是 null 元素 → 一律 null。
     *
     * <p>重算侧据此给出 {@code levelMissing=true} 提示管理员去「预警等级维护」配阈值；
     * 这里只保证「不抛异常、不返回半个结果」—— 空表时抛 NPE 会让
     * 整批重算（1433 条配套）全部失败。
     */
    @Test
    public void matchReturnsNullWhenLevelTableMissing() {
        assertNull(WarningLevelSupport.match(null, 15).getLevel(), "null 等级表应返回 null 而不是抛 NPE");
        assertNull(WarningLevelSupport.match(new ArrayList<ProcessWarningLevel>(), 15).getLevel(),
                "空等级表应返回 null");
        assertNull(WarningLevelSupport.match(Collections.<ProcessWarningLevel>singletonList(null), 15).getLevel(),
                "只含 null 元素的脏列表应等价于空表");
    }

    // ==================================================================
    // 六、等级匹配：入参顺序不影响结果
    // ==================================================================

    /**
     * 等级表按 min_days 乱序传入，结果必须与升序一致。
     *
     * <p>★ 为什么值得单测：{@code selectEnabled()} 是按 {@code sort_no} 排的，
     * 而 {@code sort_no} 是人工维护的（表列 DEFAULT 0，前端留空就传 0）。
     * 一旦有人把「红色」的 sort_no 填成 10、「蓝色」漏填成 0，库里的顺序就不再是业务顺序；
     * 匹配必须自己按 {@code min_days} 重排，否则「向下就近」会就近到错误的档。
     */
    @Test
    public void matchIsIndependentOfInputOrder() {
        List<ProcessWarningLevel> reversed = Arrays.asList(
                level("红色预警", "red", 31, 100, 4),
                level("橙色预警", "orange", 21, 30, 3),
                level("黄色预警", "yellow", 11, 20, 2),
                level("蓝色预警", "blue", 1, 10, 1));

        assertEquals("blue", WarningLevelSupport.match(reversed, 3).getLevel().getLevelCode());
        assertEquals("yellow", WarningLevelSupport.match(reversed, 15).getLevel().getLevelCode());
        assertEquals("orange", WarningLevelSupport.match(reversed, 30).getLevel().getLevelCode());
        assertEquals("red", WarningLevelSupport.match(reversed, 200).getLevel().getLevelCode());

        // ★ 顺带钉住「不就地排序调用方的 List」：Service 里同一份等级在三种口径间复用，
        //   若匹配把入参排序了，后续逻辑拿到的顺序会被悄悄改掉。
        assertEquals("red", reversed.get(0).getLevelCode(), "match 不能就地修改入参 List 的顺序");
    }

    // ==================================================================
    // 七、预警值：环节口径（契约 §3.5）
    // ==================================================================

    /**
     * 环节预警值 = {@code (stdDays + overdueDays) / stdDays}，保留 4 位小数。
     *
     * <p>与旧代码 {@code ProcessStatusController} 的
     * {@code (processTime + finalDays) / processTime} 逐字等价 —— 保留的另一种写法是
     * {@code actualDays / stdDays}，两者语义相同，这里都用 {@code yjRatio} 交叉验证一次。
     */
    @Test
    public void yjValueForItemUsesOverdueOverStdDays() {
        assertRatio(WarningLevelSupport.yjValueForItem(20, 2), "1.1000", "标准 20 天、逾期 2 天 → 110%");
        assertRatio(WarningLevelSupport.yjValueForItem(10, 15), "2.5000", "标准 10 天、逾期 15 天 → 250%");
        // 不逾期时恰好 1.0000（不是 0，也不是空）
        assertRatio(WarningLevelSupport.yjValueForItem(10, 0), "1.0000", "没逾期时预警值应为 1.0000");
        // 负数逾期（理论上不会出现，但公式不能因此变负）
        assertRatio(WarningLevelSupport.yjValueForItem(10, -5), "1.0000", "负数逾期应按 0 处理");

        // 交叉验证：环节口径与阶段/总体口径是同一个语义（实际 ÷ 标准）
        assertEquals(0, WarningLevelSupport.yjValueForItem(20, 2)
                        .compareTo(WarningLevelSupport.yjRatio(22, 20)),
                "★ 环节口径 (std+overdue)/std 必须与阶段口径 actual/std 数值一致");
    }

    /**
     * {@code stdDays <= 0} 一律按 1 算（契约 §3.1 的兜底）。
     *
     * <p>★ 旧库「项目竣工」的 {@code process_time} 曾经是 0，直接拿去做分母会
     * {@code ArithmeticException}（BigDecimal 除零）或 Infinity；种子已改成 1，
     * 但导入模板与旧接口仍可能再写进 0 —— 公式必须自己兜住，不能假设数据干净。
     */
    @Test
    public void yjValueFallsBackWhenStdDaysIsNotPositive() {
        assertRatio(WarningLevelSupport.yjValueForItem(0, 2), "3.0000",
                "★ stdDays=0 时按 1 算：(1+2)/1 = 3.0000（除零兜底）");
        assertRatio(WarningLevelSupport.yjValueForItem(-7, 2), "3.0000", "负数标准时长同样按 1 算");
        assertRatio(WarningLevelSupport.yjRatio(30, 0), "30.0000", "★ 阶段口径 stdDays=0 时同样按 1 算");
        assertRatio(WarningLevelSupport.yjRatio(5, -1), "5.0000", "阶段口径负数标准时长同样按 1 算");
    }

    /**
     * 阶段 / 总体口径 = {@code actualDays / stdDays}；{@code actualDays = 0} 时为 0.0000。
     *
     * <p>★ {@code ratio(0, 0)} 常见于「配套一条进度都没有」：分母兜底成 1、
     * 分子是 0，结果 0.0000 而不是 NaN —— 前端把它乘 100 显示成 0.0%，
     * 不会出现「NaN%」（首页看板的 D-614 就是同一个坑）。
     */
    @Test
    public void yjRatioDividesActualByStdDays() {
        assertRatio(WarningLevelSupport.yjRatio(30, 20), "1.5000", "实际 30 / 标准 20 → 150%");
        assertRatio(WarningLevelSupport.yjRatio(0, 0), "0.0000", "★ 0/0 必须是 0.0000，不能抛异常也不能是 NaN");
        assertRatio(WarningLevelSupport.yjRatio(0, 20), "0.0000", "实际 0 天 → 0.0000");
        assertRatio(WarningLevelSupport.yjRatio(-3, 20), "0.0000", "负数实际历时按 0 处理");
    }

    // ==================================================================
    // 八、展示百分比与标准时长归一
    // ==================================================================

    /**
     * {@code percent} 已 ×100 且四舍五入到 1 位小数。
     *
     * <p>★ 契约要求后端同时返回「比率」与「百分比」两个字段，前端不再自己乘 ——
     * 两边各乘一次就会显示 11000%。这里固定住 1 位与 HALF_UP 的舍入口径
     * （旧系统 {@code String.format("%.1f", yjz*100)}）。
     */
    @Test
    public void percentScalesByHundredAndRoundsTo1Decimal() {
        assertPercent(WarningLevelSupport.percent(new BigDecimal("1.1000")), "110.0");
        assertPercent(WarningLevelSupport.percent(new BigDecimal("1.2345")), "123.5",
                "1.2345 ×100 = 123.45 → HALF_UP 到 1 位是 123.5");
        assertPercent(WarningLevelSupport.percent(new BigDecimal("2.5000")), "250.0");
        assertPercent(WarningLevelSupport.percent(BigDecimal.ZERO), "0.0");
        assertPercent(WarningLevelSupport.percentOfRatio(30, 20), "150.0",
                "阶段口径 percentOfRatio 应与 percent(yjRatio) 一致");
        assertNull(WarningLevelSupport.percent(null), "比率为 null 时百分比也应为 null，不要给 0（两者语义不同）");
    }

    /** {@code normalizeStdDays}：null / 0 / 负数 → 1，正数原样返回 */
    @Test
    public void normalizeStdDaysNeverReturnsZeroOrNull() {
        assertEquals(1, WarningLevelSupport.normalizeStdDays(null), "null 标准时长按 1 算");
        assertEquals(1, WarningLevelSupport.normalizeStdDays(0), "0 标准时长按 1 算（除零兜底）");
        assertEquals(1, WarningLevelSupport.normalizeStdDays(-5), "负数标准时长按 1 算");
        assertEquals(7, WarningLevelSupport.normalizeStdDays(7), "正数原样返回");
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 造一个等级实体（只填匹配需要的五个字段） */
    private static ProcessWarningLevel level(String name, String code, int minDays, int maxDays, int sortNo) {
        return new ProcessWarningLevel()
                .setLevelName(name)
                .setLevelCode(code)
                .setMinDays(minDays)
                .setMaxDays(maxDays)
                .setSortNo(sortNo);
    }

    /** 按种子四档断言「逾期 N 天 → 哪个 code + 是否 overflow」 */
    private static void assertMatch(int overdueDays, String expectedCode, boolean expectedOverflow) {
        WarningLevelSupport.MatchResult result = WarningLevelSupport.match(SEED_LEVELS, overdueDays);
        assertNotNull(result.getLevel(), "逾期 " + overdueDays + " 天应命中等级，实际没命中");
        assertEquals(expectedCode, result.getLevel().getLevelCode(),
                "逾期 " + overdueDays + " 天应命中「" + expectedCode + "」，实际 "
                        + result.getLevel().getLevelName());
        assertEquals(expectedOverflow, result.isOverflow(),
                "逾期 " + overdueDays + " 天的 overflow 标记应为 " + expectedOverflow);
    }

    /** 比率断言：数值 + 4 位小数（分开断言，失败时能分辨「算错」与「小数位不对」） */
    private static void assertRatio(BigDecimal actual, String expected, String why) {
        assertNotNull(actual, why + "：结果不应为 null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                why + "：期望 " + expected + "，实际 " + actual);
        assertEquals(4, actual.scale(), why + "：比率应保留 4 位小数（库列 decimal(10,4)），实际 " + actual);
    }

    /** 百分比断言：数值 + 1 位小数 */
    private static void assertPercent(BigDecimal actual, String expected) {
        assertPercent(actual, expected, "百分比口径不对");
    }

    private static void assertPercent(BigDecimal actual, String expected, String why) {
        assertNotNull(actual, why + "：结果不应为 null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                why + "：期望 " + expected + "，实际 " + actual);
        assertEquals(1, actual.scale(), why + "：百分比应保留 1 位小数，实际 " + actual);
    }
}
