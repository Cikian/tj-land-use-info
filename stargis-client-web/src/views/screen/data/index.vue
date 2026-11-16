<template>
  <!--
    数据管理 · 大屏子页面
    ===============================================================
    版式与档案管理（views/screen/archive/index.vue）、提级论证管理
    （views/screen/escalation/index.vue）一致：**左侧二级导航 + 右侧内容区**。

      二级导航  左侧栏 (30,104) 宽 200，纵向铺到 底-40
      内容区    left:250 right:30 top:104 bottom:40

    两种内容区版式（由 isSplitPanel 决定用哪一种）：
      · 默认（批量导入 / 附件 / 回收站）：面板铺满上面的内容区，与旧版一致；
      · 经营性用地信息录入 / 配套地块数据录入：检索条件**竖排到屏幕右侧**
        （right:30 宽 200，与左侧导航左右对称），列表贴屏幕底部、默认只露 3~4 行，
        高度可拖动 —— 这两块要按设计稿坐标摆放，所以内容区交出整块画布
        （见样式里的 .is-split）。列表变矮之后，中间空出来的就是地图。

    三点约定：
      · 不再渲染「数据管理」页标题 —— 选中项本身就是当前模块，标题是重复信息；
      · 顶栏由 views/screen/index.vue 渲染，本组件铺满整个设计画布自己定位；
      · 本模块**保持地图可见**（不像档案/提级论证那样暂停 Cesium）：
        六个菜单项里「用地/配套录入」需要边看地块边填，内容区之外
        仍留给地图，所以画布整体仍是 pointer-events: none + 各区自己开 stage-hit。

    页签 key 与 views/screen/index.vue 的 DATA_TABS 严格一致，顺序与后端菜单
    sort_no 一致：
      landImport 经营性用地批量导入 / land 经营性用地信息录入 /
      facility 配套地块数据录入 / facilityImport 配套信息批量导入 /
      attachment 配套附件管理 / recycle 数据更新与移除
  -->
  <div class="data-screen" :style="panelStyle">
    <!-- ========== 左侧二级导航 ========== -->
    <nav class="data-screen__nav stage-hit" :style="navStyle" aria-label="数据管理二级导航">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="data-screen__nav-item"
        :class="{ 'is-active': tab.key === activeTab }"
        :aria-current="tab.key === activeTab ? 'page' : undefined"
        @click="activeTab = tab.key"
      >
        <i class="data-screen__nav-bar" aria-hidden="true" />
        <span class="data-screen__nav-text">{{ tab.label }}</span>
      </button>
    </nav>

    <!--
      左导航拖宽条 / 折叠把手（2026-11）：
      ★ 把手挂在屏幕上、不能挂进导航 —— 导航折叠时整体滑出屏幕，
        把手跟着滑走就再也点不回来（首页侧栏的同一个坑）。
    -->
    <screen-resize-bar
      v-show="!layout.left.collapsed"
      class="data-screen__nav-resizer stage-hit"
      orientation="vertical"
      :value="layout.left.size"
      :min="layout.left.min"
      :max="layout.left.max"
      aria-label="拖动调整左侧导航宽度"
      @input="layout.setSize('left', $event)"
    />
    <screen-side-rail
      side="left"
      :collapsed="layout.left.collapsed"
      collapse-text="收起左侧导航"
      expand-text="展开左侧导航"
      @toggle="layout.toggle('left')"
    />

    <!--
      右检索列拖宽条 / 折叠把手：只有「经营性用地信息 / 配套项目信息」这两个
      分栏页签才有右侧检索列（其它四个页签是整块内容区，没有右面板）。
    -->
    <template v-if="isSplitPanel">
      <screen-resize-bar
        v-show="!layout.right.collapsed"
        class="data-screen__search-resizer stage-hit"
        orientation="vertical"
        reverse
        :value="layout.right.size"
        :min="layout.right.min"
        :max="layout.right.max"
        aria-label="拖动调整右侧检索列宽度"
        @input="layout.setSize('right', $event)"
      />
      <screen-side-rail
        side="right"
        :collapsed="layout.right.collapsed"
        collapse-text="收起右侧检索列"
        expand-text="展开右侧检索列"
        @toggle="layout.toggle('right')"
      />
    </template>

    <!-- ========== 内容区 ========== -->
    <div class="data-screen__body" :class="{ 'is-split': isSplitPanel }">
      <!-- keep-alive：切页签时不销毁面板，检索条件、分页、展开状态都保留 -->
      <keep-alive>
        <component
          :is="currentPanel"
          ref="panel"
          v-bind="panelProps"
          @drill="handleDrill"
          @locate-record="handleLocateRecord"
          @open-recycle="activeTab = 'recycle'"
        />
      </keep-alive>
    </div>

  </div>
