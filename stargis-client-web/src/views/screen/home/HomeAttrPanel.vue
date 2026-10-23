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

    ★ 项目预警（方案 2.3.1（五））的两个页签在本组件里完成三件事：
      1. 七个百分比列按**旧系统的阈值**着色 —— 市级 5 档、区级 3 档
         （档位定义在 views/screen/config.js 的 warningTiers，别在这里另写一套）；
      2. 「一条配套都没上报」的区，七列合并成一句「未填报配套项目信息」
         （旧系统是逐列重复写这句，窄列里会被截成「未填报配」）；
      3. 「操作 → 查看」把行连同**当前页签 key** 抛给父组件（第二个 emit 参数），
         父组件据此决定下钻用市级还是区级口径 —— 不要靠行 id 里的中文字符串去猜。
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
        <div class="home-attr__head">
          <img class="home-attr__head-bg" :src="hf.attrHead" alt="" aria-hidden="true" />
          <span
            v-for="col in columns"
            :key="col.key"
            class="home-attr__th"
            :style="col.style"
          >{{ col.title }}</span>
        </div>
        <div class="home-attr__rows">
          <img class="home-attr__body-bg" :src="hf.attrBody" alt="" aria-hidden="true" />

          <div
            v-for="(row, index) in rows"
            :key="row.id || index"
            class="home-attr__tr"
          >
            <img
              v-if="index % 2 === 0"
              class="home-attr__tr-bg"
              :src="hf.attrRow"
              alt=""
              aria-hidden="true"
            />
            <span class="home-attr__td" :style="cellStyle('index')">{{ index + 1 }}</span>
            <span
              class="home-attr__td"
              :style="cellStyle('district')"
              :title="row.district"
            >{{ row.district }}</span>
            <!--
              「一条配套都没上报」的区：七列都是空的，旧系统逐列写「未填报配套项目信息」
              （8%/14.5%/15%…的窄列里被截成「未填报配」），这里改成跨七列一句话。
            -->
            <span
              v-if="row.unreported"
              class="home-attr__td is-unreported"
              :style="unreportedStyle"
            >未填报配套项目信息</span>
            <template v-else>
              <span
                v-for="col in indicatorColumns"
                :key="col.key"
                class="home-attr__td"
                :class="toneClass(row, col.key)"
                :style="cellStyle(col.key)"
                :title="toneTitle(row, col.key)"
              >{{ percentText(row[col.key]) }}</span>
            </template>
            <img
              class="home-attr__action"
              :src="hf.actionView"
              alt="查看"
              role="button"
              tabindex="0"
              @click="$emit('row-action', row, activeTab)"
              @keydown.enter.prevent="$emit('row-action', row, activeTab)"
            />
          </div>
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
import { warningTabs, warningTierKey, warningTiers, warningTone } from '../config'

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
  notHandedOver: 819
}
/** 「操作」列靠右锚定：设计稿 x=895、图标/文字宽约 28，即距表右边界 37px */
const ACTION_RIGHT = DESIGN_TABLE_WIDTH - 895 - 28

