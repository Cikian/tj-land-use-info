package org.jeecg.modules.land.archive;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.jeecg.JeecgSystemApplication;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.archive.document.dto.DocArchiveDTO;
import org.jeecg.modules.land.archive.document.dto.DocHandleDTO;
import org.jeecg.modules.land.archive.document.dto.DocQueryDTO;
import org.jeecg.modules.land.archive.document.entity.DocAttachment;
import org.jeecg.modules.land.archive.document.entity.DocReceive;
import org.jeecg.modules.land.archive.document.entity.DocReceiveFlow;
import org.jeecg.modules.land.archive.document.entity.DocSend;
import org.jeecg.modules.land.archive.document.service.IDocReceiveService;
import org.jeecg.modules.land.archive.document.service.IDocSendService;
import org.jeecg.modules.land.archive.dto.ArchiveQueryDTO;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.entity.ArchiveCategory;
import org.jeecg.modules.land.archive.entity.ArchiveFile;
import org.jeecg.modules.land.archive.service.IArchiveCategoryService;
import org.jeecg.modules.land.archive.service.IArchiveFileService;
import org.jeecg.modules.land.archive.service.IArchiveService;
import org.jeecg.modules.land.archive.vo.ArchiveStatVO;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.service.IFacilityService;
import org.jeecg.modules.land.data.service.ILandService;
import org.jeecg.modules.land.data.vo.FacilityOptionVO;
import org.jeecg.modules.land.data.vo.LandOptionVO;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 档案管理 + 收发文管理（方案 2.3.2 第 2/3/4/5/9 项）服务层集成测试。
 *
 * <p>直接调用 Service，绕过登录与权限校验；整个测试类带事务，结束后回滚，
 * 不会污染 tj-jyxyd 里的业务数据（宗地 847 条、配套 1433 条、类别 58 个都保持原样）。
 *
 * <p>依赖本机 MySQL（127.0.0.1:3306/tj-jyxyd，见 application-dev.yml）与 Redis。
 *
 * <p>为什么要写这组测试：本项目之前的档案类别模块就是靠集成测试才发现
 * ① MyBatis 一级缓存导致建树串味、② 探测 SQL 被 JSqlParser 判为语法错误
 * 这类「只看业务用例完全看不出来」的问题。本模块的 Mapper XML 里有
 * GROUP_CONCAT、EXISTS 子查询、CAST+SUBSTRING 等较复杂的 SQL，
 * 必须真实跑一次才能确认 JSqlParser 与 MySQL 5.7 都接受。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = JeecgSystemApplication.class)
@Transactional
public class ArchiveAndDocumentServiceTest {

    @Autowired
    private ILandService landService;

    @Autowired
    private IFacilityService facilityService;

    @Autowired
    private IArchiveService archiveService;

    @Autowired
    private IArchiveFileService archiveFileService;

    @Autowired
    private IArchiveCategoryService categoryService;

    @Autowired
    private IDocReceiveService docReceiveService;

    @Autowired
    private IDocSendService docSendService;

    // ==================================================================
    // 登录上下文
    //
    // 收文的流转校验（「不能转办给自己」「当前文件的流程不属于当前用户」）依赖
    // shiro 的当前登录用户。测试里没有真实请求，因此用 Mockito 造一个 Subject
    // 绑定到 ThreadContext —— SecurityUtils.getSubject() 会优先取线程绑定的 Subject，
    // 不需要 SecurityManager 与认证流程。
    //
    // 用到的两个账号取自本机 sys_user（status=1, del_flag=0）：admin（管理员）、zhangsan（张三）。
    // ==================================================================

    private static final String ME = "admin";
    private static final String ME_NAME = "管理员";
    private static final String OTHER = "zhangsan";
    private static final String OTHER_NAME = "张三";

    @Before
    public void bindLoginUser() {
        bindUser(ME, ME_NAME);
    }

    @After
    public void unbindLoginUser() {
        ThreadContext.unbindSubject();
    }

