package org.jeecg.modules.land.archive.handover.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.handover.dto.HandoverQueryDTO;
import org.jeecg.modules.land.archive.handover.entity.RoadHandover;
import org.jeecg.modules.land.archive.handover.vo.HandoverStatVO;
import org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Description: 道路交付及养护协议移交事项 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>列表 / 统计 / 导出<b>共用 XML 里的同一段 {@code queryWhere}</b>。
 *
 * <p><b>写入为什么不用 MyBatis-Plus 的 updateById</b>：MP 默认字段策略是 NOT_NULL，
 * 实体里为 null 的字段会被跳过 —— 于是「把填错的协议日期清空」在页面上永远做不成。
 * 协议与养护期本来就是逐步补录、经常改错的字段，所以用显式 SQL 覆盖（见 {@link #updateHandoverAll}）。
 */
public interface RoadHandoverMapper extends BaseMapper<RoadHandover> {

    // ==================================================================
    // 一、移交事项本体
    // ==================================================================

    /** 分页查询（列表 / 查询共用） */
    IPage<RoadHandover> selectHandoverPage(Page<RoadHandover> page, @Param("q") HandoverQueryDTO q);

    /** 导出取数：不分页 */
    List<RoadHandover> selectForExport(@Param("q") HandoverQueryDTO q);

    /**
     * 汇总指标（一次查询返回 8 个标量，避免页面开 8 个接口）。
     *
     * <p>返回键：{@code total / migratedCount / manualCount / archivedCount /
     * missingAgreementCount / maintenanceExpiringCount / maintenanceExpiredCount / orphanCount}。
     */
    Map<String, Object> selectSummary(@Param("q") HandoverQueryDTO q);

    /**
     * 按某一列分组计数（状态/移交类型/行政区划/功能区/道路等级/接收管养单位 共用）。
     *
     * <p><b>★ {@code column} 用 {@code ${}} 拼接，调用方只能传代码里的常量</b>
     * （目前唯一调用方是 Service 内部，列名全部写死在方法体里）。
     */
    List<HandoverStatVO.NameCount> selectStatByColumn(@Param("q") HandoverQueryDTO q,
                                                      @Param("column") String column,
                                                      @Param("emptyLabel") String emptyLabel);

    /** 按移交年度统计（年度取自移交编号 YJ-{yyyy}- 段） */
    List<HandoverStatVO.NameCount> selectStatByYear(@Param("q") HandoverQueryDTO q);

    /** 按接收管养单位统计（条形图，取 Top N） */
    List<HandoverStatVO.NameCount> selectStatByReceiveUnit(@Param("q") HandoverQueryDTO q,
                                                           @Param("limit") Integer limit);

    /** 状态计数（只返回库里存在的状态，缺失的由 Service 补 0） */
    List<HandoverStatVO.StatusCount> selectCountGroupByStatus(@Param("q") HandoverQueryDTO q);

    /** 移交编号重复校验（唯一键 uk_rh_no 是单列） */
    int countByHandoverNo(@Param("handoverNo") String handoverNo, @Param("excludeId") String excludeId);

    /** 取某年度已用掉的最大流水号（编号自动生成用） */
    Integer selectMaxSeqOfYear(@Param("prefix") String prefix);

    // ==================================================================
    // 二、写入（显式 SQL，允许把字段写成 NULL）
    // ==================================================================

    /**
     * 编辑：把 DTO 里出现的全部可编辑列整体覆盖写（未涉及的列由调用方先读出来再写回，值不变）。
     *
     * <p>刻意不含 {@code archive_id} / {@code archive_count}（由 {@link #updateArchiveLink} 单独维护）、
     * 也不含 {@code source_facility_id} / {@code del_flag} / {@code create_*}。
     */
    int updateHandoverAll(RoadHandover handover);

    /** 变更状态（只写 status 一列） */
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("updateBy") String updateBy,
                     @Param("updateTime") Date updateTime);

    /** 关联档案 / 取消关联（archiveId 传 null 即取消） */
    int updateArchiveLink(@Param("id") String id,
                          @Param("archiveId") String archiveId,
                          @Param("archiveCount") Integer archiveCount,
                          @Param("updateBy") String updateBy,
                          @Param("updateTime") Date updateTime);

    // ==================================================================
    // 三、关联档案（只读 t_archive；结果类型复用台账模块的 RelatedArchiveVO）
    // ==================================================================

    /** 该移交事项对应的全部档案（配套项目ID 优先，出让宗地编号兜底） */
    List<RelatedArchiveVO> selectRelatedArchives(@Param("facilityId") String facilityId,
                                                 @Param("crzdbh") String crzdbh,
                                                 @Param("limit") Integer limit);

    /** 关联档案计数（维护 archive_count 冗余列） */
    int countRelatedArchives(@Param("facilityId") String facilityId, @Param("crzdbh") String crzdbh);

    /** 挑档案列表（只读查询，不要求调用方另有档案模块权限） */
    List<RelatedArchiveVO> selectArchivesForPick(@Param("facilityId") String facilityId,
                                                 @Param("crzdbh") String crzdbh,
                                                 @Param("keyword") String keyword,
                                                 @Param("limit") Integer limit);

    /** 档案是否存在（关联前校验） */
    int countArchiveById(@Param("archiveId") String archiveId);
}
