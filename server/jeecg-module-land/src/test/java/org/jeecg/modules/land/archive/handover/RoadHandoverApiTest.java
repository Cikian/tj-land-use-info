package org.jeecg.modules.land.archive.handover;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.common.exception.JeecgBootExceptionHandler;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.archive.handover.controller.RoadHandoverController;
import org.jeecg.modules.land.archive.handover.dto.HandoverSaveDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Description: 道路交付及养护协议移交事项 - 接口级（HTTP）集成测试
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>用 MockMvc 把 {@link RoadHandoverController} 的 16 个接口全打一遍：
 * URL 路由、参数绑定、JSON 序列化、服务层与真实 SQL、导出字节流。
 *
 * <p><b>不覆盖什么</b>：鉴权（@RequiresPermissions 由 Shiro AOP 织入，standaloneSetup 不装 Shiro；
 * 权限的正确性由数据库侧守卫查询保证）与真实容器过滤器链。
 *
 * <p>★ 必须注册 {@link JeecgBootExceptionHandler}：真实应用里它把业务异常转成
 * {@code {"success":false,...}}，否则「业务校验失败」会表现成 500（台账模块踩过）。
 */
public class RoadHandoverApiTest extends LandIntegrationTestBase {

    private static final String BASE = "/land/archive/handover";

