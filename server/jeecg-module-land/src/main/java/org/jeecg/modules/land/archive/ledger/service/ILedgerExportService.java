package org.jeecg.modules.land.archive.ledger.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.modules.land.archive.ledger.dto.LedgerQueryDTO;

/**
 * @Description: 道路设施验收及移交资料台账 Excel 导出
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>对应清单 §6.3.7 的「导出」要求：<b>台账 Excel 导出（列 = 所有资料列 + 状态）</b>。
 * 导出的行 = 当前查询条件命中的全部台账（不分页），与页面上看到的口径完全一致。
 */
public interface ILedgerExportService {

    /**
     * 导出台账。
     *
     * @param query 与台账列表同一套查询条件
     * @return 已生成的 .xlsx 工作簿（由 Controller 写入响应流并关闭）
     */
    Workbook exportLedger(LedgerQueryDTO query);
}
