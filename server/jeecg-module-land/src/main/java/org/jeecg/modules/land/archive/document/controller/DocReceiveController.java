package org.jeecg.modules.land.archive.document.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.document.dto.DocArchiveDTO;
import org.jeecg.modules.land.archive.document.dto.DocHandleDTO;
import org.jeecg.modules.land.archive.document.dto.DocQueryDTO;
import org.jeecg.modules.land.archive.document.entity.DocReceive;
import org.jeecg.modules.land.archive.document.entity.DocReceiveFlow;
import org.jeecg.modules.land.archive.document.service.IDocReceiveFlowService;
import org.jeecg.modules.land.archive.document.service.IDocReceiveService;
import org.jeecg.modules.land.archive.document.vo.DocStatVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description: 收文管理（方案 2.3.2 第 9 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/doc/receive}。
 *
 * <p><b>权限码</b>（登记为 sys_permission 里 menu_type=2 的「按钮权限」，
 * 授权脚本见 {@code sql/document/04_document_permission_buttons.sql}）：
 * <pre>
 *   land:docReceive:list      列表 / 详情 / 流转记录 / 统计 / 生成登记号
 *   land:docReceive:add       收文登记
 *   land:docReceive:edit      编辑（不推进流转）
 *   land:docReceive:delete    删除 / 批量删除
 *   land:docReceive:transfer  转办 / 分办
 *   land:docReceive:reject    退回
 *   land:docReceive:finish    办结
 *   land:docReceive:archive   归档到档案管理
 * </pre>
 * ★ 流转被拆成 transfer / reject / finish 三个独立权限码，
 *   这样一个角色可以「只允许办结、不允许退回」，而不是共用一个 manage 码。
 *
 * <p>流转接口是三个语义化的动作，而不是一个「万能的 edit」：
 * <pre>
 *   POST /land/doc/receive/transfer  转办 / 分办（把待办交给指定人）
 *   POST /land/doc/receive/reject    退回（退回到上一位处理人）
 *   POST /land/doc/receive/finish    办结
 *   POST /land/doc/receive/archive   归档（必须选档案类别）
 * </pre>
 * 这样做的好处是每一步的业务校验（处理人一致性、退回必填原因、只有已办结才能归档）
 * 都能挂在对应动作上，不会像旧系统那样全部塞进 updateById 然后被注释掉。
 */
@Slf4j
@Api(tags = "收发文管理-收文")
@RestController
@RequestMapping("/land/doc/receive")
public class DocReceiveController {

    /** 权限码：收文查询（列表 / 详情 / 流转记录 / 统计 / 生成登记号） */
    private static final String PERM_LIST = "land:docReceive:list";
    /** 权限码：收文登记 */
    private static final String PERM_ADD = "land:docReceive:add";
    /** 权限码：收文编辑 */
    private static final String PERM_EDIT = "land:docReceive:edit";
    /** 权限码：收文删除 */
    private static final String PERM_DELETE = "land:docReceive:delete";
    /** 权限码：收文转办 / 分办 */
    private static final String PERM_TRANSFER = "land:docReceive:transfer";
    /** 权限码：收文退回 */
    private static final String PERM_REJECT = "land:docReceive:reject";
    /** 权限码：收文办结 */
    private static final String PERM_FINISH = "land:docReceive:finish";
    /** 权限码：收文归档 */
    private static final String PERM_ARCHIVE = "land:docReceive:archive";

    @Autowired
    private IDocReceiveService docReceiveService;

    @Autowired
    private IDocReceiveFlowService docReceiveFlowService;

    // ==================================================================
    // 查询
    // ==================================================================

    @AutoLog(value = "收文-分页列表查询")
    @ApiOperation(value = "收文-分页列表查询", notes = "onlyMine=true 时只看我的待办")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/list")
    public Result<IPage<DocReceive>> list(DocQueryDTO query) {
        return Result.OK(docReceiveService.queryPage(query));
    }

    @AutoLog(value = "收文-通过id查询")
    @ApiOperation(value = "收文-通过id查询", notes = "含附件与流转记录")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/queryById")
    public Result<DocReceive> queryById(@RequestParam(name = "id", required = true) String id) {
        DocReceive doc = docReceiveService.queryDetail(id);
        if (doc == null) {
            return Result.error("未找到对应的收文");
        }
        return Result.OK(doc);
    }

