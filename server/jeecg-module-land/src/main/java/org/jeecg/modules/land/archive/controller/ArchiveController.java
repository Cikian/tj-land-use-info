package org.jeecg.modules.land.archive.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.apache.shiro.SecurityUtils;
import org.jeecg.modules.land.archive.dto.ArchiveQueryDTO;
import org.jeecg.modules.land.archive.document.entity.DocReceive;
import org.jeecg.modules.land.archive.document.entity.DocSend;
import org.jeecg.modules.land.archive.document.service.IDocReceiveService;
import org.jeecg.modules.land.archive.document.service.IDocSendService;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.entity.ArchiveFile;
import org.jeecg.modules.land.archive.entity.ArchiveLog;
import org.jeecg.modules.land.archive.service.IArchiveFileService;
import org.jeecg.modules.land.archive.service.IArchiveLogService;
import org.jeecg.modules.land.archive.service.IArchiveService;
import org.jeecg.modules.land.archive.vo.ArchiveStatVO;
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
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description: 档案管理（方案 2.3.2 第 2/3/4/5 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/archive}。
 *
 * <p><b>权限码</b>（全部登记为 sys_permission 里 menu_type=2 的「按钮权限」，
 * 授权脚本见 {@code sql/archive/06_archive_permission_buttons.sql}）：
 * <pre>
 *   land:archive:list      列表 / 详情 / 卷内文件 / 操作记录 / 生成档案号 / 档案号校验
 *   land:archive:add       新增
 *   land:archive:edit      编辑
 *   land:archive:delete    删除 / 批量删除 / 删除单个文件
 *   land:archive:status    变更状态
 *   land:archive:download  下载单个卷内文件
 *   land:archive:export    按条件导出 ZIP
 *   land:archive:stat      统计
 * </pre>
 * ★ 为什么必须建成 menu_type=2 的按钮权限而不是写在菜单行上：见该脚本头部的说明
 *   （jeecg 前端 v-has 与菜单管理的按钮授权只认 menu_type=2 的行）。
 *
 * <p>接口清单：
 * <pre>
 *   GET    /land/archive/list              分页列表（档案维护 / 档案查询共用）
 *   GET    /land/archive/queryById         详情（含卷内文件）
 *   POST   /land/archive/add               新增（含卷内文件）
 *   PUT    /land/archive/edit              编辑（含卷内文件增删改同步）
 *   DELETE /land/archive/delete            删除
 *   DELETE /land/archive/deleteBatch       批量删除
 *   POST   /land/archive/status            变更状态（置为已归档时会校验必须有文件）
 *   GET    /land/archive/generateNo        生成档案号（预览）
 *   GET    /land/archive/checkNo           档案号唯一校验
 *   GET    /land/archive/stat              统计（总览/类别/年度/状态/密级/行政区/按项目）
 *   GET    /land/archive/export            按当前条件导出 ZIP
 *   GET    /land/archive/logs              操作记录
 *   GET    /land/archive/file/list         某档案的卷内文件
 *   GET    /land/archive/file/download     下载单个文件（写「下载」操作记录）
 *   DELETE /land/archive/file/delete       删除单个文件
 *   GET    /land/archive/relatedDocuments  该项目的收发文情况（详情页用）
 * </pre>
 */
@Slf4j
@Api(tags = "档案管理-档案维护/查询/统计/导出")
@RestController
@RequestMapping("/land/archive")
public class ArchiveController {

    /** 权限码：档案查询（列表 / 详情 / 卷内文件 / 操作记录 / 生成档案号 / 档案号校验） */
    private static final String PERM_LIST = "land:archive:list";
    /** 权限码：档案新增 */
    private static final String PERM_ADD = "land:archive:add";
    /** 权限码：档案编辑 */
    private static final String PERM_EDIT = "land:archive:edit";
    /** 权限码：档案删除（含批量、删单个文件） */
    private static final String PERM_DELETE = "land:archive:delete";
    /** 权限码：档案状态变更 */
    private static final String PERM_STATUS = "land:archive:status";
    /** 权限码：卷内文件下载 */
    private static final String PERM_DOWNLOAD = "land:archive:download";
    /** 权限码：导出 ZIP */
    private static final String PERM_EXPORT = "land:archive:export";
    /** 权限码：统计 */
    private static final String PERM_STAT = "land:archive:stat";

