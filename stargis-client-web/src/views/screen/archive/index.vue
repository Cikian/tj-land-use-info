<template>
  <!--
    档案管理 · 大屏子页面
    ===============================================================
    顶栏「档案管理」点进来之后的整页内容。顶栏仍由 views/screen/index.vue 渲染，
    这一层只占浮层的正文区域，所以观感与首页统计面板完全一致（同样的玻璃面板、
    同样的青绿描边），而不是跳到一个风格割裂的后台页面。

    结构：
      页签条（档案维护 / 档案查询 / 档案统计 / 档案类别管理）
      └─ 当前页签的模块内容（用 keep-alive 缓存，来回切页签不丢检索条件与滚动位置）

    页签之间的联动：
      「档案统计」的明细表点「查看档案」→ 抛 drill → 这里切到「档案查询」并把
      ptxmmc / crzdbh 带过去（调用查询面板公开的 applyDrill 方法）。
  -->
  <div class="archive-screen">
    <screen-panel class="archive-screen__tabs-panel" :bar="false">
      <template #title>
        <screen-tabs v-model="activeTab" :tabs="tabs" />
      </template>
      <template #extra>
        <span class="archive-screen__tip">
          <screen-icon name="info" :size="13" />
          档案类别挂在卷内文件上，一个档案可以包含多个类别的文件
        </span>
      </template>
    </screen-panel>

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
import { ScreenPanel, ScreenTabs, ScreenIcon } from '@/components/screen'
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
  components: { ScreenPanel, ScreenTabs, ScreenIcon },
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

.archive-screen {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  // 页签条按内容高度，模块内容吃掉剩余空间
  &__tabs-panel {
    flex: 0 0 auto;
  }

  &__tip {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    white-space: nowrap;
  }

  &__body {
    flex: 1 1 auto;
    min-height: 0;
  }
}

// 页签条所在面板高度压到 42px，与首页底部预警面板的处理保持一致
.archive-screen__tabs-panel /deep/ .screen-panel__head {
  height: 42px;
  padding-left: 8px;
}

// 说明文案在窄屏会挤掉页签，直接隐藏（信息不是必需的）
@media (max-width: 1500px) {
  .archive-screen__tip {
    display: none;
  }
}
</style>
