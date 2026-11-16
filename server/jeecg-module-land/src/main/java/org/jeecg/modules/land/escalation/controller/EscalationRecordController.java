package org.jeecg.modules.land.escalation.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.escalation.dto.EscalationRecordDTO;
import org.jeecg.modules.land.escalation.entity.EscalationRecord;
import org.jeecg.modules.land.escalation.service.IEscalationRecordService;
import org.jeecg.modules.land.escalation.support.EscalationAuditSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @Description: 提级论证审核意见 / 办理记录（方案 2.3.3 第 4 项：可批注审核意见）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/escalation/record}。
 *
 * <p><b>★ 本类刻意只有 2 个接口</b>（设计文档 4.2 注）：
 * <pre>
 *   GET  /land/escalation/record/list   意见记录列表（时间倒序）
 *   POST /land/escalation/record/add    登记一条意见（append-only）
 * </pre>
 * <b>没有 edit / delete</b>：意见一旦登记即不可篡改，这是方案痛点
 * 「可追溯性不足」的技术回答。「登记意见」归 {@code land:escalation:edit}
 * 权限（不再单设 approve 权限码，见设计文档 2.5）。
 *
 * <p>登记成功后由 Service 在<b>同一事务</b>内回写主表最新意见摘要与记录数；
 * 仅当入参带了 {@code status} 时才顺带更新办理状态（界面上的「☐ 同时更新办理状态」，
 * 默认不勾选）——系统只存不推，不做意见驱动的状态流转。
 */
@Slf4j
@Api(tags = "提级论证-审核意见登记")
@RestController
@RequestMapping("/land/escalation/record")
public class EscalationRecordController {

    /** 权限码：查询（意见记录列表） */
    private static final String PERM_LIST = "land:escalation:list";
    /** 权限码：登记意见 */
    private static final String PERM_EDIT = "land:escalation:edit";

    /** 意见正文摘要的长度（首尾各取这么多字），只用于日志定位，不落库 */
    private static final int LOG_SUMMARY_CHARS = 20;

    @Autowired
    private IEscalationRecordService recordService;

    /** 当前登录账号（日志用）；取不到时返回 null，不抛异常 */
    @Autowired
    private EscalationAuditSupport auditSupport;

    @AutoLog(value = "提级论证意见-记录列表")
    @ApiOperation(value = "提级论证意见-记录列表", notes = "时间倒序；append-only 只读")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/list")
    public Result<List<EscalationRecord>> list(@RequestParam(name = "projectId", required = true) String projectId) {
        return Result.OK(recordService.queryByProjectId(projectId));
    }

