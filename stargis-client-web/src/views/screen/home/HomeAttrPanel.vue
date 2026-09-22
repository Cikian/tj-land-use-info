<template>
  <!--
    HomeAttrPanel 首页底部「属性表」面板（高保真还原）
    ==================================================================
    定尺取自高保真模型 首页.html：
      面板底   attr-bg.png       (460, 709)  1000×321
      标题     属性表             面板内 (48, 16)  18px #FFFFFF
      页签     轨道 160×38 / 胶囊 150×28      面板内 (603,6) 与 (778,6)
      数据表                     面板内 (20, 63)  960×232
        表头 attr-head.png  36px；表体 attr-body.png 183px；4 行 × 46px
      收起态   仅保留底部条     面板内 (0, 274) 1000×47

    按「高保真 + 保留现有内容」的确认结论：
      保留原有第三个页签「地块预警信息」，点击后以高保真提供的独立弹窗
      （bg-__1_u294.png，1270×663）呈现，与设计稿中该浮层的形态一致。
  -->
  <section class="home-attr stage-hit" :class="{ 'is-collapsed': collapsed }">
    <!-- 展开态 -->
    <template v-if="!collapsed">
      <img class="home-attr__bg" :src="hf.attrBg" alt="" aria-hidden="true" />
      <h3 class="home-attr__title">属性表</h3>

      <!-- 页签（第三个「地块预警信息」点击后开弹窗，不切换面板内容） -->
      <div class="home-attr__tabs" role="tablist">
        <button
          v-for="(tab, index) in tabs"
          :key="tab.key"
          type="button"
          role="tab"
          class="home-attr__tab"
          :class="{ 'is-active': tab.key === activeTab }"
          :style="tabStyle(index)"
          :aria-selected="String(tab.key === activeTab)"
          @click="handleTab(tab)"
        >
          <img class="home-attr__tab-track" :src="hf.tabTrack" alt="" aria-hidden="true" />
          <img
            class="home-attr__tab-pill"
            :src="tab.key === activeTab ? hf.tabActive : hf.tabIdle"
            alt=""
            aria-hidden="true"
          />
          <span class="home-attr__tab-text">{{ tab.label }}</span>
        </button>
      </div>

      <!-- 数据表 -->
      <div class="home-attr__table">
        <img class="home-attr__head-bg" :src="hf.attrHead" alt="" aria-hidden="true" />
        <img class="home-attr__body-bg" :src="hf.attrBody" alt="" aria-hidden="true" />

        <span
          v-for="col in columns"
          :key="col.key"
          class="home-attr__th"
          :style="col.style"
        >{{ col.title }}</span>

        <div
          v-for="(row, index) in rows"
          :key="row.id"
          class="home-attr__tr"
          :style="{ top: `${48 + index * 46}px` }"
        >
          <img
            v-if="index % 2 === 0"
            class="home-attr__tr-bg"
            :src="hf.attrRow"
            alt=""
            aria-hidden="true"
          />
          <span class="home-attr__td" :style="cellStyle('index')">{{ index + 1 }}</span>
          <span class="home-attr__td" :style="cellStyle('district')">{{ row.district }}</span>
          <span class="home-attr__td" :style="cellStyle('landAcquisition')">{{ percent(row.landAcquisition) }}</span>
          <span class="home-attr__td" :style="cellStyle('feasibility')">{{ percent(row.feasibility) }}</span>
          <span class="home-attr__td" :style="cellStyle('preliminaryDesign')">{{ percent(row.preliminaryDesign) }}</span>
          <span class="home-attr__td" :style="cellStyle('fund')">{{ percent(row.fund) }}</span>
          <span class="home-attr__td" :style="cellStyle('notStarted')">{{ percent(row.notStarted) }}</span>
          <span class="home-attr__td" :style="cellStyle('notCompleted')">{{ percent(row.notCompleted) }}</span>
          <span class="home-attr__td" :style="cellStyle('notHandedOver')">{{ percent(row.notHandedOver) }}</span>
          <img
            class="home-attr__action"
            :src="hf.actionView"
            alt="查看"
            role="button"
            tabindex="0"
            @click="$emit('row-action', row)"
            @keydown.enter.prevent="$emit('row-action', row)"
          />
        </div>

        <p v-if="!rows.length" class="home-attr__empty">
          {{ loading ? '加载中…' : '暂无预警数据' }}
        </p>
      </div>

      <!-- 加载提示挂在标题右侧，避免压住表头切片 -->
      <span v-if="loading && rows.length" class="home-attr__loading">加载中…</span>

      <!-- 收起 -->
      <button type="button" class="home-attr__toggle" aria-label="收起属性表" @click="collapsed = true">
        <img :src="hf.attrCollapse" alt="" aria-hidden="true" />
      </button>
    </template>

    <!-- 收起态 -->
    <template v-else>
      <img class="home-attr__bar" :src="hf.attrBar" alt="" aria-hidden="true" />
      <h3 class="home-attr__title is-collapsed">属性表</h3>
      <button type="button" class="home-attr__toggle is-collapsed" aria-label="展开属性表" @click="collapsed = false">
        <img :src="hf.attrExpand" alt="" aria-hidden="true" />
      </button>
    </template>
  </section>
