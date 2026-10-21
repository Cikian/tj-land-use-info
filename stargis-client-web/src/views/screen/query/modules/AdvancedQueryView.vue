<template>
  <!--
    AdvancedQueryView 查询导出（高保真的「查询导出」页签）
    ===============================================================
    对应旧系统 `queryXjCommercialLand.html` 的四个控件：
      数据类型 / 字段名 / 运算符 / 字段值 + 关系符（与或）
    逐条追加成条件串，然后「查询」或「导出 Excel」。

    ★ 与旧系统的根本区别在**安全**：
      旧前端把条件拼成 `字段 like '%值%'` 这样的 SQL 片段（`insertSql123()`），
      交给后端原样执行；这里只提交 { field, operator, value, relation } 四个**数据**，
      后端按白名单字段生成 SQL、值走参数绑定。
      因此下面那行「条件串预览」只是给人看的（不是发给后端的东西），
      即使有人往里塞引号，也不会变成 SQL。

    ★ 导出走 GET + `conditionsJson`：
      `window.open` 只能发 GET，所以条件要能塞进查询串；
      后端会把它反序列化成同一套结构再落到同一个 Service。
  -->
  <div class="advanced-query">
    <!-- ---------- 条件构造 ---------- -->
    <screen-panel class="advanced-query__builder" title="查询导出">
      <div class="advanced-query__head">
        <screen-field label="数据类型" label-width="72px" html-for="aq-datatype">
          <screen-select
            id="aq-datatype"
            :value="dataType"
            :options="dataTypeOptions"
            :searchable="false"
            aria-label="数据类型"
            @input="handleDataTypeChange"
          />
        </screen-field>
        <screen-button size="sm" icon="plus" @click="addCondition">添加条件</screen-button>
        <screen-button size="sm" icon="close" @click="resetConditions">清空条件</screen-button>
      </div>

      <div class="advanced-query__rows">
        <div v-for="(row, index) in conditions" :key="index" class="advanced-query__row">
          <span class="advanced-query__index">{{ index + 1 }}</span>

          <!-- 关系符：第一条不需要（旧系统的「与或」也是从第二条起生效） -->
          <screen-select
            v-if="index > 0"
            :value="row.relation"
            :options="relationOptions"
            :searchable="false"
            class="advanced-query__relation"
            :aria-label="`第 ${index + 1} 条与上一条的关系`"
            @input="updateRow(index, 'relation', $event)"
          />
          <span v-else class="advanced-query__relation is-placeholder">—</span>

          <screen-select
            :value="row.field"
            :options="fieldOptions"
            class="advanced-query__field"
            :aria-label="`第 ${index + 1} 条字段`"
            @input="handleFieldChange(index, $event)"
          />

          <screen-select
            :value="row.operator"
            :options="operatorOptions(row.field)"
            :searchable="false"
            class="advanced-query__operator"
            :aria-label="`第 ${index + 1} 条运算符`"
            @input="updateRow(index, 'operator', $event)"
          />

          <screen-input
            :value="row.value"
            class="advanced-query__value"
            :placeholder="placeholderOf(row.field)"
            clearable
            :aria-label="`第 ${index + 1} 条字段值`"
            @input="updateRow(index, 'value', $event)"
            @enter="handleSearch"
          />

          <screen-button
            size="sm"
            icon="trash"
            aria-label="删除这条条件"
            @click="removeCondition(index)"
          />
        </div>
      </div>

      <div class="advanced-query__preview">
        <span class="advanced-query__preview-label">条件串（仅供核对，不发给后端）</span>
        <code class="advanced-query__preview-text">{{ conditionPreview }}</code>
      </div>

      <template #footer>
        <div class="advanced-query__actions">
          <screen-button size="sm" type="primary" icon="search" :loading="loading" @click="handleSearch">
            查询
          </screen-button>
          <screen-button size="sm" icon="download" :disabled="!pagination.total" @click="handleExport">
            导出 Excel
          </screen-button>
          <span class="advanced-query__tip">
            字段与运算符由后端白名单限定：不会出现「拼错列名」或「把值当 SQL 执行」
          </span>
        </div>
      </template>
    </screen-panel>

    <!-- ---------- 分析结果 ---------- -->
    <stat-summary-panel
      class="advanced-query__summary"
      :metrics="metrics"
      :summary="summary"
      :loading="loading"
    />

    <!-- ---------- 结果表 ---------- -->
    <query-result-panel
      class="advanced-query__result"
      title="查询结果"
      :columns="columns"
      :selected-keys="selectedKeys"
      :rows="rows"
      :total="pagination.total"
      :loading="loading"
      :page-no="pagination.current"
      :page-size="pagination.pageSize"
      :page-size-options="pageSizeOptions"
      @toggle-column="handleToggleColumn"
      @reset-columns="handleResetColumns"
      @page-change="handlePageChange"
      @refresh="handleSearch"
      @export="handleExport"
    />
  </div>
