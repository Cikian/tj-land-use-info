<template>
  <!--
    ScreenStatValue 大号统计数值
    --------------------------------
    设计稿中的「817 宗」「426 宗」：薄荷青大号数字 + 小号单位，可选前置标签与副说明。
  -->
  <div class="screen-stat-value" :class="[`is-${size}`, `is-${align}`, `is-${tone}`]">
    <span v-if="label" class="screen-stat-value__label">{{ label }}</span>
    <div class="screen-stat-value__main">
      <screen-count-up
        class="screen-stat-value__number"
        :value="value"
        :precision="precision"
        :duration="duration"
        :animated="animated"
      />
      <span v-if="unit" class="screen-stat-value__unit">{{ unit }}</span>
    </div>
    <span v-if="hint" class="screen-stat-value__hint">{{ hint }}</span>
  </div>
</template>

<script>
import ScreenCountUp from './ScreenCountUp'

export default {
  name: 'ScreenStatValue',
  components: { ScreenCountUp },
  props: {
    /** 数值 */
    value: { type: [Number, String], default: 0 },
    /** 单位，例如「宗」「条」 */
    unit: { type: String, default: '' },
    /** 数值上方的标签 */
    label: { type: String, default: '' },
    /** 数值下方的补充说明 */
    hint: { type: String, default: '' },
    /** 尺寸：lg 面板主数值 / md 次级 / sm 紧凑 */
    size: { type: String, default: 'lg' },
    /** 对齐方式 */
    align: { type: String, default: 'left' },
    /** 语义色 */
    tone: { type: String, default: 'accent' },
    /** 小数位 */
    precision: { type: Number, default: 0 },
    /** 滚动动画时长 */
    duration: { type: Number, default: 900 },
    /** 是否播放滚动动画 */
    animated: { type: Boolean, default: true },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-stat-value {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  &__label {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    letter-spacing: 0.04em;
    .screen-ellipsis();
  }

  &__main {
    display: flex;
    align-items: baseline;
    gap: 6px;
    min-width: 0;
  }

  &__number {
    .screen-number-font(clamp(30px, 3.1vw, 54px), 700);
    color: var(--screen-number);
    text-shadow: 0 0 16px rgba(98, 240, 196, 0.38);
  }

  &__unit {
    font-size: var(--screen-font-md);
    color: var(--screen-accent-soft);
  }

  &__hint {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  // ---- 尺寸变体 ----
  &.is-md &__number {
    .screen-number-font(clamp(22px, 2vw, 34px), 700);
  }

  &.is-sm &__number {
    .screen-number-font(var(--screen-font-xl), 600);
  }

  // ---- 对齐变体 ----
  &.is-center {
    align-items: center;
    text-align: center;
  }

  &.is-right {
    align-items: flex-end;
    text-align: right;
  }

  // ---- 语义色 ----
  &.is-success &__number {
    color: var(--screen-success);
    text-shadow: 0 0 16px rgba(67, 233, 114, 0.34);
  }

  &.is-warning &__number {
    color: var(--screen-warning);
    text-shadow: 0 0 16px rgba(240, 168, 60, 0.34);
  }

  &.is-danger &__number {
    color: var(--screen-danger);
    text-shadow: 0 0 16px rgba(255, 122, 107, 0.34);
  }
}
</style>