</template>

<script>
import { hf } from '@/assets/screen-blue'
import { warningTabs } from '../config'
import { demoWarningData } from '../mock'

/**
 * 列横向位置。
 * 设计稿表宽 960，列的 left 值取自高保真 CSS；这里换算成百分比，
 * 这样屏幕比 16:9 更宽、属性表被拉宽时，各列会按比例摊开，
 * 而不是全部挤在左边、右边留下大片空白。设计稿比例下数值完全一致。
 */
const DESIGN_TABLE_WIDTH = 960
const COL_LEFT = {
  index: 13,
  district: 69,
  landAcquisition: 153,
  feasibility: 278,
  preliminaryDesign: 402,
  fund: 568,
  notStarted: 668,
  notCompleted: 743,
  notHandedOver: 819,
}
/** 「操作」列靠右锚定：设计稿 x=895、图标/文字宽约 28，即距表右边界 37px */
const ACTION_RIGHT = DESIGN_TABLE_WIDTH - 895 - 28

const COL_PCT = Object.keys(COL_LEFT).reduce((acc, key) => {
  acc[key] = `${((COL_LEFT[key] / DESIGN_TABLE_WIDTH) * 100).toFixed(4)}%`
  return acc
}, {})

/** 表头列（「操作」列的标题也在表头里，单元格是三圆点按钮） */
const COLUMNS = [
  { key: 'index', title: '序号' },
  { key: 'district', title: '行政区划' },
  { key: 'landAcquisition', title: '项建批复未完成' },
  { key: 'feasibility', title: '可研批复未完成' },
  { key: 'preliminaryDesign', title: '初设及概算批复未完成' },
  { key: 'fund', title: '资金未落实' },
  { key: 'notStarted', title: '未开工' },
  { key: 'notCompleted', title: '未竣工' },
  { key: 'notHandedOver', title: '未移交' },
  { key: 'action', title: '操作' },
]

