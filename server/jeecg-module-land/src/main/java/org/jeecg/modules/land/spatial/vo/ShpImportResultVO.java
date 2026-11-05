package org.jeecg.modules.land.spatial.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * SHP 导入结果
 */
@Data
@ApiModel(value = "ShpImportResultVO", description = "SHP 导入结果")
public class ShpImportResultVO {

    @ApiModelProperty("目标数据库名")
    private String dbName;

    @ApiModelProperty("目标表名")
    private String tableName;

    @ApiModelProperty("导入要素行数")
    private Integer rowCount;

    @ApiModelProperty("使用的导入引擎：shp2pgsql / geotools")
    private String engine;

    @ApiModelProperty("坐标系 SRID")
    private Integer srid;

    @ApiModelProperty("耗时（毫秒）")
    private Long elapsedMs;

    @ApiModelProperty("结果说明/错误信息")
    private String message;

    public static ShpImportResultVO ok(String dbName, String tableName, int rows,
                                       String engine, int srid, long elapsedMs) {
        ShpImportResultVO vo = new ShpImportResultVO();
        vo.dbName = dbName;
        vo.tableName = tableName;
        vo.rowCount = rows;
        vo.engine = engine;
        vo.srid = srid;
        vo.elapsedMs = elapsedMs;
        vo.message = "导入成功";
        return vo;
    }
}
