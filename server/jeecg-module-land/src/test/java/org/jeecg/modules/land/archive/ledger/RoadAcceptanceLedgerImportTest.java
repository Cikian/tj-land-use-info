package org.jeecg.modules.land.archive.ledger;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.archive.ledger.dto.LedgerQueryDTO;
import org.jeecg.modules.land.archive.ledger.entity.RoadAcceptanceLedger;
import org.jeecg.modules.land.archive.ledger.enums.LedgerMaterial;
import org.jeecg.modules.land.archive.ledger.service.ILedgerImportService;
import org.jeecg.modules.land.archive.ledger.service.IRoadAcceptanceLedgerService;
import org.jeecg.modules.land.archive.ledger.vo.LedgerImportResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Description: 道路设施验收及移交资料台账 - Excel 批量补录集成测试（模块 C）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>测试用的上传文件不是手搓的二进制，而是<b>直接用 {@link ILedgerImportService#buildTemplate()}
 * 生成模板、再往里填格子</b>：这样「模板能不能被自己的解析器认出来」这件事被测到了
 * （模板与解析器一旦脱节，这里的测试立刻红）。
 *
 * <p>最要紧的一条断言是
 * {@link #confirmOnlyTouchesProvidedColumns()}：<b>文件里空着的列必须保持库内原值</b>。
 * 这条如果错了，一次批量补录就会把已有的验收日期、接收管养单位等全部抹成空，
 * 而这类事故在页面上很难第一时间发现。
 */
public class RoadAcceptanceLedgerImportTest extends LandIntegrationTestBase {

    @Autowired
    private ILedgerImportService importService;

    @Autowired
    private IRoadAcceptanceLedgerService ledgerService;

    // ==================================================================
    // 一、模板
    // ==================================================================

    @Test
    public void templateHasTwoSheetsAndAllHeaders() throws Exception {
        Workbook workbook = importService.buildTemplate();
        try {
            assertEquals(2, workbook.getNumberOfSheets(), "模板应有「补录表」与「填表说明」两个 sheet");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);

            // 表头 = 16 个台账/业务列 + 13 个资料列 = 29
            assertEquals(16 + LedgerMaterial.values().length, header.getLastCellNum());
            assertEquals("台账编号", header.getCell(0).getStringCellValue());
            // 13 类资料列必须在末尾且与枚举一一对应
            for (int i = 0; i < LedgerMaterial.values().length; i++) {
                LedgerMaterial material = LedgerMaterial.ordered().get(i);
                String text = header.getCell(16 + i).getStringCellValue();
                assertTrue(text.contains(material.getLabel()),
                        "第 " + (17 + i) + " 列应是资料「" + material.getLabel() + "」，实际=" + text);
            }
            // 说明 sheet 里应把 13 类资料逐条列出
            Sheet instruction = workbook.getSheetAt(1);
            StringBuilder all = new StringBuilder();
            for (int r = 0; r <= instruction.getLastRowNum(); r++) {
                Row row = instruction.getRow(r);
                if (row == null) {
                    continue;
                }
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    if (row.getCell(c) != null) {
                        all.append(row.getCell(c).toString()).append('|');
                    }
                }
            }
            String text = all.toString();
            for (LedgerMaterial material : LedgerMaterial.ordered()) {
                assertTrue(text.contains(material.getLabel()),
                        "填表说明里应解释资料「" + material.getLabel() + "」");
            }
            assertTrue(text.contains("台账编号"), "填表说明里应说明匹配键");
            assertTrue(text.contains("不会被改动"), "填表说明里应说明「空单元格不会被改动」");
        } finally {
            workbook.close();
        }
    }

    // ==================================================================
    // 二、预览与校验
    // ==================================================================

    @Test
    public void previewRejectsFileWithoutLedgerNoHeader() throws Exception {
        MockMultipartFile file = buildFileFromScratch(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("道路名称");
            header.createCell(1).setCellValue("行政区划");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("某条路");
            data.createCell(1).setCellValue("北辰区");
        });
        JeecgBootException error = assertThrows(JeecgBootException.class, () -> importService.preview(file));
        assertTrue(error.getMessage().contains("台账编号"), "错误提示应指明缺少台账编号列：" + error.getMessage());
    }

    @Test
    public void previewReportsRowLevelErrors() throws Exception {
        // ★ 每行用不同的台账编号：同编号会被「文件内重复」这条规则先拦下，
        //   那样就测不出「编号不存在 / 取值非法 / 编号为空」各自的提示了
        java.util.List<String> nos = ledgerNos(3);
        MockMultipartFile file = buildTemplateFile(sheet -> {
            // 第 2 行：合法（命中已有台账）
            setCell(sheet, 1, "台账编号", nos.get(0));
            setCell(sheet, 1, "资料01 施工许可证", "√");
            // 第 3 行：台账编号不存在
            setCell(sheet, 2, "台账编号", "YS-NOT-EXIST-9999");
            // 第 4 行：资料取值无法识别
            setCell(sheet, 3, "台账编号", nos.get(1));
            setCell(sheet, 3, "资料03 竣工图测", "abc");
            // 第 5 行：台账编号为空
            setCell(sheet, 4, "验收单编号", "YS-2026-0001");
            // 第 6 行：状态非法
            setCell(sheet, 5, "台账编号", nos.get(2));
            setCell(sheet, 5, "状态", "已完工");
        });

        LedgerImportResultVO preview = importService.preview(file);
        assertEquals(0, preview.getUpdatedRows(), "预览绝不能写库");
        assertEquals(5, preview.getTotalRows(), "应解析到 5 个数据行");
        assertEquals(4, preview.getErrors().size(), "第 3~6 行各一条错误：" + describe(preview));
        assertEquals(1, preview.getMatchedRows(), "只有第 2 行是「命中且有待写内容」");
        assertEquals(2, preview.getSkippedRows(), "第 4、6 行除了非法项没有别的可写内容");

        Map<Integer, String> errorByRow = new HashMap<>();
        preview.getErrors().forEach(e -> errorByRow.put(e.getRowNum(), e.getMessage()));
        assertTrue(errorByRow.containsKey(3), "第 3 行（编号不存在）应报错：" + describe(preview));
        assertTrue(errorByRow.get(3).contains("不存在"));
        assertTrue(errorByRow.containsKey(4), "第 4 行（资料取值非法）应报错：" + describe(preview));
        assertTrue(errorByRow.get(4).contains("无法识别"));
        assertTrue(errorByRow.containsKey(5), "第 5 行（编号为空）应报错：" + describe(preview));
        assertTrue(errorByRow.containsKey(6), "第 6 行（状态非法）应报错：" + describe(preview));
        assertTrue(errorByRow.get(6).contains("不合法"));
        assertTrue(preview.isAborted(), "有错误时预览应标记为「不会写入」");
    }

    /** 把错误清单拼成一句可读文本，断言失败时能直接看出问题 */
    private String describe(LedgerImportResultVO result) {
        StringBuilder sb = new StringBuilder();
        result.getErrors().forEach(e -> sb.append("[行").append(e.getRowNum()).append(' ').append(e.getMessage()).append(']'));
        result.getWarnings().forEach(w -> sb.append("[警告 ").append(w).append(']'));
        return sb.toString();
    }

    @Test
    public void previewWarnsWhenRoadNameDiffersButDoesNotFail() throws Exception {
        RoadAcceptanceLedger row = firstLedger();
        MockMultipartFile file = buildTemplateFile(sheet -> {
            setCell(sheet, 1, "台账编号", row.getLedgerNo());
            setCell(sheet, 1, "道路名称", "与系统完全不一样的名称");
            setCell(sheet, 1, "资料12 竣工文件", "√");
        });
        LedgerImportResultVO preview = importService.preview(file);
        assertEquals(0, preview.getErrors().size(), "道路名称不一致只是警告，不该算错误");
        assertTrue(preview.getWarnings().stream().anyMatch(w -> w.contains("道路名称与系统不一致")),
                "应给出「道路名称不一致」的警告：" + preview.getWarnings());
    }

    @Test
    public void previewMovesGnqValueOutOfXzqhColumnWithWarning() throws Exception {
        String existingNo = firstLedgerNo();
        MockMultipartFile file = buildTemplateFile(sheet -> {
            setCell(sheet, 1, "台账编号", existingNo);
            setCell(sheet, 1, "行政区划", "生态城");
            setCell(sheet, 1, "资料01 施工许可证", "1");
        });
        LedgerImportResultVO preview = importService.preview(file);
        assertEquals(0, preview.getErrors().size());
        assertTrue(preview.getWarnings().stream().anyMatch(w -> w.contains("功能区") && w.contains("自动归位")),
                "把功能区写进行政区划列时应给归位警告：" + preview.getWarnings());
    }

    // ==================================================================
    // 三、入库
    // ==================================================================

    @Test
    public void confirmRefusesTheWholeBatchWhenErrorsExist() throws Exception {
        String existingNo = firstLedgerNo();
        int before = ledgerService.queryDetail(firstLedgerId()).getMaterialCount();

        MockMultipartFile file = buildTemplateFile(sheet -> {
            setCell(sheet, 1, "台账编号", existingNo);
            setCell(sheet, 1, "资料01 施工许可证", "√");
            setCell(sheet, 1, "资料02 竣工验收报告", "√");
            setCell(sheet, 1, "资料03 竣工图测", "√");
            setCell(sheet, 2, "台账编号", "YS-NOT-EXIST-9999");
        });

        LedgerImportResultVO result = importService.confirm(file, false);
        assertEquals(0, result.getUpdatedRows(), "有错误行时默认整批不入库");
        assertTrue(result.isAborted());
        int after = ledgerService.queryDetail(firstLedgerId()).getMaterialCount();
        assertEquals(before, after, "整批不入库时，连合法行也不该被写入");
    }

    @Test
    public void confirmOnlyTouchesProvidedColumns() throws Exception {
        // 找一条「有接收管养单位」的记录：它是文件里没有的列，用来验证「空着就不改」
        RoadAcceptanceLedger target = null;
        for (int page = 1; page <= 3 && target == null; page++) {
            LedgerQueryDTO query = new LedgerQueryDTO();
            query.setPageNo(page);
            query.setPageSize(100);
            for (RoadAcceptanceLedger row : ledgerService.queryPage(query).getRecords()) {
                if (row.getReceiveUnit() != null && row.getMaterialCount() < 13) {
                    target = row;
                    break;
                }
            }
        }
        if (target == null) {
            System.out.println("[跳过] 没找到「有接收管养单位且资料未满」的记录");
            return;
        }
        // lambda 只能捕获 effectively final 的局部变量，因此这里复制一份
        final RoadAcceptanceLedger finalTarget = target;

        String receiveUnitBefore = target.getReceiveUnit();
        String xzqhBefore = target.getXzqh();
        int materialsBefore = target.getMaterialCount();
        // 挑两类当前未归集的资料来勾选
        LedgerMaterial missing1 = null;
        LedgerMaterial missing2 = null;
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            if (material.valueOf(target) == 0) {
                if (missing1 == null) {
                    missing1 = material;
                } else if (missing2 == null) {
                    missing2 = material;
                    break;
                }
            }
        }
        assertNotNull(missing1, "该记录应有未归集的资料");
        final String prefix1 = "资料" + String.format("%02d", missing1.getSeq());
        final String prefix2 = missing2 == null ? null : "资料" + String.format("%02d", missing2.getSeq());

        MockMultipartFile file = buildTemplateFile(sheet -> {
            setCell(sheet, 1, "台账编号", finalTarget.getLedgerNo());
            setCellMatching(sheet, 1, prefix1, "√");
            if (prefix2 != null) {
                setCellMatching(sheet, 1, prefix2, "是");
            }
            // 只改状态；验收日期、接收管养单位等列一律留空
            setCell(sheet, 1, "状态", "已验收");
        });

        LedgerImportResultVO result = importService.confirm(file, false);
        assertTrue(result.getErrors().isEmpty(), "不该有错误：" + result.getErrors());
        assertEquals(1, result.getUpdatedRows());
        assertTrue(result.getMaterialFilled() >= 1, "应统计到新归集的资料项数");

        RoadAcceptanceLedger after = ledgerService.queryDetail(target.getId());
        // ★ 文件里没写的列必须原样保留
        assertEquals(receiveUnitBefore, after.getReceiveUnit(), "文件里没有「接收管养单位」，不能被清空");
        assertEquals(xzqhBefore, after.getXzqh(), "文件里没有「行政区划」，不能被清空");
        assertEquals(target.getCompleteDate(), after.getCompleteDate(), "文件里没有「实际竣工日期」，不能被清空");
        // 文件里写了的状态要生效
        assertEquals("已验收", after.getStatus());
        // 资料数应增加，且 material_count 与 13 列一致
        assertTrue(after.getMaterialCount() > materialsBefore, "勾选的资料应计入 material_count");
        assertEquals(LedgerMaterial.countMaterials(after), after.getMaterialCount().intValue());
    }

    @Test
    public void confirmWithSkipErrorRowsImportsValidRowsOnly() throws Exception {
        RoadAcceptanceLedger target = firstLedger();
        LedgerMaterial missing = null;
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            if (material.valueOf(target) == 0) {
                missing = material;
                break;
            }
        }
        if (missing == null) {
            System.out.println("[跳过] 该记录 13 类资料已满，无法验证");
            return;
        }
        final String materialPrefix = "资料" + String.format("%02d", missing.getSeq());
        final String ledgerNo = target.getLedgerNo();

        MockMultipartFile file = buildTemplateFile(sheet -> {
            setCell(sheet, 1, "台账编号", ledgerNo);
            setCellMatching(sheet, 1, materialPrefix, "√");
            setCell(sheet, 2, "台账编号", "YS-NOT-EXIST-9999");
        });

        // 不跳过 → 一行都不写
        LedgerImportResultVO strict = importService.confirm(file, false);
        assertEquals(0, strict.getUpdatedRows());

        // 跳过错误行 → 只写合法行
        LedgerImportResultVO relaxed = importService.confirm(file, true);
        assertEquals(1, relaxed.getUpdatedRows(), "勾选跳过错误行时应只导入合法行");
        assertEquals(1, relaxed.getErrors().size(), "错误清单仍然要回执给用户");
        assertEquals(1, missing.valueOf(ledgerService.queryDetail(target.getId())),
                "合法行的资料勾选应已落库");
    }

    @Test
    public void confirmSkipsRowsWithNothingToWrite() throws Exception {
        String existingNo = firstLedgerNo();
        MockMultipartFile file = buildTemplateFile(sheet -> {
            // 只填了台账编号，没有任何要补的内容
            setCell(sheet, 1, "台账编号", existingNo);
        });
        LedgerImportResultVO result = importService.confirm(file, false);
        assertEquals(0, result.getUpdatedRows());
        assertEquals(1, result.getSkippedRows(), "只有编号、没有可写内容的行应计入「跳过」");
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void rejectEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.xlsx", "application/octet-stream", new byte[0]);
        JeecgBootException error = assertThrows(JeecgBootException.class, () -> importService.preview(empty));
        assertTrue(error.getMessage().contains("请选择"));
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 用模板生成一个上传文件，并把数据写在第 2 行起（rowIndex 从 1 开始） */
    private MockMultipartFile buildTemplateFile(Consumer<Sheet> filler) throws Exception {
        Workbook workbook = importService.buildTemplate();
        try {
            Sheet sheet = workbook.getSheetAt(0);
            filler.accept(sheet);
            return toFile(workbook);
        } finally {
            workbook.close();
        }
    }

    /** 自己造一个表头的文件（用于测试「模板不匹配」） */
    private MockMultipartFile buildFileFromScratch(Consumer<Sheet> filler) throws Exception {
        Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        try {
            Sheet sheet = workbook.createSheet("随便");
            filler.accept(sheet);
            return toFile(workbook);
        } finally {
            workbook.close();
        }
    }

    private MockMultipartFile toFile(Workbook workbook) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return new MockMultipartFile("file", "台账补录.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    /** 按表头名写单元格（表头在第 1 行） */
    private void setCell(Sheet sheet, int rowIndex, String header, String value) {
        int column = headerIndex(sheet, header);
        assertTrue(column >= 0, "模板里找不到列：" + header);
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        if (row.getCell(column) == null) {
            row.createCell(column);
        }
        row.getCell(column).setCellValue(value);
    }

    /** 按列名前缀写单元格（资料列的表头形如「资料01 施工许可证」） */
    private void setCellMatching(Sheet sheet, int rowIndex, String headerPrefix, String value) {
        Row header = sheet.getRow(0);
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String text = header.getCell(c) == null ? "" : header.getCell(c).getStringCellValue();
            if (text.startsWith(headerPrefix)) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    row = sheet.createRow(rowIndex);
                }
                if (row.getCell(c) == null) {
                    row.createCell(c);
                }
                row.getCell(c).setCellValue(value);
                return;
            }
        }
        throw new AssertionError("模板里找不到以「" + headerPrefix + "」开头的列");
    }

    private int headerIndex(Sheet sheet, String header) {
        Row row = sheet.getRow(0);
        for (int c = 0; c < row.getLastCellNum(); c++) {
            String text = row.getCell(c) == null ? "" : row.getCell(c).getStringCellValue();
            if (header.equals(text)) {
                return c;
            }
        }
        return -1;
    }

    private RoadAcceptanceLedger firstLedger() {
        LedgerQueryDTO query = new LedgerQueryDTO();
        query.setPageSize(1);
        return ledgerService.queryPage(query).getRecords().get(0);
    }

    private String firstLedgerId() {
        return firstLedger().getId();
    }

    private String firstLedgerNo() {
        return firstLedger().getLedgerNo();
    }

    /** 取前 n 条台账的编号（每行用不同编号，避免撞上「文件内重复」规则） */
    private java.util.List<String> ledgerNos(int n) {
        LedgerQueryDTO query = new LedgerQueryDTO();
        query.setPageSize(n);
        java.util.List<String> nos = new java.util.ArrayList<>(n);
        for (RoadAcceptanceLedger row : ledgerService.queryPage(query).getRecords()) {
            nos.add(row.getLedgerNo());
        }
        assertEquals(n, nos.size(), "库里台账不足 " + n + " 条");
        return nos;
    }
}
