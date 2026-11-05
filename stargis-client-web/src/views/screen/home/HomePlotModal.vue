<template>
  <!--
    HomePlotModal 地块预警信息弹窗（高保真还原）
    ==================================================================
    取自高保真模型 首页.html 的独立浮层（bg-__1_u294.png，1270×663）：
      标题      地块预警信息        (81, 20)
      关闭      modal-close.svg     (1235, 21)
      表头      plot-table-head.png (25, 72)   1217×36
      表体      plot-table-body.png (25, 118)  1219×457，10 行 × 46
      分页      共 N 条 / 页码 / 每页显示条数 / 前往 N 页   (25, 598) 起

    ⚠ 高保真底稿里「项建批复未完成」被复制了两列（第 3、4 列同名），
      属设计稿笔误；这里保留 10 列的位置不变，把重复列改为有信息量的第三列
      （全部宗地 → 行政区划；按区下钻 → 配套项目数），列宽与间距仍与设计稿一致。

    ★ 本弹窗承担两个入口（variant 区分，列位置共用同一套设计稿坐标）：
      · 底部第三个页签「地块预警信息」→ variant="plot"，列全部宗地（市/区混排）；
      · 预警行「操作 → 查看」→ variant="detail"，列该区逐宗地的明细
        （对应旧系统 homePage.js 的 seeDataInfo717 / seeDataInfo0805 → byPageQuery）。

    弹窗位于高保真画布内（而非挂到 body），这样它跟着 1920×1080 画布一起
    等比缩放，不需要额外处理缩放坐标。
  -->
  <div class="home-plot">
    <div class="home-plot__mask" @click="$emit('close')" />
    <section class="home-plot__dialog stage-hit" role="dialog" aria-modal="true" aria-label="地块预警信息">
      <img class="home-plot__bg" :src="hf.modalBg" alt="" aria-hidden="true" />
      <h3 class="home-plot__title">{{ data.title }}</h3>
      <button type="button" class="home-plot__close" aria-label="关闭" @click="$emit('close')">
        <img :src="hf.modalClose" alt="" aria-hidden="true" />
      </button>

      <!-- 表格 -->
      <div class="home-plot__table">
        <img class="home-plot__head" :src="hf.plotTableHead" alt="" aria-hidden="true" />
        <span
          v-for="col in columns"
          :key="col.key"
          class="home-plot__th"
          :style="{ left: colLeft(col.left) }"
        >{{ col.title }}</span>
        <img class="home-plot__body" :src="hf.plotTableBody" alt="" aria-hidden="true" />

        <div
          v-for="(row, index) in pageRows"
          :key="row.id || index"
          class="home-plot__tr"
          :style="{ top: `${118 + index * 46}px` }"
        >
          <img
            v-if="index % 2 === 0"
            class="home-plot__tr-bg"
            :src="hf.plotRow"
            alt=""
            aria-hidden="true"
          />
          <span
            v-for="col in columns"
            :key="col.key"
            class="home-plot__td"
            :class="col.percent ? toneClass(row, col.key) : ''"
            :style="cellStyle(col)"
            :title="cellTitle(row, col)"
          >{{ cellText(row, col) }}</span>
        </div>

        <p v-if="!pageRows.length" class="home-plot__empty">暂无预警数据</p>
      </div>

      <!-- 分页 -->
      <div class="home-plot__pager">
        <span class="home-plot__pager-total">共{{ data.total }}条信息</span>
        <button type="button" class="home-plot__page is-nav" @click="go(page - 1)">&lt;</button>
        <button
          v-for="n in pageNumbers"
          :key="n"
          type="button"
          class="home-plot__page"
          :class="{ 'is-active': n === page }"
          @click="go(n)"
        >{{ n }}</button>
        <button type="button" class="home-plot__page is-nav" @click="go(page + 1)">&gt;</button>
        <span class="home-plot__pager-label">每页显示条数</span>
        <button type="button" class="home-plot__pager-size">
          {{ pageSize }}
          <img :src="hf.pagerCaret" alt="" aria-hidden="true" />
        </button>
        <span class="home-plot__pager-label is-go">前往</span>
        <span class="home-plot__pager-input">
          <img :src="hf.pagerInput" alt="" aria-hidden="true" />
          <input v-model.number="jump" type="text" aria-label="前往页码" @keyup.enter="go(jump)" />
        </span>
        <span class="home-plot__pager-label is-page">页</span>
      </div>
    </section>
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'
import { warningTone } from '../config'

