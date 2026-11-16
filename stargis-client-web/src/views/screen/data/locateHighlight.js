/**
 * 定位闪烁高亮（大屏列表双击定位后的视觉反馈）
 * ==================================================================
 * 【交互】定位飞行到位后，在目标地块的位置上脉冲闪烁几下（约 3.2s，
 *   三次平滑脉冲 + 尾部自然衰减），随后自动消失 —— 让用户在成片的
 *   地块里一眼认出"刚才定位的是这块"，不需要任何手动清理。
 *
 * 【形状优先级】
 *   1. 精确几何（sliceFeatureGroup 回显接口拿到的多块 GeoJSON）——
 *      按块描边闪烁，多块宗地全部一起闪；
 *   2. 拿不到几何时回退范围矩形（sliceFeatureBounds 的 bounds）。
 *
 * 【为什么独立成模块】与 previewPolygon 同款理由：表单/列表挂在 body、
 *   地图是全局 viewer，双方够不着彼此；同一时刻最多一组闪烁（再次定位
 *   会清掉上一组），模块级单例最直接。所有 Cesium 对象在调用时才取
 *   （模块加载阶段 window.Cesium 未必就绪）。
 *
 * 【闪烁实现】CallbackProperty 每帧按「创建时刻 + 脉冲函数」计算透明度
 *   （不用手动驱动 viewer.clock，也不占 requestAnimationFrame）；
 *   实体生命周期由 setTimeout 兜底回收，clear 幂等。
 */

import { parsePolygons } from '@/views/screen/geoPolygons'

/** 闪烁总时长（ms）：0.9s 一轮脉冲 × 3 轮 + 收尾余量 */
const FLASH_MS = 3200

/** 当前闪烁中的实体（填充 + 描边混合数组）与回收定时器 */
let entities = []
let timer = null
let startedAt = 0

/**
 * 脉冲透明度（0~1）：0.9s 一个平滑脉冲（0→1→0），随总时长线性衰减到 0。
 * @param {number} now 当前时间戳
 */
function pulseAlpha (now) {
  const t = (now - startedAt) / 1000
  if (t < 0) return 0
  const phase = (t % 0.9) / 0.9
  const decay = Math.max(0, 1 - t / (FLASH_MS / 1000))
  return Math.max(0, Math.sin(Math.PI * phase)) * decay
}

/** 按基准色生成"每帧闪烁"的颜色材质（alpha 随脉冲函数起伏） */
function flashMaterial (Cesium, base, alphaScale) {
  return new Cesium.ColorMaterialProperty(new Cesium.CallbackProperty(() => {
    return base.withAlpha(pulseAlpha(Date.now()) * alphaScale)
  }, false))
}

/** 清掉当前闪烁（幂等；?map=0 无 viewer 时也安全） */
export function clear () {
  if (entities.length && window.viewer) {
    entities.forEach((entity) => window.viewer.entities.remove(entity))
  }
  entities = []
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
}

/**
 * 在目标位置闪烁高亮。
 * @param {{geometries?: string[], bounds?: number[]}} options
 *   geometries：各要素的 WGS84 GeoJSON 字符串（Polygon/MultiPolygon 都认，
 *               多块宗地全部一起闪）；
 *   bounds：[minLon, minLat, maxLon, maxLat]（拿不到几何时的范围矩形回退）。
 *   两者都没有时不做任何事。
 */
export function flashAt (options) {
  const Cesium = window.Cesium
  const viewer = window.viewer
  if (!Cesium || !viewer || !options) return
  const geometries = Array.isArray(options.geometries) ? options.geometries : []
  const bounds = Array.isArray(options.bounds) ? options.bounds : null
  if (!geometries.length && !(bounds && bounds.length === 4)) return

  // 再次定位时先清掉上一组闪烁（单例语义，与 previewPolygon 一致）
  clear()
  startedAt = Date.now()

  if (geometries.length) {
    geometries.forEach((geojson) => {
      parsePolygons(geojson).forEach((ring) => {
        const positions = ring.map((c) => Cesium.Cartesian3.fromDegrees(c[0], c[1], 0))
        // 填充：主题青色半透明脉冲
        entities.push(viewer.entities.add({
          polygon: {
            hierarchy: new Cesium.PolygonHierarchy(positions),
            material: flashMaterial(Cesium, Cesium.Color.AQUA, 0.5),
            perPositionHeight: false,
            clampToGround: true,
            // S3DM 定制构建扩展：同时贴在 3D Tiles 上（与 previewPolygon 同款）
            clampToS3M: true
          }
        }))
        // 描边：亮青轮廓脉冲（贴地折线，闭合环）
        entities.push(viewer.entities.add({
          polyline: {
            positions: positions.concat([positions[0]]),
            width: 3,
            material: flashMaterial(Cesium, Cesium.Color.AQUA, 1.0),
            clampToGround: true
          }
        }))
      })
    })
  } else {
    // 回退：范围矩形（分类贴地，地形/3D Tiles 双贴）
    entities.push(viewer.entities.add({
      rectangle: {
        coordinates: Cesium.Rectangle.fromDegrees(bounds[0], bounds[1], bounds[2], bounds[3]),
        material: flashMaterial(Cesium, Cesium.Color.AQUA, 0.4),
        height: 0,
        classificationType: Cesium.ClassificationType.BOTH
      }
    }))
  }

  if (entities.length) {
    timer = setTimeout(clear, FLASH_MS + 200)
  }
}

export default { flashAt, clear }
