<template>
  <!--
    ScreenRadioGroup 分段单选
    --------------------------------
    档案类型（电子/纸质）、类别状态筛选（全部/启用/停用）等「选项少、需要一眼看全」
    的场景使用。视觉上是连成一排的分段控件，选中项填充青绿底。

    为什么不用 a-radio-group：antd 的浅色单选框在暗色面板上对比度不足，
    且圆点样式与设计稿的「胶囊分段」不是一种语言。

    用法：
      <screen-radio-group v-model="model.archiveType" :options="archiveTypeOptions" />
      <screen-radio-group v-model="statusFilter" :options="[{value:'all',label:'全部'},{value:1,label:'启用'}]" size="sm" />

    无障碍：容器 role="radiogroup"，每项 role="radio" + aria-checked；
    采用 roving tabindex（只有选中项 tabindex=0），←→ 切换、Home/End 跳首尾，
    符合 WAI-ARIA radio 组键盘约定。
  -->
  <div
    class="screen-radio-group"
    :class="[`is-${size}`, { 'is-disabled': disabled }]"
    role="radiogroup"
    :aria-label="ariaLabel || undefined"
    :aria-disabled="disabled ? 'true' : undefined"
    :id="id || undefined"
    @keydown="handleKeydown"
  >
    <button
      v-for="(option, index) in normalizedOptions"
      :key="option.value"
      type="button"
      class="screen-radio-group__item"
      :class="{ 'is-active': option.value === value, 'is-disabled': option.disabled }"
      role="radio"
      :aria-checked="option.value === value ? 'true' : 'false'"
      :aria-disabled="option.disabled ? 'true' : undefined"
      :tabindex="tabIndexFor(option, index)"
      :data-index="index"
      :disabled="disabled || option.disabled"
      @click="select(option)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<script>
export default {
  name: 'ScreenRadioGroup',
  props: {
    /** 当前值（v-model） */
    value: { type: [String, Number], default: '' },
    /** 选项：`[{ value, label, disabled }]` 或字符串数组 */
    options: { type: Array, default: () => [] },
    disabled: { type: Boolean, default: false },
    /** 尺寸：sm 26 / md 30 */
    size: { type: String, default: 'md' },
    /** 组名称（无可见标签时必填，供读屏播报） */
    ariaLabel: { type: String, default: '' },
    id: { type: String, default: '' },
  },
  computed: {
    normalizedOptions () {
      return (this.options || []).map((item) => {
        if (item === null || item === undefined) {
          return { value: '', label: '', disabled: true }
        }
        if (typeof item === 'object') {
          return {
            value: item.value === undefined ? item.label : item.value,
            label: item.label === undefined ? String(item.value) : item.label,
            disabled: !!item.disabled,
          }
        }
        return { value: item, label: String(item), disabled: false }
      })
    },
    enabledOptions () {
      return this.normalizedOptions.filter((option) => !option.disabled)
    },
    /** 当前选中项的原始索引；没选中时退回第一个可选项，保证组内永远有一个 tab 落点 */
    activeIndex () {
      return this.normalizedOptions.findIndex((option) => option.value === this.value)
    },
  },
  methods: {
    tabIndexFor (option, index) {
      if (option.disabled) return -1
      if (this.activeIndex > -1) return option.value === this.value ? 0 : -1
      // 未选中任何项：第一个可选项作为入口
      const firstEnabled = this.normalizedOptions.findIndex((item) => !item.disabled)
      return index === firstEnabled ? 0 : -1
    },
    select (option) {
      if (this.disabled || option.disabled || option.value === this.value) return
      this.$emit('input', option.value)
      this.$emit('change', option.value, option)
    },
    focusIndex (index) {
      this.$nextTick(() => {
        const el = this.$el.querySelector(`[data-index="${index}"]`)
        if (el && el.focus) el.focus()
      })
    },
    /** ←→ 在同组内移动并即时选中（WAI-ARIA radio 的推荐行为） */
    handleKeydown (event) {
      if (this.disabled) return
      const enabled = this.normalizedOptions
        .map((option, index) => ({ option, index }))
        .filter((entry) => !entry.option.disabled)
      if (!enabled.length) return

      const current = enabled.findIndex((entry) => entry.option.value === this.value)
      let next = current

      switch (event.key) {
        case 'ArrowRight':
        case 'ArrowDown':
          event.preventDefault()
          next = current < 0 ? 0 : (current + 1) % enabled.length
          break
        case 'ArrowLeft':
        case 'ArrowUp':
          event.preventDefault()
          next = current < 0 ? enabled.length - 1 : (current - 1 + enabled.length) % enabled.length
          break
        case 'Home':
          event.preventDefault()
          next = 0
          break
        case 'End':
          event.preventDefault()
          next = enabled.length - 1
          break
        default:
          return
      }

      this.select(enabled[next].option)
      this.focusIndex(enabled[next].index)
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-radio-group {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  background: rgba(2, 24, 28, 0.72);
  border: 1px solid var(--screen-border);
  border-radius: var(--screen-radius-sm);

  &__item {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 26px;
    padding: 0 12px;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    line-height: 1;
    white-space: nowrap;
    color: var(--screen-text-sub);
    background: transparent;
    border: 1px solid transparent;
    border-radius: 3px;
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover:not(.is-disabled):not(.is-active) {
      color: var(--screen-text);
      background: rgba(47, 227, 192, 0.08);
    }

    // 选中态：底色 + 描边 + 字重三重线索，不依赖颜色单一传达
    &.is-active {
      color: var(--screen-accent);
      font-weight: 600;
      background: rgba(47, 227, 192, 0.14);
      border-color: var(--screen-border-strong);
      text-shadow: 0 0 8px var(--screen-accent-glow);
    }

    &.is-disabled {
      color: var(--screen-text-mute);
      cursor: not-allowed;
    }
  }

  &.is-sm &__item {
    min-height: 22px;
    padding: 0 9px;
    font-size: var(--screen-font-xs);
  }

  &.is-disabled {
    .screen-control-disabled();
  }
}

@media (prefers-reduced-motion: reduce) {
  .screen-radio-group__item {
    transition: none;
  }
}
</style>
