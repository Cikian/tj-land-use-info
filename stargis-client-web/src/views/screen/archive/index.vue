<template>
  <!--
    档案管理 · 大屏子页面
    ===============================================================
    版式：**左侧二级导航 + 右侧内容**（不再是横排页签）。

      二级导航  左侧栏 (30,104) 宽 200，纵向铺到 底-40
      内容区    left:250 right:30 top:104 bottom:40

    两点约定：
      · 不再渲染「档案管理」页标题 —— 选中项本身就是当前模块，标题是重复信息；
        省下来的 95..141 这条带直接让侧栏吃掉，避免顶栏和内容之间空一大段。
      · 顶栏已由 views/screen/index.vue 渲染，本组件铺满整个设计画布自己定位。
  -->
  <div class="archive-screen">
    <!-- ========== 左侧二级导航 ========== -->
    <nav class="archive-screen__nav stage-hit" aria-label="档案管理二级导航">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="archive-screen__nav-item"
        :class="{ 'is-active': tab.key === activeTab }"
        :aria-current="tab.key === activeTab ? 'page' : undefined"
        @click="activeTab = tab.key"
      >
        <i class="archive-screen__nav-bar" aria-hidden="true" />
        <span class="archive-screen__nav-text">{{ tab.label }}</span>
      </button>
    </nav>

    <!-- ========== 内容区 ========== -->
    <div class="archive-screen__body">
      <!--
        keep-alive：切页签时只保留**组件实例**（省掉重复拉字典、重建树）。
        ★ 检索条件、分页、展开状态**不保留**：没有下钻参数时由 resetActivePanel()
          调面板的 resetFilters() 清回初始值 —— 缓存下来的旧条件会变成隐性过滤，
          用户跨模块回来看到的是「上次的结果」（P2-01）。
      -->
      <keep-alive>
        <component
          :is="currentPanel"
          ref="panel"
          v-bind="panelProps"
          @drill="handleDrill"
          @open-statistics="activeTab = 'statistics'"
        />
      </keep-alive>
    </div>
  </div>
</template>

<script>
import { ScreenIcon } from '@/components/screen'
import ArchiveMaintain from './modules/ArchiveMaintain.vue'
import ArchiveStatistics from './modules/ArchiveStatistics.vue'
import ArchiveCategory from './modules/ArchiveCategory.vue'
import DocManager from './modules/doc/DocManager.vue'
// 方案 2.3.2 第 7 / 6 / 8 项：道路验收移交台账、道路交付及养护协议移交事项、竣工验收历史档案
import LedgerPanel from './modules/ledger/LedgerPanel.vue'
import HandoverPanel from './modules/handover/HandoverPanel.vue'
import CompletionPanel from './modules/completion/CompletionPanel.vue'

/**
 * 页签 key 到组件的映射。
 * 顺序固定为：档案维护 → 档案统计 → 道路交付养护移交 → 道路验收移交台账
 *            → 竣工验收历史档案 → 收发文管理 → 档案类别管理
 * （第 6/7/8 项插在统计与收发文之间，正好与方案 2.3.2 的条目序号 6、7、8、9 一致，
 *   见 data().tabs；「收发文管理」是第 9 项，不占一级导航）
 *
 * ★ 为什么没有独立的「档案查询」页签：它和「档案维护」共用同一套检索条件、
 *   同一个列表组件、同一个接口（`/land/archive/list`），差别只有「只读 + 条件默认全展开
 *   + 每页 20 条」——在大屏上这是同一个功能的两种用法，合并后查询能力一项没减。
 *   方案 2.3.2 第 3 项「档案查询」由「档案维护」承载；管理端仍保留独立菜单，
 *   那里才是「只查不改」的权限边界（查询角色只有 land:archive:list + export）。
 *   历史深链 `?tab=query` 会静默落回「档案维护」，条件照常生效。
 */
const PANELS = {
  maintain: ArchiveMaintain,
  statistics: ArchiveStatistics,
  handover: HandoverPanel,
  ledger: LedgerPanel,
  completion: CompletionPanel,
  doc: DocManager,
  category: ArchiveCategory
}

/** 需要接收下钻条件（initial-query）的面板：只有它们声明了这个 prop */
const DRILLABLE_PANELS = ['maintain', 'ledger', 'handover', 'completion']

/**
 * 路由 query 里属于「控制参数」的键，不当下钻条件用。
 * tab=页签、menu=顶栏高亮、map=是否加载真实地图、token=调试用。
 */
