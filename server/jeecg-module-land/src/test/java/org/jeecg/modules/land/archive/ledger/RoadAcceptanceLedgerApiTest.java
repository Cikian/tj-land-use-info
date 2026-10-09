package org.jeecg.modules.land.archive.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.common.exception.JeecgBootExceptionHandler;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.archive.ledger.controller.RoadAcceptanceLedgerController;
import org.jeecg.modules.land.archive.ledger.dto.LedgerSaveDTO;
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
 * @Description: 道路设施验收及移交资料台账 - 接口级（HTTP）集成测试
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>用 {@link MockMvc} 把 {@link RoadAcceptanceLedgerController} 的 <b>17 个接口全打一遍</b>：
 * URL 路由、参数绑定（GET query string / POST body / PUT）、JSON 序列化、
 * 服务层与真实 SQL、以及导出接口写出的字节流。
 *
 * <p><b>★ 本测试不覆盖什么，要说清楚</b>：
 * <ul>
 *   <li><b>鉴权</b>：{@code @RequiresPermissions} 由 Shiro 的 AOP 织入，standaloneSetup 不装 Shiro，
 *       所以这里不做「无权限应 401」的断言。权限的正确性由数据库侧的守卫查询保证
 *       （sql/ledger/04_ledger_permission_buttons.sql 的 3 段自检：6 个按钮权限、全部授权 admin、
 *       菜单行不残留权限码、有按钮的菜单 is_leaf=0）。</li>
 *   <li><b>真实 Servlet 容器</b>：用的是 MockMvc 而不是起 Tomcat，因此过滤器链
 *       （JWT、跨域等）不在覆盖范围内 —— 那些是 jeecg 框架既有能力，与本模块无关。</li>
 * </ul>
 *
 * <p>整个测试类跑在一个事务里、结束回滚，所以「新增/编辑/删除」的调用不会给库里留数据。
 */
public class RoadAcceptanceLedgerApiTest extends LandIntegrationTestBase {

    private static final String BASE = "/land/archive/ledger";

