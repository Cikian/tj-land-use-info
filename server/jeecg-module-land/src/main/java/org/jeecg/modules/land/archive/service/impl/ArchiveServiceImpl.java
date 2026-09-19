package org.jeecg.modules.land.archive.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.dto.ArchiveQueryDTO;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.entity.ArchiveCategory;
import org.jeecg.modules.land.archive.entity.ArchiveFile;
import org.jeecg.modules.land.archive.entity.ArchiveLog;
import org.jeecg.modules.land.archive.mapper.ArchiveMapper;
import org.jeecg.modules.land.archive.service.IArchiveCategoryService;
import org.jeecg.modules.land.archive.service.IArchiveFileService;
import org.jeecg.modules.land.archive.service.IArchiveLogService;
import org.jeecg.modules.land.archive.service.IArchiveService;
import org.jeecg.modules.land.archive.vo.ArchiveStatVO;
import org.jeecgframework.poi.excel.ExcelExportUtil;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.entity.params.ExcelExportEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @Description: 档案主表 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>关键约束（与设计文档的差异见 {@link Archive} 的类注释）：
 * <ul>
 *   <li>档案类别挂在<b>文件</b>上，档案本身没有类别，所以列表/统计里说的
 *       「档案类别」都是由卷内文件聚合出来的；</li>
 *   <li>档案主体是<b>配套项目</b>，但录入要先选宗地再联动选项目，
 *       两个维度都冗余落库，导出目录也是「宗地 / 项目 / 类别 / 档案 / 文件」；</li>
 *   <li>列表、统计、导出<b>共用同一套查询条件</b>（{@link ArchiveQueryDTO}），
 *       避免出现「列表 20 条、统计 35 条」这种对不上的情况。</li>
 * </ul>
 */
@Slf4j
@Service
public class ArchiveServiceImpl extends ServiceImpl<ArchiveMapper, Archive> implements IArchiveService {

    /** 档案号前缀，完整格式 DA-{yyyy}-{4位流水} */
    private static final String ARCHIVE_NO_PREFIX = "DA-";

    /** 按项目统计的默认条数 */
    private static final int DEFAULT_PROJECT_LIMIT = 20;

    /** 档案号生成的最大重试次数（并发下唯一键冲突时重试） */
    private static final int NO_RETRY = 3;

    @Autowired
    private IArchiveFileService archiveFileService;

    @Autowired
    private IArchiveLogService archiveLogService;

    @Autowired
    private IArchiveCategoryService archiveCategoryService;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public IPage<Archive> queryPage(ArchiveQueryDTO query) {
        ArchiveQueryDTO condition = query == null ? new ArchiveQueryDTO() : query;
        Page<Archive> page = new Page<>(condition.resolvePageNo(), condition.resolvePageSize());
        return baseMapper.selectArchivePage(page, condition);
    }

    @Override
    public Archive queryDetail(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        Archive archive = baseMapper.selectDetailById(id);
        if (archive == null) {
            return null;
        }
        archive.setFiles(archiveFileService.queryByArchiveId(id));
        return archive;
    }

    @Override
    public List<ArchiveFile> queryFiles(String archiveId) {
        return archiveFileService.queryByArchiveId(archiveId);
    }

    @Override
    public List<ArchiveLog> queryLogs(String archiveId) {
        return archiveLogService.queryByArchiveId(archiveId);
    }

    // ==================================================================
    // 档案号
    // ==================================================================

    @Override
    public String generateArchiveNo(Integer year) {
        int targetYear = year == null || year < 1900 || year > 2999
                ? Calendar.getInstance().get(Calendar.YEAR)
                : year;
        return nextArchiveNo(targetYear);
    }

    @Override
    public void checkArchiveNoUnique(String archiveNo, String excludeId) {
        if (StringUtils.isBlank(archiveNo)) {
            throw new JeecgBootException("档案号不能为空");
        }
        if (baseMapper.countByArchiveNo(archiveNo.trim(), excludeId) > 0) {
            throw new JeecgBootException("档案号「" + archiveNo.trim() + "」已存在，请更换");
        }
    }

    /** 按年度流水生成下一个档案号 */
    private String nextArchiveNo(int year) {
        String prefix = ARCHIVE_NO_PREFIX + year + "-";
        Integer max = baseMapper.selectMaxSeqOfYear(prefix);
        int next = (max == null ? 0 : max) + 1;
        return prefix + String.format("%04d", next);
    }

