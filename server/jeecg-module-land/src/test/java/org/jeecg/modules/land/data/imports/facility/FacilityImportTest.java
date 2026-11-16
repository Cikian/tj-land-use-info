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

    /**
     * P1-05：建设性质「现状」、道路等级「快速路」、功能区「生态城」必须能导入并原值落库。
     *
     * <p>★ 为什么这三个值要单独立一条用例：它们都是「业务说确实存在、旧字典里没有」的取值
     * （P1-05 测试现象就是这类值在页面上能选、导入却报「不在允许取值内」）。
     * 断言的是<b>原值</b>而不是归一化后的值 —— 「快速路」不能被并进「城市主干路」，
     * 「生态城」也不能被塞进行政区划，否则统计口径会悄悄错掉。
     */
    @Test
    public void buildNatureRoadLevelAndFunctionalZoneImportAsIs() throws Exception {
        String crzdbh = TEST_PREFIX + "p105-" + System.nanoTime();
        Land land = new Land();
        land.setCrzdbh(crzdbh);
        land.setDkmc("P1-05 取值测试宗地");
        landService.createLand(land);

        FacilityImportResultVO result = importService.confirm(file(sheet -> {
            fillValidRow(sheet, 2, crzdbh, "P1-05 取值测试道路");
            setCell(sheet, 2, "jsxx", "现状");
            setCell(sheet, 2, "dldj", "快速路");
            setCell(sheet, 2, "gnq", "生态城");
            // 功能区与行政区划分列：行政区划仍是 16 区里的滨海新区
            setCell(sheet, 2, "xzqh", "滨海新区");
        }), FacilityDuplicateStrategy.REJECT, false, false);

        assertTrue(result.getErrors().isEmpty(), "建设性质/道路等级/功能区确认值不该被拦下：" + result.getErrors());
        assertEquals(1, result.getInsertedRows());

        List<java.util.Map<String, Object>> rows = facilityAdminService.queryByLand(crzdbh);
        assertEquals(1, rows.size());
        Facility saved = facilityAdminService.queryDetail((String) rows.get(0).get("id"));
        assertEquals("现状", saved.getJsxx(), "建设性质「现状」必须原值落库");
        assertEquals("快速路", saved.getDldj(), "道路等级「快速路」是独立一档，不得并入主干路");
        assertEquals("生态城", saved.getGnq(), "功能区原值落库");
        assertEquals("滨海新区", saved.getXzqh(), "行政区划仍是 16 区标准值");
    }

    /**
     * P1-06：<b>不传策略</b>（默认 upsert）时，混合文件「已有行更新 + 新行插入」且不重复插入。
     *
     * <p>★ 这条用例锁住的是「默认值」本身：传 {@code null} 而不是
     * {@code FacilityDuplicateStrategy.UPSERT}，因为用户从页面导入时前端根本没传这个参数
     * （或传了老版本的默认值），后端 {@code normalize} 的兜底与枚举 {@code of(null)}
     * 才是实际生效的那一处。若三处默认值不一致，这条用例会红。
     *
     * <p>断言「不重复插入」用<b>行数</b>（同一宗地下正好 2 条），
     * 而不是只看计数 —— 计数对但库里多出一条重复行，正是最坏的组合。
     */
    @Test
    public void defaultStrategyUpsertsMixedFileWithoutDuplicates() throws Exception {
        String crzdbh = TEST_PREFIX + "p106-" + System.nanoTime();
        Land land = new Land();
        land.setCrzdbh(crzdbh);
        land.setDkmc("P1-06 默认策略测试宗地");
        landService.createLand(land);
        // ★ 名称里不要带空格：落库走 DataSupport.normalizeName 会去掉所有空白，
        //   带空格的期望值与该口径不符（这条用例要测的是「默认策略」，不是名称归一）。
        String existingProject = "P1-06已有配套";
        String newProject = "P1-06新增配套";

        // 第一次入库：库里没有重复，默认策略与其它策略等价
        FacilityImportResultVO first = importService.confirm(file(sheet -> {
            fillValidRow(sheet, 2, crzdbh, existingProject);
            setCell(sheet, 2, "jsdw", "原建设单位");
        }), null, false, false);
        assertEquals(1, first.getInsertedRows(), "首次入库应新增 1 条");
        assertEquals("upsert", first.getDuplicateStrategy(), "不传策略时后端应回执 upsert");

        // 第二次：同一文件里既有「已有配套的更新」，又有「新配套的新增」
        FacilityImportResultVO mixed = importService.confirm(file(sheet -> {
            fillValidRow(sheet, 2, crzdbh, existingProject);
            setCell(sheet, 2, "jsdw", "新建设单位");
            // 已有行的「录入单位」留空：空单元格不得把库里的原值抹掉
            setCell(sheet, 2, "lrdw", "");
            fillValidRow(sheet, 3, crzdbh, newProject);
            setCell(sheet, 3, "jsdw", "新配套建设单位");
        }), null, false, false);

        assertTrue(mixed.getErrors().isEmpty(), "默认策略下混合文件不该报错：" + mixed.getErrors());
        assertEquals(1, mixed.getUpdatedRows(), "已有配套应被更新：" + mixed.getUpdatedRows());
        assertEquals(1, mixed.getInsertedRows(), "新配套应被新增：" + mixed.getInsertedRows());
        assertEquals(0, mixed.getSkippedDuplicates(), "默认策略不跳过任何行");

        List<java.util.Map<String, Object>> rows = facilityAdminService.queryByLand(crzdbh);
        assertEquals(2, rows.size(), "同一宗地下应正好 2 条配套（重复项不得重复插入）");
        boolean foundUpdated = false;
        boolean foundInserted = false;
        for (java.util.Map<String, Object> row : rows) {
            Facility saved = facilityAdminService.queryDetail((String) row.get("id"));
            if (existingProject.equals(saved.getPtxmmc())) {
                assertEquals("新建设单位", saved.getJsdw(), "已有配套按非空单元格更新");
                assertEquals("测试录入单位", saved.getLrdw(),
                        "文件里 lrdw 留空 = 不改动，库里原值必须保留");
                foundUpdated = true;
            } else if (newProject.equals(saved.getPtxmmc())) {
                assertEquals("新配套建设单位", saved.getJsdw());
                foundInserted = true;
            }
        }
        assertTrue(foundUpdated, "应能找到被更新的那条");
        assertTrue(foundInserted, "应能找到新增的那条");
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
