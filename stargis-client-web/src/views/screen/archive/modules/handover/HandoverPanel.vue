<template>
  <!--
    HandoverPanel 道路交付及养护协议移交事项（方案 2.3.2 第 6 项）
    --------------------------------
    这是本模块的**入口组件**，由 `views/screen/archive/index.vue` 挂成一个二级页签
    （页签接线由父级负责，本组件不改那个共享文件）。

    版式：上方检索条件；下方左右分栏 ——
      左：统计概览（6 张指标卡 + 按状态 / 按接收管养单位 Top10 条形列表）
      右：移交事项列表（表 + 分页）

    对齐 admin-client 既有实现的功能：检索 / 指标卡 / 列表 / 状态变更 / 新增编辑 /
    详情与关联档案 / 导出 Excel。**不做打印**（大屏既有模块都没有打印）。

    父级接线契约：
      props:   initialQuery (Object)  外部带进来的初始条件
      方法:    applyDrill(params)     外部下钻（面板被 keep-alive 缓存时靠它，而不是改 prop）

    与「道路验收及移交资料台账」（第 7 项）的分工：台账是 1340 条道路的**普查**
    （一条一行，管 13 类资料齐不齐）；本页只登记**真正发生 / 在办的移交事项**
    （管协议签了没、养护期从哪天到哪天、谁接收管养）。没移交的道路不必在这里建记录。
  -->
  <div class="handover-panel">
    <!-- ================= 检索条件 ================= -->
    <screen-panel class="handover-panel__search" title="检索条件">
      <handover-search-form
        ref="search"
        :initial-query="initialQuery"
        @search="handleSearch"
      />
    </screen-panel>

    <div class="handover-panel__main">
      <!-- ================= 统计概览 ================= -->
      <screen-panel
        class="handover-panel__stats"
        title="统计概览"
        sub-title="与右侧列表同一套筛选条件"
        scrollable
      >
        <template #extra>
          <screen-button size="sm" icon="reload" :loading="statLoading" @click="refresh">刷新</screen-button>
        </template>

        <div class="handover-panel__cards">
          <div v-for="card in statCards" :key="card.key" class="handover-panel__card">
            <screen-stat-value
              size="md"
              align="left"
              :value="card.value"
              :unit="card.unit"
              :label="card.label"
              :hint="card.hint"
              :tone="card.tone"
            />
          </div>
        </div>

        <section class="handover-panel__chart">
          <h5 class="handover-panel__chart-title">
            按状态
            <span class="handover-panel__chart-kicker">点击某一行可把该状态筛进列表</span>
          </h5>
          <screen-bar-list
            :items="statusBars"
            unit="条"
            clickable
            @item-click="handleStatusDrill"
          />
        </section>

        <section class="handover-panel__chart">
          <h5 class="handover-panel__chart-title">
            接收管养单位 Top10
            <span class="handover-panel__chart-kicker">谁接管得最多</span>
          </h5>
          <screen-bar-list :items="receiveUnitBars" unit="条" />
        </section>
      </screen-panel>

      <!-- ================= 列表 ================= -->
      <screen-panel class="handover-panel__list" title="移交事项">
        <template #extra>
          <span class="handover-panel__total">共命中 <b>{{ total }}</b> 条</span>

          <screen-button type="primary" size="sm" icon="plus" @click="handleAdd">
            新增移交事项
          </screen-button>

          <screen-button size="sm" icon="download" :disabled="!total" @click="handleExport">
            导出 Excel
          </screen-button>

          <screen-button size="sm" icon="reload" :loading="loading" @click="refresh">刷新</screen-button>
        </template>

        <handover-table
          :data-source="dataSource"
          :loading="loading"
          :empty-text="emptyText"
          @detail="handleDetail"
          @edit="handleEdit"
          @delete="handleDelete"
          @status-change="handleStatusChange"
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

    <!-- ================= 弹窗 ================= -->
    <handover-form-modal ref="formModal" @ok="handleSaved" />
    <handover-detail-modal
      ref="detailModal"
      @edit="handleEdit"
      @status-change="handleStatusChanged"
    />
  </div>
</template>

<script>
import { ScreenPanel, ScreenButton, ScreenPagination, ScreenBarList, ScreenStatValue } from '@/components/screen'
import { toast } from '@/components/screen/toast'
import HandoverSearchForm from './HandoverSearchForm.vue'
import HandoverTable from './HandoverTable.vue'
import HandoverFormModal from './HandoverFormModal.vue'
import HandoverDetailModal from './HandoverDetailModal.vue'
import {
  changeHandoverStatus,
  deleteHandover,
  exportHandoverXls,
  queryHandoverCountByStatus,
  queryHandoverList,
  queryHandoverStat
} from '@/api/land/handover'
import { compactQuery, defaultPagination } from '../../constants'
import { HANDOVER_STATUSES } from './constants'

