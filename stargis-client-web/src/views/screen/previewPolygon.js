/**
 * 地块图形常驻预览（绘制结束后留在地图上的那个面）
 * ==================================================================
 * 【为什么独立成模块，而不是让绘制引擎管】
 *   「添加地块」结束后，画好的面要**继续留在地图上**，直到表单提交成功、
 *   取消关闭、点「添加地块」重画或点「清除」才消失 —— 这个生命周期跟着
 *   **表单会话**走，跟绘制会话（drawPolygon，弹回表单时就要销毁）不同。
 *   表单弹窗挂在 body 上、地图是全局 window.viewer，双方够不着彼此，
 *   所以这里用一个模块级单例持有这唯一的预览实体，表单直接调 clear()。
 *
 * 【所有权】同一时刻最多一个预览面（show 会先清旧的）。
 *   · ScreenPickStage：绘制 finish 时 show(geometry.geojson) 接管成品质感；
 *   · 表单（LandFormModal / FacilityFormModal）：重画前 clear、清除按钮 clear、
 *     提交成功 clear、取消/关闭 clear；取消绘制后若表单里还有旧图形则 show 回来。
 */

let entity = null

/**
 * 在地图上显示（或替换）地块预览面。
 * @param {string} geojsonString 裸 Polygon geometry 字符串（与 dk_geojson 列约定一致）
 */
export function show (geojsonString) {
  const Cesium = window.Cesium
  const viewer = window.viewer
  if (!Cesium || !viewer || !geojsonString) return
  let geometry = null
  try {
    geometry = JSON.parse(geojsonString)
  } catch (e) {
    return
  }
  const ring = geometry && geometry.type === 'Polygon' && geometry.coordinates && geometry.coordinates[0]
  if (!Array.isArray(ring) || ring.length < 4) return
  clear()
  const positions = ring.map(c => Cesium.Cartesian3.fromDegrees(c[0], c[1], 0))
  entity = viewer.entities.add({
    polygon: {
      hierarchy: new Cesium.PolygonHierarchy(positions),
      // 样式与绘制中的预览面一致（drawPolygon.js / MapPlacePanel 同款 AQUA + 贴地）
      material: Cesium.Color.AQUA.withAlpha(0.35),
      perPositionHeight: false,
      clampToGround: true,
      // S3DM 定制构建扩展：同时贴在 3D Tiles 上
      clampToS3M: true
    }
  })
}

/** 清掉预览面（没有时不做任何事；幂等，?map=0 无 viewer 时也安全） */
export function clear () {
  if (entity && window.viewer) {
    window.viewer.entities.remove(entity)
  }
  entity = null
}

export default { show, clear }
