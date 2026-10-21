package org.jeecg.modules.land.stat.query.service;

import org.jeecg.modules.land.stat.query.dto.StatQueryDTO;
import org.jeecg.modules.land.stat.query.vo.QueryColumnVO;
import org.jeecg.modules.land.stat.query.vo.QueryPageVO;
import org.jeecg.modules.land.stat.query.vo.StatSummaryVO;

import java.util.List;
import java.util.Map;

/**
 * @Description: 查询统计 · Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 */
public interface IStatQueryService {

    /** 字段元数据（条件下拉 / 自定义列 / 导出列） */
    List<QueryColumnVO> queryColumns(String dataType);

    /** 分页明细 */
    QueryPageVO queryPage(StatQueryDTO query);

    /** 分析结果指标 */
    StatSummaryVO querySummary(StatQueryDTO query);

    /**
     * 命中行数（与 {@link #queryPage} 同一套条件，不取数据）。
     * 导出前用它判断是否超过行数上限。
     */
    long countRows(StatQueryDTO query);

    /**
     * 导出用全量行（与 {@link #queryPage} 同一套条件与列，只是不分页）。
     *
     * @param limit 行数上限，超出由调用方（导出服务）报错，避免把内存打满
     */
    List<Map<String, Object>> queryRowsForExport(StatQueryDTO query, int limit);

    /** 地块配套详情：宗地基本信息 + 该宗地下全部配套 */
    Map<String, Object> queryLandDetail(String crzdbh);

    /**
     * 单条配套项目详情（结果表点开某一行时用）。
     *
     * <p>返回全部业务字段（含批复状态、开工状态、责任单位、时间节点），
     * 字段中文名由前端用 `/columns?dataType=facility` 的元数据映射 ——
     * 中文名只在后端维护一份。
     */
    Map<String, Object> queryFacilityDetail(String id);

    /** 条件下拉的候选值（旧 queryFieldContent 的去重值清单） */
    Map<String, List<String>> queryOptions(String dataType);
}
