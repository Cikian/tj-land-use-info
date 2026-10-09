package org.jeecg.modules.land.archive.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.ledger.dto.LedgerQueryDTO;
import org.jeecg.modules.land.archive.ledger.entity.RoadAcceptanceLedger;
import org.jeecg.modules.land.archive.ledger.vo.LedgerStatVO;
import org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO;

import java.util.List;
import java.util.Map;

/**
 * @Description: 道路设施验收及移交资料台账 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>台账列表 / 统计 / 导出<b>共用 XML 里的同一段 {@code queryWhere}</b>，
 * 保证「台账里看到什么，统计与导出就是什么」。
 *
 * <p><b>★ 本 Mapper 同时读两张表</b>：主表 {@code t_road_acceptance_ledger}，
 * 以及档案模块的 {@code t_archive}（只读，用于「关联档案」页签）。
 * 台账<b>不写</b>档案表：附件与档案的生命周期完全归档案模块，
 * 台账只在自己的 {@code archive_id} 上记一个指针。
 */
public interface RoadAcceptanceLedgerMapper extends BaseMapper<RoadAcceptanceLedger> {

    // ==================================================================
    // 一、台账本体
    // ==================================================================

    /**
     * 台账分页查询（台账页 / 查询共用）。
     *
     * @param page MyBatis-Plus 分页对象
     * @param q    查询条件
     */
    IPage<RoadAcceptanceLedger> selectLedgerPage(Page<RoadAcceptanceLedger> page,
                                                 @Param("q") LedgerQueryDTO q);

    /** 导出用：不分页地取出全部命中台账 */
    List<RoadAcceptanceLedger> selectForExport(@Param("q") LedgerQueryDTO q);

    /**
     * 当前条件下的汇总指标（一次查询返回 5 个标量，避免开 5 个接口）。
     *
     * <p>返回键：{@code total / migratedCount / manualCount / orphanCount /
     * fullMaterialCount / emptyMaterialCount}。
     */
    Map<String, Object> selectSummary(@Param("q") LedgerQueryDTO q);

    /**
     * 按状态统计（台账顶部 tab 的角标 + 统计页的状态分布图）。
     *
     * <p>只返回库里实际存在的状态，数量为 0 的状态由 Service 补齐成 4 项。
     */
    List<LedgerStatVO.StatusCount> selectCountGroupByStatus(@Param("q") LedgerQueryDTO q);

    /**
     * 按某一列分组计数（状态 / 行政区划 / 功能区 / 验收类型 / 验收结果 / 设施类别共用）。
     *
     * <p><b>★ {@code column} 用 {@code ${}} 拼接，调用方必须只传代码里的常量。</b>
     * 目前唯一调用方是 Service 内部的 {@code statByColumn}，列名全部写死在方法体里，
     * 绝不接受请求参数（查询条件里的资料筛选另有 {@code LedgerMaterial} 白名单）。
     *
     * @param column     列名（如 {@code xzqh}）
     * @param emptyLabel 该列为空时的归类名（如「未填写」「未登记」）
     */
    List<LedgerStatVO.NameCount> selectStatByColumn(@Param("q") LedgerQueryDTO q,
                                                    @Param("column") String column,
                                                    @Param("emptyLabel") String emptyLabel);

    /** 按台账年度统计（年度取自台账编号 YS-{yyyy}- 段；非本格式的编号不计入） */
    List<LedgerStatVO.NameCount> selectStatByYear(@Param("q") LedgerQueryDTO q);

    /**
     * 13 类资料的归集情况（每类已勾选的台账数）。
     *
     * <p>返回键 = {@code LedgerMaterial.property}（hasSgxk … hasYjwj），
     * 由 Service 按枚举顺序组装成 {@code NameCount} 列表（带中文名）。
     */
    Map<String, Object> selectMaterialCoverage(@Param("q") LedgerQueryDTO q);

