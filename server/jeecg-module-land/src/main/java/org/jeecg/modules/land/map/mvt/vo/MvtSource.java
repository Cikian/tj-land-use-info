package org.jeecg.modules.land.map.mvt.vo;

import lombok.Data;

/**
 * 数据源目录项。
 *
 * <p>dbUrl/dbUsername/dbPassword 仅在服务端用于探活（过滤已删除库的失效注册），
 * sources() 返回前会清空敏感字段，不向浏览器下发连接口令。
 */
@Data
public class MvtSource {
    private String id;
    private String code;
    private String name;
    private String dbName;
    /** 以下三项仅供探活，返回前清空；@JsonIgnore 防止序列化给前端，
     *  不能用 transient——MyBatis 的 Reflector 会跳过 transient 字段导致映射不上 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String dbUrl;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String dbUsername;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String dbPassword;
    private String remark;
    private String createTime;
}
