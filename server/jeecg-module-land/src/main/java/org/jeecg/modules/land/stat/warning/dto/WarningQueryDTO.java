package org.jeecg.modules.land.stat.warning.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 分级预警 · 列表/汇总/导出共用的筛选条件
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>{@code list / summary / export} 三个接口吃同一份条件（字段名逐字对应
 * 契约 §5.1，前端三个入口共用一份 query 对象），所以只定义这一个 DTO。
 *
 * <p><b>★ 为什么筛选条件都是「按预警行的冗余列」而不是联表</b>：
 * 预警表已经把 {@code crzdbh / xzqh / dkmc / ptxmmc} 快照下来了，
 * 直接在这些列上过滤即可走索引（{@code idx_pw_crzdbh / idx_pw_xzqh}），
 * 也不必担心「配套改名后按名称筛不到历史预警」。
 *
 * <p><b>分页参数</b>：{@code pageNo} 从 1 起、{@code pageSize} 上限 200
 * （大屏端每页最多 100，留一倍余量；再大就变成「一次拉全表」，
 * 与「分页」的初衷相反）。
 */
@Data
public class WarningQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预警分类：环节预警 / 阶段预警 / 总体预警；空 = 全部 */
    private String yjType;

    /** 预警等级ID */
    private String levelId;

    /** 预警等级别名：blue / yellow / orange / red */
    private String levelCode;

    /** 行政区划（精确匹配预警行自己的 xzqh） */
    private String xzqh;

    /** 地块名称（模糊） */
    private String dkmc;

    /** 配套项目名称（模糊） */
    private String ptxmmc;

    /** 出让宗地编号（精确） */
    private String crzdbh;

    /** 关键词：模糊匹配 yj_name / pt_name / lc_name */
    private String keyword;

    /** 页码，从 1 起（默认 1） */
    private Integer pageNo;

    /** 每页条数（默认 10，最大 200） */
    private Integer pageSize;
}
