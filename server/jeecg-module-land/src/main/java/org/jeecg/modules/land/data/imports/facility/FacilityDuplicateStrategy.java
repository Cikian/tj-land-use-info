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
 * <p><b>为什么做成多种策略而不是固定「跳过」</b>：
 * 旧系统对已存在的 (宗地, 名称) 一律跳过并计数。这在「日常补录新配套」时是对的，
 * 但在「年度台账更新」时就是灾难 —— 中心拿着一份改过投资估算、进度、六方单位的
 * 全量台账导进来，结果几百条全被跳过，还得逐条上页面手改。
 * 反过来，若只有覆盖、没有「只导新增」，一次误传的模板会把已有数据抹掉。
 * 所以给多种策略让调用方按场景显式选。
 *
 * <p><b>★ 默认值从 {@link #REJECT} 改为 {@link #UPSERT}（2026-11 整改 P1-06）</b>：
 * 实测中心交上来的台账<b>绝大多数是「既有新增、又有更新」的混合批次</b>
 * （一块地上补几行新配套、同时改几行已有配套的进度与投资）。
 * 原来默认「重复即报错」意味着这类文件必先被整批判红、再手工拆成两份表分别导入，
 * 而拆表正是最容易拆错（丢行 / 重复行）的一步。
 * 改为默认 upsert 后「一份文件一次导入」即可完成，且重复项<b>不会被重复插入</b>
 * （判重键仍是 (crzdbh, ptxmmc)，命中就更新、没命中才新增）。
 *
 * <p>为什么可以不担心「误覆盖」：upsert 的更新口径是<b>按非空单元格更新</b>
 * —— 文件里空着的列一律保持库内原值（见 {@link #UPSERT}）。
 * 只要用户是按模板填的那几列，未填的六方单位、资金来源、录入人不会被动到。
 * 「既不写也不改」的保守诉求仍由 {@link #REJECT} 提供（显式选它即可）。
 *
 * <p>另：默认策略变化<b>不影响</b>「宗地必须已存在」这条安全校验
 * （{@code allowOrphan} 默认仍为 false），避免为了少点几下就放进一批孤儿配套。
 */
public enum FacilityDuplicateStrategy {

    /**
     * 新增 + 更新（默认）：已存在的 (宗地编号, 配套项目名称) 按<b>非空单元格</b>更新，
     * 库里没有的行新增；同一批文件可同时完成新增与更新，且不会重复插入。
     *
     * <p>★ 空单元格<b>不会</b>把库里的值清空 —— 与宗地导入模块同一口径：否则用户拿一份
     * 只填了进度的补丁表导进去，会把六方单位、资金来源、录入人全部抹空。
     *
     * <p>这是默认档，理由见类注释「默认值从 REJECT 改为 UPSERT」。
     */
    UPSERT("upsert", "新增并更新已有配套（空单元格不改动）"),

    /**
     * 报错：已存在的 (宗地编号, 配套项目名称) 作为错误回执到行，整批不入库
     * （除非勾选跳过错误行）。
     *
     * <p>最保守的一档：用户能明确看到「哪几行撞了」，自己决定改名称还是换策略。
     * 2026-11 之前它是默认值，现在降级为可选项。
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
     * <p>语义与 {@link #UPSERT} 在「已存在的行」上完全一致，差别只在<b>名字与默认值</b>：
     * 2026-11 之前它是「年度台账更新」的唯一选择，历史调用方（老前端 / 脚本 / 测试）
     * 仍在传 {@code update}，因此<b>保留不删</b>——删掉会让这些调用方直接报
     * 「不支持的重复处理策略」，而它们的意图与 upsert 完全相同。
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
     * <p>兼容三种写法：{@code code}（upsert/reject/skip/update）、枚举名（UPSERT/REJECT/…）、
     * 以及空值（默认 {@link #UPSERT}）。
     *
     * <p>★ 空值走 {@link #UPSERT} 与 Service 里的兜底（{@code normalize}）必须一致：
     * 前端老版本、第三方脚本、curl 调用都可能不传这个参数，
     * 「不传」与「传 upsert」必须是同一个业务动作，否则同一份文件用两种方式调用会得到两种结果。
     */
    public static FacilityDuplicateStrategy of(String value) {
        if (StringUtils.isBlank(value)) {
            return UPSERT;
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

    /** 「upsert / reject / skip / update」，错误提示里用 */
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
