package org.jeecg.modules.land.archive.ledger.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Description: 道路设施验收及移交资料台账-统计结果
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>统计口径与台账列表<b>共用同一套查询条件</b>（SQL 侧同一段 {@code queryWhere}），
 * 因此页面上「先筛选再统计」与「统计后下钻回台账」看到的数一定是同一批数据。
 *
 * <p><b>为什么既有 {@code total} 又有 7 组分类统计</b>：
 * 台账页顶部要出「总数 / 已移交 / 资料齐全 / 宗地待核对」四张指标卡，
 * 下面要出「按状态 / 按行政区 / 按验收类型 / 按年度」四张图，
 * 再加一张「13 类资料归集情况」的横向条形图（这张图是本模块最有业务价值的视图 ——
 * 一眼看出哪类资料普遍缺失）。一次请求全部返回，避免页面开 8 个接口。
 */
@Data
public class LedgerStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前条件下的台账总数 */
    private long total;

    /** 其中「由迁移生成」的条数（source_facility_id 非空） */
    private long migratedCount;

    /** 其中「人工新增」的条数（source_facility_id 为空） */
    private long manualCount;

    /** 宗地未匹配的孤儿条数（remark 以「迁移提示」开头，需中心核对补录） */
    private long orphanCount;

    /** 13 类资料全部归集的条数（material_count = 13） */
    private long fullMaterialCount;

    /** 一类资料都没归集的条数（material_count = 0） */
    private long emptyMaterialCount;

    /** 按状态（未验收/验收中/已验收/已移交；服务端补齐 4 项，数量为 0 也返回） */
    private List<NameCount> byStatus;

    /** 按行政区划（16 区；空值归为「未填写」） */
    private List<NameCount> byXzqh;

    /** 按功能区（生态城/经开区/高新区/保税区/土地发展中心） */
    private List<NameCount> byGnq;

    /** 按验收类型（字典 land_road_acceptance_type；未填写的归为「未登记」） */
    private List<NameCount> byType;

    /** 按验收结果（字典 land_road_acceptance_result；未填写的归为「未登记」） */
    private List<NameCount> byResult;

    /** 按台账年度（从 ledger_no 的 YS-{yyyy}- 段取；固定 17 个年度分布实测于线上库） */
    private List<NameCount> byYear;

    /** ★按资料类别（13 类，name = 资料中文名，count = 该类已勾选的台账数） */
    private List<NameCount> byMaterial;

    /** 按配套设施类别（道路/市政道路/道路及管线） */
    private List<NameCount> byPtsslb;

    /**
     * @Description: 通用「名称-数量」计数项（各分类统计与图表共用）
     */
    @Data
    public static class NameCount implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 分类名（行政区名 / 状态名 / 年度 / 资料名…） */
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

    /**
     * @Description: 台账顶部状态 tab 的计数项（固定 4 项，缺的补 0）
     */
    @Data
    public static class StatusCount implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 状态文案（未验收/验收中/已验收/已移交） */
        private String status;

        /** 该状态下的台账数（无数据时为 0） */
        private long count;

        public StatusCount() {
        }

        public StatusCount(String status, long count) {
            this.status = status;
            this.count = count;
        }
    }
}
