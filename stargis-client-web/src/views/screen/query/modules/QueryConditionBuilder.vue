<template>
  <!--
    QueryConditionBuilder 自定义查询条件构造器（查询导出页 + 两个普通查询页共用）
    ==================================================================
    一行 = 关系符 + 字段 + 运算符 + 值；从第二条起关系符可选「并且 / 或者」。

    ★ 为什么抽成独立组件（清单 P2-11 第 1 条）：
      这段「字段 + 运算符 + 值 + AND/OR」原本内嵌在 AdvancedQueryView 里，
      而普通查询页（StatQueryView）也要「自定义条件」。复制一份的话，
      「数值字段不能 LIKE」「候选值字段走下拉」「空条件不下发」这些规则
      迟早两边不一致 —— 最后表现为「高级查询查得到、普通查询查不到」，
      最难解释。所以只有这一个构造器，两个页面共用同一份规则。

    ★ 安全边界不变（不许在这里被改写）：
      本组件只产出 { field, operator, value, relation } 四个**数据**，
      字段名与运算符随后仍要被后端 QueryFieldRegistry 白名单校验、值走 MyBatis
      参数绑定。下面那行「条件串」只是给人核对用的字符串，不发给后端。

    ★ 受控组件：条件数组由父组件持有（value / input），本组件不存副本。
      好处是「父组件清空条件」（P2-01 的 resetFilters）不需要再调一次子组件方法，
      也不会有「父已经清空、子还留着旧行」的两份状态。

    ★ 打开时用 v-show 而不是 v-if（父组件那侧）：
      本组件暴露的 validate() 要靠 ref 调用，v-if 卸载后 ref 是 undefined，
      条件收起时点「查询」就取不到校验结果了。
  -->
  <div class="condition-builder">
    <div class="condition-builder__toolbar">
      <screen-button size="sm" icon="plus" @click="addRow">添加条件</screen-button>
      <screen-button size="sm" icon="close" :disabled="!rows.length" @click="clearRows">
        清空条件
      </screen-button>
      <span class="condition-builder__hint">
        只有「字段 + 值」都填了的条件才参与查询
      </span>
    </div>

    <p v-if="!rows.length" class="condition-builder__empty">
      尚未添加条件：当前等于「查询全部数据」
    </p>

    <div v-else class="condition-builder__list">
      <div v-for="(row, index) in rows" :key="index" class="condition-builder__item">
        <div class="condition-builder__row">
          <span class="condition-builder__index">{{ index + 1 }}</span>

          <!-- 关系符：第一条不需要（旧系统的「与或」也是从第二条起生效） -->
          <screen-select
            v-if="index > 0"
            :value="row.relation"
            :options="relationOptions"
            :searchable="false"
            class="condition-builder__relation"
            :aria-label="`第 ${index + 1} 条与上一条的关系`"
            @input="updateRow(index, 'relation', $event)"
          />
          <span v-else class="condition-builder__relation is-placeholder">—</span>

          <screen-select
            :value="row.field"
            :options="fieldOptions"
            placeholder="选择字段"
            :searchable="true"
            class="condition-builder__field"
            :aria-label="`第 ${index + 1} 条字段`"
            @input="handleFieldChange(index, $event)"
          />

          <screen-select
            :value="row.operator"
            :options="operatorOptions(row.field)"
            :searchable="false"
            class="condition-builder__operator"
            :aria-label="`第 ${index + 1} 条运算符`"
            @input="updateRow(index, 'operator', $event)"
          />

          <!-- 值的控件由字段类型决定（清单 P2-11 第 3 条） -->
          <div class="condition-builder__value">
            <screen-select
              v-if="candidatesOf(row.field).length"
              :value="row.value"
              :options="candidatesOf(row.field)"
              :placeholder="'选择值'"
              :searchable="true"
              :invalid="!!errorOf(row)"
              :aria-label="`第 ${index + 1} 条字段值`"
              @input="updateRow(index, 'value', $event)"
            />
            <screen-date-input
              v-else-if="typeOf(row.field) === 'DATE'"
              :value="row.value"
              size="sm"
              :invalid="!!errorOf(row)"
              placeholder="选择日期"
              @input="updateRow(index, 'value', $event)"
            />
            <screen-input
              v-else
              :value="row.value"
              :type="typeOf(row.field) === 'NUMBER' ? 'number' : 'text'"
              :placeholder="placeholderOf(row.field)"
              :invalid="!!errorOf(row)"
              clearable
              :aria-label="`第 ${index + 1} 条字段值`"
              @input="updateRow(index, 'value', $event)"
              @enter="$emit('search')"
            />
          </div>

          <screen-button
            size="sm"
            icon="trash"
            :aria-label="`删除第 ${index + 1} 条条件`"
            @click="removeRow(index)"
          />
        </div>

        <!-- 颜色不能是唯一提示：非法值必须有一行文字说明（清单 P2-11 第 4 条） -->
        <p v-if="errorOf(row)" class="condition-builder__error">{{ errorOf(row) }}</p>
      </div>
    </div>

    <div v-if="rows.length" class="condition-builder__preview">
      <span class="condition-builder__preview-label">条件串（仅供核对，不发给后端）</span>
      <code class="condition-builder__preview-text">{{ preview }}</code>
    </div>
  </div>
