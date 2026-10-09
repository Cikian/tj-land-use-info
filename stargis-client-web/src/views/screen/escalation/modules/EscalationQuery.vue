<template>
  <!--
    EscalationQuery 查询统计（方案 2.3.3 第 2 项：按基本信息、论证结果多维查询，可导出）
    --------------------------------
    9 个检索条件（EscalationSearchForm，默认全展开）+ 5 张统计图 + 结果列表 + 导出。

    ★ 统计口径与列表完全一致：上方筛选什么，下面的图与列表就统计什么
      （后端也是同一段 queryWhere，照档案模块立下的约定）。

    ★ 5 张图与所用组件：
      按论证结果分布  ScreenDonut      （5 个取值，环形 + 图例数值）
      按申报单位排行  ScreenRankList   （单行紧凑排行，取前 10）
      按行政区划分布  ScreenBarList    （两行堆叠，16 区中文名较长）
      按办理状态分布  ScreenDonut      （4 个固定状态，走 ledger/countByStatus 取数）
      按申报时间趋势  ScreenLineChart  （手写 SVG 折线与面积，**不引 echarts**）

    ★ 下钻：点图元切到「资料及台账管理」并把条件透传（由 index.vue 的 handleDrill
      接管，照档案模块的 handleDrill + applyDrill 双路径写法）。
      ScreenRankList / ScreenBarList 自带 clickable + item-click；
      ScreenDonut 与 ScreenLineChart **没有点击事件**，所以给这两类图配一排
      真实的可点击图例按钮（button，键盘可达）—— 下钻能力不打折，也不改组件库。
  -->
  <div class="escalation-query">
    <screen-panel class="escalation-query__search" title="查询统计 · 检索条件" collapsible>
      <escalation-search-form ref="search" default-expanded @search="handleSearch" />
    </screen-panel>

    <div class="escalation-query__scroll">
      <!-- 统计计算中：内联提示，不遮挡已经算好的图（overlay=false） -->
      <screen-loading :loading="chartLoading" :overlay="false" text="统计计算中…" />

      <!-- ============ 关键指标 ============ -->
      <div class="escalation-query__kpi-row">
        <screen-panel class="escalation-query__kpi" title="项目总数" flat>
          <screen-stat-value
            :value="total"
            unit="个"
            size="md"
            hint="当前查询条件命中的项目"
          />
        </screen-panel>

        <screen-panel
          v-for="card in statusCards"
          :key="card.name"
          class="escalation-query__kpi"
          flat
          :title="card.name"
        >
          <screen-stat-value :value="card.count" unit="个" size="md" />
          <button type="button" class="escalation-query__kpi-link" @click="drillStatus(card.name)">
            查看明细
          </button>
        </screen-panel>
      </div>

      <!-- ============ 5 张统计图 ============ -->
      <div class="escalation-query__charts">
        <!-- 1. 按论证结果分布 -->
        <screen-panel title="按论证结果分布" sub-title="点图例可下钻台账">
          <screen-donut
            v-if="byResultItems.length"
            :data="byResultItems"
            :colors="VIZ_COLORS"
            :size="150"
            :center-value="byResultTotal"
            center-label="项目数"
            unit="个"
          />
          <screen-empty v-else size="sm" text="暂无论证结果数据" />

          <div v-if="byResultItems.length" class="escalation-query__drills">
            <button
              v-for="item in byResultItems"
              :key="`result-${item.name}`"
              type="button"
              class="escalation-query__drill"
              @click="drillResult(item.name)"
            >
              {{ item.name }}（{{ item.value }}）
            </button>
          </div>
        </screen-panel>

        <!-- 2. 按申报单位排行 -->
        <screen-panel title="按申报单位排行" sub-title="前 10 名，点条目下钻台账">
          <screen-rank-list
            :items="byDeptItems"
            unit="个"
            clickable
            empty-text="暂无申报单位数据"
            @item-click="drillDept"
          />
        </screen-panel>

        <!-- 3. 按行政区划分布 -->
        <screen-panel title="按行政区划分布" sub-title="点条目下钻台账">
          <screen-bar-list
            :items="byXzqhItems"
            unit="个"
            clickable
            empty-text="暂无行政区划数据"
            @item-click="drillXzqh"
          />
        </screen-panel>

        <!-- 4. 按办理状态分布 -->
        <screen-panel title="按办理状态分布" sub-title="点图例可下钻台账">
          <screen-donut
            v-if="byStatusItems.length"
            :data="byStatusItems"
            :colors="VIZ_COLORS"
            :size="150"
            :center-value="byStatusTotal"
            center-label="项目数"
            unit="个"
          />
          <screen-empty v-else size="sm" text="暂无办理状态数据" />

          <div v-if="byStatusItems.length" class="escalation-query__drills">
            <button
              v-for="item in byStatusItems"
              :key="`status-${item.name}`"
              type="button"
              class="escalation-query__drill"
              @click="drillStatus(item.name)"
            >
              {{ item.name }}（{{ item.value }}）
            </button>
          </div>
        </screen-panel>

        <!-- 5. 按申报时间趋势 -->
        <screen-panel class="escalation-query__chart-wide" title="按申报时间趋势" sub-title="单位：个，按月统计">
          <screen-line-chart
            :data="byMonthItems"
            label="按申报时间趋势"
            :height="200"
            unit="个"
            tone="accent"
            empty-text="暂无申报时间数据"
          />

          <div v-if="byMonthItems.length" class="escalation-query__drills">
            <button
              v-for="item in byMonthItems"
              :key="`month-${item.name}`"
              type="button"
              class="escalation-query__drill"
              @click="drillMonth(item.name)"
            >
              {{ item.name }}（{{ item.value }}）
            </button>
          </div>
        </screen-panel>

        <!-- 补充信息：按项目类型分布（不在方案的 5 张图里，放在最后作为辅助） -->
        <screen-panel title="按项目类型分布" sub-title="补充信息">
          <screen-bar-list
            :items="byTypeItems"
            unit="个"
            clickable
            empty-text="暂无项目类型数据"
            @item-click="drillType"
          />
        </screen-panel>
      </div>

      <!-- ============ 结果列表 ============ -->
      <screen-panel class="escalation-query__list" title="查询结果">
        <template #extra>
          <span class="escalation-query__total">共命中 <b>{{ total }}</b> 个项目</span>
          <screen-button size="sm" icon="download" :disabled="!total" @click="handleExport">
            导出查询结果 Excel
          </screen-button>
          <screen-button size="sm" icon="folder-open" @click="$emit('open-ledger')">
            资料及台账管理
          </screen-button>
          <screen-button size="sm" icon="reload" :loading="loading" @click="loadData">刷新</screen-button>
        </template>

        <escalation-table
          :data-source="dataSource"
          :loading="loading"
          readonly
          @detail="handleDetail"
        />

        <template #footer>
          <screen-pagination
            :current="pagination.current"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            :page-size-options="pagination.pageSizeOptions"
            @change="handlePageChange"
          />
        </template>
      </screen-panel>
    </div>

    <escalation-detail-modal ref="detailModal" />
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenPagination,
  ScreenStatValue,
  ScreenDonut,
  ScreenRankList,
  ScreenBarList,
  ScreenLineChart,
  ScreenEmpty,
  ScreenLoading,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import EscalationSearchForm from './EscalationSearchForm.vue'
