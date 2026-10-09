<template>
  <a-card :bordered="false" class="completion-page">
    <!-- ============ 页头 ============ -->
    <div class="page-head no-print">
      <div class="page-head__text">
        <h2 class="page-head__title">竣工验收项目历史工程资料数字化档案</h2>
        <p class="page-head__desc">
          集中管理存量历史项目的工程资料数字化档案（方案 2.3.2 第 8 项）：登记项目与参建信息、
          追踪数字化状态（未数字化/数字化中/已数字化）与页数、扫描分辨率，扫描件通过「关联扫描件」
          挂到档案管理模块；支持快速检索与按条件导出档案信息。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:completion:add'" type="primary" icon="plus" @click="handleAdd">新增历史档案</a-button>
        <a-button v-has="'land:completion:export'" icon="download" :disabled="!total" @click="handleExport">
          导出档案信息
        </a-button>
        <a-button icon="printer" :disabled="!dataSource.length" @click="handlePrintAll">打印档案清单</a-button>
        <a-button icon="reload" :loading="loading" @click="refresh">刷新</a-button>
      </div>
    </div>

    <!-- ============ 查询条件 ============ -->
    <completion-search-form ref="search" class="no-print" @search="handleSearch" />

    <!-- ============ 统计卡 + 竣工年度分布 ============ -->
    <div class="completion-summary no-print">
      <a-row :gutter="12">
        <a-col :xs="12" :sm="8" :md="4" v-for="card in statCards" :key="card.key">
          <div class="stat-card" :class="card.cls" @click="card.onClick && card.onClick()">
            <div class="stat-card__label">{{ card.label }}</div>
            <div class="stat-card__value">{{ card.value }}</div>
            <div class="stat-card__extra">{{ card.extra }}</div>
          </div>
        </a-col>
      </a-row>

      <div class="year-coverage">
        <div class="year-coverage__head" @click="yearCollapsed = !yearCollapsed">
          <span class="year-coverage__title">按竣工年度分布</span>
          <span class="year-coverage__hint">历史档案盘点最常用的维度；点条可按该年度筛选</span>
          <a-icon class="year-coverage__toggle" :type="yearCollapsed ? 'down' : 'up'" />
        </div>
        <div v-show="!yearCollapsed" class="year-coverage__body">
          <a-empty v-if="!completeYears.length" description="当前条件下没有带竣工日期的档案" />
          <div v-for="item in completeYears" :key="item.name" class="year-coverage__row">
            <span class="year-coverage__name">{{ item.name }} 年</span>
            <div class="year-coverage__bar">
              <div class="year-coverage__bar-inner" :style="{ width: yearPercent(item.count) + '%' }"></div>
            </div>
            <span class="year-coverage__count">{{ item.count }}</span>
            <a class="year-coverage__action" @click="handleFilterYear(item.name)">筛选</a>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 数字化状态页签 ============ -->
    <div class="completion-bar no-print">
      <div class="completion-tabs">
        <a-tabs :activeKey="statusTab" :tabBarStyle="{ marginBottom: '0' }" @change="handleTabChange">
          <a-tab-pane v-for="tab in statusTabs" :key="tab.value">
            <template slot="tab">
              <span class="completion-tabs__label">{{ tab.label }}</span>
              <span class="completion-tabs__count">{{ tab.count }}</span>
            </template>
          </a-tab-pane>
        </a-tabs>
      </div>
      <div class="completion-bar__right">
        <a-button
          v-has="'land:completion:delete'"
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

    <!-- ============ 档案表格 ============ -->
    <div class="list-panel no-print">
      <div class="list-panel__toolbar">
        <span class="list-panel__total">
          共 <b>{{ total }}</b> 条历史档案
          <span v-if="currentFilterText" class="list-panel__filter">当前筛选：{{ currentFilterText }}</span>
        </span>
        <span class="list-panel__hint">
          序号与档案编号按当前查询条件连续编号；「数字化状态」列出现黄色警告说明状态为已数字化但未挂扫描件
        </span>
      </div>

      <completion-table
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
        @remove="handleDelete" />
    </div>

    <!-- ============ 弹窗 ============ -->
    <completion-modal ref="modal" @ok="handleSaved" />
    <completion-detail-modal ref="detail" @edit="handleEditFromDetail" />

    <!-- ============ 打印区（屏幕上隐藏，打印时只显示这一块） ============ -->
    <div class="completion-print">
      <h1 class="print-title">竣工验收项目历史工程资料数字化档案清单</h1>
      <div class="print-meta">
        打印时间：{{ printTime }} | 记录数：{{ dataSource.length }} 条
        <span v-if="currentFilterText"> | 筛选：{{ currentFilterText }}</span>
      </div>
      <table class="print-table">
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
  import CompletionSearchForm from './modules/CompletionSearchForm'
  import CompletionTable from './modules/CompletionTable'
  import CompletionModal from './modules/CompletionModal'
  import CompletionDetailModal from './modules/CompletionDetailModal'
  import {
    DIGITIZE_STATUS,
    deleteCompletion,
    deleteCompletionBatch,
    exportCompletionXls,
    formatAmount,
    formatCount,
    queryCompletionCountByStatus,
    queryCompletionPage,
    queryCompletionStat
  } from '@/api/land/completion'

  /**
   * 竣工验收项目历史工程资料数字化档案（方案 2.3.2 第 8 项）
   *
   * 需求原文：「实现对竣工验收项目历史工程资料的管理，可进行快速检索查询，可快速导出档案信息。」
   *
   * 页面结构：
   *   · 5 张指标卡：档案总数 / 已数字化 / 已挂扫描件 / 总页数 / 投资额合计（万元）；
   *   · 按竣工年度分布：横向条形，点「筛选」直接按该年度过滤（历史档案盘点的主视角）；
   *   · 数字化状态页签（未数字化/数字化中/已数字化）带数量角标；
   *   · 高密度档案表格（横向滚动，序号与档案编号冻结）；
   *   · 打印区（独立，屏幕上隐藏）。
   *
   * 数据全部来自 /land/archive/completion/*；列表、统计、导出共用同一套查询条件，
   * 因此「屏幕上看到什么，指标卡、年度分布与导出就是什么」。
   */
  export default {
    name: 'CompletionArchiveList',
    components: { CompletionSearchForm, CompletionTable, CompletionModal, CompletionDetailModal },
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
          archivedCount: 0,
          digitizedCount: 0,
          pageTotal: 0,
          fileTotal: 0,
          investTotal: 0
        },
        completeYears: [],
        yearCollapsed: false,
        query: {},
        dataSource: [],
        total: 0,
        selectedRowKeys: [],
        printTime: '',
        printFields: [
          { key: 'seq', label: '序号' },
          { key: 'archiveNo', label: '档案编号' },
          { key: 'projectName', label: '历史项目名称' },
          { key: 'projectCode', label: '项目编号' },
          { key: 'xzqh', label: '行政区划' },
          { key: 'projectType', label: '项目类型' },
          { key: 'buildUnit', label: '建设单位' },
          { key: 'constructUnit', label: '施工单位' },
          { key: 'completeDate', label: '竣工日期' },
          { key: 'acceptanceDate', label: '验收日期' },
          { key: 'investAmount', label: '投资额(万元)', type: 'amount' },
          { key: 'retention', label: '保管期限' },
          { key: 'digitizeStatus', label: '数字化状态' },
          { key: 'digitizeDate', label: '数字化完成日期' },
          { key: 'pageCount', label: '总页数', type: 'count' },
          { key: 'scanDpi', label: '扫描分辨率', suffix: ' DPI' },
          { key: 'archiveCount', label: '扫描件数' }
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
        const all = DIGITIZE_STATUS.reduce((sum, name) => sum + (Number(this.statusCounts[name]) || 0), 0)
        return [{ value: 'all', label: '全部', count: all }].concat(
          DIGITIZE_STATUS.map(name => ({ value: name, label: name, count: Number(this.statusCounts[name]) || 0 }))
        )
      },
      statCards () {
        return [
          {
            key: 'total',
            label: '历史档案总数',
            value: this.stat.total || 0,
            extra: `文件数合计 ${formatCount(this.stat.fileTotal || 0)}`,
            cls: ''
          },
{
            key: 'digitized',
            label: '已数字化',
            value: this.stat.digitizedCount || 0,
            extra: `数字化中 ${this.statusCounts['数字化中'] || 0} ｜ 未数字化 ${this.statusCounts['未数字化'] || 0}`,
            cls: 'is-success',
            onClick: () => this.handleTabChange('已数字化')
          },
{
            key: 'archived',
            label: '已挂扫描件',
            value: this.stat.archivedCount || 0,
            extra: '已关联 t_archive 的档案数',
            cls: 'is-primary'
          },
{
            key: 'pages',
            label: '总页数',
            value: formatCount(this.stat.pageTotal || 0),
            extra: '当前条件下全部档案的页数合计',
            cls: ''
          },
{
            key: 'invest',
            label: '投资额合计（万元）',
            value: formatAmount(this.stat.investTotal || 0),
            extra: '历史项目投资额汇总',
            cls: ''
          }
        ]
      },
      currentFilterText () {
        const parts = []
        if (this.query.digitizeStatus) {
          parts.push(`数字化状态=${this.query.digitizeStatus}`)
        }
        if (this.query.projectName) {
          parts.push(`项目名称≈${this.query.projectName}`)
        }
        if (this.query.archiveNo) {
          parts.push(`档案编号≈${this.query.archiveNo}`)
        }
        if (this.query.xzqh) {
          parts.push(`行政区划=${this.query.xzqh}`)
        }
        if (this.query.projectType) {
          parts.push(`项目类型=${this.query.projectType}`)
        }
        if (this.query.buildUnit) {
          parts.push(`建设单位≈${this.query.buildUnit}`)
        }
        if (this.query.retention) {
          parts.push(`保管期限=${this.query.retention}`)
        }
        if (this.query.hasArchive === true) {
          parts.push('仅已挂扫描件')
        } else if (this.query.hasArchive === false) {
          parts.push('仅未挂扫描件')
        }
        if (this.query.scanDpi) {
          parts.push(`扫描分辨率=${this.query.scanDpi}DPI`)
        }
        if (this.query.beginCompleteDate || this.query.endCompleteDate) {
          parts.push(`竣工日期 ${this.query.beginCompleteDate || ''} ~ ${this.query.endCompleteDate || ''}`)
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
      // 支持从其它页面带条件下钻：/land/archive/completion?digitizeStatus=已数字化
      const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
      this.query = Object.assign({}, routeQuery)
      if (this.query.digitizeStatus) {
        this.statusTab = this.query.digitizeStatus
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
      formatCount,
      formatAmount,
      // ------------------------------------------------------------------
      // 数据
      // ------------------------------------------------------------------
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
        return queryCompletionPage(params).then(res => {
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
/** 状态页签的数量：与列表同条件、但不含 digitizeStatus（否则切一次页签数量就只剩一个） */
      loadCounts () {
        const params = Object.assign({}, this.query)
        delete params.digitizeStatus
        return queryCompletionCountByStatus(params).then(res => {
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
        return queryCompletionStat(this.query).then(res => {
          if (!res.success) {
            return
          }
          const result = res.result || {}
          this.stat = {
            total: result.total || 0,
            archivedCount: result.archivedCount || 0,
            digitizedCount: result.digitizedCount || 0,
            pageTotal: result.pageTotal || 0,
            fileTotal: result.fileTotal || 0,
            investTotal: result.investTotal || 0
          }
          this.completeYears = result.byCompleteYear || []
        })
      },
/** 年度条形宽度：以当前条件下档案总数（或最大年度值）为基准 */
      yearPercent (count) {
        const base = Math.max(1, Number(this.stat.total) || 0)
        return Math.min(100, Math.round((Number(count) || 0) / base * 100))
      },
// ------------------------------------------------------------------
      // 筛选
      // ------------------------------------------------------------------
      handleSearch (query) {
        this.query = Object.assign({}, query || {})
        this.statusTab = this.query.digitizeStatus || 'all'
        this.pagination.current = 1
        this.refresh()
      },
      handleTabChange (key) {
        this.statusTab = key
        const base = this.$refs.search ? this.$refs.search.getQuery() : {}
        if (key === 'all') {
          delete base.digitizeStatus
        } else {
          base.digitizeStatus = key
        }
        this.query = base
        this.pagination.current = 1
        this.refresh()
      },
/** 从「按竣工年度分布」点进来：按该年度筛选竣工日期 */
      handleFilterYear (year) {
        const base = this.$refs.search ? this.$refs.search.getQuery() : {}
        base.beginCompleteDate = `${year}-01-01`
        base.endCompleteDate = `${year}-12-31`
        // 同步到查询面板的日期选择器，避免「列表被筛了但表单上看不出来」
        if (this.$refs.search) {
          this.$refs.search.query.beginCompleteDate = base.beginCompleteDate
          this.$refs.search.query.endCompleteDate = base.endCompleteDate
          this.$refs.search.completeRange = [base.beginCompleteDate, base.endCompleteDate]
        }
        this.query = base
        this.statusTab = this.query.digitizeStatus || 'all'
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
// ------------------------------------------------------------------
      // 行操作
      // ------------------------------------------------------------------
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
      handleSaved () {
        this.refresh()
      },
      openDetail (record) {
        this.$refs.detail.open(record)
      },
/** 表格行上的「关联扫描件」：先开详情，再自动打开挑档案弹窗 */
      openLinkFromRow (record) {
        this.$refs.detail.open(record)
        this.$nextTick(() => {
          this.$refs.detail.openArchivePicker()
        })
      },
      handleDelete (record) {
        deleteCompletion(record.id).then(res => {
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
        deleteCompletionBatch(this.selectedRowKeys).then(res => {
          if (res.success) {
            this.$message.success('批量删除成功！')
            this.refresh()
          } else {
            this.$message.warning(res.message)
          }
        })
      },
      handleExport () {
        exportCompletionXls(this.query)
        this.$message.success('已开始导出档案信息，请稍候…')
      },
// ------------------------------------------------------------------
      // 打印
      // ------------------------------------------------------------------
      handlePrintAll () {
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
        if (value === null || value === undefined || value === '') {
          return field.key === 'digitizeStatus' ? '未数字化' : '—'
        }
        if (field.type === 'amount') {
          return formatAmount(value)
        }
        if (field.type === 'count') {
          return formatCount(value)
        }
        return field.suffix ? `${value}${field.suffix}` : value
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

  .completion-page {
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

  /* ---------------- 指标卡 ---------------- */
  .completion-summary {
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

    &.is-success {
      cursor: pointer;

      &:hover {
        border-color: #93c5fd;
        box-shadow: 0 4px 12px rgba(46, 124, 246, 0.12);
      }
    }
  }

  /* ---------------- 按竣工年度分布 ---------------- */
  .year-coverage {
    padding: 12px 16px;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;

    &__head {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 10px;
      cursor: pointer;
    }

    &__title {
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
    }

    &__hint {
      font-size: 12px;
      color: @text-weak;
    }

    &__toggle {
      margin-left: auto;
      color: @text-weak;
    }

    &__body {
      margin-top: 10px;
    }

    &__row {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 3px 0;
      font-size: 12px;
    }

    &__name {
      flex: none;
      width: 90px;
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      color: @text-muted;
    }

    &__bar {
      flex: 1 1 auto;
      height: 10px;
      background: #f1f5f9;
      border-radius: 5px;
      overflow: hidden;
    }

    &__bar-inner {
      height: 100%;
      background: linear-gradient(90deg, #2e7cf6, #22d3ee);
      border-radius: 5px;
      transition: width 0.3s ease;
    }

    &__count {
      flex: none;
      width: 52px;
      text-align: right;
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      color: #0f172a;
    }

    &__action {
      flex: none;
      font-size: 12px;
      color: #2e7cf6;
    }
  }

  /* ---------------- 状态页签 ---------------- */
  .completion-bar {
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

  .completion-tabs {
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

  /* 打印区在屏幕上隐藏（打印规则放在非 scoped 的 style 块里） */
  .completion-print {
    display: none;
  }
</style>

<!--
  打印样式必须放在非 scoped 的 style 里：
  ① 需要命中 body 以及本组件之外的页面外壳（顶栏 / 侧边栏），scoped 属性选择器匹配不到；
  ② 打印时把 body 下所有内容设为不可见，只让打印区可见（visibility 不影响布局宽度）。
-->
<style lang="less">
  @media print {
    body * {
      visibility: hidden;
    }

    .completion-print,
    .completion-print * {
      visibility: visible;
    }

    .completion-print {
      position: absolute;
      left: 0;
      top: 0;
      display: block !important;
      width: 100%;
      padding: 0 8mm;
      color: #000;
      background: #fff;
    }

    .completion-print .print-title {
      margin: 0 0 6px;
      font-size: 18px;
      font-weight: 600;
      text-align: center;
    }

    .completion-print .print-meta {
      margin-bottom: 10px;
      font-size: 12px;
      text-align: center;
    }

    .completion-print .print-table {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
      font-size: 9px;
    }

    .completion-print .print-table th,
    .completion-print .print-table td {
      padding: 4px 5px;
      border: 1px solid #333;
      word-break: break-word;
      vertical-align: top;
    }

    .completion-print .print-table th {
      background: #f1f5f9;
      font-weight: 600;
      text-align: left;
    }
  }
</style>
