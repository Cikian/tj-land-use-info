package org.jeecg.modules.land.data.attachment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentService;
import org.jeecg.modules.land.data.attachment.vo.AttachmentQueryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 数据管理 · 配套附件管理（上传 / 查询 / 预览 / 下载）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）5.3.5「配套附件管理（上传、查询、预览、下载）」。
 * 旧实现：{@code xjKjkfbCommercialLandController.file() / getFilesCommon() / viewLocalFile()}、
 * {@code xjKjkfbSupportingFacilitiesController.file() / getFilesCommon()}；
 * 旧前端 {@code attachmentList.html / attachmentView.html / attachmentAttrSee.html /
 * attachmentSee.html / attachmentContrast.html}。
 *
 * <p><b>接口一览</b>（统一前缀 {@code /land/data/attachment}）：
 * <pre>
 *   GET    /land/data/attachment/list              分页查询（业务类型/业务主键/类型/文件名/上传人/时间）
 *   GET    /land/data/attachment/byBiz             某个业务对象的附件列表（宗地/配套/环节三处共用）
 *   GET    /land/data/attachment/summary           附件概览（总数 / 总大小 / 类型数）
 *   GET    /land/data/attachment/typeDistribution  按附件类型的分布
 *   GET    /land/data/attachment/allowedTypes      允许的附件类型（码 + 中文名）
 *   POST   /land/data/attachment/save              保存已落盘的附件元数据
 *   GET    /land/data/attachment/download          下载（attachment；含 RFC 5987 文件名）
 *   GET    /land/data/attachment/previewUrl        取相对存储路径（前端据此拼静态预览地址）
 *   DELETE /land/data/attachment/delete            逻辑删除（带变更留痕）
 * </pre>
 *
 * <p><b>★ 上传为什么分两步</b>：文件字节走 jeecg 通用接口
 * {@code POST /sys/common/upload} 落盘（本模块不重复实现上传），
 * 再把返回的路径交给 {@code /save} 落库并做业务校验。
 * 这样「上传」这件事全局只有一套实现（目录规则、大小限制、文件类型限制都在那里），
 * 而「这个文件挂在哪条业务上、路径可不可信」由本模块负责。
 *
 * <p><b>★ 预览为什么返回「相对路径」而不是直接返回一个完整 URL</b>：
 * 静态资源地址由前端按部署环境拼接（jeecg 前端有 {@code staticDomainURL} 配置，
 * 反向代理后域名与后端接口不同），后端写死域名会在换环境时全部失效。
 * 后端只保证「这个相对路径是安全的、拼出来的地址能取到文件」。
 * 另外 jeecg 的 {@code /sys/common/static/**} 是 {@code anon} 放行的
 * （见 {@code ShiroConfig}），因此预览不需要带 token，适合直接塞进
 * {@code img src} 或 PDF 阅读器。
 *
 * <p><b>★ 下载的 token-in-query 兼容</b>：浏览器直接打开下载链接（{@code window.open}
 * 或 {@code <a href>}）时带不上 {@code X-Access-Token} 请求头。
 * jeecg 3.4.3 的 {@code JwtFilter} 对此有兜底：请求头取不到 token 时会读
 * 请求参数 {@code token}（{@code httpServletRequest.getParameter("token")}），
 * 所以前端只要把当前 token 拼进查询串即可下载。
 * 本接口不做任何额外处理 —— 也不接受「把 token 当路径参数」之类的自创写法。
 */
@Slf4j
@Api(tags = "数据管理-配套附件管理")
@RestController
@RequestMapping("/land/data/attachment")
public class LandAttachmentController {

    /** 本模块权限码（附件列表 / 上传 / 下载 / 删除共用一个） */
    private static final String PERM = "land:data:attachment";

    @Autowired
    private ILandAttachmentService attachmentService;

    // ==================================================================
    // 一、查询
    // ==================================================================

    /**
     * 分页查询附件。
     *
     * <p>查询条件由 Spring MVC 直接从查询串绑定到 {@link AttachmentQueryDTO}
     * （不是 {@code @RequestBody}）：GET 请求的参数就该从查询串来，
     * 而且这样前端收藏/分享一个带条件的列表地址就能直接复现。
     */
    @AutoLog(value = "配套附件-分页查询")
    @ApiOperation(value = "配套附件-分页查询",
            notes = "条件：bizType/bizId/bizKey/fileType/keyword(fileName)/uploadBy/beginDate/endDate/pageNo/pageSize")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/list")
    public Result<IPage<LandAttachment>> list(AttachmentQueryDTO query) {
        try {
            return Result.OK(attachmentService.queryPage(query));
        } catch (Exception e) {
            log.error("查询附件列表失败", e);
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /** 某个业务对象的附件列表（宗地详情 / 配套详情 / 环节进度详情三处共用） */
    @AutoLog(value = "配套附件-按业务查询")
    @ApiOperation(value = "配套附件-按业务查询",
            notes = "bizType=land/facility/process，bizId=业务主键；已补齐 url / 可读大小 / 预览方式")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/byBiz")
    public Result<List<LandAttachment>> byBiz(
            @RequestParam(name = "bizType", required = true) String bizType,
            @RequestParam(name = "bizId", required = true) String bizId) {
        return Result.OK(attachmentService.queryByBiz(bizType, bizId));
    }

    /** 附件概览（总数 / 总大小 / 类型数）；bizType 不传 = 全部业务 */
    @AutoLog(value = "配套附件-概览")
    @ApiOperation(value = "配套附件-概览", notes = "num / totalSize / readableSize / typeCount")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/summary")
    public Result<Map<String, Object>> summary(
            @RequestParam(name = "bizType", required = false) String bizType) {
        return Result.OK(attachmentService.summary(bizType));
    }

    /** 按附件类型的分布（数据管理页顶部的「附件概览」用） */
    @AutoLog(value = "配套附件-类型分布")
    @ApiOperation(value = "配套附件-类型分布", notes = "fileType / fileTypeText / num / totalSize / readableSize")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/typeDistribution")
    public Result<List<Map<String, Object>>> typeDistribution(
            @RequestParam(name = "bizType", required = false) String bizType) {
        return Result.OK(attachmentService.typeDistribution(bizType));
    }

    /**
     * 允许的附件类型（材料类型），**按业务类型返回对应那套**。
     *
     * <p>★ bizType 必传（或至少前端要传）：宗地 5 类与配套 13 类是两套清单，
     * 不区分的话前端下拉会混进一半无关选项。
     */
    @AutoLog(value = "配套附件-允许类型")
    @ApiOperation(value = "配套附件-允许类型",
            notes = "按 bizType 返回：land/process → 宗地 5 类；facility → 配套 13 类")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/allowedTypes")
    public Result<List<Map<String, String>>> allowedTypes(
            @RequestParam(name = "bizType", required = false) String bizType) {
        return Result.OK(attachmentService.allowedFileTypes(bizType));
    }

    /**
     * 附件目录树：按<b>材料类型</b>分组。
     *
     * <p>★ 树只有一层（业务对象 → 材料类型 → 文件）：
     * 目录名就是材料类型名，且**空目录不显示** —— 所以树是「该业务对象下
     * 实际有附件的材料类型」这一个派生结果，没有任何需要单独维护的目录数据。
     *
     * <p>★ 用哪一个材料清单由 bizType 决定：land/process 用宗地那套，facility 用配套那套。
     */
    @AutoLog(value = "配套附件-目录树")
    @ApiOperation(value = "配套附件-目录树", notes = "按材料类型分组的附件树；只含有文件的类型")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/tree")
    public Result<org.jeecg.modules.land.data.attachment.vo.AttachmentTreeVO> tree(
            @RequestParam(name = "bizType") String bizType,
            @RequestParam(name = "bizId") String bizId) {
        return Result.OK(attachmentService.tree(bizType, bizId));
    }

    // ==================================================================
    // 二、保存 / 删除
    // ==================================================================

    /**
     * 保存附件元数据（文件本体已由 {@code /sys/common/upload} 落盘）。
     *
     * <p>请求体就是附件实体本身的字段（camelCase），同时承载两类信息：
     * <pre>
     *   归属：bizType（land/facility/process）、bizId、bizKey（可读键）
     *   元数据：fileType（01~13/99）、fileName、fileSize、fileMd5、contentType、
     *           storePath（上传接口返回的相对路径）、remark、sortNo
     * </pre>
     *
     * <p><b>★ 安全约定</b>：请求体里的 {@code id / delFlag / uploadBy / uploadName /
     * uploadTime / downloadCount} <b>一律被忽略</b> —— Service 落库时全部重新赋值。
     * 客户端传什么都不该能决定「这条附件是谁传的、什么时候传的、是不是已删除」。
     *
     * <p>校验不通过时返回业务错误（不写库）：业务类型不合法、业务对象不存在、
     * 存储路径不是安全相对路径、附件类型不在允许集合内，见
     * {@link ILandAttachmentService#saveUploaded}。
     */
    @AutoLog(value = "配套附件-保存")
    @ApiOperation(value = "配套附件-保存",
            notes = "先调 /sys/common/upload 落盘，再用本接口落库；校验业务存在性、路径安全性、类型白名单")
    @RequiresPermissions(PERM)
    @PostMapping(value = "/save")
    public Result<?> save(@RequestBody LandAttachment body) {
        if (body == null) {
            return Result.error("缺少附件数据");
        }
        try {
            LandAttachment saved = attachmentService.saveUploaded(body.getBizType(), body.getBizId(),
                    body.getBizKey(), body.getFileType(), body);
            // 返回补齐展示字段的实体，前端拿到后可以直接渲染（不必再查一次列表）
            return Result.OK("保存成功！", attachmentService.enrich(saved));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("保存附件失败：bizType={}, bizId={}", body.getBizType(), body.getBizId(), e);
            return Result.error("保存失败：" + e.getMessage());
        }
    }

    /** 逻辑删除附件（磁盘文件保留，见 {@link ILandAttachmentService#deleteAttachment}） */
    @AutoLog(value = "配套附件-删除")
    @ApiOperation(value = "配套附件-删除", notes = "逻辑删除 + 变更留痕；不删磁盘文件（同一文件可能被多条业务引用）")
    @RequiresPermissions(PERM)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id,
                            HttpServletRequest request) {
        try {
            attachmentService.deleteAttachment(id, request);
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除附件失败：id={}", id, e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 三、下载 / 预览
    // ==================================================================

    /**
     * 下载附件。
     *
     * <p><b>★ 只接受 id</b>：磁盘路径由服务端查库 + 前缀校验得到，
     * 绝不接受前端传入的路径（旧系统的 {@code /pdf/render?filePath=} 就是因此
     * 存在任意文件读取漏洞）。
     *
     * <p>文件名按 RFC 5987 双写：{@code filename=} 给老浏览器（URL 编码后的 ASCII），
     * {@code filename*=UTF-8''} 给现代浏览器（中文名不会变乱码）。
     */
    @AutoLog(value = "配套附件-下载")
    @ApiOperation(value = "配套附件-下载",
            notes = "仅接受 id；服务端自行解析磁盘路径并做越界校验；浏览器直接打开时可用 ?token=xxx")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/download")
    public void download(@RequestParam(name = "id", required = true) String id,
                         HttpServletResponse response) {
        writeFile(id, response);
    }

    /**
     * 取预览所需的相对存储路径（前端据此拼静态资源地址）。
     *
     * <p>返回 {@code storePath}（相对路径，不含前导斜杠）与
     * {@code previewMode}（image/pdf/office/text/download）、{@code previewable}。
     * ★ 前端不要自己从 {@code fileName} 猜预览方式：扩展名归一化
     * （大写、多个点、无扩展名）与预览方式的对应关系只应该有一份实现，
     * 就是这里的 {@code previewMode}；前端另猜一套，迟早与后端不一致。
     */
    @AutoLog(value = "配套附件-取预览地址")
    @ApiOperation(value = "配套附件-取预览地址",
            notes = "返回相对 storePath 与 previewMode；前端用 staticDomainURL + /sys/common/static/ 拼接")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/previewUrl")
    public Result<Map<String, Object>> previewUrl(@RequestParam(name = "id", required = true) String id) {
        LandAttachment entity = attachmentService.queryById(id);
        if (entity == null) {
            return Result.error("附件不存在或已被删除");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entity.getId());
        // 相对路径：已去掉前导斜杠（多一个斜杠前端拼出来就是 404）
        data.put("storePath", entity.getUrl());
        data.put("fileName", entity.getFileName());
        data.put("fileExt", entity.getFileExt());
        data.put("contentType", entity.getContentType());
        data.put("fileTypeText", entity.getFileTypeText());
        data.put("previewMode", entity.getPreviewMode());
        data.put("previewable", entity.getPreviewable());
        return Result.OK(data);
    }

    // ==================================================================
    // 四、内部工具
    // ==================================================================

    /**
     * 取回文件并写出响应。
     *
     * <p>响应体是二进制流，出错时无法再改 HTTP 状态码（流可能已经写出去了），
     * 因此失败时回写一个 JSON 错误体，让前端至少能提示用户，
     * 而不是让用户拿到一个 0 字节的「下载文件」。
     */
    private void writeFile(String id, HttpServletResponse response) {
        try {
            LandAttachment entity = attachmentService.prepareDownload(id);
            if (entity == null) {
                writeJsonError(response, "附件不存在或已被删除");
                return;
            }
            String absolutePath = attachmentService.resolveAbsolutePath(entity);
            File source = absolutePath == null ? null : new File(absolutePath);
            if (source == null || !source.isFile()) {
                writeJsonError(response, "文件在服务器上不存在，可能已被清理");
                return;
            }
            String fileName = StringUtils.isBlank(entity.getFileName())
                    ? source.getName() : entity.getFileName();
            // RFC 5987：先 URL 编码，并把 "+" 换回 "%20"（URLEncoder 把空格编成 +，
            // 而 + 在文件名里应当被解读为字面加号，会让带空格的文件名显示成加号）
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
        } catch (Exception e) {
            log.error(String.format("读取附件失败：id=%s", id), e);
            writeJsonError(response, "操作失败：" + e.getMessage());
        }
    }

    /** 响应已提交时不再写错误信息（否则会把半个文件 + 一段 JSON 拼在一起） */
    private void writeJsonError(HttpServletResponse response, String message) {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\""
                    + StringUtils.defaultString(message).replace("\"", "'") + "\"}");
            response.getWriter().flush();
        } catch (Exception e) {
            log.warn("回写错误信息失败：{}", e.getMessage());
        }
    }
}
