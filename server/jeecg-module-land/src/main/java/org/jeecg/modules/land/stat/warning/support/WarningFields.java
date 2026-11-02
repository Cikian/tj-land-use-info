package org.jeecg.modules.land.stat.warning.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @Description: 分级预警 · 字面量收口（分类/环节情况/字典编码）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>为什么单独一个常量类而不是散在 Service 里：
 * <ul>
 *   <li>{@code yjType} 的三个值是**唯一键的一部分**（{@code uk_pw_key}），
 *       写错一个字就会生成三条互不覆盖的重复预警，且不会有任何报错；</li>
 *   <li>前端筛选、导出表头、算法分支三处都要用到同一批字面量，
 *       集中一处才可能「改一次全生效」；</li>
 *   <li>{@code WarningLevelSupport} 是纯函数类，不能为了几个字符串去依赖 data 域枚举。</li>
 * </ul>
 *
 * <p>环节情况（{@code lcqk}）的取值与 {@code org.jeecg.modules.land.data.process.enums.ProcessStatus}
 * 逐字一致，本类只是**镜像**一份给纯函数/单测用；业务代码优先用枚举，
 * 避免两套定义各自演化（改一处忘一处）。
 */
public final class WarningFields {

    private WarningFields() {
    }

    // ==================================================================
    // 一、预警分类（唯一键的一部分，逐字不可改）
    // ==================================================================

    /** 环节预警：对象是单个事项（{@code t_process_configuration} 的叶子） */
    public static final String YJ_TYPE_ITEM = "环节预警";
    /** 阶段预警：对象是 6 个阶段之一 */
    public static final String YJ_TYPE_STAGE = "阶段预警";
    /** 总体预警：对象是整个配套项目（{@code lc_id = ''}） */
    public static final String YJ_TYPE_WHOLE = "总体预警";

    /** 全部分类，顺序即业务顺序（由细到粗） */
    public static final List<String> YJ_TYPES = Collections.unmodifiableList(
            Arrays.asList(YJ_TYPE_ITEM, YJ_TYPE_STAGE, YJ_TYPE_WHOLE));

    /** 总体预警的 {@code lc_id}：空串而不是 NULL（MySQL 唯一键允许多个 NULL） */
    public static final String WHOLE_LC_ID = "";

    /** 总体预警的 {@code lc_name}（前端「流程环节」列直接显示，比留空更好懂） */
    public static final String WHOLE_LC_NAME = "项目总体";

    // ==================================================================
    // 二、环节情况（与 ProcessStatus 枚举逐字一致）
    // ==================================================================

    /** 未开启：{@code lckssj} 为空时用它兜底判断 */
    public static final String LCQK_NOT_STARTED = "未开启";
    /** 进行中 */
    public static final String LCQK_RUNNING = "进行中";
    /** 已完成 */
    public static final String LCQK_FINISHED = "已完成";
    /** 不涉及：不参与阶段/总体求和 */
    public static final String LCQK_NOT_INVOLVED = "不涉及";

    // ==================================================================
    // 三、字典与兜底
    // ==================================================================

    /** 问题类型字典编码（与 {@code t_facility_process.czwtlx} 同一套取值） */
    public static final String DICT_PROCESS_ISSUE = "land_process_issue";

    /**
     * 问题类型兜底清单。
     *
     * <p>字典查不到时用它（新环境还没导字典），取值与
     * {@code sql/data/01_data_dict.sql} 的 {@code land_process_issue} 前 5 项一致。
     */
    public static final List<String> DEFAULT_PROBLEM_TYPES = Collections.unmodifiableList(
            Arrays.asList("审批问题", "资金问题", "权属问题", "管线问题", "地质问题"));

    /** 决策意见的「通用」问题类型：空串（对应表列 {@code problem_type} 默认值） */
    public static final String OPINION_GENERIC_TYPE = "";

    /** 决策意见正文长度上限（与表列 {@code varchar(1000)} 一致） */
    public static final int OPINION_MAX_LENGTH = 1000;

    // ==================================================================
    // 四、其它口径常量
    // ==================================================================

    /** 预警名称后缀（旧系统口径：「{配套项目名}预警分析」，三类共用） */
    public static final String YJ_NAME_SUFFIX = "预警分析";

    /** 标准时长的兜底值：{@code process_time <= 0} 时按 1 算（否则比率除零） */
    public static final int MIN_STD_DAYS = 1;
}
