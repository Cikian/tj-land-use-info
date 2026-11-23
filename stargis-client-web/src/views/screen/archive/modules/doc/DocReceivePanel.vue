<template>
  <!--
    DocReceivePanel 收文管理
    --------------------------------
    收文登记与收文中心内的流转：登记（可指定承办人）→ 承办 → 办结，
    支持流转过程中转办与退回，每步都会留下「谁、何时、什么意见」的痕迹。
    办结时可选择是否归档到档案管理。

    三段式布局与「档案维护」保持一致：统计卡（兼作快捷筛选）→ 检索条件 → 列表。

    无障碍说明：
      统计卡是异步加载的。按「不要只播报一个裸数字」的原则，
      这里用一个视觉隐藏的 role="status" 区域播报完整语义（「收文统计已更新：共 N 条，我的待办 M 条」），
      卡片本身给读屏的是「标签 + 数值」的组合文本，避免读屏只念出一串数字。
      统计卡同时是筛选按钮，因此用 aria-pressed 暴露当前是否处于激活状态。
  -->
  <div class="doc-receive">
    <!-- ================= 统计卡（点击做快捷筛选） ================= -->
    <div class="doc-receive__stats" role="group" aria-label="收文统计与快捷筛选">
      <button
        v-for="card in statCards"
        :key="card.key || 'all'"
        type="button"
        class="doc-receive__stat"
        :class="[`is-${card.tone}`, { 'is-active': activeStat === card.key }]"
        :aria-pressed="activeStat === card.key ? 'true' : 'false'"
        @click="handleStatClick(card.key)"
      >
        <span class="doc-receive__stat-label">{{ card.label }}</span>
        <span class="doc-receive__stat-value">{{ card.value }}</span>
      </button>
    </div>

    <!-- 读屏播报：完整语义而不是裸数字 -->
    <p class="doc-receive__sr-only" role="status" aria-live="polite" aria-atomic="true">
      {{ statAnnouncement }}
    </p>

    <!-- ================= 检索条件 ================= -->
    <screen-panel class="doc-receive__search" title="收文检索" collapsible>
      <div class="doc-receive__grid">
        <screen-field label="收文登记号" :label-width="'84px'" html-for="dr-no">
          <screen-input id="dr-no" v-model="query.docNo" clearable placeholder="模糊匹配" @enter="handleSearch" />
        </screen-field>

        <screen-field label="文件标题" :label-width="'84px'" html-for="dr-title">
          <screen-input id="dr-title" v-model="query.docTitle" clearable placeholder="模糊匹配" @enter="handleSearch" />
        </screen-field>

        <screen-field label="来文单位" :label-width="'84px'" html-for="dr-from">
          <screen-input id="dr-from" v-model="query.fromDept" clearable placeholder="模糊匹配" @enter="handleSearch" />
        </screen-field>

        <screen-field label="状态" :label-width="'84px'">
          <screen-select v-model="query.status" :options="statusOptions" placeholder="全部状态" aria-label="收文状态" />
        </screen-field>

        <template v-if="expanded">
          <screen-field label="来文字号" :label-width="'84px'" html-for="dr-fromno">
            <screen-input id="dr-fromno" v-model="query.fromDocNo" clearable placeholder="模糊匹配" @enter="handleSearch" />
          </screen-field>

          <screen-field label="配套项目" :label-width="'84px'" html-for="dr-ptxm">
            <screen-input id="dr-ptxm" v-model="query.ptxmmc" clearable placeholder="项目名称，模糊匹配" @enter="handleSearch" />
          </screen-field>

          <screen-field label="宗地编号" :label-width="'84px'" html-for="dr-crz">
            <screen-input id="dr-crz" v-model="query.crzdbh" clearable placeholder="模糊匹配" @enter="handleSearch" />
          </screen-field>

          <screen-field label="收文日期" :label-width="'84px'">
            <screen-date-input v-model="dateRange" mode="range" @change="handleDateChange" />
          </screen-field>

          <screen-field label="密级" :label-width="'84px'">
            <screen-select v-model="query.secretLevel" :options="secretOptions" placeholder="全部密级" aria-label="密级" />
          </screen-field>

          <screen-field label="紧急程度" :label-width="'84px'">
            <screen-select v-model="query.urgency" :options="urgencyOptions" placeholder="全部" aria-label="紧急程度" />
          </screen-field>

          <screen-field label="归档情况" :label-width="'84px'">
            <screen-select v-model="query.archived" :options="DOC_ARCHIVED_OPTIONS" placeholder="全部" aria-label="归档情况" />
          </screen-field>
        </template>
      </div>

      <div class="doc-receive__foot">
        <button
          type="button"
          class="doc-receive__toggle"
          :aria-expanded="expanded ? 'true' : 'false'"
          @click="expanded = !expanded"
        >
          <screen-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="12" />
          {{ expanded ? '收起条件' : '更多条件' }}
        </button>

        <div class="doc-receive__actions">
          <screen-button icon="rotate-ccw" @click="handleReset">重置</screen-button>
          <screen-button type="primary" icon="search" @click="handleSearch">查询</screen-button>
        </div>
      </div>
    </screen-panel>

    <!-- ================= 列表 ================= -->
    <screen-panel class="doc-receive__list" title="收文列表">
      <template #extra>
        <span class="doc-receive__total">
          共 <b>{{ total }}</b> 条收文
          <template v-if="selectedRowKeys.length">
            · 已选 <b class="is-accent">{{ selectedRowKeys.length }}</b> 项
          </template>
        </span>

        <screen-button type="primary" size="sm" icon="plus" @click="handleAdd">收文登记</screen-button>

        <screen-popconfirm
          v-if="selectedRowKeys.length"
          title="删除后不可恢复，确定批量删除选中的收文吗？"
          :description="`共 ${selectedRowKeys.length} 条收文及其附件会被删除`"
          width="286"
          @confirm="handleBatchDelete"
        >
          <screen-button type="danger" size="sm" icon="trash">批量删除</screen-button>
        </screen-popconfirm>

        <screen-button size="sm" icon="reload" :loading="loading" @click="loadData">刷新</screen-button>
      </template>

      <screen-data-table
        :columns="columns"
        :data="dataSource"
        row-key="id"
        :loading="loading"
        selectable
        :selected-keys="selectedRowKeys"
        :min-width="1760"
        empty-text="没有符合条件的收文，试试放宽检索条件"
        @select-change="handleSelectChange"
      >
        <template #docNo="{ row }">
          <button type="button" class="doc-receive__link" @click="handleDetail(row)">{{ row.docNo }}</button>
        </template>

        <template #docTitle="{ row }">
          <span class="doc-receive__title" :title="row.docTitle">{{ row.docTitle }}</span>
        </template>

        <template #handler="{ row }">
          <span v-if="row.currentHandlerName">{{ row.currentHandlerName }}</span>
          <span v-else class="doc-receive__muted">待分办</span>
        </template>

        <template #status="{ row }">
          <screen-tag :tone="docStatusTone(row.status)" size="sm">{{ row.status || '—' }}</screen-tag>
        </template>

        <template #urgency="{ row }">
          <screen-tag v-if="row.urgency" :tone="docUrgencyTone(row.urgency)" size="sm">{{ row.urgency }}</screen-tag>
          <span v-else class="doc-receive__muted">—</span>
        </template>

        <template #attachmentCount="{ row }">
          <screen-tag v-if="row.attachmentCount" tone="info" size="sm">{{ row.attachmentCount }}</screen-tag>
          <span v-else class="doc-receive__muted">—</span>
        </template>

        <template #archiveFlag="{ row }">
          <!-- 归档情况以**档案真实状态**为准（后端随列表带出 archiveStatus）：
               无附件时档案是「归档中」，不能因为 archiveId 有值就显示成「已归档」 -->
          <screen-tag :tone="archiveTone(row)" size="sm">{{ archiveText(row) }}</screen-tag>
        </template>

        <template #action="{ row }">
          <span class="doc-receive__row-actions">
            <button type="button" class="doc-receive__link" @click="handleDetail(row)">详情</button>

            <button type="button" class="doc-receive__link" @click="handleFlow(row)">
              {{ isClosed(row) ? '流转痕迹' : '流转' }}
            </button>

            <!-- 已归档的收文只读：编辑入口直接不渲染（后端 updateDoc 也会拒绝） -->
            <button
              v-if="!row.archiveId"
              type="button"
              class="doc-receive__link"
              @click="handleEdit(row)"
            >
              编辑
            </button>

            <!-- 只有「已办结且未归档」才提供归档入口，与后端状态机一致 -->
            <button
              v-if="row.status === '已办结' && !row.archiveId"
              type="button"
              class="doc-receive__link"
              @click="handleArchive(row)"
            >
              归档
            </button>

            <screen-popconfirm
              v-if="row.archiveId"
              title="删除收文不会删除已生成的档案，确定删除吗？"
              :description="row.docNo"
              width="300"
              @confirm="handleDelete(row)"
            >
              <button type="button" class="doc-receive__link is-danger">删除</button>
            </screen-popconfirm>

            <screen-popconfirm
              v-else
              title="删除后不可恢复，确定删除该收文吗？"
              :description="row.docNo"
              width="264"
              @confirm="handleDelete(row)"
            >
              <button type="button" class="doc-receive__link is-danger">删除</button>
            </screen-popconfirm>
          </span>
        </template>
      </screen-data-table>

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

    <!-- ================= 弹窗 =================
         顺序很重要：同层级的 fixed 弹窗，**后声明的盖住先声明的**。
         归档弹窗必须排在详情弹窗之后，否则「从详情点归档」时归档弹窗会被详情挡住。 -->
    <doc-receive-form-modal ref="formModal" @ok="onChanged" />
    <doc-flow-modal ref="flowModal" @finished="handleFinished" />
    <doc-detail-modal ref="detailModal" @open-flow="handleFlow" @edit="handleEdit" @archive="handleArchive" />
    <doc-archive-modal ref="archiveModal" @ok="handleArchived" />

    <!-- 归档结果：把「原附件数 → 转换数 → 落库行数」三段计数摆出来，便于定位断点 -->
    <screen-modal
      :visible.sync="archiveResult.visible"
      title="归档完成"
      :width="600"
      ok-text="查看档案"
      cancel-text="关闭"
      @ok="handleOpenArchive"
      @cancel="handleCloseArchiveResult"
    >
      <div class="doc-receive__archive-result">
        <p class="doc-receive__archive-line">
          已归入档案 <b>{{ archiveResult.archiveNo || archiveResult.archiveId }}</b>
          <span class="doc-receive__muted">
            {{ archiveResult.reusedCaseFile ? '（复用该项目已有案卷）' : '（新建案卷）' }}
          </span>
        </p>
        <p class="doc-receive__archive-line" role="status" :class="{ 'is-warn': archiveLostFiles }">
          {{ archiveResult.verifyText }}
        </p>
        <!--
          三段计数里「落库 < 转换」= 有文件没进 t_archive_file（P1-11）。
          这是失败信号，不能用成功绿一笔带过：用户会以为卷内文件齐了。
        -->
        <p v-if="archiveLostFiles" class="doc-receive__archive-line is-warn doc-receive__alert" role="alert">
          <screen-icon name="alert-triangle" :size="12" />
          有 {{ lostFileCount }} 个文件未落库，请检查后端日志 / 磁盘（归档已写入的部分不会回滚）
        </p>
        <p
          v-if="archiveResult.archiveStatus && archiveResult.archiveStatus !== '已归档'"
          class="doc-receive__archive-line is-warn"
        >
          档案当前状态为「{{ archiveResult.archiveStatus }}」：该公文没有附件，
          需在档案维护里补传扫描件后才会变成「已归档」。
        </p>
      </div>
    </screen-modal>

    <!-- 办结后「是否立即归档」的确认：需求要求给出提示让用户选择 -->
    <screen-modal
      :visible.sync="finishPrompt.visible"
      title="收文已办结"
      :width="560"
      ok-text="立即归档"
      cancel-text="暂不归档"
      @ok="handleFinishArchive"
      @cancel="handleFinishLater"
    >
      <p class="doc-receive__prompt">
        「{{ finishPrompt.docNo }} {{ finishPrompt.docTitle }}」已办结，
        是否立即归档到档案管理？归档时需要选择档案类别。
      </p>
    </screen-modal>
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenDateInput,
  ScreenDataTable,
  ScreenPagination,
  ScreenTag,
  ScreenPopconfirm,
  ScreenModal,
  ScreenIcon
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import DocReceiveFormModal from './DocReceiveFormModal.vue'
import DocFlowModal from './DocFlowModal.vue'
import DocArchiveModal from './DocArchiveModal.vue'
import DocDetailModal from './DocDetailModal.vue'
import {
  queryDocReceiveList,
  deleteDocReceive,
  deleteDocReceiveBatch,
  queryDocReceiveStat,
  queryDocReceiveById
} from '@/api/land/document'
import {
  DOC_FILTER_STATUSES,
  DOC_URGENCIES,
  DOC_ARCHIVED_OPTIONS,
  SECRET_LEVELS,
  toOptions,
  docStatusTone,
  docUrgencyTone,
  compactQuery,
  defaultPagination
} from '../../constants'