    @AutoLog(value = "收文-流转记录")
    @ApiOperation(value = "收文-流转记录", notes = "按时间正序，供时间轴展示")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/flowList")
    public Result<List<DocReceiveFlow>> flowList(@RequestParam(name = "docId", required = true) String docId) {
        return Result.OK(docReceiveFlowService.queryByDocId(docId));
    }

    @AutoLog(value = "收文-统计")
    @ApiOperation(value = "收文-统计")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/stat")
    public Result<DocStatVO> stat() {
        return Result.OK(docReceiveService.queryStat());
    }

    // ==================================================================
    // 登记 / 编辑 / 删除
    // ==================================================================

    @AutoLog(value = "收文-生成登记号")
    @ApiOperation(value = "收文-生成登记号", notes = "仅预览；格式 SW-{yyyy}-{4位}")
    @RequiresPermissions(PERM_ADD)
    @GetMapping(value = "/generateNo")
    public Result<String> generateNo(@RequestParam(name = "year", required = false) Integer year) {
        return Result.OK(docReceiveService.generateDocNo(year));
    }

    @AutoLog(value = "收文-登记")
    @ApiOperation(value = "收文-登记", notes = "写主表 + 附件 + 第一条流转记录；currentHandler 为承办人（可空）")
    @RequiresPermissions(PERM_ADD)
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody DocReceive doc) {
        try {
            return Result.OK("登记成功！", docReceiveService.createDoc(doc));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("收文登记失败", e);
            return Result.error("登记失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "收文-编辑")
    @ApiOperation(value = "收文-编辑", notes = "只改信息与附件，不推进流转")
    @RequiresPermissions(PERM_EDIT)
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody DocReceive doc) {
        try {
            docReceiveService.updateDoc(doc);
            return Result.OK("修改成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("收文编辑失败", e);
            return Result.error("修改失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "收文-删除")
    @ApiOperation(value = "收文-删除", notes = "逻辑删除，级联附件与流转记录")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
        try {
            docReceiveService.deleteDoc(id);
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("收文删除失败", e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "收文-批量删除")
    @ApiOperation(value = "收文-批量删除")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        try {
            docReceiveService.deleteDocs(Arrays.stream(ids.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
            return Result.OK("批量删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("收文批量删除失败", e);
            return Result.error("批量删除失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 流转（收文中心内的流转）
    // ==================================================================

    @AutoLog(value = "收文-转办")
    @ApiOperation(value = "收文-转办", notes = "把待办交给 toUsername；当前待办人才能操作")
    @RequiresPermissions(PERM_TRANSFER)
    @PostMapping(value = "/transfer")
    public Result<?> transfer(@RequestBody DocHandleDTO dto) {
        try {
            docReceiveService.transfer(dto);
            return Result.OK("转办成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("收文转办失败", e);
            return Result.error("转办失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "收文-退回")
    @ApiOperation(value = "收文-退回", notes = "必须填写退回原因；退回到上一位处理人")
    @RequiresPermissions(PERM_REJECT)
    @PostMapping(value = "/reject")
    public Result<?> reject(@RequestBody DocHandleDTO dto) {
        try {
            docReceiveService.reject(dto);
            return Result.OK("已退回！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("收文退回失败", e);
            return Result.error("退回失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "收文-办结")
    @ApiOperation(value = "收文-办结", notes = "办结后前端提示「是否归档」")
    @RequiresPermissions(PERM_FINISH)
    @PostMapping(value = "/finish")
    public Result<?> finish(@RequestBody DocHandleDTO dto) {
        try {
            docReceiveService.finish(dto);
            return Result.OK("已办结！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("收文办结失败", e);
            return Result.error("办结失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 归档
    // ==================================================================

    @AutoLog(value = "收文-归档")
    @ApiOperation(value = "收文-归档", notes = "必须选择档案类别；公文附件会成为该档案的卷内文件")
    @RequiresPermissions(PERM_ARCHIVE)
    @PostMapping(value = "/archive")
    public Result<String> archive(@RequestBody DocArchiveDTO dto) {
        try {
            return Result.OK("归档成功！", docReceiveService.archive(dto));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("收文归档失败", e);
            return Result.error("归档失败：" + e.getMessage());
        }
    }
}
