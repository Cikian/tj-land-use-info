package org.jeecg.modules.land.archive.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 档案统计结果（方案 2.3.2 第 5 项：按照项目对各个项目的档案信息进行统计）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>对应原型图右侧三块图 + 方案原文重点「按项目统计」：
 * <ul>
 *   <li>{@link #total} / {@link #archivedCount} / {@link #unarchivedCount} —— 环形图；</li>
 *   <li>{@link #byCategory} —— 横向条形图（按<b>顶级</b>类别聚合）；</li>
 *   <li>{@link #byYear} —— 年度趋势折线；</li>
 *   <li>{@link #byProject} —— 按项目统计（方案原文重点）。</li>
 * </ul>
 *
 * <p><b>关于「缺失项分析」</b>：设计文档 6.3.5 提到要算「某项目缺哪些档案」必须先有
 * 「应归档清单模板」（{@code t_archive_requirement}）。本次按文档建议采用<b>方案 A</b>：
 * 只统计「已有多少、按类别分布、按年度分布、按项目分布」，不做缺失分析。
 */
@Data
public class ArchiveStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 档案总数 */
    private long total;

    /** 已归档数（status = 已归档） */
    private long archivedCount;

    /** 未归档数（status != 已归档） */
    private long unarchivedCount;

    /** 卷内文件总数 */
    private long fileTotal;

    /** 文件总字节 */
    private long totalSize;

    /** 覆盖的项目数（distinct facility_id，含为空的按 ptxmmc 去重） */
    private long projectCount;

    /** 按顶级档案类别统计 */
    private List<NameCount> byCategory = new ArrayList<>();

    /** 按档案年度统计 */
    private List<NameCount> byYear = new ArrayList<>();

    /** 按档案状态统计 */
    private List<NameCount> byStatus = new ArrayList<>();

    /** 按密级统计 */
    private List<NameCount> bySecretLevel = new ArrayList<>();

    /** 按行政区统计 */
    private List<NameCount> byXzqh = new ArrayList<>();

    /** 按项目统计（方案原文重点） */
    private List<ProjectStat> byProject = new ArrayList<>();

    /**
     * @Description: 通用「名称 + 数量」统计项
     */
    @Data
    public static class NameCount implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 名称（类别名 / 年度 / 状态 / 密级 / 行政区） */
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
     * @Description: 按项目统计项
     */
    @Data
    public static class ProjectStat implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 配套项目ID */
        private String facilityId;
        /** 配套项目名称 */
        private String ptxmmc;
        /** 出让宗地编号 */
        private String crzdbh;
        /** 地块名称 */
        private String dkmc;
        /** 所属行政区 */
        private String xzqh;
        /** 该项目的档案总数 */
        private long total;
        /** 已归档数 */
        private long archivedCount;
        /** 未归档数 */
        private long unarchivedCount;
        /** 卷内文件数 */
        private long fileCount;
    }
}
