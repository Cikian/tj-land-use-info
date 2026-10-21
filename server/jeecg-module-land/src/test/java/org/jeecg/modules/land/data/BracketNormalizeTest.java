package org.jeecg.modules.land.data;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.dto.FacilitySaveDTO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.service.IFacilityAdminService;
import org.jeecg.modules.land.data.service.ILandAdminService;
import org.jeecg.modules.land.data.support.DataSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 名称/编号归一集成测试：半角括号→中文、连字符族→{@code -}、去空白、
 * 编号摘 {@code 【…】} 标记进备注、编号补末尾「号」。
 *
 * <p><b>★ 为什么要专门测这个看起来很小的事</b>：
 * 半角与中文括号、{@code -} 与 {@code —} 是**不同的字符**，看起来却几乎一样。
 * 不归一就会出现「同一宗地两条记录」：判重失效、附件挂到另一个 id 上、统计翻倍，
 * 而且因为肉眼看不出差别，排查时极难想到是字符的问题。
 * 更直接的是编号：它是 {@code t_supporting_facilities.crzdbh = t_land.crzdbh}
 * 的关联键，带不带末尾「号」直接决定配套挂不挂得上宗地
 * （实测 58 条配套因此挂不上，首页「待落实配套」少算）。
 *
 * <p><b>★ 口径</b>（2026-10-10 定括号，2026-10-20 扩到全部字符）：
 * 用户录入与展示的名称类内容一律按同一套规则归一；编号一律以「号」结尾，
 * {@code 【城更】/【城市更新】} 这类标记不属于编号，摘下来写进备注。
 */
public class BracketNormalizeTest extends LandIntegrationTestBase {

    @Autowired
    private ILandAdminService landAdminService;

    @Autowired
    private IFacilityAdminService facilityAdminService;

    @Autowired
    private org.jeecg.modules.land.data.service.ILandService landService;

    // ==================================================================
    // 一、纯函数：字符归一规则
    // ==================================================================

    @Test
    public void normalizeBracketsOnlyTouchesBrackets() {
        assertEquals("津西浯（挂）2025-07", DataSupport.normalizeBrackets("津西浯(挂)2025-07"));
        assertEquals("义安路（永顺道-永尚道）", DataSupport.normalizeBrackets("义安路(永顺道-永尚道)"));
        // 已经是中文括号的保持不变（幂等）
        assertEquals("津北辰仓（挂）2018-015", DataSupport.normalizeBrackets("津北辰仓（挂）2018-015"));
        // normalizeBrackets 只负责括号，其余字符一律不动
        assertEquals("ABC （挂） X", DataSupport.normalizeBrackets("ABC (挂) X"));
        assertEquals("津辰青（挂）2025-008号", DataSupport.normalizeBrackets("津辰青(挂)2025-008号"));
        // null 原样返回，不能被变成空串（否则「没传」与「传了空」就分不清了）
        assertNull(DataSupport.normalizeBrackets(null));
        assertEquals("", DataSupport.normalizeBrackets(""));
        // 没有括号时原样返回（不该有任何副作用）
        assertEquals("天宇路", DataSupport.normalizeBrackets("天宇路"));
    }

    /** 括号成对与嵌套都要处理 */
    @Test
    public void normalizeHandlesPairsAndNesting() {
        assertEquals("（a）（b）", DataSupport.normalizeBrackets("(a)(b)"));
        assertEquals("（a（b））", DataSupport.normalizeBrackets("(a(b))"));
        // 只有一个半角括号时也替换（数据可能是残缺的，不能因此漏掉）
        assertEquals("（挂）2025", DataSupport.normalizeBrackets("(挂）2025"));
        assertEquals("（挂）2025", DataSupport.normalizeBrackets("（挂)2025"));
    }

    /** 连字符族统一成半角 -，空白与不可见字符一律去掉 */
    @Test
    public void normalizeTextUnifiesDashesAndStripsWhitespace() {
        // 全角/长短横、下划线、波浪线 → 半角 -
        assertEquals("义安路（永顺道-永尚道）", DataSupport.normalizeText("义安路（永顺道—永尚道）"));
        assertEquals("义安路（永顺道-永尚道）", DataSupport.normalizeText("义安路（永顺道–永尚道）"));
        assertEquals("义安路（永顺道-永尚道）", DataSupport.normalizeText("义安路（永顺道_永尚道）"));
        assertEquals("义安路（永顺道-永尚道）", DataSupport.normalizeText("义安路（永顺道～永尚道）"));
        // 中间空格、制表、换行、全角空格全部去掉
        assertEquals("东润路（桩点临A-大直沽十号路）道路工程",
                DataSupport.normalizeText("东润路（桩点临A—大直沽十号路）\n道路工程"));
        assertEquals("富贵路北旺道", DataSupport.normalizeText("富贵路 　北旺道"));
        // 半角括号同样要变中文
        assertEquals("津西浯（挂）2025-07", DataSupport.normalizeText("津西浯(挂)2025-07"));
        // 幂等
        String once = DataSupport.normalizeText("义安路（永顺道—永尚道）\n");
        assertEquals(once, DataSupport.normalizeText(once));
    }

