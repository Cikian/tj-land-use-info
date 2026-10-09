package org.jeecg.modules.land.escalation.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 提级论证统计结果（方案 2.3.3 第 2 项：按论证结果等多维查询统计）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version V1.0
 *
 * <p>对应设计文档 5.5 的 5 张统计图，每个统计接口返回一个
 * {@code List<NameCount>}，本 VO 是它们的容器（也便于将来一次性返回多张图）：
 * <ul>
 *   <li>{@link #byResult} —— 按论证结果分布（饼图）；</li>
 *   <li>{@link #byDept} —— 按申报单位排行（条形图）；</li>
 *   <li>{@link #byXzqh} —— 按行政区划分布（条形图）；</li>
 *   <li>{@link #byMonth} —— 按申报时间趋势（折线图）；</li>
 *   <li>{@link #byType} —— 按项目类型分布（环形图）。</li>
 * </ul>
 *
 * <p>统计与列表<b>共用同一套查询条件</b>（{@code EscalationQueryDTO}），
 * 所以「图上的总数」与「列表的条数」永远对得上。
 * v1 设计里的「平均审批时长」依赖流转表，本期已去掉（不上流程，没有时长可算）。
 */
@Data
public class EscalationStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前条件下的项目总数（5 张图的分母） */
    private long total;

    /** 按论证结果统计 */
    private List<NameCount> byResult = new ArrayList<>();

    /** 按申报单位统计 */
    private List<NameCount> byDept = new ArrayList<>();

    /** 按行政区划统计 */
    private List<NameCount> byXzqh = new ArrayList<>();

    /** 按申报月份（yyyy-MM）统计 */
    private List<NameCount> byMonth = new ArrayList<>();

    /** 按项目类型统计 */
    private List<NameCount> byType = new ArrayList<>();

    /**
     * @Description: 通用「名称 + 数量」统计项
     *
     * <p>与档案模块的 {@code ArchiveStatVO.NameCount} 完全同构，
     * 前端统计图组件可以复用同一套解析逻辑。
     */
    @Data
    public static class NameCount implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 名称（论证结果 / 单位 / 行政区 / 月份 / 类型） */
        private String name;

        /** 数量 */
        private long count;

        public NameCount() {
        }

        public NameCount(String name, long count) {
            this.name = name;
            this.count = count;
        }
    }
}
