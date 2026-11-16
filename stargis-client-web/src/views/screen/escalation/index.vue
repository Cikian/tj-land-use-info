<template>
  <!--
    提级论证管理 · 大屏子页面
    ===============================================================
    版式与档案管理（views/screen/archive/index.vue）完全一致：**左侧二级导航 + 右侧内容区**。

      二级导航  左侧栏 (30,104) 宽 200，纵向铺到 底-40
      内容区    left:250 right:30 top:104 bottom:40

    三点约定：
      · 不再渲染「提级论证管理」页标题 —— 选中项本身就是当前模块，标题是重复信息；
      · 顶栏由 views/screen/index.vue 渲染，本组件铺满整个设计画布自己定位；
      · 本模块是**记录型台账**（流程在线下办理），侧栏底部把这句话常驻说清楚，
        避免用户以为登记意见会驱动流转。

    页签 key 与 views/screen/index.vue 的 REVIEW_TABS 严格一致：
      entry 项目录入 / query 查询统计 / ledger 资料及台账管理 / audit 提级论证审批
  -->
  <div class="escalation-screen">
    <!-- ========== 左侧二级导航 ========== -->
    <nav class="escalation-screen__nav stage-hit" aria-label="提级论证管理二级导航">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="escalation-screen__nav-item"
        :class="{ 'is-active': tab.key === activeTab }"
        :aria-current="tab.key === activeTab ? 'page' : undefined"
        @click="activeTab = tab.key"
      >
        <i class="escalation-screen__nav-bar" aria-hidden="true" />
        <span class="escalation-screen__nav-text">{{ tab.label }}</span>
      </button>
    </nav>

    <!-- ========== 内容区 ========== -->
    <div class="escalation-screen__body">
      <!-- keep-alive：切页签时不销毁模块（只缓存组件实例，检索条件按 P2-01 统一清空，见 script） -->
      <keep-alive>
        <component
          :is="currentPanel"
          ref="panel"
          v-bind="panelProps"
          @drill="handleDrill"
          @open-query="activeTab = 'query'"
          @open-ledger="activeTab = 'ledger'"
          @open-audit="activeTab = 'audit'"
        />
      </keep-alive>
    </div>
  </div>
</template>

<script>
import { ScreenIcon } from '@/components/screen'
import EscalationEntry from './modules/EscalationEntry.vue'
import EscalationQuery from './modules/EscalationQuery.vue'
import EscalationLedger from './modules/EscalationLedger.vue'
import EscalationAudit from './modules/EscalationAudit.vue'

/**
 * 页签 key → 面板组件映射。
 * 顺序固定为：项目录入 → 查询统计 → 资料及台账管理 → 提级论证审批
 * （与方案 2.3.3 的四项对号，也是 views/screen/index.vue 里 REVIEW_TABS 的顺序）。
 */
const PANELS = {
  entry: EscalationEntry,
  query: EscalationQuery,
  ledger: EscalationLedger,
  audit: EscalationAudit
}

/**
 * 需要接收下钻条件（initial-query）的面板：只有它声明了这个 prop。
 * 本域唯一的深链落点是「资料及台账管理」——「查询统计」的图表点「查看台账」走的就是它。
 */
const DRILLABLE_PANELS = ['ledger']

/**
 * 路由 query 里属于「控制参数」的键，不当下钻条件用
 * （与 archive/index.vue 同一份口径：tab/menu/map/token 是页面外壳的控制参数）。
 */
const CONTROL_QUERY_KEYS = ['tab', 'menu', 'map', 'token', '_t']

