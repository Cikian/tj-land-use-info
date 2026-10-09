<template>
  <a-card :bordered="false" class="escalation-query-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">提级论证查询统计</h2>
        <p class="page-head__desc">
          按项目名称、申报单位、项目类型、行政区划、申报时间、<b>论证结果</b>、办理状态、是否土地整理项目、
          投资额区间共 9 个条件组合检索，并给出 5 张统计图。统计口径与下方列表完全一致——
          上方筛选什么，下面的图与列表就统计什么；点图中任一图元可下钻到台账页查看明细。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:escalation:export'" type="primary" icon="download" :disabled="!total" @click="handleExport">
          导出查询结果 Excel
        </a-button>
        <a-button icon="profile" @click="goLedger">资料及台账管理</a-button>
        <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
      </div>
    </div>

    <!-- 9 个查询条件 -->
    <escalation-search-form
      ref="search"
      :defaultExpanded="true"
      :initialQuery="initialQuery"
      @search="handleSearch" />

    <a-spin :spinning="loading">
      <!-- ============ 关键指标 ============ -->
      <div class="stat-cards">
        <div class="stat-card">
          <div class="stat-card__label">项目总数</div>
          <div class="stat-card__value">{{ total }}</div>
          <div class="stat-card__foot">当前查询条件命中的项目</div>
        </div>
        <div
          v-for="item in statusCards"
          :key="item.name"
          class="stat-card"
          :class="'stat-card--' + item.key">
          <div class="stat-card__label">{{ item.name }}</div>
          <div class="stat-card__value">{{ item.count }}</div>
          <div class="stat-card__foot">
            <a @click="drill({ status: item.name })">查看明细</a>
          </div>
        </div>
      </div>

      <!-- ============ 5 张统计图 ============ -->
      <escalation-stat-charts
        :loading="chartLoading"
        :byResult="stat.byResult"
        :byDept="stat.byDept"
        :byXzqh="stat.byXzqh"
        :byMonth="stat.byMonth"
        :byType="stat.byType"
        :byStatus="stat.byStatus"
        @drill="drill" />

      <!-- ============ 列表 ============ -->
      <div class="list-panel">
        <div class="list-panel__toolbar">
          <span class="list-panel__total">共命中 <b>{{ total }}</b> 个项目</span>
          <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
        </div>

        <escalation-table
          :dataSource="dataSource"
          :loading="loading"
          :pagination="pagination"
          :readonly="true"
          @change="handleTableChange"
          @detail="handleDetail" />
      </div>
    </a-spin>

    <escalation-detail-modal ref="detail" />
  </a-card>
</template>

