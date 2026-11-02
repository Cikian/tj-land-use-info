package org.jeecg.modules.land.stat.warning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.land.stat.warning.entity.ProcessWarningLevel;

import java.util.List;

/**
 * @Description: 分级预警 · 预警等级阈值 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>用注解 SQL 而不是 XML：本 Mapper 只有四五句简单查询、没有动态条件，
 * 注解写法少一个 XML 文件，也不会和别处的 mapper-locations 通配打架
 * （与 {@code ProcessConfigMapper} 同一取舍）。
 *
 * <p><b>★ 手写 SQL 必须自己带 {@code del_flag = 0}</b>：
 * {@code @TableLogic} 只对「实体查询」（{@code selectById} / {@code selectList(Wrapper)}）
 * 生效，注解 SQL 走的是原生语句，漏了条件就会把已删等级也查出来 ——
 * 后果是「管理端删掉了红色预警，重算却还在按它判级」，而且完全静默。
 * （AGENTS.md §3.4 记的就是这个坑。）
 *
 * <p><b>本 Mapper 没有缓存</b>：等级只有 4 行，每次重算查一次的成本可以忽略；
 * 缓存换来的「管理端改完还要等失效」是实实在在的投诉。
 */
@Mapper
public interface ProcessWarningLevelMapper extends BaseMapper<ProcessWarningLevel> {

    /** 等级列（别名与实体字段一一对应，避免依赖全局驼峰映射的隐式行为） */
    String COLUMNS = " id, level_name AS levelName, level_code AS levelCode, "
            + " min_days AS minDays, max_days AS maxDays, sort_no AS sortNo, remark, "
            + " create_by AS createBy, create_time AS createTime, "
            + " update_by AS updateBy, update_time AS updateTime, del_flag AS delFlag ";

    /**
     * 「排序号」的兜底排序片段（<b>不含 ORDER BY 关键字</b>，由调用方拼在前面，
     * 需要在它之前再叠一个排序键时不会出现两个 ORDER BY）。
     *
     * <p>为什么需要兜底：{@code sort_no} 列是 {@code NOT NULL DEFAULT 0}，
     * 管理端表单留空时前端按 0 提交，于是「没填排序号」和「排序号真的是 0」
     * 在库里是同一个值。若不兜底，新增的等级会排到蓝色预警前面 ——
     * 维护页与四色卡片的顺序会变成「乱序」，看起来像系统把配置吃掉了。
     * 兜底后语义明确：没填＝放最后，想排前面就填个正数。
     *
     * <p>注意：匹配算法不依赖这个顺序（{@code WarningLevelSupport#match}
     * 内部按 {@code min_days} 升序重排），所以兜底只影响展示顺序，不影响判级。
     */
    String SORT_BY_SORT = " CASE WHEN sort_no = 0 THEN 9999 ELSE sort_no END ASC, min_days ASC, id ASC ";

    /**
     * 全部启用中的等级，按「由轻到重」升序。
     *
     * <p>排序用 {@code sort_no}（0 兜底到最后）、{@code min_days} 兜底：
     * 维护页按 sort_no 展示（人配的顺序），而匹配算法内部还会按 min_days 再排一次
     * （见 {@code WarningLevelSupport#match}）。
     */
    @Select("SELECT " + COLUMNS + " FROM t_process_warning_level WHERE del_flag = 0"
            + " ORDER BY" + SORT_BY_SORT)
    List<ProcessWarningLevel> selectEnabled();

    /**
     * 全表（**含已删除**），仅用于排查与核对。
     *
     * <p>业务查询一律用 {@link #selectEnabled()}：把软删行混进列表或匹配，
     * 都会产生「看起来配置生效了、实际按旧行算」的问题。
     */
    @Select("SELECT " + COLUMNS + " FROM t_process_warning_level"
            + " ORDER BY del_flag ASC," + SORT_BY_SORT)
    List<ProcessWarningLevel> selectAll();

    /** 按主键取一条（不含已删）；不存在返回 null */
    @Select("SELECT " + COLUMNS + " FROM t_process_warning_level WHERE id = #{id} AND del_flag = 0")
    ProcessWarningLevel selectEnabledById(@Param("id") String id);

    /**
     * 软删（把 del_flag 置 1）。
     *
     * <p>不用 {@code deleteById}：{@code @TableLogic} 会把它变成 UPDATE，
     * 但那样「已删过再删」也会返回 1 行影响数，调用方分不清「真删了」还是「本来就没有」。
     * 自己写 UPDATE 带上 {@code del_flag = 0}，影响行数就是「本次真实删除数」。
     */
    @Update("UPDATE t_process_warning_level SET del_flag = 1, update_time = NOW() "
            + "WHERE id = #{id} AND del_flag = 0")
    int softDelete(@Param("id") String id);
}