const COL_KEYS = Object.keys(COL_LEFT)
const COL_PCT = COL_KEYS.reduce((acc, key) => {
  acc[key] = `${((COL_LEFT[key] / DESIGN_TABLE_WIDTH) * 100).toFixed(4)}%`
  return acc
}, {})
const COL_WIDTH = COL_KEYS.reduce((acc, key, index) => {
  const next = index === COL_KEYS.length - 1 ? 890 : COL_LEFT[COL_KEYS[index + 1]]
  acc[key] = `${(((next - COL_LEFT[key] - 12) / DESIGN_TABLE_WIDTH) * 100).toFixed(4)}%`
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
  { key: 'action', title: '操作' }
]

/**
 * 七个百分比列（从「项建批复未完成」到「未移交」）。
 * 旧系统对这几列逐列上色（datagrid 的 styler 从第 3 列起生效，即 index ≥ 2），
 * 「序号 / 行政区划」不上色 —— 这里用同一份列清单驱动，避免漏列或多列。
 */
const INDICATOR_KEYS = COLUMNS
  .map((col) => col.key)
  .filter((key) => key !== 'index' && key !== 'district' && key !== 'action')

export default {
  name: 'HomeAttrPanel',
  props: {
    /** 页签（默认取 config.js，与接口无关） */
    tabs: { type: Array, default: () => warningTabs },
    /** 预警数据：{ city: [...], district: [...] }，字段见 config.js 的 warningFields */
    data: { type: Object, default: () => ({ city: [], district: [], plot: [] }) },
    /** 接口加载态 */
    loading: { type: Boolean, default: false },
    /** 点击后开弹窗的页签 key（不切换面板内容） */
    plotTabKey: { type: String, default: 'plot' }
  },
  data () {
    return {
      hf,
      activeTab: (this.tabs[0] && this.tabs[0].key) || 'city',
      collapsed: false
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
            : { left: COL_PCT[col.key] }
      }))
    },
    rows () {
      // 「地块预警信息」走弹窗，不在这块定尺表格里渲染（它一行一宗地、几百行）；
      // 这里返回空数组是兜底：万一 activeTab 被外部改成 plot，面板显示空态而不是一堆 0.00%
      if (this.activeTab === this.plotTabKey) return []
      return (this.data && this.data[this.activeTab]) || []
    },
    indicatorColumns () {
      return this.columns.filter((col) => INDICATOR_KEYS.indexOf(col.key) > -1)
    },
    /** 「未填报配套项目信息」跨列的起止：从第一个指标列左缘铺到最后一个指标列右缘 */
    unreportedStyle () {
      const start = COL_LEFT[INDICATOR_KEYS[0]]
      const lastKey = INDICATOR_KEYS[INDICATOR_KEYS.length - 1]
      // 最后一个指标列的右缘与 COL_WIDTH 的算法一致（末列到 890 为止）
      const end = COL_LEFT[lastKey] + (890 - COL_LEFT[lastKey])
      return {
        left: COL_PCT[INDICATOR_KEYS[0]],
        width: `${(((end - start) / DESIGN_TABLE_WIDTH) * 100).toFixed(4)}%`
      }
    },
    /** 当前页签对应的档位表（市级 5 档 / 区级 3 档），供单元格 title 提示用 */
    tiers () {
      return warningTiers[warningTierKey(this.activeTab)] || []
    }
  },
  methods: {
    /** 单元格定位：与表头同一套列位置 */
    cellStyle (key) {
      return { left: COL_PCT[key], width: COL_WIDTH[key] }
    },
    /** 百分比文案：后端给的是 2 位小数的数字，展示时统一补「%」 */
    percentText (value) {
      const num = Number(value)
      return `${Number.isNaN(num) ? 0 : num.toFixed(2)}%`
    },
    /**
     * 阈值着色（★ 市级 5 档 / 区级 3 档，口径见 config.js 的 warningTiers）。
     * 行上带 projectType 时以它为准：地块预警那种市/区混排的表里，
     * 一行可能不属于当前页签的层级，用页签档位会着色错。
     */
    toneClass (row, key) {
      const tone = warningTone((row && row.projectType) || this.activeTab, row && row[key])
      return tone ? `is-tone-${tone}` : ''
    },
    /** 单元格悬停提示：把「这个颜色代表什么区间」讲清楚，省得用户去问阈值 */
    toneTitle (row, key) {
      const text = this.percentText(row && row[key])
      const value = Number(row && row[key])
      if (Number.isNaN(value)) return text
      const tier = this.tiers.find((item) => value < item.lt) || this.tiers[this.tiers.length - 1]
      return tier ? `${text}（${tier.label}）` : text
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
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.home-attr {
  position: absolute;
  // 设计稿 x=460..1460（左右各留 460，即与两侧面板保持 30px 间距）。
  // 纵向下贴 50、定尺高 321：设计比例下顶边正好落在 709，与设计稿一致；
  // 下贴边保证画布变高时它始终和左右面板的底边对齐。
  left: 460px;
  right: 460px;
  bottom: 50px;
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
    top: 15px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 22px;
    color: #ffffff;
    white-space: nowrap;

    &.is-collapsed {
      top: 288px;
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
    font-size: var(--screen-font-sm);
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
    overflow: hidden;
  }

  &__rows {
    position: absolute;
    left: 0;
    right: 0;
    top: 48px;
    bottom: 0;
    overflow-x: hidden;
    overflow-y: auto;
  }

  &__head {
    position: absolute;
    left: 0;
    right: 0;
    top: 0;
    height: 36px;
    z-index: 2;
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
    top: 0;
    // ⚠ <img> 必须显式给宽度，只写 left/right 不会拉伸
    width: 100%;
    height: 183px;
    display: block;
    pointer-events: none;
  }

  &__th {
    position: absolute;
    top: 8px;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__tr {
    position: relative;
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
    top: 14px;
    font-size: var(--screen-font-sm);
    line-height: 18px;
    color: #66afd4;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    font-variant-numeric: tabular-nums;
  }

  /*
   * 预警百分比的分档着色（市级 5 档 / 区级 3 档，档位见 views/screen/config.js）。
   * 旧系统用 CSS 具名色（green/blue/yellow/orange/red），在深海蓝底上黄色与橙色
   * 几乎分不开，这里换成 tokens 里的 --screen-level-*，色相不变、可读性提升。
   */
  &__td.is-tone-green {
    color: var(--screen-level-green);
  }

  &__td.is-tone-blue {
    color: var(--screen-level-blue);
  }

  &__td.is-tone-yellow {
    color: var(--screen-level-yellow);
  }

  &__td.is-tone-orange {
    color: var(--screen-level-orange);
  }

  &__td.is-tone-red {
    color: var(--screen-level-red);
  }

  /* 「未填报配套项目信息」：跨七列居中，弱化处理（不是 0%，是没有数据） */
  &__td.is-unreported {
    color: #4a7396;
    text-align: center;
  }

  &__action {
    position: absolute;
    // 设计稿 x=895（表宽 960、图标 14），即距右边界 51px
    right: 51px;
    top: 14px;
    width: 16px;
    height: 16px;
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
    font-size: var(--screen-font-sm);
    color: #4a7396;
    text-align: center;
  }

  /* 加载提示：贴在标题右侧，不压住表头 */
  &__loading {
    position: absolute;
    left: 140px;
    top: 17px;
    font-size: var(--screen-font-xs);
    line-height: 20px;
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
