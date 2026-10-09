<template>
  <!--
    EscalationLedger 资料及台账管理（方案 2.3.3 第 3 项：台账方式管理，快速查看各项目当前状态）
    --------------------------------
    结构：
      · 顶部状态页签（全部 / 未办理 / 办理中 / 已办结 / 已归档，均带数量角标）；
      · 表格视图：14 列高密度台账（EscalationTable mode="ledger"）；
      · 卡片视图：一屏能看更多项目当前状态，适合「扫一眼」；
      · 打印：window.print() + 独立打印区（屏幕上隐藏），单项与整页两种口径；
      · 办理状态与论证结果是两个独立维度，可二维筛选（表单里各有一个条件）。

    ★ 下钻入口：从「查询统计」的图表带条件切过来时走 applyDrill（见 index.vue 的双路径写法）；
      首次创建则读 initialQuery —— 两条路径都必须成立，否则条件会「只在第一次生效」。

    ★ 卡片视图与表格视图共用同一份 dataSource 与分页，所以切换视图不重新请求。

    打印区为什么用 visibility 而不是 display：
      display:none 会连带把布局宽度算没，打印出来的表格宽度会跳；
      visibility:hidden 保持布局，只把不需要打印的内容藏起来（照 admin-client 的既有做法）。
  -->
  <div class="escalation-ledger">
    <!-- 检索条件（默认收起，避免台账首屏被表单占满） -->
    <screen-panel class="escalation-ledger__search no-print" title="台账检索" collapsible>
      <escalation-search-form ref="search" :initial-query="initialQuery" @search="handleSearch" />
    </screen-panel>

    <!-- 状态页签（带数量） + 视图切换 -->
    <div class="escalation-ledger__bar no-print">
      <screen-tabs v-model="statusTab" :tabs="statusTabs" @change="handleTabChange" />

      <div class="escalation-ledger__bar-right">
        <screen-radio-group
          v-model="viewMode"
          :options="viewOptions"
          size="sm"
          aria-label="台账视图切换"
        />
      </div>
    </div>

    <screen-panel class="escalation-ledger__list no-print" title="台账列表">
      <template #extra>
        <span class="escalation-ledger__total">
          共 <b>{{ total }}</b> 条台账记录
          <template v-if="currentFilterText">
            <span class="escalation-ledger__filter">当前筛选：{{ currentFilterText }}</span>
          </template>
        </span>

        <screen-button type="primary" size="sm" icon="download" :disabled="!total" @click="handleExport">
          导出台账 Excel
        </screen-button>

        <screen-button size="sm" icon="printer" :disabled="!dataSource.length" @click="handlePrintAll">
          打印台账
        </screen-button>

        <screen-button size="sm" icon="reload" :loading="loading" @click="loadData">刷新</screen-button>
      </template>

      <!-- ============ 表格视图 ============ -->
      <template v-if="viewMode === 'table'">
        <escalation-table
          v-if="dataSource.length || loading"
          :data-source="dataSource"
          :loading="loading"
          mode="ledger"
          @detail="openDetail"
          @materials="handleMaterials"
          @print="handlePrintRow"
        />
        <screen-empty
          v-else
          text="当前筛选条件下没有台账记录"
          description="可以放宽「办理状态」或「论证结果」后再查"
          bordered
        />
      </template>

      <!-- ============ 卡片视图 ============ -->
      <template v-else>
        <div v-if="dataSource.length" class="escalation-ledger__cards">
          <article
            v-for="item in dataSource"
            :key="item.id"
            class="escalation-ledger__card"
            @click="openDetail(item)"
          >
            <header class="escalation-ledger__card-head">
              <span class="escalation-ledger__card-no">{{ item.projectNo }}</span>
              <screen-tag :tone="statusTone(item.status)" size="sm">{{ item.status || '未办理' }}</screen-tag>
            </header>

            <h4 class="escalation-ledger__card-name" :title="item.projectName">{{ item.projectName }}</h4>

            <p class="escalation-ledger__card-meta">
              <span>{{ item.declareDept || '—' }}</span>
              <span>{{ item.xzqh || item.gnq || '—' }}</span>
            </p>

            <div class="escalation-ledger__card-tags">
              <screen-tag v-if="item.argResult" :tone="argResultTone(item.argResult)" size="sm">
                {{ item.argResult }}
              </screen-tag>
              <screen-tag v-else tone="muted" size="sm">未登记结果</screen-tag>
              <screen-tag tone="info" size="sm">{{ item.materialCount || 0 }} 个材料</screen-tag>
              <screen-tag v-if="isClosedWithoutResult(item)" tone="warning" size="sm">
                已办结未登记结果
              </screen-tag>
            </div>

            <p class="escalation-ledger__card-opinion">
              {{ opinionSummary(item.latestOpinion, 46) || '暂无审核意见' }}
            </p>

            <footer class="escalation-ledger__card-foot">
              <span>{{ item.latestOpinionTime || item.updateTime || '—' }}</span>
              <span class="escalation-ledger__card-actions">
                <button type="button" class="escalation-ledger__link" @click.stop="openDetail(item)">查看</button>
                <button type="button" class="escalation-ledger__link" @click.stop="handlePrintRow(item)">打印</button>
              </span>
            </footer>
          </article>
        </div>

        <screen-empty
          v-else
          text="当前筛选条件下没有台账记录"
          description="切换到表格视图可以按列查看明细"
          bordered
        />
      </template>

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

    <escalation-detail-modal ref="detailModal" @edit="handleEdit" />

    <!-- ============ 打印区（屏幕上隐藏，打印时只显示这一块） ============ -->
    <div class="escalation-ledger__print">
      <h1 class="escalation-ledger__print-title">
        {{ printMode === 'row' && printRow ? '提级论证项目台账（单项）' : '提级论证项目台账' }}
      </h1>
      <p class="escalation-ledger__print-meta">
        打印时间：{{ printTime }}　｜　记录数：{{ printMode === 'row' ? 1 : dataSource.length }} 条
      </p>

      <table v-if="printMode === 'row' && printRow" class="escalation-ledger__print-table">
        <tbody>
          <tr v-for="field in printFields" :key="`row-${field.key}`">
            <th>{{ field.label }}</th>
            <td>{{ printCell(printRow, field) }}</td>
          </tr>
        </tbody>
      </table>

      <table v-else class="escalation-ledger__print-table is-grid">
        <thead>
          <tr>
            <th v-for="field in printFields" :key="`head-${field.key}`">{{ field.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in dataSource" :key="`body-${record.id}`">
            <td v-for="field in printFields" :key="`cell-${record.id}-${field.key}`">
              {{ printCell(record, field) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenPagination,
  ScreenTabs,
  ScreenRadioGroup,
  ScreenTag,
  ScreenEmpty,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import EscalationSearchForm from './EscalationSearchForm.vue'
import EscalationTable from './EscalationTable.vue'
import EscalationDetailModal from './EscalationDetailModal.vue'
import { queryLedgerList, queryLedgerCountByStatus, exportLedgerXls } from '@/api/land/escalation'
import {
  STATUS_OPTIONS,
  statusTone,
  argResultTone,
  opinionSummary,
  isClosedWithoutResult,
  normalizeStatusCounts,
  compactQuery,
  defaultPagination,
  nowMinuteText,
} from '../constants'

/** 打印列（12 项 + 序号），与台账列口径一致，去掉操作列 */
const PRINT_FIELDS = [
  { key: 'seq', label: '序号', type: 'index' },
  { key: 'projectNo', label: '项目编号' },
  { key: 'projectName', label: '项目名称' },
  { key: 'declareDept', label: '申报单位' },
  { key: 'xzqh', label: '行政区划', fallback: 'gnq' },
  { key: 'projectType', label: '项目类型' },
  { key: 'totalInvestment', label: '总投资（亿元）' },
  { key: 'declareDate', label: '申报时间' },
  { key: 'materialCount', label: '材料数' },
  { key: 'status', label: '办理状态' },
  { key: 'argResult', label: '论证结果' },
  { key: 'latestOpinion', label: '最新审核意见' },
  { key: 'updateTime', label: '更新时间' },
]

export default {
  name: 'EscalationLedger',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenPagination,
    ScreenTabs,
    ScreenRadioGroup,
    ScreenTag,
    ScreenEmpty,
    EscalationSearchForm,
    EscalationTable,
    EscalationDetailModal,
  },
  props: {
    /**
     * 从「查询统计」的图表下钻时带的初始条件。
     * 只在 data() 里读一次；组件已被 keep-alive 缓存后改用 applyDrill。
     */
    initialQuery: { type: Object, default: () => ({}) },
  },
  data () {
    const initial = compactQuery(this.initialQuery || {})
    return {
      loading: false,
      viewMode: 'table',
      viewOptions: [
        { value: 'table', label: '表格视图' },
        { value: 'card', label: '卡片视图' },
      ],
      statusTab: initial.status || 'all',
      statusCounts: {},
      query: initial,
      dataSource: [],
      total: 0,
      pagination: Object.assign(defaultPagination(12), {
        pageSizeOptions: [12, 24, 48, 96],
      }),
      printMode: 'all',
      printRow: null,
      printTime: '',
      printFields: PRINT_FIELDS,
    }
  },
  computed: {
    statusTabs () {
      const all = STATUS_OPTIONS.reduce((sum, name) => sum + (Number(this.statusCounts[name]) || 0), 0)
      return [{ key: 'all', label: '全部', badge: all }].concat(
        STATUS_OPTIONS.map((name) => ({
          key: name,
          label: name,
          badge: Number(this.statusCounts[name]) || 0,
        }))
      )
    },
    /** 把当前生效的条件用中文说清楚，避免用户忘了自己筛过什么 */
    currentFilterText () {
      const query = this.query || {}
      const parts = []
      if (query.status) parts.push(`办理状态=${query.status}`)
      if (query.argResult) parts.push(`论证结果=${query.argResult}`)
      if (query.projectName) parts.push(`项目名称≈${query.projectName}`)
      if (query.declareDept) parts.push(`申报单位≈${query.declareDept}`)
      if (query.xzqh) parts.push(`行政区划=${query.xzqh}`)
      if (query.beginDeclareDate || query.endDeclareDate) {
        parts.push(`申报时间 ${query.beginDeclareDate || ''} ~ ${query.endDeclareDate || ''}`)
      }
      return parts.join('，')
    },
  },
  created () {
    this.loadData()
    this.loadCounts()
  },
  methods: {
    statusTone,
    argResultTone,
    opinionSummary,
    isClosedWithoutResult,

    /* ---------------- 数据 ---------------- */

    loadData () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize,
      })

      return queryLedgerList(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '台账列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })

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
      return queryLedgerCountByStatus(params).then((res) => {
        if (!res || !res.success) return
        this.statusCounts = normalizeStatusCounts(res.result)
      })
    },

    /* ---------------- 筛选 ---------------- */

    handleSearch (query) {
      this.query = Object.assign({}, query || {})
      // 表单里的办理状态优先同步到页签，避免两处条件互相矛盾
      this.statusTab = this.query.status || 'all'
      this.pagination.current = 1
      this.loadData()
      this.loadCounts()
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
      this.loadData()
      this.loadCounts()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    /**
     * 供 index.vue 在图表下钻时调用（组件已被 keep-alive 缓存时 data() 不会再跑）。
     * 三步都要做：覆盖表单显示、覆盖查询条件、重置到第 1 页重新查。
     */
    applyDrill (query) {
      const next = compactQuery(query || {})
      if (this.$refs.search) this.$refs.search.setQuery(next)
      this.query = next
      this.statusTab = next.status || 'all'
      this.pagination.current = 1
      this.loadData()
      this.loadCounts()
    },

    /* ---------------- 行操作 ---------------- */

    openDetail (record, tab) {
      this.$refs.detailModal.open(record, tab)
    },

    handleMaterials (record) {
      this.openDetail(record, 'materials')
    },

    /** 台账不提供编辑入口：录入是「项目录入」页的职责，这里给一句明确指引 */
    handleEdit (record) {
      toast.info(`请到「项目录入」页编辑该项目：${(record && record.projectNo) || ''}`)
    },

    handleExport () {
      exportLedgerXls(this.query)
      toast.info('已开始导出台账，请稍候…')
    },

    /* ---------------- 打印 ---------------- */

    handlePrintAll () {
      this.printMode = 'all'
      this.printRow = null
      this.printTime = nowMinuteText()
      this.$nextTick(() => {
        window.print()
      })
    },

    handlePrintRow (record) {
      this.printMode = 'row'
      this.printRow = record
      this.printTime = nowMinuteText()
      this.$nextTick(() => {
        window.print()
      })
    },

    printCell (record, field) {
      if (!record) return '—'
      if (field.type === 'index') {
        return this.dataSource.indexOf(record) + 1
      }
      let value = record[field.key]
      if ((value === null || value === undefined || value === '') && field.fallback) {
        value = record[field.fallback]
      }
      if (value === null || value === undefined || value === '') {
        // 办理状态在后端可能为空，但语义上就是「未办理」，打印件上不能留空
        return field.key === 'status' ? '未办理' : '—'
      }
      if (field.key === 'argResult') {
        return record.argResult_dictText || value
      }
      if (field.key === 'projectType') {
        return record.projectType_dictText || value
      }
      return value
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.escalation-ledger {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__search {
    flex: 0 0 auto;
  }

  &__bar {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
  }

  &__bar-right {
    flex: none;
  }

  &__list {
    flex: 1 1 0;
    min-height: 0;
  }

  &__total {
    margin-right: var(--screen-space-3);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);

    b {
      margin: 0 2px;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-sm);
      color: var(--screen-accent);
    }
  }

  &__filter {
    margin-left: var(--screen-space-2);
    color: var(--screen-accent-soft);
  }

  /* ---------------- 卡片视图 ---------------- */
  &__cards {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--screen-space-4);
    overflow-y: auto;
    padding-right: 2px;
    .screen-scrollbar();
  }

  &__card {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    padding: var(--screen-space-4);
    // 卡片是玻璃面板的同族：更实的底色，保证压在表格/地图上也读得清
    background: var(--screen-panel-bg-solid);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
    cursor: pointer;
    transition: border-color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);

    &:hover {
      border-color: var(--screen-border-strong);
      background: var(--screen-elevate);
    }

    .screen-focus-ring();
  }

  &__card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-2);
  }

  &__card-no {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    letter-spacing: 0.02em;
    color: var(--screen-accent);
    .screen-ellipsis();
  }

  &__card-name {
    margin: 0;
    font-size: var(--screen-font-md);
    font-weight: 500;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__card-meta {
    display: flex;
    gap: var(--screen-space-3);
    margin: 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);

    span {
      .screen-ellipsis();
    }
  }

  &__card-tags {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-2);
  }

  &__card-opinion {
    min-height: 36px;
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-sub);
    .screen-ellipsis-multi(2);
  }

  &__card-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-2);
    padding-top: var(--screen-space-2);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    border-top: 1px solid var(--screen-border-soft);
  }

  &__card-actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
  }

  &__link {
    .screen-link-action();
  }

  /* 打印区在屏幕上隐藏（真正的打印规则放在非 scoped 的 style 块里） */
  &__print {
    display: none;
  }
}