    private void bindUser(String username, String realname) {
        LoginUser user = new LoginUser();
        user.setUsername(username);
        user.setRealname(realname);
        Subject subject = Mockito.mock(Subject.class);
        Mockito.when(subject.getPrincipal()).thenReturn(user);
        ThreadContext.bind(subject);
    }

    /** 断言某个动作抛出 JeecgBootException 且提示语包含指定片段 */
    private void assertBusinessError(Runnable action, String expectedFragment) {
        try {
            action.run();
            Assert.fail("应抛出业务异常（期望提示包含「" + expectedFragment + "」）");
        } catch (JeecgBootException e) {
            Assert.assertNotNull("业务异常应带提示语", e.getMessage());
            Assert.assertTrue("提示语应包含「" + expectedFragment + "」，实际=" + e.getMessage(),
                    e.getMessage().contains(expectedFragment));
        }
    }

    // ==================================================================
    // 一、宗地 / 配套项目（两级联动的数据基础）
    // ==================================================================

    @Test
    public void testLandOptionsCarryFacilityCount() {
        List<LandOptionVO> options = landService.queryOptions(null, null, 5);
        Assert.assertFalse("宗地下拉不应为空（t_land 已迁移 847 条）", options.isEmpty());
        for (LandOptionVO option : options) {
            Assert.assertNotNull("宗地ID不能为空", option.getId());
            Assert.assertNotNull("出让宗地编号不能为空", option.getCrzdbh());
            Assert.assertNotNull("应带上该宗地下的配套项目数量", option.getFacilityCount());
        }
    }

    @Test
    public void testLandKeywordSearch() {
        LandOptionVO first = landService.queryOptions(null, null, 1).get(0);
        List<LandOptionVO> hit = landService.queryOptions(first.getCrzdbh(), null, 10);
        Assert.assertFalse("按出让宗地编号应能搜到", hit.isEmpty());
        Assert.assertTrue("命中的第一条应与关键字一致",
                hit.stream().anyMatch(item -> first.getCrzdbh().equals(item.getCrzdbh())));
    }

    @Test
    public void testFacilityOptionsAreScopedByLand() {
        LandOptionVO land = null;
        for (LandOptionVO item : landService.queryOptions(null, null, 50)) {
            if (item.getFacilityCount() != null && item.getFacilityCount() > 0) {
                land = item;
                break;
            }
        }
        Assert.assertNotNull("应至少有一个宗地挂着配套项目（实测关联率 95.8%）", land);

        List<FacilityOptionVO> facilities = facilityService.queryOptions(land.getCrzdbh(), null, 20);
        Assert.assertFalse("该宗地下应能查到配套项目", facilities.isEmpty());
        for (FacilityOptionVO facility : facilities) {
            Assert.assertEquals("配套项目必须属于所选宗地", land.getCrzdbh(), facility.getCrzdbh());
            Assert.assertNotNull("配套项目名称不能为空", facility.getPtxmmc());
        }
    }

    @Test
    public void testFacilityOptionsWithoutLandReturnsEmpty() {
        Assert.assertTrue("没选宗地时不应返回全量配套项目",
                facilityService.queryOptions(null, null, 20).isEmpty());
    }

    // ==================================================================
    // 二、档案增删改查
    // ==================================================================

    /** 取一个可用的「叶子类别」 */
    private ArchiveCategory anyLeafCategory() {
        for (ArchiveCategory node : categoryService.queryTree(null, 1, null)) {
            ArchiveCategory leaf = findLeaf(node);
            if (leaf != null) {
                return leaf;
            }
        }
        throw new IllegalStateException("测试库中没有可用的叶子类别");
    }

