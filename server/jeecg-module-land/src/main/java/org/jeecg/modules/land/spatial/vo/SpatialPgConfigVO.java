package org.jeecg.modules.land.spatial.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 空间库（PG）默认连接信息，供 SHP 导入弹窗回显。
 *
 * <p>★ 为什么需要这个接口：导入弹窗过去把 PG 地址硬编码成 127.0.0.1 当默认值，
 * 导入时该地址被原样登记进 <code>land_mvt_source.db_url</code>；后端主机上没有本机 PG，
 * MVT 探活与出图就全部 Connection refused，而 .env 里的 TJ_PG_HOST 看起来「配了也没用」。
 * 现在默认值只来自服务端配置（land.spatial.pg-* ← TJ_PG_*），前端只负责回显。
 *
 * <p>口令不下发浏览器（与 MvtSource、SpatialSourceStatsVO 同一口径）：
 * 用户按需在弹窗里填写，空着则由服务端配置兜底。
 */
@Data
@ApiModel("空间库默认连接信息")
public class SpatialPgConfigVO {

    @ApiModelProperty("PG 主机（land.spatial.pg-host）")
    private String host;

    @ApiModelProperty("PG 端口（land.spatial.pg-port）")
    private int port;

    @ApiModelProperty("PG 账号（land.spatial.pg-user）")
    private String user;
}