import EscalationTable from './EscalationTable.vue'
import EscalationDetailModal from './EscalationDetailModal.vue'
import {
  queryProjectList,
  queryLedgerCountByStatus,
  queryStatByResult,
  queryStatByDept,
  queryStatByXzqh,
  queryStatByMonth,
  queryStatByType,
  exportProjectXls,
} from '@/api/land/escalation'
import {
  STATUS_OPTIONS,
  toNameCountList,
  normalizeStatusCounts,
  sumCounts,
  withPercent,
  defaultPagination,
} from '../constants'

/**
 * 图表配色：只用 --screen-* 令牌（README §1「禁止写死颜色」）。
 * 环形图有 5 个分类，正好对应库里 5 个可视化令牌。
 */
const VIZ_COLORS = [
  'var(--screen-viz-mint)',
  'var(--screen-viz-blue)',
  'var(--screen-viz-cyan)',
  'var(--screen-viz-green)',
  'var(--screen-viz-amber)',
]

/** 申报单位排行取前 N 名，避免长尾把图撑长 */
const DEPT_LIMIT = 10

/** 'yyyy-MM' → ['yyyy-MM-01', 'yyyy-MM-末日']（月份下钻用） */
function monthRange (text) {
  const [year, month] = String(text || '').split('-')
  if (!year || !month) return null
  const lastDay = new Date(Number(year), Number(month), 0).getDate()
  return [`${year}-${month}-01`, `${year}-${month}-${String(lastDay).padStart(2, '0')}`]
}

