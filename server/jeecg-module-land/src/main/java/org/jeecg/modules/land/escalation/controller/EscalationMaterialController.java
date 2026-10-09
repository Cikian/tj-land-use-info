package org.jeecg.modules.land.escalation.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.escalation.entity.EscalationMaterial;
import org.jeecg.modules.land.escalation.service.IEscalationMaterialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
import java.util.List;

/**
 * @Description: 提级论证材料（方案 2.3.3 第 1 项：论证材料归集；痛点核心）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/escalation/material}。
 *
 * <p><b>权限码</b>：列表归 {@code land:escalation:list}；
 * 上传 / 删除归 {@code land:escalation:edit}；下载 / 预览归 {@code land:escalation:download}
 * （设计文档 2.5 的归类，没有独立的 material 权限码）。
 *
 * <p>接口清单（本类 5 个）：
 * <pre>
 *   GET    /land/escalation/material/list      某项目的材料列表
 *   POST   /land/escalation/material/save      保存材料（带 id 为改，不带为新增）
 *   DELETE /land/escalation/material/delete    删除材料
 *   GET    /land/escalation/material/download  下载（attachment）
 *   GET    /land/escalation/material/preview   预览（inline）
 * </pre>
 *
 * <p><b>文件本体不经过本类</b>：上传复用 jeecg 通用接口 {@code POST /sys/common/upload}，
 * 落盘在 {@code jeecg.path.upload}；本类只负责「保存元数据 + 校验 + 安全地取回文件」。
 *
 * <p><b>安全约定</b>：下载 / 预览<b>只接受 id</b>，磁盘路径由服务端查库 + 前缀校验得到，
 * 绝不接受前端传入的路径（旧系统的 {@code /pdf/render?filePath=} 就是因此存在任意文件读取漏洞）。
 */
@Slf4j
@Api(tags = "提级论证-材料上传/下载")
@RestController
@RequestMapping("/land/escalation/material")
public class EscalationMaterialController {

    /** 权限码：查询（材料列表） */
    private static final String PERM_LIST = "land:escalation:list";
    /** 权限码：编辑（材料保存 / 删除） */
    private static final String PERM_EDIT = "land:escalation:edit";
    /** 权限码：下载（下载 / 预览） */
    private static final String PERM_DOWNLOAD = "land:escalation:download";

    @Autowired
    private IEscalationMaterialService materialService;

    // ==================================================================
    // 列表 / 保存 / 删除
    // ==================================================================

    @AutoLog(value = "提级论证材料-列表")
    @ApiOperation(value = "提级论证材料-列表", notes = "按项目ID取材料，已补齐 url 与可读大小")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/list")
    public Result<List<EscalationMaterial>> list(@RequestParam(name = "projectId", required = true) String projectId) {
        return Result.OK(materialService.queryByProjectId(projectId));
    }

    /**
     * 保存材料。
     *
     * <p>带 id = 改（只允许改材料类型 / 版本 / 补充标记）；不带 id = 新增（此时 {@code projectId}
     * 必填，且会强制校验扩展名白名单与 50MB 上限）。
     *
     * <p>返回落库后的实体，让前端拿到服务端生成的 id 与版本号，不必再查一次列表。
     */
    @AutoLog(value = "提级论证材料-保存")
    @ApiOperation(value = "提级论证材料-保存",
            notes = "带 id 为编辑；不带 id 为新增（projectId 必填）。扩展名白名单 pdf/doc/docx/jpg/jpeg/png/xls/xlsx，单文件 ≤50MB")
    @RequiresPermissions(PERM_EDIT)
    @PostMapping(value = "/save")
    public Result<?> save(@RequestBody EscalationMaterial material) {
        if (material == null) {
            return Result.error("缺少材料数据");
        }
        try {
            if (material.getId() != null && !material.getId().trim().isEmpty()) {
                materialService.updateMaterial(material);
                return Result.OK("修改成功！", materialService.queryMaterialById(material.getId()));
            }
            String projectId = material.getProjectId();
            EscalationMaterial saved = materialService.addMaterial(projectId, material);
            return Result.OK("保存成功！", saved);
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("保存提级论证材料失败", e);
            return Result.error("保存失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "提级论证材料-删除")
    @ApiOperation(value = "提级论证材料-删除", notes = "逻辑删除，并重算主表 material_count")
    @RequiresPermissions(PERM_EDIT)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
        try {
            materialService.deleteMaterial(id);
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除提级论证材料失败：id={}", id, e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 下载 / 预览
    // ==================================================================

    @AutoLog(value = "提级论证材料-下载")
    @ApiOperation(value = "提级论证材料-下载", notes = "仅接受 id，服务端自行解析磁盘路径")
    @RequiresPermissions(PERM_DOWNLOAD)
    @GetMapping(value = "/download")
    public void download(@RequestParam(name = "id", required = true) String id,
                         HttpServletResponse response) {
        writeFile(id, response, true);
    }

    @AutoLog(value = "提级论证材料-预览")
    @ApiOperation(value = "提级论证材料-预览", notes = "inline 方式回源，pdf/图片可直接在浏览器打开")
    @RequiresPermissions(PERM_DOWNLOAD)
    @GetMapping(value = "/preview")
    public void preview(@RequestParam(name = "id", required = true) String id,
                        HttpServletResponse response) {
        writeFile(id, response, false);
    }

    /**
     * 取回文件并写出响应（下载与预览共用的实现）。
     *
     * @param attachment true = attachment（下载）；false = inline（预览）
     */
    private void writeFile(String id, HttpServletResponse response, boolean attachment) {
        try {
            EscalationMaterial material = materialService.queryMaterialById(id);
            if (material == null) {
                writeJsonError(response, "材料不存在或已被删除");
                return;
            }
            String absolutePath = materialService.resolveAbsolutePath(material);
            File source = absolutePath == null ? null : new File(absolutePath);
            if (source == null || !source.isFile()) {
                writeJsonError(response, "文件在服务器上不存在，可能已被清理");
                return;
            }
            String fileName = material.getFileName() == null ? source.getName() : material.getFileName();
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");

            response.reset();
            response.setContentType(attachment
                    ? "application/octet-stream" : contentTypeOf(material, source));
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    (attachment ? "attachment" : "inline")
                            + "; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
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
        } catch (Exception e) {
            log.error(String.format("读取提级论证材料失败：id=%s, attachment=%s", id, attachment), e);
            writeJsonError(response, "操作失败：" + e.getMessage());
        }
    }

    /**
     * 预览用的 Content-Type。
     *
     * <p>优先按库里记录的扩展名判断，取不到再退回文件名后缀，最后兜底
     * {@code application/octet-stream}（浏览器会转为下载，不会白屏）。
     */
    private static String contentTypeOf(EscalationMaterial material, File source) {
        String ext = material.getFileExt();
        if (ext == null || ext.trim().isEmpty()) {
            String name = material.getFileName() == null ? source.getName() : material.getFileName();
            int dot = name.lastIndexOf('.');
            ext = dot < 0 ? null : name.substring(dot + 1);
        }
        if (ext == null) {
            return "application/octet-stream";
        }
        switch (ext.trim().toLowerCase()) {
            case "pdf":
                return "application/pdf";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default:
                return "application/octet-stream";
        }
    }

    /** 已提交响应时不再写错误信息 */
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
