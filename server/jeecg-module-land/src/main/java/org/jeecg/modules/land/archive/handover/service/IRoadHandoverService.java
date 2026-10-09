package org.jeecg.modules.land.archive.handover.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.handover.dto.HandoverQueryDTO;
import org.jeecg.modules.land.archive.handover.dto.HandoverSaveDTO;
import org.jeecg.modules.land.archive.handover.entity.RoadHandover;
import org.jeecg.modules.land.archive.handover.vo.HandoverStatVO;
import org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO;

import java.util.List;

/**
 * @Description: 道路交付及养护协议移交事项 Service（方案 2.3.2 第 6 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>职责：移交事项的增删改查与状态变更、编号生成（YJ-{yyyy}-{4位}）、
 * 列表/状态计数/6 组统计、以及与档案模块的关联指针维护（只写自己的 archive_id）。
 */
public interface IRoadHandoverService extends IService<RoadHandover> {

    // ===== 查询 =====

    /** 分页（列表 / 查询共用；含序号非表字段） */
    IPage<RoadHandover> queryPage(HandoverQueryDTO query);

    /** 详情（含关联档案列表） */
    RoadHandover queryDetail(String id);

    /** 导出取数：不分页（序号从 1 连续编） */
    List<RoadHandover> queryForExport(HandoverQueryDTO query);

    /** 汇总与 6 组分类统计 */
    HandoverStatVO queryStat(HandoverQueryDTO query);

    /** 各状态计数（固定 3 项，数量为 0 也返回） */
    List<HandoverStatVO.StatusCount> countByStatus(HandoverQueryDTO query);

    // ===== 增删改 =====

    /** 新增（编号自动生成或手工指定，返回新 id） */
    String createHandover(HandoverSaveDTO dto);

    /** 编辑（逐字段覆盖，允许把已填字段清空为 NULL） */
    void updateHandover(HandoverSaveDTO dto);

    /** 变更状态（只改 status 一列，不做流转顺序校验） */
    void changeStatus(String id, String status);

    /** 删除（逻辑删除） */
    void deleteHandover(String id);

    /** 批量删除 */
    void deleteHandovers(List<String> ids);

    // ===== 移交编号 =====

    /** 预生成移交编号 YJ-{yyyy}-{4位}（仅预览，不落库） */
    String generateHandoverNo(Integer year);

    /** 移交编号唯一校验（编辑时排除自身） */
    void checkHandoverNoUnique(String handoverNo, String excludeId);

    // ===== 关联档案（只读 t_archive，只写自己表的 archive_id） =====

    /** 该移交事项对应的全部档案 */
    List<RelatedArchiveVO> queryRelatedArchives(String id, Integer limit);

    /** 挑档案列表 */
    List<RelatedArchiveVO> queryArchivesForPick(String facilityId, String crzdbh, String keyword, Integer limit);

    /** 关联一个档案 */
    void linkArchive(String id, String archiveId);

    /** 取消关联 */
    void unlinkArchive(String id);
}
