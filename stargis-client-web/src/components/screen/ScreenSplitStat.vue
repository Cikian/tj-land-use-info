<template>
  <!--
    ScreenSplitStat 分级占比统计
    --------------------------------
    设计稿中的「市级 215宗 26% / 区级 602宗 74%」：
    多列网格，每列第一行是 [标签 · 数值 · 占比]，第二行是该列的横向进度条。

    items: [{ label, value, unit, percent, tone }]
    tone: green（默认，对应设计稿「市级」）/ cyan（「区级」）/ amber / muted
  -->
  <ul class="screen-split-stat" :style="{ '--split-columns': columns }">
    <li
      v-for="(item, index) in normalized"
      :key="item.label || index"
      class="screen-split-stat__item"
    >
      <span class="screen-split-stat__label">{{ item.label }}</span>
      <span class="screen-split-stat__value">
        {{ item.display }}<em v-if="item.unit">{{ item.unit }}</em>
      </span>
      <span class="screen-split-stat__percent">{{ item.percent }}%</span>
      <span
        class="screen-split-stat__track"
        role="img"
        :aria-label="`${item.label} ${item.display}${item.unit}，占比 ${item.percent}%`"
      >
        <i
          class="screen-split-stat__fill"
          :class="`is-${item.tone}`"
          :style="{ width: barReady ? `${item.percent}%` : '0%' }"
        />
      </span>
    </li>
  </ul>
</template>

<script>
import { clamp, formatNumber, prefersReducedMotion, raf, toNumber } from './utils'

export default {
  name: 'ScreenSplitStat',
  props: {
    /** 数据项：[{ label, value, percent, unit, tone }] */
    items: { type: Array, default: () => [] },
    /** 每行列数 */
    columns: { type: Number, default: 2 },
    /** 全局单位，item 未指定时生效 */
    unit: { type: String, default: '宗' },
    /** 数值小数位 */
    precision: { type: Number, default: 0 },
    /** 进度条是否入场动画 */
    animated: { type: Boolean, default: true },
  },
  data () {
    return {
      barReady: false,
    }
  },
  computed: {
    normalized () {
      return this.items.map((item) => {
        const percent = clamp(toNumber(item.percent), 0, 100)
        return {
          label: item.label,
          unit: item.unit === undefined ? this.unit : item.unit,
          percent: Number(percent.toFixed(2)),
          display: formatNumber(item.value, item.precision === undefined ? this.precision : item.precision),
          tone: item.tone || 'green',
        }
      })
    },
  },
  mounted () {
    if (!this.animated || prefersReducedMotion()) {
      this.barReady = true
      return
    }
    // 下一帧再置为 true，触发 width 过渡
    this.$nextTick(() => {
      raf(() => {
        this.barReady = true
      })
    })
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-split-stat {
  display: grid;
  grid-template-columns: repeat(var(--split-columns, 2), minmax(0, 1fr));
  gap: 12px 20px;
  margin: 0;
  padding: 0;
  list-style: none;

  &__item {
    display: grid;
    grid-template-columns: auto 1fr auto;
    align-items: baseline;
    column-gap: 8px;
    row-gap: 7px;
    min-width: 0;
    font-size: var(--screen-font-sm);
  }

  &__label {
    grid-column: 1;
    color: var(--screen-text-sub);
    white-space: nowrap;
  }

  &__value {
    grid-column: 2;
    min-width: 0;
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);
    .screen-ellipsis();

    em {
      margin-left: 1px;
      font-style: normal;
      font-size: var(--screen-font-xs);
      color: var(--screen-text-mute);
    }
  }

  &__percent {
    grid-column: 3;
    text-align: right;
    font-variant-numeric: tabular-nums;
    color: var(--screen-accent-soft);
    white-space: nowrap;
  }

  &__track {
    // 注意：不要写成 grid-column: 1 / -1，Less 会把它当作除法算成 -1
    grid-column-start: 1;
    grid-column-end: -1;
    position: relative;
    height: 6px;
    border-radius: var(--screen-radius-pill);
    background: var(--screen-bar-track);
    overflow: hidden;
  }

  &__fill {
    display: block;
    height: 100%;
    border-radius: inherit;
    transition: width 700ms var(--screen-ease);

    // 设计稿：市级为绿色，区级为青色
    &.is-green {
      background-image: linear-gradient(90deg, var(--screen-viz-green) 0%, #1f9e4a 100%);
    }

    &.is-cyan {
      background-image: linear-gradient(90deg, var(--screen-viz-cyan) 0%, #1f9aa0 100%);
    }

    &.is-amber {
      background-image: linear-gradient(90deg, var(--screen-viz-amber) 0%, #b9761c 100%);
    }

    &.is-muted {
      background-image: linear-gradient(
        90deg,
        var(--screen-bar-muted-from) 0%,
        var(--screen-bar-muted-to) 100%
      );
    }
  }
}
</style>
