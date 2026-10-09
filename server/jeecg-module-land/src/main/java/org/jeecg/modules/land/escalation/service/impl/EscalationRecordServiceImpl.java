package org.jeecg.modules.land.escalation.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.escalation.dto.EscalationRecordDTO;
import org.jeecg.modules.land.escalation.entity.EscalationProject;
import org.jeecg.modules.land.escalation.entity.EscalationRecord;
import org.jeecg.modules.land.escalation.enums.EscalationStatus;
import org.jeecg.modules.land.escalation.enums.RecordTypeEnum;
import org.jeecg.modules.land.escalation.mapper.EscalationProjectMapper;
import org.jeecg.modules.land.escalation.mapper.EscalationRecordMapper;
import org.jeecg.modules.land.escalation.service.IEscalationRecordService;
import org.jeecg.modules.land.escalation.support.EscalationAuditSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @Description: 提级论证审核意见 / 办理记录 Service 实现（★ append-only）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>本类<b>只 INSERT，不 UPDATE / DELETE 记录表</b>：全类没有一处对
 * {@code t_escalation_record} 的更新或删除（{@code updateById} / {@code removeById}
 * 都没有出现），这是「意见不可篡改」最直接的代码证据。
 *
 * <p>登记成功后，同一事务内回写主表的冗余列（最新意见摘要 + 记录数），
 * 可选同步办理状态；<b>绝不</b>自动改状态 —— 系统只存不推（设计文档 2.3）。
 */
@Slf4j
@Service
public class EscalationRecordServiceImpl extends ServiceImpl<EscalationRecordMapper, EscalationRecord>
        implements IEscalationRecordService {

    /** {@code latest_opinion} 列宽 VARCHAR(1000)：摘要按 1000 字符截断后再落库 */
    private static final int LATEST_OPINION_MAX = 1000;

    /** {@code action} 列宽 VARCHAR(30)：快捷短语都很短，超长直接报错而不是静默截断 */
    private static final int ACTION_MAX = 30;

    /** {@code opinion} 列宽 VARCHAR(2000)：前端限 1000 字，后端按列容量兜一层 */
    private static final int OPINION_MAX = 2000;

    @Autowired
    private EscalationAuditSupport auditSupport;

    /** 直接注入主表 Mapper（避免 Service 循环依赖） */
    @Autowired
    private EscalationProjectMapper projectMapper;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public List<EscalationRecord> queryByProjectId(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            return Collections.emptyList();
        }
        return baseMapper.selectByProjectId(projectId);
    }

    // ==================================================================
    // 登记（唯一写入口）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EscalationRecord addRecord(EscalationRecordDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getProjectId())) {
            throw new JeecgBootException("缺少提级论证项目ID");
        }
        String projectId = dto.getProjectId().trim();
        EscalationProject project = projectMapper.selectDetailById(projectId);
        if (project == null) {
            throw new JeecgBootException("提级论证项目不存在或已被删除");
        }

        // ---- 1) 取值校验（只校验「是否在允许集合内」，不做任何流转顺序判定） ----
        String recordType = StringUtils.isNotBlank(dto.getRecordType())
                ? dto.getRecordType().trim()
                : RecordTypeEnum.defaultValue();
        if (!RecordTypeEnum.isValid(recordType)) {
            throw new JeecgBootException("记录类型「" + recordType + "」不合法，允许值："
                    + String.join("/", RecordTypeEnum.allValues()));
        }
        String opinion = trimToNull(dto.getOpinion());
        if (opinion == null) {
            throw new JeecgBootException("意见正文不能为空");
        }
        if (opinion.length() > OPINION_MAX) {
            throw new JeecgBootException("意见正文过长（不超过 " + OPINION_MAX + " 字）");
        }
        String action = trimToNull(dto.getAction());
        if (action != null && action.length() > ACTION_MAX) {
            throw new JeecgBootException("结论短语过长（不超过 " + ACTION_MAX + " 字）");
        }
        String syncStatus = trimToNull(dto.getStatus());
        if (syncStatus != null && !EscalationStatus.isValid(syncStatus)) {
            throw new JeecgBootException("办理状态「" + syncStatus + "」不合法，允许值："
                    + String.join("/", EscalationStatus.allValues()));
        }

        // ---- 2) INSERT 意见记录（append-only） ----
        Date now = new Date();
        String recorderName = auditSupport.currentRealname();
        EscalationRecord record = new EscalationRecord()
                .setId(IdWorker.getIdStr())
                .setProjectId(projectId)
                .setRecordType(recordType)
                .setAction(action)
                .setOpinion(opinion)
                .setAttachmentIds(trimToNull(dto.getAttachmentIds()))
                .setRecorderId(defaultIfNull(auditSupport.currentUsername(), dto.getRecorderId()))
                .setRecorderName(defaultIfNull(recorderName, dto.getRecorderName()))
                .setRecorderDept(defaultIfNull(auditSupport.currentDept(), dto.getRecorderDept()))
                .setRecordTime(now);
        save(record);

        // ---- 3) 同一事务内回写主表摘要 + 记录数（重算，不累加） ----
        int recordCount = baseMapper.countByProjectId(projectId);
        String latestOpinion = truncate(opinion, LATEST_OPINION_MAX);
        baseMapper.updateLatestOpinion(projectId, latestOpinion, now, record.getRecorderName(),
                recordCount, syncStatus, auditSupport.currentUsername(), now);

        log.info(String.format("登记审核意见成功：projectId=%s, recordId=%s, recordType=%s, action=%s, 同步状态=%s, 记录数=%s, 记录人=%s",
                projectId, record.getId(), recordType, action, syncStatus, recordCount, record.getRecorderName()));
        return record;
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String defaultIfNull(String primary, String fallback) {
        return StringUtils.isNotBlank(primary) ? primary : trimToNull(fallback);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
