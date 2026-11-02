<template>
  <!--
    ScreenHeader 大屏顶栏（高保真还原）
    ==================================================================
    完全按高保真模型 docs/高保真/.../首页.html 的定尺布局实现：
      底图      bg-top_u6.png           (0, 0)    1920×128（横向跟随拉伸）
      系统标题  大标题切图 title.png      (34, 18)   599×35
      右侧工具区 一级导航 + 实时时钟 + 用户区一行 flex（y=6，高 60），
                 右对齐到设计右边界 -30，靠左止于 x=660（标题切图止于 633）
        ├─ 导航   nav-*.png            宽 128 高 60，紧随其后是 ↓
        ├─ 时钟   15:36 + 星期/日期两行（原先在地图留白区 (477,115)）
        └─ 用户区 头像/用户名/下拉箭头（点击展开下拉：退出登录）

    ⚠ 自适应：顶栏宽度随设计画布宽度变化（见 ScreenStage），
      所以工具区一律用 left/right 定边界、底图用 width:100% 拉伸，
      不能写 1920 的固定值。

    ⚠ 导航项数量与宽度：高保真是 6 项（140 宽 / 150 间距）。本系统一级菜单为
      7 项（新增「档案管理」「提级论证管理」，「收发文」是档案管理模块内的
      页签、不单独占一级菜单）。**2026-10-27 起时钟从设计稿的 (477,115)
      （地图留白区）挪进这一行、紧贴最后一项「查询统计」的右侧**，
      于是导航项宽度再收一档 150 → 128（7 项共 896）给时钟（约 175）让位：
      1920 画布下 896 + 时钟约 175 + 用户区约 105 + 两个 16px 间距 ≈ 1208，
      仍在「660 → 1890」这 1230px 可用宽内留有余量；画布更宽时余量更大。
  -->
  <header class="screen-header">
    <!-- 底图：整条顶栏的深蓝渐变 + 装饰线 -->
    <img class="screen-header__bg" :src="hf.headerBg" alt="" aria-hidden="true" />

    <!-- 系统标题（切图，含左侧徽标与下方蓝色装饰线） -->
    <img class="screen-header__title" :src="hf.title" :alt="title" />

    <!-- ===== 右侧工具区：一级导航 + 实时时钟 + 用户区（单行 flex，右对齐） ===== -->
    <div class="screen-header__bar" :style="barStyle()">
      <!-- 一级导航 -->
      <nav
        v-if="menus && menus.length"
        class="screen-header__nav"
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

      <!--
        实时时钟（大号时间 + 右侧星期 / 日期两行小字）
        ----------------------------------------------------------------
        ★ 2026-10-27：原先按设计稿贴在 (477, 115) 的地图留白区，现挪进导航行，
          紧跟最后一项「查询统计」的右侧（用户要求），所以：
          · 尺寸从 56px 收到 30px（导航行只有 60 高，56px 会顶出上下沿）；
          · 定位从 absolute 改成 flex 项，不再有 left/top 坐标；
          · 时间与星期/日期之间加一条竖向分隔线，和左侧导航区分开。
          星期/日期不写死 left —— 时间实际宽度随字体变，两列用 flex 相邻排版。
      -->
      <div v-if="showClock" class="screen-header__clock-wrap">
        <span class="screen-header__clock-divider" aria-hidden="true" />
        <time class="screen-header__clock" :datetime="clockISO">{{ clockTime }}</time>
        <span class="screen-header__clock-meta">
          <span v-if="showWeekday" class="screen-header__weekday">{{ weekText }}</span>
          <span class="screen-header__date">{{ clockDate }}</span>
        </span>
      </div>

      <!--
        用户区（设计稿：头像 + 用户名 + 下拉箭头）
        ----------------------------------------------------------------
        点击展开下拉，菜单里是账户操作（当前只有「退出登录」）。
        ⚠ 之前这里只有头像和箭头、**没有实现下拉**：箭头看着能点，点下去却什么都
          不发生，用户因此没法自己退出登录。退出动作不在这里做，通过 @logout
          抛给页面处理（组件保持纯展示，不依赖 store / 路由）。
        浮层用 ScreenPopover（挂 body），避免被顶栏或画布的 overflow 裁掉。
      -->
      <div
        ref="user"
        class="screen-header__user stage-hit"
        :class="{ 'is-open': userMenuOpen }"
        role="button"
        tabindex="0"
        :aria-expanded="userMenuOpen ? 'true' : 'false'"
        aria-haspopup="menu"
        :aria-label="userMenuLabel"
        @click="toggleUserMenu"
        @keydown="handleUserKeydown"
      >
        <img class="screen-header__avatar" :src="hf.avatar" alt="" aria-hidden="true" />
        <span class="screen-header__user-name">{{ userName }}</span>
        <img class="screen-header__caret" :src="hf.caretDown" alt="" aria-hidden="true" />
      </div>
    </div>

    <screen-popover
      :open="userMenuOpen"
      :anchor="$refs.user"
      placement="bottom-end"
      :width="148"
      :match-width="false"
      :max-height="null"
      role="menu"
      @close="userMenuOpen = false"
    >
      <button
        type="button"
        class="screen-header__user-menu-item"
        role="menuitem"
        @click="handleLogout"
      >
        <screen-icon name="logout" :size="14" />
        <span>{{ logoutText }}</span>
      </button>
    </screen-popover>

    <!--
      在线状态（高保真未出现，保留能力，默认不渲染）
    -->
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
import ScreenIcon from './ScreenIcon'
import ScreenPopover from './ScreenPopover'

