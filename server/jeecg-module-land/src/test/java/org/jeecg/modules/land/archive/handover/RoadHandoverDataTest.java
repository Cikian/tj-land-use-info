package org.jeecg.modules.land.archive.handover;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.archive.handover.dto.HandoverQueryDTO;
import org.jeecg.modules.land.archive.handover.dto.HandoverSaveDTO;
import org.jeecg.modules.land.archive.handover.entity.RoadHandover;
import org.jeecg.modules.land.archive.handover.enums.HandoverStatus;
import org.jeecg.modules.land.archive.handover.mapper.RoadHandoverMapper;
import org.jeecg.modules.land.archive.handover.service.IHandoverExportService;
import org.jeecg.modules.land.archive.handover.service.IRoadHandoverService;
import org.jeecg.modules.land.archive.handover.vo.HandoverStatVO;
import org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Description: 道路交付及养护协议移交事项 - 数据层集成测试（真实 MySQL + 真实 Mapper XML）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>与台账模块同一套做法：打真库、跑在一个事务里、结束回滚，
 * 并由基类的 {@code @AfterTransaction} 兜底物理清理 {@code YJ-TEST-%} 测试行。
 *
 * <p>断言同样分两类：**不变量断言**（怎么补录数据都成立）与
 * {@link #migrationBaselineSnapshot()}（迁移完成时的真实数字，业务补录后需同步更新期望值）。
 */
public class RoadHandoverDataTest extends LandIntegrationTestBase {

    @Autowired
    private IRoadHandoverService handoverService;

    @Autowired
    private RoadHandoverMapper handoverMapper;

    @Autowired
    private IHandoverExportService exportService;

    // ==================================================================
    // 一、查询与不变量
    // ==================================================================

    @Test
    public void listPagingKeepsSeqContinuous() {
        HandoverQueryDTO first = new HandoverQueryDTO();
        first.setPageNo(1);
        first.setPageSize(10);
        IPage<RoadHandover> page1 = handoverService.queryPage(first);
        assertTrue(page1.getTotal() > 0, "移交事项总数为 0，说明迁移或库连接有问题");
        assertEquals(10, page1.getRecords().size());
        assertEquals(Integer.valueOf(1), page1.getRecords().get(0).getSeq());
        assertEquals(Integer.valueOf(10), page1.getRecords().get(9).getSeq());

        HandoverQueryDTO second = new HandoverQueryDTO();
        second.setPageNo(2);
        second.setPageSize(10);
        IPage<RoadHandover> page2 = handoverService.queryPage(second);
        assertEquals(Integer.valueOf(11), page2.getRecords().get(0).getSeq());

        List<String> ids1 = page1.getRecords().stream().map(RoadHandover::getId).collect(Collectors.toList());
        List<String> ids2 = page2.getRecords().stream().map(RoadHandover::getId).collect(Collectors.toList());
        assertTrue(java.util.Collections.disjoint(ids1, ids2));
    }

    @Test
    public void statusCountsAlwaysSumToTotal() {
        long total = handoverService.queryPage(new HandoverQueryDTO()).getTotal();
        List<HandoverStatVO.StatusCount> counts = handoverService.countByStatus(new HandoverQueryDTO());
        assertEquals(3, counts.size(), "固定返回 3 个状态");
        assertEquals(total, counts.stream().mapToLong(HandoverStatVO.StatusCount::getCount).sum());
        for (HandoverStatVO.StatusCount item : counts) {
            assertTrue(HandoverStatus.allValues().contains(item.getStatus()),
                    "状态取值必须是 3 个固定值之一：" + item.getStatus());
        }
        // 按某个状态筛选的条数要与它的计数一致
        HandoverStatVO.StatusCount biggest = counts.stream()
                .max((a, b) -> Long.compare(a.getCount(), b.getCount())).orElse(null);
        assertNotNull(biggest);
        HandoverQueryDTO filtered = new HandoverQueryDTO();
        filtered.setStatus(biggest.getStatus());
        assertEquals(biggest.getCount(), handoverService.queryPage(filtered).getTotal());
    }

    @Test
    public void statusDistributionIgnoresStatusCondition() {
        HandoverQueryDTO query = new HandoverQueryDTO();
        query.setStatus("已移交");
        long sum = handoverService.countByStatus(query).stream()
                .mapToLong(HandoverStatVO.StatusCount::getCount).sum();
        assertEquals(handoverService.queryPage(new HandoverQueryDTO()).getTotal(), sum,
                "状态计数不能受 status 条件影响（否则切换页签后角标会全变 0）");
    }

    @Test
    public void statGroupsAreConsistent() {
        HandoverQueryDTO query = new HandoverQueryDTO();
        HandoverStatVO stat = handoverService.queryStat(query);
        long total = stat.getTotal();
        assertTrue(total > 0);
        assertEquals(total, stat.getByStatus().stream().mapToLong(HandoverStatVO.NameCount::getCount).sum());
        assertEquals(total, stat.getByXzqh().stream().mapToLong(HandoverStatVO.NameCount::getCount).sum());
        assertEquals(total, stat.getMigratedCount() + stat.getManualCount());
        assertTrue(stat.getArchivedCount() <= total);
        assertTrue(stat.getMissingAgreementCount() <= total);
        assertTrue(stat.getByType().size() >= 1);
        assertTrue(stat.getByReceiveUnit().size() >= 1);
        assertTrue(stat.getByDldj().size() >= 1);
        assertTrue(stat.getByYear().size() >= 1);
    }

    @Test
    public void allDocumentedConditionsAreAccepted() {
        // 清单 §6.3.6 明列的 8 个条件逐个打一遍，确保 DTO/XML 都能绑定
        HandoverQueryDTO query = new HandoverQueryDTO();
        query.setRoadName("路");
        assertNotNull(handoverService.queryPage(query));
        query = new HandoverQueryDTO();
        query.setXzqh("北辰区");
        assertNotNull(handoverService.queryPage(query));
        query = new HandoverQueryDTO();
        query.setCrzdbh("津");
        assertNotNull(handoverService.queryPage(query));
        query = new HandoverQueryDTO();
        query.setHandoverType("正式移交");
        assertTrue(handoverService.queryPage(query).getTotal() > 0);
        query = new HandoverQueryDTO();
        query.setStatus("已移交");
        assertTrue(handoverService.queryPage(query).getTotal() > 0);
        query = new HandoverQueryDTO();
        query.setBeginAgreementDate("2000-01-01");
        query.setEndAgreementDate("2030-01-01");
        assertNotNull(handoverService.queryPage(query));
        query = new HandoverQueryDTO();
        query.setBeginHandoverDate("2000-01-01");
        query.setEndHandoverDate("2030-01-01");
        assertNotNull(handoverService.queryPage(query));
        query = new HandoverQueryDTO();
        query.setKeyword("YJ-");
        assertTrue(handoverService.queryPage(query).getTotal() > 0);
    }

    @Test
    public void hasArchiveFilterWorks() {
        HandoverQueryDTO withArchive = new HandoverQueryDTO();
        withArchive.setHasArchive(true);
        HandoverQueryDTO without = new HandoverQueryDTO();
        without.setHasArchive(false);
        long a = handoverService.queryPage(withArchive).getTotal();
        long b = handoverService.queryPage(without).getTotal();
        long total = handoverService.queryPage(new HandoverQueryDTO()).getTotal();
        assertEquals(total, a + b, "「已关联档案」+「未关联档案」必须等于总数");
    }

    @Test
    public void allMapperStatementsExecute() {
        HandoverQueryDTO query = new HandoverQueryDTO();
        assertNotNull(handoverMapper.selectHandoverPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 5), query));
        assertNotNull(handoverMapper.selectForExport(query));
        assertNotNull(handoverMapper.selectSummary(query));
        assertNotNull(handoverMapper.selectCountGroupByStatus(query));
        assertNotNull(handoverMapper.selectStatByColumn(query, "handover_type", "未登记"));
        assertNotNull(handoverMapper.selectStatByColumn(query, "xzqh", "未填写"));
        assertNotNull(handoverMapper.selectStatByColumn(query, "gnq", "非功能区"));
        assertNotNull(handoverMapper.selectStatByColumn(query, "dldj", "未填写"));
        assertNotNull(handoverMapper.selectStatByYear(query));
        assertNotNull(handoverMapper.selectStatByReceiveUnit(query, 10));
        assertEquals(0, handoverMapper.countByHandoverNo("YJ-NOT-EXIST-9999", null));
        assertNotNull(handoverMapper.selectMaxSeqOfYear("YJ-2026-"));
        assertNotNull(handoverMapper.selectRelatedArchives(null, null, 5));
        assertEquals(0, handoverMapper.countRelatedArchives(null, null));
        assertNotNull(handoverMapper.selectArchivesForPick(null, null, null, 5));
        assertEquals(0, handoverMapper.countArchiveById("not-exist"));
    }

    // ==================================================================
    // 二、编号
    // ==================================================================

    @Test
    public void generateHandoverNoIsStableAndUnique() {
        String generated = handoverService.generateHandoverNo(2026);
        assertTrue(generated.matches("YJ-2026-\\d{4}"), "编号格式必须是 YJ-{yyyy}-{4位}：" + generated);
        assertEquals(generated, handoverService.generateHandoverNo(2026), "只预览不落库，连取两次应一致");
        handoverService.checkHandoverNoUnique(generated, null);

        String existing = handoverService.queryPage(new HandoverQueryDTO()).getRecords().get(0).getHandoverNo();
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> handoverService.checkHandoverNoUnique(existing, null));
        assertTrue(error.getMessage().contains("已存在"));
    }

    // ==================================================================
    // 三、增删改（事务回滚 + 测试行物理清理兜底）
    // ==================================================================

    @Test
    public void createUpdateChangeStatusDelete() {
        HandoverSaveDTO dto = new HandoverSaveDTO();
        dto.setHandoverNo(TEST_HANDOVER_NO_PREFIX + "0001");
        dto.setRoadName("集成测试道路（可删）");
        dto.setXzqh("北辰区");
        dto.setDldj("城市主干道");
        dto.setHandoverType("养护协议");
        dto.setAgreementNo("YH-2026-TEST-001");
        dto.setAgreementDate(new Date());
        dto.setMaintenanceStart(new Date());
        dto.setReceiveUnit("区城管委");
        dto.setStatus("移交中");

        String id = handoverService.createHandover(dto);
        assertNotNull(id);
        RoadHandover created = handoverService.queryDetail(id);
        assertEquals(TEST_HANDOVER_NO_PREFIX + "0001", created.getHandoverNo());
        assertEquals("养护协议", created.getHandoverType());
        assertNull(created.getSourceFacilityId(), "人工新增的记录，迁移幂等键必须为空");
        assertEquals(created.getRoadName(), created.getPtxmmc());

        // 编辑：故意不传 agreementNo / agreementDate / maintenanceStart → 必须被清成 NULL
        HandoverSaveDTO edit = new HandoverSaveDTO();
        edit.setId(id);
        edit.setHandoverNo(created.getHandoverNo());
        edit.setRoadName("集成测试道路（已改名）");
        edit.setStatus("移交中");
        edit.setReceiveUnit("区城管委");
        handoverService.updateHandover(edit);

        RoadHandover updated = handoverService.queryDetail(id);
        assertEquals("集成测试道路（已改名）", updated.getRoadName());
        assertNull(updated.getAgreementNo(), "★ 显式 SQL 必须能把协议编号清空（updateById 做不到）");
        assertNull(updated.getAgreementDate());
        assertNull(updated.getMaintenanceStart());

        // 状态变更
        handoverService.changeStatus(id, "已移交");
        assertEquals("已移交", handoverService.queryDetail(id).getStatus());
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> handoverService.changeStatus(id, "已完工"));
        assertTrue(error.getMessage().contains("不合法"));

        // 逻辑删除
        handoverService.deleteHandover(id);
        assertNull(handoverService.queryDetail(id));
        Integer delFlag = new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForObject(
                "SELECT del_flag FROM t_road_handover WHERE id = ?", Integer.class, id);
        assertEquals(Integer.valueOf(1), delFlag);
    }

    @Test
    public void createRequiresRoadNameAndValidStatus() {
        HandoverSaveDTO noName = new HandoverSaveDTO();
        noName.setStatus("待移交");
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> handoverService.createHandover(noName));
        assertTrue(error.getMessage().contains("道路名称不能为空"));

        HandoverSaveDTO badStatus = new HandoverSaveDTO();
        badStatus.setRoadName("集成测试道路（可删）");
        badStatus.setStatus("已完工");
        error = assertThrows(JeecgBootException.class, () -> handoverService.createHandover(badStatus));
        assertTrue(error.getMessage().contains("不合法"));
    }

    @Test
    public void createWithoutNoGeneratesOneAndDefaultsStatus() {
        HandoverSaveDTO dto = new HandoverSaveDTO();
        dto.setRoadName("集成测试道路-自动编号（可删）");
        String id = handoverService.createHandover(dto);
        RoadHandover created = handoverService.queryDetail(id);
        assertTrue(created.getHandoverNo().matches("YJ-\\d{4}-\\d{4}"),
                "自动编号格式：" + created.getHandoverNo());
        assertEquals(HandoverStatus.defaultValue(), created.getStatus());
    }

    // ==================================================================
    // 四、关联档案
    // ==================================================================

    @Test
    public void linkAndUnlinkArchive() {
        List<RelatedArchiveVO> candidates = handoverService.queryArchivesForPick(null, null, null, 5);
        if (candidates == null || candidates.isEmpty()) {
            System.out.println("[跳过] 库里没有档案数据，无法验证关联档案");
            return;
        }
        RelatedArchiveVO archive = candidates.get(0);
        HandoverSaveDTO dto = new HandoverSaveDTO();
        dto.setHandoverNo(TEST_HANDOVER_NO_PREFIX + "0002");
        dto.setRoadName("集成测试道路-关联档案（可删）");
        dto.setFacilityId(archive.getFacilityId());
        dto.setCrzdbh(archive.getCrzdbh());
        String id = handoverService.createHandover(dto);

        handoverService.linkArchive(id, archive.getId());
        RoadHandover linked = handoverService.queryDetail(id);
        assertEquals(archive.getId(), linked.getArchiveId());
        assertNotNull(linked.getArchiveCount());
        assertTrue(linked.getArchiveCount() >= 1);
        assertTrue(linked.getRelatedArchives().stream()
                .map(RelatedArchiveVO::getId).collect(Collectors.toList()).contains(archive.getId()));

        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> handoverService.linkArchive(id, "not-exist-archive-id"));
        assertTrue(error.getMessage().contains("档案不存在"));

        handoverService.unlinkArchive(id);
        assertNull(handoverService.queryDetail(id).getArchiveId());
    }

    // ==================================================================
    // 五、导出
    // ==================================================================

    @Test
    public void exportProducesRealXlsxWithAllColumns() throws Exception {
        HandoverQueryDTO query = new HandoverQueryDTO();
        query.setStatus("已移交");
        long expected = handoverService.queryPage(query).getTotal();

        Workbook workbook = exportService.exportHandover(query);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        byte[] bytes = out.toByteArray();
        assertEquals(0x50, bytes[0] & 0xFF);
        assertEquals(0x4B, bytes[1] & 0xFF);
        assertEquals(0x03, bytes[2] & 0xFF);
        assertEquals(0x04, bytes[3] & 0xFF);

        Workbook reopened = WorkbookFactory.create(new ByteArrayInputStream(bytes));
        org.apache.poi.ss.usermodel.Sheet sheet = reopened.getSheetAt(0);
        // easypoi 的 ExportParams(title, sheetName) 先写一行大标题，表头在第 2 行
        assertEquals("道路交付及养护协议移交事项", sheet.getRow(0).getCell(0).getStringCellValue());
        assertEquals((int) expected + 1, sheet.getLastRowNum(), "行数 = 数据行 + 1 行标题");
        assertEquals(31, sheet.getRow(1).getLastCellNum(), "导出列数应为 31");
        reopened.close();
    }

    // ==================================================================
    // 六、★ 迁移基线快照（业务补录后需同步更新期望值）
    // ==================================================================

    /**
     * 迁移完成时（2026-09-30）的真实数字。
     *
     * <p><b>业务补录数据后这个方法会失败，这是设计如此</b>：它就是「迁移对账」的可执行版本。
     * 数字变了要么是补录（改期望值并同步更新《线上同步记录》），要么是有人误改（该查）。
     */
    @Test
    public void migrationBaselineSnapshot() {
        HandoverStatVO stat = handoverService.queryStat(new HandoverQueryDTO());
        assertEquals(84L, stat.getTotal(), "迁移后台账总数（旧库 sfyj='是' 的道路类项目）");
        assertEquals(84L, stat.getMigratedCount());
        assertEquals(0L, stat.getManualCount());
        assertEquals(84L, stat.getMissingAgreementCount(), "协议信息全空（旧库没有协议字段）");
        assertEquals(0L, stat.getArchivedCount(), "迁移不关联档案，档案由人工建立关联");
        assertEquals(0L, stat.getMaintenanceExpiringCount(), "养护期未登记");
        assertEquals(0L, stat.getMaintenanceExpiredCount());
        assertEquals(2L, stat.getOrphanCount(), "出让宗地在 t_land 里没匹配上的条数");

        // 状态与类型：迁移后应全部是「已移交 / 正式移交」
        for (HandoverStatVO.NameCount item : stat.getByType()) {
            assertEquals("正式移交", item.getName());
            assertEquals(84L, item.getCount());
        }
        for (HandoverStatVO.NameCount item : stat.getByStatus()) {
            if ("已移交".equals(item.getName())) {
                assertEquals(84L, item.getCount());
            } else {
                assertEquals(0L, item.getCount());
            }
        }
        // 12 个年度分桶
        assertEquals(12, stat.getByYear().size(), "移交编号覆盖 2010~2026 共 12 个年度");

        // 迁移的字段映射抽样：长度/红线宽度来自旧表 cd / ghhxkd，应有 80 条有值
        HandoverQueryDTO query = new HandoverQueryDTO();
        query.setPageSize(500);
        long withLength = handoverService.queryPage(query).getRecords().stream()
                .filter(row -> row.getLengthM() != null).count();
        assertEquals(80L, withLength, "有长度的条数变了（旧库 cd 字段的填充情况）");
    }
}
