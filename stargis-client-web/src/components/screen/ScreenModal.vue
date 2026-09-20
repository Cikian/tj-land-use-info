<template>
  <!--
    ScreenModal 大屏弹窗
    --------------------------------
    档案新增/编辑、类别新增/编辑、档案详情都基于它。视觉上是「更实的面板」：
    与 ScreenPanel 同族的青绿描边 + 玻璃底，但底色更实，保证压在表格/地图上读得清。

    用法：
      <screen-modal :visible.sync="visible" :title="title" :width="1180" :confirm-loading="saving"
                    @ok="save" @cancel="close">
        <template #head-extra>...</template>
        正文
        <template #footer>自定义底栏</template>
      </screen-modal>

    实现要点（这三点是这个组件存在的理由）：
      1. **挂到 document.body**：大屏浮层是 pointer-events: none + overflow 裁剪，
         面板还会因 backdrop-filter 成为 fixed 定位的包含块，弹窗留在里面必然错位。
      2. **焦点陷阱**：打开时记住原焦点并移入弹窗，Tab 在弹窗内循环，
         关闭后把焦点还给触发元素——否则大屏上键盘用户会「丢焦点」到地图里。
      3. **滚动锁**：打开时锁掉 body 滚动，并用计数器兼容「弹窗里再开弹窗」。
  -->
  <div class="screen-modal-root">
    <transition name="screen-modal" :duration="motionDisabled ? 0 : 200">
      <div v-if="visible" class="screen-modal">
        <div class="screen-modal__mask" @click="handleMaskClick" />

        <div
          ref="dialog"
          class="screen-modal__dialog"
          :class="[`is-${size}`, { 'is-full': fullscreen }]"
          :style="dialogStyle"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="title ? titleId : undefined"
          :aria-label="title ? undefined : ariaLabel || '对话框'"
          tabindex="-1"
          @keydown="handleKeydown"
        >
          <header class="screen-modal__head">
            <span class="screen-modal__bar" aria-hidden="true" />
            <h2 v-if="title" :id="titleId" class="screen-modal__title">{{ title }}</h2>
            <div class="screen-modal__head-extra">
              <slot name="head-extra" />
            </div>
            <button
              v-if="closable"
              type="button"
              class="screen-modal__close"
              aria-label="关闭对话框"
              @click="handleCancel"
            >
              <screen-icon name="close" :size="15" />
            </button>
          </header>

          <div class="screen-modal__body" :style="bodyStyle">
            <slot />
          </div>

          <footer v-if="showFooter" class="screen-modal__foot">
            <slot name="footer">
              <screen-button size="md" @click="handleCancel">{{ cancelText }}</screen-button>
              <screen-button size="md" type="primary" :loading="confirmLoading" @click="handleOk">
                {{ okText }}
              </screen-button>
            </slot>
          </footer>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import ScreenIcon from './ScreenIcon'
import ScreenButton from './ScreenButton'
import { prefersReducedMotion, uid } from './utils'

/**
 * 打开的弹窗数量：用于滚动锁的引用计数。
 * 弹窗里再开弹窗时，内层关闭不能把外层的滚动锁一起解除。
 */
let openCount = 0
let savedOverflow = ''

function lockScroll () {
  if (openCount === 0) {
    savedOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  }
  openCount += 1
}

function unlockScroll () {
  openCount = Math.max(0, openCount - 1)
  if (openCount === 0) {
    document.body.style.overflow = savedOverflow
  }
}

/** 可获得焦点的元素选择器 */
const FOCUSABLE =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

