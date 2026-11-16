<template>
  <!--
    查询统计 · 大屏子页面（方案 2.3.1（五））
    ===============================================================
    版式与数据管理/档案管理一致：**左侧二级导航 + 右侧内容区**。
      二级导航  左侧栏 (30,104) 宽 200，纵向铺到 底-40
      内容区    left:250 right:30 top:104 bottom:40

    四个页签对应高保真 `查询统计.html` 的三个动态面板 + 分级预警（方案 2.3.1（五）D-603）：
      经营性用地查询  condition + 分析结果 + 结果表（点编号看该宗地配套）
      用地配套项目查询 同上，条件换成配套的 14 个环节字段
      查询导出        字段/运算符/值/关系符自由组合 → 查询 或 导出 Excel
      预警信息查询     筛选条 + 四色统计卡 + 预警结果表（重新计算 / 导出）

    ★ 为什么本页仍保持地图可见（不加 stage-hit、容器 pointer-events: none）：
      查询结果里常要「照着地块核编号」，把地图挡死就得多切一次页面。
      与数据管理同一处理：交互由 __nav / __body 两块显式开启。

    ★ 页签 key 与 views/screen/index.vue 的 QUERY_TABS 严格一致
      （漏改白名单的表现是「深链进不去、静默落回默认页签」）。
  -->
  <div class="query-screen">
    <!-- ========== 左侧二级导航 ========== -->
    <nav class="query-screen__nav stage-hit" aria-label="查询统计二级导航">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="query-screen__nav-item"
        :class="{ 'is-active': tab.key === activeTab }"
        :aria-current="tab.key === activeTab ? 'page' : undefined"
        @click="activeTab = tab.key"
      >
        <i class="query-screen__nav-bar" aria-hidden="true" />
        <span class="query-screen__nav-text">{{ tab.label }}</span>
      </button>
    </nav>

    <!-- ========== 内容区 ========== -->
    <div class="query-screen__body">
      <!--
        keep-alive：切页签时不销毁面板（列设置、分页、滚动位置都保留）。
        ★ 但**检索条件不保留**：切回来时由 enterPanel() 调面板的 resetFilters()
          清条件并重查（清单 P2-01）。「缓存面板」与「保留旧条件」是两件事，
          以前用 keep-alive 顺带把旧条件也留下了，才出现「返回后看着像全量、
          实际还在过滤」。
      -->
      <keep-alive>
        <stat-query-view v-if="activeTab === 'land'" ref="landPanel" key="land" data-type="land" />
        <stat-query-view v-else-if="activeTab === 'facility'" ref="facilityPanel" key="facility" data-type="facility" />
        <warning-query-view v-else-if="activeTab === 'warning'" ref="warningPanel" key="warning" />
        <advanced-query-view v-else ref="advancedPanel" key="advanced" />
      </keep-alive>
    </div>
  </div>
</template>

<script>
import { ScreenIcon } from '@/components/screen'
import { QUERY_TABS } from './constants'
import StatQueryView from './modules/StatQueryView.vue'
import AdvancedQueryView from './modules/AdvancedQueryView.vue'
import WarningQueryView from './modules/WarningQueryView.vue'

/** 页签 key → 是否合法（供深链校验，与 QUERY_TABS 同源） */
const TAB_KEYS = QUERY_TABS.map((tab) => tab.key)

/** 页签 key → 面板 ref 名（四个面板互斥渲染，同一时刻只有一个 ref 可用） */
const PANEL_REFS = {
  land: 'landPanel',
  facility: 'facilityPanel',
  advanced: 'advancedPanel',
  warning: 'warningPanel'
}

/**
 * 路由 query 里的「控制参数」：不当下钻业务条件用。
 * tab = 页签、menu = 顶栏高亮、map = 是否加载真实地图、token = 调试用。
 * 与 data/index.vue 的 CONTROL_QUERY_KEYS 同一口径（同一套深链约定）。
 */
const CONTROL_QUERY_KEYS = ['tab', 'menu', 'map', 'token', '_t']

/**
 * 从路由 query 里取出**业务条件**（下钻用）。
 * 例：/screen/query?tab=land&dkmc=浯水道 → { dkmc: '浯水道' }
 * @param {object} [query] 路由 query
 */
function readDrillQuery (query) {
  const source = query || {}
  const result = {}
  Object.keys(source).forEach((key) => {
    const value = source[key]
    if (CONTROL_QUERY_KEYS.indexOf(key) === -1 &&
      value !== undefined && value !== null && value !== '') {
      result[key] = value
    }
  })
  return result
}

