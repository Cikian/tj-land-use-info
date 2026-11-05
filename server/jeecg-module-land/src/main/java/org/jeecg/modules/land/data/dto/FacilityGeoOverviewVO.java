package org.jeecg.modules.land.data.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 配套铺图概览数据（版本化增量协议）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p>与 {@link LandGeoOverviewVO}（经营性用地）同一套协议，数据源换成
 * {@code t_supporting_facilities}（版本 = 全表 MAX(updateTime)）：
 * <ul>
 *   <li>前端带上次拿到的 version；一致 → {@code unchanged=true}，只回几十字节；</li>
 *   <li>不一致（首次 / 图形变过）→ 全量概览列表 + 新版本号。</li>
 * </ul>
 */
@Data
public class FacilityGeoOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前数据版本（全表 MAX(updateTime)，配套图形/数据变更即推高） */
    private String version;

    /** 版本一致、数据无变化（此时 lands 为空） */
    private boolean unchanged;

    /** 概览配套列表（仅 id / crzdbh / ptxmmc / dkGeojson 有值，图形为概览档） */
    private List<org.jeecg.modules.land.data.entity.Facility> lands = new ArrayList<>();
}