    @Autowired
    private RoadHandoverController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new JeecgBootExceptionHandler())
                .build();
    }

    // ==================================================================
    // 一、查询类（4 个）
    // ==================================================================

    @Test
    public void listReturnsPagedRows() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/list").param("pageNo", "1").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.records.length()").value(5))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"seq\":1"), "列表必须带序号");
        assertTrue(body.contains("\"handoverNo\":\"YJ-"), "列表必须带移交编号");
    }

    @Test
    public void listSupportsAllDocumentedConditions() throws Exception {
        mockMvc.perform(get(BASE + "/list").param("roadName", "路")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("handoverNo", "YJ-")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("xzqh", "北辰区")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("crzdbh", "津")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("handoverType", "正式移交"))
                .andExpect(jsonPath("$.result.total").value(84));
        mockMvc.perform(get(BASE + "/list").param("status", "已移交"))
                .andExpect(jsonPath("$.result.total").value(84));
        mockMvc.perform(get(BASE + "/list")
                        .param("beginAgreementDate", "2020-01-01")
                        .param("endAgreementDate", "2030-01-01"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list")
                        .param("beginHandoverDate", "2020-01-01")
                        .param("endHandoverDate", "2030-01-01"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("hasArchive", "false"))
                .andExpect(jsonPath("$.result.total").value(84));
        mockMvc.perform(get(BASE + "/list").param("keyword", "YJ-2010"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    public void queryByIdReturnsDetail() throws Exception {
        String id = firstId();
        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.id").value(id))
                .andExpect(jsonPath("$.result.relatedArchives").exists());
        mockMvc.perform(get(BASE + "/queryById").param("id", "not-exist"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    public void statAndCountByStatus() throws Exception {
        mockMvc.perform(get(BASE + "/stat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(84))
                .andExpect(jsonPath("$.result.missingAgreementCount").value(84))
                .andExpect(jsonPath("$.result.orphanCount").value(2))
                .andExpect(jsonPath("$.result.byStatus.length()").value(3))
                .andExpect(jsonPath("$.result.byYear.length()").value(12))
                .andExpect(jsonPath("$.result.byType").isArray())
                .andExpect(jsonPath("$.result.byXzqh").isArray())
                .andExpect(jsonPath("$.result.byDldj").isArray())
                .andExpect(jsonPath("$.result.byReceiveUnit").isArray());
        mockMvc.perform(get(BASE + "/countByStatus"))
                .andExpect(jsonPath("$.result.length()").value(3));
    }

    // ==================================================================
    // 二、编号（2 个）
    // ==================================================================

    @Test
    public void generateNoAndCheckNo() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/generateNo").param("year", "2026"))
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        String generated = objectMapper.readTree(result.getResponse().getContentAsString()).get("result").asText();
        assertTrue(generated.matches("YJ-2026-\\d{4}"), "编号格式：" + generated);
        mockMvc.perform(get(BASE + "/checkNo").param("handoverNo", generated))
                .andExpect(jsonPath("$.success").value(true));
        String existing = firstHandoverNo();
        mockMvc.perform(get(BASE + "/checkNo").param("handoverNo", existing))
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==================================================================
    // 三、增删改（6 个）
    // ==================================================================

    @Test
    public void addEditStatusArchiveDeleteRoundTrip() throws Exception {
        HandoverSaveDTO dto = new HandoverSaveDTO();
        dto.setHandoverNo(TEST_HANDOVER_NO_PREFIX + "9001");
        dto.setRoadName("接口测试道路（可删）");
        dto.setXzqh("西青区");
        dto.setHandoverType("道路交付");
        dto.setStatus("待移交");
        MvcResult added = mockMvc.perform(post(BASE + "/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        String id = objectMapper.readTree(added.getResponse().getContentAsString()).get("result").asText();
        assertNotNull(id);

        // 编辑（PUT）：补上协议信息
        HandoverSaveDTO edit = new HandoverSaveDTO();
        edit.setId(id);
        edit.setHandoverNo(dto.getHandoverNo());
        edit.setRoadName("接口测试道路-改名（可删）");
        edit.setHandoverType("养护协议");
        edit.setAgreementNo("YH-2026-TEST-9");
        edit.setReceiveUnit("区城管委");
        edit.setStatus("移交中");
        mockMvc.perform(put(BASE + "/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(jsonPath("$.result.roadName").value("接口测试道路-改名（可删）"))
                .andExpect(jsonPath("$.result.agreementNo").value("YH-2026-TEST-9"))
                .andExpect(jsonPath("$.result.status").value("移交中"));

        // 状态
        mockMvc.perform(post(BASE + "/status").param("id", id).param("status", "已移交"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(post(BASE + "/status").param("id", id).param("status", "已完工"))
                .andExpect(jsonPath("$.success").value(false));

        // 关联 / 取消关联档案
        String archiveId = pickAnyArchiveId();
        if (archiveId != null) {
            mockMvc.perform(post(BASE + "/linkArchive").param("id", id).param("archiveId", archiveId))
                    .andExpect(jsonPath("$.success").value(true));
            mockMvc.perform(get(BASE + "/queryById").param("id", id))
                    .andExpect(jsonPath("$.result.archiveId").value(archiveId));
            mockMvc.perform(post(BASE + "/unlinkArchive").param("id", id))
                    .andExpect(jsonPath("$.success").value(true));
            mockMvc.perform(get(BASE + "/queryById").param("id", id))
                    .andExpect(jsonPath("$.result.archiveId").doesNotExist());
        } else {
            System.out.println("[跳过] 库里没有档案数据，无法验证关联档案接口");
        }

        // 删除
        mockMvc.perform(delete(BASE + "/delete").param("id", id))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    public void addWithoutRoadNameReturnsBusinessError() throws Exception {
        HandoverSaveDTO dto = new HandoverSaveDTO();
        dto.setStatus("待移交");
        mockMvc.perform(post(BASE + "/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    public void deleteBatchRemovesAllGivenIds() throws Exception {
        String[] ids = new String[2];
        for (int i = 0; i < 2; i++) {
            HandoverSaveDTO dto = new HandoverSaveDTO();
            dto.setHandoverNo(TEST_HANDOVER_NO_PREFIX + "910" + i);
            dto.setRoadName("接口测试批量删除-" + i + "（可删）");
            MvcResult result = mockMvc.perform(post(BASE + "/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();
            ids[i] = objectMapper.readTree(result.getResponse().getContentAsString()).get("result").asText();
        }
        mockMvc.perform(delete(BASE + "/deleteBatch").param("ids", String.join(",", ids)))
                .andExpect(jsonPath("$.success").value(true));
        for (String id : ids) {
            mockMvc.perform(get(BASE + "/queryById").param("id", id))
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ==================================================================
    // 四、关联档案查询（2 个）
    // ==================================================================

    @Test
    public void relatedArchivesAndPick() throws Exception {
        mockMvc.perform(get(BASE + "/relatedArchives").param("id", firstId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/archive/pick").param("limit", "5"))
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================================================================
    // 五、导出（1 个）
    // ==================================================================

    @Test
    public void exportXlsReturnsRealXlsxBytes() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/exportXls").param("status", "已移交"))
                .andExpect(status().isOk())
                .andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0);
        // 50 4B 03 04 = OOXML(ZIP)；若是 D0 CF 11 E0 说明漏了 ExcelType.XSSF（Excel 打不开）
        assertEquals(0x50, bytes[0] & 0xFF);
        assertEquals(0x4B, bytes[1] & 0xFF);
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition);
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private String firstId() throws Exception {
        String body = mockMvc.perform(get(BASE + "/list").param("pageSize", "1"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("result").get("records").get(0).get("id").asText();
    }

    private String firstHandoverNo() throws Exception {
        String body = mockMvc.perform(get(BASE + "/list").param("pageSize", "1"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("result").get("records").get(0).get("handoverNo").asText();
    }

    private String pickAnyArchiveId() throws Exception {
        String body = mockMvc.perform(get(BASE + "/archive/pick").param("limit", "1"))
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(body).get("result");
        if (arr == null || arr.size() == 0) {
            return null;
        }
        return arr.get(0).get("id").asText();
    }
}
