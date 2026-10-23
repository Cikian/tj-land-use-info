/**
 * 地块 GeoJSON 解析工具（Polygon / MultiPolygon 双格式）
 * ==================================================================
 * 【为什么需要】
 *   dk_geojson 现在有两种合法形态：
 *     · 裸 Polygon      —— 大屏「添加地块」绘制、导入文件里该编号只有一块；
 *     · 裸 MultiPolygon —— GeoJSON 导入时同一编号出现多个 Feature，
 *       后端把多块合并成一块多面图形（「导入即替换」语义，见
 *       LandGeoImportServiceImpl.mergePolygons）。
 *   渲染层（landGeoLayer / previewPolygon）和表单顶点数统计都要同时认两种，
 *   解析口径收敛到这里，避免每个文件各写一份「type === 'Polygon' && coordinates[0]」。
 *
 * 【容错原则】坏数据返回空数组，不抛错 —— 图层/预览是辅助视图，
 *   一条坏图形不能把整个面板搞挂。
 */

/**
 * 解析裸 geometry 字符串，返回各多边形的**外环**数组。
 *
 * @param {string} geojsonString 裸 Polygon / MultiPolygon geometry 字符串
 * @returns {number[][][]} 外环数组：[[ [lon,lat], ... ], [ [lon,lat], ... ], ...]；
 *          不是合法图形 / 没有任何可用环时返回 []
 */
export function parsePolygons (geojsonString) {
  if (!geojsonString) return []
  let geometry = null
  try {
    geometry = JSON.parse(geojsonString)
  } catch (e) {
    return []
  }
  if (!geometry || !geometry.coordinates) return []

  let rings = []
  if (geometry.type === 'Polygon') {
    rings = [geometry.coordinates[0]]
  } else if (geometry.type === 'MultiPolygon') {
    // MultiPolygon.coordinates = [ polygon[ ring[ point ] ] ]，取每个多边形的外环（第 0 个环）
    rings = geometry.coordinates.map(polygon => (polygon && polygon[0]) || null)
  } else {
    return []
  }

  // 逐环校验：是数组且 ≥4 点（首尾闭合）；坏的跳过而不是整体失败
  return rings.filter(ring => Array.isArray(ring) && ring.length >= 4)
}

/**
 * 统计图形的顶点数（跨全部多边形；末位闭合点不计）。
 * 用于表单状态行「已绘制（N 个顶点）」——多面图形显示总顶点数，
 * 而不是被旧代码当成「尚未绘制（0 个）」。
 *
 * @param {string} geojsonString 裸 Polygon / MultiPolygon geometry 字符串
 * @returns {number} 顶点数；不是合法图形返回 0
 */
export function countVertices (geojsonString) {
  return parsePolygons(geojsonString).reduce((total, ring) => total + ring.length - 1, 0)
}

export default { parsePolygons, countVertices }
