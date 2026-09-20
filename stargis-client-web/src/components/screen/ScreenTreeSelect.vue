<template>
  <!--
    ScreenTreeSelect 档案类别选择器（树形下拉）
    --------------------------------
    替代 ant-design-vue 的 a-tree-select。档案管理里它有且只有两种语义：
      1. leafOnly = true（默认）：只有叶子类别可选 —— 填报时用，
         因为一条卷内文件必须挂在具体的叶子类别上；
      2. leafOnly = false：任意层级的类别都能选 —— 查询时用，
         后端会把父类别自动展开成整棵子树。
    这两种语义写在一个组件里，避免出现「填报和查询用了两套控件、两套样式」的问题。

    用法：
      + 填报：只能选叶子
        <screen-tree-select
          v-model="form.categoryId"
          :nodes="categoryTree"
          placeholder="请选择档案类别"
          :invalid="!!errors.categoryId"
          @change="onCategoryChange"
        />

      + 查询：父类别也可选（后端按子树过滤）
        <screen-tree-select v-model="query.categoryId" :nodes="categoryTree" :leaf-only="false" />

      + 父组件通过 $refs 调用
        this.$refs.categorySelect.findNode(id)   - 原始节点 或 null
        this.$refs.categorySelect.refresh()      - 只派发 refresh 事件，数据由父组件重新拉取
        this.$refs.categorySelect.open() / .close()

    无障碍：
      触发器是 role="combobox" + aria-haspopup="tree"，aria-controls 指向浮层节点；
      下拉里的焦点由 ScreenTree 自己按 WAI-ARIA tree 模式管理（roving tabindex），
      打开时把焦点交给树节点，Esc / 选中后把焦点还给触发器。
  -->
  <div
    class="screen-tree-select"
    :class="{ 'is-open': isOpen, 'is-disabled': disabled, 'is-invalid': invalid, 'has-clear': showClear }"
  >
    <!--
      触发器必须是 button（可被 <label for> 关联）；清除按钮与它是兄弟节点而不是子节点，
      因为 HTML 不允许 button 嵌套 button（嵌套会让读屏与浏览器焦点管理都出问题）。
    -->
    <button
      :id="id || undefined"
      ref="trigger"
      type="button"
      class="screen-tree-select__trigger"
      role="combobox"
      aria-haspopup="tree"
      :aria-expanded="isOpen ? 'true' : 'false'"
      :aria-controls="layerId"
      :aria-label="triggerAriaLabel"
      :aria-disabled="disabled ? 'true' : undefined"
      :aria-invalid="invalid ? 'true' : undefined"
      :disabled="disabled"
      @click="toggleOpen"
      @keydown="handleTriggerKeydown"
    >
      <span v-if="displayLabel" class="screen-tree-select__value" :title="displayLabel">{{ displayLabel }}</span>
      <span v-else class="screen-tree-select__placeholder">{{ placeholder }}</span>

      <span class="screen-tree-select__suffix" aria-hidden="true">
        <screen-icon name="chevron-down" :size="14" />
      </span>
    </button>

    <button
      v-if="showClear"
      type="button"
      class="screen-tree-select__clear"
      aria-label="清除选择"
      @click.stop="handleClear"
    >
      <screen-icon name="close" :size="12" />
    </button>

    <screen-popover
      :id="layerId"
      :open="isOpen"
      :anchor="$refs.trigger"
      placement="bottom-start"
      :max-height="320"
      @close="handlePopoverClose"
    >
      <!--
        这里不再给浮层加 role="tree"：ScreenTree 根元素本身已经是 role="tree"，
        两层 tree 嵌套（tree 的子节点必须是 treeitem / group）是无效的 ARIA 结构，
        aria-controls 指向浮层节点即可建立「组合框 — 弹出树」的关联。
      -->
      <screen-tree
        ref="tree"
        :nodes="treeNodes"
        :selected-keys="treeSelectedKeys"
        :draggable="false"
        :highlight-keyword="keyword"
        :aria-label="placeholder"
        @select="handleSelect"
      />
    </screen-popover>
  </div>
</template>

<script>
import ScreenIcon from './ScreenIcon'
import ScreenPopover from './ScreenPopover'
import ScreenTree from './ScreenTree'
import { uid } from './utils'

