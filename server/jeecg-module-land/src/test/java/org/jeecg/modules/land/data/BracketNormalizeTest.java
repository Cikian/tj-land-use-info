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
 * 括号归一（半角 {@code ()} → 中文 {@code （）}）集成测试。
 *
 * <p><b>★ 为什么要专门测这个看起来很小的事</b>：
 * 半角与中文括号是**不同的字符**，看起来却几乎一样。不归一就会出现
 * 「同一宗地两条记录」：判重失效、附件挂到另一个 id 上、统计翻倍，
 * 而且因为肉眼看不出差别，排查时极难想到是括号的问题。
 * 实测库里 847 条宗地编号曾有 2 条用了半角。
 *
 * <p><b>★ 口径</b>（2026-10-10 确认）：用户录入与展示的名称类内容一律用中文括号。
 * 旧系统存储目录名（{@code 津北辰仓（挂）2018-015}、{@code 义安路（永顺道-永尚道）}）
 * 本来就全用中文括号，所以这是「向旧系统口径对齐」。
 *
 * <p>只替换括号字符，其余字符（含中间空格、大小写）不动 ——
 * 编号的书写习惯归用户，只统一「看起来一样却是不同字符」的那一处。
 */
public class BracketNormalizeTest extends LandIntegrationTestBase {

    @Autowired
    private ILandAdminService landAdminService;

    @Autowired
    private IFacilityAdminService facilityAdminService;

    @Autowired
    private org.jeecg.modules.land.data.service.ILandService landService;

    // ==================================================================
    // 一、纯函数：只动括号，不动别的
    // ==================================================================

    @Test
    public void normalizeOnlyTouchesBrackets() {
        assertEquals("津西浯（挂）2025-07", DataSupport.normalizeBrackets("津西浯(挂)2025-07"));
        assertEquals("义安路（永顺道-永尚道）", DataSupport.normalizeBrackets("义安路(永顺道-永尚道)"));
        // 已经是中文括号的保持不变（幂等）
        assertEquals("津北辰仓（挂）2018-015", DataSupport.normalizeBrackets("津北辰仓（挂）2018-015"));
        // 其余字符一律不动：空格、大小写、连字符、末尾的「号」
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

        assertEquals(TEST_DATA_CRZDBH_PREFIX + "BRK（挂）001", saved.getCrzdbh(),
                "★ 半角括号必须归一成中文括号后再落库，否则同一宗地会变成两条记录");
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
        assertEquals("创建失败！出让宗地编号「" + TEST_DATA_CRZDBH_PREFIX + "DUP（挂）A」已存在"
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
        assertEquals(TEST_DATA_CRZDBH_PREFIX + "FBR（挂）B", saved.getCrzdbh(),
                "配套上的宗地编号也要归一，否则它会与宗地表对不上");
        assertEquals("义安路（永顺道-永尚道）", saved.getPtxmmc(),
                "★ 配套名称里的括号要归一（旧系统目录名就是中文括号）");
        assertEquals("某建设单位（天津）", saved.getJsdw());
    }

    /**
     * ★ 归一后「按编号找宗地」必须仍然成功。
     * 这是配套/附件挂载的关键路径：配套提交的编号若带半角括号，
     * 而归一后的库里存的是中文括号，宗地存在性校验会误报「宗地不存在」。
     */
    @Test
    public void landLookupWorksWithStoredFullWidthCode() {
        LandSaveDTO land = new LandSaveDTO();
        land.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "LKP（挂）C");
        land.setDkmc("查找测试地块");
        land.setXmfl("区级项目");
        landAdminService.createLand(land, null);

        // 库里的编号已是中文括号，按它查必须命中（配套存在性校验走的就是这条路）
        Land found = landService.queryByCrzdbh(TEST_DATA_CRZDBH_PREFIX + "LKP（挂）C");
        assertNotNull(found, "按归一方后的编号必须能查到宗地");
        assertEquals(TEST_DATA_CRZDBH_PREFIX + "LKP（挂）C", found.getCrzdbh());
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
