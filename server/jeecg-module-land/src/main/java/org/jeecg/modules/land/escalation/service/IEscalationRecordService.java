package org.jeecg.modules.land.escalation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.escalation.dto.EscalationRecordDTO;
import org.jeecg.modules.land.escalation.entity.EscalationRecord;

import java.util.List;

/**
 * @Description: 提级论证审核意见 / 办理记录 Service（★ append-only）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p><b>本接口刻意只有「查」与「登记」两个动作</b>：
 * 没有 update / delete 方法，对应 Controller 也没有 edit / delete 接口。
 * 意见一旦登记即不可篡改，这是方案痛点「可追溯性不足」的技术回答（设计文档 4.2 注）。
 */
public interface IEscalationRecordService extends IService<EscalationRecord> {

    /** 某项目的意见记录列表（时间倒序，append-only 只读展示） */
    List<EscalationRecord> queryByProjectId(String projectId);

    /**
     * 登记一条意见（唯一写入口）。
     *
     * <p>同一个事务内完成三件事：
     * ① INSERT 记录；② 回写主表 {@code latest_opinion*} 与 {@code record_count}；
     * ③ 可选同步主表 {@code status}（仅当入参带了 status）。
     *
     * @return 落库后的记录（含生成的 id 与记录时间）
     */
    EscalationRecord addRecord(EscalationRecordDTO dto);
}