    @AutoLog(value = "提级论证意见-登记")
    @ApiOperation(value = "提级论证意见-登记",
            notes = "append-only：只增不改不删。可选 status 用于「同时更新办理状态」，默认不传即不改状态")
    @RequiresPermissions(PERM_EDIT)
    @PostMapping(value = "/add")
    public Result<?> add(@RequestBody EscalationRecordDTO dto, HttpServletRequest request) {
        /*
         * ★ P0-05：入口先打一行「可定位」的上下文，再进业务。
         *
         * 为什么必须打在入口而不是只打在 catch 里：用户报「意见无法保存」时，
         * 现有日志只有 projectId + 原因 + 异常栈，无法区分下面这几种完全不同的根因 ——
         *   · 正文超过 opinion 的列宽（2000 字）；
         *   · 附件存储路径合计超过 attachment_ids 的列宽（1000 字）；
         *   · 请求体根本没反序列化成功（Content-Type 不是 application/json、
         *     或前端把数组当字符串传），此时 dto 的每个字段都是 null；
         *   · 前端传的 projectId 是空串。
         * 这三种情况在日志里长得一样，只能靠「进方法时到底收到了什么」区分。
         *
         * 为什么不打完整正文：意见里可能有不适合进日志的内容，而且整段正文会把
         * 单行日志撑到无法阅读 —— 只留首尾各 {@value #LOG_SUMMARY_CHARS} 字摘要，
         * 足够认出「是同一条意见」和「有没有被截断/转义」。
         *
         * 另外一条隐含判据：**若本行日志完全没有出现**，说明请求没进到这个方法
         * （Content-Type 不匹配、请求体为空、被网关/权限拦下），
         * 这时要把排查方向从「业务校验」转到「请求链路」。
         */
        log.info("登记提级论证意见-入口：projectId={}, 意见长度={}, 意见摘要={}, 附件路径长度={}, "
                        + "记录类型={}, 结论短语长度={}, 同步状态={}, 登录账号={}, HTTP={}, Content-Type={}",
                dto == null ? null : dto.getProjectId(),
                lengthOf(dto == null ? null : dto.getOpinion()),
                summarize(dto == null ? null : dto.getOpinion()),
                lengthOf(dto == null ? null : dto.getAttachmentIds()),
                dto == null ? null : dto.getRecordType(),
                lengthOf(dto == null ? null : dto.getAction()),
                dto == null ? null : dto.getStatus(),
                auditSupport.currentUsername(),
                request == null ? null : request.getMethod(),
                request == null ? null : request.getContentType());
        if (dto == null) {
            // 能进方法却拿到 null，只可能是请求体是字面量 null —— 单独点出来，省得去翻校验代码
            log.warn("登记提级论证意见失败：请求体为空（dto == null），登录账号={}，Content-Type={}",
                    auditSupport.currentUsername(),
                    request == null ? null : request.getContentType());
            return Result.error("登记失败：请求内容为空，请刷新页面后重试");
        }
        try {
            EscalationRecord record = recordService.addRecord(dto);
            return Result.OK("登记成功！", record);
        } catch (JeecgBootException e) {
            // 业务校验拒绝：这是「可解释的失败」，用 warn 即可（error 会触发告警噪音）；
            // 但仍然把入口上下文一起带上，否则运维不知道该改哪一段输入。
            log.warn("登记提级论证意见被业务校验拒绝：projectId={}，意见长度={}，附件路径长度={}，"
                            + "登录账号={}，原因={}",
                    dto.getProjectId(), lengthOf(dto.getOpinion()), lengthOf(dto.getAttachmentIds()),
                    auditSupport.currentUsername(), e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            // 非预期异常（数据库列宽、连接、事务回滚等）：必须带栈，且把入口上下文重复一遍 ——
            // 排查时通常只捞 error 级别的日志行。
            log.error("登记提级论证意见失败：projectId={}，意见长度={}，意见摘要={}，附件路径长度={}，"
                            + "记录类型={}, 登录账号={}, HTTP={}, Content-Type={}",
                    dto.getProjectId(), lengthOf(dto.getOpinion()), summarize(dto.getOpinion()),
                    lengthOf(dto.getAttachmentIds()), dto.getRecordType(),
                    auditSupport.currentUsername(),
                    request == null ? null : request.getMethod(),
                    request == null ? null : request.getContentType(), e);
            return Result.error("登记失败：" + e.getMessage());
        }
    }

    /** 文本长度；null 记 0，便于日志里直接看「是不是没传」 */
    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    /**
     * 首尾各截 {@value #LOG_SUMMARY_CHARS} 字的摘要。
     *
     * <p>刻意「首 + 尾」而不是只取开头：意见被前端按列宽截断时，
     * 结尾往往会出现半个字/半个标点，尾巴是判断「是否被截断」的唯一线索。
     */
    private static String summarize(String value) {
        if (value == null) {
            return "（空）";
        }
        if (value.length() <= LOG_SUMMARY_CHARS * 2) {
            return value;
        }
        return value.substring(0, LOG_SUMMARY_CHARS) + "…"
                + value.substring(value.length() - LOG_SUMMARY_CHARS);
    }
}