    // ==================================================================
    // 新增
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createArchive(Archive archive, List<ArchiveFile> files) {
        if (archive == null) {
            throw new JeecgBootException("缺少档案数据");
        }
        if (StringUtils.isBlank(archive.getArchiveName())) {
            throw new JeecgBootException("档案名称不能为空");
        }
        normalizeAssociation(archive);
        fillDefaults(archive);
        if (Archive.SOURCE_MANUAL.equals(archive.getSourceType()) && (files == null || files.isEmpty())) {
            // 手工录入必须有卷内文件；收文/发文归档过来的档案允许先建卷、后补扫描件
            throw new JeecgBootException("请至少上传一个档案文件");
        }

        // 档案号：手工填了就校验唯一；没填就自动生成（并发冲突时重试）
        JeecgBootException lastError = null;
        for (int attempt = 0; attempt < NO_RETRY; attempt++) {
            String archiveNo = StringUtils.isNotBlank(archive.getArchiveNo())
                    ? archive.getArchiveNo().trim()
                    : nextArchiveNo(resolveYear(archive));
            checkArchiveNoUnique(archiveNo, null);
            archive.setArchiveNo(archiveNo).setId(IdWorker.getIdStr());

            if (save(archive)) {
                lastError = null;
                break;
            }
            lastError = new JeecgBootException("档案保存失败，请重试");
        }
        if (lastError != null) {
            throw lastError;
        }

        if (files != null && !files.isEmpty()) {
            archiveFileService.saveFiles(archive.getId(), files);
        }
        archiveLogService.record(archive.getId(), null, ArchiveLog.ACTION_ADD,
                "新增档案【" + archive.getArchiveNo() + "】" + archive.getArchiveName(),
                archive.getArchiveNo());
        log.info("新增档案成功：id={}, archiveNo={}, 文件数={}",
                archive.getId(), archive.getArchiveNo(), files == null ? 0 : files.size());
        return archive.getId();
    }

    // ==================================================================
    // 编辑
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArchive(Archive archive, List<ArchiveFile> files) {
        if (archive == null || StringUtils.isBlank(archive.getId())) {
            throw new JeecgBootException("缺少档案ID");
        }
        Archive old = getById(archive.getId());
        if (old == null) {
            throw new JeecgBootException("档案不存在或已被删除");
        }
        if (StringUtils.isNotBlank(archive.getArchiveName())) {
            checkArchiveNoUnique(
                    StringUtils.isNotBlank(archive.getArchiveNo()) ? archive.getArchiveNo() : old.getArchiveNo(),
                    old.getId());
        }

        // 只更新允许修改的字段，避免前端漏传把数据清空
        Archive update = new Archive().setId(old.getId());
        update.setArchiveName(trimToNull(archive.getArchiveName()));
        update.setArchiveNo(trimToNull(archive.getArchiveNo()));
        update.setArchiveType(defaultIfBlank(archive.getArchiveType(), old.getArchiveType()));
        update.setSecretLevel(defaultIfBlank(archive.getSecretLevel(), old.getSecretLevel()));
        update.setRetention(trimToNull(archive.getRetention()));
        update.setResponsibleDept(trimToNull(archive.getResponsibleDept()));
        update.setResponsibleUser(trimToNull(archive.getResponsibleUser()));
        update.setArchiveDate(archive.getArchiveDate());
        update.setStatus(defaultIfBlank(archive.getStatus(), old.getStatus()));
        update.setRemark(trimToNull(archive.getRemark()));
        update.setArchiveYear(archive.getArchiveYear() != null ? archive.getArchiveYear() : old.getArchiveYear());
        // 宗地 / 配套项目允许改（换了项目要能重新关联）
        update.setLandId(trimToNull(archive.getLandId()));
        update.setFacilityId(trimToNull(archive.getFacilityId()));
        update.setCrzdbh(trimToNull(archive.getCrzdbh()));
        update.setPtxmmc(trimToNull(archive.getPtxmmc()));
        update.setDkmc(trimToNull(archive.getDkmc()));
        update.setPtsslb(trimToNull(archive.getPtsslb()));
        update.setXzqh(trimToNull(archive.getXzqh()));

        boolean statusChanged = StringUtils.isNotBlank(archive.getStatus())
                && !archive.getStatus().equals(old.getStatus());

        updateById(update);

        // 文件同步：新增 + 修改 + 删除
        syncFiles(old.getId(), files);

        String detail = buildEditDetail(old, archive);
        archiveLogService.record(old.getId(), null,
                statusChanged && Archive.STATUS_ARCHIVED.equals(archive.getStatus())
                        ? ArchiveLog.ACTION_ARCHIVE : ArchiveLog.ACTION_EDIT,
                detail, old.getArchiveNo());
        log.info("编辑档案成功：id={}, 状态变化={}", old.getId(), statusChanged);
    }

