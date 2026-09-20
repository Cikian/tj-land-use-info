<template>
  <!--
    ScreenSelect 大屏下拉选择
    --------------------------------
    档案查询条件、编辑表单里的所有枚举选择都用它（状态 / 密级 / 部门 / 年度 / 区域 …）。

    为什么自己实现而不是用 a-select：
      1. 大屏是固定青绿暗色主题，antd 下拉是浅色面板，观感割裂；
      2. antd 的下拉通过 body 传送门渲染，但样式覆盖需要大量 /deep/ 且易随版本变；
      3. 这里用 ScreenPopover 统一处理「浮层挂 body + 自动翻转到上方」，逻辑收敛一处。

    用法：
      <screen-select v-model="query.status" :options="statusOptions" placeholder="全部状态" />

      options 支持两种写法：
        [{ value: '已归档', label: '已归档', disabled: false }]
        ['已归档', '审核中']            ← 字符串数组会被自动转成 { value, label }

    无障碍：触发器是 role="combobox"，下拉是 role="listbox"，
            选项是 role="option" 并用 aria-activedescendant 指向当前高亮项，
            键盘 ↑ ↓ Home End Enter Esc 全部可用（焦点始终留在触发器上，
            这是 WAI-ARIA combobox 的推荐做法，读屏才会正确播报当前项）。
  -->
  <div
    class="screen-select"
    :class="[`is-${size}`, { 'is-open': open, 'is-disabled': disabled, 'is-invalid': invalid, 'has-clear': showClear }]"
  >
    <div
      ref="trigger"
      class="screen-select__trigger"
      role="combobox"
      :tabindex="disabled ? -1 : 0"
      :aria-expanded="open ? 'true' : 'false'"
      aria-haspopup="listbox"
      :aria-controls="open ? listboxId : undefined"
      :aria-activedescendant="open && activeDescendant ? activeDescendant : undefined"
      :aria-disabled="disabled ? 'true' : undefined"
      :aria-invalid="invalid ? 'true' : undefined"
      :aria-required="required ? 'true' : undefined"
      :aria-label="ariaLabel || undefined"
      :id="id || undefined"
      @click="toggle"
      @keydown="handleKeydown"
      @blur="handleBlur"
    >
      <span class="screen-select__value" :class="{ 'is-placeholder': !hasValue }">
        {{ hasValue ? selectedLabel : placeholder }}
      </span>

      <screen-icon class="screen-select__arrow" name="chevron-down" :size="14" />
    </div>

    <!-- 清除按钮与触发器是兄弟节点（button 不能嵌套 button），绝对定位到箭头左侧 -->
    <button
      v-if="showClear"
      type="button"
      class="screen-select__clear"
      aria-label="清除选择"
      @mousedown.prevent
      @click.stop="handleClear"
    >
      <screen-icon name="close" :size="12" />
    </button>

    <screen-popover
      :open="open"
      :anchor="$refs.trigger"
      :placement="placement"
      :max-height="maxHeight"
      role="listbox"
      @close="handlePopoverClose"
    >
      <div :id="listboxId" class="screen-select__panel">
        <div v-if="searchable" class="screen-select__search">
          <screen-input
            ref="search"
            v-model="keyword"
            size="sm"
            icon="search"
            placeholder="输入关键字筛选"
            @enter="selectActive"
          />
        </div>

        <ul ref="list" class="screen-select__list">
          <li
            v-for="(option, index) in filteredOptions"
            :id="optionId(index)"
            :key="option.value"
            class="screen-select__option"
            :class="{
              'is-active': index === activeIndex,
              'is-selected': option.value === value,
              'is-disabled': option.disabled,
            }"
            role="option"
            :aria-selected="option.value === value ? 'true' : 'false'"
            :aria-disabled="option.disabled ? 'true' : undefined"
            @mousedown.prevent
            @click="handleSelect(option)"
            @mousemove="setActive(index)"
          >
            <span class="screen-select__option-label">{{ option.label }}</span>
            <screen-icon v-if="option.value === value" name="check" :size="13" />
          </li>

          <li v-if="!filteredOptions.length" class="screen-select__empty">{{ emptyText }}</li>
        </ul>
      </div>
    </screen-popover>
  </div>
</template>

<script>
import ScreenIcon from './ScreenIcon'
import ScreenInput from './ScreenInput'
import ScreenPopover from './ScreenPopover'
import { uid } from './utils'

