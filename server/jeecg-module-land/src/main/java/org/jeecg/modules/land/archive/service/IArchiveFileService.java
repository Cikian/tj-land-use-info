package org.jeecg.modules.land.archive.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.entity.ArchiveFile;

import java.util.List;

/**
 * @Description: 档案文件（卷内文件）Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>职责：
 * <ul>
 *   <li><b>类别校验与冗余</b>——每个文件必选一个<b>叶子</b>类别，保存时把
 *       category_id / category_path / category_name 一起落库；</li>
 *   <li><b>文件元数据补齐</b>——扩展名、题名、可读大小；</li>
 *   <li><b>路径安全</b>——{@link #resolveAbsolutePath(ArchiveFile)} 由服务端
 *       用「上传根目录 + store_path」拼出绝对路径，并对结果做前缀校验，
 *       防止 {@code ../} 穿越（旧 tj-sfw 的 /pdf/render 就是路径拼接漏洞）；</li>
 *   <li><b>冗余统计</b>——回写 t_archive.file_count / total_size。</li>
 * </ul>
 */
public interface IArchiveFileService extends IService<ArchiveFile> {

    /** 某档案下的全部文件（已补齐 url / 可读大小） */
    List<ArchiveFile> queryByArchiveId(String archiveId);

    /**
     * 批量取多个档案的文件，按 archiveId 分组。
     * 导出打包时必须用这个而不是循环调用 {@link #queryByArchiveId(String)}，避免 N+1。
     */
    java.util.Map<String, List<ArchiveFile>> queryByArchiveIds(List<String> archiveIds);

    /** 单个文件（已补齐 url / 可读大小） */
    ArchiveFile queryFileById(String fileId);

    /** 新增一个文件（会校验类别并回写档案的文件数统计） */
    void saveFile(String archiveId, ArchiveFile file);

    /** 批量新增文件 */
    void saveFiles(String archiveId, List<ArchiveFile> files);

    /** 修改文件（类别、题名、备注、状态） */
    void updateFile(ArchiveFile file);

    /** 删除单个文件（逻辑删除，并回写统计数据） */
    void deleteFile(String fileId);

    /** 删除某档案下的全部文件（删档案时级联） */
    void deleteByArchiveId(String archiveId);

    /** 重算并回写 t_archive 的文件数与总字节 */
    void refreshArchiveFileStats(String archiveId);

    /** 文件可直接访问的相对 URL（前端预览/下载用） */
    String buildUrl(ArchiveFile file);

    /** 文件在磁盘上的绝对路径（含路径穿越校验） */
    String resolveAbsolutePath(ArchiveFile file);
}
