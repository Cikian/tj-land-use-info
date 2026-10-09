package org.jeecg.modules.land.escalation.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @Description: 提级论证查询条件（录入列表 / 查询统计 / 台账 / 导出 / 统计图共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>对应方案 2.3.3 第 2 项「按基本信息、论证结果多维查询，可导出」与
 * 设计文档 5.5 的 9 个查询条件。用 GET query string 绑定
 * （Controller 里是非 {@code @RequestBody} 的对象参数）。
 *
 * <p><b>★ 列表、台账、统计图、导出共用同一套条件</b>，SQL 侧也是同一段
 * {@code queryWhere}，保证「列表里看到什么，统计与导出就是什么」——
 * 这是档案模块立下的约定，本模块照抄。
 *
 * <p>分页沿用 jeecg 的 {@code pageNo/pageSize}，上限 500（见 {@link #resolvePageSize()}）。
 */
@Data
public class EscalationQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 9 个查询条件（设计文档 5.5）
    // ------------------------------------------------------------------

    /** 项目编号（模糊） */
    private String projectNo;

    /** 项目名称（模糊） */
    private String projectName;

    /** 申报单位（模糊） */
    private String declareDept;

    /** 项目类型（精确，字典 land_escalation_project_type） */
    private String projectType;

    /** 行政区划（精确，16 区） */
    private String xzqh;

    /** 功能区（精确） */
    private String gnq;

    /** ★论证结果（精确，字典 land_escalation_arg_result，方案明确要求） */
    private String argResult;

    /** ★办理状态（精确，固定 4 值，见 EscalationStatus） */
    private String status;

    /** 是否土地整理项目（精确，字典 yn：1 是 / 0 否） */
    private Integer tdzlProject;

    /** 申报时间起（含），格式 yyyy-MM-dd */
    private String beginDeclareDate;

    /** 申报时间止（含），格式 yyyy-MM-dd */
    private String endDeclareDate;

    /** 总投资下限（亿元，含） */
    private BigDecimal beginInvestment;

    /** 总投资上限（亿元，含） */
    private BigDecimal endInvestment;

    // ------------------------------------------------------------------
    // 其他辅助条件
    // ------------------------------------------------------------------

    /** 关联配套项目ID（精确，用于按挂钩点反查） */
    private String facilityId;

    /** 关联出让宗地编号（模糊） */
    private String crzdbh;

    /** 地名/地块名称（模糊） */
    private String dkmc;

    /** 项目概述 / 论证事由等正文关键词（模糊，走摘要列的 OR 匹配） */
    private String keyword;

    /** 创建时间起（含），格式 yyyy-MM-dd */
    private String beginCreateTime;

    /** 创建时间止（含），格式 yyyy-MM-dd */
    private String endCreateTime;

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
        // 上限 500：列表页没有理由一次拉几万条，导出走专门的 exportXls 接口
        return Math.min(pageSize, 500);
    }
}
