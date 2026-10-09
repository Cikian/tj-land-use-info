package org.jeecg.modules.land.escalation.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.escalation.entity.EscalationMaterial;
import org.jeecg.modules.land.escalation.entity.EscalationProject;
import org.jeecg.modules.land.escalation.mapper.EscalationMaterialMapper;
import org.jeecg.modules.land.escalation.mapper.EscalationProjectMapper;
import org.jeecg.modules.land.escalation.service.IEscalationMaterialService;
import org.jeecg.modules.land.escalation.support.EscalationAuditSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 提级论证材料 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>三件事是重点，其余都是常规 CRUD：
 * <ol>
 *   <li><b>上传校验</b>：扩展名白名单 + 单文件 ≤ 50MB（设计文档 4.4；上限取原型界面明示的 50MB，
 *       而<b>不是</b>档案模块的 200MB）；</li>
 *   <li><b>路径安全解析</b>：只按库里的 {@code store_path} 拼绝对路径，
 *       拒绝 {@code ..} 与越出上传根目录的情况（照抄档案模块写法）；</li>
 *   <li><b>冗余计数一致</b>：任何增删后都重算主表 {@code material_count}，
 *       用 COUNT(*) 重算而不是 ±1 累加，历史脏数据能被下一次写操作自动纠正。</li>
 * </ol>
 */
