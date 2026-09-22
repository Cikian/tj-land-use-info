<template>
  <!--
    ScreenHeader 大屏顶栏（高保真还原）
    ==================================================================
    完全按高保真模型 docs/高保真/.../首页.html 的定尺布局实现：
      底图      bg-top_u6.png            (0, 0)    1920×128
      系统标题  大标题切图 title.png       (34, 18)   599×35
      导航项    nav-*.png               y=6      宽 140 高 54，右对齐到 x=1780
      用户区    头像/用户名/下拉箭头       (1790, 22) 起
      时钟      15:36 + 星期 + 日期       (477, 115) 起

    ⚠ 导航项数量：高保真是 6 项（140 宽 / 150 间距）。本系统一级菜单为
      8 项（新增「档案管理」「提级论证管理」，保留「查询统计」「收发文」），
      因此间距由 150 收紧到 140、整块右对齐到 x=1780，
      保证 8 项仍在标题切图（止于 x=633）与用户区（起于 x=1790）之间。
  -->
  <header class="screen-header">
    <!-- 底图：整条顶栏的深蓝渐变 + 装饰线 -->
    <img class="screen-header__bg" :src="hf.headerBg" alt="" aria-hidden="true" />

    <!-- 系统标题（切图，含左侧徽标与下方蓝色装饰线） -->
    <img class="screen-header__title" :src="hf.title" :alt="title" />

    <!-- 一级导航 -->
    <nav v-if="menus && menus.length" class="screen-header__nav" aria-label="主导航">
      <button
        v-for="(item, index) in menus"
        :key="item.key"
        type="button"
        class="screen-header__nav-item stage-hit"
        :class="{ 'is-active': item.key === activeMenu }"
        :style="navItemStyle(index)"
        :aria-current="item.key === activeMenu ? 'page' : undefined"
        @click="handleMenu(item)"
      >
        <img
          class="screen-header__nav-bg"
          :src="item.key === activeMenu ? hf.navActive : hf.navIdle"
          alt=""
          aria-hidden="true"
        />
        <span class="screen-header__nav-text">{{ item.label }}</span>
      </button>
    </nav>

    <!-- 用户区 -->
    <div class="screen-header__user stage-hit">
      <img class="screen-header__avatar" :src="hf.avatar" alt="" aria-hidden="true" />
      <span class="screen-header__user-name">{{ userName }}</span>
      <img class="screen-header__caret" :src="hf.caretDown" alt="" aria-hidden="true" />
    </div>

    <!-- 实时时钟（高保真：大号时间 + 右侧星期 / 日期两行小字） -->
    <template v-if="showClock">
      <time class="screen-header__clock" :datetime="clockISO">{{ clockTime }}</time>
      <span v-if="showWeekday" class="screen-header__weekday">{{ weekText }}</span>
      <span class="screen-header__date">{{ clockDate }}</span>
    </template>

    <!-- 在线状态（高保真未出现，保留能力，默认不渲染） -->
    <span
      v-if="online !== null"
      class="screen-header__online stage-hit"
      :class="{ 'is-off': !online }"
    >
      <i class="screen-header__dot" aria-hidden="true" />
      {{ online ? onlineText : offlineText }}
    </span>
  </header>
</template>

<script>
import { padIndex } from './utils'
import { hf } from '@/assets/screen-blue'

const WEEK = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

/** 高保真定尺参数（画布坐标） */
export const HEADER_LAYOUT = {
  height: 128,
  navTop: 6,
  navItemWidth: 140,
  navItemHeight: 54,
  navItemStep: 140,
  navRight: 1780,
}

