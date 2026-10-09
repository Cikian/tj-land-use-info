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

      <span class="archive-screen__tip">
        <screen-icon name="info" :size="13" />
        档案类别挂在卷内文件上，一个档案可以包含多个类别的文件
      </span>
    </nav>

    <!-- ========== 内容区 ========== -->
    <div class="archive-screen__body">
      <!-- keep-alive：切页签时不销毁模块，检索条件、分页、展开状态都保留 -->
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
import ArchiveQuery from './modules/ArchiveQuery.vue'
import ArchiveStatistics from './modules/ArchiveStatistics.vue'
import ArchiveCategory from './modules/ArchiveCategory.vue'
import DocManager from './modules/doc/DocManager.vue'
// 方案 2.3.2 第 7 / 6 / 8 项：道路验收移交台账、道路交付及养护协议移交事项、竣工验收历史档案
import LedgerPanel from './modules/ledger/LedgerPanel.vue'
import HandoverPanel from './modules/handover/HandoverPanel.vue'
import CompletionPanel from './modules/completion/CompletionPanel.vue'

/**
 * 页签 key 到组件的映射。
 * 顺序固定为：档案维护 → 档案查询 → 档案统计 → 道路交付养护移交 → 道路验收移交台账
 *            → 竣工验收历史档案 → 收发文管理 → 档案类别管理
 * （第 6/7/8 项插在统计与收发文之间，正好与方案 2.3.2 的条目序号 6、7、8、9 一致，
 *   见 data().tabs；「收发文管理」是第 9 项，不占一级导航）
 */
const PANELS = {
  maintain: ArchiveMaintain,
  query: ArchiveQuery,
  statistics: ArchiveStatistics,
  handover: HandoverPanel,
  ledger: LedgerPanel,
  completion: CompletionPanel,
  doc: DocManager,
  category: ArchiveCategory,
}

/** 需要接收下钻条件（initial-query）的面板：只有它们声明了这个 prop */
const DRILLABLE_PANELS = ['query', 'ledger', 'handover', 'completion']

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
    defaultTab: { type: String, default: 'maintain' },
  },
  data () {
    return {
      activeTab: PANELS[this.defaultTab] ? this.defaultTab : 'maintain',
      tabs: [
        { key: 'maintain', label: '档案维护' },
        { key: 'query', label: '档案查询' },
        { key: 'statistics', label: '档案统计' },
        { key: 'handover', label: '道路交付养护移交' },
        { key: 'ledger', label: '道路验收移交台账' },
        { key: 'completion', label: '竣工验收历史档案' },
        { key: 'doc', label: '收发文管理' },
        { key: 'category', label: '档案类别管理' },
      ],
      /** 下钻条件：透传给面板的 initialQuery（首次创建时生效）；深链带条件进来时从路由取 */
      drillQuery: readRouteQuery(this.$route && this.$route.query),
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
    },
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
            const panel = this.$refs.panel
            if (panel && typeof panel.applyDrill === 'function') {
              panel.applyDrill(query)
            }
          })
        }
      },
      deep: true,
    },
  },
  methods: {
    /**
     * 从统计页下钻到查询页。
     * 两种到达路径都要覆盖：
     *   - 查询面板是首次创建 → initialQuery 在 created 里生效；
     *   - 查询面板已被 keep-alive 缓存 → created 不会再跑，必须调用它的 applyDrill。
     */
    handleDrill (params) {
      this.drillQuery = Object.assign({}, params || {})
      this.activeTab = 'query'
      this.$nextTick(() => {
        const panel = this.$refs.panel
        if (panel && typeof panel.applyDrill === 'function') {
          panel.applyDrill(this.drillQuery)
        }
      })
    },

    /** 供外部（路由 / 父组件）切换页签 */
    setTab (key) {
      if (PANELS[key]) this.activeTab = key
    },
  },
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
