<template>
  <!--
    ArchiveMaintain 档案维护（方案 2.3.2 第 2 项）
    --------------------------------
    「在已上传的配套项目中添加档案信息，并记录相关操作」——
    新增/编辑弹窗里的配套项目只能从已有记录里选，不能手工输入新项目名；
    所有写操作都会在后端写 t_archive_log 操作记录（详情页「操作记录」页签可查）。

    布局：
      上排：检索条件（可折叠）
      下排：列表面板（工具条 + 表格 + 分页），列表吃掉剩余高度
  -->
  <div class="archive-maintain">
    <screen-panel class="archive-maintain__search" title="档案检索" collapsible>
      <archive-search-form ref="search" @search="handleSearch" />
    </screen-panel>

    <screen-panel class="archive-maintain__list" title="档案列表">
      <template #extra>
        <span class="archive-maintain__total">
          共 <b>{{ total }}</b> 个档案
          <template v-if="selectedRowKeys.length">
            · 已选 <b class="is-accent">{{ selectedRowKeys.length }}</b> 项
          </template>
        </span>

        <screen-button
          type="primary"
          size="sm"
          icon="plus"
          @click="handleAdd"
        >
          新增档案
        </screen-button>

        <screen-button
          size="sm"
          icon="download"
          :disabled="!total"
          @click="handleExport"
        >
          按条件导出 ZIP
        </screen-button>

        <screen-popconfirm
          v-if="selectedRowKeys.length" title="删除后不可恢复，确定批量删除选中的档案吗？"
          :description="`共 ${selectedRowKeys.length} 个档案及其卷内文件会被删除`"
          width="286"
          @confirm="handleBatchDelete"
        >
          <screen-button type="danger" size="sm" icon="trash">
            批量删除
          </screen-button>
        </screen-popconfirm>

        <screen-button size="sm" icon="reload" :loading="loading" @click="loadData">刷新</screen-button>
      </template>

      <archive-table
        :data-source="dataSource"
        :loading="loading"
        selectable
        :selected-row-keys="selectedRowKeys"
        @select-change="handleSelectChange"
        @detail="handleDetail"
        @edit="handleEdit"
        @delete="handleDelete"
        @action="handleTableAction"
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

    <archive-form-modal ref="formModal" @ok="handleSaved" />
    <archive-detail-modal ref="detailModal" @edit="handleEdit" @export="handleExportOne" />
  </div>
</template>

<script>
import { ScreenPanel, ScreenButton, ScreenPagination, ScreenPopconfirm } from '@/components/screen'
import { toast } from '@/components/screen/toast'
import ArchiveSearchForm from './ArchiveSearchForm.vue'
import ArchiveTable from './ArchiveTable.vue'
import ArchiveFormModal from './ArchiveFormModal.vue'
import ArchiveDetailModal from './ArchiveDetailModal.vue'
import {
  queryArchiveList,
  deleteArchive,
  deleteArchiveBatch,
  exportArchiveZip,
} from '@/api/land/archive'
import { defaultPagination } from '../constants'
export default {
  name: 'ArchiveMaintain',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenPagination,
    ScreenPopconfirm,
    ArchiveSearchForm,
    ArchiveTable,
    ArchiveFormModal,
    ArchiveDetailModal,
  },
  data () {
    return {
      loading: false,
      query: {},
      dataSource: [],
      total: 0,
      selectedRowKeys: [],
      pagination: defaultPagination(10),
    }
  },
  created () {
    this.loadData()
  },
  methods: {
    loadData () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize,
      })

      return queryArchiveList(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })

          // 当前页被删空时自动回退一页，避免用户停在空白页以为数据没了
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadData()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    handleSearch (query) {
      this.query = query || {}
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.loadData()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    handleSelectChange (keys) {
      this.selectedRowKeys = keys || []
    },

    /**
     * 表格内的通用 action 事件。
     * 档案号列（type=link）点击等价于「查看详情」，这里做一次转发，
     * 让用户点档案号也能直接打开详情，不必去点操作列的「详情」。
     */
    handleTableAction (row, col) {
      if (col && col.key === 'archiveNo') {
        this.handleDetail(row)
      }
    },

    handleAdd () {
      this.$refs.formModal.showAdd()
    },

    handleEdit (record) {
      // 详情弹窗的「编辑」事件带的是完整 detail 对象，列表行也是同构数据
      if (!record || !record.id) return
      this.$refs.formModal.showEdit(record)
    },

    handleSaved () {
      this.loadData()
    },

    handleDetail (record) {
      this.$refs.detailModal.open(record)
    },

    handleDelete (record) {
      deleteArchive(record.id).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '删除失败')
          return
        }
        toast.success(res.message || '删除成功')
        this.selectedRowKeys = this.selectedRowKeys.filter((key) => key !== record.id)
        this.loadData()
      })
    },

    handleBatchDelete () {
      if (!this.selectedRowKeys.length) return
      deleteArchiveBatch(this.selectedRowKeys).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '批量删除失败')
          return
        }
        toast.success(res.message || '批量删除成功')
        this.selectedRowKeys = []
        this.loadData()
      })
    },

    /** 按当前检索条件导出（与列表用的是同一份条件，避免「看到的」和「导出的」不一致） */
    handleExport () {
      const query = this.$refs.search ? this.$refs.search.getQuery() : this.query
      exportArchiveZip(query)
      toast.info('已开始导出，请稍候…')
    },

    /** 详情页按项目导出：优先用配套项目，其次用宗地编号 */
    handleExportOne (record) {
      exportArchiveZip({
        facilityId: record.facilityId || undefined,
        crzdbh: record.facilityId ? undefined : record.crzdbh || undefined,
      })
      toast.info('已开始按项目导出，请稍候…')
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.archive-maintain {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  // 检索条件按内容高度，列表吃掉剩余高度
  &__search {
    flex: 0 0 auto;
  }

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
}

// 工具条上的按钮较多，窄屏允许换行而不是被压扁
.archive-maintain__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
