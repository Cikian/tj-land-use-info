package org.jeecg.modules.land.data.imports;

import org.apache.commons.lang.StringUtils;

/**
 * @Description: 经营性用地批量导入 - 重复宗地编号的处理策略
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>为什么要做成策略而不是固定「跳过」</b>：
 * 旧系统 {@code importData()} 对已存在的 {@code crzdbh} 一律跳过并计数。
 * 这在「日常补录新地块」时是对的，但在「年度数据更新」时就是灾难 ——
 * 中心拿着一份改过面积、金额、受让人的全量台账导进来，结果 800 条全被跳过，
 * 还得逐条上页面手改。反过来，如果默认就覆盖，一次误传的模板会把已有数据抹掉。
 * 所以给三种策略，让调用方按场景显式选，默认取最安全的
 * {@link #REJECT}（既不写也不改，只在回执里报出来）。
 */
public enum LandDuplicateStrategy {

    /**
     * 报错（默认）：已存在的宗地编号作为错误回执到行，整批不入库（除非勾选跳过错误行）。
     *
     * <p>这是最安全的一档：用户能明确看到「哪几行撞了」，自己决定改编号还是换策略。
     */
    REJECT("reject", "重复即报错（不写库）"),

    /**
     * 跳过：已存在的宗地编号整行忽略并计入「跳过重复」，其余行正常入库。
     *
     * <p>等价于旧系统的行为，适合「只导新增地块」的日常补录。
     */
    SKIP("skip", "跳过重复行（只导新增）"),

    /**
     * 覆盖更新：已存在的宗地编号按「单元格有值才覆盖」更新（空单元格保持原值）。
     *
     * <p>适合年度全量台账更新。★ 空单元格<b>不会</b>把库里的值清空 ——
     * 与台账补录模块同一口径，避免一次导入把没填的列全抹成空。
     */
    UPDATE("update", "覆盖更新已有宗地（空单元格不改动）");

    private final String code;
    private final String label;

    LandDuplicateStrategy(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 前端传参 → 枚举。
     *
     * <p>兼容三种写法：{@code code}（reject/skip/update）、枚举名（REJECT/SKIP/UPDATE）、
     * 以及空值（默认 {@link #REJECT}）。
     */
    public static LandDuplicateStrategy of(String value) {
        if (StringUtils.isBlank(value)) {
            return REJECT;
        }
        String trimmed = value.trim();
        for (LandDuplicateStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(trimmed) || strategy.name().equalsIgnoreCase(trimmed)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("不支持的重复处理策略：" + value
                + "（可选 " + allCodes() + "）");
    }

    /** 「reject / skip / update」，错误提示里用 */
    public static String allCodes() {
        StringBuilder sb = new StringBuilder();
        for (LandDuplicateStrategy strategy : values()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(strategy.code);
        }
        return sb.toString();
    }
}
