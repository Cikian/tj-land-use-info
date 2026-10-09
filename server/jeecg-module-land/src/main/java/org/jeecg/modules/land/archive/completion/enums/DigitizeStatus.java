package org.jeecg.modules.land.archive.completion.enums;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Description: 竣工验收项目历史工程资料数字化档案-数字化状态（3 值固定枚举，方案 2.3.2 第 8 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>取值与 {@code t_completion_archive.digitize_status} 列注释逐字一致：
 * {@code 未数字化 / 数字化中 / 已数字化}。
 *
 * <p><b>★ 本枚举不承载状态机</b>：历史工程资料的数字化是线下加工，完成后回系统登记，
 * 因此这里<b>只提供取值与合法性校验</b>，不做「未数字化 → 数字化中 → 已数字化」
 * 的顺序判定（业务上完全可能一次性把一批资料整体标成已数字化）。
 *
 * <p><b>为什么状态不入 sys_dict</b>：3 个取值固定，且驱动前端标签配色（灰/蓝/绿）与
 * 统计口径（数字化进度、已数字化率）；放代码枚举比放字典表更稳，
 * 避免后台误改导致「状态值与配色/统计口径不一致」。
 * 项目类型与保管期限则入字典（措辞可能被中心调整）。
 *
 * <p>本模块的 {@code digitize_status} 与第 7 项台账模块的 {@code status} 是同一处理口径。
 */
public enum DigitizeStatus {

    /** 未数字化：已建账，尚未开始扫描加工 */
    NOT_STARTED("未数字化", "已建账，尚未开始扫描加工"),

    /** 数字化中：扫描/著录加工进行中 */
    PROCESSING("数字化中", "扫描或著录加工进行中"),

    /** 已数字化：加工完成，扫描件已挂接到本档案（archive_id 非空） */
    FINISHED("已数字化", "加工完成，扫描件已挂接到本档案");

    /** 状态文案（直接落库的中文值） */
    private final String value;

    /** 状态说明（前端提示与接口文档用） */
    private final String description;

    DigitizeStatus(String value, String description) {
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
     * 判断入参是否为合法的数字化状态。
     *
     * @param value 前端传入的状态文案
     * @return true 合法；null / 空串 / 未知值一律 false
     */
    public static boolean isValid(String value) {
        return of(value) != null;
    }

    /** 按文案取枚举；取不到返回 null（不抛异常，方便调用方自定义提示语） */
    public static DigitizeStatus of(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        for (DigitizeStatus status : values()) {
            if (status.value.equals(trimmed)) {
                return status;
            }
        }
        return null;
    }

    /** 全部合法取值（拼错误提示与前端下拉用） */
    public static List<String> allValues() {
        List<String> values = new ArrayList<>(values().length);
        for (DigitizeStatus status : values()) {
            values.add(status.value);
        }
        return Collections.unmodifiableList(values);
    }

    /** 默认状态：未数字化（t_completion_archive.digitize_status 的 DDL 默认值） */
    public static String defaultValue() {
        return NOT_STARTED.value;
    }
}
