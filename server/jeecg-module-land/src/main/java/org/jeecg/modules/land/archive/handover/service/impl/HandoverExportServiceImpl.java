package org.jeecg.modules.land.archive.handover.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.handover.dto.HandoverQueryDTO;
import org.jeecg.modules.land.archive.handover.entity.RoadHandover;
import org.jeecg.modules.land.archive.handover.service.IHandoverExportService;
import org.jeecg.modules.land.archive.handover.service.IRoadHandoverService;
import org.jeecg.modules.land.archive.handover.support.HandoverSupport;
import org.jeecgframework.poi.excel.ExcelExportUtil;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.entity.params.ExcelExportEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 道路交付及养护协议移交事项 Excel 导出实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>实现方式与档案、台账模块一致：easypoi 的 {@link ExcelExportUtil} + {@link ExcelExportEntity}
 * 显式声明列，而不是靠实体注解（导出列的顺序与表头是验收时要对照的固定结构）。
 *
 * <p><b>★ 必须显式 {@code setType(ExcelType.XSSF)}</b>：easypoi 的 {@link ExportParams}
 * 默认是 HSSF，写出 OLE2（.xls 二进制），而文件名是 .xlsx —— 名字与内容不符时
 * Excel 会直接报「文件格式或文件扩展名无效」（档案模块踩过，见《档案管理-实现说明》6.1）。
 *
 * <p>注意：easypoi 的 {@code ExportParams(title, sheetName)} 会先写一行**大标题**、
 * 再写表头、然后才是数据行（序号列由 Service 侧连续编号）。
 */
@Slf4j
@Service
public class HandoverExportServiceImpl implements IHandoverExportService {

    /** 空值统一显示为 —，避免打开看到一片空白分不清「没填」与「导出丢了」 */
    private static final String EMPTY = "—";

    /** 导出上限保护 */
    private static final int MAX_EXPORT_ROWS = 20000;

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private IRoadHandoverService handoverService;

    @Autowired
    private HandoverSupport handoverSupport;

    @Override
    public Workbook exportHandover(HandoverQueryDTO query) {
        List<RoadHandover> rows = handoverService.queryForExport(query);
        int size = rows == null ? 0 : rows.size();
        if (size > MAX_EXPORT_ROWS) {
            throw new JeecgBootException("移交事项本次导出 " + size + " 行，超过 " + MAX_EXPORT_ROWS
                    + " 行的导出上限，请缩小查询范围后再导出");
        }
        List<Map<String, Object>> data = new ArrayList<>(size);
        if (rows != null) {
            for (RoadHandover row : rows) {
                data.add(toRow(row));
            }
        }
        ExportParams exportParams = new ExportParams("道路交付及养护协议移交事项", "移交事项");
        exportParams.setType(ExcelType.XSSF);
        log.info("导出道路移交事项：{} 行，导出人={}", size,
                HandoverSupport.nameOrPlaceholder(handoverSupport.currentRealname()));
        return ExcelExportUtil.exportExcel(exportParams, columns(), data);
    }

