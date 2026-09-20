<template>
  <!--
    ScreenInput 大屏输入框
    --------------------------------
    单行文本 / 多行文本 / 数字 / 密码，统一走「暗色玻璃控件」外观，
    与 ScreenSelect / ScreenDateInput 高度一致，保证同一行筛选条能对齐。

    用法：
      <screen-input v-model="query.ptxmmc" placeholder="按项目名称筛选" clearable @enter="search" />
      <screen-input v-model="model.remark" type="textarea" :rows="2" :maxlength="1000" />

    为什么不用 antd 的 a-input：
      大屏是固定青绿暗色主题，antd 输入框是浅色底 + 蓝色聚焦环，直接使用观感割裂。
  -->
  <div
    class="screen-input"
    :class="[`is-${size}`, { 'is-disabled': disabled, 'is-invalid': invalid, 'is-textarea': isTextarea }]"
  >
    <screen-icon v-if="icon && !isTextarea" class="screen-input__icon" :name="icon" :size="14" />

    <textarea
      v-if="isTextarea"
      ref="control"
      class="screen-input__area"
      :id="id || undefined"
      :value="value"
      :rows="rows"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :maxlength="maxlength"
      :aria-invalid="invalid ? 'true' : undefined"
      :aria-required="required ? 'true' : undefined"
      @input="handleInput"
      @change="handleChange"
      @focus="$emit('focus', $event)"
      @blur="$emit('blur', $event)"
      @keydown.enter="$emit('enter', $event)"
    />

    <input
      v-else
      ref="control"
      class="screen-input__control"
      :id="id || undefined"
      :type="nativeType"
      :value="value"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :maxlength="maxlength"
      :min="min"
      :max="max"
      :step="step"
      :autocomplete="autocomplete"
      :aria-invalid="invalid ? 'true' : undefined"
      :aria-required="required ? 'true' : undefined"
      @input="handleInput"
      @change="handleChange"
      @focus="$emit('focus', $event)"
      @blur="$emit('blur', $event)"
      @keydown.enter="$emit('enter', $event)"
    />

    <!-- 清除按钮：有值、可清除、未禁用时才出现，避免空态多余焦点位 -->
    <button
      v-if="showClear"
      type="button"
      class="screen-input__clear"
      aria-label="清除输入内容"
      @mousedown.prevent
      @click="handleClear"
    >
      <screen-icon name="close" :size="12" />
    </button>

    <slot name="suffix" />
  </div>
</template>

<script>
import ScreenIcon from './ScreenIcon'

export default {
  name: 'ScreenInput',
  components: { ScreenIcon },
  props: {
    /** 输入值（v-model） */
    value: { type: [String, Number], default: '' },
    /** 类型：text / password / number / search / textarea */
    type: { type: String, default: 'text' },
    /** 占位文案 */
    placeholder: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
    readonly: { type: Boolean, default: false },
    /** 必填语义（用于读屏，视觉标记由 ScreenField 负责） */
    required: { type: Boolean, default: false },
    /** 校验失败态；调用方需同时渲染错误文案 */
    invalid: { type: Boolean, default: false },
    /** 最大长度 */
    maxlength: { type: [String, Number], default: null },
    /** textarea 行数 */
    rows: { type: Number, default: 3 },
    /** 尺寸：sm 28 / md 32 */
    size: { type: String, default: 'md' },
    /** 是否显示清除按钮 */
    clearable: { type: Boolean, default: false },
    /** 前置图标名（见 ScreenIcon） */
    icon: { type: String, default: '' },
    /** 关联 label 的 id */
    id: { type: String, default: '' },
    autocomplete: { type: String, default: 'off' },
    min: { type: [String, Number], default: null },
    max: { type: [String, Number], default: null },
    step: { type: [String, Number], default: null },
  },
  computed: {
    isTextarea () {
      return this.type === 'textarea'
    },
    /** search 用 text 渲染，避免浏览器自带的清除按钮与自定义清除按钮重复 */
    nativeType () {
      if (this.type === 'number') return 'number'
      if (this.type === 'password') return 'password'
      return 'text'
    },
    showClear () {
      return this.clearable && !this.disabled && !this.readonly && this.value !== '' && this.value !== null && this.value !== undefined
    },
  },
  methods: {
    handleInput (event) {
      const raw = event.target.value
      // number 类型下空串必须原样传出去，否则 parseInt('') = NaN 会污染查询条件
      const next = this.type === 'number' ? (raw === '' ? '' : raw) : raw
      this.$emit('input', next)
    },
    handleChange (event) {
      this.$emit('change', event.target.value, event)
    },
    handleClear () {
      this.$emit('input', '')
      this.$emit('change', '')
      this.$emit('clear')
      this.$nextTick(() => {
        const el = this.$refs.control
        if (el && el.focus) el.focus()
      })
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-input {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  min-height: 32px;
  padding: 0 10px;
  color: var(--screen-text);
  background: rgba(2, 24, 28, 0.72);
  border: 1px solid var(--screen-border);
  border-radius: var(--screen-radius-sm);
  transition: border-color var(--screen-duration) var(--screen-ease),
    background-color var(--screen-duration) var(--screen-ease),
    box-shadow var(--screen-duration) var(--screen-ease);

  // 聚焦：只提亮描边 + 焦点环，不改尺寸，避免同排控件抖动
  &:focus-within {
    .screen-control-focus();
  }

  &:hover:not(.is-disabled):not(:focus-within) {
    .screen-control-hover();
  }

  &.is-disabled {
    .screen-control-disabled();

    .screen-input__control,
    .screen-input__area {
      cursor: not-allowed;
    }
  }

  &.is-invalid {
    .screen-control-invalid();
  }

  &.is-textarea {
    align-items: flex-start;
    padding: 7px 10px;
  }

  &__icon {
    flex: 0 0 auto;
    color: var(--screen-text-mute);
  }

  &__control {
    flex: 1 1 auto;
    width: 100%;
    min-width: 0;
    height: 30px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    line-height: 1.4;
    color: inherit;
    background: transparent;
    border: 0;
    outline: none;
    // 去掉 number 类型的上下箭头，大屏不需要步进控件
    appearance: none;
    -moz-appearance: textfield;

    &::-webkit-outer-spin-button,
    &::-webkit-inner-spin-button {
      margin: 0;
      appearance: none;
    }

    &::placeholder {
      .screen-placeholder();
    }

    &:disabled {
      color: var(--screen-text-mute);
    }
  }

  &__area {
    flex: 1 1 auto;
    width: 100%;
    min-width: 0;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    line-height: 1.6;
    color: inherit;
    background: transparent;
    border: 0;
    outline: none;
    resize: vertical;
    .screen-scrollbar();

    &::placeholder {
      .screen-placeholder();
    }

    &:disabled {
      color: var(--screen-text-mute);
      resize: none;
    }
  }

  &__clear {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    width: 18px;
    height: 18px;
    padding: 0;
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

  // ---------- 尺寸 ----------
  &.is-sm {
    min-height: 28px;
    padding: 0 8px;

    .screen-input__control {
      height: 26px;
      font-size: var(--screen-font-xs);
    }

    &.is-textarea {
      padding: 6px 8px;
    }
  }
}
</style>
