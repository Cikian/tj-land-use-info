import { uploadAction } from '@/api/manage'

/**
 * MVT 空间表服务 · SHP 上传入库接口
 *
 * 后端：org.jeecg.modules.land.spatial.controller.SpatialImportController
 * 接口：POST /land/spatial/import（multipart/form-data）
 *
 * 功能：上传 SHP 的 zip 压缩包（一个包只能有一个 shp + 必需 .dbf/.shx），
 * 后端自动 建库（如不存在）→ 启用 PostGIS → 导入几何与属性。
 *
 * 表单字段（与后端 @RequestParam 一一对应）：
 *   file          zip 文件
 *   pgHost        PG 地址
 *   pgPort        PG 端口（默认 5432）
 *   pgUser        PG 账号
 *   pgPassword    PG 密码
 *   dbName        目标库名（不存在则自动创建）
 *   tableName     目标表名（可空，取 shp 文件名）
 *   srid          坐标系（0=从 .prj 识别，识别不到默认 4326）
 *   importEngine  导入引擎（shp2pgsql / geotools，空取后端配置）
 */

export const spatialUrl = {
  importShp: '/land/spatial/import'
}

/**
 * 上传 SHP zip 并导入 PostGIS。
 * 导入是长事务（几十万行插入可能耗时几分钟），单独放宽超时到 10 分钟。
 *
 * @param {FormData} formData 含 file 与上述表单字段
 * @returns {Promise} result 里含 dbName / tableName / rowCount / engine / srid / elapsedMs
 */
export function importShp (formData) {
  return uploadAction(spatialUrl.importShp, formData, { timeout: 600000 })
}
