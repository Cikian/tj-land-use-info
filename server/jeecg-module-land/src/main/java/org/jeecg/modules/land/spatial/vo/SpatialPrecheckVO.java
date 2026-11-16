package org.jeecg.modules.land.spatial.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * SHP 导入前目标库预检结果。
 *
 * <p>预检在不上传文件的情况下回答三件事：库在不在、本次凭据能不能连、
 * 这个库是不是本系统管出来的——由此决定前端要不要让用户在
 * 「连接导入 / 覆盖重建」里做选择，而不是静默替用户决定。
 */
@Data
@ApiModel(value = "SpatialPrecheckVO", description = "SHP 导入目标库预检结果")
public class SpatialPrecheckVO {

    @ApiModelProperty("目标库在 PG 服务器上是否已存在")
    private boolean pgExists;

    @ApiModelProperty("本次填写的连接信息是否已验证可用（库存在时对目标库验证，不存在时对 postgres 维护库验证）")
    private boolean credOk;

    @ApiModelProperty("该库名是否已在 MVT 管理清单（land_mvt_source）中")
    private boolean registered;

    @ApiModelProperty("是否本系统 SHP 导入创建的库（仅此类库允许删除/覆盖重建）")
    private boolean ownImported;

    @ApiModelProperty("库内用户表（schema.表名，排除系统 schema）；库不存在时为空")
    private List<String> tables;
}
