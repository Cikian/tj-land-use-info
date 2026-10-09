package org.jeecg.modules.land.archive.handover.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.handover.dto.HandoverQueryDTO;
import org.jeecg.modules.land.archive.handover.dto.HandoverSaveDTO;
import org.jeecg.modules.land.archive.handover.entity.RoadHandover;
import org.jeecg.modules.land.archive.handover.service.IHandoverExportService;
import org.jeecg.modules.land.archive.handover.service.IRoadHandoverService;
import org.jeecg.modules.land.archive.handover.vo.HandoverStatVO;
import org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description: 道路交付及养护协议移交事项（方案 2.3.2 第 6 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>统一前缀 {@code /land/archive/handover}（归在档案管理域下，因为方案 2.3.2
 * 把本功能列在「档案管理」九项的第 6 项）。
 *
 * <p><b>权限码</b>（全部是 sys_permission 里 menu_type=2 的按钮权限，
 * 脚本 {@code sql/handover/04_handover_permission_buttons.sql}；按钮 id 前缀
 * {@code 2ed9e0a11ed9e0a11ed9e0a11ed9e1**}）：
 * <pre>
 *   land:handover:list      列表 / 详情 / 统计 / 状态计数
 *   land:handover:add       新增 / 生成移交编号 / 编号唯一校验
 *   land:handover:edit      编辑 / 变更状态
 *   land:handover:delete    删除 / 批量删除
 *   land:handover:archive   关联档案 / 取消关联 / 挑档案
 *   land:handover:export    导出 Excel
 * </pre>
 *
 * <p>接口清单（本类 16 个）：
 * <pre>
 *   GET    /land/archive/handover/list            分页列表
 *   GET    /land/archive/handover/queryById       详情（含关联档案）
 *   GET    /land/archive/handover/stat            汇总与 6 组统计
 *   GET    /land/archive/handover/countByStatus   各状态计数（固定 3 项）
 *   POST   /land/archive/handover/add             新增
 *   PUT    /land/archive/handover/edit            编辑
 *   POST   /land/archive/handover/status          变更状态
 *   DELETE /land/archive/handover/delete          删除
 *   DELETE /land/archive/handover/deleteBatch     批量删除
 *   GET    /land/archive/handover/generateNo      预生成移交编号 YJ-{yyyy}-{4位}
 *   GET    /land/archive/handover/checkNo         移交编号唯一校验
 *   GET    /land/archive/handover/relatedArchives 该事项的关联档案
 *   GET    /land/archive/handover/archive/pick    挑档案
 *   POST   /land/archive/handover/linkArchive     关联档案
 *   POST   /land/archive/handover/unlinkArchive   取消关联
 *   GET    /land/archive/handover/exportXls       导出 Excel
 * </pre>
 *
 * <p><b>★ 查询类接口也要捕获 JeecgBootException</b>：否则业务校验失败（如非法状态值）
 * 会以 500 抛出去，前端只能看到「系统错误」而不是具体原因 —— 这是台账模块踩过的坑。
 */
@Slf4j
@Api(tags = "档案管理-道路交付及养护协议移交事项")
@RestController
@RequestMapping("/land/archive/handover")
public class RoadHandoverController {

    private static final String PERM_LIST = "land:handover:list";
    private static final String PERM_ADD = "land:handover:add";
    private static final String PERM_EDIT = "land:handover:edit";
    private static final String PERM_DELETE = "land:handover:delete";
    private static final String PERM_ARCHIVE = "land:handover:archive";
    private static final String PERM_EXPORT = "land:handover:export";

    @Autowired
    private IRoadHandoverService handoverService;

    @Autowired
    private IHandoverExportService exportService;

    // ==================================================================
    // 查询
    // ==================================================================

    @AutoLog(value = "道路移交事项-分页列表查询")
    @ApiOperation(value = "道路移交事项-分页列表查询",
            notes = "支持的检索条件见清单 §6.3.6；与统计、导出共用同一套条件；分页上限 500")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/list")
    public Result<IPage<RoadHandover>> list(HandoverQueryDTO query) {
        try {
            return Result.OK(handoverService.queryPage(query));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        }
    }

