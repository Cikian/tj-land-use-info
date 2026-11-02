package org.jeecg.modules.land.stat.warning.support;

import org.jeecg.modules.land.stat.warning.entity.ProcessWarningLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @Description: 分级预警 · 等级匹配与预警值计算（纯函数，不依赖 Spring）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p><b>★ 为什么单独抽成一个纯函数类</b>：
 * 分级预警最容易出错、也最需要单测的就是「逾期 N 天 → 哪个等级」和
 * 「预警值怎么算」这两件事。它们混在 Service 里就要么得连库才能测，
 * 要么只能在集成测试里事务内造数验证，边界（1/10/11/20/21/30/31/100/120）
 * 会漏掉一半。抽出来之后 `new` 一个 List 就能测全部边界。
 * 因此本类**不注入任何 Bean、不读库、不读配置**，方法全静态。
 *
 * <p><b>★ 与旧代码的对齐关系</b>（对账时按这里逐行比）：
 * <pre>
 *   旧 ProcessWarningServiceImpl.getLevelForStage L733-741
 *     levelList2 = query(yjdjzxz &lt;= days)          // 下界过滤
 *     levelList  = filter(yjdjzdz &gt;= days)          // 上界过滤
 *     levelList.get(0)                              // ★ 集合顺序不定，且过滤完为空时
 *                                                   //   直接「没有等级」——逾期 120 天反而没预警
 *   旧 ProcessStatusController L158-168 / L200-205  // 同上，另外 days 只算到区间下界之上
 * </pre>
 * 新实现按「sort_no 升序扫描 + 显式兜底」重写，把旧代码的三个问题都堵住：
 * <ol>
 *   <li><b>超过最高上界不返回 null</b>（契约 §3.6 第 3 条）：逾期 120 天取最高级并标记
 *       {@code overflow}。逾期越久越不能没有预警，这是安全侧选择。</li>
 *   <li><b>区间有空洞时向下就近</b>（第 2 条）：把蓝色配成 1-10、黄色配成 15-20，
 *       逾期 12 天取蓝色，而不是「谁在集合里排第一」。</li>
 *   <li><b>低于最小下界返回 null</b>（第 4 条）：配置被改成从 5 起、逾期 2 天
 *       就没有可匹配的等级——此时**不生成预警**，因为这属于「配置认为还不算预警」，
 *       而不是「算法算不出来」。与第 3 条的区别是方向：超过上界要兜底（不能漏），
 *       低于下界要放行（不能误报）。</li>
 * </ol>
 *
 * <p>预警值统一是「实际 ÷ 标准」的比率，保留 4 位小数（库列 {@code decimal(10,4)}）。
 * 环节口径写成 {@code (stdDays + overdueDays) / stdDays}，与旧代码
 * {@code (processTime + finalDays) / processTime} **逐字等价**——
 * 保留这种写法只是为了对账时能一行行对回旧代码，两者数学上同一件事。
 */
public final class WarningLevelSupport {

    /** 预警值保留小数位（与 {@code t_process_warning.yj_value decimal(10,4)} 一致） */
    private static final int VALUE_SCALE = 4;

    /** 展示用百分比保留小数位（旧系统 {@code String.format("%.1f", yjz*100)}） */
    private static final int PERCENT_SCALE = 1;

    private WarningLevelSupport() {
    }

    // ==================================================================
    // 一、等级匹配（契约 §3.6）
    // ==================================================================

    /**
     * 匹配结果：等级（可能为 null）+ 是否溢出（超出最高等级上界）。
     *
     * <p><b>★ 为什么不直接返回 {@code ProcessWarningLevel}</b>：
     * 「没匹配到」有两种完全不同的原因——「逾期天数低于所有下界」（正常放行）与
     * 「等级表为空」（配置缺失，需要提示管理员）。前者返回实体 null 就够了，
     * 后者要额外告诉调用方「去配等级」；而「溢出」也需要单独标记，
     * 否则前端无法把「逾期 35 天判红」和「逾期 200 天判红」区分开。
     * 用一个结果对象把三种情形都表达清楚，调用方不用猜。
     */
    public static final class MatchResult {

