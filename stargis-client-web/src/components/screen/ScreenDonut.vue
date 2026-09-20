<template>
  <!--
    ScreenDonut 环形占比图
    --------------------------------
    设计稿左侧「出让地块情况统计」中的双层环形图：中心显示主数值，
    右侧为带文字说明的图例（颜色之外必须有文字，避免仅依赖颜色传达信息）。

    data: [{ name, value, unit, color }]
  -->
  <div class="screen-donut" :class="{ 'is-legend-right': legendPosition === 'right' }">
    <div class="screen-donut__chart" :style="{ width: `${size}px`, height: `${size}px` }">
      <svg
        :width="size"
        :height="size"
        :viewBox="`0 0 ${size} ${size}`"
        role="img"
        :aria-label="ariaLabel"
      >
        <!-- 底环 -->
        <circle
          :cx="center"
          :cy="center"
          :r="radius"
          fill="none"
          :stroke="trackColor"
          :stroke-width="thickness"
        />
        <!-- 数据弧 -->
        <g :transform="`rotate(-90 ${center} ${center})`">
          <circle
            v-for="(seg, index) in segments"
            :key="seg.key"
            :cx="center"
            :cy="center"
            :r="radius"
            fill="none"
            :stroke="seg.color"
            :stroke-width="thickness"
            :stroke-linecap="linecap"
            :stroke-dasharray="seg.dashArray"
            :stroke-dashoffset="seg.dashOffset"
            :style="seg.style"
          />
        </g>
      </svg>
      <div class="screen-donut__center">
        <slot>
          <span class="screen-donut__value">
            <screen-count-up :value="resolvedCenterValue" :precision="centerPrecision" />
          </span>
          <span v-if="centerLabel" class="screen-donut__label">{{ centerLabel }}</span>
        </slot>
      </div>
    </div>

    <ul v-if="legend" class="screen-donut__legend">
      <li v-for="seg in segments" :key="`legend-${seg.key}`" class="screen-donut__legend-item">
        <i class="screen-donut__dot" :style="{ background: seg.color }" aria-hidden="true" />
        <span class="screen-donut__name">{{ seg.name }}</span>
        <span class="screen-donut__legend-value">{{ seg.display }}<em>{{ seg.unit }}</em></span>
        <span class="screen-donut__legend-percent">{{ seg.percent }}%</span>
      </li>
    </ul>
  </div>
</template>

<script>
import ScreenCountUp from './ScreenCountUp'
import { clamp, formatNumber, prefersReducedMotion, raf, toNumber } from './utils'

// 设计稿配色：首项（市级）薄荷绿，次项（区级）蓝
const DEFAULT_COLORS = [
  'var(--screen-viz-mint)',
  'var(--screen-viz-blue)',
  'var(--screen-viz-cyan)',
  'var(--screen-viz-green)',
]

