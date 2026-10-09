package org.jeecg.modules.land.data.imports.facility.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * @Description: 配套信息批量导入 - 导入日志 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>落库表 {@code t_facility_import_log}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/data/07_t_facility_import_log.sql}。
 *
 * <p><b>★ 为什么不复用宗地的 {@code t_land_import_log}</b>：
 * 两者的行数口径不同（宗地按编号判重、配套按「宗地 + 名称」判重），
 * 更要紧的是配套导入多两个只有它才有的信息：{@code orphan_rows}（孤儿行数）
 * 与 {@code allow_orphan}（是否允许挂到未登记宗地）。
 * 把两件事塞进一张表就要靠一个 {@code biz_type} 列来区分，
 * 而查询时「配套的孤儿有多少」这种问题会变成「先筛 biz_type 再筛 orphan_rows」，
 * 表长起来以后每次看记录都要多想一层。分表之后两边各自演进，互不干扰。
 *
 * <p><b>★ 为什么用注解 SQL 而不是 XML</b>：本 Mapper 只有两句简单语句，
 * 且不参与任何动态条件拼装；写成注解可以少一个 XML 文件，
 * 也避免 mapper-locations 的通配把测试环境的其它 XML 一起卷进来。
 */
@Mapper
public interface FacilityImportLogMapper {

    /**
     * 新增一条导入日志。
     *
     * <p>★ 与宗地导入日志唯一的结构差异就是 {@code orphan_rows} 与 {@code allow_orphan}：
     * 事后核对「这批数据里有多少条挂不上宗地」时，只看日志表就够，
     * 不需要再去翻 {@code xj_kjkfb_supporting_facilities} 与 {@code t_land} 做差集。
     */
    @Insert("INSERT INTO t_facility_import_log "
            + "(id, file_name, file_size, total_rows, inserted_rows, updated_rows, skipped_rows, "
            + " error_rows, duplicate_strategy, skip_error_rows, aborted, orphan_rows, allow_orphan, "
            + " error_summary, operator, operator_name, create_time) "
            + "VALUES (#{id}, #{fileName}, #{fileSize}, #{totalRows}, #{insertedRows}, #{updatedRows}, "
            + " #{skippedRows}, #{errorRows}, #{duplicateStrategy}, #{skipErrorRows}, #{aborted}, "
            + " #{orphanRows}, #{allowOrphan}, #{errorSummary}, #{operator}, #{operatorName}, NOW())")
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
                  @Param("orphanRows") Integer orphanRows,
                  @Param("allowOrphan") Integer allowOrphan,
                  @Param("errorSummary") String errorSummary,
                  @Param("operator") String operator,
                  @Param("operatorName") String operatorName);

    /** 最近若干条导入记录（页面「导入记录」抽屉用） */
    @Select("SELECT id, file_name AS fileName, total_rows AS totalRows, inserted_rows AS insertedRows, "
            + " updated_rows AS updatedRows, skipped_rows AS skippedRows, error_rows AS errorRows, "
            + " duplicate_strategy AS duplicateStrategy, aborted AS aborted, "
            + " orphan_rows AS orphanRows, allow_orphan AS allowOrphan, "
            + " error_summary AS errorSummary, operator_name AS operatorName, create_time AS createTime "
            + "FROM t_facility_import_log ORDER BY create_time DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRecent(@Param("limit") int limit);
}
