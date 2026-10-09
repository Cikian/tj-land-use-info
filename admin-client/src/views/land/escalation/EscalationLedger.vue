<template>
  <a-card :bordered="false" class="escalation-ledger-page">
    <!-- 页头 -->
    <div class="page-head no-print">
      <div class="page-head__text">
        <h2 class="page-head__title">提级论证资料及台账管理</h2>
        <p class="page-head__desc">
          按台账方式集中管理提级论证项目，快速查看各项目当前办理状态、论证结果、材料数与最新审核意见。
          14 列高密度台账（冻结前 3 列）支持状态筛选、卡片视图与打印；办理状态与论证结果是两个独立维度，可二维筛选。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:escalation:export'" type="primary" icon="download" :disabled="!total" @click="handleExport">
          导出台账 Excel
        </a-button>
        <a-button icon="printer" :disabled="!dataSource.length" @click="handlePrintAll">打印台账</a-button>
        <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
      </div>
    </div>

    <!-- 查询条件（默认收起，避免台账首屏被表单占满） -->
    <escalation-search-form ref="search" class="no-print" @search="handleSearch" />

    <!-- 顶部状态 tab（带数量） -->
    <div class="ledger-bar no-print">
      <div class="ledger-tabs">
        <a-tabs :activeKey="statusTab" :tabBarStyle="{ marginBottom: '0' }" @change="handleTabChange">
          <a-tab-pane v-for="tab in statusTabs" :key="tab.value">
            <template slot="tab">
              <span class="ledger-tabs__label">{{ tab.label }}</span>
              <span class="ledger-tabs__count">{{ tab.count }}</span>
            </template>
          </a-tab-pane>
        </a-tabs>
      </div>
      <div class="ledger-bar__right">
        <a-radio-group v-model="viewMode" buttonStyle="solid" size="small">
          <a-radio-button value="table"><a-icon type="table" /> 表格视图</a-radio-button>
          <a-radio-button value="card"><a-icon type="appstore" /> 卡片视图</a-radio-button>
        </a-radio-group>
      </div>
    </div>

    <!-- ============ 表格视图 ============ -->
    <div v-show="viewMode === 'table'" class="list-panel no-print">
      <div class="list-panel__toolbar">
        <span class="list-panel__total">
          共 <b>{{ total }}</b> 条台账记录
          <span v-if="currentFilterText" class="list-panel__filter">当前筛选：{{ currentFilterText }}</span>
        </span>
        <span class="list-panel__hint">口径与「查询统计」一致；点状态/结果标签可直接按该值筛选</span>
      </div>

      <escalation-table
        :dataSource="dataSource"
        :loading="loading"
        :pagination="pagination"
        mode="ledger"
        @change="handleTableChange"
        @detail="openDetail"
        @materials="handleMaterials"
        @print="handlePrintRow" />
    </div>

    <!-- ============ 卡片视图（对应原型左侧列表样式） ============ -->
    <div v-show="viewMode === 'card'" class="ledger-cards no-print">
      <a-spin :spinning="loading">
        <a-empty v-if="!dataSource.length" description="当前筛选条件下没有台账记录" />
        <a-row v-else :gutter="16">
          <a-col v-for="item in dataSource" :key="item.id" :xs="24" :sm="12" :lg="8" :xl="6">
            <div class="ledger-card" @click="openDetail(item)">
              <div class="ledger-card__head">
                <span class="ledger-card__no">{{ item.projectNo }}</span>
                <a-tag :color="statusColor(item.status)">{{ item.status }}</a-tag>
              </div>
              <div class="ledger-card__name" :title="item.projectName">{{ item.projectName }}</div>
              <div class="ledger-card__meta">
                <span>{{ item.declareDept || '—' }}</span>
                <span>{{ item.xzqh || item.gnq || '—' }}</span>
              </div>
              <div class="ledger-card__tags">
                <a-tag v-if="item.argResult" :color="argResultColor(item.argResult)">{{ item.argResult }}</a-tag>
                <a-tag v-else>未登记结果</a-tag>
                <a-tag color="blue">{{ item.materialCount || 0 }} 个材料</a-tag>
              </div>
              <div class="ledger-card__opinion">
                {{ opinionSummary(item.latestOpinion, 46) || '暂无审核意见' }}
              </div>
              <div class="ledger-card__foot">
                <span>{{ item.latestOpinionTime || item.updateTime || '—' }}</span>
                <span class="ledger-card__actions">
                  <a @click.stop="openDetail(item)">查看</a>
                  <a-divider type="vertical" />
                  <a @click.stop="handlePrintRow(item)">打印</a>
                </span>
              </div>
            </div>
          </a-col>
        </a-row>

        <div v-if="total > pagination.pageSize" class="ledger-cards__pager">
          <a-pagination
            :current="pagination.current"
            :pageSize="pagination.pageSize"
            :total="total"
            showSizeChanger
            :pageSizeOptions="['12', '24', '48', '96']"
            @change="handleCardPageChange"
            @showSizeChange="handleCardPageSizeChange" />
        </div>
      </a-spin>
    </div>

    <escalation-detail-modal ref="detail" @edit="handleEdit" />

    <!-- ============ 打印区（屏幕上隐藏，打印时只显示这一块） ============ -->
    <div class="escalation-ledger-print">
      <h1 v-if="printMode === 'row' && printRow" class="print-title">提级论证项目台账（单项）</h1>
      <h1 v-else class="print-title">提级论证项目台账</h1>
      <div class="print-meta">
        打印时间：{{ printTime }}　｜　记录数：{{ printMode === 'row' ? 1 : dataSource.length }} 条
      </div>

      <table v-if="printMode === 'row' && printRow" class="print-table">
        <tbody>
          <tr v-for="field in printFields" :key="field.key">
            <th>{{ field.label }}</th>
            <td>{{ printCell(printRow, field) }}</td>
          </tr>
        </tbody>
      </table>

      <table v-else class="print-table print-table--grid">
        <thead>
          <tr>
            <th v-for="field in printFields" :key="field.key">{{ field.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in dataSource" :key="record.id">
            <td v-for="field in printFields" :key="field.key">{{ printCell(record, field) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </a-card>
</template>

<script>
  import EscalationSearchForm from './modules/EscalationSearchForm'
  import EscalationTable from './modules/EscalationTable'
  import EscalationDetailModal from './modules/EscalationDetailModal'
  import {
    DICT,
    STATUS_OPTIONS,
    argResultColor,
    dictText,
    exportLedgerXls,
    loadAllDicts,
    opinionSummary,
    queryLedgerCountByStatus,
    queryLedgerList,
    statusColor
  } from '@/api/land/escalation'

  /**
   * 提级论证资料及台账管理（方案 2.3.3 第 3 项：台账方式管理，快速查看各项目当前状态）
   *
   * 结构：
   *  · 顶部状态 tab（全部 / 未办理 / 办理中 / 已办结 / 已归档，均带数量）；
   *  · 表格视图：14 列高密度台账（EscalationTable mode="ledger"，冻结前 3 列）；
   *  · 卡片视图：对应原型左侧列表样式；
   *  · 打印：window.print() + 独立打印区（屏幕上隐藏），单项与整页两种口径。
   *
   * 支持从「查询统计」的图表下钻带条件进入（读 $route.query）。
   */
  export default {
    name: 'EscalationLedger',
    components: { EscalationSearchForm, EscalationTable, EscalationDetailModal },
    data () {
      return {
        loading: false,
        viewMode: 'table',
        statusTab: 'all',
        statusCounts: {},
        query: {},
        dataSource: [],
        total: 0,
        printMode: 'all',
        printRow: null,
        printTime: '',
        printFields: [
          { key: 'index', label: '序号', type: 'index' },
          { key: 'projectNo', label: '项目编号' },
          { key: 'projectName', label: '项目名称' },
          { key: 'declareDept', label: '申报单位' },
          { key: 'xzqh', label: '行政区划', fallback: 'gnq' },
          { key: 'projectType', label: '项目类型', dict: 'land_escalation_project_type' },
          { key: 'totalInvestment', label: '总投资（亿元）' },
          { key: 'declareDate', label: '申报时间' },
          { key: 'materialCount', label: '材料数' },
          { key: 'status', label: '办理状态' },
          { key: 'argResult', label: '论证结果' },
          { key: 'latestOpinion', label: '最新审核意见摘要' },
          { key: 'updateTime', label: '最近更新时间' }
        ],
        pagination: {
          current: 1,
          pageSize: 12,
          total: 0,
          showSizeChanger: true,
          showQuickJumper: true,
          pageSizeOptions: ['12', '24', '48', '96'],
          showTotal: total => `共 ${total} 条`
        }
      }
    },
    computed: {
      statusTabs () {
        const all = STATUS_OPTIONS.reduce((sum, name) => sum + (Number(this.statusCounts[name]) || 0), 0)
        return [{ value: 'all', label: '全部', count: all }].concat(
          STATUS_OPTIONS.map(name => ({ value: name, label: name, count: Number(this.statusCounts[name]) || 0 }))
        )
      },
      currentFilterText () {
        const parts = []
        if (this.query.status) {
          parts.push(`办理状态=${this.query.status}`)
        }
        if (this.query.argResult) {
          parts.push(`论证结果=${this.query.argResult}`)
        }
        if (this.query.projectName) {
          parts.push(`项目名称≈${this.query.projectName}`)
        }
        if (this.query.declareDept) {
          parts.push(`申报单位≈${this.query.declareDept}`)
        }
        if (this.query.xzqh) {
          parts.push(`行政区划=${this.query.xzqh}`)
        }
        if (this.query.beginDeclareDate || this.query.endDeclareDate) {
          parts.push(`申报时间 ${this.query.beginDeclareDate || ''} ~ ${this.query.endDeclareDate || ''}`)
        }
        return parts.join('，')
      }
    },
    created () {
      loadAllDicts()
      // 从「查询统计」图表下钻：/land/escalation/ledger?argResult=通过
      const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
      this.query = Object.assign({}, routeQuery)
      if (this.query.status) {
        this.statusTab = this.query.status
      }
      this.loadData()
      this.loadCounts()
    },
    methods: {
      statusColor,
      argResultColor,
      opinionSummary,
      // ------------------------------------------------------------------
      // 数据
      // ------------------------------------------------------------------
      loadData () {
        this.loading = true
        const params = Object.assign({}, this.query, {
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        })
        return queryLedgerList(params).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadData()
          }
        }).finally(() => {
          this.loading = false
        })
      },
      /** 状态 tab 的数量：与列表同条件、但不含 status（否则切一次 tab 数量就只剩一个） */
      loadCounts () {
        const params = Object.assign({}, this.query)
        delete params.status
        return queryLedgerCountByStatus(params).then(res => {
          if (!res.success) {
            return
          }
          const source = res.result
          const counts = {}
          if (Array.isArray(source)) {
            source.forEach(item => {
              if (item && item.name !== undefined) {
                counts[item.name] = Number(item.count) || 0
              }
            })
          } else if (source && typeof source === 'object') {
            Object.keys(source).forEach(key => {
              counts[key] = Number(source[key]) || 0
            })
          }
          this.statusCounts = counts
        })
      },
      // ------------------------------------------------------------------
      // 筛选
      // ------------------------------------------------------------------
      handleSearch (query) {
        this.query = Object.assign({}, query || {})
        // 表单里的办理状态优先同步到 tab，避免两处条件互相矛盾
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
      handleTableChange (pagination) {
        this.pagination = Object.assign({}, this.pagination, {
          current: pagination.current,
          pageSize: pagination.pageSize
        })
        this.loadData()
      },
      handleCardPageChange (page) {
        this.pagination.current = page
        this.loadData()
      },
      handleCardPageSizeChange (page, pageSize) {
        this.pagination.current = page
        this.pagination.pageSize = pageSize
        this.loadData()
      },
      // ------------------------------------------------------------------
      // 行操作
      // ------------------------------------------------------------------
      openDetail (record, tab) {
        this.$refs.detail.open(record, tab)
      },
      handleMaterials (record) {
        this.openDetail(record, 'materials')
      },
      handleEdit (record) {
        this.$message.info('请到「项目录入」页编辑该项目：' + (record.projectNo || ''))
      },
      handleExport () {
        exportLedgerXls(this.query)
        this.$message.success('已开始导出台账，请稍候…')
      },
      // ------------------------------------------------------------------
      // 打印
      // ------------------------------------------------------------------
      handlePrintAll () {
        this.printMode = 'all'
        this.printRow = null
        this.printTime = this.nowText()
        this.$nextTick(() => {
          window.print()
        })
      },
      handlePrintRow (record) {
        this.printMode = 'row'
        this.printRow = record
        this.printTime = this.nowText()
        this.$nextTick(() => {
          window.print()
        })
      },
      printCell (record, field) {
        if (!record) {
          return '—'
        }
        if (field.type === 'index') {
          return this.dataSource.indexOf(record) + 1
        }
        let value = record[field.key]
        if ((value === null || value === undefined || value === '') && field.fallback) {
          value = record[field.fallback]
        }
        if (value === null || value === undefined || value === '') {
          return field.key === 'status' ? '未办理' : '—'
        }
        if (field.key === 'projectType') {
          return record.projectType_dictText || dictText(DICT.projectType, value) || value
        }
        if (field.key === 'argResult') {
          return record.argResult_dictText || dictText(DICT.argResult, value) || value
        }
        return value
      },
      nowText () {
        const d = new Date()
        const pad = n => String(n).padStart(2, '0')
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .escalation-ledger-page {
    /deep/ .ant-card-body {
      padding: 20px 24px 24px;
    }
  }

  .page-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    padding-bottom: 16px;
    border-bottom: 1px solid @border-color;

    &__title {
      margin: 0 0 6px;
      font-size: 18px;
      font-weight: 600;
      color: #0f172a;
    }

    &__desc {
      margin: 0;
      max-width: 900px;
      font-size: 13px;
      line-height: 20px;
      color: @text-muted;
    }

    &__actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      flex-shrink: 0;
    }
  }

  /* ---------------- 状态 tab + 视图切换 ---------------- */
  .ledger-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 12px;

    &__right {
      flex: none;
    }
  }

  .ledger-tabs {
    flex: 1 1 auto;
    min-width: 0;

    &__label {
      margin-right: 6px;
    }

    &__count {
      display: inline-block;
      min-width: 20px;
      padding: 0 6px;
      font-size: 12px;
      line-height: 18px;
      text-align: center;
      color: @text-muted;
      background: #f1f5f9;
      border-radius: 9px;
    }
  }

  /* ---------------- 列表 ---------------- */
  .list-panel {
    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 12px;
      font-size: 13px;
      color: @text-muted;

      b {
        color: #0f172a;
      }
    }

    &__filter {
      margin-left: 10px;
      color: #1d4ed8;
    }

    &__hint {
      font-size: 12px;
      color: @text-weak;
    }
  }

  /* ---------------- 卡片视图 ---------------- */
  .ledger-cards {
    &__pager {
      margin-top: 16px;
      text-align: right;
    }
  }

  .ledger-card {
    padding: 14px 16px;
    margin-bottom: 16px;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;
    cursor: pointer;
    transition: box-shadow 0.2s ease, border-color 0.2s ease;

    &:hover {
      border-color: #93c5fd;
      box-shadow: 0 4px 12px rgba(46, 124, 246, 0.12);
    }

    &__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;

      /deep/ .ant-tag {
        margin: 0;
      }
    }

    &__no {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 12px;
      letter-spacing: 0.5px;
      color: #2e7cf6;
    }

    &__name {
      margin: 8px 0 6px;
      font-size: 15px;
      font-weight: 600;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__meta {
      display: flex;
      gap: 10px;
      font-size: 12px;
      color: @text-muted;

      span {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    &__tags {
      margin: 10px 0 8px;

      /deep/ .ant-tag {
        margin-right: 6px;
      }
    }

    &__opinion {
      font-size: 12px;
      line-height: 18px;
      color: @text-muted;
      min-height: 36px;
    }

    &__foot {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-top: 10px;
      padding-top: 8px;
      font-size: 12px;
      color: @text-weak;
      border-top: 1px dashed @border-color;
    }
  }

  /* 打印区在屏幕上隐藏（打印规则放在非 scoped 的 style 块里） */
  .escalation-ledger-print {
    display: none;
  }
</style>

<!--
  打印样式必须放在非 scoped 的 style 里：
  ① 需要命中 body 以及本组件之外的页面外壳（顶栏 / 侧边栏），scoped 属性选择器匹配不到；
  ② 打印时把 body 下所有内容设为不可见，只让台账打印区可见（visibility 不影响布局宽度）。
-->
<style lang="less">
  @media print {
    body * {
      visibility: hidden;
    }

    .escalation-ledger-print,
    .escalation-ledger-print * {
      visibility: visible;
    }

    .escalation-ledger-print {
      position: absolute;
      left: 0;
      top: 0;
      display: block !important;
      width: 100%;
      padding: 0 8mm;
      color: #000;
      background: #fff;
    }

    .escalation-ledger-print .print-title {
      margin: 0 0 6px;
      font-size: 18px;
      font-weight: 600;
      text-align: center;
    }

    .escalation-ledger-print .print-meta {
      margin-bottom: 10px;
      font-size: 12px;
      text-align: center;
    }

    .escalation-ledger-print .print-table {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
      font-size: 11px;
    }

    .escalation-ledger-print .print-table th,
    .escalation-ledger-print .print-table td {
      padding: 4px 6px;
      border: 1px solid #333;
      word-break: break-word;
      vertical-align: top;
    }

    .escalation-ledger-print .print-table th {
      background: #f1f5f9;
      font-weight: 600;
      text-align: left;
    }

    .escalation-ledger-print .print-table--grid th {
      text-align: center;
    }
  }
</style>