const CONTROL_QUERY_KEYS = ['tab', 'menu', 'map', 'token', '_t']

/**
 * 从路由 query 里取出**业务条件**（下钻用）。
 * 例：/screen/archive?tab=ledger&facilityId=xxx&status=已移交 → { facilityId, status }
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
  name: 'ArchiveScreen',
  components: { ScreenIcon },
  props: {
    /**
     * 初始页签，支持通过路由 ?tab=doc 直接落到某个页签
     * （统计页下钻或运维排查时很有用）
     */
    defaultTab: { type: String, default: 'maintain' }
  },
  data () {
    return {
      activeTab: PANELS[this.defaultTab] ? this.defaultTab : 'maintain',
      tabs: [
        { key: 'maintain', label: '档案维护' },
        { key: 'statistics', label: '档案统计' },
        { key: 'handover', label: '道路交付养护移交' },
        { key: 'ledger', label: '道路验收移交台账' },
        { key: 'completion', label: '竣工验收历史档案' },
        { key: 'doc', label: '收发文管理' },
        { key: 'category', label: '档案类别管理' }
      ],
      /** 下钻条件：透传给面板的 initialQuery（首次创建时生效）；深链带条件进来时从路由取 */
      drillQuery: readRouteQuery(this.$route && this.$route.query)
    }
  },
  computed: {
    /**
     * 只给「需要」的面板传下钻条件。
     * 之前是无条件 v-bind，导致 DocManager / ArchiveCategory 这些没有声明该 prop 的组件
     * 把 initial-query 透传到根元素上（变成 initial-query="[object Object]"）。
     */
    panelProps () {
      return DRILLABLE_PANELS.indexOf(this.activeTab) > -1 ? { 'initial-query': this.drillQuery } : {}
    },
    currentPanel () {
      return PANELS[this.activeTab] || ArchiveMaintain
    }
  },
  watch: {
    /**
     * 响应路由变化。
     * ★ 为什么必须 watch：`/screen/archive?tab=ledger&facilityId=xxx` 这类**同路由换 query**
     * 的跳转，Vue Router 不会重建组件 —— `data()` 只在首次创建时读一次路由，
     * 于是「从档案详情点『查看该项台账』」会表现成「地址变了但页面没动」。
     * 这里同时覆盖两种情况：换页签（tab）与换条件（facilityId / crzdbh / archiveId / status…）。
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
            this.consumeDrill(query)
          })
        }
      },
      deep: true
    },
    /**
     * 页签切换：**没有下载参数时**清空目标面板的检索条件（P2-01）。
     *
     * ★ 为什么放在这里而不是各面板自己监听：面板被 keep-alive 缓存，
     *   切走再切回不触发 created，旧条件就成了隐性筛子（测试反馈的「返回后旧条件仍生效」）。
     * ★ 为什么要判断「有没有下钻参数」：台账点「查看档案」、统计点「查看档案」这类
     *   明确下钻是靠 applyDrill 注入新条件的，此时清空会把刚注入的条件抹掉。
     */
    activeTab (tab) {
      this.$nextTick(() => {
        // 刚刚由 handleDrill 触发的页签切换：条件正在被下钻注入，不能清
        if (this._skipResetOnce) {
          this._skipResetOnce = false
          return
        }
        const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
        const query = readRouteQuery(routeQuery)
        // 路由上还挂着未消费的下钻参数 → 交给 consumeDrill，不要抢着清
        if (Object.keys(query).length) return
        this.resetActivePanel()
      })
    }
  },
  /**
   * 首屏就挂载（而不是等第一次路由变化）。
   *
   * ★ 为什么必须在 mounted 里消费一次深链：`data()` 只把路由条件塞进了
   *   drillQuery（面板 created 用得到），而 `consumeDrill` —— 它负责
   *   「按 archiveId 直接开档案详情」以及**把业务参数从地址栏摘掉** ——
   *   原来只挂在 `$route.query` watcher 上。直接打开
   *   `/screen/archive?tab=ledger&facilityId=xxx` 时 watcher 不会触发，
   *   于是参数一直挂在地址上，刷新一次就再注入一次。
   */
  mounted () {
    const query = readRouteQuery(this.$route && this.$route.query)
    if (!Object.keys(query).length) return
    this.$nextTick(() => {
      this.consumeFirstScreenDrill(query)
    })
  },
  /**
   * 页面重新激活（keep-alive / 从别的模块返回）：
   * 路由没有下钻参数就清空当前面板的条件，回到全量（P2-01）。
   */
  activated () {
    const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
    if (Object.keys(readRouteQuery(routeQuery)).length) return
    this.resetActivePanel()
  },
  methods: {
    /**
     * 消费一组下钻条件（路由 watcher 与 handleDrill 共用）。
     *
     * <p>archiveId 是「直接打开某份档案详情」的深链（台账/移交点「查看档案」用），
     * 不是检索条件：直接开详情，不要拿一个表单不认识的字段去过滤列表。
     */
    consumeDrill (query) {
      const panel = this.$refs.panel
      if (!panel) return
      if (query.archiveId && typeof panel.openDetailById === 'function') {
        panel.openDetailById(query.archiveId)
      } else if (typeof panel.applyDrill === 'function') {
        panel.applyDrill(query)
      }
      this.clearDrillQuery()
    },

    /**
     * 清掉路由上的**业务**下钻参数，避免下次进本页时被再次注入（P2-01）。
     *
     * ★ 为什么不清成空对象 `{}`：`tab` / `menu` / `map` / `token` 是**控制参数**
     *   （外层 views/screen/index.vue 靠 tab/menu 决定顶栏高亮与当前页签），
     *   一并清掉会让用户刷新后掉回默认页签、深链失效——那是新的 bug。
     *   这里只摘掉业务条件，达到「不会再次注入」的目的。
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

    /**
     * 首屏深链的消费。
     *
     * <p>与 consumeDrill 的区别只有一点：**检索条件已经在面板 created 里由
     * initialQuery 生效了**（panelProps 传的就是 drillQuery），首屏再 applyDrill 一次
     * 会让同一套条件连查两遍（白跑一次请求）。所以这里分两种情形：
     *   · 深链只是检索条件 → 只把参数从地址里摘掉；
     *   · 深链带 archiveId → 它表示「直接打开某份档案详情」，initialQuery 覆盖不到，
     *     必须走 consumeDrill（内部会 openDetailById 并摘参数）。
     */
    consumeFirstScreenDrill (query) {
      const panel = this.$refs.panel
      const needOpenDetail = !!query.archiveId && !!panel && typeof panel.openDetailById === 'function'
      if (!needOpenDetail) {
        this.clearDrillQuery()
        return
      }
      this.consumeDrill(query)
    },

    /**
     * 清空当前激活面板的检索条件。
     *
     * ★ 两级兜底，别让「面板没写 resetFilters」变成静默不动作（P2-01）：
     *   ① 有 resetFilters() 就调它（最精确：连隐藏条件一起清）；
     *   ② 否则若有 applyDrill()，用**空条件**下钻一次 —— 它本来就能"覆盖条件并重查"，
     *      等价于清空后查全量（收发文、档案类别这类共用面板走这条）。
     *   两级都没有（纯展示页签，如统计）才真的不需要动作。
     */
    resetActivePanel () {
      const panel = this.$refs.panel
      if (!panel) return
      if (typeof panel.resetFilters === 'function') {
        panel.resetFilters()
        return
      }
      if (typeof panel.applyDrill === 'function') {
        panel.applyDrill({})
      }
    },

    /**
     * 从统计页 / 台账 / 移交下钻到「档案维护」（原「档案查询」页签，已合并）。
     * 两种到达路径都要覆盖：
     *   - 维护面板是首次创建 → initialQuery 在 created 里生效；
     *   - 维护面板已被 keep-alive 缓存 → created 不会再跑，必须调用它的 applyDrill。
     */
    handleDrill (params) {
      this.drillQuery = Object.assign({}, params || {})
      /*
       * ★ 这个门闩解决的是「自己和自己打架」：
       *   切到 maintain 会触发下面的 activeTab watcher（它负责「无下钻就清条件」），
       *   而本次切页签恰恰是**带下钻**来的 —— 不清门闩的话，
       *   watcher 的 $nextTick 会排在 consumeDrill 之后执行，把刚注入的条件又清空，
       *   表现是「点查看该项台账，列表却是全量」。
       */
      this._skipResetOnce = true
      this.activeTab = 'maintain'
      this.$nextTick(() => {
        this.consumeDrill(this.drillQuery)
        this._skipResetOnce = false
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
.archive-screen {
  position: absolute;
  inset: 0;

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
      color: #ffffff;
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
