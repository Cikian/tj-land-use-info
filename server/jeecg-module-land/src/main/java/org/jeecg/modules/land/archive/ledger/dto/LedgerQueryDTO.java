package org.jeecg.modules.land.archive.ledger.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 道路设施验收及移交资料台账-查询条件（列表 / 统计 / 导出共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>对应方案 2.3.2 第 7 项「可进行快速检索查询」与清单 §6.3.7 规定的检索条件：
 * <b>道路名称、行政区、验收类型、验收日期区间、验收结果、状态</b>；
 * 另按台账的实际用法补了 6 组（宗地编号/地块名称/设施类别、资料缺失、资料数区间、
 * 移交日期区间、竣工日期区间、关键词全文）。

 * <p><b>★ 列表、统计、导出共用同一套条件</b>，SQL 侧也是同一段 {@code queryWhere}，
 * 保证「台账里看到什么，统计与导出就是什么」——这是档案与提级论证模块立下的约定，本模块照抄。
 *
 * <p>用 GET query string 绑定（Controller 里是非 {@code @RequestBody} 的对象参数）。
 * 分页沿用 jeecg 的 {@code pageNo/pageSize}，上限 500（见 {@link #resolvePageSize()}）。
 */
@Data
public class LedgerQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 基础检索条件（清单 §6.3.7 明列的 6 项）
    // ------------------------------------------------------------------

    /** 台账编号（模糊） */
    private String ledgerNo;

    /** ★道路名称（模糊） */
    private String roadName;

    /** ★行政区划（精确，16 区） */
    private String xzqh;

    /** 功能区（精确） */
    private String gnq;

    /** ★验收类型（精确，字典 land_road_acceptance_type） */
    private String acceptanceType;

    /** ★验收结果（精确，字典 land_road_acceptance_result） */
    private String acceptanceResult;

    /** ★状态（精确，固定 4 值，见 LedgerStatus） */
    private String status;

    /** ★验收日期起（含），格式 yyyy-MM-dd */
    private String beginAcceptanceDate;

    /** ★验收日期止（含），格式 yyyy-MM-dd */
    private String endAcceptanceDate;

    // ------------------------------------------------------------------
    // 补充检索条件（台账实际用法）
    // ------------------------------------------------------------------

    /** 出让宗地编号（模糊） */
    private String crzdbh;

    /** 地块名称（模糊） */
    private String dkmc;

    /** 配套设施类别（精确：道路/市政道路/道路及管线） */
    private String ptsslb;

    /** 配套项目ID（精确，用于从配套项目页跳转反查） */
    private String facilityId;

    /** 移交日期起（含），格式 yyyy-MM-dd */
    private String beginHandoverDate;

    /** 移交日期止（含），格式 yyyy-MM-dd */
    private String endHandoverDate;

    /** 实际竣工日期起（含），格式 yyyy-MM-dd */
    private String beginCompleteDate;

    /** 实际竣工日期止（含），格式 yyyy-MM-dd */
    private String endCompleteDate;

    /**
     * ★「缺少某类资料」筛选：传 13 类资料的<b>驼峰属性名</b>
     * （如 {@code hasJgwj} 表示「没有竣工文件的行」）。
     *
     * <p>取值白名单化在 Service 层做（{@code LedgerMaterial.ofProperty}）：
     * 只有能映射成 {@code LedgerMaterial} 的值才会被拼进 SQL，其余一律拒绝，
     * 避免把请求参数当列名用而引入注入面。
     */
    private String missingMaterial;

    /** 已归集资料数下限（含） */
    private Integer beginMaterialCount;

    /** 已归集资料数上限（含） */
    private Integer endMaterialCount;

    /** 关联档案ID（精确，用于从档案详情反查台账） */
    private String archiveId;

    /** 关键词（道路名称 / 宗地编号 / 地块名称 / 台账编号 的 OR 匹配） */
    private String keyword;

    /** 创建时间起（含），格式 yyyy-MM-dd */
    private String beginCreateTime;

    /** 创建时间止（含），格式 yyyy-MM-dd */
    private String endCreateTime;

    // ------------------------------------------------------------------
    // 分页
    // ------------------------------------------------------------------

    /** 页码，从 1 开始（jeecg 约定：请求参数名 pageNo） */
    private Integer pageNo;

    /** 每页条数（jeecg 约定：请求参数名 pageSize） */
    private Integer pageSize;

    public int resolvePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int resolvePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        // 上限 500：台账页没有理由一次拉几千条，导出走专门的 exportXls 接口
        return Math.min(pageSize, 500);
    }
}
