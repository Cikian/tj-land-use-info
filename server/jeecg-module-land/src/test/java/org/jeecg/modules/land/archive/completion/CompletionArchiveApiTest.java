package org.jeecg.modules.land.archive.completion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.common.exception.JeecgBootExceptionHandler;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.archive.completion.controller.CompletionArchiveController;
import org.jeecg.modules.land.archive.completion.dto.CompletionSaveDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

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
 * @Description: 竣工验收项目历史工程资料数字化档案 - 接口级（HTTP）集成测试
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>用 {@link MockMvc} 把 {@link CompletionArchiveController} 的 <b>16 个接口全打一遍</b>：
 * URL 路由、参数绑定（GET query string / POST body / PUT）、JSON 序列化、
 * 服务层与真实 SQL、以及导出接口写出的字节流。
 *
 * <p><b>★ 与台账模块测试最大的差别：本模块库里是空表</b>（没有数据迁移），
 * 所以凡是要读数据的用例，都先用 {@code /add} 接口自己造一条带
 * {@code JG-TEST-} 前缀的记录，再断言 —— 不依赖任何存量行。
 *
 * <p><b>★ 本测试不覆盖什么，要说清楚</b>：
 * <ul>
 *   <li><b>鉴权</b>：{@code @RequiresPermissions} 由 Shiro 的 AOP 织入，standaloneSetup 不装 Shiro，
 *       所以这里不做「无权限应 401」的断言。权限的正确性由数据库侧的守卫查询保证
 *       （sql/completion/04_completion_permission_buttons.sql 的 3 段自检：5 个按钮权限、
 *       全部授权 admin、菜单行不残留权限码、有按钮的菜单 is_leaf=0）。</li>
 *   <li><b>真实 Servlet 容器</b>：用的是 MockMvc 而不是起 Tomcat，因此过滤器链
 *       （JWT、跨域等）不在覆盖范围内。</li>
 * </ul>
 *
 * <p>整个测试类跑在一个事务里、结束回滚；另有按测试前缀的物理清理兜底。
 */
public class CompletionArchiveApiTest extends LandIntegrationTestBase {

    private static final String BASE = "/land/archive/completion";

    /** 测试行前缀（业务上不会产生这个前缀） */
    private static final String TEST_ARCHIVE_NO_PREFIX = "JG-TEST-";

    @Autowired
    private CompletionArchiveController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 本次测试通过接口创建过的 id（事务外兜底清理用） */
    private final List<String> createdIds = new ArrayList<>();

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

