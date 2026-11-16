/**
 * 切片源信息共享模块（数据管理页各弹窗/面板共用的「中台切片图层」读取口径）
 * ==================================================================
 * 【为什么抽出来】
 *   2026-11-14 之前这些逻辑只住在大屏 data/index.vue 里（定位 + 图层联动）。
 *   图形编辑上线后，表单弹窗（同步几何）、图形管理弹窗（整组读写）、
 *   录入面板（移除联动清图形）都需要同一件事：
 *     「本模块对应哪个切片源（连接编码/表名）+ 唯一值字段是什么」。
 *   信息只有一处来源——壳层隐藏图层树（layer-tree-pop）的树数据，
 *   所以解析逻辑收敛到本模块，谁都不许自己再抄一份：
 *   中台换库名/表名/字典值时，这里改一处全模块自动跟随（零配置的承诺不能破）。

 * 【匹配口径】tiletype ∈ 类型映射（localStorage 持久化 + 名字兜底自学习）。
 *   ★ 字典值映射是「学出来」的：首次遇到名字命中的节点时，把它的 tiletype
 *     记进映射并持久化，之后同名/换名/新增同类型图层都按类型命中；
 *     中台字典重建导致值变化时，名字兜底会重新学习并覆盖缓存。
 */
import { renderedMvtIds } from '@/utils/mvt/layerTreeDirective'
import { getLayerTreePop } from '@/views/screen/layerTreeBridge'
import { deleteSliceFeatureGroup } from '@/api/land/landAdmin'

/**
 * 各模块切片要素的唯一值字段（切片源表属性）。
 * 实测：地块0922=crzdbh（宗地编号）、配套数据0922=ptxmmc（项目名称，无编号字段）。
 */
export const SLICE_UNIQUE_FIELDS = {
  land: 'crzdbh',
  facility: 'ptxmmc'
}

/** 进入模块时自动勾选的切片图层：类型字典值兜底名单 + 名字兜底名单 */
export const SLICE_TYPE_BOOTSTRAP = {
  land: ['地块数据'],
  facility: ['配套数据', '配套设施']
}

const SLICE_TYPE_DEFAULTS = {
  land: ['4'],
  facility: ['5']
}
const SLICE_TYPE_CACHE_KEY = 'screen-slice-type-map'

/** 切片类型映射（tiletype 字典值列表，按页签分组）；localStorage 持久化 + 自学习 */
export function loadSliceTypeMap () {
  try {
    const raw = localStorage.getItem(SLICE_TYPE_CACHE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (parsed && parsed.land) return parsed
    }
  } catch (e) { /* 损坏即回退默认 */ }
  return JSON.parse(JSON.stringify(SLICE_TYPE_DEFAULTS))
}

export function saveSliceTypeMap (tab, types) {
  try {
    const map = loadSliceTypeMap()
    map[tab] = types
    localStorage.setItem(SLICE_TYPE_CACHE_KEY, JSON.stringify(map))
  } catch (e) { /* 存储不可用时映射仅本次会话有效 */ }
}

/** 收集树里的全部 MVT 节点（layertype/subtypes 任一声明为 MVT 都认） */
function collectMvtNodes (pop) {
  const mvtNodes = []
  const walk = (nodes, sink) => {
    const list = nodes || []
    list.forEach((node) => {
      if (String(node.layertype || node.subtypes || '').toUpperCase() === 'MVT') {
        sink.push(node)
      }
      if (node.children) walk(node.children, sink)
    })
  }
  walk(pop.treeData, mvtNodes)
  return mvtNodes
}

/**
 * 本模块的全部目标切片节点（tiletype 在类型映射里的 MVT 节点）。
 * 树未就绪 / 类型尚未学习到时返回空数组 —— 调用方按空处理，不报错。
 * ★ 命中名字兜底名单的节点会顺带学习其 tiletype（与 checkSliceLayerReady 同一口径）。
 */
export function findModuleMvtNodes (tab) {
  const pop = getLayerTreePop()
  if (!pop || !Array.isArray(pop.treeData) || !pop.treeData.length) return []
  const typeMap = loadSliceTypeMap()
  const tabTypes = typeMap[tab] || []
  const bootstrapNames = SLICE_TYPE_BOOTSTRAP[tab] || []
  const targets = []
  let learned = false
  collectMvtNodes(pop).forEach((node) => {
    const tiletype = String(node.tiletype || '')
    const byType = tabTypes.indexOf(tiletype) > -1
    const byName = bootstrapNames.some((name) => String(node.name || '').indexOf(name) > -1)
    if (byType || byName) {
      targets.push(node)
      if (!byType && tiletype && tabTypes.indexOf(tiletype) === -1) {
        tabTypes.push(tiletype)
        learned = true
      }
    }
  })
  if (learned) saveSliceTypeMap(tab, tabTypes)
  return targets
}

