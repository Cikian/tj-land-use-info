package org.jeecg.modules.land.stat.home.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 首页看板 · 出让地块情况统计
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-19
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（五）首页看板第一块。旧实现
 * {@code XjKjkfbCommonController.getLandInfoBySJ / getLandInfoByQJ}。
 *
 * <p><b>★ 字段名是对前端的契约，不能改</b>：前端首页按
 * {@code total / cityCount / districtCount / roadCount / ranks[]} 取数，
 * 改名会静默变成 {@code undefined}（页面不报错，只是显示空）。
 *
 * <p>本类从 data 域 {@code LandDashboardVO} 迁移而来（迁入 stat/home 后
 * data 域的重复实现已删除，保证「同一统计只有一份实现」）。
 */
@Data
public class LandOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已出让宗地总数（市级 + 区级，按宗地 id 去重） */
    private long total;

    /** 市级项目宗地数 */
    private long cityCount;

    /** 区级项目宗地数 */
    private long districtCount;

    /** 涉及道路总数（按配套项目名称去重，沿用旧系统排除口径） */
    private long roadCount;

    /** 行政区排行：市级按 t_land.xzqh、区级按 t_land.xzqh2 分组 */
    private List<RankItem> ranks = new ArrayList<>();

    /**
     * 排行项。
     *
     * <p>{@code projectType} 取「市级项目 / 区级项目」——
     * 前端按它把两级排行分成两个视图，所以不能合并成一组。
     */
    @Data
    public static class RankItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 项目分类：市级项目 / 区级项目 */
        private String projectType;

        /** 分组名（市级 = 行政区划，区级 = 旧口径的 xzqh2） */
        private String name;

        /** 该分组内的宗地数（COUNT(DISTINCT id)） */
        private long value;

        /** 该分组内涉及的配套项目数（COUNT(DISTINCT ptxmmc)） */
        private long roadCount;
    }
}
