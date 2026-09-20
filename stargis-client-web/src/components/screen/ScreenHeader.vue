<template>
  <!--
    ScreenHeader 大屏顶栏
    --------------------------------
    左侧：品牌标 + 系统标题（青色辉光）
    中部：导航菜单（胶囊高亮当前项）
    右侧：实时时钟 · 用户 · 在线状态
  -->
  <header class="screen-header">
    <div class="screen-header__inner">
      <!-- 品牌 + 标题 -->
      <div class="screen-header__brand">
        <span class="screen-header__logo" aria-hidden="true">
          <img v-if="logo" :src="logo" alt="" />
          <svg v-else viewBox="0 0 32 32" width="26" height="26">
            <path
              d="M16 2.5l11 5.2v8.1c0 6.4-4.5 11.6-11 13.7-6.5-2.1-11-7.3-11-13.7V7.7L16 2.5z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linejoin="round"
            />
            <path
              d="M16 9.6c3.4 2.1 5.1 4.6 5.1 7.4 0 2.4-2 4.4-5.1 6.1-3.1-1.7-5.1-3.7-5.1-6.1 0-2.8 1.7-5.3 5.1-7.4z"
              fill="currentColor"
              opacity=".85"
            />
          </svg>
        </span>
        <h1 class="screen-header__title">{{ title }}</h1>
      </div>

      <!-- 导航 -->
      <nav v-if="menus && menus.length" class="screen-header__nav" aria-label="主导航">
        <button
          v-for="item in menus"
          :key="item.key"
          type="button"
          class="screen-header__nav-item"
          :class="{ 'is-active': item.key === activeMenu }"
          :aria-current="item.key === activeMenu ? 'page' : undefined"
          @click="handleMenu(item)"
        >
          {{ item.label }}
        </button>
      </nav>

      <!-- 右侧状态区 -->
      <div class="screen-header__meta">
        <time v-if="showClock" class="screen-header__clock" :datetime="clockISO">{{ clockText }}</time>

        <span class="screen-header__divider" aria-hidden="true" />

        <button type="button" class="screen-header__user" @click="$emit('user-click')">
          <span class="screen-header__avatar" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="16" height="16">
              <circle cx="12" cy="8.4" r="3.6" fill="currentColor" />
              <path d="M4.6 20c0-4 3.3-6.4 7.4-6.4s7.4 2.4 7.4 6.4" fill="currentColor" />
            </svg>
          </span>
          <span class="screen-header__user-name">{{ userName }}</span>
        </button>

        <span v-if="online !== null" class="screen-header__online" :class="{ 'is-off': !online }">
          <i class="screen-header__dot" aria-hidden="true" />
          {{ online ? onlineText : offlineText }}
        </span>
      </div>
    </div>

    <!-- 顶栏底部渐变辉光线 -->
    <span class="screen-header__glow" aria-hidden="true" />
  </header>
</template>

<script>
import { padIndex } from './utils'

const WEEK = ['日', '一', '二', '三', '四', '五', '六']

export default {
  name: 'ScreenHeader',
  props: {
    /** 系统标题 */
    title: { type: String, default: '' },
    /** 自定义 logo 图片地址，缺省使用内置 SVG 标识 */
    logo: { type: String, default: '' },
    /** 导航项：[{ key, label }] */
    menus: { type: Array, default: () => [] },
    /** 当前激活导航 key */
    activeMenu: { type: String, default: '' },
    /** 是否显示实时时钟 */
    showClock: { type: Boolean, default: true },
    /** 时钟格式：datetime 含日期 / time 仅时间 */
    clockFormat: { type: String, default: 'datetime' },
    /** 是否显示星期 */
    showWeekday: { type: Boolean, default: false },
    /** 用户名 */
    userName: { type: String, default: '' },
    /** 在线状态：true 在线 / false 离线 / null 不展示 */
    online: { type: Boolean, default: null },
    onlineText: { type: String, default: '在线' },
    offlineText: { type: String, default: '离线' },
  },
  data () {
    return {
      now: new Date(),
      timer: null,
    }
  },
  computed: {
    clockText () {
      const d = this.now
      const time = [padIndex(d.getHours()), padIndex(d.getMinutes()), padIndex(d.getSeconds())].join(':')
      if (this.clockFormat === 'time') return time
      const date = `${d.getFullYear()}-${padIndex(d.getMonth() + 1)}-${padIndex(d.getDate())}`
      return this.showWeekday ? `${date} 星期${WEEK[d.getDay()]} ${time}` : `${date} ${time}`
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
  position: relative;
  flex: 0 0 auto;
  height: 60px;
  // 顶栏本身不拦截地图事件，仅内部元素可点击
  pointer-events: none;

  &__inner {
    display: flex;
    align-items: center;
    height: 100%;
    padding: 0 var(--screen-space-5);
    gap: var(--screen-space-6);
    background: linear-gradient(180deg, rgba(2, 30, 34, 0.94) 0%, rgba(2, 22, 26, 0.72) 100%);
  }

  &__brand {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    flex: 0 0 auto;
    pointer-events: auto;
  }

  &__logo {
    display: inline-flex;
    color: var(--screen-accent);
    filter: drop-shadow(0 0 6px var(--screen-accent-glow));
  }

  &__title {
    margin: 0;
    font-size: 21px;
    font-weight: 700;
    line-height: 1;
    letter-spacing: 0.06em;
    white-space: nowrap;
    color: #eafffb;
    text-shadow: 0 0 12px rgba(47, 227, 192, 0.55), 0 0 3px rgba(47, 227, 192, 0.4);
  }

  &__nav {
    display: flex;
    align-items: center;
    gap: 2px;
    flex: 1 1 auto;
    min-width: 0;
    pointer-events: auto;
  }

  &__nav-item {
    flex: 0 0 auto;
    padding: 5px 14px;
    font-size: var(--screen-font-md);
    line-height: 1.4;
    color: var(--screen-text-sub);
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--screen-radius-pill);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-text);
      background: rgba(47, 227, 192, 0.08);
    }

    &.is-active {
      color: var(--screen-accent);
      background: rgba(47, 227, 192, 0.12);
      border-color: var(--screen-border-strong);
      text-shadow: 0 0 8px var(--screen-accent-glow);
    }
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: var(--screen-space-3);
    flex: 0 0 auto;
    margin-left: auto;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
    pointer-events: auto;
  }

  &__clock {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    letter-spacing: 0.04em;
    color: var(--screen-text);
  }

  &__divider {
    width: 1px;
    height: 16px;
    background: var(--screen-divider);
  }

  &__user {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 8px;
    color: var(--screen-text-sub);
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--screen-radius-pill);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent);
      border-color: var(--screen-border);
    }
  }

  &__avatar {
    display: inline-flex;
    color: var(--screen-accent-soft);
  }

  &__user-name {
    white-space: nowrap;
  }

  &__online {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 3px 10px;
    border-radius: var(--screen-radius-pill);
    border: 1px solid var(--screen-border-soft);
    background: rgba(34, 199, 149, 0.1);
    color: var(--screen-success);
    white-space: nowrap;

    &.is-off {
      background: rgba(143, 182, 184, 0.1);
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

  &__glow {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 1px;
    background: linear-gradient(
      90deg,
      rgba(47, 227, 192, 0) 0%,
      rgba(47, 227, 192, 0.55) 22%,
      rgba(47, 227, 192, 0.1) 55%,
      rgba(47, 227, 192, 0) 100%
    );
  }
}
</style>
