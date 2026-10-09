package org.jeecg.modules.land.escalation.enums;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Description: 提级论证-办理状态（4 值固定枚举，方案 2.3.3 第 3 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>取值与 {@code t_escalation_project.status} 列注释逐字一致：
 * {@code 未办理 / 办理中 / 已办结 / 已归档}。
 *
 * <p><b>★ 本枚举不承载状态机</b>：实际论证流程在线下办理，系统只做「管理与记录」，
 * 状态由操作人员手工登记。因此这里<b>只提供取值与合法性校验</b>，
 * <b>不提供</b> {@code canTransferTo} 之类的流转顺序判定 —— 这是本模块的明确边界
 * （见设计文档 1.2「不做」清单与 4.3 第 1 条）。
 *
 * <p><b>为什么状态不入 sys_dict</b>：4 个取值固定且驱动前端标签配色，
 * 放代码枚举比放字典表更稳（设计文档 3.3）。
 */
public enum EscalationStatus {

    /** 未办理：项目刚录入，线下论证尚未开始（对应原型「待审批」） */
    PENDING("未办理", "项目已录入，线下论证尚未开始"),

    /** 办理中：线下论证进行中（对应原型「审批中」） */
    PROCESSING("办理中", "线下论证进行中"),

    /** 已办结：已有论证结论，结论记在 arg_result（对应原型「已通过」「已退回」） */
    FINISHED("已办结", "已有论证结论，具体结论见论证结果"),

    /** 已归档：材料整理完毕、不再变动 */
    ARCHIVED("已归档", "材料整理完毕，不再变动");

    /** 状态文案（直接落库的中文值） */
    private final String value;

    /** 状态说明（接口文档与前端提示用） */
    private final String description;

    EscalationStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断入参是否为合法的办理状态。
     *
     * @param value 前端传入的状态文案
     * @return true 合法；null / 空串 / 未知值一律 false
     */
    public static boolean isValid(String value) {
        return of(value) != null;
    }

    /** 按文案取枚举；取不到返回 null（不抛异常，方便调用方自定义提示语） */
    public static EscalationStatus of(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        for (EscalationStatus status : values()) {
            if (status.value.equals(trimmed)) {
                return status;
            }
        }
        return null;
    }

    /** 全部合法取值（拼错误提示与前端下拉用） */
    public static List<String> allValues() {
        List<String> values = new ArrayList<>(values().length);
        for (EscalationStatus status : values()) {
            values.add(status.value);
        }
        return Collections.unmodifiableList(values);
    }

    /** 默认状态：未办理（t_escalation_project.status 的 DDL 默认值） */
    public static String defaultValue() {
        return PENDING.value;
    }
}
