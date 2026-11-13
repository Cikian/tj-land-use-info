<template>
  <!--
    ScreenBottomDock 大屏底部「属性表」面板（可拖高 + 可折叠）
    ==================================================================
    首页底部属性表（HomeAttrPanel）有自己的业务表格（市区两级预警 + 地块预警），
    本组件是给**地图管理 / 配套分析管理**用的通用外壳：这两页底部原本只有一条
    47px 的「属性表」收起条（纯占位），现在要求与首页一样能拖高、能折叠。

    形态（与首页属性表同一套切图与坐标）：
      展开态   面板底 attr-bg.png 铺满；标题「属性表」；右上收起钮；
               上沿一条 ScreenResizeBar（往上拖 = 变高）；内容区留空态占位
      收起态   只剩底部条 attr-bar.png（47px），标题与展开钮重新对齐条内

    为什么内容区留空：这两页目前没有可展示的属性数据（地图拾取结果在右面板），
    先把「可拖高 + 可折叠」的交互做出来，后续有数据时直接用 slot 填。
    默认 collapsed = true，进入页面时与改造前逐像素一致（仍是一条收起条）。

    尺寸与折叠状态都由页面从 panelLayout 传进来（持久化在 localStorage），
    本组件只负责画和转发事件，不自己持有状态 —— 否则切页/刷新就忘了。
  -->
  <section
    class="screen-bottom-dock stage-hit"
    :class="{ 'is-collapsed': collapsed }"
    :style="rootStyle"
    role="region"
    :aria-label="title"
  >
    <!-- 底图 / 收起条（两张都是设计稿切图） -->
    <img v-if="!collapsed" class="screen-bottom-dock__bg" :src="hf.attrBg" alt="" aria-hidden="true" />
    <img v-else class="screen-bottom-dock__bar" :src="hf.attrBar" alt="" aria-hidden="true" />

    <h3 class="screen-bottom-dock__title" :class="{ 'is-collapsed': collapsed }">{{ title }}</h3>

    <!-- 收起 / 展开（与首页属性表同一组图标） -->
    <button
      type="button"
      class="screen-bottom-dock__toggle"
      :class="{ 'is-collapsed': collapsed }"
      :aria-label="collapsed ? `展开${title}` : `收起${title}`"
      :title="collapsed ? `展开${title}` : `收起${title}`"
      @click="toggle"
    >
      <img :src="collapsed ? hf.attrExpand : hf.attrCollapse" alt="" aria-hidden="true" />
    </button>

    <!-- 内容区：默认空态；有数据时由页面通过默认插槽填入 -->
    <div v-if="!collapsed" class="screen-bottom-dock__body">
      <slot>
        <p class="screen-bottom-dock__empty">{{ emptyText }}</p>
      </slot>
    </div>

    <!-- 上沿拖高条：往上拖 = 面板变高（与数据管理列表面板同一套手感） -->
    <screen-resize-bar
      v-if="!collapsed"
      class="screen-bottom-dock__resizer"
      :value="height"
      :min="min"
      :max="max"
      :aria-label="`拖动调整${title}高度`"
      @input="handleResize"
    />
  </section>
</template>

<script>
import { hf } from '@/assets/screen-blue'
import ScreenResizeBar from './ScreenResizeBar.vue'

/** 收起态高度（设计稿底部条 47px） */
const BAR_HEIGHT = 47

export default {
  name: 'ScreenBottomDock',
  components: { ScreenResizeBar },
  props: {
    /** 面板标题（两页都是「属性表」） */
    title: { type: String, default: '属性表' },
    /** 展开态高度（设计画布像素），由页面从 panelLayout 传 */
    height: { type: Number, default: 321 },
    /** 展开态最小高度：再矮就连表头都放不下 */
    min: { type: Number, default: 160 },
    /** 展开态最大高度 */
    max: { type: Number, default: 700 },
    /** 是否已折叠（只剩 47px 底部条） */
    collapsed: { type: Boolean, default: true },
    /** 左右内缩（设计画布像素）：随两侧面板拖宽 / 折叠变化 */
    leftInset: { type: Number, default: 460 },
    rightInset: { type: Number, default: 460 },
    /** 空态文案 */
    emptyText: { type: String, default: '暂无数据' }
  },
  data () {
    return { hf, barHeight: BAR_HEIGHT }
  },
  computed: {
    rootStyle () {
      return {
        left: `${this.leftInset}px`,
        right: `${this.rightInset}px`,
        bottom: '50px',
        height: `${this.collapsed ? BAR_HEIGHT : this.height}px`
      }
    }
  },
  methods: {
    handleResize (value) {
      this.$emit('update:height', value)
    },
    toggle () {
      this.$emit('update:collapsed', !this.collapsed)
    }
  }
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-bottom-dock {
  position: absolute;
  // ★ 高度 / 左右内缩由 rootStyle 内联下发：折叠与两侧面板拖宽时都要跟着变，
  //   过渡写在这里，让「面板滑走 + 属性表长出来」是同一个 320ms。
  //   ⚠ 拖动改高时必须关掉过渡（见文件末尾的 body.is-resizing-panel 规则）：
  //     否则手指已经移动，面板高度还在用 320ms 追赶，手感明显发黏。
  transition: height var(--screen-duration-slow) var(--screen-ease),
    left var(--screen-duration-slow) var(--screen-ease),
    right var(--screen-duration-slow) var(--screen-ease);

  &__bg,
  &__bar {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    display: block;
    pointer-events: none;
  }

  &__bg {
    height: 100%;
  }

  &__bar {
    height: 47px;
  }

  &__title {
    position: absolute;
    left: 48px;
    top: 15px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 22px;
    color: #ffffff;
    white-space: nowrap;

    /* 收起态：条高 47、行高 22 → 上下各留 12.5，与首页收起态一致 */
    &.is-collapsed {
      top: 13px;
    }
  }

  &__toggle {
    position: absolute;
    right: 23px;
    top: 18px;
    width: 14px;
    height: 14px;
    padding: 0;
    background: transparent;
    border: 0;
    cursor: pointer;
    .screen-focus-ring();

    img {
      display: block;
      width: 14px;
      height: 14px;
    }

    &.is-collapsed {
      top: 17px;
    }
  }

  /* 展开态内容区：后续接入属性数据时由 slot 填充，现在是空态占位 */
  &__body {
    position: absolute;
    left: 20px;
    right: 20px;
    top: 63px;
    bottom: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
  }

  &__empty {
    margin: 0;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-mute);
    text-align: center;
  }

  /* 拖高条：贴在面板上沿外侧（不参与面板内布局，避免挤压内容） */
  &__resizer {
    position: absolute;
    left: 0;
    right: 0;
    top: -12px;
  }
}

/*
 * 拖动尺寸期间关掉高度过渡（全局选择器：body 上的 .is-resizing-panel 由
 * ScreenResizeBar 在 pointerdown 时挂、pointerup 时摘）。
 * ★ 不关的话拖动改高会被 320ms 的过渡拖住，面板跟不上指针。
 */
body.is-resizing-panel .screen-bottom-dock {
  transition: none;
}
</style>
