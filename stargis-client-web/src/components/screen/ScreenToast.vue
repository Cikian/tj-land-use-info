<template>
  <!--
    ScreenToast 大屏消息提示容器
    --------------------------------
    大屏是固化的青绿暗色主题，antd 的 $message 是浅色气泡，直接使用会破坏整体观感。
    这里提供一套同族的消息提示，并由 toast.js 暴露成命令式 API：

      this.$screenToast.success('保存成功')

    无障碍：
      - 整个容器是一个 aria-live 区域，success/info 用 polite，error/warning 用 assertive；
      - 颜色不是唯一线索，每种语气都配一个语义图标；
      - 关闭按钮有明确的 aria-label。
  -->
  <div class="screen-toast" role="region" aria-label="消息提示">
    <transition-group name="screen-toast" tag="div" class="screen-toast__list">
      <div
        v-for="item in items"
        :key="item.id"
        class="screen-toast__item"
        :class="`is-${item.tone}`"
        :role="assertive(item.tone) ? 'alert' : 'status'"
        :aria-live="assertive(item.tone) ? 'assertive' : 'polite'"
      >
        <screen-icon class="screen-toast__icon" :name="iconOf(item.tone)" :size="15" />
        <span class="screen-toast__text">{{ item.message }}</span>
        <button
          type="button"
          class="screen-toast__close"
          aria-label="关闭提示"
          @click="remove(item.id)"
        >
          <screen-icon name="close" :size="12" />
        </button>
      </div>
    </transition-group>
  </div>
</template>

<script>
import ScreenIcon from './ScreenIcon'

const TONE_ICON = {
  success: 'check-circle',
  danger: 'x-circle',
  warning: 'alert-triangle',
  info: 'info',
}

const ASSERTIVE_TONES = ['danger', 'warning']

/** 同时最多展示几条，超出后挤掉最早的，避免大屏被提示铺满 */
const MAX_ITEMS = 5

export default {
  name: 'ScreenToast',
  components: { ScreenIcon },
  data () {
    return {
      items: [],
      seed: 0,
    }
  },
  methods: {
    iconOf (tone) {
      return TONE_ICON[tone] || 'info'
    },
    assertive (tone) {
      return ASSERTIVE_TONES.indexOf(tone) > -1
    },
    /** 命令式入口：由 toast.js 调用 */
    push (message, tone = 'info', duration = 2600) {
      if (!message) return null
      this.seed += 1
      const id = this.seed
      this.items.push({ id, message: String(message), tone })

      if (this.items.length > MAX_ITEMS) {
        this.items.splice(0, this.items.length - MAX_ITEMS)
      }
      if (duration > 0) {
        setTimeout(() => this.remove(id), duration)
      }
      return id
    },
    remove (id) {
      const index = this.items.findIndex((item) => item.id === id)
      if (index > -1) this.items.splice(index, 1)
    },
    clear () {
      this.items = []
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-toast {
  position: fixed;
  top: 72px;
  right: var(--screen-space-5);
  z-index: 1300;
  display: flex;
  justify-content: flex-end;
  // 容器本身不吃事件，只有提示条可交互
  pointer-events: none;

  &__list {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: var(--screen-space-2);
  }

  &__item {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    max-width: 420px;
    padding: 9px 10px 9px 12px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text);
    .screen-popover-surface();
    // 左侧语气色条：与图标一起构成非颜色单一线索
    border-left: 3px solid var(--screen-accent);
    pointer-events: auto;

    &.is-success {
      border-left-color: var(--screen-success);

      .screen-toast__icon {
        color: var(--screen-success);
      }
    }

    &.is-warning {
      border-left-color: var(--screen-warning);

      .screen-toast__icon {
        color: var(--screen-warning);
      }
    }

    &.is-danger {
      border-left-color: var(--screen-danger);

      .screen-toast__icon {
        color: var(--screen-danger);
      }
    }

    &.is-info {
      border-left-color: var(--screen-info);

      .screen-toast__icon {
        color: var(--screen-info);
      }
    }
  }

  &__icon {
    margin-top: 1px;
  }

  &__text {
    min-width: 0;
    line-height: 1.5;
    word-break: break-word;
  }

  &__close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    width: 20px;
    height: 20px;
    padding: 0;
    margin-left: 2px;
    color: var(--screen-text-mute);
    background: transparent;
    border: 0;
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-text);
    }
  }
}

/* 入场/离场：只做位移 + 透明度，不做缩放，避免文字重排 */
.screen-toast-enter-active,
.screen-toast-leave-active {
  transition: opacity var(--screen-duration) var(--screen-ease),
    transform var(--screen-duration) var(--screen-ease);
}

.screen-toast-enter,
.screen-toast-leave-to {
  opacity: 0;
  transform: translateX(12px);
}

@media (prefers-reduced-motion: reduce) {
  .screen-toast-enter-active,
  .screen-toast-leave-active {
    transition: none;
  }

  .screen-toast-enter,
  .screen-toast-leave-to {
    transform: none;
  }
}
</style>
