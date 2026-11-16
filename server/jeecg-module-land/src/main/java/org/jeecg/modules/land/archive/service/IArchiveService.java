package org.jeecg.modules.land.archive.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.dto.ArchiveQueryDTO;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.entity.ArchiveFile;
import org.jeecg.modules.land.archive.entity.ArchiveLog;
import org.jeecg.modules.land.archive.vo.ArchiveProjectVO;
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
    // 按项目聚合（P2-07）
    // ------------------------------------------------------------------

    /**
     * 按「配套项目」分页聚合（树形第一层：项目 → 案卷 → 材料类别 → 文件）。
     *
     * <p><b>分页口径：以「项目」为单位分页</b>，不是以案卷为单位分页。
     * 原因：测试要的是「同一项目的档案集中展示」，若按案卷分页，
     * 一个项目就会被切到相邻两页，用户反而更难看全。
     * 分组键 = {@code facility_id}；没有 facility_id 的历史数据退化用
     * {@code ptxmmc + crzdbh} 作键（这些数据本来就是按名称冗余存储的）。
     *
     * @param query    与列表完全相同的筛选条件（保证「聚合视图看到的」和「列表看到的是」同一批数据）
     * @param pageNo   页码（项目序号）
     * @param pageSize 每页项目数
     */
    IPage<ArchiveProjectVO> queryProjectPage(ArchiveQueryDTO query, Integer pageNo, Integer pageSize);

    /**
     * 取某个项目下的全部案卷（树形第二层）。
     *
     * @param query      与列表相同的筛选条件
     * @param projectKey 分组键（{@code queryProjectPage} 返回的 projectKey，原样回传）
     */
    List<Archive> queryProjectCaseFiles(ArchiveQueryDTO query, String projectKey);

    // ------------------------------------------------------------------
    // 收发文归档时复用同项目案卷（P2-07 第 4 条）
    // ------------------------------------------------------------------

    /**
     * 查找同项目已有的案卷（「一项目一案卷」口径下复用它追加文件）。
     *
     * <p>只读查找，<b>不删除、不合并任何历史案卷</b>：找不到就返回 null，由调用方新建。
     * 选择规则：同 facilityId 优先，其次同 crzdbh；同类里取创建时间最早的一卷
     * （最早那卷通常就是中心实际在用的案卷），并按档案号升序保证结果稳定。
     */
    Archive findReuseCaseFile(String facilityId, String crzdbh);

    /**
     * 往已有案卷追加卷内文件（收发文归档复用案卷时用）。
     *
     * <p>只做追加：不删除该案卷原有文件，也不改档案号/档案名称/状态等主表信息
     * （改状态会影响历史记录的可核对性，需业务另行确认）。
     *
     * <p><b>返回的是「本次真实写入的行数」</b>（P1-11）：实现里在 {@code saveFiles}
     * 之后重新 COUNT 一次 {@code t_archive_file}，取「归档后行数 − 归档前行数」。
     * 不再直接返回入参 {@code files.size()} —— 那是「打算写几个」，
     * 写入部分失败时它会照样等于入参，等于把缺陷掩盖成成功。
     *
     * @return 实际写入的 t_archive_file 行数
     */
    int appendFiles(String archiveId, List<ArchiveFile> files);

    /**
     * 某个案卷当前在库里真实存在的卷内文件行数（{@code del_flag = 0}）。
     *
     * <p><b>为什么收发文归档需要它（P1-11 第三段计数）</b>：新建案卷分支以前写
     * {@code savedFileCount = files.size()}，这个数字来自入参、绕过了数据库，
     * 「转换 3 个 → 落库 2 行」这种部分失败永远发现不了。新建后必须回查一次库，
     * 让 {@code verifyText} 的第三段等于档案文件页签里能看到的条数。
     *
     * @param archiveId 档案ID；为空时返回 0（不抛异常，避免把「统计口径」问题变成归档失败）
     */
    int countFiles(String archiveId);

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
