package org.jeecg.modules.land.data;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.dto.FacilitySaveDTO;
import org.jeecg.modules.land.data.dto.LandAdminQueryDTO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.process.entity.FacilityProcess;
import org.jeecg.modules.land.data.process.entity.ProcessConfig;
import org.jeecg.modules.land.data.process.enums.ProcessStatus;
import org.jeecg.modules.land.data.process.mapper.FacilityProcessMapper;
import org.jeecg.modules.land.data.process.mapper.ProcessConfigMapper;
import org.jeecg.modules.land.data.process.support.WorkdayCalculator;
import org.jeecg.modules.land.data.service.IDataRecycleService;
import org.jeecg.modules.land.data.service.IFacilityAdminService;
import org.jeecg.modules.land.data.service.ILandAdminService;
import org.jeecg.modules.land.data.support.DataSupport;
import org.jeecg.modules.land.data.vo.FacilityProcessTreeVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Description: 数据管理 · 经营性用地录入 + 配套地块录入（29 环节）+ 数据更新与移除 集成测试
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>覆盖方案 2.3.1（三）第 1、2、6 三项功能的<b>真实库</b>集成测试。
 * 测试方法跑在事务里、结束自动回滚，并用前缀 {@code TJ-TEST-DATA-} 兜底物理清理。
 *
 * <p><b>★ 这里最要紧的几条断言（都是「错了很难发现」的类型）</b>：
 * <ul>
 *   <li>{@link #stageRollUpTreatsEmptyStageAsNotStarted()} —— 旧代码在「一个事项都没录」时
 *       把阶段算成「进行中」，本实现必须算「未开启」；</li>
 *   <li>{@link #updateOnlyRecordsActuallyChangedFields()} —— 变更履历只记真变了的字段，
 *       否则一次编辑会产生 30 多条「未变化」的假履历；</li>
 *   <li>{@link #restoreBlocksWhenCrzdbhOccupied()} —— 恢复被删宗地时若不检测唯一键冲突，
 *       用户看到的是 {@code Duplicate entry} 这种数据库异常；</li>
 *   <li>{@link #facilityMustAttachToExistingLand()} —— 旧系统不校验宗地存在性，
 *       设计文档实测产生了 60 行「孤儿配套」。</li>
 * </ul>
 */
public class DataManagementTest extends LandIntegrationTestBase {

    /** 测试数据专用前缀（业务上不会出现，兜底清理按它识别） */
    private static final String TEST_PREFIX = "TJ-TEST-DATA-";

    @Autowired
    private ILandAdminService landAdminService;

    @Autowired
    private IFacilityAdminService facilityAdminService;

    @Autowired
    private IDataRecycleService recycleService;

    @Autowired
    private ProcessConfigMapper processConfigMapper;

    @Autowired
    private FacilityProcessMapper facilityProcessMapper;

    @Autowired
    private WorkdayCalculator workdayCalculator;

    // ==================================================================
    // 一、29 环节配置（数据迁移正确性）
    // ==================================================================

    @Test
    public void processConfigHasSixStages() {
        List<ProcessConfig> stages = processConfigMapper.selectStages();
        assertEquals(6, stages.size(), "六大阶段必须齐备（旧库 process_configuration 有 6 个父节点）");
        for (ProcessConfig stage : stages) {
            assertTrue(stage.isStage(), "阶段行的 parent_id 必须是 NULL（空串已在迁移时归一化）");
            assertNotNull(stage.getProcessTime(), "阶段必须有标准办理时长：" + stage.getName());
            assertTrue(stage.getProcessTime() > 0,
                    "标准办理时长必须为正数（旧库「项目竣工」是 0，会让预警除零）：" + stage.getName());
        }
        // 六大阶段名称抽查（与文档 §2.14 逐字一致）
        List<String> names = new ArrayList<>();
        for (ProcessConfig stage : stages) {
            names.add(stage.getName());
        }
        assertTrue(names.contains("立项用地规划许可阶段"), "阶段名称应与旧库一致：" + names);
        assertTrue(names.contains("竣工移交许可阶段"), "阶段名称应与旧库一致：" + names);
    }

    @Test
    public void processConfigHas24ItemsAndCorrectedTypo() {
        List<ProcessConfig> items = processConfigMapper.selectItems();
        assertEquals(24, items.size(), "事项节点应为 24 个（6 阶段 + 24 事项 = 旧库 30 行）");
        boolean hasCorrected = false;
        boolean hasTypo = false;
        for (ProcessConfig item : items) {
            assertFalse(item.isStage(), "事项行的 parent_id 必须非空：" + item.getName());
            assertNotNull(item.getParentId(), "事项必须挂在某个阶段下：" + item.getName());
            if ("中水工程移交".equals(item.getName())) {
                hasCorrected = true;
            }
            if ("中水工程移市".equals(item.getName())) {
                hasTypo = true;
            }
        }
        assertTrue(hasCorrected, "迁移时应把旧库错别字「中水工程移市」修正为「中水工程移交」");
        assertFalse(hasTypo, "迁移后不应再出现错别字");
    }

    // ==================================================================
    // 二、阶段汇总口径（★ 修掉了旧代码的漏洞）
    // ==================================================================

    @Test
    public void stageRollUpTreatsEmptyStageAsNotStarted() {
        // ★ 旧代码的漏洞：四个分支（全不涉及/全未开启/有进行中/全已完成）
        //   在子集为空时全不成立，于是落到默认的「进行中」——
        //   一个事项都没录，阶段却显示「进行中」。
        assertEquals("未开启", ProcessStatus.rollUpStageStatus(null),
                "★ 没有子事项时阶段必须是「未开启」，不能是「进行中」");
        assertEquals("未开启", ProcessStatus.rollUpStageStatus(new ArrayList<String>()),
                "★ 空列表同理");
    }

    @Test
    public void stageRollUpRules() {
        // 全不涉及 → 不涉及
        assertEquals("不涉及", ProcessStatus.rollUpStageStatus(Arrays.asList("不涉及", "不涉及")));
        // 全已完成 → 已完成
        assertEquals("已完成", ProcessStatus.rollUpStageStatus(Arrays.asList("已完成", "已完成")));
        // 全未开启 → 未开启
        assertEquals("未开启", ProcessStatus.rollUpStageStatus(Arrays.asList("未开启", "未开启")));
        // 有进行中 → 进行中
        assertEquals("进行中", ProcessStatus.rollUpStageStatus(Arrays.asList("已完成", "进行中")));
        // 未开启 + 已完成（没有进行中）→ 进行中（阶段已经开始动了）
        assertEquals("进行中", ProcessStatus.rollUpStageStatus(Arrays.asList("未开启", "已完成")));
        // 不涉及混在里面：剔除后再判 → 2 个有效全已完成 = 已完成
        assertEquals("已完成", ProcessStatus.rollUpStageStatus(Arrays.asList("已完成", "不涉及", "已完成")));
        // 脏数据（null / 未知取值）按「未开启」处理，不能因为一条脏值把阶段判成进行中
        assertEquals("未开启", ProcessStatus.rollUpStageStatus(Arrays.asList(null, "什么都没填")));
    }

    @Test
    public void stagePercentExcludesNotInvolved() {
        // 3 个事项：2 个不涉及 + 1 个已完成 → 完成度应是 100%（有效项全完成）
        assertEquals(100, ProcessStatus.stagePercent(Arrays.asList("已完成", "不涉及", "不涉及")),
                "「不涉及」必须从分母里剔除，否则完成度永远上不去");
        assertEquals(50, ProcessStatus.stagePercent(Arrays.asList("已完成", "未开启")),
                "2 个有效项完成 1 个 = 50%");
        assertEquals(0, ProcessStatus.stagePercent(new ArrayList<String>()),
                "没有事项时完成度为 0");
        assertEquals(100, ProcessStatus.stagePercent(Arrays.asList("不涉及")),
                "全部不涉及视为该阶段已了结，给 100% 而不是 0%");
    }

    @Test
    public void processStatusValuesMatchDictionaryContract() {
        // 代码枚举的取值必须与文档/字典口径逐字一致
        assertEquals(Arrays.asList("未开启", "进行中", "已完成", "不涉及"), ProcessStatus.ordered());
        assertTrue(ProcessStatus.isValid("进行中"));
        assertFalse(ProcessStatus.isValid("已完工"), "旧库 kgzt 里混入的「已完工」不是环节情况取值");
        assertEquals("未开启", ProcessStatus.defaultValue());
    }

    // ==================================================================
    // 三、工作日推算（★ 重写了旧算法）
    // ==================================================================

    @Test
    public void workdayCalculationSkipsWeekendsAndHolidays() {
        // 2026-10-01 是国庆长假首日（走内置法定节假日兜底；实测表里只有 2023 年数据）
        Map<String, Object> result = workdayCalculator.plusWorkdays("2026-10-01", 1);
        assertNotNull(result.get("endDate"));
        String endDate = String.valueOf(result.get("endDate"));
        // 从 10-01 往后推 1 个工作日：应跳过 10-01~10-08 的长假与周末，落到 10-09（周五）
        assertEquals("2026-10-09", endDate,
                "国庆长假后第 1 个工作日应是 10-09；旧递归算法在长假跨区间时会算偏");

        // ★ calendarBased 的语义是「本次推算命中了该年份的人工维护日历」。
        //   实测 t_non_working_day 只有 2023 年数据，所以 2026 年应为 false，
        //   前端据此提示「该年份未维护日历，结果按周末 + 内置法定节假日估算」。
        //   （初版实现这里写反了：表里没数据反而标记成「基于日历」，
        //     等于把最该出现的风险提示盖掉。）
        boolean yearMaintained = workdayCalculator.maintainedYears().contains(2026);
        assertEquals(yearMaintained, Boolean.TRUE.equals(result.get("calendarBased")),
                "calendarBased 必须等于「该年份是否有维护数据」，不能反：" + result);

        // 跨周末：2026-10-09 是周五，推 1 个工作日应落到下周一 10-12
        assertEquals("2026-10-12", String.valueOf(workdayCalculator.plusWorkdays("2026-10-09", 1).get("endDate")),
                "周五往后 1 个工作日是下周一（跳过周六日）");

        // 天数为 0 或负数：返回开始日当天，不往后走
        assertEquals("2026-10-09", String.valueOf(workdayCalculator.plusWorkdays("2026-10-09", 0).get("endDate")),
                "标准时长为 0 时应返回开始日当天");
    }

    @Test
    public void workdayCalculationRejectsBadDate() {
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> workdayCalculator.plusWorkdays("去年十月", 5));
        assertTrue(error.getMessage().contains("日期"), "错误提示应说明日期格式问题：" + error.getMessage());
    }

    // ==================================================================
    // 四、经营性用地录入（第 1 项）
    // ==================================================================

    @Test
    public void createLandPersistsAllFieldsAndLogsHistory() {
        String crzdbh = uniqueNo("create");
        LandSaveDTO dto = buildLand(crzdbh, "新建地块");
        dto.setXzqh("西青区");
        dto.setGhydxz("城镇住宅、商服");
        dto.setCrj(new BigDecimal("12.35"));
        dto.setKjsydmj(new BigDecimal("45231.50"));
        dto.setCrsj(parseDate("2024-06-18"));
        dto.setLxdh("13800000000");

        String id = landAdminService.createLand(dto, null);
        assertNotNull(id, "新增应返回主键");

        Land saved = landAdminService.queryDetail(id);
        assertNotNull(saved);
        assertEquals(crzdbh, saved.getCrzdbh());
        assertEquals("新建地块", saved.getDkmc());
        assertEquals("西青区", saved.getXzqh());
        assertEquals(0, new BigDecimal("12.35").compareTo(saved.getCrj()));
        assertEquals("2024-06-18", new SimpleDateFormat("yyyy-MM-dd").format(saved.getCrsj()));
        assertEquals(0, Integer.valueOf(0).compareTo(saved.getDelFlag()), "新增记录 del_flag 必须是 0");
        assertNull(saved.getSourceId(), "source_id 是迁移溯源列，录入不该写它");

        // ★ 变更履历：新增必须留一条 CREATE，且字段级明细非空
        List<Map<String, Object>> history = recyclServiceHistory(id);
        assertFalse(history.isEmpty(), "新增应产生一条变更履历");
        Map<String, Object> first = history.get(0);
        assertEquals("CREATE", first.get("action"));
        assertTrue(((Number) first.get("changeCount")).intValue() > 0,
                "CREATE 履历应带上字段级明细");
    }

    @Test
    public void createLandRejectsDuplicateCrzdbh() {
        String crzdbh = uniqueNo("dup");
        landAdminService.createLand(buildLand(crzdbh, "第一块"), null);

        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> landAdminService.createLand(buildLand(crzdbh, "第二块"), null));
        assertTrue(error.getMessage().contains("已存在"), "重复编号的错误提示：" + error.getMessage());
        assertTrue(error.getMessage().contains(crzdbh), "提示里应带上冲突的编号");
    }

    @Test
    public void createLandValidatesRequiredAndFormats() {
        // 编号为空
        LandSaveDTO noCode = buildLand("", "无编号");
        JeecgBootException e1 = assertThrows(JeecgBootException.class,
                () -> landAdminService.createLand(noCode, null));
        assertTrue(e1.getMessage().contains("出让宗地编号"), e1.getMessage());

        // 项目分类非法
        LandSaveDTO badXmfl = buildLand(uniqueNo("badxmfl"), "分类错");
        badXmfl.setXmfl("国家级项目");
        JeecgBootException e2 = assertThrows(JeecgBootException.class,
                () -> landAdminService.createLand(badXmfl, null));
        assertTrue(e2.getMessage().contains("市级项目"), "应提示允许的两个取值：" + e2.getMessage());

        // 联系电话格式错
        LandSaveDTO badPhone = buildLand(uniqueNo("badphone"), "电话错");
        badPhone.setLxdh("12345");
        JeecgBootException e3 = assertThrows(JeecgBootException.class,
                () -> landAdminService.createLand(badPhone, null));
        assertTrue(e3.getMessage().contains("手机号"), "手机号错误提示：" + e3.getMessage());
    }

    @Test
    public void checkCrzdbhReportsOccupied() {
        String crzdbh = uniqueNo("check");
        String id = landAdminService.createLand(buildLand(crzdbh, "占位块"), null);

        Map<String, Object> available = landAdminService.checkCrzdbh(crzdbh, null);
        assertEquals(Boolean.FALSE, available.get("available"), "已存在的编号应报告不可用");
        assertEquals(id, available.get("occupiedBy"), "应指出被哪条记录占用");
        assertTrue(String.valueOf(available.get("message")).contains("占用"),
                "提示文案：" + available.get("message"));

        // 编辑自身时应放行
        Map<String, Object> selfCheck = landAdminService.checkCrzdbh(crzdbh, id);
        assertEquals(Boolean.TRUE, selfCheck.get("available"), "编辑自身时该编号应视为可用");

        // 空编号
        assertEquals(Boolean.FALSE, landAdminService.checkCrzdbh("  ", null).get("available"));
    }

    // ==================================================================
    // 五、编辑与变更留痕（★ 只记真正变化的字段）
    // ==================================================================

    @Test
    public void updateOnlyRecordsActuallyChangedFields() {
        String crzdbh = uniqueNo("update");
        String id = landAdminService.createLand(buildLand(crzdbh, "原始名称"), null);

        Land current = landAdminService.queryDetail(id);
        LandSaveDTO dto = toDto(current);
        // 只改两个字段：地块名称与出让金
        dto.setDkmc("改名后");
        dto.setCrj(new BigDecimal("2.00"));

        landAdminService.updateLand(dto, null);

        Land after = landAdminService.queryDetail(id);
        assertEquals("改名后", after.getDkmc());
        assertEquals(0, new BigDecimal("2.00").compareTo(after.getCrj()));

        // ★ 履历里本次 UPDATE 只应包含这 2 个字段 ——
        //   这就是「只记真变了」的价值：否则一次编辑会记 30 多条「未变化」
        Map<String, Object> updateLog = firstLogOfAction(id, "UPDATE");
        assertNotNull(updateLog, "编辑应产生一条 UPDATE 履历");
        assertEquals(2, ((Number) updateLog.get("changeCount")).intValue(),
                "只改了 2 个字段，履历里不应出现其它字段（审计与溯源列必须跳过）："
                        + updateLog.get("details"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) updateLog.get("details");
        assertNotNull(details);
        List<String> fields = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Map<String, Object> detail : details) {
            fields.add(String.valueOf(detail.get("field")));
            labels.add(String.valueOf(detail.get("label")));
        }
        assertTrue(fields.contains("dkmc"), "应记录 dkmc：" + fields);
        assertTrue(fields.contains("crj"), "应记录 crj：" + fields);
        assertTrue(labels.contains("地块名称"), "中文名应来自 DataFieldLabels：" + labels);
        assertTrue(labels.contains("出让金（亿元）"), "中文名应来自 DataFieldLabels：" + labels);
        // 改前改后都要记
        for (Map<String, Object> detail : details) {
            if ("crj".equals(detail.get("field"))) {
                assertEquals("1.00", String.valueOf(detail.get("before")));
                assertEquals("2.00", String.valueOf(detail.get("after")));
            }
        }
    }

    @Test
    public void updateWithoutRealChangeProducesNoHistory() {
        String crzdbh = uniqueNo("nochange");
        String id = landAdminService.createLand(buildLand(crzdbh, "原样"), null);
        int before = recyclServiceHistory(id).size();

        // 原样提交一遍（前端表单原封不动保存是常见操作）
        Land current = landAdminService.queryDetail(id);
        landAdminService.updateLand(toDto(current), null);

        int after = recyclServiceHistory(id).size();
        assertEquals(before, after,
                "★ 没有任何字段变化时不该产生履历 —— 否则用户点一次保存就多一条噪音");
    }

    @Test
    public void updateRejectsCrzdbhTakenByAnother() {
        String first = uniqueNo("taken-a");
        String second = uniqueNo("taken-b");
        String firstId = landAdminService.createLand(buildLand(first, "甲"), null);
        landAdminService.createLand(buildLand(second, "乙"), null);

        Land current = landAdminService.queryDetail(firstId);
        LandSaveDTO dto = toDto(current);
        dto.setCrzdbh(second);   // 想把甲的编号改成乙的
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> landAdminService.updateLand(dto, null));
        assertTrue(error.getMessage().contains("占用"), error.getMessage());
    }

    // ==================================================================
    // 六、配套地块录入（第 2 项）
    // ==================================================================

    @Test
    public void facilityMustAttachToExistingLand() {
        // ★ 旧系统只校验 crzdbh 非空，不校验宗地是否存在，
        //   设计文档实测产生了 60 行「孤儿配套」
        FacilitySaveDTO dto = buildFacility(uniqueNo("orphan-land"), "孤儿配套");
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> facilityAdminService.createFacility(dto, null));
        assertTrue(error.getMessage().contains("不存在"),
                "★ 宗地不存在时必须拒绝并说明：" + error.getMessage());
    }

    @Test
    public void oneLandHasManyFacilities() {
        String crzdbh = uniqueNo("1n");
        landAdminService.createLand(buildLand(crzdbh, "一块地"), null);

        facilityAdminService.createFacility(buildFacility(crzdbh, "配套甲"), null);
        facilityAdminService.createFacility(buildFacility(crzdbh, "配套乙"), null);
        facilityAdminService.createFacility(buildFacility(crzdbh, "配套丙"), null);

        List<Map<String, Object>> byLand = facilityAdminService.queryByLand(crzdbh);
        assertEquals(3, byLand.size(), "1 宗地应能挂 N 个配套项目");
        for (Map<String, Object> item : byLand) {
            assertNotNull(item.get("processStat"), "每个配套应带上环节进度汇总");
            assertNotNull(item.get("stageStatus"), "每个配套应带上六大阶段状态");
        }
    }

    @Test
    public void facilityNameUniqueWithinLandButAllowedAcrossLands() {
        String landA = uniqueNo("name-a");
        String landB = uniqueNo("name-b");
        landAdminService.createLand(buildLand(landA, "甲地"), null);
        landAdminService.createLand(buildLand(landB, "乙地"), null);

        facilityAdminService.createFacility(buildFacility(landA, "同名配套"), null);
        // 同一宗地下重复 → 拒绝
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> facilityAdminService.createFacility(buildFacility(landA, "同名配套"), null));
        assertTrue(error.getMessage().contains("已存在"), error.getMessage());
        // 不同宗地下同名 → 允许（业务上不同宗地各有一条同名道路是正常的）
        facilityAdminService.createFacility(buildFacility(landB, "同名配套"), null);
        assertEquals(1, facilityAdminService.queryByLand(landB).size());
    }

    @Test
    public void facilityInheritsLandInfoWhenBlank() {
        String crzdbh = uniqueNo("inherit");
        LandSaveDTO land = buildLand(crzdbh, "带区划的地");
        land.setXzqh("武清区");
        land.setXmfl("市级项目");
        landAdminService.createLand(land, null);

        FacilitySaveDTO dto = buildFacility(crzdbh, "继承配套");
        dto.setDkmc(null);
        dto.setXzqh(null);
        dto.setXmfl(null);
        String id = facilityAdminService.createFacility(dto, null);

        Facility saved = facilityAdminService.queryDetail(id);
        assertEquals("带区划的地", saved.getDkmc(), "地块名称留空应继承宗地的");
        assertEquals("武清区", saved.getXzqh(), "行政区划留空应继承宗地的");
        assertEquals("市级项目", saved.getXmfl(), "项目分类留空应继承宗地的");
    }

    @Test
    public void facilitySoftDeleteKeepsRowAndIsRestorable() {
        String crzdbh = uniqueNo("softdel");
        landAdminService.createLand(buildLand(crzdbh, "软删测试地"), null);
        String facilityId = facilityAdminService.createFacility(buildFacility(crzdbh, "待删配套"), null);

        facilityAdminService.removeFacility(facilityId, "测试移除", null);
        // 列表里查不到
        assertNull(facilityAdminService.queryDetail(facilityId), "移除后详情应查不到");
        assertEquals(0, facilityAdminService.queryByLand(crzdbh).size(), "移除后按宗地查也应查不到");

        // ★ 但回收站里必须在（软删，不是物理删）
        List<Map<String, Object>> recycle = recycleService.queryRecycleList("facility", null);
        boolean found = false;
        for (Map<String, Object> row : recycle) {
            if (facilityId.equals(row.get("id"))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "★ 移除必须是软删：回收站里应能查到");

        // 恢复
        Map<String, Object> restored = recycleService.restore("facility", facilityId, null);
        assertEquals(Boolean.TRUE, restored.get("success"));
        assertNotNull(facilityAdminService.queryDetail(facilityId), "恢复后应能查到");
    }

    // ==================================================================
    // 七、29 环节进度录入
    // ==================================================================

    @Test
    public void processTreeReturnsAllItemsIncludingUnfilled() {
        String crzdbh = uniqueNo("tree");
        landAdminService.createLand(buildLand(crzdbh, "环节树测试地"), null);
        String facilityId = facilityAdminService.createFacility(buildFacility(crzdbh, "环节树配套"), null);

        FacilityProcessTreeVO tree = facilityAdminService.queryProcessTree(facilityId);
        assertEquals(6, tree.getStages().size(), "应返回六大阶段");
        assertEquals(24, tree.getTotalItems(), "应返回 24 个事项（未录入的也要返回）");
        assertEquals(0, tree.getFilledItems(), "还没录入任何环节");
        assertEquals(0, tree.getPercent(), "完成度应为 0");
        for (FacilityProcessTreeVO.StageNode stage : tree.getStages()) {
            assertEquals("未开启", stage.getStatus(),
                    "★ 一个环节都没录时阶段必须是「未开启」：" + stage.getStageName());
            assertEquals(0, stage.getFilledCount());
            assertFalse(stage.getItems().isEmpty(), "每个阶段都应带回事项");
            for (FacilityProcessTreeVO.ItemNode item : stage.getItems()) {
                assertEquals(Boolean.FALSE, item.getFilled(), "未录入的事项 filled 应为 false");
                assertEquals("未开启", item.getLcqk(), "未录入的事项环节情况应为「未开启」");
            }
        }
    }

    @Test
    public void saveProcessCreatesThenUpdatesAndRollsUpStage() {
        String crzdbh = uniqueNo("proc");
        landAdminService.createLand(buildLand(crzdbh, "环节录入地"), null);
        String facilityId = facilityAdminService.createFacility(buildFacility(crzdbh, "环节录入配套"), null);

        // 取「立项用地规划许可阶段」下的第一个事项
        ProcessConfig stage = processConfigMapper.selectStages().get(0);
        List<ProcessConfig> items = processConfigMapper.selectItemsByStage(stage.getId());
        assertFalse(items.isEmpty(), "阶段下应有事项");
        ProcessConfig item = items.get(0);

        // ① 第一次保存 → 新增
        Map<String, Object> payload = new HashMap<>();
        payload.put("lcqk", "进行中");
        payload.put("lckssj", "2026-03-02");
        payload.put("jtwt", "管线迁改协调中");
        payload.put("lrr", "测试录入人");
        String processId = facilityAdminService.saveProcess(facilityId, item.getId(), payload, null);
        assertNotNull(processId, "保存应返回进度记录主键");

        FacilityProcess saved = facilityProcessMapper.selectById(processId);
        assertNotNull(saved);
        assertEquals("进行中", saved.getLcqk());
        assertEquals(facilityId, saved.getPtId());
        assertEquals(stage.getId(), saved.getStageId(), "所属阶段应自动冗余");
        assertEquals(crzdbh, saved.getCrzdbh(), "宗地编号应自动冗余");
        assertNotNull(saved.getYjjssj(), "★ 未填预计结束时间时应按工作日自动推算");
        // 2026-03-02 是周一，推 10 个工作日（该环节标准时长）→ 03-16（跳过周末）
        assertEquals("2026-03-16", new SimpleDateFormat("yyyy-MM-dd").format(saved.getYjjssj()),
                "预计结束时间应按环节标准时长（工作日）推算");

        // ② 阶段汇总应随之变化（该阶段 4 个事项里有 1 个进行中 → 进行中）
        FacilityProcessTreeVO tree = facilityAdminService.queryProcessTree(facilityId);
        FacilityProcessTreeVO.StageNode stageNode = tree.getStages().get(0);
        assertEquals("进行中", stageNode.getStatus(),
                "阶段下有 1 个进行中 → 阶段应为进行中：" + stageNode.getStageName());
        assertEquals(1, stageNode.getFilledCount());
        assertEquals(1, tree.getFilledItems());

        // ③ 第二次保存同一环节 → 更新（不新增），唯一键 (pt_id, lc_id) 保证只有一行
        payload.put("lcqk", "已完成");
        payload.put("lcjssj", "2026-03-20");
        String sameId = facilityAdminService.saveProcess(facilityId, item.getId(), payload, null);
        assertEquals(processId, sameId, "★ 同一环节再次保存必须是更新，不能新增一行");
        List<FacilityProcess> rows = facilityProcessMapper.selectByFacility(facilityId);
        assertEquals(1, rows.size(), "同一配套的同一环节只能有一条进度记录");
        assertEquals("已完成", facilityProcessMapper.selectById(processId).getLcqk());
    }

    @Test
    public void saveProcessRejectsCompletedWithoutEndDate() {
        String crzdbh = uniqueNo("procreq");
        landAdminService.createLand(buildLand(crzdbh, "校验地"), null);
        String facilityId = facilityAdminService.createFacility(buildFacility(crzdbh, "校验配套"), null);
        ProcessConfig item = processConfigMapper.selectItems().get(0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("lcqk", "已完成");
        payload.put("lckssj", "2026-03-02");
        // 故意不填 lcjssj
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> facilityAdminService.saveProcess(facilityId, item.getId(), payload, null));
        assertTrue(error.getMessage().contains("结束时间"),
                "★ 状态与时间必须自洽（旧系统完全不校验，库里存在「已完成但没结束时间」）："
                        + error.getMessage());
    }

    @Test
    public void saveProcessRejectsEndBeforeStart() {
        String crzdbh = uniqueNo("procorder");
        landAdminService.createLand(buildLand(crzdbh, "顺序地"), null);
        String facilityId = facilityAdminService.createFacility(buildFacility(crzdbh, "顺序配套"), null);
        ProcessConfig item = processConfigMapper.selectItems().get(0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("lcqk", "已完成");
        payload.put("lckssj", "2026-03-20");
        payload.put("lcjssj", "2026-03-02");   // 早于开始
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> facilityAdminService.saveProcess(facilityId, item.getId(), payload, null));
        assertTrue(error.getMessage().contains("不能早于"), error.getMessage());
    }

    @Test
    public void saveProcessRejectsIllegalStatusAndStageNode() {
        String crzdbh = uniqueNo("procbad");
        landAdminService.createLand(buildLand(crzdbh, "非法地"), null);
        String facilityId = facilityAdminService.createFacility(buildFacility(crzdbh, "非法配套"), null);
        ProcessConfig item = processConfigMapper.selectItems().get(0);

        Map<String, Object> bad = new HashMap<>();
        bad.put("lcqk", "已完工");   // 旧库 kgzt 里混入的取值，不是环节情况
        JeecgBootException e1 = assertThrows(JeecgBootException.class,
                () -> facilityAdminService.saveProcess(facilityId, item.getId(), bad, null));
        assertTrue(e1.getMessage().contains("不合法"), e1.getMessage());
        assertTrue(e1.getMessage().contains("未开启"), "应列出允许值：" + e1.getMessage());

        // 阶段节点不能直接录进度
        ProcessConfig stage = processConfigMapper.selectStages().get(0);
        JeecgBootException e2 = assertThrows(JeecgBootException.class,
                () -> facilityAdminService.saveProcess(facilityId, stage.getId(), bad, null));
        assertTrue(e2.getMessage().contains("阶段"), e2.getMessage());
    }

    @Test
    public void saveProcessBatchReportsPerItemFailures() {
        String crzdbh = uniqueNo("batch");
        landAdminService.createLand(buildLand(crzdbh, "批量地"), null);
        String facilityId = facilityAdminService.createFacility(buildFacility(crzdbh, "批量配套"), null);
        List<ProcessConfig> items = processConfigMapper.selectItems();
        ProcessConfig ok1 = items.get(0);
        ProcessConfig ok2 = items.get(1);
        ProcessConfig bad = items.get(2);

        List<Map<String, Object>> payload = new ArrayList<>();
        payload.add(processPayload(ok1, "进行中", "2026-03-02", null));
        payload.add(processPayload(ok2, "已完成", "2026-03-02", "2026-03-20"));
        // 第三条：已完成但缺结束时间 → 应失败，且不影响前两条
        payload.add(processPayload(bad, "已完成", "2026-03-02", null));

        Map<String, Object> result = facilityAdminService.saveProcessBatch(facilityId, payload, null);
        assertEquals(2, ((Number) result.get("successCount")).intValue(), "前两条应成功：" + result);
        assertEquals(1, ((Number) result.get("failCount")).intValue(), "第三条应失败：" + result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertEquals(bad.getId(), failures.get(0).get("lcId"), "失败明细应指出是哪个环节");
        assertEquals(2, facilityProcessMapper.selectByFacility(facilityId).size(),
                "部分失败不应影响其它环节");
    }

    @Test
    public void removeProcessReturnsItemToUnfilled() {
        String crzdbh = uniqueNo("procdel");
        landAdminService.createLand(buildLand(crzdbh, "撤回地"), null);
        String facilityId = facilityAdminService.createFacility(buildFacility(crzdbh, "撤回配套"), null);
        ProcessConfig item = processConfigMapper.selectItems().get(0);

        facilityAdminService.saveProcess(facilityId, item.getId(),
                processPayload(item, "进行中", "2026-03-02", null), null);
        assertEquals(1, facilityProcessMapper.selectByFacility(facilityId).size());

        facilityAdminService.removeProcess(facilityId, item.getId(), null);
        assertEquals(0, facilityProcessMapper.selectByFacility(facilityId).size(), "撤回后应无进度记录");

        FacilityProcessTreeVO tree = facilityAdminService.queryProcessTree(facilityId);
        assertEquals(24, tree.getTotalItems(), "撤回后事项仍在树里（只是未填报）");
        assertEquals(0, tree.getFilledItems());
    }

    @Test
    public void processListFlattens24RowsWithStageInfo() {
        String crzdbh = uniqueNo("flat");
        landAdminService.createLand(buildLand(crzdbh, "扁平地"), null);
        String facilityId = facilityAdminService.createFacility(buildFacility(crzdbh, "扁平配套"), null);

        List<Map<String, Object>> rows = facilityAdminService.queryProcessList(facilityId);
        assertEquals(24, rows.size(), "扁平列表应有 24 行（每行一个事项）");
        int index = 0;
        for (Map<String, Object> row : rows) {
            index++;
            assertEquals(index, ((Number) row.get("index")).intValue(), "序号应连续");
            assertNotNull(row.get("stageName"), "每行应带阶段名");
            assertNotNull(row.get("lcName"), "每行应带环节名");
            assertNotNull(row.get("lcqk"));
        }
    }

    // ==================================================================
    // 八、数据更新与移除（第 6 项）
    // ==================================================================

    @Test
    public void removeLandGoesToRecycleBinAndCanBeRestored() {
        String crzdbh = uniqueNo("recycle");
        String id = landAdminService.createLand(buildLand(crzdbh, "待回收地块"), null);

        landAdminService.removeLand(id, "误录", null);
        assertNull(landAdminService.queryDetail(id), "移除后列表应查不到");
        assertNull(landAdminService.checkCrzdbh(crzdbh, null).get("occupiedBy"),
                "移除后编号应被释放");

        Map<String, Object> summary = recycleService.queryRecycleSummary();
        assertTrue(((Number) summary.get("total")).longValue() >= 1, "回收站应有记录");

        List<Map<String, Object>> recycle = recycleService.queryRecycleList("land", crzdbh);
        boolean found = false;
        for (Map<String, Object> row : recycle) {
            if (id.equals(row.get("id"))) {
                found = true;
            }
        }
        assertTrue(found, "回收站里应能按编号搜到已移除的宗地");

        Map<String, Object> restored = recycleService.restore("land", id, null);
        assertEquals(Boolean.TRUE, restored.get("success"));
        assertNotNull(landAdminService.queryDetail(id), "恢复后应能查到");
        assertEquals(crzdbh, landAdminService.queryDetail(id).getCrzdbh());
    }

    @Test
    public void restoreBlocksWhenCrzdbhOccupied() {
        String crzdbh = uniqueNo("conflict");
        String removedId = landAdminService.createLand(buildLand(crzdbh, "被移除的"), null);
        landAdminService.removeLand(removedId, "先移除", null);
        // 移除后重新录入同编号（本实现允许，因为唯一键是 (crzdbh, del_flag)）
        landAdminService.createLand(buildLand(crzdbh, "重新录入的"), null);

        // ★ 此时恢复被删的那条必然撞唯一键 —— 必须在业务层拦住并给人话
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> recycleService.restore("land", removedId, null));
        assertTrue(error.getMessage().contains("已被另一条有效记录占用"),
                "★ 不能把 Duplicate entry 原样甩给用户：" + error.getMessage());
        assertFalse(error.getMessage().contains("Duplicate"),
                "错误提示里不应出现数据库原文：" + error.getMessage());
    }

    @Test
    public void restoreFacilityOnlyWarnsOnDuplicateName() {
        String crzdbh = uniqueNo("fname");
        landAdminService.createLand(buildLand(crzdbh, "配套同名地"), null);
        String removedId = facilityAdminService.createFacility(buildFacility(crzdbh, "同名配套X"), null);
        facilityAdminService.removeFacility(removedId, null, null);
        // 移除后重新录入同名配套（配套表没有唯一键，允许）
        facilityAdminService.createFacility(buildFacility(crzdbh, "同名配套X"), null);

        // ★ 配套没有唯一键，恢复不会失败 —— 只应给一句提示，不该阻断
        Map<String, Object> result = recycleService.restore("facility", removedId, null);
        assertEquals(Boolean.TRUE, result.get("success"), "配套恢复不该被阻断：" + result);
        assertNotNull(result.get("warning"), "同名时应给出提示");
        assertTrue(String.valueOf(result.get("warning")).contains("同名"),
                "提示文案应说明同名：" + result.get("warning"));
    }

    @Test
    public void removeAndRestoreAreLoggedAsHistory() {
        String crzdbh = uniqueNo("histdel");
        String id = landAdminService.createLand(buildLand(crzdbh, "留痕地"), null);
        landAdminService.removeLand(id, "测试移除原因", null);
        recycleService.restore("land", id, null);

        List<Map<String, Object>> history = recyclServiceHistory(id);
        List<String> actions = new ArrayList<>();
        for (Map<String, Object> row : history) {
            actions.add(String.valueOf(row.get("action")));
        }
        assertTrue(actions.contains("DELETE"), "移除应留痕：" + actions);
        assertTrue(actions.contains("RESTORE"), "恢复应留痕：" + actions);
        assertTrue(actions.contains("CREATE"), "新增应留痕：" + actions);

        // 移除那条的摘要里应带上原因
        boolean hasReason = false;
        for (Map<String, Object> row : history) {
            if ("DELETE".equals(row.get("action"))
                    && String.valueOf(row.get("summary")).contains("测试移除原因")) {
                hasReason = true;
            }
        }
        assertTrue(hasReason, "★ 移除原因必须写进履历（旧日志表只有一句「删除了数据」）");
    }

    @Test
    public void changeLogPageFiltersWork() {
        String crzdbh = uniqueNo("page");
        String id = landAdminService.createLand(buildLand(crzdbh, "分页地"), null);
        Land current = landAdminService.queryDetail(id);
        LandSaveDTO dto = toDto(current);
        dto.setDkmc("分页改名");
        landAdminService.updateLand(dto, null);

        Map<String, Object> page = recycleService.queryChangeLogPage(
                DataSupport.BIZ_LAND, "UPDATE", null, crzdbh, 1, 50);
        assertTrue(((Number) page.get("total")).longValue() >= 1, "按 bizType+action+关键字应能筛到：" + page);
        @SuppressWarnings("unchecked")
        List<Object> records = (List<Object>) page.get("records");
        assertFalse(records.isEmpty());

        // 动作下拉应覆盖全部动作
        List<Map<String, String>> options = recycleService.queryActionOptions();
        assertTrue(options.size() >= 6, "动作下拉应包含 6 个以上动作：" + options.size());
        List<String> values = new ArrayList<>();
        for (Map<String, String> option : options) {
            values.add(option.get("value"));
        }
        assertTrue(values.contains("CREATE") && values.contains("DELETE") && values.contains("RESTORE"),
                "动作下拉应含 CREATE/DELETE/RESTORE：" + values);
    }

    @Test
    public void batchRemoveReportsPerRowFailures() {
        String crzdbh = uniqueNo("batchdel");
        String id = landAdminService.createLand(buildLand(crzdbh, "批量移除地"), null);
        List<String> ids = Arrays.asList(id, "not-exist-id-12345");

        Map<String, Object> result = landAdminService.removeLandBatch(ids, "批量测试", null);
        assertEquals(1, ((Number) result.get("successCount")).intValue(), "存在的那条应成功：" + result);
        assertEquals(1, ((Number) result.get("failCount")).intValue(), "不存在的应计入失败：" + result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertEquals("not-exist-id-12345", failures.get(0).get("id"));
    }

    // ==================================================================
    // 九、查询条件
    // ==================================================================

    @Test
    public void landPageSupportsFacilityExistenceFilter() {
        String withFacility = uniqueNo("hasf");
        String withoutFacility = uniqueNo("nof");
        landAdminService.createLand(buildLand(withFacility, "有配套地"), null);
        landAdminService.createLand(buildLand(withoutFacility, "无配套地"), null);
        facilityAdminService.createFacility(buildFacility(withFacility, "某配套"), null);

        LandAdminQueryDTO query = new LandAdminQueryDTO();
        query.setCrzdbh(TEST_PREFIX);
        query.setPageSize(100);

        query.setHasFacility(Boolean.TRUE);
        IPage<Land> hasOne = landAdminService.queryPage(query);
        List<String> hasCodes = new ArrayList<>();
        for (Land land : hasOne.getRecords()) {
            hasCodes.add(land.getCrzdbh());
        }
        assertTrue(hasCodes.contains(withFacility), "「有配套」应包含有配套的宗地：" + hasCodes);
        assertFalse(hasCodes.contains(withoutFacility), "「有配套」不该包含没配套的宗地：" + hasCodes);

        query.setHasFacility(Boolean.FALSE);
        IPage<Land> noneOne = landAdminService.queryPage(query);
        List<String> noneCodes = new ArrayList<>();
        for (Land land : noneOne.getRecords()) {
            noneCodes.add(land.getCrzdbh());
        }
        assertTrue(noneCodes.contains(withoutFacility), "「无配套」应包含没配套的宗地：" + noneCodes);
        assertFalse(noneCodes.contains(withFacility), "「无配套」不该包含有配套的宗地：" + noneCodes);
    }

    @Test
    public void landPageRejectsOrderByInjection() {
        LandAdminQueryDTO query = new LandAdminQueryDTO();
        query.setCrzdbh(TEST_PREFIX);
        // 恶意排序字段应被白名单挡下、退回默认排序，而不是拼进 SQL
        query.setOrderBy("id; DROP TABLE t_land; --");
        query.setPageSize(5);
        // 只要求不抛异常且能正常返回（白名单生效的证据是 SQL 没有语法错误）
        IPage<Land> page = landAdminService.queryPage(query);
        assertNotNull(page);
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 取某条宗地的变更履历 */
    private List<Map<String, Object>> recyclServiceHistory(String landId) {
        return landAdminService.queryHistory(landId, 50);
    }

    /** 在履历里找指定动作的第一条 */
    private Map<String, Object> firstLogOfAction(String landId, String action) {
        for (Map<String, Object> row : recyclServiceHistory(landId)) {
            if (action.equals(row.get("action"))) {
                return row;
            }
        }
        return null;
    }

    /** 造一个合法的宗地提交体（crj 固定 1.00，便于断言变更前后） */
    private LandSaveDTO buildLand(String crzdbh, String dkmc) {
        LandSaveDTO dto = new LandSaveDTO();
        dto.setCrzdbh(crzdbh);
        dto.setDkmc(dkmc);
        dto.setXmfl("区级项目");
        dto.setXzqh("西青区");
        dto.setLrdw("测试录入单位");
        dto.setLrr("测试录入人");
        dto.setLxdh("13800000000");
        dto.setCrj(new BigDecimal("1.00"));
        return dto;
    }

    /** 实体 → 提交体（编辑时用，模拟前端把详情原样回传） */
    private LandSaveDTO toDto(Land land) {
        LandSaveDTO dto = new LandSaveDTO();
        dto.setId(land.getId());
        dto.setCrzdbh(land.getCrzdbh());
        dto.setDkmc(land.getDkmc());
        dto.setXzqh(land.getXzqh());
        dto.setXmfl(land.getXmfl());
        dto.setGhydxz(land.getGhydxz());
        dto.setCrj(land.getCrj());
        dto.setCrsj(land.getCrsj());
        dto.setKjsydmj(land.getKjsydmj());
        dto.setZydmj(land.getZydmj());
        dto.setJsmj(land.getJsmj());
        dto.setNrcbdptf(land.getNrcbdptf());
        dto.setSrr(land.getSrr());
        dto.setHtydjfsj(land.getHtydjfsj());
        dto.setLpmc(land.getLpmc());
        dto.setLpjfsj(land.getLpjfsj());
        dto.setDz(land.getDz());
        dto.setXz(land.getXz());
        dto.setNz(land.getNz());
        dto.setBz(land.getBz());
        dto.setTdzldw(land.getTdzldw());
        dto.setTdzljhxdwjh(land.getTdzljhxdwjh());
        dto.setTdzljh(land.getTdzljh());
        dto.setPtsfqq(land.getPtsfqq());
        dto.setPtqkh(land.getPtqkh());
        dto.setPtcbh(land.getPtcbh());
        dto.setCrzdtxsj(land.getCrzdtxsj());
        dto.setPtjsnr(land.getPtjsnr());
        dto.setLrdw(land.getLrdw());
        dto.setLrr(land.getLrr());
        dto.setLxdh(land.getLxdh());
        dto.setZlqsnrsm(land.getZlqsnrsm());
        dto.setBeizhu(land.getBeizhu());
        dto.setXzqh2(land.getXzqh2());
        return dto;
    }

    /** 造一个合法的配套提交体 */
    private FacilitySaveDTO buildFacility(String crzdbh, String ptxmmc) {
        FacilitySaveDTO dto = new FacilitySaveDTO();
        dto.setCrzdbh(crzdbh);
        dto.setPtxmmc(ptxmmc);
        dto.setPtsslb("道路");
        dto.setJsxx("新建");
        dto.setDldj("城市次干路");
        dto.setZjly("土地整理成本");
        dto.setLrdw("测试录入单位");
        dto.setLrr("测试录入人");
        dto.setLxdh("13800000000");
        dto.setGhhxkd(new BigDecimal("30.00"));
        dto.setCd(new BigDecimal("520.00"));
        dto.setTzgs(new BigDecimal("1800.00"));
        return dto;
    }

    /** 造一个环节进度提交体 */
    private Map<String, Object> processPayload(ProcessConfig item, String lcqk,
                                               String start, String end) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("lcId", item.getId());
        payload.put("lcName", item.getName());
        payload.put("lcqk", lcqk);
        payload.put("lckssj", start);
        payload.put("lcjssj", end);
        payload.put("lrr", "测试录入人");
        return payload;
    }

    private String uniqueNo(String tag) {
        return TEST_PREFIX + tag + "-" + System.nanoTime();
    }

    private Date parseDate(String text) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(text);
        } catch (Exception e) {
            throw new IllegalStateException("测试日期写错了：" + text, e);
        }
    }
}
