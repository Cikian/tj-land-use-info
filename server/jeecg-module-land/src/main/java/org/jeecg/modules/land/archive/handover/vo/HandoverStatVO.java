package org.jeecg.modules.land.archive.handover.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Description: 道路交付及养护协议移交事项-统计结果
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>与列表共用同一套查询条件，因此「先筛选再统计」与「统计后下钻」看到的是同一批数据。
 *
 * <p>指标卡与图表的取数全在这一个 VO 里，页面只调一次 `/stat`。
 */
@Data
public class HandoverStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前条件下的移交事项总数 */
    private long total;

    /** 其中「由迁移生成」的条数（source_facility_id 非空） */
    private long migratedCount;

    /** 人工新增的条数 */
    private long manualCount;

    /** 已关联档案的条数 */
    private long archivedCount;

    /** 协议信息缺失的条数（协议编号、协议签订日期都为空 —— 迁移来的 84 条都属于这种，待补录） */
    private long missingAgreementCount;

    /** 养护期将在 90 天内到期的条数（养护截止日期落在 今天 ~ 今天+90 天） */
    private long maintenanceExpiringCount;

    /** 养护期已过期的条数（养护截止日期 < 今天） */
    private long maintenanceExpiredCount;

    /** 宗地未匹配的条数（备注里带「无匹配记录」，需人工核对） */
    private long orphanCount;

    /**
     * 接收管养单位未填写的条数（数据质量指标，P2-08）。
     *
     * <p>口径：receive_unit 为 NULL / 空串 / 「未填写」「否」「无」等占位值。
     * 这条指标替代了原来那个被空值污染的「接收管养单位 Top10」——
     * 测试要的是「有多少条没填」这个可行动的数字，而不是一个叫「未填写」的假单位。
     */
    private long receiveUnitBlank;

    /** 按状态（待移交/移交中/已移交；服务端补齐 3 项） */
    private List<NameCount> byStatus;

    /** 按移交类型（字典 land_road_handover_type） */
    private List<NameCount> byType;

    /** 按行政区划（16 区；空值归为「未填写」） */
    private List<NameCount> byXzqh;

    /** 按移交年度（从移交编号 YJ-{yyyy}- 段取） */
    private List<NameCount> byYear;

    /**
     * 按接收管养单位 Top N（谁接管得最多；已排除空值与占位值，页面不再展示该排行）。
     *
     * <p><b>★ P2-08：字段保留、但不再有数据。</b>
     * 两端页面已不消费它（大屏排行块整块移除，改由 {@link #receiveUnitBlank} 回答
     * 「有多少条没填」）；{@code RoadHandoverServiceImpl#queryStat} 不再调用
     * {@code selectStatByReceiveUnit}，省掉每次打开页面的一条 GROUP BY 排序查询。
     *
     * <p>为什么保留字段而不是删掉：
     * <ol>
     *   <li><b>不破坏接口结构</b>：{@code /stat} 的响应里这个 key 仍然存在，
     *       任何按字段名读的调用方（含集成测试 {@code $.result.byReceiveUnit} 断言
     *       「是数组」）都不会因为这次优化而挂掉；</li>
     *   <li><b>保留给导出/后续报表</b>：真要做「谁接管得最多」时，
     *       Mapper 与 SQL 都还在，放回一次调用即可。</li>
     * </ol>
     * 默认值刻意给空数组而不是 null：null 会被序列化成 {@code "byReceiveUnit": null}，
     * 前端 {@code .map()} 或集成测试的 {@code isArray()} 都会炸。
     */
    private List<NameCount> byReceiveUnit = new java.util.ArrayList<>();

    /** 按道路等级 */
    private List<NameCount> byDldj;

    /**
     * @Description: 通用「名称-数量」计数项
     */
    @Data
    public static class NameCount implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private long count;

        public NameCount() {
        }

        public NameCount(String name, long count) {
            this.name = name;
            this.count = count;
        }
    }

    /**
     * @Description: 状态 tab 的计数项（固定 3 项，缺的补 0）
     */
    @Data
    public static class StatusCount implements Serializable {
        private static final long serialVersionUID = 1L;

        private String status;
        private long count;

        public StatusCount() {
        }

        public StatusCount(String status, long count) {
            this.status = status;
            this.count = count;
        }
    }
}
