package org.jeecg.modules.land.stat.query.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.stat.query.dto.ResolvedCondition;
import org.jeecg.modules.land.stat.query.dto.StatQueryDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @Description: 查询统计 · Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>全部为手写 SQL（XML），原因见 {@code StatQueryMapper.xml} 文件头：
 * ① 条件是多值 + 可选的组合，QueryWrapper 拼出来可读性差；
 * ② 两张表的软删列名不同（{@code del_flag} / {@code delFlag}），
 *    {@code @TableLogic} 只对实体查询生效，手写 SQL 必须自己带；
 * ③ 统计口径要能一行行对着旧系统核对，SQL 里看得见比 Java 里拼得出来更重要。
 *
 * <p>{@code conds} 是已经在 Service 里过完白名单的结构化条件
 * （见 {@code ResolvedCondition}），XML 里 {@code ${column}} 不含任何用户输入。
 */
@Mapper
public interface StatQueryMapper {

    // ==================================================================
    // 一、经营性用地（t_land）
    // ==================================================================

    /** 命中宗地数 */
    long countLand(@Param("q") StatQueryDTO q, @Param("conds") List<ResolvedCondition> conds);

    /** 命中宗地的配套费合计（SUM(nrcbdptf)，空值不计） */
    BigDecimal sumLandFee(@Param("q") StatQueryDTO q, @Param("conds") List<ResolvedCondition> conds);

    /** 明细行（只查默认列 + 自定义列需要的物理列，全量列由前端按需取） */
    List<Map<String, Object>> selectLandRows(@Param("q") StatQueryDTO q,
                                             @Param("conds") List<ResolvedCondition> conds,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    /**
     * 统计用事实行：命中宗地下的全部配套（去重与漏斗在 Java 侧算）。
     *
     * <p>为什么不在 SQL 里去重：旧系统是按 {@code ptxmmc} 建 TreeSet 去重的
     * （同一条路挂多宗地只算 1 个），且各环节是漏斗（上一环节「非否」才进下一环节）。
     * 这种「逐级收敛」用 SQL 写得出来但极难核对，所以取事实行在 Java 里算，
     * 与旧代码逐行对齐。
     */
    List<Map<String, Object>> selectLandFacilityFacts(@Param("q") StatQueryDTO q,
                                                      @Param("conds") List<ResolvedCondition> conds);

    /** 某个宗地下的配套明细（「地块配套详情」用，不受查询条件影响） */
    List<Map<String, Object>> selectFacilitiesByLand(@Param("crzdbh") String crzdbh);

    // ==================================================================
    // 二、配套项目（t_supporting_facilities）
    // ==================================================================

    long countFacility(@Param("q") StatQueryDTO q, @Param("conds") List<ResolvedCondition> conds);

    List<Map<String, Object>> selectFacilityRows(@Param("q") StatQueryDTO q,
                                                 @Param("conds") List<ResolvedCondition> conds,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    /** 配套口径的指标（不去重、不漏斗，一次聚合出全部数字） */
    Map<String, Object> selectFacilitySummary(@Param("q") StatQueryDTO q,
                                              @Param("conds") List<ResolvedCondition> conds);

    /**
     * 单条配套的全部业务字段（「配套项目详情」用）。
     *
     * <p>为什么不复用数据管理的 {@code /land/data/facilityAdmin/detail}：
     * 那个接口挂的是 {@code land:data:facility} 权限，而查询统计页刻意不挂权限
     * （见 StatQueryController 类注释）。若详情借用录入接口，没被授权录入的角色
     * 会在「点开某一行」时 403 —— 查询页看得到、点不开，比看不到更难受。
     */
    Map<String, Object> selectFacilityDetailById(@Param("id") String id);

    // ==================================================================
    // 三、下拉候选值（旧 queryFieldContent 的去重值清单）
    // ==================================================================

    List<String> selectLandDkmcOptions(@Param("limit") int limit);

    List<String> selectLandXzqhOptions(@Param("limit") int limit);

    List<String> selectLandSrrOptions(@Param("limit") int limit);

    /** 出让年份（倒序），用于「出让时间」下拉 */
    List<String> selectLandCrsjYears(@Param("limit") int limit);

    List<String> selectFacilityPtxmmcOptions(@Param("limit") int limit);

    List<String> selectFacilityPtsslbOptions(@Param("limit") int limit);

    List<String> selectFacilityXzqhOptions(@Param("limit") int limit);

    /** 资金落实情况（已落实 / 未落实 …）—— 值取自库里实际出现的值，不写死枚举 */
    List<String> selectFacilityZjlsqkOptions(@Param("limit") int limit);
}