    @AutoLog(value = "道路移交事项-通过id查询")
    @ApiOperation(value = "道路移交事项-通过id查询", notes = "含关联档案列表")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/queryById")
    public Result<RoadHandover> queryById(@RequestParam(name = "id", required = true) String id) {
        RoadHandover handover = handoverService.queryDetail(id);
        if (handover == null) {
            return Result.error("未找到对应的移交事项");
        }
        return Result.OK(handover);
    }

    @AutoLog(value = "道路移交事项-统计")
    @ApiOperation(value = "道路移交事项-统计",
            notes = "汇总（总数/迁移数/已关联档案/协议待补录/养护将到期/养护已过期/宗地待核对）+ 按状态/类型/行政区/年度/接收管养单位/道路等级")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/stat")
    public Result<HandoverStatVO> stat(HandoverQueryDTO query) {
        try {
            return Result.OK(handoverService.queryStat(query));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        }
    }

    @AutoLog(value = "道路移交事项-各状态计数")
    @ApiOperation(value = "道路移交事项-各状态计数", notes = "固定返回待移交/移交中/已移交 3 项，数量为 0 也返回")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/countByStatus")
    public Result<List<HandoverStatVO.StatusCount>> countByStatus(HandoverQueryDTO query) {
        try {
            return Result.OK(handoverService.countByStatus(query));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================================================================
    // 增删改
    // ==================================================================

    @AutoLog(value = "道路移交事项-新增")
    @ApiOperation(value = "道路移交事项-新增", notes = "移交编号可留空自动生成 YJ-{yyyy}-{4位}")
    @RequiresPermissions(PERM_ADD)
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody HandoverSaveDTO dto) {
        try {
            return Result.OK("新增成功！", handoverService.createHandover(dto));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("新增道路移交事项失败", e);
            return Result.error("新增失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "道路移交事项-编辑")
    @ApiOperation(value = "道路移交事项-编辑",
            notes = "编号允许手工改写；字段允许清空（显式 SQL 覆盖，不受 MyBatis-Plus NOT_NULL 策略限制）")
    @RequiresPermissions(PERM_EDIT)
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody HandoverSaveDTO dto) {
        try {
            handoverService.updateHandover(dto);
            return Result.OK("修改成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("编辑道路移交事项失败", e);
            return Result.error("修改失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "道路移交事项-变更状态")
    @ApiOperation(value = "道路移交事项-变更状态", notes = "待移交/移交中/已移交（无流转顺序校验）")
    @RequiresPermissions(PERM_EDIT)
    @PostMapping(value = "/status")
    public Result<?> status(@RequestParam(name = "id") String id,
                            @RequestParam(name = "status") String status) {
        try {
            handoverService.changeStatus(id, status);
            return Result.OK("状态变更成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("变更道路移交事项状态失败", e);
            return Result.error("状态变更失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "道路移交事项-删除")
    @ApiOperation(value = "道路移交事项-删除", notes = "逻辑删除；不影响档案与配套项目")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
        try {
            handoverService.deleteHandover(id);
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除道路移交事项失败", e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "道路移交事项-批量删除")
    @ApiOperation(value = "道路移交事项-批量删除")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        try {
            List<String> idList = Arrays.stream(ids.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            handoverService.deleteHandovers(idList);
            return Result.OK("批量删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("批量删除道路移交事项失败", e);
            return Result.error("批量删除失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 移交编号
    // ==================================================================

    @AutoLog(value = "道路移交事项-生成移交编号")
    @ApiOperation(value = "道路移交事项-生成移交编号", notes = "仅预览，不落库；格式 YJ-{yyyy}-{4位流水}")
    @RequiresPermissions(PERM_ADD)
    @GetMapping(value = "/generateNo")
    public Result<String> generateNo(@RequestParam(name = "year", required = false) Integer year) {
        return Result.OK(handoverService.generateHandoverNo(year));
    }

    @AutoLog(value = "道路移交事项-移交编号唯一校验")
    @ApiOperation(value = "道路移交事项-移交编号唯一校验", notes = "编辑时传 id 排除自身")
    @RequiresPermissions(PERM_ADD)
    @GetMapping(value = "/checkNo")
    public Result<?> checkNo(@RequestParam(name = "handoverNo", required = true) String handoverNo,
                             @RequestParam(name = "id", required = false) String id) {
        try {
            handoverService.checkHandoverNoUnique(handoverNo, id);
            return Result.OK("移交编号可用");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================================================================
    // 关联档案
    // ==================================================================

    @AutoLog(value = "道路移交事项-关联档案列表")
    @ApiOperation(value = "道路移交事项-关联档案列表",
            notes = "按配套项目ID优先、出让宗地编号兜底反查 t_archive（只读，不改档案数据）")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/relatedArchives")
    public Result<List<RelatedArchiveVO>> relatedArchives(@RequestParam(name = "id") String id,
                                                          @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.OK(handoverService.queryRelatedArchives(id, limit));
    }

    @AutoLog(value = "道路移交事项-挑档案")
    @ApiOperation(value = "道路移交事项-挑档案", notes = "按配套项目/宗地过滤，或按关键词全局搜（只读）")
    @RequiresPermissions(PERM_ARCHIVE)
    @GetMapping(value = "/archive/pick")
    public Result<List<RelatedArchiveVO>> pickArchive(@RequestParam(name = "facilityId", required = false) String facilityId,
                                                      @RequestParam(name = "crzdbh", required = false) String crzdbh,
                                                      @RequestParam(name = "keyword", required = false) String keyword,
                                                      @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.OK(handoverService.queryArchivesForPick(facilityId, crzdbh, keyword, limit));
    }

    @AutoLog(value = "道路移交事项-关联档案")
    @ApiOperation(value = "道路移交事项-关联档案", notes = "写本表的 archive_id 与 archive_count，不修改档案表")
    @RequiresPermissions(PERM_ARCHIVE)
    @PostMapping(value = "/linkArchive")
    public Result<?> linkArchive(@RequestParam(name = "id") String id,
                                 @RequestParam(name = "archiveId") String archiveId) {
        try {
            handoverService.linkArchive(id, archiveId);
            return Result.OK("关联成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("移交事项关联档案失败", e);
            return Result.error("关联失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "道路移交事项-取消关联档案")
    @ApiOperation(value = "道路移交事项-取消关联档案", notes = "只清本表的 archive_id 指针，不删除档案本身")
    @RequiresPermissions(PERM_ARCHIVE)
    @PostMapping(value = "/unlinkArchive")
    public Result<?> unlinkArchive(@RequestParam(name = "id") String id) {
        try {
            handoverService.unlinkArchive(id);
            return Result.OK("已取消关联！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("移交事项取消关联档案失败", e);
            return Result.error("取消关联失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 导出
    // ==================================================================

    @AutoLog(value = "道路移交事项-导出Excel")
    @ApiOperation(value = "道路移交事项-导出Excel",
            notes = "文件名：道路交付及养护协议移交事项_{yyyyMMddHHmm}.xlsx；列 = 全部列表列 + 结果列")
    @RequiresPermissions(PERM_EXPORT)
    @GetMapping(value = "/exportXls")
    public void exportXls(HandoverQueryDTO query, HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = exportService.exportHandover(query);
            String fileName = "道路交付及养护协议移交事项_"
                    + new SimpleDateFormat("yyyyMMddHHmm").format(new Date()) + ".xlsx";
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            response.reset();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
                out.flush();
            }
        } catch (Exception e) {
            log.error("导出道路移交事项失败", e);
            writeJsonError(response, "导出失败：" + e.getMessage());
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (Exception e) {
                    log.warn("关闭导出工作簿失败：{}", e.getMessage());
                }
            }
        }
    }

    /** 已提交响应时不能再写错误信息 */
    private void writeJsonError(HttpServletResponse response, String message) {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\""
                    + (message == null ? "未知错误" : message.replace("\"", "'")) + "\"}");
        } catch (Exception e) {
            log.warn("回写错误信息失败：{}", e.getMessage());
        }
    }
}
