package org.jeecg.modules.land.stat.warning.service;

import org.jeecg.modules.land.stat.warning.vo.WarningLevelVO;

import java.util.List;

/**
 * @Description: 分级预警 · 预警等级维护（管理端「统计预警 → 预警等级维护」）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>对应契约 §5.6。只有三个动作：列表、保存（新增/编辑同一条路径）、软删。
 *
 * <p><b>★ 保存校验是这块的核心价值</b>：等级区间是算法的输入，
 * 配错了不会报错，只会「所有预警都变成蓝色」或者「逾期 25 天没有预警」。
 * 所以四类校验必须都在服务端做（前端校验拦不住直接调接口）：
 * <ol>
 *   <li>{@code levelName} / {@code levelCode} 非空，{@code levelCode} 唯一（大小写不敏感）；</li>
 *   <li>{@code 0 <= minDays <= maxDays}；</li>
 *   <li>编辑时 id 必须存在（否则会「保存成功但什么都没改」）；</li>
 *   <li><b>区间不得与其它生效等级重叠</b> —— 重叠会让匹配产生歧义
 *       （逾期 12 天同时落在蓝 1-10 与黄 10-20 里，结果取决于扫描顺序），
 *       报错时要给出具体冲突的等级名，否则用户不知道该改哪一条。</li>
 * </ol>
 *
 * <p><b>★ 保存后不刷新任何缓存</b>：等级不带进程内缓存，下一次匹配立刻生效（契约 §5.6 第 5 条）。
 */
public interface IWarningLevelService {

    /** 全部启用中的等级，按 sortNo 升序（维护页与下拉共用） */
    List<WarningLevelVO> list();

    /**
     * 新增或编辑等级。
     *
     * @param vo id 为空 = 新增；不为空 = 编辑（id 必须存在）
     * @return 保存后的等级 id（新增时是新生成的 id，前端可直接拿去刷新）
     */
    String save(WarningLevelVO vo);

    /**
     * 软删等级。
     *
     * <p>软删而不是物理删：等级 id 被 {@code t_process_warning.level_id} 与
     * {@code t_decision_opinion.level_id} 引用着，物理删会让历史行变成孤儿。
     *
     * @param id 等级ID
     */
    void delete(String id);
}