</template>

<script>
import {
  ScreenIcon,
  ScreenResizeBar,
  ScreenSideRail,
  panelSlideDistance,
  usePanelLayout
} from '@/components/screen'
import LandImportPanel from './modules/LandImportPanel.vue'
import LandEntryPanel from './modules/LandEntryPanel.vue'
import FacilityEntryPanel from './modules/FacilityEntryPanel.vue'
import FacilityImportPanel from './modules/FacilityImportPanel.vue'
import AttachmentPanel from './modules/AttachmentPanel.vue'
import DataRecyclePanel from './modules/DataRecyclePanel.vue'
import { querySliceFeatureBounds, querySliceFeatureGroup } from '@/api/land/landAdmin'
import { flashAt, clear as clearLocateHighlight } from './locateHighlight'
// 定位闪烁高亮：飞行到位后按精确几何脉冲闪烁几下（拿不到几何回退范围矩形）
// 切片源解析/唯一值字段/联动循环体收敛到 sliceInfo.js（图形编辑的表单、管理弹窗、
// 面板共用同一口径 —— 中台换库/换表/改字典这里改一处全模块跟随）
import {
  SLICE_TYPE_BOOTSTRAP,
  SLICE_UNIQUE_FIELDS,
  checkSliceLayerReady,
  getModuleSliceInfo,
  flyToBbox
} from './sliceInfo'

/**
 * 页签 key → 面板组件映射。
 * 顺序固定为：经营性用地批量导入 → 经营性用地信息录入 → 配套地块数据录入
 *            → 配套信息批量导入 → 配套附件管理 → 数据更新与移除
 * （★ 与后端菜单 sort_no 一致：先「宗地」再「配套」，每类内部「批量」在「逐条」之后，
 *   最后是本模块唯一的「回收站」入口。）
 */
const PANELS = {
  landImport: LandImportPanel,
  land: LandEntryPanel,
  facility: FacilityEntryPanel,
  facilityImport: FacilityImportPanel,
  attachment: AttachmentPanel,
  recycle: DataRecyclePanel
}

/**
 * 需要接收下钻条件（initial-query）的面板。
 * 只有它们声明了这个 prop：无条件 v-bind 会让没有该 prop 的面板把它透传到根元素上
 * （变成 initial-query="[object Object]"），档案模块踩过这个坑。
 */
const DRILLABLE_PANELS = ['land', 'facility', 'attachment', 'recycle']

/**
 * 路由 query 里属于「控制参数」的键，不当下钻条件用。
 * tab=页签、menu=顶栏高亮、map=是否加载真实地图、token=调试用。
 */
const CONTROL_QUERY_KEYS = ['tab', 'menu', 'map', 'token', '_t']

/**
 * 从路由 query 里取出**业务条件**（下钻用）。
 * 例：/screen/data?tab=facility&crzdbh=津西青(挂)2024-01号 → { crzdbh: '…' }
 * @param {object} [query] 路由 query，缺省为空对象（组件 created 前 $route 可能还没挂上）
 */
function readRouteQuery (query) {
  const source = query || {}
  const result = {}
  Object.keys(source).forEach((key) => {
    const value = source[key]
    if (CONTROL_QUERY_KEYS.indexOf(key) === -1 && value !== undefined && value !== null && value !== '') {
      result[key] = value
    }
  })
  return result
}

