import { createMvtImageryProvider, createMvtStyleAssigner } from './imageryProvider'

const managers = new WeakMap()
const bindings = new WeakMap()

/**
 * 跨实例会话勾选意图（nodeId 字符串 → true/false）。
 * ★ 三个图层树弹层实例（首页页签 / 地图管理「图层管理」/ 壳层隐藏实例）
 *   各自拉树、各自持有 checkedKeys，互相不知晓；但 scene viewer 是共享的
 *   （显隐立即生效）。服务端 getSceneAndTree 返回配置态，不包含运行时勾改，
 *   导致「首页关掉的图层进地图管理又显示成勾上」。按 nodeId 记录最后一次
 *   用户勾选意图，新实例水合时纠正勾选框，实现三树联动（见 connect/sync）。
 */
const sessionChecked = new Map()

function properties(node) {
  let value = node.customproperty || node.customProperty || {}
  // 中台历史数据可能被重复 JSON 编码，兼容两层即可。
  for (let i = 0; i < 2 && typeof value === 'string'; i++) {
    try { value = JSON.parse(value) } catch (error) { return {} }
  }
  return value && typeof value === 'object' ? (value.mvt || {}) : {}
}

/** 分组勾选也覆盖其子节点；同一资源多处挂树时，任一处勾选即显示。 */
export function mvtSelections(tree, checkedKeys) {
  const checked = new Set((Array.isArray(checkedKeys) ? checkedKeys : (checkedKeys && checkedKeys.checked) || []).map(String))
  const resources = new Map()
  function walk(nodes, parentChecked = false, parentDisabled = false) {
    ;(nodes || []).forEach(node => {
      const disabled = parentDisabled || node.disabled === true || node.disableCheckbox === true
      const enabled = !disabled && (parentChecked || checked.has(String(node.id)))
      if (String(node.layertype || node.subtypes || '').toUpperCase() === 'MVT') {
        const id = String(node.oldid || node.id)
        const previous = resources.get(id)
        resources.set(id, { node, enabled: enabled || Boolean(previous && previous.enabled) })
      }
      if (node.children) walk(node.children, enabled, disabled)
    })
  }
  walk(tree)
  return resources
}

