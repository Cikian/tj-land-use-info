package org.jeecg.modules.land.common.util;

import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文件预览方式的唯一判定口径。
 *
 * <p><b>为什么要有这个类</b>：档案卷内文件、收发文附件、数据管理附件、提级论证材料
 * 四个模块都需要回答同一个问题——「这个文件能不能在线预览、用哪种方式打开」。
 * 以前每个 Service 各写一套扩展名白名单，加一种格式要改四处，漏一处就会出现
 * 「同一个 pdf 在附件管理能看、在档案详情说不能看」。
 *
 * <p>约定（前端按 {@code previewMode} 选打开方式，<b>不自己猜扩展名</b>）：
 * <pre>
 *   image     图片：浏览器原生 &lt;img&gt;（本系统统一走 kkFileView 后仅作标签）
 *   pdf       PDF
 *   office    Office / WPS / OFD
 *   text      纯文本
 *   archive   压缩包（zip/rar/7z…）
 *   cad       CAD 图纸（dwg/dxf…）
 *   media     音视频
 *   online    其余格式：同样交 kkFileView，由它决定能否渲染/报错
 * </pre>
 *
 * <p><b>★ 2026-10-22 口径调整</b>：前端不再按 previewMode 分流渲染，**所有文件统一交给
 * kkFileView**（PDF 直连会被 Content-Disposition 变成下载，ZIP 直连浏览器不会列目录）。
 * previewMode 现在只用于界面上的「预览方式」标签；不支持/转换失败由 kkFileView 自己的
 * 错误页处理，业务侧不再提前判死。
 *
 * <p><b>Office 与 WPS 的边界</b>：{@code .wps/.wpt}（文字）、{@code .et/.ett}（表格）、
 * {@code .dps/.dpt}（演示）都是 WPS 私有格式，浏览器同样不能渲染，归入 office。
 * kkFileView 原生支持这些扩展名，因此不需要在业务侧再区分「国产格式」。
 */
public final class FilePreviewUtils {

    /** 图片 */
    public static final String MODE_IMAGE = "image";
    /** PDF */
    public static final String MODE_PDF = "pdf";
    /** Office / WPS / OFD */
    public static final String MODE_OFFICE = "office";
    /** 纯文本 */
    public static final String MODE_TEXT = "text";
    /** 压缩包 */
    public static final String MODE_ARCHIVE = "archive";
    /** CAD 图纸 */
    public static final String MODE_CAD = "cad";
    /** 音视频 */
    public static final String MODE_MEDIA = "media";
    /** 其余格式（仍然交 kkFileView） */
    public static final String MODE_ONLINE = "online";
    /** @deprecated 现在所有文件都走 kkFileView，不再返回 download；保留常量兼容旧调用 */
    @Deprecated
    public static final String MODE_DOWNLOAD = "download";

    private static final Set<String> IMAGE_EXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico")));

