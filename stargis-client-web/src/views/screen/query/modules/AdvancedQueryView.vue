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
      条件串预览（在 QueryConditionBuilder 里）只是给人看的，不是发给后端的东西，
      即使有人往里塞引号，也不会变成 SQL。

    ★ 导出走 GET + `conditionsJson`：
      `window.open` 只能发 GET，所以条件要能塞进查询串；
      后端会把它反序列化成同一套结构再落到同一个 Service。

    ★ 两个导出按钮（清单 P1-13 第 1 条）：
       「按自定义列导出」= 结果表勾了哪些列就导哪些（两个普通查询页的行为）；
       「导出全部字段」  = 忽略列选择、保留查询条件，后端按字段注册表顺序输出全部业务列。
       做成两个按钮而不是一个带状态的开关：开关会留在界面上，下一个人点导出时
       根本不知道自己正在用哪种模式。
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
      </div>

      <!--
        条件构造器与「两个普通查询页的自定义条件」是同一个组件（清单 P2-11），
        这里的规则（类型驱动控件、白名单运算符、空条件不下发）只有一份实现。
      -->
      <query-condition-builder
        ref="builder"
        :columns="columns"
        :options="options"
        :value="conditions"
        @input="conditions = $event"
        @search="handleSearch"
      />

      <template #footer>
        <div class="advanced-query__actions">
          <screen-button size="sm" type="primary" icon="search" :loading="loading" @click="handleSearch">
            查询
          </screen-button>

          <!-- 两种导出是两条明确路径，不做成一个会记住状态的开关（清单 P1-13 第 1 条） -->
          <screen-button
            size="sm"
            icon="download"
            :disabled="!pagination.total"
            @click="handleExport(false)"
          >
            按自定义列导出
          </screen-button>

          <screen-button
            size="sm"
            icon="download"
            :disabled="!pagination.total"
            :loading="exportingAll"
            @click="handleExport(true)"
          >
            导出全部字段
          </screen-button>

          <span class="advanced-query__tip">
            导出全部字段 = 忽略列选择，仅保留查询条件；表头用中文名 + 单位
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
  ScreenPanel,
  ScreenSelect
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  buildExportUrl,
  queryAdvancedPage,
  queryAdvancedSummary,
  queryColumns,
  queryOptions
} from '@/api/land/statQuery'
import { PAGE_SIZE_OPTIONS, metricsOf } from '../constants'
import QueryConditionBuilder from './QueryConditionBuilder.vue'
import QueryResultPanel from './QueryResultPanel.vue'
import StatSummaryPanel from './StatSummaryPanel.vue'

