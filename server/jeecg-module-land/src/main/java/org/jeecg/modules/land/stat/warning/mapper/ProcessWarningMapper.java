package org.jeecg.modules.land.stat.warning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.stat.warning.dto.WarningQueryDTO;
import org.jeecg.modules.land.stat.warning.entity.ProcessWarning;
import org.jeecg.modules.land.stat.warning.vo.WarningVO;

import java.util.List;
import java.util.Map;

/**
 * @Description: 分级预警 · 预警记录 Mapper（分页/汇总/按配套查/按配套删）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>查询与汇总用 XML（{@code mapper/xml/ProcessWarningMapper.xml}）：
 * 筛选条件是多选可组合的（分类 + 等级 + 行政区 + 三个模糊 + 关键词），
 * XML 里能一眼看全，也方便与前端契约逐条对照；简单句走 {@code BaseMapper}。
 *
 * <p><b>★ 本表没有 {@code del_flag}</b>：预警是「算出来的快照」，
 * 重算时该消失的行物理删除（见 {@link ProcessWarning} 类注释）。
 * 所以这里的分页/汇总语句<b>不需要</b>软删条件 —— 与等级表/意见表正好相反，
 * 别顺手「补一个 del_flag = 0」上去（会直接 SQL 报错）。
 *
 * <p><b>★ 为什么要 {@link #selectByPtId(String)} 而不是 {@code selectList(Wrapper)}</b>：
 * 重算要对「本轮该存在的行」和「库里现有的行」做差集，
 * 后者只需要 {@code id + 唯一键 + 创建人/创建时间}（更新时保留这两列），
 * 查全表 26 列纯属浪费；按配套查也天然走 {@code uk_pw_key} 的最左前缀。
 */
@Mapper
public interface ProcessWarningMapper extends BaseMapper<ProcessWarning> {

    // ==================================================================
    // 一、查询（list / summary / export 共用同一套 where 片段）
    // ==================================================================

    /** 分页（返回 {@code records/total/size/current}，前端按 jeecg 默认分页结构取） */
    IPage<WarningVO> selectWarningPage(Page<WarningVO> page, @Param("q") WarningQueryDTO q);

    /** 不分页明细（导出用；偏移量手写，因为导出可能一次拉两万行） */
    List<WarningVO> selectWarningList(@Param("q") WarningQueryDTO q,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /** 命中条数（导出前先数一次，超过上限就别浪费时间查明细） */
    long countWarnings(@Param("q") WarningQueryDTO q);

    /**
     * 按等级计数。
     *
     * @return 每行 {@code levelId / cnt}
     */
    List<Map<String, Object>> selectCountByLevel(@Param("q") WarningQueryDTO q);

    /**
     * 按分类计数。
     *
     * @return 每行 {@code yjType / cnt}
     */
    List<Map<String, Object>> selectCountByType(@Param("q") WarningQueryDTO q);

    /**
     * 命中条件下的最后计算时间（yyyy-MM-dd HH:mm:ss）；没有数据时返回 null。
     *
     * <p>取 MAX 而不是 MAX(update_time)：预警行的 {@code calc_time} 才是
     * 「这个数字是什么时候算的」，与用户关心的时效直接对应。
     */
    String selectLastCalcTime(@Param("q") WarningQueryDTO q);

    // ==================================================================
    // 二、重算（按配套查 / 更新 / 删除）
    // ==================================================================

    /**
     * 某个配套现有的全部预警行（只取差集需要的列）。
     *
     * <p>只查 {@code id / yj_type / pt_id / lc_id / create_by / create_time}：
     * 前者是差集与更新的定位键，后两者在更新时要原样保留
     * （重算不是「重建」，创建人/创建时间必须稳定，否则列表里的「录入人」会变成最后一次重算的人）。
     */
    List<ProcessWarning> selectByPtId(@Param("ptId") String ptId);

    /**
     * 更新一行预警（**显式覆盖全部业务列，含 null**）。
     *
     * <p><b>★ 为什么不用 {@code updateById}</b>：MyBatis-Plus 默认
     * 「null 字段不参与更新」，而重算恰恰会出现「字段变成 null」——
     * 例如决策意见被管理员删了、或某个阶段预警的 {@code problem_type} 本来就是 null。
     * 用 {@code updateById} 会出现「意见删了、预警上还留着旧建议」这种静默错数据。
     * 这里显式 SET 每一列，语义就是「这一行按本轮算出来的值整行覆盖」。
     */
    int updateWarning(ProcessWarning warning);

    /**
     * 物理删除某个配套的全部预警行（配套被移除时清理残留）。
     *
     * @return 删除行数
     */
    int deleteByPtId(@Param("ptId") String ptId);

    // ==================================================================
    // 三、重算的扫描范围与冗余列
    // ==================================================================

    /**
     * 全量重算的扫描范围：**存在环节进度记录**的配套项目。
     *
     * <p>为什么不是「全部 1433 条配套」：一条进度都没录的配套必然算不出任何预警
     * （三种口径都要环节起始时间），逐条扫只会让全量重算从「秒级」变成「分钟级」，
     * 而且回执里的 {@code scannedFacilities} 会虚高、让人误以为覆盖了全部配套。
     *
     * @return 配套项目ID 列表（去重）
     */
    List<String> selectDistinctPtIds();

    /**
     * 重算需要的冗余列（配套 × 宗地），一次取全，避免逐个配套查库（N+1）。
     *
     * <p>两张表的软删列名不同（AGENTS.md §3.3）：
     * {@code t_supporting_facilities.delFlag} 是驼峰 varchar '0'/'1'，
     * {@code t_land.del_flag} 是下划线 tinyint 0/1 —— 写错一个是「查不到」，
     * 写错另一个是「把已删宗地当在用」。
     *
     * <p>宗地用 LEFT JOIN 且不限 {@code crzdbh} 唯一：{@code t_land} 上有
     * {@code uk_land_crzdbh (crzdbh, del_flag)}，同一编号可能存在「已删 + 在用」两行，
     * 条件里带 {@code del_flag = 0} 才能拿到在用那条。
     * 关联不到宗地时宗地侧的列全为 null，由 Service 决定回落顺序。
     *
     * @return 每行 {@code ptId / ptName / crzdbh / facilityDkmc / facilityXzqh /
     *         landDkmc / landXzqh / xzqh2}
     */
    List<Map<String, Object>> selectFacilityInfos(@Param("ptIds") List<String> ptIds);
}
