<template>
  <!--
    LedgerPanel 道路设施验收及移交资料台账（方案 2.3.2 第 7 项）· 大屏页签面板
    ===============================================================
    这是本模块的**入口组件**，由 views/screen/archive/index.vue 的页签壳挂载。
    结构（自上而下）：

      1. 台账检索     —— LedgerSearchForm（默认折叠，可展开 17 个条件）
      2. 概况 + 资料  —— 左：4 张指标卡（可点击下钻）；右：13 类资料归集分布条形图
                         （点某一类 = 筛出「缺少该类资料」的台账，这是本模块最有价值的视图）
      3. 状态页签     —— 未验收 / 验收中 / 已验收 / 已移交（带数量角标）
      4. 台账列表     —— 列显示方式（精简列 / 资料矩阵）+ 新增 / 批量补录 / 导出 / 批量删除 / 刷新
      5. 新增编辑 / 详情 / 批量补录三个弹窗

    ★ 对外契约（页签壳按这个接线）：
      name: 'LedgerPanel'
      props: { initialQuery: Object }
      methods: applyDrill(params)  —— 外部下钻：写进检索条件并刷新

    为什么必须有 applyDrill：
      页签壳用 <keep-alive> 缓存面板，切换页签不会销毁组件，
      于是 props.initialQuery 变化**不会**重跑 created()，条件就「只在第一次生效」。
      两条路径都要覆盖：首次创建读 initialQuery，已缓存则调 applyDrill。

    ★ 页面内下钻统一走 applyDrill，而不是 $router.push 到同一路由：
      Vue Router 对「同一路由只换 query」不会重建组件，
      条件会丢；直接改状态 + 刷新，行为确定。
  -->
  <div class="ledger-panel">
    <!-- ================= 1. 检索条件 ================= -->
    <screen-panel class="ledger-panel__search" title="台账检索" collapsible>
      <ledger-search-form
        ref="search"
        :initial-query="initialQuery"
        :materials="materials"
        @search="handleSearch"
      />
    </screen-panel>

    <!-- ================= 2. 指标卡 + 资料归集分布 ================= -->
    <div class="ledger-panel__overview">
      <screen-panel class="ledger-panel__kpi" title="台账概况" sub-title="与检索条件联动">
        <div class="ledger-panel__kpi-grid">
          <div
            v-for="card in kpiCards"
            :key="card.key"
            class="ledger-panel__kpi-item"
            :class="{ 'is-clickable': !!card.drill }"
            :role="card.drill ? 'button' : undefined"
            :tabindex="card.drill ? 0 : undefined"
            @click="handleKpiClick(card)"
            @keydown.enter="handleKpiClick(card)"
            @keydown.space.prevent="handleKpiClick(card)"
          >
            <screen-stat-value
              :value="card.value"
              :unit="card.unit"
              :label="card.label"
              :tone="card.tone"
              size="md"
            />
            <span class="ledger-panel__kpi-sub">{{ card.hint }}</span>
          </div>
        </div>
      </screen-panel>

      <screen-panel class="ledger-panel__material" title="13 类资料归集情况">
        <template #extra>
          <span class="ledger-panel__hint">点某一类 = 筛出缺少该类资料的台账</span>
        </template>
        <screen-bar-list
          :items="materialBars"
          :max="materialMax"
          unit="条"
          clickable
          @item-click="handleMaterialClick"
        />
      </screen-panel>
    </div>

    <!-- ================= 3. 状态页签 + 列表 ================= -->
    <div class="ledger-panel__bar">
      <screen-tabs v-model="statusTab" :tabs="statusTabs" @change="handleTabChange" />
    </div>

    <screen-panel class="ledger-panel__list" title="台账列表">
      <template #extra>
        <span class="ledger-panel__total">
          共 <b>{{ total }}</b> 条台账
          <template v-if="currentFilterText">
            <span class="ledger-panel__filter">当前筛选：{{ currentFilterText }}</span>
          </template>
        </span>

        <screen-radio-group
          v-model="viewMode"
          :options="viewOptions"
          size="sm"
          aria-label="列显示方式"
          @change="handleViewChange"
        />

        <screen-button type="primary" size="sm" icon="plus" @click="handleAdd">新增台账</screen-button>

        <screen-button size="sm" icon="upload" @click="handleImport">批量补录</screen-button>

        <screen-button size="sm" icon="download" :disabled="!total" @click="handleExport">
          导出 Excel
        </screen-button>

        <screen-popconfirm
          title="删除选中的台账？"
          :description="selectedRowKeys.length ? `已选 ${selectedRowKeys.length} 条，删除后不可恢复` : '请先勾选要删除的台账'"
          width="280"
          :disabled="!selectedRowKeys.length"
          @confirm="handleBatchDelete"
        >
          <screen-button size="sm" icon="trash" :disabled="!selectedRowKeys.length">
            批量删除{{ selectedRowKeys.length ? '（' + selectedRowKeys.length + '）' : '' }}
          </screen-button>
        </screen-popconfirm>

        <screen-button size="sm" icon="reload" :loading="loading" @click="refresh">刷新</screen-button>
      </template>

      <ledger-table
        :data-source="dataSource"
        :loading="loading"
        :show-matrix="showMatrix"
        :materials="materials"
        selectable
        :selected-row-keys="selectedRowKeys"
        @detail="openDetail"
        @edit="handleEdit"
        @delete="handleDelete"
        @select-change="handleSelectChange"
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

    <!-- ================= 4. 弹窗 ================= -->
    <ledger-form-modal ref="formModal" :materials="materials" @ok="handleSaved" />
    <ledger-detail-modal
      ref="detailModal"
      :materials="materials"
      @edit="handleEditFromDetail"
      @open-archive="handleOpenArchive"
      @changed="loadData"
    />
    <ledger-import-modal ref="importModal" @ok="handleSaved" />
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenTabs,
  ScreenRadioGroup,
  ScreenBarList,
  ScreenStatValue,
  ScreenPagination,
  ScreenPopconfirm
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import LedgerSearchForm from './LedgerSearchForm.vue'
import LedgerTable from './LedgerTable.vue'
import LedgerFormModal from './LedgerFormModal.vue'
import LedgerDetailModal from './LedgerDetailModal.vue'
import LedgerImportModal from './LedgerImportModal.vue'
import {
  queryLedgerPage,
  queryLedgerStat,
  queryLedgerCountByStatus,
  queryLedgerMaterials,
  deleteLedger,
  deleteLedgerBatch,
  exportLedgerXls
} from '@/api/land/ledger'
import {
  LEDGER_MATERIALS_FALLBACK,
  LEDGER_STATUS,
  MATERIAL_MATRIX_KEY,
  MATERIAL_TOTAL,
  compactQuery,
  defaultPagination,
  normalizeMaterials,
  padSeq
} from './constants'

