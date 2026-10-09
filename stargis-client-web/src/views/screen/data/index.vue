<template>
  <!--
    数据管理 · 大屏子页面
    ===============================================================
    版式与档案管理（views/screen/archive/index.vue）、提级论证管理
    （views/screen/escalation/index.vue）完全一致：**左侧二级导航 + 右侧内容区**。

      二级导航  左侧栏 (30,104) 宽 200，纵向铺到 底-40
      内容区    left:250 right:30 top:104 bottom:40

    三点约定：
      · 不再渲染「数据管理」页标题 —— 选中项本身就是当前模块，标题是重复信息；
      · 顶栏由 views/screen/index.vue 渲染，本组件铺满整个设计画布自己定位；
      · 本模块**保持地图可见**（不像档案/提级论证那样暂停 Cesium）：
        六个菜单项里「用地/配套录入」需要边看地块边填，右侧内容区之外
        仍留给地图，所以画布整体仍是 pointer-events: none + 各区自己开 stage-hit。

    页签 key 与 views/screen/index.vue 的 DATA_TABS 严格一致，顺序与后端菜单
    sort_no 一致：
      landImport 经营性用地批量导入 / land 经营性用地信息录入 /
      facility 配套地块数据录入 / facilityImport 配套信息批量导入 /
      attachment 配套附件管理 / recycle 数据更新与移除
  -->
  <div class="data-screen">
    <!-- ========== 左侧二级导航 ========== -->
    <nav class="data-screen__nav stage-hit" aria-label="数据管理二级导航">
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

      <span class="data-screen__tip">
        <screen-icon name="info" :size="13" />
        移除是软删，可在「数据更新与移除」里恢复；所有写操作都会留下字段级变更履历
      </span>
    </nav>

    <!-- ========== 内容区 ========== -->
    <div class="data-screen__body">
      <!-- keep-alive：切页签时不销毁面板，检索条件、分页、展开状态都保留 -->
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
import { ScreenIcon } from '@/components/screen'
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
  components: { ScreenIcon },
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
      activeTab: PANELS[this.defaultTab] ? this.defaultTab : 'land',
      tabs: [
        { key: 'landImport', label: '经营性用地批量导入' },
        { key: 'land', label: '经营性用地信息录入' },
        { key: 'facility', label: '配套地块数据录入' },
        { key: 'facilityImport', label: '配套信息批量导入' },
        { key: 'attachment', label: '配套附件管理' },
        { key: 'recycle', label: '数据更新与移除' }
      ],
      /** 下钻条件：透传给面板的 initial-query（首次创建时生效）；深链带条件进来时从路由取 */
      drillQuery: readRouteQuery(this.$route && this.$route.query)
    }
  },
  computed: {
    /**
     * 只给「需要」的面板传下钻条件（见 DRILLABLE_PANELS 的说明）。
     * 批量导入面板没有这个 prop，传了会把它渲染成 HTML 属性。
     */
    panelProps () {
      return DRILLABLE_PANELS.indexOf(this.activeTab) > -1 ? { 'initial-query': this.drillQuery } : {}
    },
    currentPanel () {
      return PANELS[this.activeTab] || LandEntryPanel
    }
  },
  watch: {
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
    width: 200px;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 10px;
    pointer-events: auto;
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
