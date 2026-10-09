package org.jeecg.modules.land.archive.handover.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 道路交付及养护协议移交事项-查询条件（列表 / 统计 / 导出共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>对应方案 2.3.2 第 6 项「可进行快速检索查询」与清单 §6.3.6 明列的检索条件：
 * <b>道路名称、行政区划、宗地编号、配套项目、移交类型、状态、协议签订日期区间、移交日期区间</b>；
 * 另按实际用法补了：道路等级、地块名称、关键词、养护到期区间（用于「养护期快到了」的提醒）、
 * 是否已关联档案。
 *
 * <p>列表、统计、导出共用同一套条件（SQL 侧同一段 queryWhere），
 * 保证「列表里看到什么，统计与导出就是什么」。
 */
@Data
public class HandoverQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 移交编号（模糊） */
    private String handoverNo;

    /** ★道路名称（模糊） */
    private String roadName;

    /** 道路编号（模糊） */
    private String roadCode;

    /** 道路等级（精确） */
    private String dldj;

    /** ★行政区划（精确，16 区） */
    private String xzqh;

    /** 功能区（精确） */
    private String gnq;

    /** ★出让宗地编号（模糊） */
    private String crzdbh;

    /** 地块名称（模糊） */
    private String dkmc;

    /** ★配套项目（精确，配套项目ID） */
    private String facilityId;

    /** ★移交类型（精确，字典 land_road_handover_type） */
    private String handoverType;

    /** ★状态（精确，固定 3 值，见 HandoverStatus） */
    private String status;

    /** ★协议签订日期起（含），yyyy-MM-dd */
    private String beginAgreementDate;

    /** ★协议签订日期止（含），yyyy-MM-dd */
    private String endAgreementDate;

    /** ★移交日期起（含），yyyy-MM-dd */
    private String beginHandoverDate;

    /** ★移交日期止（含），yyyy-MM-dd */
    private String endHandoverDate;

    /** 养护截止日期起（含），yyyy-MM-dd —— 用于筛「养护期将到期」 */
    private String beginMaintenanceEnd;

    /** 养护截止日期止（含），yyyy-MM-dd */
    private String endMaintenanceEnd;

    /** 接收管养单位（模糊） */
    private String receiveUnit;

    /** 建设单位（模糊） */
    private String buildUnit;

    /** 关联档案ID（精确，用于从档案详情反查移交事项） */
    private String archiveId;

    /** 是否已关联档案：true 只看已关联，false 只看未关联，null 不限 */
    private Boolean hasArchive;

    /** 关键词（移交编号 / 道路名称 / 宗地编号 / 地块名称 / 协议编号 的 OR 匹配） */
    private String keyword;

    /** 创建时间起（含），yyyy-MM-dd */
    private String beginCreateTime;

    /** 创建时间止（含），yyyy-MM-dd */
    private String endCreateTime;

    /** 页码，从 1 开始 */
    private Integer pageNo;

    /** 每页条数，上限 500 */
    private Integer pageSize;

    public int resolvePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int resolvePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 500);
    }
}
