<template>
  <!--
    ScreenTag 大屏状态标签（胶囊）
    --------------------------------
    档案管理模块用它承载「状态 / 类型」这类短文案：在编、已归档、待整改、已超期……
    视觉沿用 ScreenDataTable 中 .screen-data-table__tag 的语言：
    胶囊圆角（--screen-radius-pill）+ 1px 描边 + 同色系淡染底 + --screen-font-xs。

    用法：
      <screen-tag>在编</screen-tag>
      <screen-tag tone="warning">待整改</screen-tag>
      <screen-tag tone="danger" size="sm" outline>已超期</screen-tag>
      <screen-tag tone="muted">已停用</screen-tag>

    无障碍：
      - 标签是纯展示文案、不可交互，因此不需要焦点环，也不需要 aria-* 状态；
      - 语气只靠颜色传达是不充分的，所以调用方的文案本身必须带语义
        （写「已超期」而不是写「3」）；组件默认不插图标，以免破坏表格里的行高节奏，
        确有需要时由调用方在默认插槽里自行组合 <screen-icon />；
      - 组件不含任何过渡/动画，无需额外的 reduced-motion 处理。
  -->
  <span class="screen-tag" :class="[`is-${toneClass}`, `is-${sizeClass}`, { 'is-outline': outline }]">
    <span class="screen-tag__label"><slot /></span>
  </span>
</template>

<script>
/** 允许的语气取值，未知取值一律回退到 accent，避免出现没有样式的裸标签 */
const TONES = ['accent', 'success', 'warning', 'danger', 'info', 'muted']

export default {
  name: 'ScreenTag',
  props: {
    /** 语气：accent 主强调 / success 成功 / warning 预警 / danger 危险 / info 提示 / muted 弱化 */
    tone: { type: String, default: 'accent' },
    /** 尺寸：sm 更紧凑（表格内） / md 常规 */
    size: { type: String, default: 'md' },
    /** 描边变体：透明底 + 语气色描边与文字（用于底色相近、需要更轻的场合） */
    outline: { type: Boolean, default: false },
  },
  computed: {
    /** 校验后的语气类名 */
    toneClass () {
      return TONES.indexOf(this.tone) > -1 ? this.tone : 'accent'
    },
    /** 校验后的尺寸类名 */
    sizeClass () {
      return this.size === 'sm' ? 'sm' : 'md'
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-tag {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 1px 8px;
  font-size: var(--screen-font-xs);
  line-height: 18px;
  color: var(--screen-accent-soft);
  background: rgba(130, 198, 255, 0.08);
  border: 1px solid var(--screen-border);
  border-radius: var(--screen-radius-pill);

  // 文案用一层 span 包裹，才能正确触发单行省略
  &__label {
    min-width: 0;
    .screen-ellipsis();
  }

  // ---------- 尺寸 ----------
  &.is-sm {
    padding: 0 6px;
    line-height: 16px;
  }

  // ---------- 语气 ----------
  // 说明：语气只体现在「文字色 + 淡染底」上；描边统一用 --screen-border 令牌，
  //       不使用 ScreenDataTable 里 rgba(34,199,149,0.3) 这类带透明度的硬编码描边，
  //       以保证颜色全部来自 --screen-* 令牌（见需求硬性规则 5）。
  &.is-success {
    color: var(--screen-success);
    background: rgba(67, 233, 114, 0.1);
  }

  &.is-warning {
    color: var(--screen-warning);
    background: rgba(240, 168, 60, 0.1);
  }

  &.is-danger {
    color: var(--screen-danger);
    background: rgba(255, 122, 107, 0.1);
  }

  // info 无专属淡染令牌，复用已在本库中使用的 accent 0.08 淡染底，
  // 文字用 --screen-info（蓝）与 accent（青）区分，避免引入新的硬编码颜色。
  &.is-info {
    color: var(--screen-info);
    background: rgba(130, 198, 255, 0.08);
  }

  &.is-muted {
    color: var(--screen-text-mute);
    background: var(--screen-row-alt);
    border-color: var(--screen-border-soft);
  }

  // ---------- 描边变体：透明底 + 语气色描边与文字 ----------
  &.is-outline {
    background: transparent;

    &.is-accent {
      color: var(--screen-accent);
      border-color: var(--screen-accent);
    }

    &.is-success {
      color: var(--screen-success);
      border-color: var(--screen-success);
    }

    &.is-warning {
      color: var(--screen-warning);
      border-color: var(--screen-warning);
    }

    &.is-danger {
      color: var(--screen-danger);
      border-color: var(--screen-danger);
    }

    &.is-info {
      color: var(--screen-info);
      border-color: var(--screen-info);
    }

    &.is-muted {
      color: var(--screen-text-mute);
      border-color: var(--screen-text-mute);
    }
  }
}
</style>
