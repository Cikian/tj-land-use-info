<template>
  <!--
    ScreenLineChart 趋势折线 / 面积图
    --------------------------------
    「档案统计」面板里的年度趋势图（例如各年度归档数量）。手写 SVG，不引入图表库。

    为什么按真实像素宽度绘制而不是拉伸 viewBox：
      大屏里同一张图会出现在宽度不同的面板中，若用固定 viewBox + width:100%，
      preserveAspectRatio="none" 会把线宽、圆点一起非等比拉伸变形；
      这里改为监听容器宽度，用真实 px 作为 <svg width/height>，图形永不失真。

    用法：
      <screen-line-chart
        label="档案年度趋势"
        :data="[{ name: '2024', value: 12 }, { name: '2025', value: 30 }]"
        :height="200"
        unit="件"
        tone="cyan"
      />

    无障碍：
      - <svg> 带 role="img" + 概述性 aria-label（x/y 极值、数据点数）；
      - 具体数值另以视觉隐藏的 <ul> 逐条列出，读屏能听到真实数字而不只是概述；
      - 颜色仅作辅助，数值同时以文本（y 轴刻度 + 隐藏列表）呈现。
  -->
  <div ref="root" class="screen-line-chart">
    <template v-if="hasData">
      <svg
        class="screen-line-chart__svg"
        :width="svgWidth"
        :height="svgHeight"
        :viewBox="`0 0 ${svgWidth} ${svgHeight}`"
        role="img"
        :aria-label="ariaLabel"
      >
        <defs>
          <linearGradient :id="gradientId" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" :stop-color="colors.from" stop-opacity="0.34" />
            <stop offset="100%" :stop-color="colors.to" stop-opacity="0" />
          </linearGradient>
        </defs>

        <!-- y 轴刻度线与刻度值（4 条） -->
        <g v-if="showAxis">
          <template v-for="(tick, index) in yTicks">
            <line
              :key="`line-${index}`"
              :x1="pad.left"
              :y1="tick.y"
              :x2="svgWidth - pad.right"
              :y2="tick.y"
              stroke="var(--screen-border-soft)"
              stroke-width="1"
            />
            <text
              :key="`label-${index}`"
              class="screen-line-chart__axis-text"
              :x="pad.left - 6"
              :y="tick.y"
              text-anchor="end"
              dominant-baseline="middle"
            >
              {{ tick.label }}
            </text>
          </template>
        </g>

        <!-- 面积 -->
        <polygon
          v-if="showArea"
          :points="areaPoints"
          :fill="`url(#${gradientId})`"
          :opacity="areaOpacity"
          :style="areaStyle"
        />

        <!-- 折线：直线段 polyline 比不可靠的曲线拟合更稳 -->
        <polyline
          :points="linePoints"
          fill="none"
          :stroke="colors.stroke"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          :stroke-dasharray="lineDashArray"
          :stroke-dashoffset="lineDashOffset"
          :style="lineStyle"
        />

        <!-- 数据点 -->
        <template v-if="showPoints">
          <circle
            v-for="point in points"
            :key="`dot-${point.key}`"
            :cx="point.x"
            :cy="point.y"
            r="3.2"
            fill="var(--screen-panel-bg-solid)"
            :stroke="colors.stroke"
            stroke-width="1.6"
          />
        </template>

        <!-- x 轴标签 -->
        <g>
          <text
            v-for="point in points"
            :key="`x-${point.key}`"
            class="screen-line-chart__axis-text"
            :x="point.x"
            :y="svgHeight - 8"
            :text-anchor="rotateLabels ? 'end' : 'middle'"
            :transform="rotateLabels ? `rotate(-38 ${point.x} ${svgHeight - 8})` : undefined"
          >
            <title>{{ point.fullName }}</title>
            {{ point.label }}
          </text>
        </g>
      </svg>

      <!-- 数值列表：给读屏提供逐条真实数字（含单位） -->
      <ul class="screen-visually-hidden">
        <li v-for="point in points" :key="`a11y-${point.key}`">
          {{ point.fullName }} {{ point.display }}{{ unit }}
        </li>
      </ul>
    </template>

    <div v-else class="screen-line-chart__empty" :style="{ height: `${svgHeight}px` }">
      {{ emptyText }}
    </div>
  </div>
</template>

<script>
import { formatNumber, prefersReducedMotion, raf, toNumber, uid } from './utils'

/** 画布内边距：左侧留给 y 轴刻度值，底部留给 x 轴标签 */
const PAD = { left: 40, right: 16, top: 16, bottom: 26 }

/** 单点 / 全零数据时折线所处的垂直位置（画布高度的百分比） */
const FLAT_RATIO = 0.55

/** 色调 -> 线色 / 渐变止色 */
const TONE_COLORS = {
  accent: { stroke: 'var(--screen-accent)', from: 'var(--screen-accent)', to: 'var(--screen-viz-mint)' },
  cyan: { stroke: 'var(--screen-viz-cyan)', from: 'var(--screen-viz-cyan)', to: 'var(--screen-viz-blue)' },
  warning: { stroke: 'var(--screen-viz-amber)', from: 'var(--screen-viz-amber)', to: 'var(--screen-viz-amber)' },
}

