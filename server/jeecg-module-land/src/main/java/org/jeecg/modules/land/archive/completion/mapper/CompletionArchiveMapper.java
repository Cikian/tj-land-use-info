package org.jeecg.modules.land.archive.completion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.completion.dto.CompletionQueryDTO;
import org.jeecg.modules.land.archive.completion.entity.CompletionArchive;
import org.jeecg.modules.land.archive.completion.vo.CompletionStatVO;
import org.jeecg.modules.land.archive.completion.vo.RelatedArchiveVO;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Description: 竣工验收项目历史工程资料数字化档案 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>列表 / 统计 / 导出<b>共用 XML 里的同一段 {@code queryWhere}</b>，
 * 保证「档案列表里看到什么，统计与导出就是什么」。
 *
 * <p><b>★ 本 Mapper 同时读两张表</b>：主表 {@code t_completion_archive}，
 * 以及档案模块的 {@code t_archive}（只读，用于「关联扫描件」页签）。
 * 本模块<b>不写</b>档案表：扫描件的生命周期完全归档案模块，
 * 本模块只在自己的 {@code archive_id} 上记一个指针。
 */
public interface CompletionArchiveMapper extends BaseMapper<CompletionArchive> {

    // ==================================================================
    // 一、档案本体
    // ==================================================================

    /**
     * 档案分页查询（档案页 / 查询共用）。
     *
     * @param page MyBatis-Plus 分页对象
     * @param q    查询条件
     */
    IPage<CompletionArchive> selectCompletionPage(Page<CompletionArchive> page,
                                                 @Param("q") CompletionQueryDTO q);

    /** 导出用：不分页地取出全部命中档案（需求原文「可快速导出档案信息」） */
    List<CompletionArchive> selectForExport(@Param("q") CompletionQueryDTO q);

    /**
     * 当前条件下的汇总指标（一次查询返回 6 个标量，避免开 6 个接口）。
     *
     * <p>返回键：{@code total / archivedCount / digitizedCount / pageTotal / fileTotal / investTotal}。
     */
    Map<String, Object> selectSummary(@Param("q") CompletionQueryDTO q);

    /**
     * 按数字化状态统计（顶部状态 tab 的角标 + 统计页的状态分布）。
     *
     * <p>只返回库里实际存在的状态，数量为 0 的状态由 Service 补齐成 3 项。
     */
    List<CompletionStatVO.StatusCount> selectCountGroupByStatus(@Param("q") CompletionQueryDTO q);

    /**
     * 按某一列分组计数（行政区划 / 项目类型 / 保管期限共用）。
     *
     * <p><b>★ {@code column} 用 {@code ${}} 拼接，调用方必须只传代码里的常量。</b>
     * 目前唯一调用方是 Service 内部的 {@code statByColumn}，列名全部写死在方法体里，
     * 绝不接受请求参数。
     *
     * @param column     列名（如 {@code xzqh}）
     * @param emptyLabel 该列为空时的归类名（如「未填写」）
     */
    List<CompletionStatVO.NameCount> selectStatByColumn(@Param("q") CompletionQueryDTO q,
                                                        @Param("column") String column,
                                                        @Param("emptyLabel") String emptyLabel);

    /** 按档案编号年度统计（年度取自 archive_no 的 JG-{yyyy}- 段；非本格式的编号不计入） */
    List<CompletionStatVO.NameCount> selectStatByYear(@Param("q") CompletionQueryDTO q);

    /** ★按竣工年度统计（历史档案盘点最常用的维度；竣工日期为空的档案不计入） */
    List<CompletionStatVO.NameCount> selectStatByCompleteYear(@Param("q") CompletionQueryDTO q);

    /**
     * 档案编号重复校验（唯一键 {@code uk_ca_no} 是 archive_no <b>单列</b>）。
     *
     * @param archiveNo 编号
     * @param excludeId 编辑时排除自身，可为空
     */
    int countByArchiveNo(@Param("archiveNo") String archiveNo, @Param("excludeId") String excludeId);

    /**
     * 取某年度已用掉的最大流水号（档案编号自动生成用）。
     *
     * @param prefix 形如 {@code JG-2026-}
     * @return 最大流水号；没有记录时返回 null
     */
    Integer selectMaxSeqOfYear(@Param("prefix") String prefix);

    // ==================================================================
    // 二、写入（为什么不用 MyBatis-Plus 的 updateById，见下）
    // ==================================================================

    /**
     * 编辑档案：把 DTO 里出现的<b>全部可编辑列</b>整体覆盖写。
     *
     * <p><b>★ 为什么不用 {@code updateById}</b>：MyBatis-Plus 默认字段策略是
     * {@code NOT_NULL}，实体里为 null 的字段会被<b>跳过</b>，于是
     * 「把填错的竣工日期/页数清空」这类操作在页面上永远做不成（用户删掉日期点保存，值还在）。
     * 历史档案是**逐条补录**的（页数、DPI、加工单位大多后补），改错、撤回是常态，
     * 所以这里用显式 SQL 覆盖，允许把列写成 NULL。
     *
     * <p>刻意<b>不</b>包含 {@code archive_id} / {@code archive_count}
     * （由 {@link #updateArchiveLink} 单独维护，避免编辑表单误清扫描件关联），
     * 也不包含 {@code del_flag} / {@code create_*}。
     */
    int updateCompletionAll(CompletionArchive archive);

    /** 变更数字化状态（只写 digitize_status 一列） */
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("updateBy") String updateBy,
                     @Param("updateTime") Date updateTime);

    /**
     * 关联扫描件 / 取消关联（{@code archiveId} 传 null 即取消关联）。
     *
     * <p>同样用显式 SQL：取消关联就是要把 {@code archive_id} 写回 NULL。
     */
    int updateArchiveLink(@Param("id") String id,
                          @Param("archiveId") String archiveId,
                          @Param("archiveCount") Integer archiveCount,
                          @Param("updateBy") String updateBy,
                          @Param("updateTime") Date updateTime);

    // ==================================================================
    // 三、关联扫描件（只读 t_archive）
    // ==================================================================

    /**
     * 某条档案对应的「全部关联扫描件」。
     *
     * <p>口径：**配套项目ID 优先**，为空时退回按出让宗地编号匹配
     * （与档案模块「先选宗地 → 再选配套项目」的关联方式一致）。
     *
     * @param facilityId 配套项目ID（可空）
     * @param crzdbh     出让宗地编号（可空）
     * @param limit      条数上限
     */
    List<RelatedArchiveVO> selectRelatedArchives(@Param("facilityId") String facilityId,
                                                 @Param("crzdbh") String crzdbh,
                                                 @Param("limit") Integer limit);

    /** 关联扫描件计数（服务端维护 {@code archive_count} 冗余列） */
    int countRelatedArchives(@Param("facilityId") String facilityId, @Param("crzdbh") String crzdbh);

    /**
     * 关联扫描件时的「挑档案」列表（只读查询，不要求调用方有档案模块权限）。
     *
     * @param facilityId 配套项目ID（可空，给了就按它过滤）
     * @param crzdbh     宗地编号（可空）
     * @param keyword    档案号 / 档案名称 / 配套项目名称 模糊
     * @param limit      条数上限
     */
    List<RelatedArchiveVO> selectArchivesForPick(@Param("facilityId") String facilityId,
                                                 @Param("crzdbh") String crzdbh,
                                                 @Param("keyword") String keyword,
                                                 @Param("limit") Integer limit);

    /** 档案是否存在（关联前校验；不存在/已删除返回 0） */
    int countArchiveById(@Param("archiveId") String archiveId);
}
