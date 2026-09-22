<template>
  <!--
    ScreenTabs 大屏标签页
    --------------------------------
    设计稿底部「市级项目预警 / 区级项目预警 / 地块预警信息」：
    胶囊高亮当前项。支持 v-model 与键盘左右方向键切换。

    tabs: [{ key, label, disabled, badge }]
  -->
  <div class="screen-tabs" :class="[`is-${type}`, `is-${align}`]" role="tablist" @keydown="handleKeydown">
    <button
      v-for="(tab, index) in tabs"
      :key="tab.key"
      type="button"
      role="tab"
      class="screen-tabs__item"
      :class="{ 'is-active': tab.key === value, 'is-disabled': tab.disabled }"
      :aria-selected="String(tab.key === value)"
      :aria-disabled="tab.disabled ? 'true' : undefined"
      :tabindex="tab.key === value ? 0 : -1"
      :disabled="tab.disabled"
      :data-index="index"
      @click="select(tab)"
    >
      <span class="screen-tabs__label">{{ tab.label }}</span>
      <em v-if="tab.badge !== undefined && tab.badge !== null" class="screen-tabs__badge">{{ tab.badge }}</em>
    </button>
  </div>
</template>

<script>
export default {
  name: 'ScreenTabs',
  props: {
    /** 标签项：[{ key, label, disabled, badge }] */
    tabs: { type: Array, default: () => [] },
    /** 当前激活 key，配合 v-model 使用 */
    value: { type: [String, Number], default: '' },
    /** 外观：pill 胶囊 / underline 下划线 */
    type: { type: String, default: 'pill' },
    /** 对齐方式 */
    align: { type: String, default: 'left' },
  },
  methods: {
    select (tab) {
      if (tab.disabled || tab.key === this.value) return
      this.$emit('input', tab.key)
      this.$emit('change', tab.key, tab)
    },
    /** 方向键 / Home / End 在标签间移动焦点，符合 WAI-ARIA tabs 模式 */
    handleKeydown (event) {
      const keys = ['ArrowLeft', 'ArrowRight', 'Home', 'End']
      if (keys.indexOf(event.key) === -1) return
      event.preventDefault()

      const enabled = this.tabs.filter((tab) => !tab.disabled)
      if (!enabled.length) return

      const current = enabled.findIndex((tab) => tab.key === this.value)
      let next = current
      if (event.key === 'Home') next = 0
      else if (event.key === 'End') next = enabled.length - 1
      else if (event.key === 'ArrowLeft') next = (current - 1 + enabled.length) % enabled.length
      else next = (current + 1) % enabled.length

      const target = enabled[next]
      this.select(target)
      this.$nextTick(() => {
        const el = this.$el.querySelector(`[data-index="${this.tabs.indexOf(target)}"]`)
        el && el.focus()
      })
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-tabs {
  display: flex;
  align-items: center;
  gap: var(--screen-space-1);
  min-width: 0;

  &.is-center {
    justify-content: center;
  }

  &.is-right {
    justify-content: flex-end;
  }

  &__item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex: 0 0 auto;
    padding: 4px 14px;
    font-size: var(--screen-font-sm);
    line-height: 1.5;
    color: var(--screen-text-sub);
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover:not(.is-disabled) {
      color: var(--screen-text);
      background: rgba(130, 198, 255, 0.08);
    }

    &.is-active {
      color: var(--screen-accent);
      background: rgba(130, 198, 255, 0.12);
      border-color: var(--screen-border-strong);
      text-shadow: 0 0 8px var(--screen-accent-glow);
    }

    &.is-disabled {
      color: var(--screen-text-mute);
      cursor: not-allowed;
      opacity: 0.6;
    }
  }

  &__badge {
    padding: 0 5px;
    font-style: normal;
    font-size: var(--screen-font-xs);
    line-height: 15px;
    border-radius: var(--screen-radius-pill);
    background: rgba(130, 198, 255, 0.16);
    color: var(--screen-accent);
  }

  // 下划线样式：用底部指示条替代胶囊底
  &.is-underline &__item {
    position: relative;
    border-radius: 0;
    padding: 4px 12px 8px;

    &.is-active {
      background: transparent;
      border-color: transparent;

      &::after {
        content: '';
        position: absolute;
        left: 12px;
        right: 12px;
        bottom: 0;
        height: 2px;
        border-radius: var(--screen-radius-pill);
        background: linear-gradient(90deg, var(--screen-accent) 0%, var(--screen-accent-deep) 100%);
        box-shadow: 0 0 8px var(--screen-accent-glow);
      }
    }
  }
}
</style>
