<template>
  <!--
    ScreenResizeBar 面板高度调节条
    --------------------------------
    放在「可调高面板」的**上沿**：按住它上下拖动就能改变该面板的高度
    （往上拖 = 面板变高，往下拖 = 变矮），松开即定稿。

    为什么收口成一个组件、而不是每个面板自己写一遍：
      1. ★ 大屏画布是 `transform: scale(k)` 的**定尺画布**（见 ScreenStage）：
         鼠标位移是**真实像素**，而面板高度是**画布像素**，两者差一个 k。
         不换算就会出现「鼠标往上移 100px，面板只长了 91px」，
         而且误差在整段拖动里持续累积，越拖越偏（见 measureScale）。
      2. 拖动中要锁住文本选中、把光标统一换成 ns-resize、还要处理 pointercancel
         与组件被销毁时的兜底清理 —— 这些细节每写一遍就会漏一遍。

    无障碍（不是可选项，大屏同样有用键盘操作的用户）：
      用 `role="separator"` + `aria-valuenow/min/max` 表达「这是可调节的分隔条」，
      键盘：↑/↓ 微调、PgUp/PgDn 大步、Home/End 到最小/最大。

    用法（父组件持有高度，这里只负责算出新值）：
      <screen-resize-bar
        :value="listHeight"
        :min="LIST_MIN_HEIGHT"
        :max="listMaxHeight"
        aria-label="拖动调整列表高度"
        @input="handleResize"
      />
  -->
  <div
    class="screen-resize-bar"
    :class="{ 'is-active': dragging, 'is-disabled': disabled }"
    role="separator"
    aria-orientation="horizontal"
    :aria-label="ariaLabel"
    :aria-valuenow="value"
    :aria-valuemin="min"
    :aria-valuemax="max"
    :tabindex="disabled ? -1 : 0"
    @pointerdown="handlePointerDown"
    @keydown="handleKeydown"
  >
    <span class="screen-resize-bar__grip" aria-hidden="true" />
  </div>
</template>

