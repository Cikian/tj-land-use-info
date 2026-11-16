<template>
  <!--
    联动图层树的隐藏挂载点（2026-11-14）
    --------------------------------
    包装插件的全局组件 layer-tree-pop + mvt-layer-tree 指令，隐藏挂载在大屏壳层。
    数据管理页进入「经营性用地信息 / 配套项目信息」模块时，经 layerTreeBridge
    取本实例做程序化勾选，让「地块数据 / 配套数据」切片图层走图层树渲染管线显示。

    ★ 为什么用包装组件而不是在宿主里直接挂载 + $nextTick 注册：
      screen-stage 对默认插槽的渲染是**延迟**的——宿主 mounted + $nextTick 的
      时刻 $refs 里还没有这个组件，注册会静默落空（实测踩坑：联动从未执行）。
      包装组件自己的 mounted 在它**真正挂载完成**的那一刻触发，时机精确。

    ★ 视觉隐藏：fixed 甩出屏幕 + transform 形成包含块（裁掉插件自己的 fixed
      弹层）+ overflow hidden + visibility 继承 + pointer-events none。
  -->
  <div class="screen-hidden-layertree" aria-hidden="true">
    <layer-tree-pop ref="pop" v-mvt-layer-tree :config-min="{ icon: 'map-btns/QueryPick' }" />
  </div>
</template>

<script>
// ★ 指令与桥是两个模块：v-mvt-layer-tree 指令在 utils/mvt/layerTreeDirective，
//   注册函数 registerLayerTreePop 在本目录的 layerTreeBridge —— 之前从这里
//   误引了指令模块的注册函数（不存在 → undefined → 注册静默失败），
//   联动因此永远拿不到图层树实例。
import mvtLayerTree from '@/utils/mvt/layerTreeDirective'
import { registerLayerTreePop } from './layerTreeBridge'

export default {
  name: 'ScreenHiddenLayerTree',
  directives: { mvtLayerTree },
  mounted () {
    // ★ layer-tree-pop 是插件的**异步组件**：包装组件 mounted 的那一刻它还没
    //   解析完成，$refs.pop 是 undefined —— 直接注册会写入 undefined。
    //   轮询等它出现再注册（500ms × 120，覆盖插件慢加载）。
    const register = () => {
      const pop = this.$refs.pop
      if (pop) {
        registerLayerTreePop(pop)
        return true
      }
      return false
    }
    if (register()) return
    let tries = 0
    const timer = setInterval(() => {
      tries++
      if (register() || tries >= 120) clearInterval(timer)
    }, 500)
  }
}
</script>

<style scoped lang="less">
.screen-hidden-layertree {
  position: fixed;
  left: -10000px;
  top: 0;
  width: 800px;
  height: 600px;
  overflow: hidden;
  visibility: hidden;
  pointer-events: none;
  // transform 让插件自己的 fixed 弹层也以本节点为包含块、被 overflow 裁掉
  transform: translateZ(0);
}
</style>
