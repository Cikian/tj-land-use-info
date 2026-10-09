package org.jeecg.modules.land.data.imports.facility;

import org.apache.commons.lang.StringUtils;

/**
 * @Description: 配套信息批量导入 - 重复配套项目的处理策略
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 为什么不直接复用宗地导入的 {@code LandDuplicateStrategy}</b>：
 * 两者的<b>判重键不同</b> —— 宗地是「一个 {@code crzdbh}」（编号即唯一），
 * 配套是「{@code (crzdbh, ptxmmc)} 两个字段一起」（一块地上可以有很多配套，
 * 只要名称不重复）。两个模块共用同一个枚举，会让人误以为「策略也一样、判重也一样」，
 * 将来任一边调整语义（比如宗地改成按编号+批次判重）就会互相牵连。
 * 因此本模块自带一个同构的枚举，两份互相独立 —— 多 90 行代码，换两个模块互不干扰。
 *
 * <p><b>判重键的来历</b>：旧系统 {@code xjKjkfbSupportingFacilitiesController.importData()}
 * 的判重条件就是 {@code Cnd.where("crzdbh","=",crzdbh).and("ptxmmc","=",ptxmmc)}；
 * 同一段代码在「属性修改」接口里也是这么判的（旧库该表<b>没有唯一键</b>，
 * 唯一性完全靠应用层保证，所以这里必须做扎实）。
 *
 * <p><b>为什么做成三种策略而不是固定「跳过」</b>：
 * 旧系统对已存在的 (宗地, 名称) 一律跳过并计数。这在「日常补录新配套」时是对的，
 * 但在「年度台账更新」时就是灾难 —— 中心拿着一份改过投资估算、进度、六方单位的
 * 全量台账导进来，结果几百条全被跳过，还得逐条上页面手改。
 * 反过来，若默认覆盖，一次误传的模板会把已有数据抹掉。
 * 所以给三种策略让调用方按场景显式选，默认取最安全的
 * {@link #REJECT}（既不写也不改，只在回执里报出来）。
 */
public enum FacilityDuplicateStrategy {

    /**
     * 报错（默认）：已存在的 (宗地编号, 配套项目名称) 作为错误回执到行，整批不入库
     * （除非勾选跳过错误行）。
     *
     * <p>这是最安全的一档：用户能明确看到「哪几行撞了」，自己决定改名称还是换策略。
     */
    REJECT("reject", "重复即报错（不写库）"),

    /**
     * 跳过：已存在的 (宗地编号, 配套项目名称) 整行忽略并计入「跳过重复」，其余行正常入库。
     *
     * <p>等价于旧系统的行为，适合「只导新增配套」的日常补录。
     */
    SKIP("skip", "跳过重复行（只导新增）"),

    /**
     * 覆盖更新：已存在的 (宗地编号, 配套项目名称) 按「单元格有值才覆盖」更新
     * （空单元格保持原值）。
     *
     * <p>适合年度全量台账更新。★ 空单元格<b>不会</b>把库里的值清空 ——
     * 与宗地导入模块同一口径：否则用户拿一份只填了进度的补丁表导进去，
     * 会把六方单位、资金来源、录入人全部抹空。
     */
    UPDATE("update", "覆盖更新已有配套（空单元格不改动）");

    private final String code;
    private final String label;

    FacilityDuplicateStrategy(String code, String label) {
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
    public static FacilityDuplicateStrategy of(String value) {
        if (StringUtils.isBlank(value)) {
            return REJECT;
        }
        String trimmed = value.trim();
        for (FacilityDuplicateStrategy strategy : values()) {
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
        for (FacilityDuplicateStrategy strategy : values()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(strategy.code);
        }
        return sb.toString();
    }
}
