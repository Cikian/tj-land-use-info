package org.jeecg.modules.land.stat.warning.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 决策意见 · 维护页一行（合同 §5.7）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p><b>★ {@code problemType} 为空串表示「通用（所有问题类型）」</b>，
 * 前端在问题类型下拉里就是这么展示的。注意：这不是 null ——
 * 表列是 {@code NOT NULL DEFAULT ''}，判重与匹配都用 `= ''`。
 *
 * <p>{@code levelName} 是库里的冗余列，新增/编辑时由 Service 按 levelId 回填，
 * 前端**不需要**传（传了也会被覆盖）：让前端自己拼等级名，
 * 一旦改了下拉数据就会存进过期名称。
 */
@Data
public class DecisionOpinionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 决策意见ID；为空 = 新增 */
    private String id;

    /** 问题类型；空串 = 通用兜底意见 */
    private String problemType;

    /** 预警等级ID */
    private String levelId;

    /** 预警等级名称（冗余，服务端回填） */
    private String levelName;

    /** 决策意见正文（≤1000 字） */
    private String opinion;

    /** 排序号 */
    private Integer sortNo;
}
