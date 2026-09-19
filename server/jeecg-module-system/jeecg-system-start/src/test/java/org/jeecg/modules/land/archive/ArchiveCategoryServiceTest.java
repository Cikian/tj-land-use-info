package org.jeecg.modules.land.archive;

import org.jeecg.JeecgSystemApplication;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.dto.ArchiveCategorySortDTO;
import org.jeecg.modules.land.archive.entity.ArchiveCategory;
import org.jeecg.modules.land.archive.mapper.ArchiveCategoryMapper;
import org.jeecg.modules.land.archive.service.IArchiveCategoryService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 档案类别管理（方案 2.3.2 第 1 项）服务层集成测试。
 *
 * <p>直接调用 Service，绕过登录与权限校验；整个测试类带事务，结束后回滚，
 * 不会污染 tj-jyxyd 里的初始化数据。
 *
 * <p>依赖：本机 MySQL（127.0.0.1:3306/tj-jyxyd，见 application-dev.yml）与 Redis。
 * 运行：mvn -o test -pl jeecg-module-system/jeecg-system-start -Dtest=ArchiveCategoryServiceTest
 *
 * <p>webEnvironment 用 RANDOM_PORT：jeecg 的 WebSocketConfig 需要真实的 ServerContainer，
 * MOCK 环境下 ServerEndpointExporter 会启动失败（与 SysUserTest 保持一致）。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = JeecgSystemApplication.class)
@Transactional
public class ArchiveCategoryServiceTest {

    private static final String ROOT_NAME = "基本建设手续";

    @Autowired
    private IArchiveCategoryService categoryService;

    @Autowired
    private ArchiveCategoryMapper mapper;

    // ==================================================================
    // 初始化数据 & 树查询
    // ==================================================================

    @Test
    public void testTreeContainsSixSeedRoots() {
        List<ArchiveCategory> tree = categoryService.queryTree(null, null, null);
        Assert.assertEquals("方案原文的 6 个顶级类别应全部存在", 6, tree.size());
        List<String> names = new ArrayList<>();
        Integer lastSortNo = null;
        for (ArchiveCategory node : tree) {
            names.add(node.getName());
            Assert.assertNull("顶级类别 parentId 应为空", node.getParentId());
            Assert.assertEquals("顶级类别 level 应为 1", Integer.valueOf(1), node.getLevel());
            if (lastSortNo != null) {
                Assert.assertTrue("顶级类别应按 sort_no 升序返回", node.getSortNo() > lastSortNo);
            }
            lastSortNo = node.getSortNo();
        }
        Assert.assertTrue(names.contains(ROOT_NAME));
        Assert.assertEquals(ROOT_NAME, tree.get(0).getName());
    }

    @Test
    public void testArchiveCountIsFilledAfterArchiveModuleLands() {
        // 档案管理模块已落地（t_archive_file 已建表），因此类别树会带上「本类别档案数」。
        // 注意口径：类别挂在卷内文件上，所以这里的数量是「该类别下的文件数」。
        List<ArchiveCategory> tree = categoryService.queryTree(null, null, null);
        for (ArchiveCategory node : tree) {
            Assert.assertNotNull("t_archive_file 已建表时 archiveCount 不应为 null", node.getArchiveCount());
        }
    }

    /**
     * 单独验证「探测表是否存在」的 SQL 本身可用。
     *
     * <p>这条用例很关键——如果探测 SQL 被 JSqlParser 判为语法错误（例如误用 DATABASE()），
     * 服务层会 catch 住并一律按「表不存在」处理，档案数量校验将永远不会生效，
     * 而这一点从业务用例上是看不出来的。
     *
     * <p>档案管理模块落地后，探测目标从 t_archive 换成了 t_archive_file
     * （因为档案类别挂在卷内文件上）。
     */
    @Test
    public void testTableProbeSqlWorks() {
        Assert.assertEquals("探测已存在的表应返回 1", 1, mapper.countTable("t_archive_category"));
        Assert.assertEquals("档案文件表已建立，应返回 1", 1, mapper.countTable("t_archive_file"));
        Assert.assertEquals("档案主表已建立，应返回 1", 1, mapper.countTable("t_archive"));
        Assert.assertEquals("不存在的表应返回 0", 0, mapper.countTable("t_not_exists_at_all"));
    }

