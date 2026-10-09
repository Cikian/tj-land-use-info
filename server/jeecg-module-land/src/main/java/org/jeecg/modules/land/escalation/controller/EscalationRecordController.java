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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @Autowired
    private IEscalationRecordService recordService;

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
    public Result<?> add(@RequestBody EscalationRecordDTO dto) {
        try {
            EscalationRecord record = recordService.addRecord(dto);
            return Result.OK("登记成功！", record);
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("登记提级论证意见失败", e);
            return Result.error("登记失败：" + e.getMessage());
        }
    }
}
