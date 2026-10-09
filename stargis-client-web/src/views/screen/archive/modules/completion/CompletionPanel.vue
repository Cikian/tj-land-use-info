<template>
  <!--
    CompletionPanel 竣工验收项目历史工程资料数字化档案（方案 2.3.2 第 8 项）
    ---------------------------------------------------------------
    面板入口组件，三段式版式：
      1. 检索条件 —— 4 个常驻 + 10 个折叠（CompletionSearchForm）
      2. 数字化概览 —— 6 张指标卡 + 3 张图（按状态环形图 / 按项目类型与保管期限条形图）
      3. 历史档案列表 —— 列表 + 分页 + 新增 / 编辑 / 详情 / 关联扫描件 / 删除 / 导出

    数据来自 Java 业务后端（api/land/completion.js）：
      GET  /list             列表（与统计、导出共用同一套条件）
      GET  /stat             汇总与 6 组分类统计（页数、文件数、投资额、按类型/期限/区划…）
      GET  /countByStatus    3 个数字化状态计数（固定 3 项，数量为 0 也返回）
      POST /add /edit /status、DELETE /delete、GET /exportXls …

    外部契约（页签壳按这个接线）：
      props   initialQuery  外部带进来的初始条件
      method  applyDrill(params)  外部下钻：覆盖条件并重新取数（keep-alive 缓存下也能生效）

    ★ 需求原文只有一句「实现竣工验收项目历史工程资料的数字化档案管理，可快速导出档案信息」，
      因此这里的取舍是：把「档案信息」做全（35 列全部可录入/可查/可导出），
      把「导出」做成一等入口（列表右上角常驻），并把「数字化进度」做成页面的第一视觉
      （状态 KPI + 环形图 + 列表标签），因为它是这份档案在业务上唯一的动态属性。
  -->
  <div class="completion-panel">
    <!-- ================= 1. 检索条件 ================= -->
    <screen-panel class="completion-panel__search" title="检索条件">
      <completion-search-form
        ref="search"
        :initial-query="initialQuery"
        @search="handleSearch"
      />
    </screen-panel>

    <!-- ================= 2. 数字化概览 ================= -->
    <screen-panel
      class="completion-panel__overview"
      title="数字化概览"
      sub-title="点击「已数字化 / 数字化中 / 未数字化」可按状态筛选"
      collapsible
    >
      <template #extra>
        <screen-button size="sm" icon="reload" :loading="statLoading" @click="loadStat">刷新统计</screen-button>
      </template>

      <!-- 指标卡 -->
      <div class="completion-panel__kpis">
        <div class="completion-panel__kpi">
          <screen-stat-value
            :value="stat.total"
            unit="份"
            label="历史档案总数"
            size="md"
            :animated="false"
          />
          <span class="completion-panel__kpi-sub">
            投资额合计 {{ formatAmount(stat.investTotal) }} 万元
          </span>
        </div>

        <div
          v-for="card in statusCards"
          :key="card.status"
          class="completion-panel__kpi is-clickable"
          :class="{ 'is-active': query.digitizeStatus === card.status }"
          role="button"
          tabindex="0"
          @click="filterByStatus(card.status)"
          @keyup.enter="filterByStatus(card.status)"
        >
          <screen-stat-value
            :value="card.count"
            unit="份"
            :label="card.status"
            size="md"
            :tone="card.tone"
            :animated="false"
          />
          <span class="completion-panel__kpi-sub">{{ card.hint }}</span>
        </div>

        <div class="completion-panel__kpi">
          <screen-stat-value
            :value="stat.pageTotal"
            unit="页"
            label="扫描总页数"
            size="md"
            :animated="false"
          />
          <span class="completion-panel__kpi-sub">
            文件夹数 {{ formatCount(stat.fileTotal) }} 个
          </span>
        </div>

        <div class="completion-panel__kpi">
          <screen-stat-value
            :value="stat.archivedCount"
            unit="份"
            label="已挂扫描件"
            size="md"
            :animated="false"
          />
          <span class="completion-panel__kpi-sub">挂接率 {{ archivedRate }}%</span>
        </div>

        <!--
          ★ 平均扫描 DPI：后端 CompletionStatVO 目前没有这个口径（它给的是 scanDpi 精确筛选，
          没有 AVG）；这里做成「有就显示」，后端补上 avgScanDpi 后本卡片自动出现，
          不需要再改前端。按「宁可留白也不串口径」的原则，不拿当前页的行去算平均数充数。
        -->
        <div v-if="avgScanDpi !== null" class="completion-panel__kpi">
          <screen-stat-value
            :value="avgScanDpi"
            unit="DPI"
            label="平均扫描分辨率"
            size="md"
            :precision="1"
            :animated="false"
          />
          <span class="completion-panel__kpi-sub">按已登记分辨率的档案平均</span>
        </div>
      </div>

      <!-- 图表 -->
      <div class="completion-panel__charts">
        <div class="completion-panel__chart">
          <h5 class="completion-panel__chart-title">按数字化状态</h5>
          <screen-donut
            v-if="statusChart.length"
            :data="statusChart"
            :colors="statusColors"
            :size="chartSize"
            :thickness="13"
            unit="份"
            center-label="合计"
            :center-value="stat.total"
            legend-position="right"
          />
          <screen-empty v-else size="sm" text="暂无数字化状态数据" />
        </div>

        <div class="completion-panel__chart">
          <h5 class="completion-panel__chart-title">按项目类型</h5>
          <screen-bar-list
            :items="typeChart"
            unit="份"
            :show-percent="true"
            :sort-desc="true"
            empty-text="暂无项目类型数据"
          />
        </div>

        <div class="completion-panel__chart">
          <h5 class="completion-panel__chart-title">按保管期限</h5>
          <screen-bar-list
            :items="retentionChart"
            unit="份"
            :show-percent="true"
            :sort-desc="true"
            empty-text="暂无保管期限数据"
          />
        </div>
      </div>
    </screen-panel>

    <!-- ================= 3. 历史档案列表 ================= -->
    <screen-panel class="completion-panel__list" title="历史档案列表">
      <template #extra>
        <span class="completion-panel__total">共 <b>{{ total }}</b> 份档案</span>

        <screen-button type="primary" size="sm" icon="plus" @click="handleAdd">新增历史档案</screen-button>

        <screen-button
          size="sm"
          icon="download"
          :disabled="!total"
          @click="handleExport"
        >
          导出 Excel
        </screen-button>

        <screen-button size="sm" icon="reload" :loading="loading" @click="loadData">刷新</screen-button>
      </template>

      <completion-table
        :data-source="dataSource"
        :loading="loading"
        empty-text="没有符合条件的历史档案，试试放宽检索条件"
        @detail="handleDetail"
        @edit="handleEdit"
        @link="handleLinkFromRow"
        @remove="handleRemove"
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

    <!-- ================= 弹窗 ================= -->
    <completion-form-modal ref="formModal" @ok="handleSaved" />
    <completion-detail-modal
      ref="detailModal"
      @edit="handleEditFromDetail"
      @changed="handleChanged"
    />
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenStatValue,
  ScreenDonut,
  ScreenBarList,
  ScreenEmpty,
  ScreenPagination
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import CompletionSearchForm from './CompletionSearchForm.vue'
import CompletionTable from './CompletionTable.vue'
import CompletionFormModal from './CompletionFormModal.vue'
import CompletionDetailModal from './CompletionDetailModal.vue'
import {
  deleteCompletion,
  exportCompletionXls,
  queryCompletionCountByStatus,
  queryCompletionList,
  queryCompletionStat
} from '@/api/land/completion'
import { defaultPagination } from '../../constants'
import {
  DIGITIZE_DONUT_COLORS,
  DIGITIZE_STATUSES,
  formatAmount,
  formatCount,
  normalizeQuery
} from './constants'

