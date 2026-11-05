/**
 * 宗地几何图层工厂（全部已画图地块的面 + 定位高亮）
 * ==================================================================
 * 【干什么】
 *   「数据管理」各录入菜单（经营性用地 / 配套地块）进入时，把后端
 *   geoList 返回的全部已画图地块一次性铺到地图上——样式为「方案 B · 描边玻璃」：
 *   半透明填充 + 亮色描边勾轮廓（用地红橙系 / 配套青色系，见 STYLE_PRESETS）；
 *   列表**双击**某条时定位（相机飞行）并把该面高亮（黄色粗描边 + 填充加深）。
 *
 * 【为什么是工厂而不是单例】
 *   宗地图层与配套图层是两份独立状态（各自的 id 空间、各自的配色、各自的高亮），
 *   两个面板各 createGeoLayer('land' / 'facility') 一个实例，互不干扰。
 *   两个面板都被 keep-alive 缓存且共享同一张地图，但「激活时重铺、
 *   离开时清空」的节奏保证同一时刻地图上只有一个图层。
 *
 * 【生命周期跟菜单走，不跟绘制会话走】
 *   面板 activated（首次挂载也会触发）时 renderAll，deactivated（切走页签）
 *   时 clearAll —— 每次进菜单都是最新数据。表单里画的那个面走
 *   previewPolygon.js（跟着一次表单会话），与本图层互不相干。
 *
 * 【描边为什么用折线实体】
 *   Cesium 贴地面多边形不支持 outline（经典限制），轮廓线只能用
 *   clampToGround 折线一圈一圈画——每宗地一圈，随面同增同删。
 *
 * 【数据来源】dk_geojson 列，<b>两级加载</b>（WMS 式按需的轻量版）：
 *   进菜单先拉首屏概览档（每环 ≤40 点，总体几百 KB 秒开）铺图，
 *   双击定位某宗地时按 id 拉<b>精确</b>图形（/geoDetail，几 KB）经
 *   applyPreciseOverlay 把高亮覆盖层重建为定位档几何 —— 边界即测绘精度。
 *   两档都贴地形 + S3M 模型；差别只在顶点密度（概览轮廓远看够用，
 *   近看略见棱角，定位后即换精确边界）。
 */

import { parsePolygons } from './geoPolygons'


/**
 * 配色方案（2026-10 设计定稿 · 方案 B「描边玻璃」）：
 *   · 经营性用地 = 红橙系（呼应旧系统红色地块），配套地块 = 青色系；
 *   · 填充半透明透出底图，描边亮色勾轮廓（贴地面不支持 outline，折线实现）；
 *   · 高亮统一黄色粗描边 + 填充加深（与原高亮习惯衔接）。
 * 调整渲染样式只改这里，图层逻辑不感知具体颜色。
 */
const STYLE_PRESETS = {
  land: {
    fill: 'rgba(255,106,61,0.25)',
    stroke: 'rgba(255,138,80,0.95)',
    strokeWidth: 2,
    // 高亮 = 同几何的「加深覆盖层」叠加在常态填充之上（0.25+0.32 视觉 ≈ 0.49），
    // 不改底图层材质 —— 812 宗同材质同批次，改任何一个实体的材质都会触发
    // 整批重建（秒级卡顿），覆盖层是独立小批次，加/删只动自己
    highlightOverlayFill: 'rgba(255,106,61,0.32)',
    highlightStroke: '#FFFFFF',
    highlightStrokeWidth: 3
  },
  facility: {
    fill: 'rgba(41,216,255,0.25)',
    stroke: 'rgba(79,227,255,0.95)',
    strokeWidth: 2,
    highlightOverlayFill: 'rgba(41,216,255,0.32)',
    highlightStroke: '#FFFFFF',
    highlightStrokeWidth: 3
  }
}

/**
 * 创建一个地块几何图层实例。
 * @param {string} [styleKey] 配色方案：'land'（红橙，默认）/ 'facility'（青色）
   * @returns {{renderAll: Function, has: Function, highlight: Function, flyTo: Function, applyPreciseOverlay: Function, clearAll: Function, size: Function}}
 */