</template>

<script>
import {
  ScreenButton,
  ScreenField,
  ScreenInput,
  ScreenPanel,
  ScreenSelect
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  buildExportUrl,
  queryAdvancedPage,
  queryAdvancedSummary,
  queryColumns
} from '@/api/land/statQuery'
import { PAGE_SIZE_OPTIONS, metricsOf } from '../constants'
import QueryResultPanel from './QueryResultPanel.vue'
import StatSummaryPanel from './StatSummaryPanel.vue'

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

export default {
  name: 'AdvancedQueryView',
  components: {
    QueryResultPanel,
    ScreenButton,
    ScreenField,
    ScreenInput,
    ScreenPanel,
    ScreenSelect,
    StatSummaryPanel
  },
  data () {
    return {
      dataType: 'land',
      dataTypeOptions: [
        { value: 'land', label: '经营性用地' },
        { value: 'facility', label: '用地配套项目' }
      ],
      relationOptions: [
        { value: 'AND', label: '并且' },
        { value: 'OR', label: '或者' }
      ],
      columns: [],
      /** 条件行：[{ field, operator, value, relation }] */
      conditions: [],
      selectedKeys: [],
      rows: [],
      summary: {},
      loading: false,
      pagination: { current: 1, pageSize: 20, total: 0 },
      pageSizeOptions: PAGE_SIZE_OPTIONS
    }
  },
  computed: {
    metrics () {
      return metricsOf(this.dataType)
    },
    fieldOptions () {
      return this.columns.map((column) => ({ value: column.key, label: column.label }))
    },
    /** 条件串预览（人类可读，形如 dkmc LIKE '%浯水道%' 并且 xzqh = '津南区'） */
    conditionPreview () {
      const parts = []
      this.conditions.forEach((row, index) => {
        if (!row.field || !row.value) {
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
  created () {
    this.loadColumns()
  },
  methods: {
    labelOf (key) {
      const column = this.columns.find((item) => item.key === key)
      return column ? column.label : key
    },
    operatorOptions (key) {
      const column = this.columns.find((item) => item.key === key)
      const list = (column && column.operators) || ['like', '=', '!=']
      return list.map((operator) => ({ value: operator, label: OPERATOR_LABELS[operator] || operator }))
    },
    placeholderOf (key) {
      const column = this.columns.find((item) => item.key === key)
      if (!column) {
        return '请先选字段'
      }
      if (column.type === 'DATE') {
        return '如 2025-06-01'
      }
      if (column.type === 'NUMBER') {
        return '输入数值'
      }
      return '输入关键词'
    },

    async loadColumns () {
      const res = await queryColumns(this.dataType)
      if (!res || !res.success) {
        toast.error((res && res.message) || '字段定义加载失败')
        return
      }
      this.columns = res.result || []
      this.selectedKeys = this.columns
        .filter((column) => column.defaultColumn)
        .map((column) => column.key)
      if (!this.conditions.length) {
        this.addCondition()
      }
      await this.handleSearch()
    },

    handleDataTypeChange (value) {
      this.dataType = value
      this.conditions = []
      this.rows = []
      this.summary = {}
      this.pagination = Object.assign({}, this.pagination, { current: 1, total: 0 })
      this.loadColumns()
    },

    addCondition () {
      this.conditions.push({ field: '', operator: 'like', value: '', relation: 'AND' })
    },

    removeCondition (index) {
      this.conditions.splice(index, 1)
    },

    resetConditions () {
      this.conditions = []
      this.addCondition()
    },

    updateRow (index, key, value) {
      this.$set(this.conditions[index], key, value)
    },

    /** 换字段时把运算符重置成该字段的第一个合法运算符（数值字段不能 like） */
    handleFieldChange (index, key) {
      const options = this.operatorOptions(key)
      this.$set(this.conditions[index], 'field', key)
      this.$set(this.conditions[index], 'operator', options.length ? options[0].value : 'like')
    },

    /** 只有「字段 + 值」都填了的条件才下发（顺序保持界面上的顺序，关系符按序拼接） */
    buildPayload (pageNo) {
      return {
        dataType: this.dataType,
        conditions: this.conditions
          .filter((row) => row.field && row.value !== '' && row.value !== null && row.value !== undefined)
          .map((row) => ({
            field: row.field,
            operator: row.operator,
            value: String(row.value).trim(),
            relation: row.relation
          })),
        columns: this.selectedKeys,
        pageNo: pageNo || 1,
        pageSize: this.pagination.pageSize
      }
    },

    async handleSearch (pageNo) {
      const current = typeof pageNo === 'number' ? pageNo : 1
      const payload = this.buildPayload(current)
      this.loading = true
      try {
        const [pageRes, statRes] = await Promise.all([
          queryAdvancedPage(payload),
          queryAdvancedSummary(payload)
        ])
        if (!pageRes || !pageRes.success) {
          toast.error((pageRes && pageRes.message) || '高级查询失败')
          return
        }
        const page = pageRes.result || {}
        this.rows = page.rows || []
        this.pagination = Object.assign({}, this.pagination, {
          current: Number(page.pageNo || current),
          pageSize: Number(page.pageSize || this.pagination.pageSize),
          total: Number(page.total || 0)
        })
        this.summary = (statRes && statRes.success && statRes.result) || {}
      } catch (e) {
        toast.error('高级查询失败：' + (e && e.message ? e.message : '网络异常'))
      } finally {
        this.loading = false
      }
    },

    handlePageChange (payload) {
      const current = payload && payload.current ? payload.current : this.pagination.current
      const pageSize = payload && payload.pageSize ? payload.pageSize : this.pagination.pageSize
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.handleSearch(current)
    },

    handleToggleColumn (key) {
      const index = this.selectedKeys.indexOf(key)
      if (index > -1) {
        if (this.selectedKeys.length === 1) {
          toast.warning('至少要保留一列')
          return
        }
        this.selectedKeys = this.selectedKeys.filter((item) => item !== key)
      } else {
        this.selectedKeys = this.selectedKeys.concat(key)
      }
    },

    handleResetColumns () {
      this.selectedKeys = this.columns
        .filter((column) => column.defaultColumn)
        .map((column) => column.key)
    },

    handleExport () {
      const payload = this.buildPayload(1)
      window.open(buildExportUrl({
        dataType: payload.dataType,
        columns: payload.columns.join(','),
        conditionsJson: JSON.stringify(payload.conditions)
      }), '_blank')
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.advanced-query {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;

  &__builder {
    flex: 0 0 auto;
  }

  &__summary {
    flex: 0 0 auto;
  }

  &__result {
    flex: 1 1 0;
    min-height: 220px;
  }

  &__head {
    display: flex;
    align-items: flex-end;
    gap: 12px;
    margin-bottom: 10px;
  }

  &__rows {
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 190px;
    overflow-y: auto;
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
  }

  &__preview {
    display: flex;
    gap: 10px;
    align-items: baseline;
    margin-top: 10px;
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

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__tip {
    margin-left: auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }
}
</style>
