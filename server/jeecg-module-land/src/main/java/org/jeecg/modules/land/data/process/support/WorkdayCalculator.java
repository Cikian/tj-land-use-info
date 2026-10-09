package org.jeecg.modules.land.data.process.support;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.process.mapper.NonWorkingDayMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 工作日日历 - 环节「预计结束时间」推算
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★★ 为什么重写旧算法</b>：旧 {@code ProcessStatusController.getYjjssj}
 * 用的是「递归 + 补偿」：
 * <pre>
 *   getEndDay(time, num):
 *     end = time + num 天
 *     n = (time, end] 区间内的非工作日个数
 *     if num == (end - time) - n:  return end          // 已经够了
 *     else:                        return getEndDay(end, n)   // ★ 把「非工作日个数」当成新的补偿天数
 * </pre>
 * 问题在于递归调用时传的是 {@code (新终点, 非工作日个数)} ——
 * 语义从「还要加 num 个工作日」变成了「还要加 n 天」，且每次递归都以
 * <b>已经顺延过的终点</b>为起点再查一遍非工作日，多个节假日区间相连时天数会滚雪球。
 * 春节、国庆这种长假跨区间时算出来的日期明显偏晚。
 *
 * <p>本实现改为最直白的「逐日推进」：
 * <pre>
 *   从开始日往后一天一天走，遇到工作日就计数 +1，计到 N 就停。
 * </pre>
 * 语义清晰、可单测、结果与「非工作日分成几个区间」无关。
 * 代价是循环次数 = N + 区间内非工作日数（通常几十次），可忽略。
 *
 * <p><b>★ 数据时效性是本类必须处理的现实问题</b>：
 * {@code t_non_working_day} 实测只覆盖 2023 年（116 行），2024~2027 全空。
 * 若直接「表里查不到就当工作日」，2026 年的春节七天会被算成工作日，
 * 预计结束时间系统性偏早、逾期预警提前一片。
 * 所以算法分三层：
 * <ol>
 *   <li><b>人工维护优先</b>：表里查到的日期一律视为非工作日；</li>
 *   <li><b>周末兜底</b>：无论表里有没有数据，周六日一律算非工作日
 *       （旧表里 2023 的周末本来也都在，但未来年份没有）；</li>
 *   <li><b>法定节假日兜底</b>：内置 2024~2027 的春节/国庆长假日期段，
 *       让没有维护数据的年份也不至于离谱；</li>
 * </ol>
 * 并在返回值里给出 {@code calendarBased} 标记，让前端能提示
 * 「该年份未维护节假日日历，结果按周末 + 内置法定节假日估算」。
 */
@Slf4j
@Component
public class WorkdayCalculator {

    /** 日期格式 */
    private static final String PATTERN = "yyyy-MM-dd";

    /**
     * 内置法定节假日起止（{@code yyyy-MM-dd ~ yyyy-MM-dd}，含首尾）。
     *
     * <p>★ 这里<b>只放长假</b>（春节、国庆），不放清明/端午/中秋这类 1 天或 3 天的小长假 ——
     * 后者的调休规则每年都变，硬编码会引入错误；而长假对「预计结束时间」的影响
     * （7 天量级）远大于小长假（1~3 天）。
     *
     * <p>★ 这些日期是<b>兜底估算</b>，不是权威数据：中心在
     * 「系统管理 → 非工作日日历」按年维护后，表内数据优先，本表自动失效。
     */
    private static final String[] BUILTIN_HOLIDAYS = {
            // 2024
            "2024-02-10~2024-02-17", "2024-10-01~2024-10-07",
            // 2025
            "2025-01-28~2025-02-04", "2025-10-01~2025-10-08",
            // 2026
            "2026-02-16~2026-02-23", "2026-10-01~2026-10-08",
            // 2027
            "2027-02-05~2027-02-12", "2027-10-01~2027-10-08",
    };

    @Autowired
    private NonWorkingDayMapper nonWorkingDayMapper;

    /** 表内已维护的年份（懒加载 + 缓存，避免每次推算都查库） */
    private volatile Set<Integer> maintainedYears;

    /** 表内维护的非工作日（懒加载 + 缓存，key = yyyy-MM-dd） */
    private volatile Set<String> maintainedDays;

    // ==================================================================
    // 一、对外主方法
    // ==================================================================