    /** 权限码：收文查询（relatedDocuments 需要读收文） */
    private static final String PERM_DOC_RECEIVE = "land:docReceive:list";
    /** 权限码：发文查询（relatedDocuments 需要读发文） */
    private static final String PERM_DOC_SEND = "land:docSend:list";

    @Autowired
    private IArchiveService archiveService;

    @Autowired
    private IArchiveFileService archiveFileService;

    @Autowired
    private IArchiveLogService archiveLogService;

    /**
     * 收发文服务在这里直接注入。
     *
     * <p>为什么放在 Controller 而不是 Service：Spring Boot 2.6 默认禁止循环依赖，
     * DocReceiveServiceImpl 需要 IArchiveService（办结归档），
     * 若 ArchiveServiceImpl 又注入收发文服务就会形成环。
     * 「档案详情展示该项目的收发文」本质上是<b>组合展示</b>，放在 Controller 层最自然。
     */
    @Autowired
    private IDocReceiveService docReceiveService;

    @Autowired
    private IDocSendService docSendService;

    // ==================================================================
    // 查询
    // ==================================================================

    @AutoLog(value = "档案-分页列表查询")
    @ApiOperation(value = "档案-分页列表查询", notes = "档案维护与档案查询共用同一套条件")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/list")
    public Result<IPage<Archive>> list(ArchiveQueryDTO query) {
        return Result.OK(archiveService.queryPage(query));
    }

    @AutoLog(value = "档案-通过id查询")
    @ApiOperation(value = "档案-通过id查询", notes = "返回档案主信息 + 卷内文件列表")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/queryById")
    public Result<Archive> queryById(@RequestParam(name = "id", required = true) String id) {
        Archive archive = archiveService.queryDetail(id);
        if (archive == null) {
            return Result.error("未找到对应的档案");
        }
        return Result.OK(archive);
    }

    // ==================================================================
    // 增删改
    // ==================================================================

