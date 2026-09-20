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
          <screen-tag :tone="row.archiveId ? 'success' : 'muted'" size="sm">
            {{ row.archiveId ? '已归档' : '未归档' }}
          </screen-tag>
        </template>

        <template #action="{ row }">
          <span class="doc-receive__row-actions">
            <button type="button" class="doc-receive__link" @click="handleDetail(row)">详情</button>

            <button type="button" class="doc-receive__link" @click="handleFlow(row)">
              {{ isClosed(row) ? '流转痕迹' : '流转' }}
            </button>

            <button type="button" class="doc-receive__link" @click="handleEdit(row)">编辑</button>

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

    <!-- ================= 弹窗 ================= -->
    <doc-receive-form-modal ref="formModal" @ok="onChanged" />
    <doc-flow-modal ref="flowModal" @finished="handleFinished" />
    <doc-archive-modal ref="archiveModal" @ok="onChanged" />
    <doc-detail-modal ref="detailModal" @open-flow="handleFlow" @edit="handleEdit" @archive="handleArchive" />

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
  ScreenIcon,
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
  queryDocReceiveById,
} from '@/api/land/document'
import {
  DOC_STATUSES,
  DOC_URGENCIES,
  DOC_ARCHIVED_OPTIONS,
  SECRET_LEVELS,
  toOptions,
  docStatusTone,
  docUrgencyTone,
  compactQuery,
  defaultPagination,
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
    onlyMine: undefined,
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
    DocDetailModal,
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
      statusOptions: toOptions(DOC_STATUSES),
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
        { key: 'action', title: '操作', width: 260, type: 'slot', align: 'center' },
      ],
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
      ]
    },
    /** 给读屏的完整语义播报，避免只念出裸数字 */
    statAnnouncement () {
      return `收文统计已更新：共 ${this.stat.total || 0} 条，我的待办 ${this.stat.myTodo || 0} 条，`
        + `待承办 ${this.stat.pending || 0} 条，承办中 ${this.stat.handling || 0} 条，`
        + `已退回 ${this.stat.rejected || 0} 条，已办结 ${this.stat.finished || 0} 条`
    },
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
        pageSize: this.pagination.pageSize,
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

    /** 点统计卡做快捷筛选（再点一次取消） */
    handleStatClick (key) {
      if (key === 'mine') {
        this.activeStat = this.activeStat === 'mine' ? '' : 'mine'
        this.query.onlyMine = this.activeStat === 'mine' ? true : undefined
      } else {
        this.activeStat = this.activeStat === key ? '' : key
        this.query.status = this.activeStat || ''
      }
      this.pagination.current = 1
      this.loadData()
    },

    /* ---------------- 行操作 ---------------- */

    isClosed (record) {
      return record.status === '已办结' || record.status === '已归档'
    },
    handleAdd () {
      this.$refs.formModal.showAdd()
    },
    handleEdit (record) {
      if (!record || !record.id) return
      this.$refs.formModal.showEdit(record)
    },
    handleFlow (record) {
      if (!record || !record.id) return
      this.$refs.flowModal.open(record)
    },
    handleArchive (record) {
      this.$refs.archiveModal.open(record, 'receive')
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
        docTitle: (doc && doc.docTitle) || '',
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
    },
  },
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
    grid-template-columns: repeat(6, minmax(0, 1fr));
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
      background: rgba(47, 227, 192, 0.1);
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
    flex: 1 1 auto;
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
