package org.jeecg.modules.land.data.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 数据管理 - 通用查询条件（宗地 / 配套项目列表共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 为什么不用 jeecg 的 {@code QueryGenerator.initQueryWrapper}</b>：
 * 它是从 request 参数自动拼条件的通用工具，好处是省事，坏处是
 * <b>查询条件完全由前端参数决定</b> —— 前端传一个实体上没有的字段名会被当成
 * 合法列名拼进 SQL，传 {@code *} 通配符的语义也和业务预期不一致。
 * 数据管理的列表条件就这几个，显式声明换成「可读、可控、可测」。
 */
@Data
public class LandAdminQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 出让宗地编号（模糊） */
    private String crzdbh;

    /** 地块名称（模糊） */
    private String dkmc;

    /** 行政区划（精确） */
    private String xzqh;

    /** 项目分类（精确：市级项目 / 区级项目） */
    private String xmfl;

    /** 规划用地性质（模糊） */
    private String ghydxz;

    /** 受让人（模糊） */
    private String srr;

    /** 出让时间起（yyyy-MM-dd） */
    private String crsjBegin;

    /** 出让时间止（yyyy-MM-dd） */
    private String crsjEnd;

    /** 配套是否齐全（精确：是 / 否） */
    private String ptsfqq;

    /** 是否有配套项目（true=只看有配套的宗地，false=只看没配套的「孤儿宗地」） */
    private Boolean hasFacility;

    /** 配套项目名称 / 编号（模糊，跨表到配套表；用于「按配套反查宗地」） */
    private String facilityKeyword;

    /** 排序字段（白名单校验，默认 create_time） */
    private String orderBy;

    /** 是否升序（默认 false = 倒序） */
    private Boolean asc;

    /** 页码（从 1 开始） */
    private Integer pageNo = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