export function createGeoLayer (styleKey) {
  const style = STYLE_PRESETS[styleKey || 'land'] || STYLE_PRESETS.land
  let colors = null   // Cesium.Color 解析缓存（首次渲染时解析）

  function getColors (Cesium) {
    if (!colors) {
      colors = {
        fill: Cesium.Color.fromCssColorString(style.fill),
        stroke: Cesium.Color.fromCssColorString(style.stroke),
        highlightOverlayFill: Cesium.Color.fromCssColorString(style.highlightOverlayFill),
        highlightStroke: Cesium.Color.fromCssColorString(style.highlightStroke)
      }
    }
    return colors
  }

  /** 图层状态：[{ id, crzdbh, entities: [...面+描边...], positionsList: [[...],[...]] }]。
   *  ★ 底图层在 renderAll 之后<b>永远不再增删改</b>——任何改动都会触发
   *    Cesium 大批次重建；高亮/定位全部走覆盖层（overlayEntities）。 */
  let items = []
  let highlightId = ''
  /** 高亮/定位覆盖层（加深填充 + 白描边；定位后重建为定位档几何） */
  let overlayEntities = []

  /**
   * 重铺全部地块（先清后画，幂等）。每块图形 = 1 个填充面 + 1 圈描边折线。
   * @param {Array<{id: string, crzdbh: string, dkmc: string, dkGeojson: string}>} list
   * @param {boolean} [heavy=false] 渲染口径：false=概览档（不贴 S3M，减负）；true=精确档（全贴）
   */
  function renderAll (list, heavy) {
    const Cesium = window.Cesium
    const viewer = window.viewer
    clearAll()
    if (!Cesium || !viewer || !Array.isArray(list)) return
    const colors = getColors(Cesium)
    list.forEach((item) => {
      if (!item || !item.id || !item.dkGeojson) return
      const rings = parsePolygons(item.dkGeojson)
      if (!rings.length) return
      const entities = []
      const positionsList = []
      rings.forEach((ring) => {
        const positions = ring.map(c => Cesium.Cartesian3.fromDegrees(c[0], c[1], 0))
        entities.push(viewer.entities.add({
          polygon: {
            hierarchy: new Cesium.PolygonHierarchy(positions),
            material: colors.fill,
            perPositionHeight: false,
            clampToGround: true,
            // ★ 概览档也贴 S3M：不贴的话地块会沉在 3D Tiles 模型地面之下，
            //   远看「残缺不全」。渲染开销主要由顶点数驱动——概览档每环只有
            //   40 点（精确档的 1/5），贴模型的代价可控，不会再回到从前卡顿
            clampToS3M: true
          }
        }))
        entities.push(viewer.entities.add({
          polyline: {
            positions: positions.concat([positions[0]]),
            width: style.strokeWidth,
            material: colors.stroke,
            clampToGround: true,
            clampToS3M: true
          }
        }))
        positionsList.push(positions)
      })
      items.push({ id: item.id, crzdbh: item.crzdbh || '', entities, positionsList, heavy: !!heavy })
    })
  }

  /** 该地块是否在图层上（有几何才在） */
  function has (id) {
    return items.some(item => item.id === id)
  }

  /**
   * 高亮某条地块：<b>覆盖层</b>实现——同几何叠一层「加深填充」+ 一圈白色描边。
   *
   * <p>★ 为什么不用「改材质」：812 宗常态填充是同一种材质，Cesium 打进同一个
   * 地面渲染批次；改其中任何一个实体的材质都会把整个大批次拆了重建
   * （重新贴地求交，秒级）——这是「定位后过好几秒才高亮」的根源。
   * 覆盖层是<b>独立材质的独立小批次</b>，加/删只动自己，毫秒级。
   * 再次传同一个 id 会先清后画（幂等）。
   *
   * @param {string} id 地块主键（传空清除高亮）
   */
  function highlight (id) {
    const Cesium = window.Cesium
    const viewer = window.viewer
    if (!Cesium || !viewer) return
    const t0 = performance.now()
    clearOverlays()
    highlightId = id || ''
    if (!highlightId) return
    const hit = items.find(item => item.id === highlightId)
    if (!hit) return
    hit.positionsList.forEach((positions) => {
      addOverlayFill(positions)
      addOverlayRing(positions)
    })
    console.log('[定位计时] 高亮覆盖层创建(同步部分): ' + Math.round(performance.now() - t0) + 'ms, 块数=' + hit.positionsList.length)
  }

  /** 清掉全部高亮/定位覆盖层（幂等） */
  function clearOverlays () {
    const viewer = window.viewer
    if (viewer) {
      overlayEntities.forEach((entity) => viewer.entities.remove(entity))
    }
    overlayEntities = []
  }

  /** 叠一层「加深填充」覆盖面（与底图常态 0.25 叠加 ≈ 加深效果） */
  function addOverlayFill (positions) {
    const Cesium = window.Cesium
    const viewer = window.viewer
    const colors = getColors(Cesium)
    overlayEntities.push(viewer.entities.add({
      polygon: {
        hierarchy: new Cesium.PolygonHierarchy(positions),
        material: colors.highlightOverlayFill,
        perPositionHeight: false,
        clampToGround: true,
        clampToS3M: true
      }
    }))
  }

  /**
   * 叠一圈高亮描边（白色、贴地——与填充同层同贴地口径）。
   * ★ 悬浮圈方案已废弃：3D 视角下 +50m 的圈因透视偏移会「飘」在地块上方半空，
   *   与地面填充脱节，观感极差。贴地圈与深化填充一起随瓦片就绪渲染
   *   （覆盖层架构下底图层不再重建，实测渲染正常）。
   * ★ positions 是【已转换好的 Cartesian3 数组】（highlight 传概览的、
   *   applyPreciseOverlay 传定位档的，两种来源格式一致）——不能按 [lon,lat]
   *   数组取下标（undefined 会直接炸掉定位）。
   */
  function addOverlayRing (positions) {
    const Cesium = window.Cesium
    const viewer = window.viewer
    const colors = getColors(Cesium)
    const closed = positions.slice()
    if (!Cesium.Cartesian3.equals(closed[0], closed[closed.length - 1])) {
      closed.push(closed[0])   // 闭合
    }
    overlayEntities.push(viewer.entities.add({
      polyline: {
        positions: closed,
        width: style.highlightStrokeWidth,
        material: colors.highlightStroke,
        clampToGround: true,
        clampToS3M: true
      }
    }))
  }

  /**
   * 用<b>定位档图形</b>重建当前高亮覆盖层（两级加载的按需档）。
   *
   * 双击定位 → 飞行落定后前端按 id 拉定位档图形调这里：覆盖层整体重画为
   * 精确几何（加深填充 + 白描边），底图常态层<b>始终不动</b>（不触发大批次重建）。
   * id 与当前高亮不一致（连点了别的行）时返回 false，调用方静默即可。
   *
   * @param {string} id 记录主键
   * @param {string} geojsonString 定位档 dk_geojson（Polygon / MultiPolygon）
   * @returns {boolean} 是否应用成功
   */
  function applyPreciseOverlay (id, geojsonString) {
    const Cesium = window.Cesium
    const viewer = window.viewer
    if (!Cesium || !viewer || !id || !geojsonString) return false
    if (id !== highlightId) return false
    const hit = items.find(item => item.id === id)
    if (!hit) return false
    const rings = parsePolygons(geojsonString)
    if (!rings.length) return false
    const t1 = performance.now()
    clearOverlays()
    rings.forEach((ring) => {
      const positions = ring.map(c => Cesium.Cartesian3.fromDegrees(c[0], c[1], 0))
      addOverlayFill(positions)
      addOverlayRing(positions)
    })
    console.log('[定位计时] 定位档覆盖层创建(同步部分): ' + Math.round(performance.now() - t1) + 'ms, 顶点=' + rings.reduce((s, r) => s + r.length, 0))
    return true
  }

  /**
   * 相机飞到某条地块（按**全部块**顶点合集的包围球，带 45° 俯视角）；没有该地块时不做任何事。
   * @param {string} id 地块主键
   * @param {Function} [onComplete] 飞行结束后的回调 —— 调用方用它把「换定位档图形」
   *   这类有建面开销的操作推迟到相机落定之后，避免阻塞飞行动画（定位迟滞的另一半主因）
   */
  function flyTo (id, onComplete) {
    const Cesium = window.Cesium
    const viewer = window.viewer
    if (!Cesium || !viewer) return
    const hit = items.find(item => item.id === id)
    if (!hit) return
    const allPositions = [].concat(...hit.positionsList)
    if (!allPositions.length) return
    const sphere = Cesium.BoundingSphere.fromPoints(allPositions)
    viewer.camera.flyToBoundingSphere(sphere, {
      duration: 1.2,
      offset: new Cesium.HeadingPitchRange(0, Cesium.Math.toRadians(-45), sphere.radius * 2.4),
      complete: typeof onComplete === 'function' ? onComplete : undefined
    })
  }

  /** 清掉整个图层与高亮（切走菜单 / 面板销毁时调用；幂等） */
  function clearAll () {
    const viewer = window.viewer
    if (viewer) {
      items.forEach((item) => item.entities.forEach((entity) => viewer.entities.remove(entity)))
      overlayEntities.forEach((entity) => viewer.entities.remove(entity))
    }
    items = []
    overlayEntities = []
    highlightId = ''
  }

  /** 当前图层数量（调试/面板展示用） */
  function size () {
    return items.length
  }

  return { renderAll, has, highlight, flyTo, applyPreciseOverlay, clearAll, size }
}

export default { createGeoLayer }
