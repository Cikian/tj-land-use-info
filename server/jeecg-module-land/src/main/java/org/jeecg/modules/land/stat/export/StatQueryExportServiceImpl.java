package org.jeecg.modules.land.stat.export;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.stat.query.dto.StatQueryDTO;
import org.jeecg.modules.land.stat.query.service.IStatQueryService;
import org.jeecg.modules.land.stat.query.support.QueryFieldRegistry;
import org.jeecg.modules.land.stat.query.support.QueryFieldRegistry.DataSet;
import org.jeecg.modules.land.stat.query.support.QueryFieldRegistry.Field;
import org.jeecg.modules.land.stat.query.vo.StatSummaryVO;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.entity.params.ExcelExportEntity;
import org.jeecgframework.poi.excel.export.ExcelExportServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 查询统计 · Excel 导出实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>两个页签：
 * <ol>
 *   <li><b>查询结果</b>：序号 + 当前勾选的自定义列（与屏幕上从左到右完全一致）；</li>
 *   <li><b>统计指标</b>：分析结果面板上的全部数字（旧的 {@code exportStatisticsToExcel} 那份）。</li>
 * </ol>
 *
 * <p><b>★ 必须显式 {@link ExcelType#XSSF}</b>：否则写出的是 .xls 内容配 .xlsx 文件名，
 * Excel 打开会报「文件格式或扩展名无效」——档案导出踩过同一个坑。
 *
 * <p><b>★ 行数上限 20000</b>：easypoi 的内存版导出会把所有行先建成 cell，
 * 全市 847 条宗地 / 1433 条配套远达不到上限，但「高级查询配一个宽松条件」时
 * 有可能全量导出；与其让服务端 OOM，不如给一句能看懂的话让用户缩小范围。
 */
@Slf4j
@Service
public class StatQueryExportServiceImpl implements IStatQueryExportService {

    /** 单次导出行数上限 */
    private static final int MAX_EXPORT_ROWS = 20000;

    @Autowired
    private IStatQueryService statQueryService;

    @Override
    public Workbook exportQuery(StatQueryDTO query) {
        StatQueryDTO condition = query == null ? new StatQueryDTO() : query;
        DataSet dataSet = DataSet.of(condition.getDataType());

        // 先按同一套条件数一下总量，超限就别浪费时间查明细了
        long total = statQueryService.countRows(condition);
        if (total > MAX_EXPORT_ROWS) {
            throw new JeecgBootException("本次导出 " + total + " 行，超过 " + MAX_EXPORT_ROWS
                    + " 行的导出上限，请缩小查询范围（例如按行政区或年份筛选）后再导出");
        }

        List<String> keys = resolveKeys(condition, dataSet);
        List<Map<String, Object>> rows = statQueryService.queryRowsForExport(condition, MAX_EXPORT_ROWS);
        StatSummaryVO summary = statQueryService.querySummary(condition);

        // ★ 为什么不用 ExcelExportUtil.exportExcel(List<Map>, ExcelType)：
        //   本工程用的 autopoi 1.4.4 里，那个重载的 map 只认 "title"/"entity"(Class)/"data"，
        //   entity 期望是一个 Class（实体 + @Excel 注解），而本页的列是**用户自定义的**，
        //   拿不到 Class —— 传 List<ExcelExportEntity> 会在运行期抛
        //   ClassCastException: java.util.ArrayList cannot be cast to java.lang.Class。
        //   所以直接用 ExcelExportServer.createSheetForMap 往同一个工作簿里写两个 sheet。
        Workbook workbook = new XSSFWorkbook();
        ExcelExportServer server = new ExcelExportServer();
        server.createSheetForMap(workbook, resultParams(),
                resultEntities(keys, dataSet), resultData(keys, rows));
        server.createSheetForMap(workbook, summaryParams(),
                summaryEntities(), summaryData(summary, dataSet));

        log.info("查询统计导出：数据集={}，{} 行，{} 列", dataSet, rows.size(), keys.size());
        return workbook;
    }

    /** 结果页签的参数 */
    private ExportParams resultParams() {
        ExportParams params = new ExportParams("查询结果", "查询结果");
        params.setType(ExcelType.XSSF);
        return params;
    }

    /** 统计指标页签的参数 */
    private ExportParams summaryParams() {
        ExportParams params = new ExportParams("统计指标", "统计指标");
        params.setType(ExcelType.XSSF);
        return params;
    }

    /** 结果页签的列：序号 + 用户勾选的自定义列 */
    private List<ExcelExportEntity> resultEntities(List<String> keys, DataSet dataSet) {
        List<ExcelExportEntity> entities = new ArrayList<>();
        entities.add(new ExcelExportEntity("序号", "seq", 6));
        for (String key : keys) {
            Field field = QueryFieldRegistry.find(dataSet, key);
            if (field == null) {
                continue;
            }
            String title = field.getUnit() == null
                    ? field.getLabel()
                    : field.getLabel() + "（" + field.getUnit() + "）";
            entities.add(new ExcelExportEntity(title, key, widthOf(field)));
        }
        return entities;
    }

    /** 结果页签的数据（补 1 起的序号） */
    private List<Map<String, Object>> resultData(List<String> keys, List<Map<String, Object>> rows) {
        List<Map<String, Object>> data = new ArrayList<>(rows.size());
        int seq = 1;
        for (Map<String, Object> row : rows) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("seq", seq++);
            for (String key : keys) {
                line.put(key, row.get(key));
            }
            data.add(line);
        }
        return data;
    }

    /** 统计指标页签的列：指标 / 数值 */
    private List<ExcelExportEntity> summaryEntities() {
        List<ExcelExportEntity> entities = new ArrayList<>();
        entities.add(new ExcelExportEntity("指标", "label", 26));
        entities.add(new ExcelExportEntity("数值", "value", 20));
        return entities;
    }

    /** 统计指标页签的数据：顺序与页面「分析结果」一致 */
    private List<Map<String, Object>> summaryData(StatSummaryVO summary, DataSet dataSet) {
        List<Map<String, Object>> data = new ArrayList<>();
        if (dataSet == DataSet.FACILITY) {
            add(data, "涉及宗地数（宗）", summary.getLandTotal());
            add(data, "配套项目总数（个）", summary.getFacilityTotal());
            add(data, "配套项目名称为空（条）", summary.getFacilityNameBlank());
        } else {
            add(data, "地块总数（宗）", summary.getLandTotal());
            add(data, "配套总数（个）", summary.getFacilityTotal());
            add(data, "配套项目名称为空（条）", summary.getFacilityNameBlank());
            add(data, "配套费总额（万元）", summary.getSupportingFeeTotal());
        }
        add(data, "新建道路（条）", summary.getNewRoadCount());
        add(data, "已立项（个）", summary.getApprovedCount());
        add(data, "立项率（%）", summary.getApprovedRate());
        add(data, "已可研（个）", summary.getFeasibilityCount());
        add(data, "可研率（%）", summary.getFeasibilityRate());
        add(data, "已初设（个）", summary.getDesignCount());
        add(data, "初设率（%）", summary.getDesignRate());
        add(data, "已开工（个）", summary.getStartedCount());
        add(data, "开工率（%）", summary.getStartedRate());
        add(data, "已竣工（个）", summary.getCompletedCount());
        add(data, "竣工率（%）", summary.getCompletedRate());
        if (dataSet == DataSet.FACILITY) {
            add(data, "已移交（个）", summary.getHandedOverCount());
            add(data, "移交率（%）", summary.getHandedOverRate());
        }
        return data;
    }

    private static void add(List<Map<String, Object>> data, String label, Object value) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("label", label);
        line.put("value", value == null ? "" : value);
        data.add(line);
    }

    /** 列宽：编号类 22、名称类 30、其余 14（与页面表格的列宽量级一致） */
    private static int widthOf(Field field) {
        switch (field.getKey()) {
            case "crzdbh":
                return 22;
            case "dkmc":
            case "srr":
            case "ptxmmc":
            case "lpmc":
                return 30;
            case "createTime":
                return 20;
            default:
                return 14;
        }
    }

    private List<String> resolveKeys(StatQueryDTO condition, DataSet dataSet) {
        List<String> keys = new ArrayList<>();
        if (condition.getColumns() != null) {
            for (String key : condition.getColumns()) {
                Field field = QueryFieldRegistry.find(dataSet, key);
                if (field != null && !keys.contains(field.getKey())) {
                    keys.add(field.getKey());
                }
            }
        }
        if (keys.isEmpty()) {
            keys.addAll(QueryFieldRegistry.defaultColumns(dataSet));
        }
        return keys;
    }
}