    /**
     * Office / WPS / OFD 扩展名。
     *
     * <p>★ 新增格式时只改这里：四个业务模块以及前端「预览方式」标签都会跟着变。
     * 这里不区分「微软 Office」与「WPS」，因为对业务来说两者的处理方式完全一样
     * （都不能在浏览器里直接渲染，都要交 kkFileView）。
     */
    private static final Set<String> OFFICE_EXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    // 微软 Office
                    "doc", "docx", "docm", "dot", "dotx", "dotm",
                    "xls", "xlsx", "xlsm", "xlt", "xltx", "xltm",
                    "ppt", "pptx", "pptm", "pot", "potx",
                    "rtf",
                    // WPS Office（文字 / 表格 / 演示，含模板）
                    "wps", "wpt", "et", "ett", "dps", "dpt",
                    // 国产版式文档
                    "ofd"
            )));

    private static final Set<String> TEXT_EXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("txt", "csv", "log", "md", "xml", "json")));

    /** 压缩包（kkFileView 支持目录浏览） */
    private static final Set<String> ARCHIVE_EXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "jar")));

    /** CAD 图纸（kkFileView 支持 dwg/dxf 等） */
    private static final Set<String> CAD_EXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("dwg", "dxf", "dwf", "dwt", "plt", "cf2")));

    /** 音视频（kkFileView 支持直接播放/转码） */
    private static final Set<String> MEDIA_EXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("mp4", "avi", "mov", "wmv", "mkv", "flv", "webm",
                    "mp3", "wav", "m4a", "ogg")));

    private FilePreviewUtils() {
        // 工具类不允许实例化
    }

    /**
     * 扩展名或文件名 → 预览方式。
     *
     * <p>入参既接受 {@code docx} 也接受 {@code 规划许可证.docx}：调用方经常只拿到
     * {@code fileExt}，但历史数据里该字段可能为空，这时用文件名兜底更稳。
     *
     * @param extOrFileName 扩展名（可带点）或完整文件名
     * @return image / pdf / office / text / archive / cad / media / online，永不为 null
     */
    public static String previewModeOf(String extOrFileName) {
        String ext = extOf(extOrFileName);
        if (StringUtils.isBlank(ext)) {
            return MODE_ONLINE;
        }
        if (IMAGE_EXTS.contains(ext)) {
            return MODE_IMAGE;
        }
        if ("pdf".equals(ext)) {
            return MODE_PDF;
        }
        if (OFFICE_EXTS.contains(ext)) {
            return MODE_OFFICE;
        }
        if (TEXT_EXTS.contains(ext)) {
            return MODE_TEXT;
        }
        if (ARCHIVE_EXTS.contains(ext)) {
            return MODE_ARCHIVE;
        }
        if (CAD_EXTS.contains(ext)) {
            return MODE_CAD;
        }
        if (MEDIA_EXTS.contains(ext)) {
            return MODE_MEDIA;
        }
        // ★ 兜底不再是 download：所有文件都交 kkFileView，
        //   能不能渲染由它判断（它的错误页比业务侧提前判死更准确）。
        return MODE_ONLINE;
    }

    /**
     * 是否可以在线预览。
     *
     * <p>★ 现在**恒为 true**：预览统一由 kkFileView 承担，业务侧不再用扩展名白名单
     * 阻止用户打开。真正的「不支持」由 kkFileView 的错误页反馈。
     */
    public static boolean previewableOf(String extOrFileName) {
        return true;
    }

    /**
     * 取小写扩展名（不含点）。
     *
     * <p>兼容三种历史写法：{@code docx} / {@code .docx} / {@code 规划许可证.docx}。
     * 没有扩展名（或文件名以点结尾）返回 null。
     */
    public static String extOf(String extOrFileName) {
        String value = StringUtils.trimToNull(extOrFileName);
        if (value == null) {
            return null;
        }
        // 路径分隔符出现时按文件名处理，避免把目录名里的点当成扩展名
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        int dot = value.lastIndexOf('.');
        if (dot >= 0) {
            if (dot == value.length() - 1) {
                return null;
            }
            value = value.substring(dot + 1);
        } else if (!value.matches("[A-Za-z0-9]+")) {
            // 没有点、又不全是字母数字：这是「没有扩展名的文件名」，不是扩展名。
            // 例如「没有扩展名的文件」不能被当成一种扩展名，否则会进到 download 之外的分支。
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value.toLowerCase(Locale.ROOT);
    }

    /**
     * storePath → 前端可直接拼静态资源地址的相对路径（去掉前导斜杠）。
     *
     * <p>只做展示层归一，不做安全校验：真正的路径归属校验在每个模块的
     * {@code resolveAbsolutePath} 里（必须落在上传根目录内）。
     */
    public static String toRelativePath(String storePath) {
        if (StringUtils.isBlank(storePath)) {
            return null;
        }
        String path = storePath.replace('\\', '/').trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        // 兼容历史数据里重复的分隔符（如 archive//2026//10/a.pdf），
        // 重复斜杠在某些静态资源实现上会被当成不同路径，导致 404。
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        return path.isEmpty() ? null : path;
    }
}