</template>

<script>
import {
  ScreenButton,
  ScreenDateInput,
  ScreenInput,
  ScreenSelect
} from '@/components/screen'

/** 运算符的中文名（后端只认 like / = / != / > / >= / < / <=） */
const OPERATOR_LABELS = {
  like: '类似',
  '=': '等于',
  '!=': '不等于',
  '>': '大于',
  '>=': '大于等于',
  '<': '小于',
  '<=': '小于等于'
}

/** 数值：允许负号与小数（后端是 BigDecimal，不做四舍五入） */
const NUMBER_PATTERN = /^-?\d+(\.\d+)?$/
/** 日期：与后端 DATE 字段的比较口径一致（yyyy-MM-dd） */
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

export default {
  name: 'QueryConditionBuilder',
  components: { ScreenButton, ScreenDateInput, ScreenInput, ScreenSelect },
  props: {
    /** 可查询字段（后端 /land/stat/query/columns 的 QueryColumnVO[]：key/label/type/operators） */
    columns: { type: Array, default: () => [] },
    /** 候选值：{ [字段 key]: ['候选1', ...] }，来自 /land/stat/query/options */
    options: { type: Object, default: () => ({}) },
    /** 条件行：[{ field, operator, value, relation }]，由父组件持有 */
    value: { type: Array, default: () => [] }
  },
  computed: {
    rows () {
      return this.value || []
    },
    relationOptions () {
      return [
        { value: 'AND', label: '并且' },
        { value: 'OR', label: '或者' }
      ]
    },
    fieldOptions () {
      return this.columns.map((column) => ({ value: column.key, label: column.label }))
    },
    /** 条件串预览（人类可读，形如 地块名称 类似 %浯水道% 并且 行政区划 等于 '津南区'） */
    preview () {
      const parts = []
      this.rows.forEach((row, index) => {
        if (!row.field || !this.hasValue(row)) {
          return
        }
        const label = this.labelOf(row.field)
        const operator = OPERATOR_LABELS[row.operator] || row.operator
        const value = row.operator === 'like' ? `%${row.value}%` : `'${row.value}'`
        const relation = index > 0 ? (row.relation === 'OR' ? ' 或者 ' : ' 并且 ') : ''
        parts.push(`${relation}${label} ${operator} ${value}`)
      })
      return parts.length ? parts.join('') : '（未设置条件，查询全部数据）'
    }
  },
  methods: {
    /* ---------------- 对外能力（父组件通过 ref 调用） ---------------- */

    /** 新增一行（父组件「自定义条件」入口也用它，保证空白行格式一致） */
    addRow () {
      this.emitRows(this.rows.concat([this.emptyRow()]))
    },

    /** 清空（父组件的 resetFilters 用它，等价于把条件数组置空） */
    clearRows () {
      this.emitRows([])
    },

    /**
     * 校验：返回 '' 表示通过，否则返回一句能直接 toast 的中文。
     *
     * ★ 为什么前端也要拦一道：后端对数值字段收到非数字时是 SQL 报错（或静默 0 行），
     *   用户看到的是「查询失败」而不是「这条条件填错了」。这里按字段类型先给结论，
     *   后端的白名单与参数绑定仍然是最终边界，不因为这里放过就变成注入面。
     */
    validate () {
      for (let index = 0; index < this.rows.length; index += 1) {
        const row = this.rows[index]
        if (!row.field) {
          if (this.hasValue(row)) {
            return `第 ${index + 1} 条条件已填值但没选字段`
          }
          continue
        }
        const error = this.errorOf(row)
        if (error) {
          return error
        }
      }
      return ''
    },

    /**
     * 下发给后端的条件数组：只有「字段 + 值」都有的才进（清单 P2-11 第 3 条）。
     * 关系符按界面顺序拼接，第一条无效。
     */
    buildConditions () {
      const conditions = []
      this.rows.forEach((row) => {
        if (!row.field || !this.hasValue(row)) {
          return
        }
        conditions.push({
          field: row.field,
          operator: row.operator || 'like',
          value: String(row.value).trim(),
          relation: row.relation === 'OR' ? 'OR' : 'AND'
        })
      })
      return conditions
    },

    /* ---------------- 内部 ---------------- */

    emptyRow () {
      return { field: '', operator: 'like', value: '', relation: 'AND' }
    },

    emitRows (rows) {
      this.$emit('input', rows)
    },

    /** 改某行的某个字段：整数组换新（受控组件，避免就地修改 prop） */
    updateRow (index, key, value) {
      const rows = this.rows.map((row, i) => {
        if (i !== index) {
          return row
        }
        const next = Object.assign({}, row)
        next[key] = value
        return next
      })
      this.emitRows(rows)
    },

    removeRow (index) {
      this.emitRows(this.rows.filter((row, i) => i !== index))
    },

    /** 换字段时把运算符重置成该字段的第一个合法运算符（数值/日期字段不能 like） */
    handleFieldChange (index, key) {
      const options = this.operatorOptions(key)
      const rows = this.rows.map((row, i) => {
        if (i !== index) {
          return row
        }
        const next = Object.assign({}, row)
        next.field = key
        next.operator = options.length ? options[0].value : 'like'
        // 换字段后旧值几乎一定不合法（比如从区划换成日期），一并清掉而不是留着误导
        next.value = ''
        return next
      })
      this.emitRows(rows)
    },

    columnOf (key) {
      if (!key) {
        return null
      }
      return this.columns.find((column) => column.key === key) || null
    },

    labelOf (key) {
      const column = this.columnOf(key)
      return column ? column.label : key
    },

    typeOf (key) {
      const column = this.columnOf(key)
      return column ? column.type : 'TEXT'
    },

    operatorOptions (key) {
      const column = this.columnOf(key)
      const list = (column && column.operators) || ['like', '=', '!=']
      return list.map((operator) => ({
        value: operator,
        label: OPERATOR_LABELS[operator] || operator
      }))
    },

    placeholderOf (key) {
      const type = this.typeOf(key)
      if (type === 'DATE') {
        return '如 2025-06-01'
      }
      if (type === 'NUMBER') {
        return '输入数值'
      }
      return '输入关键词'
    },

    /** 字段有候选值时用下拉（候选来自后端注册表的候选值接口，不写死枚举） */
    candidatesOf (key) {
      if (!key) {
        return []
      }
      const list = this.options[key] || []
      return list.map((item) => ({ value: String(item), label: String(item) }))
    },

    hasValue (row) {
      return row && row.value !== null && row.value !== undefined && String(row.value).trim() !== ''
    },

    /** 某一行的非法原因（空串 = 合法）；同时用于描边与错误文案，两处判断不会分叉 */
    errorOf (row) {
      if (!row || !this.hasValue(row)) {
        return ''
      }
      const type = this.typeOf(row.field)
      const value = String(row.value).trim()
      const label = this.labelOf(row.field)
      if (type === 'NUMBER' && !NUMBER_PATTERN.test(value)) {
        return `「${label}」需要填数值，当前是「${value}」`
      }
      if (type === 'DATE' && !DATE_PATTERN.test(value)) {
        return `「${label}」需要 yyyy-MM-dd 格式的日期，当前是「${value}」`
      }
      return ''
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.condition-builder {
  display: flex;
  flex-direction: column;
  gap: 8px;

  &__toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__hint {
    margin-left: auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__empty {
    margin: 0;
    padding: 8px 10px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    background: var(--screen-elevate);
    border: 1px dashed var(--screen-border);
    border-radius: var(--screen-radius-sm);
  }

  // 条件多时只在内部滚动，不把整块面板撑高（面板高度决定下面结果表能看几行）
  &__list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 190px;
    overflow-y: auto;
  }

  &__item {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__row {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__index {
    flex: 0 0 20px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    text-align: center;
  }

  &__relation {
    flex: 0 0 92px;
  }

  &__field {
    flex: 0 0 200px;
  }

  &__operator {
    flex: 0 0 120px;
  }

  &__value {
    flex: 1 1 auto;
    min-width: 0;
  }

  &__error {
    margin: 0 0 0 28px;
    font-size: var(--screen-font-xs);
    // 复用全局危险色：与控件描边的 invalid 态同一个来源，不另造一套红
    color: var(--screen-danger);
  }

  &__preview {
    display: flex;
    gap: 10px;
    align-items: baseline;
    padding: 8px 10px;
    background: var(--screen-elevate);
    border: 1px dashed var(--screen-border);
    border-radius: var(--screen-radius-sm);
  }

  &__preview-label {
    flex: 0 0 auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__preview-text {
    flex: 1 1 auto;
    font-family: var(--screen-font-mono, monospace);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    word-break: break-all;
  }
}
</style>