export default {
  name: 'DataScreen',
  components: { ScreenIcon, ScreenResizeBar, ScreenSideRail },
  props: {
    /**
     * 初始页签，支持通过路由 ?tab=facility 直接落到某个页签
     * （与 views/screen/index.vue 的 DATA_TABS 白名单配合使用；
     *   漏改白名单的表现是「深链进不去、静默落回默认页签」）。
     */
    defaultTab: { type: String, default: 'land' }
  },
  data () {
    return {
      /**
       * 三块面板的尺寸 / 折叠状态（左二级导航、右检索列、底部列表）。
       * ★ 与首页 / 地图管理 / 配套分析管理共用 panelLayout：
       *   左/右宽度与折叠状态在这里持有，entry 面板通过同一份单例读右列宽度
       *   与列表高度（模块级单例，父页面与子面板拿到的都是同一对象）。
       */
      layout: usePanelLayout('data'),
      activeTab: PANELS[this.defaultTab] ? this.defaultTab : 'land',
      tabs: [
        { key: 'land', label: '经营性用地信息' },
        { key: 'facility', label: '配套项目信息' },
        { key: 'landImport', label: '经营性用地批量导入' },
        { key: 'facilityImport', label: '配套信息批量导入' },
        { key: 'attachment', label: '配套附件管理' },
        { key: 'recycle', label: '数据更新与移除' }
      ],
      /** 下钻条件：透传给面板的 initial-query（首次创建时生效）；深链带条件进来时从路由取 */
      drillQuery: readRouteQuery(this.$route && this.$route.query)
    }
  },
  created () {
    // 非响应式实例属性：联动图层树的按页签重试定时器（见 ensureSliceLayer）
    this._sliceLayerTimers = {}
  },
  activated () {
    // 从其它大屏页面切回来时页签没变、watch 不触发 —— 显式补一次联动
    this.ensureSliceLayer(this.activeTab)
  },
  beforeDestroy () {
    Object.keys(this._sliceLayerTimers || {}).forEach((key) => {
      clearTimeout(this._sliceLayerTimers[key])
    })
    this._sliceLayerTimers = {}
    // 页签销毁时清掉可能还在闪的定位高亮实体（防孤儿实体挂在共享 viewer 上）
    clearLocateHighlight()
  },
  computed: {
    /**
     * 宽度 / 折叠状态经 CSS 变量下发（2026-11）：
     *   --data-nav-w       左导航宽度（entry 面板的列表左偏移也用它）
     *   --data-search-w    右检索列宽度（entry 面板的检索列宽 + 列表右偏移）
     *   --data-nav-offset  内容区左偏移：展开 = 30 + 导航宽 + 20，折叠 = 30
     * CSS 自定义属性会自然继承进 entry 子组件（scoped 也拦不住），
     * 所以 LandEntryPanel / FacilityEntryPanel 一个 prop 都不用加。
     */
    panelStyle () {
      const nav = this.layout.left
      return {
        '--data-nav-w': `${nav.size}px`,
        '--data-search-w': `${this.layout.right.size}px`,
        '--data-nav-offset': `${nav.collapsed ? 30 : nav.size + 50}px`
      }
    },
    /** 左导航折叠位移：整体滑出屏幕（导航宽 + 屏边 30），与首页侧栏同一套口径 */
    navStyle () {
      if (!this.layout.left.collapsed) return null
      return { transform: `translateX(-${panelSlideDistance(this.layout, 'left')}px)` }
    },
    /**
     * 只给「需要」的面板传下钻条件（见 DRILLABLE_PANELS 的说明）。
     * 批量导入面板没有这个 prop，传了会把它渲染成 HTML 属性。
     */
    panelProps () {
      return DRILLABLE_PANELS.indexOf(this.activeTab) > -1 ? { 'initial-query': this.drillQuery } : {}
    },
    currentPanel () {
      return PANELS[this.activeTab] || LandEntryPanel
    },

    /**
     * 是否「检索列 + 可拖高列表」版式（只有经营性用地录入 / 配套地块录入这两个面板）。
     *
     * 这两个面板的版式与其它四个不同：
     *   · 检索条件竖排到屏幕**右侧**（200px，与左侧二级导航对称）；
     *   · 列表贴屏幕底部、默认只露 3~4 行，高度可拖动调整。
     * 它们需要按**设计稿坐标**摆放这两块，所以内容区要让出整块画布
     * （见样式里的 .is-split）：否则面板只能拿到「左侧栏右边到屏幕右边 30px」
     * 这个盒子，右侧检索列的坐标就得写成相对这个盒子的负偏移，非常难读。
     */
    isSplitPanel () {
      return this.activeTab === 'land' || this.activeTab === 'facility'
    }
  },
  watch: {
    /**
     * 页签切换 → 联动图层树勾选对应切片图层（经营性用地 → 地块数据，
     * 配套项目 → 配套设施；其它页签不联动）。
     */
    activeTab: {
      handler (tab) {
        this.ensureSliceLayer(tab)
      },
      immediate: true
    },
    /**
     * 响应路由变化。
     * ★ 为什么必须 watch：`/screen/data?tab=facility&crzdbh=xxx` 这类**同路由换 query**
     *   的跳转，Vue Router 不会重建组件 —— data() 只在首次创建时读一次路由，
     *   于是「从宗地详情点『查看该宗地配套』」会表现成「地址变了但页面没动」。
     */
    '$route.query': {
      handler () {
        const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
        const query = readRouteQuery(routeQuery)
        const tab = routeQuery.tab
        if (tab && PANELS[tab] && tab !== this.activeTab) {
          this.activeTab = tab
        }
        if (Object.keys(query).length) {
          this.drillQuery = query
          this.$nextTick(() => {
            const panel = this.$refs.panel
            if (panel && typeof panel.applyDrill === 'function') {
              panel.applyDrill(query)
            }
          })
        }
      },
      deep: true
    }
  },
  methods: {
    /**
     * 进入经营性用地 / 配套模块时联动图层树：勾选对应切片图层（幂等）。
     *
     * ★ 为什么在本页隐藏挂载图层树：图层树组件（layer-tree-pop，插件全局组件）
     *   只在地图管理页挂载，本页拿不到实例 —— 隐藏挂载同款组件并接上
     *   mvt-layer-tree 指令后，程序化勾选经指令的 watch → manager.sync
     *   渲染到共享 viewer；指令的 hydrate 保证树晚到也安全。
     * ★ 重试循环的判定是「viewer 上真的出现了该资源 id 的影像层」而不是
     *   「checkedKeys 已赋值」——实测插件在树数据异步加载完成后会**重置勾选**，
     *   只盯赋值会出现「第二次进菜单才渲染」；每秒重新断言，被重置就再勾，
     *   直到渲染出来为止（自愈）。
     * ★ 赋值必须给**新数组引用**：即使 id 已勾过，引用变化才能触发指令的
     *   watch 重新 sync。
     * ★ 合并语义：本次勾选会把「当前已在地图上渲染的其它 MVT 图层」一并并入
     *   keys（sync 是全量替换语义），否则地图管理侧勾的层会被这次联动整批撤下。
     */
    ensureSliceLayer (tab) {
      const bootstrapNames = SLICE_TYPE_BOOTSTRAP[tab]
      if (!bootstrapNames) return
      // ★ 兜底初始化：watch 的 immediate 处理器在 created() **之前**执行，
      //   此时 _sliceLayerTimers 还没被 created 赋值 —— 不兜底的话首次进入
      //   的联动调用直接 TypeError，重试循环从未启动（首次进入永远不渲染）
      if (!this._sliceLayerTimers) this._sliceLayerTimers = {}
      if (this._sliceLayerTimers[tab]) {
        clearTimeout(this._sliceLayerTimers[tab])
      }
      let tries = 0
      this._sliceLayerTimers[tab] = setInterval(() => {
        tries++
        if (checkSliceLayerReady(tab, bootstrapNames) || tries >= 120) {
          clearInterval(this._sliceLayerTimers[tab])
          delete this._sliceLayerTimers[tab]
        }
      }, 1000)
    },
    /**
     * 列表定位（双击）：按记录唯一值到切片源表查范围后飞行相机。
     * ★ 数据源与图形均来自 MVT 切片（dk_geojson 已废弃）：
     *   唯一值 = 地块 crzdbh / 配套 ptxmmc（切片源表属性，后端按值匹配）。
     *   切片源信息（连接编码/表名）从壳层隐藏图层树的树数据动态解析，
     *   中台改库名/表名后这里自动跟随，零配置。
     */
    async handleLocateRecord (record) {
      if (!record) return
      const tab = this.activeTab
      const field = SLICE_UNIQUE_FIELDS[tab]
      if (!field) return
      const value = String(record[field] || '').trim()
      if (!value) {
        this.$message.warning('该记录缺少唯一值（' + field + '），无法定位')
        return
      }
      const info = getModuleSliceInfo(tab)
      if (!info) {
        this.$message.warning('切片图层尚未就绪，请稍后重试')
        return
      }
      try {
        const res = await querySliceFeatureBounds({
          source: info.source,
          table: info.table,
          field: field,
          value: value
        })
        if (!res || !res.success) {
          this.$message.warning((res && res.message) || '定位查询失败')
          return
        }
        const bounds = res.result || []
        if (!bounds.length) {
          this.$message.warning('切片图层中未找到该记录的图形')
          return
        }
        // ★ setView 定位（flyTo 在 S3DM 定制版里动画会被残留 lookAtTransform
        //   取消、实测失效）——外扩 35% 的边距在 flyToBbox 内统一处理
        flyToBbox(bounds)
        // 闪烁高亮：飞行已启动（不等待），异步取精确几何按块闪烁；
        // 拿不到几何（接口慢/失败/该记录无图形）时退回范围矩形闪烁
        querySliceFeatureGroup({
          source: info.source,
          table: info.table,
          field: field,
          value: value
        })
          .then((geoRes) => {
            const geometries = (geoRes && geoRes.success && Array.isArray(geoRes.result)) ? geoRes.result : []
            flashAt({ geometries: geometries, bounds: bounds })
          })
          .catch(() => {
            flashAt({ bounds: bounds })
          })
      } catch (e) {
        this.$message.warning('定位查询失败：' + (e.message || e))
      }
    },

    /**
     * 面板之间的下钻（例如从宗地表点「该宗地配套」跳到配套面板）。
     * 两种到达路径都要覆盖：
     *   - 目标面板是首次创建 → initialQuery 在 created 里生效；
     *   - 目标面板已被 keep-alive 缓存 → created 不会再跑，必须调用它的 applyDrill。
     */
    handleDrill (payload) {
      const params = payload || {}
      const target = params.tab && PANELS[params.tab] ? params.tab : 'facility'
      const query = Object.assign({}, params)
      delete query.tab
      this.drillQuery = query
      this.activeTab = target
      this.$nextTick(() => {
        const panel = this.$refs.panel
        if (panel && typeof panel.applyDrill === 'function') {
          panel.applyDrill(query)
        }
      })
    },

    /** 供外部（路由 / 父组件）切换页签 */
    setTab (key) {
      if (PANELS[key]) this.activeTab = key
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

/* 本组件铺满整个设计画布，内部按设计稿坐标摆放 */
.data-screen {
  position: absolute;
  inset: 0;
  /*
    画布整体保持 pointer-events: none —— 与旧版一致：
    数据管理页左侧只有一条二级导航，其余位置要留给地图，
    所以穿透交给父画布，交互由 __nav / __body 各自显式开启（README 踩坑 3）。
  */

  /* ---------------- 左侧二级导航 ---------------- */
  &__nav {
    position: absolute;
    left: 30px;
    top: 104px;
    bottom: 40px;
    // 宽度可拖（160~340），由页面根的 --data-nav-w 下发
    width: var(--data-nav-w, 200px);
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 10px;
    pointer-events: auto;
    // 折叠 = 整体向左滑出屏幕（位移在 navStyle 里，= 导航宽 + 30）
    transition: transform var(--screen-duration-slow) var(--screen-ease);
    // 与首页面板同一套玻璃质感
    // ⚠ 不加 backdrop-filter：侧栏是 104px 到屏幕底部的一大片，
    //   没有硬件加速时背景模糊会让鼠标划过/滚动都掉到 100ms 以上（见 screen-mixins.less）
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius);
    box-shadow: var(--screen-shadow), var(--screen-shadow-inset);
  }

  /* 左导航拖宽条：贴导航内侧竖边；折叠时藏掉（滑出屏幕的条没有意义） */
  &__nav-resizer {
    position: absolute;
    top: 104px;
    bottom: 40px;
    left: calc(30px + var(--data-nav-w, 200px) - 6px);
    z-index: 4;
  }

  /* 右检索列拖宽条：右列贴的是画布右缘、内容往左长，所以 reverse（往左拖变宽） */
  &__search-resizer {
    position: absolute;
    top: 104px;
    bottom: 40px;
    right: calc(30px + var(--data-search-w, 200px) - 6px);
    z-index: 4;
  }

  &__nav-item {
    position: relative;
    flex: 0 0 auto;
    height: 48px;
    padding: 0 14px 0 20px;
    font-family: inherit;
    font-size: var(--screen-font-md);
    line-height: 48px;
    color: var(--screen-text-sub);
    text-align: left;
    background: transparent;
    border: 0;
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-text);
      background: var(--screen-elevate);
    }

    &.is-active {
      color: var(--screen-text);
      background: rgba(130, 198, 255, 0.14);
      box-shadow: inset 0 0 0 1px var(--screen-border);
    }
  }

  /* 选中项左侧的高亮竖条（与首页面板标题前的竖条同一语言） */
  &__nav-bar {
    position: absolute;
    left: 7px;
    top: 50%;
    width: 3px;
    height: 20px;
    margin-top: -10px;
    border-radius: var(--screen-radius-pill);
    background: linear-gradient(180deg, var(--screen-accent) 0%, var(--screen-accent-deep) 100%);
    box-shadow: 0 0 8px var(--screen-accent-glow);
    opacity: 0;
    transition: opacity var(--screen-duration) var(--screen-ease);
  }

  &__nav-item.is-active &__nav-bar {
    opacity: 1;
  }

  &__nav-text {
    position: relative;
    z-index: 1;
    display: block;
    // 六个菜单项里「经营性用地信息录入」最长（9 字），48px 行高下需要省略保护
    .screen-ellipsis();
  }

  /* 侧栏底部的说明：正好用掉导航下方的空白 */
  &__tip {
    display: flex;
    gap: 6px;
    margin-top: auto;
    padding: 10px 8px 2px;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);
  }

  /* ---------------- 内容区 ---------------- */
  &__body {
    position: absolute;
    // 左偏移随导航宽度 / 折叠变化：展开 = 30 + 导航宽 + 20，折叠 = 30
    // （--data-nav-offset 由 panelStyle 下发，默认 250 与设计稿一致）
    left: var(--data-nav-offset, 250px);
    right: 30px;
    top: 104px;
    bottom: 40px;
    display: flex;
    flex-direction: column;
    min-height: 0;
    pointer-events: auto;
    // 导航折叠 / 拖宽时内容区跟着让位，与导航滑出同一个 320ms
    transition: left var(--screen-duration-slow) var(--screen-ease);

    > * {
      flex: 1 1 0;
      min-height: 0;
    }

    /*
     * 「经营性用地信息录入 / 配套地块数据录入」两个面板专用版式
     * （由 isSplitPanel 打开，见 script 里的说明）。
     *
     * 这两块版面自己按**设计稿坐标**摆放「右侧检索列 + 贴底列表」，
     * 所以要交出整块画布：
     *   · inset: 0 —— 面板根节点铺满画布，面板内部写的 left/right/top/bottom
     *     就等于设计稿坐标，与左侧栏的 left:30/top:104 是同一套坐标系，
     *     「与左侧边栏对称」这件事在代码里也是字面对称的；
     *   · pointer-events: none —— 内容区一放大到整屏就会盖住地图，
     *     ★ 绝不能在这里开 auto（那正是之前「看不见的面板吃掉地图事件」的成因）。
     *     交互由面板内部那两块区域各自显式开启。
     */
    &.is-split {
      inset: 0;
      display: block;
      pointer-events: none;
    }

    /*
     * 联动图层树的隐藏挂载点（2026-11-13）：
     * layer-tree-pop 是插件的全局组件，树数据与勾选状态都在它内部；
     * 隐藏挂载后 mvt-layer-tree 指令照常连接，程序化勾选经
     * watch → manager.sync 渲染到共享 viewer。
     * ★ transform 让插件自己的 fixed 弹层也以本节点为包含块、被 overflow 裁掉；
     *   visibility 继承隐藏 + pointer-events: none 双保险（README 踩坑口径）。
     */
    &__hidden-tree {
      position: fixed;
      left: -10000px;
      top: 0;
      width: 800px;
      height: 600px;
      overflow: hidden;
      visibility: hidden;
      pointer-events: none;
      transform: translateZ(0);
    }
  }
}
</style>
