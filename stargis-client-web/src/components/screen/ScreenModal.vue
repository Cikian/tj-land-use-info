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

    两个可选的「非模态」能力（表单弹窗要一边填一边操作下面的地图时用）：
      :dimmed="false"  —— 背景不压暗，**且遮罩不再吃鼠标**（两件事必须同时做，见 &__mask）
      draggable        —— 抓标题栏可拖动，且保证不会被拖到找不回来（见 applyDrag）

    实现要点（这三点是这个组件存在的理由）：
      1. **挂到 document.body**：大屏浮层是 pointer-events: none，面板又是 overflow: hidden，
         弹窗留在里面会被裁掉。（面板原先还有 backdrop-filter、会成为 fixed 的包含块，
         该属性已因性能原因移除，见 styles/screen-mixins.less。挂 body 的做法保持不动。）
      2. **焦点陷阱**：打开时记住原焦点并移入弹窗，Tab 在弹窗内循环，
         关闭后把焦点还给触发元素——否则大屏上键盘用户会「丢焦点」到地图里。
      3. **滚动锁**：打开时锁掉 body 滚动，并用计数器兼容「弹窗里再开弹窗」。
  -->
  <div class="screen-modal-root">
    <transition name="screen-modal" :duration="motionDisabled ? 0 : 200">
      <div v-if="visible" class="screen-modal" :class="{ 'is-clear': !dimmed }">
        <div class="screen-modal__mask" :class="{ 'is-clear': !dimmed }" @click="handleMaskClick" />

        <div
          ref="dialog"
          class="screen-modal__dialog"
          :class="[`is-${size}`, { 'is-full': fullscreen }]"
          :style="dialogStyle"
          role="dialog"
          :aria-modal="dimmed ? 'true' : 'false'"
          :aria-labelledby="title ? titleId : undefined"
          :aria-label="title ? undefined : ariaLabel || '对话框'"
          tabindex="-1"
          @keydown="handleKeydown"
        >
          <!--
            标题栏。draggable 时它同时是拖动把手：
              · 鼠标/触摸 → handleDragStart（pointerdown）
              · 键盘 → tabindex 让它可聚焦，方向键移动（见 handleKeydown 的 MOVE_KEYS 分支）
            ★ 打开时的自动聚焦会**跳过**它（见 firstFocusable），否则用户进表单得先多按一次 Tab。
          -->
          <header
            ref="head"
            class="screen-modal__head"
            :class="{ 'is-draggable': draggable }"
            :tabindex="draggable ? 0 : undefined"
            @pointerdown="handleDragStart"
          >
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

/**
 * 方向键 → 单位位移（可拖拽弹窗的键盘通道）。
 * ★ 拖动不能只有鼠标：大屏上也有只用键盘的岗位，且「把窗口挪开看地图」
 *   这件事用方向键做比用鼠标更精确（按 Shift 走大步）。
 */