/**
 * 从路由 query 里取出**业务条件**（下钻用）。
 * 例：/screen/review?tab=ledger&district=滨海新区&status=已通过 → { district, status }
 * @param {object} [query] 路由 query，缺省为空对象
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
  name: 'EscalationScreen',
  components: { ScreenIcon },
  props: {
    /**
     * 初始页签，支持路由深链 ?tab=audit 直接落到某个页签
     * （与 views/screen/index.vue 的 REVIEW_TABS 校验配合使用）。
     */
    defaultTab: { type: String, default: 'entry' }
  },
  data () {
    return {
      activeTab: PANELS[this.defaultTab] ? this.defaultTab : 'entry',
      tabs: [
        { key: 'entry', label: '项目录入' },
        { key: 'query', label: '查询统计' },
        { key: 'ledger', label: '资料及台账管理' },
        { key: 'audit', label: '提级论证审批' }
      ],
      /** 下钻条件：透传给台账面板的 initial-query（首次创建时生效） */
      drillQuery: readRouteQuery(this.$route && this.$route.query),
      /**
       * 本次页签变化是否由「业务下钻」引起。
       * 为真时 activeTab watcher 不清条件 —— 条件由 handleDrill 里的 applyDrill 注入，
       * 清掉就把刚下钻的条件抹了（档案模块踩过同样的坑，见 `_skipResetOnce`）。
       */
      pendingDrill: false,
      /** 首次激活不清（面板刚创建，条件本就是空），避免白跑一次请求 */
      firstActivation: true
    }
  },
  computed: {
    /**
     * 只给「需要」的面板传下钻条件。
     * 无条件 v-bind 会让没有声明该 prop 的面板把它透传到根元素上
     * （变成 initial-query="[object Object]"），档案模块踩过这个坑。
     */
    panelProps () {
      return DRILLABLE_PANELS.indexOf(this.activeTab) > -1 ? { 'initial-query': this.drillQuery } : {}
    },
    currentPanel () {
      return PANELS[this.activeTab] || EscalationEntry
    }
  },
  /**
   * P2-01：从别的模块切回提级论证时清空检索（与数据管理 / 档案 / 查询统计同一套契约）。
   *
   * ★ 「有下钻参数就不清」：台账点「查看档案」、查询统计点图表这类**深链**
   *   （`/screen/review?tab=ledger&district=xxx`）带着条件进来，
   *   此时清空会把刚注入的条件抹掉，表现是「地址里明明有区县，列表却是全量」。
   */
  activated () {
    if (this.firstActivation) {
      this.firstActivation = false
      return
    }
    if (this.pendingDrill) {
      this.pendingDrill = false
      return
    }
    const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
    if (Object.keys(readRouteQuery(routeQuery)).length) return
    this.$nextTick(() => this.resetActivePanelFilters())
  },
  /**
   * 首屏消费深链。
   * ★ 与档案模块同一个坑：`data()` 只把条件塞进 drillQuery（台账面板 created 用得到），
   *   而「按条件 applyDrill + 摘掉地址栏参数」原来只挂在路由 watcher 上。
   *   直接打开深链时 watcher 不触发，参数就永远留在地址里，刷新一次注入一次。
   * ★ 本域**确实有**可下钻入口（查询统计的图表 → 资料及台账管理），
   *   所以这里做真实消费，而不是「只做页签切换清条件」。
   */
  mounted () {
    const query = readRouteQuery(this.$route && this.$route.query)
    if (!Object.keys(query).length) return
    this.$nextTick(() => {
      /*
       * ★ 首屏不重复请求：路由带 tab=ledger 时页签壳已把 activeTab 设成 ledger，
       *   台账面板的 created 也已经用 initialQuery 取过一次数。
       *   这里再 applyDrill 一次就是同一套条件连查两遍（明细接口慢时肉眼可见地闪一下），
       *   所以「已经在台账页签」时只把地址栏参数摘掉。
       */
      if (this.activeTab === 'ledger') {
        this.clearDrillQuery()
        return
      }
      this.consumeRouteDrill(query)
    })
  },
  watch: {
    /**
     * 页签切换同样按「普通进入」处理：旧条件下再切回来会变成隐性过滤
     * （清单 P2-01 的测试现象就是「检索后跳到别的模块再返回，旧条件仍生效」）。
     * ★ 路由上还挂着未消费的业务参数时不清 —— 那是下钻，交给 consumeRouteDrill。
     */
    activeTab () {
      if (this.pendingDrill) {
        this.pendingDrill = false
        return
      }
      const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
      if (Object.keys(readRouteQuery(routeQuery)).length) return
      this.$nextTick(() => this.resetActivePanelFilters())
    },
    /**
     * 路由 query 变化（同路由换条件）。
     * ★ 为什么必须 watch：`/screen/review?tab=ledger&district=xxx` 这类**同路由换 query**
     *   的跳转，vue-router 不会重建组件 —— data() 只在首次创建时读一次路由，
     *   于是「地址变了但页面没动」。
     */
    '$route.query': {
      handler () {
        const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
        const tab = routeQuery.tab
        if (tab && PANELS[tab] && tab !== this.activeTab) {
          this.activeTab = tab
        }
        const query = readRouteQuery(routeQuery)
        if (Object.keys(query).length) {
          this.$nextTick(() => this.consumeRouteDrill(query))
        }
      },
      deep: true
    }
  },
  methods: {
    /** 调当前面板公开的 resetFilters()：清可见与隐藏条件、页码回 1、清选中并重查 */
    resetActivePanelFilters () {
      const panel = this.$refs.panel
      if (!panel) return
      if (typeof panel.resetFilters === 'function') {
        panel.resetFilters()
        return
      }
      // 兜底（与 archive/index.vue 同一套）：有 applyDrill 就用空条件下钻一次 = 清空后查全量
      if (typeof panel.applyDrill === 'function') {
        panel.applyDrill({})
      }
    },

    /**
     * 从「查询统计」的图表下钻到「资料及台账管理」。
     * 两种到达路径都要覆盖（照档案模块的 handleDrill + applyDrill 双路径写法）：
     *   - 台账面板首次创建 → initialQuery 在 data() 里生效；
     *   - 台账面板已被 keep-alive 缓存 → data() 不会再跑，必须调用它的 applyDrill。
     */
    handleDrill (params) {
      this.applyDrillToLedger(params, false)
    },

    /** 路由深链下钻：与页内下钻同一条路径，额外把地址栏参数摘掉 */
    consumeRouteDrill (query) {
      this.applyDrillToLedger(query, true)
    },

    /**
     * 把下钻条件送到台账面板。
     * @param {object} params 下钻条件
     * @param {boolean} fromRoute 是否来自路由深链（来自路由要顺手 clearDrillQuery）
     */
    applyDrillToLedger (params, fromRoute) {
      this.drillQuery = Object.assign({}, params || {})
      /*
       * ★ 门闩只在**真的切页签**时上：activeTab 不变时 watcher 根本不触发，
       *   留着门闩会让下一次普通切换页签静默跳过清条件（条件被带到下个页签）。
       */
      const switching = this.activeTab !== 'ledger'
      this.pendingDrill = switching
      this.activeTab = 'ledger'
      this.$nextTick(() => {
        const panel = this.$refs.panel
        if (panel && typeof panel.applyDrill === 'function') {
          panel.applyDrill(this.drillQuery)
        }
        // 条件已经交给面板持有，索引里的副本必须清掉：
        // 否则用户离开再回来时 panelProps 会把它重新当成 initial-query 注入一次。
        this.drillQuery = {}
        if (fromRoute) this.clearDrillQuery()
      })
    },

    /**
     * 摘掉路由上的**业务**下钻参数（保留 tab / menu 等控制参数）。
     * ★ 不清成空对象的原因同 archive/index.vue：控制参数丢了会让刷新后掉回默认页签。
     */
    clearDrillQuery () {
      const current = Object.assign({}, (this.$route && this.$route.query) || {})
      let changed = false
      Object.keys(current).forEach((key) => {
        if (CONTROL_QUERY_KEYS.indexOf(key) === -1) {
          delete current[key]
          changed = true
        }
      })
      if (!changed) return
      this.drillQuery = {}
      this.$router.replace({ path: this.$route.path, query: current }).catch(() => {
        // 同一路由重复 replace 会被 vue-router 判为冗余导航，忽略即可
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
.escalation-screen {
  position: absolute;
  inset: 0;
  /*
    画布整体 pointer-events: none（否则会吃掉地图的鼠标事件），
    所以整页模块必须显式开启，否则内部按钮点不动（README 踩坑 3）。
    父组件同时挂了 .stage-hit，这里再声明一次是为了本组件单独使用时也成立。
  */
  pointer-events: auto;

  /* ---------------- 左侧二级导航 ---------------- */
  &__nav {
    position: absolute;
    left: 30px;
    top: 104px;
    bottom: 40px;
    width: 200px;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 10px;
    // 与首页面板同一套玻璃质感
    // ⚠ 不加 backdrop-filter：侧栏是 104px 到屏幕底部的一大片，
    //   没有硬件加速时背景模糊会让鼠标划过/滚动都掉到 100ms 以上（见 screen-mixins.less）
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius);
    box-shadow: var(--screen-shadow), var(--screen-shadow-inset);
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
      background: var(--screen-elevate);
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
    // 30(左边距) + 200(侧栏) + 20(间距)
    left: 250px;
    right: 30px;
    top: 104px;
    bottom: 40px;
    display: flex;
    flex-direction: column;
    min-height: 0;

    > * {
      flex: 1 1 0;
      min-height: 0;
    }
  }
}
</style>