.escalation-ledger__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 1500px) {
  .escalation-ledger__cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1200px) {
  .escalation-ledger__cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

<!--
  打印样式必须放在**非 scoped** 的 style 块里：
  ① 需要命中 body 以及本组件之外的页面外壳（顶栏 / 侧栏），scoped 的属性选择器匹配不到；
  ② 打印时把 body 下所有内容设为不可见，只让台账打印区可见（visibility 不影响布局宽度）。

  ★ 颜色用 white / black 这两个 CSS 颜色关键字，而不是十六进制的白与黑：
    纸是白底黑字，不能沿用暗色令牌；同时本模块的自查规则要求
    「新增文件里不出现十六进制颜色字面量」，用关键字既满足打印需求又能被机械校验。
-->
<style lang="less">
@print-ink: black;
@print-paper: white;
@print-line: gray;

@media print {
  body * {
    visibility: hidden;
  }

  .escalation-ledger__print,
  .escalation-ledger__print * {
    visibility: visible;
  }

  .escalation-ledger__print {
    position: absolute;
    left: 0;
    top: 0;
    display: block !important;
    width: 100%;
    padding: 0 8mm;
    color: @print-ink;
    background: @print-paper;
  }

  .escalation-ledger__print-title {
    margin: 0 0 6px;
    font-size: 18px;
    font-weight: 600;
    text-align: center;
  }

  .escalation-ledger__print-meta {
    margin: 0 0 10px;
    font-size: 12px;
    text-align: center;
  }

  .escalation-ledger__print-table {
    width: 100%;
    border-collapse: collapse;
    table-layout: fixed;
    font-size: 11px;

    th,
    td {
      padding: 4px 6px;
      border: 1px solid @print-line;
      vertical-align: top;
      word-break: break-word;
    }

    th {
      font-weight: 600;
      text-align: left;
      background: @print-paper;
    }

    &.is-grid th {
      text-align: center;
    }
  }
}
</style>
