package org.jeecg.modules.land.data.process.enums;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @Description: 环节情况（环节进度状态）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>取值与 {@code sys_dict} 的 {@code land_process_status} 字典<b>必须逐字一致</b>
 * （sql/data/01_data_dict.sql 末尾有守卫查询比对），原因：
 * <ul>
 *   <li>字典供界面下拉与导出显示；</li>
 *   <li>本枚举驱动<b>状态配色</b>与<b>阶段汇总口径</b>。</li>
 * </ul>
 * 两边一旦漂移，就会出现「下拉里能选到某个值、但汇总/配色不认识它」——
 * 表现是阶段状态算错或标签空白，且很难查。所以两侧都登记，并用守卫查询兜底。
 *
 * <p><b>取值来源（实测旧库 {@code process_status.lcqk}）</b>：
 * 旧库表里只有「进行中 / 已完成」两种，但旧代码
 * （{@code ProcessStatusController.createSpecial} 的阶段汇总分支）里还判断了
 * 「未开启 / 不涉及」——说明这两个值在业务上是存在的，只是旧数据恰好没落库。
 * 本枚举把四个值都收齐，与旧代码的口径对齐。
 *
 * <p><b>★ 阶段汇总口径</b>（照搬旧代码，但把它的一个漏洞补上）：
 * 旧逻辑是「全不涉及 → 不涉及；全未开启 → 未开启；有进行中 → 进行中；全已完成 → 已完成」。
 * 漏洞在于：<b>一个事项都还没录（子集为空）时，四个分支全不成立</b>，
 * 旧代码会把阶段状态设成默认的「进行中」—— 明明什么都没开始，却显示进行中。
 * 本实现显式处理空集：没有子进度记录时阶段为「未开启」。
 */
public enum ProcessStatus {

    /** 未开启（默认值：什么都没录时就是这个） */
    NOT_STARTED("未开启"),
    /** 进行中 */
    RUNNING("进行中"),
    /** 已完成 */
    FINISHED("已完成"),
    /** 不涉及（阶段汇总时剔除，不参与「是否办完」的判断） */
    NOT_INVOLVED("不涉及");

    private final String value;

    ProcessStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** 全部取值（下拉与校验用，顺序即业务顺序） */
    public static List<String> allValues() {
        List<String> values = new ArrayList<>();
        for (ProcessStatus status : values()) {
            values.add(status.value);
        }
        return Collections.unmodifiableList(values);
    }

    /** 是否合法取值 */
    public static boolean isValid(String value) {
        return of(value) != null;
    }

    /** 按文本取枚举，未匹配返回 null */
    public static ProcessStatus of(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        for (ProcessStatus status : values()) {
            if (status.value.equals(trimmed)) {
                return status;
            }
        }
        return null;
    }

    /** 空值兜底为「未开启」（新增进度记录时的默认状态） */
    public static String defaultValue() {
        return NOT_STARTED.value;
    }

    /** 允许值提示文案，错误信息里用 */
    public static String allowedText() {
        return String.join(" / ", allValues());
    }

    /**
     * 由「本阶段下各事项的环节情况」归并出阶段状态。
     *
     * <p>规则（与旧代码一致，并补上它的空集漏洞）：
     * <ol>
     *   <li>一个事项都没有 → 未开启；</li>
     *   <li>全部为「不涉及」→ 不涉及（此时这个阶段对本项目整体无意义）；</li>
     *   <li>去掉「不涉及」后，全部「已完成」→ 已完成；</li>
     *   <li>去掉「不涉及」后，全部「未开启」→ 未开启；</li>
     *   <li>其余（既有未开启又有进行中/已完成）→ 进行中。</li>
     * </ol>
     *
     * @param childStatuses 本阶段下各事项的环节情况（可含 null 与「不涉及」）
     * @return 阶段状态文本
     */
    public static String rollUpStageStatus(List<String> childStatuses) {
        if (childStatuses == null || childStatuses.isEmpty()) {
            // ★ 旧代码在这里会落到默认的「进行中」，是错的：什么都没录 ≠ 正在办
            return NOT_STARTED.value;
        }
        int notInvolved = 0;
        int finished = 0;
        int running = 0;
        int notStarted = 0;
        for (String raw : childStatuses) {
            ProcessStatus status = of(raw);
            if (status == null) {
                // 取值缺失视为「未开启」，不因为一条脏数据把整个阶段判成进行中
                notStarted++;
                continue;
            }
            switch (status) {
                case NOT_INVOLVED:
                    notInvolved++;
                    break;
                case FINISHED:
                    finished++;
                    break;
                case RUNNING:
                    running++;
                    break;
                default:
                    notStarted++;
                    break;
            }
        }
        int effective = childStatuses.size() - notInvolved;
        if (effective <= 0) {
            return NOT_INVOLVED.value;
        }
        if (finished == effective) {
            return FINISHED.value;
        }
        if (notStarted == effective) {
            return NOT_STARTED.value;
        }
        if (running > 0) {
            return RUNNING.value;
        }
        return RUNNING.value;
    }

    /**
     * 阶段完成度百分比（0~100），供进度条展示。
     *
     * <p>「不涉及」的事项从分母里剔除 —— 若某阶段 3 个事项里 2 个不涉及、1 个已完成，
     * 该阶段应当是 100% 而不是 33%。
     */
    public static int stagePercent(List<String> childStatuses) {
        if (childStatuses == null || childStatuses.isEmpty()) {
            return 0;
        }
        int effective = 0;
        int finished = 0;
        for (String raw : childStatuses) {
            ProcessStatus status = of(raw);
            if (status == ProcessStatus.NOT_INVOLVED) {
                continue;
            }
            effective++;
            if (status == ProcessStatus.FINISHED) {
                finished++;
            }
        }
        if (effective <= 0) {
            // 全部不涉及：视为该阶段已完成（无需办理 = 已了结），给 100% 而不是 0%
            return 100;
        }
        return (int) Math.round(finished * 100.0 / effective);
    }

    /** 状态 → 前端标签颜色（与档案/台账模块的取值保持同一套 antd 预设色） */
    public static String tagColor(String value) {
        ProcessStatus status = of(value);
        if (status == null) {
            return null;
        }
        switch (status) {
            case FINISHED:
                return "green";
            case RUNNING:
                return "blue";
            default:
                // 未开启 / 不涉及 → 默认灰底
                return null;
        }
    }

    /** 状态 → 图表小圆点颜色 */
    public static String dotColor(String value) {
        ProcessStatus status = of(value);
        if (status == null) {
            return "#94a3b8";
        }
        switch (status) {
            case FINISHED:
                return "#10b981";
            case RUNNING:
                return "#2e7cf6";
            default:
                return "#94a3b8";
        }
    }

    /** 供测试与文档引用的顺序化取值 */
    public static List<String> ordered() {
        return Collections.unmodifiableList(Arrays.asList(
                NOT_STARTED.value, RUNNING.value, FINISHED.value, NOT_INVOLVED.value));
    }
}
