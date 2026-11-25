package org.jeecg.modules.land.escalation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.exception.JeecgBootExceptionHandler;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.escalation.controller.EscalationRecordController;
import org.jeecg.modules.land.escalation.dto.EscalationRecordDTO;
import org.jeecg.modules.land.escalation.dto.EscalationSaveDTO;
import org.jeecg.modules.land.escalation.entity.EscalationProject;
import org.jeecg.modules.land.escalation.entity.EscalationRecord;
import org.jeecg.modules.land.escalation.service.IEscalationProjectService;
import org.jeecg.modules.land.escalation.service.IEscalationRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 提级论证审核意见登记：真实数据库字段、主表回写与事务边界回归。 */
public class EscalationRecordTest extends LandIntegrationTestBase {

    private static final String TEST_PREFIX = "TJ-TEST-ESC-";

    @Autowired
    private IEscalationProjectService projectService;

    @Autowired
    private IEscalationRecordService recordService;

    @Autowired
    private EscalationRecordController recordController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    public void setUpMockMvc() {
        // 只验证 Controller 的 JSON/Result 契约；权限由只读库中的 admin 授权单独核查。
        mockMvc = MockMvcBuilders.standaloneSetup(recordController)
                .setControllerAdvice(new JeecgBootExceptionHandler())
                .build();
    }

    @Test
    public void addRecordOnlyKeepsStatusAndUpdatesSummaryAndCount() {
        String projectId = createProject("仅登记");
        EscalationProject before = projectService.queryDetail(projectId);
        String originalStatus = before.getStatus();

        EscalationRecordDTO dto = opinion(projectId, "仅登记审核意见，不同步办理状态");
        dto.setAttachmentIds("escalation/opinion/2026/11/测试意见.pdf,escalation/opinion/2026/11/测试附件.docx");
        EscalationRecord saved = recordService.addRecord(dto);
        assertNotNull(saved.getId());
        assertEquals(dto.getAttachmentIds(), saved.getAttachmentIds(), "附件必须沿用既有 store_path 口径");

        EscalationProject after = projectService.queryDetail(projectId);
        assertEquals(originalStatus, after.getStatus(), "未传 status 时项目状态不得变化");
        assertEquals(Integer.valueOf(1), after.getRecordCount());
        assertEquals(dto.getOpinion(), after.getLatestOpinion());
        assertNotNull(after.getLatestOpinionTime());
        List<EscalationRecord> records = recordService.queryByProjectId(projectId);
        assertEquals(1, records.size());
        assertEquals(dto.getOpinion(), records.get(0).getOpinion());
    }

    @Test
    public void addRecordCanSynchronizeStatusInSameTransaction() {
        String projectId = createProject("同步状态");
        EscalationRecordDTO dto = opinion(projectId, "登记审核意见并同步为已办结");
        dto.setStatus("已办结");
        recordService.addRecord(dto);

        EscalationProject after = projectService.queryDetail(projectId);
        assertEquals("已办结", after.getStatus());
        assertEquals(Integer.valueOf(1), after.getRecordCount());
        assertEquals(dto.getOpinion(), after.getLatestOpinion());
    }

    @Test
    public void oversizedAttachmentPathsFailBeforeInsertWithActionableReason() {
        String projectId = createProject("附件长度");
        EscalationRecordDTO dto = opinion(projectId, "附件长度校验");
        StringBuilder paths = new StringBuilder();
        while (paths.length() <= 1000) {
            paths.append("escalation/opinion/2026/11/超长附件路径.pdf,");
        }
        dto.setAttachmentIds(paths.toString());

        JeecgBootException error = assertThrows(JeecgBootException.class, () -> recordService.addRecord(dto));
        assertTrue(error.getMessage().contains("附件存储路径"), error.getMessage());
        assertTrue(recordService.queryByProjectId(projectId).isEmpty(), "校验失败不得留下半条意见记录");
        assertEquals(Integer.valueOf(0), projectService.queryDetail(projectId).getRecordCount());
    }