/** 数字化状态 → 指标卡语气（与列表标签同一套语义：绿=完成，蓝=进行中，橙=待办） */
function statusTone (status) {
  if (status === '已数字化') return 'success'
  if (status === '数字化中') return 'accent'
  return 'warning'
}

/** 状态卡的副说明 */
const STATUS_HINTS = {
  已数字化: '已完成扫描加工并挂接扫描件',
  数字化中: '扫描或著录加工进行中',
  未数字化: '已建账，尚未开始扫描加工'
}

/** { name, count } → 图表需要的 { name, value }，并算好百分比 */
function toChartData (list) {
  const items = (list || []).map((item) => ({
    name: item.name || '未填写',
    value: Number(item.count) || 0
  }))
  const total = items.reduce((sum, item) => sum + item.value, 0)
  return items.map((item) => Object.assign({}, item, {
    percent: total ? Number(((item.value / total) * 100).toFixed(1)) : 0
  }))
}

export default {
  name: 'CompletionPanel',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenStatValue,
    ScreenDonut,
    ScreenBarList,
    ScreenEmpty,
    ScreenPagination,
    CompletionSearchForm,
    CompletionTable,
    CompletionFormModal,
    CompletionDetailModal
  },
  props: {
    /** 外部（其它页签 / 路由 query）带进来的初始条件 */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      loading: false,
      statLoading: false,
      query: {},
      dataSource: [],
      total: 0,
      pagination: defaultPagination(20),
      /** 三维固定状态计数（后端固定返回 3 项，缺的补 0） */
      statusCounts: { 未数字化: 0, 数字化中: 0, 已数字化: 0 },
      stat: this.buildEmptyStat(),
      viewportHeight: 1080
    }
  },
  computed: {
    /** 三张状态卡（顺序 = 业务推进顺序） */
    statusCards () {
      return DIGITIZE_STATUSES.map((status) => ({
        status,
        count: Number(this.statusCounts[status]) || 0,
        tone: statusTone(status),
        hint: STATUS_HINTS[status] || ''
      }))
    },
    /** 数字化状态环形图（用 countByStatus 的固定 3 项，保证 0 值也进图例） */
    statusChart () {
      return DIGITIZE_STATUSES.map((status) => ({
        name: status,
        value: Number(this.statusCounts[status]) || 0,
        unit: '份'
      }))
    },
    statusColors () {
      return DIGITIZE_DONUT_COLORS
    },
    typeChart () {
      return toChartData(this.stat.byType)
    },
    retentionChart () {
      return toChartData(this.stat.byRetention)
    },
    archivedRate () {
      if (!this.stat.total) return 0
      return Math.round(((this.stat.archivedCount || 0) / this.stat.total) * 100)
    },
    /** 平均扫描 DPI：后端补上该口径前恒为 null，卡片不显示 */
    avgScanDpi () {
      const value = Number(this.stat.avgScanDpi)
      return Number.isFinite(value) && value > 0 ? value : null
    },
    chartSize () {
      return this.viewportHeight < 800 ? 122 : 146
    }
  },
  created () {
    this.query = normalizeQuery(this.initialQuery)
    this.loadData()
    this.loadStat()
  },
  mounted () {
    this.syncViewport()
    window.addEventListener('resize', this.syncViewport)
  },
  beforeDestroy () {
    window.removeEventListener('resize', this.syncViewport)
  },
  methods: {
    formatAmount,
    formatCount,

    /**
     * 视口高度必须存进 data：窗口尺寸变化不会触发重渲染，
     * 矮屏压缩图表尺寸的逻辑就会失效。
     */
    syncViewport () {
      this.viewportHeight = window.innerHeight || 1080
    },

    buildEmptyStat () {
      return {
        total: 0,
        archivedCount: 0,
        digitizedCount: 0,
        pageTotal: 0,
        fileTotal: 0,
        investTotal: 0,
        byStatus: [],
        byXzqh: [],
        byType: [],
        byRetention: [],
        byYear: [],
        byCompleteYear: []
      }
    },

    /* ---------------- 取数 ---------------- */

    loadData () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize
      })
      return queryCompletionList(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '历史档案列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })
          // 删到当前页空了就回退一页，避免停在空白页
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadData()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    /** 统计与状态计数一起取：两者共用同一套条件，必须同时刷新才自洽 */
    loadStat () {
      this.statLoading = true
      return Promise.all([
        queryCompletionStat(this.query),
        queryCompletionCountByStatus(this.query)
      ])
        .then(([statRes, countRes]) => {
          if (statRes && statRes.success) {
            // 用空结构兜底：后端某些口径无数据时不会返回对应字段
            this.stat = Object.assign(this.buildEmptyStat(), statRes.result || {})
          } else {
            toast.error((statRes && statRes.message) || '历史档案统计加载失败')
          }

          if (countRes && countRes.success) {
            const counts = { 未数字化: 0, 数字化中: 0, 已数字化: 0 }
            ;(countRes.result || []).forEach((item) => {
              if (item && item.status) counts[item.status] = Number(item.count) || 0
            })
            this.statusCounts = counts
          }
        })
        .finally(() => {
          this.statLoading = false
        })
    },

    refresh () {
      this.loadData()
      this.loadStat()
    },

    /* ---------------- 检索与分页 ---------------- */

    handleSearch (query) {
      this.query = query || {}
      this.pagination.current = 1
      this.refresh()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    /** 指标卡点按状态筛选（再点一次取消） */
    filterByStatus (status) {
      const next = Object.assign({}, this.query)
      if (next.digitizeStatus === status) {
        delete next.digitizeStatus
      } else {
        next.digitizeStatus = status
      }
      this.applyQuery(next)
    },

    /** 把一组条件同时写回检索表单与状态，并重新取数 */
    applyQuery (query) {
      this.query = normalizeQuery(query)
      this.pagination.current = 1
      if (this.$refs.search && this.$refs.search.setQuery) {
        this.$refs.search.setQuery(this.query)
      }
      this.refresh()
    },

    /**
     * 外部下钻：覆盖条件并重新取数。
     * 两种到达路径都要覆盖：
     *   - 面板是首次创建 → initialQuery 在 created 里生效；
     *   - 面板已被 keep-alive 缓存 → created 不会再跑，必须调用本方法。
     * @param {{projectName?: string, digitizeStatus?: string, xzqh?: string}} params
     */
    applyDrill (params) {
      this.applyQuery(Object.assign({}, params || {}))
    },

    /* ---------------- 行操作 ---------------- */

    handleAdd () {
      this.$refs.formModal.showAdd()
    },

    handleEdit (row) {
      this.$refs.formModal.showEdit(row)
    },

    handleEditFromDetail (detail) {
      if (this.$refs.detailModal && this.$refs.detailModal.close) {
        this.$refs.detailModal.close()
      }
      this.$refs.formModal.showEdit(detail)
    },

    handleDetail (row) {
      this.$refs.detailModal.open(row)
    },

    /** 「关联档案」：先开详情，再自动展开挑档案区，省一次点击 */
    handleLinkFromRow (row) {
      this.$refs.detailModal.open(row)
      this.$nextTick(() => {
        const modal = this.$refs.detailModal
        if (modal && !modal.pickerVisible) {
          modal.togglePicker()
        }
      })
    },

    handleRemove (row) {
      deleteCompletion(row.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '删除失败')
            return
          }
          toast.success('删除成功')
          this.refresh()
        })
        .catch(() => {})
    },

    handleSaved () {
      this.refresh()
    },

    /** 详情里关联/取消关联成功后：列表的扫描件计数与统计都会变，两边都要刷 */
    handleChanged () {
      this.refresh()
    },

    /* ---------------- 导出 ---------------- */

    /** 按当前条件导出档案信息 Excel（需求原文「可快速导出档案信息」） */
    handleExport () {
      exportCompletionXls(this.query)
      toast.info('已开始导出，请稍候…')
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.completion-panel {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__search {
    flex: 0 0 auto;
  }

  &__overview {
    flex: 0 0 auto;
  }

  &__list {
    // flex-basis 必须是 0：用 auto 会按内容撑开，导致正文超出模块高度、分页被挤出可见区
    flex: 1 1 0;
    min-height: 240px;
  }

  // ---------- 指标卡 ----------
  &__kpis {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: var(--screen-space-2);
  }

  &__kpi {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
    padding: var(--screen-space-2) var(--screen-space-3);
    background: var(--screen-row-alt);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
    transition: border-color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);

    &.is-clickable {
      cursor: pointer;
      .screen-focus-ring();

      &:hover {
        border-color: var(--screen-border);
        background: var(--screen-elevate);
      }

      // 当前生效的状态筛选：用强调描边标出来，不靠颜色单独传达（卡片副说明里有文字）
      &.is-active {
        border-color: var(--screen-accent);
        box-shadow: inset 0 0 0 1px var(--screen-accent);
      }
    }
  }

  &__kpi-sub {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  // ---------- 图表 ----------
  &__charts {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1fr);
    gap: var(--screen-space-3);
    margin-top: var(--screen-space-3);
    min-height: 150px;
  }

  &__chart {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-1);
    min-width: 0;
  }

  &__chart-title {
    margin: 0;
    font-size: var(--screen-font-xs);
    font-weight: 600;
    color: var(--screen-text-sub);
  }

  // ---------- 列表 ----------
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

.completion-panel__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}

// 窄屏：KPI 逐级降列，图表从三列降到一列，避免每个块被压得失去可读性
@media (max-width: 1900px) {
  .completion-panel__kpis {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 1500px) {
  .completion-panel__charts {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }
}

@media (max-width: 1200px) {
  .completion-panel__kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .completion-panel__charts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
