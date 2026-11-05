package org.jeecg.modules.land.data.imports.facilitygeo;

import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: 配套地块图形（GeoJSON）导入 - Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p>功能：上传配套矢量成果 GeoJSON（FeatureCollection），按每个 Feature 的
 * {@code properties.PTXMMC}（配套项目名称，归一后）匹配 {@code t_supporting_facilities.ptxmmc}，
 * 把匹配记录的 {@code dk_geojson} 更新为该 Feature 的 geometry。
 *
 * <p>与经营性用地 GeoJSON 导入同一核心约定：
 * <b>预览与入库共用同一条解析路径</b>（「预览看到什么，导入就写什么」）；
 * <b>同名多 Feature 合并、同名多记录全更新、有错整批不写</b>。
 */
public interface IFacilityGeoImportService {

    /** 预览校验（不写库） */
    FacilityGeoImportResultVO preview(MultipartFile file);

    /** 确认入库（重新解析文件后写库）；存在错误时抛异常（整批不写） */
    FacilityGeoImportResultVO confirm(MultipartFile file);
}