/** 列横向偏移（取自高保真 CSS 的 left 值，相对弹窗左上角） */
const TABLE_WIDTH = 1367
const COL_LEFT = {
  landNo: 16,
  plotName: 180,
  extra: 360,
  landAcquisition: 620,
  feasibility: 730,
  preliminaryDesign: 850,
  fund: 990,
  notStarted: 1090,
  notCompleted: 1170,
  notHandedOver: 1260
}
const COL_WIDTH = {
  landNo: 150,
  plotName: 165,
  extra: 245,
  landAcquisition: 95,
  feasibility: 105,
  preliminaryDesign: 125,
  fund: 85,
  notStarted: 65,
  notCompleted: 65,
  notHandedOver: 75
}

/**
 * 七个预警指标列（弹窗里的表头比底部属性表短：批复 / 资金 / 开工 / 竣工 / 移交）。
 * percent: true 的列按阈值着色并补「%」——档位规则与底部面板完全一致
 * （市级 5 档 / 区级 3 档，定义在 views/screen/config.js 的 warningTiers）。
 */
const INDICATOR_COLUMNS = [
  { key: 'landAcquisition', title: '项建批复', left: COL_LEFT.landAcquisition, width: COL_WIDTH.landAcquisition, percent: true },
  { key: 'feasibility', title: '可研批复', left: COL_LEFT.feasibility, width: COL_WIDTH.feasibility, percent: true },
  { key: 'preliminaryDesign', title: '初设批复', left: COL_LEFT.preliminaryDesign, width: COL_WIDTH.preliminaryDesign, percent: true },
  { key: 'fund', title: '资金落实', left: COL_LEFT.fund, width: COL_WIDTH.fund, percent: true },
  { key: 'notStarted', title: '开工', left: COL_LEFT.notStarted, width: COL_WIDTH.notStarted, percent: true },
  { key: 'notCompleted', title: '竣工', left: COL_LEFT.notCompleted, width: COL_WIDTH.notCompleted, percent: true },
  { key: 'notHandedOver', title: '移交', left: COL_LEFT.notHandedOver, width: COL_WIDTH.notHandedOver, percent: true }
]

/**
 * 两套列定义（第三列不同，其余九列位置完全一致，因此不必重排设计稿坐标）：
 *   plot   第三页签「地块预警信息」：市/区两级全部宗地 → 显示行政区划；
 *   detail 「操作 → 查看」下钻：某个区逐宗地明细 → 显示该宗地的配套项目数
 *          （同一个区里各宗地的配套条数差别很大，这一列是解释「为什么是 60%」的关键）。
 *
 * ⚠ 高保真底稿把「项建批复未完成」复制了两列（第 3、4 列同名），属设计稿笔误，
 *   这里保留 10 列的位置不变，把第三列改成上面两种有信息量的列。
 */
const COLUMN_PRESETS = {
  plot: [
    { key: 'landNo', title: '出让宗地编号', left: COL_LEFT.landNo, width: COL_WIDTH.landNo },
    { key: 'plotName', title: '地块名称', left: COL_LEFT.plotName, width: COL_WIDTH.plotName },
    { key: 'district', title: '行政区划', left: COL_LEFT.extra, width: COL_WIDTH.extra }
  ].concat(INDICATOR_COLUMNS),
  detail: [
    { key: 'landNo', title: '出让宗地编号', left: COL_LEFT.landNo, width: COL_WIDTH.landNo },
    { key: 'plotName', title: '地块名称', left: COL_LEFT.plotName, width: COL_WIDTH.plotName },
    { key: 'facilityCount', title: '配套项目数', left: COL_LEFT.extra, width: COL_WIDTH.extra }
  ].concat(INDICATOR_COLUMNS)
}

