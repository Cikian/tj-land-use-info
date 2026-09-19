package org.jeecg.modules.land.archive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.archive.dto.ArchiveCategorySortDTO;
import org.jeecg.modules.land.archive.entity.ArchiveCategory;
import org.jeecg.modules.land.archive.mapper.ArchiveCategoryMapper;
import org.jeecg.modules.land.archive.service.IArchiveCategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @Description: 档案类别树 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>path 用主键串</b>（顶级 /id，下级 父.path + "/" + id）。改名不动 path，
 *       所以「更名」不需要级联更新任何东西；只有「移动」才需要重算子树 path/level。</li>
 *   <li><b>移除前置校验</b>：先查子类别，再查档案文件数（口径 = t_archive_file 上
 *       该类别及其整棵子树下的文件数）。t_archive_file 表不存在时自动跳过档案校验，
 *       并在日志中告警，接口返回的 archiveCount 为 null，前端据此提示「档案校验未启用」。</li>
 *   <li><b>停用级联</b>：停用父类别会连同整棵子树一起停用；启用不级联，且要求上级已启用。</li>
 * </ul>
 */
@Slf4j
@Service
public class ArchiveCategoryServiceImpl
        extends ServiceImpl<ArchiveCategoryMapper, ArchiveCategory>
        implements IArchiveCategoryService {

    /** 树路径分隔符 */
    private static final String PATH_SEPARATOR = "/";

    /**
     * 档案文件表名。
     *
     * <p>★ 档案类别挂在<b>卷内文件</b>（t_archive_file.category_id）上，
     * 而不是挂在档案主表 t_archive 上——每个上传的文件各关联一个档案类别。
     * 因此「类别下有档案则不能移除」的校验统计的是 t_archive_file。
     */
    private static final String ARCHIVE_FILE_TABLE = "t_archive_file";

    /** t_archive_file 表是否存在的本地缓存（首次访问时探测） */
    private volatile Boolean archiveTablePresent;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public List<ArchiveCategory> queryTree(String keyword, Integer status, String excludeId) {
        List<ArchiveCategory> rows = list(new QueryWrapper<ArchiveCategory>()
                .orderByAsc("sort_no")
                .orderByAsc("name"));
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        // 先把查询结果复制一份再建树。
        // MyBatis 一级缓存（localCacheScope=SESSION，事务/请求级）会复用同一次会话里的实体对象，
        // 若直接在被缓存的实体上写 children，同一会话内重复调用本方法时会把同一个节点挂载多次。
        List<ArchiveCategory> all = new ArrayList<>(rows.size());
        Map<String, ArchiveCategory> byId = new LinkedHashMap<>();
        for (ArchiveCategory row : rows) {
            ArchiveCategory node = new ArchiveCategory();
            BeanUtils.copyProperties(row, node);
            node.setChildren(null);
            all.add(node);
            byId.put(node.getId(), node);
        }
        fillArchiveCount(all);

        // 1) 剪掉 excludeId 这棵子树
        Set<String> visible = new HashSet<>(byId.keySet());
        String excludePath = null;
        if (StringUtils.isNotBlank(excludeId)) {
            ArchiveCategory excluded = byId.get(excludeId);
            if (excluded != null) {
                excludePath = excluded.getPath();
                for (ArchiveCategory category : all) {
                    if (isSelfOrDescendant(category, excludePath)) {
                        visible.remove(category.getId());
                    }
                }
            }
        }

        // 2) 状态过滤（保留命中节点的祖先）
        if (status != null) {
            visible = keepMatchedWithAncestors(all, visible,
                    category -> Objects.equals(status, category.getStatus()));
        }

        // 3) 关键字过滤（保留命中节点的祖先）
        if (StringUtils.isNotBlank(keyword)) {
            final String kw = keyword.trim().toLowerCase();
            visible = keepMatchedWithAncestors(all, visible,
                    category -> containsIgnoreCase(category.getName(), kw)
                            || containsIgnoreCase(category.getCode(), kw)
                            || containsIgnoreCase(category.getAliasName(), kw));
        }

        // 4) 组装树
        List<ArchiveCategory> roots = new ArrayList<>();
        for (ArchiveCategory category : all) {
            if (!visible.contains(category.getId())) {
                continue;
            }
            String parentId = normalizeParentId(category.getParentId());
            ArchiveCategory parent = parentId == null ? null : byId.get(parentId);
            if (parent != null && visible.contains(parent.getId())) {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(category);
            } else {
                roots.add(category);
            }
        }
        return roots;
    }

    @Override
    public ArchiveCategory queryDetail(String id) {
        ArchiveCategory row = getById(id);
        if (row == null) {
            return null;
        }
        // 同样复制一份，避免把一级缓存里的实体对象（可能残留 children）直接返回出去
        ArchiveCategory detail = new ArchiveCategory();
        BeanUtils.copyProperties(row, detail);
        detail.setChildren(null);
        fillArchiveCount(java.util.Collections.singletonList(detail));
        detail.setFullPathName(buildFullPathName(row));
        if (StringUtils.isNotBlank(row.getParentId())) {
            ArchiveCategory parent = getById(row.getParentId());
            if (parent != null) {
                detail.setParentName(parent.getName());
            }
        }
        return detail;
    }

    // ==================================================================
    // 新增
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCategory(ArchiveCategory category) {
        if (category == null || StringUtils.isBlank(category.getName())) {
            throw new JeecgBootException("类别名称不能为空");
        }
        String name = category.getName().trim();
        String parentId = normalizeParentId(category.getParentId());

        ArchiveCategory parent = null;
        if (parentId != null) {
            parent = getById(parentId);
            if (parent == null) {
                throw new JeecgBootException("上级类别不存在或已被移除");
            }
            if (!Integer.valueOf(ArchiveCategory.STATUS_ENABLED).equals(parent.getStatus())) {
                throw new JeecgBootException("上级类别「" + parent.getName() + "」已停用，请先启用上级类别");
            }
        }
        checkNameUnique(parentId, name, null);

        String id = IdWorker.getIdStr();
        ArchiveCategory entity = new ArchiveCategory()
                .setId(id)
                .setParentId(parentId)
                .setPath(parent == null ? PATH_SEPARATOR + id : parent.getPath() + PATH_SEPARATOR + id)
                .setName(name)
                .setCode(trimToNull(category.getCode()))
                .setAliasName(trimToNull(category.getAliasName()))
                .setNote(trimToNull(category.getNote()))
                .setLevel(parent == null ? 1 : parent.getLevel() + 1)
                .setHasChildren(0)
                .setIsLeaf(1)
                .setSortNo(nextSortNo(parentId))
                .setStatus(category.getStatus() == null ? ArchiveCategory.STATUS_ENABLED : category.getStatus())
                .setDelFlag(0);

        save(entity);
        if (parent != null) {
            refreshParentFlags(parent.getId());
        }
        log.info("新增档案类别成功：id={}, name={}, parentId={}", id, name, parentId);
    }

    // ==================================================================
    // 编辑 / 更名 / 移动
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editCategory(ArchiveCategory category) {
        if (category == null || StringUtils.isBlank(category.getId())) {
            throw new JeecgBootException("缺少类别ID");
        }
        ArchiveCategory old = getById(category.getId());
        if (old == null) {
            throw new JeecgBootException("未找到对应的档案类别");
        }
        String name = StringUtils.isBlank(category.getName()) ? old.getName() : category.getName().trim();
        String oldParentId = normalizeParentId(old.getParentId());
        // 上级类别约定：字段缺失（null）= 不改上级；传 "" 或 "0" = 移动到顶级；其余 = 移动到指定上级。
        // 这样即便调用方只传了要改的字段，也不会把类别意外提到顶级。
        boolean parentSpecified = category.getParentId() != null;
        String newParentId = parentSpecified ? normalizeParentId(category.getParentId()) : oldParentId;
        boolean parentChanged = parentSpecified && !Objects.equals(newParentId, oldParentId);

        checkNameUnique(newParentId, name, old.getId());

        ArchiveCategory newParent = null;
        if (parentChanged && newParentId != null) {
            newParent = getById(newParentId);
            if (newParent == null) {
                throw new JeecgBootException("上级类别不存在或已被移除");
            }
            if (isSelfOrDescendant(newParent, old.getPath())) {
                throw new JeecgBootException("不能把类别移动到它自己或它的下级类别中");
            }
            if (!Integer.valueOf(ArchiveCategory.STATUS_ENABLED).equals(newParent.getStatus())
                    && Integer.valueOf(ArchiveCategory.STATUS_ENABLED).equals(old.getStatus())) {
                throw new JeecgBootException("上级类别「" + newParent.getName() + "」已停用，请先启用上级类别");
            }
        }

        // 启停：停用需要级联到整棵子树
        if (category.getStatus() != null && !Objects.equals(category.getStatus(), old.getStatus())) {
            applyStatus(old, category.getStatus());
        }

        ArchiveCategory update = new ArchiveCategory()
                .setId(old.getId())
                .setName(name)
                .setCode(trimToNull(category.getCode()))
                .setAliasName(trimToNull(category.getAliasName()))
                .setNote(trimToNull(category.getNote()));
        if (category.getSortNo() != null) {
            update.setSortNo(category.getSortNo());
        }
        if (category.getStatus() != null) {
            update.setStatus(category.getStatus());
        }

        if (parentChanged) {
            String newPath = newParent == null
                    ? PATH_SEPARATOR + old.getId()
                    : newParent.getPath() + PATH_SEPARATOR + old.getId();
            int newLevel = newParent == null ? 1 : newParent.getLevel() + 1;
            update.setParentId(newParentId)
                    .setPath(newPath)
                    .setLevel(newLevel);
            baseMapper.refreshSubtreePath(old.getPath(), newPath, newLevel - old.getLevel());
        }

        updateById(update);

        if (parentChanged) {
            if (oldParentId != null) {
                refreshParentFlags(oldParentId);
            }
            if (newParentId != null) {
                refreshParentFlags(newParentId);
            }
        }
        log.info("编辑档案类别成功：id={}, name={}, 上级变化={}", old.getId(), name, parentChanged);
    }

    // ==================================================================
    // 移除
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(String id) {
        ArchiveCategory category = getById(id);
        if (category == null) {
            throw new JeecgBootException("未找到对应的档案类别");
        }

        // 前置校验 ①：不能有子类别
        long childCount = count(new QueryWrapper<ArchiveCategory>().eq("parent_id", id));
        if (childCount > 0) {
            throw new JeecgBootException("该类别下存在 " + childCount + " 个子类别，请先移除子类别");
        }

        // 前置校验 ②：该类别（含全部子类别）下不能有档案文件
        if (archiveTableExists()) {
            Long archiveCount = queryArchiveCountOfSubtree(id, category.getPath());
            if (archiveCount != null && archiveCount > 0) {
                throw new JeecgBootException("该类别（含子类别）下存在 " + archiveCount + " 份档案，无法移除");
            }
        } else {
            log.warn("t_archive_file 表尚未建立（档案管理模块未落地），类别移除跳过「类别下有档案」校验：id={}, name={}",
                    id, category.getName());
        }

        removeById(id);
        String parentId = normalizeParentId(category.getParentId());
        if (parentId != null) {
            refreshParentFlags(parentId);
        }
        log.info("移除档案类别成功：id={}, name={}", id, category.getName());
    }

    // ==================================================================
    // 排序 / 移动排序
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortCategory(ArchiveCategorySortDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("缺少排序参数");
        }
        String parentId = normalizeParentId(dto.getParentId());
        List<ArchiveCategory> siblings = list(siblingQuery(parentId));
        Set<String> siblingIds = new HashSet<>();
        for (ArchiveCategory sibling : siblings) {
            siblingIds.add(sibling.getId());
        }
        List<ArchiveCategory> ordered = new ArrayList<>();
        Set<String> handled = new HashSet<>();
        if (dto.getOrderedIds() != null) {
            for (String id : dto.getOrderedIds()) {
                if (StringUtils.isBlank(id) || !siblingIds.contains(id)) {
                    throw new JeecgBootException("排序数据与类别树不一致，请刷新页面后重试");
                }
                if (handled.add(id)) {
                    ordered.add(new ArchiveCategory().setId(id));
                }
            }
        }
        // 未出现在 orderedIds 里的同级节点，按原排序号补在后面，保证 sort_no 连续且唯一
        siblings.sort(Comparator.comparing(ArchiveCategory::getSortNo,
                Comparator.nullsLast(Comparator.naturalOrder())));
        for (ArchiveCategory sibling : siblings) {
            if (handled.add(sibling.getId())) {
                ordered.add(new ArchiveCategory().setId(sibling.getId()));
            }
        }
        int sortNo = 1;
        for (ArchiveCategory category : ordered) {
            category.setSortNo(sortNo++);
        }
        if (ordered.isEmpty()) {
            log.info("档案类别排序请求为空，无需更新：parentId={}", parentId);
            return;
        }
        updateBatchById(ordered);
        log.info("档案类别排序成功：parentId={}, 共 {} 项", parentId, ordered.size());
    }

    // ==================================================================
    // 启停
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String id, Integer status) {
        ArchiveCategory category = getById(id);
        if (category == null) {
            throw new JeecgBootException("未找到对应的档案类别");
        }
        if (status == null
                || (status != ArchiveCategory.STATUS_ENABLED && status != ArchiveCategory.STATUS_DISABLED)) {
            throw new JeecgBootException("状态参数不正确");
        }
        applyStatus(category, status);
        log.info("档案类别状态变更：id={}, name={}, status={}", id, category.getName(), status);
    }

    /**
     * 执行状态变更。停用级联子树；启用前校验上级状态。
     */
    private void applyStatus(ArchiveCategory category, int status) {
        if (status == ArchiveCategory.STATUS_DISABLED) {
            baseMapper.updateStatusByPathPrefix(category.getPath(), ArchiveCategory.STATUS_DISABLED, currentUsername());
            return;
        }
        String parentId = normalizeParentId(category.getParentId());
        if (parentId != null) {
            ArchiveCategory parent = getById(parentId);
            if (parent != null && !Integer.valueOf(ArchiveCategory.STATUS_ENABLED).equals(parent.getStatus())) {
                throw new JeecgBootException("上级类别「" + parent.getName() + "」已停用，请先启用上级类别");
            }
        }
        updateById(new ArchiveCategory().setId(category.getId()).setStatus(ArchiveCategory.STATUS_ENABLED));
    }

    // ==================================================================
    // 校验
    // ==================================================================

    @Override
    public void checkNameUnique(String parentId, String name, String excludeId) {
        if (StringUtils.isBlank(name)) {
            throw new JeecgBootException("类别名称不能为空");
        }
        QueryWrapper<ArchiveCategory> wrapper = new QueryWrapper<>();
        wrapper.eq("name", name.trim());
        applyParentCondition(wrapper, normalizeParentId(parentId));
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne("id", excludeId);
        }
        if (count(wrapper) > 0) {
            throw new JeecgBootException("同级下已存在名为「" + name.trim() + "」的类别");
        }
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    /** parentId 归一化：空串 / "0" / "null" → null（顶级） */
    private String normalizeParentId(String parentId) {
        if (StringUtils.isBlank(parentId) || "0".equals(parentId.trim()) || "null".equalsIgnoreCase(parentId.trim())) {
            return null;
        }
        return parentId.trim();
    }

    /** 同级查询条件：顶级用 parent_id is null or ''（兼容历史数据） */
    private QueryWrapper<ArchiveCategory> siblingQuery(String parentId) {
        QueryWrapper<ArchiveCategory> wrapper = new QueryWrapper<>();
        applyParentCondition(wrapper, parentId);
        return wrapper;
    }

    private void applyParentCondition(QueryWrapper<ArchiveCategory> wrapper, String parentId) {
        if (parentId == null) {
            wrapper.and(w -> w.isNull("parent_id").or().eq("parent_id", ""));
        } else {
            wrapper.eq("parent_id", parentId);
        }
    }

    /** 同级末尾排序号 */
    private int nextSortNo(String parentId) {
        List<ArchiveCategory> siblings = list(siblingQuery(parentId));
        int max = 0;
        for (ArchiveCategory sibling : siblings) {
            if (sibling.getSortNo() != null && sibling.getSortNo() > max) {
                max = sibling.getSortNo();
            }
        }
        return max + 1;
    }

    /** 维护父节点的 has_children / is_leaf */
    private void refreshParentFlags(String parentId) {
        if (StringUtils.isBlank(parentId)) {
            return;
        }
        long childCount = count(new QueryWrapper<ArchiveCategory>().eq("parent_id", parentId));
        updateById(new ArchiveCategory()
                .setId(parentId)
                .setHasChildren(childCount > 0 ? 1 : 0)
                .setIsLeaf(childCount > 0 ? 0 : 1));
    }

    /** 判断 node 是否为 path 对应的节点自身或其后代 */
    private boolean isSelfOrDescendant(ArchiveCategory node, String path) {
        if (node == null || StringUtils.isBlank(node.getPath()) || StringUtils.isBlank(path)) {
            return false;
        }
        return node.getPath().equals(path) || node.getPath().startsWith(path + PATH_SEPARATOR);
    }

    /**
     * 过滤：只保留「命中谓词的节点」以及它们在 scope 内的所有祖先。
     */
    private Set<String> keepMatchedWithAncestors(List<ArchiveCategory> all,
                                                 Set<String> scope,
                                                 java.util.function.Predicate<ArchiveCategory> predicate) {
        Set<String> keep = new HashSet<>();
        for (ArchiveCategory category : all) {
            if (!scope.contains(category.getId()) || !predicate.test(category)) {
                continue;
            }
            keep.add(category.getId());
            for (ArchiveCategory candidate : all) {
                if (scope.contains(candidate.getId())
                        && !candidate.getId().equals(category.getId())
                        && category.getPath() != null
                        && candidate.getPath() != null
                        && category.getPath().startsWith(candidate.getPath() + PATH_SEPARATOR)) {
                    keep.add(candidate.getId());
                }
            }
        }
        return keep;
    }

    /** 按 path 逐级拼出「基本建设手续 / 项目建议书批复」这样的全路径名称 */
    private String buildFullPathName(ArchiveCategory category) {
        if (category == null || StringUtils.isBlank(category.getPath())) {
            return category == null ? null : category.getName();
        }
        String[] ids = category.getPath().split(PATH_SEPARATOR);
        Map<String, String> nameById = new HashMap<>();
        List<String> pathIds = new ArrayList<>();
        for (String id : ids) {
            if (StringUtils.isNotBlank(id)) {
                pathIds.add(id);
            }
        }
        List<ArchiveCategory> nodes = listByIds(pathIds);
        for (ArchiveCategory node : nodes) {
            nameById.put(node.getId(), node.getName());
        }
        List<String> names = new ArrayList<>();
        for (String id : pathIds) {
            String name = nameById.get(id);
            if (StringUtils.isNotBlank(name)) {
                names.add(name);
            }
        }
        return names.isEmpty() ? category.getName() : String.join(" / ", names);
    }

    /**
     * t_archive_file 表是否存在。首次调用探测一次并缓存。
     */
    private boolean archiveTableExists() {
        if (archiveTablePresent == null) {
            synchronized (this) {
                if (archiveTablePresent == null) {
                    try {
                        archiveTablePresent = baseMapper.countTable(ARCHIVE_FILE_TABLE) > 0;
                    } catch (Exception e) {
                        log.error("探测 {} 表是否存在失败，按不存在处理（将跳过档案数量前置校验）", ARCHIVE_FILE_TABLE, e);
                        archiveTablePresent = Boolean.FALSE;
                    }
                }
            }
        }
        return archiveTablePresent;
    }

    /**
     * 查「某类别及其整棵子树」下的档案文件数。
     *
     * <p>只有 t_archive_file 已建表时才能调用（调用方先判断 {@link #archiveTableExists()}）。
     */
    private Long queryArchiveCountOfSubtree(String categoryId, String categoryPath) {
        if (StringUtils.isBlank(categoryPath)) {
            // path 异常时退化为「只统计本类别」，避免因为一条脏数据把整棵树卡死
            return queryOwnArchiveCount(categoryId);
        }
        return (long) baseMapper.countArchiveByCategoryPath(categoryPath);
    }

    /** 查单个类别下的档案数量（精确匹配 category_id，不含子类别） */
    private Long queryOwnArchiveCount(String categoryId) {
        List<Map<String, Object>> rows = baseMapper.countArchiveGroupByCategory();
        for (Map<String, Object> row : rows) {
            Object id = row.get("categoryId");
            if (id != null && categoryId.equals(String.valueOf(id))) {
                Object cnt = row.get("cnt");
                return cnt == null ? 0L : Long.valueOf(String.valueOf(cnt));
            }
        }
        return 0L;
    }

    /** 填充 archiveCount；t_archive_file 未建立时保持 null（前端据此提示校验未启用） */
    private void fillArchiveCount(List<ArchiveCategory> categories) {
        if (categories == null || categories.isEmpty() || !archiveTableExists()) {
            return;
        }
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : baseMapper.countArchiveGroupByCategory()) {
            Object id = row.get("categoryId");
            if (id == null) {
                continue;
            }
            Object cnt = row.get("cnt");
            counts.put(String.valueOf(id), cnt == null ? 0L : Long.valueOf(String.valueOf(cnt)));
        }
        for (ArchiveCategory category : categories) {
            category.setArchiveCount(counts.getOrDefault(category.getId(), 0L));
        }
    }

    /** 当前登录账号；无登录上下文时返回 null（由拦截器 / 库默认值兜底） */
    private String currentUsername() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser) {
                return ((LoginUser) principal).getUsername();
            }
        } catch (Exception e) {
            log.debug("获取当前登录用户失败：{}", e.getMessage());
        }
        return null;
    }

    private static boolean containsIgnoreCase(String source, String lowerKeyword) {
        return source != null && source.toLowerCase().contains(lowerKeyword);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
