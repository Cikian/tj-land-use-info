package org.jeecg.modules.land.data.attachment;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;
import org.jeecg.modules.land.data.attachment.entity.LandAttachmentDir;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentDirService;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentDirService.AttachmentTreeVO;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentService;
import org.jeecg.modules.land.data.attachment.vo.AttachmentTreeNodeVO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.service.ILandAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附件目录（目录维护 + 附件树）集成测试。
 *
 * <p><b>★ 这些断言为什么重要</b>：
 * <ul>
 *   <li>{@link #ensureDirsCreatesIntermediateLevels()} —— 上传 {@code a/b/c} 文件夹时
 *       不能要求用户先手工建 a 和 a/b，否则「上传文件夹」这个需求等于没做；</li>
 *   <li>{@link #removeDirMovesFilesToParent()} —— 删目录**不能删文件**。
 *       这是本功能里最危险的一处：用户以为在删分类，实际可能丢凭证。</li>
 *   <li>{@link #renameUpdatesDescendantsAndFiles()} —— 目录靠**字符串前缀**关联，
 *       重命名只改自己会导致子树与附件集体「失联」，在树上表现为整块消失。</li>
 *   <li>{@link #treeIncludesDirPathsNotRegisteredInDirTable()} —— 历史数据（附件有
 *       dir_path、目录表没登记）也要能正确落进树，不能要求先跑数据修复脚本。</li>
 * </ul>
 */
public class LandAttachmentDirTest extends LandIntegrationTestBase {

    @Autowired
    private ILandAttachmentDirService dirService;

    @Autowired
    private ILandAttachmentService attachmentService;

    @Autowired
    private ILandAdminService landAdminService;

    @Autowired
    private org.jeecg.modules.land.data.attachment.mapper.LandAttachmentMapper attachmentMapper;

    /** 造一条测试宗地，返回 id */
    private String createLand() {
        LandSaveDTO dto = new LandSaveDTO();
        dto.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "DIR-001");
        dto.setDkmc("目录测试用地");
        dto.setXmfl("区级项目");
        return landAdminService.createLand(dto, null);
    }

    private LandAttachment meta(String storePath, String dirPath) {
        LandAttachment meta = new LandAttachment();
        meta.setFileName("目录测试文件.png");
        meta.setFileExt("png");
        meta.setFileSize(1024L);
        meta.setStoreType("local");
        meta.setStorePath(storePath);
        meta.setDirPath(dirPath);
        return meta;
    }

    /** 把一个附件存进指定目录 */
    private LandAttachment saveFile(String landId, String dirPath, String fileName, long size) {
        LandAttachment meta = meta("facility/attachment/2026/10/" + fileName, dirPath);
        meta.setFileName(fileName);
        meta.setFileSize(size);
        return attachmentService.saveUploaded("land", landId, TEST_DATA_CRZDBH_PREFIX + "DIR-001",
                "05", meta);
    }

    /** 深度优先收集树里的所有节点 */
    private List<AttachmentTreeNodeVO> flatten(List<AttachmentTreeNodeVO> nodes) {
        List<AttachmentTreeNodeVO> all = new ArrayList<>();
        Deque<AttachmentTreeNodeVO> stack = new ArrayDeque<>(nodes);
        while (!stack.isEmpty()) {
            AttachmentTreeNodeVO node = stack.pop();
            all.add(node);
            stack.addAll(node.getChildren());
        }
        return all;
    }

    private List<String> dirPaths(AttachmentTreeVO tree) {
        return flatten(tree.getNodes()).stream()
                .filter(n -> "dir".equals(n.getNodeType()))
                .map(AttachmentTreeNodeVO::getDirPath)
                .collect(Collectors.toList());
    }

    private AttachmentTreeNodeVO findDir(AttachmentTreeVO tree, String path) {
        return flatten(tree.getNodes()).stream()
                .filter(n -> "dir".equals(n.getNodeType()) && path.equals(n.getDirPath()))
                .findFirst().orElse(null);
    }

    // ==================================================================
    // 一、ensureDirs / 新建
    // ==================================================================

    /**
     * ★ 多级路径必须逐级补建：上传「招标文件/2024/合同」文件夹时，
     * 三级目录都要在，否则前端展开不到最深那层。
     */
    @Test
    public void ensureDirsCreatesIntermediateLevels() {
        String landId = createLand();
        int created = dirService.ensureDirs("land", landId, "招标文件/2024/合同", "测试宗地");
        assertEquals(3, created, "三级路径应补建 3 个目录");

        List<LandAttachmentDir> dirs = dirService.listDirs("land", landId);
        List<String> paths = dirs.stream().map(LandAttachmentDir::getDirPath).collect(Collectors.toList());
        assertTrue(paths.contains("招标文件"), "缺少中间层：招标文件");
        assertTrue(paths.contains("招标文件/2024"), "缺少中间层：招标文件/2024");
        assertTrue(paths.contains("招标文件/2024/合同"), "缺少最深层");

        // 父路径与层级也要对
        LandAttachmentDir leaf = dirs.stream()
                .filter(d -> "招标文件/2024/合同".equals(d.getDirPath())).findFirst().orElse(null);
        assertNotNull(leaf);
        assertEquals("招标文件/2024", leaf.getParentPath());
        assertEquals(3, leaf.getDepth().intValue());
        assertEquals("合同", leaf.getDirName());
    }

    /** 重复调用是幂等的（上传同一批文件会反复触发） */
    @Test
    public void ensureDirsIsIdempotent() {
        String landId = createLand();
        assertEquals(2, dirService.ensureDirs("land", landId, "a/b", "K"));
        assertEquals(0, dirService.ensureDirs("land", landId, "a/b", "K"),
                "第二次应为 0（都已存在）");
        assertEquals(1, dirService.ensureDirs("land", landId, "a/b/c", "K"),
                "只缺最深那层时只建 1 个");
    }

    /** 根目录不需要建，也不能在目录表里留记录 */
    @Test
    public void rootPathCreatesNothing() {
        String landId = createLand();
        assertEquals(0, dirService.ensureDirs("land", landId, "", "K"));
        assertEquals(0, dirService.ensureDirs("land", landId, "/", "K"));
        assertTrue(dirService.listDirs("land", landId).isEmpty());
    }

    /** 路径归一：反斜杠 / 重复斜杠 / 首尾斜杠 / 空段 都要收敛成同一种写法 */
    @Test
    public void pathNormalizationCollapsesVariants() {
        String landId = createLand();
        // 浏览器上传文件夹时给的是反斜杠形态（旧版 Edge/IE），必须能吃
        dirService.ensureDirs("land", landId, "\\招标文件\\2024\\", "K");
        assertEquals(0, dirService.ensureDirs("land", landId, "招标文件//2024", "K"),
                "归一后应命中已存在的同一条目录");
        assertEquals(2, dirService.listDirs("land", landId).size(), "不应因写法不同而重复建");
    }

    /** 非法路径必须被拦住，并给出原因（用户明确指定目录时不能静默吞掉） */
    @Test
    public void invalidPathIsRejected() {
        String landId = createLand();
        assertThrows(JeecgBootException.class, () -> dirService.createDir("land", landId, "..", "K"));
        assertThrows(JeecgBootException.class, () -> dirService.createDir("land", landId, "a/../b", "K"));
        assertThrows(JeecgBootException.class, () -> dirService.createDir("land", landId, "a:b", "K"));
        assertThrows(JeecgBootException.class, () -> dirService.createDir("land", landId, "", "K"));
        // 层级过深
        assertThrows(JeecgBootException.class, () -> dirService.createDir("land", landId,
                "1/2/3/4/5/6/7/8/9", "K"));
    }

    // ==================================================================
    // 二、树
    // ==================================================================

    /** 树形结构：父子关系、文件挂在所属目录、统计含子目录累加 */
    @Test
    public void treeBuildsHierarchyAndAggregates() {
        String landId = createLand();
        saveFile(landId, "", "根目录文件.txt", 100);
        saveFile(landId, "招标文件", "招标公告.pdf", 200);
        saveFile(landId, "招标文件/2024", "中标通知.pdf", 300);
        saveFile(landId, "招标文件/2024", "合同扫描.pdf", 400);

        AttachmentTreeVO tree = dirService.tree("land", landId, true);

        // 根目录的文件单独放在 rootFiles（根不是真实目录，不上树）
        assertEquals(1, tree.getRootFiles().size(), "根目录应有 1 个文件");
        assertEquals("根目录文件.txt", tree.getRootFiles().get(0).getFileName());
        // 4 个文件 / 3 个目录（招标文件、招标文件/2024 由附件路径补出，
        // 另外 saveUploaded 会自动补建，所以数量以实际为准）
        assertEquals(4, tree.getTotalFiles().intValue());
        assertEquals(1000L, tree.getTotalSize().longValue());

        AttachmentTreeNodeVO bidding = findDir(tree, "招标文件");
        assertNotNull(bidding, "树上应有 招标文件");
        assertEquals(1, bidding.getDepth().intValue());
        assertFalse(bidding.getEmpty(), "该目录下有文件（含子目录），不应为空");

        AttachmentTreeNodeVO year2024 = findDir(tree, "招标文件/2024");
        assertNotNull(year2024, "树上应有 招标文件/2024");

        // ★ 累加口径：招标文件 = 自己的 1 个 + 子目录的 2 个 = 3
        assertEquals(3, bidding.getFileCount().intValue(),
                "统计应含子目录（否则父节点角标对不上）");
        assertEquals(900L, bidding.getTotalSize().longValue(), "200+300+400");
        assertEquals(2, year2024.getFileCount().intValue());

        // 父子关系：2024 挂到 招标文件 下，而不是平铺在根
        assertTrue(bidding.getChildren().stream()
                        .anyMatch(c -> "招标文件/2024".equals(c.getDirPath())),
                "2024 应是 招标文件的子节点");
        assertFalse(tree.getNodes().stream()
                        .anyMatch(c -> "招标文件/2024".equals(c.getDirPath())),
                "2024 不该同时出现在第一层");
    }

    /** 空目录（没有任何文件）也要在树上，且 empty=true */
    @Test
    public void emptyDirAppearsInTree() {
        String landId = createLand();
        dirService.createDir("land", landId, "待归档", "K");

        AttachmentTreeVO tree = dirService.tree("land", landId, true);
        AttachmentTreeNodeVO node = findDir(tree, "待归档");
        assertNotNull(node, "空目录必须出现在树上（这正是目录要单独建表的原因）");
        assertEquals(0, node.getFileCount().intValue());
        assertTrue(node.getEmpty());
        assertEquals(0, tree.getTotalFiles().intValue());
    }

    /**
     * ★ 历史数据兼容：附件有 dir_path、但目录表里没登记时，
     * 树要把目录**逐级补出来**（否则文件挂不上树、整棵树少一块）。
     *
     * <p>模拟方式：先按正常流程存一个附件（会自动建目录），
     * 再把目录表记录清掉、并直接把附件的 dir_path 改成多级路径 ——
     * 这正是「修复前的老数据」的形态（附件上有路径，目录表还空着）。
     */
    @Test
    public void treeIncludesDirPathsNotRegisteredInDirTable() {
        String landId = createLand();
        LandAttachment saved = saveFile(landId, "临时", "老文件.pdf", 500);
        // 清掉自动建出来的目录记录，让目录表变空
        for (LandAttachmentDir dir : dirService.listDirs("land", landId)) {
            dirService.removeDir("land", landId, dir.getDirPath(), true, null);
        }
        assertEquals(0, dirService.listDirs("land", landId).size(), "目录表应已清空");

        // 直接把附件的 dir_path 写成多级路径：目录表里没有任何登记
        attachmentMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<LandAttachment>lambdaUpdate()
                .eq(LandAttachment::getId, saved.getId())
                .set(LandAttachment::getDirPath, "历史/深层/目录"));
        assertEquals(0, dirService.listDirs("land", landId).size(), "目录表仍应为空");

        AttachmentTreeVO tree = dirService.tree("land", landId, true);
        List<String> paths = dirPaths(tree);
        assertTrue(paths.contains("历史"), "应从未登记的附件路径补出 历史，实际：" + paths);
        assertTrue(paths.contains("历史/深层"), "应逐级补出中间层，实际：" + paths);
        assertTrue(paths.contains("历史/深层/目录"), "应补出最深那层，实际：" + paths);
    }

    /** withFile=false 时只要目录骨架，不带文件节点 */
    @Test
    public void treeWithoutFilesOmitsFileNodes() {
        String landId = createLand();
        saveFile(landId, "有文件", "a.pdf", 10);

        AttachmentTreeVO tree = dirService.tree("land", landId, false);
        List<AttachmentTreeNodeVO> all = flatten(tree.getNodes());
        assertTrue(all.stream().anyMatch(n -> "dir".equals(n.getNodeType())), "目录仍在");
        assertFalse(all.stream().anyMatch(n -> "file".equals(n.getNodeType())),
                "withFile=false 时不应有文件节点");
        // 统计仍然照算（角标要显示这一支有多少东西）
        assertEquals(1, findDir(tree, "有文件").getFileCount().intValue());
    }

    // ==================================================================
    // 三、重命名
    // ==================================================================

    /**
     * ★ 重命名必须连带：① 子目录路径前缀；② 附件 dir_path 前缀。
     * 只改目录自己会让子树与附件集体失联（树上表现为整块消失）。
     */
    @Test
    public void renameUpdatesDescendantsAndFiles() {
        String landId = createLand();
        saveFile(landId, "旧名/子目录", "a.pdf", 100);
        saveFile(landId, "旧名", "b.pdf", 200);

        dirService.renameDir("land", landId, "旧名", "新名", "K", null);

        List<String> paths = dirService.listDirs("land", landId).stream()
                .map(LandAttachmentDir::getDirPath).collect(Collectors.toList());
        assertTrue(paths.contains("新名"), "目录自身应改名，实际：" + paths);
        assertTrue(paths.contains("新名/子目录"), "子目录前缀应一起改，实际：" + paths);
        assertFalse(paths.contains("旧名"), "不应残留旧路径");
        assertFalse(paths.contains("旧名/子目录"), "不应残留旧子路径");

        // 附件也要跟着走，否则树上新名目录下会空掉
        AttachmentTreeVO tree = dirService.tree("land", landId, true);
        AttachmentTreeNodeVO renamed = findDir(tree, "新名");
        AttachmentTreeNodeVO renamedChild = findDir(tree, "新名/子目录");
        assertNotNull(renamedChild, "子目录应挂在新名下");
        // 新名：自己 1 个（b.pdf）+ 子目录 1 个（a.pdf）= 2；子目录：1
        assertEquals(2, renamed.getFileCount().intValue(),
                "新名应含自己与子目录的文件（子树累加）");
        assertEquals(1, renamedChild.getFileCount().intValue());
        assertNull(findDir(tree, "旧名"), "旧目录不应还在树上");
    }

    /** 同级下改名撞已有目录要拒绝（否则唯一键会抛数据库异常，提示很难懂） */
    @Test
    public void renameRejectsExistingSiblingName() {
        String landId = createLand();
        dirService.createDir("land", landId, "甲", "K");
        dirService.createDir("land", landId, "乙", "K");
        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> dirService.renameDir("land", landId, "甲", "乙", "K", null));
        assertTrue(ex.getMessage().contains("已存在"), "应提示同级已存在，实际：" + ex.getMessage());
    }

    /** 重命名不存在的目录要报错，而不是悄悄建一个 */
    @Test
    public void renameMissingDirIsRejected() {
        String landId = createLand();
        assertThrows(JeecgBootException.class,
                () -> dirService.renameDir("land", landId, "不存在", "新名", "K", null));
    }

    // ==================================================================
    // 四、删除（★ 不删文件）
    // ==================================================================

    /**
     * ★ 最要紧的一条：删目录**不删文件**，文件上移到父目录。
     * 用户以为在删分类，实际丢凭证是不可接受的。
     */
    @Test
    public void removeDirMovesFilesToParent() {
        String landId = createLand();
        LandAttachment file = saveFile(landId, "待删/子目录", "重要凭证.pdf", 999);

        dirService.removeDir("land", landId, "待删", true, null);

        // 目录没了
        List<String> paths = dirService.listDirs("land", landId).stream()
                .map(LandAttachmentDir::getDirPath).collect(Collectors.toList());
        assertFalse(paths.contains("待删"), "目录应已删除");
        assertFalse(paths.contains("待删/子目录"), "子目录应一并删除");

        // 文件还在，且回到了「待删」的父目录（根）—— 注意它的路径是**去掉 待删 前缀**后的
        // 子目录，而不是根目录：文件本来在 待删/子目录，把 待删 这一段摘掉就是 子目录。
        // 这正是「目录只是分类，摘掉一层分类不等于把文件也挪平」的语义。
        LandAttachment reloaded = attachmentService.queryById(file.getId());
        assertNotNull(reloaded, "★ 删目录绝不能删文件");
        assertEquals("子目录", reloaded.getDirPath(),
                "去掉被删目录那一段后应剩下 子目录，且不能留下前导斜杠");
        assertFalse(reloaded.getDirPath().startsWith("/"),
                "★ 不能出现前导斜杠：那会让这个文件匹配不上任何目录节点（树上看不到它）");
    }

    /**
     * ★ 单独固定「文件直接位于被删目录下」这一种情况：
     * 去掉前缀后应该是**根目录（空串）**，而不是 {@code "/"}（那是个不存在目录的畸形路径）。
     */
    @Test
    public void removeDirMovesDirectChildFilesToRoot() {
        String landId = createLand();
        LandAttachment file = saveFile(landId, "整层", "根下文件.pdf", 100);

        dirService.removeDir("land", landId, "整层", true, null);

        LandAttachment reloaded = attachmentService.queryById(file.getId());
        assertNotNull(reloaded);
        assertEquals("", reloaded.getDirPath(),
                "去掉唯一那一段后应落回根目录（空串），不能是 \"/\"");
        AttachmentTreeVO tree = dirService.tree("land", landId, true);
        assertEquals(1, tree.getRootFiles().size(), "文件应出现在根目录下");
    }

    /** 有子目录但没勾「连同子目录」时要拒绝，并说清有几个 */
    @Test
    public void removeDirWithoutRecursiveIsRejectedWhenHasChildren() {
        String landId = createLand();
        dirService.createDir("land", landId, "父/子", "K");
        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> dirService.removeDir("land", landId, "父", false, null));
        assertTrue(ex.getMessage().contains("子目录"), "应提示存在子目录，实际：" + ex.getMessage());
        assertEquals(2, dirService.listDirs("land", landId).size(), "拒绝时不应删掉任何东西");
    }

    /** 叶子目录直接删（不需要 recursive） */
    @Test
    public void removeLeafDirWorksWithoutRecursive() {
        String landId = createLand();
        dirService.createDir("land", landId, "叶子", "K");
        assertEquals(1, dirService.removeDir("land", landId, "叶子", false, null));
        assertTrue(dirService.listDirs("land", landId).isEmpty());
    }

    // ==================================================================
    // 五、与附件保存联动
    // ==================================================================

    /**
     * ★ 上传文件夹时前端只带 dirPath，服务端要顺手把目录建出来 ——
     * 不能要求用户先去「新建目录」再上传。
     */
    @Test
    public void savingFileAutoCreatesItsDirectory() {
        String landId = createLand();
        saveFile(landId, "招标文件/2024", "中标通知.pdf", 100);

        List<String> paths = dirService.listDirs("land", landId).stream()
                .map(LandAttachmentDir::getDirPath).collect(Collectors.toList());
        assertTrue(paths.contains("招标文件"), "保存附件应自动建父目录");
        assertTrue(paths.contains("招标文件/2024"), "保存附件应自动建目标目录");
    }

    /** dirPath 里带反斜杠（浏览器文件夹上传的形态）也要被归一后落库 */
    @Test
    public void savingFileNormalizesBackslashDirPath() {
        String landId = createLand();
        LandAttachment saved = saveFile(landId, "招标\\2024", "a.pdf", 10);
        assertEquals("招标/2024", saved.getDirPath(), "反斜杠应归一为正斜杠");
    }

    /** 不传 dirPath 时落在根目录（空串），保持与既有数据一致 */
    @Test
    public void savingFileWithoutDirPathGoesToRoot() {
        String landId = createLand();
        LandAttachment saved = attachmentService.saveUploaded("land", landId,
                TEST_DATA_CRZDBH_PREFIX + "DIR-001", "05",
                meta("facility/attachment/2026/10/a.pdf", null));
        assertEquals("", saved.getDirPath(), "未指定目录应落根目录（空串，不是 null）");
        assertTrue(dirService.listDirs("land", landId).isEmpty(), "根目录不建目录记录");
    }

    /** 不同业务对象的同名目录互不干扰 */
    @Test
    public void dirsAreIsolatedPerBusinessObject() {
        String landId = createLand();
        LandSaveDTO other = new LandSaveDTO();
        other.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "DIR-002");
        other.setDkmc("目录测试用地二");
        other.setXmfl("区级项目");
        String otherId = landAdminService.createLand(other, null);

        dirService.createDir("land", landId, "合同", "K1");
        // 同名目录在另一个业务对象下必须能建
        assertNotNull(dirService.createDir("land", otherId, "合同", "K2"));

        assertEquals(1, dirService.listDirs("land", landId).size());
        assertEquals(1, dirService.listDirs("land", otherId).size());
    }
}
