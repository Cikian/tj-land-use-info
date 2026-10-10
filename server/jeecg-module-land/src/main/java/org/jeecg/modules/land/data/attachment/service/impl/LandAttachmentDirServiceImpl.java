package org.jeecg.modules.land.data.attachment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;
import org.jeecg.modules.land.data.attachment.entity.LandAttachmentDir;
import org.jeecg.modules.land.data.attachment.mapper.LandAttachmentDirMapper;
import org.jeecg.modules.land.data.attachment.mapper.LandAttachmentMapper;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentDirService;
import org.jeecg.modules.land.data.attachment.vo.AttachmentTreeNodeVO;
import org.jeecg.modules.land.data.support.DataSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 附件目录服务实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-10
 * @Version V1.0
 */
@Slf4j
@Service
public class LandAttachmentDirServiceImpl implements ILandAttachmentDirService {

    /** 路径里一律禁止的字符：它们会把路径语义搞乱或让前端展示出错 */
    private static final char[] FORBIDDEN_CHARS = {'\\', ':', '*', '?', '"', '<', '>', '|', '\r', '\n', '\t'};

    /** 单个目录名里允许的最多字符数（防超长路径把界面撑坏） */
    private static final int MAX_DIR_NAME_LENGTH = 60;

    @Autowired
    private LandAttachmentDirMapper dirMapper;

    @Autowired
    private LandAttachmentMapper attachmentMapper;

    @Autowired
    private DataSupport dataSupport;

    // ==================================================================
    // 一、路径规范化与校验
    // ==================================================================