export default {
  name: 'ScreenDonut',
  components: { ScreenCountUp },
  props: {
    /** 数据项：[{ name, value, unit, color }] */
    data: { type: Array, default: () => [] },
    /** 环形直径（px） */
    size: { type: Number, default: 150 },
    /** 环宽（px） */
    thickness: { type: Number, default: 14 },
    /** 扇区之间的留白，占周长百分比 */
    gap: { type: Number, default: 2.4 },
    /** 中心主数值，缺省取 data 首项 value */
    centerValue: { type: [Number, String], default: null },
    /** 中心数值下方说明 */
    centerLabel: { type: String, default: '' },
    /** 中心数值小数位 */
    centerPrecision: { type: Number, default: 0 },
    /** 图例占比小数位（设计稿为整数） */
    percentPrecision: { type: Number, default: 0 },
    /** 单位，数据项未指定时生效 */
    unit: { type: String, default: '宗' },
    /** 是否展示图例 */
    legend: { type: Boolean, default: true },
    /** 图例位置：right 右侧 / bottom 下方 */
    legendPosition: { type: String, default: 'right' },
    /** 弧线端点：round 圆角 / butt 平角 */
    linecap: { type: String, default: 'round' },
    /** 底环颜色 */
    trackColor: { type: String, default: 'var(--screen-bar-track)' },
    /** 自定义配色 */
    colors: { type: Array, default: () => DEFAULT_COLORS },
    /** 是否入场动画 */
    animated: { type: Boolean, default: true },
  },
  data () {
    return {
      ready: false,
    }
  },
  computed: {
    center () {
      return this.size / 2
    },
    radius () {
      return (this.size - this.thickness) / 2
    },
    circumference () {
      return 2 * Math.PI * this.radius
    },
    total () {
      return this.data.reduce((sum, item) => sum + toNumber(item.value), 0)
    },
    /** 中心数值缺省取首项，避免使用方必须重复传参 */
    resolvedCenterValue () {
      if (this.centerValue !== null && this.centerValue !== undefined) return this.centerValue
      return this.data.length ? toNumber(this.data[0].value) : 0
    },
    segments () {
      const c = this.circumference
      const total = this.total || 1
      const gapLength = (clamp(this.gap, 0, 20) / 100) * c
      let accumulated = 0

      return this.data.map((item, index) => {
        const value = toNumber(item.value)
        const fraction = value / total
        const fullLength = fraction * c
        const visibleLength = Math.max(fullLength - gapLength, 0.5)
        const startOffset = -accumulated
        accumulated += fullLength

        const shown = this.ready ? visibleLength : 0
        const color = item.color || this.colors[index % this.colors.length]

        return {
          key: item.name || index,
          name: item.name,
          color,
          unit: item.unit === undefined ? this.unit : item.unit,
          display: formatNumber(value, item.precision === undefined ? 0 : item.precision),
          percent: Number((fraction * 100).toFixed(this.percentPrecision)),
          dashArray: `${shown} ${Math.max(c - shown, 0)}`,
          dashOffset: startOffset,
          style: {
            transition: this.animated && !prefersReducedMotion()
              ? `stroke-dasharray 720ms var(--screen-ease) ${index * 120}ms`
              : 'none',
          },
        }
      })
    },
    ariaLabel () {
      if (!this.data.length) return '环形占比图，暂无数据'
      const parts = this.segments.map((seg) => `${seg.name} ${seg.display}${seg.unit}，占 ${seg.percent}%`)
      return `环形占比图：${parts.join('；')}`
    },
  },
  mounted () {
    if (!this.animated || prefersReducedMotion()) {
      this.ready = true
      return
    }
    this.$nextTick(() => {
      raf(() => {
        this.ready = true
      })
    })
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-donut {
  display: flex;
  align-items: center;
  gap: var(--screen-space-4);
  min-width: 0;

  &__chart {
    position: relative;
    flex: 0 0 auto;
  }

  &__center {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    pointer-events: none;
  }

  &__value {
    .screen-number-font(clamp(20px, 1.7vw, 30px), 700);
    color: var(--screen-number);
    text-shadow: 0 0 12px rgba(98, 240, 196, 0.35);
  }

  &__label {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__legend {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 10px;
    flex: 1 1 auto;
    min-width: 0;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__legend-item {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    min-width: 0;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
  }

  &__dot {
    flex: 0 0 auto;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    box-shadow: 0 0 6px currentColor;
  }

  &__name {
    flex: 0 0 auto;
    min-width: 30px;
    .screen-ellipsis();
  }

  &__legend-value {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-accent-soft);

    em {
      margin-left: 1px;
      font-style: normal;
      font-size: var(--screen-font-xs);
      color: var(--screen-text-mute);
    }
  }

  &__legend-percent {
    flex: 0 0 auto;
    margin-left: auto;
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-mute);
  }

  // 图例在下方时改为两列网格，避免大屏窄列里换行错位
  &:not(.is-legend-right) {
    flex-direction: column;
    gap: var(--screen-space-3);

    .screen-donut__legend {
      flex-direction: row;
      flex-wrap: wrap;
      justify-content: center;
      gap: var(--screen-space-2) var(--screen-space-4);
    }

    .screen-donut__legend-percent {
      margin-left: 0;
    }
  }
}
</style>
