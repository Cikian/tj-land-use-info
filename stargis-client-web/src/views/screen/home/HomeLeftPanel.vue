<template>
  <!--
    HomeLeftPanel 首页左面板（高保真还原）
    ==================================================================
    定尺取自高保真模型 首页.html：
      标题装饰 大标题-左_u21.png   (9, 95)    420×46
      标题文字                     (83, 104)  18px #FFFFFF
      面板底     bg-模块-左_u23.png (30, 149)  400×881
      页签       2 个，轨道 160×38 / 选中胶囊 150×28，起始 (65,159) 步进 175
      内容区                       (50, 209)  360×785
        ├─ 统计卡  stat-card.png    360×120  + 3D 立方体切图 131×118
        ├─ 双卡    市级 / 区级       175×80，同时作为下方明细表的切换按钮
        └─ 明细表  table-head/body/row.png，表头 36 + 11 行 × 46

    对高保真的两处「按高保真 + 保留现有内容」扩展（已与需求方确认）：
      1. 明细区右上角增加「明细 / 排行」切换：默认「明细」与设计稿完全一致，
         「排行」展示原有「各区出让排行 / 各区配套落实」，不丢失既有内容。
      2. 数字全部来自接口（不再是设计稿里的 909 / 245 / 664 占位值）。
         圆环的弧长也改成按数据现画（内联 SVG），不再用设计稿里 28% / 72% 的定值切图。
  -->
  <div class="home-left">
    <!-- 面板标题 -->
    <img class="home-left__title-deco" :src="hf.panelTitleLeft" alt="" aria-hidden="true" />
    <h2 class="home-left__title">出让地块情况统计</h2>

    <!-- 面板主体 -->
    <section class="home-left__panel stage-hit">
      <!-- 页签 -->
      <div class="home-left__tabs" role="tablist">
        <button
          v-for="(tab, index) in panel.tabs"
          :key="tab.key"
          type="button"
          role="tab"
          class="home-left__tab"
          :class="{ 'is-active': tab.key === activeTab }"
          :style="tabStyle(index)"
          :aria-selected="String(tab.key === activeTab)"
          @click="activeTab = tab.key"
        >
          <img class="home-left__tab-track" :src="hf.tabTrack" alt="" aria-hidden="true" />
          <img
            class="home-left__tab-pill"
            :src="tab.key === activeTab ? hf.tabActive : hf.tabIdle"
            alt=""
            aria-hidden="true"
          />
          <span class="home-left__tab-text">{{ tab.label }}</span>
        </button>

        <!--
          「明细 / 排行」切换。
          高保真只有两个页签，没有这个控件；但需求方要求保留原有的
          「各区出让排行 / 各区配套落实」，因此做成一个紧凑的页签条右侧小按钮：
          默认「明细」与设计稿完全一致，点一下切到排行视图。
          页签步进由设计稿的 175 收到 150，为它腾出 55px 空间（左侧 2 个页签仅右移 -25px）。
        -->
        <button
          type="button"
          class="home-left__view-switch"
          :aria-pressed="String(viewMode === 'rank')"
          :title="viewMode === 'table' ? '切换到排行视图' : '切换到明细视图'"
          @click="viewMode = viewMode === 'table' ? 'rank' : 'table'"
        >
          {{ viewMode === 'table' ? '排行' : '明细' }}
        </button>
      </div>

      <!-- 内容区 -->
      <div class="home-left__body">
        <!-- 统计卡 -->
        <div class="home-left__stat">
          <img class="home-left__stat-bg" :src="hf.statCard" alt="" aria-hidden="true" />
          <img
            class="home-left__stat-cube"
            :src="current.cube === 'b' ? hf.statCubeB : hf.statCubeA"
            alt=""
            aria-hidden="true"
          />
          <span class="home-left__stat-label">{{ current.stat.label }}</span>
          <span class="home-left__stat-figure">
            <span class="home-left__stat-value">{{ current.stat.value }}</span>
            <span class="home-left__stat-unit">{{ current.stat.unit }}</span>
          </span>
        </div>

        <!-- 市级 / 区级 双卡（兼作明细切换） -->
        <div class="home-left__split">
          <button
            v-for="(item, index) in current.split"
            :key="item.key"
            type="button"
            class="home-left__split-card"
            :class="{ 'is-active': item.key === activeSplit }"
            :style="{ left: `${index * 185}px` }"
            @click="activeSplit = item.key"
          >
            <img
              class="home-left__split-bg"
              :src="item.key === activeSplit ? hf.splitCardActive : hf.splitCard"
              alt=""
              aria-hidden="true"
            />
            <img
              class="home-left__ring-track"
              :src="hf.ringTrack"
              alt=""
              aria-hidden="true"
            />
            <!--
              进度弧用内联 SVG 现画，不再用设计稿那 4 张「定值弧」切图。
              ★ 切图的问题（就是圆环看起来「乱」的原因）：
                1) 弧长写死（市级 28%、区级 72%），数据一变弧长就对不上；
                2) 四张图的画布尺寸各不相同（76×76 / 50×76），
                   塞进 56×56 / 30×56 的盒子里会被裁切、横向压扁，
                   区级那张还会变成一条竖着的椭圆弧。
              这里统一按 56×56 画布、r=26.25、线宽 3.1 重画，
              几何与 ring-track.svg 的圆环完全重合；弧长由 percent 决定，
              起止角从 12 点开始顺时针（与旧系统 canvas 的 1.5π 起笔一致）。
            -->
            <svg class="home-left__ring-arc" viewBox="0 0 56 56" aria-hidden="true">
              <!-- percent=0 时不画：圆头线帽会把零长度弧画成一个点 -->
              <circle
                v-if="ringDash(item) > 0"
                class="home-left__ring-arc-line"
                cx="28"
                cy="28"
                r="26.25"
                :stroke="ringColor(item)"
                :style="{ filter: `drop-shadow(0 0 3px ${ringColor(item)})` }"
                :stroke-dasharray="`${ringDash(item)} 999`"
                transform="rotate(-90 28 28)"
              />
            </svg>
            <!--
              高保真里圆环文字是 14px、只显示整数百分比（28%），
              精确到 2 位的占比放进 title，避免 6 个字符把 56px 的圆环撑破。
            -->
            <span class="home-left__ring-text" :title="ringTitle(item)">{{ ringPercent(item) }}%</span>
            <span class="home-left__split-label">{{ item.label }}</span>
            <span class="home-left__split-figure">
              <span class="home-left__split-value">{{ item.value }}</span>
              <span class="home-left__split-unit">{{ item.unit }}</span>
            </span>
          </button>
        </div>

        <!-- 明细 / 排行 -->
        <div class="home-left__detail">
          <div class="home-left__detail-head">
            <img class="home-left__detail-head-bg" :src="hf.tableHead" alt="" aria-hidden="true" />
            <template v-if="viewMode === 'table'">
              <span
                v-for="col in current.columns"
                :key="col.key"
                class="home-left__th"
                :style="{ left: `${TH_OFFSET[col.key]}px` }"
              >{{ col.title }}</span>
            </template>
            <span v-else class="home-left__th home-left__th--rank">
              {{ rankTitle }}
            </span>
          </div>

          <!-- 明细表 -->
          <div v-if="viewMode === 'table'" class="home-left__table">
            <img class="home-left__table-bg" :src="hf.tableBody" alt="" aria-hidden="true" />
            <div
              v-for="(row, index) in tableRows"
              :key="index"
              class="home-left__tr"
              :class="{ 'is-alt': index % 2 === 0, 'is-total': row.isTotal }"
            >
              <img
                v-if="index % 2 === 0"
                class="home-left__tr-bg"
                :src="hf.tableRow"
                alt=""
                aria-hidden="true"
              />
              <!--
                单元格按 current.columns 通用渲染：
                列与取值字段由页面（views/screen/index.vue）按页签下发，
                这里不再写死 plots / roads —— 否则「落实配套」页签会把
                已完成配套数显示到「涉及道路」列下。
              -->
              <span
                v-for="col in current.columns"
                :key="col.key"
                class="home-left__td"
                :style="{ left: `${TH_OFFSET[col.key]}px` }"
              >{{ row[col.key] }}</span>
            </div>

            <!-- 后端暂无该维度明细时明确留白，不用其它口径的数据顶替 -->
            <p v-if="!table.rows.length" class="home-left__empty">暂无数据</p>
          </div>

          <!-- 排行（保留原有内容） -->
          <div v-else class="home-left__rank">
            <img class="home-left__table-bg" :src="hf.tableBody" alt="" aria-hidden="true" />
            <div
              v-for="(item, index) in rankRows"
              :key="item.name"
              class="home-left__rank-row"
            >
              <img
                v-if="index % 2 === 0"
                class="home-left__tr-bg"
                :src="hf.tableRow"
                alt=""
                aria-hidden="true"
              />
              <span class="home-left__rank-no">{{ pad(index + 1) }}</span>
              <span class="home-left__rank-name">{{ item.name }}</span>
              <span class="home-left__rank-bar">
                <i :style="{ width: `${barWidth(item.value)}%` }" />
              </span>
              <span class="home-left__rank-value">{{ item.value }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'

/**
 * 表头 / 单元格的横向偏移（取自高保真 CSS 的 left 值）。
 * ★ facilities / completed 沿用 plots / roads 的 186 / 283：
 *   「落实配套」页签只是换列名与取值字段，设计稿的横向定尺不变，
 *   所以不新增一套坐标，避免两个页签的列宽视觉漂移。
 */
const TH_OFFSET = {
  index: 14,
  name: 52,
  plots: 186,
  roads: 283,
  facilities: 186,
  completed: 283
}

const padIndex = (n) => (n < 10 ? `0${n}` : `${n}`)

export default {
  name: 'HomeLeftPanel',
  props: {
    /**
     * 面板数据（出让情况 / 落实配套 两个页签）。
     * 只使用接口返回的数据，没有数据时为空。
     */
    panel: { type: Object, default: () => ({}) },
    /** 各区出让排行 / 各区配套落实（切换到「排行」视图时展示） */
    transferRank: { type: Array, default: () => [] },
    supportingRank: { type: Array, default: () => [] },
    /** 接口加载态 */
    loading: { type: Boolean, default: false }
  },
  data () {
    return {
      hf,
      TH_OFFSET,
      activeTab: (this.panel.tabs && this.panel.tabs[0] && this.panel.tabs[0].key) || 'transfer',
      /** 与 split 双卡一一对应：city = 市级，district = 区级 */
      activeSplit: 'city',
      viewMode: 'table'
    }
  },
  computed: {
    current () {
      return this.panel[this.activeTab] || this.panel.transfer || {
        stat: { label: '', value: 0, unit: '宗' },
        split: [],
        columns: [],
        tables: { city: { rows: [], total: null }, district: { rows: [], total: null } }
      }
    },
    table () {
      const tables = this.current.tables || {}
      return tables[this.activeSplit] || tables.city || { rows: [], total: null }
    },
    /** 明细表行（补上序号，并把合计行单独标记） */
    tableRows () {
      const rows = this.table.rows.map((row, index) => ({ ...row, index: index + 1 }))
      if (this.table.total) rows.push({ ...this.table.total, index: '', isTotal: true })
      return rows
    },
    rankRows () {
      // 出让情况 → 各区出让排行；落实配套 → 各区配套落实
      return this.activeTab === 'transfer' ? this.transferRank : this.supportingRank
    },
    rankTitle () {
      return this.activeTab === 'transfer' ? '各区出让排行' : '各区配套落实'
    },
    rankMax () {
      return this.rankRows.reduce((max, item) => Math.max(max, item.value), 1)
    }
  },
  watch: {
    activeTab () {
      // 切页签时把「市级 / 区级」重置回该页签的第一张卡，避免残留上一个页签的选择
      this.activeSplit = (this.current.split[0] && this.current.split[0].key) || 'city'
    }
  },
  methods: {
    pad: padIndex,
    tabStyle (index) {
      // 设计稿步进 175；收到 150 是为了在页签条右端放「明细 / 排行」切换
      return { left: `${35 + index * 150}px` }
    },
    /**
     * 环形图进度色：出让情况绿/青，落实配套红/金。
     * 四个色值都取自高保真切图（#43e972 / #54c9c3 / #e95b43 / #c99e54）——
     * 切图弃用后颜色直接写在这里，保证观感与设计稿一致。
     */
    ringColor (item) {
      const palette = this.current.cube === 'b'
        ? { city: '#e95b43', district: '#c99e54' }
        : { city: '#43e972', district: '#54c9c3' }
      return palette[item.key] || palette.city
    },
    /**
     * 进度弧长（px）。圆周长按 r=26.25 计算，percent 是精确百分比（如 25.38）。
     * 0% → "0 999" 不画；100% → 正好一整圈。
     */
    ringDash (item) {
      const circumference = 2 * Math.PI * 26.25
      const ratio = Math.min(Math.max(Number(item.percent || 0) / 100, 0), 1)
      return Number((circumference * ratio).toFixed(2))
    },
    /** 圆环文字：高保真只显示整数百分比（28%），精确值放 title */
    ringPercent (item) {
      return Math.round(Number(item.percent || 0))
    },
    ringTitle (item) {
      return `占比 ${Number(item.percent || 0).toFixed(2)}%`
    },
    barWidth (value) {
      return Math.max(4, Math.round((value / this.rankMax) * 100))
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.home-left {
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
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__panel {
    position: absolute;
    left: 30px;
    // 上贴 149、下贴 50：正文区随设计画布高度伸展。
    // 设计比例（1080 高）下高度正好是 881，与设计稿一致；
    // 比 16:9 窄的窗口画布会更高，面板跟着长高，底部不会空出一片。
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-left-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;
  }

  /* ---------------- 页签 ---------------- */
  &__tab {
    position: absolute;
    top: 10px;
    /* 设计稿是 160；收到 150 是为了让第二个页签不压住右端「明细 / 排行」按钮 */
    width: 150px;
    height: 38px;
    padding: 0;
    font-family: inherit;
    /* 设计稿 u29/u32：页签文字 14px、行高 28（与 150×28 的胶囊等高） */
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
    width: 150px;
    height: 38px;
  }

  &__tab-pill {
    left: 5px;
    top: 5px;
    width: 140px;
    height: 28px;
  }

  &__tab-text {
    position: relative;
    z-index: 1;
    line-height: 28px;
  }

  /* ---------------- 内容区 ---------------- */
  &__body {
    position: absolute;
    left: 20px;
    top: 60px;
    right: 20px;
    // 设计稿 881 - 60 - 785 = 36
    bottom: 36px;
  }

  &__stat {
    position: absolute;
    left: 0;
    top: 3px;
    width: 100%;
    height: 120px;
  }

  &__stat-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 120px;
    display: block;
  }

  &__stat-cube {
    position: absolute;
    left: 12px;
    top: 0;
    width: 131px;
    height: 118px;
    display: block;
  }

  &__stat-label {
    position: absolute;
    left: 156px;
    top: 29px;
    font-size: 16px;
    font-weight: 500;
    line-height: 16px;
    color: #ffffff;
    white-space: nowrap;
  }

  /*
   * 数值 + 单位排成一行。
   * ⚠ 不能用「各自绝对定位」：数值位数一变（2 位 / 3 位 / 4 位）单位就会压上去。
   *   用 flex 让单位始终跟在数值右侧，数值再长也只是往右推。
   *   字号/行高取设计稿原值（u37 = 28px / 21px、u38 = 16px），
   *   之前放大到 32px 是「看起来更醒目」，但会把统计卡的文字压到卡片下沿。
   */
  &__stat-figure {
    position: absolute;
    left: 156px;
    right: 16px;
    top: 59px;
    display: flex;
    align-items: baseline;
    gap: 4px;
    min-width: 0;
  }

  &__stat-value {
    font-family: var(--screen-font-number-family);
    font-size: 28px;
    font-weight: 500;
    line-height: 21px;
    color: #ffe597;
    font-variant-numeric: tabular-nums;
  }

  &__stat-unit {
    font-size: 16px;
    line-height: 1;
    color: #66afd4;
  }

  /* ---------------- 市级 / 区级 ---------------- */
  &__split {
    position: absolute;
    left: 0;
    top: 133px;
    width: 100%;
    height: 80px;
  }

  &__split-card {
    position: absolute;
    top: 0;
    width: 175px;
    height: 80px;
    padding: 0;
    background: transparent;
    border: 0;
    cursor: pointer;
    .screen-focus-ring();
  }

  &__split-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 175px;
    height: 80px;
    display: block;
  }

  &__ring-track,
  &__ring-arc {
    position: absolute;
    left: 20px;
    top: 10px;
    width: 56px;
    height: 56px;
    display: block;
    pointer-events: none;
  }

  /* 进度弧：几何与 ring-track.svg 重合（r=26.25 / 线宽 3.1），弧长由数据决定 */
  &__ring-arc-line {
    fill: none;
    stroke-width: 3.1;
    stroke-linecap: round;
  }

  /*
   * 圆环文字：设计稿 u46 是 left 36 / top 32 / 27×11 / 14px。
   * 这里用「left 20 + width 56 + 居中」代替：圆心正好 48，
   * 位数变化（9% / 100%）时也不会偏离圆心；top 31 + 行高 14 让文字在圆心垂直居中。
   */
  &__ring-text {
    position: absolute;
    left: 20px;
    top: 31px;
    width: 56px;
    height: 14px;
    font-family: var(--screen-font-number-family);
    font-size: 14px;
    font-weight: 500;
    line-height: 14px;
    color: #ffffff;
    text-align: center;
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
  }

  &__split-label {
    position: absolute;
    left: 95px;
    top: 19px;
    font-size: 14px;
    line-height: 14px;
    color: #66afd4;
    white-space: nowrap;
  }

  /* 数值 + 单位同样排成一行，避免位数变化时单位被压住 */
  &__split-figure {
    position: absolute;
    left: 95px;
    right: 8px;
    top: 41px;
    display: flex;
    align-items: baseline;
    gap: 4px;
    min-width: 0;
  }

  &__split-value {
    font-family: var(--screen-font-number-family);
    font-size: 20px;
    font-weight: 500;
    line-height: 15px;
    color: #ffffff;
    font-variant-numeric: tabular-nums;
  }

  &__split-unit {
    font-size: 12px;
    line-height: 1;
    color: #66afd4;
  }

  /* ---------------- 明细 / 排行 ---------------- */
  &__detail {
    position: absolute;
    left: 0;
    top: 233px;
    right: 0;
    // 设计稿 785 - 233 = 552，高度随面板一起伸展
    bottom: 0;
  }

  &__detail-head {
    position: absolute;
    left: 1px;
    right: 0;
    top: 0;
    height: 36px;
  }

  &__detail-head-bg {
    position: absolute;
    left: 0;
    top: 0;
    // 设计稿里表头切图 357 宽，占 359 的表头区、右边留 2px。
    // ⚠ <img> 必须显式给宽度，只写 left/right 不会拉伸
    width: calc(100% - 2px);
    height: 36px;
    display: block;
  }

  &__th {
    position: absolute;
    top: 11px;
    /* 设计稿 u69~u72：14px / 行高 14 / top 11 */
    font-size: 14px;
    line-height: 14px;
    color: #ffffff;
    white-space: nowrap;

    &--rank {
      left: 14px;
    }
  }

  /* 「明细 / 排行」切换：高保真无此控件，放在页签条右端的空白处 */
  &__view-switch {
    position: absolute;
    left: 345px;
    top: 14px;
    width: 50px;
    height: 30px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    line-height: 28px;
    color: #82c6ff;
    background: rgba(85, 185, 255, 0.12);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: #ffffff;
      background: rgba(85, 185, 255, 0.24);
    }

    &[aria-pressed='true'] {
      color: #ffffff;
      background: rgba(85, 185, 255, 0.28);
      border-color: var(--screen-border-strong);
    }
  }

  &__table,
  &__rank {
    position: absolute;
    left: 0;
    right: 0;
    top: 46px;
    bottom: 0;
    overflow-x: hidden;
    overflow-y: auto;
  }

  &__table {
    .home-left__tr:last-child {
      position: sticky;
      bottom: 0;
      z-index: 1;
      background: rgba(5, 24, 48, 0.96);
    }
  }

  &__table-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    display: block;
    pointer-events: none;
  }

  /* 后端暂无该维度明细时的留白提示 */
  &__empty {
    position: absolute;
    left: 0;
    top: 120px;
    width: 100%;
    margin: 0;
    font-size: var(--screen-font-sm);
    color: #4a7396;
    text-align: center;
  }

  /*
   * 行高必须回到设计稿的 46px（u58/u59/u60 切图就是 359×46）。
   * 之前写 54px 是为了容纳放大的 17px 字号，结果把 46px 的行底切图
   * 纵向拉长了 17%，行与行之间的渐变和分隔线全部变形 —— 这是表格看起来「乱」的直接原因。
   */
  &__tr,
  &__rank-row {
    position: relative;
    width: 100%;
    height: 46px;
  }

  &__tr-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 46px;
    display: block;
    pointer-events: none;
  }

  &__td {
    position: absolute;
    /* 14px 文字在 46px 行内垂直居中：(46 - 14) / 2 = 16 */
    top: 16px;
    font-size: 14px;
    line-height: 14px;
    color: #66afd4;
    white-space: nowrap;
  }

  &__tr.is-total &__td {
    color: #ffffff;
    font-weight: 500;
  }

  /* 排行条 */
  &__rank-no {
    position: absolute;
    left: 14px;
    top: 16px;
    font-size: 14px;
    color: #4a7396;
    font-variant-numeric: tabular-nums;
  }

  &__rank-name {
    position: absolute;
    left: 52px;
    top: 16px;
    font-size: 14px;
    color: #66afd4;
    white-space: nowrap;
  }

  &__rank-bar {
    position: absolute;
    left: 148px;
    top: 18px;
    width: 130px;
    height: 9px;
    border-radius: var(--screen-radius-pill);
    background: var(--screen-bar-track);
    overflow: hidden;

    i {
      display: block;
      height: 100%;
      border-radius: var(--screen-radius-pill);
      background-image: linear-gradient(90deg, var(--screen-bar-from) 0%, var(--screen-bar-to) 100%);
    }
  }

  &__rank-value {
    position: absolute;
    right: 14px;
    top: 16px;
    font-family: var(--screen-font-number-family);
    font-size: 14px;
    color: #ffffff;
    font-variant-numeric: tabular-nums;
  }
}
</style>
