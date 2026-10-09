package org.jeecg.modules.land.archive.handover.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.modules.land.archive.handover.dto.HandoverQueryDTO;

/**
 * @Description: 道路交付及养护协议移交事项 Excel 导出
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>导出的行 = 当前查询条件命中的全部移交事项（不分页），与页面上看到的口径一致。
 */
public interface IHandoverExportService {

    /**
     * 导出移交事项。
     *
     * @param query 与列表同一套查询条件
     * @return 已生成的 .xlsx 工作簿（由 Controller 写入响应流并关闭）
     */
    Workbook exportHandover(HandoverQueryDTO query);
}
