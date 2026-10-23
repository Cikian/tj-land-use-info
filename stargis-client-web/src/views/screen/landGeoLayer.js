/**
 * 宗地几何图层工厂（全部已画图地块的面 + 定位高亮）
 * ==================================================================
 * 【干什么】
 *   「数据管理」各录入菜单（经营性用地 / 配套地块）进入时，把后端
 *   geoList 返回的全部已画图地块面一次性铺到地图上（半透明底面）；
 *   列表**双击**某条时定位（相机飞行）并把该面高亮（加亮填充 + 黄色描边环）。
 *
 * 【为什么是工厂而不是单例】
 *   宗地图层与配套图层是两份独立状态（各自的 id 空间、各自的高亮），
 *   两个面板各 createGeoLayer() 一个实例，互不干扰。
 *   两个面板都被 keep-alive 缓存且共享同一张地图，但「激活时重铺、
 *   离开时清空」的节奏保证同一时刻地图上只有一个图层。
 *
 * 【生命周期跟菜单走，不跟绘制会话走】
 *   面板 activated（首次挂载也会触发）时 renderAll，deactivated（切走页签）
 *   时 clearAll —— 每次进菜单都是最新数据。表单里画的那个面走
 *   previewPolygon.js（跟着一次表单会话），与本图层互不相干。
 *
 * 【数据来源】dk_geojson 列。没画过图的地块（存量数据）没有几何，
 *   不在 geoList 返回里，自然也不上图 —— 这是数据现实，不是 bug；
 *   面上的几何由大屏「添加地块」绘制（单 Polygon）或「导入地块图形」
 *   （同编号多块合并为 MultiPolygon）产生，两种格式都认（geoPolygons.js）。
 */

import { parsePolygons } from './geoPolygons'

/** 普通态：淡填充（贴地面不支持 outline，边界靠明暗差区分） */
function normalFill (Cesium) {
  return Cesium.Color.AQUA.withAlpha(0.15)
}

/** 高亮态：加亮填充 */
function highlightFill (Cesium) {
  return Cesium.Color.AQUA.withAlpha(0.5)
}

/** 高亮描边环（贴地折线） */
function ringMaterial (Cesium) {
  return Cesium.Color.YELLOW.withAlpha(0.9)
}

/**
 * 创建一个地块几何图层实例。
 * @returns {{renderAll: Function, has: Function, highlight: Function, flyTo: Function, clearAll: Function, size: Function}}
 */
export function createGeoLayer () {
  /** 图层状态：[{ id, crzdbh, entities: [...], positionsList: [[...],[...]] }] */
  let items = []
  let highlightId = ''
  /** 高亮描边（MultiPolygon 有多块，一宗地可能多条描边线） */
  let ringEntities = []

  /**
   * 重铺全部地块面（先清后画，幂等）。
   * @param {Array<{id: string, crzdbh: string, dkmc: string, dkGeojson: string}>} list
   */
  function renderAll (list) {
    const Cesium = window.Cesium
    const viewer = window.viewer
    clearAll()
    if (!Cesium || !viewer || !Array.isArray(list)) return
    list.forEach((item) => {
      if (!item || !item.id || !item.dkGeojson) return
      const rings = parsePolygons(item.dkGeojson)
      if (!rings.length) return
      // 一宗地 N 块图形 = N 个面实体（MultiPolygon 时 rings.length > 1）
      const entities = []
      const positionsList = []
      rings.forEach((ring) => {
        const positions = ring.map(c => Cesium.Cartesian3.fromDegrees(c[0], c[1], 0))
        entities.push(viewer.entities.add({
          polygon: {
            hierarchy: new Cesium.PolygonHierarchy(positions),
            material: normalFill(Cesium),
            perPositionHeight: false,
            clampToGround: true,
            // S3DM 定制构建扩展：同时贴在 3D Tiles 上（MapPlacePanel 同款）
            clampToS3M: true
          }
        }))
        positionsList.push(positions)
      })
      items.push({ id: item.id, crzdbh: item.crzdbh || '', entities, positionsList })
    })
  }

  /** 该地块是否在图层上（有几何才在） */
  function has (id) {
    return items.some(item => item.id === id)
  }

  /**
   * 高亮某条地块（全部面换填充色 + 每块各画黄色描边环）；再次传同一个 id 无副作用。
   * @param {string} id 地块主键
   */
  function highlight (id) {
    const Cesium = window.Cesium
    const viewer = window.viewer
    if (!Cesium || !viewer) return
    highlightId = id || ''
    items.forEach((item) => {
      item.entities.forEach((entity) => {
        entity.polygon.material = item.id === highlightId
          ? highlightFill(Cesium)
          : normalFill(Cesium)
      })
    })
    // 描边环重建（贴地面不支持 outline，只能用折线画边界）；
    // MultiPolygon 每块一条线
    ringEntities.forEach((entity) => viewer.entities.remove(entity))
    ringEntities = []
    const hit = items.find(item => item.id === highlightId)
    if (!hit) return
    hit.positionsList.forEach((positions) => {
      if (!positions.length) return
      ringEntities.push(viewer.entities.add({
        polyline: {
          positions: positions.concat([positions[0]]),
          width: 3,
          material: ringMaterial(Cesium),
          clampToGround: true,
          clampToS3M: true
        }
      }))
    })
  }

  /** 相机飞到某条地块（按**全部块**顶点合集的包围球，带 45° 俯视角）；没有该地块时不做任何事 */
  function flyTo (id) {
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
      offset: new Cesium.HeadingPitchRange(0, Cesium.Math.toRadians(-45), sphere.radius * 2.4)
    })
  }

  /** 清掉整个图层与高亮（切走菜单 / 面板销毁时调用；幂等） */
  function clearAll () {
    const viewer = window.viewer
    if (viewer) {
      items.forEach((item) => item.entities.forEach((entity) => viewer.entities.remove(entity)))
      ringEntities.forEach((entity) => viewer.entities.remove(entity))
    }
    items = []
    ringEntities = []
    highlightId = ''
  }

  /** 当前图层数量（调试/面板展示用） */
  function size () {
    return items.length
  }

  return { renderAll, has, highlight, flyTo, clearAll, size }
}

export default { createGeoLayer }
