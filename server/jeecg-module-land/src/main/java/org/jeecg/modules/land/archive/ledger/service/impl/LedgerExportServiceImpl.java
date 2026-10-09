package org.jeecg.modules.land.archive.ledger.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.ledger.dto.LedgerQueryDTO;
import org.jeecg.modules.land.archive.ledger.entity.RoadAcceptanceLedger;
import org.jeecg.modules.land.archive.ledger.enums.LedgerMaterial;
import org.jeecg.modules.land.archive.ledger.service.ILedgerExportService;
import org.jeecg.modules.land.archive.ledger.service.IRoadAcceptanceLedgerService;
import org.jeecg.modules.land.archive.ledger.support.LedgerSupport;
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
 * @Description: 道路设施验收及移交资料台账 Excel 导出实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>实现方式与档案、提级论证模块一致：用 easypoi 的 {@link ExcelExportUtil} +
 * {@link ExcelExportEntity} 显式声明列，而不是靠实体注解 ——
 * 导出列的顺序、表头文案、宽度都是验收时要对照的固定结构，不该随实体字段增删而变化。
 *
 * <p><b>★ 必须显式 {@code setType(ExcelType.XSSF)}</b>：
 * easypoi 的 {@link ExportParams} 默认是 HSSF，写出的是 OLE2 复合文档（.xls 的二进制格式），
 * 而文件名是 {@code .xlsx} —— 名字是 xlsx、内容是 xls，Excel 会直接报
 * 「文件格式或文件扩展名无效」拒绝打开（档案模块踩过这个坑，见《档案管理-实现说明》6.1）。
 *
 * <p><b>★ 13 类资料的列由 {@link LedgerMaterial#ordered()} 生成</b>，
 * 不是手写 13 遍：这样「后端枚举 → 导出表头」不会漂移，
 * 若以后加第 14 类资料，只要补枚举值，导出的列自动多一列。
 */
@Slf4j
@Service
public class LedgerExportServiceImpl implements ILedgerExportService {

    /** 导出里的空值统一显示为 —，避免打开看到一片空白分不清「没填」与「导出丢了」 */
    private static final String EMPTY = "—";

    /** 资料勾选在两列里的展示：已归集 / 未归集 */
    private static final String MATERIAL_YES = "√";
    private static final String MATERIAL_NO = "×";

    /** 导出上限保护：超大批量导出让调用方明确报错，而不是把服务器内存打满 */
    private static final int MAX_EXPORT_ROWS = 20000;

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private IRoadAcceptanceLedgerService ledgerService;

    @Autowired
    private LedgerSupport ledgerSupport;

    @Override
    public Workbook exportLedger(LedgerQueryDTO query) {
        List<RoadAcceptanceLedger> rows = ledgerService.queryForExport(query);
        int size = rows == null ? 0 : rows.size();
        if (size > MAX_EXPORT_ROWS) {
            throw new JeecgBootException("台账本次导出 " + size + " 行，超过 " + MAX_EXPORT_ROWS
                    + " 行的导出上限，请缩小查询范围（例如按行政区或年度筛选）后再导出");
        }
        List<Map<String, Object>> data = new ArrayList<>(size);
        if (rows != null) {
            for (RoadAcceptanceLedger row : rows) {
                data.add(toRow(row));
            }
        }
        ExportParams exportParams = new ExportParams("道路设施验收及移交资料台账", "台账");
        exportParams.setType(ExcelType.XSSF);
        log.info("导出道路验收移交台账：{} 行，导出人={}", size,
                LedgerSupport.nameOrPlaceholder(ledgerSupport.currentRealname()));
        return ExcelExportUtil.exportExcel(exportParams, columns(), data);
    }

    /**
     * 导出列：台账列 + ★13 类资料列 + 状态。
     *
     * <p>列序与台账页的表格保持一致（先标识与道路信息、再验收、再移交、再资料矩阵、最后状态），
     * 这样「屏幕上从左往右看」与「Excel 里从左往右看」是同一个顺序。
     */
    private List<ExcelExportEntity> columns() {
        List<ExcelExportEntity> columns = new ArrayList<>();
        // ---- 标识与道路信息 ----
        columns.add(new ExcelExportEntity("序号", "seq", 6));
        columns.add(new ExcelExportEntity("台账编号", "ledgerNo", 16));
        columns.add(new ExcelExportEntity("道路名称", "roadName", 36));
        columns.add(new ExcelExportEntity("行政区划", "xzqh", 12));
        columns.add(new ExcelExportEntity("功能区", "gnq", 14));
        columns.add(new ExcelExportEntity("出让宗地编号", "crzdbh", 20));
        columns.add(new ExcelExportEntity("地块名称", "dkmc", 24));
        columns.add(new ExcelExportEntity("配套设施类别", "ptsslb", 14));
        columns.add(new ExcelExportEntity("道路等级", "dldj", 12));
        columns.add(new ExcelExportEntity("建设单位", "jsdw", 24));
        columns.add(new ExcelExportEntity("施工单位", "sgdw", 24));
        columns.add(new ExcelExportEntity("监理单位", "jldw", 24));
        // ---- 验收信息 ----
        columns.add(new ExcelExportEntity("验收类型", "acceptanceType", 14));
        columns.add(new ExcelExportEntity("验收单编号", "acceptanceNo", 18));
        columns.add(new ExcelExportEntity("验收日期", "acceptanceDate", 14));
        columns.add(new ExcelExportEntity("验收组织单位", "acceptanceOrg", 24));
        columns.add(new ExcelExportEntity("验收结果", "acceptanceResult", 12));
        columns.add(new ExcelExportEntity("实际竣工日期", "completeDate", 14));
        // ---- 移交信息 ----
        columns.add(new ExcelExportEntity("移交单位", "handoverUnit", 24));
        columns.add(new ExcelExportEntity("接收管养单位", "receiveUnit", 24));
        columns.add(new ExcelExportEntity("移交日期", "handoverDate", 14));
        // ---- ★ 13 类资料（由枚举生成，避免与后端定义漂移）----
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            columns.add(new ExcelExportEntity(
                    String.format("资料%02d %s", material.getSeq(), material.getLabel()),
                    material.getProperty(), 12));
        }
        columns.add(new ExcelExportEntity("已归集资料数", "materialCount", 12));
        columns.add(new ExcelExportEntity("待补资料数", "materialMissing", 12));
        // ---- 状态与留痕 ----
        columns.add(new ExcelExportEntity("状态", "status", 12));
        columns.add(new ExcelExportEntity("关联档案数", "archiveCount", 10));
        columns.add(new ExcelExportEntity("备注", "remark", 40));
        columns.add(new ExcelExportEntity("迁移来源配套项目ID", "sourceFacilityId", 26));
        columns.add(new ExcelExportEntity("创建人", "createBy", 14));
        columns.add(new ExcelExportEntity("创建时间", "createTime", 20));
        columns.add(new ExcelExportEntity("更新时间", "updateTime", 20));
        return columns;
    }

    /** 一条台账 → 一行导出数据（键必须与 {@link #columns()} 的第二个参数一致） */
    private Map<String, Object> toRow(RoadAcceptanceLedger ledger) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seq", ledger.getSeq() == null ? EMPTY : ledger.getSeq());
        row.put("ledgerNo", value(ledger.getLedgerNo()));
        row.put("roadName", value(ledger.getRoadName()));
        row.put("xzqh", value(ledger.getXzqh()));
        row.put("gnq", value(ledger.getGnq()));
        row.put("crzdbh", value(ledger.getCrzdbh()));
        row.put("dkmc", value(ledger.getDkmc()));
        row.put("ptsslb", value(ledger.getPtsslb()));
        row.put("dldj", value(ledger.getDldj()));
        row.put("jsdw", value(ledger.getJsdw()));
        row.put("sgdw", value(ledger.getSgdw()));
        row.put("jldw", value(ledger.getJldw()));
        row.put("acceptanceType", value(ledger.getAcceptanceType()));
        row.put("acceptanceNo", value(ledger.getAcceptanceNo()));
        row.put("acceptanceDate", format(ledger.getAcceptanceDate(), DATE_PATTERN));
        row.put("acceptanceOrg", value(ledger.getAcceptanceOrg()));
        row.put("acceptanceResult", value(ledger.getAcceptanceResult()));
        row.put("completeDate", format(ledger.getCompleteDate(), DATE_PATTERN));
        row.put("handoverUnit", value(ledger.getHandoverUnit()));
        row.put("receiveUnit", value(ledger.getReceiveUnit()));
        row.put("handoverDate", format(ledger.getHandoverDate(), DATE_PATTERN));
        // ★ 13 类资料：由枚举取值，保证「表头 ↔ 取值」永远配对
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            row.put(material.getProperty(), material.valueOf(ledger) == 1 ? MATERIAL_YES : MATERIAL_NO);
        }
        row.put("materialCount", ledger.getMaterialCount() == null
                ? LedgerMaterial.countMaterials(ledger) : ledger.getMaterialCount());
        row.put("materialMissing", LedgerSupport.MATERIAL_TOTAL - LedgerMaterial.countMaterials(ledger));
        row.put("status", value(ledger.getStatus()));
        row.put("archiveCount", ledger.getArchiveCount() == null ? 0 : ledger.getArchiveCount());
        row.put("remark", value(ledger.getRemark()));
        row.put("sourceFacilityId", value(ledger.getSourceFacilityId()));
        row.put("createBy", value(ledger.getCreateBy()));
        row.put("createTime", format(ledger.getCreateTime(), DATETIME_PATTERN));
        row.put("updateTime", format(ledger.getUpdateTime(), DATETIME_PATTERN));
        return row;
    }

    private static Object value(String text) {
        return text == null || text.trim().isEmpty() ? EMPTY : text;
    }

    private static String format(Date date, String pattern) {
        return date == null ? EMPTY : new SimpleDateFormat(pattern).format(date);
    }
}
