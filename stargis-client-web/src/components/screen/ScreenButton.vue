<template>
  <!--
    ScreenButton 大屏按钮
    --------------------------------
    设计稿里的按钮是「青绿描边玻璃块」：主按钮实心青绿，次按钮描边透明底。
    与 antd 按钮的差别：全部走 --screen-* 令牌，悬停只改描边/底色不改尺寸，
    按下状态用底色加深而不是位移，避免大屏上出现「按钮抖动」。

    用法：
      <screen-button type="primary" icon="plus" @click="add">新增档案</screen-button>
      <screen-button icon="reload" :loading="loading" @click="load">刷新</screen-button>
      <screen-button icon="trash" type="danger" aria-label="删除" square @click="del" />
  -->
  <button
    type="button"
    class="screen-btn"
    :class="[`is-${type}`, `is-${size}`, { 'is-loading': loading, 'is-square': square, 'is-block': block }]"
    :disabled="disabled || loading"
    :aria-busy="loading ? 'true' : undefined"
    :aria-label="ariaLabel || undefined"
    @click="handleClick"
  >
    <!-- 加载态用旋转图标替代原图标，宽度不变，避免布局跳动 -->
    <screen-icon v-if="loading" name="reload" :size="iconSize" spin />
    <screen-icon v-else-if="icon" :name="icon" :size="iconSize" />

    <span v-if="$slots.default || !square" class="screen-btn__label">
      <slot />
    </span>
  </button>
</template>

<script>
import ScreenIcon from './ScreenIcon'

export default {
  name: 'ScreenButton',
  components: { ScreenIcon },
  props: {
    /** 外观：default 描边 / primary 实心青绿 / danger 危险 / text 纯文字 */
    type: { type: String, default: 'default' },
    /** 尺寸：sm 28 / md 32 / lg 36 */
    size: { type: String, default: 'md' },
    /** 前置图标名（见 ScreenIcon） */
    icon: { type: String, default: '' },
    /** 加载中：禁用点击并展示旋转图标 */
    loading: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    /** 撑满父容器宽度 */
    block: { type: Boolean, default: false },
    /** 正方形（纯图标按钮）。此时必须提供 ariaLabel */
    square: { type: Boolean, default: false },
    /** 纯图标按钮的无障碍名称 */
    ariaLabel: { type: String, default: '' },
  },
  computed: {
    iconSize () {
      return this.size === 'lg' ? 16 : 14
    },
  },
  methods: {
    handleClick (event) {
      if (this.disabled || this.loading) return
      this.$emit('click', event)
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex: 0 0 auto;
  min-height: 32px;
  padding: 0 12px;
  font-family: inherit;
  font-size: var(--screen-font-sm);
  line-height: 1;
  white-space: nowrap;
  color: var(--screen-text);
  background: rgba(6, 20, 40, 0.72);
  border: 1px solid var(--screen-border);
  border-radius: var(--screen-radius-sm);
  cursor: pointer;
  transition: color var(--screen-duration) var(--screen-ease),
    background-color var(--screen-duration) var(--screen-ease),
    border-color var(--screen-duration) var(--screen-ease);
  .screen-focus-ring();

  // 悬停：提亮描边并轻微加底色，不改变任何尺寸属性
  &:hover:not(:disabled) {
    color: var(--screen-accent);
    background: rgba(130, 198, 255, 0.1);
    border-color: var(--screen-border-strong);
  }

  // 按下：加深底色，位移为 0
  &:active:not(:disabled) {
    background: rgba(130, 198, 255, 0.2);
  }

  &:disabled {
    color: var(--screen-text-mute);
    background: rgba(6, 20, 40, 0.38);
    border-color: var(--screen-border-soft);
    cursor: not-allowed;
  }

  &__label {
    min-width: 0;
    .screen-ellipsis();
  }

  // ---------- 尺寸 ----------
  &.is-sm {
    min-height: 28px;
    padding: 0 10px;
    font-size: var(--screen-font-xs);
  }

  &.is-lg {
    min-height: 36px;
    padding: 0 16px;
    font-size: var(--screen-font-md);
  }

  &.is-square {
    padding: 0;
    width: 32px;

    &.is-sm {
      width: 28px;
    }

    &.is-lg {
      width: 36px;
    }
  }

  &.is-block {
    width: 100%;
  }

  // ---------- 主按钮：实心青绿 ----------
  &.is-primary {
    color: var(--screen-text-on-accent);
    background: linear-gradient(180deg, var(--screen-accent) 0%, var(--screen-viz-mint) 100%);
    border-color: var(--screen-accent);
    font-weight: 600;

    &:hover:not(:disabled) {
      color: var(--screen-text-on-accent);
      background: linear-gradient(180deg, #6cc4ff 0%, #2f8ce0 100%);
      border-color: var(--screen-accent-bright);
    }

    &:active:not(:disabled) {
      background: linear-gradient(180deg, #57b6f5 0%, #1d5fd0 100%);
    }

    &:disabled {
      color: var(--screen-text-mute);
      background: rgba(130, 198, 255, 0.14);
      border-color: var(--screen-border-soft);
    }
  }

  // ---------- 危险按钮 ----------
  &.is-danger {
    color: var(--screen-danger);
    border-color: rgba(255, 122, 107, 0.4);
    background: rgba(255, 122, 107, 0.08);

    &:hover:not(:disabled) {
      color: #ffab9f;
      background: rgba(255, 122, 107, 0.18);
      border-color: var(--screen-danger);
    }

    &:active:not(:disabled) {
      background: rgba(255, 122, 107, 0.28);
    }
  }

  // ---------- 纯文字按钮 ----------
  &.is-text {
    padding: 0 6px;
    color: var(--screen-accent);
    background: transparent;
    border-color: transparent;

    &:hover:not(:disabled) {
      color: var(--screen-accent-bright);
      background: rgba(130, 198, 255, 0.1);
      border-color: transparent;
    }
  }
}
</style>
