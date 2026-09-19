package org.jeecg.modules.land.archive.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.document.entity.DocAttachment;

import java.util.List;
import java.util.Map;

/**
 * @Description: 收发文附件 Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface IDocAttachmentService extends IService<DocAttachment> {

    /** 某单据的附件列表 */
    List<DocAttachment> queryByDoc(String docType, String docId);

    /**
     * 同步附件（以前端提交的列表为准，做新增/删除合并）。
     *
     * @param docType   receive / send
     * @param docId     单据ID
     * @param submitted 前端提交的附件（带 id 的保留，不带的视为新上传）
     */
    void syncAttachments(String docType, String docId, List<DocAttachment> submitted);

    /** 删除某单据的全部附件（删单据时级联） */
    void deleteByDoc(String docType, String docId);

    /** 批量统计各单据的附件数：docId → count */
    Map<String, Integer> countByDocs(String docType, List<String> docIds);
}
