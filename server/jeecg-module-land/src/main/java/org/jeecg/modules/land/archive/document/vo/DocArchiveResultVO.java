package org.jeecg.modules.land.archive.document.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @Description: 收发文归档结果（含可核对的分段计数）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-16
 * @Version V1.0
 *
 * <p><b>为什么要有这个 VO（而不是继续返回一个 archiveId 字符串）</b>：
 * 测试反馈「归档后档案文件页签看不到收发文档案」。能看到的现象只有「0 个文件」，
 * 但断点可能在三处完全不同的位置：
 * <ol>
 *   <li>原公文本来就没有附件（attachmentCount = 0）——不是缺陷；</li>
 *   <li>附件读出来了，但转换时被过滤掉（convertedFileCount &lt; attachmentCount）；</li>
 *   <li>转换结果没写进 t_archive_file（savedFileCount &lt; convertedFileCount）。</li>
 * </ol>
 * 把这三段计数随归档结果一起返回、并在归档结果弹窗里展示，
 * 用户与运维一眼就能判断该找谁（补附件 / 查转换 / 查落库），
 * 不必再靠「看档案文件页签是空的」猜。
 *
 * <p><b>不改业务口径</b>：这里只加可观测性，不伪造任何文件，也不改变归档的落库行为。
 */
@Data
@Accessors(chain = true)
public class DocArchiveResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 档案ID（前端「查看档案」用它打开档案详情） */
    private String archiveId;

    /** 档案号，便于用户核对归档去了哪一卷 */
    private String archiveNo;

    /** 本次是复用同项目已有案卷（true）还是新建案卷（false） */
    private boolean reusedCaseFile;

    /** 归档后档案的真实状态（已归档 / 归档中） */
    private String archiveStatus;

    /** 归档后公文的真实状态（已归档 / 已办结 等） */
    private String docStatus;

    /** 原公文附件数 */
    private int attachmentCount;

    /** 转换出的卷内文件数（正常情况下应等于附件数） */
    private int convertedFileCount;

    /** 实际写入 t_archive_file 的行数（归档后重新统计，是最终事实） */
    private int savedFileCount;

    /** 一段可直接展示的中文核验说明，例如「原附件 3 → 转换 3 → 落库 3」 */
    private String verifyText;
}
