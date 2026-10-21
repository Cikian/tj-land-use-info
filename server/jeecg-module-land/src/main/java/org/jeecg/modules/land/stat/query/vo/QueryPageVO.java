package org.jeecg.modules.land.stat.query.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Description: 查询统计 · 分页结果（行 + 列元数据）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-21
 * @Version V1.0
 *
 * <p>为什么把「列元数据」和「数据行」放在同一个响应里：
 * 旧系统的结果表列是由后端字段定义接口（{@code getCommercialLandField} /
 * {@code getCustomCommercialLandField}）单独下发的，前端要发两次请求、
 * 而且两次请求之间用户可能已经改了自定义列 —— 列和数据对不上时表格会错位。
 * 一次请求同时拿到「这 5 列是什么」和「这 5 列的值」，天然一致。
 *
 * <p>行是 {@code Map<String,Object>} 而不是实体：自定义列意味着每行的字段集合由用户决定，
 * 用实体会把 40 个字段全部序列化出去（数据管理列表已有实体接口，这里是查询页，不需要）。
 */
@Data
public class QueryPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总条数（分页用） */
    private Long total = 0L;

    /** 当前页码（从 1 开始） */
    private Integer pageNo = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    /** 本次返回的列（顺序 = 表格/Excel 的从左到右） */
    private List<QueryColumnVO> columns = new ArrayList<>();

    /** 数据行：key 与 columns 的 key 一一对应 */
    private List<Map<String, Object>> rows = new ArrayList<>();
}
