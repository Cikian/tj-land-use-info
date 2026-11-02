package org.jeecg.modules.land.stat.warning.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 分级预警 · 手动重算入参
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>{@code ptId} 为空 = <b>全量重算</b>（大屏端「重新计算」按钮）；
 * 有值 = 只重算该配套项目（环节进度保存后的钩子走这个分支）。
 *
 * <p><b>★ 为什么不用 {@code @RequestParam} 而单独定义 DTO</b>：
 * 契约 §5.4 定的是 {@code POST} + JSON body（{@code {"ptId": null}}）。
 * 用 body 而不是查询串，是为了以后要加「按环节重算」之类的参数时不破坏 URL 契约。
 * 请求体整个缺失也允许（前端「重新计算」按钮不带参数即可），
 * 由 Controller 的 {@code @RequestBody(required = false)} 兜住。
 */
@Data
public class WarningRecalcDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配套项目ID；为空表示全量重算 */
    private String ptId;
}