    @Autowired
    private RoadAcceptanceLedgerController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        // ★ 必须把 jeecg 的全局异常处理器装上：真实应用里它把 JeecgBootException
        //   转成 {"success":false,"message":"..."}，而 standaloneSetup 默认不注册任何
        //   @RestControllerAdvice，会让「业务校验失败」表现成 500 NestedServletException。
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new JeecgBootExceptionHandler())
                .build();
    }

    // ==================================================================
    // 一、查询类（8 个）
    // ==================================================================

    @Test
    public void listReturnsPagedRows() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/list")
                        .param("pageNo", "1")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.records.length()").value(5))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"seq\":1"), "列表必须带序号 seq");
        assertTrue(body.contains("\"materialTotal\":13"), "列表必须带 13 类资料的统计");
        assertTrue(body.contains("\"ledgerNo\":\"YS-"), "列表必须带台账编号");
    }

    @Test
    public void listSupportsAllDocumentedConditions() throws Exception {
        // 清单 §6.3.7 明列的 6 个条件 + 资料缺失 + 关键词，逐个打到接口上确保能绑定
        mockMvc.perform(get(BASE + "/list").param("roadName", "路")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("xzqh", "北辰区")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("acceptanceType", "竣工验收")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("acceptanceResult", "合格")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("status", "已移交"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.total").value(84));
        mockMvc.perform(get(BASE + "/list")
                        .param("beginAcceptanceDate", "2020-01-01")
                        .param("endAcceptanceDate", "2030-01-01"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("missingMaterial", "hasJgwj"))
                .andExpect(jsonPath("$.success").value(true))
                // 1340 条里有 7 条已归集竣工文件 → 缺它的应是 1333
                .andExpect(jsonPath("$.result.total").value(1333));
        mockMvc.perform(get(BASE + "/list").param("keyword", "YS-2026-0001"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("ptsslb", "道路").param("gnq", "生态城"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("beginMaterialCount", "5"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    public void listRejectsIllegalMissingMaterial() throws Exception {
        // 非法资料名走 Service 的白名单校验 → 返回错误信息（HTTP 仍是 200，业务错误由 success=false 表达）
        mockMvc.perform(get(BASE + "/list").param("missingMaterial", "hasHack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    public void queryByIdReturnsDetailWithRelatedArchives() throws Exception {
        String id = firstLedgerId();
        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.id").value(id))
                .andExpect(jsonPath("$.result.materialTotal").value(13))
                .andExpect(jsonPath("$.result.relatedArchives").exists());
    }

    @Test
    public void queryByIdWithUnknownIdReturnsBusinessError() throws Exception {
        mockMvc.perform(get(BASE + "/queryById").param("id", "not-exist-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    public void statReturnsSummaryAndEightGroups() throws Exception {
        mockMvc.perform(get(BASE + "/stat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.total").value(1340))
                .andExpect(jsonPath("$.result.migratedCount").value(1340))
                .andExpect(jsonPath("$.result.orphanCount").value(53))
                .andExpect(jsonPath("$.result.byStatus.length()").value(4))
                .andExpect(jsonPath("$.result.byMaterial.length()").value(13))
                .andExpect(jsonPath("$.result.byYear.length()").value(17))
                .andExpect(jsonPath("$.result.byXzqh").isArray())
                .andExpect(jsonPath("$.result.byGnq").isArray())
                .andExpect(jsonPath("$.result.byType").isArray())
                .andExpect(jsonPath("$.result.byResult").isArray())
                .andExpect(jsonPath("$.result.byPtsslb").isArray());
    }

    @Test
    public void countByStatusAlwaysReturnsFourItems() throws Exception {
        mockMvc.perform(get(BASE + "/countByStatus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(4))
                .andExpect(jsonPath("$.result[0].status").exists())
                .andExpect(jsonPath("$.result[0].count").exists());
    }

    @Test
    public void materialsReturnsThirteenDefinitions() throws Exception {
        mockMvc.perform(get(BASE + "/materials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(13))
                .andExpect(jsonPath("$.result[0].seq").value(1))
                .andExpect(jsonPath("$.result[0].key").value("hasSgxk"))
                .andExpect(jsonPath("$.result[0].column").value("has_sgxk"))
                .andExpect(jsonPath("$.result[0].label").value("施工许可证"))
                .andExpect(jsonPath("$.result[0].source").exists())
                .andExpect(jsonPath("$.result[12].key").value("hasYjwj"));
    }

    @Test
    public void generateNoAndCheckNo() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/generateNo").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        String generated = objectMapper.readTree(result.getResponse().getContentAsString()).get("result").asText();
        assertTrue(generated.matches("YS-2026-\\d{4}"), "编号格式：" + generated);

        // 未占用的号 → 可用
        mockMvc.perform(get(BASE + "/checkNo").param("ledgerNo", generated))
                .andExpect(jsonPath("$.success").value(true));

        // 已占用的号 → 冲突
        String existing = objectMapper.readTree(
                        mockMvc.perform(get(BASE + "/list").param("pageSize", "1"))
                                .andReturn().getResponse().getContentAsString())
                .get("result").get("records").get(0).get("ledgerNo").asText();
        mockMvc.perform(get(BASE + "/checkNo").param("ledgerNo", existing))
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==================================================================
    // 二、关联档案（3 个）
    // ==================================================================

    @Test
    public void relatedArchivesAndPick() throws Exception {
        String id = firstLedgerId();
        mockMvc.perform(get(BASE + "/relatedArchives").param("id", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/archive/pick").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/archive/pick").param("keyword", "档案"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================================================================
    // 三、增删改（6 个）—— 全程在事务里，结束回滚
    // ==================================================================

    @Test
    public void addEditStatusLinkUnlinkDeleteRoundTrip() throws Exception {
        // ---------- 新增 ----------
        LedgerSaveDTO dto = new LedgerSaveDTO();
        dto.setLedgerNo(TEST_LEDGER_NO_PREFIX + "9001");
        dto.setRoadName("接口测试道路（可删）");
        dto.setXzqh("西青区");
        dto.setStatus("验收中");
        dto.setHasSgxk(1);
        dto.setHasYsbg(1);
        MvcResult added = mockMvc.perform(post(BASE + "/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        String id = objectMapper.readTree(added.getResponse().getContentAsString()).get("result").asText();
        assertNotNull(id);

        // 新增后立刻查：资料统计应为 2/13
        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(jsonPath("$.result.materialCount").value(2))
                .andExpect(jsonPath("$.result.materialMissing").value(11));

        // ---------- 编辑（PUT） ----------
        dto.setId(id);
        dto.setRoadName("接口测试道路-改名（可删）");
        dto.setHasSgxk(1);
        dto.setHasYsbg(1);
        dto.setHasJgtc(1);
        dto.setHasJgwj(1);
        mockMvc.perform(put(BASE + "/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(jsonPath("$.result.roadName").value("接口测试道路-改名（可删）"))
                .andExpect(jsonPath("$.result.materialCount").value(4));

        // ---------- 变更状态 ----------
        mockMvc.perform(post(BASE + "/status").param("id", id).param("status", "已移交"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(jsonPath("$.result.status").value("已移交"));
        // 非法状态
        mockMvc.perform(post(BASE + "/status").param("id", id).param("status", "不存在"))
                .andExpect(jsonPath("$.success").value(false));

        // ---------- 关联 / 取消关联档案 ----------
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

        // ---------- 删除 ----------
        mockMvc.perform(delete(BASE + "/delete").param("id", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    public void addWithoutRoadNameReturnsBusinessError() throws Exception {
        LedgerSaveDTO dto = new LedgerSaveDTO();
        dto.setStatus("未验收");
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
            LedgerSaveDTO dto = new LedgerSaveDTO();
            dto.setLedgerNo(TEST_LEDGER_NO_PREFIX + "910" + i);
            dto.setRoadName("接口测试批量删除-" + i + "（可删）");
            MvcResult result = mockMvc.perform(post(BASE + "/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();
            ids[i] = objectMapper.readTree(result.getResponse().getContentAsString()).get("result").asText();
        }
        mockMvc.perform(delete(BASE + "/deleteBatch").param("ids", String.join(",", ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        for (String id : ids) {
            mockMvc.perform(get(BASE + "/queryById").param("id", id))
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ==================================================================
    // 四、导出（1 个）
    // ==================================================================

    @Test
    public void exportXlsReturnsRealXlsxBytes() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/exportXls").param("status", "已移交"))
                .andExpect(status().isOk())
                .andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0, "导出内容不能为空");
        // 50 4B 03 04 = OOXML(ZIP)。若 ExportParams 漏了 ExcelType.XSSF，
        // 这里会是 D0 CF 11 E0（OLE2）—— 文件名是 xlsx、内容是 xls，Excel 打不开
        assertEquals(0x50, bytes[0] & 0xFF);
        assertEquals(0x4B, bytes[1] & 0xFF);
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains("filename*=UTF-8''"), "文件名应由 URLEncoder 编码： " + disposition);
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 取一条真实台账的 id */
    private String firstLedgerId() throws Exception {
        String body = mockMvc.perform(get(BASE + "/list").param("pageSize", "1"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("result").get("records").get(0).get("id").asText();
    }

    /** 取一个可关联的档案 id（库里没有档案时返回 null） */
    private String pickAnyArchiveId() throws Exception {
        String body = mockMvc.perform(get(BASE + "/archive/pick").param("limit", "1"))
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(body).get("result");
        if (arr == null || arr.size() == 0) {
            return null;
        }
        return arr.get(0).get("id").asText();
    }
}
