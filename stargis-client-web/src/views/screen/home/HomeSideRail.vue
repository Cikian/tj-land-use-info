<template>
  <!--
    HomeSideRail 首页侧栏「边缘轨道」：屏幕边缘的竖排小页签 + 折叠把手
    ==================================================================
    为什么单独一个组件：两侧面板折叠时是**整体滑出屏幕**（位移在面板自己身上），
    而把手必须留在屏幕边缘不动 —— 把手若挂在面板里，会跟着一起滑走，
    折叠后就再也点不回来。

    组成（都在 30px 的屏边留白里，不与 400 宽的面板重叠）：
      · 竖排小页签  右面板「统计 / 图层树」切换用（只有右侧传 tabs 才渲染）；
                    面板滑走后仍可点，点一下顺便把面板拉回来（见父组件 handleRightTab）
      · 折叠把手    始终贴在边缘、竖向居中：展开时朝屏幕外（收起方向），
                    折叠时朝屏幕内（展开方向）

    坐标全部相对设计画布的左右边缘（left:0 / right:0），不用 1920 定值。
  -->
  <div class="home-rail" :class="`is-${side}`">
    <!-- 竖排小页签（可选） -->
    <div v-if="tabs && tabs.length" class="home-rail__tabs" role="tablist" :aria-label="tabsLabel">
      <button
        v-for="(tab, index) in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        class="home-rail__tab stage-hit"
        :class="{ 'is-active': tab.key === activeTab }"
        :style="{ top: `${tabsTop + index * tabStep}px` }"
        :aria-selected="String(tab.key === activeTab)"
        @click="$emit('select', tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 折叠 / 展开把手 -->
    <button
      type="button"
      class="home-rail__handle stage-hit"
      :aria-expanded="String(!collapsed)"
      :aria-label="collapsed ? expandText : collapseText"
      :title="collapsed ? expandText : collapseText"
      @click="$emit('toggle')"
    >
      <svg class="home-rail__chevron" viewBox="0 0 10 16" aria-hidden="true">
        <path
          :d="chevronPath"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>
  </div>
</template>

<script>
/** 竖排页签的起点与步进（设计画布坐标）：面板从 y=149 起，页签从标题行下方开始 */
const TABS_TOP = 190
const TAB_STEP = 100

/**
 * 箭头方向：左边缘「收起」朝左、「展开」朝右；右边缘反过来。
 * 语义是「点下去面板会往哪边走」，不是「面板现在在哪边」。
 */
const CHEVRON_LEFT = 'M8 1 L2 8 L8 15'
const CHEVRON_RIGHT = 'M2 1 L8 8 L2 15'

export default {
  name: 'HomeSideRail',
  props: {
    /** 哪一侧：left / right（决定贴哪条边、把手圆角方向、箭头朝向） */
    side: {
      type: String,
      default: 'left',
      validator: (value) => value === 'left' || value === 'right'
    },
    /** 竖排小页签：[{ key, label }]；为空则只渲染折叠把手 */
    tabs: { type: Array, default: () => [] },
    /** 当前选中的页签 key */
    activeTab: { type: String, default: '' },
    /** 面板是否已折叠（只影响把手文案与箭头方向） */
    collapsed: { type: Boolean, default: false },
    /** 页签组的无障碍名称 */
    tabsLabel: { type: String, default: '面板内容切换' },
    /** 把手文案 */
    collapseText: { type: String, default: '收起面板' },
    expandText: { type: String, default: '展开面板' }
  },
  data () {
    return { tabsTop: TABS_TOP, tabStep: TAB_STEP }
  },
  computed: {
    chevronPath () {
      const toEdge = this.side === 'left' ? CHEVRON_LEFT : CHEVRON_RIGHT
      const toCenter = this.side === 'left' ? CHEVRON_RIGHT : CHEVRON_LEFT
      return this.collapsed ? toCenter : toEdge
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.home-rail {
  /*
    只占屏边这一条：宽 30（= 面板 left/right 的 30px 留白）。
    本身不开 pointer-events，子元素（页签 / 把手）各自带 .stage-hit，
    中间的地图与页签之间的缝隙仍然可以拖动。
  */
  position: absolute;
  top: 0;
  bottom: 0;
  width: 30px;
  pointer-events: none;

  &.is-left {
    left: 0;
  }

  &.is-right {
    right: 0;
  }

  /* ---------------- 竖排小页签 ---------------- */
  &__tabs {
    position: absolute;
    top: 0;
    bottom: 0;
    right: 0;
    width: 26px;
    pointer-events: none;
  }

  &__tab {
    position: absolute;
    right: 0;
    width: 26px;
    height: 92px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    letter-spacing: 2px;
    line-height: 26px;
    /* 中文竖排：自上而下，单列 */
    writing-mode: vertical-rl;
    text-orientation: upright;
    color: #82c6ff;
    background: rgba(8, 30, 62, 0.86);
    border: 1px solid var(--screen-border);
    border-right: 0;
    border-radius: var(--screen-radius-sm) 0 0 var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: #ffffff;
      background: rgba(20, 60, 110, 0.95);
      border-color: var(--screen-border-strong);
    }

    &.is-active {
      color: #ffffff;
      background: rgba(28, 82, 148, 0.95);
      border-color: var(--screen-border-strong);
      box-shadow: -2px 0 12px rgba(85, 185, 255, 0.35);
    }
  }

  /* ---------------- 折叠把手 ---------------- */
  &__handle {
    position: absolute;
    top: 50%;
    width: 20px;
    height: 60px;
    padding: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #82c6ff;
    background: rgba(8, 30, 62, 0.9);
    border: 1px solid var(--screen-border);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: #ffffff;
      background: rgba(20, 60, 110, 0.95);
      border-color: var(--screen-border-strong);
    }

    /* 只平移 Y，不要再叠加 X —— 位移由左右两套规则各自给 */
    transform: translateY(-50%);
  }

  &__chevron {
    width: 9px;
    height: 15px;
    display: block;
  }

  &.is-left &__handle {
    left: 0;
    border-left: 0;
    border-radius: 0 var(--screen-radius-sm) var(--screen-radius-sm) 0;
  }

  &.is-right &__handle {
    right: 0;
    border-right: 0;
    border-radius: var(--screen-radius-sm) 0 0 var(--screen-radius-sm);
  }
}
</style>
