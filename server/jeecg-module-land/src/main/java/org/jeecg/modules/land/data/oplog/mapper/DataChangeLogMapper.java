package org.jeecg.modules.land.data.oplog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;

import java.util.List;
import java.util.Map;

/**
 * @Description: 数据变更留痕 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 与导入日志 {@code t_land_import_log} 的分工</b>（两张表都要有，不是重复）：
 * <pre>
 *   t_land_import_log  —— 一次「批量导入」的汇总：解析多少行 / 新增多少条 / 错了多少条
 *   t_data_change_log  —— 「单条数据」的每一次字段级变更（含批量导入写进去的每一条）
 * </pre>
 * 前者回答「这批导入怎么样」，后者回答「这条数据经历了什么」。
 */
@Mapper
public interface DataChangeLogMapper extends BaseMapper<DataChangeLog> {

    String COLUMNS = " id, biz_type AS bizType, biz_id AS bizId, biz_key AS bizKey, action, "
            + " change_summary AS changeSummary, change_detail AS changeDetail, "
            + " change_count AS changeCount, operator, operator_name AS operatorName, "
            + " operator_ip AS operatorIp, create_time AS createTime ";

    /** 某业务对象的完整履历（倒序：最近的在最上面） */
    @Select("SELECT " + COLUMNS + " FROM t_data_change_log "
            + "WHERE biz_type = #{bizType} AND biz_id = #{bizId} "
            + "ORDER BY create_time DESC LIMIT #{limit}")
    List<DataChangeLog> selectByBiz(@Param("bizType") String bizType,
                                    @Param("bizId") String bizId,
                                    @Param("limit") int limit);

    /**
     * 分页查变更留痕。
     *
     * <p>条件都可空：{@code bizType} / {@code action} / {@code operator} /
     * {@code keyword}（模糊匹配 biz_key 与 change_summary）。
     */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM t_data_change_log "
            + "<where>"
            + "  <if test='bizType != null and bizType != \"\"'> AND biz_type = #{bizType} </if>"
            + "  <if test='action != null and action != \"\"'> AND action = #{action} </if>"
            + "  <if test='operator != null and operator != \"\"'> AND operator = #{operator} </if>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    AND (biz_key LIKE CONCAT('%', #{keyword}, '%')"
            + "         OR change_summary LIKE CONCAT('%', #{keyword}, '%'))"
            + "  </if>"
            + "</where>"
            + " ORDER BY create_time DESC, id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<DataChangeLog> selectPageList(@Param("bizType") String bizType,
                                        @Param("action") String action,
                                        @Param("operator") String operator,
                                        @Param("keyword") String keyword,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    /** 同上，取总数（分页组件要） */
    @Select("<script>"
            + "SELECT COUNT(*) FROM t_data_change_log "
            + "<where>"
            + "  <if test='bizType != null and bizType != \"\"'> AND biz_type = #{bizType} </if>"
            + "  <if test='action != null and action != \"\"'> AND action = #{action} </if>"
            + "  <if test='operator != null and operator != \"\"'> AND operator = #{operator} </if>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    AND (biz_key LIKE CONCAT('%', #{keyword}, '%')"
            + "         OR change_summary LIKE CONCAT('%', #{keyword}, '%'))"
            + "  </if>"
            + "</where>"
            + "</script>")
    long countPageList(@Param("bizType") String bizType,
                       @Param("action") String action,
                       @Param("operator") String operator,
                       @Param("keyword") String keyword);

    /** 变更动作分布（页面顶部的动作计数卡） */
    @Select("SELECT action, COUNT(*) AS num FROM t_data_change_log "
            + "WHERE (#{bizType} IS NULL OR biz_type = #{bizType}) "
            + "GROUP BY action")
    List<Map<String, Object>> selectActionDistribution(@Param("bizType") String bizType);

    /** 某业务对象的变更次数（列表页「履历 N 次」角标） */
    @Select("SELECT COUNT(*) FROM t_data_change_log WHERE biz_type = #{bizType} AND biz_id = #{bizId}")
    long countByBiz(@Param("bizType") String bizType, @Param("bizId") String bizId);
}
