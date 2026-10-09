package org.jeecg.modules.land.data.process.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.land.data.process.entity.FacilityProcess;

import java.util.List;
import java.util.Map;

/**
 * @Description: 配套项目环节进度 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 排序必须靠 {@code t_process_configuration.location}</b>，不能靠
 * {@code t_facility_process.create_time}：后者是「谁先录」的顺序，
 * 而环节界面要的是「审批流程的先后顺序」。补录时差别很明显 ——
 * 先补录了第 20 个环节，它会排在第 1 个环节前面。
 */
@Mapper
public interface FacilityProcessMapper extends BaseMapper<FacilityProcess> {

    /** 环节进度全部列（t_facility_process 别名 p） */
    String P_COLUMNS = " p.id, p.pt_id AS ptId, p.crzdbh, p.lc_id AS lcId, p.lc_path AS lcPath, "
            + " p.lc_name AS lcName, p.stage_id AS stageId, p.lcqk, p.lckssj, p.yjjssj, p.lcjssj, "
            + " p.czwtlx, p.jtwt, p.gzjy, p.lrdw, p.lrr, p.lxdh, p.file_url AS fileUrl, "
            + " p.create_account AS createAccount, p.create_time AS createTime, "
            + " p.update_by AS updateBy, p.update_time AS updateTime ";

    /** 环节配置附加列（t_process_configuration 别名 c） */
    String C_COLUMNS = " c.process_time AS processTime, c.zgbm, c.location AS location ";

    /**
     * 某个配套项目的全部环节进度，按环节业务顺序返回。
     * ★ 只返回「已录入进度」的环节；未录入的环节由前端按配置表补空行。
     */
    @Select("SELECT " + P_COLUMNS + ", " + C_COLUMNS
            + " FROM t_facility_process p "
            + " LEFT JOIN t_process_configuration c ON c.id = p.lc_id "
            + " WHERE p.pt_id = #{ptId} "
            + " ORDER BY IFNULL(c.location, 9999) ASC, p.lc_path ASC")
    List<FacilityProcess> selectByFacility(@Param("ptId") String ptId);

    /** 一次取多个配套项目的环节进度（列表页批量展示，避免 N+1） */
    @Select("<script>"
            + "SELECT " + P_COLUMNS + ", " + C_COLUMNS
            + " FROM t_facility_process p "
            + " LEFT JOIN t_process_configuration c ON c.id = p.lc_id "
            + " WHERE p.pt_id IN "
            + "<foreach collection='ptIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " ORDER BY p.pt_id ASC, IFNULL(c.location, 9999) ASC"
            + "</script>")
    List<FacilityProcess> selectByFacilities(@Param("ptIds") List<String> ptIds);

    /**
     * 按「配套 × 阶段」聚合环节情况计数。
     *
     * <p>返回列：{@code ptId} / {@code stageId} / {@code lcqk} / {@code num}
     * —— 供 Service 归并出阶段状态。放进 SQL 聚合是为了
     * <b>一条 SQL 拿到所有配套的所有阶段分布</b>，而不是逐个配套逐个阶段查。
     */
    @Select("<script>"
            + "SELECT p.pt_id AS ptId, p.stage_id AS stageId, p.lcqk AS lcqk, COUNT(*) AS num "
            + "FROM t_facility_process p WHERE p.stage_id IS NOT NULL AND p.pt_id IN "
            + "<foreach collection='ptIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " GROUP BY p.pt_id, p.stage_id, p.lcqk"
            + "</script>")
    List<Map<String, Object>> selectStageStatusCount(@Param("ptIds") List<String> ptIds);

    /**
     * 逾期未完成的环节。
     *
     * <p>判据（与旧系统 {@code ProcessStatusController} 一致）：
     * {@code yjjssj < 今天} 且环节情况不是「已完成 / 不涉及」。
     */
    @Select("SELECT " + P_COLUMNS + ", " + C_COLUMNS
            + " FROM t_facility_process p "
            + " LEFT JOIN t_process_configuration c ON c.id = p.lc_id "
            + " WHERE p.yjjssj IS NOT NULL AND p.yjjssj &lt; CURDATE() "
            + "   AND (p.lcqk IS NULL OR p.lcqk NOT IN ('已完成', '不涉及')) "
            + " ORDER BY p.yjjssj ASC LIMIT #{limit}")
    List<FacilityProcess> selectOverdue(@Param("limit") int limit);

    /** 某个配套项目的环节进度统计（总数 / 已完成 / 进行中 / 不涉及 / 逾期） */
    @Select("SELECT COUNT(*) AS total, "
            + " SUM(CASE WHEN lcqk = '已完成' THEN 1 ELSE 0 END) AS finished, "
            + " SUM(CASE WHEN lcqk = '进行中' THEN 1 ELSE 0 END) AS running, "
            + " SUM(CASE WHEN lcqk = '不涉及' THEN 1 ELSE 0 END) AS notInvolved, "
            + " SUM(CASE WHEN lcqk = '未开启' OR lcqk IS NULL THEN 1 ELSE 0 END) AS notStarted, "
            + " SUM(CASE WHEN yjjssj IS NOT NULL AND yjjssj &lt; CURDATE() "
            + "          AND (lcqk IS NULL OR lcqk NOT IN ('已完成','不涉及')) THEN 1 ELSE 0 END) AS overdue "
            + "FROM t_facility_process WHERE pt_id = #{ptId}")
    Map<String, Object> selectProgressStat(@Param("ptId") String ptId);
}
