package org.jeecg.modules.land.archive.completion;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.archive.completion.dto.CompletionQueryDTO;
import org.jeecg.modules.land.archive.completion.dto.CompletionSaveDTO;
import org.jeecg.modules.land.archive.completion.entity.CompletionArchive;
import org.jeecg.modules.land.archive.completion.enums.DigitizeStatus;
import org.jeecg.modules.land.archive.completion.mapper.CompletionArchiveMapper;
import org.jeecg.modules.land.archive.completion.service.ICompletionArchiveService;
import org.jeecg.modules.land.archive.completion.service.ICompletionExportService;
import org.jeecg.modules.land.archive.completion.support.CompletionSupport;
import org.jeecg.modules.land.archive.completion.vo.CompletionStatVO;
import org.jeecg.modules.land.archive.completion.vo.RelatedArchiveVO;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
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
 * @Description: 竣工验收项目历史工程资料数字化档案 - 数据层集成测试（真实 MySQL + 真实 Mapper XML）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>这不是「mock 一堆东西的单元测试」，而是**打真库的集成测试**：
 * 走 Controller 用的同一套 Service → Mapper → XML → MySQL。
 * 目的很明确 —— 本机因为缺中台私有件起不了整个应用，就用它来替代「把接口点一遍」。
 *
 * <p><b>★ 与台账模块测试最大的差别：本模块【没有数据迁移】，库里是空表</b>，
 * 所以除了 {@link #noMigrationBaselineSnapshot()} 这一个基线方法外，
 * <b>其余用例全部自己造数据</b>（前缀 {@code JG-TEST-}），不依赖任何存量行。
 *
 * <p><b>断言分两类，不要混</b>：
 * <ul>
 *   <li><b>不变量断言</b>（占多数）：只依赖系统自身的逻辑，业务数据怎么变都成立
 *       —— 例如「3 个状态计数之和 = 总数」「序号在分页间连续」「显式 SQL 能把字段清空」；</li>
 *   <li><b>空表基线</b>（{@link #noMigrationBaselineSnapshot()} 一个方法）：
 *       钉住「建表后没有任何存量数据」这个事实，即「本次不做数据迁移」的可执行版本。
 *       一旦开始录入历史档案，这个方法会失败 —— 那是设计如此，请把期望值改成当时的真实条数。</li>
 * </ul>
 *
 * <p><b>清理</b>：整个测试类跑在一个事务里、结束回滚；另有 {@link #cleanCompletionTestRows()}
 * 在事务结束后按 {@code archive_no LIKE 'JG-TEST-%'} 物理兜底清理
 * （写在 {@code @AfterTransaction} 而不是 {@code @AfterEach}：后者时事务还没结束，DELETE 会被一起回滚）。
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class CompletionArchiveDataTest extends LandIntegrationTestBase {

    /** 测试行前缀（业务上不会产生这个前缀；也刻意不是 JG-{4位年}- 格式，一眼能认出是测试数据） */
    private static final String TEST_ARCHIVE_NO_PREFIX = "JG-TEST-";

    /**
     * 本次测试真正创建过的档案 id。
     *
     * <p>为什么要单独记：有的用例刻意用**真实编号格式**（如「自动编号生成」与「编号唯一校验」
     * 用 JG-{yyyy}-{4位}），它们不匹配 {@code JG-TEST-%}，只靠前缀清理覆盖不到。
     * JUnit 5 每个测试方法都新建测试实例，所以这个列表天然是「本方法专属」的。
     */
    private final List<String> createdIds = new java.util.ArrayList<>();

    @Autowired
    private ICompletionArchiveService completionService;

    @Autowired
    private CompletionArchiveMapper completionMapper;

    @Autowired
    private ICompletionExportService exportService;

    /**
     * 事务结束（已回滚）后，把测试行物理删掉，保证库里不留测试记录。
     *
     * <p>★ 走基类的 {@link LandIntegrationTestBase#safeCleanup}：
     * 它是「兜底」不是断言，失败时不能把测试判成失败。本测试连的是远程库、
     * 数据源没有连接池，逐条 update 会各开一次连接，全量跑下来偶发撞上
     * 「Communications link failure」——那会报出一个假红。
     */
    @AfterTransaction
    public void cleanCompletionTestRows() {
        List<Object[]> statements = new ArrayList<>();
        // ① 按测试前缀清理（覆盖绝大多数用例）
        statements.add(new Object[]{
                "DELETE FROM `t_completion_archive` WHERE `archive_no` LIKE ?",
                TEST_ARCHIVE_NO_PREFIX + "%"});
        // ② 兜底：按本次测试真正创建的 id 清理（覆盖「故意占用真实编号格式」的用例）
        for (String id : createdIds) {
            statements.add(new Object[]{"DELETE FROM `t_completion_archive` WHERE `id` = ?", id});
        }
        safeCleanup(dataSource, "竣工档案测试行清理",
                statements.toArray(new Object[0][]));
    }

    // ==================================================================
    // 一、查询与不变量
    // ==================================================================

    @Test
    public void listPagingKeepsSeqContinuousAcrossPages() {
        // 空表没法验证分页，先造 12 条（全部带测试前缀，查询时按前缀过滤）
        for (int i = 1; i <= 12; i++) {
            createTestArchive(String.format("%04d", i), "分页测试项目-" + i);
        }

        CompletionQueryDTO first = new CompletionQueryDTO();
        first.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        first.setPageNo(1);
        first.setPageSize(10);
        IPage<CompletionArchive> page1 = completionService.queryPage(first);
        assertNotNull(page1);
        assertEquals(12L, page1.getTotal(), "测试前缀下应有 12 条");
        assertEquals(10, page1.getRecords().size());
        // 序号由 Service 用「分页偏移 + 行号」赋值：第 1 页 1..10
        assertEquals(Integer.valueOf(1), page1.getRecords().get(0).getSeq());
        assertEquals(Integer.valueOf(10), page1.getRecords().get(9).getSeq());

        CompletionQueryDTO second = new CompletionQueryDTO();
        second.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        second.setPageNo(2);
        second.setPageSize(10);
        IPage<CompletionArchive> page2 = completionService.queryPage(second);
        // 第 2 页从 11 开始（这是「翻页后序号错位」这类 bug 的直接护栏）
        assertEquals(Integer.valueOf(11), page2.getRecords().get(0).getSeq());
        assertEquals(Integer.valueOf(12), page2.getRecords().get(1).getSeq());

        // 两页不能出现同一条记录
        List<String> ids1 = page1.getRecords().stream().map(CompletionArchive::getId).collect(Collectors.toList());
        List<String> ids2 = page2.getRecords().stream().map(CompletionArchive::getId).collect(Collectors.toList());
        assertTrue(java.util.Collections.disjoint(ids1, ids2));
    }

    @Test
    public void everyListedRowHasDigitizeStatsFilled() {
        createTestArchive("0201", "展示字段测试项目");
        CompletionQueryDTO query = new CompletionQueryDTO();
        query.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        query.setPageSize(50);
        IPage<CompletionArchive> page = completionService.queryPage(query);
        assertFalse(page.getRecords().isEmpty());
        for (CompletionArchive row : page.getRecords()) {
            // 非表字段必须由服务端补齐（前端进度条直接读它）
            assertNotNull(row.getDigitizePercent(), "digitizePercent 必须由服务端补齐：" + row.getArchiveNo());
            assertTrue(DigitizeStatus.isValid(row.getDigitizeStatus()), "状态必须是 3 个固定值之一：" + row.getDigitizeStatus());
            // 进度折算必须与 CompletionSupport 的口径一致（0 / 50 / 100）
            assertEquals(CompletionSupport.digitizePercent(row.getDigitizeStatus()),
                    row.getDigitizePercent().intValue(),
                    "digitizePercent 与状态不一致：" + row.getArchiveNo());
        }
    }

    @Test
    public void statusCountsAlwaysSumToTotal() {
        createTestArchive("0301", "状态计数项目-A");
        createTestArchive("0302", "状态计数项目-B");
        createTestArchive("0303", "状态计数项目-C");

        CompletionQueryDTO query = new CompletionQueryDTO();
        query.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        long total = completionService.queryPage(query).getTotal();
        assertEquals(3L, total);

        List<CompletionStatVO.StatusCount> counts = completionService.countByStatus(query);
        assertEquals(3, counts.size(), "固定返回 3 个状态（数量为 0 也要返回）");
        long sum = counts.stream().mapToLong(CompletionStatVO.StatusCount::getCount).sum();
        assertEquals(total, sum, "各状态计数之和必须等于总数");
        for (CompletionStatVO.StatusCount item : counts) {
            assertTrue(DigitizeStatus.allValues().contains(item.getStatus()),
                    "状态取值必须是 3 个固定值之一：" + item.getStatus());
        }

        // 按某个状态筛选，条数必须与它的计数一致（这条能抓出「tab 计数与列表口径不一致」）
        CompletionStatVO.StatusCount biggest = counts.stream()
                .max((a, b) -> Long.compare(a.getCount(), b.getCount())).orElse(null);
        assertNotNull(biggest);
        CompletionQueryDTO filtered = new CompletionQueryDTO();
        filtered.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        filtered.setDigitizeStatus(biggest.getStatus());
        assertEquals(biggest.getCount(), completionService.queryPage(filtered).getTotal());
    }

    @Test
    public void statusDistributionIgnoresStatusCondition() {
        // 状态页签与统计页的状态分布都必须「清掉 digitizeStatus 条件」，
        // 否则切换一次筛选，分布图里就只剩一根柱子。
        // ★ 同时这条用例也是「normalize 必须返回独立副本」的护栏：
        //   若 Service 直接复用请求 DTO，清空 status 会连带影响后续分组统计的口径。
        createTestArchive("0401", "状态分布项目-A");
        createTestArchive("0402", "状态分布项目-B");

        CompletionQueryDTO filteredQuery = new CompletionQueryDTO();
        filteredQuery.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        filteredQuery.setDigitizeStatus("已数字化");

        List<CompletionStatVO.StatusCount> counts = completionService.countByStatus(filteredQuery);
        long sum = counts.stream().mapToLong(CompletionStatVO.StatusCount::getCount).sum();

        CompletionQueryDTO allQuery = new CompletionQueryDTO();
        allQuery.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        assertEquals(completionService.queryPage(allQuery).getTotal(), sum,
                "状态计数不能受 digitizeStatus 条件影响");

        // 传进去的 DTO 不该被 Service 改坏（副本语义）
        assertEquals("已数字化", filteredQuery.getDigitizeStatus(),
                "Service 不应修改调用方传入的条件对象");
    }

    @Test
    public void statGroupsAreConsistentWithQueryScope() {
        createTestArchive("0501", "统计口径项目-A");
        createTestArchive("0502", "统计口径项目-B");
        createTestArchive("0503", "统计口径项目-C");

        CompletionQueryDTO query = new CompletionQueryDTO();
        query.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        CompletionStatVO stat = completionService.queryStat(query);
        long total = stat.getTotal();
        assertEquals(3L, total);

        // 状态分布之和 = 总数
        assertEquals(total, stat.getByStatus().stream().mapToLong(CompletionStatVO.NameCount::getCount).sum());
        // 行政区划分布之和 = 总数（空值会被归到「未填写」，所以不会漏行）
        assertEquals(total, stat.getByXzqh().stream().mapToLong(CompletionStatVO.NameCount::getCount).sum());
        // 项目类型 / 保管期限分布之和 = 总数（同样有空值兜底）
        assertEquals(total, stat.getByType().stream().mapToLong(CompletionStatVO.NameCount::getCount).sum());
        assertEquals(total, stat.getByRetention().stream().mapToLong(CompletionStatVO.NameCount::getCount).sum());
        // 档案编号年度之和 = 总数（测试行的编号也是 JG- 开头，所以不会漏）
        assertEquals(total, stat.getByYear().stream().mapToLong(CompletionStatVO.NameCount::getCount).sum());
        // 竣工年度只统计有竣工日期的行 → 不能超过总数
        assertTrue(stat.getByCompleteYear().stream().mapToLong(CompletionStatVO.NameCount::getCount).sum() <= total);
        // 已挂扫描件 / 已数字化是子集
        assertTrue(stat.getArchivedCount() <= total);
        assertTrue(stat.getDigitizedCount() <= total);
        // 页数与投资额合计不能为负
        assertTrue(stat.getPageTotal() >= 0);
        assertTrue(stat.getFileTotal() >= 0);
        assertNotNull(stat.getInvestTotal());
        assertTrue(stat.getInvestTotal().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    public void hasArchiveFilterSplitsRowsInTwo() {
        createTestArchive("0601", "扫描件筛选项目-未挂");
        createTestArchive("0602", "扫描件筛选项目-未挂");

        CompletionQueryDTO base = new CompletionQueryDTO();
        base.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        long all = completionService.queryPage(base).getTotal();
        assertEquals(2L, all);

        CompletionQueryDTO withArchive = new CompletionQueryDTO();
        withArchive.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        withArchive.setHasArchive(Boolean.TRUE);
        CompletionQueryDTO withoutArchive = new CompletionQueryDTO();
        withoutArchive.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        withoutArchive.setHasArchive(Boolean.FALSE);

        long has = completionService.queryPage(withArchive).getTotal();
        long hasNot = completionService.queryPage(withoutArchive).getTotal();
        assertEquals(all, has + hasNot, "「是否已挂扫描件」两个分支之和必须等于总数（既不漏也不重）");
        assertEquals(0L, has, "本用例没有关联任何扫描件，已挂的应为 0");
        assertEquals(all, hasNot);

        // 筛出来「未挂扫描件」的行，archive_id 必须真的为空
        CompletionQueryDTO page = new CompletionQueryDTO();
        page.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        page.setHasArchive(Boolean.FALSE);
        page.setPageSize(50);
        for (CompletionArchive row : completionService.queryPage(page).getRecords()) {
            assertNull(row.getArchiveId(), "筛出的行不该有扫描件：" + row.getArchiveNo());
        }
    }

    @Test
    public void digitizeStatusEnumContract() {
        // ★ 这是「代码枚举 ↔ 表注释」的可执行契约检查：3 个取值、顺序与中文逐字一致
        List<String> expected = Arrays.asList("未数字化", "数字化中", "已数字化");
        assertEquals(expected, DigitizeStatus.allValues());
        assertEquals("未数字化", DigitizeStatus.defaultValue());
        assertTrue(DigitizeStatus.isValid("已数字化"));
        assertFalse(DigitizeStatus.isValid("已完工"), "不在这 3 个值里的状态必须判非法");
        assertFalse(DigitizeStatus.isValid(null));
        // 进度折算：0 / 50 / 100（只用于展示，不参与统计）
        assertEquals(0, CompletionSupport.digitizePercent("未数字化"));
        assertEquals(50, CompletionSupport.digitizePercent("数字化中"));
        assertEquals(100, CompletionSupport.digitizePercent("已数字化"));
        assertEquals(0, CompletionSupport.digitizePercent(null));
    }

    // ==================================================================
    // 二、档案编号
    // ==================================================================

    @Test
    public void generateArchiveNoIsStableAndUnique() {
        String generated = completionService.generateArchiveNo(2026);
        assertTrue(generated.matches("JG-2026-\\d{4}"), "编号格式必须是 JG-{yyyy}-{4位}：" + generated);
        // 只预览不落库：连取两次必须一致
        assertEquals(generated, completionService.generateArchiveNo(2026));
        // 生成出来的号一定不冲突
        completionService.checkArchiveNoUnique(generated, null);

        // 已存在的号必须被判冲突（造一条占用它）
        createTestArchiveWithNo(generated, "编号占用测试项目");
        try {
            completionService.checkArchiveNoUnique(generated, null);
            fail("已存在的档案编号必须报冲突：" + generated);
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("已存在"));
        }
        // 排除自身后不算冲突（编辑场景）
        CompletionQueryDTO query = new CompletionQueryDTO();
        query.setArchiveNo(generated);
        query.setPageSize(1);
        String id = completionService.queryPage(query).getRecords().get(0).getId();
        completionService.checkArchiveNoUnique(generated, id);
    }

    // ==================================================================
    // 三、增删改（全程在事务里，结束回滚；另有 TEST 前缀行的物理清理兜底）
    // ==================================================================

    @Test
    public void createUpdateChangeStatusDelete() {
        // ---------- 新增：字段尽量填满 ----------
        CompletionSaveDTO dto = new CompletionSaveDTO();
        dto.setArchiveNo(TEST_ARCHIVE_NO_PREFIX + "1001");
        dto.setProjectName("集成测试历史项目（可删）");
        dto.setProjectCode("HIST-2008-001");
        dto.setXzqh("北辰区");
        dto.setProjectType("道路工程");
        dto.setRetention("永久");
        dto.setBuildUnit("天津市某建设单位");
        dto.setConstructUnit("天津市某施工单位");
        dto.setDesignUnit("天津市某设计单位");
        dto.setSuperviseUnit("天津市某监理单位");
        dto.setStartDate(new Date());
        dto.setCompleteDate(new Date());
        dto.setAcceptanceDate(new Date());
        dto.setInvestAmount(new BigDecimal("12345.67"));
        dto.setDigitizeStatus("数字化中");
        dto.setDigitizeOrg("某数字化加工单位");
        dto.setPageCount(1234);
        dto.setFileCount(56);
        dto.setScanDpi(300);

        String id = completionService.createArchive(dto);
        assertNotNull(id);

        CompletionArchive created = completionService.queryDetail(id);
        assertNotNull(created);
        assertEquals(TEST_ARCHIVE_NO_PREFIX + "1001", created.getArchiveNo());
        assertEquals("数字化中", created.getDigitizeStatus());
        assertEquals(Integer.valueOf(50), created.getDigitizePercent(), "数字化中应折算成 50%");
        assertEquals(Integer.valueOf(1234), created.getPageCount());
        assertEquals(Integer.valueOf(300), created.getScanDpi());
        assertEquals(0, new BigDecimal("12345.67").compareTo(created.getInvestAmount()));
        assertNull(created.getArchiveId(), "新增时不应有扫描件指针");
        assertEquals(Integer.valueOf(0), created.getArchiveCount(), "没有关联键时关联数为 0");
        assertNotNull(created.getRelatedArchives(), "详情必须带关联扫描件列表（可以为空表）");

        // ---------- 编辑：清空一批字段 + 改成已数字化 ----------
        CompletionSaveDTO edit = new CompletionSaveDTO();
        edit.setId(id);
        edit.setArchiveNo(created.getArchiveNo());
        edit.setProjectName("集成测试历史项目（已改名）");
        edit.setDigitizeStatus("已数字化");
        edit.setDigitizeDate(new Date());
        edit.setPageCount(2000);
        // ★ 故意不传 completeDate / acceptanceDate / investAmount / retention / digitizeOrg / scanDpi：
        //   显式 UPDATE 会把它们写成 NULL（这正是 updateCompletionAll 存在的理由）
        completionService.updateArchive(edit);

        CompletionArchive updated = completionService.queryDetail(id);
        assertEquals("集成测试历史项目（已改名）", updated.getProjectName());
        assertEquals("已数字化", updated.getDigitizeStatus());
        assertEquals(Integer.valueOf(100), updated.getDigitizePercent());
        assertEquals(Integer.valueOf(2000), updated.getPageCount());
        assertNull(updated.getCompleteDate(), "★ 显式 SQL 必须能把已填日期清空为 NULL（updateById 做不到）");
        assertNull(updated.getAcceptanceDate());
        assertNull(updated.getInvestAmount());
        assertNull(updated.getRetention());
        assertNull(updated.getScanDpi());

        // ---------- 变更数字化状态 ----------
        completionService.changeDigitizeStatus(id, "未数字化");
        assertEquals("未数字化", completionService.queryDetail(id).getDigitizeStatus());
        try {
            completionService.changeDigitizeStatus(id, "随便写的状态");
            fail("非法数字化状态必须被拒绝");
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("不合法"));
        }

        // ---------- 逻辑删除 ----------
        completionService.deleteArchive(id);
        assertNull(completionService.queryDetail(id), "删除后详情应为空（@TableLogic 逻辑删除）");
        // 库里仍留一行且 del_flag=1（可追溯，不是物理删除）
        Integer delFlag = new JdbcTemplate(dataSource).queryForObject(
                "SELECT del_flag FROM t_completion_archive WHERE id = ?", Integer.class, id);
        assertEquals(Integer.valueOf(1), delFlag);
    }

    @Test
    public void createRequiresProjectNameAndValidStatus() {
        CompletionSaveDTO noName = new CompletionSaveDTO();
        noName.setDigitizeStatus("未数字化");
        try {
            completionService.createArchive(noName);
            fail("历史项目名称为空必须被拒绝");
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("历史项目名称不能为空"));
        }

        CompletionSaveDTO badStatus = new CompletionSaveDTO();
        badStatus.setProjectName("集成测试历史项目（可删）");
        badStatus.setDigitizeStatus("已完工");
        try {
            completionService.createArchive(badStatus);
            fail("非法数字化状态必须被拒绝");
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("不合法"));
        }
    }

    @Test
    public void createWithoutArchiveNoGeneratesOne() {
        CompletionSaveDTO dto = new CompletionSaveDTO();
        dto.setProjectName("集成测试历史项目-自动编号（可删）");
        // 刻意不传 archiveNo
        String id = completionService.createArchive(dto);
        // 自动生成的是**真实编号格式**（JG-{yyyy}-{4位}），不匹配测试前缀 → 单独登记 id 兜底清理
        createdIds.add(id);
        CompletionArchive created = completionService.queryDetail(id);
        assertNotNull(created.getArchiveNo());
        assertTrue(created.getArchiveNo().matches("JG-\\d{4}-\\d{4}"),
                "自动编号格式：JG-{yyyy}-{4位}，实际=" + created.getArchiveNo());
        assertEquals(DigitizeStatus.defaultValue(), created.getDigitizeStatus(), "默认状态必须是「未数字化」");
        assertEquals(Integer.valueOf(0), created.getDigitizePercent());
    }

    // ==================================================================
    // 四、关联扫描件
    // ==================================================================

    @Test
    public void linkAndUnlinkArchive() {
        // 从真实档案里挑一个可关联的（库里已有档案数据；没有就跳过，不让测试因环境缺数据而红）
        List<RelatedArchiveVO> candidates = completionService.queryArchivesForPick(null, null, null, 5);
        if (candidates == null || candidates.isEmpty()) {
            System.out.println("[跳过] 库里没有档案数据，无法验证关联扫描件");
            return;
        }
        RelatedArchiveVO archive = candidates.get(0);

        CompletionSaveDTO dto = new CompletionSaveDTO();
        dto.setArchiveNo(TEST_ARCHIVE_NO_PREFIX + "2001");
        dto.setProjectName("集成测试历史项目-关联扫描件（可删）");
        // 用该档案的配套项目/宗地信息建账，这样「关联扫描件」列表里能查到它
        dto.setFacilityId(archive.getFacilityId());
        dto.setCrzdbh(archive.getCrzdbh());
        String id = completionService.createArchive(dto);

        // 关联
        completionService.linkArchive(id, archive.getId());
        CompletionArchive linked = completionService.queryDetail(id);
        assertEquals(archive.getId(), linked.getArchiveId());
        assertNotNull(linked.getArchiveCount(), "关联档案数应被重算");
        assertTrue(linked.getArchiveCount() >= 1);
        // 详情里的关联扫描件列表必须能查到它
        List<String> archiveIds = linked.getRelatedArchives().stream()
                .map(RelatedArchiveVO::getId).collect(Collectors.toList());
        assertTrue(archiveIds.contains(archive.getId()), "关联扫描件列表里必须有刚关联的那份");

        // 关联不存在的档案必须报错
        try {
            completionService.linkArchive(id, "not-exist-archive-id");
            fail("关联不存在的档案必须被拒绝");
        } catch (JeecgBootException e) {
            assertTrue(e.getMessage().contains("档案不存在"));
        }

        // 取消关联：archive_id 必须真的被写成 NULL
        completionService.unlinkArchive(id);
        assertNull(completionService.queryDetail(id).getArchiveId(), "取消关联后 archive_id 必须为 NULL");
    }

    // ==================================================================
    // 五、导出（需求原文：可快速导出档案信息）
    // ==================================================================

    @Test
    public void exportProducesRealXlsxWithAllColumns() throws Exception {
        createTestArchive("3001", "导出测试项目-A");
        createTestArchive("3002", "导出测试项目-B");

        CompletionQueryDTO query = new CompletionQueryDTO();
        query.setArchiveNo(TEST_ARCHIVE_NO_PREFIX);
        long expected = completionService.queryPage(query).getTotal();
        assertEquals(2L, expected);

        Workbook workbook = exportService.exportArchive(query);
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
        Sheet sheet = reopened.getSheetAt(0);
        // ★ easypoi 的 ExportParams(title, sheetName) 会先写一行「大标题」，再写表头，
        //   所以：第 1 行（下标 0）是标题、第 2 行（下标 1）是表头、数据从下标 2 开始。
        assertEquals("竣工验收项目历史工程资料数字化档案",
                sheet.getRow(0).getCell(0).getStringCellValue());
        // 最后一行下标 = 1 标题行 + N 数据行
        assertEquals((int) expected + 1, sheet.getLastRowNum(), "导出行数应等于筛选结果数 + 1 行标题");
        org.apache.poi.ss.usermodel.Row header = sheet.getRow(1);
        // 30 列 = 10 项目与参建 + 3 时间 + 2 投资保管 + 6 数字化 + 5 扫描件与关联 + 4 备注留痕
        assertEquals(30, header.getLastCellNum(), "导出列数应为 30");
        // 数字化相关的关键列必须都在（需求重点是「数字化状态追踪」）
        List<String> headers = new java.util.ArrayList<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            headers.add(header.getCell(i).getStringCellValue());
        }
        assertTrue(headers.contains("数字化状态"), "导出表头必须含「数字化状态」：" + headers);
        assertTrue(headers.contains("数字化完成日期"));
        assertTrue(headers.contains("数字化加工单位"));
        assertTrue(headers.contains("总页数"));
        assertTrue(headers.contains("扫描分辨率(DPI)"));
        reopened.close();
    }

    // ==================================================================
    // 六、★ 空表基线（「本次不做数据迁移」的可执行版本）
    // ==================================================================

    /**
     * 本模块<b>没有数据迁移</b>：旧系统不存在这个功能，也没有对应数据可迁，
     * 建表后表里应当<b>一行都没有</b>，全部数据由页面录入。
     *
     * <p><b>这个方法会在开始录入历史档案后失败，这是设计如此</b>：
     * 它是「未做迁移」这一结论的可执行版本 ——
     * 数字变了就说明库里已经有数据了，要么是正常录入
     * （那就把期望值改成当时的真实条数，并同步更新模块交付说明/同步记录），
     * 要么是有人往空表里灌了不该有的数据（那就该查）。
     *
     * <p>注意：本方法在**独立事务**里执行，看不到其它测试方法造的行
     * （那些行既会被回滚，也会被 {@link #cleanCompletionTestRows()} 物理清理）。
     */
    @Test
    public void noMigrationBaselineSnapshot() {
        CompletionStatVO stat = completionService.queryStat(new CompletionQueryDTO());
        assertEquals(0L, stat.getTotal(),
                "竣工验收历史档案表应起始为空（本模块明确不做数据迁移）；"
                        + "若已开始录入，请把本断言改成当时的真实条数并更新交付说明");
        assertEquals(0L, stat.getArchivedCount());
        assertEquals(0L, stat.getDigitizedCount());
        assertEquals(0L, stat.getPageTotal());
        assertEquals(0L, stat.getFileTotal());
        assertEquals(0, BigDecimal.ZERO.compareTo(stat.getInvestTotal()), "空表的投资额合计应为 0");

        // 三个状态即使都为 0 也必须返回（前端 tab 不必兜底）
        assertEquals(3, stat.getByStatus().size());
        assertEquals(3, completionService.countByStatus(new CompletionQueryDTO()).size());
    }

    // ==================================================================
    // 七、Mapper 语句直测（16 条语句，确保 XML 全部可执行）
    // ==================================================================

    @Test
    public void allMapperStatementsExecute() {
        CompletionQueryDTO query = new CompletionQueryDTO();
        assertNotNull(completionMapper.selectCompletionPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 5), query));
        assertNotNull(completionMapper.selectForExport(query));
        assertNotNull(completionMapper.selectSummary(query));
        assertNotNull(completionMapper.selectCountGroupByStatus(query));
        assertNotNull(completionMapper.selectStatByColumn(query, "xzqh", "未填写"));
        assertNotNull(completionMapper.selectStatByColumn(query, "project_type", "未填写"));
        assertNotNull(completionMapper.selectStatByColumn(query, "retention", "未填写"));
        assertNotNull(completionMapper.selectStatByYear(query));
        assertNotNull(completionMapper.selectStatByCompleteYear(query));
        assertEquals(0, completionMapper.countByArchiveNo("JG-NOT-EXIST-9999", null));
        // ★ 本模块**不做数据迁移**，表初始为空 → 空表上 MAX() 必然返回 NULL，这里只要求「语句可执行」。
        //   台账/移交模块有迁移数据，所以那边可以断言非空；照抄过来会变成一条假红（实测踩到）。
        completionMapper.selectMaxSeqOfYear("JG-2026-");
        assertNotNull(completionMapper.selectRelatedArchives(null, null, 5));
        assertEquals(0, completionMapper.countRelatedArchives(null, null));
        assertNotNull(completionMapper.selectArchivesForPick(null, null, null, 5));
        assertEquals(0, completionMapper.countArchiveById("not-exist"));
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 造一条测试档案（带测试前缀，方法内即用即弃） */
    private void createTestArchive(String suffix, String projectName) {
        createTestArchiveWithNo(TEST_ARCHIVE_NO_PREFIX + suffix, projectName);
    }

    /** 造一条测试档案（指定编号），并登记 id 以便事务外兜底清理 */
    private String createTestArchiveWithNo(String archiveNo, String projectName) {
        CompletionSaveDTO dto = new CompletionSaveDTO();
        dto.setArchiveNo(archiveNo);
        dto.setProjectName(projectName);
        dto.setXzqh("西青区");
        dto.setProjectType("道路工程");
        dto.setCompleteDate(new Date());
        dto.setPageCount(100);
        dto.setFileCount(10);
        dto.setScanDpi(300);
        String id = completionService.createArchive(dto);
        createdIds.add(id);
        return id;
    }
}
