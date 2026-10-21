package org.jeecg.modules.land.stat.query.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @Description: 查询统计 · 已校验的高级查询条件（供 Mapper 直接拼装）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p><b>★ 这个类是「安全边界」的落地点</b>：
 * 用户传来的 {@link AdvancedCondition} 只有经过 {@code QueryFieldRegistry} 白名单映射
 * 才会变成这里的 {@code column}，运算符只会是 {@code Operator.sql()} 返回的固定串。
 * 因此 Mapper 里用 {@code ${column}} / {@code ${operator}} 是安全的 ——
 * 它们不可能包含用户输入的任何字符。值仍然走 {@code #{value}} 参数绑定。
 *
 * <p>关系符 {@code relation} 同时限制为 AND / OR 两个值（由 Service 归一）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedCondition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已白名单校验的真实列名 */
    private String column;

    /** 已白名单校验的 SQL 比较符（=、<>、>、>=、<、<=、LIKE） */
    private String operator;

    /** 条件值（参数绑定） */
    private String value;

    /** 与上一条的关系：AND / OR（第一条不使用） */
    private String relation;

    /** 是否 LIKE（LIKE 走 CONCAT('%',#{value},'%')，不能直接拼比较符） */
    private Boolean likePattern;

    public static ResolvedCondition of(String column, String operator, String value,
                                       String relation, boolean likePattern) {
        return new ResolvedCondition(column, operator, value, relation, likePattern);
    }
}
