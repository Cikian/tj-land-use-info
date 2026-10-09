package org.jeecg.modules.land.archive.handover.enums;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Description: 道路交付及养护协议移交事项-状态（3 值固定枚举，方案 2.3.2 第 6 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>取值与 {@code t_road_handover.status} 列注释逐字一致：{@code 待移交 / 移交中 / 已移交}。
 *
 * <p><b>不承载状态机</b>：交付与移交都是线下办完才回系统登记，这里只做取值校验。
 * 与台账模块的 {@code LedgerStatus} 同一处理口径。
 *
 * <p><b>为什么状态不入字典</b>：3 个取值固定，驱动前端标签配色（灰/橙/绿）与列表筛选，
 * 放代码枚举比放字典表更稳；而「移交类型」（道路交付/养护协议/正式移交）措辞可能调整，
 * 因此入字典 {@code land_road_handover_type}。
 */
public enum HandoverStatus {

    /** 待移交：已列入移交计划，尚未办理 */
    PENDING("待移交", "已列入移交计划，尚未办理"),

    /** 移交中：正在办理移交（含签订养护协议） */
    PROCESSING("移交中", "正在办理移交，或正在签订养护协议"),

    /** 已移交：已办理移交、接收管养单位已接管 */
    HANDED_OVER("已移交", "已办理移交，接收管养单位已接管");

    private final String value;
    private final String description;

    HandoverStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static boolean isValid(String value) {
        return of(value) != null;
    }

    public static HandoverStatus of(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        for (HandoverStatus status : values()) {
            if (status.value.equals(trimmed)) {
                return status;
            }
        }
        return null;
    }

    public static List<String> allValues() {
        List<String> values = new ArrayList<>(values().length);
        for (HandoverStatus status : values()) {
            values.add(status.value);
        }
        return Collections.unmodifiableList(values);
    }

    /** 默认状态：待移交（DDL 默认值） */
    public static String defaultValue() {
        return PENDING.value;
    }
}
