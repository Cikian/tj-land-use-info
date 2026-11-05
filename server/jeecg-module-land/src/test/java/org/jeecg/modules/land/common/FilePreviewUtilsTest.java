package org.jeecg.modules.land.common;

import org.jeecg.modules.land.common.util.FilePreviewUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件预览方式判定的回归测试。
 *
 * <p><b>它防的是什么</b>：{@link FilePreviewUtils} 是档案、收发文、数据附件、提级材料
 * 四个模块共用的唯一口径。一旦这里把 wps 漏掉，表现不是编译错误，而是
 * 「用户上传的 .wps 在档案页只显示下载」——功能看起来坏了，却找不到哪行代码有问题。
 *
 * <p>同时钉住两个历史数据兼容点：
 * <ul>
 *   <li>{@code fileExt} 为空时用文件名兜底；</li>
 *   <li>大小写、多个点、带前导点的各种写法都要归一。</li>
 * </ul>
 */
public class FilePreviewUtilsTest {

    @Test
    public void officeAndWpsArePreviewable() {
        // 微软 Office
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("doc"));
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("DOCX"));
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("xls"));
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("xlsx"));
        // WPS 文字 / 表格 / 演示，含模板
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("wps"));
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("wpt"));
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("et"));
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("ett"));
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("dps"));
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("dpt"));
        // 国产版式文档
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("ofd"));
        assertTrue(FilePreviewUtils.previewableOf("wps"));
    }

    @Test
    public void imagePdfAndTextKeepTheirOwnModes() {
        assertEquals(FilePreviewUtils.MODE_IMAGE, FilePreviewUtils.previewModeOf("jpg"));
        assertEquals(FilePreviewUtils.MODE_IMAGE, FilePreviewUtils.previewModeOf("PNG"));
        assertEquals(FilePreviewUtils.MODE_PDF, FilePreviewUtils.previewModeOf("pdf"));
        assertEquals(FilePreviewUtils.MODE_TEXT, FilePreviewUtils.previewModeOf("txt"));
        assertEquals(FilePreviewUtils.MODE_TEXT, FilePreviewUtils.previewModeOf("csv"));
    }

    @Test
    public void archiveCadMediaAndUnknownAreStillPreviewable() {
        // 2026-10-22 起所有文件统一走 kkFileView：这里只决定界面标签，不再阻止预览
        assertEquals(FilePreviewUtils.MODE_ARCHIVE, FilePreviewUtils.previewModeOf("zip"));
        assertEquals(FilePreviewUtils.MODE_ARCHIVE, FilePreviewUtils.previewModeOf("rar"));
        assertEquals(FilePreviewUtils.MODE_CAD, FilePreviewUtils.previewModeOf("dwg"));
        assertEquals(FilePreviewUtils.MODE_CAD, FilePreviewUtils.previewModeOf("dxf"));
        assertEquals(FilePreviewUtils.MODE_MEDIA, FilePreviewUtils.previewModeOf("mp4"));
        assertEquals(FilePreviewUtils.MODE_MEDIA, FilePreviewUtils.previewModeOf("mp3"));
        assertEquals(FilePreviewUtils.MODE_ONLINE, FilePreviewUtils.previewModeOf("xyz"));
        assertEquals(FilePreviewUtils.MODE_ONLINE, FilePreviewUtils.previewModeOf(null));
        assertEquals(FilePreviewUtils.MODE_ONLINE, FilePreviewUtils.previewModeOf(""));
        assertEquals(FilePreviewUtils.MODE_ONLINE, FilePreviewUtils.previewModeOf("没有扩展名的文件"));
        assertTrue(FilePreviewUtils.previewableOf("zip"));
        assertTrue(FilePreviewUtils.previewableOf("xyz"));
    }

    @Test
    public void fileNameAndMessyExtAreNormalized() {
        // 历史数据里 fileExt 可能为空，调用方会把 fileName 传进来
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("规划许可证.WPS"));
        assertEquals(FilePreviewUtils.MODE_OFFICE, FilePreviewUtils.previewModeOf("/archive/2026/10/归档文件.docx"));
        assertEquals(FilePreviewUtils.MODE_PDF, FilePreviewUtils.previewModeOf("...pdf"));
        assertEquals("docx", FilePreviewUtils.extOf(".docx"));
        assertEquals("docx", FilePreviewUtils.extOf("C:\\temp\\方案.docx"));
        assertNull(FilePreviewUtils.extOf("没有扩展名"));
        assertNull(FilePreviewUtils.extOf("结尾是点."));
    }

    @Test
    public void relativePathStripsLeadingSlashesAndBackslashes() {
        assertEquals("archive/2026/10/a.pdf", FilePreviewUtils.toRelativePath("/archive/2026/10/a.pdf"));
        assertEquals("archive/2026/10/a.pdf", FilePreviewUtils.toRelativePath("\\\\archive\\\\2026\\\\10\\\\a.pdf"));
        assertNull(FilePreviewUtils.toRelativePath(null));
        assertNull(FilePreviewUtils.toRelativePath("   "));
    }
}