function buildEmptyQuery () {
  return {
    docNo: '',
    docTitle: '',
    fromDept: '',
    fromDocNo: '',
    ptxmmc: '',
    crzdbh: '',
    status: '',
    secretLevel: '',
    urgency: '',
    // 归档情况在界面上用字符串 'true'/'false'（ScreenSelect 不接受布尔值），
    // 拼查询条件时再转回布尔，详见 constants.js 的说明
    archived: '',
    beginDate: '',
    endDate: '',
    onlyMine: undefined
  }
}

export default {
  name: 'DocReceivePanel',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenDataTable,
    ScreenPagination,
    ScreenTag,
    ScreenPopconfirm,
    ScreenModal,
    ScreenIcon,
    DocReceiveFormModal,
    DocFlowModal,
    DocArchiveModal,
    DocDetailModal
  },
  data () {
    return {
      DOC_ARCHIVED_OPTIONS,
      loading: false,
      expanded: false,
      dateRange: [],
      query: buildEmptyQuery(),
      /** 当前激活的统计卡 key（'' = 全部，'mine' = 我的待办） */
      activeStat: '',
      stat: { total: 0, pending: 0, handling: 0, rejected: 0, finished: 0, archived: 0, myTodo: 0 },
      dataSource: [],
      total: 0,
      selectedRowKeys: [],
      pagination: defaultPagination(10),
      finishPrompt: { visible: false, doc: null, docNo: '', docTitle: '' },
      /** 归档结果（后端返回的三段计数 + archiveId；visible 控制结果弹窗） */
      archiveResult: {
        visible: false,
        archiveId: '',
        archiveNo: '',
        archiveStatus: '',
        reusedCaseFile: false,
        verifyText: '',
        /** 三段计数（P1-11）：原附件 → 转换 → 落库，后端如实返回 */
        attachmentCount: 0,
        convertedFileCount: 0,
        savedFileCount: 0
      },
      statusOptions: toOptions(DOC_FILTER_STATUSES),
      secretOptions: toOptions(SECRET_LEVELS),
      urgencyOptions: toOptions(DOC_URGENCIES),
      columns: [
        { key: 'docNo', title: '收文登记号', width: 150, type: 'slot' },
        { key: 'docTitle', title: '文件标题', width: 260, type: 'slot' },
        { key: 'fromDept', title: '来文单位', width: 170, ellipsis: true },
        { key: 'fromDocNo', title: '来文字号', width: 150, ellipsis: true },
        { key: 'receiveDate', title: '收文日期', width: 110 },
        { key: 'ptxmmc', title: '配套项目', width: 190, ellipsis: true },
        { key: 'currentHandlerName', title: '当前处理人', width: 110, type: 'slot' },
        { key: 'status', title: '状态', width: 90, type: 'slot', align: 'center' },
        { key: 'urgency', title: '紧急程度', width: 90, type: 'slot', align: 'center' },
        { key: 'attachmentCount', title: '附件', width: 70, type: 'slot', align: 'center' },
        { key: 'archiveFlag', title: '归档', width: 90, type: 'slot', align: 'center' },
        { key: 'action', title: '操作', width: 260, type: 'slot', align: 'center' }
      ]
    }
  },
  computed: {
    statCards () {
      return [
        { key: '', label: '收文总数', value: this.stat.total || 0, tone: 'default' },
        { key: 'mine', label: '我的待办', value: this.stat.myTodo || 0, tone: 'primary' },
        { key: '待承办', label: '待承办', value: this.stat.pending || 0, tone: 'warn' },
        { key: '承办中', label: '承办中', value: this.stat.handling || 0, tone: 'info' },
        { key: '已退回', label: '已退回', value: this.stat.rejected || 0, tone: 'danger' },
        { key: '已办结', label: '已办结', value: this.stat.finished || 0, tone: 'ok' },
        { key: '已归档', label: '已归档', value: this.stat.archived || 0, tone: 'info' }
      ]
    },
    /** 给读屏的完整语义播报，避免只念出裸数字 */
    statAnnouncement () {
      return `收文统计已更新：共 ${this.stat.total || 0} 条，我的待办 ${this.stat.myTodo || 0} 条，` +
        `待承办 ${this.stat.pending || 0} 条，承办中 ${this.stat.handling || 0} 条，` +
        `已退回 ${this.stat.rejected || 0} 条，已办结 ${this.stat.finished || 0} 条，已归档 ${this.stat.archived || 0} 条`
    },
    /**
     * 是否有文件没落库（P1-11）。
     * ★ 判据用后端两个真实计数比大小，而不是去解析 verifyText 文案 ——
     *   文案是给人看的，改个措辞就会让前端判断失效。
     */
    archiveLostFiles () {
      const result = this.archiveResult
      const saved = Number(result.savedFileCount || 0)
      const converted = Number(result.convertedFileCount || 0)
      return converted > 0 && saved < converted
    },
    lostFileCount () {
      const result = this.archiveResult
      return Math.max(Number(result.convertedFileCount || 0) - Number(result.savedFileCount || 0), 0)
    }
  },
  created () {
    this.loadData()
    this.loadStat()
  },
  methods: {
    docStatusTone,
    docUrgencyTone,

    /* ---------------- 数据 ---------------- */

    loadData () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize
      })
      // 只查「我的待办」时不能再叠加状态条件，否则两个条件会自相矛盾
      if (params.onlyMine) delete params.status

      return queryDocReceiveList(this.normalizeQuery(params))
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '收文列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })

          // 当前页被删空时自动回退一页
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadData()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    /** 归档情况的字符串值转回布尔，并剔除空值 */
    normalizeQuery (params) {
      const next = Object.assign({}, params)
      if (next.archived === 'true') next.archived = true
      else if (next.archived === 'false') next.archived = false
      else delete next.archived
      return compactQuery(next)
    },

    loadStat () {
      return queryDocReceiveStat().then((res) => {
        if (res && res.success && res.result) {
          this.stat = Object.assign({}, this.stat, res.result)
        }
      })
    },

    onChanged () {
      this.loadData()
      this.loadStat()
    },

    /* ---------------- 检索 ---------------- */

    handleSearch () {
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.loadData()
    },
    handleReset () {
      this.query = buildEmptyQuery()
      this.dateRange = []
      this.activeStat = ''
      this.pagination.current = 1
      this.loadData()
    },
    /**
     * 清空检索条件（页签壳 P2-01 的公开契约）。
     *
     * ★ 为什么不能只复用 handleReset：契约里还要求「页码回 1 + 清选中 + 重查汇总」，
     *   而 handleReset 是页内按钮、只看列表。两者共用同一份空结构，避免条件漂移。
     * ★ 这里**不清**归档结果弹窗：它是上一次操作的结论，与检索条件无关。
     */
    resetFilters () {
      this.query = buildEmptyQuery()
      this.dateRange = []
      this.activeStat = ''
      this.selectedRowKeys = []
      this.pagination = Object.assign({}, this.pagination, { current: 1 })
      this.loadData()
      this.loadStat()
    },
    handleDateChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginDate = values[0] || ''
      this.query.endDate = values[1] || ''
    },
    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },
    handleSelectChange (keys) {
      this.selectedRowKeys = keys || []
    },

    /** 统计是全量口径；清掉其它条件后，卡片数字才与点击后的列表 total 一致。 */
    handleStatClick (key) {
      const active = this.activeStat === key ? '' : key
      const query = buildEmptyQuery()
      if (active === 'mine') {
        query.onlyMine = true
      } else if (active) {
        query.status = active
      }
      this.activeStat = active
      this.query = query
      this.dateRange = []
      this.selectedRowKeys = []
      this.pagination.current = 1
      this.loadData()
    },

    /* ---------------- 行操作 ---------------- */

    isClosed (record) {
      return record.status === '已办结' || record.status === '已归档'
    },
    /** 归档情况文案：以档案真实状态为准（archiveStatus 由后端随列表带出） */
    archiveText (row) {
      if (!row || !row.archiveId) return '未归档'
      return row.archiveStatus || '已归档'
    },
    archiveTone (row) {
      if (!row || !row.archiveId) return 'muted'
      if (!row.archiveStatus || row.archiveStatus === '已归档') return 'success'
      // 归档中 / 审核中：进过档案但还没成为正式归档件，用 info 语气而不是成功色
      return 'info'
    },
    handleAdd () {
      this.$refs.formModal.showAdd()
    },
    handleEdit (record) {
      if (!record || !record.id) return
      // 已归档只读（后端 updateDoc 也拒绝，这里只是少一次白跑的请求）
      if (record.archiveId) {
        toast.warning('该收文已归档，不能再编辑；如需更正请在档案维护里处理')
        return
      }
      this.$refs.formModal.showEdit(record)
    },
    handleFlow (record) {
      if (!record || !record.id) return
      this.$refs.flowModal.open(record)
    },
    /**
     * 从详情 / 列表打开归档弹窗。
     *
     * ★ 必须先把详情关掉：两个弹窗都是同层 fixed，后声明的盖住先声明的。
     *   即使模板里已经把归档弹窗挪到详情之后（双保险），
     *   详情留着不收也会让「归档完成后回到详情」看起来像卡住。
     */
    handleArchive (record) {
      const detail = this.$refs.detailModal
      if (detail && typeof detail.close === 'function') detail.close()
      this.$refs.archiveModal.open(record, 'receive')
    },
    /** 归档成功：刷新列表与统计，并弹出「三段计数 + 查看档案」结果 */
    handleArchived (result) {
      this.onChanged()
      const data = result || {}
      this.archiveResult = {
        visible: true,
        archiveId: data.archiveId || '',
        archiveNo: data.archiveNo || '',
        archiveStatus: data.archiveStatus || '',
        reusedCaseFile: !!data.reusedCaseFile,
        verifyText: data.verifyText || '',
        attachmentCount: Number(data.attachmentCount || 0),
        convertedFileCount: Number(data.convertedFileCount || 0),
        savedFileCount: Number(data.savedFileCount || 0)
      }
    },
    /** 「查看档案」：走页签壳的 drill 通道，用同一个 archiveId 打开档案详情 */
    handleOpenArchive () {
      const archiveId = this.archiveResult.archiveId
      this.handleCloseArchiveResult()
      if (!archiveId) return
      this.$emit('drill', { archiveId })
    },
    handleCloseArchiveResult () {
      this.archiveResult = Object.assign({}, this.archiveResult, { visible: false })
    },
    handleDetail (record) {
      queryDocReceiveById(record.id).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '收文详情加载失败')
          return
        }
        this.$refs.detailModal.open(res.result, 'receive')
      })
    },

    /**
     * 办结成功 → 先提示是否归档（需求：给出提示，让用户选择是否归档）。
     * 选择归档后打开归档弹窗，其中档案类别必填。
     */
    handleFinished (doc) {
      this.onChanged()
      this.finishPrompt = {
        visible: true,
        doc,
        docNo: (doc && doc.docNo) || '',
        docTitle: (doc && doc.docTitle) || ''
      }
    },
    handleFinishArchive () {
      const doc = this.finishPrompt.doc
      this.finishPrompt = Object.assign({}, this.finishPrompt, { visible: false })
      if (doc) this.handleArchive(doc)
    },
    handleFinishLater () {
      this.finishPrompt = Object.assign({}, this.finishPrompt, { visible: false })
      toast.info('已办结。可稍后在列表点「归档」补办归档。')
    },

    handleDelete (record) {
      deleteDocReceive(record.id).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '删除失败')
          return
        }
        toast.success(res.message || '删除成功')
        this.onChanged()
      })
    },
    handleBatchDelete () {
      if (!this.selectedRowKeys.length) return
      deleteDocReceiveBatch(this.selectedRowKeys).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '批量删除失败')
          return
        }
        toast.success(res.message || '批量删除成功')
        this.selectedRowKeys = []
        this.onChanged()
      })
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.doc-receive {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  /* ---------------- 统计卡 ---------------- */
  &__stats {
    display: grid;
    grid-template-columns: repeat(7, minmax(0, 1fr));
    gap: var(--screen-space-2);
    flex: 0 0 auto;
  }

  &__stat {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    padding: var(--screen-space-2) var(--screen-space-3);
    font-family: inherit;
    text-align: left;
    cursor: pointer;
    .screen-glass();
    // 左侧语气条：与数值颜色一起构成非颜色单一线索
    border-left: 3px solid var(--screen-bar-muted-to);
    transition: border-color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      border-left-color: var(--screen-accent);
      background: var(--screen-elevate);
    }

    &.is-active {
      border-left-color: var(--screen-accent);
      border-color: var(--screen-border-strong);
      background: rgba(130, 198, 255, 0.1);
    }
  }

  &__stat-label {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__stat-value {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-number-sm);
    font-weight: 700;
    line-height: 1.1;
    color: var(--screen-text);
  }

  &__stat.is-primary {
    border-left-color: var(--screen-info);

    .doc-receive__stat-value {
      color: var(--screen-info);
    }
  }

  &__stat.is-warn {
    border-left-color: var(--screen-warning);

    .doc-receive__stat-value {
      color: var(--screen-warning);
    }
  }

  &__stat.is-info {
    border-left-color: var(--screen-viz-cyan);

    .doc-receive__stat-value {
      color: var(--screen-viz-cyan);
    }
  }

  &__stat.is-danger {
    border-left-color: var(--screen-danger);

    .doc-receive__stat-value {
      color: var(--screen-danger);
    }
  }

  &__stat.is-ok {
    border-left-color: var(--screen-success);

    .doc-receive__stat-value {
      color: var(--screen-success);
    }
  }

  /* 视觉隐藏但可被读屏读到（统计播报用） */
  &__sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }

  /* ---------------- 检索条件 ---------------- */
  &__search {
    flex: 0 0 auto;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-3);
    margin-top: var(--screen-space-3);
    padding-top: var(--screen-space-2);
    border-top: 1px solid var(--screen-border-soft);
  }

  &__toggle {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent);
    background: none;
    border: 0;
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
    }
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin-left: auto;
  }

  /* ---------------- 列表 ---------------- */
  &__list {
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
      color: var(--screen-text);
    }

    b.is-accent {
      color: var(--screen-accent);
    }
  }

  &__title {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__muted {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__row-actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
    justify-content: center;
  }

  &__link {
    .screen-link-action();

    &.is-danger {
      color: var(--screen-danger);

      &:hover {
        color: #ffab9f;
      }
    }
  }

  &__prompt {
    margin: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-text-sub);
  }

  /* ---------------- 归档结果（三段计数） ---------------- */
  &__archive-result {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
  }

  &__archive-line {
    margin: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-text-sub);

    b {
      color: var(--screen-accent);
    }

    &.is-warn {
      color: var(--screen-warning);
    }
  }

  /* 带图标的告警行：图标与文字同一行居中，不与正文抢行高 */
  &__alert {
    display: flex;
    align-items: center;
    gap: 6px;
  }
}

// 工具条按钮较多，窄屏允许换行
.doc-receive__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 1760px) {
  .doc-receive__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1400px) {
  .doc-receive__stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .doc-receive__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .doc-receive__stat {
    transition: none;
  }
}
</style>
