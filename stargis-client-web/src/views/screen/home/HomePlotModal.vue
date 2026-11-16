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
        <!--
          行区域单独包一层滚动视口（P2-04）：
          每页 10 / 20 / 50 三档共享同一块 484px 高的可视区，行数超出一屏时
          靠滚动看全，而不是让外层的 overflow:hidden 把第 11 行之后悄悄裁掉
          （那正是「切到 20 条仍只看到 10 行」的表现）。
          表头留在视口外，滚动时不会跟着走。
        -->
        <div ref="scroll" class="home-plot__scroll">
          <div class="home-plot__canvas" :style="{ height: canvasHeight }">
            <img class="home-plot__body" :src="hf.plotTableBody" alt="" aria-hidden="true" />

            <div
              v-for="(row, index) in pageRows"
              :key="row.id || index"
              class="home-plot__tr"
              :style="{ top: rowTop(index) }"
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
        </div>
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
        <!--
          每页条数选择（P2-04）：原来只是一个没有任何事件的展示按钮，点不动。
          这里用原生 select —— 弹窗只挂在画布内、只有 10/20/50 三档，
          不值得为它引一层浮层组件；appearance:none 掉原生箭头，
          右侧仍用设计稿的 caret 图，外观与原来的「10 ⌄」完全一致。
        -->
        <span class="home-plot__pager-size">
          <select
            class="home-plot__pager-size-select"
            :value="pageSize"
            aria-label="每页显示条数"
            @change="handlePageSizeChange"
          >
            <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }}</option>
          </select>
          <img class="home-plot__pager-size-caret" :src="hf.pagerCaret" alt="" aria-hidden="true" />
        </span>
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

/**
 * 表格几何常量 —— 与 less 里 .home-plot__head / &__tr / &__scroll 三处必须逐个对上，
 * 否则行会错位或被裁掉（旧版本把行高写成 52、pageSize 写成 18 就是这么出的事）。
 *   ROW_TOP     设计稿里首行的 top，相对整个表格容器（表体 457px 装 10 行 × 46，
 *               起点在高保真稿的 y=118，而表格容器自己从 y=72 开始 → 46）
 *   HEAD_HEIGHT 表头高度；行区域在它下面的滚动视口里，所以行的 top 要减掉这一截
 *   ROW_HEIGHT  行高
 *   BODY_HEIGHT 行区域可视高度（表格 520 − 表头 36），一屏正好 10 行
 */
const HEAD_HEIGHT = 36
const ROW_HEIGHT = 46
const ROW_TOP = 46
const BODY_HEIGHT = 484

/**
 * 每页条数三档（测试清单 P2-04：10 / 20 / 50）。
 * ★ 10 必须留在选项里：select 显示的是当前值，当前值不在 options 里时浏览器会显示空白。
 */