<script>
export default {
  name: 'ScreenResizeBar',
  props: {
    /** 当前值（画布像素），由父组件持有 */
    value: { type: Number, required: true },
    /** 下限：小于它就没法用了（例如连表头都露不出来） */
    min: { type: Number, default: 120 },
    /** 上限：通常由「面板最多能长到哪」决定 */
    max: { type: Number, default: 900 },
    /** 键盘微调的步长（画布像素） */
    step: { type: Number, default: 16 },
    /** 禁用拖动（例如数据还没加载出来） */
    disabled: { type: Boolean, default: false },
    /** 无障碍名称 */
    ariaLabel: { type: String, default: '拖动调整面板高度' }
  },
  data () {
    return {
      dragging: false,
      /** 按下时的鼠标 y（真实像素）与当时的取值（画布像素） */
      startY: 0,
      startValue: 0,
      /** 画布缩放系数：真实像素 → 画布像素要除以它 */
      scale: 1,
      /** 本次拖动的主指针 id，用于精准释放捕获（鼠标是 1，触屏/笔各不相同） */
      pointerId: null,
      /** 最近一次真正生效的值，供 change 事件用（prop 回传有一帧延迟） */
      lastValue: 0
    }
  },
  beforeDestroy () {
    // 组件在拖动中被销毁（例如切页签）也要把监听和 body 标记摘干净，
    // 否则 body 会永远停在 ns-resize 光标 + 禁止选中。
    this.teardown()
  },
  methods: {
    /**
     * 量出「真实像素 / 画布像素」的比值。
     *
     * ★ 为什么必须量：设计画布是 `transform: scale(k)`，`event.clientY` 是缩放后的
     *   真实像素，而我们要设置的高度是画布内的像素。1920×1080 画布在 1894×987 的
     *   窗口里 k≈0.914，直接拿位移当高度会让面板「跟不上鼠标」。
     *
     * 量父元素而不是自己：本组件只有 12px 高，缩放后取整误差占比重；
     * 父元素（被调节的那个面板）高得多，同样的误差可以忽略。
     * 量不到就退回 1，宁可「手感略偏」也不要报错。
     */
    measureScale () {
      const el = this.$el
      const target = (el && el.parentElement) || el
      if (!target || !target.offsetHeight) return 1
      const rect = target.getBoundingClientRect()
      const scale = rect.height / target.offsetHeight
      return scale > 0 ? scale : 1
    },

    handlePointerDown (event) {
      if (this.disabled) return
      // 只响应左键/主指针；右键与中键留给浏览器默认行为
      if (event.button !== undefined && event.button !== 0) return
      event.preventDefault()

      this.dragging = true
      this.startY = event.clientY
      this.startValue = this.value
      this.lastValue = this.value
      this.scale = this.measureScale()

      // 拖动期间锁住选中与光标（非 scoped 的全局规则，见文件末尾）
      document.body.classList.add('is-resizing-panel')

      /*
       * 指针捕获：让后续 pointermove/pointerup 一律发给本元素，
       * 即使鼠标已经移到地图或其它面板上。
       * ★ 这同时避免了「拖调节条时把地图一起拖走」—— 事件根本不会落到 Cesium 的
       *   canvas 上。不支持捕获的内核（或 pointerId 缺失）退回 window 监听，同样有效。
       */
      const el = this.$el
      if (el && el.setPointerCapture && event.pointerId !== undefined) {
        this.pointerId = event.pointerId
        try {
          el.setPointerCapture(event.pointerId)
        } catch (e) {
          // 某些内核在 pointerId 失效时会抛，忽略即可 —— window 监听是兜底
        }
      }

      window.addEventListener('pointermove', this.handlePointerMove)
      window.addEventListener('pointerup', this.handlePointerUp)
      window.addEventListener('pointercancel', this.handlePointerUp)
    },

    handlePointerMove (event) {
      if (!this.dragging) return
      // 往上拖 = 变高，所以是「起点 - 当前」
      const delta = (this.startY - event.clientY) / (this.scale || 1)
      this.apply(this.startValue + delta)
    },

    handlePointerUp () {
      if (!this.dragging) return
      this.teardown()
      this.$emit('change', this.lastValue)
    },

    /** 统一收尾：摘监听、释放捕获、去掉 body 标记 */
    teardown () {
      if (typeof window !== 'undefined') {
        window.removeEventListener('pointermove', this.handlePointerMove)
        window.removeEventListener('pointerup', this.handlePointerUp)
        window.removeEventListener('pointercancel', this.handlePointerUp)
      }
      const el = this.$el
      if (el && el.releasePointerCapture && this.pointerId !== null) {
        try {
          el.releasePointerCapture(this.pointerId)
        } catch (e) {
          // 忽略：指针抬起时浏览器本来也会自动释放捕获
        }
      }
      this.pointerId = null
      if (typeof document !== 'undefined') {
        document.body.classList.remove('is-resizing-panel')
      }
      this.dragging = false
    },

    /** 夹到 [min, max] 并抛给父组件；值没变就不抛（避免无谓的重渲染） */
    apply (next) {
      const clamped = Math.min(this.max, Math.max(this.min, Math.round(next)))
      if (clamped === this.lastValue) return
      this.lastValue = clamped
      this.$emit('input', clamped)
    },

    /**
     * 键盘调节。
     * ↑/↓ 微调、PgUp/PgDn 大步、Home/End 直达两端 —— 与桌面端分隔条的惯例一致。
     * 每次按键都补发 change，让父组件有机会做「调整后重算表格可见行」这类收尾。
     */
    handleKeydown (event) {
      if (this.disabled) return
      const big = this.step * 4
      let next = null
      switch (event.key) {
        case 'ArrowUp':
          next = this.value + this.step
          break
        case 'ArrowDown':
          next = this.value - this.step
          break
        case 'PageUp':
          next = this.value + big
          break
        case 'PageDown':
          next = this.value - big
          break
        case 'Home':
          next = this.min
          break
        case 'End':
          next = this.max
          break
        default:
          return
      }
      event.preventDefault()
      this.lastValue = this.value
      this.apply(next)
      this.$emit('change', this.lastValue)
    }
  }
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-resize-bar {
  position: relative;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 12px;
  padding: 0;
  background: transparent;
  border: 0;
  cursor: ns-resize;
  // 触屏上不要把手势交给浏览器（否则拖动会变成页面滚动）
  touch-action: none;
  .screen-focus-ring();

  &__grip {
    width: 56px;
    height: 4px;
    border-radius: var(--screen-radius-pill);
    background: var(--screen-border-strong);
    // 抓手本身不吃事件，命中测试落在条上，拖动区域就是整条
    pointer-events: none;
    transition: width var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);
  }

  &:hover &__grip {
    background: var(--screen-accent);
  }

  // 拖动中：抓手变长变亮，明确「正在调整」而不是「点了一下没反应」
  &.is-active &__grip {
    width: 80px;
    background: var(--screen-accent);
    box-shadow: 0 0 8px var(--screen-accent-glow);
  }

  &.is-disabled {
    cursor: default;

    .screen-resize-bar__grip {
      background: var(--screen-border-soft);
    }
  }
}
</style>

<style lang="less">
/*
 * 拖动调节条期间的全局态（**故意不加 scoped**）：
 * 光标要覆盖整页（鼠标可能已经移到地图或别的面板上），
 * 选中也要在整页范围内禁掉，否则快速拖动会把面板文字刷成选中态。
 *
 * ⚠ 只在 .is-resizing-panel 存在时生效，抬起指针就会移除（含组件被销毁的兜底）。
 */
body.is-resizing-panel {
  cursor: ns-resize;
  user-select: none;
  -webkit-user-select: none;

  * {
    cursor: ns-resize !important;
  }
}
</style>
