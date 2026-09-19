package org.jeecg.modules.land.archive.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.dto.ArchiveQueryDTO;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.entity.ArchiveFile;
import org.jeecg.modules.land.archive.entity.ArchiveLog;
import org.jeecg.modules.land.archive.vo.ArchiveStatVO;

import java.io.File;
import java.util.List;

/**
 * @Description: 档案主表 Service（方案 2.3.2 第 2/3/4/5 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface IArchiveService extends IService<Archive> {

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 档案分页查询（档案维护 / 档案查询共用） */
    IPage<Archive> queryPage(ArchiveQueryDTO query);

    /** 档案详情（含卷内文件，文件已补齐 url 与可读大小） */
    Archive queryDetail(String id);

    /** 某档案的卷内文件列表 */
    List<ArchiveFile> queryFiles(String archiveId);

    /** 某档案的操作记录 */
    List<ArchiveLog> queryLogs(String archiveId);

    // ------------------------------------------------------------------
    // 档案号
    // ------------------------------------------------------------------

    /**
     * 生成档案号（不落库，仅预览）。
     *
     * @param year 年度；为空时取当前年
     * @return 形如 DA-2026-0001
     */
    String generateArchiveNo(Integer year);

    /** 档案号重复校验（编辑时用 excludeId 排除自身） */
    void checkArchiveNoUnique(String archiveNo, String excludeId);

    // ------------------------------------------------------------------
    // 增删改
    // ------------------------------------------------------------------

    /**
     * 新增档案（含卷内文件）。
     *
     * @return 新档案ID
     */
    String createArchive(Archive archive, List<ArchiveFile> files);

    /** 编辑档案（含卷内文件的增/改/删同步） */
    void updateArchive(Archive archive, List<ArchiveFile> files);

    /** 删除档案（逻辑删除，级联逻辑删除其卷内文件） */
    void deleteArchive(String id);

    /** 批量删除档案 */
    void deleteArchives(List<String> ids);

    /** 变更档案状态（改为「已归档」时会写一条「归档」操作记录） */
    void changeStatus(String id, String status);

    // ------------------------------------------------------------------
    // 统计与导出
    // ------------------------------------------------------------------

    /**
     * 档案统计。
     *
     * @param query        过滤条件（与列表同一套，保证「看到的列表」和「统计出来的图」一致）
     * @param projectLimit 按项目统计的条数上限
     */
    ArchiveStatVO queryStat(ArchiveQueryDTO query, Integer projectLimit);

    /**
     * 按当前查询条件导出 ZIP（方案 2.3.2 第 4 项：可按照项目将其档案进行导出）。
     *
     * <p>目录结构：{@code {宗地编号}/{配套项目名称}/{档案类别}/{档案名称}/{文件名}}，
     * 根目录附一份 {@code 档案清单.xlsx}。
     *
     * @return 生成的临时 ZIP 文件；调用方负责流式下载后删除
     */
    File exportZip(ArchiveQueryDTO query, String operatorName);
}
