package org.jeecg.modules.land.stat.warning.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 预警等级 · 维护页与下拉共用（合同 §5.6）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>为什么实体 {@code ProcessWarningLevel} 之外还要一个 VO：
 * <ul>
 *   <li>维护页只暴露「名称/别名/区间/排序/备注」，审计列与 del_flag 不给前端 ——
 *       前端一旦能传 delFlag，就会出现「保存时顺手把它置 1」这类事故；</li>
 *   <li>大屏端下拉也要这几个字段，用它比用实体更窄、更稳定。</li>
 * </ul>
 *
 * <p><b>★ id 为空表示新增</b>；保存时由 Service 判断（合同 §5.6 第 1 条）。
 */
@Data
public class WarningLevelVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 等级ID；为空 = 新增 */
    private String id;

    /** 等级名称：蓝色预警 / 黄色预警 / 橙色预警 / 红色预警 */
    private String levelName;

    /** 等级别名（前端着色令牌）：blue / yellow / orange / red */
    private String levelCode;

    /** 区间下界（含） */
    private Integer minDays;

    /** 区间上界（含） */
    private Integer maxDays;

    /** 排序号（升序＝由轻到重） */
    private Integer sortNo;

    /** 备注（处置口径提示） */
    private String remark;
}
