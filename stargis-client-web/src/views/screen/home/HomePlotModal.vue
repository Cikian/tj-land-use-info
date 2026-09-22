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
      属设计稿笔误；这里保留 10 列的位置不变，把重复列改为「可研批复未完成」
      并在末尾补「操作」列，列宽与间距仍与设计稿一致。

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
        <img class="home-plot__body" :src="hf.plotTableBody" alt="" aria-hidden="true" />

        <span
          v-for="col in columns"
          :key="col.key"
          class="home-plot__th"
          :style="{ left: `${colLeft(col.key)}px` }"
        >{{ col.title }}</span>

        <div
          v-for="(row, index) in rows"
          :key="index"
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
          <span class="home-plot__td" :style="{ left: `${colLeft('landNo')}px` }">{{ row.landNo }}</span>
          <span class="home-plot__td" :style="{ left: `${colLeft('plotName')}px` }">{{ row.plotName }}</span>
          <span class="home-plot__td" :style="{ left: `${colLeft('landAcquisition')}px` }">{{ row.landAcquisition }}</span>
          <span class="home-plot__td" :style="{ left: `${colLeft('feasibility')}px` }">{{ row.feasibility }}</span>
          <span class="home-plot__td" :style="{ left: `${colLeft('preliminaryDesign')}px` }">{{ row.preliminaryDesign }}</span>
          <span class="home-plot__td" :style="{ left: `${colLeft('fund')}px` }">{{ row.fund }}</span>
          <span class="home-plot__td" :style="{ left: `${colLeft('notStarted')}px` }">{{ row.notStarted }}</span>
          <span class="home-plot__td" :style="{ left: `${colLeft('notCompleted')}px` }">{{ row.notCompleted }}</span>
          <span class="home-plot__td" :style="{ left: `${colLeft('notHandedOver')}px` }">{{ row.notHandedOver }}</span>
          <img
            class="home-plot__action"
            :src="hf.actionView"
            alt="查看"
            role="button"
            tabindex="0"
            @click="$emit('row-action', row)"
          />
        </div>
      </div>

      <!-- 分页 -->
      <div class="home-plot__pager">
        <span class="home-plot__pager-total">共{{ data.total }}条信息</span>
        <button type="button" class="home-plot__page is-nav" @click="go(page - 1)">&lt;</button>
        <button
          v-for="n in 5"
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
import { demoPlotWarning } from '../mock'

/** 列横向偏移（取自高保真 CSS 的 left 值，相对弹窗左上角） */
const COL_LEFT = {
  landNo: 46,
  plotName: 227,
  landAcquisition: 493,
  feasibility: 605,
  preliminaryDesign: 717,
  fund: 829,
  notStarted: 940,
  notCompleted: 1038,
  notHandedOver: 1108,
  action: 1178,
}

/** 表头列（高保真底稿把「项建批复未完成」复制了两列，属笔误，这里去重并补「操作」列） */
const COLUMNS = [
  { key: 'landNo', title: '出让宗地编号' },
  { key: 'plotName', title: '地块名称' },
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
  name: 'HomePlotModal',
  props: {
    /**
     * 弹窗数据：{ title, total, rows[] }。
     * 后端暂未提供「地块预警信息」粒度的明细，默认用高保真兜底数据。
     */
    data: { type: Object, default: () => demoPlotWarning },
  },
  data () {
    return {
      hf,
      columns: COLUMNS,
      page: 1,
      pageSize: 18,
      jump: null,
    }
  },
  computed: {
    rows () {
      return (this.data && this.data.rows) || []
    },
  },
  mounted () {
    document.addEventListener('keydown', this.handleKey)
  },
  beforeDestroy () {
    document.removeEventListener('keydown', this.handleKey)
  },
  methods: {
    colLeft (key) {
      return COL_LEFT[key]
    },
    go (n) {
      if (typeof n !== 'number' || Number.isNaN(n)) return
      this.page = Math.min(5, Math.max(1, n))
    },
    handleKey (e) {
      if (e.key === 'Escape') this.$emit('close')
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.home-plot {
  position: absolute;
  left: 0;
  top: 0;
  width: 1920px;
  height: 1080px;
  z-index: 20;

  &__mask {
    position: absolute;
    inset: 0;
    background: rgba(2, 7, 18, 0.55);
    pointer-events: auto;
  }

  &__dialog {
    position: absolute;
    left: 325px;
    top: 208px;
    width: 1270px;
    height: 663px;
  }

  &__bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 1270px;
    height: 663px;
    display: block;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    left: 81px;
    top: 20px;
    margin: 0;
    font-size: 18px;
    font-weight: 500;
    line-height: 18px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__close {
    position: absolute;
    left: 1235px;
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
  &__table {
    position: absolute;
    left: 0;
    top: 0;
    width: 1270px;
    height: 663px;
  }

  &__head {
    position: absolute;
    left: 25px;
    top: 72px;
    width: 1217px;
    height: 36px;
    display: block;
    pointer-events: none;
  }

  &__body {
    position: absolute;
    left: 25px;
    top: 118px;
    width: 1219px;
    height: 457px;
    display: block;
    pointer-events: none;
  }

  &__th {
    position: absolute;
    top: 83px;
    font-size: 14px;
    line-height: 14px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__tr {
    position: absolute;
    left: 26px;
    width: 1219px;
    height: 46px;
  }

  &__tr-bg {
    position: absolute;
    left: -1px;
    top: 0;
    width: 1219px;
    height: 46px;
    display: block;
    pointer-events: none;
  }

  &__td {
    position: absolute;
    top: 16px;
    font-size: 13px;
    line-height: 14px;
    color: #66afd4;
    white-space: nowrap;
  }

  &__action {
    position: absolute;
    left: 1178px;
    top: 16px;
    width: 14px;
    height: 14px;
    cursor: pointer;
    .screen-focus-ring();
  }

  /* ---------------- 分页 ---------------- */
  &__pager {
    position: absolute;
    left: 480px;
    top: 598px;
    display: flex;
    align-items: center;
    height: 36px;
    font-size: 14px;
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
    font-size: 14px;
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
    font-size: 14px;
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
      font-size: 14px;
      color: #ffffff;
      text-align: center;
      background: transparent;
      border: 0;
      outline: none;
    }
  }
}
</style>
