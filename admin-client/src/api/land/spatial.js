import { uploadAction } from '@/api/manage'
import { axios } from '@/utils/request'

/**
 * MVT 空间表服务 · SHP 上传入库接口
 *
 * 后端：org.jeecg.modules.land.spatial.controller.SpatialImportController
 * 接口：POST /land/spatial/import（multipart/form-data）
 *
 * 功能：上传 SHP 的 zip 压缩包（一个包只能有一个 shp + 必需 .dbf/.shx），
 * 后端自动 建库（如不存在）→ 启用 PostGIS → 导入几何与属性 → 替换式登记。
 *
 * 表单字段（与后端 @RequestParam 一一对应）：
 *   file           zip 文件
 *   pgHost         PG 地址
 *   pgPort         PG 端口（默认 5432）
 *   pgUser         PG 账号
 *   pgPassword     PG 密码
 *   dbName         目标库名（不存在则自动创建）
 *   tableName      目标表名（可空，取 shp 文件名）
 *   srid           坐标系（0=从 .prj 识别，识别不到默认 4326）
 *   importEngine   导入引擎（shp2pgsql / geotools，空取后端配置）
 *   existingPolicy 目标库已存在时的处理：connect=连接导入（默认）/ overwrite=覆盖重建
 */

export const spatialUrl = {
  importShp: '/land/spatial/import',
  precheck: '/land/spatial/precheck',
  sourceStats: '/land/spatial/source'
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

/**
 * 导入前预检（不上传文件）：目标库是否存在 / 本次凭据是否可用 /
 * 是否本系统导入的库（能否覆盖重建）/ 库内现有表清单。
 * 密码用 FormData 放请求体，不能进 URL 查询串——会被网关与日志明文记录。
 *
 * @returns {Promise} result: { pgExists, credOk, registered, ownImported, tables: ['schema.表名', ...] }
 */
export function precheckDb (params) {
  const fd = new FormData()
  Object.keys(params).forEach(k => fd.append(k, params[k]))
  return axios({ url: spatialUrl.precheck, method: 'post', data: fd, timeout: 30000 })
}

/**
 * 查询清单中某库的现状（能否连上 / 库内表数量），移除确认弹窗展示"将丢失什么"用。
 * 后端用登记里存的连接信息探活，口令不出服务端。
 *
 * @returns {Promise} result: { code, dbName, ownImported, reachable, tableCount }
 */
export function getSourceStats (id) {
  return axios({ url: `${spatialUrl.sourceStats}/${id}/stats`, method: 'get', timeout: 30000 })
}
