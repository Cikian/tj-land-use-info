<template>
  <!--
    地图管理页（高保真 地图管理.html 还原 + 接入原拾取查询功能）
    ==================================================================
    与首页同一版式：真实地图保持可见、可交互，画布上叠三块 UI：
      ├─ 左面板  MapToolPanel（工具箱：18 个工具卡片，3 列 × 6 行）
      ├─ 右面板  MapPickPanel（拾取查询：当前图层 + 字段名/字段值表）
      └─ 底部    「属性表」收起态横条（与首页属性表同一条底 bar）

    拾取查询接的是 stargis-function 插件里原有的一套（老三维工作台
    HomeHeader 用的同一个组件，全局注册见 main.js 的 Vue.use(StargisFunction)）：
      · <query-pick>   工具按钮组件：btnClick() 进入 / 退出拾取模式，
        内部挂 Cesium LEFT_CLICK 拾中要素 → 查字段别名 → 组装 {name,value} 行
      · 结果通过全局总线抛出：$bus.$emit('showPickQueryPop', rows|false)
        （false = 退出拾取 / 未拾中）。原弹窗 <query-pick-pop> 不渲染，
        由右侧 MapPickPanel 代替它展示结果，版式与高保真一致。
      · 图层名：插件的 payload 里不带图层名，这里在插件处理器之后
        追加一个自己的 LEFT_CLICK，从 scene.pick 结果里尽力取图层名，
        取不到就保留原值（高保真演示名）。
    注意：容器本身不开启 pointer-events（不加 stage-hit），
    地图的缩放 / 拖动不能被整页模块挡住，由各面板自行 stage-hit。
  -->
  <div class="map-screen">
    <map-tool-panel :pick-active="pickActive" @toggle-pick="togglePick" />
    <map-pick-panel :result="pickRows" :layer-name="pickLayer" />

    <!--
      stargis-function 拾取引擎（原「拾取查询」按钮组件，老工作台顶栏同款）。
      按钮本体隐藏，只借用它的拾取逻辑；$parent.cancelAll() 是它的激活契约
      （btnClick 激活时会先调父组件的 cancelAll 关掉其它工具），本页只有
      拾取一个功能，实现为空即可。
    -->
    <query-pick v-show="false" ref="pickEngine" aria-hidden="true" />

    <!-- 底部「属性表」收起条（设计稿 (460,983) 1000×47，下贴 50） -->
    <section class="map-screen__attr stage-hit">
      <img class="map-screen__attr-bar" :src="hf.attrBar" alt="" aria-hidden="true" />
      <h3 class="map-screen__attr-title">属性表</h3>
      <img class="map-screen__attr-toggle" :src="hf.attrExpand" alt="" aria-hidden="true" />
    </section>
  </div>
</template>

<script>
import MapToolPanel from './MapToolPanel.vue'
import MapPickPanel from './MapPickPanel.vue'
import { hf } from '@/assets/screen-blue'

export default {
  name: 'MapScreen',
  components: { MapToolPanel, MapPickPanel },
  data () {
    return {
      hf,
      /** 「拾取查询」工具卡选中态 = 插件引擎是否处于拾取模式 */
      pickActive: false,
      /** 最近一次拾取结果的 {name,value} 行数组；null = 无结果（面板回退演示数据） */
      pickRows: null,
      /** 拾中要素所在图层名（取不到时保持空，面板显示演示名） */
      pickLayer: '',
    }
  },
  created () {
    // 拾取结果：payload 是 {name,value} 行数组；false = 取消拾取 / 未拾中
    this.$bus.$on('showPickQueryPop', this.handlePickResult)
  },
  beforeDestroy () {
    this.$bus.$off('showPickQueryPop', this.handlePickResult)
    this.stopLayerProbe()
    this.removeLayerNameHandler()
  },
  mounted () {
    // 真实地图（Cesium）由 S3dmViewer 异步创建，等 window.viewer 就绪后再
    // 挂图层名探测；?map=0 预览时永远等不到，轮询若干次后自动放弃
    this.startLayerProbeWhenReady()
  },
  methods: {
    /**
     * 插件按钮的激活契约：btnClick() 激活前会调用 $parent.cancelAll()
     * 关掉兄弟工具。本页只有拾取一个功能，无可取消项，留空实现。
     */
    cancelAll () {},

    /** 左面板「拾取查询」卡片 → 切换插件拾取模式，并把选中态同步回卡片 */
    togglePick () {
      const engine = this.$refs.pickEngine
      if (!engine) return
      engine.btnClick()
      this.pickActive = !!engine.checked
    },

    /** 总线结果 → 右面板字段表；false / 空数组回退演示数据 */
    handlePickResult (payload) {
      this.pickRows = Array.isArray(payload) && payload.length ? payload : null
    },

    /**
     * 图层名探测：在插件自己的 LEFT_CLICK 处理器**之后**再挂一个
     * （子组件 mounted 早于本组件，插件的处理器先注册），从 pick 结果里
     * 尽力读图层名；失败保持原值，不干扰插件的拾取流程。
     */
    setupLayerNameProbe () {
      if (!window.Cesium || !window.viewer || this._layerProbeHandler) return !!this._layerProbeHandler
      try {
        const Cesium = window.Cesium
        this._layerProbeHandler = new Cesium.ScreenSpaceEventHandler(window.viewer.scene.canvas)
        this._layerProbeHandler.setInputAction((movement) => {
          try {
            const picked = window.viewer.scene.pick(movement.position)
            if (!picked) return
            const entity = picked.id
            const primitive = picked.primitive || picked
            const name = (entity && entity.name) || primitive.name || primitive.path
            if (name) this.pickLayer = String(name)
          } catch (error) { /* 图层名取不到就保留原值 */ }
        }, Cesium.ScreenSpaceEventType.LEFT_CLICK)
      } catch (error) {
        this._layerProbeHandler = null
      }
      return !!this._layerProbeHandler
    },

    startLayerProbeWhenReady () {
      this.stopLayerProbe()
      let tries = 0
      this._layerProbeTimer = setInterval(() => {
        if (this.setupLayerNameProbe() || ++tries > 20) this.stopLayerProbe()
      }, 500)
    },

    stopLayerProbe () {
      if (this._layerProbeTimer) {
        clearInterval(this._layerProbeTimer)
        this._layerProbeTimer = null
      }
    },

    removeLayerNameHandler () {
      if (this._layerProbeHandler) {
        try { this._layerProbeHandler.destroy() } catch (error) { /* 已销毁则忽略 */ }
        this._layerProbeHandler = null
      }
    },
  },
}
</script>

<style scoped lang="less">
.map-screen {
  position: absolute;
  inset: 0;
  // 整页容器不拦截鼠标：地图必须保持可交互，各面板自己 stage-hit
  pointer-events: none;

  &__attr {
    position: absolute;
    // 设计稿 x=460..1460、下贴 50（与首页属性表收起态同位）
    left: 460px;
    right: 460px;
    bottom: 50px;
    height: 47px;
  }

  &__attr-bar {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 47px;
    display: block;
    pointer-events: none;
  }

  &__attr-title {
    position: absolute;
    left: 48px;
    // 条高 47、字 22 行高：与首页收起态一致微微下沉
    top: 14px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 22px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__attr-toggle {
    position: absolute;
    // 设计稿图标 17×14 @ (963, 17)，即距右边界 20px
    right: 20px;
    top: 17px;
    width: 17px;
    height: 14px;
    display: block;
    pointer-events: none;
  }
}
</style>
