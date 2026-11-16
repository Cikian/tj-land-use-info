/**
 * 隐藏挂载的插件图层树实例桥。
 * ==================================================================
 * 【为什么需要】
 *   联动图层树的组件（layer-tree-pop，混淆插件的全局组件）原来只在
 *   「数据管理」页隐藏挂载——但插件**首次拉树数据要 10~30 秒**，
 *   用户进数据管理模块时才挂载，等拉完树早已经判定「图层没渲染」。
 *   把挂载点提前到**大屏壳层**（screen/index.vue，打开大屏即挂载），
 *   树数据在打开大屏时就开始拉取，进入模块时树早已就绪、勾选渲染
 *   只剩 1~2 秒。
 *
 * 【用法】
 *   screen/index.vue：隐藏挂载 layer-tree-pop + mvt-layer-tree 指令，
 *   mounted 后 registerLayerTreePop(this.$refs.xxx) 注册实例；
 *   数据管理页（data/index.vue）：ensure 循环里 getLayerTreePop() 取实例
 *   做勾选（findMvt + checkedKeys 合并），与指令的 watch → manager.sync 联动。
 */
let popVm = null

/** 注册隐藏挂载的图层树实例（重复注册以后者为准）。 */
export function registerLayerTreePop (vm) {
  popVm = vm
}

/** 取已注册的图层树实例；未注册/已销毁返回 null。 */
export function getLayerTreePop () {
  if (popVm && popVm._isDestroyed) popVm = null
  return popVm
}