    /** 事务结束（已回滚）后物理清理测试行；基类只清理台账表，本模块要自己清 */
    @AfterTransaction
    public void cleanCompletionTestRows() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM `t_completion_archive` WHERE `archive_no` LIKE ?",
                TEST_ARCHIVE_NO_PREFIX + "%");
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM `t_completion_archive` WHERE `id` = ?", id);
        }
    }

    // ==================================================================
    // 一、查询类（6 个）
    // ==================================================================

    @Test
    public void listReturnsPagedRowsWithSeqAndProgress() throws Exception {
        addArchive(TEST_ARCHIVE_NO_PREFIX + "9001", "接口测试历史项目（可删）");

        MvcResult result = mockMvc.perform(get(BASE + "/list")
                        .param("archiveNo", TEST_ARCHIVE_NO_PREFIX)
                        .param("pageNo", "1")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.total").value(1))
                .andExpect(jsonPath("$.result.records.length()").value(1))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"seq\":1"), "列表必须带序号 seq");
        assertTrue(body.contains("\"digitizePercent\":"), "列表必须带数字化进度 digitizePercent");
        assertTrue(body.contains("\"archiveNo\":\"" + TEST_ARCHIVE_NO_PREFIX), "列表必须带档案编号");
    }

    @Test
    public void listSupportsAllDocumentedConditions() throws Exception {
        // 逐个把查询条件打到接口上，确保都能绑定（不能出现 400/500）
        addArchive(TEST_ARCHIVE_NO_PREFIX + "9002", "条件绑定测试项目");

        mockMvc.perform(get(BASE + "/list").param("projectName", "条件")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("projectCode", "HIST")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("xzqh", "西青区")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("projectType", "道路工程")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("buildUnit", "建设")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("constructUnit", "施工")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("designUnit", "设计")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("superviseUnit", "监理")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("digitizeStatus", "未数字化")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("digitizeOrg", "加工")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("retention", "永久")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("scanDpi", "300")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("crzdbh", "津西")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("keyword", "条件绑定")).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("beginPageCount", "10").param("endPageCount", "99999"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list").param("beginInvestAmount", "1").param("endInvestAmount", "999999"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list")
                        .param("beginStartDate", "2000-01-01").param("endStartDate", "2030-01-01"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list")
                        .param("beginCompleteDate", "2000-01-01").param("endCompleteDate", "2030-01-01"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list")
                        .param("beginAcceptanceDate", "2000-01-01").param("endAcceptanceDate", "2030-01-01"))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/list")
                        .param("beginDigitizeDate", "2000-01-01").param("endDigitizeDate", "2030-01-01"))
                .andExpect(jsonPath("$.success").value(true));
        // ★ Boolean 条件：true / false 都必须能绑定（且 false 不被当成「空值」丢掉）
        mockMvc.perform(get(BASE + "/list")
                        .param("archiveNo", TEST_ARCHIVE_NO_PREFIX).param("hasArchive", "false"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.total").value(1));
        mockMvc.perform(get(BASE + "/list")
                        .param("archiveNo", TEST_ARCHIVE_NO_PREFIX).param("hasArchive", "true"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.total").value(0));
    }

    @Test
    public void listTreatsUnknownStatusAsPlainFilterInsteadOfError() throws Exception {
        // 查询条件不做取值白名单：非法状态只当普通过滤值（0 条），不会 500。
        // 这是刻意的：状态是「筛选维度」，不是「写入值」；写入值的白名单在 /add、/edit、/status 上。
        mockMvc.perform(get(BASE + "/list").param("digitizeStatus", "不存在的状态"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.total").value(0));
    }

    @Test
    public void queryByIdReturnsDetailWithRelatedArchives() throws Exception {
        String id = addArchive(TEST_ARCHIVE_NO_PREFIX + "9003", "详情测试历史项目（可删）");

        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.id").value(id))
                .andExpect(jsonPath("$.result.digitizePercent").value(0))
                .andExpect(jsonPath("$.result.relatedArchives").exists());
    }

    @Test
    public void queryByIdWithUnknownIdReturnsBusinessError() throws Exception {
        mockMvc.perform(get(BASE + "/queryById").param("id", "not-exist-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    public void statReturnsSummaryAndSixGroups() throws Exception {
        addArchive(TEST_ARCHIVE_NO_PREFIX + "9004", "统计测试历史项目（可删）");

        mockMvc.perform(get(BASE + "/stat").param("archiveNo", TEST_ARCHIVE_NO_PREFIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.total").value(1))
                .andExpect(jsonPath("$.result.archivedCount").value(0))
                .andExpect(jsonPath("$.result.digitizedCount").value(0))
                .andExpect(jsonPath("$.result.byStatus.length()").value(3))
                .andExpect(jsonPath("$.result.byXzqh").isArray())
                .andExpect(jsonPath("$.result.byType").isArray())
                .andExpect(jsonPath("$.result.byRetention").isArray())
                .andExpect(jsonPath("$.result.byYear").isArray())
                .andExpect(jsonPath("$.result.byCompleteYear").isArray());
    }

    @Test
    public void countByStatusAlwaysReturnsThreeItems() throws Exception {
        mockMvc.perform(get(BASE + "/countByStatus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.length()").value(3))
                .andExpect(jsonPath("$.result[0].status").exists())
                .andExpect(jsonPath("$.result[0].count").exists());
    }

    @Test
    public void generateNoAndCheckNo() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/generateNo").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        String generated = objectMapper.readTree(result.getResponse().getContentAsString()).get("result").asText();
        assertTrue(generated.matches("JG-2026-\\d{4}"), "编号格式：" + generated);

        // 未占用的号 → 可用
        mockMvc.perform(get(BASE + "/checkNo").param("archiveNo", generated))
                .andExpect(jsonPath("$.success").value(true));

        // 占用它之后 → 冲突
        addArchive(generated, "编号占用测试项目（可删）");
        mockMvc.perform(get(BASE + "/checkNo").param("archiveNo", generated))
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==================================================================
    // 二、关联扫描件（3 个）
    // ==================================================================

    @Test
    public void relatedArchivesAndPick() throws Exception {
        String id = addArchive(TEST_ARCHIVE_NO_PREFIX + "9005", "关联扫描件测试（可删）");
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
    // 三、增删改（5 个）—— 全程在事务里，结束回滚
    // ==================================================================

    @Test
    public void addEditStatusLinkUnlinkDeleteRoundTrip() throws Exception {
        // ---------- 新增 ----------
        String id = addArchive(TEST_ARCHIVE_NO_PREFIX + "9101", "接口往返测试（可删）");

        // ---------- 编辑（PUT）：改数字化状态 + 清空一个字段 ----------
        CompletionSaveDTO edit = new CompletionSaveDTO();
        edit.setId(id);
        edit.setArchiveNo(TEST_ARCHIVE_NO_PREFIX + "9101");
        edit.setProjectName("接口往返测试-改名（可删）");
        edit.setDigitizeStatus("已数字化");
        edit.setDigitizeOrg("接口测试加工单位");
        edit.setPageCount(888);
        edit.setScanDpi(400);
        mockMvc.perform(put(BASE + "/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(jsonPath("$.result.projectName").value("接口往返测试-改名（可删）"))
                .andExpect(jsonPath("$.result.digitizeStatus").value("已数字化"))
                .andExpect(jsonPath("$.result.digitizePercent").value(100))
                .andExpect(jsonPath("$.result.pageCount").value(888))
                // ★ 这次编辑没有传 xzqh / completeDate → 显式 SQL 应把它们清成 NULL
                .andExpect(jsonPath("$.result.xzqh").doesNotExist())
                .andExpect(jsonPath("$.result.completeDate").doesNotExist());

        // ---------- 变更数字化状态 ----------
        mockMvc.perform(post(BASE + "/status").param("id", id).param("status", "数字化中"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(jsonPath("$.result.digitizeStatus").value("数字化中"))
                .andExpect(jsonPath("$.result.digitizePercent").value(50));
        // 非法状态 → 业务错误（HTTP 仍 200，由 success=false 表达）
        mockMvc.perform(post(BASE + "/status").param("id", id).param("status", "不存在"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        // ---------- 关联 / 取消关联扫描件 ----------
        String archiveId = pickAnyArchiveId();
        if (archiveId != null) {
            mockMvc.perform(post(BASE + "/linkArchive").param("id", id).param("archiveId", archiveId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            mockMvc.perform(get(BASE + "/queryById").param("id", id))
                    .andExpect(jsonPath("$.result.archiveId").value(archiveId))
                    .andExpect(jsonPath("$.result.archiveCount").isNumber());
            mockMvc.perform(post(BASE + "/unlinkArchive").param("id", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            mockMvc.perform(get(BASE + "/queryById").param("id", id))
                    .andExpect(jsonPath("$.result.archiveId").doesNotExist());
        } else {
            System.out.println("[跳过] 库里没有档案数据，无法验证关联扫描件接口");
        }

        // ---------- 删除（逻辑删除） ----------
        mockMvc.perform(delete(BASE + "/delete").param("id", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get(BASE + "/queryById").param("id", id))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    public void addWithoutProjectNameReturnsBusinessError() throws Exception {
        CompletionSaveDTO dto = new CompletionSaveDTO();
        dto.setDigitizeStatus("未数字化");
        mockMvc.perform(post(BASE + "/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    public void addWithIllegalStatusReturnsBusinessError() throws Exception {
        CompletionSaveDTO dto = new CompletionSaveDTO();
        dto.setProjectName("非法状态测试（可删）");
        dto.setDigitizeStatus("已扫描完");
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
            ids[i] = addArchive(TEST_ARCHIVE_NO_PREFIX + "920" + i, "接口批量删除测试-" + i + "（可删）");
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
    // 四、导出（1 个）—— 需求原文「可快速导出档案信息」
    // ==================================================================

    @Test
    public void exportXlsReturnsRealXlsxBytes() throws Exception {
        addArchive(TEST_ARCHIVE_NO_PREFIX + "9301", "导出接口测试（可删）");

        MvcResult result = mockMvc.perform(get(BASE + "/exportXls").param("archiveNo", TEST_ARCHIVE_NO_PREFIX))
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
        assertTrue(disposition.contains("filename*=UTF-8''"), "文件名应由 URLEncoder 编码：" + disposition);
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 通过 /add 接口造一条档案，返回 id（同时登记用于事务外兜底清理） */
    private String addArchive(String archiveNo, String projectName) throws Exception {
        CompletionSaveDTO dto = new CompletionSaveDTO();
        dto.setArchiveNo(archiveNo);
        dto.setProjectName(projectName);
        dto.setXzqh("西青区");
        dto.setProjectType("道路工程");
        dto.setPageCount(100);
        dto.setFileCount(10);
        dto.setScanDpi(300);
        MvcResult added = mockMvc.perform(post(BASE + "/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        String id = objectMapper.readTree(added.getResponse().getContentAsString()).get("result").asText();
        assertNotNull(id);
        createdIds.add(id);
        return id;
    }

    /** 取一个可关联的档案 id（库里没有档案时返回 null） */
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