        /** 命中的等级；未命中（含等级表为空）时为 null */
        private final ProcessWarningLevel level;

        /** 是否因为「超过所有等级的上界」而取的最高级 */
        private final boolean overflow;

        private MatchResult(ProcessWarningLevel level, boolean overflow) {
            this.level = level;
            this.overflow = overflow;
        }

        /** 未命中 */
        static MatchResult none() {
            return new MatchResult(null, false);
        }

        /** 命中 */
        static MatchResult hit(ProcessWarningLevel level, boolean overflow) {
            return new MatchResult(level, overflow);
        }

        public ProcessWarningLevel getLevel() {
            return level;
        }

        public boolean isOverflow() {
            return overflow;
        }

        /** 是否真的命中了某个等级 */
        public boolean isMatched() {
            return level != null;
        }

        @Override
        public String toString() {
            return "MatchResult{level=" + (level == null ? "null" : level.getLevelName())
                    + ", overflow=" + overflow + '}';
        }
    }

    /**
     * 按逾期工作日天数匹配预警等级（契约 §3.6 的五种情形）。
     *
     * <p>扫描顺序按 <b>{@code minDays} 升序</b>（{@code minDays} 相同再按 {@code sortNo}）。
     * 契约说的是「按 sort_no 升序（即 min_days 升序）」——两者正常配置下一致，
     * 但「区间不得重叠」的校验只在保存时生效，历史数据仍可能不一致；
     * 而 <b>匹配的语义是区间匹配</b>，所以以 {@code minDays} 为主键排序、
     * {@code sortNo} 只做并列时的次序，这样「向下就近」才是真的就近。
     *
     * @param levels      已启用的等级（{@code del_flag = 0}）；可为 null / 空
     * @param overdueDays 逾期工作日天数（> 0 才有意义；调用方负责「0 天不预警」）
     * @return 匹配结果；逾期天数低于所有下界或等级表为空时 {@code getLevel() == null}
     */
    public static MatchResult match(List<ProcessWarningLevel> levels, int overdueDays) {
        if (levels == null || levels.isEmpty()) {
            // 情形 ⑤：等级表为空 —— 返回 null，由重算回执给 levelMissing 提示
            return MatchResult.none();
        }
        List<ProcessWarningLevel> sorted = sortByRange(levels);
        if (sorted.isEmpty()) {
            // 传进来的都是 null（脏数据）——与「等级表为空」同等处理
            return MatchResult.none();
        }

        ProcessWarningLevel lastBelow = null;
        int highestMax = Integer.MIN_VALUE;
        for (ProcessWarningLevel level : sorted) {
            if (level == null) {
                continue;
            }
            int min = intOf(level.getMinDays(), Integer.MIN_VALUE);
            int max = intOf(level.getMaxDays(), Integer.MIN_VALUE);
            if (max > highestMax) {
                highestMax = max;
            }
            if (min <= overdueDays) {
                lastBelow = level;
                // 情形 ①：闭区间命中
                if (overdueDays <= max) {
                    return MatchResult.hit(level, false);
                }
            }
        }

        // 情形 ③：超过所有等级的上界 → 取最高级（升序最后一条），标记溢出，不返回 null
        if (highestMax != Integer.MIN_VALUE && overdueDays > highestMax) {
            return MatchResult.hit(sorted.get(sorted.size() - 1), true);
        }

        // 情形 ④：低于所有下界 → 不生成预警
        if (lastBelow == null) {
            return MatchResult.none();
        }

        // 情形 ②：区间空洞 → 向下就近，overflow = false
        return MatchResult.hit(lastBelow, false);
    }

