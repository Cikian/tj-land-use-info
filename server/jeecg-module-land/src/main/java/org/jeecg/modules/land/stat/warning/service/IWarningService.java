package org.jeecg.modules.land.stat.warning.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.modules.land.stat.warning.dto.WarningQueryDTO;
import org.jeecg.modules.land.stat.warning.vo.RecalcResultVO;
import org.jeecg.modules.land.stat.warning.vo.WarningOptionsVO;
import org.jeecg.modules.land.stat.warning.vo.WarningSummaryVO;
import org.jeecg.modules.land.stat.warning.vo.WarningVO;

/**
 * @Description: 分级预警 · 查询 / 汇总 / 下拉 / 重算 / 导出
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>对应契约 §5.1 ~ §5.5 的五个接口。五个方法的分工：
 * <ul>
 *   <li>{@link #list} / {@link #summary} / {@link #export} —— <b>只读</b>，
 *       直接查 {@code t_process_warning} 的快照行，不做任何计算；</li>
 *   <li>{@link #options} —— 下拉选项，等级来自等级表、问题类型来自字典；</li>
 *   <li>{@link #recalc} —— <b>唯一的写入口</b>，三种口径的算法都在这里。</li>
 * </ul>
 *
 * <p><b>★ 为什么不做定时任务（契约 §3.9）</b>：预警的本质是「按当前数据算出来的快照」，
 * 库里没有事件流可以增量推导。定时重算只会让「列表里的数字」和
 * 「最后一次重算时刻」更难解释 —— 用户看到 37 条预警，无法判断是数据变了
 * 还是刚好到了重算时刻。所以只保留「手动重算」与「保存环节进度后重算」两个触发点。
 */
public interface IWarningService {

    /**
     * 预警记录分页（契约 §5.1）。
     *
     * @param query 筛选条件（分页参数在此收敛：pageNo 从 1 起、pageSize 上限 200）
     * @return jeecg 默认分页结构（{@code records/total/size/current}）
     */
    IPage<WarningVO> list(WarningQueryDTO query);

    /**
     * 按等级 / 分类汇总（契约 §5.2）。
     *
     * <p>{@code byLevel} 会补齐 count = 0 的等级并按 sort_no 升序 ——
     * 大屏端四色卡片缺一档就会跳色。
     */
    WarningSummaryVO summary(WarningQueryDTO query);

    /** 下拉选项（契约 §5.3）：等级 + 分类 + 问题类型 */
    WarningOptionsVO options();

    /**
     * 重算预警（契约 §3.8 / §5.4）。
     *
     * <p>口径：本轮应当存在的预警集合 S = 环节预警 × n + 阶段预警 × m + 总体预警 × 0或1；
     * 对 S 逐条按 {@code (pt_id, yj_type, lc_id)} upsert，库里存在但不在 S 里的行删除。
     * 因此本方法是<b>幂等</b>的：同一份数据连算两次，第二次只会有 updated、行数不变。
     *
     * @param ptId 配套项目ID；<b>null / 空串 = 全量</b>（只扫「存在环节进度记录」的配套）
     * @return 重算回执（created / updated / removed / skipped 等，默认全 0 不是 null）
     */
    RecalcResultVO recalc(String ptId);

    /**
     * 导出 xlsx 工作簿（契约 §5.5）。
     *
     * <p>返回 {@link Workbook} 而不是直接写响应流：Service 层不感知
     * {@code HttpServletResponse}，控制器负责 Content-Disposition 与文件名 ——
     * 与 {@code stat/export/StatQueryExportServiceImpl} 的分工一致。
     *
     * @param query 筛选条件（分页参数忽略，但受导出行数上限保护）
     * @return 只含一个 sheet 的工作簿；调用方负责 close
     */
    Workbook export(WarningQueryDTO query);
}
