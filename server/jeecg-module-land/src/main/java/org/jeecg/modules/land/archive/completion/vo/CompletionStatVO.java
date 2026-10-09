package org.jeecg.modules.land.archive.completion.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * @Description: 竣工验收项目历史工程资料数字化档案-统计结果
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>统计口径与档案列表<b>共用同一套查询条件</b>（SQL 侧同一段 {@code queryWhere}），
 * 因此页面上「先筛选再统计」与「统计后下钻回列表」看到的数一定是同一批数据。
 *
 * <p><b>为什么一次返回这么多</b>：档案页顶部要出「档案总数 / 已数字化 / 已挂扫描件 / 总页数」
 * 四张指标卡，下面要出「按数字化状态 / 按行政区 / 按项目类型 / 按竣工年度」四组分布，
 * 再加「按档案编号年度」「按保管期限」。一次请求全部返回，避免页面开 8 个接口。
 *
 * <p><b>★ 与台账模块的差别</b>：本模块多了 3 个「盘点型」标量
 * （{@link #pageTotal} 总页数、{@link #fileTotal} 文件数、{@link #investTotal} 投资额合计）——
 * 历史工程资料数字化的核心诉求就是「盘清家底有多少页要扫」，所以这几个数字比台账更需要。
 */
@Data
public class CompletionStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前条件下的档案总数 */
    private long total;

    /** 已关联扫描件（archive_id 非空）的档案数 */
    private long archivedCount;

    /** 已数字化的档案数（digitize_status = 已数字化） */
    private long digitizedCount;

    /** 总页数合计（SUM(page_count)，可能很大，用 long） */
    private long pageTotal;

    /** 文件数合计（SUM(file_count)） */
    private long fileTotal;

    /** 投资额合计（万元，SUM(invest_amount)） */
    private BigDecimal investTotal;

    /** 按数字化状态（固定 3 项，数量为 0 也返回） */
    private List<NameCount> byStatus;

    /** 按行政区划（16 区；空值归为「未填写」） */
    private List<NameCount> byXzqh;

    /** 按项目类型（字典 land_completion_project_type；空值归为「未填写」） */
    private List<NameCount> byType;

    /** 按保管期限（字典 land_completion_retention；空值归为「未填写」） */
    private List<NameCount> byRetention;

    /** 按档案编号年度（从 archive_no 的 JG-{yyyy}- 段取） */
    private List<NameCount> byYear;

    /** ★按竣工年度（从 complete_date 取；历史档案盘点最常用的一个维度） */
    private List<NameCount> byCompleteYear;

    /**
     * @Description: 通用「名称-数量」计数项（各分类统计与图表共用）
     */
    @Data
    public static class NameCount implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 分类名（行政区名 / 状态名 / 年度 / 项目类型…） */
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
     * @Description: 顶部状态 tab 的计数项（固定 3 项，缺的补 0）
     */
    @Data
    public static class StatusCount implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 状态文案（未数字化/数字化中/已数字化） */
        private String status;

        /** 该状态下的档案数（无数据时为 0） */
        private long count;

        public StatusCount() {
        }

        public StatusCount(String status, long count) {
            this.status = status;
            this.count = count;
        }
    }
}
