package org.jeecg.modules.land.data.attachment;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentService;
import org.jeecg.modules.land.data.attachment.vo.AttachmentTreeVO;
import org.jeecg.modules.land.data.dto.FacilitySaveDTO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.service.IFacilityAdminService;
import org.jeecg.modules.land.data.service.ILandAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附件目录（按材料类型分组）集成测试。
 *
 * <p><b>★ 为什么这些断言重要</b>：
 * <ul>
 *   <li>{@link #landAndFacilityUseDifferentTypeSets()} —— 宗地与配套是**两套不同的
 *       材料清单**（5 类 / 13 类，逐字取自旧系统存储目录）。
 *       若退回成共用一套，用户会在下拉里看到一半无关选项，
 *       而且配套的历史附件会因为码值不同而「找不到类型名」。</li>
 *   <li>{@link #treeOnlyShowsTypesWithFiles()} —— **空类型不进树**（明确口径）。
 *       如果哪天有人「顺手」把所有类型都输出，界面会立刻多出一堆空目录。</li>
 *   <li>{@link #treeOrderFollowsTypeListNotFileCount()} —— 顺序按材料清单（审批流程顺序），
 *       不按文件数。否则不同项目看到的目录顺序都不一样，用户找位置靠猜。</li>
 *   <li>{@link #facilityTypeCodesAreRejectedForLand()} —— 跨套的码值必须被拒，
 *       否则「配套的 13」存进宗地附件，树里就会出现一个没有名字的目录。</li>
 * </ul>
 */
public class AttachmentTypeTreeTest extends LandIntegrationTestBase {

    @Autowired
    private ILandAttachmentService attachmentService;

    @Autowired
    private ILandAdminService landAdminService;

    @Autowired
    private IFacilityAdminService facilityAdminService;

    private String createLand(String suffix) {
        LandSaveDTO dto = new LandSaveDTO();
        dto.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "TREE-" + suffix);
        dto.setDkmc("树测试用地" + suffix);
        dto.setXmfl("区级项目");
        return landAdminService.createLand(dto, null);
    }

    private String createFacility(String suffix) {
        // 配套必须挂在一宗真实宗地下（新系统硬校验宗地存在性）
        String landId = createLand("F" + suffix);
        FacilitySaveDTO dto = new FacilitySaveDTO();
        dto.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "TREE-F" + suffix);
        dto.setPtxmmc("树测试配套" + suffix);
        return facilityAdminService.createFacility(dto, null);
    }

    private LandAttachment meta(String name, long size) {
        LandAttachment meta = new LandAttachment();
        meta.setFileName(name);
        meta.setFileExt("pdf");
        meta.setFileSize(size);
        meta.setStoreType("local");
        meta.setStorePath("facility/attachment/2026/10/" + name);
        return meta;
    }

    private LandAttachment save(String bizType, String bizId, String bizKey, String typeCode,
                                String name, long size) {
        return attachmentService.saveUploaded(bizType, bizId, bizKey, typeCode, meta(name, size));
    }

    // ==================================================================
    // 一、两套材料清单
    // ==================================================================

    /** ★ 宗地与配套必须是两套不同的清单，且名称逐字来自旧系统存储目录 */
    @Test
    public void landAndFacilityUseDifferentTypeSets() {
        List<Map<String, String>> landTypes = attachmentService.allowedFileTypes("land");
        List<Map<String, String>> facilityTypes = attachmentService.allowedFileTypes("facility");

        assertEquals(5, landTypes.size(), "宗地应为 5 类（旧目录：土地整理计划/配套方案/配套情况函/配套筹备函/出让宗地图形数据（SHP））");
        assertEquals(13, facilityTypes.size(), "配套应为 13 类（旧目录 13 个类型目录名）");

        List<String> landNames = landTypes.stream().map(m -> m.get("text")).collect(Collectors.toList());
        assertTrue(landNames.contains("土地整理计划"));
        assertTrue(landNames.contains("配套方案"));
        assertTrue(landNames.contains("配套情况函"));
        assertTrue(landNames.contains("配套筹备函"));
        assertTrue(landNames.contains("出让宗地图形数据（SHP）"),
                "旧目录有三种写法（SHP）/（shp）/无后缀，规范名取「（SHP）」，实际：" + landNames);

        List<String> facilityNames = facilityTypes.stream().map(m -> m.get("text")).collect(Collectors.toList());
        assertTrue(facilityNames.contains("项建批复文件"), "旧目录名带「文件」后缀，实际：" + facilityNames);
        assertTrue(facilityNames.contains("竣工文件") && facilityNames.contains("移交文件"),
                "旧系统竣工与移交是两个独立目录，不应合并，实际：" + facilityNames);
        assertTrue(facilityNames.contains("专业管理意见"));
        assertTrue(facilityNames.contains("配套项目核定用地与地籍调查"));
        assertTrue(facilityNames.contains("规划工程许可"));
        assertTrue(facilityNames.contains("规划用地许可与划拨手续办理"));
        assertTrue(facilityNames.contains("不动产登记"));

        // 环节走宗地那一套（环节佐证材料属于项目自身资料）
        assertEquals(5, attachmentService.allowedFileTypes("process").size());
    }

    /** 不传 bizType 时退回宗地那套，不能返回空列表（否则前端下拉是空的） */
    @Test
    public void missingBizTypeFallsBackToLandSet() {
        assertEquals(5, attachmentService.allowedFileTypes(null).size());
        assertEquals(5, attachmentService.allowedFileTypes("").size());
    }

    /**
     * ★ 跨套的码值必须被拒。
     * 配套有 13 类，宗地只有 5 类；把「13」存进宗地附件，
     * 树里就会出现一个查不到名字的目录（用户看到的是个空标题行）。
     */
    @Test
    public void facilityTypeCodesAreRejectedForLand() {
        String landId = createLand("X");
        // 13 是配套的「移交文件」，宗地清单里没有
        JeecgBootException ex = assertThrows(JeecgBootException.class, () ->
                save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-X", "13", "a.pdf", 10));
        assertTrue(ex.getMessage().contains("不在允许的范围"),
                "应提示类型不在允许范围，实际：" + ex.getMessage());
        // 宗地的 05 是合法的
        assertNotNull(save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-X", "05", "ok.pdf", 10));
    }

    // ==================================================================
    // 二、树形结构
    // ==================================================================

    /** 树按材料类型分组，节点名是字典中文名，含小计 */
    @Test
    public void treeGroupsFilesByMaterialType() {
        String landId = createLand("G");
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-G", "01", "整理计划.pdf", 100);
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-G", "01", "整理计划2.pdf", 200);
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-G", "03", "情况函.pdf", 300);

        AttachmentTreeVO tree = attachmentService.tree("land", landId);

        assertEquals(3, tree.getTotalFiles().intValue());
        assertEquals(600L, tree.getTotalSize().longValue());
        assertEquals(2, tree.getTypeCount().intValue(), "用了 2 个材料类型");

        AttachmentTreeVO.TypeGroup plan = tree.getGroups().stream()
                .filter(g -> "01".equals(g.getFileType())).findFirst().orElse(null);
        assertNotNull(plan, "应有「土地整理计划」分组");
        assertEquals("土地整理计划", plan.getFileTypeName());
        assertEquals(2, plan.getFileCount().intValue());
        assertEquals(300L, plan.getTotalSize().longValue(), "100+200");
        // key 形如 land:<bizId>:01 —— 带上业务对象 id 是为了跨项目视图下不撞 key
        //（多个项目都会有「01」这个码）
        assertTrue(plan.getKey().endsWith(":" + landId + ":01"),
                "分组 key 应带上业务对象 id，实际：" + plan.getKey());
        assertEquals(landId, plan.getBizId(),
                "分组应冗余业务对象 id —— 前端「上传到该类型」要用它定归属");
        assertEquals("land", plan.getBizType());

        // 分组里的文件要带展示字段（可读大小 / 预览方式），否则前端渲染不出来
        LandAttachment first = plan.getFiles().get(0);
        assertNotNull(first.getReadableSize(), "文件应带可读大小（enrich 过）");
        assertNotNull(first.getPreviewMode(), "文件应带预览方式（enrich 过）");
    }

    /** ★ 空类型不进树：没有任何附件的材料类型不应出现 */
    @Test
    public void treeOnlyShowsTypesWithFiles() {
        String landId = createLand("E");
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-E", "02", "配套方案.pdf", 10);

        AttachmentTreeVO tree = attachmentService.tree("land", landId);
        assertEquals(1, tree.getTypeCount().intValue(), "只用了 1 个类型");
        assertEquals("02", tree.getGroups().get(0).getFileType());
        // 其余 4 类都没有文件，不应出现
        assertTrue(tree.getGroups().stream().noneMatch(g -> "01".equals(g.getFileType())),
                "没有文件的类型不应进树");
    }

    /** 一个附件都没有时返回空树，不报错（新建项目就是这个状态） */
    @Test
    public void treeIsEmptyWhenNoAttachment() {
        String landId = createLand("N");
        AttachmentTreeVO tree = attachmentService.tree("land", landId);
        assertNotNull(tree);
        assertTrue(tree.getGroups().isEmpty());
        assertEquals(0, tree.getTotalFiles().intValue());
        assertEquals(0L, tree.getTotalSize().longValue());
    }

    /** ★ 顺序按材料清单（审批流程顺序），不按文件数 */
    @Test
    public void treeOrderFollowsTypeListNotFileCount() {
        String landId = createLand("O");
        // 「配套情况函」是 03、「土地整理计划」是 01；故意让 03 的文件更多
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-O", "03", "a.pdf", 10);
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-O", "03", "b.pdf", 10);
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-O", "03", "c.pdf", 10);
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-O", "01", "d.pdf", 10);

        AttachmentTreeVO tree = attachmentService.tree("land", landId);
        assertEquals("01", tree.getGroups().get(0).getFileType(),
                "01 应排在 03 前面（按清单顺序，不按文件数）");
        assertEquals("03", tree.getGroups().get(1).getFileType());
    }

    /** 配套用配套那套类型：同一个码值在两套里的含义不同，树上的名字必须按归属取 */
    @Test
    public void facilityTreeUsesFacilityTypeNames() {
        String facilityId = createFacility("T");
        // 配套的 01 是「项建批复文件」；宗地的 01 是「土地整理计划」
        save("facility", facilityId, TEST_DATA_CRZDBH_PREFIX + "TREE-FT", "01", "项建.pdf", 50);

        AttachmentTreeVO tree = attachmentService.tree("facility", facilityId);
        assertEquals(1, tree.getTypeCount().intValue());
        assertEquals("项建批复文件", tree.getGroups().get(0).getFileTypeName(),
                "配套的 01 必须显示配套清单里的名字，不能显示宗地的「土地整理计划」");
    }

    /** 13 类配套类型全部可用（逐个存一遍，确认白名单没有漏项） */
    @Test
    public void allThirteenFacilityTypesAreAccepted() {
        String facilityId = createFacility("A");
        List<Map<String, String>> types = attachmentService.allowedFileTypes("facility");
        for (Map<String, String> type : types) {
            String code = type.get("value");
            save("facility", facilityId, TEST_DATA_CRZDBH_PREFIX + "TREE-FA", code,
                    "file" + code + ".pdf", 10);
        }
        AttachmentTreeVO tree = attachmentService.tree("facility", facilityId);
        assertEquals(13, tree.getTypeCount().intValue(), "13 类都应有文件");
        assertEquals(13, tree.getTotalFiles().intValue());
        // 顺序应与清单一致
        assertEquals(types.stream().map(m -> m.get("value")).collect(Collectors.toList()),
                tree.getGroups().stream().map(AttachmentTreeVO.TypeGroup::getFileType)
                        .collect(Collectors.toList()));
    }

    /** 树要带上业务可读键（界面标题显示「配套项目 · 天宇路」） */
    @Test
    public void treeCarriesBizKey() {
        String landId = createLand("K");
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-K", "01", "a.pdf", 10);
        assertEquals(TEST_DATA_CRZDBH_PREFIX + "TREE-K",
                attachmentService.tree("land", landId).getBizKey());
    }

    /** 非法业务类型返回空树而不是抛异常（界面首屏可能还没选对象） */
    @Test
    public void treeWithUnknownBizTypeIsEmpty() {
        AttachmentTreeVO tree = attachmentService.tree("unknown", "whatever");
        assertNotNull(tree);
        assertTrue(tree.getGroups().isEmpty());
    }

    /** 与 byBiz 列表口径一致：树上的文件数应等于按业务查到的条数 */
    @Test
    public void treeTotalsMatchByBizList() {
        String landId = createLand("C");
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-C", "01", "a.pdf", 11);
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-C", "04", "b.pdf", 22);

        AttachmentTreeVO tree = attachmentService.tree("land", landId);
        List<LandAttachment> flat = attachmentService.queryByBiz("land", landId);
        assertEquals(flat.size(), tree.getTotalFiles().intValue(),
                "树的文件总数必须与按业务查到的条数一致（两个入口不能各算一套）");
        assertFalse(flat.isEmpty());
    }

    // ==================================================================
    // 三、跨项目树（附件管理页用）
    // ==================================================================

    /**
     * ★ 跨项目树：按业务对象分组，组内再按材料类型分组，
     * 且**与单项目树的口径完全一致**（同一个 buildTree，不会出现两套算法）。
     */
    @Test
    public void treeByProjectGroupsAndMatchesSingleTree() {
        String landA = createLand("PA");
        String landB = createLand("PB");
        save("land", landA, TEST_DATA_CRZDBH_PREFIX + "TREE-PA", "01", "a1.pdf", 100);
        save("land", landA, TEST_DATA_CRZDBH_PREFIX + "TREE-PA", "03", "a2.pdf", 200);
        save("land", landB, TEST_DATA_CRZDBH_PREFIX + "TREE-PB", "01", "b1.pdf", 50);

        List<Map<String, Object>> projects = attachmentService.treeByProject("land", 200);
        assertNotNull(projects);
        assertFalse(projects.isEmpty());

        // 至少包含这两个项目（库里可能有其它测试残留，所以按 id 找而不是断言总数）
        Map<String, Object> itemA = findProject(projects, landA);
        Map<String, Object> itemB = findProject(projects, landB);
        assertNotNull(itemA, "应包含项目 A");
        assertNotNull(itemB, "应包含项目 B");

        assertEquals(2, ((Number) itemA.get("totalFiles")).intValue());
        assertEquals(300L, ((Number) itemA.get("totalSize")).longValue());
        assertEquals(2, ((Number) itemA.get("typeCount")).intValue());
        assertEquals(1, ((Number) itemB.get("totalFiles")).intValue());

        // ★ 与单项目树逐项一致：两个入口共用同一套分组逻辑
        AttachmentTreeVO single = attachmentService.tree("land", landA);
        AttachmentTreeVO inProject = (AttachmentTreeVO) itemA.get("tree");
        assertEquals(single.getTotalFiles(), inProject.getTotalFiles());
        assertEquals(single.getTotalSize(), inProject.getTotalSize());
        assertEquals(single.getTypeCount(), inProject.getTypeCount());
        assertEquals(
                single.getGroups().stream().map(AttachmentTreeVO.TypeGroup::getFileType)
                        .collect(Collectors.toList()),
                inProject.getGroups().stream().map(AttachmentTreeVO.TypeGroup::getFileType)
                        .collect(Collectors.toList()),
                "跨项目视图里的分组顺序必须与单项目视图一致");

        // ★ 分组要带归属信息，否则前端「上传到该类型」不知道该传到哪个项目
        AttachmentTreeVO.TypeGroup group = inProject.getGroups().get(0);
        assertEquals(landA, group.getBizId());
        assertEquals("land", group.getBizType());
    }

    /** 项目按附件数倒序（用户最可能先看资料齐全的项目；limit 截断时丢的也该是少的） */
    @Test
    public void treeByProjectSortsByFileCountDesc() {
        String few = createLand("PF");
        String many = createLand("PM");
        save("land", few, TEST_DATA_CRZDBH_PREFIX + "TREE-PF", "01", "f.pdf", 10);
        for (int i = 0; i < 4; i++) {
            save("land", many, TEST_DATA_CRZDBH_PREFIX + "TREE-PM", "01", "m" + i + ".pdf", 10);
        }

        List<Map<String, Object>> projects = attachmentService.treeByProject("land", 200);
        int indexMany = indexOfProject(projects, many);
        int indexFew = indexOfProject(projects, few);
        assertTrue(indexMany > -1 && indexFew > -1, "两个项目都应在结果里");
        assertTrue(indexMany < indexFew,
                "附件多的项目应排在前面（多的在第 " + indexMany + " 位，少的在第 " + indexFew + " 位）");
    }

    /** 没有任何附件时返回空列表，不报错 */
    @Test
    public void treeByProjectEmptyWhenNoAttachment() {
        assertNotNull(attachmentService.treeByProject("land", 200));
        // 未知业务类型也要安全返回空
        assertTrue(attachmentService.treeByProject("unknown", 200).isEmpty());
        assertTrue(attachmentService.treeByProject(null, 200).isEmpty());
    }

    private Map<String, Object> findProject (List<Map<String, Object>> projects, String bizId) {
        return projects.stream()
                .filter(p -> bizId.equals(p.get("bizId")))
                .findFirst().orElse(null);
    }

    private int indexOfProject (List<Map<String, Object>> projects, String bizId) {
        for (int i = 0; i < projects.size(); i++) {
            if (bizId.equals(projects.get(i).get("bizId"))) {
                return i;
            }
        }
        return -1;
    }
}