@Slf4j
@Service
public class EscalationMaterialServiceImpl extends ServiceImpl<EscalationMaterialMapper, EscalationMaterial>
        implements IEscalationMaterialService {

    /** 扩展名白名单（设计文档 4.4 逐项列出） */
    private static final Set<String> ALLOWED_EXT = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("pdf", "doc", "docx", "jpg", "jpeg", "png", "xls", "xlsx")));

    /** 单文件大小上限：50MB（原型界面明示「单个文件不超过 50MB」） */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 上传根目录（jeecg.path.upload），dev 为 E:/work-space/upFiles/tj-land-use-info */
    @Value("${jeecg.path.upload}")
    private String uploadRoot;

    @Autowired
    private EscalationAuditSupport auditSupport;

    /** 直接注入主表 Mapper（而不是 IEscalationProjectService）：避免 Service 之间形成循环依赖 */
    @Autowired
    private EscalationProjectMapper projectMapper;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public List<EscalationMaterial> queryByProjectId(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            return Collections.emptyList();
        }
        return enrichAll(baseMapper.selectByProjectId(projectId));
    }

    @Override
    public EscalationMaterial queryMaterialById(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        return enrich(baseMapper.selectMaterialById(id));
    }

    // ==================================================================
    // 新增 / 修改
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EscalationMaterial addMaterial(String projectId, EscalationMaterial material) {
        if (StringUtils.isBlank(projectId)) {
            throw new JeecgBootException("缺少提级论证项目ID，无法保存材料");
        }
        if (material == null) {
            throw new JeecgBootException("缺少材料数据");
        }
        validate(material);
        material.setId(IdWorker.getIdStr())
                .setProjectId(projectId)
                .setDelFlag(0);
        if (StringUtils.isBlank(material.getFileExt())) {
            material.setFileExt(resolveExt(material.getFileName()));
        }
        if (StringUtils.isBlank(material.getStoreType())) {
            material.setStoreType(EscalationMaterial.STORE_TYPE_LOCAL);
        }
        if (material.getVersion() == null || material.getVersion() < 1) {
            material.setVersion(nextVersion(projectId, material));
        }
        if (material.getIsSupplement() == null) {
            material.setIsSupplement(EscalationMaterial.SUPPLEMENT_NO);
        }
        if (StringUtils.isBlank(material.getUploadBy())) {
            material.setUploadBy(auditSupport.currentUsername());
        }
        if (material.getUploadTime() == null) {
            material.setUploadTime(new Date());
        }
        save(material);
        refreshMaterialCount(projectId);
        log.info(String.format("提级论证材料保存成功：projectId=%s, materialId=%s, fileName=%s, version=%s, size=%s",
                projectId, material.getId(), material.getFileName(), material.getVersion(), material.getFileSize()));
        return material;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMaterial(EscalationMaterial material) {
        if (material == null || StringUtils.isBlank(material.getId())) {
            throw new JeecgBootException("缺少材料ID");
        }
        EscalationMaterial old = baseMapper.selectMaterialById(material.getId());
        if (old == null) {
            throw new JeecgBootException("材料不存在或已被删除");
        }
        if (StringUtils.isNotBlank(material.getMaterialType())) {
            checkMaterialTypeLength(material.getMaterialType());
        }
        // 只更新允许改的列：文件本身（store_path / file_name / file_size）不允许在编辑时替换
        EscalationMaterial update = new EscalationMaterial().setId(old.getId());
        if (material.getMaterialType() != null) {
            update.setMaterialType(trimToNull(material.getMaterialType()));
        }
        if (material.getIsSupplement() != null) {
            update.setIsSupplement(material.getIsSupplement());
        }
        if (material.getVersion() != null && material.getVersion() > 0) {
            update.setVersion(material.getVersion());
        }
        updateById(update);
        log.info("提级论证材料更新成功：materialId={}", old.getId());
    }

    // ==================================================================
    // 删除
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterial(String id) {
        EscalationMaterial material = baseMapper.selectMaterialById(id);
        if (material == null) {
            throw new JeecgBootException("材料不存在或已被删除");
        }
        removeById(id);
        refreshMaterialCount(material.getProjectId());
        log.info("提级论证材料删除成功：materialId={}, projectId={}", id, material.getProjectId());
    }

    // ==================================================================
    // 三向合并（项目编辑时）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncMaterials(String projectId, List<EscalationMaterial> submitted) {
        if (StringUtils.isBlank(projectId)) {
            throw new JeecgBootException("缺少提级论证项目ID");
        }
        List<EscalationMaterial> submittedList = submitted == null ? new ArrayList<>() : submitted;
        List<EscalationMaterial> exists = baseMapper.selectByProjectId(projectId);
        Set<String> existsIds = new HashSet<>();
        for (EscalationMaterial material : exists) {
            existsIds.add(material.getId());
        }

        Set<String> keptIds = new HashSet<>();
        List<EscalationMaterial> toInsert = new ArrayList<>();
        for (EscalationMaterial material : submittedList) {
            if (material == null) {
                continue;
            }
            if (StringUtils.isNotBlank(material.getId()) && existsIds.contains(material.getId())) {
                keptIds.add(material.getId());
                updateMaterial(material);
            } else {
                toInsert.add(material);
            }
        }

        // 库里存在、但前端没提交的，视为删除（与档案模块 syncFiles 的口径一致）
        for (EscalationMaterial material : exists) {
            if (!keptIds.contains(material.getId())) {
                removeById(material.getId());
            }
        }
        for (EscalationMaterial material : toInsert) {
            addMaterial(projectId, material);
        }
        refreshMaterialCount(projectId);
    }

    // ==================================================================
    // 冗余计数
    // ==================================================================

    @Override
    public void refreshMaterialCount(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            return;
        }
        Map<String, Object> row = baseMapper.sumByProjectId(projectId);
        int materialCount = 0;
        if (row != null) {
            materialCount = (int) toLong(row.get("fileCount"));
        }
        // 直接用主表 Mapper 只更新那一列：不用把整条项目读出来再写回
        EscalationProject patch = new EscalationProject()
                .setId(projectId)
                .setMaterialCount(materialCount)
                .setUpdateBy(auditSupport.currentUsername())
                .setUpdateTime(new Date());
        projectMapper.updateById(patch);
    }

    // ==================================================================
    // 路径与 URL
    // ==================================================================

    @Override
    public String buildUrl(EscalationMaterial material) {
        if (material == null || StringUtils.isBlank(material.getStorePath())) {
            return null;
        }
        String path = material.getStorePath().trim();
        // store_path 由 jeecg 的 /sys/common/upload 返回，形如 /escalation/2026/09/xxx.pdf；
        // 统一成不含前导斜杠的相对路径，交给前端的 staticDomainURL 拼接。
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    @Override
    public String resolveAbsolutePath(EscalationMaterial material) {
        if (material == null || StringUtils.isBlank(material.getStorePath())) {
            return null;
        }
        String relative = material.getStorePath().replace('\\', '/').trim();
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        // 路径穿越防护：store_path 只允许「相对路径」，出现 .. 一律拒绝
        if (relative.contains("..")) {
            log.warn("检测到疑似路径穿越的 store_path，已拒绝：materialId={}, storePath={}",
                    material.getId(), material.getStorePath());
            return null;
        }
        File root = new File(uploadRoot);
        File target = new File(root, relative);
        try {
            String rootPath = root.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            if (!targetPath.startsWith(rootPath + File.separator) && !targetPath.equals(rootPath)) {
                log.warn("材料绝对路径越出上传根目录，已拒绝：materialId={}, target={}",
                        material.getId(), targetPath);
                return null;
            }
            return targetPath;
        } catch (Exception e) {
            log.warn("解析材料绝对路径失败：materialId={}, 原因={}", material.getId(), e.getMessage());
            return null;
        }
    }

    // ==================================================================
    // 校验
    // ==================================================================

    /**
     * 上传校验：路径必填 + 扩展名白名单 + 单文件 ≤ 50MB。
     *
     * <p>校验是<b>后端强制</b>的，不依赖前端限制：前端限制只是体验，
     * 后端限制才是约束（设计文档 6.1 第 10 项验收点）。
     */
    private void validate(EscalationMaterial material) {
        if (StringUtils.isBlank(material.getStorePath())) {
            throw new JeecgBootException("文件「" + safeName(material) + "」缺少存储路径，请重新上传");
        }
        String ext = StringUtils.isNotBlank(material.getFileExt())
                ? material.getFileExt().trim().toLowerCase()
                : resolveExt(material.getFileName());
        if (StringUtils.isBlank(ext) || !ALLOWED_EXT.contains(ext)) {
            throw new JeecgBootException("文件「" + safeName(material) + "」格式不支持，仅允许 "
                    + String.join("/", ALLOWED_EXT) + " 格式");
        }
        if (material.getFileSize() != null && material.getFileSize() > MAX_FILE_SIZE) {
            throw new JeecgBootException("文件「" + safeName(material) + "」大小为 "
                    + formatSize(material.getFileSize()) + "，超过单个文件 50MB 的上限");
        }
        if (StringUtils.isNotBlank(material.getMaterialType())) {
            checkMaterialTypeLength(material.getMaterialType());
        }
    }

    /** 材料类型列宽 VARCHAR(50)：这里按内容长度兜一层，避免落库被静默截断 */
    private void checkMaterialTypeLength(String materialType) {
        if (materialType.trim().length() > 50) {
            throw new JeecgBootException("材料类型过长（不超过 50 字）");
        }
    }

    // ==================================================================
    // 展示字段补齐
    // ==================================================================

    /** 给单个材料补 url 与可读大小 */
    public EscalationMaterial enrich(EscalationMaterial material) {
        if (material == null) {
            return null;
        }
        material.setUrl(buildUrl(material));
        material.setReadableSize(formatSize(material.getFileSize()));
        return material;
    }

    /** 批量补齐 */
    public List<EscalationMaterial> enrichAll(List<EscalationMaterial> materials) {
        if (materials == null || materials.isEmpty()) {
            return materials;
        }
        for (EscalationMaterial material : materials) {
            enrich(material);
        }
        return materials;
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    /**
     * 下一个版本号。
     *
     * <p>口径：<b>补充材料</b>取该项目当前最大版本 +1，其余材料从 1 开始。
     * 这样「补充材料 version=2」与原型/设计文档的演示数据（version=1/2）一致。
     */
    private int nextVersion(String projectId, EscalationMaterial material) {
        boolean supplement = material.getIsSupplement() != null
                && material.getIsSupplement() == EscalationMaterial.SUPPLEMENT_YES;
        if (!supplement) {
            return 1;
        }
        int max = 0;
        for (EscalationMaterial exist : baseMapper.selectByProjectId(projectId)) {
            if (exist.getVersion() != null && exist.getVersion() > max) {
                max = exist.getVersion();
            }
        }
        return max + 1;
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

    private static String safeName(EscalationMaterial material) {
        return StringUtils.isBlank(material.getFileName()) ? "（未命名）" : material.getFileName();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    /** 人性化文件大小（导出与列表共用） */
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

    /** 扩展名白名单（供前端/接口文档展示，避免前后端各写一份） */
    public static List<String> allowedExtensions() {
        List<String> list = new ArrayList<>(ALLOWED_EXT);
        Collections.sort(list);
        return list;
    }

    /** 上传上限（字节），供接口文档展示 */
    public static long maxFileSize() {
        return MAX_FILE_SIZE;
    }

    /** 供导出复用：项目ID → 材料列表（空入参直接返回空表，避免生成 IN () 语法） */
    public Map<String, List<EscalationMaterial>> queryGrouped(List<String> projectIds) {
        Map<String, List<EscalationMaterial>> grouped = new LinkedHashMap<>();
        if (projectIds == null || projectIds.isEmpty()) {
            return grouped;
        }
        for (EscalationMaterial material : baseMapper.selectByProjectIds(projectIds)) {
            grouped.computeIfAbsent(material.getProjectId(), key -> new ArrayList<>()).add(material);
        }
        return grouped;
    }
}
