package org.jeecg.modules.land.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.JeecgSystemApplication;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.system.controller.SysPermissionController;
import org.jeecg.modules.system.entity.SysPermission;
import org.jeecg.modules.system.entity.SysRolePermission;
import org.jeecg.modules.system.model.SysPermissionTree;
import org.jeecg.modules.system.service.ISysPermissionService;
import org.jeecg.modules.system.service.ISysRolePermissionService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 档案管理 / 收发文管理 的「菜单管理」权限登记校验。
 *
 * <p>这组用例存在的唯一原因，是踩过一个**从代码上完全看不出来**的坑：
 *
 * <p>jeecg 的菜单树（{@code SysPermissionController.getTreeList}、
 * {@code SysRoleController.getTreeModelList}）在建树时是
 * <pre>
 *   if (!permission.isLeaf()) { 递归子节点 }
 * </pre>
 * 也就是说：**只要菜单行的 {@code is_leaf = 1}，它的子节点就再也不会被遍历到**。
 * 如果菜单下面挂了 {@code menu_type=2} 的按钮权限，而这些按钮又「看不见」，
 * 菜单管理里既看不到按钮、角色授权里也勾不到按钮 ——
 * 而数据库里那 27 行按钮权限明明是存在的。
 *
 * <p>本机实测：所有 jeecg 自带菜单（用户管理、Online表单开发…）只要是按钮的父节点，
 * {@code is_leaf} 一律为 0；只有本模块最初写的 5 个菜单是 「有按钮 + is_leaf=1」。
 *
 * <p>依赖本机 MySQL（127.0.0.1:3306/tj-jyxyd）与 Redis。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = JeecgSystemApplication.class)
@Transactional
public class LandPermissionTreeTest {

    /** 管理员角色（sys_role.role_code = 'admin'） */
    private static final String ADMIN_ROLE_ID = "f6817f48af4fb3af11b9e8bf182f618b";

    private static final String ARCHIVE_ROOT = "档案管理";
    private static final String DOC_ROOT = "收发文管理";

    /** 各菜单下应有的按钮数量（menu_type=2） */
    private static final Map<String, Integer> EXPECTED_BUTTON_COUNT = new HashMap<String, Integer>() {{
        put("档案维护", 7);
        put("档案统计", 1);
        put("档案类别管理", 6);
        put("收文管理", 8);
        put("发文管理", 5);
    }};

    /** 本模块的 8 个菜单节点（2 个一级 + 6 个二级），用于校验菜单行不残留权限码 */
    private static final List<String> LAND_MENU_IDS = java.util.Arrays.asList(
            "7e2a9c4f1b6d4a3e8f0c5b7d9a1e2f30",   // 档案管理
            "a1b2c3d4e5f6470891a2b3c4d5e6f701",   // 档案维护
            "a1b2c3d4e5f6470891a2b3c4d5e6f702",   // 档案查询
            "a1b2c3d4e5f6470891a2b3c4d5e6f703",   // 档案统计
            "8f3b0d5a2c7e4b6f9a1d6c8e0b2f3a41",   // 档案类别管理
            "a1b2c3d4e5f6470891a2b3c4d5e6f710",   // 收发文管理
            "a1b2c3d4e5f6470891a2b3c4d5e6f711",   // 收文管理
            "a1b2c3d4e5f6470891a2b3c4d5e6f712");  // 发文管理

    @Autowired
    private SysPermissionController sysPermissionController;

    @Autowired
    private ISysPermissionService sysPermissionService;

    @Autowired
    private ISysRolePermissionService sysRolePermissionService;

    // ==================================================================
    // 一、菜单管理里「看得见」：直接调用菜单管理页用的那个接口
    // ==================================================================

    /**
     * 菜单管理页调用的是 {@code GET /sys/permission/list}，
     * 这里直接调同一个 Controller 方法，断言按钮确实出现在树的对应位置。
     */
    @Test
    public void testMenuTreeContainsLandButtonPermissions() {
        List<SysPermissionTree> tree = loadMenuTree();

        SysPermissionTree archiveRoot = findByName(tree, ARCHIVE_ROOT);
        Assert.assertNotNull("菜单树中应存在一级菜单「" + ARCHIVE_ROOT + "」", archiveRoot);

        SysPermissionTree docRoot = findByName(tree, DOC_ROOT);
        Assert.assertNotNull("菜单树中应存在一级菜单「" + DOC_ROOT + "」", docRoot);

        int checked = 0;
        for (Map.Entry<String, Integer> entry : EXPECTED_BUTTON_COUNT.entrySet()) {
            String menuName = entry.getKey();
            SysPermissionTree menu = findByName(archiveRoot.getChildren(), menuName);
            if (menu == null) {
                menu = findByName(docRoot.getChildren(), menuName);
            }
            Assert.assertNotNull("菜单树中应存在菜单「" + menuName + "」", menu);
            List<SysPermissionTree> children = menu.getChildren();
            Assert.assertNotNull(
                    "「" + menuName + "」的 children 为空 —— 说明它的 is_leaf 被写成了 1，"
                            + "jeecg 建树时不会递归到它的按钮权限（这正是本次修复的坑）", children);
            Assert.assertEquals("「" + menuName + "」下的按钮数量不符",
                    entry.getValue().intValue(), children.size());
            for (SysPermissionTree button : children) {
                Assert.assertEquals("「" + menuName + "」下的子节点都应是按钮权限（menu_type=2）",
                        Integer.valueOf(2), button.getMenuType());
                Assert.assertNotNull("按钮权限必须有权限码：" + button.getName(), button.getPerms());
            }
            checked += children.size();
        }
        Assert.assertEquals("菜单树里应能看到的按钮总数", 27, checked);
    }

