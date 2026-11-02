package org.jeecg.modules.land.stat.warning.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.land.stat.warning.vo.DecisionOpinionVO;

/**
 * @Description: 分级预警 · 决策意见维护（管理端「统计预警 → 决策意见维护」）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>对应契约 §5.7。语义是「问题类型 × 预警等级 → 处理建议」的配置表，
 * 重算时按「精确 → 通用兜底 → 无意见」三级顺序取值（见 {@code DecisionOpinionMapper}）。
 *
 * <p><b>★ 保存校验（服务端做，前端拦不住直接调接口）</b>：
 * <ul>
 *   <li>{@code levelId} 必须存在且 {@code del_flag = 0} —— 指向已删等级的意见永远不会被用到，
 *       存进去等于一条静默无效配置；</li>
 *   <li>{@code opinion} 非空且 ≤ 1000 字（与表列 {@code varchar(1000)} 一致；
 *       不截断而是报错：话术被截掉半句比报错更糟）；</li>
 *   <li>同一 {@code (problemType, levelId)} 只允许一条 —— 重复时报「已存在，请直接编辑」，
 *       而不是让用户困惑「为什么我改了没生效」（实际改的是另一行）。</li>
 * </ul>
 */
public interface IDecisionOpinionService {

    /**
     * 分页查询决策意见。
     *
     * @param levelId     等级ID（空 = 全部）
     * @param problemType 问题类型；<b>null = 全部，空串 = 只看通用意见</b>
     *                    （两种语义必须区分开，详见 Controller 与 Mapper 注释）
     * @param pageNo      页码，从 1 起
     * @param pageSize    每页条数
     */
    IPage<DecisionOpinionVO> list(String levelId, String problemType, Integer pageNo, Integer pageSize);

    /**
     * 新增或编辑决策意见。
     *
     * @param vo id 为空 = 新增；不为空 = 编辑
     * @return 保存后的意见 id
     */
    String save(DecisionOpinionVO vo);

    /** 软删决策意见（历史预警里已填进去的意见正文不受影响，它是快照） */
    void delete(String id);
}