    /** 编号：摘【…】标记 + 补末尾「号」 */
    @Test
    public void normalizeCodeStripsMarkersAndAppendsHao() {
        DataSupport.CodeInfo plain = DataSupport.normalizeCode("津北辰（挂）2018-015");
        assertEquals("津北辰（挂）2018-015号", plain.getCode(), "★ 编号一律以「号」结尾");
        assertNull(plain.getMarkers(), "没有标记时不该产生备注");

        DataSupport.CodeInfo marked = DataSupport.normalizeCode("津南（挂）2025-08【城市更新】");
        assertEquals("津南（挂）2025-08号", marked.getCode(), "★ 【…】标记不属于编号");
        assertEquals("【城市更新】", marked.getMarkers(), "标记要原样交回，由调用方写进备注");

        DataSupport.CodeInfo shortMark = DataSupport.normalizeCode("津南（挂）2025-09【城更】");
        assertEquals("津南（挂）2025-09号", shortMark.getCode());
        assertEquals("【城更】", shortMark.getMarkers());

        // 已经带「号」的不能变成「号号」（幂等）
        assertEquals("津北辰（挂）2018-015号", DataSupport.normalizeCode("津北辰（挂）2018-015号").getCode());
        // 半角括号 + 缺号 + 标记，一次全处理
        assertEquals("津南（挂）2025-10号",
                DataSupport.normalizeCode("津南(挂)2025-10 【城市更新】").getCode());
    }

    // ==================================================================
    // 二、落库路径：两种写法必须落成同一种
    // ==================================================================

    /**
     * ★ 核心断言：用半角括号提交的宗地编号，落库后应是中文括号。
     * 这样后续判重、按编号查配套才不会被写法差异骗过。
     */
    @Test
    public void landCodeWithHalfWidthBracketsIsStoredAsFullWidth() {
        LandSaveDTO dto = new LandSaveDTO();
        dto.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "BRK(挂)001");
        dto.setDkmc("括号测试地块(a)");
        dto.setSrr("某公司(甲)");
        dto.setXmfl("区级项目");

        String id = landAdminService.createLand(dto, null);
        Land saved = landAdminService.queryDetail(id);