/**
 * 解析本模块的切片源信息（连接编码/表名/几何列）。
 * 取类型匹配的**第一个**目标节点，从服务地址解析
 * `/land/mvt/public/{code}/{schema}/{table}/{geom}` 四段；树未就绪返回 null。
 */
export function getModuleSliceInfo (tab) {
  const hit = findModuleMvtNodes(tab)[0]
  if (!hit) return null
  const url = String(hit.url || '')
  const marker = '/land/mvt/public/'
  const idx = url.indexOf(marker)
  if (idx === -1) return null
  const segs = url.slice(idx + marker.length).split('?')[0].split('/')
  if (segs.length < 4) return null
  const decoded = segs.map((seg) => decodeURIComponent(seg))
  return { source: decoded[0], schema: decoded[1], table: decoded[2], geom: decoded[3] }
}

/** 切片源信息 + 唯一值字段一次性取齐（写路径统一入口，null = 图层树未就绪） */
export function getModuleSliceContext (tab) {
  const info = getModuleSliceInfo(tab)
  if (!info) return null
  const field = SLICE_UNIQUE_FIELDS[tab]
  if (!field) return null
  return { info, field }
}

/**
 * 图层联动循环体（index.vue 的 ensureSliceLayer 每秒调一次直到就绪）。
 * 目标判定 = tiletype ∈ 类型映射 或 名字命中兜底名单（同时学习其 tiletype）。
 * @returns {boolean} 是否全部目标图层都已渲染到 viewer
 */
export function checkSliceLayerReady (tab, bootstrapNames) {
  const pop = getLayerTreePop()
  if (!pop || !Array.isArray(pop.treeData) || !pop.treeData.length) return false
  const targets = findModuleMvtNodes(tab)
  if (!targets.length) return false
  // 完成判定：全部目标节点都已在 viewer 上渲染（勾选可能被插件重置过，以实际渲染为准）
  const renderedIds = renderedMvtIds(window.viewer)
  const unfinished = targets.filter((node) => !renderedIds.has(String(node.oldid || node.id)))
  const current = pop.checkedKeys || []
  const isArray = Array.isArray(current)
  const keys = isArray ? current.slice() : (current.checked || []).slice()
  unfinished.forEach((node) => {
    if (keys.indexOf(node.id) === -1) keys.push(node.id)
  })
  pop.checkedKeys = isArray ? keys : { ...current, checked: keys }
  return unfinished.length === 0
}

/**
 * 定位到包围盒（EPSG:4326 [xmin,ymin,xmax,ymax]）——**setView 版**。
 * ★ 为什么不用 camera.flyTo(Rectangle)：S3DM 定制版里 flyTo 的动画会被
 *   残留的 lookAtTransform 状态取消，实测调用后相机纹丝不动（2026-11-16）；
 *   setView 立即生效、无动画窗口。先解除 lookAtTransform 残留，再定位。
 * 外扩 35%（与设计稿视口比例一致），最小高度 600m 兜底。
 * @param {number[]} bbox [xmin,ymin,xmax,ymax]
 */
export function flyToBbox (bbox) {
  const C = window.Cesium
  const viewer = window.viewer
  if (!C || !viewer || !Array.isArray(bbox) || bbox.length !== 4) return
  const cam = viewer.camera
  if (cam.lookAtTransform) cam.lookAtTransform(C.Matrix4.IDENTITY)
  const cx = (bbox[0] + bbox[2]) / 2
  const cy = (bbox[1] + bbox[3]) / 2
  const w = Math.max(bbox[2] - bbox[0], 0.0008)
  const h = Math.max(bbox[3] - bbox[1], 0.0006)
  const spanM = Math.max(w, h) * 111320 * 1.6
  const height = Math.max(spanM, 600)
  cam.setView({
    destination: C.Cartesian3.fromDegrees(cx, cy, height),
    orientation: { heading: 0, pitch: -C.Math.PI_OVER_TWO || -Math.PI / 2, roll: 0 }
  })
}

