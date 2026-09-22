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

/**
 * 页签 key 到组件的映射。
 * 顺序固定为：档案维护 → 档案查询 → 档案统计 → 收发文管理 → 档案类别管理
 * （「收发文管理」插在统计与类别管理之间，见 data().tabs）
 */
const PANELS = {
  maintain: ArchiveMaintain,
  query: ArchiveQuery,
  statistics: ArchiveStatistics,
  doc: DocManager,
  category: ArchiveCategory,
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
        { key: 'doc', label: '收发文管理' },
        { key: 'category', label: '档案类别管理' },
      ],
      /** 下钻条件：透传给查询面板的 initialQuery（首次创建时生效） */
      drillQuery: {},
    }
  },
  computed: {
    /**
     * 只给「需要」的面板传下钻条件。
     * 之前是无条件 v-bind，导致 DocManager / ArchiveCategory 这些没有声明该 prop 的组件
     * 把 initial-query 透传到根元素上（变成 initial-query="[object Object]"）。
     */
    panelProps () {
      return this.activeTab === 'query' ? { 'initial-query': this.drillQuery } : {}
    },
    currentPanel () {
      return PANELS[this.activeTab] || ArchiveMaintain
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
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius);
    box-shadow: var(--screen-shadow), var(--screen-shadow-inset);
    -webkit-backdrop-filter: blur(var(--screen-blur));
    backdrop-filter: blur(var(--screen-blur));
  }

  &__nav-item {
    position: relative;
    flex: 0 0 auto;
    height: 44px;
    padding: 0 12px 0 18px;
    font-family: inherit;
    font-size: var(--screen-font-md);
    line-height: 44px;
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
    left: 6px;
    top: 50%;
    width: 3px;
    height: 18px;
    margin-top: -9px;
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
