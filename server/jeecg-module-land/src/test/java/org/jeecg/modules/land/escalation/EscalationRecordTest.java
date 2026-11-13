package org.jeecg.modules.land.escalation;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.escalation.dto.EscalationRecordDTO;
import org.jeecg.modules.land.escalation.dto.EscalationSaveDTO;
import org.jeecg.modules.land.escalation.entity.EscalationProject;
import org.jeecg.modules.land.escalation.entity.EscalationRecord;
import org.jeecg.modules.land.escalation.service.IEscalationProjectService;
import org.jeecg.modules.land.escalation.service.IEscalationRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.AfterTransaction;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 提级论证审核意见登记：真实数据库字段、主表回写与事务边界回归。 */
public class EscalationRecordTest extends LandIntegrationTestBase {

    private static final String TEST_PREFIX = "TJ-TEST-ESC-";

    @Autowired
    private IEscalationProjectService projectService;

    @Autowired
    private IEscalationRecordService recordService;

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
        dto.setProjectType("基础设施类");
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