    @Test
    public void controllerAcceptsJsonWithAttachmentAndUsesAdminAuditContext() throws Exception {
        String projectId = createProject("JSON入口");
        EscalationRecordDTO dto = opinion(projectId, "开发环境 Controller JSON 登记复现");
        dto.setAttachmentIds("escalation/opinion/2026/11/controller-test.pdf");

        Subject subject = mock(Subject.class);
        LoginUser admin = new LoginUser();
        admin.setUsername("admin");
        admin.setRealname("管理员");
        admin.setOrgCode("A01");
        when(subject.getPrincipal()).thenReturn(admin);
        ThreadContext.bind(subject);
        try {
            MvcResult result = mockMvc.perform(post("/land/escalation/record/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(dto)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = responseBody(result);
            assertTrue(body.path("success").asBoolean(), body.path("message").asText());
            assertEquals("登记成功！", body.path("message").asText());
        } finally {
            ThreadContext.unbindSubject();
        }

        List<EscalationRecord> records = recordService.queryByProjectId(projectId);
        assertEquals(1, records.size());
        assertEquals("admin", records.get(0).getRecorderId());
        assertEquals("管理员", records.get(0).getRecorderName());
        assertEquals(dto.getAttachmentIds(), records.get(0).getAttachmentIds());
        assertEquals(dto.getOpinion(), projectService.queryDetail(projectId).getLatestOpinion());
    }

    @Test
    public void multipleRegistrationsAreAppendOnlyAndLatestSummaryFollowsLastRecord() {
        String projectId = createProject("多次登记");
        String originalStatus = projectService.queryDetail(projectId).getStatus();
        recordService.addRecord(opinion(projectId, "第一次登记意见"));
        recordService.addRecord(opinion(projectId, "第二次登记意见"));
        EscalationRecordDTO third = opinion(projectId, "第三次登记意见");
        third.setAttachmentIds("escalation/opinion/2026/11/repeat-test.png");
        recordService.addRecord(third);

        List<EscalationRecord> records = recordService.queryByProjectId(projectId);
        assertEquals(3, records.size(), "多次登记必须全部保留，不能覆盖历史意见");
        assertTrue(records.stream().anyMatch(item -> "第一次登记意见".equals(item.getOpinion())));
        assertTrue(records.stream().anyMatch(item -> "第二次登记意见".equals(item.getOpinion())));
        assertTrue(records.stream().anyMatch(item -> "第三次登记意见".equals(item.getOpinion())));
        EscalationProject project = projectService.queryDetail(projectId);
        assertEquals(Integer.valueOf(3), project.getRecordCount());
        assertEquals(third.getOpinion(), project.getLatestOpinion());
        assertEquals(originalStatus, project.getStatus());
    }

    @Test
    public void opinionBoundaryPersists2000AndRejects2001WithoutPartialWrite() {
        String projectId = createProject("正文边界");
        String atColumnLimit = String.join("", Collections.nCopies(2000, "审"));
        recordService.addRecord(opinion(projectId, atColumnLimit));

        EscalationProject afterLimit = projectService.queryDetail(projectId);
        assertEquals(2000, recordService.queryByProjectId(projectId).get(0).getOpinion().length());
        assertEquals(1000, afterLimit.getLatestOpinion().length(), "主表摘要必须按自身列宽截断");
        assertEquals(atColumnLimit.substring(0, 1000), afterLimit.getLatestOpinion());
        assertEquals(Integer.valueOf(1), afterLimit.getRecordCount());

        String overColumnLimit = atColumnLimit + "字";
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> recordService.addRecord(opinion(projectId, overColumnLimit)));
        assertTrue(error.getMessage().contains("2000"), error.getMessage());
        assertEquals(1, recordService.queryByProjectId(projectId).size(), "超长拒绝后不得追加半条记录");
        EscalationProject afterReject = projectService.queryDetail(projectId);
        assertEquals(Integer.valueOf(1), afterReject.getRecordCount());
        assertEquals(afterLimit.getLatestOpinion(), afterReject.getLatestOpinion());
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private EscalationRecordDTO opinion(String projectId, String text) {
        EscalationRecordDTO dto = new EscalationRecordDTO();
        dto.setProjectId(projectId);
        dto.setRecordType("审核意见");
        dto.setAction("同意");
        dto.setOpinion(text);
        return dto;
    }

    private String createProject(String tag) {
        EscalationSaveDTO dto = new EscalationSaveDTO();
        dto.setProjectNo(TEST_PREFIX + tag + "-" + System.nanoTime());
        dto.setProjectName(tag + "测试项目");
        dto.setDeclareDept("测试申报单位");
        // 项目类型口径已改为「市级项目 / 区级项目」（清单 P2-10），测试数据同步新值
        dto.setProjectType("市级项目");
        // 1118 确认口径：行政区划、项目类型、办理状态必填（论证结论仅办结时必填）
        dto.setXzqh("东丽区");
        dto.setTotalInvestment(new BigDecimal("1.25"));
        dto.setBuildLocation("天津市测试地址");
        dto.setDeclareDate(new Date());
        dto.setProjectSummary("审核意见登记集成测试");
        return projectService.createProject(dto);
    }

    @AfterTransaction
    public void cleanEscalationRows() {
        safeCleanup(dataSource, "提级论证审核意见",
                new Object[]{"DELETE FROM `t_escalation_record` WHERE `project_id` IN "
                        + "(SELECT `id` FROM `t_escalation_project` WHERE `project_no` LIKE ?)", TEST_PREFIX + "%"},
                new Object[]{"DELETE FROM `t_escalation_material` WHERE `project_id` IN "
                        + "(SELECT `id` FROM `t_escalation_project` WHERE `project_no` LIKE ?)", TEST_PREFIX + "%"},
                new Object[]{"DELETE FROM `t_escalation_project` WHERE `project_no` LIKE ?", TEST_PREFIX + "%"});
    }
}
