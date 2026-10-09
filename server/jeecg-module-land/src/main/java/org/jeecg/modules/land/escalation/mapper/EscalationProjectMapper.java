package org.jeecg.modules.land.escalation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.escalation.dto.EscalationQueryDTO;
import org.jeecg.modules.land.escalation.entity.EscalationProject;
import org.jeecg.modules.land.escalation.vo.EscalationLedgerVO;
import org.jeecg.modules.land.escalation.vo.EscalationStatVO;

import java.util.List;

/**
 * @Description: 提级论证项目主表 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>列表 / 台账 / 统计 / 导出<b>共用 XML 里的同一段 {@code queryWhere}</b>，
 * 保证「列表里看到什么，台账、统计与导出就是什么」。
 *
 * <p>台账直接读主表的冗余列（{@code material_count} / {@code latest_opinion}），
 * <b>不 JOIN 材料表与意见表</b>：旧设施表与大表都在这个库里，
 * 台账是高频页面，能免子查询就免（设计文档 3.2 第 7 条）。
 */
public interface EscalationProjectMapper extends BaseMapper<EscalationProject> {

    /**
     * 项目分页查询（录入列表 / 查询统计共用）。
     *
     * @param page MyBatis-Plus 分页对象
     * @param q    查询条件
     */
    IPage<EscalationProject> selectProjectPage(Page<EscalationProject> page, @Param("q") EscalationQueryDTO q);

    /** 项目详情（不含材料与意见，子表由 Service 组装） */
    EscalationProject selectDetailById(@Param("id") String id);

    /** 导出用：不分页地取出全部命中项目 */
    List<EscalationProject> selectForExport(@Param("q") EscalationQueryDTO q);

    /**
     * 台账分页查询（14 列所需的窄视图）。
     *
     * <p>「序号」列不由 SQL 生成（MySQL 5.7 没有 ROW_NUMBER()，用户变量又会被分页插件的
     * COUNT 优化搅乱），由 Service 用「分页偏移 + 行号」赋值。
     *
     * @param page MyBatis-Plus 分页对象
     * @param q    查询条件（与列表同一套）
     */
    IPage<EscalationLedgerVO> selectLedgerPage(Page<EscalationLedgerVO> page,
                                              @Param("q") EscalationQueryDTO q);

    /** 台账导出用：不分页取出全部命中项目 */
    List<EscalationLedgerVO> selectLedgerForExport(@Param("q") EscalationQueryDTO q);

    /** 当前条件下的项目总数（统计图的分母） */
    long countByQuery(@Param("q") EscalationQueryDTO q);

    /** 按论证结果统计（饼图；结果为空的归为「未登记」） */
    List<EscalationStatVO.NameCount> selectStatByArgResult(@Param("q") EscalationQueryDTO q);

    /** 按申报单位统计（条形图） */
    List<EscalationStatVO.NameCount> selectStatByDeclareDept(@Param("q") EscalationQueryDTO q);

    /** 按行政区划统计（条形图） */
    List<EscalationStatVO.NameCount> selectStatByXzqh(@Param("q") EscalationQueryDTO q);

    /** 按申报月份统计（折线图，name 形如 2026-09） */
    List<EscalationStatVO.NameCount> selectStatByMonth(@Param("q") EscalationQueryDTO q);

    /** 按项目类型统计（环形图） */
    List<EscalationStatVO.NameCount> selectStatByProjectType(@Param("q") EscalationQueryDTO q);

    /**
     * 按办理状态统计（台账顶部 tab 的角标）。
     *
     * <p>只返回库里实际存在的状态，数量为 0 的状态由 Service 补齐成 4 项。
     */
    List<EscalationLedgerVO.StatusCount> selectCountGroupByStatus(@Param("q") EscalationQueryDTO q);

    /**
     * 项目编号重复校验（唯一键 {@code uk_esc_no} 是 project_no <b>单列</b>）。
     *
     * @param projectNo 编号
     * @param excludeId 编辑时排除自身，可为空
     */
    int countByProjectNo(@Param("projectNo") String projectNo, @Param("excludeId") String excludeId);

    /**
     * 取某年度已用掉的最大流水号（项目编号自动生成用）。
     *
     * @param prefix 形如 {@code TJ-2026-}
     * @return 最大流水号；没有记录时返回 null
     */
    Integer selectMaxSeqOfYear(@Param("prefix") String prefix);
}
