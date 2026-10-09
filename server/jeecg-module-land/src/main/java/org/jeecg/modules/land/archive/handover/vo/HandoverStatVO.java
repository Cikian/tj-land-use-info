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

    /** 按状态（待移交/移交中/已移交；服务端补齐 3 项） */
    private List<NameCount> byStatus;

    /** 按移交类型（字典 land_road_handover_type） */
    private List<NameCount> byType;

    /** 按行政区划（16 区；空值归为「未填写」） */
    private List<NameCount> byXzqh;

    /** 按移交年度（从移交编号 YJ-{yyyy}- 段取） */
    private List<NameCount> byYear;

    /** 按接收管养单位 Top N（谁接管得最多） */
    private List<NameCount> byReceiveUnit;

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