        assertEquals(TEST_DATA_CRZDBH_PREFIX + "BRK（挂）001号", saved.getCrzdbh(),
                "★ 半角括号必须归一成中文括号、并补末尾「号」后再落库，否则同一宗地会变成两条记录");
        assertEquals("括号测试地块（a）", saved.getDkmc(), "地块名称也要归一");
        assertEquals("某公司（甲）", saved.getSrr(), "受让人也要归一");
    }

    /**
     * ★ 归一必须发生在**判重之前**：先建一条中文括号的，
     * 再用半角括号提交同一个编号，应当被判为重复并拒绝。
     * 如果净一发生在判重之后，这里会成功地建出第二条记录。
     */
    @Test
    public void duplicateDetectionWorksAcrossBracketStyles() {
        LandSaveDTO first = new LandSaveDTO();
        first.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "DUP（挂）A");
        first.setDkmc("先建的中文括号地块");
        first.setXmfl("区级项目");
        landAdminService.createLand(first, null);

        LandSaveDTO second = new LandSaveDTO();
        // 同一个编号，但用半角括号提交
        second.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "DUP(挂)A");
        second.setDkmc("后建的半角括号地块");
        second.setXmfl("区级项目");

        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> landAdminService.createLand(second, null));
        assertEquals("创建失败！出让宗地编号「" + TEST_DATA_CRZDBH_PREFIX + "DUP（挂）A号」已存在"
                + "（地块名称：先建的中文括号地块）", ex.getMessage(),
                "★ 两种括号写法必须判为同一个编号，否则同一宗地被录入两次");
    }

    /** 配套项目名称与它挂的宗地编号都要归一 */
    @Test
    public void facilityNameAndLandCodeAreNormalized() {
        // 先用半角括号建宗地（会被归一成中文）
        LandSaveDTO land = new LandSaveDTO();
        land.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "FBR（挂）B");
        land.setDkmc("配套括号测试地块");
        land.setXmfl("区级项目");
        String landId = landAdminService.createLand(land, null);
        assertNotNull(landId);

        FacilitySaveDTO dto = new FacilitySaveDTO();
        dto.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "FBR(挂)B");
        dto.setPtxmmc("义安路(永顺道-永尚道)");
        dto.setJsdw("某建设单位(天津)");

        String facilityId = facilityAdminService.createFacility(dto, null);
        assertNotNull(facilityId, "配套应创建成功（它挂的宗地用半角括号提交，靠归一才匹配上）");
        Facility saved = facilityAdminService.queryDetail(facilityId);
        assertEquals(TEST_DATA_CRZDBH_PREFIX + "FBR（挂）B号", saved.getCrzdbh(),
                "配套上的宗地编号也要归一，否则它会与宗地表对不上");
        assertEquals("义安路（永顺道-永尚道）", saved.getPtxmmc(),
                "★ 配套名称里的括号要归一（旧系统目录名就是中文括号）");
        assertEquals("某建设单位（天津）", saved.getJsdw());
    }

    /**
     * ★ 归一后「按编号找宗地」必须仍然成功，**而且不带「号」也要能查到**。
     * 这是配套/附件挂载的关键路径：用户/导入表里填的编号通常不带末尾「号」，
     * 库里存的是规范写法，查询侧不归一就会误报「宗地不存在」。
     */
    @Test
    public void landLookupWorksWithStoredFullWidthCode() {
        LandSaveDTO land = new LandSaveDTO();
        land.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "LKP（挂）C");
        land.setDkmc("查找测试地块");
        land.setXmfl("区级项目");
        landAdminService.createLand(land, null);

        // 库里存的是「…C号」，用不带「号」的写法查也必须命中（查询侧会归一）
        Land found = landService.queryByCrzdbh(TEST_DATA_CRZDBH_PREFIX + "LKP（挂）C");
        assertNotNull(found, "按未归一（不带「号」）的编号也要能查到宗地");
        assertEquals(TEST_DATA_CRZDBH_PREFIX + "LKP（挂）C号", found.getCrzdbh());
    }

    /** ★ 编号末尾的【城更】标记要摘下来写进备注，不能留在编号里 */
    @Test
    public void cityRenewalMarkerMovesToRemark() {
        LandSaveDTO land = new LandSaveDTO();
        land.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "MRK（挂）E【城市更新】");
        land.setDkmc("城更标记地块");
        land.setXmfl("区级项目");
        String id = landAdminService.createLand(land, null);

        Land saved = landAdminService.queryDetail(id);
        assertEquals(TEST_DATA_CRZDBH_PREFIX + "MRK（挂）E号", saved.getCrzdbh(),
                "★ 标记不属于编号，必须摘掉再补「号」");
        assertEquals("【城市更新】", saved.getBeizhu(), "★ 标记要落到备注里，不能丢");

        // 配套侧同样处理
        FacilitySaveDTO facility = new FacilitySaveDTO();
        facility.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "MRK（挂）E【城更】");
        facility.setPtxmmc("城更配套");
        String facilityId = facilityAdminService.createFacility(facility, null);
        Facility savedFacility = facilityAdminService.queryDetail(facilityId);
        assertEquals(TEST_DATA_CRZDBH_PREFIX + "MRK（挂）E号", savedFacility.getCrzdbh());
        assertEquals("【城更】", savedFacility.getBz(), "配套的标记要落到备注 bz 里");
    }

    /** ★ 连字符与空白的归一也要在落库路径上生效（不只是纯函数） */
    @Test
    public void dashAndWhitespaceAreNormalizedOnSave() {
        LandSaveDTO land = new LandSaveDTO();
        land.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "DSH(挂)  F");
        land.setDkmc("义安路（永顺道—永尚道）\n道路工程");
        land.setSrr("某公司_天津");
        land.setXmfl("区级项目");
        String id = landAdminService.createLand(land, null);

        Land saved = landAdminService.queryDetail(id);
        assertEquals(TEST_DATA_CRZDBH_PREFIX + "DSH（挂）F号", saved.getCrzdbh());
        assertEquals("义安路（永顺道-永尚道）道路工程", saved.getDkmc(),
                "★ 连字符要统一成半角 -，换行/空格要全部去掉");
        assertEquals("某公司-天津", saved.getSrr());
    }

    /** 编辑时也要归一（改了名称再存回去，不能被还原成半角） */
    @Test
    public void updateAlsoNormalizes() {
        LandSaveDTO dto = new LandSaveDTO();
        dto.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "UPD（挂）D");
        dto.setDkmc("原名称");
        dto.setXmfl("区级项目");
        String id = landAdminService.createLand(dto, null);

        LandSaveDTO edit = new LandSaveDTO();
        edit.setId(id);
        edit.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "UPD（挂）D");
        edit.setDkmc("改后名称(乙)");
        edit.setXmfl("区级项目");
        landAdminService.updateLand(edit, null);

        assertEquals("改后名称（乙）", landAdminService.queryDetail(id).getDkmc(),
                "编辑时也要归一，否则改一次就把半角写法又带回来了");
    }
}