    /**
     * 文件同步：以前端提交的列表为准，做「新增 / 更新 / 删除」三向合并。
     *
     * <p>前端提交的每个条目：带 id 的是已存在文件（可改题名/类别/备注/状态），
     * 不带 id 的是新上传文件，服务端库里存在但前端没提交的视为已删除。
     */
    private void syncFiles(String archiveId, List<ArchiveFile> submitted) {
        List<ArchiveFile> submittedList = submitted == null ? new ArrayList<>() : submitted;
        List<ArchiveFile> exists = archiveFileService.queryByArchiveId(archiveId);
        Set<String> existsIds = new HashSet<>();
        for (ArchiveFile file : exists) {
            existsIds.add(file.getId());
        }

        Set<String> keptIds = new HashSet<>();
        List<ArchiveFile> toInsert = new ArrayList<>();
        for (ArchiveFile file : submittedList) {
            if (file == null) {
                continue;
            }
            String id = trimToNull(file.getId());
            if (id != null && existsIds.contains(id)) {
                keptIds.add(id);
                archiveFileService.updateFile(file);
            } else {
                file.setId(null);
                toInsert.add(file);
            }
        }

        if (!toInsert.isEmpty()) {
            archiveFileService.saveFiles(archiveId, toInsert);
        }
        for (ArchiveFile file : exists) {
            if (!keptIds.contains(file.getId())) {
                archiveFileService.deleteFile(file.getId());
            }
        }
        archiveFileService.refreshArchiveFileStats(archiveId);
    }

    // ==================================================================
    // 删除
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArchive(String id) {
        Archive archive = getById(id);
        if (archive == null) {
            throw new JeecgBootException("档案不存在或已被删除");
        }
        // 先删文件（同时会刷新统计），再删档案主表
        archiveFileService.deleteByArchiveId(id);
        removeById(id);
        archiveLogService.record(id, null, ArchiveLog.ACTION_DELETE,
                "删除档案【" + archive.getArchiveNo() + "】" + archive.getArchiveName(),
                archive.getArchiveNo());
        log.info("删除档案成功：id={}, archiveNo={}", id, archive.getArchiveNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArchives(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new JeecgBootException("请选择要删除的档案");
        }
        for (String id : ids) {
            if (StringUtils.isNotBlank(id)) {
                deleteArchive(id.trim());
            }
        }
    }

    // ==================================================================
    // 状态
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String id, String status) {
        Archive archive = getById(id);
        if (archive == null) {
            throw new JeecgBootException("档案不存在或已被删除");
        }
        if (StringUtils.isBlank(status)) {
            throw new JeecgBootException("状态参数不能为空");
        }
        String target = status.trim();
        if (target.equals(archive.getStatus())) {
            return;
        }
        if (Archive.STATUS_ARCHIVED.equals(target)) {
            long fileCount = archiveFileService.queryByArchiveId(id).size();
            if (fileCount == 0) {
                throw new JeecgBootException("该档案下没有卷内文件，不能置为「已归档」");
            }
            if (archive.getArchiveDate() == null) {
                updateById(new Archive().setId(id).setArchiveDate(new Date()));
            }
        }
        updateById(new Archive().setId(id).setStatus(target));
        archiveLogService.record(id, null, ArchiveLog.ACTION_ARCHIVE,
                "档案【" + archive.getArchiveNo() + "】状态 " + archive.getStatus() + " → " + target,
                archive.getArchiveNo());
    }

    // ==================================================================
    // 统计
    // ==================================================================