export default {
  name: 'ScreenSelect',
  components: { ScreenIcon, ScreenInput, ScreenPopover },
  props: {
    /** 当前值（v-model）；空值用 '' 表示 */
    value: { type: [String, Number], default: '' },
    /**
     * 选项：`[{ value, label, disabled }]` 或字符串数组。
     * label 缺省时取 value。
     */
    options: { type: Array, default: () => [] },
    placeholder: { type: String, default: '请选择' },
    disabled: { type: Boolean, default: false },
    /** 必填语义（用于读屏） */
    required: { type: Boolean, default: false },
    /** 校验失败态；调用方需同时渲染错误文案 */
    invalid: { type: Boolean, default: false },
    /** 是否显示清除按钮 */
    clearable: { type: Boolean, default: true },
    /** 是否在下拉顶部提供关键字筛选 */
    searchable: { type: Boolean, default: false },
    /**
     * 是否在本地过滤选项。
     * 传 false 表示选项由父组件远程搜索提供（本组件只负责把关键字抛出去），
     * 此时监听 `search` 事件重新拉取 options —— 宗地/配套项目下拉就是这种用法。
     */
    filterLocal: { type: Boolean, default: true },
    /** 尺寸：sm 28 / md 32 */
    size: { type: String, default: 'md' },
    /** 关联 label 的 id */
    id: { type: String, default: '' },
    /**
     * 无障碍名称。
     * 没有可见 <label> 关联时**必须**传，否则读屏只会念出一个没有名字的组合框。
     */
    ariaLabel: { type: String, default: '' },
    /** 浮层相对触发器的位置 */
    placement: { type: String, default: 'bottom-start' },
    /** 浮层最大高度 */
    maxHeight: { type: Number, default: 288 },
    emptyText: { type: String, default: '暂无数据' },
  },
  data () {
    return {
      open: false,
      keyword: '',
      activeIndex: -1,
      /** 本次打开是否由键盘触发：键盘打开时直接把高亮落在已选项上 */
      openedByKeyboard: false,
      uid: uid('screen-select'),
    }
  },
  computed: {
    listboxId () {
      return `${this.uid}-listbox`
    },
    /** 规范化选项，兼容字符串数组 */
    normalizedOptions () {
      return (this.options || []).map((item, index) => {
        if (item === null || item === undefined) {
          return { value: '', label: '', disabled: true, raw: item, index }
        }
        if (typeof item === 'object') {
          return {
            value: item.value === undefined ? item.label : item.value,
            label: item.label === undefined ? String(item.value) : item.label,
            disabled: !!item.disabled,
            raw: item,
            index,
          }
        }
        return { value: item, label: String(item), disabled: false, raw: item, index }
      })
    },
    filteredOptions () {
      const keyword = this.keyword.trim().toLowerCase()
      if (!this.searchable || !keyword) return this.normalizedOptions
      // 远程搜索模式下不过滤：options 已经是被搜索过的结果，再过滤会把服务端命中的项滤掉
      if (!this.filterLocal) return this.normalizedOptions
      return this.normalizedOptions.filter((option) =>
        String(option.label).toLowerCase().indexOf(keyword) > -1
      )
    },
    hasValue () {
      return this.value !== '' && this.value !== null && this.value !== undefined
    },
    selectedOption () {
      return this.normalizedOptions.find((option) => option.value === this.value) || null
    },
    selectedLabel () {
      // 值不在选项里（例如按项目下钻带过来的条件）时退化为直接展示原始值，
      // 否则触发器会显示空白，用户以为条件没生效
      return this.selectedOption ? this.selectedOption.label : String(this.value)
    },
    showClear () {
      return this.clearable && !this.disabled && this.hasValue
    },
    activeDescendant () {
      const option = this.filteredOptions[this.activeIndex]
      return option ? this.optionId(this.activeIndex) : ''
    },
  },
  watch: {
    open (val) {
      this.$emit('open-change', val)
    },
    value () {
      // 外部改值后同步高亮位置，避免下次打开还停在旧项
      this.syncActiveToValue()
    },
    keyword (val) {
      // 远程搜索：把关键字抛给父组件去重新拉取 options
      if (this.searchable && !this.filterLocal) {
        this.$emit('search', String(val || '').trim())
      }
    },
  },
  methods: {
    optionId (index) {
      return `${this.uid}-option-${index}`
    },
    setActive (index) {
      this.activeIndex = index
    },
    /** 把高亮定位到已选项；没有已选项时落在第一个可选中的项上 */
    syncActiveToValue () {
      const selectedIndex = this.filteredOptions.findIndex((option) => option.value === this.value)
      if (selectedIndex > -1) {
        this.activeIndex = selectedIndex
        return
      }
      this.activeIndex = this.filteredOptions.findIndex((option) => !option.disabled)
    },
    toggle () {
      if (this.disabled) return
      if (this.open) {
        this.close()
      } else {
        this.openPanel(false)
      }
    },
    /**
     * 打开下拉
     * @param {boolean} byKeyboard 是否由键盘触发（决定是否聚焦搜索框）
     */
    openPanel (byKeyboard) {
      if (this.disabled || this.open) return
      this.openedByKeyboard = !!byKeyboard
      this.keyword = ''
      this.open = true
      this.syncActiveToValue()
      this.$emit('open')
      this.$nextTick(() => {
        if (this.searchable && byKeyboard) {
          const search = this.$refs.search
          if (search && search.$refs.control && search.$refs.control.focus) {
            search.$refs.control.focus()
          }
        }
        this.scrollActiveIntoView()
      })
    },
    close () {
      if (!this.open) return
      this.open = false
      this.keyword = ''
      this.$emit('close')
    },
    handlePopoverClose () {
      this.close()
      this.focusTrigger()
    },
    /**
     * 触发器失焦时收起。
     *
     * 两个都必须处理的坑：
     *  1. 下拉浮层被 ScreenPopover 移动到了 document.body，已经不在 $el 子树内，
     *     所以不能只用 `this.$el.contains(next)` 判断 —— 还要看焦点是否落进了浮层，
     *     否则「可搜索」模式里点搜索框的一瞬间下拉就会自己关掉；
     *  2. 点击选项（<li> 本身不可聚焦）会让焦点落到 body，relatedTarget 为 null。
     *     如果这时立刻关闭，v-show 会把选项隐藏掉，随后的 click 事件根本不会触发，
     *     鼠标选择就彻底失效了。因此选项上加了 @mousedown.prevent 阻止焦点离开触发器；
     *     这里再对 relatedTarget 为 null 的情况做一次兜底（不立即关闭，
     *     交给 @mousedown.prevent 与浮层的 outside 逻辑决定）。
     */
    handleBlur (event) {
      if (!this.open) return
      const next = event.relatedTarget
      // 焦点没落到别的元素上：可能正在点击浮层内的选项，交给 click 处理，不要抢先关闭
      if (!next) return
      if (this.$el.contains(next)) return
      if (next.closest && next.closest('.screen-popover')) return
      this.close()
    },
    focusTrigger () {
      const trigger = this.$refs.trigger
      if (trigger && trigger.focus) trigger.focus()
    },
    scrollActiveIntoView () {
      const list = this.$refs.list
      if (!list || this.activeIndex < 0) return
      const el = list.children[this.activeIndex]
      if (el && el.scrollIntoView) {
        el.scrollIntoView({ block: 'nearest' })
      }
    },
    moveActive (delta) {
      const total = this.filteredOptions.length
      if (!total) return
      let next = this.activeIndex
      // 跳过 disabled 项，最多绕一圈，避免死循环
      for (let step = 0; step < total; step += 1) {
        next = (next + delta + total) % total
        if (!this.filteredOptions[next].disabled) {
          this.activeIndex = next
          break
        }
      }
      this.$nextTick(this.scrollActiveIntoView)
    },
    selectActive () {
      const option = this.filteredOptions[this.activeIndex]
      if (!option || option.disabled) return
      this.handleSelect(option)
    },
    handleSelect (option) {
      if (option.disabled) return
      this.$emit('input', option.value)
      this.$emit('change', option.value, option.raw)
      this.close()
      this.focusTrigger()
    },
    handleClear () {
      this.$emit('input', '')
      this.$emit('change', '', null)
      this.$emit('clear')
      this.focusTrigger()
    },
    handleKeydown (event) {
      if (this.disabled) return
      switch (event.key) {
        case 'ArrowDown':
          event.preventDefault()
          if (!this.open) {
            this.openPanel(true)
          } else {
            this.moveActive(1)
          }
          break
        case 'ArrowUp':
          event.preventDefault()
          if (!this.open) {
            this.openPanel(true)
          } else {
            this.moveActive(-1)
          }
          break
        case 'Home':
          if (!this.open) return
          event.preventDefault()
          this.activeIndex = -1
          this.moveActive(1)
          break
        case 'End':
          if (!this.open) return
          event.preventDefault()
          this.activeIndex = this.filteredOptions.length
          this.moveActive(-1)
          break
        case 'Enter':
          event.preventDefault()
          if (this.open) {
            this.selectActive()
          } else {
            this.openPanel(true)
          }
          break
        case ' ':
          // 空格只在未打开时用于打开，避免和页面滚动抢键
          if (this.open) return
          event.preventDefault()
          this.openPanel(true)
          break
        case 'Escape':
          if (!this.open) return
          event.preventDefault()
          this.close()
          break
        case 'Tab':
          this.close()
          break
        default:
          // 可搜索时，直接打字就打开并进入筛选
          if (this.searchable && !this.open && event.key.length === 1) {
            this.openPanel(true)
          }
      }
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-select {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  min-height: 32px;
  color: var(--screen-text);
  background: rgba(2, 24, 28, 0.72);
  border: 1px solid var(--screen-border);
  border-radius: var(--screen-radius-sm);
  transition: border-color var(--screen-duration) var(--screen-ease),
    background-color var(--screen-duration) var(--screen-ease),
    box-shadow var(--screen-duration) var(--screen-ease);

  &:focus-within {
    .screen-control-focus();
  }

  &:hover:not(.is-disabled):not(:focus-within) {
    .screen-control-hover();
  }

  &.is-disabled {
    .screen-control-disabled();

    .screen-select__trigger {
      cursor: not-allowed;
    }
  }

  &.is-invalid {
    .screen-control-invalid();
  }

  &.is-open {
    .screen-select__arrow {
      transform: rotate(180deg);
    }
  }

  &__trigger {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1 1 auto;
    width: 100%;
    min-width: 0;
    min-height: 30px;
    padding: 0 10px;
    font-size: var(--screen-font-sm);
    line-height: 1.4;
    cursor: pointer;
    outline: none;
    // 焦点环画在外层容器上（:focus-within），这里不要再画一圈
  }

  // 有清除按钮时给文字留出避让空间，避免长文本压到按钮下面
  &.has-clear &__trigger {
    padding-right: 44px;
  }

  &__value {
    flex: 1 1 auto;
    min-width: 0;
    text-align: left;
    .screen-ellipsis();

    &.is-placeholder {
      .screen-placeholder();
    }
  }

  &__arrow {
    flex: 0 0 auto;
    color: var(--screen-text-mute);
    transition: transform var(--screen-duration) var(--screen-ease);
  }

  &__clear {
    position: absolute;
    top: 50%;
    right: 26px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    padding: 0;
    color: var(--screen-text-mute);
    background: transparent;
    border: 0;
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transform: translateY(-50%);
    transition: color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-text);
    }
  }

  // ---------- 下拉面板 ----------
  &__panel {
    display: flex;
    flex-direction: column;
    max-height: inherit;
  }

  &__search {
    padding: var(--screen-space-2);
    border-bottom: 1px solid var(--screen-border-soft);
  }

  &__list {
    flex: 1 1 auto;
    min-height: 0;
    margin: 0;
    padding: 4px;
    overflow-y: auto;
    list-style: none;
    .screen-scrollbar();
  }

  &__option {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    padding: 6px 8px;
    font-size: var(--screen-font-sm);
    line-height: 1.5;
    color: var(--screen-text-sub);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);

    // 键盘高亮与鼠标悬停统一成同一个视觉，指针和键盘用户看到一致的状态
    &.is-active:not(.is-disabled) {
      color: var(--screen-text);
      background: var(--screen-elevate);
    }

    &.is-selected {
      color: var(--screen-accent);

      .screen-select__option-label {
        font-weight: 600;
      }
    }

    &.is-disabled {
      color: var(--screen-text-mute);
      cursor: not-allowed;
    }
  }

  &__option-label {
    flex: 1 1 auto;
    min-width: 0;
    .screen-ellipsis();
  }

  &__empty {
    padding: 14px 8px;
    font-size: var(--screen-font-sm);
    text-align: center;
    color: var(--screen-text-mute);
  }

  // ---------- 尺寸 ----------
  &.is-sm {
    min-height: 28px;

    .screen-select__trigger {
      min-height: 26px;
      padding: 0 8px;
      font-size: var(--screen-font-xs);
    }

    &.has-clear .screen-select__trigger {
      padding-right: 40px;
    }
  }
}

// 尊重「减少动态效果」：箭头翻转与底色过渡全部取消
@media (prefers-reduced-motion: reduce) {
  .screen-select__arrow,
  .screen-select__option,
  .screen-select__trigger {
    transition: none;
  }
}
</style>
