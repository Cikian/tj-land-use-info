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
import org.jeecg.modules.land.archive.document.dto.DocQueryDTO;
import org.jeecg.modules.land.archive.document.entity.DocSend;
import org.jeecg.modules.land.archive.document.service.IDocSendService;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description: 发文管理（方案 2.3.2 第 9 项：发文的信息记录）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/doc/send}。
 *
 * <p><b>权限码</b>（登记为 sys_permission 里 menu_type=2 的「按钮权限」，
 * 授权脚本见 {@code sql/document/04_document_permission_buttons.sql}）：
 * <pre>
 *   land:docSend:list     列表 / 详情 / 统计 / 生成登记号
 *   land:docSend:add      发文登记
 *   land:docSend:edit     编辑
 *   land:docSend:delete   删除 / 批量删除
 *   land:docSend:archive  归档到档案管理
 * </pre>
 *
 * <p>发文无流转，因此接口比收文少 transfer / reject / finish 三个，
 * 只保留「归档」这个与档案模块的衔接点。
 */
@Slf4j
@Api(tags = "收发文管理-发文")
@RestController
@RequestMapping("/land/doc/send")
public class DocSendController {

    /** 权限码：发文查询（列表 / 详情 / 统计 / 生成登记号） */
    private static final String PERM_LIST = "land:docSend:list";
    /** 权限码：发文登记 */
    private static final String PERM_ADD = "land:docSend:add";
    /** 权限码：发文编辑 */
    private static final String PERM_EDIT = "land:docSend:edit";
    /** 权限码：发文删除 */
    private static final String PERM_DELETE = "land:docSend:delete";
    /** 权限码：发文归档 */
    private static final String PERM_ARCHIVE = "land:docSend:archive";

    @Autowired
    private IDocSendService docSendService;

    // ==================================================================
    // 查询
    // ==================================================================

    @AutoLog(value = "发文-分页列表查询")
    @ApiOperation(value = "发文-分页列表查询")
//    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/list")
    public Result<IPage<DocSend>> list(DocQueryDTO query) {
        return Result.OK(docSendService.queryPage(query));
    }

    @AutoLog(value = "发文-通过id查询")
    @ApiOperation(value = "发文-通过id查询", notes = "含附件")
//    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/queryById")
    public Result<DocSend> queryById(@RequestParam(name = "id", required = true) String id) {
        DocSend doc = docSendService.queryDetail(id);
        if (doc == null) {
            return Result.error("未找到对应的发文");
        }
        return Result.OK(doc);
    }

    /** 列表页顶部的统计条：发文总数 + 已归档数 */
    @AutoLog(value = "发文-统计")
    @ApiOperation(value = "发文-统计")
//    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/stat")
    public Result<Map<String, Object>> stat() {
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("total", docSendService.countAll());
        DocQueryDTO archivedQuery = new DocQueryDTO();
        archivedQuery.setArchived(Boolean.TRUE);
        archivedQuery.setPageNo(1);
        archivedQuery.setPageSize(1);
        result.put("archived", docSendService.queryPage(archivedQuery).getTotal());
        return Result.OK(result);
    }

    // ==================================================================
    // 登记 / 编辑 / 删除
    // ==================================================================

    @AutoLog(value = "发文-生成登记号")
    @ApiOperation(value = "发文-生成登记号", notes = "仅预览；格式 FW-{yyyy}-{4位}")
//    @RequiresPermissions(PERM_ADD)
    @GetMapping(value = "/generateNo")
    public Result<String> generateNo(@RequestParam(name = "year", required = false) Integer year) {
        return Result.OK(docSendService.generateDocNo(year));
    }

    @AutoLog(value = "发文-登记")
    @ApiOperation(value = "发文-登记")
//    @RequiresPermissions(PERM_ADD)
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody DocSend doc) {
        try {
            return Result.OK("登记成功！", docSendService.createDoc(doc));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("发文登记失败", e);
            return Result.error("登记失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "发文-编辑")
    @ApiOperation(value = "发文-编辑")
//    @RequiresPermissions(PERM_EDIT)
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody DocSend doc) {
        try {
            docSendService.updateDoc(doc);
            return Result.OK("修改成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("发文编辑失败", e);
            return Result.error("修改失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "发文-删除")
    @ApiOperation(value = "发文-删除", notes = "逻辑删除，级联附件")
//    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
        try {
            docSendService.deleteDoc(id);
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("发文删除失败", e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "发文-批量删除")
    @ApiOperation(value = "发文-批量删除")
//    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        try {
            docSendService.deleteDocs(Arrays.stream(ids.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
            return Result.OK("批量删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("发文批量删除失败", e);
            return Result.error("批量删除失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 归档
    // ==================================================================

    @AutoLog(value = "发文-归档")
    @ApiOperation(value = "发文-归档", notes = "必须选择档案类别；发文附件会成为该档案的卷内文件")
//    @RequiresPermissions(PERM_ARCHIVE)
    @PostMapping(value = "/archive")
    public Result<String> archive(@RequestBody DocArchiveDTO dto) {
        try {
            return Result.OK("归档成功！", docSendService.archive(dto));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("发文归档失败", e);
            return Result.error("归档失败：" + e.getMessage());
        }
    }
}
