package org.jeecg.modules.land.spatial.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 管理清单中某个 GIS 库的现状（删除/覆盖确认弹窗的知情依据）。
 * 用登记里存的连接信息在服务端探活并统计库内表数量，
 * 口令不下发浏览器（MvtSource 的敏感字段同样 @JsonIgnore 处理）。
 */
@Data
@ApiModel(value = "SpatialSourceStatsVO", description = "GIS 库现状统计")
public class SpatialSourceStatsVO {

    @ApiModelProperty("连接编码（= land_mvt_source.code）")
    private String code;

    @ApiModelProperty("PG 数据库名")
    private String dbName;

    @ApiModelProperty("是否本系统 SHP 导入创建的库（仅此类允许删除 PG 库）")
    private boolean ownImported;

    @ApiModelProperty("用登记的连接信息能否连上该库")
    private boolean reachable;

    @ApiModelProperty("库内用户表数量（不可达时为 -1）")
    private int tableCount;
}