    /**
     * 区间升序副本（不改调用方传进来的 List：它往往是 Service 里缓存的一份，
     * 就地排序会让「同一份等级在不同口径下顺序不同」这种难查的问题出现）。
     */
    private static List<ProcessWarningLevel> sortByRange(List<ProcessWarningLevel> levels) {
        List<ProcessWarningLevel> sorted = new ArrayList<>(levels.size());
        for (ProcessWarningLevel level : levels) {
            if (level != null) {
                sorted.add(level);
            }
        }
        Collections.sort(sorted, new Comparator<ProcessWarningLevel>() {
            @Override
            public int compare(ProcessWarningLevel a, ProcessWarningLevel b) {
                int byMin = Integer.compare(intOf(a.getMinDays(), Integer.MAX_VALUE),
                        intOf(b.getMinDays(), Integer.MAX_VALUE));
                if (byMin != 0) {
                    return byMin;
                }
                return Integer.compare(intOf(a.getSortNo(), Integer.MAX_VALUE),
                        intOf(b.getSortNo(), Integer.MAX_VALUE));
            }
        });
        return sorted;
    }

    // ==================================================================
    // 二、预警值（契约 §3.5）
    // ==================================================================

    /**
     * 环节口径的预警值：{@code (stdDays + overdueDays) / stdDays}。
     *
     * <p>与旧代码 {@code ProcessStatusController} L224
     * {@code (processTime + finalDays) / processTime} 逐字等价。
     *
     * @param stdDays     标准办理时长（工作日）；&lt;= 0 时按 1 算
     * @param overdueDays 逾期工作日天数
     * @return 比率，保留 4 位小数（1.1000 = 110%）
     */
    public static BigDecimal yjValueForItem(int stdDays, int overdueDays) {
        int std = normalizeStdDays(stdDays);
        int overdue = Math.max(overdueDays, 0);
        return BigDecimal.valueOf((long) std + overdue)
                .divide(BigDecimal.valueOf(std), VALUE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 阶段 / 总体口径的预警值：{@code actualDays / stdDays}。
     *
     * <p>旧代码 {@code getYjzForStage} L686 / L705 与 {@code getWarningMsgForWhole} L800
     * 都是「实际 ÷ 标准」，只是写法分散在两处；这里收口成一个方法。
     *
     * @param actualDays 实际历时（工作日）
     * @param stdDays    标准办理时长（工作日）；&lt;= 0 时按 1 算
     * @return 比率，保留 4 位小数
     */
    public static BigDecimal yjRatio(int actualDays, int stdDays) {
        int std = normalizeStdDays(stdDays);
        return BigDecimal.valueOf(Math.max(actualDays, 0))
                .divide(BigDecimal.valueOf(std), VALUE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 标准时长归一：{@code <= 0} 一律按 1 处理（契约 §3.1）。
     *
     * <p>旧库「项目竣工」环节的 {@code process_time} 曾是 0，直接拿去做分母会
     * {@code ArithmeticException}（BigDecimal 除零）或得到 Infinity；
     * 种子已改成 1，但**不能假设**所有环境都改过——导入、旧接口都可能再写进 0。
     *
     * @param stdDays 原始标准时长（可空、可负）
     * @return 归一后的天数（&gt;= 1）
     */
    public static int normalizeStdDays(Integer stdDays) {
        if (stdDays == null || stdDays <= 0) {
            return WarningFields.MIN_STD_DAYS;
        }
        return stdDays;
    }

    /**
     * 比率 → 展示用百分比（已 ×100，保留 1 位小数）。
     *
     * <p>契约要求后端同时返回 {@code yjValue}（比率）与 {@code yjPercent}，
     * 前端不要再乘一次——两边各乘一次就会出现 11000%。
     *
     * @param ratio 比率（可为 null）
     * @return 百分比数值（110.0 表示 110%）；入参为 null 时返回 null
     */
    public static BigDecimal percent(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        return ratio.multiply(BigDecimal.valueOf(100))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** 同 {@link #percent(BigDecimal)}，入参用 int 天数比，省掉调用方的装箱 */
    public static BigDecimal percentOfRatio(int actualDays, int stdDays) {
        return percent(yjRatio(actualDays, stdDays));
    }

    // ==================================================================
    // 三、内部工具
    // ==================================================================

    private static int intOf(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
