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

      <span class="escalation-screen__tip">
        <screen-icon name="info" :size="13" />
        论证流程在线下办理，系统只做登记与台账留存；意见记录只增不改不删
      </span>
    </nav>

    <!-- ========== 内容区 ========== -->
    <div class="escalation-screen__body">
      <!-- keep-alive：切页签时不销毁模块，检索条件、分页、列表视图都保留 -->
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
  audit: EscalationAudit,
}

export default {
  name: 'EscalationScreen',
  components: { ScreenIcon },
  props: {
    /**
     * 初始页签，支持路由深链 ?tab=audit 直接落到某个页签
     * （与 views/screen/index.vue 的 REVIEW_TABS 校验配合使用）。
     */
    defaultTab: { type: String, default: 'entry' },
  },
  data () {
    return {
      activeTab: PANELS[this.defaultTab] ? this.defaultTab : 'entry',
      tabs: [
        { key: 'entry', label: '项目录入' },
        { key: 'query', label: '查询统计' },
        { key: 'ledger', label: '资料及台账管理' },
        { key: 'audit', label: '提级论证审批' },
      ],
      /** 下钻条件：透传给台账面板的 initial-query（首次创建时生效） */
      drillQuery: {},
    }
  },
  computed: {
    /**
     * 只给「需要」的面板传下钻条件。
     * 无条件 v-bind 会让没有声明该 prop 的面板把它透传到根元素上
     * （变成 initial-query="[object Object]"），档案模块踩过这个坑。
     */
    panelProps () {
      return this.activeTab === 'ledger' ? { 'initial-query': this.drillQuery } : {}
    },
    currentPanel () {
      return PANELS[this.activeTab] || EscalationEntry
    },
  },
  methods: {
    /**
     * 从「查询统计」的图表下钻到「资料及台账管理」。
     * 两种到达路径都要覆盖（照档案模块的 handleDrill + applyDrill 双路径写法）：
     *   - 台账面板首次创建 → initialQuery 在 data() 里生效；
     *   - 台账面板已被 keep-alive 缓存 → data() 不会再跑，必须调用它的 applyDrill。
     */
    handleDrill (params) {
      this.drillQuery = Object.assign({}, params || {})
      this.activeTab = 'ledger'
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