export default {
  name: 'EscalationQuery',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenPagination,
    ScreenStatValue,
    ScreenDonut,
    ScreenRankList,
    ScreenBarList,
    ScreenLineChart,
    ScreenEmpty,
    ScreenLoading,
    EscalationSearchForm,
    EscalationTable,
    EscalationDetailModal,
  },
  data () {
    return {
      VIZ_COLORS,
      loading: false,
      chartLoading: false,
      query: {},
      dataSource: [],
      total: 0,
      stat: {
        byResult: [],
        byDept: [],
        byXzqh: [],
        byMonth: [],
        byType: [],
        byStatus: [],
      },
      statusCounts: {},
      pagination: defaultPagination(10),
    }
  },
  computed: {
    /** 4 张状态卡（数量与列表同条件；配色由 ScreenTag 的合法 tone 表达） */
    statusCards () {
      return STATUS_OPTIONS.map((name) => ({
        name,
        count: Number(this.statusCounts[name]) || 0,
      }))
    },
    byResultItems () {
      return toNameCountList(this.stat.byResult).map((item) => ({ name: item.name, value: item.count }))
    },
    byResultTotal () {
      return sumCounts(toNameCountList(this.stat.byResult))
    },
    byStatusItems () {
      return toNameCountList(this.stat.byStatus).map((item) => ({ name: item.name, value: item.count }))
    },
    byStatusTotal () {
      return sumCounts(toNameCountList(this.stat.byStatus))
    },
    byDeptItems () {
      return toNameCountList(this.stat.byDept)
        .sort((a, b) => b.count - a.count)
        .slice(0, DEPT_LIMIT)
        .map((item) => ({ name: item.name, value: item.count }))
    },
    byXzqhItems () {
      return withPercent(toNameCountList(this.stat.byXzqh)).map((item) => ({
        name: item.name,
        value: item.count,
        percent: item.percent,
      }))
    },
    byTypeItems () {
      return withPercent(toNameCountList(this.stat.byType)).map((item) => ({
        name: item.name,
        value: item.count,
        percent: item.percent,
      }))
    },
    /** 折线图要求按时间正序，后端不保证顺序，这里排一次 */
    byMonthItems () {
      return toNameCountList(this.stat.byMonth)
        .sort((a, b) => String(a.name).localeCompare(String(b.name)))
        .map((item) => ({ name: item.name, value: item.count }))
    },
  },
  created () {
    const routeQuery = (this.$route && this.$route.query) || {}
    // 深链 ?argResult=通过 之类的初始条件直接落到表单上
    this.query = Object.assign({}, routeQuery)
    this.loadData()
    this.loadStat()
  },
  methods: {
    /* ---------------- 数据 ---------------- */

    loadData () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize,
      })

      return queryProjectList(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '项目列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })
        })
        .finally(() => {
          this.loading = false
        })
    },

    /**
     * 5 张图 + 状态计数，与列表共用同一套查询条件。
     * 逐个 catch 成空数组：某一张图取不到数据不应该让整页统计全空。
     */
    loadStat () {
      const params = Object.assign({}, this.query)
      this.chartLoading = true
      const unwrap = (task) => task.then((res) => (res && res.success ? res.result || [] : [])).catch(() => [])

      return Promise.all([
        unwrap(queryStatByResult(params)),
        unwrap(queryStatByDept(params)),
        unwrap(queryStatByXzqh(params)),
        unwrap(queryStatByMonth(params)),
        unwrap(queryStatByType(params)),
        unwrap(queryLedgerCountByStatus(params)),
      ])
        .then(([byResult, byDept, byXzqh, byMonth, byType, byStatus]) => {
          this.stat = { byResult, byDept, byXzqh, byMonth, byType, byStatus }
          this.statusCounts = normalizeStatusCounts(byStatus)
        })
        .finally(() => {
          this.chartLoading = false
        })
    },

    handleSearch (query) {
      this.query = query || {}
      this.pagination.current = 1
      this.loadData()
      this.loadStat()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    handleDetail (record) {
      this.$refs.detailModal.open(record)
    },

    handleExport () {
      exportProjectXls(this.query)
      toast.info('已开始导出，请稍候…')
    },

    /* ---------------- 下钻 ---------------- */

    /** 统一出口：空的条件下钻没有意义（那和直接切页签一样），所以过滤掉空值再抛 */
    drill (query) {
      const clean = {}
      Object.keys(query || {}).forEach((key) => {
        const value = query[key]
        if (value === undefined || value === null || value === '') return
        clean[key] = value
      })
      if (!Object.keys(clean).length) return
      this.$emit('drill', clean)
    },

    drillResult (name) {
      this.drill({ argResult: name })
    },

    drillStatus (name) {
      this.drill({ status: name })
    },

    drillDept (item) {
      this.drill({ declareDept: item && item.name })
    },

    drillXzqh (item) {
      this.drill({ xzqh: item && item.name })
    },

    drillType (item) {
      this.drill({ projectType: item && item.name })
    },

    /** 月份下钻转成申报时间区间：台账的查询条件里没有「月份」这一项 */
    drillMonth (name) {
      const range = monthRange(name)
      if (!range) return
      this.drill({ beginDeclareDate: range[0], endDeclareDate: range[1] })
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.escalation-query {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__search {
    flex: 0 0 auto;
  }

  /* 统计区整体可滚：图表 + 列表在一屏里放不下，交给模块内部滚动 */
  &__scroll {
    flex: 1 1 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    overflow-y: auto;
    padding-right: 2px;
    .screen-scrollbar();
  }

  &__kpi-row {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: var(--screen-space-3);
  }

  &__kpi {
    padding: var(--screen-space-2) var(--screen-space-3) var(--screen-space-3);
    min-width: 0;
  }

  &__kpi-link {
    .screen-link-action();
    margin-top: var(--screen-space-2);
  }

  &__charts {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-3);
  }

  /* 趋势图占整行：折线的 x 轴月份多了以后，半栏宽度会挤成一根线 */
  &__chart-wide {
    grid-column-start: 1;
    grid-column-end: -1;
  }

  &__drills {
    display: flex;
    flex-wrap: wrap;
    gap: var(--screen-space-2);
    margin-top: var(--screen-space-3);
    padding-top: var(--screen-space-2);
    border-top: 1px solid var(--screen-border-soft);
  }

  /* 图例按钮：ScreenDonut / ScreenLineChart 没有点击事件，用这几个真实按钮接管下钻 */
  &__drill {
    padding: 1px 8px;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    line-height: 18px;
    color: var(--screen-accent-soft);
    background: var(--screen-row-alt);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-pill);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
      border-color: var(--screen-border-strong);
    }
  }

  &__list {
    flex: 0 0 auto;
  }

  &__total {
    margin-right: var(--screen-space-3);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    white-space: nowrap;

    b {
      margin: 0 2px;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-sm);
      color: var(--screen-accent);
    }
  }
}

.escalation-query__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 1500px) {
  .escalation-query__kpi-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1200px) {
  .escalation-query__charts {
    grid-template-columns: minmax(0, 1fr);
  }

  .escalation-query__chart-wide {
    grid-column-start: auto;
    grid-column-end: auto;
  }

  .escalation-query__kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
