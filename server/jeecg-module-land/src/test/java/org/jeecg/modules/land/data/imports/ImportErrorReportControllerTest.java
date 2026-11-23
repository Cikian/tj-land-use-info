package org.jeecg.modules.land.data.imports;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jeecg.modules.land.data.imports.controller.LandImportController;
import org.jeecg.modules.land.data.imports.facility.FacilityImportField;
import org.jeecg.modules.land.data.imports.facility.FacilityImportResultVO;
import org.jeecg.modules.land.data.imports.facility.controller.FacilityImportController;
import org.jeecg.modules.land.data.imports.facility.service.IFacilityImportService;
import org.jeecg.modules.land.data.imports.facility.support.FacilityImportSupport;
import org.jeecg.modules.land.data.imports.service.ILandImportService;
import org.jeecg.modules.land.data.imports.support.LandImportSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 两类导入错误回执的 HTTP 二进制契约测试。
 *
 * <p>不启动完整 Spring 容器、不连接数据库：这里只验证 multipart 参数能进入 Controller、
 * Workbook 先序列化再写响应、跨域文件名可读，以及异常不会残留半个 xlsx。
 * 生产鉴权过滤器不在 standalone MockMvc 内，鉴权链路须另做真实服务验证。
 */
public class ImportErrorReportControllerTest {

