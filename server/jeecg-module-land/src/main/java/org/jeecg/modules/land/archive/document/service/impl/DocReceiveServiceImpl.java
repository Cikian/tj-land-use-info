package org.jeecg.modules.land.archive.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.document.dto.DocArchiveDTO;
import org.jeecg.modules.land.archive.document.dto.DocHandleDTO;
import org.jeecg.modules.land.archive.document.dto.DocQueryDTO;
import org.jeecg.modules.land.archive.document.entity.DocAttachment;
import org.jeecg.modules.land.archive.document.entity.DocReceive;
import org.jeecg.modules.land.archive.document.entity.DocReceiveFlow;
import org.jeecg.modules.land.archive.document.mapper.DocReceiveMapper;
import org.jeecg.modules.land.archive.document.service.IDocAttachmentService;
import org.jeecg.modules.land.archive.document.service.IDocReceiveFlowService;
import org.jeecg.modules.land.archive.document.service.IDocReceiveService;
import org.jeecg.modules.land.archive.document.support.DocSupport;
import org.jeecg.modules.land.archive.document.vo.DocArchiveResultVO;
import org.jeecg.modules.land.archive.document.vo.DocStatVO;
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
import java.util.Map;

/**
 * @Description: 收文 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p><b>流转实现（精简三级 + 退回）</b>
 * <pre>
 *   登记 ──► 待承办 ──转办/分办──► 承办中 ──办结──► 已办结 ──归档──► 已归档
 *                      ▲                │
 *                      └──── 退回 ───────┘
 * </pre>
 *
 * <p>「流转记录」用「工单」模型表达：{@code handle_time} 为空的那一条就是当前待办，
 * 其 {@code handler} 即 {@code current_handler}。所有推进动作都是
 * 「关掉当前待办 + 开一条新待办」，因此不会出现旧系统那种流转链断掉的情况。
 *
 * <p><b>与旧 tj-sfw 的差异（合理化优化）</b>：
 * <table border="1">
 *   <tr><th>项</th><th>旧系统</th><th>本实现</th></tr>
 *   <tr><td>新增收文</td><td>save() 方法体被注释，不写流转记录</td><td>登记时写「登记」记录，并可为承办人开待办</td></tr>
 *   <tr><td>编辑推进流转</td><td>updateById() 方法体被注释</td><td>编辑只改信息；流转走独立的 transfer/reject/finish 接口（职责清晰）</td></tr>
 *   <tr><td>退回</td><td>getLastFlow() 未判空 → 必 NPE；无事务</td><td>全链路判空；{@code @Transactional}；退回目标自动取上一位处理人</td></tr>
 *   <tr><td>权限</td><td>@RequiresPermissions 全被注释</td><td>接口级权限码 doc:receive:manage + 处理人一致性校验</td></tr>
 *   <tr><td>附件</td><td>逗号分隔的 file_path</td><td>独立附件表，文件名含逗号也不会被拆错</td></tr>
 * </table>
 */
@Slf4j
@Service
public class DocReceiveServiceImpl extends ServiceImpl<DocReceiveMapper, DocReceive> implements IDocReceiveService {

    /** 登记号前缀 */
    private static final String DOC_NO_PREFIX = "SW-";
    /** 登记号生成的最大重试次数 */
    private static final int NO_RETRY = 3;
    /** 关联收发文默认条数（档案详情的「收发文情况」块） */
    private static final int RELATED_LIMIT = 100;

    @Autowired
    private DocSupport docSupport;

    @Autowired
    private IDocAttachmentService attachmentService;

    @Autowired
    private IDocReceiveFlowService flowService;

    /**
     * 直接注入 IArchiveService（而不是让它反过来依赖收文服务），
     * 避免 Spring Boot 2.6 默认禁止的循环依赖；
     * 反向的「档案详情看收发文」放在 ArchiveController 里组合，不进入 Service 依赖图。
     */
    @Autowired
    private IArchiveService archiveService;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public IPage<DocReceive> queryPage(DocQueryDTO query) {
        DocQueryDTO condition = query == null ? new DocQueryDTO() : query;
        if (Boolean.TRUE.equals(condition.getOnlyMine())) {
            condition.setCurrentHandler(docSupport.currentUsername());
        }
        Page<DocReceive> page = new Page<>(condition.resolvePageNo(), condition.resolvePageSize());
        return baseMapper.selectDocPage(page, condition);
    }