    @Override
    public ArchiveStatVO queryStat(ArchiveQueryDTO query, Integer projectLimit) {
        ArchiveQueryDTO condition = query == null ? new ArchiveQueryDTO() : query;
        ArchiveStatVO stat = new ArchiveStatVO();

        Map<String, Object> overview = baseMapper.selectStatOverview(condition);
        stat.setTotal(toLong(overview == null ? null : overview.get("total")));
        stat.setArchivedCount(toLong(overview == null ? null : overview.get("archivedCount")));
        stat.setUnarchivedCount(toLong(overview == null ? null : overview.get("unarchivedCount")));
        stat.setProjectCount(toLong(overview == null ? null : overview.get("projectCount")));

        // 文件数与总字节以 t_archive_file 实际数据为准（主表上的冗余字段只用于列表快速展示）
        Map<String, Object> fileOverview = baseMapper.selectStatFileOverview(condition);
        stat.setFileTotal(toLong(fileOverview == null ? null : fileOverview.get("fileTotal")));
        stat.setTotalSize(toLong(fileOverview == null ? null : fileOverview.get("totalSize")));

        stat.setByStatus(baseMapper.selectStatByStatus(condition));
        stat.setByYear(baseMapper.selectStatByYear(condition));
        stat.setBySecretLevel(baseMapper.selectStatBySecretLevel(condition));
        stat.setByXzqh(baseMapper.selectStatByXzqh(condition));
        stat.setByCategory(rollUpCategoryCount(condition));

        int limit = projectLimit == null || projectLimit <= 0 ? DEFAULT_PROJECT_LIMIT : Math.min(projectLimit, 200);
        stat.setByProject(baseMapper.selectStatByProject(condition, limit));
        return stat;
    }

    /**
     * 把「按叶子/任意层级类别的文件数」归并到<b>顶级类别</b>。
     *
     * <p>为什么要归并：原型图的横向条形图是「基本建设手续 45 / 规划手续 38 / …」，
     * 口径是方案原文明确的 6 大分类；若直接按叶子类别画图会得到上百根条。
     */
    private List<ArchiveStatVO.NameCount> rollUpCategoryCount(ArchiveQueryDTO condition) {
        List<Map<String, Object>> rows = baseMapper.selectStatFileByCategory(condition);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        // 类别树：id -> 实体（含 path / name），用于把任意层级的 category_id 归到顶级
        List<ArchiveCategory> categories = archiveCategoryService.list();
        Map<String, ArchiveCategory> byId = new HashMap<>();
        for (ArchiveCategory category : categories) {
            byId.put(category.getId(), category);
        }

        Map<String, Long> countByTop = new LinkedHashMap<>();
        Map<String, String> nameByTop = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String categoryId = asString(row.get("categoryId"));
            String categoryPath = asString(row.get("categoryPath"));
            String categoryName = asString(row.get("categoryName"));
            long cnt = toLong(row.get("cnt"));

            ArchiveCategory category = categoryId == null ? null : byId.get(categoryId);
            String topId = topLevelId(categoryId, categoryPath, category, byId);
            String topName = resolveTopName(topId, categoryName, byId);
            if (topId == null && topName == null) {
                topName = "未分类";
            }
            String key = topId != null ? topId : "unknown:" + topName;
            countByTop.merge(key, cnt, Long::sum);
            nameByTop.putIfAbsent(key, topName == null ? "未分类" : topName);
        }