<script>
  import EscalationSearchForm from './modules/EscalationSearchForm'
  import EscalationTable from './modules/EscalationTable'
  import EscalationDetailModal from './modules/EscalationDetailModal'
  import EscalationStatCharts from './modules/EscalationStatCharts'
  import {
    STATUS_OPTIONS,
    exportProjectXls,
    loadAllDicts,
    queryLedgerCountByStatus,
    queryProjectList,
    queryStatByDept,
    queryStatByMonth,
    queryStatByResult,
    queryStatByType,
    queryStatByXzqh
  } from '@/api/land/escalation'

  /**
   * 提级论证查询统计（方案 2.3.3 第 2 项：多维查询 + 可导出）
   *
   * 9 个条件见 EscalationSearchForm；5 张统计图见 EscalationStatCharts；
   * 图表下钻统一跳台账页（/land/escalation/ledger）并把条件带在 query 上，
   * 与 ArchiveStatistics.vue 的下钻方式一致。
   *
   * 统计接口：stat/byResult、byDept、byXzqh、byMonth、byType（设计文档 4.2），
   * 第 5 张图「办理状态分布」用 ledger/countByStatus 取数。
   */
  export default {
    name: 'EscalationQuery',
    components: { EscalationSearchForm, EscalationTable, EscalationDetailModal, EscalationStatCharts },
    data () {
      return {
        loading: false,
        chartLoading: false,
        /** 从台账/统计跳转过来时带的初始条件 */
        initialQuery: {},
        query: {},
        dataSource: [],
        total: 0,
        stat: this.buildEmptyStat(),
        statusCounts: {},
        pagination: {
          current: 1,
          pageSize: 10,
          total: 0,
          showSizeChanger: true,
          showQuickJumper: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: total => `共 ${total} 条`
        }
      }
    },
    computed: {
      /** 4 张状态卡（配色与台账标签语义一致） */
      statusCards () {
        const keys = { 未办理: 'idle', 办理中: 'doing', 已办结: 'done', 已归档: 'archived' }
        return STATUS_OPTIONS.map(name => ({
          name: name,
          key: keys[name] || 'idle',
          count: Number(this.statusCounts[name]) || 0
        }))
      }
    },
    created () {
      loadAllDicts()
      // 支持从台账/统计下钻过来：/land/escalation/query?argResult=通过
      const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
      this.initialQuery = Object.assign({}, routeQuery)
      this.query = Object.assign({}, routeQuery)
      this.loadData()
      this.loadStat()
    },
    methods: {
      buildEmptyStat () {
        return {
          byResult: [],
          byDept: [],
          byXzqh: [],
          byMonth: [],
          byType: [],
          byStatus: []
        }
      },
      loadData () {
        this.loading = true
        const params = Object.assign({}, this.query, {
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        })
        return queryProjectList(params).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })
        }).finally(() => {
          this.loading = false
        })
      },
      /** 5 张图 + 状态计数，与列表共用同一套查询条件 */
      loadStat () {
        const params = Object.assign({}, this.query)
        this.chartLoading = true
        const unwrap = task => task.then(res => (res && res.success ? (res.result || []) : [])).catch(() => [])
        return Promise.all([
          unwrap(queryStatByResult(params)),
          unwrap(queryStatByDept(params)),
          unwrap(queryStatByXzqh(params)),
          unwrap(queryStatByMonth(params)),
          unwrap(queryStatByType(params)),
          unwrap(queryLedgerCountByStatus(params))
        ]).then(([byResult, byDept, byXzqh, byMonth, byType, byStatus]) => {
          this.stat = { byResult, byDept, byXzqh, byMonth, byType, byStatus }
          this.statusCounts = this.normalizeStatusCounts(byStatus)
        }).finally(() => {
          this.chartLoading = false
        })
      },
      /** countByStatus 兼容 NameCount 数组与 { 状态: 数量 } 两种返回 */
      normalizeStatusCounts (source) {
        const result = {}
        if (Array.isArray(source)) {
          source.forEach(item => {
            if (item && item.name !== undefined) {
              result[item.name] = Number(item.count) || 0
            }
          })
          return result
        }
        if (source && typeof source === 'object') {
          Object.keys(source).forEach(key => {
            result[key] = Number(source[key]) || 0
          })
        }
        return result
      },
      handleSearch (query) {
        this.query = query || {}
        this.pagination.current = 1
        this.loadData()
        this.loadStat()
      },
      handleTableChange (pagination) {
        this.pagination = Object.assign({}, this.pagination, {
          current: pagination.current,
          pageSize: pagination.pageSize
        })
        this.loadData()
      },
      handleDetail (record) {
        this.$refs.detail.open(record)
      },
      handleExport () {
        exportProjectXls(this.query)
        this.$message.success('已开始导出，请稍候…')
      },
      /** ★ 图表下钻：带着条件跳到台账页（台账页 created 时读取 $route.query） */
      drill (query) {
        const clean = {}
        Object.keys(query || {}).forEach(key => {
          const value = query[key]
          if (value !== undefined && value !== null && value !== '') {
            clean[key] = value
          }
        })
        if (!Object.keys(clean).length) {
          return
        }
        this.$router.push({ path: '/land/escalation/ledger', query: clean })
      },
      goLedger () {
        this.$router.push({ path: '/land/escalation/ledger' })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .escalation-query-page {
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

      b {
        color: #0f172a;
      }
    }

    &__actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      flex-shrink: 0;
    }
  }

  /* ---------------- 关键指标卡 ---------------- */
  .stat-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 16px;
    margin-bottom: 16px;
  }

  .stat-card {
    padding: 16px 18px;
    background: #fff;
    border: 1px solid @border-color;
    border-left: 3px solid #cbd5e1;
    border-radius: 8px;

    &__label {
      font-size: 13px;
      color: @text-muted;
    }

    &__value {
      margin: 6px 0 4px;
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 28px;
      line-height: 32px;
      letter-spacing: 1px;
      color: #0f172a;
    }

    &__foot {
      font-size: 12px;
      color: @text-weak;
    }

    &--doing {
      border-left-color: #2e7cf6;

      .stat-card__value {
        color: #1d4ed8;
      }
    }

    &--done {
      border-left-color: #10b981;

      .stat-card__value {
        color: #0f766e;
      }
    }

    &--archived {
      border-left-color: #06b6d4;

      .stat-card__value {
        color: #0e7490;
      }
    }

    &--idle {
      border-left-color: #94a3b8;
    }
  }

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
  }

  @media (max-width: 576px) {
    .page-head__actions {
      width: 100%;

      /deep/ .ant-btn {
        flex: 1 1 auto;
      }
    }
  }
</style>