const WEEK = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

/**
 * 高保真定尺参数（设计稿 1920×1080 坐标）。
 *   barTop / barHeight 工具区竖向位置：与设计稿导航项同一行（y=6、高 60）
 *   barLeft           工具区左边界：标题切图止于 x=633，留 27px 呼吸
 *   barRightMargin    工具区右边界到设计画布右边界的距离（设计稿用户区收尾于 1890）
 *   barGap            导航 / 时钟 / 用户区三块之间的间距
 * 工具区（导航 + 时钟 + 用户区）是**一行 flex、右对齐**：
 * 画布比 1920 窄时按 flex-shrink 收窄导航项（flex: 0 1 navItemWidth），
 * 而不是跟标题切图重叠；时钟与用户区不收缩。
 *
 * ⚠ 尺寸已随全局字号上移一档：导航项 54 → 60 高。
 *   宽度 140 → 150 是「导航行只有 7 项、没有时钟」时代的取值；
 *   2026-10-27 时钟（约 177px）挪进这一行后收到 128（7 项共 896），
 *   才塞得进 660 → 1890 这 1230px。17px 导航文字最长 6 字 ≈ 102px，
 *   128 宽左右各留约 13px —— 背景切图是满宽的柔光底纹，收窄不会露边。
 */
export const HEADER_LAYOUT = {
  height: 128,
  barTop: 6,
  barHeight: 60,
  barLeft: 660,
  barRightMargin: 30,
  barGap: 16,
  navItemWidth: 128,
}

