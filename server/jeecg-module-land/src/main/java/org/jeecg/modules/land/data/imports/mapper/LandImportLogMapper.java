package org.jeecg.modules.land.data.imports.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * @Description: 经营性用地批量导入 - 导入日志 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>落库表 {@code t_land_import_log}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/land/02_t_land_import_log.sql}。
 *
 * <p><b>★ 为什么单独建表而不是复用 jeecg 的 {@code sys_log}</b>：
 * {@code sys_log} 只记「谁在什么时候调了哪个接口」，记不下「本次解析 800 行、
 * 新增 780 条、20 条错误、错误明细是什么」这类批量导入特有的信息。
 * 而中心在核对「这批 800 条到底进去没有、哪几条没有」时，要的恰恰是后者。
 *
 * <p><b>★ 为什么用注解 SQL 而不是 XML</b>：本 Mapper 只有两句简单语句，
 * 且不参与任何动态条件拼装；写成注解可以少一个 XML 文件，
 * 也避免 mapper-locations 的通配把测试环境的其它 XML 一起卷进来。
 */
@Mapper
public interface LandImportLogMapper {

    /** 新增一条导入日志 */
    @Insert("INSERT INTO t_land_import_log "
            + "(id, file_name, file_size, total_rows, inserted_rows, updated_rows, skipped_rows, "
            + " error_rows, duplicate_strategy, skip_error_rows, aborted, error_summary, operator, "
            + " operator_name, create_time) "
            + "VALUES (#{id}, #{fileName}, #{fileSize}, #{totalRows}, #{insertedRows}, #{updatedRows}, "
            + " #{skippedRows}, #{errorRows}, #{duplicateStrategy}, #{skipErrorRows}, #{aborted}, "
            + " #{errorSummary}, #{operator}, #{operatorName}, NOW())")
    int insertLog(@Param("id") String id,
                  @Param("fileName") String fileName,
                  @Param("fileSize") Long fileSize,
                  @Param("totalRows") Integer totalRows,
                  @Param("insertedRows") Integer insertedRows,
                  @Param("updatedRows") Integer updatedRows,
                  @Param("skippedRows") Integer skippedRows,
                  @Param("errorRows") Integer errorRows,
                  @Param("duplicateStrategy") String duplicateStrategy,
                  @Param("skipErrorRows") Integer skipErrorRows,
                  @Param("aborted") Integer aborted,
                  @Param("errorSummary") String errorSummary,
                  @Param("operator") String operator,
                  @Param("operatorName") String operatorName);

    /** 最近若干条导入记录（页面「导入记录」抽屉用） */
    @Select("SELECT id, file_name AS fileName, total_rows AS totalRows, inserted_rows AS insertedRows, "
            + " updated_rows AS updatedRows, skipped_rows AS skippedRows, error_rows AS errorRows, "
            + " duplicate_strategy AS duplicateStrategy, aborted AS aborted, error_summary AS errorSummary, "
            + " operator_name AS operatorName, create_time AS createTime "
            + "FROM t_land_import_log ORDER BY create_time DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRecent(@Param("limit") int limit);
}
