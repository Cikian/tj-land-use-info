package org.jeecg.modules.land.stat.export;

import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.modules.land.stat.query.dto.StatQueryDTO;

/**
 * @Description: 查询统计 · Excel 导出（结果表 + 分析结果两个 sheet）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>对应清单 5-07「查询导出」。旧实现是 {@code getQueryExcel()}（结果表）
 * 与 {@code exportStatisticsToExcel()}（统计数字）两个独立的导出，
 * 各给一个文件；新系统合成一个工作簿两个页签 —— 中心导出后最常做的动作是
 * 「把数字和明细一起贴进汇报」，分两个文件反而要手工拼。
 */
public interface IStatQueryExportService {

    /**
     * 生成导出工作簿。
     *
     * @param query 与列表页完全相同的条件（含自定义列）
     * @return 已写入数据的 .xlsx 工作簿，由 Controller 写响应流
     */
    Workbook exportQuery(StatQueryDTO query);
}
