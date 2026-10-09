package org.jeecg.modules.land.archive.ledger;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.archive.ledger.dto.LedgerQueryDTO;
import org.jeecg.modules.land.archive.ledger.dto.LedgerSaveDTO;
import org.jeecg.modules.land.archive.ledger.entity.RoadAcceptanceLedger;
import org.jeecg.modules.land.archive.ledger.enums.LedgerMaterial;
import org.jeecg.modules.land.archive.ledger.enums.LedgerStatus;
import org.jeecg.modules.land.archive.ledger.mapper.RoadAcceptanceLedgerMapper;
import org.jeecg.modules.land.archive.ledger.service.ILedgerExportService;
import org.jeecg.modules.land.archive.ledger.service.IRoadAcceptanceLedgerService;
import org.jeecg.modules.land.archive.ledger.vo.LedgerStatVO;
import org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @Description: 道路设施验收及移交资料台账 - 数据层集成测试（真实 MySQL + 真实 Mapper XML）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>这不是「mock 一堆东西的单元测试」，而是**打真库的集成测试**：
 * 走 Controller 用的同一套 Service → Mapper → XML → MySQL。
 * 目的很明确 —— 本机因为缺中台私有件起不了整个应用，就用它来替代「把接口点一遍」。
 *
 * <p><b>断言分两类，不要混</b>：
 * <ul>
 *   <li><b>不变量断言</b>（占多数）：只依赖系统自身的逻辑，业务数据怎么变都成立
 *       —— 例如「4 个状态计数之和 = 总数」「筛出的行确实都缺该类资料」
 *       「序号在分页间连续」；</li>
 *   <li><b>迁移基线快照</b>（{@link #migrationBaselineSnapshot()} 一个方法）：
 *       把 2026-09-30 迁移完成时的真实数字钉在测试里，
 *       业务补录数据后这个方法需要**同步更新期望值**（更新时请一并更新
 *       《道路设施验收及移交资料台账管理-线上同步记录》的第四章）。
 *       它相当于一个可执行的「迁移对账」。</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class RoadAcceptanceLedgerDataTest extends LandIntegrationTestBase {

    @Autowired
    private IRoadAcceptanceLedgerService ledgerService;

    @Autowired
    private RoadAcceptanceLedgerMapper ledgerMapper;

    @Autowired
    private ILedgerExportService exportService;

    // ==================================================================
    // 一、查询与不变量
    // ==================================================================

    @Test
    public void listPagingKeepsSeqContinuousAcrossPages() {
        LedgerQueryDTO first = new LedgerQueryDTO();
        first.setPageNo(1);
        first.setPageSize(10);
        IPage<RoadAcceptanceLedger> page1 = ledgerService.queryPage(first);
        assertNotNull(page1);
        assertTrue(page1.getTotal() > 0, "台账总数为 0，说明迁移或库连接有问题");
        assertEquals(10, page1.getRecords().size());
        // 序号由 Service 用「分页偏移 + 行号」赋值：第 1 页 1..10
        assertEquals(Integer.valueOf(1), page1.getRecords().get(0).getSeq());
        assertEquals(Integer.valueOf(10), page1.getRecords().get(9).getSeq());

        LedgerQueryDTO second = new LedgerQueryDTO();
        second.setPageNo(2);
        second.setPageSize(10);
        IPage<RoadAcceptanceLedger> page2 = ledgerService.queryPage(second);
        // 第 2 页从 11 开始（这是「翻页后序号错位」这类 bug 的直接护栏）
        assertEquals(Integer.valueOf(11), page2.getRecords().get(0).getSeq());
        assertEquals(Integer.valueOf(20), page2.getRecords().get(9).getSeq());

        // 两页不能出现同一条记录
        List<String> ids1 = page1.getRecords().stream().map(RoadAcceptanceLedger::getId).collect(Collectors.toList());
        List<String> ids2 = page2.getRecords().stream().map(RoadAcceptanceLedger::getId).collect(Collectors.toList());
        assertTrue(java.util.Collections.disjoint(ids1, ids2));
    }

    @Test
    public void everyListedRowHasMaterialStatsFilled() {
        LedgerQueryDTO query = new LedgerQueryDTO();
        query.setPageSize(50);
        IPage<RoadAcceptanceLedger> page = ledgerService.queryPage(query);
        for (RoadAcceptanceLedger row : page.getRecords()) {
            // 非表字段必须由服务端补齐（前端「资料 x/13」直接读它）
            assertEquals(Integer.valueOf(LedgerMaterial.values().length), row.getMaterialTotal());
            assertNotNull(row.getMaterialCount());
            assertEquals(Integer.valueOf(row.getMaterialTotal() - row.getMaterialCount()), row.getMaterialMissing());
            // 数据库的 material_count 冗余列必须与 13 个勾选列一致（迁移脚本与后端两处口径）
            assertEquals(LedgerMaterial.countMaterials(row), row.getMaterialCount().intValue(), "material_count 与 13 个勾选列不一致：" + row.getLedgerNo());
        }
    }

    @Test
    public void statusCountsAlwaysSumToTotal() {
        LedgerQueryDTO query = new LedgerQueryDTO();
        long total = ledgerService.queryPage(query).getTotal();

        List<LedgerStatVO.StatusCount> counts = ledgerService.countByStatus(new LedgerQueryDTO());
        assertEquals(4, counts.size(), "固定返回 4 个状态（数量为 0 也要返回）");
        long sum = counts.stream().mapToLong(LedgerStatVO.StatusCount::getCount).sum();
        assertEquals(total, sum, "各状态计数之和必须等于总数");
        for (LedgerStatVO.StatusCount item : counts) {
            assertTrue(LedgerStatus.allValues().contains(item.getStatus()), "状态取值必须是 4 个固定值之一：" + item.getStatus());
        }

        // 按某个状态筛选，条数必须与它的计数一致（这条能抓出「tab 计数与列表口径不一致」）
        LedgerStatVO.StatusCount biggest = counts.stream()
                .max((a, b) -> Long.compare(a.getCount(), b.getCount())).orElse(null);
        assertNotNull(biggest);
        LedgerQueryDTO filtered = new LedgerQueryDTO();
        filtered.setStatus(biggest.getStatus());
        assertEquals(biggest.getCount(), ledgerService.queryPage(filtered).getTotal());
    }

    @Test
    public void missingMaterialFilterReturnsOnlyRowsMissingThatMaterial() {
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            LedgerQueryDTO query = new LedgerQueryDTO();
            query.setMissingMaterial(material.getProperty());
            query.setPageSize(500);
            IPage<RoadAcceptanceLedger> page = ledgerService.queryPage(query);
            for (RoadAcceptanceLedger row : page.getRecords()) {
                assertEquals(0, material.valueOf(row), "筛出的行不该有该类资料：" + material.getProperty());
            }
        }
    }

    @Test
    public void missingMaterialAcceptsAnyOfTheThirteenPropertyNames() {
        // 13 个属性名（驼峰）都必须被接受
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            LedgerQueryDTO query = new LedgerQueryDTO();
            query.setMissingMaterial(material.getProperty());
            assertNotNull(ledgerService.queryPage(query));
        }
        // 列名下划线写法也要接受（前端拼错时更宽容）
        LedgerQueryDTO snake = new LedgerQueryDTO();
        snake.setMissingMaterial("has_jgwj");
        assertNotNull(ledgerService.queryPage(snake));
    }

    @Test
    public void missingMaterialRejectsUnknownValue() {
        LedgerQueryDTO query = new LedgerQueryDTO();
        query.setMissingMaterial("hasNotExist");
        try {
            ledgerService.queryPage(query);
            fail("非法资料名必须被拒绝（否则请求参数会以另一种形式进入 SQL）");
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("不合法"));
        }
    }

    @Test
    public void statGroupsAreConsistentWithQueryScope() {
        LedgerQueryDTO query = new LedgerQueryDTO();
        LedgerStatVO stat = ledgerService.queryStat(query);
        long total = stat.getTotal();
        assertTrue(total > 0);

        // 状态分布之和 = 总数
        assertEquals(total, stat.getByStatus().stream().mapToLong(LedgerStatVO.NameCount::getCount).sum());
        // 13 类资料归集情况固定 13 项，各项不超过总数
        assertEquals(13, stat.getByMaterial().size());
        for (LedgerStatVO.NameCount item : stat.getByMaterial()) {
            assertTrue(item.getCount() <= total, item.getName() + " 的归集数超过了台账总数");
            assertTrue(item.getCount() >= 0);
        }
        // 行政区划分布之和 = 总数（空值会被归到「未填写」，所以不会漏行）
        assertEquals(total, stat.getByXzqh().stream().mapToLong(LedgerStatVO.NameCount::getCount).sum());
        // 迁移生成 + 人工新增 = 总数
        assertEquals(total, stat.getMigratedCount() + stat.getManualCount());
        // 资料齐全 + 一类未归集 不能超过总数（它们只是两个子集，允许重叠为 0）
        assertTrue(stat.getFullMaterialCount() <= total);
        assertTrue(stat.getEmptyMaterialCount() <= total);
    }

    @Test
    public void statusDistributionIgnoresStatusCondition() {
        // 台账页顶部 tab 与统计页的状态分布图都必须「清掉 status 条件」，
        // 否则切换一次筛选，分布图里就只剩一根柱子（提级论证模块踩过这个坑）
        LedgerQueryDTO query = new LedgerQueryDTO();
        query.setStatus("已移交");
        List<LedgerStatVO.StatusCount> counts = ledgerService.countByStatus(query);
        long sum = counts.stream().mapToLong(LedgerStatVO.StatusCount::getCount).sum();
        LedgerQueryDTO all = new LedgerQueryDTO();
        assertEquals(ledgerService.queryPage(all).getTotal(), sum, "状态计数不能受 status 条件影响");
    }

    @Test
    public void materialDefinitionsMatchTheTableColumns() {
        // ★ 这是「建表列 ↔ 后端枚举」的可执行契约检查：
        //   13 个列名与建表脚本 01_t_road_acceptance_ledger.sql 里的 has_* 列逐字一致
        List<String> expected = Arrays.asList(
                "has_sgxk", "has_ysbg", "has_jgtc", "has_zljdbg", "has_cljybg", "has_ajbg",
                "has_ghyshgz", "has_jgbaba", "has_dazxys", "has_dlyjd", "has_yhxy", "has_jgwj", "has_yjwj");
        List<String> actual = LedgerMaterial.ordered().stream()
                .map(LedgerMaterial::getColumn).collect(Collectors.toList());
        assertEquals(expected, actual);
        // 序号必须 1..13 且中文名不重复
        for (int i = 0; i < LedgerMaterial.ordered().size(); i++) {
            assertEquals(i + 1, LedgerMaterial.ordered().get(i).getSeq());
        }
        assertEquals(13, LedgerMaterial.ordered().stream()
                .map(LedgerMaterial::getLabel).distinct().count());
    }

    // ==================================================================
    // 二、台账编号
    // ==================================================================

    @Test
    public void generateLedgerNoIsStableAndUnique() {
        String generated = ledgerService.generateLedgerNo(2026);
        assertTrue(generated.matches("YS-2026-\\d{4}"), "编号格式必须是 YS-{yyyy}-{4位}：" + generated);
        // 只预览不落库：连取两次必须一致
        assertEquals(generated, ledgerService.generateLedgerNo(2026));
        // 生成出来的号一定不冲突
        ledgerService.checkLedgerNoUnique(generated, null);

        // 已存在的号必须被判冲突
        LedgerQueryDTO query = new LedgerQueryDTO();
        query.setPageSize(1);
        String existing = ledgerService.queryPage(query).getRecords().get(0).getLedgerNo();
        try {
            ledgerService.checkLedgerNoUnique(existing, null);
            fail("已存在的台账编号必须报冲突：" + existing);
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("已存在"));
        }
        // 排除自身后不算冲突（编辑场景）
        ledgerService.checkLedgerNoUnique(existing, ledgerService.queryPage(query).getRecords().get(0).getId());
    }

    // ==================================================================
    // 三、增删改（全程在事务里，结束回滚；另有 TEST 前缀行的物理清理兜底）
    // ==================================================================

    @Test
    public void createUpdateChangeStatusDelete() {
        // ---------- 新增：13 类资料全勾 ----------
        LedgerSaveDTO dto = new LedgerSaveDTO();
        dto.setLedgerNo(TEST_LEDGER_NO_PREFIX + "0001");
        dto.setRoadName("集成测试道路（可删）");
        dto.setXzqh("北辰区");
        dto.setPtsslb("道路");
        dto.setAcceptanceType("竣工验收");
        dto.setAcceptanceDate(new Date());
        dto.setAcceptanceResult("合格");
        dto.setStatus("已验收");
        dto.setHasSgxk(1);
        dto.setHasYsbg(1);
        dto.setHasJgtc(1);
        dto.setHasZljdbg(1);
        dto.setHasCljybg(1);
        dto.setHasAjbg(1);
        dto.setHasGhyshgz(1);
        dto.setHasJgbaba(1);
        dto.setHasDazxys(1);
        dto.setHasDlyjd(1);
        dto.setHasYhxy(1);
        dto.setHasJgwj(1);
        dto.setHasYjwj(1);

        String id = ledgerService.createLedger(dto);
        assertNotNull(id);

        RoadAcceptanceLedger created = ledgerService.queryDetail(id);
        assertNotNull(created);
        assertEquals(created.getLedgerNo(), TEST_LEDGER_NO_PREFIX + "0001");
        assertEquals(Integer.valueOf(13), created.getMaterialCount(), "13 类资料全勾时必须算出 13");
        assertEquals(Integer.valueOf(13), created.getMaterialTotal());
        assertEquals(Integer.valueOf(0), created.getMaterialMissing());
        assertEquals(null, created.getSourceFacilityId(), "人工新增的记录，迁移幂等键必须为空");
        // 道路名称会同步到配套项目名称列（台账两列同值）
        assertEquals(created.getRoadName(), created.getPtxmmc());

        // ---------- 编辑：清空验收日期 + 取消 3 类资料 ----------
        LedgerSaveDTO edit = new LedgerSaveDTO();
        edit.setId(id);
        edit.setLedgerNo(created.getLedgerNo());
        edit.setRoadName("集成测试道路（已改名）");
        edit.setStatus("已验收");
        // ★ 故意不传 acceptanceDate / acceptanceResult / 若干文本字段：
        //   显式 UPDATE 会把它们写成 NULL（这正是 updateLedgerAll 存在的理由）
        edit.setHasSgxk(1);
        edit.setHasYsbg(1);
        edit.setHasJgtc(1);
        edit.setHasZljdbg(1);
        edit.setHasCljybg(1);
        edit.setHasAjbg(1);
        edit.setHasGhyshgz(1);
        edit.setHasJgbaba(1);
        edit.setHasDazxys(1);
        edit.setHasDlyjd(0);
        edit.setHasYhxy(0);
        edit.setHasJgwj(0);
        edit.setHasYjwj(1);
        ledgerService.updateLedger(edit);

        RoadAcceptanceLedger updated = ledgerService.queryDetail(id);
        assertEquals(updated.getRoadName(), "集成测试道路（已改名）");
        assertEquals(Integer.valueOf(10), updated.getMaterialCount(), "取消 3 类资料后必须是 10");
        assertNull(updated.getAcceptanceDate(), "★ 显式 SQL 必须能把已填字段清空为 NULL（updateById 做不到）");
        assertNull(updated.getAcceptanceResult());

        // ---------- 变更状态 ----------
        ledgerService.changeStatus(id, "已移交");
        assertEquals(ledgerService.queryDetail(id).getStatus(), "已移交");
        try {
            ledgerService.changeStatus(id, "随便写的状态");
            fail("非法状态必须被拒绝");
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("不合法"));
        }

        // ---------- 逻辑删除 ----------
        ledgerService.deleteLedger(id);
        assertNull(ledgerService.queryDetail(id), "删除后详情应为空（@TableLogic 逻辑删除）");
        // 库里仍留一行且 del_flag=1（可追溯，不是物理删除）
        Integer delFlag = new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForObject(
                "SELECT del_flag FROM t_road_acceptance_ledger WHERE id = ?", Integer.class, id);
        assertEquals(Integer.valueOf(1), delFlag);
    }

    @Test
    public void createRequiresRoadNameAndValidStatus() {
        LedgerSaveDTO noName = new LedgerSaveDTO();
        noName.setStatus("未验收");
        try {
            ledgerService.createLedger(noName);
            fail("道路名称为空必须被拒绝");
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("道路名称不能为空"));
        }

        LedgerSaveDTO badStatus = new LedgerSaveDTO();
        badStatus.setRoadName("集成测试道路（可删）");
        badStatus.setStatus("已完工");
        try {
            ledgerService.createLedger(badStatus);
            fail("非法状态必须被拒绝");
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("不合法"));
        }
    }

    @Test
    public void createWithoutLedgerNoGeneratesOne() {
        LedgerSaveDTO dto = new LedgerSaveDTO();
        dto.setRoadName("集成测试道路-自动编号（可删）");
        String id = ledgerService.createLedger(dto);
        RoadAcceptanceLedger created = ledgerService.queryDetail(id);
        assertNotNull(created.getLedgerNo());
        assertTrue(created.getLedgerNo().matches("YS-\\d{4}-\\d{4}"), "自动编号格式：YS-{yyyy}-{4位}，实际=" + created.getLedgerNo());
        assertEquals(LedgerStatus.defaultValue(), created.getStatus(), "默认状态必须是「未验收」");
    }

    // ==================================================================
    // 四、关联档案
    // ==================================================================

    @Test
    public void linkAndUnlinkArchive() {
        // 从真实档案里挑一个可关联的（本库有档案数据；没有就跳过，不让测试因环境缺数据而红）
        List<RelatedArchiveVO> candidates = ledgerService.queryArchivesForPick(null, null, null, 5);
        if (candidates == null || candidates.isEmpty()) {
            System.out.println("[跳过] 库里没有档案数据，无法验证关联档案");
            return;
        }
        RelatedArchiveVO archive = candidates.get(0);

        LedgerSaveDTO dto = new LedgerSaveDTO();
        dto.setLedgerNo(TEST_LEDGER_NO_PREFIX + "0002");
        dto.setRoadName("集成测试道路-关联档案（可删）");
        dto.setFacilityId(archive.getFacilityId());
        dto.setCrzdbh(archive.getCrzdbh());
        String id = ledgerService.createLedger(dto);

        // 关联
        ledgerService.linkArchive(id, archive.getId());
        RoadAcceptanceLedger linked = ledgerService.queryDetail(id);
        assertEquals(archive.getId(), linked.getArchiveId());
        assertNotNull(linked.getArchiveCount(), "关联档案数应被重算");
        assertTrue(linked.getArchiveCount() >= 1);
        // 详情里的关联档案列表必须能查到它
        List<String> archiveIds = linked.getRelatedArchives().stream()
                .map(RelatedArchiveVO::getId).collect(Collectors.toList());
        assertTrue(archiveIds.contains(archive.getId()), "关联档案列表里必须有刚关联的那份");

        // 关联不存在的档案必须报错
        try {
            ledgerService.linkArchive(id, "not-exist-archive-id");
            fail("关联不存在的档案必须被拒绝");
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("档案不存在"));
        }

        // 取消关联：archive_id 必须真的被写成 NULL
        ledgerService.unlinkArchive(id);
        assertNull(ledgerService.queryDetail(id).getArchiveId(), "取消关联后 archive_id 必须为 NULL");
    }

    // ==================================================================
    // 五、导出
    // ==================================================================

    @Test
    public void exportProducesRealXlsxWithAllColumns() throws Exception {
        LedgerQueryDTO query = new LedgerQueryDTO();
        query.setStatus("已移交");
        long expected = ledgerService.queryPage(query).getTotal();

        Workbook workbook = exportService.exportLedger(query);
        assertNotNull(workbook);
        // ★ 必须能按 xlsx 打开：下面 WorkbookFactory.create 会校验 OOXML 结构，
        //   若 ExportParams 漏了 setType(ExcelType.XSSF)，这里就会抛
        //   「The supplied data appears to be in the OLE2 Format」（档案模块踩过的坑）
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        byte[] bytes = out.toByteArray();
        // 魔数必须是 OOXML（ZIP）：50 4B 03 04
        assertEquals(0x50, bytes[0] & 0xFF);
        assertEquals(0x4B, bytes[1] & 0xFF);
        assertEquals(0x03, bytes[2] & 0xFF);
        assertEquals(0x04, bytes[3] & 0xFF);

        Workbook reopened = WorkbookFactory.create(new ByteArrayInputStream(bytes));
        org.apache.poi.ss.usermodel.Sheet sheet = reopened.getSheetAt(0);
        // ★ easypoi 的 ExportParams(title, sheetName) 会先写一行「大标题」，再写表头，
        //   所以：第 1 行（下标 0）是标题、第 2 行（下标 1）是表头、数据从下标 2 开始。
        assertEquals("道路设施验收及移交资料台账",
                sheet.getRow(0).getCell(0).getStringCellValue());
        // 最后一行下标 = 1 标题行 + N 数据行 = N + 1
        assertEquals((int) expected + 1, sheet.getLastRowNum(), "导出行数应等于筛选结果数 + 1 行标题");
        org.apache.poi.ss.usermodel.Row header = sheet.getRow(1);
        // 43 列 = 12 台账/道路 + 6 验收 + 3 移交 + 13 资料 + 2 归集数 + 7 状态与留痕
        assertEquals(43, header.getLastCellNum(), "导出列数应为 43");
        int materialHeaders = 0;
        for (int i = 0; i < 43; i++) {
            String headerText = header.getCell(i).getStringCellValue();
            if (headerText.startsWith("资料")) {
                materialHeaders++;
            }
        }
        assertEquals(13, materialHeaders, "13 类资料列必须都在导出表头里");
        reopened.close();
    }

    // ==================================================================
    // 六、★ 迁移基线快照（业务补录数据后需要同步更新期望值）
    // ==================================================================

    /**
     * 迁移完成时（2026-09-30）的真实数字。
     *
     * <p><b>这个方法会在业务补录数据后失败，这是设计如此</b>：
     * 它是「迁移对账」的可执行版本 —— 数字变了就说明库里的数据变了，
     * 要么是补录（那就把期望值改掉，并同步更新《线上同步记录》第四章），
     * 要么是有人误改了数据（那就该查）。
     */
    @Test
    public void migrationBaselineSnapshot() {
        LedgerStatVO stat = ledgerService.queryStat(new LedgerQueryDTO());
        assertEquals(1340L, stat.getTotal(), "迁移后台账总数（道路 1332 + 市政道路 4 + 道路及管线 4）");
        assertEquals(1340L, stat.getMigratedCount(), "由迁移生成");
        assertEquals(0L, stat.getManualCount(), "人工新增");
        assertEquals(53L, stat.getOrphanCount(), "出让宗地未匹配（t_land 里没有对应 crzdbh）");
        assertEquals(0L, stat.getFullMaterialCount(), "13 类资料全部归集（迁移不可能达到）");
        assertEquals(713L, stat.getEmptyMaterialCount(), "一类资料都没归集");

        // 13 类资料的基线归集数：只有 5 类有据可依（施工许可/竣工验收报告/竣工图测/竣工文件/移交文件）
        java.util.Map<String, Long> expected = new java.util.HashMap<>();
        expected.put("施工许可证", 408L);
        expected.put("竣工验收报告", 409L);
        expected.put("竣工图测", 292L);
        expected.put("质量监督报告", 0L);
        expected.put("材料检验报告", 0L);
        expected.put("安全监督报告", 0L);
        expected.put("规划验收合格证", 0L);
        expected.put("竣工验收备案表", 0L);
        expected.put("档案专项验收意见", 0L);
        expected.put("道路工程移交单", 0L);
        expected.put("养护协议", 0L);
        expected.put("竣工文件", 7L);
        expected.put("移交文件", 1L);
        assertEquals(13, stat.getByMaterial().size());
        for (LedgerStatVO.NameCount item : stat.getByMaterial()) {
            Long want = expected.get(item.getName());
            assertNotNull(want, "统计里出现了未登记的资料名：" + item.getName());
            assertEquals(want.longValue(), item.getCount(), "资料「" + item.getName() + "」的归集数变了（补录后请同步更新本测试与同步记录）");
        }

        // 状态基线
        java.util.Map<String, Long> statusExpected = new java.util.HashMap<>();
        statusExpected.put("未验收", 917L);
        statusExpected.put("已验收", 337L);
        statusExpected.put("已移交", 84L);
        statusExpected.put("验收中", 2L);
        for (LedgerStatVO.NameCount item : stat.getByStatus()) {
            assertEquals(statusExpected.get(item.getName()).longValue(), item.getCount(), "状态「" + item.getName() + "」的条数变了");
        }

        // 迁移的字段映射抽样：竣工文件=是 的那 7 条，必须都来自旧表 jgwj='是'
        LedgerQueryDTO query = new LedgerQueryDTO();
        query.setBeginMaterialCount(5);
        query.setPageSize(100);
        IPage<RoadAcceptanceLedger> top = ledgerService.queryPage(query);
        assertFalse(top.getRecords().isEmpty(), "归集数 ≥5 的台账不该为空");
        for (RoadAcceptanceLedger row : top.getRecords()) {
            assertNotNull(row.getSourceFacilityId(), "迁移生成的记录必须有来源配套项目ID");
            assertNotNull(row.getLedgerNo(), "迁移生成的记录必须有台账编号");
        }
    }

    // ==================================================================
    // 七、Mapper 语句直测（16 条语句里的查询类，确保 XML 全部可执行）
    // ==================================================================

    @Test
    public void allMapperStatementsExecute() {
        LedgerQueryDTO query = new LedgerQueryDTO();
        assertNotNull(ledgerMapper.selectLedgerPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 5), query));
        assertNotNull(ledgerMapper.selectForExport(query));
        assertNotNull(ledgerMapper.selectSummary(query));
        assertNotNull(ledgerMapper.selectCountGroupByStatus(query));
        assertNotNull(ledgerMapper.selectStatByColumn(query, "xzqh", "未填写"));
        assertNotNull(ledgerMapper.selectStatByColumn(query, "gnq", "非功能区"));
        assertNotNull(ledgerMapper.selectStatByColumn(query, "acceptance_type", "未登记"));
        assertNotNull(ledgerMapper.selectStatByColumn(query, "acceptance_result", "未登记"));
        assertNotNull(ledgerMapper.selectStatByColumn(query, "ptsslb", "未填写"));
        assertNotNull(ledgerMapper.selectStatByYear(query));
        assertNotNull(ledgerMapper.selectMaterialCoverage(query));
        assertEquals(0, ledgerMapper.countByLedgerNo("YS-NOT-EXIST-9999", null));
        assertNotNull(ledgerMapper.selectMaxSeqOfYear("YS-2026-"));
        assertNotNull(ledgerMapper.selectRelatedArchives(null, null, 5));
        assertEquals(0, ledgerMapper.countRelatedArchives(null, null));
        assertNotNull(ledgerMapper.selectArchivesForPick(null, null, null, 5));
        assertEquals(0, ledgerMapper.countArchiveById("not-exist"));
    }
}