export default {
  name: 'ScreenHeader',
  props: {
    /** 系统标题（用于无障碍文本；视觉标题来自切图） */
    title: { type: String, default: '' },
    /** 导航项：[{ key, label }] */
    menus: { type: Array, default: () => [] },
    /** 当前激活导航 key */
    activeMenu: { type: String, default: '' },
    /** 是否显示实时时钟 */
    showClock: { type: Boolean, default: true },
    /** 时钟格式：datetime 含日期 / time 仅时间 */
    clockFormat: { type: String, default: 'datetime' },
    /** 是否显示星期 */
    showWeekday: { type: Boolean, default: true },
    /** 用户名 */
    userName: { type: String, default: '' },
    /** 在线状态：true 在线 / false 离线 / null 不展示 */
    online: { type: Boolean, default: null },
    onlineText: { type: String, default: '在线' },
    offlineText: { type: String, default: '离线' },
  },
  data () {
    return {
      hf,
      now: new Date(),
      timer: null,
    }
  },
  computed: {
    clockTime () {
      const d = this.now
      return [padIndex(d.getHours()), padIndex(d.getMinutes())].join(':')
    },
    clockDate () {
      const d = this.now
      return `${d.getFullYear()}-${padIndex(d.getMonth() + 1)}-${padIndex(d.getDate())}`
    },
    weekText () {
      return WEEK[this.now.getDay()]
    },
    clockISO () {
      return this.now.toISOString()
    },
  },
  mounted () {
    this.startClock()
    document.addEventListener('visibilitychange', this.handleVisibility)
  },
  beforeDestroy () {
    this.stopClock()
    document.removeEventListener('visibilitychange', this.handleVisibility)
  },
  methods: {
    /** 第 index 个导航项的绝对定位（整块右对齐，见头部注释） */
    navItemStyle (index) {
      const { navTop, navItemWidth, navItemHeight, navItemStep, navRight } = HEADER_LAYOUT
      const count = this.menus.length
      const left = navRight - (count - 1 - index) * navItemStep - navItemWidth
      return {
        left: `${left}px`,
        top: `${navTop}px`,
        width: `${navItemWidth}px`,
        height: `${navItemHeight}px`,
      }
    },
    startClock () {
      if (!this.showClock || this.timer) return
      this.timer = setInterval(() => {
        this.now = new Date()
      }, 1000)
    },
    stopClock () {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    /** 页面不可见时停止计时，避免大屏长期挂后台空转 */
    handleVisibility () {
      if (document.hidden) {
        this.stopClock()
      } else {
        this.now = new Date()
        this.startClock()
      }
    },
    handleMenu (item) {
      if (item.key === this.activeMenu) return
      this.$emit('menu-change', item.key, item)
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-header {
  position: absolute;
  left: 0;
  top: 0;
  width: 1920px;
  height: 128px;
  // 顶部装饰线会略微溢出，允许显示
  overflow: visible;

  &__bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 1920px;
    height: 128px;
    display: block;
  }

  &__title {
    position: absolute;
    left: 34px;
    top: 18px;
    width: 599px;
    height: 35px;
    display: block;
  }

  &__nav {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 128px;
  }

  &__nav-item {
    position: absolute;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0;
    font-family: inherit;
    font-size: 16px;
    font-weight: 500;
    color: #66afd4;
    background: transparent;
    border: 0;
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
    }

    &.is-active {
      color: #ffffff;
      text-shadow: 0 0 10px rgba(130, 198, 255, 0.75);
    }
  }

  &__nav-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 140px;
    height: 54px;
    display: block;
    pointer-events: none;
  }

  &__nav-text {
    position: relative;
    z-index: 1;
    padding-top: 2px;
    white-space: nowrap;
  }

  /* ---------- 用户区 ---------- */
  &__user {
    position: absolute;
    left: 1790px;
    top: 22px;
    display: flex;
    align-items: center;
    height: 26px;
    cursor: pointer;
  }

  &__avatar {
    width: 26px;
    height: 26px;
    display: block;
  }

  &__user-name {
    margin-left: 10px;
    font-size: 14px;
    font-weight: 500;
    line-height: 14px;
    color: #b8d5ea;
    white-space: nowrap;
  }

  &__caret {
    margin-left: 12px;
    width: 8px;
    height: 5px;
    display: block;
  }

  /* ---------- 时钟 ---------- */
  &__clock {
    position: absolute;
    left: 477px;
    top: 115px;
    font-family: var(--screen-font-number-family);
    font-size: 52px;
    font-weight: 400;
    line-height: 39px;
    color: #ffffff;
    letter-spacing: 0.01em;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  &__weekday {
    position: absolute;
    left: 597px;
    top: 116px;
    font-size: 16px;
    line-height: 16px;
    color: #66afd4;
    white-space: nowrap;
  }

  &__date {
    position: absolute;
    left: 597px;
    top: 140px;
    font-size: 16px;
    line-height: 16px;
    color: #66afd4;
    white-space: nowrap;
  }

  /* ---------- 在线状态（默认不渲染） ---------- */
  &__online {
    position: absolute;
    right: 24px;
    bottom: 10px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 3px 10px;
    font-size: var(--screen-font-xs);
    border-radius: var(--screen-radius-pill);
    border: 1px solid var(--screen-border-soft);
    background: rgba(67, 233, 114, 0.1);
    color: var(--screen-success);
    white-space: nowrap;

    &.is-off {
      background: rgba(102, 175, 212, 0.1);
      color: var(--screen-text-mute);
    }
  }

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
    box-shadow: 0 0 6px currentColor;
  }
}
</style>