    /** 导出列：与页面列表的左→右顺序保持一致 */
    private List<ExcelExportEntity> columns() {
        List<ExcelExportEntity> columns = new ArrayList<>();
        columns.add(new ExcelExportEntity("序号", "seq", 6));
        columns.add(new ExcelExportEntity("移交编号", "handoverNo", 16));
        columns.add(new ExcelExportEntity("道路名称", "roadName", 36));
        columns.add(new ExcelExportEntity("道路编号", "roadCode", 16));
        columns.add(new ExcelExportEntity("道路等级", "dldj", 14));
        columns.add(new ExcelExportEntity("起点", "startPoint", 20));
        columns.add(new ExcelExportEntity("终点", "endPoint", 20));
        columns.add(new ExcelExportEntity("长度(米)", "lengthM", 12));
        columns.add(new ExcelExportEntity("红线宽度(米)", "redLineWidth", 14));
        columns.add(new ExcelExportEntity("行政区划", "xzqh", 12));
        columns.add(new ExcelExportEntity("功能区", "gnq", 14));
        columns.add(new ExcelExportEntity("配套设施类别", "ptsslb", 14));
        columns.add(new ExcelExportEntity("出让宗地编号", "crzdbh", 20));
        columns.add(new ExcelExportEntity("地块名称", "dkmc", 24));
        columns.add(new ExcelExportEntity("配套项目名称", "ptxmmc", 30));
        columns.add(new ExcelExportEntity("移交类型", "handoverType", 12));
        columns.add(new ExcelExportEntity("协议编号", "agreementNo", 20));
        columns.add(new ExcelExportEntity("协议名称", "agreementName", 30));
        columns.add(new ExcelExportEntity("协议签订日期", "agreementDate", 14));
        columns.add(new ExcelExportEntity("建设单位", "buildUnit", 28));
        columns.add(new ExcelExportEntity("接收管养单位", "receiveUnit", 28));
        columns.add(new ExcelExportEntity("实际移交日期", "handoverDate", 14));
        columns.add(new ExcelExportEntity("养护起始日期", "maintenanceStart", 14));
        columns.add(new ExcelExportEntity("养护截止日期", "maintenanceEnd", 14));
        columns.add(new ExcelExportEntity("状态", "status", 10));
        columns.add(new ExcelExportEntity("关联档案数", "archiveCount", 10));
        columns.add(new ExcelExportEntity("备注", "remark", 40));
        columns.add(new ExcelExportEntity("迁移来源配套项目ID", "sourceFacilityId", 26));
        columns.add(new ExcelExportEntity("创建人", "createBy", 14));
        columns.add(new ExcelExportEntity("创建时间", "createTime", 20));
        columns.add(new ExcelExportEntity("更新时间", "updateTime", 20));
        return columns;
    }

    private Map<String, Object> toRow(RoadHandover handover) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seq", handover.getSeq() == null ? EMPTY : handover.getSeq());
        row.put("handoverNo", value(handover.getHandoverNo()));
        row.put("roadName", value(handover.getRoadName()));
        row.put("roadCode", value(handover.getRoadCode()));
        row.put("dldj", value(handover.getDldj()));
        row.put("startPoint", value(handover.getStartPoint()));
        row.put("endPoint", value(handover.getEndPoint()));
        row.put("lengthM", handover.getLengthM() == null ? EMPTY : handover.getLengthM());
        row.put("redLineWidth", handover.getRedLineWidth() == null ? EMPTY : handover.getRedLineWidth());
        row.put("xzqh", value(handover.getXzqh()));
        row.put("gnq", value(handover.getGnq()));
        row.put("ptsslb", value(handover.getPtsslb()));
        row.put("crzdbh", value(handover.getCrzdbh()));
        row.put("dkmc", value(handover.getDkmc()));
        row.put("ptxmmc", value(handover.getPtxmmc()));
        row.put("handoverType", value(handover.getHandoverType()));
        row.put("agreementNo", value(handover.getAgreementNo()));
        row.put("agreementName", value(handover.getAgreementName()));
        row.put("agreementDate", format(handover.getAgreementDate(), DATE_PATTERN));
        row.put("buildUnit", value(handover.getBuildUnit()));
        row.put("receiveUnit", value(handover.getReceiveUnit()));
        row.put("handoverDate", format(handover.getHandoverDate(), DATE_PATTERN));
        row.put("maintenanceStart", format(handover.getMaintenanceStart(), DATE_PATTERN));
        row.put("maintenanceEnd", format(handover.getMaintenanceEnd(), DATE_PATTERN));
        row.put("status", value(handover.getStatus()));
        row.put("archiveCount", handover.getArchiveCount() == null ? 0 : handover.getArchiveCount());
        row.put("remark", value(handover.getRemark()));
        row.put("sourceFacilityId", value(handover.getSourceFacilityId()));
        row.put("createBy", value(handover.getCreateBy()));
        row.put("createTime", format(handover.getCreateTime(), DATETIME_PATTERN));
        row.put("updateTime", format(handover.getUpdateTime(), DATETIME_PATTERN));
        return row;
    }

    private static Object value(String text) {
        return text == null || text.trim().isEmpty() ? EMPTY : text;
    }

    private static String format(Date date, String pattern) {
        return date == null ? EMPTY : new SimpleDateFormat(pattern).format(date);
    }
}
