package org.jeecg.modules.land.escalation.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.modules.land.escalation.dto.EscalationQueryDTO;

/**
 * @Description: 提级论证 Excel 导出 Service（方案 2.3.3 第 2 项：可导出）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>导出与列表<b>严格共用同一套查询条件</b>（{@code EscalationQueryDTO}），
 * 保证「导出的就是当前屏幕上筛出来的」。
 *
 * <p>两个导出各自一张工作表，文件名由 Controller 拼：
 * {@code 提级论证项目_{yyyyMMddHHmm}.xlsx} / {@code 提级论证台账_{yyyyMMddHHmm}.xlsx}。
 */
public interface IEscalationExportService {

    /**
     * 查询结果导出（全部查询条件列 + 结果列）。
     *
     * @param query 查询条件
     * @return 工作簿；调用方负责写出到响应流并关闭
     */
    Workbook exportProject(EscalationQueryDTO query);

    /**
     * 台账导出（台账 14 列，含材料数 / 办理状态 / 论证结果 / 最新意见摘要）。
     *
     * @param query 查询条件
     * @return 工作簿；调用方负责写出到响应流并关闭
     */
    Workbook exportLedger(EscalationQueryDTO query);
}
