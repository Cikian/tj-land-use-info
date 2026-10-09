package org.jeecg.modules.land.escalation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.escalation.dto.EscalationQueryDTO;
import org.jeecg.modules.land.escalation.dto.EscalationSaveDTO;
import org.jeecg.modules.land.escalation.entity.EscalationProject;
import org.jeecg.modules.land.escalation.service.IEscalationExportService;
import org.jeecg.modules.land.escalation.service.IEscalationProjectService;
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
 * @Description: 提级论证项目（方案 2.3.3 第 1/2 项：项目录入、查询统计与导出）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/escalation/project}。
 *
 * <p><b>权限码</b>（全部登记为 sys_permission 里 menu_type=2 的「按钮权限」，
 * 授权脚本见 {@code sql/escalation/04_escalation_permission_buttons.sql}；
 * 按钮 id 前缀必须用 {@code ...d5e6bc}，档案 {@code ...d5e6ba}、收发文 {@code ...d5e6bb} 已占用）：
 * <pre>
 *   land:escalation:list      列表 / 详情 / 编号预览 / 编号校验
 *   land:escalation:add       新增
 *   land:escalation:edit      编辑
 *   land:escalation:delete    删除 / 批量删除
 *   land:escalation:export    导出 Excel
 * </pre>
 *
 * <p>接口清单（本类 9 个）：
 * <pre>
 *   GET    /land/escalation/project/list         分页列表（录入列表 / 查询统计共用）
 *   GET    /land/escalation/project/queryById    详情（含材料与意见记录）
 *   POST   /land/escalation/project/add          新增（含材料列表）
 *   PUT    /land/escalation/project/edit         编辑（含材料三向合并）
 *   DELETE /land/escalation/project/delete       删除
 *   DELETE /land/escalation/project/deleteBatch  批量删除
 *   GET    /land/escalation/project/genProjectNo 预生成项目编号（TJ-{yyyy}-{4位}）
 *   GET    /land/escalation/project/checkNo      项目编号唯一校验
 *   GET    /land/escalation/project/exportXls    导出 Excel
 * </pre>
 *
 * <p><b>材料文件上传不在这里</b>：复用 jeecg 通用接口 {@code POST /sys/common/upload}
 * （与档案模块一致），上传成功后把返回的相对路径作为 {@code store_path} 随项目一起提交。
 */
@Slf4j
@Api(tags = "提级论证-项目录入/查询/导出")
@RestController
@RequestMapping("/land/escalation/project")
public class EscalationProjectController {

    /** 权限码：查询（列表 / 详情 / 编号预览 / 编号校验） */
    private static final String PERM_LIST = "land:escalation:list";
    /** 权限码：新增 */
    private static final String PERM_ADD = "land:escalation:add";
    /** 权限码：编辑 */
    private static final String PERM_EDIT = "land:escalation:edit";
    /** 权限码：删除（含批量） */
    private static final String PERM_DELETE = "land:escalation:delete";
    /** 权限码：导出 */
    private static final String PERM_EXPORT = "land:escalation:export";

    @Autowired
    private IEscalationProjectService projectService;

    @Autowired
    private IEscalationExportService exportService;

    // ==================================================================
    // 查询
    // ==================================================================

    @AutoLog(value = "提级论证项目-分页列表查询")
    @ApiOperation(value = "提级论证项目-分页列表查询", notes = "录入列表与查询统计共用同一套条件；分页上限 500")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/list")
    public Result<IPage<EscalationProject>> list(EscalationQueryDTO query) {
        return Result.OK(projectService.queryPage(query));
    }

    @AutoLog(value = "提级论证项目-通过id查询")
    @ApiOperation(value = "提级论证项目-通过id查询", notes = "返回项目主信息 + 材料列表 + 意见记录")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/queryById")
    public Result<EscalationProject> queryById(@RequestParam(name = "id", required = true) String id) {
        EscalationProject project = projectService.queryDetail(id);
        if (project == null) {
            return Result.error("未找到对应的提级论证项目");
        }
        return Result.OK(project);
    }

