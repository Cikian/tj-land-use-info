package org.jeecg.modules.land.stat.home;

import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.dto.FacilitySaveDTO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.service.IFacilityAdminService;
import org.jeecg.modules.land.data.service.ILandAdminService;
import org.jeecg.modules.land.stat.home.service.IHomeStatService;
import org.jeecg.modules.land.stat.home.vo.LandOverviewVO;
import org.jeecg.modules.land.stat.home.vo.PendingFacilityVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Description: 首页看板 · 出让地块情况统计 + 待落实配套统计 集成测试
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-19
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（五），覆盖从 data 域迁到 stat/home 的两块看板统计。
 * 测试跑在事务里、结束回滚，并用 {@code TJ-TEST-DATA-} 前缀兜底清理
 * （见 {@link LandIntegrationTestBase}）。
 *
 * <p><b>★ 断言一律用「前后差值」，不用绝对值</b>：本测试连的是真实库
 * （847 宗地 / 1433 配套），绝对值随时会变；差值才能验证「本次新增的数据
 * 是否按预期被统计」。
 *
 * <p><b>★ 这里最要紧的几条断言（错了前端数字会悄悄错，且很难发现）</b>：
 * <ul>
 *   <li>{@link #districtRanksGroupByXzqh2NotXzqh()} —— 区级子表按旧系统
 *       {@code xzqh2} 分组，不是 {@code xzqh}；「顺手统一」会把区级排行从几十行
 *       塌缩成几个区；</li>
 *   <li>{@link #roadCountExcludesNotInvolvedAndD007Hardcode()} —— D-007 的
 *       硬编码排除（「引河里北道、富彩道」）必须继续生效；</li>
 *   <li>{@link #facilityTotalDeduplicatesSameNameAcrossLands()} —— 配套必须
 *       先按 {@code ptxmmc} 去重再计数，同名道路跨宗地只算一条；</li>
 *   <li>{@link #notStartedFlagExcludesCompletedFacility()} —— {@code sfkg='否'}
 *       的「已竣工」不能算完成；</li>
 *   <li>{@link #actionableConditionExcludesCurrentStateAndNotInvolved()} ——
 *       「现状 / 不涉及」不计入需落实配套。</li>
 * </ul>
 */
public class HomeStatTest extends LandIntegrationTestBase {

    /** 测试数据专用前缀（业务上不会出现，兜底清理按它识别） */
    private static final String TEST_PREFIX = "TJ-TEST-DATA-";

    @Autowired
    private IHomeStatService homeStatService;

    @Autowired
    private ILandAdminService landAdminService;

    @Autowired
    private IFacilityAdminService facilityAdminService;

    // ==================================================================
    // 一、出让地块情况统计
    // ==================================================================

    @Test
    public void landOverviewCountsDelta() {
        LandOverviewVO before = homeStatService.queryLandOverview();

        String tag = uniqueTag();
        landAdminService.createLand(buildLand(TEST_PREFIX + "lo-city-a-" + tag, "看板市级甲",
                "市级项目", TEST_PREFIX + "市级区甲-" + tag, null), null);
        landAdminService.createLand(buildLand(TEST_PREFIX + "lo-city-b-" + tag, "看板市级乙",
                "市级项目", TEST_PREFIX + "市级区乙-" + tag, null), null);
        landAdminService.createLand(buildLand(TEST_PREFIX + "lo-dist-a-" + tag, "看板区级甲",
                "区级项目", TEST_PREFIX + "区级区甲-" + tag, TEST_PREFIX + "街镇甲-" + tag), null);

        LandOverviewVO after = homeStatService.queryLandOverview();

        assertEquals(before.getTotal() + 3, after.getTotal(), "总数应增加 3 宗地");
        assertEquals(before.getCityCount() + 2, after.getCityCount(), "市级项目应增加 2 宗地");
        assertEquals(before.getDistrictCount() + 1, after.getDistrictCount(), "区级项目应增加 1 宗地");
        // 三条宗地都没挂配套，道路数不应变化（roadCount 按配套名称去重）
        assertEquals(before.getRoadCount(), after.getRoadCount(), "没有配套时涉及道路数不应变化");
    }

    @Test
    public void districtRanksGroupByXzqh2NotXzqh() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "区级测试区-" + tag;
        String xzqh2 = TEST_PREFIX + "区级测试街镇-" + tag;
        landAdminService.createLand(buildLand(TEST_PREFIX + "rank-qj-" + tag, "区级分组测试地",
                "区级项目", xzqh, xzqh2), null);

        LandOverviewVO vo = homeStatService.queryLandOverview();

        LandOverviewVO.RankItem town = findLandRank(vo.getRanks(), "区级项目", xzqh2);
        assertNotNull(town, "★ 区级排行必须按旧系统 xzqh2 分组，找不到分组：" + xzqh2);
        assertEquals(1L, town.getValue(), "该分组应恰好有 1 宗测试宗地");
        assertNull(findLandRank(vo.getRanks(), "区级项目", xzqh),
                "★ 区级排行不应按 xzqh 分组（旧系统口径是 xzqh2）：" + xzqh);
    }

    @Test
    public void cityRanksGroupByXzqh() {
        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "市级测试区-" + tag;
        // xzqh2 给一个不同的值：市级分组只认 xzqh，不应出现 xzqh2
        String xzqh2 = TEST_PREFIX + "市级不应分组-" + tag;
        landAdminService.createLand(buildLand(TEST_PREFIX + "rank-sj-" + tag, "市级分组测试地",
                "市级项目", xzqh, xzqh2), null);

        LandOverviewVO vo = homeStatService.queryLandOverview();

        LandOverviewVO.RankItem item = findLandRank(vo.getRanks(), "市级项目", xzqh);
        assertNotNull(item, "市级排行应按 xzqh 分组，找不到分组：" + xzqh);
        assertEquals(1L, item.getValue());
        assertNull(findLandRank(vo.getRanks(), "市级项目", xzqh2),
                "市级排行不应按 xzqh2 分组：" + xzqh2);
    }

    @Test
    public void roadCountExcludesNotInvolvedAndD007Hardcode() {
        LandOverviewVO before = homeStatService.queryLandOverview();

        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "道路口径区-" + tag;
        String crzdbh = TEST_PREFIX + "road-" + tag;
        landAdminService.createLand(buildLand(crzdbh, "道路口径测试地", "市级项目", xzqh, null), null);

        // ① 正常配套：应计入
        facilityAdminService.createFacility(buildFacility(crzdbh,
                TEST_PREFIX + "正常道路-" + tag, "新建", "否", "是"), null);
        // ② 名称含「不涉及」：旧系统排除
        facilityAdminService.createFacility(buildFacility(crzdbh,
                TEST_PREFIX + "不涉及道路-" + tag, "新建", "否", "是"), null);
        // ③ ★ D-007 硬编码排除项：沿用旧缺陷，不要「顺手修好」
        facilityAdminService.createFacility(buildFacility(crzdbh,
                "引河里北道、富彩道", "新建", "否", "是"), null);

        LandOverviewVO after = homeStatService.queryLandOverview();
        assertEquals(before.getRoadCount() + 1, after.getRoadCount(),
                "★ 全局道路数只应增加 1 条（含「不涉及」与 D-007 硬编码项都被排除）");

        LandOverviewVO.RankItem group = findLandRank(after.getRanks(), "市级项目", xzqh);
        assertNotNull(group, "测试分组应出现在市级排行里");
        assertEquals(1L, group.getValue());
        assertEquals(1L, group.getRoadCount(),
                "★ 该分组涉及道路数应为 1（两条被排除的配套不能计入）");
    }

    // ==================================================================
    // 二、待落实配套统计
    // ==================================================================

    @Test
    public void actionableConditionExcludesCurrentStateAndNotInvolved() {
        PendingFacilityVO before = homeStatService.queryPendingFacility();

        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "待落实区-" + tag;
        String crzdbh = TEST_PREFIX + "pf-" + tag;
        landAdminService.createLand(buildLand(crzdbh, "待落实口径测试地", "市级项目", xzqh, null), null);

        // 唯一一条「需落实」配套
        facilityAdminService.createFacility(buildFacility(crzdbh,
                TEST_PREFIX + "需落实道路-" + tag, "新建", "否", "否"), null);
        // 现状道路：不计入需落实
        facilityAdminService.createFacility(buildFacility(crzdbh,
                TEST_PREFIX + "现状道路-" + tag, "现状", "否", "否"), null);
        // 名称含「不涉及」：不计入需落实
        facilityAdminService.createFacility(buildFacility(crzdbh,
                TEST_PREFIX + "不涉及配套-" + tag, "新建", "否", "否"), null);

        PendingFacilityVO after = homeStatService.queryPendingFacility();

        assertEquals(before.getLandTotal() + 1, after.getLandTotal(),
                "该宗地至少有一条需落实配套，应计入待落实宗地数");
        assertEquals(before.getCityLandCount() + 1, after.getCityLandCount(), "市级宗地数应增加 1");
        assertEquals(before.getDistrictLandCount(), after.getDistrictLandCount(), "区级宗地数不应变化");
        assertEquals(before.getFacilityTotal() + 1, after.getFacilityTotal(),
                "★ 三条配套里只有 1 条是「需落实」，配套总数只应增加 1");
        assertEquals(before.getCompletedFacilityCount(), after.getCompletedFacilityCount(),
                "没有竣工的配套，完成数不应变化");

        PendingFacilityVO.RankItem group = findPendingRank(after.getRanks(), "市级项目", xzqh);
        assertNotNull(group, "测试分组应出现在待落实排行里");
        assertEquals(1L, group.getValue(), "该分组有需落实配套的宗地数为 1");
        assertEquals(1L, group.getFacilityCount(), "该分组需落实配套数为 1");
        assertEquals(0L, group.getCompletedCount(), "没有竣工配套，完成数为 0");
    }

    @Test
    public void facilityTotalDeduplicatesSameNameAcrossLands() {
        PendingFacilityVO before = homeStatService.queryPendingFacility();

        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "去重区-" + tag;
        String ptxmmc = TEST_PREFIX + "同名道路-" + tag;
        String landA = TEST_PREFIX + "dedupe-a-" + tag;
        String landB = TEST_PREFIX + "dedupe-b-" + tag;
        landAdminService.createLand(buildLand(landA, "去重甲地", "市级项目", xzqh, null), null);
        landAdminService.createLand(buildLand(landB, "去重乙地", "市级项目", xzqh, null), null);
        // 同一个配套名称挂在两宗地下，且两行都已竣工 —— 去重后只能算 1 条
        facilityAdminService.createFacility(buildFacility(landA, ptxmmc, "新建", "是", "是"), null);
        facilityAdminService.createFacility(buildFacility(landB, ptxmmc, "新建", "是", "是"), null);

        PendingFacilityVO after = homeStatService.queryPendingFacility();

        assertEquals(before.getFacilityTotal() + 1, after.getFacilityTotal(),
                "★ 同一 ptxmmc 跨两宗地只能算 1 条配套（先去重再计数）");
        assertEquals(before.getCompletedFacilityCount() + 1, after.getCompletedFacilityCount(),
                "★ 去重后的这 1 条配套已完成，完成数只应增加 1");

        PendingFacilityVO.RankItem group = findPendingRank(after.getRanks(), "市级项目", xzqh);
        assertNotNull(group, "测试分组应出现在待落实排行里");
        assertEquals(2L, group.getValue(), "两宗地都有需落实配套，宗地数为 2");
        assertEquals(1L, group.getFacilityCount(),
                "★ 同名配套跨两宗地只算 1 条，不能按行数算成 2");
        assertEquals(1L, group.getCompletedCount(),
                "★ 同名配套两行都已完成，也只能算 1 条已完成");
    }

    @Test
    public void notStartedFlagExcludesCompletedFacility() {
        PendingFacilityVO before = homeStatService.queryPendingFacility();

        String tag = uniqueTag();
        String xzqh = TEST_PREFIX + "开工否区-" + tag;
        String crzdbh = TEST_PREFIX + "sfkg-no-" + tag;
        landAdminService.createLand(buildLand(crzdbh, "开工否测试地", "市级项目", xzqh, null), null);
        // sfjg='是' 但 sfkg='否'：旧系统口径不算完成（两个字段自相矛盾时以「未开工」为准）
        facilityAdminService.createFacility(buildFacility(crzdbh,
                TEST_PREFIX + "竣工但未开工-" + tag, "新建", "是", "否"), null);

        PendingFacilityVO after = homeStatService.queryPendingFacility();

        assertEquals(before.getFacilityTotal() + 1, after.getFacilityTotal(),
                "这条配套本身是需落实的，配套总数应增加 1");
        assertEquals(before.getCompletedFacilityCount(), after.getCompletedFacilityCount(),
                "★ sfkg='否' 时即使 sfjg='是' 也不能算完成");

        PendingFacilityVO.RankItem group = findPendingRank(after.getRanks(), "市级项目", xzqh);
        assertNotNull(group);
        assertEquals(1L, group.getFacilityCount());
        assertEquals(0L, group.getCompletedCount(),
                "★ 排行里的完成数同样要排除 sfkg='否'");
    }

    // ==================================================================
    // 三、预警面板契约（前端 HomeAttrPanel 已依赖字段名）
    // ==================================================================

    @Test
    public void warningsKeepContractShape() {
        PendingFacilityVO vo = homeStatService.queryPendingFacility();

        assertNotNull(vo.getWarnings(), "warnings 不能为 null（前端直接取 warnings.city 等）");
        assertTrue(vo.getWarnings().containsKey("city"), "warnings 必须含 city");
        assertTrue(vo.getWarnings().containsKey("district"), "warnings 必须含 district");
        assertTrue(vo.getWarnings().containsKey("plot"), "warnings 必须含 plot");

        // 真实库必然有数据；这里只校验「有行时字段齐不齐」，
        // 避免新环境空库时把测试跑红
        assertWarningItems(vo.getWarnings().get("city"), "city");
        assertWarningItems(vo.getWarnings().get("district"), "district");
        assertWarningItems(vo.getWarnings().get("plot"), "plot");

        // 完成率：分母为 0 时为 0.00，否则 completed / total * 100 保留 2 位
        assertNotNull(vo.getCompletionRate(), "completionRate 不能为 null");
        assertEquals(2, vo.getCompletionRate().scale(), "completionRate 应保留 2 位小数");
        if (vo.getFacilityTotal() > 0) {
            BigDecimal expected = BigDecimal.valueOf(vo.getCompletedFacilityCount())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(vo.getFacilityTotal()), 2, RoundingMode.HALF_UP);
            assertEquals(0, expected.compareTo(vo.getCompletionRate()),
                    "完成率口径应为 completed / total * 100：" + vo.getCompletionRate());
        }
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private static void assertWarningItems(List<PendingFacilityVO.WarningItem> items, String key) {
        if (items == null) {
            return;
        }
        for (PendingFacilityVO.WarningItem item : items) {
            assertNotNull(item.getId(), key + " 预警行的 id 不能为 null");
            assertNotNull(item.getDistrict(), key + " 预警行的 district 不能为 null");
            // 7 个比率字段是前端直接渲染的，null 会让页面显示空白/报错
            assertNotNull(item.getLandAcquisition(), key + " 缺 landAcquisition");
            assertNotNull(item.getFeasibility(), key + " 缺 feasibility");
            assertNotNull(item.getPreliminaryDesign(), key + " 缺 preliminaryDesign");
            assertNotNull(item.getFund(), key + " 缺 fund");
            assertNotNull(item.getNotStarted(), key + " 缺 notStarted");
            assertNotNull(item.getNotCompleted(), key + " 缺 notCompleted");
            assertNotNull(item.getNotHandedOver(), key + " 缺 notHandedOver");
        }
    }

    private static LandOverviewVO.RankItem findLandRank(List<LandOverviewVO.RankItem> ranks,
                                                        String projectType, String name) {
        for (LandOverviewVO.RankItem item : ranks) {
            if (projectType.equals(item.getProjectType()) && name.equals(item.getName())) {
                return item;
            }
        }
        return null;
    }

    private static PendingFacilityVO.RankItem findPendingRank(List<PendingFacilityVO.RankItem> ranks,
                                                              String projectType, String name) {
        for (PendingFacilityVO.RankItem item : ranks) {
            if (projectType.equals(item.getProjectType()) && name.equals(item.getName())) {
                return item;
            }
        }
        return null;
    }

    /** 造一个合法的宗地提交体（xzqh2 按需传，null 表示不设置） */
    private LandSaveDTO buildLand(String crzdbh, String dkmc, String xmfl, String xzqh, String xzqh2) {
        LandSaveDTO dto = new LandSaveDTO();
        dto.setCrzdbh(crzdbh);
        dto.setDkmc(dkmc);
        dto.setXmfl(xmfl);
        dto.setXzqh(xzqh);
        dto.setXzqh2(xzqh2);
        dto.setLrdw("测试录入单位");
        dto.setLrr("测试录入人");
        dto.setLxdh("13800000000");
        dto.setCrj(new BigDecimal("1.00"));
        return dto;
    }

    /** 造一个合法的配套提交体（sfjg / sfkg 用于验证完成口径） */
    private FacilitySaveDTO buildFacility(String crzdbh, String ptxmmc,
                                          String jsxx, String sfjg, String sfkg) {
        FacilitySaveDTO dto = new FacilitySaveDTO();
        dto.setCrzdbh(crzdbh);
        dto.setPtxmmc(ptxmmc);
        dto.setPtsslb("道路");
        dto.setJsxx(jsxx);
        dto.setSfjg(sfjg);
        dto.setSfkg(sfkg);
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

    /** 测试数据唯一后缀（nanoTime 保证同一轮测试内不重号） */
    private static String uniqueTag() {
        return String.valueOf(System.nanoTime());
    }
}
