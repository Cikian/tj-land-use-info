package org.jeecg.modules.land.data.service;

import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * @Description: 数据管理 · 数据更新与移除（软删 / 恢复 / 变更留痕）Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）第 6 项「数据更新与移除」。
 * 旧实现：{@code xjKjkfbCommercialLandController.updateItem / importData} 里的软删分支
 * + {@code xj_kjkfb_operationrecord} 日志表。
 *
 * <p><b>★ 三块能力，各解决一个旧系统的具体问题</b>：
 * <ol>
 *   <li><b>移除（软删）</b>：旧系统用字符串拼 SQL 把 {@code delFlag} 改成 '1'，
 *       且<b>没有回收站</b> —— 误删之后只能去数据库里手工改回来。
 *       本实现提供可查、可恢复的回收站。</li>
 *   <li><b>恢复</b>：恢复前<b>必须做冲突检测</b>。{@code t_land} 的唯一键是
 *       {@code (crzdbh, del_flag)}，同一编号最多一条有效 + 一条已删；
 *       「移除 A → 重新录入同编号 B → 再恢复 A」会直接撞唯一键。
 *       不检测就等于把数据库异常甩给用户看。</li>
 *   <li><b>变更留痕</b>：字段级履历的查询面（写入在 ChangeLogSupport）。</li>
 * </ol>
 */
public interface IDataRecycleService {

    // ==================================================================
    // 一、回收站
    // ==================================================================

    /**
     * 回收站列表（宗地 + 配套项目合并）。
     *
     * @param bizType 业务类型：land / facility / null(全部)
     * @param keyword 宗地编号 / 名称 模糊
     */
    List<Map<String, Object>> queryRecycleList(String bizType, String keyword);

    /** 回收站概览计数（页面顶部卡片） */
    Map<String, Object> queryRecycleSummary();

    // ==================================================================
    // 二、恢复
    // ==================================================================

    /**
     * 恢复一条数据。
     *
     * @param bizType land / facility
     * @param id      主键
     * @return 含 {@code success} / {@code message} / {@code warning} 的结果
     */
    Map<String, Object> restore(String bizType, String id, HttpServletRequest request);

    /** 批量恢复 */
    Map<String, Object> restoreBatch(String bizType, List<String> ids, HttpServletRequest request);

    // ==================================================================
    // 三、变更留痕
    // ==================================================================

    /** 分页查询变更留痕（可按业务类型 / 动作 / 操作人 / 关键字筛） */
    Map<String, Object> queryChangeLogPage(String bizType, String action, String operator,
                                           String keyword, Integer pageNo, Integer pageSize);

    /** 某业务对象的完整履历（含字段级明细） */
    List<DataChangeLog> queryHistory(String bizType, String bizId, Integer limit);

    /** 变更动作分布（页面计数卡） */
    List<Map<String, Object>> queryActionDistribution(String bizType);

    /** 可选的动作清单（下拉，含中文名） */
    List<Map<String, String>> queryActionOptions();
}
