package org.jeecg.modules.land.stat.warning.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 分级预警 · 按等级/分类汇总（合同 §5.2）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p><b>★ {@code byLevel} 必须「四档齐全」且按 sort_no 升序</b>：
 * 大屏端用这四条画四色卡片，缺一档就会「跳色」（蓝、橙、红并排，
 * 中间黄色卡位空掉）。所以 Service 是拿**等级表全量**去 left join 统计数，
 * 而不是拿 GROUP BY 的结果直接返回。
 *
 * <p><b>⚠ total 可能大于 sum(byLevel.count)</b>：某等级被删除后，
 * 它已生成的预警行还在库里（level_id 指向已删等级），这些行计入 total
 * 但不出现在 byLevel 里——因为 byLevel 的语义是「当前生效的四档」。
 * 这些行会在下一次重算时被改写或删除。
 *
 * <p>{@code byType} 只返回**实际有数据**的分类（合同示例即为一条），
 * 与 byLevel 的处理故意不同：四色卡片必须补齐，分类下拉则不该凭空多出 0 值项。
 */
@Data
public class WarningSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 命中条件下的预警总条数（含等级已失效的历史行） */
    private Long total;

    /** 按等级分布（按 sort_no 升序，包含 count = 0 的等级） */
    private List<LevelCount> byLevel = new ArrayList<>();

    /** 按分类分布（只含有数据的分类，顺序：环节 / 阶段 / 总体） */
    private List<TypeCount> byType = new ArrayList<>();

    /** 这批数据的最后计算时间（yyyy-MM-dd HH:mm:ss）；没有数据时为 null */
    private String calcTime;

    /** 单个等级的计数 */
    @Data
    public static class LevelCount implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 等级ID */
        private String levelId;

        /** 等级别名（前端着色令牌） */
        private String levelCode;

        /** 等级名称 */
        private String levelName;

        /** 该等级下的预警条数（无数据时为 0，不是 null） */
        private Long count;
    }

    /** 单个分类的计数 */
    @Data
    public static class TypeCount implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 预警分类：环节预警 / 阶段预警 / 总体预警 */
        private String yjType;

        /** 该分类下的预警条数 */
        private Long count;
    }
}
