package org.jeecg.modules.land.archive.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.modules.land.archive.document.entity.DocAttachment;
import org.jeecg.modules.land.archive.document.mapper.DocAttachmentMapper;
import org.jeecg.modules.land.archive.document.service.IDocAttachmentService;
import org.jeecg.modules.land.archive.document.support.DocSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 收发文附件 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Slf4j
@Service
public class DocAttachmentServiceImpl extends ServiceImpl<DocAttachmentMapper, DocAttachment>
        implements IDocAttachmentService {

    @Autowired
    private DocSupport docSupport;

    @Override
    public List<DocAttachment> queryByDoc(String docType, String docId) {
        if (StringUtils.isBlank(docType) || StringUtils.isBlank(docId)) {
            return Collections.emptyList();
        }
        return baseMapper.selectByDoc(docType, docId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncAttachments(String docType, String docId, List<DocAttachment> submitted) {
        if (StringUtils.isBlank(docType) || StringUtils.isBlank(docId)) {
            return;
        }
        List<DocAttachment> submittedList = submitted == null ? new ArrayList<>() : submitted;
        List<DocAttachment> exists = baseMapper.selectByDoc(docType, docId);
        Set<String> existsIds = new HashSet<>();
        for (DocAttachment item : exists) {
            existsIds.add(item.getId());
        }

        Set<String> keptIds = new HashSet<>();
        List<DocAttachment> toInsert = new ArrayList<>();
        int sortNo = 1;
        for (DocAttachment item : submittedList) {
            if (item == null || StringUtils.isBlank(item.getStorePath())) {
                continue;
            }
            String id = StringUtils.trimToNull(item.getId());
            if (id != null && existsIds.contains(id)) {
                keptIds.add(id);
                // 附件只允许改排序，不重新上传
                if (item.getSortNo() != null) {
                    updateById(new DocAttachment().setId(id).setSortNo(item.getSortNo()));
                }
            } else {
                item.setId(IdWorker.getIdStr())
                        .setDocType(docType)
                        .setDocId(docId)
                        .setSortNo(sortNo)
                        .setDelFlag(0);
                if (StringUtils.isBlank(item.getStoreType())) {
                    item.setStoreType("local");
                }
                if (StringUtils.isBlank(item.getFileExt())) {
                    item.setFileExt(resolveExt(item.getFileName()));
                }
                if (item.getUploadTime() == null) {
                    item.setUploadTime(new Date());
                }
                if (StringUtils.isBlank(item.getUploadBy())) {
                    item.setUploadBy(docSupport.currentUsername());
                    item.setUploadName(docSupport.currentRealname());
                }
                toInsert.add(item);
            }
            sortNo++;
        }

        if (!toInsert.isEmpty()) {
            saveBatch(toInsert);
        }
        // 前端没提交的视为已删除
        List<String> removed = new ArrayList<>();
        for (DocAttachment item : exists) {
            if (!keptIds.contains(item.getId())) {
                removed.add(item.getId());
            }
        }
        if (!removed.isEmpty()) {
            removeByIds(removed);
        }
        log.info("收发文附件同步完成：docType={}, docId={}, 新增 {} 个, 删除 {} 个",
                docType, docId, toInsert.size(), removed.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDoc(String docType, String docId) {
        if (StringUtils.isBlank(docType) || StringUtils.isBlank(docId)) {
            return;
        }
        remove(new QueryWrapper<DocAttachment>()
                .eq("doc_type", docType)
                .eq("doc_id", docId));
    }

    @Override
    public Map<String, Integer> countByDocs(String docType, List<String> docIds) {
        Map<String, Integer> result = new HashMap<>();
        if (StringUtils.isBlank(docType) || docIds == null || docIds.isEmpty()) {
            return result;
        }
        List<DocAttachment> rows = baseMapper.selectByDocs(docType, docIds);
        for (DocAttachment item : rows) {
            result.merge(item.getDocId(), 1, Integer::sum);
        }
        return result;
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
}
