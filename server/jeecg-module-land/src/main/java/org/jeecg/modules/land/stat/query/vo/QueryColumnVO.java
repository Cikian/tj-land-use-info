package org.jeecg.modules.land.stat.query.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Description: 查询统计 · 字段元数据（条件下拉 / 自定义列 / 导出列共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>下发给前端的只有「能看的东西」：key / 中文名 / 类型 / 单位 / 运算符 / 是否默认列。
 * 真实列名（{@code crzdbh}、{@code nrcbdptf} …）留在后端白名单里，不出网。
 * 前端结果表的列、自定义列勾选项、条件下拉的字段、导出列全部由它生成，
 * 这样加一个字段只需要改后端一处。
 */
@Data
public class QueryColumnVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段 key（查询条件与自定义列都用它，不用中文名） */
    private String key;

    /** 中文名（列头 / 条件下拉 / Excel 表头） */
    private String label;

    /** 数据类型：TEXT / NUMBER / DATE */
    private String type;

    /** 单位（万元 / 平方米 / 米 …），没有则为 null */
    private String unit;

    /** 是否结果表默认列（旧系统写死的那几列） */
    private Boolean defaultColumn;

    /** 该字段允许的运算符（like / = / != / > / >= / < / <=） */
    private List<String> operators;
}
