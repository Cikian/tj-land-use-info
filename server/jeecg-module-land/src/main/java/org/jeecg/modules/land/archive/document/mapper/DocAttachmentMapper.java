package org.jeecg.modules.land.archive.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.document.entity.DocAttachment;

import java.util.Collection;
import java.util.List;

/**
 * @Description: 收发文附件 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface DocAttachmentMapper extends BaseMapper<DocAttachment> {

    /** 某单据的附件列表 */
    List<DocAttachment> selectByDoc(@Param("docType") String docType,
                                    @Param("docId") String docId);

    /** 批量取多个单据的附件（列表页/归档打包用，避免 N+1） */
    List<DocAttachment> selectByDocs(@Param("docType") String docType,
                                     @Param("docIds") Collection<String> docIds);
}