    // ==================================================================
    // 新增
    // ==================================================================

    @Test
    public void testAddChildKeepsTreeConsistent() {
        ArchiveCategory root = findRoot(ROOT_NAME);
        categoryService.addCategory(new ArchiveCategory()
                .setParentId(root.getId())
                .setName("单元测试-子类别")
                .setCode("UT-01")
                .setAliasName("danyuanceshi"));

        ArchiveCategory child = findByName("单元测试-子类别");
        Assert.assertNotNull("新增的子类别应能查询到", child);
        Assert.assertEquals("子类别 level 应为 2", Integer.valueOf(2), child.getLevel());
        Assert.assertEquals("子类别 path 应为 父path/id",
                root.getPath() + "/" + child.getId(), child.getPath());
        Assert.assertEquals("新建类别应为叶子", Integer.valueOf(1), child.getIsLeaf());
        Assert.assertEquals("新建类别不应有子节点", Integer.valueOf(0), child.getHasChildren());

        ArchiveCategory refreshedRoot = categoryService.getById(root.getId());
        Assert.assertEquals("父类别 hasChildren 应更新为 1", Integer.valueOf(1), refreshedRoot.getHasChildren());
        Assert.assertEquals("父类别 isLeaf 应更新为 0", Integer.valueOf(0), refreshedRoot.getIsLeaf());
    }

    @Test
    public void testAddDuplicateNameInSameLevelIsRejected() {
        ArchiveCategory root = findRoot(ROOT_NAME);
        try {
            categoryService.addCategory(new ArchiveCategory()
                    .setParentId(root.getId())
                    .setName("单元测试-重复名"));
            categoryService.addCategory(new ArchiveCategory()
                    .setParentId(root.getId())
                    .setName("单元测试-重复名"));
            Assert.fail("同级同名应该被拦截");
        } catch (JeecgBootException e) {
            Assert.assertTrue("异常信息应说明同级重名：" + e.getMessage(), e.getMessage().contains("已存在"));
        }
    }

    // ==================================================================
    // 更名 / 移动
    // ==================================================================

    @Test
    public void testRenameKeepsPathUnchanged() {
        ArchiveCategory child = createChild(ROOT_NAME, "单元测试-待更名");
        String pathBefore = child.getPath();

        categoryService.editCategory(new ArchiveCategory()
                .setId(child.getId())
                .setName("单元测试-已更名")
                .setCode("UT-02"));

        ArchiveCategory renamed = categoryService.getById(child.getId());
        Assert.assertEquals("单元测试-已更名", renamed.getName());
        Assert.assertEquals("UT-02", renamed.getCode());
        Assert.assertEquals("更名不应改变 path", pathBefore, renamed.getPath());
        Assert.assertEquals("未传 parentId 时不应改动上级类别", child.getParentId(), renamed.getParentId());
    }

    @Test
    public void testMoveRecomputesSubtreePathAndLevel() {
        ArchiveCategory parent = createChild(ROOT_NAME, "单元测试-原父级");
        ArchiveCategory child = createChild2(parent, "单元测试-被移动");
        ArchiveCategory grandChild = createChild2(child, "单元测试-孙类别");
        Assert.assertEquals("孙类别层级应为 4", Integer.valueOf(4), grandChild.getLevel());

        // 新建一个顶级类别作为新的上级
        categoryService.addCategory(new ArchiveCategory().setName("单元测试-新父级"));
        ArchiveCategory newParent = findByName("单元测试-新父级");
        Assert.assertEquals(Integer.valueOf(1), newParent.getLevel());

        // 把 child 连同它的整棵子树移动到新的顶级类别下：level 3→2，孙 4→3（levelDelta = -1）
        categoryService.editCategory(new ArchiveCategory()
                .setId(child.getId())
                .setParentId(newParent.getId())
                .setName(child.getName()));

        ArchiveCategory movedChild = categoryService.getById(child.getId());
        ArchiveCategory movedGrandChild = categoryService.getById(grandChild.getId());
        Assert.assertEquals("被移动节点的 level 应变为 2", Integer.valueOf(2), movedChild.getLevel());
        Assert.assertEquals("被移动节点的 path 应重算",
                newParent.getPath() + "/" + child.getId(), movedChild.getPath());
        Assert.assertEquals("孙类别 level 应级联变为 3", Integer.valueOf(3), movedGrandChild.getLevel());
        Assert.assertEquals("孙类别 path 应级联重算",
                newParent.getPath() + "/" + child.getId() + "/" + grandChild.getId(),
                movedGrandChild.getPath());

        ArchiveCategory oldParent = categoryService.getById(parent.getId());
        Assert.assertEquals("原父级应重新变成叶子", Integer.valueOf(1), oldParent.getIsLeaf());
        Assert.assertEquals("原父级 hasChildren 应清零", Integer.valueOf(0), oldParent.getHasChildren());
    }

