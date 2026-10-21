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

    地名定位接的是 HomeHeader.vue 里那份可读源码（与插件 QueryPlaceName
    同一套实现），提取成 MapPlacePanel：右面板切到搜索界面，搜到结果列表，
    点结果画图定位 + 属性落字段表；配置来自中台功能配置
    sysConfig.ShortCut → QueryPlaceName（没配置时面板显示提示）。

    道路定位走插件路线（弹窗含道路/路口双页签，逻辑在混淆包里无法可读
    重建）：隐藏 <query-road> 按钮引擎 + 右面板容器内渲染 <query-road-pop>，
    configMin 来自 sysConfig.QueryStatistics → QueryRoad（老工作台
    HomeQueryBody/Footer 同款传法）。没配置时点卡片只弹提示不进模式。
    弹窗原样式是老工作台浮层，靠 .map-screen__road-host 下的深度覆盖
    塞进右面板框里（尺寸铺满、去定位、可滚动）。

    三个工具互斥：激活一个会收起其它两个（插件引擎经 cancelAll 契约
    回调父组件；地名定位由各 toggle 显式关闭）。弹窗若被插件内部关闭
    （showQueryRoadPop false），经总线监听同步回卡片状态。
    注意：容器本身不开启 pointer-events（不加 stage-hit），
    地图的缩放 / 拖动不能被整页模块挡住，由各面板自行 stage-hit。
  -->
  <div class="map-screen">
    <map-tool-panel
      :active-tool="activeTool"
      @toggle-tool="toggleToolByKey"
      @toggle-hd="handleHdExport"
    />

    <!--
      右面板多模式：地名定位 / 道路定位（插件弹窗）/ 空间查询（插件弹窗）/
      属性查询（插件弹窗）/ 综合查询（插件弹窗）/ 综合统计（插件弹窗）/
      拾取查询。其余状态（含默认空态）都是拾取查询面板——空态下按设计稿
      展示演示数据。v-if / v-else-if 让面板互斥挂载：切换时地名面板的
      beforeDestroy 会自动清掉它画的图形和气泡标签。
    -->
    <template v-if="activeTool === 'place'">
      <map-place-panel />
    </template>
    <template v-else-if="activeTool === 'road' && roadConfig">
      <!-- 面板标题（画布坐标层，与其它右面板同位） -->
      <img class="map-screen__road-title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
      <h2 class="map-screen__road-title">道路定位</h2>
      <!-- 插件弹窗容器：铺满右面板框，内部样式深度覆盖见样式区 -->
      <div class="map-screen__road-host stage-hit">
        <query-road-pop :config-min="roadConfig" />
      </div>
    </template>
    <template v-else-if="activeTool === 'spatial' && spatialConfig">
      <img class="map-screen__spatial-title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
      <h2 class="map-screen__spatial-title">空间查询</h2>
      <div class="map-screen__spatial-host stage-hit">
        <query-spatial-pop :config-min="spatialConfig" />
      </div>
    </template>
    <template v-else-if="activeTool === 'attr' && attrConfig">
      <img class="map-screen__attrpanel-title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
      <h2 class="map-screen__attrpanel-title">属性查询</h2>
      <div class="map-screen__attr-host stage-hit">
        <query-attribute-pop :config-min="attrConfig" />
      </div>
    </template>
    <template v-else-if="activeTool === 'comp' && compConfig">
      <img class="map-screen__comp-title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
      <h2 class="map-screen__comp-title">综合查询</h2>
      <div class="map-screen__comp-host stage-hit">
        <query-comprehensive-pop :config-min="compConfig" />
      </div>
    </template>
    <template v-else-if="activeTool === 'stat' && statConfig">
      <img class="map-screen__stat-title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
      <h2 class="map-screen__stat-title">综合统计</h2>
      <div class="map-screen__stat-host stage-hit">
        <query-statistic-pop :config-min="statConfig" />
      </div>
    </template>
    <!-- 其余插件弹窗工具（书签/动画导航/图层管理/高程/地下/地形透明）：
         通用宿主容器 + 数据驱动渲染，标题取自注册表 -->
    <template v-else-if="activePopTool">
      <img class="map-screen__plugin-title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
      <h2 class="map-screen__plugin-title">{{ activePopTool.title }}</h2>
      <div class="map-screen__plugin-host stage-hit">
        <component :is="activePopTool.popTag" ref="activePopRef" :config-min="activePopTool.config" />
      </div>
    </template>
    <map-pick-panel v-else :result="pickRows" :layer-name="pickLayer" />

    <!--
      stargis-function 量测/地形类开关引擎（原「直线量测」「地形透明」等按钮
      组件）：btnClick() 直接在地图上进入对应模式（画线/画面/地形开关等），
      没有独立弹窗；激活契约同其它引擎。需要真实地图，?map=0 或未就绪时
      由 toggle 兜底提示。注册表见下方 PLUGIN_TOOLS。
    -->
    <component
      v-for="tool in engineTools"
      :key="'engine-' + tool.key"
      :is="tool.engineTag"
      v-show="false"
      :ref="'engine-' + tool.key"
      :config-min="tool.config"
      aria-hidden="true"
    />

    <!--
      stargis-function 拾取引擎（原「拾取查询」按钮组件，老工作台顶栏同款）。
      按钮本体隐藏，只借用它的拾取逻辑；$parent.cancelAll() 是它的激活契约
      （btnClick 激活时会先调父组件的 cancelAll 关掉其它工具）。
    -->
    <query-pick v-show="false" ref="pickEngine" aria-hidden="true" />

    <!--
      stargis-function 道路定位引擎（原「道路定位」按钮组件，老工作台
      查询统计模块同款）。按钮本体隐藏，只借用它的开关逻辑：btnClick()
      经总线 showQueryRoadPop 开关右上角的弹窗；激活契约同拾取引擎。
      只有配置了数据源才挂载（弹窗与引擎都依赖 configMin）。
    -->
    <query-road
      v-if="roadConfig"
      v-show="false"
      ref="roadEngine"
      :config-min="roadConfig"
      aria-hidden="true"
    />

    <!--
      stargis-function 空间查询引擎（原「空间查询」按钮组件，老工作台
      查询统计模块同款）。btnClick() 经总线 showQuerySpatialPop 开关
      右上角的弹窗；激活契约同拾取/道路引擎。只有配置了数据源才挂载。
    -->
    <query-spatial
      v-if="spatialConfig"
      v-show="false"
      ref="spatialEngine"
      :config-min="spatialConfig"
      aria-hidden="true"
    />

    <!--
      stargis-function 属性查询引擎（原「属性查询」按钮组件，老工作台
      查询统计模块同款）。btnClick() 经总线 showQueryAttributePop 开关
      右上角的弹窗；激活契约同其它引擎。只有配置了数据源才挂载。
    -->
    <query-attribute
      v-if="attrConfig"
      v-show="false"
      ref="attrEngine"
      :config-min="attrConfig"
      aria-hidden="true"
    />

    <!--
      stargis-function 综合查询引擎（原「综合查询」按钮组件，老工作台
      查询统计模块同款；空间范围 + 属性条件 + 查询方案书签三合一）。
      btnClick() 经总线 showQueryComprehensivePop 开关右上角的弹窗；
      激活契约同其它引擎。只有配置了数据源才挂载。
    -->
    <query-comprehensive
      v-if="compConfig"
      v-show="false"
      ref="compEngine"
      :config-min="compConfig"
      aria-hidden="true"
    />

    <!--
      stargis-function 综合统计引擎（原「综合统计」按钮组件，老工作台
      查询统计模块同款；空间/属性筛选 + 分组统计图表）。
      btnClick() 经总线 showQueryStatisticPop 开关右上角的弹窗；
      激活契约同其它引擎。只有配置了数据源才挂载。
    -->
    <query-statistic
      v-if="statConfig"
      v-show="false"
      ref="statEngine"
      :config-min="statConfig"
      aria-hidden="true"
    />

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
import MapPlacePanel from './MapPlacePanel.vue'
import { hf } from '@/assets/screen-blue'