/**
 * MVT 数据图层联动勾选的**自愈循环**（供数据管理页页签切换与大屏壳层常驻
 * 引导共同调用）：每秒断言目标切片图层已在 viewer 上渲染，被插件重置就再勾，
 * 直到渲染成功（120s 预算）。幂等：同模块重复调用会先清旧定时器。
 *
 * @param {string} tab 'land' / 'facility'
 */
const autoTimers = {}
export function startSliceLayerAuto (tab) {
  const bootstrapNames = SLICE_TYPE_BOOTSTRAP[tab]
  if (!bootstrapNames) return
  if (autoTimers[tab]) clearInterval(autoTimers[tab])
  let tries = 0
  autoTimers[tab] = setInterval(() => {
    tries++
    if (checkSliceLayerReady(tab, bootstrapNames) || tries >= 120) {
      clearInterval(autoTimers[tab])
      delete autoTimers[tab]
    }
  }, 1000)
}

export function stopSliceLayerAuto (tab) {
  if (autoTimers[tab]) {
    clearInterval(autoTimers[tab])
    delete autoTimers[tab]
  }
}

/**
 * 重载本模块的切片图层（图形保存/删除后调用）。
 *
 * ★ 为什么必须重载：MVT 瓦片是 XYZ 请求，浏览器会缓存已取过的瓦片 ——
 *   源表几何变了、图层不动的话用户看到的还是旧形状。
 *   实现走「撤下 → 稍候勾回」两步：checkedKeys 每次赋新数组引用，
 *   指令的 watch → manager.sync（全量替换语义）自然把层撤下再重建，
 *   重建后的瓦片请求绕开缓存。500ms 是撤下动画/实体清理的经验值。
 *   图层树未就绪时静默跳过（用户下次进模块自愈循环会重新勾选）。
 */
export function reloadModuleLayers (tab) {
  const pop = getLayerTreePop()
  if (!pop) return
  const targets = findModuleMvtNodes(tab)
  if (!targets.length) return
  const targetKeys = targets.map((node) => node.id)
  const current = pop.checkedKeys || []
  const isArray = Array.isArray(current)
  const keys = isArray ? current.slice() : (current.checked || []).slice()
  const rest = keys.filter((key) => targetKeys.indexOf(key) === -1)
  // 第一步：撤下（新数组引用触发指令重新 sync）
  pop.checkedKeys = isArray ? rest.slice() : { ...current, checked: rest.slice() }
  // 第二步：勾回重建（撤下的实体清理留 500ms）
  setTimeout(() => {
    const after = rest.slice()
    targetKeys.forEach((key) => {
      if (after.indexOf(key) === -1) after.push(key)
    })
    pop.checkedKeys = isArray ? after.slice() : { ...current, checked: after.slice() }
  }, 500)
}

/**
 * 移除业务记录后联动清掉切片库里该记录名下的图形（整组）。
 *
 * ★ fire-and-forget：业务记录在调用前已经软删成功，图形清理失败**不能**阻断
 *   列表刷新 —— 只如实提示（残留图形会在图层上继续显示，重新走一次移除或
 *   手动清切片库都能处理）。切片源未就绪时整批跳过（图形以唯一值关联，
 *   不清也不影响系统运行，只是图层上多画几个块）。
 *
 * @param {string} tab 'land' / 'facility'（决定唯一值字段与切片源上下文）
 * @param {Array<object>} records 被移除的记录（按 SLICE_UNIQUE_FIELDS[tab] 取唯一值）
 * @param {{warning: Function, success: Function}} toast 大屏 toast（模块层不依赖组件实例）
 */
export function purgeFeatureGroups (tab, records, toast) {
  const ctx = getModuleSliceContext(tab)
  if (!ctx) return
  const field = SLICE_UNIQUE_FIELDS[tab]
  const values = (records || [])
    .map((record) => String((record && record[field]) || '').trim())
    .filter(Boolean)
  if (!values.length) return
  let failed = 0
  const jobs = values.map((value) =>
    deleteSliceFeatureGroup({ source: ctx.info.source, table: ctx.info.table, field: field, value: value })
      .then((res) => {
        if (!res || !res.success) failed++
      })
      .catch(() => { failed++ })
  )
  Promise.all(jobs).then(() => {
    if (failed) {
      toast.warning(`有 ${failed} 条记录的地图图形清理失败，图形会继续留在图层上；可重新移除该记录重试，或手动清理切片库`)
    } else {
      toast.success('对应的地块图形已从切片库同步清除')
    }
    reloadModuleLayers(tab)
  })
}