export default {
  name: 'HomeAttrPanel',
  props: {
    /** 页签（默认取 config.js，与接口无关） */
    tabs: { type: Array, default: () => warningTabs },
    /** 预警数据：{ city: [...], district: [...] }，字段见 config.js 的 warningFields */
    data: { type: Object, default: () => demoWarningData },
    /** 接口加载态 */
    loading: { type: Boolean, default: false },
    /** 点击后开弹窗的页签 key（不切换面板内容） */
    plotTabKey: { type: String, default: 'plot' },
  },
  data () {
    return {
      hf,
      activeTab: (this.tabs[0] && this.tabs[0].key) || 'city',
      collapsed: false,
    }
  },
  computed: {
    columns () {
      return COLUMNS.map((col) => ({
        key: col.key,
        title: col.title,
        style:
          col.key === 'action'
            ? { right: `${ACTION_RIGHT}px` }
            : { left: COL_PCT[col.key] },
      }))
    },
    rows () {
      return (this.data && this.data[this.activeTab]) || []
    },
  },
  methods: {
    /** 单元格定位：与表头同一套列位置 */
    cellStyle (key) {
      return { left: COL_PCT[key] }
    },
    /**
     * 高保真只有两个页签（x=603 / 778，步进 175，宽 160）。保留原有
     * 「地块预警信息」后共 3 个，整组右对齐（设计稿最后一个页签右缘距面板
     * 右边界 15px），所以前两个页签相对设计稿左移 —— 这是刻意的位置偏移，
     * 面板标题与下方数据表均不受影响。用 right 定位是为了让属性表被拉宽时
     * 页签始终贴在右侧。
     */
    tabStyle (index) {
      const count = this.tabs.length
      return { right: `${15 + (count - 1 - index) * 175}px` }
    },
    handleTab (tab) {
      if (tab.key === this.plotTabKey) {
        this.$emit('open-plot-warning')
        return
      }
      this.activeTab = tab.key
    },
    percent (value) {
      return `${Number(value).toFixed(2)}%`
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.home-attr {
  position: absolute;
  // 设计稿 x=460..1460（左右各留 460，即与两侧面板保持 30px 间距），
  // 纵向用设计稿原值 y=709..1030，与左右面板底边对齐；
  // ScreenStage 保证了设计高度 ≥1080，所以这里不会溢出。
  left: 460px;
  right: 460px;
  top: 709px;
  height: 321px;

  &__bg,
  &__bar {
    position: absolute;
    display: block;
    pointer-events: none;
  }

  &__bg {
    left: 0;
    top: 0;
    width: 100%;
    height: 321px;
  }

  &__bar {
    left: 0;
    top: 274px;
    width: 100%;
    height: 47px;
  }

  &__title {
    position: absolute;
    left: 48px;
    top: 16px;
    margin: 0;
    font-size: 18px;
    font-weight: 500;
    line-height: 18px;
    color: #ffffff;
    white-space: nowrap;

    &.is-collapsed {
      top: 289px;
    }
  }

  /* ---------------- 页签 ---------------- */
  &__tab {
    position: absolute;
    top: 6px;
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

  /* ---------------- 数据表 ---------------- */
  &__table {
    position: absolute;
    left: 20px;
    right: 20px;
    top: 63px;
    height: 232px;
  }

  &__head-bg {
    position: absolute;
    left: 2px;
    top: 0;
    // 设计稿里表头切图 957 宽，占 960 的表头区、左右各留 2px/1px。
    // ⚠ <img> 必须显式给宽度，只写 left/right 不会拉伸
    width: calc(100% - 3px);
    height: 36px;
    display: block;
    pointer-events: none;
  }

  &__body-bg {
    position: absolute;
    left: 0;
    top: 48px;
    // ⚠ <img> 必须显式给宽度，只写 left/right 不会拉伸
    width: 100%;
    height: 183px;
    display: block;
    pointer-events: none;
  }

  &__th {
    position: absolute;
    top: 11px;
    font-size: 14px;
    line-height: 14px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__tr {
    position: absolute;
    left: 0;
    width: 100%;
    height: 46px;

    // ::before 是普通元素（非替换元素），left+right 可以正常拉伸
    &:hover::before {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      top: 0;
      height: 46px;
      background-image: url('~@/assets/screen-blue/attr-row-hover.png');
      background-repeat: no-repeat;
      background-size: 100% 46px;
      pointer-events: none;
    }
  }

  &__tr-bg {
    position: absolute;
    left: 0;
    top: 0;
    // ⚠ <img> 必须显式给宽度，只写 left/right 不会拉伸
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
    font-variant-numeric: tabular-nums;
  }

  &__action {
    position: absolute;
    // 设计稿 x=895（表宽 960、图标 14），即距右边界 51px
    right: 51px;
    top: 16px;
    width: 14px;
    height: 14px;
    cursor: pointer;
    .screen-focus-ring();
  }

  /* 空数据提示：压在表体中央，不遮挡切片 */
  &__empty {
    position: absolute;
    left: 0;
    top: 130px;
    width: 100%;
    margin: 0;
    font-size: 14px;
    color: #4a7396;
    text-align: center;
  }

  /* 加载提示：贴在标题右侧，不压住表头 */
  &__loading {
    position: absolute;
    left: 130px;
    top: 18px;
    font-size: 12px;
    line-height: 14px;
    color: #82c6ff;
  }

  /* ---------------- 收起 / 展开 ---------------- */
  &__toggle {
    position: absolute;
    left: 963px;
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
      top: 291px;
    }
  }
}
</style>