    @AutoLog(value = "档案-新增")
    @ApiOperation(value = "档案-新增", notes = "请求体为档案 JSON，files 为卷内文件（每个文件必须带 categoryId）")
    @RequiresPermissions(PERM_ADD)
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody Archive archive) {
        try {
            String id = archiveService.createArchive(archive, archive.getFiles());
            return Result.OK("新增成功！", id);
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("新增档案失败", e);
            return Result.error("新增失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "档案-编辑")
    @ApiOperation(value = "档案-编辑")
    @RequiresPermissions(PERM_EDIT)
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody Archive archive) {
        try {
            archiveService.updateArchive(archive, archive.getFiles());
            return Result.OK("修改成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("编辑档案失败", e);
            return Result.error("修改失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "档案-删除")
    @ApiOperation(value = "档案-删除", notes = "逻辑删除，级联逻辑删除卷内文件")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
        try {
            archiveService.deleteArchive(id);
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除档案失败", e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "档案-批量删除")
    @ApiOperation(value = "档案-批量删除")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        try {
            List<String> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            archiveService.deleteArchives(idList);
            return Result.OK("批量删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("批量删除档案失败", e);
            return Result.error("批量删除失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "档案-变更状态")
    @ApiOperation(value = "档案-变更状态", notes = "status: 未归档/归档中/审核中/已归档")
    @RequiresPermissions(PERM_STATUS)
    @PostMapping(value = "/status")
    public Result<?> changeStatus(@RequestBody Archive body) {
        if (body == null || body.getId() == null) {
            return Result.error("缺少档案ID");
        }
        try {
            archiveService.changeStatus(body.getId(), body.getStatus());
            return Result.OK("操作成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("变更档案状态失败", e);
            return Result.error("操作失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 档案号
    // ==================================================================

    @AutoLog(value = "档案-生成档案号")
    @ApiOperation(value = "档案-生成档案号", notes = "仅预览，不落库；格式 DA-{yyyy}-{4位流水}")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/generateNo")
    public Result<String> generateNo(@RequestParam(name = "year", required = false) Integer year) {
        return Result.OK(archiveService.generateArchiveNo(year));
    }

    @AutoLog(value = "档案-档案号唯一校验")
    @ApiOperation(value = "档案-档案号唯一校验", notes = "编辑时传 id 排除自身")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/checkNo")
    public Result<?> checkNo(@RequestParam(name = "archiveNo", required = true) String archiveNo,
                             @RequestParam(name = "id", required = false) String id) {
        try {
            archiveService.checkArchiveNoUnique(archiveNo, id);
            return Result.OK("档案号可用");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================================================================
    // 统计 / 导出 / 日志
    // ==================================================================

    @AutoLog(value = "档案-统计")
    @ApiOperation(value = "档案-统计", notes = "总览 + 按类别/年度/状态/密级/行政区/项目")
    @RequiresPermissions(PERM_STAT)
    @GetMapping(value = "/stat")
    public Result<ArchiveStatVO> stat(ArchiveQueryDTO query,
                                      @RequestParam(name = "projectLimit", required = false) Integer projectLimit) {
        return Result.OK(archiveService.queryStat(query, projectLimit));
    }

    @AutoLog(value = "档案-导出ZIP")
    @ApiOperation(value = "档案-导出ZIP", notes = "目录结构：宗地编号/配套项目/档案类别/档案名称/文件名，附 档案清单.xlsx")
    @RequiresPermissions(PERM_EXPORT)
    @GetMapping(value = "/export")
    public void export(ArchiveQueryDTO query, HttpServletResponse response) {
        File zip = null;
        try {
            String operatorName = currentRealname();
            zip = archiveService.exportZip(query, operatorName);
            String fileName = "档案导出_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip";
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");

            response.reset();
            response.setContentType("application/zip");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
            response.setHeader("Content-Length", String.valueOf(zip.length()));

            try (InputStream in = new BufferedInputStream(new FileInputStream(zip));
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
        } catch (Exception e) {
            log.error("导出档案失败", e);
            if (!response.isCommitted()) {
                response.reset();
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"success\":false,\"message\":\"导出失败："
                            + (e.getMessage() == null ? "未知错误" : e.getMessage().replace("\"", "'")) + "\"}");
                } catch (Exception ignored) {
                    log.warn("导出失败后回写错误信息也失败了：{}", ignored.getMessage());
                }
            }
        } finally {
            // 临时 ZIP 必须清理，否则批量导出会把服务器磁盘撑满
            if (zip != null && zip.exists() && !zip.delete()) {
                log.warn("导出临时文件清理失败：{}", zip.getAbsolutePath());
            }
        }
    }

    @AutoLog(value = "档案-操作记录")
    @ApiOperation(value = "档案-操作记录")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/logs")
    public Result<List<ArchiveLog>> logs(@RequestParam(name = "archiveId", required = true) String archiveId) {
        return Result.OK(archiveService.queryLogs(archiveId));
    }

    // ==================================================================
    // 卷内文件
    // ==================================================================

    @AutoLog(value = "档案-卷内文件列表")
    @ApiOperation(value = "档案-卷内文件列表")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/file/list")
    public Result<List<ArchiveFile>> fileList(@RequestParam(name = "archiveId", required = true) String archiveId) {
        return Result.OK(archiveService.queryFiles(archiveId));
    }

    /**
     * 下载单个卷内文件。
     *
     * <p><b>安全约定</b>：只接受 fileId，磁盘路径由服务端查库 + 前缀校验得到，
     * 绝不接受前端传入的路径（旧 tj-sfw 的 {@code /pdf/render?filePath=} 就是因此
     * 存在任意文件读取漏洞）。
     */
    @AutoLog(value = "档案-文件下载")
    @ApiOperation(value = "档案-文件下载", notes = "仅接受 fileId，服务端自行解析磁盘路径")
    @RequiresPermissions(PERM_DOWNLOAD)
    @GetMapping(value = "/file/download")
    public void downloadFile(@RequestParam(name = "id", required = true) String id,
                             HttpServletResponse response) {
        try {
            ArchiveFile file = archiveFileService.queryFileById(id);
            if (file == null) {
                writeJsonError(response, "文件不存在或已被删除");
                return;
            }
            String absolutePath = archiveFileService.resolveAbsolutePath(file);
            File source = absolutePath == null ? null : new File(absolutePath);
            if (source == null || !source.isFile()) {
                writeJsonError(response, "文件在服务器上不存在，可能已被清理");
                return;
            }
            String fileName = file.getFileName() == null ? source.getName() : file.getFileName();
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");

            response.reset();
            response.setContentType("application/octet-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
            response.setHeader("Content-Length", String.valueOf(Files.size(source.toPath())));
            try (InputStream in = new BufferedInputStream(new FileInputStream(source));
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
            archiveLogService.record(file.getArchiveId(), file.getId(), ArchiveLog.ACTION_DOWNLOAD,
                    "下载文件「" + fileName + "」", file.getCategoryName());
        } catch (Exception e) {
            log.error("下载档案文件失败：id={}", id, e);
            writeJsonError(response, "下载失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "档案-删除单个文件")
    @ApiOperation(value = "档案-删除单个文件")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/file/delete")
    public Result<?> deleteFile(@RequestParam(name = "id", required = true) String id) {
        try {
            ArchiveFile file = archiveFileService.queryFileById(id);
            archiveFileService.deleteFile(id);
            if (file != null) {
                archiveLogService.record(file.getArchiveId(), id, ArchiveLog.ACTION_DELETE,
                        "删除文件「" + file.getFileName() + "」", file.getCategoryName());
            }
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除档案文件失败：id={}", id, e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 关联收发文（档案详情页的「收发文情况」块）
    // ==================================================================

    /**
     * 查该档案所属项目的收发文。
     *
     * <p>优先按配套项目（facilityId）关联；没传时退化为按宗地关联。
     * 需求原文：「在档案详情中，有一块需要关联展示该项目的收发文情况」。
     */
    @AutoLog(value = "档案-关联收发文情况")
    @ApiOperation(value = "档案-关联收发文情况", notes = "按配套项目关联；未传项目时按宗地关联")
    @RequiresPermissions(value = {PERM_LIST, PERM_DOC_RECEIVE, PERM_DOC_SEND}, logical = Logical.OR)
    @GetMapping(value = "/relatedDocuments")
    public Result<Map<String, Object>> relatedDocuments(
            @RequestParam(name = "facilityId", required = false) String facilityId,
            @RequestParam(name = "landId", required = false) String landId,
            @RequestParam(name = "crzdbh", required = false) String crzdbh,
            @RequestParam(name = "limit", required = false) Integer limit) {
        List<DocReceive> receives = docReceiveService.queryRelated(facilityId, landId, crzdbh, limit);
        List<DocSend> sends = docSendService.queryRelated(facilityId, landId, crzdbh, limit);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("receives", receives);
        result.put("sends", sends);
        result.put("receiveCount", receives.size());
        result.put("sendCount", sends.size());
        return Result.OK(result);
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    private String currentRealname() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser) {
                LoginUser user = (LoginUser) principal;
                return user.getRealname() == null ? user.getUsername() : user.getRealname();
            }
        } catch (Exception e) {
            log.debug("获取当前登录用户失败：{}", e.getMessage());
        }
        return null;
    }

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
