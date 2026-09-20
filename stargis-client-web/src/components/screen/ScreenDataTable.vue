<template>
  <!--
    ScreenDataTable 大屏数据表格
    --------------------------------
    设计稿底部「市级项目预警」表格：操作 / 行政区划 / 各环节未完成率（数值 + 细条形）。
    档案模块在此基础上扩展了「行选择 / 文字链接列 / 多操作列 / 加载态 / 行点击」。

    使用原生 <table> 语义，保证读屏可读；列宽由 columns[].width 控制，横向溢出时局部滚动。

    columns: [{
      key, title, width, align,
      type,        // text | number | percent | action | index | tag | slot | link | actions
      unit, tone, formatter, placeholder,
      actionText,  // type=action 时的按钮文案
      linkText,    // type=link 时的链接文案，缺省取单元格值
      actions,     // type=actions 时的操作数组：[{ key, label, tone, visible }]
                   //   visible 可以是布尔，也可以是 (row) => boolean
      precision,   // number / percent 的小数位
    }]

    事件：
      action      (row, col, rowIndex, actionKey)  操作列点击；type=action 时 actionKey 为 undefined
      cell-click  (row, col, rowIndex)             链接列点击
      row-click   (row, rowIndex)                  rowClickable 时整行点击
      select-change (keys, rows)                   行选择变化

    无障碍：
      - 原生 <table> + <th scope="col">，行选择用真正的 <input type="checkbox"> 并带 aria-label；
      - 表头全选用原生 indeterminate 表达「部分选中」，不用颜色暗示；
      - 百分比条是 aria-hidden 的装饰，真实数值始终以文本呈现，颜色不是唯一线索。
  -->
  <div class="screen-data-table" :class="{ 'is-loading': loading }">
    <div class="screen-data-table__scroll" :style="scrollStyle">
      <table class="screen-data-table__table" :style="{ minWidth: `${minWidth}px` }">
        <colgroup>
          <col v-if="selectable" style="width: 40px" />
          <col v-for="col in columns" :key="`col-${col.key}`" :style="{ width: columnWidth(col) }" />
        </colgroup>

        <thead>
          <tr>
            <th v-if="selectable" class="is-center" scope="col">
              <label class="screen-data-table__checkbox">
                <input
                  ref="selectAll"
                  type="checkbox"
                  :checked="allSelected"
                  :disabled="!selectableEntries.length"
                  aria-label="全选当前页"
                  @change="handleSelectAll"
                />
                <span class="screen-data-table__checkbox-box" aria-hidden="true">
                  <screen-icon name="check" :size="11" />
                </span>
              </label>
            </th>

            <th
              v-for="col in columns"
              :key="`th-${col.key}`"
              scope="col"
              :class="alignClass(col)"
              :title="col.title"
            >
              {{ col.title }}
            </th>
          </tr>
        </thead>

        <tbody>
          <tr
            v-for="(row, rowIndex) in data"
            :key="rowKeyValue(row, rowIndex)"
            :class="{
              'is-alt': stripe && rowIndex % 2 === 1,
              'is-selected': selectable && isSelected(row, rowIndex),
              'is-clickable': rowClickable,
            }"
            @click="handleRowClick(row, rowIndex)"
          >
            <td v-if="selectable" class="is-center" @click.stop>
              <label class="screen-data-table__checkbox">
                <input
                  type="checkbox"
                  :checked="isSelected(row, rowIndex)"
                  :disabled="isRowSelectDisabled(row)"
                  :aria-label="`选择第 ${rowIndex + 1} 行`"
                  @change="handleSelectRow(row, rowIndex, $event)"
                />
                <span class="screen-data-table__checkbox-box" aria-hidden="true">
                  <screen-icon name="check" :size="11" />
                </span>
              </label>
            </td>

            <td v-for="col in columns" :key="`td-${col.key}`" :class="alignClass(col)">
              <!-- 序号 -->
              <template v-if="col.type === 'index'">
                <span class="screen-data-table__index">{{ padIndex(rowIndex + 1) }}</span>
              </template>

              <!-- 文字链接 -->
              <template v-else-if="col.type === 'link'">
                <button
                  type="button"
                  class="screen-data-table__link"
                  @click.stop="handleCellClick(row, col, rowIndex)"
                >
                  {{ cellText(row, col, col.linkText) }}
                </button>
              </template>

              <!-- 多操作 -->
              <template v-else-if="col.type === 'actions'">
                <span class="screen-data-table__actions">
                  <button
                    v-for="action in visibleActions(col, row)"
                    :key="action.key"
                    type="button"
                    class="screen-data-table__link"
                    :class="`is-${action.tone || 'accent'}`"
                    @click.stop="handleAction(row, col, rowIndex, action.key)"
                  >
                    {{ action.label }}
                  </button>
                </span>
              </template>

              <!-- 单操作（兼容设计稿原有写法） -->
              <template v-else-if="col.type === 'action'">
                <button
                  type="button"
                  class="screen-data-table__action"
                  @click.stop="handleAction(row, col, rowIndex, undefined)"
                >
                  {{ col.actionText || '查看' }}
                </button>
              </template>

              <!-- 百分比 + 条 -->
              <template v-else-if="col.type === 'percent'">
                <span class="screen-data-table__percent">
                  <span class="screen-data-table__percent-value">{{ cellText(row, col) }}</span>
                  <span class="screen-data-table__percent-track" aria-hidden="true">
                    <i
                      class="screen-data-table__percent-fill"
                      :class="`is-${col.tone || 'muted'}`"
                      :style="{ width: barReady ? `${barWidth(row, col)}%` : '0%' }"
                    />
                  </span>
                </span>
              </template>

              <!-- 标签：复用 ScreenTag，保证表格内外的标签是同一套视觉 -->
              <template v-else-if="col.type === 'tag'">
                <screen-tag :tone="tagTone(row, col)" size="sm">{{ cellText(row, col) }}</screen-tag>
              </template>

              <!-- 自定义插槽 -->
              <template v-else-if="col.type === 'slot'">
                <slot :name="col.key" :row="row" :col="col" :value="row[col.key]" :index="rowIndex" />
              </template>

              <!-- 普通文本 / 数字 -->
              <template v-else>
                <span class="screen-data-table__text" :title="col.ellipsis === false ? undefined : cellText(row, col)">
                  {{ cellText(row, col) }}
                </span>
              </template>
            </td>
          </tr>

          <tr v-if="!data.length && !loading" class="is-empty">
            <td :colspan="totalColumns">
              <slot name="empty">{{ emptyText }}</slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 加载遮罩：自绘而不依赖 ScreenLoading，保证表格组件零外部依赖 -->
    <div v-if="loading" class="screen-data-table__loading" role="status" aria-live="polite">
      <span class="screen-data-table__spinner" aria-hidden="true" />
      <span class="screen-data-table__loading-text">{{ loadingText }}</span>
    </div>
  </div>
