package org.jeecg.modules.land.data.attachment.controller;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.land.data.attachment.entity.LandAttachmentDir;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentDirService;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentDirService.AttachmentTreeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @Description: 附件目录（目录树 + 目录维护）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-10
 * @Version V1.0
 *
 * <p><b>★ 权限码沿用 {@code land:data:attachment}</b>：
 * 目录是附件分类能力的一部分，用户在「附件管理」这一屏里既管附件也管目录；
 * 拆出独立权限码会让角色配置多一个几乎总是同开同关的开关，
 * 与「一面菜单一个权限码」的既有取舍一致（见 数据管理-实现说明 §3.5）。
 *
 * <p><b>接口一览</b>：
 * <pre>
 *   GET    /land/data/attachmentDir/tree          附件树（目录 + 文件）
 *   GET    /land/data/attachmentDir/list          某业务对象的目录列表（扁平）
 *   POST   /land/data/attachmentDir/add           新建目录（可多级，中间层自动补建）
 *   POST   /land/data/attachmentDir/rename        重命名目录（连带子目录与附件）
 *   DELETE /land/data/attachmentDir/delete        删除目录（文件上移到父目录，不删文件）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/land/data/attachmentDir")
public class LandAttachmentDirController {

    @Autowired
    private ILandAttachmentDirService dirService;

    /**
     * 附件树（目录 + 该目录下的文件）。
     *
     * @param bizType  业务类型 land / facility / process
     * @param bizId    业务对象 id
     * @param withFile 是否带文件节点（默认 true）；只要目录骨架时传 false
     */
    @GetMapping(value = "/tree")
    public Result<AttachmentTreeVO> tree(
            @RequestParam(name = "bizType") String bizType,
            @RequestParam(name = "bizId") String bizId,
            @RequestParam(name = "withFile", required = false, defaultValue = "true") Boolean withFile) {
        return Result.OK(dirService.tree(bizType, bizId, withFile == null || withFile));
    }

    /** 某业务对象的目录列表（扁平，供「上传时选目录」的下拉用） */
    @GetMapping(value = "/list")
    public Result<List<LandAttachmentDir>> list(
            @RequestParam(name = "bizType") String bizType,
            @RequestParam(name = "bizId") String bizId) {
        return Result.OK(dirService.listDirs(bizType, bizId));
    }

    /**
     * 新建目录。可一次建多级（{@code 招标文件/2024}），中间层自动补建。
     * 幂等：已存在则直接返回，不报错。
     */
    @PostMapping(value = "/add")
    public Result<LandAttachmentDir> add(
            @RequestParam(name = "bizType") String bizType,
            @RequestParam(name = "bizId") String bizId,
            @RequestParam(name = "dirPath") String dirPath,
            @RequestParam(name = "bizKey", required = false) String bizKey) {
        return Result.OK("目录已创建", dirService.createDir(bizType, bizId, dirPath, bizKey));
    }

    /** 重命名目录：连带子目录与目录下的附件一起改路径 */
    @PostMapping(value = "/rename")
    public Result<LandAttachmentDir> rename(
            @RequestParam(name = "bizType") String bizType,
            @RequestParam(name = "bizId") String bizId,
            @RequestParam(name = "oldPath") String oldPath,
            @RequestParam(name = "newName") String newName,
            @RequestParam(name = "bizKey", required = false) String bizKey,
            HttpServletRequest request) {
        return Result.OK("目录已重命名",
                dirService.renameDir(bizType, bizId, oldPath, newName, bizKey, request));
    }

    /**
     * 删除目录。
     *
     * <p>★ 不删文件：目录下的文件会移到父目录。
     *
     * @param recursive 有子目录时必须显式传 true，否则拒绝并提示
     */
    @DeleteMapping(value = "/delete")
    public Result<?> delete(
            @RequestParam(name = "bizType") String bizType,
            @RequestParam(name = "bizId") String bizId,
            @RequestParam(name = "dirPath") String dirPath,
            @RequestParam(name = "recursive", required = false, defaultValue = "false") Boolean recursive,
            HttpServletRequest request) {
        int removed = dirService.removeDir(bizType, bizId, dirPath,
                recursive != null && recursive, request);
        return Result.OK("已删除 " + removed + " 个目录（目录下的文件已移到上级目录）", removed);
    }
}