/**
 * 插件开关/弹窗类工具注册表（卡片 key → stargis-function 组件）。
 * 名称对应关系来自 package.html 的插件清单与 HomeHeader 兜底配置：
 *   直线量测=MeasureLine、水平量测=MeasureSpace（3D 空间测量，无独立
 *   垂直量测组件，垂直量测共用一个 MeasureSpace 实例）、面积量测=MeasureArea、
 *   视图书签=Bookmarks、动画导航=AnimationNavigation、图层管理=LayerTree、
 *   地形透明=TransparentTerrain。
 * pop = null 的是开关型（直接在地图上生效，无独立弹窗）；有 pop 的走
 * 「先挂弹窗、再让引擎发 showXxxPop」的通用流程。
 * icon：插件按钮的切图路径（引擎隐藏后只影响它自己的按钮图，不影响功能）。
 * ⚠ 三个例外不走本注册表：
 *   高清出图——老工作台无按钮引擎，直接驱动总线 showScenePlottingPop
 *   开关 ScenePlottingPop（见 toggleHdExport）；
 *   开启地下模式——纯 Cesium 场景开关（scene.undergroundMode），
 *   插件里没有对应组件（见 toggleUnderground）；
 *   开启高程——老系统语义是给数据源图层设置 DEM 可用
 *   （featureLayer.setDemAvailable，见旧系统 common.js openHeight），
 *   同样是本地场景开关（见 toggleHeight）。
 */
const PLUGIN_TOOLS = [
  { key: 'measureLine', title: '直线量测', engine: 'MeasureLine', pop: null, event: null, icon: 'map-btns/MeasureLine' },
  { key: 'measureHorizontal', title: '水平量测', engine: 'MeasureSpace', pop: null, event: null, icon: 'map-btns/MeasureSpace' },
  { key: 'measureVertical', title: '垂直量测', engine: 'MeasureSpace', pop: null, event: null, icon: 'map-btns/MeasureSpace' },
  { key: 'measureArea', title: '面积量测', engine: 'MeasureArea', pop: null, event: null, icon: 'map-btns/MeasureArea' },
  { key: 'bookmark', title: '视图书签', engine: 'Bookmarks', pop: 'BookmarksPop', event: 'showHeaderBookmarksPop', icon: 'map-btns/Bookmarks' },
  { key: 'tour', title: '动画导航', engine: 'AnimationNavigation', pop: 'AnimationNavigationPop', event: 'showAnimationNavigationPop', icon: 'map-btns/AnimationNavigation' },
  { key: 'layers', title: '图层管理', engine: 'LayerTree', pop: 'LayerTreePop', event: null, icon: 'map-btns/QueryPick' },
  { key: 'terrainAlpha', title: '地形透明', engine: 'TransparentTerrain', pop: 'TransparentTerrainPop', event: 'showTerrainPop', icon: 'map-btns/TransparentTerrain' },
  { key: 'hdExport', title: '高清出图', engine: null, pop: 'ScenePlottingPop', event: 'showScenePlottingPop', icon: null },
]

/** PascalCase → kebab-case（组件名 → 全局注册的标签名） */
function toKebab (name) {
  return name.replace(/([A-Z])/g, '-$1').toLowerCase().replace(/^-/, '')
}

