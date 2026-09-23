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
      2. 数据全部改用高保真设计稿中的数字（909 / 245 / 664 / 28% / 72% …）。
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
          <span class="home-left__stat-value">{{ current.stat.value }}</span>
          <span class="home-left__stat-unit">{{ current.stat.unit }}</span>
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
            <img
              class="home-left__ring-fill"
              :style="ringFillStyle(item)"
              :src="ringFill(item)"
              alt=""
              aria-hidden="true"
            />
            <span class="home-left__ring-text">{{ item.percent }}%</span>
            <span class="home-left__split-label">{{ item.label }}</span>
            <span class="home-left__split-value">{{ item.value }}</span>
            <span class="home-left__split-unit">{{ item.unit }}</span>
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
              <span class="home-left__td" :style="{ left: `${TH_OFFSET.index}px` }">{{ row.index }}</span>
              <span class="home-left__td" :style="{ left: `${TH_OFFSET.name}px` }">{{ row.name }}</span>
              <span class="home-left__td" :style="{ left: `${TH_OFFSET.plots}px` }">{{ row.plots }}</span>
              <span class="home-left__td" :style="{ left: `${TH_OFFSET.roads}px` }">{{ row.roads }}</span>
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

/** 表头 / 单元格的横向偏移（取自高保真 CSS 的 left 值） */
const TH_OFFSET = { index: 14, name: 52, plots: 186, roads: 283 }

const padIndex = (n) => (n < 10 ? `0${n}` : `${n}`)

export default {
  name: 'HomeLeftPanel',
  props: {
    /**
     * 面板数据（出让情况 / 落实配套情况 两个页签）。
     * 只使用接口返回的数据，没有数据时为空。
     */
    panel: { type: Object, default: () => ({}) },
    /** 各区出让排行 / 各区配套落实（切换到「排行」视图时展示） */
    transferRank: { type: Array, default: () => [] },
    supportingRank: { type: Array, default: () => [] },
    /** 接口加载态 */
    loading: { type: Boolean, default: false },
  },
  data () {
    return {
      hf,
      TH_OFFSET,
      activeTab: (this.panel.tabs && this.panel.tabs[0] && this.panel.tabs[0].key) || 'transfer',
      /** 与 split 双卡一一对应：city = 市级，district = 区级 */
      activeSplit: 'city',
      viewMode: 'table',
    }
  },
  computed: {
    current () {
      return this.panel[this.activeTab] || this.panel.transfer || {
        stat: { label: '', value: 0, unit: '宗' },
        split: [],
        columns: [],
        tables: { city: { rows: [], total: null }, district: { rows: [], total: null } },
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
      // 出让情况 → 各区出让排行；落实配套情况 → 各区配套落实
      return this.activeTab === 'transfer' ? this.transferRank : this.supportingRank
    },
    rankTitle () {
      return this.activeTab === 'transfer' ? '各区出让排行' : '各区配套落实'
    },
    rankMax () {
      return this.rankRows.reduce((max, item) => Math.max(max, item.value), 1)
    },
  },
  watch: {
    activeTab () {
      // 切页签时把「市级 / 区级」重置回该页签的第一张卡，避免残留上一个页签的选择
      this.activeSplit = (this.current.split[0] && this.current.split[0].key) || 'city'
    },
  },
  methods: {
    pad: padIndex,
    tabStyle (index) {
      // 设计稿步进 175；收到 150 是为了在页签条右端放「明细 / 排行」切换
      return { left: `${35 + index * 150}px` }
    },
    /** 环形图填充切图：出让情况用绿/青，落实配套情况用红/金（均为高保真原色） */
    ringFill (item) {
      if (this.current.cube === 'b') {
        return item.key === 'city' ? hf.ringFillRed : hf.ringFillGold
      }
      return item.key === 'city' ? hf.ringFillGreen : hf.ringFillTeal
    },
    /**
     * 高保真里两个填充切图的尺寸不同：
     *   市级 fill 56×56 整环弧，区级 fill 30×56 只有右半环，
     * 位置与尺寸按设计稿原值摆放，保证弧线起止角度与设计稿一致。
     */
    ringFillStyle (item) {
      return item.key === 'city'
        ? { left: '20px', top: '10px', width: '56px', height: '56px' }
        : { left: '45px', top: '10px', width: '30px', height: '56px' }
    },
    barWidth (value) {
      return Math.max(4, Math.round((value / this.rankMax) * 100))
    },
  },
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
    top: 104px;
    margin: 0;
    font-size: 18px;
    font-weight: 500;
    line-height: 18px;
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

  &__stat-value {
    position: absolute;
    left: 156px;
    top: 57px;
    font-family: var(--screen-font-number-family);
    font-size: 28px;
    font-weight: 500;
    line-height: 22px;
    color: #ffe597;
    font-variant-numeric: tabular-nums;
  }

  &__stat-unit {
    position: absolute;
    left: 201px;
    top: 67px;
    font-size: 16px;
    line-height: 12px;
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
  &__ring-fill {
    position: absolute;
    left: 20px;
    top: 10px;
    width: 56px;
    height: 56px;
    display: block;
    pointer-events: none;
  }

  &__ring-text {
    position: absolute;
    left: 36px;
    top: 31px;
    width: 27px;
    font-family: var(--screen-font-number-family);
    font-size: 14px;
    font-weight: 500;
    color: #ffffff;
    text-align: center;
    font-variant-numeric: tabular-nums;
  }

  &__split-label {
    position: absolute;
    left: 95px;
    top: 19px;
    font-size: 14px;
    color: #66afd4;
    white-space: nowrap;
  }

  &__split-value {
    position: absolute;
    left: 95px;
    top: 39px;
    font-family: var(--screen-font-number-family);
    font-size: 20px;
    font-weight: 500;
    line-height: 17px;
    color: #ffffff;
    font-variant-numeric: tabular-nums;
  }

  &__split-unit {
    position: absolute;
    left: 131px;
    top: 46px;
    font-size: 12px;
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
    left: 340px;
    top: 17px;
    width: 52px;
    height: 24px;
    padding: 0;
    font-family: inherit;
    font-size: 12px;
    line-height: 22px;
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
    font-size: 14px;
    color: #4a7396;
    text-align: center;
  }

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
    font-size: 13px;
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
    top: 19px;
    width: 130px;
    height: 8px;
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
    top: 15px;
    font-family: var(--screen-font-number-family);
    font-size: 14px;
    color: #ffffff;
    font-variant-numeric: tabular-nums;
  }
}
</style>