const PAGE_SIZE_OPTIONS = [10, 20, 50]

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
      // 默认一屏 10 行（设计稿表体 457px / 行高 46），可在 10 / 20 / 50 之间切换；
      // 20 / 50 时行数超过一屏，由 .home-plot__scroll 滚动承载（见 canvasHeight）
      pageSize: PAGE_SIZE_OPTIONS[0],
      pageSizeOptions: PAGE_SIZE_OPTIONS,
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
    },
    /**
     * 行画布高度：只占一屏时就是可视高度；20 / 50 行时把画布撑高，交给滚动视口，
     * 不再靠 overflow:hidden 硬裁（裁掉的部分用户根本看不到，会以为「只显示 10 行」）。
     */
    canvasHeight () {
      const rowsBottom = ROW_TOP - HEAD_HEIGHT + this.pageRows.length * ROW_HEIGHT
      return `${Math.max(BODY_HEIGHT, rowsBottom)}px`
    }
  },
  watch: {
    /**
     * 行数变了（切页签、父组件重新查询）之后当前页可能越界，
     * 夹回最后一页，避免出现「共 N 条却停在一页空白」。
     * 盯 pageCount 而不是 rows：每页条数变化时也走同一次兜底。
     */
    pageCount () {
      this.clampPage()
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
      this.resetScroll()
    },
    /** 行的 top：设计稿首行相对表格是 46，进了滚动视口还要再减掉表头那一截（36） */
    rowTop (index) {
      return `${ROW_TOP - HEAD_HEIGHT + index * ROW_HEIGHT}px`
    },
    /**
     * 切换每页条数。
     * ★ 必须回到第 1 页：否则会停在新页大小下已不存在的页码上，出现一页空白。
     */
    handlePageSizeChange (event) {
      const next = Number((event && event.target && event.target.value) || 0)
      if (!next || this.pageSizeOptions.indexOf(next) < 0 || next === this.pageSize) return
      this.pageSize = next
      this.page = 1
      this.jump = null
      this.resetScroll()
    },
    /** 换页 / 换每页条数后把行区域滚回顶部，别停在上次滚到的位置 */
    resetScroll () {
      this.$nextTick(() => {
        const scroller = this.$refs.scroll
        if (scroller) scroller.scrollTop = 0
      })
    },
    /** 页码越界兜底：数据变少或每页条数变大后，不许停在空页上 */
    clampPage () {
      const next = Math.min(this.pageCount, Math.max(1, this.page))
      if (next !== this.page) this.page = next
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
  // 表格容器：表头 36 + 行区域可视 484 = 520，落在设计稿的表体区（表头 72、分页 598）之内。
  // ⚠ 表头 36 / 行高 46 / 可视高 484 必须与脚本里的 HEAD_HEIGHT、ROW_HEIGHT、BODY_HEIGHT 逐个对上；
  //   行区域改由下面的 &__scroll 滚动承载，20 / 50 条时多出来的行不会再被 overflow:hidden 裁掉
  //   （旧版只按 10 行写死，切到 20 条用户仍只看到 10 行，会以为数据丢了）。
  &__table {
    position: absolute;
    left: 25px;
    top: 72px;
    width: 1367px;
    height: 520px;
    overflow: hidden;
  }

  // 行区域的滚动视口：贴在表头下方，高度 = 520 − 36。
  // 用 auto 而不是 hidden —— 每页 10 条时正好一屏（不出滚动条），20 / 50 条时靠滚轮看全。
  // 滚动条用项目统一的 6px 细条，暗色下不抢视线。
  &__scroll {
    position: absolute;
    left: 0;
    top: 36px;
    width: 100%;
    height: 484px;
    overflow-x: hidden;
    overflow-y: auto;
    .screen-scrollbar(6px);
  }

  // 行的定位画布：高度由脚本按当前行数算（canvasHeight），绝对定位的行都挂在它上面。
  // ★ 宽度必须与 &__table 一样写死 1367：出现滚动条时视口内容区会窄 6px，
  //   若画布用 100% 撑，按百分比定位的列会整体左移约 5px，与表头对不齐。
  &__canvas {
    position: relative;
    width: 1367px;
  }

  &__head,
  &__body {
    left: 0;
    display: block;
    width: 100%;
    pointer-events: none;
  }

  &__head {
    // 表头留在滚动视口**外面**，所以仍是文档流里的普通块，自己顶住那 36px
    position: relative;
    height: 36px;
  }

  &__body {
    // 表体底图只负责一屏的底纹：行数超出后不再把它拉高，
    // 拉伸会把图里隐约的分隔线糊成大片渐变；多出来的行直接落在弹窗底图上，观感一致
    position: absolute;
    top: 0;
    height: 484px;
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

  // 每页条数：外层仍按设计稿占 80×36，里面是原生 select
  &__pager-size {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 80px;
    height: 36px;
    margin-left: 24px;
  }

  // 原生 select：去掉系统外观，只留文字（右侧箭头用设计稿的 caret 图，见下一条）。
  // 不用浮层组件是因为这里只有三档、且弹窗整个在缩放画布内，原生控件最稳。
  &__pager-size-select {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    padding: 0 18px 0 0;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    color: #ffffff;
    text-align: center;
    text-align-last: center;
    background: transparent;
    border: 0;
    outline: none;
    appearance: none;
    cursor: pointer;
    .screen-focus-ring();

    // 展开的列表由系统绘制，必须显式给深色字：否则可能跟随上面的白色文字变成白底白字
    option {
      color: #0b1e30;
      background: #ffffff;
    }
  }

  &__pager-size-caret {
    position: absolute;
    right: 10px;
    top: 50%;
    width: 8px;
    height: 5px;
    margin-top: -2.5px;
    pointer-events: none;
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
