package org.jeecg.modules.land.archive.ledger.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.ledger.dto.LedgerQueryDTO;
import org.jeecg.modules.land.archive.ledger.dto.LedgerSaveDTO;
import org.jeecg.modules.land.archive.ledger.entity.RoadAcceptanceLedger;
import org.jeecg.modules.land.archive.ledger.vo.LedgerStatVO;
import org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO;

import java.util.List;

/**
 * @Description: 道路设施验收及移交资料台账 Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>职责边界：
 * <ol>
 *   <li><b>台账本身</b>：增删改查、状态变更、台账编号（YS-{yyyy}-{4位}）；</li>
 *   <li><b>资料矩阵</b>：13 类资料勾选值的归一化与 {@code material_count} 重算；</li>
 *   <li><b>档案关联</b>：只读写自己表上的 {@code archive_id} / {@code archive_count}，
 *       <b>不写</b> {@code t_archive}（档案的生命周期完全归档案管理模块）；</li>
 *   <li><b>查询与统计</b>：台账列表 / 状态计数 / 8 组统计共用同一套条件。</li>
 * </ol>
 */
public interface IRoadAcceptanceLedgerService extends IService<RoadAcceptanceLedger> {

    // ==================================================================
    // 查询
    // ==================================================================

    /** 台账分页（台账页 / 查询共用；含序号与资料统计的非表字段） */
    IPage<RoadAcceptanceLedger> queryPage(LedgerQueryDTO query);

    /** 台账详情（含 13 类资料统计 + 关联档案列表） */
    RoadAcceptanceLedger queryDetail(String id);

    /** 导出取数：不分页（序号从 1 连续编） */
    List<RoadAcceptanceLedger> queryForExport(LedgerQueryDTO query);

    /** 汇总与 8 组分类统计（一次请求全部返回） */
    LedgerStatVO queryStat(LedgerQueryDTO query);

    /** 各状态计数（固定 4 项，数量为 0 也返回，前端 tab 角标直接用） */
    List<LedgerStatVO.StatusCount> countByStatus(LedgerQueryDTO query);

    // ==================================================================
    // 增删改
    // ==================================================================

    /** 新增台账（编号自动生成或手工指定，返回新 id） */
    String createLedger(LedgerSaveDTO dto);

    /** 编辑台账（逐字段覆盖，允许把已填字段清空为 NULL） */
    void updateLedger(LedgerSaveDTO dto);

    /** 变更状态（只改 state 一列，不做流转顺序校验） */
    void changeStatus(String id, String status);

    /** 删除（逻辑删除；不动档案、不动配套项目） */
    void deleteLedger(String id);

    /** 批量删除 */
    void deleteLedgers(List<String> ids);

    // ==================================================================
    // 台账编号
    // ==================================================================

    /** 预生成台账编号 YS-{yyyy}-{4位}（仅预览，不落库） */
    String generateLedgerNo(Integer year);

    /** 台账编号唯一校验（编辑时排除自身） */
    void checkLedgerNoUnique(String ledgerNo, String excludeId);

    // ==================================================================
    // 关联档案（只读 t_archive，只写自己表的 archive_id）
    // ==================================================================

    /** 某条台账对应的全部档案（配套项目ID 优先，宗地编号兜底） */
    List<RelatedArchiveVO> queryRelatedArchives(String id, Integer limit);

    /** 挑档案列表（按配套项目/宗地过滤，或按关键词全局搜） */
    List<RelatedArchiveVO> queryArchivesForPick(String facilityId, String crzdbh, String keyword, Integer limit);

    /** 关联一个档案（写 archive_id + 重算 archive_count） */
    void linkArchive(String id, String archiveId);

    /** 取消关联（archive_id 置空 + archive_count 重算） */
    void unlinkArchive(String id);
}