/** 列显示方式（大屏没有 switch 原子，用分段单选表达「开 / 关」两态） */
const VIEW_OPTIONS = [
  { value: 'simple', label: '精简列' },
  { value: 'matrix', label: '资料矩阵' }
]

export default {
  name: 'LedgerPanel',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenTabs,
    ScreenRadioGroup,
    ScreenBarList,
    ScreenStatValue,
    ScreenPagination,
    ScreenPopconfirm,
    LedgerSearchForm,
    LedgerTable,
    LedgerFormModal,
    LedgerDetailModal,
    LedgerImportModal
  },
  props: {
    /**
     * 外部带进来的初始条件（下钻 / 深链）。
     * 只在 data() 里读一次；组件被 keep-alive 缓存后改用 applyDrill。
     */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    const initial = compactQuery(this.initialQuery || {})
    const stored = this.readMatrixPreference()
    return {
      loading: false,
      query: initial,
      statusTab: initial.status || 'all',
      statusCounts: {},
      dataSource: [],
      total: 0,
      selectedRowKeys: [],
      pagination: Object.assign(defaultPagination(20), {
        pageSizeOptions: [20, 50, 100]
      }),
      viewMode: stored ? 'matrix' : 'simple',
      viewOptions: VIEW_OPTIONS,
      materials: LEDGER_MATERIALS_FALLBACK.slice(),
      stat: {
        total: 0,
        migratedCount: 0,
        manualCount: 0,
        orphanCount: 0,
        fullMaterialCount: 0,
        emptyMaterialCount: 0,
        byMaterial: []
      }
    }
  },
  computed: {
    /** 是否插入 13 列资料矩阵 */
    showMatrix () {
      return this.viewMode === 'matrix'
    },
    statusTabs () {
      const all = LEDGER_STATUS.reduce((sum, name) => sum + (Number(this.statusCounts[name]) || 0), 0)
      return [{ key: 'all', label: '全部', badge: all }].concat(
        LEDGER_STATUS.map((name) => ({
          key: name,
          label: name,
          badge: Number(this.statusCounts[name]) || 0
        }))
      )
    },
    /** 4 张指标卡（带下钻：点一下就把对应条件写进检索并刷新） */
    kpiCards () {
      return [
        {
          key: 'total',
          label: '台账总数',
          value: this.stat.total || 0,
          unit: '条',
          hint: `迁移 ${this.stat.migratedCount || 0} / 人工 ${this.stat.manualCount || 0}`,
          tone: 'accent',
          drill: {}
        },
        {
          key: 'handedOver',
          label: '已移交',
          value: Number(this.statusCounts['已移交']) || 0,
          unit: '条',
          hint: '状态为「已移交」的台账',
          tone: 'success',
          drill: { status: '已移交' }
        },
        {
          key: 'full',
          label: '资料齐全(13/13)',
          value: this.stat.fullMaterialCount || 0,
          unit: '条',
          hint: `一类未归集 ${this.stat.emptyMaterialCount || 0} 条`,
          tone: 'warning',
          drill: { materialCompleteness: 'full' }
        },
        {
          key: 'orphan',
          label: '宗地待核对',
          value: this.stat.orphanCount || 0,
          unit: '条',
          hint: '旧库迁移后宗地在系统里找不到',
          tone: 'warning',
          // 后端没有「宗地待核对」这个查询条件（它来自 remark 文本），
          // 所以这张卡只做展示，不提供下钻 —— 不给一个点了没反应的假入口。
          drill: null
        }
      ]
    },
    /** 13 类资料条形图：name 带序号、value = 已归集的台账条数 */
    materialBars () {
      const list = this.stat.byMaterial && this.stat.byMaterial.length
        ? this.stat.byMaterial
        : this.materials.map((item) => ({ name: item.label, count: 0 }))
      const totalCount = this.stat.total || 0
      return list.map((item, index) => {
        const def = this.findMaterialByLabel(item.name, index)
        const value = Number(item.count) || 0
        return {
          key: def ? def.key : `material-${index}`,
          name: `${padSeq(def ? def.seq : index + 1)} ${item.name}`,
          value,
          // 点击时用 key 去筛「缺少该类资料」的行
          materialKey: def ? def.key : '',
          hint: totalCount ? `未归集 ${Math.max(0, totalCount - value)} 条` : ''
        }
      })
    },
    /** 条形图的分母：当前条件下的台账总数（于是百分比 = 该类资料的覆盖率） */
    materialMax () {
      return Math.max(1, this.stat.total || 0)
    },
    /** 把当前生效的条件用中文说清楚，避免用户忘了自己筛过什么 */
    currentFilterText () {
      const query = this.query || {}
      const parts = []
      if (query.status) parts.push(`状态=${query.status}`)
      if (query.roadName) parts.push(`道路名称≈${query.roadName}`)
      if (query.ledgerNo) parts.push(`台账编号≈${query.ledgerNo}`)
      if (query.xzqh) parts.push(`行政区划=${query.xzqh}`)
      if (query.gnq) parts.push(`功能区=${query.gnq}`)
      if (query.acceptanceType) parts.push(`验收类型=${query.acceptanceType}`)
      if (query.acceptanceResult) parts.push(`验收结果=${query.acceptanceResult}`)
      if (query.ptsslb) parts.push(`设施类别=${query.ptsslb}`)
      if (query.crzdbh) parts.push(`宗地编号≈${query.crzdbh}`)
      if (query.dkmc) parts.push(`地块名称≈${query.dkmc}`)
      if (query.facilityId) parts.push('已按配套项目过滤')
      if (query.archiveId) parts.push('已按关联档案过滤')
      if (query.missingMaterial) parts.push(`缺少「${this.materialLabel(query.missingMaterial)}」`)
      if (query.beginMaterialCount !== undefined && query.endMaterialCount !== undefined) {
        if (Number(query.beginMaterialCount) >= MATERIAL_TOTAL) parts.push('资料齐全')
        else if (Number(query.beginMaterialCount) === 0 && Number(query.endMaterialCount) === 0) {
          parts.push('一类资料都未归集')
        }
      }
      if (query.beginAcceptanceDate || query.endAcceptanceDate) {
        parts.push(`验收日期 ${query.beginAcceptanceDate || ''} ~ ${query.endAcceptanceDate || ''}`)
      }
      if (query.beginHandoverDate || query.endHandoverDate) {
        parts.push(`移交日期 ${query.beginHandoverDate || ''} ~ ${query.endHandoverDate || ''}`)
      }
      if (query.keyword) parts.push(`关键词≈${query.keyword}`)
      return parts.join('，')
    }
  },
  created () {
    this.loadMaterials()
    this.refresh()
  },
  methods: {
    /* ---------------- 用户偏好（资料矩阵开关） ---------------- */

    /**
     * 读取「显示资料矩阵」偏好。
     * 大屏是长驻页面，用户打开矩阵后刷新不应被重置，所以存在 localStorage；
     * 读写都包 try/catch —— 隐私模式 / 禁用存储时 localStorage 会抛异常，
     * 不能因为一个列显示偏好把整个面板搞白屏。
     */
    readMatrixPreference () {
      try {
        return window.localStorage.getItem(MATERIAL_MATRIX_KEY) === '1'
      } catch (e) {
        return false
      }
    },

    saveMatrixPreference (enabled) {
      try {
        window.localStorage.setItem(MATERIAL_MATRIX_KEY, enabled ? '1' : '0')
      } catch (e) {
        // 存不了就算了，仅影响下次打开的默认值
      }
    },

    handleViewChange (value) {
      this.saveMatrixPreference(value === 'matrix')
    },

    /* ---------------- 数据加载 ---------------- */

    refresh () {
      this.loadData()
      this.loadCounts()
      this.loadStat()
    },

    loadData () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize
      })

      return queryLedgerPage(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '台账列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })
          this.selectedRowKeys = []

          // 当前页被筛空时自动回退一页，避免用户停在空白页以为筛选没生效
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadData()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    /**
     * 状态页签的数量：与列表同条件、但**不含 status**
     * （否则切一次页签，其余页签的数量就全变 0 了）。
     */
    loadCounts () {
      const params = Object.assign({}, this.query)
      delete params.status
      return queryLedgerCountByStatus(params)
        .then((res) => {
          if (!res || !res.success) return
          const counts = {}
          ;(res.result || []).forEach((item) => {
            if (item && item.status) counts[item.status] = Number(item.count) || 0
          })
          this.statusCounts = counts
        })
        .catch(() => {})
    },

    /** 指标卡 + 13 类资料分布（与列表同条件，所以「图里看到的 = 列表里的」） */
    loadStat () {
      return queryLedgerStat(this.query)
        .then((res) => {
          if (!res || !res.success) return
          const result = res.result || {}
          this.stat = {
            total: result.total || 0,
            migratedCount: result.migratedCount || 0,
            manualCount: result.manualCount || 0,
            orphanCount: result.orphanCount || 0,
            fullMaterialCount: result.fullMaterialCount || 0,
            emptyMaterialCount: result.emptyMaterialCount || 0,
            byMaterial: result.byMaterial || []
          }
        })
        .catch(() => {})
    },

    /** 13 类资料定义：接口拉不到时用本地兜底（页面仍能渲染，只是标签可能不是最新的） */
    loadMaterials () {
      return queryLedgerMaterials()
        .then((res) => {
          if (res && res.success) {
            this.materials = normalizeMaterials(res.result)
          }
        })
        .catch(() => {
          this.materials = LEDGER_MATERIALS_FALLBACK.slice()
        })
    },

    /* ---------------- 筛选 ---------------- */

    handleSearch (query) {
      this.query = compactQuery(query || {})
      // 表单里的状态优先同步到页签，避免两处条件互相矛盾
      this.statusTab = this.query.status || 'all'
      this.pagination.current = 1
      this.refresh()
    },

    handleTabChange (key) {
      this.statusTab = key
      const base = this.$refs.search ? this.$refs.search.getQuery() : {}
      if (key === 'all') {
        delete base.status
      } else {
        base.status = key
      }
      this.query = base
      this.pagination.current = 1
      this.refresh()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    handleSelectChange (keys) {
      this.selectedRowKeys = keys || []
    },

    /* ---------------- 下钻 ---------------- */

    handleKpiClick (card) {
      if (!card || !card.drill) return
      this.applyDrill(card.drill)
    },

    /** 点条形图 → 筛出「缺少该类资料」的台账 */
    handleMaterialClick (raw) {
      if (!raw || !raw.materialKey) return
      this.applyDrill({ missingMaterial: raw.materialKey })
    },

    /**
     * 外部下钻入口（页签壳 / 其它模块调用）。
     *
     * 同时做三件事，缺一个用户就会困惑：
     *   1. 条件写进检索表单（能看到「是按什么筛出来的」，而不是结果被悄悄过滤）；
     *   2. 写进本组件的 query（列表与统计都用它）；
     *   3. 重置到第 1 页并刷新（否则会停在旧页码上看到空列表）。
     *
     * @param {{status?:string, missingMaterial?:string, facilityId?:string,
     *          crzdbh?:string, archiveId?:string, materialCompleteness?:string,
     *          beginMaterialCount?:number, endMaterialCount?:number}} params
     */
    applyDrill (params) {
      const next = compactQuery(params || {})
      if (this.$refs.search && typeof this.$refs.search.setQuery === 'function') {
        this.$refs.search.setQuery(next)
      }
      // 表单可能把「资料齐全与否」翻译成资料数区间，这里以表单的结果为准
      this.query = this.$refs.search ? this.$refs.search.getQuery() : next
      this.statusTab = this.query.status || 'all'
      this.pagination.current = 1
      this.refresh()
    },

    /* ---------------- 增删改 ---------------- */

    handleAdd () {
      this.$refs.formModal.showAdd()
    },

    handleEdit (record) {
      this.$refs.formModal.showEdit(record)
    },

    handleEditFromDetail (detail) {
      this.$refs.detailModal.handleClose()
      this.$refs.formModal.showEdit(detail)
    },

    openDetail (record) {
      this.$refs.detailModal.open(record)
    },

    handleDelete (record) {
      deleteLedger(record.id)
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

    handleBatchDelete () {
      if (!this.selectedRowKeys.length) return
      deleteLedgerBatch(this.selectedRowKeys)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '批量删除失败')
            return
          }
          toast.success(`已删除 ${this.selectedRowKeys.length} 条台账`)
          this.refresh()
        })
        .catch(() => {})
    },

    /** 保存成功（新增 / 编辑 / 批量补录）后：列表、页签角标、指标卡都要重算 */
    handleSaved () {
      this.refresh()
    },

    /* ---------------- 导出与批量补录 ---------------- */

    handleExport () {
      exportLedgerXls(this.query)
      toast.info('已开始导出，请稍候…')
    },

    handleImport () {
      this.$refs.importModal.open()
    },

    /**
     * 台账 → 档案查询 的下钻（双向跳转的「台账 → 档案」方向）。
     *
     * 走页签壳**已有的 drill 通道**：views/screen/archive/index.vue 上挂着
     * `@drill="handleDrill"`，它会把条件写进 drillQuery、切到「档案查询」页签，
     * 并调用档案查询面板的 applyDrill —— 于是这里不需要改任何共享文件就能跳过去，
     * 而且用户能看到「是按什么筛出来的」，不是被悄悄过滤的列表。
     *
     * 条件口径：优先按档案号（精确定位到用户点的那一卷档案），
     * 没有档案号时退化为按配套项目名（看该项目的全部档案）。
     */
    handleOpenArchive (row) {
      if (!row) return
      if (row.archiveNo) {
        this.$emit('drill', { archiveNo: row.archiveNo })
      } else if (row.ptxmmc) {
        this.$emit('drill', { ptxmmc: row.ptxmmc })
      }
    },

    /* ---------------- 展示辅助 ---------------- */

    /** 按资料中文名反查定义（条形图接口只回 name + count） */
    findMaterialByLabel (label, index) {
      const byLabel = this.materials.filter((item) => item.label === label)[0]
      if (byLabel) return byLabel
      return this.materials[index] || null
    },

    materialLabel (key) {
      const hit = this.materials.filter((item) => item.key === key)[0]
      return hit ? hit.label : key
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.ledger-panel {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__search {
    flex: 0 0 auto;
  }

  /* ---------------- 指标卡 + 资料分布 ---------------- */
  &__overview {
    display: grid;
    grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
    gap: var(--screen-space-3);
    flex: 0 0 auto;
  }

  &__kpi-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-3);
  }

  &__kpi-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 6px 8px;
    border: 1px solid transparent;
    border-radius: var(--screen-radius-sm);

    &.is-clickable {
      cursor: pointer;
      transition: border-color var(--screen-duration) var(--screen-ease),
        background-color var(--screen-duration) var(--screen-ease);
      .screen-focus-ring();

      &:hover {
        border-color: var(--screen-border);
        background: var(--screen-elevate);
      }
    }
  }

  &__kpi-sub {
    font-size: var(--screen-font-xs);
    line-height: 1.5;
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  /* 13 类资料条形图排成两列：13 行排一列会把这屏高度吃光 */
  &__material {
    /deep/ .screen-bar-list {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--screen-space-1) var(--screen-space-3);
    }
  }

  &__hint {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  /* ---------------- 状态页签 ---------------- */
  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
    flex: 0 0 auto;
  }

  /* ---------------- 列表 ---------------- */
  &__list {
    // flex-basis 必须是 0：用 auto 会按内容撑开，导致正文超出高度、分页被挤出可见区
    flex: 1 1 0;
    min-height: 0;
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

  &__filter {
    margin-left: 10px;
    color: var(--screen-accent-soft);
  }
}

// 列表标题栏按钮多，允许换行右对齐（与档案查询同一处理）
.ledger-panel__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 1600px) {
  .ledger-panel__overview {
    grid-template-columns: minmax(0, 1fr);
  }

  .ledger-panel__material /deep/ .screen-bar-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .ledger-panel__kpi-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .ledger-panel__material /deep/ .screen-bar-list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
