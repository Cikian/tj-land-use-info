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

    /**
     * {@code latest_opinion} 列宽 VARCHAR(1000)：摘要按 1000 字符截断后再落库。
     *
     * <p><b>已与线上库核定</b>（2026-11-16 DBX 只读执行
     * {@code SHOW COLUMNS FROM t_escalation_project}，库 tj-jyxyd@49.232.252.56）：
     * {@code latest_opinion varchar(1000)}。
     */
    private static final int LATEST_OPINION_MAX = 1000;

    /**
     * {@code action} 列宽 VARCHAR(30)：快捷短语都很短，超长直接报错而不是静默截断。
     *
     * <p><b>已与线上库核定</b>：{@code t_escalation_record.action varchar(30)}。
     */
    private static final int ACTION_MAX = 30;

    /**
     * {@code opinion} 列宽 VARCHAR(2000)：前端限 1000 字，后端按列容量兜一层。
     *
     * <p><b>已与线上库核定</b>：{@code t_escalation_record.opinion varchar(2000)}。
     * 前端 1000 字的上限比列宽更紧，所以正常输入不会撞到这里；
     * 一旦撞到，说明调用方绕过了前端（脚本/旧页面），报错比静默截断更安全 ——
     * 审核意见被悄悄砍掉一半，比保存失败严重得多。
     */
    private static final int OPINION_MAX = 2000;

    /**
     * {@code attachment_ids} 当前列宽 VARCHAR(1000)：提前给出可读错误，避免整事务被数据库异常回滚。
     *
     * <p><b>已与线上库核定</b>：{@code t_escalation_record.attachment_ids varchar(1000)}。
     * 该列存的是逗号拼接的存储路径（不是附件 id 列表），路径一长就很容易超；
     * 超长时给出「请减少本次附件数量分批登记」的提示，而不是让 MySQL 抛
     * {@code Data too long for column} 把用户挡在一句看不懂的英文报错前。
     */
    private static final int ATTACHMENT_IDS_MAX = 1000;

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
            // ★ P0-05：前置校验命中也要留日志。
            //   只在 Controller 里记「被业务校验拒绝」的话，
            //   一旦有别处（定时任务 / 测试 / 新的接口）直接调 Service，
            //   失败就完全没有痕迹 —— 校验命中点必须自己会说话。
            log.warn("登记审核意见被前置校验拒绝：缺少提级论证项目ID（projectId={}），"
                    + "意见长度={}，附件路径长度={}",
                    dto == null ? null : dto.getProjectId(),
                    dto == null || dto.getOpinion() == null ? 0 : dto.getOpinion().length(),
                    dto == null || dto.getAttachmentIds() == null ? 0 : dto.getAttachmentIds().length());
            throw new JeecgBootException("缺少提级论证项目ID");
        }
        String projectId = dto.getProjectId().trim();
        EscalationProject project = projectMapper.selectDetailById(projectId);
        if (project == null) {
            log.warn("登记审核意见被前置校验拒绝：项目不存在。projectId={}，意见长度={}",
                    projectId, dto.getOpinion() == null ? 0 : dto.getOpinion().length());
            throw new JeecgBootException("提级论证项目不存在或已被删除");
        }

        // ---- 1) 取值校验（只校验「是否在允许集合内」，不做任何流转顺序判定） ----
        String recordType = StringUtils.isNotBlank(dto.getRecordType())
                ? dto.getRecordType().trim()
                : RecordTypeEnum.defaultValue();
        if (!RecordTypeEnum.isValid(recordType)) {
            log.warn("登记审核意见被前置校验拒绝：记录类型不合法。projectId={}，recordType={}",
                    projectId, recordType);
            throw new JeecgBootException("记录类型「" + recordType + "」不合法，允许值："
                    + String.join("/", RecordTypeEnum.allValues()));
        }
        String opinion = trimToNull(dto.getOpinion());
        if (opinion == null) {
            // 区分「没传」与「只传了空白」：前者是前端漏字段，后者是用户只敲了空格。
            // 两者的修法完全不同，日志里必须能看出来。
            log.warn("登记审核意见被前置校验拒绝：意见正文为空。projectId={}，原始长度={}，去空白后为空={}",
                    projectId,
                    dto.getOpinion() == null ? 0 : dto.getOpinion().length(),
                    dto.getOpinion() != null);
            throw new JeecgBootException("意见正文不能为空");
        }
        if (opinion.length() > OPINION_MAX) {
            log.warn("登记审核意见被前置校验拒绝：意见正文超列宽。projectId={}，实际长度={}，列上限={}，"
                            + "首20字={}",
                    projectId, opinion.length(), OPINION_MAX,
                    opinion.substring(0, Math.min(20, opinion.length())));
            throw new JeecgBootException("意见正文过长（不超过 " + OPINION_MAX + " 字）");
        }
        String action = trimToNull(dto.getAction());
        if (action != null && action.length() > ACTION_MAX) {
            log.warn("登记审核意见被前置校验拒绝：结论短语超列宽。projectId={}，action={}，长度={}，列上限={}",
                    projectId, action, action.length(), ACTION_MAX);
            throw new JeecgBootException("结论短语过长（不超过 " + ACTION_MAX + " 字）");
        }
        String syncStatus = trimToNull(dto.getStatus());
        if (syncStatus != null && !EscalationStatus.isValid(syncStatus)) {
            log.warn("登记审核意见被前置校验拒绝：办理状态不合法。projectId={}，status={}",
                    projectId, syncStatus);
            throw new JeecgBootException("办理状态「" + syncStatus + "」不合法，允许值："
                    + String.join("/", EscalationStatus.allValues()));
        }

        String attachmentIds = trimToNull(dto.getAttachmentIds());
        if (attachmentIds != null && attachmentIds.length() > ATTACHMENT_IDS_MAX) {
            // 这是本模块最容易被误判成「前端 bug」的一种失败：附件路径是逗号拼接的长串，
            // 同时选十几个扫描件就会超 1000 字。日志里把实际长度与列上限并排打出来，
            // 一眼就能判断是该「分批登记」还是该「加宽列」。
            log.warn("登记审核意见被前置校验拒绝：附件路径合计超列宽。projectId={}，实际长度={}，列上限={}，"
                            + "路径条数={}",
                    projectId, attachmentIds.length(), ATTACHMENT_IDS_MAX,
                    attachmentIds.split(",").length);
            throw new JeecgBootException("附件存储路径合计 " + attachmentIds.length() + " 字，超过当前字段上限 "
                    + ATTACHMENT_IDS_MAX + " 字，请减少本次附件数量后分批登记");
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
                .setAttachmentIds(attachmentIds)
                .setRecorderId(defaultIfNull(auditSupport.currentUsername(), dto.getRecorderId()))
                .setRecorderName(defaultIfNull(recorderName, dto.getRecorderName()))
                .setRecorderDept(defaultIfNull(auditSupport.currentDept(), dto.getRecorderDept()))
                .setRecordTime(now);
        /*
         * ★ 这里刻意用 {@code baseMapper.insert} 而不是 {@code save()}：
         *   save() 只返回 boolean（内部 SqlHelper.retBool），拿不到「受影响行数」。
         *   而定位「意见无法保存」时，「插入了 0 行」与「插入时抛异常」是两种根因，
         *   必须能区分，所以直接要行数并把真实行数写进日志。
         */
        int insertedRows = baseMapper.insert(record);
        if (insertedRows != 1) {
            log.error("登记审核意见失败：t_escalation_record 插入结果异常。projectId={}, recordId={}, "
                            + "受影响行数={}（期望 1），意见长度={}，附件路径长度={}",
                    projectId, record.getId(), insertedRows, opinion.length(),
                    attachmentIds == null ? 0 : attachmentIds.length());
            throw new JeecgBootException("审核意见记录写入失败，请查看后端数据库异常日志");
        }
        log.info("审核意见记录已写入：projectId={}, recordId={}, t_escalation_record 受影响行数={}, 意见长度={}",
                projectId, record.getId(), insertedRows, opinion.length());

        // ---- 3) 同一事务内回写主表摘要 + 记录数（重算，不累加） ----
        int recordCount = baseMapper.countByProjectId(projectId);
        String latestOpinion = truncate(opinion, LATEST_OPINION_MAX);
        int updated = baseMapper.updateLatestOpinion(projectId, latestOpinion, now, record.getRecorderName(),
                recordCount, syncStatus, auditSupport.currentUsername(), now);
        if (updated != 1) {
            // 必须抛出以回滚刚才的 INSERT，避免“记录已保存、主表摘要与状态却没更新”的半成功。
            // 日志里同时给出「刚插入了几行」与「主表回写影响了几行」：
            // 前者说明记录表没问题，后者说明是不是项目行被并发删了 / projectId 对不上。
            log.error("登记审核意见回写主表失败：projectId={}, recordId={}, t_escalation_record 已插入行数={}, "
                            + "t_escalation_project 回写受影响行数={}（期望 1）, 统计到的记录数={}",
                    projectId, record.getId(), insertedRows, updated, recordCount);
            throw new JeecgBootException("审核意见已写入但项目主表回写失败（影响行数 " + updated
                    + "），本次事务已回滚，请刷新项目后重试");
        }

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
