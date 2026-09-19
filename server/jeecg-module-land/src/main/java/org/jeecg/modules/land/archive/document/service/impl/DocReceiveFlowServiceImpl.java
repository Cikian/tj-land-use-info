package org.jeecg.modules.land.archive.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.modules.land.archive.document.entity.DocReceiveFlow;
import org.jeecg.modules.land.archive.document.mapper.DocReceiveFlowMapper;
import org.jeecg.modules.land.archive.document.service.IDocReceiveFlowService;
import org.jeecg.modules.land.archive.document.support.DocSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * @Description: 收文流转记录 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Slf4j
@Service
public class DocReceiveFlowServiceImpl extends ServiceImpl<DocReceiveFlowMapper, DocReceiveFlow>
        implements IDocReceiveFlowService {

    @Autowired
    private DocSupport docSupport;

    @Override
    public List<DocReceiveFlow> queryByDocId(String docId) {
        if (StringUtils.isBlank(docId)) {
            return java.util.Collections.emptyList();
        }
        return baseMapper.selectByDocId(docId);
    }

    @Override
    public DocReceiveFlow currentOpenFlow(String docId) {
        if (StringUtils.isBlank(docId)) {
            return null;
        }
        return baseMapper.selectCurrentOpenFlow(docId);
    }

    @Override
    public DocReceiveFlow openFlow(String docId, String action, String nodeName,
                                   String handlerUsername, String handlerName, String opinion) {
        DocReceiveFlow flow = new DocReceiveFlow()
                .setId(IdWorker.getIdStr())
                .setDocId(docId)
                .setAction(action)
                .setNodeName(nodeName)
                .setHandler(handlerUsername)
                .setHandlerName(StringUtils.isNotBlank(handlerName)
                        ? handlerName : docSupport.realnameOf(handlerUsername))
                .setOpinion(trimToNull(opinion))
                .setReceiveTime(new Date())
                .setSeqNo(nextSeqNo(docId))
                .setDelFlag(0)
                .setCreateBy(docSupport.currentUsername())
                .setCreateTime(new Date());
        save(flow);
        log.info("收文流转开单：docId={}, action={}, handler={}, seqNo={}",
                docId, action, handlerUsername, flow.getSeqNo());
        return flow;
    }

    @Override
    public void closeFlow(DocReceiveFlow flow, String opinion) {
        if (flow == null || StringUtils.isBlank(flow.getId())) {
            return;
        }
        DocReceiveFlow update = new DocReceiveFlow()
                .setId(flow.getId())
                .setHandleTime(new Date());
        if (StringUtils.isNotBlank(opinion)) {
            update.setOpinion(trimToNull(opinion));
        }
        updateById(update);
    }

    @Override
    public int nextSeqNo(String docId) {
        List<DocReceiveFlow> flows = baseMapper.selectByDocId(docId);
        int max = 0;
        for (DocReceiveFlow flow : flows) {
            if (flow.getSeqNo() != null && flow.getSeqNo() > max) {
                max = flow.getSeqNo();
            }
        }
        return max + 1;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