    private static final String XLSX_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Test
    public void landMultipartReturnsReadableWorkbookWithFormulaDiagnostics() throws Exception {
        ILandImportService service = mock(ILandImportService.class);
        LandImportResultVO result = new LandImportResultVO();
        result.addError(3, "TJ-TEST-RECEIPT-3", "crj", "出让金", "公式没有缓存结果");
        result.addError(4, "TJ-TEST-RECEIPT-4", "lxdh", "联系电话", "手机号格式不正确");
        List<String> sourceRow = sourceRow(LandImportField.templated(), "crzdbh", "TJ-TEST-RECEIPT-3");
        sourceRow.set(columnIndex(LandImportField.templated(), "crj"), "=1+1");
        when(service.buildErrorReport(any(), eq(LandDuplicateStrategy.REJECT)))
                .thenReturn(new LandImportSupport().buildErrorReport(result,
                        rowNum -> rowNum == 3 ? sourceRow : null));

        LandImportController controller = new LandImportController();
        ReflectionTestUtils.setField(controller, "importService", service);
        MvcResult response = MockMvcBuilders.standaloneSetup(controller).build()
                .perform(multipart("/land/data/import/errorReport")
                        .file(excelFile())
                        .param("duplicateStrategy", "reject"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX_TYPE + ";charset=UTF-8"))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        "Content-Disposition, Content-Type, Content-Length"))
                .andReturn();

        assertXlsxResponse(response, "经营性用地导入错误回执_");
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(response.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheet("错误回执");
            assertNotNull(sheet);
            assertEquals(2, sheet.getLastRowNum(), "两条错误必须分别保留行号与诊断信息");
            Row first = sheet.getRow(1);
            assertEquals(3, (int) first.getCell(sheet.getRow(0).getLastCellNum() - 3).getNumericCellValue());
            assertEquals("出让金", first.getCell(sheet.getRow(0).getLastCellNum() - 2).getStringCellValue());
            assertTrue(first.getCell(sheet.getRow(0).getLastCellNum() - 1).getStringCellValue()
                    .contains("没有缓存"));
            assertEquals("=1+1", first.getCell(columnIndex(LandImportField.templated(), "crj"))
                    .getStringCellValue(), "回执必须保留原公式而不是只留校验后的值");
        }
    }

    @Test
    public void facilityMultipartReturnsWorkbookWithOrphanSheet() throws Exception {
        IFacilityImportService service = mock(IFacilityImportService.class);
        FacilityImportResultVO result = new FacilityImportResultVO();
        result.setAllowOrphan(true);
        result.addError(3, "TJ-TEST-RECEIPT-F3", "lxdh", "联系电话", "手机号格式不正确");
        result.addOrphan(4, "TJ-TEST-ORPHAN-4", "测试配套道路", "宗地编号未登记");
        List<String> sourceRow = sourceRow(FacilityImportField.templated(), "crzdbh", "TJ-TEST-RECEIPT-F3");
        when(service.buildErrorReport(any(), any(), anyBoolean()))
                .thenReturn(new FacilityImportSupport().buildErrorReport(result,
                        rowNum -> rowNum == 3 ? sourceRow : null));

        FacilityImportController controller = new FacilityImportController();
        ReflectionTestUtils.setField(controller, "importService", service);
        MvcResult response = MockMvcBuilders.standaloneSetup(controller).build()
                .perform(multipart("/land/data/facilityImport/errorReport")
                        .file(excelFile())
                        .param("duplicateStrategy", "upsert")
                        .param("allowOrphan", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX_TYPE + ";charset=UTF-8"))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        "Content-Disposition, Content-Type, Content-Length"))
                .andReturn();

        assertXlsxResponse(response, "配套信息导入错误回执_");
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(response.getResponse().getContentAsByteArray()))) {
            assertNotNull(workbook.getSheet("错误回执"));
            Sheet orphan = workbook.getSheet("孤儿清单");
            assertNotNull(orphan, "allowOrphan=true 时孤儿不进错误行，但仍必须随回执下载");
            assertEquals(4, (int) orphan.getRow(1).getCell(0).getNumericCellValue());
            assertEquals("TJ-TEST-ORPHAN-4", orphan.getRow(1).getCell(1).getStringCellValue());
            assertEquals("测试配套道路", orphan.getRow(1).getCell(2).getStringCellValue());
            assertTrue(orphan.getRow(1).getCell(3).getStringCellValue().contains("未登记"));
        }
    }

    @Test
    public void poiSerializationFailureResetsResponseToJson() throws Exception {
        ILandImportService service = mock(ILandImportService.class);
        Workbook broken = mock(Workbook.class);
        doThrow(new IOException("模拟 POI 序列化失败")).when(broken).write(any(OutputStream.class));
        when(service.buildErrorReport(any(org.springframework.web.multipart.MultipartFile.class),
                any(LandDuplicateStrategy.class))).thenReturn(broken);

        LandImportController controller = new LandImportController();
        ReflectionTestUtils.setField(controller, "importService", service);
        MvcResult response = MockMvcBuilders.standaloneSetup(controller).build()
                .perform(multipart("/land/data/import/errorReport").file(excelFile()))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
                .andReturn();
        String body = new String(response.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.startsWith("{"), "异常响应不得带 ZIP/xlsx 前缀：" + body);
        assertTrue(body.contains("模拟 POI 序列化失败"), "应把真实序列化异常交给前端：" + body);
        assertTrue(body.contains("\"success\":false"));
    }

    private MockMultipartFile excelFile() {
        return new MockMultipartFile("file", "导入错误.xlsx", XLSX_TYPE,
                new byte[]{0x50, 0x4b, 0x03, 0x04});
    }

    private void assertXlsxResponse(MvcResult result, String fileNamePrefix) throws Exception {
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 4, "xlsx blob 不得为空");
        assertEquals(0x50, bytes[0] & 0xff);
        assertEquals(0x4b, bytes[1] & 0xff);
        assertEquals(bytes.length, result.getResponse().getContentLength());
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains("filename*=UTF-8''"));
        assertTrue(disposition.contains(java.net.URLEncoder.encode(fileNamePrefix, "UTF-8")
                .replace("+", "%20")));
    }

    private <T> List<String> sourceRow(List<T> fields, String keyColumn, String keyValue) {
        List<String> values = new ArrayList<>(Collections.nCopies(fields.size(), ""));
        values.set(columnIndex(fields, keyColumn), keyValue);
        return values;
    }

    private int columnIndex(List<?> fields, String column) {
        for (int i = 0; i < fields.size(); i++) {
            Object field = fields.get(i);
            String value = field instanceof LandImportField.FieldSpec
                    ? ((LandImportField.FieldSpec) field).getColumn()
                    : ((FacilityImportField.FieldSpec) field).getColumn();
            if (column.equals(value)) {
                return i;
            }
        }
        throw new IllegalArgumentException("模板缺少列：" + column);
    }
}