    @Override
    public DocReceive queryDetail(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        DocReceive doc = getById(id);
        if (doc == null) {
            return null;
        }
        doc.setAttachments(attachmentService.queryByDoc(DocAttachment.DOC_TYPE_RECEIVE, id));
        doc.setFlows(flowService.queryByDocId(id));
        // 详情页的「归档」标签也要按档案真实状态显示（列表由 SQL 带出，详情这里补上）
        if (StringUtils.isNotBlank(doc.getArchiveId())) {
            Archive archive = archiveService.getById(doc.getArchiveId());
            doc.setArchiveStatus(archive == null ? null : archive.getStatus());
        }
        return doc;
    }

    @Override
    public List<DocReceive> queryRelated(String facilityId, String landId, String crzdbh, Integer limit) {
        QueryWrapper<DocReceive> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(facilityId)) {
            wrapper.eq("facility_id", facilityId.trim());
        } else if (StringUtils.isNotBlank(landId)) {
            wrapper.eq("land_id", landId.trim());
        } else if (StringUtils.isNotBlank(crzdbh)) {
            wrapper.eq("crzdbh", crzdbh.trim());
        } else {
            return Collections.emptyList();
        }
        wrapper.orderByDesc("receive_date").orderByDesc("create_time");
        int size = limit == null || limit <= 0 ? RELATED_LIMIT : Math.min(limit, 500);
        Page<DocReceive> page = page(new Page<>(1, size, false), wrapper);
        return page.getRecords();
    }

    @Override
    public List<DocReceive> queryByArchiveId(String archiveId, Integer limit) {
        if (StringUtils.isBlank(archiveId)) {
            return Collections.emptyList();
        }
        int size = limit == null || limit <= 0 ? RELATED_LIMIT : Math.min(limit, 500);
        QueryWrapper<DocReceive> wrapper = new QueryWrapper<DocReceive>()
                .eq("archive_id", archiveId.trim())
                .orderByDesc("receive_date")
                .orderByDesc("create_time");
        return page(new Page<>(1, size, false), wrapper).getRecords();
    }

    @Override
    public DocStatVO queryStat() {
        DocStatVO stat = new DocStatVO();
        List<Map<String, Object>> rows = baseMapper.countGroupByStatus();
        long total = 0L;
        for (Map<String, Object> row : rows) {
            String status = row.get("status") == null ? null : String.valueOf(row.get("status"));
            long cnt = toLong(row.get("cnt"));
            total += cnt;
            if (DocReceive.STATUS_PENDING.equals(status)) {
                stat.setPending(cnt);
            } else if (DocReceive.STATUS_HANDLING.equals(status)) {
                stat.setHandling(cnt);
            } else if (DocReceive.STATUS_REJECTED.equals(status)) {
                stat.setRejected(cnt);
            } else if (DocReceive.STATUS_FINISHED.equals(status)) {
                stat.setFinished(cnt);
            } else if (DocReceive.STATUS_ARCHIVED.equals(status)) {
                stat.setArchived(cnt);
                // 已归档是从「已办结」走过来的，统计「已办结」时应把它算进去
                stat.setFinished(stat.getFinished() + cnt);
            }
        }
        stat.setTotal(total);
        String username = docSupport.currentUsername();
        stat.setMyTodo(StringUtils.isBlank(username) ? 0L : baseMapper.countMyTodo(username));
        return stat;
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
    public String createDoc(DocReceive doc) {
        if (doc == null) {
            throw new JeecgBootException("缺少收文数据");
        }
        if (StringUtils.isBlank(doc.getDocTitle())) {
            throw new JeecgBootException("文件标题不能为空");
        }
        fillAssociation(doc);
        if (doc.getStatus() == null) {
            doc.setStatus(DocReceive.STATUS_PENDING);
        }
        if (StringUtils.isBlank(doc.getSecretLevel())) {
            doc.setSecretLevel("一般");
        }

        // 登记号：手工填了就校验唯一；没填就自动生成
        JeecgBootException lastError = null;
        for (int attempt = 0; attempt < NO_RETRY; attempt++) {
            String docNo = StringUtils.isNotBlank(doc.getDocNo())
                    ? doc.getDocNo().trim()
                    : nextDocNo(resolveYear(doc.getReceiveDate()));
            checkDocNoUnique(docNo, null);
            doc.setDocNo(docNo).setId(IdWorker.getIdStr()).setDelFlag(0);
            if (save(doc)) {
                lastError = null;
                break;
            }
            lastError = new JeecgBootException("收文保存失败，请重试");
        }
        if (lastError != null) {
            throw lastError;
        }

        attachmentService.syncAttachments(DocAttachment.DOC_TYPE_RECEIVE, doc.getId(), doc.getAttachments());

        // 流转：① 登记记录（当场完成）② 若指定了承办人，再开一条待办给他
        DocReceiveFlow registerFlow = flowService.openFlow(doc.getId(),
                DocReceiveFlow.ACTION_REGISTER, DocReceiveFlow.NODE_REGISTER,
                docSupport.currentUsername(), docSupport.currentRealname(), "收文登记");
        flowService.closeFlow(registerFlow, "收文登记");

        String handler = trimToNull(doc.getCurrentHandler());
        if (StringUtils.isNotBlank(handler)) {
            String handlerName = docSupport.requireRealname(handler);
            flowService.openFlow(doc.getId(), DocReceiveFlow.ACTION_TRANSFER, DocReceiveFlow.NODE_HANDLE,
                    handler, handlerName, null);
            updateById(new DocReceive()
                    .setId(doc.getId())
                    .setCurrentHandler(handler)
                    .setCurrentHandlerName(handlerName)
                    .setStatus(DocReceive.STATUS_PENDING));
        } else {
            // 未指定承办人：处于「待分办」状态，由有权限的人分办
            updateById(new DocReceive().setId(doc.getId()).setStatus(DocReceive.STATUS_PENDING));
        }
        log.info("收文登记成功：id={}, docNo={}, 承办人={}", doc.getId(), doc.getDocNo(), handler);
        return doc.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDoc(DocReceive doc) {
        if (doc == null || StringUtils.isBlank(doc.getId())) {
            throw new JeecgBootException("缺少收文ID");
        }
        DocReceive old = getById(doc.getId());
        if (old == null) {
            throw new JeecgBootException("收文不存在或已被删除");
        }
        // ★ P1-11 第 2 条：已归档的收文只读。
        //   前端把「编辑」入口藏了，但直接调接口仍能改（旧系统就是这么被绕过的），
        //   所以这里必须有显式校验；提示里带上档案ID，便于用户去档案里改。
        assertNotArchived(old, "编辑");
        String docNo = StringUtils.isNotBlank(doc.getDocNo()) ? doc.getDocNo() : old.getDocNo();
        checkDocNoUnique(docNo, old.getId());

        DocReceive update = new DocReceive().setId(old.getId());
        update.setDocNo(docNo.trim());
        update.setDocTitle(trimToNull(doc.getDocTitle()));
        update.setDocType(trimToNull(doc.getDocType()));
        update.setFromDept(trimToNull(doc.getFromDept()));
        update.setFromDocNo(trimToNull(doc.getFromDocNo()));
        update.setReceiveDate(doc.getReceiveDate());
        update.setUrgency(trimToNull(doc.getUrgency()));
        update.setSecretLevel(trimToNull(doc.getSecretLevel()));
        update.setPageCount(doc.getPageCount());
        update.setCopies(doc.getCopies());
        update.setHandleDeadline(doc.getHandleDeadline());
        update.setRemark(trimToNull(doc.getRemark()));

        // 关联项目允许改
        fillAssociation(doc);
        update.setLandId(trimToNull(doc.getLandId()));
        update.setCrzdbh(trimToNull(doc.getCrzdbh()));
        update.setFacilityId(trimToNull(doc.getFacilityId()));
        update.setPtxmmc(trimToNull(doc.getPtxmmc()));

        updateById(update);
        attachmentService.syncAttachments(DocAttachment.DOC_TYPE_RECEIVE, old.getId(), doc.getAttachments());
        log.info("收文编辑成功：id={}, docNo={}", old.getId(), docNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDoc(String id) {
        DocReceive doc = getById(id);
        if (doc == null) {
            throw new JeecgBootException("收文不存在或已被删除");
        }
        attachmentService.deleteByDoc(DocAttachment.DOC_TYPE_RECEIVE, id);
        flowService.remove(new QueryWrapper<DocReceiveFlow>().eq("doc_id", id));
        removeById(id);
        log.info("收文删除成功：id={}, docNo={}", id, doc.getDocNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocs(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new JeecgBootException("请选择要删除的收文");
        }
        for (String id : ids) {
            if (StringUtils.isNotBlank(id)) {
                deleteDoc(id.trim());
            }
        }
    }

    // ==================================================================
    // 流转
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(DocHandleDTO dto) {
        DocReceive doc = requireDoc(dto);
        assertFinished(doc, "转办");
        String toUsername = trimToNull(dto.getToUsername());
        if (StringUtils.isBlank(toUsername)) {
            throw new JeecgBootException("请选择转办的处理人");
        }
        String me = docSupport.currentUsername();
        if (toUsername.equals(me)) {
            throw new JeecgBootException("不能把文件转办给自己");
        }
        DocReceiveFlow open = flowService.currentOpenFlow(doc.getId());
        assertHandler(open, me, "转办");
        flowService.closeFlow(open, dto.getOpinion());

        String toName = docSupport.requireRealname(toUsername);
        flowService.openFlow(doc.getId(), DocReceiveFlow.ACTION_TRANSFER, DocReceiveFlow.NODE_HANDLE,
                toUsername, toName, null);
        updateById(new DocReceive()
                .setId(doc.getId())
                .setCurrentHandler(toUsername)
                .setCurrentHandlerName(toName)
                .setStatus(DocReceive.STATUS_HANDLING));
        log.info("收文转办成功：docId={}, from={}, to={}", doc.getId(), me, toUsername);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(DocHandleDTO dto) {
        DocReceive doc = requireDoc(dto);
        assertFinished(doc, "退回");
        String me = docSupport.currentUsername();
        DocReceiveFlow open = flowService.currentOpenFlow(doc.getId());
        assertHandler(open, me, "退回");
        if (StringUtils.isBlank(dto.getOpinion())) {
            throw new JeecgBootException("退回必须填写退回原因");
        }

        // 退回目标 = 当前待办之前最近的一个「不是自己」的处理人；都找不到则退回给登记人
        String target = resolveRejectTarget(doc, open, me);
        if (StringUtils.isBlank(target)) {
            throw new JeecgBootException(
                    "找不到可退回的对象（上一处理人与登记人都是当前用户），请改用「转办」指定处理人");
        }

        flowService.closeFlow(open, "退回：" + dto.getOpinion().trim());
        String targetName = docSupport.realnameOf(target);
        flowService.openFlow(doc.getId(), DocReceiveFlow.ACTION_REJECT, DocReceiveFlow.NODE_HANDLE,
                target, targetName, null);
        updateById(new DocReceive()
                .setId(doc.getId())
                .setCurrentHandler(target)
                .setCurrentHandlerName(targetName)
                .setStatus(DocReceive.STATUS_REJECTED));
        log.info("收文退回成功：docId={}, from={}, to={}", doc.getId(), me, target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(DocHandleDTO dto) {
        DocReceive doc = requireDoc(dto);
        if (DocReceive.STATUS_FINISHED.equals(doc.getStatus())
                || DocReceive.STATUS_ARCHIVED.equals(doc.getStatus())) {
            throw new JeecgBootException("该收文已办结，无需重复操作");
        }
        String me = docSupport.currentUsername();
        DocReceiveFlow open = flowService.currentOpenFlow(doc.getId());
        assertHandler(open, me, "办结");
        flowService.closeFlow(open, dto.getOpinion());

        String opinion = trimToNull(dto.getOpinion());
        DocReceiveFlow finishFlow = flowService.openFlow(doc.getId(), DocReceiveFlow.ACTION_FINISH,
                DocReceiveFlow.NODE_FINISH, me, docSupport.currentRealname(), opinion);
        flowService.closeFlow(finishFlow, opinion);

        updateById(new DocReceive()
                .setId(doc.getId())
                .setStatus(DocReceive.STATUS_FINISHED)
                .setFinishTime(new Date())
                .setFinishOpinion(opinion));
        // ★ 清空 current_handler / current_handler_name 必须走 UpdateWrapper：
        //   MyBatis-Plus 的 updateById 默认忽略 null 字段（FieldStrategy.NOT_NULL），
        //   直接 setCurrentHandler(null) 是「不更新」而不是「更新为 NULL」。
        update(new UpdateWrapper<DocReceive>()
                .eq("id", doc.getId())
                .set("current_handler", null)
                .set("current_handler_name", null));
        log.info("收文办结成功：docId={}, 操作人={}", doc.getId(), me);
    }

    // ==================================================================
    // 归档
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocArchiveResultVO archive(DocArchiveDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getDocId())) {
            throw new JeecgBootException("缺少收文ID");
        }
        if (StringUtils.isBlank(dto.getCategoryId())) {
            throw new JeecgBootException("归档必须选择档案类别");
        }
        DocReceive doc = getById(dto.getDocId());
        if (doc == null) {
            throw new JeecgBootException("收文不存在或已被删除");
        }
        if (StringUtils.isNotBlank(doc.getArchiveId())) {
            throw new JeecgBootException("该收文已归档，无需重复归档");
        }
        if (!DocReceive.STATUS_FINISHED.equals(doc.getStatus())) {
            throw new JeecgBootException("只有「已办结」的收文才能归档");
        }

        // ① 原附件数（断点核验的第一段）
        List<DocAttachment> attachments =
                attachmentService.queryByDoc(DocAttachment.DOC_TYPE_RECEIVE, doc.getId());
        // ② 转换出的卷内文件数（第二段）
        List<ArchiveFile> files = toArchiveFiles(attachments, dto.getCategoryId());

        String archiveId;
        boolean reused = false;
        // ③ 落库行数（第三段）—— 必须是库内真实行数，不是入参大小（P1-11）
        int savedFileCount;
        /*
         * ★ P2-07 第 4 条「一项目一案卷」：先找同项目已有案卷，有就追加文件，没有才新建。
         *   只追加、不删除、不合并任何历史案卷；对历史数据的影响见交付报告。
         */
        Archive reuse = archiveService.findReuseCaseFile(doc.getFacilityId(), doc.getCrzdbh());
        if (reuse != null) {
            archiveId = reuse.getId();
            // appendFiles 内部已改成「保存后回查库、返回真实新增行数」
            savedFileCount = archiveService.appendFiles(archiveId, files);
            reused = true;
            log.info("收文归档复用同项目案卷：docId={}, archiveId={}, archiveNo={}, 库内新增行数={}",
                    doc.getId(), archiveId, reuse.getArchiveNo(), savedFileCount);
        } else {
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
                    .setResponsibleUser(doc.getCurrentHandlerName())
                    .setSourceType(Archive.SOURCE_DOC_RECEIVE)
                    .setSourceId(doc.getId())
                    .setRemark("由收文【" + doc.getDocNo() + "】办结归档")
                    // 无附件时不能标成「已归档」：归档中 = 卷已建、扫描件待补
                    .setStatus(files.isEmpty() ? Archive.STATUS_ARCHIVING : Archive.STATUS_ARCHIVED);
            fillArchiveAssociation(archive, doc);
            archiveId = archiveService.createArchive(archive, files);
            /*
             * ★ P1-11：新建分支以前写 files.size()，那是「转换出几个」而不是「库里存了几个」。
             *   这里改成建卷后立刻回查 t_archive_file，让第三段计数与档案文件页签的条数一致。
             */
            savedFileCount = archiveService.countFiles(archiveId);
        }
        if (savedFileCount < files.size()) {
            // 不抛异常：文件已写进去一部分，回滚会把那部分也丢掉，还会让收文一直卡在「已办结」。
            // 但必须 warn，并让 verifyText 如实变小 —— 不能假装成功。
            log.warn("收文归档落库行数小于转换文件数：docId={}, archiveId={}, reused={}, 转换={}, 落库={}，"
                            + "缺失的通常是 store_path 为空的附件（见 ArchiveFileServiceImpl#saveFiles 的校验）",
                    doc.getId(), archiveId, reused, files.size(), savedFileCount);
        }

        /*
         * 公文自身的状态跟档案的真实状态走（P1-11 第 5 条）：
         *   · 有附件 → 档案已归档 → 公文「已归档」；
         *   · 无附件 → 档案「归档中」 → 公文保持「已办结」（archiveId 已指向案卷，
         *     前端据此显示「归档中」，并仍然禁止编辑——它已经进了档案）。
         */
        Archive saved = archiveService.getById(archiveId);
        String archiveStatus = saved == null ? null : saved.getStatus();
        boolean reallyArchived = Archive.STATUS_ARCHIVED.equals(archiveStatus);
        updateById(new DocReceive()
                .setId(doc.getId())
                .setArchiveId(archiveId)
                .setStatus(reallyArchived ? DocReceive.STATUS_ARCHIVED : DocReceive.STATUS_FINISHED));

        DocArchiveResultVO result = new DocArchiveResultVO()
                .setArchiveId(archiveId)
                .setArchiveNo(saved == null ? null : saved.getArchiveNo())
                .setReusedCaseFile(reused)
                .setArchiveStatus(archiveStatus)
                .setDocStatus(reallyArchived ? DocReceive.STATUS_ARCHIVED : DocReceive.STATUS_FINISHED)
                .setAttachmentCount(attachments.size())
                .setConvertedFileCount(files.size())
                .setSavedFileCount(savedFileCount)
                // 第三段是「库里真实新增的行数」；对不上时把差额明写出来，
                // 让用户看到的是「落库 2 行（比转换少 1 个…）」而不是一句整齐的成功话术。
                .setVerifyText("原附件 " + attachments.size() + " 个 → 转换 " + files.size()
                        + " 个 → 落库 " + savedFileCount + " 行"
                        + (savedFileCount < files.size()
                        ? "（比转换数少 " + (files.size() - savedFileCount)
                        + " 个，请查看后端日志定位缺失的附件）" : ""));
        // 断点核验日志：数字对不上时看这一行就能定位是哪一段丢了
        log.info("收文归档成功：docId={}, archiveId={}, reused={}, 原附件数={}, 转换文件数={}, "
                        + "写入t_archive_file行数={}, 档案状态={}",
                doc.getId(), archiveId, reused, attachments.size(), files.size(),
                savedFileCount, archiveStatus);
        return result;
    }

    /**
     * 已归档的收文禁止修改（后端兜底，防止绕过页面直接调接口）。
     *
     * <p>判断依据是 {@code archive_id} 非空：只要进过档案，内容就已经是档案的一部分，
     * 单方面改公文会让档案里的卷内文件与公文对不上。要改请走档案维护。
     */
    private void assertNotArchived(DocReceive doc, String action) {
        if (doc != null && StringUtils.isNotBlank(doc.getArchiveId())) {
            throw new JeecgBootException("该收文已归档（档案ID：" + doc.getArchiveId()
                    + "），不能再" + action + "；如需更正请在档案维护里处理");
        }
    }

    /** 把公文附件转成档案卷内文件（类别由归档弹窗统一选择） */
    private List<ArchiveFile> toArchiveFiles(List<DocAttachment> attachments, String categoryId) {
        List<ArchiveFile> files = new ArrayList<>();
        int seq = 1;
        for (DocAttachment attachment : attachments) {
            files.add(new ArchiveFile()
                    .setCategoryId(categoryId)
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
        return files;
    }

    /** 从配套项目补齐宗地 / 项目 / 行政区等冗余字段 */
    private void fillArchiveAssociation(Archive archive, DocReceive doc) {
        Facility facility = StringUtils.isNotBlank(doc.getFacilityId())
                ? safeFacility(doc.getFacilityId()) : null;
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
        } else if (StringUtils.isNotBlank(archive.getCrzdbh())) {
            Land land = docSupport.queryLandByCrzdbh(archive.getCrzdbh());
            if (land != null) {
                archive.setLandId(land.getId())
                        .setDkmc(land.getDkmc())
                        .setXzqh(land.getXzqh());
            }
        }
    }

    /** 取配套项目；不存在时返回 null（归档场景不因关联失效而阻断） */
    private Facility safeFacility(String facilityId) {
        try {
            return docSupport.requireFacility(facilityId);
        } catch (Exception e) {
            log.warn("收文归档时配套项目已失效：facilityId={}, 原因={}", facilityId, e.getMessage());
            return null;
        }
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    private DocReceive requireDoc(DocHandleDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getDocId())) {
            throw new JeecgBootException("缺少收文ID");
        }
        DocReceive doc = getById(dto.getDocId());
        if (doc == null) {
            throw new JeecgBootException("收文不存在或已被删除");
        }
        return doc;
    }

    private void assertFinished(DocReceive doc, String action) {
        if (DocReceive.STATUS_FINISHED.equals(doc.getStatus())
                || DocReceive.STATUS_ARCHIVED.equals(doc.getStatus())) {
            throw new JeecgBootException("该收文已办结，不能再" + action);
        }
    }

    /**
     * 处理人一致性校验。
     *
     * <p>旧系统 rejectFlow 在 {@code getLastFlow()} 为 null 时直接 .getRecipient() → 必 NPE；
     * 这里改成显式判空 + 明确提示。
     */
    private void assertHandler(DocReceiveFlow open, String me, String action) {
        if (StringUtils.isBlank(me)) {
            throw new JeecgBootException("无法识别当前登录用户，请重新登录后再操作");
        }
        if (open == null) {
            // 没有待办（例如登记时未指定承办人），允许有权限的人直接分办
            return;
        }
        if (!me.equals(open.getHandler())) {
            throw new JeecgBootException("当前文件的流程不属于当前用户，不能" + action);
        }
    }

    /**
     * 退回目标。
     *
     * <p>规则：从当前待办往前找<b>最近的一个不是自己的处理人</b>；
     * 若前面的处理人全是自己，再退化为「登记人」；登记人也是自己则返回 null，
     * 由调用方给出「请改用转办指定处理人」的提示。
     *
     * <p>为什么要跳过自己：登记环节会把「登记人」也写成一条流转记录，
     * 当登记人与承办人是同一个账号时，单纯取「上一条流转的处理人」会得到自己，
     * 变成一条退不出去的流程。
     */
    private String resolveRejectTarget(DocReceive doc, DocReceiveFlow open, String me) {
        List<DocReceiveFlow> flows = flowService.queryByDocId(doc.getId());
        String target = null;
        for (DocReceiveFlow flow : flows) {
            if (open != null && flow.getId().equals(open.getId())) {
                break;
            }
            String handler = flow.getHandler();
            if (StringUtils.isNotBlank(handler) && !handler.equals(me)) {
                // 循环是从早到晚，最后一次赋值即「最近的一个不是自己的处理人」
                target = handler;
            }
        }
        if (StringUtils.isBlank(target) && StringUtils.isNotBlank(doc.getCreateBy())
                && !doc.getCreateBy().equals(me)) {
            target = doc.getCreateBy();
        }
        return target;
    }

    private void checkDocNoUnique(String docNo, String excludeId) {
        if (StringUtils.isBlank(docNo)) {
            throw new JeecgBootException("收文登记号不能为空");
        }
        if (baseMapper.countByDocNo(docNo.trim(), excludeId) > 0) {
            throw new JeecgBootException("收文登记号「" + docNo.trim() + "」已存在，请更换");
        }
    }

    private String nextDocNo(int year) {
        String prefix = DOC_NO_PREFIX + year + "-";
        Integer max = baseMapper.selectMaxSeqOfYear(prefix);
        int next = (max == null ? 0 : max) + 1;
        return prefix + String.format("%04d", next);
    }

    /** 关联项目/宗地补齐：前端只传 facilityId 也能落全冗余字段 */
    private void fillAssociation(DocReceive doc) {
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

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