export default {
  name: 'ScreenTreeSelect',
  components: { ScreenIcon, ScreenPopover, ScreenTree },
  props: {
    /** 绑定值（v-model）：选中的类别 id，未选中为 '' */
    value: { type: String, default: '' },
    /** 类别树数据：[{ id, name, children, isLeaf, status, archiveCount, disabled }] */
    nodes: { type: Array, default: () => [] },
    /** 未选中时的占位文案，同时作为未关联 label 时的无障碍名称 */
    placeholder: { type: String, default: '请选择档案类别' },
    /** 是否禁用整个选择器 */
    disabled: { type: Boolean, default: false },
    /** 有选中值且允许清除时，是否显示清除按钮 */
    clearable: { type: Boolean, default: true },
    /**
     * 是否只能选叶子类别。
     * true（默认）用于填报：卷内文件必须挂在叶子类别上；
     * false 用于查询：后端会把父类别展开成整棵子树。
     */
    leafOnly: { type: Boolean, default: true },
    /** 停用状态值：节点的 status 等于它时不可选（默认 0 = 停用） */
    disabledStatus: { type: Number, default: 0 },
    /** 外部校验失败态；这里只负责描边与 aria-invalid，错误文案由调用方渲染 */
    invalid: { type: Boolean, default: false },
    /** 触发器 id，便于外部用 <label for> 关联可见标签 */
    id: { type: String, default: '' },
    /**
     * 无障碍名称。
     * 没有可见 <label> 关联时建议传入 —— 比让读屏去念占位符更准确。
     * 传了 `id` 且外部有 <label for> 时可以省略。
     */
    ariaLabel: { type: String, default: '' },
    /** 外部搜索关键字，仅用于下拉里的文本高亮（组件本身不渲染搜索框） */
    keyword: { type: String, default: '' },
  },
  data () {
    return {
      isOpen: false,
      /** 浮层节点 id：触发器的 aria-controls 指向它 */
      layerId: uid('screen-tree-select'),
    }
  },
  computed: {
    /**
     * 交给 ScreenTree 的数据：补齐 children / isLeaf / disabled。
     * disabled = 后端停用 或（只允许选叶子时）它不是叶子。
     */
    treeNodes () {
      return this.buildTree(this.nodes)
    },
    /** 扁平索引：id 字符串到原始节点的映射，findNode 与触发器回显都靠它 */
    nodeIndex () {
      const index = {}
      const walk = (list) => {
        if (!Array.isArray(list)) return
        list.forEach((node) => {
          if (!node) return
          const key = node.id
          if (key !== undefined && key !== null) index[String(key)] = node
          walk(node.children)
        })
      }
      walk(this.nodes)
      return index
    },
    /** 当前选中的原始节点，可能为 null（值不在树里） */
    selectedNode () {
      return this.findNode(this.value)
    },
    /**
     * 触发器上显示的文案。
     * 值不在 nodes 里时退化成直接显示原始值，否则触发器会空白，用户以为选择丢了。
     */
    displayLabel () {
      if (this.value === '' || this.value === null || this.value === undefined) return ''
      const node = this.selectedNode
      return node ? this.labelOf(node) : String(this.value)
    },
    showClear () {
      return this.clearable && !this.disabled && this.displayLabel !== ''
    },
    /** 传给 ScreenTree 的选中 key（保持引用稳定，避免每次渲染都触发子组件 watch） */
    treeSelectedKeys () {
      if (this.value === '' || this.value === null || this.value === undefined) return []
      return [this.value]
    },
    /**
     * 无障碍名称：传了 id 说明外部用 <label for> 关联了可见标签，
     * 此时不要用 aria-label 覆盖它；未关联标签时用占位符兜底，
     * 并带上当前值，避免读屏永远只念同一个固定名称。
     */
    triggerAriaLabel () {
      // 显式传入的名称优先：调用方比组件更清楚这个选择器的语义
      // （例如「批量上传默认类别」比占位符「先选类别再上传」更准确）
      if (this.ariaLabel) {
        return this.displayLabel ? `${this.ariaLabel}：${this.displayLabel}` : this.ariaLabel
      }
      if (this.id) return undefined
      if (this.displayLabel) return `${this.placeholder}：${this.displayLabel}`
      return this.placeholder
    },
  },
  methods: {
    /** 递归补齐节点结构（不改动入参，避免污染父组件的数据） */
    buildTree (list) {
      if (!Array.isArray(list)) return []
      return list.map((node) => {
        const children = this.buildTree(node.children)
        const isLeaf = node.isLeaf === true || children.length === 0
        return Object.assign({}, node, {
          children,
          isLeaf,
          disabled: Boolean(node.disabled) || this.isStatusDisabled(node) || (this.leafOnly && !isLeaf),
        })
      })
    },
    /** 节点的 status 是否等于停用值 */
    isStatusDisabled (node) {
      if (!node) return false
      const status = node.status
      if (status === undefined || status === null) return false
      return Number(status) === Number(this.disabledStatus)
    },
    /** 取节点名称（与 ScreenTree 默认的 labelKey='name' 保持一致），缺失时退回 id */
    labelOf (node) {
      const name = node.name
      if (name === undefined || name === null || name === '') {
        return node.id === undefined || node.id === null ? '' : String(node.id)
      }
      return String(name)
    },
    /**
     * 按 id 查原始节点
     * @param {string|number} id 节点 id
     * @returns {Object|null} 原始节点，找不到返回 null
     */
    findNode (id) {
      if (id === '' || id === null || id === undefined) return null
      return this.nodeIndex[String(id)] || null
    },
    /** 刷新占位方法：本组件不持有数据源，只把意图抛给父组件重新拉取 */
    refresh () {
      this.$emit('refresh')
    },
    /** 打开下拉（父组件可通过 $refs 调用） */
    open () {
      this.openDropdown()
    },
    /**
     * 关闭下拉（父组件可通过 $refs 调用）
     * @param {boolean} restoreFocus 是否把焦点还给触发器，默认 true
     */
    close (restoreFocus = true) {
      if (!this.isOpen) return
      this.isOpen = false
      if (restoreFocus === false) return
      this.$nextTick(this.focusTrigger)
    },
    openDropdown () {
      if (this.disabled || this.isOpen) return
      this.isOpen = true
      // 打开后焦点必须进入树，否则键盘用户在浮层里无法导航
      this.$nextTick(() => {
        const tree = this.$refs.tree
        if (!tree) return
        if (typeof tree.focus === 'function') {
          tree.focus()
        } else if (tree.$el && tree.$el.focus) {
          tree.$el.focus()
        }
      })
    },
    focusTrigger () {
      const trigger = this.$refs.trigger
      if (trigger && trigger.focus) trigger.focus()
    },
    toggleOpen () {
      if (this.disabled) return
      if (this.isOpen) {
        this.close(true)
      } else {
        this.openDropdown()
      }
    },
    /** 触发器上的键盘：下 / 上方向键直接展开下拉（Enter / Space 走 button 原生点击） */
    handleTriggerKeydown (event) {
      if (this.disabled) return
      if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
      event.preventDefault()
      this.openDropdown()
    },
    /**
     * 浮层关闭回调
     * @param {string} reason 关闭原因：'esc' | 'outside'
     * 外部点击时把焦点留给用户点击的元素，Esc 时把焦点还给触发器。
     */
    handlePopoverClose (reason) {
      this.close(reason !== 'outside')
    },
    /**
     * 选中节点
     * @param {string|number} key 节点 id
     * @param {Object} node ScreenTree 传出的（已加工过的）节点
     */
    handleSelect (key, node) {
      if (node && node.disabled) return
      const raw = this.findNode(key)
      if (!raw) return
      // value 是 String 类型，这里统一转成字符串，避免父组件收到 Number 触发 prop 类型告警
      const next = String(key)
      this.$emit('input', next)
      this.$emit('change', next, raw)
      this.close(true)
    },
    /** 清除选择：清空值并派发 clear */
    handleClear () {
      if (this.disabled) return
      this.$emit('input', '')
      this.$emit('change', '', null)
      this.$emit('clear')
      this.$nextTick(this.focusTrigger)
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-tree-select {
  position: relative;
  display: block;
  min-width: 0;

  // 清除按钮与触发器是兄弟节点，悬停到清除按钮上时也要点亮触发器描边
  &:not(.is-disabled):hover .screen-tree-select__trigger {
    .screen-control-hover();
  }

  &__trigger {
    .screen-control(32px);
    text-align: left;
    cursor: pointer;

    &:disabled {
      .screen-control-disabled();
    }

    &:focus-visible {
      .screen-control-focus();
    }
  }

  &.is-invalid .screen-tree-select__trigger {
    .screen-control-invalid();
  }

  &__value,
  &__placeholder {
    flex: 1 1 auto;
    min-width: 0;
    .screen-ellipsis();
  }

  &__placeholder {
    .screen-placeholder();
  }

  &__suffix {
    flex: 0 0 auto;
    display: inline-flex;
    margin-left: auto;
    color: var(--screen-text-mute);
    transition: transform var(--screen-duration) var(--screen-ease),
      color var(--screen-duration) var(--screen-ease);
  }

  &.is-open &__suffix {
    color: var(--screen-accent);
    transform: rotate(180deg);
  }

  // 有清除按钮时给文字留出避让空间；按钮固定在箭头左侧
  &.has-clear &__value,
  &.has-clear &__placeholder {
    padding-right: 16px;
  }

  &__clear {
    position: absolute;
    top: 50%;
    right: 26px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    padding: 0;
    color: var(--screen-text-mute);
    background: transparent;
    border: 0;
    border-radius: 50%;
    cursor: pointer;
    transform: translateY(-50%);
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-text);
      background: rgba(47, 227, 192, 0.12);
    }
  }
}

// 尊重「减少动态效果」：箭头翻转与悬停过渡全部取消
@media (prefers-reduced-motion: reduce) {
  .screen-tree-select__trigger,
  .screen-tree-select__suffix,
  .screen-tree-select__clear {
    transition: none;
  }
}
</style>