    // ==================================================================
    // 增删改
    // ==================================================================

    @AutoLog(value = "提级论证项目-新增")
    @ApiOperation(value = "提级论证项目-新增", notes = "请求体为录入向导的 28 个字段 + materials 材料列表")
    @RequiresPermissions(PERM_ADD)
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody EscalationSaveDTO dto) {
        try {
            String id = projectService.createProject(dto);
            return Result.OK("新增成功！", id);
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("新增提级论证项目失败", e);
            return Result.error("新增失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "提级论证项目-编辑")
    @ApiOperation(value = "提级论证项目-编辑", notes = "项目编号允许手工改写；materials 为 null 表示本次不改材料")
    @RequiresPermissions(PERM_EDIT)
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody EscalationSaveDTO dto) {
        try {
            projectService.updateProject(dto);
            return Result.OK("修改成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("编辑提级论证项目失败", e);
            return Result.error("修改失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "提级论证项目-删除")
    @ApiOperation(value = "提级论证项目-删除", notes = "逻辑删除，级联逻辑删除其材料；意见记录保留（append-only）")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
        try {
            projectService.deleteProject(id);
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除提级论证项目失败", e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "提级论证项目-批量删除")
    @ApiOperation(value = "提级论证项目-批量删除")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        try {
            List<String> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            projectService.deleteProjects(idList);
            return Result.OK("批量删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("批量删除提级论证项目失败", e);
            return Result.error("批量删除失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 项目编号
    // ==================================================================

    @AutoLog(value = "提级论证项目-生成项目编号")
    @ApiOperation(value = "提级论证项目-生成项目编号", notes = "仅预览，不落库；格式 TJ-{yyyy}-{4位流水}")
    @RequiresPermissions(PERM_ADD)
    @GetMapping(value = "/genProjectNo")
    public Result<String> genProjectNo(@RequestParam(name = "year", required = false) Integer year) {
        return Result.OK(projectService.generateProjectNo(year));
    }

    @AutoLog(value = "提级论证项目-项目编号唯一校验")
    @ApiOperation(value = "提级论证项目-项目编号唯一校验", notes = "编辑时传 id 排除自身")
    @RequiresPermissions(PERM_ADD)
    @GetMapping(value = "/checkNo")
    public Result<?> checkNo(@RequestParam(name = "projectNo", required = true) String projectNo,
                             @RequestParam(name = "id", required = false) String id) {
        try {
            projectService.checkProjectNoUnique(projectNo, id);
            return Result.OK("项目编号可用");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================================================================
    // 导出
    // ==================================================================

    /**
     * 导出当前查询结果。
     *
     * <p><b>实现方式照抄档案模块</b>：用 easypoi 生成 .xlsx 工作簿再写响应流。
     * 必须显式 {@code ExcelType.XSSF}（在 {@code EscalationExportServiceImpl} 里设置），
     * 否则写出的是 .xls 内容配 .xlsx 文件名，Excel 会报「文件格式或扩展名无效」。
     */
    @AutoLog(value = "提级论证项目-导出Excel")
    @ApiOperation(value = "提级论证项目-导出Excel", notes = "文件名：提级论证项目_{yyyyMMddHHmm}.xlsx")
    @RequiresPermissions(PERM_EXPORT)
    @GetMapping(value = "/exportXls")
    public void exportXls(EscalationQueryDTO query, HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = exportService.exportProject(query);
            String fileName = "提级论证项目_" + new SimpleDateFormat("yyyyMMddHHmm").format(new Date()) + ".xlsx";
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
            log.error("导出提级论证项目失败", e);
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

    // ==================================================================
    // 公共小工具（本模块 5 个 Controller 各自保留一份私有实现，互不依赖）
    // ==================================================================

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
