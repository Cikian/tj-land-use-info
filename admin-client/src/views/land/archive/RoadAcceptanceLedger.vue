<template>
  <a-card :bordered="false" class="ledger-page">
    <!-- ============ 页头 ============ -->
    <div class="page-head no-print">
      <div class="page-head__text">
        <h2 class="page-head__title">道路设施验收及移交资料台账</h2>
        <p class="page-head__desc">
          按台账方式集中管理道路设施的验收与移交资料（方案 2.3.2 第 7 项）。
          13 类资料的勾选矩阵支持快速检索、按「缺少某类资料」一键筛选、台账 Excel 导出与打印；
          资料原件统一存放于档案管理模块，本台账只记录关联。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:ledger:add'" type="primary" icon="plus" @click="handleAdd">新增台账</a-button>
        <a-button v-has="'land:ledger:import'" icon="upload" @click="handleImport">
          批量补录资料
        </a-button>
        <a-button v-has="'land:ledger:export'" icon="download" :disabled="!total" @click="handleExport">
          导出台账 Excel
        </a-button>
        <a-button icon="printer" :disabled="!dataSource.length" @click="handlePrintAll">打印台账</a-button>
        <a-button icon="reload" :loading="loading" @click="refresh">刷新</a-button>
      </div>
    </div>

    <!-- ============ 查询条件 ============ -->
    <ledger-search-form
      ref="search"
      class="no-print"
      :defaultExpanded="hasDeepLink"
      :initialQuery="initialQuery"
      @search="handleSearch" />

    <!-- ============ 指标卡 + 资料归集情况 ============ -->
    <div class="ledger-summary no-print">
      <a-row :gutter="12">
        <a-col :xs="12" :sm="8" :md="4" v-for="card in statCards" :key="card.key">
          <div class="stat-card" :class="card.cls" @click="card.onClick && card.onClick()">
            <div class="stat-card__label">{{ card.label }}</div>
            <div class="stat-card__value">{{ card.value }}</div>
            <div class="stat-card__extra">{{ card.extra }}</div>
          </div>
        </a-col>
      </a-row>

      <div class="material-coverage">
        <div class="material-coverage__head" @click="coverageCollapsed = !coverageCollapsed">
          <span class="material-coverage__title">13 类资料归集情况</span>
          <span class="material-coverage__hint">横向条越长表示该类资料归集得越多；点「筛出缺失」可直接筛出缺这类资料的道路</span>
          <a-icon class="material-coverage__toggle" :type="coverageCollapsed ? 'down' : 'up'" />
        </div>
        <div v-show="!coverageCollapsed" class="material-coverage__body">
          <div v-for="item in coverage" :key="item.name" class="material-coverage__row">
            <span class="material-coverage__name" :title="item.name">{{ item.name }}</span>
            <div class="material-coverage__bar">
              <div class="material-coverage__bar-inner" :style="{ width: coveragePercent(item.count) + '%' }"></div>
            </div>
            <span class="material-coverage__count">{{ item.count }}</span>
            <a class="material-coverage__action" @click="handleFilterMissing(item)">筛出缺失</a>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 状态页签 + 矩阵开关 ============ -->
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
        <a-tooltip title="在表格里展开 13 列逐项勾选（√/×），用于核对「哪类资料普遍没交」；列很宽，默认收起">
          <span class="ledger-bar__switch">
            <a-switch v-model="showMaterials" size="small" />
            <span class="ledger-bar__switch-label">显示资料矩阵</span>
          </span>
        </a-tooltip>
        <a-button
          v-has="'land:ledger:delete'"
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

    <!-- ============ 台账表格 ============ -->
    <div class="list-panel no-print">
      <div class="list-panel__toolbar">
        <span class="list-panel__total">
          共 <b>{{ total }}</b> 条台账记录
          <span v-if="currentFilterText" class="list-panel__filter">当前筛选：{{ currentFilterText }}</span>
        </span>
        <span class="list-panel__hint">
          台账编号与序号按当前查询条件连续编号；黄色提示表示「状态与资料不一致」或「出让宗地待核对」
        </span>
      </div>

      <ledger-table
        :dataSource="dataSource"
        :loading="loading"
        :pagination="pagination"
        :scrollY="tableScrollY"
        :materials="materials"
        :showMaterials="showMaterials"
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
    <ledger-modal ref="modal" :materialDefs="materials" @ok="handleSaved" />
    <ledger-detail-modal
      ref="detail"
      @edit="handleEditFromDetail"
      @status-change="handleStatusChange"
      @filter="handleDetailFilter"
      @print="handlePrintRow" />
    <ledger-import-modal ref="importer" @ok="refresh" />

    <!-- ============ 打印区（屏幕上隐藏，打印时只显示这一块） ============ -->
    <div class="ledger-print">
      <h1 class="print-title">
        {{ printMode === 'row' && printRow ? '道路设施验收及移交资料台账（单项）' : '道路设施验收及移交资料台账' }}
      </h1>
      <div class="print-meta">
        打印时间：{{ printTime }} | 记录数：{{ printMode === 'row' ? 1 : dataSource.length }} 条
        <span v-if="printMode !== 'row' && currentFilterText"> | 筛选：{{ currentFilterText }}</span>
      </div>

      <!-- 单项：纵向明细（含 13 类资料逐项） -->
      <table v-if="printMode === 'row' && printRow" class="print-table print-table--detail">
        <tbody>
          <tr v-for="field in printRowFields" :key="field.key">
            <th>{{ field.label }}</th>
            <td>{{ printRowCell(printRow, field) }}</td>
          </tr>
          <tr>
            <th>验收及移交资料（13 类）</th>
            <td>{{ printRowMaterialText(printRow) }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 整页：横向表格 -->
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
  import Vue from 'vue'
  import LedgerSearchForm from './modules/LedgerSearchForm'
  import LedgerTable from './modules/LedgerTable'
  import LedgerModal from './modules/LedgerModal'
  import LedgerDetailModal from './modules/LedgerDetailModal'
  import LedgerImportModal from './modules/LedgerImportModal'
  import {
    LEDGER_MATERIALS_FALLBACK,
    LEDGER_STATUS,
    changeLedgerStatus,
    deleteLedger,
    deleteLedgerBatch,
    exportLedgerXls,
    materialSummary,
    queryLedgerCountByStatus,
    queryLedgerMaterials,
    queryLedgerPage,
    queryLedgerStat
  } from '@/api/land/ledger'

  /** 资料矩阵开关的记忆键（记住每个用户上次的选择） */
  const SHOW_MATERIALS_KEY = 'land:ledger:showMaterials'

  /**
   * 道路设施验收及移交资料台账（方案 2.3.2 第 7 项）
   *
   * 需求原文：「实现对道路设施验收及移交资料台账的管理，可进行快速检索查询。」
   *
   * 页面结构（对应清单 §6.3.7 的「表格密度要高、字段以是否有某资料的勾选矩阵为主」）：
   *   · 5 张指标卡：台账总数 / 迁移·人工 / 已移交 / 资料齐全 / 宗地待核对；
   *   · 13 类资料归集情况：横向条形图，点「筛出缺失」直接按该类资料缺失筛选台账；
   *   · 状态页签（未验收/验收中/已验收/已移交）带数量角标 + **资料矩阵开关**；
   *   · 高密度台账表格（序号与台账编号冻结；开关打开时展开 13 列逐项勾选）；
   *   · 打印区（整页横向表 / 单项纵向明细，打印时只显示它）。
   *
   * ★ 支持从其它页面带条件下钻（双向跳转的落点）：
   *   `/land/archive/ledger?facilityId=xxx`、`?crzdbh=xxx`、`?archiveId=xxx`、`?missingMaterial=hasJgwj`、`?status=已移交`
   *   条件会自动填进查询面板（`initialQuery`）并展开更多条件，用户一眼能看到「为什么只有这些数据」。
   */
  export default {
    name: 'RoadAcceptanceLedger',
    components: { LedgerSearchForm, LedgerTable, LedgerModal, LedgerDetailModal, LedgerImportModal },
    data () {
      return {
        loading: false,
        /** 表体滚动高度（px）：由 updateTableScrollY 按视口剩余高度算出，见该方法注释 */
        tableScrollY: 520,
        /** 兜底预留：把「实测后仍存在的溢出」累计进来，只增不减（见 killResidualScroll） */
        extraReserve: 0,
        statusTab: 'all',
        statusCounts: {},
        stat: { total: 0, migratedCount: 0, manualCount: 0, orphanCount: 0, fullMaterialCount: 0, emptyMaterialCount: 0 },
        coverage: [],
        coverageCollapsed: false,
        materials: LEDGER_MATERIALS_FALLBACK,
        /** 是否在表格里展开 13 列资料矩阵（记住上次选择） */
        showMaterials: Vue.ls.get(SHOW_MATERIALS_KEY) === true,
        /** 从路由带入的初始条件（只用于播种查询面板，之后由面板自己维护） */
        initialQuery: {},
        hasDeepLink: false,
        query: {},
        dataSource: [],
        total: 0,
        selectedRowKeys: [],
        printMode: 'all',
        printRow: null,
        printTime: '',
        /** 整页打印的横向列 */
        printFields: [
          { key: 'seq', label: '序号' },
          { key: 'ledgerNo', label: '台账编号' },
          { key: 'roadName', label: '道路名称' },
          { key: 'xzqh', label: '行政区划', fallback: 'gnq' },
          { key: 'crzdbh', label: '出让宗地编号' },
          { key: 'dkmc', label: '地块名称' },
          { key: 'acceptanceType', label: '验收类型' },
          { key: 'acceptanceDate', label: '验收日期' },
          { key: 'acceptanceResult', label: '验收结果' },
          { key: 'completeDate', label: '竣工日期' },
          { key: 'handoverDate', label: '移交日期' },
          { key: 'receiveUnit', label: '接收管养单位' },
          { key: 'material', label: '资料归集' },
          { key: 'status', label: '状态' }
        ],
        /** 单项打印的纵向字段 */
        printRowFields: [
          { key: 'ledgerNo', label: '台账编号' },
          { key: 'roadName', label: '道路名称' },
          { key: 'ptsslb', label: '配套设施类别' },
          { key: 'xzqh', label: '行政区划', fallback: 'gnq' },
          { key: 'crzdbh', label: '出让宗地编号' },
          { key: 'dkmc', label: '地块名称' },
          { key: 'dldj', label: '道路等级' },
          { key: 'jsdw', label: '建设单位' },
          { key: 'sgdw', label: '施工单位' },
          { key: 'jldw', label: '监理单位' },
          { key: 'acceptanceType', label: '验收类型' },
          { key: 'acceptanceNo', label: '验收单编号' },
          { key: 'acceptanceDate', label: '验收日期' },
          { key: 'acceptanceOrg', label: '验收组织单位' },
          { key: 'acceptanceResult', label: '验收结果' },
          { key: 'completeDate', label: '实际竣工日期' },
          { key: 'handoverUnit', label: '移交单位' },
          { key: 'receiveUnit', label: '接收管养单位' },
          { key: 'handoverDate', label: '移交日期' },
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
        const all = LEDGER_STATUS.reduce((sum, name) => sum + (Number(this.statusCounts[name]) || 0), 0)
        return [{ value: 'all', label: '全部', count: all }].concat(
          LEDGER_STATUS.map(name => ({ value: name, label: name, count: Number(this.statusCounts[name]) || 0 }))
        )
      },
      statCards () {
        return [
          {
            key: 'total',
            label: '台账总数',
            value: this.stat.total || 0,
            extra: '全部道路设施台账',
            cls: ''
          },
{
            key: 'manual',
            label: '迁移 / 人工',
            value: `${this.stat.migratedCount || 0} / ${this.stat.manualCount || 0}`,
            extra: '由旧库迁移 / 页面新增',
            cls: ''
          },
{
            key: 'handedOver',
            label: '已移交',
            value: this.statusCounts['已移交'] || 0,
            extra: `已验收 ${this.statusCounts['已验收'] || 0}`,
            cls: 'is-success',
            onClick: () => this.handleTabChange('已移交')
          },
{
            key: 'full',
            label: '资料齐全（13/13）',
            value: this.stat.fullMaterialCount || 0,
            extra: `一类未归集 ${this.stat.emptyMaterialCount || 0}`,
            cls: 'is-primary'
          },
{
            key: 'orphan',
            label: '宗地待核对',
            value: this.stat.orphanCount || 0,
            extra: '出让宗地在宗地表无匹配',
            cls: this.stat.orphanCount ? 'is-warning' : ''
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
        if (this.query.ledgerNo) {
          parts.push(`台账编号≈${this.query.ledgerNo}`)
        }
        if (this.query.xzqh) {
          parts.push(`行政区划=${this.query.xzqh}`)
        }
        if (this.query.gnq) {
          parts.push(`功能区=${this.query.gnq}`)
        }
        if (this.query.acceptanceType) {
          parts.push(`验收类型=${this.query.acceptanceType}`)
        }
        if (this.query.acceptanceResult) {
          parts.push(`验收结果=${this.query.acceptanceResult}`)
        }
        if (this.query.missingMaterial) {
          const hit = this.materials.filter(item => item.key === this.query.missingMaterial)[0]
          parts.push(`缺少资料=${hit ? hit.label : this.query.missingMaterial}`)
        }
        if (this.query.facilityId) {
          parts.push('已按配套项目过滤')
        }
        if (this.query.archiveId) {
          parts.push('已按关联档案过滤')
        }
        if (this.query.crzdbh) {
          parts.push(`宗地编号≈${this.query.crzdbh}`)
        }
        if (this.query.keyword) {
          parts.push(`关键词≈${this.query.keyword}`)
        }
        return parts.join('，')
      }
    },
    watch: {
      showMaterials (value) {
        // 记住用户的选择：下次进页面直接是他习惯的形态
        Vue.ls.set(SHOW_MATERIALS_KEY, value === true)
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
      // 13 类资料定义以后端为准（后端加一类资料时页头、矩阵列与筛选自动跟上）
      queryLedgerMaterials().then(res => {
        if (res && res.success && Array.isArray(res.result) && res.result.length) {
          this.materials = res.result
        }
      }).catch(() => { /* 静默：保留本地兜底列表 */ })

      // ★ 支持从档案 / 配套项目 / 统计图带条件下钻
      //   /land/archive/ledger?facilityId=xxx&missingMaterial=hasJgwj&status=已移交
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
      materialSummary,
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
        return queryLedgerPage(params).then(res => {
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
/** 状态页签的数量：与列表同条件、但不含 status（否则切一次页签数量就只剩一个） */
      loadCounts () {
        const params = Object.assign({}, this.query)
        delete params.status
        return queryLedgerCountByStatus(params).then(res => {
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
        return queryLedgerStat(this.query).then(res => {
          if (!res.success) {
            return
          }
          const result = res.result || {}
          this.stat = {
            total: result.total || 0,
            migratedCount: result.migratedCount || 0,
            manualCount: result.manualCount || 0,
            orphanCount: result.orphanCount || 0,
            fullMaterialCount: result.fullMaterialCount || 0,
            emptyMaterialCount: result.emptyMaterialCount || 0
          }
          this.coverage = result.byMaterial || []
        })
      },
/** 条形图宽度：以当前条件下的总数为基准 */
      coveragePercent (count) {
        const base = Math.max(1, this.stat.total || 0)
        return Math.min(100, Math.round((Number(count) || 0) / base * 100))
      },
// ------------------------------------------------------------------
      // 筛选
      // ------------------------------------------------------------------
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
/** 从「13 类资料归集情况」点进来：筛出缺该类资料的道路 */
      handleFilterMissing (item) {
        const hit = this.materials.filter(material => material.label === item.name)[0]
        if (!hit) {
          this.$message.warning('未识别的资料类别')
          return
        }
        const base = this.$refs.search ? this.$refs.search.getQuery() : {}
        base.missingMaterial = hit.key
        if (this.$refs.search) {
          // 同步进查询面板，避免「列表被筛了但表单上看不出来」
          this.$refs.search.setQuery({ missingMaterial: hit.key })
        }
        this.query = base
        this.statusTab = this.query.status || 'all'
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
/**
       * 详情弹窗里的「按该配套项目/宗地查台账」联动：把条件写进查询面板并立刻查询。
       * ★ 不走路由：跳到同一路由只换 query 时组件不会重建，条件会丢（见详情弹窗注释）。
       */
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
/** 表格行上的「关联档案」：先开详情，再自动打开挑档案弹窗 */
      openLinkFromRow (record) {
        this.$refs.detail.open(record)
        this.$nextTick(() => {
          this.$refs.detail.openArchivePicker()
        })
      },
/** 状态变更（行内下拉 / 详情弹窗共用） */
      handleStatusChange (record, status) {
        if (!record || !record.id || !status) {
          return
        }
        changeLedgerStatus(record.id, status).then(res => {
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
      handleDelete (record) {
        deleteLedger(record.id).then(res => {
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
        deleteLedgerBatch(this.selectedRowKeys).then(res => {
          if (res.success) {
            this.$message.success('批量删除成功！')
            this.refresh()
          } else {
            this.$message.warning(res.message)
          }
        })
      },
      handleExport () {
        exportLedgerXls(this.query)
        this.$message.success('已开始导出台账，请稍候…')
      },
/** ★批量补录资料（Excel 导入，模块 C） */
      handleImport () {
        if (this.$refs.importer) {
          this.$refs.importer.open()
        }
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
        if (field.key === 'material') {
          return materialSummary(record)
        }
        let value = record[field.key]
        if ((value === null || value === undefined || value === '') && field.fallback) {
          value = record[field.fallback]
        }
        if (value === null || value === undefined || value === '') {
          return field.key === 'status' ? '未验收' : '—'
        }
        return value
      },
      printRowCell (record, field) {
        return this.printCell(record, field)
      },
/** 单项打印：13 类资料逐项列出「已归集 / 待补」 */
      printRowMaterialText (record) {
        if (!record) {
          return '—'
        }
        const done = []
        const todo = []
        this.materials.forEach(item => {
          const label = `${this.padSeq(item.seq)} ${item.label}`
          if (Number(record[item.key]) === 1) {
            done.push(label)
          } else {
            todo.push(label)
          }
        })
        const total = this.materials.length
        const parts = [`已归集 ${done.length}/${total}`]
        if (done.length) {
          parts.push(`已归集：${done.join('；')}`)
        }
        if (todo.length) {
          parts.push(`待补：${todo.join('；')}`)
        }
        return parts.join('　')
      },
      padSeq (seq) {
        return seq < 10 ? `0${seq}` : `${seq}`
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

  .ledger-page {
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
  .ledger-summary {
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

  /* ---------------- 13 类资料归集情况 ---------------- */
  .material-coverage {
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
      width: 130px;
      color: @text-muted;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
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
  .ledger-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    margin: 12px 0;

    &__right {
      flex: none;
      display: flex;
      align-items: center;
      gap: 12px;
    }

    &__switch {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
    }

    &__switch-label {
      font-size: 12px;
      color: @text-muted;
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

  /* 打印区在屏幕上隐藏（打印规则放在非 scoped 的 style 块里） */
  .ledger-print {
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

    .ledger-print,
    .ledger-print * {
      visibility: visible;
    }

    .ledger-print {
      position: absolute;
      left: 0;
      top: 0;
      display: block !important;
      width: 100%;
      padding: 0 8mm;
      color: #000;
      background: #fff;
    }

    .ledger-print .print-title {
      margin: 0 0 6px;
      font-size: 18px;
      font-weight: 600;
      text-align: center;
    }

    .ledger-print .print-meta {
      margin-bottom: 10px;
      font-size: 12px;
      text-align: center;
    }

    .ledger-print .print-table {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
      font-size: 10px;
    }

    .ledger-print .print-table th,
    .ledger-print .print-table td {
      padding: 4px 5px;
      border: 1px solid #333;
      word-break: break-word;
      vertical-align: top;
    }

    .ledger-print .print-table th {
      background: #f1f5f9;
      font-weight: 600;
      text-align: left;
    }

    /* 单项打印：左侧标签列固定窄宽，右侧内容自适应 */
    .ledger-print .print-table--detail {
      font-size: 11px;
    }

    .ledger-print .print-table--detail th {
      width: 130px;
      background: #f8fafc;
      text-align: left;
      white-space: nowrap;
    }
  }
</style>
