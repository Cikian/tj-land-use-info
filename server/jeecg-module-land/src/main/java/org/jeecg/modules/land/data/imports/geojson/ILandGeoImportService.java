package org.jeecg.modules.land.data.imports.geojson;

import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: 地块图形（GeoJSON）导入 - Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p>功能：上传 GeoJSON 文件（FeatureCollection），按每个 Feature 的
 * {@code properties.CRZDBH} 匹配 {@code t_land.crzdbh}，
 * 把匹配记录的 {@code dk_geojson} 更新为该 Feature 的 geometry。
 *
 * <p>与 Excel 批量导入同一核心约定：<b>预览与入库共用同一条解析路径</b>，
 * 保证「预览看到什么，导入就写什么」。
 */
public interface ILandGeoImportService {

    /**
     * 预览校验（不写库）。
     *
     * @param file 上传的 .geojson / .json 文件
     * @return 解析计数 + 错误/警告清单；有错误时 {@code aborted=true}
     */
    LandGeoImportResultVO preview(MultipartFile file);

    /**
     * 确认入库（重新解析文件后写库）。
     *
     * <p>与 Excel 导入一致：接口无状态，前端在预览与确认两步各传一次同一文件。
     * 存在错误时抛出异常（整批不写）。
     *
     * @param file 上传的 .geojson / .json 文件
     * @return 实际更新的条数等
     */
    LandGeoImportResultVO confirm(MultipartFile file);
}
