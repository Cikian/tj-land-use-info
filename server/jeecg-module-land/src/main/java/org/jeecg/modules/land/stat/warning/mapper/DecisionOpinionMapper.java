package org.jeecg.modules.land.stat.warning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.land.stat.warning.entity.DecisionOpinion;
import org.jeecg.modules.land.stat.warning.vo.DecisionOpinionVO;

import java.util.List;

/**
 * @Description: 分级预警 · 决策意见 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>三个查询对应契约 §3.7 的匹配顺序：
 * <pre>
 *   ① selectByTypeAndLevel(problemType, levelId)  精确匹配
 *   ② selectByLevel(levelId)                      通用兜底（problem_type = ''）
 *   ③ 都没有                                       返回 null，前端显示「—」
 * </pre>
 * 旧系统只有 ①，没配到的格子永远是空的（5 类 × 4 级 = 20 格漏一格就是一处静默失效）。
 *
 * <p><b>★ 手写 SQL 一律带 {@code del_flag = 0}</b>（AGENTS.md §3.4）：
 * {@code @TableLogic} 不作用于注解 SQL，漏了就会拿已删的意见去填预警。
 *
 * <p><b>★ {@code problem_type} 用 {@code = ''} 而不是 {@code IS NULL} 判通用</b>：
 * 表列是 {@code NOT NULL DEFAULT ''}，历史数据与种子都是空串；
 * 写成 IS NULL 会一条都匹配不到。
 */
@Mapper
public interface DecisionOpinionMapper extends BaseMapper<DecisionOpinion> {

    /** 意见列（别名与实体/VO 字段对应） */
    String COLUMNS = " id, problem_type AS problemType, level_id AS levelId, "
            + " level_name AS levelName, opinion, sort_no AS sortNo, "
            + " create_by AS createBy, create_time AS createTime, "
            + " update_by AS updateBy, update_time AS updateTime, del_flag AS delFlag ";

    /**
     * 精确匹配：某个问题类型在某个等级下的意见。
     *
     * <p>{@code LIMIT 1}：保存时已保证 (problem_type, level_id) 唯一，
     * 但历史数据可能有多条（旧库没有唯一约束），此时取 sort_no 最小的那条，
     * 至少结果稳定、可解释。
     */
    @Select("SELECT " + COLUMNS + " FROM t_decision_opinion "
            + "WHERE del_flag = 0 AND level_id = #{levelId} AND problem_type = #{problemType} "
            + "ORDER BY sort_no ASC, id ASC LIMIT 1")
    DecisionOpinion selectByTypeAndLevel(@Param("problemType") String problemType,
                                        @Param("levelId") String levelId);

    /**
     * 通用兜底：某个等级下 {@code problem_type = ''} 的那条意见。
     *
     * <p>精确匹配不到时用它，避免「中心只配了通用意见，所有环节预警却都没有建议」。
     */
    @Select("SELECT " + COLUMNS + " FROM t_decision_opinion "
            + "WHERE del_flag = 0 AND level_id = #{levelId} AND problem_type = '' "
            + "ORDER BY sort_no ASC, id ASC LIMIT 1")
    DecisionOpinion selectByLevel(@Param("levelId") String levelId);

    /**
     * 全部启用意见（重算时一次取全，按 (问题类型|等级) 在内存里建索引）。
     *
     * <p>为什么不在匹配时逐条查库：一次重算要处理几十个环节 + 6 个阶段，
     * 每条都查一次意见表就是几十次往返；意见表只有个位数行，一次取全更划算，
     * 也避免「同一批数据里前后两次查询结果不一致」。
     */
    @Select("SELECT " + COLUMNS + " FROM t_decision_opinion "
            + "WHERE del_flag = 0 ORDER BY sort_no ASC, id ASC")
    List<DecisionOpinion> selectAllEnabled();

    /**
     * 维护页分页查询。
     *
     * <p>{@code problemType} 的三种传参语义（前端约定，见 Controller 注释）：
     * <ul>
     *   <li>{@code null} —— 不按问题类型过滤（全部）；</li>
     *   <li>{@code ""} —— 只看通用意见（{@code problem_type = ''}）。</li>
     * </ul>
     * 所以判断条件必须是 {@code problemType != null} 而不是 {@code != ''} ——
     * 后者会让「只看通用」这个筛选项永远查不到东西。
     */
    @Select("<script>"
            + "SELECT id, problem_type AS problemType, level_id AS levelId, "
            + " level_name AS levelName, opinion, sort_no AS sortNo "
            + "FROM t_decision_opinion WHERE del_flag = 0 "
            + "<if test='levelId != null and levelId != \"\"'> AND level_id = #{levelId} </if>"
            + "<if test='problemType != null'> AND problem_type = #{problemType} </if>"
            + "ORDER BY sort_no ASC, level_id ASC, problem_type ASC, id ASC"
            + "</script>")
    IPage<DecisionOpinionVO> selectOpinionPage(Page<DecisionOpinionVO> page,
                                               @Param("levelId") String levelId,
                                               @Param("problemType") String problemType);

    /** 软删（同 {@code ProcessWarningLevelMapper#softDelete}：影响行数即本次真实删除数） */
    @Update("UPDATE t_decision_opinion SET del_flag = 1, update_time = NOW() "
            + "WHERE id = #{id} AND del_flag = 0")
    int softDelete(@Param("id") String id);
}
