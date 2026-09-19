package org.jeecg.modules.land.archive.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.dto.ArchiveCategorySortDTO;
import org.jeecg.modules.land.archive.entity.ArchiveCategory;
import org.jeecg.modules.land.archive.service.IArchiveCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Description: 档案管理 · 档案类别管理（方案 2.3.2 第 1 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/archive/category}。
 *
 * <p><b>权限码</b>（登记为 sys_permission 里 menu_type=2 的「按钮权限」，
 * 授权脚本见 {@code sql/archive/06_archive_permission_buttons.sql}）：
 * <pre>
 *   land:archiveCategory:list    类别树 / 类别详情 / 同级重名校验
 *   land:archiveCategory:add     新建类别
 *   land:archiveCategory:edit    更名 / 移动
 *   land:archiveCategory:delete  移除类别
 *   land:archiveCategory:sort    同级排序 / 拖拽移动
 *   land:archiveCategory:status  启用 / 停用
 * </pre>
 *
 * <p>接口清单：
 * <pre>
 *   GET    /land/archive/category/tree      类别树（支持关键字/状态过滤、剪除子树）
 *   GET    /land/archive/category/queryById 类别详情
 *   GET    /land/archive/category/checkName 同级重名校验
 *   POST   /land/archive/category/add       新建类别
 *   PUT    /land/archive/category/edit      编辑（更名 / 排序 / 移动 / 启停）
 *   DELETE /land/archive/category/delete    移除类别（含前置校验）
 *   POST   /land/archive/category/sort      同级排序 / 拖拽移动落库
 *   POST   /land/archive/category/status    启用 / 停用
 * </pre>
 */
@Slf4j
@Api(tags = "档案管理-档案类别管理")
@RestController
@RequestMapping("/land/archive/category")
public class ArchiveCategoryController {

    /** 权限码：类别查询（树 / 详情 / 重名校验） */
    private static final String PERM_LIST = "land:archiveCategory:list";
    /** 权限码：类别新增 */
    private static final String PERM_ADD = "land:archiveCategory:add";
    /** 权限码：类别编辑（更名 / 移动） */
    private static final String PERM_EDIT = "land:archiveCategory:edit";
    /** 权限码：类别移除 */
    private static final String PERM_DELETE = "land:archiveCategory:delete";
    /** 权限码：类别排序 */
    private static final String PERM_SORT = "land:archiveCategory:sort";
    /** 权限码：类别启停 */
    private static final String PERM_STATUS = "land:archiveCategory:status";

    /**
     * 类别树是所有「需要选档案类别」的页面共用的只读基础数据：
     * 档案新增/编辑、收文登记/编辑/归档、发文登记/编辑/归档都要拉它。
     * 因此 {@code /tree} 用 OR 放行——只要拥有其中任意一个业务权限就能读到类别树，
     * 不需要为「读类别树」单独再授一次权。
     *
     * <p>注意：{@code @RequiresPermissions} 的 value 必须是编译期常量表达式，
     * Java 不允许引用「数组类型的 static final 字段」，所以注解里写的是完整字符串数组。
     */

    @Autowired
    private IArchiveCategoryService archiveCategoryService;

    /**
     * 类别树查询。
     *
     * @param keyword   名称/编码/别名 模糊过滤
     * @param status    1只看启用 0只看停用；不传则不过滤
     * @param excludeId 需要整棵剪掉的子树根ID（「上级类别」下拉里排除自己）
     */
    @AutoLog(value = "档案类别-类别树查询")
    @ApiOperation(value = "档案类别-类别树查询", notes = "返回 antd tree 结构；keyword/status 过滤时保留命中节点的祖先")
    @RequiresPermissions(value = {PERM_LIST,
            "land:archive:list", "land:archive:add", "land:archive:edit",
            "land:docReceive:add", "land:docReceive:edit", "land:docReceive:archive",
            "land:docSend:add", "land:docSend:edit", "land:docSend:archive"}, logical = Logical.OR)
    @GetMapping(value = "/tree")
    public Result<List<ArchiveCategory>> tree(@RequestParam(name = "keyword", required = false) String keyword,
                                              @RequestParam(name = "status", required = false) Integer status,
                                              @RequestParam(name = "excludeId", required = false) String excludeId) {
        return Result.OK(archiveCategoryService.queryTree(keyword, status, excludeId));
    }

