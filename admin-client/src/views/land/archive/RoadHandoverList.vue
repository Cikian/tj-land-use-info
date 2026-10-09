<template>
  <a-card :bordered="false" class="handover-page">
    <!-- ============ 页头 ============ -->
    <div class="page-head no-print">
      <div class="page-head__text">
        <h2 class="page-head__title">道路交付及养护协议移交事项</h2>
        <p class="page-head__desc">
          管理道路的交付、养护协议与正式移交事项（方案 2.3.2 第 6 项）：登记协议签订情况、
          接收管养单位、移交日期与养护期，支持按道路/行政区/宗地/配套项目/移交类型/状态/协议与移交日期区间快速检索。
          协议扫描件与移交单统一存放于档案管理模块，本页只记录关联。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:handover:add'" type="primary" icon="plus" @click="handleAdd">新增移交事项</a-button>
        <a-button v-has="'land:handover:export'" icon="download" :disabled="!total" @click="handleExport">
          导出 Excel
        </a-button>
        <a-button icon="printer" :disabled="!dataSource.length" @click="handlePrintAll">打印</a-button>
        <a-button icon="reload" :loading="loading" @click="refresh">刷新</a-button>
      </div>
    </div>

    <!-- ============ 查询条件 ============ -->
    <handover-search-form
      ref="search"
      class="no-print"
      :defaultExpanded="hasDeepLink"
      :initialQuery="initialQuery"
      @search="handleSearch" />

    <!-- ============ 指标卡 ============ -->
    <div class="handover-summary no-print">
      <a-row :gutter="12">
        <a-col :xs="12" :sm="8" :md="4" v-for="card in statCards" :key="card.key">
          <div class="stat-card" :class="card.cls" @click="card.onClick && card.onClick()">
            <div class="stat-card__label">{{ card.label }}</div>
            <div class="stat-card__value">{{ card.value }}</div>
            <div class="stat-card__extra">{{ card.extra }}</div>
          </div>
        </a-col>
      </a-row>
    </div>

    <!-- ============ 状态页签 ============ -->
    <div class="handover-bar no-print">
      <div class="handover-tabs">
        <a-tabs :activeKey="statusTab" :tabBarStyle="{ marginBottom: '0' }" @change="handleTabChange">
          <a-tab-pane v-for="tab in statusTabs" :key="tab.value">
            <template slot="tab">
              <span class="handover-tabs__label">{{ tab.label }}</span>
              <span class="handover-tabs__count">{{ tab.count }}</span>
            </template>
          </a-tab-pane>
        </a-tabs>
      </div>
      <div class="handover-bar__right">
        <a-button
          v-has="'land:handover:delete'"
          size="small"
          type="danger"
          ghost
          icon="delete"
          :disabled="!selectedRowKeys.length"
          @click="handleBatchDelete">
          批量删除{{ selectedRowKeys.length ? '（' + selectedRowKeys.length + '）' : '' }}
        </a-button>
      </div>
    </div>

    <!-- ============ 列表 ============ -->
    <div class="list-panel no-print">
      <div class="list-panel__toolbar">
        <span class="list-panel__total">
          共 <b>{{ total }}</b> 条移交事项
          <span v-if="currentFilterText" class="list-panel__filter">当前筛选：{{ currentFilterText }}</span>
        </span>
        <span class="list-panel__hint">
          黄色提示表示「协议信息待补录」「出让宗地待核对」或「养护期已到期/将到期」
        </span>
      </div>

      <handover-table
        :dataSource="dataSource"
        :loading="loading"
        :pagination="pagination"
        :scrollY="tableScrollY"
        selectable
        :selectedRowKeys="selectedRowKeys"
        @change="handleTableChange"
        @select-change="handleSelectChange"
        @detail="openDetail"
        @edit="handleEdit"
        @link="openLinkFromRow"
        @remove="handleDelete"
        @print="handlePrintRow"
        @status-change="handleStatusChange" />
    </div>

    <!-- ============ 弹窗 ============ -->
    <handover-modal ref="modal" @ok="refresh" />
    <handover-detail-modal
      ref="detail"
      @edit="handleEditFromDetail"
      @status-change="handleStatusChange"
      @filter="handleDetailFilter"
      @print="handlePrintRow" />

    <!-- ============ 打印区（屏幕上隐藏，打印时只显示这一块） ============ -->
    <div class="handover-print">
      <h1 class="print-title">
        {{ printMode === 'row' && printRow ? '道路交付及养护协议移交事项（单项）' : '道路交付及养护协议移交事项' }}
      </h1>
      <div class="print-meta">
        打印时间：{{ printTime }} | 记录数：{{ printMode === 'row' ? 1 : dataSource.length }} 条
        <span v-if="printMode !== 'row' && currentFilterText"> | 筛选：{{ currentFilterText }}</span>
      </div>

      <table v-if="printMode === 'row' && printRow" class="print-table print-table--detail">
        <tbody>
          <tr v-for="field in printRowFields" :key="field.key">
            <th>{{ field.label }}</th>
            <td>{{ printCell(printRow, field) }}</td>
          </tr>
        </tbody>
      </table>

      <table v-else class="print-table">
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
  import HandoverSearchForm from './modules/HandoverSearchForm'
  import HandoverTable from './modules/HandoverTable'
  import HandoverModal from './modules/HandoverModal'
  import HandoverDetailModal from './modules/HandoverDetailModal'
  import {
    HANDOVER_STATUS,
    changeHandoverStatus,
    deleteHandover,
    deleteHandoverBatch,
    exportHandoverXls,
    queryHandoverCountByStatus,
    queryHandoverPage,
    queryHandoverStat
  } from '@/api/land/handover'

  /**
   * 道路交付及养护协议移交事项（方案 2.3.2 第 6 项）
   *
   * 结构：查询条件面板 + 6 张指标卡 + 状态页签（3 值带角标）+ 列表 + 新增/编辑弹窗 + 详情弹窗 + 打印区。
   *
   * ★ 支持从其它页面带条件下钻（与台账/档案的互相跳转配套）：
   *   /land/archive/handover?facilityId=xxx、?crzdbh=xxx、?archiveId=xxx、?status=已移交
   */
  export default {
    name: 'RoadHandoverList',
    components: { HandoverSearchForm, HandoverTable, HandoverModal, HandoverDetailModal },
    data () {
      return {
        loading: false,
        /** 表体滚动高度（px）：由 updateTableScrollY 按视口剩余高度算出，见该方法注释 */
        tableScrollY: 520,
        /** 兜底预留：把「实测后仍存在的溢出」累计进来，只增不减（见 killResidualScroll） */
        extraReserve: 0,
        statusTab: 'all',
        statusCounts: {},
        stat: {
          total: 0,
          migratedCount: 0,
          manualCount: 0,
          archivedCount: 0,
          missingAgreementCount: 0,
          maintenanceExpiringCount: 0,
          maintenanceExpiredCount: 0,
          orphanCount: 0
        },
        initialQuery: {},
        hasDeepLink: false,
        query: {},
        dataSource: [],
        total: 0,
        selectedRowKeys: [],
        printMode: 'all',
        printRow: null,
        printTime: '',
        printFields: [
          { key: 'seq', label: '序号' },
          { key: 'handoverNo', label: '移交编号' },
          { key: 'roadName', label: '道路名称' },
          { key: 'dldj', label: '道路等级' },
          { key: 'xzqh', label: '行政区划', fallback: 'gnq' },
          { key: 'crzdbh', label: '出让宗地编号' },
          { key: 'dkmc', label: '地块名称' },
          { key: 'handoverType', label: '移交类型' },
          { key: 'agreementNo', label: '协议编号' },
          { key: 'agreementDate', label: '协议日期' },
          { key: 'receiveUnit', label: '接收管养单位' },
          { key: 'handoverDate', label: '移交日期' },
          { key: 'maintenanceEnd', label: '养护截止' },
          { key: 'status', label: '状态' }
        ],
        printRowFields: [
          { key: 'handoverNo', label: '移交编号' },
          { key: 'roadName', label: '道路名称' },
          { key: 'roadCode', label: '道路编号' },
          { key: 'dldj', label: '道路等级' },
          { key: 'startPoint', label: '起点' },
          { key: 'endPoint', label: '终点' },
          { key: 'lengthM', label: '长度(米)' },
          { key: 'redLineWidth', label: '红线宽度(米)' },
          { key: 'xzqh', label: '行政区划', fallback: 'gnq' },
          { key: 'crzdbh', label: '出让宗地编号' },
          { key: 'dkmc', label: '地块名称' },
          { key: 'handoverType', label: '移交类型' },
          { key: 'agreementNo', label: '协议编号' },
          { key: 'agreementName', label: '协议名称' },
          { key: 'agreementDate', label: '协议签订日期' },
          { key: 'buildUnit', label: '建设单位' },
          { key: 'receiveUnit', label: '接收管养单位' },
          { key: 'handoverDate', label: '实际移交日期' },
          { key: 'maintenanceStart', label: '养护起始日期' },
          { key: 'maintenanceEnd', label: '养护截止日期' },
          { key: 'status', label: '状态' },
          { key: 'remark', label: '备注' }
        ],
        pagination: {
          current: 1,
          pageSize: 20,
          total: 0,
          showSizeChanger: true,
          showQuickJumper: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: total => `共 ${total} 条`
        }
      }
    },
    computed: {
      statusTabs () {
        const all = HANDOVER_STATUS.reduce((sum, name) => sum + (Number(this.statusCounts[name]) || 0), 0)
        return [{ value: 'all', label: '全部', count: all }].concat(
          HANDOVER_STATUS.map(name => ({ value: name, label: name, count: Number(this.statusCounts[name]) || 0 }))
        )
      },
      statCards () {
        return [
          { key: 'total', label: '移交事项总数', value: this.stat.total || 0, extra: '登记在册的移交事项', cls: '' },
          {
            key: 'migrated',
            label: '迁移 / 人工',
            value: `${this.stat.migratedCount || 0} / ${this.stat.manualCount || 0}`,
            extra: '由旧库迁移 / 页面新增',
            cls: ''
          },
{
            key: 'done',
            label: '已移交',
            value: this.statusCounts['已移交'] || 0,
            extra: `移交中 ${this.statusCounts['移交中'] || 0}`,
            cls: 'is-success',
            onClick: () => this.handleTabChange('已移交')
          },
{
            key: 'agreement',
            label: '协议待补录',
            value: this.stat.missingAgreementCount || 0,
            extra: '协议编号与日期均为空',
            cls: this.stat.missingAgreementCount ? 'is-warning' : 'is-primary'
          },
{
            key: 'archived',
            label: '已关联档案',
            value: this.stat.archivedCount || 0,
            extra: '协议扫描件 / 移交单',
            cls: 'is-primary'
          },
{
            key: 'maintenance',
            label: '养护将到期 / 已过期',
            value: `${this.stat.maintenanceExpiringCount || 0} / ${this.stat.maintenanceExpiredCount || 0}`,
            extra: '90 天内到期 / 已过期',
            cls: this.stat.maintenanceExpiredCount ? 'is-warning' : ''
          }
        ]
      },
      currentFilterText () {
        const parts = []
        if (this.query.status) {
          parts.push(`状态=${this.query.status}`)
        }
        if (this.query.roadName) {
          parts.push(`道路名称≈${this.query.roadName}`)
        }
        if (this.query.handoverNo) {
          parts.push(`移交编号≈${this.query.handoverNo}`)
        }
        if (this.query.xzqh) {
          parts.push(`行政区划=${this.query.xzqh}`)
        }
        if (this.query.handoverType) {
          parts.push(`移交类型=${this.query.handoverType}`)
        }
        if (this.query.crzdbh) {
          parts.push(`宗地编号≈${this.query.crzdbh}`)
        }
        if (this.query.facilityId) {
          parts.push('已按配套项目过滤')
        }
        if (this.query.archiveId) {
          parts.push('已按关联档案过滤')
        }
        if (this.query.hasArchive === true) {
          parts.push('仅看已关联档案')
        } else if (this.query.hasArchive === false) {
          parts.push('仅看未关联档案')
        }
        if (this.query.keyword) {
          parts.push(`关键词≈${this.query.keyword}`)
        }
        return parts.join('，')
      }
    },
    mounted () {
      // 首屏算一次；之后由 window resize 与 ResizeObserver 自动重算（检索面板展开等局部变化）
      this.updateTableScrollY()
      this.killResidualScroll()
      window.addEventListener('resize', this.onWindowResize)
      this.observeLayout()
    },
    beforeDestroy () {
      window.removeEventListener('resize', this.onWindowResize)
      if (this.layoutObserver) {
        this.layoutObserver.disconnect()
        this.layoutObserver = null
      }
    },
    created () {
      const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
      this.initialQuery = Object.assign({}, routeQuery)
      this.query = Object.assign({}, routeQuery)
      this.hasDeepLink = Object.keys(this.initialQuery).length > 0
      if (this.query.status) {
        this.statusTab = this.query.status
      }
      this.refresh()
    },
    methods: {
      /**
       * 让列表「恰好铺满屏幕剩余高度」：表体自己滚、表头固定、整页不出现纵向滚动条。
       *
       * 全部**实测**，不留估算值 —— 上一版凭 88px 估算、且只在挂载/刷新时算一次，
       * 结果台账超屏、移交下方留白（两个方向都错）。
       *   1. 表格控件在**文档**中的位置：固定顶栏 59px、页签栏、检索面板是否展开、指标卡换行都由它体现；
       *   2. 表头高度（开了 scroll.y 之后 antd 才渲染固定表头）；
       *   3. 分页器高度**及其上下外边距**（antd 是 margin: 16px 0，只算高度会少 16px → 仍出滚动条）；
       *   4. 卡片自身的下内边距与下外边距（用 computedStyle 读，不假设有没有）。
       */
      updateTableScrollY () {
        const widget = this.$el.querySelector('.ant-table-wrapper')
        if (!widget) {
          return
        }
        const top = widget.getBoundingClientRect().top + window.pageYOffset
        const header = widget.querySelector('.ant-table-header')
        const pager = widget.querySelector('.ant-table-pagination')
        const body = this.$el.querySelector('.ant-card-body')
        const pagerStyle = pager ? window.getComputedStyle(pager) : null
        const pagerGap = pagerStyle
          ? parseFloat(pagerStyle.marginTop || 0) + parseFloat(pagerStyle.marginBottom || 0)
          : 32
        const rootStyle = window.getComputedStyle(this.$el)
        const bodyStyle = body ? window.getComputedStyle(body) : null
        const outerGap = parseFloat(rootStyle.marginBottom || 0) +
          (bodyStyle ? parseFloat(bodyStyle.paddingBottom || 0) : 0)
        const chrome = (header ? header.offsetHeight : 47) +
          (pager ? pager.offsetHeight : 48) + pagerGap + outerGap + 2
        const next = Math.max(240, Math.floor(window.innerHeight - top - chrome - this.extraReserve))
        // 变化小于 2px 不赋值，避免「改高度 → 触发 ResizeObserver → 再改」的抖动
        if (Math.abs(next - this.tableScrollY) > 2) {
          this.tableScrollY = next
        }
      },
/**
       * 兜底校准：以**渲染结果**为准 —— 若整页仍然能滚动，说明还有我没算到的内/外边距，
       * 把溢出量记进 extraReserve 再收一次。
       *
       * 为什么需要它：页面的高度构成不止「表格 + 分页器 + 卡片内边距」，
       * 全局样式、布局组件、将来新增的元素都可能再插一点进来；与其一条条猜，
       * 不如让「实际有没有滚动条」来定音。extraReserve 只增不减，所以不会与上面形成来回抖动；
       * 窗口尺寸变化时在 onWindowResize 里归零重来。
       */
      killResidualScroll () {
        this.$nextTick(() => {
          const scroller = this.findScroller()
          const isWindow = scroller === document.documentElement || scroller === document.body
          const overflow = isWindow
            ? scroller.scrollHeight - window.innerHeight
            : scroller.scrollHeight - scroller.clientHeight
          // 页面根节点的真实底边（含自身下外边距）：比视口高就是溢出，比视口矮就是留白
          const rootStyle = window.getComputedStyle(this.$el)
          const plainBottom = this.$el.getBoundingClientRect().bottom +
            parseFloat(rootStyle.marginBottom || 0)
          const slack = window.innerHeight - plainBottom
          let next = this.extraReserve
          if (overflow > 2) {
            next += overflow // 还能滚 → 收
          } else if (slack > 2) {
            next -= slack // 下方留白 → 放
          } else {
            return // 恰好铺满 → 不动（这正是收敛点）
          }
          // 夹在合理区间，避免某一帧的异常测量把高度带跑；窗口尺寸变化时 onWindowResize 会归零重来
          this.extraReserve = Math.max(-240, Math.min(400, next))
          this.updateTableScrollY()
        })
      },
/**
       * 找真正会滚动的那个容器。
       * 管理端桌面端是「窗口滚动 + 固定侧栏」，但若外层某个 div 变成 overflow:auto，
       * 参照物就得换成它 —— 这里统一探测，避免把窗口当成滚动的那个。
       */
      findScroller () {
        let node = this.$el && this.$el.parentElement
        while (node && node !== document.body && node !== document.documentElement) {
          const style = window.getComputedStyle(node)
          if (/(auto|scroll|overlay)/.test(style.overflowY) && node.scrollHeight > node.clientHeight + 2) {
            return node
          }
          node = node.parentElement
        }
        return document.documentElement
      },
/** 窗口尺寸变化：清掉兜底预留，重新按新视口算一遍并校准 */
      onWindowResize () {
        this.extraReserve = 0
        this.updateTableScrollY()
        this.killResidualScroll()
      },
/**
       * 监听布局变化自动重算。
       * 观察页面根节点：检索面板展开/收起、指标卡换行、统计面板折叠都会改变它的高度 → 触发重算；
       * 观察表格控件本身：覆盖表体高度变化。重算后值不变（有 2px 守卫）即稳定，不会来回抖。
       */
      observeLayout () {
        if (typeof ResizeObserver === 'undefined') {
          return
        }
        this.layoutObserver = new ResizeObserver(() => {
          this.updateTableScrollY()
          this.killResidualScroll()
        })
        this.layoutObserver.observe(this.$el)
        const widget = this.$el.querySelector('.ant-table-wrapper')
        if (widget) {
          this.layoutObserver.observe(widget)
        }
      },
      refresh () {
        this.loadData()
        this.loadCounts()
        this.loadStat()
        // 检索面板展开/收起会移动列表位置，这里兜底重算一次
        this.$nextTick(this.updateTableScrollY)
      },
      loadData () {
        this.loading = true
        const params = Object.assign({}, this.query, {
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        })
        return queryHandoverPage(params).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })
          this.selectedRowKeys = []
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadData()
          }
        }).finally(() => {
          this.loading = false
        })
      },
      loadCounts () {
        const params = Object.assign({}, this.query)
        delete params.status
        return queryHandoverCountByStatus(params).then(res => {
          if (!res.success) {
            return
          }
          const counts = {}
          ;(res.result || []).forEach(item => {
            if (item && item.status) {
              counts[item.status] = Number(item.count) || 0
            }
          })
          this.statusCounts = counts
        })
      },
      loadStat () {
        return queryHandoverStat(this.query).then(res => {
          if (!res.success) {
            return
          }
          const result = res.result || {}
          this.stat = {
            total: result.total || 0,
            migratedCount: result.migratedCount || 0,
            manualCount: result.manualCount || 0,
            archivedCount: result.archivedCount || 0,
            missingAgreementCount: result.missingAgreementCount || 0,
            maintenanceExpiringCount: result.maintenanceExpiringCount || 0,
            maintenanceExpiredCount: result.maintenanceExpiredCount || 0,
            orphanCount: result.orphanCount || 0
          }
        })
      },
      handleSearch (query) {
        this.query = Object.assign({}, query || {})
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
      handleTableChange (pagination) {
        this.pagination = Object.assign({}, this.pagination, {
          current: pagination.current,
          pageSize: pagination.pageSize
        })
        this.loadData()
      },
      handleSelectChange (selectedRowKeys) {
        this.selectedRowKeys = selectedRowKeys
      },
      handleAdd () {
        this.$refs.modal.add()
      },
      handleEdit (record) {
        this.$refs.modal.edit(record)
      },
      handleEditFromDetail (record) {
        this.$refs.detail.handleClose()
        this.$refs.modal.edit(record)
      },
      openDetail (record) {
        this.$refs.detail.open(record)
      },
      openLinkFromRow (record) {
        this.$refs.detail.open(record)
        this.$nextTick(() => {
          this.$refs.detail.openArchivePicker()
        })
      },
      handleStatusChange (record, status) {
        if (!record || !record.id || !status) {
          return
        }
        changeHandoverStatus(record.id, status).then(res => {
          if (res.success) {
            this.$message.success(`状态已变更为「${status}」`)
            this.refresh()
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '状态变更失败')
        })
      },
      handleDetailFilter (params) {
        if (!params) {
          return
        }
        if (this.$refs.search) {
          this.$refs.search.setQuery(params)
        }
        const base = this.$refs.search ? this.$refs.search.getQuery() : Object.assign({}, params)
        this.query = Object.assign({}, base)
        this.statusTab = this.query.status || 'all'
        this.pagination.current = 1
        this.refresh()
      },
      handleDelete (record) {
        deleteHandover(record.id).then(res => {
          if (res.success) {
            this.$message.success('删除成功！')
            this.refresh()
          } else {
            this.$message.warning(res.message)
          }
        })
      },
      handleBatchDelete () {
        if (!this.selectedRowKeys.length) {
          return
        }
        deleteHandoverBatch(this.selectedRowKeys).then(res => {
          if (res.success) {
            this.$message.success('批量删除成功！')
            this.refresh()
          } else {
            this.$message.warning(res.message)
          }
        })
      },
      handleExport () {
        exportHandoverXls(this.query)
        this.$message.success('已开始导出，请稍候…')
      },
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
        let value = record[field.key]
        if ((value === null || value === undefined || value === '') && field.fallback) {
          value = record[field.fallback]
        }
        if (value === null || value === undefined || value === '') {
          return field.key === 'status' ? '待移交' : '—'
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

  .handover-page {
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

  .handover-summary {
    margin-bottom: 12px;
  }

  .stat-card {
    padding: 12px 14px;
    margin-bottom: 12px;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;
    transition: border-color 0.2s ease, box-shadow 0.2s ease;

    &__label {
      font-size: 12px;
      color: @text-muted;
    }

    &__value {
      margin: 4px 0 2px;
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 24px;
      letter-spacing: 1px;
      color: #0f172a;
    }

    &__extra {
      font-size: 12px;
      color: @text-weak;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &.is-primary &__value {
      color: #2e7cf6;
    }

    &.is-success &__value {
      color: #10b981;
    }

    &.is-warning &__value {
      color: #d48806;
    }

    &.is-success,
    &.is-warning {
      cursor: pointer;

      &:hover {
        border-color: #93c5fd;
        box-shadow: 0 4px 12px rgba(46, 124, 246, 0.12);
      }
    }
  }

  .handover-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    margin: 12px 0;

    &__right {
      flex: none;
    }
  }

  .handover-tabs {
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

  .handover-print {
    display: none;
  }
</style>

<!--
  打印样式必须放在非 scoped 的 style 里（要命中 body 与页面外壳，scoped 匹配不到）。
-->
<style lang="less">
  @media print {
    body * {
      visibility: hidden;
    }

    .handover-print,
    .handover-print * {
      visibility: visible;
    }

    .handover-print {
      position: absolute;
      left: 0;
      top: 0;
      display: block !important;
      width: 100%;
      padding: 0 8mm;
      color: #000;
      background: #fff;
    }

    .handover-print .print-title {
      margin: 0 0 6px;
      font-size: 18px;
      font-weight: 600;
      text-align: center;
    }

    .handover-print .print-meta {
      margin-bottom: 10px;
      font-size: 12px;
      text-align: center;
    }

    .handover-print .print-table {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
      font-size: 10px;
    }

    .handover-print .print-table th,
    .handover-print .print-table td {
      padding: 4px 5px;
      border: 1px solid #333;
      word-break: break-word;
      vertical-align: top;
    }

    .handover-print .print-table th {
      background: #f1f5f9;
      font-weight: 600;
      text-align: left;
    }

    .handover-print .print-table--detail {
      font-size: 11px;
    }

    .handover-print .print-table--detail th {
      width: 130px;
      background: #f8fafc;
      text-align: left;
      white-space: nowrap;
    }
  }
</style>