const MOVE_KEYS = {
  ArrowLeft: [-1, 0],
  ArrowRight: [1, 0],
  ArrowUp: [0, -1],
  ArrowDown: [0, 1]
}

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
    /**
     * 背景是否压暗。
     * ★ 关闭压暗时**必须同时**让遮罩不吃鼠标（见 &__mask.is-clear）：
     *   透明元素照样参与命中测试，只把颜色改透明的话，用户会「看得见下面的地图、
     *   却点不动」——这比压暗更难排查，因为肉眼完全看不出有一层东西挡着。
     * 默认 true：保持既有弹窗（档案、收发文…）的模态行为不变。
     */
    dimmed: { type: Boolean, default: true },
    /**
     * 是否可拖动（抓标题栏）。
     * 默认 false：既有弹窗的位置由 flex 居中决定，不给它们引入新的位移来源。
     */
    draggable: { type: Boolean, default: false }
  },
  data () {
    return {
      titleId: uid('screen-modal-title'),
      /** 打开前的焦点元素，关闭后还给它 */
      lastActiveElement: null,
      motionDisabled: false,
      /**
       * 拖动位移（**真实像素**，相对 flex 居中位置）。
       * 本弹窗由 mounted 挂到 document.body，不在 ScreenStage 的缩放画布内，
       * 所以这里不需要（也不能）除以画布缩放 k —— 与 ScreenResizeBar 正好相反。
       */
      dragOffset: { x: 0, y: 0 },
      /** 一次拖动的过程状态；不在拖动中时为 null */
      dragState: null
    }
  },
  computed: {
    dialogStyle () {
      const style = {}
      if (!this.fullscreen) {
        style.width = typeof this.width === 'number' ? `${this.width}px` : this.width
      }
      /*
       * 拖动位移用 left/top（position: relative 的相对偏移）落地，
       * ★ 不能用 transform：进出场动画就是靠 .screen-modal__dialog 的
       *   transform: translateY(10px) 做的，行内 transform 会把它整个盖掉，
       *   弹窗就失去「淡入 + 上浮」的动效了。
       */
      if (this.draggable && (this.dragOffset.x || this.dragOffset.y)) {
        style.left = `${this.dragOffset.x}px`
        style.top = `${this.dragOffset.y}px`
      }
      return style
    },
    bodyStyle () {
      return this.bodyMaxHeight ? { maxHeight: this.bodyMaxHeight } : {}
    }
  },
  watch: {
    visible (val, oldVal) {
      if (val === oldVal) return
      if (val) {
        this.handleOpen()
      } else {
        this.handleClose()
      }
    }
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
    // 拖动中途被销毁（父组件直接 v-if 掉弹窗）时必须收尾：
    // 否则 window 上留着 pointermove 监听与 body 上的 is-dragging-modal，光标一直是 move
    if (this.dragState) this.handleDragEnd(null)
    if (this.$el && this.$el.parentNode) {
      this.$el.parentNode.removeChild(this.$el)
    }
  },
  methods: {
    handleOpen () {
      this.lastActiveElement = document.activeElement
      // 每次打开都回到居中位置：否则「上次拖到角落」会被带进下一次，
      // 用户看到弹窗从角落里冒出来，会以为打开失败或者上次没关干净。
      this.dragOffset = { x: 0, y: 0 }
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
      // ★ 拖动把手（标题栏）不参与「打开时自动聚焦」：可拖拽弹窗里它排在 DOM 最前，
      //   不排除的话打开表单时光标会落在标题栏上，用户得先多按一次 Tab 才能开始填。
      const list = this.focusableElements().filter((el) => el !== this.$refs.head)
      return list.length ? list[0] : null
    },
    handleKeydown (event) {
      if (event.key === 'Escape') {
        if (!this.escClosable) return
        event.stopPropagation()
        this.handleCancel()
        return
      }

      // 可拖拽弹窗：焦点在标题栏（把手）上时，方向键移动弹窗，按住 Shift 走大步
      const move = MOVE_KEYS[event.key]
      if (this.draggable && move && event.target === this.$refs.head) {
        event.preventDefault()
        const step = event.shiftKey ? 40 : 16
        const dialog = this.$refs.dialog
        if (dialog) {
          this.applyDrag(move[0] * step, move[1] * step, dialog.getBoundingClientRect(), {
            x: this.dragOffset.x,
            y: this.dragOffset.y
          })
        }
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

    /* ==================== 拖动（draggable） ==================== */

    /**
     * 抓住标题栏开始拖动。
     *
     * ★ 位移用 event.clientX/Y 的差值直接算，**不除以画布缩放 k**：
     *   本弹窗由 mounted 挂到 document.body，压根不在 ScreenStage 的缩放画布内，
     *   它的几何量本来就是真实像素（与 ScreenResizeBar 的情况正好相反）。
     * ★ 监听挂在 window 而不是标题栏：指针一旦移出标题栏（拖快了很常见），
     *   元素上的 pointermove 就断了，表现是「弹窗跟不上鼠标」。
     *   配合 setPointerCapture 双保险（个别环境不支持捕获时靠 window 兜底）。
     */
    handleDragStart (event) {
      if (!this.draggable || event.button !== 0) return
      // 标题栏里的按钮 / 自定义 head-extra 不参与拖动，否则「点关闭」会变成拖窗口
      const target = event.target
      if (target && target.closest && target.closest('button, a, input, select, textarea, [role="button"]')) return
      const dialog = this.$refs.dialog
      if (!dialog) return
      event.preventDefault()
      this.dragState = {
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        // 拖动起点同时记下「位移」与「可视矩形」：夹紧范围必须按真实矩形算，
        // 不能假设弹窗是居中的（用户可能已经把它拖到别处了）
        base: { x: this.dragOffset.x, y: this.dragOffset.y },
        rect: dialog.getBoundingClientRect()
      }
      const head = this.$refs.head
      if (head && head.setPointerCapture && event.pointerId !== undefined) {
        try { head.setPointerCapture(event.pointerId) } catch (e) { /* 不支持捕获，window 监听兜底 */ }
      }
      window.addEventListener('pointermove', this.handleDragMove)
      window.addEventListener('pointerup', this.handleDragEnd)
      window.addEventListener('pointercancel', this.handleDragEnd)
      document.body.classList.add('is-dragging-modal')
    },

    handleDragMove (event) {
      const state = this.dragState
      if (!state) return
      // 多指 / 多指针时只认一开始那根手指
      if (state.pointerId !== undefined && event.pointerId !== undefined && event.pointerId !== state.pointerId) return
      event.preventDefault()
      this.applyDrag(event.clientX - state.startX, event.clientY - state.startY, state.rect, state.base)
    },

    handleDragEnd (event) {
      const state = this.dragState
      if (!state) return
      this.dragState = null
      window.removeEventListener('pointermove', this.handleDragMove)
      window.removeEventListener('pointerup', this.handleDragEnd)
      window.removeEventListener('pointercancel', this.handleDragEnd)
      document.body.classList.remove('is-dragging-modal')
      const head = this.$refs.head
      if (head && head.releasePointerCapture && state.pointerId !== undefined) {
        try { head.releasePointerCapture(state.pointerId) } catch (e) { /* 已经释放过了 */ }
      }
      if (event && event.type === 'pointerup') {
        this.$emit('drag-end', { x: this.dragOffset.x, y: this.dragOffset.y })
      }
    },

    /**
     * 把「期望位移」夹进「弹窗不会被拖丢」的范围，再落到 dragOffset 上。
     *
     * 夹紧判据（两条都是必须的）：
     *   · 横向：左右各至少留 80px 露在视口里 —— 完全推出屏幕后用户就再也抓不到它了；
     *   · 纵向：标题栏（唯一的把手）必须完整留在视口内。
     * 不做夹紧的话，「随意拖动」在触摸板上一滑就变成「弹窗消失」。
     *
     * @param {number} dx 期望横向位移（真实像素）
     * @param {number} dy 期望纵向位移（真实像素）
     * @param {DOMRect} rect 位移生效**前**的可视矩形
     * @param {{x:number,y:number}} base 位移生效**前**的 dragOffset
     */
    applyDrag (dx, dy, rect, base) {
      const vw = window.innerWidth
      const vh = window.innerHeight
      const headHeight = this.$refs.head ? this.$refs.head.getBoundingClientRect().height : 46
      const minDx = 80 - rect.right
      const maxDx = vw - 80 - rect.left
      const minDy = -rect.top
      const maxDy = vh - headHeight - rect.top
      // 用 Math.max(min, max) 兜底：弹窗比视口还宽时区间会反向，此时固定在左边界
      const cx = Math.min(Math.max(dx, minDx), Math.max(minDx, maxDx))
      const cy = Math.min(Math.max(dy, minDy), Math.max(minDy, maxDy))
      const next = { x: Math.round(base.x + cx), y: Math.round(base.y + cy) }
      if (next.x === this.dragOffset.x && next.y === this.dragOffset.y) return
      this.dragOffset = next
    }
  }
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
  /*
   * ★ 整屏容器自己**不吃**鼠标，事件只由两个子元素按需接：
   *     .screen-modal__mask   默认 auto（模态：挡住背景点击，这是既有行为）
   *     .screen-modal__dialog auto（弹窗本体）
   *   父级 none + 子级 auto 是合法的：子元素可以重新打开自己的命中测试。
   *   这么写是为了让 :dimmed="false" 的弹窗能「只改一个类」就放行背景
   *   （见 &__mask.is-clear），不必去改根节点的 pointer-events。
   */
  pointer-events: none;

  &__mask {
    position: absolute;
    inset: 0;
    // 遮罩要足够实，保证弹窗正文在任何背景（含地图）上都读得清。
    // ⚠ 这里**不要加 backdrop-filter**：这是整屏大小的一层，没有硬件加速时
    //   模糊整屏会把「打开弹窗」拖到 200~300ms 才出画面（见 screen-mixins.less
    //   里 .screen-glass 的实测数据）。0.72 的底色已经足够压暗背景。
    background: rgba(1, 14, 17, 0.72);
    pointer-events: auto;

    /*
     * :dimmed="false"：背景不压暗。
     * ★★ 必须**同时** pointer-events: none。只把颜色改透明的话，
     *    这一层仍然铺满视口参与命中测试 —— 用户看得见下面的地图却拖不动、点不到，
     *    而且肉眼完全看不出有东西挡着（透明层没有任何视觉线索）。
     *    这是本项目反复踩过的一类坑：透明 ≠ 不接事件。
     */
    &.is-clear {
      background: transparent;
      pointer-events: none;
    }
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
    // 弹窗本体必须自己收事件（外层是 pointer-events: none）
    pointer-events: auto;

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
    // 拖动时手指/鼠标可能划出标题栏，选中文字会打断拖动
    user-select: none;
    -webkit-user-select: none;

    /* 可拖拽：光标提示这是把手，并给一条焦点环（键盘用户按方向键前先 Tab 到它） */
    &.is-draggable {
      cursor: move;
      touch-action: none;
      .screen-focus-ring();
    }
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

<!--
  拖动期间的全局收尾（**故意不加 scoped**：要作用在 body 上）。
  拖动时指针会离开标题栏、划过页面里的任意元素，光标必须整屏保持一致，
  否则「光标在标题栏上是 move、移出去立刻变回箭头」，看起来像拖丢了。
  与 ScreenResizeBar 的 body.is-resizing-panel 是同一套做法。
-->
<style lang="less">
body.is-dragging-modal {
  cursor: move;

  * {
    // !important 是必要的：拖过的元素（按钮、链接）都有自己的 cursor 声明
    cursor: move !important;
    user-select: none;
    -webkit-user-select: none;
  }
}
</style>