    @Test
    public void testMoveIntoOwnDescendantIsRejected() {
        ArchiveCategory parent = createChild(ROOT_NAME, "单元测试-父A");
        ArchiveCategory child = createChild2(parent, "单元测试-子B");
        try {
            categoryService.editCategory(new ArchiveCategory()
                    .setId(parent.getId())
                    .setParentId(child.getId())
                    .setName(parent.getName()));
            Assert.fail("把类别移到自己的下级应被拦截");
        } catch (JeecgBootException e) {
            Assert.assertTrue("异常信息应说明不能移入自己的下级：" + e.getMessage(),
                    e.getMessage().contains("下级"));
        }
    }

    // ==================================================================
    // 排序
    // ==================================================================

    @Test
    public void testSortRewritesSortNo() {
        ArchiveCategory root = findRoot(ROOT_NAME);
        ArchiveCategory a = createChild2(root, "单元测试-排序A");
        ArchiveCategory b = createChild2(root, "单元测试-排序B");
        ArchiveCategory c = createChild2(root, "单元测试-排序C");

        ArchiveCategorySortDTO dto = new ArchiveCategorySortDTO();
        dto.setParentId(root.getId());
        dto.setOrderedIds(Arrays.asList(c.getId(), a.getId(), b.getId()));
        categoryService.sortCategory(dto);

        Assert.assertEquals(Integer.valueOf(1), categoryService.getById(c.getId()).getSortNo());
        Assert.assertEquals(Integer.valueOf(2), categoryService.getById(a.getId()).getSortNo());
        Assert.assertEquals(Integer.valueOf(3), categoryService.getById(b.getId()).getSortNo());
    }

    @Test
    public void testSortRejectsIdsOutsideTheLevel() {
        ArchiveCategory root = findRoot(ROOT_NAME);
        ArchiveCategory other = findRoot("规划手续");
        ArchiveCategorySortDTO dto = new ArchiveCategorySortDTO();
        dto.setParentId(root.getId());
        dto.setOrderedIds(Arrays.asList(other.getId()));
        try {
            categoryService.sortCategory(dto);
            Assert.fail("排序列表里混入其它层级的节点应被拦截");
        } catch (JeecgBootException e) {
            Assert.assertTrue(e.getMessage().contains("不一致"));
        }
    }

    // ==================================================================
    // 移除前置校验
    // ==================================================================

    @Test
    public void testDeleteWithChildrenIsRejected() {
        ArchiveCategory parent = createChild(ROOT_NAME, "单元测试-有子类别");
        createChild2(parent, "单元测试-子类别1");
        try {
            categoryService.deleteCategory(parent.getId());
            Assert.fail("有子类别时应拒绝移除");
        } catch (JeecgBootException e) {
            Assert.assertTrue("异常信息应提示先移除子类别：" + e.getMessage(),
                    e.getMessage().contains("子类别"));
        }
    }

    @Test
    public void testDeleteLeafSucceeds() {
        ArchiveCategory leaf = createChild(ROOT_NAME, "单元测试-可移除");
        categoryService.deleteCategory(leaf.getId());
        Assert.assertNull("逻辑删除后按 id 应查不到", categoryService.getById(leaf.getId()));
        Assert.assertNull("逻辑删除后不应出现在树里", findByName("单元测试-可移除"));
    }

    // ==================================================================
    // 启停（停用级联）
    // ==================================================================

