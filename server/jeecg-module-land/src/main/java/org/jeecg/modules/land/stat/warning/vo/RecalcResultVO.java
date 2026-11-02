package org.jeecg.modules.land.stat.warning.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 分级预警 · 手动重算结果回执（合同 §4 / §5.4）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>为什么重算要回执而不是「成功」两个字：重算的输入是当前库里的数据，
 * 用户点完之后一定会问「到底算了多少、为什么数字没变」。回执把这几个数摊开：
 * <ul>
 *   <li>{@code scannedFacilities / scannedProcesses} —— 扫描范围。
 *       {@code scannedProcesses = 0} 时看到「created=0」是正确的（还没人录环节进度），
 *       而不是算法坏了；</li>
 *   <li>{@code skippedNoStart / skippedNoPlan} —— 因为「未开启」「没有标准时长」
 *       被跳过的环节数，用来解释「为什么这个阶段没预警」；</li>
 *   <li>{@code levelMissing} —— 等级表一条都没配（此时所有逾期都生成不出预警），
 *       提示管理员先去「预警等级维护」配阈值；</li>
 *   <li>{@code elapsedMs} —— 全量重算的耗时，方便判断手动重算能不能常态化点。</li>
 * </ul>
 *
 * <p><b>★ created / updated / removed 是幂等性的直接证据</b>：
 * 同一份数据连续重算两次，第二次应当是 {@code created=0, removed=0,
 * updated=行数}，且总行数不变（合同 §8 验收第 4 条）。
 */
@Data
public class RecalcResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次扫描的配套项目数（全量口径下 = 存在环节进度记录的配套数） */
    private Integer scannedFacilities = 0;

    /** 本次扫描的环节进度行数 */
    private Integer scannedProcesses = 0;

    /** 新生成的预警行数 */
    private Integer created = 0;

    /** 被更新的预警行数（等级/预警值/意见/calc_time 变了，或只是重算了一次） */
    private Integer updated = 0;

    /** 被删除的预警行数（不再逾期、等级匹配不到、配套已移除） */
    private Integer removed = 0;

    /** 因「环节未开启」（lckssj 为空）而跳过的环节数 */
    private Integer skippedNoStart = 0;

    /** 因「拿不到预计结束时间且无法推算」而跳过的环节数（正常数据下为 0） */
    private Integer skippedNoPlan = 0;

    /** 等级表为空：逾期也生成不出预警，需要先去维护等级阈值 */
    private Boolean levelMissing = Boolean.FALSE;

    /** 耗时（毫秒） */
    private Long elapsedMs;

    /** 计算时间（yyyy-MM-dd HH:mm:ss） */
    private String calcTime;
}
