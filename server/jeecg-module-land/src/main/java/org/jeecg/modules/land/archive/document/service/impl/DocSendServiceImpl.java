package org.jeecg.modules.land.archive.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.document.dto.DocArchiveDTO;
import org.jeecg.modules.land.archive.document.dto.DocQueryDTO;
import org.jeecg.modules.land.archive.document.entity.DocAttachment;
import org.jeecg.modules.land.archive.document.entity.DocSend;
import org.jeecg.modules.land.archive.document.mapper.DocSendMapper;
import org.jeecg.modules.land.archive.document.service.IDocAttachmentService;
import org.jeecg.modules.land.archive.document.service.IDocSendService;
import org.jeecg.modules.land.archive.document.support.DocSupport;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.entity.ArchiveFile;
import org.jeecg.modules.land.archive.service.IArchiveService;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @Description: 发文 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>发文是纯台账（无流转），因此实现比收文简单得多：
 * 登记 / 编辑 / 附件同步 / 删除 / 可选归档。
 * 归档逻辑与收文完全一致（同样必须选择档案类别），复用 {@link IArchiveService}。
 */
@Slf4j
@Service
public class DocSendServiceImpl extends ServiceImpl<DocSendMapper, DocSend> implements IDocSendService {

    private static final String DOC_NO_PREFIX = "FW-";
    private static final int NO_RETRY = 3;
    private static final int RELATED_LIMIT = 100;

    @Autowired
    private DocSupport docSupport;

    @Autowired
    private IDocAttachmentService attachmentService;

    @Autowired
    private IArchiveService archiveService;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public IPage<DocSend> queryPage(DocQueryDTO query) {
        DocQueryDTO condition = query == null ? new DocQueryDTO() : query;
        Page<DocSend> page = new Page<>(condition.resolvePageNo(), condition.resolvePageSize());
        return baseMapper.selectDocPage(page, condition);
    }

    @Override
    public DocSend queryDetail(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        DocSend doc = getById(id);
        if (doc == null) {
            return null;
        }
        doc.setAttachments(attachmentService.queryByDoc(DocAttachment.DOC_TYPE_SEND, id));
        return doc;
    }

