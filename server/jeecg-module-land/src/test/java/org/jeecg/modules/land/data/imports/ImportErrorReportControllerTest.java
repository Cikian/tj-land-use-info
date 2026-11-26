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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

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

    // ------------------------------------------------------------------
    // 跨域（CORS）回归
    // ------------------------------------------------------------------

    /**
     * ★ 回归：跨域下载必须保住 CORS 响应头（两类回执都要）。
     *
     * <p><b>2026-11-24 真机故障</b>：点「下载错误回执」报
     * {@code Access to fetch at '.../errorReport?token=...' from origin 'http://localhost:3000'
     * has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present}。
     *
     * <p><b>根因</b>：Controller 的 {@code writeWorkbook} / {@code writeJsonError} 调了
     * {@link javax.servlet.http.HttpServletResponse#reset()}，而按 Servlet 规范
     * {@code reset()} 会把**响应头连同缓冲区一起清空** —— 其中就包括
     * {@code CorsFilter}（{@code WebMvcConfiguration#corsFilter}）与 {@code JwtFilter#preHandle}
     * 在进入 Controller 之前加好的 {@code Access-Control-Allow-Origin}。
     * 于是浏览器认定「响应没有授权跨域」，前端 {@code fetch} 直接被拦成「Failed to fetch」。
     *
     * <p><b>为什么只有回执下载会中招</b>：模板/变更明细走 {@code window.open} 原生下载，
     * 导航不参与 CORS 校验；回执因为要 POST 文件内容，只能用 {@code fetch + blob}，
     * 这是全项目唯一受 CORS 约束的二进制下载。
     *
     * <p>用例用真实的 {@link CorsFilter}（与线上同一份配置口径）在 Controller 前面加头，
     * 再断言经过 {@code reset()} 之后头还在。
     */
    @Test
    public void crossOriginBinaryDownloadKeepsCorsHeaders() throws Exception {
        ILandImportService service = mock(ILandImportService.class);
        LandImportResultVO result = new LandImportResultVO();
        result.addError(3, "TJ-TEST-CORS-3", "crzdbh", "出让宗地编号", "编号重复");
        when(service.buildErrorReport(any(), eq(LandDuplicateStrategy.REJECT)))
                .thenReturn(new LandImportSupport().buildErrorReport(result, rowNum -> null));

        LandImportController controller = new LandImportController();
        ReflectionTestUtils.setField(controller, "importService", service);
        MvcResult response = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(allowAllCorsFilter())
                .build()
                .perform(crossOrigin(multipart("/land/data/import/errorReport").file(excelFile())))
                .andExpect(status().isOk())
                .andReturn();

        assertCorsHeadersUsable(response, "经营性用地错误回执", true);
        assertXlsxResponse(response, "经营性用地导入错误回执_");
    }

    /** 配套回执同构：同一个 @reset 陷阱、同一个断言（真机两类都能复现） */
    @Test
    public void crossOriginFacilityErrorReportKeepsCorsHeaders() throws Exception {
        IFacilityImportService service = mock(IFacilityImportService.class);
        FacilityImportResultVO result = new FacilityImportResultVO();
        result.addError(3, "TJ-TEST-CORS-F3", "lxdh", "联系电话", "手机号格式不正确");
        when(service.buildErrorReport(any(), any(), anyBoolean()))
                .thenReturn(new FacilityImportSupport().buildErrorReport(result, rowNum -> null));

        FacilityImportController controller = new FacilityImportController();
        ReflectionTestUtils.setField(controller, "importService", service);
        MvcResult response = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(allowAllCorsFilter())
                .build()
                .perform(crossOrigin(multipart("/land/data/facilityImport/errorReport").file(excelFile())))
                .andExpect(status().isOk())
                .andReturn();

        assertCorsHeadersUsable(response, "配套信息错误回执", true);
        assertXlsxResponse(response, "配套信息导入错误回执_");
    }

    /**
     * 失败分支同样要保住 CORS 头：{@code writeJsonError} 也是 reset 后重写，
     * 否则前端拿不到后端给的「为什么失败」，只剩一句笼统的「Failed to fetch」。
     */
    @Test
    public void crossOriginJsonErrorKeepsCorsHeaders() throws Exception {
        ILandImportService service = mock(ILandImportService.class);
        Workbook broken = mock(Workbook.class);
        doThrow(new IOException("模拟 POI 序列化失败")).when(broken).write(any(OutputStream.class));
        when(service.buildErrorReport(any(org.springframework.web.multipart.MultipartFile.class),
                any(LandDuplicateStrategy.class))).thenReturn(broken);

        LandImportController controller = new LandImportController();
        ReflectionTestUtils.setField(controller, "importService", service);
        MvcResult response = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(allowAllCorsFilter())
                .build()
                .perform(crossOrigin(multipart("/land/data/import/errorReport").file(excelFile())))
                .andExpect(status().isInternalServerError())
                .andReturn();

        assertCorsHeadersUsable(response, "错误回执的 JSON 失败响应", false);
        String body = new String(response.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.contains("模拟 POI 序列化失败"), "失败原因必须仍能跨域读到：" + body);
    }

    /** 与线上 {@code WebMvcConfiguration#corsFilter} 同口径：放行任意来源 + 允许携带凭证 */
    private CorsFilter allowAllCorsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /** 模拟浏览器从管理端/大屏端 devServer 发出的跨域请求（带 Origin 与自定义令牌头） */
    private MockHttpServletRequestBuilder crossOrigin(MockHttpServletRequestBuilder builder) {
        return builder
                .header("Origin", "http://localhost:3000")
                .header("X-Access-Token", "stub-token");
    }

    /**
     * 浏览器放行一个跨域响应要同时满足：回显来源 + 允许凭证；成功分支还必须能读到文件名字段。
     * 少任何一个，前端都会退化成「Failed to fetch」且拿不到错误原因。
     *
     * @param expectExposeHeaders 二进制成功响应才需要（前端要从 Content-Disposition 取文件名）；
     *                            JSON 失败响应只回状态与原因，不要求这一项
     */
    private void assertCorsHeadersUsable(MvcResult result, String what, boolean expectExposeHeaders) {
        assertEquals("http://localhost:3000",
                result.getResponse().getHeader("Access-Control-Allow-Origin"),
                what + "：reset() 之后 Access-Control-Allow-Origin 丢了，浏览器会直接拦掉这个响应");
        assertEquals("true",
                result.getResponse().getHeader("Access-Control-Allow-Credentials"),
                what + "：Access-Control-Allow-Credentials 丢了");
        if (expectExposeHeaders) {
            assertNotNull(result.getResponse().getHeader("Access-Control-Expose-Headers"),
                    what + "：跨域下前端读不到 Content-Disposition，文件名与错误判定都会失效");
        }
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
