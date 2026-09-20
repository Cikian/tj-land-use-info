<template>
  <!--
    ScreenDateInput 日期输入框
    --------------------------------
    档案管理筛选栏里的日期控件，替代 ant-design-vue 的 a-date-picker / a-range-picker。
    为什么用原生 <input type="date" / "month">：
      1. 原生选择面板由浏览器绘制，是「浏览器级浮层」，不会被大屏面板的
         overflow: hidden / transform 裁切（自绘日历最常见的翻车点）；
      2. Chromium 下 color-scheme: dark 会让面板直接渲染成暗色，无需重绘；
      3. 原生输入自带键盘操作、读屏支持与本地化格式，无障碍成本最低。

    用法：
      // 单选：v-model 为 'YYYY-MM-DD'
      <screen-date-input v-model="form.archiveDate" id="archive-date" placeholder="选择归档日期" />

      // 月份单选
      <screen-date-input v-model="form.month" input-type="month" placeholder="选择月份" />

      // 区间：v-model 为 [begin, end]，任一侧可为空字符串
      <screen-date-input
        v-model="form.range"
        mode="range"
        start-id="archive-start"
        end-id="archive-end"
        @change="onRangeChange"
      />

    无障碍：
      - mode="single" 时根元素为 <label>，点击文字即聚焦输入框，无需额外 id 也可用；
        传入 id 后可用外部 <label for="..."> 关联；
      - mode="range" 时两个输入各自带 aria-label（取 start/end 占位符，或 id 指定的名称），
        因为一个 <label> 只能关联一个表单控件；
      - 起点晚于终点时，除描边变红外，还把 aria-invalid 置为 true，
        并通过 change 事件的第二个参数 { valid: false } 让调用方补错误文案
        （颜色不能是唯一提示）。
  -->
  <div class="screen-date-input" :class="[`screen-date-input--${size}`, { 'is-disabled': disabled }]">
    <!-- 单选 -->
    <label v-if="mode === 'single'" class="screen-date-input__control" :class="{ 'is-invalid': invalid }">
      <input
        :id="id || undefined"
        class="screen-date-input__field"
        :type="inputType"
        :value="singleValue"
        :disabled="disabled"
        :aria-label="fieldAriaLabel"
        :aria-invalid="invalid ? 'true' : undefined"
        @input="handleSingleInput"
        @change="handleSingleChange"
      />
      <span v-if="showSinglePlaceholder" class="screen-date-input__placeholder" aria-hidden="true">
        {{ placeholder || (inputType === 'month' ? '选择月份' : '选择日期') }}
      </span>
      <button
        v-if="showClear"
        type="button"
        class="screen-date-input__clear"
        aria-label="清除日期"
        :disabled="disabled"
        @click="handleClear"
      >
        <screen-icon name="close" :size="12" />
      </button>
    </label>

    <!-- 区间 -->
    <div
      v-else
      class="screen-date-input__control"
      :class="{ 'is-range': true, 'is-invalid': invalid || rangeInvalid }"
    >
      <label class="screen-date-input__segment">
        <input
          :id="startId || undefined"
          class="screen-date-input__field"
          type="date"
          :value="startValue"
          :disabled="disabled"
          :aria-label="startAriaLabel"
          :aria-invalid="invalid || rangeInvalid ? 'true' : undefined"
          @input="handleRangeInput(0, $event)"
          @change="handleRangeChange"
        />
        <span v-if="!startValue" class="screen-date-input__placeholder" aria-hidden="true">
          {{ startPlaceholder }}
        </span>
      </label>

      <span class="screen-date-input__separator" aria-hidden="true">~</span>

      <label class="screen-date-input__segment">
        <input
          :id="endId || undefined"
          class="screen-date-input__field"
          type="date"
          :value="endValue"
          :disabled="disabled"
          :aria-label="endAriaLabel"
          :aria-invalid="invalid || rangeInvalid ? 'true' : undefined"
          @input="handleRangeInput(1, $event)"
          @change="handleRangeChange"
        />
        <span v-if="!endValue" class="screen-date-input__placeholder" aria-hidden="true">
          {{ endPlaceholder }}
        </span>
      </label>

      <button
        v-if="showClear"
        type="button"
        class="screen-date-input__clear"
        aria-label="清除日期"
        :disabled="disabled"
        @click="handleClear"
      >
        <screen-icon name="close" :size="12" />
      </button>
    </div>
  </div>
