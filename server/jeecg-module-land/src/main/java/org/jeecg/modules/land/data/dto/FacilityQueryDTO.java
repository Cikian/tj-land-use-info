package org.jeecg.modules.land.data.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Description: 数据管理 - 配套项目列表查询条件（在通用条件之上补「功能区」）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-16
 * @Version V1.0
 *
 * <p><b>★ 为什么另起一个类而不是往 {@link LandAdminQueryDTO} 里加 {@code gnq}</b>：
 * 那个类是「宗地 / 配套列表共用」的（见它的类注释），而 {@code gnq}（功能区）只有配套表
 * 与配套侧统计有这一列，宗地表没有。加在共用类里，会让宗地列表的查询条件看起来
 * 「支持功能区过滤」，实际点了没效果 —— 这类「界面有、后端不理」的条件最难自查。
 * 继承而不是复制：其余条件的字段名、精确/模糊口径与宗地列表完全一致，
 * 复制一份将来必然漂移（`@Data` 的 equals/hashCode 也跟着 callSuper）。
 *
 * <p><b>★ 参数风格</b>：与 {@link LandAdminQueryDTO} 一致 —— <b>裸字段名、无下划线、无
 * {@code @JsonProperty}</b>，由 Spring MVC 按 JavaBean 属性名绑定 GET 查询串，
 * 因此前端传 {@code gnq=生态城} 即可；不做多值（逗号分隔 / 数组）。
 * 这是跟现有 {@code xzqh}（String、精确匹配）对齐的结果 ——
 * 列表页的行政区划就是一个下拉、单值，功能区同理；多值检索的场景在
 * {@code stat.query} 的 {@code StatQueryDTO#gnq}（{@code List<String>}）里，
 * 那是查询统计页签，不是本列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FacilityQueryDTO extends LandAdminQueryDTO {

    private static final long serialVersionUID = 1L;

    /**
     * 功能区（精确匹配：生态城 / 土地发展中心 / 经开区 / 高新区 / 保税区 …）。
     *
     * <p>★ 与 {@code xzqh} 是两个独立条件：2026-11 把功能区从行政区划里拆出来以后
     * （见 {@code sql/data/14_facility_gnq_and_dict.sql}），只给 {@code xzqh}
     * 加检索会让库里那批功能区记录「录得进、查不到」。
     */
    private String gnq;

    /** 配套设施类别（精确匹配，对应 t_supporting_facilities.ptsslb）。 */
    private String ptsslb;
}