export default {
  name: 'ScreenLineChart',
  props: {
    /** 数据项：[{ name, value }]，name 为 x 轴标签 */
    data: { type: Array, default: () => [] },
    /** 系列名称，用于拼装读屏概述（例如「档案年度趋势」） */
    label: { type: String, default: '' },
    /** 绘图区高度（px，不含组件内边距） */
    height: { type: Number, default: 200 },
    /** 数值单位，出现在视觉隐藏的数值列表里，供读屏读到「12 件」这样的完整读法 */
    unit: { type: String, default: '' },
    /** 数值小数位 */
    precision: { type: Number, default: 0 },
    /** 色调：accent 青绿 / cyan 青蓝 / warning 预警橙 */
    tone: { type: String, default: 'accent' },
    /** 是否绘制线下方面积 */
    showArea: { type: Boolean, default: true },
    /** 是否绘制数据点 */
    showPoints: { type: Boolean, default: true },
    /** 是否绘制 y 轴刻度线与刻度值 */
    showAxis: { type: Boolean, default: true },
    /** 空数据文案 */
    emptyText: { type: String, default: '暂无数据' },
    /** 是否入场动画（画线 + 面积淡入） */
    animated: { type: Boolean, default: true },
  },
  data () {
    return {
      /** 渐变 id 必须逐实例隔离，同页多图不能复用 */
      gradientId: uid('screen-line'),
      /** 容器实测宽度（px），由 ResizeObserver / resize 事件维护 */
      width: 0,
      ready: false,
      /** 保存观察器与监听函数，beforeDestroy 里必须清理 */
      observer: null,
      resizeHandler: null,
      pad: PAD,
    }
  },
  computed: {
    colors () {
      return TONE_COLORS[this.tone] || TONE_COLORS.accent
    },
    /** 读屏概述里的系列名，缺省用通用说法 */
    seriesLabel () {
      return this.label || '折线趋势图'
    },
    /** 归一化后的数据点：数值、标题、展示文本 */
    normalized () {
      return this.data.map((item, index) => {
        const value = toNumber(item && item.value)
        const name = item && item.name !== undefined && item.name !== null ? String(item.name) : ''
        return {
          key: `${name || 'i'}-${index}`,
          name,
          value,
          display: formatNumber(value, this.precision),
        }
      })
    },
    hasData () {
      return this.normalized.length > 0
    },
    maxValue () {
      return this.normalized.reduce((max, item) => Math.max(max, item.value), 0)
    },
    svgHeight () {
      return Math.max(toNumber(this.height, 200), 120)
    },
    /** 容器实测宽度；未测量时退化为 0，避免出现负的绘图宽度 */
    svgWidth () {
      return Math.max(Math.round(this.width), 0)
    },
    /** 绘图区实际可用宽高（负数一律夹到 0，杜绝 NaN） */
    plotWidth () {
      return Math.max(this.svgWidth - PAD.left - PAD.right, 0)
    },
    plotHeight () {
      return Math.max(this.svgHeight - PAD.top - PAD.bottom, 0)
    },
    /** 单点居中：x = 绘图区水平中点 */
    singleX () {
      return PAD.left + this.plotWidth / 2
    },
    /** 最大值为 0（含全零、负值）时统一落在 55% 高度处，不贴边也不抖动 */
    flatY () {
      return PAD.top + this.plotHeight * FLAT_RATIO
    },
    /** 极值：动画与刻度都基于它，避免把 0 当作有效高度 */
    scaleMax () {
      return this.maxValue > 0 ? this.maxValue : 1
    },
    points () {
      const count = this.normalized.length
      if (!count) return []
      const step = count > 1 ? this.plotWidth / (count - 1) : 0

      return this.normalized.map((item, index) => {
        const x = count === 1 ? this.singleX : PAD.left + index * step
        const ratio = this.maxValue > 0 ? item.value / this.maxValue : 0
        const y = this.maxValue > 0 ? PAD.top + this.plotHeight * (1 - ratio) : this.flatY
        const label = item.name.length > 6 ? `${item.name.slice(0, 5)}…` : item.name
        return {
          key: item.key,
          fullName: item.name,
          label,
          display: item.display,
          x: Number(x.toFixed(2)),
          y: Number(y.toFixed(2)),
        }
      })
    },
    linePoints () {
      return this.points.map((point) => `${point.x},${point.y}`).join(' ')
    },
    areaPoints () {
      if (!this.points.length) return ''
      const first = this.points[0]
      const last = this.points[this.points.length - 1]
      const baseY = PAD.top + this.plotHeight
      return `${first.x},${baseY} ${this.linePoints} ${last.x},${baseY}`
    },
    /** 折线总长度，用于 stroke-dasharray 画线动画；带兜底以免为 0 */
    lineLength () {
      let total = 0
      for (let i = 1; i < this.points.length; i += 1) {
        const prev = this.points[i - 1]
        const cur = this.points[i]
        total += Math.sqrt(Math.pow(cur.x - prev.x, 2) + Math.pow(cur.y - prev.y, 2))
      }
      return Math.ceil(total) || 1
    },
    /** 折线在 12 个点以上时旋转标签，避免中文标签互相重叠 */
    rotateLabels () {
      return this.normalized.length > 12
    },
    yTicks () {
      const baseY = PAD.top + this.plotHeight
      // 最大值非正时所有刻度都是 0，避免出现「最大值为 0 却标 1」的误导性刻度
      const hasScale = this.maxValue > 0
      return Array.from({ length: 5 }).map((item, index) => {
        const ratio = index / 4
        return {
          y: Number((baseY - this.plotHeight * ratio).toFixed(2)),
          label: formatNumber(hasScale ? this.scaleMax * ratio : 0, this.precision),
        }
      })
    },
    lineDashArray () {
      // 折线画入动画是否可行：允许动效、未开启减少动态、至少两个点、且宽度已测量
      if (!this.canAnimateLine) return undefined
      return `${this.lineLength} ${this.lineLength}`
    },
    lineDashOffset () {
      if (!this.canAnimateLine) return undefined
      return this.lineDrawn ? 0 : this.lineLength
    },
    lineStyle () {
      // 首帧宽度可能还未测量（绘图区为 0），此时不挂过渡，避免用错误的线长播放动画
      if (!this.canAnimateLine || !this.svgWidth) return null
      return { transition: 'stroke-dashoffset 900ms var(--screen-ease)' }
    },
    areaStyle () {
      // 单点没有可播放的折线，面积直接呈现终态，避免「永远半透明」
      if (!this.canAnimateLine) return null
      return { transition: 'opacity 900ms var(--screen-ease) 160ms' }
    },
    /** 折线画入动画是否可行：允许动效、未开启减少动态、至少两个点、且宽度已测量 */
    canAnimateLine () {
      return this.animated && !prefersReducedMotion() && this.points.length > 1 && this.svgWidth > 0
    },
    /** 面积淡入：动画未开始前为 0，其余情况按终态 */
    areaOpacity () {
      if (!this.canAnimateLine || this.ready) return 1
      return 0
    },
    /** 动画开关：animated=false 或系统偏好「减少动态效果」时直接画终态 */
    lineDrawn () {
      return !this.animated || prefersReducedMotion() || this.ready
    },
    ariaLabel () {
      if (!this.normalized.length) return `${this.seriesLabel}，${this.emptyText}`
      const parts = this.normalized.map((item) => `${item.name} ${item.display}`)
      return `${this.seriesLabel}：${parts.join('，')}，共 ${this.normalized.length} 个数据点，最大 ${formatNumber(this.maxValue, this.precision)}`
    },
  },
  watch: {
    // 数据变化后重新播放一次画线动画（仅在允许动效时）
    data () {
      if (!this.animated || prefersReducedMotion()) {
        this.ready = true
        return
      }
      this.ready = false
      this.$nextTick(() => {
        raf(() => {
          this.ready = true
        })
      })
    },
  },
  mounted () {
    this.measure()
    this.bindResize()
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
  beforeDestroy () {
    this.unbindResize()
  },
  methods: {
    /** 读取容器实际宽度；组件被隐藏时宽度为 0，宽度变化会由观察器补触发 */
    measure () {
      const el = this.$refs.root
      if (!el) return
      const next = el.clientWidth || el.getBoundingClientRect().width || 0
      if (next !== this.width) this.width = next
    },
    bindResize () {
      if (typeof window === 'undefined') return
      if (typeof window.ResizeObserver === 'function') {
        this.observer = new window.ResizeObserver(() => {
          this.measure()
        })
        this.observer.observe(this.$refs.root)
        return
      }
      // 兜底：老浏览器无 ResizeObserver 时监听窗口尺寸变化
      this.resizeHandler = () => {
        this.measure()
      }
      window.addEventListener('resize', this.resizeHandler)
    },
    unbindResize () {
      if (this.observer) {
        this.observer.disconnect()
        this.observer = null
      }
      if (this.resizeHandler && typeof window !== 'undefined') {
        window.removeEventListener('resize', this.resizeHandler)
        this.resizeHandler = null
      }
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-line-chart {
  position: relative;
  display: block;
  width: 100%;
  min-width: 0;

  &__svg {
    display: block;
    // 宽度跟随实测像素，避免容器尺寸与测量值短暂不一致时被等比缩放变形
    max-width: 100%;
    overflow: visible;
  }

  &__axis-text {
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    font-variant-numeric: tabular-nums;
    fill: var(--screen-text-mute);
  }

  &__empty {
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: var(--screen-font-sm);
    .screen-placeholder();
  }
}

// 仅供读屏使用：视觉隐藏但仍参与无障碍树
.screen-visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
