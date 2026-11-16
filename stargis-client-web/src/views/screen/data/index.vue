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

    检索条件生命周期（P2-01，与其它域同一套契约）：
      · 面板公开 `resetFilters()`：条件恢复初始值（含 bizId/bizKey 等隐藏条件）、
        页码回 1、清空选中与展开项，并保证界面与请求参数一致；
      · 本页在**页签切换 / 页面 activated()** 时调用当前面板的 `resetFilters()`，
        条件是「路由 query 里没有下钻参数」；
      · 下钻（applyDrill / 路由带条件）是唯一允许带条件进面板的路径，消费完
        立刻把路由上的条件摘掉，避免离开再回来时旧条件被重新注入。
      · 用户点面板里的「重置」照旧，不走本页的逻辑。

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
      <!--
        keep-alive：切页签时**保留面板实例**（省掉重复拉字典、重铺图层、重建树），
        但**不再保留检索条件** —— 缓存下来的旧条件会变成隐性过滤，用户跨模块
        回来看到的不是全量（测试问题 P2-01）。条件由本页在页签切换 / activated
        时调面板的 resetFilters() 清空，见 script 里的 resetActivePanelFilters。
      -->
      <keep-alive>
        <component
          :is="currentPanel"
          ref="panel"
          v-bind="panelProps"
          @drill="handleDrill"
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
      drillQuery: readRouteQuery(this.$route && this.$route.query),
      /**
       * 本页会话内「已经出现过」的页签（key → true，只在逻辑里用，不参与渲染）。
       * ★ 用途：第一次切到某个页签时面板实例是刚创建的、条件本来就是空的，
       *   再调一次 resetFilters() 只是白打一次全量请求；只有复用 keep-alive
       *   缓存的实例才真的需要清。
       */
      seenTabs: {},
      /**
       * 是否已经经历过首次 activated。
       * ★ keep-alive 下 activated 在首次挂载时也会触发，那一刻面板刚按
       *   initial-query 取过数，再清一次又是一次重复请求 —— 首轮跳过。
       */
      hasActivated: false,
      /** 本次页签切换是否由下钻驱动：是的话条件刚注入，不能马上被清掉 */
      drillTabPending: false
    }
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
     * 响应路由变化。
     * ★ 为什么必须 watch：`/screen/data?tab=facility&crzdbh=xxx` 这类**同路由换 query**
     *   的跳转，Vue Router 不会重建组件 —— data() 只在首次创建时读一次路由，
     *   于是「从宗地详情点『查看该宗地配套』」会表现成「地址变了但页面没动」。
     * ★ 条件注入与「摘掉路由参数」都收口在 consumeRouteDrill 里，见该方法注释。
     */
    '$route.query': {
      handler () {
        this.consumeRouteDrill()
      },
      deep: true
    },

    /**
     * 普通页签切换（左二级导航）。
     * ★ 缓存下来的面板会带着上一次的检索条件复活，跨模块回来时表现为
     *   「明明没筛却只看到一部分数据」（P2-01），所以这里清空当前面板。
     * ★ 例外两条：
     *   - 本次切换由下钻驱动（drillTabPending）→ 条件刚注入，不能马上清；
     *   - 路由上还有没消费的下钻条件 → 走注入，不清空。
     */
    activeTab () {
      if (this.drillTabPending) {
        this.drillTabPending = false
        return
      }
      this.$nextTick(() => {
        if (this.hasRouteDrill()) {
          this.consumeRouteDrill()
          return
        }
        const tab = this.activeTab
        const firstVisit = !this.seenTabs[tab]
        this.seenTabs[tab] = true
        // 首次创建的实例本来就是干净的，再清一次只会多发一次全量请求
        if (firstVisit) return
        this.resetActivePanelFilters()
      })
    }
  },
  created () {
    /*
     * 首次挂载：当前页签的面板马上会被创建，它的 data() 会读 initial-query
     * （下钻 / 深链条件），所以这里只登记「已出现」并**把路由上的条件摘掉**。
     * ★ 摘掉这一步是 P2-01 的核心缺陷修复：原来条件一直留在地址栏里，
     *   用户离开本模块再回来（或从另一个对象下钻）时旧条件会被重新注入，
     *   表现成「检索条件自己被记住了」，必须手动重置才能看全量。
     */
    this.seenTabs[this.activeTab] = true
    this.clearRouteDrillParams()
  },
  activated () {
    /*
     * 页面重新可见（keep-alive 下从别的模块 / 别的页签回来）。
     * ★ 首次挂载也会触发 activated，那一刻当前面板刚按 initial-query 取过数，
     *   再清一次就是重复请求，所以首轮跳过（hasActivated）。
     */
    if (!this.hasActivated) {
      this.hasActivated = true
      return
    }
    if (this.consumeRouteDrill()) return
    this.$nextTick(() => this.resetActivePanelFilters())
  },
  methods: {
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
      const switching = target !== this.activeTab
      // 目标面板是本次才创建的 → 它的 created 已经按 initial-query 取过数
      const freshInstance = switching && !this.seenTabs[target]
      this.seenTabs[target] = true
      this.drillQuery = query
      if (switching) {
        // 下钻驱动的页签切换：让 activeTab watcher 放过这次切换
        this.drillTabPending = true
        this.activeTab = target
      }
      this.$nextTick(() => {
        if (freshInstance) return
        const panel = this.$refs.panel
        if (panel && typeof panel.applyDrill === 'function') {
          panel.applyDrill(query)
        }
      })
    },

    /**
     * 消费路由上的下钻参数：注入面板 → 记住 drillQuery → **把路由参数摘掉**。
     * @returns {boolean} 是否有条件 / 页签变化被消费（调用方据此决定要不要清空）
     */
    consumeRouteDrill () {
      const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
      const query = readRouteQuery(routeQuery)
      const tab = routeQuery.tab
      const wantTab = tab && PANELS[tab] ? tab : ''
      const switching = !!wantTab && wantTab !== this.activeTab
      const targetTab = switching ? wantTab : this.activeTab
      const freshInstance = switching && !this.seenTabs[targetTab]
      let consumed = switching
      if (switching) {
        this.seenTabs[targetTab] = true
        this.drillTabPending = true
        this.activeTab = targetTab
      }
      if (Object.keys(query).length) {
        consumed = true
        this.drillQuery = query
        this.$nextTick(() => {
          if (freshInstance) return
          const panel = this.$refs.panel
          if (panel && typeof panel.applyDrill === 'function') {
            panel.applyDrill(query)
          }
        })
      }
      if (consumed) this.clearRouteDrillParams()
      return consumed
    },

    /** 路由上是否还有**业务**下钻参数（tab / menu / map / token 这类控制参数不算） */
    hasRouteDrill () {
      return Object.keys(readRouteQuery(this.$route && this.$route.query)).length > 0
    },

    /**
     * 把路由上的下钻参数摘掉。
     * ★ 用 replace 而不是 push：清参数不该在浏览器历史里留一条记录，
     *   否则用户按「后退」正好回到带旧条件的那一条，缺陷原样复活。
     * ★ 控制参数（tab / menu / map / token）原样保留：它们不是检索条件，
     *   其中 `?map=0` 是「不加载真实地图」的预览开关（见 views/screen/index.vue），
     *   一并清掉会让一次下钻之后的刷新变成强行加载地图，反而干扰调试与验收。
     */
    clearRouteDrillParams () {
      const route = this.$route
      if (!route || !route.query || !this.$router) return
      if (!Object.keys(readRouteQuery(route.query)).length) return
      const next = {}
      Object.keys(route.query).forEach((key) => {
        if (CONTROL_QUERY_KEYS.indexOf(key) > -1) next[key] = route.query[key]
      })
      const done = this.$router.replace({ query: next })
      // 重复导航会被 vue-router 拒绝；这里只关心「参数摘掉了」，失败无需打扰用户
      if (done && typeof done.catch === 'function') done.catch(() => {})
    },

    /**
     * 清空当前激活面板的检索条件 —— P2-01 的统一契约（其它域同一套实现）。
     *
     * 面板侧契约（每个可检索面板必须提供）：
     *   `resetFilters()` —— 检索条件恢复初始值（**含 bizId / bizKey 等隐藏条件**）、
     *   页码回 1、清空选中行与展开项，并保证「界面」与「实际请求参数」一致
     *   （不能出现输入框空了、query 里还留着旧值）。
     *
     * ★ 为什么父级也要把 drillQuery 清空：被 keep-alive 缓存的面板改不动
     *   initial-query；而被销毁重建（切走页签再回来 / 重进模块）的面板会重新读
     *   initial-query —— 不清空的话，上一次下钻的条件会「一进面板就回来」。
     */
    resetActivePanelFilters () {
      this.drillQuery = {}
      const panel = this.$refs.panel
      if (!panel) return
      if (typeof panel.resetFilters === 'function') {
        panel.resetFilters()
        return
      }
      /*
       * 兜底：面板还没补上 resetFilters() 时，走它已有的 applyDrill 通道注入
       * 「空条件」——效果等价（条件清空 + 页码回 1 + 取消选中 + 重新取数），
       * 面板逐个补齐后自动走上面的正路（判据只看方法在不在，不用改这里）。
       */
      if (typeof panel.applyDrill === 'function') panel.applyDrill({})
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
  }
}
</style>