    /**
     * 从开始日往后推 N 个工作日，得到预计结束时间。
     *
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param days      标准办理时长（工作日天数）；null 或 &lt;=0 时返回开始日当天
     * @return 含 {@code endDate}（yyyy-MM-dd）/ {@code workdays} / {@code calendarBased} 的结果
     */
    public Map<String, Object> plusWorkdays(String startDate, Integer days) {
        Date start = parseDate(startDate);
        if (start == null) {
            throw new JeecgBootException("开始时间「" + startDate + "」不是可识别的日期，请用 yyyy-MM-dd");
        }
        int target = days == null || days <= 0 ? 0 : days;
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(start);
        int counted = 0;
        // 安全阀：最多往后走 3650 天（10 年）。正常 N ≤ 60，
        // 这个上限只为挡住「数据异常导致死循环」——没有上限的 while 是定时炸弹。
        int guard = 0;
        while (counted < target && guard < 3650) {
            cursor.add(Calendar.DATE, 1);
            guard++;
            if (isWorkday(cursor.getTime())) {
                counted++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startDate", format(start));
        result.put("workdays", target);
        result.put("endDate", format(cursor.getTime()));
        result.put("actualWorkdays", counted);
        // ★ calendarBased = 「本次推算命中了该年份的人工维护日历」。
        //
        //   语义必须是这个，不能写成 !isYearMaintained ——
        //   后者（初版实现）在「表里没有任何数据」时返回 true，
        //   字面意思成了「结果基于日历」，而事实恰恰相反：
        //   日历越空、越不该说结果基于日历。前端据此提示
        //   「该年份未维护节假日日历，结果按周末 + 内置法定节假日估算」，
        //   标记反了就会把「没有日历」说成「有日历」，把风险提示彻底盖掉。
        result.put("calendarBased", isYearMaintained(start));
        result.put("maintainedYears", maintainedYears());
        return result;
    }

    /**
     * 某日期是否为工作日。
     *
     * <p>顺序：表内人工维护数据 → 周末 → 内置法定节假日。
     * 表里明确标为工作日的日期（如果有）会覆盖周末判断 ——
     * 这就是「调休上班的周六」这种情况，人工维护的优先级必须最高。
     */
    public boolean isWorkday(Date date) {
        if (date == null) {
            return false;
        }
        String key = format(date);
        loadCacheIfNeeded();
        // ① 人工维护优先：在「非工作日」集合里 → 非工作日
        if (maintainedDays != null && maintainedDays.contains(key)) {
            return false;
        }
        // ② 周末
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return false;
        }
        // ③ 内置法定节假日（仅当该年没有人工维护数据时才用，避免与权威数据打架）
        if (!isYearMaintained(date) && inBuiltinHoliday(key)) {
            return false;
        }
        return true;
    }

    /** 该日期所在年份是否已人工维护非工作日数据 */
    public boolean isYearMaintained(Date date) {
        if (date == null) {
            return false;
        }
        loadCacheIfNeeded();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return maintainedYears != null && maintainedYears.contains(calendar.get(Calendar.YEAR));
    }

    /** 已维护的年份列表（页面提示「哪些年还没维护」用） */
    public Set<Integer> maintainedYears() {
        loadCacheIfNeeded();
        return maintainedYears == null ? Collections.<Integer>emptySet() : maintainedYears;
    }

    /** 清空缓存（中心维护完日历后调一次；也供测试重置） */
    public void refreshCache() {
        this.maintainedDays = null;
        this.maintainedYears = null;
    }

    // ==================================================================
    // 二、内部
    // ==================================================================

    /** 懒加载表内数据（双重检查，避免并发重复查库） */
    private void loadCacheIfNeeded() {
        if (maintainedDays != null) {
            return;
        }
        synchronized (this) {
            if (maintainedDays != null) {
                return;
            }
            Set<String> days = new HashSet<>();
            Set<Integer> years = new HashSet<>();
            try {
                List<String> rows = nonWorkingDayMapper.selectAllDates();
                if (rows != null) {
                    for (String row : rows) {
                        if (row == null || row.trim().isEmpty()) {
                            continue;
                        }
                        // 兼容「2023-01-01」与「20230101」两种存法
                        String normalized = normalizeDate(row);
                        if (normalized != null) {
                            days.add(normalized);
                            years.add(Integer.valueOf(normalized.substring(0, 4)));
                        }
                    }
                }
            } catch (Exception e) {
                // ★ 表不存在或查库失败时不能抛：日历只是「让结果更准」，
                //   不该让「录入环节进度」这件事直接失败。退化为「只剔周末」。
                log.warn("读取非工作日日历失败，将退化为「只剔周末 + 内置法定节假日」：{}", e.getMessage());
            }
            maintainedDays = days;
            maintainedYears = years;
            log.info("非工作日日历已加载：{} 天，覆盖年份 {}", days.size(), years);
        }
    }

    /** 归一化为 yyyy-MM-dd，无法识别返回 null */
    private static String normalizeDate(String raw) {
        String value = raw.trim().replace('/', '-').replace('.', '-');
        if (value.matches("\\d{8}")) {
            return value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6, 8);
        }
        if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            String[] parts = value.split("-");
            return parts[0] + "-" + pad(parts[1]) + "-" + pad(parts[2]);
        }
        return null;
    }

    private static String pad(String value) {
        return value.length() == 1 ? "0" + value : value;
    }

    /** 是否落在内置法定节假日区间内 */
    private static boolean inBuiltinHoliday(String dateKey) {
        for (String range : BUILTIN_HOLIDAYS) {
            int tilde = range.indexOf('~');
            if (tilde < 0) {
                continue;
            }
            String from = range.substring(0, tilde);
            String to = range.substring(tilde + 1);
            // 用字符串比较即可：yyyy-MM-dd 的字典序与时间序一致，
            // 比每次 parse 成 Date 再比更快，也没有时区问题
            if (dateKey.compareTo(from) >= 0 && dateKey.compareTo(to) <= 0) {
                return true;
            }
        }
        return false;
    }

    private static Date parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String value = text.trim().replace('/', '-').replace('.', '-');
        for (String pattern : new String[]{"yyyy-MM-dd", "yyyy-M-d", "yyyyMMdd"}) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern);
                format.setLenient(false);
                return format.parse(value);
            } catch (ParseException ignored) {
                // 试下一种
            }
        }
        return null;
    }

    private static String format(Date date) {
        return date == null ? null : new SimpleDateFormat(PATTERN).format(date);
    }
}
