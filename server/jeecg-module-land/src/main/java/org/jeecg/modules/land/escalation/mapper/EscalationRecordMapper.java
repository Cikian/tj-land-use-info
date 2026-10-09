package org.jeecg.modules.land.escalation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.escalation.entity.EscalationRecord;

import java.util.Date;
import java.util.List;

/**
 * @Description: 提级论证审核意见 / 办理记录 Mapper（append-only）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>本 Mapper <b>只提供查询与写入</b>：
 * <ul>
 *   <li>{@link #selectByProjectId} —— 意见列表（时间倒序，服务 2.3.3 第 4 项「可批注审核意见」）；</li>
 *   <li>{@link #countByProjectId} —— 冗余计数 {@code record_count} 的唯一口径；</li>
 *   <li>{@link #updateLatestOpinion} —— 登记意见后回写主表摘要（窄 UPDATE，
 *       只碰 {@code latest_opinion*} / {@code record_count}，可选带上 {@code status}）。</li>
 * </ul>
 * 意见记录本身<b>没有</b> update / delete 方法（继承的 BaseMapper 方法在 Service 层不被调用，
 * 且对外不暴露 edit / delete 接口）——这是「可追溯」的技术保证。
 */
public interface EscalationRecordMapper extends BaseMapper<EscalationRecord> {

    /** 某项目的意见记录列表（时间倒序：最新的排最前） */
    List<EscalationRecord> selectByProjectId(@Param("projectId") String projectId);

    /** 某项目的意见记录数（主表 {@code record_count} 以此为准重算，不做 +1 累加） */
    int countByProjectId(@Param("projectId") String projectId);

    /**
     * 回写主表「最新审核意见」三列 + 记录数，可选同步办理状态。
     *
     * <p><b>为什么不用 {@code updateById} 写实体</b>：主表实体有 30+ 字段，
     * 读出来再写回既慢又容易把并发写入的其它字段覆盖掉（比如刚保存的论证结果）。
     * 这里用一条窄 UPDATE，只碰需要改的列。
     *
     * @param projectId         项目ID
     * @param latestOpinion     最新意见摘要（已按 {@code latest_opinion} 列容量截断）
     * @param latestOpinionTime 最新意见时间
     * @param latestOpinionBy   最新意见记录人（姓名）
     * @param recordCount       重算后的意见记录数
     * @param status            可选：同时更新办理状态；为空则不动该列
     * @param updateBy          更新人（当前登录账号）
     * @param updateTime        更新时间
     */
    int updateLatestOpinion(@Param("projectId") String projectId,
                            @Param("latestOpinion") String latestOpinion,
                            @Param("latestOpinionTime") Date latestOpinionTime,
                            @Param("latestOpinionBy") String latestOpinionBy,
                            @Param("recordCount") Integer recordCount,
                            @Param("status") String status,
                            @Param("updateBy") String updateBy,
                            @Param("updateTime") Date updateTime);
}