</template>

<script>
import ScreenIcon from './ScreenIcon'

export default {
  name: 'ScreenDateInput',
  components: { ScreenIcon },
  props: {
    /**
     * 绑定值（v-model）
     * mode="single" 时为 'YYYY-MM-DD'（inputType="month" 时为 'YYYY-MM'）字符串；
     * mode="range" 时为 [开始, 结束] 数组，任一侧缺失用 '' 占位。
     */
    value: { type: [String, Array], default: '' },
    /** 模式：single 单个日期 / range 日期区间 */
    mode: { type: String, default: 'single' },
    /** 原生输入类型：date 日期 / month 月份（仅在 single 模式下生效） */
    inputType: { type: String, default: 'date' },
    /** single 模式下的占位提示（原生输入本身不渲染 placeholder，这里自绘） */
    placeholder: { type: String, default: '' },
    /** range 模式下开始日期的占位提示，同时用作无障碍名称 */
    startPlaceholder: { type: String, default: '开始日期' },
    /** range 模式下结束日期的占位提示，同时用作无障碍名称 */
    endPlaceholder: { type: String, default: '结束日期' },
    /** 是否禁用 */
    disabled: { type: Boolean, default: false },
    /** 是否显示清除按钮 */
    clearable: { type: Boolean, default: true },
    /** 外部校验失败：仅用于描边提示，调用方需自行渲染错误文案 */
    invalid: { type: Boolean, default: false },
    /** 尺寸：sm 28px / md 32px（与筛选栏其他控件对齐） */
    size: { type: String, default: 'md' },
    /** single 模式输入框 id，便于外部 <label for> 关联 */
    id: { type: String, default: '' },
    /** range 模式开始日期输入框 id */
    startId: { type: String, default: '' },
    /** range 模式结束日期输入框 id */
    endId: { type: String, default: '' },
  },
  data () {
    return {
      /** 区间起止顺序非法（begin > end）时置位；仅作视觉与 aria 提示，不阻断事件 */
      rangeInvalid: false,
    }
  },
  computed: {
    isRange () {
      return this.mode === 'range'
    },
    /** range 模式绑定值的安全视图，始终是长度 2 的数组 */
    rangeValues () {
      const list = Array.isArray(this.value) ? this.value : []
      return [list[0] || '', list[1] || '']
    },
    startValue () {
      return this.rangeValues[0]
    },
    endValue () {
      return this.rangeValues[1]
    },
    singleValue () {
      return Array.isArray(this.value) ? '' : this.value || ''
    },
    /** 值为空时展示自绘占位文案（原生日期控件的 placeholder 属性无效） */
    showSinglePlaceholder () {
      return !this.singleValue && !this.isRange
    },
    /** 有任何一侧有值时出现一个（且仅一个）清除按钮 */
    showClear () {
      if (!this.clearable || this.disabled) return false
      if (this.isRange) return Boolean(this.startValue || this.endValue)
      return Boolean(this.singleValue)
    },
    /** single 模式的无障碍名称：优先取传入的 id 文案，否则按类型兜底 */
    fieldAriaLabel () {
      if (this.placeholder) return this.placeholder
      return this.inputType === 'month' ? '选择月份' : '选择日期'
    },
    startAriaLabel () {
      return this.startPlaceholder || '开始日期'
    },
    endAriaLabel () {
      return this.endPlaceholder || '结束日期'
    },
  },
  watch: {
    // 外部把值清空时同步复位校验态，避免红框残留
    value () {
      if (!this.rangeInvalid) return
      const [begin, end] = this.rangeValues
      if (!begin || !end || begin <= end) this.rangeInvalid = false
    },
  },
  methods: {
    /** 区间顺序校验：两侧都填了才判非法 */
    isRangeOutOfOrder (begin, end) {
      return Boolean(begin && end && begin > end)
    },
    handleSingleInput (event) {
      const next = event.target.value || ''
      this.$emit('input', next)
    },
    handleSingleChange (event) {
      const next = event.target.value || ''
      this.$emit('change', next)
    },
    handleRangeInput (index, event) {
      const next = [...this.rangeValues]
      next[index] = event.target.value || ''
      this.rangeInvalid = this.isRangeOutOfOrder(next[0], next[1])
      this.$emit('input', next)
    },
    /** 原生 change 在失焦 / 选定后触发，此处补一个 { valid } 供调用方提示错误 */
    handleRangeChange () {
      const [begin, end] = this.rangeValues
      this.rangeInvalid = this.isRangeOutOfOrder(begin, end)
      this.$emit('change', [begin, end], { valid: !this.rangeInvalid })
    },
    handleClear () {
      const next = this.isRange ? ['', ''] : ''
      this.rangeInvalid = false
      this.$emit('input', next)
      this.$emit('change', next, { valid: true })
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-date-input {
  display: block;
  min-width: 0;

  &--sm {
    .screen-date-input__control {
      .screen-control(28px);
    }
  }

  &__control {
    position: relative;
    .screen-control(32px);

    &:hover:not(.is-disabled) {
      .screen-control-hover();
    }

    &:focus-within {
      .screen-control-focus();
    }

    // 清除按钮与分隔符由内部元素抬起，原生输入不加任何 hover 装饰
    &:focus-within .screen-date-input__field:focus {
      outline: none;
    }
  }

  &.is-disabled &__control {
    .screen-control-disabled();
  }

  // ---------- 区间模式：两段等宽输入 + ~ 分隔符 ----------
  &__control.is-range {
    gap: var(--screen-space-1);
  }

  // 校验失败作用在整只控件上（描边 + 浅红辉光），并且调用方必须同时给出错误文案
  &__control.is-invalid {
    .screen-control-invalid();
  }

  &__segment {
    position: relative;
    display: flex;
    flex: 1 1 0;
    min-width: 0;
    height: 100%;
  }

  &__separator {
    flex: 0 0 auto;
    color: var(--screen-text-mute);
    user-select: none;
  }

  // ---------- 原生输入 ----------
  &__field {
    width: 100%;
    min-width: 0;
    height: 100%;
    padding: 2px 0;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    line-height: 1.4;
    color: var(--screen-text);
    // 让 Chromium 用暗色渲染原生日历面板与内部字段
    color-scheme: dark;
    background: transparent;
    border: 0;
    outline: none;
    cursor: text;

    &:disabled {
      cursor: not-allowed;
    }

    // 原生日历按钮默认近乎纯黑，暗底上不可见：反相后压低透明度，悬停提亮
    &::-webkit-calendar-picker-indicator {
      flex: 0 0 auto;
      padding: 0;
      margin-left: 2px;
      opacity: 0.5;
      cursor: pointer;
      filter: invert(1);
      transition: opacity var(--screen-duration) var(--screen-ease);
    }

    &:hover::-webkit-calendar-picker-indicator {
      opacity: 1;
    }

    &:disabled::-webkit-calendar-picker-indicator {
      cursor: not-allowed;
      opacity: 0.25;
    }

    // 原生「年/月/日」内部字段：空值状态用弱化色，与占位符语义一致
    &::-webkit-datetime-edit-fields-wrapper {
      color: inherit;
      padding: 0;
    }

    &::-webkit-datetime-edit-text {
      color: currentColor;
      padding: 0 1px;
    }

    &::-webkit-datetime-edit-year-field,
    &::-webkit-datetime-edit-month-field,
    &::-webkit-datetime-edit-day-field {
      color: currentColor;
    }

    // Chromium 在「未选值」时会给表单控件加 :invalid，借此把占位态压暗
    &:invalid {
      .screen-placeholder();
    }
  }

  // 自绘占位（绝对定位，pointer-events: none 以免挡住原生按钮）
  &__placeholder {
    position: absolute;
    top: 50%;
    left: 0;
    max-width: calc(100% - 22px);
    transform: translateY(-50%);
    pointer-events: none;
    .screen-placeholder();
    .screen-ellipsis();
  }

  // ---------- 清除按钮 ----------
  &__clear {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    margin-left: 2px;
    padding: 0;
    color: var(--screen-text-mute);
    background: transparent;
    border: 0;
    border-radius: 50%;
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-text);
      background: rgba(47, 227, 192, 0.12);
    }

    &:disabled {
      color: var(--screen-text-mute);
      cursor: not-allowed;
    }
  }
}
</style>