function managerFor(viewer) {
  if (managers.has(viewer)) return managers.get(viewer)
  const entries = new Map()
  const styleFor = createMvtStyleAssigner()
  let disposed = false
  function remove(entry) {
    if (entry.controller) entry.controller.abort()
    if (entry.provider) entry.provider.destroy()
    if (entry.layer && !viewer.isDestroyed()) viewer.imageryLayers.remove(entry.layer, true)
  }
  const manager = {
    selectedIds() { return new Set(Array.from(entries).filter(([, entry]) => entry.enabled).map(([id]) => id)) },
    sync(resources, notify) {
      if (disposed || viewer.isDestroyed()) return
      // 对整棵树分配，包含未勾选节点，避免用户先勾选哪一层决定颜色。
      Array.from(resources.keys()).sort().forEach(id => styleFor(id))
      entries.forEach((entry, id) => {
        if (!resources.has(id)) { remove(entry); entries.delete(id) }
      })
      resources.forEach(({ node, enabled }, id) => {
        const url = String(node.url || '').trim()
        const fingerprint = url + JSON.stringify(properties(node))
        let entry = entries.get(id)
        if (entry && entry.fingerprint !== fingerprint) {
          remove(entry)
          entries.delete(id)
          entry = null
        }
        if (!entry) {
          entry = { fingerprint, enabled: false, layer: null, provider: null, promise: null }
          entries.set(id, entry)
        }
        entry.enabled = enabled
        if (!enabled) {
          // 取消即释放，避免中止中的空瓦片被缓存、快速重勾复用旧异步任务。
          remove(entry)
          entries.delete(id)
          return
        }
        if (entry.layer) {
          entry.layer.show = true
          return
        }
        if (entry.promise) return
        entry.controller = new AbortController()
        entry.warned = false
        const report = error => {
          if (!entry.enabled || entries.get(id) !== entry || entry.warned || disposed) return
          entry.warned = true
          notify('图层“' + node.name + '”加载失败，请检查 MVT 服务后重新勾选')
          console.error('[MVT]', node.name, error)
        }
        const options = properties(node)
        entry.promise = createMvtImageryProvider(url, {
          ...options,
          style: { ...styleFor(id), ...options.style },
          signal: entry.controller.signal,
          onError: report
        }).then(provider => {
          if (disposed || entries.get(id) !== entry || !entry.enabled || viewer.isDestroyed()) {
            provider.destroy()
            return
          }
          entry.provider = provider
          entry.layer = viewer.imageryLayers.addImageryProvider(provider)
          entry.layer._mvtResourceId = id
        }).catch(error => {
          if (entry.provider && !entry.layer) entry.provider.destroy()
          if (error.name !== 'AbortError') report(error)
        }).finally(() => { entry.promise = null })
      })
    },
    async locate(node, signal) {
      const entry = entries.get(String(node.oldid || node.id))
      let provider = entry && entry.provider
      const temporary = !provider
      try {
        // 未勾选也只读取范围，不创建影像层或改变勾选状态。
        if (!provider) provider = await createMvtImageryProvider(node.url, { ...properties(node), signal })
        if (disposed || signal.aborted || viewer.isDestroyed()) return
        manager.fly(provider)
      } finally {
        if (temporary && provider) provider.destroy()
      }
    },
    fly(provider) {
      const rectangle = provider.rectangle
      if (rectangle.east - rectangle.west >= Math.PI) throw new Error('MVT 服务未配置定位范围，请补充 bounds')
      // ★ setView 定位（flyTo 在 S3DM 定制版里会被残留 lookAtTransform 取消，实测失效）
      const C = window.Cesium
      if (viewer.camera.lookAtTransform) viewer.camera.lookAtTransform(C.Matrix4.IDENTITY)
      const lon = (rectangle.west + rectangle.east) / 2 * 180 / Math.PI
      const lat = (rectangle.south + rectangle.north) / 2 * 180 / Math.PI
      const w = (rectangle.east - rectangle.west) * 180 / Math.PI
      const h = (rectangle.north - rectangle.south) * 180 / Math.PI
      const spanM = Math.max(w, h) * 111320 * 1.6
      viewer.camera.setView({
        destination: C.Cartesian3.fromDegrees(lon, lat, Math.max(spanM, 600)),
        orientation: { heading: 0, pitch: -Math.PI / 2, roll: 0 }
      })
    },
    dispose() { disposed = true; entries.forEach(remove); entries.clear() }
  }
  managers.set(viewer, manager)
  return manager
}

export function disposeMvtLayers(viewer) {
  const manager = viewer && managers.get(viewer)
  if (manager) { manager.dispose(); managers.delete(viewer) }
}

/**
 * 取某 viewer 上当前勾选生效（已渲染）的 MVT 资源 id 集合。
 * 跨页联动勾选（数据管理隐藏图层树）合并 keys 时用：sync 是全量替换语义，
 * 不并进这些 id 的话，一次联动勾选会把其它入口（地图管理图层树）勾的层整批撤下。
 */
export function renderedMvtIds(viewer) {
  const manager = viewer && managers.get(viewer)
  return manager ? manager.selectedIds() : new Set()
}

