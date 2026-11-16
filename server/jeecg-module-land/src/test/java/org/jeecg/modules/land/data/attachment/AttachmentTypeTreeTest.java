package org.jeecg.modules.land.data.attachment;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;
import org.jeecg.modules.land.data.attachment.mapper.LandAttachmentMapper;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentService;
import org.jeecg.modules.land.data.attachment.vo.AttachmentTreeVO;
import org.jeecg.modules.land.data.dto.FacilitySaveDTO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.process.entity.ProcessConfig;
import org.jeecg.modules.land.data.process.mapper.ProcessConfigMapper;
import org.jeecg.modules.land.data.service.IFacilityAdminService;
import org.jeecg.modules.land.data.service.ILandAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.HashMap;
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
 *       材料清单**（宗地 4 类 / 配套 13 类，P1-08 报告图 3 为唯一基准）。
 *       若退回成共用一套，用户会在下拉里看到一半无关选项，
 *       而且两套的 01~04 重号，同一个码值会显示成另一种材料名。</li>
 *   <li>{@link #processFollowsFacilityTypeSet()} —— 环节进度挂在配套项目下，
 *       材料目录必须**跟随挂靠侧**用配套那 13 类（整改前是「环节统一用宗地」）。</li>
 *   <li>{@link #treeOnlyShowsTypesWithFiles()} —— **空类型不进树**（明确口径）。
 *       如果哪天有人「顺手」把所有类型都输出，界面会立刻多出一堆空目录。</li>
 *   <li>{@link #treeOrderFollowsTypeListNotFileCount()} —— 顺序按材料清单（审批流程顺序），
 *       不按文件数。否则不同项目看到的目录顺序都不一样，用户找位置靠猜。</li>
 *   <li>{@link #legacyTypesAreRejectedOnUpload()} —— 旧码不再可写，
 *       否则新附件会落到一个「字典里没有的名字」下（P1-08 的验收红线是数据不错归类）。</li>
 *   <li>{@link #legacyBucketStillVisitedInTree()} —— 迁移到 99「其他（历史）」的历史附件
 *       必须仍然出现在树里、能看能下载，**不得因为码值不在清单里就消失**。</li>
 * </ul>
 */
public class AttachmentTypeTreeTest extends LandIntegrationTestBase {

    @Autowired
    private ILandAttachmentService attachmentService;

    /**
     * 直接用 Mapper 落一条「历史遗留」附件（绕过写侧白名单）。
     *
     * <p>★ 为什么需要绕过：99「其他（历史）」只可能由迁移脚本写进库
     * （{@code sql/data/15_attachment_type_remap.sql}），正常上传是拒绝 99 的。
     * 要验证「历史附件不丢」，只能从数据层造出这条历史行。
     *
     * <p>★ 为什么用 Mapper 而不是注 Service 实现类：测试上下文用
     * {@code @EnableTransactionManagement}（JDK 动态代理），Service Bean 的实际类型是
     * 接口，注入实现类会启动失败；Mapper 没有这层代理。
     */
    @Autowired
    private LandAttachmentMapper attachmentMapper;

    @Autowired
    private ILandAdminService landAdminService;

    @Autowired
    private IFacilityAdminService facilityAdminService;

    @Autowired
    private ProcessConfigMapper processConfigMapper;

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

    /**
     * 给配套登记一个环节进度，返回 {@code t_facility_process.id}。
     *
     * <p>★ 为什么要走真实链路（而不是直接往表里插）：环节的展示名（lcName）与
     * 宗地编号冗余都由 saveProcess 维护，插一条假行会让断言看不出真实问题。
     */
    private String createProcess(String facilityId) {
        List<ProcessConfig> items = processConfigMapper.selectItems();
        assertFalse(items.isEmpty(), "前置条件：t_process_configuration 应有环节配置（先跑 02 脚本）");
        ProcessConfig item = items.get(0);
        Map<String, Object> payload = new HashMap<>();
        payload.put("lcId", item.getId());
        payload.put("lcName", item.getName());
        payload.put("lcqk", "进行中");
        payload.put("lckssj", "2026-03-02");
        payload.put("lrr", "测试录入人");
        return facilityAdminService.saveProcess(facilityId, item.getId(), payload, null);
    }

    /** 类型清单的编码序列（便于整体比对顺序） */
    private static List<String> codesOf(List<Map<String, String>> types) {
        return types.stream().map(m -> m.get("value")).collect(Collectors.toList());
    }

    /** 类型清单的中文名序列 */
    private static List<String> textsOf(List<Map<String, String>> types) {
        return types.stream().map(m -> m.get("text")).collect(Collectors.toList());
    }

    /** 取某个编码对应的中文名（不存在返回 null） */
    private static String nameOf(List<Map<String, String>> types, String code) {
        return types.stream().filter(m -> code.equals(m.get("value")))
                .map(m -> m.get("text")).findFirst().orElse(null);
    }

    // ==================================================================
    // 一、两套材料清单
    // ==================================================================

    /**
     * ★ 宗地与配套必须是两套不同的清单，编码/顺序/名称逐字对齐 P1-08 报告图 3。
     *
     * <p>顺序是业务目录顺序（审批流程），不是字典项插入顺序的巧合 ——
     * 所以这里按**整个列表**比对，而不是只断言「包含某几个名字」。
     */
    @Test
    public void landAndFacilityUseDifferentTypeSets() {
        List<Map<String, String>> landTypes = attachmentService.allowedFileTypes("land");
        List<Map<String, String>> facilityTypes = attachmentService.allowedFileTypes("facility");

        assertEquals(4, landTypes.size(),
                "宗地应为 4 类（报告图 3：01 土地整理计划 / 02 配套情况函 / 03 配套筹备函 / "
                        + "04 出让宗地图形数据（shp）），实际：" + codesOf(landTypes));
        assertEquals(Arrays.asList("01", "02", "03", "04"),
                codesOf(landTypes), "宗地编码必须是连续的 01~04");
        assertEquals(Arrays.asList("土地整理计划", "配套情况函", "配套筹备函", "出让宗地图形数据（shp）"),
                textsOf(landTypes), "宗地名称与顺序必须逐字对齐报告图 3");

        assertEquals(13, facilityTypes.size(),
                "配套应为 13 类（报告图 3），实际：" + codesOf(facilityTypes));
        assertEquals(Arrays.asList("01", "02", "03", "04", "05", "06", "07",
                        "08", "09", "10", "11", "12", "13"),
                codesOf(facilityTypes), "配套编码必须是连续的 01~13");
        assertEquals(Arrays.asList(
                        "配套项目核定用地与地籍调查", "项建批复", "可研批复", "初设及概算批复",
                        "道路规划", "规划工程许可", "专业配套方案", "专业管理意见",
                        "规划用地许可与划拨手续办理", "施工许可", "不动产登记",
                        "竣工文件", "移交文件"),
                textsOf(facilityTypes), "配套名称与顺序必须逐字对齐报告图 3");

        // 三条「报告图 3 特征位」单独钉死：整改前它们分别是 08 / 05 / 13 的名字或位置
        assertEquals("配套项目核定用地与地籍调查", nameOf(facilityTypes, "01"),
                "报告图 3 里「核定用地与地籍调查」是第 1 项（整改前在 08）");
        assertEquals("专业配套方案", nameOf(facilityTypes, "07"),
                "报告图 3 里「专业配套方案」是第 7 项（宗地侧原「配套方案」的内容也并入这里）");
        assertEquals("移交文件", nameOf(facilityTypes, "13"),
                "报告图 3 里「移交文件」是最后一项，且与「竣工文件」是两个独立类型");
        assertEquals("竣工文件", nameOf(facilityTypes, "12"));

        // ★ 迁移遗留的 99「其他（历史）」不进任何一套白名单：新数据不允许再落到历史桶
        assertFalse(codesOf(landTypes).contains("99"), "宗地上传白名单里不应出现 99");
        assertFalse(codesOf(facilityTypes).contains("99"), "配套上传白名单里不应出现 99");
    }

    /** 不传 bizType 时退回宗地那套，不能返回空列表（否则前端下拉是空的） */
    @Test
    public void missingBizTypeFallsBackToLandSet() {
        assertEquals(4, attachmentService.allowedFileTypes(null).size());
        assertEquals(4, attachmentService.allowedFileTypes("").size());
        assertEquals(4, attachmentService.allowedFileTypes("unexpected").size());
    }

    /**
     * ★ 环节进度跟随挂靠侧 = 配套那 13 类。
     *
     * <p>整改前是「环节统一用宗地 5 类」，与挂靠侧不一致：同一份「项建批复」
     * 在环节里叫一种名字、在配套详情里叫另一种 —— 这就是 P1-08 投诉的
     * 「材料类型与归属类型不匹配」。
     *
     * <p>这里走真实链路：建宗地 → 建配套 → 登记一个环节 → 给环节传附件，
     * 断言环节用的清单与配套完全一致，且树上的中文名是配套侧的名字。
     */
    @Test
    public void processFollowsFacilityTypeSet() {
        assertEquals(codesOf(attachmentService.allowedFileTypes("facility")),
                codesOf(attachmentService.allowedFileTypes("process")),
                "环节与配套必须用同一套材料目录（环节挂在配套项目下）");

        String facilityId = createFacility("P");
        String processId = createProcess(facilityId);
        // 环节的 01 必须是配套侧的「配套项目核定用地与地籍调查」，不是宗地的「土地整理计划」
        save("process", processId, TEST_DATA_CRZDBH_PREFIX + "TREE-P", "01", "核定用地.pdf", 20);

        AttachmentTreeVO tree = attachmentService.tree("process", processId);
        assertEquals(1, tree.getTypeCount().intValue());
        assertEquals("配套项目核定用地与地籍调查", tree.getGroups().get(0).getFileTypeName(),
                "环节的 01 必须显示配套清单里的名字，不能显示宗地的「土地整理计划」");
    }

    /**
     * ★ 上传时旧码被拒（本项目采取的归一策略 = 「拒绝」而不是「按映射自动改写」）。
     *
     * <p>口径说明：旧码里有几个（宗地的 01~04、配套的 01~13）在数值上与新码重叠，
     * 光看数值无法分辨「用户点的是新 03（配套筹备函）还是旧 03（配套情况函）」
     * —— 这类请求只能按新语义落库（前后端同源后已不存在旧码调用方）。
     * 因此这里的断言只覆盖**能确定是旧码**的那几种：数值已不在新集合里的，
     * 例如宗地旧 05（出让宗地图形数据，整改后是 04）、配套 14 / 99。
     */
    @Test
    public void legacyTypesAreRejectedOnUpload() {
        String landId = createLand("X");
        // 宗地旧 05（出让宗地图形数据）在新目录里不存在了（新编码是 04）
        JeecgBootException ex = assertThrows(JeecgBootException.class, () ->
                save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-X", "05", "a.pdf", 10));
        assertTrue(ex.getMessage().contains("不在允许的范围"),
                "应提示类型不在允许范围，实际：" + ex.getMessage());
        // 迁移用的 99 历史桶同样不允许新上传（只用于历史回显）
        assertThrows(JeecgBootException.class, () ->
                save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-X", "99", "b.pdf", 10));
        // 新目录的 04 是合法的（出让宗地图形数据（shp））
        assertNotNull(save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-X", "04", "ok.pdf", 10));

        String facilityId = createFacility("Y");
        // 配套只有 01~13
        assertThrows(JeecgBootException.class, () ->
                save("facility", facilityId, TEST_DATA_CRZDBH_PREFIX + "TREE-FY", "14", "c.pdf", 10));
        assertNotNull(save("facility", facilityId, TEST_DATA_CRZDBH_PREFIX + "TREE-FY", "12", "ok2.pdf", 10));
    }

    /**
     * ★ 迁移到 99「其他（历史）」的历史附件不得丢失。
     *
     * <p>这类行的码值不在任何一套清单里（写侧也会拒绝），但必须仍然出现在树里，
     * 名字显示为「其他（历史）」，且排在清单项之后 —— 否则用户会以为文件丢了，
     * 而 P1-08 的验收口径是「历史附件仍能按正确类别查看和下载，不得丢弃附件」。
     */
    @Test
    public void legacyBucketStillVisitedInTree() {
        String landId = createLand("L99");
        // 正常的一类 + 一条历史遗留（模拟 15_attachment_type_remap.sql 迁过来的行）
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-L99", "01", "正常.pdf", 10);
        LandAttachment legacy = new LandAttachment()
                .setBizType("land")
                .setBizId(landId)
                .setBizKey(TEST_DATA_CRZDBH_PREFIX + "TREE-L99")
                .setFileType("99")
                .setFileName("历史配套方案.pdf")
                .setFileExt("pdf")
                .setFileSize(30L)
                .setStoreType("local")
                .setStorePath("land/attachment/2026/10/历史配套方案.pdf")
                .setDownloadCount(0)
                .setDelFlag(0);
        attachmentMapper.insert(legacy);

        AttachmentTreeVO tree = attachmentService.tree("land", landId);
        assertEquals(2, tree.getTotalFiles().intValue(), "历史附件必须计入总数（不得丢弃）");
        assertEquals(2, tree.getTypeCount().intValue());
        assertEquals("01", tree.getGroups().get(0).getFileType(), "清单内的类型仍排在前面");
        AttachmentTreeVO.TypeGroup legacyGroup = tree.getGroups().get(1);
        assertEquals("99", legacyGroup.getFileType(), "清单外的码值追加在最后，不丢文件");
        assertEquals("其他（历史）", legacyGroup.getFileTypeName(),
                "历史桶要有可读名称，否则界面显示成一个裸码值");
        assertEquals("历史配套方案.pdf", legacyGroup.getFiles().get(0).getFileName());
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
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-G", "03", "筹备函.pdf", 300);

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
        // 02 = 配套情况函（整改后的编码；整改前 02 是「配套方案」）
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-E", "02", "配套情况函.pdf", 10);

        AttachmentTreeVO tree = attachmentService.tree("land", landId);
        assertEquals(1, tree.getTypeCount().intValue(), "只用了 1 个类型");
        assertEquals("02", tree.getGroups().get(0).getFileType());
        // 其余 3 类都没有文件，不应出现
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
        // 「配套筹备函」是 03、「土地整理计划」是 01；故意让 03 的文件更多
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
        // 配套的 01 是「配套项目核定用地与地籍调查」（报告图 3 第 1 项）；
        // 宗地的 01 是「土地整理计划」—— 两套 01 的含义完全不同
        save("facility", facilityId, TEST_DATA_CRZDBH_PREFIX + "TREE-FT", "01", "核定用地.pdf", 50);

        AttachmentTreeVO tree = attachmentService.tree("facility", facilityId);
        assertEquals(1, tree.getTypeCount().intValue());
        assertEquals("配套项目核定用地与地籍调查", tree.getGroups().get(0).getFileTypeName(),
                "配套的 01 必须显示配套清单里的名字，不能显示宗地的「土地整理计划」");
        // 列表接口的展示字段（enrich）也必须按归属取名，不能退回宗地那套
        assertEquals("配套项目核定用地与地籍调查",
                tree.getGroups().get(0).getFiles().get(0).getFileTypeText(),
                "fileTypeText 必须按 bizType 取名（整改前会串成宗地那一套）");
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

    /** 未知业务类型安全返回空（不能抛异常） */
    @Test
    public void treeByProjectEmptyWhenUnknownType() {
        assertNotNull(attachmentService.treeByProject("land", 200));
        assertTrue(attachmentService.treeByProject("unknown", 200).isEmpty());
    }

    /**
     * ★ bizType 为空 = 附件管理页的「归属类型：全部」。
     *
     * <p>回归：原来 `type == null` 直接返回空列表，导致不带类型时树永远是空的 ——
     * 而前端在不限类型时正是**不带**这个参数，页面就一片空白。
     * 现在应当返回**所有类型**的项目，且每个项目带上自己的 bizType。
     */
    @Test
    public void treeByProjectWithoutBizTypeCoversAllTypes() {
        String landId = createLand("AL");
        save("land", landId, TEST_DATA_CRZDBH_PREFIX + "TREE-AL", "01", "land.pdf", 30);

        List<Map<String, Object>> all = attachmentService.treeByProject(null, 200);
        assertFalse(all.isEmpty(), "不传 bizType 应返回全部类型的项目，而不是空列表");

        // 该项目必须能找到，且带 bizType
        Map<String, Object> hit = all.stream()
                .filter(p -> landId.equals(p.get("bizId")))
                .findFirst().orElse(null);
        assertNotNull(hit, "不传 bizType 时也应包含刚建的宗地项目");
        assertEquals("land", hit.get("bizType"));

        // 与按类型查的结果应一致（同一条项目在两个入口不能算出不同数字）
        Map<String, Object> byType = findProject(attachmentService.treeByProject("land", 200), landId);
        assertNotNull(byType);
        assertEquals(byType.get("totalFiles"), hit.get("totalFiles"));
        assertEquals(byType.get("totalSize"), hit.get("totalSize"));
    }

    /** 项目行必须带 bizType —— 前端「上传到该项目」要靠它决定归属 */
    @Test
    public void treeByProjectRowsCarryBizType() {
        String facilityId = createFacility("BT");
        save("facility", facilityId, TEST_DATA_CRZDBH_PREFIX + "TREE-BT", "01", "f.pdf", 20);

        Map<String, Object> hit = findProject(attachmentService.treeByProject("facility", 200), facilityId);
        assertNotNull(hit, "应包含刚建的配套项目");
        assertEquals("facility", hit.get("bizType"));
        assertNotNull(hit.get("bizKey"), "项目行要有可读名称（界面直接展示它）");
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
