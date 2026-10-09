package org.jeecg.modules.land.archive.completion.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.modules.land.archive.completion.dto.CompletionQueryDTO;

/**
 * @Description: 竣工验收项目历史工程资料数字化档案 Excel 导出
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>对应需求原文「**可快速导出档案信息**」（方案 2.3.2 第 8 项）。
 * 导出的行 = 当前查询条件命中的全部档案（不分页），与页面上看到的口径完全一致；
 * 导出的列 = <b>全部查询条件列 + 全部结果列</b>（共 30 列），
 * 这样「导出的台账」本身就是一份可直接归档/报盘的档案信息表。
 */
public interface ICompletionExportService {

    /**
     * 导出档案信息。
     *
     * @param query 与档案列表同一套查询条件
     * @return 已生成的 .xlsx 工作簿（由 Controller 写入响应流并关闭）
     */
    Workbook exportArchive(CompletionQueryDTO query);
}
