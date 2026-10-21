/**
 * Cesium 多边形绘制工具（「添加地块」的绘制引擎）
 * ==================================================================
 * 【为什么自写而不用 stargis-function 的绘制插件】
 *   stargis-function 里有 DrawGeometric / DrawPLS 等绘制组件，但它是闭源 UMD：
 *   激活方法名、绘制完成回调、结果格式（多半是插件自己的标绘对象）都不可考，
 *   把「存 GeoJSON」押在格式不可控的回调上等于把核心交付物交给黑盒。
 *   本工具只用 Cesium 公开 API（ScreenSpaceEventHandler + CallbackProperty），
 *   ~230 行，交互与输出完全可控 —— src/ 里此前没有任何绘制实现，这是第一份。

 * 【职责边界】
 *   只管「画」：加点 / 橡皮筋预览 / 结束判定 / 清理。纯 GIS 操作，**没有顶部按钮**，
 *   绘制过程中也**不弹任何提示**（2026-10-21 用户要求）：
 *     · 左键加点；双击或右键 = 结束，结束即由 ScreenPickStage 把图形回传表单；
 *     · 点数不足 3 个时结束 = 静默忽略（不弹「顶点不足」这类提示）；
 *     · Esc = 取消（ScreenPickStage 的捕获监听负责）。
 *   ★ 画完的面**不随本引擎销毁**：finish 时 ScreenPickStage 把它转交给
 *     previewPolygon.js 常驻预览层（跟着表单会话走，提交/取消/重画才清），
 *     本引擎 stop() 只回收自己的工作实体（顶点点、橡皮筋面）。
 *   ★ 结束事件的类型名有个天坑（2026-10-21「没法结束绘制」的根因）：
 *     本项目的定制 Cesium 构建是老版本，双击事件叫 **LEFT_DOUBLE_CLICK**，
 *     没有新版才有的 DOUBLE_CLICK —— 用 undefined 注册监听 = 永远不触发。
 *     取类型时必须两个名字都兜（见 startDraw 里的 FINISH_DOUBLE_CLICK）；
 *     右键（RIGHT_CLICK）两代都有，是最稳的结束方式。

 * 【与全局 Cesium 的关系】
 *   Cesium 与 viewer 都是全局（public/index.html 的 script 标签 +
 *   S3dmViewer.vue 的 window.viewer），本文件不 import、不 new Viewer，
 *   与 MapPlacePanel.vue 等组件同款写法。
 */

/** 经纬度保留位数：6 位 ≈ 0.1m，足够地块精度且 GeoJSON 体积可控 */
const COORD_DECIMALS = 6

/** 两个顶点视为「同一点」的经度/纬度阈值（度）。1e-6 ≈ 0.1m，用于双击去重 */
const SAME_POINT_EPSILON = 1e-6

/**
 * 开始一次绘制。
 *
 * @param {object} viewer 全局 Cesium Viewer（window.viewer）；调用方保证非空
 * @param {{ onFinish?: () => void }} [options]
 *   onFinish：双击/右键成功结束（顶点 ≥3）时回调，**绘制中不弹任何提示**
 *   （点数不足时结束 = 静默忽略）。ScreenPickStage 在这里取图形、转交常驻
 *   预览层并回传表单。
 * @returns {{ stop: () => void, getGeoJSON: () => ({geojson: string, vertexCount: number}|null), vertexCount: () => number }}
 *   绘制句柄。stop() 必须在会话结束时调用（幂等），否则事件监听与实体残留。
 */
