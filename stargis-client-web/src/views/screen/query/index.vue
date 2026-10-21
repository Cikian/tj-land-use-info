<template>
  <!--
    查询统计 · 大屏子页面（方案 2.3.1（五））
    ===============================================================
    版式与数据管理/档案管理一致：**左侧二级导航 + 右侧内容区**。
      二级导航  左侧栏 (30,104) 宽 200，纵向铺到 底-40
      内容区    left:250 right:30 top:104 bottom:40

    三个页签对应高保真 `查询统计.html` 的三个动态面板：
      经营性用地查询  condition + 分析结果 + 结果表（点编号看该宗地配套）
      用地配套项目查询 同上，条件换成配套的 14 个环节字段
      查询导出        字段/运算符/值/关系符自由组合 → 查询 或 导出 Excel

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

      <span class="query-screen__tip">
        <screen-icon name="info" :size="13" />
        统计口径与旧系统一致：经营性用地按配套名称去重、环节为漏斗；导出含「结果 + 指标」两个页签
      </span>
    </nav>

    <!-- ========== 内容区 ========== -->
    <div class="query-screen__body">
      <!-- keep-alive：切页签时不销毁，条件、列设置、分页都保留 -->
      <keep-alive>
        <stat-query-view v-if="activeTab === 'land'" key="land" data-type="land" />
        <stat-query-view v-else-if="activeTab === 'facility'" key="facility" data-type="facility" />
        <advanced-query-view v-else key="advanced" />
      </keep-alive>
    </div>
  </div>
</template>

<script>
import { ScreenIcon } from '@/components/screen'
import { QUERY_TABS } from './constants'
import StatQueryView from './modules/StatQueryView.vue'
import AdvancedQueryView from './modules/AdvancedQueryView.vue'

/** 页签 key → 是否合法（供深链校验，与 QUERY_TABS 同源） */
const TAB_KEYS = QUERY_TABS.map((tab) => tab.key)

export default {
  name: 'QueryScreen',
  components: { AdvancedQueryView, ScreenIcon, StatQueryView },
  props: {
    /** 初始页签，支持 /screen/query?tab=facility 深链 */
    defaultTab: { type: String, default: 'land' }
  },
  data () {
    return {
      activeTab: TAB_KEYS.indexOf(this.defaultTab) > -1 ? this.defaultTab : 'land',
      tabs: QUERY_TABS
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
    }
  },
  methods: {
    /** 供外部（路由 / 父组件）切页签 */
    setTab (key) {
      if (TAB_KEYS.indexOf(key) > -1) {
        this.activeTab = key
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