export default {
  name: 'ScreenHeader',
  components: { ScreenIcon, ScreenPopover },
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
    /** 用户区下拉里「退出登录」的文案 */
    logoutText: { type: String, default: '退出登录' },
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
      /** 用户区下拉是否展开 */
      userMenuOpen: false,
    }
  },
  computed: {
    userMenuLabel () {
      return this.userName ? `${this.userName}，点击展开账户菜单` : '点击展开账户菜单'
    },
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
     * 工具区样式：左右边界 + 竖向位置 + 块间距固定，行内元素用 flex 右对齐。
     * 设计画布 ≥1920（ScreenStage 保证不会更窄）时，7 项导航 896 + 时钟约 175
     * + 用户区约 105 + 两个 16px 间距 ≈ 1208 < 可用宽 1230，右对齐后正好落在
     * 设计位置；万一画布更窄，收窄的是导航项（flex-shrink），不会压到标题切图。
     */
    barStyle () {
      const { barTop, barLeft, barRightMargin, barHeight, barGap } = HEADER_LAYOUT
      return {
        left: `${barLeft}px`,
        right: `${barRightMargin}px`,
        top: `${barTop}px`,
        height: `${barHeight}px`,
        gap: `${barGap}px`,
      }
    },
    /** 单个导航项：定宽 128，空间不足时按 flex-shrink 等比收窄 */
    navItemStyle () {
      const { navItemWidth, barHeight } = HEADER_LAYOUT
      return {
        width: `${navItemWidth}px`,
        flex: `0 1 ${navItemWidth}px`,
        height: `${barHeight}px`,
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
    toggleUserMenu () {
      this.userMenuOpen = !this.userMenuOpen
    },
    /** 触发器是 role="button"，键盘 Enter / Space 也要能展开 */
    handleUserKeydown (event) {
      if (event.key !== 'Enter' && event.key !== ' ' && event.key !== 'Spacebar') return
      event.preventDefault()
      this.toggleUserMenu()
    },
    handleLogout () {
      this.userMenuOpen = false
      // 退出动作由页面处理（组件本身不认识 store / 路由，保持纯展示）
      this.$emit('logout')
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

  /* ---------- 右侧工具区（导航 + 时钟 + 用户区） ---------- */
  /*
    一行 flex、右对齐：右边界到画布 30px（设计稿用户区收尾于 1890），
    左边界 660（标题切图止于 633）。位置与间距由 barStyle() 给出，
    见 HEADER_LAYOUT。容器本身不开 pointer-events（画布整体穿透，
    只有 .stage-hit 的子元素接事件），顶栏空白处仍旧可以拖动地图。
  */
  &__bar {
    position: absolute;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    min-width: 0;
  }

  &__nav {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    // 空间不足时只收窄导航，不挤压时钟 / 用户区
    flex: 0 1 auto;
    min-width: 0;
  }

  &__nav-item {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 0;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-md);
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
  /* 工具区最右一格：设计稿里头像起于 x=1790、下拉箭头收尾于 1890（距右边界 30px，
     由 barStyle() 的 right 保证）。这里只负责行内对齐，不要再写 right/top。 */
  &__user {
    position: relative;
    display: flex;
    align-items: center;
    flex: 0 0 auto;
    height: 32px;
    cursor: pointer;
    border-radius: var(--screen-radius-sm);
    .screen-focus-ring();

    /* 展开时给一点反馈，避免点下去「没反应」的观感 */
    &.is-open {
      .screen-header__caret {
        transform: rotate(180deg);
      }
    }

    &:hover .screen-header__user-name,
    &.is-open .screen-header__user-name {
      color: #ffffff;
    }
  }

  &__avatar {
    width: 32px;
    height: 32px;
    display: block;
  }

  &__user-name {
    margin-left: 10px;
    // 名字过长时省略，避免把左侧导航挤窄（导航是 flex-shrink 的唯一受害者）
    max-width: 140px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--screen-font-sm);
    font-weight: 500;
    line-height: 1.2;
    color: #b8d5ea;
    white-space: nowrap;
    transition: color var(--screen-duration) var(--screen-ease);
  }

  &__caret {
    margin-left: 12px;
    width: 8px;
    height: 5px;
    display: block;
    transition: transform var(--screen-duration) var(--screen-ease);
  }

  /* 用户区下拉里的条目（浮层内容在 ScreenPopover 中，样式随 scoped 生效） */
  &__user-menu-item {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 8px 10px;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    line-height: 1.4;
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
      color: #ffffff;
      background: var(--screen-elevate);
    }
  }

  /* ---------- 时钟（导航行内，紧跟「查询统计」右侧） ---------- */
  /*
    设计稿里时钟贴在地图留白区 (477, 115)：时间 52px、星期/日期各一行 17px。
    2026-10-27 挪进导航行（行高只有 60）后按行高重排：
      · 尺寸收到 30px（约等于导航文字的 1.75 倍，仍明显是「大号时间」）；
      · 从 absolute 改成 flex 项，竖向和导航项 / 用户区居中对齐（中心都是 y=36）；
      · 左侧加一条竖向分隔线，与导航区分开；时钟不参与收缩（flex: 0 0 auto）。
  */
  &__clock-wrap {
    display: flex;
    align-items: center;
    flex: 0 0 auto;
    // 分隔线 → 时间 → 星期/日期（与 bar 的 16px 块间距叠加成 16 + 12）
    gap: 12px;
    white-space: nowrap;
  }

  &__clock-divider {
    width: 1px;
    height: 26px;
    background: linear-gradient(
      180deg,
      rgba(102, 175, 212, 0) 0%,
      rgba(102, 175, 212, 0.6) 50%,
      rgba(102, 175, 212, 0) 100%
    );
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
    font-size: 30px;
    font-weight: 400;
    line-height: 1;
    color: #ffffff;
    letter-spacing: 0;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  /* 星期 / 日期两行小字：跟在时间右侧，间距固定，不依赖时间的实际宽度 */
  &__clock-meta {
    display: flex;
    flex-direction: column;
    // 两行整体在 60 高的行内居中（星期在上、日期在下）
    justify-content: center;
    margin-left: 4px;
  }

  &__weekday {
    font-size: var(--screen-font-xs);
    line-height: 1.2;
    color: #66afd4;
    white-space: nowrap;
  }

  &__date {
    // 日期与星期之间留 3px，两行合计 39px，仍在 60 高的行内居中
    margin-top: 3px;
    font-size: var(--screen-font-xs);
    line-height: 1.2;
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