function connect(el, binding, vnode) {
  const vm = vnode.componentInstance
  const previous = bindings.get(el)
  if (previous && previous.vm === vm) return
  if (previous) previous.dispose()
  if (!vm || vm.$options.name !== 'LayerTreePop') return
  const notify = message => {
    const toast = vnode.context.$screenToast
    if (toast) toast.info(message)
    else if (vnode.context.$message) vnode.context.$message.warning(message)
  }
  const originalGoToLayer = vm.goToLayer
  let locateController
  function goToLayer(id, event) {
    const node = event && event.node && event.node.dataRef
    if (!node || String(node.layertype || node.subtypes || '').toUpperCase() !== 'MVT') return originalGoToLayer.apply(vm, arguments)
    const viewer = window.viewer
    if (!viewer || viewer.isDestroyed()) return
    if (locateController) locateController.abort()
    locateController = new AbortController()
    managerFor(viewer).locate(node, locateController.signal).catch(error => {
      if (error.name !== 'AbortError') notify('图层“' + node.name + '”定位失败：' + error.message)
    })
  }
  // 原树的双击会调用 goToLayer；仅补上 MVT 分支，其它服务继续走原方法。
  vm.goToLayer = goToLayer
  let hydrated = false
  // ★ 跨实例联动：三个弹层实例（首页 / 地图管理 / 壳层隐藏）各自拉树、各自持有
  //   checkedKeys，互相不知晓；但 scene viewer 是共享的（显隐立即生效）。
  //   服务端 getSceneAndTree 返回的是**配置态**，不包含其它实例的运行时勾改，
  //   于是在首页关掉的图层进地图管理后又显示成勾上（用户看到的「不联动」）。
  //   这里按 nodeId 记录「最后一次用户勾选意图」，新实例水合时按它纠正勾选框。
  let lastSeen = null
  function recordSession () {
    const current = vm.checkedKeys || []
    const keys = (Array.isArray(current) ? current : current.checked || []).map(String)
    if (!lastSeen) {
      lastSeen = new Set(keys)
      return
    }
    keys.forEach(k => { if (!lastSeen.has(k)) sessionChecked.set(k, true) })
    lastSeen.forEach(k => { if (keys.indexOf(k) < 0) sessionChecked.set(k, false) })
    lastSeen = new Set(keys)
  }
  function sync() {
    const viewer = window.viewer
    if (!viewer || viewer.isDestroyed() || !vm.treeData) return false
    const manager = managerFor(viewer)
    // 新弹层先有空树，再异步取场景；不要用这个空壳清掉仍显示在地图上的层。
    if (!hydrated && !vm.treeData.length) return false
    if (!hydrated) {
      hydrated = true
      const selected = manager.selectedIds()
      const restored = []
      const walk = nodes => {
        nodes.forEach(node => {
          if (String(node.layertype || node.subtypes || '').toUpperCase() === 'MVT' && selected.has(String(node.oldid || node.id))) restored.push(node.id)
          if (node.children) walk(node.children)
        })
      }
      walk(vm.treeData)
      // ① 会话勾选意图优先：服务端配置态被本会话内其它实例的勾改覆盖
      const current = vm.checkedKeys || []
      const keys = Array.isArray(current) ? current : current.checked || []
      const keySet = new Set(keys.map(String))
      const walkSession = nodes => {
        nodes.forEach(node => {
          const want = sessionChecked.get(String(node.id))
          if (want === true) keySet.add(String(node.id))
          else if (want === false) keySet.delete(String(node.id))
          if (node.children) walkSession(node.children)
        })
      }
      walkSession(vm.treeData)
      // ② 已渲染 MVT 层必须勾上（跨页常驻渲染的恢复，仅加不减）
      restored.forEach(id => keySet.add(String(id)))
      const merged = Array.from(keySet)
      vm.checkedKeys = Array.isArray(current) ? merged : { ...current, checked: merged }
    }
    manager.sync(mvtSelections(vm.treeData, vm.checkedKeys || []), notify)
    recordSession()
    return true
  }
  const unwatch = [vm.$watch('treeData', sync, { deep: true }), vm.$watch('checkedKeys', sync, { deep: true })]
  let tries = 0
  const timer = setInterval(() => {
    if (sync()) clearInterval(timer)
    else if (++tries >= 20) {
      const viewer = window.viewer
      if (viewer && !viewer.isDestroyed() && vm.treeData) managerFor(viewer).sync(new Map(), notify)
      clearInterval(timer)
    }
  }, 500)
  bindings.set(el, {
    vm,
    dispose() {
      unwatch.forEach(stop => stop())
      clearInterval(timer)
      if (locateController) locateController.abort()
      if (vm.goToLayer === goToLayer) vm.goToLayer = originalGoToLayer
    }
  })
  sync()
}

/** 局部指令不改变组件父子关系，原插件的 $parent 契约和宿主 ref 均保留。 */
export default {
  inserted: connect,
  componentUpdated: connect,
  unbind(el, binding, vnode) {
    const state = bindings.get(el)
    if (state && state.vm === vnode.componentInstance) { state.dispose(); bindings.delete(el) }
  }
}
