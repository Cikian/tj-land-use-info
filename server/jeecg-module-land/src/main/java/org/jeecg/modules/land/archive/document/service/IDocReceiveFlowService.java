package org.jeecg.modules.land.archive.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.document.entity.DocReceiveFlow;

import java.util.List;

/**
 * @Description: 收文流转记录 Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>把「流转」抽象成两条原语：<b>开工单</b>（openFlow）与 <b>关工单</b>（closeFlow）。
 * 收文的转办 / 退回 / 办结都只是这两条原语的组合，
 * 因此不会像旧系统那样出现「新增不写流转」「退回方向搞反」的补丁式代码。
 */
public interface IDocReceiveFlowService extends IService<DocReceiveFlow> {

    /** 某收文的全部流转记录（按顺序正序，供时间轴展示） */
    List<DocReceiveFlow> queryByDocId(String docId);

    /** 当前待办记录（handleTime 为空且 seqNo 最大），没有则 null */
    DocReceiveFlow currentOpenFlow(String docId);

    /**
     * 开一条新的待办流转记录。
     *
     * @param docId           收文ID
     * @param action          动作：登记/转办/退回
     * @param nodeName        节点名
     * @param handlerUsername 待办人账号
     * @param handlerName     待办人姓名（冗余）
     * @param opinion         意见 / 批注（在下一条流转完成时会被补写）
     * @return 新建的流转记录
     */
    DocReceiveFlow openFlow(String docId, String action, String nodeName,
                            String handlerUsername, String handlerName, String opinion);

    /**
     * 关闭一条待办流转记录（写入处理时间与最终意见）。
     *
     * @param flow    待关闭的流转记录
     * @param opinion 处理意见；为空时保留原值
     */
    void closeFlow(DocReceiveFlow flow, String opinion);

    /** 下一条流转顺序号 */
    int nextSeqNo(String docId);
}