    /**
     * 静默归一：把用户/浏览器给的各种写法收敛成唯一一种。
     *
     * <p>处理：反斜杠转正斜杠、去掉首尾斜杠、折叠重复斜杠、去掉空白段、
     * 去掉首尾空格、超长截断。**不抛异常** —— 用于「上传文件夹时前端自动带出来」
     * 这种不该因为一个小瑕疵就整批失败的地方。
     */
    @Override
    public String normalizePath(String dirPath) {
        if (dirPath == null) {
            return LandAttachmentDir.ROOT_PATH;
        }
        String path = dirPath.replace('\\', '/').trim();
        // 折叠重复斜杠 + 去掉首尾斜杠
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return LandAttachmentDir.ROOT_PATH;
        }
        // 逐段清洗：去掉空白段与段首尾空格，禁止的字符直接剔除（静默）
        String[] rawSegments = path.split("/");
        List<String> segments = new ArrayList<>();
        for (String segment : rawSegments) {
            String name = cleanSegmentQuietly(segment);
            if (!name.isEmpty()) {
                segments.add(name);
            }
        }
        if (segments.isEmpty()) {
            return LandAttachmentDir.ROOT_PATH;
        }
        // 超深：只保留前 MAX_DEPTH 层（静默截断，避免整批上传失败）
        if (segments.size() > LandAttachmentDir.MAX_DEPTH) {
            segments = new ArrayList<>(segments.subList(0, LandAttachmentDir.MAX_DEPTH));
        }
        return String.join(LandAttachmentDir.SEPARATOR, segments);
    }

    /**
     * 严格校验：非法就抛业务异常并说明原因。
     * 用于「用户明确指定目录」的入口（新建 / 重命名），这里必须让他知道哪里不合法。
     */
    @Override
    public String requireValidPath(String dirPath) {
        String raw = dirPath == null ? "" : dirPath.replace('\\', '/').trim();
        if (raw.isEmpty() || "/".equals(raw)) {
            throw new JeecgBootException("请填写目录名称");
        }
        // 先看原始串里有没有路径穿越：归一化会把它们消化掉，不能等归一化之后再查
        if (raw.contains("..")) {
            throw new JeecgBootException("目录名称不能包含「..」");
        }
        for (char forbidden : FORBIDDEN_CHARS) {
            if (raw.indexOf(forbidden) >= 0) {
                throw new JeecgBootException("目录名称不能包含字符「" + forbidden + "」");
            }
        }
        // ★ 层级检查必须在 normalizePath **之前**做：
        //   normalizePath 对超过 MAX_DEPTH 的路径会**静默截断**（那是给「上传文件夹」
        //   用的宽松口径），而这里是用户明确指定目录的入口，必须报错而不是悄悄改掉他填的值。
        int rawDepth = 0;
        for (String segment : raw.split("/")) {
            if (!segment.trim().isEmpty()) {
                rawDepth++;
            }
        }
        if (rawDepth > LandAttachmentDir.MAX_DEPTH) {
            throw new JeecgBootException("目录层级最多 " + LandAttachmentDir.MAX_DEPTH
                    + " 级，当前 " + rawDepth + " 级");
        }
        String path = normalizePath(raw);
        if (path.isEmpty()) {
            throw new JeecgBootException("目录名称不合法：" + dirPath);
        }
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.length() > MAX_DIR_NAME_LENGTH) {
                throw new JeecgBootException("单级目录名称最多 " + MAX_DIR_NAME_LENGTH
                        + " 个字符：「" + segment + "」");
            }
        }
        return path;
    }

    /** 单段目录名清洗（静默）：去掉禁止字符、控制字符与首尾空白 */
    private String cleanSegmentQuietly(String segment) {
        if (segment == null) {
            return "";
        }
        String name = segment.trim();
        for (char forbidden : FORBIDDEN_CHARS) {
            name = name.replace(String.valueOf(forbidden), "");
        }
        // 控制字符（含上传时可能带入的各种不可见字符）
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 32 && c != 127) {
                sb.append(c);
            }
        }
        name = sb.toString().trim();
        if (name.length() > MAX_DIR_NAME_LENGTH) {
            name = name.substring(0, MAX_DIR_NAME_LENGTH);
        }
        return name;
    }

    /** 路径的父路径；第一层的父是空串 */
    private String parentOf(String dirPath) {
        int index = dirPath.lastIndexOf(LandAttachmentDir.SEPARATOR);
        return index < 0 ? LandAttachmentDir.ROOT_PATH : dirPath.substring(0, index);
    }

    /** 路径的末段名 */
    private String nameOf(String dirPath) {
        int index = dirPath.lastIndexOf(LandAttachmentDir.SEPARATOR);
        return index < 0 ? dirPath : dirPath.substring(index + 1);
    }

    /** 路径的层级：a → 1，a/b → 2 */
    private int depthOf(String dirPath) {
        if (StringUtils.isBlank(dirPath)) {
            return 0;
        }
        return dirPath.split("/").length;
    }

    /**
     * 把 {@code path} 里以 {@code from} 开头的部分替换成 {@code to}。
     *
     * <p>★ 必须防「to 为空串」：{@code "" + "/子目录"} = {@code "/子目录"}，
     * 会得到一个带前导斜杠、任何目录节点都匹配不上的畸形路径
     * （树上看不到该文件、目录删不干净）。根目录是空串而不是没有目录，
     * 所以这里要显式处理，不能直接字符串相加。
     */
    private String rewritePrefix(String path, String from, String to) {
        String suffix = path.substring(from.length());
        if (to == null || to.isEmpty()) {
            return suffix.startsWith(LandAttachmentDir.SEPARATOR)
                    ? suffix.substring(1) : suffix;
        }
        return to + suffix;
    }

    // ==================================================================
    // 二、目录 CRUD
    // ==================================================================

    @Override
    public List<LandAttachmentDir> listDirs(String bizType, String bizId) {
        return dirMapper.selectByBiz(bizType, bizId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LandAttachmentDir createDir(String bizType, String bizId, String dirPath, String bizKey) {
        String path = requireValidPath(dirPath);
        // 逐级补建：允许用户直接建 招标文件/2024，中间层自动补齐
        ensureDirs(bizType, bizId, path, bizKey);
        return dirMapper.selectByPath(bizType, bizId, path);
    }

    /**
     * 确保一串目录存在，逐级补建。
     *
     * <p>★ 为什么要逐级：用户选一个 {@code 招标文件/2024/合同} 的文件夹上传时，
     * 他脑子里只有一个「文件夹」，不该被要求先去建三级目录。
     * 而且中间层目录即使没有文件也要存在 —— 否则前端展开 A 时看不到 A/B。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int ensureDirs(String bizType, String bizId, String dirPath, String bizKey) {
        String path = normalizePath(dirPath);
        if (path.isEmpty()) {
            // 根目录不需要建（目录表不存空路径）
            return 0;
        }
        String[] segments = path.split("/");
        String parent = LandAttachmentDir.ROOT_PATH;
        int created = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            current.setLength(0);
            for (int j = 0; j <= i; j++) {
                if (j > 0) {
                    current.append(LandAttachmentDir.SEPARATOR);
                }
                current.append(segments[j]);
            }
            String currentPath = current.toString();
            if (dirMapper.selectByPath(bizType, bizId, currentPath) == null) {
                LandAttachmentDir dir = new LandAttachmentDir()
                        .setBizType(bizType)
                        .setBizId(bizId)
                        .setBizKey(DataSupport.capLength(bizKey, 100))
                        .setDirPath(currentPath)
                        .setParentPath(parent)
                        .setDirName(segments[i])
                        .setDepth(i + 1)
                        .setCreateBy(currentUsername())
                        .setCreateName(currentRealname())
                        .setCreateTime(new Date())
                        .setDelFlag(0);
                dirMapper.insert(dir);
                created++;
            }
            parent = currentPath;
        }
        if (created > 0) {
            log.debug("附件目录自动补建：bizType={}, bizId={}, path={}, created={}",
                    bizType, bizId, path, created);
        }
        return created;
    }

    /**
     * 重命名 / 移动目录。
     *
     * <p>★ 三处必须一起改，否则树会「断」：
     * ① 目录自身的 dirPath/parentPath/dirName；
     * ② 子目录的路径前缀（它们是靠字符串前缀关联的，不是靠 id）；
     * ③ 附件表里那些 dir_path 以旧路径开头的记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LandAttachmentDir renameDir(String bizType, String bizId, String oldPath, String newName,
                                       String bizKey, javax.servlet.http.HttpServletRequest request) {
        String from = requireValidPath(oldPath);
        String target = requireValidPath(newName);
        // 允许「只改名字」，也允许「带上父级一起改」：统一按「同父级下改末段名」处理
        String newSegment = nameOf(target);
        String newPath = parentOf(from).isEmpty()
                ? newSegment
                : parentOf(from) + LandAttachmentDir.SEPARATOR + newSegment;

        LandAttachmentDir dir = dirMapper.selectByPath(bizType, bizId, from);
        if (dir == null) {
            throw new JeecgBootException("目录不存在或已被删除：" + from);
        }
        if (!from.equals(newPath) && dirMapper.selectByPath(bizType, bizId, newPath) != null) {
            throw new JeecgBootException("同级下已存在目录「" + newSegment + "」");
        }
        if (from.equals(newPath)) {
            return dir;
        }

        // ② 子目录：把路径前缀整体替换
        List<LandAttachmentDir> descendants = dirMapper.selectDescendants(bizType, bizId, from);
        for (LandAttachmentDir child : descendants) {
            String childOld = child.getDirPath();
            String childNew = rewritePrefix(childOld, from, newPath);
            child.setDirPath(childNew);
            child.setParentPath(parentOf(childNew));
            child.setDirName(nameOf(childNew));
            child.setDepth(depthOf(childNew));
            dirMapper.updateById(child);
        }

        // ③ 附件：同样替换前缀
        int movedFiles = rewriteAttachmentPaths(bizType, bizId, from, newPath);

        dir.setDirPath(newPath);
        dir.setParentPath(parentOf(newPath));
        dir.setDirName(newSegment);
        dir.setDepth(depthOf(newPath));
        dirMapper.updateById(dir);

        log.info("附件目录重命名：bizType={}, bizId={}, {} → {}（子目录 {} 个，附件 {} 个）",
                bizType, bizId, from, newPath, descendants.size(), movedFiles);
        return dirMapper.selectByPath(bizType, bizId, newPath);
    }

    /**
     * 把附件表里 {@code dir_path} 以 from 开头（=from 或 from/…）的记录改成 newPrefix。
     *
     * @return 受影响的附件数
     */
    private int rewriteAttachmentPaths(String bizType, String bizId, String from, String to) {
        List<LandAttachment> files = attachmentMapper.selectByBiz(bizType, bizId);
        int changed = 0;
        for (LandAttachment file : files) {
            String path = file.getDirPath() == null ? LandAttachmentDir.ROOT_PATH : file.getDirPath();
            if (!path.equals(from) && !path.startsWith(from + LandAttachmentDir.SEPARATOR)) {
                continue;
            }
            String next = rewritePrefix(path, from, to);
            attachmentMapper.update(null, Wrappers.<LandAttachment>lambdaUpdate()
                    .eq(LandAttachment::getId, file.getId())
                    .set(LandAttachment::getDirPath, next));
            changed++;
        }
        return changed;
    }

    /**
     * 删除目录：**不删文件**，把文件移到父目录；子目录一并删掉。
     *
     * <p>★ 为什么文件不跟着删：用户说「删掉这个目录」时想清理的是**分类**，
     * 不是里面的凭证。连带删文件属于不可逆的数据丢失，
     * 而删除目录是个看起来无害的操作 —— 让它的代价与预期一致。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeDir(String bizType, String bizId, String dirPath, boolean recursive,
                         javax.servlet.http.HttpServletRequest request) {
        String path = requireValidPath(dirPath);
        LandAttachmentDir dir = dirMapper.selectByPath(bizType, bizId, path);
        if (dir == null) {
            throw new JeecgBootException("目录不存在或已被删除：" + path);
        }
        List<LandAttachmentDir> children = dirMapper.selectChildren(bizType, bizId, path);
        if (!children.isEmpty() && !recursive) {
            throw new JeecgBootException("目录「" + dir.getDirName() + "」下还有 "
                    + children.size() + " 个子目录，请确认后勾选「连同子目录一起删除」");
        }
        List<LandAttachmentDir> descendants = dirMapper.selectDescendants(bizType, bizId, path);
        // 文件先移到父目录（含子目录里的文件）
        int movedFiles = rewriteAttachmentPaths(bizType, bizId, path, dir.getParentPath());

        // 物理删除：目录只是个名字，保留软删记录反而会让「再建同名目录」撞唯一键
        for (LandAttachmentDir child : descendants) {
            dirMapper.deleteById(child.getId());
        }
        dirMapper.deleteById(dir.getId());

        log.info("附件目录删除：bizType={}, bizId={}, path={}, 子目录 {} 个，文件 {} 个已上移到 {}",
                bizType, bizId, path, descendants.size(), movedFiles,
                StringUtils.isBlank(dir.getParentPath()) ? "根目录" : dir.getParentPath());
        return descendants.size() + 1;
    }

    // ==================================================================
    // 三、目录树
    // ==================================================================

    /**
     * 构建附件树。
     *
     * <p>算法（单次查询，不用递归 SQL）：
     * <ol>
     *   <li>取该业务对象的全部目录 + 全部附件；</li>
     *   <li>把「附件有、目录表里没有」的路径**补出来**（历史数据：dir_path 有值但目录没登记）；
     *       这样老数据也能正确落进树里，不需要额外的数据修复脚本；</li>
     *   <li>按路径层级建父子关系；</li>
     *   <li>把文件挂到所属目录；根目录的文件单独放 rootFiles；</li>
     *   <li>自底向上累加 fileCount / totalSize（含子目录）。</li>
     * </ol>
     */
    @Override
    public AttachmentTreeVO tree(String bizType, String bizId, boolean withFile) {
        AttachmentTreeVO result = new AttachmentTreeVO();
        List<LandAttachmentDir> dirs = dirMapper.selectByBiz(bizType, bizId);
        List<LandAttachment> files = attachmentMapper.selectByBiz(bizType, bizId);

        // ---- 1. 目录集合（key = 路径） ----
        Map<String, AttachmentTreeNodeVO> nodeByPath = new LinkedHashMap<>();
        for (LandAttachmentDir dir : dirs) {
            String path = normalizePath(dir.getDirPath());
            if (path.isEmpty() || nodeByPath.containsKey(path)) {
                continue;
            }
            nodeByPath.put(path, newDirNode(path, dir.getDirName(), dir.getSortNo()));
        }

        // ---- 2. 目录表里缺失、但附件在用的路径，补出目录节点 ----
        //       路径必须**逐级**补：附件的 dir_path 是 a/b/c 时，a 与 a/b 也要有节点，
        //       否则 a/b/c 挂不到树上（找不到父节点）。
        for (LandAttachment file : files) {
            String path = normalizePath(file.getDirPath());
            if (path.isEmpty()) {
                continue;
            }
            String[] segments = path.split("/");
            StringBuilder acc = new StringBuilder();
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) {
                    acc.append(LandAttachmentDir.SEPARATOR);
                }
                acc.append(segments[i]);
                String currentPath = acc.toString();
                if (!nodeByPath.containsKey(currentPath)) {
                    nodeByPath.put(currentPath, newDirNode(currentPath, segments[i], null));
                }
            }
        }

        // ---- 3. 建父子关系 ----
        List<AttachmentTreeNodeVO> roots = new ArrayList<>();
        for (Map.Entry<String, AttachmentTreeNodeVO> entry : nodeByPath.entrySet()) {
            String path = entry.getKey();
            AttachmentTreeNodeVO node = entry.getValue();
            String parentPath = parentOf(path);
            AttachmentTreeNodeVO parent = parentPath.isEmpty() ? null : nodeByPath.get(parentPath);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }

        // ---- 4. 挂文件 ----
        long totalSize = 0L;
        for (LandAttachment file : files) {
            if (file.getFileSize() != null) {
                totalSize += file.getFileSize();
            }
            String path = normalizePath(file.getDirPath());
            if (path.isEmpty()) {
                result.getRootFiles().add(file);
                continue;
            }
            AttachmentTreeNodeVO node = nodeByPath.get(path);
            if (node == null) {
                // 理论上不会发生（第 2 步已补建），兜底不丢文件
                result.getRootFiles().add(file);
                continue;
            }
            // ★ 先把「本目录直接挂的文件」记在该目录上：
            //   即使 withFile=false（只要目录骨架），fileCount/totalSize 也必须照算 ——
            //   角标要回答「这一支下面有多少东西」，与要不要渲染文件节点无关。
            node.setFileCount((node.getFileCount() == null ? 0 : node.getFileCount()) + 1);
            node.setTotalSize((node.getTotalSize() == null ? 0L : node.getTotalSize())
                    + (file.getFileSize() == null ? 0L : file.getFileSize()));
            if (withFile) {
                AttachmentTreeNodeVO fileNode = new AttachmentTreeNodeVO()
                        .setKey("file:" + file.getId())
                        .setNodeType("file")
                        .setLabel(file.getFileName())
                        .setDirPath(path)
                        .setDepth(depthOf(path) + 1)
                        .setFileCount(1)
                        .setTotalSize(file.getFileSize() == null ? 0L : file.getFileSize())
                        .setEmpty(false)
                        .setFile(file);
                node.getChildren().add(fileNode);
            }
        }

        // ---- 5. 排序 + 自底向上累加 ----
        for (AttachmentTreeNodeVO root : roots) {
            accumulate(root);
        }
        sortNodes(roots);

        result.setNodes(roots);
        result.setTotalFiles(files.size());
        result.setTotalSize(totalSize);
        result.setTotalDirs(nodeByPath.size());
        return result;
    }

    /**
     * 自底向上：子目录累加到自己身上。
     *
     * <p>★ 起手先把节点上**已有的** fileCount/totalSize 存下来 ——
     * 那是「直接挂在本目录下的文件」（第 4 步挂文件时记下的）。
     * 若直接从 0 开始累加子节点，本目录自带的那几个文件就被抹掉了。
     */
    private AttachmentTreeNodeVO accumulate(AttachmentTreeNodeVO node) {
        int count = node.getFileCount() == null ? 0 : node.getFileCount();
        long size = node.getTotalSize() == null ? 0L : node.getTotalSize();
        for (AttachmentTreeNodeVO child : node.getChildren()) {
            if ("file".equals(child.getNodeType())) {
                // 文件节点的计数已在建节点时设置，这里不重复累加（见上）
                continue;
            }
            AttachmentTreeNodeVO done = accumulate(child);
            count += done.getFileCount() == null ? 0 : done.getFileCount();
            size += done.getTotalSize() == null ? 0L : done.getTotalSize();
        }
        node.setFileCount(count);
        node.setTotalSize(size);
        node.setEmpty(count == 0);
        return node;
    }

    /** 子目录在前、文件在后，各自按名称排（中文按拼音序，与界面观感一致） */
    private void sortNodes(List<AttachmentTreeNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator
                .comparing((AttachmentTreeNodeVO n) -> "file".equals(n.getNodeType()) ? 1 : 0)
                .thenComparing(n -> n.getLabel() == null ? "" : n.getLabel(),
                        java.text.Collator.getInstance(java.util.Locale.CHINA)));
        for (AttachmentTreeNodeVO node : nodes) {
            sortNodes(node.getChildren());
        }
    }

    private AttachmentTreeNodeVO newDirNode(String path, String name, Integer sortNo) {
        return new AttachmentTreeNodeVO()
                .setKey("dir:" + path)
                .setNodeType("dir")
                .setLabel(StringUtils.isBlank(name) ? nameOf(path) : name)
                .setDirPath(path)
                .setDepth(depthOf(path))
                .setFileCount(0)
                .setTotalSize(0L)
                .setEmpty(true);
    }

    // ==================================================================
    // 四、当前操作用户（与附件服务同一口径）
    // ==================================================================

    private String currentUsername() {
        try {
            return dataSupport.currentUsername();
        } catch (Exception e) {
            return null;
        }
    }

    private String currentRealname() {
        try {
            return dataSupport.currentRealname();
        } catch (Exception e) {
            return null;
        }
    }
}
