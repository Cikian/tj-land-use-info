package org.jeecg.modules.land.stat.query.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 查询统计 · 高级查询的单个条件（字段 / 运算符 / 值 / 关系符）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>对应旧系统 {@code queryXjCommercialLand.html} 的四个控件
 * （{@code #fieldName} / {@code #operator} / {@code #fieldValue} / {@code #relation}）。
 *
 * <p><b>★ 与旧系统的区别（安全）</b>：旧前端把每条条件拼成
 * {@code 字段 like '%值%'} 这样的 SQL 片段累积进 {@code textSql} 后原样执行；
 * 这里只传三个<b>数据</b>，SQL 由后端按白名单字段 + 固定运算符模板拼装，
 * 值永远走 {@code #{}} 参数绑定。
 */
@Data
public class AdvancedCondition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段 key（必须是 QueryFieldRegistry 里的 key，否则该条被忽略） */
    private String field;

    /** 运算符：like / = / != / > / >= / < / <=（见 QueryFieldRegistry.Operator） */
    private String operator;

    /** 字段值（原样作为参数绑定，不拼进 SQL） */
    private String value;

    /**
     * 与<b>上一条</b>的关系符：AND / OR。
     * 第一条忽略（旧系统的「与或」下拉也是在追加第二条起才生效）。
     */
    private String relation;
}
