<template>
  <!--
    ScreenResizeBar 面板尺寸调节条（横向改高 / 纵向改宽）
    ==================================================================
    放在「可调尺寸面板」的边缘：按住拖动就能改变面板的高度或宽度，
    松开即定稿。两种朝向：

      orientation="horizontal"（默认，改**高度**）
        贴面板**上沿**：往上拖 = 面板变高、往下拖 = 变矮。
        数据管理的「经营性用地 / 配套项目」列表面板就是这种。

      orientation="vertical"（改**宽度**）
        贴面板的**内侧竖边**：左面板贴在右边缘，往右拖 = 变宽；
        右面板贴在左边缘，需要同时给 `reverse`，往左拖 = 变宽。
        首页 / 地图管理 / 配套分析管理的左右面板用它。

    为什么收口成一个组件、而不是每个面板自己写一遍：
      1. ★ 大屏画布是 `transform: scale(k)` 的**定尺画布**（见 ScreenStage）：
         鼠标位移是**真实像素**，而面板尺寸是**画布像素**，两者差一个 k。
         不换算就会出现「鼠标往上移 100px，面板只长了 91px」，
         而且误差在整段拖动里持续累积，越拖越偏（见 measureScale）。
         纵向拖动要量**宽度**的比值、横向要量**高度**的，这里按朝向自动切换。
      2. 拖动中要锁住文本选中、把光标统一换成 ns-/ew-resize、还要处理
         pointercancel 与组件被销毁时的兜底清理 —— 这些细节每写一遍就会漏一遍。

    无障碍（不是可选项，大屏同样有用键盘操作的用户）：
      用 `role="separator"` + `aria-orientation` + `aria-valuenow/min/max`
      表达「这是可调节的分隔条」；
      横向：↑/↓ 微调、PgUp/PgDn 大步、Home/End 到最小/最大；
      纵向：←/→ 微调（→ 变宽）、PgUp/PgDn 大步、Home/End 到两端。

    用法（父组件持有尺寸，这里只负责算出新值）：
      <screen-resize-bar
        :value="listHeight"
        :min="LIST_MIN_HEIGHT"
        :max="listMaxHeight"
        aria-label="拖动调整列表高度"
        @input="handleResize"
      />
      左右面板：
      <screen-resize-bar
        orientation="vertical"
        :value="width"
        :min="340"
        :max="560"
        @input="$emit('width-change', $event)"
      />
  -->
  <div
    class="screen-resize-bar"
    :class="[
      orientation === 'vertical' ? 'is-vertical' : 'is-horizontal',
      { 'is-active': dragging, 'is-disabled': disabled }
    ]"
    role="separator"
    :aria-orientation="ariaOrientation"
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
    /**
     * 朝向：horizontal = 改高度（默认，贴面板上沿）；
     *       vertical   = 改宽度（贴面板内侧竖边）
     */
    orientation: {
      type: String,
      default: 'horizontal',
      validator: (value) => value === 'horizontal' || value === 'vertical'
    },
    /**
     * 纵向拖动时的方向反转：右面板贴在左边缘，往**左**拖才是变宽。
     * 横向朝向暂不需要反转（贴下沿改高的场景没有）。
     */
    reverse: { type: Boolean, default: false },
    /** 无障碍名称 */
    ariaLabel: { type: String, default: '拖动调整面板尺寸' }
  },
  data () {
    return {
      dragging: false,
      /** 按下时的鼠标坐标（真实像素）与当时的取值（画布像素） */
      startX: 0,
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
  computed: {
    /** role="separator" 的朝向与视觉朝向一致（横条调高 = horizontal） */
    ariaOrientation () {
      return this.orientation === 'vertical' ? 'vertical' : 'horizontal'
    }
  },
  beforeDestroy () {
    // 组件在拖动中被销毁（例如切页签）也要把监听和 body 标记摘干净，
    // 否则 body 会永远停在 resize 光标 + 禁止选中。
    this.teardown()
  },
  methods: {
    /**
     * 量出「真实像素 / 画布像素」的比值。
     *
     * ★ 为什么必须量：设计画布是 `transform: scale(k)`，`event.clientY/X` 是缩放后的
     *   真实像素，而我们要设置的是画布内的尺寸。1920×1080 画布在 1894×987 的
     *   窗口里 k≈0.914，直接拿位移当尺寸会让面板「跟不上鼠标」。
     *
     * 量父元素而不是自己：本组件只有 12px 厚，缩放后取整误差占比重；
     * 父元素（被调节的那个面板）大得多，同样的误差可以忽略。
     * 量不到就退回 1，宁可「手感略偏」也不要报错。
     *
     * ★ 轴向必须跟着朝向走：纵向拖动改的是**宽度**，要量宽度比值；
     *   拿高度比值去换算宽度，宽高比不是 1 的窗口里会明显不跟手。
     */
    measureScale () {
      const el = this.$el
      const target = (el && el.parentElement) || el
      if (!target) return 1
      if (this.orientation === 'vertical') {
        if (!target.offsetWidth) return 1
        const rect = target.getBoundingClientRect()
        const scale = rect.width / target.offsetWidth
        return scale > 0 ? scale : 1
      }
      if (!target.offsetHeight) return 1
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
      this.startX = event.clientX
      this.startY = event.clientY
      this.startValue = this.value
      this.lastValue = this.value
      this.scale = this.measureScale()

      // 拖动期间锁住选中与光标（非 scoped 的全局规则，见文件末尾）
      document.body.classList.add('is-resizing-panel')
      if (this.orientation === 'vertical') {
        document.body.classList.add('is-resizing-panel-x')
      }

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
      const scale = this.scale || 1
      let delta
      if (this.orientation === 'vertical') {
        // 左面板：往右拖 = 变宽；右面板（reverse）：往左拖 = 变宽
        delta = (this.reverse ? this.startX - event.clientX : event.clientX - this.startX) / scale
      } else {
        // 上沿的横条：往上拖 = 变高，所以是「起点 - 当前」
        delta = (this.startY - event.clientY) / scale
      }
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
        document.body.classList.remove('is-resizing-panel-x')
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
     * 横向：↑/↓ 微调；纵向：←/→ 微调（→ = 变宽，与「展开」同向）。
     * PgUp/PgDn 大步、Home/End 直达两端 —— 与桌面端分隔条的惯例一致。
     * 每次按键都补发 change，让父组件有机会做「调整后重算可见行」这类收尾。
     */
    handleKeydown (event) {
      if (this.disabled) return
      const big = this.step * 4
      const vertical = this.orientation === 'vertical'
      let next = null
      switch (event.key) {
        case 'ArrowUp':
          if (!vertical) next = this.value + this.step
          break
        case 'ArrowDown':
          if (!vertical) next = this.value - this.step
          break
        case 'ArrowRight':
          if (vertical) next = this.value + this.step
          break
        case 'ArrowLeft':
          if (vertical) next = this.value - this.step
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
      if (next === null) return
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
      height var(--screen-duration) var(--screen-ease),
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

  /*
   * 纵向（改宽度）：贴在面板内侧竖边。
   * 高宽反过来 —— 宽 12、高度由父级（top/bottom 或 flex stretch）决定，
   * 抓手竖过来 4×56，拖动中变高 80。
   */
  &.is-vertical {
    width: 12px;
    height: auto;
    align-self: stretch;
    cursor: ew-resize;

    .screen-resize-bar__grip {
      width: 4px;
      height: 56px;
    }

    &.is-active .screen-resize-bar__grip {
      width: 4px;
      height: 80px;
    }
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
 * 纵向拖动额外挂 .is-resizing-panel-x —— 两个类同时存在时后者的规则优先，
 * 光标才是 ew-resize（两个独立类选择器靠声明顺序 + 更高优先级保证）。
 */
body.is-resizing-panel {
  cursor: ns-resize;
  user-select: none;
  -webkit-user-select: none;

  * {
    cursor: ns-resize !important;
  }
}

body.is-resizing-panel.is-resizing-panel-x {
  cursor: ew-resize;

  * {
    cursor: ew-resize !important;
  }
}
</style>
