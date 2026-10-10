package org.jeecg.modules.land.data.attachment.service;

import org.jeecg.modules.land.data.attachment.entity.LandAttachmentDir;
import org.jeecg.modules.land.data.attachment.vo.AttachmentTreeNodeVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @Description: 附件目录服务（目录维护 + 附件树）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-10
 * @Version V1.0
 *
 * <p><b>设计要点</b>：
 * <ol>
 *   <li><b>目录是独立实体</b>，所以空目录能存在（这是与「只靠路径推导」的关键差别）；</li>
 *   <li><b>上传文件夹会自动补建父目录</b>（{@link #ensureDirs}）：
 *       用户选一个 {@code 招标文件/2024/合同} 的文件夹，不该要求他先手工建三级目录；</li>
 *   <li><b>删除目录不删文件</b>：目录下的文件被移到父目录。
 *       理由：用户说「删掉这个目录」时想清理的是**分类**，不是文件。
 *       若连带删文件，误操作代价是数据丢失，而附件是业务凭证。</li>
 *   <li><b>重命名目录只改目录表</b>，同时把附件与子目录的路径前缀一起改
 *       （dir_path 是冗余字符串，这是它的代价）。</li>
 * </ol>
 *
 * <p><b>★ 路径规范</b>：一律使用「相对业务对象的路径」，例如 {@code 招标文件/2024}；
 * 根目录是空字符串。所有入口都会先过 {@code normalizePath}：
 * 去掉首尾斜杠、折叠重复斜杠、拒绝 {@code ..} 与非法字符、
 * 限制单段长度与总深度 —— 这些值最终会被拼进 SQL 的 LIKE 与前端展示，
 * 不归一的话「招标文件 / 招标文件/ / 招标文件//」会被当成三个目录。
 */
public interface ILandAttachmentDirService {

    /**
     * 目录树（含文件）。
     *
     * @param bizType 业务类型 land / facility / process
     * @param bizId   业务对象 id
     * @param withFile 是否把文件节点挂到目录上（false = 只要目录骨架）
     * @return 根节点列表（即第一层目录；根目录本身不作为一个节点返回，
     *         它的文件在 {@code rootFiles} 里 —— 见 {@link AttachmentTreeVO}）
     */
    AttachmentTreeVO tree(String bizType, String bizId, boolean withFile);

    /**
     * 新建目录。幂等：已存在则直接返回现有目录，不报错。
     *
     * @param dirPath 相对路径（可多级，例如 招标文件/2024）
     */
    LandAttachmentDir createDir(String bizType, String bizId, String dirPath, String bizKey);

    /**
     * 重命名 / 移动目录。
     *
     * <p>★ 会连带更新：① 子目录的 {@code dirPath/parentPath}；
     * ② 附件表里以该路径为前缀的 {@code dir_path}。
     */
    LandAttachmentDir renameDir(String bizType, String bizId, String oldPath, String newName,
                                String bizKey, HttpServletRequest request);

    /**
     * 删除目录（**不删文件**）：目录下的文件移到父目录，子目录一并删除。
     *
     * @param recursive true = 连同子目录一起删；false = 有子目录时拒绝（提示用户）
     */
    int removeDir(String bizType, String bizId, String dirPath, boolean recursive,
                  HttpServletRequest request);

    /**
     * 确保一串目录存在（上传文件夹 / 保存附件时调用）。
     *
     * <p>会逐级补建：传入 {@code a/b/c} 时，{@code a}、{@code a/b}、{@code a/b/c}
     * 缺失的都会被建出来。已存在的层级跳过。
     *
     * @return 实际新建的目录数（0 = 全都已存在）
     */
    int ensureDirs(String bizType, String bizId, String dirPath, String bizKey);

    /** 某业务对象下的目录（含空目录），按路径升序 */
    List<LandAttachmentDir> listDirs(String bizType, String bizId);

    /** 路径规范化（公开给 Controller / 保存附件复用） */
    String normalizePath(String dirPath);

    /**
     * 校验并返回规范的目录路径。
     *
     * <p>与 {@link #normalizePath} 的区别：这里对**非法**输入抛业务异常，
     * 用于「用户明确指定了一个目录」的入口（新建 / 重命名）；
     * 上传路径那种「前端自动带出来」的场景用 normalizePath 静默归一即可。
     */
    String requireValidPath(String dirPath);

    /**
     * 附件树：根文件 + 第一层目录。
     *
     * <p>单独立一个类而不是复用 {@code AttachmentTreeNodeVO}：
     * 根目录不是一个真实目录（目录表里不存空路径），但它**一定有文件**，
     * 所以「根的文件列表」需要在树外面单独放一份。
     */
    @lombok.Data
    @lombok.experimental.Accessors(chain = true)
    class AttachmentTreeVO implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        /** 目录节点（第一层及以下） */
        private List<AttachmentTreeNodeVO> nodes = new java.util.ArrayList<>();

        /** 直接放在根目录（dir_path = ''）的文件 */
        private List<org.jeecg.modules.land.data.attachment.entity.LandAttachment> rootFiles
                = new java.util.ArrayList<>();

        /** 全部文件数（含根与各级目录，便于界面显示总数） */
        private Integer totalFiles = 0;

        /** 全部文件总字节数 */
        private Long totalSize = 0L;

        /** 目录总数（不含根） */
        private Integer totalDirs = 0;
    }
}