    /**
     * 台账编号重复校验（唯一键 {@code uk_ral_no} 是 ledger_no <b>单列</b>）。
     *
     * @param ledgerNo  编号
     * @param excludeId 编辑时排除自身，可为空
     */
    int countByLedgerNo(@Param("ledgerNo") String ledgerNo, @Param("excludeId") String excludeId);

    // ==================================================================
    // 一·B、写入（为什么不用 MyBatis-Plus 的 updateById，见下）
    // ==================================================================

    /**
     * 编辑台账：把 DTO 里出现的<b>全部可编辑列</b>整体覆盖写。
     *
     * <p><b>★ 为什么不用 {@code updateById}</b>：MyBatis-Plus 默认字段策略是
     * {@code NOT_NULL}，实体里为 null 的字段会被<b>跳过</b>，于是
     * 「把填错的验收日期清空」这类操作在页面上永远做不成（用户删掉日期点保存，值还在）。
     * 台账的验收/移交字段大多由人工补录，改错、撤回是常态，所以这里用显式 SQL 覆盖，
     * 允许把列写成 NULL。
     *
     * <p>刻意<b>不</b>包含 {@code archive_id} / {@code archive_count}
     * （由 {@link #updateArchiveLink} 单独维护，避免编辑表单误清关联档案）、
     * 也不包含 {@code source_facility_id} / {@code del_flag} / {@code create_*}。
     */
    int updateLedgerAll(RoadAcceptanceLedger ledger);

    /** 变更状态（只写 status 一列） */
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("updateBy") String updateBy,
                     @Param("updateTime") java.util.Date updateTime);

    /**
     * 关联档案 / 取消关联（{@code archiveId} 传 null 即取消关联）。
     *
     * <p>同样用显式 SQL：取消关联就是要把 {@code archive_id} 写回 NULL。
     */
    int updateArchiveLink(@Param("id") String id,
                          @Param("archiveId") String archiveId,
                          @Param("archiveCount") Integer archiveCount,
                          @Param("updateBy") String updateBy,
                          @Param("updateTime") java.util.Date updateTime);

    /**
     * 取某年度已用掉的最大流水号（台账编号自动生成用）。
     *
     * @param prefix 形如 {@code YS-2026-}
     * @return 最大流水号；没有记录时返回 null
     */
    Integer selectMaxSeqOfYear(@Param("prefix") String prefix);

    // ==================================================================
    // 二、关联档案（只读 t_archive / 只读 t_archive_file 的冗余计数列）
    // ==================================================================

    /**
     * 某条台账对应的「全部关联档案」。
     *
     * <p>口径：**配套项目ID 优先**，为空时退回按出让宗地编号匹配
     * （与档案模块「先选宗地 → 再选配套项目」的关联方式一致：
     * 档案挂在配套项目上，同一宗地下的多条道路各有自己的档案）。
     *
     * @param facilityId 配套项目ID（可空）
     * @param crzdbh     出让宗地编号（可空）
     * @param limit      条数上限
     */
    List<RelatedArchiveVO> selectRelatedArchives(@Param("facilityId") String facilityId,
                                                 @Param("crzdbh") String crzdbh,
                                                 @Param("limit") Integer limit);

    /** 关联档案计数（服务端维护 {@code archive_count} 冗余列） */
    int countRelatedArchives(@Param("facilityId") String facilityId, @Param("crzdbh") String crzdbh);

    /**
     * 关联档案时的「挑档案」列表（只读查询，不要求调用方有档案模块权限）。
     *
     * @param facilityId 配套项目ID（可空，给了就按它过滤）
     * @param crzdbh     宗地编号（可空）
     * @param keyword    档案号 / 档案名称 模糊
     * @param limit      条数上限
     */
    List<RelatedArchiveVO> selectArchivesForPick(@Param("facilityId") String facilityId,
                                                 @Param("crzdbh") String crzdbh,
                                                 @Param("keyword") String keyword,
                                                 @Param("limit") Integer limit);

    /** 档案是否存在（关联前校验；不存在/已删除返回 0） */
    int countArchiveById(@Param("archiveId") String archiveId);
}