    private ArchiveCategory findLeaf(ArchiveCategory node) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return node;
        }
        for (ArchiveCategory child : node.getChildren()) {
            ArchiveCategory leaf = findLeaf(child);
            if (leaf != null) {
                return leaf;
            }
        }
        return null;
    }

    /** 找一个有配套项目的宗地 + 配套项目 */
    private Facility anyFacility() {
        for (LandOptionVO item : landService.queryOptions(null, null, 50)) {
            if (item.getFacilityCount() != null && item.getFacilityCount() > 0) {
                List<FacilityOptionVO> list = facilityService.queryOptions(item.getCrzdbh(), null, 1);
                if (!list.isEmpty()) {
                    return facilityService.queryById(list.get(0).getId());
                }
            }
        }
        throw new IllegalStateException("测试库中没有可用的配套项目");
    }

    /** 构造一个「档案 + 一个卷内文件」的入参 */
    private Archive buildArchive(String archiveName) {
        Facility facility = anyFacility();
        ArchiveCategory category = anyLeafCategory();

        ArchiveFile file = new ArchiveFile()
                .setCategoryId(category.getId())
                .setFileName("规划许可证.pdf")
                .setFileTitle("规划许可证")
                .setFileExt("pdf")
                .setFileSize(1024L * 1024L)
                .setStorePath("/archive/2026/09/test-file.pdf")
                .setStatus(ArchiveFile.STATUS_ARCHIVED);

        Archive archive = new Archive()
                .setArchiveName(archiveName)
                .setArchiveType(Archive.TYPE_ELECTRONIC)
                .setSecretLevel("一般")
                .setStatus(Archive.STATUS_PENDING)
                .setFacilityId(facility.getId())
                .setPtxmmc(facility.getPtxmmc())
                .setCrzdbh(facility.getCrzdbh())
                .setResponsibleUser("测试员")
                .setFiles(new ArrayList<>(java.util.Collections.singletonList(file)));

        Land land = landService.queryByCrzdbh(facility.getCrzdbh());
        if (land != null) {
            archive.setLandId(land.getId());
        }
        return archive;
    }

    @Test
    public void testArchiveNumberGenerationFollowsRule() {
        String no = archiveService.generateArchiveNo(2026);
        Assert.assertTrue("档案号应形如 DA-2026-0001，实际=" + no, no.matches("^DA-2026-\\d{4}$"));
    }

    @Test
    public void testCreateQueryEditDeleteArchive() {
        Archive archive = buildArchive("测试档案-规划许可证");
        String id = archiveService.createArchive(archive, archive.getFiles());
        Assert.assertNotNull("新增应返回档案ID", id);

        // 档案号自动生成
        Archive saved = archiveService.queryDetail(id);
        Assert.assertNotNull(saved);
        Assert.assertTrue("自动生成的档案号应形如 DA-yyyy-NNNN，实际=" + saved.getArchiveNo(),
                saved.getArchiveNo().matches("^DA-\\d{4}-\\d{4}$"));

        // 列表查询（覆盖 GROUP_CONCAT 聚合与查询条件）
        ArchiveQueryDTO query = new ArchiveQueryDTO();
        query.setArchiveNo(saved.getArchiveNo());
        IPage<Archive> page = archiveService.queryPage(query);
        Assert.assertEquals("按档案号应能精确命中 1 条", 1, page.getTotal());
        Assert.assertNotNull("列表里的档案类别应由卷内文件聚合而来",
                page.getRecords().get(0).getCategoryNames());

        // 按文件名子查询
        ArchiveQueryDTO byFile = new ArchiveQueryDTO();
        byFile.setFileName("规划许可证");
        Assert.assertTrue("按文件名应能检索到", archiveService.queryPage(byFile).getTotal() >= 1);

        // 按配套项目关联
        ArchiveQueryDTO byFacility = new ArchiveQueryDTO();
        byFacility.setFacilityId(saved.getFacilityId());
        Assert.assertTrue("按配套项目应能检索到", archiveService.queryPage(byFacility).getTotal() >= 1);

        // 详情里的文件应有类别全路径名与可读大小
        Assert.assertEquals("卷内文件应为 1 个", 1, saved.getFiles().size());
        ArchiveFile detailFile = saved.getFiles().get(0);
        Assert.assertNotNull("文件的类别全路径名应由服务端冗余写入", detailFile.getCategoryName());
        Assert.assertTrue("类别全路径名应包含分隔符", detailFile.getCategoryName().contains("/"));
        Assert.assertNotNull("文件应带上可读大小", detailFile.getReadableSize());
        Assert.assertNotNull("文件应带上可访问 URL", detailFile.getUrl());

        // 统计（覆盖 CASE WHEN / COUNT DISTINCT / LIMIT 等多条统计 SQL）
        ArchiveStatVO stat = archiveService.queryStat(byFacility, 10);
        Assert.assertTrue("统计总数应 >= 1", stat.getTotal() >= 1);
        Assert.assertTrue("卷内文件数应 >= 1", stat.getFileTotal() >= 1);
        Assert.assertFalse("按类别统计不应为空（含 GROUP_CONCAT 归并到顶级类别）", stat.getByCategory().isEmpty());
        Assert.assertFalse("按项目统计不应为空", stat.getByProject().isEmpty());

        // 状态流转：没有文件时不能置为已归档（反向用例见下），这里有文件所以可以
        archiveService.changeStatus(id, Archive.STATUS_ARCHIVED);
        Assert.assertEquals(Archive.STATUS_ARCHIVED, archiveService.queryDetail(id).getStatus());

        // 操作记录应至少有「新增」与「归档」两条
        Assert.assertTrue("操作记录应 >= 2 条", archiveService.queryLogs(id).size() >= 2);

        // 编辑：改名称 + 改文件题名
        Archive edit = new Archive()
                .setId(id)
                .setArchiveName("测试档案-改名后")
                .setArchiveNo(saved.getArchiveNo())
                .setStatus(Archive.STATUS_ARCHIVED)
                .setFiles(new ArrayList<>(saved.getFiles()));
        archiveService.updateArchive(edit, edit.getFiles());
        Assert.assertEquals("测试档案-改名后", archiveService.queryDetail(id).getArchiveName());

        // 删除
        archiveService.deleteArchive(id);
        Assert.assertNull("删除后详情应为 null", archiveService.queryDetail(id));
    }

    @Test
    public void testArchiveRequiresAtLeastOneFile() {
        Archive archive = buildArchive("测试档案-无文件");
        try {
            archiveService.createArchive(archive, new ArrayList<>());
            Assert.fail("手工录入的档案没有文件时应被拒绝");
        } catch (JeecgBootException e) {
            Assert.assertTrue("提示语应说明需要上传文件", e.getMessage().contains("文件"));
        }
    }

    @Test
    public void testArchiveRejectsNonLeafCategory() {
        Archive archive = buildArchive("测试档案-非叶子类别");
        // 找一个非叶子类别
        ArchiveCategory root = categoryService.queryTree(null, 1, null).get(0);
        Assert.assertNotNull("根类别应存在", root);
        archive.getFiles().get(0).setCategoryId(root.getId());
        try {
            archiveService.createArchive(archive, archive.getFiles());
            Assert.fail("非叶子类别应被拒绝");
        } catch (JeecgBootException e) {
            Assert.assertTrue("提示语应说明要选末级类别，实际=" + e.getMessage(),
                    e.getMessage().contains("下级") || e.getMessage().contains("末"));
        }
    }

    @Test
    public void testArchiveNoDuplicateIsRejected() {
        Archive first = buildArchive("测试档案-重号A");
        String id = archiveService.createArchive(first, first.getFiles());
        String no = archiveService.queryDetail(id).getArchiveNo();

        Archive second = buildArchive("测试档案-重号B");
        second.setArchiveNo(no);
        try {
            archiveService.createArchive(second, second.getFiles());
            Assert.fail("重复档案号应被拒绝");
        } catch (JeecgBootException e) {
            Assert.assertTrue("提示语应说明档案号已存在", e.getMessage().contains("已存在"));
        }
    }

    @Test
    public void testArchiveExportZipProducesFile() {
        Archive archive = buildArchive("测试档案-导出");
        archiveService.createArchive(archive, archive.getFiles());

        ArchiveQueryDTO query = new ArchiveQueryDTO();
        query.setFacilityId(archive.getFacilityId());
        File zip = archiveService.exportZip(query, "测试员");
        try {
            Assert.assertNotNull("导出应返回临时文件", zip);
            Assert.assertTrue("导出的 ZIP 不应为空", zip.exists() && zip.length() > 0);
        } finally {
            if (zip != null && zip.exists()) {
                Assert.assertTrue("临时文件应可删除", zip.delete());
            }
        }
    }

    @Test
    public void testCategoryWithArchiveFileCannotBeRemoved() {
        Archive archive = buildArchive("测试档案-类别占用");
        archiveService.createArchive(archive, archive.getFiles());
        String categoryId = archive.getFiles().get(0).getCategoryId();

        try {
            categoryService.deleteCategory(categoryId);
            Assert.fail("类别下已有档案文件时不应允许移除");
        } catch (JeecgBootException e) {
            Assert.assertTrue("提示语应说明类别下存在档案，实际=" + e.getMessage(),
                    e.getMessage().contains("档案"));
        }
    }

    // ==================================================================
    // 三、收文：登记 → 转办 → 退回 → 办结 → 归档
    // ==================================================================

    private DocReceive buildReceive(String title) {
        Facility facility = anyFacility();
        DocReceive doc = new DocReceive()
                .setDocTitle(title)
                .setDocType("函")
                .setFromDept("天津市住房和城乡建设委员会")
                .setReceiveDate(new Date())
                .setUrgency("普通")
                .setSecretLevel("一般")
                .setFacilityId(facility.getId())
                .setCurrentHandler("admin");
        DocAttachment attachment = new DocAttachment()
                .setFileName("来文扫描件.pdf")
                .setFileExt("pdf")
                .setFileSize(2048L)
                .setStorePath("/receive/2026/09/test.pdf")
                .setStoreType("local");
        doc.setAttachments(new ArrayList<>(java.util.Collections.singletonList(attachment)));
        return doc;
    }

    @Test
    public void testReceiveRegisterCreatesFlowRecords() {
        DocReceive doc = buildReceive("测试收文-登记");
        String id = docReceiveService.createDoc(doc);
        Assert.assertNotNull(id);

        DocReceive saved = docReceiveService.queryDetail(id);
        Assert.assertTrue("登记号应形如 SW-yyyy-NNNN，实际=" + saved.getDocNo(),
                saved.getDocNo().matches("^SW-\\d{4}-\\d{4}$"));
        Assert.assertEquals("登记时应把承办人写成当前处理人", "admin", saved.getCurrentHandler());
        Assert.assertEquals(DocReceive.STATUS_PENDING, saved.getStatus());
        Assert.assertEquals("附件应落库 1 条", 1, saved.getAttachments().size());

        // ★ 修正旧系统「新增不写流转记录」的缺陷：登记后应有 2 条流转（登记 + 给承办人的待办）
        Assert.assertEquals("登记后应有 2 条流转记录（登记 + 待办）", 2, saved.getFlows().size());
        DocReceiveFlow open = saved.getFlows().stream()
                .filter(item -> item.getHandleTime() == null).findFirst().orElse(null);
        Assert.assertNotNull("应存在一条未处理的待办流转", open);
        Assert.assertEquals("待办流转的处理人应为承办人", "admin", open.getHandler());

        // 统计
        Assert.assertTrue("收文统计的我的待办应 >= 1", docReceiveService.queryStat().getMyTodo() >= 1);
    }

    /**
     * 收文流转全链路：登记 → 转办 → 退回 → 办结 → 归档。
     *
     * <p>用两个账号（admin=登记人、zhangsan=承办人）走完整条链，
     * 并且刻意在流转中途切换登录人，以便验证「当前文件的流程不属于当前用户」这条校验。
     */
    @Test
    public void testReceiveFlowTransferRejectAndFinish() {
        DocReceive doc = buildReceive("测试收文-流转");
        String id = docReceiveService.createDoc(doc);

        // ① 转办给自己应被拒绝（保留旧系统的校验规则）
        DocHandleDTO selfTransfer = new DocHandleDTO();
        selfTransfer.setDocId(id);
        selfTransfer.setToUsername(ME);
        assertBusinessError(() -> docReceiveService.transfer(selfTransfer), "自己");

        // ② 退回必须填原因（此时待办在 admin 手上）
        DocHandleDTO noReasonReject = new DocHandleDTO();
        noReasonReject.setDocId(id);
        assertBusinessError(() -> docReceiveService.reject(noReasonReject), "原因");

        // ③ 转办给 zhangsan
        DocHandleDTO toOther = new DocHandleDTO();
        toOther.setDocId(id);
        toOther.setToUsername(OTHER);
        toOther.setOpinion("请承办");
        docReceiveService.transfer(toOther);

        DocReceive afterTransfer = docReceiveService.queryDetail(id);
        Assert.assertEquals("转办后状态应为承办中", DocReceive.STATUS_HANDLING, afterTransfer.getStatus());
        Assert.assertEquals("当前处理人应切到接收人", OTHER, afterTransfer.getCurrentHandler());
        Assert.assertEquals("当前处理人姓名应冗余落库", OTHER_NAME, afterTransfer.getCurrentHandlerName());
        // 流转记录：登记（已关闭）+ 转办给 admin（已关闭）+ 转办给 zhangsan（待办）
        Assert.assertEquals("应有 3 条流转记录", 3, afterTransfer.getFlows().size());

        // ④ 不在自己手上的待办不能被操作（旧系统此处会 NPE 或静默放过）
        DocHandleDTO byWrongUser = new DocHandleDTO();
        byWrongUser.setDocId(id);
        byWrongUser.setOpinion("我不该能退回");
        assertBusinessError(() -> docReceiveService.reject(byWrongUser), "不属于当前用户");

        // ⑤ 切换到承办人 zhangsan，退回给上一位处理人
        bindUser(OTHER, OTHER_NAME);
        DocHandleDTO reject = new DocHandleDTO();
        reject.setDocId(id);
        reject.setOpinion("材料不全，请补齐后重报");
        docReceiveService.reject(reject);

        DocReceive rejected = docReceiveService.queryDetail(id);
        Assert.assertEquals("退回后状态应为已退回", DocReceive.STATUS_REJECTED, rejected.getStatus());
        Assert.assertEquals("退回应回到上一位处理人", ME, rejected.getCurrentHandler());
        Assert.assertEquals("退回与转办共 4 条流转记录", 4, rejected.getFlows().size());

        // ⑥ 切回 admin 办结
        bindUser(ME, ME_NAME);
        DocHandleDTO finish = new DocHandleDTO();
        finish.setDocId(id);
        finish.setOpinion("已补齐，办结");
        docReceiveService.finish(finish);

        DocReceive finished = docReceiveService.queryDetail(id);
        Assert.assertEquals(DocReceive.STATUS_FINISHED, finished.getStatus());
        Assert.assertNull("办结后当前处理人应被清空", finished.getCurrentHandler());
        Assert.assertNotNull("办结时间应写入", finished.getFinishTime());

        // ⑦ 归档：不选类别应被拒绝
        DocArchiveDTO archiveDto = new DocArchiveDTO();
        archiveDto.setDocId(id);
        assertBusinessError(() -> docReceiveService.archive(archiveDto), "档案类别");

        // ⑧ 归档：选择类别后应生成档案，且公文附件变成卷内文件
        archiveDto.setCategoryId(anyLeafCategory().getId());
        String archiveId = docReceiveService.archive(archiveDto);
        Assert.assertNotNull("归档应返回档案ID", archiveId);

        Archive generated = archiveService.queryDetail(archiveId);
        Assert.assertNotNull(generated);
        Assert.assertEquals("归档来源应为收文", Archive.SOURCE_DOC_RECEIVE, generated.getSourceType());
        Assert.assertEquals("归档来源单据应为该收文", id, generated.getSourceId());
        Assert.assertEquals("公文附件应成为卷内文件", 1, generated.getFiles().size());
        Assert.assertEquals("归档后收文状态应为已归档",
                DocReceive.STATUS_ARCHIVED, docReceiveService.queryDetail(id).getStatus());

        // ⑨ 重复归档应被拒绝
        assertBusinessError(() -> docReceiveService.archive(archiveDto), "已归档");
    }

    @Test
    public void testReceiveListQueryAndRelated() {
        DocReceive doc = buildReceive("测试收文-查询");
        String id = docReceiveService.createDoc(doc);
        DocReceive saved = docReceiveService.queryDetail(id);

        DocQueryDTO query = new DocQueryDTO();
        query.setDocNo(saved.getDocNo());
        IPage<DocReceive> page = docReceiveService.queryPage(query);
        Assert.assertEquals("按收文登记号应能精确命中", 1, page.getTotal());
        Assert.assertNotNull("列表应带上附件数", page.getRecords().get(0).getAttachmentCount());

        // 档案详情页的「收发文情况」按配套项目关联
        List<DocReceive> related = docReceiveService.queryRelated(saved.getFacilityId(), null, null, 50);
        Assert.assertTrue("按配套项目应能查到该收文",
                related.stream().anyMatch(item -> id.equals(item.getId())));
    }

    // ==================================================================
    // 四、发文：登记 → 查询 → 归档
    // ==================================================================

    @Test
    public void testSendRegisterAndArchive() {
        Facility facility = anyFacility();
        DocSend doc = new DocSend()
                .setDocTitle("测试发文-关于配套建设的函")
                .setDocType("函")
                .setToDept("天津市规划和自然资源局")
                .setIssueDate(new Date())
                .setSigner("张三")
                .setDrafter("李四")
                .setSecretLevel("一般")
                .setFacilityId(facility.getId());
        DocAttachment attachment = new DocAttachment()
                .setFileName("发文正文.pdf")
                .setFileExt("pdf")
                .setFileSize(4096L)
                .setStorePath("/send/2026/09/test.pdf")
                .setStoreType("local");
        doc.setAttachments(new ArrayList<>(java.util.Collections.singletonList(attachment)));

        String id = docSendService.createDoc(doc);
        DocSend saved = docSendService.queryDetail(id);
        Assert.assertTrue("发文登记号应形如 FW-yyyy-NNNN，实际=" + saved.getDocNo(),
                saved.getDocNo().matches("^FW-\\d{4}-\\d{4}$"));
        Assert.assertEquals("附件应落库 1 条", 1, saved.getAttachments().size());

        DocQueryDTO query = new DocQueryDTO();
        query.setDocNo(saved.getDocNo());
        Assert.assertEquals("按发文登记号应能精确命中", 1, docSendService.queryPage(query).getTotal());
        Assert.assertTrue("发文总数应 >= 1", docSendService.countAll() >= 1);

        List<DocSend> related = docSendService.queryRelated(saved.getFacilityId(), null, null, 50);
        Assert.assertTrue("按配套项目应能查到该发文",
                related.stream().anyMatch(item -> id.equals(item.getId())));

        // 归档必须选类别
        DocArchiveDTO dto = new DocArchiveDTO();
        dto.setDocId(id);
        try {
            docSendService.archive(dto);
            Assert.fail("发文归档不选档案类别应被拒绝");
        } catch (JeecgBootException e) {
            Assert.assertTrue(e.getMessage().contains("档案类别"));
        }

        dto.setCategoryId(anyLeafCategory().getId());
        String archiveId = docSendService.archive(dto);
        Archive generated = archiveService.queryDetail(archiveId);
        Assert.assertNotNull(generated);
        Assert.assertEquals(Archive.SOURCE_DOC_SEND, generated.getSourceType());
        Assert.assertEquals("发文附件应成为卷内文件", 1, generated.getFiles().size());
    }

    // ==================================================================
    // 五、统计 SQL 的完整性（去掉过滤条件也必须能跑通）
    // ==================================================================

    @Test
    public void testStatisticQueriesWithoutFilter() {
        ArchiveStatVO stat = archiveService.queryStat(new ArchiveQueryDTO(), 5);
        Assert.assertNotNull(stat);
        Assert.assertNotNull(stat.getByStatus());
        Assert.assertNotNull(stat.getByYear());
        Assert.assertNotNull(stat.getBySecretLevel());
        Assert.assertNotNull(stat.getByXzqh());
        Assert.assertNotNull(stat.getByCategory());
        Assert.assertNotNull(stat.getByProject());

        // 行政区划下拉来自 t_land 聚合
        List<Map<String, Object>> xzqh = landService.queryXzqhOptions();
        Assert.assertFalse("行政区划下拉不应为空", xzqh.isEmpty());
    }
}
