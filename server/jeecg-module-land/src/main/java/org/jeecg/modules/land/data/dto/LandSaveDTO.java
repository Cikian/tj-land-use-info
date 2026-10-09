package org.jeecg.modules.land.data.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.modules.land.data.entity.Land;

import java.io.Serializable;

/**
 * @Description: 经营性用地信息录入 - 新增/编辑提交体
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 为什么继承 {@link Land} 而不是逐个字段列一遍</b>：
 * 宗地有 34 个业务字段。再抄一遍就是 34 行 getter/setter + 34 行「DTO→实体」映射，
 * 而这份映射<b>没有任何业务规则</b>（就是名字对名字）——纯粹的重复。
 *
 * <p><b>★ 那为什么还要这个 DTO，不直接用实体接参</b>：
 * 因为要挡住「客户端自己传 id / delFlag / createTime」。
 * 直接用实体接 {@code @RequestBody} 时，前端只要在 JSON 里塞一个
 * {@code "delFlag": 0} 或 {@code "id": "别人的id"}，就可能改到不该改的行 ——
 * 这是典型的越权写入。本 DTO 继承全部字段（便于接参），
 * 但 Service 侧<b>显式逐字段赋值</b>，id 与审计字段一律由服务端决定，
 * 客户端传什么都不影响。
 *
 * <p>{@code removeReason} 不参与落库，只在批量移除时作为留痕原因使用。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class LandSaveDTO extends Land implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 移除原因（仅移除操作使用，不算宗地字段） */
    private String removeReason;
}
