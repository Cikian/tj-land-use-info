package org.jeecg.modules.land.spatial.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 大屏图形管理「保存」请求体：某唯一值名下要素几何的整组替换。
 *
 * <p>交互契约（与前端图形管理弹窗严格对齐）：弹窗内对小块的新增/移除都是
 * 暂存态，点「保存」才把**最终的整组几何**一次性提交上来 —— 后端因此不需要
 * 行级删除接口，也不要求切片源表有行主键；落库语义 = 事务内先删该唯一值
 * 全部旧行，再逐条插入本次几何。
 */
@Data
@ApiModel(value = "SpatialFeatureGroupVO", description = "切片要素几何整组替换请求")
public class SpatialFeatureGroupVO {

    @ApiModelProperty("连接编码（land_mvt_source.code，从大屏图层树服务地址解析）")
    private String source;

    @ApiModelProperty("切片源表名")
    private String table;

    @ApiModelProperty("唯一值字段名（地块 crzdbh / 配套 ptxmmc）")
    private String field;

    @ApiModelProperty("唯一值（宗地编号 / 项目名称）")
    private String value;

    @ApiModelProperty("整组几何（WGS84 GeoJSON 字符串列表）；空列表 = 显式清空整组")
    private List<String> geometries;
}