export default {
  name: 'HomePlotModal',
  props: {
    /**
     * 弹窗数据：{ title, total, rows[] }。
     * 只使用接口返回的地块预警，没有数据时为空。
     *
     * rows 里的 7 个指标是**数字**（不是格式化好的字符串）：着色要拿数值比档位，
     * 在父组件先拼成「12.34%」再传进来就得反解析，容易出现「颜色与文字对不上」。
     * 每行还要带 projectType（市级项目 / 区级项目），混排表里逐行选档位。
     */
    data: { type: Object, default: () => ({ title: '地块预警信息', total: 0, rows: [] }) },
    /** 列定义：plot（全部宗地）/ detail（某区逐宗地） */
    variant: { type: String, default: 'plot' }
  },
  data () {
    return {
      hf,
      page: 1,
      // 一屏正好 10 行（设计稿表体 457px / 行高 46）；与 less 里的行高、行距必须一致，
      // 否则多出来的行会被 overflow:hidden 裁掉（旧值 18 就是这个坑）
      pageSize: 10,
      jump: null
    }
  },
  computed: {
    columns () {
      return COLUMN_PRESETS[this.variant] || COLUMN_PRESETS.plot
    },
    rows () {
      return (this.data && this.data.rows) || []
    },
    pageCount () {
      return Math.max(1, Math.ceil(this.rows.length / this.pageSize))
    },
    pageRows () {
      const start = (this.page - 1) * this.pageSize
      return this.rows.slice(start, start + this.pageSize)
    },
    pageNumbers () {
      const count = this.pageCount
      const start = Math.max(1, Math.min(this.page - 2, count - 4))
      const end = Math.min(count, start + 4)
      const numbers = []
      for (let n = start; n <= end; n += 1) numbers.push(n)
      return numbers
    }
  },
  mounted () {
    document.addEventListener('keydown', this.handleKey)
  },
  beforeDestroy () {
    document.removeEventListener('keydown', this.handleKey)
  },
  methods: {
    colLeft (left) {
      return `${((left / TABLE_WIDTH) * 100).toFixed(4)}%`
    },
    cellStyle (col) {
      return {
        left: this.colLeft(col.left),
        width: `${((col.width / TABLE_WIDTH) * 100).toFixed(4)}%`
      }
    },
    /** 单元格文案：百分比列补「%」并保留 2 位，其余列原样输出 */
    cellText (row, col) {
      const value = row ? row[col.key] : ''
      if (!col.percent) return value === null || value === undefined ? '' : value
      const num = Number(value)
      return `${Number.isNaN(num) ? 0 : num.toFixed(2)}%`
    },
    /** 阈值着色：行上的 projectType 优先（市/区两级混排时按各自档位） */
    toneClass (row, key) {
      const tone = warningTone((row && row.projectType) || '', row && row[key])
      return tone ? `is-tone-${tone}` : ''
    },
    cellTitle (row, col) {
      const text = this.cellText(row, col)
      if (!col.percent) return text
      return `${text}（${(row && row.projectType) || ''}）`
    },
    go (n) {
      if (typeof n !== 'number' || Number.isNaN(n)) return
      this.page = Math.min(this.pageCount, Math.max(1, n))
    },
    handleKey (e) {
      if (e.key === 'Escape') this.$emit('close')
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.home-plot {
  position: absolute;
  // 铺满整个设计画布（宽度会随屏幕比例变化，不能写死 1920×1080，
  // 否则遮罩会盖不住右边/下边，出现一条没被压暗的亮边）
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;

  &__mask {
    position: absolute;
    inset: 0;
    background: rgba(2, 7, 18, 0.55);
    pointer-events: auto;
  }

  &__dialog {
    // 跟着画布居中，而不是按 1920×1080 写死 left/top
    position: relative;
    width: 1420px;
    height: 663px;
  }

  &__bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 1420px;
    height: 663px;
    display: block;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    left: 81px;
    top: 20px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__close {
    position: absolute;
    right: 21px;
    left: auto;
    top: 21px;
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
  }

  /* ---------------- 表格 ---------------- */
  // 表头 36 + 10 行 × 46 = 496，落在设计稿的表体区（表头 72、分页 598）之内。
  // ⚠ 行高必须是 46，且与脚本里 pageSize=10、行的 top 步进（118 + i*46）三处一致：
  //   之前行高写 52、pageSize 写 18，结果第 11 行往后被 overflow:hidden 裁掉，
  //   用户翻到第 1 页只看到 10 条、以为数据丢了（每页条数还写着 18）。
  &__table {
    position: absolute;
    left: 25px;
    top: 72px;
    width: 1367px;
    height: 520px;
    overflow: hidden;
  }

  &__head,
  &__body {
    position: relative;
    left: 0;
    display: block;
    width: 100%;
    pointer-events: none;
  }

  &__head {
    height: 36px;
  }

  &__body {
    height: 484px;
    display: block;
    pointer-events: none;
  }

  &__th {
    position: absolute;
    top: 9px;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__tr {
    position: absolute;
    left: 0;
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
    top: 13px;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: #66afd4;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    font-variant-numeric: tabular-nums;
  }

  /*
   * 七个指标列的阈值着色 —— 与底部属性表同一套档位
   * （市级 5 档 / 区级 3 档，见 views/screen/config.js 的 warningTiers）。
   * 这里是市/区两级混排的表，逐行按 row.projectType 选档位。
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

  /* 空态：压在表体中央，不遮挡表头/行底切图 */
  &__empty {
    position: absolute;
    left: 0;
    top: 200px;
    width: 100%;
    margin: 0;
    font-size: var(--screen-font-sm);
    color: #4a7396;
    text-align: center;
  }

  /* ---------------- 分页 ---------------- */
  &__pager {
    position: absolute;
    left: 480px;
    top: 598px;
    display: flex;
    align-items: center;
    height: 36px;
    font-size: var(--screen-font-sm);
    color: #66afd4;
  }

  &__pager-total {
    margin-right: 58px;
    white-space: nowrap;
  }

  &__page {
    width: 36px;
    height: 36px;
    margin-right: 10px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    color: #66afd4;
    background: transparent;
    border: 0;
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
    }

    &.is-active {
      color: #ffffff;
      background: rgba(85, 185, 255, 0.22);
    }
  }

  &__pager-label {
    margin-left: 128px;
    white-space: nowrap;

    &.is-go {
      margin-left: 24px;
    }

    &.is-page {
      margin-left: 24px;
    }
  }

  &__pager-size {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    width: 80px;
    height: 36px;
    margin-left: 24px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    color: #ffffff;
    background: transparent;
    border: 0;
    cursor: pointer;

    img {
      width: 8px;
      height: 5px;
    }
  }

  &__pager-input {
    position: relative;
    display: inline-flex;
    align-items: center;
    width: 42px;
    height: 36px;
    margin-left: 12px;

    img {
      position: absolute;
      left: 0;
      top: 0;
      width: 42px;
      height: 36px;
      pointer-events: none;
    }

    input {
      position: relative;
      width: 100%;
      height: 100%;
      padding: 0;
      font-family: inherit;
      font-size: var(--screen-font-sm);
      color: #ffffff;
      text-align: center;
      background: transparent;
      border: 0;
      outline: none;
    }
  }
}
</style>
