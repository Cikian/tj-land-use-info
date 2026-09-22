<template>
  <!--
    ScreenHeader 大屏顶栏（高保真还原）
    ==================================================================
    完全按高保真模型 docs/高保真/.../首页.html 的定尺布局实现：
      底图      bg-top_u6.png           (0, 0)    1920×128（横向跟随拉伸）
      系统标题  大标题切图 title.png      (34, 18)   599×35
      导航项    nav-*.png              y=6      宽 140 高 54，右对齐到设计右边界 -140
      用户区    头像/用户名/下拉箭头      设计右边界 -130 起
      时钟      15:36 + 星期 + 日期      (477, 115) 起

    ⚠ 自适应：顶栏宽度随设计画布宽度变化（见 ScreenStage），
      所以导航与用户区一律用 right 定位、底图用 width:100% 拉伸，
      不能写 1920 的固定值。

    ⚠ 导航项数量：高保真是 6 项（140 宽 / 150 间距）。本系统一级菜单为
      7 项（新增「档案管理」「提级论证管理」，「收发文」是档案管理模块内的
      页签、不单独占一级菜单），因此间距由 150 收紧到 140，
      整块右对齐，保证仍落在标题切图（止于 x=633）与用户区之间。
  -->
  <header class="screen-header">
    <!-- 底图：整条顶栏的深蓝渐变 + 装饰线 -->
    <img class="screen-header__bg" :src="hf.headerBg" alt="" aria-hidden="true" />

    <!-- 系统标题（切图，含左侧徽标与下方蓝色装饰线） -->
    <img class="screen-header__title" :src="hf.title" :alt="title" />

    <!-- 一级导航 -->
    <nav
      v-if="menus && menus.length"
      class="screen-header__nav"
      :style="navStyle()"
      aria-label="主导航"
    >
      <button
        v-for="item in menus"
        :key="item.key"
        type="button"
        class="screen-header__nav-item stage-hit"
        :class="{ 'is-active': item.key === activeMenu }"
        :style="navItemStyle()"
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

    <!--
      实时时钟（高保真：大号时间 + 右侧星期 / 日期两行小字）
      ⚠ 星期/日期不能按设计稿写死 left:597 —— 设计稿用的 D-DIN-PRO 数字更窄，
        实际字体下 "15:42" 比设计稿预留的 104px 宽，写死 left 会和时间挤在一起。
        改成 flex：时间 + 「星期/日期」两行小字列，间距 16px（设计稿的 581→597）。
    -->
    <div v-if="showClock" class="screen-header__clock-wrap">
      <time class="screen-header__clock" :datetime="clockISO">{{ clockTime }}</time>
      <span class="screen-header__clock-meta">
        <span v-if="showWeekday" class="screen-header__weekday">{{ weekText }}</span>
        <span class="screen-header__date">{{ clockDate }}</span>
      </span>
    </div>

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

/**
 * 高保真定尺参数（设计稿 1920×1080 坐标）。
 *   navLeft       导航区左边界：标题切图止于 x=633，留 27px 呼吸
 *   navRightMargin 导航区右边界到设计画布右边界的距离（设计稿最后一项止于 1780）
 *   userRightMargin 用户区右边界到设计画布右边界的距离（设计稿收尾于 1890）
 * 导航用「弹性收缩的 flex 行」实现：设计画布比 1920 窄时，
 * 各项等比收窄而不是跟标题切图重叠。
 */
export const HEADER_LAYOUT = {
  height: 128,
  navTop: 6,
  navItemWidth: 140,
  navItemHeight: 54,
  navLeft: 660,
  navRightMargin: 140,
  userRightMargin: 30,
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
    /**
     * 导航行样式：左右边界固定，行内各项用 flex 平分剩余空间。
     * 设计画布 ≥1920 时 7 项总宽 980 < 可用宽，右对齐到设计位置；
     * 更窄时等比收缩，绝不会压到标题切图上。
     */
    navStyle () {
      const { navTop, navLeft, navRightMargin, navItemHeight } = HEADER_LAYOUT
      return {
        left: `${navLeft}px`,
        right: `${navRightMargin}px`,
        top: `${navTop}px`,
        height: `${navItemHeight}px`,
      }
    },
    /** 单个导航项：定宽 140，空间不足时按 flex-shrink 等比收窄 */
    navItemStyle () {
      const { navItemWidth, navItemHeight } = HEADER_LAYOUT
      return {
        width: `${navItemWidth}px`,
        flex: `0 1 ${navItemWidth}px`,
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
  // 宽度跟随设计画布（自适应），不能写死 1920
  width: 100%;
  height: 128px;
  // 顶部装饰线会略微溢出，允许显示
  overflow: visible;

  &__bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 128px;
    display: block;
    // 底图横向跟随拉伸（装饰线轻微变形可接受，换来的是一直铺满）
    object-fit: fill;
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
    display: flex;
    align-items: center;
    justify-content: flex-end;
    // 左右边界由 navStyle() 给出，见 HEADER_LAYOUT
  }

  &__nav-item {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 0;
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
    // 宽度跟随按钮（窄屏时按钮会等比收窄，切图一起缩）
    width: 100%;
    height: 100%;
    display: block;
    pointer-events: none;
  }

  &__nav-text {
    position: relative;
    z-index: 1;
    padding-top: 2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100%;
  }

  /* ---------- 用户区 ---------- */
  /* 靠右定位：设计稿里头像起于 x=1790、下拉箭头收尾于 1890，即距右边界 30px */
  &__user {
    position: absolute;
    right: 30px;
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
  /* 设计稿：时间左上角 (477, 115)，星期 (597, 117)，日期 (597, 141) */
  &__clock-wrap {
    position: absolute;
    left: 477px;
    top: 115px;
    display: flex;
    align-items: flex-start;
    white-space: nowrap;
  }

  &__clock {
    /*
     * 高保真用 D-DIN-PRO（窄体数字），52px 的 "15:36" 只有约 104px 宽；
     * 项目内置的思源黑体数字偏宽（同样字号约 205px），会把右侧的星期/日期
     * 顶出去近百像素。这里给时钟单独指定窄体数字字体栈：
     * DIN Alternate（macOS）→ Bahnschrift（Windows 自带的 DIN 风格可变字体）
     * → Arial Narrow → 最后才回退到项目字体。只影响时钟，不动全局数字字体。
     */
    font-family: 'DIN Alternate', 'Bahnschrift', 'Arial Narrow', var(--screen-font-number-family);
    // Bahnschrift 是可变字体，默认落在 Normal 字宽（并不窄），要显式取窄体实例
    font-stretch: 75%;
    font-size: 52px;
    font-weight: 400;
    line-height: 39px;
    color: #ffffff;
    letter-spacing: 0;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  /* 星期 / 日期两行小字：跟在时间右侧，间距固定，不依赖时间的实际宽度 */
  &__clock-meta {
    display: flex;
    flex-direction: column;
    margin-left: 16px;
    // 设计稿里星期比时间顶端低 2px（115 → 117）
    padding-top: 2px;
  }

  &__weekday {
    font-size: 16px;
    line-height: 16px;
    color: #66afd4;
    white-space: nowrap;
  }

  &__date {
    // 设计稿：星期 117..133，日期 141 起，间隔 8px
    margin-top: 8px;
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