        List<ArchiveStatVO.NameCount> result = new ArrayList<>(countByTop.size());
        for (Map.Entry<String, Long> entry : countByTop.entrySet()) {
            result.add(new ArchiveStatVO.NameCount(nameByTop.get(entry.getKey()), entry.getValue()));
        }
        result.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        return result;
    }

    /** 取类别所在子树的顶级类别ID：优先用 path 首段，退化时沿 parentId 上溯 */
    private String topLevelId(String categoryId, String categoryPath, ArchiveCategory category,
                              Map<String, ArchiveCategory> byId) {
        if (StringUtils.isNotBlank(categoryPath)) {
            String top = ArchiveFileServiceImpl.topLevelIdOfPath(categoryPath);
            if (StringUtils.isNotBlank(top)) {
                return top;
            }
        }
        ArchiveCategory current = category;
        if (current == null && categoryId != null) {
            current = byId.get(categoryId);
        }
        int guard = 0;
        while (current != null && StringUtils.isNotBlank(current.getParentId()) && guard++ < 50) {
            current = byId.get(current.getParentId());
        }
        return current == null ? categoryId : current.getId();
    }

    private String resolveTopName(String topId, String fallbackName, Map<String, ArchiveCategory> byId) {
        if (topId != null) {
            ArchiveCategory top = byId.get(topId);
            if (top != null) {
                return top.getName();
            }
        }
        return fallbackName;
    }

    // ==================================================================
    // 导出
    // ==================================================================

    @Override
    public File exportZip(ArchiveQueryDTO query, String operatorName) {
        ArchiveQueryDTO condition = query == null ? new ArchiveQueryDTO() : query;
        List<Archive> archives = baseMapper.selectForExport(condition);
        List<Archive> list = archives == null ? Collections.emptyList() : archives;
        List<String> archiveIds = new ArrayList<>(list.size());
        for (Archive archive : list) {
            archiveIds.add(archive.getId());
        }
        Map<String, List<ArchiveFile>> filesByArchive = archiveFileService.queryByArchiveIds(archiveIds);

        File zipFile = null;
        try {
            zipFile = File.createTempFile("archive-export-", ".zip");
            Set<String> usedEntries = new HashSet<>();
            List<Map<String, Object>> manifest = new ArrayList<>();

            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(zipFile)), ZIP_CHARSET)) {
                if (list.isEmpty()) {
                    writeTextEntry(zos, usedEntries, "没有符合条件的档案.txt",
                            "当前查询条件下没有可导出的档案，请调整查询条件后重试。");
                }
                for (Archive archive : list) {
                    List<ArchiveFile> files = filesByArchive.getOrDefault(archive.getId(), Collections.emptyList());
                    String archiveDir = safeSegment(archive.getCrzdbh(), "未关联宗地")
                            + "/" + safeSegment(archive.getPtxmmc(), "未关联项目");
                    if (files.isEmpty()) {
                        // 没有卷内文件的档案也要在包里留一个说明，否则用户会以为漏导了
                        manifest.add(buildManifestRow(archive, null, "无卷内文件"));
                        writeTextEntry(zos, usedEntries,
                                archiveDir + "/" + safeSegment(archive.getArchiveNo() + "_" + archive.getArchiveName(), "档案")
                                        + "/无卷内文件.txt",
                                "档案【" + archive.getArchiveNo() + "】" + archive.getArchiveName()
                                        + " 目前没有卷内文件。");
                        continue;
                    }
                    for (ArchiveFile file : files) {
                        String categoryDir = safeSegment(normalizeCategoryName(file.getCategoryName()), "未分类");
                        String archiveNameDir = safeSegment(
                                archive.getArchiveNo() + "_" + archive.getArchiveName(), "档案");
                        String fileName = safeSegment(
                                (file.getSeqNo() == null ? "" : file.getSeqNo() + "_") + file.getFileName(), "文件");
                        String entryName = archiveDir + "/" + categoryDir + "/" + archiveNameDir + "/" + fileName;

                        String absolutePath = archiveFileService.resolveAbsolutePath(file);
                        File source = absolutePath == null ? null : new File(absolutePath);
                        if (source != null && source.isFile()) {
                            writeFileEntry(zos, usedEntries, entryName, source);
                            manifest.add(buildManifestRow(archive, file, ""));
                        } else {
                            manifest.add(buildManifestRow(archive, file, "文件缺失（仅登记元数据）"));
                            writeTextEntry(zos, usedEntries, entryName + ".缺失说明.txt",
                                    "文件【" + file.getFileName() + "】在上传目录中未找到，"
                                            + "仅导出了档案元数据记录。存储路径：" + file.getStorePath());
                        }
                    }
                }
                writeManifest(zos, manifest);
            }
            archiveLogService.record(null, null, ArchiveLog.ACTION_EXPORT,
                    "导出档案，共 " + list.size() + " 卷，操作人：" + (operatorName == null ? "未知" : operatorName),
                    condition.getPtxmmc());
            log.info("档案导出完成：卷数={}, 文件={}", list.size(), manifest.size());
            return zipFile;
        } catch (Exception e) {
            if (zipFile != null && zipFile.exists() && !zipFile.delete()) {
                log.warn("导出失败后清理临时文件失败：{}", zipFile.getAbsolutePath());
            }
            throw new JeecgBootException("导出失败：" + e.getMessage());
        }
    }

    /**
     * ZIP 条目名使用 GBK 编码。
     *
     * <p>原因：Java 的 {@link ZipOutputStream} 若不写 UTF-8 标记位，Windows 资源管理器
     * 会按系统 ANSI 代码页（简体中文 = GBK）解析中文条目名；反之若用 UTF-8，
     * 在不支持 UTF-8 标记位的旧版 Windows 解压工具里中文会变成乱码。
     * 本系统的落地环境是简体中文 Windows 桌面，因此选择 GBK。
     */
    private static final Charset ZIP_CHARSET = Charset.forName("GBK");

    private void writeFileEntry(ZipOutputStream zos, Set<String> usedEntries,
                               String entryName, File source) throws IOException {
        String unique = uniqueEntryName(usedEntries, entryName);
        zos.putNextEntry(new ZipEntry(unique));
        try (InputStream in = Files.newInputStream(source.toPath())) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                zos.write(buffer, 0, len);
            }
        }
        zos.closeEntry();
    }

    private void writeTextEntry(ZipOutputStream zos, Set<String> usedEntries,
                                String entryName, String content) throws IOException {
        String unique = uniqueEntryName(usedEntries, entryName);
        zos.putNextEntry(new ZipEntry(unique));
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }

    /** 同目录下重名时追加 (2)、(3)…，保证 ZIP 内不丢文件 */
    private String uniqueEntryName(Set<String> usedEntries, String entryName) {
        String candidate = entryName;
        int index = 2;
        while (!usedEntries.add(candidate)) {
            int dot = entryName.lastIndexOf('.');
            candidate = dot > 0
                    ? entryName.substring(0, dot) + "(" + index + ")" + entryName.substring(dot)
                    : entryName + "(" + index + ")";
            index++;
        }
        return candidate;
    }

    private Map<String, Object> buildManifestRow(Archive archive, ArchiveFile file, String note) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("archiveNo", archive.getArchiveNo());
        row.put("archiveName", archive.getArchiveName());
        row.put("crzdbh", archive.getCrzdbh());
        row.put("ptxmmc", archive.getPtxmmc());
        row.put("categoryName", file == null ? "" : normalizeCategoryName(file.getCategoryName()));
        row.put("fileTitle", file == null ? "" : defaultIfBlank(file.getFileTitle(), file.getFileName()));
        row.put("fileName", file == null ? "" : file.getFileName());
        row.put("fileSize", file == null ? "" : ArchiveFileServiceImpl.formatSize(file.getFileSize()));
        row.put("archiveDate", formatDate(archive.getArchiveDate()));
        row.put("status", archive.getStatus());
        row.put("secretLevel", archive.getSecretLevel());
        row.put("note", note);
        return row;
    }

    /** 往 ZIP 根目录写一份 档案清单.xlsx */
    private void writeManifest(ZipOutputStream zos, List<Map<String, Object>> manifest) throws IOException {
        List<ExcelExportEntity> columns = new ArrayList<>();
        columns.add(new ExcelExportEntity("档案号", "archiveNo", 22));
        columns.add(new ExcelExportEntity("档案名称", "archiveName", 34));
        columns.add(new ExcelExportEntity("出让宗地编号", "crzdbh", 22));
        columns.add(new ExcelExportEntity("配套项目", "ptxmmc", 30));
        columns.add(new ExcelExportEntity("档案类别", "categoryName", 34));
        columns.add(new ExcelExportEntity("文件题名", "fileTitle", 30));
        columns.add(new ExcelExportEntity("文件名", "fileName", 30));
        columns.add(new ExcelExportEntity("大小", "fileSize", 12));
        columns.add(new ExcelExportEntity("归档日期", "archiveDate", 14));
        columns.add(new ExcelExportEntity("状态", "status", 10));
        columns.add(new ExcelExportEntity("密级", "secretLevel", 10));
        columns.add(new ExcelExportEntity("备注", "note", 24));

        /*
         * ★ 必须显式指定 XSSF，否则 easypoi 的 ExportParams 默认是 HSSF：
         *   HSSFWorkbook 写出的是 OLE2 复合文档（.xls 的二进制格式），首 8 字节 D0 CF 11 E0 A1 B1 1A E1；
         *   而我们把这个条目命名成「档案清单.xlsx」。名字是 xlsx、内容是 xls，
         *   Excel 直接拒绝打开并报「无法打开文件…因为文件格式或文件扩展名无效」。
         *   （注意这**不是**那个「扩展名与格式不匹配，是否仍要打开」的软提示，是硬报错。）
         *   jeecg 自己的 JeecgController#exportXls 在导出 .xlsx 时同样是显式 setType(ExcelType.XSSF)。
         */
        ExportParams exportParams = new ExportParams("档案清单", "档案清单");
        exportParams.setType(ExcelType.XSSF);

        Workbook workbook = ExcelExportUtil.exportExcel(exportParams, columns, manifest);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            zos.putNextEntry(new ZipEntry("档案清单.xlsx"));
            zos.write(bos.toByteArray());
            zos.closeEntry();
        } finally {
            workbook.close();
        }
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    /** 宗地/项目冗余字段补齐：只传了 facilityId 时由调用方负责补齐名称，这里做基础校验 */
    private void normalizeAssociation(Archive archive) {
        if (StringUtils.isBlank(archive.getFacilityId()) && StringUtils.isBlank(archive.getPtxmmc())) {
            throw new JeecgBootException("请选择关联的配套项目");
        }
    }

    private void fillDefaults(Archive archive) {
        if (StringUtils.isBlank(archive.getSourceType())) {
            archive.setSourceType(Archive.SOURCE_MANUAL);
        }
        if (StringUtils.isBlank(archive.getArchiveType())) {
            archive.setArchiveType(Archive.TYPE_ELECTRONIC);
        }
        if (StringUtils.isBlank(archive.getSecretLevel())) {
            archive.setSecretLevel("一般");
        }
        if (StringUtils.isBlank(archive.getStatus())) {
            archive.setStatus(Archive.STATUS_PENDING);
        }
        if (archive.getArchiveDate() == null) {
            archive.setArchiveDate(new Date());
        }
        if (archive.getArchiveYear() == null) {
            archive.setArchiveYear(resolveYear(archive));
        }
        archive.setDelFlag(0);
        if (archive.getFileCount() == null) {
            archive.setFileCount(0);
        }
        if (archive.getTotalSize() == null) {
            archive.setTotalSize(0L);
        }
    }

    private int resolveYear(Archive archive) {
        Date date = archive.getArchiveDate() == null ? new Date() : archive.getArchiveDate();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR);
    }

    /** 编辑差异描述，写进操作记录（方案要求「记录相关操作」） */
    private String buildEditDetail(Archive old, Archive now) {
        StringBuilder sb = new StringBuilder("修改档案【").append(old.getArchiveNo()).append("】");
        appendDiff(sb, "档案名称", old.getArchiveName(), now.getArchiveName());
        appendDiff(sb, "档案号", old.getArchiveNo(), now.getArchiveNo());
        appendDiff(sb, "密级", old.getSecretLevel(), now.getSecretLevel());
        appendDiff(sb, "保管期限", old.getRetention(), now.getRetention());
        appendDiff(sb, "责任部门", old.getResponsibleDept(), now.getResponsibleDept());
        appendDiff(sb, "配套负责人", old.getResponsibleUser(), now.getResponsibleUser());
        appendDiff(sb, "状态", old.getStatus(), now.getStatus());
        appendDiff(sb, "配套项目", old.getPtxmmc(), now.getPtxmmc());
        return sb.length() > 0 ? sb.toString() : "修改档案【" + old.getArchiveNo() + "】";
    }

    private void appendDiff(StringBuilder sb, String label, String before, String after) {
        String b = before == null ? "" : before;
        String a = after == null ? "" : after;
        if (!b.equals(a) && !a.isEmpty()) {
            sb.append("；").append(label).append(' ').append(b.isEmpty() ? "（空）" : b)
              .append(" → ").append(a);
        }
    }

    private static String normalizeCategoryName(String categoryName) {
        if (StringUtils.isBlank(categoryName)) {
            return "未分类";
        }
        // 类别的全路径名用「 / 」分隔，作为 ZIP 目录名时会把层级压平，这里换成「-」
        return categoryName.replace(" / ", "-").replace("/", "-");
    }

    /** ZIP 目录/文件名单段净化：去掉 Windows 非法字符与首尾空白 */
    private static String safeSegment(String value, String fallback) {
        String text = StringUtils.isBlank(value) ? fallback : value.trim();
        if (text == null || text.isEmpty()) {
            text = fallback;
        }
        text = text.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        // Windows 下目录名不能以点结尾
        while (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty()) {
            text = fallback;
        }
        return text.length() > 120 ? text.substring(0, 120) : text;
    }

    private static String formatDate(Date date) {
        return date == null ? "" : new SimpleDateFormat("yyyy-MM-dd").format(date);
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

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