export function startDraw (viewer, options) {
  const Cesium = window.Cesium
  const opts = options || {}
  const scene = viewer.scene
  const ellipsoid = scene.globe.ellipsoid

  // ---------------- 状态 ----------------
  /** 已定下的顶点（WGS84 度）：[{ lon, lat }] */
  let vertices = []
  /** 鼠标当前悬停位置（度）；离开球面时为 null —— 橡皮筋据此显隐 */
  let cursor = null
  /** 当前多边形是否已定格（定格后再点左键 = 作废重画） */
  let finished = false
  let stopped = false

  // ---------------- 屏幕坐标 → 经纬度 ----------------

  /**
   * 屏幕坐标转 WGS84 经纬度。
   * 用 pickEllipsoid 而不是 scene.pickPosition：后者要开深度纹理且在
   * 无 3D Tiles/地形时表现不稳，而地块只需要经纬度（椭球面交点就够）。
   * 点到天空（球外）返回 null，调用方忽略本次点击。
   */
  function toLonLat (windowPosition) {
    const cartesian = viewer.camera.pickEllipsoid(windowPosition, ellipsoid)
    if (!cartesian) return null
    const carto = ellipsoid.cartesianToCartographic(cartesian)
    return {
      lon: Cesium.Math.toDegrees(carto.longitude),
      lat: Cesium.Math.toDegrees(carto.latitude)
    }
  }

  function toCartesian (pt) {
    return Cesium.Cartesian3.fromDegrees(pt.lon, pt.lat, 0)
  }

  // ---------------- 预览实体 ----------------
  // 样式对齐 MapPlacePanel.drawSelected 的既有范式（AQUA + 贴地），
  // 这是本工程里唯一被验证过的「GeoJSON 面 → Cesium 实体」写法。

  /** 顶点小圆点（每加点一次 push 一个实体） */
  const pointEntities = []
  /** 橡皮筋面：层级随顶点/鼠标实时变化（CallbackProperty） */
  const polygonEntity = viewer.entities.add({
    polygon: {
      hierarchy: new Cesium.CallbackProperty(() => {
        const ring = cursor && !finished
          ? vertices.concat([cursor])
          : vertices
        return new Cesium.PolygonHierarchy(ring.map(toCartesian))
      }, false),
      material: Cesium.Color.AQUA.withAlpha(0.35),
      // 贴地：不取顶点高度，面 draped 在地形/场景表面
      perPositionHeight: false,
      clampToGround: true,
      // S3DM 定制构建的扩展：同时贴在 3D Tiles 上（MapPlacePanel 同款）
      clampToS3M: true
    }
  })
  /** 边线：跟面同一组顶点，贴地 */
  const polylineEntity = viewer.entities.add({
    polyline: {
      positions: new Cesium.CallbackProperty(() => {
        const ring = cursor && !finished
          ? vertices.concat([cursor])
          : vertices
        const positions = ring.map(toCartesian)
        // 闭合：首尾相接（顶点 ≥ 3 才有“面”的意义，2 点时也画折线便于观察）
        if (positions.length >= 3) positions.push(positions[0])
        return positions
      }, false),
      width: 2,
      material: Cesium.Color.AQUA,
      clampToGround: true,
      clampToS3M: true
    }
  })

  function addPointEntity (pt) {
    pointEntities.push(viewer.entities.add({
      position: toCartesian(pt),
      point: {
        pixelSize: 9,
        color: Cesium.Color.AQUA,
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 1.5,
        // 点也要贴地，否则有地形时顶点会悬空/入地
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
        disableDepthTestDistance: Number.POSITIVE_INFINITY
      }
    }))
  }

  function clearEntities () {
    pointEntities.splice(0).forEach(e => viewer.entities.remove(e))
  }

  // ---------------- 交互 ----------------

  function samePoint (a, b) {
    return Math.abs(a.lon - b.lon) < SAME_POINT_EPSILON &&
      Math.abs(a.lat - b.lat) < SAME_POINT_EPSILON
  }

  /** 双击会先触发两次 LEFT_CLICK（Cesium 事件序），最后一点是重复点，去掉再定格 */
  function dedupeTrailingClick () {
    const n = vertices.length
    if (n >= 2 && samePoint(vertices[n - 1], vertices[n - 2])) vertices.pop()
  }

  function finishCurrent () {
    if (finished) return
    dedupeTrailingClick()
    cursor = null
    // 点数不足 = 静默忽略（用户要求：不弹「顶点不足」这类提示），继续画
    if (vertices.length < 3) return
    finished = true
    // finish = 画完：ScreenPickStage 收到后把图形转交常驻预览层并回传表单
    if (typeof opts.onFinish === 'function') opts.onFinish()
  }

  const handler = new Cesium.ScreenSpaceEventHandler(scene.canvas)
  handler.setInputAction(movement => {
    if (finished) {
      // 定格后再次点击 = 作废当前图形、重新开始
      vertices = []
      finished = false
      clearEntities()
    }
    const pt = toLonLat(movement.position)
    if (!pt) return
    vertices.push(pt)
    addPointEntity(pt)
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK)

  handler.setInputAction(movement => {
    cursor = toLonLat(movement.endPosition)
  }, Cesium.ScreenSpaceEventType.MOUSE_MOVE)

  /*
   * ★★ 双击结束的事件类型必须兜底两个名字：
   *   本项目的定制 Cesium 构建是老版本，ScreenSpaceEventType 里只有
   *   LEFT_DOUBLE_CLICK（0x3），没有新版改名的 DOUBLE_CLICK ——
   *   用 undefined 注册 = 永远不触发，这就是「双击无法结束绘制」的根因
   *   （2026-10-21 实测）。两个名字取存在的那个，兼容将来换新构建。
   */
  const FINISH_DOUBLE_CLICK = Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK ||
    Cesium.ScreenSpaceEventType.DOUBLE_CLICK

  handler.setInputAction(_movement => {
    // Viewer 自带的默认双击行为是「跟踪拾到的实体」，且它的监听注册在先、
    // 必然先于本回调执行 —— 这里紧跟清掉，避免双击结束后视角被预览面带跑。
    // ★ 用 try 包住：定制构建若没有 trackedEntity 的 setter，不能让异常
    //   中断下面的 finishCurrent（结束流程比视角清理重要得多）。
    try { viewer.trackedEntity = undefined } catch (e) { /* 忽略 */ }
    finishCurrent()
  }, FINISH_DOUBLE_CLICK)

  // 右键结束：RIGHT_CLICK 在新老 Cesium 里都存在（本构建为 0x7），
  // 是最稳的结束方式；部分用户习惯双击，两条路都保留
  handler.setInputAction(_movement => {
    finishCurrent()
  }, Cesium.ScreenSpaceEventType.RIGHT_CLICK)

  // ---------------- 句柄 ----------------

  return {
    /**
     * 停止绘制并清理（取消 / 完成 / 覆盖层卸载都要调；幂等）。
     * 只删自己的实体与监听，不碰 viewer 的其它状态。
     */
    stop () {
      if (stopped) return
      stopped = true
      handler.destroy()
      clearEntities()
      viewer.entities.remove(polygonEntity)
      viewer.entities.remove(polylineEntity)
      // 双击可能触发了 Viewer 的实体跟踪（见 DOUBLE_CLICK 注释），兜底清一次
      try { viewer.trackedEntity = undefined } catch (e) { /* 忽略 */ }
      vertices = []
      cursor = null
      finished = false
    },

    /**
     * 取当前图形 —— **按钮结束的权威出口，不要求「已经双击定格过」**。
     * 只要顶点 ≥ 3（能连成面）就当场收口：去掉末尾可能因双击产生的重复点、
     * 闭合首尾，返回结果。这样无论双击/右键事件在定制构建里是否生效，
     * 「完成绘制」按钮永远能把图形带走（2026-10-21「没法结束」的修复）。
     *
     * @returns {({geojson: string, vertexCount: number}|null)}
     *   null = 顶点不足 3 个，画不出面（调用方保留字段原值）。
     *   geojson 是**裸 Polygon geometry** 字符串（与后端 dk_geojson 列约定一致）：
     *   {"type":"Polygon","coordinates":[[[lon,lat],…首尾闭合]]}
     */
    getGeoJSON () {
      if (stopped) return null
      const pts = vertices.slice()
      // 双击会先触发两次 LEFT_CLICK，末尾可能留一个重复点，收口前去掉
      const n = pts.length
      if (n >= 2 && samePoint(pts[n - 1], pts[n - 2])) pts.pop()
      if (pts.length < 3) return null
      const ring = pts.map(v => [
        Number(v.lon.toFixed(COORD_DECIMALS)),
        Number(v.lat.toFixed(COORD_DECIMALS))
      ])
      ring.push(ring[0])
      return {
        geojson: JSON.stringify({ type: 'Polygon', coordinates: [ring] }),
        vertexCount: pts.length
      }
    },

    /** 当前顶点数（含未定格的）——状态提示用 */
    vertexCount () {
      return vertices.length
    }
  }
}

export default { startDraw }