export default {
  name: 'AdvancedQueryView',
  components: {
    QueryConditionBuilder,
    QueryResultPanel,
    ScreenButton,
    ScreenField,
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
      columns: [],
      /** 候选值（字段 key → 候选清单），给构造器里的下拉用；来自后端候选值接口 */
      options: {},
      /** 条件行：[{ field, operator, value, relation }]，与构造器是受控关系 */
      conditions: [],
      selectedKeys: [],
      rows: [],
      summary: {},
      loading: false,
      /** 「导出全部字段」的下载中标记：导出是 window.open，只能给人一个「已经点了」的反馈 */
      exportingAll: false,
      pagination: { current: 1, pageSize: 20, total: 0 },
      pageSizeOptions: PAGE_SIZE_OPTIONS
    }
  },
  computed: {
    metrics () {
      return metricsOf(this.dataType)
    }
  },
  created () {
    this.loadColumns()
  },
  methods: {
    /**
     * 字段元数据 + 候选值。
     * 候选值单独取一次（与 StatQueryView 同一个接口），否则构造器里的
     * 「行政区划」「配套设施类别」这些字段只能手打，打错一个字符就查不到。
     */
    async loadColumns () {
      const [colRes, optRes] = await Promise.all([
        queryColumns(this.dataType),
        queryOptions(this.dataType)
      ])
      if (!colRes || !colRes.success) {
        toast.error((colRes && colRes.message) || '字段定义加载失败')
        return
      }
      this.columns = colRes.result || []
      this.options = (optRes && optRes.success && optRes.result) || {}
      this.selectedKeys = this.columns
        .filter((column) => column.defaultColumn)
        .map((column) => column.key)
      if (!this.conditions.length) {
        // 至少给一行空条件，用户不用先点「添加条件」才知道这里能填
        this.conditions = [{ field: '', operator: 'like', value: '', relation: 'AND' }]
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

    /**
     * 清单 P2-01：普通离开再进入模块时清空检索条件、页码与结果。
     * 由父级（query/index.vue）在页签切换 / 页面激活时调用：
     *   - 条件回到一行空行（界面上能看见「没有条件」，而不是留着旧条件）；
     *   - 页码回 1 并重查，结果与条件永远一致。
     */
    async resetFilters () {
      this.conditions = [{ field: '', operator: 'like', value: '', relation: 'AND' }]
      this.pagination = Object.assign({}, this.pagination, { current: 1 })
      return this.handleSearch(1)
    },

    /** 只有「字段 + 值」都填了的条件才下发（顺序保持界面上的顺序，关系符按序拼接） */
    buildPayload (pageNo) {
      const builder = this.$refs.builder
      // 构造器还没挂载（created 里的首次查询可能早于 ref 就绪）时按父组件持有的行自行过滤，
      // 规则与构造器一致：字段与值都要有。不能直接给空数组 —— 那会把用户刚填的条件吞掉。
      const conditions = builder && builder.buildConditions
        ? builder.buildConditions()
        : (this.conditions || [])
          .filter((row) => row && row.field &&
            row.value !== null && row.value !== undefined && String(row.value).trim() !== '')
          .map((row) => ({
            field: row.field,
            operator: row.operator || 'like',
            value: String(row.value).trim(),
            relation: row.relation === 'OR' ? 'OR' : 'AND'
          }))
      return {
        dataType: this.dataType,
        conditions,
        columns: this.selectedKeys,
        pageNo: pageNo || 1,
        pageSize: this.pagination.pageSize
      }
    },

    /** 提交前先校验：非法值（数值字段填了文字、日期格式不对）给一句明确的提示 */
    checkConditions () {
      const builder = this.$refs.builder
      const message = builder && builder.validate ? builder.validate() : ''
      if (message) {
        toast.warning(message)
        return false
      }
      return true
    },

    async handleSearch (pageNo) {
      if (!this.checkConditions()) {
        return
      }
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

    /**
     * 导出。
     *
     * @param {boolean} allFields true = 「导出全部字段」（忽略列选择、保留查询条件）
     *
     * ★ 全部字段模式**不传 columns**：不是省流量，而是让请求本身就表达清楚
     *   「这次导的是全部字段」—— 后端看到 allColumns=true 会直接按注册表出列，
     *   但日志/抓包里少一个可能误导人的 columns 参数更省事。
     * ★ 查询条件照常带上（conditionsJson），否则「全量导出」会变成「忽略筛选的全表导出」，
     *   那正是清单要求里明确不做的。
     */
    handleExport (allFields) {
      if (!this.checkConditions()) {
        return
      }
      const payload = this.buildPayload(1)
      const params = {
        dataType: payload.dataType,
        conditionsJson: JSON.stringify(payload.conditions)
      }
      if (allFields) {
        params.allColumns = true
        this.exportingAll = true
        // window.open 拿不到「下载完成」的回调，1.5 秒后放掉 loading 只为避免按钮一直转
        window.setTimeout(() => { this.exportingAll = false }, 1500)
      } else {
        params.columns = payload.columns.join(',')
      }
      window.open(buildExportUrl(params), '_blank')
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

  /*
    条件行的版式（关系符/字段/运算符/值/删除）已经搬进 QueryConditionBuilder.vue，
    这里不再保留同名样式 —— 留着只会让下一个人以为改这里能生效（scoped 也够不到子组件）。
  */

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  &__tip {
    margin-left: auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }
}
</style>