</template>

<script>
import ScreenIcon from './ScreenIcon'
import ScreenTag from './ScreenTag'
import { clamp, formatNumber, formatPercent, padIndex, prefersReducedMotion, raf, toNumber } from './utils'

export default {
  name: 'ScreenDataTable',
  components: { ScreenIcon, ScreenTag },
  props: {
    /** 列定义 */
    columns: { type: Array, default: () => [] },
    /** 行数据 */
    data: { type: Array, default: () => [] },
    /** 行唯一键字段，缺省使用索引 */
    rowKey: { type: String, default: '' },
    /** 斑马纹 */
    stripe: { type: Boolean, default: true },
    /** 表格最小宽度（px），不足时横向滚动 */
    minWidth: { type: Number, default: 720 },
    /** 最大高度，超出后纵向滚动并吸顶表头 */
    maxHeight: { type: [Number, String], default: null },
    /** 百分比列保留小数位 */
    precision: { type: Number, default: 2 },
    /** 是否入场动画（百分比条） */
    animated: { type: Boolean, default: true },
    /** 空数据文案 */
    emptyText: { type: String, default: '暂无数据' },
    /** 是否显示行选择列 */
    selectable: { type: Boolean, default: false },
    /** 已选行的 rowKey 值集合 */
    selectedKeys: { type: Array, default: () => [] },
    /** 单行是否禁止选择：布尔或 (row) => boolean */
    rowSelectDisabled: { type: [Boolean, Function], default: false },
    /** 是否整行可点击（抛出 row-click） */
    rowClickable: { type: Boolean, default: false },
    /** 加载态 */
    loading: { type: Boolean, default: false },
    /** 加载文案 */
    loadingText: { type: String, default: '加载中…' },
  },
  data () {
    return {
      barReady: false,
    }
  },
  computed: {
    scrollStyle () {
      if (!this.maxHeight) return {}
      const value = typeof this.maxHeight === 'number' ? `${this.maxHeight}px` : this.maxHeight
      return { maxHeight: value }
    },
    /** 空数据行的 colspan：选中列 + 数据列 */
    totalColumns () {
      return this.columns.length + (this.selectable ? 1 : 0)
    },
    /**
     * 可选中行 + 其在 data 中的真实索引。
     * 必须把索引一起带上：data 里可能存在内容相同的两行，
     * 用 indexOf(row) 反查会拿到错误的位置，导致全选时选错行。
     */
    selectableEntries () {
      const entries = []
      this.data.forEach((row, index) => {
        if (!this.isRowSelectDisabled(row)) entries.push({ row, index })
      })
      return entries
    },
    allSelected () {
      return (
        this.selectableEntries.length > 0 &&
        this.selectableEntries.every((entry) => this.isSelected(entry.row, entry.index))
      )
    },
  },
  watch: {
    allSelected () {
      this.syncIndeterminate()
    },
    data () {
      this.$nextTick(this.syncIndeterminate)
    },
    selectedKeys () {
      this.$nextTick(this.syncIndeterminate)
    },
  },
  mounted () {
    if (!this.animated || prefersReducedMotion()) {
      this.barReady = true
    } else {
      this.$nextTick(() => {
        raf(() => {
          this.barReady = true
        })
      })
    }
    this.syncIndeterminate()
  },
  methods: {
    padIndex,
    rowKeyValue (row, index) {
      return this.rowKey && row[this.rowKey] !== undefined ? row[this.rowKey] : index
    },
    /** 行标识：优先用 rowKey 字段，缺省退回索引 */
    keyOf (row, index) {
      return this.rowKeyValue(row, index)
    },
    isSelected (row, index) {
      return this.selectedKeys.indexOf(this.keyOf(row, index)) > -1
    },
    isRowSelectDisabled (row) {
      if (typeof this.rowSelectDisabled === 'function') return !!this.rowSelectDisabled(row)
      return !!this.rowSelectDisabled
    },
    /**
     * 全选框的「部分选中」态。
     * 原生 input 没有 HTML 属性表达 indeterminate，只能通过 DOM 属性设置，
     * 所以这里在渲染后手动同步一次。
     */
    syncIndeterminate () {
      const el = this.$refs.selectAll
      if (!el) return
      const total = this.selectableEntries.length
      const selected = this.selectableEntries.filter((entry) => this.isSelected(entry.row, entry.index)).length
      el.indeterminate = selected > 0 && selected < total
    },
    handleSelectAll (event) {
      const checked = event.target.checked
      const currentKeys = this.data.map((row, index) => this.keyOf(row, index))
      let next

      if (checked) {
        next = this.selectedKeys.slice()
        this.selectableEntries.forEach((entry) => {
          const key = this.keyOf(entry.row, entry.index)
          if (key !== undefined && key !== null && next.indexOf(key) === -1) next.push(key)
        })
      } else {
        // 只取消当前页，保留其它页已选的项
        next = this.selectedKeys.filter((key) => currentKeys.indexOf(key) === -1)
      }

      this.emitSelectChange(next)
    },
    handleSelectRow (row, index, event) {
      const key = this.keyOf(row, index)
      if (key === undefined || key === null) return
      const next = this.selectedKeys.slice()
      const position = next.indexOf(key)
      if (event.target.checked && position === -1) {
        next.push(key)
      } else if (!event.target.checked && position > -1) {
        next.splice(position, 1)
      }
      this.emitSelectChange(next)
    },
    emitSelectChange (keys) {
      const rows = this.data.filter((row, index) => keys.indexOf(this.keyOf(row, index)) > -1)
      this.$emit('update:selectedKeys', keys)
      this.$emit('select-change', keys, rows)
      this.$nextTick(this.syncIndeterminate)
    },
    columnWidth (col) {
      if (col.width) return typeof col.width === 'number' ? `${col.width}px` : col.width
      return 'auto'
    },
    alignClass (col) {
      return col.align ? `is-${col.align}` : ''
    },
    /** 单元格文本：优先使用列自定义 formatter */
    cellText (row, col, override) {
      if (override !== undefined && override !== null && override !== '') return override
      const value = row[col.key]
      if (typeof col.formatter === 'function') return col.formatter(value, row, col)
      if (col.type === 'percent') return formatPercent(value, col.precision === undefined ? this.precision : col.precision)
      if (col.type === 'number') {
        return formatNumber(value, col.precision === undefined ? 0 : col.precision)
      }
      if (value === null || value === undefined || value === '') return col.placeholder || '—'
      return value
    },
    /** 标签语气：支持静态 tone，也支持 (value, row, col) => tone */
    tagTone (row, col) {
      if (typeof col.tone === 'function') return col.tone(row[col.key], row, col) || 'accent'
      return col.tone || 'accent'
    },
    /** 过滤出当前行可见的操作 */
    visibleActions (col, row) {
      const actions = col.actions || []
      return actions.filter((action) => {
        if (typeof action.visible === 'function') return action.visible(row)
        if (action.visible === undefined) return true
        return !!action.visible
      })
    },
    /** 百分比条宽度，限制在 0-100，避免异常数据撑破布局 */
    barWidth (row, col) {
      return clamp(toNumber(row[col.key]), 0, 100)
    },
    handleCellClick (row, col, index) {
      this.$emit('cell-click', row, col, index)
      // 链接列同时也是轻量操作列，统一再抛一次 action 方便父组件只监听一个事件
      this.$emit('action', row, col, index, undefined)
    },
    handleAction (row, col, index, actionKey) {
      this.$emit('action', row, col, index, actionKey)
    },
    handleRowClick (row, index) {
      if (!this.rowClickable) return
      this.$emit('row-click', row, index)
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-data-table {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;

  &__scroll {
    flex: 1 1 auto;
    min-height: 0;
    overflow-x: auto;
    // 高度由外层面板约束，超出即纵向滚动（表头 sticky 吸顶）
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 4px;
      height: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: var(--screen-border);
      border-radius: var(--screen-radius-pill);
    }
  }

  &__table {
    width: 100%;
    border-collapse: separate;
    border-spacing: 0;
    table-layout: fixed;
    font-size: var(--screen-font-sm);
  }

  thead th {
    position: sticky;
    top: 0;
    z-index: 2;
    height: 34px;
    padding: 0 var(--screen-space-3);
    font-weight: 500;
    text-align: left;
    color: var(--screen-text);
    background: var(--screen-panel-bg-solid);
    border-bottom: 1px solid var(--screen-border);
    white-space: nowrap;
  }

  // 表头首列与面板标题栏底色衔接，形成设计稿中的深色表头带
  thead th:first-child {
    border-top-left-radius: var(--screen-radius-sm);
  }

  thead th:last-child {
    border-top-right-radius: var(--screen-radius-sm);
  }

  tbody td {
    height: 44px;
    padding: 4px var(--screen-space-3);
    color: var(--screen-text-sub);
    border-bottom: 1px solid var(--screen-divider);
    vertical-align: middle;
  }

  tbody tr.is-alt td {
    background: var(--screen-row-alt);
  }

  tbody tr:hover td {
    background: var(--screen-elevate);
  }

  // 选中行：底色 + 左侧高亮条，不改变行高
  tbody tr.is-selected td {
    background: rgba(47, 227, 192, 0.09);
  }

  tbody tr.is-selected td:first-child {
    box-shadow: inset 2px 0 0 var(--screen-accent);
  }

  tbody tr.is-clickable {
    cursor: pointer;
  }

  tbody tr.is-empty td {
    height: 72px;
    text-align: center;
    color: var(--screen-text-mute);
  }

  th.is-center,
  td.is-center {
    text-align: center;
  }

  th.is-right,
  td.is-right {
    text-align: right;
  }

  &__text {
    display: block;
    .screen-ellipsis();
  }

  &__index {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-mute);
  }

  // ---------- 行选择复选框 ----------
  &__checkbox {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    cursor: pointer;

    // 原生 checkbox 保留在无障碍树里，只是视觉上隐藏
    input {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      margin: 0;
      opacity: 0;
      cursor: inherit;
    }

    input:disabled {
      cursor: not-allowed;
    }

    input:focus-visible + .screen-data-table__checkbox-box {
      outline: 2px solid var(--screen-accent);
      outline-offset: 2px;
    }

    input:checked + .screen-data-table__checkbox-box,
    input:indeterminate + .screen-data-table__checkbox-box {
      color: var(--screen-text-on-accent);
      background: var(--screen-accent);
      border-color: var(--screen-accent);
    }

    input:disabled + .screen-data-table__checkbox-box {
      opacity: 0.45;
    }
  }

  &__checkbox-box {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 15px;
    height: 15px;
    color: transparent;
    background: rgba(2, 24, 28, 0.72);
    border: 1px solid var(--screen-border);
    border-radius: 3px;
    transition: background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
  }

  // ---------- 百分比单元格 ----------
  &__percent {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }

  &__percent-value {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-sub);
    line-height: 1.1;
  }

  &__percent-track {
    display: block;
    height: 5px;
    border-radius: var(--screen-radius-pill);
    background: var(--screen-bar-track);
    overflow: hidden;
  }

  &__percent-fill {
    display: block;
    height: 100%;
    border-radius: inherit;
    transition: width 760ms var(--screen-ease);

    &.is-accent {
      .screen-bar-fill(var(--screen-viz-green), #0f9c86);
    }

    &.is-muted {
      .screen-bar-fill(var(--screen-bar-muted-from), var(--screen-bar-muted-to));
    }

    &.is-warning {
      .screen-bar-fill(var(--screen-viz-amber), #b9761c);
    }

    &.is-danger {
      .screen-bar-fill(#ff9d8d, #c9432f);
    }
  }

  // ---------- 操作按钮 ----------
  &__action {
    padding: 2px 12px;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent);
    background: rgba(47, 227, 192, 0.08);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      background: rgba(47, 227, 192, 0.18);
      border-color: var(--screen-border-strong);
    }
  }

  // ---------- 文字链接 / 多操作 ----------
  &__link {
    .screen-link-action();

    &.is-danger {
      color: var(--screen-danger);
    }

    &.is-muted {
      color: var(--screen-text-sub);
    }
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    flex-wrap: nowrap;
  }

  // ---------- 标签 ----------
  // 标签样式已统一收敛到 ScreenTag 组件（见模板中的 type='tag' 分支），
  // 这里不再保留一份表格专用的 .screen-data-table__tag，避免两套标签视觉漂移。

  // ---------- 加载遮罩 ----------
  &__loading {
    position: absolute;
    inset: 0;
    z-index: 3;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--screen-space-2);
    background: rgba(2, 19, 22, 0.62);
  }

  &__spinner {
    width: 22px;
    height: 22px;
    border: 2px solid var(--screen-border);
    border-top-color: var(--screen-accent);
    border-radius: 50%;
    animation: screen-data-table-spin 800ms linear infinite;
  }

  &__loading-text {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }
}

@keyframes screen-data-table-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .screen-data-table__percent-fill {
    transition: none;
  }

  // 减速而不是静止：仍然能看出「正在加载」
  .screen-data-table__spinner {
    animation-duration: 2400ms;
  }
}
</style>