    @Override
    public List<DocSend> queryRelated(String facilityId, String landId, String crzdbh, Integer limit) {
        QueryWrapper<DocSend> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(facilityId)) {
            wrapper.eq("facility_id", facilityId.trim());
        } else if (StringUtils.isNotBlank(landId)) {
            wrapper.eq("land_id", landId.trim());
        } else if (StringUtils.isNotBlank(crzdbh)) {
            wrapper.eq("crzdbh", crzdbh.trim());
        } else {
            return Collections.emptyList();
        }
        wrapper.orderByDesc("issue_date").orderByDesc("create_time");
        int size = limit == null || limit <= 0 ? RELATED_LIMIT : Math.min(limit, 500);
        return page(new Page<>(1, size, false), wrapper).getRecords();
    }

    @Override
    public long countAll() {
        return count();
    }

    // ==================================================================
    // 维护
    // ==================================================================

    @Override
    public String generateDocNo(Integer year) {
        int targetYear = year == null || year < 1900 || year > 2999
                ? Calendar.getInstance().get(Calendar.YEAR)
                : year;
        return nextDocNo(targetYear);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDoc(DocSend doc) {
        if (doc == null) {
            throw new JeecgBootException("缺少发文数据");
        }
        if (StringUtils.isBlank(doc.getDocTitle())) {
            throw new JeecgBootException("文件标题不能为空");
        }
        fillAssociation(doc);
        if (StringUtils.isBlank(doc.getSecretLevel())) {
            doc.setSecretLevel("一般");
        }

        JeecgBootException lastError = null;
        for (int attempt = 0; attempt < NO_RETRY; attempt++) {
            String docNo = StringUtils.isNotBlank(doc.getDocNo())
                    ? doc.getDocNo().trim()
                    : nextDocNo(resolveYear(doc.getIssueDate()));
            checkDocNoUnique(docNo, null);
            doc.setDocNo(docNo).setId(IdWorker.getIdStr()).setDelFlag(0);
            if (save(doc)) {
                lastError = null;
                break;
            }
            lastError = new JeecgBootException("发文保存失败，请重试");
        }
        if (lastError != null) {
            throw lastError;
        }
        attachmentService.syncAttachments(DocAttachment.DOC_TYPE_SEND, doc.getId(), doc.getAttachments());
        log.info("发文登记成功：id={}, docNo={}", doc.getId(), doc.getDocNo());
        return doc.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDoc(DocSend doc) {
        if (doc == null || StringUtils.isBlank(doc.getId())) {
            throw new JeecgBootException("缺少发文ID");
        }
        DocSend old = getById(doc.getId());
        if (old == null) {
            throw new JeecgBootException("发文不存在或已被删除");
        }
        String docNo = StringUtils.isNotBlank(doc.getDocNo()) ? doc.getDocNo() : old.getDocNo();
        checkDocNoUnique(docNo, old.getId());

        DocSend update = new DocSend().setId(old.getId());
        update.setDocNo(docNo.trim());
        update.setDocTitle(trimToNull(doc.getDocTitle()));
        update.setDocType(trimToNull(doc.getDocType()));
        update.setToDept(trimToNull(doc.getToDept()));
        update.setCcDept(trimToNull(doc.getCcDept()));
        update.setIssueDate(doc.getIssueDate());
        update.setSigner(trimToNull(doc.getSigner()));
        update.setDrafter(trimToNull(doc.getDrafter()));
        update.setSecretLevel(trimToNull(doc.getSecretLevel()));
        update.setUrgency(trimToNull(doc.getUrgency()));
        update.setCopies(doc.getCopies());
        update.setRemark(trimToNull(doc.getRemark()));

        fillAssociation(doc);
        update.setLandId(trimToNull(doc.getLandId()));
        update.setCrzdbh(trimToNull(doc.getCrzdbh()));
        update.setFacilityId(trimToNull(doc.getFacilityId()));
        update.setPtxmmc(trimToNull(doc.getPtxmmc()));

        updateById(update);
        attachmentService.syncAttachments(DocAttachment.DOC_TYPE_SEND, old.getId(), doc.getAttachments());
        log.info("发文编辑成功：id={}, docNo={}", old.getId(), docNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDoc(String id) {
        DocSend doc = getById(id);
        if (doc == null) {
            throw new JeecgBootException("发文不存在或已被删除");
        }
        attachmentService.deleteByDoc(DocAttachment.DOC_TYPE_SEND, id);
        removeById(id);
        log.info("发文删除成功：id={}, docNo={}", id, doc.getDocNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocs(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new JeecgBootException("请选择要删除的发文");
        }
        for (String id : ids) {
            if (StringUtils.isNotBlank(id)) {
                deleteDoc(id.trim());
            }
        }
    }

    // ==================================================================
    // 归档
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String archive(DocArchiveDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getDocId())) {
            throw new JeecgBootException("缺少发文ID");
        }
        if (StringUtils.isBlank(dto.getCategoryId())) {
            throw new JeecgBootException("归档必须选择档案类别");
        }
        DocSend doc = getById(dto.getDocId());
        if (doc == null) {
            throw new JeecgBootException("发文不存在或已被删除");
        }
        if (StringUtils.isNotBlank(doc.getArchiveId())) {
            throw new JeecgBootException("该发文已归档，无需重复归档");
        }

        List<DocAttachment> attachments = attachmentService.queryByDoc(DocAttachment.DOC_TYPE_SEND, doc.getId());
        List<ArchiveFile> files = new ArrayList<>();
        int seq = 1;
        for (DocAttachment attachment : attachments) {
            files.add(new ArchiveFile()
                    .setCategoryId(dto.getCategoryId())
                    .setFileName(attachment.getFileName())
                    .setFileTitle(attachment.getFileName())
                    .setFileExt(attachment.getFileExt())
                    .setFileSize(attachment.getFileSize())
                    .setFileMd5(attachment.getFileMd5())
                    .setStoreType(StringUtils.isNotBlank(attachment.getStoreType())
                            ? attachment.getStoreType() : "local")
                    .setStorePath(attachment.getStorePath())
                    .setPreviewPath(attachment.getPreviewPath())
                    .setStatus(ArchiveFile.STATUS_ARCHIVED)
                    .setSeqNo(seq)
                    .setSortNo(seq));
            seq++;
        }

        Archive archive = new Archive()
                .setArchiveName(StringUtils.isNotBlank(dto.getArchiveName())
                        ? dto.getArchiveName().trim()
                        : doc.getDocTitle())
                .setLandId(doc.getLandId())
                .setCrzdbh(doc.getCrzdbh())
                .setFacilityId(doc.getFacilityId())
                .setPtxmmc(doc.getPtxmmc())
                .setArchiveType(Archive.TYPE_ELECTRONIC)
                .setSecretLevel(StringUtils.isNotBlank(dto.getSecretLevel())
                        ? dto.getSecretLevel() : doc.getSecretLevel())
                .setArchiveDate(parseDate(dto.getArchiveDate(), new Date()))
                .setResponsibleUser(doc.getDrafter())
                .setSourceType(Archive.SOURCE_DOC_SEND)
                .setSourceId(doc.getId())
                .setRemark("由发文【" + doc.getDocNo() + "】归档")
                .setStatus(files.isEmpty() ? Archive.STATUS_ARCHIVING : Archive.STATUS_ARCHIVED);
        fillArchiveAssociation(archive, doc);

        String archiveId = archiveService.createArchive(archive, files);
        updateById(new DocSend().setId(doc.getId()).setArchiveId(archiveId));
        log.info("发文归档成功：docId={}, archiveId={}, 文件数={}", doc.getId(), archiveId, files.size());
        return archiveId;
    }

    private void fillArchiveAssociation(Archive archive, DocSend doc) {
        if (StringUtils.isNotBlank(doc.getFacilityId())) {
            Facility facility = safeFacility(doc.getFacilityId());
            if (facility != null) {
                archive.setPtxmmc(facility.getPtxmmc())
                        .setCrzdbh(facility.getCrzdbh())
                        .setDkmc(facility.getDkmc())
                        .setPtsslb(facility.getPtsslb())
                        .setXzqh(facility.getXzqh());
                Land land = docSupport.queryLandByCrzdbh(facility.getCrzdbh());
                if (land != null) {
                    archive.setLandId(land.getId());
                }
                return;
            }
        }
        if (StringUtils.isNotBlank(doc.getCrzdbh())) {
            Land land = docSupport.queryLandByCrzdbh(doc.getCrzdbh());
            if (land != null) {
                archive.setLandId(land.getId()).setDkmc(land.getDkmc()).setXzqh(land.getXzqh());
            }
        }
    }

    private Facility safeFacility(String facilityId) {
        try {
            return docSupport.requireFacility(facilityId);
        } catch (Exception e) {
            log.warn("发文归档时配套项目已失效：facilityId={}, 原因={}", facilityId, e.getMessage());
            return null;
        }
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    private void checkDocNoUnique(String docNo, String excludeId) {
        if (StringUtils.isBlank(docNo)) {
            throw new JeecgBootException("发文登记号不能为空");
        }
        if (baseMapper.countByDocNo(docNo.trim(), excludeId) > 0) {
            throw new JeecgBootException("发文登记号「" + docNo.trim() + "」已存在，请更换");
        }
    }

    private String nextDocNo(int year) {
        String prefix = DOC_NO_PREFIX + year + "-";
        Integer max = baseMapper.selectMaxSeqOfYear(prefix);
        int next = (max == null ? 0 : max) + 1;
        return prefix + String.format("%04d", next);
    }

    private void fillAssociation(DocSend doc) {
        if (StringUtils.isBlank(doc.getFacilityId())) {
            return;
        }
        Facility facility = docSupport.requireFacility(doc.getFacilityId());
        doc.setPtxmmc(facility.getPtxmmc()).setCrzdbh(facility.getCrzdbh());
        Land land = docSupport.queryLandByCrzdbh(facility.getCrzdbh());
        doc.setLandId(land == null ? null : land.getId());
    }

    private int resolveYear(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date == null ? new Date() : date);
        return calendar.get(Calendar.YEAR);
    }

    private static Date parseDate(String text, Date fallback) {
        if (StringUtils.isBlank(text)) {
            return fallback;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(text.trim());
        } catch (ParseException e) {
            return fallback;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
