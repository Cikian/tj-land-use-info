<template>
  <!--
    ScreenCountUp 数字滚动
    --------------------------------
    纯展示组件，只负责把数值「滚」到目标值；格式化交给 formatNumber。
    尊重 prefers-reduced-motion：开启后直接显示终值，不做动画。
  -->
  <span class="screen-count-up">{{ display }}</span>
</template>

<script>
import { caf, easeOutCubic, formatNumber, prefersReducedMotion, raf, toNumber } from './utils'

export default {
  name: 'ScreenCountUp',
  props: {
    /** 目标数值 */
    value: { type: [Number, String], default: 0 },
    /** 动画时长（毫秒） */
    duration: { type: Number, default: 900 },
    /** 小数位 */
    precision: { type: Number, default: 0 },
    /** 是否使用千分位 */
    grouped: { type: Boolean, default: true },
    /** 是否开启动画 */
    animated: { type: Boolean, default: true },
  },
  data () {
    return {
      current: toNumber(this.value),
    }
  },
  computed: {
    display () {
      return formatNumber(this.current, this.precision, this.grouped)
    },
  },
  watch: {
    value: {
      immediate: false,
      handler (val) {
        this.animateTo(toNumber(val))
      },
    },
  },
  mounted () {
    // 首次进入时从 0 滚动到目标值，形成大屏「数值跳入」效果
    const target = toNumber(this.value)
    if (!this.animated || prefersReducedMotion() || target === 0) {
      this.current = target
      return
    }
    this.current = 0
    this.animateTo(target)
  },
  beforeDestroy () {
    this.stop()
  },
  methods: {
    stop () {
      if (this.frame) {
        caf(this.frame)
        this.frame = null
      }
      if (this.timer) {
        clearTimeout(this.timer)
        this.timer = null
      }
    },
    animateTo (target) {
      this.stop()
      if (!this.animated || prefersReducedMotion() || this.duration <= 0) {
        this.current = target
        return
      }
      const from = this.current
      const delta = target - from
      if (delta === 0) return
      const start = Date.now()
      const step = () => {
        const elapsed = Date.now() - start
        const progress = Math.min(elapsed / this.duration, 1)
        this.current = from + delta * easeOutCubic(progress)
        if (progress < 1) {
          this.frame = raf(step)
        } else {
          this.current = target
          this.frame = null
        }
      }
      this.frame = raf(step)
    },
  },
}
</script>

<style scoped>
.screen-count-up {
  font-variant-numeric: tabular-nums;
}
</style>