    // ==================================================================
    // 二、防回归：三条例外情况的守卫
    // ==================================================================

    /**
     * 守卫 1：**有按钮子节点的菜单，is_leaf 必须为 0**。
     *
     * <p>这条是整个坑的根因，必须长期守住 —— 以后新增菜单+按钮时，
     * 只要忘了把 is_leaf 置 0，这条用例就会红。
     */
    @Test
    public void testMenuWithButtonsMustNotBeLeaf() {
        List<SysPermission> all = sysPermissionService.list(
                new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getDelFlag, 0));

        Map<String, SysPermission> byId = new HashMap<>();
        for (SysPermission permission : all) {
            byId.put(permission.getId(), permission);
        }

        List<String> offenders = new ArrayList<>();
        for (SysPermission permission : all) {
            if (!Integer.valueOf(2).equals(permission.getMenuType())) {
                continue;
            }
            String parentId = permission.getParentId();
            SysPermission parent = parentId == null ? null : byId.get(parentId);
            if (parent != null && parent.isLeaf()) {
                offenders.add(parent.getName() + "(" + parent.getId() + ")");
            }
        }
        Assert.assertTrue(
                "以下菜单挂了按钮权限，但 is_leaf=1，jeecg 建树时不会递归到这些按钮，"
                        + "菜单管理与角色授权里都会看不到：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 守卫 2：按钮权限必须都是 {@code menu_type=2}，且菜单行上不能残留权限码。
     *
     * <p>只有 menu_type=2 的行才会被 {@code getAuthJsonArray()} 收进前端的按钮权限集合，
     * 从而被 {@code v-has} 识别；写在菜单行（menu_type=1）上会「后端拦得住、前端看不见」。
     */
    @Test
    public void testLandPermissionsAreAllButtonType() {
        List<SysPermission> landPerms = sysPermissionService.list(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getDelFlag, 0)
                        .likeRight(SysPermission::getPerms, "land:"));

        Assert.assertEquals("本模块应登记 27 个接口权限", 27, landPerms.size());
        for (SysPermission permission : landPerms) {
            Assert.assertEquals("权限码必须登记为按钮权限（menu_type=2）：" + permission.getPerms(),
                    Integer.valueOf(2), permission.getMenuType());
            Assert.assertEquals("按钮权限必须是启用状态：" + permission.getPerms(),
                    "1", permission.getStatus());
        }

        // 菜单行（menu_type != 2）不应残留权限码
        List<SysPermission> landMenus = sysPermissionService.list(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getDelFlag, 0)
                        .in(SysPermission::getId, LAND_MENU_IDS));
        Assert.assertEquals("本模块应有 8 个菜单节点（2 个一级 + 6 个二级）", 8, landMenus.size());
        for (SysPermission menu : landMenus) {
            Assert.assertNull("菜单行不应残留权限码，权限码只放在按钮上：" + menu.getName(), menu.getPerms());
        }
    }

    /**
     * 守卫 3：27 个按钮权限必须都已授权给 admin 角色。
     */
    @Test
    public void testAllLandPermissionsGrantedToAdmin() {
        List<SysPermission> landPerms = sysPermissionService.list(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getDelFlag, 0)
                        .likeRight(SysPermission::getPerms, "land:"));

        List<SysRolePermission> grants = sysRolePermissionService.list(
                new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, ADMIN_ROLE_ID));

        Set<String> grantedPermissionIds = new HashSet<>();
        for (SysRolePermission grant : grants) {
            grantedPermissionIds.add(grant.getPermissionId());
        }

        List<String> missing = new ArrayList<>();
        for (SysPermission permission : landPerms) {
            if (!grantedPermissionIds.contains(permission.getId())) {
                missing.add(permission.getPerms());
            }
        }
        Assert.assertTrue("以下权限尚未授权给 admin 角色：" + missing, missing.isEmpty());
    }

    /**
     * 守卫 4：按钮名不应与任何菜单名重复（否则菜单管理/角色授权树里会出现两个同名节点，无法区分）。
     */
    @Test
    public void testButtonNameDoesNotClashWithMenuName() {
        List<SysPermission> all = sysPermissionService.list(
                new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getDelFlag, 0));

        Set<String> menuNames = new HashSet<>();
        Set<String> buttonNames = new HashSet<>();
        for (SysPermission permission : all) {
            if (Integer.valueOf(2).equals(permission.getMenuType())) {
                buttonNames.add(permission.getName());
            } else {
                menuNames.add(permission.getName());
            }
        }
        Set<String> clash = new HashSet<>(buttonNames);
        clash.retainAll(menuNames);
        Assert.assertTrue("按钮名与菜单名重复，界面里会分不清：" + clash, clash.isEmpty());
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private List<SysPermissionTree> loadMenuTree() {
        // 传 null 的 request 是安全的：list() 方法体只用到 sysPermission 的查询条件
        Result<List<SysPermissionTree>> result = sysPermissionController.list(new SysPermission(), null);
        Assert.assertTrue("菜单树接口应返回成功", result.isSuccess());
        List<SysPermissionTree> tree = result.getResult();
        Assert.assertNotNull("菜单树不应为空", tree);
        return tree;
    }

    private SysPermissionTree findByName(List<SysPermissionTree> nodes, String name) {
        if (nodes == null) {
            return null;
        }
        for (SysPermissionTree node : nodes) {
            if (name.equals(node.getName())) {
                return node;
            }
        }
        return null;
    }
}
