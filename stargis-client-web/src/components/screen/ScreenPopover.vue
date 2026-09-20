<template>
  <!--
    ScreenPopover 浮层容器（挂到 document.body）
    --------------------------------
    为什么必须挂到 body：
      大屏面板 ScreenPanel 用了 `overflow: hidden` + `backdrop-filter`。
      backdrop-filter 会让面板成为 fixed 定位的包含块，overflow 又会裁掉溢出内容，
      所以「贴着输入框弹出的下拉/气泡」只要还在面板 DOM 里就一定会被裁掉或错位。
      这里在 mounted 时把自身节点移动到 document.body，并用 position: fixed
      按锚点元素的 getBoundingClientRect() 定位，彻底绕开裁剪与包含块问题。
      浮层被移出 `.land-screen__ui`（pointer-events: none）之后也不再需要单独开事件。

    用法：
      <screen-popover :open="open" :anchor="anchorEl" placement="bottom-start" @close="open = false">
        ...下拉内容...
      </screen-popover>

    注意：浮层内容通过插槽来自父组件，因此父组件的 scoped 样式依然生效；
          但父组件若给根节点加 max-height/overflow，需要改用本组件的 maxHeight 属性。
  -->
  <div
    v-show="open"
    ref="layer"
    class="screen-popover"
    :class="[`is-${placement}`, { 'is-scroll': !!maxHeight }]"
    :style="layerStyle"
    :role="role || undefined"
  >
    <slot />
  </div>
</template>

<script>
export default {
  name: 'ScreenPopover',
  props: {
    /** 是否展示（建议加 .sync 以便外部同步关闭状态） */
    open: { type: Boolean, default: false },
    /** 锚点元素：HTMLElement 或返回 HTMLElement 的函数 */
    anchor: { type: [Object, Function], default: null },
    /** 相对锚点的位置：bottom-start / bottom-end / top-start / top-end */
    placement: { type: String, default: 'bottom-start' },
    /** 浮层宽度：数字（px）或 CSS 字符串；不传则与锚点等宽 */
    width: { type: [Number, String], default: null },
    /** 是否强制与锚点等宽 */
    matchWidth: { type: Boolean, default: true },
    /** 最大高度，超出后内部滚动 */
    maxHeight: { type: [Number, String], default: 300 },
    /** 与锚点的间距 */
    gap: { type: Number, default: 4 },
    /**
     * 层级。
     * 必须高于 ScreenModal（1200），否则「弹窗里的下拉/确认气泡」会被弹窗盖住而看不见、
     * 点不到 —— 例如新增档案弹窗里的项目/类别下拉。低于 ScreenToast（1300），
     * 保证消息提示永远在最上层。
     */
    zIndex: { type: Number, default: 1260 },
    /** 点击浮层外部是否关闭 */
    closeOnClickOutside: { type: Boolean, default: true },
    /** Esc 是否关闭 */
    closeOnEsc: { type: Boolean, default: true },
    /** 无障碍角色，例如 listbox */
    role: { type: String, default: '' },
  },
  data () {
    return {
      style: {},
    }
  },
  computed: {
    layerStyle () {
      return Object.assign({ zIndex: this.zIndex }, this.style)
    },
  },
  watch: {
    open (val) {
      if (val) {
        this.bind()
        this.$nextTick(this.update)
      } else {
        this.unbind()
      }
    },
    placement () {
      this.update()
    },
  },
  mounted () {
    // 移出面板 DOM，避免被 overflow / backdrop-filter 裁剪
    if (this.$el && this.$el.parentNode !== document.body) {
      document.body.appendChild(this.$el)
    }
    if (this.open) {
      this.bind()
      this.$nextTick(this.update)
    }
  },
  beforeDestroy () {
    this.unbind()
    if (this.$el && this.$el.parentNode) {
      this.$el.parentNode.removeChild(this.$el)
    }
  },
  methods: {
    /** 解析锚点元素 */
    resolveAnchor () {
      const anchor = this.anchor
      if (!anchor) return null
      const el = typeof anchor === 'function' ? anchor() : anchor
      if (!el) return null
      // 支持 Vue 组件实例 / $refs 对象
      return el.$el || el
    },
    /** 重新计算位置（锚点尺寸变化后可手动调用） */
    update () {
      const el = this.resolveAnchor()
      if (!el || !this.$el) return

      const rect = el.getBoundingClientRect()
      const layerHeight = this.$el.offsetHeight || 0
      const gap = this.gap
      const style = {}

      // ---- 宽度：默认与锚点等宽，保证「选择框 — 下拉面板」左右对齐 ----
      if (this.width !== null && this.width !== undefined) {
        style.width = typeof this.width === 'number' ? `${this.width}px` : this.width
      } else if (this.matchWidth) {
        style.width = `${rect.width}px`
      } else {
        style.minWidth = `${rect.width}px`
      }
      style.maxHeight = typeof this.maxHeight === 'number' ? `${this.maxHeight}px` : this.maxHeight

      // ---- 纵向：下方空间不足且上方更宽裕时自动翻转到上方 ----
      const spaceBelow = window.innerHeight - rect.bottom
      const flipUp =
        this.placement.indexOf('top') === 0 ||
        (spaceBelow < layerHeight + gap && rect.top > spaceBelow)

      if (flipUp) {
        style.top = `${Math.max(gap, rect.top - gap)}px`
        style.transform = 'translateY(-100%)'
      } else {
        style.top = `${rect.bottom + gap}px`
        style.transform = 'none'
      }

      // ---- 横向：start 左对齐锚点，end 右对齐锚点 ----
      if (this.placement.indexOf('end') > -1) {
        style.left = 'auto'
        style.right = `${Math.max(0, window.innerWidth - rect.right)}px`
      } else {
        style.right = 'auto'
        style.left = `${Math.max(0, rect.left)}px`
      }

      this.style = style
    },
    bind () {
      this.unbind()
      window.addEventListener('resize', this.update)
      // capture: true 才能捕获到面板内部滚动容器的 scroll 事件
      window.addEventListener('scroll', this.update, true)
      document.addEventListener('mousedown', this.handleOutside, true)
      if (this.closeOnEsc) {
        document.addEventListener('keydown', this.handleKeydown, true)
      }
    },
    unbind () {
      window.removeEventListener('resize', this.update)
      window.removeEventListener('scroll', this.update, true)
      document.removeEventListener('mousedown', this.handleOutside, true)
      document.removeEventListener('keydown', this.handleKeydown, true)
    },
    handleOutside (event) {
      if (!this.closeOnClickOutside || !this.open) return
      const target = event.target
      if (this.$el.contains(target)) return
      const el = this.resolveAnchor()
      if (el && el.contains(target)) return
      this.$emit('close', 'outside')
      this.$emit('update:open', false)
    },
    handleKeydown (event) {
      if (event.key !== 'Escape') return
      this.$emit('close', 'esc')
      this.$emit('update:open', false)
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-popover {
  position: fixed;
  top: 0;
  left: 0;
  .screen-popover-surface();
  .screen-scrollbar();

  &.is-scroll {
    overflow-y: auto;
    overflow-x: hidden;
  }
}
</style>
