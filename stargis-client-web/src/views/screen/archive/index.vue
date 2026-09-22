<template>
  <!--
    档案管理 · 大屏子页面
    ===============================================================
    顶栏由 views/screen/index.vue 渲染，本组件铺满整个设计画布，
    自己按设计稿坐标摆放（与首页的左右面板同一套坐标体系）：

      页标题   大标题-左切图 + 「档案管理」   (9,95) / (83,104)   ← 与首页面板标题同款
      页签条   5 个页签，切图与首页页签一致   (30,149)  高 46
      正文     left:30 right:30 top:207 bottom:40

    为什么要显式写坐标而不是用 flex 排：
      高保真是定尺设计稿，首页的标题/面板都对在 (95..141) 与 (149..1030) 两条带里。
      flex 自适应排会让本页的正文从顶栏正下方开始，既没有页标题、右侧也对不齐，
      看起来和首页不像一套东西。这里改成同一套坐标后，两页的版式完全一致。
  -->
  <div class="archive-screen">
    <!-- 页标题（与首页「出让地块情况统计」同款：切图 + 18px 白字） -->
    <img class="archive-screen__title-deco" :src="hf.panelTitleLeft" alt="" aria-hidden="true" />
    <h2 class="archive-screen__title">档案管理</h2>

    <!-- 页签条（切图复用首页的 tab 轨道/胶囊，保证两页观感一致） -->
    <div class="archive-screen__tabs stage-hit" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        class="archive-screen__tab"
        :class="{ 'is-active': tab.key === activeTab }"
        :aria-selected="String(tab.key === activeTab)"
        @click="activeTab = tab.key"
      >
        <img class="archive-screen__tab-track" :src="hf.tabTrack" alt="" aria-hidden="true" />
        <img
          class="archive-screen__tab-pill"
          :src="tab.key === activeTab ? hf.tabActive : hf.tabIdle"
          alt=""
          aria-hidden="true"
        />
        <span class="archive-screen__tab-text">{{ tab.label }}</span>
      </button>

      <span class="archive-screen__tip">
        <screen-icon name="info" :size="13" />
        档案类别挂在卷内文件上，一个档案可以包含多个类别的文件
      </span>
    </div>

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
import { hf } from '@/assets/screen-blue'
import ArchiveMaintain from './modules/ArchiveMaintain.vue'
import ArchiveQuery from './modules/ArchiveQuery.vue'
import ArchiveStatistics from './modules/ArchiveStatistics.vue'
import ArchiveCategory from './modules/ArchiveCategory.vue'
import DocManager from './modules/doc/DocManager.vue'

/**
 * 页签 key 到组件的映射。
 * 页签顺序固定为：档案维护 → 档案查询 → 档案统计 → 收发文管理 → 档案类别管理
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
      hf,
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

/* 本组件铺满整个设计画布，内部按设计稿坐标摆放（见模板注释） */
.archive-screen {
  position: absolute;
  inset: 0;

  /* ---------- 页标题：与首页面板标题同款 ---------- */
  &__title-deco {
    position: absolute;
    left: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    left: 83px;
    top: 104px;
    margin: 0;
    font-size: 18px;
    font-weight: 500;
    line-height: 18px;
    color: #ffffff;
    white-space: nowrap;
  }

  /* ---------- 页签条 ---------- */
  &__tabs {
    position: absolute;
    left: 30px;
    right: 30px;
    top: 149px;
    height: 46px;
    display: flex;
    align-items: center;
    // 与首页页签同一步进（轨道 160 + 间距 15）
    gap: 15px;
  }

  &__tab {
    position: relative;
    flex: 0 0 auto;
    width: 160px;
    height: 38px;
    padding: 0;
    font-family: inherit;
    font-size: 14px;
    color: #82c6ff;
    background: transparent;
    border: 0;
    cursor: pointer;
    .screen-focus-ring();

    &.is-active {
      color: #ffffff;
    }
  }

  &__tab-track,
  &__tab-pill {
    position: absolute;
    display: block;
    pointer-events: none;
  }

  &__tab-track {
    left: 0;
    top: 0;
    width: 160px;
    height: 38px;
  }

  &__tab-pill {
    left: 5px;
    top: 5px;
    width: 150px;
    height: 28px;
  }

  &__tab-text {
    position: relative;
    z-index: 1;
    line-height: 28px;
  }

  &__tip {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    margin-left: auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    white-space: nowrap;
  }

  /* ---------- 正文：页签条下方，留出与首页一致的底部边距 ---------- */
  &__body {
    position: absolute;
    left: 30px;
    right: 30px;
    top: 207px;
    bottom: 40px;
    // 高度必须由这里定死，子面板才能用 flex 正确分配剩余空间
    display: flex;
    flex-direction: column;
    min-height: 0;

    > * {
      flex: 1 1 0;
      min-height: 0;
    }
  }
}

/* 窄屏（画布被纵向拉长）时说明文案可能挤掉页签，直接隐藏 */
@media (max-width: 1500px) {
  .archive-screen__tip {
    display: none;
  }
}
</style>
