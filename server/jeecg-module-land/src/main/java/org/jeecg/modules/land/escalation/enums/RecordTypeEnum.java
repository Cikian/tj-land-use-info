package org.jeecg.modules.land.escalation.enums;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Description: 提级论证-记录类型（审核意见 / 补充说明 / 其他）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>取值与 {@code t_escalation_record.record_type} 列注释逐字一致。
 * 与 {@link EscalationStatus} 一样，本枚举只做取值与合法性校验，不含任何流程语义
 * （{@code node_code} / {@code seq_no} 是二期上流程时的预留列，本期恒为 NULL）。
 */
public enum RecordTypeEnum {

    /** 审核意见：方案 2.3.3 第 4 项点名的「可批注审核意见」 */
    AUDIT_OPINION("审核意见"),

    /** 补充说明：对已有意见的补充，不改变结论 */
    SUPPLEMENT("补充说明"),

    /** 其他：兜底类型 */
    OTHER("其他");

    /** 记录类型文案（直接落库的中文值） */
    private final String value;

    RecordTypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 判断入参是否为合法的记录类型。
     *
     * @param value 前端传入的类型文案
     * @return true 合法；null / 空串 / 未知值一律 false
     */
    public static boolean isValid(String value) {
        return of(value) != null;
    }

    /** 按文案取枚举；取不到返回 null */
    public static RecordTypeEnum of(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        for (RecordTypeEnum type : values()) {
            if (type.value.equals(trimmed)) {
                return type;
            }
        }
        return null;
    }

    /** 全部合法取值 */
    public static List<String> allValues() {
        List<String> values = new ArrayList<>(values().length);
        for (RecordTypeEnum type : values()) {
            values.add(type.value);
        }
        return Collections.unmodifiableList(values);
    }

    /** 默认记录类型：审核意见（t_escalation_record.record_type 的 DDL 默认值） */
    public static String defaultValue() {
        return AUDIT_OPINION.value;
    }
}
