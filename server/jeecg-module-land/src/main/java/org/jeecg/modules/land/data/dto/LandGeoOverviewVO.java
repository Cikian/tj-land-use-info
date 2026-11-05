package org.jeecg.modules.land.data.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 铺图概览数据（版本化增量协议）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p>与前端约定（{@code GET /land/data/landAdmin/geoList?version=xxx}）：
 * <ul>
 *   <li>前端带上次拿到的 {@code version}；后端比对当前版本（= 全表 MAX(update_time)，
 *       任何宗地图形/数据的变更都会推高它）；</li>
 *   <li>版本一致 → {@code unchanged=true}，不再传输宗地数据 ——
 *       响应只有几十字节，进页面的加载量与「总共有多少宗」解耦；</li>
 *   <li>版本不一致（首次 / 图形变过）→ {@code unchanged=false} + 全量概览列表，
 *       前端替换本地缓存后渲染。</li>
 * </ul>
 */
@Data
public class LandGeoOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前数据版本（全表 MAX(update_time)，图形变更即推高） */
    private String version;

    /** 版本一致、数据无变化（此时 lands 为空） */
    private boolean unchanged;

    /** 概览宗地列表（仅 id / crzdbh / dkmc / dkGeojson 有值，图形为概览档） */
    private List<org.jeecg.modules.land.data.entity.Land> lands = new ArrayList<>();
}