export default {
  name: 'QueryScreen',
  components: { AdvancedQueryView, ScreenIcon, StatQueryView, WarningQueryView },
  props: {
    /** 初始页签，支持 /screen/query?tab=facility 深链 */
    defaultTab: { type: String, default: 'land' }
  },
  data () {
    return {
      activeTab: TAB_KEYS.indexOf(this.defaultTab) > -1 ? this.defaultTab : 'land',
      tabs: QUERY_TABS,
      /**
       * 已经进入过的页签。
       * ★ 首次进入时面板刚创建（条件本来就是空的），不必再 reset 一次 ——
       *   那会白打一次「刚查完又查一次」的请求。只有**再次**进入（面板已被
       *   keep-alive 缓存、条件还留在里面）才需要清。
       */
      visitedTabs: {}
    }
  },
  watch: {
    /**
     * 同路由换 query 不会重建组件（Vue Router 复用实例），
     * 必须自己跟着 tab 走，否则「地址变了页面没动」。
     */
    '$route.query': {
      handler (query) {
        const tab = query && query.tab
        if (tab && TAB_KEYS.indexOf(tab) > -1 && tab !== this.activeTab) {
          this.activeTab = tab
        }
      },
      deep: true
    },
    /** 页签切换：处理下钻参数，或按 P2-01 清掉上一个页签留下的检索条件 */
    activeTab (key) {
      this.enterPanel(key)
    }
  },
  /*
   * 页面被再次激活时同样按 P2-01 处理一遍。
   * ★ 当前 query/index.vue 由 views/screen/index.vue 用 v-if 挂载（切模块会销毁重建），
   *   所以这个钩子现在多半不会触发；保留它是为了「父级哪天改成缓存整页」时
   *   行为仍然正确 —— 清条件这件事不该取决于父级用 v-if 还是 keep-alive。
   */
  activated () {
    this.enterPanel(this.activeTab)
  },
  created () {
    // 进入去重标记（{ tab, at }）：非响应式，只在 enterPanel 里用
    this.entryGuard = null
  },
  mounted () {
    // 首次挂载也要把「深链带条件进来」这条路走一遍（此时面板刚创建，ref 已就绪）
    this.$nextTick(() => this.enterPanel(this.activeTab))
  },
  methods: {
    /** 供外部（路由 / 父组件）切页签 */
    setTab (key) {
      if (TAB_KEYS.indexOf(key) > -1) {
        this.activeTab = key
      }
    },

    /** 当前页签对应的面板实例（已被缓存的页签同样能拿到 ref） */
    currentPanel (key) {
      const ref = PANEL_REFS[key || this.activeTab]
      return ref ? this.$refs[ref] : null
    },

    /**
     * 进入某个页签时的统一处理（清单 P2-01 第 1~3 条）。
     *
     * 三条分支：
     *   ① 路由带业务条件（明确下钻）→ 交给面板 applyDrill 注入，随后把
     *      地址栏里的下钻参数清掉，避免刷新 / 再切页签时旧条件又被注一次；
     *   ② 首次进入该页签 → 什么都不做（面板刚创建，条件本来就是空的）；
     *   ③ 再次进入（面板从 keep-alive 缓存里回来）→ 调 resetFilters()：
     *      清条件、页码回 1、清选中行，并按空条件重查。
     */
    enterPanel (key) {
      const tab = key || this.activeTab
      /*
       * 同一帧去重：mounted 与 activated 可能同时触发同一次「进入页签」，
       * 不去重就会对刚建好的面板连调两次（多打一次一模一样的查询）。
       * 只在极短窗口内合并，真正的再次进入（切走再切回）不会落在窗口里。
       */
      const now = Date.now()
      if (this.entryGuard && this.entryGuard.tab === tab && now - this.entryGuard.at < 300) {
        return
      }
      this.entryGuard = { tab, at: now }
      this.$nextTick(() => {
        // 这一帧里页签又被切走了：这次进入已经作废，别再动面板
        if (tab !== this.activeTab) {
          return
        }
        const panel = this.currentPanel(tab)
        if (!panel) {
          return
        }
        const drill = readDrillQuery(this.$route && this.$route.query)
        if (Object.keys(drill).length) {
          if (typeof panel.applyDrill === 'function') {
            panel.applyDrill(drill)
          } else if (typeof panel.resetFilters === 'function') {
            // 面板不支持下钻（例如预警页签）：至少别把旧条件留着
            panel.resetFilters()
          }
          this.clearDrillQuery()
          this.$set(this.visitedTabs, tab, true)
          return
        }
        if (!this.visitedTabs[tab]) {
          this.$set(this.visitedTabs, tab, true)
          return
        }
        if (typeof panel.resetFilters === 'function') {
          panel.resetFilters()
        }
      })
    },

    /**
     * 清掉地址栏里的下钻参数。
     *
     * ★ 只删业务条件、保留控制参数（tab / menu / map / token）：
     *   把整条 query 置空会把 `?map=0`（无地图预览）与 `?tab=xxx` 一起抹掉，
     *   地址栏与页面状态就不再一一对应；而「避免旧条件再次注入」只需要删掉业务条件。
     */
    clearDrillQuery () {
      const current = (this.$route && this.$route.query) || {}
      const rest = {}
      Object.keys(current).forEach((key) => {
        if (CONTROL_QUERY_KEYS.indexOf(key) > -1) {
          rest[key] = current[key]
        }
      })
      if (Object.keys(current).length === Object.keys(rest).length) {
        return
      }
      if (!this.$router || typeof this.$router.replace !== 'function') {
        return
      }
      // vue-router 3.1+ 的 replace 返回 promise，导航被中断时会 reject，静默吃掉
      const navigation = this.$router.replace({ query: rest })
      if (navigation && typeof navigation.catch === 'function') {
        navigation.catch(() => {})
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

/* 本组件铺满整个设计画布，内部按设计稿坐标摆放 */
.query-screen {
  position: absolute;
  inset: 0;
  /* 画布整体 pointer-events: none，交互由 __nav / __body 显式开启（README 踩坑 3） */

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
    pointer-events: auto;
    // ⚠ 不加 backdrop-filter：整列一大片，无硬件加速时会让滚动掉帧（见 screen-mixins.less）
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
      background: rgba(130, 198, 255, 0.14);
      box-shadow: inset 0 0 0 1px var(--screen-border);
    }
  }

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

  &__tip {
    display: flex;
    gap: 6px;
    margin-top: auto;
    padding: 10px 8px 2px;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);
  }

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
    pointer-events: auto;

    > * {
      flex: 1 1 0;
      min-height: 0;
    }
  }
}
</style>