    @Test
    public void testDisableCascadesToSubtreeAndEnableIsGuarded() {
        ArchiveCategory parent = createChild(ROOT_NAME, "单元测试-停用父级");
        ArchiveCategory child = createChild2(parent, "单元测试-停用子级");

        categoryService.changeStatus(parent.getId(), 0);
        Assert.assertEquals("父级应停用", Integer.valueOf(0), categoryService.getById(parent.getId()).getStatus());
        Assert.assertEquals("子级应被级联停用", Integer.valueOf(0), categoryService.getById(child.getId()).getStatus());

        try {
            categoryService.changeStatus(child.getId(), 1);
            Assert.fail("上级停用时不应允许启用下级");
        } catch (JeecgBootException e) {
            Assert.assertTrue("异常信息应提示上级已停用：" + e.getMessage(),
                    e.getMessage().contains("上级类别"));
        }

        categoryService.changeStatus(parent.getId(), 1);
        Assert.assertEquals("父级应恢复启用", Integer.valueOf(1), categoryService.getById(parent.getId()).getStatus());
    }

    // ==================================================================
    // 过滤 / 详情
    // ==================================================================

    @Test
    public void testKeywordFilterKeepsAncestors() {
        ArchiveCategory parent = createChild(ROOT_NAME, "单元测试-关键字父级");
        createChild2(parent, "单元测试-关键字命中XYZ");

        List<ArchiveCategory> tree = categoryService.queryTree("关键字命中XYZ", null, null);
        Assert.assertFalse("按关键字过滤后不应为空", tree.isEmpty());
        // 命中节点的父级必须被保留，否则树上无法展开
        Assert.assertEquals("应保留命中节点的祖先链", 1, tree.size());
        ArchiveCategory kept = tree.get(0);
        Assert.assertEquals(ROOT_NAME, kept.getName());
        Assert.assertNotNull(kept.getChildren());
        Assert.assertEquals(1, kept.getChildren().size());
        Assert.assertEquals("单元测试-关键字父级", kept.getChildren().get(0).getName());
    }

    @Test
    public void testExcludeIdPrunesWholeSubtree() {
        ArchiveCategory root = findRoot(ROOT_NAME);
        List<ArchiveCategory> tree = categoryService.queryTree(null, null, root.getId());
        for (ArchiveCategory node : tree) {
            Assert.assertNotEquals("被排除的子树不应出现在结果里", root.getId(), node.getId());
        }
    }

    @Test
    public void testQueryDetailBuildsFullPathName() {
        ArchiveCategory parent = createChild(ROOT_NAME, "单元测试-详情父级");
        ArchiveCategory child = createChild2(parent, "单元测试-详情子级");
        ArchiveCategory detail = categoryService.queryDetail(child.getId());
        Assert.assertNotNull(detail);
        Assert.assertEquals("详情应带父类别名称", "单元测试-详情父级", detail.getParentName());
        Assert.assertEquals("详情应带全路径名称",
                ROOT_NAME + " / 单元测试-详情父级 / 单元测试-详情子级", detail.getFullPathName());
    }

    // ==================================================================
    // 辅助方法
    // ==================================================================

    private ArchiveCategory findRoot(String name) {
        for (ArchiveCategory node : categoryService.queryTree(null, null, null)) {
            if (name.equals(node.getName())) {
                return node;
            }
        }
        Assert.fail("未找到顶级类别：" + name);
        return null;
    }

    private ArchiveCategory createChild(String parentName, String name) {
        ArchiveCategory parent = findRoot(parentName);
        categoryService.addCategory(new ArchiveCategory().setParentId(parent.getId()).setName(name));
        ArchiveCategory created = findByName(name);
        Assert.assertNotNull("新增子类别失败：" + name, created);
        return created;
    }

    private ArchiveCategory createChild2(ArchiveCategory parent, String name) {
        categoryService.addCategory(new ArchiveCategory().setParentId(parent.getId()).setName(name));
        ArchiveCategory created = findByName(name);
        Assert.assertNotNull("新增子类别失败：" + name, created);
        return created;
    }

    /** 在整棵树里按名称查节点（同一个事务里能看到刚写入的数据） */
    private ArchiveCategory findByName(String name) {
        ArchiveCategory found = search(categoryService.queryTree(null, null, null), name);
        return found;
    }

    private ArchiveCategory search(List<ArchiveCategory> nodes, String name) {
        if (nodes == null) {
            return null;
        }
        for (ArchiveCategory node : nodes) {
            if (name.equals(node.getName())) {
                return node;
            }
            ArchiveCategory hit = search(node.getChildren(), name);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }
}