    /**
     * 类别详情。
     */
    @AutoLog(value = "档案类别-通过id查询")
    @ApiOperation(value = "档案类别-通过id查询", notes = "附带父类别名称与全路径名称")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/queryById")
    public Result<ArchiveCategory> queryById(@RequestParam(name = "id", required = true) String id) {
        ArchiveCategory category = archiveCategoryService.queryDetail(id);
        if (category == null) {
            return Result.error("未找到对应的档案类别");
        }
        return Result.OK(category);
    }

    /**
     * 同级类别名称重复校验。
     */
    @AutoLog(value = "档案类别-名称重复校验")
    @ApiOperation(value = "档案类别-名称重复校验", notes = "编辑时传 id 排除自身")
    @RequiresPermissions(value = {PERM_LIST, PERM_ADD, PERM_EDIT}, logical = Logical.OR)
    @GetMapping(value = "/checkName")
    public Result<?> checkName(@RequestParam(name = "parentId", required = false) String parentId,
                              @RequestParam(name = "name", required = true) String name,
                              @RequestParam(name = "id", required = false) String id) {
        try {
            archiveCategoryService.checkNameUnique(parentId, name, id);
            return Result.OK("同级下名称可用");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 新建类别。
     */
    @AutoLog(value = "档案类别-新建")
    @ApiOperation(value = "档案类别-新建", notes = "parentId 为空表示新建顶级类别；同名同级会拦截")
    @RequiresPermissions(PERM_ADD)
    @PostMapping(value = "/add")
    public Result<?> add(@RequestBody ArchiveCategory category) {
        try {
            archiveCategoryService.addCategory(category);
            return Result.OK("添加成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("新建档案类别失败", e);
            return Result.error("新建失败：" + e.getMessage());
        }
    }

    /**
     * 编辑类别（更名 / 编码别名说明 / 排序号 / 启停 / 移动到其它上级）。
     */
    @AutoLog(value = "档案类别-编辑")
    @ApiOperation(value = "档案类别-编辑", notes = "更名、移动、启停、排序号均走此接口")
    @RequiresPermissions(PERM_EDIT)
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody ArchiveCategory category) {
        try {
            archiveCategoryService.editCategory(category);
            return Result.OK("修改成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("编辑档案类别失败", e);
            return Result.error("修改失败：" + e.getMessage());
        }
    }

    /**
     * 移除类别。
     *
     * <p>前置校验：① 该类别下无子类别；② 该类别下无档案（t_archive 表已建立时校验）。
     * 校验不通过时返回业务提示，例如「该类别下存在 3 份档案，无法移除」。
     */
    @AutoLog(value = "档案类别-移除")
    @ApiOperation(value = "档案类别-移除", notes = "有子类别或有档案时不允许移除")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
        try {
            archiveCategoryService.deleteCategory(id);
            return Result.OK("移除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("移除档案类别失败", e);
            return Result.error("移除失败：" + e.getMessage());
        }
    }

    /**
     * 同级排序 / 拖拽移动后的顺序落库。
     */
    @AutoLog(value = "档案类别-排序")
    @ApiOperation(value = "档案类别-排序", notes = "parentId + 该父级下按新顺序排列的子节点ID")
    @RequiresPermissions(PERM_SORT)
    @PostMapping(value = "/sort")
    public Result<?> editSort(@RequestBody ArchiveCategorySortDTO dto) {
        try {
            archiveCategoryService.sortCategory(dto);
            return Result.OK("排序成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("档案类别排序失败", e);
            return Result.error("排序失败：" + e.getMessage());
        }
    }

    /**
     * 启用 / 停用类别。停用会级联停用整棵子树。
     */
    @AutoLog(value = "档案类别-启用停用")
    @ApiOperation(value = "档案类别-启用停用", notes = "status 1启用 0停用；停用级联子树")
    @RequiresPermissions(PERM_STATUS)
    @PostMapping(value = "/status")
    public Result<?> editStatus(@RequestBody ArchiveCategory body) {
        if (body == null || body.getId() == null) {
            return Result.error("缺少类别ID");
        }
        try {
            archiveCategoryService.changeStatus(body.getId(), body.getStatus());
            return Result.OK("操作成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("档案类别状态变更失败", e);
            return Result.error("操作失败：" + e.getMessage());
        }
    }
}
