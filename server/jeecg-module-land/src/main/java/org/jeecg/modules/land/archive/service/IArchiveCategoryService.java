package org.jeecg.modules.land.archive.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.dto.ArchiveCategorySortDTO;
import org.jeecg.modules.land.archive.entity.ArchiveCategory;

import java.util.List;

/**
 * @Description: 档案类别树 Service（方案 2.3.2 第 1 项：档案类别管理）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface IArchiveCategoryService extends IService<ArchiveCategory> {

    /**
     * 查询类别树。
     *
     * @param keyword   名称/编码/别名 模糊过滤；命中节点及其所有祖先会被保留
     * @param status    1只看启用 0只看停用；null 不过滤
     * @param excludeId 需要整棵剪掉的子树根ID（前端「上级类别」下拉里排除自己）
     * @return 树形结构（顶级节点列表）
     */
    List<ArchiveCategory> queryTree(String keyword, Integer status, String excludeId);

    /**
     * 查询类别详情，附带父类别名称与全路径名称。
     *
     * @param id 类别ID
     * @return 详情；不存在时返回 null
     */
    ArchiveCategory queryDetail(String id);

    /**
     * 新建类别。自动计算 path / level / sortNo（同级末尾）。
     *
     * @param category parentId、name 必填
     */
    void addCategory(ArchiveCategory category);

    /**
     * 编辑类别（更名 / 改编码别名说明 / 排序号 / 启停 / 移动到其它上级）。
     * 父级变化时会级联重算整棵子树的 path 与 level。
     *
     * <p>上级类别约定：{@code parentId} 为 null（未传）表示不改上级；
     * 传 {@code ""} 或 {@code "0"} 表示移动到顶级；其余值表示移动到该上级下。
     *
     * @param category id 必填
     */
    void editCategory(ArchiveCategory category);

    /**
     * 移除类别。
     *
     * <p>前置校验：① 无子类别；② 类别下无档案（t_archive 表已建立时才校验）。
     * 任一不满足则抛出 JeecgBootException，消息可直接展示给用户。
     *
     * @param id 类别ID
     */
    void deleteCategory(String id);

    /**
     * 同级排序 / 拖拽移动后的排序落库。
     *
     * @param dto parentId + 该父级下按新顺序排列的子节点ID
     */
    void sortCategory(ArchiveCategorySortDTO dto);

    /**
     * 启用 / 停用类别。停用会级联停用整棵子树；启用时要求上级处于启用状态。
     *
     * @param id     类别ID
     * @param status 1启用 0停用
     */
    void changeStatus(String id, Integer status);

    /**
     * 校验同一上级下类别名称不重复。
     *
     * @param parentId  上级ID，空表示顶级
     * @param name      类别名称
     * @param excludeId 编辑时排除自身ID，新建传 null
     * @throws org.jeecg.common.exception.JeecgBootException 名称重复
     */
    void checkNameUnique(String parentId, String name, String excludeId);
}
