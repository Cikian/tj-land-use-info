import { VectorTile } from '@mapbox/vector-tile'
import Pbf from 'pbf'

const TILE_SIZE = 256
const DEFAULT_STYLE = { fill: 'rgba(35, 205, 185, 0.38)', stroke: '#69fff0', lineWidth: 1.5, pointRadius: 3 }
const LAYER_COLORS = [
  [35, 205, 185], [255, 166, 66], [98, 162, 255], [217, 119, 255],
  [244, 100, 123], [188, 218, 75], [255, 214, 83], [100, 210, 255],
  [242, 148, 200], [144, 127, 255], [74, 205, 115], [211, 164, 116]
]

/** 按资源保留配色，不随勾选顺序或图层销毁改变；注册表随 viewer 一起释放。 */
export function createMvtStyleAssigner() {
  const styles = new Map()
  return id => {
    if (!styles.has(id)) {
      const index = styles.size
      const rgb = LAYER_COLORS[index]
      const hue = ((index - LAYER_COLORS.length) * 137.508 + 15) % 360
      styles.set(id, rgb ? {
        fill: `rgba(${rgb.join(',')},0.38)`,
        stroke: `rgb(${rgb.map(value => Math.round(value + (255 - value) * 0.45)).join(',')})`
      } : {
        fill: `hsla(${hue},75%,58%,0.38)`,
        stroke: `hsl(${hue},90%,75%)`
      })
    }
    return styles.get(id)
  }
}

function canvas() {
  const image = document.createElement('canvas')
  image.width = image.height = TILE_SIZE
  return image
}

/** XYZ 瓦片号 → 经纬度（pickFeatures 的点内测试用） */
function tile2lon (x, z) { return x / Math.pow(2, z) * 360 - 180 }
function tile2lat (y, z) {
  const n = Math.PI - 2 * Math.PI * y / Math.pow(2, z)
  return 180 / Math.PI * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)))
}

/** 射线法点内多环测试（奇偶规则，天然支持洞） */
function pointInRings (lon, lat, rings) {
  let inside = false
  rings.forEach(ring => {
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const xi = ring[i][0]; const yi = ring[i][1]
      const xj = ring[j][0]; const yj = ring[j][1]
      if (((yi > lat) !== (yj > lat)) && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)) inside = !inside
    }
  })
  return inside
}

/** Cesium 起始层最多覆盖四张瓦片，避免地方数据从全球层级逐层生成父瓦片。 */
export function mvtMinimumLevel(C, scheme, rectangle, minimum, maximum) {
  let level = minimum
  // 跨日期变更线及零面积范围保留服务声明；细小数据也不自动从过高层级起步。
  if (rectangle.east <= rectangle.west || rectangle.east - rectangle.west >= Math.PI || rectangle.north <= rectangle.south) return level
  for (let next = minimum + 1; next <= Math.min(8, maximum); next++) {
    const sw = scheme.positionToTileXY(new C.Cartographic(rectangle.west, rectangle.south), next)
    const ne = scheme.positionToTileXY(new C.Cartographic(rectangle.east, rectangle.north), next)
    if (!sw || !ne || (ne.x - sw.x + 1) * (sw.y - ne.y + 1) > 4) break
    level = next
  }
  return level
}

/** 每条环单独闭合，使用奇偶填充保留 Polygon / MultiPolygon 内洞。 */
export function drawMvt(bytes, style = {}, sourceLayer) {
  const tile = new VectorTile(new Pbf(new Uint8Array(bytes)))
  const image = canvas()
  const context = image.getContext('2d')
  const paint = { ...DEFAULT_STYLE, ...style }
  context.fillStyle = paint.fill
  context.strokeStyle = paint.stroke
  context.lineWidth = paint.lineWidth
  context.lineJoin = 'round'
  Object.keys(tile.layers).forEach(name => {
    if (sourceLayer && name !== sourceLayer) return
    const layer = tile.layers[name]
    const scale = TILE_SIZE / layer.extent
    for (let i = 0; i < layer.length; i++) {
      const feature = layer.feature(i)
      context.beginPath()
      feature.loadGeometry().forEach(ring => {
        ring.forEach((point, index) => {
          const x = point.x * scale
          const y = point.y * scale
          if (feature.type === 1) {
            context.moveTo(x + paint.pointRadius, y)
            context.arc(x, y, paint.pointRadius, 0, Math.PI * 2)
          } else if (index === 0) context.moveTo(x, y)
          else context.lineTo(x, y)
        })
        if (feature.type === 3) context.closePath()
      })
      if (feature.type === 1 || feature.type === 3) context.fill('evenodd')
      if (feature.type !== 1) context.stroke()
    }
  })
  return image
}

