package org.jeecg.modules.land.data.imports.facility;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.imports.facility.service.IFacilityImportService;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;
import org.jeecg.modules.land.data.oplog.mapper.DataChangeLogMapper;
import org.jeecg.modules.land.data.oplog.service.impl.DataChangeLogExportServiceImpl;
import org.jeecg.modules.land.data.oplog.support.ChangeLogSupport;
import org.jeecg.modules.land.data.service.IFacilityAdminService;
import org.jeecg.modules.land.data.service.ILandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 配套导入混合新增/更新的字段留痕与本批导出集成测试。 */
public class FacilityImportTest extends LandIntegrationTestBase {

    private static final String TEST_PREFIX = "TJ-TEST-DATA-FIMP-";

    @Autowired
    private IFacilityImportService importService;

    @Autowired
    private IFacilityAdminService facilityAdminService;

    @Autowired
    private ILandService landService;

    @Autowired
    private DataChangeLogMapper changeLogMapper;

    @Autowired
    private ChangeLogSupport changeLogSupport;

    @Autowired
    private DataChangeLogExportServiceImpl changeLogExportService;

    @Test
    public void blankEntryUnitAndRecorderAllowedAndAuditUsesLoginUser() throws Exception {
        Subject subject = mock(Subject.class);
        LoginUser loginUser = new LoginUser();
        loginUser.setUsername("facility_import_tester");
        loginUser.setRealname("配套导入测试人");
        when(subject.getPrincipal()).thenReturn(loginUser);
        org.apache.shiro.util.ThreadContext.bind(subject);
        try {
            String crzdbh = TEST_PREFIX + "optional-" + System.nanoTime();
            Land land = new Land();
            land.setCrzdbh(crzdbh);
            land.setDkmc("选填配套测试宗地");
            landService.createLand(land);

            FacilityImportResultVO result = importService.confirm(file(sheet -> {
                fillValidRow(sheet, 2, crzdbh, "选填配套测试道路");
                setCell(sheet, 2, "lrdw", "");
                setCell(sheet, 2, "lrr", "");
            }), FacilityDuplicateStrategy.REJECT, false, false);
            assertTrue(result.getErrors().isEmpty(), "配套单位与录入人为空不应阻断导入");
            assertEquals(1, result.getInsertedRows());
            List<java.util.Map<String, Object>> rows = facilityAdminService.queryByLand(crzdbh);
            assertEquals(1, rows.size());
            assertNull(rows.get(0).get("lrdw"));
            assertNull(rows.get(0).get("lrr"));
            Facility saved = facilityAdminService.queryDetail((String) rows.get(0).get("id"));
            assertEquals("facility_import_tester", saved.getCreateAccount(),
                    "系统创建账号仍取当前登录人，不覆盖原始录入字段");
            List<DataChangeLog> logs = changeLogMapper.selectByBatch(result.getBatchId());
            assertEquals(1, logs.size());
            assertEquals("facility_import_tester", logs.get(0).getOperator());
        } finally {
            org.apache.shiro.util.ThreadContext.unbindSubject();
        }
    }

    @Test
    public void mixedInsertUpdateWritesDiffAndExportsSameBatch() throws Exception {
        String crzdbh = TEST_PREFIX + System.nanoTime();
        Land land = new Land();
        land.setCrzdbh(crzdbh);
        land.setDkmc("配套留痕测试宗地");
        landService.createLand(land);
        String projectName = "配套留痕测试道路";

        FacilityImportResultVO inserted = importService.confirm(file(sheet -> {
            fillValidRow(sheet, 2, crzdbh, projectName);
            setCell(sheet, 2, "jsdw", "原建设单位");
        }), FacilityDuplicateStrategy.REJECT, false, false);
        assertEquals(1, inserted.getInsertedRows());
        assertNotNull(inserted.getBatchId());

        FacilityImportResultVO updated = importService.confirm(file(sheet -> {
            fillValidRow(sheet, 2, crzdbh, projectName);
            setCell(sheet, 2, "jsdw", "新建设单位");
            // 同一文件同时包含已有项目更新与新项目新增，验证真正的混合批次。
            fillValidRow(sheet, 3, crzdbh, "配套留痕测试新道路");
            setCell(sheet, 3, "jsdw", "新增建设单位");
        }), FacilityDuplicateStrategy.UPDATE, false, false);
        assertEquals(1, updated.getUpdatedRows());
        assertEquals(1, updated.getInsertedRows());

        List<DataChangeLog> logs = changeLogMapper.selectByBatch(updated.getBatchId());
        assertEquals(2, logs.size(), "新增和更新各一条，REJECT/SKIP 不得产生额外日志");
        DataChangeLog updateLog = null;
        for (DataChangeLog log : logs) {
            if (DataChangeLog.ACTION_UPDATE.equals(log.getAction())) {
                updateLog = log;
            }
        }
        assertNotNull(updateLog);
        assertTrue(updateLog.getChangeSummary().contains("Excel 第 3 行"));
        boolean foundDiff = false;
        for (java.util.Map<String, Object> detail : changeLogSupport.parseDetail(updateLog.getChangeDetail())) {
            if ("jsdw".equals(detail.get("field"))) {
                foundDiff = true;
                assertEquals("原建设单位", String.valueOf(detail.get("before")));
                assertEquals("新建设单位", String.valueOf(detail.get("after")));
            }
        }
        assertTrue(foundDiff, "配套更新必须保留建设单位改前改后值");

        Workbook export = changeLogExportService.buildBatchExport(updated.getBatchId());
        try {
            Sheet sheet = export.getSheetAt(0);
            boolean foundExport = false;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if ("建设单位".equals(row.getCell(5).getStringCellValue())
                        && "原建设单位".equals(row.getCell(6).getStringCellValue())
                        && "新建设单位".equals(row.getCell(7).getStringCellValue())) {
                    foundExport = true;
                    assertEquals("配套项目", row.getCell(1).getStringCellValue());
                    assertEquals(projectName, row.getCell(4).getStringCellValue());
                    assertEquals(3, (int) row.getCell(2).getNumericCellValue());
                }
            }
            assertTrue(foundExport, "导出必须包含配套字段原值与新值");
        } finally {
            export.close();
        }
    }

    private MockMultipartFile file(Consumer<Sheet> filler) throws Exception {
        Workbook workbook = importService.buildTemplate();
        try {
            filler.accept(workbook.getSheetAt(0));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "配套导入.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } finally {
            workbook.close();
        }
    }

    private void fillValidRow(Sheet sheet, int rowIndex, String crzdbh, String projectName) {
        setCell(sheet, rowIndex, "crzdbh", crzdbh);
        setCell(sheet, rowIndex, "ptxmmc", projectName);
        setCell(sheet, rowIndex, "lrdw", "测试录入单位");
        setCell(sheet, rowIndex, "lrr", "测试录入人");
        setCell(sheet, rowIndex, "lxdh", "13800000000");
    }

    private void setCell(Sheet sheet, int rowIndex, String header, String value) {
        Row headerRow = sheet.getRow(0);
        Integer column = null;
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            if (header.equals(headerRow.getCell(i).getStringCellValue())) {
                column = i;
                break;
            }
        }
        assertNotNull(column, "模板缺少字段：" + header);
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        if (row.getCell(column) == null) {
            row.createCell(column);
        }
        row.getCell(column).setCellValue(value);
    }
}