/** 统计口径的初始值（与后端 HandoverStatVO 的字段一一对应） */
function buildEmptyStat () {
  return {
    total: 0,
    migratedCount: 0,
    manualCount: 0,
    archivedCount: 0,
    missingAgreementCount: 0,
    maintenanceExpiringCount: 0,
    maintenanceExpiredCount: 0,
    orphanCount: 0,
    byStatus: [],
    byType: [],
    byXzqh: [],
    byYear: [],
    byReceiveUnit: [],
    byDldj: []
  }
}

export default {
  name: 'HandoverPanel',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenPagination,
    ScreenBarList,
    ScreenStatValue,
    HandoverSearchForm,
    HandoverTable,
    HandoverFormModal,
    HandoverDetailModal
  },
  props: {
    /** 外部（路由 query / 其它页签下钻）带进来的初始条件 */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      loading: false,
      statLoading: false,
      /** 当前生效的查询条件（列表 / 统计 / 导出共用同一份） */
      query: {},
      dataSource: [],
      total: 0,
      pagination: defaultPagination(20),
      stat: buildEmptyStat(),
      /** 各状态计数（后端固定返回 3 项） */
      statusCounts: {}
    }
  },
  computed: {
    statCards () {
      const stat = this.stat
      const statusCounts = this.statusCounts
      return [
        {
          key: 'total',
          label: '移交事项总数',
          value: stat.total || 0,
          unit: '条',
          hint: '登记在册的移交事项',
          tone: 'accent'
        },
        {
          key: 'source',
          label: '迁移 / 人工',
          value: `${stat.migratedCount || 0} / ${stat.manualCount || 0}`,
          unit: '',
          hint: '由旧库迁移 / 页面新增',
          tone: 'accent'
        },
        {
          key: 'done',
          label: '已移交',
          value: statusCounts['已移交'] || 0,
          unit: '条',
          hint: `移交中 ${statusCounts['移交中'] || 0} 条`,
          tone: 'success'
        },
        {
          key: 'agreement',
          label: '协议信息待补录',
          value: stat.missingAgreementCount || 0,
          unit: '条',
          hint: '协议编号与签订日期都为空',
          tone: stat.missingAgreementCount ? 'warning' : 'accent'
        },
        {
          key: 'archived',
          label: '已关联档案',
          value: stat.archivedCount || 0,
          unit: '条',
          hint: '协议扫描件 / 移交单',
          tone: 'accent'
        },
        {
          key: 'maintenance',
          label: '养护将到期 / 已过期',
          value: `${stat.maintenanceExpiringCount || 0} / ${stat.maintenanceExpiredCount || 0}`,
          unit: '',
          hint: '90 天内到期 / 已过期',
          tone: stat.maintenanceExpiredCount ? 'warning' : 'accent'
        }
      ]
    },

    /** 按状态（后端固定 3 项，数量为 0 也显示，便于看出「一条都还没移交」） */
    statusBars () {
      const counts = {}
      ;(this.stat.byStatus || []).forEach((item) => {
        if (item && item.name) counts[item.name] = item.count
      })
      const tones = { 待移交: 'muted', 移交中: 'warning', 已移交: 'cyan' }
      return HANDOVER_STATUSES.map((status) => ({
        key: status,
        name: status,
        value: counts[status] || 0,
        tone: tones[status] || 'muted'
      }))
    },

    /** 接收管养单位 Top10（后端已按数量降序取前 10） */
    receiveUnitBars () {
      return (this.stat.byReceiveUnit || []).map((item) => ({
        key: item.name,
        name: item.name,
        value: item.count
      }))
    },

    emptyText () {
      const hasFilter = Object.keys(this.query).length > 0
      return hasFilter
        ? '没有符合条件的移交事项，试试放宽检索条件'
        : '还没有登记任何移交事项，点右上角「新增移交事项」开始'
    }
  },
  created () {
    this.query = this.normalizeQuery(this.initialQuery)
    this.refresh()
  },
  methods: {
    /* ---------------- 取数 ---------------- */

    refresh () {
      this.loadData()
      this.loadStatusCounts()
      this.loadStat()
    },

    loadData () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize
      })

      return queryHandoverList(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '移交事项列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })
          // 删除/筛选后当前页可能已越界，回退一页重取（否则会看到空列表却总数为正）
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination = Object.assign({}, this.pagination, { current: this.pagination.current - 1 })
            return this.loadData()
          }
        })
        .catch(() => {
          // 请求层已提示
        })
        .finally(() => {
          this.loading = false
        })
    },

    /** 状态计数：后端会忽略 status 条件，否则切到某个状态后其它状态的角标会全变 0 */
    loadStatusCounts () {
      const params = Object.assign({}, this.query)
      delete params.status
      return queryHandoverCountByStatus(params)
        .then((res) => {
          if (!res || !res.success) return
          const counts = {}
          ;(res.result || []).forEach((item) => {
            if (item && item.status) counts[item.status] = item.count
          })
          this.statusCounts = counts
        })
        .catch(() => {
          this.statusCounts = {}
        })
    },

    loadStat () {
      this.statLoading = true
      return queryHandoverStat(this.query)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '统计加载失败')
            return
          }
          this.stat = Object.assign(buildEmptyStat(), res.result || {})
        })
        .catch(() => {
          // 请求层已提示
        })
        .finally(() => {
          this.statLoading = false
        })
    },

    /* ---------------- 交互 ---------------- */

    handleSearch (query) {
      this.query = this.normalizeQuery(query)
      this.pagination = Object.assign({}, this.pagination, { current: 1 })
      this.refresh()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    /** 点「按状态」条形列表：把该状态写回检索表单，用户能看到列表为什么只有这几条 */
    handleStatusDrill (item) {
      const status = item && item.name
      if (!status) return
      this.applyDrill({ status })
    },

    /* ---------------- 列表动作 ---------------- */

    handleAdd () {
      this.$refs.formModal.showAdd()
    },

    handleEdit (record) {
      this.$refs.formModal.showEdit(record)
    },

    handleDetail (record) {
      this.$refs.detailModal.open(record)
    },

    /** 保存成功：列表与统计都要刷新（新增会改变总数与迁移/人工口径） */
    handleSaved () {
      this.refresh()
    },

    handleDelete (record) {
      deleteHandover(record.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '删除失败')
            return
          }
          toast.success('删除成功')
          this.refresh()
        })
        .catch(() => {
          // 请求层已提示
        })
    },

    /** 列表「状态」列里直接改 */
    handleStatusChange (row, status) {
      changeHandoverStatus(row.id, status)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '状态变更失败')
            // 失败要重取，把下拉显示的值退回库里的真实值
            this.loadData()
            return
          }
          toast.success(`状态已变更为「${status}」`)
          this.refresh()
        })
        .catch(() => {
          this.loadData()
        })
    },

    /** 详情弹窗里改的状态：只刷新列表与计数（详情弹窗自己已经更新过） */
    handleStatusChanged () {
      this.loadData()
      this.loadStatusCounts()
      this.loadStat()
    },

    handleExport () {
      if (!this.total) return
      exportHandoverXls(this.query)
      toast.info('已开始导出，请稍候…')
    },

    /* ---------------- 对外接口（父级接线用） ---------------- */

    /**
     * 外部下钻：用一组新条件覆盖当前筛选条件并回到第一页。
     * 同时写回检索表单 —— 否则用户会面对一个「被悄悄过滤了却看不出原因」的列表。
     * @param {{status?: string, facilityId?: string, facilityLabel?: string,
     *          crzdbh?: string, handoverType?: string, xzqh?: string}} params
     */
    applyDrill (params) {
      const next = Object.assign({}, params || {})
      this.query = this.normalizeQuery(next)
      this.pagination = Object.assign({}, this.pagination, { current: 1 })
      if (this.$refs.search && typeof this.$refs.search.setQuery === 'function') {
        this.$refs.search.setQuery(next)
      }
      this.refresh()
    },

    /**
     * 条件归一化：只留后端认得的字段。
     * `facilityLabel` 是给下拉回显用的展示名（下钻时带进来的），发给后端无意义。
     * hasArchive 保真为布尔值，ScreenSelect 的 'true'/'false' 在检索表单里已经转过。
     */
    normalizeQuery (query) {
      const source = Object.assign({}, query || {})
      delete source.facilityLabel
      return compactQuery(source)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.handover-panel {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__search {
    flex: 0 0 auto;
  }

  &__main {
    display: flex;
    gap: var(--screen-space-3);
    // flex-basis 必须是 0：用 auto 会按内容撑开，导致列表超出模块高度、分页被挤出可见区
    flex: 1 1 0;
    min-height: 0;
  }

  &__stats {
    flex: 0 0 420px;
    min-width: 0;
  }

  &__list {
    flex: 1 1 0;
    min-width: 0;
  }

  /* ---------------- 指标卡 ---------------- */

  &__cards {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-2);
    flex: 0 0 auto;
  }

  &__card {
    padding: var(--screen-space-2) var(--screen-space-3);
    background: var(--screen-row-alt);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
    min-width: 0;
  }

  /* ---------------- 条形列表 ---------------- */

  &__chart {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    margin-top: var(--screen-space-2);
    flex: 0 0 auto;
  }

  &__chart-title {
    display: flex;
    align-items: baseline;
    flex-wrap: wrap;
    gap: 8px;
    margin: 0;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    color: var(--screen-text);
  }

  &__chart-kicker {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  /* ---------------- 列表页头 ---------------- */

  &__total {
    margin-right: var(--screen-space-2);
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

// 页头按钮多，允许换行；否则窄屏时「导出 / 刷新」会被挤掉
.handover-panel__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 1600px) {
  .handover-panel__stats {
    flex-basis: 360px;
  }
}
</style>