export default {
  name: 'MapScreen',
  components: { MapToolPanel, MapPickPanel, MapPlacePanel },
  data () {
    return {
      hf,
      /**
       * 当前激活的左侧工具：'' 无 / 'pick' 拾取查询 / 'place' 地名定位 /
       * 'road' 道路定位。右面板跟随它切换；无激活工具时展示拾取查询的
       * 演示数据（设计稿空态）。
       */
      activeTool: '',
      /** 「拾取查询」是否处于拾取模式（= 插件引擎 checked） */
      pickActive: false,
      /** 最近一次拾取结果的 {name,value} 行数组；null = 无结果（面板回退演示数据） */
      pickRows: null,
      /** 拾中要素所在图层名（取不到时保持空，面板显示演示名） */
      pickLayer: '',
      /**
       * 道路定位插件配置（sysConfig.QueryStatistics → QueryRoad 项）。
       * 没配置时为 null：道路卡片点击只提示，不挂载插件组件
       * （QueryRoadPop 的 created 直接读 configMin 字段，缺了会崩）。
       */
      roadConfig: null,
      /** 道路定位弹窗开关状态（= 道路引擎 checked，随总线事件同步） */
      roadActive: false,
      /**
       * 空间查询插件配置（sysConfig.QueryStatistics → QuerySpatial 项）。
       * 依赖同道路定位：弹窗 created 直读 configMin，没配置不挂载。
       */
      spatialConfig: null,
      /** 空间查询弹窗开关状态（= 空间引擎 checked，随总线事件同步） */
      spatialActive: false,
      /**
       * 属性查询插件配置（sysConfig.QueryStatistics → QueryAttribute 项）。
       * 依赖同道路定位：弹窗 created 直读 configMin，没配置不挂载。
       */
      attrConfig: null,
      /** 属性查询弹窗开关状态（= 属性引擎 checked，随总线事件同步） */
      attrActive: false,
      /**
       * 综合查询插件配置（sysConfig.QueryStatistics → QueryComprehensive 项）。
       * 依赖同道路定位：弹窗 created 直读 configMin，没配置不挂载。
       */
      compConfig: null,
      /** 综合查询弹窗开关状态（= 综合引擎 checked，随总线事件同步） */
      compActive: false,
      /**
       * 综合统计插件配置（sysConfig.QueryStatistics → QueryStatistic 项）。
       * 依赖同道路定位：弹窗 created 直读 configMin，没配置不挂载。
       */
      statConfig: null,
      /** 综合统计弹窗开关状态（= 综合统计引擎 checked，随总线事件同步） */
      statActive: false,
      /** 开启高程的开关状态（驱动数据源图层 setDemAvailable） */
      heightOn: false,
    }
  },
  computed: {
    /**
     * 当前激活的弹窗型插件工具（书签/动画导航/图层管理/高程/地下/地形透明）。
     * 供通用宿主容器渲染对应弹窗。
     */
    activePopTool () {
      const def = PLUGIN_TOOLS.find((tool) => tool.key === this.activeTool && tool.pop)
      if (!def) return null
      return {
        ...def,
        popTag: toKebab(def.pop),
        config: { icon: def.icon },
      }
    },
    /** 需要挂载的隐藏引擎清单（无引擎的工具如高清出图跳过；configMin 只需 icon） */
    engineTools () {
      return PLUGIN_TOOLS
        .filter((tool) => tool.engine)
        .map((tool) => ({
          ...tool,
          engineTag: toKebab(tool.engine),
          config: { icon: tool.icon },
        }))
    },
  },
  created () {
    // 拾取结果：payload 是 {name,value} 行数组；false = 取消拾取 / 未拾中
    this.$bus.$on('showPickQueryPop', this.handlePickResult)
    // 道路定位弹窗开关（插件按钮 / 弹窗自带关闭钮都会发）
    this.$bus.$on('showQueryRoadPop', this.handleRoadPopEvent)
    // 空间查询弹窗开关
    this.$bus.$on('showQuerySpatialPop', this.handleSpatialPopEvent)
    // 属性查询弹窗开关
    this.$bus.$on('showQueryAttributePop', this.handleAttrPopEvent)
    // 综合查询弹窗开关
    this.$bus.$on('showQueryComprehensivePop', this.handleCompPopEvent)
    // 综合统计弹窗开关
    this.$bus.$on('showQueryStatisticPop', this.handleStatPopEvent)
    // 其余插件弹窗工具的开关事件（数据驱动注册）
    PLUGIN_TOOLS.forEach((tool) => {
      if (!tool.event) return
      this.$bus.$on(tool.event, (payload) => this.handlePluginPopEvent(tool.key, payload))
    })
    this.loadRoadConfig()
    this.loadSpatialConfig()
    this.loadAttrConfig()
    this.loadCompConfig()
    this.loadStatConfig()
  },
  beforeDestroy () {
    this.$bus.$off('showPickQueryPop', this.handlePickResult)
    this.$bus.$off('showQueryRoadPop', this.handleRoadPopEvent)
    this.$bus.$off('showQuerySpatialPop', this.handleSpatialPopEvent)
    this.$bus.$off('showQueryAttributePop', this.handleAttrPopEvent)
    this.$bus.$off('showQueryComprehensivePop', this.handleCompPopEvent)
    this.$bus.$off('showQueryStatisticPop', this.handleStatPopEvent)
    PLUGIN_TOOLS.forEach((tool) => {
      if (!tool.event) return
      this.$bus.$off(tool.event, this.handlePluginPopEvent)
    })
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
     * 关掉兄弟工具（拾取/道路/空间/属性/综合引擎激活时都会走到这里）。
     */
    cancelAll () {
      this.closeOtherTools(null)
    },

    /** 左面板「拾取查询」卡片 → 切换插件拾取模式，并把状态同步回卡片 */
    togglePick () {
      const engine = this.$refs.pickEngine
      if (!engine) return
      this.closeOtherTools('pick')
      try {
        engine.btnClick()
      } catch (error) {
        // 插件引擎进入拾取模式要访问 window.viewer.scene：
        // ?map=0 预览或 Cesium 还没就绪时会抛错，这里兜住并复位引擎，
        // 避免「卡片亮着但拾取实际没生效」的卡死状态
        try { if (engine.checked) engine.cancelChecked() } catch (ignored) { /* 引擎复位失败不必再处理 */ }
        this.pickActive = false
        if (this.activeTool === 'pick') this.activeTool = ''
        this.$screenToast.info('地图未就绪，拾取查询暂不可用')
        return
      }
      this.pickActive = !!engine.checked
      this.activeTool = this.pickActive ? 'pick' : ''
    },

    /** 左面板「地名定位」卡片 → 切换右面板到地名定位搜索；再点一次退出 */
    /**
     * 复位除指定工具外的全部插件引擎 + 收起地名定位面板。
     * 五个工具互斥的统一入口：每个 toggle 的打开路径先调它。
     * （插件引擎激活时自己也会调 $parent.cancelAll()，两者殊途同归。）
     */
    closeOtherTools (current) {
      if (current !== 'place' && this.activeTool === 'place') this.activeTool = ''
      if (current !== 'pick') this.closePickEngine()
      // 高清出图无引擎：切走时直接经总线关闭出图弹窗
      if (current !== 'hdExport' && this.activeTool === 'hdExport') {
        this.$bus.$emit('showScenePlottingPop', false)
      }
      // 注册表里的插件引擎（量测/书签/动画导航/图层管理/高程/地下/地形透明）
      PLUGIN_TOOLS.forEach((tool) => {
        if (tool.key === current) return
        const engine = this.getEngine(tool.key)
        if (engine && engine.checked) {
          try { engine.btnClick() } catch (error) { /* 复位失败由总线监听兜底 */ }
        }
      })
      if (current !== 'road') this.closeRoadEngine()
      if (current !== 'spatial') this.closeSpatialEngine()
      if (current !== 'attr') this.closeAttrEngine()
      if (current !== 'comp') this.closeCompEngine()
      if (current !== 'stat') this.closeStatEngine()
    },

    togglePlace () {
      if (this.activeTool === 'place') {
        this.activeTool = ''
        return
      }
      this.closeOtherTools('place')
      this.activeTool = 'place'
    },

    /**
     * 左面板「道路定位」卡片 → 切换右面板到插件道路定位弹窗；再点一次退出。
     * 弹窗依赖 configMin（功能配置里的数据源），没配置时只提示不进模式。
     */
    toggleRoad () {
      if (!this.roadConfig) {
        this.$screenToast.info('道路定位未配置数据源，请在 GIS 功能配置「查询统计 → 道路定位」里配置后使用')
        return
      }
      const engine = this.$refs.roadEngine
      if (this.activeTool === 'road') {
        // 再点一次 = 关闭弹窗（引擎走 cancelChecked，总线发 false，监听里同步状态）
        if (engine) engine.btnClick()
        this.roadActive = false
        this.activeTool = ''
        return
      }
      this.closeOtherTools('road')
      if (!engine) {
        // 配置刚就绪、引擎还没挂载完成的瞬间（正常使用几乎不会遇到）
        this.$screenToast.info('道路定位组件未就绪，请稍后再试')
        return
      }
      // 顺序不能反：先切模式把弹窗挂载出来，再让引擎发「开」事件——
      // 弹窗的 $once('showQueryRoadPop') 监听是挂载后才注册的，
      // 先发后挂会让弹窗错过事件、内容一直隐藏
      this.activeTool = 'road'
      this.$nextTick(() => {
        engine.btnClick()
        this.roadActive = !!engine.checked
        if (!this.roadActive && this.activeTool === 'road') this.activeTool = ''
      })
    },

    /** 复位拾取引擎（三工具互斥用）：无地图时引擎会抛错，兜住并归零状态 */
    closePickEngine () {
      if (!this.pickActive || !this.$refs.pickEngine) return
      try { this.$refs.pickEngine.btnClick() } catch (error) { /* 引擎内部访问 viewer 失败，状态强制归零 */ }
      this.pickActive = false
      if (this.activeTool === 'pick') this.activeTool = ''
    },

    /** 复位道路引擎（三工具互斥用）：btnClick 走 cancelChecked，总线发 false */
    closeRoadEngine () {
      if (!this.roadActive || !this.$refs.roadEngine) return
      try { this.$refs.roadEngine.btnClick() } catch (error) { /* 复位失败由总线监听兜底 */ }
      this.roadActive = false
      if (this.activeTool === 'road') this.activeTool = ''
    },

    /** 道路定位弹窗开关总线事件：弹窗自带关闭钮关闭时同步回卡片状态 */
    handleRoadPopEvent (payload) {
      this.roadActive = !!payload
      if (!payload && this.activeTool === 'road') this.activeTool = ''
    },

    /** 读取道路定位的功能配置（sysConfig.QueryStatistics → QueryRoad 项） */
    loadRoadConfig () {
      const config = this.$store.getters.sysConfig || {}
      const module = config.QueryStatistics
      const item = module && module.children
        ? module.children.find((child) => child.componentname === 'QueryRoad')
        : null
      this.roadConfig = item || null
    },

    /**
     * 左面板「空间查询」卡片 → 切换右面板到插件空间查询面板；再点一次退出。
     * 弹窗依赖 configMin（功能配置里的数据源），没配置时只提示不进模式。
     */
    toggleSpatial () {
      if (!this.spatialConfig) {
        this.$screenToast.info('空间查询未配置数据源，请在 GIS 功能配置「查询统计 → 空间查询」里配置后使用')
        return
      }
      const engine = this.$refs.spatialEngine
      if (this.activeTool === 'spatial') {
        // 再点一次 = 关闭弹窗（引擎走 cancelChecked，总线发 false，监听里同步状态）
        if (engine) engine.btnClick()
        this.spatialActive = false
        this.activeTool = ''
        return
      }
      this.closeOtherTools('spatial')
      if (!engine) {
        // 配置刚就绪、引擎还没挂载完成的瞬间（正常使用几乎不会遇到）
        this.$screenToast.info('空间查询组件未就绪，请稍后再试')
        return
      }
      // 顺序不能反：先切模式把弹窗挂载出来，再让引擎发「开」事件
      // （与道路定位同一坑：弹窗的 $once 监听挂载后才注册）
      this.activeTool = 'spatial'
      this.$nextTick(() => {
        engine.btnClick()
        this.spatialActive = !!engine.checked
        if (!this.spatialActive && this.activeTool === 'spatial') this.activeTool = ''
      })
    },

    /** 复位空间引擎（四工具互斥用）：btnClick 走 cancelChecked，总线发 false */
    closeSpatialEngine () {
      if (!this.spatialActive || !this.$refs.spatialEngine) return
      try { this.$refs.spatialEngine.btnClick() } catch (error) { /* 复位失败由总线监听兜底 */ }
      this.spatialActive = false
      if (this.activeTool === 'spatial') this.activeTool = ''
    },

    /** 空间查询弹窗开关总线事件：弹窗自带关闭钮关闭时同步回卡片状态 */
    handleSpatialPopEvent (payload) {
      this.spatialActive = !!payload
      if (!payload && this.activeTool === 'spatial') this.activeTool = ''
    },

    /** 读取空间查询的功能配置（sysConfig.QueryStatistics → QuerySpatial 项） */
    loadSpatialConfig () {
      const config = this.$store.getters.sysConfig || {}
      const module = config.QueryStatistics
      const item = module && module.children
        ? module.children.find((child) => child.componentname === 'QuerySpatial')
        : null
      this.spatialConfig = item || null
    },

    /**
     * 左面板「属性查询」卡片 → 切换右面板到插件属性查询面板；再点一次退出。
     * 弹窗依赖 configMin（功能配置里的数据源），没配置时只提示不进模式。
     */
    toggleAttr () {
      if (!this.attrConfig) {
        this.$screenToast.info('属性查询未配置数据源，请在 GIS 功能配置「查询统计 → 属性查询」里配置后使用')
        return
      }
      const engine = this.$refs.attrEngine
      if (this.activeTool === 'attr') {
        // 再点一次 = 关闭弹窗（引擎走 cancelChecked，总线发 false，监听里同步状态）
        if (engine) engine.btnClick()
        this.attrActive = false
        this.activeTool = ''
        return
      }
      this.closeOtherTools('attr')
      if (!engine) {
        // 配置刚就绪、引擎还没挂载完成的瞬间（正常使用几乎不会遇到）
        this.$screenToast.info('属性查询组件未就绪，请稍后再试')
        return
      }
      // 顺序不能反：先切模式把弹窗挂载出来，再让引擎发「开」事件
      // （与道路/空间同一坑：弹窗的 $once 监听挂载后才注册）
      this.activeTool = 'attr'
      this.$nextTick(() => {
        engine.btnClick()
        this.attrActive = !!engine.checked
        if (!this.attrActive && this.activeTool === 'attr') this.activeTool = ''
      })
    },

    /** 复位属性引擎（五工具互斥用）：btnClick 走 cancelChecked，总线发 false */
    closeAttrEngine () {
      if (!this.attrActive || !this.$refs.attrEngine) return
      try { this.$refs.attrEngine.btnClick() } catch (error) { /* 复位失败由总线监听兜底 */ }
      this.attrActive = false
      if (this.activeTool === 'attr') this.activeTool = ''
    },

    /** 属性查询弹窗开关总线事件：弹窗自带关闭钮关闭时同步回卡片状态 */
    handleAttrPopEvent (payload) {
      this.attrActive = !!payload
      if (!payload && this.activeTool === 'attr') this.activeTool = ''
    },

    /** 读取属性查询的功能配置（sysConfig.QueryStatistics → QueryAttribute 项） */
    loadAttrConfig () {
      const config = this.$store.getters.sysConfig || {}
      const module = config.QueryStatistics
      const item = module && module.children
        ? module.children.find((child) => child.componentname === 'QueryAttribute')
        : null
      this.attrConfig = item || null
    },

    /**
     * 左面板「综合查询」卡片 → 切换右面板到插件综合查询面板；再点一次退出。
     * 弹窗依赖 configMin（功能配置里的数据源），没配置时只提示不进模式。
     */
    toggleComp () {
      if (!this.compConfig) {
        this.$screenToast.info('综合查询未配置数据源，请在 GIS 功能配置「查询统计 → 综合查询」里配置后使用')
        return
      }
      const engine = this.$refs.compEngine
      if (this.activeTool === 'comp') {
        // 再点一次 = 关闭弹窗（引擎走 cancelChecked，总线发 false，监听里同步状态）
        if (engine) engine.btnClick()
        this.compActive = false
        this.activeTool = ''
        return
      }
      this.closeOtherTools('comp')
      if (!engine) {
        // 配置刚就绪、引擎还没挂载完成的瞬间（正常使用几乎不会遇到）
        this.$screenToast.info('综合查询组件未就绪，请稍后再试')
        return
      }
      // 顺序不能反：先切模式把弹窗挂载出来，再让引擎发「开」事件
      // （与道路/空间/属性同一坑：弹窗的 $once 监听挂载后才注册）
      this.activeTool = 'comp'
      this.$nextTick(() => {
        engine.btnClick()
        this.compActive = !!engine.checked
        if (!this.compActive && this.activeTool === 'comp') this.activeTool = ''
      })
    },

    /** 复位综合引擎（六工具互斥用）：btnClick 走 cancelChecked，总线发 false */
    closeCompEngine () {
      if (!this.compActive || !this.$refs.compEngine) return
      try { this.$refs.compEngine.btnClick() } catch (error) { /* 复位失败由总线监听兜底 */ }
      this.compActive = false
      if (this.activeTool === 'comp') this.activeTool = ''
    },

    /** 综合查询弹窗开关总线事件：弹窗自带关闭钮关闭时同步回卡片状态 */
    handleCompPopEvent (payload) {
      this.compActive = !!payload
      if (!payload && this.activeTool === 'comp') this.activeTool = ''
    },

    /** 读取综合查询的功能配置（sysConfig.QueryStatistics → QueryComprehensive 项） */
    loadCompConfig () {
      const config = this.$store.getters.sysConfig || {}
      const module = config.QueryStatistics
      const item = module && module.children
        ? module.children.find((child) => child.componentname === 'QueryComprehensive')
        : null
      this.compConfig = item || null
    },

    /**
     * 左面板「综合统计」卡片 → 切换右面板到插件综合统计面板；再点一次退出。
     * 弹窗依赖 configMin（功能配置里的数据源），没配置时只提示不进模式。
     */
    toggleStat () {
      if (!this.statConfig) {
        this.$screenToast.info('综合统计未配置数据源，请在 GIS 功能配置「查询统计 → 综合统计」里配置后使用')
        return
      }
      const engine = this.$refs.statEngine
      if (this.activeTool === 'stat') {
        // 再点一次 = 关闭弹窗（引擎走 cancelChecked，总线发 false，监听里同步状态）
        if (engine) engine.btnClick()
        this.statActive = false
        this.activeTool = ''
        return
      }
      this.closeOtherTools('stat')
      if (!engine) {
        // 配置刚就绪、引擎还没挂载完成的瞬间（正常使用几乎不会遇到）
        this.$screenToast.info('综合统计组件未就绪，请稍后再试')
        return
      }
      // 顺序不能反：先切模式把弹窗挂载出来，再让引擎发「开」事件
      // （与道路/空间/属性/综合同一坑：弹窗的 $once 监听挂载后才注册）
      this.activeTool = 'stat'
      this.$nextTick(() => {
        engine.btnClick()
        this.statActive = !!engine.checked
        if (!this.statActive && this.activeTool === 'stat') this.activeTool = ''
      })
    },

    /** 复位综合统计引擎（七工具互斥用）：btnClick 走 cancelChecked，总线发 false */
    closeStatEngine () {
      if (!this.statActive || !this.$refs.statEngine) return
      try { this.$refs.statEngine.btnClick() } catch (error) { /* 复位失败由总线监听兜底 */ }
      this.statActive = false
      if (this.activeTool === 'stat') this.activeTool = ''
    },

    /** 综合统计弹窗开关总线事件：弹窗自带关闭钮关闭时同步回卡片状态 */
    handleStatPopEvent (payload) {
      this.statActive = !!payload
      if (!payload && this.activeTool === 'stat') this.activeTool = ''
    },

    /** 读取综合统计的功能配置（sysConfig.QueryStatistics → QueryStatistic 项） */
    loadStatConfig () {
      const config = this.$store.getters.sysConfig || {}
      const module = config.QueryStatistics
      const item = module && module.children
        ? module.children.find((child) => child.componentname === 'QueryStatistic')
        : null
      this.statConfig = item || null
    },

    /**
     * 工具卡片点击分发：已接入的按 key 走各自的 toggle。
     * ⚠ 卡片 key 与模式 key 不同名（卡片 locateName/locateRoad/querySpatial/
     *   queryAttr/queryCombined/statistics ↔ 模式 place/road/spatial/attr/
     *   comp/stat），先查 MODE_KEY_MAP 换算；其余（量测/书签/动画导航/
     *   图层管理/高程/地下/地形透明/高清出图）与卡片 key 同名，直接走注册表。
     */
    toggleToolByKey (key) {
      const MODE_KEY_MAP = {
        locateName: 'place',
        locateRoad: 'road',
        querySpatial: 'spatial',
        queryAttr: 'attr',
        queryCombined: 'comp',
        statistics: 'stat',
      }
      if (MODE_KEY_MAP[key]) key = MODE_KEY_MAP[key]
      if (key === 'pick') return this.togglePick()
      if (key === 'place') return this.togglePlace()
      if (key === 'road') return this.toggleRoad()
      if (key === 'spatial') return this.toggleSpatial()
      if (key === 'attr') return this.toggleAttr()
      if (key === 'comp') return this.toggleComp()
      if (key === 'stat') return this.toggleStat()
      if (key === 'hdExport') return this.toggleHdExport()
      if (key === 'underground') return this.toggleUnderground()
      if (key === 'terrainHeight') return this.toggleHeight()
      const def = PLUGIN_TOOLS.find((tool) => tool.key === key)
      if (def) return this.togglePluginTool(def)
    },

    /**
     * 开启地下模式：纯 Cesium 场景开关（插件里没有对应组件）。
     * 开：scene.undergroundMode = true + 隐藏地表球体 + 允许相机进入地表以下；
     * 关：全部还原。第二次点击卡片即关闭。
     * ⚠ 以卡片状态（activeTool）驱动而不是回读 scene.undergroundMode——
     *   SuperMap 场景属性连续写入时回读不可靠。
     */
    toggleUnderground () {
      const viewer = window.viewer
      if (!viewer || !viewer.scene) {
        this.$screenToast.info('地图未就绪，开启地下模式暂不可用')
        return
      }
      const scene = viewer.scene
      const on = this.activeTool !== 'underground'
      // ⚠ 顺序有讲究：恢复 globe.show = true 会把 SuperMap 的 undergroundMode
      // 顶回 true，所以关路径必须先恢复地表、再关地下模式；
      // 开路径反过来（先开地下模式、再藏地表），实测两序都不能反。
      if (on) {
        scene.undergroundMode = true
        if (scene.globe) scene.globe.show = false
      } else {
        if (scene.globe) scene.globe.show = true
        scene.undergroundMode = false
      }
      // 允许相机缩放到地表以下（还原时收回）
      scene.screenSpaceCameraController.minimumZoomDistance = on ? -1000 : 0
      this.activeTool = on ? 'underground' : ''
    },

    /**
     * 开启高程：给场景里所有已加载的 S3M 数据源图层设置 DEM 可用
     * （语义对照旧系统 common.js 的 openHeight/closeHeight：
     *   featureLayer.setDemAvailable(true/false)，让图层几何按地形起伏显示）。
     * 开关状态以 heightOn 数据字段驱动（不回读图层属性）。
     */
    toggleHeight () {
      const viewer = window.viewer
      if (!viewer || !viewer.scene) {
        this.$screenToast.info('地图未就绪，开启高程暂不可用')
        return
      }
      const queue = (viewer.scene._layers && viewer.scene._layers._layerQueue) || []
      if (!queue.length) {
        this.$screenToast.info('当前场景没有已加载的图层，开启高程前请先加载图层')
        return
      }
      this.heightOn = !this.heightOn
      queue.forEach((layer) => {
        try {
          if (typeof layer.setDemAvailable === 'function') {
            layer.setDemAvailable(this.heightOn)
          } else if ('demAvailable' in layer) {
            layer.demAvailable = this.heightOn
          }
        } catch (error) { /* 单个图层设置失败不影响其它图层 */ }
      })
      this.activeTool = this.heightOn ? 'terrainHeight' : ''
    },

    /** v-for 里的 ref 是数组，统一在这里取引擎实例 */
    getEngine (key) {
      const ref = this.$refs['engine-' + key]
      return Array.isArray(ref) ? ref[0] : ref
    },

    /**
     * 通用插件工具开关（注册表里除查询类外的工具）：
     * 开关型（量测等）btnClick 直接在地图上生效；弹窗型先挂弹窗再发开事件。
     */
    togglePluginTool (def) {
      const engine = this.getEngine(def.key)
      if (this.activeTool === def.key) {
        // 再点一次 = 复位（弹窗型走 cancelChecked，总线发 false，监听里同步状态）
        try { if (engine) engine.btnClick() } catch (error) { /* 复位失败由总线监听兜底 */ }
        this.activeTool = ''
        return
      }
      this.closeOtherTools(def.key)
      if (!engine) {
        this.$screenToast.info(def.title + '组件未就绪，请稍后再试')
        return
      }
      if (def.pop) {
        // 顺序不能反：先挂弹窗，再让引擎发「开」事件（$once 监听挂载后才注册）
        this.activeTool = def.key
        this.$nextTick(() => {
          try {
            engine.btnClick()
          } catch (error) {
            this.activeTool = ''
            this.$screenToast.info('地图未就绪，' + def.title + '暂不可用')
            return
          }
          this.ensurePopVisible()
        })
        return
      }
      // 开关型：直接在地图上生效（需要真实地图）
      try {
        engine.btnClick()
        this.activeTool = def.key
      } catch (error) {
        try { if (engine.checked) engine.cancelChecked() } catch (ignored) { /* 引擎复位失败不必再处理 */ }
        this.activeTool = ''
        this.$screenToast.info('地图未就绪，' + def.title + '暂不可用')
      }
    },

    /** 高清出图：无按钮引擎，直接驱动总线事件开关场景出图弹窗 */
    toggleHdExport () {
      if (this.activeTool === 'hdExport') {
        // 再点一次 = 关闭
        this.$bus.$emit('showScenePlottingPop', false)
        this.activeTool = ''
        return
      }
      this.closeOtherTools('hdExport')
      this.activeTool = 'hdExport'
      this.$nextTick(() => {
        // 顺序同其它弹窗：先挂弹窗（$once 监听注册），再发「开」事件
        this.$bus.$emit('showScenePlottingPop', true)
      })
    },

    /** 插件弹窗开关总线事件（数据驱动）：弹窗自带关闭钮关闭时同步回卡片状态 */
    handlePluginPopEvent (key, payload) {
      if (!payload && this.activeTool === key) this.activeTool = ''
    },

    /**
     * 弹窗可见性兜底：有的引擎（如图层管理）不发任何开关事件，弹窗自己的
     * isShow 会一直是 false（内容 v-show 隐藏）。挂载后检查一次，
     * 没显示就直呼它的 showPop。事件驱动型的弹窗此刻已 isShow=true，跳过。
     */
    ensurePopVisible () {
      const ref = this.$refs.activePopRef
      const popVm = Array.isArray(ref) ? ref[0] : ref
      if (popVm && popVm.isShow === false && typeof popVm.showPop === 'function') {
        popVm.showPop()
      }
    },

    /** 高清出图：插件里没有对应组件，明确告知而不是静默无反应 */
    handleHdExport () {
      this.$screenToast.info('高清出图暂未接入，插件中无对应组件')
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

  /* ---------------- 插件弹窗外壳统一适配（道路 + 空间共用 .pop 外壳） ----------------
   * 插件弹窗（QueryRoadPop/QuerySpatialPop）内部都套了一层老工作台的
   * .pop.pop-left 外壳：自带深绿渐变底、青色内辉光、旧式标题栏（">> 标题" + X），
   * 浮成一张 300px 宽的小卡片，和新右面板完全不搭。
   * 这里统一改成：外壳铺满面板框、去掉自带底色和辉光、隐藏旧标题
   * （画布层已有「道路定位 / 空间查询」标题），关闭钮挪到面板右上角保留可用。
   */
  &__road-host /deep/ .road-pop .pop,
  &__spatial-host /deep/ .spatial-pop .pop {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    width: 100% !important;
    height: 100% !important;
    background: transparent !important;
    box-shadow: none !important;
    border: none !important;
  }

  // 道路弹窗的根元素自身就是 .pop.road-pop（旧青色辉光挂在根上），一并压掉；
  // 内容区已改为内部列表滚动，根上不再开滚动条（否则和外层列表出双滚动条）
  &__road-host /deep/ .road-pop {
    background: transparent !important;
    box-shadow: none !important;
    overflow: hidden !important;
  }

  // 旧标题栏压成零高（不是 display:none——关闭钮是它的子元素，
  // 隐藏整个 header 会把右上角的关闭钮一起藏掉；关闭钮自身是
  // 绝对定位、相对弹窗根定位，压缩 header 不影响它的位置）
  &__road-host /deep/ .pop-header,
  &__spatial-host /deep/ .pop-header {
    height: 0 !important;
    overflow: visible;
    background: transparent !important;
  }

  // 旧标题（logo 图 + 文字）与画布层标题重复，隐藏
  &__road-host /deep/ .pop-title,
  &__spatial-host /deep/ .pop-title {
    display: none !important;
  }

  // 关闭钮保留：贴面板右上角（点它经总线同步回卡片状态）
  &__road-host /deep/ .pop-close,
  &__spatial-host /deep/ .pop-close {
    position: absolute !important;
    top: 6px !important;
    right: 6px !important;
    z-index: 5;
  }

  &__road-host /deep/ .pop-body,
  &__spatial-host /deep/ .pop-body {
    width: 100% !important;
    padding: 6px 10px !important;
  }

  /* 结果区拉满面板剩余高度：
   * 插件原样式把 .pop-body 写死 min-height:354px、结果列表写死 height:500px，
   * 搜索结果一多只能在 500px 的小框里滚，面板下半截全空着。
   * 这里把 body 和页签内容改成纵向 flex，列表 flex:1 吃满剩余高度。 */
  &__road-host /deep/ .pop-body {
    height: 100% !important;
    min-height: 0 !important;
    display: flex !important;
    flex-direction: column;
    overflow-y: hidden !important;
  }

  &__road-host /deep/ .pop-body .ant-tabs {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  &__road-host /deep/ .ant-tabs-content {
    flex: 1;
    min-height: 0;
    height: auto !important;
    // 裁掉动画页签停在看不见位置的「交叉道路」隐藏页签，
    // 否则它从面板右缘露出来，看起来像多了一条滚动条
    overflow: hidden !important;
  }

  &__road-host /deep/ .ant-tabs-content .ant-tabs-tabpane {
    height: 100%;
  }

  &__road-host /deep/ .road-list {
    height: 100%;
    display: flex;
    flex-direction: column;
  }

  &__road-host /deep/ .road-list .search-list-body {
    flex: 1;
    min-height: 0;
    height: auto !important;
    overflow-y: auto !important;
  }

  /* ---------------- 道路定位（插件弹窗容器） ---------------- */
  &__road-title-deco {
    position: absolute;
    right: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__road-title {
    position: absolute;
    // 与「拾取查询 / 地名定位」标题同一位置：文字右缘距右边界 83px
    right: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  &__road-host {
    position: absolute;
    right: 30px;
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;

    // 插件弹窗原是老工作台浮层（自带绝对定位与旧配色），塞进右面板框：
    // 铺满容器、去掉它自己的定位/尺寸，允许滚动（路口页签内容较长）。
    // 插件样式来自 stargis-function.css（.road-pop），必须 !important 压制。
    /deep/ .road-pop {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      width: 100% !important;
      height: 100% !important;
      max-height: none !important;
      overflow-y: auto;
      overflow-x: hidden;
      background: transparent;
    }
  }

  /* ---------------- 空间查询（插件弹窗容器） ---------------- */
  &__spatial-title-deco {
    position: absolute;
    right: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__spatial-title {
    position: absolute;
    // 与其它右面板标题同一位置：文字右缘距右边界 83px
    right: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  &__spatial-host {
    position: absolute;
    right: 30px;
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;

    // 空间查询弹窗内容多（图层树 + 画笔 + 缓冲 + 结果表 + 图表），
    // 同样铺满右面板框并整体滚动；内部控件密度按实际渲染再微调。
    /deep/ .spatial-pop {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      width: 100% !important;
      height: 100% !important;
      max-height: none !important;
      overflow-y: auto;
      overflow-x: hidden;
      background: transparent;

      // 面板内嵌后收窄内边距，给 400px 宽度腾空间
      &.pop,
      .pop-body {
        padding: 8px !important;
      }
    }

    // 内层 .pop.pop-left 旧卡片（300px 宽）铺满面板框、去自带底色
    /deep/ .spatial-pop .pop {
      position: absolute;
      top: 0;
      left: 0;
      width: 100% !important;
      min-height: 100% !important;
      background: transparent !important;
      box-shadow: none !important;
      border: none !important;
    }
  }

  /* ---------------- 属性查询（插件弹窗容器） ---------------- */
  // ⚠ 类名不能与底部属性表条的 __attr-title 撞车（后者是「属性表」标题）
  &__attrpanel-title-deco {
    position: absolute;
    right: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__attrpanel-title {
    position: absolute;
    // 与其它右面板标题同一位置：文字右缘距右边界 83px
    right: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  &__attr-host {
    position: absolute;
    right: 30px;
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;

    // 属性查询弹窗（条件面板 + 结果表 + 图表）同样铺满右面板框并整体滚动；
    // 内部控件密度按实际渲染再微调。
    /deep/ .query-attribute {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      width: 100% !important;
      height: 100% !important;
      max-height: none !important;
      overflow-y: auto;
      overflow-x: hidden;
      background: transparent;

      .pop-body {
        padding: 8px !important;
      }
    }
  }

  /* 属性弹窗的旧标题栏 / 外壳与道路、空间同一套压制：
   * 内层 .pop.pop-left 旧卡片（300px 宽、自带底色）铺满面板框并去底色，
   * 浅色的 ant 表单控件（条件输入框等）压成暗色主题。 */
  &__attr-host /deep/ .query-attribute .pop {
    position: absolute;
    top: 0;
    left: 0;
    width: 100% !important;
    min-height: 100% !important;
    background: transparent !important;
    box-shadow: none !important;
    border: none !important;
  }

  &__attr-host /deep/ .query-attribute .pop-header {
    height: 0 !important;
    overflow: visible;
    background: transparent !important;
  }

  &__attr-host /deep/ .query-attribute .pop-title {
    display: none !important;
  }

  &__attr-host /deep/ .query-attribute .pop-close {
    position: absolute !important;
    top: 6px !important;
    right: 6px !important;
    z-index: 5;
  }

  &__attr-host /deep/ .query-attribute .pop-body {
    width: 100% !important;
    padding: 6px 10px !important;
  }

  &__attr-host /deep/ .ant-input,
  &__attr-host /deep/ textarea.ant-input,
  &__attr-host /deep/ .ant-select-selection {
    background-color: rgba(0, 0, 0, 0.15) !important;
    color: #ffffff !important;
    border-color: #2c6b8a !important;
  }

  &__attr-host /deep/ .ant-select-arrow,
  &__attr-host /deep/ .ant-input::placeholder {
    color: #4a7396 !important;
  }

  // 多条件组合编辑区（插件自带浅灰底），压成暗色
  &__attr-host /deep/ .query-attribute .all-condition {
    background: rgba(0, 0, 0, 0.2) !important;
    border: 1px solid #2c6b8a !important;
    color: #ffffff;
  }

  /* ---------------- 综合查询（插件弹窗容器） ---------------- */
  &__comp-title-deco {
    position: absolute;
    right: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__comp-title {
    position: absolute;
    // 与其它右面板标题同一位置：文字右缘距右边界 83px
    right: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  &__comp-host {
    position: absolute;
    right: 30px;
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;

    // 综合查询弹窗原是老工作台的宽版浮层（左查询面板 + 右结果表并排），
    // 400px 面板里放不下：整体铺满面板框、纵向堆叠（查询面板在上、
    // 结果表在下）、整体滚动。内部控件密度按实际渲染再微调。
    /deep/ .comprehensive-pop {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      width: 100% !important;
      height: 100% !important;
      overflow-y: auto !important;
      overflow-x: hidden !important;
      background: transparent !important;
      box-shadow: none !important;
      border: none !important;

      // 旧浮层的左右并排改纵向堆叠
      .pop-left {
        position: relative !important;
        width: 100% !important;
        min-width: 0 !important;
        left: auto !important;
        top: auto !important;
        background: transparent !important;
        box-shadow: none !important;
      }

      .info-table {
        position: relative !important;
        width: 100% !important;
        left: auto !important;
        margin-top: 8px;
      }
    }

    // 旧标题栏压成零高（关闭钮绝对定位不受影响）、旧标题隐藏
    /deep/ .comprehensive-pop .pop-header {
      height: 0 !important;
      overflow: visible;
      background: transparent !important;
    }

    /deep/ .comprehensive-pop .pop-title {
      display: none !important;
    }

    /deep/ .comprehensive-pop .pop-close {
      position: absolute !important;
      top: 6px !important;
      right: 6px !important;
      z-index: 5;
    }

    /deep/ .comprehensive-pop .pop-body {
      width: 100% !important;
      padding: 6px 10px !important;
    }
  }

  /* ---------------- 综合统计（插件弹窗容器） ---------------- */
  &__stat-title-deco {
    position: absolute;
    right: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__stat-title {
    position: absolute;
    // 与其它右面板标题同一位置：文字右缘距右边界 83px
    right: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  &__stat-host {
    position: absolute;
    right: 30px;
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;

    // 综合统计弹窗（图层树 + 空间范围 + 属性条件 + 统计配置 + 图表 + 结果表）
    // 与空间查询同款处理：铺满面板框、整体滚动、去旧底色。
    /deep/ .statistic-pop {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      width: 100% !important;
      height: 100% !important;
      overflow-y: auto !important;
      overflow-x: hidden !important;
      background: transparent !important;
      box-shadow: none !important;
      border: none !important;

      // 内层 .pop.pop-left 旧卡片（300px 宽、带青色辉光、左侧缩进）
      // 铺满面板框、去底色辉光，否则表单挤在窄卡里、标签折行
      .pop {
        position: relative !important;
        left: 0 !important;
        width: 100% !important;
        min-height: 100% !important;
        background: transparent !important;
        box-shadow: none !important;
        border: none !important;
      }

      // 插件自带的浅灰条件组合区压成暗色
      .all-condition {
        background: rgba(0, 0, 0, 0.2) !important;
        border: 1px solid #2c6b8a !important;
        color: #ffffff;
      }

      // 表单控件压成暗色主题
      .ant-input,
      textarea.ant-input,
      .ant-select-selection {
        background-color: rgba(0, 0, 0, 0.15) !important;
        color: #ffffff !important;
        border-color: #2c6b8a !important;
      }

      .ant-select-arrow,
      .ant-input::placeholder {
        color: #4a7396 !important;
      }

      // 表单标签不折行（原 300px 卡片里「数据类型:」等会折成两行）
      .layer-info-title,
      .query-type-title {
        white-space: nowrap;
      }
    }

    // 旧标题栏压成零高（关闭钮绝对定位不受影响）、旧标题隐藏
    /deep/ .statistic-pop .pop-header {
      height: 0 !important;
      overflow: visible;
      background: transparent !important;
    }

    /deep/ .statistic-pop .pop-title {
      display: none !important;
    }

    /deep/ .statistic-pop .pop-close {
      position: absolute !important;
      top: 6px !important;
      right: 6px !important;
      z-index: 5;
    }

    /deep/ .statistic-pop .pop-body {
      width: 100% !important;
      padding: 6px 10px !important;
    }
  }

  /* ---------------- 通用插件弹窗宿主（书签/动画导航/图层管理/高程/地下/地形透明） ---------------- */
  &__plugin-title-deco {
    position: absolute;
    right: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__plugin-title {
    position: absolute;
    // 与其它右面板标题同一位置：文字右缘距右边界 83px
    right: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  &__plugin-host {
    position: absolute;
    right: 30px;
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;

    // 各插件弹窗根类名不一（*.pop 结尾或自定义），统一按「根铺满面板 +
    // 内层 .pop 卡片铺满 + 旧标题栏零高 + 关闭钮右上角」处理
    /deep/ [class*='-pop'],
    /deep/ .placename {
      position: absolute !important;
      top: 0 !important;
      left: 0 !important;
      right: 0 !important;
      bottom: 0 !important;
      width: 100% !important;
      height: 100% !important;
      max-height: none !important;
      overflow-y: auto !important;
      overflow-x: hidden !important;
      background: transparent !important;
      box-shadow: none !important;
      border: none !important;

      .pop {
        position: relative !important;
        left: 0 !important;
        top: 0 !important;
        width: 100% !important;
        min-height: 100% !important;
        background: transparent !important;
        box-shadow: none !important;
        border: none !important;
      }

      .pop-header {
        height: 0 !important;
        overflow: visible;
        background: transparent !important;
      }

      .pop-title {
        display: none !important;
      }

      .pop-close {
        position: absolute !important;
        top: 6px !important;
        right: 6px !important;
        z-index: 5;
      }

      .pop-body {
        width: 100% !important;
        padding: 6px 10px !important;
      }

      // 插件自带的浅灰条件区压成暗色
      .all-condition {
        background: rgba(0, 0, 0, 0.2) !important;
        border: 1px solid #2c6b8a !important;
        color: #ffffff;
      }

      // 表单控件压成暗色主题
      .ant-input,
      textarea.ant-input,
      .ant-select-selection {
        background-color: rgba(0, 0, 0, 0.15) !important;
        color: #ffffff !important;
        border-color: #2c6b8a !important;
      }

      .ant-select-arrow,
      .ant-input::placeholder {
        color: #4a7396 !important;
      }

      .layer-info-title,
      .query-type-title {
        white-space: nowrap;
      }

      // 插件弹窗内文字默认亮色（老主题文字色不定，深底上会不可见）
      color: #cfe8ff;

      .ant-tree,
      .ant-tree .ant-tree-node-content-wrapper {
        color: #82c6ff;
      }

      .ant-list {
        color: #cfe8ff;
      }
    }
  }
}
</style>