/** 标准 XYZ MVT → Cesium 透明影像；由 Cesium 负责视野调度和瓦片缓存。 */
export async function createMvtImageryProvider(url, options = {}) {
  const C = window.Cesium
  const absoluteUrl = new URL(url, window.location.href).href
  let metadata = {}
  let template = absoluteUrl
  if (!/\{z\}|%7Bz%7D/i.test(url)) {
    const response = await fetch(absoluteUrl, { signal: options.signal })
    if (!response.ok) throw new Error('MVT 服务信息请求失败：HTTP ' + response.status)
    metadata = await response.json()
    if (!metadata.tiles || !metadata.tiles.length) throw new Error('MVT 服务信息缺少 tiles 地址')
    template = new URL(metadata.tiles[0], absoluteUrl).href
  }
  // URL 会转义花括号，统一还原占位符，避免生成永远相同的瓦片请求。
  template = template.replace(/%7B/ig, '{').replace(/%7D/ig, '}')
  const scheme = new C.WebMercatorTilingScheme()
  const bounds = options.bounds || metadata.bounds
  const rectangle = Array.isArray(bounds) && bounds.length === 4
    ? C.Rectangle.fromDegrees(...bounds.map(Number)) : scheme.rectangle
  const maximumLevel = Number(options.maximumLevel !== undefined ? options.maximumLevel : metadata.maxzoom || 20)
  const declaredMinimum = Number(options.minimumLevel !== undefined ? options.minimumLevel : metadata.minzoom || 0)
  const minimumLevel = options.minimumLevel !== undefined || !Array.isArray(bounds)
    ? declaredMinimum : mvtMinimumLevel(C, scheme, rectangle, declaredMinimum, maximumLevel)
  const transparent = canvas()
  const controllers = new Set()
  let destroyed = false
  const provider = {
    ready: true,
    readyPromise: Promise.resolve(true),
    tileWidth: TILE_SIZE,
    tileHeight: TILE_SIZE,
    minimumLevel,
    maximumLevel,
    tilingScheme: scheme,
    rectangle,
    tileDiscardPolicy: undefined,
    credit: undefined,
    proxy: undefined,
    hasAlphaChannel: true,
    errorEvent: new C.Event(),
    getTileCredits() { return undefined },
    pickFeatures() { return undefined },
    requestImage(x, y, level, request) {
      if (destroyed) return Promise.resolve(transparent)
      // 返回 undefined 让 Cesium 下一帧重试，限制快速缩放时的并发请求。
      if (controllers.size >= 16) return undefined
      const controller = new AbortController()
      controllers.add(controller)
      if (request) request.cancelFunction = () => controller.abort()
      const tileY = metadata.scheme === 'tms' ? Math.pow(2, level) - y - 1 : y
      const tileUrl = template.replace(/\{z\}/ig, level).replace(/\{x\}/ig, x).replace(/\{y\}/ig, tileY)
      return fetch(tileUrl, { signal: controller.signal }).then(async response => {
        if (response.status === 204 || response.status === 404) return transparent
        if (!response.ok) throw new Error('MVT 瓦片请求失败：HTTP ' + response.status)
        const bytes = await response.arrayBuffer()
        return destroyed || !bytes.byteLength ? transparent : drawMvt(bytes, options.style, options.sourceLayer)
      }).catch(error => {
        if (error.name === 'AbortError' || destroyed) return transparent
        if (options.onError) options.onError(error)
        throw error
      }).finally(() => controllers.delete(controller))
    },
    /**
     * 点选拾取（scene.imageryLayers.pickImageryLayerFeatures 调用）：
     * 取点击点所在瓦片，解析 MVT，返回点内要素的属性。
     * 插件的 QueryPick 只认 scene.pick（不含影像层），MVT 的属性拾取
     * 由地图管理页的点击分支走 Cesium 标准 imagery 拾取 API 消费。
     */
    async pickFeatures(x, y, level, longitude, latitude) {
      if (destroyed) return undefined
      const tileXY = scheme.positionToTileXY(new C.Cartographic(longitude, latitude), level)
      if (!tileXY) return undefined
      const tileY = metadata.scheme === 'tms' ? Math.pow(2, level) - tileXY.y - 1 : tileXY.y
      const tileUrl = template.replace(/\{z\}/ig, level)
        .replace(/\{x\}/ig, tileXY.x).replace(/\{y\}/ig, tileY)
      let bytes
      try {
        const response = await fetch(tileUrl, { signal: options.signal })
        if (!response.ok) return undefined
        bytes = new Uint8Array(await response.arrayBuffer())
      } catch (error) { return undefined }
      const tile = new VectorTile(new Pbf(bytes))
      const lonDeg = longitude * 180 / Math.PI
      const latDeg = latitude * 180 / Math.PI
      const found = []
      Object.keys(tile.layers).forEach(name => {
        if (options.sourceLayer && name !== options.sourceLayer) return
        const layer = tile.layers[name]
        const x0 = tile2lon(tileXY.x, level); const x1 = tile2lon(tileXY.x + 1, level)
        const y0 = tile2lat(tileXY.y + 1, level); const y1 = tile2lat(tileXY.y, level)
        const toLL = p => [x0 + (x1 - x0) * p.x / layer.extent, y1 - (y1 - y0) * (layer.extent - p.y) / layer.extent]
        for (let i = 0; i < layer.length; i++) {
          const feature = layer.feature(i)
          if (feature.type !== 3) continue // 拾取查询按面要素（地块）
          const rings = feature.loadGeometry().map(ring => ring.map(toLL))
          if (!pointInRings(lonDeg, latDeg, rings)) continue
          const info = new C.ImageryLayerFeatureInfo()
          info.name = String(feature.properties.gid || feature.id || name + '#' + i)
          info.data = feature.properties
          info.description = '<table style="color:#0a0a0a;font-size:13px">' +
            Object.entries(feature.properties)
              .map(([k, v]) => `<tr><td style="padding:2px 8px;font-weight:bold">${k}</td><td style="padding:2px 8px">${v}</td></tr>`)
              .join('') + '</table>'
          info.coordinate = { longitude: lonDeg, latitude: latDeg }
          found.push(info)
        }
      })
      return found.length ? found : undefined
    },
    cancelRequests() { controllers.forEach(controller => controller.abort()) },
    destroy() { destroyed = true; provider.cancelRequests() },
    isDestroyed() { return destroyed }
  }
  return provider
}
