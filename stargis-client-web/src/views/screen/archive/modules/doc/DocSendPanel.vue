<template>
  <!--
    DocSendPanel 发文管理
    --------------------------------
    发文信息记录台账：登记发文登记号、标题、主送与抄送单位、签发人、拟稿人、日期与附件。
    发文 **没有流转环节**，登记后可直接归档到档案管理（归档时必须选择档案类别）。

    与「收文管理」保持同一套交互：统计卡兼作快捷筛选 → 检索条件 → 列表。

    相对管理端的两点增强（后端接口本来就支持，界面补齐以保持两个页签一致）：
      1. 统计卡可点击做「全部 / 已归档 / 未归档」快捷筛选（管理端只是展示数字）；
      2. 列表支持多选 + 批量删除（管理端只有单条删除）。

    无障碍：统计卡异步加载，因此用一个视觉隐藏的 role="status" 区域播报完整语义，
    避免读屏只念出一个裸数字。
  -->
  <div class="doc-send">
    <!-- ================= 统计卡（点击做快捷筛选） ================= -->
    <div class="doc-send__stats" role="group" aria-label="发文统计与快捷筛选">
      <button
        v-for="card in statCards"
        :key="card.key || 'all'"
        type="button"
        class="doc-send__stat"
        :class="[`is-${card.tone}`, { 'is-active': activeStat === card.key }]"
        :aria-pressed="activeStat === card.key ? 'true' : 'false'"
        @click="handleStatClick(card.key)"
      >
        <span class="doc-send__stat-label">{{ card.label }}</span>
        <span class="doc-send__stat-value">{{ card.value }}</span>
      </button>
    </div>

    <p class="doc-send__sr-only" role="status" aria-live="polite" aria-atomic="true">
      {{ statAnnouncement }}
    </p>

    <!-- ================= 检索条件 ================= -->
    <screen-panel class="doc-send__search" title="发文检索" collapsible>
      <div class="doc-send__grid">
        <screen-field label="发文登记号" :label-width="'84px'" html-for="ds-no">
          <screen-input id="ds-no" v-model="query.docNo" clearable placeholder="模糊匹配" @enter="handleSearch" />
        </screen-field>

        <screen-field label="文件标题" :label-width="'84px'" html-for="ds-title">
          <screen-input id="ds-title" v-model="query.docTitle" clearable placeholder="模糊匹配" @enter="handleSearch" />
        </screen-field>

        <screen-field label="主送单位" :label-width="'84px'" html-for="ds-to">
          <screen-input id="ds-to" v-model="query.toDept" clearable placeholder="模糊匹配" @enter="handleSearch" />
        </screen-field>

        <screen-field label="归档情况" :label-width="'84px'">
          <screen-select v-model="query.archived" :options="DOC_ARCHIVED_OPTIONS" placeholder="全部" aria-label="归档情况" />
        </screen-field>

        <template v-if="expanded">
          <screen-field label="抄送单位" :label-width="'84px'" html-for="ds-cc">
            <screen-input id="ds-cc" v-model="query.ccDept" clearable placeholder="模糊匹配" @enter="handleSearch" />
          </screen-field>

          <screen-field label="配套项目" :label-width="'84px'" html-for="ds-ptxm">
            <screen-input id="ds-ptxm" v-model="query.ptxmmc" clearable placeholder="项目名称，模糊匹配" @enter="handleSearch" />
          </screen-field>

          <screen-field label="宗地编号" :label-width="'84px'" html-for="ds-crz">
            <screen-input id="ds-crz" v-model="query.crzdbh" clearable placeholder="模糊匹配" @enter="handleSearch" />
          </screen-field>

          <screen-field label="发文日期" :label-width="'84px'">
            <screen-date-input v-model="dateRange" mode="range" @change="handleDateChange" />
          </screen-field>

          <screen-field label="密级" :label-width="'84px'">
            <screen-select v-model="query.secretLevel" :options="secretOptions" placeholder="全部密级" aria-label="密级" />
          </screen-field>

          <screen-field label="签发人" :label-width="'84px'" html-for="ds-signer">
            <screen-input id="ds-signer" v-model="query.signer" clearable placeholder="模糊匹配" @enter="handleSearch" />
          </screen-field>
        </template>
      </div>

      <div class="doc-send__foot">
        <button
          type="button"
          class="doc-send__toggle"
          :aria-expanded="expanded ? 'true' : 'false'"
          @click="expanded = !expanded"
        >
          <screen-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="12" />
          {{ expanded ? '收起条件' : '更多条件' }}
        </button>

        <div class="doc-send__actions">
          <screen-button icon="rotate-ccw" @click="handleReset">重置</screen-button>
          <screen-button type="primary" icon="search" @click="handleSearch">查询</screen-button>
        </div>
      </div>
    </screen-panel>

    <!-- ================= 列表 ================= -->
    <screen-panel class="doc-send__list" title="发文列表">
      <template #extra>
        <span class="doc-send__total">
          共 <b>{{ total }}</b> 条发文
          <template v-if="selectedRowKeys.length">
            · 已选 <b class="is-accent">{{ selectedRowKeys.length }}</b> 项
          </template>
        </span>

        <screen-button type="primary" size="sm" icon="plus" @click="handleAdd">发文登记</screen-button>

        <screen-popconfirm
          v-if="selectedRowKeys.length"
          title="删除后不可恢复，确定批量删除选中的发文吗？"
          :description="`共 ${selectedRowKeys.length} 条发文及其附件会被删除`"
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
        :min-width="1660"
        empty-text="没有符合条件的发文，试试放宽检索条件"
        @select-change="handleSelectChange"
      >
        <template #docNo="{ row }">
          <button type="button" class="doc-send__link" @click="handleDetail(row)">{{ row.docNo }}</button>
        </template>

        <template #docTitle="{ row }">
          <span class="doc-send__title" :title="row.docTitle">{{ row.docTitle }}</span>
        </template>

        <template #attachmentCount="{ row }">
          <screen-tag v-if="row.attachmentCount" tone="info" size="sm">{{ row.attachmentCount }}</screen-tag>
          <span v-else class="doc-send__muted">—</span>
        </template>

        <template #archiveFlag="{ row }">
          <screen-tag :tone="row.archiveId ? 'success' : 'muted'" size="sm">
            {{ row.archiveId ? '已归档' : '未归档' }}
          </screen-tag>
        </template>

        <template #action="{ row }">
          <span class="doc-send__row-actions">
            <button type="button" class="doc-send__link" @click="handleDetail(row)">详情</button>

            <button type="button" class="doc-send__link" @click="handleEdit(row)">编辑</button>

            <button
              v-if="!row.archiveId"
              type="button"
              class="doc-send__link"
              @click="handleArchive(row)"
            >
              归档
            </button>

            <screen-popconfirm
              title="删除后不可恢复，确定删除该发文吗？"
              :description="row.docNo"
              width="264"
              @confirm="handleDelete(row)"
            >
              <button type="button" class="doc-send__link is-danger">删除</button>
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
    <doc-send-form-modal ref="formModal" @ok="onChanged" />
    <doc-archive-modal ref="archiveModal" @ok="onChanged" />
    <doc-detail-modal ref="detailModal" @edit="handleEdit" @archive="handleArchive" />
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
  ScreenIcon,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import DocSendFormModal from './DocSendFormModal.vue'
