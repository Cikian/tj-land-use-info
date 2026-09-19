package org.jeecg.modules.land.archive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.entity.ArchiveCategory;
import org.jeecg.modules.land.archive.entity.ArchiveFile;
import org.jeecg.modules.land.archive.mapper.ArchiveCategoryMapper;
import org.jeecg.modules.land.archive.mapper.ArchiveFileMapper;
import org.jeecg.modules.land.archive.mapper.ArchiveMapper;
import org.jeecg.modules.land.archive.service.IArchiveFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 档案文件（卷内文件）Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Slf4j
@Service
public class ArchiveFileServiceImpl extends ServiceImpl<ArchiveFileMapper, ArchiveFile>
        implements IArchiveFileService {

    /** 上传根目录（jeecg.path.upload），dev 为 E:/work-space/upFiles/tj-land-use-info */
    @Value("${jeecg.path.upload}")
    private String uploadRoot;

    @Autowired
    private ArchiveCategoryMapper archiveCategoryMapper;

    /** 直接注入 Mapper 而不是 IArchiveService —— 避免 Service 之间形成循环依赖 */
    @Autowired
    private ArchiveMapper archiveMapper;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public List<ArchiveFile> queryByArchiveId(String archiveId) {
        if (StringUtils.isBlank(archiveId)) {
            return Collections.emptyList();
        }
        return enrichAll(baseMapper.selectByArchiveId(archiveId));
    }

    @Override
    public Map<String, List<ArchiveFile>> queryByArchiveIds(List<String> archiveIds) {
        if (archiveIds == null || archiveIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ArchiveFile> files = baseMapper.selectByArchiveIds(archiveIds);
        Map<String, List<ArchiveFile>> grouped = new java.util.LinkedHashMap<>();
        for (ArchiveFile file : enrichAll(files)) {
            grouped.computeIfAbsent(file.getArchiveId(), key -> new ArrayList<>()).add(file);
        }
        return grouped;
    }

    @Override
    public ArchiveFile queryFileById(String fileId) {
        if (StringUtils.isBlank(fileId)) {
            return null;
        }
        return enrich(baseMapper.selectFileById(fileId));
    }

    // ==================================================================
    // 新增 / 修改
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFile(String archiveId, ArchiveFile file) {
        saveFiles(archiveId, Collections.singletonList(file));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFiles(String archiveId, List<ArchiveFile> files) {
        if (StringUtils.isBlank(archiveId)) {
            throw new JeecgBootException("缺少档案ID，无法保存文件");
        }
        if (files == null || files.isEmpty()) {
            return;
        }
        int seq = nextSeqNo(archiveId);
        List<ArchiveFile> entities = new ArrayList<>(files.size());
        for (ArchiveFile file : files) {
            if (file == null) {
                continue;
            }
            if (StringUtils.isBlank(file.getStorePath())) {
                throw new JeecgBootException("文件「" + safeName(file) + "」缺少存储路径，请重新上传");
            }
            ArchiveCategory category = loadLeafCategory(file.getCategoryId());

            // 类别冗余：列表/统计/查询都靠这三个字段，落库时一次写死
            file.setCategoryPath(category.getPath());
            file.setCategoryName(buildFullPathName(category));

            file.setId(IdWorker.getIdStr())
                    .setArchiveId(archiveId)
                    .setSeqNo(seq++)
                    .setSortNo(seq)
                    .setDelFlag(0);
            if (StringUtils.isBlank(file.getFileExt())) {
                file.setFileExt(resolveExt(file.getFileName()));
            }
            if (StringUtils.isBlank(file.getFileTitle())) {
                file.setFileTitle(stripExt(file.getFileName()));
            }
            if (StringUtils.isBlank(file.getStoreType())) {
                file.setStoreType("local");
            }
            if (StringUtils.isBlank(file.getStatus())) {
                file.setStatus(ArchiveFile.STATUS_ARCHIVED);
            }
            entities.add(file);
        }
        if (entities.isEmpty()) {
            return;
        }
        saveBatch(entities);
        refreshArchiveFileStats(archiveId);
        log.info("档案文件保存成功：archiveId={}, 共 {} 个文件", archiveId, entities.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFile(ArchiveFile file) {
        if (file == null || StringUtils.isBlank(file.getId())) {
            throw new JeecgBootException("缺少文件ID");
        }
        ArchiveFile old = baseMapper.selectFileById(file.getId());
        if (old == null) {
            throw new JeecgBootException("文件不存在或已被删除");
        }
        ArchiveFile update = new ArchiveFile().setId(old.getId());
        boolean categoryChanged = StringUtils.isNotBlank(file.getCategoryId())
                && !file.getCategoryId().equals(old.getCategoryId());
        if (categoryChanged) {
            ArchiveCategory category = loadLeafCategory(file.getCategoryId());
            update.setCategoryId(category.getId())
                    .setCategoryPath(category.getPath())
                    .setCategoryName(buildFullPathName(category));
        }
        if (file.getFileTitle() != null) {
            update.setFileTitle(trimToNull(file.getFileTitle()));
        }
        if (file.getRemark() != null) {
            update.setRemark(trimToNull(file.getRemark()));
        }
        if (StringUtils.isNotBlank(file.getStatus())) {
            update.setStatus(file.getStatus());
        }
        if (file.getSortNo() != null) {
            update.setSortNo(file.getSortNo());
        }
        updateById(update);
        log.info("档案文件更新成功：fileId={}, 类别变化={}", old.getId(), categoryChanged);
    }

    // ==================================================================
    // 删除
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(String fileId) {
        ArchiveFile file = baseMapper.selectFileById(fileId);
        if (file == null) {
            throw new JeecgBootException("文件不存在或已被删除");
        }
        removeById(fileId);
        refreshArchiveFileStats(file.getArchiveId());
        log.info("档案文件删除成功：fileId={}, archiveId={}", fileId, file.getArchiveId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByArchiveId(String archiveId) {
        if (StringUtils.isBlank(archiveId)) {
            return;
        }
        remove(new QueryWrapper<ArchiveFile>().eq("archive_id", archiveId));
        refreshArchiveFileStats(archiveId);
    }

    // ==================================================================
    // 冗余统计
    // ==================================================================

    @Override
    public void refreshArchiveFileStats(String archiveId) {
        if (StringUtils.isBlank(archiveId)) {
            return;
        }
        Map<String, Object> row = baseMapper.sumByArchiveId(archiveId);
        long fileCount = 0L;
        long totalSize = 0L;
        if (row != null) {
            fileCount = toLong(row.get("fileCount"));
            totalSize = toLong(row.get("totalSize"));
        }
        // 直接用 Mapper 更新两列：不用 updateById 是为了避免把整条档案（含大批字段）读出来再写回
        Archive patch = new Archive()
                .setId(archiveId)
                .setFileCount((int) fileCount)
                .setTotalSize(totalSize);
        archiveMapper.updateById(patch);
    }

    // ==================================================================
    // 路径与 URL
    // ==================================================================

    @Override
    public String buildUrl(ArchiveFile file) {
        if (file == null || StringUtils.isBlank(file.getStorePath())) {
            return null;
        }
        String path = file.getStorePath().trim();
        // store_path 由 jeecg 的 /sys/common/upload 返回，形如 /archive/2026/09/xxx.pdf；
        // 统一成不含前导斜杠的相对路径，交给前端的 staticDomainURL 拼接。
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    @Override
    public String resolveAbsolutePath(ArchiveFile file) {
        if (file == null || StringUtils.isBlank(file.getStorePath())) {
            return null;
        }
        String relative = file.getStorePath().replace('\\', '/').trim();
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        // 路径穿越防护：store_path 只允许「相对路径」，出现 .. 一律拒绝
        if (relative.contains("..")) {
            log.warn("检测到疑似路径穿越的 store_path，已拒绝：fileId={}, storePath={}",
                    file.getId(), file.getStorePath());
            return null;
        }
        File root = new File(uploadRoot);
        File target = new File(root, relative);
        try {
            String rootPath = root.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            if (!targetPath.startsWith(rootPath + File.separator) && !targetPath.equals(rootPath)) {
                log.warn("文件绝对路径越出上传根目录，已拒绝：fileId={}, target={}", file.getId(), targetPath);
                return null;
            }
            return targetPath;
        } catch (Exception e) {
            log.warn("解析文件绝对路径失败：fileId={}, 原因={}", file.getId(), e.getMessage());
            return null;
        }
    }

    // ==================================================================
    // 补齐展示字段
    // ==================================================================

    /** 给单个文件补 url 与可读大小 */
    public ArchiveFile enrich(ArchiveFile file) {
        if (file == null) {
            return null;
        }
        file.setUrl(buildUrl(file));
        file.setReadableSize(formatSize(file.getFileSize()));
        return file;
    }

    /** 批量补齐 */
    public List<ArchiveFile> enrichAll(List<ArchiveFile> files) {
        if (files == null || files.isEmpty()) {
            return files;
        }
        for (ArchiveFile file : files) {
            enrich(file);
        }
        return files;
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    /** 下一个卷内序号 */
    private int nextSeqNo(String archiveId) {
        Map<String, Object> row = baseMapper.sumByArchiveId(archiveId);
        long count = row == null ? 0L : toLong(row.get("fileCount"));
        return (int) count + 1;
    }

    /**
     * 校验并取出叶子类别。
     * 三条规则：存在 → 已启用 → 是叶子（只有叶子才能挂档案）。
     */
    private ArchiveCategory loadLeafCategory(String categoryId) {
        if (StringUtils.isBlank(categoryId)) {
            throw new JeecgBootException("每个文件都必须选择档案类别");
        }
        ArchiveCategory category = archiveCategoryMapper.selectById(categoryId.trim());
        if (category == null) {
            throw new JeecgBootException("档案类别不存在或已被移除，请重新选择");
        }
        if (!Integer.valueOf(ArchiveCategory.STATUS_ENABLED).equals(category.getStatus())) {
            throw new JeecgBootException("档案类别「" + category.getName() + "」已停用，请重新选择");
        }
        if (!Integer.valueOf(1).equals(category.getIsLeaf())) {
            throw new JeecgBootException("「" + category.getName() + "」还有下级类别，请选择到最末一级");
        }
        return category;
    }

    /** 按 path 逐级拼出「基本建设手续 / 项目建议书批复」 */
    private String buildFullPathName(ArchiveCategory category) {
        if (category == null || StringUtils.isBlank(category.getPath())) {
            return category == null ? null : category.getName();
        }
        List<String> pathIds = new ArrayList<>();
        for (String id : category.getPath().split("/")) {
            if (StringUtils.isNotBlank(id)) {
                pathIds.add(id);
            }
        }
        if (pathIds.isEmpty()) {
            return category.getName();
        }
        List<ArchiveCategory> nodes = archiveCategoryMapper.selectBatchIds(new HashSet<>(pathIds));
        Map<String, String> nameById = new java.util.HashMap<>();
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

    private static String resolveExt(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private static String stripExt(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String safeName(ArchiveFile file) {
        return StringUtils.isBlank(file.getFileName()) ? "（未命名）" : file.getFileName();
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 人性化文件大小 */
    public static String formatSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "—";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double size = bytes;
        int unit = 0;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit++;
        }
        return unit == 0
                ? ((long) size) + " " + units[unit]
                : String.format("%.2f %s", size, units[unit]);
    }

    /** 供外部（导出/统计）复用的类别集合工具：取出 path 的顶级 id（path 是「主键串」，第一段即顶级） */
    public static String topLevelIdOfPath(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        for (String id : path.split("/")) {
            if (StringUtils.isNotBlank(id)) {
                return id;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