export default {
  name: 'ScreenModal',
  components: { ScreenIcon, ScreenButton },
  props: {
    /** 是否显示（建议 .sync） */
    visible: { type: Boolean, default: false },
    /** 标题；不传时用 ariaLabel 作为无障碍名称 */
    title: { type: String, default: '' },
    /** 无标题时的无障碍名称 */
    ariaLabel: { type: String, default: '' },
    /** 宽度：数字（px）或 CSS 字符串 */
    width: { type: [Number, String], default: 720 },
    /** 尺寸档位，仅影响默认宽度上限 */
    size: { type: String, default: 'md' },
    /** 铺满视口（档案详情用） */
    fullscreen: { type: Boolean, default: false },
    /** 正文最大高度，超出后内部滚动 */
    bodyMaxHeight: { type: String, default: 'calc(100vh - 220px)' },
    /** 点击遮罩是否关闭 */
    maskClosable: { type: Boolean, default: false },
    /** Esc 是否关闭 */
    escClosable: { type: Boolean, default: true },
    /** 是否显示右上角关闭按钮 */
    closable: { type: Boolean, default: true },
    /** 是否显示底栏 */
    showFooter: { type: Boolean, default: true },
    /** 确定按钮 loading */
    confirmLoading: { type: Boolean, default: false },
    okText: { type: String, default: '保存' },
    cancelText: { type: String, default: '取消' },
  },
  data () {
    return {
      titleId: uid('screen-modal-title'),
      /** 打开前的焦点元素，关闭后还给它 */
      lastActiveElement: null,
      motionDisabled: false,
    }
  },
  computed: {
    dialogStyle () {
      const style = {}
      if (!this.fullscreen) {
        style.width = typeof this.width === 'number' ? `${this.width}px` : this.width
      }
      return style
    },
    bodyStyle () {
      return this.bodyMaxHeight ? { maxHeight: this.bodyMaxHeight } : {}
    },
  },
  watch: {
    visible (val, oldVal) {
      if (val === oldVal) return
      if (val) {
        this.handleOpen()
      } else {
        this.handleClose()
      }
    },
  },
  mounted () {
    // 移出面板 DOM：避免 overflow 裁剪与 backdrop-filter 的包含块影响定位
    if (this.$el && this.$el.parentNode !== document.body) {
      document.body.appendChild(this.$el)
    }
    this.motionDisabled = prefersReducedMotion()
    if (this.visible) this.handleOpen()
  },
  beforeDestroy () {
    if (this.visible) {
      this.handleClose()
    }
    if (this.$el && this.$el.parentNode) {
      this.$el.parentNode.removeChild(this.$el)
    }
  },
  methods: {
    handleOpen () {
      this.lastActiveElement = document.activeElement
      lockScroll()
      this.$emit('open')
      this.$nextTick(() => {
        const target = this.firstFocusable() || this.$refs.dialog
        if (target && target.focus) target.focus()
      })
    },
    handleClose () {
      unlockScroll()
      const target = this.lastActiveElement
      this.lastActiveElement = null
      this.$emit('close')
      // 焦点还给触发元素，但不要抢走别处刚获得的焦点
      this.$nextTick(() => {
        if (target && target.focus && document.body.contains(target)) {
          target.focus()
        }
      })
    },
    /** 弹窗内可获得焦点的可见元素 */
    focusableElements () {
      const dialog = this.$refs.dialog
      if (!dialog) return []
      return Array.prototype.slice
        .call(dialog.querySelectorAll(FOCUSABLE))
        .filter((el) => el.offsetParent !== null || el === document.activeElement)
    },
    firstFocusable () {
      const list = this.focusableElements()
      return list.length ? list[0] : null
    },
    handleKeydown (event) {
      if (event.key === 'Escape') {
        if (!this.escClosable) return
        event.stopPropagation()
        this.handleCancel()
        return
      }
      if (event.key !== 'Tab') return

      // 焦点陷阱：Tab 到边界时回到另一端
      const list = this.focusableElements()
      if (!list.length) {
        event.preventDefault()
        if (this.$refs.dialog) this.$refs.dialog.focus()
        return
      }
      const first = list[0]
      const last = list[list.length - 1]
      const active = document.activeElement

      if (event.shiftKey && (active === first || !this.$refs.dialog.contains(active))) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    },
    handleMaskClick () {
      if (!this.maskClosable) return
      this.handleCancel()
    },
    handleCancel () {
      this.$emit('update:visible', false)
      this.$emit('cancel')
    },
    handleOk () {
      this.$emit('ok')
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

// 外层只作为 body 上的挂载点，不参与布局
.screen-modal-root {
  position: static;
}

.screen-modal {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--screen-space-5);

  &__mask {
    position: absolute;
    inset: 0;
    // 遮罩要足够实，保证弹窗正文在任何背景（含地图）上都读得清
    background: rgba(1, 14, 17, 0.72);
    backdrop-filter: blur(2px);
  }

  &__dialog {
    position: relative;
    display: flex;
    flex-direction: column;
    width: 720px;
    max-width: 100%;
    max-height: calc(100vh - 40px);
    min-height: 0;
    color: var(--screen-text);
    background: var(--screen-panel-bg-solid);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-lg);
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.55), var(--screen-shadow-inset);
    outline: none;

    &.is-full {
      width: 100%;
      height: 100%;
      max-height: 100%;
      border-radius: var(--screen-radius);
    }
  }

  // 宽度档位只是「上限」，实际宽度由 width 属性决定
  &__dialog.is-sm {
    width: 520px;
  }

  &__dialog.is-lg {
    width: 960px;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    flex: 0 0 auto;
    min-height: 46px;
    padding: 0 var(--screen-space-4) 0 var(--screen-space-3);
    background: var(--screen-panel-head-bg);
    border-bottom: 1px solid var(--screen-border-soft);
    border-top-left-radius: inherit;
    border-top-right-radius: inherit;
  }

  &__bar {
    flex: 0 0 auto;
    width: 3px;
    height: 15px;
    border-radius: var(--screen-radius-pill);
    background: linear-gradient(180deg, var(--screen-accent) 0%, var(--screen-accent-deep) 100%);
    box-shadow: 0 0 8px var(--screen-accent-glow);
  }

  &__title {
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 600;
    line-height: 1.3;
    letter-spacing: 0.03em;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__head-extra {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin-left: auto;
    min-width: 0;
  }

  &__close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    width: 26px;
    height: 26px;
    padding: 0;
    margin-left: var(--screen-space-2);
    color: var(--screen-text-sub);
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent);
      border-color: var(--screen-border);
    }
  }

  &__body {
    flex: 1 1 auto;
    min-height: 0;
    padding: var(--screen-space-4);
    overflow-y: auto;
    overflow-x: hidden;
    .screen-scrollbar();
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: var(--screen-space-2);
    flex: 0 0 auto;
    padding: 10px var(--screen-space-4);
    border-top: 1px solid var(--screen-border-soft);
  }
}

/* 入场/离场：遮罩淡入 + 弹窗轻微上移，不做缩放以避免文字重排 */
.screen-modal-enter-active,
.screen-modal-leave-active {
  transition: opacity var(--screen-duration) var(--screen-ease);
}

.screen-modal-enter-active .screen-modal__dialog,
.screen-modal-leave-active .screen-modal__dialog {
  transition: transform var(--screen-duration) var(--screen-ease);
}

.screen-modal-enter,
.screen-modal-leave-to {
  opacity: 0;
}

.screen-modal-enter .screen-modal__dialog,
.screen-modal-leave-to .screen-modal__dialog {
  transform: translateY(10px);
}

@media (prefers-reduced-motion: reduce) {
  .screen-modal-enter-active,
  .screen-modal-leave-active,
  .screen-modal-enter-active .screen-modal__dialog,
  .screen-modal-leave-active .screen-modal__dialog {
    transition: none;
  }

  .screen-modal-enter .screen-modal__dialog,
  .screen-modal-leave-to .screen-modal__dialog {
    transform: none;
  }
}
</style>
