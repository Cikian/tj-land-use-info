<template>
  <!--
    ScreenGauge 仪表盘进度
    --------------------------------
    设计稿右侧「需要落实配套」中的完成率表盘：底部开口的圆弧进度 + 中心百分比。

    无障碍：数值以文本形式同时呈现在中心，颜色仅作辅助。
  -->
  <div class="screen-gauge" :style="{ width: `${size}px`, height: `${svgHeight}px` }">
    <svg
      :width="size"
      :height="svgHeight"
      :viewBox="`0 0 ${size} ${svgHeight}`"
      role="img"
      :aria-label="ariaLabel"
    >
      <defs>
        <linearGradient :id="gradientId" x1="0%" y1="100%" x2="100%" y2="0%">
          <stop offset="0%" :stop-color="fromColor" />
          <stop offset="100%" :stop-color="toColor" />
        </linearGradient>
      </defs>

      <!-- 轨道 -->
      <path
        :d="arcPath"
        fill="none"
        :stroke="trackColor"
        :stroke-width="thickness"
        stroke-linecap="round"
      />
      <!-- 进度 -->
      <path
        :d="arcPath"
        fill="none"
        :stroke="strokePaint"
        :stroke-width="thickness"
        stroke-linecap="round"
        :stroke-dasharray="dashArray"
        :style="progressStyle"
      />
    </svg>

    <div class="screen-gauge__center" :style="centerStyle">
      <slot>
        <span class="screen-gauge__value">
          <screen-count-up :value="displayValue" :precision="precision" :grouped="false" />%
        </span>
        <span v-if="label" class="screen-gauge__label">{{ label }}</span>
      </slot>
    </div>
  </div>
</template>

<script>
import ScreenCountUp from './ScreenCountUp'
import { clamp, prefersReducedMotion, raf, toNumber, uid } from './utils'

export default {
  name: 'ScreenGauge',
  components: { ScreenCountUp },
  props: {
    /** 百分比 0-100，决定弧长 */
    percent: { type: [Number, String], default: 0 },
    /** 中心展示的数值，缺省取 percent */
    value: { type: [Number, String], default: null },
    /** 中心数值下方说明 */
    label: { type: String, default: '' },
    /** 中心数值小数位 */
    precision: { type: Number, default: 1 },
    /** 表盘直径（px） */
    size: { type: Number, default: 190 },
    /** 弧宽（px） */
    thickness: { type: Number, default: 12 },
    /** 起始角（度，0 为三点钟方向，顺时针为正） */
    startAngle: { type: Number, default: 135 },
    /** 顺时针扫过角度，270 即底部开口的仪表盘 */
    sweep: { type: Number, default: 270 },
    /** 轨道颜色（设计稿为深青蓝，切勿用亮色以免抢占进度层的视觉权重） */
    trackColor: { type: String, default: 'rgba(17, 96, 116, 0.55)' },
    /** 渐变起色 */
    fromColor: { type: String, default: '#6cc4ff' },
    /** 渐变止色 */
    toColor: { type: String, default: '#1d5fd0' },
    /** 数值文案颜色 tone */
    tone: { type: String, default: 'accent' },
    /** 是否入场动画 */
    animated: { type: Boolean, default: true },
  },
  data () {
    return {
      gradientId: uid('screen-gauge'),
      ready: false,
    }
  },
  computed: {
    ratio () {
      return clamp(toNumber(this.percent), 0, 100) / 100
    },
    displayValue () {
      return this.value === null || this.value === undefined ? toNumber(this.percent) : toNumber(this.value)
    },
    /** 表盘几何中心 */
    cx () {
      return this.size / 2
    },
    cy () {
      return this.size / 2
    },
    radius () {
      return (this.size - this.thickness) / 2
    },
    /** 底开口仪表盘的 SVG 高度：只保留上半部分与少量下沿 */
    svgHeight () {
      const radians = (this.sweep / 2) * (Math.PI / 180)
      const bottomExtent = this.cy + this.radius * Math.sin(radians)
      return Math.ceil(Math.min(bottomExtent + this.thickness / 2, this.size))
    },
    arcPath () {
      const start = this.startAngle
      const end = this.startAngle + this.sweep
      const p0 = this.polar(start)
      const p1 = this.polar(end)
      const largeArc = this.sweep > 180 ? 1 : 0
      return `M ${p0.x} ${p0.y} A ${this.radius} ${this.radius} 0 ${largeArc} 1 ${p1.x} ${p1.y}`
    },
    arcLength () {
      return this.radius * (this.sweep * Math.PI) / 180
    },
    /** 已绘制的弧长 dash 值，ready 之前为 0 以形成扫掠动画 */
    dashArray () {
      const shown = this.ready ? this.arcLength * this.ratio : 0
      return `${shown} ${this.arcLength}`
    },
    progressStyle () {
      return {
        transition:
          this.animated && !prefersReducedMotion()
            ? 'stroke-dasharray 900ms var(--screen-ease)'
            : 'none',
      }
    },
    /** 进度弧引用的渐变 */
    strokePaint () {
      return `url(#${this.gradientId})`
    },
    /** 中心文字块的垂直定位：对齐表盘几何圆心 */
    centerStyle () {
      return { top: `${this.cy}px` }
    },
    ariaLabel () {
      return `${this.label || '完成率'} ${this.displayValue.toFixed(this.precision)}%`
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
  methods: {
    /** 角度转笛卡尔坐标（屏幕坐标系，y 轴向下） */
    polar (angle) {
      const rad = (angle * Math.PI) / 180
      return {
        x: this.cx + this.radius * Math.cos(rad),
        y: this.cy + this.radius * Math.sin(rad),
      }
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-gauge {
  position: relative;
  flex: 0 0 auto;

  &__center {
    position: absolute;
    left: 0;
    right: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    transform: translateY(-50%);
    pointer-events: none;
  }

  &__value {
    .screen-number-font(clamp(22px, 2vw, 32px), 700);
    color: var(--screen-number);
    text-shadow: 0 0 14px rgba(98, 240, 196, 0.35);
  }

  &__label {
    font-size: var(--screen-font-sm);
    color: var(--screen-text);
    letter-spacing: 0.04em;
  }
}
</style>