import DocArchiveModal from './DocArchiveModal.vue'
import DocDetailModal from './DocDetailModal.vue'
import {
  queryDocSendList,
  deleteDocSend,
  deleteDocSendBatch,
  queryDocSendStat,
  queryDocSendById,
} from '@/api/land/document'
import {
  DOC_ARCHIVED_OPTIONS,
  SECRET_LEVELS,
  toOptions,
  compactQuery,
  defaultPagination,
} from '../../constants'

function buildEmptyQuery () {
  return {
    docNo: '',
    docTitle: '',
    toDept: '',
    ccDept: '',
    signer: '',
    ptxmmc: '',
    crzdbh: '',
    secretLevel: '',
    archived: '',
    beginDate: '',
    endDate: '',
  }
}

export default {
  name: 'DocSendPanel',
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
    ScreenIcon,
    DocSendFormModal,
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
      activeStat: '',
      stat: { total: 0, archived: 0 },
      dataSource: [],
      total: 0,
      selectedRowKeys: [],
      pagination: defaultPagination(10),
      secretOptions: toOptions(SECRET_LEVELS),
      columns: [
        { key: 'docNo', title: '发文登记号', width: 150, type: 'slot' },
        { key: 'docTitle', title: '文件标题', width: 260, type: 'slot' },
        { key: 'toDept', title: '主送单位', width: 190, ellipsis: true },
        { key: 'ccDept', title: '抄送单位', width: 160, ellipsis: true },
        { key: 'issueDate', title: '发文日期', width: 110 },
        { key: 'ptxmmc', title: '配套项目', width: 190, ellipsis: true },
        { key: 'signer', title: '签发人', width: 100 },
        { key: 'attachmentCount', title: '附件', width: 70, type: 'slot', align: 'center' },
        { key: 'archiveFlag', title: '归档', width: 90, type: 'slot', align: 'center' },
        { key: 'action', title: '操作', width: 220, type: 'slot', align: 'center' },
      ],
    }
  },
  computed: {
    statCards () {
      const total = this.stat.total || 0
      const archived = this.stat.archived || 0
      return [
        { key: '', label: '发文总数', value: total, tone: 'default' },
        { key: 'true', label: '已归档', value: archived, tone: 'ok' },
        { key: 'false', label: '未归档', value: Math.max(0, total - archived), tone: 'warn' },
      ]
    },
    statAnnouncement () {
      const total = this.stat.total || 0
      const archived = this.stat.archived || 0
      return `发文统计已更新：共 ${total} 条，已归档 ${archived} 条，未归档 ${Math.max(0, total - archived)} 条`
    },
  },
  created () {
    this.loadData()
    this.loadStat()
  },
  methods: {
    /* ---------------- 数据 ---------------- */

    loadData () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize,
      })

      return queryDocSendList(this.normalizeQuery(params))
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '发文列表加载失败')
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
      return queryDocSendStat().then((res) => {
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

    /** 统计卡快捷筛选：'' 全部 / 'true' 已归档 / 'false' 未归档 */
    handleStatClick (key) {
      this.activeStat = this.activeStat === key ? '' : key
      this.query.archived = this.activeStat
      this.pagination.current = 1
      this.loadData()
    },

    /* ---------------- 行操作 ---------------- */

    handleAdd () {
      this.$refs.formModal.showAdd()
    },
    handleEdit (record) {
      if (!record || !record.id) return
      this.$refs.formModal.showEdit(record)
    },
    handleArchive (record) {
      this.$refs.archiveModal.open(record, 'send')
    },
    handleDetail (record) {
      queryDocSendById(record.id).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '发文详情加载失败')
          return
        }
        this.$refs.detailModal.open(res.result, 'send')
      })
    },

    handleDelete (record) {
      deleteDocSend(record.id).then((res) => {
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
      deleteDocSendBatch(this.selectedRowKeys).then((res) => {
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

.doc-send {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
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

  &__stat.is-ok {
    border-left-color: var(--screen-success);

    .doc-send__stat-value {
      color: var(--screen-success);
    }
  }

  &__stat.is-warn {
    border-left-color: var(--screen-warning);

    .doc-send__stat-value {
      color: var(--screen-warning);
    }
  }

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
}

.doc-send__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 1760px) {
  .doc-send__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1400px) {
  .doc-send__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .doc-send__stat {
    transition: none;
  }
}
</style>
