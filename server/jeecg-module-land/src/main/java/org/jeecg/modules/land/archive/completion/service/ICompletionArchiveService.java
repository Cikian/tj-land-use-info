package org.jeecg.modules.land.archive.completion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.completion.dto.CompletionQueryDTO;
import org.jeecg.modules.land.archive.completion.dto.CompletionSaveDTO;
import org.jeecg.modules.land.archive.completion.entity.CompletionArchive;
import org.jeecg.modules.land.archive.completion.vo.CompletionStatVO;
import org.jeecg.modules.land.archive.completion.vo.RelatedArchiveVO;

import java.util.List;

/**
 * @Description: 竣工验收项目历史工程资料数字化档案 Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>职责边界：
 * <ol>
 *   <li><b>档案本身</b>：增删改查、数字化状态变更、档案编号（JG-{yyyy}-{4位}）；</li>
 *   <li><b>数字化追踪</b>：状态白名单校验 + 进度折算（清单 §6.3.8 的重点）；</li>
 *   <li><b>扫描件关联</b>：只读写自己表上的 {@code archive_id} / {@code archive_count}，
 *       <b>不写</b> {@code t_archive}（扫描件的生命周期完全归档案管理模块）；</li>
 *   <li><b>查询与统计</b>：列表 / 状态计数 / 6 组统计共用同一套条件。</li>
 * </ol>
 */
public interface ICompletionArchiveService extends IService<CompletionArchive> {

    // ==================================================================
    // 查询
    // ==================================================================

    /** 档案分页（档案页 / 查询共用；含序号与数字化进度的非表字段） */
    IPage<CompletionArchive> queryPage(CompletionQueryDTO query);

    /** 档案详情（含数字化进度 + 关联扫描件列表） */
    CompletionArchive queryDetail(String id);

    /** 导出取数：不分页（序号从 1 连续编） */
    List<CompletionArchive> queryForExport(CompletionQueryDTO query);

    /** 汇总与 6 组分类统计（一次请求全部返回） */
    CompletionStatVO queryStat(CompletionQueryDTO query);

    /** 各数字化状态计数（固定 3 项，数量为 0 也返回，前端 tab 角标直接用） */
    List<CompletionStatVO.StatusCount> countByStatus(CompletionQueryDTO query);

    // ==================================================================
    // 增删改
    // ==================================================================

    /** 新增历史档案（编号自动生成或手工指定，返回新 id） */
    String createArchive(CompletionSaveDTO dto);

    /** 编辑档案（逐字段覆盖，允许把已填字段清空为 NULL） */
    void updateArchive(CompletionSaveDTO dto);

    /** ★变更数字化状态（只改 digitize_status 一列，不做流转顺序校验） */
    void changeDigitizeStatus(String id, String digitizeStatus);

    /** 删除（逻辑删除；不动档案、不动配套项目） */
    void deleteArchive(String id);

    /** 批量删除 */
    void deleteArchives(List<String> ids);

    // ==================================================================
    // 档案编号
    // ==================================================================

    /** 预生成档案编号 JG-{yyyy}-{4位}（仅预览，不落库） */
    String generateArchiveNo(Integer year);

    /** 档案编号唯一校验（编辑时排除自身） */
    void checkArchiveNoUnique(String archiveNo, String excludeId);

    // ==================================================================
    // 关联扫描件（只读 t_archive，只写自己表的 archive_id）
    // ==================================================================

    /** 某条档案对应的全部扫描件（配套项目ID 优先，宗地编号兜底） */
    List<RelatedArchiveVO> queryRelatedArchives(String id, Integer limit);

    /** 挑档案列表（按配套项目/宗地过滤，或按关键词全局搜） */
    List<RelatedArchiveVO> queryArchivesForPick(String facilityId, String crzdbh, String keyword, Integer limit);

    /** 关联一个扫描件档案（写 archive_id + 重算 archive_count） */
    void linkArchive(String id, String archiveId);

    /** 取消关联（archive_id 置空 + archive_count 重算） */
    void unlinkArchive(String id);
}
