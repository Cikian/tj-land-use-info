package org.jeecg.modules.land.archive.ledger.enums;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Description: 道路设施验收及移交资料台账-状态（4 值固定枚举，方案 2.3.2 第 7 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>取值与 {@code t_road_acceptance_ledger.status} 列注释逐字一致：
 * {@code 未验收 / 验收中 / 已验收 / 已移交}。
 *
 * <p><b>★ 本枚举不承载状态机</b>：台账是「记录型」的，验收与移交都是线下办完才回系统登记，
 * 因此这里<b>只提供取值与合法性校验</b>，不提供 {@code canTransferTo} 之类的流转判定。
 * 与提级论证模块的 {@code EscalationStatus} 是同一处理口径。
 *
 * <p><b>为什么状态不入 sys_dict</b>：4 个取值固定，且驱动前端标签配色
 * （灰/蓝/绿/青）与台账顶部 tab 的角标，放代码枚举比放字典表更稳，
 * 避免后台误改导致「状态值与配色/统计口径不一致」。验收类型与验收结果则入字典
 * （见 {@code sql/ledger/02_ledger_dict.sql}），因为那两组措辞中心随时可能调整。
 *
 * <p><b>★ 迁移时的推导规则</b>（见 {@code sql/ledger/05_migrate_road_facilities.sql}）：
 * <pre>
 *   旧表 sfyj='是'                        → 已移交
 *   旧表 sfjg='是'                        → 已验收
 *   旧表 jgwj='是' 或 yjwj='是'（但未竣工）→ 验收中
 *   其余                                  → 未验收
 * </pre>
 * 实测线上库分布：未验收 917 / 已验收 337 / 已移交 84 / 验收中 2（合计 1340）。
 */
public enum LedgerStatus {

    /** 未验收：道路尚未竣工验收 */
    NOT_ACCEPTED("未验收", "道路尚未竣工验收"),

    /** 验收中：已在办理验收或资料归集，尚未形成验收结论 */
    ACCEPTING("验收中", "正在办理验收或归集竣工/移交资料"),

    /** 已验收：已通过竣工验收，尚未移交 */
    ACCEPTED("已验收", "已通过竣工验收，尚未办理移交"),

    /** 已移交：已办理移交（接收管养单位接管） */
    HANDED_OVER("已移交", "已办理移交，接收管养单位已接管");

    /** 状态文案（直接落库的中文值） */
    private final String value;

    /** 状态说明（前端提示与接口文档用） */
    private final String description;

    LedgerStatus(String value, String description) {
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
     * 判断入参是否为合法的台账状态。
     *
     * @param value 前端传入的状态文案
     * @return true 合法；null / 空串 / 未知值一律 false
     */
    public static boolean isValid(String value) {
        return of(value) != null;
    }

    /** 按文案取枚举；取不到返回 null（不抛异常，方便调用方自定义提示语） */
    public static LedgerStatus of(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        for (LedgerStatus status : values()) {
            if (status.value.equals(trimmed)) {
                return status;
            }
        }
        return null;
    }

    /** 全部合法取值（拼错误提示与前端下拉用） */
    public static List<String> allValues() {
        List<String> values = new ArrayList<>(values().length);
        for (LedgerStatus status : values()) {
            values.add(status.value);
        }
        return Collections.unmodifiableList(values);
    }

    /** 默认状态：未验收（t_road_acceptance_ledger.status 的 DDL 默认值） */
    public static String defaultValue() {
        return NOT_ACCEPTED.value;
    }
}
