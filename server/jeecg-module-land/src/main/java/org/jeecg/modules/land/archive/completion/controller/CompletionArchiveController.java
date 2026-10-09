package org.jeecg.modules.land.archive.completion.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.completion.dto.CompletionQueryDTO;
import org.jeecg.modules.land.archive.completion.dto.CompletionSaveDTO;
import org.jeecg.modules.land.archive.completion.entity.CompletionArchive;
import org.jeecg.modules.land.archive.completion.service.ICompletionArchiveService;
import org.jeecg.modules.land.archive.completion.service.ICompletionExportService;
import org.jeecg.modules.land.archive.completion.vo.CompletionStatVO;
import org.jeecg.modules.land.archive.completion.vo.RelatedArchiveVO;
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
 * @Description: 竣工验收项目历史工程资料数字化档案（方案 2.3.2 第 8 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/archive/completion}（归在档案管理域下，
 * 因为方案 2.3.2 把本功能列在「档案管理」九项里）。
 *
 * <p><b>权限码</b>（全部登记为 sys_permission 里 menu_type=2 的「按钮权限」，
 * 授权脚本见 {@code sql/completion/04_completion_permission_buttons.sql}；
 * 按钮 id 前缀 {@code 3ed9e0a11ed9e0a11ed9e0a11ed9e1**}）：
 * <pre>
 *   land:completion:list      列表 / 详情 / 统计 / 状态计数 / 关联扫描件列表
 *   land:completion:add       新增 / 生成档案编号 / 编号唯一校验
 *   land:completion:edit      编辑 / 变更数字化状态 / 挑档案 / 关联与取消关联
 *   land:completion:delete    删除 / 批量删除
 *   land:completion:export    导出档案信息 Excel
 * </pre>
 *
 * <p>接口清单（本类 16 个）：
 * <pre>
 *   GET    /land/archive/completion/list            档案分页（档案页/查询共用）
 *   GET    /land/archive/completion/queryById       详情（含数字化进度 + 关联扫描件）
 *   GET    /land/archive/completion/stat            汇总与 6 组统计
 *   GET    /land/archive/completion/countByStatus   各数字化状态计数（固定 3 项）
 *   POST   /land/archive/completion/add             新增
 *   PUT    /land/archive/completion/edit            编辑
 *   POST   /land/archive/completion/status          变更数字化状态
 *   DELETE /land/archive/completion/delete          删除
 *   DELETE /land/archive/completion/deleteBatch     批量删除
 *   GET    /land/archive/completion/generateNo      预生成档案编号 JG-{yyyy}-{4位}
 *   GET    /land/archive/completion/checkNo         档案编号唯一校验
 *   GET    /land/archive/completion/relatedArchives 该档案的关联扫描件
 *   GET    /land/archive/completion/archive/pick    挑档案（关联时用）
 *   POST   /land/archive/completion/linkArchive     关联扫描件
 *   POST   /land/archive/completion/unlinkArchive   取消关联
 *   GET    /land/archive/completion/exportXls       导出档案信息 Excel
 * </pre>
 *
 * <p><b>★ 查询接口也捕获异常</b>：业务参数非法（例如数据库侧约束、MyBatis 映射问题）
 * 若直接抛出会变成 HTTP 500，前端拿到的是 NestedServletException 而不是可读提示。
 * 因此本类的<b>所有</b>接口统一把 {@link JeecgBootException} 转成
 * {@code Result.error(message)}，未预期异常也转成一句可读的提示并记 error 日志。
 *
 * <p><b>扫描件不在本模块上传</b>：扫描件本体统一走档案管理模块
 * （{@code POST /sys/common/upload} + {@code /land/archive/**}），
 * 本模块只通过 {@code linkArchive} 记一个指针。
 */
@Slf4j
@Api(tags = "档案管理-竣工验收项目历史工程资料数字化档案")
@RestController
@RequestMapping("/land/archive/completion")
public class CompletionArchiveController {

    /** 权限码：查询（列表 / 详情 / 统计 / 状态计数 / 关联扫描件列表） */
    private static final String PERM_LIST = "land:completion:list";
    /** 权限码：新增（含编号预览与校验） */
    private static final String PERM_ADD = "land:completion:add";
    /** 权限码：编辑（含状态变更、挑档案、关联与取消关联） */
    private static final String PERM_EDIT = "land:completion:edit";
    /** 权限码：删除（含批量） */
    private static final String PERM_DELETE = "land:completion:delete";
    /** 权限码：导出 */
    private static final String PERM_EXPORT = "land:completion:export";

    @Autowired
    private ICompletionArchiveService completionService;

    @Autowired
    private ICompletionExportService exportService;

    // ==================================================================
    // 一、查询
    // ==================================================================

    @AutoLog(value = "竣工验收历史档案-分页列表查询")
    @ApiOperation(value = "竣工验收历史档案-分页列表查询",
            notes = "与统计、导出共用同一套查询条件；分页上限 500；每条带 seq 序号与 digitizePercent 数字化进度")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/list")
    public Result<IPage<CompletionArchive>> list(CompletionQueryDTO query) {
        try {
            return Result.OK(completionService.queryPage(query));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询竣工验收历史档案列表失败", e);
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-通过id查询")
    @ApiOperation(value = "竣工验收历史档案-通过id查询",
            notes = "返回档案 + 数字化进度 + 关联扫描件列表（relatedArchives）")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/queryById")
    public Result<CompletionArchive> queryById(@RequestParam(name = "id", required = true) String id) {
        try {
            CompletionArchive archive = completionService.queryDetail(id);
            if (archive == null) {
                return Result.error("未找到对应的历史档案记录");
            }
            return Result.OK(archive);
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询竣工验收历史档案详情失败", e);
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-统计")
    @ApiOperation(value = "竣工验收历史档案-统计",
            notes = "汇总指标（总数/已挂扫描件/已数字化/总页数/文件数/投资额合计）+ 按数字化状态/行政区/项目类型/保管期限/档案年度/竣工年度 共 6 组分类统计")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/stat")
    public Result<CompletionStatVO> stat(CompletionQueryDTO query) {
        try {
            return Result.OK(completionService.queryStat(query));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("统计竣工验收历史档案失败", e);
            return Result.error("统计失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-各数字化状态计数")
    @ApiOperation(value = "竣工验收历史档案-各数字化状态计数",
            notes = "固定返回未数字化/数字化中/已数字化 3 项，数量为 0 也返回，前端 tab 角标直接用")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/countByStatus")
    public Result<List<CompletionStatVO.StatusCount>> countByStatus(CompletionQueryDTO query) {
        try {
            return Result.OK(completionService.countByStatus(query));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("统计竣工验收历史档案数字化状态失败", e);
            return Result.error("统计失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 二、增删改
    // ==================================================================

    @AutoLog(value = "竣工验收历史档案-新增")
    @ApiOperation(value = "竣工验收历史档案-新增",
            notes = "档案编号可留空自动生成；digitizeStatus 只接受未数字化/数字化中/已数字化")
    @RequiresPermissions(PERM_ADD)
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody CompletionSaveDTO dto) {
        try {
            String id = completionService.createArchive(dto);
            return Result.OK("新增成功！", id);
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("新增竣工验收历史档案失败", e);
            return Result.error("新增失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-编辑")
    @ApiOperation(value = "竣工验收历史档案-编辑",
            notes = "档案编号允许手工改写；字段允许清空（显式 SQL 覆盖，不受 MyBatis-Plus NOT_NULL 策略限制）")
    @RequiresPermissions(PERM_EDIT)
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody CompletionSaveDTO dto) {
        try {
            completionService.updateArchive(dto);
            return Result.OK("修改成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("编辑竣工验收历史档案失败", e);
            return Result.error("修改失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-变更数字化状态")
    @ApiOperation(value = "竣工验收历史档案-变更数字化状态",
            notes = "未数字化/数字化中/已数字化（无流转顺序校验）")
    @RequiresPermissions(PERM_EDIT)
    @PostMapping(value = "/status")
    public Result<?> status(@RequestParam(name = "id") String id,
                            @RequestParam(name = "status") String status) {
        try {
            completionService.changeDigitizeStatus(id, status);
            return Result.OK("状态变更成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("变更竣工验收历史档案数字化状态失败", e);
            return Result.error("状态变更失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-删除")
    @ApiOperation(value = "竣工验收历史档案-删除",
            notes = "逻辑删除；不动档案（扫描件归档案模块）、不动宗地与配套项目")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
        try {
            completionService.deleteArchive(id);
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除竣工验收历史档案失败", e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-批量删除")
    @ApiOperation(value = "竣工验收历史档案-批量删除")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        try {
            List<String> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            completionService.deleteArchives(idList);
            return Result.OK("批量删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("批量删除竣工验收历史档案失败", e);
            return Result.error("批量删除失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 三、档案编号
    // ==================================================================

    @AutoLog(value = "竣工验收历史档案-生成档案编号")
    @ApiOperation(value = "竣工验收历史档案-生成档案编号", notes = "仅预览，不落库；格式 JG-{yyyy}-{4位流水}")
    @RequiresPermissions(PERM_ADD)
    @GetMapping(value = "/generateNo")
    public Result<String> generateNo(@RequestParam(name = "year", required = false) Integer year) {
        try {
            return Result.OK(completionService.generateArchiveNo(year));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("生成档案编号失败", e);
            return Result.error("生成失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-档案编号唯一校验")
    @ApiOperation(value = "竣工验收历史档案-档案编号唯一校验", notes = "编辑时传 id 排除自身")
    @RequiresPermissions(PERM_ADD)
    @GetMapping(value = "/checkNo")
    public Result<?> checkNo(@RequestParam(name = "archiveNo", required = true) String archiveNo,
                             @RequestParam(name = "id", required = false) String id) {
        try {
            completionService.checkArchiveNoUnique(archiveNo, id);
            return Result.OK("档案编号可用");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("档案编号唯一校验失败", e);
            return Result.error("校验失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 四、关联扫描件
    // ==================================================================

    @AutoLog(value = "竣工验收历史档案-关联扫描件列表")
    @ApiOperation(value = "竣工验收历史档案-关联扫描件列表",
            notes = "按配套项目ID优先、出让宗地编号兜底反查 t_archive（只读，不改档案数据）")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/relatedArchives")
    public Result<List<RelatedArchiveVO>> relatedArchives(@RequestParam(name = "id") String id,
                                                          @RequestParam(name = "limit", required = false) Integer limit) {
        try {
            return Result.OK(completionService.queryRelatedArchives(id, limit));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询历史档案关联扫描件失败", e);
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-挑档案")
    @ApiOperation(value = "竣工验收历史档案-挑档案", notes = "按配套项目/宗地过滤，或按关键词全局搜（只读）")
    @RequiresPermissions(PERM_EDIT)
    @GetMapping(value = "/archive/pick")
    public Result<List<RelatedArchiveVO>> pickArchive(@RequestParam(name = "facilityId", required = false) String facilityId,
                                                      @RequestParam(name = "crzdbh", required = false) String crzdbh,
                                                      @RequestParam(name = "keyword", required = false) String keyword,
                                                      @RequestParam(name = "limit", required = false) Integer limit) {
        try {
            return Result.OK(completionService.queryArchivesForPick(facilityId, crzdbh, keyword, limit));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("挑档案失败", e);
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-关联扫描件")
    @ApiOperation(value = "竣工验收历史档案-关联扫描件",
            notes = "写本模块自己的 archive_id 与 archive_count，不修改档案表")
    @RequiresPermissions(PERM_EDIT)
    @PostMapping(value = "/linkArchive")
    public Result<?> linkArchive(@RequestParam(name = "id") String id,
                                 @RequestParam(name = "archiveId") String archiveId) {
        try {
            completionService.linkArchive(id, archiveId);
            return Result.OK("关联成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("历史档案关联扫描件失败", e);
            return Result.error("关联失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "竣工验收历史档案-取消关联扫描件")
    @ApiOperation(value = "竣工验收历史档案-取消关联扫描件", notes = "只清本模块的 archive_id 指针，不删除档案本身")
    @RequiresPermissions(PERM_EDIT)
    @PostMapping(value = "/unlinkArchive")
    public Result<?> unlinkArchive(@RequestParam(name = "id") String id) {
        try {
            completionService.unlinkArchive(id);
            return Result.OK("已取消关联！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("历史档案取消关联扫描件失败", e);
            return Result.error("取消关联失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 五、导出（需求原文：可快速导出档案信息）
    // ==================================================================

    /**
     * 导出档案信息。
     *
     * <p>与档案列表<b>共用同一套查询条件</b>，所以导出的行就是屏幕上的行；
     * 文件里的「序号」列由 Service 侧从 1 连续编号。
     *
     * <p>实现方式照抄档案模块：easypoi 生成 .xlsx 工作簿再写响应流。
     * 必须显式 {@code ExcelType.XSSF}（在 {@code CompletionExportServiceImpl} 里设置），
     * 否则写出的是 .xls 内容配 .xlsx 文件名，Excel 会报「文件格式或扩展名无效」。
     */
    @AutoLog(value = "竣工验收历史档案-导出Excel")
    @ApiOperation(value = "竣工验收历史档案-导出Excel",
            notes = "文件名：竣工验收项目历史工程资料数字化档案_{yyyyMMddHHmm}.xlsx；30 列 = 全部查询条件列 + 全部结果列")
    @RequiresPermissions(PERM_EXPORT)
    @GetMapping(value = "/exportXls")
    public void exportXls(CompletionQueryDTO query, HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = exportService.exportArchive(query);
            String fileName = "竣工验收项目历史工程资料数字化档案_"
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
            log.error("导出竣工验收历史档案失败", e);
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

    /** 已提交响应时不能再写错误信息，避免 IllegalStateException 掩盖真实异常 */
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
